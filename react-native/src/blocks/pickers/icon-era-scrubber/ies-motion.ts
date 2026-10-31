// Icon Era Scrubber — the year, the era it falls in, and the autoPlay loop as pure functions of time.
// Helpers first: worklets capture what is above them.

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function smooth(a: number, b: number, x: number) {
  'worklet';
  const u = clamp01((x - a) / (b - a));
  return u * u * (3 - 2 * u);
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

export const FIRST = 2008;
export const LAST = 2026;
// the eras change over a year and a half of the ruler, so a finger can stop half-way through a change
const KEYS = [FIRST, 2011.5, 2013, 2018, 2019.5, 2023.5, 2025, LAST];
const ERAS = [0, 0, 1, 1, 2, 2, 3, 3];

/** The era at a year: 0 glossy, 1 flat, 2 soft, 3 glass, blended across each change. */
export function eraOf(year: number) {
  'worklet';
  if (year <= KEYS[0]) return 0;
  for (let i = 1; i < KEYS.length; i++) {
    if (year <= KEYS[i]) {
      const u = (year - KEYS[i - 1]) / (KEYS[i] - KEYS[i - 1]);
      return ERAS[i - 1] + (ERAS[i] - ERAS[i - 1]) * u;
    }
  }
  return 3;
}

export const LOOP = 10;
// [from s, to s, to year]; a finger drags the ruler forward through the eras, holding half-way through
// two changes, then flicks it back to 2008
const MOVES: [number, number, number][] = [
  [1.0, 2.0, 2012.3],
  [2.5, 3.3, 2016],
  [3.6, 4.6, 2020],
  [4.9, 5.8, 2024.3],
  [6.3, 6.9, LAST],
  [7.8, 9.4, FIRST],
];

export function yearAt(t: number) {
  'worklet';
  let y = FIRST;
  for (let i = 0; i < MOVES.length; i++) {
    const m = MOVES[i];
    if (t >= m[0]) {
      const from = y;
      // the flick back glides out (ease-out), the drags follow the finger (ease-in-out)
      const k = i === MOVES.length - 1 ? easeOut(seg(t, m[0], m[1])) : easeInOut(seg(t, m[0], m[1]));
      y = from + (m[2] - from) * k;
    }
  }
  return y;
}

export type Frame = {
  year: number;
  lean: number; // with the ruler's speed, −1 … 1
  finger: number;
  fingerX: number; // the finger's offset along the ruler from where it grabbed (pt / STEP, in years)
};

export function frameAt(t: number): Frame {
  'worklet';
  const year = yearAt(t);
  const v = (yearAt(t + 0.03) - yearAt(t - 0.03)) / 0.06;
  let finger = 0;
  let fingerX = 0;
  for (let i = 0; i < MOVES.length; i++) {
    const m = MOVES[i];
    const a = m[0];
    const b = i === MOVES.length - 1 ? a + 0.25 : m[1];
    const p = easeOut(seg(t, a - 0.12, a)) * (1 - easeOut(seg(t, b, b + 0.12)));
    if (p > 0) {
      finger = p;
      const from = i === 0 ? FIRST : MOVES[i - 1][2];
      fingerX = i === MOVES.length - 1 ? -2.5 * easeOut(seg(t, a, a + 0.25)) : year - from;
    }
  }
  return { year, lean: Math.max(-1, Math.min(1, v * 0.18)), finger, fingerX };
}
