import {
  BlurStyle,
  PaintStyle,
  type SkCanvas,
  type SkPathBuilder,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
} from '@shopify/react-native-skia';

import type { Layout } from './crossStitchFont';
import { LOOP, REST_THREAD, SECOND_THREAD, needleScript, restPresent, secondPresent, threadBlend } from './crossStitchMotion';

/*
 Cross Stitch Input — what is drawn. The cloth, the hoop and the printed border are baked once into
 one image (2 px per pt); every frame draws only the name's stitches, the needle and its thread.
 */

export type RGB = [number, number, number];

/** The screen's geometry (pt): canvas size, hoop centre, outer hoop and fabric radius, grid cell. */
export type Geo = {
  w: number;
  h: number;
  cx: number;
  cy: number;
  hoopR: number;
  fabricR: number;
  cell: number;
  /** Where the needle rests, stuck in the cloth, and its angle there (rad). */
  parkX: number;
  parkY: number;
};

/** Cells across the fabric. */
export const CELLS = 30;

export function makeGeo(w: number, h: number, hero: { x: number; y: number; width: number; height: number }): Geo {
  const hoopR = Math.min(hero.width - 36, hero.height - 20, 380) / 2;
  const fabricR = hoopR - 15;
  const cell = (2 * fabricR) / CELLS;
  const cx = hero.x + hero.width / 2;
  const cy = hero.y + hero.height / 2;
  return { w, h, cx, cy, hoopR, fabricR, cell, parkX: cx + fabricR * 0.55, parkY: cy + fabricR * 0.36 };
}

export const THREADS: { name: string; rgb: RGB; hex: string }[] = [
  { name: 'Madder', rgb: [0.66, 0.23, 0.2], hex: '#A83B33' },
  { name: 'Indigo', rgb: [0.2, 0.28, 0.5], hex: '#33477F' },
  { name: 'Marigold', rgb: [0.83, 0.55, 0.15], hex: '#D48C26' },
  { name: 'Moss', rgb: [0.36, 0.48, 0.28], hex: '#5C7A47' },
  { name: 'Plum', rgb: [0.46, 0.26, 0.43], hex: '#76426E' },
];
/** The printed border's thread: a soft sage. */
export const BORDER_RGB: RGB = [0.5, 0.6, 0.47];

function col(c: RGB, a: number) {
  'worklet';
  return Float32Array.of(c[0], c[1], c[2], a);
}

function shade(c: RGB, k: number): RGB {
  'worklet';
  // k < 0 darkens towards a warm brown-black, k > 0 lightens towards warm cream.
  if (k < 0) return [c[0] * (1 + k) + 0.08 * -k, c[1] * (1 + k) + 0.05 * -k, c[2] * (1 + k) + 0.04 * -k];
  return [c[0] + (0.99 - c[0]) * k, c[1] + (0.96 - c[1]) * k, c[2] + (0.9 - c[2]) * k];
}

