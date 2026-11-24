// One loop time drives everything: the typing, the 2D paper, the fold (the glTF animation's time), the plane's pose
// on screen and the thread. Every value below is a pure function of t (and of the screen's geometry), so t = LOOP
// gives the same frame as t = 0, and a finger only moves the clock (to the send) and sets the throw's size.
//
//  0.0 – 0.6   rest: three bubbles, an empty composer
//  0.6 – 2.3   the message types itself into the field (no keyboard); the send button lights up
//  2.3 – 2.6   a finger arrives on the send button, 2.6 it presses
//  2.7 – 3.1   the message leaves the field as a sheet of paper: it rises and grows to the sheet's size, the words fade
//  3.06 – 3.16 hand-off: the flat 3D sheet fades in over the 2D paper (same rectangle), the 2D paper fades out
//  3.12 – 4.52 the fold (1.4 s): corners in, edges in, halves up, wings down, while the sheet lies back and its nose
//              swings up to the launch heading; a small dip before the throw
//  4.52 – 5.72 the flight (1.2 s): up, over the thread and down into the new message's place, banking, shrinking
//  5.42 – 5.72 the thread moves up to make room
//  5.68        the sent bubble pops in on a spring as the plane fades into it (haptic); 6.0 "Delivered"
//  7.4 – 8.2   reset: the bubble and its receipt fade, the thread settles back
//  8.2 – 9.0   rest; last frame = first frame

import { FOLD_S, MESSAGE } from "./planeScene";

export const LOOP = 9;

export const PHASE = {
  type: 0.6,
  typeEnd: 2.3,
  tap: 2.6,
  lift: 2.7,
  liftEnd: 3.1,
  fold: 3.12,
  foldEnd: 3.12 + FOLD_S,
  flight: 3.12 + FOLD_S,
  flightEnd: 3.12 + FOLD_S + 1.2,
  pop: 3.12 + FOLD_S + 1.16,
  delivered: 6.0,
  reset: 7.4,
  resetEnd: 8.2,
} as const;

/** Reduced motion shows this still: the message delivered. */
export const REDUCED_T = 6.8;
/** The hand-off's cross-fade (s): the 3D sheet comes in over this long, ending as the 2D paper starts to go. */
export const HANDOFF = 0.06;
/** The fold's pose when it ends: how far the sheet lies back (deg, top edge away), its roll, how far it rose (pt)
 * and the dip before the throw (pt). */
export const TILT_FOLD = 38;
export const ROLL_FOLD = -16;
export const RISE = 26;
export const DIP = 9;
/** The flight: the tilt it ends with, the bank at the top of the turn (deg) and the scale it shrinks to. */
export const TILT_FLY = 20;
export const BANK = 34;
export const SCALE_END = 0.2;
/** The bubble's pop: x(τ) = 1 - e^(-Zτ) (cos Wτ + Z/W sin Wτ), from POP_FROM to 1. */
export const POP_Z = 13;
export const POP_W = 24;
export const POP_FROM = 0.55;
/** A throw scales the flight's arc by 1 .. THROW_MAX (the same 1.2 s, so a harder throw flies faster). */
export const THROW_MAX = 1.35;

/** The screen's geometry the motion needs, as one array (it crosses to the Filament thread):
 * [width, height, sheet centre x, sheet centre y, slot centre x, slot centre y, the sheet's width in pt, throw]. */
export const G_W = 0;
export const G_H = 1;
export const G_SX = 2;
export const G_SY = 3;
export const G_TX = 4;
export const G_TY = 5;
export const G_PT = 6;
export const G_THROW = 7;

// ---------------------------------------------------------------- helpers (declared before their callers)

export function clamp01(x: number): number {
  "worklet";
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number): number {
  "worklet";
  return clamp01((t - a) / (b - a));
}

export function lerp(a: number, b: number, k: number): number {
  "worklet";
  return a + (b - a) * k;
}

