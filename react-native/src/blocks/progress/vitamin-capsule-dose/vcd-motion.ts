// Vitamin Capsule Dose — the loop and its helpers. Helpers first: a worklet captures only what is
// declared above it.
//
// The hero is one glass object that morphs between three forms (0 the capsule, 1 the tablet, 2 a drop
// of the liquid medicine): from form a to form b by k 0 … 1, turning once round as it changes. The
// capsule opens: its white cap pulled off along the axis (sep, units) and twisted (twist), the blue cup
// tipped (tilt) to pour its pearls onto the sheet, where they are rigid balls on the GPU. Holding Log
// Medication fills the button (hold); then the pearls stream into the next open check, or, for a
// closed form, the object dissolves into beads that do (take, reform). A filled check undoes.

import { DEFAULT_MED, MEDS } from './vcd-theme';

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}
/** a spring-like settle: overshoots a little, then rests at 1 */
export function backOut(x: number) {
  'worklet';
  const c = 1.9;
  const u = x - 1;
  return 1 + (c + 1) * u * u * u + c * u * u;
}

export const LOOP = 10;
export const MORPH = 0.9;
export const HOLD = 1.2;
export const TAKE = 1.25;
export const REFORM = 0.6;

/** the air's idle motion: whole cycles per loop */
export function idle(t: number) {
  'worklet';
  const a = (2 * Math.PI * t) / LOOP;
  return { yaw: 0.22 * Math.sin(a), pitch: 0.1 * Math.sin(2 * a + 0.7), bob: 2.2 * Math.sin(2 * a) };
}

/* ---------------------------------------------------------------- the loop (10 s)
 0.35 a finger takes the white cap and pulls it along the capsule: it twists and pops off at 1.15
 1.2  the blue cup tips and pours its pearls, which bounce and settle on the sheet
 3.9  a finger runs through them           5.0 Log Medication held (the button fills)
 6.2  logged: the pearls stream into the 3:00 PM check   7.4 the capsule closes   8.6 the check tapped
 10 = 0 */
export const T = {
  pull: [0.45, 1.15],
  pop: [1.15, 1.5],
  pour: [1.2, 1.7],
  spill: 1.3,
  stir: [3.9, 4.6],
  hold: 5.0,
  close: [7.4, 8.1],
  undo: 8.6,
} as const;
const TAKEN_AT = T.hold + HOLD;
/** the capsule's tilt at rest (the pin's 45°) and while it pours */
export const TILT = 0.785;
export const POUR_TILT = -0.4;
export const SEP_OFF = 1.5;

export type Frame = {
  a: number;
  b: number;
  k: number;
  med: number;
  hold: number;
  take: number;
  reform: number;
  taken: number; // Lumivit's 3:00 PM check, 0 … 1
  yaw: number;
  pitch: number;
  bob: number;
  sep: number;
  twist: number;
  tilt: number;
  hidden: number;
  mode: number; // the pearls: 0 none, 2 live, 4 to the check (1 = spawn is sent by the ticker on the frame it crosses T.spill)
  finger: number;
  fingerAt: number; // 0 the cap, 1 Log Medication, 2 a check, 3 through the pearls
  fingerX: number; // 0 … 1 along the stir
  press: number;
};

export function frameAt(t: number): Frame {
  'worklet';
  const shape = MEDS[DEFAULT_MED].shape;
  const hold = t >= T.hold && t < TAKEN_AT ? seg(t, T.hold, TAKEN_AT) : 0;
  const taken = easeOut(seg(t, TAKEN_AT + 0.95, TAKEN_AT + 1.15)) * (1 - easeInOut(seg(t, T.undo + 0.05, T.undo + 0.4)));
  const closing = easeInOut(seg(t, T.close[0], T.close[1]));
  let sep = 0.55 * easeInOut(seg(t, T.pull[0], T.pull[1]));
  if (t >= T.pop[0]) sep = 0.55 + (SEP_OFF - 0.55) * backOut(seg(t, T.pop[0], T.pop[1]));
  sep *= 1 - closing;
  const twist = 1.1 * easeOut(seg(t, T.pull[0], T.pop[1])) * (1 - closing);
  const tilt = TILT + (POUR_TILT - TILT) * easeInOut(seg(t, T.pour[0], T.pour[1])) * (1 - easeInOut(seg(t, T.close[0] - 0.2, T.close[0] + 0.4)));
  const hidden = t >= T.spill && t < T.close[1] ? 1 : 0;
  const mode = t < T.spill || t >= T.close[0] ? 0 : t < TAKEN_AT ? 2 : 4;
  const id = idle(t);

  // the scripted finger
  let finger = 0;
  let fingerAt = 0;
  let fingerX = 0;
  let press = 0;
  const pullF = easeOut(seg(t, T.pull[0] - 0.18, T.pull[0] - 0.02)) * (1 - easeOut(seg(t, T.pull[1], T.pull[1] + 0.16)));
  if (pullF > finger) {
    finger = pullF;
    fingerAt = 0;
    fingerX = easeInOut(seg(t, T.pull[0], T.pull[1]));
    press = pullF;
  }
  const stir = easeOut(seg(t, T.stir[0] - 0.18, T.stir[0] - 0.02)) * (1 - easeOut(seg(t, T.stir[1], T.stir[1] + 0.16)));
  if (stir > finger) {
    finger = stir;
    fingerAt = 3;
    fingerX = easeInOut(seg(t, T.stir[0], T.stir[1]));
    press = stir;
  }
  const h = easeOut(seg(t, T.hold - 0.2, T.hold - 0.03)) * (1 - easeOut(seg(t, TAKEN_AT, TAKEN_AT + 0.18)));
  if (h > finger) {
    finger = h;
    fingerAt = 1;
    press = h;
  }
  const u = easeOut(seg(t, T.undo - 0.2, T.undo - 0.04)) * (1 - easeOut(seg(t, T.undo + 0.14, T.undo + 0.3)));
  if (u > finger) {
    finger = u;
    fingerAt = 2;
    fingerX = 1;
    press = easeOut(seg(t, T.undo - 0.04, T.undo + 0.05)) * (1 - easeOut(seg(t, T.undo + 0.1, T.undo + 0.22)));
  }
  return { a: shape, b: shape, k: 0, med: DEFAULT_MED, hold, take: 0, reform: 1, taken, yaw: id.yaw * (1 - hidden * 0.6), pitch: id.pitch - hold * 0.06, bob: id.bob, sep, twist, tilt, hidden, mode, finger, fingerAt, fingerX, press };
}
