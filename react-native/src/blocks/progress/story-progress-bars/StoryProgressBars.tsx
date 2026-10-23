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

export type Story = {
  title: string;
  description: string;
  /** Label of the call-to-action button. */
  action: string;
};

type Props = {
  /** Controlled position, 0…1 across all four stories. Leave undefined to show the reference (or the autoPlay loop). */
  progress?: number;
  /** Plays the stories one after another, like a story viewer, and starts over after the last. */
  autoPlay?: boolean;
  /** Exactly four stories; the second one is the reference's. */
  stories?: Story[];
  /** Called with the index of the story on screen. */
  onActionPress?: (story: number) => void;
  onPausedChange?: (paused: boolean) => void;
};

const WIDTH = 310;
const CARD_H = 280;
const HEIGHT = 330;
const RADIUS = 27.5;
// The canvas overflows the component by this much on every side to fit the card's shadow.
const BLEED = 24;

const COUNT = 4;
const SEG_X = 21.5;
const SEG_Y = 20.5;
const SEG_W = 63;
const SEG_PITCH = 68;
const SEG_H = 1.5;

const HORIZON_Y = 131.5;
const HORIZON_X0 = 52;
const HORIZON_X1 = 260;

const ACTION_X = 15.8;
const ACTION_Y = 228;
const ACTION_W = 278.4;
const ACTION_H = 36.5;
const PAUSE = 39;
const PAUSE_X = WIDTH - PAUSE;
const PAUSE_Y = 290.5;

// Seconds each story stays up, and how long the hand-over to the next one takes.
const STORY = 3.4;
const TRANSITION = 0.7;
const LOOP = STORY * COUNT;
// Position shown in the reference: the second story with its bar full.
const REST = 0.5;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const RAD = Math.PI / 180;

const DEFAULT_STORIES: Story[] = [
  {
    title: 'Workspaces',
    description: 'Bring every project, doc and thread together in one shared space.',
    action: 'Create Workspace →',
  },
  {
    title: 'Integrations',
    description: 'Integrate your apps to create, automate, and command in one place.',
    action: 'Connect Integrations →',
  },
  {
    title: 'Automations',
    description: 'Turn the steps you repeat into flows that run while you sleep.',
    action: 'Build Automation →',
  },
  {
    title: 'Insights',
    description: 'See what shipped, what stalled and what needs you next.',
    action: 'View Insights →',
  },
];

/** The four floating app chips: size, and where each sits (x, y, tilt in degrees, scale) in every story. */
const CHIPS = [
  { size: 29, at: [[88, 92, -8, 0.85], [51.8, 80.8, -14, 1], [70, 64, 10, 0.9], [60, 98, -4, 0.8]] },
  { size: 19.5, at: [[128, 104, 6, 1], [104.8, 96.8, -10, 1], [140, 84, 0, 1.35], [118, 62, 12, 1.05]] },
  { size: 18, at: [[170, 78, -6, 1], [177.5, 67, 8, 1], [206, 98, -10, 1.05], [160, 92, 4, 1.4]] },
  { size: 28, at: [[214, 96, 10, 0.85], [241.5, 71.3, 6, 1], [252, 60, -8, 0.8], [236, 84, 14, 0.95]] },
];

/** Glow colour of each story. */
const GLOWS = [
  [84, 136, 255],
  [140, 92, 255],
  [255, 92, 176],
  [58, 214, 196],
];

const STARS = [
  { x: 98.5, y: 62.2, r: 0.8 },
  { x: 72, y: 106.5, r: 0.5 },
  { x: 192.5, y: 83.5, r: 0.8 },
  { x: 202.5, y: 63.2, r: 0.4 },
  { x: 266.2, y: 114.8, r: 0.9 },
  { x: 280, y: 81.2, r: 0.4 },
  { x: 41, y: 114, r: 0.6 },
  { x: 150, y: 96, r: 0.5 },
];
const BEAMS = 'M131.5 86V129M219 94V129M24 100V130M272 72V118';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function easeOutCubic(t: number) {
  'worklet';
  return 1 - Math.pow(1 - t, 3);
}

