// One loop drives the film, and one number drives the case: `open`, 0 (closed) .. 1 (lid open) .. 2 (pillow up). The
// film is open = filmOpen(t); a finger drags the same number (the lid up, then the pillow), so the film is a demo of
// the gesture. The watch's turn and the camera's push to the dial are film curves a finger can take over. Every value
// below is a pure function of t (and open), so t = LOOP gives the same frame as t = 0.
//
//  0.0 –  1.2  poster: the closed walnut case, the camera drifting
//  1.2 –  1.36 the clasp lets go: the lid pops 2 deg (haptic)
//  1.36 – 3.7  the lid swings open on a damped hinge: quick to start, a long slow settle (soft haptic at rest)
//  3.7 –  4.7  the pillow rises 18 mm and tips the dial 15 deg to the lens (haptic at the top)
//  4.7 –  8.6  the watch turns to catch the light (left 24 deg, right 16 deg, home); the camera pushes in to the dial
//              (5.4 – 6.8) and pulls back (7.9 – 8.9); the dial picker tries every dial: green at 5.5, silver at 6.9,
//              back to blue at 8.2 (0.5 s blends)
//  8.5         "Reserve in a boutique" is pressed: it reads "Reserved" until 10.3
//  8.6 –  9.4  the pillow sinks back
//  9.4 – 11.3  the lid closes (soft haptic as it lands)
// 11.3 – 12.0  poster; last frame = first frame
// The second hand ticks once a second all along; its jump at the loop's seam happens with the lid shut.

import { SHOTS } from "./watchScene";
import type { Shot, Vec3 } from "./watchScene";

export const LOOP = 12;

export const PHASE = {
  latch: 1.2,
  swing: 1.36,
  swingEnd: 3.7,
  liftEnd: 4.7,
  turnStart: 4.7,
  turnEnd: 8.6,
  macroIn: 5.4,
  macroInEnd: 6.8,
  macroOut: 7.9,
  macroOutEnd: 8.9,
  sinkEnd: 9.4,
  closeEnd: 11.3,
  reserve: 8.5,
  reserved: 10.3,
} as const;

/** The dial picker's steps (s): each moves it one along, three steps bring it home. */
export const DIAL_STEPS = [5.5, 6.9, 8.2];
/** How long the reserve button reads "Reserved" after a finger presses it (ms). */
export const RESERVED_MS = 1500;

/** The dial blend's duration (s), film and finger. */
export const DIAL_BLEND = 0.5;
export const IDLE_MS = 2000;
export const RETURN_MS = 2000;

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

/** A damped hinge released from rest: no speed at the start, quickest early, then a long slow settle (critically
 * damped step response, normalised to land on 1). */
export function hinge(u: number): number {
  "worklet";
  const x = clamp01(u) * 6;
  const end = 1 - 7 * Math.exp(-6);
  return (1 - (1 + x) * Math.exp(-x)) / end;
}

// ---------------------------------------------------------------- the film

export function filmOpen(t: number): number {
  "worklet";
  if (t < PHASE.latch) return 0;
  if (t < PHASE.swing) return 0.022 * easeOutCubic(seg(t, PHASE.latch, PHASE.swing));
  if (t < PHASE.swingEnd) return 0.022 + 0.978 * hinge(seg(t, PHASE.swing, PHASE.swingEnd));
  if (t < PHASE.liftEnd) return 1 + easeInOutCubic(seg(t, PHASE.swingEnd, PHASE.liftEnd));
  if (t < PHASE.turnEnd) return 2;
  if (t < PHASE.sinkEnd) return 2 - easeInOutCubic(seg(t, PHASE.turnEnd, PHASE.sinkEnd));
  if (t < PHASE.closeEnd) return 1 - easeInOutCubic(seg(t, PHASE.sinkEnd, PHASE.closeEnd));
  return 0;
}

/** The watch's turn to catch the light (rad): left, right, home, with no speed at either end. */
export function filmYaw(t: number): number {
  "worklet";
  const u = seg(t, PHASE.turnStart, PHASE.turnEnd);
  const env = (1 - Math.cos(2 * Math.PI * u)) / 2;
  const deg = -22 * Math.sin(2 * Math.PI * u) * env * 1.6 + 4 * Math.sin(Math.PI * u) * env;
  return (deg * Math.PI) / 180;
}

/** How far the camera has pushed in to the dial, 0..1. */
export function filmMacro(t: number): number {
  "worklet";
  return easeInOutSine(seg(t, PHASE.macroIn, PHASE.macroInEnd)) * (1 - easeInOutSine(seg(t, PHASE.macroOut, PHASE.macroOutEnd)));
}

