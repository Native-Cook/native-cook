import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  Skia,
  rect,
  rrect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, TextInput, type TextInputProps, type TextStyle, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  cubicBezier,
  useAnimatedProps,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  /** Share of the step goal reached, 0…1. Leave undefined to show the reference's 6825 steps. */
  progress?: number;
  /** Plays by itself: the week's checks pop in, the steps count up, hold, and start over. */
  autoPlay?: boolean;
  streakDays?: number;
  goal?: number;
  /** How many days of the week are already ticked; the next one is today and shows a ring. */
  completedDays?: number;
  onPress?: () => void;
  accessibilityLabel?: string;
};

const SIZE = 312;
const RADIUS = 62;
// The canvas overflows the widget by this much on every side to fit its shadow.
const BLEED = 40;

const FLAME_X = 54;
const FLAME_Y = 56;
const FOOT_X = 265.5;
const FOOT_Y = 55.5;
const DIVIDER_Y = 100.5;
const INSET = 30;
const DAY_X = 45.8;
const DAY_GAP = 38.9;
const DAY_Y = 136.5;
const DAY_R = 16.3;
const RING_R = 15.1;
const RING_STROKE = 2.4;
const BAR_Y = 272;
const BAR_W = SIZE - INSET * 2;
const BAR_H = 11.5;
// Width of the highlight that travels along the filled bar.
const SHIMMER_W = 70;
const SHIMMER_PERIOD = 3.2;
const EMBERS = 4;
const EMBER_IDS = [0, 1, 2, 3];
const EMBER_SPEED = 0.55;
const EMBER_RISE = 26;

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
// The value the reference shows: 6825 of 10,000 steps.
const REST = 0.6825;

// The autoPlay run is one clock in seconds; every part below is a window on it.
const T_CHECKS = 0.3;
const CHECK_STAGGER = 0.2;
const CHECK_LENGTH = 0.45;
const T_FILL = [0.9, 3.1];
const T_RESET = [5.7, 6.2];
const TOTAL = 6.6;
const HEAT_GAIN = 2.2;

const GREEN = '#47bd29';
const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

const FLAME =
  'M3 -22C12 -16 19 -6 19 5C19 15 11 22 0 22C-11 22 -19 15 -19 5C-19 -1 -17 -6 -13 -10C-11 -6 -9 -3 -6 -3C-2 -3 -1 -8 -2 -13C-2.5 -17 0 -21 3 -22Z';
const FLAME_CORE =
  'M-1.5 2C0 5 1.5 7 3 7.5C4 7.8 5 7 5.5 6C7 8.5 7.5 11 7.5 13.5C7.5 18 4 21 0 21C-4 21 -7.5 18 -7.5 13.5C-7.5 9 -4 6 -1.5 2Z';
const FOOT =
  'M-6.2 -1.5C-6.2 -5.5 -3 -7.3 0.5 -7.3C5 -7.3 7.8 -3.5 7.8 1C7.8 6 5.5 12 1.2 12C-1.5 12 -3 10 -2.6 7.5C-2.3 5.5 -1.6 4.6 -3.2 3.2C-5 1.6 -6.2 0.5 -6.2 -1.5Z';
const TOES = [
  [-9.5, -9.1, 2.75],
  [-3.3, -11.8, 2.1],
  [3.5, -11.8, 2.1],
  [9, -9, 1.4],
];
const CHECK = 'M-7.5 0L-2.5 5L7.5 -5.5';
// A full turn starting at 12 o'clock, trimmed to today's share of the goal.
const RING = Skia.Path.Make().addArc(Skia.XYWHRect(-RING_R, -RING_R, RING_R * 2, RING_R * 2), -90, 359.9);

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

type Motion = {
  /** Share of the goal on show, 0…1. */
  p: SharedValue<number>;
  /** Seconds into the autoPlay run. */
  t: SharedValue<number>;
  /** Free-running seconds for the flame and the shimmer. */
  time: SharedValue<number>;
  /** 0…1, how fast the value is moving right now: lights the ring's leading cap. */
  heat: SharedValue<number>;
  playing: boolean;
};

