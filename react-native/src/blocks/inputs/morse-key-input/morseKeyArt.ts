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
  Skia,
  StrokeCap,
  TileMode,
} from '@shopify/react-native-skia';

import { type Pose, type Strip, clamp01, hash } from './morseKeyMotion';

/*
 Morse Key Input — the drawing. One Skia canvas: a baked desk (green leather, the telegram card
 with its typed clue and answer boxes, the key's walnut base with its contact post, trunnion and
 binding posts, and the wires to the register), a baked register (the brass box the tape comes out
 of), and every frame: the tape and its ink, the torn strip falling away, the register's reel, the
 lever with its knob and spring, contact sparks, and the decoded letters.
*/

export type Fonts = { type: SkFont; typeBold: SkFont; slot: SkFont; small: SkFont; chip: SkFont };

export type Scene = {
  w: number;
  h: number;
  cardX: number;
  cardY: number;
  cardW: number;
  cardH: number;
  slotY: number;
  slotW: number;
  slotH: number;
  slotX: number[];
  /** Tape: centre line, height; print head x (where the tape leaves the register). */
  tapeY: number;
  tapeH: number;
  headX: number;
  /** Resting tape length (a blank leader out of the register). */
  leader: number;
  regX: number;
  regY: number;
  regW: number;
  regH: number;
  baseTop: number;
  baseX: number;
  baseW: number;
  leverY: number;
  pivotX: number;
  knobX: number;
  contactX: number;
  springX: number;
  /** Lever travel at full press, degrees. */
  tilt: number;
};

const TILT = 3.3;

