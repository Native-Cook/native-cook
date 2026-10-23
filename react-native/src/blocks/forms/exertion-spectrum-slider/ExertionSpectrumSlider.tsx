import { Ionicons } from '@expo/vector-icons';
import {
  Blur,
  BlurMask,
  Canvas,
  Circle,
  Group,
  Line,
  LinearGradient,
  Paint,
  Paragraph,
  Path,
  Rect,
  RoundedRect,
  Skia,
  TextAlign,
  interpolatePaths,
  rect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  FadeIn,
  FadeOut,
  ReduceMotion,
  type SharedValue,
  type WithSpringConfig,
  cancelAnimation,
  interpolateColor,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export type Level = {
  /** The name under the numeral. */
  name: string;
  /** One or two lines under the name. */
  description: string;
};

export type Stop = {
  /** Where along the track, 0 to 1. */
  at: number;
  color: string;
};

type Props = {
  /** The level the screen opens on, counted from 1; "Clear Entry" comes back to it. */
  value?: number;
  /** One entry per level, the lightest first. The track is cut into as many equal parts. */
  levels?: Level[];
  /** The first and last level of the usual range. The track is hatched outside it. */
  usualRange?: [number, number];
  /** The colours along the track. The thumb and Save take the colour under the thumb. */
  spectrum?: Stop[];
  /** Drags the thumb up and back by itself every LOOP seconds. */
  autoPlay?: boolean;
  title?: string;
  subtitle?: string;
  /** The caption over the track's left end while the thumb is in the usual range. */
  basisLabel?: string;
  rangeLabel?: string;
  /** The tags over the thumb outside the usual range, and their colours. */
  aboveLabel?: string;
  belowLabel?: string;
  aboveColor?: string;
  belowColor?: string;
  clearLabel?: string;
  helpLabel?: string;
  saveLabel?: string;
  /** Save's second line outside the usual range. */
  aboveNote?: string;
  belowNote?: string;
  /** Called with the new level every time the thumb enters another part of the track. */
  onChange?: (value: number) => void;
  onSave?: (value: number) => void;
  onClear?: () => void;
  onHelp?: () => void;
};

const LEVELS: Level[] = [
  { name: 'Very, Very Light', description: 'Very easy activity, like slowly walking' },
  { name: 'Very Light', description: 'Easy activity, like a casual walk' },
  { name: 'Light', description: 'Light effort, breathing slightly harder than normal' },
  { name: 'Moderate', description: 'Moderate effort, breathing\nheavier and sweating lightly' },
  { name: 'Somewhat Hard', description: 'Starting to feel challenging,\ncan still talk in sentences' },
  { name: 'Hard', description: 'Challenging effort, hard to speak in full sentences' },
  { name: 'Very Hard', description: 'Very challenging, can only\nspeak a few words at a time' },
  { name: 'Very, Very Hard', description: 'Extremely challenging, can barely speak' },
  { name: 'Extremely Hard', description: 'Close to your limit, impossible\nto keep up for long' },
  { name: 'Maximal', description: 'All-out effort, nothing left to give' },
];

// Teal through sky blue, yellow, orange, red and magenta to purple; the levels' own colours are the
// ones in the middle of each tenth of the track.
const SPECTRUM: Stop[] = [
  { at: 0, color: '#1fb8a6' },
  { at: 0.06, color: '#19c3f0' },
  { at: 0.15, color: '#12b5f5' },
  { at: 0.205, color: '#5fd080' },
  { at: 0.25, color: '#f5e500' },
  { at: 0.35, color: '#ff9a00' },
  { at: 0.45, color: '#ff7a00' },
  { at: 0.55, color: '#f0142a' },
  { at: 0.65, color: '#ee1048' },
  { at: 0.75, color: '#d800d8' },
  { at: 0.85, color: '#9a10c8' },
  { at: 1, color: '#6a0dad' },
];

const PAGE = '#0b0b0c';
const SHEET = 'rgba(28,28,30,0.82)';
const INK = '#ffffff';
const NUMERAL = '#f5f5f5';
const MUTE = '#9a9aa0';
const CAPTION = '#6f6f75';
const DIVIDER = 'rgba(255,255,255,0.09)';
const ABOVE = '#f0142a';
const BELOW = '#19c3f0';
// What shows through the sheet: a few bars and blots of the page behind it, as x, y, width, height
// in fractions of the screen, and a colour.
const SMUDGES: [number, number, number, number, string][] = [
  [0.04, 0.22, 0.26, 0.022, '#56565c'],
  [0.04, 0.36, 0.15, 0.022, '#4a4a50'],
  [0.5, 0.25, 0.24, 0.02, '#3c3c42'],
  [0.3, 0.672, 0.05, 0.022, '#8b1fb4'],
  [0.31, 0.722, 0.12, 0.026, '#d0202c'],
  [0.31, 0.775, 0.19, 0.026, '#d6c400'],
  [0.03, 0.674, 0.2, 0.014, '#3c3c42'],
  [0.03, 0.726, 0.24, 0.014, '#3c3c42'],
  [0.72, 0.726, 0.2, 0.014, '#3c3c42'],
];
const SMUDGE_BLUR = 9;

// Sizes in points, for a 402 × 874 screen.
const SHEET_TOP = 8;
const SHEET_RADIUS = 30;
const MARGIN = 28;
const NUMERAL_SIZE = 112;
// The numeral's place in the layout, and the taller canvas it is drawn on so a digit can roll out of it.
const NUMERAL_SLOT = 118;
const NUMERAL_CANVAS = 300;
const NUMERAL_WIDTH = 240;
const TRACK_MARGIN = 42;
const SLIDER = 116;
// The track is this thick at rest. Touched, it stays so at the left end and grows to WIDE at the right.
const THIN = 9;
const WIDE = 54;
const WIDE_CORNER = 5;
// The hatched band behind the track.
const BAND = 70;
const HATCH_STEP = 8;
const HATCH_WIDTH = 2.2;
const HATCH_ALPHA = 0.1;
// The lit hatching between the usual range and the thumb, while the track is at rest.
const HATCH_REST = 0.45;
// The parts of the spectrum the thumb has not reached are dimmed by this much black.
const SHADE = 0.46;
const THUMB = 28;
const THUMB_GROWN = 30;
// The dot of colour in the thumb: small at rest, a 3 pt ring away from the edge when touched.
const DOT = 11;
const DOT_GROWN = 24;
const TAG_WIDTH = 132;
const TAG_HEIGHT = 18;
const TAG_EDGE = 12;
const SAVE = 56;
const SAVE_RADIUS = 20;

// Motion.
const FOLLOW: WithSpringConfig = { mass: 1, stiffness: 520, damping: 44 };
const SWELL: WithSpringConfig = { mass: 1, stiffness: 300, damping: 21 };
const POP: WithSpringConfig = { mass: 1, stiffness: 420, damping: 22 };
// The numeral rolls one level in this many ms; a digit travels ROLL_STEP points and is blurred the
// most half way, mostly along the roll.
const ROLL = 240;
const ROLL_STEP = 86;
const ROLL_BLUR_X = 2.5;
const ROLL_BLUR_Y = 13;
const TEXT_FADE = 160;
const TONE = 260;
const NOTE = 200;
// Save's label moves up by this much to make room for its second line.
const NOTE_SHIFT = 8;

/** autoPlay, in seconds from the start of a loop: touch, drag up to PEAK, back to SETTLE, let go, clear. */
const LOOP = 9.4;
const DOWN_AT = 0.8;
const UP_FROM = 1.15;
const UP_FOR = 3.1;
const PEAK = 8;
const BACK_FROM = 4.7;
const BACK_FOR = 1.5;
const SETTLE = 4;
const LIFT_AT = 6.6;
const CLEAR_AT = 7.4;
const PRESS_FOR = 0.15;
/** After a touch autoPlay waits this long before it starts over from rest. */
const USER_HOLD = 3;
// Clear Entry to the thumb at rest again, in seconds.
const RETURN = 0.8;

const FONT = Platform.select({ ios: ['.AppleSystemUIFont'], default: ['sans-serif'] });

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

/** A finger's way from one place to another: steady, with a soft start and stop. */
function glide(u: number) {
  'worklet';
  return 0.55 * clamp01(u) + 0.45 * smooth(u, 0, 1);
}

function channels(hex: string) {
  const value = parseInt(hex.slice(1), 16);
  return [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff];
}

/** The spectrum's colour at `p` along the track, from its stops' places and red, green, blue. */
function colorAt(p: number, ats: number[], rgb: number[][]) {
  'worklet';
  let i = 1;
  while (i < ats.length - 1 && p > ats[i]) i++;
  const t = clamp01((p - ats[i - 1]) / (ats[i] - ats[i - 1]));
  const a = rgb[i - 1];
  const b = rgb[i];
  return `rgb(${Math.round(a[0] + (b[0] - a[0]) * t)},${Math.round(a[1] + (b[1] - a[1]) * t)},${Math.round(a[2] + (b[2] - a[2]) * t)})`;
}

/**
 * The track between x0 and x1 round the height cy: `left` thick at its round left end, `right` at
 * its right end, whose corners are cut by `corner`. Always the same commands, so that two of them
 * can be blended.
 */
function trackPath(x0: number, x1: number, cy: number, left: number, right: number, corner: number) {
  const a = left / 2;
  const b = right / 2;
  // A quarter circle as one curve.
  const k = 0.5523;
  return Skia.PathBuilder.Make()
    .moveTo(x0 + a, cy - a)
    .lineTo(x1 - corner, cy - b)
    .cubicTo(x1 - corner + corner * k, cy - b, x1, cy - b + corner - corner * k, x1, cy - b + corner)
    .lineTo(x1, cy + b - corner)
    .cubicTo(x1, cy + b - corner + corner * k, x1 - corner + corner * k, cy + b, x1 - corner, cy + b)
    .lineTo(x0 + a, cy + a)
    .cubicTo(x0 + a - a * k, cy + a, x0, cy + a * k, x0, cy)
    .cubicTo(x0, cy - a * k, x0 + a - a * k, cy - a, x0 + a, cy - a)
    .close()
    .build();
}

/** Diagonal lines over the whole band, to be clipped to the parts that are hatched. */
function hatchPath(x0: number, x1: number, cy: number) {
  const builder = Skia.PathBuilder.Make();
  for (let x = x0 - BAND; x < x1; x += HATCH_STEP) builder.moveTo(x, cy + BAND / 2).lineTo(x + BAND, cy - BAND / 2);
  return builder.build();
}

function digit(text: string) {
  const builder = Skia.ParagraphBuilder.Make({ textAlign: TextAlign.Center, maxLines: 1 });
  builder.pushStyle({ color: Skia.Color(NUMERAL), fontFamilies: FONT, fontSize: NUMERAL_SIZE, fontStyle: { weight: 700 }, letterSpacing: -2 });
  builder.addText(text);
  const built = builder.build();
  built.layout(NUMERAL_WIDTH);
  return built;
}

function haptic(kind: 'step' | 'press') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'step') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type DigitProps = { value: number; roll: SharedValue<number>; still: boolean };

