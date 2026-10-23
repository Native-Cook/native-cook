import { BlurMask, Canvas, Circle, Group, LinearGradient, Path, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import { useEffect, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';
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
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Stat = { value: number; suffix?: string; label: string };

type Props = {
  /** Controlled constancy, 0…1: the big ring and its percentage. */
  progress?: number;
  /** Draws the rings up to the values shown in the reference, holds, and starts over. */
  autoPlay?: boolean;
  /** Constancy at the resting frame, 0…1. */
  constancy?: number;
  label?: string;
  /** The two small rings: value out of 100 and caption. */
  week?: number;
  weekLabel?: string;
  day?: number;
  dayLabel?: string;
  caption?: string;
  stats?: [Stat, Stat, Stat];
};

const WIDTH = 340;
const CARD_H = 285;
const CARD_R = 28.5;
const TILE_Y = 308.7;
const TILE_W = 106.5;
const TILE_H = 68;
const TILE_STEP = 116.75;
const TILE_R = 15;
const HEIGHT = TILE_Y + TILE_H;
// The canvas overflows the component by this much on every side to fit the soft shadows.
const BLEED = 30;

const CX = 170;
const CY = 115.7;
const RING_R = 81.4;
const RING_W = 13.7;
const SMALL_X = [44.7, 294.7];
const SMALL_Y = 107.7;
const SMALL_R = 22.5;
const SMALL_W = 4.8;

const ORANGE = '#f0742d';
const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });

// autoPlay loop (seconds).
const START = 0.5;
const DRAW = 2.4;
const HOLD = 4.6;
const RESET = 0.7;
const LOOP = START + DRAW + HOLD + RESET;
// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
const REST_T = START + DRAW + 1.8;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const TAU = Math.PI * 2;

// Sparks thrown off the head of the big ring while it draws: sideways spread and phase of each.
const EMBERS = [
  { side: -0.9, phase: 0.0, r: 1.9 },
  { side: 0.5, phase: 0.14, r: 1.5 },
  { side: -0.2, phase: 0.29, r: 2.1 },
  { side: 0.95, phase: 0.43, r: 1.4 },
  { side: -0.6, phase: 0.57, r: 1.7 },
  { side: 0.15, phase: 0.71, r: 2.0 },
  { side: 0.7, phase: 0.86, r: 1.5 },
];

/** A full turn starting at 12 o'clock, clockwise: trimmed with `end` to draw a ring. */
function turn(cx: number, cy: number, r: number) {
  return `M${cx} ${cy - r}A${r} ${r} 0 1 1 ${cx - 0.01} ${cy - r}`;
}

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

function easeOutCubic(t: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(t), 3);
}

/** Constancy at `t` seconds into the autoPlay loop, where `rest` is the reference value. */
function loopValue(t: number, rest: number) {
  'worklet';
  if (t < START) return 0;
  if (t < START + DRAW) return rest * easeOutCubic((t - START) / DRAW);
  if (t < START + DRAW + HOLD) return rest;
  return rest * (1 - easeInOutCubic((t - START - DRAW - HOLD) / RESET));
}

/** 0…1: how far small ring `index` is drawn when the big ring is at `share` of `rest`. The second starts later. */
function smallAt(share: number, rest: number, index: number) {
  'worklet';
  return easeOutCubic(clamp01(share / Math.max(rest, 0.001)) * 1.25 - 0.25 * index);
}

/** 0…1: how far stat tile `index` has risen into place at `t` seconds into the loop. */
function tileAt(t: number, index: number) {
  'worklet';
  if (t >= START + DRAW + HOLD) return 1 - clamp01((t - START - DRAW - HOLD) / (RESET * 0.6));
  return easeOutCubic((t - 0.15 - 0.14 * index) / 0.55);
}

/** Scale after the ring lands: a quick overshoot. */
function pop(since: number, amount: number) {
  'worklet';
  const u = clamp01(since / 0.55);
  return 1 + amount * Math.sin(Math.PI * u) * (1 - u);
}

