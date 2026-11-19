// One loop drives the whole film. Every film value below is a pure function of the loop time t (seconds,
// 0 <= t < LOOP), so t = LOOP gives the same frame as t = 0 and the Swift / Kotlin ports can match it frame for
// frame. The stage (Filament thread), the Skia layers and the welcome screen (UI thread) read the same t; the turn of
// the sculpture is a separate value (yaw), the film's own while it plays and the finger's once a finger takes over.
//
//  0.0 –  1.25 poster: the Arca sculpture (three nested arches and a terracotta pearl) stands still, the floor
//              track empty, "Turn to open" under it
//  1.25 – 3.45 a ghost finger drags left to right (accelerating), lets go; the sculpture turns on with momentum
//              (quadratic deceleration): 0 -> 181 deg; the track fills behind it
//  3.3  – 5.0  a second flick takes it to exactly 360 deg; the track closes and pops (5.0 – 5.4)
//  5.1  – 6.5  the fold: the camera sinks to the front; the middle and inner arches swing round to face the lens
//              with a small overshoot, staggered 150 ms; the pearl drops onto the baseline and bounces once
//  6.6  – 7.15 hand-off: the flat 2D mark fades in exactly over the 3D one, the 3D fades out; a gleam crosses it
//  7.45 – 9.1  the mark rises and shrinks to the top, "arca" sets letter by letter, the first screen fades up
//  9.1  – 11.4 hold on the welcome screen
// 11.4  – 14.0 the welcome screen fades out, the mark comes down, swaps back to 3D and unfolds; the camera rises to
//              the poster; the track and hint fade back in. Last frame = first frame.

import { HFOV_DEG, PEARL_FLOAT, PEARL_R, REST_YAW, SHOTS } from "./spinScene";

export const LOOP = 14;

export const PHASE = {
  rest: 1.25,
  /** First flick: finger down, drag (p = x^2), release, deceleration. */
  down1: 1.25,
  drag1: 1.4,
  rel1: 2.25,
  stop1: 3.45,
  down2: 3.3,
  drag2: 3.45,
  rel2: 4.2,
  /** The turn is complete (yaw = 360). */
  turnEnd: 5.0,
  foldStart: 5.15,
  foldEnd: 6.5,
  handoff: 6.6,
  riseStart: 7.45,
  riseEnd: 8.35,
  welcomeIn: 8.3,
  holdEnd: 11.4,
  unfoldStart: 12.7,
  unfoldEnd: 13.75,
} as const;

/** Finger to turn: degrees per point of horizontal drag. */
export const YAW_PER_PT = 0.45;
/** Film flicks: degrees turned while the finger drags, and how long the momentum lasts after it lets go. */
const D1 = 75;
const TD1 = PHASE.stop1 - PHASE.rel1;
const T1 = PHASE.rel1 - PHASE.drag1;
const V1 = (2 * D1) / T1;
export const A1 = D1 + (V1 * TD1) / 2;
const T2 = PHASE.rel2 - PHASE.drag2;
const TD2 = PHASE.turnEnd - PHASE.rel2;
/** The second flick is sized so the two land exactly on 360. */
export const D2 = (360 - A1) / (1 + TD2 / T2);
const V2 = (2 * D2) / T2;

/** After the last touch, wait this long, then go back to the film. */
export const IDLE_MS = 2000;
export const RETURN_MS = 1200;

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

export function easeInCubic(x: number): number {
  "worklet";
  return x * x * x;
}

/** Ease out with a small overshoot (about 4 %). */
export function easeOutBack(x: number): number {
  "worklet";
  const c1 = 1.05;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
}

export function bump(t: number, a: number, b: number): number {
  "worklet";
  const k = Math.sin(Math.PI * seg(t, a, b));
  return k * k;
}

// ---------------------------------------------------------------- the turn

/** One flick: drag of d degrees over [a, r] (accelerating, p = x^2), then momentum of velocity v decelerating
 * linearly to zero over td. */
