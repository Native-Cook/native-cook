import { ClipOp, FilterMode, MipmapMode, PaintStyle, Skia, StrokeCap, StrokeJoin, TileMode, BlendMode, BlurStyle, type SkCanvas, type SkFont, type SkImage, type SkPath, type SkPoint } from '@shopify/react-native-skia';

import { CAR_WIDTH, CENTRE_X, HEADLINE_PITCH, HEART_X, HEART_Y, JAR_HEIGHT, JAR_RADIUS, JAR_WIDTH, heartScale, type Jar } from './glassDomeMotion';

/** One car of the invented Nuvra line-up, with the colour wash it brings to the screen. */
export type Car = { model: string; trim: string; info: string; top: number; mid: number; bottom: number };

export const CARS: Car[] = [
  { model: 'Kite 3', trim: 'Spark', info: '268 mi range · from $219/mo', top: 0x5a93da, mid: 0x2b5faa, bottom: 0x0d2246 },
  { model: 'Torva', trim: 'Line', info: '301 mi range · from $259/mo', top: 0xcf4a57, mid: 0x9c1d2c, bottom: 0x36060d },
  { model: 'Orla 4', trim: 'Pro', info: '322 mi range · from $289/mo', top: 0x3db8cf, mid: 0x14879e, bottom: 0x063a47 },
  { model: 'Nimbus', trim: 'Lux Pro', info: '347 mi range · from $339/mo', top: 0xa8b3be, mid: 0x75818e, bottom: 0x252c34 },
  { model: 'Zest', trim: 'Sport', info: '244 mi range · from $199/mo', top: 0xadbd3a, mid: 0x78881d, bottom: 0x262e08 },
];
export const CAR_IMAGES = [
  require('./assets/car-cobalt.webp'),
  require('./assets/car-red.webp'),
  require('./assets/car-aqua.webp'),
  require('./assets/car-pearl.webp'),
  require('./assets/car-lime.webp'),
];

const HEART_RED = 0xff3b4e;
const HEART_PINK = 0xff8a9a;

export function rgba(hex: number, a: number) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255, Math.min(1, Math.max(0, a)));
}

export function white(a: number) {
  'worklet';
  return Float32Array.of(1, 1, 1, Math.min(1, Math.max(0, a)));
}

export function hexColor(hex: number) {
  'worklet';
  return `#${hex.toString(16).padStart(6, '0')}`;
}

export function mixHex(a: number, b: number, f: number) {
  'worklet';
  const r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * f);
  const g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * f);
  const bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * f);
  return (r << 16) | (g << 8) | bl;
}

/** One of the wash's three colours (0 top, 1 middle, 2 bottom) for carousel position φ. */
export function washAt(phi: number, which: number) {
  'worklet';
  const n = CARS.length;
  const base = Math.floor(phi);
  const f = phi - base;
  const i = ((base % n) + n) % n;
  const a = CARS[i];
  const b = CARS[(i + 1) % n];
  if (which === 0) return mixHex(a.top, b.top, f);
  if (which === 1) return mixHex(a.mid, b.mid, f);
  return mixHex(a.bottom, b.bottom, f);
}

export function hash(i: number) {
  'worklet';
  const v = Math.sin(i * 12.9898) * 43758.5453;
  return v - Math.floor(v);
}

/** A heart `size` pt wide centred on the origin (the 24-pt "favourite" glyph, redrawn). */
export function heartPath(size: number): SkPath {
  'worklet';
  const s = size / 20;
  const ox = 12;
  const oy = 12.18;
  const p = Skia.PathBuilder.Make();
  p.moveTo((12 - ox) * s, (21.35 - oy) * s);
  p.lineTo((10.55 - ox) * s, (20.03 - oy) * s);
  p.cubicTo((5.4 - ox) * s, (15.36 - oy) * s, (2 - ox) * s, (12.28 - oy) * s, (2 - ox) * s, (8.5 - oy) * s);
  p.cubicTo((2 - ox) * s, (5.42 - oy) * s, (4.42 - ox) * s, (3 - oy) * s, (7.5 - ox) * s, (3 - oy) * s);
  p.cubicTo((9.24 - ox) * s, (3 - oy) * s, (10.91 - ox) * s, (3.81 - oy) * s, (12 - ox) * s, (5.09 - oy) * s);
  p.cubicTo((13.09 - ox) * s, (3.81 - oy) * s, (14.76 - ox) * s, (3 - oy) * s, (16.5 - ox) * s, (3 - oy) * s);
  p.cubicTo((19.58 - ox) * s, (3 - oy) * s, (22 - ox) * s, (5.42 - oy) * s, (22 - ox) * s, (8.5 - oy) * s);
  p.cubicTo((22 - ox) * s, (12.28 - oy) * s, (18.6 - ox) * s, (15.36 - oy) * s, (13.45 - ox) * s, (20.04 - oy) * s);
  p.close();
  return p.detach();
}

