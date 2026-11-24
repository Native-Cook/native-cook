// One loop drives the film, and one number drives the switch: `press`, 0 (off: the bottom half in) .. 1 (on: the
// top half in). The film is press = filmPress(t); a finger moves the same number, so the film is a demo of the
// gesture. The light follows the latch (press >= 0.5) the way a filament does: a short lag, then an ease-out
// warm-up. Every value below is a pure function of t, so t = LOOP gives the same frame as t = 0.
//
//  0.0 – 0.8   poster: the room at dusk, the switch off, the camera drifting
//  0.8 – 1.22  a finger presses the top half: the rocker follows the press 40 % of the way
//  1.22        it tips past the middle and snaps home on a spring (click); 120 ms later the filament warms up
//              (0.52 s ease-out): the globe glows, the wall takes the warm pool, the plate throws its shadow
//  2.0 – 4.4   the camera eases a few degrees round the wall
//  2.5 – 3.2   the brightness slider goes down to 40 %,  3.7 – 4.4 back up to 100 %
//  4.9 – 6.9   the camera eases back
//  5.2 – 5.62  the finger presses the bottom half; at 5.62 it snaps off (click), the light dies in 0.3 s
//  6.9 – 8.0   poster; last frame = first frame

import { EYE_ORBIT, EYE_REST, FRAME_H, FRAME_W, TARGET, WARM_FULL, WARM_LOW } from "./switchScene";
import type { Vec3 } from "./switchScene";

export const LOOP = 8;

export const PHASE = {
  pressOn: 0.8,
  snapOn: 1.22,
  orbitOut: 2.0,
  orbitOutEnd: 4.4,
  dim: 2.5,
  dimEnd: 3.2,
  bright: 3.7,
  brightEnd: 4.4,
  orbitBack: 4.9,
  orbitBackEnd: 6.9,
  pressOff: 5.2,
  snapOff: 5.62,
} as const;

/** A press pushes the rocker this far (0..1) before it tips, over PRESS seconds in the film. */
export const PRESS_DEPTH = 0.4;
export const PRESS = PHASE.snapOn - PHASE.pressOn;
/** The snap: an under-damped spring, x(τ) = 1 - e^(-Zτ) (cos Wτ + Z/W sin Wτ). As a Reanimated spring: mass 1,
 * stiffness Z² + W² = 1096, damping 2Z = 28. */
export const SNAP_Z = 14;
export const SNAP_W = 30;
export const SNAP_SPRING = { mass: 1, stiffness: SNAP_Z * SNAP_Z + SNAP_W * SNAP_W, damping: 2 * SNAP_Z } as const;
/** The filament: the light starts LAG after the click and warms up over WARM (ease-out cubic); it dies after
 * LAG_OFF over COOL. Seconds. */
export const LAG = 0.12;
export const WARM = 0.52;
export const LAG_OFF = 0.06;
export const COOL = 0.3;
/** The film dims the light to this brightness and back. */
export const DIM_TO = 0.4;
/** The slider's range. */
export const BRIGHT_MIN = 0.1;
/** A finger: 2 s after the last touch the switch goes off, then the film restarts. */
export const IDLE_MS = 2000;
export const RETURN_MS = 1100;
/** The touch dot: fades in this long before a press and out this long after the snap (s). */
export const GHOST_IN = 0.3;
export const GHOST_OUT = 0.3;

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

export function easeOutCubic(x: number): number {
  "worklet";
  return 1 - Math.pow(1 - x, 3);
}

export function easeInQuad(x: number): number {
  "worklet";
  return x * x;
}

export function easeInOutSine(x: number): number {
  "worklet";
  return -(Math.cos(Math.PI * x) - 1) / 2;
}

/** The snap spring's step response at τ seconds (0 → 1, one small overshoot). */
export function snapStep(tau: number): number {
  "worklet";
  if (tau <= 0) return 0;
  return 1 - Math.exp(-SNAP_Z * tau) * (Math.cos(SNAP_W * tau) + (SNAP_Z / SNAP_W) * Math.sin(SNAP_W * tau));
}

/** One flip, τ seconds after the finger starts pressing: follows the press to PRESS_DEPTH, then snaps to 1. */
export function flipCurve(tau: number): number {
  "worklet";
  if (tau <= 0) return 0;
  if (tau < PRESS) return PRESS_DEPTH * easeInQuad(tau / PRESS);
  return PRESS_DEPTH + (1 - PRESS_DEPTH) * snapStep(tau - PRESS);
}

/** A finger dragging the rocker: it resists near either rest and tips quickly through the middle. x and the result
 * are 0..1; 0, 0.5 and 1 stay where they are. */
export function detent(x: number): number {
  "worklet";
  return x - 0.1 * Math.sin(2 * Math.PI * x);
}

/** The x that detent() maps to p (bisection; detent is monotonic). */
export function detentInverse(p: number): number {
  "worklet";
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 14; i++) {
    const mid = (lo + hi) / 2;
    if (detent(mid) < p) lo = mid;
    else hi = mid;
  }
  return (lo + hi) / 2;
}

