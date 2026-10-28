import {
  BlurStyle,
  ClipOp,
  PaintStyle,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
  type SkCanvas,
  type SkColor,
  type SkPaint,
  type SkPath,
} from '@shopify/react-native-skia';

import { hash, R, type Piece } from './omeletteMotion';

/*
 Omelette Topping Builder — the food, drawn in code (a port of OmeletteToppingBuilderArt.swift, same
 shapes and colours). Worklets; helpers first.

 Shelf ingredients are drawn in a unit box (-0.5 … 0.5, y down) that the caller scales: a red bell
 pepper with three lobes and a curled stem, a bunch of four spinach leaves, two wavy bacon strips,
 three cherry tomatoes, a button mushroom. Their light comes from the top left; soft spots are radial
 gradients (no blur), so they scale cleanly.

 Pieces are drawn in their own points around (0, 0), already rotated by the caller; `lx, ly` is the
 direction of the light in the piece's frame and `sx, sy` the offset of its shadow, so the cubes,
 leaves and strips keep one light whatever their angle.

 The omelette is drawn in its own frame: a half disc of radius R with its fold edge on y = 0 and the
 bulge towards +y. Every draw takes the group's alpha `a`.
*/

export function hex(v: number, a = 1): SkColor {
  'worklet';
  return Float32Array.of(((v >> 16) & 0xff) / 255, ((v >> 8) & 0xff) / 255, (v & 0xff) / 255, a);
}

/** Android's BlurMaskFilter radius as a Skia sigma, so the stacks soften alike. */
export function sigma(r: number) {
  'worklet';
  return 0.57735 * r + 0.5;
}

/** A gradient stop list: [hex, alpha, location] triples flattened. */
type Stops = number[];

function colors(stops: Stops, a: number) {
  'worklet';
  const out: SkColor[] = [];
  for (let i = 0; i < stops.length; i += 3) out.push(hex(stops[i], stops[i + 1] * a));
  return out;
}

function positions(stops: Stops) {
  'worklet';
  const out: number[] = [];
  for (let i = 0; i < stops.length; i += 3) out.push(stops[i + 2]);
  return out;
}

export function solid(v: number, alpha: number, a: number): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(hex(v, alpha * a));
  return p;
}

export function linear(stops: Stops, x0: number, y0: number, x1: number, y1: number, a: number): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors(stops, a), positions(stops), TileMode.Clamp));
  return p;
}

export function radial(stops: Stops, x: number, y: number, r: number, a: number): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeRadialGradient({ x, y }, Math.max(1e-4, r), colors(stops, a), positions(stops), TileMode.Clamp));
  return p;
}

export function stroke(p: SkPaint, w: number, round: boolean): SkPaint {
  'worklet';
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(w);
  if (round) {
    p.setStrokeCap(StrokeCap.Round);
    p.setStrokeJoin(StrokeJoin.Round);
  }
  return p;
}

export function circlePath(x: number, y: number, r: number): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  p.addCircle(x, y, r);
  return p;
}

/** A soft elliptical spot fading from `alpha` at its centre to 0 at its edge. */
export function blob(c: SkCanvas, x: number, y: number, rx: number, ry: number, v: number, alpha: number, a: number) {
  'worklet';
  c.save();
  c.translate(x, y);
  c.scale(1, ry / rx);
  c.drawCircle(0, 0, rx, radial([v, alpha, 0, v, 0, 1], 0, 0, rx, a));
  c.restore();
}

/** A soft ring shadow under a disc: `alpha` up to radius r0, fading to 0 at r1. */
export function ringShadow(c: SkCanvas, x: number, y: number, r0: number, r1: number, alpha: number) {
  'worklet';
  c.drawCircle(x, y, r1, radial([0, alpha, 0, 0, alpha, r0 / r1, 0, 0, 1], x, y, r1, 1));
}

/** A blurred fill, moved by (dx, dy). */
export function blurred(c: SkCanvas, p: SkPath, dx: number, dy: number, blur: number, v: number, alpha: number) {
  'worklet';
  const paint = solid(v, alpha, 1);
  paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma(blur), true));
  c.save();
  c.translate(dx, dy);
  c.drawPath(p, paint);
  c.restore();
}