// Overshoots a little before settling: the chips land in their new places like on a spring.
function easeOutBack(t: number) {
  'worklet';
  const c = 1.9;
  return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2);
}

/** Story on screen for a position `v` (0…1 across all stories). */
function storyAt(v: number) {
  'worklet';
  return Math.min(COUNT - 1, Math.max(0, Math.ceil(v * COUNT - 1e-6) - 1));
}

/**
 * 0…1: how far story `storyAt(v)` has taken over from the previous one. Without `wraps` the
 * first story has nothing to take over from.
 */
function handOver(v: number, wraps: boolean) {
  'worklet';
  const k = storyAt(v);
  if (k === 0 && !wraps) return 1;
  return clamp01(((v * COUNT - k) * STORY) / TRANSITION);
}

type Motion = {
  /** 0…1 across all stories. */
  value: SharedValue<number>;
  time: SharedValue<number>;
  /** True when the last story hands over to the first (autoPlay). */
  wraps: boolean;
  /** 0 with reduced motion: nothing floats or twinkles. */
  ambient: number;
};

function Segment({ index, value, wraps }: { index: number; value: SharedValue<number>; wraps: boolean }) {
  const x = SEG_X + index * SEG_PITCH;
  const width = useDerivedValue(() => SEG_W * clamp01(value.get() * COUNT - index));
  // A finished bar flashes as its story hands over; when the set starts again the old bars fade out.
  const flash = useDerivedValue(() => {
    const k = storyAt(value.get());
    const t = handOver(value.get(), wraps);
    return k === index + 1 ? 0.9 * (1 - t) : 0;
  });
  const ghost = useDerivedValue(() => (wraps && storyAt(value.get()) === 0 ? 1 - handOver(value.get(), true) : 0));
  const head = useDerivedValue(() => x + width.get());
  // It fades out as the bar completes, so a full bar at rest is a plain line.
  const glint = useDerivedValue(() =>
    storyAt(value.get()) === index ? clamp01(width.get() / 3) * clamp01((SEG_W - width.get()) / 2) : 0,
  );
  return (
    <Group>
      <RoundedRect x={x} y={SEG_Y} width={SEG_W} height={SEG_H} r={SEG_H / 2} color="rgba(255,255,255,0.2)" />
      <RoundedRect x={x} y={SEG_Y} width={SEG_W} height={SEG_H} r={SEG_H / 2} color="#ffffff" opacity={ghost} />
      <RoundedRect x={x} y={SEG_Y - 1} width={SEG_W} height={SEG_H + 2} r={2} color="#ffffff" opacity={flash}>
        <BlurMask blur={3} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={SEG_Y} width={width} height={SEG_H} r={SEG_H / 2} color="#ffffff" />
      {/* A glint rides the head of the bar that is filling. */}
      <Circle cx={head} cy={SEG_Y + SEG_H / 2} r={2.2} color="#ffffff" opacity={glint}>
        <BlurMask blur={2.2} style="normal" />
      </Circle>
    </Group>
  );
}

