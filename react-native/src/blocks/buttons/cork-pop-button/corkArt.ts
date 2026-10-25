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

import { type Geo, type Pose, hash } from './corkMotion';

/*
 Cork Pop Button — the drawing, one Skia canvas. Baked once: the room (emerald night, a warm spot
 behind the bottle, vignette, grain), the order card with its text, the bottle (green glass with two
 specular strips, a crinkled gold foil with a scalloped tear edge, the cream "Celebrate" label on
 the shoulder), the cork (mushroom and shaft) and one gold bubble. Per frame: the card's lift and
 sheen, the bottle's drop, the cork's inch and rock, the wire cage (legs bow and the key spins as
 it loosens), the foam shader, the CO2 puff, the flying cork and the bubbles.
*/

export type Fonts = { kicker: SkFont; mono: SkFont; row: SkFont; rowBold: SkFont; script: SkFont; tiny: SkFont };
export type Baked = { img: SkImage; x: number; y: number; w: number; h: number };
export type Layers = { bg: Baked; card: Baked; bottle: Baked; cork: Baked; bubble: Baked };
export type Layout = Geo & { shoulder: number; labelY: number; hintY: number };

export function makeLayout(w: number, h: number, top: number): Layout {
  const cardX = 20;
  const cardY = top + 108;
  const cardW = w - 40;
  const cardH = h < 740 ? 128 : 148;
  const hintY = cardY + cardH + 18;
  const lipY = cardY + cardH + (h < 740 ? 100 : 128);
  const avail = h - lipY;
  const neckLen = Math.max(110, Math.min(180, avail * 0.42));
  const shoulder = Math.max(80, Math.min(120, avail * 0.3));
  return {
    w,
    h,
    cx: w / 2,
    lipY,
    neck: 27,
    neckLen,
    body: Math.min(w * 0.43, 190),
    corkW: 25,
    corkH: 30,
    cardX,
    cardY,
    cardW,
    cardH,
    shoulder,
    labelY: lipY + neckLen + shoulder * 0.86,
    hintY,
  };
}

function paint(color: string, alpha = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  if (alpha < 1) p.setAlphaf(alpha);
  return p;
}

function stroke(color: string, width: number, alpha = 1) {
  'worklet';
  const p = paint(color, alpha);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  return p;
}

function colors(list: string[]) {
  'worklet';
  const out = [];
  for (let i = 0; i < list.length; i += 1) out.push(Skia.Color(list[i]));
  return out;
}

function linear(x0: number, y0: number, x1: number, y1: number, list: string[], pos: number[] | null = null) {
  'worklet';
  return Skia.Shader.MakeLinearGradient(Skia.Point(x0, y0), Skia.Point(x1, y1), colors(list), pos, TileMode.Clamp);
}

function radial(x: number, y: number, r: number, list: string[], pos: number[] | null = null) {
  'worklet';
  return Skia.Shader.MakeRadialGradient(Skia.Point(x, y), Math.max(0.1, r), colors(list), pos, TileMode.Clamp);
}

function blurred(color: string, alpha: number, sigma: number) {
  'worklet';
  const p = paint(color, alpha);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function drawBaked(c: SkCanvas, b: Baked, p: SkPaint, dx = 0, dy = 0) {
  'worklet';
  c.drawImageRectOptions(b.img, Skia.XYWHRect(0, 0, b.img.width(), b.img.height()), Skia.XYWHRect(b.x + dx, b.y + dy, b.w, b.h), FilterMode.Linear, MipmapMode.None, p);
}

function textWidth(font: SkFont, text: string) {
  const ws = font.getGlyphWidths(font.getGlyphIDs(text));
  let sum = 0;
  for (let i = 0; i < ws.length; i += 1) sum += ws[i];
  return sum;
}

function grain(alpha: number, seed: number) {
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 2, seed, 0, 0));
  p.setColorFilter(Skia.ColorFilter.MakeMatrix([0.33, 0.33, 0.33, 0, 0, 0.33, 0.33, 0.33, 0, 0, 0.33, 0.33, 0.33, 0, 0, 0, 0, 0, 0, alpha]));
  p.setBlendMode(BlendMode.Overlay);
  return p;
}

