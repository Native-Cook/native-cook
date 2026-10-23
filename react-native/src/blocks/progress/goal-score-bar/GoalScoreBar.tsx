import { BlurMask, Canvas, Circle, Group, LinearGradient, Path, RoundedRect, vec } from '@shopify/react-native-skia';
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

type Friend = {
  initials: string;
  color: string;
  ink: string;
  /** Share of their own goal reached, 0…1: the ring around the avatar. */
  share: number;
};

type Props = {
  /** Controlled share of the goal reached, 0…1. */
  progress?: number;
  /** Counts the score up to `score`, climbing the ranking on the way, holds, and starts over. */
  autoPlay?: boolean;
  /** Score shown at the resting frame. */
  score?: number;
  goal?: number;
  /** Scores of the other people in the ranking: passing one moves the trophy up a place. */
  rivals?: number[];
  friends?: [Friend, Friend, Friend];
  buttonLabel?: string;
  onAllFriends?: () => void;
};

const WIDTH = 340;
// The compact pill, then the expanded panel under it.
const PILL_W = 200.5;
const PILL_H = 36;
const PILL_X = (WIDTH - PILL_W) / 2;
const PANEL_Y = 59.3;
const PANEL_H = 146.3;
const PANEL_R = 43;
const HEIGHT = PANEL_Y + PANEL_H;
// The canvas overflows the component by this much on every side to fit the shadow and the device frame.
const BLEED = 44;

const BAR_X = 21.5;
const BAR_W = 296.2;
const BAR_Y = PANEL_Y + 64;
const BAR_H = 3.4;
const AVATAR_X = [43, 98.5, 153.5];
const AVATAR_Y = PANEL_Y + 102.2;
const AVATAR_R = 17;
const RING_R = 20.3;
const RING_W = 3.2;
const TROPHY_X = 278.6;
const TROPHY_Y = PANEL_Y + 41.6;
const PILL_TROPHY_X = PILL_X + 173;

// The rolling number: four columns, each a strip of digits behind a window one digit tall.
const COL_W = 21.9;
const DIGIT_H = 44;
const STRIP = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];
// A column never rolls faster than this (digits per second), so fast counting reads as a blur of rolling
// digits instead of flicker; it eases into its digit over the last turn.
const ROLL_MAX = 22;
const ROLL_MIN = 2.5;

const ORANGE = '#f2a23e';

// autoPlay loop (seconds).
const START = 0.5;
const COUNT = 2.8;
const HOLD = 4.2;
const RESET = 0.7;
const LOOP = START + COUNT + HOLD + RESET;
// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
const REST_T = START + COUNT + 1.8;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

// Trophy, drawn around its centre in a 12.4 × 12.6 box.
const CUP = 'M-3.7 -6.3L3.7 -6.3L3.7 -2.4A3.7 3.7 0 0 1 -3.7 -2.4Z';
const HANDLES = 'M-3.9 -5L-5.6 -5A2.5 2.5 0 0 0 -3.5 -1.7M3.9 -5L5.6 -5A2.5 2.5 0 0 1 3.5 -1.7';
const FOOT = 'M-0.8 1L0.8 1L0.8 4.3L3 4.3L3 6.2L-3 6.2L-3 4.3L-0.8 4.3Z';

