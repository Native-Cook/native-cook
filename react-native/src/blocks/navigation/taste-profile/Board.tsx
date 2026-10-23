import {
  Canvas,
  ClipOp,
  createPicture,
  Picture,
  type SkCanvas,
  type SkFont,
  type SkImage,
  PaintStyle,
  Skia,
  useFont,
} from '@shopify/react-native-skia';
import { useEffect, useMemo, useState } from 'react';
import { Image as RNImage, StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';


import {
  BOARD,
  BOARD_END,
  type Change,
  clamp01,
  COVER,
  drumChar,
  drumSteps,
  easeInOut,
  easeOut,
  flapAt,
  PHOTO_COUNT,
  REDUCED,
  STATS,
  STRIP,
} from './motion';
import type { HoldView } from './TileChart';
import { box, colors, DIGIT_GAP, DIGIT_W, GLYPH_EM, type GlyphRun, glyphRuns, layoutNumber, radius, statNumbers } from './theme';

/**
 * The departure board: the four stat numbers, the photo strip, its pager dots and the photo count,
 * drawn in one Skia picture per frame. At rest it draws exactly what the pin shows; when the city
 * changes every digit and photo becomes a split flap; a swipe on the strip turns it into a 3D
 * cover-flow. Coordinates are ref px (1206 wide) until they are mapped to the screen by `geo`.
 */

export type BoardGeo = { U: number; Y0: number; width: number; height: number; pr: number };

/** Glyph = [char, ink-left, ink width] in ref px. */
type Glyph = readonly [string, number, number];

const F = 40; // Skia font size; glyphs are scaled to their run's size
const EXTRA = require('./assets/Anybody-ExtraBold.ttf');

/** Each tile's number per city: the pin's glyphs for "all", laid out like the pin for the cities. */
export const STAT_GLYPHS: readonly (readonly (readonly Glyph[])[])[] = statNumbers.map((run, k) =>
  STATS.map((row, c) => (c === 0 ? run.glyphs : layoutNumber(row[k], run.glyphs[0][1], run.digitH))),
);

/** The photo count per city, right-aligned on the pin's last digit. */
const COUNT_RUN = glyphRuns.c1470;
const COUNT_RIGHT = COUNT_RUN.glyphs[3][1] + COUNT_RUN.glyphs[3][2];
const COUNT_GLYPHS: readonly (readonly Glyph[])[] = PHOTO_COUNT.map((v, c) => {
  if (c === 0) return COUNT_RUN.glyphs;
  const k = COUNT_RUN.digitH / 36;
  let w = 0;
  for (const ch of v) w += Math.round(DIGIT_W[ch] * k) + DIGIT_GAP;
  return layoutNumber(v, COUNT_RIGHT - (w - DIGIT_GAP), COUNT_RUN.digitH);
});

/**
 * Every colour the board draws, parsed once (Skia.Color allocates): the frame worklet only looks them up. Mixed
 * colours are drawn as two passes (base, then the target at the mix's alpha) instead of a new colour per frame.
 */
const PAL: Record<string, Float32Array> = (() => {
  const names = ['#000000', '#FFFFFF', 'rgba(255,255,255,0.55)', BOARD.cardTop, BOARD.cardBottom, BOARD.cardGap, colors.dotOn, colors.dotOff, ...box.dots.map((d) => d.color)];
  for (const run of Object.values(glyphRuns)) names.push(run.color);
  const out: Record<string, Float32Array> = {};
  for (const n of names) out[n] = Skia.Color(n);
  return out;
})();

/** The pin's footless "1" in its 17 × 36 viewBox (icons.tsx One), built once and scaled into place. */
const ONE_PATH = Skia.PathBuilder.Make().moveTo(16.5, 0).lineTo(8, 0).lineTo(0, 4.5).lineTo(0, 13).lineTo(8.5, 13).lineTo(8.5, 36).lineTo(16.5, 36).close().build();

/** The board's two paints: the one every shape is drawn with, and the alpha of a fading layer. Made once. */
export type BoardPaints = { paint: ReturnType<typeof Skia.Paint>; layer: ReturnType<typeof Skia.Paint> };

/** Width of a glyph in a run (ref px), for the drum's in-between characters. */
function glyphW(ch: string, run: GlyphRun) {
  'worklet';
  if (ch === ' ') return 0;
  return Math.round(DIGIT_W[ch] * (run.digitH / 36));
}

/** One glyph of a run at its measured ink box (as GlyphView in Roll.tsx draws it with React Native Text). */
function drawGlyph(canvas: SkCanvas, ch: string, inkL: number, inkW: number, run: GlyphRun, g: BoardGeo, font: SkFont, paint: ReturnType<typeof Skia.Paint>) {
  'worklet';
  if (ch === ' ' || inkW <= 0) return;
  const { U, Y0 } = g;
  const fs = (run.size * g.width) / 402;
  if (ch === '1') {
    // The pin's "1" has no foot: a flag and a stem (icons.tsx One, viewBox 17 × 36).
    const sx = (inkW * U) / 17;
    const sy = (run.digitH * U) / 36;
    const x = inkL * U;
    const y = (run.baseline - run.digitH) * U + Y0;
    canvas.save();
    canvas.translate(x, y);
    canvas.scale(sx, sy);
    canvas.drawPath(ONE_PATH, paint);
    canvas.restore();
    return;
  }
  const em = GLYPH_EM[ch];
  const sx = inkW / (em[1] * run.size * 3);
  canvas.save();
  canvas.translate(inkL * U - em[0] * fs * sx, run.baseline * U + Y0);
  canvas.scale((sx * fs) / F, fs / F);
  canvas.drawText(ch, 0, 0, paint, font);
  canvas.restore();
}

/**
 * T(h)·M·T(−h) for a flap turning about the horizontal hinge at (hx, hy) by `deg` toward the viewer.
 * `below`: the flap hangs below the hinge (the new bottom half rising from flat).
 */
function flapMatrix(deg: number, hx: number, hy: number, d: number, below: boolean) {
  'worklet';
  const a = (deg * Math.PI) / 180;
  const c = Math.cos(a);
  const p = (below ? -Math.sin(a) : Math.sin(a)) / d;
  return Skia.Matrix([1, hx * p, -hx * p * hy, 0, c + hy * p, -c * hy + hy - p * hy * hy, 0, p, 1 - p * hy]);
}

/** T(c)·M·T(−c) for a card turning about its vertical axis at (cx, cy) by `deg`. */
function turnMatrix(deg: number, cx: number, cy: number, d: number) {
  'worklet';
  const a = (deg * Math.PI) / 180;
  const cs = Math.cos(a);
  const q = Math.sin(a) / d;
  return Skia.Matrix([cs + cx * q, 0, -cs * cx + cx - q * cx * cx, cy * q, 1, -q * cx * cy, q, 0, 1 - q * cx]);
}

/** save(), or saveLayer() with an alpha when the content fades. Pair with canvas.restore(). */
function layer(canvas: SkCanvas, cx: Ctx, alpha: number) {
  'worklet';
  if (alpha >= 0.999) {
    canvas.save();
    return;
  }
  cx.layer.setAlphaf(alpha);
  canvas.saveLayer(cx.layer);
}

/** The hinge of a cell (screen pt), on the device pixel grid so the two halves meet without a seam. */
function hingeY(y: number, h: number, pr: number) {
  'worklet';
  return Math.round((y + h / 2) * pr) / pr;
}

/** Photo i drawn "cover" into rect (screen pt). */
function drawPhoto(canvas: SkCanvas, img: SkImage | null, x: number, y: number, w: number, h: number, paint: ReturnType<typeof Skia.Paint>) {
  'worklet';
  if (!img) return;
  const iw = img.width();
  const ih = img.height();
  const s = Math.max(w / iw, h / ih);
  const sw = w / s;
  const sh = h / s;
  canvas.drawImageRect(img, Skia.XYWHRect((iw - sw) / 2, (ih - sh) / 2, sw, sh), Skia.XYWHRect(x, y, w, h), paint);
}

/** What a flap face shows: a photo (kind 0) or a digit on a card (kind 1). */
type Face = { kind: number; img: number; ch: string; inkL: number; inkW: number };

type Ctx = {
  g: BoardGeo;
  /** Card corner radius (ref px) of the run being drawn. */
  cardR: number;
  font: SkFont;
  imgs: readonly (SkImage | null)[];
  paint: ReturnType<typeof Skia.Paint>;
  layer: ReturnType<typeof Skia.Paint>;
  run: GlyphRun;
};

/** Draw a face's half (top or bottom of the cell), clipped, with an overlay of black (+) or white (−). */
function drawHalf(canvas: SkCanvas, cx: Ctx, face: Face, x: number, y: number, w: number, h: number, top: boolean, shade: number, cardAlpha: number) {
  'worklet';
  const hy = hingeY(y, h, cx.g.pr);
  canvas.save();
  canvas.clipRect(top ? Skia.XYWHRect(x - 2, y - 2, w + 4, hy - y + 2) : Skia.XYWHRect(x - 2, hy, w + 4, y + h - hy + 2), ClipOp.Intersect, false);
  const paint = cx.paint;
  if (face.kind === 0) {
    paint.setColor(PAL['#000000']);
    paint.setAlphaf(1);
    drawPhoto(canvas, cx.imgs[face.img] ?? null, x, y, w, h, paint);
  } else {
    if (cardAlpha > 0) {
      const r = cx.cardR * cx.g.U;
      paint.setColor(PAL[top ? BOARD.cardTop : BOARD.cardBottom]);
      paint.setAlphaf(cardAlpha);
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r), paint);
    }
    // The digit turns white on its black card: its own colour, then white over it at the card's alpha.
    if (cardAlpha < 0.999) {
      paint.setColor(PAL[cx.run.color]);
      paint.setAlphaf(1);
      drawGlyph(canvas, face.ch, face.inkL, face.inkW, cx.run, cx.g, cx.font, paint);
    }
    if (cardAlpha > 0.001) {
      paint.setColor(PAL['#FFFFFF']);
      paint.setAlphaf(Math.min(1, cardAlpha));
      drawGlyph(canvas, face.ch, face.inkL, face.inkW, cx.run, cx.g, cx.font, paint);
    }
  }
  if (shade !== 0) {
    paint.setColor(PAL[shade > 0 ? '#000000' : '#FFFFFF']);
    paint.setAlphaf(Math.abs(shade) * (face.kind === 0 ? 1 : cardAlpha));
    if (face.kind === 0) canvas.drawRect(Skia.XYWHRect(x, y, w, h), paint);
    else {
      const r = cx.cardR * cx.g.U;
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r), paint);
    }
  }
  canvas.restore();
}