type Motion = {
  /** Constancy, 0…1. */
  value: SharedValue<number>;
  time: SharedValue<number>;
  /** Position in the autoPlay loop: drives the entrance of the stat tiles. */
  clock: SharedValue<number>;
  /** When the big ring last reached its value, on the `time` axis. */
  landed: SharedValue<number>;
  /** 0…1: how fast the ring is moving. */
  moving: SharedValue<number>;
  /** 0 with reduced motion: no sparks, breathing or pops. */
  ambient: number;
  rest: number;
};

function SmallRing({ index, full, value, rest }: { index: number; full: number } & Pick<Motion, 'value' | 'rest'>) {
  const cx = BLEED + SMALL_X[index];
  const cy = BLEED + SMALL_Y;
  const end = useDerivedValue(() => (full / 100) * smallAt(value.get(), rest, index));
  return (
    <>
      <Circle cx={cx} cy={cy} r={SMALL_R} style="stroke" strokeWidth={SMALL_W} color="#222328" />
      <Path path={turn(cx, cy, SMALL_R)} style="stroke" strokeWidth={SMALL_W} strokeCap="round" color={ORANGE} end={end} />
    </>
  );
}

function TilePlate({ index, clock }: { index: number; clock: SharedValue<number> }) {
  const x = BLEED + TILE_STEP * index;
  const y = BLEED + TILE_Y;
  const transform = useDerivedValue(() => [{ translateY: 10 * (1 - tileAt(clock.get(), index)) }]);
  const opacity = useDerivedValue(() => tileAt(clock.get(), index));
  return (
    <Group transform={transform} opacity={opacity}>
      <RoundedRect x={x + 4} y={y + 6} width={TILE_W} height={TILE_H} r={TILE_R} color="rgba(0,0,0,0.34)">
        <BlurMask blur={8} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={TILE_W} height={TILE_H} r={TILE_R}>
        <LinearGradient start={vec(x, y)} end={vec(x + TILE_W * 0.4, y + TILE_H)} colors={['#37393f', '#303238']} />
      </RoundedRect>
      <RoundedRect x={x + 0.5} y={y + 0.5} width={TILE_W - 1} height={TILE_H - 1} r={TILE_R - 0.5} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.075)" />
    </Group>
  );
}