export function mixRGB(a: RGB, b: RGB, t: number): RGB {
  'worklet';
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

/** Adds one X stitch at cell centre (x, y) to the builder; f = 0..1 how far it is worked. */
function addStitch(b: SkPathBuilder, x: number, y: number, h: number, f: number) {
  'worklet';
  // First leg bottom-left → top-right (f 0–0.42), second bottom-right → top-left (0.5–0.92):
  // every top leg lies the same way, as on a well-worked sampler.
  const a = Math.min(1, Math.max(0, f / 0.42));
  if (a > 0) {
    b.moveTo(x - h, y + h);
    b.lineTo(x - h + 2 * h * a, y + h - 2 * h * a);
  }
  const c = Math.min(1, Math.max(0, (f - 0.5) / 0.42));
  if (c > 0) {
    b.moveTo(x + h, y + h);
    b.lineTo(x + h - 2 * h * c, y + h - 2 * h * c);
  }
}

/** Where the needle's point is while stitch k is at fraction f (pt). */
export function needlePoint(x: number, y: number, h: number, f: number): [number, number] {
  'worklet';
  if (f < 0.42) {
    const a = f / 0.42;
    return [x - h + 2 * h * a, y + h - 2 * h * a];
  }
  if (f < 0.5) {
    const a = (f - 0.42) / 0.08;
    return [x + h, y - h + 2 * h * a];
  }
  if (f < 0.92) {
    const a = (f - 0.5) / 0.42;
    return [x + h - 2 * h * a, y + h - 2 * h * a];
  }
  return [x - h, y - h];
}

/**
 * Draws a set of stitches: cells (xs, ys) in grid units around the fabric centre, the first
 * `present` of them worked (fractional = the one in progress), shifted by (ox, oy) cells per glyph
 * through `shift` (null = no shift). Thread: a soft shadow, the floss, and two dashed plies on top
 * so it reads as twisted strands.
 */
export function drawStitches(
  canvas: SkCanvas,
  g: Geo,
  xs: number[],
  ys: number[],
  present: number,
  rgb: RGB,
  alpha: number,
  shiftX: number[] | null,
  shiftY: number[] | null,
  glyphOf: number[] | null,
  glyphPresent: number[] | null,
  first: number[] | null,
) {
  'worklet';
  if (present <= 0 || alpha <= 0.002) return;
  const b = Skia.PathBuilder.Make();
  const h = g.cell * 0.4;
  const n = Math.min(xs.length, Math.ceil(present));
  for (let k = 0; k < n; k += 1) {
    // Either one running count for the whole set, or a count per glyph (letters typed at
    // different moments are worked independently).
    const f =
      glyphPresent && first && glyphOf
        ? Math.min(1, Math.max(0, glyphPresent[glyphOf[k]] - (k - first[glyphOf[k]])))
        : Math.min(1, present - k);
    if (f <= 0) continue;
    let x = xs[k];
    let y = ys[k];
    if (shiftX && shiftY && glyphOf) {
      x += shiftX[glyphOf[k]];
      y += shiftY[glyphOf[k]];
    }
    addStitch(b, g.cx + x * g.cell, g.cy + y * g.cell, h, f);
  }
  const path = b.build();
  const width = g.cell * 0.33;

  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setStyle(PaintStyle.Stroke);
  shadow.setStrokeWidth(width * 1.15);
  shadow.setStrokeCap(StrokeCap.Round);
  shadow.setColor(Float32Array.of(0.24, 0.16, 0.1, 0.32 * alpha));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 0.9, true));
  canvas.save();
  canvas.translate(0.45, 0.85);
  canvas.drawPath(path, shadow);
  canvas.restore();

  const floss = Skia.Paint();
  floss.setAntiAlias(true);
  floss.setStyle(PaintStyle.Stroke);
  floss.setStrokeWidth(width);
  floss.setStrokeCap(StrokeCap.Round);
  floss.setStrokeJoin(StrokeJoin.Round);
  floss.setColor(col(rgb, alpha));
  canvas.drawPath(path, floss);

  const ply = Skia.Paint();
  ply.setAntiAlias(true);
  ply.setStyle(PaintStyle.Stroke);
  ply.setStrokeWidth(width * 0.34);
  ply.setStrokeCap(StrokeCap.Butt);
  ply.setPathEffect(Skia.PathEffect.MakeDash([1.5, 1.3], 0));
  ply.setColor(col(shade(rgb, 0.45), 0.7 * alpha));
  canvas.save();
  canvas.translate(-width * 0.16, -width * 0.16);
  canvas.drawPath(path, ply);
  canvas.restore();
  ply.setPathEffect(Skia.PathEffect.MakeDash([1.5, 1.3], 1.4));
  ply.setColor(col(shade(rgb, -0.35), 0.55 * alpha));
  canvas.save();
  canvas.translate(width * 0.16, width * 0.16);
  canvas.drawPath(path, ply);
  canvas.restore();
}

/**
 * The needle (point at x, y, lying along `angle`, eye up-right) and its thread, which runs from the
 * eye in a soft sag down to (fromX, fromY), where it last went into the cloth.
 */
export function drawNeedle(
  canvas: SkCanvas,
  x: number,
  y: number,
  angle: number,
  lift: number,
  fromX: number,
  fromY: number,
  rgb: RGB,
  alpha: number,
) {
  'worklet';
  if (alpha <= 0.002) return;
  const L = 58;
  const ca = Math.cos(angle);
  const sa = Math.sin(angle);
  const eyeX = x + ca * (L - 4);
  const eyeY = y + sa * (L - 4);

  // Thread: from the eye, sagging under its weight, to the hole it came out of.
  const thread = Skia.PathBuilder.Make();
  const midX = (eyeX + fromX) / 2;
  const midY = Math.max(eyeY, fromY) + 10 + Math.hypot(eyeX - fromX, eyeY - fromY) * 0.18;
  thread.moveTo(fromX, fromY);
  thread.quadTo(midX, midY, eyeX, eyeY);
  const tp = thread.build();
  const tpaint = Skia.Paint();
  tpaint.setAntiAlias(true);
  tpaint.setStyle(PaintStyle.Stroke);
  tpaint.setStrokeCap(StrokeCap.Round);
  tpaint.setStrokeWidth(2.6);
  tpaint.setColor(Float32Array.of(0.2, 0.13, 0.08, 0.18 * alpha));
  tpaint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.4, true));
  canvas.save();
  canvas.translate(1, 3);
  canvas.drawPath(tp, tpaint);
  canvas.restore();
  tpaint.setMaskFilter(null);
  tpaint.setStrokeWidth(2.2);
  tpaint.setColor(col(rgb, alpha));
  canvas.drawPath(tp, tpaint);
  tpaint.setStrokeWidth(0.8);
  tpaint.setColor(col(shade(rgb, 0.4), 0.6 * alpha));
  canvas.save();
  canvas.translate(-0.5, -0.5);
  canvas.drawPath(tp, tpaint);
  canvas.restore();

  // Needle: a tapered steel shaft. Its shadow falls further away the higher it is lifted.
  const nb = Skia.PathBuilder.Make();
  nb.moveTo(0, 0);
  nb.lineTo(L - 8, -1.8);
  nb.quadTo(L + 1.5, -2.1, L + 1.5, 0);
  nb.quadTo(L + 1.5, 2.1, L - 8, 1.8);
  nb.close();
  const needle = nb.build();
  const eye = Skia.PathBuilder.Make().addOval(Skia.XYWHRect(L - 8, -0.7, 6.5, 1.4)).build();

  const sh = Skia.Paint();
  sh.setAntiAlias(true);
  sh.setColor(Float32Array.of(0.22, 0.15, 0.08, 0.3 * alpha));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.2 + lift * 2, true));
  canvas.save();
  canvas.translate(x + 1.5 + lift * 5, y + 2.5 + lift * 7);
  canvas.rotate((angle * 180) / Math.PI, 0, 0);
  canvas.drawPath(needle, sh);
  canvas.restore();

  canvas.save();
  canvas.translate(x, y);
  canvas.rotate((angle * 180) / Math.PI, 0, 0);
  const steel = Skia.Paint();
  steel.setAntiAlias(true);
  steel.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, -2.1),
      Skia.Point(0, 2.1),
      [
        Float32Array.of(0.97, 0.97, 0.96, alpha),
        Float32Array.of(0.62, 0.65, 0.68, alpha),
        Float32Array.of(0.4, 0.42, 0.45, alpha),
        Float32Array.of(0.8, 0.82, 0.83, alpha),
      ],
      [0, 0.35, 0.7, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawPath(needle, steel);
  const hole = Skia.Paint();
  hole.setAntiAlias(true);
  hole.setColor(Float32Array.of(0.3, 0.28, 0.27, alpha));
  canvas.drawPath(eye, hole);
  canvas.restore();
}

