import { BlurStyle, Canvas, PaintStyle, Picture, type SkCanvas, type SkPaint, Skia, StrokeCap, createPicture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { type ReactNode, useEffect, useEffectEvent, useRef, useState } from 'react';
import { type GestureResponderEvent, Pressable, type StyleProp, StyleSheet, Text, TextInput, View, type ViewStyle } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnUI } from 'react-native-worklets';

type Props = {
  /** Controlled text. Leave it out and the component keeps its own. */
  value?: string;
  /** Opens the pill, types the script, sends it and starts over. */
  autoPlay?: boolean;
  placeholder?: string;
  /** The question autoPlay types. */
  script?: string;
  /** Fill of the send button. */
  sendColor?: string;
  onChangeText?: (text: string) => void;
  /** Called with the question when the arrow or the return key is pressed. */
  onSend?: (text: string) => void;
  onOpen?: () => void;
  onClose?: () => void;
  onImage?: () => void;
  onLens?: () => void;
  onMic?: () => void;
};

const WIDTH = 350;
const HEIGHT = 170;

// The three shapes the outline takes, all centred in the box.
const PILL_WIDTH = 330;
const PILL_HEIGHT = 64;
const CARD_WIDTH = 330;
const CARD_HEIGHT = 116;
const CARD_RADIUS = 24;
const LINE_WIDTH = 290;
const LINE_HEIGHT = 2.5;
// How far the middle of the line rises as it fades.
const LINE_BOW = 9;
const GLOW_WIDTH = 8;
const GLOW_BLUR = 6;
const SHADOW_BLUR = 14;
const SHADOW_DROP = 8;
const STROKE = 2.5;

// Centre of the search icon from the pill's left end.
const ICON_X = 44;
const TEXT_X = 20;
// Centre of the tools from the card's bottom edge.
const TOOL_Y = 34;
const FONT_SIZE = 16;
const FIELD_WIDTH = CARD_WIDTH - TEXT_X - 56;
const FIELD_HEIGHT = 24;
const INK = '#1f1f1f';
const CARET = '#4285f4';

// Positions on the outline run 0…1 from the right end of the top edge, leftwards, down the left
// side, back along the bottom and up the right side.
const RIBBON_FROM = 0.27;
const RIBBON_TO = 0.86;
const RIBBON_PIECES = 72;
// How much of the colour wheel the ribbon shows at once; the wheel turns once per loop.
const RAMP_SPAN = 0.9;
const STREAK_FROM = 0.62;
const STREAK_LENGTH = 0.24;
const STREAK_PIECES = 30;
// Whole laps of the streak per loop, so the loop closes.
const STREAK_LAPS = 4;

// autoPlay (seconds). Keys land `KEY` apart on average, stretched by `RHYTHM` in turn.
const OPEN_AT = 0.8;
// From the tap on the pill to the first key.
const TYPE_AFTER = 0.75;
const KEY = 0.052;
const RHYTHM = [1.0, 0.72, 1.3, 0.85, 1.12, 0.64, 1.42, 0.95];
// From the last key to the tap on the arrow.
const SEND_AFTER = 0.85;
const LOOP = 7.6;
// After the field is left empty, the card closes and the script waits this long.
const RESUME_AFTER = 3.0;

const BLINK = 0.53;
const PRESS = 0.12;
const OPEN_TIME = 0.45;
const FOLD_TIME = 0.36;
const GONE_TIME = 0.55;
// From the line fading out to the outline starting to draw.
const DRAW_AFTER = 0.15;
const DRAW_TIME = 0.95;

// Reduced motion is handled here (the morph becomes a fade), so no animation leaves it to the system switch.
const KEEP = ReduceMotion.Never;
// Strong ease-out used for every state change.
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.42, 0, 0.58, 1);
// Opens past its size and settles back.
const SNAP = Easing.bezier(0.34, 1.4, 0.64, 1);
const SWEEP = Easing.bezier(0.3, 0, 0.2, 1);
const CARET_FADE = { duration: 80, easing: Easing.bezier(0, 0, 0.58, 1), reduceMotion: KEEP };
const CSS_EASE_OUT = cubicBezier(0, 0, 0.58, 1);
const PRESS_EASE = cubicBezier(0.23, 1, 0.32, 1);

