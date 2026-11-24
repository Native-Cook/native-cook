// One loop time drives the film: the wheel, the hands, the switch, the ring and the camera are pure functions of t,
// so t = LOOP gives the same frame as t = 0. A finger's own changes use the same functions (the ring, the camera's
// push) from their own start times.
//
//  0.00 – 0.65  rest: 7:00, alarm off
//  0.65 – 0.90  a finger arrives on the wheel
//  0.90 – 1.80  it drags the wheel down to 6:30, one row every 0.15 s; the hands sweep back with it
//  1.80 – 2.40  the hands settle past 6:30 and back (a damped wave); the finger leaves
//  2.80         the finger taps the switch: alarm on
//  2.90 – 4.10  the ring: the hammer chatters between the bells, the bells shake, the clock hops and rocks on its
//               legs; the camera moves in and round (2.80 – 3.50)
//  4.40 – 5.60  the camera eases back
//  6.00         the finger taps the switch: alarm off
//  6.50 – 7.40  it drags the wheel back up to 7:00; the hands follow and settle (to 8.00)
//  8.20 – 10.0  rest; last frame = first frame

import { AZ_REST, DIST, EL_REST, FILM_MIN, FRAME_H, FRAME_W, HAMMER_REACH, REST_MIN, TARGET, WHEEL_STEP } from "./alarmScene";

export const LOOP = 10;

export const PHASE = {
  wheelA: 0.9,
  wheelAEnd: 1.8,
  on: 2.8,
  ring: 2.9,
  pushIn: 2.8,
  pushInEnd: 3.5,
  pushOut: 4.4,
  pushOutEnd: 5.6,
  off: 6.0,
  wheelB: 6.5,
  wheelBEnd: 7.4,
  rest: 8.2,
} as const;

/** The ring lasts this long (s): it comes in over RING_IN and dies over RING_OUT. */
export const RING = 1.2;
export const RING_IN = 0.05;
export const RING_OUT = 0.22;
/** The hammer: how far it swings either way (deg, just short of the bells) and how often (Hz). */
export const HAMMER_SWING = HAMMER_REACH - 0.6;
export const HAMMER_HZ = 15;
/** The bells: each shakes this much (deg) at its own rate (Hz). */
export const BELL_SHAKE = 2.2;
export const BELL_HZ_L = 23;
export const BELL_HZ_R = 26;
/** The clock: it rocks on its legs (deg, Hz), shuffles sideways (m, Hz), jumps once (m, over HOP_S) and then
 * chatters on the floor (m, hops a second). */
export const ROCK = 1.5;
export const ROCK_HZ = 11;
export const SHUFFLE = 0.0012;
export const SHUFFLE_HZ = 17;
export const HOP = 0.012;
export const HOP_S = 0.2;
export const CHATTER = 0.003;
export const CHATTER_HZ = 9;
/** The camera's push while it rings: closer by ZOOM (a fraction of its distance), round by AZ_PUSH, lower by EL_PUSH. */
export const ZOOM = 0.14;
export const AZ_PUSH = 17;
export const EL_PUSH = -3;
/** A finger's orbit: degrees for a drag across the whole screen, and the most it turns (rubber-banded past it). */
export const ORBIT_SPAN = 110;
export const ORBIT_MAX = 40;
/** The hands settling: a wave of this size (a fraction of the sweep) that decays at SETTLE_DECAY and swings at
 * SETTLE_FREQ (rad/s), gone after SETTLE_S. */
export const SETTLE = 0.04;
export const SETTLE_DECAY = 8;
export const SETTLE_FREQ = 16;
export const SETTLE_S = 0.6;
/** The film's wheel: its first row lands this long after the drag starts, then one row every WHEEL_BEAT. */
export const WHEEL_LEAD = 0.05;
export const WHEEL_BEAT = 0.15;
export const WHEEL_ROWS = (REST_MIN - FILM_MIN) / WHEEL_STEP;

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

export function smooth(x: number): number {
  "worklet";
  return x * x * (3 - 2 * x);
}

