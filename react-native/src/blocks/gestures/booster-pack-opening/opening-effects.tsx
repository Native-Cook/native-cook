import {
  BlurMask,
  Group,
  LinearGradient,
  Path,
  Rect,
  Shader,
  Skia,
  vec,
  type SkPath,
} from '@shopify/react-native-skia';
import { useCallback, useEffect } from 'react';
import {
  Easing,
  cancelAnimation,
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withRepeat,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { makeEmptyPath } from './geometry';
import type { Point, Rect as PackRect } from './model';
import {
  BURST_DURATION_MS,
  BURST_GLINT_COUNT,
  IDLE_CYCLE_MS,
  MAX_TEAR_SPARKS,
  burstGlintAt,
  cardRiseLightAt,
  shockwaveAt,
  spawnTearSpark,
  stepTearSpark,
  type IdlePrime,
  type TearSpark,
} from './opening-effects-motion';

type PathBuilder = ReturnType<typeof Skia.PathBuilder.Make>;

const GLINT_COLORS = ['#36E4FF', '#FF62E6', '#FFD84A', '#5CF0FF', '#FF7BEA', '#FFE66B'];

// Rays are evaluated around the rising card. Their rotation follows the shared
// opening clock, so a paused session also pauses the light.
export const CARD_RISE_LIGHT = Skia.RuntimeEffect.Make(`
uniform float2 center;
uniform float radius;
uniform float phase;
uniform float strength;

half4 main(float2 p) {
  float2 d = p - center;
  float dist = length(d) / radius;
  float angle = atan(d.y, d.x);
  float broad = pow(0.5 + 0.5 * sin(angle * 7.0 + phase), 3.0);
  float fine = pow(0.5 + 0.5 * sin(angle * 13.0 - phase * 1.7 + 1.9), 4.0);
  float falloff = pow(max(0.0, 1.0 - dist), 1.35);
  float core = pow(max(0.0, 1.0 - dist * 1.15), 2.2);
  float light = clamp(strength * (core * 0.95 + (broad * 0.62 + fine * 0.38) * falloff * 1.05), 0.0, 1.0);
  return half4(float3(1.0, 0.98, 0.88) * light, light);
}
`)!;

/**
 * Runs the idle loop only while an untouched pack is on screen. A new `prime`
 * from the autoPlay script starts the cycle part of the way in and fully
 * faded in, as on a pack that has been idle for that long already.
 */
export function IdleClockDriver({ phase, mix, idle, enabled, prime }: Readonly<{
  phase: SharedValue<number>; mix: SharedValue<number>;
  idle: SharedValue<number>; enabled: boolean; prime: SharedValue<IdlePrime>;
}>) {
  const primed = useSharedValue(0);
  useAnimatedReaction(
    () => enabled && idle.get() === 1 ? 1 + prime.get().count : 0,
    (running, previous) => {
      if (running === previous) return;
      cancelAnimation(phase);
      cancelAnimation(mix);
      if (!running) {
        // Hold the phase while fading, so a sheen never jumps as a tear begins.
        mix.set(withTiming(0, { duration: 160, easing: Easing.out(Easing.quad) }));
        return;
      }
      const cycle = () => {
        phase.set(0);
        phase.set(withRepeat(withTiming(1, {
          duration: IDLE_CYCLE_MS,
          easing: Easing.linear,
        }), -1, false));
      };
      const request = prime.get();
      if (request.count === primed.get()) {
        cycle();
        mix.set(withTiming(1, { duration: 420, easing: Easing.out(Easing.quad) }));
        return;
      }
      primed.set(request.count);
      const lead = (request.ms % IDLE_CYCLE_MS) / IDLE_CYCLE_MS;
      phase.set(lead);
      phase.set(withTiming(1, {
        duration: (1 - lead) * IDLE_CYCLE_MS,
        easing: Easing.linear,
      }, (finished) => {
        if (finished) cycle();
      }));
      mix.set(1);
    },
    [enabled, idle, mix, phase, prime, primed],
  );
  useEffect(() => () => {
    cancelAnimation(phase);
    cancelAnimation(mix);
  }, [mix, phase]);
  return null;
}

function addSpark(builder: PathBuilder, spark: TearSpark, width: number) {
  'worklet';
  const speed = Math.hypot(spark.vx, spark.vy);
  if (speed < 0.001) return;
  const fade = 1 - spark.age / spark.life;
  const dx = spark.vx / speed;
  const dy = spark.vy / speed;
  const length = spark.size * (1.6 + speed / (width * 0.22)) * fade;
  const half = spark.size * 0.5 * fade + 0.25;
  builder.moveTo(spark.x + dx * length, spark.y + dy * length);
  builder.lineTo(spark.x - dy * half, spark.y + dx * half);
  builder.lineTo(spark.x - dx * length * 0.55, spark.y - dy * length * 0.55);
  builder.lineTo(spark.x + dy * half, spark.y - dx * half);
  builder.close();
}

/** Sparks thrown from the tear frontier in proportion to newly torn material. */
export function TearSparks({ points, active, direction, width, enabled }: Readonly<{
  points: SharedValue<Point[]>; active: SharedValue<number>;
  direction: SharedValue<1 | -1>; width: number; enabled: boolean;
}>) {
  const path = useSharedValue<SkPath>(makeEmptyPath());
  const sparks = useSharedValue<TearSpark[]>([]);
  const emitter = useSharedValue({ x: 0, y: 0, tracking: false, budget: 0, seed: 0 });
  const spacing = width * 0.017;

  const frame = useFrameCallback((info) => {
    'worklet';
    const seconds = Math.min(info.timeSincePreviousFrame ?? 16.667, 50) / 1000;
    const next: TearSpark[] = [];
    for (const spark of sparks.get()) {
      const stepped = stepTearSpark(spark, seconds, width);
      if (stepped) next.push(stepped);
    }

    const trace = points.get();
    const head = active.get() === 1 && trace.length > 1 ? trace[trace.length - 1] : null;
    let { x, y, tracking, budget, seed } = emitter.get();
    if (head) {
      if (tracking) {
        // Emission follows distance. Holding the finger still emits nothing.
        budget = Math.min(budget + Math.hypot(head.x - x, head.y - y) / spacing, 6);
        const count = Math.floor(budget);
        for (let index = 0; index < count; index += 1) {
          seed += 1;
          if (next.length >= MAX_TEAR_SPARKS) continue;
          const along = (index + 1) / count;
          next.push(spawnTearSpark(
            { x: x + (head.x - x) * along, y: y + (head.y - y) * along },
            seed, direction.get(), width,
          ));
        }
        budget -= count;
      }
      x = head.x;
      y = head.y;
      tracking = true;
    } else {
      tracking = false;
      budget = 0;
    }
    emitter.set({ x, y, tracking, budget, seed });
    sparks.set(next);

    const builder = Skia.PathBuilder.Make();
    for (const spark of next) addSpark(builder, spark, width);
    path.set(builder.build());
  }, false);

  const setRunning = useCallback((running: boolean) => frame.setActive(running), [frame]);
  useAnimatedReaction(
    () => enabled && (active.get() === 1 || sparks.get().length > 0),
    (running, previous) => {
      if (running !== previous) scheduleOnRN(setRunning, running);
    },
    [active, enabled, setRunning, sparks],
  );
  useEffect(() => () => frame.setActive(false), [frame]);

  return (
    <Group blendMode="screen">
      <Path path={path} color="#F45CFF">
        <BlurMask blur={width * 0.012} style="normal" />
      </Path>
      <Path path={path} color="#FF9CF7">
        <BlurMask blur={width * 0.004} style="normal" />
      </Path>
      <Path path={path} color="#FFF6FF" />
    </Group>
  );
}

function addGlint(builder: PathBuilder, x: number, y: number, size: number, rotation: number) {
  'worklet';
  for (let index = 0; index < 8; index += 1) {
    const angle = rotation + index * Math.PI / 4;
    const radius = index % 2 === 0 ? size : size * 0.26;
    const px = x + Math.cos(angle) * radius;
    const py = y + Math.sin(angle) * radius;
    if (index === 0) builder.moveTo(px, py); else builder.lineTo(px, py);
  }
  builder.close();
}

function addEllipse(builder: PathBuilder, cx: number, cy: number, rx: number, ry: number) {
  'worklet';
  const arc = 0.55228475;
  builder.moveTo(cx - rx, cy);
  builder.cubicTo(cx - rx, cy - ry * arc, cx - rx * arc, cy - ry, cx, cy - ry);
  builder.cubicTo(cx + rx * arc, cy - ry, cx + rx, cy - ry * arc, cx + rx, cy);
  builder.cubicTo(cx + rx, cy + ry * arc, cx + rx * arc, cy + ry, cx, cy + ry);
  builder.cubicTo(cx - rx * arc, cy + ry, cx - rx, cy + ry * arc, cx - rx, cy);
  builder.close();
}

/** Expanding ring and glints that carry the single-frame flash into the opening. */
export function CutBurst({ time, committed, pack, cutY, width, height, enabled }: Readonly<{
  time: SharedValue<number>; committed: SharedValue<number>; pack: PackRect;
  cutY: number; width: number; height: number; enabled: boolean;
}>) {
  const live = useDerivedValue(() => enabled && committed.get() === 1);
  const ring = useDerivedValue(() => {
    const builder = Skia.PathBuilder.Make();
    const ms = time.get();
    if (!live.get() || ms <= 0 || ms >= 480) return builder.build();
    const wave = shockwaveAt(ms, width, height);
    addEllipse(builder, width * 0.5, cutY, wave.radiusX, wave.radiusY);
    return builder.build();
  });
  const ringWidth = useDerivedValue(() => shockwaveAt(time.get(), width, height).strokeWidth);
  const ringOpacity = useDerivedValue(() =>
    live.get() ? shockwaveAt(time.get(), width, height).opacity : 0);
  const glints = useDerivedValue(() => {
    const builder = Skia.PathBuilder.Make();
    const ms = time.get();
    if (!live.get() || ms <= 0 || ms >= BURST_DURATION_MS) return builder.build();
    for (let index = 0; index < BURST_GLINT_COUNT; index += 1) {
      const glint = burstGlintAt(index, ms, pack, cutY, width, height);
      if (glint && glint.size > 0.2) addGlint(builder, glint.x, glint.y, glint.size, glint.rotation);
    }
    return builder.build();
  });

  return (
    <Group>
      <Path path={ring} style="stroke" strokeWidth={ringWidth} color="#B4FFFA"
        opacity={ringOpacity} blendMode="screen">
        <BlurMask blur={width * 0.014} style="normal" />
      </Path>
      {/* Saturated halos keep the glints readable over the pale background. */}
      <Path path={glints}>
        <LinearGradient start={vec(pack.x, 0)} end={vec(pack.x + pack.width, 0)} colors={GLINT_COLORS} />
        <BlurMask blur={width * 0.012} style="normal" />
      </Path>
      <Path path={glints}>
        <LinearGradient start={vec(pack.x, 0)} end={vec(pack.x + pack.width, 0)} colors={GLINT_COLORS} />
        <BlurMask blur={width * 0.004} style="normal" />
      </Path>
      <Path path={glints} color="#FFFFFF" opacity={0.92} />
    </Group>
  );
}

/** Warm rays behind the card as it leaves the pack. */
export function CardRiseLight({ time, cardTop, cardHeight, width, height, enabled }: Readonly<{
  time: SharedValue<number>; cardTop: SharedValue<number>; cardHeight: number;
  width: number; height: number; enabled: boolean;
}>) {
  const opacity = useDerivedValue(() => enabled ? cardRiseLightAt(time.get()) : 0);
  // Collapse the quad while dark; an invisible full-screen shader still costs fill.
  const lightWidth = useDerivedValue(() => opacity.get() > 0.001 ? width : 0);
  const uniforms = useDerivedValue(() => ({
    center: [width * 0.5, cardTop.get() + cardHeight * 0.46],
    radius: width * 1.02,
    phase: time.get() * 0.0021,
    strength: 1,
  }));
  return (
    <Rect x={0} y={0} width={lightWidth} height={height} opacity={opacity} blendMode="plus">
      <Shader source={CARD_RISE_LIGHT} uniforms={uniforms} />
    </Rect>
  );
}
