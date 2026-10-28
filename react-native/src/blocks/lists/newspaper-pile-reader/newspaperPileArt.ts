import {
  ClipOp,
  FontSlant,
  FontWeight,
  PaintStyle,
  Skia,
  TileMode,
  type SkCanvas,
  type SkColor,
  type SkImage,
  type SkParagraph,
  type SkPath,
  type SkTypefaceFontProvider,
} from '@shopify/react-native-skia';
import { Platform } from 'react-native';

import { hash, PAPER_H, PAPER_W, PAPERS, type Paper } from './newspaperPileModel';

/*
 The six folded papers of Newspaper Pile Reader (a port of NewspaperPileReaderArt.swift), drawn once
 into bitmaps (CRAFT §7) so a frame only moves images around: page edges, tone gradient, grain,
 masthead, rules, headline, columns of grey "text", a photo drawn in code with a halftone, the fold and
 a curled corner.
*/

/** The paper image is the 340 × 228 sheet with 6 pt around it for the page edges. */
export const PAD = 6;

export const BLACK = 'Unifraktur';
export const SERIF = 'DMSerif';
export const SYSTEM = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });

const INK = 0x1c1b19;
const RULE = 0x34322d;
const LINES = 0x9e9a8f;
const RED = 0xc23a33;
const BLUE = 0x2c5ba6;
const NAVY = 0x213a66;

export function hex(v: number, a: number): SkColor {
  'worklet';
  return Float32Array.of(((v >> 16) & 0xff) / 255, ((v >> 8) & 0xff) / 255, (v & 0xff) / 255, a);
}

/** The colour a little darker (k = 0.04 → 4 %). */
function darker(v: number, k: number): SkColor {
  return Float32Array.of(
    (((v >> 16) & 0xff) / 255) * (1 - k),
    (((v >> 8) & 0xff) / 255) * (1 - k),
    ((v & 0xff) / 255) * (1 - k),
    1,
  );
}

function fill(color: SkColor) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  return p;
}

function stroke(color: SkColor, width: number) {
  const p = fill(color);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  return p;
}

function rect(c: SkCanvas, x: number, y: number, w: number, h: number, color: SkColor) {
  c.drawRect(Skia.XYWHRect(x, y, w, h), fill(color));
}

function rrect(x: number, y: number, w: number, h: number, r: number): SkPath {
  return Skia.PathBuilder.Make().addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r)).detach();
}

function oval(x: number, y: number, w: number, h: number): SkPath {
  return Skia.PathBuilder.Make().addOval(Skia.XYWHRect(x, y, w, h)).detach();
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: SkColor[], pos: number[] | null) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient(Skia.Point(x0, y0), Skia.Point(x1, y1), colors, pos, TileMode.Clamp));
  return p;
}

function vgrad(c: SkCanvas, x: number, y: number, w: number, h: number, cols: number[]) {
  const colors = cols.map((v) => hex(v, 1));
  c.drawRect(Skia.XYWHRect(x, y, w, h), linear(x, y, x, y + h, colors, null));
}

// Text

export type TextOpts = {
  family: string;
  size: number;
  color: SkColor;
  weight?: number;
  italic?: boolean;
  tracking?: number;
};

export function para(fonts: SkTypefaceFontProvider, s: string, o: TextOpts, width: number): SkParagraph {
  const p = Skia.ParagraphBuilder.Make({}, fonts)
    .pushStyle({
      color: o.color,
      fontFamilies: [o.family],
      fontSize: o.size,
      letterSpacing: o.tracking ?? 0,
      fontStyle: { weight: (o.weight ?? 400) as FontWeight, slant: o.italic ? FontSlant.Italic : FontSlant.Upright },
    })
    .addText(s)
    .build();
  p.layout(width);
  return p;
}

