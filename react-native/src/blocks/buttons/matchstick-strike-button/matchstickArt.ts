import {
  BlendMode,
  BlurStyle,
  ClipOp,
  FilterMode,
  MipmapMode,
  PaintStyle,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPaint,
  type SkRuntimeEffect,
  Skia,
  StrokeCap,
  TileMode,
} from '@shopify/react-native-skia';

import { type Geo, type Match, type Pose, clamp01 } from './matchstickMotion';

/*
 Matchstick Strike Button — the drawing. One Skia canvas: a baked table (walnut planks, the
 product card with an amber candle jar) and a baked matchbox (oxblood label, sandpaper striker,
 printing), with the match, the CTA's letters, sparks, the warm light and the fire shader drawn
 over them every frame. A match going back into the box is drawn under the box image.
*/

export const CTA = 'Strike to start';
const FLAME_GOLD = [1, 0.77, 0.42];
const FLASH = [1, 0.96, 0.84];
const PRINT = [0.23, 0.075, 0.06];

export type Fonts = { cta: SkFont; caps: SkFont; small: SkFont; jar: SkFont };

export type Scene = {
  w: number;
  h: number;
  cardX: number;
  cardY: number;
  cardW: number;
  cardH: number;
  boxX: number;
  boxY: number;
  boxW: number;
  boxH: number;
  /** Letter left edges (the glyphs are drawn one by one so each can catch on its own). */
  letterX: number[];
  geo: Geo;
};

function textWidth(font: SkFont, text: string) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let sum = 0;
  for (let i = 0; i < widths.length; i += 1) sum += widths[i];
  return sum;
}

export function makeScene(w: number, h: number, top: number, bottom: number, fonts: Fonts): Scene {
  const cardX = 16;
  const cardY = top + 112;
  const cardW = w - 32;
  const cardH = Math.round(Math.min(142, Math.max(112, h * 0.165)));
  const boxX = 20;
  const boxW = w - 40;
  const boxH = Math.round(Math.min(196, Math.max(160, h * 0.23)));
  const boxBottom = h - bottom - 92;
  const boxY = boxBottom - boxH;
  const strikerTop = boxY + 14;
  const strikerBottom = boxY + 40;
  const strikerLeft = boxX + 18;
  const strikerRight = boxX + boxW - 18;
  const baseline = boxY + Math.round(boxH * 0.6);
  const widths = fonts.cta.getGlyphWidths(fonts.cta.getGlyphIDs(CTA));
  let total = 0;
  for (const g of widths) total += g;
  let x = w / 2 - total / 2;
  const letterX: number[] = [];
  const letters: number[] = [];
  for (const g of widths) {
    letterX.push(x);
    letters.push(x + g / 2);
    x += g;
  }
  const midStrip = (strikerTop + strikerBottom) / 2;
  const geo: Geo = {
    rest: { x: boxX + 34, y: boxY - 32 },
    strikeFrom: { x: strikerRight - 20, y: midStrip },
    strikeTo: { x: strikerLeft + 36, y: midStrip },
    glideFrom: { x: letterX[0] - 12, y: baseline + 16 },
    glideTo: { x: x + 12, y: baseline + 16 },
    strikerTop,
    strikerBottom,
    strikerLeft,
    strikerRight,
    boxTop: boxY,
    letters,
    baseline,
    matchLength: Math.min(270, w * 0.68),
  };
  return { w, h, cardX, cardY, cardW, cardH, boxX, boxY, boxW, boxH, letterX, geo };
}

/** Draws a baked image with linear filtering (it moves, rotates and lands between pixels). */
function drawImg(c: SkCanvas, img: SkImage, src: ReturnType<typeof Skia.XYWHRect>, dst: ReturnType<typeof Skia.XYWHRect>, p: SkPaint) {
  'worklet';
  c.drawImageRectOptions(img, src, dst, FilterMode.Linear, MipmapMode.None, p);
}

function paint(color: string, alpha = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  if (alpha < 1) p.setAlphaf(alpha);
  return p;
}

function rgba(c: number[], a: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Float32Array.of(c[0], c[1], c[2], clamp01(a)));
  return p;
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: string[], pos: number[] | null = null) {
  'worklet';
  return Skia.Shader.MakeLinearGradient(Skia.Point(x0, y0), Skia.Point(x1, y1), colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp);
}

