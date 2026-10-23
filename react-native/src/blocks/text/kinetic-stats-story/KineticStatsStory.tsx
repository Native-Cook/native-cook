import {
  BlurStyle,
  Canvas,
  ClipOp,
  PaintStyle,
  Picture,
  type SkCanvas,
  type SkFont,
  type SkTypeface,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
  createPicture,
  useTypeface,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

/*
 Kinetic Stats Story: a year-in-review story for a running app. Three cards on a drifting mesh
 gradient; the stats are set far bigger than the screen and travel through it.

 Layout (pt, W × H the whole screen, `top`/`bottom` the safe area): story bars at top + 8 (3 pt,
 12 pt in from the sides, 4 pt apart), a header row centred at top + 32 (label left, close button
 right), round buttons (36 pt) in the bottom corners, 16 pt from the sides and 12 pt above the safe
 area. All type is Inter Tight Bold.

 Timeline of the story (LOOP = 10 s, story time s):
   0.00–1.55  Cover at rest: kicker "Your year on the road" and "Rewind" / "’26" (sized to fill
              W − 48) at the bottom left, on the deep teal mesh. The first bar fills.
   1.55–2.00  The cover's three lines leave to the left (48 pt, ease-in cubic, 0.35 s each, 0.05 s
              apart) while they blur (sigma 0 → 10) and fade; the mesh turns orange (1.5–2.4).
   1.85–2.41  "This year you ran" / "1,284 kilometres." (30 pt) enter from the right (48 pt,
              ease-out cubic, 0.5 s, 0.06 s apart), blur 10 → 0.
   2.15–5.20  The number "1,284" (0.7 W, so about twice the screen wide) comes in from the right
              edge (W − 8 pt, 0.75 s, ease-out cubic) and from 2.75 slides out to the left, slowly
              at first (its width + 0.3 W + 24 pt, 2.45 s, ease-in cubic). Its digits roll like an
              odometer from 2.2 to 3.4 (ease-out cubic), each slot turning ten more times than the
              one left of it; a digit rises out (fading) as the next rises in from 1.25 cap heights
              below, inside a band clipped to the digits.
   4.95–5.40  The caption leaves to the left like the cover; the mesh shifts (4.9–5.8).
   5.35–8.85  "And 212" / "sunrise" / "runs." (0.4 W, lines 0.94 em apart) come in from the right
              (0.95 W, ease-out cubic, 0.85 s, 0.1 s apart) and drift left, each at its own speed,
              so that by 8.85 its end sits at 0.62 W.
   8.80–9.30  The lines leave to the left (0.7 W, ease-in cubic, 0.4 s, 0.05 s apart); the bars
              drain (8.85–9.45, ease-in-out) and the mesh returns to teal (8.8–9.9).
   9.20–9.84  The cover's lines come back in from the right; from 9.84 the frame is the one at 0.
 The mesh's five blobs move on paths with a period of 10 s (or 5 s), so the loop has no seam.

 Touch: a tap on the left third goes to the previous card, elsewhere to the next card (light
 haptic); holding a finger down pauses the story, and it plays on from there on release. Without
 `autoPlay` the story waits on the cover and plays through once from a tap. Reduced motion: the
 story holds each card still (the number and the lines scaled to fit the width), taps cross-fade
 between cards in 0.3 s, and nothing plays by itself.
*/

/** One pass of the story, in seconds. */
export const LOOP = 10;

const MARGIN = 24;
const SHIFT = 48;
const BLUR = 10;
const CARDS = [0, 1.55, 4.95];
const BARS = [
  [0, 1.55],
  [1.55, 4.95],
  [4.95, 8.85],
];
const DRAIN_START = 8.85;
const DRAIN = 0.6;
const TAP_TIME = 0.3;
const TAP_SLOP = 10;
const FADE = 0.3;

/** Base colour then the five blobs, for the cover, the first card and the second card. */
const PALETTES = [
  [0x0d2433, 0x1f8f7d, 0x2c5da8, 0x5fd39a, 0x081520, 0xb9772c],
  [0xff6a00, 0x25d98c, 0xffb300, 0xff4400, 0xff8a00, 0xffd34d],
  [0xff7400, 0xffc02e, 0xff5000, 0x38e0a0, 0xffa000, 0xffe58a],
];

/** Centre (fractions of the screen), swing, frequency (turns per loop), phase and radius (fraction of the longer side). */
const BLOBS = [
  { x: 0.15, y: 0.22, ax: 0.25, ay: 0.08, fx: 1, fy: 2, px: 0.0, py: 1.1, r: 0.42 },
  { x: 0.85, y: 0.35, ax: 0.2, ay: 0.12, fx: 1, fy: 1, px: 2.1, py: 0.4, r: 0.48 },
  { x: 0.35, y: 0.75, ax: 0.3, ay: 0.1, fx: 2, fy: 1, px: 4.0, py: 2.6, r: 0.5 },
  { x: 0.9, y: 0.9, ax: 0.15, ay: 0.08, fx: 1, fy: 2, px: 1.3, py: 3.7, r: 0.4 },
  { x: 0.45, y: 0.05, ax: 0.3, ay: 0.06, fx: 1, fy: 1, px: 5.2, py: 0.9, r: 0.35 },
];

function clamp(x: number) {
  'worklet';
  return Math.min(Math.max(x, 0), 1);
}
function easeOutCubic(t: number) {
  'worklet';
  return 1 - Math.pow(1 - t, 3);
}
function easeInCubic(t: number) {
  'worklet';
  return t * t * t;
}
function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}
function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = clamp((x - a) / (b - a));
  return t * t * (3 - 2 * t);
}

