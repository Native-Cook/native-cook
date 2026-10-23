import { BlurMask, Canvas, Circle, Group, LinearGradient, Path, RadialGradient, RoundedRect, vec } from '@shopify/react-native-skia';
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
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Controlled share of the day's figures reached, 0…1: at 1 the rings show their full values. */
  progress?: number;
  /** Draws the rings one after another up to the values shown in the reference, holds, and starts over. */
  autoPlay?: boolean;
  title?: string;
  sleep?: string;
  /** Change against yesterday, shown in green with an up arrow. */
  sleepChange?: string;
  sleepLabel?: string;
  heartRate?: string;
  heartLabel?: string;
  /** Beats per minute the heart glyph pulses at. */
  bpm?: number;
  /** The numbers the blue, green and red rings count up to. */
  values?: [number, number, number];
  score?: number;
  scoreLabel?: string;
};

const WIDTH = 344;
const HEIGHT = 148;
const RADIUS = 34;
// The canvas overflows the card by this much on every side to fit its shadow.
const BLEED = 40;

// Centre of the rings and of the white disc in the middle of them.
const CX = 306.8;
const CY = 111.6;
const DISC_R = 35.9;
const RING_W = 19.6;
const BADGE_R = 8.5;

// Outside in: radius, degrees swept at full value (clockwise from 6 o'clock), colours, and where
// in the overall progress the ring starts drawing.
const RINGS = [
  { r: 91.6, sweep: 119.7, color: '#48a8e2', badge: '#80d2f8', ink: '#0b3a66', delay: 0 },
  { r: 68.2, sweep: 183.3, color: '#5ee24a', badge: '#92f37f', ink: '#0d4a0a', delay: 0.15 },
  { r: 44.5, sweep: 153.1, color: '#e84a66', badge: '#f27c90', ink: '#5a0a16', delay: 0.3 },
];
// Share of the overall progress one ring takes to draw.
const RING_SPAN = 0.7;

// The reference shows every ring at its full value; the autoPlay loop built around it (seconds).
const REST = 1;
const START = 0.4;
const DRAW = 2.8;
const HOLD = 3.8;
const RESET = 0.7;
const LOOP = START + DRAW + HOLD + RESET;
// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
const REST_T = START + DRAW + 1.6;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const DEG = Math.PI / 180;

const MOON =
  'M3.6 -8.3C-2.6 -10 -9.2 -5.4 -9.2 0.6C-9.2 5.8 -5 9.4 0 9.4C4.4 9.4 7.8 6.8 8.9 3C5.8 3.9 2.6 2.2 1.7 -1C1 -3.6 1.8 -6.6 3.6 -8.3Z';
const HEART =
  'M0 7.6C-5.4 3.6 -8.6 0.8 -8.6 -2.8C-8.6 -5.6 -6.6 -7.4 -4.2 -7.4C-2.4 -7.4 -0.9 -6.4 0 -4.9C0.9 -6.4 2.4 -7.4 4.2 -7.4C6.6 -7.4 8.6 -5.6 8.6 -2.8C8.6 0.8 5.4 3.6 0 7.6Z';

/** The arc a ring covers at full value, starting straight below the centre. */
function arcPath(r: number, sweep: number) {
  const end = (90 + sweep) * DEG;
  const x = BLEED + CX + r * Math.cos(end);
  const y = BLEED + CY + r * Math.sin(end);
  return `M${BLEED + CX} ${BLEED + CY + r}A${r} ${r} 0 ${sweep > 180 ? 1 : 0} 1 ${x.toFixed(3)} ${y.toFixed(3)}`;
}

const ARCS = RINGS.map((ring) => arcPath(ring.r, ring.sweep));

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

/** Overall progress at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < START) return 0;
  if (t < START + DRAW) return (t - START) / DRAW;
  if (t < START + DRAW + HOLD) return 1;
  return 1 - easeInOutCubic((t - START - DRAW - HOLD) / RESET);
}

/** 0…1: how far ring `index` is drawn at overall progress `p`. The rings start one after another. */
function ringAt(p: number, index: number) {
  'worklet';
  return easeOutCubic((clamp01(p) - RINGS[index].delay) / RING_SPAN);
}

/** One heartbeat, 0…1, at `phase` (0…1) of the beat: a strong thump and a softer one after it. */
function heartbeat(phase: number) {
  'worklet';
  if (phase < 0.16) return Math.sin((Math.PI * phase) / 0.16);
  if (phase > 0.24 && phase < 0.4) return 0.55 * Math.sin((Math.PI * (phase - 0.24)) / 0.16);
  return 0;
}

type Motion = {
  value: SharedValue<number>;
  time: SharedValue<number>;
  /** When each ring last reached its full value, on the `time` axis. */
  landed: [SharedValue<number>, SharedValue<number>, SharedValue<number>];
  /** 0 with reduced motion: no heartbeat, breathing, sheen or pops. */
  ambient: number;
};

