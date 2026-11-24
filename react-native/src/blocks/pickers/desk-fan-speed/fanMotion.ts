// One loop time drives the film. The blades' speed is a sum of smooth ramps, and their angle is its exact integral,
// scaled so the loop holds a whole number of turns: t = LOOP gives the same frame as t = 0.
//
//  0.00 – 0.80  rest: Off, blades still, head at its rest angle
//  0.80         level 1: spins up over 0.7 s
//  2.60         level 2
//  4.30         level 3: the peak, the camera has moved in a little
//  6.60         back to level 1
//  7.70         Off: the blades coast down for 1.7 s (to 9.4)
//  1.10 – 9.20  the head oscillates, two sweeps, under an envelope that eases in and out
//  9.40 – 10.0  rest; last frame = first frame

import { AZ_PEAK, AZ_REST, DIST, EL_PEAK, EL_REST, FRAME_H, FRAME_W, RPM, TARGET, WATTS, ZOOM_PEAK } from "./fanScene";

export const LOOP = 10;
/** Whole turns of the blades in one loop. */
export const TURNS = 20;
/** Blade speed for each level (turns a second, before the small scale that makes TURNS exact). */
export const SPEEDS = [0, 1.5, 2.9, 4.4] as const;
/** The film's level changes: [time, new level, ramp length (s)]. */
export const RAMPS = [
  [0.8, 1, 0.7],
  [2.6, 2, 0.7],
  [4.3, 3, 0.7],
  [6.6, 1, 0.9],
  [7.7, 0, 1.7],
] as const;
/** The head: rest angle and the film's sweep (deg), its sweeps per loop and envelope. */
export const YAW_REST = -10;
export const OSC_DEG = 24;
export const OSC_CYCLES = 2;
export const OSC_IN = [1.1, 2.4] as const;
export const OSC_OUT = [7.4, 9.2] as const;
/** A finger's aim: degrees for a drag across the screen, and the most it turns (rubber-banded past it). */
export const AIM_SPAN = 110;
export const AIM_MAX = 42;
/** A finger's speed changes: time constants (s) of the motor spinning up and coasting down. */
export const SPIN_UP_TAU = 0.42;
export const SPIN_DOWN_TAU = 0.95;
/** Airflow dashes: the pattern's length (pt) and how many patterns pass per blade turn (a half, so TURNS stays whole). */
export const FLOW_PERIOD = 72;
export const FLOW_PER_TURN = 0.5;
/** A pressed key takes this long to go down (s, around its moment). */
export const KEY_LEAD = 0.05;
export const KEY_FALL = 0.09;

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

/** The integral of smooth(x) from 0 to x. */
export function smoothIntegral(x: number): number {
  "worklet";
  return x * x * x - (x * x * x * x) / 2;
}

/** The turns of the film's speed profile from 0 to t, before scaling. */
export function rawTurns(t: number): number {
  "worklet";
  let total = 0;
  let prev = 0;
  for (let i = 0; i < RAMPS.length; i++) {
    const at = RAMPS[i][0];
    const to = SPEEDS[RAMPS[i][1]];
    const len = RAMPS[i][2];
    const dv = to - prev;
    prev = to;
    if (t <= at) continue;
    if (t >= at + len) total += dv * (t - at - len / 2);
    else total += dv * len * smoothIntegral((t - at) / len);
  }
  return total;
}

const TURN_SCALE = TURNS / rawTurns(LOOP);

// ---------------------------------------------------------------- the film

/** The film's blade angle, in turns (0 .. TURNS). */
export function filmTurns(t: number): number {
  "worklet";
  return rawTurns(t) * TURN_SCALE;
}

/** The film's blade speed (turns a second). */
export function filmSpeed(t: number): number {
  "worklet";
  let v = 0;
  let prev = 0;
  for (let i = 0; i < RAMPS.length; i++) {
    const to = SPEEDS[RAMPS[i][1]];
    v += (to - prev) * smooth(seg(t, RAMPS[i][0], RAMPS[i][0] + RAMPS[i][2]));
    prev = to;
  }
  return v * TURN_SCALE;
}

