import {
  Blur,
  BlurMask,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Paint,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Controlled charge level, 0…1. Leave undefined to show the reference's 18% (or the autoPlay loop). */
  progress?: number;
  /** Charges by itself from empty to 18%, holds, drains and starts over. */
  autoPlay?: boolean;
  chipLabel?: string;
  time?: string;
  date?: string;
  caption?: string;
  /** Minutes a charge from empty to full takes; the remaining time is derived from the level. */
  fullChargeMinutes?: number;
  onSpeedUpPress?: () => void;
};

const WIDTH = 330;
const HEIGHT = 414;
// The canvas overflows the component by this much on every side to fit the chip's glow.
const BLEED = 20;

const CX = 165;
const CY = 167;
const R_BOWL = 155;
const R_OUTER = 90.7;
const R_RING = 84.2;
const R_LIQUID = 82.8;

const CHIP_X = 94.8;
const CHIP_Y = 12.6;
const CHIP_W = 140.4;
const CHIP_H = 32.9;

// Value shown in the reference, and the autoPlay loop built around it (seconds).
const REST = 0.18;
const START = 0.5;
const FILL = 2.3;
const HOLD = 4.8;
const RESET = 0.9;
const LOOP = START + FILL + HOLD + RESET;
const LANDED = START + FILL;
// A moment in the loop where everything is at rest: used when nothing is playing.
const REST_T = LANDED + 2;
// A pulse leaves the ring every PULSE seconds while it charges.
const PULSE = 2.6;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const RAD = Math.PI / 180;

const BOLT = 'M1.6 -9L-5.6 1.4H-0.5L-1.9 9L5.6 -1.5H0.5Z';
const PLUG =
  'M-9.4 -5.6H9.4V1.8C9.4 7.6 5.2 11.2 0 11.2C-5.2 11.2 -9.4 7.6 -9.4 1.8ZM-4.6 -11.2V-5.6M4.6 -11.2V-5.6';
const ROCKET =
  'M0 -12.5C6 -7.5 7 1 4.2 8H-4.2C-7 1 -6 -7.5 0 -12.5ZM-4.9 3.6L-9.2 9.8L-4.2 8.4M4.9 3.6L9.2 9.8L4.2 8.4M-2 11Q0 14.6 2 11';

function snowflake() {
  let d = '';
  const point = (radius: number, degrees: number) =>
    `${(radius * Math.cos(degrees * RAD)).toFixed(2)} ${(radius * Math.sin(degrees * RAD)).toFixed(2)}`;
  for (let arm = 0; arm < 6; arm++) {
    const a = arm * 60 + 30;
    d += `M0 0L${point(13, a)}`;
    // Two short barbs near the tip of every arm.
    const bx = 7.6 * Math.cos(a * RAD);
    const by = 7.6 * Math.sin(a * RAD);
    for (const side of [-1, 1]) {
      const b = (a + side * 52) * RAD;
      d += `M${bx.toFixed(2)} ${by.toFixed(2)}l${(4.6 * Math.cos(b)).toFixed(2)} ${(4.6 * Math.sin(b)).toFixed(2)}`;
    }
  }
  return d;
}

const SNOWFLAKE = snowflake();
// The selected mode's tab: a slice of the bowl under the gauge.
const TAB = (() => {
  const point = (radius: number, degrees: number) =>
    `${(CX + radius * Math.cos(degrees * RAD)).toFixed(2)} ${(CY + radius * Math.sin(degrees * RAD)).toFixed(2)}`;
  return `M${point(97, 74)}A97 97 0 0 1 ${point(97, 106)}L${point(R_BOWL, 103.5)}A${R_BOWL} ${R_BOWL} 0 0 0 ${point(R_BOWL, 76.5)}Z`;
})();

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** Charge level at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < START) return 0;
  if (t < LANDED) return REST * easeInOutCubic((t - START) / FILL);
  if (t < LOOP - RESET) return REST;
  return REST * (1 - easeInOutCubic((t - (LOOP - RESET)) / RESET));
}

