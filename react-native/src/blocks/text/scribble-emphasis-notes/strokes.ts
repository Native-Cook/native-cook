// The marks as geometry. Built once per layout on the JS thread (seeded, no randomness per frame),
// drawn on the UI thread by the worklets at the bottom.
import { Skia, type SkCanvas, type SkPaint } from '@shopify/react-native-skia';

import {
  BAND,
  BRACE,
  CIRCLE,
  clamp,
  hash,
  HEAD,
  JITTER,
  mix,
  NOTE,
  NOTE_WIDTH,
  PEN_LIFT,
  PEN_WIDTH,
  STEP,
  TAPER_IN,
  TAPER_IN_SPAN,
  TAPER_OUT,
  TAPER_OUT_SPAN,
  WAVE,
  WIDTH_NOISE,
} from './motion';
import type { Band } from './page';

type Point = { x: number; y: number };
type Stroke = { points: Point[]; widths: number[]; length: number };

/** A mark's strokes, flat: x, y, width, share of the mark drawn when the pen is here, pen (0 = moved here in the air). */
export const STRIDE = 5;

function smoothstep(a: number, b: number, v: number) {
  const u = clamp((v - a) / (b - a), 0, 1);
  return u * u * (3 - 2 * u);
}

/** Catmull-Rom through the points. */
function curve(points: Point[]): Point[] {
  if (points.length < 3) return points;
  const out: Point[] = [];
  for (let i = 0; i < points.length - 1; i += 1) {
    const p0 = points[Math.max(i - 1, 0)];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[Math.min(i + 2, points.length - 1)];
    const steps = Math.max(2, Math.ceil(Math.hypot(p2.x - p1.x, p2.y - p1.y) / (STEP * 0.5)));
    for (let s = 0; s < steps; s += 1) {
      const u = s / steps;
      const u2 = u * u;
      const u3 = u2 * u;
      out.push({
        x: 0.5 * (2 * p1.x + (p2.x - p0.x) * u + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * u2 + (3 * p1.x - p0.x - 3 * p2.x + p3.x) * u3),
        y: 0.5 * (2 * p1.y + (p2.y - p0.y) * u + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * u2 + (3 * p1.y - p0.y - 3 * p2.y + p3.y) * u3),
      });
    }
  }
  out.push(points[points.length - 1]);
  return out;
}

/** The same line through points STEP apart. */
function resample(points: Point[]): Point[] {
  const out: Point[] = [points[0]];
  let carried = 0;
  for (let i = 1; i < points.length; i += 1) {
    const a = points[i - 1];
    const b = points[i];
    const d = Math.hypot(b.x - a.x, b.y - a.y);
    if (d === 0) continue;
    let at = STEP - carried;
    while (at <= d) {
      out.push({ x: mix(a.x, b.x, at / d), y: mix(a.y, b.y, at / d) });
      at += STEP;
    }
    carried = d - (at - STEP);
  }
  const last = points[points.length - 1];
  const tail = out[out.length - 1];
  if (Math.hypot(last.x - tail.x, last.y - tail.y) > 0.2) out.push(last);
  return out;
}

/** A pen stroke: a hand's wobble across the line, pressure that swells in and flicks out. */
function ink(through: Point[], width: number, seed: number, smooth = true): Stroke {
  const line = resample(smooth ? curve(through) : through);
  const phase = [hash(seed) * 6.28, hash(seed + 1) * 6.28, hash(seed + 2) * 6.28];
  const lengths = [0];
  for (let i = 1; i < line.length; i += 1) lengths.push(lengths[i - 1] + Math.hypot(line[i].x - line[i - 1].x, line[i].y - line[i - 1].y));
  const length = Math.max(lengths[lengths.length - 1], 0.001);
  // Short strokes (a dot, an arrow head) wobble less.
  const wobble = JITTER * clamp(length / 40, 0.15, 1);
  const points: Point[] = [];
  const widths: number[] = [];
  for (let i = 0; i < line.length; i += 1) {
    const before = line[Math.max(i - 1, 0)];
    const after = line[Math.min(i + 1, line.length - 1)];
    const d = Math.hypot(after.x - before.x, after.y - before.y) || 1;
    const s = lengths[i];
    const off = wobble * (0.6 * Math.sin(s * 0.21 + phase[0]) + 0.4 * Math.sin(s * 0.47 + phase[1]));
    points.push({ x: line[i].x - ((after.y - before.y) / d) * off, y: line[i].y + ((after.x - before.x) / d) * off });
    const u = s / length;
    // A dot is pressed, not drawn: no taper.
    const pressure = length < 4 ? 1.25 : mix(TAPER_IN, 1, smoothstep(0, TAPER_IN_SPAN, u)) * mix(1, TAPER_OUT, smoothstep(1 - TAPER_OUT_SPAN, 1, u));
    widths.push(width * pressure * (1 + WIDTH_NOISE * Math.sin(s * 0.33 + phase[2])));
  }
  return { points, widths, length };
}