/** The moving flap at `deg` (0…180): old top half falling (< 90), new bottom half landing (≥ 90). */
function drawFlap(canvas: SkCanvas, cx: Ctx, from: Face, to: Face, x: number, y: number, w: number, h: number, deg: number, d: number, alpha: number, cardAlpha: number) {
  'worklet';
  const hx = x + w / 2;
  const hy = hingeY(y, h, cx.g.pr);
  const s = BOARD.shade;
  const rad = (deg * Math.PI) / 180;
  layer(canvas, cx, alpha);
  if (deg < 90) {
    canvas.save();
    canvas.concat(flapMatrix(deg, hx, hy, d, false));
    // A photo darkens as its face turns down; a black card catches a glint on the way.
    drawHalf(canvas, cx, from, x, y, w, h, true, from.kind === 0 ? s.flapDark * Math.sin(rad) : -s.glint * Math.sin(2 * rad), cardAlpha);
    canvas.restore();
  } else {
    const phi = 180 - deg;
    canvas.save();
    canvas.concat(flapMatrix(phi, hx, hy, d, true));
    drawHalf(canvas, cx, to, x, y, w, h, false, -s.flapLight * Math.sin((phi * Math.PI) / 180), cardAlpha);
    canvas.restore();
  }
  canvas.restore();
}

