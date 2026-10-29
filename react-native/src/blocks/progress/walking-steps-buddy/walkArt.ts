/*
 Everything of Walking Steps Buddy drawn in code with Skia: the parallax street, Bao the bun buddy,
 the goal bar, the confetti and the small icons, in the 393-pt-wide design space. Every function is a
 worklet, and every helper is declared above its callers.
 */
import { ClipOp, PaintStyle, Skia, StrokeCap, StrokeJoin, TileMode, type SkCanvas } from '@shopify/react-native-skia';

import {
  barX,
  bubble,
  BUSH_FACTOR,
  BUSH_TILE,
  CELEBRATE,
  clamp,
  CLOUD_DRIFT,
  dusk as duskAt,
  fract,
  FRONT_FACTOR,
  FRONT_TILE,
  hash,
  HILL_FACTOR,
  HILL_TILE,
  MASCOT_X,
  mod,
  smooth,
  TICK_X,
  TILE,
  tickPop,
  type Pose,
} from './walkMotion';

export const PAGE = 0xfcfcfa;
export const INK = 0x1d2733;
export const LABEL = 0x4a5a6c;
export const MUTED = 0x8a95a3;
const ORANGE = 0xff6a1f;
const ORANGE_LIGHT = 0xff9a4d;

export function hexString(hex: number) {
  return `#${hex.toString(16).padStart(6, '0')}`;
}

// MARK: Colour and paint

export function rgba(hex: number, a: number) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255, clamp(a, 0, 1));
}

export function mix(a: number, b: number, f: number, alpha: number) {
  'worklet';
  const g = clamp(f, 0, 1);
  const ch = (h: number, s: number) => ((h >> s) & 255) / 255;
  return Float32Array.of(
    ch(a, 16) + (ch(b, 16) - ch(a, 16)) * g,
    ch(a, 8) + (ch(b, 8) - ch(a, 8)) * g,
    ch(a, 0) + (ch(b, 0) - ch(a, 0)) * g,
    clamp(alpha, 0, 1),
  );
}

function solid(color: Float32Array) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  return p;
}

function lin(x0: number, y0: number, x1: number, y1: number, colors: Float32Array[], stops: number[] | null) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors, stops, TileMode.Clamp));
  return p;
}

function rad(cx: number, cy: number, r: number, colors: Float32Array[], stops: number[] | null) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, Math.max(0.01, r), colors, stops, TileMode.Clamp));
  return p;
}

function line(color: Float32Array, width: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  p.setColor(color);
  return p;
}

function ellipse(c: SkCanvas, cx: number, cy: number, w: number, h: number, paint: ReturnType<typeof solid>) {
  'worklet';
  c.drawOval(Skia.XYWHRect(cx - w / 2, cy - h / 2, w, h), paint);
}

function rrect(c: SkCanvas, x: number, y: number, w: number, h: number, r: number, paint: ReturnType<typeof solid>) {
  'worklet';
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r), paint);
}

// MARK: Helpers

function blob(c: SkCanvas, cx: number, cy: number, r: number, hi: number, base: number, shade: number) {
  'worklet';
  ellipse(c, cx, cy, 2 * r, 2 * r, rad(cx - 0.35 * r, cy - 0.45 * r, 1.35 * r, [rgba(hi, 1), rgba(base, 1), rgba(shade, 1)], [0, 0.5, 1]));
}

function softShadow(c: SkCanvas, cx: number, cy: number, w: number, h: number, alpha: number, hex: number) {
  'worklet';
  c.save();
  c.translate(cx, cy);
  c.scale(1, h / w);
  ellipse(c, 0, 0, w, w, rad(0, 0, w / 2, [rgba(hex, alpha), rgba(hex, alpha * 0.6), rgba(hex, 0)], [0, 0.5, 1]));
  c.restore();
}

const BLADES = [-5, 0.72, -5, 5, 0.8, 6, 0, 1, 1];

function tuft(c: SkCanvas, x: number, base: number, h: number, dark: number, light: number) {
  'worklet';
  const p = Skia.PathBuilder.Make();
  for (let i = 0; i < 3; i++) {
    const dx = BLADES[3 * i];
    const dh = BLADES[3 * i + 1];
    const lean = BLADES[3 * i + 2];
    const bx = x + (dx * h) / 20;
    const w = (7 * h) / 20;
    const top = base - h * dh;
    p.moveTo(bx - w / 2, base);
    p.quadTo(bx - w / 4 + (lean * h) / 40, base - h * dh * 0.55, bx + (lean * h) / 20, top);
    p.quadTo(bx + w / 4 + (lean * h) / 40, base - h * dh * 0.5, bx + w / 2, base);
    p.close();
  }
  c.drawPath(p.detach(), lin(x, base - h, x, base, [rgba(light, 1), rgba(dark, 1)], null));
}