/** Flattens strokes into one mark; the pen takes PEN_LIFT points of "air" between two strokes. */
function pack(strokes: Stroke[]): number[] {
  const total = strokes.reduce((sum, stroke) => sum + stroke.length, 0) + PEN_LIFT * (strokes.length - 1);
  const out: number[] = [];
  let done = 0;
  for (const stroke of strokes) {
    let along = 0;
    for (let i = 0; i < stroke.points.length; i += 1) {
      if (i > 0) along += Math.hypot(stroke.points[i].x - stroke.points[i - 1].x, stroke.points[i].y - stroke.points[i - 1].y);
      out.push(stroke.points[i].x, stroke.points[i].y, stroke.widths[i], Math.min((done + along) / total, 1), i === 0 ? 0 : 1);
    }
    done += stroke.length + PEN_LIFT;
  }
  if (out.length >= STRIDE) out[out.length - STRIDE + 3] = 1;
  return out;
}

/** An ellipse round one band: a little over one turn, spiralling out so its end crosses its start. */
export function circleMark(band: Band, fontSize: number, seed: number): number[] {
  const cx = (band.x0 + band.x1) / 2;
  const cy = band.base - fontSize * 0.3;
  const rx = (band.x1 - band.x0) / 2 + CIRCLE.padX;
  const ry = fontSize * CIRCLE.ry;
  const count = 72;
  const points: Point[] = [];
  for (let i = 0; i <= count; i += 1) {
    const u = i / count;
    const angle = CIRCLE.start + CIRCLE.turns * 2 * Math.PI * u;
    const r = 1 + CIRCLE.spiral * (u - 0.5);
    const px = rx * r * Math.cos(angle);
    const py = ry * r * Math.sin(angle);
    points.push({
      x: cx + px * Math.cos(CIRCLE.tilt) - py * Math.sin(CIRCLE.tilt),
      y: cy + px * Math.sin(CIRCLE.tilt) + py * Math.cos(CIRCLE.tilt),
    });
  }
  return pack([ink(points, PEN_WIDTH, seed, false)]);
}

/** A wave under each band, one stroke per line. */
export function underlineMark(bands: Band[], seed: number): number[] {
  return pack(
    bands.map((band, n) => {
      const x0 = band.x0 - WAVE.overshoot;
      const x1 = band.x1 + WAVE.overshoot;
      const points: Point[] = [];
      const phase = hash(seed + n * 7) * 6.28;
      for (let x = x0; x <= x1; x += 1.5) {
        const u = (x - x0) / (x1 - x0);
        points.push({ x, y: band.base + WAVE.drop - WAVE.rise * u + WAVE.amp * Math.sin((2 * Math.PI * (x - x0)) / WAVE.length + phase) });
      }
      return ink(points, PEN_WIDTH, seed + n * 13, false);
    }),
  );
}

/** A brace in the margin, from `top` to `bottom`, opening to the left of `x`. */
export function bracketMark(x: number, top: number, bottom: number, seed: number): { data: number[]; nose: Point } {
  const curl = Math.min(BRACE.curl, (bottom - top) / 4);
  const mid = (top + bottom) / 2;
  const edge = x + BRACE.depth;
  const nose = { x: edge + BRACE.nose, y: mid };
  const points: Point[] = [
    { x, y: top },
    { x: edge - 1, y: top + curl * 0.45 },
    { x: edge, y: top + curl },
    { x: edge, y: mid - curl },
    { x: edge + BRACE.nose * 0.5, y: mid - curl * 0.3 },
    nose,
    { x: edge + BRACE.nose * 0.5, y: mid + curl * 0.3 },
    { x: edge, y: mid + curl },
    { x: edge, y: bottom - curl },
    { x: edge - 1, y: bottom - curl * 0.45 },
    { x, y: bottom },
  ];
  return { data: pack([ink(points, PEN_WIDTH, seed)]), nose };
}

/**
 * Handwriting, as strokes: x to the right, y down, 0 the top of a small letter and 1 the baseline.
 * `advance` is the letter's width in the same unit.
 */
