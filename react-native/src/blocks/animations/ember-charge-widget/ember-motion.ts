// Ember — timings and the small math the worklets share.
// Every function here runs on the UI thread.

// The self-playing loop, in seconds: rest, charge, ignite and hold, cool.
const loop = { rest: 0.8, charged: 3.0, held: 5.4, end: 6.2 } as const;

export const M = {
  // Holding: the charge fills over this long (900 ms with Reduce Motion on).
  charge: 2200,
  chargeReduced: 900,
  // At the goal: hold, then cool back to the resting count.
  hold: (loop.held - loop.charged) * 1000,
  cool: 800,
  // Letting go early: the charge springs back.
  release: { duration: 450, dampingRatio: 0.75 },
  // Seconds the ignition plays: flash, shockwave, embers, slam.
  ignite: 1.1,
  press: 120,
  // Let go before the ignition pop peaks, even if the finger stays down.
  pressRelease: 60,
  // A short follow and a slower return. Critical damping carries velocity
  // without ringing back and forth; the loop's figure stays below 0.35.
  aim: { follow: { duration: 240, dampingRatio: 1 }, release: { duration: 480, dampingRatio: 1 }, x: 0.3, y: 0.2 },
  quality: { window: 2000, frame: 24, allowed: 6 },
  // Stripe drift in points per second, at rest and fully charged.
  stripeIdle: 18,
  stripeMax: 260,
  // Bar: unit-mass meniscus spring, velocity drag in seconds. Ignition
  // rounds the tube in 65 ms, relaxes over 240 ms and rings at 15 rad/s.
  bar: { stiffness: 360, damping: 16, drag: 0.04, fuseIn: 0.065, fuseOut: 0.24, ringDecay: 9, ringFrequency: 15 },
  // The light is four inner shadows pushed up from the floor; this scales
  // how far, at rest and fully charged.
  rise: { rest: 1, hot: 1.28 },
  // Its lean is a damped spring; these kick it.
  slosh: { stiffness: 90, damping: 7, press: 0.35, release: -0.5, ignite: 1.1 },
  // The card fades in once the canvases have drawn (see the live gate).
  enter: 320,
  // After a touch the self-playing loop waits this long at rest, then resumes.
  resume: 2500,
  loop,
} as const;

export function seg(t: number, from: number, to: number) {
  "worklet";
  return Math.min(1, Math.max(0, (t - from) / (to - from)));
}

export function easeInOut(x: number) {
  "worklet";
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOut(x: number) {
  "worklet";
  return 1 - Math.pow(1 - x, 4);
}

/** The charge, 0…1, at second `t` of the self-playing loop. */
export function chargeAt(t: number) {
  "worklet";
  const L = M.loop;
  if (t < L.rest) return 0;
  if (t < L.charged) return easeInOut((t - L.rest) / (L.charged - L.rest));
  if (t < L.held) return 1;
  return 1 - easeInOut((t - L.held) / (L.end - L.held));
}

/** Time since ignition in shares of `M.ignite` at second `t` of the loop, 0 before it. */
export function ignitionAt(t: number) {
  "worklet";
  return t < M.loop.charged ? 0 : Math.min(1, (t - M.loop.charged) / M.ignite);
}

/** A lub-dub heart pulse at beat phase 0…1: 1 at rest, 1.12 at the peak. */
export function beat(phase: number) {
  "worklet";
  const ms = phase * 430;
  if (ms < 90) return 1 + 0.12 * easeOut(ms / 90);
  if (ms < 200) return 1.12 - 0.12 * easeOut((ms - 90) / 110);
  if (ms < 270) return 1 + 0.07 * easeOut((ms - 200) / 70);
  return 1.07 - 0.07 * easeOut(Math.min(1, (ms - 270) / 160));
}
