/*
 Viewmaster Reel — the autoPlay timeline and its helpers. Pure functions of the loop time t.

 The reel's position `pos` is in slots (7 per turn); slot k sits at the gate (12 o'clock) when
 pos = k. The disc is drawn turned by −pos × 360/7°, the window shows slot round(pos) mod 7, and
 the window's shutter closes as pos leaves a whole slot (fully shut halfway between two), so a
 change of slide is always a blink — under the finger as well as in the loop.

   0.0 – 1.3   rest on slot 1 (pos 0).
   1.3 – 2.1   advance: the lever is pulled down (0 – 0.3 of the step, ease-in-out), the disc
               turns one slot once the lever is most of the way down (0.28 – 0.72, ease-out-back
               with an 8 % overshoot: the clunk), the lever springs back (0.4 – 1.0, ease-out-back),
               the viewer shudders 1.4 pt as the detent catches.
   3.3 – 4.1   the same to slot 3.          5.3 – 6.1   the same to slot 4.
   7.0 – 8.4   the disc is spun by hand through four slots back round to slot 1 (pos 3 → 7, ease
               in-out with a small dip and overshoot); the shutter flickers at every slot.
   8.4 – 9.0   rest. pos = 7 draws exactly as pos = 0.
 */

export const LOOP = 9;
export const SLOTS = 7;
export const STEP_DEG = 360 / SLOTS;

const ADVANCES = [1.3, 3.3, 5.3];
const ADVANCE_LEN = 0.8;
const SPIN: [number, number] = [7.0, 8.4];

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const u = clamp01((x - a) / (b - a));
  return u * u * (3 - 2 * u);
}

export function easeInOutCubic(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

export function easeOutBack(u: number, c1: number) {
  'worklet';
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(u - 1, 3) + c1 * Math.pow(u - 1, 2);
}

export function easeInOutBack(u: number, c1: number) {
  'worklet';
  const c2 = c1 * 1.525;
  return u < 0.5
    ? (Math.pow(2 * u, 2) * ((c2 + 1) * 2 * u - c2)) / 2
    : (Math.pow(2 * u - 2, 2) * ((c2 + 1) * (u * 2 - 2) + c2) + 2) / 2;
}

/** Positive modulo. */
export function wrap(x: number, m: number) {
  'worklet';
  return ((x % m) + m) % m;
}

/** The window's shutter for a reel position: 0 open on a whole slot, 1 shut halfway between. */
export function shutterFor(pos: number) {
  'worklet';
  const d = Math.abs(pos - Math.round(pos));
  return smoothstep(0.05, 0.3, d);
}

export type ReelPose = { pos: number; lever: number; shake: number };

export function reelPose(t: number): ReelPose {
  'worklet';
  let pos = 0;
  let lever = 0;
  let shake = 0;
  for (let i = 0; i < ADVANCES.length; i += 1) {
    const u = seg(t, ADVANCES[i], ADVANCES[i] + ADVANCE_LEN);
    pos += easeOutBack(seg(u, 0.28, 0.72), 1.6);
    const down = easeInOutCubic(seg(u, 0, 0.3));
    const up = easeOutBack(seg(u, 0.4, 1), 1.2);
    lever += down * (1 - up);
    const tau = (t - (ADVANCES[i] + ADVANCE_LEN * 0.52));
    if (tau > 0 && tau < 0.5) shake += 1.4 * Math.sin(2 * Math.PI * 16 * tau) * Math.exp(-tau * 11);
  }
  pos += 4 * easeInOutBack(seg(t, SPIN[0], SPIN[1]), 0.5);
  return { pos, lever, shake };
}

/** Pulled past a limit, the value follows less and less. */
export function rubber(x: number, lo: number, hi: number, k: number) {
  'worklet';
  if (x < lo) {
    const o = lo - x;
    return lo - o / (1 + o / k);
  }
  if (x > hi) {
    const o = x - hi;
    return hi + o / (1 + o / k);
  }
  return x;
}
