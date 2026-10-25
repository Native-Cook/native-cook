import { BlendMode, BlurStyle, type SkCanvas, type SkFont, type SkPicture, Skia, TileMode } from '@shopify/react-native-skia';

import { PPCM, WAVE_LENGTH } from './tapeMeasureMotion';

/*
 The tape measure, seen from above: a rubber-armoured case with a yellow lacquered face, chrome
 screws and a belt clip at the left; the yellow tape runs out of its mouth to the right, cupped
 (darker at both edges, a gloss line just above the middle), with millimetre ticks on both edges
 and a number at every centimetre (every tenth in red). Numbers grow toward the case: the hook end
 is off screen to the right. A red hairline just outside the mouth reads the value.

 Recorded once: the paper background and the case. Per frame: the tape's body, shadow, ticks and
 the ~12 visible numbers (only the visible range is built), then the case, then the hairline.
 */

export type Geo = {
  w: number;
  h: number;
  tapeY: number;
  tapeH: number;
  caseX: number;
  caseY: number;
  caseS: number;
  mouthX: number;
  readX: number;
};

export function makeGeo(w: number, h: number): Geo {
  const tapeY = h * 0.6;
  const caseS = Math.min(170, w * 0.43);
  const caseX = 14;
  const mouthX = caseX + caseS - 4;
  return { w, h, tapeY, tapeH: 72, caseX, caseY: tapeY - 24 - caseS / 2, caseS, mouthX, readX: mouthX + 22 };
}

export type Fonts = { tick: SkFont; tickRed: SkFont; brand: SkFont; small: SkFont };

function hexA(hex: number, a = 1) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 0xff) / 255, ((hex >> 8) & 0xff) / 255, (hex & 0xff) / 255, a);
}

function fill(c: Float32Array) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(c);
  return p;
}

function line(c: Float32Array, width: number) {
  'worklet';
  const p = fill(c);
  p.setStyle(1);
  p.setStrokeWidth(width);
  return p;
}

function blurred(c: Float32Array, sigma: number) {
  'worklet';
  const p = fill(c);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: Float32Array[], pos: number[]) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors, pos, TileMode.Clamp));
  return p;
}

function textWidth(font: SkFont, s: string) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(s));
  let total = 0;
  for (let i = 0; i < widths.length; i++) total += widths[i];
  return total;
}

function rr(x: number, y: number, w: number, h: number, r: number) {
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}

function screw(canvas: SkCanvas, x: number, y: number, r: number, turn: number) {
  canvas.drawCircle(x, y + 0.8, r + 0.6, blurred(hexA(0x5a3a00, 0.45), 0.8));
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeRadialGradient({ x: x - r * 0.4, y: y - r * 0.5 }, r * 1.7, [hexA(0xffffff), hexA(0xb9bec5), hexA(0x5d636b)], [0, 0.45, 1], TileMode.Clamp));
  canvas.drawCircle(x, y, r, p);
  const slot = line(hexA(0x2b2e33, 0.85), 1);
  canvas.save();
  canvas.rotate(turn, x, y);
  canvas.drawLine(x - r * 0.6, y, x + r * 0.6, y, slot);
  canvas.drawLine(x, y - r * 0.6, x, y + r * 0.6, slot);
  canvas.restore();
}

/** Paper background with grain and a soft window light. */
export function recordBackground(g: Geo): SkPicture {
  const rec = Skia.PictureRecorder();
  const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, g.w, g.h));
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), linear(0, 0, 0, g.h, [hexA(0xf4f1eb), hexA(0xece7de), hexA(0xe3ddd1)], [0, 0.55, 1]));
  const light = Skia.Paint();
  light.setShader(Skia.Shader.MakeRadialGradient({ x: g.w * 0.15, y: g.h * 0.05 }, g.w * 1.1, [hexA(0xfffdf8, 0.7), hexA(0xfffdf8, 0)], [0, 1], TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), light);
  // A faint table edge behind the tape, so the case and tape sit on something.
  canvas.drawRect(Skia.XYWHRect(0, g.tapeY - g.caseS * 0.95, g.w, g.h), linear(0, g.tapeY - g.caseS * 0.95, 0, g.h, [hexA(0xd9d2c4, 0), hexA(0xd9d2c4, 0.35), hexA(0xd2cabb, 0.5)], [0, 0.3, 1]));
  const noise = Skia.Paint();
  noise.setShader(Skia.Shader.MakeFractalNoise(1.1, 1.1, 2, 3, 0, 0));
  noise.setAlphaf(0.06);
  noise.setBlendMode(BlendMode.Multiply);
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), noise);
  return rec.finishRecordingAsPicture();
}