/** One numeral of the roll: in place and solid when the roll is at its value, a step away and gone at the next. */
function Digit({ value, roll, still }: DigitProps) {
  const paragraph = useMemo(() => digit(String(value)), [value]);
  const opacity = useDerivedValue(() => Math.max(0, 1 - Math.abs(value - roll.get())));
  // A higher value comes up from below.
  const transform = useDerivedValue(() => [{ translateY: still ? 0 : (value - roll.get()) * ROLL_STEP }]);
  return (
    <Group transform={transform} layer={<Paint opacity={opacity} />}>
      <Paragraph paragraph={paragraph} x={0} y={(NUMERAL_CANVAS - paragraph.getHeight()) / 2} width={NUMERAL_WIDTH} />
    </Group>
  );
}

type NumeralProps = { count: number; roll: SharedValue<number>; still: boolean };

/** The big numeral. Between two levels it rolls like a counter wheel, smeared along the roll. */
function Numeral({ count, roll, still }: NumeralProps) {
  const blur = useDerivedValue(() => {
    const between = still ? 0 : Math.sin(Math.PI * (roll.get() - Math.floor(roll.get())));
    return { x: ROLL_BLUR_X * between, y: ROLL_BLUR_Y * between };
  });
  return (
    <Canvas style={styles.numeralCanvas}>
      <Group
        layer={
          <Paint>
            <Blur blur={blur} />
          </Paint>
        }
      >
        {Array.from({ length: count }, (_, index) => (
          <Digit key={index} value={index + 1} roll={roll} still={still} />
        ))}
      </Group>
    </Canvas>
  );
}