/** Origins (x) of the tiles of a layer that cover the screen. */
function tileOrigins(distance: number, factor: number, width: number) {
  'worklet';
  const out: number[] = [];
  let x = -mod(distance * factor, width);
  while (x < 393 + 140) {
    if (x + width > -140) out.push(x);
    x += width;
  }
  return out;
}

// MARK: Scene parts

const CLOUDS = [60, -200, 1.0, 300, -250, 0.78, 190, -150, 0.62, 380, -292, 0.9];

function cloudPath(cx: number, cy: number, s: number) {
  'worklet';
  const p = Skia.PathBuilder.Make();
  p.addOval(Skia.XYWHRect(cx - 34 * s, cy - 22 * s, 44 * s, 44 * s));
  p.addOval(Skia.XYWHRect(cx - 6 * s, cy - 30 * s, 50 * s, 50 * s));
  p.addOval(Skia.XYWHRect(cx - 54 * s, cy - 6 * s, 34 * s, 30 * s));
  p.addOval(Skia.XYWHRect(cx + 26 * s, cy - 4 * s, 32 * s, 28 * s));
  p.addRRect(Skia.RRectXY(Skia.XYWHRect(cx - 46 * s, cy + 2 * s, 98 * s, 22 * s), 11 * s, 11 * s));
  return p.detach();
}

function drawClouds(c: SkCanvas, d: number, g: number, dk: number) {
  'worklet';
  for (let i = 0; i < 4; i++) {
    const tau = fract(d / TILE + i / 4);
    const alpha = smooth(0, 0.18, tau) * (1 - smooth(0.82, 1, tau));
    if (alpha < 0.01) continue;
    const s = CLOUDS[3 * i + 2];
    const x = CLOUDS[3 * i] - CLOUD_DRIFT * tau;
    const y = g + CLOUDS[3 * i + 1];
    c.drawPath(cloudPath(x, y, s), lin(x, y - 10 * s, x, y + 24 * s, [mix(0xffffff, 0xfff0ea, dk, alpha), mix(0xe4f1fb, 0xf3cbcb, dk, alpha)], null));
  }
}

function drawHills(c: SkCanvas, d: number, g: number, dk: number) {
  'worklet';
  const off = mod(d * HILL_FACTOR, HILL_TILE);
  const p = Skia.PathBuilder.Make();
  p.moveTo(-4, g + 2);
  for (let x = -4; x <= 397; x += 4) {
    const u = (2 * Math.PI * (x + off)) / HILL_TILE;
    p.lineTo(x, g - 22 - 10 * Math.sin(u) - 5 * Math.sin(2 * u + 1.3));
  }
  p.lineTo(397, g + 2);
  p.close();
  c.drawPath(p.detach(), lin(0, g - 40, 0, g, [mix(0xd3eedb, 0xdcd4e4, dk * 0.6, 1), mix(0xb9e0c5, 0xc2d3be, dk * 0.6, 1)], null));
}

function drawBushTile(c: SkCanvas, x: number, g: number) {
  'worklet';
  rrect(c, x + 66, g - 40, 9, 40, 3, lin(x + 66, 0, x + 75, 0, [rgba(0xd5a877, 1), rgba(0xb98a5c, 1)], null));
  blob(c, x + 52, g - 52, 20, 0xc7eda9, 0x9bd67e, 0x76bd5f);
  blob(c, x + 90, g - 50, 21, 0xc7eda9, 0x9bd67e, 0x76bd5f);
  blob(c, x + 71, g - 70, 29, 0xcdf0b0, 0xa2da84, 0x7cc164);
  blob(c, x + 190, g - 12, 17, 0xc2eba2, 0x94d276, 0x6fb95a);
  blob(c, x + 232, g - 11, 15, 0xc2eba2, 0x94d276, 0x6fb95a);
  blob(c, x + 211, g - 19, 22, 0xc9eeab, 0x9ad57c, 0x73bc5d);
  blob(c, x + 322, g - 11, 15, 0xc2eba2, 0x94d276, 0x6fb95a);
  blob(c, x + 342, g - 17, 19, 0xc9eeab, 0x9ad57c, 0x73bc5d);
  rrect(c, x + 396, g - 30, 7, 30, 3, solid(rgba(0xc99a6b, 1)));
  blob(c, x + 399, g - 48, 18, 0xcdf0b0, 0xa2da84, 0x7cc164);
}