/** One line at a point: anchor -1 leading, 0 centre, 1 trailing, centred vertically; shrunk to fit. */
function line(
  c: SkCanvas,
  fonts: SkTypefaceFontProvider,
  s: string,
  o: TextOpts,
  x: number,
  y: number,
  anchor: number,
  maxWidth: number,
) {
  const p = para(fonts, s, o, 10000);
  const w = p.getLongestLine();
  const h = p.getHeight();
  const k = w > maxWidth ? maxWidth / w : 1;
  c.save();
  c.translate(x, y);
  c.scale(k, k);
  const dx = anchor < 0 ? 0 : anchor > 0 ? -w : -w / 2;
  p.paint(c, dx, -h / 2);
  c.restore();
}

/** A wrapped serif headline from its top-left corner. */
function headline(c: SkCanvas, fonts: SkTypefaceFontProvider, s: string, x: number, y: number, w: number, size: number, col: number) {
  para(fonts, s, { family: SERIF, size, color: hex(col, 1) }, w).paint(c, x, y);
}

// Paper parts

function grain(c: SkCanvas, seed: number) {
  const dark = Skia.PathBuilder.Make();
  for (let k = 0; k < 900; k++) {
    const x = hash(seed + k * 1.31) * PAPER_W;
    const y = hash(seed + k * 2.17 + 0.5) * PAPER_H;
    const r = 0.35 + 0.3 * hash(seed + k * 0.77);
    dark.addCircle(x, y, r);
  }
  c.drawPath(dark.detach(), fill(Float32Array.of(0, 0, 0, 0.06)));
  const light = Skia.PathBuilder.Make();
  for (let k = 0; k < 300; k++) {
    const x = hash(seed + k * 3.11 + 9) * PAPER_W;
    const y = hash(seed + k * 1.93 + 4) * PAPER_H;
    light.addCircle(x, y, 0.5);
  }
  c.drawPath(light.detach(), fill(Float32Array.of(1, 1, 1, 0.4)));
}

/** Rows of grey "text", with a short last line and a gap at each paragraph end. */
function textLines(c: SkCanvas, x: number, y0: number, w: number, h: number, seed: number, tint: number, alpha: number) {
  const path = Skia.PathBuilder.Make();
  let y = y0 + 1;
  let k = 0;
  while (y + 1.6 <= y0 + h) {
    const hh = hash(seed + k);
    const end = hash(seed * 1.7 + k * 3.1) < 0.12;
    const len = end ? w * (0.3 + 0.4 * hh) : w * (0.86 + 0.14 * hh);
    path.addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, len, 1.6), 0.6, 0.6));
    y += end ? 8 : 5;
    k += 1;
  }
  c.drawPath(path.detach(), fill(hex(tint, alpha)));
}

function columns(c: SkCanvas, x0: number, y: number, w: number, h: number, n: number, seed: number) {
  const gut = 8;
  const cw = (w - gut * (n - 1)) / n;
  for (let j = 0; j < n; j++) {
    const x = x0 + j * (cw + gut);
    textLines(c, x, y, cw, h, seed + j * 31, LINES, 0.6);
    if (j > 0) rect(c, x - gut / 2 - 0.25, y, 0.5, h, hex(RULE, 0.35));
  }
}

function hrule(c: SkCanvas, y: number, x0: number, x1: number, wid: number, col: number) {
  rect(c, x0, y - wid / 2, x1 - x0, wid, hex(col, 1));
}

function dateline(c: SkCanvas, fonts: SkTypefaceFontProvider, left: string, right: string) {
  const o = { family: SYSTEM, size: 5.5, weight: 600, color: hex(INK, 0.75), tracking: 0.4 };
  line(c, fonts, left, o, 12, 8, -1, 1000);
  line(c, fonts, right, o, 328, 8, 1, 1000);
}

function infoRow(c: SkCanvas, fonts: SkTypefaceFontProvider, y: number, s: string) {
  line(c, fonts, s, { family: SYSTEM, size: 5, weight: 500, color: hex(INK, 0.7), tracking: 0.5 }, 170, y, 0, 1000);
}