type Tone = readonly [number, number, number];

const GREEN: Tone = [52, 168, 83];
const YELLOW: Tone = [251, 188, 4];
const RED: Tone = [234, 67, 53];
const VIOLET: Tone = [161, 66, 244];
const BLUE: Tone = [66, 133, 244];
const WHITE: Tone = [255, 255, 255];
const BLACK: Tone = [0, 0, 0];
// The card's resting border.
const PALE: Tone = [201, 215, 245];
const WHEEL = [GREEN, YELLOW, RED, VIOLET, BLUE];

// Icons, as outlines in a 20 pt box (the magnifying glass and its sparkle in 28 pt).
const GLASS_ICON =
  'M19.47 13.65 C19.2 16.73 17.07 19.33 14.1 20.2 C11.13 21.06 7.93 20.02 6.05 17.56 C4.17 15.11 3.98 11.75 5.59 9.11 ' +
  'C7.19 6.46 10.25 5.07 13.3 5.61 M17.6 18.6 L23.4 24.4';
const SPARKLE_ICON =
  'M21 1.5 C21.6 4.57 23.43 6.4 26.5 7 C23.43 7.6 21.6 9.43 21 12.5 C20.4 9.43 18.57 7.6 15.5 7 C18.57 6.4 20.4 4.57 21 1.5 Z';
const CLOSE_ICON = 'M5.5 5.5 L14.5 14.5 M14.5 5.5 L5.5 14.5';
const IMAGE_ICON =
  'M5 3.5 L15 3.5 C15.83 3.5 16.5 4.17 16.5 5 L16.5 15 C16.5 15.83 15.83 16.5 15 16.5 L5 16.5 C4.17 16.5 3.5 15.83 3.5 15 ' +
  'L3.5 5 C3.5 4.17 4.17 3.5 5 3.5 Z M3.5 13.5 L7 10 L10 13 L12.5 10.5 L16.5 14.5';
const LENS_ICON =
  'M3.5 7 L3.5 5.5 C3.5 4.4 4.4 3.5 5.5 3.5 L7 3.5 M13 3.5 L14.5 3.5 C15.6 3.5 16.5 4.4 16.5 5.5 L16.5 7 ' +
  'M16.5 13 L16.5 14.5 C16.5 15.6 15.6 16.5 14.5 16.5 L13 16.5 M7 16.5 L5.5 16.5 C4.4 16.5 3.5 15.6 3.5 14.5 L3.5 13 ' +
  'M12.6 10 C12.6 11.44 11.44 12.6 10 12.6 C8.56 12.6 7.4 11.44 7.4 10 C7.4 8.56 8.56 7.4 10 7.4 C11.44 7.4 12.6 8.56 12.6 10 Z';
const MIC_ICON =
  'M10 2.5 C11.38 2.5 12.5 3.62 12.5 5 L12.5 9.5 C12.5 10.88 11.38 12 10 12 C8.62 12 7.5 10.88 7.5 9.5 L7.5 5 ' +
  'C7.5 3.62 8.62 2.5 10 2.5 Z M5 9.5 C5 12.26 7.24 14.5 10 14.5 C12.76 14.5 15 12.26 15 9.5 M10 14.5 L10 17.5';
const ARROW_ICON = 'M10 15.5 L10 4.5 M5.5 9 L10 4.5 L14.5 9';

function mix(from: Tone, to: Tone, t: number): Tone {
  'worklet';
  return [from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t, from[2] + (to[2] - from[2]) * t];
}

function color(tone: Tone, alpha: number) {
  'worklet';
  return Float32Array.of(tone[0] / 255, tone[1] / 255, tone[2] / 255, alpha);
}

/** The colour wheel at `x` turns: green, yellow, red, violet, blue and round to green again. */
function ramp(x: number) {
  'worklet';
  const at = (x - Math.floor(x)) * WHEEL.length;
  const index = Math.min(WHEEL.length - 1, Math.floor(at));
  return mix(WHEEL[index], WHEEL[(index + 1) % WHEEL.length], at - index);
}

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(from: number, to: number, x: number) {
  'worklet';
  const t = clamp01((x - from) / (to - from));
  return t * t * (3 - 2 * t);
}

