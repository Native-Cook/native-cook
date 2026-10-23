import { BlendMode, Canvas, PaintStyle, Picture, type SkCanvas, type SkFont, Skia, createPicture, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

/*
 Weight Breathing Glyph: a type specimen of one giant capital S that breathes between hairline and
 black along a weight axis, with the glyph's skeleton (its points and the lines between them) drawn
 over it like a font editor.

 The outline is Inter Tight's S at its three masters (weights 100, 400 and 900), kept here as point
 lists (font units, 2048 to the em). The font has no avar table, so a weight between two masters is
 exactly the linear blend of their points, which is what the variable font itself draws. A contour is
 TrueType quadratics: two off-curve points in a row have an implied on-curve point half-way. (Skia
 in React Native cannot set a font's weight axis, so the block needs no font file at all.)

 Layout (pt, W × H the whole screen, on #ebeae6): a black pill "Type specimen 01" and "● Variable"
 at top + 12; "U+0053" and "wght 100–900" at top + 56; the S scaled to fit W − 48 at its widest
 (black) advance and centred in the room between top + 72 and the panel, with hairline guides at
 its cap height and baseline; below, the readout (the weight, 56 pt monospaced, the style name and
 "wght"), the axis (ticks every 100, a black knob at the weight) and a caption.

 Drawing: the outline at 900 as a ghost (0.75 pt, ink 28%), the current outline filled #0b0b0b,
 then the skeleton in white with a difference blend, so it reads dark on the paper and light on the
 letter: the control polygon (0.75 pt), on-curve points as dots (r 3.2) and off-curve points as
 rings (r 2.4).

 Timeline of the self-playing loop (LOOP = 8 s, two breaths of 4 s):
   weight = 100 + 800 · (½ − ½ cos(2π t / 4 s)), so 100 (the resting state, hairline) at 0, 4 and 8 s
   and 900 (black) at 2 and 6 s, easing in and out at both ends like a sine.

 Touch: dragging sideways sets the weight directly (the axis's width, W − 48, spans 100 to 900),
 with a selection haptic each time it crosses a hundred. 2 s after the finger lifts the weight eases
 back to 100 (0.8 s, ease-in-out) and the loop starts over. Reduced motion: the S stands still at
 400 (Regular) and the loop is off; dragging still sets the weight, and it stays where it is left.
*/

/** The self-playing loop, in seconds. */
export const LOOP = 8;

const BREATH = 4;
const IDLE = 2;
const BACK = 0.8;
const PAPER = '#ebeae6';
/**
 * SwiftUI's monospaced system font (SF Mono), which Skia finds under Core Text's private name; Menlo
 * if it ever does not (Menlo has no medium, so its 500 is bold).
 */
const MONO = Platform.select({ ios: '.AppleSystemUIFontMonospaced', default: 'monospace' });

function mono(fontSize: number, fontWeight: '400' | '500'): SkFont {
  const font = matchFont({ fontFamily: MONO, fontSize, fontWeight });
  if (font.getGlyphWidths(font.getGlyphIDs('0'))[0] > 0) return font;
  return matchFont({ fontFamily: Platform.select({ ios: 'Menlo', default: 'monospace' }), fontSize, fontWeight });
}

const BLACK_ADVANCE = 1316;
const ADVANCES = [1254, 1210, 1316];
/** Inter Tight's S at weights 100, 400 and 900 (font units, y up), as x, y pairs. */
// prettier-ignore
const THIN = [622, -24, 486, -24, 283, 71, 165, 240, 158, 350, 204, 350, 211, 253, 317, 106, 500, 24, 622, 24, 751, 24, 943, 113, 1050, 273, 1050, 378, 1050, 458, 980, 579, 831, 673, 710, 710, 542, 762, 366, 817, 192, 999, 192, 1128, 192, 1238, 307, 1412, 507, 1512, 634, 1512, 762, 1512, 955, 1410, 1069, 1233, 1076, 1118, 1030, 1118, 1019, 1270, 807, 1466, 634, 1466, 521, 1466, 341, 1379, 238, 1226, 238, 1128, 238, 1053, 306, 932, 446, 841, 552, 808, 722, 756, 818, 726, 959, 652, 1051, 560, 1096, 447, 1096, 378, 1096, 262, 977, 80, 764, -24];
// prettier-ignore
const REGULAR = [604, -26, 444, -26, 210, 77, 77, 262, 68, 384, 254, 384, 262, 300, 361, 190, 515, 136, 604, 136, 707, 136, 872, 204, 968, 326, 968, 408, 968, 482, 884, 574, 749, 632, 670, 654, 488, 706, 313, 756, 110, 943, 110, 1094, 110, 1219, 245, 1406, 476, 1510, 618, 1510, 762, 1510, 986, 1407, 1118, 1230, 1122, 1118, 946, 1118, 933, 1228, 748, 1350, 612, 1350, 514, 1350, 366, 1286, 284, 1174, 284, 1102, 284, 1023, 382, 926, 517, 872, 572, 856, 722, 814, 780, 798, 923, 744, 1056, 652, 1142, 509, 1142, 404, 1142, 284, 1016, 89, 776, -26];
// prettier-ignore
const BLACK = [678, -18, 484, -18, 195, 97, 35, 337, 34, 524, 418, 524, 422, 456, 483, 362, 594, 314, 672, 314, 737, 314, 825, 346, 870, 403, 870, 440, 870, 474, 827, 525, 729, 567, 646, 584, 496, 616, 299, 658, 72, 861, 72, 1036, 72, 1178, 226, 1391, 497, 1510, 674, 1510, 855, 1510, 1118, 1389, 1261, 1170, 1262, 1024, 874, 1024, 869, 1097, 772, 1178, 674, 1178, 612, 1178, 532, 1149, 494, 1097, 494, 1064, 494, 1029, 535, 976, 624, 937, 694, 922, 816, 896, 939, 870, 1116, 784, 1229, 669, 1282, 525, 1282, 440, 1282, 293, 1137, 88, 867, -18];
/** On-curve (1) or off-curve (0), point by point; the S is one contour. */
// prettier-ignore
const ON = [1, 0, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0];

const NAMES = ['Thin', 'ExtraLight', 'Light', 'Regular', 'Medium', 'SemiBold', 'Bold', 'ExtraBold', 'Black'];

function styleName(weight: number) {
  'worklet';
  return NAMES[Math.min(Math.max(Math.round(weight / 100) - 1, 0), 8)];
}

function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** The outline at `weight` (100 … 900): its points and its advance. */
function outlineAt(weight: number) {
  'worklet';
  const w = Math.min(Math.max(weight, 100), 900);
  const low = w <= 400;
  const a = low ? THIN : REGULAR;
  const b = low ? REGULAR : BLACK;
  const t = low ? (w - 100) / 300 : (w - 400) / 500;
  const advance = low ? ADVANCES[0] + (ADVANCES[1] - ADVANCES[0]) * t : ADVANCES[1] + (ADVANCES[2] - ADVANCES[1]) * t;
  const points: number[] = [];
  for (let i = 0; i < a.length; i++) points.push(a[i] + (b[i] - a[i]) * t);
  return { points, advance };
}

/** The closed quadratic contour through `points` (font units), placed on screen. */
function contour(points: number[], x0: number, baseline: number, k: number) {
  'worklet';
  const path = Skia.Path.Make();
  const n = ON.length;
  const x = (i: number) => x0 + points[2 * i] * k;
  const y = (i: number) => baseline - points[2 * i + 1] * k;
  const first = ON.indexOf(1);
  if (first < 0) return path;
  path.moveTo(x(first), y(first));
  let i = 1;
  while (i <= n) {
    const index = (first + i) % n;
    if (ON[index]) {
      path.lineTo(x(index), y(index));
      i += 1;
    } else {
      const next = (first + i + 1) % n;
      if (ON[next]) {
        path.quadTo(x(index), y(index), x(next), y(next));
        i += 2;
      } else {
        path.quadTo(x(index), y(index), (x(index) + x(next)) / 2, (y(index) + y(next)) / 2);
        i += 1;
      }
    }
  }
  path.close();
  return path;
}

type Fonts = { label: SkFont; small: SkFont; caption: SkFont; number: SkFont; name: SkFont };

function color(alpha: number) {
  'worklet';
  return Float32Array.of(0x0b / 255, 0x0b / 255, 0x0b / 255, alpha);
}

function widthOf(font: SkFont, text: string, tracking = 0) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let sum = 0;
  for (let i = 0; i < widths.length; i++) sum += widths[i] + tracking;
  return sum;
}

