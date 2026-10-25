import { BlurMask, Canvas, Fill, Group, Image, LinearGradient, Path, RoundedRect, Shader, Skia, rrect, vec, type SkPath } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { PAL, bakeBack, makeLayout, rocketPaths } from './rocketArt';
import { COUNT_AT, DONE_AT, IGNITE_AT, LIFT_AT, LOOP, ROLL_AT, autoFuel, clamp01, easeOutCubic, rocketPose, shake, throttle } from './rocketMotion';
import { ROCKET_EXHAUST_SKSL } from './rocketShader';
import { Badge, Countdown, Odometer, StatusLine, StepsCard } from './RocketStatus';

/*
 Rocket Launch Progress — "Shipwright", a deploy screen where the progress is fuel. A small rocket
 waits on its pad at dusk; the tank beside it fills as Build, Test and Upload tick; at 100 % the
 sky counts 3-2-1, the engine lights (flame, smoke thrown sideways along the pad, the camera
 shaking), the rocket lifts off and arcs away on a contrail, "v4.2.0 is live" — and the next
 rocket rolls onto the pad. Hold the pad to fuel it yourself; full tanks launch.

 autoPlay (LOOP = 10 s, rocketMotion.ts): rest 0–1.4 · fuelling 1.4–4.4 · countdown 4.4–5.6 ·
 ignition 5.6–6.2 · lift-off 6.2–7.6 · deployed 7.6–8.4 · new rocket 8.4–9.4 · rest. A touch
 takes over: holding fuels (2.4 s to full; letting go holds the gauge on a whole number); a full
 tank hands the rest of the launch back to the loop. 2 s after letting go of a part-full tank it
 drains (0.9 s) and the loop restarts. Reduced motion: no loop, no shake or flight; the gauge
 fills under the finger and the screen shows the deployed state when full.

 Drawing: one Canvas — the baked scene, the contrail (two paths), the fuel (a clipped level with
 a sloshing top), the rocket (static paths under a moving transform), and one RuntimeEffect for
 flame, smoke and glow. Text and the pipeline are views reading the same shared values.
*/

type Props = {
  /** Fuels, counts down, launches, deploys, rolls out the next rocket. */
  autoPlay?: boolean;
};

const MODE_AUTO = 0;
const MODE_USER = 1;
const MODE_RETURN = 2;
const IDLE = 2;
const RETURN = 0.9;
/** Holding fills the tank in this long. */
const FILL_FOR = 2.4;
const exhaust = Skia.RuntimeEffect.Make(ROCKET_EXHAUST_SKSL);
const PARTS = rocketPaths();

type Frame = {
  t: number;
  fuel: number;
  rx: number;
  ry: number;
  rot: number;
  on: number;
  sx: number;
  sy: number;
  thr: number;
  lift: number;
  age: number;
  fade: number;
  trail: SkPath;
  trailA: number;
  slosh: SkPath;
};

