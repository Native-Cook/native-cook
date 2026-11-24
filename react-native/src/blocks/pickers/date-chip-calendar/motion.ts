// Every number the calendar uses. Swift and Kotlin are ported from this file.

/** Seconds. One autoPlay loop; rest at both ends (0–0.55 and 8.22–10, 23 %). */
export const LOOP = 10;

/** autoPlay: 2 s after the last touch it eases to rest and restarts at t = 0. */
export const RESUME_AFTER = 2;

/** The loop's events, in seconds. */
export const T = {
  ghostIn: 0.55,
  open: 1.0,
  pick: 2.2, // finger lifts on the first day
  dragStart: 2.55,
  dragSpan: 1.1,
  commit: 3.75, // the chip's text rolls to the dates
  rollSpan: 0.42,
  done: 4.6,
  fold: 4.65,
  clear: 7.8,
  rest: 8.22,
} as const;

export const OPEN_SPRING = { response: 0.5, ratio: 0.82 };
export const FOLD_SPRING = { response: 0.42, ratio: 0.92 };
export const CAP_SPRING = { response: 0.32, ratio: 0.6 };

/** Cells rise on one 0–1 clock: a cell starts at diagonal / 11 × CELL_SPREAD and takes CELL_SPAN. */
export const REVEAL_DELAY = 0.12;
export const REVEAL_IN = 0.75;
export const REVEAL_OUT = 0.3;
export const CELL_SPREAD = 0.55;
export const CELL_SPAN = 0.45;
export const CELL_RISE = 10;

export const DRAG_DAY_MS = 100;
export const PAGE_THRESHOLD = 0.28; // of a page
export const PAGE_VELOCITY = 500; // pt/s
export const PAGE_RUBBER = 0.3;

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