/** 0…1: how fast the level is moving at `t` seconds into the loop. */
function loopActivity(t: number) {
  'worklet';
  if (t >= LOOP - RESET) return Math.sin(Math.PI * clamp01((t - (LOOP - RESET)) / RESET));
  const u = (t - START) / FILL;
  return u <= 0 || u >= 1 ? 0 : Math.sin(Math.PI * u);
}

/**
 * One layer of the liquid: a travelling wave on a surface at `level`, tilted by `tilt`, closed
 * along the bottom of the gauge. The gauge's circle clips it.
 */
function liquid(level: number, amplitude: number, tilt: number, phase: number, wavelength: number) {
  'worklet';
  const y0 = CY + R_LIQUID - 2 * R_LIQUID * clamp01(level);
  const left = CX - R_LIQUID - 2;
  const right = CX + R_LIQUID + 2;
  const steps = 26;
  let d = `M${left} ${CY + R_LIQUID + 4}`;
  for (let i = 0; i <= steps; i++) {
    const x = left + ((right - left) * i) / steps;
    const y = y0 + amplitude * Math.sin(((x - CX) / wavelength) * Math.PI * 2 + phase) + tilt * (x - CX);
    d += `L${x.toFixed(2)} ${y.toFixed(2)}`;
  }
  return `${d}L${right} ${CY + R_LIQUID + 4}Z`;
}

type Motion = {
  value: SharedValue<number>;
  time: SharedValue<number>;
  activity: SharedValue<number>;
  /** Seconds since the level last landed, or since the last boost; large when nothing happened. */
  landed: SharedValue<number>;
  /** 0 with reduced motion: still liquid, no pulses. */
  ambient: number;
};