function Chip({ index, value, time, wraps, ambient }: { index: number } & Motion) {
  const chip = CHIPS[index];
  const s = chip.size;
  const r = s * 0.27;
  // The chips fly to a new arrangement for every story, and bob gently where they are.
  const transform = useDerivedValue(() => {
    const k = storyAt(value.get());
    const from = chip.at[(k + COUNT - 1) % COUNT];
    const to = chip.at[k];
    const e = easeOutBack(handOver(value.get(), wraps));
    const t = time.get();
    return [
      { translateX: from[0] + (to[0] - from[0]) * e + ambient * 1.2 * Math.sin(t * 0.6 + index) },
      { translateY: from[1] + (to[1] - from[1]) * e + ambient * 2.4 * Math.sin(t * 0.9 + index * 1.7) },
      { rotate: (from[2] + (to[2] - from[2]) * e + ambient * 2 * Math.sin(t * 0.7 + index * 2.3)) * RAD },
      { scale: from[3] + (to[3] - from[3]) * e },
    ];
  });
  const faces = [
    ['#3b3b3b', '#1f1f1f'],
    ['#9a3cf0', '#5a17a6'],
    ['#f7f4ed', '#e2ded3'],
    ['#ffffff', '#e8e8e8'],
  ][index];
  return (
    <Group transform={transform}>
      <RoundedRect x={-s / 2} y={-s / 2 + 3} width={s} height={s} r={r} color="rgba(0,0,0,0.5)">
        <BlurMask blur={4} style="normal" />
      </RoundedRect>
      <RoundedRect x={-s / 2} y={-s / 2} width={s} height={s} r={r}>
        <LinearGradient start={vec(0, -s / 2)} end={vec(0, s / 2)} colors={faces} />
      </RoundedRect>
      <RoundedRect x={-s / 2 + 0.4} y={-s / 2 + 0.4} width={s - 0.8} height={s - 0.8} r={r - 0.4} style="stroke" strokeWidth={0.8} color="rgba(255,255,255,0.2)" />
      {/* Generic glyphs: a crescent, three bars, a note and four dots. */}
      {index === 0 && (
        <>
          <Circle cx={0} cy={0} r={7.4}>
            <LinearGradient start={vec(0, -7.4)} end={vec(0, 7.4)} colors={['#ffffff', '#b9b9b9']} />
          </Circle>
          <Circle cx={3.6} cy={-3.2} r={6} color="#2b2b2b" />
        </>
      )}
      {index === 1 && (
        <>
          <RoundedRect x={-4.6} y={-4} width={9.2} height={1.7} r={0.85} color="#ffffff" />
          <RoundedRect x={-4.6} y={-0.85} width={9.2} height={1.7} r={0.85} color="#ffffff" />
          <RoundedRect x={-4.6} y={2.3} width={5.8} height={1.7} r={0.85} color="#ffffff" />
        </>
      )}
      {index === 2 && (
        <>
          <RoundedRect x={-4} y={-5.2} width={8} height={10.4} r={1.7} color="#f5b301" />
          <Rect x={-2.1} y={-1.7} width={4.2} height={1.1} color="rgba(255,255,255,0.92)" />
          <Rect x={-2.1} y={0.8} width={4.2} height={1.1} color="rgba(255,255,255,0.92)" />
        </>
      )}
      {index === 3 && (
        <>
          <Circle cx={-3.8} cy={-3.8} r={2.7} color="#4f9dff" />
          <Circle cx={3.8} cy={-3.8} r={2.7} color="#34c98a" />
          <Circle cx={-3.8} cy={3.8} r={2.7} color="#ff5d7a" />
          <Circle cx={3.8} cy={3.8} r={2.7} color="#ffc043" />
        </>
      )}
    </Group>
  );
}