/** A closed smooth curve through the points [x0, y0, x1, y1, …] (Catmull-Rom as cubic Béziers). */
function smoothClosed(p: number[]): SkPath {
  'worklet';
  const path = Skia.Path.Make();
  const n = p.length / 2;
  path.moveTo(p[0], p[1]);
  for (let i = 0; i < n; i++) {
    const i0 = ((i - 1 + n) % n) * 2;
    const i1 = i * 2;
    const i2 = ((i + 1) % n) * 2;
    const i3 = ((i + 2) % n) * 2;
    path.cubicTo(
      p[i1] + (p[i2] - p[i0]) / 6,
      p[i1 + 1] + (p[i2 + 1] - p[i0 + 1]) / 6,
      p[i2] - (p[i3] - p[i1]) / 6,
      p[i2 + 1] - (p[i3 + 1] - p[i1 + 1]) / 6,
      p[i2],
      p[i2 + 1],
    );
  }
  path.close();
  return path;
}

/** A band following a wavy centre line y = amp·sin(2πx/period + phase), between offsets a and b. */
function band(x0: number, x1: number, a: number, b: number, amp: number, period: number, phase: number, n: number): SkPath {
  'worklet';
  const path = Skia.Path.Make();
  for (let i = 0; i <= n; i++) {
    const x = x0 + ((x1 - x0) * i) / n;
    const y = amp * Math.sin((2 * Math.PI * x) / period + phase) + a;
    if (i === 0) path.moveTo(x, y);
    else path.lineTo(x, y);
  }
  for (let i = n; i >= 0; i--) {
    const x = x0 + ((x1 - x0) * i) / n;
    path.lineTo(x, amp * Math.sin((2 * Math.PI * x) / period + phase) + b);
  }
  path.close();
  return path;
}

// Shelf ingredients (unit box)

function pepper(c: SkCanvas, a: number) {
  'worklet';
  const body = Skia.Path.Make();
  body.moveTo(-0.32, -0.16);
  body.cubicTo(-0.32, -0.3, -0.14, -0.33, 0, -0.27);
  body.cubicTo(0.14, -0.33, 0.32, -0.3, 0.32, -0.16);
  body.cubicTo(0.36, 0.02, 0.34, 0.22, 0.24, 0.33);
  body.cubicTo(0.2, 0.39, 0.1, 0.41, 0.06, 0.36);
  body.cubicTo(0.03, 0.42, -0.03, 0.42, -0.06, 0.36);
  body.cubicTo(-0.1, 0.41, -0.2, 0.39, -0.24, 0.33);
  body.cubicTo(-0.34, 0.22, -0.36, 0.02, -0.32, -0.16);
  body.close();
  c.drawPath(body, radial([0xff6a48, 1, 0, 0xe42c1f, 1, 0.42, 0xb8171a, 1, 0.78, 0x7e0e12, 1, 1], -0.12, -0.12, 0.62, a));
  c.save();
  c.clipPath(body, ClipOp.Intersect, true);
  blob(c, -0.085, 0.14, 0.04, 0.24, 0x6e0a0e, 0.42, a);
  blob(c, 0.1, 0.14, 0.04, 0.24, 0x6e0a0e, 0.36, a);
  blob(c, 0, -0.25, 0.2, 0.06, 0x5c080b, 0.45, a);
  blob(c, 0.26, 0.1, 0.08, 0.26, 0x5c080b, 0.35, a);
  blob(c, -0.19, -0.04, 0.05, 0.15, 0xffffff, 0.62, a);
  blob(c, -0.04, 0.1, 0.025, 0.12, 0xffffff, 0.3, a);
  blob(c, 0.17, -0.1, 0.03, 0.08, 0xffffff, 0.38, a);
  c.restore();
  blob(c, 0, -0.27, 0.13, 0.05, 0x2b5a1c, 0.9, a);
  const stem = Skia.Path.Make();
  stem.moveTo(-0.035, -0.26);
  stem.cubicTo(-0.04, -0.35, -0.005, -0.41, 0.05, -0.43);
  stem.lineTo(0.085, -0.4);
  stem.cubicTo(0.04, -0.37, 0.035, -0.33, 0.035, -0.26);
  stem.close();
  c.drawPath(stem, linear([0x3a6e25, 1, 0, 0x74b24a, 1, 0.45, 0x3e7a2a, 1, 1], -0.04, 0, 0.06, 0, a));
  c.drawOval(Skia.XYWHRect(0.045, -0.437, 0.045, 0.035), solid(0xb5d98a, 1, a));
}

