import {
  Blur,
  BlurMask,
  Canvas,
  Group,
  LinearGradient,
  Paint,
  Path,
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

type Collaborator = {
  initials: string;
  /** Avatar fill and the colour of its initials. */
  color: string;
  ink: string;
};

type Props = {
  /** Controlled progress, 0…1. Leave undefined to show the reference's 65% (or the autoPlay loop). */
  progress?: number;
  /** Fills from 0 to 65% by itself, holds, resets and starts over. */
  autoPlay?: boolean;
  title?: string;
  tag?: string;
  due?: string;
  collaborators?: Collaborator[];
  detailsLabel?: string;
  onDetailsPress?: () => void;
};

const WIDTH = 340;
const HEIGHT = 225;
const RADIUS = 32;
// The canvas overflows the card by this much on every side to fit its shadow.
const BLEED = 40;

const BAR_X = 25.5;
const BAR_Y = 107;
const BAR_W = 289;
const BAR_H = 29.5;

const SPINNER_X = 32.5;
const SPINNER_Y = 26.5;
const SPOKES = 8;

const AVATAR = 34;
const AVATAR_PITCH = 38.7;

// Value shown in the reference, and the autoPlay loop built around it (seconds).
const REST = 0.65;
const START = 0.35;
const FILL = 2.2;
const HOLD = 3.2;
const RESET = 0.55;
const LOOP = START + FILL + HOLD + RESET;
// A moment in the loop where everything is at rest: used when nothing is playing.
const REST_T = START + FILL + 1;
const SWEEP_PERIOD = 2.8;
const SWEEP_TRAVEL = 1.15;
const SWEEP_W = 70;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

const DEFAULT_COLLABORATORS: Collaborator[] = [
  { initials: 'AM', color: '#e7a273', ink: '#5a2e12' },
  { initials: 'RK', color: '#d9d3c5', ink: '#4b4636' },
  { initials: 'JS', color: '#c6d2da', ink: '#2f3f4a' },
];

function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

// Overshoots a little before settling: avatars pop in like they are on a spring.
function easeOutBack(t: number) {
  'worklet';
  const c = 2.2;
  return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2);
}

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/** Progress at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < START) return 0;
  if (t < START + FILL) return REST * easeInOutCubic((t - START) / FILL);
  if (t < LOOP - RESET) return REST;
  return REST * (1 - easeInOutCubic((t - (LOOP - RESET)) / RESET));
}

/** 0…1: how busy the spinner is at `t` seconds into the loop (it only chases while the bar moves). */
function loopActivity(t: number) {
  'worklet';
  return clamp01((t - START) / 0.25) * clamp01((START + FILL + 0.1 - t) / 0.45);
}

/** Scale of avatar `index`: they leave during the reset and pop back in one after another. */
function avatarScale(t: number, index: number) {
  'worklet';
  if (t > LOOP - RESET) return 1 - clamp01((t - (LOOP - RESET)) / (RESET * 0.7));
  return easeOutBack(clamp01((t - 0.12 - 0.11 * index) / 0.5));
}

function fillWidth(p: number) {
  'worklet';
  return BAR_H + (BAR_W - BAR_H) * clamp01(p);
}

const SPOKE_PATHS = Array.from({ length: SPOKES }, (_, i) => {
  const a = (i / SPOKES) * Math.PI * 2 - Math.PI / 2;
  const c = Math.cos(a);
  const s = Math.sin(a);
  return `M${3 * c} ${3 * s}L${8 * c} ${8 * s}`;
});

function Spoke({ index, time, activity }: { index: number; time: SharedValue<number>; activity: SharedValue<number> }) {
  // A bright head travels clockwise and leaves a fading trail, like a system activity indicator.
  const opacity = useDerivedValue(() => {
    const phase = (((time.get() * 1.25 - index / SPOKES) % 1) + 1) % 1;
    return 1 - activity.get() * 0.78 * phase;
  });
  return <Path path={SPOKE_PATHS[index]} opacity={opacity} />;
}

type Motion = {
  value: SharedValue<number>;
  time: SharedValue<number>;
  activity: SharedValue<number>;
  /** 0 with reduced motion: no sweep, no breathing. */
  ambient: number;
};

