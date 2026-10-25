import { ClipOp, FilterMode, MipmapMode, type SkCanvas, type SkFont, type SkImage, Skia } from '@shopify/react-native-skia';

import { PITCH_WORDS, clamp, pitchIndex, riseOf, softRound } from './spiritMotion';
import { fill, grain, linear, radial, rgba, spaced, stroke, textWidth } from './spiritPaint';

/*
 The spirit level's art. Baked once: the warm paper background, the pitch card with the house's
 walls, and the level itself (anodised yellow body, machined end caps, etched ruler, the vial
 window with its lime liquid and centre marks). Per frame: the roof at the current pitch, the
 readouts, the level's shadow and image under its rotation, the bubble and the vial's glass.
 */

export const INK = 0x2a241c;
const LIME = 0xc6e23f;

export type Fonts = { kicker: SkFont; degrees: SkFont; rise: SkFont; small: SkFont; pill: SkFont; tiny: SkFont; etch: SkFont };

export type Geo = {
  w: number;
  h: number;
  card: { x: number; y: number; w: number; h: number };
  /** The house: centre x, ground y, half width, wall height. */
  house: { x: number; y: number; hw: number; wh: number };
  cx: number;
  cy: number;
  /** Level length and body thickness, and the vial's inner half length and bubble size. */
  len: number;
  thick: number;
  vialHalf: number;
  bubbleLen: number;
  bubbleH: number;
  travel: number;
  chipsTop: number;
  ctaTop: number;
};

export const PAD = 24;

/** Size of the baked level image, in points. */
export function levelBox(g: { len: number; thick: number }) {
  return { w: g.len + PAD * 2, h: g.thick * 1.18 + PAD * 2 };
}

export function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const cardY = top + 150;
  const cardH = clamp(h * 0.2, 140, 176);
  const card = { x: 16, y: cardY, w: w - 32, h: cardH };
  const ctaTop = h - Math.max(bottom, 16) - 8 - 56;
  const chipsTop = ctaTop - 16 - 40;
  const heroTop = cardY + cardH + 12;
  const heroBottom = chipsTop - 12;
  const heroH = heroBottom - heroTop;
  const sin44 = Math.sin((44 * Math.PI) / 180);
  const len = Math.max(200, Math.min(w - 56, 340, (heroH - 56) / sin44));
  const thick = len * 0.135;
  const vialHalf = (len * 0.44 - 12) / 2;
  const bubbleLen = thick * 0.74;
  const hw = Math.min(card.w * 0.25, 86);
  return {
    w,
    h,
    card,
    house: { x: card.x + card.w * 0.7, y: card.y + card.h - 22, hw, wh: card.h * 0.24 },
    cx: w / 2,
    cy: (heroTop + heroBottom) / 2,
    len,
    thick,
    vialHalf,
    bubbleLen,
    bubbleH: thick * 0.3,
    travel: vialHalf - bubbleLen / 2 - 3,
    chipsTop,
    ctaTop,
  };
}

/** The tube's centre line height (local y) at local x: arched, highest in the middle. */
export function tubeY(g: Geo, x: number) {
  'worklet';
  const u = x / g.vialHalf;
  return -g.thick * 0.02 + 3.2 * u * u;
}

// ——— Baked ———