function leaf(L: number, W: number): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  p.moveTo(0, 0);
  p.cubicTo(W * 0.9, -L * 0.15, W * 1.05, -L * 0.7, 0, -L);
  p.cubicTo(-W * 1.05, -L * 0.7, -W * 0.9, -L * 0.15, 0, 0);
  p.close();
  return p;
}

const LEAVES = [-36, 0.46, 0.19, 38, 0.45, 0.18, -12, 0.62, 0.23, 15, 0.56, 0.21];

function spinach(c: SkCanvas, a: number) {
  'worklet';
  for (let i = 0; i < LEAVES.length; i += 3) {
    const deg = LEAVES[i];
    const L = LEAVES[i + 1];
    const W = LEAVES[i + 2];
    c.save();
    c.translate(0, 0.4);
    c.rotate(deg, 0, 0);
    const stalk = Skia.Path.Make();
    stalk.moveTo(0, 0);
    stalk.lineTo(0, -0.16);
    c.drawPath(stalk, stroke(solid(0x8dba5e, 1, a), 0.03, true));
    c.translate(0, -0.12);
    const blade = leaf(L, W);
    c.drawPath(blade, linear([0x3e8e3b, 1, 0, 0x2a7230, 1, 0.5, 0x1c5520, 1, 1], 0, 0, 0, -L, a));
    c.save();
    c.clipPath(blade, ClipOp.Intersect, true);
    c.drawRect(Skia.XYWHRect(0, -L, W * 1.2, L), solid(0x0a2e0e, 0.18, a));
    blob(c, -W * 0.38, -L * 0.55, W * 0.24, L * 0.2, 0xffffff, 0.16, a);
    const vein = Skia.Path.Make();
    vein.moveTo(0, -0.02);
    vein.quadTo(W * 0.08, -L * 0.5, 0, -L * 0.92);
    const fs = [0.28, 0.48, 0.68];
    for (let k = 0; k < fs.length; k++) {
      const f = fs[k];
      vein.moveTo(0, -L * f);
      vein.lineTo(W * 0.62, -L * (f + 0.13));
      vein.moveTo(0, -L * f);
      vein.lineTo(-W * 0.62, -L * (f + 0.13));
    }
    c.drawPath(vein, stroke(solid(0x9ccb6e, 0.55, a), 0.011, false));
    c.restore();
    c.drawPath(blade, stroke(solid(0x123e16, 0.35, a), 0.01, false));
    c.restore();
  }
}

function baconStrip(c: SkCanvas, len: number, w: number, phase: number, a: number) {
  'worklet';
  const amp = 0.032;
  const period = 0.34;
  const h = len / 2;
  const meat = band(-h, h, -w / 2, w / 2, amp, period, phase, 24);
  c.drawPath(meat, linear([0xc84e3c, 1, 0, 0xa2352a, 1, 0.5, 0x7a2119, 1, 1], 0, -w / 2, 0, w / 2, a));
  c.save();
  c.clipPath(meat, ClipOp.Intersect, true);
  c.drawPath(
    band(-h, h, -w * 0.12, w * 0.12, amp, period, phase, 24),
    linear([0xf8e2cf, 1, 0, 0xebc3a6, 1, 1], 0, -w * 0.12, 0, w * 0.12, a),
  );
  c.drawPath(band(-h, h, w * 0.3, w * 0.38, amp, period, phase, 24), solid(0xf1d0ba, 0.8, a));
  c.drawPath(band(-h, h, -w * 0.42, -w * 0.36, amp, period, phase, 24), solid(0xf1d0ba, 0.45, a));
  blob(c, -h * 0.55, 0, 0.07, 0.05, 0x4a120c, 0.35, a);
  blob(c, h * 0.45, w * 0.2, 0.08, 0.05, 0x4a120c, 0.3, a);
  c.drawPath(band(-h, h, -w / 2, -w * 0.42, amp, period, phase, 24), solid(0xffffff, 0.22, a));
  c.restore();
  c.drawPath(meat, stroke(solid(0x5e1a12, 0.45, a), 0.012, false));
}

