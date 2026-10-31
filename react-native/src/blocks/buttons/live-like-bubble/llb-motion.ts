// Live Like Bubble — the hearts' flights, the count and the autoPlay loop as pure functions. Helpers
// first: worklets capture what is above them.

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function smooth(a: number, b: number, x: number) {
  'worklet';
  const u = clamp01((x - a) / (b - a));
  return u * u * (3 - 2 * u);
}
export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export const FLIGHT = 0.8; // s from the throw to the bubble
export const SWALLOW = 0.16; // s to melt in
export const SLOTS = 6;
export const BASE = 11; // likes a minute at rest
export const CENTRE_Y = 1.15;

/** Where heart i lands on the bubble (units, the bubble's space). */
export function targetOf(i: number) {
  'worklet';
  const a = (i * 2.39996) % (2 * Math.PI);
  return { x: 0.15 + 0.75 * Math.cos(a), y: CENTRE_Y + 0.45 + 0.15 * Math.sin(a), z: 0.1 };
}

/** A heart thrown at time `start` from `lane` (x, units): its place and size at `now`, size 0 when gone. */
export function heartAt(now: number, start: number, lane: number, i: number) {
  'worklet';
  const u = (now - start) / FLIGHT;
  if (u < 0 || u > 1 + SWALLOW / FLIGHT) return { x: 0, y: 0, z: 0, s: 0 };
  const tg = targetOf(i);
  const k = clamp01(u);
  const e = k * k * (1.6 - 0.6 * k); // a throw: fast out, slowing a little at the top
  const sx = lane, sy = -1.25, sz = 2.3; // the heart button, low and near the viewer
  const cx = (sx + tg.x) / 2, cy = Math.max(sy, tg.y) + 0.9, cz = 0.8;
  const v = 1 - e;
  const x = v * v * sx + 2 * v * e * cx + e * e * tg.x;
  const y = v * v * sy + 2 * v * e * cy + e * e * tg.y;
  const z = v * v * sz + 2 * v * e * cz + e * e * tg.z;
  // once it touches it melts in toward the middle
  const w = clamp01((now - start - FLIGHT) / SWALLOW);
  const size = (0.22 + 0.04 * Math.sin(i)) * smooth(0, 0.12, u) * (1 - w);
  return { x: x * (1 - w * 0.5), y: y + (CENTRE_Y - y) * w * 0.5, z: z * (1 - w), s: size };
}

/** The bubble's swell from the hearts it has swallowed by `now` (each a quick pulse). */
export function pulse(age: number) {
  'worklet';
  if (age <= 0 || age >= 0.6) return 0;
  return Math.exp(-age * 9) * Math.sin(age * 22 + 0.3);
}

export const LOOP = 9;
// three single taps, then a held stream; the count falls back while nobody taps
export const THROWS: number[] = [1.0, 1.45, 1.9, 3.0, 3.22, 3.44, 3.66, 3.88, 4.1, 4.32, 4.54, 4.76, 4.98, 5.2];
export const LANES: number[] = [-0.25, 0.1, 0.3, 0.0, -0.15, 0.2, -0.08, 0.14, -0.22, 0.05, 0.25, -0.12, 0.18, -0.03];
export const DECAY = [6.4, 8.6] as const;

export function countAt(t: number) {
  'worklet';
  let n = 0;
  for (let i = 0; i < THROWS.length; i++) if (t >= THROWS[i] + FLIGHT + SWALLOW) n++;
  const fall = Math.floor(THROWS.length * smooth(DECAY[0], DECAY[1], t) + 1e-6);
  return BASE + n - fall;
}

export type Frame = { count: number; swell: number; press: number; finger: number; fingerX: number };

export function frameAt(t: number): Frame {
  'worklet';
  let swell = 0;
  for (let i = 0; i < THROWS.length; i++) swell += pulse(t - THROWS[i] - FLIGHT - SWALLOW * 0.5);
  const tap = (a: number) => easeOut(seg(t, a - 0.06, a)) * (1 - easeOut(seg(t, a + 0.05, a + 0.16)));
  let press = tap(THROWS[0]) + tap(THROWS[1]) + tap(THROWS[2]);
  press += easeOut(seg(t, THROWS[3] - 0.06, THROWS[3])) * (1 - easeOut(seg(t, THROWS[THROWS.length - 1] + 0.05, THROWS[THROWS.length - 1] + 0.2)));
  const near = easeOut(seg(t, THROWS[0] - 0.3, THROWS[0] - 0.05)) * (1 - easeOut(seg(t, THROWS[THROWS.length - 1] + 0.1, THROWS[THROWS.length - 1] + 0.35)));
  // the finger stays on the heart button; the hearts leave it in slightly different directions
  return { count: countAt(t), swell: Math.max(-1, Math.min(1, swell)), press: clamp01(press), finger: near, fingerX: 0 };
}
