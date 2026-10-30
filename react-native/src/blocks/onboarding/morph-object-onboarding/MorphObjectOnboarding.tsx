import { Canvas, Picture, Skia, useFont, useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withTiming, ReduceMotion } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, type Live, makeRes, type Res } from './moo-draw';
import { arrivalAt, band, type Driver, goTo, grab, makeDriver, release, stepDriver } from './moo-motion';
import { C, MORPH, REF_H, REF_W, UI } from './moo-theme';
import { type Cue, useMooAudio } from './use-moo-audio';

/*
 Morph Object Onboarding — three onboarding pages of an invented language-learning app.

 Look (the pin, three stills): a near-black screen with a bright 3D object — a clock with book
 pages fanned out of it, a laptop with stickers and two balls, an open book with someone reading
 behind it — over a white card with a two-line title, a grey line, and a black pill button.

 Motion (ours): paging morphs the object itself, frame by frame under the finger. The fanned
 pages fold together into the laptop's lid and base, the clock's face rounds into the yellow ball,
 its knob into the pink one, and the stickers pop onto the lid; then lid and base swing open into
 the book's orange covers with cream pages, the ball darkens into the reader's head and the reader
 rises behind it. 33 frames rendered in Blender (sharp renders at rest, a small atlas in between,
 cross-faded). The words slide in from where their page lies and come into focus, the dots grow
 and shrink, a coloured light behind the object turns from yellow to violet to orange, sparkles
 burst when a page lands, and the button becomes "Let's start" on the last page.

 autoPlay: LOOP = 9 s (moo-motion.ts): hold, morph, hold, morph, hold, rewind; frame 9 s = frame 0.
 Touch: drag sideways anywhere to scrub the morph (rubber band past the ends; a flick turns one
 page with the finger's speed); Continue Now goes to the next page, Skip runs to the last; tap the
 object for a hop. 2 s after the last touch the loop resumes from that page.
 Reduced motion: no morph in between: pages change with a 200 ms cross-fade.
*/

type Props = { autoPlay?: boolean; previewTime?: number };
type Target = 0 | 1 | 2 | 3 | 4; // none, pager, button, skip, object (tap) / pager (drag)

let debugState = '';

