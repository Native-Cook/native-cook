import {
  AlphaType,
  BlendMode,
  ClipOp,
  ColorType,
  FilterMode,
  MipmapMode,
  PaintStyle,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPath,
  type SkPicture,
} from '@shopify/react-native-skia';

import { DROP_COUNT, GLOB_COUNT, LOOP, clamp, dropAt, easeOutCubic, hash, type Frame } from './liftMotion';

/* Layout, artwork and drawing of Pineapple Lift Product, ported from PineappleLiftProductArt.swift. */

export const TOP_IMAGE = require('./assets/pineapple-top.webp');
export const BOTTOM_IMAGE = require('./assets/pineapple-bottom.webp');
export const BOTTLE_IMAGE = require('./assets/bottle.webp');
export const FLOWER_IMAGE = require('./assets/hibiscus.webp');

// Palette
export const INK = 0x2a1803;
const INK2 = 0x3a2304;
const CREAM = 0xfff4d6;
const SKY_TOP = 0xffcb2e;
export const SKY_MID = 0xffb506;
const SKY_LOW = 0xf7a100;
const HAZE = 0xffe27a;
const GLOW = 0xfff1b8;
const JUICE = 0xffe58a;
const JUICE_RIM = 0xb86a00;
const SHADE = 0x7a4100;
const PETAL = 0xc8361e;

// Artwork (bundled px)
const PINE = 0.345; // pt per px of the pineapple halves
const TOP_W = 632;
const TOP_H = 837;
const TOP_PX = 229.6; // centre of its cut face
const TOP_PY = 748;
const TOP_TILT = 16.7; // degrees, the cut face's tilt in the image
const BOTTOM_W = 488;
const BOTTOM_H = 366;
const BOTTOM_FX = 242.4;
const BOTTOM_FY = 66;
const FACE_RX = 218;
const FACE_RY = 53;
const BOTTLE_W = 276;
const BOTTLE_PX_H = 716;
export const BOTTLE_H = 200;
export const BOTTLE_DRAW_W = (BOTTLE_W * BOTTLE_H) / BOTTLE_PX_H;
const FLOWER_W = 400;
const FLOWER_H = 392;

export function hexColor(hex: number, alpha?: number) {
  'worklet';
  const a = alpha === undefined ? 1 : Math.min(Math.max(alpha, 0), 1);
  const r = (hex >> 16) & 0xff;
  const g = (hex >> 8) & 0xff;
  const b = hex & 0xff;
  return `rgba(${r}, ${g}, ${b}, ${a})`;
}

function skColor(hex: number, alpha: number) {
  'worklet';
  return Skia.Color(hexColor(hex, alpha));
}

function freshPaint() {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  return p;
}

// Layout

export type Layout = {
  height: number;
  top: number;
  ctaTop: number;
  heroBase: number;
  barY: number;
  captionY: number;
  titleY: number;
  descY: number;
  faceX: number; // centre of the bottom half's cut face
  faceY: number;
  rx: number;
  ry: number;
};

export function makeLayout(height: number, top: number, bottom: number): Layout {
  const ctaTop = height - bottom - 12 - 56;
  const heroBase = ctaTop - 132;
  return {
    height,
    top,
    ctaTop,
    heroBase,
    barY: top + 26,
    captionY: ctaTop - 106,
    titleY: ctaTop - 76,
    descY: ctaTop - 38,
    faceX: 196.5,
    faceY: heroBase - BOTTOM_H * PINE + BOTTOM_FY * PINE,
    rx: FACE_RX * PINE,
    ry: FACE_RY * PINE,
  };
}

type Flower = { x: number; y: number; fromTop: boolean; size: number; rot: number; dx: number; dy: number; drot: number; phase: number };

const FLOWERS: Flower[] = [
  { x: 64, y: 150, fromTop: true, size: 128, rot: -18, dx: -16, dy: -26, drot: 14, phase: 0 },
  { x: 8, y: -190, fromTop: false, size: 104, rot: 28, dx: -18, dy: -14, drot: -12, phase: 2.1 },
  { x: 388, y: -150, fromTop: false, size: 112, rot: -36, dx: 20, dy: -24, drot: 16, phase: 4.2 },
];

