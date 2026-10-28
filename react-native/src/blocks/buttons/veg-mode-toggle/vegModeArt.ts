import {
  BlurStyle,
  PaintStyle,
  Skia,
  StrokeCap,
  TileMode,
  type SkCanvas,
  type SkColor,
  type SkPaint,
  type SkPath,
} from '@shopify/react-native-skia';

/*
 Veg Mode Toggle — the two dishes, drawn in code (a port of VegModeToggleArt.swift, same shapes
 and colours). Worklets; helpers first.

 Units: the toggle's height H = 1, origin at the track's top-left; the track is 2.42 × 1. Each dish
 pops up about its pivot on the track's bottom line (y = 1), which also cuts it: the caller clips
 everything below y = 1.
 Broccoli: a cloud of seven lobes, a two-prong stem, a darker front floret and a garlic bulb.
 Drumstick: a red leg leaning right, a white bone on top, a halved boiled egg in front.
*/

export function hex(v: number, a = 1): SkColor {
  'worklet';
  return Float32Array.of(((v >> 16) & 0xff) / 255, ((v >> 8) & 0xff) / 255, (v & 0xff) / 255, a);
}

function circle(p: SkPath, x: number, y: number, r: number) {
  'worklet';
  p.addCircle(x, y, r);
  return p;
}

function linear(stops: [number, number][], x0: number, y0: number, x1: number, y1: number): SkPaint {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: x0, y: y0 },
      { x: x1, y: y1 },
      stops.map((s) => hex(s[0])),
      stops.map((s) => s[1]),
      TileMode.Clamp,
    ),
  );
  return paint;
}

function radial(v: number, alpha: number, x: number, y: number, r: number): SkPaint {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setShader(Skia.Shader.MakeRadialGradient({ x, y }, r, [hex(v, alpha), hex(v, 0)], null, TileMode.Clamp));
  return paint;
}

function solid(v: number, alpha = 1): SkPaint {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(hex(v, alpha));
  return paint;
}

function asStroke(paint: SkPaint, width: number): SkPaint {
  'worklet';
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(width);
  paint.setStrokeCap(StrokeCap.Round);
  return paint;
}

/** Fills or strokes in colour, or for the shadow layer as a blurred black silhouette. */
type Pen = { c: SkCanvas; ink: SkPaint | null };

export function makePen(c: SkCanvas, blur: number): Pen {
  'worklet';
  if (blur <= 0) return { c, ink: null };
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(hex(0x000000));
  // The same blur as Android's BlurMaskFilter radius (sigma = 0.57735 · r + 0.5), in the canvas' units.
  ink.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur * 0.57735 + 0.5 / 104, true));
  return { c, ink };
}

function penFill(pen: Pen, path: SkPath, paint: () => SkPaint) {
  'worklet';
  if (pen.ink) {
    pen.ink.setStyle(PaintStyle.Fill);
    pen.c.drawPath(path, pen.ink);
  } else {
    pen.c.drawPath(path, paint());
  }
}

function penStroke(pen: Pen, path: SkPath, width: number, paint: () => SkPaint) {
  'worklet';
  if (pen.ink) {
    asStroke(pen.ink, width);
    pen.c.drawPath(path, pen.ink);
  } else {
    pen.c.drawPath(path, asStroke(paint(), width));
  }
}

// Geometry (shared by the art and its shadow).

const LOBES: [number, number, number][] = [
  [0.27, -0.13, 0.24], [-0.16, 0.25, 0.3], [0.54, 0.06, 0.22], [0.19, 0.4, 0.4],
  [0.22, 0.74, 0.28], [0.58, 0.58, 0.2], [-0.06, 0.58, 0.26],
];
const FLORET_LOBES: [number, number, number][] = [
  [0.64, 0.28, 0.13], [0.8, 0.2, 0.14], [0.97, 0.29, 0.12],
  [0.7, 0.42, 0.11], [0.82, 0.4, 0.15], [0.94, 0.42, 0.1],
];