export function drawBackground(canvas: SkCanvas, g: Geo, f: Fonts) {
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), linear(0, 0, 0, g.h, [rgba(0xf5f0e6), rgba(0xece4d5), rgba(0xdfd5c2)], [0, 0.55, 1]));
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), radial(g.cx, g.cy, g.w * 0.9, [rgba(0xfffaf0, 0.55), rgba(0xfffaf0, 0)], [0, 1]));
  grain(canvas, 0, 0, g.w, g.h, 0.09, 7);
  const { card, house } = g;
  const rr = Skia.RRectXY(Skia.XYWHRect(card.x, card.y, card.w, card.h), 24, 24);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(card.x, card.y + 12, card.w, card.h), 24, 24), fill(0x5a4a30, 0.14, 18));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(card.x, card.y + 1.5, card.w, card.h), 24, 24), fill(0x5a4a30, 0.18, 1.5));
  canvas.drawRRect(rr, linear(0, card.y, 0, card.y + card.h, [rgba(0xfffdf8), rgba(0xfbf7ef), rgba(0xf4eee2)], [0, 0.6, 1]));
  canvas.drawRRect(rr, stroke(0xffffff, 1, 0.9));
  spaced(canvas, 'PITCH', card.x + 20, card.y + 28, f.kicker, fill(INK, 0.45), 1.8);
  // Ground, a hedge and the walls: everything of the house but its roof.
  const gy = house.y;
  canvas.drawLine(card.x + card.w * 0.42, gy, card.x + card.w - 18, gy, stroke(INK, 1.2, 0.25));
  for (let i = 0; i < 7; i += 1) {
    const x = house.x + house.hw + 12 + i * 7;
    if (x > card.x + card.w - 22) break;
    canvas.drawCircle(x, gy - 6 - (i % 2) * 2, 7, fill(i % 2 ? 0x7c8f4e : 0x6b7f42, 0.9));
  }
  const left = house.x - house.hw;
  canvas.drawRect(Skia.XYWHRect(left, gy - house.wh, house.hw * 2, house.wh), linear(left, 0, left + house.hw * 2, 0, [rgba(0xe9dcc6), rgba(0xf1e6d3), rgba(0xdccbb0)], [0, 0.5, 1]));
  canvas.drawRect(Skia.XYWHRect(left, gy - house.wh, house.hw * 2, house.wh), stroke(INK, 1, 0.35));
  const dw = house.hw * 0.26;
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(house.x - dw / 2, gy - house.wh * 0.72, dw, house.wh * 0.72), 2, 2), fill(0x8a5a3a));
  canvas.drawCircle(house.x + dw * 0.28, gy - house.wh * 0.36, 1.3, fill(0xf2c66b));
  for (const side of [-1, 1]) {
    const wx = house.x + side * house.hw * 0.58 - house.hw * 0.17;
    const r = Skia.XYWHRect(wx, gy - house.wh * 0.74, house.hw * 0.34, house.wh * 0.36);
    canvas.drawRect(r, linear(0, r.y, 0, r.y + r.height, [rgba(0xbcd3dc), rgba(0x8fb0bf), rgba(0x6f93a4)], [0, 0.5, 1]));
    canvas.drawRect(r, stroke(0xfdfaf3, 1.5));
  }
}

