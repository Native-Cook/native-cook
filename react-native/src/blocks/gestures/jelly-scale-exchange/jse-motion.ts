// Jelly Scale Exchange — the pour, the melt, the coin and the autoPlay loop as pure functions of time.
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
/** A jelly wobble that starts and ends at 0. */
export function wobbleAt(u: number) {
  'worklet';
  if (u <= 0 || u >= 1) return 0;
  return Math.exp(-u * 5) * Math.sin(u * 26);
}

export const BEANS = 16;
export const POUR_RATE = 6; // beans a second while the button is held
export const POINTS_PER_BEAN = 5;
export const TILT = 0.55;

/** How far the pad sinks under the weight (units): the beans, until the coin lifts out. */
export function sinkOf(pour: number, coin: number) {
  'worklet';
  const land = pour - Math.floor(pour);
  // each bean's landing nudges the pad
  const nudge = pour > 0.5 ? 0.012 * Math.exp(-land * 9) * Math.sin(land * 30) : 0;
  return (0.03 * Math.min(pour, BEANS)) / BEANS * (1 - smooth(0.3, 0.8, coin)) + nudge * (1 - coin);
}

export const LOOP = 10;
export const T = {
  hold: 0.8,
  pour: [0.9, 0.9 + BEANS / POUR_RATE] as const,
  release: 3.65,
  exchange: 4.3,
  melt: [4.4, 5.5] as const,
  coin: [5.5, 6.3] as const,
  spin: [5.8, 6.8] as const,
  fly: [6.9, 7.6] as const,
  plus: [7.6, 8.5] as const,
} as const;

export type Frame = {
  pour: number;
  melt: number;
  coin: number;
  spin: number;
  fly: number;
  wobbleU: number; // the balance pill's jelly (wobbleAt), 1 = at rest
  plus: number; // the "+80" rising, 1 = gone
  pressPour: number;
  pressSwap: number;
  finger: number;
  fingerAt: number; // 0 the Pour button, 1 Exchange
};

export function frameAt(t: number): Frame {
  'worklet';
  // once the coin has gone the bowl is empty again: the melt and the pour return to 0 out of sight
  const gone = t >= T.fly[1] + 0.05;
  const pour = gone ? 0 : BEANS * seg(t, T.pour[0], T.pour[1]);
  const melt = gone ? 0 : easeInOut(seg(t, T.melt[0], T.melt[1]));
  const coin = gone ? 0 : easeInOut(seg(t, T.coin[0], T.coin[1]));
  const press = (a: number, b: number) => easeOut(seg(t, a, a + 0.1)) * (1 - easeOut(seg(t, b, b + 0.15)));
  const tap = (a: number) => easeOut(seg(t, a - 0.08, a)) * (1 - easeOut(seg(t, a + 0.06, a + 0.2)));
  const near = (a: number, b: number) => easeOut(seg(t, a - 0.25, a)) * (1 - easeOut(seg(t, b, b + 0.2)));
  const onPour = near(T.hold, T.release);
  const onSwap = near(T.exchange - 0.05, T.exchange + 0.15);
  return {
    pour,
    melt,
    coin,
    spin: gone ? 0 : 2 * Math.PI * easeInOut(seg(t, T.spin[0], T.spin[1])),
    fly: gone ? 0 : easeInOut(seg(t, T.fly[0], T.fly[1])),
    wobbleU: t < T.fly[1] ? 1 : seg(t, T.fly[1], T.fly[1] + 0.7),
    plus: t < T.plus[0] ? 1 : seg(t, T.plus[0], T.plus[1]),
    pressPour: press(T.hold, T.release),
    pressSwap: tap(T.exchange),
    finger: clamp01(onPour + onSwap),
    fingerAt: onPour > 0.001 ? 0 : 1,
  };
}
