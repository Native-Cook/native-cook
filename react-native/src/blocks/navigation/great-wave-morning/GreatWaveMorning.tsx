import { Canvas, Picture, Skia, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { SensorType, useAnimatedSensor, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, makeRes, type Res } from './gwm-draw';
import { type Driver, type Frame, frameAt, frameOf, makeDriver, PULL, rubber, stepDriver, takeOver } from './gwm-motion';
import { C, DY, MAX_SCROLL, REF_W, S, UI } from './gwm-theme';
import { type Cue, useGwmAudio } from './use-gwm-audio';

/*
 Great Wave Morning — the home screen of a team's task app, under a cut-paper sea.

 Look (the pin, an app screen): "Good morning, Yuna" over a sea of layered paper waves, a winding river
 with a paper boat, cream clouds and curling white crests; three stat cards with a paper wave in their
 corner, "Today's Focus" with five tasks (a coloured edge, a time and a status pill), and a tab bar.

 Motion (ours): the sea is alive and answers the scroll. Its eight layers drift at their own pace, the
 boat bobs and rocks, the crests roll. Pull the page down and the sea drops with it and swells, the
 crests rear up; let go and it springs back while the crests throw foam over the page. Scroll up and
 the whole sea folds, layer into layer, into one thin wave under the greeting (the boat sails on it);
 scroll back and it unfolds.

 autoPlay: LOOP = 9 s (gwm-motion.ts); frame 9 s equals frame 0.
 Touch: drag the page (rubber band past the top, momentum, springs back past the ends); a pull of more
 than 60 px makes a splash on release; tap a task to mark it done (its edge turns green, its title is
 struck through, its pill flips to Done); tap a tab and the tile slides to it. 2 s after the last touch
 the page goes back to the top and the loop resumes.
 Reduced motion: the sea stands still and only folds with the scroll; no foam.
*/

type Props = { autoPlay?: boolean; previewTime?: number };

let debugState = '';

export function GreatWaveMorning({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const regular = useFont(require('./assets/fonts/Manrope_400Regular.ttf'), 100);
  const medium = useFont(require('./assets/fonts/Manrope_500Medium.ttf'), 100);
  const semi = useFont(require('./assets/fonts/Manrope_600SemiBold.ttf'), 100);
  const bold = useFont(require('./assets/fonts/Manrope_700Bold.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!regular || !medium || !semi || !bold) return null;
    const f = { regular: regular.getTypeface(), medium: medium.getTypeface(), semi: semi.getTypeface(), bold: bold.getTypeface() };
    if (!f.regular || !f.medium || !f.semi || !f.bold) return null;
    return makeRes({ regular: f.regular, medium: f.medium, semi: f.semi, bold: f.bold });
  }, [regular, medium, semi, bold]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? 874;
  const geo = useMemo<Geo>(() => ({ w, h, s: w / REF_W }), [w, h]);

  const play = useGwmAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const frame = useSharedValue<Frame>(frameAt(0));
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(previewTime ?? 0));
    clock.set(0);
  }, [auto, reduced, ready, previewTime, driver, clock]);

  // Debug (development builds only): `globalThis.__greatWave.at(3.5)` pins the loop, `.play()`
  // resumes, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __greatWave?: unknown };
    g.__greatWave = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__greatWave;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  const rotation = useAnimatedSensor(SensorType.ROTATION, { interval: 'auto' });
  const gyro = useSharedValue({ init: 0, pitch: 0, roll: 0, x: 0, y: 0 });

  const frameStep = (since: number) => {
    'worklet';
    const dt = Math.min(since / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    // the paper layers answer the phone's tilt, around wherever it is held (a slow baseline)
    const r = rotation.sensor.get();
    const gy = gyro.get();
    if (!gy.init) {
      gy.init = 1;
      gy.pitch = r.pitch;
      gy.roll = r.roll;
    }
    const kb = Math.min(1, dt * 0.6);
    gy.pitch += (r.pitch - gy.pitch) * kb;
    gy.roll += (r.roll - gy.roll) * kb;
    const ks = 1 - Math.exp(-dt * 8);
    gy.x += (Math.max(-1, Math.min(1, (r.roll - gy.roll) / 0.35)) - gy.x) * ks;
    gy.y += (Math.max(-1, Math.min(1, (r.pitch - gy.pitch) / 0.35)) - gy.y) * ks;
    const d = driver.get();
    if (debugT.get() >= 0) {
      d.mode = 0;
      d.t = debugT.get();
      d.amb = debugT.get();
    } else stepDriver(d, dt, now, auto);
    const f = frameOf(d, now);
    if (reduced) {
      f.amb = 0;
      f.foam = -1;
    } else {
      f.tiltX = gy.x;
      f.tiltY = gy.y;
    }
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${d.t.toFixed(2)} mode=${d.mode} s=${d.s.toFixed(1)} v=${d.v.toFixed(0)} done=${d.done.map((x) => (x ? 1 : 0)).join('')} tab=${d.tab}`);
    }
    frame.set(f);
  };
  const frameCb = useFrameCallback((tick) => {
    // a frame that throws must not stop the callback for good
    try {
      frameStep(tick.timeSincePreviousFrame ?? 16);
    } catch (err) {
      if (__DEV__) scheduleOnRN(mirror, `error ${String(err)}`);
    }
  }, false);

  useEffect(() => {
    frameCb.setActive(ready && active);
    return () => frameCb.setActive(false);
  }, [ready, active, frameCb]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    if (res) drawScene(c, res, geo, frame.get());
    else c.drawColor(Skia.Color(C.page));
    return recorder.finishRecordingAsPicture();
  }, [res, geo]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  const startRaw = useSharedValue(0);
  const surged = useSharedValue(0);

  const toPin = (x: number, y: number) => {
    'worklet';
    return { x: x / geo.s / S, y: (y / geo.s - DY) / S };
  };

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      d.v = 0;
      d.raw = d.s;
      startRaw.set(d.s);
      surged.set(0);
      // a task under the finger presses in
      const p = toPin(e.x, e.y);
      const R = UI.rows;
      const cy = p.y + d.s;
      d.pressRow = -1;
      if (p.y < UI.tab.y && p.x > R.x && p.x < R.x + R.w) {
        for (let i = 0; i < 5; i++) {
          const y = R.y0 + i * R.pitch;
          if (cy > y && cy < y + R.h && p.y > (d.s > 0 ? 170 : 520)) {
            d.pressRow = i;
            d.pressLast = i;
            d.pressAt = now;
          }
        }
      }
    })
    .onUpdate((e) => {
      const d = driver.get();
      const now = clock.get();
      d.last = now;
      const dy = e.translationY / geo.s / S;
      if (d.dragging === 0 && Math.abs(dy) > 8) {
        d.dragging = 1;
        if (d.pressRow >= 0) {
          d.pressRow = -1;
          d.releaseRowAt = now;
        }
      }
      if (d.dragging === 0) return;
      const raw = startRaw.get() - dy;
      d.raw = raw;
      d.s = raw < 0 ? -rubber(-raw) : raw > MAX_SCROLL ? MAX_SCROLL + rubber(raw - MAX_SCROLL) : raw;
      if (d.s < -60 && surged.get() === 0) {
        surged.set(1);
        scheduleOnRN(cue, 'surge');
        scheduleOnRN(buzz, 0);
      }
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      d.last = now;
      if (d.dragging === 1) {
        d.dragging = 0;
        d.v = Math.max(-4000, Math.min(4000, -e.velocityY / geo.s / S));
        if (d.s < -60 && !reduced) {
          d.releaseAt = now;
          d.foamPow = Math.min(1.2, -d.s / PULL);
          scheduleOnRN(cue, 'splash');
          scheduleOnRN(buzz, 1);
        }
        return;
      }
      const p = toPin(e.x, e.y);
      if (d.pressRow >= 0) {
        const i = d.pressRow;
        d.pressRow = -1;
        d.releaseRowAt = now;
        d.done[i] = !d.done[i];
        d.doneAt[i] = reduced ? now - 1 : now;
        scheduleOnRN(cue, 'tap');
        scheduleOnRN(buzz, d.done[i] ? 0 : 2);
        return;
      }
      if (p.y > UI.tab.y) {
        for (let i = 0; i < 5; i++) {
          if (Math.abs(p.x - UI.tab.x[i]) < 70 && d.tab !== i) {
            d.tab = i;
            if (reduced) d.tabPos = i;
            scheduleOnRN(cue, 'tap');
            scheduleOnRN(buzz, 2);
          }
        }
      }
    });

  // VoiceOver: a double tap marks the next task done.
  const markNext = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      for (let i = 0; i < 5; i++) {
        if (!d.done[i]) {
          d.done[i] = true;
          d.doneAt[i] = now;
          return;
        }
      }
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
          accessibilityLabel="Good morning, Yuna. Sprint 8, 14 tasks due this week. 38 open, 6 due today, 94 done this sprint. Today's focus: Review new component specs with dev, 09:30, due now."
          accessibilityHint="Marks the next task done."
          onAccessibilityTap={markNext}
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
  screen: { flex: 1, backgroundColor: C.sky },
});