export function easeInOutCubic(x: number): number {
  "worklet";
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOutCubic(x: number): number {
  "worklet";
  return 1 - Math.pow(1 - x, 3);
}

export function easeInOutSine(x: number): number {
  "worklet";
  return -(Math.cos(Math.PI * x) - 1) / 2;
}

export function smooth(x: number): number {
  "worklet";
  return x * x * (3 - 2 * x);
}

/** The pop spring's step response at τ seconds (0 → 1, one small overshoot). */
export function popStep(tau: number): number {
  "worklet";
  if (tau <= 0) return 0;
  return 1 - Math.exp(-POP_Z * tau) * (Math.cos(POP_W * tau) + (POP_Z / POP_W) * Math.sin(POP_W * tau));
}

/** One Catmull-Rom coordinate between p1 and p2 (u 0..1); d = true gives the derivative. */
export function catmull(p0: number, p1: number, p2: number, p3: number, u: number, d: boolean): number {
  "worklet";
  const a = -p0 + 3 * p1 - 3 * p2 + p3;
  const b = 2 * p0 - 5 * p1 + 4 * p2 - p3;
  const c = p2 - p0;
  if (d) return 0.5 * (3 * a * u * u + 2 * b * u + c);
  return 0.5 * (a * u * u * u + b * u * u + c * u + 2 * p1);
}

// ---------------------------------------------------------------- the composer

/** How many characters of MESSAGE the field shows. */
export function typedCount(t: number): number {
  "worklet";
  if (t >= PHASE.lift) return 0;
  return Math.floor(seg(t, PHASE.type, PHASE.typeEnd) * MESSAGE.length + 1e-6);
}

/** The film's finger on the send button: out = [opacity, pressed 0..1]. */
export function ghostAt(t: number, out: number[]): void {
  "worklet";
  out[0] = seg(t, PHASE.tap - 0.3, PHASE.tap - 0.06) * (1 - seg(t, PHASE.tap + 0.12, PHASE.tap + 0.36));
  out[1] = seg(t, PHASE.tap - 0.06, PHASE.tap + 0.02) * (1 - seg(t, PHASE.tap + 0.06, PHASE.tap + 0.18));
}

// ---------------------------------------------------------------- the 2D paper and the hand-off

/** The paper's way from the field's rectangle (0) to the sheet's (1). */
export function liftAt(t: number): number {
  "worklet";
  return easeInOutCubic(seg(t, PHASE.lift, PHASE.liftEnd));
}

/** The 2D paper's opacity: in as the message leaves the field, out once the 3D sheet covers it. */
export function paperAlpha(t: number): number {
  "worklet";
  return seg(t, PHASE.lift, PHASE.lift + 0.06) * (1 - seg(t, PHASE.liftEnd + 0.02, PHASE.liftEnd + 0.02 + HANDOFF));
}

/** The words on the paper: they fade as it grows. */
export function wordsAlpha(t: number): number {
  "worklet";
  return 1 - seg(t, PHASE.lift + 0.1, PHASE.liftEnd - 0.08);
}

/** The 3D layer's opacity: in over the flat 2D paper, out into the bubble's pop. */
export function stageAlpha(t: number): number {
  "worklet";
  return seg(t, PHASE.liftEnd - 0.04, PHASE.liftEnd - 0.04 + HANDOFF) * (1 - seg(t, PHASE.pop - 0.02, PHASE.pop + 0.06));
}

/** Seconds into the fold animation (0 .. FOLD_S). */
export function foldTime(t: number): number {
  "worklet";
  const x = t - PHASE.fold;
  return x < 0 ? 0 : x > FOLD_S ? FOLD_S : x;
}

// ---------------------------------------------------------------- the flight

/** The flight path at s (0..1), screen points: out = [x, y, dx/ds, dy/ds]. A Catmull-Rom curve from the sheet up,
 * over the thread and down into the slot; a throw (g[G_THROW]) makes the arc bigger. */
export function pathAt(s: number, g: number[], out: number[]): void {
  "worklet";
  const w = g[G_W];
  const h = g[G_H];
  const a = g[G_THROW];
  const x0 = g[G_SX];
  const y0 = g[G_SY] - RISE;
  const xs = [x0, x0 - 0.16 * w * a, x0 + 0.02 * w, x0 + 0.3 * w, g[G_TX]];
  const ys = [y0, y0 - 0.22 * h * a, y0 - 0.4 * h * a, y0 - 0.27 * h * a, g[G_TY]];
  const f = clamp01(s) * 4;
  let i = Math.floor(f);
  if (i > 3) i = 3;
  const u = f - i;
  const xa = i === 0 ? 2 * xs[0] - xs[1] : xs[i - 1];
  const ya = i === 0 ? 2 * ys[0] - ys[1] : ys[i - 1];
  const xd = i === 3 ? 2 * xs[4] - xs[3] : xs[i + 2];
  const yd = i === 3 ? 2 * ys[4] - ys[3] : ys[i + 2];
  out[0] = catmull(xa, xs[i], xs[i + 1], xd, u, false);
  out[1] = catmull(ya, ys[i], ys[i + 1], yd, u, false);
  out[2] = catmull(xa, xs[i], xs[i + 1], xd, u, true);
  out[3] = catmull(ya, ys[i], ys[i + 1], yd, u, true);
}

/** The flight's progress along its path, 0..1. */
export function flightAt(t: number): number {
  "worklet";
  return smooth(seg(t, PHASE.flight, PHASE.flightEnd));
}

/** The plane on screen: out = [x, y (pt), heading (deg, 0 = nose right, 90 = up), tilt (deg, the top edge away),
 * roll (deg, + = right wing down), scale]. The flat sheet is [sheet centre, 0, 0, 0, 1]. */
export function poseAt(t: number, g: number[], out: number[]): void {
  "worklet";
  const p = [0, 0, 0, 0];
  if (t < PHASE.flight) {
    pathAt(0, g, p);
    const launch = (Math.atan2(-p[3], p[2]) * 180) / Math.PI;
    const k = easeInOutCubic(seg(t, PHASE.fold, PHASE.foldEnd));
    out[0] = g[G_SX];
    out[1] = g[G_SY] - RISE * k + DIP * Math.sin(Math.PI * seg(t, PHASE.foldEnd - 0.24, PHASE.foldEnd));
    out[2] = launch * k;
    out[3] = TILT_FOLD * easeInOutSine(seg(t, PHASE.fold, PHASE.fold + 0.9));
    out[4] = ROLL_FOLD * k;
    out[5] = 1;
    return;
  }
  const s = flightAt(t);
  pathAt(s, g, p);
  out[0] = p[0];
  out[1] = p[1];
  out[2] = (Math.atan2(-p[3], p[2]) * 180) / Math.PI;
  out[3] = lerp(TILT_FOLD, TILT_FLY, s);
  out[4] = ROLL_FOLD * (1 - clamp01(s * 3)) + BANK * Math.sin(Math.PI * s);
  out[5] = 1 - (1 - SCALE_END) * Math.pow(s, 2.2);
}

/** The pose's rotation as an axis and an angle: out = [x, y, z, radians]. World: x right, y up, z to the camera.
 * q = Rx(-tilt) · Rz(heading) · Rx(90° + roll): the sheet's top faces the camera, its nose turns in the screen's
 * plane, it rolls about its nose, then the whole lies back. */
export function poseRotation(headingDeg: number, tiltDeg: number, rollDeg: number, out: number[]): void {
  "worklet";
  const r = Math.PI / 360;
  const a = -tiltDeg * r;
  const b = headingDeg * r;
  const c = (90 + rollDeg) * r;
  // qz(b) * qx(c)
  const sb = Math.sin(b);
  const cb = Math.cos(b);
  const sc = Math.sin(c);
  const cc = Math.cos(c);
  const x1 = cb * sc;
  const y1 = sb * sc;
  const z1 = sb * cc;
  const w1 = cb * cc;
  // qx(a) * that
  const sa = Math.sin(a);
  const ca = Math.cos(a);
  const x = ca * x1 + sa * w1;
  const y = ca * y1 - sa * z1;
  const z = ca * z1 + sa * y1;
  const w = ca * w1 - sa * x1;
  const n = Math.hypot(x, y, z);
  if (n < 1e-6) {
    out[0] = 0;
    out[1] = 0;
    out[2] = 1;
    out[3] = 0;
    return;
  }
  out[0] = x / n;
  out[1] = y / n;
  out[2] = z / n;
  out[3] = 2 * Math.atan2(n, w);
}

/** The plane's soft shadow on the chat: out = [x, y (pt), radius x, radius y (pt), opacity]. It slides away and
 * thins as the plane climbs. */
export function shadowAt(t: number, g: number[], out: number[]): void {
  "worklet";
  const p = [0, 0, 0, 0, 0, 0];
  poseAt(t, g, p);
  const s = t < PHASE.flight ? 0 : flightAt(t);
  const f = seg(t, PHASE.fold, PHASE.foldEnd);
  const lift = f * 0.35 + s * 0.65;
  const size = g[G_PT] * p[5];
  // flat against the screen the sheet throws none: it grows out from under the paper as it lifts and lies back
  const rise = easeInOutSine(seg(t, PHASE.fold + 0.1, PHASE.fold + 0.7));
  out[0] = p[0] + 28 * lift * p[5];
  out[1] = p[1] + (22 * rise + 40 * lift) * p[5];
  out[2] = size * lerp(0.3, 0.42, rise) * lerp(1, 0.8, f);
  out[3] = size * lerp(0.2, 0.3, rise) * lerp(1, 0.7, f);
  out[4] = stageAlpha(t) * rise * lerp(0.7, 0.3, lift);
}

// ---------------------------------------------------------------- the thread

/** The thread's way up to make room for the new message (0..1). */
export function shiftAt(t: number): number {
  "worklet";
  return easeInOutCubic(seg(t, PHASE.flightEnd - 0.3, PHASE.flightEnd)) - easeInOutCubic(seg(t, PHASE.reset, PHASE.resetEnd));
}

/** The sent bubble: out = [opacity, scale]. */
export function bubbleAt(t: number, out: number[]): void {
  "worklet";
  out[0] = seg(t, PHASE.pop, PHASE.pop + 0.08) * (1 - seg(t, PHASE.reset, PHASE.reset + 0.4));
  out[1] = POP_FROM + (1 - POP_FROM) * popStep(t - PHASE.pop);
}

/** The "Delivered" receipt's opacity. */
export function deliveredAt(t: number): number {
  "worklet";
  return seg(t, PHASE.delivered, PHASE.delivered + 0.25) * (1 - seg(t, PHASE.reset, PHASE.reset + 0.3));
}

/** The send button: lit while there is something to send. */
export function sendLitAt(t: number): number {
  "worklet";
  return seg(t, PHASE.type, PHASE.type + 0.18) * (1 - seg(t, PHASE.lift, PHASE.lift + 0.18));
}
