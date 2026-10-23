import { Ionicons, MaterialCommunityIcons } from '@expo/vector-icons';
import {
  Blur,
  BlurMask,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Paint,
  Paragraph,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  Skia,
  type SkParagraph,
  type SkPath,
  TextAlign,
  rect,
  rrect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export type Glyph = 'spark' | 'bloom' | 'dot' | 'petal' | 'clover';

export type Mood = {
  name: string;
  /** The capsule from top to bottom. */
  light: string;
  base: string;
  deep: string;
  /** The mark in the capsule, the chips' text and the round button. */
  tint: string;
  /** The arrow on that button. */
  ink: string;
  glyph: Glyph;
  /** What the first chip goes through, tap by tap. */
  emotions: string[];
};

export type CheckIn = {
  mood: Mood;
  /** The emotion and the context picked on the chips, if any. */
  emotion: string | null;
  context: string | null;
  /** Which segment was on: the first ("Current mood") or the second ("Overall today"). */
  scope: number;
};

type Props = {
  moods?: Mood[];
  /** What the second chip goes through, tap by tap. */
  contexts?: string[];
  question?: string;
  /** The chips before anything is picked on them. */
  emotionLabel?: string;
  contextLabel?: string;
  /** The two segments at the bottom. */
  scopes?: [string, string];
  /** Opens a mood, swipes to its neighbours, picks an emotion and closes, every LOOP seconds. */
  autoPlay?: boolean;
  /** Called when a mood opens or the swipe settles on another one. */
  onMoodChange?: (mood: Mood) => void;
  /** Called by the round button, before the capsule closes. */
  onSubmit?: (checkIn: CheckIn) => void;
  onSettings?: () => void;
  onHistory?: () => void;
};

const MOODS: Mood[] = [
  { name: 'Very unpleasant', light: '#8B66F2', base: '#6A3FD8', deep: '#4A27B4', tint: '#C9B6FF', ink: '#2A1670', glyph: 'spark', emotions: ['Angry', 'Anxious', 'Drained'] },
  { name: 'Unpleasant', light: '#4C84FF', base: '#2F63EE', deep: '#1C3FC2', tint: '#ABC6FF', ink: '#0F2A8A', glyph: 'bloom', emotions: ['Sad', 'Tired', 'Stressed'] },
  { name: 'Neutral', light: '#78D8E4', base: '#45BCCB', deep: '#2F99AB', tint: '#CAF7FD', ink: '#0E5560', glyph: 'dot', emotions: ['Calm', 'Content', 'Indifferent'] },
  { name: 'Pleasant', light: '#F3D83A', base: '#E6C417', deep: '#CDA70E', tint: '#FFF27E', ink: '#6B5600', glyph: 'petal', emotions: ['Happy', 'Grateful', 'Relaxed'] },
  { name: 'Very pleasant', light: '#FFA26A', base: '#F58A4B', deep: '#E3672A', tint: '#FFD6B6', ink: '#7A3210', glyph: 'clover', emotions: ['Joyful', 'Excited', 'Proud'] },
];
const CONTEXTS = ['Work', 'Family', 'Friends', 'Health'];

const BACKGROUND = '#0E0E10';
const MUTE = '#8E8E93';
const TILE = '#1C1C1E';
// The segment that is on, before a mood tints it.
const SEGMENT_ON = '#1F1F22';
const SEGMENT_INK = '#D6D6DA';
// A capsule that is not the open one: white at these strengths for its fill, its edge and its mark.
const GREY_FILL = 'rgba(255,255,255,0.1)';
const GREY_EDGE = 'rgba(255,255,255,0.13)';
const GREY_MARK = 'rgba(255,255,255,0.34)';
// A chip nothing is picked on.
const CHIP_FILL = 'rgba(255,255,255,0.2)';

// Sizes in points on a 402-point-wide screen; narrower screens scale everything down.
const DESIGN_WIDTH = 402;
// The row's centre, as a share of the screen's height.
const ROW_AT = 0.52;
// A capsule at rest, one beside the open capsule, and the open one.
const REST_WIDTH = 62;
const REST_HEIGHT = 103;
const REST_GAP = 9.5;
const SMALL_WIDTH = 42;
const SMALL_HEIGHT = 73;
const LARGE_WIDTH = 158;
const LARGE_HEIGHT = 306;
const OPEN_GAP = 10;
// The mark: its size in each of those, and how far above the centre it sits in the open capsule.
const MARK_REST = 27;
const MARK_SMALL = 17;
const MARK_LARGE = 31;
const MARK_Y = -95;
// Inside the open capsule, from its centre: two chips and the round button.
const CHIP_WIDTH = 116;
const CHIP_HEIGHT = 38;
const CHIP_RADIUS = 14;
const CHIP_Y = [-52, -11];
const CHIP_TEXT = 15;
const SEND_Y = 85;
const SEND_RADIUS = 34.5;
// The question and the mood's name, from the row's centre to the middle of the text.
const QUESTION_Y = -103;
const QUESTION_TEXT = 20;
const NAME_Y = -212;
const NAME_TEXT = 21;
const TEXT_WIDTH = 320;

const GLOW_BLUR = 16;
const BLOOM_BLUR = 34;
// How far text blurs as it goes, the open capsule's content as it comes, and a capsule's own
// edge while it is half way between two sizes.
const TEXT_BLUR = 8;
const CONTENT_BLUR = 12;
const SOFT_BLUR = 3;
// The haze behind the row: each resting capsule's own, and the open mood's over the whole screen.
const HAZE_REST = 120;
const HAZE_OPEN = 340;

const SEGMENTS_WIDTH = 289;
const SEGMENTS_HEIGHT = 54;
const SEGMENTS_INSET = 7;
const SEGMENT = (SEGMENTS_WIDTH - SEGMENTS_INSET * 2) / 2;

// Springs: a capsule swelling open (it overshoots a little), closing, and the swipe settling.
const OPEN_SPRING = { stiffness: 260, damping: 20, mass: 1 };
const CLOSE_SPRING = { stiffness: 260, damping: 28, mass: 1 };
const GLIDE_SPRING = { stiffness: 150, damping: 20, mass: 1 };
const THUMB_SPRING = { stiffness: 300, damping: 26, mass: 1 };
// Points of finger travel that carry the swipe from one mood to the next, how much of the pull
// past either end is followed, and how many seconds of the finger's speed are added on release.
const SWIPE = 120;
const RUBBER = 0.3;
const THROW = 0.15;
// A pressed capsule or button shrinks by this much.
const PRESS = 0.06;
const PRESS_IN = 120;
// A chip dips to this scale when it takes a new word, and springs back.
const CHIP_DIP = 0.9;
// The round button's arrow becomes a check over SENT_IN; SENT_HOLD after the tap the capsule closes.
const SENT_IN = 0.28;
const SENT_HOLD = 0.9;

/** autoPlay, in seconds into each loop: a tap on a capsule, swipes to other moods, a tap on the first chip, a tap outside. */
export const LOOP = 9;
const AUTO_MOOD = 3;
const TAP_AT = 0.8;
const SWIPES_AT = [
  [2.0, 2],
  [3.0, 3],
  [4.0, 4],
  [5.0, 3],
];
const CHIP_AT = 6.0;
const CLOSE_AT = 7.2;
const AUTO_PRESS = 0.12;
/** After a touch autoPlay waits this long, closes the capsule if it is open, and starts over. */
const USER_HOLD = 3;
const CLOSE_FOR = 0.7;

// What a touch landed on: a capsule by its index, or one of these.
const NOTHING = -1;
const CHIP = 10;
const SEND = 12;
const INSIDE = 20;

const FONT = Platform.select({ ios: ['.AppleSystemUIFont'], default: ['sans-serif'] });

/** A closed outline r(θ) = radius + wave·cos(lobes·θ) round the origin, one lobe pointing up. */
function wavy(lobes: number, radius: number, wave: number) {
  const points: string[] = [];
  for (let i = 0; i < 120; i++) {
    const angle = (i / 120) * Math.PI * 2;
    const r = radius + wave * Math.cos(lobes * angle);
    points.push(`${(r * Math.sin(angle)).toFixed(3)} ${(-r * Math.cos(angle)).toFixed(3)}`);
  }
  return Skia.Path.MakeFromSVGString(`M${points.join('L')}Z`)!;
}

/** A star with `points` tips at radius 1 and valleys at `inner`. */
function star(points: number, inner: number) {
  const corners: string[] = [];
  for (let i = 0; i < points * 2; i++) {
    const angle = (i / (points * 2)) * Math.PI * 2;
    const r = i % 2 === 0 ? 1 : inner;
    corners.push(`${(r * Math.sin(angle)).toFixed(3)} ${(-r * Math.cos(angle)).toFixed(3)}`);
  }
  return Skia.Path.MakeFromSVGString(`M${corners.join('L')}Z`)!;
}

// The marks, in a box of radius 1.
const GLYPHS: Record<Glyph, SkPath> = {
  spark: star(8, 0.66),
  bloom: wavy(8, 0.86, 0.12),
  dot: Skia.Path.Circle(0, 0, 0.8),
  petal: wavy(5, 0.8, 0.2),
  clover: wavy(6, 0.8, 0.18),
};
const ARROW = Skia.Path.MakeFromSVGString('M0 12L0 -11M-9.5 -1.5L0 -11L9.5 -1.5')!;
const CHECK = Skia.Path.MakeFromSVGString('M-11 1L-3.5 8.5L11.5 -7.5')!;

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

/**
 * The row for a swipe position `focus` (a mood's index, or between two) and `open` (0 at rest, 1
 * with a capsule open; it may overshoot). For each capsule: its centre's x from the middle of the
 * screen, its width, its height, and how much it is the open one (0 to 1).
 */
function rowAt(focus: number, open: number, count: number) {
  'worklet';
  const opened = clamp01(open);
  const gap = REST_GAP + (OPEN_GAP - REST_GAP) * opened;
  const row: number[] = [];
  let x = 0;
  for (let i = 0; i < count; i++) {
    const chosen = smooth(1 - Math.abs(i - focus), 0, 1);
    const width = REST_WIDTH + (SMALL_WIDTH + (LARGE_WIDTH - SMALL_WIDTH) * chosen - REST_WIDTH) * open;
    const height = REST_HEIGHT + (SMALL_HEIGHT + (LARGE_HEIGHT - SMALL_HEIGHT) * chosen - REST_HEIGHT) * open;
    row.push(x + width / 2, width, height, chosen);
    x += width + gap;
  }
  // At rest the whole row is centred; open, the capsule under `focus` is, and a pull past either
  // end drags the row a little further.
  const inside = Math.min(count - 1, Math.max(0, focus));
  const before = Math.floor(inside);
  const after = Math.min(count - 1, before + 1);
  const focused = row[before * 4] + (row[after * 4] - row[before * 4]) * (inside - before);
  const middle = (x - gap) / 2;
  const anchor = middle + (focused - middle + (focus - inside) * SWIPE) * opened;
  for (let i = 0; i < count; i++) row[i * 4] -= anchor;
  return row;
}

/** What is under a point given from the row's centre, with the row settled on `focus`, open or not. */
function hit(x: number, y: number, focus: number, open: number, count: number) {
  'worklet';
  const row = rowAt(focus, open, count);
  if (open === 1) {
    const dx = x - row[focus * 4];
    if (Math.hypot(dx, y - SEND_Y) < SEND_RADIUS + 6) return SEND;
    if (Math.abs(dx) < CHIP_WIDTH / 2 + 4) {
      if (y >= CHIP_Y[0] - 4 && y < CHIP_Y[0] + CHIP_HEIGHT + 1) return CHIP;
      if (y >= CHIP_Y[1] - 1 && y < CHIP_Y[1] + CHIP_HEIGHT + 4) return CHIP + 1;
    }
    if (Math.abs(dx) < LARGE_WIDTH / 2 && Math.abs(y) < LARGE_HEIGHT / 2) return INSIDE;
  }
  for (let i = 0; i < count; i++) {
    if (Math.abs(x - row[i * 4]) < row[i * 4 + 1] / 2 + 5 && Math.abs(y) < row[i * 4 + 2] / 2 + 8) return i;
  }
  return NOTHING;
}

function paragraph(text: string, size: number, weight: number, color: string, width: number) {
  const builder = Skia.ParagraphBuilder.Make({ textAlign: TextAlign.Center, maxLines: 1, ellipsis: '…' });
  builder.pushStyle({ color: Skia.Color(color), fontFamilies: FONT, fontSize: size, fontStyle: { weight }, letterSpacing: -0.2 });
  builder.addText(text);
  const built = builder.build();
  built.layout(width);
  return built;
}

/** A colour between two hex colours. */
function blend(from: string, to: string, amount: number) {
  const channel = (hex: string, at: number) => parseInt(hex.slice(at, at + 2), 16);
  const mix = (at: number) => Math.round(channel(from, at) + (channel(to, at) - channel(from, at)) * amount);
  return `rgb(${mix(1)},${mix(3)},${mix(5)})`;
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function tap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type LineProps = {
  text: SkParagraph;
  /** The middle of the line, from the row's centre. */
  y: number;
  /** 0 to 1: how much of it shows; the rest is blur. */
  weight: SharedValue<number>;
};

/** A line of text that comes sharp out of a blur and blurs away. */
function Line({ text, y, weight }: LineProps) {
  const sigma = useDerivedValue(() => TEXT_BLUR * (1 - weight.get()));
  const top = y - text.getHeight() / 2;
  return (
    <Group
      clip={rect(-TEXT_WIDTH / 2, top - 24, TEXT_WIDTH, text.getHeight() + 48)}
      layer={
        <Paint opacity={weight}>
          <Blur blur={sigma} />
        </Paint>
      }
    >
      <Paragraph paragraph={text} x={-TEXT_WIDTH / 2} y={top} width={TEXT_WIDTH} />
    </Group>
  );
}

type NameProps = { text: SkParagraph; index: number; row: SharedValue<number[]>; open: SharedValue<number> };

/** A mood's name above its open capsule. */
function Name({ text, index, row, open }: NameProps) {
  const weight = useDerivedValue(() => smooth(row.get()[index * 4 + 3], 0.5, 1) * smooth(open.get(), 0.6, 1));
  return <Line text={text} y={NAME_Y} weight={weight} />;
}

type HazeProps = {
  index: number;
  mood: Mood;
  row: SharedValue<number[]>;
  open: SharedValue<number>;
  /** The screen, in the row's own points, from its centre. */
  frame: { x: number; y: number; width: number; height: number };
};

/** The light a mood throws on the screen: a small glow round its resting capsule, a wide one once it is open. */
function Haze({ index, mood, row, open, frame }: HazeProps) {
  const rest = useDerivedValue(() => 0.2 * (1 - clamp01(open.get())));
  const wide = useDerivedValue(() => clamp01(open.get()) * row.get()[index * 4 + 3]);
  const centre = useDerivedValue(() => vec(row.get()[index * 4], 0));
  const hex = mood.base;
  return (
    <>
      <Rect x={frame.x} y={frame.y} width={frame.width} height={frame.height} opacity={rest}>
        <RadialGradient c={centre} r={HAZE_REST} colors={[`${hex}FF`, `${hex}55`, `${hex}00`]} positions={[0, 0.45, 1]} />
      </Rect>
      <Rect x={frame.x} y={frame.y} width={frame.width} height={frame.height} opacity={wide}>
        <RadialGradient c={vec(0, 0)} r={HAZE_OPEN} colors={[`${hex}70`, `${hex}38`, `${hex}16`, `${hex}0C`]} positions={[0, 0.35, 0.7, 1]} />
      </Rect>
    </>
  );
}

type CapsuleProps = {
  index: number;
  mood: Mood;
  row: SharedValue<number[]>;
  open: SharedValue<number>;
  /** What a finger is on, as `hit` gives it. */
  pressed: SharedValue<number>;
  /** 0 to 1: the open capsule's lighter edge, for the second segment. */
  outline: SharedValue<number>;
  /** 0 to 1: the round button's arrow has become a check. */
  sent: SharedValue<number>;
  /** 0 to 1 for each chip, running after a tap on it. */
  dips: SharedValue<number>[];
  chips: { text: SkParagraph; picked: boolean }[];
};

/**
 * One mood: a capsule in its colour at rest, a small grey one beside the open capsule, or the open
 * one itself, with its mark at the top, the two chips and the round button.
 */
function Capsule({ index, mood, row, open, pressed, outline, sent, dips, chips }: CapsuleProps) {
  const at = index * 4;
  const down = useDerivedValue(() => withTiming(pressed.get() === index ? 1 : 0, { duration: PRESS_IN }));
  const sendDown = useDerivedValue(() => withTiming(pressed.get() === SEND ? 1 : 0, { duration: PRESS_IN }));
  // How much colour it has, and how much it is the open capsule.
  const lit = useDerivedValue(() => 1 - clamp01(open.get()) * (1 - row.get()[at + 3]));
  const grey = useDerivedValue(() => 1 - lit.get());
  const chosen = useDerivedValue(() => clamp01(open.get()) * row.get()[at + 3]);

  const transform = useDerivedValue(() => [{ translateX: row.get()[at] }, { scale: 1 - PRESS * down.get() }]);
  const shape = useDerivedValue(() => {
    const width = row.get()[at + 1];
    const height = row.get()[at + 2];
    return rrect(rect(-width / 2, -height / 2, width, height), width / 2, width / 2);
  });
  const top = useDerivedValue(() => vec(0, -row.get()[at + 2] / 2));
  const bottom = useDerivedValue(() => vec(0, row.get()[at + 2] / 2));
  const glow = useDerivedValue(() => 0.5 * lit.get() * (1 - chosen.get()));
  const bloom = useDerivedValue(() => 0.62 * chosen.get());
  // Half way between two sizes its edge goes soft, like one blob handing over to the next.
  const soft = useDerivedValue(() => {
    const share = row.get()[at + 3];
    return SOFT_BLUR * 4 * share * (1 - share) * clamp01(open.get());
  });
  const edge = useDerivedValue(() => outline.get() * chosen.get());

  const inner = useDerivedValue(() => [{ scale: row.get()[at + 2] / LARGE_HEIGHT }]);
  const mark = useDerivedValue(() => {
    const opened = clamp01(open.get());
    const size = MARK_REST + (MARK_SMALL + (MARK_LARGE - MARK_SMALL) * row.get()[at + 3] - MARK_REST) * opened;
    return [{ translateY: (MARK_Y * chosen.get() * row.get()[at + 2]) / LARGE_HEIGHT }, { scale: size / 2 }];
  });
  const content = useDerivedValue(() => smooth(chosen.get(), 0.3, 0.9));
  const contentBlur = useDerivedValue(() => CONTENT_BLUR * (1 - smooth(chosen.get(), 0.5, 1)));
  const send = useDerivedValue(() => [{ translateY: SEND_Y }, { scale: 1 - PRESS * sendDown.get() }]);
  const arrow = useDerivedValue(() => 1 - smooth(sent.get(), 0, 0.5));
  const check = useDerivedValue(() => smooth(sent.get(), 0.4, 1));
  const firstChip = useDerivedValue(() => [{ scale: CHIP_DIP + (1 - CHIP_DIP) * dips[0].get() }]);
  const secondChip = useDerivedValue(() => [{ scale: CHIP_DIP + (1 - CHIP_DIP) * dips[1].get() }]);
  const chipScales = [firstChip, secondChip];

  return (
    <Group transform={transform}>
      <RoundedRect rect={shape} color={mood.base} opacity={bloom}>
        <BlurMask blur={BLOOM_BLUR} style="normal" />
      </RoundedRect>
      <RoundedRect rect={shape} color={mood.base} opacity={glow}>
        <BlurMask blur={GLOW_BLUR} style="normal" />
      </RoundedRect>

      <RoundedRect rect={shape} color={GREY_FILL} opacity={grey} />
      <RoundedRect rect={shape} style="stroke" strokeWidth={1} color={GREY_EDGE} opacity={grey} />
      <RoundedRect rect={shape} opacity={lit}>
        <LinearGradient start={top} end={bottom} colors={[mood.light, mood.base, mood.deep]} positions={[0, 0.5, 1]} />
        <BlurMask blur={soft} style="normal" />
      </RoundedRect>
      {/* A glassy edge: bright at the top, faint down the sides. */}
      <RoundedRect rect={shape} style="stroke" strokeWidth={1.5} opacity={lit}>
        <LinearGradient start={top} end={bottom} colors={['rgba(255,255,255,0.5)', 'rgba(255,255,255,0.06)', 'rgba(255,255,255,0.2)']} positions={[0, 0.5, 1]} />
        <BlurMask blur={soft} style="normal" />
      </RoundedRect>
      <RoundedRect rect={shape} style="stroke" strokeWidth={2} color={mood.tint} opacity={edge} />

      <Group transform={mark}>
        <Path path={GLYPHS[mood.glyph]} color={GREY_MARK} opacity={grey} />
        <Path path={GLYPHS[mood.glyph]} color={mood.tint} opacity={lit} />
      </Group>

      <Group transform={inner}>
        <Group
          clip={rect(-LARGE_WIDTH / 2, CHIP_Y[0] - 16, LARGE_WIDTH, SEND_Y + SEND_RADIUS - CHIP_Y[0] + 32)}
          layer={
            <Paint opacity={content}>
              <Blur blur={contentBlur} />
            </Paint>
          }
        >
          {chips.map((chip, which) => (
            <Group key={which} transform={chipScales[which]} origin={vec(0, CHIP_Y[which] + CHIP_HEIGHT / 2)}>
              <RoundedRect
                x={-CHIP_WIDTH / 2}
                y={CHIP_Y[which]}
                width={CHIP_WIDTH}
                height={CHIP_HEIGHT}
                r={CHIP_RADIUS}
                color={chip.picked ? mood.tint : CHIP_FILL}
              />
              <Paragraph
                paragraph={chip.text}
                x={-CHIP_WIDTH / 2 + 6}
                y={CHIP_Y[which] + (CHIP_HEIGHT - chip.text.getHeight()) / 2}
                width={CHIP_WIDTH - 12}
              />
            </Group>
          ))}
          <Group transform={send}>
            <Circle cx={0} cy={0} r={SEND_RADIUS} color={mood.tint} />
            <Path path={ARROW} style="stroke" strokeWidth={3.2} strokeCap="round" strokeJoin="round" color={mood.ink} opacity={arrow} />
            <Path path={CHECK} style="stroke" strokeWidth={3.4} strokeCap="round" strokeJoin="round" color={mood.ink} end={check} />
          </Group>
        </Group>
      </Group>
    </Group>
  );
}

/**
 * A mood check-in. Five capsules sit under a question; a tap makes one swell into a glowing
 * lozenge that holds the next fields (an emotion, a context, and the button that sends), while the
 * others turn grey, shrink and slide apart and the screen takes the mood's colour. A sideways
 * swipe hands the glow to the neighbouring mood; a tap outside closes it. Nothing is stored:
 * `onSubmit` receives the answer.
 */
export function MoodCapsuleCheckIn({
  moods = MOODS,
  contexts = CONTEXTS,
  question = 'How are you feeling?',
  emotionLabel = 'Emotions',
  contextLabel = 'Context',
  scopes = ['Current mood', 'Overall today'],
  autoPlay = false,
  onMoodChange,
  onSubmit,
  onSettings,
  onHistory,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  // The open mood's index, or null with the row at rest.
  const [selected, setSelected] = useState<number | null>(null);
  // What the chips hold, as indexes into the mood's emotions and into `contexts`.
  const [emotion, setEmotion] = useState<number | null>(null);
  const [context, setContext] = useState<number | null>(null);
  const [scope, setScope] = useState(0);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const count = moods.length;
  // 0 at rest, 1 with a capsule open; the spring carries it a little past 1.
  const open = useSharedValue(0);
  // The swipe: a mood's index, or between two while a finger drags or the spring settles.
  const focus = useSharedValue(Math.min(AUTO_MOOD, count - 1));
  // Where the row is settling, for the gestures: open or not, and on which mood.
  const opened = useSharedValue(0);
  const aim = useSharedValue(Math.min(AUTO_MOOD, count - 1));
  const dragFrom = useSharedValue(0);
  const pressed = useSharedValue(NOTHING);
  const outline = useSharedValue(0);
  const thumb = useSharedValue(0);
  const sent = useSharedValue(0);
  const firstDip = useSharedValue(1);
  const secondDip = useSharedValue(1);
  // Reduced motion: the stage dips and comes back while the row jumps.
  const veil = useSharedValue(1);

  const scale = Math.min(1, size.width / DESIGN_WIDTH);
  const cx = size.width / 2;
  const cy = size.height * ROW_AT;
  const frame = { x: -cx / scale, y: -cy / scale, width: size.width / scale, height: size.height / scale };

  const row = useDerivedValue(() => rowAt(focus.get(), open.get(), count));

  const texts = useMemo(
    () => ({
      question: paragraph(question, QUESTION_TEXT, 400, MUTE, TEXT_WIDTH),
      names: moods.map((mood) => paragraph(mood.name, NAME_TEXT, 400, blend(mood.tint, mood.base, 0.3), TEXT_WIDTH)),
    }),
    [question, moods],
  );

  // The chips of every mood: only the open one's show, but the swipe passes through the others.
  const chips = useMemo(
    () =>
      moods.map((mood, index) => {
        const own = index === selected;
        const first = own && emotion !== null ? mood.emotions[emotion] : emotionLabel;
        const second = context !== null ? contexts[context] : contextLabel;
        // An empty chip is pale text on a translucent fill; a picked one is solid, like the button.
        const chip = (text: string, picked: boolean) => ({
          text: paragraph(text, CHIP_TEXT, 500, picked ? mood.ink : blend(mood.tint, '#FFFFFF', 0.4), CHIP_WIDTH - 12),
          picked,
        });
        return [chip(first, own && emotion !== null), chip(second, context !== null)];
      }),
    [moods, contexts, selected, emotion, context, emotionLabel, contextLabel],
  );

  /** Moves a value on a spring, or at once behind a short fade under reduced motion. */
  const move = (value: SharedValue<number>, to: number, spring: typeof OPEN_SPRING, then?: () => void) => {
    if (reducedMotion) {
      value.set(to);
      veil.set(0.2);
      veil.set(withTiming(1, { duration: 260 }));
      then?.();
      return;
    }
    value.set(
      withSpring(to, spring, (finished) => {
        if (finished && then) scheduleOnRN(then);
      }),
    );
  };

  const forget = () => {
    setEmotion(null);
    setContext(null);
    cancelAnimation(sent);
    sent.set(0);
  };

  /** Opens a mood from the resting row. */
  const choose = (index: number, auto: boolean) => {
    if (!auto) tap();
    cancelAnimation(focus);
    focus.set(index);
    aim.set(index);
    opened.set(1);
    setSelected(index);
    onMoodChange?.(moods[index]);
    move(open, 1, OPEN_SPRING);
  };

  /** Hands the open capsule to another mood: the end of a swipe, or a tap on a small capsule. */
  const glide = (index: number, auto: boolean) => {
    if (index !== selected) {
      if (!auto) tick();
      setEmotion(null);
      setSelected(index);
      onMoodChange?.(moods[index]);
    }
    aim.set(index);
    move(focus, index, GLIDE_SPRING);
  };

  /** Closes the open capsule and drops what was picked in it. */
  const close = (auto: boolean) => {
    if (selected === null) return;
    if (!auto) tap();
    opened.set(0);
    setSelected(null);
    move(open, 0, CLOSE_SPRING, forget);
  };

  /** A tap on a chip moves it to its next word; after the last one it is empty again. */
  const pick = (which: number, auto: boolean) => {
    if (selected === null) return;
    if (!auto) tap();
    const next = (value: number | null, length: number) => (value === null ? 0 : value + 1 < length ? value + 1 : null);
    if (which === 0) setEmotion((value) => next(value, moods[selected].emotions.length));
    else setContext((value) => next(value, contexts.length));
    const dip = which === 0 ? firstDip : secondDip;
    dip.set(0);
    dip.set(reducedMotion ? withTiming(1, { duration: 200 }) : withSpring(1, { stiffness: 420, damping: 18, mass: 1 }));
  };

  const submit = () => {
    if (selected === null) return;
    if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    onSubmit?.({
      mood: moods[selected],
      emotion: emotion === null ? null : moods[selected].emotions[emotion],
      context: context === null ? null : contexts[context],
      scope,
    });
    // The check is drawn, holds, and the capsule closes.
    sent.set(
      withSequence(
        withTiming(1, { duration: SENT_IN * 1000, reduceMotion: ReduceMotion.Never }),
        withDelay(
          (SENT_HOLD - SENT_IN) * 1000,
          withTiming(1, { duration: 1, reduceMotion: ReduceMotion.Never }, (finished) => {
            if (finished) scheduleOnRN(close, true);
          }),
          ReduceMotion.Never,
        ),
      ),
    );
  };

  const pickScope = (index: number) => {
    touched();
    tick();
    setScope(index);
    thumb.set(reducedMotion ? index : withSpring(index, THUMB_SPRING));
    outline.set(withTiming(index, { duration: 220 }));
  };

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  /** A tap, by what `hit` found under it. */
  const act = (target: number, auto: boolean) => {
    if (selected === null) {
      if (target >= 0 && target < count) choose(target, auto);
    } else if (target === SEND) submit();
    else if (target === CHIP || target === CHIP + 1) pick(target - CHIP, auto);
    else if (target === NOTHING) close(auto);
    else if (target < count && target !== selected) glide(target, auto);
  };

  // The loop and the timers read the latest state when they fire.
  const latest = useRef({ act, glide, close, selected, scope, pickScope });
  useEffect(() => {
    latest.current = { act, glide, close, selected, scope, pickScope };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const queue: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => queue.push(setTimeout(run, ms));
    // A press by the loop: the target dips as under a finger, then the tap lands.
    const press = (origin: number, at: number, target: number) => {
      after(origin + at * 1000 - Date.now(), () => pressed.set(target));
      after(origin + (at + AUTO_PRESS) * 1000 - Date.now(), () => {
        pressed.set(NOTHING);
        latest.current.act(target, true);
      });
    };
    const cycle = (origin: number) => {
      press(origin, TAP_AT, Math.min(AUTO_MOOD, count - 1));
      SWIPES_AT.forEach(([at, mood]) => after(origin + at * 1000 - Date.now(), () => latest.current.glide(Math.min(mood, count - 1), true)));
      press(origin, CHIP_AT, CHIP);
      after(origin + CLOSE_AT * 1000 - Date.now(), () => latest.current.close(true));
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    const resume = () => {
      // The loop starts from the resting row, on the first segment.
      if (latest.current.scope !== 0) latest.current.pickScope(0);
      if (latest.current.selected !== null) {
        latest.current.close(true);
        return after(CLOSE_FOR * 1000, resume);
      }
      cycle(Date.now());
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, resume);
    return () => queue.forEach(clearTimeout);
  }, [autoPlay, touches, count, pressed]);

  const tapGesture = Gesture.Tap()
    .maxDistance(12)
    .onBegin((event) => {
      pressed.set(hit((event.x - cx) / scale, (event.y - cy) / scale, aim.get(), opened.get(), count));
      scheduleOnRN(touched);
    })
    .onEnd((event, success) => {
      if (success) scheduleOnRN(act, hit((event.x - cx) / scale, (event.y - cy) / scale, aim.get(), opened.get(), count), false);
    })
    .onFinalize(() => {
      pressed.set(NOTHING);
    });

  const panGesture = Gesture.Pan()
    .activeOffsetX([-10, 10])
    .failOffsetY([-18, 18])
    .onStart(() => {
      dragFrom.set(focus.get());
      scheduleOnRN(touched);
    })
    .onUpdate((event) => {
      if (opened.get() === 0) return;
      const raw = dragFrom.get() - event.translationX / (SWIPE * scale);
      const last = count - 1;
      focus.set(raw < 0 ? raw * RUBBER : raw > last ? last + (raw - last) * RUBBER : raw);
    })
    .onEnd((event) => {
      if (opened.get() === 0) return;
      // The finger's speed carries the swipe on, by one mood at most.
      const thrown = Math.round(focus.get() - (event.velocityX * THROW) / (SWIPE * scale));
      const next = Math.min(Math.min(count - 1, aim.get() + 1), Math.max(Math.max(0, aim.get() - 1), thrown));
      scheduleOnRN(glide, next, false);
    });

  const questionWeight = useDerivedValue(() => 1 - smooth(open.get(), 0, 0.5));
  const stageStyle = useAnimatedStyle(() => ({ opacity: veil.get() }));
  const tileStyle = useAnimatedStyle(() => ({
    opacity: 1 - smooth(open.get(), 0, 0.5),
    transform: [{ scale: 1 - 0.08 * clamp01(open.get()) }],
  }));
  // The segment that is on takes the open mood's colour.
  const moodIndexes = moods.map((_, index) => index);
  const tints = moods.map((mood) => mood.tint);
  const bases = moods.map((mood) => mood.base);
  const thumbStyle = useAnimatedStyle(() => {
    const at = Math.min(count - 1, Math.max(0, focus.get()));
    const well = interpolateColor(0.2, [0, 1], [SEGMENT_ON, interpolateColor(at, moodIndexes, bases)]);
    return {
      backgroundColor: interpolateColor(clamp01(open.get()), [0, 1], [SEGMENT_ON, well]),
      transform: [{ translateX: thumb.get() * SEGMENT }],
    };
  });
  const onInkStyle = useAnimatedStyle(() => {
    const at = Math.min(count - 1, Math.max(0, focus.get()));
    return { color: interpolateColor(clamp01(open.get()), [0, 1], [SEGMENT_INK, interpolateColor(at, moodIndexes, tints)]) };
  });

  const onLayout = (event: LayoutChangeEvent) => {
    setLaidOut({ width: event.nativeEvent.layout.width, height: event.nativeEvent.layout.height });
  };

  const dips = [firstDip, secondDip];
  const openMood = selected === null ? null : moods[selected];

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />

      <Animated.View style={[StyleSheet.absoluteFill, stageStyle]}>
        <Canvas style={StyleSheet.absoluteFill}>
          <Group transform={[{ translateX: cx }, { translateY: cy }, { scale }]}>
            {moods.map((mood, index) => (
              <Haze key={index} index={index} mood={mood} row={row} open={open} frame={frame} />
            ))}
            {moods.map((mood, index) => (
              <Capsule
                key={index}
                index={index}
                mood={mood}
                row={row}
                open={open}
                pressed={pressed}
                outline={outline}
                sent={sent}
                dips={dips}
                chips={chips[index]}
              />
            ))}
            <Line text={texts.question} y={QUESTION_Y} weight={questionWeight} />
            {texts.names.map((text, index) => (
              <Name key={index} text={text} index={index} row={row} open={open} />
            ))}
          </Group>
        </Canvas>
      </Animated.View>

      <GestureDetector gesture={Gesture.Race(panGesture, tapGesture)}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={question}
          accessibilityValue={{ text: openMood ? openMood.name : undefined }}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }, { name: 'activate' }, { name: 'escape' }]}
          onAccessibilityAction={(event) => {
            const action = event.nativeEvent.actionName;
            const current = selected ?? aim.get();
            if (action === 'escape') close(false);
            else if (action === 'activate') {
              if (selected === null) choose(current, false);
              else submit();
            } else {
              const next = Math.min(count - 1, Math.max(0, current + (action === 'increment' ? 1 : -1)));
              if (selected === null) choose(next, false);
              else glide(next, false);
            }
          }}
        />
      </GestureDetector>

      <Animated.View pointerEvents={selected === null ? 'auto' : 'none'} style={[styles.tile, { top: insets.top + 4 }, tileStyle]}>
        <Pressable
          style={styles.tileButton}
          accessibilityRole="button"
          accessibilityLabel="Settings"
          onPress={() => {
            touched();
            onSettings?.();
          }}
        >
          <Ionicons name="settings-sharp" size={22} color={MUTE} />
        </Pressable>
        <Pressable
          style={styles.tileButton}
          accessibilityRole="button"
          accessibilityLabel="History"
          onPress={() => {
            touched();
            onHistory?.();
          }}
        >
          <MaterialCommunityIcons name="chart-line" size={23} color={MUTE} />
        </Pressable>
      </Animated.View>

      <View style={[styles.segments, { bottom: Math.max(insets.bottom, 16) + 24 }]} accessibilityRole="tablist">
        <Animated.View style={[styles.thumb, thumbStyle]} />
        {scopes.map((label, index) => (
          <Pressable
            key={label}
            style={styles.segment}
            accessibilityRole="tab"
            accessibilityState={{ selected: scope === index }}
            onPress={() => pickScope(index)}
          >
            <Animated.Text style={[styles.segmentLabel, scope === index && styles.segmentOn, scope === index && onInkStyle]} numberOfLines={1}>
              {label}
            </Animated.Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
  },
  tile: {
    position: 'absolute',
    right: 27,
    width: 48,
    height: 94,
    borderRadius: 24,
    backgroundColor: TILE,
    paddingVertical: 4,
  },
  tileButton: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  segments: {
    position: 'absolute',
    alignSelf: 'center',
    width: SEGMENTS_WIDTH,
    height: SEGMENTS_HEIGHT,
    borderRadius: SEGMENTS_HEIGHT / 2,
    padding: SEGMENTS_INSET,
    flexDirection: 'row',
    backgroundColor: 'rgba(255,255,255,0.035)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.08)',
  },
  thumb: {
    position: 'absolute',
    left: SEGMENTS_INSET,
    top: SEGMENTS_INSET,
    width: SEGMENT,
    height: SEGMENTS_HEIGHT - SEGMENTS_INSET * 2,
    borderRadius: (SEGMENTS_HEIGHT - SEGMENTS_INSET * 2) / 2,
  },
  segment: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  segmentLabel: {
    color: MUTE,
    fontSize: 16,
    lineHeight: 20,
  },
  segmentOn: {
    fontWeight: '500',
  },
});