/** Scale of a badge after its ring lands: a quick overshoot. */
function pop(since: number, ambient: number) {
  'worklet';
  const u = clamp01(since / 0.5);
  return 1 + ambient * 0.55 * Math.sin(Math.PI * u) * (1 - u);
}

function Ring({ index, value, time, landed, ambient }: Motion & { index: number }) {
  const ring = RINGS[index];
  const drawn = useDerivedValue(() => ringAt(value.get(), index));
  const angle = useDerivedValue(() => (90 + ring.sweep * drawn.get()) * DEG);
  const hx = useDerivedValue(() => BLEED + CX + ring.r * Math.cos(angle.get()));
  const hy = useDerivedValue(() => BLEED + CY + ring.r * Math.sin(angle.get()));
  // The head of the ring is bright while it is still drawing.
  const lead = useDerivedValue(() => clamp01(drawn.get() * 10) * clamp01((1 - drawn.get()) * 4) * 0.75);
  const badgeR = useDerivedValue(() => BADGE_R * pop(time.get() - landed[index].get(), ambient));
  // A sheen runs along the finished ring every few seconds.
  const sheenAt = useDerivedValue(() => ((time.get() + index * 0.5) % 3.4) / 1.3);
  const sheenEnd = useDerivedValue(() => drawn.get() * clamp01(sheenAt.get()));
  const sheenStart = useDerivedValue(() => Math.max(0, sheenEnd.get() - 16 / ring.sweep));
  const sheen = useDerivedValue(() => {
    const s = sheenAt.get();
    return s >= 1 || drawn.get() < 0.999 ? 0 : ambient * 0.34 * Math.sin(Math.PI * s);
  });

  return (
    <>
      <Circle cx={BLEED + CX} cy={BLEED + CY} r={ring.r} style="stroke" strokeWidth={RING_W + 7} color="#0a0a0a" />
      <Path path={ARCS[index]} style="stroke" strokeWidth={RING_W} strokeCap="round" color={ring.color} end={drawn} />
      <Path path={ARCS[index]} style="stroke" strokeWidth={RING_W - 7} strokeCap="round" color="#ffffff" start={sheenStart} end={sheenEnd} opacity={sheen}>
        <BlurMask blur={3} style="normal" />
      </Path>
      <Circle cx={hx} cy={hy} r={RING_W / 2} color="#ffffff" opacity={lead}>
        <BlurMask blur={4} style="normal" />
      </Circle>
      <Circle cx={hx} cy={hy} r={badgeR} color={ring.badge} />
    </>
  );
}

function Surface(motion: Motion & { bpm: number }) {
  const { time, landed, ambient, bpm } = motion;
  const x = BLEED;
  const y = BLEED;
  const card = { rect: { x, y, width: WIDTH, height: HEIGHT }, rx: RADIUS, ry: RADIUS };

  // The heart beats at the rate it reports; its glow follows.
  const beat = useDerivedValue(() => ambient * heartbeat((time.get() * bpm) / 60 % 1));
  const heart = useDerivedValue(() => [{ translateX: x + 29.6 }, { translateY: y + 115.4 }, { scale: 1 + 0.16 * beat.get() }]);
  const heartGlow = useDerivedValue(() => 0.55 + 0.45 * beat.get());
  // The moon's glow breathes slowly.
  const moonGlow = useDerivedValue(() => 0.6 + ambient * 0.3 * Math.sin(time.get() * 1.2));
  // When the last ring lands a ripple leaves the white disc.
  const ripple = useDerivedValue(() => (time.get() - landed[2].get()) / 0.9);
  const rippleR = useDerivedValue(() => DISC_R + 16 * easeOutCubic(ripple.get()));
  const rippleOpacity = useDerivedValue(() => {
    const s = ripple.get();
    return s <= 0 || s >= 1 ? 0 : ambient * 0.5 * (1 - s);
  });

  return (
    <Canvas style={styles.canvas}>
      <RoundedRect x={x + 12} y={y + 20} width={WIDTH - 24} height={HEIGHT - 12} r={RADIUS} color="rgba(0,0,0,0.5)">
        <BlurMask blur={16} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS}>
        <LinearGradient start={vec(x, y)} end={vec(x + WIDTH * 0.3, y + HEIGHT)} colors={['#1b1b1b', '#0e0e0e']} />
      </RoundedRect>

      <Group clip={card}>
        {/* Faint outer tracks behind the rings. */}
        <Circle cx={x + CX} cy={y + CY} r={139} style="stroke" strokeWidth={RING_W} color="rgba(255,255,255,0.018)" />
        <Circle cx={x + CX} cy={y + CY} r={115.5} style="stroke" strokeWidth={RING_W} color="rgba(255,255,255,0.03)" />
        {[0, 1, 2].map((index) => (
          <Ring key={index} index={index} {...motion} />
        ))}

        {/* White disc in the middle. */}
        <Circle cx={x + CX} cy={y + CY} r={rippleR} style="stroke" strokeWidth={1.6} color="#ffffff" opacity={rippleOpacity} />
        <Circle cx={x + CX} cy={y + CY + 1} r={DISC_R + 2} color="rgba(0,0,0,0.5)">
          <BlurMask blur={5} style="normal" />
        </Circle>
        <Circle cx={x + CX} cy={y + CY} r={DISC_R}>
          <RadialGradient c={vec(x + CX, y + CY)} r={DISC_R} colors={['#ffffff', '#ffffff', '#e6e6e6']} positions={[0, 0.82, 1]} />
        </Circle>
      </Group>

      {/* Moon and heart glyphs with their glow. */}
      <Group transform={[{ translateX: x + 29.6 }, { translateY: y + 71.4 }]}>
        <Path path={MOON} style="stroke" strokeWidth={3} color="#6f8cff" opacity={moonGlow}>
          <BlurMask blur={3.5} style="normal" />
        </Path>
        <Path path={MOON} color="#3a478c" />
        <Path path={MOON} style="stroke" strokeWidth={1.6} strokeJoin="round" color="#7592ff" />
      </Group>
      <Group transform={heart}>
        <Path path={HEART} style="stroke" strokeWidth={3.4} color="#ff1f1f" opacity={heartGlow}>
          <BlurMask blur={3.5} style="normal" />
        </Path>
        <Path path={HEART} color="#7a0c0c" />
        <Path path={HEART} style="stroke" strokeWidth={1.8} strokeJoin="round" color="#ff2222" />
      </Group>
    </Canvas>
  );
}

