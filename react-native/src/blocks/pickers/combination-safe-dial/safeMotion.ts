// One loop time drives the film: the dial, the unlock and the camera are pure functions of t, so t = LOOP gives the
// same frame as t = 0. The unlock is one timeline `u` (0 = locked, UNLOCK = door open): handle, then bolts, then door.
// The film plays it forward and back; a finger's right code plays the same timeline.
//
//  0.00 – 0.70  rest: locked, dial at 0
//  0.70 – 1.70  the dial turns clockwise to the first number
//  2.05 – 3.05  counter-clockwise to the second
//  3.40 – 4.30  clockwise to the third
//  4.50 – 6.25  u 0 -> 1.75: handle swings (0 – 0.5), bolts draw in (0.22 – 0.78), door opens (0.65 – 1.75)
//  6.25 – 7.30  held open, warm light through the gap
//  7.30 – 8.75  u 1.75 -> 0: door shuts, bolts go out, handle returns
//  8.70 – 9.40  the dial returns to 0, the entries clear
//  9.40 – 10.0  rest; last frame = first frame

import {
  AZ_OPEN,
  AZ_REST,
  BOLT_FIRST_DEG,
  BOLT_STEP_DEG,
  DIST,
  EL_OPEN,
  EL_REST,
  FRAME_H,
  FRAME_W,
  TARGET,
  ZOOM_OPEN,
} from "./safeScene";

export const LOOP = 10;
export const DIAL_N = 40;
export const DEG_PER_NUMBER = 360 / DIAL_N;

export const PHASE = {
  a: 0.7,
  aEnd: 1.7,
  b: 2.05,
  bEnd: 3.05,
  c: 3.4,
  cEnd: 4.3,
  open: 4.5,
  shut: 7.3,
  shutEnd: 8.75,
  dialBack: 8.7,
  dialBackEnd: 9.4,
} as const;

/** The unlock timeline, in seconds of `u`. */
export const UNLOCK = 1.75;
export const HANDLE_U = [0, 0.5] as const;
export const BOLT_U = [0.22, 0.64] as const;
export const BOLT_STAGGER = 0.02;
export const DOOR_U = [0.65, 1.75] as const;
/** How far the handle swings (deg, clockwise) and the door opens (deg). */
export const HANDLE_DEG = 82;
export const DOOR_DEG = 36;
/** How strongly the dial catches on each number (0 = smooth, 1 = stops at every number). */
export const DETENT = 0.62;

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

export function smooth(x: number): number {
  "worklet";
  return x * x * (3 - 2 * x);
}

