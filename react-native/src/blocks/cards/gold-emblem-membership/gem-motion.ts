// Gold Emblem Membership — the loop. Helpers first: a worklet captures only what is declared above it.
//
// A change of tier melts the card and casts it again: cast 0 … 1 runs a heat front across from the
// side it was swiped from, the plate goes molten (ripples, drips off its lower edge, a white-hot glow),
// changes metal while liquid, then cools back the other way and is stamped with its new medal.

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

export const LOOP = 10;
/** how long a cast takes, s */
export const CAST = 1.7;

/* ---------------------------------------------------------------- the loop (10 s)
 0.6  a swipe left: the gold melts and is cast in platinum (0.8 … 2.5)
 3.6  a swipe left: platinum to black (3.8 … 5.5)
 7.0  a long fling right: black straight back to gold (7.3 … 9.0)          10 = 0 */
export const T = {
  casts: [
    { at: 0.8, from: 0, to: 1, side: 1 },
    { at: 3.8, from: 1, to: 2, side: 1 },
    { at: 7.3, from: 2, to: 0, side: -1 },
  ],
} as const;

export type Frame = {
  from: number;
  to: number;
  cast: number;
  side: number;
  drag: number; // the card's give under the finger, pt
  finger: number;
  fingerX: number; // the finger's offset from the card's centre, pt
  press: number;
};

export function frameAt(t: number): Frame {
  'worklet';
  let from = 0;
  let to = 0;
  let cast = 0;
  let side = 1;
  let drag = 0;
  let finger = 0;
  let fingerX = 0;
  for (let i = 0; i < T.casts.length; i++) {
    const c = T.casts[i];
    if (t >= c.at - 0.5) {
      from = c.from;
      to = c.to;
      side = c.side;
      cast = seg(t, c.at, c.at + CAST);
      // the swipe before it: the card gives a little, then the finger lifts as the heat starts
      const sw = seg(t, c.at - 0.45, c.at + 0.05);
      const f = easeOut(seg(t, c.at - 0.5, c.at - 0.4)) * (1 - easeOut(seg(t, c.at + 0.02, c.at + 0.16)));
      if (f > 0) {
        finger = f;
        fingerX = side * (90 - 200 * easeInOut(sw));
      }
      drag = -side * 26 * Math.sin(Math.PI * easeInOut(sw));
    }
  }
  if (cast >= 1) from = to;
  return { from, to, cast: cast >= 1 ? 0 : cast, side, drag, finger, fingerX, press: finger };
}