/** The number in a ring's badge: rides the head of the ring and counts up with it. */
function Badge({
  index,
  value,
  time,
  landed,
  ambient,
  full,
  initial,
}: Motion & { index: number; full: number; initial: number }) {
  const ring = RINGS[index];
  const [shown, setShown] = useState(() => Math.round(full * ringAt(initial, index)));
  useAnimatedReaction(
    () => Math.round(full * ringAt(value.get(), index)),
    (now, old) => {
      if (now !== old) scheduleOnRN(setShown, now);
    },
  );
  const style = useAnimatedStyle(() => {
    const drawn = ringAt(value.get(), index);
    const angle = (90 + ring.sweep * drawn) * DEG;
    return {
      opacity: clamp01(drawn * 8),
      transform: [
        { translateX: CX + ring.r * Math.cos(angle) - 12 },
        { translateY: CY + ring.r * Math.sin(angle) - 8 },
        { scale: pop(time.get() - landed[index].get(), ambient) },
      ],
    };
  });
  return (
    <Animated.Text style={[styles.badge, { color: ring.ink }, style]} numberOfLines={1}>
      {shown}
    </Animated.Text>
  );
}

/**
 * Black "today" widget: sleep and heart-rate rows on the left, three thick concentric rings on the
 * right, each ending in a badge with its number, around a white disc with the day's score. The
 * rings draw one after another with a bright head, their badges ride along counting up and pop
 * when they land, a sheen keeps running along the finished rings, and the heart beats at its rate.
 */