function radial(x: number, y: number, r: number, colors: string[], pos: number[] | null = null) {
  'worklet';
  return Skia.Shader.MakeRadialGradient(Skia.Point(x, y), r, colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp);
}

function blurred(color: string, alpha: number, sigma: number) {
  'worklet';
  const p = paint(color, alpha);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function grain(alpha: number, fx: number, fy: number, seed: number, mode = BlendMode.Overlay) {
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeFractalNoise(fx, fy, 2, seed, 0, 0));
  p.setColorFilter(Skia.ColorFilter.MakeMatrix([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, alpha]));
  p.setBlendMode(mode);
  return p;
}

function bake(w: number, h: number, scale: number, draw: (c: SkCanvas) => void): SkImage | null {
  const surface = Skia.Surface.Make(Math.ceil(w * scale), Math.ceil(h * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  draw(c);
  surface.flush();
  return surface.makeImageSnapshot();
}

/** Walnut table, the product card and its candle jar. */
export function bakeTable(s: Scene, fonts: Fonts, scale: number) {
  return bake(s.w, s.h, scale, (c) => {
    const { w, h } = s;
    const wood = Skia.Paint();
    wood.setShader(linear(0, 0, 0, h, ['#21160F', '#1A110C', '#150E0A', '#100B08'], [0, 0.4, 0.75, 1]));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), wood);
    // Long grain along the planks, and the seams between them.
    c.drawRect(Skia.XYWHRect(0, 0, w, h), grain(0.22, 0.006, 0.34, 9));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), grain(0.06, 0.9, 0.9, 4));
    for (let y = 96; y < h; y += 118) {
      c.drawLine(0, y, w, y, paint('#070403', 0.55));
      c.drawLine(0, y + 1, w, y + 1, paint('#FFD9B0', 0.05));
    }
    const vig = Skia.Paint();
    vig.setShader(radial(w * 0.5, h * 0.55, h * 0.72, ['rgba(0,0,0,0)', 'rgba(5,3,2,0.25)', 'rgba(5,3,2,0.68)'], [0.35, 0.72, 1]));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), vig);

    // Card: two shadows, a warm three-stop body, hairline highlight.
    const r = 22;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(s.cardX, s.cardY + 14, s.cardW, s.cardH), r, r), blurred('#000000', 0.5, 16));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(s.cardX, s.cardY + 1.5, s.cardW, s.cardH), r, r), blurred('#000000', 0.55, 1.5));
    const card = Skia.RRectXY(Skia.XYWHRect(s.cardX, s.cardY, s.cardW, s.cardH), r, r);
    const body = Skia.Paint();
    body.setAntiAlias(true);
    body.setShader(linear(0, s.cardY, 0, s.cardY + s.cardH, ['#3A2A20', '#2D2119', '#261B15']));
    c.drawRRect(card, body);
    c.save();
    c.clipRRect(card, ClipOp.Intersect, true);
    c.drawRect(Skia.XYWHRect(s.cardX, s.cardY, s.cardW, s.cardH), grain(0.08, 1.2, 1.2, 2));
    c.restore();
    const edge = paint('#FFE2C2', 0.12);
    edge.setStyle(PaintStyle.Stroke);
    edge.setStrokeWidth(1);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(s.cardX + 0.5, s.cardY + 0.5, s.cardW - 1, s.cardH - 1), r - 0.5, r - 0.5), edge);

    // The candle jar: amber glass, cream wax with three wicks, a printed paper band.
    const jh = s.cardH - 32;
    const jw = Math.round(jh * 0.8);
    const jx = s.cardX + 18;
    const jy = s.cardY + 16;
    c.drawOval(Skia.XYWHRect(jx - 4, jy + jh - 8, jw + 8, 14), blurred('#000000', 0.55, 5));
    const glass = Skia.Paint();
    glass.setAntiAlias(true);
    glass.setShader(linear(jx, 0, jx + jw, 0, ['#4A240C', '#9C5A22', '#C98A45', '#7A4316', '#3B1C09'], [0, 0.28, 0.42, 0.75, 1]));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(jx, jy + 6, jw, jh - 6), 12, 12), glass);
    const wax = Skia.Paint();
    wax.setAntiAlias(true);
    wax.setShader(linear(0, jy, 0, jy + 14, ['#F4E6CC', '#E2CDA8', '#C9B08A']));
    c.drawOval(Skia.XYWHRect(jx + 4, jy + 2, jw - 8, 14), wax);
    const wick = paint('#2A1B12');
    wick.setStrokeWidth(1.6);
    wick.setStrokeCap(StrokeCap.Round);
    for (let i = -1; i <= 1; i += 1) c.drawLine(jx + jw / 2 + i * jw * 0.2, jy + 9, jx + jw / 2 + i * jw * 0.2 + 1, jy + 3, wick);
    const band = Skia.Paint();
    band.setShader(linear(jx, 0, jx + jw, 0, ['#CDB894', '#EBDDC2', '#D9C6A3', '#B29C78'], [0, 0.35, 0.7, 1]));
    const by = jy + jh * 0.42;
    c.drawRect(Skia.XYWHRect(jx + 1, by, jw - 2, jh * 0.3), band);
    const label = 'FIG & CEDAR';
    const lw = textWidth(fonts.jar, label);
    c.drawText(label, jx + jw / 2 - lw / 2, by + jh * 0.15 + 3, paint('#4A2A18', 0.9), fonts.jar);
    c.drawLine(jx + 10, by + jh * 0.24, jx + jw - 10, by + jh * 0.24, paint('#4A2A18', 0.4));
    // Glass highlights: a long specular strip and a rim.
    const spec = Skia.Paint();
    spec.setShader(linear(0, jy + 10, 0, jy + jh, ['rgba(255,236,206,0.42)', 'rgba(255,236,206,0.1)', 'rgba(255,236,206,0)']));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(jx + jw * 0.16, jy + 12, jw * 0.1, jh - 26), 3, 3), spec);
    const rim = paint('#FFE0B0', 0.3);
    rim.setStyle(PaintStyle.Stroke);
    rim.setStrokeWidth(1);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(jx + 0.5, jy + 6.5, jw - 1, jh - 7), 11.5, 11.5), rim);
  });
}

