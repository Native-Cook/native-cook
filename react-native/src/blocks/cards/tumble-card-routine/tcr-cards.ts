// Tumble Card Routine: the four card faces, drawn in card-local points (origin at the card's top-left).
// A face is three pictures: `under` (shadow and photo, re-recorded while the photo settles), `static` (recorded once),
// and `over` (dots, bars, the rolling numbers: re-recorded each frame while the card lands, on the UI thread).

import {
  BlendMode,
  BlurStyle,
  ClipOp,
  PaintStyle,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPaint,
  type SkPath,
  type SkPicture,
  type SkRuntimeEffect,
  type SkSVG,
  Skia,
  TileMode,
  vec,
} from '@shopify/react-native-skia';

import { easeInOut, easeOut, REVEAL, seg, springAt } from './tcr-motion';
import { BARS, C, CARDS, COPY, type LineSpec, SPRING } from './tcr-theme';

export type Fonts = { r12: SkFont; s12: SkFont; s11: SkFont; s14: SkFont; s15: SkFont; s40: SkFont };
export type Svgs = { fork: SkSVG; drop: SkSVG; scale: SkSVG; bolt: SkSVG; flame: SkSVG };

/** Widths measured once on the JS thread (Skia rounds `measureText`; glyph widths are exact). */
export type Metrics = {
  digit: number[]; // advance of 0–9 at the number size
  dot: number;
  cell: number; // tabular cell: the widest digit, tightened by the −0.03 em tracking
  unit: Record<string, number>;
  burned: number;
};

export type Assets = {
  fonts: Fonts;
  svgs: Svgs;
  photos: (SkImage | null)[];
  paths: SkPath[]; // continuous-corner outline per card
  metrics: Metrics;
};

const TRACK = -0.03 * 40;

/** iOS-style continuous corners (the curve UIKit draws for `.continuous`), as a path. */
export function squircle(w: number, h: number, rIn: number, x = 0, y = 0): SkPath {
  const r = Math.min(rIn, Math.min(w, h) / 2 / 1.52866483);
  const b = Skia.PathBuilder.Make();
  // one corner, in corner coordinates: a along the incoming edge (back from the corner), b along the outgoing one
  const P: [number, number][] = [
    [1.52866483, 0],
    [1.08849323, 0],
    [0.86840689, 0],
    [0.66993427, 0.065496],
    [0.63149399, 0.074911],
    [0.37282392, 0.16905899],
    [0.16906013, 0.37282401],
    [0.07491176, 0.63149399],
    [0, 0.86840701],
    [0, 1.08849299],
    [0, 1.52866483],
  ];
  const corners = [
    { cx: x + w, cy: y, ux: -1, uy: 0, vx: 0, vy: 1 },
    { cx: x + w, cy: y + h, ux: 0, uy: -1, vx: -1, vy: 0 },
    { cx: x, cy: y + h, ux: 1, uy: 0, vx: 0, vy: -1 },
    { cx: x, cy: y, ux: 0, uy: 1, vx: 1, vy: 0 },
  ];
  const pt = (k: (typeof corners)[number], a: number, bb: number) => [k.cx + (a * k.ux + bb * k.vx) * r, k.cy + (a * k.uy + bb * k.vy) * r];
  corners.forEach((k, ci) => {
    const p0 = pt(k, P[0][0], P[0][1]);
    if (ci === 0) b.moveTo(p0[0], p0[1]);
    else b.lineTo(p0[0], p0[1]);
    const c1 = pt(k, P[1][0], P[1][1]);
    const c2 = pt(k, P[2][0], P[2][1]);
    const p3 = pt(k, P[3][0], P[3][1]);
    b.cubicTo(c1[0], c1[1], c2[0], c2[1], p3[0], p3[1]);
    const p4 = pt(k, P[4][0], P[4][1]);
    b.lineTo(p4[0], p4[1]);
    const c5 = pt(k, P[5][0], P[5][1]);
    const c6 = pt(k, P[6][0], P[6][1]);
    const p7 = pt(k, P[7][0], P[7][1]);
    b.cubicTo(c5[0], c5[1], c6[0], c6[1], p7[0], p7[1]);
    const c8 = pt(k, P[8][0], P[8][1]);
    const c9 = pt(k, P[9][0], P[9][1]);
    const p10 = pt(k, P[10][0], P[10][1]);
    b.cubicTo(c8[0], c8[1], c9[0], c9[1], p10[0], p10[1]);
  });
  b.close();
  return b.detach();
}