function lerp(from: number, to: number, t: number) {
  'worklet';
  return from + (to - from) * t;
}

/** Under reduced motion the shape jumps half way through a fade instead of morphing. */
function shapeOf(o: number, still: boolean) {
  'worklet';
  if (!still) return o;
  return o < 0.5 ? 0 : 1;
}

/** The fade that covers that jump: everything is gone when the shape changes. */
function veilOf(o: number, still: boolean) {
  'worklet';
  return still ? Math.abs(2 * o - 1) : 1;
}

type Box = { w: number; h: number; r: number };

/** The outline `o` of the way from pill to card and `f` of the way from there to the flat line. */
function outlineBox(o: number, f: number): Box {
  'worklet';
  const w = lerp(lerp(PILL_WIDTH, CARD_WIDTH, o), LINE_WIDTH, f);
  const h = lerp(lerp(PILL_HEIGHT, CARD_HEIGHT, o), LINE_HEIGHT, f);
  const r = lerp(lerp(PILL_HEIGHT / 2, CARD_RADIUS, o), LINE_HEIGHT / 2, f);
  return { w, h, r: Math.max(0.01, Math.min(r, h / 2, w / 2)) };
}

/** The point `u` (in turns) of the way round a rounded rectangle centred in the box. */
function outlinePoint(u: number, box: Box) {
  'worklet';
  const r = box.r;
  const across = Math.max(0, box.w - 2 * r);
  const down = Math.max(0, box.h - 2 * r);
  const corner = (Math.PI / 2) * r;
  const left = WIDTH / 2 - box.w / 2;
  const right = WIDTH / 2 + box.w / 2;
  const top = HEIGHT / 2 - box.h / 2;
  const bottom = HEIGHT / 2 + box.h / 2;
  let s = (u - Math.floor(u)) * (2 * across + 2 * down + 4 * corner);
  if (s < across) return { x: right - r - s, y: top };
  s -= across;
  if (s < corner) return { x: left + r - r * Math.sin(s / r), y: top + r - r * Math.cos(s / r) };
  s -= corner;
  if (s < down) return { x: left, y: top + r + s };
  s -= down;
  if (s < corner) return { x: left + r - r * Math.cos(s / r), y: bottom - r + r * Math.sin(s / r) };
  s -= corner;
  if (s < across) return { x: left + r + s, y: bottom };
  s -= across;
  if (s < corner) return { x: right - r + r * Math.sin(s / r), y: bottom - r + r * Math.cos(s / r) };
  s -= corner;
  if (s < down) return { x: right, y: bottom - r - s };
  s -= down;
  return { x: right - r + r * Math.cos(s / r), y: top + r - r * Math.sin(s / r) };
}

/** Strokes the outline between two positions in short pieces, each in the colour and opacity `tone` gives for its place (0…1) along the stretch. */
function ribbon(
  canvas: SkCanvas,
  pen: SkPaint,
  box: Box,
  from: number,
  to: number,
  pieces: number,
  tone: (q: number) => { tint: Tone; alpha: number },
) {
  'worklet';
  const step = (to - from) / pieces;
  for (let index = 0; index < pieces; index++) {
    const { tint, alpha } = tone((index + 0.5) / pieces);
    if (alpha <= 0.004) continue;
    const start = outlinePoint(from + step * index, box);
    // A hair longer than its share, so no seam shows between two pieces.
    const end = outlinePoint(from + step * (index + 1.08), box);
    pen.setColor(color(tint, alpha));
    canvas.drawLine(start.x, start.y, end.x, end.y, pen);
  }
}

