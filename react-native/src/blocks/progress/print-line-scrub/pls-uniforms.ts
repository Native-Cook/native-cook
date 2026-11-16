// Print Line Scrub — the numbers that turn the live state into shader uniforms and screen positions. Pure functions
// of plain data ('worklet'), shared by the component and by the headless review harness.

import { FIGURE_SIZE, silhouetteAt } from './pls-figure';
import { clamp01, mix, percentAt } from './pls-motion';
import { ref } from './pls-theme';
import type { Layout } from './pls-theme';

const PITCH = (20 * Math.PI) / 180;
export const CAM_COS = Math.cos(PITCH);
export const CAM_SIN = Math.sin(PITCH);

/** Layout numbers the worklets need, as plain data (no functions). */
export type Geo = {
  S: number;
  W: number;
  H: number;
  axisX: number; // screen x of the figure's axis
  baseY: number; // screen y of the base's top face (world y = 0)
  perY: number; // screen pt per world unit of height (the line's travel)
  U: number; // pt per world unit across
  capL: number; // capsule left
  capW: number;
  capH: number;
  capR: number;
  capYMin: number; // centre range
  capYMax: number;
  barX: number; // dotted bar box, relative to the capsule's top-left (screen pt)
  barY: number;
  barW: number;
  barH: number;
  pillD: number;
  hdrY: number;
  dockY: number;
};

export function makeGeo(L: Layout): Geo {
  const S = L.S;
  const perY = ref.stage.unit * S;
  const capH = ref.capsule.h * S;
  return {
    S,
    W: L.W,
    H: L.H,
    axisX: L.x(ref.stage.cx),
    baseY: L.y(ref.stage.yBase),
    perY,
    U: perY / CAM_COS,
    capL: L.x(ref.stage.cx - ref.capsule.w / 2),
    capW: ref.capsule.w * S,
    capH,
    capR: ref.capsule.r * S,
    capYMin: L.yt(ref.header.y + ref.header.h) + ref.rangeInset * S + capH / 2,
    capYMax: L.yb(ref.dock.y) - ref.rangeInset * S - capH / 2,
    barX: ref.bar.x * S,
    barY: ref.bar.y * S,
    barW: ref.bar.w * S,
    barH: ref.bar.h * S,
    pillD: ref.pill.d * S,
    hdrY: L.yt(ref.header.y),
    dockY: L.yb(ref.dock.y),
  };
}

/** screen y of the cut plane on the figure's axis */
export function planeY(g: Geo, h: number): number {
  'worklet';
  return g.baseY - g.perY * h;
}

/** centre y of the capsule: it sits `duck` below the line, kept inside the header and the dock */
export function capsuleCy(g: Geo, h: number, duck: number): number {
  'worklet';
  const y = planeY(g, h) + duck * g.S;
  return y < g.capYMin ? g.capYMin : y > g.capYMax ? g.capYMax : y;
}

/** x of the pill's centre, relative to the bar box's left, in screen pt */
export function pillX(g: Geo, h: number): number {
  'worklet';
  const r = g.pillD / 2;
  return r + (g.barW - g.pillD) * clamp01(percentAt(h) / 100);
}

export type SceneState = { h: number; yaw: number; band: number; heat: number; bead: number };

export function sceneUniforms(g: Geo, s: SceneState, p3: number, px: number) {
  'worklet';
  const figureH = ref.figure.height * g.S;
  const figureW = (figureH * FIGURE_SIZE[0]) / FIGURE_SIZE[1];
  const figureY = g.baseY + (ref.figure.top - ref.stage.yBase) * g.S;
  const row = (planeY(g, s.h) - figureY) / figureH;
  const spans = silhouetteAt(row);
  return {
    uFigure: [g.axisX - figureW / 2, figureY, figureW, figureH] as [number, number, number, number],
    uImage: [FIGURE_SIZE[0], FIGURE_SIZE[1]] as [number, number],
    uSpanA: spans.slice(0, 4),
    uSpanB: spans.slice(4, 8),
    uSpanC: spans.slice(8, 12),
    uO: [g.axisX, g.baseY] as [number, number],
    uU: g.U,
    uCam: [CAM_COS, CAM_SIN] as [number, number],
    uYaw: Math.max((-4 * Math.PI) / 180, Math.min((4 * Math.PI) / 180, s.yaw)),
    uH: s.h,
    uBand: s.band * g.S,
    uHeat: s.heat,
    uBead: s.bead,
    uTint: clamp01(percentAt(s.h) / 100),
    uPx: px,
    uP3: p3,
    uGlass: 0,
    uLens: [0, 0, 1, 1] as [number, number, number, number],
    uLensR: 1,
    uLensK: 1,
    uRefr: 0,
  };
}

/** the same scene seen through the capsule: magnified a little, bent along the rim */
export function glassUniforms(g: Geo, s: SceneState, cy: number, p3: number, px: number) {
  'worklet';
  const u = sceneUniforms(g, s, p3, px);
  u.uGlass = 1;
  u.uLens = [g.capL + g.capW / 2, cy, g.capW / 2, g.capH / 2];
  u.uLensR = g.capR;
  u.uLensK = ref.glass.lens;
  u.uRefr = ref.glass.refraction * g.S;
  return u;
}

export function barUniforms(g: Geo, h: number, cy: number, over: number, tail: number, flash: number, p3: number) {
  'worklet';
  const top = cy - g.capH / 2;
  return {
    uOrigin: [g.capL + g.barX, top + g.barY] as [number, number],
    uS: g.S,
    uSize: [g.barW / g.S, g.barH / g.S] as [number, number],
    uFill: pillX(g, h) / g.S,
    uOver: over,
    uTail: tail,
    uFlash: flash,
    uP3: p3,
  };
}

export const mixN = mix;
