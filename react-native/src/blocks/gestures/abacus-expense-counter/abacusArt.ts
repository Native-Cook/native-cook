import { BlendMode, BlurStyle, ClipOp, type SkCanvas, type SkPicture, Skia, TileMode } from '@shopify/react-native-skia';

import { BEADS, type Geo, RODS } from './abacusMotion';

/*
 The abacus, drawn in one canvas. Recorded once: the screen's backdrop, the walnut frame with its
 grain and bevels, the lacquered back panel, the brass rods with their rivets, one bead per
 category colour (a barrel-shaped lacquer cylinder: dark edges, a highlight band, a specular
 glint, darker rounded ends, contact shadows on the rod either side) and one soft drop shadow.
 Per frame: 50 shadows + 50 beads as translated pictures, and the scripted fingertip.
 */

/** Lacquer colours: vermilion, teal, ochre, pine, plum. */
export const BEAD_COLORS = [0xb8432e, 0x2e6f73, 0xc98f2a, 0x4d6b3f, 0x6a3b55];

function hexA(hex: number, a = 1) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 0xff) / 255, ((hex >> 8) & 0xff) / 255, (hex & 0xff) / 255, a);
}

function shade(hex: number, k: number, a = 1) {
  const c = hexA(hex);
  const f = (v: number) => (k >= 0 ? v + (1 - v) * k : v * (1 + k));
  return Float32Array.of(f(c[0]), f(c[1]), f(c[2]), a);
}

function fill(c: Float32Array) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(c);
  return p;
}

function stroke(c: Float32Array, width: number) {
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
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors, pos, TileMode.Clamp));
  return p;
}

function rr(x: number, y: number, w: number, h: number, r: number) {
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}

function hash(i: number) {
  const s = Math.sin(i * 12.9898) * 43758.5453;
  return s - Math.floor(s);
}

