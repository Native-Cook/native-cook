/**
 * Constellation Steps Welcome — the sky's script and the three constellations. Every value is a
 * closed-form function of the loop time t; twinkles use whole multiples of the loop's frequency.
 *
 * Constellations are laid out upright around the hero centre (pt, y down, before scaling); in the
 * sky, constellation k sits k steps of STEP_ANGLE round the pole, so turning the sky by k steps
 * brings it to the centre.
 *
 * Every helper is a module-level named worklet declared above its callers.
 */

export const LOOP = 10;
export const STEPS = 3;
export const STEP_ANGLE = (48 * Math.PI) / 180;

export type Constellation = {
  name: string;
  /** Stars: [x, y, size (pt)]. */
  stars: number[][];
  /** Lines between stars, in drawing order. */
  edges: number[][];
  /** The brightest star, labelled. */
  alpha: number;
  label: string;
};

export const CONSTELLATIONS: Constellation[] = [
  {
    name: 'Cygnus',
    // Deneb, Sadr, η, Albireo, Gienah, left wing tip, δ, right wing tip.
    stars: [
      [0, -112, 3.4],
      [2, -22, 2.6],
      [6, 42, 1.9],
      [12, 108, 2.4],
      [-78, -38, 2.4],
      [-124, -2, 1.8],
      [74, -48, 2.2],
      [118, -24, 1.7],
    ],
    edges: [
      [0, 1],
      [1, 2],
      [2, 3],
      [1, 4],
      [4, 5],
      [1, 6],
      [6, 7],
    ],
    alpha: 0,
    label: 'Deneb',
  },
  {
    name: 'Cassiopeia',
    // Caph, Schedar, Navi, Ruchbah, Segin.
    stars: [
      [112, -30, 2.4],
      [58, 30, 3.0],
      [2, -14, 2.7],
      [-56, 38, 2.4],
      [-110, -8, 2.0],
    ],
    edges: [
      [0, 1],
      [1, 2],
      [2, 3],
      [3, 4],
    ],
    alpha: 1,
    label: 'Schedar',
  },
  {
    name: 'Orion',
    // Meissa, Betelgeuse, Bellatrix, Alnitak, Alnilam, Mintaka, Saiph, Rigel.
    stars: [
      [0, -122, 1.7],
      [-62, -86, 3.4],
      [56, -78, 2.6],
      [-24, 2, 2.4],
      [0, -6, 2.5],
      [24, -14, 2.3],
      [-46, 98, 2.3],
      [62, 104, 3.2],
    ],
    edges: [
      [0, 1],
      [0, 2],
      [1, 3],
      [2, 5],
      [3, 4],
      [4, 5],
      [3, 6],
      [5, 7],
    ],
    alpha: 7,
    label: 'Rigel',
  },
];

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

export type SkyPose = {
  /** Sky turn round the pole (radians). */
  theta: number;
  /** How far each constellation's lines are drawn (0..1). */
  drawn: number[];
  /** Lines and outlines fade with this (the return home). */
  fade: number;
  /** The step text (continuous index). */
  step: number;
  /** Twinkle time (s). */
  time: number;
};

export function loopPose(t: number): SkyPose {
  'worklet';
  const theta =
    STEP_ANGLE * (easeInOut(seg(t, 3.6, 4.4)) + easeInOut(seg(t, 6.2, 7.0)) - 2 * easeInOut(seg(t, 8.4, 9.4)));
  const drawn = [seg(t, 1.6, 3.6), seg(t, 4.4, 6.2), seg(t, 7.0, 8.4)];
  const step = easeInOut(seg(t, 3.7, 4.3)) + easeInOut(seg(t, 6.3, 6.9)) - 2 * easeInOut(seg(t, 8.5, 9.3));
  return { theta, drawn, fade: 1 - easeInOut(seg(t, 8.4, 9.2)), step, time: t };
}