/**
 * A split-flap cell turning from face A to face B (`deg` of the falling flap, -1 none), with the
 * uncovered and covered halves shaded, motion-blur ghosts at the trailing angles and the hinge seam.
 */
function drawCell(
  canvas: SkCanvas,
  cx: Ctx,
  A: Face,
  B: Face,
  x: number,
  y: number,
  w: number,
  h: number,
  deg: number,
  speed: number,
  d: number,
  cardAlpha: number,
  seam: number,
) {
  'worklet';
  const s = BOARD.shade;
  if (deg < 0) {
    // At rest on face A, lifted by `speed` degrees when it bounces (speed holds the bounce here).
    drawHalf(canvas, cx, A, x, y, w, h, true, 0, cardAlpha);
    if (speed > 0.01) {
      drawHalf(canvas, cx, A, x, y, w, h, false, s.cover * 0.5 * Math.sin((speed * Math.PI) / 180), cardAlpha);
      drawFlap(canvas, cx, A, A, x, y, w, h, 180 - speed, d, 1, cardAlpha);
    } else drawHalf(canvas, cx, A, x, y, w, h, false, 0, cardAlpha);
  } else {
    const rad = (deg * Math.PI) / 180;
    // Behind the falling flap: B's top (in its shadow at first), A's bottom (covered at the end).
    drawHalf(canvas, cx, B, x, y, w, h, true, deg < 90 ? s.uncover * Math.cos(rad) : 0, cardAlpha);
    drawHalf(canvas, cx, A, x, y, w, h, false, deg > 90 ? s.cover * -Math.cos(rad) : 0, cardAlpha);
    const blur = BOARD.blur;
    const step = (speed * blur.shutter) / blur.alpha.length;
    for (let i = blur.alpha.length - 1; i >= 0; i--) {
      const gd = deg - (i + 1) * step;
      if (gd > 0) drawFlap(canvas, cx, A, B, x, y, w, h, gd, d, blur.alpha[i], cardAlpha);
    }
    drawFlap(canvas, cx, A, B, x, y, w, h, deg, d, 1, cardAlpha);
  }
  if (seam > 0) {
    const p = cx.paint;
    // Photos: a dark crack at the hinge. Cards: the gap between the two halves shows the tile.
    p.setColor(PAL[A.kind === 0 ? '#000000' : BOARD.cardGap]);
    p.setAlphaf(A.kind === 0 ? BOARD.shade.seam * seam : 0.9 * seam);
    const hy = hingeY(y, h, cx.g.pr);
    canvas.drawRect(Skia.XYWHRect(x, hy - 0.75, w, 1.5), p);
  }
}