export function makeScene(w: number, h: number, top: number, bottom: number): Scene {
  const cardX = 16;
  const cardY = top + 108;
  const cardW = w - 32;
  const cardH = 176;
  const slotW = 52;
  const slotH = 62;
  const slotY = cardY + cardH - slotH - 18;
  const slotX = [cardX + 20, cardX + 20 + slotW + 10];
  const tapeY = cardY + cardH + 58;
  const tapeH = 30;
  const regW = 78;
  const regX = w - regW - 14;
  const regY = tapeY - 40;
  const regH = 94;
  const headX = regX + 2;
  const baseTop = h - bottom - 60 - 128;
  const baseX = 22;
  const baseW = w - 44;
  const leverY = baseTop - 56;
  const pivotX = w - 112;
  return {
    w,
    h,
    cardX,
    cardY,
    cardW,
    cardH,
    slotY,
    slotW,
    slotH,
    slotX,
    tapeY,
    tapeH,
    headX,
    leader: headX - 22,
    regX,
    regY,
    regW,
    regH,
    baseTop,
    baseX,
    baseW,
    leverY,
    pivotX,
    knobX: 82,
    contactX: Math.round(w * 0.46),
    springX: Math.round(w * 0.46) + 54,
    tilt: TILT,
  };
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
  if (alpha < 1) p.setAlphaf(Math.max(0, alpha));
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

function shaded(shader: ReturnType<typeof linear>) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(shader);
  return p;
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

function textWidth(font: SkFont, text: string) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let sum = 0;
  for (let i = 0; i < widths.length; i += 1) sum += widths[i];
  return sum;
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

const BRASS = ['#6E4F22', '#C9A35C', '#F0D9A0', '#B88E45', '#5E421B'];
const BRASS_POS = [0, 0.3, 0.45, 0.7, 1];

/** Desk, card, key base and its fixed parts, wires. */
export function bakeDesk(s: Scene, fonts: Fonts, scale: number) {
  return bake(s.w, s.h, scale, (c) => {
    const { w, h } = s;
    // Green leather desk with a lamp's pool of light and a vignette.
    c.drawRect(Skia.XYWHRect(0, 0, w, h), shaded(linear(0, 0, 0, h, ['#1A2A25', '#14221E', '#101B18', '#0C1513'], [0, 0.4, 0.75, 1])));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), shaded(radial(w * 0.42, s.baseTop - 60, h * 0.55, ['rgba(214,190,130,0.16)', 'rgba(214,190,130,0.05)', 'rgba(0,0,0,0)'], [0, 0.5, 1])));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), grain(0.14, 0.7, 0.7, 31));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), shaded(radial(w * 0.5, h * 0.5, h * 0.72, ['rgba(0,0,0,0)', 'rgba(2,6,5,0.25)', 'rgba(2,6,5,0.62)'], [0.4, 0.75, 1])));

    // The telegram card: cream paper, two shadows, a red-brown printed header and typed clue.
    const { cardX: x, cardY: y, cardW: cw, cardH: ch } = s;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y + 14, cw, ch), 14, 14), blurred('#000000', 0.45, 16));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y + 1.5, cw, ch), 14, 14), blurred('#000000', 0.5, 1.5));
    const card = Skia.RRectXY(Skia.XYWHRect(x, y, cw, ch), 14, 14);
    c.drawRRect(card, shaded(linear(x, y, x + cw * 0.3, y + ch, ['#F3EAD6', '#EADFC6', '#E0D2B5'])));
    c.save();
    c.clipRRect(card, ClipOp.Intersect, true);
    c.drawRect(Skia.XYWHRect(x, y, cw, ch), grain(0.16, 1.4, 1.4, 7));
    c.restore();
    const red = '#9A3B2A';
    c.drawText('TELEGRAM', x + 20, y + 28, paint(red), fonts.typeBold);
    const no = 'PUZZLE No. 27';
    c.drawText(no, x + cw - 20 - textWidth(fonts.typeBold, no), y + 28, paint(red), fonts.typeBold);
    const rule = paint(red, 0.7);
    rule.setStrokeWidth(1.2);
    c.drawLine(x + 20, y + 38, x + cw - 20, y + 38, rule);
    rule.setStrokeWidth(0.6);
    c.drawLine(x + 20, y + 41, x + cw - 20, y + 41, rule);
    const ink = paint('#2D2A26', 0.88);
    c.drawText('THE OPERATOR IS WAITING STOP', x + 20, y + 62, ink, fonts.type);
    c.drawText('SAY HELLO IN TWO LETTERS STOP', x + 20, y + 80, ink, fonts.type);
    // Answer boxes: typed outlines with a baseline.
    for (const sx of s.slotX) {
      const box = Skia.RRectXY(Skia.XYWHRect(sx, s.slotY, s.slotW, s.slotH), 8, 8);
      c.drawRRect(box, paint('#D9CBAE', 0.55));
      const edge = paint('#8C7A5C', 0.6);
      edge.setStyle(PaintStyle.Stroke);
      edge.setStrokeWidth(1);
      c.drawRRect(box, edge);
      const base = paint('#8C7A5C', 0.8);
      base.setStrokeWidth(1.5);
      c.drawLine(sx + 12, s.slotY + s.slotH - 12, sx + s.slotW - 12, s.slotY + s.slotH - 12, base);
    }

    // Wires from the binding posts up into the register: cloth-covered, one red, one green.
    const postA = s.baseX + s.baseW - 44;
    const postB = s.baseX + s.baseW - 24;
    const regBottom = s.regY + s.regH;
    for (const [px, col, dx] of [
      [postA, '#7A2E24', -10],
      [postB, '#2F5A43', 8],
    ] as const) {
      const b = Skia.PathBuilder.Make();
      b.moveTo(px, s.baseTop - 20);
      b.cubicTo(px - 30 + dx, s.baseTop - 120, s.regX + s.regW / 2 + dx - 20, regBottom + 90, s.regX + s.regW / 2 + dx, regBottom - 4);
      const path = b.build();
      const shadow = blurred('#000000', 0.45, 3);
      shadow.setStyle(PaintStyle.Stroke);
      shadow.setStrokeWidth(4);
      c.save();
      c.translate(3, 5);
      c.drawPath(path, shadow);
      c.restore();
      const wire = paint(col);
      wire.setStyle(PaintStyle.Stroke);
      wire.setStrokeWidth(3.4);
      wire.setStrokeCap(StrokeCap.Round);
      c.drawPath(path, wire);
      const hi = paint('#FFFFFF', 0.18);
      hi.setStyle(PaintStyle.Stroke);
      hi.setStrokeWidth(1);
      c.save();
      c.translate(-0.8, -0.8);
      c.drawPath(path, hi);
      c.restore();
    }

    // The key's base: a walnut block seen from the front, its top face catching the lamp.
    const bx = s.baseX;
    const bw = s.baseW;
    const top = s.baseTop;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(bx + 4, top + 30, bw - 8, 44), 10, 10), blurred('#000000', 0.6, 14));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(bx, top + 2, bw, 58), 6, 6), blurred('#000000', 0.6, 1.5));
    const face = Skia.RRectXY(Skia.XYWHRect(bx, top + 12, bw, 46), 6, 6);
    c.drawRRect(face, shaded(linear(0, top + 12, 0, top + 58, ['#5A3720', '#45291A', '#2F1C12'])));
    c.save();
    c.clipRRect(face, ClipOp.Intersect, true);
    c.drawRect(Skia.XYWHRect(bx, top + 12, bw, 46), grain(0.4, 0.012, 0.5, 41));
    c.restore();
    const topFace = Skia.RRectXY(Skia.XYWHRect(bx + 3, top, bw - 6, 14), 5, 5);
    c.drawRRect(topFace, shaded(linear(0, top, 0, top + 14, ['#8A5C3A', '#734A2D', '#5E3B23'])));
    c.save();
    c.clipRRect(topFace, ClipOp.Intersect, true);
    c.drawRect(Skia.XYWHRect(bx, top, bw, 14), grain(0.35, 0.012, 0.6, 43));
    c.restore();
    c.drawLine(bx + 8, top + 0.6, bx + bw - 8, top + 0.6, paint('#FFD9AE', 0.35));
    // A small engraved brass plate on the front.
    const plateW = 92;
    const px0 = bx + bw / 2 - plateW / 2;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(px0, top + 26, plateW, 18), 3, 3), shaded(linear(px0, 0, px0 + plateW, 0, BRASS, BRASS_POS)));
    const plate = 'DOT DASH  No. 3';
    c.drawText(plate, px0 + plateW / 2 - textWidth(fonts.small, plate) / 2, top + 38.5, paint('#3B2A12', 0.85), fonts.small);
    // Lower contact: a silver post with a domed tip.
    const cx = s.contactX;
    const tipY = s.leverY + 16;
    c.drawRect(Skia.XYWHRect(cx - 5, tipY + 3, 10, top - tipY), shaded(linear(cx - 5, 0, cx + 5, 0, ['#6F7378', '#E4E7EA', '#9EA3A8', '#55595E'], [0, 0.35, 0.65, 1])));
    c.drawOval(Skia.XYWHRect(cx - 7, top - 4, 14, 7), shaded(linear(cx - 7, 0, cx + 7, 0, BRASS, BRASS_POS)));
    c.drawOval(Skia.XYWHRect(cx - 4, tipY, 8, 6), shaded(radial(cx - 1, tipY + 2, 5, ['#FFFFFF', '#B9BEC4', '#6B7075'])));
    // Trunnion: a brass upright with the axle's screw head.
    const tx = s.pivotX;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(tx - 9, s.leverY - 12, 18, top - s.leverY + 14), 4, 4), shaded(linear(tx - 9, 0, tx + 9, 0, BRASS, BRASS_POS)));
    // Binding posts: knurled brass with a dark collar.
    for (const px of [postA, postB]) {
      c.drawRect(Skia.XYWHRect(px - 6, top - 22, 12, 24), shaded(linear(px - 6, 0, px + 6, 0, BRASS, BRASS_POS)));
      const knurl = paint('#4A3412', 0.5);
      knurl.setStrokeWidth(0.8);
      for (let k = -4; k <= 4; k += 2) c.drawLine(px + k, top - 20, px + k, top - 12, knurl);
      c.drawRect(Skia.XYWHRect(px - 7, top - 6, 14, 5), paint('#2A1E12'));
    }
  });
}