/** Everything above the bottom half's front rim: what comes out of the fruit is drawn there. */
export function rimClip(l: Layout, sink: number): SkPath {
  const p = Skia.PathBuilder.Make();
  p.moveTo(-60, -2000);
  p.lineTo(453, -2000);
  p.lineTo(453, l.faceY);
  const n = 36;
  for (let i = 0; i <= n; i++) {
    const a = (i / n) * Math.PI;
    p.lineTo(l.faceX + Math.cos(a) * l.rx * 0.98, l.faceY + Math.sin(a) * (l.ry + sink));
  }
  p.lineTo(-60, l.faceY);
  p.close();
  return p.detach();
}

export function makeGrain(): SkImage | null {
  const n = 128;
  const bytes = new Uint8Array(n * n * 4);
  for (let i = 0; i < n * n; i++) {
    const v = Math.floor(hash(i * 0.731 + 0.5) * 255);
    bytes[i * 4] = v;
    bytes[i * 4 + 1] = v;
    bytes[i * 4 + 2] = v;
    bytes[i * 4 + 3] = 255;
  }
  return Skia.Image.MakeImage(
    { width: n, height: n, alphaType: AlphaType.Opaque, colorType: ColorType.RGBA_8888 },
    Skia.Data.fromBytes(bytes),
    n * 4,
  );
}

// Text (measured per glyph, so tracking and centring match SwiftUI's Text)

export type Fonts = {
  sans13: SkFont;
  semi11: SkFont;
  serif34: SkFont;
  sans14: SkFont;
  bold26: SkFont;
  medium12: SkFont;
  semi16: SkFont;
  bold10: SkFont;
  labelSemi: SkFont;
  labelSerifBold: SkFont;
  labelSerifItalic: SkFont;
  labelSmall: SkFont;
};

/** Draws `s` with its line box centred on `cy`; `align` 0 = left at x, 0.5 = centred on x. */
function drawText(c: SkCanvas, font: SkFont, s: string, x: number, cy: number, hex: number, alpha: number, tracking: number, align: number) {
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(skColor(hex, alpha));
  const ids = font.getGlyphIDs(s);
  const widths = font.getGlyphWidths(ids);
  let total = 0;
  for (let i = 0; i < widths.length; i++) total += widths[i] + (i < widths.length - 1 ? tracking : 0);
  const m = font.getMetrics();
  const baseline = cy - (m.ascent + m.descent) / 2;
  let pen = x - total * align;
  const positions = [];
  for (let i = 0; i < ids.length; i++) {
    positions.push(Skia.Point(pen, 0));
    pen += widths[i] + tracking;
  }
  c.drawGlyphs(ids, positions, 0, baseline, font, paint);
}

/** Backdrop: gradient, warm haze behind the hero and a fine grain. */
export function makeBackground(l: Layout, grain: SkImage | null): SkPicture {
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, l.height));
  const full = Skia.XYWHRect(0, 0, 393, l.height);
  let p = freshPaint();
  p.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, 0),
      Skia.Point(0, l.height),
      [skColor(SKY_TOP, 1), skColor(SKY_MID, 1), skColor(SKY_LOW, 1)],
      [0, 0.6, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(full, p);
  p = freshPaint();
  p.setShader(
    Skia.Shader.MakeRadialGradient(Skia.Point(l.faceX, l.faceY - 70), 280, [skColor(HAZE, 0.55), skColor(HAZE, 0)], [0, 1], TileMode.Clamp),
  );
  c.drawRect(full, p);
  if (grain) {
    p = freshPaint();
    const m = Skia.Matrix();
    m.scale(0.5, 0.5);
    p.setShader(grain.makeShaderOptions(TileMode.Repeat, TileMode.Repeat, FilterMode.Nearest, MipmapMode.None, m));
    p.setAlphaf(0.045);
    p.setBlendMode(BlendMode.Overlay);
    c.drawRect(full, p);
  }
  return recorder.finishRecordingAsPicture();
}