export const textW = (font: SkFont, s: string) => {
  'worklet';
  if (!s) return 0;
  const ws = font.getGlyphWidths(font.getGlyphIDs(s));
  let t = 0;
  for (let j = 0; j < ws.length; j++) t += ws[j];
  return t;
};

const paintOf = (hex: string, alpha = 1) => {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(hex));
  p.setAlphaf(alpha);
  return p;
};

/** Text with tracking (pt per glyph), drawn glyph by glyph; returns the advance. */
const drawTracked = (cv: SkCanvas, s: string, x: number, y: number, font: SkFont, p: SkPaint, tr: number) => {
  'worklet';
  let cx = x;
  for (let j = 0; j < s.length; j++) {
    const ch = s[j];
    cv.drawText(ch, cx, y, p, font);
    cx += textW(font, ch) + tr;
  }
  return cx - x - tr;
};
const drawSvgAt = (cv: SkCanvas, svg: SkSVG, x: number, y: number, size: number) => {
  'worklet';
  cv.save();
  cv.translate(x, y);
  cv.drawSvg(svg, size, size);
  cv.restore();
};

/** A line of text runs (font, colour, alpha) centred on cx. */
const drawRuns = (cv: SkCanvas, runs: { s: string; f: SkFont; c: string; a?: number }[], cx: number, y: number) => {
  let w = 0;
  runs.forEach((r) => {
    w += textW(r.f, r.s);
  });
  let x = cx - w / 2;
  runs.forEach((r) => {
    cv.drawText(r.s, x, y, paintOf(r.c, r.a ?? 1), r.f);
    x += textW(r.f, r.s);
  });
};

const grain = (cv: SkCanvas, fx: SkRuntimeEffect, path: SkPath, alpha: number) => {
  const p = Skia.Paint();
  p.setShader(fx.makeShader([]));
  p.setBlendMode(BlendMode.SoftLight);
  p.setAlphaf(alpha);
  cv.save();
  cv.clipPath(path, ClipOp.Intersect, true);
  cv.drawPaint(p);
  cv.restore();
};

const ovalScrim = (cv: SkCanvas, cx: number, cy: number, rx: number, ry: number, alpha: number) => {
  const p = paintOf('#000000', alpha);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 15, true));
  cv.drawOval(Skia.XYWHRect(cx - rx, cy - ry, rx * 2, ry * 2), p);
};

/** Photo cards: a 1 pt highlight on the top edge, fading down the sides. */
const edgeLight = (cv: SkCanvas, path: SkPath, h: number, alpha: number) => {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(1);
  p.setShader(
    Skia.Shader.MakeLinearGradient(vec(0, 0), vec(0, h * 0.4), [Skia.Color(`rgba(255,255,255,${alpha})`), Skia.Color('rgba(255,255,255,0)')], null, TileMode.Clamp),
  );
  cv.save();
  cv.clipPath(path, ClipOp.Intersect, true);
  cv.drawPath(path, p);
  cv.restore();
};

