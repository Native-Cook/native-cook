import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Path,
  Rect,
  RoundedRect,
  interpolateColors,
  rect,
  rrect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  cubicBezier,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Share of the milestones reached, 0…1. Leave undefined to show the reference's 3 of 5. */
  progress?: number;
  /** Plays by itself: the fill springs from milestone to milestone up to 3 of 5, holds, and drains. */
  autoPlay?: boolean;
  title?: string;
  steps?: string[];
  tip?: string;
  onPress?: () => void;
  accessibilityLabel?: string;
};

const WIDTH = 330;
const HEIGHT = 254;
const RADIUS = 16;
// The canvas overflows the card by this much on every side to fit its shadow.
const BLEED = 44;

const BUBBLE_X = 19.7;
const BUBBLE_Y = 56;
const BUBBLE_W = 289.5;
const BUBBLE_H = 63.3;
const BUBBLE_R = 13;
const TRACK_X = 21.3;
const TRACK_Y = 140;
const TRACK_W = 288;
const TRACK_H = 33;
const DOT_Y = TRACK_Y + TRACK_H / 2;
const DOT_R = 9.6;
// Distance of the first and last milestone from the pill's ends.
const DOT_INSET = 19;
// How far past a milestone's centre the fill reaches once it is done: up to the next circle.
const FILL_LEAD = 57;
const TIP_X = 70.8;
const TIP_Y = 214.4;
const TIP_W = 186.7;
const TIP_H = 22.3;
const SPARKS = [0, 1, 2, 3, 4, 5, 6];

// The reference rests with three of five milestones reached.
const REST_DONE = 3;
const DEFAULT_STEPS = ['Create Project', 'Add Media', 'Set Goals', 'Team', 'Launch'];

// The autoPlay run is one clock in seconds: one spring per milestone, a hold, a drain.
const STEP_FIRST = 0.5;
const STEP_EVERY = 0.95;
const STEP_LENGTH = 0.6;
const HOLD = 2.6;
const RESET = 0.5;
const TAIL = 0.4;

const GREEN = '#2faa77';
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

const CHECK = 'M-4 0.3L-1.3 3L4.2 -2.8';
const PLUS = 'M-3.6 0H3.6M0 -3.6V3.6';
const TAIL_PATH = `M${BUBBLE_X + 19} ${BUBBLE_Y + BUBBLE_H - 1}H${BUBBLE_X + 41.5}L${BUBBLE_X + 31.5} ${BUBBLE_Y + BUBBLE_H + 7.4}Q${BUBBLE_X + 29.8} ${BUBBLE_Y + BUBBLE_H + 8.8} ${BUBBLE_X + 28.1} ${BUBBLE_Y + BUBBLE_H + 7.4}Z`;

function seg(t: number, from: number, to: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - from) / (to - from)));
}
function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
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
  /** Milestones reached, a fraction while the fill is between two. */
  done: SharedValue<number>;
  /** Free-running seconds for the breathing haze and the floating bubble. */
  time: SharedValue<number>;
  count: number;
};

