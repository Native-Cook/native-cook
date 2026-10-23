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
  RoundedRect,
  Skia,
  TextAlign,
  rect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

/** The title and the lines under it, for one state of the card. */
export type Copy = { title: string; text: string };

type Props = {
  /** The name on the tag of the file that flies in. */
  fileName?: string;
  /** Seconds from the drop to the moment the ring has closed. */
  duration?: number;
  /** Picks a file by itself every LOOP seconds, lets the import finish and puts the card back. */
  autoPlay?: boolean;
  idle?: Copy;
  hover?: Copy;
  importing?: Copy;
  done?: Copy;
  /** The button while nothing is picked, and while a file is on its way. */
  chooseLabel?: string;
  cancelLabel?: string;
  backdrop?: string;
  cardColor?: string;
  /** The grey disc round the icon. */
  ringColor?: string;
  /** Titles, discs and particles. */
  ink?: string;
  /** The lines under a title. */
  mute?: string;
  /** Called when a file is picked: open the real picker here. */
  onPick?: (fileName: string) => void;
  /** Called when the file is dropped and the import starts. */
  onImport?: (fileName: string) => void;
  /** Called when the ring has closed and the check shows. */
  onComplete?: (fileName: string) => void;
  /** Called when Cancel stops a file on its way. */
  onCancel?: (fileName: string) => void;
};

const IDLE: Copy = { title: 'Import file', text: 'Tap to choose a file to upload it to your project. It only takes a few seconds' };
const HOVER: Copy = { title: 'Release file', text: 'Release your files here to upload' };
const IMPORTING: Copy = { title: 'Importing data', text: 'Please wait a few seconds while we’re importing your data to the project' };
const DONE: Copy = { title: 'Import complete', text: 'Your import is complete. You can now start sharing your file.' };

const BACKDROP = '#F1F3F5';
const CARD = '#17161F';
const RING = '#2A2932';
const INK = '#FFFFFF';
const MUTE = '#A0A0A8';
const DISC_BLACK = '#000000';
// White with this much grey mixed in while the button is held.
const BUTTON_DIM = 0.22;

// The card, in points; it is as wide as the screen allows up to CARD_MAX.
const MARGIN = 24;
const CARD_MAX = 360;
const CARD_HEIGHT = 340;
const CARD_RADIUS = 8;
// The card sits a little above the middle of the screen.
const CARD_LIFT = 12;
const ICON_Y = 102;
// Radii of the icon in its four states.
const RING_IDLE = 36;
const DISC_IDLE = 22;
const GLOW = 32;
const GLOW_BLUR = 12;
const DISC_IMPORT = 31;
const RING_IMPORT = 47.5;
const BAND = 12;
const DISC_DONE = 33;
// The arrow is drawn 19 points tall; on the black disc it is this much bigger.
const ARROW_IMPORT = 1.7;

const COPY_TOP = 176;
const COPY_WIDTH = 280;
const TITLE = 18;
const TITLE_LINE = 24;
const TEXT = 14;
const TEXT_LINE = 20;
const COPY_GAP = 6;
// "Release file" has one line under it and sits lower than the other titles.
const HOVER_DROP = 18;

const BUTTON = 44;
const BUTTON_INSET = 16;
const BUTTON_RADIUS = 5;
const LABEL = 15;
const LABEL_LINE = 20;

// How far the card's content blurs as it swaps, and the button while a file hovers.
const BLUR = 8;
const BUTTON_BLUR = 9;
// What is left of the white button under that blur: a grey bar as wide as the card.
const BUTTON_SMEAR = 0.34;

// The file: a sheet with a folded corner and a name tag under it, drawn in a box with room for its
// shadow and its blur.
const SHEET_WIDTH = 60;
const SHEET_HEIGHT = 77;
const SHEET_FOLD = 16;
const TAG = 24;
const TAG_GAP = 4;
const TAG_TEXT = 11;
const TAG_MAX = 112;
const FILE_BOX = 168;
const FILE_TOP = 28;
// Where the sheet's centre hovers: this far in from the card's right edge, this far down the card.
const FILE_IN = 58;
const FILE_DOWN = 0.57;
// It starts off the right edge of the screen, lower, and tilted.
const FILE_OUT = 70;
const FILE_LOW = 90;
const FILE_TILT = 16;
const FILE_BOB = 2.5;

// The clock, in seconds since a file was picked.

/** autoPlay: Choose file is pressed at PRESS_AT for PRESS_FOR; the card is put back DONE_HOLD after the check. */
export const LOOP = 9.2;
const PRESS_AT = 0.8;
const PRESS_FOR = 0.15;
const DONE_HOLD = 1.25;
/** After a touch autoPlay waits this long, and for the card to be idle, before it starts over. */
const USER_HOLD = 3;
const DURATION = 3;