/** The face that never changes: recorded once per card. */
export function recordStatic(i: number, A: Assets, grainFx: SkRuntimeEffect, paperPath: SkPath): SkPicture {
  const c = CARDS[i];
  const { fonts: F, svgs: V, paths, metrics } = A;
  const path = paths[i];
  const rec = Skia.PictureRecorder();
  const cv = rec.beginRecording(Skia.XYWHRect(-20, -20, c.w + 40, c.h + 40));
  const cx = c.w / 2;
  if (c.id === 'food') {
    cv.drawPath(path, paintOf(C.foodFrame));
    // the lacquered frame catches the light on its top edge
    cv.save();
    cv.clipPath(path, ClipOp.Intersect, true);
    cv.drawRect(Skia.XYWHRect(0, 0, c.w, 0.75), paintOf(C.foodFrameLit, 0.9));
    cv.restore();
    drawSvgAt(cv, V.fork, 11, 9, 18);
    cv.drawText(c.name, 32, 23.2, paintOf(C.foodInk), F.s14);
    // paper inset 8 in the frame, with a hairline of shadow under the frame's top edge and a little grain
    cv.drawPath(paperPath, paintOf(C.paper));
    cv.save();
    cv.clipPath(paperPath, ClipOp.Intersect, true);
    const sh = Skia.Paint();
    sh.setShader(Skia.Shader.MakeLinearGradient(vec(0, 36), vec(0, 40), [Skia.Color('rgba(33,29,18,0.10)'), Skia.Color('rgba(33,29,18,0)')], null, TileMode.Clamp));
    cv.drawRect(Skia.XYWHRect(8, 36, c.w - 16, 4), sh);
    cv.restore();
    grain(cv, grainFx, paperPath, 0.1);
    drawRuns(cv, [{ s: COPY.foodLeft, f: F.r12, c: C.ink2 }], cx, 66);
    drawRuns(
      cv,
      [
        { s: COPY.foodPct, f: F.r12, c: C.ink2 },
        { s: COPY.foodGoal, f: F.s12, c: C.paperInk },
      ],
      cx,
      240,
    );
    drawRuns(cv, [{ s: COPY.foodTotal, f: F.s12, c: C.paperInk }], cx, 256);
  } else if (c.id === 'water') {
    // header and footer legibility on the photo: soft scrims, and one behind the number only
    const top = Skia.Paint();
    top.setShader(Skia.Shader.MakeLinearGradient(vec(0, 0), vec(0, 70), [Skia.Color('rgba(6,18,17,0.30)'), Skia.Color('rgba(6,18,17,0)')], null, TileMode.Clamp));
    const bot = Skia.Paint();
    bot.setShader(
      Skia.Shader.MakeLinearGradient(vec(0, c.h * 0.68), vec(0, c.h), [Skia.Color('rgba(6,22,22,0)'), Skia.Color('rgba(6,22,22,0.42)')], null, TileMode.Clamp),
    );
    cv.save();
    cv.clipPath(path, ClipOp.Intersect, true);
    cv.drawRect(Skia.XYWHRect(0, 0, c.w, 70), top);
    cv.drawRect(Skia.XYWHRect(0, c.h * 0.68, c.w, c.h * 0.32), bot);
    ovalScrim(cv, cx, c.numY, 84, 30, 0.2);
    cv.restore();
    grain(cv, grainFx, path, 0.12);
    edgeLight(cv, path, c.h, 0.14);
    drawSvgAt(cv, V.drop, 13, 13, 18);
    cv.drawText(c.name, 33, 27.5, paintOf(C.white), F.s15);
    drawRuns(
      cv,
      [
        { s: COPY.waterOf, f: F.r12, c: C.white, a: 0.85 },
        { s: COPY.waterGoal, f: F.s12, c: C.white },
      ],
      cx,
      249,
    );
    drawRuns(cv, [{ s: COPY.waterDaily, f: F.s12, c: C.white }], cx, 265);
  } else if (c.id === 'weight') {
    const g = Skia.Paint();
    g.setAntiAlias(true);
    g.setShader(Skia.Shader.MakeLinearGradient(vec(0, 0), vec(0, c.h), [Skia.Color(C.glassTop), Skia.Color(C.glassBottom)], null, TileMode.Clamp));
    cv.drawPath(path, g);
    grain(cv, grainFx, path, 0.06);
    const rim = paintOf(C.white, 0.1);
    rim.setStyle(PaintStyle.Stroke);
    rim.setStrokeWidth(1);
    cv.save();
    cv.clipPath(path, ClipOp.Intersect, true);
    cv.drawPath(path, rim);
    cv.restore();
    drawSvgAt(cv, V.scale, 13, 13, 18);
    cv.drawText(c.name, 35, 27.5, paintOf(C.white), F.s15);
    const chipW = textW(F.s11, COPY.weightChip) + 16;
    const chipX = c.w - 14 - chipW;
    cv.drawRRect(Skia.RRectXY(Skia.XYWHRect(chipX, 12, chipW, 20), 10, 10), paintOf(C.chipFill));
    cv.drawText(COPY.weightChip, chipX + 8, 26, paintOf(C.chipInkDark), F.s11);
    drawRuns(cv, [{ s: COPY.weightGoal, f: F.r12, c: C.goalInk }], cx, 230);
    drawRuns(cv, [{ s: COPY.weightLose, f: F.s12, c: C.white }], cx, 246);
  } else {
    const bot = Skia.Paint();
    bot.setShader(Skia.Shader.MakeLinearGradient(vec(0, 0), vec(0, 64), [Skia.Color('rgba(10,9,6,0.30)'), Skia.Color('rgba(10,9,6,0)')], null, TileMode.Clamp));
    cv.save();
    cv.clipPath(path, ClipOp.Intersect, true);
    cv.drawRect(Skia.XYWHRect(0, 0, c.w, 64), bot);
    ovalScrim(cv, cx, c.numY + c.numLH / 2 - 6, 110, 62, 0.22);
    cv.restore();
    grain(cv, grainFx, path, 0.12);
    edgeLight(cv, path, c.h, 0.12);
    drawSvgAt(cv, V.bolt, 13, 13, 18);
    cv.drawText(c.name, 33, 27.5, paintOf(C.white), F.s15);
    // "You burned", with room on its left for the flame (drawn live, it flickers on landing)
    const x0 = cx - (14 + 4 + metrics.burned) / 2;
    cv.drawText(COPY.actBurned, x0 + 18, 102, paintOf(C.white, 0.85), F.r12);
  }
  return rec.finishRecordingAsPicture();
}

