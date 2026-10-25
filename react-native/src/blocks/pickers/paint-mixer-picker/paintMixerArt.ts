import {
  BlurStyle,
  ClipOp,
  PaintStyle,
  Skia,
  StrokeCap,
  TileMode,
  type SkCanvas,
  type SkFont,
} from '@shopify/react-native-skia';

import { type Geo, type Mix, NAME_LIST, POTS, lrv, potCenter, toHex } from './paintMixerMotion';

/*
 Drawing for Paint Mixer Picker. Recorded once: the linen table with grain, the wall card's
 shadows and its room (floor, skirting, a framed print, a sideboard, a lamp) over a wall left
 empty for the mixed colour. Recorded per frame: the five paint tins (lifted when picked), drops in
 the air, splashes, the stirring stick, and the colour's name, code and swatch.
 */

export type Fonts = { label: SkFont; name: SkFont; code: SkFont; chip: SkFont };
/** The colour's label: current and previous name, seconds since it changed, the colour before. */
export type Label = { cur: number; prev: number; since: number; prevRgb: number[] };

const INK = '#2B2620';
const INK_SOFT = '#7A7064';
export const CARD_R = 22;

function fill(color: string) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  return p;
}

function blurred(color: string, sigma: number) {
  'worklet';
  const p = fill(color);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function widthOf(font: SkFont, text: string) {
  'worklet';
  const ws = font.getGlyphWidths(font.getGlyphIDs(text));
  let s = 0;
  for (let i = 0; i < ws.length; i += 1) s += ws[i];
  return s;
}

export function cardRect(g: Geo) {
  'worklet';
  return Skia.RRectXY(Skia.XYWHRect(24, g.cardY, g.w - 48, g.cardH), CARD_R, CARD_R);
}

/** Table, grain and the card's two shadows. */
export function drawTable(c: SkCanvas, g: Geo) {
  const bg = fill('#EDE6DA');
  bg.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, 0),
      Skia.Point(0, g.h),
      ['#F3EDE3', '#EBE3D6', '#E2D8C8'].map((h) => Skia.Color(h)),
      [0, 0.55, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), bg);
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.85, 0.85, 2, 7, 0, 0));
  grain.setAlphaf(0.05);
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), grain);
  // A faint linen weave.
  const weave = fill('rgba(120,100,70,0.035)');
  for (let y = 0; y < g.h; y += 3) c.drawRect(Skia.XYWHRect(0, y, g.w, 1), weave);
  const card = cardRect(g);
  c.save();
  c.translate(0, 14);
  c.drawRRect(card, blurred('rgba(70,52,30,0.16)', 18));
  c.restore();
  c.save();
  c.translate(0, 1.5);
  c.drawRRect(card, blurred('rgba(70,52,30,0.22)', 2));
  c.restore();
}

