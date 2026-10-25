/*
 The cradle's motion, as one "folded" pendulum.

 An ideal Newton's cradle behaves like a single pendulum angle ψ folded at the vertical: while
 ψ < 0 the left group of n balls hangs at ψ (the rest at 0), while ψ > 0 the right group of n balls
 hangs at ψ. Each zero crossing is a collision: the momentum passes through the still balls and
 the same number leaves on the other side.

 autoPlay (LOOP = 8 s), every frame a pure function of t:
   0.0 – 1.2   rest; a fingertip fades in on the left ball from 1.0.
   1.2 – 2.0   the finger pulls ball 1 out to −15° (ease-in-out).
   2.0 – 6.0   released: ψ = −A(s)·cos(ωs), period 1.02 s, amplitude × 0.97 per collision
               (two collisions a period), so balls 1 and 5 take turns.
   6.0 – 7.4   the amplitude eases to 0 (ease-in-out), the swing dies down.
   7.4 – 8.0   rest. t = 8 is the frame at t = 0.

 Touch: the same folded pendulum, integrated (ψ'' = −ω²·sin ψ, a little air drag, velocity
 × 0.97 at every crossing), from the angle and angular velocity the finger leaves it with.
 */

export const LOOP = 8;
/** Pendulum period (s), and its angular frequency. */
export const PERIOD = 1.02;
export const OMEGA = (2 * Math.PI) / PERIOD;
/** The scripted pull, in radians. */
export const PULL = (15 * Math.PI) / 180;
/** Furthest a finger may pull before the rubber band, and the band's softness (radians). */
export const PULL_LIMIT = (16 * Math.PI) / 180;
export const PULL_SOFT = (2.5 * Math.PI) / 180;
/** Amplitude kept at each collision. */
export const KEEP = 0.97;
export const BALLS = 5;

const PULL_START = 1.2;
const RELEASE = 2.0;
const FADE_START = 6.0;
const FADE_END = 7.4;

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

/** The loop's folded angle ψ at loop time t (radians, n = 1). */
export function loopPsi(t: number) {
  'worklet';
  if (t < PULL_START) return 0;
  if (t < RELEASE) return -PULL * easeInOut(seg(t, PULL_START, RELEASE));
  const s = t - RELEASE;
  const amp = PULL * Math.pow(KEEP, (2 * s) / PERIOD) * (1 - easeInOut(seg(t, FADE_START, FADE_END)));
  return -amp * Math.cos(OMEGA * s);
}

/** Collisions so far in the loop (shown while the balls swing). */
export function loopHits(t: number) {
  'worklet';
  if (t < RELEASE + PERIOD / 4 || t > FADE_END - 0.35) return 0;
  return Math.floor((t - RELEASE - PERIOD / 4) / (PERIOD / 2)) + 1;
}

/** 0 → 1 while the scripted fingertip is on the left ball. */
export function loopFinger(t: number) {
  'worklet';
  return easeOut(seg(t, 1.0, 1.2)) * (1 - easeOut(seg(t, RELEASE, RELEASE + 0.22)));
}

/** How full the energy meter is (1 = the pull, 0 = still), for the loop. */
export function loopEnergy(t: number) {
  'worklet';
  if (t < RELEASE) return easeInOut(seg(t, PULL_START, RELEASE));
  const s = t - RELEASE;
  return Math.pow(KEEP, (2 * s) / PERIOD) * (1 - easeInOut(seg(t, FADE_START, FADE_END)));
}

/** Each ball's angle (radians) for a folded angle ψ and a group of n balls. */
export function ballAngles(psi: number, n: number, out: number[]) {
  'worklet';
  for (let i = 0; i < BALLS; i++) out[i] = 0;
  if (psi < 0) for (let i = 0; i < n; i++) out[i] = psi;
  else if (psi > 0) for (let i = BALLS - n; i < BALLS; i++) out[i] = psi;
  return out;
}

/** Past PULL_LIMIT the pull gets stiffer: limit + soft·(1 − e^(−over/soft)). */
export function rubberBand(theta: number) {
  'worklet';
  const a = Math.abs(theta);
  if (a <= PULL_LIMIT) return theta;
  const over = a - PULL_LIMIT;
  return Math.sign(theta) * (PULL_LIMIT + PULL_SOFT * (1 - Math.exp(-over / PULL_SOFT)));
}

/** The touch simulation: [ψ, ψ', n, hits, energy reference]. Returns 1 when a collision happened. */
export function stepSim(sim: number[], dt: number) {
  'worklet';
  let hit = 0;
  const sub = Math.max(1, Math.ceil(dt / (1 / 240)));
  const h = dt / sub;
  for (let k = 0; k < sub; k++) {
    const before = sim[0];
    sim[1] += -OMEGA * OMEGA * Math.sin(sim[0]) * h;
    sim[1] *= Math.exp(-0.06 * h);
    sim[0] += sim[1] * h;
    if ((before < 0 && sim[0] >= 0) || (before > 0 && sim[0] <= 0)) {
      sim[1] *= KEEP;
      sim[3] += 1;
      hit = 1;
    }
  }
  // Too small to see: stop.
  const amp = Math.sqrt(sim[0] * sim[0] + (sim[1] / OMEGA) * (sim[1] / OMEGA));
  if (amp < 0.004) {
    sim[0] = 0;
    sim[1] = 0;
  }
  return hit;
}

/** The touch simulation's swing amplitude (radians). */
export function simAmplitude(sim: number[]) {
  'worklet';
  return Math.sqrt(sim[0] * sim[0] + (sim[1] / OMEGA) * (sim[1] / OMEGA));
}