const LETTERS: Record<string, { advance: number; strokes: number[][][] }> = {
  e: {
    advance: 0.95,
    strokes: [[[0.12, 0.54], [0.5, 0.5], [0.84, 0.4], [0.74, 0.1], [0.45, 0], [0.16, 0.2], [0.08, 0.55], [0.22, 0.88], [0.5, 1], [0.88, 0.8]]],
  },
  x: {
    advance: 0.9,
    strokes: [
      [[0.06, 0.02], [0.42, 0.48], [0.86, 1]],
      [[0.84, 0], [0.48, 0.5], [0.04, 1]],
    ],
  },
  a: {
    advance: 1.02,
    strokes: [
      [[0.82, 0.22], [0.6, 0.02], [0.3, 0.04], [0.1, 0.36], [0.12, 0.76], [0.36, 0.98], [0.66, 0.8], [0.83, 0.34]],
      [[0.84, 0.02], [0.85, 0.6], [0.9, 0.92], [1.04, 0.97]],
    ],
  },
  m: {
    advance: 1.45,
    strokes: [
      [[0.06, 0.02], [0.08, 0.5], [0.06, 1]],
      [[0.08, 0.4], [0.2, 0.1], [0.4, 0.02], [0.58, 0.2], [0.62, 0.6], [0.62, 1]],
      [[0.63, 0.4], [0.78, 0.1], [0.98, 0.02], [1.16, 0.2], [1.2, 0.6], [1.22, 1]],
    ],
  },
  '!': {
    advance: 0.4,
    strokes: [
      [[0.22, -0.78], [0.19, -0.1], [0.16, 0.5]],
      [[0.14, 0.93], [0.17, 1]],
    ],
  },
};

/** A word in that hand, its baseline starting at (x, baseline), tilted about that point. */
export function noteMark(word: string, x: number, baseline: number, seed: number): { data: number[]; width: number } {
  const strokes: Stroke[] = [];
  const cos = Math.cos(NOTE.tilt);
  const sin = Math.sin(NOTE.tilt);
  let pen = 0;
  let n = 0;
  for (const letter of word) {
    const glyph = LETTERS[letter];
    if (!glyph) {
      pen += 0.6;
      continue;
    }
    // Each letter sits a touch high or low, as a hand writes.
    const bob = (hash(seed + n * 3) - 0.5) * 0.12;
    for (const stroke of glyph.strokes) {
      const points = stroke.map(([gx, gy]) => {
        const px = (pen + gx) * NOTE.xHeight;
        const py = (gy - 1 + bob) * NOTE.xHeight;
        return { x: x + px * cos - py * sin, y: baseline + px * sin + py * cos };
      });
      strokes.push(ink(points, NOTE_WIDTH, seed + n * 17 + strokes.length));
    }
    pen += glyph.advance + NOTE.gap;
    n += 1;
  }
  return { data: pack(strokes), width: (pen - NOTE.gap) * NOTE.xHeight };
}

/** A curved shaft through the points and a two-stroke head at its end. */
export function arrowMark(through: Point[], seed: number): number[] {
  const shaft = ink(through, PEN_WIDTH, seed);
  const tip = shaft.points[shaft.points.length - 1];
  const back = shaft.points[Math.max(shaft.points.length - 4, 0)];
  const angle = Math.atan2(back.y - tip.y, back.x - tip.x);
  const head = [-HEAD.spread, HEAD.spread].map((turn, i) =>
    ink(
      [
        { x: tip.x + HEAD.length * Math.cos(angle + turn), y: tip.y + HEAD.length * Math.sin(angle + turn) },
        { x: tip.x + HEAD.length * 0.4 * Math.cos(angle + turn * 0.8), y: tip.y + HEAD.length * 0.4 * Math.sin(angle + turn * 0.8) },
        tip,
      ],
      PEN_WIDTH,
      seed + 5 + i,
      false,
    ),
  );
  return pack([shaft, ...head]);
}

/** Bands, flat: x0, x1, baseline. */
export const BAND_STRIDE = 3;
export function flatBands(bands: Band[]): number[] {
  const out: number[] = [];
  for (const band of bands) out.push(band.x0, band.x1, band.base);
  return out;
}

// ---- UI thread ----

/** Draws `amount` (0…1) of a mark, from its start. The paint's width is set per segment: that is the taper. */
export function drawInk(canvas: SkCanvas, paint: SkPaint, data: number[], amount: number) {
  'worklet';
  if (amount <= 0.0005) return;
  const count = data.length / STRIDE;
  for (let i = 1; i < count; i += 1) {
    const o = i * STRIDE;
    const f0 = data[o - STRIDE + 3];
    if (f0 >= amount) break;
    if (data[o + 4] === 0) continue;
    const f1 = data[o + 3];
    const x0 = data[o - STRIDE];
    const y0 = data[o - STRIDE + 1];
    let x1 = data[o];
    let y1 = data[o + 1];
    if (f1 > amount) {
      const k = (amount - f0) / (f1 - f0);
      x1 = mix(x0, x1, k);
      y1 = mix(y0, y1, k);
    }
    paint.setStrokeWidth((data[o - STRIDE + 2] + data[o + 2]) / 2);
    canvas.drawLine(x0, y0, x1, y1, paint);
  }
}