/** The room in the card, drawn over the wall colour (which shows through where nothing is drawn). */
export function drawRoom(c: SkCanvas, g: Geo, chip: SkFont) {
  const x0 = 24;
  const y0 = g.cardY;
  const w = g.w - 48;
  const h = g.cardH;
  const u = g.u;
  c.save();
  c.clipRRect(cardRect(g), ClipOp.Intersect, true);
  // Light from the lamp on the right, darker corners.
  const light = Skia.Paint();
  light.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(x0 + w * 0.8, y0 + h * 0.35),
      w * 0.7,
      [Skia.Color('rgba(255,248,232,0.30)'), Skia.Color('rgba(255,248,232,0.08)'), Skia.Color('rgba(40,30,20,0.16)')],
      [0, 0.45, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(x0, y0, w, h), light);
  // Floor and skirting.
  const floorY = y0 + h * 0.76;
  const floor = fill('#A57A52');
  floor.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, floorY),
      Skia.Point(0, y0 + h),
      ['#C29466', '#A57A52', '#80593A'].map((v) => Skia.Color(v)),
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(x0, floorY, w, h * 0.24), floor);
  const plank = Skia.Paint();
  plank.setAntiAlias(true);
  plank.setStyle(PaintStyle.Stroke);
  plank.setStrokeWidth(0.8);
  plank.setColor(Skia.Color('rgba(70,44,24,0.35)'));
  for (let i = -6; i <= 6; i += 1) {
    const p = Skia.PathBuilder.Make();
    p.moveTo(x0 + w / 2 + i * 26 * u, floorY);
    p.lineTo(x0 + w / 2 + i * 44 * u, y0 + h);
    c.drawPath(p.build(), plank);
  }
  c.drawRect(Skia.XYWHRect(x0, floorY - 7 * u, w, 7 * u), fill('#F4EFE6'));
  c.drawRect(Skia.XYWHRect(x0, floorY, w, 3 * u), fill('rgba(40,26,14,0.22)'));
  // A framed print.
  const fx = x0 + 34 * u;
  const fy = y0 + 26 * u;
  const fw = 70 * u;
  const fh = 52 * u;
  c.drawRect(Skia.XYWHRect(fx + 2, fy + 5, fw, fh), blurred('rgba(30,20,10,0.25)', 4));
  c.drawRect(Skia.XYWHRect(fx, fy, fw, fh), fill('#3A322B'));
  c.drawRect(Skia.XYWHRect(fx + 3, fy + 3, fw - 6, fh - 6), fill('#F7F3EA'));
  c.drawCircle(fx + fw * 0.4, fy + fh * 0.55, 11 * u, fill('#C0704F'));
  c.drawCircle(fx + fw * 0.6, fy + fh * 0.45, 8 * u, fill('#2F4858'));
  // A sideboard with a vase.
  const sx = x0 + w * 0.42;
  const sw = w * 0.34;
  const sy = floorY - 34 * u;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(sx + 3, sy + 6, sw, 30 * u), 3, 3), blurred('rgba(30,20,10,0.25)', 5));
  const wood = fill('#6E5440');
  wood.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(0, sy), Skia.Point(0, sy + 30 * u), ['#836550', '#5E4636'].map((v) => Skia.Color(v)), null, TileMode.Clamp),
  );
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(sx, sy, sw, 30 * u), 3, 3), wood);
  c.drawRect(Skia.XYWHRect(sx + sw / 2 - 0.5, sy + 4 * u, 1, 22 * u), fill('rgba(30,20,10,0.35)'));
  for (const lx of [sx + 6 * u, sx + sw - 8 * u]) c.drawRect(Skia.XYWHRect(lx, sy + 30 * u, 2 * u, 7 * u), fill('#4A3628'));
  const vx = sx + sw * 0.25;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(vx - 7 * u, sy - 22 * u, 14 * u, 22 * u), 6 * u, 6 * u), fill('#E9E2D6'));
  const stem = Skia.Paint();
  stem.setAntiAlias(true);
  stem.setStyle(PaintStyle.Stroke);
  stem.setStrokeWidth(1.2);
  stem.setStrokeCap(StrokeCap.Round);
  stem.setColor(Skia.Color('#5E6B48'));
  for (const [dx, dy] of [
    [-12, -48],
    [4, -56],
    [14, -42],
  ]) {
    const p = Skia.PathBuilder.Make();
    p.moveTo(vx, sy - 20 * u);
    p.quadTo(vx + dx * 0.3 * u, sy + dy * 0.6 * u, vx + dx * u, sy + dy * u);
    c.drawPath(p.build(), stem);
    c.drawOval(Skia.XYWHRect(vx + dx * u - 3 * u, sy + dy * u - 2 * u, 6 * u, 4 * u), fill('#6F7F55'));
  }
  // A floor lamp on the right.
  const lx = x0 + w * 0.86;
  const lampLine = Skia.Paint();
  lampLine.setAntiAlias(true);
  lampLine.setStyle(PaintStyle.Stroke);
  lampLine.setStrokeWidth(1.6);
  lampLine.setColor(Skia.Color('#3A322B'));
  const pole = Skia.PathBuilder.Make();
  pole.moveTo(lx, floorY + 4 * u);
  pole.lineTo(lx, y0 + 40 * u);
  c.drawPath(pole.build(), lampLine);
  const shade = Skia.PathBuilder.Make();
  shade.moveTo(lx - 9 * u, y0 + 24 * u);
  shade.lineTo(lx + 9 * u, y0 + 24 * u);
  shade.lineTo(lx + 14 * u, y0 + 42 * u);
  shade.lineTo(lx - 14 * u, y0 + 42 * u);
  shade.close();
  c.drawPath(shade.build(), fill('#F1E7D4'));
  c.drawOval(Skia.XYWHRect(lx - 30 * u, y0 + 36 * u, 60 * u, 40 * u), blurred('rgba(255,236,190,0.35)', 14));
  // The room's tag.
  const tag = 'Living room · North light';
  const tw = widthOf(chip, tag) + 20;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0 + w - tw - 12, y0 + 12, tw, 22), 11, 11), fill('rgba(255,252,246,0.72)'));
  c.drawText(tag, x0 + w - tw - 2, y0 + 27, fill(INK), chip);
  c.restore();
  // Inner edge highlight.
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1);
  edge.setColor(Skia.Color('rgba(255,255,255,0.35)'));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(24.5, g.cardY + 0.5, g.w - 49, g.cardH - 1), CARD_R - 0.5, CARD_R - 0.5), edge);
}