/** A spark that leaves the flame's tip, drifts up and burns out. */
function Ember({ index, time }: { index: number; time: SharedValue<number> }) {
  const life = useDerivedValue(() => (time.get() * EMBER_SPEED + index / EMBERS) % 1);
  const cx = useDerivedValue(
    () => BLEED + FLAME_X + 2 + (index % 2 ? -1 : 1) * (3 + index * 1.7) * life.get() + 2.5 * Math.sin(time.get() * 3 + index * 2),
  );
  const cy = useDerivedValue(() => BLEED + FLAME_Y - 16 - EMBER_RISE * life.get());
  const r = useDerivedValue(() => (1.5 - 0.15 * index) * (1 - 0.6 * life.get()));
  const opacity = useDerivedValue(() => 0.95 * seg(life.get(), 0, 0.15) * (1 - seg(life.get(), 0.45, 1)));
  return <Circle cx={cx} cy={cy} r={r} color="#ff8a3d" opacity={opacity} />;
}

/** One day of the week: an empty disc, a ticked green one, or today's ring. */
function Day({ index, completed, p, t, heat, playing }: Motion & { index: number; completed: number }) {
  const cx = BLEED + DAY_X + DAY_GAP * index;
  const cy = BLEED + DAY_Y;
  const start = T_CHECKS + CHECK_STAGGER * index;
  // 0 = empty, 1 = ticked. While playing, the ticks pop in one after another and leave together.
  const landing = useDerivedValue(() => (playing ? seg(t.get(), start, start + CHECK_LENGTH) : 1));
  const on = useDerivedValue(() => landing.get() * (playing ? 1 - seg(t.get(), T_RESET[0], T_RESET[1]) : 1));
  const pop = useDerivedValue(() => [{ scale: 0.5 + 0.5 * easeOutBack(on.get()) }]);
  const shown = useDerivedValue(() => Math.min(1, on.get() * 3));
  const drawn = useDerivedValue(() => seg(on.get(), 0.35, 1));
  // A green ring spreads from the disc as its tick lands.
  const rippleR = useDerivedValue(() => DAY_R + 9 * (1 - Math.pow(1 - landing.get(), 3)));
  const rippleOpacity = useDerivedValue(() => (landing.get() > 0 ? 0.7 * (1 - landing.get()) : 0));
  const ring = useDerivedValue(() => Math.min(1, p.get()));
  // Today's ring is drawn by a bright cap that cools down once the value settles.
  const cap = useDerivedValue(() => [{ rotate: ring.get() * 2 * Math.PI }]);

  if (index === completed) {
    return (
      <Group transform={[{ translateX: cx }, { translateY: cy }]} style="stroke" strokeWidth={RING_STROKE}>
        <Circle cx={0} cy={0} r={RING_R} color="#2e2e2e" />
        <Path path={RING} color={GREEN} strokeCap="round" start={0} end={ring} />
        <Group transform={cap} opacity={heat} style="fill">
          <Circle cx={0} cy={-RING_R} r={5} color="rgba(120,255,80,0.7)">
            <BlurMask blur={4} style="normal" />
          </Circle>
          <Circle cx={0} cy={-RING_R} r={2} color="#eaffdf" />
        </Group>
      </Group>
    );
  }
  if (index > completed) return <Circle cx={cx} cy={cy} r={DAY_R} color="#1d1d1d" />;
  return (
    <Group>
      <Circle cx={cx} cy={cy} r={DAY_R} color="#1d1d1d" />
      <Circle cx={cx} cy={cy} r={rippleR} style="stroke" strokeWidth={1.5} color={GREEN} opacity={rippleOpacity} />
      <Group transform={pop} origin={vec(cx, cy)} opacity={shown}>
        {/* Lighter towards the rim, like a lit bead. */}
        <Circle cx={cx} cy={cy} r={DAY_R}>
          <RadialGradient c={vec(cx, cy - 2)} r={DAY_R + 2} colors={['#46bc2b', '#4fc435', '#a5f493']} positions={[0, 0.62, 1]} />
        </Circle>
        <Group transform={[{ translateX: cx }, { translateY: cy }]}>
          <Path path={CHECK} style="stroke" strokeWidth={2.4} strokeCap="round" strokeJoin="round" color="#0d1a09" start={0} end={drawn} />
        </Group>
      </Group>
    </Group>
  );
}