/** A milestone: a grey circle that pops to mint when the fill reaches it, then ages to green and white. */
function Milestone({ index, done, count }: Motion & { index: number }) {
  const pitch = (TRACK_W - DOT_INSET * 2) / Math.max(count - 1, 1);
  const cx = BLEED + TRACK_X + DOT_INSET + pitch * index;
  const cy = BLEED + DOT_Y;
  const last = index === count - 1;

  // How long ago, in milestones, the fill got here; negative while it is still on its way.
  const age = useDerivedValue(() => done.get() - index);
  const reached = useDerivedValue(() => seg(age.get(), 0.15, 0.6));
  const pop = useDerivedValue(() => [{ scale: 1 + 0.32 * Math.sin(Math.PI * reached.get()) * (1 - reached.get() * 0.4) }]);
  const next = useDerivedValue(() => seg(age.get(), -1.1, -0.9) * (1 - reached.get()));
  // Newest is mint with a dark tick, the one before deep green with a white tick, older ones white.
  const fill = useDerivedValue(() => interpolateColors(age.get(), [1.2, 2, 3], ['#8df6cf', '#1f7d54', '#ffffff']));
  const tick = useDerivedValue(() => interpolateColors(age.get(), [1.2, 2, 3], ['#0d5a3a', '#ffffff', '#0b3d24']));
  const drawn = useDerivedValue(() => seg(age.get(), 0.35, 0.8));
  const waiting = useDerivedValue(() => 1 - reached.get());

  // A ring and a handful of sparks celebrate the landing.
  const burst = useDerivedValue(() => seg(age.get(), 0.3, 1));
  const rippleR = useDerivedValue(() => DOT_R + 12 * easeOut(burst.get()));
  const ripple = useDerivedValue(() => (burst.get() > 0 && burst.get() < 1 ? 0.8 * (1 - burst.get()) : 0));

  return (
    <Group>
      <Circle cx={cx} cy={cy} r={rippleR} style="stroke" strokeWidth={1.5} color="#bdfbe4" opacity={ripple} />
      {SPARKS.map((spark) => (
        <Spark key={spark} spark={spark} cx={cx} cy={cy} burst={burst} />
      ))}
      <Group transform={pop} origin={vec(cx, cy)}>
        <Group opacity={waiting}>
          <Circle cx={cx} cy={cy} r={DOT_R} color="#cfcfd3" />
          <Circle cx={cx} cy={cy} r={DOT_R} color="#b4e6d4" opacity={next} />
          <Group transform={[{ translateX: cx }, { translateY: cy }]} style="stroke" strokeWidth={1.7} strokeCap="round" strokeJoin="round">
            <Path path={last ? PLUS : CHECK} color="#a3a3a8" />
            <Path path={last ? PLUS : CHECK} color="#8fc7b2" opacity={next} />
          </Group>
        </Group>
        <Group opacity={reached}>
          <Circle cx={cx} cy={cy + 1} r={DOT_R} color="rgba(0,40,20,0.25)">
            <BlurMask blur={2.5} style="normal" />
          </Circle>
          <Circle cx={cx} cy={cy} r={DOT_R} color={fill} />
          <Group transform={[{ translateX: cx }, { translateY: cy }]}>
            <Path path={CHECK} style="stroke" strokeWidth={1.9} strokeCap="round" strokeJoin="round" color={tick} start={0} end={drawn} />
          </Group>
        </Group>
      </Group>
    </Group>
  );
}

function Spark({ spark, cx, cy, burst }: { spark: number; cx: number; cy: number; burst: SharedValue<number> }) {
  const angle = (spark / SPARKS.length) * Math.PI * 2 + 0.4;
  const reach = 15 + 9 * ((spark * 0.618) % 1);
  const x = useDerivedValue(() => cx + Math.cos(angle) * (DOT_R + reach * easeOut(burst.get())));
  const y = useDerivedValue(() => cy + Math.sin(angle) * (DOT_R + reach * easeOut(burst.get())) + 5 * burst.get() * burst.get());
  const r = useDerivedValue(() => (spark % 2 ? 1.5 : 2.1) * (1 - 0.7 * burst.get()));
  const opacity = useDerivedValue(() => (burst.get() > 0 && burst.get() < 1 ? 1 - burst.get() * burst.get() : 0));
  return <Circle cx={x} cy={y} r={r} color={spark % 3 === 0 ? '#ffffff' : spark % 3 === 1 ? '#5edcac' : '#1d885c'} opacity={opacity} />;
}

