import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Oval,
  Path,
  Points,
  RadialGradient,
  RoundedRect,
  rect,
  rrect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { memo, useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  cubicBezier,
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Share of the missions completed, 0…1. Leave undefined to show the reference's 4 of 7. */
  progress?: number;
  /** Plays by itself: the bar advances one mission at a time up to 4 of 7, holds, and starts over. */
  autoPlay?: boolean;
  /** Missions in the level. */
  total?: number;
  title?: string;
  /** Draws the reference's orange and blue backdrop glow around the card; the parent clips it. */
  backdrop?: boolean;
  onPress?: () => void;
  accessibilityLabel?: string;
};

const WIDTH = 330;
const HEIGHT = 139;
const RADIUS = 26;
// The canvas overflows the card by this much on every side to fit the backdrop glow.
const BLEED = 90;

const BADGE_X = 38.6;
const BADGE_Y = 40.5;
const BADGE_R = 24;
const BAR_X = 15;
const BAR_Y = 83.5;
const BAR_W = 298;
const BAR_H = 20;
const BAR_CY = BAR_Y + BAR_H / 2;
// The last three missions sit this far apart, counted back from the bar's right end.
const DOT_PITCH = 49;
const DOT_END = 16;
const DOT_R = 2.2;
const SPREAD = 3;
const GRID = 4;
const SPARKS = [0, 1, 2, 3, 4, 5];

// The reference rests at 4 of 7 missions.
const REST_DONE = 4;
const REST_TOTAL = 7;

// The autoPlay run is one clock in seconds: one hop per mission, a hold, a slide back.
const T_FIRST = 0.5;
const HOP_EVERY = 0.85;
const HOP_LENGTH = 0.6;
// The head lands (and rings) a little before its overshoot has settled.
const LAND_AFTER = 0.3;
const RING_LENGTH = 0.55;
const HOLD = 2.6;
const RESET = 0.5;
const TAIL = 0.4;
const HEAT_GAIN = 4;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const LIME = '#c4f84a';

const GRID_POINTS = (() => {
  const points = [];
  for (let gy = GRID / 2; gy < HEIGHT * 0.8; gy += GRID) {
    for (let gx = GRID / 2; gx < WIDTH; gx += GRID) points.push(vec(BLEED + gx, BLEED + gy));
  }
  return points;
})();

function seg(t: number, from: number, to: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - from) / (to - from)));
}
function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
function easeOutBack(x: number) {
  'worklet';
  const c = 1.7;
  return 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2);
}

/**
 * Where the head sits (from the bar's left end) with `missions` done. The last three missions are
 * spread out at the reference's spacing; the earlier ones share what is left of the bar.
 */
function headX(missions: number, total: number) {
  'worklet';
  const knee = Math.max(total - SPREAD, 0);
  const kneeX = BAR_W - DOT_END - Math.min(SPREAD, total) * DOT_PITCH;
  if (missions <= knee) return knee > 0 ? (kneeX * missions) / knee : 0;
  return BAR_W - DOT_END - (total - missions) * DOT_PITCH;
}

type Motion = {
  /** Missions done, a fraction while the head is between two. */
  missions: SharedValue<number>;
  /** 0…1 after the head lands on a mission: spreads a ring from it. */
  ring: SharedValue<number>;
  /** Free-running seconds for the breathing glow, the beacon and the sparks. */
  time: SharedValue<number>;
  /** 0…1, how fast the head is moving: stretches the comet tail and throws sparks. */
  heat: SharedValue<number>;
  total: number;
};