function bake(x: number, y: number, w: number, h: number, scale: number, draw: (c: SkCanvas) => void): Baked | null {
  const surface = Skia.Surface.Make(Math.ceil(w * scale), Math.ceil(h * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  c.translate(-x, -y);
  draw(c);
  surface.flush();
  return { img: surface.makeImageSnapshot(), x, y, w, h };
}

function drawRoom(c: SkCanvas, g: Layout) {
  const { w, h } = g;
  const bg = Skia.Paint();
  bg.setShader(linear(0, 0, 0, h, ['#0F201B', '#112A22', '#0C1E18', '#08130F'], [0, 0.4, 0.75, 1]));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), bg);
  const spot = Skia.Paint();
  spot.setShader(radial(g.cx, g.lipY + 40, w * 0.95, ['rgba(214,170,96,0.32)', 'rgba(120,110,70,0.12)', 'rgba(20,40,32,0)'], [0, 0.5, 1]));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), spot);
  // A faint diamond trellis, like the shop's wrapping paper.
  for (let i = -12; i < 24; i += 1) {
    const x = i * 34;
    c.drawLine(x, 0, x + h * 0.5, h, stroke('#E8D3A0', 0.6, 0.03));
    c.drawLine(x + h * 0.5, 0, x, h, stroke('#E8D3A0', 0.6, 0.03));
  }
  const vig = Skia.Paint();
  vig.setShader(radial(w / 2, h * 0.5, Math.hypot(w, h) * 0.6, ['rgba(4,10,8,0)', 'rgba(4,10,8,0.3)', 'rgba(3,8,6,0.7)'], [0, 0.6, 1]));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), vig);
  c.drawRect(Skia.XYWHRect(0, 0, w, h), grain(0.07, 7));
}

function drawCard(c: SkCanvas, g: Layout, f: Fonts) {
  const { cardX: x, cardY: y, cardW: w, cardH: h } = g;
  const rr = Skia.RRectXY(Skia.XYWHRect(x, y, w, h), 22, 22);
  const face = Skia.Paint();
  face.setShader(linear(x, y, x + w * 0.4, y + h, ['#FBF5E8', '#F3EBD8', '#E9DEC6'], [0, 0.55, 1]));
  c.drawRRect(rr, face);
  c.save();
  c.clipRRect(rr, ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(x, y, w, h), grain(0.08, 11));
  // A gold deckle line inside the edge.
  c.restore();
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 6, y + 6, w - 12, h - 12), 17, 17), stroke('#C9A35A', 0.8, 0.55));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 0.5, y + 0.5, w - 1, h - 1), 21.5, 21.5), stroke('#FFFFFF', 1, 0.6));
  const small = h < 140;
  const ink = paint('#16302A');
  const mute = paint('#5E6E66');
  const gold = paint('#A87F33');
  const left = x + 22;
  const right = x + w - 22;
  c.drawText('ORDER', left, y + (small ? 30 : 34), gold, f.kicker);
  c.drawText('MV-20481', left, y + (small ? 54 : 60), ink, f.mono);
  // "Confirmed" pill.
  const label = 'Confirmed';
  const lw = textWidth(f.tiny, label) + 26;
  const py = y + (small ? 22 : 26);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(right - lw, py, lw, 24), 12, 12), paint('#1F5B45', 0.12));
  c.drawCircle(right - lw + 11, py + 12, 3, paint('#2C7A5C'));
  c.drawText(label, right - lw + 18, py + 16.5, paint('#1F5B45'), f.tiny);
  const div = y + (small ? 68 : 78);
  c.drawLine(left, div, right, div, stroke('#C9B48A', 1, 0.5));
  const r1 = div + (small ? 24 : 28);
  const r2 = r1 + (small ? 24 : 28);
  c.drawText('Cuvée Lumière Brut × 2', left, r1, ink, f.row);
  const p1 = '€96.00';
  c.drawText(p1, right - textWidth(f.rowBold, p1), r1, ink, f.rowBold);
  c.drawText('Delivery · Thu 30 Oct', left, r2, mute, f.row);
  const p2 = 'Free';
  c.drawText(p2, right - textWidth(f.row, p2), r2, mute, f.row);
}

/** Half width of the bottle at height y (from the lip down). */
export function bottleHalf(g: Layout, y: number) {
  'worklet';
  const d = y - g.lipY;
  if (d < 10) return g.neck + 3;
  if (d < g.neckLen) return g.neck + (6 * (d - 10)) / (g.neckLen - 10);
  const u = Math.min(1, (d - g.neckLen) / g.shoulder);
  const s = u * u * (3 - 2 * u);
  return g.neck + 6 + (g.body - g.neck - 6) * s;
}