type TickProps = { x: number; cy: number; share: number; swell: SharedValue<number> };

/** A mark between two levels: a dot on the thin track, a short line on the swollen one. */
function Tick({ x, cy, share, swell }: TickProps) {
  const height = useDerivedValue(() => 2 + Math.max(0, swell.get()) * (0.34 * (THIN + (WIDE - THIN) * share) - 2));
  const y = useDerivedValue(() => cy - height.get() / 2);
  return <RoundedRect x={x - 1} y={y} width={2} height={height} r={1} color="rgba(255,255,255,0.3)" />;
}

type TagProps = { label: string; color: string; shown: SharedValue<number>; x: SharedValue<number>; still: boolean };

/** The tag over the thumb while it is outside the usual range. */
function Tag({ label, color, shown, x, still }: TagProps) {
  const style = useAnimatedStyle(() => ({
    opacity: clamp01(shown.get() * 2),
    transform: [{ translateX: x.get() }, { translateY: still ? 0 : (1 - shown.get()) * 6 }, { scale: still ? 1 : 0.7 + 0.3 * shown.get() }],
  }));
  return (
    <Animated.View pointerEvents="none" style={[styles.tag, { backgroundColor: color }, style]}>
      <Text style={styles.tagLabel} numberOfLines={1}>
        {label}
      </Text>
    </Animated.View>
  );
}

