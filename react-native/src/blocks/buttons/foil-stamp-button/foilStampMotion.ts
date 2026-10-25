/**
 * The Foil Stamp Button's clock: every value on screen is a closed-form function of the loop time
 * t (seconds), so the frame at t = LOOP is the frame at t = 0. Lengths are points, angles radians.
 *
 *   0.0 – 1.5   rest: blank cotton card, "Seal order" on the brass stamp.
 *   1.5 – 1.62  anticipation: the stamp lifts 4 pt (ease-out).
 *   1.62 – 2.3  it descends (ease-in-out): scale 1 → 0.94, shadows tighten.
 *   2.3         contact: a 0.15 s squash to 0.925, the paper dips round the seal, the blind
 *               emboss rises (ease-out, 0.3 s) and 16 foil flakes spring out to the seal's edge.
 *   2.9 – 3.6   lift: the stamp springs back (ζ 0.45, a little past rest).
 *   3.6 – 5.6   the foil is laid on by a diagonal sweep with a gleam at its front.
 *   4.2 – 4.8   the label turns into a drawn check and "Sealed".
 *   6.6 – 7.6   the foil and the emboss dissolve through a noise threshold; the label returns.
 *   7.6 – 8.0   rest (blank, as at 0).
 */

export const LOOP = 8;

const TAU = Math.PI * 2;

// Stamp travel (pt) and scale.
export const ANTICIPATION = 4;
export const DESCENT = 3;
export const PRESS_SCALE = 0.06;
export const SQUASH = 0.015;

// The light: azimuth (radians, 0 = from the right, −π/2 = from the top) and elevation.
export const LIGHT_AZ = -2.25;
const LIGHT_SWING = 0.5;
export const LIGHT_EL = 0.62;

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

/** 0 before a, 1 after b, linear in between. */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOut(x: number) {
  'worklet';
  const u = 1 - clamp01(x);
  return 1 - u * u * u;
}

export function easeInOut(x: number) {
  'worklet';
  const u = clamp01(x);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

/** A damped spring released from 1 toward 0 at time u (s): ω rad/s, ζ damping ratio. */
export function springFrom1(u: number, omega: number, zeta: number) {
  'worklet';
  if (u <= 0) return 1;
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  const a = zeta * omega;
  return Math.exp(-a * u) * (Math.cos(wd * u) + (a / wd) * Math.sin(wd * u));
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 4.1414) * 43758.5453;
  return s - Math.floor(s);
}

/** Resistance past an edge: follows the finger at first, never further than `limit`. */
export function rubber(x: number, limit: number) {
  'worklet';
  const s = x < 0 ? -1 : 1;
  const a = Math.abs(x);
  return s * limit * (1 - 1 / (1 + a / limit));
}

/** Everything the screen draws, for one frame. */
export type Pose = {
  /** Stamp offset in pt (negative = up) and how far it is pressed (0…1, may overshoot a little). */
  lift: number;
  press: number;
  /** Extra squash at contact (0…1). */
  squash: number;
  /** Emboss height (0 blank … 1 crisp), foil coverage (sweep 0 … 1), dissolve (0 … 1). */
  depth: number;
  foil: number;
  dissolve: number;
  /** The paper's dip round the seal at contact (0…1). */
  ring: number;
  /** Light azimuth (radians). */
  light: number;
  /** Label: 0 "Seal order", 1 check + "Sealed". */
  check: number;
  /** Seconds since the flakes sprang out (negative = none). */
  flakeAge: number;
};

export function restPose(): Pose {
  'worklet';
  return { lift: 0, press: 0, squash: 0, depth: 0, foil: 0, dissolve: 0, ring: 0, light: LIGHT_AZ, check: 0, flakeAge: -1 };
}

export function loopPose(t: number): Pose {
  'worklet';
  // The stamp: up 4 pt, then down onto the paper, held, and sprung back.
  const up = easeOut(seg(t, 1.5, 1.62));
  const down = easeInOut(seg(t, 1.62, 2.3));
  let press = down;
  if (t >= 2.9) press = springFrom1(t - 2.9, 11, 0.45);
  const lift = -ANTICIPATION * up * (1 - down) + DESCENT * press;
  const s = seg(t, 2.3, 2.45);
  const squash = t >= 2.3 ? Math.sin(Math.PI * s) : 0;
  const ring = t >= 2.3 ? Math.sin(Math.PI * seg(t, 2.3, 2.75)) : 0;

  const dissolve = easeInOut(seg(t, 6.6, 7.6));
  const depth = t < 2.3 ? 0 : easeOut(seg(t, 2.3, 2.6));
  const foil = easeInOut(seg(t, 3.6, 5.6));
  const check = easeOut(seg(t, 4.2, 4.8)) * (1 - easeInOut(seg(t, 6.6, 7.0)));
  const light = LIGHT_AZ + LIGHT_SWING * Math.sin((TAU * t) / LOOP);
  return { lift, press, squash, depth, foil, dissolve, ring, light, check, flakeAge: t >= 2.3 ? t - 2.3 : -1 };
}

/** Under reduced motion: the sealed card, still. */
export function sealedPose(): Pose {
  'worklet';
  return { lift: 0, press: 0, squash: 0, depth: 1, foil: 1, dissolve: 0, ring: 0, light: LIGHT_AZ, check: 1, flakeAge: 3 };
}

// Flakes of foil that spring out from under the stamp and settle round the seal.
export const FLAKES = 16;

/** Flake i at `age` s after contact: [x, y, angle, size, alpha] relative to the seal centre. */
export function flake(i: number, age: number, R: number): [number, number, number, number, number] {
  'worklet';
  const a = hash(i) * TAU;
  const out = 1.02 + hash(i + 31) * 0.24;
  const fly = easeOut(age / 0.55);
  // They overshoot a little past where they settle, as they skid on the paper.
  const settle = 1 + 0.08 * Math.sin(Math.PI * clamp01(age / 0.55)) * (1 - clamp01(age / 0.9));
  const r = R * (0.7 + (out - 0.7) * fly) * settle;
  const spin = (hash(i + 7) - 0.5) * 3 * fly + hash(i + 3) * TAU;
  const size = 1.8 + hash(i + 11) * 2.6;
  const alpha = clamp01(age / 0.08);
  return [Math.cos(a) * r, Math.sin(a) * r, spin, size, alpha];
}