/** The card, its grain and the backdrop never change, so they get a canvas that is drawn once. */
const Glass = memo(function Glass({ backdrop }: { backdrop: boolean }) {
  const x = BLEED;
  const y = BLEED;
  const card = rrect(rect(x, y, WIDTH, HEIGHT), RADIUS, RADIUS);
  return (
    <Canvas style={styles.canvas}>
      {backdrop && (
        <Group>
          <Circle cx={x + WIDTH + 30} cy={y - 120} r={300}>
            <RadialGradient
              c={vec(x + WIDTH + 30, y - 120)}
              r={300}
              colors={['rgba(225,75,5,0.95)', 'rgba(150,45,0,0.55)', 'rgba(70,22,0,0.25)', 'rgba(40,12,0,0)']}
              positions={[0, 0.35, 0.7, 1]}
            />
          </Circle>
          <Circle cx={x + WIDTH / 2 - 30} cy={y + HEIGHT + 170} r={280}>
            <RadialGradient
              c={vec(x + WIDTH / 2 - 30, y + HEIGHT + 170)}
              r={280}
              colors={['rgba(24,34,170,0.95)', 'rgba(14,20,100,0.55)', 'rgba(6,8,40,0)']}
              positions={[0, 0.5, 1]}
            />
          </Circle>
        </Group>
      )}
      <RoundedRect rect={card}>
        <LinearGradient start={vec(x, y)} end={vec(x + WIDTH * 0.3, y + HEIGHT)} colors={['rgba(52,40,38,0.62)', 'rgba(10,10,18,0.55)']} />
      </RoundedRect>
      {/* The fine halftone grain of the glass, fading out towards the bottom. */}
      <Group clip={card}>
        <Points points={GRID_POINTS} mode="points" style="stroke" strokeWidth={1} strokeCap="round">
          <LinearGradient
            start={vec(0, y)}
            end={vec(0, y + HEIGHT * 0.8)}
            colors={['rgba(255,255,255,0.13)', 'rgba(255,255,255,0.07)', 'rgba(255,255,255,0)']}
          />
        </Points>
      </Group>
      <RoundedRect x={x + 0.5} y={y + 0.5} width={WIDTH - 1} height={HEIGHT - 1} r={RADIUS - 0.5} style="stroke" strokeWidth={1}>
        <LinearGradient
          start={vec(x, y)}
          end={vec(x, y + HEIGHT)}
          colors={['rgba(255,255,255,0.2)', 'rgba(255,255,255,0.07)', 'rgba(255,255,255,0.12)']}
        />
      </RoundedRect>
    </Canvas>
  );
});

/** A mission still to do: grey, or a lime beacon when it is the next one. */
function Dot({ index, missions, time, total }: Motion & { index: number }) {
  const cx = BLEED + BAR_X + headX(index, total);
  const cy = BLEED + BAR_CY;
  // How many missions ahead of the head this dot is.
  const ahead = useDerivedValue(() => index - missions.get());
  const shown = useDerivedValue(() => seg(ahead.get(), 0.2, 0.45));
  const next = useDerivedValue(() => shown.get() * (1 - seg(ahead.get(), 1.05, 1.3)));
  const glow = useDerivedValue(() => next.get() * (0.55 + 0.25 * Math.sin(time.get() * 3)));
  // The beacon sends out a faint ring every so often.
  const beat = useDerivedValue(() => (time.get() % 1.8) / 1.1);
  const beatR = useDerivedValue(() => DOT_R + 7 * Math.min(1, beat.get()));
  const beatOpacity = useDerivedValue(() => (beat.get() < 1 ? 0.6 * next.get() * (1 - beat.get()) : 0));
  return (
    <Group>
      <Circle cx={cx} cy={cy} r={DOT_R} color="#434343" opacity={shown} />
      <Circle cx={cx} cy={cy} r={6} color="rgba(170,255,60,0.9)" opacity={glow}>
        <BlurMask blur={4} style="normal" />
      </Circle>
      <Circle cx={cx} cy={cy} r={beatR} style="stroke" strokeWidth={1} color={LIME} opacity={beatOpacity} />
      <Circle cx={cx} cy={cy} r={DOT_R} color={LIME} opacity={next} />
    </Group>
  );
}

/** A spark shed backwards from the head while it moves. */
function Spark({ index, head, time, heat }: { index: number; head: SharedValue<number>; time: SharedValue<number>; heat: SharedValue<number> }) {
  const side = index % 2 ? 1 : -1;
  const reach = 0.5 + 0.5 * ((index * 0.37) % 1);
  const life = useDerivedValue(() => (time.get() * 1.7 + index / SPARKS.length) % 1);
  const cx = useDerivedValue(() => head.get() - 4 - (10 + 30 * reach) * life.get());
  const cy = useDerivedValue(() => BLEED + BAR_CY + side * (5 + 17 * reach * life.get()) + 6 * life.get() * life.get());
  const r = useDerivedValue(() => 1.4 * (1 - 0.6 * life.get()));
  const opacity = useDerivedValue(() => heat.get() * (1 - life.get()));
  return <Circle cx={cx} cy={cy} r={r} color="#eaff8a" opacity={opacity} />;
}

