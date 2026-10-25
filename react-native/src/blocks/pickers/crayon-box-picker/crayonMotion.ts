/*
 Crayon Box Picker — the autoPlay script. Every value is a pure function of the loop time t.

   0.0 – 1.2   rest: twelve crayons standing in their box, Sky is the colour, its swatch on the paper.
   1.2 – 2.4   Tangerine is pulled up against the box's friction (ease-out, 0.7 s), wobbles as it
               comes free, its neighbours lean into the gap.
   2.4 – 2.75  it swings down onto the paper (arc, turning to a writing angle, a little lift).
   2.75 – 3.75 it scribbles a swatch: back and forth in wax, rows working down; the Sky swatch fades.
   3.75        Tangerine becomes the colour (the name rolls).
   3.8 – 4.6   it goes home: back up along the arc, then slides down into its slot (spring settle).
   4.8 – 8.0   the same for Sky: pull 4.8 – 5.8, onto the paper 5.8 – 6.15, scribble 6.15 – 7.0,
               home 7.0 – 8.0.
   8.0 – 9.0   rest.
 */

export const LOOP = 9;

export const CRAYONS: { name: string; hex: string; rgb: number[] }[] = [
  { name: 'Cherry', hex: '#D23A43', rgb: [0.82, 0.23, 0.26] },
  { name: 'Tangerine', hex: '#F07A2A', rgb: [0.94, 0.48, 0.16] },
  { name: 'Lemon', hex: '#F2C531', rgb: [0.95, 0.77, 0.19] },
  { name: 'Lime', hex: '#86B83A', rgb: [0.53, 0.72, 0.23] },
  { name: 'Moss', hex: '#4F7A3A', rgb: [0.31, 0.48, 0.23] },
  { name: 'Ocean', hex: '#1F6F9E', rgb: [0.12, 0.44, 0.62] },
  { name: 'Sky', hex: '#5BB2E6', rgb: [0.36, 0.7, 0.9] },
  { name: 'Grape', hex: '#7A4FB0', rgb: [0.48, 0.31, 0.69] },
  { name: 'Bubblegum', hex: '#F08BB6', rgb: [0.94, 0.55, 0.71] },
  { name: 'Coral', hex: '#F2876E', rgb: [0.95, 0.53, 0.43] },
  { name: 'Cocoa', hex: '#7A4A2C', rgb: [0.48, 0.29, 0.17] },
  { name: 'Midnight', hex: '#2B3350', rgb: [0.17, 0.2, 0.31] },
];
export const TANGERINE = 1;
export const SKY = 6;
export const REST_COLOUR = SKY;

/**
 * One pick, as times: [pull from, pull to, fly to paper end, scribble end, fly home end, settle end].
 * The pull itself (0..1) is eased with friction; a user pick starts at its release instead.
 */
export type Pick = { crayon: number; times: number[] };

export const LOOP_PICKS: Pick[] = [
  { crayon: TANGERINE, times: [1.2, 2.4, 2.75, 3.75, 4.25, 4.6] },
  { crayon: SKY, times: [4.8, 5.8, 6.15, 7.0, 7.45, 8.0] },
];

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

/** A settle with one soft overshoot (spring-like), 0 → 1. */
export function settle(u: number) {
  'worklet';
  if (u <= 0) return 0;
  if (u >= 1) return 1;
  return 1 - Math.exp(-6 * u) * Math.cos(u * 8);
}

/**
 * Where the pick is at t: [phase, amount, wobble]. Phase 0 = in its slot, 1 = pulling (amount =
 * pull 0..1), 2 = flying to the paper (amount 0..1), 3 = scribbling (amount 0..1), 4 = flying home
 * (amount 0..1), 5 = settling into the slot (amount 1 → 0 pull).
 */
export function pickPhase(p: Pick, t: number): number[] {
  'worklet';
  const T = p.times;
  if (t < T[0] || t >= T[5]) return [0, 0, 0];
  if (t < T[1]) {
    // Friction: quick at first, then it drags; free after 60 % of the window, where it wobbles.
    const u = seg(t, T[0], T[0] + (T[1] - T[0]) * 0.6);
    const free = seg(t, T[0] + (T[1] - T[0]) * 0.6, T[1]);
    const pull = easeOut(u) * 0.97 + 0.03 * Math.sin(u * 12) * (1 - u);
    return [1, pull, Math.sin(free * Math.PI * 3) * (1 - free) * (u >= 1 ? 1 : 0)];
  }
  if (t < T[2]) return [2, easeInOut(seg(t, T[1], T[2])), 0];
  if (t < T[3]) return [3, seg(t, T[2], T[3]), 0];
  if (t < T[4]) return [4, easeInOut(seg(t, T[3], T[4])), 0];
  return [5, 1 - settle(seg(t, T[4], T[5])), 0];
}

/** Swatches on the paper: [old colour, old alpha, new colour, new progress 0..1]. */
export function loopSwatch(t: number): number[] {
  'worklet';
  const a = seg(t, 2.75, 3.75);
  const b = seg(t, 6.15, 7.0);
  if (t < 2.75) return [SKY, 1, SKY, 1];
  if (t < 3.75) return [SKY, 1 - a, TANGERINE, a];
  if (t < 6.15) return [TANGERINE, 1, TANGERINE, 1];
  if (t < 7.0) return [TANGERINE, 1 - b, SKY, b];
  return [SKY, 1, SKY, 1];
}

/** The chosen colour's name: [from, to, roll 0..1]. */
export function loopColour(t: number): number[] {
  'worklet';
  if (t < 3.75) return [TANGERINE, SKY, 1];
  if (t < 7.0) return [SKY, TANGERINE, easeInOut(seg(t, 3.75, 4.1))];
  return [TANGERINE, SKY, easeInOut(seg(t, 7.0, 7.35))];
}