function drawBench(c: SkCanvas, x: number, base: number) {
  'worklet';
  softShadow(c, x + 80, base, 176, 12, 0.16, 0x6b5a3e);
  rrect(c, x + 30, base - 30, 7, 24, 2, solid(rgba(0xe2c390, 1)));
  rrect(c, x + 140, base - 30, 7, 24, 2, solid(rgba(0xe2c390, 1)));
  for (let i = 0; i < 2; i++) {
    const sx = i === 0 ? 22 : 134;
    rrect(c, x + sx, base - 70, 7, 44, 3, lin(x + sx, 0, x + sx + 7, 0, [rgba(0xddb57a, 1), rgba(0xc59a60, 1)], null));
  }
  for (let i = 0; i < 2; i++) {
    const top = base - (i === 0 ? 68 : 50);
    rrect(c, x + 4, top, 152, 13, 5, lin(0, top, 0, top + 13, [rgba(0xf8e2b6, 1), rgba(0xe9c88e, 1)], null));
    rrect(c, x + 8, top + 1.5, 144, 2, 1, solid(rgba(0xffffff, 0.45)));
  }
  rrect(c, x, base - 34, 160, 12, 5, lin(0, base - 34, 0, base - 22, [rgba(0xfbe7bf, 1), rgba(0xefd09a, 1)], null));
  rrect(c, x + 2, base - 24, 156, 6, 3, solid(rgba(0xd9b479, 1)));
  for (let i = 0; i < 2; i++) {
    const lx = i === 0 ? 10 : 140;
    rrect(c, x + lx, base - 22, 9, 22, 2.5, lin(x + lx, 0, x + lx + 9, 0, [rgba(0xddb57a, 1), rgba(0xc59a60, 1)], null));
  }
}

function drawSign(c: SkCanvas, x: number, base: number) {
  'worklet';
  softShadow(c, x, base, 34, 8, 0.18, 0x6b5a3e);
  rrect(c, x - 3.5, base - 128, 7, 128, 3, lin(x - 3.5, 0, x + 3.5, 0, [rgba(0xf1f4f7, 1), rgba(0xc5ced8, 1)], null));
  const cy = base - 150;
  ellipse(c, x + 1.5, cy + 2.5, 56, 56, solid(rgba(0x2f6fb0, 0.18)));
  ellipse(c, x, cy, 56, 56, solid(rgba(0xffffff, 1)));
  ellipse(c, x, cy, 48, 48, rad(x - 8, cy - 10, 32, [rgba(0x7cc2f7, 1), rgba(0x3e95e6, 1)], null));
  ellipse(c, x - 1, cy - 13.5, 7, 7, solid(rgba(0xffffff, 1)));
  const limbs = Skia.PathBuilder.Make();
  limbs.moveTo(x - 1, cy - 7).lineTo(x + 1, cy + 4);
  limbs.moveTo(x + 1, cy + 4).lineTo(x - 5, cy + 13);
  limbs.moveTo(x + 1, cy + 4).lineTo(x + 7, cy + 12);
  limbs.moveTo(x - 7, cy + 1).lineTo(x, cy - 4).lineTo(x + 7, cy - 1);
  c.drawPath(limbs.detach(), line(rgba(0xffffff, 1), 3.2));
  const gleam = Skia.PathBuilder.Make();
  gleam.addArc(Skia.XYWHRect(x - 19, cy - 19, 38, 38), 200, 50);
  c.drawPath(gleam.detach(), line(rgba(0xffffff, 0.45), 3));
}

const FLOWERS = [24, 48, 0xff9fb8, 0xffe38a, 52, 58, 0xffd25c, 0xff9a4d, 78, 47, 0xff9fb8, 0xffe38a, 40, 40, 0xffffff, 0xffd25c, 64, 42, 0xff9fb8, 0xffe38a];

function drawPlanter(c: SkCanvas, x: number, base: number) {
  'worklet';
  softShadow(c, x + 50, base, 118, 10, 0.16, 0x6b5a3e);
  blob(c, x + 22, base - 36, 15, 0xb9e794, 0x8fd06e, 0x68b44e);
  blob(c, x + 78, base - 36, 15, 0xb9e794, 0x8fd06e, 0x68b44e);
  blob(c, x + 50, base - 42, 19, 0xb9e794, 0x8fd06e, 0x68b44e);
  for (let i = 0; i < 5; i++) {
    const fx = FLOWERS[4 * i];
    const fy = FLOWERS[4 * i + 1];
    for (let k = 0; k < 5; k++) {
      const a = (k * 2 * Math.PI) / 5;
      ellipse(c, x + fx + 4 * Math.cos(a), base - fy + 4 * Math.sin(a), 5.5, 5.5, solid(rgba(FLOWERS[4 * i + 2], 1)));
    }
    ellipse(c, x + fx, base - fy, 4, 4, solid(rgba(FLOWERS[4 * i + 3], 1)));
  }
  rrect(c, x, base - 30, 100, 30, 7, lin(0, base - 30, 0, base, [rgba(0xf1ae86, 1), rgba(0xd9825c, 1)], null));
  rrect(c, x - 3, base - 33, 106, 9, 4.5, lin(0, base - 33, 0, base - 24, [rgba(0xf8c2a0, 1), rgba(0xe79a73, 1)], null));
}

const PEBBLES = [150, 14, 8, 340, 28, 10, 520, 18, 7, 700, 30, 9, 810, 12, 6, 40, 26, 7];
const NEAR_TUFTS = [30, 16, 300, 20, 408, 14, 610, 18, 822, 22];

