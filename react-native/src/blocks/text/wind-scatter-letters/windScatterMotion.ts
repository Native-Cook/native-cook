/*
 Wind Scatter Letters — where every glyph and leaf is, as a pure function of the loop time, and
 the small simulation a finger drives.

 LOOP = 9 s
   0.0 – 1.6   rest: the headline set, six leaves lying on the ridge.
   1.6 – 2.4   the gust builds: wind streaks sweep in, the glyphs tremble (up to 3°), eight
               leaves blow in from the left edge.
   2.2 – 3.2   the glyphs lift off one by one from the left (0.45 s across the line): each
               eases out (cubic, 1 s) toward its own drift (60–300 pt right, −250…+190 pt
               vertical) while circling an eddy (20–60 pt, 1.5–3 rad/s), tumbling (0.8–2.2 rad/s
               plus a 25° flutter) and swelling or shrinking with depth (−30 %…+45 %).
   5.0 – 7.2   they come home on an underdamped spring (ζ 0.55, ω 9 rad/s, ~12 % overshoot),
               30 ms apart left to right, the offset spiralling in as it shrinks.
   7.2 – 9.0   rest.

 Bodies are the headline's glyphs followed by the leaves on the ridge; the eight leaves that
 cross the screen are separate and always analytic.
*/