function Surface({ done, time, count, still }: Motion & { still: boolean }) {
  const x = BLEED;
  const y = BLEED;
  const left = x + TRACK_X;
  const top = y + TRACK_Y;
  const pitch = (TRACK_W - DOT_INSET * 2) / Math.max(count - 1, 1);
  const track = rrect(rect(left, top, TRACK_W, TRACK_H), TRACK_H / 2, TRACK_H / 2);

  // The fill ends just short of the next milestone, and takes the whole pill at the last one.
  const end = useDerivedValue(() => {
    const d = done.get();
    const full = seg(d, count - 1, count);
    return Math.min(TRACK_W, DOT_INSET + pitch * (d - 1) + FILL_LEAD + (TRACK_W - DOT_INSET - pitch * (count - 2) - FILL_LEAD) * full);
  });
  const shown = useDerivedValue(() => seg(done.get(), 0.05, 0.35));
  const fill = useDerivedValue(() => rrect(rect(left, top, Math.max(TRACK_H, end.get()), TRACK_H), TRACK_H / 2, TRACK_H / 2));
  const fillEnd = useDerivedValue(() => vec(left + Math.max(TRACK_H, end.get()), 0));
  // A mint haze runs ahead of the fill and breathes.
  const haze = useDerivedValue(() => rect(left + end.get() - TRACK_H / 2, top, 76, TRACK_H));
  const hazeStart = useDerivedValue(() => vec(left + end.get() - TRACK_H / 2, 0));
  const hazeEnd = useDerivedValue(() => vec(left + end.get() + 58, 0));
  const hazeOpacity = useDerivedValue(() => shown.get() * (still ? 0.8 : 0.72 + 0.2 * Math.sin(time.get() * 2.2)));
  // The speech bubble floats a little.
  const float = useDerivedValue(() => [{ translateY: still ? 0 : 1.4 * Math.sin(time.get() * 1.7) }]);

  const indices = [];
  for (let index = 0; index < count; index++) indices.push(index);

  return (
    <Canvas style={styles.canvas}>
      {/* The reference's heavy slate shadow, thrown down and to the right. */}
      <RoundedRect x={x + 14} y={y + 22} width={WIDTH - 6} height={HEIGHT - 8} r={RADIUS + 4} color="rgba(86,103,119,0.5)">
        <BlurMask blur={16} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS}>
        <LinearGradient start={vec(x, y)} end={vec(x + WIDTH, y + HEIGHT)} colors={['#f7f6f7', '#efeef0', '#e9e8eb']} />
      </RoundedRect>
      <RoundedRect x={x + 0.6} y={y + 0.6} width={WIDTH - 1.2} height={HEIGHT - 1.2} r={RADIUS - 0.6} style="stroke" strokeWidth={1.2} color="rgba(255,255,255,0.95)" />

      <Group transform={float}>
        <RoundedRect x={x + BUBBLE_X} y={y + BUBBLE_Y + 5} width={BUBBLE_W} height={BUBBLE_H} r={BUBBLE_R} color="rgba(60,70,90,0.16)">
          <BlurMask blur={9} style="normal" />
        </RoundedRect>
        <Group transform={[{ translateX: x }, { translateY: y }]}>
          <Path path={TAIL_PATH} color="#ffffff" />
        </Group>
        <RoundedRect x={x + BUBBLE_X} y={y + BUBBLE_Y} width={BUBBLE_W} height={BUBBLE_H} r={BUBBLE_R} color="#ffffff" />
        <RoundedRect x={x + 28.2} y={y + 87.2} width={165} height={25.6} r={12.8} color="#f1f2f4" />
        <Group transform={[{ translateX: x + 40 }, { translateY: y + 100 }]}>
          <Circle cx={0} cy={0} r={6.2} color={GREEN} />
          <Path path="M-2.7 0.2L-0.9 2L2.9 -1.9" style="stroke" strokeWidth={1.4} strokeCap="round" strokeJoin="round" color="#ffffff" />
        </Group>
        <Group transform={[{ translateX: x + 208 }, { translateY: y + 100 }]}>
          <Circle cx={0} cy={0} r={6.2} color={GREEN} />
          <Path path="M-2.3 -2.3L2.3 2.3M2.3 -2.3L-2.3 2.3" style="stroke" strokeWidth={1.3} strokeCap="round" color="#ffffff" />
        </Group>
        <Group transform={[{ translateX: x + 292 }, { translateY: y + 72.4 }]}>
          <Path path="M-4.4 -2.8L-1.2 -0.8L4.2 -3.2M-4.4 1.4L-1.2 3.4L4.2 1" style="stroke" strokeWidth={1.4} strokeCap="round" strokeJoin="round" color="#111111" />
        </Group>
        <Group transform={[{ translateX: x + 291.5 }, { translateY: y + 100.5 }]} style="stroke" strokeWidth={1} strokeCap="round" color="#7c7c82">
          <RoundedRect x={-4.4} y={-3.8} width={8.8} height={8.2} r={1.6} />
          <Path path="M0.6 -5.6V-3.8M-1.6 1.6L1.2 -1" />
        </Group>
      </Group>

      <RoundedRect rect={track} color="#e9e9ec" />
      <Group clip={track}>
        <Group opacity={hazeOpacity}>
          <Rect rect={haze}>
            <LinearGradient start={hazeStart} end={hazeEnd} colors={['rgba(125,228,189,0.85)', 'rgba(150,235,205,0.4)', 'rgba(170,240,215,0)']} />
          </Rect>
        </Group>
        <Group opacity={shown}>
          <RoundedRect rect={fill}>
            <LinearGradient
              start={vec(left, 0)}
              end={fillEnd}
              colors={['#03170c', '#06281a', '#1d885c', '#5edcac', '#7de4bd']}
              positions={[0, 0.25, 0.6, 0.9, 1]}
            />
          </RoundedRect>
        </Group>
      </Group>
      <RoundedRect x={left + 0.5} y={top + 0.5} width={TRACK_W - 1} height={TRACK_H - 1} r={TRACK_H / 2} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.7)" />
      <RoundedRect x={left - 0.3} y={top - 0.3} width={TRACK_W + 0.6} height={TRACK_H + 0.6} r={TRACK_H / 2} style="stroke" strokeWidth={0.6} color="rgba(120,125,135,0.22)" />
      {indices.map((index) => (
        <Milestone key={index} index={index} done={done} time={time} count={count} />
      ))}

      <RoundedRect x={x + TIP_X} y={y + TIP_Y} width={TIP_W} height={TIP_H} r={TIP_H / 2} color="#f0f0f1" />
      <RoundedRect x={x + TIP_X + 0.4} y={y + TIP_Y + 0.4} width={TIP_W - 0.8} height={TIP_H - 0.8} r={TIP_H / 2} style="stroke" strokeWidth={0.8} color="#dcdcdf" />
      <Group transform={[{ translateX: x + TIP_X + 12.5 }, { translateY: y + TIP_Y + TIP_H / 2 }]}>
        <Path path="M-3 0H3M0 -3V3" style="stroke" strokeWidth={1.9} strokeCap="round" color={GREEN} />
      </Group>
    </Canvas>
  );
}