/** Where a highlight's leading edge is when `amount` of it is drawn: [x, baseline]. */
export function bandHead(bands: number[], amount: number) {
  'worklet';
  const count = bands.length / BAND_STRIDE;
  let total = 0;
  for (let i = 0; i < count; i += 1) total += bands[i * BAND_STRIDE + 1] - bands[i * BAND_STRIDE];
  let left = clamp(amount, 0, 1) * total;
  for (let i = 0; i < count; i += 1) {
    const x0 = bands[i * BAND_STRIDE];
    const w = bands[i * BAND_STRIDE + 1] - x0;
    if (left <= w || i === count - 1) return [x0 + Math.min(left, w), bands[i * BAND_STRIDE + 2]];
    left -= w;
  }
  return [0, 0];
}

/**
 * Draws `amount` (0…1) of a highlighter swipe over the bands, line after line. The edges wobble with a seeded
 * hash of the position along the line, so the part already drawn never changes as the swipe grows.
 */
export function drawHighlight(canvas: SkCanvas, fill: SkPaint, streak: SkPaint, bands: number[], fontSize: number, seed: number, amount: number) {
  'worklet';
  if (amount <= 0.0005) return;
  const count = bands.length / BAND_STRIDE;
  let total = 0;
  for (let i = 0; i < count; i += 1) total += bands[i * BAND_STRIDE + 1] - bands[i * BAND_STRIDE];
  let left = clamp(amount, 0, 1) * total;
  for (let i = 0; i < count && left > 0.01; i += 1) {
    const x0 = bands[i * BAND_STRIDE] - BAND.padX;
    const full = bands[i * BAND_STRIDE + 1] + BAND.padX - x0;
    const base = bands[i * BAND_STRIDE + 2];
    const shown = Math.min(left / (full - 2 * BAND.padX), 1) * full;
    left -= full - 2 * BAND.padX;
    const top = base - BAND.up * fontSize;
    const bottom = base + BAND.down * fontSize;
    const steps = Math.max(1, Math.ceil(shown / BAND.step));
    const path = Skia.PathBuilder.Make();
    // The nib is a chisel: both ends lean.
    for (let k = 0; k <= steps; k += 1) {
      const x = x0 + Math.min(k * BAND.step, shown);
      const y = top + (hash(seed + i * 31 + k) - 0.5) * 2 * BAND.wobble;
      if (k === 0) path.moveTo(x + BAND.slant, y);
      else path.lineTo(x + (k === steps ? BAND.slant : 0), y);
    }
    for (let k = steps; k >= 0; k -= 1) {
      const x = x0 + Math.min(k * BAND.step, shown);
      const y = bottom + (hash(seed + i * 31 + k + 400) - 0.5) * 2 * BAND.wobble;
      path.lineTo(x, y);
    }
    path.close();
    canvas.drawPath(path.detach(), fill);
    // Where the nib pressed harder, the ink is a little deeper.
    streak.setStrokeWidth(fontSize * 0.24);
    canvas.drawLine(x0 + BAND.slant, base + BAND.down * fontSize * 0.2, x0 + shown, base + BAND.down * fontSize * 0.2, streak);
  }
}

/** The bands words a…b cover, flat, from the measured words and lines. */
export function wordBands(a: number, b: number, wordX: number[], wordW: number[], wordLine: number[], lineLeft: number[], lineRight: number[], lineBase: number[]) {
  'worklet';
  const out: number[] = [];
  const first = wordLine[a];
  const last = wordLine[b];
  for (let line = first; line <= last; line += 1) {
    out.push(line === first ? wordX[a] : lineLeft[line], line === last ? wordX[b] + wordW[b] : lineRight[line], lineBase[line]);
  }
  return out;
}

/** The word under a point, or the nearest one on the nearest line. */
export function wordAt(x: number, y: number, fontSize: number, wordX: number[], wordW: number[], wordLine: number[], lineBase: number[]) {
  'worklet';
  let line = 0;
  let near = Infinity;
  for (let i = 0; i < lineBase.length; i += 1) {
    const d = Math.abs(lineBase[i] - fontSize * 0.32 - y);
    if (d < near) {
      near = d;
      line = i;
    }
  }
  let word = -1;
  near = Infinity;
  for (let i = 0; i < wordX.length; i += 1) {
    if (wordLine[i] !== line) continue;
    const d = x < wordX[i] ? wordX[i] - x : x > wordX[i] + wordW[i] ? x - wordX[i] - wordW[i] : 0;
    if (d < near) {
      near = d;
      word = i;
    }
  }
  return word;
}