/** An element's offset, opacity and blur (sigma, pt) during a transition. */
type Look = { dx: number; alpha: number; blur: number };
const REST: Look = { dx: 0, alpha: 1, blur: 0 };

/** Comes in from the right, sharpening. */
function enter(s: number, start: number, duration: number): Look {
  'worklet';
  const e = easeOutCubic(clamp((s - start) / duration));
  return { dx: SHIFT * (1 - e), alpha: e, blur: BLUR * (1 - e) };
}

/** Leaves to the left, blurring. */
function leave(s: number, start: number, duration: number): Look {
  'worklet';
  const q = clamp((s - start) / duration);
  const e = easeInCubic(q);
  return { dx: -SHIFT * e, alpha: 1 - q, blur: BLUR * e };
}

function then(a: Look, b: Look): Look {
  'worklet';
  return { dx: a.dx + b.dx, alpha: a.alpha * b.alpha, blur: a.blur + b.blur };
}

/** How much of each card's palette the mesh shows. */
function meshWeights(s: number) {
  'worklet';
  const c1 = smoothstep(1.5, 2.4, s) * (1 - smoothstep(4.9, 5.8, s));
  const c2 = smoothstep(4.9, 5.8, s) * (1 - smoothstep(8.8, 9.9, s));
  return [1 - c1 - c2, c1, c2];
}

function cardAt(s: number) {
  'worklet';
  if (s >= CARDS[2] && s < 9.2) return 2;
  if (s >= CARDS[1] && s < CARDS[2]) return 1;
  return 0;
}

type Copy = {
  label: string;
  kicker: string;
  title: string[];
  caption: string[];
  number: string;
  lines: string[];
};

type Frame = { w: number; h: number; top: number; bottom: number };

/** Inter Tight Bold at any size, measured by glyph advances. */
function fontAt(face: SkTypeface, size: number): SkFont {
  'worklet';
  return Skia.Font(face, size);
}

function widthOf(face: SkTypeface, text: string, size: number) {
  'worklet';
  const font = fontAt(face, size);
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let sum = 0;
  for (let i = 0; i < widths.length; i++) sum += widths[i];
  return sum;
}

/** The largest size up to `cap` at which `text` fits `width`. */
function fitSize(face: SkTypeface, text: string, width: number, cap: number) {
  'worklet';
  const unit = widthOf(face, text, 100);
  return unit > 0 ? Math.min(cap, (100 * width) / unit) : cap;
}

