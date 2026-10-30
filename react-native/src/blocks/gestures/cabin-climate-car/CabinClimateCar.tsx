import { Canvas, Picture, Skia, useFont, useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, makeRes, type Res } from './ccc-draw';
import { type Driver, type Frame, frameAt, frameOf, makeDriver, setTemp, stepDriver, takeOver, toggleLeg } from './ccc-motion';
import { C, REF_W, S, UI } from './ccc-theme';
import { type Cue, useCccAudio } from './use-ccc-audio';

/*
 Cabin Climate Car — the cabin controls of a robotaxi app.

 Look (the pin, an app screen): a lavender-blue wash, an "RL Edit" pill over the car, "Cabin controls"
 with a tall temperature card (+, 70°, −), a "Distributed legroom" card with two seats and green bars,
 an "Auto" fan card, then Preferences and a floating tab bar. The car is our own, an invented
 robotaxi with a sensor dome, modelled and rendered in Blender for this block.

 Motion (ours): the cabin's air is visible. Streaks of air run over the car's cabin, from the
 windscreen round to the back, in the colour of the temperature: teal at 70°, cooling to blue, warming
 to orange, with a glow in the cabin, a halo behind the car and a wash at the foot of the temperature
 card; the air follows the setting a little behind it. Each degree rolls in (up when warmer, down when
 cooler) as + or − presses in. Extra rear legroom slides the front seat forward and the rear one back,
 the bars showing the room. The fan spins at its mode's speed.

 autoPlay: LOOP = 9 s (ccc-motion.ts); frame 9 s equals frame 0.
 Touch: tap + or − (hold to repeat), or drag up and down on the temperature card, a click and a
 selection haptic per degree; tap the legroom card; tap the fan card for Auto, Low and High; the pill
 presses. 2 s after the last touch it goes back to 70° a degree at a time, the seats come back and the
 loop resumes.
 Reduced motion: no streaks; changes land at once.
*/

type Props = { autoPlay?: boolean; previewTime?: number };

let debugState = '';

export function CabinClimateCar({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const car = useImage(require('./assets/car.webp'));
  const regular = useFont(require('./assets/fonts/Inter-Regular.ttf'), 100);
  const medium = useFont(require('./assets/fonts/Inter-Medium.ttf'), 100);
  const semi = useFont(require('./assets/fonts/Inter-SemiBold.ttf'), 100);
  const bold = useFont(require('./assets/fonts/Inter-Bold.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!car || !regular || !medium || !semi || !bold) return null;
    const f = { regular: regular.getTypeface(), medium: medium.getTypeface(), semi: semi.getTypeface(), bold: bold.getTypeface() };
    if (!f.regular || !f.medium || !f.semi || !f.bold) return null;
    return makeRes({ regular: f.regular, medium: f.medium, semi: f.semi, bold: f.bold }, car);
  }, [car, regular, medium, semi, bold]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? 874;
  const geo = useMemo<Geo>(() => ({ w, h, s: w / REF_W }), [w, h]);

  const play = useCccAudio(!reduced);
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

  // Debug (development builds only): `globalThis.__cabinClimate.at(4.4)` pins the loop, `.play()`
  // resumes, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __cabinClimate?: unknown };
    g.__cabinClimate = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__cabinClimate;
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
    } else if (stepDriver(d, dt, now, auto) === 1) scheduleOnRN(tick);
    const f = frameOf(d, now);
    if (reduced) {
      f.amb = 0;
      f.tempAt = 10;
      f.heat = f.temp;
      f.leg = f.legOn ? 1 : 0;
    }
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${d.t.toFixed(2)} mode=${d.mode} temp=${f.temp} heat=${f.heat.toFixed(1)} leg=${f.legOn ? 1 : 0} fan=${f.fan}`);
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

  const start = useSharedValue({ y: 0, temp: 70 });
  const inRect = (x: number, y: number, k: { x: number; y: number; w: number; h: number }) => {
    'worklet';
    return x > k.x && x < k.x + k.w && y > k.y && y < k.y + k.h;
  };

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      const x = e.x / geo.s / S;
      const y = e.y / geo.s / S;
      d.released = 0;
      const T = UI.temp;
      start.set({ y, temp: d.temp });
      if (inRect(x, y, T)) {
        if (Math.abs(y - T.plus) < 70) d.pressing = 1;
        else if (Math.abs(y - T.minus) < 70) d.pressing = 2;
        else d.pressing = 6;
      } else if (inRect(x, y, UI.legroom)) d.pressing = 3;
      else if (inRect(x, y, UI.fan)) d.pressing = 4;
      else if (Math.abs(x - UI.pill.x) < UI.pill.w / 2 && Math.abs(y - UI.pill.y) < UI.pill.h / 2 + 10) d.pressing = 5;
      else d.pressing = 0;
      d.pressAt = now;
      d.repeatAt = now;
    })
    .onUpdate((e) => {
      const d = driver.get();
      const now = clock.get();
      d.last = now;
      const dy = e.translationY / geo.s / S;
      // a drag on the card scrubs the temperature, 36 px a degree
      if ((d.pressing === 1 || d.pressing === 2 || d.pressing === 6) && Math.abs(dy) > 18) d.pressing = 6;
      if (d.pressing === 6) {
        const v = Math.round(start.get().temp - dy / 36);
        if (setTemp(d, v, reduced ? now - 2 : now)) scheduleOnRN(tick);
      }
    })
    .onFinalize(() => {
      const d = driver.get();
      const now = clock.get();
      d.last = now;
      const was = d.pressing;
      d.pressing = 0;
      d.released = was;
      d.releaseAt = now;
      if (was === 1 || was === 2) {
        // a tap is one degree (a hold has been stepping already)
        if (now - d.pressAt <= 0.45 && setTemp(d, d.temp + (was === 1 ? 1 : -1), reduced ? now - 2 : now)) scheduleOnRN(tick);
      } else if (was === 3) {
        toggleLeg(d, reduced ? now - 2 : now);
        scheduleOnRN(cue, 'slide');
        scheduleOnRN(buzz, 0);
      } else if (was === 4) {
        d.fan = (d.fan + 1) % 3;
        d.fanAt = now;
        scheduleOnRN(cue, 'air');
        scheduleOnRN(buzz, 2);
      } else if (was === 5) {
        scheduleOnRN(cue, 'tap');
        scheduleOnRN(buzz, 0);
      }
    });

  // VoiceOver: a double tap warms the cabin a degree.
  const warmer = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      const now = clock.get();
      takeOver(d, now);
      setTemp(d, d.temp + 1, now);
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
          accessibilityLabel="Cabin controls. Temperature 70 degrees. Distributed legroom. Fan auto."
          accessibilityHint="Double tap to warm the cabin a degree."
          onAccessibilityTap={warmer}
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