function lighten(hex: string, k: number) {
  'worklet';
  const r = parseInt(hex.slice(1, 3), 16);
  const gg = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  const f = (v: number) => Math.round(k >= 0 ? v + (255 - v) * k : v * (1 + k));
  return `rgb(${f(r)},${f(gg)},${f(b)})`;
}

/** A glossy paint drop of pot i, h = height towards the camera (0 … 1). */
function drawDrop(c: SkCanvas, g: Geo, x: number, y: number, h: number, pot: number) {
  'worklet';
  const u = g.u;
  const col = POTS[pot].color;
  const s = 1 + 0.45 * h;
  const shadow = blurred('rgba(40,30,20,0.28)', 2 + 4 * h);
  shadow.setAlphaf(0.28 * (1 - 0.45 * h));
  c.drawCircle(x + 10 * h * u, y + 16 * h * u, 8 * u, shadow);
  const r = 8 * u * s;
  const body = fill(col);
  body.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(x - r * 0.35, y - r * 0.4),
      r * 1.5,
      [Skia.Color(lighten(col, 0.35)), Skia.Color(col), Skia.Color(lighten(col, -0.35))],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  c.drawCircle(x, y, r, body);
  c.drawCircle(x - r * 0.35, y - r * 0.4, r * 0.22, fill('rgba(255,255,255,0.75)'));
}

function rollEase(x: number) {
  'worklet';
  const k = x < 0 ? 0 : x > 1 ? 1 : x;
  return 1 - (1 - k) * (1 - k) * (1 - k);
}

function codeLine(rgb: number[]) {
  'worklet';
  return `${toHex(rgb)}   ·   LRV ${lrv(rgb)}`;
}