/** The jar outline (top corners rounded, bottom open edge at y = 4), inset by `inset`. */
export function jarOutline(inset: number): SkPath {
  'worklet';
  const w = JAR_WIDTH - 2 * inset;
  const r = JAR_RADIUS - inset;
  const top = 4 - JAR_HEIGHT + inset;
  const bottom = inset > 0 ? 8 : 4;
  const p = Skia.PathBuilder.Make();
  p.moveTo(-w / 2, bottom);
  p.lineTo(-w / 2, top + r);
  p.arcToOval(Skia.XYWHRect(-w / 2, top, 2 * r, 2 * r), 180, 90, false);
  p.lineTo(w / 2 - r, top);
  p.arcToOval(Skia.XYWHRect(w / 2 - 2 * r, top, 2 * r, 2 * r), 270, 90, false);
  p.lineTo(w / 2, bottom);
  p.close();
  return p.detach();
}

/** Half of the base ellipse: the far half when `far`, else the near one. */
function baseArc(far: boolean): SkPath {
  'worklet';
  const p = Skia.PathBuilder.Make();
  const rx = JAR_WIDTH / 2;
  for (let s = 0; s <= 40; s++) {
    const a = (Math.PI * s) / 40 + (far ? Math.PI : 0);
    const x = rx * Math.cos(a);
    const y = 4 + 12 * Math.sin(a);
    if (s === 0) p.moveTo(x, y);
    else p.lineTo(x, y);
  }
  return p.detach();
}

function domeArc(): SkPath {
  'worklet';
  const p = Skia.PathBuilder.Make();
  const w = JAR_WIDTH;
  const r = JAR_RADIUS;
  const top = 4 - JAR_HEIGHT;
  for (let s = 0; s <= 24; s++) {
    const a = ((188 + (72 * s) / 24) * Math.PI) / 180;
    const x = -w / 2 + r + (r - 6) * Math.cos(a);
    const y = top + r + (r - 6) * Math.sin(a);
    if (s === 0) p.moveTo(x, y);
    else p.lineTo(x, y);
  }
  return p.detach();
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: Float32Array[], stops: number[] | null) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors, stops, TileMode.Clamp));
  return paint;
}

function solid(color: Float32Array) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(color);
  return paint;
}

function strokePaint(color: Float32Array, width: number) {
  'worklet';
  const paint = solid(color);
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(width);
  return paint;
}

function softEllipse(c: SkCanvas, x: number, y: number, rx: number, ry: number, alpha: number) {
  'worklet';
  c.save();
  c.translate(x, y);
  c.scale(1, ry / rx);
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setShader(
    Skia.Shader.MakeRadialGradient({ x: 0, y: 0 }, rx, [rgba(0, alpha), rgba(0, alpha * 0.55), rgba(0, 0)], [0, 0.5, 1], TileMode.Clamp),
  );
  c.drawCircle(0, 0, rx, paint);
  c.restore();
}

/** The wash and the floor's spotlight, in the design space. */
export function drawBackground(c: SkCanvas, widthPt: number, heightPt: number, phi: number, floorY: number) {
  'worklet';
  c.drawRect(
    Skia.XYWHRect(0, 0, widthPt, heightPt),
    linear(0, 0, 0, heightPt, [rgba(washAt(phi, 0), 1), rgba(washAt(phi, 1), 1), rgba(washAt(phi, 2), 1)], [0, 0.55, 1]),
  );
  c.save();
  c.translate(CENTRE_X, floorY + 6);
  c.scale(1, 0.22);
  const spot = Skia.Paint();
  spot.setAntiAlias(true);
  spot.setShader(Skia.Shader.MakeRadialGradient({ x: 0, y: 0 }, 210, [white(0.16), white(0)], null, TileMode.Clamp));
  c.drawCircle(0, 0, 210, spot);
  c.restore();
}

/** Glyphs of "MORE" with the headline's -1 pt tracking, centred on x = 0. */
export type Headline = { glyphs: number[]; xs: number[]; centre: number };

export function makeHeadline(font: SkFont): Headline {
  const glyphs = font.getGlyphIDs('MORE');
  const widths = font.getGlyphWidths(glyphs);
  const xs: number[] = [];
  let x = 0;
  for (let i = 0; i < glyphs.length; i++) {
    xs.push(x);
    x += widths[i] - 1;
  }
  const total = x + 1;
  for (let i = 0; i < xs.length; i++) xs[i] -= total / 2;
  const metrics = font.getMetrics();
  return { glyphs: Array.from(glyphs), xs, centre: -(metrics.ascent + metrics.descent) / 2 };
}