/** The film's dial state from `home`: out = [from offset, to offset, blend 0..1] (offsets modulo the three dials). */
export function filmDial(t: number, out: number[]): void {
  "worklet";
  let k = -1;
  for (let i = 0; i < DIAL_STEPS.length; i++) {
    if (t >= DIAL_STEPS[i]) k = i;
  }
  if (k < 0) {
    out[0] = 0;
    out[1] = 0;
    out[2] = 0;
    return;
  }
  out[0] = k;
  out[1] = k + 1;
  out[2] = easeInOutSine(seg(t, DIAL_STEPS[k], DIAL_STEPS[k] + DIAL_BLEND));
}

/** The reserve button's confirmation in the film. */
export function filmReserved(t: number): boolean {
  "worklet";
  return t >= PHASE.reserve && t < PHASE.reserved;
}

// ---------------------------------------------------------------- open -> the case's parts

/** Lid opening 0..1 (times LID_OPEN). */
export function lidOf(o: number): number {
  "worklet";
  return clamp01(o);
}

/** The pillow's rise 0..1 and tip 0..1. */
export function liftOf(o: number): number {
  "worklet";
  return easeInOutSine(seg(o, 1, 2));
}

export function tipOf(o: number): number {
  "worklet";
  return easeInOutSine(seg(o, 1.3, 2));
}

/** The specs' entrance 0..1 (they count up as the pillow rises). */
export function specsOf(o: number): number {
  "worklet";
  return easeOutCubic(seg(o, 0.6, 1.9));
}

// ---------------------------------------------------------------- camera

export function blendShot(a: Shot, b: Shot, k: number, out: number[]): void {
  "worklet";
  const az0 = Math.atan2(a.eye[0], a.eye[2]);
  let az1 = Math.atan2(b.eye[0], b.eye[2]);
  if (az1 - az0 > Math.PI) az1 -= 2 * Math.PI;
  if (az0 - az1 > Math.PI) az1 += 2 * Math.PI;
  const r0 = Math.hypot(a.eye[0], a.eye[2]);
  const r1 = Math.hypot(b.eye[0], b.eye[2]);
  const az = lerp(az0, az1, k);
  const r = lerp(r0, r1, k);
  out[0] = Math.sin(az) * r;
  out[1] = lerp(a.eye[1], b.eye[1], k);
  out[2] = Math.cos(az) * r;
  out[3] = lerp(a.target[0], b.target[0], k);
  out[4] = lerp(a.target[1], b.target[1], k);
  out[5] = lerp(a.target[2], b.target[2], k);
}

function asShot(c: number[]): Shot {
  "worklet";
  return { eye: [c[0], c[1], c[2]], target: [c[3], c[4], c[5]] };
}

/** Camera for a case state, the push to the dial (0..1) and the loop time: out = [eye xyz, target xyz]. */
export function cameraAt(o: number, macro: number, t: number, out: number[]): void {
  "worklet";
  blendShot(SHOTS.poster, SHOTS.open, easeInOutSine(seg(o, 0.15, 1.3)), out);
  blendShot(asShot(out), SHOTS.turn, easeInOutSine(seg(o, 1.1, 2)), out);
  blendShot(asShot(out), SHOTS.macro, clamp01(macro), out);
  const d = Math.sin((2 * Math.PI * t) / LOOP);
  const az = Math.atan2(out[0] - out[3], out[2] - out[5]) + 0.05 * d;
  const r = Math.hypot(out[0] - out[3], out[2] - out[5]);
  out[0] = out[3] + Math.sin(az) * r;
  out[2] = out[5] + Math.cos(az) * r;
  out[1] += 0.004 * d;
}

// ---------------------------------------------------------------- projection (the stage camera's own maths)

export function cameraBasis(c: number[], width: number, hfovDeg: number, out: number[]): void {
  "worklet";
  let fx = c[3] - c[0];
  let fy = c[4] - c[1];
  let fz = c[5] - c[2];
  const fl = Math.hypot(fx, fy, fz) || 1;
  fx /= fl;
  fy /= fl;
  fz /= fl;
  let rx = -fz;
  let rz = fx;
  const rl = Math.hypot(rx, rz) || 1;
  rx /= rl;
  rz /= rl;
  out[0] = fx;
  out[1] = fy;
  out[2] = fz;
  out[3] = rx;
  out[4] = rz;
  out[5] = -rz * fy;
  out[6] = rz * fx - rx * fz;
  out[7] = rx * fy;
  out[8] = width / 2 / Math.tan((hfovDeg * Math.PI) / 360);
}

export function project(c: number[], b: number[], w: number, h: number, p: Vec3, out: number[]): boolean {
  "worklet";
  const px = p[0] - c[0];
  const py = p[1] - c[1];
  const pz = p[2] - c[2];
  const z = px * b[0] + py * b[1] + pz * b[2];
  out[2] = z;
  if (z < 0.01) return false;
  out[0] = w / 2 + ((px * b[3] + pz * b[4]) / z) * b[8];
  out[1] = h / 2 - ((px * b[5] + py * b[6] + pz * b[7]) / z) * b[8];
  return true;
}
