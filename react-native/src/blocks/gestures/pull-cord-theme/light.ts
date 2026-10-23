/**
 * The lamp's light on the page, the same model the shader uses (shaders.ts), so anything drawn outside the shader can
 * agree with it.
 *
 * The page is a wall `depth` points behind the bulb. The shade lets light out in a cone around its axis, which cuts
 * the wall in the hyperbolic scallop a real pendant throws; inside it the illuminance falls off as cos(incidence)/r²
 * = depth/r³. A little bounce light from the shade and ceiling reaches everywhere. The theme front is a level of log
 * illuminance, so the Day page develops along the light's own contours.
 *
 * Worklets: every helper is declared above the function that calls it.
 */
import type { Uniforms } from '@shopify/react-native-skia';

import { lampPoint, type Geometry } from './cord-sim';
import { FILAMENT } from './motion';
import { BLACKBODY, CHAIN, LAMP } from './theme';

export const BOUNCE = 0.03;
const RAD = Math.PI / 180;
export const CONE = {
  cos0: Math.cos((LAMP.coneDeg + LAMP.penumbraDeg) * RAD),
  cos1: Math.cos((LAMP.coneDeg - LAMP.penumbraDeg) * RAD),
};

function smooth(a: number, b: number, x: number) {
  'worklet';
  const t = Math.min(1, Math.max(0, (x - a) / (b - a)));
  return t * t * (3 - 2 * t);
}

/** Direct light from the shade (no bounce) at a page point, for a bulb at (bx, by) and a shade axis (sx, sy). */
export function directLight(px: number, py: number, bx: number, by: number, sx: number, sy: number, depth: number) {
  'worklet';
  const dx = px - bx;
  const dy = py - by;
  const r = Math.sqrt(dx * dx + dy * dy + depth * depth);
  const c = (dx * sx + dy * sy) / r;
  const k = depth / r;
  return smooth(CONE.cos0, CONE.cos1, c) * k * k * k;
}

export function illuminance(px: number, py: number, bx: number, by: number, sx: number, sy: number, depth: number) {
  'worklet';
  const dx = px - bx;
  const dy = py - by;
  const d2 = 4 * depth * depth;
  return directLight(px, py, bx, by, sx, sy, depth) + (BOUNCE * d2) / (dx * dx + dy * dy + d2);
}

/** The log-illuminance range over the screen at rest: the front runs from its top (0) to its bottom (1). */
export function fieldRange(g: Geometry) {
  const b = lampPoint(g, 0, 0, LAMP.bulbY);
  const depth = LAMP.depth * g.s;
  let lo = Infinity;
  let hi = -Infinity;
  for (let i = 0; i <= 40; i++) {
    for (let j = 0; j <= 80; j++) {
      const e = Math.log(illuminance((g.w * i) / 40, (g.h * j) / 80, b.x, b.y, 0, 1, depth));
      lo = Math.min(lo, e);
      hi = Math.max(hi, e);
    }
  }
  // The brightest few per cent flip at once: the front starts as a visible scallop, not a point.
  return { lo, hi: hi - 0.04 * (hi - lo) };
}

export type RGB = [number, number, number];

export function hex(c: string): RGB {
  const n = parseInt(c.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

const BB = BLACKBODY.color.map(hex);

/** The filament's colour by heat, 0..255 per channel. */
export function lampColor(heat: number): RGB {
  'worklet';
  const hs = BLACKBODY.heat;
  const h = Math.min(1, Math.max(0, heat));
  for (let i = 0; i < hs.length - 1; i++) {
    if (h <= hs[i + 1]) {
      const u = (h - hs[i]) / (hs[i + 1] - hs[i]);
      const a = BB[i];
      const b = BB[i + 1];
      return [a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u];
    }
  }
  return BB[BB.length - 1];
}

export function css(c: readonly number[], alpha = 1) {
  'worklet';
  return `rgba(${Math.round(c[0])}, ${Math.round(c[1])}, ${Math.round(c[2])}, ${alpha})`;
}

export function mix(a: readonly number[], b: readonly number[], t: number): RGB {
  'worklet';
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

/** What the lamp and the page need for one frame, from the simulation's numbers. */
export type LampState = {
  angle: number; heat: number; front: number; flat: number; adapt: number; flicker: number; time: number;
};

/** Visible brightness of the filament: radiant output climbs steeply with temperature. */
export function brightness(heat: number) {
  'worklet';
  return Math.pow(Math.min(1, Math.max(0, heat)), FILAMENT.power);
}

/**
 * The page's uniforms. `chain` holds the balls (x, y pairs) then the housing's foot; the shader wants the balls and
 * the tip of the pull, which hangs a pull's length below the last ball along the last link.
 */
export function pageUniforms(
  g: Geometry, range: { lo: number; hi: number }, st: LampState, band: number, chain: readonly number[], balls: number,
): Uniforms {
  'worklet';
  const b = lampPoint(g, st.angle, 0, LAMP.bulbY);
  const lc = lampColor(st.heat);
  const pts: number[] = [];
  for (let i = 0; i < balls; i++) pts.push(chain[i * 2], chain[i * 2 + 1]);
  const n = balls - 1;
  const dx = chain[n * 2] - chain[n * 2 - 2];
  const dy = chain[n * 2 + 1] - chain[n * 2 - 1];
  const len = Math.sqrt(dx * dx + dy * dy) || 1;
  const reach = (CHAIN.pullHeight + CHAIN.ball) * g.s;
  pts.push(chain[n * 2] + (dx / len) * reach, chain[n * 2 + 1] + (dy / len) * reach);
  return {
    uBulb: [b.x, b.y],
    uAxis: [Math.sin(st.angle), Math.cos(st.angle)],
    uDepth: LAMP.depth * g.s,
    uRange: [range.lo, range.hi],
    uFront: st.front,
    uBand: band,
    uFlat: st.flat,
    uBright: brightness(st.heat) * st.flicker,
    uLamp: [lc[0] / 255, lc[1] / 255, lc[2] / 255],
    uScale: g.s,
    uAdapt: st.adapt,
    uChain: pts,
  };
}

export function airUniforms(g: Geometry, st: LampState): Uniforms {
  'worklet';
  const b = lampPoint(g, st.angle, 0, LAMP.bulbY);
  const lc = lampColor(st.heat);
  return {
    uBulb: [b.x, b.y],
    uAxis: [Math.sin(st.angle), Math.cos(st.angle)],
    uBright: brightness(st.heat) * st.flicker * (st.flat ? 0.6 : 1),
    uLamp: [lc[0] / 255, lc[1] / 255, lc[2] / 255],
    uTime: st.time,
    uScale: g.s,
  };
}