export const LOOP = 9;
export const GROUND_LEAVES = 6;
export const FLYING_LEAVES = 8;
const LIFT_AT = 2.2;
const LIFT_SPREAD = 0.45;
const LIFT_LEN = 1.0;
const RETURN_AT = 5.0;
const RETURN_STAGGER = 0.03;
const ZETA = 0.55;
const OMEGA = 9;
const SETTLED = 2.1;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOutCubic(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** Unit step response of the return spring. */
export function springStep(tau: number) {
  'worklet';
  if (tau <= 0) return 0;
  if (tau >= SETTLED) return 1;
  const wd = OMEGA * Math.sqrt(1 - ZETA * ZETA);
  const e = Math.exp(-ZETA * OMEGA * tau);
  return 1 - e * (Math.cos(wd * tau) + (ZETA / Math.sqrt(1 - ZETA * ZETA)) * Math.sin(wd * tau));
}

/** How strong the gust is (0…1): streaks, tremble, trails. */
export function gustAt(t: number) {
  'worklet';
  return easeInOutCubic(seg(t, 1.6, 2.4)) * (1 - easeInOutCubic(seg(t, 4.6, 5.6)));
}

/** Depth of body i: every third glyph sits behind the others (shrinks, blurs), the rest come forward. */
export function depthOf(i: number) {
  'worklet';
  const h = hash(i * 9.13 + 4);
  return i % 3 === 1 ? -(0.15 + 0.15 * h) : 0.1 + 0.35 * h;
}

/** A body's home: x, y of its centre, and how far it may scatter (1 for glyphs, less for leaves). */
export type Home = { x: number; y: number; reach: number };

/** [dx, dy, rotation (rad), scale] of body i at loop time t, relative to its home. */
export function autoOffset(t: number, i: number, home: Home, W: number, H: number): number[] {
  'worklet';
  const h1 = hash(i * 3.17 + 1);
  const h2 = hash(i * 5.31 + 2);
  const h3 = hash(i * 7.77 + 3);
  const h4 = hash(i * 9.13 + 4);
  // Tremble before lift-off.
  const lift = LIFT_AT + LIFT_SPREAD * clamp01(home.x / W) + 0.05 * h1;
  const tremble = (3 * Math.PI) / 180 * easeInOutCubic(seg(t, 1.6, lift)) * Math.sin(t * 23 + i * 1.9);
  if (t < lift) return [0, 0, tremble, 1];

  // The drift: toward a spot spread over the screen, kept on it.
  const tx = Math.min(W - 40, Math.max(40, home.x + (60 + 240 * h1) * home.reach));
  const ty = Math.min(H * 0.82, Math.max(H * 0.12, home.y + (-250 + 440 * h2) * home.reach));
  const ax = tx - home.x;
  const ay = ty - home.y;
  const r = (20 + 40 * h3) * home.reach;
  const w = (1.5 + 1.5 * h4) * (h1 > 0.5 ? 1 : -1);
  const spin = (0.8 + 1.4 * h3) * (h2 > 0.5 ? 1 : -1);
  const depth = depthOf(i);

  const ret = RETURN_AT + RETURN_STAGGER * i;
  const tf = Math.min(t, ret) - lift;
  const e = easeOutCubic(clamp01(tf / LIFT_LEN));
  const th = h1 * 6.283 + w * tf;
  const sx = e * ax * (1 - Math.exp(-tf / 0.9)) + e * r * (Math.cos(th) - Math.cos(h1 * 6.283));
  const sy = e * ay * (1 - Math.exp(-tf / 0.9)) + e * r * (Math.sin(th) - Math.sin(h1 * 6.283));
  const rot = e * (spin * tf + 0.44 * Math.sin(tf * 5 + i));
  const sc = 1 + e * depth * (1 + 0.1 * Math.sin(tf * 3 + i));
  if (t < ret) return [sx, sy, rot + tremble * (1 - e), sc];

  // Home on the spring; the offset turns as it shrinks so the way back curves.
  const k = 1 - springStep(t - ret);
  const a = 0.6 * k;
  const c = Math.cos(a);
  const s = Math.sin(a);
  return [k * (sx * c - sy * s), k * (sx * s + sy * c), k * rot, 1 + k * (sc - 1)];
}

/** One of the leaves crossing the screen: [x, y, rotation, flip (−1…1 as scaleX), scale]. */
export function flyingLeaf(t: number, j: number, W: number, H: number): number[] {
  'worklet';
  const h1 = hash(j * 11.3 + 21);
  const h2 = hash(j * 4.7 + 22);
  const start = 1.75 + 1.9 * h1;
  const dur = 2.2 + 1.2 * h2;
  const u = seg(t, start, start + dur);
  if (u <= 0 || u >= 1) return [-200, -200, 0, 1, 1];
  const x = -50 + (W + 100) * easeInOutCubic(u);
  const y0 = H * (0.18 + 0.55 * h2);
  const y = y0 + Math.sin(u * 6.283 * (1 + h1)) * 40 + u * 90;
  const rot = u * (5 + 6 * h1) * (h2 > 0.5 ? 1 : -1);
  const flip = Math.cos(u * 6.283 * (2 + h2 * 2));
  return [x, y, rot, flip, 0.85 + 0.5 * h1];
}

/** Simulation state for touch: per body offset, velocity, rotation and its speed, plus history for trails. */
export type Sim = { x: number[]; y: number[]; vx: number[]; vy: number[]; r: number[]; vr: number[]; px: number[][]; py: number[][] };

export function createSim(n: number): Sim {
  'worklet';
  const z = () => new Array(n).fill(0);
  return { x: z(), y: z(), vx: z(), vy: z(), r: z(), vr: z(), px: [z(), z()], py: [z(), z()] };
}

/** Finger wind: at (fx, fy) moving at (fvx, fvy) pt/s, `on` = 1 while touching. */
export type Wind = { fx: number; fy: number; fvx: number; fvy: number; on: number };

/** One step of the touch simulation. `home` springs are soft while the wind blows, firm when `settle` is 1. */
export function stepSim(s: Sim, homes: Home[], wind: Wind, dt: number, settle: number) {
  'worklet';
  const k = settle > 0 ? 70 : 9;
  const c = settle > 0 ? 2 * Math.sqrt(70) : 2.2;
  for (let i = 0; i < homes.length; i += 1) {
    s.px[1][i] = s.px[0][i];
    s.py[1][i] = s.py[0][i];
    s.px[0][i] = s.x[i];
    s.py[0][i] = s.y[i];
    let ax = -k * s.x[i] - c * s.vx[i];
    let ay = -k * s.y[i] - c * s.vy[i];
    let ar = -k * s.r[i] - c * s.vr[i];
    if (wind.on > 0) {
      const dx = homes[i].x + s.x[i] - wind.fx;
      const dy = homes[i].y + s.y[i] - wind.fy;
      const d2 = dx * dx + dy * dy;
      const near = Math.exp(-d2 / (2 * 120 * 120)) * homes[i].reach;
      const sp = Math.min(3000, Math.hypot(wind.fvx, wind.fvy));
      ax += (wind.fvx * (0.15 + 4.5 * near)) * (sp > 40 ? 1 : 0);
      ay += (wind.fvy * (0.15 + 4.5 * near) - 60 * near) * (sp > 40 ? 1 : 0);
      ar += ((dx * wind.fvy - dy * wind.fvx) / 4000) * near;
    }
    s.vx[i] += ax * dt;
    s.vy[i] += ay * dt;
    s.vr[i] += ar * dt;
    s.x[i] += s.vx[i] * dt;
    s.y[i] += s.vy[i] * dt;
    s.r[i] += s.vr[i] * dt;
  }
}

export function simEnergy(s: Sim) {
  'worklet';
  let e = 0;
  for (let i = 0; i < s.x.length; i += 1) e = Math.max(e, Math.abs(s.x[i]) + Math.abs(s.y[i]) + Math.abs(s.vx[i]) * 0.05 + Math.abs(s.vy[i]) * 0.05 + Math.abs(s.r[i]) * 30);
  return e;
}
