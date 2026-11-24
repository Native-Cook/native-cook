// All the motion of the Dual Range Histogram as pure functions of loop time. Positions are `u`, 0 … 1 across the
// price axis. Swift and Kotlin port these numbers one to one.

export const LOOP = 8;

export const MODE_AUTO = 0;
export const MODE_MANUAL = 1;
export const MODE_RETURN = 2;

export const IDLE_S = 2; // after the last touch
export const RETURN_S = 0.7;

export const THUMB = 30;
export const TRACK_H = 4;
export const TRACK_AREA = 44; // height of the band holding the track and the thumbs
export const TAG_W = 72;
export const TAG_H = 30;
export const TAG_GAP = 6;
export const GRAB_R = 38; // pt around a thumb where a touch picks it up
export const TAP_SLOP = 8;

export const BAR_GAP = 2;
export const BAR_RADIUS = 2.5;
export const SINK = 0.58; // height of a bar outside the range
export const LIFT_PT = 7; // bars next to a held thumb rise
export const LIFT_SIGMA = 20;

export const SQUASH_X = 1.18;
export const SQUASH_Y = 0.86;
export const CONTACT_SHARE = 0.3; // how far two thumbs in contact push each other, per unit of overlap
export const FLING_S = 0.12; // the release lands where the velocity would carry it in this time

// response 0.42 s, damping ratio 0.68
export const RELEASE_SPRING = { mass: 1, stiffness: 223.8, damping: 20.35 };
export const PRESS_SPRING = { mass: 1, stiffness: 520, damping: 30 };

const FILM_RESPONSE = 0.42;
const FILM_RATIO = 0.62;

export function clamp(v: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max(v, a), b);
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function mix(a: number, b: number, k: number) {
  'worklet';
  return a + (b - a) * k;
}

export function smooth(k: number) {
  'worklet';
  return k * k * (3 - 2 * k);
}

export function easeInOutCubic(k: number) {
  'worklet';
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
}

/** Up over a → b, held, down over c → d. */
export function win(t: number, a: number, b: number, c: number, d: number) {
  'worklet';
  return smooth(seg(t, a, b)) * (1 - smooth(seg(t, c, d)));
}

/** A damped spring released at rest from `from`, s seconds ago. */
export function springAt(s: number, from: number, to: number) {
  'worklet';
  if (s <= 0) return from;
  const w = (2 * Math.PI) / FILM_RESPONSE;
  const zw = FILM_RATIO * w;
  const wd = w * Math.sqrt(1 - FILM_RATIO * FILM_RATIO);
  const a = from - to;
  return to + Math.exp(-zw * s) * (a * Math.cos(wd * s) + ((zw * a) / wd) * Math.sin(wd * s));
}

/** How far a finger's overshoot `over` (in u) moves a thumb past its limit. */
export function rubber(over: number) {
  'worklet';
  return (over * 0.55) / (1 + over / 0.09);
}

export function constrain(u: number, lo: number, hi: number) {
  'worklet';
  if (u < lo) return lo - rubber(lo - u);
  if (u > hi) return hi + rubber(u - hi);
  return u;
}

/** Two thumbs pressed together push each other apart instead of crossing. */
export function shownLow(low: number, high: number, gap: number) {
  'worklet';
  return low - CONTACT_SHARE * Math.max(0, low + gap - high);
}

export function shownHigh(low: number, high: number, gap: number) {
  'worklet';
  return high + CONTACT_SHARE * Math.max(0, low + gap - high);
}

export type Film = {
  restLow: number;
  restHigh: number;
  lowHold: number;
  lowOver: number;
  highLimit: number;
  highFinger: number;
  highOver: number;
};

export function makeFilm(restLow: number, restHigh: number, gap: number): Film {
  const lowHold = Math.max(0.02, restLow - 0.1);
  const highLimit = lowHold + gap;
  return {
    restLow,
    restHigh,
    lowHold,
    lowOver: lowHold - 0.012,
    highLimit,
    highFinger: highLimit - 0.055,
    highOver: Math.min(1, restHigh + 0.02),
  };
}

export function filmLow(t: number, f: Film) {
  'worklet';
  if (t < 0.85) return f.restLow;
  if (t < 1.75) return mix(f.restLow, f.lowOver, easeInOutCubic(seg(t, 0.85, 1.75)));
  if (t < 5.9) return springAt(t - 1.75, f.lowOver, f.lowHold);
  if (t < 6.7) return mix(f.lowHold, f.restLow, easeInOutCubic(seg(t, 5.9, 6.7)));
  return f.restLow;
}

export function filmHigh(t: number, f: Film) {
  'worklet';
  const held = f.highLimit - rubber(f.highLimit - f.highFinger);
  if (t < 2.3) return f.restHigh;
  if (t < 3.55) {
    const finger = mix(f.restHigh, f.highFinger, easeInOutCubic(seg(t, 2.3, 3.55)));
    return finger < f.highLimit ? f.highLimit - rubber(f.highLimit - finger) : finger;
  }
  if (t < 3.7) return held;
  if (t < 4.35) return springAt(t - 3.7, held, f.highLimit);
  if (t < 5.4) return mix(f.highLimit, f.highOver, easeInOutCubic(seg(t, 4.35, 5.4)));
  if (t < 6.85) return springAt(t - 5.4, f.highOver, f.restHigh);
  return f.restHigh;
}

export function filmPressLow(t: number) {
  'worklet';
  return win(t, 0.65, 0.8, 1.75, 1.9) + win(t, 5.75, 5.9, 6.7, 6.85);
}

export function filmPressHigh(t: number) {
  'worklet';
  return win(t, 2.15, 2.3, 3.7, 3.85) + win(t, 4.2, 4.35, 5.4, 5.55);
}

export function filmGhost(t: number) {
  'worklet';
  return (
    win(t, 0.45, 0.65, 1.8, 2.0) + win(t, 1.98, 2.15, 3.75, 3.95) + win(t, 4.02, 4.2, 5.45, 5.62) + win(t, 5.6, 5.75, 6.75, 6.95)
  );
}

/** 0: the ghost finger is on the low thumb, 1: on the high one. */
export function filmGhostSide(t: number) {
  'worklet';
  return t >= 1.99 && t < 5.61 ? 1 : 0;
}

/** Odometer position of one digit column: the lowest column turns freely, the others only as the one below wraps. */
export function digitAt(value: number, place: number, lowest: boolean) {
  'worklet';
  const v = Math.max(0, value);
  if (lowest) return (v / place) % 10;
  const below = (v % place) / place;
  return (Math.floor(v / place) % 10) + smooth(seg(below, 0.88, 1));
}

/** Listings between two positions, counting a bin by the share of it inside the range. */
export function rangeCount(a: readonly number[], b: readonly number[], morph: number, low: number, high: number) {
  'worklet';
  const n = b.length;
  let sum = 0;
  for (let i = 0; i < n; i += 1) {
    const share = clamp(Math.min(high * n, i + 1) - Math.max(low * n, i), 0, 1);
    if (share > 0) sum += mix(a[i] ?? 0, b[i] ?? 0, morph) * share;
  }
  return sum;
}