// ---------------------------------------------------------------- live layers (UI thread)

/** Left x of the "You burned" row's flame. */
const flameX = (metrics: Metrics, w: number) => {
  'worklet';
  return w / 2 - (14 + 4 + metrics.burned) / 2;
};

/** Photo settle on landing: 1.12× and 8 pt of parallax, gliding to rest. */
const settleAt = (rv: number) => {
  'worklet';
  if (rv >= REVEAL) return 1;
  return 1 - Math.max(0, Math.min(1, springAt(rv, 1, 0, 0, SPRING.glide.d, SPRING.glide.k)));
};

export const recordUnder = (i: number, A: Assets, rv: number, rot: number, lift: number, shadowK: number): SkPicture => {
  'worklet';
  const c = CARDS[i];
  const path = A.paths[i];
  const rec = Skia.PictureRecorder();
  const cv = rec.beginRecording(Skia.XYWHRect(-80, -80, c.w + 160, c.h + 200));
  // two shadows tinted by the card (light from straight above: offsets fall straight down on the screen)
  const r = (rot * Math.PI) / 180;
  const sx = Math.sin(r);
  const cy = Math.cos(r);
  const contactA = 0.18 * (1 - lift) * shadowK;
  if (contactA > 0.002) {
    const p = paintOf(c.shadow, contactA);
    p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2, true));
    cv.save();
    cv.translate(2 * sx, 2 * cy);
    cv.drawPath(path, p);
    cv.restore();
  }
  // ambient shadow in the card's own tint, strong enough to colour the stage under it
  const dy = 22 + 10 * lift;
  const ambA = (0.34 - 0.1 * lift) * shadowK;
  if (ambA > 0.002) {
    const p = paintOf(c.shadow, ambA);
    p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, (48 + 16 * lift) / 2, true));
    cv.save();
    cv.translate(dy * sx, dy * cy);
    cv.drawPath(path, p);
    cv.restore();
  }
  const img = A.photos[i];
  if (img) {
    const g = settleAt(rv);
    const zoom = 1 + 0.12 * (1 - g);
    const par = 8 * (1 - g);
    const iw = img.width();
    const ih = img.height();
    const cs = Math.max(c.w / iw, c.h / ih) * zoom;
    const sw = c.w / cs;
    const sh = c.h / cs;
    const sxx = (iw - sw) / 2;
    const syy = Math.min(Math.max((ih - sh) / 2 - par / cs, 0), ih - sh);
    cv.save();
    cv.clipPath(path, ClipOp.Intersect, true);
    const p = Skia.Paint();
    p.setAntiAlias(true);
    cv.drawImageRect(img, Skia.XYWHRect(sxx, syy, sw, sh), Skia.XYWHRect(0, 0, c.w, c.h), p);
    cv.restore();
  }
  return rec.finishRecordingAsPicture();
};