// ---------------------------------------------------------------- the film

/** The rocker, 0 (off) .. 1 (on). */
export function filmPress(t: number): number {
  "worklet";
  if (t < PHASE.pressOff) return flipCurve(t - PHASE.pressOn);
  return 1 - flipCurve(t - PHASE.pressOff);
}

/** The filament, 0..1 (before the brightness). */
export function filmLevel(t: number): number {
  "worklet";
  const up = easeOutCubic(seg(t, PHASE.snapOn + LAG, PHASE.snapOn + LAG + WARM));
  const down = easeOutCubic(seg(t, PHASE.snapOff + LAG_OFF, PHASE.snapOff + LAG_OFF + COOL));
  return up * (1 - down);
}

/** The brightness slider, DIM_TO..1. */
export function filmBright(t: number): number {
  "worklet";
  const k = easeInOutSine(seg(t, PHASE.dim, PHASE.dimEnd)) - easeInOutSine(seg(t, PHASE.bright, PHASE.brightEnd));
  return 1 - (1 - DIM_TO) * k;
}

/** The camera's way round the wall, 0 (rest) .. 1. */
export function filmOrbit(t: number): number {
  "worklet";
  return easeInOutSine(seg(t, PHASE.orbitOut, PHASE.orbitOutEnd)) - easeInOutSine(seg(t, PHASE.orbitBack, PHASE.orbitBackEnd));
}

/** The touch dot: out = [opacity 0..1, half (+1 the top half, -1 the bottom), pressed 0..1]. */
export function filmGhost(t: number, out: number[]): void {
  "worklet";
  const second = t > (PHASE.snapOn + PHASE.pressOff) / 2;
  const a = second ? PHASE.pressOff : PHASE.pressOn;
  const b = second ? PHASE.snapOff : PHASE.snapOn;
  out[0] = seg(t, a - GHOST_IN, a) * (1 - seg(t, b + 0.05, b + 0.05 + GHOST_OUT));
  out[1] = second ? -1 : 1;
  out[2] = seg(t, a, b) * (1 - seg(t, b, b + 0.12));
}

// ---------------------------------------------------------------- light

/** What the scene shows: the filament times the brightness. */
export function lightOut(level: number, bright: number): number {
  "worklet";
  return clamp01(level) * clamp01(bright);
}

/** The light's colour (linear RGB) at an output k: orange while it barely glows, warm white at full. */
export function warmColor(k: number, out: number[]): void {
  "worklet";
  const q = Math.sqrt(clamp01(k));
  for (let i = 0; i < 3; i++) out[i] = lerp(WARM_LOW[i], WARM_FULL[i], q);
}

// ---------------------------------------------------------------- camera

const DIST = Math.hypot(EYE_REST[0] - TARGET[0], EYE_REST[1] - TARGET[1], EYE_REST[2] - TARGET[2]);

/** tan(half the vertical field of view) that keeps FRAME_W x FRAME_H of wall in a stage of this aspect (w / h). */
export function tanHalfFovY(aspect: number): number {
  "worklet";
  return Math.max(FRAME_H / 2, FRAME_W / 2 / aspect) / DIST;
}

/** Camera at orbit k and film time t: out = [eye xyz, target xyz]. A slow drift (period LOOP) keeps it alive. */
export function cameraAt(k: number, t: number, out: number[]): void {
  "worklet";
  const w = (2 * Math.PI * t) / LOOP;
  out[0] = lerp(EYE_REST[0], EYE_ORBIT[0], k) + 0.006 * Math.sin(w);
  out[1] = lerp(EYE_REST[1], EYE_ORBIT[1], k) + 0.004 * Math.sin(2 * w);
  out[2] = lerp(EYE_REST[2], EYE_ORBIT[2], k);
  out[3] = TARGET[0];
  out[4] = TARGET[1];
  out[5] = TARGET[2];
}

/** A world point on the stage (w x h pt) for camera c (from cameraAt): out = [x, y, points per metre there]. */
export function project(c: number[], w: number, h: number, p: Vec3, out: number[]): void {
  "worklet";
  // forward, right (forward x up), up
  let fx = c[3] - c[0];
  let fy = c[4] - c[1];
  let fz = c[5] - c[2];
  const fl = Math.hypot(fx, fy, fz);
  fx /= fl;
  fy /= fl;
  fz /= fl;
  let rx = -fz;
  let rz = fx;
  const rl = Math.hypot(rx, rz);
  rx /= rl;
  rz /= rl;
  const ux = -rz * fy;
  const uy = rz * fx - rx * fz;
  const uz = rx * fy;
  const dx = p[0] - c[0];
  const dy = p[1] - c[1];
  const dz = p[2] - c[2];
  const depth = dx * fx + dy * fy + dz * fz;
  const th = tanHalfFovY(w / h);
  const ppm = h / 2 / (th * depth);
  out[0] = w / 2 + (dx * rx + dz * rz) * ppm;
  out[1] = h / 2 - (dx * ux + dy * uy + dz * uz) * ppm;
  out[2] = ppm;
}