/** The outline and everything made of light: shadow and fill of the card, the folded line, the two ribbons. */
function paint(canvas: SkCanvas, time: number, o: number, f: number, gone: number, drawn: number, veil: number) {
  'worklet';
  const box = outlineBox(o, f);
  const centreX = WIDTH / 2;
  const centreY = HEIGHT / 2;
  const left = centreX - box.w / 2;
  const top = centreY - box.h / 2;
  const card = clamp01(o) * veil;
  const folded = clamp01(f);

  if (card > 0 && gone <= 0) {
    const outline = { rect: { x: left, y: top, width: box.w, height: box.h }, rx: box.r, ry: box.r };
    const shadow = Skia.Paint();
    shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, SHADOW_BLUR, true));
    shadow.setColor(color(BLACK, 0.1 * card * (1 - folded)));
    canvas.drawRRect({ ...outline, rect: { ...outline.rect, y: top + SHADOW_DROP } }, shadow);
    // The white card takes the colour of the line it folds into.
    const fill = Skia.Paint();
    fill.setColor(color(mix(WHITE, BLUE, folded), card));
    canvas.drawRRect(outline, fill);
    const border = Skia.Paint();
    border.setStyle(PaintStyle.Stroke);
    border.setStrokeWidth(1.5);
    border.setColor(color(PALE, card * (1 - folded)));
    canvas.drawRRect(outline, border);
  }

  // The folded card is a line of light: it glows, bows upwards and fades.
  if (folded > 0) {
    const line = Skia.Path.Make();
    line.moveTo(left, centreY);
    line.quadTo(centreX, centreY - LINE_BOW * 2 * gone, left + box.w, centreY);
    const glow = Skia.Paint();
    glow.setStyle(PaintStyle.Stroke);
    glow.setStrokeWidth(GLOW_WIDTH);
    glow.setStrokeCap(StrokeCap.Round);
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, GLOW_BLUR, true));
    glow.setColor(color(BLUE, 0.6 * folded * (1 - gone) * veil));
    canvas.drawPath(line, glow);
    if (gone > 0) {
      const core = Skia.Paint();
      core.setStyle(PaintStyle.Stroke);
      core.setStrokeWidth(LINE_HEIGHT);
      core.setStrokeCap(StrokeCap.Round);
      core.setColor(color(BLUE, (1 - gone) * veil));
      canvas.drawPath(line, core);
    }
  }

  const pen = Skia.Paint();
  pen.setStyle(PaintStyle.Stroke);
  pen.setStrokeWidth(STROKE);

  // The pill: a ribbon over the top left, round the left cap and along the bottom, fading to the right.
  const closed = (1 - clamp01(o * 2.5)) * veil;
  if (closed > 0 && drawn > 0) {
    // It is drawn from its right end; `front` is how far the drawing has come.
    const front = 1 - drawn * 1.1;
    const lead = Math.min(1, (1 - drawn) * 4);
    ribbon(canvas, pen, box, RIBBON_FROM, RIBBON_TO, RIBBON_PIECES, (q) => {
      const rest = smooth(0, 0.14, q) * (1 - smooth(0.45, 1, q));
      // While it is drawn, a bright head runs ahead of the faded ribbon it leaves behind.
      const head = lead * (1 - smooth(0, 0.3, q - front));
      return { tint: ramp(q * RAMP_SPAN - time / LOOP), alpha: Math.max(rest, head) * clamp01((q - front) / 0.1) * closed };
    });
  }

  // The card: a green, yellow and red streak travelling round the pale border.
  const streak = card * (1 - folded);
  if (streak > 0) {
    const from = STREAK_FROM - (STREAK_LAPS * time) / LOOP;
    ribbon(canvas, pen, box, from, from + STREAK_LENGTH, STREAK_PIECES, (q) => ({
      tint: q < 0.5 ? mix(GREEN, YELLOW, q * 2) : mix(YELLOW, RED, q * 2 - 1),
      alpha: smooth(0, 0.3, q) * (1 - smooth(0.7, 1, q)) * streak,
    }));
  }
}

type Act = { kind: 'open' } | { kind: 'type'; text: string } | { kind: 'press' } | { kind: 'send' };