// The file's leading edge crosses the card's edge, and the content swaps to "Release file".
const HOVER_AT = 0.1;
const RELEASE_AT = 1.25;
// The file shrinks into the icon.
const DROP_FOR = 0.3;
const IMPORT_AT = 1.5;
// The grey ring widens and throws the particles out.
const RING_AT = IMPORT_AT + 0.2;
// A swap: the old state blurs away over SWAP_OUT while the new one comes in over SWAP_IN, SWAP_LAG
// later, out of a lighter blur (IN_BLUR of the full one) so that it reads at once.
const SWAP_OUT = 0.28;
const SWAP_LAG = 0.04;
const SWAP_IN = 0.22;
const IN_BLUR = 0.5;
// The particles leave for the ring one every PULL_STEP, each taking PULL_TRAVEL; the last one
// lands when `duration` is over.
const PULL_STEP = 0.045;
const PULL_TRAVEL = 0.28;
// The closed ring holds, then turns into the check.
const DONE_AFTER = 0.45;
const DONE_FOR = 0.4;
// Cancel, or a tap on the finished card: everything returns to "Import file".
const LEAVE = 0.55;

// Particles: how fast one slows after it is thrown (per second), how long a streak its speed
// draws (seconds of travel, and the most), how much its orbit breathes, and how far it swirls on
// its way back in (radians).
const BURST = 5.5;
const STREAK = 0.03;
const STREAK_MAX = 9;
const BREATH = 2;
const SWIRL = 0.7;

/**
 * One particle: where on the circle it is thrown (degrees, clockwise from the right), the radius
 * it floats at, its diameter, how fast it circles (radians a second), how long after the ring
 * widens it leaves, and its place in the queue back.
 */
const DOTS = [
  [226, 86, 6, 0.14, 0.0, 3],
  [304, 80, 10, 0.12, 0.05, 6],
  [273, 57, 5, 0.26, 0.02, 1],
  [224, 59, 3, 0.24, 0.08, 9],
  [196, 88, 9, 0.11, 0.03, 0],
  [189, 65, 3, 0.2, 0.1, 11],
  [162, 68, 6, 0.18, 0.01, 5],
  [140, 61, 3, 0.22, 0.06, 2],
  [42, 66, 3, 0.2, 0.09, 8],
  [19, 82, 9, 0.12, 0.04, 4],
  [351, 57, 5, 0.26, 0.0, 10],
  [9, 52, 3, 0.3, 0.07, 7],
  [322, 63, 3.5, 0.2, 0.05, 12],
  [343, 80, 2.5, 0.13, 0.11, 13],
];
const PULL_FOR = (DOTS.length - 1) * PULL_STEP + PULL_TRAVEL;
// The layers `levelsAt` describes: four states of the copy and the importing icon.
const STATES = 5;

const FONT = Platform.select({ ios: ['.AppleSystemUIFont'], default: ['sans-serif'] });

// An up arrow and a check round the origin, and the stack of pages: a sheet with a cut corner in
// front of the edge of the one behind it.
const ARROW = Skia.Path.MakeFromSVGString('M0 8.8L0 -7.4M-6.8 -1.2L0 -8L6.8 -1.2')!;
const CHECK = Skia.Path.MakeFromSVGString('M-11.5 1L-4 8.5L12 -8')!;
const PAGE = Skia.Path.MakeFromSVGString('M-3.5 -16H8L14 -10V6.5Q14 10 10.5 10H-3.5Q-7 10 -7 6.5V-12.5Q-7 -16 -3.5 -16Z')!;
const PAGE_BEHIND = Skia.Path.MakeFromSVGString('M-12.6 -9.5V12Q-12.6 15.8 -8.8 15.8H7.5')!;

function sheetPath() {
  const w = SHEET_WIDTH;
  const h = SHEET_HEIGHT;
  const c = SHEET_FOLD;
  return Skia.Path.MakeFromSVGString(`M5 0H${w - 5}Q${w} 0 ${w} 5V${h - c}L${w - c} ${h}H5Q0 ${h} 0 ${h - 5}V5Q0 0 5 0Z`)!;
}

const SHEET = sheetPath();
const FOLD = Skia.Path.MakeFromSVGString(
  `M${SHEET_WIDTH - SHEET_FOLD} ${SHEET_HEIGHT}V${SHEET_HEIGHT - SHEET_FOLD + 4}Q${SHEET_WIDTH - SHEET_FOLD} ${SHEET_HEIGHT - SHEET_FOLD} ${SHEET_WIDTH - SHEET_FOLD + 4} ${SHEET_HEIGHT - SHEET_FOLD}H${SHEET_WIDTH}Z`,
)!;
// The mark in the middle of the sheet: two small peaks.
const MARK = Skia.Path.MakeFromSVGString('M-9 5L-3.5 -3.5L0 1.5L3.5 -5L9 5')!;

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