function Surface({ value, time, activity, landed, ambient }: Motion) {
  const center = vec(CX, CY);

  // The liquid sloshes while the level moves and settles with a damped rocking once it lands.
  const swell = useDerivedValue(() => {
    const u = landed.get();
    const settle = u > 0 && u < 2 ? Math.exp(-2.6 * u) : 0;
    return clamp01(value.get() / 0.05) * (ambient * 5.4 + 3.2 * activity.get() + 3 * settle);
  });
  const tilt = useDerivedValue(() => {
    const u = landed.get();
    const settle = u > 0 && u < 2 ? 0.11 * Math.exp(-2.6 * u) * Math.sin(10 * u) : 0;
    return clamp01(value.get() / 0.05) * (0.07 * activity.get() * Math.sin(time.get() * 8) + settle);
  });
  const back = useDerivedValue(() => liquid(value.get() + 0.018, swell.get() * 0.85, -tilt.get() * 0.6, -time.get() * 1.3 + 2.1, 118));
  const front = useDerivedValue(() => liquid(value.get(), swell.get(), tilt.get(), time.get() * 1.7, 99));
  // Bubbles rise through the liquid and vanish under the surface.
  const bubbles = useDerivedValue(() => {
    const surface = CY + R_LIQUID - 2 * R_LIQUID * clamp01(value.get());
    const bottom = CY + R_LIQUID;
    let d = '';
    for (let i = 0; i < 6; i++) {
      const cycle = time.get() / (2.1 + 0.35 * i) + i * 0.37;
      const age = cycle - Math.floor(cycle);
      const x = CX + (i - 2.5) * 15 + 4 * Math.sin(time.get() * 2 + i);
      const y = bottom - 4 - (bottom - surface - 2) * age;
      const r = ambient * (0.9 + 0.25 * (i % 3)) * Math.sin(Math.PI * age);
      if (bottom - surface < 8 || r < 0.1) continue;
      d += `M${(x - r).toFixed(2)} ${y.toFixed(2)}a${r.toFixed(2)} ${r.toFixed(2)} 0 1 0 ${(r * 2).toFixed(2)} 0a${r.toFixed(2)} ${r.toFixed(2)} 0 1 0 ${(-r * 2).toFixed(2)} 0Z`;
    }
    return d || 'M0 0';
  });

  // A pulse of light leaves the ring towards the outer one, and the bolt flashes with it.
  const pulsePhase = useDerivedValue(() => (time.get() % PULSE) / 1.25);
  const pulseRadius = useDerivedValue(() => R_RING + 13 * (1 - Math.pow(1 - Math.min(1, pulsePhase.get()), 2)));
  const pulseAlpha = useDerivedValue(() => (pulsePhase.get() < 1 ? ambient * 0.55 * (1 - pulsePhase.get()) : 0));
  const spark = useDerivedValue(() => ambient * Math.exp(-3.2 * pulsePhase.get()));
  const boltTransform = useDerivedValue(() => [{ translateX: CX }, { translateY: 113.3 }, { scale: 1 + 0.14 * spark.get() }]);
  const boltGlow = useDerivedValue(() => 0.25 + 0.75 * spark.get());
  // The ring's bloom breathes, brightens while charging and flashes as the level lands.
  const bloom = useDerivedValue(() => {
    const u = landed.get() / 0.8;
    const flash = u > 0 && u < 1 ? 0.35 * (1 - u) : 0;
    return Math.min(1, 0.55 + 0.12 * ambient * Math.sin(time.get() * 1.7) + 0.25 * activity.get() + flash);
  });
  const plugGlow = useDerivedValue(() => 0.55 + 0.3 * ambient * Math.sin(time.get() * 2.3));

  const gauge = { rect: { x: CX - R_LIQUID, y: CY - R_LIQUID, width: R_LIQUID * 2, height: R_LIQUID * 2 }, rx: R_LIQUID, ry: R_LIQUID };

  return (
    <Canvas style={styles.canvas}>
      <Group transform={[{ translateX: BLEED }, { translateY: BLEED }]}>
        {/* The bowl the gauge sits in: it shows towards its foot. */}
        <Circle cx={CX} cy={CY} r={R_BOWL}>
          <LinearGradient start={vec(0, CY - 70)} end={vec(0, CY + R_BOWL)} colors={['rgba(30,54,37,0)', 'rgba(30,54,37,0.75)', 'rgba(30,56,38,1)']} positions={[0, 0.5, 1]} />
        </Circle>
        <Path path={TAB}>
          <LinearGradient start={vec(0, CY + 97)} end={vec(0, CY + R_BOWL)} colors={['rgba(52,104,64,0.75)', 'rgba(40,84,52,0.45)']} />
        </Path>
        <Circle cx={CX} cy={CY + 3} r={R_OUTER + 6} color="rgba(6,12,8,0.75)">
          <BlurMask blur={9} style="normal" />
        </Circle>

        {/* Mode icons */}
        <Group transform={[{ translateX: 82.7 }, { translateY: 263.7 }]}>
          <Path path={SNOWFLAKE} style="stroke" strokeWidth={2.3} strokeCap="round" color="#33573f" />
        </Group>
        <Group transform={[{ translateX: 247.3 }, { translateY: 264.2 }, { rotate: 38 * RAD }]}>
          <Path path={ROCKET} style="stroke" strokeWidth={2.1} strokeCap="round" strokeJoin="round" color="#33573f" />
          <Circle cx={0} cy={-2.4} r={2.1} color="#33573f" />
        </Group>
        <Group transform={[{ translateX: CX }, { translateY: 294 }]}>
          <Group
            layer={
              <Paint opacity={plugGlow}>
                <Blur blur={4} />
              </Paint>
            }
          >
            <Path path={PLUG} style="stroke" strokeWidth={3.4} strokeCap="round" strokeJoin="round" color="rgba(96,228,128,0.8)" />
          </Group>
          <Path path={PLUG} style="stroke" strokeWidth={2.4} strokeCap="round" strokeJoin="round" color="#74e592" />
          <Group transform={[{ translateY: 1.6 }, { scale: 0.56 }]}>
            <Path path={BOLT} color="#74e592" />
          </Group>
        </Group>

        {/* Gauge */}
        <Circle cx={CX} cy={CY} r={R_OUTER} style="stroke" strokeWidth={1.1} color="rgba(86,180,110,0.42)" />
        <Circle cx={CX} cy={CY} r={pulseRadius} style="stroke" strokeWidth={1.4} color="#8df0a8" opacity={pulseAlpha}>
          <BlurMask blur={1.4} style="normal" />
        </Circle>
        <Circle cx={CX} cy={CY} r={R_RING}>
          <RadialGradient c={center} r={R_RING} colors={['#17241b', '#18281d', '#1e3a27', '#2a5a38']} positions={[0, 0.62, 0.9, 1]} />
        </Circle>
        <Group clip={gauge}>
          <Path path={back} color="rgba(120,226,148,0.5)" />
          <Path path={front}>
            <LinearGradient start={vec(0, CY + 30)} end={vec(0, CY + R_LIQUID)} colors={['#45c768', '#2fa54d']} />
          </Path>
          <Path path={bubbles} color="rgba(214,255,224,0.55)" />
          {/* Shade under the rim, so the liquid sits inside the glass. */}
          <Circle cx={CX} cy={CY} r={R_LIQUID + 3} style="stroke" strokeWidth={9} color="rgba(10,24,14,0.55)">
            <BlurMask blur={5} style="normal" />
          </Circle>
        </Group>
        <Group
          layer={
            <Paint opacity={bloom}>
              <Blur blur={6} />
            </Paint>
          }
        >
          <Circle cx={CX} cy={CY} r={R_RING} style="stroke" strokeWidth={5.5} color="rgba(104,236,138,0.85)" />
        </Group>
        <Circle cx={CX} cy={CY} r={R_RING} style="stroke" strokeWidth={1.8} color="#8ceca6" />

        {/* Charging bolt */}
        <Group transform={boltTransform}>
          <Path path={BOLT} color="#baffc9" opacity={boltGlow}>
            <BlurMask blur={4} style="normal" />
          </Path>
          <Path path={BOLT} color="#f1f6f1" />
          <Path path={BOLT} style="stroke" strokeWidth={0.9} strokeJoin="round" color="#f1f6f1" />
        </Group>

        {/* Divider between the read-outs */}
        <Rect x={CX - 0.5} y={375} width={1} height={25} color="rgba(255,255,255,0.14)" />
      </Group>
    </Canvas>
  );
}