function Surface({ value, time, activity, ambient }: Motion) {
  const x = BLEED;
  const y = BLEED;
  const bx = x + BAR_X;
  const by = y + BAR_Y;

  const width = useDerivedValue(() => fillWidth(value.get()));
  // The pill has a minimum length, so it fades in over the first few percent.
  const fillAlpha = useDerivedValue(() => clamp01(value.get() / 0.03));
  const glowAlpha = useDerivedValue(
    () => fillAlpha.get() * (0.84 + ambient * 0.14 * Math.sin(time.get() * 1.9)),
  );
  // A band of light crosses the fill every few seconds.
  const sweep = useDerivedValue(() => {
    const phase = (time.get() % SWEEP_PERIOD) / SWEEP_TRAVEL;
    return [{ translateX: -SWEEP_W + (width.get() + SWEEP_W) * Math.min(phase, 1) }];
  });
  const sweepAlpha = useDerivedValue(() => {
    const phase = (time.get() % SWEEP_PERIOD) / SWEEP_TRAVEL;
    return ambient * fillAlpha.get() * (phase < 1 ? Math.sin(Math.PI * phase) : 0);
  });

  const colors = ['#c0fe6e', '#93fda9', '#6bfcdf', '#6bfcdf'];
  const stops = [0, 0.42, 0.78, 1];
  const gradient = (
    <LinearGradient start={vec(bx, by)} end={vec(bx + BAR_W * 0.72, by)} colors={colors} positions={stops} />
  );

  return (
    <Canvas style={styles.canvas}>
      <RoundedRect x={x + 6} y={y + 16} width={WIDTH - 12} height={HEIGHT - 8} r={RADIUS} color="rgba(60,70,90,0.3)">
        <BlurMask blur={18} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS}>
        <LinearGradient
          start={vec(x, y)}
          end={vec(x, y + HEIGHT)}
          colors={['#525252', '#3b3b3b', '#353535', '#313131', '#1f1f1f', '#242424', '#3d3b3c']}
          positions={[0, 0.06, 0.16, 0.5, 0.93, 0.985, 1]}
        />
      </RoundedRect>

      <Group transform={[{ translateX: x + SPINNER_X }, { translateY: y + SPINNER_Y }]}>
        <Group style="stroke" strokeWidth={1.5} strokeCap="round" color="#f4f4f4">
          {SPOKE_PATHS.map((_, i) => (
            <Spoke key={i} index={i} time={time} activity={activity} />
          ))}
        </Group>
      </Group>

      {/* Glow first so the translucent track sits on top of it, as in the reference. */}
      <Group
        layer={
          <Paint opacity={glowAlpha}>
            <Blur blur={11} />
          </Paint>
        }
      >
        <RoundedRect x={bx} y={by} width={width} height={BAR_H} r={BAR_H / 2}>
          {gradient}
        </RoundedRect>
      </Group>
      <RoundedRect x={bx} y={by} width={BAR_W} height={BAR_H} r={BAR_H / 2} color="rgba(118,125,128,0.86)" />
      <RoundedRect
        x={bx + 0.5}
        y={by + 0.5}
        width={BAR_W - 1}
        height={BAR_H - 1}
        r={BAR_H / 2 - 0.5}
        style="stroke"
        strokeWidth={1}
        color="rgba(255,255,255,0.1)"
      />
      <RoundedRect x={bx} y={by} width={width} height={BAR_H} r={BAR_H / 2} opacity={fillAlpha}>
        {gradient}
      </RoundedRect>
      <RoundedRect x={bx} y={by} width={width} height={BAR_H} r={BAR_H / 2} opacity={sweepAlpha}>
        <LinearGradient
          start={vec(bx, by)}
          end={vec(bx + SWEEP_W, by + 12)}
          colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.55)', 'rgba(255,255,255,0)']}
          transform={sweep}
        />
      </RoundedRect>
    </Canvas>
  );
}

function Avatar({ person, index, clock, animated }: { person: Collaborator; index: number; clock: SharedValue<number>; animated: boolean }) {
  const style = useAnimatedStyle(() => {
    const s = animated ? avatarScale(clock.get(), index) : 1;
    return { opacity: clamp01(s * 2), transform: [{ scale: s }] };
  });
  return (
    <Animated.View style={[styles.avatarRing, { left: 30 + AVATAR_PITCH * index }, style]}>
      <View style={[styles.avatar, { backgroundColor: person.color }]}>
        <Text style={[styles.initials, { color: person.ink }]}>{person.initials}</Text>
      </View>
    </Animated.View>
  );
}

