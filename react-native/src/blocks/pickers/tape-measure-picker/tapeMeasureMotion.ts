/*
 The tape's value (cm under the red hairline) and its wobble.

 autoPlay (LOOP = 8 s). The value is 178 plus a sum of spring steps, each a closed-form damped
 response that starts at rest, so the value and its speed are pure functions of t:
   0.0 – 1.3   rest at 178 cm.
   1.3 – 1.42  anticipation: the tape slips back 0.5 cm.
   1.42 – 3.8  pulled out to 192 (ω 6 rad/s, ζ 0.72: ~3 % overshoot), wobbling while it moves.
   3.8 – 5.4   let go: it retracts to 165 with a whip (ω 11, ζ 0.6: ~2 cm overshoot, the free end
               waves hardest right after the stop).
   6.6 – 7.6   pulled back to 178 (ω 7.5, ζ 0.8).
   7.6 – 8.0   rest (the last residual is faded out so t = 8 is exactly t = 0).

 Wobble: a travelling wave along the tape (3 Hz, a whole number of waves per loop), its height
 growing toward the free end; its amplitude follows the tape's recent speed (a short decaying
 memory, so a sudden stop keeps the tape waving for ~0.3 s).
 */

export const LOOP = 8;
export const REST = 178;
export const MIN = 120;
export const MAX = 230;
/** Points per centimetre on screen. */
export const PPCM = 20;
/** Wave frequency (Hz) and wavelength (pt). */
export const WAVE_HZ = 3;
export const WAVE_LENGTH = 170;
export const WAVE_MAX = 6;

/** [start, change, ω, ζ] */
const STEPS: number[][] = [
  [1.3, -0.5, 18, 0.9],
  [1.42, 14.5, 6, 0.72],
  [3.8, -27, 11, 0.6],
  [6.6, 13, 7.5, 0.8],
];

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** Step response of a spring released from rest: [position 0 → 1, speed]. */
export function springStep(tau: number, w: number, z: number) {
  'worklet';
  if (tau <= 0) return [0, 0];
  const wd = w * Math.sqrt(1 - z * z);
  const e = Math.exp(-z * w * tau);
  const c = Math.cos(wd * tau);
  const s = Math.sin(wd * tau);
  return [1 - e * (c + ((z * w) / wd) * s), e * ((w * w) / wd) * s];
}

/** The loop's value and speed (cm, cm/s) at loop time t. */
export function loopValue(t: number) {
  'worklet';
  let v = REST;
  let speed = 0;
  for (let i = 0; i < STEPS.length; i++) {
    const st = STEPS[i];
    const r = springStep(t - st[0], st[2], st[3]);
    v += st[1] * r[0];
    speed += st[1] * r[1];
  }
  const fade = 1 - seg(t, 7.6, 7.95);
  return [REST + (v - REST) * fade, speed * fade];
}

export function wobbleFromSpeed(speed: number) {
  'worklet';
  return WAVE_MAX * (1 - Math.exp(-speed / 70));
}

/** The wobble's amplitude: the strongest recent speed, remembered with a 0.3 s decay. */
export function loopWobble(t: number) {
  'worklet';
  let s = 0;
  for (let j = 0; j < 7; j++) {
    const tau = j * 0.06;
    const sp = Math.abs(loopValue(Math.max(0, t - tau))[1]) * Math.exp(-tau / 0.3);
    if (sp > s) s = sp;
  }
  return wobbleFromSpeed(s);
}

/** Rubber band past the ends of the scale (cm). */
export function band(v: number) {
  'worklet';
  if (v > MAX) {
    const o = v - MAX;
    return MAX + o / (1 + o / 8);
  }
  if (v < MIN) {
    const o = MIN - v;
    return MIN - o / (1 + o / 8);
  }
  return v;
}

/** The inverse of `band`, so a drag that starts past an end keeps its place. */
export function unband(v: number) {
  'worklet';
  if (v > MAX) {
    const o = Math.min(v - MAX, 7.9);
    return MAX + o / (1 - o / 8);
  }
  if (v < MIN) {
    const o = Math.min(MIN - v, 7.9);
    return MIN - o / (1 - o / 8);
  }
  return v;
}

/** One centimetre of height in feet and inches, e.g. "5 ft 10 in". */
export function feetInches(cm: number) {
  const inches = Math.round(cm / 2.54);
  return `${Math.floor(inches / 12)} ft ${inches % 12} in`;
}
