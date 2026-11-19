// One loop drives the film, and one number drives the box: `open`, 0 (shut) .. 1 (lid up). The film is
// open = filmOpen(t); a finger moves the same number (flick the lid up, or tap the box). The camera (`shot`: 0 the
// open box, 1 the ring, 2 the macro on the stone), the metal, the stone, the carat and the booking are film curves of
// t that a finger can take over. Every value below is a pure function of t, so t = LOOP gives the same frame as t = 0.
//
// The film is the product tour: every control on the screen is used once, in the order a buyer would.
//  0.00 – 0.70  poster: the shut navy box on the paper, the camera drifting
//  0.70 – 0.88  anticipation: the lid presses down a hair (thumb on the front)
//  0.88 – 1.12  the hinge snaps: the lid flies up to 104 deg (6 % past), a crisp haptic; glints burst at 0.98
//  1.12 – 1.60  it settles on its spring
//  1.15 – 2.35  the camera comes round to the front: the ring shot
//  2.35 / 3.05 / 3.75  the metal picker: yellow gold, rose gold, back to platinum (0.45 s blends)
//  3.95 – 5.05  macro push on the stone (the box turns a little so the glints travel, 4.2 – 8.2)
//  4.75 / 5.45 / 6.15  the stone picker: sapphire, morganite, back to diamond
//  6.70 / 6.95  the carat slider steps to 1.75 and 2.00 ct (the stone grows, the price rises)
//  7.75 / 8.00  and back to 1.75 and 1.50 ct
//  8.00 – 9.20  the camera pulls back to the open box
//  8.70         "Book a private viewing" is pressed: it reads "Viewing requested" until 10.2
//  9.40 – 10.25 the lid closes; it lands with a tiny rebound (10.25 – 10.47, soft haptic)
// 10.47 – 11.5  poster; last frame = first frame

import { GLINT_LIGHTS, SHOTS } from "./ringScene";
import type { Shot, Vec3 } from "./ringScene";

export const LOOP = 11.5;

export const PHASE = {
  press: 0.7,
  snap: 0.88,
  snapEnd: 1.12,
  settleEnd: 1.6,
  burst: 0.98,
  toRing: 1.15,
  toRingEnd: 2.35,
  toMacro: 3.95,
  toMacroEnd: 5.05,
  turn: 4.2,
  turnEnd: 8.2,
  back: 8.0,
  backEnd: 9.2,
  book: 8.7,
  booked: 10.2,
  close: 9.4,
  closeEnd: 10.25,
  landEnd: 10.47,
} as const;

/** The pickers' steps (s): each moves the selection one along, three steps bring it home. */
export const METAL_STEPS = [2.35, 3.05, 3.75];
export const STONE_STEPS = [4.75, 5.45, 6.15];
/** The carat slider's steps (s) and where each lands, in slider steps (0.25 ct) from home. */
export const CARAT_STEPS = [6.7, 6.95, 7.75, 8.0];
export const CARAT_AT = [1, 2, 1, 0];
/** Lid overshoot at the snap (share of LID_OPEN). */
export const OVERSHOOT = 0.06;
export const BLEND_S = 0.45;
export const CARAT_S = 0.28;
export const IDLE_MS = 2000;
export const RETURN_MS = 2000;
/** How long the booking button reads "Viewing requested" after a finger presses it (ms). */
export const BOOKED_MS = 1500;

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

export function easeOutQuart(x: number): number {
  "worklet";
  return 1 - Math.pow(1 - x, 4);
}

export function easeInOutSine(x: number): number {
  "worklet";
  return -(Math.cos(Math.PI * x) - 1) / 2;
}