/** Where the box image sits (it only covers what it draws). */
export function boxRect(s: Scene) {
  'worklet';
  return { x: s.boxX - 60, y: s.boxY - 20, w: s.boxW + 120, h: s.boxH + 110 };
}

/** The matchbox, transparent around it, so a match can pass behind it. */
export function bakeBox(s: Scene, fonts: Fonts, scale: number) {
  const r = boxRect(s);
  return bake(r.w, r.h, scale, (c) => {
    c.translate(-r.x, -r.y);
    const { boxX: x, boxY: y, boxW: w, boxH: h } = s;
    const g = s.geo;
    // Shadows on the table, then the drawer's edge (the box's thickness) under the label.
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y + 22, w, h), 10, 10), blurred('#000000', 0.55, 20));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y + 4, w, h + 10), 8, 8), blurred('#000000', 0.6, 2));
    const side = Skia.Paint();
    side.setAntiAlias(true);
    side.setShader(linear(0, y + h - 10, 0, y + h + 12, ['#5A2019', '#3E140F', '#2A0D0A']));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y + h - 12, w, 24), 8, 8), side);
    // The label: oxblood paper with grain and a soft sheen from the upper left.
    const face = Skia.RRectXY(Skia.XYWHRect(x, y, w, h), 8, 8);
    const label = Skia.Paint();
    label.setAntiAlias(true);
    label.setShader(linear(x, y, x + w * 0.4, y + h, ['#8A3328', '#74281F', '#621F18', '#561A14'], [0, 0.35, 0.7, 1]));
    c.drawRRect(face, label);
    c.save();
    c.clipRRect(face, ClipOp.Intersect, true);
    c.drawRect(Skia.XYWHRect(x, y, w, h), grain(0.12, 1.3, 1.3, 21));
    const sheen = Skia.Paint();
    sheen.setShader(radial(x + w * 0.2, y, w * 0.9, ['rgba(255,214,180,0.14)', 'rgba(255,214,180,0)']));
    c.drawRect(Skia.XYWHRect(x, y, w, h), sheen);
    c.restore();
    c.drawLine(x + 8, y + 0.5, x + w - 8, y + 0.5, paint('#FFC9B0', 0.3));
    // A cream double border printed inside the label.
    const border = paint('#F1DEC2', 0.55);
    border.setStyle(PaintStyle.Stroke);
    border.setStrokeWidth(1.2);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 8, y + 52, w - 16, h - 62), 5, 5), border);
    border.setStrokeWidth(0.6);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 12, y + 56, w - 24, h - 70), 3, 3), border);
    // Sandpaper striker: red-brown grit with dark speckle, a pressed edge.
    const sx = g.strikerLeft;
    const sw = g.strikerRight - g.strikerLeft;
    const sh = g.strikerBottom - g.strikerTop;
    const strip = Skia.RRectXY(Skia.XYWHRect(sx, g.strikerTop, sw, sh), 4, 4);
    const grit = Skia.Paint();
    grit.setAntiAlias(true);
    grit.setShader(linear(0, g.strikerTop, 0, g.strikerBottom, ['#5E3222', '#4A2518', '#3A1C12']));
    c.drawRRect(strip, grit);
    c.save();
    c.clipRRect(strip, ClipOp.Intersect, true);
    c.drawRect(Skia.XYWHRect(sx, g.strikerTop, sw, sh), grain(0.55, 2.4, 2.4, 13));
    c.drawRect(Skia.XYWHRect(sx, g.strikerTop, sw, sh), grain(0.35, 0.9, 0.9, 17, BlendMode.Multiply));
    c.restore();
    const inner = paint('#1A0905', 0.6);
    inner.setStyle(PaintStyle.Stroke);
    inner.setStrokeWidth(1);
    c.drawRRect(strip, inner);
    c.drawLine(sx + 4, g.strikerBottom + 0.8, sx + sw - 4, g.strikerBottom + 0.8, paint('#FFC9B0', 0.18));
    // Printing: the brand line in caps, the hint and the count.
    const caps = 'HEARTH · SAFETY MATCHES';
    c.drawText(caps, x + w / 2 - textWidth(fonts.caps, caps) / 2, y + 72, paint('#F1DEC2', 0.7), fonts.caps);
    const hint = 'Swipe the match across the strip';
    c.drawText(hint, x + w / 2 - textWidth(fonts.small, hint) / 2, g.baseline + 30, paint('#F1DEC2', 0.55), fonts.small);
    const count = 'No. 7  ·  40 long matches';
    c.drawText(count, x + w / 2 - textWidth(fonts.caps, count) / 2, y + h - 22, paint('#F1DEC2', 0.45), fonts.caps);
  });
}