export function easeInOutCubic(x: number): number {
  "worklet";
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOutCubic(x: number): number {
  "worklet";
  return 1 - Math.pow(1 - x, 3);
}

/** A number on the dial, 0..39, for an unwrapped dial position. */
export function dialNumber(n: number): number {
  "worklet";
  const r = Math.round(n) % DIAL_N;
  return r < 0 ? r + DIAL_N : r;
}

/** The dial catches on each number: between two numbers it hurries, at a number it lingers. */
export function detented(v: number): number {
  "worklet";
  const f = Math.floor(v);
  const x = v - f;
  return f + lerp(x, smooth(smooth(x)), DETENT);
}

// ---------------------------------------------------------------- the film

/** The film's dial (unwrapped numbers: + is counter-clockwise), for the combination c0, c1, c2. */
export function filmDial(t: number, c0: number, c1: number, c2: number): number {
  "worklet";
  const s1 = c0 === 0 ? 0 : c0 - DIAL_N;
  const s2 = c1;
  const s3 = c2 === 0 ? 0 : c2 - DIAL_N;
  const v =
    s1 * easeInOutCubic(seg(t, PHASE.a, PHASE.aEnd)) +
    (s2 - s1) * easeInOutCubic(seg(t, PHASE.b, PHASE.bEnd)) +
    (s3 - s2) * easeInOutCubic(seg(t, PHASE.c, PHASE.cEnd)) -
    s3 * easeInOutCubic(seg(t, PHASE.dialBack, PHASE.dialBackEnd));
  return detented(v);
}

/** How many numbers the film has entered (0..3). */
export function filmEntered(t: number): number {
  "worklet";
  if (t >= PHASE.dialBack + 0.05) return 0;
  if (t >= PHASE.cEnd) return 3;
  if (t >= PHASE.bEnd) return 2;
  if (t >= PHASE.aEnd) return 1;
  return 0;
}

/** The film's place on the unlock timeline. */
export function filmUnlock(t: number): number {
  "worklet";
  return clamp01((t - PHASE.open) / UNLOCK) * UNLOCK - seg(t, PHASE.shut, PHASE.shutEnd) * UNLOCK;
}

/** The film's finger on the dial: out = [opacity 0..1, pressed 0..1]. */
export function dialGhost(t: number, out: number[]): void {
  "worklet";
  const a = t < 1.875 ? PHASE.a : t < 3.225 ? PHASE.b : PHASE.c;
  const b = t < 1.875 ? PHASE.aEnd : t < 3.225 ? PHASE.bEnd : PHASE.cEnd;
  out[0] = seg(t, a - 0.22, a - 0.04) * (1 - seg(t, b + 0.02, b + 0.16));
  out[1] = seg(t, a - 0.06, a + 0.04) * (1 - seg(t, b - 0.04, b + 0.06));
}

// ---------------------------------------------------------------- the unlock, from u

/** The handle's swing, 0..1. */
export function handleAt(u: number): number {
  "worklet";
  return easeInOutCubic(seg(u, HANDLE_U[0], HANDLE_U[1]));
}

/** Bolt i's travel, 0 (out) .. 1 (drawn in). */
export function boltAt(u: number, i: number): number {
  "worklet";
  const from = BOLT_U[0] + i * BOLT_STAGGER;
  return easeInOutCubic(seg(u, from, from + (BOLT_U[1] - BOLT_U[0])));
}

/** The door's opening, 0..1. */
export function doorAt(u: number): number {
  "worklet";
  return easeInOutCubic(seg(u, DOOR_U[0], DOOR_U[1]));
}

/** Bolt i's direction, clockwise from straight up: out = [x, y]. */
export function boltDir(i: number, out: number[]): void {
  "worklet";
  const a = ((BOLT_FIRST_DEG + BOLT_STEP_DEG * i) * Math.PI) / 180;
  out[0] = Math.sin(a);
  out[1] = Math.cos(a);
}

// ---------------------------------------------------------------- camera and projection

/** tan(half the vertical field of view) that keeps FRAME_W x FRAME_H around the target in a stage of this aspect. */
export function tanHalfFovY(aspect: number): number {
  "worklet";
  return Math.max(FRAME_H / 2, FRAME_W / 2 / aspect) / DIST;
}

/** The camera for the door's opening (0..1): out = [eye xyz]. It looks at TARGET. */
export function cameraAt(open: number, out: number[]): void {
  "worklet";
  const k = smooth(open);
  const az = (lerp(AZ_REST, AZ_OPEN, k) * Math.PI) / 180;
  const el = (lerp(EL_REST, EL_OPEN, k) * Math.PI) / 180;
  const d = DIST * (1 - ZOOM_OPEN * k);
  out[0] = TARGET[0] + d * Math.cos(el) * Math.sin(az);
  out[1] = TARGET[1] + d * Math.sin(el);
  out[2] = TARGET[2] + d * Math.cos(el) * Math.cos(az);
}

/** Projects a world point to the stage (pt) with the stage's own camera: out = [x, y]. */
export function project(x: number, y: number, z: number, open: number, width: number, height: number, out: number[]): void {
  "worklet";
  const eye = [0, 0, 0];
  cameraAt(open, eye);
  // the camera's basis: f forward, r right, u up
  let fx = TARGET[0] - eye[0];
  let fy = TARGET[1] - eye[1];
  let fz = TARGET[2] - eye[2];
  const fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
  fx /= fl;
  fy /= fl;
  fz /= fl;
  let rx = -fz;
  let rz = fx;
  const rl = Math.sqrt(rx * rx + rz * rz);
  rx /= rl;
  rz /= rl;
  const ux = -rz * fy;
  const uy = rz * fx - rx * fz;
  const uz = rx * fy;
  const px = x - eye[0];
  const py = y - eye[1];
  const pz = z - eye[2];
  const depth = px * fx + py * fy + pz * fz;
  const aspect = width / height;
  const tanY = tanHalfFovY(aspect);
  const ndcX = (px * rx + pz * rz) / (depth * tanY * aspect);
  const ndcY = (px * ux + py * uy + pz * uz) / (depth * tanY);
  out[0] = (ndcX * 0.5 + 0.5) * width;
  out[1] = (0.5 - ndcY * 0.5) * height;
}

/** The change of a finger's angle round a centre (radians, -pi..pi). */
export function angleDelta(a: number, b: number): number {
  "worklet";
  let d = b - a;
  while (d > Math.PI) d -= 2 * Math.PI;
  while (d < -Math.PI) d += 2 * Math.PI;
  return d;
}