/** The shortest way round from one angle to another (deg, -180..180). */
export function shortest(d: number): number {
  "worklet";
  return (((d % 360) + 540) % 360) - 180;
}

/** The hour hand's angle (deg clockwise from 12) for a minute of the day. */
export function hourDeg(min: number): number {
  "worklet";
  return (min % 720) / 2;
}

/** The minute hand's angle (deg clockwise from 12) for a minute of the day. */
export function minuteDeg(min: number): number {
  "worklet";
  return (min % 60) * 6;
}

/** The damped wave the hands settle with, tau seconds after their sweep ends. */
export function settleWave(tau: number): number {
  "worklet";
  if (tau <= 0 || tau >= SETTLE_S) return 0;
  return SETTLE * Math.exp(-SETTLE_DECAY * tau) * Math.sin(SETTLE_FREQ * tau) * (1 - seg(tau, SETTLE_S - 0.15, SETTLE_S));
}

// ---------------------------------------------------------------- the film

/** The hands' way from the rest time (0) to the film's time (1), with the settle at either end. */
export function filmSweep(t: number): number {
  "worklet";
  const a = easeInOutSine(seg(t, PHASE.wheelA, PHASE.wheelAEnd)) + settleWave(t - PHASE.wheelAEnd);
  const b = easeInOutSine(seg(t, PHASE.wheelB, PHASE.wheelBEnd)) + settleWave(t - PHASE.wheelBEnd);
  return a - b;
}

/** The film's hands (deg clockwise from 12, not wrapped): from 7:00 back to 6:30 the minute hand goes round the
 * left side. */
export function filmHour(t: number): number {
  "worklet";
  return lerp(hourDeg(REST_MIN), hourDeg(FILM_MIN), filmSweep(t));
}

export function filmMinute(t: number): number {
  "worklet";
  return lerp(0, -(REST_MIN - FILM_MIN) * 6, filmSweep(t));
}

/** The film's wheel (minutes of the day, in steps of WHEEL_STEP). */
export function filmWheel(t: number): number {
  "worklet";
  const down = Math.min(WHEEL_ROWS, Math.max(0, Math.floor((t - PHASE.wheelA - WHEEL_LEAD) / WHEEL_BEAT) + 1));
  const up = Math.min(WHEEL_ROWS, Math.max(0, Math.floor((t - PHASE.wheelB - WHEEL_LEAD) / WHEEL_BEAT) + 1));
  return REST_MIN - WHEEL_STEP * (down - up);
}

/** The film's switch: 1 while the alarm is on. */
export function filmArmed(t: number): number {
  "worklet";
  return t >= PHASE.on && t < PHASE.off ? 1 : 0;
}

/** Seconds into the film's ring (negative: it is not ringing). */
export function filmRing(t: number): number {
  "worklet";
  const tau = t - PHASE.ring;
  return tau >= 0 && tau <= RING ? tau : -1;
}

/** The film's camera push, 0..1. */
export function filmPush(t: number): number {
  "worklet";
  return easeInOutSine(seg(t, PHASE.pushIn, PHASE.pushInEnd)) - easeInOutSine(seg(t, PHASE.pushOut, PHASE.pushOutEnd));
}

/** The film's finger on the wheel: out = [opacity 0..1, pressed 0..1, where it is along its drag (-0.5 above the
 * wheel's middle .. 0.5 below)]. It drags down to go back in time, up to come forward. */
export function wheelGhost(t: number, out: number[]): void {
  "worklet";
  const second = t > (PHASE.wheelAEnd + PHASE.wheelB) / 2;
  const a = second ? PHASE.wheelB : PHASE.wheelA;
  const b = second ? PHASE.wheelBEnd : PHASE.wheelAEnd;
  const k = easeInOutSine(seg(t, a, b));
  out[0] = seg(t, a - 0.25, a - 0.05) * (1 - seg(t, b + 0.1, b + 0.3));
  out[1] = seg(t, a - 0.05, a + 0.05) * (1 - seg(t, b, b + 0.12));
  out[2] = second ? 0.5 - k : k - 0.5;
}

