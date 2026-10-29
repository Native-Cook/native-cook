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
  type SkPathBuilder,
  type SkPicture,
} from '@shopify/react-native-skia';

import { clamp, easeOutCubic, hash, lineProgress, mod, type Frame } from './floodMotion';

/* Layout, palette and drawing of Moka Flood Welcome, ported from MokaFloodWelcomeArt.swift. */

export const HAND_FONT = require('./assets/GochiHand-Regular.ttf');

// Palette
export const PEACH = 0xf8d0c3;
const COFFEE = 0x402d06;
const CREMA = 0x6e4d17;
const SPECK = 0x8a6427;
const POT = 0x8c0f22;
const POT_BAND = 0x6e0a1a;
const POT_LID = 0x7a0c1d;
const POT_LINE = 0xe99aa6;
const HANDLE = 0xd47e8d;
const KNOB = 0xd6a4a3;
const GLASS_LINE = 0x3b2a96;
const SHADOW = 0xe3a898;
const CREAM = 0xf6eedb;

const SPOUT_X = -99;
const SPOUT_Y = -125;
const POT_SCALE = 0.86;

export function hexColor(hex: number, alpha?: number) {
  'worklet';
  const a = alpha === undefined ? 1 : Math.min(Math.max(alpha, 0), 1);
  return `rgba(${(hex >> 16) & 0xff}, ${(hex >> 8) & 0xff}, ${hex & 0xff}, ${a})`;
}

function skColor(hex: number, alpha: number) {
  'worklet';
  return Skia.Color(hexColor(hex, alpha));
}

function fillPaint(hex: number, alpha: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(skColor(hex, alpha));
  return p;
}

function strokePaint(hex: number, alpha: number, width: number, round: boolean) {
  'worklet';
  const p = fillPaint(hex, alpha);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  if (round) {
    p.setStrokeCap(StrokeCap.Round);
    p.setStrokeJoin(StrokeJoin.Round);
  }
  return p;
}

// Layout

export type Layout = {
  height: number;
  top: number;
  barY: number;
  ctaTop: number;
  dotsY: number;
  hintY: number;
  glassX: number;
  glassTop: number;
  glassBottom: number;
  pivotX: number;
  pivotY: number;
  titleY: number;
  subY: number;
  floor: number;
};

export function makeLayout(height: number, top: number, bottom: number): Layout {
  const ctaTop = height - bottom - 16 - 56;
  const glassBottom = ctaTop - 130;
  const glassTop = glassBottom - 172;
  const titleY = glassTop - 74;
  return {
    height,
    top,
    barY: top + 26,
    ctaTop,
    dotsY: ctaTop - 30,
    hintY: ctaTop - 70,
    glassX: 132,
    glassTop,
    glassBottom,
    pivotX: 266,
    pivotY: glassTop - 170,
    titleY,
    subY: titleY + 3 * 64 - 6,
    floor: glassBottom - 5,
  };
}

/** The flood surface's base height (or NaN while there is none) and wave share. */
function floodBase(l: Layout, f: Frame) {
  'worklet';
  if (f.drain >= 0) return -70 + (l.height + 110) * f.drain;
  if (f.volume <= 1.75) return NaN;
  return l.floor - (l.floor + 70) * clamp((f.volume - 1.75) / 1.25, 0, 1);
}

function floodAmp(f: Frame) {
  'worklet';
  if (f.drain >= 0) return 1;
  return easeOutCubic(clamp((f.volume - 1.75) / 0.4, 0, 1));
}

/** Whether the flood covers the status bar (for its style). */
export function coversTop(l: Layout, f: Frame) {
  'worklet';
  const base = floodBase(l, f);
  return !Number.isNaN(base) && base + 9 * floodAmp(f) < l.top * 0.6;
}

// Static artwork (recorded once on the JS thread)