function flick(t: number, a: number, r: number, d: number, v: number, td: number): number {
  "worklet";
  if (t <= a) return 0;
  if (t < r) {
    const x = (t - a) / (r - a);
    return d * x * x;
  }
  const u = Math.min((t - r) / td, 1);
  return d + ((v * td) / 2) * (1 - (1 - u) * (1 - u));
}

/** Turn of the sculpture in the film (degrees). 360 from turnEnd on: the same pose as 0. */
export function filmYaw(t: number): number {
  "worklet";
  if (t >= PHASE.turnEnd) return 360;
  return flick(t, PHASE.drag1, PHASE.rel1, D1, V1, TD1) + flick(t, PHASE.drag2, PHASE.rel2, D2, V2, TD2);
}

/** The ghost finger: [x offset in degrees of drag (convert with YAW_PER_PT), visibility 0..1, press 0..1]. */
export function filmFinger(t: number, out: number[]): void {
  "worklet";
  let x = 0;
  let vis = 0;
  let press = 0;
  if (t >= PHASE.down1 - 0.15 && t < PHASE.rel1 + 0.3) {
    const k = clamp01((t - PHASE.drag1) / T1);
    x = -D1 / 2 + D1 * k * k;
    vis = easeOutCubic(seg(t, PHASE.down1 - 0.15, PHASE.drag1)) * (1 - easeOutCubic(seg(t, PHASE.rel1, PHASE.rel1 + 0.25)));
    press = seg(t, PHASE.down1, PHASE.drag1) * (1 - seg(t, PHASE.rel1, PHASE.rel1 + 0.12));
  } else if (t >= PHASE.down2 - 0.15 && t < PHASE.turnEnd) {
    const k = clamp01((t - PHASE.drag2) / T2);
    x = -D2 / 2 + D2 * k * k;
    vis = easeOutCubic(seg(t, PHASE.down2 - 0.15, PHASE.drag2)) * (1 - easeOutCubic(seg(t, PHASE.rel2, PHASE.rel2 + 0.25)));
    press = seg(t, PHASE.down2, PHASE.drag2) * (1 - seg(t, PHASE.rel2, PHASE.rel2 + 0.12));
  }
  out[0] = x;
  out[1] = vis;
  out[2] = press;
}

/** How full the floor track is (0..1) for a turn of yaw degrees; empty again once the welcome screen is up. */
export function trackFill(t: number, yaw: number): number {
  "worklet";
  if (t >= 9) return 0;
  return clamp01(Math.abs(yaw) / 360);
}

/** The pop when the track closes: 0..1. */
export function trackPop(t: number): number {
  "worklet";
  return bump(t, PHASE.turnEnd - 0.05, PHASE.turnEnd + 0.4);
}

// ---------------------------------------------------------------- the fold

/** Turn of each arch relative to the sculpture (degrees), by index into ARCHES. */
export function archYaw(t: number, i: number): number {
  "worklet";
  const rest = REST_YAW[i];
  if (rest === 0) return 0;
  const lag = i === 1 ? 0 : 0.15;
  const fold = easeOutBack(seg(t, PHASE.foldStart + 0.1 + lag, PHASE.foldStart + 1.1 + lag));
  const unfold = easeInOutCubic(seg(t, PHASE.unfoldStart + lag * 0.6, PHASE.unfoldEnd - 0.15 + lag * 0.6));
  return rest * (1 - fold + unfold);
}

/** Height of the pearl's centre. */
export function pearlY(t: number): number {
  "worklet";
  const dropA = PHASE.foldStart + 0.3;
  const land = dropA + 0.45;
  if (t < dropA) return PEARL_FLOAT;
  if (t < land) {
    const x = seg(t, dropA, land);
    return lerp(PEARL_FLOAT, PEARL_R, x * x);
  }
  if (t < PHASE.unfoldStart) {
    // one small bounce
    const u = seg(t, land, land + 0.36);
    return PEARL_R + 0.075 * Math.sin(Math.PI * u) * (1 - u * 0.3);
  }
  return lerp(PEARL_R, PEARL_FLOAT, easeInOutCubic(seg(t, PHASE.unfoldStart + 0.1, PHASE.unfoldEnd)));
}