/** One digit column of a rolling number. */
const drawColumn = (
  cv: SkCanvas,
  font: SkFont,
  metrics: Metrics,
  p: SkPaint,
  a: string,
  b: string,
  e: number,
  x: number,
  cw: number,
  base: number,
  size: number,
) => {
  'worklet';
  const cap = 0.727 * size;
  const wOf = (ch: string) => (ch === '.' ? metrics.dot : ch === ' ' ? 0 : metrics.digit[ch.charCodeAt(0) - 48]);
  const drawAt = (ch: string, y: number, alpha: number) => {
    if (ch === ' ' || alpha <= 0.002) return;
    p.setAlphaf(alpha);
    cv.drawText(ch, x + (cw - wOf(ch)) / 2, y, p, font);
  };
  cv.save();
  cv.clipRect(Skia.XYWHRect(x - 6, base - cap - 0.16 * size, cw + 12, cap + 0.32 * size), ClipOp.Intersect, true);
  if (e <= 0 || e >= 1 || (a === b && (a === ' ' || a === '.'))) {
    drawAt(e >= 1 ? b : a, base, 1);
  } else if (a === ' ' || a === '.' || b === ' ' || b === '.') {
    // appears from below / leaves upwards
    drawAt(a, base - e * 0.62 * size, 1 - e);
    drawAt(b, base + (1 - e) * 0.62 * size, e);
  } else {
    const da = a.charCodeAt(0) - 48;
    const db = b.charCodeAt(0) - 48;
    // a digit that keeps its value spins one full turn: the card's own number rolls in and settles
    const steps = (db - da + 10) % 10 || 10;
    const q = e * steps;
    const k = Math.floor(q);
    const f = q - k;
    drawAt(String.fromCharCode(48 + ((da + k) % 10)), base - f * size, 1);
    drawAt(String.fromCharCode(48 + ((da + k + 1) % 10)), base + (1 - f) * size, 1);
  }
  cv.restore();
  p.setAlphaf(1);
};

/** A number line rolling from `from` to `to` (tabular cells, the unit cross-fading in the middle 40 %). */
const drawNumberLine = (cv: SkCanvas, A: Assets, from: LineSpec, to: LineSpec, rv: number, delay: number, cx: number, capMid: number, color: string) => {
  'worklet';
  const font = A.fonts.s40;
  const M = A.metrics;
  const size = 40;
  const n = Math.max(from.v.length, to.v.length);
  const fa = from.v.padStart(n, ' ');
  const tb = to.v.padStart(n, ' ');
  const DIG = 0.42;
  const STAG = 0.045;
  const total = DIG + STAG * (n - 1);
  const es: number[] = [];
  const ws: number[] = [];
  let W = 0;
  for (let c = 0; c < n; c++) {
    const e = easeOut(seg(rv - delay, STAG * c, STAG * c + DIG));
    es.push(e);
    const wa = fa[c] === ' ' ? 0 : fa[c] === '.' ? M.dot + TRACK : M.cell;
    const wb = tb[c] === ' ' ? 0 : tb[c] === '.' ? M.dot + TRACK : M.cell;
    const w = wa + (wb - wa) * e;
    ws.push(w);
    W += w;
  }
  const ue = easeInOut(seg(rv - delay, total * 0.3, total * 0.7));
  const uwA = from.u ? M.unit[from.u] ?? 0 : 0;
  const uwB = to.u ? M.unit[to.u] ?? 0 : 0;
  const gap = 0.2 * size;
  const uw = uwA + (uwB - uwA) * ue;
  const hasUnit = (from.u ? 1 - ue : 0) + (to.u ? ue : 0);
  const Wt = W + gap * Math.min(1, hasUnit) + uw;
  const base = capMid + (0.727 * size) / 2;
  const p = paintOf(color);
  let x = cx - Wt / 2;
  for (let c = 0; c < n; c++) {
    if (ws[c] > 0.01 || es[c] < 1) drawColumn(cv, font, M, p, fa[c], tb[c], es[c], x, Math.max(ws[c], 0.01), base, size);
    x += ws[c];
  }
  x += gap * Math.min(1, hasUnit);
  if (from.u && from.u !== to.u && ue < 1) {
    p.setAlphaf(1 - ue);
    drawTracked(cv, from.u, x, base, font, p, TRACK);
  }
  if (to.u) {
    p.setAlphaf(from.u === to.u ? 1 : ue);
    drawTracked(cv, to.u, x, base, font, p, TRACK);
  }
};