function arch(p: SkPathBuilder, xb: number, xt: number, w: number, yb: number, yt: number) {
  p.moveTo(xb - w / 2, yb);
  p.lineTo(xt - w / 2, yt + w / 2);
  p.quadTo(xt, yt - w * 0.5, xt + w / 2, yt + w / 2);
  p.lineTo(xb + w / 2, yb);
}

/** The pot in its own coordinates (pivot at the waist, upright, unscaled). */
export function makePot(): SkPicture {
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(-140, -160, 300, 320));
  const handle = Skia.PathBuilder.Make();
  handle.moveTo(62, -102);
  handle.cubicTo(122, -118, 118, -26, 58, -36);
  c.drawPath(handle.detach(), strokePaint(HANDLE, 1, 18, true));
  const spout = Skia.PathBuilder.Make();
  spout.moveTo(-58, -114).lineTo(-99, -125).lineTo(-68, -86).close();
  c.drawPath(spout.detach(), fillPaint(POT, 1));
  const upper = Skia.PathBuilder.Make();
  upper.moveTo(-52, -6).lineTo(-70, -112).lineTo(70, -112).lineTo(52, -6).close();
  c.drawPath(upper.detach(), fillPaint(POT, 1));
  const boiler = Skia.PathBuilder.Make();
  boiler.moveTo(-52, 7).lineTo(-76, 116).quadTo(-77, 122, -70, 122).lineTo(70, 122).quadTo(77, 122, 76, 116).lineTo(52, 7).close();
  c.drawPath(boiler.detach(), fillPaint(POT, 1));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-67, -121, 134, 10), 3, 3), fillPaint(POT_LID, 1));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-36, -131, 16, 12), 3, 3), fillPaint(KNOB, 1));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-57, -8, 114, 16), 2, 2), fillPaint(POT_BAND, 1));
  const lines = Skia.PathBuilder.Make();
  const upperArches = [
    [-36, -50],
    [-12, -17],
    [12, 17],
    [36, 50],
  ];
  for (const a of upperArches) arch(lines, a[0], a[1], 21, -20, -100);
  const lowerArches = [
    [-34, -54],
    [-11, -18],
    [11, 18],
    [34, 54],
  ];
  for (const a of lowerArches) arch(lines, a[1], a[0], 19, 110, 20);
  c.drawPath(lines.detach(), strokePaint(POT_LINE, 1, 2.4, true));
  c.drawLine(-46, 14, -66, 112, strokePaint(0xffffff, 0.1, 6, true));
  return recorder.finishRecordingAsPicture();
}

function glassOutline(p: SkPathBuilder, l: Layout) {
  const x = l.glassX;
  const t = l.glassTop;
  const b = l.glassBottom;
  p.moveTo(x - 76, t);
  p.lineTo(x - 61.5, b - 12);
  p.quadTo(x - 60, b, x - 48, b);
  p.lineTo(x + 48, b);
  p.quadTo(x + 60, b, x + 61.5, b - 12);
  p.lineTo(x + 76, t);
}

function halfEllipse(p: SkPathBuilder, cx: number, cy: number, rx: number, ry: number, front: boolean) {
  for (let i = 0; i <= 24; i++) {
    const a = (Math.PI * i) / 24;
    const x = cx - rx * Math.cos(a);
    const y = cy + (front ? 1 : -1) * ry * Math.sin(a);
    if (i === 0) p.moveTo(x, y);
    else p.lineTo(x, y);
  }
}

/** The floor shadow and the glass's clear body: drawn under the coffee. */
export function makeGlassBack(l: Layout): SkPicture {
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, l.height));
  const b = l.glassBottom;
  const shadow = Skia.PathBuilder.Make();
  shadow.moveTo(112, b - 1).lineTo(296, b + 1).quadTo(302, b + 4, 294, b + 7).lineTo(112, b + 7).close();
  const sp = fillPaint(0, 1);
  sp.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(150, b),
      Skia.Point(300, b),
      [skColor(SHADOW, 1), skColor(SHADOW, 0.95), skColor(SHADOW, 0)],
      null,
      TileMode.Clamp,
    ),
  );
  c.drawPath(shadow.detach(), sp);
  return recorder.finishRecordingAsPicture();
}