function Surface({ value, time, wraps, ambient }: Motion) {
  // The glow under the horizon takes each story's colour and breathes.
  const glow = useDerivedValue(() => {
    const k = storyAt(value.get());
    const from = GLOWS[(k + COUNT - 1) % COUNT];
    const to = GLOWS[k];
    const e = easeOutCubic(handOver(value.get(), wraps));
    const c = [0, 1, 2].map((i) => Math.round(from[i] + (to[i] - from[i]) * e));
    return `${c[0]},${c[1]},${c[2]}`;
  });
  const glowColors = useDerivedValue(() => [`rgba(${glow.get()},0.34)`, `rgba(${glow.get()},0.12)`, `rgba(${glow.get()},0)`]);
  const lineColors = useDerivedValue(() => [
    `rgba(${glow.get()},0)`,
    `rgba(${glow.get()},0.9)`,
    'rgba(255,236,244,1)',
    `rgba(${glow.get()},0.9)`,
    `rgba(${glow.get()},0)`,
  ]);
  const breath = useDerivedValue(() => 0.86 + ambient * 0.14 * Math.sin(time.get() * 1.4));
  // A spark runs along the horizon each time a story takes over.
  const spark = useDerivedValue(() => HORIZON_X0 + (HORIZON_X1 - HORIZON_X0) * easeOutCubic(handOver(value.get(), wraps)));
  const sparkAlpha = useDerivedValue(() => {
    const t = handOver(value.get(), wraps);
    return t >= 1 ? 0 : Math.sin(Math.PI * t);
  });
  const twinkleA = useDerivedValue(() => 0.62 + ambient * 0.38 * Math.sin(time.get() * 2.3));
  const twinkleB = useDerivedValue(() => 0.62 + ambient * 0.38 * Math.sin(time.get() * 1.7 + 2.1));
  const cx = (HORIZON_X0 + HORIZON_X1) / 2;

  return (
    <Canvas style={styles.canvas}>
      <Group transform={[{ translateX: BLEED }, { translateY: BLEED }]}>
        <RoundedRect x={6} y={12} width={WIDTH - 12} height={CARD_H - 8} r={RADIUS} color="rgba(0,0,0,0.55)">
          <BlurMask blur={12} style="normal" />
        </RoundedRect>
        <RoundedRect x={0} y={0} width={WIDTH} height={CARD_H} r={RADIUS}>
          <LinearGradient start={vec(0, 0)} end={vec(0, CARD_H)} colors={['#1b1b1b', '#141414']} />
        </RoundedRect>
        <RoundedRect x={0.5} y={0.5} width={WIDTH - 1} height={CARD_H - 1} r={RADIUS - 0.5} style="stroke" strokeWidth={1}>
          <LinearGradient start={vec(0, 0)} end={vec(0, CARD_H)} colors={['rgba(255,255,255,0.2)', 'rgba(255,255,255,0.07)']} />
        </RoundedRect>

        {Array.from({ length: COUNT }, (_, i) => (
          <Segment key={i} index={i} value={value} wraps={wraps} />
        ))}

        {/* Scene: faint beams and stars over a glowing horizon. */}
        <Path path={BEAMS} style="stroke" strokeWidth={0.6}>
          <LinearGradient start={vec(0, 70)} end={vec(0, 130)} colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.13)', 'rgba(255,255,255,0.04)']} />
        </Path>
        <Group opacity={twinkleA}>
          {STARS.filter((_, i) => i % 2 === 0).map((star, i) => (
            <Circle key={i} cx={star.x} cy={star.y} r={star.r} color="rgba(255,255,255,0.9)" />
          ))}
        </Group>
        <Group opacity={twinkleB}>
          {STARS.filter((_, i) => i % 2 === 1).map((star, i) => (
            <Circle key={i} cx={star.x} cy={star.y} r={star.r} color="rgba(255,255,255,0.9)" />
          ))}
        </Group>
        <Group clip={{ x: 0, y: HORIZON_Y - 40, width: WIDTH, height: 40 }} opacity={breath}>
          <Group transform={[{ translateX: cx }, { translateY: HORIZON_Y }, { scaleX: 112 }, { scaleY: 26 }]}>
            <Circle cx={0} cy={0} r={1}>
              <RadialGradient c={vec(0, 0)} r={1} colors={glowColors} />
            </Circle>
          </Group>
          <Group transform={[{ translateX: cx }, { translateY: HORIZON_Y }, { scaleX: 46 }, { scaleY: 11 }]}>
            <Circle cx={0} cy={0} r={1}>
              <RadialGradient c={vec(0, 0)} r={1} colors={['rgba(255,170,120,0.2)', 'rgba(255,170,120,0)']} />
            </Circle>
          </Group>
        </Group>
        <Rect x={HORIZON_X0} y={HORIZON_Y - 0.4} width={HORIZON_X1 - HORIZON_X0} height={0.8}>
          <LinearGradient start={vec(HORIZON_X0, 0)} end={vec(HORIZON_X1, 0)} colors={lineColors} positions={[0, 0.3, 0.5, 0.7, 1]} />
        </Rect>
        <Circle cx={spark} cy={HORIZON_Y} r={2.4} color="#ffffff" opacity={sparkAlpha}>
          <BlurMask blur={3} style="solid" />
        </Circle>

        {CHIPS.map((_, i) => (
          <Chip key={i} index={i} value={value} time={time} wraps={wraps} ambient={ambient} />
        ))}
      </Group>
    </Canvas>
  );
}