/** Where the register image sits (it only covers what it draws). */
export function registerRect(s: Scene) {
  'worklet';
  return { x: s.regX - 50, y: s.regY - 30, w: s.regW + 100, h: s.regH + 90 };
}

/** The register the tape comes out of. */
export function bakeRegister(s: Scene, fonts: Fonts, scale: number) {
  const r = registerRect(s);
  return bake(r.w, r.h, scale, (c) => {
    c.translate(-r.x, -r.y);
    const { regX: x, regY: y, regW: w, regH: h } = s;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 2, y + 16, w + 4, h), 10, 10), blurred('#000000', 0.55, 14));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y + 1.5, w, h), 10, 10), blurred('#000000', 0.6, 1.5));
    const body = Skia.RRectXY(Skia.XYWHRect(x, y, w, h), 10, 10);
    c.drawRRect(body, shaded(linear(0, y, 0, y + h, ['#2B3330', '#1C2321', '#141A18'])));
    const trim = shaded(linear(x, 0, x + w, 0, BRASS, BRASS_POS));
    trim.setStyle(PaintStyle.Stroke);
    trim.setStrokeWidth(2);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 1, y + 1, w - 2, h - 2), 9, 9), trim);
    // The window over the paper reel (the reel itself is drawn each frame).
    const wy = y + 10;
    c.drawCircle(x + w / 2, wy + 24, 25, paint('#0A0E0D'));
    const ring = shaded(linear(x, 0, x + w, 0, BRASS, BRASS_POS));
    ring.setStyle(PaintStyle.Stroke);
    ring.setStrokeWidth(3);
    c.drawCircle(x + w / 2, wy + 24, 26, ring);
    // The slot the tape comes out of, at the tape's height on the left edge.
    c.drawRect(Skia.XYWHRect(x - 1, s.tapeY - s.tapeH / 2 - 3, 8, s.tapeH + 6), paint('#0A0E0D'));
    const label = 'REGISTER';
    c.drawText(label, x + w / 2 - textWidth(fonts.small, label) / 2, y + h - 12, paint('#D8BC80', 0.75), fonts.small);
  });
}