/** The digits of a number run (stat or count) as split flaps, `local` s after the run started. */
function drawNumber(
  canvas: SkCanvas,
  cx: Ctx,
  from: readonly Glyph[],
  to: readonly Glyph[],
  local: number,
  slotStagger: number,
  step: number,
  card: { w: number; padY: number; r: number; spread: number },
  reduced: boolean,
  tau: number,
  alignRight: boolean,
) {
  'worklet';
  const { U, Y0 } = cx.g;
  const run = cx.run;
  const paint = cx.paint;
  if (reduced || local < 0 || from === to) {
    const show = reduced ? (tau >= REDUCED.fade / 2 ? to : from) : local < 0 ? from : to;
    const dip = reduced && tau > 0 && tau < REDUCED.fade ? 1 - (1 - REDUCED.dip) * Math.sin((Math.PI * tau) / REDUCED.fade) : 1;
    paint.setColor(PAL[run.color]);
    paint.setAlphaf(dip);
    for (let j = 0; j < show.length; j++) drawGlyph(canvas, show[j][0], show[j][1], show[j][2], run, cx.g, cx.font, paint);
    paint.setAlphaf(1);
    return;
  }
  const n = Math.max(from.length, to.length);
  const cw = card.w;
  const ch = run.digitH + 2 * card.padY;
  cx.cardR = card.r;
  // Right-aligned runs (the count) pair slots from the right; stats pair them from the left.
  const fromRight = alignRight;
  for (let j = 0; j < n; j++) {
    const fi = fromRight ? from.length - n + j : j;
    const ti = fromRight ? to.length - n + j : j;
    const a = fi >= 0 && fi < from.length ? from[fi] : null;
    const b = ti >= 0 && ti < to.length ? to[ti] : null;
    const ca = a ? a[0] : ' ';
    const cb = b ? b[0] : ' ';
    const steps = drumSteps(ca, cb);
    const centre = a && b ? (a[1] + a[2] / 2 + b[1] + b[2] / 2) / 2 : a ? a[1] + a[2] / 2 : b ? b[1] + b[2] / 2 : 0;
    const slotLocal = local - j * slotStagger;
    const f = flapAt(slotLocal, steps, step);
    // The card: in before the first flap, out after the landing.
    let cardA = 0;
    if (steps > 0) {
      const dIn = BOARD.digit.cardIn;
      const end = steps * step;
      cardA = slotLocal < end ? clamp01((slotLocal + dIn) / dIn) : 1 - clamp01((slotLocal - end - BOARD.bounce.dur * 0.5) / BOARD.digit.cardOut);
    }
    // The digits move apart while they are cards, so each card has room (left runs spread right).
    const shift = easeOut(clamp01(cardA)) * card.spread * (fromRight ? -(n - 1 - j) : j);
    const face = (c: string, k: number): Face => {
      'worklet';
      if (k === 0 && a) return { kind: 1, img: 0, ch: a[0], inkL: a[1] + shift, inkW: a[2] };
      if (k === steps && b) return { kind: 1, img: 0, ch: b[0], inkL: b[1] + shift, inkW: b[2] };
      const w = glyphW(c, run);
      return { kind: 1, img: 0, ch: c, inkL: centre + shift - w / 2, inkW: w };
    };
    const x = (centre + shift - cw / 2) * U;
    const y = (run.baseline - run.digitH - card.padY) * U + Y0;
    const A = face(drumChar(ca, f.k), f.k);
    const B = face(drumChar(ca, Math.min(f.k + 1, steps)), Math.min(f.k + 1, steps));
    const settled = f.angle < 0 && f.bounce < 0.01 && cardA <= 0;
    if (steps === 0 || slotLocal < -BOARD.digit.cardIn || settled) {
      // Not flapping: the glyph whole (two clipped halves would leave a hairline at the hinge).
      const gl = steps === 0 || settled ? b : a;
      paint.setColor(PAL[run.color]);
      paint.setAlphaf(1);
      if (gl) drawGlyph(canvas, gl[0], gl[1], gl[2], run, cx.g, cx.font, paint);
      continue;
    }
    drawCell(canvas, cx, A, B, x, y, cw * U, ch * U, f.angle, f.angle >= 0 ? f.speed : f.bounce, BOARD.persp.digit, cardA, f.angle >= 0 ? cardA : 0);
  }
}