/** A spring that starts moving at once and settles on 1: 1 − e^(−damping·t)·cos(frequency·t). */
function spring(t: number, damping: number, frequency: number) {
  'worklet';
  return t <= 0 ? 0 : 1 - Math.exp(-damping * t) * Math.cos(frequency * t);
}

/** When the check starts to show, `duration` after the drop plus the hold of the closed ring. */
function doneAt(duration: number) {
  'worklet';
  return IMPORT_AT + duration + DONE_AFTER;
}

/** The clock stops here, on the finished card. */
function endAt(duration: number) {
  'worklet';
  return doneAt(duration) + DONE_FOR + 0.2;
}

/** Which state the card is in: 0 idle, 1 a file hovers, 2 importing, 3 done. */
function stageAt(e: number, duration: number) {
  'worklet';
  return e < 0 ? 0 : e < IMPORT_AT ? 1 : e < doneAt(duration) ? 2 : 3;
}

/**
 * How much of each state shows at `e` seconds, 0 to 1 (the idle, hover, importing and done copy,
 * then the importing icon, which stays and becomes the check), followed by how blurred each is,
 * 0 to 1. `leave` takes everything back to idle.
 */
function levelsAt(e: number, leave: number, duration: number) {
  'worklet';
  const done = doneAt(duration);
  const out = smooth(leave, 0, 0.6);
  const back = smooth(leave, 0.3, 1);
  const hoverIn = smooth(e, HOVER_AT + SWAP_LAG, HOVER_AT + SWAP_LAG + SWAP_IN);
  const importIn = smooth(e, IMPORT_AT + SWAP_LAG, IMPORT_AT + SWAP_LAG + SWAP_IN);
  const doneIn = smooth(e, done + SWAP_LAG, done + SWAP_LAG + SWAP_IN);
  const idleOut = smooth(e, HOVER_AT, HOVER_AT + SWAP_OUT);
  const hoverOut = smooth(e, IMPORT_AT, IMPORT_AT + SWAP_OUT);
  const importOut = smooth(e, done, done + SWAP_OUT);
  return [
    1 - idleOut * (1 - back),
    hoverIn * (1 - hoverOut) * (1 - out),
    importIn * (1 - importOut) * (1 - out),
    doneIn * (1 - out),
    importIn * (1 - out),
    leave > 0 ? IN_BLUR * idleOut * (1 - back) : idleOut,
    Math.max(IN_BLUR * (1 - hoverIn), hoverOut, out),
    Math.max(IN_BLUR * (1 - importIn), importOut, out),
    Math.max(IN_BLUR * (1 - doneIn), out),
    Math.max(IN_BLUR * (1 - importIn), out),
  ];
}

/** The width of the white band round the black disc: it thickens as each particle lands. */
function bandAt(e: number, duration: number) {
  'worklet';
  const pull = IMPORT_AT + duration - PULL_FOR;
  let landed = 0;
  for (let i = 0; i < DOTS.length; i++) {
    const lands = pull + DOTS[i][5] * PULL_STEP + PULL_TRAVEL;
    landed += smooth(e, lands - 0.08, lands + 0.1);
  }
  return (BAND * landed) / DOTS.length;
}

/** Particle `index` at `e` seconds: x and y from the icon's centre, its heading, half its length and width, alpha. */
function dotAt(index: number, e: number, leave: number, duration: number, still: boolean) {
  'worklet';
  const [degrees, orbit, size, speed, delay, order] = DOTS[index];
  const t = e - RING_AT - delay;
  if (t <= 0) return [0, 0, 0, 0, 0, 0];
  // Thrown from the disc's edge, it slows to its orbit, then circles and breathes.
  const thrown = still ? 1 : 1 - Math.exp(-BURST * t);
  const floating = DISC_IMPORT + (orbit - DISC_IMPORT) * thrown + (still ? 0 : BREATH * Math.sin(t * 1.3 + index));
  // Called back: it falls into the ring faster and faster, along a short spiral.
  const u = clamp01((e - (IMPORT_AT + duration - PULL_FOR) - order * PULL_STEP) / PULL_TRAVEL);
  const fall = still ? 0 : u * u * u;
  const cancel = still ? 0 : leave * leave;
  const radius = (floating + (DISC_IMPORT + 2 - floating) * fall) * (1 - cancel) + (DISC_IMPORT + 2) * cancel;
  const angle = (degrees * Math.PI) / 180 + (still ? 0 : speed * t + SWIRL * fall);
  // Its speed along the radius stretches it into a streak.
  const velocity = still
    ? 0
    : (orbit - DISC_IMPORT) * BURST * (1 - thrown) + ((floating - DISC_IMPORT) * 3 * u * u * (u < 1 ? 1 : 0)) / PULL_TRAVEL;
  const long = size + Math.min(STREAK_MAX, velocity * STREAK);
  const wide = size * Math.max(0.7, Math.sqrt(size / long));
  const alpha = smooth(t, 0, still ? 0.3 : 0.06) * (1 - smooth(u, still ? 0 : 0.8, 1)) * (1 - smooth(leave, 0, 0.7));
  return [Math.cos(angle) * radius, Math.sin(angle) * radius, angle, long / 2, wide / 2, alpha];
}