/** One strip of tape whose free end is `len` to the left of the head, with its ink. */
function drawStrip(c: SkCanvas, s: Scene, strip: Strip, torn: boolean) {
  'worklet';
  if (strip.len < 0.5) return;
  const x1 = s.headX + 4;
  const x0 = s.headX - strip.len;
  const top = s.tapeY - s.tapeH / 2;
  const bottom = s.tapeY + s.tapeH / 2;
  // The free end is torn: a jagged edge, seeded so it never flickers.
  const b = Skia.PathBuilder.Make();
  b.moveTo(x1, top);
  const jag = torn ? 1 : 0;
  b.lineTo(x0 + 2, top);
  for (let i = 0; i <= 8; i += 1) {
    const y = top + (s.tapeH * i) / 8;
    b.lineTo(x0 + (hash(i + 3.3) - 0.5) * 4 + (jag ? (hash(i * 7.1) - 0.5) * 3 : 0), y);
  }
  b.lineTo(x1, bottom);
  b.close();
  const path = b.build();
  c.save();
  c.translate(0, 5);
  c.drawPath(path, blurred('#000000', 0.45, 4));
  c.restore();
  c.drawPath(path, shaded(linear(0, top, 0, bottom, ['#F4ECD9', '#EDE3CC', '#E2D5B8'])));
  const edge = paint('#B9A984', 0.6);
  edge.setStrokeWidth(0.8);
  c.drawLine(Math.max(x0, -10), top + 0.5, x1, top + 0.5, edge);
  c.drawLine(Math.max(x0, -10), bottom - 0.5, x1, bottom - 0.5, edge);
  // Ink: each mark sits where its run has carried it, a little bleed around the stroke.
  const ink = Skia.PathBuilder.Make();
  const m = strip.marks;
  for (let i = 0; i + 1 < m.length; i += 2) {
    const a = s.headX - (strip.run - m[i]);
    const e = s.headX - (strip.run - m[i + 1]);
    const mid = (a + e) / 2;
    const half = Math.max(3.8, Math.abs(e - a) / 2);
    if (mid + half < -10 || mid - half > s.headX) continue;
    ink.addRRect(Skia.RRectXY(Skia.XYWHRect(mid - half, s.tapeY - 3.8, half * 2, 7.6), 3.8, 3.8));
  }
  const inkPath = ink.build();
  c.drawPath(inkPath, blurred('#1D2230', 0.3, 1.6));
  c.drawPath(inkPath, paint('#1D2230', 0.92));
}