function bottlePath(g: Layout, inset = 0) {
  const b = Skia.PathBuilder.Make();
  const top = g.lipY;
  const bottom = g.h + 60;
  b.moveTo(g.cx - bottleHalf(g, top) + inset, top + 4);
  b.quadTo(g.cx - bottleHalf(g, top) + inset, top, g.cx - bottleHalf(g, top) + 4 + inset, top);
  b.lineTo(g.cx + bottleHalf(g, top) - 4 - inset, top);
  b.quadTo(g.cx + bottleHalf(g, top) - inset, top, g.cx + bottleHalf(g, top) - inset, top + 4);
  for (let y = top + 6; y <= bottom; y += 6) b.lineTo(g.cx + bottleHalf(g, y) - inset, y);
  for (let y = bottom; y >= top + 6; y -= 6) b.lineTo(g.cx - bottleHalf(g, y) + inset, y);
  b.close();
  return b.build();
}

function drawBottle(c: SkCanvas, g: Layout, f: Fonts) {
  const { cx, lipY } = g;
  const glass = bottlePath(g);
  const body = Skia.Paint();
  const B = g.body;
  body.setShader(linear(cx - B, 0, cx + B, 0, ['#03100B', '#0B3325', '#1C5A40', '#0E3B2B', '#062015', '#020A07'], [0, 0.2, 0.36, 0.55, 0.82, 1]));
  c.drawPath(glass, body);
  c.save();
  c.clipPath(glass, ClipOp.Intersect, true);
  // Light from the spot through the glass: a glow low in the body.
  c.drawCircle(cx + B * 0.15, g.labelY + 160, B * 0.9, blurred('#3E8F63', 0.35, 40));
  // Specular strips that follow the bottle's outline.
  for (const [k, a, wdt] of [
    [-0.55, 0.45, 5],
    [-0.42, 0.18, 2],
    [0.62, 0.14, 7],
  ]) {
    const s = Skia.PathBuilder.Make();
    for (let y = lipY + 8; y < g.h + 60; y += 6) {
      const x = cx + bottleHalf(g, y) * k;
      if (y === lipY + 8) s.moveTo(x, y);
      else s.lineTo(x, y);
    }
    const p = stroke('#E9FFF2', wdt, a);
    p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, wdt * 0.5, true));
    c.drawPath(s.build(), p);
  }
  c.restore();
  // The foil: from the lip down 85 % of the neck, with a scalloped tear edge.
  const foilEnd = lipY + g.neckLen * 0.85;
  const foil = Skia.PathBuilder.Make();
  foil.moveTo(cx - bottleHalf(g, lipY) - 1.5, lipY - 1);
  foil.lineTo(cx + bottleHalf(g, lipY) + 1.5, lipY - 1);
  for (let y = lipY; y <= foilEnd; y += 4) foil.lineTo(cx + bottleHalf(g, y) + 1.5, y);
  const half = bottleHalf(g, foilEnd) + 1.5;
  const teeth = 9;
  for (let i = teeth; i >= 0; i -= 1) {
    const x = cx - half + (2 * half * i) / teeth;
    foil.lineTo(x, foilEnd + (i % 2 === 0 ? 0 : 7));
  }
  for (let y = foilEnd; y >= lipY; y -= 4) foil.lineTo(cx - bottleHalf(g, y) - 1.5, y);
  foil.close();
  const fp = foil.build();
  const fpaint = Skia.Paint();
  fpaint.setShader(linear(cx - half, 0, cx + half, 0, ['#5E4317', '#B89042', '#F7E3A6', '#D5B062', '#8E6A2A', '#4C3612'], [0, 0.22, 0.38, 0.55, 0.8, 1]));
  c.drawPath(fp, fpaint);
  c.save();
  c.clipPath(fp, ClipOp.Intersect, true);
  // Crinkles: short light and dark strokes, seeded.
  for (let i = 0; i < 90; i += 1) {
    const y = lipY + hash(i + 3) * (foilEnd - lipY + 6);
    const x = cx + (hash(i + 7) - 0.5) * 2 * bottleHalf(g, y);
    const a = (hash(i + 11) - 0.5) * 1.2;
    const l = 4 + 9 * hash(i + 13);
    c.drawLine(x, y, x + Math.cos(a) * l, y + Math.sin(a) * l, stroke(i % 2 === 0 ? '#FFF3C8' : '#3E2A0C', 0.7, 0.32));
  }
  // The neck band: an embossed ring with the house's initials.
  const band = lipY + g.neckLen * 0.45;
  c.drawRect(Skia.XYWHRect(cx - 60, band - 9, 120, 18), paint('#2A1C06', 0.25));
  c.drawLine(cx - 60, band - 9, cx + 60, band - 9, stroke('#FFF1C4', 0.8, 0.5));
  c.drawLine(cx - 60, band + 9, cx + 60, band + 9, stroke('#2A1C06', 0.8, 0.5));
  const mv = 'M  V';
  c.drawText(mv, cx - textWidth(f.tiny, mv) / 2, band + 4.5, paint('#3B2A0C', 0.8), f.tiny);
  c.restore();
  // The label on the shoulder: the call to action.
  const ly = g.labelY;
  const lw = Math.min(214, g.body * 1.16);
  const lh = 66;
  const lrect = Skia.RRectXY(Skia.XYWHRect(cx - lw / 2, ly - lh / 2, lw, lh), lh / 2, lh / 2);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - lw / 2, ly - lh / 2 + 4, lw, lh), lh / 2, lh / 2), blurred('#010503', 0.5, 8));
  const lp = Skia.Paint();
  lp.setShader(linear(cx - lw / 2, 0, cx + lw / 2, 0, ['#CFC3A6', '#F7F0DE', '#FBF6EA', '#E8DDC2', '#BBAE8E'], [0, 0.25, 0.45, 0.75, 1]));
  c.drawRRect(lrect, lp);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - lw / 2 + 5, ly - lh / 2 + 5, lw - 10, lh - 10), lh / 2 - 5, lh / 2 - 5), stroke('#B08A3E', 1.1, 0.9));
  const cap = 'CUVÉE LUMIÈRE · BRUT';
  c.drawText(cap, cx - textWidth(f.tiny, cap) / 2, ly - 11, paint('#9A7631'), f.tiny);
  const cta = 'Celebrate';
  c.drawText(cta, cx - textWidth(f.script, cta) / 2, ly + 17, paint('#163A2D'), f.script);
}