/** Draws `text` from `x` on `baseline`, `tracking` pt between letters. */
function drawText(canvas: SkCanvas, font: SkFont, text: string, x: number, baseline: number, rgba: Float32Array, tracking = 0) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(rgba);
  if (tracking === 0) {
    canvas.drawText(text, x, baseline, paint, font);
    return;
  }
  let pen = x;
  for (const ch of text) {
    canvas.drawText(ch, pen, baseline, paint, font);
    pen += widthOf(font, ch) + tracking;
  }
}

/** The baseline that centres the font's line box on `y`, as SwiftUI's centre anchors do. */
function centred(font: SkFont, y: number) {
  'worklet';
  const m = font.getMetrics();
  return y - (m.ascent + m.descent) / 2;
}

function drawSpecimen(
  canvas: SkCanvas,
  fonts: Fonts,
  w: number,
  h: number,
  top: number,
  bottom: number,
  weight: number,
  badge: string,
  tag: string,
  caption: string,
) {
  'worklet';
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setColor(Skia.Color(PAPER));
  canvas.drawRect(Skia.XYWHRect(0, 0, w, h), fill);
  const faint = color(0.45);

  // Header.
  const label = badge.toUpperCase();
  const pillWidth = widthOf(fonts.label, label, 1) + 24;
  const pillMid = top + 25;
  fill.setColor(color(1));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(24, top + 12, pillWidth, 26), 13, 13), fill);
  drawText(canvas, fonts.label, label, 24 + (pillWidth - widthOf(fonts.label, label, 1)) / 2, centred(fonts.label, pillMid), Float32Array.of(1, 1, 1, 1), 1);
  const tagLabel = tag.toUpperCase();
  const tagWidth = widthOf(fonts.label, tagLabel, 1);
  drawText(canvas, fonts.label, tagLabel, w - 24 - tagWidth, centred(fonts.label, pillMid), color(1), 1);
  canvas.drawCircle(w - 24 - tagWidth - 11, pillMid, 3, fill);
  drawText(canvas, fonts.small, 'U+0053', 24, centred(fonts.small, top + 56), faint);
  drawText(canvas, fonts.small, 'wght 100–900', w - 24 - widthOf(fonts.small, 'wght 100–900'), centred(fonts.small, top + 56), faint);

  // Panel, laid out from the bottom up.
  const floor = h - bottom - 16;
  const trackY = floor - 44;
  const readout = floor - 76;
  const panelTop = readout - 56;

  // The glyph: fitted to the black advance and to the room above the panel, centred on its advance.
  const roomTop = top + 72;
  const roomBottom = panelTop - 16;
  const k = Math.min((w - 48) / BLACK_ADVANCE, (roomBottom - roomTop) / 1600);
  const baseline = (roomTop + roomBottom) / 2 + ((1510 - 18) * k) / 2;
  const outline = outlineAt(weight);
  const x0 = w / 2 - (outline.advance * k) / 2;
  const ghostX0 = w / 2 - (BLACK_ADVANCE * k) / 2;

  // Guides at cap height and baseline.
  const descent = fonts.small.getMetrics().descent;
  const guides: [number, string][] = [
    [baseline - 1490 * k, 'Cap height 1490'],
    [baseline, 'Baseline 0'],
  ];
  for (const [y, guide] of guides) {
    fill.setColor(color(0.18));
    canvas.drawRect(Skia.XYWHRect(0, y - 0.25, w, 0.5), fill);
    drawText(canvas, fonts.small, guide, 24, y - 4 - descent, faint);
  }

  const stroke = Skia.Paint();
  stroke.setAntiAlias(true);
  stroke.setStyle(PaintStyle.Stroke);
  stroke.setStrokeWidth(0.75);
  stroke.setColor(color(0.28));
  canvas.drawPath(contour(outlineAt(900).points, ghostX0, baseline, k), stroke);
  fill.setColor(color(1));
  canvas.drawPath(contour(outline.points, x0, baseline, k), fill);

  // The skeleton, in white with a difference blend.
  const white = Float32Array.of(1, 1, 1, 1);
  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeWidth(0.75);
  line.setColor(white);
  line.setBlendMode(BlendMode.Difference);
  const dot = Skia.Paint();
  dot.setAntiAlias(true);
  dot.setColor(white);
  dot.setBlendMode(BlendMode.Difference);
  const n = ON.length;
  const polygon = Skia.Path.Make();
  for (let i = 0; i < n; i++) {
    const x = x0 + outline.points[2 * i] * k;
    const y = baseline - outline.points[2 * i + 1] * k;
    if (i === 0) polygon.moveTo(x, y);
    else polygon.lineTo(x, y);
  }
  polygon.close();
  canvas.drawPath(polygon, line);
  line.setStrokeWidth(1);
  for (let i = 0; i < n; i++) {
    const x = x0 + outline.points[2 * i] * k;
    const y = baseline - outline.points[2 * i + 1] * k;
    if (ON[i]) canvas.drawCircle(x, y, 3.2, dot);
    else canvas.drawCircle(x, y, 2.4, line);
  }

  // Readout.
  drawText(canvas, fonts.number, `${Math.round(weight)}`, 24, readout, color(1));
  const name = styleName(weight);
  drawText(canvas, fonts.name, name, w - 24 - widthOf(fonts.name, name), readout, color(1));
  drawText(canvas, fonts.small, 'wght', w - 24 - widthOf(fonts.small, 'wght'), readout - 24, faint);

  // Axis: a hairline, a tick every 100 (taller at 100, 400 and 900) and the knob.
  const span = w - 48;
  fill.setColor(color(0.3));
  canvas.drawRect(Skia.XYWHRect(24, trackY - 0.5, span, 1), fill);
  for (let step = 0; step <= 8; step++) {
    const x = 24 + (span * step) / 8;
    const major = step === 0 || step === 3 || step === 8;
    const tall = major ? 10 : 6;
    fill.setColor(color(major ? 0.6 : 0.3));
    canvas.drawRect(Skia.XYWHRect(x - 0.5, trackY - tall, 1, tall), fill);
  }
  const labelY = centred(fonts.small, floor - 26);
  drawText(canvas, fonts.small, '100', 24, labelY, faint);
  drawText(canvas, fonts.small, '400', 24 + (span * 300) / 800 - widthOf(fonts.small, '400') / 2, labelY, faint);
  drawText(canvas, fonts.small, '900', w - 24 - widthOf(fonts.small, '900'), labelY, faint);
  const knobX = 24 + (span * (weight - 100)) / 800;
  fill.setColor(color(1));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(knobX - 2, trackY - 16, 4, 24), 2, 2), fill);
  drawText(canvas, fonts.caption, caption, 24, floor - fonts.caption.getMetrics().descent, color(0.5));
}

