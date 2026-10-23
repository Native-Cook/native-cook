import {
  BlendColor,
  Blur,
  BlurMask,
  Canvas,
  Circle,
  DashPathEffect,
  Group,
  Paint,
  Paragraph,
  Path,
  RoundedRect,
  Skia,
  type SkParagraph,
  rect,
  rrect,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Platform, Pressable, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedReaction,
  useDerivedValue,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Opens the pad, signs it, finishes, then edits, clears and folds it away again, every LOOP seconds. */
  autoPlay?: boolean;
  /** The label is one shared word with a word before it ("Start", "Finish") or after it ("Done"). */
  sharedWord?: string;
  startWord?: string;
  finishWord?: string;
  doneWord?: string;
  title?: string;
  /** The ink's colour and width. */
  inkColor?: string;
  inkWidth?: number;
  background?: string;
  /**
   * The signature autoPlay writes: one SVG path a stroke, in a box SIGNATURE_WIDTH × SIGNATURE_HEIGHT
   * that sits in the middle of the pad.
   */
  signature?: string[];
  /** Called when the pad opens. */
  onStart?: () => void;
  /** Called when a signed pad is finished, with its strokes: each one x, y, x, y… from the pad's centre. */
  onSign?: (strokes: number[][]) => void;
  /** Called when the ink is wiped, and when an empty pad is folded away. */
  onClear?: () => void;
};

/** autoPlay: the whole round every LOOP seconds. Each press is held for PRESS_FOR. */
const LOOP = 10.4;
const PRESS_FOR = 0.12;
const START_AT = 0.8;
const SIGN_AT = 1.75;
const SIGN_FOR = 2.6;
// The pen is off the paper this long between two strokes.
const SIGN_LIFT = 0.12;
const FINISH_AT = 4.75;
const EDIT_AT = 6.4;
const CLEAR_AT = 7.5;
const FOLD_AT = 8.3;
/** After a touch autoPlay waits this long before it starts over from the closed button. */
const USER_HOLD = 3;
const RESET_FOR = 0.9;

const CARD_WIDTH = 300;
const CARD_HEIGHT = 372;
const CARD_RADIUS = 36;
// The stage is the card and room round it for the spring's overshoot.
const STAGE_MARGIN = 28;
const STAGE_WIDTH = CARD_WIDTH + STAGE_MARGIN * 2;
const STAGE_HEIGHT = CARD_HEIGHT + STAGE_MARGIN * 2;
const DASH = [9, 7];
const OUTLINE = 2.2;
// The button: its height, its width as "Start", as "Done" and in the card, and the card's inset round it.
const BUTTON = 64;
const START_WIDTH = 206;
const DONE_WIDTH = 214;
const INSET = 10;
const OPEN_WIDTH = CARD_WIDTH - INSET * 2;
// How far the button drops from the middle to the card's bottom.
const DROP = CARD_HEIGHT / 2 - INSET - BUTTON / 2;
const EDIT = 64;
const EDIT_GAP = 12;
const HEADER_Y = -CARD_HEIGHT / 2 + 44;
const HEADER_INSET = 37;
const HEADER_ICON = 24;
const HIT = 48;
// The pad takes ink between the header and the button.
const PAD_TOP = -CARD_HEIGHT / 2 + 74;
const PAD_BOTTOM = DROP - BUTTON / 2 - 8;
const PAD_INSET = 8;
const LABEL = 18;
const LABEL_WEIGHT = 500;
const LABEL_ICON = 28;
const LABEL_DISC = 24;
const LABEL_GAP = 8;
const WORD_GAP = 5;
// A letter that is on its way is blurred by up to this much and sits this far low.
const LETTER_BLUR = 5;
const LETTER_DROP = 3;
// How far behind its neighbour a letter changes, as a share of one letter's change.
const LETTER_STAGGER = 0.4;
const PRESS_SCALE = 0.06;
// The ink shrinks to this share of its size as it folds into the button.
const INK_FOLDED = 0.25;
// A new point is taken once the finger is this far from the last one.
const INK_STEP = 1.5;
const SIGNATURE_STEP = 3;
export const SIGNATURE_WIDTH = 200;
export const SIGNATURE_HEIGHT = 96;
const SIGNATURE_Y = -12;

const UNFOLD = { stiffness: 120, damping: 14, mass: 1 };
const WORDING = 320;
const DARKEN = 280;
const INK_OUT = 260;
const PRESS_IN = 70;
const PRESS_OUT = 180;