/** The level in its own frame: origin at the body's centre, baked with PAD around it. */
export function drawLevelBody(canvas: SkCanvas, g: Geo, f: Fonts) {
  canvas.save();
  canvas.translate(g.len / 2 + PAD, g.thick / 2 + PAD);
  const L = g.len;
  const T = g.thick;
  const hl = L / 2;
  const ht = T / 2;
  const side = T * 0.18;
  // Side face below the top face: the extrusion's thickness.
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-hl, -ht + 2, L, T + side), 7, 7), linear(0, ht - 4, 0, ht + side, [rgba(0xb07714), rgba(0x8a5a0c), rgba(0x5c3a06)], [0, 0.6, 1]));
  // Anodised top face.
  const body = Skia.RRectXY(Skia.XYWHRect(-hl, -ht, L, T), 6, 6);
  canvas.drawRRect(body, linear(0, -ht, 0, ht, [rgba(0xffe189), rgba(0xf6bf3a), rgba(0xe8a522), rgba(0xc98714)], [0, 0.3, 0.72, 1]));
  // Brushed grain along the length.
  canvas.save();
  canvas.clipRRect(body, ClipOp.Intersect, true);
  const brush = Skia.Paint();
  brush.setShader(Skia.Shader.MakeFractalNoise(0.004, 0.9, 2, 5, 0, 0));
  brush.setAlphaf(0.12);
  canvas.drawRect(Skia.XYWHRect(-hl, -ht, L, T), brush);
  canvas.restore();
  canvas.drawLine(-hl + 6, -ht + 0.8, hl - 6, -ht + 0.8, stroke(0xfff6d6, 1.2, 0.9));
  canvas.drawLine(-hl + 6, ht - 0.6, hl - 6, ht - 0.6, stroke(0x7a4c08, 1, 0.5));
  // Etched ruler along the top edge, every 4 pt, longer every 20.
  const etch = Skia.PathBuilder.Make();
  const cap = T * 0.62;
  for (let x = -hl + cap + 6; x <= hl - cap - 6; x += 4) {
    const k = Math.round((x + hl) / 4);
    const len = k % 5 === 0 ? 6 : 3;
    etch.moveTo(x, -ht + 2).lineTo(x, -ht + 2 + len);
  }
  canvas.drawPath(etch.build(), stroke(0x3a2606, 0.7, 0.55));
  spaced(canvas, 'TRUELINE 600', -hl + cap + 10, ht - 7, f.etch, fill(0x3a2606, 0.6), 1.4);
  const mm = '0.5 mm/m';
  canvas.drawText(mm, hl - cap - 10 - textWidth(f.etch, mm), ht - 7, fill(0x3a2606, 0.6), f.etch);
  // Machined end caps with rubber bumpers.
  for (const s of [-1, 1]) {
    const x0 = s < 0 ? -hl : hl - cap;
    const r = Skia.RRectXY(Skia.XYWHRect(x0, -ht - 1, cap, T + side + 1), 6, 6);
    canvas.drawRRect(r, linear(0, -ht, 0, ht + side, [rgba(0x3a3d42), rgba(0x24272b), rgba(0x15171a)], [0, 0.5, 1]));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0 + (s < 0 ? 5 : 0), -ht + 3, cap - 5, T - 6), 3, 3), linear(0, -ht, 0, ht, [rgba(0xeef0f2), rgba(0xb6bbc2), rgba(0x8a9099)], [0, 0.5, 1]));
    for (let i = 0; i < 4; i += 1) {
      const gx = x0 + (s < 0 ? 9 : 4) + i * ((cap - 13) / 3);
      canvas.drawLine(gx, -ht + 7, gx, ht - 7, stroke(0x5d636b, 1, 0.55));
    }
    canvas.drawLine(x0 + (s < 0 ? 5 : 0) + 2, -ht + 3.8, x0 + cap - (s < 0 ? 2 : 7), -ht + 3.8, stroke(0xffffff, 0.8, 0.8));
  }
  // The vial window: a dark bezel, the lime liquid, the centre marks.
  const vw = g.vialHalf * 2 + 12;
  const vh = T * 0.62;
  const win = Skia.RRectXY(Skia.XYWHRect(-vw / 2, -vh / 2, vw, vh), vh / 2, vh / 2);
  canvas.drawRRect(win, fill(0x3b2a0a, 0.85));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-vw / 2 + 2, -vh / 2 + 2, vw - 4, vh - 4), vh / 2 - 2, vh / 2 - 2), linear(0, -vh / 2, 0, vh / 2, [rgba(0xf4f0e3), rgba(0xd9d2bd), rgba(0xb5ac92)], [0, 0.5, 1]));
  const tube = Skia.PathBuilder.Make();
  const th = T * 0.4;
  const steps = 24;
  for (let i = 0; i <= steps; i += 1) {
    const x = -g.vialHalf + (i / steps) * g.vialHalf * 2;
    const y = tubeY(g, x) - th / 2;
    if (i === 0) tube.moveTo(x, y);
    else tube.lineTo(x, y);
  }
  for (let i = steps; i >= 0; i -= 1) {
    const x = -g.vialHalf + (i / steps) * g.vialHalf * 2;
    tube.lineTo(x, tubeY(g, x) + th / 2);
  }
  tube.close();
  const tubePath = tube.build();
  canvas.drawPath(tubePath, linear(0, -th / 2, 0, th / 2 + 4, [rgba(0xe8f78a), rgba(LIME), rgba(0x93b81f), rgba(0x6f8f14)], [0, 0.35, 0.75, 1]));
  canvas.drawPath(tubePath, stroke(0x4d6410, 1, 0.55));
  for (const s of [-1, 1]) {
    const x = s * (g.bubbleLen / 2 + 2);
    canvas.drawLine(x, tubeY(g, x) - th / 2 - 2, x, tubeY(g, x) + th / 2 + 2, stroke(0x1f1a10, 1.6, 0.9));
    const x2 = s * (g.bubbleLen / 2 + 2 + g.travel * 0.45);
    canvas.drawLine(x2, tubeY(g, x2) - th / 2, x2, tubeY(g, x2) - th / 2 + 4, stroke(0x1f1a10, 1, 0.6));
    canvas.drawLine(x2, tubeY(g, x2) + th / 2 - 4, x2, tubeY(g, x2) + th / 2, stroke(0x1f1a10, 1, 0.6));
  }
  canvas.restore();
}

// ——— Per frame ———