function drawCork(c: SkCanvas, g: Layout) {
  const { cx, lipY, corkW, corkH } = g;
  const top = lipY - corkH;
  // The shaft, which shows as the cork inches out of the neck.
  const shaft = Skia.Paint();
  shaft.setShader(linear(cx - 17, 0, cx + 17, 0, ['#9C7646', '#E2C796', '#D2B07A', '#8E6939']));
  c.drawRect(Skia.XYWHRect(cx - 17, lipY - 4, 34, 30), shaft);
  for (let i = 0; i < 14; i += 1) c.drawCircle(cx + (hash(i + 90) - 0.5) * 30, lipY + hash(i + 94) * 26, 0.7 + hash(i + 98), paint('#6C4B24', 0.5));
  // The mushroom.
  const m = Skia.PathBuilder.Make()
    .moveTo(cx - corkW + 3, lipY + 2)
    .cubicTo(cx - corkW - 2, top + corkH * 0.55, cx - corkW * 0.9, top, cx, top)
    .cubicTo(cx + corkW * 0.9, top, cx + corkW + 2, top + corkH * 0.55, cx + corkW - 3, lipY + 2)
    .close()
    .build();
  const mp = Skia.Paint();
  mp.setShader(linear(cx - corkW, 0, cx + corkW, 0, ['#7E5A2E', '#C9A471', '#E3C693', '#B48A54', '#6B4A22'], [0, 0.25, 0.42, 0.7, 1]));
  c.drawPath(m, mp);
  c.save();
  c.clipPath(m, ClipOp.Intersect, true);
  for (let i = 0; i < 40; i += 1) {
    const x = cx + (hash(i + 30) - 0.5) * corkW * 2;
    const y = top + hash(i + 34) * corkH;
    c.drawOval(Skia.XYWHRect(x, y, 1 + 2 * hash(i + 38), 0.8 + hash(i + 39)), paint('#5A3C18', 0.45));
  }
  const shade = Skia.Paint();
  shade.setShader(linear(0, top, 0, lipY, ['rgba(255,240,210,0.25)', 'rgba(0,0,0,0)', 'rgba(40,20,5,0.35)'], [0, 0.4, 1]));
  c.drawRect(Skia.XYWHRect(cx - corkW - 4, top, corkW * 2 + 8, corkH + 4), shade);
  c.restore();
}