/** Seeded hash 0..1. */
export function hash(i: number): number {
  "worklet";
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

// ---------------------------------------------------------------- the film

export function filmOpen(t: number): number {
  "worklet";
  if (t < PHASE.press) return 0;
  if (t < PHASE.snap) return -0.012 * easeOutCubic(seg(t, PHASE.press, PHASE.snap));
  if (t < PHASE.snapEnd) return lerp(-0.012, 1 + OVERSHOOT, easeOutQuart(seg(t, PHASE.snap, PHASE.snapEnd)));
  if (t < PHASE.settleEnd) {
    const u = seg(t, PHASE.snapEnd, PHASE.settleEnd);
    return 1 + OVERSHOOT * Math.exp(-6 * u) * Math.cos(3 * Math.PI * u);
  }
  if (t < PHASE.close) return 1;
  if (t < PHASE.closeEnd) return 1 - easeInOutCubic(seg(t, PHASE.close, PHASE.closeEnd));
  if (t < PHASE.landEnd) return 0.016 * Math.sin(Math.PI * seg(t, PHASE.closeEnd, PHASE.landEnd));
  return 0;
}

/** The camera's shot: 0 the open box, 1 the ring, 2 the macro on the stone. */
export function filmShot(t: number): number {
  "worklet";
  if (t < PHASE.toMacro) return easeInOutSine(seg(t, PHASE.toRing, PHASE.toRingEnd));
  if (t < PHASE.back) return 1 + easeInOutSine(seg(t, PHASE.toMacro, PHASE.toMacroEnd));
  return 2 * (1 - easeInOutCubic(seg(t, PHASE.back, PHASE.backEnd)));
}

/** The box's turn in the macro (rad): left, right, home, with no speed at either end. */
export function filmYaw(t: number): number {
  "worklet";
  const u = seg(t, PHASE.turn, PHASE.turnEnd);
  const env = (1 - Math.cos(2 * Math.PI * u)) / 2;
  return 0.26 * Math.sin(2 * Math.PI * u) * env;
}

/** The glints' burst as the lid snaps, 0..1. */
export function filmBurst(t: number): number {
  "worklet";
  return easeOutCubic(seg(t, PHASE.burst, PHASE.burst + 0.2)) * Math.pow(1 - seg(t, PHASE.burst + 0.2, PHASE.burst + 1.5), 1.5);
}

/** A picker's film state from its home: out = [from offset, to offset, blend 0..1]. Three steps of one bring it home
 * (offsets are taken modulo the three choices by the caller). */
export function filmSteps(t: number, steps: readonly number[], out: number[]): void {
  "worklet";
  let k = -1;
  for (let i = 0; i < steps.length; i++) {
    if (t >= steps[i]) k = i;
  }
  if (k < 0) {
    out[0] = 0;
    out[1] = 0;
    out[2] = 0;
    return;
  }
  out[0] = k;
  out[1] = k + 1;
  out[2] = easeInOutSine(seg(t, steps[k], steps[k] + BLEND_S));
}

/** Which way the film's carat steps from home (up, unless home is already near the top). */
export function caratDir(home: number): number {
  "worklet";
  return home <= 1.5 ? 1 : -1;
}

/** The slider's value in the film (what the native slider shows: it steps at each beat). */
export function filmCaratStep(t: number, home: number): number {
  "worklet";
  let at = 0;
  for (let i = 0; i < CARAT_STEPS.length; i++) {
    if (t >= CARAT_STEPS[i]) at = CARAT_AT[i];
  }
  return home + 0.25 * at * caratDir(home);
}

/** The stone's size in the film (ct): it follows the slider's steps with a short ease out. */
export function filmCarat(t: number, home: number): number {
  "worklet";
  let prev = 0;
  let at = 0;
  let k = -1;
  for (let i = 0; i < CARAT_STEPS.length; i++) {
    if (t >= CARAT_STEPS[i]) {
      prev = at;
      at = CARAT_AT[i];
      k = i;
    }
  }
  if (k < 0) return home;
  const e = easeOutCubic(seg(t, CARAT_STEPS[k], CARAT_STEPS[k] + CARAT_S));
  return home + 0.25 * caratDir(home) * lerp(prev, at, e);
}

/** The booking button's confirmation in the film. */
export function filmBooked(t: number): boolean {
  "worklet";
  return t >= PHASE.book && t < PHASE.booked;
}

/** How much the stone can be seen (and sparkle) for a lid opening: the lid hides it until it is well up. */
export function sparkleOf(o: number): number {
  "worklet";
  return easeInOutSine(seg(o, 0.35, 0.85));
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

/** Camera for a lid opening, the shot (0 open box, 1 ring, 2 macro) and the loop time: out = [eye xyz, target xyz]. */
export function cameraAt(o: number, shot: number, t: number, out: number[]): void {
  "worklet";
  blendShot(SHOTS.poster, SHOTS.open, easeInOutSine(seg(o, 0, 1)), out);
  blendShot(asShot(out), SHOTS.ring, clamp01(shot), out);
  blendShot(asShot(out), SHOTS.macro, clamp01(shot - 1), out);
  const d = Math.sin((2 * Math.PI * t) / LOOP);
  const az = Math.atan2(out[0] - out[3], out[2] - out[5]) + 0.06 * d;
  const r = Math.hypot(out[0] - out[3], out[2] - out[5]);
  out[0] = out[3] + Math.sin(az) * r;
  out[2] = out[5] + Math.cos(az) * r;
  out[1] += 0.002 * d;
}

// ---------------------------------------------------------------- projection and glints

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
  if (z < 0.005) return false;
  out[0] = w / 2 + ((px * b[3] + pz * b[4]) / z) * b[8];
  out[1] = h / 2 - ((px * b[5] + py * b[6] + pz * b[7]) / z) * b[8];
  return true;
}

/** Facet i of the stone's crown: out = [nx, ny, nz, px, py, pz] in stone space (centre at the origin, y up); every
 * fifth facet is part of the table (facing up). */
export function facet(i: number, radius: number, crown: number, out: number[]): void {
  "worklet";
  const table = i % 5 === 0;
  const th = table ? 0.04 * hash(i + 3) : 0.38 + 0.32 * hash(i);
  const ph = (2 * Math.PI * i) / 40 + 0.35 * hash(i + 7);
  const st = Math.sin(th);
  out[0] = st * Math.cos(ph);
  out[1] = Math.cos(th);
  out[2] = st * Math.sin(ph);
  const rr = table ? radius * 0.45 * hash(i + 11) : radius * (0.62 + 0.3 * hash(i + 13));
  out[3] = Math.cos(ph) * rr;
  out[4] = table ? crown : crown * (1 - (rr / radius - 0.55) * 1.2);
  out[5] = Math.sin(ph) * rr;
}

/** A facet's glint strength for a view direction v (unit, towards the eye) and a normal n (unit): the reflected ray
 * against the studio's lights, sharp. */
export function glint(n: number[], v: number[]): number {
  "worklet";
  const d = n[0] * v[0] + n[1] * v[1] + n[2] * v[2];
  if (d <= 0) return 0;
  const rx = 2 * d * n[0] - v[0];
  const ry = 2 * d * n[1] - v[1];
  const rz = 2 * d * n[2] - v[2];
  let s = 0;
  for (let k = 0; k < GLINT_LIGHTS.length; k++) {
    const L = GLINT_LIGHTS[k];
    const c = rx * L[0] + ry * L[1] + rz * L[2];
    if (c > 0.9) s += L[3] * Math.pow(c, 90);
  }
  return s;
}