/** Two-digit odometer; the tens column hides when zero. Returns the width drawn. */
function odometer(canvas: SkCanvas, value: number, x: number, base: number, font: SkFont, digitW: number, lineH: number, color: number) {
  'worklet';
  const r = softRound(Math.max(0, value));
  const units = r;
  const um = ((units % 10) + 10) % 10;
  const tens = Math.floor(units / 10) + Math.max(0, um - 9);
  const lead = clamp(tens, 0, 1);
  const shift = -digitW * (1 - lead);
  canvas.save();
  canvas.clipRect(Skia.XYWHRect(x - 4, base - lineH * 0.8, digitW * 2 + 8, lineH), ClipOp.Intersect, true);
  const cols = [tens, units];
  for (let c = 0; c < 2; c += 1) {
    const d = ((cols[c] % 10) + 10) % 10;
    const i = Math.floor(d);
    const fr = d - i;
    const cx = x + shift + c * digitW;
    for (let j = 0; j < 2; j += 1) {
      const digit = (i + j) % 10;
      if (c === 0 && digit === 0 && cols[0] < 1.5) continue;
      const a = (c === 0 ? lead : 1) * (1 - Math.min(1, Math.abs(j - fr)));
      if (a <= 0.01) continue;
      const s = `${digit}`;
      canvas.drawText(s, cx + (digitW - textWidth(font, s)) / 2, base + (j - fr) * lineH * 0.8, fill(color, a), font);
    }
  }
  canvas.restore();
  return digitW * 2 + shift;
}

export function drawReadout(canvas: SkCanvas, g: Geo, f: Fonts, digitW: number, riseW: number, angle: number) {
  'worklet';
  const { card } = g;
  const a = Math.max(0, angle);
  const base = card.y + 82;
  const w = odometer(canvas, a, card.x + 18, base, f.degrees, digitW, 64, INK);
  canvas.drawText('°', card.x + 18 + w + 1, base, fill(INK, 0.9), f.degrees);
  const b2 = card.y + 110;
  const rw = odometer(canvas, riseOf(a), card.x + 20, b2, f.rise, riseW, 26, INK);
  canvas.drawText('/12 pitch', card.x + 20 + rw + 1, b2, fill(INK, 0.6), f.rise);
  // The pitch word in a pill whose width follows the rolling word.
  const k = pitchIndex(a);
  const i = clamp(Math.floor(k), 0, PITCH_WORDS.length - 1);
  const fr = k - i;
  const w0 = textWidth(f.pill, PITCH_WORDS[i]);
  const w1 = i + 1 < PITCH_WORDS.length ? textWidth(f.pill, PITCH_WORDS[i + 1]) : w0;
  const pw = w0 + (w1 - w0) * fr + 22;
  const py = card.y + card.h - 44;
  const pill = Skia.RRectXY(Skia.XYWHRect(card.x + 18, py, pw, 26), 13, 13);
  canvas.drawRRect(pill, fill(0xe8862a, 0.14));
  canvas.save();
  canvas.clipRRect(pill, ClipOp.Intersect, true);
  for (let j = 0; j < 2; j += 1) {
    const word = PITCH_WORDS[i + j];
    if (word === undefined) continue;
    const al = 1 - Math.min(1, Math.abs(j - fr));
    if (al <= 0.01) continue;
    canvas.drawText(word, card.x + 29, py + 17.5 + (j - fr) * 18, fill(0xa8520f, al), f.pill);
  }
  canvas.restore();
}

/** Gable, roof slab with shingle rows, and the pitch arc, for the current angle. */
export function drawRoof(canvas: SkCanvas, g: Geo, f: Fonts, angle: number) {
  'worklet';
  const { house } = g;
  const a = (clamp(angle, 0, 50) * Math.PI) / 180;
  const tan = Math.min(Math.tan(a), (g.card.h - 38 - house.wh) / house.hw);
  const top = house.y - house.wh;
  const ridgeY = top - house.hw * tan;
  const over = 12;
  const lx = house.x - house.hw - over;
  const rx = house.x + house.hw + over;
  const ey = top + over * tan;
  const slab = 7 / Math.cos(a);
  // Gable wall.
  const gable = Skia.PathBuilder.Make().moveTo(house.x - house.hw, top).lineTo(house.x, ridgeY).lineTo(house.x + house.hw, top).close().build();
  canvas.drawPath(gable, fill(0xe4d5bb));
  // Roof slab.
  const roof = Skia.PathBuilder.Make()
    .moveTo(lx, ey - slab)
    .lineTo(house.x, ridgeY - slab)
    .lineTo(rx, ey - slab)
    .lineTo(rx, ey)
    .lineTo(house.x, ridgeY)
    .lineTo(lx, ey)
    .close()
    .build();
  canvas.drawPath(roof, linear(0, ridgeY - slab, 0, ey, [rgba(0x5a6470), rgba(0x434c57), rgba(0x2f363e)], [0, 0.5, 1]));
  canvas.drawLine(lx, ey - slab, house.x, ridgeY - slab, stroke(0xa6b0bb, 1, 0.7));
  canvas.drawLine(house.x, ridgeY - slab, rx, ey - slab, stroke(0x8994a0, 1, 0.5));
  // Pitch arc and its run line at the left eave.
  const ax = house.x - house.hw;
  const arcR = 26;
  const run = stroke(INK, 1, 0.35);
  run.setPathEffect(Skia.PathEffect.MakeDash([3, 3], 0));
  canvas.drawLine(ax, top, ax + arcR + 14, top, run);
  if (angle > 0.5) {
    const arc = Skia.PathBuilder.Make().addArc(Skia.XYWHRect(ax - arcR, top - arcR, arcR * 2, arcR * 2), -((a * 180) / Math.PI), (a * 180) / Math.PI).build();
    canvas.drawPath(arc, stroke(0xe8862a, 1.6, 0.95));
  }
}