/** The lever group, rotated about the trunnion: bar, knob, upper contact screw. */
function drawLever(c: SkCanvas, s: Scene, depth: number) {
  'worklet';
  const angle = s.tilt * depth;
  const y = s.leverY;
  // Its shadow on the base's top face moves with it.
  const sh = blurred('#000000', 0.4, 6);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(s.knobX - 20, s.baseTop - 3, s.pivotX - s.knobX + 40, 8), 4, 4), sh);
  c.save();
  c.rotate(-angle, s.pivotX, y);
  // Bar: brass with a bright upper edge.
  const bar = Skia.RRectXY(Skia.XYWHRect(s.knobX - 18, y - 7, s.pivotX - s.knobX + 46, 14), 4, 4);
  c.drawRRect(bar, shaded(linear(0, y - 7, 0, y + 7, ['#F2DDA6', '#C9A15A', '#94702F', '#5F451B'], [0, 0.35, 0.7, 1])));
  c.drawLine(s.knobX - 14, y - 6.4, s.pivotX + 24, y - 6.4, paint('#FFF3D0', 0.5));
  // Axle boss at the pivot.
  c.drawCircle(s.pivotX, y, 8, shaded(radial(s.pivotX - 2, y - 2, 9, ['#F5E2AE', '#B08A44', '#5E451B'])));
  c.drawCircle(s.pivotX, y, 2.2, paint('#3A2A10'));
  // Upper contact screw under the bar.
  c.drawRect(Skia.XYWHRect(s.contactX - 3.5, y + 7, 7, 9), shaded(linear(s.contactX - 3.5, 0, s.contactX + 3.5, 0, ['#7A7F84', '#E8EBEE', '#62666B'])));
  c.drawOval(Skia.XYWHRect(s.contactX - 4, y + 13, 8, 4), paint('#C8CDD2'));
  // Knob: a shaft and a black bakelite mushroom with a soft top highlight.
  const kx = s.knobX;
  c.drawRect(Skia.XYWHRect(kx - 5, y - 24, 10, 20), shaded(linear(kx - 5, 0, kx + 5, 0, BRASS, BRASS_POS)));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(kx - 29, y - 52, 58, 30), 13, 13), shaded(linear(kx - 29, 0, kx + 29, 0, ['#0F0D0C', '#2C2724', '#3A3430', '#1A1715', '#0B0A09'], [0, 0.3, 0.42, 0.7, 1])));
  c.drawOval(Skia.XYWHRect(kx - 33, y - 64, 66, 22), shaded(radial(kx - 10, y - 59, 36, ['#4A433E', '#24201D', '#121010'])));
  c.drawOval(Skia.XYWHRect(kx - 20, y - 62, 26, 7), paint('#FFFFFF', 0.16));
  c.restore();
}

function drawSpring(c: SkCanvas, s: Scene, depth: number) {
  'worklet';
  const drop = (s.pivotX - s.springX) * Math.sin((s.tilt * depth * Math.PI) / 180);
  const top = s.leverY + 6 + drop;
  const bottom = s.baseTop + 1;
  const turns = 9;
  const b = Skia.PathBuilder.Make();
  b.moveTo(s.springX, top);
  for (let i = 0; i <= turns * 2; i += 1) {
    const yy = top + ((bottom - top) * i) / (turns * 2);
    b.lineTo(s.springX + (i % 2 === 0 ? -6 : 6), yy);
  }
  const path = b.build();
  const coil = paint('#B8934E');
  coil.setStyle(PaintStyle.Stroke);
  coil.setStrokeWidth(1.6);
  c.drawPath(path, coil);
  const hi = paint('#FFF0C8', 0.45);
  hi.setStyle(PaintStyle.Stroke);
  hi.setStrokeWidth(0.6);
  c.drawPath(path, hi);
}