function drawBubble(c: SkCanvas, R: number) {
  const p = Skia.Paint();
  p.setShader(radial(0, 0, R, ['rgba(255,232,170,0.04)', 'rgba(255,220,140,0.12)', 'rgba(232,184,92,0.55)', 'rgba(255,236,186,0.95)'], [0, 0.6, 0.88, 1]));
  c.drawCircle(0, 0, R, p);
  c.drawCircle(-R * 0.36, -R * 0.4, R * 0.22, blurred('#FFFFFF', 0.85, R * 0.1));
  c.drawArc(Skia.XYWHRect(-R * 0.7, -R * 0.7, R * 1.4, R * 1.4), 20, 70, false, stroke('#FFF6DA', R * 0.08, 0.6));
}

export function bakeLayers(g: Layout, f: Fonts, scale: number): Layers | null {
  const bg = bake(0, 0, g.w, g.h, Math.min(2, scale), (c) => drawRoom(c, g));
  const card = bake(g.cardX - 2, g.cardY - 2, g.cardW + 4, g.cardH + 4, scale, (c) => drawCard(c, g, f));
  const bx = g.cx - g.body - 30;
  const bottle = bake(bx, g.lipY - 10, (g.body + 30) * 2, g.h + 80 - g.lipY, Math.min(2.5, scale), (c) => drawBottle(c, g, f));
  const cork = bake(g.cx - g.corkW - 6, g.lipY - g.corkH - 4, g.corkW * 2 + 12, g.corkH + 34, scale, (c) => drawCork(c, g));
  const bubble = bake(-24, -24, 48, 48, scale, (c) => drawBubble(c, 22));
  if (!bg || !card || !bottle || !cork || !bubble) return null;
  return { bg, card, bottle, cork, bubble };
}

/** The wire cage: a cap plate on the cork, four legs to a ring under the lip, the twisted key. */
function drawCage(c: SkCanvas, g: Layout, inch: number, loose: number, rock: number, dy: number) {
  'worklet';
  const { cx, corkW } = g;
  const top = g.lipY - g.corkH - inch + dy;
  const ringY = g.lipY + 9 + loose * 2 + dy;
  const ringR = g.neck + 3.5;
  const plateR = corkW * 0.66;
  const wire = stroke('#DDE3E7', 1.1, 0.9);
  const back = stroke('#7E878E', 0.9, 0.55);
  const bow = -1 + loose * 4;
  // Back legs first, then the ring, then the front legs.
  const legs = [-0.96, 0.96, -0.5, 0.5];
  for (let k = 0; k < 4; k += 1) {
    const s = legs[k];
    const x0 = cx + s * plateR;
    const x1 = cx + s * ringR;
    const path = Skia.PathBuilder.Make()
      .moveTo(x0, top + 3)
      .quadTo(cx + s * (corkW + bow), top + g.corkH * 0.55, x1, ringY)
      .build();
    c.drawPath(path, k < 2 ? back : wire);
    if (k === 1) {
      c.drawArc(Skia.XYWHRect(cx - ringR, ringY - 3.5, ringR * 2, 7), 0, 180, false, stroke('#C9D0D5', 1.6));
    }
  }
  // The key: a twisted loop hanging at the front of the ring, spinning as it loosens.
  const spin = loose * Math.PI * 6 + rock * 0.05;
  const kw = 1 + 4 * Math.abs(Math.cos(spin));
  c.drawLine(cx, ringY + 3, cx, ringY + 7, stroke('#E9EDF0', 1.6));
  c.drawOval(Skia.XYWHRect(cx - kw, ringY + 7, kw * 2, 9), stroke('#F2F5F7', 1.4));
  // The cap plate on top with its embossed ring.
  const pp = Skia.Paint();
  pp.setShader(linear(cx - plateR, 0, cx + plateR, 0, ['#8C6A2C', '#F3D88E', '#C49A48', '#7A5A22']));
  c.drawOval(Skia.XYWHRect(cx - plateR, top - 1.5, plateR * 2, 7), pp);
  c.drawOval(Skia.XYWHRect(cx - plateR * 0.6, top + 0.2, plateR * 1.2, 3.6), stroke('#FFF1C2', 0.7, 0.7));
  const mark = rock * 0.12 + loose * 2.4;
  c.drawCircle(cx + Math.cos(mark) * plateR * 0.75, top + 2 + Math.sin(mark) * 2, 1, paint('#5A4012', 0.8));
}