const LIGHT = [244, 244, 248];
const DARK = [35, 35, 38];
const TEXT = [42, 42, 46];
const WHITE = [255, 255, 255];
const LIGHT_COLOR = '#f4f4f8';
const DARK_COLOR = '#232326';
const OUTLINE_COLOR = '#dadadf';
const HEADER_COLOR = '#b5b5ba';
const TITLE = [154, 154, 160];
const POINTER = '#111113';

const FONT = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });

// Icons in a 24 × 24 box: a turning arrow, a pencil whose tip is at 3, 21, a cross and a check.
const ICON_CLEAR =
  'M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z';
const ICON_PENCIL =
  'M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z';
const ICON_CROSS = 'M8.4 8.4L15.6 15.6M15.6 8.4L8.4 15.6';
const ICON_CHECK = 'M7.2 12.4L10.6 15.6L16.8 8.8';
// The line a pen has just left, drawn left of the pencil in the button's 28 × 28 icon.
const ICON_SCRIBBLE = 'M2.5 21.5C3.5 14.5 6.5 9 9 9.6C11.6 10.3 7.6 20.6 10.4 21.4C12.4 22 13.4 19.4 14.6 17.2';

/** "Alex" and a line under it, in a SIGNATURE_WIDTH × SIGNATURE_HEIGHT box. */
const SIGNATURE = [
  'M5 86C7 60 11 32 17 10C24 34 33 62 42 82',
  'M1 60C15 56 31 52 46 49',
  'M56 60C68 50 82 28 84 14C85 5 78 4 75 14C69 32 66 54 72 63C77 69 86 57 93 47C99 38 97 32 92 36C85 42 86 57 93 61C100 64 109 54 115 45C121 36 131 27 143 24C159 21 179 24 198 26',
  'M114 22C120 33 129 48 138 59',
  'M112 88C141 80 171 69 195 58C200 55 201 51 197 51',
];

type Mode = 'idle' | 'open' | 'done';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

function mix(from: number[], to: number[], t: number) {
  'worklet';
  const u = clamp01(t);
  return `rgb(${Math.round(from[0] + (to[0] - from[0]) * u)},${Math.round(from[1] + (to[1] - from[1]) * u)},${Math.round(from[2] + (to[2] - from[2]) * u)})`;
}

/** The button's centre and width, `open` of the way into the card and `dark` of the way to "Done". */
function buttonX(open: number, dark: number) {
  'worklet';
  // Beside the round button, the dark button sits left of the middle.
  return -((EDIT_GAP + EDIT) / 2) * dark * (1 - open);
}

function buttonWidth(open: number, dark: number) {
  'worklet';
  const closed = START_WIDTH + (DONE_WIDTH - START_WIDTH) * dark;
  return closed + (OPEN_WIDTH - closed) * open;
}

/** The pen touches the paper: a new stroke. */
function penDown(ink: SharedValue<number[][]>, x: number, y: number) {
  'worklet';
  const strokes = ink.get().slice();
  strokes.push([x, y]);
  ink.set(strokes);
}

/** The pen moves on the paper: the last stroke grows. */
function penMove(ink: SharedValue<number[][]>, x: number, y: number) {
  'worklet';
  const strokes = ink.get().slice();
  if (strokes.length === 0) return;
  const stroke = strokes[strokes.length - 1];
  const n = stroke.length;
  if (Math.hypot(x - stroke[n - 2], y - stroke[n - 1]) < INK_STEP) return;
  strokes[strokes.length - 1] = stroke.concat([x, y]);
  ink.set(strokes);
}

/** The strokes as one path: each a curve through the middles of its points, so it has no corners. */
function inkPath(strokes: number[][]) {
  'worklet';
  const path = Skia.PathBuilder.Make();
  for (let s = 0; s < strokes.length; s++) {
    const p = strokes[s];
    const n = p.length;
    path.moveTo(p[0], p[1]);
    // A tap leaves a dot.
    if (n === 2) path.lineTo(p[0] + 0.01, p[1]);
    for (let i = 2; i < n - 2; i += 2) path.quadTo(p[i], p[i + 1], (p[i] + p[i + 2]) / 2, (p[i + 1] + p[i + 3]) / 2);
    if (n > 2) path.lineTo(p[n - 2], p[n - 1]);
  }
  return path.build();
}