/** Backdrop, frame, back panel, rods: everything that never moves. */
export function recordBoard(g: Geo): SkPicture {
  const rec = Skia.PictureRecorder();
  const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, g.w, g.h));
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), linear(0, 0, 0, g.h, [hexA(0x1d1712), hexA(0x16110d), hexA(0x100c09)], [0, 0.5, 1]));
  const warm = Skia.Paint();
  warm.setShader(Skia.Shader.MakeRadialGradient({ x: g.w * 0.5, y: g.boardY + g.boardH * 0.4 }, g.w * 0.95, [hexA(0xd9a56a, 0.12), hexA(0xd9a56a, 0)], [0, 1], TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), warm);

  const { boardX: x, boardY: y, boardW: w, boardH: h, frame: f } = g;
  // Two shadows under the abacus.
  canvas.drawRRect(rr(x + 8, y + 20, w - 16, h - 6, 20), blurred(hexA(0x000000, 0.55), 18));
  canvas.drawRRect(rr(x + 1, y + 3, w - 2, h, 18), blurred(hexA(0x000000, 0.5), 3));
  // Walnut frame with grain running along it.
  const outer = rr(x, y, w, h, 18);
  canvas.drawRRect(outer, linear(0, y, 0, y + h, [hexA(0x7a4b2a), hexA(0x6a3f22), hexA(0x5a341c), hexA(0x4a2a16)], [0, 0.3, 0.7, 1]));
  canvas.save();
  canvas.clipRRect(outer, ClipOp.Intersect, true);
  const grain = Skia.Paint();
  grain.setAntiAlias(true);
  grain.setStyle(1);
  for (let k = 0; k < 46; k++) {
    const yy = y + (k / 46) * h + hash(k) * 4;
    const pathB = Skia.PathBuilder.Make();
    for (let xx = x - 4; xx <= x + w + 4; xx += 10) {
      const py = yy + Math.sin(xx * 0.02 + hash(k + 9) * 6) * 2.2 + Math.sin(xx * 0.07 + k) * 0.6;
      if (xx === x - 4) pathB.moveTo(xx, py);
      else pathB.lineTo(xx, py);
    }
    grain.setStrokeWidth(0.5 + hash(k + 3) * 1.1);
    grain.setColor(k % 4 === 0 ? hexA(0xc28a5a, 0.16) : hexA(0x2a160a, 0.28));
    const path = pathB.build();
    canvas.drawPath(path, grain);
  }
  const noise = Skia.Paint();
  noise.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.2, 2, 4, 0, 0));
  noise.setAlphaf(0.08);
  noise.setBlendMode(BlendMode.Overlay);
  canvas.drawRect(Skia.XYWHRect(x, y, w, h), noise);
  canvas.restore();
  canvas.drawRRect(rr(x + 0.6, y + 0.6, w - 1.2, h - 1.2, 17.4), stroke(hexA(0xf0c18e, 0.28), 1.2));

  // The back panel: dark lacquer, sunk into the frame (shadow on its top edge, light at the bottom).
  const ix = x + f;
  const iy = y + f;
  const iw = w - 2 * f;
  const ih = h - 2 * f;
  canvas.drawRRect(rr(ix, iy, iw, ih, 8), linear(0, iy, 0, iy + ih, [hexA(0x120c09), hexA(0x1d1410), hexA(0x221812)], [0, 0.3, 1]));
  canvas.save();
  canvas.clipRRect(rr(ix, iy, iw, ih, 8), ClipOp.Intersect, true);
  canvas.drawRect(Skia.XYWHRect(ix, iy - 6, iw, 10), blurred(hexA(0x000000, 0.7), 5));
  canvas.drawRect(Skia.XYWHRect(ix - 6, iy, 8, ih), blurred(hexA(0x000000, 0.4), 4));
  // Dividers between the rods' bands.
  for (let r = 1; r < RODS; r++) canvas.drawLine(ix + 10, iy + r * g.band, ix + iw - 10, iy + r * g.band, stroke(hexA(0xf0c18e, 0.05), 1));
  canvas.restore();
  canvas.drawLine(ix + 8, iy + ih + 0.5, ix + iw - 8, iy + ih + 0.5, stroke(hexA(0xf0c18e, 0.22), 1));

  // Brass rods with a rivet at each end.
  for (let r = 0; r < RODS; r++) {
    const ry = g.rodY[r];
    canvas.drawRRect(rr(ix - 4, ry - 1.6 + 3, iw + 8, 3.2, 1.6), blurred(hexA(0x000000, 0.5), 2));
    canvas.drawRRect(rr(ix - 4, ry - 1.7, iw + 8, 3.4, 1.7), linear(0, ry - 1.7, 0, ry + 1.7, [hexA(0xfbe3a8), hexA(0xc79a4c), hexA(0x7a5a24)], [0, 0.45, 1]));
    for (const rx of [x + f / 2, x + w - f / 2]) {
      const p = Skia.Paint();
      p.setAntiAlias(true);
      p.setShader(Skia.Shader.MakeRadialGradient({ x: rx - 1.2, y: ry - 1.4 }, 5, [hexA(0xfff1c8), hexA(0xc79a4c), hexA(0x5e4219)], [0, 0.5, 1], TileMode.Clamp));
      canvas.drawCircle(rx, ry + 0.6, 3.6, blurred(hexA(0x000000, 0.5), 1));
      canvas.drawCircle(rx, ry, 3.4, p);
    }
  }
  return rec.finishRecordingAsPicture();
}