function bacon(c: SkCanvas, a: number) {
  'worklet';
  c.save();
  c.translate(-0.08, 0);
  c.rotate(-66, 0, 0);
  baconStrip(c, 0.84, 0.17, 0.4, a);
  c.restore();
  c.save();
  c.translate(0.09, 0.03);
  c.rotate(-58, 0, 0);
  baconStrip(c, 0.8, 0.16, 2.2, a);
  c.restore();
}

function tomato(c: SkCanvas, x: number, y: number, r: number, a: number) {
  'worklet';
  c.drawCircle(x, y, r, radial([0xff8a68, 1, 0, 0xea3b2b, 1, 0.38, 0xb91d17, 1, 0.82, 0x86110f, 1, 1], x - 0.35 * r, y - 0.38 * r, 1.35 * r, a));
  blob(c, x - 0.34 * r, y - 0.38 * r, 0.24 * r, 0.15 * r, 0xffffff, 0.8, a);
  blob(c, x + 0.42 * r, y + 0.3 * r, 0.12 * r, 0.22 * r, 0xff9a7a, 0.35, a);
}

function tomatoes(c: SkCanvas, a: number) {
  'worklet';
  tomato(c, 0, -0.11, 0.2, a);
  tomato(c, -0.17, 0.12, 0.19, a);
  tomato(c, 0.18, 0.13, 0.18, a);
  const star = Skia.Path.Make();
  for (let i = 0; i < 10; i++) {
    const ang = (i * Math.PI) / 5 - Math.PI / 2;
    const r = i % 2 === 0 ? 0.085 : 0.028;
    const x = r * Math.cos(ang);
    const y = -0.27 + r * Math.sin(ang) * 0.7;
    if (i === 0) star.moveTo(x, y);
    else star.lineTo(x, y);
  }
  star.close();
  c.drawPath(star, solid(0x3e7b2b, 1, a));
  const stem = Skia.Path.Make();
  stem.moveTo(0, -0.27);
  stem.quadTo(0, -0.33, 0.04, -0.36);
  c.drawPath(stem, stroke(solid(0x4c8a33, 1, a), 0.022, true));
  blob(c, -0.17, -0.05, 0.03, 0.02, 0x5e2a10, 0.5, a);
  blob(c, 0.18, -0.03, 0.03, 0.02, 0x5e2a10, 0.5, a);
}

const SPOTS = [0.1, -0.15, 0.018, 0.2, -0.06, 0.014, -0.02, -0.2, 0.012, 0.24, -0.14, 0.01];

