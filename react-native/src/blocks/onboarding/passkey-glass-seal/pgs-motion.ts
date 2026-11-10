// Passkey Glass Seal — the loop. Helpers first: a worklet captures only what is declared above it.
//
// light 0 … 1 runs a glow out from the print's core along its ridges while a finger holds it; unwind
// 0 … 1 pulls the ridges off the print and winds them into the outline of a key; key 0 … 1 fills the
// outline with glass; turn 0 … 1 turns the key a quarter in its lock; flip 0 … 1 turns the card over to
// the welcome on its back (a whole number is a face: even the print, odd the welcome; a spring swings
// it a little past).

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
/** the key's turn: a quarter, overshooting a little and settling, as a lock gives */
export function turnAt(x: number) {
  'worklet';
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  return 1 - Math.exp(-6 * x) * Math.cos(9 * x) * (1 - x);
}

/**
 * the card's turn: the step response of an underdamped spring, t s after it was let go. It swings ~10°
 * past the face it turns to and settles there, as a card flicked over on a table does.
 */
export const FLIP = { zeta: 0.68, omega: 2 * Math.PI * 1.25 } as const;
export function flipSpring(t: number) {
  'worklet';
  if (t <= 0) return 0;
  const z = FLIP.zeta;
  const w = FLIP.omega;
  const wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * t) * (Math.cos(wd * t) + ((z * w) / wd) * Math.sin(wd * t));
}

export const LOOP = 10;
/** the scan line's period: four sweeps a loop */
export const SCAN = 2.5;
/** how long a finger holds before the print is read */
export const HOLD = 1.4;

/* ---------------------------------------------------------------- the loop (10 s)
 1.0  a finger lands on the print      1.2 its ridges light from the core out    2.6 read
 2.7  the ridges unwind into a key     3.9 the key turns to glass   4.4 it turns in its lock
 5.2  the card flips to the welcome    7.6 back     8.3 the key winds back into the print   10 = 0 */
export const T = {
  press: [1.0, 2.75],
  light: [1.2, 2.6],
  unwind: [2.7, 3.9],
  key: [3.7, 4.3],
  turn: [4.4, 5.0],
  flip: [5.2, 5.9],
  back: [7.6, 8.3],
  rewind: [8.2, 9.4],
  unlight: [8.6, 9.5],
} as const;

export type Frame = {
  light: number;
  unwind: number;
  key: number;
  turn: number;
  flip: number;
  scan: number; // the scan line's weight
  finger: number;
  press: number;
};

export function frameAt(t: number): Frame {
  'worklet';
  const light = seg(t, T.light[0], T.light[1]) * (1 - easeInOut(seg(t, T.unlight[0], T.unlight[1])));
  const unwind = easeInOut(seg(t, T.unwind[0], T.unwind[1])) * (1 - easeInOut(seg(t, T.rewind[0], T.rewind[1])));
  const key = easeOut(seg(t, T.key[0], T.key[1])) * (1 - easeInOut(seg(t, T.rewind[0], T.rewind[0] + 0.4)));
  const turn = turnAt(seg(t, T.turn[0], T.turn[1])) * (1 - easeInOut(seg(t, T.back[1] - 0.2, T.rewind[0] + 0.2)));
  const flip = flipSpring(t - T.flip[0]) - flipSpring(t - T.back[0]);
  const scan = 1 - easeOut(seg(t, T.press[0], T.press[0] + 0.3)) + easeOut(seg(t, T.unlight[1] - 0.3, T.unlight[1]));
  const finger = easeOut(seg(t, T.press[0] - 0.2, T.press[0])) * (1 - easeOut(seg(t, T.press[1], T.press[1] + 0.18)));
  const press = finger;
  return { light, unwind, key, turn, flip, scan: clamp01(scan), finger, press };
}