export function makeGlassBody(l: Layout): SkPicture {
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, l.height));
  const body = Skia.PathBuilder.Make();
  glassOutline(body, l);
  for (let i = 1; i <= 24; i++) {
    const a = (Math.PI * i) / 24;
    body.lineTo(l.glassX + 76 * Math.cos(a), l.glassTop - 9 * Math.sin(a));
  }
  body.close();
  c.drawPath(body.detach(), fillPaint(0xffffff, 0.18));
  return recorder.finishRecordingAsPicture();
}

export function makeGlassLines(l: Layout): SkPicture {
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, l.height));
  const x = l.glassX;
  const t = l.glassTop;
  const b = l.glassBottom;
  const p = Skia.PathBuilder.Make();
  glassOutline(p, l);
  halfEllipse(p, x, t, 76, 9, true);
  halfEllipse(p, x, t, 76, 9, false);
  halfEllipse(p, x, t + 28, 73.5, 8, true);
  halfEllipse(p, x, b - 14, 60, 7, true);
  for (let k = -2; k <= 2; k++) arch(p, x + k * 23, x + k * 27.5, 17, b - 19 - Math.abs(k) * 2, t + 50);
  c.drawPath(p.detach(), strokePaint(GLASS_LINE, 1, 3, true));
  return recorder.finishRecordingAsPicture();
}

export function makeGlassInterior(l: Layout): SkPath {
  const x = l.glassX;
  const t = l.glassTop;
  const b = l.glassBottom - 14;
  const p = Skia.PathBuilder.Make();
  p.moveTo(x - 73, t - 40).lineTo(x - 73, t).lineTo(x - 59.5, b).quadTo(x, b + 14, x + 59.5, b).lineTo(x + 73, t).lineTo(x + 73, t - 40).close();
  return p.detach();
}

export function makeGrain(): SkImage | null {
  // Same xorshift noise as the Swift tile.
  const n = 160;
  const bytes = new Uint8Array(n * n * 4);
  let seed = 0x9e3779b9 >>> 0;
  for (let i = 0; i < n * n; i++) {
    seed ^= seed << 13;
    seed >>>= 0;
    seed ^= seed >>> 17;
    seed ^= seed << 5;
    seed >>>= 0;
    const v = seed & 0xff;
    bytes[i * 4] = v;
    bytes[i * 4 + 1] = v;
    bytes[i * 4 + 2] = v;
    bytes[i * 4 + 3] = 255;
  }
  return Skia.Image.MakeImage({ width: n, height: n, alphaType: AlphaType.Opaque, colorType: ColorType.RGBA_8888 }, Skia.Data.fromBytes(bytes), n * 4);
}

export function makeGrainLayer(l: Layout, grain: SkImage | null, alpha: number): SkPicture {
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, l.height));
  if (grain) {
    const p = Skia.Paint();
    const m = Skia.Matrix();
    m.scale(0.5, 0.5);
    p.setShader(grain.makeShaderOptions(TileMode.Repeat, TileMode.Repeat, FilterMode.Nearest, MipmapMode.None, m));
    p.setAlphaf(alpha);
    p.setBlendMode(BlendMode.Overlay);
    c.drawRect(Skia.XYWHRect(0, 0, 393, l.height), p);
  }
  return recorder.finishRecordingAsPicture();
}

// Copy

export type Fonts = {
  hand28: SkFont;
  hand22: SkFont;
  hand60: SkFont;
  semi15: SkFont;
  semi17: SkFont;
  sans16: SkFont;
};

function textWidth(font: SkFont, s: string) {
  const widths = font.getGlyphWidths(font.getGlyphIDs(s));
  let total = 0;
  for (let i = 0; i < widths.length; i++) total += widths[i];
  return total;
}