export function MorphObjectOnboarding({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const atlas = useImage(require('./assets/morph-atlas.webp'));
  const k0 = useImage(require('./assets/morph-clock.webp'));
  const k1 = useImage(require('./assets/morph-laptop.webp'));
  const k2 = useImage(require('./assets/morph-book.webp'));
  const regular = useFont(require('./assets/fonts/Poppins-Regular.ttf'), 100);
  const medium = useFont(require('./assets/fonts/Poppins-Medium.ttf'), 100);
  const bold = useFont(require('./assets/fonts/Poppins-Bold.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!atlas || !k0 || !k1 || !k2 || !regular || !medium || !bold) return null;
    const f = { regular: regular.getTypeface(), medium: medium.getTypeface(), bold: bold.getTypeface() };
    if (!f.regular || !f.medium || !f.bold) return null;
    return makeRes({ regular: f.regular, medium: f.medium, bold: f.bold }, atlas, [k0, k1, k2]);
  }, [atlas, k0, k1, k2, regular, medium, bold]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? REF_H;
  const geo = useMemo<Geo>(() => {
    const s = w / REF_W;
    return { w, h, s, dy: h - REF_H * s };
  }, [w, h]);

  const play = useMooAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const live = useSharedValue<Live>({ amb: 0, p: 0, arrive: -1, hop: -1, press: 0 });
  const fadePage = useSharedValue(0); // reduced motion: the page position, faded between whole pages
  const target = useSharedValue<Target>(0);
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(previewTime ?? 0));
    clock.set(0);
  }, [auto, reduced, ready, previewTime, driver, clock]);

  // Debug (development builds only): `globalThis.__morphObject.at(2.0)` pins the loop, `.play()`
  // resumes, `.page(p)` parks the pager at a page position, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __morphObject?: unknown };
    g.__morphObject = {
      at: (t: number) => debugT.set(t),
      play: () => debugT.set(-1),
      state: () => debugState,
      page: (p: number) =>
        scheduleOnUI(() => {
          const d = driver.get();
          d.mode = 2;
          d.p = p;
          d.v = 0;
          d.aim = -1;
          d.last = clock.get() + 60;
        }),
    };
    return () => {
      delete g.__morphObject;
    };
  }, [debugT, driver, clock]);
  const mirror = (s: string) => {
    debugState = s;
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const d = driver.get();
    if (debugT.get() >= 0) {
      d.mode = 0;
      d.t = debugT.get();
      d.amb = debugT.get();
    }
    const before = d.p;
    const out = stepDriver(d, dt, now, auto && debugT.get() < 0);
    if (out.arrived >= 0) {
      d.arriveAt = now;
      scheduleOnRN(cue, 'pop');
      scheduleOnRN(buzz, 2);
    }
    // a finger's page crossing a whole page: one tick of the dots
    if (d.mode === 2 && d.aim < 0 && Math.floor(before + 0.5) !== Math.floor(d.p + 0.5)) scheduleOnRN(buzz, 2);
    const loopArrive = d.mode === 0 ? arrivalAt(d.t) : -1;
    const arrive = d.arriveAt >= 0 && now - d.arriveAt < 1 ? now - d.arriveAt : loopArrive;
    const hop = d.hopAt >= 0 && now - d.hopAt < 1.2 ? now - d.hopAt : -1;
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${d.t.toFixed(2)} mode=${d.mode} p=${d.p.toFixed(3)} v=${d.v.toFixed(2)} aim=${d.aim} tg=${target.get()}`);
    }
    live.set({ amb: d.amb, p: out.p, arrive, hop, press: d.press });
  }, false);

  useEffect(() => {
    frameCb.setActive(ready && active && !reduced);
    return () => frameCb.setActive(false);
  }, [ready, active, reduced, frameCb]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    if (res && reduced) {
      // whole pages only, cross-faded
      const p = fadePage.get();
      const lo = Math.floor(p);
      const k = p - lo;
      drawScene(c, res, geo, { amb: 0, p: lo, arrive: -1, hop: -1, press: 0 });
      if (k > 0.001) {
        const pt = Skia.Paint();
        pt.setAlphaf(k);
        c.saveLayer(pt);
        drawScene(c, res, geo, { amb: 0, p: Math.min(2, lo + 1), arrive: -1, hop: -1, press: 0 });
        c.restore();
      }
    } else if (res) drawScene(c, res, geo, live.get());
    else c.drawColor(Skia.Color(C.bg));
    return recorder.finishRecordingAsPicture();
  }, [res, geo, reduced]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  // ---- touch (reference space: scaled to the width, standing on the bottom edge)
  const start = useSharedValue({ x: 0, y: 0 });
  const moved = useSharedValue(0);
  const inButton = (x: number, y: number, pad: number) => {
    'worklet';
    const b = UI.button;
    return x >= b.x - pad && x <= b.x + b.w + pad && y >= b.y - pad && y <= b.y + b.h + pad;
  };
  const reducedGo = (page: number) => {
    'worklet';
    fadePage.set(withTiming(Math.max(0, Math.min(2, page)), { duration: 200, reduceMotion: ReduceMotion.Never }));
  };

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const x = e.x / geo.s;
      const y = (e.y - geo.dy) / geo.s;
      start.set({ x, y });
      moved.set(0);
      let tg: Target = 1;
      if (inButton(x, y, 6)) tg = 2;
      else if (Math.abs(x - UI.skip.x + 16) < 34 && Math.abs(y - UI.skip.y) < 24) tg = 3;
      else {
        const b = MORPH.box;
        if (x > b.x + 40 && x < b.x + b.w - 40 && y > b.y + 30 && y < b.y + b.h - 30) tg = 4;
      }
      target.set(tg);
      if (reduced) return;
      if (tg === 2) {
        d.pressAim = 1;
        d.last = clock.get();
      } else if (tg === 1 || tg === 4) grab(d, clock.get());
    })
    .onUpdate((e) => {
      if (reduced) return;
      const tg = target.get();
      if (tg !== 1 && tg !== 4) return;
      const d = driver.get();
      d.last = clock.get();
      if (Math.abs(e.translationX) > 8) moved.set(1);
      // a full swipe across the screen is one page
      d.p = band(d.dragFrom - e.translationX / geo.s / (REF_W * 0.85));
      d.v = 0;
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      const tg = target.get();
      const x = e.x / geo.s;
      const y = (e.y - geo.dy) / geo.s;
      const tap = Math.hypot(x - start.get().x, y - start.get().y) < 10 && moved.get() === 0;
      target.set(0);
      d.pressAim = 0;
      d.last = now;
      if (reduced) {
        const cur = Math.round(fadePage.get());
        if (tap && tg === 2) reducedGo(cur + 1 > 2 ? 0 : cur + 1);
        else if (tap && tg === 3) reducedGo(2);
        else if (!tap && (tg === 1 || tg === 4)) reducedGo(cur + (e.translationX < 0 ? 1 : -1));
        return;
      }
      if (tg === 2 && tap && inButton(x, y, 12)) {
        const cur = Math.round(d.mode === 0 ? d.p : d.aim >= 0 ? d.aim : d.p);
        goTo(d, cur >= 2 ? 0 : cur + 1, now);
        scheduleOnRN(cue, 'morph');
        scheduleOnRN(buzz, 0);
      } else if (tg === 3 && tap) {
        goTo(d, 2, now, true);
        scheduleOnRN(cue, 'morph');
        scheduleOnRN(buzz, 0);
      } else if ((tg === 1 || tg === 4) && !tap) {
        const vPages = -e.velocityX / geo.s / (REF_W * 0.85);
        const from = Math.round(d.dragFrom);
        release(d, vPages);
        if (d.aim !== from) scheduleOnRN(cue, 'morph');
      } else if (tg === 4 && tap) {
        d.hopAt = now;
        d.aim = Math.round(d.p);
        scheduleOnRN(buzz, 0);
      } else if (tg === 1) d.aim = Math.round(d.p);
    });

  const nextForAccessibility = () => {
    scheduleOnUI(() => {
      if (reduced) {
        const cur = Math.round(fadePage.get());
        reducedGo(cur + 1 > 2 ? 0 : cur + 1);
        return;
      }
      const d = driver.get();
      const cur = Math.round(d.p);
      goTo(d, cur >= 2 ? 0 : cur + 1, clock.get());
    });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={touch}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Onboarding, three pages: We really value your time. You will learn online. You will learn correctly."
          accessibilityHint="Goes to the next page."
          onAccessibilityTap={nextForAccessibility}
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
  screen: { flex: 1, backgroundColor: C.bg },
});
