import { BlurMask, Canvas, Group, LinearGradient, Path, RoundedRect, vec } from '@shopify/react-native-skia';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Tone = 'green' | 'yellow';

type Props = {
  /** Controlled level, 0…1 of the 120 dB scale. Leave undefined to show `level` (or the autoPlay loop). */
  progress?: number;
  /** Swings from silence up to `level` like a meter needle, holds, drops and starts over. */
  autoPlay?: boolean;
  /** Reading in dB shown at rest: 40 and 60 are the two cards of the reference. */
  level?: number;
  /** Fixed colour. When omitted the meter is green and turns yellow as it passes 50 dB. */
  tone?: Tone;
  title?: string;
  lowLabel?: string;
  highLabel?: string;
};

const SIZE = 164;
const RADIUS = 31;
// The canvas overflows the card by this much on every side to fit its shadow.
const BLEED = 24;

const MAX_DB = 120;
const BAND = 30;

const TROUGH_X = 16.5;
const TROUGH_Y = 106.5;
const TROUGH_W = 131;
const TROUGH_H = 41.5;
const TROUGH_R = 7;
// The block stops short of the level, a thin needle marks it and the empty trough starts after.
const BLOCK_GAP = 2.4;
const NEEDLE_AT = 1.1;
const NEEDLE_W = 1.8;
const NEEDLE_H = 33;
const REST_GAP = 4.6;

const STRIPE_PITCH = 4.4;
const STRIPE_SLANT = 11;

// Signal bars: centre x, resting height.
const BARS = [
  { x: 127.4, h: 5.4 },
  { x: 134.4, h: 12.1 },
  { x: 141.2, h: 18.8 },
];
const BARS_BOTTOM = 61.2;
const BAR_W = 2.2;

// autoPlay loop (seconds).
const START = 0.45;
const RISE = 1.7;
const HOLD = 3.6;
const RESET = 0.6;
const LOOP = START + RISE + HOLD + RESET;
// A moment in the loop where everything is at rest: used when nothing is playing.
const REST_T = START + RISE + 1;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

const TONES = {
  green: { from: '#1ccf45', to: '#30e96b', glow: 'rgba(40,220,90,0.55)', needle: '#86ac96', bar: '#22d94e' },
  yellow: { from: '#e7b400', to: '#f9ea00', glow: 'rgba(246,214,0,0.5)', needle: '#b9ad90', bar: '#f1c500' },
};

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

// A damped swing: shoots past the target by about 8% and settles, like a meter needle.
function swing(u: number) {
  'worklet';
  return 1 - Math.exp(-8 * u) * Math.cos(10 * u);
}

/** Level (0…1 of the scale) at `t` seconds into the autoPlay loop. */
function loopValue(t: number, rest: number) {
  'worklet';
  if (t < START) return 0;
  if (t < START + RISE) return rest * swing((t - START) / RISE);
  if (t < LOOP - RESET) return rest;
  return rest * (1 - easeInOutCubic((t - (LOOP - RESET)) / RESET));
}

/** 0…1: how lively the meter is at `t` seconds into the loop. */
function loopActivity(t: number) {
  'worklet';
  return clamp01((t - START) / 0.15) * clamp01((START + RISE * 0.85 - t) / 0.6);
}

const STRIPES = (() => {
  let d = '';
  for (let x = -STRIPE_SLANT - STRIPE_PITCH * 2; x < TROUGH_W + STRIPE_PITCH * 2; x += STRIPE_PITCH) {
    d += `M${x.toFixed(2)} -1L${(x + STRIPE_SLANT).toFixed(2)} ${TROUGH_H + 1}`;
  }
  return d;
})();

type Motion = {
  /** 0…1 of the 120 dB scale. */
  value: SharedValue<number>;
  time: SharedValue<number>;
  activity: SharedValue<number>;
  /** 0 = green, 1 = yellow. */
  mix: SharedValue<number>;
  /** 0 with reduced motion: no drift, no tremble, no breathing. */
  ambient: number;
};

type FillProps = {
  tone: Tone;
  edge: SharedValue<number>;
  time: SharedValue<number>;
  mix: SharedValue<number>;
  ambient: number;
};