/**
 * A rating screen for how hard a workout felt: a big numeral with its name and description, and a
 * slider over a spectrum. Touching the slider swells its thin track into a wedge; dragging rolls the
 * numeral with a blur at every level, lights the spectrum from the usual range to the thumb, and
 * gives Save the colour under the thumb.
 */
export function ExertionSpectrumSlider({
  value = 2,
  levels = LEVELS,
  usualRange = [2, 4],
  spectrum = SPECTRUM,
  autoPlay = false,
  title = 'How hard was it?',
  subtitle = 'Rate the effort of this workout',
  basisLabel = 'Based on your heart rate',
  rangeLabel = 'Usual range',
  aboveLabel = 'Above usual range',
  belowLabel = 'Below usual range',
  aboveColor = ABOVE,
  belowColor = BELOW,
  clearLabel = 'Clear Entry',
  helpLabel = 'How to Self-Assess',
  saveLabel = 'Save',
  aboveNote = 'Effort Above Your Usual Range',
  belowNote = 'Effort Below Your Usual Range',
  onChange,
  onSave,
  onClear,
  onHelp,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width, height } = laidOut ?? window;
  const count = levels.length;
  const opening = Math.min(count, Math.max(1, Math.round(value)));
  const [current, setCurrent] = useState(opening);
  // -1 below the usual range, 1 above it, 0 inside; and the last one that was not 0, for Save's second line as it fades.
  const [side, setSide] = useState(0);
  const [lastSide, setLastSide] = useState(1);
  // Clear Entry held down by the autoPlay loop.
  const [autoPressed, setAutoPressed] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const x0 = TRACK_MARGIN;
  const x1 = width - TRACK_MARGIN;
  const span = x1 - x0;
  const cy = SLIDER / 2;
  // A level's place on the track is the middle of its part.
  const home = (opening - 0.5) / count;
  const rangeFrom = (usualRange[0] - 1) / count;
  const rangeTo = usualRange[1] / count;

  // Where the thumb is drawn to along the track, 0 to 1, and where it is: a short way behind.
  const target = useSharedValue(home);
  const place = useDerivedValue(() => withSpring(target.get(), FOLLOW));
  // 1 while a finger, or the loop's, is on the slider.
  const touching = useSharedValue(0);
  const swell = useDerivedValue(() => withSpring(touching.get(), SWELL));
  // The gap between the finger and the thumb's middle when it was caught.
  const grip = useSharedValue(0);
  // A finger is doing this, not the autoPlay loop: haptics are for fingers.
  const byHand = useSharedValue(false);
  const holding = useSharedValue(false);
  // Seconds into the autoPlay loop; negative while it is off.
  const clock = useSharedValue(-1);

  const level = useDerivedValue(() => Math.min(count, Math.floor(clamp01(place.get()) * count) + 1));
  const where = useDerivedValue(() => (place.get() < rangeFrom ? -1 : place.get() > rangeTo ? 1 : 0));
  const roll = useDerivedValue(() =>
    withTiming(level.get(), { duration: ROLL, easing: Easing.out(Easing.cubic), reduceMotion: ReduceMotion.Never }),
  );
  // Save's colour travels along the spectrum from one level's to the next.
  const tone = useDerivedValue(() => withTiming(level.get(), { duration: TONE, reduceMotion: ReduceMotion.Never }));
  // The tags pop in over the thumb; under reduced motion they fade.
  const above = useDerivedValue<number>(() => {
    const to = where.get() === 1 ? 1 : 0;
    return reducedMotion ? withTiming(to, { duration: NOTE, reduceMotion: ReduceMotion.Never }) : withSpring(to, POP);
  });
  const below = useDerivedValue<number>(() => {
    const to = where.get() === -1 ? 1 : 0;
    return reducedMotion ? withTiming(to, { duration: NOTE, reduceMotion: ReduceMotion.Never }) : withSpring(to, POP);
  });
  const outside = useDerivedValue(() => withTiming(where.get() === 0 ? 0 : 1, { duration: NOTE, reduceMotion: ReduceMotion.Never }));

  const ats = useMemo(() => spectrum.map((stop) => stop.at), [spectrum]);
  const rgb = useMemo(() => spectrum.map((stop) => channels(stop.color)), [spectrum]);
  const colors = useMemo(() => spectrum.map((stop) => stop.color), [spectrum]);
  // Each level's own colour: the spectrum in the middle of its part of the track.
  const tones = useMemo(() => levels.map((_, index) => colorAt((index + 0.5) / levels.length, ats, rgb)), [levels, ats, rgb]);
  const steps = useMemo(() => levels.map((_, index) => index + 1), [levels]);

  const thin = useMemo(() => trackPath(x0, x1, cy, THIN, THIN, THIN / 2), [x0, x1, cy]);
  const wide = useMemo(() => trackPath(x0, x1, cy, THIN, WIDE, WIDE_CORNER), [x0, x1, cy]);
  const hatch = useMemo(() => hatchPath(x0, x1, cy), [x0, x1, cy]);
  const track = useDerivedValue(() => interpolatePaths(swell.get(), [0, 1], [thin, wide]));
  const thumbX = useDerivedValue(() => x0 + clamp01(place.get()) * span);
  const thumbColor = useDerivedValue(() => colorAt(place.get(), ats, rgb));
  const thumbRadius = useDerivedValue(() => (THUMB + (THUMB_GROWN - THUMB) * swell.get()) / 2);
  const dotRadius = useDerivedValue(() => (DOT + (DOT_GROWN - DOT) * swell.get()) / 2);
  // The spectrum is lit from the usual range to the thumb, whichever side it is on, and shaded beyond.
  const shadeLeft = useDerivedValue(() => rect(x0, 0, Math.min(place.get(), rangeFrom) * span, SLIDER));
  const shadeRight = useDerivedValue(() => {
    const from = x0 + Math.max(place.get(), rangeTo) * span;
    return rect(from, 0, Math.max(0, x1 - from), SLIDER);
  });
  // The hatching between the usual range and a thumb outside it takes the spectrum's colours.
  const litHatch = useDerivedValue(() => {
    const p = clamp01(place.get());
    const from = p > rangeTo ? rangeTo : Math.min(p, rangeFrom);
    const to = p > rangeTo ? p : rangeFrom;
    return rect(x0 + from * span, cy - BAND / 2, Math.max(0, to - from) * span, BAND);
  });
  const litOpacity = useDerivedValue(() => outside.get() * (HATCH_REST + (1 - HATCH_REST) * clamp01(swell.get())));
  const needleTop = useDerivedValue(() => vec(thumbX.get(), cy - BAND / 2));
  const needleBottom = useDerivedValue(() => vec(thumbX.get(), cy + BAND / 2));
  const tagX = useDerivedValue(() => Math.max(TAG_EDGE, Math.min(width - TAG_WIDTH - TAG_EDGE, thumbX.get() - TAG_WIDTH / 2)));

  const changed = (next: number, felt: boolean) => {
    setCurrent(next);
    if (felt) haptic('step');
    onChange?.(next);
  };

  const sided = (next: number) => {
    setSide(next);
    if (next !== 0) setLastSide(next);
  };

  useAnimatedReaction(
    () => level.get(),
    (now, old) => {
      if (old !== null && now !== old) scheduleOnRN(changed, now, byHand.get());
    },
  );

  useAnimatedReaction(
    () => where.get(),
    (now, old) => {
      if (now !== old) scheduleOnRN(sided, now);
    },
  );

  /** Back to the level the screen opened on. */
  const clear = () => {
    setAutoPressed(false);
    target.set(home);
    onClear?.();
  };

  // The autoPlay loop: the clock moves a finger that is not there.
  useAnimatedReaction(
    () => clock.get(),
    (t, last) => {
      if (t < 0) return;
      const was = last === null || last > t ? -1 : last;
      const peak = (Math.min(count, PEAK) - 0.5) / count;
      const settle = (Math.min(count, SETTLE) - 0.5) / count;
      if (was < DOWN_AT && t >= DOWN_AT) touching.set(1);
      if (t >= UP_FROM && t < BACK_FROM) target.set(home + (peak - home) * glide((t - UP_FROM) / UP_FOR));
      else if (t >= BACK_FROM && t < LIFT_AT) target.set(peak + (settle - peak) * glide((t - BACK_FROM) / BACK_FOR));
      if (was < LIFT_AT && t >= LIFT_AT) touching.set(0);
      if (was < CLEAR_AT && t >= CLEAR_AT) scheduleOnRN(setAutoPressed, true);
      if (was < CLEAR_AT + PRESS_FOR && t >= CLEAR_AT + PRESS_FOR) scheduleOnRN(clear);
    },
  );

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const start = () => {
      byHand.set(false);
      clock.set(0);
      clock.set(withRepeat(withTiming(LOOP, { duration: LOOP * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }), -1, false, undefined, ReduceMotion.Never));
    };
    const resume = () => {
      // A finger still down puts the loop off again when it lifts.
      if (holding.get()) return;
      // The loop starts over from the level it was given.
      target.set(home);
      after(RETURN * 1000, start);
    };
    if (touches === 0) start();
    else after(USER_HOLD * 1000, resume);
    return () => {
      timers.forEach(clearTimeout);
      cancelAnimation(clock);
      clock.set(-1);
    };
  }, [autoPlay, touches, home, byHand, clock, holding, target]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    setAutoPressed(false);
    setTouches((n) => n + 1);
  };

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      // The loop is stopped at once, here on the UI thread, before the finger's first move.
      cancelAnimation(clock);
      clock.set(-1);
      byHand.set(true);
      holding.set(true);
      touching.set(1);
      // Caught on the thumb, it keeps its place under the finger; touched elsewhere, it comes to the finger.
      const at = (event.x - x0) / span;
      grip.set(Math.abs(at - target.get()) * span <= THUMB ? at - target.get() : 0);
      target.set(clamp01(at - grip.get()));
      scheduleOnRN(touched);
    })
    .onChange((event) => {
      target.set(clamp01((event.x - x0) / span - grip.get()));
    })
    .onFinalize(() => {
      holding.set(false);
      touching.set(0);
      scheduleOnRN(touched);
    });

  const saveStyle = useAnimatedStyle(() => ({ backgroundColor: interpolateColor(tone.get(), steps, tones) }));
  const saveLabelStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -NOTE_SHIFT * outside.get() }] }));
  const noteStyle = useAnimatedStyle(() => ({ opacity: outside.get() }));
  const basisStyle = useAnimatedStyle(() => ({ opacity: 1 - outside.get() }));

  const onLayout = (event: LayoutChangeEvent) => {
    setLaidOut({ width: event.nativeEvent.layout.width, height: event.nativeEvent.layout.height });
  };

  const shown = levels[current - 1];
  const cleared = current === opening;
  const sheetTop = insets.top + SHEET_TOP;
  const bottom = Math.max(insets.bottom, 16) + 8;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {/* The page the sheet lies on: only blurred traces of it show through. */}
      <Canvas style={styles.fill}>
        {SMUDGES.map(([x, y, w, h, color], index) => (
          <RoundedRect key={index} x={x * width} y={y * height} width={w * width} height={h * height} r={6} color={color}>
            <BlurMask blur={SMUDGE_BLUR} style="normal" />
          </RoundedRect>
        ))}
      </Canvas>

      <View style={[styles.sheet, { top: sheetTop, paddingBottom: bottom }]}>
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <Text style={styles.subtitle} numberOfLines={1}>
          {subtitle}
        </Text>

        <View style={styles.space} />

        <View
          style={styles.numeral}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={title}
          accessibilityValue={{ min: 1, max: count, now: current, text: `${current}, ${shown.name}` }}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(event) => {
            touched();
            const next = Math.min(count, Math.max(1, current + (event.nativeEvent.actionName === 'increment' ? 1 : -1)));
            target.set((next - 0.5) / count);
          }}
        >
          <Numeral count={count} roll={roll} still={reducedMotion} />
        </View>
        <View style={styles.words}>
          {/* A new level's words fade in over the old ones. */}
          <Animated.View key={current} style={styles.wordsLayer} entering={FadeIn.duration(TEXT_FADE)} exiting={FadeOut.duration(TEXT_FADE)}>
            <Text style={styles.name} numberOfLines={1}>
              {shown.name}
            </Text>
            <Text style={styles.description} numberOfLines={2}>
              {shown.description}
            </Text>
          </Animated.View>
        </View>

        <View style={styles.spaceWide} />

        <GestureDetector gesture={pan}>
          <View style={styles.slider}>
            <Canvas style={styles.fill}>
              {/* Outside the usual range the band is hatched, fading towards the ends. */}
              <Group clip={rect(x0, cy - BAND / 2, rangeFrom * span, BAND)}>
                <Path path={hatch} style="stroke" strokeWidth={HATCH_WIDTH}>
                  <LinearGradient start={vec(x0, 0)} end={vec(x0 + rangeFrom * span, 0)} colors={['rgba(255,255,255,0)', `rgba(255,255,255,${HATCH_ALPHA})`]} />
                </Path>
              </Group>
              <Group clip={rect(x0 + rangeTo * span, cy - BAND / 2, (1 - rangeTo) * span, BAND)}>
                <Path path={hatch} style="stroke" strokeWidth={HATCH_WIDTH}>
                  <LinearGradient
                    start={vec(x0 + rangeTo * span, 0)}
                    end={vec(x1, 0)}
                    colors={[`rgba(255,255,255,${HATCH_ALPHA})`, `rgba(255,255,255,${HATCH_ALPHA})`, 'rgba(255,255,255,0)']}
                    positions={[0, 0.55, 1]}
                  />
                </Path>
              </Group>
              <Line p1={vec(x0 + rangeFrom * span, cy - BAND / 2)} p2={vec(x0 + rangeFrom * span, cy + BAND / 2)} strokeWidth={1} color="rgba(255,255,255,0.14)" />
              <Line p1={vec(x0 + rangeTo * span, cy - BAND / 2)} p2={vec(x0 + rangeTo * span, cy + BAND / 2)} strokeWidth={1} color="rgba(255,255,255,0.14)" />

              <Group clip={litHatch} opacity={litOpacity}>
                <Path path={hatch} style="stroke" strokeWidth={HATCH_WIDTH + 0.6}>
                  <LinearGradient start={vec(x0, 0)} end={vec(x1, 0)} colors={colors} positions={ats} />
                </Path>
              </Group>
              <Line p1={needleTop} p2={needleBottom} strokeWidth={1.5} color={INK} opacity={litOpacity} />

              <Group clip={track}>
                <Rect x={x0} y={0} width={span} height={SLIDER}>
                  <LinearGradient start={vec(x0, 0)} end={vec(x1, 0)} colors={colors} positions={ats} />
                </Rect>
                <Rect rect={shadeLeft} color="black" opacity={SHADE} />
                <Rect rect={shadeRight} color="black" opacity={SHADE} />
                {steps.slice(1).map((step) => (
                  <Tick key={step} x={x0 + ((step - 1) / count) * span} cy={cy} share={(step - 1) / count} swell={swell} />
                ))}
              </Group>

              <Circle cx={thumbX} cy={cy + 2} r={thumbRadius} color="rgba(0,0,0,0.45)">
                <BlurMask blur={5} style="normal" />
              </Circle>
              <Circle cx={thumbX} cy={cy} r={thumbRadius} color={INK} />
              <Circle cx={thumbX} cy={cy} r={dotRadius} color={thumbColor} />
            </Canvas>

            <Animated.Text style={[styles.caption, styles.basis, { left: x0 + 10 }, basisStyle]} numberOfLines={1}>
              {basisLabel}
            </Animated.Text>
            <Text style={[styles.caption, styles.range, { left: x0 + rangeFrom * span, width: (rangeTo - rangeFrom) * span }]} numberOfLines={1}>
              {rangeLabel}
            </Text>
            <Tag label={aboveLabel} color={aboveColor} shown={above} x={tagX} still={reducedMotion} />
            <Tag label={belowLabel} color={belowColor} shown={below} x={tagX} still={reducedMotion} />
          </View>
        </GestureDetector>

        <View style={styles.spaceNarrow} />

        <Pressable
          disabled={cleared}
          hitSlop={12}
          accessibilityRole="button"
          accessibilityState={{ disabled: cleared }}
          onPress={() => {
            touched();
            haptic('press');
            clear();
          }}
        >
          {({ pressed }) => (
            <Animated.Text
              style={[styles.clear, { opacity: cleared ? 0.35 : pressed || autoPressed ? 0.5 : 1, transitionProperty: 'opacity', transitionDuration: 150 }]}
              numberOfLines={1}
            >
              {clearLabel}
            </Animated.Text>
          )}
        </Pressable>

        <View style={styles.spaceNarrow} />

        <View style={styles.divider} />
        <Pressable
          style={styles.help}
          accessibilityRole="button"
          onPress={() => {
            touched();
            haptic('press');
            onHelp?.();
          }}
        >
          {({ pressed }) => (
            <Animated.View style={[styles.helpRow, { opacity: pressed ? 0.5 : 1, transitionProperty: 'opacity', transitionDuration: 120 }]}>
              <Text style={styles.helpLabel} numberOfLines={1}>
                {helpLabel}
              </Text>
              <Ionicons name="chevron-forward" size={14} color={INK} />
            </Animated.View>
          )}
        </Pressable>

        <View style={styles.spaceNarrow} />

        <Pressable
          accessibilityRole="button"
          accessibilityLabel={side === 0 ? saveLabel : `${saveLabel}, ${side > 0 ? aboveNote : belowNote}`}
          onPress={() => {
            touched();
            haptic('press');
            onSave?.(current);
          }}
        >
          {({ pressed }) => (
            <Animated.View style={[styles.save, saveStyle]}>
              <Animated.Text style={[styles.saveLabel, saveLabelStyle]} numberOfLines={1}>
                {saveLabel}
              </Animated.Text>
              <Animated.Text style={[styles.note, noteStyle]} numberOfLines={1}>
                {lastSide > 0 ? aboveNote : belowNote}
              </Animated.Text>
              {/* A press darkens the button whatever its colour. */}
              <Animated.View style={[styles.fill, styles.pressed, { opacity: pressed ? 0.18 : 0, transitionProperty: 'opacity', transitionDuration: 110 }]} />
            </Animated.View>
          )}
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: PAGE,
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  sheet: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    paddingTop: 30,
    borderTopLeftRadius: SHEET_RADIUS,
    borderTopRightRadius: SHEET_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: SHEET,
  },
  title: {
    color: INK,
    fontSize: 25,
    lineHeight: 30,
    fontWeight: '700',
    textAlign: 'center',
  },
  subtitle: {
    marginTop: 6,
    color: MUTE,
    fontSize: 16,
    lineHeight: 20,
    textAlign: 'center',
  },
  space: {
    flex: 1,
  },
  spaceWide: {
    flex: 1.15,
  },
  spaceNarrow: {
    flex: 0.55,
  },
  numeral: {
    height: NUMERAL_SLOT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  numeralCanvas: {
    position: 'absolute',
    width: NUMERAL_WIDTH,
    height: NUMERAL_CANVAS,
  },
  words: {
    height: 70,
    marginTop: 14,
    marginHorizontal: MARGIN,
  },
  wordsLayer: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    alignItems: 'center',
  },
  name: {
    color: INK,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '700',
    textAlign: 'center',
  },
  description: {
    marginTop: 4,
    color: MUTE,
    fontSize: 15,
    lineHeight: 20,
    textAlign: 'center',
  },
  slider: {
    height: SLIDER,
  },
  caption: {
    position: 'absolute',
    color: CAPTION,
    fontSize: 10.5,
    lineHeight: 13,
    letterSpacing: 0.2,
    textTransform: 'uppercase',
  },
  basis: {
    top: 0,
  },
  range: {
    bottom: 0,
    textAlign: 'center',
  },
  tag: {
    position: 'absolute',
    left: 0,
    top: -2,
    width: TAG_WIDTH,
    height: TAG_HEIGHT,
    borderRadius: 6,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
  },
  tagLabel: {
    color: INK,
    fontSize: 10,
    lineHeight: 13,
    fontWeight: '800',
    letterSpacing: 0.3,
    textTransform: 'uppercase',
  },
  clear: {
    color: INK,
    fontSize: 17,
    lineHeight: 22,
    textAlign: 'center',
  },
  divider: {
    height: StyleSheet.hairlineWidth,
    marginHorizontal: MARGIN,
    backgroundColor: DIVIDER,
  },
  help: {
    height: 48,
    marginHorizontal: MARGIN,
    justifyContent: 'center',
  },
  helpRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  helpLabel: {
    color: INK,
    fontSize: 14,
    lineHeight: 18,
  },
  save: {
    height: SAVE,
    marginHorizontal: TRACK_MARGIN,
    borderRadius: SAVE_RADIUS,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  // White on the spectrum's yellow needs a little shadow to stay readable.
  saveLabel: {
    color: INK,
    fontSize: 16,
    lineHeight: 20,
    fontWeight: '700',
    textShadowColor: 'rgba(0,0,0,0.22)',
    textShadowOffset: { width: 0, height: 0.5 },
    textShadowRadius: 3,
  },
  note: {
    position: 'absolute',
    bottom: 10,
    color: 'rgba(255,255,255,0.85)',
    fontSize: 10.5,
    lineHeight: 13,
    fontWeight: '500',
    textShadowColor: 'rgba(0,0,0,0.22)',
    textShadowOffset: { width: 0, height: 0.5 },
    textShadowRadius: 3,
  },
  pressed: {
    backgroundColor: 'black',
  },
});