/** Tins, drops, splashes, the stick, and the colour's name, code and swatch. */
export function drawDynamic(c: SkCanvas, g: Geo, m: Mix, shown: number[], label: Label, fonts: Fonts) {
  'worklet';
  const u = g.u;
  for (let i = 0; i < POTS.length; i += 1) {
    const [px, py] = potCenter(g, i);
    const l = m.lift[i];
    const r = g.potR * (1 + 0.06 * l);
    c.drawCircle(px + 2 * u, py + (4 + 6 * l) * u, r, blurred('rgba(60,44,26,0.20)', 5 + 5 * l));
    c.drawCircle(px, py + 1.5, r, blurred('rgba(60,44,26,0.28)', 1.5));
    const tin = fill('#D8D4CD');
    tin.setShader(
      Skia.Shader.MakeLinearGradient(
        Skia.Point(px - r, py - r),
        Skia.Point(px + r, py + r),
        ['#F6F4F0', '#B9B3AA', '#E8E5DF', '#9C968D'].map((v) => Skia.Color(v)),
        [0, 0.4, 0.65, 1],
        TileMode.Clamp,
      ),
    );
    c.drawCircle(px, py, r, tin);
    c.drawCircle(px, py, r * 0.86, fill('rgba(60,50,40,0.35)'));
    const col = POTS[i].color;
    const paint = fill(col);
    paint.setShader(
      Skia.Shader.MakeRadialGradient(
        Skia.Point(px - r * 0.3, py - r * 0.3),
        r,
        [Skia.Color(lighten(col, 0.18)), Skia.Color(col), Skia.Color(lighten(col, -0.2))],
        [0, 0.6, 1],
        TileMode.Clamp,
      ),
    );
    c.drawCircle(px, py, r * 0.8, paint);
    c.drawOval(Skia.XYWHRect(px - r * 0.48, py - r * 0.55, r * 0.5, r * 0.26), fill('rgba(255,255,255,0.38)'));
    // A drip over the rim.
    c.drawCircle(px + r * 0.62, py + r * 0.58, r * 0.16, fill(col));
    const name = POTS[i].name;
    const nameP = fill(INK_SOFT);
    c.drawText(name, px - widthOf(fonts.label, name) / 2, py + r + 16, nameP, fonts.label);
  }

  for (const s of m.splashes) {
    const k = Math.min(1, s.age / 0.45);
    const e = 1 - (1 - k) * (1 - k) * (1 - k);
    for (let j = 0; j < 7; j += 1) {
      const a = (j / 7) * Math.PI * 2 + j * 0.37;
      const dist = (6 + (22 + (j % 3) * 7) * e) * u;
      c.drawCircle(s.x + Math.cos(a) * dist, s.y + Math.sin(a) * dist, 2.8 * u * (1 - k), fill(POTS[s.pot].color));
    }
  }

  if (m.stick[2] > 0.01) {
    const a = m.stick[2];
    const sx = m.stick[0];
    const sy = m.stick[1];
    const sh = blurred('rgba(40,28,14,0.3)', 5);
    sh.setAlphaf(0.3 * a);
    c.drawCircle(sx + 7 * u, sy + 12 * u, 9 * u, sh);
    const wood = fill('#C9A577');
    wood.setShader(
      Skia.Shader.MakeRadialGradient(Skia.Point(sx - 3 * u, sy - 3 * u), 12 * u, ['#E6CBA0', '#C9A577', '#9A7448'].map((v) => Skia.Color(v)), [0, 0.6, 1], TileMode.Clamp),
    );
    wood.setAlphaf(a);
    c.drawCircle(sx, sy, 9 * u * (0.9 + 0.1 * a), wood);
    const ring = Skia.Paint();
    ring.setAntiAlias(true);
    ring.setStyle(PaintStyle.Stroke);
    ring.setStrokeWidth(0.8);
    ring.setColor(Skia.Color('rgba(110,78,44,0.45)'));
    ring.setAlphaf(0.45 * a);
    c.drawCircle(sx, sy, 5 * u, ring);
    c.drawCircle(sx, sy, 2.4 * u, ring);
  }

  for (const f of m.flights) drawDrop(c, g, f.x, f.y, f.h, f.pot);

  // The colour: swatch, name, code and light reflectance.
  const py = g.panelY;
  const col = `rgb(${Math.round(shown[0] * 255)},${Math.round(shown[1] * 255)},${Math.round(shown[2] * 255)})`;
  c.drawCircle(46, py + 22, 22, blurred('rgba(60,44,26,0.22)', 4));
  c.drawCircle(46, py + 22, 22, fill(col));
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1.5);
  edge.setColor(Skia.Color('rgba(255,255,255,0.7)'));
  c.drawCircle(46, py + 22, 20.5, edge);
  // A vertical roll on change: the old line slides up 10 pt and fades, the new one rises from
  // 10 pt below 60 ms later (ease-out, 260 ms each), both clipped to their line.
  const tx = 82;
  const out = rollEase(label.since / 0.26);
  const inn = rollEase((label.since - 0.06) / 0.26);
  const lines: [string, string, SkFont, string, number, number][] = [
    [NAME_LIST[label.prev], NAME_LIST[label.cur], fonts.name, INK, py + 22, 28],
    [codeLine(label.prevRgb), codeLine(shown), fonts.code, INK_SOFT, py + 42, 16],
  ];
  for (const [before, after, font, color, base, lh] of lines) {
    c.save();
    c.clipRect(Skia.XYWHRect(tx - 2, base - lh * 0.82, g.w - tx - 20, lh), ClipOp.Intersect, true);
    if (out < 1) {
      const p = fill(color);
      p.setAlphaf(1 - out);
      c.drawText(before, tx, base - 10 * out, p, font);
    }
    if (inn > 0) {
      const p = fill(color);
      p.setAlphaf(inn);
      c.drawText(after, tx, base + 10 * (1 - inn), p, font);
    }
    c.restore();
  }
}
