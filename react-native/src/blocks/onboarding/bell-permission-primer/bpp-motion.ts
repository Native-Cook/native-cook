// Bell Permission Primer — the loop and the bell's physics. Helpers first: a worklet captures only
// what is declared above it.
//
// The bell hangs from its crown in the hero's disc. swing is its angle (rad, in the screen's plane),
// clap the clapper's, which hangs on a longer arm, lags and strikes the rim when it leans GAP from the
// bell: every strike sends a ring over the page. Drawn up (lift), the bell stretches towards the
// Dynamic Island like taffy; let go near it and the island swallows it (suck), then opens into the
// notification it grants (expand). Closed again, the island drops the bell back.

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
/** the island's spring as it opens into the notification: damping ratio 0.7 at 2 Hz, so the slab
 * overshoots by about 5 % (6 pt a side) and settles, as the Dynamic Island does (the same spring drives a touch) */
export const OPEN = { zeta: 0.7, omega: 4 * Math.PI } as const;
export function openSpring(t: number) {
  'worklet';
  if (t <= 0) return 0;
  const z = OPEN.zeta;
  const w = OPEN.omega;
  const wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * t) * (Math.cos(wd * t) + ((z * w) / wd) * Math.sin(wd * t));
}
export function smooth(x: number) {
  'worklet';
  return x * x * (3 - 2 * x);
}

export const LOOP = 10;
export const REST = { yaw: -0.22, pitch: -0.12, roll: 0 } as const;
/** a swing of the bell: one period, s */
export const PERIOD = 1.05;
/** how far the clapper can lean from the bell before it strikes the rim, rad */
export const GAP = 0.13;

export const T = {
  grab: 0.9,
  let: 1.15, // the finger lets go
  calm: [3.1, 3.7],
  lift: [4.0, 4.75], // the finger draws the bell up towards the island
  suck: [4.75, 5.05], // it lets go and the island takes the bell
  expand: [5.05, 5.6], // the island opens into the notification
  collapse: [7.8, 8.2], // tapped: it closes
  emerge: [8.2, 8.8], // and drops the bell back
  strikes: [1.42, 1.94, 2.47],
} as const;
/** how far up the finger draws the bell, in pt at full lift */
export const LIFT = 150;

export type Frame = {
  swing: number;
  clap: number;
  lift: number;
  suck: number;
  expand: number;
  rings: [number, number, number];
  finger: number;
  fingerAt: number; // 0 bell, 2 the notification
  fingerX: number; // bell: share of the bell's half width
  fingerPress: number;
  bob: number;
};

/** The swing after a flick of amplitude a at time 0, as the loop plays it. */
export function swingAt(tau: number, a: number) {
  'worklet';
  if (tau <= 0) return a;
  const w = (2 * Math.PI) / PERIOD;
  return a * Math.exp(-0.75 * tau) * Math.cos(w * tau);
}

/** a fall that lands at 1 with a small bounce */
export function bounceIn(x: number) {
  'worklet';
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  if (x < 0.62) return (x / 0.62) * (x / 0.62);
  const u = (x - 0.62) / 0.38;
  return 1 - 0.09 * Math.sin(Math.PI * u) * (1 - u);
}

export function frameAt(t: number): Frame {
  'worklet';
  const TAU = Math.PI * 2;
  const A = 0.42;
  const calm = 1 - easeInOut(seg(t, T.calm[0], T.calm[1]));
  let swing = 0;
  let clap = 0;
  if (t >= T.grab && t < T.calm[1]) {
    if (t < T.let) swing = A * easeOut(seg(t, T.grab, T.let));
    else swing = swingAt(t - T.let, A);
    swing *= calm;
    // the clapper hangs behind the bell and is thrown against its rim
    const tau = Math.max(0, t - T.let);
    const w = (2 * Math.PI) / PERIOD;
    const raw = t < T.let ? swing * 0.7 : A * 1.12 * Math.exp(-0.6 * tau) * Math.cos(w * tau - 0.9) * calm;
    clap = swing + Math.max(-GAP, Math.min(GAP, raw - swing));
  }
  // dropped back out of the island, it swings a little and settles
  if (t >= T.emerge[1]) {
    const tau = t - T.emerge[1];
    const k = 1 - smooth(seg(t, 9.4, 9.95));
    swing += 0.12 * Math.exp(-1.6 * tau) * Math.sin(((2 * Math.PI) / PERIOD) * tau) * k;
    clap += 0.16 * Math.exp(-1.4 * tau) * Math.sin(((2 * Math.PI) / PERIOD) * tau - 0.7) * k;
  }
  // a slow sway at rest, whole periods per loop
  swing += 0.012 * Math.sin((2 * TAU * t) / LOOP);
  clap += 0.012 * Math.sin((2 * TAU * t) / LOOP - 0.4);
  // drawn up by the finger, taken by the island, opened, closed, dropped back
  const lift = easeInOut(seg(t, T.lift[0], T.lift[1])) * (t < T.suck[1] ? 1 : 0);
  let suck = t < T.emerge[0] ? Math.pow(seg(t, T.suck[0], T.suck[1]), 2) : 1 - bounceIn(seg(t, T.emerge[0], T.emerge[1]));
  if (t < T.suck[0]) suck = 0;
  const expand = openSpring(t - T.expand[0]) * (1 - easeInOut(seg(t, T.collapse[0], T.collapse[1])));
  const rings: [number, number, number] = [0, 0, 0];
  for (let i = 0; i < 3; i++) rings[i] = t > T.strikes[i] && t < T.strikes[i] + 0.9 ? seg(t, T.strikes[i], T.strikes[i] + 0.9) : 0;
  let finger = 0;
  let fingerAt = 0;
  let fingerX = 0;
  let fingerPress = 0;
  if (t < 2) {
    finger = easeOut(seg(t, T.grab - 0.15, T.grab)) * (1 - easeOut(seg(t, T.let, T.let + 0.18)));
    fingerX = -0.1 + 0.55 * easeOut(seg(t, T.grab, T.let));
    fingerPress = finger;
  } else if (t < 6.5) {
    finger = easeOut(seg(t, T.lift[0] - 0.18, T.lift[0] - 0.02)) * (1 - easeOut(seg(t, T.lift[1], T.lift[1] + 0.16)));
    fingerPress = finger;
  } else {
    const at = T.collapse[0] - 0.1;
    finger = easeOut(seg(t, at - 0.15, at - 0.02)) * (1 - easeOut(seg(t, at + 0.14, at + 0.3)));
    fingerAt = 2;
    fingerPress = easeOut(seg(t, at - 0.02, at + 0.08)) * (1 - easeOut(seg(t, at + 0.12, at + 0.26)));
  }
  const bob = 0.015 * Math.sin((TAU * t) / LOOP);
  return { swing, clap, lift, suck, expand, rings, finger, fingerAt, fingerX, fingerPress, bob };
}