/** The needle's point on stitch `present` of a layout, and the hole its thread comes from. */
function workPoint(g: Geo, L: Layout, present: number): number[] {
  'worklet';
  if (L.total === 0) return [g.parkX, g.parkY, g.parkX, g.parkY];
  const h = g.cell * 0.4;
  const k = Math.min(L.total - 1, Math.max(0, Math.floor(present)));
  const f = Math.min(1, Math.max(0, present - k));
  const x = g.cx + L.cx[k] * g.cell;
  const y = g.cy + L.cy[k] * g.cell;
  const p = needlePoint(x, y, h, f);
  return [p[0], p[1], x - h, y + h];
}

/** The needle in the loop: [x, y, angle, lift, fromX, fromY]. */
export function autoNeedle(t: number, g: Geo, rest: Layout, second: Layout): number[] {
  'worklet';
  const w = needleScript(t);
  const angle = -0.95;
  const a = workPoint(g, rest, Math.min(rest.total - 0.001, restPresent(t, rest.total)));
  const b = workPoint(g, second, Math.min(second.total - 0.001, secondPresent(t, second.total)));
  // The knot: one small loop round the last hole.
  const kr = g.cell * 0.9 * Math.sin(Math.PI * w[3]);
  const ka = w[3] * Math.PI * 2;
  const bx = b[0] + kr * Math.cos(ka);
  const by = b[1] + kr * Math.sin(ka);
  const work = w[1] + w[2];
  const x = g.parkX * w[0] + a[0] * w[1] + bx * w[2];
  const y = g.parkY * w[0] + a[1] * w[1] + by * w[2];
  // The thread comes out of the last stitch of whichever name is on the cloth.
  const restOn = restPresent(t, rest.total) > 0.5;
  const tail = restOn ? a : b;
  const fromX = work > 0.5 ? (w[1] > w[2] ? a[2] : b[2]) : tail[2];
  const fromY = work > 0.5 ? (w[1] > w[2] ? a[3] : b[3]) : tail[3];
  // A small bob per stitch while working, a lift while travelling.
  const bob = work * 0.25 * (1 + Math.sin(t * 2 * Math.PI * 9));
  return [x, y, angle + 0.06 * Math.sin((t / LOOP) * 2 * Math.PI * 4) * work, w[4] + bob, fromX, fromY];
}

/** One frame of the loop's cloth: both names at their stitch counts, and the needle. */
export function drawLoopFrame(canvas: SkCanvas, g: Geo, t: number, alpha: number, withNeedle: boolean, rest: Layout, second: Layout) {
  'worklet';
  const restRGB = THREADS[REST_THREAD].rgb;
  const secondRGB = THREADS[SECOND_THREAD].rgb;
  drawStitches(canvas, g, rest.cx, rest.cy, restPresent(t, rest.total), restRGB, alpha, null, null, null, null, null);
  drawStitches(canvas, g, second.cx, second.cy, secondPresent(t, second.total), secondRGB, alpha, null, null, null, null, null);
  if (withNeedle) {
    const n = autoNeedle(t, g, rest, second);
    drawNeedle(canvas, n[0], n[1], n[2], n[3], n[4], n[5], mixRGB(restRGB, secondRGB, threadBlend(t)), alpha);
  }
}