/** How far the camera has gone from the poster to the front shot (0..1). */
export function camFront(t: number): number {
  "worklet";
  return easeInOutCubic(seg(t, PHASE.turnEnd, PHASE.foldEnd)) - easeInOutCubic(seg(t, 12.35, 13.95));
}

/** The film's camera: [eye x, y, z, target x, y, z]. */
export function cameraAt(t: number, out: number[]): void {
  "worklet";
  const k = camFront(t);
  const p = SHOTS.poster;
  const f = SHOTS.front;
  out[0] = lerp(p.eye[0], f.eye[0], k);
  out[1] = lerp(p.eye[1], f.eye[1], k);
  out[2] = lerp(p.eye[2], f.eye[2], k);
  out[3] = lerp(p.target[0], f.target[0], k);
  out[4] = lerp(p.target[1], f.target[1], k);
  out[5] = lerp(p.target[2], f.target[2], k);
}

/** The camera at the hand-off (the 2D mark is drawn from it). */
export function handoffCamera(out: number[]): void {
  "worklet";
  cameraAt(PHASE.handoff, out);
}

// ---------------------------------------------------------------- layers

/** 3D stage visibility (the wrapper's opacity). */
export function stageVisible(t: number): number {
  "worklet";
  return 1 - seg(t, PHASE.handoff + 0.25, PHASE.handoff + 0.55) + seg(t, 12.4, 12.7);
}

/** The flat 2D mark's visibility. */
export function markVisible(t: number): number {
  "worklet";
  return easeOutCubic(seg(t, PHASE.handoff, PHASE.handoff + 0.35)) * (1 - seg(t, 12.6, 12.85));
}

/** The gleam across the 2D mark: its position -0.2..1.2 and strength. */
export function markGleam(t: number, out: number[]): void {
  "worklet";
  const u = seg(t, PHASE.handoff + 0.35, PHASE.handoff + 0.95);
  out[0] = lerp(-0.25, 1.25, easeInOutCubic(u));
  out[1] = bump(t, PHASE.handoff + 0.35, PHASE.handoff + 0.95);
}

/** How far the mark has risen to its place on the welcome screen (0..1). */
export function markRise(t: number): number {
  "worklet";
  return easeInOutCubic(seg(t, PHASE.riseStart, PHASE.riseEnd)) - easeInOutCubic(seg(t, 11.7, 12.5));
}

/** Track, shadows and hint under the sculpture. */
export function posterUi(t: number): number {
  "worklet";
  return 1 - easeOutCubic(seg(t, PHASE.turnEnd + 0.1, PHASE.turnEnd + 0.5)) + easeInOutCubic(seg(t, 13.35, 13.95));
}

/** Letter i of the wordmark: 0..1. */
export function wordLetter(t: number, i: number): number {
  "worklet";
  const a = 7.9 + i * 0.05;
  return easeOutCubic(seg(t, a, a + 0.5)) * (1 - easeInCubic(seg(t, 11.5, 11.8)));
}

/** Item j of the welcome screen (title, body, primary, secondary): 0..1. */
export function welcomeItem(t: number, j: number): number {
  "worklet";
  const a = PHASE.welcomeIn + j * 0.08;
  const b = PHASE.holdEnd + (3 - j) * 0.04;
  return easeOutCubic(seg(t, a, a + 0.55)) * (1 - easeInCubic(seg(t, b, b + 0.3)));
}

// ---------------------------------------------------------------- the stage camera, for drawing 2D over 3D

/** Projects world point p with camera c (eye, target) into a w x h view; writes [x, y, depth]. */
export function project(px: number, py: number, pz: number, c: number[], w: number, h: number, out: number[]): void {
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
  const ux = -rz * fy;
  const uy = rz * fx - rx * fz;
  const uz = rx * fy;
  const focal = w / 2 / Math.tan((HFOV_DEG * Math.PI) / 360);
  const dx = px - c[0];
  const dy = py - c[1];
  const dz = pz - c[2];
  const z = dx * fx + dy * fy + dz * fz;
  out[0] = w / 2 + ((dx * rx + dz * rz) / z) * focal;
  out[1] = h / 2 - ((dx * ux + dy * uy + dz * uz) / z) * focal;
  out[2] = z;
}