function mushroom(c: SkCanvas, a: number) {
  'worklet';
  const stem = Skia.Path.Make();
  stem.moveTo(-0.12, 0.02);
  stem.cubicTo(-0.13, 0.18, -0.16, 0.3, -0.12, 0.36);
  stem.cubicTo(-0.04, 0.4, 0.04, 0.4, 0.12, 0.36);
  stem.cubicTo(0.16, 0.3, 0.13, 0.18, 0.12, 0.02);
  stem.close();
  c.drawPath(stem, linear([0xcdbda8, 1, 0, 0xf7f0e7, 1, 0.45, 0xd4c4af, 1, 1], -0.13, 0, 0.13, 0, a));
  blob(c, 0, 0.36, 0.11, 0.03, 0xa88c6e, 0.4, a);
  c.drawOval(Skia.XYWHRect(-0.34, -0.03, 0.68, 0.13), linear([0x6e543f, 1, 0, 0x9c8066, 1, 1], 0, -0.03, 0, 0.1, a));
  const gills = Skia.Path.Make();
  for (let i = 0; i < 9; i++) {
    const x = -0.28 + 0.07 * i;
    gills.moveTo(x, 0.035);
    gills.lineTo(x * 0.35, 0.03);
  }
  c.drawPath(gills, stroke(solid(0x4e3a2a, 0.45, a), 0.008, false));
  const cap = Skia.Path.Make();
  cap.moveTo(-0.39, 0.03);
  cap.cubicTo(-0.4, -0.34, 0.4, -0.34, 0.39, 0.03);
  cap.cubicTo(0.3, 0.07, -0.3, 0.07, -0.39, 0.03);
  cap.close();
  c.drawPath(cap, radial([0xfbf5ee, 1, 0, 0xe8daca, 1, 0.5, 0xc4aa8b, 1, 0.9, 0xa98d6d, 1, 1], -0.1, -0.18, 0.52, a));
  c.save();
  c.clipPath(cap, ClipOp.Intersect, true);
  blob(c, -0.15, -0.16, 0.1, 0.05, 0xffffff, 0.7, a);
  for (let i = 0; i < SPOTS.length; i += 3) blob(c, SPOTS[i], SPOTS[i + 1], SPOTS[i + 2], SPOTS[i + 2], 0x8a6b4e, 0.45, a);
  c.translate(0, -0.01);
  c.drawPath(cap, stroke(solid(0x8e7356, 0.25, a), 0.012, false));
  c.restore();
}

export function ingredient(c: SkCanvas, kind: number, ground: boolean, a: number) {
  'worklet';
  if (ground) blob(c, 0.02, 0.44, 0.32, 0.065, 0x000000, 0.16, a);
  if (kind === 0) pepper(c, a);
  else if (kind === 1) spinach(c, a);
  else if (kind === 2) bacon(c, a);
  else if (kind === 3) tomatoes(c, a);
  else mushroom(c, a);
}

// Pieces (points)

function pieceLeaf(): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  p.moveTo(-7, 0);
  p.cubicTo(-3, -4.6, 3, -4.8, 7, 0);
  p.cubicTo(3, 4.3, -3, 4.5, -7, 0);
  p.close();
  return p;
}

function mushroomSlice(): SkPath {
  'worklet';
  const s = Skia.Path.Make();
  s.moveTo(-6, 0.5);
  s.cubicTo(-6.2, -5.6, 6.2, -5.6, 6, 0.5);
  s.lineTo(1.9, 0.9);
  s.lineTo(2.2, 4.6);
  s.quadTo(0, 5.6, -2.2, 4.6);
  s.lineTo(-1.9, 0.9);
  s.close();
  return s;
}

function moved(c: SkCanvas, p: SkPath, dx: number, dy: number, paint: SkPaint) {
  'worklet';
  c.save();
  c.translate(dx, dy);
  c.drawPath(p, paint);
  c.restore();
}