/** What autoPlay does in one loop, and when (seconds from the loop's start). The last step sends. */
function scriptSteps(script: string) {
  const steps: { at: number; act: Act }[] = [{ at: OPEN_AT, act: { kind: 'open' } }];
  let at = OPEN_AT + TYPE_AFTER;
  let typed = '';
  Array.from(script).forEach((character, index) => {
    typed += character;
    steps.push({ at, act: { kind: 'type', text: typed } });
    at += KEY * RHYTHM[index % RHYTHM.length];
  });
  at += SEND_AFTER;
  steps.push({ at: at - PRESS, act: { kind: 'press' } }, { at, act: { kind: 'send' } });
  return steps;
}

/**
 * Whether a touch in the pill's box misses the pill itself: it landed in a corner outside one of its
 * round ends. A press that comes without a place, as a screen reader's does, never misses.
 */
function offPill(x: number, y: number) {
  const r = PILL_HEIGHT / 2;
  const nearest = Math.min(Math.max(x, r), PILL_WIDTH - r);
  return Math.hypot(x - nearest, y - r) > r;
}

type Motion = {
  open: SharedValue<number>;
  fold: SharedValue<number>;
  gone: SharedValue<number>;
  draw: SharedValue<number>;
  clock: SharedValue<number>;
  still: boolean;
};

/** The canvas under the views: the whole picture is recorded again for every frame. */
function Light({ open, fold, gone, draw, clock, still }: Motion) {
  const picture = useDerivedValue(() => {
    const o = open.get();
    const f = fold.get();
    const away = gone.get();
    const drawn = draw.get();
    const time = still ? 0 : clock.get();
    return createPicture((canvas) => paint(canvas, time, shapeOf(o, still), f, away, drawn, veilOf(o, still)), {
      width: WIDTH,
      height: HEIGHT,
    });
  });

  return (
    <Canvas style={styles.canvas}>
      <Picture picture={picture} />
    </Canvas>
  );
}