/** The case: belt clip, rubber armour, yellow face, screws, lock button, the chrome mouth. */
export function recordCase(g: Geo, fonts: Fonts): SkPicture {
  const rec = Skia.PictureRecorder();
  const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, g.w, g.h));
  const { caseX: x, caseY: y, caseS: s } = g;
  const cx = x + s / 2;
  const cy = y + s / 2;
  // Shadows: soft ambient and a tight contact one.
  canvas.drawRRect(rr(x + 4, y + 14, s - 4, s - 6, 36), blurred(hexA(0x3b2c14, 0.24), 14));
  canvas.drawRRect(rr(x + 1, y + 3, s - 2, s - 2, 36), blurred(hexA(0x2a1e0c, 0.32), 3));
  // Belt clip peeking out at the left, and the lock button at the top.
  canvas.drawRRect(rr(x - 7, cy - 46, 30, 92, 7), linear(x - 7, 0, x + 23, 0, [hexA(0x6c727a), hexA(0xd9dde2), hexA(0x8a9098), hexA(0x4a4f56)], [0, 0.3, 0.6, 1]));
  canvas.drawRRect(rr(cx + 6, y - 7, 38, 16, 7), linear(0, y - 7, 0, y + 9, [hexA(0x4a4c51), hexA(0x2a2b2f), hexA(0x1b1c1f)], [0, 0.5, 1]));
  canvas.drawLine(cx + 12, y - 4.5, cx + 38, y - 4.5, line(hexA(0xffffff, 0.18), 1));
  // Rubber armour.
  canvas.drawRRect(rr(x, y, s, s, 36), linear(0, y, 0, y + s, [hexA(0x3c3d42), hexA(0x2a2b2f), hexA(0x1f2023), hexA(0x161719)], [0, 0.3, 0.7, 1]));
  canvas.drawRRect(rr(x + 0.5, y + 0.5, s - 1, s - 1, 35.5), line(hexA(0xffffff, 0.12), 1));
  const ridge = line(hexA(0x0e0f10, 0.6), 1.6);
  const ridgeLight = line(hexA(0xffffff, 0.08), 0.8);
  for (let k = 0; k < 7; k++) {
    const yy = cy - 33 + k * 11;
    canvas.drawLine(x + 3, yy, x + 9, yy, ridge);
    canvas.drawLine(x + 3, yy + 1.4, x + 9, yy + 1.4, ridgeLight);
  }
  // Yellow lacquered face.
  const inset = 11;
  const fx = x + inset;
  const fy = y + inset;
  const fs = s - inset * 2;
  canvas.drawRRect(rr(fx, fy + 1.5, fs, fs, 26), blurred(hexA(0x000000, 0.45), 2));
  canvas.drawRRect(rr(fx, fy, fs, fs, 26), linear(fx, fy, fx + fs, fy + fs, [hexA(0xffe36d), hexA(0xfac929), hexA(0xeeae17), hexA(0xcf8d0c)], [0, 0.35, 0.7, 1]));
  const sheen = Skia.Paint();
  sheen.setShader(Skia.Shader.MakeRadialGradient({ x: fx + fs * 0.28, y: fy + fs * 0.22 }, fs * 0.75, [hexA(0xfffbe6, 0.55), hexA(0xfff4c0, 0)], [0, 1], TileMode.Clamp));
  canvas.drawRRect(rr(fx, fy, fs, fs, 26), sheen);
  canvas.drawRRect(rr(fx + 0.7, fy + 0.7, fs - 1.4, fs - 1.4, 25.3), line(hexA(0xfffbe8, 0.6), 1.2));
  canvas.drawRRect(rr(fx + 1, fy + 2, fs - 2, fs - 3, 25), line(hexA(0x8a5a00, 0.18), 1));
  // The reel cover: an embossed ring with the brand.
  canvas.drawCircle(cx, cy + 1, fs * 0.33, line(hexA(0xfff6cc, 0.55), 1.4));
  canvas.drawCircle(cx, cy, fs * 0.33, line(hexA(0x8a5800, 0.35), 1.4));
  const brandW = textWidth(fonts.brand, 'FITLINE') + 6 * 2.2;
  let bx = cx - brandW / 2;
  const chars = 'FITLINE'.split('');
  for (const ch of chars) {
    canvas.drawText(ch, bx, cy + 4.8, fill(hexA(0xfff8d8, 0.55)), fonts.brand);
    canvas.drawText(ch, bx, cy + 4, fill(hexA(0x5c3c02, 0.82)), fonts.brand);
    bx += textWidth(fonts.brand, ch) + 2.2;
  }
  const sub = '3 m · 10 ft';
  canvas.drawText(sub, cx - textWidth(fonts.small, sub) / 2, cy + 19, fill(hexA(0x6b4705, 0.7)), fonts.small);
  // Screws in the face's corners.
  const sIn = 15;
  screw(canvas, fx + sIn, fy + sIn, 4.6, 20);
  screw(canvas, fx + fs - sIn, fy + sIn, 4.6, 65);
  screw(canvas, fx + sIn, fy + fs - sIn, 4.6, 40);
  screw(canvas, fx + fs - sIn, fy + fs - sIn, 4.6, 5);
  // The mouth: a chrome guide where the tape comes out.
  const my = g.tapeY - g.tapeH / 2 - 7;
  const mh = g.tapeH + 14;
  canvas.drawRRect(rr(x + s - 10, my + 2, 20, mh, 5), blurred(hexA(0x000000, 0.35), 2.5));
  canvas.drawRRect(rr(x + s - 10, my, 20, mh, 5), linear(0, my, 0, my + mh, [hexA(0xf1f3f5), hexA(0xa2a8b0), hexA(0xe0e3e7), hexA(0x6d737b), hexA(0x3d4248)], [0, 0.3, 0.55, 0.85, 1]));
  canvas.drawLine(x + s + 8.5, my + 6, x + s + 8.5, my + mh - 6, line(hexA(0x1d1f22, 0.8), 1.6));
  return rec.finishRecordingAsPicture();
}

