import {
  BlendMode,
  BlurStyle,
  ClipOp,
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
  type SkPaint,
  type SkPath,
} from '@shopify/react-native-skia';

import { BAG_H, BELT_TOP, BELT_Y, LOOP, PITCH, SKIRT_BOTTOM, SKIRT_TOP, WIN_B, WIN_L, WIN_R, WIN_T, clamp, mod } from './xrayMotion';

/*
 Everything of X-Ray Bag Scan drawn in code with Skia, in the 393-wide design space (scene y measured
 for a 59-unit safe-area top; `sy` shifts it): the terminal wall, the belt, the scanner gantry, the
 x-ray window, detection boxes, the card's status icon and bar. Helpers sit above their callers.
 */

export const SUITCASE = require('./assets/suitcase.webp');
export const SUITCASE_XRAY = require('./assets/suitcase-xray.webp');

export const INK = 0x2b1a0e;
export const BROWN = 0x5a3418;
export const MUTED = 0x8a6a50;
export const CARD = 0xfff7ec;
export const TRACK = 0xf0e2cf;
export const ACCENT = 0xf08a2c;
export const GREEN = 0x3dbe6b;
export const CYAN = 0x6fe6ff;
export const BOX_GREEN = 0x7cf2a0;
export const FLOOR = 0x857762;

export const IMAGE_W = (BAG_H * 329) / 600;

export type Fonts = { sign: SkFont; poster: SkFont; label: SkFont };

/** Detected items: box as fractions of the bag image, and where the label tab sits. */
export const ITEMS = [
  { label: 'Liquids 100 ml', x0: 0.12, y0: 0.29, x1: 0.5, y1: 0.47, below: false, right: false },
  { label: 'Headphones', x0: 0.53, y0: 0.31, x1: 0.91, y1: 0.5, below: true, right: true },
  { label: 'Laptop', x0: 0.1, y0: 0.5, x1: 0.89, y1: 0.7, below: true, right: false },
];

/** Item centres relative to the bag's centre, for the scrub reveal. */
export const ITEM_OFFSETS = ITEMS.map((it) => ((it.x0 + it.x1) / 2 - 0.5) * IMAGE_W);

export function rgba(hex: number, a = 1) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255, Math.min(1, Math.max(0, a)));
}

export function mixHex(a: number, b: number, f: number) {
  'worklet';
  const g = clamp(f);
  const r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * g);
  const gr = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * g);
  const bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * g);
  return (r << 16) | (gr << 8) | bl;
}

export function hexColor(hex: number, a = 1) {
  'worklet';
  return `rgba(${(hex >> 16) & 255},${(hex >> 8) & 255},${hex & 255},${a})`;
}

function solid(hex: number, a = 1): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(rgba(hex, a));
  return p;
}

function line(hex: number, a: number, width: number): SkPaint {
  'worklet';
  const p = solid(hex, a);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

function vertical(y0: number, y1: number, colors: Float32Array[]): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: y0 }, { x: 0, y: y1 }, colors, null, TileMode.Clamp));
  return p;
}

function horizontal(x0: number, x1: number, colors: Float32Array[]): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: 0 }, { x: x1, y: 0 }, colors, null, TileMode.Clamp));
  return p;
}

function radial(cx: number, cy: number, r: number, colors: Float32Array[], stops: number[] | null = null): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, r, colors, stops, TileMode.Clamp));
  return p;
}

function rect(c: SkCanvas, x: number, y: number, w: number, h: number, p: SkPaint) {
  'worklet';
  c.drawRect(Skia.XYWHRect(x, y, w, h), p);
}

function round(c: SkCanvas, x: number, y: number, w: number, h: number, r: number, p: SkPaint) {
  'worklet';
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r), p);
}

function textWidth(font: SkFont, text: string) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let w = 0;
  for (let i = 0; i < widths.length; i++) w += widths[i];
  return w;
}