/** Draws `s` with its line box centred on `cy`; `align` 0 = left at x, 0.5 = centred, 1 = right. */
function drawText(c: SkCanvas, font: SkFont, s: string, x: number, cy: number, hex: number, alpha: number, align: number) {
  const paint = fillPaint(hex, alpha);
  const m = font.getMetrics();
  c.drawText(s, x - textWidth(font, s) * align, cy - (m.ascent + m.descent) / 2, paint, font);
}

/** The bar, dots and CTA in one ink (and the hint on the peach layer). */
export function makeChrome(l: Layout, fonts: Fonts, coffee: boolean): SkPicture {
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, l.height));
  const main = coffee ? CREAM : COFFEE;
  drawText(c, fonts.hand28, 'brewday', 24, l.barY, main, 1, 0);
  drawText(c, fonts.semi15, 'Skip', 369, l.barY, main, coffee ? 0.7 : 0.62, 1);
  if (!coffee) drawText(c, fonts.hand22, 'hold the pot to pour', 196.5, l.hintY, main, 0.62, 0.5);
  let x = 196.5 - (22 + 32) / 2;
  for (let i = 0; i < 3; i++) {
    const w = i === 0 ? 22 : 8;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, l.dotsY - 4, w, 8), 4, 4), fillPaint(main, i === 0 ? 1 : 0.3));
    x += w + 8;
  }
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(24, l.ctaTop, 345, 56), 28, 28), fillPaint(main, 1));
  drawText(c, fonts.semi17, 'Get started', 196.5, l.ctaTop + 28, coffee ? COFFEE : CREAM, 1, 0.5);
  return recorder.finishRecordingAsPicture();
}

export type TitleArt = { pictures: SkPicture[]; widths: number[]; sub: SkPicture };

const TITLE_LINES = ['brew your', 'morning', 'routine!'];
const SUB_LINES = ['Brewday lines up your first cup,', 'your stretch and the plan for today.'];

export function makeTitle(l: Layout, fonts: Fonts): TitleArt {
  const pictures: SkPicture[] = [];
  const widths: number[] = [];
  for (let i = 0; i < TITLE_LINES.length; i++) {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, l.height));
    drawText(c, fonts.hand60, TITLE_LINES[i], 32, l.titleY + i * 64, CREAM, 1, 0);
    pictures.push(recorder.finishRecordingAsPicture());
    widths.push(textWidth(fonts.hand60, TITLE_LINES[i]));
  }
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(0, -20, 393, l.height + 40));
  for (let i = 0; i < SUB_LINES.length; i++) drawText(c, fonts.sans16, SUB_LINES[i], 32, l.subY + i * 22, CREAM, 0.78, 0);
  return { pictures, widths, sub: recorder.finishRecordingAsPicture() };
}

export type Art = {
  pot: SkPicture;
  glassBack: SkPicture;
  glassBody: SkPicture;
  glassLines: SkPicture;
  interior: SkPath;
  grainPeach: SkPicture;
  grainCoffee: SkPicture;
  chromePeach: SkPicture | null;
  chromeCoffee: SkPicture | null;
  title: TitleArt | null;
};

// Per-frame drawing (UI thread)

function meniscus(x: number, level: number, f: Frame, l: Layout, impactX: number) {
  'worklet';
  const u = (x - (l.glassX - 73)) / 146;
  let y = level + 2.6 * f.slosh * Math.cos(Math.PI * u) * Math.sin(7.1 * f.time) + 1.1 * f.slosh * Math.sin(2 * Math.PI * u * 1.5 + 9.3 * f.time);
  if (!Number.isNaN(impactX)) {
    const d = Math.abs(x - impactX);
    y += 2.2 * Math.sin(0.2 * d - 17 * f.time) * Math.exp(-d / 38);
  }
  const wall = Math.min(u, 1 - u) * 146;
  y -= 2.2 * Math.exp(-wall / 4);
  return y;
}

function glassLevel(l: Layout, volume: number) {
  'worklet';
  const bottom = l.glassBottom - 14;
  const rim = l.glassTop + 1;
  return bottom - (bottom - rim) * clamp(volume, 0, 1);
}