function drawStory(canvas: SkCanvas, face: SkTypeface, capRatio: number, f: Frame, copy: Copy, s: number, still: number) {
  'worklet';
  const capHeight = (size: number) => size * capRatio;

  /** Draws `text` with its baseline at `baseline` and its left edge at `x` (plus the look's offset). */
  const text = (str: string, size: number, x: number, baseline: number, look: Look = REST, opacity = 1) => {
    if (look.alpha <= 0.002 || str.length === 0) return;
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    paint.setColor(Float32Array.of(1, 1, 1, look.alpha * opacity));
    // SwiftUI's blur radius is treated as a sigma, which is what Skia takes.
    if (look.blur > 0.05) paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, look.blur, true));
    canvas.drawText(str, x + look.dx, baseline, paint, fontAt(face, size));
  };

  const mix = (weights: number[], index: number, alpha: number) => {
    let r = 0;
    let g = 0;
    let b = 0;
    for (let p = 0; p < PALETTES.length; p++) {
      const c = PALETTES[p][index];
      r += weights[p] * ((c >> 16) & 0xff);
      g += weights[p] * ((c >> 8) & 0xff);
      b += weights[p] * (c & 0xff);
    }
    return Float32Array.of(r / 255, g / 255, b / 255, alpha);
  };

  const mesh = (time: number, weights: number[]) => {
    const fill = Skia.Paint();
    fill.setAntiAlias(true);
    fill.setColor(mix(weights, 0, 1));
    canvas.drawRect(Skia.XYWHRect(0, 0, f.w, f.h), fill);
    const reach = Math.max(f.w, f.h);
    const phase = (2 * Math.PI * time) / LOOP;
    for (let i = 0; i < BLOBS.length; i++) {
      const blob = BLOBS[i];
      const x = f.w * (blob.x + blob.ax * Math.sin(blob.fx * phase + blob.px));
      const y = f.h * (blob.y + blob.ay * Math.sin(blob.fy * phase + blob.py));
      const r = reach * blob.r;
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      paint.setShader(
        Skia.Shader.MakeRadialGradient(
          { x, y },
          r,
          [mix(weights, i + 1, 1), mix(weights, i + 1, 0.6), mix(weights, i + 1, 0)],
          [0, 0.45, 1],
          TileMode.Clamp,
        ),
      );
      canvas.drawCircle(x, y, r, paint);
    }
    // A light scrim under the story bars and the header.
    const scrim = Skia.Paint();
    scrim.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: 0, y: 0 },
        { x: 0, y: f.top + 90 },
        [Float32Array.of(0, 0, 0, 0.22), Float32Array.of(0, 0, 0, 0)],
        null,
        TileMode.Clamp,
      ),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, f.w, f.top + 90), scrim);
  };

  const cover = (time: number) => {
    let longest = copy.title[0] ?? '';
    for (const t of copy.title) if (t.length > longest.length) longest = t;
    const size = fitSize(face, longest, f.w - 2 * MARGIN, 120);
    const cap = capHeight(size);
    const baseline2 = f.h - f.bottom - 92;
    const baseline1 = baseline2 - 0.92 * size;
    const kickerBaseline = baseline1 - cap - 22;
    const rows: [string, number, number][] = [
      [copy.kicker, 22, kickerBaseline],
      [copy.title[0] ?? '', size, baseline1],
      [copy.title[1] ?? '', size, baseline2],
    ];
    for (let i = 0; i < rows.length; i++) {
      const look = time >= 9 ? enter(time, 9.2 + 0.07 * i, 0.5) : leave(time, 1.55 + 0.05 * i, 0.35);
      text(rows[i][0], rows[i][1], MARGIN, rows[i][2], look, i === 0 ? 0.85 : 1);
    }
  };

  /** Digits get equal slots (the widest digit's advance), so the number does not jitter while it rolls. */
  const slots = (str: string, size: number) => {
    let digit = 0;
    for (let d = 0; d <= 9; d++) digit = Math.max(digit, widthOf(face, `${d}`, size));
    let x = 0;
    const list: { char: string; x: number; width: number }[] = [];
    for (const ch of str) {
      const w = ch >= '0' && ch <= '9' ? digit : widthOf(face, ch, size);
      list.push({ char: ch, x, width: w });
      x += w;
    }
    return { list, width: x };
  };

  const first = (time: number, fit: boolean) => {
    if (time <= 1.8 || time >= 5.5) return;
    for (let i = 0; i < copy.caption.length; i++) {
      const look = fit ? REST : then(enter(time, 1.85 + 0.06 * i, 0.5), leave(time, 4.95 + 0.05 * i, 0.4));
      text(copy.caption[i], 30, MARGIN, f.top + 86 + 36 * i, look);
    }

    // The number: one slot per digit, as wide as the widest digit, and the group separators.
    let size = 0.7 * f.w;
    let layout = slots(copy.number, size);
    if (fit) {
      size *= Math.min(1, (f.w - 2 * MARGIN) / layout.width);
      layout = slots(copy.number, size);
    }
    const cap = capHeight(size);
    const baseline = 0.6 * f.h + cap / 2;
    let x0 = MARGIN;
    if (!fit) {
      if (time <= 2.15 || time >= 5.2) return;
      x0 =
        MARGIN +
        (f.w - 8) * (1 - easeOutCubic(clamp((time - 2.15) / 0.75))) -
        (layout.width + 0.3 * f.w + MARGIN) * easeInCubic(clamp((time - 2.75) / 2.45));
    }
    const roll = fit ? 1 : easeOutCubic(clamp((time - 2.2) / 1.2));
    const step = cap * 1.25;
    canvas.save();
    canvas.clipRect(Skia.XYWHRect(0, baseline - cap * 1.08, f.w, cap * 1.16), ClipOp.Intersect, true);
    let digit = 0;
    for (const slot of layout.list) {
      const x = x0 + slot.x;
      if (slot.char >= '0' && slot.char <= '9') {
        const target = Number(slot.char);
        const position = (target + 10 * digit) * roll;
        const base = Math.floor(position);
        const frac = position - base;
        const a = `${base % 10}`;
        const b = `${(base + 1) % 10}`;
        // The outgoing digit rises and fades, the next one comes up from below.
        text(a, size, x + (slot.width - widthOf(face, a, size)) / 2, baseline - frac * step, { dx: 0, alpha: 1 - frac, blur: 0 });
        if (frac > 0.001) text(b, size, x + (slot.width - widthOf(face, b, size)) / 2, baseline + (1 - frac) * step, { dx: 0, alpha: frac, blur: 0 });
        digit++;
      } else {
        text(slot.char, size, x, baseline);
      }
    }
    canvas.restore();
  };

  const second = (time: number, fit: boolean) => {
    if (!fit && (time <= 5.3 || time >= 9.4)) return;
    let size = 0.4 * f.w;
    let widest = 1;
    for (const line of copy.lines) widest = Math.max(widest, widthOf(face, line, size));
    if (fit) size *= Math.min(1, (f.w - 2 * MARGIN) / widest);
    const pitch = 0.94 * size;
    const cap = capHeight(size);
    // The block of lines is centred a little below the middle.
    const firstBaseline = 0.53 * f.h - (pitch * (copy.lines.length - 1)) / 2 + cap / 2;
    for (let i = 0; i < copy.lines.length; i++) {
      const line = copy.lines[i];
      const baseline = firstBaseline + pitch * i;
      if (fit) {
        text(line, size, MARGIN, baseline);
        continue;
      }
      const width = widthOf(face, line, size);
      const speed = Math.max(0, MARGIN + width - 0.62 * f.w) / 3.5;
      const arrive = 5.35 + 0.1 * i;
      const x = MARGIN + 0.95 * f.w * (1 - easeOutCubic(clamp((time - arrive) / 0.85))) - speed * Math.max(0, time - 5.35);
      const out = clamp((time - 8.8 - 0.05 * i) / 0.4);
      text(line, size, x, baseline, { dx: -0.7 * f.w * easeInCubic(out), alpha: 1 - smoothstep(0.3, 1, out), blur: BLUR * easeInCubic(out) });
    }
  };

  const chrome = (time: number) => {
    const w = f.w;
    const fill = Skia.Paint();
    fill.setAntiAlias(true);
    // Story bars.
    const gap = 4;
    const inset = 12;
    const barWidth = (w - 2 * inset - gap * 2) / 3;
    const drain = 1 - easeInOut(clamp((time - DRAIN_START) / DRAIN));
    for (let i = 0; i < BARS.length; i++) {
      const left = inset + i * (barWidth + gap);
      const y = f.top + 8;
      fill.setColor(Float32Array.of(1, 1, 1, 0.32));
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(left, y, barWidth, 3), 1.5, 1.5), fill);
      const amount = clamp((time - BARS[i][0]) / (BARS[i][1] - BARS[i][0])) * drain;
      if (amount > 0) {
        fill.setColor(Float32Array.of(1, 1, 1, 1));
        canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(left, y, Math.max(3, barWidth * amount), 3), 1.5, 1.5), fill);
      }
    }
    // Header: label and close button.
    const row = f.top + 32;
    text(copy.label, 15, 16, row + capHeight(15) / 2);
    const cx = w - 16 - 15;
    fill.setColor(Float32Array.of(1, 1, 1, 0.22));
    canvas.drawCircle(cx, row, 15, fill);
    const stroke = Skia.Paint();
    stroke.setAntiAlias(true);
    stroke.setStyle(PaintStyle.Stroke);
    stroke.setStrokeCap(StrokeCap.Round);
    stroke.setStrokeJoin(StrokeJoin.Round);
    stroke.setColor(Float32Array.of(1, 1, 1, 1));
    stroke.setStrokeWidth(2);
    canvas.drawLine(cx - 4.5, row - 4.5, cx + 4.5, row + 4.5, stroke);
    canvas.drawLine(cx + 4.5, row - 4.5, cx - 4.5, row + 4.5, stroke);

    // Bottom corners: pause and share.
    const cy = f.h - f.bottom - 12 - 18;
    canvas.drawCircle(16 + 18, cy, 18, fill);
    canvas.drawCircle(w - 16 - 18, cy, 18, fill);
    fill.setColor(Float32Array.of(1, 1, 1, 1));
    const left = 16 + 18;
    for (const dx of [-3.5, 3.5]) canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(left + dx - 1.5, cy - 6.5, 3, 13), 1.5, 1.5), fill);
    const right = w - 16 - 18;
    stroke.setStrokeWidth(1.8);
    const share = Skia.Path.Make();
    share.moveTo(right, cy + 2).lineTo(right, cy - 8);
    share.moveTo(right - 3.5, cy - 4.5).lineTo(right, cy - 8).lineTo(right + 3.5, cy - 4.5);
    share.moveTo(right - 3, cy - 2).lineTo(right - 6, cy - 2).lineTo(right - 6, cy + 7);
    share.lineTo(right + 6, cy + 7).lineTo(right + 6, cy - 2).lineTo(right + 3, cy - 2);
    canvas.drawPath(share, stroke);
  };

  if (still >= 0) {
    const weights = [still === 0 ? 1 : 0, still === 1 ? 1 : 0, still === 2 ? 1 : 0];
    mesh([0, 3.5, 7][still], weights);
    if (still === 0) cover(0);
    else if (still === 1) first(3, true);
    else second(7, true);
    chrome([0, 4.95, 8.85][still]);
    return;
  }
  mesh(s, meshWeights(s));
  cover(s);
  first(s, false);
  second(s, false);
  chrome(s);
}