/** One piece of kind k at the origin, with the light along (lx, ly) and its shadow at (sx, sy). */
export function piece(c: SkCanvas, k: number, p: Piece, lx: number, ly: number, sx: number, sy: number, shadow: number, a: number) {
  'worklet';
  if (k === 0) {
    const cube = Skia.Path.Make();
    cube.addRRect(Skia.RRectXY(Skia.XYWHRect(-4.6, -4.1, 9.2, 8.2), 2.3, 2.3));
    moved(c, cube, sx, sy, solid(0x8a3a00, 0.3 * shadow, a));
    c.drawPath(cube, solid(0xa3141a, 1, a));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-4.1 + lx * 0.9, -3.6 + ly * 0.9, 8, 7), 2, 2), solid(0xe3321f, 1, a));
    blob(c, lx * 2.2, ly * 2.2, 2.2, 1.4, 0xffffff, 0.55, a);
  } else if (k === 1) {
    const blade = pieceLeaf();
    moved(c, blade, sx, sy, solid(0x6a5000, 0.26 * shadow, a));
    c.drawPath(blade, solid(0x2b732d, 1, a));
    c.save();
    c.clipPath(blade, ClipOp.Intersect, true);
    blob(c, lx * 1.6, ly * 1.6, 4.6, 2.2, 0x6db24e, 0.65, a);
    const vein = Skia.Path.Make();
    vein.moveTo(-6.2, 0);
    vein.quadTo(0, 0.8, 6.4, 0);
    c.drawPath(vein, stroke(solid(0x9ccb6e, 0.75, a), 0.7, false));
    c.restore();
    c.drawPath(blade, stroke(solid(0x174a1a, 0.45, a), 0.6, false));
  } else if (k === 2) {
    const meat = band(-11, 11, -3, 3, 1.1, 11, p.phase, 10);
    moved(c, meat, sx, sy, solid(0x6a3000, 0.28 * shadow, a));
    c.drawPath(meat, solid(0xa8372b, 1, a));
    c.save();
    c.clipPath(meat, ClipOp.Intersect, true);
    c.drawPath(band(-11, 11, -0.9, 0.7, 1.1, 11, p.phase, 10), solid(0xf3d3bc, 1, a));
    c.drawPath(band(-11, 11, 1.7, 2.2, 1.1, 11, p.phase, 10), solid(0xefc9ae, 0.8, a));
    blob(c, lx * 2, ly * 2, 6, 2, 0xffffff, 0.22, a);
    c.restore();
    c.drawPath(meat, stroke(solid(0x5a170f, 0.5, a), 0.7, false));
  } else if (k === 3) {
    c.drawCircle(sx, sy, 5.2, solid(0x8a3a00, 0.28 * shadow, a));
    c.drawCircle(0, 0, 5.2, solid(0xc4261c, 1, a));
    c.drawCircle(0, 0, 4.2, solid(0xe8452f, 1, a));
    for (let i = 0; i < 3; i++) {
      const ang = (i * 2 * Math.PI) / 3 + 0.4;
      blob(c, 2.3 * Math.cos(ang), 2.3 * Math.sin(ang), 1.5, 1.5, 0xf9c066, 0.95, a);
    }
    c.drawCircle(0, 0, 1.1, solid(0xf26a4a, 1, a));
    blob(c, lx * 2.6, ly * 2.6, 1.6, 1, 0xffffff, 0.6, a);
  } else {
    const s = mushroomSlice();
    moved(c, s, sx, sy, solid(0x6a4000, 0.26 * shadow, a));
    c.drawPath(s, solid(0xede3d4, 1, a));
    c.save();
    c.clipPath(s, ClipOp.Intersect, true);
    const gill = Skia.Path.Make();
    gill.moveTo(-5.2, 0.2);
    gill.quadTo(0, -2.4, 5.2, 0.2);
    c.drawPath(gill, stroke(solid(0xa58665, 0.6, a), 1.1, false));
    blob(c, lx * 2, ly * 2, 3, 1.6, 0xffffff, 0.6, a);
    c.restore();
    c.drawPath(s, stroke(solid(0x96765a, 0.85, a), 0.8, false));
  }
}

// Omelette (its own frame)

function outline(): number[] {
  const p: number[] = [];
  for (let i = 0; i < 9; i++) {
    const x = -R * 0.96 + (R * 1.92 * i) / 8;
    p.push(x, -3.5 * (1 - (x / R) * (x / R)));
  }
  for (let i = 0; i <= 20; i++) {
    const th = Math.PI * (0.025 + (0.95 * i) / 20);
    const r = R * (1 + 0.012 * Math.sin(3 * th + 0.5) + 0.008 * Math.sin(7 * th + 1.3));
    p.push(r * Math.cos(th), r * Math.sin(th));
  }
  return p;
}

const OUTLINE = outline();

/** Seeded speckles of the omelette's surface: [x, y, radius, kind (0 light, 1 golden, 2 brown)] flattened. */
function makeSpeckles(): number[] {
  const out: number[] = [];
  for (let i = 0; i < 72; i++) {
    const th = Math.PI * (0.03 + 0.94 * hash(70000 + i * 4));
    const rho = (R - 6) * Math.sqrt(hash(70001 + i * 4));
    const k = Math.floor(hash(70002 + i * 4) * 3);
    const r = k === 2 ? 1 + 0.8 * hash(70003 + i * 4) : 2.2 + 3.6 * hash(70003 + i * 4);
    out.push(Math.cos(th) * rho, Math.max(3, Math.sin(th) * rho), r, k);
  }
  return out;
}