function Surface(motion: Motion & { week: number; day: number }) {
  const { value, time, clock, landed, moving, ambient } = motion;
  const x = BLEED;
  const y = BLEED;
  const cx = x + CX;
  const cy = y + CY;

  const end = useDerivedValue(() => clamp01(value.get()));
  const angle = useDerivedValue(() => -Math.PI / 2 + TAU * clamp01(value.get()));
  const hx = useDerivedValue(() => cx + RING_R * Math.cos(angle.get()));
  const hy = useDerivedValue(() => cy + RING_R * Math.sin(angle.get()));
  const ringOpacity = useDerivedValue(() => clamp01(value.get() * 80));
  // The glow behind the ring breathes slowly.
  const glow = useDerivedValue(() => ringOpacity.get() * (0.3 + ambient * (0.13 * Math.sin(time.get() * 1.7) + 0.25 * moving.get())));
  // The head burns white while the ring is drawing.
  const hot = useDerivedValue(() => ringOpacity.get() * ambient * moving.get());
  const embers = useDerivedValue(() => {
    const path = Skia.Path.Make();
    const power = ambient * moving.get();
    if (power < 0.02) return path;
    const a = angle.get();
    for (const ember of EMBERS) {
      const u = (time.get() * 1.5 + ember.phase) % 1;
      // Left behind the head, drifting off the ring to either side.
      const trail = a - u * 0.42;
      const r = RING_R + ember.side * (4 + 13 * u);
      path.addCircle(cx + r * Math.cos(trail), cy + r * Math.sin(trail), ember.r * (1 - u) * power);
    }
    return path;
  });
  // When the ring lands, and every few seconds after, a ripple leaves its head.
  const ripple = useDerivedValue(() => (((time.get() - landed.get()) % 3.4) + 3.4) % 3.4 / 0.9);
  const rippleR = useDerivedValue(() => RING_W / 2 + 12 * easeOutCubic(ripple.get()));
  const rippleOpacity = useDerivedValue(() => {
    const s = ripple.get();
    return s >= 1 || moving.get() > 0.05 || value.get() < 0.01 ? 0 : ambient * 0.55 * (1 - s);
  });

  return (
    <Canvas style={styles.canvas}>
      {/* The card: raised from the page by a dark shadow below and a faint light above. */}
      <RoundedRect x={x + 6} y={y + 9} width={WIDTH} height={CARD_H} r={CARD_R} color="rgba(0,0,0,0.38)">
        <BlurMask blur={13} style="normal" />
      </RoundedRect>
      <RoundedRect x={x - 4} y={y - 4} width={WIDTH} height={CARD_H} r={CARD_R} color="rgba(255,255,255,0.03)">
        <BlurMask blur={9} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={WIDTH} height={CARD_H} r={CARD_R}>
        <LinearGradient start={vec(x, y)} end={vec(x + WIDTH * 0.35, y + CARD_H)} colors={['#373940', '#2f3137']} />
      </RoundedRect>
      <RoundedRect x={x + 0.5} y={y + 0.5} width={WIDTH - 1} height={CARD_H - 1} r={CARD_R - 0.5} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.075)" />

      <Circle cx={cx} cy={cy} r={RING_R} style="stroke" strokeWidth={RING_W} color="#202125" />
      <Path path={turn(cx, cy, RING_R)} style="stroke" strokeWidth={RING_W + 4} strokeCap="round" color={ORANGE} end={end} opacity={glow}>
        <BlurMask blur={9} style="normal" />
      </Path>
      <Path path={turn(cx, cy, RING_R)} style="stroke" strokeWidth={RING_W} strokeCap="round" color={ORANGE} end={end} opacity={ringOpacity} />
      <Circle cx={hx} cy={hy} r={rippleR} style="stroke" strokeWidth={1.6} color="#ffb78a" opacity={rippleOpacity} />
      <Path path={embers} color="#ffc9a2" />
      <Circle cx={hx} cy={hy} r={RING_W / 2 + 2} color="#fff1e4" opacity={hot}>
        <BlurMask blur={5} style="normal" />
      </Circle>

      <SmallRing index={0} full={motion.week} {...motion} />
      <SmallRing index={1} full={motion.day} {...motion} />
      {[0, 1, 2].map((index) => (
        <TilePlate key={index} index={index} clock={clock} />
      ))}
    </Canvas>
  );
}

/** A number that counts with the rings without re-rendering the whole card. */
function Count({ of, initial, style, suffix = '' }: { of: SharedValue<number>; initial: number; style: object | object[]; suffix?: string }) {
  const [shown, setShown] = useState(Math.round(initial));
  useAnimatedReaction(
    () => Math.round(of.get()),
    (now, old) => {
      if (now !== old) scheduleOnRN(setShown, now);
    },
  );
  return (
    <Text style={style} numberOfLines={1}>
      {shown}
      {suffix}
    </Text>
  );
}

function TileText({ index, stat, start, value, clock, rest }: { index: number; stat: Stat; start: number } & Pick<Motion, 'value' | 'clock' | 'rest'>) {
  const count = useDerivedValue(() => stat.value * easeOutCubic(clamp01(value.get() / Math.max(rest, 0.001))));
  const style = useAnimatedStyle(() => {
    const at = tileAt(clock.get(), index);
    return { opacity: at, transform: [{ translateY: 10 * (1 - at) }] };
  });
  return (
    <Animated.View style={[styles.tile, { left: TILE_STEP * index }, style]}>
      <Count of={count} initial={stat.value * easeOutCubic(start / Math.max(rest, 0.001))} style={styles.statValue} suffix={stat.suffix} />
      <Text style={styles.statLabel} numberOfLines={1}>
        {stat.label}
      </Text>
    </Animated.View>
  );
}