type Props = {
  badge?: string;
  tag?: string;
  caption?: string;
  /** Breathes the weight by itself; the pattern repeats every `LOOP` seconds. */
  autoPlay?: boolean;
};

/**
 * A type specimen of one giant S breathing between hairline and black, with its points and control
 * lines drawn over it; drag sideways to set the weight.
 */
export function WeightBreathingGlyph({ badge = 'Type specimen 01', tag = 'Variable', caption = 'Drag sideways to set the weight', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [fonts] = useState<Fonts>(() => ({
    label: mono(11, '500'),
    small: mono(10, '400'),
    caption: mono(11, '400'),
    number: mono(56, '500'),
    name: mono(17, '500'),
  }));

  const clock = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  // The weight a finger set (-1: none), the ease back to rest after it lifts, and the drag.
  const touched = useSharedValue(-1);
  const returning = useSharedValue(false);
  const backFrom = useSharedValue(100);
  const backStart = useSharedValue(0);
  const dragging = useSharedValue(false);
  const dragBase = useSharedValue(100);
  const lifted = useSharedValue(-1);

  const weightAt = (now: number) => {
    'worklet';
    if (touched.get() >= 0) return touched.get();
    if (returning.get()) return backFrom.get() + (100 - backFrom.get()) * easeInOut(Math.min((now - backStart.get()) / BACK, 1));
    if (reduced) return 400;
    if (!autoPlay) return 100;
    const t = (((now - loopStart.get()) % LOOP) + LOOP) % LOOP;
    return 100 + 800 * (0.5 - 0.5 * Math.cos((2 * Math.PI * t) / BREATH));
  };

  useFrameCallback((frame) => {
    const now = frame.timestamp / 1000;
    clock.set(now);
    if (loopStart.get() < 0) loopStart.set(now);
    // 2 s after the finger lifts the weight eases back to rest and the loop starts over.
    if (!reduced && lifted.get() >= 0 && !dragging.get() && now - lifted.get() >= IDLE) {
      lifted.set(-1);
      backFrom.set(touched.get() >= 0 ? touched.get() : 100);
      backStart.set(now);
      touched.set(-1);
      returning.set(true);
    }
    if (returning.get() && now - backStart.get() >= BACK) {
      returning.set(false);
      loopStart.set(now);
    }
  });

  const tick = () => {
    Haptics.selectionAsync().catch(() => {});
  };

  const picture = useDerivedValue(() => {
    const weight = weightAt(clock.get());
    return createPicture((canvas) => {
      if (size.width === 0) return;
      drawSpecimen(canvas, fonts, size.width, size.height, insets.top, insets.bottom, weight, badge, tag, caption);
    });
  });

  const gesture = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      dragBase.set(weightAt(clock.get()));
      dragging.set(true);
      returning.set(false);
      lifted.set(-1);
    })
    .onUpdate((e) => {
      const span = Math.max(size.width - 48, 1);
      const next = Math.min(Math.max(dragBase.get() + (e.translationX * 800) / span, 100), 900);
      const previous = touched.get() >= 0 ? touched.get() : dragBase.get();
      if (Math.floor(previous / 100) !== Math.floor(next / 100)) scheduleOnRN(tick);
      touched.set(next);
    })
    .onFinalize(() => {
      dragging.set(false);
      if (touched.get() >= 0) lifted.set(clock.get());
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout} accessible accessibilityLabel="The letter S" accessibilityHint={caption}>
      <StatusBar style="dark" />
      <GestureDetector gesture={gesture}>
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAPER },
});