function brackets(x: number, y: number, w: number, h: number, l: number): SkPath {
  'worklet';
  const p = Skia.PathBuilder.Make();
  const x1 = x + w;
  const y1 = y + h;
  p.moveTo(x, y + l); p.lineTo(x, y); p.lineTo(x + l, y);
  p.moveTo(x1 - l, y); p.lineTo(x1, y); p.lineTo(x1, y + l);
  p.moveTo(x1, y1 - l); p.lineTo(x1, y1); p.lineTo(x1 - l, y1);
  p.moveTo(x + l, y1); p.lineTo(x, y1); p.lineTo(x, y1 - l);
  return p.detach();
}

// MARK: Wall and floor

function drawSign(c: SkCanvas, sy: number, fonts: Fonts) {
  'worklet';
  const x = 16;
  const y = 236 + sy;
  const w = 92;
  const h = 26;
  round(c, x, y + 3, w, h, 5, solid(0x1e2b1c, 0.25));
  round(c, x, y, w, h, 5, vertical(y, y + h, [rgba(0xfade52), rgba(0xefc92f)]));
  c.drawText('Bag drop', x + 9, y + h / 2 + 4.2, solid(INK), fonts.sign);
  const mx = x + w;
  const my = y + h / 2;
  const p = Skia.PathBuilder.Make();
  p.moveTo(mx - 22, my); p.lineTo(mx - 10, my);
  p.moveTo(mx - 15, my - 4.5); p.lineTo(mx - 10, my); p.lineTo(mx - 15, my + 4.5);
  c.drawPath(p.build(), line(INK, 1, 2));
}

function drawPoster(c: SkCanvas, sy: number, fonts: Fonts) {
  'worklet';
  c.save();
  c.translate(338, 300 + sy);
  c.rotate(5, 0, 0);
  round(c, -30, -38, 64, 84, 4, solid(0x1e2b1c, 0.28));
  round(c, -32, -42, 64, 84, 4, vertical(-42, 42, [rgba(0xf47a43), rgba(0xe45a2e)]));
  const lines = ['Pack', 'light,', 'fly', 'bright'];
  const ink = solid(0xfff1de);
  for (let i = 0; i < lines.length; i++) c.drawText(lines[i], -24, -42 + 7 + 12.5 + i * 15.5, ink, fonts.poster);
  c.drawCircle(0, -39, 3, solid(0xe8e2d6));
  c.restore();
}

export function drawBackground(c: SkCanvas, width: number, height: number, sy: number, fonts: Fonts) {
  'worklet';
  const wallBottom = 204 + sy;
  rect(c, 0, 0, width, wallBottom, vertical(0, wallBottom, [rgba(0xf6a54e), rgba(0xef9238), rgba(0xe6812b)]));
  for (let i = 1; i <= 2; i++) {
    const x = 131 * i;
    rect(c, x, 0, 1, wallBottom, solid(0x000000, 0.07));
    rect(c, x + 1, 0, 1, wallBottom, solid(0xffffff, 0.1));
  }
  rect(c, 0, 0, width, wallBottom, radial(70, 100 + sy, 300, [rgba(0xffffff, 0.2), rgba(0xffffff, 0)]));

  const tileTop = wallBottom;
  const tileBottom = BELT_TOP + sy;
  rect(c, 0, tileTop, width, tileBottom - tileTop, vertical(tileTop, tileBottom, [rgba(0x5e7a56), rgba(0x4f6a4a), rgba(0x425a3f)]));
  const dark = solid(0x1e2b1c, 0.22);
  const light = solid(0xffffff, 0.05);
  for (let y = tileTop + 22; y < tileBottom; y += 22) {
    rect(c, 0, y, width, 1, dark);
    rect(c, 0, y + 1, width, 1, light);
  }
  for (let x = 9.5; x < width; x += 22) {
    rect(c, x, tileTop, 1, tileBottom - tileTop, dark);
    rect(c, x + 1, tileTop, 1, tileBottom - tileTop, light);
  }
  rect(c, 0, tileTop, width, tileBottom - tileTop, radial(196.5, 420 + sy, 230, [rgba(0xfff3c8, 0.16), rgba(0xfff3c8, 0)]));
  rect(c, 0, tileTop - 2, width, 10, vertical(tileTop - 2, tileTop + 8, [rgba(0xf7b15f), rgba(0xd8732a)]));
  rect(c, 0, tileTop + 8, width, 6, vertical(tileTop + 8, tileTop + 14, [rgba(0x000000, 0.22), rgba(0x000000, 0)]));

  const floorTop = SKIRT_BOTTOM + sy;
  rect(c, 0, floorTop, width, height - floorTop, vertical(floorTop, height, [rgba(0x9c8d78), rgba(0x857762)]));

  drawSign(c, sy, fonts);
  drawPoster(c, sy, fonts);
}

