// One loop time drives everything: the carton (flaps, tape, label, its slide), the camera and the list. Every value
// below is a pure function of t, so t = LOOP gives the same frame as t = 0. A finger never adds state of its own: a
// tap on a step moves t to that step's time, and a drag adds an orbit angle that springs back.
//
//  0.00 – 0.80  rest: an open carton, "Packed" in progress
//  0.80 – 2.40  step 1: the two short flaps fall shut (a small bounce each), then the two long ones; the camera
//               rises and moves in. 2.40 "Packed" done
//  2.75 – 3.95  step 2: the tape runs up the left wall, along the seam and down the right wall at one speed, while a
//               finger drags the carton round 18 deg; it lets go at 3.90 and the view swings back. 4.05 "Sealed" done
//  4.60 – 5.35  step 3: the label drops, lands at 4.95 and settles with a small squash. 5.40 "Labelled" done
//  5.80 – 6.70  the camera eases back to where it started
//  6.25 – 7.45  step 4: the carton backs up 3 cm, then slides off to the right. 7.30 "On its way" done
//  7.65         out of sight, the carton becomes a new open one on the left
//  7.80 – 8.15  the list starts over
//  7.90 – 8.75  the new carton slides in, turning the last few degrees square
//  8.75 – 10.0  rest; last frame = first frame

import { BOX_YAW, DIST, EL_REST, FLAPS, FRAME_H, FRAME_W, STEP_N, TAPE_SEAM, TAPE_SIDE, TARGET } from "./parcelScene";

export const LOOP = 10;

/** Each flap's fall, [start, end] in the order of FLAPS: left, right (short), front, back (long). */
export const FOLDS = [0.8, 1.45, 0.98, 1.63, 1.5, 2.2, 1.72, 2.4] as const;

export const PHASE = {
  tape: 2.75,
  tapeEnd: 3.95,
  labelDrop: 4.6,
  labelLand: 4.95,
  labelSettle: 5.4,
  backUp: 6.25,
  go: 6.55,
  gone: 7.45,
  /** out of sight: the carton is a new, open one from here */
  swap: 7.65,
  reset: 7.8,
  resetEnd: 8.15,
  arrive: 7.9,
  arriveEnd: 8.75,
  /** nothing moves from here to the loop's end (and until FOLDS[0] after it) */
  rest: 8.75,
} as const;

/** When each step is finished (its check appears; a light haptic). */
export const DONE = [2.4, 4.05, 5.4, 7.3] as const;
/** Where a tap on a step takes the clock: that step done and settled, the next not yet begun. */
export const JUMP = [2.7, 4.4, 5.75, 7.6] as const;
/** Reduced motion shows this still: sealed and labelled, waiting for the courier. */
export const REDUCED_T = 5.75;

/** The camera's film: it rises EL_LIFT deg and moves in by ZOOM_IN (a fraction of its distance). */
export const EL_LIFT = 7;
export const ZOOM_IN = 0.08;
export const CAM_IN = [0.8, 2.4] as const;
export const CAM_OUT = [5.8, 6.7] as const;
/** The film's finger: it presses at ghost[0], drags from ghost[1] to ghost[2] (the view turns ORBIT_FILM deg), lets
 * go at ghost[3]; the view is back by ghost[4]. */
export const GHOST = [2.55, 2.75, 3.75, 3.9, 4.6] as const;
export const ORBIT_FILM = 18;
/** A finger's orbit: degrees for a drag across the whole screen, and the most it turns (rubber-banded past it). */
export const ORBIT_SPAN = 70;
export const ORBIT_MAX = 25;

/** The slide: how far off screen the carton goes (m), how far it backs up first, how much it overshoots coming in
 * (the back ease's constant), and how far it is turned (deg) when it arrives. */
export const SLIDE = 1.15;
export const BACK_UP = 0.03;
export const ARRIVE_BACK = 0.6;
export const ARRIVE_YAW = 7;
/** The label: how high it starts (m), its tilt on the way down (deg), and its squash on landing. */
export const LABEL_DROP = 0.2;
export const LABEL_TILT = 14;
export const LABEL_SQUASH = 0.1;
export const BOX_SQUASH = 0.012;
/** A flap's bounce: it lands at FALL of its window, rebounds to BOUNCE_1 of its open angle, then BOUNCE_2. */
export const FALL = 0.62;
export const BOUNCE_1 = 0.06;
export const BOUNCE_2 = 0.018;

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