/** The level the film has selected. */
export function filmLevel(t: number): number {
  "worklet";
  let level = 0;
  for (let i = 0; i < RAMPS.length; i++) {
    if (t >= RAMPS[i][0]) level = RAMPS[i][1];
  }
  return level;
}

/** The film's head angle (deg). */
export function filmYaw(t: number): number {
  "worklet";
  const env = smooth(seg(t, OSC_IN[0], OSC_IN[1])) * (1 - smooth(seg(t, OSC_OUT[0], OSC_OUT[1])));
  return YAW_REST + OSC_DEG * env * Math.sin((2 * Math.PI * OSC_CYCLES * t) / LOOP);
}

/** How far key i is down in the film, 0..1. */
export function filmKey(t: number, i: number): number {
  "worklet";
  let v = i === 0 ? 1 : 0;
  let prev = 0;
  for (let k = 0; k < RAMPS.length; k++) {
    const e = smooth(seg(t, RAMPS[k][0] - KEY_LEAD, RAMPS[k][0] + KEY_FALL));
    if (RAMPS[k][1] === i) v += e;
    if (prev === i) v -= e;
    prev = RAMPS[k][1];
  }
  return clamp01(v);
}

/** The film's finger on the segmented control: out = [opacity, pressed, level it is over]. */
export function tapGhost(t: number, out: number[]): void {
  "worklet";
  let best = 0;
  let dist = 1e9;
  for (let k = 0; k < RAMPS.length; k++) {
    const d = Math.abs(t - RAMPS[k][0]);
    if (d < dist) {
      dist = d;
      best = k;
    }
  }
  const at = RAMPS[best][0];
  out[0] = seg(t, at - 0.3, at - 0.08) * (1 - seg(t, at + 0.1, at + 0.3));
  out[1] = seg(t, at - 0.08, at) * (1 - seg(t, at + 0.04, at + 0.16));
  out[2] = RAMPS[best][1];
}

// ---------------------------------------------------------------- what the speed reads as

/** How much the blades are a blur at this speed, 0..1. */
export function blurAt(speed: number): number {
  "worklet";
  return smooth(seg(speed, 0.9, 3.6)) * 0.85;
}

/** A reading between the levels' values, from the blade speed. */
export function readAt(speed: number, table: readonly number[]): number {
  "worklet";
  for (let i = 0; i < SPEEDS.length - 1; i++) {
    if (speed <= SPEEDS[i + 1]) return lerp(table[i], table[i + 1], seg(speed, SPEEDS[i], SPEEDS[i + 1]));
  }
  return table[table.length - 1];
}

export function rpmAt(speed: number): number {
  "worklet";
  return Math.round(readAt(speed, RPM) / 10) * 10;
}

export function wattsAt(speed: number): number {
  "worklet";
  return Math.round(readAt(speed, WATTS));
}

// ---------------------------------------------------------------- camera

/** tan(half the vertical field of view) that keeps FRAME_W x FRAME_H around the target in a stage of this aspect. */
export function tanHalfFovY(aspect: number): number {
  "worklet";
  return Math.max(FRAME_H / 2, FRAME_W / 2 / aspect) / DIST;
}

/** The camera for a blade speed (it leans in as the fan works): out = [eye xyz]. It looks at TARGET. */
export function cameraAt(speed: number, out: number[]): void {
  "worklet";
  const k = smooth(clamp01(speed / SPEEDS[3]));
  const az = (lerp(AZ_REST, AZ_PEAK, k) * Math.PI) / 180;
  const el = (lerp(EL_REST, EL_PEAK, k) * Math.PI) / 180;
  const d = DIST * (1 - ZOOM_PEAK * k);
  out[0] = TARGET[0] + d * Math.cos(el) * Math.sin(az);
  out[1] = TARGET[1] + d * Math.sin(el);
  out[2] = TARGET[2] + d * Math.cos(el) * Math.cos(az);
}

/** A finger's aim from its drag (pt), rubber-banded past AIM_MAX either side of the centre. */
export function aimFromDrag(from: number, dx: number, width: number): number {
  "worklet";
  const a = from + (dx / width) * AIM_SPAN;
  const m = Math.abs(a);
  if (m <= AIM_MAX) return a;
  const over = m - AIM_MAX;
  return Math.sign(a) * (AIM_MAX + (over * 12) / (over + 12));
}