function paragraph(text: string, size: number, line: number, weight: number, color: string, width: number, spacing: number, oneLine = false) {
  const builder = Skia.ParagraphBuilder.Make(oneLine ? { textAlign: TextAlign.Center, maxLines: 1, ellipsis: '…' } : { textAlign: TextAlign.Center });
  builder.pushStyle({
    color: Skia.Color(color),
    fontFamilies: FONT,
    fontSize: size,
    fontStyle: { weight },
    heightMultiplier: line / size,
    halfLeading: true,
    letterSpacing: spacing,
  });
  builder.addText(text);
  const built = builder.build();
  built.layout(width);
  return built;
}

function tick(style: Haptics.ImpactFeedbackStyle) {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(style).catch(() => {});
}

type LayerProps = {
  /** What `levelsAt` gives, and which of its states this layer is. */
  levels: SharedValue<number[]>;
  index: number;
  /** The part of the canvas the layer is kept to (leave room for the blur). */
  bounds: { x: number; y: number; width: number; height: number };
  children: ReactNode;
};

/** A part of the card that belongs to one state: it comes out of a blur as it appears and blurs away as it goes. */
function Layer({ levels, index, bounds, children }: LayerProps) {
  const opacity = useDerivedValue(() => levels.get()[index]);
  const sigma = useDerivedValue(() => BLUR * levels.get()[index + STATES]);
  return (
    <Group
      clip={rect(bounds.x, bounds.y, bounds.width, bounds.height)}
      layer={
        <Paint opacity={opacity}>
          <Blur blur={sigma} />
        </Paint>
      }
    >
      {children}
    </Group>
  );
}

/** A label of the button, which only fades. */
function Fade({ weight, children }: { weight: SharedValue<number>; children: ReactNode }) {
  return <Group layer={<Paint opacity={weight} />}>{children}</Group>;
}

type DotProps = {
  index: number;
  elapsed: SharedValue<number>;
  leave: SharedValue<number>;
  duration: number;
  still: boolean;
  color: string;
};

function Dot({ index, elapsed, leave, duration, still, color }: DotProps) {
  const state = useDerivedValue(() => dotAt(index, elapsed.get(), leave.get(), duration, still));
  const transform = useDerivedValue(() => {
    const s = state.get();
    return [{ translateX: s[0] }, { translateY: s[1] }, { rotate: s[2] }, { scaleX: s[3] }, { scaleY: s[4] }];
  });
  const opacity = useDerivedValue(() => state.get()[5]);
  return (
    <Group transform={transform}>
      <Circle cx={0} cy={0} r={1} color={color} opacity={opacity} />
    </Group>
  );
}

/**
 * An upload card. Picking a file (a tap on the card or on its button) flies the file in from the
 * right; as it crosses the card everything inside blurs and swaps to "Release file". The file
 * then drops into the icon, the ring round it widens and throws white particles out, which circle
 * while the import runs, are called back one by one and close the ring into a white band that
 * becomes a check. Nothing is uploaded here: `onImport` is where the real request goes.
 */