export function easeInOutSine(x: number): number {
  "worklet";
  return -(Math.cos(Math.PI * x) - 1) / 2;
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

/** Ease out with an overshoot (c = 0 is ease-out cubic). */
export function easeOutBack(x: number, c: number): number {
  "worklet";
  const u = x - 1;
  return 1 + (c + 1) * u * u * u + c * u * u;
}

/** A flap's way down over its window k (0..1): 1 (open) falls to 0 at FALL, then two small rebounds. */
export function flapFall(k: number): number {
  "worklet";
  if (k <= 0) return 1;
  if (k >= 1) return 0;
  if (k < FALL) {
    const u = k / FALL;
    return 1 - u * u;
  }
  const mid = FALL + (1 - FALL) * 0.63;
  if (k < mid) {
    const u = (k - FALL) / (mid - FALL);
    return BOUNCE_1 * 4 * u * (1 - u);
  }
  const u = (k - mid) / (1 - mid);
  return BOUNCE_2 * 4 * u * (1 - u);
}

// ---------------------------------------------------------------- the carton

/** Flap i (the order of FLAPS): 1 = open, 0 = shut. */
export function flapOpen(i: number, t: number): number {
  "worklet";
  if (t >= PHASE.swap) return 1;
  return flapFall(seg(t, FOLDS[i * 2], FOLDS[i * 2 + 1]));
}

/** The tape's three pieces, each 0..1 of its length: out = [up the left wall, along the seam, down the right wall]. */
export function tapeAt(t: number, out: number[]): void {
  "worklet";
  if (t >= PHASE.swap) {
    out[0] = 0;
    out[1] = 0;
    out[2] = 0;
    return;
  }
  const p = easeInOutSine(seg(t, PHASE.tape, PHASE.tapeEnd)) * (TAPE_SIDE * 2 + TAPE_SEAM);
  out[0] = clamp01(p / TAPE_SIDE);
  out[1] = clamp01((p - TAPE_SIDE) / TAPE_SEAM);
  out[2] = clamp01((p - TAPE_SIDE - TAPE_SEAM) / TAPE_SIDE);
}

/** The landing's ring (0 before it, a decaying wave after, gone by PHASE.labelSettle). */
export function landRing(t: number, decay: number, freq: number): number {
  "worklet";
  const tau = t - PHASE.labelLand;
  if (tau <= 0 || t >= PHASE.labelSettle) return 0;
  return Math.exp(-decay * tau) * Math.sin(freq * tau) * (1 - seg(t, PHASE.labelSettle - 0.12, PHASE.labelSettle));
}

/** The label: out = [height above the lid (m), size 0..1, tilt (deg), squash (its width and depth grow by this)]. */
export function labelAt(t: number, out: number[]): void {
  "worklet";
  if (t < PHASE.labelDrop || t >= PHASE.swap) {
    out[0] = LABEL_DROP;
    out[1] = 0;
    out[2] = LABEL_TILT;
    out[3] = 0;
    return;
  }
  const k = seg(t, PHASE.labelDrop, PHASE.labelLand);
  out[0] = LABEL_DROP * (1 - k * k);
  out[1] = easeOutCubic(seg(k, 0, 0.4));
  out[2] = LABEL_TILT * (1 - easeInOutSine(k));
  out[3] = LABEL_SQUASH * landRing(t, 9, 24);
}

/** The carton's height scale: it gives a little when the label lands. */
export function boxSquash(t: number): number {
  "worklet";
  return 1 - BOX_SQUASH * landRing(t, 12, 26);
}

/** The carton's place along x (m): backs up, slides off right, comes back in from the left. */
export function slideX(t: number): number {
  "worklet";
  if (t < PHASE.backUp) return 0;
  if (t < PHASE.go) return -BACK_UP * easeOutCubic(seg(t, PHASE.backUp, PHASE.go));
  if (t < PHASE.swap) return lerp(-BACK_UP, SLIDE, easeInCubic(seg(t, PHASE.go, PHASE.gone)));
  return lerp(-SLIDE, 0, easeOutBack(seg(t, PHASE.arrive, PHASE.arriveEnd), ARRIVE_BACK));
}

/** The carton's turn on the floor (deg about y). */
export function yawAt(t: number): number {
  "worklet";
  if (t < PHASE.swap) return BOX_YAW;
  return BOX_YAW + ARRIVE_YAW * (1 - easeOutCubic(seg(t, PHASE.arrive, PHASE.arriveEnd)));
}

// ---------------------------------------------------------------- camera

/** The film's camera move, 0 (rest) .. 1 (up and in). */
export function camIn(t: number): number {
  "worklet";
  return easeInOutSine(seg(t, CAM_IN[0], CAM_IN[1])) - easeInOutSine(seg(t, CAM_OUT[0], CAM_OUT[1]));
}

/** The film's orbit (deg): the ghost finger's drag, and the swing back when it lets go. */
export function filmOrbit(t: number): number {
  "worklet";
  return ORBIT_FILM * (easeInOutSine(seg(t, GHOST[1], GHOST[2])) - easeOutCubic(seg(t, GHOST[3], GHOST[4])));
}

/** The film's finger: out = [opacity 0..1, pressed 0..1, how far along its drag 0..1]. */
export function ghostAt(t: number, out: number[]): void {
  "worklet";
  out[0] = seg(t, GHOST[0] - 0.2, GHOST[0]) * (1 - seg(t, GHOST[3], GHOST[3] + 0.2));
  out[1] = seg(t, GHOST[0], GHOST[0] + 0.1) * (1 - seg(t, GHOST[3] - 0.05, GHOST[3] + 0.08));
  out[2] = easeInOutSine(seg(t, GHOST[1], GHOST[2]));
}

/** tan(half the vertical field of view) that keeps FRAME_W x FRAME_H around the target in a stage of this aspect. */
export function tanHalfFovY(aspect: number): number {
  "worklet";
  return Math.max(FRAME_H / 2, FRAME_W / 2 / aspect) / DIST;
}

/** The camera at film time t with a finger's orbit (deg) added: out = [eye xyz, target xyz]. */
export function cameraAt(t: number, orbit: number, out: number[]): void {
  "worklet";
  const k = camIn(t);
  const az = ((filmOrbit(t) + orbit) * Math.PI) / 180;
  const el = ((EL_REST + EL_LIFT * k) * Math.PI) / 180;
  const d = DIST * (1 - ZOOM_IN * k);
  out[0] = TARGET[0] + d * Math.cos(el) * Math.sin(az);
  out[1] = TARGET[1] + d * Math.sin(el);
  out[2] = TARGET[2] + d * Math.cos(el) * Math.cos(az);
  out[3] = TARGET[0];
  out[4] = TARGET[1];
  out[5] = TARGET[2];
}

/** A finger's orbit from its drag (pt) on a screen `width` wide: dragging right turns the carton right (the camera
 * goes left), rubber-banded past ORBIT_MAX. */
export function orbitFromDrag(dx: number, width: number): number {
  "worklet";
  const a = (-dx / width) * ORBIT_SPAN;
  const m = Math.abs(a);
  if (m <= ORBIT_MAX) return a;
  const over = m - ORBIT_MAX;
  return Math.sign(a) * (ORBIT_MAX + (over * 12) / (over + 12));
}

// ---------------------------------------------------------------- the list

/** The list starting over (0..1) once the carton has gone. */
export function resetAt(t: number): number {
  "worklet";
  return seg(t, PHASE.reset, PHASE.resetEnd);
}

/** Step i's check: 0 (not yet) .. 1 (done). */
export function doneAt(i: number, t: number): number {
  "worklet";
  return seg(t, DONE[i], DONE[i] + 0.25) * (1 - resetAt(t));
}

/** The check's scale: a small pop past 1 as it appears. */
export function checkScale(i: number, t: number): number {
  "worklet";
  const k = seg(t, DONE[i], DONE[i] + 0.32);
  return 0.5 + 0.5 * easeOutBack(k, 2.2);
}

/** The row in progress, as a place in the list (0..3, in between while it moves down). */
export function activeRow(t: number): number {
  "worklet";
  let a = 0;
  // back at the top once the list starts over (the highlight is hidden then: see activeAlpha)
  if (t >= PHASE.reset) return 0;
  for (let i = 0; i < STEP_N - 1; i++) a += easeInOutCubic(seg(t, DONE[i], DONE[i] + 0.4));
  return a;
}

/** The highlight behind the row in progress: gone while everything is done. */
export function activeAlpha(t: number): number {
  "worklet";
  return 1 - seg(t, DONE[STEP_N - 1], DONE[STEP_N - 1] + 0.25) + seg(t, PHASE.reset + 0.1, PHASE.resetEnd);
}

/** Headline j (0..4 finished steps): its opacity. */
export function headlineAlpha(j: number, t: number): number {
  "worklet";
  const before = j === 0 ? 1 : doneAt(j - 1, t);
  const after = j === STEP_N ? 0 : doneAt(j, t);
  return clamp01(before - after);
}

/** The pulse of the row in progress (0..1, one beat a second: LOOP is a whole number of beats). */
export function pulseAt(t: number): number {
  "worklet";
  return 0.5 - 0.5 * Math.cos(2 * Math.PI * t);
}

/** The number of flaps (the stage's loop). */
export const FLAP_N = FLAPS.length;