export function drawFrame(c: SkCanvas, g: Layout, L: Layers, p: Pose, effect: SkRuntimeEffect | null, p3: number) {
  'worklet';
  const ip = Skia.Paint();
  drawBaked(c, L.bg, ip);
  // The card, lifted.
  const lift = p.lift;
  const cardDY = -12 * lift;
  const r = Skia.RRectXY(Skia.XYWHRect(g.cardX, g.cardY + 2, g.cardW, g.cardH), 22, 22);
  c.drawRRect(r, blurred('#020604', 0.4 - 0.15 * lift, 3 + 2 * lift));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.cardX + 6, g.cardY + 16 + 10 * lift, g.cardW - 12, g.cardH), 22, 22), blurred('#020604', 0.35 + 0.2 * lift, 18 + 14 * lift));
  c.save();
  const s = 1 + 0.015 * lift;
  c.translate(g.cx, g.cardY + g.cardH / 2 + cardDY);
  c.scale(s, s);
  c.translate(-g.cx, -(g.cardY + g.cardH / 2));
  drawBaked(c, L.card, ip);
  if (p.sheen > 0 && p.sheen < 1) {
    c.save();
    c.clipRRect(Skia.RRectXY(Skia.XYWHRect(g.cardX, g.cardY, g.cardW, g.cardH), 22, 22), ClipOp.Intersect, true);
    const x = g.cardX - 120 + (g.cardW + 240) * p.sheen;
    const sh = Skia.Paint();
    sh.setShader(linear(x - 60, g.cardY, x + 60, g.cardY + g.cardH, ['rgba(255,230,160,0)', 'rgba(255,226,150,0.5)', 'rgba(255,255,255,0.35)', 'rgba(255,230,160,0)'], [0, 0.45, 0.55, 1]));
    sh.setBlendMode(BlendMode.Plus);
    c.drawRect(Skia.XYWHRect(g.cardX, g.cardY, g.cardW, g.cardH), sh);
    c.restore();
  }
  c.restore();
  // Cork under the bottle (its shaft hides in the neck), the bottle, the cage.
  const dy = p.drop;
  c.save();
  c.translate(g.cx, g.lipY + dy);
  c.rotate(p.rock * 0.8, 0, 0);
  c.translate(-g.cx, -g.lipY);
  if (p.corked) drawBaked(c, L.cork, ip, 0, -p.inch);
  c.restore();
  drawBaked(c, L.bottle, ip, 0, dy);
  if (p.corked) drawCage(c, g, p.inch, p.loose, p.rock, dy);
  // Foam.
  if (effect && p.bounds[2] > 0) {
    const u: number[] = [];
    for (let i = 0; i < 48; i += 1) u.push(p.balls[i]);
    u.push(p3);
    const fp = Skia.Paint();
    fp.setShader(effect.makeShader(u));
    c.drawRect(Skia.XYWHRect(p.bounds[0], p.bounds[1], p.bounds[2], p.bounds[3]), fp);
  }
  // CO2 puff.
  for (let i = 0; i < p.fog.length; i += 4) {
    if (p.fog[i + 3] > 0.005) c.drawCircle(p.fog[i], p.fog[i + 1], p.fog[i + 2], blurred('#E6F1EC', p.fog[i + 3], p.fog[i + 2] * 0.45));
  }
  // The cork in flight, with its cap.
  if (p.cork.length === 3) {
    c.save();
    c.translate(p.cork[0], p.cork[1]);
    c.rotate(p.cork[2], 0, 0);
    c.translate(-g.cx, -(g.lipY - g.corkH * 0.5));
    drawBaked(c, L.cork, ip);
    drawCage(c, g, 0, 1, 0, 0);
    c.restore();
  }
  // Gold bubbles.
  const bp = Skia.Paint();
  for (let i = 0; i < p.bubbles.length; i += 5) {
    const R = p.bubbles[i + 2];
    bp.setAlphaf(p.bubbles[i + 3]);
    c.drawImageRectOptions(L.bubble.img, Skia.XYWHRect(0, 0, L.bubble.img.width(), L.bubble.img.height()), Skia.XYWHRect(p.bubbles[i] - R * 1.09, p.bubbles[i + 1] - R * 1.09, R * 2.18, R * 2.18), FilterMode.Linear, MipmapMode.None, bp);
  }
  // A soft touch marker while the loop twists the cork.
  if (p.finger > 0.01) {
    const fy = g.lipY - g.corkH * 0.55 - p.inch;
    c.drawCircle(g.cx + p.rock * 1.6, fy, 24, blurred('#FFFFFF', 0.16 * p.finger, 6));
    c.drawCircle(g.cx + p.rock * 1.6, fy, 22, stroke('#FFFFFF', 1.2, 0.35 * p.finger));
  }
}
