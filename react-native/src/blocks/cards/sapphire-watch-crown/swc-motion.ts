// Sapphire Watch Crown — the loop. Helpers first: a worklet captures only what is declared above it.
//
// hours is the time the hands show (10:08 at rest, a turn of the crown winds them on); flip 0 … 1
// turns the watch over about its 12–6 axis to show the movement through its sapphire back; metal
// blends the case from one metal to the next (from, to, k).

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
/** the time at rest: 10:08 */
export const REST_HOURS = 10 + 8 / 60;
/** how far a full drag winds the hands, hours per pt of drag */
export const HOURS_PER_PT = 0.06;

/* ---------------------------------------------------------------- the loop (10 s)
 0.5  a finger takes the crown and winds it: the hands whirl twelve hours round to the same time (0.7 … 2.4)
 3.0  a swipe turns the watch over: the movement runs behind the sapphire (3.1 … 3.9)
 6.3  over again to the dial (6.4 … 7.2)
 7.6  the yellow gold swatch: the case recolours (7.8 … 8.4)    9.0 rose gold again (9.2 … 9.8)   10 = 0 */
export const T = {
  wind: [0.7, 2.4],
  flipOver: [3.1, 3.9],
  flipBack: [6.4, 7.2],
  gold: [7.8, 8.4],
  rose: [9.2, 9.8],
} as const;

export type Frame = {
  hours: number;
  crown: number; // the crown's own turn, rad
  flip: number;
  metalFrom: number;
  metalTo: number;
  metalK: number;
  finger: number;
  fingerAt: number; // 0 the crown, 1 the watch (a swipe), 2 a swatch
  fingerU: number; // along the gesture, 0 … 1
  swatch: number;
  press: number;
};

export function frameAt(t: number): Frame {
  'worklet';
  const w = easeInOut(seg(t, T.wind[0], T.wind[1]));
  const hours = REST_HOURS + 12 * w;
  const crown = w * 12 * Math.PI;
  const flip = easeInOut(seg(t, T.flipOver[0], T.flipOver[1])) * (1 - easeInOut(seg(t, T.flipBack[0], T.flipBack[1])));
  let metalFrom = 1;
  let metalTo = 1;
  let metalK = 0;
  if (t >= T.gold[0] && t < T.rose[0]) {
    metalFrom = 1;
    metalTo = 2;
    metalK = easeInOut(seg(t, T.gold[0], T.gold[1]));
  } else if (t >= T.rose[0]) {
    metalFrom = 2;
    metalTo = 1;
    metalK = easeInOut(seg(t, T.rose[0], T.rose[1]));
  }
  let finger = 0;
  let fingerAt = 0;
  let fingerU = 0;
  let press = 0;
  let swatch = metalK > 0.5 ? metalTo : metalFrom;
  const f0 = easeOut(seg(t, T.wind[0] - 0.2, T.wind[0] - 0.04)) * (1 - easeOut(seg(t, T.wind[1], T.wind[1] + 0.16)));
  if (f0 > finger) {
    finger = f0;
    fingerU = w;
    press = f0;
  }
  const f1 = easeOut(seg(t, T.flipOver[0] - 0.22, T.flipOver[0] - 0.05)) * (1 - easeOut(seg(t, T.flipOver[0] + 0.2, T.flipOver[0] + 0.36)));
  if (f1 > finger) {
    finger = f1;
    fingerAt = 1;
    fingerU = seg(t, T.flipOver[0] - 0.1, T.flipOver[0] + 0.25);
    press = f1;
  }
  const f3 = easeOut(seg(t, T.flipBack[0] - 0.22, T.flipBack[0] - 0.05)) * (1 - easeOut(seg(t, T.flipBack[0] + 0.2, T.flipBack[0] + 0.36)));
  if (f3 > finger) {
    finger = f3;
    fingerAt = 1;
    fingerU = seg(t, T.flipBack[0] - 0.1, T.flipBack[0] + 0.25);
    press = f3;
  }
  const g = easeOut(seg(t, T.gold[0] - 0.2, T.gold[0] - 0.04)) * (1 - easeOut(seg(t, T.gold[0] + 0.1, T.gold[0] + 0.26)));
  if (g > finger) {
    finger = g;
    fingerAt = 2;
    swatch = 2;
    press = g;
  }
  const r = easeOut(seg(t, T.rose[0] - 0.2, T.rose[0] - 0.04)) * (1 - easeOut(seg(t, T.rose[0] + 0.1, T.rose[0] + 0.26)));
  if (r > finger) {
    finger = r;
    fingerAt = 3;
    press = r;
  }
  return { hours, crown, flip, metalFrom, metalTo, metalK, finger, fingerAt, fingerU, swatch, press };
}