/** The stacked MORE headline in its 393 × 236 window (top at y = 0), rows shifted up by `shift`, edges faded, smeared vertically by `sigma`. */
export function drawHeadline(c: SkCanvas, font: SkFont, head: Headline, shift: number, sigma: number) {
  'worklet';
  const layer = Skia.Paint();
  if (sigma > 0.2) layer.setImageFilter(Skia.ImageFilter.MakeBlur(0.01, sigma, TileMode.Decal, null));
  c.saveLayer(layer, Skia.XYWHRect(0, 0, 393, 236));
  c.saveLayer(Skia.Paint(), Skia.XYWHRect(0, 0, 393, 236));
  c.clipRect(Skia.XYWHRect(0, 0, 393, 236), ClipOp.Intersect, true);
  const ink = solid(white(1));
  const positions: SkPoint[] = [];
  for (let n = -1; n <= 4; n++) {
    const y = 40 + n * HEADLINE_PITCH - shift + head.centre;
    for (let i = 0; i < head.xs.length; i++) positions.push(Skia.Point(CENTRE_X + head.xs[i], y));
  }
  const glyphs: number[] = [];
  for (let n = 0; n < 6; n++) for (let i = 0; i < head.glyphs.length; i++) glyphs.push(head.glyphs[i]);
  c.drawGlyphs(glyphs, positions, 0, 0, font, ink);
  const mask = linear(0, 0, 0, 236, [white(0), white(1), white(1), white(0.25), white(0)], [0, 14 / 236, 182 / 236, 212 / 236, 1]);
  mask.setBlendMode(BlendMode.DstIn);
  c.drawRect(Skia.XYWHRect(0, 0, 393, 236), mask);
  c.restore();
  c.restore();
}

function drawHeart(c: SkCanvas, liked: boolean, age: number) {
  'worklet';
  c.save();
  c.translate(HEART_X, HEART_Y);
  c.drawCircle(0, 0, 19, strokePaint(white(0.42), 1.5));
  if (age >= 0 && age < 0.8) {
    // Ring and particles of the pop.
    const p = age / 0.8;
    const e = 1 - Math.pow(1 - Math.min(1, age / 0.45), 3);
    if (age < 0.45) {
      c.drawCircle(0, 0, 19 + 18 * e, strokePaint(rgba(HEART_RED, 0.7 * (1 - e)), 2.2 * (1 - e) + 0.4));
    }
    const ease = 1 - Math.pow(1 - p, 3);
    for (let i = 0; i < 10; i++) {
      const angle = -Math.PI / 2 + ((i - 4.5) / 4.5) * 1.25 + (hash(i + 1) - 0.5) * 0.3;
      const dist = 18 + (34 + 16 * hash(i + 7)) * ease;
      const size = (7 + 5 * hash(i + 3)) * (1 - 0.45 * p);
      c.save();
      c.translate(Math.cos(angle) * dist, Math.sin(angle) * dist - 10 * p);
      c.drawPath(heartPath(size), solid(rgba(i % 2 === 0 ? HEART_RED : HEART_PINK, 1 - p)));
      c.restore();
    }
  }
  const scale = heartScale(age);
  c.scale(scale, scale);
  const heart = heartPath(18);
  if (liked) {
    c.drawPath(heart, solid(rgba(HEART_RED, 1)));
  } else {
    const line = strokePaint(white(0.9), 1.6);
    line.setStrokeJoin(StrokeJoin.Round);
    c.drawPath(heart, line);
  }
  c.restore();
}

function drawLabel(c: SkCanvas, text: string, x: number, top: number, font: SkFont, alpha: number) {
  'worklet';
  const baseline = top - font.getMetrics().ascent;
  const shadow = solid(rgba(0, 0.22));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.5, true));
  c.drawText(text, x, baseline + 1, shadow, font);
  c.drawText(text, x, baseline, solid(white(alpha)), font);
}

export type JarFonts = { bold: SkFont; regular: SkFont };