function drawGlassCoffee(c: SkCanvas, l: Layout, art: Art, f: Frame, impactX: number) {
  'worklet';
  if (f.drain >= 0 || f.volume <= 0.001) return;
  const x = l.glassX;
  const level = glassLevel(l, f.volume);
  const x0 = x - 74;
  const x1 = x + 74;
  const liquid = Skia.PathBuilder.Make();
  for (let px = x0, i = 0; px <= x1 + 0.1; px += 4, i++) {
    const y = meniscus(px, level, f, l, impactX);
    if (i === 0) liquid.moveTo(px, y);
    else liquid.lineTo(px, y);
  }
  liquid.lineTo(x1, l.glassBottom).lineTo(x0, l.glassBottom).close();
  c.save();
  c.clipPath(art.interior, ClipOp.Intersect, true);
  c.drawPath(liquid.detach(), fillPaint(COFFEE, 1));
  if (f.volume < 1.02) {
    const band = Skia.PathBuilder.Make();
    for (let bx = x0, i = 0; bx <= x1 + 0.1; bx += 4, i++) {
      const y = meniscus(bx, level, f, l, impactX) + 2.5;
      if (i === 0) band.moveTo(bx, y);
      else band.lineTo(bx, y);
    }
    c.drawPath(band.detach(), strokePaint(CREMA, 0.9, 2.5, false));
  }
  c.restore();

  const dome = clamp((f.volume - 1) / 0.15, 0, 1);
  if (dome > 0) {
    const h = 14 * dome * (1 + 0.06 * Math.sin(6.3 * f.time));
    const d = Skia.PathBuilder.Make();
    for (let i = 0; i <= 28; i++) {
      const u = i / 28;
      const dx = x - 78 + 156 * u;
      const dy = l.glassTop + 3 - h * Math.pow(Math.sin(Math.PI * u), 0.6) + 1.2 * Math.sin(9 * u + 8 * f.time) * dome;
      if (i === 0) d.moveTo(dx, dy);
      else d.lineTo(dx, dy);
    }
    d.lineTo(x + 76, l.glassTop + 8).lineTo(x - 76, l.glassTop + 8).close();
    c.drawPath(d.detach(), fillPaint(COFFEE, 1));
  }
}

function drawSpill(c: SkCanvas, l: Layout, side: number, s: number, w: number) {
  'worklet';
  if (s <= 0) return;
  const x = l.glassX;
  const t = l.glassTop;
  const b = l.glassBottom;
  const xs = [x + side * 77, x + side * 63.5, x + side * 58];
  const ys = [t + 4, b - 8, b + 1];
  const lengths = [0, 0, 0];
  for (let i = 1; i < 3; i++) lengths[i] = lengths[i - 1] + Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
  const front = Math.pow(s, 1.5) * lengths[2];
  const p = Skia.PathBuilder.Make();
  p.moveTo(xs[0], ys[0]);
  for (let i = 1; i < 3; i++) {
    if (lengths[i] <= front) {
      p.lineTo(xs[i], ys[i]);
    } else {
      const k = (front - lengths[i - 1]) / (lengths[i] - lengths[i - 1]);
      p.lineTo(xs[i - 1] + (xs[i] - xs[i - 1]) * k, ys[i - 1] + (ys[i] - ys[i - 1]) * k);
      break;
    }
  }
  c.drawPath(p.detach(), strokePaint(COFFEE, 1, w, true));
}