// MARK: Belt

export function drawBelt(c: SkCanvas, width: number, d: number, sy: number) {
  'worklet';
  const top = BELT_TOP + sy;
  const bottom = SKIRT_TOP + sy;
  rect(c, 0, top, width, bottom - top, vertical(top, bottom, [rgba(0x4a484f), rgba(0x3a3940), rgba(0x2c2b31)]));
  const seam = solid(0x1b1a1f);
  const shine = solid(0x6a6870, 0.7);
  for (let x = mod(d, PITCH) - PITCH; x < width + PITCH; x += PITCH) {
    rect(c, x, top + 1, 1.6, bottom - top - 1, seam);
    rect(c, x + 1.6, top + 1, 1, bottom - top - 1, shine);
  }
  rect(c, 0, top, width, 1.2, solid(0xffffff, 0.22));

  const st = SKIRT_TOP + sy;
  const sb = SKIRT_BOTTOM + sy;
  rect(c, 0, st, width, sb - st, vertical(st, sb, [rgba(0xd6d9de), rgba(0xbcc0c7), rgba(0x979ca5)]));
  rect(c, 0, st, width, 1.2, solid(0xffffff, 0.6));
  rect(c, 0, st + 9, width, 1, solid(0x7c818a, 0.5));
  const bolt = solid(0x80858e);
  for (let bx = 24.5; bx < width; bx += 49) c.drawCircle(bx, st + 18, 2, bolt);
  rect(c, 0, sb, width, 12, vertical(sb, sb + 12, [rgba(0x2a2218, 0.3), rgba(0x2a2218, 0)]));
}

export function drawBeltXray(c: SkCanvas, d: number, sy: number) {
  'worklet';
  const top = BELT_TOP + sy;
  const bottom = SKIRT_TOP + sy;
  const w = WIN_R - WIN_L;
  rect(c, WIN_L, top, w, bottom - top, solid(CYAN, 0.08));
  rect(c, WIN_L, top, w, 1.2, solid(CYAN, 0.65));
  rect(c, WIN_L, bottom - 1, w, 1, solid(CYAN, 0.4));
  const seam = solid(CYAN, 0.32);
  for (let x = mod(d, PITCH) - PITCH; x < WIN_R + PITCH; x += PITCH) rect(c, x, top + 2, 1, bottom - top - 3, seam);
}

// MARK: Bags

export function drawBagShadow(c: SkCanvas, x: number, sy: number) {
  'worklet';
  const w = IMAGE_W * 1.08;
  c.save();
  c.translate(x, BELT_Y + sy + 1);
  c.scale(1, 0.12);
  c.drawCircle(0, 0, w / 2, radial(0, 0, w / 2, [rgba(0x0e0d10, 0.55), rgba(0x0e0d10, 0.3), rgba(0x0e0d10, 0)], [0, 0.6, 1]));
  c.restore();
}

export function drawBag(c: SkCanvas, image: SkImage | null, x: number, lean: number, sy: number, glow = false) {
  'worklet';
  if (!image) return;
  c.save();
  c.translate(x, BELT_Y + sy);
  c.rotate(lean, 0, 0);
  const p = Skia.Paint();
  p.setAntiAlias(true);
  if (glow) {
    p.setAlphaf(0.35);
    p.setBlendMode(BlendMode.Plus);
    p.setImageFilter(Skia.ImageFilter.MakeBlur(6, 6, TileMode.Decal, null));
  }
  c.drawImageRectOptions(
    image,
    Skia.XYWHRect(0, 0, image.width(), image.height()),
    Skia.XYWHRect(-IMAGE_W / 2, -BAG_H, IMAGE_W, BAG_H),
    FilterMode.Linear,
    MipmapMode.Linear,
    p,
  );
  c.restore();
}