/** One match in its own frame: head at the origin, the stick along +x. */
function drawMatchBody(c: SkCanvas, m: Match, len: number, lit: number) {
  'worklet';
  const stick = Skia.Paint();
  stick.setAntiAlias(true);
  stick.setShader(linear(0, -3.6, 0, 3.6, ['#F3E0B6', '#DEC08A', '#B98F58']));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(2, -3.6, len, 7.2), 2.5, 2.5), stick);
  const fibre = paint('#9C7444', 0.22);
  fibre.setStrokeWidth(0.6);
  c.drawLine(14, -1.2, len - 10, -1.4, fibre);
  c.drawLine(30, 1.6, len - 30, 1.3, fibre);
  // Char creeping down from the head, with a glowing edge while it burns.
  if (m.burnt > 0.01) {
    const cl = 6 + 28 * m.char;
    const ch = Skia.Paint();
    ch.setAntiAlias(true);
    ch.setShader(linear(cl - 8, 0, cl, 0, ['#241710', 'rgba(36,23,16,0)']));
    ch.setAlphaf(clamp01(m.burnt));
    c.drawRect(Skia.XYWHRect(2, -3.7, cl, 7.4), ch);
    if (lit > 0.02) {
      const ember = paint('#FF8A3A', 0.7 * lit);
      ember.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.5, true));
      c.drawRect(Skia.XYWHRect(cl - 4, -3.6, 3, 7.2), ember);
    }
  }
  // The head: a red bulb going to charcoal when struck, with a highlight.
  const head = Skia.Paint();
  head.setAntiAlias(true);
  head.setShader(radial(-3, -2.5, 11, ['#D2564A', '#A3291F', '#6E1712']));
  c.drawOval(Skia.XYWHRect(-9, -6.2, 18, 12.4), head);
  if (m.burnt > 0.01) {
    const coal = Skia.Paint();
    coal.setAntiAlias(true);
    coal.setShader(radial(-2, -2, 10, ['#4A3328', '#261A14', '#140E0B']));
    coal.setAlphaf(clamp01(m.burnt));
    c.drawOval(Skia.XYWHRect(-9.5, -6.6, 19, 13.2), coal);
  }
  c.drawOval(Skia.XYWHRect(-5.5, -4.6, 6, 3), paint('#FFE6D6', 0.42 * (1 - clamp01(m.burnt))));
}