function union(list: [number, number, number][]) {
  'worklet';
  const p = Skia.Path.Make();
  for (let i = 0; i < list.length; i++) circle(p, list[i][0], list[i][1], list[i][2]);
  return p;
}

function poly(points: number[]) {
  'worklet';
  const p = Skia.Path.Make();
  p.moveTo(points[0], points[1]);
  for (let i = 2; i < points.length; i += 2) p.lineTo(points[i], points[i + 1]);
  p.close();
  return p;
}

// Pop.

/**
 * Places a dish for its amount a (0 hidden, 1 at rest) and velocity v: it rises from below the
 * bottom line, grows, leans in and squashes with its speed. Reduced motion only fades it.
 */
function pop(c: SkCanvas, a: number, v: number, pivot: number, lean: number, reduced: boolean) {
  'worklet';
  if (reduced) return;
  const k = Math.max(-1, Math.min(1, v / 8));
  const s = Math.max(0, 0.6 + 0.4 * a);
  c.translate(pivot, 1 + (1 - a) * 1.05);
  c.rotate((1 - a) * lean, 0, 0);
  c.scale(s * (1 - 0.05 * k), s * (1 + 0.07 * k));
  c.translate(-pivot, -1);
}

/** A part that lags its dish: scaled about its own pivot by how far behind it is. */
function part(c: SkCanvas, a: number, a2: number, x: number, y: number, reduced: boolean) {
  'worklet';
  if (reduced) return;
  const s = Math.max(0, 1 + 0.6 * (a2 - a));
  c.translate(x, y);
  c.scale(s, s);
  c.translate(-x, -y);
}

// Drawing.

export function drawBroccoli(pen: Pen, a: number, v: number, a2: number, reduced: boolean) {
  'worklet';
  const c = pen.c;
  const colour = pen.ink === null;
  c.save();
  pop(c, a, v, 0.3, -14, reduced);

  const stem = Skia.Path.Make();
  stem.moveTo(0.56, 1.05);
  stem.lineTo(0.47, 0.78);
  stem.moveTo(0.58, 1.05);
  stem.lineTo(0.66, 0.8);
  penStroke(pen, stem, 0.09, () => solid(0x3bb455));

  const cloud = union(LOBES);
  penFill(pen, cloud, () => linear([[0x1fa244, 0], [0x4cc262, 0.5], [0xaee483, 1]], -0.3, -0.3, 0.35, 1.0));
  // One broad gloss on the crown.
  if (colour) c.drawPath(cloud, radial(0xffffff, 0.38, 0.05, 0.02, 0.34));

  c.save();
  part(c, a, a2, 0.82, 0.74, reduced);
  penFill(pen, poly([0.74, 0.46, 0.9, 0.46, 0.86, 0.74, 0.78, 0.74]), () =>
    linear([[0x5cc266, 0], [0x9edc86, 1]], 0.82, 0.46, 0.82, 0.74),
  );
  penFill(pen, union(FLORET_LOBES), () => linear([[0x1c9a40, 0], [0x5dcb6c, 1]], 0.6, 0.1, 0.95, 0.55));
  if (colour) {
    c.drawPath(circle(Skia.Path.Make(), 0.8, 0.2, 0.14), radial(0xffffff, 0.24, 0.76, 0.15, 0.12));
    c.drawPath(circle(Skia.Path.Make(), 0.64, 0.28, 0.13), radial(0xffffff, 0.18, 0.6, 0.23, 0.11));
  }
  c.restore();

  c.save();
  part(c, a, a2, 0.93, 1.0, reduced);
  const tip = Skia.Path.Make();
  tip.moveTo(0.88, 0.76);
  tip.quadTo(0.92, 0.72, 0.94, 0.63);
  tip.quadTo(0.95, 0.72, 0.98, 0.76);
  tip.close();
  penFill(pen, tip, () => linear([[0xf4f1ea, 0], [0xdcd9d2, 1]], 0.93, 0.63, 0.93, 0.76));
  penFill(pen, circle(Skia.Path.Make(), 0.93, 0.97, 0.25), () =>
    linear([[0xffffff, 0], [0xdcd9d2, 1]], 0.93, 0.7, 0.93, 1.05),
  );
  if (colour) {
    const cloves = Skia.Path.Make();
    cloves.moveTo(0.86, 0.8);
    cloves.quadTo(0.81, 0.9, 0.84, 1.02);
    cloves.moveTo(1.0, 0.8);
    cloves.quadTo(1.05, 0.9, 1.02, 1.02);
    const line = solid(0xcfcbc2);
    line.setStyle(PaintStyle.Stroke);
    line.setStrokeWidth(0.012);
    c.drawPath(cloves, line);
    c.drawPath(circle(Skia.Path.Make(), 0.87, 0.84, 0.07), radial(0xffffff, 0.9, 0.87, 0.84, 0.07));
  }
  c.restore();
  c.restore();
}

