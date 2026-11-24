// One loop time drives the film: every part of the machine, the cup and the panel is a pure function of t, so t = LOOP
// gives the same frame as t = 0. A finger's shot plays the same timeline (from PHASE.brew), so it looks the same.
//
//  0.00 – 0.75  rest: Single selected, the cup empty, the needle at 0, the group light off
//  0.75         Double tapped on the segmented control
//  1.35         Brew tapped: the group light warms up (to 1.70)
//  1.40 – 2.35  pre-infusion: the needle climbs to 3 bar and waits
//  2.35 – 3.10  full pressure: up to 9 bar, a slight tremble while it holds
//  2.45         first drops: two streams fall from the spouts and fill out by 3.10
//  2.60 – 6.95  the cup fills, the crema rises on top (2.80 – 7.50) and turns slowly
//  6.50         the pump stops: the needle falls (to 7.20), the streams thin and their tails fall (6.80 – 7.05)
//  7.10 – 7.70  the light fades; "Shot ready"
//  7.85 – 8.45  the cup is carried out to the right; a fresh one comes in (8.55 – 9.20)
//  8.95         Single tapped again
//  9.20 – 10.0  rest; last frame = first frame. The camera leans in for the shot (1.5 – 3.3) and back (7.4 – 9.1).

import {
  AZ_PEAK,
  AZ_REST,
  CREMA_SWIRL,
  CUP_AWAY,
  CUP_LIFT,
  DIST,
  EL_PEAK,
  EL_REST,
  FRAME_H,
  FRAME_W,
  GAUGE_MAX_BAR,
  NEEDLE_SPAN_DEG,
  NEEDLE_ZERO_DEG,
  SHOT_S,
  TARGET_PEAK,
  TARGET_REST,
  ZOOM_PEAK,
} from "./espressoScene";

export const LOOP = 10;

export const PHASE = {
  dose: 0.75,
  brew: 1.35,
  lightIn: 1.7,
  pre: 1.4,
  preEnd: 2.0,
  ramp: 2.35,
  rampEnd: 3.1,
  stream: 2.45,
  streamHit: 2.72,
  streamFull: 3.1,
  fill: 2.6,
  fillEnd: 6.95,
  crema: 2.8,
  cremaEnd: 7.5,
  stop: 6.5,
  dropEnd: 7.2,
  thinEnd: 6.8,
  tailEnd: 7.05,
  lightOut: 7.1,
  lightOutEnd: 7.7,
  /** A finger's shot stops here, with the cup full. */
  full: 7.75,
  cupOut: 7.85,
  cupOutEnd: 8.45,
  /** The cup is out of frame: from here it is the fresh, empty one. */
  swap: 8.5,
  cupIn: 8.55,
  cupInEnd: 9.2,
  single: 8.95,
  camIn: 1.5,
  camInEnd: 3.3,
  camOut: 7.4,
  camOutEnd: 9.1,
} as const;

/** The film's taps: [time, what] with 0 = Single, 1 = Double (segmented control), 2 = the Brew button. */
export const TAPS = [
  [PHASE.dose, 1],
  [PHASE.brew, 2],
  [PHASE.single, 0],
] as const;

/** Pressure: pre-infusion and full (bar), and the tremble while it holds. */
export const PRE_BAR = 3;
export const FULL_BAR = 9;
export const TREMBLE_BAR = 0.14;
export const TREMBLE_CYCLES = 9;
/** The second stream starts this much after the first (s): two spouts never quite agree. */
export const STREAM_LAG = 0.06;
/** A stream's width at its first drops, as a share of its full width; and after the pump stops. */
export const STREAM_THIN = 0.45;
export const STREAM_END = 0.5;

/** Status lines, by `statusAt`. */
export const STATUS = ["Ready", "Pre-infusion", "Extracting", "Shot ready", "Fresh cup"] as const;

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

export function easeOut(x: number): number {
  "worklet";
  const k = 1 - x;
  return 1 - k * k * k;
}

// ---------------------------------------------------------------- the film

/** The selected dose in the film: 0 = Single, 1 = Double. */
export function filmDose(t: number): number {
  "worklet";
  return t >= PHASE.dose && t < PHASE.single ? 1 : 0;
}

/** The pump's pressure (bar). */
export function barAt(t: number): number {
  "worklet";
  const pre = PRE_BAR * easeOut(seg(t, PHASE.pre, PHASE.preEnd));
  const full = (FULL_BAR - PRE_BAR) * smooth(seg(t, PHASE.ramp, PHASE.rampEnd));
  const hold = smooth(seg(t, PHASE.rampEnd, PHASE.rampEnd + 0.4)) * (1 - smooth(seg(t, PHASE.stop - 0.3, PHASE.stop)));
  const tremble = TREMBLE_BAR * hold * Math.sin((2 * Math.PI * TREMBLE_CYCLES * t) / LOOP);
  return (pre + full + tremble) * (1 - smooth(seg(t, PHASE.stop, PHASE.dropEnd)));
}

/** The needle's angle (deg, counter-clockwise from straight up) for a pressure. */
export function needleDeg(bar: number): number {
  "worklet";
  return NEEDLE_ZERO_DEG - (bar / GAUGE_MAX_BAR) * NEEDLE_SPAN_DEG;
}

/** The group head's light, 0..1. */
export function lightAt(t: number): number {
  "worklet";
  return smooth(seg(t, PHASE.brew, PHASE.lightIn)) * (1 - smooth(seg(t, PHASE.lightOut, PHASE.lightOutEnd)));
}