/**
 * Habit tracker summary: a big constancy ring between two small ones on a soft raised card, with
 * three stat tiles under it. The big ring draws behind a white-hot head that throws sparks, its
 * glow breathes and a ripple keeps leaving the head once it has landed; the percentage counts up
 * and settles with a small bounce, the small rings follow, and the tiles rise in one by one.
 */
export function HabitRing({
  progress,
  autoPlay = false,
  constancy = 0.82,
  label = 'CONSTANCE',
  week = 90,
  weekLabel = 'SEMAINE',
  day = 53,
  dayLabel = 'JOUR',
  caption = 'Average of 18 habits over 18 days\nelapsed.',
  stats = [
    { value: 202, label: 'CHECK-INS' },
    { value: 18, suffix: ' j', label: 'BEST STREAK' },
    { value: 14, label: 'PERFECT DAYS' },
  ],
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const rest = constancy;
  const target = controlled ? Math.min(1, Math.max(0, progress)) : rest;
  const auto = autoPlay && !controlled && !reducedMotion;

  // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
  const time = useSharedValue(0);
  const clock = useSharedValue(auto ? 0 : REST_T);
  const playing = useSharedValue(false);
  const looping = useSharedValue(false);
  const held = useSharedValue(target);
  const before = useSharedValue(auto ? 0 : target);
  const moving = useSharedValue(0);
  const landed = useSharedValue(-10);

  useEffect(() => {
    looping.set(auto);
    playing.set(auto);
    clock.set(auto ? 0 : REST_T);
  }, [auto, clock, looping, playing]);

  useEffect(() => {
    if (!controlled) return;
    held.set(withTiming(target, { duration: reducedMotion ? 200 : 1600, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
  }, [controlled, target, reducedMotion, held]);

  const value = useDerivedValue(() => (controlled ? held.get() : loopValue(clock.get(), rest)));

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    const now = time.get() + dt;
    time.set(now);
    if (playing.get()) {
      let t = clock.get() + dt;
      if (looping.get()) {
        if (t >= LOOP) t -= LOOP;
      } else if (t >= REST_T) {
        // A tapped, one-off run stops at the resting frame.
        t = REST_T;
        playing.set(false);
      }
      clock.set(t);
    }
    const share = value.get();
    const was = before.get();
    before.set(share);
    const speed = clamp01((Math.max(0, share - was) / dt) * 4);
    const wasMoving = moving.get();
    // Smoothed so the sparks fade out instead of cutting off.
    moving.set(wasMoving + (speed - wasMoving) * Math.min(1, dt * 10));
    // The ring coming to rest sets off the bounce and the first ripple.
    if (wasMoving > 0.05 && moving.get() <= 0.05 && share > 0.01) landed.set(now);
  }, !reducedMotion);

  const percent = useDerivedValue(() => value.get() * 100);
  const weekCount = useDerivedValue(() => week * smallAt(value.get(), rest, 0));
  const dayCount = useDerivedValue(() => day * smallAt(value.get(), rest, 1));
  const ambient = reducedMotion ? 0 : 1;
  const figureStyle = useAnimatedStyle(() => ({
    transform: [{ scale: ambient > 0 ? pop(time.get() - landed.get(), 0.07) : 1 }],
  }));

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  // What the counters show before the first frame.
  const start = auto ? 0 : target;
  const motion: Motion & { week: number; day: number } = { value, time, clock, landed, moving, ambient, rest, week, day };
  const shownPercent = Math.round(target * 100);

  return (
    <Pressable
      onPress={restart}
      style={styles.root}
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={label}
      accessibilityValue={{
        min: 0,
        max: 100,
        now: shownPercent,
        text: `${shownPercent}%, ${weekLabel} ${week}, ${dayLabel} ${day}. ${caption.replace('\n', ' ')}`,
      }}
    >
      <View pointerEvents="none" style={styles.surface}>
        <Surface {...motion} />
      </View>

      <View pointerEvents="none" style={StyleSheet.absoluteFill}>
        <Animated.View style={[styles.figure, figureStyle]}>
          <Count of={percent} initial={start * 100} style={styles.percent} />
          <Text style={styles.sign}>%</Text>
        </Animated.View>
        <Text style={styles.label} numberOfLines={1}>
          {label}
        </Text>

        <Count of={weekCount} initial={week * smallAt(start, rest, 0)} style={[styles.smallValue, { left: SMALL_X[0] - 20 }]} />
        <Text style={[styles.smallLabel, { left: SMALL_X[0] - 40 }]} numberOfLines={1}>
          {weekLabel}
        </Text>
        <Count of={dayCount} initial={day * smallAt(start, rest, 1)} style={[styles.smallValue, { left: SMALL_X[1] - 20 }]} />
        <Text style={[styles.smallLabel, { left: SMALL_X[1] - 40 }]} numberOfLines={1}>
          {dayLabel}
        </Text>

        <Text style={styles.caption} numberOfLines={2}>
          {caption}
        </Text>

        {[0, 1, 2].map((index) => (
          <TileText key={index} index={index} stat={stats[index]} start={start} value={value} clock={clock} rest={rest} />
        ))}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: {
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
  figure: {
    position: 'absolute',
    left: 0,
    top: 72.4,
    width: WIDTH,
    height: 64,
    paddingLeft: 3.4,
    flexDirection: 'row',
    justifyContent: 'center',
  },
  percent: {
    color: '#f0f0f2',
    fontSize: 58,
    lineHeight: 64,
    fontWeight: '800',
    letterSpacing: -1.5,
    fontVariant: ['tabular-nums'],
  },
  sign: {
    marginTop: 1.5,
    marginLeft: 1,
    color: '#f0f0f2',
    fontSize: 24,
    lineHeight: 28,
    fontWeight: '800',
  },
  label: {
    position: 'absolute',
    left: 0,
    top: 143.7,
    width: WIDTH,
    paddingLeft: 3.1,
    textAlign: 'center',
    color: '#9a9da4',
    fontFamily: MONO,
    fontSize: 9,
    lineHeight: 12,
    letterSpacing: 3.1,
  },
  smallValue: {
    position: 'absolute',
    top: SMALL_Y - 8,
    width: 40,
    textAlign: 'center',
    color: '#f0f0f2',
    fontSize: 11.8,
    lineHeight: 16,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  smallLabel: {
    position: 'absolute',
    top: 137.8,
    width: 80,
    paddingLeft: 2.2,
    textAlign: 'center',
    color: '#8b8f97',
    fontFamily: MONO,
    fontSize: 8.2,
    lineHeight: 12,
    letterSpacing: 2.2,
  },
  caption: {
    position: 'absolute',
    left: 50,
    top: 220.4,
    width: WIDTH - 100,
    textAlign: 'center',
    color: '#9b9ea6',
    fontSize: 12.6,
    lineHeight: 18.5,
    letterSpacing: -0.2,
  },
  tile: {
    position: 'absolute',
    top: TILE_Y,
    width: TILE_W,
    height: TILE_H,
  },
  statValue: {
    position: 'absolute',
    left: 14.9,
    top: 11.7,
    color: '#f0f0f2',
    fontSize: 20.8,
    lineHeight: 26,
    fontWeight: '800',
    letterSpacing: -0.3,
    fontVariant: ['tabular-nums'],
  },
  statLabel: {
    position: 'absolute',
    left: 14.9,
    top: 41.7,
    color: '#8b8f97',
    fontFamily: MONO,
    fontSize: 8.2,
    lineHeight: 12,
    letterSpacing: 1.3,
  },
});