/** Title and description of one story: it rises in as its story takes over and leaves upwards. */
function Copy({ index, story, value, wraps }: { index: number; story: Story; value: SharedValue<number>; wraps: boolean }) {
  const style = useAnimatedStyle(() => {
    const k = storyAt(value.get());
    const t = handOver(value.get(), wraps);
    // The old copy is gone before the new one shows, so the two never read through each other.
    if (index === k) {
      return { opacity: clamp01((t - 0.45) / 0.55), transform: [{ translateY: (1 - easeOutCubic(t)) * 10 }] };
    }
    if (index === (k + COUNT - 1) % COUNT) {
      return { opacity: 1 - clamp01(t / 0.4), transform: [{ translateY: -easeOutCubic(t) * 8 }] };
    }
    return { opacity: 0, transform: [{ translateY: 0 }] };
  });
  return (
    <Animated.View style={[styles.copy, style]}>
      <Text style={styles.title} numberOfLines={1}>
        {story.title}
      </Text>
      <Text style={styles.description} numberOfLines={2}>
        {story.description}
      </Text>
    </Animated.View>
  );
}

function ActionLabel({ index, story, value, wraps }: { index: number; story: Story; value: SharedValue<number>; wraps: boolean }) {
  const style = useAnimatedStyle(() => {
    const k = storyAt(value.get());
    const t = handOver(value.get(), wraps);
    if (index === k) return { opacity: clamp01((t - 0.45) / 0.55) };
    if (index === (k + COUNT - 1) % COUNT) return { opacity: 1 - clamp01(t / 0.4) };
    return { opacity: 0 };
  });
  return (
    <Animated.Text style={[styles.actionLabel, style]} numberOfLines={1}>
      {story.action}
    </Animated.Text>
  );
}

function usePress() {
  const [pressed, setPressed] = useState(false);
  const handlers = {
    onPressIn: () => {
      setPressed(true);
      if (process.env.EXPO_OS !== 'web') {
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
      }
    },
    onPressOut: () => setPressed(false),
  };
  const style = {
    transform: [{ scale: pressed ? 0.97 : 1 }],
    transitionProperty: 'transform' as const,
    transitionDuration: 130,
    transitionTimingFunction: CSS_EASE_OUT,
  };
  return { handlers, style };
}