function Glyph({ d, tint, width }: { d: string; tint: string; width: number }) {
  return (
    <Svg width={20} height={20} viewBox="0 0 20 20">
      <Path d={d} stroke={tint} strokeWidth={width} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

/** A button whose content sinks a little while it is held. */
function Press({
  style,
  label,
  onPress,
  children,
}: {
  style: StyleProp<ViewStyle>;
  label: string;
  onPress: () => void;
  children: ReactNode;
}) {
  return (
    <Pressable style={style} accessibilityRole="button" accessibilityLabel={label} onPress={onPress}>
      {({ pressed }) => (
        <Animated.View
          style={{
            transform: [{ scale: pressed ? 0.92 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: PRESS_EASE,
          }}
        >
          {children}
        </Animated.View>
      )}
    </Pressable>
  );
}

function Tool({
  style,
  icon,
  tint,
  label,
  onPress,
}: {
  style: StyleProp<ViewStyle>;
  icon: string;
  tint: string;
  label: string;
  onPress: () => void;
}) {
  return (
    <Press style={style} label={label} onPress={onPress}>
      <View style={styles.tool}>
        <Glyph d={icon} tint={tint} width={1.7} />
      </View>
    </Press>
  );
}

function Caret({ visible, empty, on }: { visible: boolean; empty: boolean; on: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ opacity: visible ? on.get() : 0 }));
  // Before the placeholder's first letter, not on it.
  return <Animated.View style={[styles.caret, { transform: [{ translateX: empty ? -2 : 1 }] }, style]} />;
}

/**
 * A search pill drawn as a partial rainbow outline, with colours that slide along it. A tap snaps it
 * open into an input card whose border carries a coloured streak round and round. On send the card
 * folds flat into a line of light that bows and fades, and the outline is drawn again from the right.
 */
export function RainbowOutlineSearch({
  value,
  autoPlay = false,
  placeholder = 'Ask anything',
  script = 'Best hikes near Lisbon in May',
  sendColor = '#2f5fd0',
  onChangeText,
  onSend,
  onOpen,
  onClose,
  onImage,
  onLens,
  onMic,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = value != null;
  const field = useRef<TextInput>(null);

  const [inner, setInner] = useState('');
  const [playing, setPlaying] = useState(autoPlay && !controlled);
  const [focused, setFocused] = useState(false);
  // The arrow is held down by the script.
  const [pressed, setPressed] = useState(false);
  const [opened, setOpened] = useState(false);
  // The card is folding or the pill is being drawn: taps wait.
  const [busy, setBusy] = useState(false);

  // 0 pill, 1 card.
  const open = useSharedValue(0);
  // 0 card, 1 flat line.
  const fold = useSharedValue(0);
  // How far the line has bowed and faded.
  const gone = useSharedValue(0);
  // How much of the pill's outline is drawn.
  const draw = useSharedValue(1);
  // Seconds the colours and the streak have been running.
  const clock = useSharedValue(0);
  const caretOn = useSharedValue(1);

  const text = value ?? inner;
  const ready = text.trim() !== '';

  useFrameCallback((frame) => {
    clock.set(clock.get() + (frame.timeSincePreviousFrame ?? 0) / 1000);
  }, !reducedMotion);

  const edit = (next: string) => {
    if (next === text) return;
    if (!controlled) setInner(next);
    onChangeText?.(next);
  };

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const openUp = (byHand: boolean) => {
    if (opened || busy) return;
    setOpened(true);
    open.set(withTiming(1, { duration: OPEN_TIME * 1000, easing: reducedMotion ? EASE_OUT : SNAP, reduceMotion: KEEP }));
    onOpen?.();
    if (byHand) {
      tick();
      field.current?.focus();
    }
  };

  /** Folds the card away, after sending what it holds or because it was dismissed. */
  const close = (sending: boolean, byHand: boolean) => {
    if (byHand) {
      // A touch on the card's buttons takes over from the script as well.
      setPlaying(false);
      setPressed(false);
    }
    if (!opened || busy || (sending && !ready)) return;
    if (sending) onSend?.(text.trim());
    else onClose?.();
    if (byHand) tick();
    field.current?.blur();
    setPressed(false);
    setBusy(true);
  };

  const perform = useEffectEvent((act: Act) => {
    if (act.kind === 'open') openUp(false);
    else if (act.kind === 'type') edit(act.text);
    else if (act.kind === 'press') setPressed(true);
    else close(true, false);
  });

  useEffect(() => {
    if (!playing) return;
    const steps = scriptSteps(script);
    const back = FOLD_TIME + GONE_TIME + DRAW_AFTER + DRAW_TIME;
    const period = Math.max(LOOP, steps[steps.length - 1].at + back + OPEN_AT);
    // Every step is timed from the start of its loop, so the period does not drift.
    let origin = Date.now();
    let index = 0;
    let timer: ReturnType<typeof setTimeout>;
    const wait = () => {
      const at = index < steps.length ? steps[index].at : period;
      timer = setTimeout(
        () => {
          if (index < steps.length) {
            perform(steps[index].act);
            index += 1;
          } else {
            origin += period * 1000;
            index = 0;
            clock.set((Date.now() - origin) / 1000);
          }
          wait();
        },
        Math.max(0, origin + at * 1000 - Date.now()),
      );
    };
    // The colours and the streak run on the loop's clock, so every loop shows the same frames.
    clock.set(0);
    wait();
    return () => clearTimeout(timer);
  }, [playing, script, clock]);

  // The way back to the pill: fold flat, bow and fade, then draw the outline again.
  const clear = useEffectEvent(() => edit(''));
  useEffect(() => {
    if (!busy) return;
    let timer: ReturnType<typeof setTimeout>;
    const rest = () => {
      setOpened(false);
      setBusy(false);
    };
    if (reducedMotion) {
      open.set(withTiming(0, { duration: OPEN_TIME * 1000, easing: EASE_OUT, reduceMotion: KEEP }));
      timer = setTimeout(() => {
        clear();
        rest();
      }, OPEN_TIME * 1000);
    } else {
      fold.set(withTiming(1, { duration: FOLD_TIME * 1000, easing: EASE_IN_OUT, reduceMotion: KEEP }));
      timer = setTimeout(() => {
        clear();
        gone.set(withTiming(1, { duration: GONE_TIME * 1000, easing: EASE_OUT, reduceMotion: KEEP }));
        timer = setTimeout(
          () => {
            // Nothing is on screen now: back to the pill, not yet drawn. In one step, so no frame sees half of it.
            scheduleOnUI(() => {
              'worklet';
              open.set(0);
              fold.set(0);
              gone.set(0);
              draw.set(0);
              draw.set(withTiming(1, { duration: DRAW_TIME * 1000, easing: SWEEP, reduceMotion: KEEP }));
            });
            timer = setTimeout(rest, DRAW_TIME * 1000);
          },
          (GONE_TIME + DRAW_AFTER) * 1000,
        );
      }, FOLD_TIME * 1000);
    }
    return () => clearTimeout(timer);
  }, [busy, reducedMotion, open, fold, gone, draw]);

  // Left empty, the card closes; closed, the script resumes after a while.
  const resume = useEffectEvent(() => {
    if (text !== '') return;
    if (opened) close(false, false);
    else setPlaying(true);
  });
  useEffect(() => {
    if (!autoPlay || controlled || playing || focused || busy) return;
    const timer = setTimeout(() => resume(), RESUME_AFTER * 1000);
    return () => clearTimeout(timer);
  }, [autoPlay, controlled, playing, focused, opened, busy]);

  // The caret stays solid while keys arrive and blinks once they stop.
  useEffect(() => {
    caretOn.set(withTiming(1, CARET_FADE));
    if (!playing || !opened) return;
    let on = true;
    const timer = setInterval(() => {
      on = !on;
      caretOn.set(withTiming(on ? 1 : 0, CARET_FADE));
    }, BLINK * 1000);
    return () => clearInterval(timer);
  }, [text, playing, opened, caretOn]);

  const onFocus = () => {
    setFocused(true);
    // A touch takes the field over from the script.
    if (playing) {
      setPlaying(false);
      edit('');
    }
  };

  const onPillPress = (event: GestureResponderEvent) => {
    if (offPill(event.nativeEvent.locationX, event.nativeEvent.locationY)) return;
    // A touch takes over from the script.
    setPlaying(false);
    openUp(true);
  };

  const iconStyle = useAnimatedStyle(() => {
    const o = open.get();
    const shape = shapeOf(o, reducedMotion);
    const icon = clamp01(1 - shape * 3) * clamp01((draw.get() - 0.6) / 0.4) * veilOf(o, reducedMotion);
    return { opacity: icon, transform: [{ scale: 0.8 + 0.2 * icon }] };
  });

  const cardStyle = useAnimatedStyle(() => {
    const o = open.get();
    const shape = shapeOf(o, reducedMotion);
    const content = clamp01((shape - 0.55) / 0.45) * clamp01(1 - fold.get() * 4) * veilOf(o, reducedMotion);
    return { opacity: content, transform: [{ scale: 0.94 + 0.06 * clamp01(shape) }] };
  });

  return (
    <View style={styles.box}>
      <View pointerEvents="none" style={styles.canvas}>
        <Light open={open} fold={fold} gone={gone} draw={draw} clock={clock} still={reducedMotion} />
      </View>

      {/* A magnifying glass open at the top right, where a four-point sparkle sits. */}
      <Animated.View pointerEvents="none" style={[styles.icon, iconStyle]}>
        <Svg width={28} height={28} viewBox="0 0 28 28">
          <Path d={GLASS_ICON} stroke={INK} strokeWidth={2.6} strokeLinecap="round" fill="none" />
          <Path d={SPARKLE_ICON} fill={INK} />
        </Svg>
      </Animated.View>

      <Pressable
        style={styles.pill}
        pointerEvents={!opened && !busy ? 'auto' : 'none'}
        accessibilityRole="button"
        accessibilityLabel="Search"
        onPress={onPillPress}
      />

      {/* What the open card holds: the field, a close button, and the tools along the bottom. */}
      <Animated.View
        style={[styles.card, cardStyle]}
        pointerEvents={opened && !busy ? 'auto' : 'none'}
        accessibilityElementsHidden={!opened}
        importantForAccessibility={opened ? 'auto' : 'no-hide-descendants'}
      >
        <View style={styles.field}>
          {/* autoPlay's caret hangs off an invisible copy of the text; a focused field shows its own. */}
          <View pointerEvents="none" style={styles.mirror} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
            <Text style={styles.copy} numberOfLines={1}>
              {text.replace(/ /g, '\u00a0')}
            </Text>
            <Caret visible={playing && opened && !focused} empty={text === ''} on={caretOn} />
          </View>
          <TextInput
            ref={field}
            style={styles.input}
            value={text}
            onChangeText={edit}
            placeholder={placeholder}
            placeholderTextColor="#9aa0a6"
            selectionColor={CARET}
            cursorColor={CARET}
            returnKeyType="search"
            onSubmitEditing={() => close(true, true)}
            onFocus={onFocus}
            onBlur={() => setFocused(false)}
            accessibilityLabel={placeholder}
          />
        </View>

        <Tool style={styles.close} icon={CLOSE_ICON} tint="#5f6368" label="Close" onPress={() => close(false, true)} />
        <Tool style={styles.image} icon={IMAGE_ICON} tint={INK} label="Add an image" onPress={() => onImage?.()} />
        <Tool style={styles.lens} icon={LENS_ICON} tint={INK} label="Search with the camera" onPress={() => onLens?.()} />
        <Tool style={styles.mic} icon={MIC_ICON} tint={INK} label="Dictate" onPress={() => onMic?.()} />
        <Press style={styles.send} label="Send" onPress={() => close(true, true)}>
          <Animated.View
            style={[
              styles.arrow,
              {
                backgroundColor: sendColor,
                transform: [{ scale: pressed ? 0.88 : 1 }],
                transitionProperty: 'transform',
                transitionDuration: PRESS * 1000,
                transitionTimingFunction: CSS_EASE_OUT,
              },
            ]}
          >
            <Glyph d={ARROW_ICON} tint="#ffffff" width={2} />
          </Animated.View>
        </Press>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  box: {
    width: WIDTH,
    height: HEIGHT,
  },
  canvas: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: HEIGHT,
  },
  icon: {
    position: 'absolute',
    left: (WIDTH - PILL_WIDTH) / 2 + ICON_X - 14,
    top: HEIGHT / 2 - 14,
    width: 28,
    height: 28,
  },
  pill: {
    position: 'absolute',
    left: (WIDTH - PILL_WIDTH) / 2,
    top: (HEIGHT - PILL_HEIGHT) / 2,
    width: PILL_WIDTH,
    height: PILL_HEIGHT,
  },
  card: {
    position: 'absolute',
    left: (WIDTH - CARD_WIDTH) / 2,
    top: (HEIGHT - CARD_HEIGHT) / 2,
    width: CARD_WIDTH,
    height: CARD_HEIGHT,
  },
  field: {
    position: 'absolute',
    left: TEXT_X,
    top: 16,
    width: FIELD_WIDTH,
    height: FIELD_HEIGHT,
  },
  mirror: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: FIELD_HEIGHT,
    flexDirection: 'row',
    alignItems: 'center',
  },
  copy: {
    fontSize: FONT_SIZE,
    includeFontPadding: false,
    opacity: 0,
  },
  caret: {
    width: 2,
    height: 20,
    borderRadius: 1,
    backgroundColor: CARET,
  },
  input: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: FIELD_WIDTH,
    height: FIELD_HEIGHT,
    padding: 0,
    color: INK,
    fontSize: FONT_SIZE,
    includeFontPadding: false,
    textAlignVertical: 'center',
  },
  tool: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
  },
  close: {
    position: 'absolute',
    left: CARD_WIDTH - 28 - 18,
    top: 28 - 18,
  },
  image: {
    position: 'absolute',
    left: 32 - 18,
    top: CARD_HEIGHT - TOOL_Y - 18,
  },
  lens: {
    position: 'absolute',
    left: 70 - 18,
    top: CARD_HEIGHT - TOOL_Y - 18,
  },
  mic: {
    position: 'absolute',
    left: CARD_WIDTH - 88 - 18,
    top: CARD_HEIGHT - TOOL_Y - 18,
  },
  send: {
    position: 'absolute',
    left: CARD_WIDTH - 36 - 20,
    top: CARD_HEIGHT - TOOL_Y - 20,
  },
  arrow: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
