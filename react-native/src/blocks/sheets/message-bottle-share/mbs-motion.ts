// Message Bottle Share — the loop and the throw. Helpers first: a worklet captures only what is above.

import { type Layout } from './mbs-theme';

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export const LOOP = 10;
export const REST_ANGLE = -0.42;
/** the bottle's bob at the shore: 4 periods per loop */
export function bob(t: number) {
  'worklet';
  const ph = (t / 2.5) * Math.PI * 2;
  return { dy: 4 * Math.sin(ph), da: 0.05 * Math.sin(ph + 0.9) };
}

export const FLIGHT = 1.1; // s in the air
export const DRIFT = 2.4; // s fading into the distance after landing

export type Bottle = { x: number; y: number; angle: number; scale: number; float: number; alpha: number };

/** the bottle in the air, p 0 … 1, from where it was let go to the landing point */
export function flightAt(L: Layout, fromX: number, fromY: number, fromAngle: number, p: number): Bottle {
  'worklet';
  const k = easeOut(p);
  const apex = L.horizon - L.S * 1.2;
  // a quadratic arc through a point high above the middle
  const mx = (fromX + L.landX) / 2;
  const x = (1 - k) * (1 - k) * fromX + 2 * (1 - k) * k * mx + k * k * L.landX;
  const y = (1 - k) * (1 - k) * fromY + 2 * (1 - k) * k * apex + k * k * L.landY;
  return { x, y, angle: fromAngle - 1.4 * Math.PI * k, scale: L.S * (1 - 0.9 * Math.pow(k, 0.8)), float: 0, alpha: 1 };
}

/** after landing: drifting off and fading, tau s */
export function driftAt(L: Layout, tau: number): Bottle {
  'worklet';
  const k = seg(tau, 0, DRIFT);
  return { x: L.landX + 18 * k, y: L.landY - 3 * k, angle: REST_ANGLE * 0.3, scale: L.S * 0.1 * (1 - 0.4 * k), float: 0, alpha: 1 - easeInOut(k) };
}

/** a new bottle rising at the shore, k 0 … 1 */
export function riseAt(L: Layout, t: number, k: number): Bottle {
  'worklet';
  const b = bob(t);
  const e = easeOut(k);
  return { x: L.homeX, y: L.homeY + b.dy + (1 - e) * L.S * 0.6, angle: REST_ANGLE + b.da + (1 - e) * 0.3, scale: L.S, float: 1, alpha: e };
}

export const T = { grab: 1.2, release: 1.7, sentText: 2.4, again: 7.8, rise: [8.0, 9.4] } as const;

export type Frame = Bottle & { splash: number; sent: number; finger: number; fingerX: number; fingerY: number; fingerPress: number };

export function frameAt(t: number, L: Layout): Frame {
  'worklet';
  let bt: Bottle;
  const b = bob(t);
  // the finger takes the bottle at grab, draws it back and up a little, then lets go
  const pull = easeInOut(seg(t, T.grab + 0.05, T.release));
  const heldX = L.homeX - 24 * pull;
  const heldY = L.homeY + b.dy - L.S * 0.35 * pull;
  const heldA = REST_ANGLE + b.da - 0.25 * pull;
  let splash = -1;
  if (t < T.release) bt = { x: heldX, y: heldY, angle: heldA, scale: L.S, float: 1 - pull, alpha: 1 };
  else if (t < T.release + FLIGHT) bt = flightAt(L, heldX, heldY, heldA, (t - T.release) / FLIGHT);
  else if (t < T.rise[0]) {
    bt = driftAt(L, t - T.release - FLIGHT);
    splash = t - T.release - FLIGHT;
  } else bt = riseAt(L, t, seg(t, T.rise[0], T.rise[1]));
  const sent = t >= T.sentText && t < T.again ? 1 : 0;
  const onBottle = easeOut(seg(t, T.grab - 0.15, T.grab)) * (1 - easeOut(seg(t, T.release, T.release + 0.12)));
  const onCta = easeOut(seg(t, T.again - 0.2, T.again - 0.05)) * (1 - easeOut(seg(t, T.again + 0.15, T.again + 0.32)));
  const finger = Math.max(onBottle, onCta);
  const fingerX = onCta > 0 ? L.w / 2 : bt.x - L.S * 0.2;
  const fingerY = onCta > 0 ? L.ctaTop + 28 : bt.y;
  const fingerPress = onCta > 0 ? easeOut(seg(t, T.again - 0.05, T.again + 0.04)) * (1 - easeOut(seg(t, T.again + 0.1, T.again + 0.22))) : onBottle;
  return { ...bt, splash, sent, finger, fingerX, fingerY, fingerPress };
}