function drawPropTile(c: SkCanvas, x: number, g: number) {
  'worklet';
  const base = g + 4;
  for (let i = 0; i < 6; i++) {
    const px = PEBBLES[3 * i];
    const py = PEBBLES[3 * i + 1];
    const w = PEBBLES[3 * i + 2];
    ellipse(c, x + px, g + py, w, w * 0.5, solid(rgba(0xe6cb9c, 1)));
    ellipse(c, x + px - w * 0.12, g + py - w * 0.1, w * 0.5, w * 0.2, solid(rgba(0xffffff, 0.6)));
  }
  drawBench(c, x + 70, base);
  drawSign(c, x + 470, base);
  drawPlanter(c, x + 660, base);
  for (let i = 0; i < 5; i++) tuft(c, x + NEAR_TUFTS[2 * i], g + 3, NEAR_TUFTS[2 * i + 1], 0x6cbe4c, 0xa8e07e);
}

// MARK: Bao

function bunPath() {
  'worklet';
  const p = Skia.PathBuilder.Make();
  p.moveTo(0, 0);
  p.cubicTo(-58, 0, -76, -18, -75, -52);
  p.cubicTo(-74, -102, -42, -136, 0, -136);
  p.cubicTo(42, -136, 74, -102, 75, -52);
  p.cubicTo(76, -18, 58, 0, 0, 0);
  p.close();
  return p.detach();
}

function drawFoot(c: SkCanvas, x: number, y: number, angle: number, far: boolean) {
  'worklet';
  c.save();
  c.translate(x, y);
  c.rotate(angle, 0, 0);
  const cols = far ? [rgba(0xf3e6d2, 1), rgba(0xd6bf9f, 1)] : [rgba(0xfff8ee, 1), rgba(0xe6d2b6, 1)];
  ellipse(c, 0, 0, 38, 20, lin(0, -10, 0, 10, cols, null));
  ellipse(c, -4, -5, 18, 5, solid(rgba(0xffffff, far ? 0.25 : 0.55)));
  c.restore();
}

function drawArm(c: SkCanvas, px: number, py: number, angle: number, far: boolean) {
  'worklet';
  c.save();
  c.translate(px, py);
  c.rotate(angle, 0, 0);
  const cols = far ? [rgba(0xf1e3cd, 1), rgba(0xd9c3a3, 1)] : [rgba(0xfffcf6, 1), rgba(0xe8d6bc, 1)];
  rrect(c, -9.5, -6, 19, 34, 9.5, lin(-9.5, 0, 9.5, 0, cols, null));
  rrect(c, -9.5, -6, 19, 34, 9.5, line(rgba(0xdcc6a6, 0.8), 1.2));
  if (!far) ellipse(c, -3.5, 6, 5, 14, solid(rgba(0xffffff, 0.6)));
  c.restore();
}

function drawStrap(c: SkCanvas, hx: number, hy: number, angle: number) {
  'worklet';
  c.save();
  c.translate(hx, hy);
  c.rotate(angle, 0, 0);
  c.scale(0.84, 0.84);
  const strap = Skia.PathBuilder.Make();
  strap.moveTo(-13, 18).cubicTo(-13, -8, 13, -8, 13, 18);
  c.drawPath(strap.detach(), line(rgba(0xe2911f, 1), 4.4));
  const shine = Skia.PathBuilder.Make();
  shine.moveTo(-9, 4).quadTo(-8, -3, -2, -4);
  c.drawPath(shine.detach(), line(rgba(0xffd27a, 1), 1.6));
  c.restore();
}

function drawBag(c: SkCanvas, hx: number, hy: number, angle: number) {
  'worklet';
  c.save();
  c.translate(hx, hy);
  c.rotate(angle, 0, 0);
  c.scale(0.84, 0.84);
  c.save();
  c.translate(10, 10);
  c.rotate(24, 0, 0);
  rrect(c, -7, -26, 14, 46, 7, lin(-7, 0, 7, 0, [rgba(0xf3c784, 1), rgba(0xc98b45, 1)], null));
  for (let i = 0; i < 3; i++) {
    const sy = -19 + 8 * i;
    c.drawLine(-3.5, sy + 3, 3.5, sy, line(rgba(0xfbe3b8, 1), 2));
  }
  c.restore();
  const body = Skia.PathBuilder.Make();
  body.moveTo(-22, 16).lineTo(22, 16);
  body.quadTo(25, 18, 26, 50).quadTo(27, 58, 18, 58);
  body.lineTo(-18, 58);
  body.quadTo(-27, 58, -26, 50).quadTo(-25, 18, -22, 16);
  body.close();
  c.drawPath(body.detach(), lin(0, 16, 0, 58, [rgba(0xffd272, 1), rgba(0xf6b23e, 1), rgba(0xe69523, 1)], null));
  rrect(c, -16, 21, 5, 30, 2.5, solid(rgba(0xffffff, 0.35)));
  rrect(c, -22, 15, 44, 5, 2.5, solid(rgba(0xffe29a, 1)));
  const heart = Skia.PathBuilder.Make();
  heart.moveTo(6, 42).cubicTo(-2, 37, 1, 29, 6, 32).cubicTo(11, 29, 14, 37, 6, 42);
  c.drawPath(heart.detach(), solid(rgba(0xff7a59, 1)));
  c.restore();
}