/** The ring around an avatar: a full turn starting at 12 o'clock. */
function ringPath(cx: number, cy: number) {
  return `M${cx} ${cy - RING_R}A${RING_R} ${RING_R} 0 1 1 ${cx - 0.01} ${cy - RING_R}`;
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

/** Share of the goal reached at `t` seconds into the autoPlay loop, where `rest` is the reference share. */
function loopValue(t: number, rest: number) {
  'worklet';
  if (t < START) return 0;
  if (t < START + COUNT) return rest * easeOutCubic((t - START) / COUNT);
  if (t < START + COUNT + HOLD) return rest;
  return rest * (1 - easeInOutCubic((t - START - COUNT - HOLD) / RESET));
}

/** How far friend `index`'s ring is drawn: they sweep in one after another early in the count. */
function friendAt(share: number, rest: number, index: number) {
  'worklet';
  return easeOutCubic(clamp01((share / Math.max(rest, 0.001)) * 4 - 0.5 * index));
}

function rankOf(points: number, rivals: number[]) {
  'worklet';
  let rank = 1;
  for (const rival of rivals) if (rival > points) rank++;
  return rank;
}

function ordinal(rank: number) {
  const tail = rank % 100;
  if (tail >= 11 && tail <= 13) return `${rank}th`;
  return `${rank}${['th', 'st', 'nd', 'rd'][rank % 10] ?? 'th'}`;
}

function spaced(n: number) {
  return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
}

/** Scale of the trophy after the rank changes: a quick overshoot. */
function pop(since: number) {
  'worklet';
  const u = clamp01(since / 0.55);
  return 1 + 0.45 * Math.sin(Math.PI * u) * (1 - u);
}

type Motion = {
  /** Share of the goal reached. */
  value: SharedValue<number>;
  time: SharedValue<number>;
  /** When the rank last changed, on the `time` axis. */
  rankedAt: SharedValue<number>;
  /** 0 with reduced motion: no shimmer, glow pulse or pops. */
  ambient: number;
  rest: number;
};

function Trophy({ x, y, size, time, rankedAt, ambient }: { x: number; y: number; size: number } & Pick<Motion, 'time' | 'rankedAt' | 'ambient'>) {
  // Climbing a place makes the trophy jump and tip.
  const transform = useDerivedValue(() => {
    const since = time.get() - rankedAt.get();
    const u = clamp01(since / 0.55);
    return [
      { translateX: x },
      { translateY: y - ambient * 3 * Math.sin(Math.PI * u) * (1 - u) },
      { rotate: ambient * 0.35 * Math.sin(u * Math.PI * 3) * (1 - u) },
      { scale: size * (ambient > 0 ? pop(since) : 1) },
    ];
  });
  const flash = useDerivedValue(() => {
    const u = clamp01((time.get() - rankedAt.get()) / 0.7);
    return ambient * 0.8 * (1 - u) * clamp01(u * 8);
  });
  return (
    <Group transform={transform}>
      <Circle cx={0} cy={0} r={9} color={ORANGE} opacity={flash}>
        <BlurMask blur={6} style="normal" />
      </Circle>
      <Path path={CUP} color={ORANGE} />
      <Path path={HANDLES} style="stroke" strokeWidth={1.15} strokeCap="round" color={ORANGE} />
      <Path path={FOOT} color={ORANGE} />
    </Group>
  );
}

function FriendRing({ index, friend, value, rest }: { index: number; friend: Friend } & Pick<Motion, 'value' | 'rest'>) {
  const cx = BLEED + AVATAR_X[index];
  const cy = BLEED + AVATAR_Y;
  const drawn = useDerivedValue(() => friendAt(value.get(), rest, index));
  const end = useDerivedValue(() => friend.share * drawn.get());
  const avatarR = useDerivedValue(() => AVATAR_R * (0.72 + 0.28 * drawn.get()));
  const avatarOpacity = useDerivedValue(() => clamp01(drawn.get() * 3));
  return (
    <>
      <Circle cx={cx} cy={cy} r={RING_R} style="stroke" strokeWidth={RING_W} color="#2a2a2c" />
      <Path path={ringPath(cx, cy)} style="stroke" strokeWidth={RING_W} strokeCap="round" color={ORANGE} end={end} />
      <Circle cx={cx} cy={cy} r={avatarR} color={friend.color} opacity={avatarOpacity} />
    </>
  );
}

function Surface({ friends, speed, ...motion }: Motion & { friends: [Friend, Friend, Friend]; speed: SharedValue<number> }) {
  const { value, time, ambient } = motion;
  const x = BLEED;
  const y = BLEED;

  const fillW = useDerivedValue(() => Math.max(BAR_H, BAR_W * clamp01(value.get())));
  const fillOpacity = useDerivedValue(() => clamp01(value.get() * 60));
  const headX = useDerivedValue(() => x + BAR_X + fillW.get() - BAR_H / 2);
  // The head glows while the bar is moving and keeps a faint pulse afterwards.
  const headOpacity = useDerivedValue(
    () => fillOpacity.get() * (0.35 + ambient * (0.12 * Math.sin(time.get() * 3) + 0.5 * clamp01(Math.abs(speed.get()) * 2.5))),
  );
  // A highlight runs along the filled part every few seconds.
  const shimmer = useDerivedValue(() => (time.get() % 3.2) / 1.2);
  const shimmerX = useDerivedValue(() => x + BAR_X - 30 + (fillW.get() + 30) * clamp01(shimmer.get()));
  const shimmerOpacity = useDerivedValue(() => {
    const s = shimmer.get();
    return s >= 1 ? 0 : ambient * 0.85 * Math.sin(Math.PI * s);
  });
  const fillClip = useDerivedValue(() => ({
    rect: { x: x + BAR_X, y: y + BAR_Y - BAR_H / 2, width: fillW.get(), height: BAR_H },
    rx: BAR_H / 2,
    ry: BAR_H / 2,
  }));

  return (
    <Canvas style={styles.canvas}>
      {/* The body of the device the panel sits on, fading out downwards. */}
      <RoundedRect x={x - 9} y={y + PANEL_Y - 9} width={WIDTH + 18} height={PANEL_H + 120} r={PANEL_R + 9}>
        <LinearGradient
          start={vec(0, y + PANEL_Y)}
          end={vec(0, y + HEIGHT + 40)}
          colors={['rgba(246,246,246,1)', 'rgba(246,246,246,1)', 'rgba(246,246,246,0)']}
          positions={[0, 0.72, 1]}
        />
      </RoundedRect>
      <RoundedRect x={x + 10} y={y + PANEL_Y + 16} width={WIDTH - 20} height={PANEL_H - 6} r={PANEL_R} color="rgba(0,0,0,0.3)">
        <BlurMask blur={14} style="normal" />
      </RoundedRect>
      <RoundedRect x={x + PILL_X} y={y} width={PILL_W} height={PILL_H} r={PILL_H / 2} color="#000000" />
      <RoundedRect x={x} y={y + PANEL_Y} width={WIDTH} height={PANEL_H} r={PANEL_R} color="#000000" />

      <RoundedRect x={x + BAR_X} y={y + BAR_Y - BAR_H / 2} width={BAR_W} height={BAR_H} r={BAR_H / 2} color="#29292b" />
      <Circle cx={headX} cy={y + BAR_Y} r={5} color={ORANGE} opacity={headOpacity}>
        <BlurMask blur={5} style="normal" />
      </Circle>
      <RoundedRect x={x + BAR_X} y={y + BAR_Y - BAR_H / 2} width={fillW} height={BAR_H} r={BAR_H / 2} color={ORANGE} opacity={fillOpacity} />
      <Group clip={fillClip}>
        <RoundedRect x={shimmerX} y={y + BAR_Y - 3} width={22} height={6} r={3} color="#fff4d6" opacity={shimmerOpacity}>
          <BlurMask blur={3} style="normal" />
        </RoundedRect>
      </Group>

      {[0, 1, 2].map((index) => (
        <FriendRing key={index} index={index} friend={friends[index]} value={value} rest={motion.rest} />
      ))}
      <Trophy x={x + TROPHY_X} y={y + TROPHY_Y} size={1.16} {...motion} />
      <Trophy x={x + PILL_TROPHY_X} y={y + PILL_H / 2} size={0.96} {...motion} />
    </Canvas>
  );
}

/** One column of the rolling number. */
function Column({ pos, shown }: { pos: SharedValue<number>; shown: SharedValue<number> }) {
  const box = useAnimatedStyle(() => ({ width: COL_W * shown.get(), opacity: shown.get() }));
  const strip = useAnimatedStyle(() => ({ transform: [{ translateY: -pos.get() * DIGIT_H }] }));
  return (
    <Animated.View style={[styles.column, box]}>
      <Animated.View style={[styles.strip, strip]}>
        {STRIP.map((digit, index) => (
          <Text key={index} style={styles.digit}>
            {digit}
          </Text>
        ))}
      </Animated.View>
    </Animated.View>
  );
}

/** The score in the compact pill: a plain count. */
function PillScore({ value, goal, initial }: { value: SharedValue<number>; goal: number; initial: number }) {
  const [shown, setShown] = useState(initial);
  useAnimatedReaction(
    () => Math.round(value.get() * goal),
    (now, old) => {
      if (now !== old) scheduleOnRN(setShown, now);
    },
  );
  return (
    <Text style={styles.pillScore} numberOfLines={1}>
      {shown}
    </Text>
  );
}

function Initials({ index, friend, value, rest }: { index: number; friend: Friend } & Pick<Motion, 'value' | 'rest'>) {
  const style = useAnimatedStyle(() => {
    const drawn = friendAt(value.get(), rest, index);
    return { opacity: clamp01(drawn * 3 - 1), transform: [{ scale: 0.72 + 0.28 * drawn }] };
  });
  return (
    <Animated.Text style={[styles.initials, { left: AVATAR_X[index] - 20, color: friend.ink }, style]} numberOfLines={1}>
      {friend.initials}
    </Animated.Text>
  );
}

/**
 * Leaderboard live activity: a compact pill with the score and place, and the expanded panel with
 * the score against the goal, a thin progress line, three friends and a button. The score rolls up
 * like an odometer, the line fills behind a glowing head with a highlight running along it, the
 * trophy jumps each time a rival is passed, and the friends' rings sweep in one after another.
 */
export function GoalScoreBar({
  progress,
  autoPlay = false,
  score = 8814,
  goal = 10000,
  rivals = [9480, 8120, 6730, 5210],
  friends = [
    { initials: 'MK', color: '#33363d', ink: '#ffffff', share: 0.75 },
    { initials: 'JL', color: '#f6b90f', ink: '#2a1c00', share: 0.66 },
    { initials: 'TS', color: '#90a78f', ink: '#16240f', share: 0.42 },
  ],
  buttonLabel = 'All friends',
  onAllFriends,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const rest = score / goal;
  const target = controlled ? Math.min(1, Math.max(0, progress)) : rest;
  const auto = autoPlay && !controlled && !reducedMotion;

  // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
  const time = useSharedValue(0);
  const clock = useSharedValue(auto ? 0 : REST_T);
  const playing = useSharedValue(false);
  const looping = useSharedValue(false);
  const held = useSharedValue(target);
  const before = useSharedValue(auto ? 0 : target);
  // Change of the share per second: lights the head of the line.
  const speed = useSharedValue(0);
  const rankedAt = useSharedValue(-10);

  const startPoints = Math.round((auto ? 0 : target) * goal);
  // Where each column of the rolling number is (0…10), and whether it is shown (leading zeros fold away).
  const pos0 = useSharedValue(startPoints % 10);
  const pos1 = useSharedValue(Math.floor(startPoints / 10) % 10);
  const pos2 = useSharedValue(Math.floor(startPoints / 100) % 10);
  const pos3 = useSharedValue(Math.floor(startPoints / 1000) % 10);
  const shown1 = useSharedValue(startPoints >= 10 ? 1 : 0);
  const shown2 = useSharedValue(startPoints >= 100 ? 1 : 0);
  const shown3 = useSharedValue(startPoints >= 1000 ? 1 : 0);
  const one = useSharedValue(1);

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
    speed.set((share - was) / dt);
    const points = Math.round(share * goal);
    // Passing a rival moves the trophy up a place.
    if (rankOf(points, rivals) < rankOf(Math.round(was * goal), rivals)) rankedAt.set(now);

    // Each column rolls towards its digit in the direction the score is moving.
    const dir = share >= was ? 1 : -1;
    const columns = [pos0, pos1, pos2, pos3];
    for (let k = 0; k < 4; k++) {
      const digit = Math.floor(points / Math.pow(10, k)) % 10;
      const at = columns[k].get();
      let dist = (((digit - at) * dir) % 10 + 10) % 10;
      if (dist > 9.99) dist = 0;
      const step = Math.max(ROLL_MIN, Math.min(dist * 10, ROLL_MAX)) * dt;
      columns[k].set(step >= dist ? digit : (at + dir * step + 10) % 10);
    }
    const fold = Math.min(1, dt * 14);
    shown1.set(shown1.get() + ((points >= 10 ? 1 : 0) - shown1.get()) * fold);
    shown2.set(shown2.get() + ((points >= 100 ? 1 : 0) - shown2.get()) * fold);
    shown3.set(shown3.get() + ((points >= 1000 ? 1 : 0) - shown3.get()) * fold);
  }, !reducedMotion);

  // Reduced motion: nothing rolls, the columns simply show the digits.
  useAnimatedReaction(
    () => (reducedMotion ? Math.round(value.get() * goal) : -1),
    (points) => {
      if (points < 0) return;
      pos0.set(points % 10);
      pos1.set(Math.floor(points / 10) % 10);
      pos2.set(Math.floor(points / 100) % 10);
      pos3.set(Math.floor(points / 1000) % 10);
      shown1.set(points >= 10 ? 1 : 0);
      shown2.set(points >= 100 ? 1 : 0);
      shown3.set(points >= 1000 ? 1 : 0);
    },
  );

  const [rank, setRank] = useState(rankOf(startPoints, rivals));
  useAnimatedReaction(
    () => rankOf(Math.round(value.get() * goal), rivals),
    (now, old) => {
      if (now !== old) scheduleOnRN(setRank, now);
    },
  );

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  const motion: Motion = { value, time, rankedAt, ambient: reducedMotion ? 0 : 1, rest };
  const points = Math.round(target * goal);

  return (
    <Pressable onPress={restart} style={styles.root} accessible={false}>
      <View pointerEvents="none" style={styles.surface}>
        <Surface friends={friends} speed={speed} {...motion} />
      </View>

      <View
        pointerEvents="none"
        style={StyleSheet.absoluteFill}
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel="Score"
        accessibilityValue={{ min: 0, max: goal, now: points, text: `${points} of ${goal}, ${ordinal(rank)} place` }}
      >
        <PillScore value={value} goal={goal} initial={startPoints} />
        <Text style={styles.pillRank} numberOfLines={1}>
          {rank}
        </Text>

        <View style={styles.number}>
          <Column pos={pos3} shown={shown3} />
          <Column pos={pos2} shown={shown2} />
          <Column pos={pos1} shown={shown1} />
          <Column pos={pos0} shown={one} />
          <Text style={styles.goal} numberOfLines={1}>
            / {spaced(goal)}
          </Text>
        </View>
        <Text style={styles.rank} numberOfLines={1}>
          {ordinal(rank)}
        </Text>

        {[0, 1, 2].map((index) => (
          <Initials key={index} index={index} friend={friends[index]} value={value} rest={rest} />
        ))}
      </View>

      <Pressable
        style={styles.buttonSlot}
        accessibilityRole="button"
        accessibilityLabel={buttonLabel}
        onPress={onAllFriends}
        onPressIn={() => {
          if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }}
      >
        {({ pressed }) => (
          <Animated.View
            style={[
              styles.button,
              {
                transform: [{ scale: pressed ? 0.97 : 1 }],
                transitionProperty: 'transform',
                transitionDuration: 130,
                transitionTimingFunction: CSS_EASE_OUT,
              },
            ]}
          >
            <Text style={styles.buttonLabel} numberOfLines={1}>
              {buttonLabel}
            </Text>
          </Animated.View>
        )}
      </Pressable>
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
  pillScore: {
    position: 'absolute',
    left: PILL_X + 9,
    top: 9,
    width: 80,
    color: '#ffffff',
    fontSize: 13.6,
    lineHeight: 18,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  pillRank: {
    position: 'absolute',
    left: PILL_X + PILL_W - 8.7 - 30,
    top: 9,
    width: 30,
    textAlign: 'right',
    color: ORANGE,
    fontSize: 13.6,
    lineHeight: 18,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  number: {
    position: 'absolute',
    left: 22.2,
    top: PANEL_Y + 12.7,
    height: DIGIT_H,
    flexDirection: 'row',
  },
  column: {
    height: DIGIT_H,
    overflow: 'hidden',
  },
  strip: {
    width: COL_W,
  },
  digit: {
    width: COL_W,
    height: DIGIT_H,
    textAlign: 'center',
    color: '#ffffff',
    fontSize: 37,
    lineHeight: DIGIT_H,
    fontWeight: '700',
    letterSpacing: -0.5,
    fontVariant: ['tabular-nums'],
  },
  goal: {
    marginLeft: 8,
    marginTop: 19.5,
    color: '#6c6c6e',
    fontSize: 16,
    lineHeight: 20,
    fontWeight: '500',
  },
  rank: {
    position: 'absolute',
    left: WIDTH - 22.8 - 40,
    top: PANEL_Y + 32.2,
    width: 40,
    textAlign: 'right',
    color: ORANGE,
    fontSize: 16,
    lineHeight: 20,
    fontWeight: '500',
  },
  initials: {
    position: 'absolute',
    top: AVATAR_Y - 8,
    width: 40,
    textAlign: 'center',
    fontSize: 11.5,
    lineHeight: 16,
    fontWeight: '700',
    letterSpacing: 0.2,
  },
  buttonSlot: {
    position: 'absolute',
    left: 190,
    top: PANEL_Y + 80.7,
  },
  button: {
    width: 127.7,
    height: 43.5,
    borderRadius: 21.75,
    backgroundColor: '#242426',
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonLabel: {
    color: '#ffffff',
    fontSize: 16,
    lineHeight: 21,
    fontWeight: '500',
  },
});