function Fill({ tone, edge, time, mix, ambient }: FillProps) {
  const colors = TONES[tone];
  const x = BLEED + TROUGH_X;
  const y = BLEED + TROUGH_Y;
  const block = useDerivedValue(() => {
    const width = Math.max(0, edge.get() - BLOCK_GAP);
    const r = Math.min(TROUGH_R, width / 2);
    return { rect: { x, y, width, height: TROUGH_H }, rx: r, ry: r };
  });
  const glow = useDerivedValue(() => {
    const width = Math.max(0, edge.get() - BLOCK_GAP);
    const r = Math.min(TROUGH_R, width / 2);
    return { rect: { x, y: y + 2, width, height: TROUGH_H }, rx: r, ry: r };
  });
  // The yellow copy fades in over the green one, whose glow has to leave or it would show around it.
  const opacity = useDerivedValue(() => (tone === 'yellow' ? mix.get() : 1));
  const glowAlpha = useDerivedValue(
    () =>
      (tone === 'yellow' ? 1 : 1 - mix.get()) *
      clamp01(edge.get() / 14) *
      (0.8 + ambient * 0.2 * Math.sin(time.get() * 2.1)),
  );
  // The texture is pinned to the leading edge, so it travels with the level, and drifts slowly at rest.
  const stripes = useDerivedValue(() => {
    const shift = (((edge.get() * 0.7 + time.get() * 2.6) % STRIPE_PITCH) + STRIPE_PITCH) % STRIPE_PITCH;
    return [{ translateX: x + shift }, { translateY: y }];
  });
  const needle = useDerivedValue(() => clamp01((edge.get() - 5) / 8));
  const needleX = useDerivedValue(() => x + edge.get() + NEEDLE_AT - NEEDLE_W / 2);

  return (
    <Group opacity={opacity}>
      <RoundedRect rect={glow} color={colors.glow} opacity={glowAlpha}>
        <BlurMask blur={7} style="normal" />
      </RoundedRect>
      <RoundedRect rect={block}>
        <LinearGradient start={vec(x, y)} end={vec(x + TROUGH_W * 0.55, y + TROUGH_H)} colors={[colors.from, colors.to]} />
      </RoundedRect>
      <Group clip={block}>
        <Path path={STRIPES} transform={stripes} style="stroke" strokeWidth={0.8} color="rgba(255,255,255,0.22)" />
      </Group>
      <RoundedRect
        x={needleX}
        y={y + (TROUGH_H - NEEDLE_H) / 2}
        width={NEEDLE_W}
        height={NEEDLE_H}
        r={NEEDLE_W / 2}
        color={colors.needle}
        opacity={needle}
      />
    </Group>
  );
}

type BarProps = {
  index: number;
  tone: Tone;
  value: SharedValue<number>;
  time: SharedValue<number>;
  activity: SharedValue<number>;
  opacity: SharedValue<number> | number;
};

function Bar({ index, tone, value, time, activity, opacity }: BarProps) {
  const bar = BARS[index];
  // Each bar grows as the level passes 10, 20 and 30 dB, and bounces like an equaliser while it moves.
  const height = useDerivedValue(() => {
    const grown = clamp01((value.get() * MAX_DB - index * 9) / 12);
    const bounce = 1 + activity.get() * 0.32 * Math.sin(time.get() * (17 - index * 3.5) + index * 2.1);
    return Math.min(BARS[2].h + 1.5, Math.max(BAR_W, bar.h * grown * bounce));
  });
  const y = useDerivedValue(() => BLEED + BARS_BOTTOM - height.get());
  return (
    <RoundedRect
      x={BLEED + bar.x - BAR_W / 2}
      y={y}
      width={BAR_W}
      height={height}
      r={BAR_W / 2}
      color={TONES[tone].bar}
      opacity={opacity}
    />
  );
}