export function ActivityRings({
  progress,
  autoPlay = false,
  title = 'Today · 21 Feb',
  sleep = '7h 2m',
  sleepChange = '12%',
  sleepLabel = 'Sleep time',
  heartRate = '56 bpm',
  heartLabel = 'Avg. heart rate',
  bpm = 56,
  values = [31, 84, 53],
  score = 16,
  scoreLabel = 'PAI',
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const target = controlled ? Math.min(1, Math.max(0, progress)) : REST;

  // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
  const time = useSharedValue(0);
  const clock = useSharedValue(autoPlay && !controlled && !reducedMotion ? 0 : REST_T);
  const playing = useSharedValue(false);
  const looping = useSharedValue(false);
  const held = useSharedValue(target);
  const before = useSharedValue(target);
  const landed0 = useSharedValue(-10);
  const landed1 = useSharedValue(-10);
  const landed2 = useSharedValue(-10);

  useEffect(() => {
    const auto = autoPlay && !controlled && !reducedMotion;
    looping.set(auto);
    playing.set(auto);
    clock.set(auto ? 0 : REST_T);
  }, [autoPlay, controlled, reducedMotion, clock, looping, playing]);

  useEffect(() => {
    if (!controlled) return;
    held.set(withTiming(target, { duration: reducedMotion ? 200 : 1600, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
  }, [controlled, target, reducedMotion, held]);

  const value = useDerivedValue(() => (controlled ? held.get() : loopValue(clock.get())));
  // Plain copy of the starting value: children seed their text from it instead of reading a
  // shared value while rendering.
  const initialValue = controlled ? target : loopValue(autoPlay && !reducedMotion ? 0 : REST_T);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(time.get() + dt);
    const now = value.get();
    const was = before.get();
    before.set(now);
    // A ring reaching its full value sets off its badge's pop.
    if (ringAt(was, 0) < 0.995 && ringAt(now, 0) >= 0.995) landed0.set(time.get());
    if (ringAt(was, 1) < 0.995 && ringAt(now, 1) >= 0.995) landed1.set(time.get());
    if (ringAt(was, 2) < 0.995 && ringAt(now, 2) >= 0.995) landed2.set(time.get());
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

  // The score follows the rings: the average of how far they are drawn.
  const [scoreShown, setScoreShown] = useState(Math.round(score * target));
  useAnimatedReaction(
    () => {
      const p = value.get();
      return Math.round((score * (ringAt(p, 0) + ringAt(p, 1) + ringAt(p, 2))) / 3);
    },
    (now, old) => {
      if (now !== old) scheduleOnRN(setScoreShown, now);
    },
  );

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  const motion: Motion = { value, time, landed: [landed0, landed1, landed2], ambient: reducedMotion ? 0 : 1 };

  return (
    <Pressable
      onPress={restart}
      style={styles.card}
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={title}
      accessibilityValue={{
        min: 0,
        max: score,
        now: scoreShown,
        text: `${scoreShown} ${scoreLabel}, ${sleepLabel} ${sleep}, ${heartLabel} ${heartRate}`,
      }}
    >
      <View pointerEvents="none" style={styles.surface}>
        <Surface {...motion} bpm={bpm} />
      </View>

      <View pointerEvents="none" style={styles.content}>
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <View style={[styles.row, styles.sleepRow]}>
          <Text style={styles.figure} numberOfLines={1}>
            {sleep}
          </Text>
          <Text style={styles.change} numberOfLines={1}>
            {sleepChange}↑
          </Text>
        </View>
        <Text style={[styles.caption, styles.sleepCaption]} numberOfLines={1}>
          {sleepLabel}
        </Text>
        <View style={[styles.row, styles.heartRow]}>
          <Text style={styles.figure} numberOfLines={1}>
            {heartRate}
          </Text>
        </View>
        <Text style={[styles.caption, styles.heartCaption]} numberOfLines={1}>
          {heartLabel}
        </Text>

        {[0, 1, 2].map((index) => (
          <Badge key={index} index={index} full={values[index]} initial={initialValue} {...motion} />
        ))}
        <Text style={styles.score} numberOfLines={1}>
          {scoreShown}
        </Text>
        <Text style={styles.scoreLabel} numberOfLines={1}>
          {scoreLabel}
        </Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
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
  // Clips the badge numbers while their rings start below the card's edge.
  content: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    overflow: 'hidden',
  },
  title: {
    position: 'absolute',
    left: 18.8,
    top: 14,
    width: 220,
    color: '#ffffff',
    fontSize: 19.6,
    lineHeight: 24,
    fontWeight: '700',
    letterSpacing: -0.2,
  },
  row: {
    position: 'absolute',
    left: 48.7,
    height: 22,
    flexDirection: 'row',
  },
  sleepRow: {
    top: 53.5,
  },
  heartRow: {
    top: 97,
  },
  figure: {
    color: '#ffffff',
    fontSize: 16,
    lineHeight: 22,
    fontWeight: '500',
  },
  change: {
    marginLeft: 4,
    marginTop: -1.5,
    color: '#4cd964',
    fontSize: 10.6,
    lineHeight: 16,
    fontWeight: '500',
  },
  caption: {
    position: 'absolute',
    left: 48.7,
    width: 160,
    color: '#707070',
    fontSize: 11.8,
    lineHeight: 16,
    fontWeight: '500',
    letterSpacing: 0.2,
  },
  sleepCaption: {
    top: 73.6,
  },
  heartCaption: {
    top: 117.4,
  },
  badge: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 24,
    textAlign: 'center',
    fontSize: 9.6,
    lineHeight: 16,
    fontWeight: '800',
    fontVariant: ['tabular-nums'],
  },
  score: {
    position: 'absolute',
    left: CX - 30 + 0.6,
    top: 90.4,
    width: 60,
    textAlign: 'center',
    color: '#0a0a0a',
    fontSize: 22,
    lineHeight: 27,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  scoreLabel: {
    position: 'absolute',
    left: CX - 30 + 0.6,
    top: 116.8,
    width: 60,
    textAlign: 'center',
    color: '#0a0a0a',
    fontSize: 13.2,
    lineHeight: 18,
    fontWeight: '600',
  },
});
