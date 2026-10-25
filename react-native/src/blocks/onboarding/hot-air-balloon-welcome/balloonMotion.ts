/**
 * Hot Air Balloon Welcome — the autoPlay script. Each onboarding step is an altitude: A = 0 on the
 * ground (step 1), 1 and 2 aloft. Every value is a closed-form function of the loop time t, and
 * periodic motion uses whole multiples of the loop's frequency, so t = LOOP equals t = 0.
 *
 * Every helper is a module-level named worklet declared above its callers.
 */

export const LOOP = 10;
export const STEPS = 3;
/** Gores round the envelope; the colour pattern repeats every PATTERN gores. */
export const GORES = 12;
export const PATTERN = 4;
/** The envelope turns one pattern (4 gores = 120°) per loop. */
export const PATTERN_ANGLE = (2 * Math.PI * PATTERN) / GORES;

export function clamp01(x: number) {
  'worklet';
  return Math.min(Math.max(x, 0), 1);
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOut(x: number) {
  'worklet';
  const t = clamp01(x);
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  const t = clamp01(x);
  return 1 - Math.pow(1 - t, 3);
}

/** A burst of the burner: roars in 0.15 s, holds, dies away in 0.35 s. */
function burst(t: number, a: number, b: number, level: number) {
  'worklet';
  return level * easeOut(seg(t, a, a + 0.15)) * (1 - easeInOut(seg(t, b, b + 0.35)));
}

export type BalloonPose = {
  /** Altitude in steps (0 = ground). */
  alt: number;
  /** Burner heat 0..1 (flame, bloom, the glow inside the envelope, the swell). */
  heat: number;
  /** The step text (continuous index, rolls between steps). */
  step: number;
  /** Envelope turn (radians). */
  turn: number;
  /** Time the flicker and sway are drawn at (s). */
  time: number;
};

export function loopPose(t: number): BalloonPose {
  'worklet';
  const alt =
    easeInOut(seg(t, 2.0, 4.0)) + easeInOut(seg(t, 4.8, 7.0)) - 2 * easeInOut(seg(t, 7.0, 8.8));
  const heat = burst(t, 1.6, 2.6, 1) + burst(t, 4.6, 5.4, 1) + burst(t, 8.0, 8.3, 0.55);
  const step = easeInOut(seg(t, 2.4, 2.9)) + easeInOut(seg(t, 5.2, 5.7)) - 2 * easeInOut(seg(t, 7.6, 8.4));
  return { alt, heat, step, turn: (PATTERN_ANGLE * t) / LOOP, time: t };
}

/** Flame height factor: three sines (whole multiples of the loop) for a living flicker. */
export function flicker(time: number) {
  'worklet';
  const w = (2 * Math.PI) / LOOP;
  return 1 + 0.09 * Math.sin(w * 37 * time) + 0.06 * Math.sin(w * 61 * time + 1.3) + 0.04 * Math.sin(w * 89 * time + 2.1);
}

/** Aloft the balloon sways a little and bobs; on the ground it is still. */
export function sway(time: number, alt: number) {
  'worklet';
  const w = (2 * Math.PI) / LOOP;
  const k = clamp01(alt);
  return [1.3 * k * Math.sin(w * time + 0.4), 2.2 * k * Math.sin(w * 2 * time)];
}
