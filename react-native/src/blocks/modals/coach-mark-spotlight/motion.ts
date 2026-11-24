// Every number the tour uses. Swift and Kotlin are ported from this file.

/** Seconds. One autoPlay loop; rest at both ends on step 1 (0–1.05 and 8.35–9, 19 %). */
export const LOOP = 9;

/** autoPlay: 2 s after the last touch it eases to rest and restarts at t = 0. */
export const RESUME_AFTER = 2;

/** The loop's events, in seconds. */
export const T = {
  ghostIn: 0.5,
  next1: 1.05, // finger lifts on Next
  morph1: 1.15, // step 1 → 2: the round button's cutout becomes the wide card's
  tapCard: 3.25, // finger lifts on the highlighted card itself
  morph2: 3.35, // step 2 → 3: the card's cutout becomes the tab item's
  dragStart: 5.0, // finger swipes the tooltip right…
  dragEnd: 5.45, // …and lets go: back one step
  morph3: 5.5, // step 3 → 2
  skip: 6.8, // finger lifts on Skip
  fadeOut: 6.85,
  fadeOutSpan: 0.4,
  reset: 7.3, // invisible: progress jumps back to step 1
  intro: 7.45, // the tour comes back: dim fades in, the cutout irises down onto the button
  introSpan: 0.85,
  tipIn: 7.95,
  tipInSpan: 0.4,
  rest: 8.35,
} as const;

/** A step change: the cutout travels on this curve; the tooltip follows 70 ms later. */
export const MORPH = 0.75;
export const TIP_LAG = 0.07;
/** While the cutout travels it narrows a little about its centre, then opens onto the target. */
export const TRAVEL_SQUEEZE = 0.3;
/** The finger: the same morph on springs. */
export const MORPH_SPRING = { response: 0.55, ratio: 0.82 };
export const TIP_SPRING = { response: 0.6, ratio: 0.78 };
export const DRAG_SPRING = { response: 0.4, ratio: 0.7 };
/** The opening iris: from a circle IRIS_SCALE times the first target down onto it, landing 2 % tight. */
export const IRIS_SPRING = { response: 0.6, ratio: 0.8 };
export const IRIS_SCALE = 3.2;

/** Cutout padding around each target, pt, and the ring drawn on its edge. */
export const PAD = 8;
export const SOFT_EDGE = 5; // blur sigma of the cutout's edge
export const RING_INSET = 3;
/** A ring breathes out of the cutout while a step holds: period, travel, starting opacity. */
export const PULSE_PERIOD = 1.4;
export const PULSE_GROW = 14;
export const PULSE_ALPHA = 0.5;
/** autoPlay holds in which the ring breathes: [start, end] in s (the first wraps through the loop's end). */
export const HOLDS: [number, number][] = [
  [8.35, 10.0],
  [1.95, 3.2],
  [4.15, 4.95],
  [6.3, 6.78],
];

/** Tooltip, pt. */
export const TIP_MAX_W = 340;
export const TIP_H = 176;
export const TIP_GAP = 14; // between the cutout and the arrow's tip
export const ARROW_W = 20;
export const ARROW_H = 9;
export const TIP_FADE_SCALE = 0.88;
/** The tooltip is opaque except in the last third of its fade (no see-through card). */
export const TIP_OPACITY_GAIN = 3;
/** Swipe the tooltip: past this (or this fast) it goes back (right) or on (left). */
export const SWIPE_STEP = 64;
export const SWIPE_VELOCITY = 500;
export const SWIPE_RUBBER = 0.45;
export const GHOST_DRAG = 78; // pt the ghost finger drags the tooltip

export const DIM = 'rgba(16,21,28,0.66)';

export type Box = { x: number; y: number; w: number; h: number; r: number };

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

/** The closed-form step response of a spring, so autoPlay is a pure function of t. */
export function springAt(t: number, s: { response: number; ratio: number }) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / s.response;
  const wd = w * Math.sqrt(1 - s.ratio * s.ratio);
  return 1 - Math.exp(-s.ratio * w * t) * (Math.cos(wd * t) + ((s.ratio * w) / wd) * Math.sin(wd * t));
}

/** Piecewise-linear value of a per-step list at a fractional step p (clamped to the ends). */
export function along(values: number[], p: number) {
  'worklet';
  const last = values.length - 1;
  if (p <= 0) return values[0];
  if (p >= last) return values[last];
  const i = Math.floor(p);
  return mix(p - i, values[i], values[i + 1]);
}

/**
 * The cutout at progress p (0 = first target, 1 = second, …): position, size and corner radius
 * interpolated between neighbouring targets, narrowed mid-travel, then opened by `iris` (1 = a circle
 * IRIS_SCALE times the first target's size around it, 0 = closed onto it).
 */
export function cutoutAt(targets: Box[], p: number, iris: number): Box {
  'worklet';
  const last = targets.length - 1;
  const q = p < 0 ? 0 : p > last ? last : p;
  const i = last === 0 ? 0 : q >= last ? last - 1 : Math.floor(q);
  const u = last === 0 ? 0 : q - i;
  const a = targets[i];
  const b = targets[Math.min(i + 1, last)];
  const squeeze = 1 - TRAVEL_SQUEEZE * Math.sin(Math.PI * u);
  const cx = mix(u, a.x + a.w / 2, b.x + b.w / 2);
  const cy = mix(u, a.y + a.h / 2, b.y + b.h / 2);
  let w = mix(u, a.w, b.w) * squeeze;
  let h = mix(u, a.h, b.h) * squeeze;
  let r = Math.min(mix(u, a.r, b.r) * squeeze, Math.min(w, h) / 2);
  let x = cx - w / 2;
  let y = cy - h / 2;
  if (iris > 0) {
    const f = targets[0];
    const fx = f.x + f.w / 2;
    const fy = f.y + f.h / 2;
    const big = Math.max(f.w, f.h) * IRIS_SCALE;
    w = mix(iris, w, big);
    h = mix(iris, h, big);
    r = mix(iris, r, big / 2);
    x = mix(iris, x, fx - big / 2);
    y = mix(iris, y, fy - big / 2);
  }
  return { x, y, w, h, r };
}