function Surface({ p, t, time, heat, playing, still, completed }: Motion & { still: boolean; completed: number }) {
  const x = BLEED;
  const y = BLEED;

  // The flame leans and stretches from its base; the white core flickers faster.
  const flame = useDerivedValue(() => {
    const s = time.get();
    const stretch = 0.035 * Math.sin(s * 5.1) + 0.02 * Math.sin(s * 8.7 + 1);
    return [{ skewX: 0.05 * Math.sin(s * 3.3) }, { scaleX: 1 - stretch * 0.5 }, { scaleY: 1 + stretch }];
  });
  const core = useDerivedValue(() => {
    const s = time.get();
    const stretch = 0.09 * Math.sin(s * 7.3 + 2) + 0.05 * Math.sin(s * 11.9);
    return [{ scaleX: 1 - stretch * 0.4 }, { scaleY: 1 + stretch }];
  });
  const glow = useDerivedValue(() => 0.72 + 0.14 * Math.sin(time.get() * 2.1));

  const fillW = useDerivedValue(() => BAR_W * Math.min(1, Math.max(0, p.get())));
  // The fill keeps its round end while it grows out of the track's left edge.
  const fillX = useDerivedValue(() => x + INSET + Math.min(0, fillW.get() - BAR_H));
  const fillDraw = useDerivedValue(() => Math.max(fillW.get(), BAR_H));
  const fillClip = useDerivedValue(() =>
    rrect(rect(fillX.get(), y + BAR_Y, fillDraw.get(), BAR_H), BAR_H / 2, BAR_H / 2),
  );
  const glowOpacity = useDerivedValue(() => Math.min(1, fillW.get() / 24));
  const shimmer = useDerivedValue(() => {
    const travel = seg(time.get() % SHIMMER_PERIOD, 0, 1.5);
    return [{ translateX: x + INSET - SHIMMER_W + (fillW.get() + SHIMMER_W) * travel }];
  });

  const track = rrect(rect(x + INSET, y + BAR_Y, BAR_W, BAR_H), BAR_H / 2, BAR_H / 2);

  return (
    <Canvas style={styles.canvas}>
      <RoundedRect x={x + 6} y={y + 14} width={SIZE - 12} height={SIZE - 12} r={RADIUS} color="rgba(0,0,0,0.5)">
        <BlurMask blur={16} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={SIZE} height={SIZE} r={RADIUS} color="#101010" />
      {/* A hairline that catches the light along the top edge. */}
      <RoundedRect x={x + 0.5} y={y + 0.5} width={SIZE - 1} height={SIZE - 1} r={RADIUS - 0.5} style="stroke" strokeWidth={1}>
        <LinearGradient
          start={vec(0, y)}
          end={vec(0, y + SIZE)}
          colors={['rgba(255,255,255,0.3)', 'rgba(255,255,255,0.035)', 'rgba(255,255,255,0.035)', 'rgba(255,255,255,0.1)']}
          positions={[0, 0.3, 0.8, 1]}
        />
      </RoundedRect>

      <Group transform={[{ translateX: x + FLAME_X }, { translateY: y + FLAME_Y }]}>
        <Group transform={[{ scale: 1.15 }]} opacity={glow}>
          <Path path={FLAME} color="#ff3d0d">
            <BlurMask blur={12} style="normal" />
          </Path>
        </Group>
        <Group transform={flame} origin={vec(0, 22)}>
          <Path path={FLAME} color="#fa3d0e" />
        </Group>
        <Group transform={core} origin={vec(0, 21)}>
          <Path path={FLAME_CORE} color="#fffaf8" />
        </Group>
      </Group>
      {!still && EMBER_IDS.map((index) => <Ember key={index} index={index} time={time} />)}

      <Group transform={[{ translateX: x + FOOT_X }, { translateY: y + FOOT_Y }]} color="#cfcfcf">
        <Path path={FOOT} style="stroke" strokeWidth={2.6} strokeJoin="round" />
        {TOES.map(([tx, ty, r]) => (
          <Circle key={tx} cx={tx} cy={ty} r={r} />
        ))}
      </Group>

      <Rect x={x + INSET + 1} y={y + DIVIDER_Y} width={BAR_W - 2} height={1} color="rgba(255,255,255,0.13)" />

      {DAYS.map((day, index) => (
        <Day key={day} index={index} completed={completed} p={p} t={t} time={time} heat={heat} playing={playing} />
      ))}

      <RoundedRect rect={track} color="#162015" />
      <Group opacity={glowOpacity}>
        <RoundedRect x={x + INSET} y={y + BAR_Y} width={fillW} height={BAR_H} r={BAR_H / 2} color="rgba(71,189,41,0.6)">
          <BlurMask blur={7} style="normal" />
        </RoundedRect>
      </Group>
      <Group clip={track}>
        <RoundedRect rect={fillClip} color={GREEN} />
        <Group clip={fillClip}>
          <Group transform={shimmer}>
            <Rect x={0} y={y + BAR_Y} width={SHIMMER_W} height={BAR_H}>
              <LinearGradient
                start={vec(0, 0)}
                end={vec(SHIMMER_W, 0)}
                colors={['rgba(255,255,255,0)', 'rgba(220,255,200,0.55)', 'rgba(255,255,255,0)']}
              />
            </Rect>
          </Group>
        </Group>
      </Group>
    </Canvas>
  );
}