/** One lacquered bead centred on the origin. */
export function recordBead(g: Geo, color: number): SkPicture {
  const rec = Skia.PictureRecorder();
  const { bw, bh } = g;
  const canvas = rec.beginRecording(Skia.XYWHRect(-bw, -bh, bw * 2, bh * 2));
  const x = -bw / 2 + 0.4;
  const w = bw - 0.8;
  const y = -bh / 2;
  const r = Math.min(w, bh) * 0.42;
  // Contact shadows on the rod, either side.
  canvas.drawOval(Skia.XYWHRect(-bw / 2 - 2.5, -2.5, 5, 6), blurred(hexA(0x000000, 0.45), 1.4));
  canvas.drawOval(Skia.XYWHRect(bw / 2 - 2.5, -2.5, 5, 6), blurred(hexA(0x000000, 0.45), 1.4));
  const body = rr(x, y, w, bh, r);
  canvas.drawRRect(body, linear(0, y, 0, y + bh, [shade(color, -0.55), shade(color, -0.05), shade(color, 0.32), shade(color, 0.05), shade(color, -0.25), shade(color, -0.62)], [0, 0.14, 0.3, 0.5, 0.8, 1]));
  canvas.save();
  canvas.clipRRect(body, ClipOp.Intersect, true);
  // Rounded ends: the barrel turns away from the light at both sides.
  canvas.drawRect(Skia.XYWHRect(x, y, w, bh), linear(x, 0, x + w, 0, [hexA(0x000000, 0.4), hexA(0x000000, 0), hexA(0x000000, 0), hexA(0x000000, 0.45)], [0, 0.28, 0.7, 1]));
  // The highlight band and a glint, toward the light at the upper left.
  canvas.drawRect(Skia.XYWHRect(x, y + bh * 0.2, w, bh * 0.12), blurred(hexA(0xffffff, 0.22), 1.5));
  canvas.drawOval(Skia.XYWHRect(x + w * 0.2, y + bh * 0.17, w * 0.28, bh * 0.12), blurred(hexA(0xffffff, 0.75), 0.8));
  // Bounce light from the panel along the bottom.
  canvas.drawRect(Skia.XYWHRect(x, y + bh * 0.84, w, bh * 0.08), blurred(shade(color, 0.2, 0.35), 1.2));
  canvas.restore();
  canvas.drawRRect(rr(x + 0.4, y + 0.4, w - 0.8, bh - 0.8, r - 0.4), stroke(hexA(0x000000, 0.35), 0.8));
  return rec.finishRecordingAsPicture();
}

/** The soft shadow a bead casts on the back panel. */
export function recordBeadShadow(g: Geo): SkPicture {
  const rec = Skia.PictureRecorder();
  const canvas = rec.beginRecording(Skia.XYWHRect(-g.bw * 1.5, -g.bh * 1.5, g.bw * 3, g.bh * 3));
  canvas.drawRRect(rr(-g.bw / 2 + 1, -g.bh / 2 + 6, g.bw - 2, g.bh, 9), blurred(hexA(0x000000, 0.5), 4));
  return rec.finishRecordingAsPicture();
}

export type Art = { board: SkPicture; beads: SkPicture[]; shadow: SkPicture };

/** One frame: the board, then every bead's shadow, then every bead, then the fingertip. */
export function drawAbacus(canvas: SkCanvas, g: Geo, art: Art, x: number[], finger: number[]) {
  'worklet';
  canvas.drawPicture(art.board);
  for (let r = 0; r < RODS; r++)
    for (let j = 0; j < BEADS; j++) {
      canvas.save();
      canvas.translate(x[r * BEADS + j], g.rodY[r]);
      canvas.drawPicture(art.shadow);
      canvas.restore();
    }
  for (let r = 0; r < RODS; r++)
    for (let j = 0; j < BEADS; j++) {
      canvas.save();
      canvas.translate(x[r * BEADS + j], g.rodY[r]);
      canvas.drawPicture(art.beads[r]);
      canvas.restore();
    }
  const a = finger[2];
  if (a > 0.01) {
    const rad = 20 * (1.1 - 0.1 * a);
    canvas.drawCircle(finger[0], finger[1], rad + 6, blurred(hexA(0xffe2bf, 0.1 * a), 8));
    canvas.drawCircle(finger[0], finger[1], rad, fill(hexA(0xfff1e0, 0.16 * a)));
    const ring = fill(hexA(0xfff1e0, 0.55 * a));
    ring.setStyle(1);
    ring.setStrokeWidth(1.4);
    canvas.drawCircle(finger[0], finger[1], rad, ring);
  }
}
