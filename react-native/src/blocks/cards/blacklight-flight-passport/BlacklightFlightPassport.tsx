import { Canvas, Picture, Skia, useFont, useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { ReduceMotion, SensorType, useAnimatedSensor, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, type Live, makeRes, type Res } from './bfp-draw';
import { type Driver, frameAt, isUv, makeDriver, stepDriver, toggleUv, touched, UV_PEAK } from './bfp-motion';
import { LIGHT, REF_H, REF_W, UI } from './bfp-theme';
import { type Cue, useBfpAudio } from './use-bfp-audio';

/*
 Blacklight Flight Passport — the "all-time passport" of an invented flight tracker, Contrail.

 Look (the pin, two screenshots): a passport page with a dotted world map, every route flown as an
 arc between red airport dots, a row of flags, "MY CONTRAIL PASSPORT", 27 flights and the totals,
 a machine-readable zone, and a Blacklight button; under the blacklight the page turns violet and
 pink and the screen goes dark.

 Motion (ours): the routes lift off the map and fly again one after another, each with a small
 plane at its tip, the airports popping as they are reached, while every counter rolls up to its
 total. Then Blacklight: its light spreads out of the button over the whole screen, a glowing
 violet edge leading, and under it the hidden UV ink wakes up — fine security lines, a slowly
 turning rosette, a big plane watermark and a VERIFIED TRAVELLER stamp. Then the light falls back
 into the button.

 autoPlay: LOOP = 9 s (bfp-motion.ts); frame 9 s equals frame 0.
 Touch: tap Blacklight to switch the light on or off; drag a finger over the passport in daylight to
 shine a blacklight torch on it (the hidden ink shows only inside the spot). 2 s after the last
 touch the loop resumes.
 Reduced motion: no replay; Blacklight cross-fades the two looks in 220 ms.
*/

type Props = { autoPlay?: boolean; previewTime?: number };
type Target = 0 | 1 | 2; // none, button, torch

let debugState = '';

export function BlacklightFlightPassport({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [dark, setDark] = useState(false);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const map = useImage(require('./assets/map-dots.webp'));
  const regular = useFont(require('./assets/fonts/Inter-Regular.ttf'), 100);
  const medium = useFont(require('./assets/fonts/Inter-Medium.ttf'), 100);
  const semi = useFont(require('./assets/fonts/Inter-SemiBold.ttf'), 100);
  const bold = useFont(require('./assets/fonts/Inter-Bold.ttf'), 100);
  const mono = useFont(require('./assets/fonts/JetBrainsMono-Regular.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!map || !regular || !medium || !semi || !bold || !mono) return null;
    const f = { regular: regular.getTypeface(), medium: medium.getTypeface(), semi: semi.getTypeface(), bold: bold.getTypeface(), mono: mono.getTypeface() };
    if (!f.regular || !f.medium || !f.semi || !f.bold || !f.mono) return null;
    return makeRes({ regular: f.regular, medium: f.medium, semi: f.semi, bold: f.bold, mono: f.mono }, map);
  }, [map, regular, medium, semi, bold, mono]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? REF_H;
  const geo = useMemo<Geo>(() => {
    const s = w / REF_W;
    return { w, h, s, dy: (h - REF_H * s) / 2 };
  }, [w, h]);

  const play = useBfpAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const time = useSharedValue(0);
  const live = useSharedValue<Live>({ amb: 0, torch: 0, tx: 200, ty: 400, press: 0 });
  const fade = useSharedValue(0);
  const target = useSharedValue<Target>(0);
  const darkMirror = useSharedValue(0);
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(previewTime ?? 0));
    clock.set(0);
    time.set(previewTime ?? 0);
  }, [auto, reduced, ready, previewTime, driver, clock, time]);

  // Debug (development builds only): `globalThis.__passport.at(4.6)` pins the loop, `.play()`
  // resumes, `.park(t)` parks it for touch tests, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __passport?: unknown };
    g.__passport = {
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
      delete g.__passport;
    };
  }, [debugT, driver, clock]);
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
    // the hologram follows the phone's tilt around wherever it is held (a slow baseline)
    const rot = rotation.sensor.get();
    const gy = gyro.get();
    if (!gy.init) {
      gy.init = 1;
      gy.pitch = rot.pitch;
      gy.roll = rot.roll;
    }
    const kb = Math.min(1, dt * 0.6);
    gy.pitch += (rot.pitch - gy.pitch) * kb;
    gy.roll += (rot.roll - gy.roll) * kb;
    const ks = 1 - Math.exp(-dt * 8);
    gy.x += (Math.max(-1, Math.min(1, (rot.roll - gy.roll) / 0.35)) - gy.x) * ks;
    gy.y += (Math.max(-1, Math.min(1, (rot.pitch - gy.pitch) / 0.35)) - gy.y) * ks;
    const d = driver.get();
    if (debugT.get() >= 0) {
      d.mode = 2;
      d.t = debugT.get();
      d.amb = debugT.get();
      d.last = now;
    }
    const t = stepDriver(d, debugT.get() >= 0 ? 0 : dt, now, auto);
    // the status bar turns light under the blacklight (one call per crossing, not per frame)
    const isDark = frameAt(t).uv > 0.5 ? 1 : 0;
    if (isDark !== darkMirror.get()) {
      darkMirror.set(isDark);
      scheduleOnRN(setDark, isDark === 1);
    }
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} mode=${d.mode} stop=${d.stop} torch=${d.torch.toFixed(2)} tg=${target.get()}`);
    }
    live.set({ amb: d.amb, torch: d.torch, tx: d.tx, ty: d.ty, press: d.pressP, tiltX: reduced ? 0 : gy.x, tiltY: reduced ? 0 : gy.y });
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
      const still = { amb: 0, torch: 0, tx: 0, ty: 0, press: 0 };
      drawScene(c, res, geo, frameAt(0), still);
      if (k > 0.001) {
        const p = Skia.Paint();
        p.setAlphaf(k);
        c.saveLayer(p);
        drawScene(c, res, geo, frameAt(UV_PEAK), still);
        c.restore();
      }
    } else if (res) drawScene(c, res, geo, frameAt(time.get()), live.get());
    else c.drawColor(Skia.Color(LIGHT.bg));
    return recorder.finishRecordingAsPicture();
  }, [res, geo, reduced]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  const moved = useSharedValue(0);
  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const now = clock.get();
      const x = e.x / geo.s;
      const y = (e.y - geo.dy) / geo.s;
      moved.set(0);
      const b = UI.button;
      const cd = UI.card;
      let tg: Target = 0;
      if (x > b.x - 10 && x < b.x + b.w + 10 && y > b.y - 10 && y < b.y + b.h + 10) tg = 1;
      else if (x > cd.x - 10 && x < cd.x + cd.w + 10 && y > cd.y - 10 && y < cd.y + cd.h + 10) tg = 2;
      target.set(tg);
      if (reduced) return;
      touched(d, now);
      if (tg === 1) d.pressAim = 1;
      if (tg === 2 && !isUv(d.t)) {
        d.torchAim = 1;
        d.tx = x;
        d.ty = y - 40; // the spot sits just above the fingertip so the finger does not hide it
      }
    })
    .onUpdate((e) => {
      if (reduced) return;
      const d = driver.get();
      d.last = clock.get();
      if (Math.hypot(e.translationX, e.translationY) > 10) moved.set(1);
      if (target.get() === 2 && d.torchAim > 0) {
        d.tx = e.x / geo.s;
        d.ty = (e.y - geo.dy) / geo.s - 40;
      }
    })
    .onFinalize(() => {
      const d = driver.get();
      const now = clock.get();
      const tg = target.get();
      target.set(0);
      d.pressAim = 0;
      d.torchAim = 0;
      d.last = now;
      if (reduced) {
        if (tg === 1 && moved.get() === 0) fade.set(withTiming(fade.get() > 0.5 ? 0 : 1, { duration: 220, reduceMotion: ReduceMotion.Never }));
        return;
      }
      if (tg === 1 && moved.get() === 0) {
        const on = toggleUv(d);
        scheduleOnRN(cue, on ? 'on' : 'off');
        scheduleOnRN(buzz, on ? 1 : 0);
      }
    });

  const toggleForAccessibility = () => {
    scheduleOnUI(() => {
      if (reduced) {
        fade.set(withTiming(fade.get() > 0.5 ? 0 : 1, { duration: 220, reduceMotion: ReduceMotion.Never }));
        return;
      }
      const d = driver.get();
      touched(d, clock.get());
      toggleUv(d);
    });
  };

  return (
    <View style={[styles.screen, dark && styles.dark]} onLayout={onLayout}>
      <StatusBar style={dark ? 'light' : 'dark'} />
      <GestureDetector gesture={touch}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Your all-time passport: 27 flights, 128,714 kilometres, 7 days 1 hour in the air, 21 airports, 14 airlines."
          accessibilityHint="Switches the blacklight on or off."
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
  screen: { flex: 1, backgroundColor: LIGHT.bg },
  dark: { backgroundColor: '#090A2A' },
});