/** The bottle with its label printed once, so a frame draws one picture. */
export function makeBottle(bottle: SkImage | null, fonts: Fonts): SkPicture {
  const w = BOTTLE_DRAW_W;
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(0, 0, w, BOTTLE_H));
  if (bottle) {
    c.drawImageRectOptions(
      bottle,
      Skia.XYWHRect(0, 0, bottle.width(), bottle.height()),
      Skia.XYWHRect(0, 0, w, BOTTLE_H),
      FilterMode.Linear,
      MipmapMode.Linear,
      freshPaint(),
    );
  }
  const cx = w / 2;
  drawText(c, fonts.labelSemi, 'ISLA BLOOM', cx, 88, INK2, 0.9, 1.3, 0.5);
  const line = freshPaint();
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeWidth(0.5);
  line.setColor(skColor(INK2, 0.5));
  c.drawLine(cx - 12, 94.5, cx + 12, 94.5, line);
  drawText(c, fonts.labelSerifBold, 'PINEAPPLE', cx, 108, INK2, 1, 0.4, 0.5);
  drawText(c, fonts.labelSerifItalic, 'body oil', cx, 121, INK2, 1, 0, 0.5);
  const petal = freshPaint();
  petal.setColor(skColor(PETAL, 0.9));
  for (let i = 0; i < 5; i++) {
    const a = (i / 5) * 2 * Math.PI - Math.PI / 2;
    c.drawCircle(cx + Math.cos(a) * 2.6, 136 + Math.sin(a) * 2.6, 2.2, petal);
  }
  const heart = freshPaint();
  heart.setColor(skColor(0xf2c230, 1));
  c.drawCircle(cx, 136, 1, heart);
  drawText(c, fonts.labelSmall, '100 ml  ·  3.4 fl oz', cx, 152, INK2, 0.75, 0.3, 0.5);
  return recorder.finishRecordingAsPicture();
}

/** Top bar, copy and the Add to bag pill. */
export function makeUi(l: Layout, fonts: Fonts): SkPicture {
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, l.height));
  drawText(c, fonts.sans13, 'ISLA BLOOM', 24, l.barY, INK2, 1, 3, 0);
  const cx = 349;
  const cy = l.barY;
  let p = freshPaint();
  p.setColor(skColor(INK2, 0.1));
  c.drawCircle(cx, cy, 20, p);
  p = freshPaint();
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(1.6);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  p.setColor(skColor(INK2, 1));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - 7.5, cy - 3.5, 15, 12.5), 2.5, 2.5), p);
  const handle = Skia.PathBuilder.Make();
  handle.moveTo(cx - 4, cy - 3.5);
  handle.cubicTo(cx - 4, cy - 10, cx + 4, cy - 10, cx + 4, cy - 3.5);
  c.drawPath(handle.detach(), p);
  p = freshPaint();
  p.setColor(skColor(INK, 1));
  c.drawCircle(cx + 14, cy - 8, 8, p);
  drawText(c, fonts.bold10, '1', cx + 14, cy - 8, CREAM, 1, 0, 0.5);

  drawText(c, fonts.semi11, 'BODY OIL  ·  NO. 07', 24, l.captionY, INK2, 0.65, 2.2, 0);
  drawText(c, fonts.serif34, 'Golden Pineapple', 24, l.titleY, INK, 1, 0, 0);
  drawText(c, fonts.sans14, 'Cold-pressed pineapple & coconut for a sunlit glow.', 24, l.descY, INK2, 0.72, 0, 0);
  drawText(c, fonts.bold26, '$28', 24, l.ctaTop + 19, INK, 1, 0, 0);
  drawText(c, fonts.medium12, '100 ml', 24, l.ctaTop + 43, INK2, 0.6, 0, 0);

  p = freshPaint();
  p.setColor(skColor(INK, 1));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(169, l.ctaTop, 200, 56), 28, 28), p);
  drawText(c, fonts.semi16, 'Add to bag', 269, l.ctaTop + 28, CREAM, 1, 0, 0.5);
  return recorder.finishRecordingAsPicture();
}

// Per-frame drawing (worklets; helpers above their callers)