/** The pager dots: the active one (continuous index p, 0…3) is dark, the others the pin's greys. */
function drawDots(canvas: SkCanvas, cx: Ctx, p: number) {
  'worklet';
  const { U, Y0 } = cx.g;
  const paint = cx.paint;
  for (let i = 0; i < box.dots.length; i++) {
    const dt = box.dots[i];
    const k = clamp01(1 - Math.abs(p - i));
    // Its grey, then the active ink over it at k (the same as mixing the two colours).
    if (k < 0.999) {
      paint.setColor(PAL[dt.color === colors.dotOn ? colors.dotOff : dt.color]);
      paint.setAlphaf(1);
      canvas.drawCircle(dt.cx * U, box.dotsY * U + Y0, (dt.d / 2) * U, paint);
    }
    if (k > 0.001) {
      paint.setColor(PAL[colors.dotOn]);
      paint.setAlphaf(Math.min(1, k));
      canvas.drawCircle(dt.cx * U, box.dotsY * U + Y0, (dt.d / 2) * U, paint);
    }
  }
}

/** Photo list of a city, read at slot i from offset x0 (clamped). */
function photoOf(city: number, i: number) {
  'worklet';
  const list = STRIP[city];
  return list[Math.max(0, Math.min(list.length - 1, i))];
}