function drawSpark(c: SkCanvas, s: Scene, pose: Pose) {
  'worklet';
  const x = s.contactX;
  const y = s.leverY + 17;
  const age = pose.sparkAge;
  if (age >= 0 && age < 0.25) {
    const flash = Math.exp(-age * 24);
    const glow = Skia.Paint();
    glow.setShader(radial(x, y, 34, ['rgba(200,225,255,0.95)', 'rgba(120,170,255,0.35)', 'rgba(80,120,255,0)'], [0, 0.35, 1]));
    glow.setAlphaf(flash);
    glow.setBlendMode(BlendMode.Plus);
    c.drawCircle(x, y, 34, glow);
    const lines = Skia.PathBuilder.Make();
    for (let i = 0; i < 9; i += 1) {
      const ang = (-170 + 160 * hash(i * 2.3)) * (Math.PI / 180);
      const sp = 70 + 160 * hash(i * 5.1);
      const life = 0.1 + 0.12 * hash(i * 9.7);
      if (age > life) continue;
      const a0 = Math.max(0, age - 0.025);
      lines.moveTo(x + Math.cos(ang) * sp * a0, y + Math.sin(ang) * sp * a0 + 300 * a0 * a0);
      lines.lineTo(x + Math.cos(ang) * sp * age, y + Math.sin(ang) * sp * age + 300 * age * age);
    }
    const sp = paint('#E6F0FF');
    sp.setStyle(PaintStyle.Stroke);
    sp.setStrokeWidth(1.3);
    sp.setStrokeCap(StrokeCap.Round);
    sp.setBlendMode(BlendMode.Plus);
    c.drawPath(lines.build(), sp);
  }
  if (pose.hum > 0) {
    const hum = Skia.Paint();
    hum.setShader(radial(x, y, 12, ['rgba(170,200,255,0.55)', 'rgba(170,200,255,0)']));
    hum.setBlendMode(BlendMode.Plus);
    c.drawCircle(x, y, 12, hum);
  }
}

function drawReel(c: SkCanvas, s: Scene, run: number) {
  'worklet';
  const cx = s.regX + s.regW / 2;
  const cy = s.regY + 34;
  c.drawCircle(cx, cy, 21, shaded(radial(cx - 4, cy - 4, 24, ['#F2EAD8', '#E2D5BA', '#BFAF8E'])));
  // Paper layers and three spokes turning as the tape pays out.
  const rings = paint('#A8987A', 0.45);
  rings.setStyle(PaintStyle.Stroke);
  rings.setStrokeWidth(0.5);
  for (let r = 9; r < 21; r += 3) c.drawCircle(cx, cy, r, rings);
  c.save();
  c.rotate((-run / 21) * (180 / Math.PI), cx, cy);
  const spoke = paint('#6E5C3E');
  spoke.setStrokeWidth(2);
  for (let i = 0; i < 3; i += 1) {
    const a = (i * 2 * Math.PI) / 3;
    c.drawLine(cx, cy, cx + Math.cos(a) * 8, cy + Math.sin(a) * 8, spoke);
  }
  c.restore();
  c.drawCircle(cx, cy, 3, shaded(radial(cx - 1, cy - 1, 4, ['#F5E2AE', '#8A6A2E'])));
  // Glass reflection over the window.
  const glass = Skia.Paint();
  glass.setShader(linear(cx - 24, cy - 24, cx + 10, cy + 10, ['rgba(255,255,255,0.22)', 'rgba(255,255,255,0.04)', 'rgba(255,255,255,0)'], [0, 0.45, 1]));
  c.drawCircle(cx, cy, 24, glass);
}

