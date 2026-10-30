import { Canvas, Picture, Skia, useFont, useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { ReduceMotion, SensorType, useAnimatedSensor, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, type Live, makeRes, type Res } from './gfw-draw';
import { boo, type Driver, frameAt, ghostExtra, liveHome, makeDriver, N_LIVE, stepDriver, touched } from './gfw-motion';
import { BODIES, C, FIELD_BOTTOM, REF_H, REF_W, UI } from './gfw-theme';
import { type Cue, useGfwAudio } from './use-gfw-audio';

/*
 Ghost Float Welcome — the welcome screen of an invented wallet app, "Wisp".

 Look (the pin, a still): a near-black screen with a white ghost, four coins and two collectible
 avatars floating among dark violet stars, white glints on the coins; "Welcome to Wisp", page
 dots, an "I agree to the Terms of Service" checkbox and a muted "Create a new wallet" button.

 Motion (ours): everything floats in zero gravity on closed paths (whole cycles of the loop), the
 ghost's hem ripples and its eyes look round. It draws a breath and says boo: a ring of air spreads
 and every coin, avatar and star is kicked away, spinning, reaching the far ones later, then
 springs home through a small overshoot. Later the checkbox is ticked, the button wakes from muted
 to lavender with a sheen, the ghost smiles ^ ^ and loops over, and the tokens lean toward the
 button. Then it all quietly undoes itself.

 autoPlay: LOOP = 9 s (timeline in gfw-motion.ts); frame 9 s equals frame 0.
 Touch: stir the air with a finger (things near it are carried along its path and swirl round it,
 bump into each other, and spring home); grab any coin, avatar or the ghost and throw it (it
 bounces off the edges); tap the ghost for a boo; tick the checkbox; "Create a new wallet" shakes
 its head until you have agreed, then the ghost loops over. The ghost's eyes follow your finger.
 On a phone the floating things also drift with its tilt (parallax by depth). 2 s after the last
 touch the loop resumes.
 Reduced motion: nothing floats; the checkbox and button change with a short fade.
*/

type Props = { autoPlay?: boolean; previewTime?: number };

type Target = 0 | 1 | 2 | 3 | 4; // none, stir, a body (grab / tap), checkbox, button

let debugState = '';

export function GhostFloatWelcome({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const dino = useImage(require('./assets/avatar-dino.webp'));
  const penguin = useImage(require('./assets/avatar-penguin.webp'));
  const regular = useFont(require('./assets/fonts/Manrope_400Regular.ttf'), 100);
  const medium = useFont(require('./assets/fonts/Manrope_500Medium.ttf'), 100);
  const semi = useFont(require('./assets/fonts/Manrope_600SemiBold.ttf'), 100);
  const bold = useFont(require('./assets/fonts/Manrope_700Bold.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!dino || !penguin || !regular || !medium || !semi || !bold) return null;
    const f = { regular: regular.getTypeface(), medium: medium.getTypeface(), semi: semi.getTypeface(), bold: bold.getTypeface() };
    if (!f.regular || !f.medium || !f.semi || !f.bold) return null;
    return makeRes({ regular: f.regular, medium: f.medium, semi: f.semi, bold: f.bold }, { dino, penguin });
  }, [dino, penguin, regular, medium, semi, bold]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? REF_H;
  const geo = useMemo<Geo>(() => {
    const s = w / REF_W;
    return { w, h, s, dy: (h - REF_H * s) / 2 };
  }, [w, h]);

  const play = useGfwAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else if (kind === 3) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning).catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const zeros = () => new Array<number>(N_LIVE).fill(0);
  const driver = useSharedValue<Driver>(makeDriver(0));
  const clock = useSharedValue(0);
  const time = useSharedValue(0);
  const live = useSharedValue<Live>({
    amb: 0,
    ox: zeros(),
    oy: zeros(),
    or: zeros(),
    lookX: 0,
    lookY: 0,
    puff: 0,
    ring: -1,
    wide: 0,
    roll: 0,
    hop: 0,
    agree: -1,
    btnPress: 0,
    no: 0,
    noP: -1,
    tiltX: 0,
    tiltY: 0,
  });
  // Reduced motion: the agreement changes with a short fade, nothing else moves.
  const fade = useSharedValue(0);
  const rotation = useAnimatedSensor(SensorType.ROTATION, { interval: 'auto' });
  const gyro = useSharedValue({ init: 0, pitch: 0, roll: 0, x: 0, y: 0 });
  const target = useSharedValue<Target>(0);
  const ready = res !== null && size !== null;

  useEffect(() => {
    const t0 = previewTime ?? 0;
    driver.set(makeDriver(t0));
    clock.set(0);
    time.set(t0);
  }, [auto, reduced, ready, previewTime, driver, clock, time]);

  // Debug (development builds only): `globalThis.__ghostFloat.at(1.9)` pins the timeline, `.play()`
  // resumes, `.park(t)` parks it for touch tests, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __ghostFloat?: unknown };
    g.__ghostFloat = {
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
      delete g.__ghostFloat;
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
      d.t = debugT.get();
      d.amb = debugT.get();
      time.set(d.t);
      live.set({ ...live.get(), amb: d.amb });
      return;
    }
    const t = stepDriver(d, dt, now, auto);
    const gx = ghostExtra(d, now);
    const r = rotation.sensor.get();
    const gy = gyro.get();
    if (gy.init === 0 && (r.pitch !== 0 || r.roll !== 0)) {
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
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} mode=${d.mode} tg=${target.get()} grab=${d.grab} ox0=${d.ox[0].toFixed(1)} ox1=${d.ox[1].toFixed(1)} vx1=${d.vx[1].toFixed(0)} agree=${d.agree.toFixed(2)} look=${d.lookX.toFixed(2)}`);
    }
    live.set({
      amb: d.amb,
      ox: d.ox.slice(),
      oy: d.oy.slice(),
      or: d.or.slice(),
      vx: d.vx.slice(),
      vy: d.vy.slice(),
      lookX: d.lookX,
      lookY: d.lookY,
      puff: gx.puff,
      ring: gx.ring,
      wide: gx.wide,
      roll: gx.roll,
      hop: gx.hop,
      agree: d.agree,
      btnPress: d.btnPress,
      no: gx.no,
      noP: gx.noP,
      tiltX: gy.x,
      tiltY: gy.y,
    });
    time.set(t);
  }, false);

  useEffect(() => {
    frameCb.setActive(ready && active && !reduced);
    return () => frameCb.setActive(false);
  }, [ready, active, reduced, frameCb]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    if (res && reduced) {
      const still = { ...live.get(), amb: 0, agree: fade.get(), ring: -1, puff: 0, wide: 0, roll: 0, hop: 0, no: 0, noP: -1, tiltX: 0, tiltY: 0 };
      drawScene(c, res, geo, frameAt(0), still);
    } else if (res) {
      drawScene(c, res, geo, frameAt(time.get()), live.get());
    } else c.drawColor(Skia.Color(C.bg));
    return recorder.finishRecordingAsPicture();
  }, [res, geo, reduced]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  // ---- touch, in the reference space (scaled to the width, centred vertically)
  const start = useSharedValue({ x: 0, y: 0, at: 0 });
  const moved = useSharedValue(0);
  const swished = useSharedValue(0);

  const inButton = (x: number, y: number, pad: number) => {
    'worklet';
    const b = UI.button;
    return x >= b.x - pad && x <= b.x + b.w + pad && y >= b.y - pad && y <= b.y + b.h + pad;
  };
  const onCheck = (x: number, y: number) => {
    'worklet';
    return Math.abs(y - UI.check.y) < 24 && x > UI.check.x - 26 && x < UI.check.x + 250;
  };
  const toggleAgree = (d: Driver) => {
    'worklet';
    const cur = d.agree >= 0 ? d.agree : frameAt(d.t).tick;
    d.agree = cur;
    d.agreeAim = cur > 0.5 ? 0 : 1;
  };

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const now = clock.get();
      const x = e.x / geo.s;
      const y = (e.y - geo.dy) / geo.s;
      start.set({ x, y, at: now });
      moved.set(0);
      swished.set(0);
      let tg: Target = 0;
      if (onCheck(x, y)) tg = 3;
      else if (inButton(x, y, 6)) tg = 4;
      else if (y < FIELD_BOTTOM) {
        tg = 1;
        // a body under the finger? (the nearest one it is on)
        let best = -1;
        let bestD = 1e9;
        for (let j = 0; j < BODIES.length; j++) {
          const hm = liveHome(j, d.t, d.amb);
          const bx = hm.x + d.ox[j];
          const by = hm.y + d.oy[j];
          const dist = Math.hypot(x - bx, y - by);
          if (dist < hm.r + 8 && dist < bestD) {
            best = j;
            bestD = dist;
          }
        }
        if (best >= 0) {
          tg = 2;
          const hm = liveHome(best, d.t, d.amb);
          d.grab = best;
          d.gdx = x - (hm.x + d.ox[best]);
          d.gdy = y - (hm.y + d.oy[best]);
        }
      }
      target.set(tg);
      if (reduced) return;
      touched(d, now);
      d.down = tg === 1 || tg === 2 ? 1 : 0;
      d.fx = x;
      d.fy = y;
      d.fvx = 0;
      d.fvy = 0;
      if (tg === 4) d.btnAim = 1;
    })
    .onUpdate((e) => {
      if (reduced) return;
      const d = driver.get();
      const x = e.x / geo.s;
      const y = (e.y - geo.dy) / geo.s;
      d.last = clock.get();
      d.fx = x;
      d.fy = y;
      d.fvx = e.velocityX / geo.s;
      d.fvy = e.velocityY / geo.s;
      const s0 = start.get();
      if (Math.hypot(x - s0.x, y - s0.y) > 10) moved.set(1);
      // one swish when the stirring gets going (not one per frame)
      if (target.get() === 1 && swished.get() === 0 && Math.hypot(d.fvx, d.fvy) > 700) {
        swished.set(1);
        scheduleOnRN(cue, 'swish');
      }
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      const tg = target.get();
      const x = e.x / geo.s;
      const y = (e.y - geo.dy) / geo.s;
      const tap = moved.get() === 0;
      target.set(0);
      d.down = 0;
      d.btnAim = 0;
      d.last = now;
      if (reduced) {
        if (tap && tg === 3) fade.set(withTiming(fade.get() > 0.5 ? 0 : 1, { duration: 200, reduceMotion: ReduceMotion.Never }));
        d.grab = -1;
        return;
      }
      if (tg === 2) {
        const j = d.grab;
        d.grab = -1;
        if (tap && j === 0) {
          boo(d, now, d.t);
          scheduleOnRN(cue, 'boo');
          scheduleOnRN(buzz, 1);
        } else if (j >= 0) {
          // the throw keeps the finger's speed
          d.vx[j] = e.velocityX / geo.s;
          d.vy[j] = e.velocityY / geo.s;
          if (!tap) scheduleOnRN(buzz, 0);
        }
      } else if (tg === 3 && tap) {
        toggleAgree(d);
        scheduleOnRN(cue, 'tick');
        scheduleOnRN(buzz, 2);
      } else if (tg === 4 && tap && inButton(x, y, 12)) {
        const agreed = (d.agree >= 0 ? d.agree : frameAt(d.t).tick) > 0.5;
        if (agreed) {
          d.rollAt = now;
          scheduleOnRN(buzz, 1);
        } else {
          d.noAt = now;
          scheduleOnRN(buzz, 3);
        }
      }
    });

  const toggleForAccessibility = () => {
    scheduleOnUI(() => {
      if (reduced) {
        fade.set(withTiming(fade.get() > 0.5 ? 0 : 1, { duration: 200, reduceMotion: ReduceMotion.Never }));
        return;
      }
      const d = driver.get();
      touched(d, clock.get());
      toggleAgree(d);
    });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={touch}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="checkbox"
          accessibilityLabel="Welcome to Wisp. To get started, create a new wallet or import an existing one. I agree to the Terms of Service."
          accessibilityHint="Ticks or unticks the agreement."
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
  screen: { flex: 1, backgroundColor: C.bg },
});
