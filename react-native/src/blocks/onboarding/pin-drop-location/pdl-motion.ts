// Pin Drop Location — the loop. Helpers first: a worklet captures only what is declared above it.
//
// The hero is a little night map in a disc with a pink glass pin standing on it. Pulled down, the
// disc bends into a globe (bend 0 … 1) that turns under the finger (lon, lat, rad); let go, it
// flattens again on the place the finger turned to, and the pin drops onto it from above with a
// squash, rings across the map and the city's lights coming on round it (since: s since it landed).
// Allowed, the map zooms in on the pin (zoom) and three places rise round it (places).

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
/** the squash of a landing τ s after it: wide and short, then a small rebound */
export function squashAt(tau: number) {
  'worklet';
  if (tau <= 0 || tau >= 0.5) return 0;
  const x = tau / 0.5;
  return 0.22 * Math.min(1, x / 0.08) * Math.exp(-5.5 * x) * Math.cos(12 * x) * (1 - x);
}

export const LOOP = 10;
/** how far the globe turns in the loop (rad) */
export const TURN = 1.5;

/* ---------------------------------------------------------------- the loop (10 s)
 0.4  a finger on the map pulls it down: it bends into a globe, the pin lifts off     1.1 it turns
 2.6  let go: the globe flattens on the new place     3.15 the pin falls and lands at 3.55
 5.4  Allow: the map zooms in and three places rise   8.0 back out, the map turns home   10 = 0 */
export const T = {
  bend: [0.5, 1.1],
  turn: [1.1, 2.6],
  flat: [2.6, 3.1],
  fall: [3.15, 3.55],
  allow: 5.4,
  zoomIn: [5.5, 6.3],
  places: [6.0, 6.8],
  zoomOut: [8.0, 8.8],
  home: [8.0, 9.4],
} as const;

export type Frame = {
  bend: number;
  lon: number;
  lat: number;
  hover: number; // the pin lifted off the map, 0 … 1
  fall: number; // the pin's fall: 1 = on the map, 0 = out of sight above
  since: number; // s since the pin landed (−1 none)
  lights: number; // the city's lights round the pin, 0 … 1
  zoom: number;
  places: number;
  allowed: number;
  finger: number;
  fingerAt: number; // 0 the map, 1 Allow
  fingerU: number; // 0 … 1 along the drag
  press: number;
};

export function frameAt(t: number): Frame {
  'worklet';
  const bend = easeInOut(seg(t, T.bend[0], T.bend[1])) * (1 - easeInOut(seg(t, T.flat[0], T.flat[1])));
  const turn = easeInOut(seg(t, T.turn[0], T.turn[1]));
  const home = easeInOut(seg(t, T.home[0], T.home[1]));
  const lon = TURN * turn * (1 - home);
  const lat = 0.35 * Math.sin(Math.PI * turn) * (1 - home);
  // the pin lifts off as the globe forms and is gone by the time it flattens; then it falls
  const hover = t < T.fall[0] ? easeOut(seg(t, T.bend[0], T.bend[1])) : 0;
  let fall = 1;
  if (t >= T.bend[0] && t < T.fall[0]) fall = 1 - easeIn(seg(t, T.bend[0] + 0.1, T.bend[1] + 0.2));
  else if (t >= T.fall[0] && t < T.fall[1]) fall = easeIn(seg(t, T.fall[0], T.fall[1]));
  const since = t >= T.fall[1] ? t - T.fall[1] : -1;
  const lights = 1 - easeInOut(seg(t, T.home[0] + 0.6, T.home[1]));
  const zoom = easeInOut(seg(t, T.zoomIn[0], T.zoomIn[1])) * (1 - easeInOut(seg(t, T.zoomOut[0], T.zoomOut[1])));
  const places = easeOut(seg(t, T.places[0], T.places[1])) * (1 - easeInOut(seg(t, T.zoomOut[0], T.zoomOut[0] + 0.4)));
  const allowed = t >= T.allow && t < T.zoomOut[0] + 0.3 ? 1 : 0;
  let finger = 0;
  let fingerAt = 0;
  let fingerU = 0;
  let press = 0;
  const drag = easeOut(seg(t, T.bend[0] - 0.18, T.bend[0] - 0.02)) * (1 - easeOut(seg(t, T.turn[1], T.turn[1] + 0.16)));
  if (drag > finger) {
    finger = drag;
    fingerU = t < T.turn[0] ? -1 + easeInOut(seg(t, T.bend[0], T.bend[1])) : turn;
    press = drag;
  }
  const a = easeOut(seg(t, T.allow - 0.18, T.allow - 0.04)) * (1 - easeOut(seg(t, T.allow + 0.14, T.allow + 0.3)));
  if (a > finger) {
    finger = a;
    fingerAt = 1;
    press = easeOut(seg(t, T.allow - 0.04, T.allow + 0.05)) * (1 - easeOut(seg(t, T.allow + 0.1, T.allow + 0.22)));
  }
  return { bend, lon, lat, hover, fall, since, lights, zoom, places, allowed, finger, fingerAt, fingerU, press };
}
