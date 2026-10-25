import { ClipOp, type SkCanvas, type SkImage, Skia } from '@shopify/react-native-skia';

import { type Geo, ICINGS, drawCake, drawKnife, drawLettering, drawNozzle, strokePoint } from './icingArt';
import type { Lettering } from './icingFont';
import { easeInOut, knifeAt, nozzleWork, pipeProgress, roseFlow, seg, slideAt } from './icingMotion';

/*
 Icing Name Form — one frame of the loop, and the nozzle's path along a piping schedule. Shared by
 the component and kept free of React so it can be checked offline.
 */

/** Toward the light (upper left), as a unit vector in screen space. */
export const LIGHT_X = -0.6;
export const LIGHT_Y = -0.8;

/** Where the nozzle rests, relative to the cake's centre (pt). */
export function parkPoint(g: Geo): number[] {
  'worklet';
  return [g.R * 0.98, -g.R * 0.72];
}

/** Per-stroke progress of a scripted piping at u (0..1 of its schedule). */
export function scheduleFracs(t0: number[], t1: number[], u: number): number[] {
  'worklet';
  const out: number[] = [];
  for (let i = 0; i < t0.length; i += 1) {
    const f = seg(u, t0[i], t1[i]);
    out.push(0.75 * f + 0.25 * easeInOut(f));
  }
  return out;
}

/** The nozzle on a scripted piping at u: [x, y (cake-local), lift 0..1]. */
export function schedulePen(L: Lettering, t0: number[], t1: number[], u: number): number[] {
  'worklet';
  const n = L.strokes.length;
  if (n === 0) return [0, 0, 1];
  for (let i = 0; i < n; i += 1) {
    const s = L.strokes[i];
    const full = s.len[s.len.length - 1];
    if (u <= t0[i]) {
      if (i === 0) return [s.pts[0], s.pts[1], 0];
      // Travelling, lifted, from the end of the last stroke to the start of this one.
      const p = L.strokes[i - 1];
      const ex = p.pts[p.pts.length - 2];
      const ey = p.pts[p.pts.length - 1];
      const v = easeInOut(seg(u, t1[i - 1], t0[i]));
      return [ex + (s.pts[0] - ex) * v, ey + (s.pts[1] - ey) * v, Math.sin(Math.PI * v)];
    }
    if (u <= t1[i]) {
      const f = seg(u, t0[i], t1[i]);
      const q = strokePoint(s.pts, s.len, full * (0.75 * f + 0.25 * easeInOut(f)));
      return [q[0], q[1], 0];
    }
  }
  const last = L.strokes[n - 1];
  return [last.pts[last.pts.length - 2], last.pts[last.pts.length - 1], 0];
}

/** Draws a lettering on a cake at (x, y) turned by `turn`, keeping its gloss lit from the upper left. */
export function drawWriting(
  canvas: SkCanvas,
  L: Lettering,
  x: number,
  y: number,
  turn: number,
  frac: number[] | null,
  sx: number[] | null,
  sy: number[] | null,
  a: number,
  b: number,
  flow: number,
  alpha: number,
  clipLeft: number,
) {
  'worklet';
  canvas.save();
  canvas.translate(x, y);
  canvas.rotate((turn * 180) / Math.PI, 0, 0);
  if (clipLeft > -1e4) canvas.clipRect(Skia.XYWHRect(clipLeft, -1e4, 2e4, 2e4), ClipOp.Intersect, true);
  const c = Math.cos(-turn);
  const s = Math.sin(-turn);
  drawLettering(canvas, L, frac, sx, sy, ICINGS[a].rgb, ICINGS[b].rgb, flow, LIGHT_X * c - LIGHT_Y * s, LIGHT_X * s + LIGHT_Y * c, alpha);
  canvas.restore();
}

/** One frame of the loop at t. */
export function drawLoopFrame(canvas: SkCanvas, g: Geo, cake: SkImage, t: number, rest: Lettering, second: Lettering, t0: number[], t1: number[], alpha: number, withNozzle: boolean) {
  'worklet';
  const s = slideAt(t);
  const away = g.w / 2 + g.boardR + 40;
  const cur = t >= 1.4 ? 1 : 0;
  // The cake now on the turntable (it slides out from 7.4), and the next one sliding in.
  const x0 = g.cx - s * away;
  const turn0 = -s * 0.7;
  drawCake(canvas, g, cake, x0, g.cy, turn0, 1);
  if (cur === 0) drawWriting(canvas, rest, x0, g.cy, turn0, null, null, null, 0, 0, 0, alpha, -1e5);
  else if (t < 2.2) {
    const k = knifeAt(t);
    drawWriting(canvas, rest, x0, g.cy, turn0, null, null, null, 0, 0, 0, alpha, k < -1.9 ? -1e5 : k * g.R * 0.75);
  } else {
    drawWriting(canvas, second, x0, g.cy, turn0, scheduleFracs(t0, t1, pipeProgress(t)), null, null, 0, 1, roseFlow(t), alpha, -1e5);
  }
  if (s > 0) {
    const x1 = g.cx + (1 - s) * away;
    const turn1 = (1 - s) * 0.7;
    drawCake(canvas, g, cake, x1, g.cy, turn1, 1);
    drawWriting(canvas, rest, x1, g.cy, turn1, null, null, null, 0, 0, 0, alpha, -1e5);
  }
  const k = knifeAt(t);
  if (k > -1.9) drawKnife(canvas, g, g.cx + k * g.R * 0.75, g.cy);
  if (withNozzle) {
    const w = nozzleWork(t);
    const park = parkPoint(g);
    const pen = schedulePen(second, t0, t1, pipeProgress(t));
    const px = park[0] + (pen[0] - park[0]) * w;
    const py = park[1] + (pen[1] - park[1]) * w;
    const lift = 1 - w + w * pen[2];
    const flow = roseFlow(t);
    drawNozzle(canvas, g.cx + px, g.cy + py, lift, flow > 0.5 || t > 7.9 ? ICINGS[t > 7.9 ? 0 : 1].rgb : ICINGS[0].rgb);
  }
}