// Photos (drawn in code, then printed: halftone, paper tint, hairline)

function portrait(c: SkCanvas, x: number, y: number, w: number, h: number) {
  vgrad(c, x, y, w, h, [0xd9a273, 0xc8704f, 0xa9483a]);
  const cx = x + w / 2;
  c.drawPath(oval(cx - w * 0.55, y + h * 0.68, w * 1.1, h * 0.6), fill(hex(0x24324f, 1)));
  const collar = Skia.PathBuilder.Make()
    .moveTo(cx - 9, y + h * 0.69)
    .lineTo(cx + 9, y + h * 0.69)
    .lineTo(cx, y + h * 0.82)
    .close()
    .detach();
  c.drawPath(collar, fill(hex(0xf2efe8, 1)));
  rect(c, cx - 6, y + h * 0.55, 12, h * 0.15, hex(0xd9a88c, 1));
  const hx = cx - 15;
  const hy = y + h * 0.24;
  c.drawPath(oval(hx, hy, 30, 37), fill(hex(0xe6bca0, 1)));
  const hair = Skia.PathBuilder.Make()
    .arcToOval(Skia.XYWHRect(cx - 16.5, hy + 14 - 16.5, 33, 33), 195, 150, true)
    .close()
    .detach();
  c.drawPath(hair, fill(hex(0xe9e4da, 1)));
  c.drawPath(oval(hx + 1, hy + 4, 28, 12), fill(hex(0xede8df, 1)));
}

function harbour(c: SkCanvas, x: number, y: number, w: number, h: number) {
  vgrad(c, x, y, w, h, [0xc4cdd2, 0xdddcd5]);
  const seaY = y + h * 0.62;
  vgrad(c, x, seaY, w, h * 0.38, [0x6e8591, 0x4d6270]);
  const fx = x + w * 0.22;
  const fy = seaY;
  const hull = Skia.PathBuilder.Make()
    .moveTo(fx, fy - 4)
    .lineTo(fx + 36, fy - 4)
    .lineTo(fx + 31, fy + 4)
    .lineTo(fx + 4, fy + 4)
    .close()
    .detach();
  c.drawPath(hull, fill(hex(0x2d3740, 1)));
  rect(c, fx + 7, fy - 11, 22, 7, hex(0xf2f0ea, 1));
  for (let k = 0; k < 4; k++) rect(c, fx + 9 + k * 5, fy - 9, 3, 2.5, hex(0x3e4b57, 1));
  rect(c, fx + 4, fy + 5, 30, 1, Float32Array.of(1, 1, 1, 0.35));
  rect(c, x, seaY - 3, w, 3, hex(0x8e9a9e, 1));
}

