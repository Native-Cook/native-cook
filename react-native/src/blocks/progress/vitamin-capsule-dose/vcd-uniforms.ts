// Vitamin Capsule Dose — what the drawing layers read from the block's state: the WebGPU hero's frame
// (see gpu/VcdGpu.tsx) and the Skia fallback's uniforms.

import { D, type Layout } from './vcd-theme';

export type HeroState = { a: number; b: number; k: number; yaw: number; pitch: number; bob: number; take: number; reform: number; hold: number; t: number; dotX: number; dotY: number };
/** the capsule opened and its pearls: cap pulled off (units), twist and tilt (rad), the physics' mode */
export type SpillState = { sep: number; twist: number; tilt: number; hidden: number; dt: number; mode: number; fx: number; fy: number; fdx: number; fdy: number; down: number };
/** the pin's capsule lies at 45° */
export const REST_TILT = 0.785;
export const NO_SPILL: SpillState = { sep: 0, twist: 0, tilt: REST_TILT, hidden: 0, dt: 0, mode: 0, fx: -999, fy: -999, fdx: 0, fdy: 0, down: 0 };
export type Geo = { cx: number; cy: number; unit: number; shadow: number; floor: number; xmin: number; xmax: number };

export function makeGeo(L: Layout): Geo {
  return { cx: D.hero.x * L.sx, cy: L.Y(D.hero.y), unit: D.hero.unit * L.sx, shadow: L.Y(D.hero.shadowY) - L.Y(D.hero.y), floor: L.Y(D.hero.shadowY + 4), xmin: 14 * L.sx, xmax: L.w - 14 * L.sx };
}

/** the hero's frame as 48 floats (FrameU in gpu/vcd-wgsl.ts) */
export function makeFrame(g: Geo, w: number, h: number, pr: number, s: HeroState, o: SpillState) {
  'worklet';
  // the object turns once round while it changes form
  const yaw = s.yaw + 2 * Math.PI * s.k;
  const cy = Math.cos(yaw);
  const sy = Math.sin(yaw);
  const cp = Math.cos(s.pitch);
  const sp = Math.sin(s.pitch);
  // M = Rx(pitch) · Ry(yaw), columns
  const c0 = [cy, sp * sy, -cp * sy];
  const c1 = [0, cp, sp];
  const c2 = [sy, -sp * cy, cp * cy];
  return [
    w, h, pr, s.t,
    g.cx, g.cy + s.bob, g.unit, g.shadow - s.bob,
    0, s.a, s.b, s.k,
    c0[0], c0[1], c0[2], 0,
    c1[0], c1[1], c1[2], 0,
    c2[0], c2[1], c2[2], 0,
    s.take, s.reform, s.hold, 0,
    s.dotX, s.dotY, 0, 0,
    o.sep, o.twist, o.hidden, o.tilt,
    o.dt, o.mode, o.fx, o.fy,
    g.floor, g.xmin, g.xmax, 5.5,
    o.fdx, o.fdy, o.down, 0,
  ];
}
