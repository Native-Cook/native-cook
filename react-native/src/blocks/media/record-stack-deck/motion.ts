/** The block's clock and the deck's geometry as functions, ported from RecordStackDeckMotion.swift. */

export const LOOP = 10;
/** Records in the crate; the deck wraps around, so scrolling by COUNT is the same picture. */
export const COUNT = 10;
/** Points of finger travel per record. */
export const DRAG_UNIT = 44;

export function clamp01(x: number) {
  'worklet';
  return Math.min(Math.max(x, 0), 1);
}
export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = clamp01((x - a) / (b - a));
  return t * t * (3 - 2 * t);
}
export function easeInOut(x: number) {
  'worklet';
  const t = clamp01(x);
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}
function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(x), 3);
}
function easeOutQuart(x: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(x), 4);
}
/** Ease-out with a small overshoot (back, s = 1.4). */
function easeOutBack(x: number) {
  'worklet';
  const t = clamp01(x);
  const s = 1.4;
  return 1 + (s + 1) * Math.pow(t - 1, 3) + s * Math.pow(t - 1, 2);
}

/** A critically damped spring (ω 12) from x0 with velocity v0 towards target: value after t. */
export function settleAt(x0: number, v0: number, target: number, t: number) {
  'worklet';
  const w = 12;
  const d = x0 - target;
  return target + (d + (v0 + w * d) * t) * Math.exp(-w * t);
}

/** How far a record is out: 0 in the deck, 1 lifted flat, 2 its vinyl half out of the sleeve. */
export const OPEN_LENGTH = 1.4;
export function opening(e: number) {
  'worklet';
  return e < 0.8 ? easeOutBack(e / 0.8) : 1 + easeInOut((e - 0.8) / 0.6);
}
export function closing(e: number) {
  'worklet';
  return e < 0.6 ? 2 - easeInOut(e / 0.6) : 1 - easeInOut((e - 0.6) / 0.8);
}

/** Deck position (records scrolled past the focus) at loop time t. */
export function loopScroll(t: number) {
  'worklet';
  if (t < 0.4) return 0;
  if (t < 1.1) return easeOutBack((t - 0.4) / 0.7);
  if (t < 8.0) return 1 + 2 * easeOut((t - 1.1) / 0.8);
  // A long flick through seven records back to the first one: the crate has turned once.
  return 3 + 7 * easeOutQuart((t - 8.0) / 1.7);
}

/** The hold ring around the focus: 0 to 1. */
export function loopRing(t: number) {
  'worklet';
  if (t < 1.9 || t >= 2.8 + 0.25) return 0;
  return t < 2.8 ? easeInOut((t - 1.9) / 0.9) : 1;
}

export function loopOpen(t: number) {
  'worklet';
  if (t < 2.8 || t >= 8.0) return 0;
  if (t < 2.8 + OPEN_LENGTH) return opening(t - 2.8);
  if (t < 6.6) return 2;
  return closing(t - 6.6);
}

/** A record's place relative to the focus, wrapped to −COUNT/2 … COUNT/2. */
export function offsetOf(index: number, scroll: number) {
  'worklet';
  const n = COUNT;
  let d = (index - scroll) % n;
  if (d < -n / 2) d += n;
  if (d >= n / 2) d -= n;
  return d;
}

/** Distance of a strip's centre from the focus, in points (down positive). */
export function yOf(d: number) {
  'worklet';
  const a = Math.abs(d);
  return (d < 0 ? -1 : 1) * (26 * a + 11 * a * a);
}

/** How open a sleeve is (the cosine of its tilt): a thin strip at the focus, more of its face further out. */
export function openness(d: number) {
  'worklet';
  const a = Math.min(Math.abs(d), 5);
  return 0.02 + 0.08 * a - 0.0065 * a * a;
}

/** The sleeve's tilt about its horizontal axis, in degrees: the far edge leans away from the focus. */
export function tiltOf(d: number) {
  'worklet';
  const t = (Math.acos(openness(d)) * 180) / Math.PI;
  return d >= 0 ? t : -t;
}

export function fadeOf(d: number) {
  'worklet';
  return 1 - smoothstep(3.0, 4.2, Math.abs(d));
}

/** The deck's focus, as a fraction of the screen height. */
export const FOCUS_Y = 0.42;

/** Sleeves fade out before they reach the header. */
export function topFade(y: number, safeTop: number) {
  'worklet';
  return smoothstep(safeTop + 40, safeTop + 120, y);
}

/** The camera's distance for the sleeves' perspective, in points. */
export const DEPTH = 1400;

/**
 * The 3×3 matrix (row-major) that draws a sleeve's square (0, 0, s, s), scaled by k about its
 * centre and tilted by tiltDeg about its horizontal centre line, centred at (cx, cy) and seen from
 * DEPTH away: the same projection as Core Animation's m34 = −1/DEPTH in the Swift version.
 */
export function sleeveMatrix(s: number, k: number, tiltDeg: number, cx: number, cy: number): number[] {
  'worklet';
  const t = (tiltDeg * Math.PI) / 180;
  const c = Math.cos(t);
  const p = Math.sin(t) / DEPTH;
  // H maps a centred point (x, y) to the screen: X = cx + x / w, Y = cy + y cos t / w, w = 1 − y sin t / DEPTH.
  const h = [1, -cx * p, cx, 0, c - cy * p, cy, 0, -p, 1];
  // A scales by k and moves the square's centre to the origin.
  const a = [k, 0, (-k * s) / 2, 0, k, (-k * s) / 2, 0, 0, 1];
  const m: number[] = [];
  for (let r = 0; r < 3; r++) {
    for (let col = 0; col < 3; col++) {
      m.push(h[r * 3] * a[col] + h[r * 3 + 1] * a[3 + col] + h[r * 3 + 2] * a[6 + col]);
    }
  }
  return m;
}

/** A sleeve point (x, y) relative to its centre, after scaling, on screen relative to the centre. */
export function project(x: number, y: number, tiltDeg: number): [number, number] {
  'worklet';
  const t = (tiltDeg * Math.PI) / 180;
  const w = 1 - (y * Math.sin(t)) / DEPTH;
  return [x / w, (y * Math.cos(t)) / w];
}

/** Where a sleeve of side s tilted by tilt degrees shows its top edge, relative to its centre. */
export function topEdge(s: number, tilt: number) {
  'worklet';
  return project(0, -s / 2, tilt)[1];
}

// The open record: the sleeve lies flat at 0.8 × its deck size and moves left, the vinyl
// (0.92 × the flat sleeve across) slides out to the right.
export const FLAT_SCALE = 0.8;
export const SLEEVE_SHIFT = -0.26;
export const VINYL_SHIFT = 0.32;
export const VINYL_RADIUS = 0.46;
