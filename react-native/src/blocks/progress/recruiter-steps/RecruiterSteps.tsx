import { BlurMask, Canvas, Circle, Group, LinearGradient, Path, RoundedRect, vec } from '@shopify/react-native-skia';
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
  /** Controlled progress through the steps, 0…1: each step is a fifth. */
  progress?: number;
  /** Works through the steps up to the state shown in the reference, holds, and starts over. */
  autoPlay?: boolean;
  title?: string;
  /** What the agent is doing during each of the five steps (the dots after it are animated). */
  statuses?: [string, string, string, string, string];
  doneStatus?: string;
};

const WIDTH = 340;
const HEIGHT = 139.2;
const RADIUS = 46;
// The canvas overflows the panel by this much on every side to fit its shadow.
const BLEED = 40;

const STEPS = 5;
const STEP_X = 36.4;
const STEP_GAP = 66.6;
const STEP_Y = 71.3;
const DOT_R = 12.8;
const RING_R = 12.4;
const RING_W = 2.9;
// A dash starts and ends this far from the centres of the circles it joins.
const DASH_FROM = 19;
const DASH_TO = 47.6;
const DASH_W = 4.1;
const ICON_X = [279.8, 295.3, 306.7];
const ICON_Y = 34.3;
const ICON_R = 10.9;

const BLUE = '#1e9bff';
const DIM = '#1c5c96';

// The reference shows three steps done and the fourth well under way.
const REST = 0.754;
// autoPlay loop (seconds): steps per second while working.
const START = 0.4;
const RATE = 0.8;
const RUN = (REST * STEPS) / RATE;
const HOLD = 3.6;
const RESET = 0.7;
const LOOP = START + RUN + HOLD + RESET;
// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
const REST_T = START + RUN + 1.5;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

const CHECK = 'M-5.4 -0.6L-1.2 4.8L5.8 -5';
// Glyphs of the three tools, drawn around the centre of their disc.
const ENVELOPE = 'M-5.6 -3.8L5.6 -3.8L5.6 3.8L-5.6 3.8Z';
const FLAP = 'M-5.6 -3.6L0 1L5.6 -3.6';
const MERIDIAN = 'M0 -7.6C-4.6 -3.6 -4.6 3.6 0 7.6C4.6 3.6 4.6 -3.6 0 -7.6ZM-7.6 0L7.6 0';
const PAGE = 'M-3.9 -5.2L1.6 -5.2L3.9 -2.9L3.9 5.2L-3.9 5.2Z';
const LINES = 'M-1.8 -0.6L1.8 -0.6M-1.8 2.2L1.8 2.2';

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
  if (t < START + RUN) return ((t - START) * RATE) / STEPS;
  if (t < START + RUN + HOLD) return REST;
  return REST * (1 - easeInOutCubic((t - START - RUN - HOLD) / RESET));
}

/** How far past its completion step `index` is, in steps: everything about a dot follows from it. */
function pastStep(p: number, index: number) {
  'worklet';
  return clamp01(p) * STEPS - (index + 1);
}

/** The step being worked on at progress `p` (0…5; 5 when everything is done). */
function activeStep(p: number) {
  'worklet';
  return Math.min(STEPS, Math.floor(clamp01(p) * STEPS + 0.0001));
}

type Motion = {
  value: SharedValue<number>;
  time: SharedValue<number>;
  /** 0 with reduced motion: no spinner, pulses, pops or ripples. */
  ambient: number;
};