function drawBao(c: SkCanvas, x: number, groundY: number, p: Pose, happy: boolean) {
  'worklet';
  const air = p.lift;
  softShadow(c, x, groundY, 128 * (1 - air / 110) - 10 * (-p.bob / 7), 15, 0.24 * (1 - air / 80), 0x6b5a3e);
  c.save();
  c.translate(x, groundY - air);
  drawFoot(c, 14 + p.far.x, -9 - p.far.lift, p.far.angle, true);
  drawFoot(c, -12 + p.near.x, -9 - p.near.lift, p.near.angle, false);
  c.restore();

  c.save();
  c.translate(x, groundY - 15 + p.bob - air);
  c.rotate(p.lean, 0, 0);
  c.scale(p.sx, p.sy);
  drawArm(c, 66, -72, p.arm, true);
  // The near arm comes out from behind the body and holds the tote away from it.
  const armAngle = 42 - 0.8 * p.arm;
  const a = (armAngle * Math.PI) / 180;
  const hx = -62 - 26 * Math.sin(a);
  const hy = -68 + 26 * Math.cos(a);
  const bagAngle = -p.arm * 0.35 + p.bag;
  drawArm(c, -62, -68, armAngle, false);
  const bun = bunPath();
  c.drawPath(bun, rad(-22, -98, 150, [rgba(0xffffff, 1), rgba(0xfff8ee, 1), rgba(0xe6d3b8, 1)], [0, 0.45, 1]));
  c.save();
  c.clipPath(bun, ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(-80, -40, 160, 42), lin(0, -40, 0, 0, [rgba(0xd8c2a2, 0), rgba(0xd8c2a2, 0.45)], null));
  ellipse(c, -30, -104, 46, 26, rad(-30, -104, 24, [rgba(0xffffff, 0.9), rgba(0xffffff, 0)], null));
  c.restore();
  const pleats = Skia.PathBuilder.Make();
  for (let i = 0; i < 5; i++) {
    const r = ((-70 + 35 * i) * Math.PI) / 180;
    const r2 = r + 0.35;
    pleats.moveTo(7 * Math.sin(r), -129 + 4 * Math.cos(r));
    pleats.quadTo(18 * Math.sin(r), -129 + 13 * Math.cos(r), 26 * Math.sin(r2), -129 + 17 * Math.cos(r2));
  }
  c.drawPath(pleats.detach(), line(rgba(0xe8d6bc, 1), 2));
  c.save();
  c.translate(0, -130);
  c.rotate(p.knot, 0, 0);
  const twist = Skia.PathBuilder.Make();
  twist.moveTo(-12, 2).cubicTo(-14, -10, -6, -18, 2, -20).cubicTo(8, -22, 11, -16, 9, -12).cubicTo(14, -8, 15, -2, 12, 2).close();
  c.drawPath(twist.detach(), rad(-4, -12, 20, [rgba(0xffffff, 1), rgba(0xfff6ea, 1), rgba(0xe4cfb0, 1)], null));
  const curl = Skia.PathBuilder.Make();
  curl.moveTo(-4, -4).quadTo(-3, -14, 5, -14);
  c.drawPath(curl.detach(), line(rgba(0xe3cfb2, 1), 2));
  c.restore();
  // Face, turned to the right.
  for (let i = 0; i < 2; i++) {
    const cx = i === 0 ? -4 : 58;
    ellipse(c, cx, -54, 20, 12, rad(cx, -54, 11, [rgba(0xff9c87, 0.7), rgba(0xff9c87, 0)], null));
  }
  for (let i = 0; i < 2; i++) {
    const ex = i === 0 ? 12 : 44;
    if (happy) {
      const arc = Skia.PathBuilder.Make();
      arc.moveTo(ex - 6, -66).quadTo(ex, -77, ex + 6, -66);
      c.drawPath(arc.detach(), line(rgba(0x2a2420, 1), 3.2));
    } else {
      ellipse(c, ex, -69, 10.5, 14, solid(rgba(0x2a2420, 1)));
      ellipse(c, ex - 1.8, -72.5, 3.6, 4.2, solid(rgba(0xffffff, 1)));
    }
  }
  const mouth = Skia.PathBuilder.Make();
  if (happy) {
    mouth.moveTo(21, -58).quadTo(28, -45, 35, -58).close();
    c.drawPath(mouth.detach(), solid(rgba(0x9a4232, 1)));
  } else {
    mouth.moveTo(21, -58).quadTo(24.5, -53, 28, -57).quadTo(31.5, -53, 35, -58);
    c.drawPath(mouth.detach(), line(rgba(0x2a2420, 1), 2.2));
  }
  drawBag(c, hx, hy, bagAngle);
  drawStrap(c, hx, hy, bagAngle);
  c.restore();
}