/** Shadow, body image, bubble and glass, in the level's rotated frame. */
export function drawLevel(canvas: SkCanvas, g: Geo, image: SkImage, scale: number, angle: number, bubble: number, speed: number) {
  'worklet';
  const rot = -angle;
  canvas.save();
  canvas.translate(g.cx, g.cy + 18);
  canvas.rotate(rot, 0, 0);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-g.len / 2, -g.thick / 2, g.len, g.thick), 8, 8), fill(0x4a3a20, 0.22, 14));
  canvas.restore();
  canvas.save();
  canvas.translate(g.cx, g.cy);
  canvas.rotate(rot, 0, 0);
  const bw = g.len + PAD * 2;
  const bh = g.thick * 1.18 + PAD * 2;
  canvas.drawImageRectOptions(image, Skia.XYWHRect(0, 0, bw * scale, bh * scale), Skia.XYWHRect(-g.len / 2 - PAD, -g.thick / 2 - PAD, bw, bh), FilterMode.Linear, MipmapMode.None, null);
  // Bubble: travels to the higher end, stretched by speed, squashed against the ends.
  let x = bubble * g.travel;
  let len = g.bubbleLen;
  let h = g.bubbleH;
  const stretch = clamp((Math.abs(speed) * g.travel) / 700, 0, 0.32);
  len *= 1 + stretch;
  h *= 1 - stretch * 0.4;
  const limit = g.vialHalf - 2;
  if (Math.abs(x) + len / 2 > limit) {
    const over = Math.abs(x) + len / 2 - limit;
    const squash = Math.min(over, len * 0.32);
    len -= squash;
    h *= 1 + (squash / g.bubbleLen) * 0.35;
    x = Math.sign(x) * (limit - len / 2);
  }
  const y = tubeY(g, x) - g.thick * 0.2 + h / 2 + 1;
  const r = Skia.RRectXY(Skia.XYWHRect(x - len / 2, y - h / 2, len, h), h / 2, h / 2);
  canvas.drawRRect(r, linear(0, y - h / 2, 0, y + h / 2, [rgba(0xfbffe9, 0.98), rgba(0xeef9b8, 0.95), rgba(0xd2ea6a, 0.9)], [0, 0.55, 1]));
  canvas.drawRRect(r, stroke(0x56700f, 1.3, 0.75));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - len * 0.32, y - h / 2 + 1.6, len * 0.5, h * 0.3), h * 0.15, h * 0.15), fill(0xffffff, 0.9));
  canvas.drawLine(x - len / 2 + h * 0.4, y + h / 2 + 1.2, x + len / 2 - h * 0.4, y + h / 2 + 1.2, stroke(0x4d6410, 1, 0.35));
  // Glass over the tube: a bright streak along the top and a faint one below.
  const th = g.thick * 0.4;
  const streak = Skia.PathBuilder.Make();
  for (let i = 0; i <= 16; i += 1) {
    const sx = -g.vialHalf * 0.86 + (i / 16) * g.vialHalf * 1.72;
    const sy = tubeY(g, sx) - th / 2 + 2.4;
    if (i === 0) streak.moveTo(sx, sy);
    else streak.lineTo(sx, sy);
  }
  canvas.drawPath(streak.build(), stroke(0xffffff, 1.6, 0.6));
  canvas.restore();
}