export function OrbitUploadDropzone({
  fileName = 'brand_guide.pdf',
  duration = DURATION,
  autoPlay = false,
  idle = IDLE,
  hover = HOVER,
  importing = IMPORTING,
  done = DONE,
  chooseLabel = 'Choose file',
  cancelLabel = 'Cancel',
  backdrop = BACKDROP,
  cardColor = CARD,
  ringColor = RING,
  ink = INK,
  mute = MUTE,
  onPick,
  onImport,
  onComplete,
  onCancel,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  // 0 idle, 1 a file hovers, 2 importing, 3 done.
  const [stage, setStage] = useState(0);
  // The card is on its way back to idle.
  const [leaving, setLeaving] = useState(false);
  // The button held down by the autoPlay loop.
  const [autoPressed, setAutoPressed] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // Seconds since a file was picked; negative while the card is idle.
  const elapsed = useSharedValue(-1);
  // 0 to 1 while the card returns to idle from wherever the clock stopped.
  const leave = useSharedValue(0);
  const buttonDown = useSharedValue(0);
  const zoneDown = useSharedValue(0);
  // The run in progress was started by autoPlay, which gives no haptics.
  const silent = useRef(false);

  const width = Math.min(CARD_MAX, size.width - MARGIN * 2);
  const left = (size.width - width) / 2;
  const top = Math.max(insets.top + MARGIN, (size.height - CARD_HEIGHT) / 2 - CARD_LIFT);
  const cx = width / 2;
  const end = endAt(duration);
  const still = reducedMotion;

  const copies = useMemo(() => {
    const x = (width - COPY_WIDTH) / 2;
    return [idle, hover, importing, done].map((copy, index) => ({
      x,
      y: COPY_TOP + (index === 1 ? HOVER_DROP : 0),
      title: paragraph(copy.title, TITLE, TITLE_LINE, 500, ink, COPY_WIDTH, -0.3),
      text: paragraph(copy.text, TEXT, TEXT_LINE, 400, mute, COPY_WIDTH, -0.1),
    }));
  }, [idle, hover, importing, done, ink, mute, width]);

  const labels = useMemo(
    () => [chooseLabel, cancelLabel].map((label) => paragraph(label, LABEL, LABEL_LINE, 500, cardColor, width - BUTTON_INSET * 2, -0.2, true)),
    [chooseLabel, cancelLabel, cardColor, width],
  );

  const tag = useMemo(() => {
    const name = paragraph(fileName, TAG_TEXT, TAG, 500, CARD, TAG_MAX, 0, true);
    return { name, width: Math.ceil(name.getLongestLine()) + 16 };
  }, [fileName]);

  const rest = () => {
    elapsed.set(-1);
    leave.set(0);
    setStage(0);
    setLeaving(false);
  };

  const arrive = (now: number) => {
    setStage(now);
    if (now === 2) {
      if (!silent.current) tick(Haptics.ImpactFeedbackStyle.Medium);
      onImport?.(fileName);
    }
    if (now === 3) {
      if (!silent.current && process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
      onComplete?.(fileName);
    }
  };

  /** Picks the file: one clock then carries the card to the check. */
  const start = (auto: boolean) => {
    if (stage !== 0 || leaving) return;
    silent.current = auto;
    if (!auto) tick(Haptics.ImpactFeedbackStyle.Light);
    onPick?.(fileName);
    elapsed.set(0);
    elapsed.set(withTiming(end, { duration: end * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
  };

  /** Takes the card back to idle from where it is: Cancel on the way, or a tap once it is done. */
  const back = (auto: boolean) => {
    if (stage === 0 || leaving) return;
    setLeaving(true);
    cancelAnimation(elapsed);
    if (stage < 3) onCancel?.(fileName);
    if (!auto) tick(Haptics.ImpactFeedbackStyle.Light);
    leave.set(
      withTiming(1, { duration: LEAVE * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }, (finished) => {
        if (finished) scheduleOnRN(rest);
      }),
    );
  };

  useAnimatedReaction(
    () => stageAt(elapsed.get(), duration),
    (now, old) => {
      if (old !== null && now !== old) scheduleOnRN(arrive, now);
    },
  );

  useEffect(
    () => () => {
      cancelAnimation(elapsed);
      cancelAnimation(leave);
    },
    [elapsed, leave],
  );

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ start, back, stage, leaving });
  useEffect(() => {
    latest.current = { start, back, stage, leaving };
  });

  // A longer import makes the loop longer by as much, so the rests stay the same.
  const period = LOOP + duration - DURATION;

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      const picked = origin + (PRESS_AT + PRESS_FOR) * 1000;
      after(origin + PRESS_AT * 1000 - Date.now(), () => setAutoPressed(true));
      after(picked - Date.now(), () => {
        setAutoPressed(false);
        latest.current.start(true);
      });
      after(picked + (doneAt(duration) + DONE_FOR + DONE_HOLD) * 1000 - Date.now(), () => latest.current.back(true));
      after(origin + period * 1000 - Date.now(), () => cycle(origin + period * 1000));
    };
    const resume = () => {
      // A finished card is put back first; a file on its way is left to finish.
      if (latest.current.stage === 3) latest.current.back(true);
      if (latest.current.stage !== 0 || latest.current.leaving) return after(100, resume);
      cycle(Date.now());
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, resume);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, duration, period]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    setAutoPressed(false);
    setTouches((n) => n + 1);
  };

  useEffect(() => {
    buttonDown.set(withTiming(autoPressed ? 1 : 0, { duration: 100 }));
  }, [autoPressed, buttonDown]);

  const levels = useDerivedValue(() => levelsAt(elapsed.get(), leave.get(), duration));

  // The idle icon gives a little under a finger.
  const idleScale = useDerivedValue(() => [{ scale: 1 - 0.06 * zoneDown.get() }]);
  // The glowing disc swells as the file lands in it.
  const glowScale = useDerivedValue(() => {
    const landing = RELEASE_AT + DROP_FOR * 0.7;
    return [{ scale: still ? 1 : 1 + 0.1 * smooth(elapsed.get(), landing - 0.12, landing) * (1 - smooth(elapsed.get(), landing, landing + 0.2)) }];
  });

  const band = useDerivedValue(() => bandAt(elapsed.get(), duration) * (1 - leave.get()));
  // 0 to 1: the closed ring turns into the check.
  const turned = useDerivedValue(() => smooth(elapsed.get(), doneAt(duration), doneAt(duration) + DONE_FOR));
  const ringRadius = useDerivedValue(() => {
    const t = elapsed.get() - RING_AT;
    return DISC_IMPORT + (RING_IMPORT - DISC_IMPORT) * (still ? smooth(t, 0, 0.2) : spring(t, 9, 11));
  });
  // The grey ring flashes lighter as it widens, and goes once the white band has covered it.
  const ringFlash = useDerivedValue(() => 0.1 * smooth(elapsed.get(), RING_AT, RING_AT + 0.08) * (1 - smooth(elapsed.get(), RING_AT + 0.1, RING_AT + 0.6)));
  const ringOpacity = useDerivedValue(() => 1 - smooth(band.get(), BAND * 0.7, BAND));
  // The white band grows out from the black disc, then, once the disc has begun to pale, draws in
  // to the size of the check's disc.
  const whiteRadius = useDerivedValue(() => {
    const t = elapsed.get() - doneAt(duration) - DONE_FOR * 0.25;
    const settle = still ? smooth(t, 0, DONE_FOR * 0.75) : spring(t, 8, 9);
    return DISC_IMPORT + band.get() - (DISC_IMPORT + BAND - DISC_DONE) * settle;
  });
  const whiteOpacity = useDerivedValue(() => smooth(band.get(), 0, 0.6));
  // The black disc pales through grey to white under the arrow, which goes as the check is drawn.
  const discColor = useDerivedValue(() => {
    const v = Math.round(255 * smooth(turned.get(), 0, 0.75));
    return `rgb(${v},${v},${v})`;
  });
  const arrowOpacity = useDerivedValue(() => 1 - smooth(turned.get(), 0.15, 0.6));
  const checkEnd = useDerivedValue(() => smooth(turned.get(), 0.45, 1));

  // The button: sharp and white, a blurred grey bar across the card while a file hovers, gone once done.
  const smear = useDerivedValue(() => {
    const e = elapsed.get();
    return smooth(e, HOVER_AT, HOVER_AT + SWAP_OUT) * (1 - smooth(e, IMPORT_AT + SWAP_LAG, IMPORT_AT + SWAP_LAG + SWAP_IN)) * (1 - smooth(leave.get(), 0, 0.6));
  });
  const buttonWeight = useDerivedValue(() => {
    const gone = smooth(elapsed.get(), doneAt(duration), doneAt(duration) + SWAP_OUT) * (1 - smooth(leave.get(), 0.3, 1));
    return (1 - (1 - BUTTON_SMEAR) * smear.get()) * (1 - gone);
  });
  const buttonBlur = useDerivedValue(() => BUTTON_BLUR * smear.get());
  const buttonX = useDerivedValue(() => BUTTON_INSET * (1 - smear.get()));
  const buttonWidth = useDerivedValue(() => width - 2 * BUTTON_INSET * (1 - smear.get()));
  const buttonColor = useDerivedValue(() => {
    const v = Math.round(255 * (1 - BUTTON_DIM * buttonDown.get()));
    return `rgb(${v},${v},${v})`;
  });
  const chooseWeight = useDerivedValue(() => {
    const shown = 1 - smooth(elapsed.get(), HOVER_AT, HOVER_AT + 0.15);
    return shown + (1 - shown) * smooth(leave.get(), 0.4, 1);
  });
  const cancelWeight = useDerivedValue(() => smooth(elapsed.get(), HOVER_AT + 0.1, HOVER_AT + 0.3) * (1 - smooth(leave.get(), 0, 0.5)));

  // The file, as offsets from the spot it hovers at.
  const fromX = size.width + FILE_OUT - (left + width - FILE_IN);
  const toIconX = cx - (width - FILE_IN);
  const toIconY = ICON_Y - CARD_HEIGHT * FILE_DOWN;
  const file = useDerivedValue(() => {
    const e = elapsed.get();
    const gone = leave.get() * leave.get();
    if (still) {
      return [0, 0, 0, 1, smooth(e, 0, 0.25) * (1 - smooth(e, RELEASE_AT, RELEASE_AT + DROP_FOR)) * (1 - leave.get()), 0];
    }
    // It flies in on a soft spring, bobs as if held, then falls into the icon; Cancel sends it back out.
    const away = 1 - spring(e, 5.5, 6.5) * (1 - gone);
    const held = smooth(e, 0.5, 0.9);
    const u = clamp01((e - RELEASE_AT) / DROP_FOR);
    const drop = u * u * (3 - 2 * u);
    return [
      fromX * away + toIconX * drop,
      FILE_LOW * away + FILE_BOB * held * Math.sin((e - 0.5) * 3.2) * (1 - drop) + toIconY * drop,
      FILE_TILT * away + 1.2 * held * Math.sin(e * 2.6) * (1 - drop),
      1 - 0.8 * drop,
      e < 0 ? 0 : 1 - smooth(u, 0.6, 1),
      BLUR * drop,
    ];
  });
  const fileStyle = useAnimatedStyle(() => {
    const [x, y, tilt, scale, opacity] = file.get();
    return { opacity, transform: [{ translateX: x }, { translateY: y }, { rotate: `${tilt}deg` }, { scale }] };
  });
  const fileBlur = useDerivedValue(() => file.get()[5]);

  const onLayout = (event: LayoutChangeEvent) => {
    setLaidOut({ width: event.nativeEvent.layout.width, height: event.nativeEvent.layout.height });
  };

  const shown = [idle, hover, importing, done][stage];
  const buttonTop = CARD_HEIGHT - BUTTON_INSET - BUTTON;
  const iconBounds = { x: cx - 110, y: 0, width: 220, height: ICON_Y + 80 };
  const copyBounds = { x: 0, y: COPY_TOP - 16, width, height: buttonTop - COPY_TOP + 16 };
  const buttonBounds = { x: 0, y: buttonTop - 28, width, height: BUTTON + BUTTON_INSET + 28 };
  const labelY = buttonTop + (BUTTON - LABEL_LINE) / 2;
  const sheetX = (FILE_BOX - SHEET_WIDTH) / 2;
  const tagX = (FILE_BOX - tag.width) / 2;
  const tagY = FILE_TOP + SHEET_HEIGHT + TAG_GAP;

  return (
    <View style={[styles.screen, { backgroundColor: backdrop }]} onLayout={onLayout}>
      <StatusBar style="dark" />

      <View style={[styles.card, { left, top, width, backgroundColor: cardColor }]}>
        <View style={styles.clip}>
          <Canvas style={StyleSheet.absoluteFill}>
            {/* Idle: a white disc with a dark arrow on a grey ring. */}
            <Layer levels={levels} index={0} bounds={iconBounds}>
              <Group transform={idleScale} origin={vec(cx, ICON_Y)}>
                <Circle cx={cx} cy={ICON_Y} r={RING_IDLE} color={ringColor} />
                <Circle cx={cx} cy={ICON_Y} r={DISC_IDLE} color={ink} />
                <Group transform={[{ translateX: cx }, { translateY: ICON_Y }]}>
                  <Path path={ARROW} style="stroke" strokeWidth={4} strokeCap="round" strokeJoin="round" color={cardColor} />
                </Group>
              </Group>
            </Layer>

            {/* A file hovers: a glowing disc with a stack of pages. */}
            <Layer levels={levels} index={1} bounds={iconBounds}>
              <Group transform={glowScale} origin={vec(cx, ICON_Y)}>
                <Circle cx={cx} cy={ICON_Y} r={GLOW} color={ink} opacity={0.34}>
                  <BlurMask blur={GLOW_BLUR} style="normal" />
                </Circle>
                <Circle cx={cx} cy={ICON_Y} r={GLOW}>
                  <RadialGradient c={vec(cx, ICON_Y)} r={GLOW} colors={['rgba(255,255,255,0.62)', 'rgba(255,255,255,0.34)']} />
                </Circle>
                <Group transform={[{ translateX: cx }, { translateY: ICON_Y }]}>
                  <Path path={PAGE_BEHIND} style="stroke" strokeWidth={3} strokeCap="round" strokeJoin="round" color={ink} />
                  <Path path={PAGE} color={ink} />
                </Group>
              </Group>
            </Layer>

            {/* Importing: a black disc on a wider ring, which the white band closes round; then the check. */}
            <Layer levels={levels} index={4} bounds={iconBounds}>
              <Circle cx={cx} cy={ICON_Y} r={ringRadius} color={ringColor} opacity={ringOpacity} />
              <Circle cx={cx} cy={ICON_Y} r={ringRadius} color={ink} opacity={ringFlash} />
              <Circle cx={cx} cy={ICON_Y} r={whiteRadius} color={ink} opacity={whiteOpacity} />
              <Circle cx={cx} cy={ICON_Y} r={DISC_IMPORT} color={discColor} />
              <Group transform={[{ translateX: cx }, { translateY: ICON_Y }]}>
                <Group transform={[{ scale: ARROW_IMPORT }]}>
                  <Path path={ARROW} style="stroke" strokeWidth={4} strokeCap="round" strokeJoin="round" color={ink} opacity={arrowOpacity} />
                </Group>
                <Path path={CHECK} style="stroke" strokeWidth={4} strokeCap="round" strokeJoin="round" color={DISC_BLACK} end={checkEnd} />
              </Group>
            </Layer>

            <Group transform={[{ translateX: cx }, { translateY: ICON_Y }]}>
              {DOTS.map((_, index) => (
                <Dot key={index} index={index} elapsed={elapsed} leave={leave} duration={duration} still={still} color={ink} />
              ))}
            </Group>

            {copies.map((copy, index) => (
              <Layer key={index} levels={levels} index={index} bounds={copyBounds}>
                <Paragraph paragraph={copy.title} x={copy.x} y={copy.y} width={COPY_WIDTH} />
                <Paragraph paragraph={copy.text} x={copy.x} y={copy.y + TITLE_LINE + COPY_GAP} width={COPY_WIDTH} />
              </Layer>
            ))}

            <Group
              clip={rect(buttonBounds.x, buttonBounds.y, buttonBounds.width, buttonBounds.height)}
              layer={
                <Paint opacity={buttonWeight}>
                  <Blur blur={buttonBlur} />
                </Paint>
              }
            >
              <RoundedRect x={buttonX} y={buttonTop} width={buttonWidth} height={BUTTON} r={BUTTON_RADIUS} color={buttonColor} />
              <Fade weight={chooseWeight}>
                <Paragraph paragraph={labels[0]} x={BUTTON_INSET} y={labelY} width={width - BUTTON_INSET * 2} />
              </Fade>
              <Fade weight={cancelWeight}>
                <Paragraph paragraph={labels[1]} x={BUTTON_INSET} y={labelY} width={width - BUTTON_INSET * 2} />
              </Fade>
            </Group>
          </Canvas>
        </View>

        <Pressable
          style={StyleSheet.absoluteFill}
          accessibilityRole="button"
          accessibilityLabel={`${shown.title}. ${shown.text}`}
          onPressIn={() => zoneDown.set(withTiming(1, { duration: 100 }))}
          onPressOut={() => zoneDown.set(withTiming(0, { duration: 200 }))}
          onPress={() => {
            touched();
            if (stage === 0) start(false);
            else if (stage === 3) back(false);
          }}
        />
        {stage < 3 && (
          <Pressable
            style={[styles.button, { top: buttonTop }]}
            accessibilityRole="button"
            accessibilityLabel={stage === 0 ? chooseLabel : cancelLabel}
            onPressIn={() => buttonDown.set(withTiming(1, { duration: 100 }))}
            onPressOut={() => buttonDown.set(withTiming(0, { duration: 150 }))}
            onPress={() => {
              touched();
              if (stage === 0) start(false);
              else back(false);
            }}
          />
        )}
      </View>

      <Animated.View
        pointerEvents="none"
        style={[
          styles.file,
          { left: left + width - FILE_IN - FILE_BOX / 2, top: top + CARD_HEIGHT * FILE_DOWN - FILE_TOP - SHEET_HEIGHT / 2 },
          fileStyle,
        ]}
      >
        <Canvas style={StyleSheet.absoluteFill}>
          <Group
            layer={
              <Paint>
                <Blur blur={fileBlur} />
              </Paint>
            }
          >
            <Group transform={[{ translateX: sheetX }, { translateY: FILE_TOP }]}>
              <Path path={SHEET} color="rgba(10,10,20,0.28)" transform={[{ translateY: 8 }]}>
                <BlurMask blur={10} style="normal" />
              </Path>
              <Path path={SHEET}>
                <LinearGradient start={vec(0, 0)} end={vec(SHEET_WIDTH, SHEET_HEIGHT)} colors={['#FFFFFF', '#E9EAEE']} />
              </Path>
              <Path path={FOLD} color="#D3D5DC" />
              <Group transform={[{ translateX: SHEET_WIDTH / 2 }, { translateY: SHEET_HEIGHT / 2 - 4 }]}>
                <Path path={MARK} style="stroke" strokeWidth={2.2} strokeCap="round" strokeJoin="round" color="#CDCFD8" />
              </Group>
            </Group>
            <RoundedRect x={tagX} y={tagY + 3} width={tag.width} height={TAG} r={5} color="rgba(10,10,20,0.22)">
              <BlurMask blur={6} style="normal" />
            </RoundedRect>
            <RoundedRect x={tagX} y={tagY} width={tag.width} height={TAG} r={5} color="#FFFFFF" />
            <Paragraph paragraph={tag.name} x={(FILE_BOX - TAG_MAX) / 2} y={tagY} width={TAG_MAX} />
          </Group>
        </Canvas>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  card: {
    position: 'absolute',
    height: CARD_HEIGHT,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    // A wide soft shadow, as under a card lifted well off the page.
    boxShadow: [
      { offsetX: 0, offsetY: 44, blurRadius: 90, color: 'rgba(23,22,31,0.34)' },
      { offsetX: 0, offsetY: 14, blurRadius: 30, color: 'rgba(23,22,31,0.2)' },
    ],
  },
  clip: {
    ...StyleSheet.absoluteFill,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  button: {
    position: 'absolute',
    left: BUTTON_INSET,
    right: BUTTON_INSET,
    height: BUTTON,
  },
  file: {
    position: 'absolute',
    width: FILE_BOX,
    height: FILE_BOX,
  },
});