export function drawBoxes(c: SkCanvas, x: number, lean: number, sy: number, bx: number[], green: number, fonts: Fonts) {
  'worklet';
  c.save();
  c.translate(x, BELT_Y + sy);
  c.rotate(lean, 0, 0);
  c.translate(-IMAGE_W / 2, -BAG_H);
  const hue = mixHex(CYAN, BOX_GREEN, green);
  for (let i = 0; i < ITEMS.length; i++) {
    const s = bx[2 * i];
    const a = bx[2 * i + 1];
    if (a <= 0.002) continue;
    const it = ITEMS[i];
    const w = (it.x1 - it.x0) * IMAGE_W;
    const h = (it.y1 - it.y0) * BAG_H;
    c.save();
    c.translate(it.x0 * IMAGE_W + w / 2, it.y0 * BAG_H + h / 2);
    c.scale(s, s);
    const l = -w / 2;
    const t = -h / 2;
    round(c, l, t, w, h, 3, solid(hue, 0.1 * a));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(l, t, w, h), 3, 3), line(hue, 0.35 * a, 0.8));
    c.drawPath(brackets(l, t, w, h, 6), line(hue, a, 1.8));
    const tw = textWidth(fonts.label, it.label) + 10;
    const th = 14;
    const tx = it.right ? l + w - tw : l;
    const ty = it.below ? t + h + 1 : t - th - 1;
    round(c, tx, ty, tw, th, 4, solid(hue, a));
    c.drawText(it.label, tx + 5, ty + th / 2 + 3.4, solid(0x061226, a), fonts.label);
    c.restore();
  }
  c.restore();
}

// MARK: Window

export function windowRect(sy: number) {
  'worklet';
  return Skia.XYWHRect(WIN_L, WIN_T + sy, WIN_R - WIN_L, WIN_B - WIN_T);
}

export function clipWindow(c: SkCanvas, sy: number, inside: boolean) {
  'worklet';
  c.clipRect(windowRect(sy), inside ? ClipOp.Intersect : ClipOp.Difference, true);
}

export function drawWindowBack(c: SkCanvas, sy: number) {
  'worklet';
  const top = WIN_T + sy;
  const bottom = WIN_B + sy;
  const w = WIN_R - WIN_L;
  rect(c, WIN_L, top, w, bottom - top, vertical(top, bottom, [rgba(0x0e2148), rgba(0x0a1a38), rgba(0x061229)]));
  const minor = solid(CYAN, 0.07);
  const major = solid(CYAN, 0.15);
  let i = 0;
  for (let x = WIN_L + 6; x < WIN_R; x += 14, i++) rect(c, x, top, 0.7, bottom - top, i % 4 === 0 ? major : minor);
  i = 0;
  for (let y = top + 6; y < bottom; y += 14, i++) rect(c, WIN_L, y, w, 0.7, i % 4 === 0 ? major : minor);
  c.drawPath(brackets(WIN_L + 6, top + 6, w - 12, bottom - top - 12, 9), line(CYAN, 0.45, 1.2));
}