export function RocketLaunchProgress({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const win = useWindowDimensions();
  const [size, setSize] = useState({ width: win.width, height: win.height });
  const art = useMemo(() => {
    const L = makeLayout(size.width, size.height, insets.top, insets.bottom);
    return { L, back: bakeBack(L, Math.min(3, PixelRatio.get())) };
  }, [size.width, size.height, insets.top, insets.bottom]);
  const { L } = art;
  const baseY = L.groundY - 14;
  const tank = L.tank;

  const mode = useSharedValue(MODE_AUTO);
  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const holding = useSharedValue(0);
  const oneShot = useSharedValue(0);
  const userFuel = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const returnFuel = useSharedValue(0);
  const T = useSharedValue(0);
  const F = useSharedValue(0);
  const spin = useSharedValue(0);
  const frame = useSharedValue<Frame | null>(null);

  const feel = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  };

  /** The contrail: the nozzle's path since lift-off, a ribbon that widens with age. */
  function trailPath(t: number) {
    'worklet';
    const b = Skia.PathBuilder.Make();
    const k = Math.min(t, ROLL_AT) - LIFT_AT;
    if (k <= 0.02) return b.build();
    const n = 28;
    const pose = { x: 0, y: 0, rot: 0, on: 1 };
    const left: number[] = [];
    const right: number[] = [];
    for (let j = 0; j <= n; j++) {
      const kk = (k * j) / n;
      rocketPose(LIFT_AT + kk, pose);
      const age = k - kk;
      const w = (3 + 26 * age + 30 * Math.max(0, t - DONE_AT)) * L.k;
      const nx = Math.cos(pose.rot);
      const ny = Math.sin(pose.rot);
      const x = L.padX + pose.x;
      const y = baseY + pose.y + 10 * L.k * Math.min(1, age * 4);
      left.push(x - nx * w, y - ny * w);
      right.push(x + nx * w, y + ny * w);
    }
    b.moveTo(left[0], left[1]);
    for (let j = 1; j <= n; j++) b.lineTo(left[j * 2], left[j * 2 + 1]);
    for (let j = n; j >= 0; j--) b.lineTo(right[j * 2], right[j * 2 + 1]);
    b.close();
    return b.build();
  }

  /** The fuel's surface: a level with a small wave that grows while it moves. */
  function sloshPath(level: number, t: number, moving: number) {
    'worklet';
    const b = Skia.PathBuilder.Make();
    const y0 = tank.y + tank.h - level * (tank.h - 4);
    const amp = 0.8 + 2.2 * moving;
    b.moveTo(tank.x, tank.y + tank.h);
    for (let j = 0; j <= 10; j++) {
      const x = tank.x + (tank.w * j) / 10;
      b.lineTo(x, y0 + amp * Math.sin((j / 10) * Math.PI * 2 + (2 * Math.PI * 7 * t) / LOOP));
    }
    b.lineTo(tank.x + tank.w, tank.y + tank.h);
    b.close();
    return b.build();
  }

  useFrameCallback((f) => {
    const dt = Math.min((f.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const at = now.get() + dt;
    now.set(at);
    let t = 0;
    let fuel = 0;
    let moving = 0;
    const m = mode.get();
    if (m === MODE_AUTO) {
      if (reduced) {
        // Still: the pad, or once fuelled by hand, the deployed screen.
        t = userFuel.get() >= 100 ? DONE_AT + 0.3 : 0;
        fuel = t > 0 ? 100 : 0;
      } else if (auto || oneShot.get()) {
        const next = clock.get() + dt;
        // Without autoPlay a hand-fuelled launch plays once and stops back on the pad.
        if (!auto && next >= LOOP) oneShot.set(0);
        t = next % LOOP;
        fuel = autoFuel(t);
      }
      clock.set(t);
      moving = t > 1.4 && t < 4.6 ? 1 : 0;
      spin.set((2 * Math.PI * 6 * t) / LOOP);
    } else {
      t = 0.5;
      let u = userFuel.get();
      if (m === MODE_USER && holding.get()) {
        const before = u;
        u = Math.min(100, u + (100 / FILL_FOR) * dt);
        userFuel.set(u);
        moving = 1;
        for (const s of [25, 55, 85]) if (before < s && u >= s) scheduleOnRN(feel, 0);
        if (u >= 100) {
          // Full: the loop carries the launch on from the countdown.
          holding.set(0);
          scheduleOnRN(feel, 1);
          mode.set(MODE_AUTO);
          clock.set(COUNT_AT);
          oneShot.set(1);
        }
      }
      if (m === MODE_USER && auto && !holding.get() && at - lastTouch.get() > IDLE) {
        returnAt.set(at);
        returnFuel.set(u);
        mode.set(MODE_RETURN);
      }
      if (mode.get() === MODE_RETURN) {
        const p = easeOutCubic((at - returnAt.get()) / RETURN);
        u = returnFuel.get() * (1 - p);
        userFuel.set(u);
        moving = 1;
        if (p >= 1) {
          mode.set(MODE_AUTO);
          clock.set(0);
          userFuel.set(0);
        }
      }
      fuel = u;
      spin.set(at * 5);
    }
    T.set(t);
    F.set(fuel);

    const pose = { x: 0, y: 0, rot: 0, on: 1 };
    if (!reduced) rocketPose(t, pose);
    const a = reduced ? 0 : shake(t);
    const w = (2 * Math.PI * t) / LOOP;
    const thr = reduced ? 0 : throttle(t);
    frame.set({
      t,
      fuel,
      rx: L.padX + pose.x,
      ry: baseY + pose.y,
      rot: pose.rot,
      on: reduced && t > LIFT_AT ? 0 : pose.on,
      sx: a * 2.6 * (Math.sin(37 * w) + 0.5 * Math.sin(71 * w + 1)),
      sy: a * 2.2 * (Math.sin(43 * w + 2) + 0.5 * Math.sin(89 * w)),
      thr,
      lift: clamp01((t - LIFT_AT) / 1.2),
      age: Math.max(0, t - IGNITE_AT),
      fade: 1 - easeOutCubic((t - (DONE_AT - 0.2)) / 1.2),
      trail: trailPath(t),
      trailA: reduced ? 0 : clamp01((t - LIFT_AT) / 0.15) * (1 - easeOutCubic((t - DONE_AT) / 0.8)),
      slosh: sloshPath(Math.min(1, fuel / 100), t, moving),
    });
  });

  function touched() {
    'worklet';
    lastTouch.set(now.get());
    const m = mode.get();
    if (m === MODE_USER) return;
    if (m === MODE_AUTO) {
      // Mid-launch the loop keeps the stage; on the pad the finger takes the tank as it is.
      const t = clock.get();
      if (t >= COUNT_AT && t < ROLL_AT + 0.6) return;
      userFuel.set(t > ROLL_AT ? 0 : Math.round(autoFuel(t)));
    }
    mode.set(MODE_USER);
  }

  const hold = Gesture.LongPress()
    .minDuration(0)
    .maxDistance(4000)
    .onBegin(() => {
      touched();
      if (mode.get() === MODE_USER) holding.set(1);
    })
    .onFinalize(() => {
      if (!holding.get()) return;
      holding.set(0);
      lastTouch.set(now.get());
      // The gauge comes to rest on a whole number.
      userFuel.set(Math.round(userFuel.get()));
    });

  const shakeT = useDerivedValue(() => {
    const s = frame.get();
    return [{ translateX: s?.sx ?? 0 }, { translateY: s?.sy ?? 0 }];
  });
  const rocketT = useDerivedValue(() => {
    const s = frame.get();
    return [{ translateX: s?.rx ?? L.padX }, { translateY: s?.ry ?? baseY }, { rotate: s?.rot ?? 0 }, { scale: L.k }];
  });
  const rocketOn = useDerivedValue(() => frame.get()?.on ?? 1);
  const trail = useDerivedValue(() => frame.get()?.trail ?? Skia.Path.Make());
  const trailA = useDerivedValue(() => frame.get()?.trailA ?? 0);
  const slosh = useDerivedValue(() => frame.get()?.slosh ?? Skia.Path.Make());
  const uniforms = useDerivedValue(() => {
    const s = frame.get();
    const rot = s?.rot ?? 0;
    return {
      nozzle: [s?.rx ?? L.padX, s?.ry ?? baseY],
      dir: [Math.sin(rot), -Math.cos(rot)],
      throttle: s?.thr ?? 0,
      lift: s?.lift ?? 0,
      age: s?.age ?? 0,
      fade: s?.fade ?? 0,
      pad: [L.padX, L.groundY - 8],
      k: L.k,
    };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (Math.abs(width - size.width) > 0.5 || Math.abs(height - size.height) > 0.5) setSize({ width, height });
  };
  const tankRR = rrect({ x: tank.x, y: tank.y, width: tank.w, height: tank.h }, tank.w / 2, tank.w / 2);
  const skyMid = insets.top + 108 + (baseY - L.rocketH - insets.top - 108) * 0.42;

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={hold}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityRole="button" accessibilityLabel="Launch pad. Hold to fuel the rocket.">
          <Canvas style={StyleSheet.absoluteFill}>
            <Image image={art.back} x={0} y={0} width={L.W} height={L.H} fit="fill" />
            {/* The engine shakes what stands on the pad; the sky and the ground stay put. */}
            <Group transform={shakeT}>
              <Group opacity={trailA}>
                <Path path={trail} color="rgba(232,224,240,0.35)">
                  <BlurMask blur={9} style="normal" />
                </Path>
                <Path path={trail} color="rgba(255,250,246,0.55)">
                  <BlurMask blur={2.5} style="normal" />
                </Path>
              </Group>
              <Group clip={tankRR}>
                <Path path={slosh}>
                  <LinearGradient start={vec(tank.x, 0)} end={vec(tank.x + tank.w, 0)} colors={['#C2561C', '#FFB04F', '#FFD58A', '#D86A22']} positions={[0, 0.35, 0.55, 1]} />
                </Path>
              </Group>
              <RoundedRect rect={tankRR} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.18)" />
              <RoundedRect x={tank.x + 5} y={tank.y + 8} width={4} height={tank.h - 16} r={2} color="rgba(255,255,255,0.16)" />
              <Group transform={rocketT} opacity={rocketOn}>
                <Path path={PARTS.nozzle}>
                  <LinearGradient start={vec(-8, 0)} end={vec(8, 0)} colors={['#2A2630', '#6E6878', '#1E1A24']} positions={[0, 0.4, 1]} />
                </Path>
                <Path path={PARTS.finL} color="#9E2A22" />
                <Path path={PARTS.finR} color="#7E1F1A" />
                <Path path={PARTS.body}>
                  <LinearGradient start={vec(-15, 0)} end={vec(15, 0)} colors={['#7C7686', '#E9E4EE', '#FFFFFF', '#C7C0CF', '#5E5868']} positions={[0, 0.3, 0.45, 0.75, 1]} />
                </Path>
                <Path path={PARTS.band}>
                  <LinearGradient start={vec(-15, 0)} end={vec(15, 0)} colors={['#6E1A16', '#E3523A', '#A52A20']} positions={[0, 0.4, 1]} />
                </Path>
                <Path path={PARTS.nose}>
                  <LinearGradient start={vec(-15, 0)} end={vec(15, 0)} colors={['#6E1A16', '#F06A4A', '#E3523A', '#7A1E18']} positions={[0, 0.35, 0.55, 1]} />
                </Path>
                <Path path={PARTS.finC}>
                  <LinearGradient start={vec(-2.5, 0)} end={vec(2.5, 0)} colors={['#B8352A', '#E05A44', '#8E2420']} positions={[0, 0.5, 1]} />
                </Path>
                <RoundedRect x={-5.5} y={-84} width={11} height={11} r={5.5} color="#2E3B5E" />
                <RoundedRect x={-4} y={-82.5} width={5} height={4} r={2} color="rgba(190,220,255,0.75)" />
              </Group>
              {exhaust ? (
                <Fill>
                  <Shader source={exhaust} uniforms={uniforms} />
                </Fill>
              ) : null}
            </Group>
          </Canvas>
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 14 }]}>
        <Text style={styles.kicker}>SHIPWRIGHT</Text>
        <Text style={styles.title}>Deploying v4.2.0</Text>
        <StatusLine T={T} />
      </View>
      <View pointerEvents="none" style={[styles.sky, { top: insets.top + 118 }]}>
        <Odometer F={F} T={T} />
      </View>
      <View pointerEvents="none" style={[styles.center, { top: skyMid - 60 }]}>
        <Countdown T={T} />
      </View>
      <View pointerEvents="none" style={[styles.center, { top: skyMid - 22 }]}>
        <Badge T={T} />
      </View>
      <StepsCard clock={{ T, F, spin }} top={L.cardTop} height={L.cardH} rowH={L.rowH} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: PAL.night },
  header: { position: 'absolute', left: 22, right: 22, gap: 4 },
  kicker: { color: PAL.amber, fontSize: 11, fontWeight: '800', letterSpacing: 2 },
  title: { color: PAL.ink, fontSize: 32, fontWeight: '800', letterSpacing: -0.5 },
  sky: { position: 'absolute', left: 0, right: 0 },
  center: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
});