function DetailsButton({ label, onPress }: { label: string; onPress?: () => void }) {
  const [pressed, setPressed] = useState(false);
  const w = 89.5;
  const h = 22;
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
      hitSlop={10}
      style={styles.detailsSlot}
    >
      <Animated.View
        style={[
          styles.details,
          {
            transform: [{ scale: pressed ? 0.97 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: CSS_EASE_OUT,
          },
        ]}
      >
        <Canvas style={StyleSheet.absoluteFill}>
          <RoundedRect x={0} y={0} width={w} height={h} r={h / 2}>
            <LinearGradient start={vec(0, 0)} end={vec(0, h)} colors={['#2c3438', '#1e1f20', '#232425']} />
          </RoundedRect>
          {/* Glassy rim: cool light from the top left, a white glint at the bottom right. */}
          <RoundedRect x={0.5} y={0.5} width={w - 1} height={h - 1} r={h / 2 - 0.5} style="stroke" strokeWidth={1}>
            <LinearGradient
              start={vec(0, 0)}
              end={vec(w, h)}
              colors={['rgba(120,150,170,0.55)', 'rgba(120,150,170,0.18)', 'rgba(255,255,255,0.1)', 'rgba(255,255,255,0.6)']}
              positions={[0, 0.4, 0.7, 1]}
            />
          </RoundedRect>
        </Canvas>
        <Text style={styles.detailsLabel} numberOfLines={1}>
          {label}
        </Text>
        <Canvas style={styles.arrow}>
          <Path
            path="M1 4.5H8M5 1.5L8 4.5L5 7.5"
            style="stroke"
            strokeWidth={1.1}
            strokeCap="round"
            strokeJoin="round"
            color="#ffffff"
          />
        </Canvas>
      </Animated.View>
    </Pressable>
  );
}

/**
 * Dark project card: a spinner glyph, title and tag, a large percentage and a lime-to-mint pill
 * bar with the due date sitting on its track, then the collaborators and a "More details" pill.
 * The bar glows and breathes, a band of light sweeps along it, the spinner chases while the value
 * moves and the avatars pop back in each time the loop restarts.
 */
export function ProjectProgressCard({
  progress,
  autoPlay = false,
  title = 'Project Progress',
  tag = 'Onboarding prototype',
  due = 'Due July 28',
  collaborators = DEFAULT_COLLABORATORS,
  detailsLabel = 'More details',
  onDetailsPress,
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
  // When a controlled value last changed, on the `time` axis.
  const movedAt = useSharedValue(-10);

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

  const value = useDerivedValue(() => (controlled ? held.get() : loopValue(clock.get())));
  const activity = useDerivedValue(() => {
    if (reducedMotion) return 0;
    if (!controlled) return loopActivity(clock.get());
    const since = time.get() - movedAt.get();
    return clamp01(since / 0.2) * clamp01((0.9 - since) / 0.4);
  });

  // The number is real text: re-render only when the whole percent changes.
  const [percent, setPercent] = useState(Math.round(target * 100));
  useAnimatedReaction(
    () => Math.round(value.get() * 100),
    (now, before) => {
      if (now !== before) scheduleOnRN(setPercent, now);
    },
  );

  // The due date turns dark where the fill has reached it: a bar-sized window slides so that its
  // right edge follows the fill, and the dark copy of the text slides back by as much to stay put.
  const covered = useAnimatedStyle(() => ({
    transform: [{ translateX: (value.get() <= 0 ? 0 : fillWidth(value.get())) - BAR_W }],
  }));
  const uncovered = useAnimatedStyle(() => ({
    transform: [{ translateX: BAR_W - (value.get() <= 0 ? 0 : fillWidth(value.get())) }],
  }));

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  return (
    <Pressable onPress={restart} accessible={false} style={styles.card}>
      <View pointerEvents="none" style={styles.surface}>
        <Surface value={value} time={time} activity={activity} ambient={reducedMotion ? 0 : 1} />
      </View>

      <View
        style={StyleSheet.absoluteFill}
        pointerEvents="none"
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={`${title}, ${tag}, ${due}`}
        accessibilityValue={{ min: 0, max: 100, now: percent }}
      >
        <View style={styles.heading}>
          <Text style={styles.title} numberOfLines={1}>
            {title}
          </Text>
          <View style={styles.tag}>
            <Text style={styles.tagLabel} numberOfLines={1}>
              {tag}
            </Text>
          </View>
        </View>

        <View style={styles.value}>
          <Text style={styles.number}>{percent}</Text>
          <Text style={[styles.number, styles.percent]}>%</Text>
        </View>

        <Text style={[styles.due, styles.dueOnTrack]} numberOfLines={1}>
          {due}
        </Text>
        <Animated.View style={[styles.covered, covered]}>
          <Animated.Text style={[styles.due, styles.dueOnFill, uncovered]} numberOfLines={1}>
            {due}
          </Animated.Text>
        </Animated.View>

        <Text style={styles.collaborators} numberOfLines={1}>
          Collaborators <Text style={styles.count}>{collaborators.length}</Text>
        </Text>
        {collaborators.map((person, i) => (
          <Avatar key={i} person={person} index={i} clock={clock} animated={!controlled && !reducedMotion} />
        ))}
      </View>

      <DetailsButton label={detailsLabel} onPress={onDetailsPress} />
    </Pressable>
  );
}

const DUE_W = 96;

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
  heading: {
    position: 'absolute',
    left: 25.5,
    top: 39.5,
    height: 22,
    flexDirection: 'row',
    alignItems: 'center',
  },
  title: {
    color: '#f4f4f4',
    fontSize: 14.8,
    lineHeight: 20,
    fontWeight: '500',
  },
  tag: {
    marginLeft: 7.5,
    height: 22,
    paddingHorizontal: 8,
    borderRadius: 11,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.09)',
    backgroundColor: 'rgba(255,255,255,0.02)',
    justifyContent: 'center',
  },
  tagLabel: {
    color: '#c9c9c9',
    fontSize: 9.2,
    lineHeight: 13,
    fontWeight: '500',
  },
  value: {
    position: 'absolute',
    left: 25.5,
    top: 63.5,
    flexDirection: 'row',
    alignItems: 'baseline',
  },
  number: {
    color: '#f2f2f2',
    fontSize: 31,
    lineHeight: 38,
    fontWeight: '500',
    letterSpacing: -1,
    fontVariant: ['tabular-nums'],
  },
  percent: {
    color: '#8b8b8b',
  },
  due: {
    position: 'absolute',
    width: DUE_W,
    textAlign: 'center',
    fontSize: 11.5,
    lineHeight: 15,
    fontWeight: '600',
  },
  dueOnTrack: {
    left: 269 - DUE_W / 2,
    top: BAR_Y + BAR_H / 2 - 7.5,
    color: '#d6dfe1',
  },
  covered: {
    position: 'absolute',
    left: BAR_X,
    top: BAR_Y,
    width: BAR_W,
    height: BAR_H,
    overflow: 'hidden',
  },
  dueOnFill: {
    left: 269 - DUE_W / 2 - BAR_X,
    top: BAR_H / 2 - 7.5,
    color: '#1f5a4c',
  },
  collaborators: {
    position: 'absolute',
    left: 30,
    top: 148.5,
    color: '#b6b6b6',
    fontSize: 11,
    lineHeight: 15,
    fontWeight: '500',
  },
  count: {
    color: '#7c7c7c',
  },
  avatarRing: {
    position: 'absolute',
    top: 172.5,
    width: AVATAR,
    height: AVATAR,
    borderRadius: 11,
    borderCurve: 'continuous',
    backgroundColor: '#474747',
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatar: {
    width: AVATAR - 4,
    height: AVATAR - 4,
    borderRadius: 9,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
  },
  initials: {
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '700',
    letterSpacing: 0.2,
  },
  detailsSlot: {
    position: 'absolute',
    left: 225,
    top: 185.3,
  },
  details: {
    width: 89.5,
    height: 22,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  detailsLabel: {
    color: '#ffffff',
    fontSize: 9.5,
    lineHeight: 14,
    fontWeight: '500',
  },
  arrow: {
    width: 9,
    height: 9,
    marginLeft: 3,
  },
});