function drawMatch(c: SkCanvas, m: Match, len: number, lit: number) {
  'worklet';
  if (m.alpha < 0.005) return;
  if (m.alpha < 0.999) {
    const layer = Skia.Paint();
    layer.setAlphaf(m.alpha);
    c.saveLayer(layer);
  }
  // The shadow falls down-screen; held up, it is further, larger and softer.
  c.save();
  c.translate(m.x + 2 + 8 * m.lift, m.y + 4 + 18 * m.lift);
  c.rotate(m.angle, 0, 0);
  const sh = blurred('#000000', 0.5 - 0.18 * m.lift, 1.8 + 5 * m.lift);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-8, -5, len + 10, 10), 5, 5), sh);
  c.restore();
  c.save();
  c.translate(m.x, m.y);
  c.rotate(m.angle, 0, 0);
  const s = 1 + 0.05 * m.lift;
  c.scale(s, s);
  drawMatchBody(c, m, len, lit);
  c.restore();
  if (m.alpha < 0.999) c.restore();
}

function drawSparks(c: SkCanvas, sparks: number[]) {
  'worklet';
  if (sparks.length === 0) return;
  const young = Skia.PathBuilder.Make();
  const old = Skia.PathBuilder.Make();
  for (let i = 0; i + 5 < sparks.length; i += 6) {
    const [x0, y0, vx, vy, a, life] = [sparks[i], sparks[i + 1], sparks[i + 2], sparks[i + 3], sparks[i + 4], sparks[i + 5]];
    const a0 = Math.max(0, a - 0.03);
    const b = a / life < 0.5 ? young : old;
    b.moveTo(x0 + vx * a0, y0 + vy * a0 + 450 * a0 * a0);
    b.lineTo(x0 + vx * a, y0 + vy * a + 450 * a * a);
  }
  for (const [path, color, width] of [
    [young.build(), '#FFE7A8', 1.6],
    [old.build(), '#FF9A45', 1.2],
  ] as const) {
    const glow = paint(color, 0.55);
    glow.setStyle(PaintStyle.Stroke);
    glow.setStrokeWidth(width * 3.5);
    glow.setStrokeCap(StrokeCap.Round);
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
    glow.setBlendMode(BlendMode.Plus);
    c.drawPath(path, glow);
    const core = paint(color);
    core.setStyle(PaintStyle.Stroke);
    core.setStrokeWidth(width);
    core.setStrokeCap(StrokeCap.Round);
    core.setBlendMode(BlendMode.Plus);
    c.drawPath(path, core);
  }
}

function drawLetters(c: SkCanvas, s: Scene, heat: number[], font: SkFont) {
  'worklet';
  const y = s.geo.baseline;
  for (let i = 0; i < CTA.length; i += 1) {
    const ch = CTA[i];
    if (ch === ' ') continue;
    const x = s.letterX[i];
    const h = heat[i] ?? 0;
    // Printed (debossed): a light lower lip, then the dark ink.
    c.drawText(ch, x, y + 0.9, paint('#FFC7B0', 0.16 * (1 - clamp01(h))), font);
    if (h > 0.01) {
      const glow = rgba([1, 0.5, 0.18], 0.85 * Math.min(1.2, h));
      glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8, true));
      glow.setBlendMode(BlendMode.Screen);
      c.drawText(ch, x, y, glow, font);
    }
    const k = clamp01(h);
    const f = clamp01((h - 1) / 0.6);
    const col = [0, 1, 2].map((j) => PRINT[j] + (FLAME_GOLD[j] - PRINT[j]) * k + (FLASH[j] - FLAME_GOLD[j]) * f);
    c.drawText(ch, x, y, rgba(col, 1), font);
  }
}