export function drawWindowFront(c: SkCanvas, sy: number, sweep: number, sweepA: number) {
  'worklet';
  const top = WIN_T + sy;
  const bottom = WIN_B + sy;
  const w = WIN_R - WIN_L;
  const lines = solid(0x000814, 0.16);
  for (let y = top + 1; y < bottom; y += 3) rect(c, WIN_L, y, w, 1, lines);
  if (sweepA > 0.001) {
    const s = sweep + sy;
    rect(c, WIN_L, s - 26, w, 26, vertical(s - 26, s, [rgba(CYAN, 0), rgba(CYAN, 0.22 * sweepA)]));
    rect(c, WIN_L, s - 0.8, w, 1.6, solid(0xd8fbff, 0.95 * sweepA));
  }
  const r0 = w * 0.45;
  const r1 = (bottom - top) * 0.75;
  rect(c, WIN_L, top, w, bottom - top, radial(WIN_L + w / 2, (top + bottom) / 2, r1, [rgba(0, 0), rgba(0, 0), rgba(0x000814, 0.45)], [0, r0 / r1, 1]));
  const p = Skia.PathBuilder.Make();
  p.moveTo(WIN_L + 30, top); p.lineTo(WIN_L + 70, top); p.lineTo(WIN_L, top + 120); p.lineTo(WIN_L, top + 60); p.close();
  c.drawPath(p.build(), solid(0xffffff, 0.05));
}

export function drawCurtains(c: SkCanvas, sy: number, left: number, right: number) {
  'worklet';
  const top = WIN_T + sy;
  const bottom = WIN_B + sy;
  const edges = [
    [WIN_L, left, 1],
    [WIN_R, right, -1],
  ];
  for (let i = 0; i < 2; i++) {
    const x = edges[i][0];
    const g = edges[i][1];
    const dir = edges[i][2];
    rect(c, dir > 0 ? x : x - 22, top, 22, bottom - top, horizontal(x, x + 22 * dir, [rgba(CYAN, (0.22 * (g - 0.35)) / 0.65), rgba(CYAN, 0)]));
    const glow = solid(CYAN, 0.55 * g);
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
    rect(c, x - 4, top, 8, bottom - top, glow);
    rect(c, x - 1, top, 2, bottom - top, vertical(top, bottom, [rgba(0xcff8ff, 0.25 + 0.4 * g), rgba(0xcff8ff, 0.5 + 0.5 * g), rgba(0xcff8ff, 0.25 + 0.4 * g)]));
  }
}

// MARK: Gantry

function drawBeacon(c: SkCanvas, sy: number, glow: number) {
  'worklet';
  const cx = 196.5;
  const base = 292 + sy;
  if (glow > 0.001) c.drawCircle(cx, base - 12, 46, radial(cx, base - 12, 46, [rgba(0xffc25a, 0.55 * glow), rgba(0xffb23e, 0)]));
  round(c, cx - 12, base - 7, 24, 8, 2, solid(0x585d66));
  const dome = Skia.PathBuilder.Make();
  dome.addRRect({
    rect: Skia.XYWHRect(cx - 8.5, base - 27, 17, 21),
    topLeft: { x: 8.5, y: 8.5 },
    topRight: { x: 8.5, y: 8.5 },
    bottomRight: { x: 1, y: 1 },
    bottomLeft: { x: 1, y: 1 },
  });
  c.drawPath(dome.build(), vertical(base - 27, base - 6, [rgba(mixHex(0xe59a4c, 0xfff0b8, glow)), rgba(mixHex(0xc8682a, 0xffb23e, glow))]));
  const cage = Skia.PathBuilder.Make();
  const dxs = [-4.5, 0, 4.5];
  for (let i = 0; i < 3; i++) {
    cage.moveTo(cx + dxs[i], base - 27 + (dxs[i] === 0 ? 0 : 2));
    cage.lineTo(cx + dxs[i], base - 6);
  }
  cage.moveTo(cx - 8.5, base - 18); cage.lineTo(cx + 8.5, base - 18);
  const wire = line(0x4a4f58, 1, 1.3);
  wire.setStrokeCap(StrokeCap.Butt);
  c.drawPath(cage.build(), wire);
  c.drawPath(dome.build(), wire);
}