function Step({ index, value, time, ambient }: Motion & { index: number }) {
  const cx = BLEED + STEP_X + STEP_GAP * index;
  const cy = BLEED + STEP_Y;

  const past = useDerivedValue(() => pastStep(value.get(), index));
  const filled = useDerivedValue(() => clamp01(past.get() / 0.1));
  const ringOpacity = useDerivedValue(() => 1 - filled.get());
  // The dot lands with a springy overshoot and sends out a ripple.
  const dotR = useDerivedValue(() => {
    const u = clamp01(past.get() / 0.36);
    return DOT_R * (0.7 + 0.3 * clamp01(past.get() / 0.1) + ambient * 0.24 * Math.sin(Math.PI * u) * (1 - u));
  });
  const rippleR = useDerivedValue(() => DOT_R + 10 * easeOutCubic(past.get() / 0.5));
  const rippleOpacity = useDerivedValue(() => {
    const u = past.get() / 0.5;
    return u <= 0 || u >= 1 ? 0 : ambient * 0.7 * (1 - u);
  });
  // The check is drawn just after the dot fills.
  const check = useDerivedValue(() => (ambient > 0 ? easeOutCubic((past.get() - 0.06) / 0.26) : filled.get()));
  // The step being worked on: a short arc runs around its ring.
  const working = useDerivedValue(() => {
    const p = clamp01(value.get());
    return activeStep(p) === index && p > 0.0005 ? ambient * clamp01((p * STEPS - index) * 8) * ringOpacity.get() : 0;
  });
  const spin = useDerivedValue(() => [{ rotate: time.get() * 5.2 }]);
  // Then the dash to the next dot fills, with a pulse of light running along it.
  const dashFill = useDerivedValue(() => clamp01(past.get()));
  const dashOpacity = useDerivedValue(() => clamp01(past.get() * 40));
  const pulseX = useDerivedValue(() => cx + DASH_FROM + (DASH_TO - DASH_FROM) * dashFill.get() * ((time.get() * 1.3) % 1));
  const pulseOpacity = useDerivedValue(() => {
    const u = past.get();
    return u <= 0.04 || u >= 1 ? 0 : ambient * 0.9 * Math.sin(Math.PI * ((time.get() * 1.3) % 1));
  });

  return (
    <>
      {index < STEPS - 1 && (
        <>
          <Path path={`M${cx + DASH_FROM} ${cy}L${cx + DASH_TO} ${cy}`} style="stroke" strokeWidth={DASH_W} strokeCap="round" color={DIM} />
          <Path
            path={`M${cx + DASH_FROM} ${cy}L${cx + DASH_TO} ${cy}`}
            style="stroke"
            strokeWidth={DASH_W}
            strokeCap="round"
            color={BLUE}
            end={dashFill}
            opacity={dashOpacity}
          />
          <Circle cx={pulseX} cy={cy} r={2.6} color="#d9efff" opacity={pulseOpacity}>
            <BlurMask blur={1.6} style="normal" />
          </Circle>
        </>
      )}
      <Circle cx={cx} cy={cy} r={RING_R} style="stroke" strokeWidth={RING_W} color={DIM} opacity={ringOpacity} />
      <Group origin={vec(cx, cy)} transform={spin}>
        <Path
          path={`M${cx} ${cy - RING_R}A${RING_R} ${RING_R} 0 0 1 ${cx + RING_R} ${cy}`}
          style="stroke"
          strokeWidth={RING_W}
          strokeCap="round"
          color={BLUE}
          opacity={working}
        />
      </Group>
      <Circle cx={cx} cy={cy} r={rippleR} style="stroke" strokeWidth={1.5} color={BLUE} opacity={rippleOpacity} />
      <Circle cx={cx} cy={cy} r={dotR} color={BLUE} opacity={filled} />
      <Group transform={[{ translateX: cx }, { translateY: cy }]}>
        <Path path={CHECK} style="stroke" strokeWidth={2.1} strokeCap="round" strokeJoin="round" color="#04070c" end={check} />
      </Group>
    </>
  );
}

/** One of the tools the agent uses: they bob one after another, like a queue being worked through. */
function Tool({ index, time, ambient }: { index: number; time: SharedValue<number>; ambient: number }) {
  const transform = useDerivedValue(() => [
    { translateX: BLEED + ICON_X[index] },
    { translateY: BLEED + ICON_Y - ambient * 2.2 * Math.pow(Math.max(0, Math.sin(time.get() * 2.4 - index * 1.1)), 3) },
  ]);
  return (
    <Group transform={transform}>
      <Circle cx={0} cy={0} r={ICON_R + 1.5} color="#000000" />
      {index === 0 && (
        <>
          <Circle cx={0} cy={0} r={ICON_R}>
            <LinearGradient start={vec(0, -ICON_R)} end={vec(0, ICON_R)} colors={['#45c6ff', '#1477f5']} />
          </Circle>
          <Path path={ENVELOPE} color="#ffffff" />
          <Path path={FLAP} style="stroke" strokeWidth={1.1} strokeJoin="round" color="#2a8cf2" />
        </>
      )}
      {index === 1 && (
        <>
          <Circle cx={0} cy={0} r={ICON_R}>
            <LinearGradient start={vec(-ICON_R, -ICON_R)} end={vec(ICON_R, ICON_R)} colors={['#7fe0ff', '#0b5fd6']} />
          </Circle>
          <Circle cx={0} cy={0} r={7.6} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.85)" />
          <Path path={MERIDIAN} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.85)" />
        </>
      )}
      {index === 2 && (
        <>
          <Circle cx={0} cy={0} r={ICON_R} color="#f4f4f2" />
          <Path path={PAGE} style="stroke" strokeWidth={1.3} strokeJoin="round" color="#16171a" />
          <Path path={LINES} style="stroke" strokeWidth={1.1} strokeCap="round" color="#16171a" />
        </>
      )}
    </Group>
  );
}

function Surface(motion: Motion) {
  const x = BLEED;
  const y = BLEED;
  return (
    <Canvas style={styles.canvas}>
      <RoundedRect x={x + 10} y={y + 16} width={WIDTH - 20} height={HEIGHT - 8} r={RADIUS} color="rgba(6,16,40,0.5)">
        <BlurMask blur={16} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS} color="#000000" />
      {[0, 1, 2, 3, 4].map((index) => (
        <Step key={index} index={index} {...motion} />
      ))}
      {/* Back to front, so the first tool overlaps the others. */}
      {[2, 1, 0].map((index) => (
        <Tool key={index} index={index} time={motion.time} ambient={motion.ambient} />
      ))}
    </Canvas>
  );
}

