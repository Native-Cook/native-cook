/*
 The rope and its knot, as closed-form functions of time.

 The rope is pinned at both screen edges along the top of the tab bar. Its height at x (u = x / w):
   y = slack sag  S0 · sin(πu)
     + the knot's weight: a tent with its point at the knot, deepest when the knot is mid-span,
       K · 4·uk·(1 − uk) · (u / uk, or (1 − u) / (1 − uk) past the knot)
     + whip: the first four string modes sin(nπu), excited by "kicks" (a time, a place, a size)
       and ringing down: a / n · sin(nπ·u_kick) · sin(ω_n τ) · e^(−τ / 0.5), with ω_n = n · 2π · 1.8.
       A kick fades to exactly 0 between 1.4 and 1.9 s after it, so the loop closes on itself.

 autoPlay (LOOP = 8 s): the knot's place p (0…3, a tab index) is a sum of spring steps (ω 7.5,
 ζ 0.78, ~1 % overshoot, settled in ~1 s):
   0.0 – 1.3   rest on Explore.
   1.3 – 2.6   to Gear (p 0 → 2): the rope lifts where the knot left, dips and rings where it lands.
   2.6 – 3.6   hold.
   3.6 – 4.6   to Routes (2 → 1).
   4.6 – 5.6   hold.
   5.6 – 6.8   back to Explore (1 → 0), with a harder whip.
   6.8 – 8.0   rest (the last kick rings out by 7.95).
 A tap ripple marks each move's tab, as if a finger tapped it.
 */

export const LOOP = 8;
export const TABS = 4;
export const SAG = 4;
export const KNOT_WEIGHT = 7;
const WHIP_HZ = 1.8;
const WHIP_DECAY = 0.5;
export const KNOT_W = 7.5;
export const KNOT_Z = 0.78;
/** How long after the start of a move its landing kick comes (s). */
export const LAND_AFTER = 0.42;

/** [start, from, to] */
export const LOOP_MOVES: number[][] = [
  [1.3, 0, 2],
  [3.6, 2, 1],
  [5.6, 1, 0],
];

export function tabU(i: number) {
  'worklet';
  return (i + 0.5) / TABS;
}

/** Kicks: [time, u, size (pt, + = down)], flattened. */
export const LOOP_KICKS: number[] = (() => {
  const out: number[] = [];
  LOOP_MOVES.forEach(([t, from, to], i) => {
    const d = Math.pow(Math.abs(to - from), 0.6);
    const hard = i === LOOP_MOVES.length - 1 ? 1.45 : 1;
    out.push(t + 0.05, tabU(from), -3.2 * d * hard);
    out.push(t + LAND_AFTER, tabU(to), 6 * d * hard);
  });
  return out;
})();

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** Step response of a spring from rest: position 0 → 1. */
export function springStep(tau: number, w: number, z: number) {
  'worklet';
  if (tau <= 0) return 0;
  const wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * tau) * (Math.cos(wd * tau) + ((z * w) / wd) * Math.sin(wd * tau));
}

/** The loop's knot place p at loop time t. */
export function loopKnot(t: number) {
  'worklet';
  let p = 0;
  for (let i = 0; i < LOOP_MOVES.length; i++) {
    const m = LOOP_MOVES[i];
    p += (m[2] - m[1]) * springStep(t - m[0], KNOT_W, KNOT_Z);
  }
  return t > 7.9 ? 0 : p;
}

/** The rope's height below its pinned line at u, for a knot at uk, from kicks at time t. */
export function ropeAt(u: number, uk: number, kicks: number[], t: number, gain: number) {
  'worklet';
  let y = SAG * Math.sin(Math.PI * u);
  const depth = KNOT_WEIGHT * 4 * uk * (1 - uk);
  y += depth * (u < uk ? u / Math.max(uk, 0.001) : (1 - u) / Math.max(1 - uk, 0.001));
  for (let k = 0; k + 2 < kicks.length; k += 3) {
    const tau = t - kicks[k];
    if (tau <= 0 || tau >= 1.9) continue;
    const env = Math.exp(-tau / WHIP_DECAY) * (1 - easeInOut(seg(tau, 1.4, 1.9))) * kicks[k + 2] * gain;
    for (let n = 1; n <= 4; n++) {
      y += (env / n) * Math.sin(n * Math.PI * kicks[k + 1]) * Math.sin(n * Math.PI * u) * Math.sin(n * 2 * Math.PI * WHIP_HZ * tau);
    }
  }
  return y;
}

/** A tap ripple: 0 → 1 over its life, or −1 when none is showing. */
export function rippleAt(t: number, start: number) {
  'worklet';
  const tau = t - start;
  if (tau < -0.12 || tau > 0.55) return -1;
  return seg(tau, -0.12, 0.55);
}