// MARK: Scene

export function drawScene(c: SkCanvas, d: number, t: number, g: number, p: Pose, happy: boolean) {
  'worklet';
  const dk = duskAt(t);
  const tint = 0.85 * dk;
  c.drawRect(
    Skia.XYWHRect(0, 0, 393, g + 8),
    lin(0, 0, 0, g + 8, [mix(0x8ccbf7, 0xb3a6ea, tint, 1), mix(0xb8e0fa, 0xf6bcb4, tint, 1), mix(0xe6f4fc, 0xffe4c8, tint, 1)], [0, 0.55, 1]),
  );
  // Sun: sets behind the hills at dusk and rises again for the next day.
  const sunY = g - 210 + 250 * dk;
  c.save();
  c.clipRect(Skia.XYWHRect(0, 0, 393, g), ClipOp.Intersect, true);
  ellipse(c, 318, sunY, 128, 128, rad(318, sunY, 64, [mix(0xfff3b0, 0xffc27a, dk, 0.55), mix(0xfff3b0, 0xffc27a, dk, 0)], null));
  ellipse(c, 318, sunY, 40, 40, rad(312, sunY - 6, 24, [mix(0xfffbe0, 0xffe2b0, dk, 1), mix(0xffd66b, 0xff9a5a, dk, 1)], null));
  c.restore();
  drawClouds(c, d, g, dk);
  drawHills(c, d, g, dk);
  const bushes = tileOrigins(d, BUSH_FACTOR, BUSH_TILE);
  for (let i = 0; i < bushes.length; i++) drawBushTile(c, bushes[i], g);
  c.drawRect(Skia.XYWHRect(0, g - 5, 393, 8), lin(0, g - 5, 0, g + 3, [rgba(0xc4e9a4, 1), rgba(0xa9dc86, 1)], null));
  const s = g + 38;
  c.drawRect(
    Skia.XYWHRect(0, g, 393, 40),
    lin(0, g, 0, s + 2, [rgba(0xf3ddb4, 1), rgba(0xf8ead0, 1), rgba(0xfaf1e1, 1), rgba(PAGE, 1)], [0, 0.5, 0.82, 1]),
  );
  c.drawRect(Skia.XYWHRect(0, g, 393, 1.2), solid(rgba(0xffffff, 0.55)));
  const props = tileOrigins(d, 1, TILE);
  for (let i = 0; i < props.length; i++) drawPropTile(c, props[i], g);
  drawBao(c, MASCOT_X, g + 22, p, happy);
  const front = tileOrigins(d, FRONT_FACTOR, FRONT_TILE);
  for (let i = 0; i < front.length; i++) {
    const x = front[i];
    tuft(c, x + 40, s - 1, 20, 0x58ae40, 0x93d56e);
    tuft(c, x + 196, s - 2, 26, 0x58ae40, 0x93d56e);
    tuft(c, x + 300, s - 1, 15, 0x58ae40, 0x93d56e);
  }
}

// MARK: Bar

export function drawBar(c: SkCanvas, t: number, stepCount: number, top: number) {
  'worklet';
  const cy = top + 6;
  rrect(c, 20, top, 353, 12, 6, solid(rgba(0xedf0f4, 1)));
  const end = Math.max(34, barX(stepCount));
  rrect(c, 20, top, end - 20, 12, 6, lin(20, 0, end, 0, [rgba(ORANGE_LIGHT, 1), rgba(ORANGE, 1)], null));
  rrect(c, 24, top + 2, Math.max(0, end - 30), 3, 1.5, solid(rgba(0xffffff, 0.35)));
  for (let i = 0; i < 4; i++) {
    const x = TICK_X[i];
    const pop = tickPop(i, t);
    if (pop > 0.02) {
      const r = 3.5 + 6 * pop;
      ellipse(c, x, cy + 1.5, 2 * r + 2, 2 * r + 2, solid(rgba(0xc9471a, 0.25)));
      ellipse(c, x, cy, 2 * r + 4, 2 * r + 4, solid(rgba(0xffffff, 1)));
      ellipse(c, x, cy, 2 * r, 2 * r, lin(x, cy - r, x, cy + r, [rgba(ORANGE_LIGHT, 1), rgba(ORANGE, 1)], null));
      const k = r / 9.5;
      const check = Skia.PathBuilder.Make();
      check.moveTo(x - 4 * k, cy + 0.2 * k).lineTo(x - 1 * k, cy + 3.2 * k).lineTo(x + 4.5 * k, cy - 3 * k);
      c.drawPath(check.detach(), line(rgba(0xffffff, 1), 2.2 * k));
    } else {
      ellipse(c, x, cy, 6, 6, solid(rgba(0xffffff, 1)));
    }
  }
  ellipse(c, end - 6, cy, 6, 6, solid(rgba(0xffffff, 1)));
}

