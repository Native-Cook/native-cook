// Liquid Metal Swap — the swap as a function of one number, and the autoPlay loop. Helpers first:
// worklets capture what is above them.

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
/** A soft spring's step response: 0 → 1 with one overshoot, flat at the start. */
export function springStep(x: number) {
  'worklet';
  if (x <= 0) return 0;
  return 1 - Math.exp(-6 * x) * (Math.cos(10 * x) + 0.6 * Math.sin(10 * x));
}
/** A critically damped return to 0 from d0, leaving at speed v0 (per s), after tau s. */
export function settle(d0: number, v0: number, tau: number, omega: number) {
  'worklet';
  return (d0 + (v0 + omega * d0) * tau) * Math.exp(-omega * tau);
}

/* ------------------------------------------------------------ the swap, m = 0 … 1 */
// m = 0: you hold the silver Prism coin · 0 → 0.5 it sinks into a pool of mercury that grows round
// it · 0.5 all liquid, the pool turns gold · 0.5 → 1 the gold Spark coin rises out of it. The same
// function runs backwards for the swap back, so a release mid-way simply runs the clock home.
export const HOLD = 1.5; // s of holding for a whole swap
export const R_POOL = 118;

export type Look = { kind: number; sink: number; soft: number; poolR: number; poolH: number; wobble: number; drops: number; gold: number };

export function look(m: number): Look {
  'worklet';
  const x = clamp01(m);
  const kind = x < 0.5 ? 0 : 1;
  const sink = x < 0.5 ? easeInOut(seg(x, 0.03, 0.5)) : 1 - easeInOut(seg(x, 0.5, 0.97));
  return {
    kind,
    sink,
    soft: smooth(0.1, 0.55, sink),
    poolR: R_POOL * Math.pow(sink, 0.65),
    poolH: 5 + 12 * sink,
    wobble: sink,
    drops: x > 0.36 && x < 0.64 ? seg(x, 0.36, 0.64) : 0,
    gold: smooth(0.4, 0.6, x),
  };
}

export const LOOP = 10;

/** The coin's slow sway, periodic with the loop. */
export function sway(t: number) {
  'worklet';
  return 0.3 * Math.sin((2 * Math.PI * 2 * t) / LOOP);
}

/* ------------------------------------------------------------ autoPlay */
export const T = {
  press1: 1.0, // hold: Prism → Spark
  drag0: 3.3, // a finger turns the coin
  drag1: 3.9,
  press2: 5.2, // a hold let go half way
  release2: 5.8,
  press3: 6.6, // hold: Spark → Prism
  flip: 0.35, // the two cards trade places
  swapped: 1.1, // the button reads "Swapped"
} as const;
const OMEGA = 9;

export type Frame = {
  fill: number; // how far the current hold has gone, 0 … 1
  start: number; // which end the hold started from (0 or 1)
  m: number;
  flip: number; // 0 Prism pays … 1 Spark pays
  swapped: number; // 0 … 1, the button's "Swapped" face (it takes over from a full fill at once)
  doneText: number; // 0 … 1, "Hold to swap" → "Swapped" on that face
  press: number; // the button's press
  yaw: number; // the finger's turn of the coin
  finger: number; // the scripted finger's presence
  fingerAt: number; // 0 the button, 1 the coin
  fingerX: number; // along the drag, 0 … 1
};

export function frameAt(t: number): Frame {
  'worklet';
  const done1 = T.press1 + HOLD;
  const done3 = T.press3 + HOLD;
  let fill = 0;
  let start = 0;
  if (t >= T.press1 && t < done1) fill = (t - T.press1) / HOLD;
  else if (t >= done1 && t < T.press2) fill = 0;
  if (t >= done1) start = 1;
  if (t >= T.press2 && t < T.release2) fill = (t - T.press2) / HOLD;
  else if (t >= T.release2 && t < T.press3) fill = settle((T.release2 - T.press2) / HOLD, 1 / HOLD, t - T.release2, OMEGA);
  if (t >= T.press3 && t < done3) fill = (t - T.press3) / HOLD;
  if (t >= done3) {
    start = 0;
    fill = 0;
  }
  // at the instant a hold completes, start flips and fill drops to 0: the same m, no jump
  const m = start === 0 ? fill : 1 - fill;
  const flip = easeInOut(seg(t, done1, done1 + T.flip)) * (1 - easeInOut(seg(t, done3, done3 + T.flip)));
  const swapped = (t >= done1 && t < done1 + T.swapped + 0.3 ? 1 - seg(t, done1 + T.swapped, done1 + T.swapped + 0.3) : 0)
    + (t >= done3 && t < done3 + T.swapped + 0.3 ? 1 - seg(t, done3 + T.swapped, done3 + T.swapped + 0.3) : 0);
  const doneText = clamp01(seg(t, done1, done1 + 0.2) - seg(t, done1 + T.swapped, done1 + T.swapped + 0.3))
    + clamp01(seg(t, done3, done3 + 0.2) - seg(t, done3 + T.swapped, done3 + T.swapped + 0.3));
  const pressOf = (a: number, b: number) => easeOut(seg(t, a, a + 0.1)) * (1 - easeOut(seg(t, b, b + 0.15)));
  const press = pressOf(T.press1, done1) + pressOf(T.press2, T.release2) + pressOf(T.press3, done3);
  let yaw = 0;
  if (t >= T.drag0 && t < T.drag1) yaw = 1.6 * easeOut(seg(t, T.drag0, T.drag1));
  else if (t >= T.drag1) yaw = 1.6 * (1 - springStep((t - T.drag1) / 1.1));
  const near = (a: number, b: number) => easeOut(seg(t, a - 0.22, a)) * (1 - easeOut(seg(t, b, b + 0.22)));
  const onButton = near(T.press1, done1) + near(T.press2, T.release2) + near(T.press3, done3);
  const onCoin = near(T.drag0, T.drag1);
  return {
    fill,
    start,
    m,
    flip,
    swapped: clamp01(swapped),
    doneText: clamp01(doneText),
    press: clamp01(press),
    yaw,
    finger: clamp01(onButton + onCoin),
    fingerAt: onCoin > 0.001 ? 1 : 0,
    fingerX: easeOut(seg(t, T.drag0, T.drag1)),
  };
}