export const recordOver = (i: number, A: Assets, rv: number, soft: number): SkPicture => {
  'worklet';
  const c = CARDS[i];
  const rec = Skia.PictureRecorder();
  const cv = rec.beginRecording(Skia.XYWHRect(-20, -20, c.w + 40, c.h + 40));
  const cx = c.w / 2;
  // dots: fill from the previous card's count to this one's (40 ms stagger)
  if (c.dots) {
    const d = c.dots;
    const y = c.id === 'food' ? 18 : 22;
    const right = c.id === 'food' ? c.w - 14 : c.w - 16;
    for (let j = 0; j < d.n; j++) {
      // lit dots pop in turn (40 ms stagger) as the card lands
      const on = j < d.on;
      const pop = on && rv > 0 && rv < REVEAL ? Math.sin(Math.PI * seg(rv, 0.12 + 0.04 * j, 0.32 + 0.04 * j)) : 0;
      cv.drawCircle(right - 3 - (d.n - 1 - j) * 9, y, 3 * (1 + 0.4 * pop), paintOf(d.color, on ? 1 : d.off));
    }
  }
  if (c.id === 'weight') {
    const x0 = cx - (4 * BARS.w + 3 * BARS.gap) / 2;
    for (let j = 0; j < 4; j++) {
      // the week's bars refill as the card lands: each dips and rises again (50 ms stagger), the green one last with a pop
      let k = 1;
      if (rv > 0 && rv < REVEAL) {
        if (j < 3) k = 1 - 0.7 * Math.sin(Math.PI * easeOut(seg(rv, 0.08 + 0.05 * j, 0.48 + 0.05 * j)));
        else k = rv < 0.3 ? 1 - 0.85 * Math.sin((Math.PI / 2) * seg(rv, 0.08, 0.3)) : springAt(rv - 0.3, 0.15, 1, 0, SPRING.pop.d, SPRING.pop.k);
      }
      const bh = BARS.h * Math.max(k, 0);
      if (bh < 0.5) continue;
      const x = x0 + j * (BARS.w + BARS.gap);
      const y = BARS.top + BARS.h - bh;
      const r = Math.min(BARS.r, bh / 2);
      cv.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, BARS.w, bh), r, r), paintOf(j === 3 ? C.barOn : C.barOff));
      // top highlight
      cv.save();
      cv.clipRRect(Skia.RRectXY(Skia.XYWHRect(x, y, BARS.w, bh), r, r), ClipOp.Intersect, true);
      cv.drawRect(Skia.XYWHRect(x, y, BARS.w, 0.75), paintOf(C.white, j === 3 ? 0.35 : 0.1));
      cv.restore();
    }
    // one reflection sweep across the glass as it lands
    if (rv > 0.1 && rv < 0.6) {
      const u = easeInOut(seg(rv, 0.1, 0.6));
      const bx = -60 + (c.w + 120) * u;
      const sp = Skia.Paint();
      sp.setShader(
        Skia.Shader.MakeLinearGradient(
          vec(bx - 30, 0),
          vec(bx + 30, 0),
          [Skia.Color('rgba(255,255,255,0)'), Skia.Color('rgba(255,255,255,0.10)'), Skia.Color('rgba(255,255,255,0)')],
          null,
          TileMode.Clamp,
        ),
      );
      cv.save();
      cv.clipPath(A.paths[i], ClipOp.Intersect, true);
      cv.translate(bx, c.h / 2);
      cv.rotate(25, 0, 0);
      cv.translate(-bx, -c.h / 2);
      cv.drawRect(Skia.XYWHRect(bx - 30, -c.h, 60, c.h * 3), sp);
      cv.restore();
    }
  }
  if (c.id === 'act') {
    // the flame flickers twice as the card lands
    const f1 = rv > 0.2 && rv < 0.38 ? Math.sin(Math.PI * seg(rv, 0.2, 0.38)) : 0;
    const f2 = rv > 0.45 && rv < 0.63 ? Math.sin(Math.PI * seg(rv, 0.45, 0.63)) : 0;
    const sy = 1 + 0.15 * (f1 + f2);
    const fx = flameX(A.metrics, c.w);
    cv.save();
    cv.translate(fx + 7, 105);
    cv.scale(1, sy);
    cv.translate(-7, -14);
    cv.drawSvg(A.svgs.flame, 14, 14);
    cv.restore();
  }
  // the numbers
  for (let li = 0; li < c.lines.length; li++) {
    const to = c.lines[li];
    drawNumberLine(cv, A, to, to, rv >= REVEAL ? 1 : rv, li * 0.08, cx, c.numY + li * c.numLH, c.numColor);
  }
  if (soft > 0.002) {
    cv.save();
    cv.clipPath(A.paths[i], ClipOp.Intersect, true);
    cv.drawPaint(paintOf('#000000', 0.06 * soft));
    cv.restore();
  }
  return rec.finishRecordingAsPicture();
};