function market(c: SkCanvas, x: number, y: number, w: number, h: number) {
  vgrad(c, x, y, w, h, [0x1f2a44, 0x3b3552, 0x5a3f4f]);
  const y0 = y + 12;
  const wire = Skia.PathBuilder.Make()
    .moveTo(x, y0)
    .quadTo(x + w / 2, y0 + 26, x + w, y0)
    .detach();
  c.drawPath(wire, stroke(hex(0x111522, 0.8), 0.6));
  for (let k = 0; k < 11; k++) {
    const t = (k + 0.5) / 11;
    const lx = x + w * t;
    const ly = (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * (y0 + 26) + t * t * y0;
    c.drawPath(oval(lx - 4, ly - 3, 8, 8), fill(hex(0xf4c25b, 0.25)));
    c.drawPath(oval(lx - 1.6, ly - 0.6, 3.2, 3.2), fill(hex(0xffe3a1, 1)));
  }
  const cols = [0xc23a33, 0x2f8f86, 0xe2a23b];
  for (let k = 0; k < 3; k++) {
    const sx = x + 6 + (k * (w - 12)) / 3;
    const sw = (w - 12) / 3 - 4;
    const top = y + h * 0.5;
    const roof = Skia.PathBuilder.Make()
      .moveTo(sx - 2, top + 10)
      .lineTo(sx + sw / 2, top)
      .lineTo(sx + sw + 2, top + 10)
      .close()
      .detach();
    c.drawPath(roof, fill(hex(cols[k], 1)));
    rect(c, sx, top + 10, sw, y + h - top - 10, hex(0x2a2233, 1));
    rect(c, sx + 3, top + 14, sw - 6, 9, hex(0xf4c25b, 0.75));
  }
  for (let k = 0; k < 5; k++) {
    const px = x + 10 + k * 23;
    c.drawPath(oval(px, y + h - 18, 6, 6), fill(hex(0x14111a, 1)));
    c.drawPath(rrect(px - 2, y + h - 12, 10, 14, 4), fill(hex(0x14111a, 1)));
  }
}

function train(c: SkCanvas, x: number, y: number, w: number, h: number) {
  vgrad(c, x, y, w, h, [0xb9b8b3, 0xe2e1dc]);
  const horizon = y + h * 0.58;
  vgrad(c, x, horizon, w, y + h - horizon, [0x76746e, 0x4e4d49]);
  const hill = Skia.PathBuilder.Make()
    .moveTo(x, horizon)
    .quadTo(x + w * 0.3, horizon - 22, x + w, horizon)
    .close()
    .detach();
  c.drawPath(hill, fill(hex(0x8f8d87, 1)));
  const vx = x + w * 0.62;
  for (let s = -1; s <= 1; s += 2) {
    const rail = Skia.PathBuilder.Make()
      .moveTo(vx + s * 6, horizon + 4)
      .lineTo(vx + s * 46, y + h)
      .detach();
    c.drawPath(rail, stroke(hex(0xd7d5cf, 1), 1));
  }
  const tx = vx - 16;
  const ty = horizon - 26;
  c.drawPath(rrect(tx, ty, 32, 32, 5), fill(hex(0x3a3936, 1)));
  c.drawPath(rrect(tx + 4, ty + 5, 24, 10, 2), fill(hex(0xc9c8c2, 1)));
  c.drawPath(oval(tx + 5, ty + 32 - 9, 5, 5), fill(hex(0xf5f3ec, 1)));
  c.drawPath(oval(tx + 32 - 10, ty + 32 - 9, 5, 5), fill(hex(0xf5f3ec, 1)));
}

function sunset(c: SkCanvas, x: number, y: number, w: number, h: number) {
  vgrad(c, x, y, w, h, [0x8e5a8c, 0xe9785a, 0xf2a65a]);
  const sea = y + h * 0.6;
  const mx = x + w / 2;
  c.drawPath(oval(mx - 16, sea - 18, 32, 32), fill(hex(0xffe2a8, 1)));
  vgrad(c, x, sea, w, y + h - sea, [0x3f5f86, 0x2b4466]);
  for (let k = 0; k < 6; k++) {
    const sw = 26 - k * 3;
    c.drawPath(rrect(mx - sw / 2, sea + 3 + k * 5, sw, 1.6, 0.8), fill(hex(0xffd9a0, 0.8)));
  }
  for (let k = 0; k < 7; k++) rect(c, x + 8 + k * 9, sea + 6 + k * 2.5, 2, 14, hex(0x1e2433, 1));
}

function garden(c: SkCanvas, x: number, y: number, w: number, h: number) {
  vgrad(c, x, y, w, h, [0xdde6d2, 0xa9c092, 0x6f8e5e]);
  const mx = x + w / 2;
  const path = Skia.PathBuilder.Make()
    .moveTo(mx - 6, y + h * 0.55)
    .lineTo(mx + 6, y + h * 0.55)
    .lineTo(mx + 40, y + h)
    .lineTo(mx - 40, y + h)
    .close()
    .detach();
  c.drawPath(path, fill(hex(0xe6dcc6, 1)));
  const trees = [0.22, 0.8];
  for (let k = 0; k < 2; k++) {
    const tx = x + w * trees[k];
    rect(c, tx - 1.5, y + h * 0.4, 3, h * 0.45, hex(0x5a4636, 1));
    const blossoms = Skia.PathBuilder.Make();
    for (let j = 0; j < 26; j++) {
      const a = hash(k * 50 + j + 0.3) * 2 * Math.PI;
      const d = Math.sqrt(hash(k * 50 + j + 0.7)) * 22;
      const bx = tx + Math.cos(a) * d * 1.2;
      const by = y + h * 0.32 + Math.sin(a) * d * 0.7;
      blossoms.addOval(Skia.XYWHRect(bx - 3, by - 3, 6, 6));
    }
    c.drawPath(blossoms.detach(), fill(hex(0xe7a6b8, 1)));
  }
}

function photo(c: SkCanvas, kind: number, x: number, y: number, w: number, h: number) {
  c.save();
  c.clipRect(Skia.XYWHRect(x, y, w, h), ClipOp.Intersect, true);
  if (kind === 0) portrait(c, x, y, w, h);
  else if (kind === 1) harbour(c, x, y, w, h);
  else if (kind === 2) market(c, x, y, w, h);
  else if (kind === 3) train(c, x, y, w, h);
  else if (kind === 4) sunset(c, x, y, w, h);
  else garden(c, x, y, w, h);
  // Halftone dots and a paper tint so it reads as print.
  const dots = Skia.PathBuilder.Make();
  const pitch = 2.4;
  let row = 0;
  let dy = y + 0.6;
  while (dy < y + h) {
    let dx = x + (row % 2 === 0 ? 0.6 : 1.8);
    while (dx < x + w) {
      dots.addCircle(dx, dy, 0.42);
      dx += pitch;
    }
    dy += pitch;
    row += 1;
  }
  c.drawPath(dots.detach(), fill(Float32Array.of(0, 0, 0, 0.12)));
  rect(c, x, y, w, h, hex(0xf1eee5, 0.12));
  c.restore();
  c.drawRect(Skia.XYWHRect(x, y, w, h), stroke(hex(RULE, 0.3), 0.5));
}

// Styles

function tabloid(c: SkCanvas, f: SkTypefaceFontProvider, p: Paper) {
  line(c, f, p.masthead, { family: BLACK, size: 31, color: hex(INK, 1) }, 12, 27, -1, 236);
  rect(c, 12, 46, 236, 15, hex(RED, 1));
  line(c, f, 'WEEKEND PULLOUT · 24 PAGES', { family: SYSTEM, size: 7, weight: 700, color: hex(0xffffff, 1), tracking: 0.9 }, 130, 53.5, 0, 1000);
  photo(c, 0, 256, 10, 72, 98);
  line(c, f, p.headline, { family: SERIF, size: 34, color: hex(INK, 1) }, 12, 86, -1, 236);
  line(c, f, p.deck, { family: SERIF, size: 12.5, color: hex(RED, 1) }, 12, 114, -1, 1000);
  hrule(c, 126, 12, 328, 0.5, RULE);
  columns(c, 12, 132, 316, 74, 4, 11);
}

function quayside(c: SkCanvas, f: SkTypefaceFontProvider, p: Paper) {
  dateline(c, f, 'TUESDAY, JUNE 4', '$3.00');
  line(c, f, p.masthead, { family: SERIF, size: 25, color: hex(INK, 1), tracking: -0.2 }, 170, 30, 0, 312);
  hrule(c, 49.5, 12, 328, 1.2, RULE);
  hrule(c, 52, 12, 328, 0.5, RULE);
  infoRow(c, f, 57, 'VOL. CXII  ·  NO. 108  ·  LATE EDITION  ·  ★★★★');
  hrule(c, 62, 12, 328, 0.5, RULE);
  c.drawRect(Skia.XYWHRect(12, 68, 52, 56), stroke(hex(BLUE, 0.8), 0.6));
  line(c, f, "What's", { family: SERIF, size: 9, color: hex(BLUE, 1) }, 16, 76, -1, 1000);
  line(c, f, 'Inside', { family: SERIF, size: 9, color: hex(BLUE, 1) }, 16, 86, -1, 1000);
  textLines(c, 16, 94, 44, 28, 3, BLUE, 0.45);
  textLines(c, 12, 130, 52, 76, 5, LINES, 0.6);
  rect(c, 67.75, 68, 0.5, 138, hex(RULE, 0.35));
  headline(c, f, p.headline, 72, 64, 186, 17, INK);
  columns(c, 72, 112, 186, 94, 3, 17);
  rect(c, 261.75, 68, 0.5, 138, hex(RULE, 0.35));
  photo(c, 1, 266, 68, 62, 56);
  headline(c, f, p.deck, 266, 127, 62, 10.5, INK);
  textLines(c, 266, 158, 62, 48, 23, LINES, 0.6);
}

function larkmoor(c: SkCanvas, f: SkTypefaceFontProvider, p: Paper) {
  dateline(c, f, 'TUESDAY, JUNE 4', 'TWO DOLLARS');
  line(c, f, p.masthead, { family: BLACK, size: 30, color: hex(INK, 1) }, 170, 29, 0, 312);
  hrule(c, 49.5, 12, 328, 1.2, RULE);
  hrule(c, 52, 12, 328, 0.5, RULE);
  infoRow(c, f, 57, 'LARKMOOR AND THE VALLEY  ·  SINCE 1874  ·  WEATHER: MILD, 18°');
  hrule(c, 62, 12, 328, 0.5, RULE);
  headline(c, f, p.headline, 12, 64, 194, 17, INK);
  columns(c, 12, 112, 194, 94, 3, 29);
  photo(c, 2, 214, 68, 114, 82);
  textLines(c, 214, 155, 114, 51, 37, LINES, 0.6);
}

function fernhill(c: SkCanvas, f: SkTypefaceFontProvider, p: Paper) {
  dateline(c, f, 'TUESDAY, JUNE 4', 'FINAL');
  line(c, f, p.masthead, { family: BLACK, size: 31, color: hex(INK, 1) }, 170, 29, 0, 312);
  hrule(c, 49.5, 12, 328, 1.2, RULE);
  hrule(c, 52, 12, 328, 0.5, RULE);
  infoRow(c, f, 57, 'AN INDEPENDENT NEWSPAPER  ·  FERNHILL, TUESDAY  ·  $2.75');
  hrule(c, 62, 12, 328, 0.5, RULE);
  photo(c, 3, 12, 68, 120, 82);
  textLines(c, 12, 155, 120, 51, 41, LINES, 0.6);
  headline(c, f, p.headline, 140, 64, 188, 17, INK);
  columns(c, 140, 112, 188, 94, 3, 43);
}

function saltday(c: SkCanvas, f: SkTypefaceFontProvider, p: Paper) {
  hrule(c, 12, 12, 328, 2, RED);
  line(c, f, p.masthead, { family: SYSTEM, size: 32, weight: 900, italic: true, color: hex(NAVY, 1), tracking: -0.6 }, 170, 31, 0, 312);
  hrule(c, 50, 12, 328, 2, RED);
  infoRow(c, f, 57, 'TUESDAY, JUNE 4  ·  NORTH SHORE EDITION  ·  $2.00');
  photo(c, 4, 12, 66, 164, 84);
  columns(c, 12, 156, 164, 50, 2, 47);
  headline(c, f, p.headline, 184, 63, 144, 16, NAVY);
  columns(c, 184, 112, 144, 94, 2, 53);
}

function orrin(c: SkCanvas, f: SkTypefaceFontProvider, p: Paper) {
  dateline(c, f, 'TUESDAY, JUNE 4', '$2.50');
  line(c, f, p.masthead, { family: SERIF, size: 28, color: hex(INK, 1) }, 170, 30, 0, 312);
  hrule(c, 49.5, 12, 328, 1.2, RULE);
  hrule(c, 52, 12, 328, 0.5, RULE);
  infoRow(c, f, 57, "THE BAY'S OWN PAPER  ·  SPRING GARDENS ISSUE");
  hrule(c, 62, 12, 328, 0.5, RULE);
  line(c, f, p.headline, { family: SERIF, size: 19, color: hex(INK, 1) }, 170, 78, 0, 312);
  photo(c, 5, 12, 94, 188, 78);
  textLines(c, 12, 177, 188, 29, 59, LINES, 0.6);
  columns(c, 208, 94, 120, 112, 2, 61);
}

function drawPaper(c: SkCanvas, f: SkTypefaceFontProvider, i: number) {
  const p = PAPERS[i];
  const w = PAPER_W;
  const h = PAPER_H;
  // Page edges of the folded paper behind the front page.
  rect(c, 3.5, 4, w, h, hex(0xccc7bb, 1));
  rect(c, 2, 2, w, h, hex(0xdad6cb, 1));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), linear(0, 0, 0, h, [hex(p.tone, 1), darker(p.tone, 0.045)], null));
  grain(c, i * 101 + 7);

  c.save();
  c.clipRect(Skia.XYWHRect(0, 0, w, h), ClipOp.Intersect, true);
  if (p.style === 0) tabloid(c, f, p);
  else if (p.style === 1) quayside(c, f, p);
  else if (p.style === 2) larkmoor(c, f, p);
  else if (p.style === 3) fernhill(c, f, p);
  else if (p.style === 4) saltday(c, f, p);
  else orrin(c, f, p);
  c.restore();

  // Fold: the bottom edge is the fold, rounded and a little darker, with a light lip.
  c.drawRect(Skia.XYWHRect(0, h - 20, w, 20), linear(0, h - 20, 0, h, [Float32Array.of(0, 0, 0, 0), Float32Array.of(0, 0, 0, 0.09)], null));
  rect(c, 0, h - 1.2, w, 0.8, Float32Array.of(1, 1, 1, 0.55));
  // Left edge thickness.
  c.drawRect(Skia.XYWHRect(0, 0, 6, h), linear(0, 0, 6, 0, [Float32Array.of(0, 0, 0, 0.05), Float32Array.of(0, 0, 0, 0)], null));
  // Curl highlight on the top-right corner.
  const tri = Skia.PathBuilder.Make()
    .moveTo(w - 46, 0)
    .lineTo(w, 0)
    .lineTo(w, 46)
    .close()
    .detach();
  c.drawPath(
    tri,
    linear(
      w,
      0,
      w - 23,
      23,
      [Float32Array.of(1, 1, 1, 0), Float32Array.of(1, 1, 1, 0.22), Float32Array.of(0, 0, 0, 0.02), Float32Array.of(0, 0, 0, 0)],
      [0, 0.45, 0.85, 1],
    ),
  );
  c.drawRect(Skia.XYWHRect(0.25, 0.25, w - 0.5, h - 0.5), stroke(Float32Array.of(0, 0, 0, 0.06), 0.5));
}

/** The six paper bitmaps at the screen's pixel ratio. */
export function makePaperImages(fonts: SkTypefaceFontProvider, scale: number): SkImage[] {
  const out: SkImage[] = [];
  const W = PAPER_W + 2 * PAD;
  const H = PAPER_H + 2 * PAD;
  for (let i = 0; i < PAPERS.length; i++) {
    const surface = Skia.Surface.Make(Math.ceil(W * scale), Math.ceil(H * scale));
    if (!surface) continue;
    const c = surface.getCanvas();
    c.scale(scale, scale);
    c.translate(PAD, PAD);
    drawPaper(c, fonts, i);
    surface.flush();
    out.push(surface.makeImageSnapshot());
  }
  return out;
}