export type Layers = { table: SkImage; box: SkImage };

/** The rectangle the fire shader needs this frame (flame and both smoke columns). */
function fireBounds(pose: Pose, base: { x: number; y: number }) {
  'worklet';
  let l = base.x - 90;
  let r = base.x + 90;
  let t = base.y - 130;
  let b = base.y + 40;
  for (const sm of [pose.puff, pose.curl]) {
    if (sm[3] > 0.01 && sm[2] >= 0) {
      l = Math.min(l, sm[0] - 110);
      r = Math.max(r, sm[0] + 110);
      t = Math.min(t, sm[1] - 330);
      b = Math.max(b, sm[1] + 10);
    }
  }
  return Skia.XYWHRect(l, t, r - l, b - t);
}

/** One frame. */
export function drawFrame(c: SkCanvas, s: Scene, layers: Layers, pose: Pose, fonts: Fonts, effect: SkRuntimeEffect | null, p3: number) {
  'worklet';
  const full = Skia.XYWHRect(0, 0, s.w, s.h);
  const ip = Skia.Paint();
  const len = s.geo.matchLength;
  drawImg(c, layers.table, Skia.XYWHRect(0, 0, layers.table.width(), layers.table.height()), full, ip);
  // A match on its way into (or out of) the box is under the box.
  if (pose.match.front < 0.5) {
    c.save();
    c.clipRect(Skia.XYWHRect(0, 0, s.w, s.boxY + s.boxH - 12), ClipOp.Intersect, true);
    drawMatch(c, pose.match, len, pose.lit);
    c.restore();
  }
  const boxAt = boxRect(s);
  drawImg(c, layers.box, Skia.XYWHRect(0, 0, layers.box.width(), layers.box.height()), Skia.XYWHRect(boxAt.x, boxAt.y, boxAt.w, boxAt.h), ip);
  // A scrape on the strip: darker, polished grit.
  if (pose.scrape[2] > 0.005) {
    const x0 = Math.min(pose.scrape[0], pose.scrape[1]);
    const x1 = Math.max(pose.scrape[0], pose.scrape[1]);
    const mark = paint('#140805', pose.scrape[2]);
    mark.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2, true));
    const g = s.geo;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0, (g.strikerTop + g.strikerBottom) / 2 - 3, Math.max(4, x1 - x0), 6), 3, 3), mark);
  }
  drawLetters(c, s, pose.heat, fonts.cta);
  if (pose.spare > 0.005) {
    const g = s.geo;
    drawMatch(c, { x: g.rest.x, y: g.rest.y, angle: 0, lift: 0, burnt: 0, char: 0, alpha: pose.spare, front: 1 }, len, 0);
  }
  if (pose.match.front >= 0.5) drawMatch(c, pose.match, len, pose.lit);
  // Warm light from the flame over everything it reaches.
  const base = { x: pose.match.x, y: pose.match.y - 6 };
  if (pose.light > 0.01) {
    const light = Skia.Paint();
    light.setShader(radial(base.x, base.y - 24, 360, ['rgba(255,150,70,0.42)', 'rgba(255,120,50,0.16)', 'rgba(255,110,40,0)'], [0, 0.4, 1]));
    light.setAlphaf(clamp01(pose.light));
    light.setBlendMode(BlendMode.Screen);
    c.drawRect(full, light);
  }
  drawSparks(c, pose.sparks);
  if (effect && (pose.lit > 0.005 || pose.flare > 0.005 || pose.puff[3] > 0.01 || pose.curl[3] > 0.01)) {
    const u = [
      base.x,
      base.y,
      pose.lit,
      pose.flare,
      pose.lean,
      pose.phase * 7,
      pose.phase * 11,
      0,
      pose.puff[0],
      pose.puff[1],
      pose.puff[2],
      pose.puff[3],
      pose.curl[0],
      pose.curl[1],
      pose.curl[2],
      pose.curl[3],
      p3,
    ];
    const fire = Skia.Paint();
    fire.setShader(effect.makeShader(u));
    c.drawRect(fireBounds(pose, base), fire);
  }
}