const SPECKLES = makeSpeckles();

export function omelette(c: SkCanvas, shx: number, shy: number, ctx: number, cty: number) {
  'worklet';
  const path = smoothClosed(OUTLINE);
  blurred(c, path, shx, shy, 13, 0x4a3200, 0.2);
  blurred(c, path, ctx, cty, 3, 0x3a2600, 0.22);
  c.drawPath(path, radial([0xffdf5c, 1, 0, 0xf9ca36, 1, 0.5, 0xf0b328, 1, 0.82, 0xe09a1a, 1, 1], 0, R * 0.3, R * 1.05, 1));
  c.save();
  c.clipPath(path, ClipOp.Intersect, true);
  blob(c, -34, 44, 36, 20, 0xfff2b8, 0.42, 1);
  blob(c, 34, 66, 26, 14, 0xfff2b8, 0.36, 1);
  blob(c, -66, 22, 20, 11, 0xfff2b8, 0.32, 1);
  blob(c, 8, 92, 22, 9, 0xd8901c, 0.28, 1);
  for (let i = 0; i < SPECKLES.length; i += 4) {
    const x = SPECKLES[i];
    const y = SPECKLES[i + 1];
    const r = SPECKLES[i + 2];
    const k = SPECKLES[i + 3];
    if (k === 0) blob(c, x, y, r, r * 0.8, 0xfff4c2, 0.6, 1);
    else if (k === 1) blob(c, x, y, r, r * 0.75, 0xe0a038, 0.42, 1);
    else c.drawCircle(x, y, r, solid(0xb97a1c, 0.32, 1));
  }
  c.drawCircle(0, 0, R * 1.03, radial([0xc97e12, 0, 0, 0xc97e12, 0, 0.78, 0xb86e0e, 0.5, 1], 0, 0, R * 1.03, 1));
  c.drawRect(Skia.XYWHRect(-R, -8, 2 * R, 34), linear([0xfff3b0, 0.85, 0, 0xffe58a, 0, 1], 0, -4, 0, 26, 1));
  const fold = Skia.Path.Make();
  fold.moveTo(OUTLINE[0], OUTLINE[1]);
  for (let i = 1; i < 9; i++) fold.lineTo(OUTLINE[i * 2], OUTLINE[i * 2 + 1]);
  c.translate(0, 1.2);
  c.drawPath(fold, stroke(solid(0xffffff, 0.45, 1), 1.4, false));
  c.restore();
}

// Plate (points, centred at x, y)

export function plate(c: SkCanvas, x: number, y: number) {
  'worklet';
  ringShadow(c, x, y + 8, 160, 188, 0.07);
  c.drawCircle(x, y, 168, radial([0xf8f8f7, 1, 0, 0xefefed, 1, 1], x - 40, y - 50, 230, 1));
  c.drawCircle(x, y, 167.5, stroke(linear([0xffffff, 0.9, 0, 0xffffff, 0, 0.6], x - 120, y - 120, x + 60, y + 60, 1), 1, false));
  ringShadow(c, x, y + 12, 136, 178, 0.12);
  ringShadow(c, x, y + 2, 142, 150, 0.16);
  c.drawCircle(x, y, 144, linear([0xffffff, 1, 0, 0xf6f4f1, 1, 0.5, 0xe4e0da, 1, 1], x - 100, y - 100, x + 100, y + 100, 1));
  c.drawCircle(x, y, 143.3, stroke(linear([0xffffff, 1, 0, 0xffffff, 0, 0.55], x - 100, y - 100, x + 60, y + 60, 1), 1.2, false));
  c.drawCircle(x, y, 112, radial([0xfdfcfa, 1, 0, 0xf2efea, 1, 1], x - 20, y - 24, 130, 1));
  c.drawCircle(x, y, 112, radial([0, 0, 0, 0, 0, 0.9, 0x5a4a30, 0.08, 1], x, y, 112, 1));
  c.drawCircle(x, y, 112.6, stroke(linear([0xffffff, 0, 0.4, 0xffffff, 0.95, 1], x - 80, y - 80, x + 80, y + 80, 1), 1.2, false));
}