/** The film's finger on the switch: out = [opacity 0..1, pressed 0..1]. */
export function toggleGhost(t: number, out: number[]): void {
  "worklet";
  const at = t > (PHASE.on + PHASE.off) / 2 ? PHASE.off : PHASE.on;
  out[0] = seg(t, at - 0.3, at - 0.08) * (1 - seg(t, at + 0.1, at + 0.3));
  out[1] = seg(t, at - 0.08, at) * (1 - seg(t, at + 0.04, at + 0.16));
}

// ---------------------------------------------------------------- the ring

/** The ring's loudness tau seconds in: up over RING_IN, down over RING_OUT. */
export function ringEnvelope(tau: number): number {
  "worklet";
  if (tau < 0 || tau > RING) return 0;
  return smooth(seg(tau, 0, RING_IN)) * (1 - smooth(seg(tau, RING - RING_OUT, RING)));
}

/** The clock tau seconds into a ring: out = [hammer, left bell, right bell, the clock's rock (all radians about z),
 * its shuffle along x (m), its height off the floor (m)]. All zero when it is not ringing. */
export function ringAt(tau: number, out: number[]): void {
  "worklet";
  const e = ringEnvelope(tau);
  if (e <= 0) {
    out[0] = 0;
    out[1] = 0;
    out[2] = 0;
    out[3] = 0;
    out[4] = 0;
    out[5] = 0;
    return;
  }
  const w = 2 * Math.PI * tau;
  const rad = Math.PI / 180;
  out[0] = e * HAMMER_SWING * rad * Math.sin(w * HAMMER_HZ);
  out[1] = e * BELL_SHAKE * rad * Math.sin(w * BELL_HZ_L + 0.6);
  out[2] = e * BELL_SHAKE * rad * Math.sin(w * BELL_HZ_R + 2.1);
  out[3] = e * ROCK * rad * Math.sin(w * ROCK_HZ);
  out[4] = e * SHUFFLE * Math.sin(w * SHUFFLE_HZ + 1);
  const u = tau / HOP_S;
  const jump = u < 1 ? HOP * 4 * u * (1 - u) : 0;
  out[5] = jump + e * CHATTER * Math.abs(Math.sin(Math.PI * CHATTER_HZ * tau)) * seg(tau, HOP_S * 0.8, HOP_S * 1.4);
}

// ---------------------------------------------------------------- camera

/** tan(half the vertical field of view) that keeps FRAME_W x FRAME_H around the target in a stage of this aspect. */
export function tanHalfFovY(aspect: number): number {
  "worklet";
  return Math.max(FRAME_H / 2, FRAME_W / 2 / aspect) / DIST;
}

/** The camera for a push (0..1) and a finger's orbit (deg): out = [eye xyz, target xyz]. */
export function cameraAt(push: number, orbit: number, out: number[]): void {
  "worklet";
  const az = ((AZ_REST + AZ_PUSH * push + orbit) * Math.PI) / 180;
  const el = ((EL_REST + EL_PUSH * push) * Math.PI) / 180;
  const d = DIST * (1 - ZOOM * push);
  out[0] = TARGET[0] + d * Math.cos(el) * Math.sin(az);
  out[1] = TARGET[1] + d * Math.sin(el);
  out[2] = TARGET[2] + d * Math.cos(el) * Math.cos(az);
  out[3] = TARGET[0];
  out[4] = TARGET[1];
  out[5] = TARGET[2];
}

/** A finger's orbit from its drag (pt) on a screen `width` wide: dragging right turns the clock right (the camera
 * goes left), rubber-banded past ORBIT_MAX. */
export function orbitFromDrag(dx: number, width: number): number {
  "worklet";
  const a = (-dx / width) * ORBIT_SPAN;
  const m = Math.abs(a);
  if (m <= ORBIT_MAX) return a;
  const over = m - ORBIT_MAX;
  return Math.sign(a) * (ORBIT_MAX + (over * 14) / (over + 14));
}