function Surface({ missions, ring, time, heat, total, still }: Motion & { still: boolean }) {
  const x = BLEED;
  const y = BLEED;
  const top = y + BAR_Y;
  const cy = y + BAR_CY;

  const head = useDerivedValue(() => x + BAR_X + Math.max(0, headX(missions.get(), total)));
  // Nothing is lit until the first mission is under way.
  const lit = useDerivedValue(() => seg(missions.get(), 0.02, 0.3));
  const fill = useDerivedValue(() => rrect(rect(x + BAR_X, top, head.get() + BAR_H / 2 - x - BAR_X, BAR_H), BAR_H / 2, BAR_H / 2));
  const fillEnd = useDerivedValue(() => vec(head.get() + BAR_H / 2, 0));
  const breath = useDerivedValue(() => (still ? 0.85 : 0.8 + 0.12 * Math.sin(time.get() * 1.9)) + 0.2 * heat.get());
  const haloR = useDerivedValue(() => 14 + 4 * heat.get());
  // The white-hot core stretches back into a tail while the head moves.
  const core = useDerivedValue(() => {
    const tail = 40 + 34 * heat.get();
    return rect(head.get() + 7 - tail, cy - 7.5, tail, 15);
  });
  const ringR = useDerivedValue(() => 9 + 20 * (1 - Math.pow(1 - ring.get(), 3)));
  const ringOpacity = useDerivedValue(() => (ring.get() > 0 && ring.get() < 1 ? 0.85 * (1 - ring.get()) : 0));
  const flash = useDerivedValue(() => (ring.get() > 0 ? 0.7 * (1 - seg(ring.get(), 0, 0.5)) : 0));

  const track = rrect(rect(x + BAR_X, top, BAR_W, BAR_H), BAR_H / 2, BAR_H / 2);
  const dots = [];
  for (let index = 1; index <= total; index++) dots.push(index);

  return (
    <Canvas style={styles.canvas}>
      {/* Mission badge: a red card in a ring lit orange from the left and red from the right. */}
      <Group transform={[{ translateX: x + BADGE_X }, { translateY: y + BADGE_Y }]}>
        <Group opacity={breath}>
          <Circle cx={0} cy={0} r={BADGE_R} style="stroke" strokeWidth={5}>
            <LinearGradient start={vec(-BADGE_R, BADGE_R * 0.6)} end={vec(BADGE_R, -BADGE_R * 0.6)} colors={['rgba(255,160,40,0.9)', 'rgba(230,30,50,0.9)']} />
            <BlurMask blur={6} style="normal" />
          </Circle>
        </Group>
        <Circle cx={0} cy={0} r={BADGE_R}>
          <RadialGradient c={vec(4, -4)} r={BADGE_R * 1.2} colors={['#2a0d12', '#0d0709']} />
        </Circle>
        <Circle cx={0} cy={0} r={BADGE_R - 0.75} style="stroke" strokeWidth={1.5}>
          <LinearGradient start={vec(-BADGE_R, BADGE_R * 0.6)} end={vec(BADGE_R, -BADGE_R * 0.6)} colors={['#ffb04a', '#f0553a', '#d8243c']} />
        </Circle>
        <Group transform={[{ rotate: 0.2 }]}>
          <RoundedRect x={-10.5} y={-14} width={21} height={28} r={3.5}>
            <LinearGradient start={vec(-10, -14)} end={vec(10, 14)} colors={['#ff8a5c', '#e83038', '#a5121f']} />
          </RoundedRect>
          <RoundedRect x={-10} y={-13.5} width={20} height={27} r={3} style="stroke" strokeWidth={0.8} color="rgba(255,200,180,0.55)" />
          <Circle cx={-1.5} cy={3} r={4.6} style="stroke" strokeWidth={1.8} color="rgba(255,150,140,0.75)" />
          <Circle cx={-1.5} cy={3} r={1.4} color="rgba(120,10,20,0.7)" />
          <Path path="M-7 -7.5l1.6 -3.6M-4.4 -7.5l1.6 -3.6M-1.8 -7.5l1.6 -3.6" style="stroke" strokeWidth={1.1} color="rgba(255,255,255,0.85)" />
          <Path path="M6.6 -4v0.01M6.6 -1.4v0.01M6.6 1.2v0.01M6.6 3.8v0.01" style="stroke" strokeWidth={1.3} strokeCap="round" color="rgba(255,255,255,0.85)" />
        </Group>
      </Group>

      <RoundedRect rect={track} color="rgba(22,22,22,0.9)" />
      {dots.map((index) => (
        <Dot key={index} index={index} missions={missions} ring={ring} time={time} heat={heat} total={total} />
      ))}

      <Group opacity={lit}>
        {/* A wide lime haze and a tight halo around the head. */}
        <Group opacity={breath}>
          <Circle cx={head} cy={cy} r={34} color="rgba(160,235,30,0.32)">
            <BlurMask blur={18} style="normal" />
          </Circle>
          <Circle cx={head} cy={cy} r={haloR} color="rgba(215,255,60,1)">
            <BlurMask blur={7} style="normal" />
          </Circle>
        </Group>
        <Circle cx={head} cy={cy} r={ringR} style="stroke" strokeWidth={1.6} color={LIME} opacity={ringOpacity} />

        <RoundedRect rect={fill}>
          <LinearGradient
            start={vec(x + BAR_X, 0)}
            end={fillEnd}
            colors={['#d5e255', '#b3d038', '#80b520', '#9ccb28']}
            positions={[0, 0.28, 0.66, 1]}
          />
        </RoundedRect>
        <Group clip={fill}>
          {/* Lit from above like a glass tube. */}
          <RoundedRect x={x + BAR_X + 4} y={top + 2} width={BAR_W} height={5} r={2.5} color="rgba(255,255,255,0.22)">
            <BlurMask blur={2.5} style="normal" />
          </RoundedRect>
          <RoundedRect x={x + BAR_X} y={top + BAR_H - 4} width={BAR_W} height={6} r={3} color="rgba(40,70,0,0.3)">
            <BlurMask blur={3} style="normal" />
          </RoundedRect>
          <Oval rect={core} color="rgba(255,255,185,1)">
            <BlurMask blur={6} style="normal" />
          </Oval>
          <Circle cx={head} cy={cy} r={7.5} color="#fffff0">
            <BlurMask blur={4} style="normal" />
          </Circle>
          <RoundedRect rect={fill} color="#ffffff" opacity={flash} />
        </Group>
        {!still && SPARKS.map((index) => <Spark key={index} index={index} head={head} time={time} heat={heat} />)}
      </Group>
    </Canvas>
  );
}