function SpeedUpChip({ label, time, ambient, onPress }: { label: string; time: SharedValue<number>; ambient: number; onPress: () => void }) {
  const [pressed, setPressed] = useState(false);
  // The chip's glow breathes and its bolt flickers; a sheen crosses it now and then.
  const glow = useDerivedValue(() => 0.6 + 0.25 * ambient * Math.sin(time.get() * 2.1));
  const flicker = useDerivedValue(() => 0.6 + 0.4 * ambient * Math.sin(time.get() * 5.3) * Math.sin(time.get() * 1.9));
  const sheen = useDerivedValue(() => [{ translateX: -40 + (CHIP_W + 80) * Math.min(1, ((time.get() + 0.8) % 3.8) / 1.1) }, { skewX: -0.35 }]);
  const pad = 12;
  const chip = { rect: { x: pad, y: pad, width: CHIP_W, height: CHIP_H }, rx: CHIP_H / 2, ry: CHIP_H / 2 };
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      onPressIn={() => {
        setPressed(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => setPressed(false)}
      style={styles.chip}
    >
      <Animated.View
        style={{
          transform: [{ scale: pressed ? 0.97 : 1 }],
          transitionProperty: 'transform',
          transitionDuration: 130,
          transitionTimingFunction: CSS_EASE_OUT,
        }}
      >
        <Canvas style={{ position: 'absolute', left: -pad, top: -pad, width: CHIP_W + pad * 2, height: CHIP_H + pad * 2 }} pointerEvents="none">
          <RoundedRect x={pad} y={pad} width={CHIP_W} height={CHIP_H} r={CHIP_H / 2} style="stroke" strokeWidth={3} color="rgba(60,214,84,0.9)" opacity={glow}>
            <BlurMask blur={4.5} style="normal" />
          </RoundedRect>
          <RoundedRect x={pad} y={pad} width={CHIP_W} height={CHIP_H} r={CHIP_H / 2}>
            <LinearGradient start={vec(0, pad)} end={vec(0, pad + CHIP_H)} colors={['#1f3f27', '#1a3321']} />
          </RoundedRect>
          <Group clip={chip} opacity={ambient}>
            <Group transform={sheen}>
              <Rect x={-14} y={pad - 4} width={28} height={CHIP_H + 8}>
                <LinearGradient start={vec(-14, 0)} end={vec(14, 0)} colors={['rgba(190,255,205,0)', 'rgba(190,255,205,0.16)', 'rgba(190,255,205,0)']} />
              </Rect>
            </Group>
          </Group>
          <RoundedRect x={pad + 0.6} y={pad + 0.6} width={CHIP_W - 1.2} height={CHIP_H - 1.2} r={CHIP_H / 2} style="stroke" strokeWidth={1.2} color="#2f9a3f" />
          <Group transform={[{ translateX: pad + 20.2 }, { translateY: pad + CHIP_H / 2 }, { scale: 0.88 }]}>
            <Path path={BOLT} color="#d9ff3c" opacity={flicker}>
              <BlurMask blur={3.2} style="normal" />
            </Path>
            <Path path={BOLT} color="#eaff58" />
          </Group>
        </Canvas>
        <Text style={styles.chipLabel} numberOfLines={1}>
          {label}
        </Text>
      </Animated.View>
    </Pressable>
  );
}

/**
 * Dark green charging screen: a "speed up" chip, a glowing circular gauge with the charge level
 * and a liquid filling its foot, the charging modes and the time read-outs. The liquid is two
 * waves that never stop travelling; it sloshes and tilts while the level moves and rocks itself
 * calm afterwards, bubbles rise through it, a pulse of light keeps leaving the ring while the
 * bolt flashes with it, and the chip's glow breathes under a passing sheen. Tapping the chip
 * gives the liquid a kick.
 */
export function ChargeWaveCircle({
  progress,
  autoPlay = false,
  chipLabel = 'Speed up charging',
  time: clockTime = '10:23',
  date = '11/28 Sunday',
  caption = 'Full Charge takes about',
  fullChargeMinutes = 227,
  onSpeedUpPress,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const target = controlled ? Math.min(1, Math.max(0, progress)) : REST;

  // Seconds: `time` never stops (waves, pulses), `clock` is the position in the autoPlay loop.
  const time = useSharedValue(0);
  const clock = useSharedValue(autoPlay && !controlled && !reducedMotion ? 0 : REST_T);
  const playing = useSharedValue(false);
  const looping = useSharedValue(false);
  const held = useSharedValue(target);
  // When a controlled value last changed / the chip last kicked the liquid, on the `time` axis.
  const movedAt = useSharedValue(-10);
  const kickedAt = useSharedValue(-10);

  useEffect(() => {
    const auto = autoPlay && !controlled && !reducedMotion;
    looping.set(auto);
    playing.set(auto);
    clock.set(auto ? 0 : REST_T);
  }, [autoPlay, controlled, reducedMotion, clock, looping, playing]);

  useEffect(() => {
    if (!controlled) return;
    movedAt.set(time.get());
    held.set(withTiming(target, { duration: reducedMotion ? 200 : 900, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
  }, [controlled, target, reducedMotion, held, movedAt, time]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(time.get() + dt);
    if (!playing.get()) return;
    let t = clock.get() + dt;
    if (looping.get()) {
      if (t >= LOOP) t -= LOOP;
    } else if (t >= REST_T) {
      // A tapped, one-off run stops at the resting frame.
      t = REST_T;
      playing.set(false);
    }
    clock.set(t);
  }, !reducedMotion);

  const value = useDerivedValue(() => (controlled ? held.get() : loopValue(clock.get())));
  const activity = useDerivedValue(() => {
    if (reducedMotion) return 0;
    if (!controlled) return loopActivity(clock.get());
    const u = (time.get() - movedAt.get()) / 0.8;
    return u <= 0 || u >= 1 ? 0 : Math.sin(Math.PI * u);
  });
  const landed = useDerivedValue(() => {
    if (reducedMotion) return 10;
    const kick = time.get() - kickedAt.get();
    if (!controlled) return Math.min(kick, clock.get() < LOOP - RESET && clock.get() >= LANDED ? clock.get() - LANDED : 10);
    return Math.min(kick, Math.max(0, time.get() - movedAt.get() - 0.7));
  });

  // The figures are real text: re-render only when a whole percent changes.
  const [percent, setPercent] = useState(Math.round(target * 100));
  useAnimatedReaction(
    () => Math.round(value.get() * 100),
    (now, before) => {
      if (now !== before) scheduleOnRN(setPercent, now);
    },
  );
  // The number swells a little while the level moves.
  const figure = useAnimatedStyle(() => ({ transform: [{ scale: 1 + 0.05 * activity.get() }] }));

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  const remaining = Math.round(fullChargeMinutes * (1 - percent / 100));
  const ambient = reducedMotion ? 0 : 1;

  return (
    <View style={styles.screen}>
      <Pressable
        onPress={restart}
        style={StyleSheet.absoluteFill}
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={`Charging. ${caption} ${Math.floor(remaining / 60)} hours ${remaining % 60} minutes`}
        accessibilityValue={{ min: 0, max: 100, now: percent }}
      >
        <View pointerEvents="none" style={styles.surface}>
          <Surface value={value} time={time} activity={activity} landed={landed} ambient={ambient} />
        </View>
        <View style={StyleSheet.absoluteFill} pointerEvents="none">
          <Animated.View style={[styles.figure, figure]}>
            <Text style={styles.number}>{percent}</Text>
          </Animated.View>
          <Text style={styles.unit}>%</Text>

          <Text style={[styles.readout, styles.left]}>{clockTime}</Text>
          <Text style={[styles.note, styles.left]} numberOfLines={1}>
            {date}
          </Text>
          <Text style={[styles.readout, styles.right]}>
            {Math.floor(remaining / 60)}
            <Text style={styles.small}> H </Text>
            {remaining % 60}
            <Text style={styles.small}> M</Text>
          </Text>
          <Text style={[styles.note, styles.right]} numberOfLines={1}>
            {caption}
          </Text>
        </View>
      </Pressable>

      <SpeedUpChip
        label={chipLabel}
        time={time}
        ambient={ambient}
        onPress={() => {
          if (!reducedMotion) kickedAt.set(time.get());
          onSpeedUpPress?.();
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    width: WIDTH,
    height: HEIGHT,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  canvas: {
    width: WIDTH + BLEED * 2,
    height: HEIGHT + BLEED * 2,
  },
  chip: {
    position: 'absolute',
    left: CHIP_X,
    top: CHIP_Y,
    width: CHIP_W,
    height: CHIP_H,
  },
  chipLabel: {
    position: 'absolute',
    left: 40.5,
    top: CHIP_H / 2 - 8,
    color: '#e3ece4',
    fontSize: 9.8,
    lineHeight: 16,
    fontWeight: '400',
  },
  figure: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: CY - 32,
    height: 64,
    alignItems: 'center',
  },
  number: {
    color: '#e6e8e6',
    fontSize: 56,
    lineHeight: 64,
    fontWeight: '500',
    letterSpacing: 0.5,
    fontVariant: ['tabular-nums'],
  },
  unit: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 215.3 - 10,
    textAlign: 'center',
    color: '#f0f4f0',
    fontSize: 15.5,
    lineHeight: 20,
    fontWeight: '400',
  },
  readout: {
    position: 'absolute',
    top: 379.5 - 13,
    textAlign: 'center',
    color: '#dfe2df',
    fontSize: 20,
    lineHeight: 26,
    fontWeight: '400',
    fontVariant: ['tabular-nums'],
  },
  small: {
    fontSize: 9.5,
    fontWeight: '500',
  },
  note: {
    position: 'absolute',
    top: 403 - 8,
    textAlign: 'center',
    color: '#7d827e',
    fontSize: 11.8,
    lineHeight: 16,
    fontWeight: '400',
  },
  left: {
    left: 0,
    width: CX,
  },
  right: {
    left: CX,
    width: CX,
  },
});
