/** The block's clock: the disc's turn and the captions as functions of time. Ported from IridescentCdFlipMotion.swift. */

export const LOOP = 8;
/** The two flips of the loop: to the data side, then on round to the printed side. */
const FLIPS = [1.2, 4.2];
/** The camera's distance for the disc's perspective, in points. */
export const DEPTH = 1000;

export function clamp01(x: number) {
  'worklet';
  return Math.min(Math.max(x, 0), 1);
}

// The flip spring: ω 6.5, ζ 0.6 (about 9 % overshoot, a wobble on landing).
const SPRING_A = 0.6 * 6.5;
const SPRING_B = 6.5 * Math.sqrt(1 - 0.6 * 0.6);

/** A spring from x0 (velocity v0) to target: [value, velocity] after t. */
export function spring(x0: number, v0: number, target: number, t: number): [number, number] {
  'worklet';
  const a = SPRING_A;
  const b = SPRING_B;
  const c1 = x0 - target;
  const c2 = (v0 + a * c1) / b;
  const e = Math.exp(-a * t);
  const value = target + e * (c1 * Math.cos(b * t) + c2 * Math.sin(b * t));
  const velocity = e * ((-a * c1 + b * c2) * Math.cos(b * t) + (-a * c2 - b * c1) * Math.sin(b * t));
  return [value, velocity];
}

/** The idle sway: ±4° once a loop, so the light keeps sliding across the disc. */
function sway(t: number) {
  'worklet';
  return 4 * Math.sin((2 * Math.PI * t) / LOOP);
}

/** The disc's turn in degrees at loop time t (0 printed side, 180 data side, 360 printed again). */
export function loopAngle(t: number) {
  'worklet';
  // Each flip springs from the previous rest to the next; it has settled long before the next one.
  let angle = 0;
  for (let i = 0; i < FLIPS.length; i++) if (t >= FLIPS[i]) angle += 180 + spring(-180, 0, 0, t - FLIPS[i])[0];
  return angle + sway(t);
}

/** The side the loop is heading for at t (true: printed), and since when (the start of the last flip). */
export function loopCaption(t: number): [boolean, number] {
  'worklet';
  if (t >= FLIPS[1]) return [true, t - FLIPS[1]];
  if (t >= FLIPS[0]) return [false, t - FLIPS[0]];
  return [true, 10];
}

/** Which side faces the viewer for a turn (degrees): true for the printed side. */
export function printedSide(angle: number) {
  'worklet';
  return Math.cos((angle * Math.PI) / 180) >= 0;
}

const GLYPHS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&*/<>';

/** A small integer hash (PCG), the same in every stack: 0 to 1. */
export function hash(x: number, salt: number) {
  'worklet';
  let h = (Math.imul(x, 747796405) + Math.imul(salt, 2891336453 | 0) + 1) | 0;
  h = Math.imul((h >>> ((h >>> 28) + 4)) ^ h, 277803737);
  h = (h >>> 22) ^ h;
  return (h >>> 0) / 4294967295;
}

/**
 * text typed in over 0.45 s after a change: unresolved letters flicker through random glyphs
 * (a new one every 1/30 s), resolving left to right.
 */
export function glitch(text: string, e: number, salt: number) {
  'worklet';
  if (e >= 0.45) return text;
  const frame = Math.floor(Math.max(0, e) * 30);
  let out = '';
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    const resolve = (0.45 * i) / Math.max(1, text.length);
    if (e >= resolve || ch === ' ') out += ch;
    else out += GLYPHS[Math.floor(hash(frame * 131 + i, salt) * GLYPHS.length) % GLYPHS.length];
  }
  return out;
}

/**
 * The 3×3 matrix (row-major) that draws the face's square (0, 0, 2r, 2r) turned by angleDeg about
 * the vertical line through its centre, centred at (cx, cy) and seen from DEPTH away: the same
 * projection as Core Animation's m34 = −1/DEPTH in the Swift version.
 */
export function faceMatrix(r: number, angleDeg: number, cx: number, cy: number): number[] {
  'worklet';
  const t = (angleDeg * Math.PI) / 180;
  const p = Math.sin(t) / DEPTH;
  const h00 = Math.cos(t) + cx * p;
  const h10 = cy * p;
  return [h00, 0, cx - h00 * r, h10, 1, cy - h10 * r - r, p, 0, 1 - p * r];
}
