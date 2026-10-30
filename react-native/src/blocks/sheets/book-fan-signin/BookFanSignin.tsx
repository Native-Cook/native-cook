import { Canvas, Picture, Skia, useFont, useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { ReduceMotion, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, type Live, makeRes, type Res } from './bfs-draw';
import { closeSheet, type Driver, frameAt, inHome, inSheet, LOOP, makeDriver, openSheet, PEAK, stepDriver, T, touched } from './bfs-motion';
import { A, B, C, REF_H, REF_W, WALL } from './bfs-theme';
import { type Cue, useBfsAudio } from './use-bfs-audio';

/*
 Book Fan Sign-in — the home page of an invented book-summary app, "retold", and its sign-in sheet.

 Look (the pin, two stills): a white home page with a tilted wall of book covers fading into the
 header, "Which book is on your mind?", search, chips and recommended books; and the same page
 blurred and dimmed under a sign-in sheet whose top edge is a strip of covers, an app icon, a pink
 tag, "Books Don't Change You, Practice Does." and a black button.

 Motion (ours): the wall drifts up its columns like a slow conveyor. Sign in: the page blurs from
 the top down and dims, six covers lift off the wall and fly in arcs (turning, growing as they
 pass over) into the sheet's strip while the sheet springs up under them; the icon pops and its
 mark draws itself, the tag, the title (line by line, coming into focus), the text and the button
 follow, and a light runs through "Practice Does.". Then it all goes home, the covers flying back.

 autoPlay: LOOP = 9 s (bfs-motion.ts); frame 9 s equals frame 0.
 Touch: drag the wall to scroll it (it glides on); tap Sign in; pull the sheet down to dismiss it
 (past 110 pt or a flick), tap the dimmed page to close it; the button presses. 2 s after the last
 touch the loop resumes.
 Reduced motion: no flights and no drift; taps cross-fade between the page and the sheet.
*/

type Props = { autoPlay?: boolean; previewTime?: number };
type Target = 0 | 1 | 2 | 3 | 4 | 5; // none, wall, sign in, sheet, button, dim

let debugState = '';

export function BookFanSignin({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const covers = useImage(require('./assets/covers.webp'));
  const regular = useFont(require('./assets/fonts/Inter-Regular.ttf'), 100);
  const medium = useFont(require('./assets/fonts/Inter-Medium.ttf'), 100);
  const semi = useFont(require('./assets/fonts/Inter-SemiBold.ttf'), 100);
  const bold = useFont(require('./assets/fonts/Inter-Bold.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!covers || !regular || !medium || !semi || !bold) return null;
    const f = { regular: regular.getTypeface(), medium: medium.getTypeface(), semi: semi.getTypeface(), bold: bold.getTypeface() };
    if (!f.regular || !f.medium || !f.semi || !f.bold) return null;
    return makeRes({ regular: f.regular, medium: f.medium, semi: f.semi, bold: f.bold }, covers);
  }, [covers, regular, medium, semi, bold]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? REF_H;
  const geo = useMemo<Geo>(() => {
    const s = w / REF_W;
    return { w, h, s, dy: h - REF_H * s };
  }, [w, h]);

  const play = useBfsAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const time = useSharedValue(0);
  const live = useSharedValue<Live>({ amb: 0, scroll: 0, drag: 0, cta: 0, press: 0 });
  const fade = useSharedValue(0); // reduced motion: 0 page … 1 sheet
  const target = useSharedValue<Target>(0);
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(previewTime ?? 0));
    clock.set(0);
    time.set(previewTime ?? 0);
  }, [auto, reduced, ready, previewTime, driver, clock, time]);

  // Debug (development builds only): `globalThis.__bookFan.at(2.0)` pins the loop, `.play()`
  // resumes, `.park(t)` parks it for touch tests, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __bookFan?: unknown };
    g.__bookFan = {
      at: (t: number) => debugT.set(t),
      play: () => debugT.set(-1),
      state: () => debugState,
      park: (t: number) =>
        scheduleOnUI(() => {
          const d = driver.get();
          d.t = t;
          d.mode = 2;
          d.last = clock.get() + 60;
        }),
    };
    return () => {
      delete g.__bookFan;
    };
  }, [debugT, driver, clock]);
  const mirror = (s: string) => {
    debugState = s;
  };

  const frameStep = (since: number) => {
    'worklet';
    const dt = Math.min(since / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const d = driver.get();
    if (debugT.get() >= 0) {
      d.mode = 2;
      d.t = debugT.get();
      d.amb = debugT.get();
      d.last = now;
    }
    const before = d.t;
    const t = stepDriver(d, debugT.get() >= 0 ? 0 : dt, now, auto);
    if (d.mode === 1 && d.rate > 0 && before < T.sheet + 0.5 && t >= T.sheet + 0.5 && t < T.close) scheduleOnRN(buzz, 0);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} mode=${d.mode} stop=${d.stop} drag=${d.drag.toFixed(1)} scroll=${d.scroll.toFixed(1)} tg=${target.get()}`);
    }
    live.set({ amb: d.amb, scroll: d.scroll, drag: d.drag, cta: d.ctaP, press: d.pressP });
    time.set(t);
  };
  const frameCb = useFrameCallback((info) => {
    frameStep(info.timeSincePreviousFrame ?? 16);
  }, false);

  useEffect(() => {
    frameCb.setActive(ready && active && !reduced);
    return () => frameCb.setActive(false);
  }, [ready, active, reduced, frameCb]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    if (res && reduced) {
      const k = fade.get();
      const still = { amb: 0, scroll: 0, drag: 0, cta: 0, press: 0 };
      drawScene(c, res, geo, frameAt(0), still);
      if (k > 0.001) {
        const p = Skia.Paint();
        p.setAlphaf(k);
        c.saveLayer(p);
        drawScene(c, res, geo, frameAt(PEAK), still);
        c.restore();
      }
    } else if (res) drawScene(c, res, geo, frameAt(time.get()), live.get());
    else c.drawColor(Skia.Color(C.page));
    return recorder.finishRecordingAsPicture();
  }, [res, geo, reduced]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  // ---- touch: the page hangs from the top, the sheet stands on the bottom edge
  const start = useSharedValue({ x: 0, y: 0 });
  const moved = useSharedValue(0);
  const lastT = useSharedValue({ x: 0, y: 0 });
  const toggleReduced = (toSheet: boolean) => {
    'worklet';
    fade.set(withTiming(toSheet ? 1 : 0, { duration: 220, reduceMotion: ReduceMotion.Never }));
  };

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const now = clock.get();
      const x = e.x / geo.s;
      const y = e.y / geo.s;
      const ys = (e.y - geo.dy) / geo.s; // in the sheet's space
      start.set({ x, y });
      moved.set(0);
      lastT.set({ x: 0, y: 0 });
      const showingHome = reduced ? fade.get() < 0.5 : inHome(d.t);
      const showingSheet = reduced ? fade.get() >= 0.5 : inSheet(d.t) || (d.t >= PEAK && d.t < T.close);
      let tg: Target = 0;
      const si = A.signIn;
      if (showingHome) {
        if (x > si.x - 8 && x < si.x + si.w + 8 && y > si.y - 10 && y < si.y + si.h + 10) tg = 2;
        else if (y < 300) tg = 1;
      } else if (showingSheet) {
        const b = B.cta;
        if (x > b.x && x < b.x + b.w && ys > b.y && ys < b.y + b.h) tg = 4;
        else if (ys > B.sheet.y) tg = 3;
        else tg = 5;
      }
      target.set(tg);
      if (reduced) return;
      touched(d, now);
      if (tg === 2) d.pressAim = 1;
      if (tg === 4) d.ctaAim = 1;
      if (tg === 1 || tg === 3) d.dragging = 1;
    })
    .onUpdate((e) => {
      if (reduced) return;
      const d = driver.get();
      const tg = target.get();
      d.last = clock.get();
      if (Math.hypot(e.translationX, e.translationY) > 8) moved.set(1);
      if (tg === 1) {
        // the wall scrolls along its columns with the finger
        const rad = (WALL.angle * Math.PI) / 180;
        const l = lastT.get();
        const along = (-Math.sin(rad) * (e.translationX - l.x) + Math.cos(rad) * (e.translationY - l.y)) / geo.s;
        lastT.set({ x: e.translationX, y: e.translationY });
        d.scroll += along;
      } else if (tg === 3) {
        // the sheet follows the finger down, and resists going up
        d.drag = e.translationY / geo.s;
      }
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      const tg = target.get();
      const tap = moved.get() === 0;
      target.set(0);
      d.pressAim = 0;
      d.ctaAim = 0;
      d.dragging = 0;
      d.last = now;
      if (reduced) {
        if (tap && tg === 2) toggleReduced(true);
        else if (tap && (tg === 5 || tg === 3)) toggleReduced(false);
        return;
      }
      if (tg === 1 && !tap) {
        const rad = (WALL.angle * Math.PI) / 180;
        d.scrollV = (-Math.sin(rad) * e.velocityX + Math.cos(rad) * e.velocityY) / geo.s;
      } else if (tg === 2 && tap) {
        openSheet(d);
        scheduleOnRN(cue, 'flight');
        scheduleOnRN(buzz, 0);
      } else if (tg === 3) {
        const v = e.velocityY / geo.s;
        if (d.drag > 110 || v > 900) {
          if (closeSheet(d)) {
            scheduleOnRN(cue, 'flight');
            scheduleOnRN(buzz, 0);
          }
        }
        d.dragV = v;
      } else if (tg === 5 && tap) {
        if (closeSheet(d)) scheduleOnRN(cue, 'flight');
      } else if (tg === 4 && tap) {
        scheduleOnRN(cue, 'tap');
        scheduleOnRN(buzz, 1);
      }
    });

  const toggleForAccessibility = () => {
    scheduleOnUI(() => {
      if (reduced) {
        toggleReduced(fade.get() < 0.5);
        return;
      }
      const d = driver.get();
      touched(d, clock.get());
      if (inHome(d.t)) openSheet(d);
      else if (!closeSheet(d)) {
        d.mode = 1;
        d.rate = 1;
        d.stop = d.t < PEAK ? PEAK : LOOP;
      }
    });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <GestureDetector gesture={touch}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Which book is on your mind? Sign in."
          accessibilityHint="Opens the sign-in sheet, or closes it when it is open."
          onAccessibilityTap={toggleForAccessibility}
        >
          {size ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.page },
});