function Surface({ value, time, activity, mix, ambient }: Motion) {
  const x = BLEED;
  const y = BLEED;
  const tx = x + TROUGH_X;
  const ty = y + TROUGH_Y;

  // Where the level sits in the trough, with the faint tremble of a live microphone.
  const edge = useDerivedValue(() => {
    const tremble = ambient * 0.45 * Math.sin(time.get() * 9.1) * Math.sin(time.get() * 2.3);
    return TROUGH_W * clamp01(value.get()) + (value.get() > 0.02 ? tremble : 0);
  });
  const rest = useDerivedValue(() => {
    const left = Math.min(TROUGH_W, edge.get() < 5 ? edge.get() : edge.get() + REST_GAP);
    const width = TROUGH_W - left;
    const r = Math.min(TROUGH_R, width / 2);
    return { rect: { x: tx + left, y: ty, width, height: TROUGH_H }, rx: r, ry: r };
  });
  const restShade = useDerivedValue(() => [{ translateX: rest.get().rect.x }]);
  const restShadeAlpha = useDerivedValue(() => clamp01((edge.get() - 5) / 8));

  return (
    <Canvas style={styles.canvas}>
      <RoundedRect x={x + 4} y={y + 10} width={SIZE - 8} height={SIZE - 8} r={RADIUS} color="rgba(96,106,130,0.28)">
        <BlurMask blur={12} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={SIZE} height={SIZE} r={RADIUS}>
        <LinearGradient start={vec(x, y)} end={vec(x, y + SIZE)} colors={['#eeeff3', '#e9eaee']} />
      </RoundedRect>
      <RoundedRect x={x + 0.5} y={y + 0.5} width={SIZE - 1} height={SIZE - 1} r={RADIUS - 0.5} style="stroke" strokeWidth={1}>
        <LinearGradient start={vec(x, y)} end={vec(x, y + SIZE)} colors={['rgba(255,255,255,0.9)', 'rgba(255,255,255,0.25)']} />
      </RoundedRect>

      {/* Empty part of the trough, a touch warmer where it meets the needle. */}
      <RoundedRect rect={rest} color="#d9dbe3" />
      <Group clip={rest}>
        <RoundedRect x={0} y={ty} width={18} height={TROUGH_H} r={0} transform={restShade} opacity={restShadeAlpha}>
          <LinearGradient start={vec(0, 0)} end={vec(18, 0)} colors={['rgba(212,211,212,0.6)', 'rgba(212,211,212,0)']} />
        </RoundedRect>
      </Group>

      <Fill tone="green" edge={edge} time={time} mix={mix} ambient={ambient} />
      <Fill tone="yellow" edge={edge} time={time} mix={mix} ambient={ambient} />

      {BARS.map((_, i) => (
        <Bar key={i} index={i} tone="green" value={value} time={time} activity={activity} opacity={1} />
      ))}
      {BARS.map((_, i) => (
        <Bar key={i} index={i} tone="yellow" value={value} time={time} activity={activity} opacity={mix} />
      ))}
    </Canvas>
  );
}

/**
 * Soft light widget with a big dB reading, a signal-bars glyph and a Low–High trough holding a
 * chunky striped block. The level swings in like a meter needle and overshoots, the stripes
 * travel with it, the bars bounce like an equaliser while it moves, the block turns from green to
 * yellow past 50 dB and the number inside it steps through the 30 dB bands with a pop.
 */