/** How full the cup is, 0..1 of the dose (the fresh cup after the swap is empty). */
export function fillAt(t: number): number {
  "worklet";
  if (t >= PHASE.swap) return 0;
  return smooth(seg(t, PHASE.fill, PHASE.fillEnd));
}

/** How much of its crema the shot has, 0..1. */
export function cremaAt(t: number): number {
  "worklet";
  if (t >= PHASE.swap) return 0;
  return smooth(seg(t, PHASE.crema, PHASE.cremaEnd));
}

/** The crema's turn (rad). */
export function swirlAt(t: number): number {
  "worklet";
  return CREMA_SWIRL * smooth(seg(t, PHASE.fill, PHASE.cremaEnd + 0.1));
}

/** Stream i: out = [head, tail, width]: how far down the gap from spout to surface its head and its tail are (0..1),
 * and its width as a share of a full stream. */
export function streamAt(t: number, i: number, out: number[]): void {
  "worklet";
  const lag = i * STREAM_LAG;
  const h = seg(t, PHASE.stream + lag, PHASE.streamHit + lag);
  const tl = seg(t, PHASE.thinEnd + lag, PHASE.tailEnd + lag);
  out[0] = h * h;
  out[1] = tl * tl;
  const grow = lerp(STREAM_THIN, 1, smooth(seg(t, PHASE.stream + lag, PHASE.streamFull)));
  out[2] = t < PHASE.stream + lag ? 0 : grow * (1 - (1 - STREAM_END) * smooth(seg(t, PHASE.stop, PHASE.thinEnd)));
}

/** The cup's slide for the change: out = [x (m), lift (m)]. */
export function cupAt(t: number, out: number[]): void {
  "worklet";
  if (t < PHASE.cupIn) {
    const k = seg(t, PHASE.cupOut, PHASE.cupOutEnd);
    out[0] = CUP_AWAY * smooth(k);
    out[1] = CUP_LIFT * Math.sin(Math.PI * k);
    return;
  }
  const k = seg(t, PHASE.cupIn, PHASE.cupInEnd);
  out[0] = CUP_AWAY * (1 - smooth(k));
  out[1] = CUP_LIFT * Math.sin(Math.PI * k);
}

/** The machine's shot timer (its own seconds), reset with the fresh cup. */
export function shotSecondsAt(t: number): number {
  "worklet";
  if (t >= PHASE.swap) return 0;
  return SHOT_S * seg(t, PHASE.brew, PHASE.stop);
}

/** The status line's index into STATUS. */
export function statusAt(t: number): number {
  "worklet";
  if (t < PHASE.brew) return 0;
  if (t < PHASE.ramp) return 1;
  if (t < PHASE.stop) return 2;
  if (t < PHASE.cupOut) return 3;
  if (t < PHASE.cupInEnd) return 4;
  return 0;
}

/** The film's finger: out = [opacity, pressed, target (0 Single, 1 Double, 2 Brew)]. */
export function tapGhost(t: number, out: number[]): void {
  "worklet";
  let best = 0;
  let dist = 1e9;
  for (let k = 0; k < TAPS.length; k++) {
    const d = Math.abs(t - TAPS[k][0]);
    if (d < dist) {
      dist = d;
      best = k;
    }
  }
  const at = TAPS[best][0];
  out[0] = seg(t, at - 0.32, at - 0.1) * (1 - seg(t, at + 0.1, at + 0.32));
  out[1] = seg(t, at - 0.1, at) * (1 - seg(t, at + 0.04, at + 0.18));
  out[2] = TAPS[best][1];
}

// ---------------------------------------------------------------- camera

/** How far the camera has leaned in towards the cup, 0..1. */
export function camAt(t: number): number {
  "worklet";
  return smooth(seg(t, PHASE.camIn, PHASE.camInEnd)) * (1 - smooth(seg(t, PHASE.camOut, PHASE.camOutEnd)));
}

/** tan(half the vertical field of view) that keeps FRAME_W x FRAME_H in a stage of this aspect. */
export function tanHalfFovY(aspect: number): number {
  "worklet";
  return Math.max(FRAME_H / 2, FRAME_W / 2 / aspect) / DIST;
}

/** The camera for a lean k and a finger's orbit (deg): out = [eye xyz, target xyz]. */
export function cameraAt(k: number, orbit: number, out: number[]): void {
  "worklet";
  const az = ((lerp(AZ_REST, AZ_PEAK, k) + orbit) * Math.PI) / 180;
  const el = (lerp(EL_REST, EL_PEAK, k) * Math.PI) / 180;
  const d = DIST * (1 - ZOOM_PEAK * k);
  const tx = lerp(TARGET_REST[0], TARGET_PEAK[0], k);
  const ty = lerp(TARGET_REST[1], TARGET_PEAK[1], k);
  const tz = lerp(TARGET_REST[2], TARGET_PEAK[2], k);
  out[0] = tx + d * Math.cos(el) * Math.sin(az);
  out[1] = ty + d * Math.sin(el);
  out[2] = tz + d * Math.cos(el) * Math.cos(az);
  out[3] = tx;
  out[4] = ty;
  out[5] = tz;
}

/** A finger's orbit (deg) from its drag (pt), rubber-banded past ORBIT_MAX. */
export const ORBIT_SPAN = 70;
export const ORBIT_MAX = 22;
export function orbitFromDrag(from: number, dx: number, width: number): number {
  "worklet";
  const a = from + (dx / width) * ORBIT_SPAN;
  const m = Math.abs(a);
  if (m <= ORBIT_MAX) return a;
  const over = m - ORBIT_MAX;
  return Math.sign(a) * (ORBIT_MAX + (over * 8) / (over + 8));
}