/**
 * Dark glass "next level" card: a mission badge, a line of encouragement, and a lime bar whose
 * white-hot head rides from mission to mission, with dots for the missions still to do and
 * captions that count them. The head trails a comet tail and sparks while it moves and rings
 * when it lands.
 */
export function MissionGlowProgress({
  progress,
  autoPlay = false,
  total = REST_TOTAL,
  title = 'Good job!',
  backdrop = true,
  onPress,
  accessibilityLabel = 'Missions',
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress !== undefined;
  const playing = autoPlay && !controlled && !reducedMotion;
  const rest = Math.min(REST_DONE, total);
  const [pressed, setPressed] = useState(false);
  const [run, setRun] = useState(0);
  const initial = controlled ? Math.min(1, Math.max(0, progress)) * total : rest;
  const [done, setDone] = useState(Math.round(initial));

  const target = useSharedValue(initial);
  const landed = useSharedValue(0);
  const t = useSharedValue(0);
  const time = useSharedValue(0);
  const heat = useSharedValue(0);
  const last = useSharedValue(initial);

  const hops = T_FIRST + (rest - 1) * HOP_EVERY + HOP_LENGTH;
  const loop = hops + HOLD + RESET + TAIL;

  useEffect(() => {
    if (progress === undefined) return;
    const next = Math.min(1, Math.max(0, progress)) * total;
    const forwards = next > target.get();
    // Reanimated drops animations under reduced motion; value changes keep a short tween.
    target.set(withTiming(next, { duration: reducedMotion ? 200 : 700, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
    if (forwards && !reducedMotion) {
      landed.set(withSequence(withTiming(0, { duration: 0 }), withDelay(LAND_AFTER * 1000, withTiming(1, { duration: RING_LENGTH * 1000, easing: Easing.linear }))));
    }
  }, [progress, total, reducedMotion, target, landed]);

  useEffect(() => {
    if (!playing) return;
    t.set(0);
    t.set(withRepeat(withTiming(loop, { duration: loop * 1000, easing: Easing.linear }), -1));
    return () => cancelAnimation(t);
  }, [playing, run, loop, t]);

  const missions = useDerivedValue(() => {
    if (controlled) return target.get();
    if (!playing) return rest;
    let m = 0;
    for (let k = 0; k < rest; k++) m += easeOutBack(seg(t.get(), T_FIRST + k * HOP_EVERY, T_FIRST + k * HOP_EVERY + HOP_LENGTH));
    return m * (1 - easeInOut(seg(t.get(), hops + HOLD, hops + HOLD + RESET)));
  });
  const ring = useDerivedValue(() => {
    if (controlled) return landed.get();
    if (!playing) return 0;
    let value = 0;
    for (let k = 0; k < rest; k++) {
      const a = seg(t.get(), T_FIRST + k * HOP_EVERY + LAND_AFTER, T_FIRST + k * HOP_EVERY + LAND_AFTER + RING_LENGTH);
      if (a > 0 && a < 1) value = a;
    }
    return value;
  });

  useFrameCallback((frame) => {
    time.set(frame.timeSinceFirstFrame / 1000);
    // Speed of the head in shares of the bar per second, eased so the tail grows and fades.
    const dt = Math.max(1, frame.timeSincePreviousFrame ?? 16) / 1000;
    const speed = Math.abs(missions.get() - last.get()) / total / dt;
    last.set(missions.get());
    heat.set(heat.get() + (Math.min(1, speed * HEAT_GAIN) - heat.get()) * 0.18);
  }, !reducedMotion);

  // The captions only change when a whole mission is reached.
  useAnimatedReaction(
    () => Math.min(total, Math.max(0, Math.round(missions.get()))),
    (now, before) => {
      if (now !== before) scheduleOnRN(setDone, now);
    },
  );

  const left = total - done;
  const plural = (n: number) => (n === 1 ? 'Mission' : 'Missions');

  return (
    <Pressable
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={accessibilityLabel}
      accessibilityValue={{ min: 0, max: total, now: done, text: `${done} of ${total} missions completed` }}
      onPress={() => {
        onPress?.();
        // A tap restarts the demo run.
        setRun((n) => n + 1);
      }}
      onPressIn={() => {
        setPressed(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => setPressed(false)}
    >
      <Animated.View
        style={[
          styles.card,
          {
            transform: [{ scale: pressed ? 0.97 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: CSS_EASE_OUT,
          },
        ]}
      >
        <View pointerEvents="none" style={styles.surface}>
          <Glass backdrop={backdrop} />
        </View>
        <View pointerEvents="none" style={styles.surface}>
          <Surface missions={missions} ring={ring} time={time} heat={heat} total={total} still={reducedMotion} />
        </View>

        <Text style={styles.message}>
          <Text style={styles.title}>{title} </Text>
          {left > 0
            ? `Just complete ${left} more ${plural(left).toLowerCase()} to advance to the next level.`
            : 'Every mission is complete. On to the next level.'}
        </Text>
        <Text style={[styles.caption, styles.completed]} numberOfLines={1}>
          {done} {plural(done)} Completed
        </Text>
        <Text style={[styles.caption, styles.remaining]} numberOfLines={1}>
          {left} {plural(left)} Remaining
        </Text>
      </Animated.View>
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
  message: {
    position: 'absolute',
    left: 75,
    top: 22.5,
    width: 240,
    color: '#dbd9da',
    fontSize: 13,
    lineHeight: 18,
    letterSpacing: 0.1,
  },
  title: {
    fontWeight: '700',
  },
  caption: {
    position: 'absolute',
    top: 109.5,
    color: '#51565a',
    fontSize: 11.5,
    lineHeight: 16,
    letterSpacing: 0.1,
    fontVariant: ['tabular-nums'],
  },
  completed: {
    left: 14,
  },
  remaining: {
    right: 17,
  },
});