/** The strip's white hairline border (RN borderWidth: drawn inside the box). */
function drawStripBorder(canvas: SkCanvas, cx: Ctx, alpha: number) {
  'worklet';
  if (alpha <= 0) return;
  const { U, Y0 } = cx.g;
  const st = box.strip;
  const bw = Math.max(1, 2 * U);
  const R = radius.photo * U;
  const p = cx.paint;
  p.setColor(PAL['rgba(255,255,255,0.55)']);
  p.setAlphaf(0.55 * alpha);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(bw);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(st.x * U + bw / 2, st.y * U + Y0 + bw / 2, st.w * U - bw, st.h * U - bw), R - bw / 2, R - bw / 2), p);
  p.setStyle(PaintStyle.Fill);
}

/** The strip flat or as a cover-flow: city's photos from offset x (photos), separation k. */
function drawStrip(canvas: SkCanvas, cx: Ctx, city: number, x: number, k: number) {
  'worklet';
  const { U, Y0 } = cx.g;
  const st = box.strip;
  const R = radius.photo * U;
  const sx = st.x * U;
  const sy = st.y * U + Y0;
  const sw = st.w * U;
  const sh = st.h * U;
  const cw = sw / 4;
  const paint = cx.paint;
  const list = STRIP[city];
  canvas.save();
  const grow = COVER.grow * U * k;
  canvas.clipRRect(Skia.RRectXY(Skia.XYWHRect(sx - grow * 3, sy - grow, sw + grow * 6, sh + grow * 2), R, R), ClipOp.Intersect, true);
  if (k < 0.0005) {
    const base = Math.floor(x);
    const frac = x - base;
    for (let i = -1; i <= 4; i++) {
      const idx = base + i;
      if (idx < 0 || idx >= list.length) continue;
      paint.setAlphaf(1);
      drawPhoto(canvas, cx.imgs[list[idx]] ?? null, sx + (i - frac) * cw, sy, cw, sh, paint);
    }
  } else {
    // Farthest first, so the centre photos are drawn over their neighbours.
    const first = Math.max(0, Math.floor(x) - 2);
    const last = Math.min(list.length - 1, Math.ceil(x) + 5);
    const order: number[] = [];
    for (let i = first; i <= last; i++) order.push(i);
    order.sort((p, q) => Math.abs(q - x - 1.5) - Math.abs(p - x - 1.5));
    for (let n = 0; n < order.length; n++) {
      const i = order[n];
      const o = i - x - 1.5; // photos from the strip's centre
      const ao = Math.min(Math.abs(o), COVER.span);
      const ccx = sx + sw / 2 + o * cw * (1 - COVER.squeeze * k * (ao / COVER.span));
      const ccy = sy + sh / 2;
      const turn = (-Math.sign(o) * COVER.rotate * k * ao) / COVER.span;
      const scale = 1 - COVER.shrink * k * (ao / COVER.span);
      const w = cw * (1 - COVER.inset * k);
      const r = R * COVER.radius * k;
      canvas.save();
      canvas.concat(turnMatrix(turn, ccx, ccy, BOARD.persp.photo * 1.2));
      canvas.translate(ccx, ccy);
      canvas.scale(scale, scale);
      canvas.translate(-ccx, -ccy);
      canvas.clipRRect(Skia.RRectXY(Skia.XYWHRect(ccx - w / 2, sy, w, sh), r, r), ClipOp.Intersect, true);
      paint.setAlphaf(1);
      drawPhoto(canvas, cx.imgs[list[i]] ?? null, ccx - cw / 2, sy, cw, sh, paint);
      const dim = COVER.dim * k * (ao / COVER.span);
      if (dim > 0) {
        paint.setColor(PAL['#000000']);
        paint.setAlphaf(dim);
        canvas.drawRect(Skia.XYWHRect(ccx - w / 2, sy, w, sh), paint);
      }
      canvas.restore();
    }
  }
  canvas.restore();
  drawStripBorder(canvas, cx, 1 - k);
}

