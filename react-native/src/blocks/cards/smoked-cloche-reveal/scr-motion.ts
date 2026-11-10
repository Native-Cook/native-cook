// Smoked Cloche Reveal — the loop. Helpers first: a worklet captures only what is declared above it.
//
// Seen from above, the bowl sits under a glass cloche full of applewood smoke. lift 0 … 1 raises the
// cloche towards the eye (it grows, blurs and fades) while the smoke, no longer held by its rim,
// spills out from under it and thins away; cart 0 … 1 sends the dish into the cart; the cloche comes
// back down over it and fills with smoke again.

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
export function easeIn(x: number) {
  'worklet';
  return x * x * x;
}
/** the cloche set down: lowered on an ease (the first 72 %), then the glass rim touches the plate and
 * rocks up a hair (3 % of the lift) before it rests. 1 … 0, a multiplier of the lift it came down from */
export const LAND = { at: 0.72, hop: 0.03 } as const;
export function setDown(p: number) {
  'worklet';
  if (p <= 0) return 1;
  if (p >= 1) return 0;
  if (p < LAND.at) return 1 - easeInOut(p / LAND.at);
  return LAND.hop * Math.sin((Math.PI * (p - LAND.at)) / (1 - LAND.at));
}

export const LOOP = 10;
/** the part of the lift that resists, as if the smoke held the cloche down */
export const SEAL = 0.14;

/* ---------------------------------------------------------------- the loop (10 s)
 0.5  a finger takes the knob and draws the cloche up    1.55 lets go: it rises away, the smoke spills
 2.3  the finger runs through the smoke                  6.2 Add to Cart: the dish flies into the cart
 7.4  the dish comes back                                 8.1 the cloche comes down and fills with smoke
 10 = 0 */
export const T = {
  grab: 0.5,
  pull: [0.6, 1.55],
  away: [1.55, 2.05],
  stir: [2.3, 3.3],
  cart: [6.2, 6.9],
  back: [7.4, 8.0],
  down: [8.1, 8.9],
  refill: 8.7,
} as const;

export type Frame = {
  lift: number;
  cart: number;
  mode: number; // the smoke: 0 held, 1 spilling, 2 refilling (the ticker sends 3 on the frame a refill starts)
  finger: number;
  fingerAt: number; // 0 the knob, 1 through the smoke, 2 the cart button
  fingerU: number; // along a stroke, 0 … 1
  press: number;
};

export function frameAt(t: number): Frame {
  'worklet';
  let lift = 0.55 * easeInOut(seg(t, T.pull[0], T.pull[1]));
  if (t >= T.away[0]) lift = 0.55 + 0.45 * easeIn(seg(t, T.away[0], T.away[1]));
  lift *= setDown(seg(t, T.down[0], T.down[1]));
  const cart = easeInOut(seg(t, T.cart[0], T.cart[1])) * (1 - easeOut(seg(t, T.back[0], T.back[1])));
  const mode = t < T.pull[0] + 0.25 || t >= T.refill ? (t >= T.refill ? 2 : 0) : 1;
  let finger = 0;
  let fingerAt = 0;
  let fingerU = 0;
  let press = 0;
  const knob = easeOut(seg(t, T.grab - 0.15, T.grab)) * (1 - easeOut(seg(t, T.pull[1], T.pull[1] + 0.16)));
  if (knob > finger) {
    finger = knob;
    fingerAt = 0;
    fingerU = easeInOut(seg(t, T.pull[0], T.pull[1]));
    press = knob;
  }
  const s = easeOut(seg(t, T.stir[0] - 0.18, T.stir[0] - 0.02)) * (1 - easeOut(seg(t, T.stir[1], T.stir[1] + 0.16)));
  if (s > finger) {
    finger = s;
    fingerAt = 1;
    fingerU = seg(t, T.stir[0], T.stir[1]);
    press = s;
  }
  const c = easeOut(seg(t, T.cart[0] - 0.2, T.cart[0] - 0.04)) * (1 - easeOut(seg(t, T.cart[0] + 0.14, T.cart[0] + 0.3)));
  if (c > finger) {
    finger = c;
    fingerAt = 2;
    press = easeOut(seg(t, T.cart[0] - 0.04, T.cart[0] + 0.05)) * (1 - easeOut(seg(t, T.cart[0] + 0.1, T.cart[0] + 0.22)));
  }
  return { lift, cart, mode, finger, fingerAt, fingerU, press };
}