function drawStream(c: SkCanvas, f: Frame, tipX: number, tipY: number, landY: number) {
  'worklet';
  if (!f.stream) return;
  const top = tipY + f.tail;
  const bottom = Math.min(tipY + f.head, landY + 2);
  if (bottom <= top + 1) return;
  const w = 11 * f.width;
  const n = 18;
  const p = Skia.PathBuilder.Make();
  const rx: number[] = [];
  const ry: number[] = [];
  let headX = tipX;
  let tailX = tipX;
  for (let i = 0; i <= n; i++) {
    const y = top + ((bottom - top) * i) / n;
    const fall = y - tipY;
    const bend = -5 * (1 - Math.exp(-fall / 16)) + 0.5 * Math.sin(0.06 * y - 21 * f.time) * Math.min(1, fall / 40);
    const taper = w * (1 - 0.18 * Math.min(1, fall / 260)) * (f.tail > 0 ? 0.85 : 1);
    const cx = tipX + bend;
    if (i === 0) {
      p.moveTo(cx - taper / 2, y);
      tailX = cx;
    } else p.lineTo(cx - taper / 2, y);
    rx.push(cx + taper / 2);
    ry.push(y);
    headX = cx;
  }
  for (let i = n; i >= 0; i--) p.lineTo(rx[i], ry[i]);
  p.close();
  const paint = fillPaint(COFFEE, 1);
  c.drawPath(p.detach(), paint);
  const endR = (w * 0.82) / 2;
  c.drawCircle(headX, bottom, endR, paint);
  if (f.tail > 0) c.drawCircle(tailX, top, endR, paint);
}

function floodY(x: number, base: number, amp: number, time: number) {
  'worklet';
  const tilt = 0.06 * Math.sin(1.7 * time + 0.4) * (x - 196.5);
  const waves = 9 * (0.6 * Math.sin((x * 2 * Math.PI) / 230 + 2.1 * time) + 0.4 * Math.sin((x * 2 * Math.PI) / 120 - 3.3 * time + 1.3));
  return base + amp * (waves + tilt);
}

function surfaceLine(p: SkPathBuilder, base: number, amp: number, time: number, offset: number) {
  'worklet';
  let x = -12;
  p.moveTo(x, floodY(x, base, amp, time) + offset);
  while (x < 405) {
    x += 6;
    p.lineTo(x, floodY(x, base, amp, time) + offset);
  }
}

function puddlePath(l: Layout, f: Frame): SkPath | null {
  'worklet';
  const s = clamp((f.volume - 1.28) / 0.47, 0, 1);
  if (f.drain >= 0 || s <= 0 || f.volume > 1.75) return null;
  const rx = 50 + 710 * Math.pow(s, 1.25);
  const ry = 0.42 * rx;
  const cx = l.glassX - 6;
  const cy = l.glassBottom + 4;
  const p = Skia.PathBuilder.Make();
  for (let i = 0; i < 72; i++) {
    const th = (2 * Math.PI * i) / 72;
    const wob = 1 + 0.05 * Math.sin(5 * th + 3.1 * f.time) + 0.03 * Math.sin(9 * th - 4.3 * f.time);
    const x = cx + rx * wob * Math.cos(th);
    const y = Math.max(l.floor, cy + ry * wob * Math.sin(th));
    if (i === 0) p.moveTo(x, y);
    else p.lineTo(x, y);
  }
  p.close();
  return p.detach();
}

function hash2(i: number, salt: number) {
  'worklet';
  return hash(i * 7.31 + salt * 1.97);
}

function drawFloodLife(c: SkCanvas, l: Layout, base: number, amp: number, time: number) {
  'worklet';
  const band = Skia.PathBuilder.Make();
  surfaceLine(band, base, amp, time, 1.5);
  c.drawPath(band.detach(), strokePaint(CREMA, 0.85, 4, false));
  const speck = fillPaint(SPECK, 0.75);
  for (let i = 0; i < 26; i++) {
    const x = mod(hash2(i, 1) * 417 + time * (5 + 9 * hash2(i, 2)), 417) - 12;
    const y = floodY(x, base, amp, time) + 7 + 16 * hash2(i, 3);
    c.drawCircle(x, y, 1.1 + 1.7 * hash2(i, 4), speck);
  }
  const ring = strokePaint(CREMA, 0.8, 1.3, false);
  const span = l.height + 60;
  for (let i = 0; i < 18; i++) {
    const x = 18 + 357 * hash2(i, 5) + 6 * Math.sin(1.3 * time + i);
    const y = l.height + 30 - mod(time * (26 + 44 * hash2(i, 6)) + hash2(i, 7) * span, span);
    c.drawCircle(x, y, 1.6 + 3.4 * hash2(i, 8), ring);
  }
}

