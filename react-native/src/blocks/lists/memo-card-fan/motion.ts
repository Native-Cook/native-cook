// A single deterministic clock drives every autoplay frame. This keeps t=0 and t=LOOP_MS
// identical and makes the deep-link freeze exact.
export const LOOP_MS = 8000;

// Hero (the open folder): the front print is pulled out of the pocket like a card from a hand,
// the two side prints spread wider as it leaves, it is held up with a deep shadow, then slid home.
export const PULL = {
  dipAt: 900, dipMs: 120, dip: -0.05, // anticipation: pressed a little into the pocket
  outAt: 1020, out: { omega: 12.5, zeta: 0.78 },
  homeAt: 2700, home: { omega: 13, zeta: 0.92 },
  rise: 118, // pin px up: its bottom clears the pocket's edge (343 -> 225, edge at 239)
  clear: 0.9, // share of the rise at which it is out of the pocket and moves in front of it
  scale: 0.1,
  rot: -4, // degrees, the hand turns the card a little as it lifts
  shadow: { opacity: 0.55, radius: [6, 22], dy: [4, 24] },
  sheen: 0.28, // a soft light band crosses the photo as it turns
  dim: 0.3, // the prints left in the hand fall back into shade while it is out
} as const;
export const SPREAD = {
  at: 1120, out: { omega: 15, zeta: 0.6 }, // a little overshoot, like fingers opening
  backAt: 2850, back: { omega: 14, zeta: 1 },
  rot: 10, // degrees added to each side print's 22.5
  dx: 14, // pin px further out (more would push the left print off the screen)
  dy: -12,
} as const;
export const TOP_DELAY = 2800; // the closed folder's open-and-close plays after the hero
export const RESUME_AFTER_MS = 3500;

export const OPEN = { duration: 520, dampingRatio: 0.84 };
export const CLOSE = { duration: 420, dampingRatio: 0.9 };
export const FLING = { duration: 430, dampingRatio: 0.82 };
export const STAGGER = 0.12; // each side print starts this share of the progress later
export const PRESS = { scale: 0.97, in: 120, out: 150 };
export const RM_FADE = 200;

function clamp01(value: number) {
  'worklet';
  return value < 0 ? 0 : value > 1 ? 1 : value;
}

function smoother(value: number) {
  'worklet';
  const x = clamp01(value);
  return x * x * x * (x * (x * 6 - 15) + 10);
}

/** A spring released at `at` from 0 towards 1 (0 before it). */
export function springStep(t: number, at: number, omega: number, zeta: number) {
  'worklet';
  const s = (t - at) / 1000;
  if (s <= 0) return 0;
  if (zeta >= 1) return 1 - (1 + omega * s) * Math.exp(-omega * s);
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * omega * s) * (Math.cos(wd * s) + ((zeta * omega) / wd) * Math.sin(wd * s));
}

function wrap(input: number) {
  'worklet';
  return ((input % LOOP_MS) + LOOP_MS) % LOOP_MS;
}

export function pullAt(input: number) {
  'worklet';
  const t = wrap(input);
  const dip = t > PULL.dipAt && t < PULL.outAt + 120 ? PULL.dip * Math.sin(Math.min(1, (t - PULL.dipAt) / (PULL.outAt + 120 - PULL.dipAt)) * Math.PI) : 0;
  return dip + springStep(t, PULL.outAt, PULL.out.omega, PULL.out.zeta) - springStep(t, PULL.homeAt, PULL.home.omega, PULL.home.zeta);
}

export function spreadAt(input: number) {
  'worklet';
  const t = wrap(input);
  return springStep(t, SPREAD.at, SPREAD.out.omega, SPREAD.out.zeta) - springStep(t, SPREAD.backAt, SPREAD.back.omega, SPREAD.back.zeta);
}

function springRise(value: number) {
  'worklet';
  const x = clamp01(value);
  const end = 1 - Math.exp(-6.4) * Math.cos(9.4);
  return (1 - Math.exp(-6.4 * x) * Math.cos(9.4 * x)) / end;
}

export function topOpenAt(input: number) {
  'worklet';
  const t = wrap(input) - TOP_DELAY;
  if (t < 900) return 0;
  if (t < 1050) return -0.025 * Math.sin(((t - 900) / 150) * Math.PI);
  if (t < 1740) return springRise((t - 1050) / 690);
  if (t < 2600) return 1;
  if (t < 3240) return 1 - smoother((t - 2600) / 640);
  return 0;
}

export function bottomOpenAt(_input: number) {
  'worklet';
  return 1; // the open folder stays open; its prints carry the hero (pullAt, spreadAt)
}
