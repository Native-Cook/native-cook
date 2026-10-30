import { Canvas, Picture, Skia, useFont, useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { ReduceMotion, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, type Live, makeRes, type Res } from './isg-draw';
import { choose, columnPose, type Driver, dragTo, type Frame, makeDriver, release, stepDriver, takeOver } from './isg-motion';
import { C, COLUMNS, GAUGE, REF_H, REF_W, UI } from './isg-theme';
import { type Cue, useIsgAudio } from './use-isg-audio';

/*
 Isometric Score Gauge — the score screen of an invented credit coach, "outlook".

 Look (the pin, a still from a video): a credit score of 765 in chrome digits inside a dial that
 runs red to green, "MAKE A CHOICE. SEE WHERE IT TAKES YOU", and a curved carousel of dark
 scenario cards with isometric objects (a card, a car, a house, a money bag, a paid statement and
 a cut card), the cards at its edges turning away.

 Motion (ours): choosing a scenario previews where it takes the score. The carousel turns one
 column (cards flatten as they reach the middle and turn away at the edges), "pay off my credit
 card" lifts and glows green and the score rolls up to 791 like an odometer while the dial's arc
 grows; then "take a new home loan" glows orange and the score rolls down to 743; then all back.

 autoPlay: LOOP = 9 s (isg-motion.ts); frame 9 s equals frame 0.
 Touch: drag the carousel (it snaps to a column, with a rubber band at its ends); tap a card to
 choose it (tap again to let go): the score springs to the outcome with a little overshoot.
 2 s after the last touch, with nothing chosen, the loop resumes.
 Reduced motion: no carousel or rolling; a tap shows the outcome with a 200 ms fade.
*/

type Props = { autoPlay?: boolean; previewTime?: number };

let debugState = '';

export function IsometricScoreGauge({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const icons = useImage(require('./assets/icons.webp'));
  const serif = useFont(require('./assets/fonts/DMSerifDisplay-Regular.ttf'), 100);
  const display = useFont(require('./assets/fonts/ArchivoBlack-Regular.ttf'), 100);
  const medium = useFont(require('./assets/fonts/Inter-Medium.ttf'), 100);
  const semi = useFont(require('./assets/fonts/Inter-SemiBold.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!icons || !serif || !display || !medium || !semi) return null;
    const f = { serif: serif.getTypeface(), display: display.getTypeface(), medium: medium.getTypeface(), semi: semi.getTypeface() };
    if (!f.serif || !f.display || !f.medium || !f.semi) return null;
    return makeRes({ serif: f.serif, display: f.display, medium: f.medium, semi: f.semi }, icons);
  }, [icons, serif, display, medium, semi]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? REF_H;
  const geo = useMemo<Geo>(() => {
    const s = w / REF_W;
    return { w, h, s, dy: (h - REF_H * s) / 2 };
  }, [w, h]);

  const play = useIsgAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const frame = useSharedValue<Frame>({ scroll: 0, sel: -1, selW: 0, score: GAUGE.score, base: GAUGE.score });
  const live = useSharedValue<Live>({ amb: 0 });
  const pick = useSharedValue(-1); // reduced motion: the chosen card
  const pickFade = useSharedValue(0);
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(previewTime ?? 0));
    clock.set(0);
  }, [auto, reduced, ready, previewTime, driver, clock]);

  // Debug (development builds only): `globalThis.__scoreGauge.at(2.6)` pins the loop, `.play()`
  // resumes, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __scoreGauge?: unknown };
    g.__scoreGauge = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__scoreGauge;
    };
  }, [debugT]);
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
      d.mode = 0;
      d.t = debugT.get();
      d.amb = debugT.get();
      stepDriver(d, 0, now, false);
    } else stepDriver(d, dt, now, auto);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${d.t.toFixed(2)} mode=${d.mode} scroll=${d.scroll.toFixed(1)} sel=${d.sel} score=${d.score.toFixed(1)}`);
    }
    frame.set({ scroll: d.scroll, sel: d.sel, selW: d.selW, score: d.score, base: GAUGE.score });
    live.set({ amb: d.amb });
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
      const p = pick.get();
      const k = pickFade.get();
      const base = { scroll: 0, sel: -1, selW: 0, score: GAUGE.score, base: GAUGE.score };
      drawScene(c, res, geo, base, { amb: 0 });
      if (p >= 0 && k > 0.001) {
        const pt = Skia.Paint();
        pt.setAlphaf(k);
        c.saveLayer(pt);
        drawScene(c, res, geo, { ...base, sel: p, selW: 1, score: GAUGE.score + COLUMNS[Math.floor(p / 2)][p % 2].delta }, { amb: 0 });
        c.restore();
      }
    } else if (res) drawScene(c, res, geo, frame.get(), live.get());
    else c.drawColor(Skia.Color(C.bg));
    return recorder.finishRecordingAsPicture();
  }, [res, geo, reduced]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  const start = useSharedValue({ x: 0, y: 0, scroll: 0 });
  const moved = useSharedValue(0);
  const inGrid = useSharedValue(0);

  /** The card under a point (column · 2 + row), or −1. */
  const cardAt = (x: number, y: number, scroll: number) => {
    'worklet';
    const G = UI.grid;
    for (let i = 0; i < COLUMNS.length; i++) {
      const pose = columnPose(i, scroll);
      if (Math.abs(pose.ang) > 30) continue;
      if (Math.abs(x - pose.x) > G.w / 2) continue;
      for (let r = 0; r < 2; r++) if (y > G.rows[r] && y < G.rows[r] + G.h) return i * 2 + r;
    }
    return -1;
  };

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const x = e.x / geo.s;
      const y = (e.y - geo.dy) / geo.s;
      moved.set(0);
      inGrid.set(y > UI.grid.rows[0] - 20 ? 1 : 0);
      start.set({ x, y, scroll: d.scroll });
      if (reduced) return;
      takeOver(d, clock.get());
    })
    .onUpdate((e) => {
      if (reduced || inGrid.get() === 0) return;
      const d = driver.get();
      d.last = clock.get();
      if (Math.abs(e.translationX) > 8) moved.set(1);
      if (moved.get() === 1) dragTo(d, start.get().scroll, e.translationX / geo.s);
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      d.last = now;
      const s0 = start.get();
      if (reduced) {
        const idx = cardAt(s0.x, s0.y, 0);
        if (idx >= 0) {
          const same = pick.get() === idx && pickFade.get() > 0.5;
          if (!same) pick.set(idx);
          pickFade.set(withTiming(same ? 0 : 1, { duration: 200, reduceMotion: ReduceMotion.Never }));
        }
        return;
      }
      if (moved.get() === 1) {
        release(d, e.velocityX / geo.s);
        scheduleOnRN(buzz, 2);
        return;
      }
      const idx = cardAt(s0.x, s0.y, d.scroll);
      if (idx >= 0) {
        const delta = choose(d, idx);
        scheduleOnRN(cue, delta > 0 ? 'up' : 'pick');
        scheduleOnRN(cue, 'roll');
        scheduleOnRN(buzz, delta !== 0 ? 1 : 0);
      }
    });

  const chooseForAccessibility = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      takeOver(d, clock.get());
      choose(d, d.sel >= 0 ? d.sel : 0);
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
          accessibilityLabel="Credit score 765 out of 900. Make a choice, see where it takes you."
          accessibilityHint="Previews paying off your credit card."
          onAccessibilityTap={chooseForAccessibility}
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