function PauseButton({ paused, morph, onPress }: { paused: boolean; morph: SharedValue<number>; onPress: () => void }) {
  const press = usePress();
  const c = PAUSE / 2;
  // The two bars turn and shrink away while the play triangle grows in.
  const bars = useDerivedValue(() => [{ rotate: morph.get() * 90 * RAD }, { scale: 1 - 0.5 * morph.get() }]);
  const barsAlpha = useDerivedValue(() => 1 - morph.get());
  const play = useDerivedValue(() => [{ rotate: (morph.get() - 1) * 90 * RAD }, { scale: 0.5 + 0.5 * morph.get() }]);
  const glow = useDerivedValue(() => 0.75 + 0.25 * morph.get());
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={paused ? 'Play' : 'Pause'}
      onPress={onPress}
      {...press.handlers}
      hitSlop={8}
      style={styles.pauseSlot}
    >
      <Animated.View style={[styles.pause, press.style]}>
        <Canvas style={StyleSheet.absoluteFill}>
          <Circle cx={c} cy={c} r={c}>
            <LinearGradient start={vec(0, 0)} end={vec(0, PAUSE)} colors={['#2d2d2d', '#1f1f1f']} />
          </Circle>
          <Group clip={{ rect: { x: 0, y: 0, width: PAUSE, height: PAUSE }, rx: c, ry: c }} opacity={glow}>
            <Circle cx={c} cy={PAUSE + 2} r={PAUSE * 0.62}>
              <RadialGradient c={vec(c, PAUSE + 2)} r={PAUSE * 0.62} colors={['rgba(120,84,220,0.6)', 'rgba(120,84,220,0)']} />
            </Circle>
          </Group>
          <Circle cx={c} cy={c} r={c - 0.5} style="stroke" strokeWidth={1}>
            <LinearGradient start={vec(0, 0)} end={vec(0, PAUSE)} colors={['rgba(255,255,255,0.22)', 'rgba(255,255,255,0.04)']} />
          </Circle>
          <Group transform={bars} origin={vec(c, c - 1.5)} opacity={barsAlpha}>
            <RoundedRect x={c - 5.2} y={c - 6.8} width={3.2} height={10.6} r={1.6}>
              <LinearGradient start={vec(0, c - 6.8)} end={vec(0, c + 3.8)} colors={['#ffffff', '#c9c9c9']} />
            </RoundedRect>
            <RoundedRect x={c + 2} y={c - 6.8} width={3.2} height={10.6} r={1.6}>
              <LinearGradient start={vec(0, c - 6.8)} end={vec(0, c + 3.8)} colors={['#ffffff', '#c9c9c9']} />
            </RoundedRect>
          </Group>
          <Group transform={play} origin={vec(c, c - 1.5)} opacity={morph}>
            <Path
              path={`M${c - 3.4} ${c - 7}L${c + 5.6} ${c - 1.5}L${c - 3.4} ${c + 4}Z`}
              color="#f1f1f1"
              style="fill"
            />
            <Path
              path={`M${c - 3.4} ${c - 7}L${c + 5.6} ${c - 1.5}L${c - 3.4} ${c + 4}Z`}
              color="#f1f1f1"
              style="stroke"
              strokeWidth={1.6}
              strokeJoin="round"
            />
          </Group>
        </Canvas>
      </Animated.View>
    </Pressable>
  );
}

/**
 * Dark onboarding card with story-style progress lines across the top, floating app chips over a
 * glowing horizon, a title, a description and a call-to-action, plus a round pause button. Each
 * bar that fills hands the card over to its next story: the copy rises in, the chips fly to a
 * new arrangement and land with a little overshoot, the glow changes colour and a spark runs
 * along the horizon. The pause button really pauses and turns into a play button.
 */
