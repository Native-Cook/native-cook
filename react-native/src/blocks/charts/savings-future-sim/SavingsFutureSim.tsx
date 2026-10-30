import { Canvas, Picture, Skia, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, makeRes, rateFrac, type Res } from './sfs-draw';
import { type Driver, type Frame, frameAt, frameOf, makeDriver, setOpen, stepDriver, takeOver } from './sfs-motion';
import { C, PLAN, REF_W, S, UI } from './sfs-theme';
import { type Cue, useSfsAudio } from './use-sfs-audio';

/*
 Savings Future Sim — a savings account's waitlist screen with a rate gauge that becomes a forecast.

 Look (the pin, an app screen): dark navy, "Joined Waitlist", "You're on your way to the future of
 Savings.", a card with a half-circle gauge filled in violet to a knob, "6,00% Your Savings Rate",
 "See your savings grow." and a "Simulate My Future" button, then a "Skip the Waitlist" card with share
 buttons.

 Motion (ours): one path is both the gauge and the forecast. Simulate uncurls the arc from its left end
 into the growth curve of thirty years at that rate (its stroke thinning, a violet wash filling beneath
 it) while 6,00% crosses over to a balance; the knob becomes a scrubber that runs out along the years,
 the balance rolling up a digit at a time each year, the wash bright behind it and dim ahead. Then the
 curve curls back into the gauge.

 autoPlay: LOOP = 9 s (sfs-motion.ts); frame 9 s equals frame 0.
 Touch: tap the button to open or close the forecast; drag along the curve to scrub the years (a tick
 per year; letting go curls it back after a moment); on the gauge, drag the knob round to set the rate
 from 4,00 to 8,00 % (a tick per quarter point). 2 s after the last touch it goes back to 6,00 % and the
 loop resumes.
 Reduced motion: the gauge and the curve swap with a short crossfade-like morph only on a tap.
*/

type Props = { autoPlay?: boolean; previewTime?: number };

let debugState = '';

export function SavingsFutureSim({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const regular = useFont(require('./assets/fonts/Inter-Regular.ttf'), 100);
  const medium = useFont(require('./assets/fonts/Inter-Medium.ttf'), 100);
  const semi = useFont(require('./assets/fonts/Inter-SemiBold.ttf'), 100);
  const bold = useFont(require('./assets/fonts/Inter-Bold.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!regular || !medium || !semi || !bold) return null;
    const f = { regular: regular.getTypeface(), medium: medium.getTypeface(), semi: semi.getTypeface(), bold: bold.getTypeface() };
    if (!f.regular || !f.medium || !f.semi || !f.bold) return null;
    return makeRes({ regular: f.regular, medium: f.medium, semi: f.semi, bold: f.bold });
  }, [regular, medium, semi, bold]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? 874;
  const geo = useMemo<Geo>(() => ({ w, h, s: w / REF_W }), [w, h]);

  const play = useSfsAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const tick = () => {
    play('tick');
    buzz(2);
  };

  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const frame = useSharedValue<Frame>(frameAt(0));
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(previewTime ?? 0));
    clock.set(0);
  }, [auto, reduced, ready, previewTime, driver, clock]);

  // Debug (development builds only): `globalThis.__savingsSim.at(4.0)` pins the loop, `.play()`
  // resumes, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __savingsSim?: unknown };
    g.__savingsSim = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__savingsSim;
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
    } else stepDriver(d, dt, now, auto);
    const f = frameOf(d, now);
    if (reduced) {
      f.amb = 0;
      f.morph = f.open ? 1 : 0;
    }
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${d.t.toFixed(2)} mode=${d.mode} morph=${f.morph.toFixed(2)} year=${f.year.toFixed(1)} rate=${f.rate.toFixed(2)}`);
    }
    frame.set(f);
  };
  const frameCb = useFrameCallback((t) => {
    // a frame that throws must not stop the callback for good
    try {
      frameStep(t.timeSincePreviousFrame ?? 16);
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
    else c.drawColor(Skia.Color(C.bg));
    return recorder.finishRecordingAsPicture();
  }, [res, geo]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  const lastStep = useSharedValue(0);

  const toPin = (x: number, y: number) => {
    'worklet';
    return { x: x / geo.s / S, y: y / geo.s / S };
  };
  const yearAt = (x: number) => {
    'worklet';
    const Ch = UI.chart;
    return Math.max(1, Math.min(PLAN.years, ((x - Ch.x) / Ch.w) * PLAN.years));
  };
  const rateAt = (p: { x: number; y: number }) => {
    'worklet';
    const G = UI.gauge;
    let a = Math.atan2(p.y - G.y, p.x - G.x);
    if (a > Math.PI / 2) a -= 2 * Math.PI;
    const u = Math.max(0, Math.min(1, (a + Math.PI) / Math.PI));
    const rate = PLAN.minRate + (u - 0.1) / 0.125;
    return Math.max(PLAN.minRate, Math.min(PLAN.maxRate, Math.round(rate * 4) / 4));
  };

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      const p = toPin(e.x, e.y);
      const B = UI.button;
      const G = UI.gauge;
      d.pressing = 0;
      if (p.x > B.x && p.x < B.x + B.w && p.y > B.y - 8 && p.y < B.y + B.h + 8) {
        d.pressing = 1;
        d.pressAt = now;
      } else if (d.open && p.y > UI.chart.y - 80 && p.y < UI.chart.y + UI.chart.h + 50) {
        d.pressing = 2;
        d.closeAt = -10;
        d.year = yearAt(p.x);
        lastStep.set(Math.floor(d.year));
      } else if (!d.open) {
        const fr = rateFrac(d.rate);
        const th = Math.PI + Math.PI * fr;
        const kx = G.x + G.r * Math.cos(th);
        const ky = G.y + G.r * Math.sin(th);
        if (Math.hypot(p.x - kx, p.y - ky) < 70) {
          d.pressing = 3;
          lastStep.set(d.rate);
        }
      }
    })
    .onUpdate((e) => {
      const d = driver.get();
      d.last = clock.get();
      const p = toPin(e.x, e.y);
      if (d.pressing === 2) {
        d.year = yearAt(p.x);
        const yi = Math.floor(d.year);
        if (yi !== lastStep.get()) {
          lastStep.set(yi);
          scheduleOnRN(tick);
        }
      } else if (d.pressing === 3) {
        const r = rateAt(p);
        if (r !== lastStep.get()) {
          lastStep.set(r);
          d.rate = r;
          scheduleOnRN(tick);
        }
      }
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      d.last = now;
      const was = d.pressing;
      d.pressing = 0;
      if (was === 1) {
        d.releaseAt = now;
        const p = toPin(e.x, e.y);
        const B = UI.button;
        if (p.x < B.x - 20 || p.x > B.x + B.w + 20 || p.y < B.y - 30 || p.y > B.y + B.h + 30) return;
        setOpen(d, !d.open, reduced ? now - 2 : now);
        if (d.open) d.year = 1;
        scheduleOnRN(cue, d.open ? 'unfurl' : 'furl');
        scheduleOnRN(buzz, 0);
      } else if (was === 2) {
        // let go: the curve curls back into the gauge after a moment
        d.closeAt = now + 1.2;
      }
    });

  // VoiceOver: a double tap opens or closes the forecast.
  const toggle = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      setOpen(d, !d.open, now);
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
          accessibilityLabel="Joined waitlist. Your savings rate: 6 percent. See your savings grow. Simulate my future."
          accessibilityHint="Opens the forecast."
          onAccessibilityTap={toggle}
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