const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

/** Text set from the UI thread, so a counting number costs no React render. */
function LiveText({ text, style }: { text: SharedValue<string>; style: TextStyle }) {
  const animatedProps = useAnimatedProps(() => ({ text: text.get(), defaultValue: text.get() }) as TextInputProps);
  return (
    <AnimatedTextInput
      editable={false}
      pointerEvents="none"
      accessible={false}
      underlineColorAndroid="transparent"
      style={[styles.live, style]}
      animatedProps={animatedProps}
    />
  );
}

/**
 * Dark "streak + steps" widget: a flickering flame with the streak length, the week as a row of
 * ticked discs with today as a ring, and the step count with its percentage over a glowing bar.
 * The count, the percentage, the bar and today's ring all follow `progress`.
 */
export function StreakStepsWidget({
  progress,
  autoPlay = false,
  streakDays = 32,
  goal = 10000,
  completedDays = 3,
  onPress,
  accessibilityLabel = 'Steps',
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress !== undefined;
  const playing = autoPlay && !controlled && !reducedMotion;
  const [pressed, setPressed] = useState(false);
  const [run, setRun] = useState(0);
  // Width of one tabular digit of the step count, so "/10,000" can follow it as it grows.
  const [digit, setDigit] = useState(13.4);

  const target = useSharedValue(progress ?? REST);
  const t = useSharedValue(0);
  const time = useSharedValue(0);
  const heat = useSharedValue(0);
  const last = useSharedValue(progress ?? REST);

  useEffect(() => {
    if (progress === undefined) return;
    // Reanimated drops animations under reduced motion; value changes keep a short tween.
    target.set(
      withTiming(Math.min(1, Math.max(0, progress)), {
        duration: reducedMotion ? 200 : 700,
        easing: EASE_OUT,
        reduceMotion: ReduceMotion.Never,
      }),
    );
  }, [progress, reducedMotion, target]);

  useEffect(() => {
    if (!playing) return;
    t.set(0);
    t.set(withRepeat(withTiming(TOTAL, { duration: TOTAL * 1000, easing: Easing.linear }), -1));
    return () => cancelAnimation(t);
  }, [playing, run, t]);

  const p = useDerivedValue(() => {
    if (controlled) return target.get();
    if (!playing) return REST;
    return REST * easeInOut(seg(t.get(), T_FILL[0], T_FILL[1])) * (1 - easeInOut(seg(t.get(), T_RESET[0], T_RESET[1])));
  });
  useFrameCallback((frame) => {
    time.set(frame.timeSinceFirstFrame / 1000);
    // Speed of the value in shares per second, eased so the cap fades in and out.
    const dt = Math.max(1, frame.timeSincePreviousFrame ?? 16) / 1000;
    const speed = Math.abs(p.get() - last.get()) / dt;
    last.set(p.get());
    heat.set(heat.get() + (Math.min(1, speed * HEAT_GAIN) - heat.get()) * 0.18);
  }, !reducedMotion);

  const steps = useDerivedValue(() => String(Math.round(p.get() * goal)));
  const percent = useDerivedValue(() => `${Math.round(p.get() * 100)}%`);
  const goalStyle = useAnimatedStyle(() => ({ transform: [{ translateX: steps.get().length * digit }] }));

  const shown = Math.min(1, Math.max(0, progress ?? REST));

  return (
    <Pressable
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={`${accessibilityLabel}, ${streakDays} day streak`}
      accessibilityValue={{ min: 0, max: goal, now: Math.round(shown * goal) }}
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
          styles.widget,
          {
            transform: [{ scale: pressed ? 0.97 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: CSS_EASE_OUT,
          },
        ]}
      >
        <View pointerEvents="none" style={styles.surface}>
          <Surface p={p} t={t} time={time} heat={heat} playing={playing} still={reducedMotion} completed={completedDays} />
        </View>

        <Text style={[styles.caption, styles.streak]}>STREAK</Text>
        <Text style={styles.streakDays}>{streakDays}</Text>
        <Text style={styles.days}>DAYS</Text>

        {DAYS.map((day, index) => (
          <Text key={day} style={[styles.day, { left: DAY_X + DAY_GAP * index - DAY_GAP / 2 }]}>
            {day}
          </Text>
        ))}

        <Text style={[styles.caption, styles.stepsCaption]}>STEPS</Text>
        <LiveText text={steps} style={styles.steps} />
        <Animated.Text style={[styles.goal, goalStyle]}>/{goal.toLocaleString('en-US')}</Animated.Text>
        <LiveText text={percent} style={styles.percent} />
        <Text style={[styles.steps, styles.probe]} onLayout={(e) => setDigit(e.nativeEvent.layout.width / 4)}>
          0000
        </Text>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  widget: {
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
  live: {
    padding: 0,
  },
  caption: {
    position: 'absolute',
    color: '#6a6a6a',
    fontWeight: '500',
    letterSpacing: 0.7,
  },
  streak: {
    left: 88,
    top: 32,
    fontSize: 14,
    lineHeight: 18,
  },
  streakDays: {
    position: 'absolute',
    left: 88,
    top: 50,
    color: '#dbdbdb',
    fontSize: 24,
    lineHeight: 29,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  days: {
    position: 'absolute',
    left: 124.5,
    top: 58,
    color: '#d6d6d6',
    fontSize: 14.5,
    lineHeight: 18,
    fontWeight: '500',
    letterSpacing: 0.3,
  },
  day: {
    position: 'absolute',
    top: 162.5,
    width: DAY_GAP,
    textAlign: 'center',
    color: '#8a8a8a',
    fontFamily: MONO,
    fontSize: 13,
    lineHeight: 18,
  },
  stepsCaption: {
    left: INSET,
    top: 217.5,
    fontSize: 13.5,
    lineHeight: 18,
  },
  steps: {
    position: 'absolute',
    left: INSET,
    top: 236,
    width: 120,
    height: 28,
    color: '#ffffff',
    fontSize: 22,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  goal: {
    position: 'absolute',
    left: INSET + 4,
    top: 244,
    color: '#5c5c5c',
    fontFamily: MONO,
    fontSize: 14,
    lineHeight: 18,
  },
  percent: {
    position: 'absolute',
    right: INSET,
    top: 243,
    width: 70,
    height: 20,
    textAlign: 'right',
    color: '#5c5c5c',
    fontFamily: MONO,
    fontSize: 14,
    fontVariant: ['tabular-nums'],
  },
  // Measures a digit; never seen.
  probe: {
    width: undefined,
    height: undefined,
    opacity: 0,
  },
});