/** A radial falloff squashed into an ellipse: full `alpha` at the centre, 0 at the rim. */
function softEllipse(c: SkCanvas, x: number, y: number, rx: number, ry: number, hex: number, alpha: number) {
  'worklet';
  if (alpha <= 0.001) return;
  c.save();
  c.translate(x, y);
  c.scale(1, ry / rx);
  const p = freshPaint();
  p.setShader(
    Skia.Shader.MakeRadialGradient(Skia.Point(0, 0), rx, [skColor(hex, alpha), skColor(hex, alpha * 0.45), skColor(hex, 0)], [0, 0.5, 1], TileMode.Clamp),
  );
  c.drawCircle(0, 0, rx, p);
  c.restore();
}

function drawImage(c: SkCanvas, img: SkImage, x: number, y: number, w: number, h: number) {
  'worklet';
  c.drawImageRectOptions(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(x, y, w, h), FilterMode.Linear, MipmapMode.Linear, freshPaint());
}

export function drawFlowers(c: SkCanvas, l: Layout, img: SkImage | null, drift: number, ambient: number) {
  'worklet';
  if (!img) return;
  for (let i = 0; i < FLOWERS.length; i++) {
    const fl = FLOWERS[i];
    const a = (2 * Math.PI * ambient) / LOOP + fl.phase;
    const x = fl.x + fl.dx * drift + 3 * Math.sin(a);
    const y = (fl.fromTop ? l.top : l.heroBase) + fl.y + fl.dy * drift + 2.5 * Math.cos(a);
    const rot = fl.rot + fl.drot * drift + 3 * Math.sin(a + 0.7);
    const h = (fl.size * FLOWER_H) / FLOWER_W;
    c.save();
    c.translate(x, y);
    c.rotate(rot, 0, 0);
    drawImage(c, img, -fl.size / 2, -h / 2, fl.size, h);
    c.restore();
  }
}

/** A soft diagonal band of light crossing the glass from left to right. */
function drawSheen(c: SkCanvas, w: number, h: number, progress: number) {
  'worklet';
  const alpha = 0.55 * Math.sin(Math.PI * progress);
  const x = -w / 2 - 50 + (w + 100) * progress;
  const top = -h / 2;
  const bottom = h / 2;
  const band = Skia.PathBuilder.Make();
  band.moveTo(x - 14, bottom);
  band.lineTo(x + 10, bottom);
  band.lineTo(x + 50, top);
  band.lineTo(x + 26, top);
  band.close();
  const p = freshPaint();
  p.setBlendMode(BlendMode.SrcATop);
  p.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(x - 14, 0),
      Skia.Point(x + 50, 0),
      [skColor(0xffffff, 0), skColor(0xffffff, alpha), skColor(0xffffff, 0)],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  c.drawPath(band.detach(), p);
}

export type HeroArt = {
  top: SkImage | null;
  bottom: SkImage | null;
  bottle: SkPicture | null;
  bottleClip: SkPath;
  topClip: SkPath;
  faceOval: SkPath;
};