export function StoryProgressBars({
  progress,
  autoPlay = false,
  stories = DEFAULT_STORIES,
  onActionPress,
  onPausedChange,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const target = controlled ? Math.min(1, Math.max(0, progress)) : REST;
  const auto = autoPlay && !controlled && !reducedMotion;
  const [paused, setPaused] = useState(false);
  const action = usePress();

  // Seconds: `time` never stops (ambient effects), `clock` is the position in the story loop.
  const time = useSharedValue(0);
  const clock = useSharedValue(auto ? 0 : REST * LOOP);
  const playing = useSharedValue(false);
  const held = useSharedValue(target);
  const morph = useSharedValue(0);

  useEffect(() => {
    playing.set(auto);
    clock.set(auto ? 0 : REST * LOOP);
  }, [auto, clock, playing]);

  useEffect(() => {
    if (!controlled) return;
    held.set(withTiming(target, { duration: reducedMotion ? 200 : 700, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
  }, [controlled, target, reducedMotion, held]);

  useEffect(() => {
    morph.set(withTiming(paused ? 1 : 0, { duration: 240, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
  }, [paused, morph]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(time.get() + dt);
    // The pause button holds the story clock; the scene keeps floating.
    if (!playing.get() || morph.get() > 0.5) return;
    let t = clock.get() + dt;
    if (t >= LOOP) t -= LOOP;
    clock.set(t);
  }, !reducedMotion);

  const value = useDerivedValue(() => (controlled ? held.get() : clock.get() / LOOP));

  // Only the story index reaches React, for the labels read by assistive technology.
  const [story, setStory] = useState(storyAt(target));
  useAnimatedReaction(
    () => storyAt(value.get()),
    (now, before) => {
      if (now !== before) scheduleOnRN(setStory, now);
    },
  );

  // Tapping the card skips to the next story, like a story viewer.
  const skip = () => {
    if (!auto) return;
    clock.set(((Math.floor(clock.get() / STORY + 1e-6) + 1) * STORY) % LOOP);
  };

  const togglePaused = () => {
    setPaused(!paused);
    onPausedChange?.(!paused);
  };

  const current = stories[story] ?? stories[0];

  return (
    <View style={styles.root}>
      <Pressable onPress={skip} accessible={false} style={styles.card}>
        <View pointerEvents="none" style={styles.surface}>
          <Surface value={value} time={time} wraps={auto} ambient={reducedMotion ? 0 : 1} />
        </View>

        <View
          style={StyleSheet.absoluteFill}
          pointerEvents="none"
          accessible
          accessibilityRole="progressbar"
          accessibilityLabel={`${current.title}. ${current.description}`}
          accessibilityValue={{ min: 1, max: COUNT, now: story + 1, text: `Story ${story + 1} of ${COUNT}` }}
        >
          {stories.slice(0, COUNT).map((item, i) => (
            <Copy key={i} index={i} story={item} value={value} wraps={auto} />
          ))}
        </View>

        <Pressable
          accessibilityRole="button"
          accessibilityLabel={current.action}
          onPress={() => onActionPress?.(story)}
          {...action.handlers}
          style={styles.actionSlot}
        >
          <Animated.View style={[styles.action, action.style]}>
            <Canvas style={StyleSheet.absoluteFill}>
              <RoundedRect x={0} y={0} width={ACTION_W} height={ACTION_H} r={ACTION_H / 2}>
                <LinearGradient start={vec(0, 0)} end={vec(0, ACTION_H)} colors={['#212121', '#191919']} />
              </RoundedRect>
              <RoundedRect x={0.5} y={0.5} width={ACTION_W - 1} height={ACTION_H - 1} r={ACTION_H / 2 - 0.5} style="stroke" strokeWidth={1}>
                <LinearGradient start={vec(0, 0)} end={vec(0, ACTION_H)} colors={['rgba(255,255,255,0.1)', 'rgba(255,255,255,0.16)']} />
              </RoundedRect>
            </Canvas>
            {stories.slice(0, COUNT).map((item, i) => (
              <ActionLabel key={i} index={i} story={item} value={value} wraps={auto} />
            ))}
          </Animated.View>
        </Pressable>
      </Pressable>

      <PauseButton paused={paused} morph={morph} onPress={togglePaused} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
  },
  card: {
    width: WIDTH,
    height: CARD_H,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  canvas: {
    width: WIDTH + BLEED * 2,
    height: CARD_H + BLEED * 2,
  },
  copy: {
    position: 'absolute',
    left: 19.5,
    top: 146.5,
    width: 276,
  },
  title: {
    color: '#f5f5f5',
    fontSize: 16,
    lineHeight: 22,
    fontWeight: '500',
    letterSpacing: 0.1,
  },
  description: {
    marginTop: 4,
    color: 'rgba(255,255,255,0.56)',
    fontSize: 13.5,
    lineHeight: 20,
    fontWeight: '400',
    letterSpacing: 0.1,
  },
  actionSlot: {
    position: 'absolute',
    left: ACTION_X,
    top: ACTION_Y,
  },
  action: {
    width: ACTION_W,
    height: ACTION_H,
    alignItems: 'center',
    justifyContent: 'center',
  },
  actionLabel: {
    position: 'absolute',
    color: '#f5f5f5',
    fontSize: 14,
    lineHeight: 20,
    fontWeight: '500',
    letterSpacing: 0.1,
  },
  pauseSlot: {
    position: 'absolute',
    left: PAUSE_X,
    top: PAUSE_Y,
  },
  pause: {
    width: PAUSE,
    height: PAUSE,
  },
});