/** The strip as a departure board: four cells flapping from (from, x0) to (to, 0). */
function drawStripFlaps(canvas: SkCanvas, cx: Ctx, c: Change) {
  'worklet';
  const { U, Y0 } = cx.g;
  const st = box.strip;
  const R = radius.photo * U;
  const sx = st.x * U;
  const sy = st.y * U + Y0;
  const sw = st.w * U;
  const sh = st.h * U;
  const cw = sw / 4;
  const P = BOARD.photo;
  canvas.save();
  canvas.clipRRect(Skia.RRectXY(Skia.XYWHRect(sx, sy, sw, sh), R, R), ClipOp.Intersect, true);
  const toList = STRIP[c.to];
  for (let i = 0; i < 4; i++) {
    const seq0 = photoOf(c.from, c.x0 + i);
    const seq3 = photoOf(c.to, i);
    const local = c.tau - P.delay - i * P.cellStagger;
    const steps = seq0 === seq3 ? 0 : P.steps;
    const f = flapAt(local, steps, P.step);
    // In-between photos: two others of the city, different per cell.
    const mid1 = toList[(i + 4) % toList.length];
    const mid2 = toList[(i + 2) % toList.length];
    const seqAt = (k: number) => {
      'worklet';
      return k <= 0 ? seq0 : k === 1 ? mid1 : k === 2 ? mid2 : seq3;
    };
    const A: Face = { kind: 0, img: steps === 0 ? seq3 : seqAt(f.k), ch: '', inkL: 0, inkW: 0 };
    const B: Face = { kind: 0, img: seqAt(Math.min(f.k + 1, steps)), ch: '', inkL: 0, inkW: 0 };
    const seam = steps === 0 ? 0 : local < 0 ? clamp01((local + 0.1) / 0.1) : local < steps * P.step ? 1 : 1 - clamp01((local - steps * P.step) / 0.25);
    drawCell(canvas, cx, A, B, sx + i * cw, sy, cw, sh, f.angle, f.angle >= 0 ? f.speed : f.bounce, BOARD.persp.photo, 1, seam);
  }
  canvas.restore();
  drawStripBorder(canvas, cx, 1);
}

