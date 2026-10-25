/*
 Icing Name Form — the autoPlay script; every value is a pure function of the loop time t.

   0.0 – 1.4   rest: "Happy birthday" piped in chocolate, the nozzle resting at the upper right.
   1.4 – 2.2   a palette knife sweeps left to right and lifts the writing off; the field empties.
   2.0 – 2.35  the nozzle comes down to the cake; "Noor" is typed (2.25 – 2.55).
   2.35 – 5.4  "Noor" is piped stroke by stroke, the nozzle lifting between strokes.
   5.5 – 5.95  the nozzle goes back to rest.
   5.6 – 6.4   the Rose chip is chosen: rose icing flows through the letters (5.65 – 6.35).
   6.4 – 7.4   hold.
   7.4 – 8.5   the turntable hands over to the next cake: this one slides out to the left with a
               quarter turn, a fresh cake with "Happy birthday" slides in from the right, the
               field, chip and price go back (7.9).
   8.5 – 9.0   rest.
 */

export const LOOP = 9;
export const REST_TEXT = 'Happy birthday';
export const SECOND_TEXT = 'Noor';
export const PIPE_FROM = 2.35;
export const PIPE_TO = 5.4;

export const TEXT_SCRIPT: [number, string][] = [
  [0, REST_TEXT],
  [1.5, 'Happy birth'],
  [1.65, 'Happy'],
  [1.8, 'Hap'],
  [1.95, ''],
  [2.25, 'N'],
  [2.35, 'No'],
  [2.45, 'Noo'],
  [2.55, SECOND_TEXT],
  [7.9, REST_TEXT],
];

/** Price in cents over the loop: base cake $38, 50 ¢ a letter, $1 for a coloured icing. */
const PRICE_SCRIPT: [number, number][] = [
  [0, 4450],
  [1.95, 3800],
  [2.6, 4000],
  [5.7, 4100],
  [7.9, 4450],
];
export const PRICE_ROLL = 0.45;

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - a) / (b - a)));
}

export function easeInOut(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

export function easeOut(u: number) {
  'worklet';
  return 1 - Math.pow(1 - u, 3);
}

export function easeOutBack(u: number) {
  'worklet';
  const c1 = 0.9;
  return 1 + (c1 + 1) * Math.pow(u - 1, 3) + c1 * Math.pow(u - 1, 2);
}

export function scriptIndex(t: number) {
  'worklet';
  let i = 0;
  for (let k = 0; k < TEXT_SCRIPT.length; k += 1) if (t >= TEXT_SCRIPT[k][0]) i = k;
  return i;
}

/** The price: [from cents, to cents, progress 0..1]. */
export function priceAt(t: number): number[] {
  'worklet';
  let i = 0;
  for (let k = 0; k < PRICE_SCRIPT.length; k += 1) if (t >= PRICE_SCRIPT[k][0]) i = k;
  const from = i === 0 ? PRICE_SCRIPT[PRICE_SCRIPT.length - 1][1] : PRICE_SCRIPT[i - 1][1];
  const p = i === 0 ? 1 : easeInOut(seg(t, PRICE_SCRIPT[i][0], PRICE_SCRIPT[i][0] + PRICE_ROLL));
  return [from, PRICE_SCRIPT[i][1], p];
}

/** Knife position across the cake, -1 (left edge) → 1 (right edge); outside ±1.3 it is away. */
export function knifeAt(t: number) {
  'worklet';
  if (t < 1.4 || t > 2.2) return -2;
  return -1.35 + 2.7 * easeInOut(seg(t, 1.4, 2.2));
}

/** Piping progress of the second message, 0..1 of its schedule. */
export function pipeProgress(t: number) {
  'worklet';
  return seg(t, PIPE_FROM, PIPE_TO);
}

/** Rose flowing through the letters, 0..1 of their ink length. */
export function roseFlow(t: number) {
  'worklet';
  return easeInOut(seg(t, 5.65, 6.35));
}

/** Chip ring position (0 Chocolate, 1 Rose). */
export function chipPosition(t: number) {
  'worklet';
  return easeOutBack(seg(t, 5.6, 5.9)) * (1 - easeOutBack(seg(t, 7.9, 8.2)));
}

/** The cake hand-over: 0 → the Noor cake is centred, 1 → the next cake is. */
export function slideAt(t: number) {
  'worklet';
  return easeInOut(seg(t, 7.4, 8.5));
}

/** 0 = nozzle at rest, 1 = nozzle at work on the cake. */
export function nozzleWork(t: number) {
  'worklet';
  return easeInOut(seg(t, 2.0, 2.35)) * (1 - easeInOut(seg(t, 5.5, 5.95)));
}