// MARK: Celebration

const CONFETTI_COLORS = [0xff6a1f, 0xffc14d, 0x7cc95a, 0x5db4f2, 0xff8fb1];

export function drawConfetti(c: SkCanvas, t: number, barCentre: number) {
  'worklet';
  const tau = t - CELEBRATE;
  if (tau < 0 || tau > 1.3) return;
  const x0 = TICK_X[0];
  const alpha = 1 - smooth(0.8, 1.3, tau);
  for (let j = 0; j < 24; j++) {
    const h1 = hash(j + 1);
    const h2 = hash(j + 31.7);
    const h3 = hash(j + 77.1);
    const a = -Math.PI / 2 + (h1 - 0.5) * 2.0;
    const v = 170 + 150 * h2;
    const x = Math.cos(a) * v * tau;
    const y = Math.sin(a) * v * tau + 260 * tau * tau;
    const angle = (h3 - 0.5) * 14 * tau + h1 * 6;
    c.save();
    c.translate(x0 + x, barCentre + y);
    c.rotate((angle * 180) / Math.PI, 0, 0);
    const paint = solid(rgba(CONFETTI_COLORS[j % 5], alpha));
    if (j % 3 === 0) ellipse(c, 0, 0, 6, 6, paint);
    else rrect(c, -4, -2, 8, 4, 1, paint);
    c.restore();
  }
}

// MARK: Icons (static, drawn once into pictures)

export function drawCoin(c: SkCanvas, cx: number, cy: number, r: number, mixF: number) {
  'worklet';
  ellipse(c, cx, cy, 2 * r, 2 * r, lin(cx, cy - r, cx, cy + r, [mix(0xd9dee4, 0xffd76a, mixF, 1), mix(0xbec6cf, 0xf0a12e, mixF, 1)], null));
  ellipse(c, cx, cy, 1.25 * r, 1.25 * r, line(mix(0xeef1f4, 0xffeba8, mixF, 1), Math.max(1, r * 0.22)));
}

/** The "+10 coins" bubble above the 1k tick, without its text (the text is a native label on top). */
export function drawBubble(c: SkCanvas, t: number, barCentre: number) {
  'worklet';
  const b = bubble(t);
  if (b.alpha <= 0.01) return;
  const layer = Skia.Paint();
  layer.setAlphaf(b.alpha);
  c.saveLayer(layer);
  c.translate(TICK_X[0] + 22, barCentre + b.y);
  c.scale(b.scale, b.scale);
  rrect(c, -42, -11, 84, 26, 13, solid(rgba(0xc9471a, 0.25)));
  rrect(c, -42, -13, 84, 26, 13, lin(0, -13, 0, 13, [rgba(ORANGE_LIGHT, 1), rgba(ORANGE, 1)], null));
  const tail = Skia.PathBuilder.Make();
  tail.moveTo(-28, 12).lineTo(-22, 19).lineTo(-16, 12).close();
  c.drawPath(tail.detach(), solid(rgba(ORANGE, 1)));
  drawCoin(c, -27, 0, 7, 1);
  c.restore();
}

export function drawShoe(c: SkCanvas) {
  softShadow(c, 18, 25, 32, 5, 0.25, 0xc9471a);
  const upper = Skia.PathBuilder.Make();
  upper.moveTo(3, 19).cubicTo(2, 10, 7, 5, 13, 5).lineTo(18, 6).cubicTo(24, 10, 33, 11, 33, 17).lineTo(33, 19).close();
  c.drawPath(upper.detach(), lin(0, 5, 0, 19, [rgba(0xffb067, 1), rgba(0xff6a1f, 1)], null));
  ellipse(c, 8, 8, 8, 5, solid(rgba(0xd9531a, 1)));
  const laces = Skia.PathBuilder.Make();
  laces.moveTo(15, 8.5).lineTo(18.5, 11.5).moveTo(18.5, 7.5).lineTo(22, 10.5);
  c.drawPath(laces.detach(), line(rgba(0xffffff, 1), 1.8));
  rrect(c, 1.5, 17.5, 33, 6.5, 3.2, lin(0, 17.5, 0, 24, [rgba(0xffffff, 1), rgba(0xf0d8c4, 1)], null));
}