export function NoiseLevelMeter({
  progress,
  autoPlay = false,
  level = 40,
  tone,
  title = 'Current noise level',
  lowLabel = 'Low',
  highLabel = 'High',
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const restValue = Math.min(1, Math.max(0, level / MAX_DB));
  const target = controlled ? Math.min(1, Math.max(0, progress)) : restValue;

  // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
  const time = useSharedValue(0);
  const clock = useSharedValue(autoPlay && !controlled && !reducedMotion ? 0 : REST_T);
  const playing = useSharedValue(false);
  const looping = useSharedValue(false);
  const held = useSharedValue(target);
  // When a controlled value last changed, on the `time` axis.
  const movedAt = useSharedValue(-10);
  const pop = useSharedValue(1);

  useEffect(() => {
    const auto = autoPlay && !controlled && !reducedMotion;
    looping.set(auto);
    playing.set(auto);
    clock.set(auto ? 0 : REST_T);
  }, [autoPlay, controlled, reducedMotion, clock, looping, playing]);

  useEffect(() => {
    if (!controlled) return;
    movedAt.set(time.get());
    held.set(withTiming(target, { duration: reducedMotion ? 200 : 700, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
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

  const value = useDerivedValue(() => (controlled ? held.get() : loopValue(clock.get(), restValue)));
  const activity = useDerivedValue(() => {
    if (reducedMotion) return 0;
    if (!controlled) return loopActivity(clock.get());
    const since = time.get() - movedAt.get();
    return clamp01(since / 0.15) * clamp01((0.9 - since) / 0.5);
  });
  const mix = useDerivedValue(() => {
    if (tone) return tone === 'yellow' ? 1 : 0;
    return clamp01((value.get() * MAX_DB - 46) / 8);
  });

  // The numbers are real text: re-render only when a whole dB (or a band) changes.
  const [decibels, setDecibels] = useState(Math.round(target * MAX_DB));
  useAnimatedReaction(
    () => Math.round(value.get() * MAX_DB),
    (now, before) => {
      if (now !== before) scheduleOnRN(setDecibels, now);
    },
  );
  const [band, setBand] = useState(Math.floor((target * MAX_DB + 0.5) / BAND) * BAND);
  useAnimatedReaction(
    () => Math.floor((value.get() * MAX_DB + 0.5) / BAND) * BAND,
    (now, before) => {
      if (now === before || before == null) return;
      scheduleOnRN(setBand, now);
      if (!reducedMotion && now > before) {
        pop.set(withSequence(withTiming(1.28, { duration: 90 }), withTiming(1, { duration: 320, easing: EASE_OUT })));
      }
    },
  );

  const bandStyle = useAnimatedStyle(() => ({
    opacity: clamp01((value.get() * MAX_DB - 26) / 4),
    transform: [{ scale: pop.get() }],
  }));
  // "120" hides as the block closes in on it.
  const maxStyle = useAnimatedStyle(() => ({ opacity: clamp01((0.78 - value.get()) / 0.06) }));

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  return (
    <Pressable onPress={restart} accessible={false} style={styles.card}>
      <View pointerEvents="none" style={styles.surface}>
        <Surface value={value} time={time} activity={activity} mix={mix} ambient={reducedMotion ? 0 : 1} />
      </View>

      <View
        style={StyleSheet.absoluteFill}
        pointerEvents="none"
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={title}
        accessibilityValue={{ min: 0, max: MAX_DB, now: decibels, text: `${decibels} dB` }}
      >
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <View style={styles.reading}>
          <Text style={styles.number}>{decibels}</Text>
          <Text style={styles.unit}>dB</Text>
        </View>
        <Text style={[styles.scale, styles.low]}>{lowLabel}</Text>
        <Text style={[styles.scale, styles.high]}>{highLabel}</Text>
        <Animated.Text style={[styles.mark, styles.max, maxStyle]}>{MAX_DB}</Animated.Text>
        <Animated.Text style={[styles.mark, styles.band, bandStyle]}>{band}</Animated.Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    width: SIZE,
    height: SIZE,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  canvas: {
    width: SIZE + BLEED * 2,
    height: SIZE + BLEED * 2,
  },
  title: {
    position: 'absolute',
    left: 16.5,
    top: 12,
    color: '#7c8099',
    fontSize: 13.2,
    lineHeight: 18,
    fontWeight: '300',
    letterSpacing: -0.1,
  },
  reading: {
    position: 'absolute',
    left: 16.5,
    top: 28,
    flexDirection: 'row',
    alignItems: 'flex-start',
  },
  number: {
    color: '#0b0d12',
    fontSize: 39.5,
    lineHeight: 46,
    fontWeight: '700',
    letterSpacing: -0.6,
    fontVariant: ['tabular-nums'],
  },
  unit: {
    marginLeft: 6,
    marginTop: 4.5,
    color: '#7c8099',
    fontSize: 16,
    lineHeight: 20,
    fontWeight: '400',
  },
  scale: {
    position: 'absolute',
    top: 93.5,
    color: '#8286a0',
    fontSize: 8.3,
    lineHeight: 11,
    fontWeight: '400',
  },
  low: {
    left: 16.5,
  },
  high: {
    right: 16.5,
  },
  mark: {
    position: 'absolute',
    top: TROUGH_Y + TROUGH_H - 18.5,
    fontSize: 11.6,
    lineHeight: 14,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  band: {
    left: TROUGH_X + 4.6,
    color: '#ffffff',
  },
  max: {
    right: 16.5 + 4.6,
    color: 'rgba(255,255,255,0.72)',
  },
});