/** One of the three dots after the status: they light one after another while the agent works. */
function Dot({ index, time, ambient }: { index: number; time: SharedValue<number>; ambient: number }) {
  const style = useAnimatedStyle(() => ({
    opacity: 1 - ambient * 0.72 * (0.5 - 0.5 * Math.sin(time.get() * 5 - index * 1.1)),
  }));
  return <Animated.Text style={[styles.status, style]}>.</Animated.Text>;
}

/**
 * Agent live activity: a title with the tools in use, five steps joined by dashes, and what the
 * agent is doing right now. Each step's dash fills with a pulse of light running along it, the
 * step being worked on carries a spinning arc, a finished step pops in with a ripple and draws its
 * check, the status line and the count slide over to the next step, and the dots after the status
 * keep typing.
 */
export function RecruiterSteps({
  progress,
  autoPlay = false,
  title = 'Recruiter',
  statuses = ['Reading inbox', 'Searching profiles', 'Screening candidates', 'Writing summary', 'Sending shortlist'],
  doneStatus = 'Shortlist sent',
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const target = controlled ? Math.min(1, Math.max(0, progress)) : REST;
  const auto = autoPlay && !controlled && !reducedMotion;
  const ambient = reducedMotion ? 0 : 1;

  // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
  const time = useSharedValue(0);
  const clock = useSharedValue(auto ? 0 : REST_T);
  const playing = useSharedValue(false);
  const looping = useSharedValue(false);
  const held = useSharedValue(target);
  // When the status line last changed, on the `time` axis.
  const swappedAt = useSharedValue(-10);

  useEffect(() => {
    looping.set(auto);
    playing.set(auto);
    clock.set(auto ? 0 : REST_T);
  }, [auto, clock, looping, playing]);

  useEffect(() => {
    if (!controlled) return;
    held.set(withTiming(target, { duration: reducedMotion ? 200 : 1600, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
  }, [controlled, target, reducedMotion, held]);

  const value = useDerivedValue(() => (controlled ? held.get() : loopValue(clock.get())));

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

  // The step being worked on, and the one before it for the hand-over of the status line.
  const initial = Math.min(STEPS, Math.floor((auto ? 0 : target) * STEPS + 0.0001));
  const [steps, setSteps] = useState({ now: initial, before: initial });
  const advance = (now: number) => setSteps((old) => (old.now === now ? old : { now, before: old.now }));
  useAnimatedReaction(
    () => activeStep(value.get()),
    (now, old) => {
      if (old == null || now === old) return;
      swappedAt.set(time.get());
      scheduleOnRN(advance, now);
    },
  );

  const incoming = useAnimatedStyle(() => {
    const u = ambient > 0 ? easeOutCubic((time.get() - swappedAt.get()) / 0.32) : 1;
    return { opacity: u, transform: [{ translateY: 7 * (1 - u) }] };
  });
  const outgoing = useAnimatedStyle(() => {
    const u = ambient > 0 ? easeOutCubic((time.get() - swappedAt.get()) / 0.24) : 1;
    return { opacity: 1 - u, transform: [{ translateY: -7 * u }] };
  });

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  const label = (step: number) => (step >= STEPS ? doneStatus : statuses[step]);
  const motion: Motion = { value, time, ambient };
  const done = Math.min(STEPS, Math.floor(target * STEPS + 0.0001));

  return (
    <Pressable
      onPress={restart}
      style={styles.panel}
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={title}
      accessibilityValue={{ min: 0, max: STEPS, now: done, text: `${label(done)}, ${done} of ${STEPS}` }}
    >
      <View pointerEvents="none" style={styles.surface}>
        <Surface {...motion} />
      </View>
      <View pointerEvents="none" style={StyleSheet.absoluteFill}>
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <Animated.View style={[styles.statusRow, outgoing]}>
          <Text style={styles.status} numberOfLines={1}>
            {label(steps.before)}
            {steps.before < STEPS ? '...' : ''}
          </Text>
        </Animated.View>
        <Animated.View style={[styles.statusRow, incoming]}>
          <Text style={styles.status} numberOfLines={1}>
            {label(steps.now)}
          </Text>
          {steps.now < STEPS && [0, 1, 2].map((index) => <Dot key={index} index={index} time={time} ambient={ambient} />)}
        </Animated.View>
        <Animated.Text style={[styles.status, styles.count, incoming]} numberOfLines={1}>
          {steps.now} of {STEPS}
        </Animated.Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  panel: {
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
    left: 24,
    top: 22.3,
    width: 200,
    color: '#ffffff',
    fontSize: 15.2,
    lineHeight: 20,
    fontWeight: '700',
    letterSpacing: -0.1,
  },
  statusRow: {
    position: 'absolute',
    left: 24,
    top: 96.6,
    height: 21,
    flexDirection: 'row',
  },
  status: {
    color: '#8e8e93',
    fontSize: 16.4,
    lineHeight: 21,
    fontWeight: '400',
    letterSpacing: -0.1,
    fontVariant: ['tabular-nums'],
  },
  count: {
    position: 'absolute',
    left: WIDTH - 24.4 - 80,
    top: 96.6,
    width: 80,
    textAlign: 'right',
  },
});