/**
 Draws the tape for value `v` (cm under the hairline): its shadow, cupped body, ticks and numbers,
 displaced by a wave of height `amp` (pt) travelling at `phase` (radians).
 */
export function drawTape(canvas: SkCanvas, g: Geo, fonts: Fonts, v: number, amp: number, phase: number) {
  'worklet';
  const x0 = g.mouthX - 26;
  const x1 = g.w + 12;
  const top = g.tapeY - g.tapeH / 2;
  const span = Math.max(1, x1 - g.mouthX);
  const wave = (x: number) => {
    if (amp < 0.01 || x <= g.mouthX) return 0;
    const e = Math.pow(Math.min((x - g.mouthX) / span, 1), 1.3);
    return amp * e * Math.sin(((x - g.mouthX) / WAVE_LENGTH) * Math.PI * 2 - phase);
  };

  const bodyB = Skia.PathBuilder.Make();
  for (let x = x0; x <= x1; x += 6) {
    if (x === x0) bodyB.moveTo(x, top + wave(x));
    else bodyB.lineTo(x, top + wave(x));
  }
  for (let x = x1; x >= x0; x -= 6) bodyB.lineTo(x, top + g.tapeH + wave(x));
  bodyB.close();

  canvas.save();
  canvas.translate(0, 6);
  const body = bodyB.build();
  canvas.drawPath(body, blurred(hexA(0x4a3410, 0.24), 5));
  canvas.translate(0, -4.5);
  canvas.drawPath(body, blurred(hexA(0x3a2808, 0.3), 1.5));
  canvas.restore();
  canvas.drawPath(
    body,
    linear(0, top, 0, top + g.tapeH, [hexA(0xc28f05), hexA(0xeebd18), hexA(0xffdc45), hexA(0xfff1a0), hexA(0xf7c923), hexA(0xe3ad10), hexA(0xb07f04)], [0, 0.1, 0.3, 0.38, 0.5, 0.82, 1]),
  );

  // Ticks: millimetres, half centimetres and centimetres on the top edge, a shorter row below.
  const cHi = v + (g.readX - x0) / PPCM;
  const cLo = v - (x1 - g.readX) / PPCM;
  const thinB = Skia.PathBuilder.Make();
  const thickB = Skia.PathBuilder.Make();
  const mStart = Math.ceil(Math.max(0, cLo) * 10);
  const mEnd = Math.floor(cHi * 10);
  for (let m = mStart; m <= mEnd; m++) {
    const x = g.readX + (v - m / 10) * PPCM;
    const dy = wave(x);
    const cm = m % 10 === 0;
    const len = cm ? 22 : m % 5 === 0 ? 15 : 9;
    const p = cm ? thickB : thinB;
    p.moveTo(x, top + 1 + dy);
    p.lineTo(x, top + 1 + len + dy);
    const lenB = cm ? 12 : m % 5 === 0 ? 9 : 5.5;
    p.moveTo(x, top + g.tapeH - 1 + dy);
    p.lineTo(x, top + g.tapeH - 1 - lenB + dy);
  }
  const thin = thinB.build();
  canvas.drawPath(thin, line(hexA(0x1d1a14, 0.82), 0.8));
  const thick = thickB.build();
  canvas.drawPath(thick, line(hexA(0x1d1a14, 0.95), 1.5));

  // A large number under every fifth centimetre line, every tenth in red (the value grows
  // toward the case); the centimetres between carry ticks only.
  const ink = fill(hexA(0x1d1a14));
  const red = fill(hexA(0xc8261c));
  for (let c = Math.ceil(cLo / 5) * 5; c <= cHi + 2; c += 5) {
    if (c <= 0) continue;
    const x = g.readX + (v - c) * PPCM;
    const tens = c % 10 === 0;
    const font = tens ? fonts.tickRed : fonts.tick;
    const s = `${c}`;
    canvas.drawText(s, x - textWidth(font, s) / 2, top + 50 + wave(x), tens ? red : ink, font);
  }

  // Edges: a bright lip on top, a darker one below.
  const lipTopB = Skia.PathBuilder.Make();
  const lipBottomB = Skia.PathBuilder.Make();
  for (let x = x0; x <= x1; x += 6) {
    const d = wave(x);
    if (x === x0) {
      lipTopB.moveTo(x, top + 0.6 + d);
      lipBottomB.moveTo(x, top + g.tapeH - 0.6 + d);
    } else {
      lipTopB.lineTo(x, top + 0.6 + d);
      lipBottomB.lineTo(x, top + g.tapeH - 0.6 + d);
    }
  }
  const lipTop = lipTopB.build();
  canvas.drawPath(lipTop, line(hexA(0xfff6c8, 0.75), 0.9));
  const lipBottom = lipBottomB.build();
  canvas.drawPath(lipBottom, line(hexA(0x6e4c00, 0.55), 0.9));
}

/** The red hairline that reads the value, with its little pointer tab. */
export function drawHairline(canvas: SkCanvas, g: Geo) {
  'worklet';
  const top = g.tapeY - g.tapeH / 2 - 18;
  const bottom = g.tapeY + g.tapeH / 2 + 14;
  canvas.drawLine(g.readX, top + 6, g.readX, bottom, blurred(hexA(0xe2342a, 0.28), 2));
  canvas.drawLine(g.readX, top + 6, g.readX, bottom, line(hexA(0xd92b20), 1.4));
  const tabB = Skia.PathBuilder.Make();
  tabB.moveTo(g.readX - 6, top - 3);
  tabB.lineTo(g.readX + 6, top - 3);
  tabB.lineTo(g.readX + 6, top + 2);
  tabB.lineTo(g.readX, top + 8);
  tabB.lineTo(g.readX - 6, top + 2);
  tabB.close();
  const tab = tabB.build();
  canvas.drawPath(tab, blurred(hexA(0x5a0c06, 0.3), 1.5));
  canvas.drawPath(tab, linear(0, top - 3, 0, top + 8, [hexA(0xf05247), hexA(0xd42a1f), hexA(0xa81c13)], [0, 0.5, 1]));
}