export function drawGantry(c: SkCanvas, sy: number, glow: number, scan: number, done: number, t: number) {
  'worklet';
  const beamTop = 292 + sy;
  const shadow = solid(0x1e2b1c, 0.3);
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8, true));
  round(c, 108, beamTop + 6, 186, 40, 10, shadow);
  const shadow2 = solid(0x1e2b1c, 0.22);
  shadow2.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8, true));
  rect(c, 116, beamTop + 40, 14, 250, shadow2);
  rect(c, 270, beamTop + 40, 14, 250, shadow2);

  for (let i = 0; i < 2; i++) {
    const px = i === 0 ? 112.5 : 266.5;
    round(c, px, beamTop + 20, 14, 586 - 292 - 20, 3, horizontal(px, px + 14, [rgba(0xa4a9b1), rgba(0xdcdfe3), rgba(0xc2c6cc), rgba(0x989da6)]));
    rect(c, px + 2, SKIRT_TOP + sy + 6, 10, 3, solid(0xe9883a));
  }
  const top = WIN_T + sy;
  const bottom = WIN_B + sy;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(WIN_L - 1.5, top - 1.5, WIN_R - WIN_L + 3, bottom - top + 3), 3, 3), line(0x7f858e, 1, 3));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(WIN_L - 3.2, top - 3.2, WIN_R - WIN_L + 6.4, bottom - top + 6.4), 4, 4), line(0xffffff, 0.25, 0.8));

  round(c, 104.5, beamTop, 184, 40, 10, vertical(beamTop, beamTop + 40, [rgba(0xdde0e4), rgba(0xc5c9cf), rgba(0xa6abb3)]));
  round(c, 104.5, beamTop + 35, 184, 5, 2.5, solid(0xe9883a));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(105, beamTop + 0.5, 183, 39), 10, 10), line(0xffffff, 0.45, 1));
  round(c, 150, beamTop + 8, 93, 20, 6, solid(0x1d2230));
  for (let i = 0; i < 3; i++) {
    const cx = 176.5 + 20 * i;
    const cy = beamTop + 18;
    const chase = 0.5 + 0.5 * Math.sin(2 * Math.PI * ((6 * t) / LOOP - i / 3));
    const on = Math.max(scan * (0.35 + 0.65 * chase), done);
    const hue = mixHex(0xffb23e, 0x5be38a, done);
    const g = solid(hue, 0.7 * on);
    g.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
    c.drawCircle(cx, cy, 6, g);
    c.drawCircle(cx, cy, 3.5, solid(0x3a4152));
    c.drawCircle(cx, cy, 3.5, solid(hue, on));
  }
  round(c, 118, beamTop + 14, 22, 8, 2, solid(0xe9883a));
  round(c, 253, beamTop + 14, 22, 8, 2, solid(0xe9883a));
  drawBeacon(c, sy, glow);
}

// MARK: Card pieces (drawn on the scene canvas at the card's place)

export function drawCardBack(c: SkCanvas, top: number) {
  'worklet';
  const s1 = solid(INK, 0.16);
  s1.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 15, true));
  round(c, 16, top + 10, 361, 210, 28, s1);
  const s2 = solid(INK, 0.08);
  s2.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.5, true));
  round(c, 16, top + 1, 361, 210, 28, s2);
  round(c, 16, top, 361, 210, 28, solid(CARD));
}

function drawSuitcaseGlyph(c: SkCanvas, cx: number, cy: number, a: number) {
  'worklet';
  const p = line(0xfff3e3, a, 2.2);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - 9, cy - 6, 18, 15), 3.5, 3.5), p);
  const h = Skia.PathBuilder.Make();
  h.moveTo(cx - 4, cy - 6); h.lineTo(cx - 4, cy - 10); h.lineTo(cx + 4, cy - 10); h.lineTo(cx + 4, cy - 6);
  c.drawPath(h.build(), p);
  const ribs = Skia.PathBuilder.Make();
  ribs.moveTo(cx - 3, cy - 2); ribs.lineTo(cx - 3, cy + 5);
  ribs.moveTo(cx + 3, cy - 2); ribs.lineTo(cx + 3, cy + 5);
  c.drawPath(ribs.build(), line(0xfff3e3, a, 1.6));
}