/** One jar with its car, in the design space; the caller has set the floor line at `floorY`. */
export function drawJar(c: SkCanvas, jar: Jar, floorY: number, washTop: number, liked: boolean, age: number, image: SkImage | null, fonts: JarFonts) {
  'worklet';
  const car = CARS[jar.index];
  const aspect = image && image.height() > 0 ? image.width() / image.height() : 2.55;
  const carW = CAR_WIDTH;
  const carH = carW / aspect;
  const w = JAR_WIDTH;
  const h = JAR_HEIGHT;
  const top = 4 - h;
  const shift = -22 * jar.d;
  const path = jarOutline(0);
  const imagePaint = Skia.Paint();
  imagePaint.setAntiAlias(true);

  c.save();
  c.translate(jar.x, floorY);
  c.scale(jar.scale, jar.scale);

  // Mirror image on the glossy floor.
  if (image) {
    c.saveLayer(Skia.Paint(), Skia.XYWHRect(-carW / 2 - 4, -2, carW + 8, carH + 4));
    c.save();
    c.scale(1, -1);
    c.drawImageRectOptions(image, Skia.XYWHRect(0, 0, image.width(), image.height()), Skia.XYWHRect(-carW / 2, -carH + 1, carW, carH), FilterMode.Linear, MipmapMode.Linear, imagePaint);
    c.restore();
    const fade = linear(0, 0, 0, 48, [rgba(0, 0.3), rgba(0, 0)], null);
    fade.setBlendMode(BlendMode.DstIn);
    c.drawRect(Skia.XYWHRect(-carW / 2 - 4, -2, carW + 8, carH + 4), fade);
    c.restore();
  }

  // Back of the glass: the wash refracted lighter, frosted, brighter at the edges (Fresnel).
  c.save();
  c.clipPath(path, ClipOp.Intersect, true);
  c.drawPath(path, linear(0, top, 0, 4, [rgba(washTop, 0.45), rgba(washTop, 0.1)], null));
  c.drawPath(path, linear(0, top, 0, 4, [white(0.13), white(0.05), white(0.09)], [0, 0.55, 1]));
  c.drawPath(path, linear(-w / 2, 0, w / 2, 0, [white(0.22), white(0.05), white(0), white(0), white(0.05), white(0.2)], [0, 0.09, 0.24, 0.78, 0.91, 1]));
  c.drawOval(Skia.XYWHRect(-w / 2, 4 - 12, w, 24), solid(white(0.06)));
  c.restore();
  c.drawPath(baseArc(true), strokePaint(white(0.22), 1));

  // The label behind the glass.
  const tx = -w / 2 + 24;
  const ty = top + 30;
  drawLabel(c, 'The new', tx, ty, fonts.bold, 1);
  drawLabel(c, car.model, tx, ty + 24, fonts.bold, 1);
  drawLabel(c, car.trim, tx, ty + 48, fonts.regular, 0.92);
  drawHeart(c, liked, age);

  // The car on its contact shadow.
  softEllipse(c, 0, 0, 130, 15, 0.32);
  softEllipse(c, 0, -1, 104, 5, 0.6);
  if (image) {
    c.drawImageRectOptions(image, Skia.XYWHRect(0, 0, image.width(), image.height()), Skia.XYWHRect(-carW / 2, -carH + 1, carW, carH), FilterMode.Linear, MipmapMode.Linear, imagePaint);
  }

  // Front of the glass: specular streaks that slide as the jar moves, the dome's highlight.
  c.save();
  c.clipPath(path, ClipOp.Intersect, true);
  c.drawPath(path, linear(-w / 2, top, w / 2, 4, [white(0.07), white(0), white(0)], [0, 0.6, 1]));
  const broadX = -w / 2 + 30 + shift;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(broadX, top + 34, 34, h - 70), 17, 17), linear(broadX, 0, broadX + 34, 0, [white(0), white(0.2), white(0)], [0, 0.45, 1]));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(broadX + 42, top + 52, 3.5, h - 120), 1.75, 1.75), linear(0, top + 52, 0, top + h - 68, [white(0.42), white(0.06)], null));
  const rightX = w / 2 - 26 + 0.6 * shift;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(rightX, top + 46, 10, h - 96), 5, 5), linear(rightX, 0, rightX + 10, 0, [white(0), white(0.16), white(0)], [0, 0.5, 1]));
  const dome = domeArc();
  const glow = strokePaint(white(0.14), 10);
  glow.setStrokeCap(StrokeCap.Round);
  c.drawPath(dome, glow);
  const shine = strokePaint(white(0.6), 2.4);
  shine.setStrokeCap(StrokeCap.Round);
  c.drawPath(dome, shine);
  c.restore();

  // The rim: a faint chromatic fringe, the bright edge, and the glass's inner edge.
  c.save();
  c.clipRect(Skia.XYWHRect(-w / 2 - 6, top - 6, w + 12, h + 3), ClipOp.Intersect, true);
  c.save();
  c.translate(-0.9, 0);
  c.drawPath(path, strokePaint(Float32Array.of(0.55, 0.95, 1, 0.28), 1));
  c.restore();
  c.save();
  c.translate(0.9, 0);
  c.drawPath(path, strokePaint(Float32Array.of(1, 0.6, 0.75, 0.22), 1));
  c.restore();
  const rim = linear(0, top, 0, 4, [white(0.9), white(0.45), white(0.3)], [0, 0.5, 1]);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(1.6);
  c.drawPath(path, rim);
  c.drawPath(jarOutline(4), strokePaint(white(0.14), 1));
  c.restore();
  // The near rim of the base, in front of the car's wheels.
  c.drawPath(baseArc(false), strokePaint(white(0.42), 1.4));

  c.restore();
}