export function drawCalendar(c: SkCanvas) {
  softShadow(c, 22, 41, 34, 5, 0.18, 0x3a4a5c);
  rrect(c, 5, 8, 34, 31, 8, lin(0, 8, 0, 39, [rgba(0xffffff, 1), rgba(0xe5ebf2, 1)], null));
  c.save();
  c.clipRRect(Skia.RRectXY(Skia.XYWHRect(5, 8, 34, 31), 8, 8), ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(5, 8, 34, 10), lin(0, 8, 0, 18, [rgba(0xff8c6b, 1), rgba(0xf25f3c, 1)], null));
  c.restore();
  rrect(c, 13, 4, 4, 9, 2, solid(rgba(0x5a6676, 1)));
  rrect(c, 27, 4, 4, 9, 2, solid(rgba(0x5a6676, 1)));
  const check = Skia.PathBuilder.Make();
  check.moveTo(15, 28).lineTo(20, 33).lineTo(29, 23);
  c.drawPath(check.detach(), line(rgba(0x37b26c, 1), 3.6));
}

export function drawGift(c: SkCanvas) {
  softShadow(c, 22, 41, 36, 5, 0.18, 0x7a2e1a);
  rrect(c, 7, 20, 30, 20, 4, lin(0, 20, 0, 40, [rgba(0xff8a65, 1), rgba(0xe8552f, 1)], null));
  rrect(c, 4, 13, 36, 10, 3.5, lin(0, 13, 0, 23, [rgba(0xffa486, 1), rgba(0xf46c47, 1)], null));
  c.drawRect(Skia.XYWHRect(19, 13, 6, 27), lin(0, 13, 0, 40, [rgba(0xffe08a, 1), rgba(0xf5b93a, 1)], null));
  for (let i = 0; i < 2; i++) {
    c.save();
    c.translate(i === 0 ? 15 : 29, 9);
    c.rotate(i === 0 ? -28 : 28, 0, 0);
    ellipse(c, 0, 0, 12, 8, solid(rgba(0xffd15c, 1)));
    ellipse(c, 0, 0, 5, 3, solid(rgba(0xe9a72a, 1)));
    c.restore();
  }
  ellipse(c, 22, 11, 6, 6, solid(rgba(0xf5b93a, 1)));
  rrect(c, 9, 25, 3, 10, 1.5, solid(rgba(0xffffff, 0.35)));
}

export function drawPlane(c: SkCanvas) {
  softShadow(c, 24, 41, 30, 5, 0.16, 0x1e4f86);
  const wing = Skia.PathBuilder.Make();
  wing.moveTo(40, 6).lineTo(5, 21).lineTo(17, 25).close();
  c.drawPath(wing.detach(), lin(5, 6, 40, 25, [rgba(0x8fd0fa, 1), rgba(0x4aa3ec, 1)], null));
  const lower = Skia.PathBuilder.Make();
  lower.moveTo(40, 6).lineTo(17, 25).lineTo(29, 36).close();
  c.drawPath(lower.detach(), lin(17, 6, 40, 36, [rgba(0x5db4f2, 1), rgba(0x2f82d6, 1)], null));
  const fold = Skia.PathBuilder.Make();
  fold.moveTo(40, 6).lineTo(17, 25).lineTo(19, 34).close();
  c.drawPath(fold.detach(), solid(rgba(0x2369b8, 1)));
  const dash = Skia.PathBuilder.Make();
  dash.moveTo(3, 31).lineTo(10, 29).moveTo(6, 38).lineTo(15, 35);
  c.drawPath(dash.detach(), line(rgba(0x9ccbf2, 1), 2.2));
}

export function drawChevron(c: SkCanvas) {
  const p = Skia.PathBuilder.Make();
  p.moveTo(9, 2).lineTo(2, 9).lineTo(9, 16);
  c.drawPath(p.detach(), line(rgba(INK, 1), 2.4));
}

export function drawShare(c: SkCanvas) {
  const p = Skia.PathBuilder.Make();
  p.moveTo(7, 8).lineTo(4, 8).lineTo(4, 20).lineTo(18, 20).lineTo(18, 8).lineTo(15, 8);
  p.moveTo(11, 2).lineTo(11, 13);
  p.moveTo(7, 5.5).lineTo(11, 1.5).lineTo(15, 5.5);
  c.drawPath(p.detach(), line(rgba(INK, 1), 2));
}

export function drawGear(c: SkCanvas) {
  const p = Skia.PathBuilder.Make();
  for (let i = 0; i < 8; i++) {
    const a = (i * Math.PI) / 4;
    const radii = [7.6, 10, 10, 7.6];
    const angles = [a - 0.25, a - 0.15, a + 0.15, a + 0.25];
    for (let j = 0; j < 4; j++) {
      const px = 11 + radii[j] * Math.cos(angles[j]);
      const py = 11 + radii[j] * Math.sin(angles[j]);
      if (i === 0 && j === 0) p.moveTo(px, py);
      else p.lineTo(px, py);
    }
  }
  p.close();
  c.drawPath(p.detach(), line(rgba(INK, 1), 1.9));
  ellipse(c, 11, 11, 6, 6, line(rgba(INK, 1), 1.9));
}