/**
 * Light "You're almost there!" card: a funding-goal speech bubble over a pill-shaped stepper whose
 * dark-to-mint fill springs from milestone to milestone. Each milestone pops with a ring and a few
 * sparks when the fill lands on it, then ages from mint to green to white as the fill moves on.
 */
export function MilestoneStepper({
  progress,
  autoPlay = false,
  title = 'You’re almost there!',
  steps = DEFAULT_STEPS,
  tip = 'Tip: Use round numbers to attract backers.',
  onPress,
  accessibilityLabel = 'Project setup',
}: Props) {
  const reducedMotion = useReducedMotion();
  const count = steps.length;
  const controlled = progress !== undefined;
  const playing = autoPlay && !controlled && !reducedMotion;
  const rest = Math.min(REST_DONE, count);
  const initial = controlled ? Math.min(1, Math.max(0, progress)) * count : rest;
  const [pressed, setPressed] = useState(false);
  const [run, setRun] = useState(0);
  // Whole milestones the fill has covered: switches the labels.
  const [reached, setReached] = useState(Math.floor(initial + 0.75));

  const target = useSharedValue(initial);
  const t = useSharedValue(0);
  const time = useSharedValue(0);

  const drainAt = STEP_FIRST + (rest - 1) * STEP_EVERY + STEP_LENGTH + HOLD;
  const loop = drainAt + RESET + TAIL;

  useEffect(() => {
    if (progress === undefined) return;
    // Reanimated drops animations under reduced motion; value changes keep a short tween.
    target.set(
      withTiming(Math.min(1, Math.max(0, progress)) * count, {
        duration: reducedMotion ? 200 : 700,
        easing: EASE_OUT,
        reduceMotion: ReduceMotion.Never,
      }),
    );
  }, [progress, count, reducedMotion, target]);

  useEffect(() => {
    if (!playing) return;
    t.set(0);
    t.set(withRepeat(withTiming(loop, { duration: loop * 1000, easing: Easing.linear }), -1));
    return () => cancelAnimation(t);
  }, [playing, run, loop, t]);

  useFrameCallback((frame) => {
    time.set(frame.timeSinceFirstFrame / 1000);
  }, !reducedMotion);

  const done = useDerivedValue(() => {
    if (controlled) return target.get();
    if (!playing) return rest;
    let d = 0;
    for (let k = 0; k < rest; k++) d += easeOutBack(seg(t.get(), STEP_FIRST + k * STEP_EVERY, STEP_FIRST + k * STEP_EVERY + STEP_LENGTH));
    return d * (1 - easeInOut(seg(t.get(), drainAt, drainAt + RESET)));
  });

  useAnimatedReaction(
    () => Math.min(count, Math.max(0, Math.floor(done.get() + 0.75))),
    (now, before) => {
      if (now !== before) scheduleOnRN(setReached, now);
    },
  );

  const float = useAnimatedStyle(() => ({ transform: [{ translateY: reducedMotion ? 0 : 1.4 * Math.sin(time.get() * 1.7) }] }));
  const pitch = (TRACK_W - DOT_INSET * 2) / Math.max(count - 1, 1);
  const shown = Math.min(1, Math.max(0, progress ?? rest / count));

  return (
    <Pressable
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={accessibilityLabel}
      accessibilityValue={{ min: 0, max: count, now: Math.round(shown * count), text: `${reached} of ${count} steps done` }}
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
          <Surface done={done} time={time} count={count} still={reducedMotion} />
        </View>

        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, float]}>
          <Text style={[styles.strong, styles.goal]}>Funding goal</Text>
          <Text style={styles.placeholder}>Funding target</Text>
          <Text style={[styles.strong, styles.stretch]}>Stretch goals.</Text>
        </Animated.View>

        {steps.map((step, index) => {
          const on = index < reached;
          // The first and last labels are nudged inwards so they stay inside the card.
          const center = Math.min(WIDTH - 38, Math.max(42, TRACK_X + DOT_INSET + pitch * index));
          return (
            <View key={index} pointerEvents="none" style={[styles.step, { left: center - 40 }]}>
              <View style={[styles.number, index === 0 && on && styles.numberSelected]}>
                <Text style={[styles.numberLabel, on && styles.numberOn, index === 0 && on && styles.numberSelectedLabel]}>{index + 1}</Text>
              </View>
              <Text style={[styles.name, on && styles.nameOn]} numberOfLines={1}>
                {index === reached - 1 ? `(${step})` : step}
              </Text>
            </View>
          );
        })}

        <Text style={styles.tip} numberOfLines={1}>
          {tip}
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
  title: {
    position: 'absolute',
    left: 20,
    top: 16,
    color: '#0b0b0b',
    fontSize: 22,
    lineHeight: 28,
    fontWeight: '800',
    letterSpacing: -0.6,
  },
  strong: {
    position: 'absolute',
    color: '#111111',
    fontSize: 10.5,
    lineHeight: 14,
    fontWeight: '600',
  },
  goal: {
    left: 29.3,
    top: 65.4,
  },
  stretch: {
    left: 219.5,
    top: 93,
    fontSize: 9.3,
    letterSpacing: -0.3,
  },
  placeholder: {
    position: 'absolute',
    left: 50.5,
    top: 93,
    color: '#8e9096',
    fontSize: 10,
    lineHeight: 14,
  },
  step: {
    position: 'absolute',
    top: 175.5,
    width: 80,
    alignItems: 'center',
  },
  number: {
    width: 11,
    height: 11,
    borderRadius: 5.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  numberSelected: {
    backgroundColor: GREEN,
  },
  numberLabel: {
    color: '#8d8d92',
    fontSize: 7.5,
    lineHeight: 10,
    fontVariant: ['tabular-nums'],
  },
  numberOn: {
    color: '#2a9d6e',
    fontWeight: '700',
  },
  numberSelectedLabel: {
    color: '#ffffff',
  },
  name: {
    marginTop: 0.5,
    color: '#8d8d92',
    fontSize: 8.5,
    lineHeight: 12,
    letterSpacing: -0.1,
  },
  nameOn: {
    color: '#0d0d0d',
    fontWeight: '700',
  },
  tip: {
    position: 'absolute',
    left: TIP_X + 20,
    top: TIP_Y + TIP_H / 2 - 6,
    color: '#222222',
    fontSize: 8,
    lineHeight: 12,
    fontWeight: '500',
    letterSpacing: -0.3,
  },
});