/** Status icon (46 units) with its top-left at (x, y). */
export function drawStatusIcon(c: SkCanvas, x: number, y: number, stg: number, ang: number, scale: number, strokeF: number) {
  'worklet';
  const s = mod(stg, 4);
  const ready = clamp(Math.max(1 - s, s - 3));
  const spinner = clamp(Math.min(s, 3 - s));
  const cx = x + 23;
  const cy = y + 23;
  c.drawCircle(cx, cy, 23, solid(ACCENT, 1 - clamp(scale)));
  if (ready > 0.001) drawSuitcaseGlyph(c, cx, cy, ready);
  if (spinner > 0.001) {
    c.drawCircle(cx, cy, 11, line(0xfff3e3, 0.3 * spinner, 3));
    const arc = Skia.PathBuilder.Make();
    arc.addArc(Skia.XYWHRect(cx - 11, cy - 11, 22, 22), (ang * 180) / Math.PI, (1.9 * 180) / Math.PI);
    c.drawPath(arc.build(), line(0xfff3e3, spinner, 3));
  }
  if (scale > 0.001) {
    c.save();
    c.translate(cx, cy);
    c.scale(scale, scale);
    c.drawCircle(0, 0, 23, solid(GREEN));
    // The check is two strokes; draw the first `strokeF` of their total length.
    const l1 = Math.hypot(6, 6);
    const l2 = Math.hypot(12.5, 12.5);
    const len = clamp(strokeF) * (l1 + l2);
    if (len > 0.01) {
      const tick = Skia.PathBuilder.Make();
      tick.moveTo(-9, 0.5);
      const a = Math.min(len, l1) / l1;
      tick.lineTo(-9 + 6 * a, 0.5 + 6 * a);
      if (len > l1) {
        const b = (len - l1) / l2;
        tick.lineTo(-3 + 12.5 * b, 6.5 - 12.5 * b);
      }
      c.drawPath(tick.detach(), line(0xffffff, 1, 3.4));
    }
    c.restore();
  }
}

/** Three-segment bar (321 × 8 units) with its top-left at (x, y). */
export function drawBar(c: SkCanvas, x: number, y: number, f: number, done: number) {
  'worklet';
  const gap = 4;
  const sw = (321 - 2 * gap) / 3;
  const hue = solid(mixHex(ACCENT, GREEN, done));
  for (let i = 0; i < 3; i++) {
    const sx = x + i * (sw + gap);
    const rr = Skia.RRectXY(Skia.XYWHRect(sx, y, sw, 8), 4, 4);
    c.drawRRect(rr, solid(TRACK));
    const part = clamp((f - i / 3) * 3);
    if (part > 0.001) {
      c.save();
      c.clipRRect(rr, ClipOp.Intersect, true);
      rect(c, sx, y, sw * part, 8, hue);
      c.restore();
    }
  }
}

/** Circular arrow for "Scan again" (18 units). */
export function drawAgainGlyph(c: SkCanvas, tint: number) {
  'worklet';
  const p = line(tint, 1, 2.2);
  const arc = Skia.PathBuilder.Make();
  arc.addArc(Skia.XYWHRect(2.5, 2.5, 13, 13), -60, 290);
  c.drawPath(arc.build(), p);
  const tx = 9 + 6.5 * Math.cos(-Math.PI / 3);
  const ty = 9 + 6.5 * Math.sin(-Math.PI / 3);
  const head = Skia.PathBuilder.Make();
  head.moveTo(tx - 5, ty - 1.2); head.lineTo(tx, ty); head.lineTo(tx - 0.6, ty + 5);
  c.drawPath(head.build(), p);
}

/** The airline's bird mark (16 units). */
export function drawBird(c: SkCanvas) {
  'worklet';
  const p = Skia.PathBuilder.Make();
  p.moveTo(1, 9);
  p.quadTo(4, 4, 8, 7);
  p.quadTo(11, 2, 15, 3);
  p.quadTo(13, 9, 9, 11);
  p.quadTo(5, 12, 1, 9);
  p.close();
  c.drawPath(p.build(), solid(ACCENT));
}
