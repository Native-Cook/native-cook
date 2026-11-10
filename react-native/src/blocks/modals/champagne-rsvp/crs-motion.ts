// Champagne RSVP — the loop. Helpers first: a worklet captures only what is declared above it.

import { clinkLean, FEET_GAP } from './crs-theme';

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export const LOOP = 10;
/** the glasses at rest lean a little apart */
export const REST_LEAN = 0.05;
export const T = { accept: 1.0, swing: [1.1, 1.6], clink: 1.6, back: [1.6, 2.8], burst: [1.6, 3.0], reset: [8.0, 8.5] } as const;

/** a clink at tau = 0: the glasses meet, bounce off and swing back to rest (left lean; the right is its mirror) */
export function clinkLeanAt(tau: number) {
  'worklet';
  const meet = clinkLean(FEET_GAP);
  if (tau < 0) return -REST_LEAN;
  // after the touch the glasses bounce a hair apart, then are drawn back to rest by the hands
  const k = tau / 1.2;
  if (k >= 1) return -REST_LEAN;
  const back = (meet + REST_LEAN) * (1 - easeInOut(k));
  const bounce = -0.035 * Math.sin(Math.PI * Math.min(1, k / 0.12));
  return -REST_LEAN + back + bounce;
}

export type Frame = { lean: number; clink: number; burst: number; done: number; finger: number; fingerPress: number; phase: number };

export function frameAt(t: number): Frame {
  'worklet';
  const meet = clinkLean(FEET_GAP);
  let lean = -REST_LEAN;
  if (t >= T.swing[0] && t < T.clink) {
    // the glasses swing towards each other, faster as they come (ease-in)
    const k = seg(t, T.swing[0], T.swing[1]);
    lean = -REST_LEAN + (meet + REST_LEAN) * k * k;
  } else if (t >= T.clink) lean = clinkLeanAt(t - T.clink);
  const clink = t > T.clink && t < T.clink + 0.9 ? seg(t, T.clink, T.clink + 0.9) : 0;
  const burst = t > T.burst[0] && t < T.burst[1] ? seg(t, T.burst[0], T.burst[1]) : 0;
  const done = t >= T.clink + 0.15 && t < T.reset[0] ? 1 : 0;
  const finger = easeOut(seg(t, T.accept - 0.15, T.accept - 0.02)) * (1 - easeOut(seg(t, T.accept + 0.14, T.accept + 0.3)));
  const fingerPress = easeOut(seg(t, T.accept - 0.02, T.accept + 0.08)) * (1 - easeOut(seg(t, T.accept + 0.12, T.accept + 0.26)));
  const phase = (t / LOOP) * 6;
  return { lean, clink, burst, done, finger, fingerPress, phase };
}