function write(text: string, color: string) {
  const paragraph = Skia.ParagraphBuilder.Make({})
    .pushStyle({ color: Skia.Color(color), fontFamilies: [FONT], fontSize: LABEL, fontStyle: { weight: LABEL_WEIGHT } })
    .addText(text)
    .build();
  paragraph.layout(STAGE_WIDTH);
  return paragraph;
}

type Word = { width: number; letters: { paragraph: SkParagraph; x: number }[] };

/** A word as one paragraph a letter, each where it sits in the whole word. */
function spell(text: string): Word {
  const whole = write(text, '#ffffff');
  const letters = text.split('').map((letter, index) => ({ paragraph: write(letter, '#ffffff'), x: whole.getRectsForRange(index, index + 1)[0]?.x ?? 0 }));
  return { width: whole.getLongestLine(), letters };
}

/**
 * What autoPlay writes, as samples of time, x, y and whether a stroke starts there: the pen moves
 * at one speed along every stroke and lifts for SIGN_LIFT between two, SIGN_FOR in all.
 */
function script(strokes: string[]) {
  const contours = strokes.flatMap((stroke) => {
    const path = Skia.Path.MakeFromSVGString(stroke);
    const contour = path ? Skia.ContourMeasureIter(path, false, 1).next() : null;
    return contour ? [contour] : [];
  });
  const length = contours.reduce((sum, contour) => sum + contour.length(), 0);
  const speed = length / (SIGN_FOR - SIGN_LIFT * (contours.length - 1));
  const samples: number[] = [];
  let time = 0;
  for (const contour of contours) {
    const steps = Math.max(1, Math.ceil(contour.length() / SIGNATURE_STEP));
    for (let i = 0; i <= steps; i++) {
      const distance = (contour.length() * i) / steps;
      const [point] = contour.getPosTan(distance);
      samples.push(time + distance / speed, point.x - SIGNATURE_WIDTH / 2, point.y - SIGNATURE_HEIGHT / 2 + SIGNATURE_Y, i === 0 ? 1 : 0);
    }
    time += contour.length() / speed + SIGN_LIFT;
  }
  return samples;
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type LetterProps = {
  paragraph: SkParagraph;
  x: number;
  /** Which word it belongs to: the one before the shared word at rest, the one in the card, or the one after. */
  word: 'start' | 'finish' | 'done';
  index: number;
  count: number;
  wording: SharedValue<number>;
  dark: SharedValue<number>;
};

/** One letter of a word that comes and goes: letters change one after the other, from the left. */
function Letter({ paragraph, x, word, index, count, wording, dark }: LetterProps) {
  const shown = useDerivedValue(() => {
    const span = 1 + LETTER_STAGGER * (count - 1);
    const finishing = clamp01(wording.get() * span - LETTER_STAGGER * index);
    const done = clamp01(dark.get() * span - LETTER_STAGGER * index);
    return word === 'start' ? 1 - finishing : word === 'finish' ? finishing * (1 - done) : done;
  });
  const blur = useDerivedValue(() => LETTER_BLUR * (1 - shown.get()));
  // The letter fades through the colour its layer is filled with.
  const veil = useDerivedValue(() => `rgba(255,255,255,${shown.get()})`);
  const transform = useDerivedValue(() => [{ translateY: LETTER_DROP * (1 - shown.get()) }]);
  return (
    <Group
      transform={transform}
      layer={
        <Paint>
          <Blur blur={blur} />
          <BlendColor color={veil} mode="srcIn" />
        </Paint>
      }
    >
      <Paragraph paragraph={paragraph} x={x} y={0} width={LABEL * 2} />
    </Group>
  );
}

/**
 * A signature field that starts as a "Start Signing" button. A press unfolds it into a dashed card
 * to sign in, the button dropping to the card's bottom as "Finish Signing"; finishing folds the card
 * back into a dark "Signing Done" button beside a round one that opens it again. The label changes
 * in place: the shared word slides, the other word blurs across letter by letter.
 */
export function UnfoldingSignaturePad({
  autoPlay = false,
  sharedWord = 'Signing',
  startWord = 'Start',
  finishWord = 'Finish',
  doneWord = 'Done',
  title = 'Sign',
  inkColor = '#111113',
  inkWidth = 3,
  background = '#ffffff',
  signature = SIGNATURE,
  onStart,
  onSign,
  onClear,
}: Props) {
  const insets = useSafeAreaInsets();
  const [mode, setMode] = useState<Mode>('idle');
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // 0 the button alone, 1 the card: a spring, so the card and the button overshoot.
  const open = useSharedValue(0);
  // 0 "Start", 1 "Finish"; and 0 light, 1 the dark "Done" button.
  const wording = useSharedValue(0);
  const dark = useSharedValue(0);
  // The strokes, each x, y, x, y… from the card's centre; and their opacity, for wiping them.
  const ink = useSharedValue<number[][]>([]);
  const inkShown = useSharedValue(1);
  // What is held down.
  const buttonPress = useSharedValue(0);
  const editPress = useSharedValue(0);
  const clearPress = useSharedValue(0);
  const closePress = useSharedValue(0);
  // autoPlay's pen: seconds into the signature (negative when it is not writing), and samples written.
  const pen = useSharedValue(-1);
  const written = useSharedValue(0);

  // The ink as it was when the pad opened, for the close button.
  const before = useSharedValue<number[][]>([]);

  const samples = useMemo(() => script(signature), [signature]);
  const label = useMemo(() => {
    const shared = write(sharedWord, '#ffffff');
    const start = spell(startWord);
    const finish = spell(finishWord);
    const done = spell(doneWord);
    const sharedWidth = shared.getLongestLine();
    // Each label is centred in the button: where its icon starts.
    const left = (icon: number, first: number, second: number) => -(icon + LABEL_GAP + first + WORD_GAP + second) / 2;
    return {
      shared,
      start,
      finish,
      done,
      sharedWidth,
      height: shared.getHeight(),
      startLeft: left(LABEL_ICON, start.width, sharedWidth),
      finishLeft: left(LABEL_ICON, finish.width, sharedWidth),
      doneLeft: left(LABEL_DISC, sharedWidth, done.width),
    };
  }, [sharedWord, startWord, finishWord, doneWord]);
  const heading = useMemo(() => {
    const paragraph = write(title, '#ffffff');
    return { paragraph, width: paragraph.getLongestLine(), height: paragraph.getHeight() };
  }, [title]);

  const hold = (press: SharedValue<number>, down: boolean) => press.set(withTiming(down ? 1 : 0, { duration: down ? PRESS_IN : PRESS_OUT }));

  const unfold = () => {
    before.set(ink.get().map((stroke) => stroke.slice()));
    setMode('open');
    open.set(withSpring(1, UNFOLD));
    wording.set(withTiming(1, { duration: WORDING }));
    dark.set(withTiming(0, { duration: DARKEN }));
    onStart?.();
  };

  /** Folds the card away: into the dark button if `strokes` holds ink, into "Start" if it is empty. */
  const fold = (strokes: number[][]) => {
    const signed = strokes.length > 0;
    setMode(signed ? 'done' : 'idle');
    open.set(withSpring(0, UNFOLD));
    if (signed) dark.set(withTiming(1, { duration: DARKEN }));
    else wording.set(withTiming(0, { duration: WORDING }));
    return signed;
  };

  /** The button in the card. A loop that plays by itself is silent. */
  const finish = (byItself: boolean) => {
    const strokes = ink.get().map((stroke) => stroke.slice());
    if (!fold(strokes)) return onClear?.();
    if (!byItself && process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    onSign?.(strokes);
  };

  /** The ink fades, then it is gone. */
  const erase = () => {
    inkShown.set(
      withTiming(0, { duration: INK_OUT }, (finished) => {
        if (!finished) return;
        ink.set([]);
        inkShown.set(1);
      }),
    );
  };

  const wipe = () => {
    erase();
    onClear?.();
  };

  /** The close button: the pad folds away with the ink it had when it opened. */
  const close = () => {
    cancelAnimation(inkShown);
    const strokes = before.get();
    ink.set(strokes);
    inkShown.set(1);
    fold(strokes);
  };

  /** Back to the closed "Start" button with no ink, from anywhere. */
  const reset = () => {
    cancelAnimation(pen);
    pen.set(-1);
    setMode('idle');
    open.set(withSpring(0, UNFOLD));
    wording.set(withTiming(0, { duration: WORDING }));
    dark.set(withTiming(0, { duration: DARKEN }));
    erase();
  };

  const sign = () => {
    written.set(0);
    pen.set(0);
    // The pencil stays a moment after the last stroke, then fades.
    pen.set(withTiming(SIGN_FOR + 0.4, { duration: (SIGN_FOR + 0.4) * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
  };

  // autoPlay's pen writes through the same two functions as a finger.
  useAnimatedReaction(
    () => pen.get(),
    (time) => {
      if (time < 0) return;
      let next = written.get();
      while (next * 4 < samples.length && samples[next * 4] <= time) {
        if (samples[next * 4 + 3] === 1) penDown(ink, samples[next * 4 + 1], samples[next * 4 + 2]);
        else penMove(ink, samples[next * 4 + 1], samples[next * 4 + 2]);
        next++;
      }
      if (next !== written.get()) written.set(next);
    },
  );

  useEffect(() => () => cancelAnimation(pen), [pen]);

  // The loop reads the latest handlers when its timers fire.
  const latest = useRef({ unfold, finish, wipe, reset, sign, hold });
  useEffect(() => {
    latest.current = { unfold, finish, wipe, reset, sign, hold };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, act: () => void) => timers.push(setTimeout(act, ms));
    const cycle = (origin: number) => {
      const at = (seconds: number, act: () => void) => after(origin + seconds * 1000 - Date.now(), act);
      const press = (seconds: number, held: SharedValue<number>, act: () => void) => {
        at(seconds, () => latest.current.hold(held, true));
        at(seconds + PRESS_FOR, () => {
          latest.current.hold(held, false);
          act();
        });
      };
      press(START_AT, buttonPress, () => latest.current.unfold());
      at(SIGN_AT, () => latest.current.sign());
      press(FINISH_AT, buttonPress, () => latest.current.finish(true));
      press(EDIT_AT, editPress, () => latest.current.unfold());
      press(CLEAR_AT, clearPress, () => latest.current.wipe());
      press(FOLD_AT, buttonPress, () => latest.current.finish(true));
      at(LOOP, () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else {
      after(USER_HOLD * 1000, () => {
        latest.current.reset();
        after(RESET_FOR * 1000, () => cycle(Date.now()));
      });
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, buttonPress, editPress, clearPress]);

  /** A touch takes over from the autoPlay loop, which starts again from the closed button a while later. */
  const touched = () => {
    cancelAnimation(pen);
    pen.set(-1);
    [buttonPress, editPress, clearPress, closePress].forEach((press) => hold(press, false));
    setTouches((n) => n + 1);
  };

  const draw = Gesture.Pan()
    .minDistance(0)
    .enabled(mode === 'open')
    .onBegin((event) => {
      scheduleOnRN(touched);
      penDown(ink, event.x - CARD_WIDTH / 2 + PAD_INSET, event.y + PAD_TOP);
    })
    .onChange((event) => {
      penMove(ink, event.x - CARD_WIDTH / 2 + PAD_INSET, event.y + PAD_TOP);
    });

  const pressScale = useDerivedValue(() => 1 - PRESS_SCALE * buttonPress.get());
  const pill = useDerivedValue(() => {
    const o = open.get();
    const k = dark.get();
    const width = buttonWidth(o, k) * pressScale.get();
    const height = BUTTON * pressScale.get();
    return rrect(rect(buttonX(o, k) - width / 2, DROP * o - height / 2, width, height), height / 2, height / 2);
  });
  const shadow = useDerivedValue(() => {
    const o = open.get();
    const k = dark.get();
    const width = buttonWidth(o, k) - 16;
    return rrect(rect(buttonX(o, k) - width / 2, DROP * o - BUTTON / 2 + 10, width, BUTTON - 8), BUTTON / 2, BUTTON / 2);
  });
  const fill = useDerivedValue(() => mix(LIGHT, DARK, dark.get()));
  // The dashed card grows out of the button: up to its top, and out by the inset on the other sides.
  const outline = useDerivedValue(() => {
    const o = open.get();
    const k = dark.get();
    const width = buttonWidth(o, k) + INSET * 2 * o;
    const top = -BUTTON / 2 + (-CARD_HEIGHT / 2 + BUTTON / 2) * o;
    const bottom = DROP * o + BUTTON / 2 + INSET * o;
    const radius = BUTTON / 2 + (CARD_RADIUS - BUTTON / 2) * clamp01(o);
    return rrect(rect(buttonX(o, k) - width / 2, top, width, Math.max(BUTTON, bottom - top)), radius, radius);
  });
  const outlineOpacity = useDerivedValue(() => smooth(open.get(), 0.12, 0.6));
  const headerOpacity = useDerivedValue(() => smooth(open.get(), 0.45, 0.95));
  const titleTint = useDerivedValue(() => `rgba(${TITLE[0]},${TITLE[1]},${TITLE[2]},${headerOpacity.get()})`);
  const clearTransform = useDerivedValue(() => [{ translateX: -CARD_WIDTH / 2 + HEADER_INSET }, { translateY: HEADER_Y }, { scale: 1 - 0.18 * clearPress.get() }, { rotate: clearPress.get() * 0.5 }]);
  const closeTransform = useDerivedValue(() => [{ translateX: CARD_WIDTH / 2 - HEADER_INSET }, { translateY: HEADER_Y }, { scale: 1 - 0.18 * closePress.get() }]);

  // The round button slides out from behind the dark button's right end.
  const editTransform = useDerivedValue(() => {
    const o = open.get();
    const k = dark.get();
    const out = clamp01(k * (1 - o));
    const end = buttonX(o, k) + buttonWidth(o, k) / 2;
    return [{ translateX: end - EDIT / 2 - 4 + (EDIT + EDIT_GAP + 4) * out }, { translateY: DROP * o }, { scale: 1 - PRESS_SCALE * 1.5 * editPress.get() }];
  });
  const editOpacity = useDerivedValue(() => smooth(dark.get() * (1 - open.get()), 0.15, 0.7));

  const labelTransform = useDerivedValue(() => {
    const o = open.get();
    return [{ translateX: buttonX(o, dark.get()) }, { translateY: DROP * o }, { scale: pressScale.get() }];
  });
  const tint = useDerivedValue(() => mix(TEXT, WHITE, smooth(dark.get(), 0.1, 0.6)));
  // Where the label's icon starts: the three labels are centred, so it moves as the words change.
  const iconX = useDerivedValue(
    () => label.startLeft + (label.finishLeft - label.startLeft) * wording.get() + (label.doneLeft - label.finishLeft) * dark.get(),
  );
  const firstTransform = useDerivedValue(() => [{ translateX: iconX.get() + LABEL_ICON + LABEL_GAP }, { translateY: -label.height / 2 }]);
  // The shared word slides from behind the first word to behind the disc.
  const sharedX = useDerivedValue(() => {
    const first = label.start.width + (label.finish.width - label.start.width) * wording.get();
    const afterFirst = iconX.get() + LABEL_ICON + LABEL_GAP + first + WORD_GAP;
    const afterDisc = iconX.get() + LABEL_DISC + LABEL_GAP;
    // It waits for the first letters to go.
    return afterFirst + (afterDisc - afterFirst) * smooth(dark.get(), 0.15, 1);
  });
  const sharedTransform = useDerivedValue(() => [{ translateX: sharedX.get() }, { translateY: -label.height / 2 }]);
  const lastTransform = useDerivedValue(() => [{ translateX: sharedX.get() + label.sharedWidth + WORD_GAP }, { translateY: -label.height / 2 }]);
  const penTransform = useDerivedValue(() => [{ translateX: iconX.get() }, { translateY: -LABEL_ICON / 2 }]);
  const penOpacity = useDerivedValue(() => 1 - smooth(dark.get(), 0, 0.5));
  const discTransform = useDerivedValue(() => [{ translateX: iconX.get() + LABEL_DISC / 2 }, { scale: 0.5 + 0.5 * smooth(dark.get(), 0.3, 1) }]);
  const discOpacity = useDerivedValue(() => smooth(dark.get(), 0.3, 0.9));

  const strokes = useDerivedValue(() => inkPath(ink.get()));
  // The ink keeps its place in the card, and shrinks into the button as the card folds.
  const inkTransform = useDerivedValue(() => {
    const o = open.get();
    return [{ translateX: buttonX(o, dark.get()) }, { translateY: DROP * o }, { scale: INK_FOLDED + (1 - INK_FOLDED) * o }, { translateY: -DROP }];
  });
  const inkOpacity = useDerivedValue(() => inkShown.get() * smooth(open.get(), 0.1, 0.7));

  // The pencil that writes for autoPlay glides between the samples on either side of the clock.
  const pencilTransform = useDerivedValue(() => {
    const time = pen.get();
    const next = Math.min(written.get(), samples.length / 4 - 1);
    const last = Math.max(0, next - 1);
    const span = samples[next * 4] - samples[last * 4];
    const u = span > 0 ? clamp01((time - samples[last * 4]) / span) : 1;
    const x = samples[last * 4 + 1] + (samples[next * 4 + 1] - samples[last * 4 + 1]) * u;
    const y = samples[last * 4 + 2] + (samples[next * 4 + 2] - samples[last * 4 + 2]) * u;
    return [{ translateX: x - 3 }, { translateY: y - 21 }];
  });
  const pencilOpacity = useDerivedValue(() => smooth(pen.get(), 0, 0.15) * (1 - smooth(pen.get(), SIGN_FOR + 0.1, SIGN_FOR + 0.4)));

  const centre = [{ translateX: STAGE_WIDTH / 2 }, { translateY: STAGE_HEIGHT / 2 }];
  const pressable = (held: SharedValue<number>, act: () => void) => ({
    onPressIn: () => {
      touched();
      hold(held, true);
    },
    onPressOut: () => hold(held, false),
    onPress: () => {
      tick();
      act();
    },
  });

  return (
    <View style={[styles.screen, { backgroundColor: background, paddingTop: insets.top, paddingBottom: insets.bottom }]}>
      <StatusBar style="dark" />
      <View style={styles.stage}>
        <Canvas style={StyleSheet.absoluteFill}>
          <Group transform={centre}>
            <RoundedRect rect={outline} style="stroke" strokeWidth={OUTLINE} color={OUTLINE_COLOR} opacity={outlineOpacity}>
              <DashPathEffect intervals={DASH} />
            </RoundedRect>

            <Group opacity={headerOpacity}>
              <Group transform={clearTransform}>
                <Path path={ICON_CLEAR} color={HEADER_COLOR} transform={[{ translateX: -13 }, { translateY: -13 }, { scale: 26 / 24 }]} />
              </Group>
              <Group transform={closeTransform}>
                <Circle cx={0} cy={0} r={HEADER_ICON / 2} color={HEADER_COLOR} />
                <Path path={ICON_CROSS} style="stroke" strokeWidth={2.6} strokeCap="round" color="#ffffff" transform={[{ translateX: -12 }, { translateY: -12 }]} />
              </Group>
            </Group>
            {/* Text takes its colour and its opacity from a layer; a layer inside a group that has an
                opacity leaves that opacity on everything drawn after it, so this one stands alone. */}
            <Group
              layer={
                <Paint>
                  <BlendColor color={titleTint} mode="srcIn" />
                </Paint>
              }
            >
              <Paragraph paragraph={heading.paragraph} x={-heading.width / 2} y={HEADER_Y - heading.height / 2} width={heading.width + 2} />
            </Group>

            <Group transform={inkTransform} opacity={inkOpacity}>
              <Path path={strokes} style="stroke" strokeWidth={inkWidth} strokeCap="round" strokeJoin="round" color={inkColor} />
              <Group transform={pencilTransform} opacity={pencilOpacity}>
                <Path path={ICON_PENCIL} color={POINTER} />
              </Group>
            </Group>

            <Group transform={editTransform} opacity={editOpacity}>
              <Circle cx={0} cy={0} r={EDIT / 2} color={LIGHT_COLOR} />
              <Path path={ICON_PENCIL} color={DARK_COLOR} transform={[{ translateX: -12 }, { translateY: -12 }]} />
            </Group>

            <RoundedRect rect={shadow} color="rgba(40,40,70,0.07)">
              <BlurMask blur={10} style="normal" />
            </RoundedRect>
            <RoundedRect rect={pill} color={fill} />

            <Group transform={labelTransform}>
              <Group
                layer={
                  <Paint>
                    <BlendColor color={tint} mode="srcIn" />
                  </Paint>
                }
              >
                <Group transform={penTransform} opacity={penOpacity}>
                  <Path path={ICON_SCRIBBLE} style="stroke" strokeWidth={2.6} strokeCap="round" strokeJoin="round" color="#ffffff" />
                  <Path path={ICON_PENCIL} color="#ffffff" transform={[{ translateX: 11.5 }, { translateY: 1.5 }, { scale: 0.68 }]} />
                </Group>
                <Group transform={firstTransform}>
                  {label.start.letters.map((letter, index) => (
                    <Letter key={`start-${index}`} {...letter} word="start" index={index} count={label.start.letters.length} wording={wording} dark={dark} />
                  ))}
                  {label.finish.letters.map((letter, index) => (
                    <Letter key={`finish-${index}`} {...letter} word="finish" index={index} count={label.finish.letters.length} wording={wording} dark={dark} />
                  ))}
                </Group>
                <Group transform={sharedTransform}>
                  <Paragraph paragraph={label.shared} x={0} y={0} width={label.sharedWidth + 2} />
                </Group>
                <Group transform={lastTransform}>
                  {label.done.letters.map((letter, index) => (
                    <Letter key={`done-${index}`} {...letter} word="done" index={index} count={label.done.letters.length} wording={wording} dark={dark} />
                  ))}
                </Group>
              </Group>
              <Group transform={discTransform} opacity={discOpacity}>
                <Circle cx={0} cy={0} r={LABEL_DISC / 2} color="#ffffff" />
                <Path path={ICON_CHECK} style="stroke" strokeWidth={2.6} strokeCap="round" strokeJoin="round" color={DARK_COLOR} transform={[{ translateX: -12 }, { translateY: -12 }]} />
              </Group>
            </Group>
          </Group>
        </Canvas>

        {mode === 'open' && (
          <GestureDetector gesture={draw}>
            <View style={styles.pad} accessibilityLabel={`${title}: draw your signature here`} />
          </GestureDetector>
        )}
        {mode === 'open' && (
          <>
            <Pressable style={[styles.hit, styles.clear]} accessibilityRole="button" accessibilityLabel="Clear" {...pressable(clearPress, wipe)} />
            <Pressable style={[styles.hit, styles.close]} accessibilityRole="button" accessibilityLabel="Close" {...pressable(closePress, close)} />
            <Pressable
              style={[styles.button, styles.buttonOpen]}
              accessibilityRole="button"
              accessibilityLabel={`${finishWord} ${sharedWord}`}
              {...pressable(buttonPress, () => finish(false))}
            />
          </>
        )}
        {mode === 'idle' && (
          <Pressable style={[styles.button, styles.buttonStart]} accessibilityRole="button" accessibilityLabel={`${startWord} ${sharedWord}`} {...pressable(buttonPress, unfold)} />
        )}
        {mode === 'done' && (
          <>
            <View style={[styles.button, styles.buttonDone]} accessible accessibilityLabel={`${sharedWord} ${doneWord}`} />
            <Pressable style={[styles.hit, styles.edit]} accessibilityRole="button" accessibilityLabel="Edit signature" {...pressable(editPress, unfold)} />
          </>
        )}
      </View>
    </View>
  );
}

const MIDDLE_X = STAGE_WIDTH / 2;
const MIDDLE_Y = STAGE_HEIGHT / 2;
// The dark button and the round one beside it, centred as a pair.
const DONE_LEFT = MIDDLE_X - (DONE_WIDTH + EDIT_GAP + EDIT) / 2;

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stage: {
    width: STAGE_WIDTH,
    height: STAGE_HEIGHT,
  },
  pad: {
    position: 'absolute',
    left: STAGE_MARGIN + PAD_INSET,
    top: MIDDLE_Y + PAD_TOP,
    width: CARD_WIDTH - PAD_INSET * 2,
    height: PAD_BOTTOM - PAD_TOP,
  },
  hit: {
    position: 'absolute',
    width: HIT,
    height: HIT,
  },
  clear: {
    left: STAGE_MARGIN + HEADER_INSET - HIT / 2,
    top: MIDDLE_Y + HEADER_Y - HIT / 2,
  },
  close: {
    left: STAGE_MARGIN + CARD_WIDTH - HEADER_INSET - HIT / 2,
    top: MIDDLE_Y + HEADER_Y - HIT / 2,
  },
  button: {
    position: 'absolute',
    height: BUTTON,
  },
  buttonStart: {
    left: MIDDLE_X - START_WIDTH / 2,
    top: MIDDLE_Y - BUTTON / 2,
    width: START_WIDTH,
  },
  buttonOpen: {
    left: MIDDLE_X - OPEN_WIDTH / 2,
    top: MIDDLE_Y + DROP - BUTTON / 2,
    width: OPEN_WIDTH,
  },
  buttonDone: {
    left: DONE_LEFT,
    top: MIDDLE_Y - BUTTON / 2,
    width: DONE_WIDTH,
  },
  edit: {
    left: DONE_LEFT + DONE_WIDTH + EDIT_GAP,
    top: MIDDLE_Y - EDIT / 2,
    width: EDIT,
    height: EDIT,
  },
});