type Props = {
  label?: string;
  kicker?: string;
  title?: string[];
  caption?: string[];
  /** The number of the first card; it rolls up to this value. */
  number?: number;
  lines?: string[];
  /** Plays the story by itself, over and over; one pass lasts `LOOP` seconds. */
  autoPlay?: boolean;
};

/**
 * A year-in-review story: three cards on a drifting mesh gradient, with stats set far bigger than
 * the screen that roll and travel through it. Tap to step, hold to pause.
 */
export function KineticStatsStory({
  label = 'Rewind ’26',
  kicker = 'Your year on the road',
  title = ['Rewind', '’26'],
  caption = ['This year you ran', '1,284 kilometres.'],
  number = 1284,
  lines = ['And 212', 'sunrise', 'runs.'],
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const face = useTypeface(require('./assets/InterTight-Bold.ttf'));
  const [size, setSize] = useState({ width: 0, height: 0 });
  const copy: Copy = { label, kicker, title, caption, number: String(number).replace(/\B(?=(\d{3})+(?!\d))/g, ','), lines };
  // Cap height per unit of size, from the outline of "H".
  const capRatio = face ? (Skia.Path.MakeFromText('H', 0, 0, Skia.Font(face, 100))?.computeTightBounds().height ?? 72.75) / 100 : 0.7275;

  const clock = useSharedValue(0);
  // The moment story time 0 fell on; story time frozen while a finger is down (-1: none).
  const start = useSharedValue(-1);
  const held = useSharedValue(-1);
  const playing = useSharedValue(autoPlay);
  const downAt = useSharedValue(0);
  const downX = useSharedValue(0);
  const downY = useSharedValue(0);
  // Reduced motion: the card shown, the one before it and when it changed.
  const still = useSharedValue(0);
  const stillBefore = useSharedValue(0);
  const stillChange = useSharedValue(-10);

  const storyTime = (now: number) => {
    'worklet';
    if (held.get() >= 0) return held.get();
    if (!playing.get()) return 0;
    const raw = now - start.get();
    if (autoPlay) return ((raw % LOOP) + LOOP) % LOOP;
    return Math.min(raw, LOOP) % LOOP;
  };

  useFrameCallback((frame) => {
    const now = frame.timestamp / 1000;
    clock.set(now);
    if (start.get() < 0) start.set(now);
    // Without autoPlay the story stops on the cover when a pass ends.
    if (!autoPlay && playing.get() && held.get() < 0 && now - start.get() >= LOOP) playing.set(false);
  });

  const haptic = () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const picture = useDerivedValue(() => {
    const now = clock.get();
    const frame: Frame = { w: size.width, h: size.height, top: insets.top, bottom: insets.bottom };
    return createPicture((canvas) => {
      if (!face || size.width === 0) return;
      if (reduced) {
        const k = clamp((now - stillChange.get()) / FADE);
        if (k < 1) {
          drawStory(canvas, face, capRatio, frame, copy, 0, stillBefore.get());
          const layer = Skia.Paint();
          layer.setAlphaf(k);
          canvas.saveLayer(layer);
          drawStory(canvas, face, capRatio, frame, copy, 0, still.get());
          canvas.restore();
        } else {
          drawStory(canvas, face, capRatio, frame, copy, 0, still.get());
        }
        return;
      }
      drawStory(canvas, face, capRatio, frame, copy, storyTime(now), -1);
    });
  });

  const gesture = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      downAt.set(clock.get());
      downX.set(e.absoluteX);
      downY.set(e.absoluteY);
      if (!reduced) held.set(storyTime(clock.get()));
    })
    .onFinalize((e) => {
      const now = clock.get();
      const moved = Math.hypot(e.absoluteX - downX.get(), e.absoluteY - downY.get());
      const tap = now - downAt.get() < TAP_TIME && moved < TAP_SLOP;
      const forward = e.x >= size.width / 3;
      if (reduced) {
        stillBefore.set(still.get());
        still.set(forward ? (still.get() + 1) % 3 : Math.max(still.get() - 1, 0));
        stillChange.set(now);
        scheduleOnRN(haptic);
        return;
      }
      const at = Math.max(held.get(), 0);
      held.set(-1);
      if (tap) {
        const current = cardAt(at);
        const target = forward ? (current === 2 ? 8.8 : CARDS[current + 1]) : CARDS[Math.max(current - 1, 0)];
        start.set(now - target);
        scheduleOnRN(haptic);
      } else {
        start.set(now - at);
      }
      playing.set(true);
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View
      style={styles.screen}
      onLayout={onLayout}
      accessible
      accessibilityLabel={`${kicker}. ${title.join(' ')}. ${caption.join(' ')} ${lines.join(' ')}`}
      accessibilityHint="Tap the right side for the next card, the left side for the previous one."
    >
      <GestureDetector gesture={gesture}>
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#0d2433' },
});
