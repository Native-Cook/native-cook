/**
 * What the flight card and the timeline read off the model, as worklets: how much of each episode is on the timeline,
 * how much of the flight that covers, where the plane is, and the route's curve. Helpers come before their callers.
 */
import { Skia, type SkPath } from '@shopify/react-native-skia';

import { PHASE, clamp01, sheetAlpha, type Model } from './model';
import { ROUTE } from './theme';

/** How much of row i is on the timeline: picked (its presence, fading with the selection) or already downloaded. */
export function share(m: Model, i: number): number {
  'worklet';
  return clamp01(Math.max(clamp01(m.shown[i]) * sheetAlpha(m), m.mark[i]));
}

/** Minutes on the timeline as drawn. */
export function drawnMinutes(m: Model): number {
  'worklet';
  let s = 0;
  for (let i = 0; i < m.n; i++) s += m.dur[i] * share(m, i);
  return s;
}

/** The share of the flight the timeline covers, 0..1. */
export function coverage(m: Model): number {
  'worklet';
  return clamp01(drawnMinutes(m) / m.flight);
}

/**
 * The plane waits at the gate until a download starts, then flies the route as the episodes land: its position is
 * the downloaded share of the picked minutes, so it passes each episode's part of the route as that episode lands.
 */
export function planeTarget(m: Model): number {
  'worklet';
  if (m.phase === PHASE.downloading) {
    let got = 0;
    let all = 0;
    for (let k = 0; k < m.dlRows.length; k++) {
      const r = m.dlRows[k];
      got += m.dur[r] * clamp01(m.prog[r]);
      all += m.dur[r];
    }
    return all > 0 ? got / all : 0;
  }
  if (m.phase === PHASE.done || m.phase === PHASE.ready) return 1;
  return 0;
}

/** The route: a quadratic arc between the two codes. */
function control(): [number, number] {
  'worklet';
  return [(ROUTE.x0 + ROUTE.x1) / 2, ROUTE.y - 2 * ROUTE.lift];
}

export function routePath(): SkPath {
  'worklet';
  const [cx, cy] = control();
  const b = Skia.PathBuilder.Make();
  b.moveTo(ROUTE.x0, ROUTE.y);
  b.quadTo(cx, cy, ROUTE.x1, ROUTE.y);
  return b.build();
}

/** A point on the arc at t and the arc's heading there (radians). */
export function routeAt(t: number): { x: number; y: number; a: number } {
  'worklet';
  const [cx, cy] = control();
  const u = clamp01(t);
  const v = 1 - u;
  const x = v * v * ROUTE.x0 + 2 * v * u * cx + u * u * ROUTE.x1;
  const y = v * v * ROUTE.y + 2 * v * u * cy + u * u * ROUTE.y;
  const dx = 2 * v * (cx - ROUTE.x0) + 2 * u * (ROUTE.x1 - cx);
  const dy = 2 * v * (cy - ROUTE.y) + 2 * u * (ROUTE.y - cy);
  return { x, y, a: Math.atan2(dy, dx) };
}