export function drawDrumstick(pen: Pen, a: number, v: number, a2: number, reduced: boolean) {
  'worklet';
  const c = pen.c;
  const colour = pen.ink === null;
  c.save();
  pop(c, a, v, 1.76, 14, reduced);
  const boneShade = () => linear([[0xffffff, 0], [0xdad5cb, 1]], 2.05, -0.28, 2.18, 0.08);

  const bone = Skia.Path.Make();
  bone.moveTo(2.03, 0.1);
  bone.lineTo(2.13, -0.12);
  penStroke(pen, bone, 0.1, boneShade);
  const ends = Skia.Path.Make();
  circle(ends, 2.08, -0.19, 0.075);
  circle(ends, 2.19, -0.14, 0.075);
  penFill(pen, ends, boneShade);

  const meat = Skia.Path.Make();
  meat.moveTo(1.33, 1.0);
  meat.cubicTo(1.3, 0.62, 1.64, 0.26, 2.03, 0.05);
  meat.cubicTo(2.12, 0.14, 2.23, 0.42, 2.22, 0.66);
  meat.lineTo(2.1, 1.0);
  meat.close();
  penFill(pen, meat, () => linear([[0xf0603a, 0], [0xe2412f, 0.45], [0xd2282b, 1]], 1.95, 0.1, 1.45, 0.95));
  if (colour) {
    const gloss = Skia.Path.Make();
    gloss.moveTo(1.45, 0.8);
    gloss.cubicTo(1.47, 0.55, 1.68, 0.32, 1.93, 0.17);
    c.drawPath(gloss, asStroke(solid(0xffffff, 0.28), 0.05));
    c.drawPath(circle(Skia.Path.Make(), 1.98, 0.16, 0.1), radial(0xffffff, 0.3, 1.98, 0.16, 0.1));
  }

  c.save();
  part(c, a, a2, 2.0, 1.0, reduced);
  c.save();
  c.translate(2.02, 0.86);
  c.rotate(-30, 0, 0);
  const white = Skia.Path.Make();
  white.addOval(Skia.XYWHRect(-0.3, -0.24, 0.6, 0.48));
  // The egg's gradient in its own turned frame (the same line as the Swift one in track units).
  penFill(pen, white, () => linear([[0xffffff, 0], [0xe3dfd6, 1]], 0.207, -0.158, -0.197, 0.14));
  c.restore();
  if (colour) {
    c.drawPath(
      circle(Skia.Path.Make(), 1.96, 0.92, 0.17),
      linear([[0xffd64f, 0], [0xf0961e, 1]], 1.9, 0.76, 2.04, 1.08),
    );
    c.drawPath(circle(Skia.Path.Make(), 1.91, 0.86, 0.04), solid(0xffffff, 0.6));
  }
  c.restore();
  c.restore();
}