function drawTitle(c: SkCanvas, l: Layout, title: TitleArt, f: Frame) {
  'worklet';
  for (let i = 0; i < 3; i++) {
    const p = lineProgress(f.title, i);
    if (p <= 0) continue;
    const cy = l.titleY + i * 64;
    c.save();
    c.clipRect(Skia.XYWHRect(20, cy - 50, 12 + p * (title.widths[i] + 16), 100), ClipOp.Intersect, true);
    c.drawPicture(title.pictures[i]);
    c.restore();
  }
  if (f.sub > 0) {
    c.save();
    c.translate(0, 8 * (1 - f.sub));
    const paint = Skia.Paint();
    paint.setAlphaf(f.sub);
    c.saveLayer(paint);
    c.drawPicture(title.sub);
    c.restore();
    c.restore();
  }
}

export function drawScene(c: SkCanvas, l: Layout, art: Art, f: Frame) {
  'worklet';
  c.drawRect(Skia.XYWHRect(0, 0, 393, l.height), fillPaint(PEACH, 1));
  c.drawPicture(art.glassBack);
  c.save();
  c.translate(l.pivotX, l.pivotY);
  c.rotate(f.angle, 0, 0);
  c.scale(POT_SCALE, POT_SCALE);
  c.drawPicture(art.pot);
  c.restore();

  const base = floodBase(l, f);
  const amp = floodAmp(f);
  const a = (f.angle * Math.PI) / 180;
  const sx = SPOUT_X * POT_SCALE;
  const sy = SPOUT_Y * POT_SCALE;
  const tipX = l.pivotX + sx * Math.cos(a) - sy * Math.sin(a);
  const tipY = l.pivotY + sx * Math.sin(a) + sy * Math.cos(a);
  const level = glassLevel(l, f.volume);
  const inGlass = tipX > l.glassX - 70 && tipX < l.glassX + 70;
  let land = l.height + 40;
  if (inGlass && f.drain < 0) {
    land = f.volume > 1 ? l.glassTop + 3 - 14 * clamp((f.volume - 1) / 0.15, 0, 1) : f.volume > 0.001 ? level : l.glassBottom - 10;
  }
  if (!Number.isNaN(base)) land = Math.min(land, floodY(tipX, base, amp, f.time));
  drawStream(c, f, tipX, tipY, land);

  c.drawPicture(art.glassBody);
  const hits = f.stream && tipY + f.head >= land - 1 && f.tail < land - tipY;
  drawGlassCoffee(c, l, art, f, hits && inGlass ? tipX - 4 : NaN);
  c.drawPicture(art.glassLines);
  if (f.drain < 0) {
    drawSpill(c, l, -1, clamp((f.volume - 1.08) / 0.24, 0, 1), 10.5);
    drawSpill(c, l, 1, clamp((f.volume - 1.16) / 0.22, 0, 1), 7.5);
  }
  c.drawPicture(art.grainPeach);
  if (art.chromePeach) c.drawPicture(art.chromePeach);

  let region: SkPath | null = null;
  if (!Number.isNaN(base)) {
    if (base >= l.height + 20) return;
    const p = Skia.PathBuilder.Make();
    surfaceLine(p, base, amp, f.time, 0);
    p.lineTo(405, l.height + 60).lineTo(-12, l.height + 60).close();
    region = p.detach();
  } else {
    region = puddlePath(l, f);
  }
  if (!region) return;
  c.drawPath(region, fillPaint(COFFEE, 1));
  c.save();
  c.clipPath(region, ClipOp.Intersect, true);
  c.drawPicture(art.grainCoffee);
  if (!Number.isNaN(base)) drawFloodLife(c, l, base, amp, f.time);
  if (art.chromeCoffee) c.drawPicture(art.chromeCoffee);
  if (art.title) drawTitle(c, l, art.title, f);
  c.restore();
}