function drawAnswer(c: SkCanvas, s: Scene, pose: Pose, fonts: Fonts) {
  'worklet';
  // Decoded letters roll up into their boxes; clearing, they roll on up and out.
  for (let i = 0; i < 2; i += 1) {
    const sl = pose.slots[i];
    if (!sl.ch || sl.in <= 0.001) continue;
    const x = s.slotX[i] + s.slotW / 2 - textWidth(fonts.slot, sl.ch) / 2;
    const base = s.slotY + s.slotH - 17;
    const dy = (1 - sl.in) * 18 - sl.out * 20;
    const a = clamp01(sl.in * 1.4) * (1 - sl.out);
    if (a <= 0.001) continue;
    c.save();
    c.clipRect(Skia.XYWHRect(s.slotX[i] + 2, s.slotY + 2, s.slotW - 4, s.slotH - 4), ClipOp.Intersect, true);
    const stamp = 1 + 0.12 * (1 - sl.in);
    c.translate(x + textWidth(fonts.slot, sl.ch) / 2, base + dy);
    c.scale(stamp, stamp);
    c.drawText(sl.ch, -textWidth(fonts.slot, sl.ch) / 2, 0, paint('#23201C', a), fonts.slot);
    c.restore();
  }
  // The letter in progress: its dots and dashes next to the boxes.
  if (pose.bufferAlpha > 0 && pose.buffer.length > 0) {
    const y = s.slotY + s.slotH / 2;
    let x = s.slotX[1] + s.slotW + 18;
    const p = paint('#9A3B2A', 0.9);
    for (let i = 0; i < pose.buffer.length; i += 1) {
      if (pose.buffer[i] === '.') {
        c.drawCircle(x + 3, y, 3.4, p);
        x += 14;
      } else {
        c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y - 3, 18, 6), 3, 3), p);
        x += 26;
      }
    }
  }
  // Result chip on the right of the row.
  if (pose.chip > 0.005) {
    const label = pose.correct ? 'Correct · +40' : 'Not quite';
    const tw = textWidth(fonts.chip, label);
    const w = tw + 28;
    const x = s.cardX + s.cardW - 20 - w;
    const y = s.slotY + s.slotH / 2 - 15 + (1 - pose.chip) * 6;
    const col = pose.correct ? '#2F6B4A' : '#9A3B2A';
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, 30), 15, 15), paint(col, 0.92 * pose.chip));
    c.drawText(label, x + 14, y + 19.5, paint('#F6EEDC', pose.chip), fonts.chip);
  }
}

export type Layers = { desk: SkImage; register: SkImage };

/** One frame. */
export function drawFrame(c: SkCanvas, s: Scene, layers: Layers, pose: Pose, fonts: Fonts) {
  'worklet';
  const full = Skia.XYWHRect(0, 0, s.w, s.h);
  const ip = Skia.Paint();
  drawImg(c, layers.desk, Skia.XYWHRect(0, 0, layers.desk.width(), layers.desk.height()), full, ip);
  drawAnswer(c, s, pose, fonts);
  drawStrip(c, s, pose.tape, true);
  if (pose.fall > 0 && pose.fall < 1) {
    // Torn off at the head: it drops, swings away from the tear and fades.
    const k = pose.fall;
    const alpha = 1 - clamp01((k - 0.45) / 0.55);
    const layer = Skia.Paint();
    layer.setAlphaf(alpha);
    c.saveLayer(layer);
    c.translate(-30 * k, 300 * k * k);
    c.rotate(-10 * k, s.headX, s.tapeY);
    drawStrip(c, s, pose.torn, true);
    c.restore();
  }
  const registerAt = registerRect(s);
  drawImg(c, layers.register, Skia.XYWHRect(0, 0, layers.register.width(), layers.register.height()), Skia.XYWHRect(registerAt.x, registerAt.y, registerAt.w, registerAt.h), ip);
  drawReel(c, s, pose.tape.run);
  // The pen: an inked wheel in the slot, down on the tape while the contact is closed.
  const penY = s.tapeY - 2 + 2 * pose.pen;
  c.drawCircle(s.headX + 4, penY, 4, shaded(radial(s.headX + 3, penY - 1, 5, ['#8A8F96', '#2A2E36'])));
  drawSpring(c, s, pose.depth);
  drawLever(c, s, pose.depth);
  drawSpark(c, s, pose);
}