export function drawHero(c: SkCanvas, l: Layout, art: HeroArt, fr: Frame, ambient: number) {
  'worklet';
  // Contact shadow on the backdrop.
  softEllipse(c, 196.5, l.heroBase - 2, 96, 13, SHADE, 0.38);

  // The bottle's pose: centre, rotation and scale for the rise, with its hover bob.
  const r = fr.rise;
  const hover = clamp(r, 0, 1);
  const wave = (2 * Math.PI * 3 * ambient) / LOOP;
  const bx = 196.5 - 10.5 * r;
  const by = l.faceY + 135 - 243 * r + 4 * Math.sin(wave) * hover;
  const bAngle = -9 * r + 1.5 * Math.sin(wave + 1.3) * hover;
  const bScale = 0.92 + 0.08 * hover;

  // Glow behind the bottle.
  if (fr.glow > 0.001) {
    const p = freshPaint();
    p.setShader(Skia.Shader.MakeRadialGradient(Skia.Point(bx, by), 170, [skColor(GLOW, 0.6 * fr.glow), skColor(GLOW, 0)], [0, 1], TileMode.Clamp));
    c.drawCircle(bx, by, 170, p);
  }

  // Bottom half.
  if (art.bottom) drawImage(c, art.bottom, l.faceX - BOTTOM_FX * PINE, l.heroBase - BOTTOM_H * PINE, BOTTOM_W * PINE, BOTTOM_H * PINE);

  // The bottle's shadow on the cut face.
  if (hover > 0.001) {
    c.save();
    c.clipPath(art.faceOval, ClipOp.Intersect, true);
    softEllipse(c, l.faceX - 10, l.faceY + 1.5, 38 - 7 * hover, 8, SHADE, 0.34 * hover);
    c.restore();
  }

  // Bottle, pulled out of the fruit.
  if (r > -0.2 && art.bottle) {
    const w = BOTTLE_DRAW_W;
    c.save();
    c.clipPath(art.bottleClip, ClipOp.Intersect, true);
    c.translate(bx, by);
    c.rotate(bAngle, 0, 0);
    c.scale(bScale, bScale);
    if (fr.sheen >= 0) {
      c.saveLayer(undefined, Skia.XYWHRect(-w / 2, -BOTTLE_H / 2, w, BOTTLE_H));
      c.save();
      c.translate(-w / 2, -BOTTLE_H / 2);
      c.drawPicture(art.bottle);
      c.restore();
      drawSheen(c, w, BOTTLE_H, fr.sheen);
      c.restore();
    } else {
      c.translate(-w / 2, -BOTTLE_H / 2);
      c.drawPicture(art.bottle);
    }
    c.restore();
  }

  // Top half.
  if (art.top) {
    const o = fr.open;
    c.save();
    c.clipPath(art.topClip, ClipOp.Intersect, true);
    c.translate(l.faceX + 76 * o, l.faceY + 34 - 226 * o + fr.squash);
    c.rotate(-TOP_TILT * (1 - o), 0, 0);
    c.scale(PINE, PINE);
    drawImage(c, art.top, -TOP_PX, -TOP_PY, TOP_W, TOP_H);
    c.restore();
  }
}

export function drawDrops(c: SkCanvas, l: Layout, fr: Frame) {
  'worklet';
  const total = DROP_COUNT + GLOB_COUNT;
  for (let bi = 0; bi < fr.bursts.length; bi++) {
    const b = fr.bursts[bi];
    // A bright ring flashes off the rim as the burst leaves it.
    if (b.age < 0.35) {
      const e = easeOutCubic(b.age / 0.35);
      const s = 1 + 0.7 * e;
      const p = freshPaint();
      p.setStyle(PaintStyle.Stroke);
      p.setStrokeWidth(2.5);
      p.setColor(skColor(0xfff6c8, 0.6 * (1 - e) * b.strength));
      c.drawOval(Skia.XYWHRect(l.faceX - l.rx * s, l.faceY - l.ry * s, 2 * l.rx * s, 2 * l.ry * s), p);
    }
    for (let i = 0; i < total; i++) {
      const d = dropAt(i, b.age, b.strength, l.rx, l.ry);
      if (d === null || d.alpha <= 0.01) continue;
      const speed = Math.hypot(d.vx, d.vy);
      const stretch = 1 + Math.min(speed / 900, 1.2);
      const len = d.r * stretch;
      const wid = d.r / Math.sqrt(stretch);
      c.save();
      c.translate(l.faceX + d.x, l.faceY + d.y);
      c.rotate((Math.atan2(d.vy, d.vx) * 180) / Math.PI, 0, 0);
      const oval = Skia.XYWHRect(-len, -wid, 2 * len, 2 * wid);
      const fill = freshPaint();
      fill.setColor(skColor(JUICE, 0.88 * d.alpha));
      c.drawOval(oval, fill);
      const rim = freshPaint();
      rim.setStyle(PaintStyle.Stroke);
      rim.setStrokeWidth(1);
      rim.setColor(skColor(JUICE_RIM, 0.7 * d.alpha));
      c.drawOval(oval, rim);
      c.restore();
      const hl = freshPaint();
      hl.setColor(skColor(0xfffdf0, 0.9 * d.alpha));
      c.drawCircle(l.faceX + d.x - 0.28 * d.r, l.faceY + d.y - 0.3 * d.r, 0.38 * d.r, hl);
    }
  }
}