/** Everything the board draws for one frame. */
export function drawBoard(canvas: SkCanvas, c: Change, x: number, k: number, g: BoardGeo, font: SkFont | null, imgs: readonly (SkImage | null)[], heldTile: number, paints: BoardPaints) {
  'worklet';
  if (!font) return;
  const paint = paints.paint;
  paint.setStyle(PaintStyle.Fill);
  paint.setAlphaf(1);
  const cx: Ctx = { g, font, imgs, paint, layer: paints.layer, run: statNumbers[0], cardR: 0 };
  const D = BOARD.digit;
  const changing = c.tau >= 0 && c.tau < BOARD_END && (c.from !== c.to || c.x0 !== 0);
  // Stats.
  for (let t = 0; t < 4; t++) {
    if (t === heldTile) continue; // the chart layer draws the held tile's number
    cx.run = statNumbers[t];
    const local = c.tau - D.delay - t * D.tileStagger;
    drawNumber(canvas, cx, STAT_GLYPHS[t][c.from], STAT_GLYPHS[t][c.to], c.from === c.to ? -1 : local, D.slotStagger, D.step, D.card, c.reduced, c.tau, false);
  }
  // Photo count.
  cx.run = COUNT_RUN;
  const C = BOARD.count;
  drawNumber(canvas, cx, COUNT_GLYPHS[c.from], COUNT_GLYPHS[c.to], c.from === c.to ? -1 : c.tau - C.delay, C.slotStagger, C.step, C.card, c.reduced, c.tau, true);
  // Strip and dots.
  const lenOf = (city: number) => {
    'worklet';
    return Math.max(1, STRIP[city].length - 4);
  };
  if (c.tau < 0) {
    drawStrip(canvas, cx, c.from, c.x0, 0);
    drawDots(canvas, cx, (c.x0 / lenOf(c.from)) * 3);
  } else if (changing && !c.reduced) {
    drawStripFlaps(canvas, cx, c);
    drawDots(canvas, cx, (c.x0 / lenOf(c.from)) * 3 * (1 - easeInOut(clamp01(c.tau / 0.5))));
  } else if (c.reduced && c.tau < REDUCED.fade && (c.from !== c.to || c.x0 !== 0)) {
    const half = c.tau < REDUCED.fade / 2;
    layer(canvas, cx, 1 - (1 - REDUCED.dip) * Math.sin((Math.PI * c.tau) / REDUCED.fade));
    drawStrip(canvas, cx, half ? c.from : c.to, half ? c.x0 : 0, 0);
    canvas.restore();
    drawDots(canvas, cx, half ? (c.x0 / lenOf(c.from)) * 3 : 0);
  } else {
    drawStrip(canvas, cx, c.to, x, k);
    drawDots(canvas, cx, (x / lenOf(c.to)) * 3);
  }
}

/** The block's photos, decoded for Skia once. */
function usePhotos(sources: readonly number[]) {
  const [imgs, setImgs] = useState<(SkImage | null)[] | null>(null);
  useEffect(() => {
    let on = true;
    Promise.all(
      sources.map(async (src) => {
        const uri = RNImage.resolveAssetSource(src).uri;
        const data = await Skia.Data.fromURI(uri);
        return Skia.Image.MakeImageFromEncoded(data);
      }),
    )
      .then((list) => {
        if (on) setImgs(list);
      })
      .catch(() => {});
    return () => {
      on = false;
    };
  }, [sources]);
  return imgs;
}

export function Board(props: {
  change: SharedValue<Change>;
  sx: SharedValue<number>;
  sk: SharedValue<number>;
  hold: SharedValue<HoldView>;
  geo: BoardGeo;
  photos: readonly number[];
  onReady: () => void;
}) {
  const { change, sx, sk, hold, geo, photos, onReady } = props;
  const font = useFont(EXTRA, F);
  const imgs = usePhotos(photos);
  const ready = font !== null && imgs !== null;
  useEffect(() => {
    if (ready) onReady();
  }, [ready, onReady]);
  const paints = useMemo<BoardPaints>(() => {
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    return { paint, layer: Skia.Paint() };
  }, []);
  const picture = useDerivedValue(() =>
    createPicture((canvas) => {
      if (imgs) drawBoard(canvas, change.get(), sx.get(), sk.get(), geo, font, imgs, hold.get().tile, paints);
    }),
  );
  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <Picture picture={picture} />
    </Canvas>
  );
}
