/** Seconds since the banner was tapped. The sheet rises first, so the loader starts a little in. */
export const SHEET_RISE = 0.45;
export const BADGE_IN = SHEET_RISE;
export const BADGE_POP = 0.7;
export const RING_IN = 0.6;
export const RING_DRAW = 1;
export const CAPTION_IN = 0.9;
export const CAPTION_OUT = 3.9;
export const DISC_IN = 1.3;
export const DISC_STAGGER = 0.05;
export const DISC_FLY = 0.9;
export const SUCK_AT = 4;
export const SUCK_FOR = 0.6;
export const SUCK_STAGGER = 0.015;
export const RING_OUT = 4;
export const BADGE_UP = 4.3;
export const BADGE_UP_FOR = 0.7;
export const RESULT_IN = 4.9;
export const RESULT_FOR = 0.7;
/** The clock stops here; everything after it is a still result. */
export const END = 5.7;
/** Seconds one disc takes for a full turn at speed 1. */
export const PERIOD = 3;

export function lerp(from: number, to: number, t: number) {
  'worklet';
  return from + (to - from) * t;
}

export function ramp(value: number, from: number, to: number) {
  'worklet';
  return Math.min(1, Math.max(0, (value - from) / (to - from)));
}

export function easeOut(t: number) {
  'worklet';
  return 1 - (1 - t) * (1 - t) * (1 - t);
}

export function easeIn(t: number) {
  'worklet';
  return t * t * t;
}

/** 0 → 1 with a small overshoot, for a pop. */
export function pop(t: number) {
  'worklet';
  const c = 1.70158;
  return 1 + (c + 1) * (t - 1) * (t - 1) * (t - 1) + c * (t - 1) * (t - 1);
}
