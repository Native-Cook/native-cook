// Every number the morph uses. Swift and Kotlin are ported from this file.

/** Seconds. One autoPlay loop; rest at both ends (0–0.62 and 8.05–9, 17 %). */
export const LOOP = 9;

/** autoPlay: 2 s after the last touch it eases to rest and restarts at t = 0. */
export const RESUME_AFTER = 2;

/** The loop's events, in seconds. */
export const T = {
  ghostIn: 0.62,
  open: 1.0, // finger lifts on +
  typeStart: 1.9,
  typeStep: 0.07, // per character
  priority: 3.3,
  list: 3.8,
  add: 4.4,
  fly: 4.78,
  flySpan: 0.62,
  check: 7.0,
  checkSpan: 0.3,
  remove: 7.55,
  removeSpan: 0.5,
  rest: 8.05,
} as const;

/** Two staggered springs: width leads, height follows 70 ms later. */
export const WIDTH_SPRING = { response: 0.46, ratio: 0.74 };
export const HEIGHT_SPRING = { response: 0.52, ratio: 0.78 };
export const HEIGHT_DELAY = 0.07;
export const COLLAPSE_SPRING = { response: 0.36, ratio: 0.9 };
export const PART_SPRING = { response: 0.42, ratio: 0.76 };
/** Rows start to part this long after the pill takes off. */
export const PART_DELAY = 0.16;
/** The row fades in under the pill over the last part of the flight. */
export const LAND_DELAY = 0.46;
export const LAND_SPAN = 0.16;
export const FAB_DELAY = 0.3;
export const FAB_SPAN = 0.36;
export const CONTENT_DELAY = 0.24;
export const CONTENT_IN = 0.42;
export const CONTENT_OUT = 0.14;
/** Field, chips and Add rise in on these shares of the content clock. */
export const CONTENT_STAGGER = [0, 0.14, 0.28];
export const CONTENT_ITEM = 0.72;
export const CONTENT_RISE = 10;

export const PLUS_TURN = 135; // degrees
export const FLY_ARC = 22; // pt, sideways bow of the flight
export const DRAG_CLOSE_DISTANCE = 90;
export const DRAG_CLOSE_VELOCITY = 700;
export const DRAG_UP_RUBBER = 0.15;

/** Corner radii along the morph. */
export const RADIUS = { fab: 30, card: 26, pill: 22, row: 16 };

export function springConfig(s: { response: number; ratio: number }) {
  'worklet';
  const stiffness = (2 * Math.PI / s.response) ** 2;
  return { stiffness, damping: 2 * s.ratio * Math.sqrt(stiffness), mass: 1 };
}

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function mix(u: number, a: number, b: number) {
  'worklet';
  return a + (b - a) * u;
}

export function seg(t: number, start: number, span: number) {
  'worklet';
  return clamp01((t - start) / span);
}

export function easeOutCubic(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - (-2 * x + 2) ** 3 / 2;
}

/** The closed-form step response of the springs above, so autoPlay is a pure function of t. */
export function springAt(t: number, response: number, ratio: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / response;
  const wd = w * Math.sqrt(1 - ratio * ratio);
  return 1 - Math.exp(-ratio * w * t) * (Math.cos(wd * t) + ((ratio * w) / wd) * Math.sin(wd * t));
}
