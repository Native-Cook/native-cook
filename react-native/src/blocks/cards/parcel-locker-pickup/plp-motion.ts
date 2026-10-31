// Parcel Locker Pickup: timings, springs and the autoplay script.
// Every beat is a function of the time since an event (open, take, done), so the script and the
// finger share the same curves, and one loop cut from a recording repeats exactly.

export const LOOP = 10;

export const OPEN = {
  digit0: 0.15, // the code's digits light one by one
  digitGap: 0.12,
  latch: 0.72, // the latch lets go: the LED turns green, the door kicks out
  swing: 0.95, // then it swings open on its hinge
  angle: 104, // degrees, just past square so its inside shows
  flicker: [1.05, 1.6], // the compartment's light flickers on
};

export const TAKE = {
  lift: [0, 0.32], // the parcel is lifted toward you
  fly: [0.32, 0.92], // and flies down into the sheet
  slam: 0.62, // the door swings shut behind it
  receipt: 0.45, // the sheet becomes the receipt, in time to catch the parcel
};

export const DONE = { fold: [0, 0.5] };

/** Autoplay events, seconds into the loop. */
export const S = { press: [1.2, 1.4], open: 1.4, take: 4.0, done: 8.4, rest: 9.2 } as const;

export const SPRING = {
  swing: { duration: 0.95, ratio: 0.7 },
  kick: { duration: 0.22, ratio: 0.35 },
  slam: { duration: 0.55, ratio: 0.42 },
  receipt: { duration: 0.6, ratio: 0.86 },
  drag: { duration: 0.18, ratio: 0.9 },
};

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

export function smooth(a: number, b: number, x: number) {
  'worklet';
  const t = Math.min(Math.max((x - a) / (b - a), 0), 1);
  return t * t * (3 - 2 * t);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function springStep(t: number, duration: number, ratio: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / duration;
  if (ratio >= 1) return 1 - Math.exp(-w * t) * (1 + w * t);
  const wd = w * Math.sqrt(1 - ratio * ratio);
  return 1 - Math.exp(-ratio * w * t) * (Math.cos(wd * t) + ((ratio * w) / wd) * Math.sin(wd * t));
}

/** Impulse response of a spring (starts at 0, swings out and dies away), peak about 1. */
export function springKick(t: number, duration: number, ratio: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / duration;
  const wd = w * Math.sqrt(1 - ratio * ratio);
  return Math.exp(-ratio * w * t) * Math.sin(wd * t) * 1.6;
}

export function springTo(x: number, v: number, target: number, duration: number, ratio: number, h: number) {
  'worklet';
  const w = (2 * Math.PI) / duration;
  const nv = v + (w * w * (target - x) - 2 * ratio * w * v) * h;
  return [x + nv * h, nv];
}

/** How many of the code's digits are lit, 0..4 (fractional while one lights). */
export function digitsAt(to: number) {
  'worklet';
  if (to < 0) return 0;
  return Math.min(4, Math.max(0, (to - OPEN.digit0) / OPEN.digitGap + 1));
}

/** The compartment's light: off, a flicker, on. */
export function lightAt(to: number) {
  'worklet';
  const [a, b] = OPEN.flicker;
  if (to < a) return 0;
  if (to >= b) return 1;
  const u = (to - a) / (b - a);
  // a tube striking: on, off, a dim stutter, on
  if (u < 0.12) return 1;
  if (u < 0.28) return 0.08;
  if (u < 0.36) return 0.7;
  if (u < 0.5) return 0.15;
  if (u < 0.58) return 0.9;
  if (u < 0.66) return 0.4;
  return 1;
}

/** The door's angle (degrees) after the open event, before anything is taken. */
export function openAngle(to: number) {
  'worklet';
  if (to < OPEN.latch) return 0;
  const kick = 4 * springKick(to - OPEN.latch, SPRING.kick.duration, SPRING.kick.ratio);
  const swing = OPEN.angle * springStep(to - OPEN.swing, SPRING.swing.duration, SPRING.swing.ratio);
  return Math.max(0, kick * (1 - seg(to, OPEN.swing, OPEN.swing + 0.2)) + swing);
}

/** The door swinging shut after the parcel is taken: it hits the frame and bounces off it. */
export function slamAngle(tk: number, from: number) {
  'worklet';
  if (tk < TAKE.slam) return from;
  return Math.abs(from * (1 - springStep(tk - TAKE.slam, SPRING.slam.duration, SPRING.slam.ratio)));
}

export type Pose = {
  press: number;
  digits: number;
  led: number; // 0 amber blink, 1 green, 2 off
  angle: number;
  light: number;
  parcel: number; // 0 in the compartment, 1 in the sheet
  sheet: number; // 0 pickup card, 1 receipt
  takeAge: number;
  fold: number;
};

/** The whole autoplay pose at loop time t. */
export function script(t: number): Pose {
  'worklet';
  if (t >= S.rest || t < S.press[0]) {
    return { press: 0, digits: 0, led: 0, angle: 0, light: 0, parcel: 0, sheet: 0, takeAge: -10, fold: 0 };
  }
  const press = Math.sin(Math.PI * seg(t, S.press[0], S.press[1]));
  const to = t - S.open;
  const tk = t - S.take;
  const td = t - S.done;
  const opened = openAngle(to);
  const angle = tk >= 0 ? slamAngle(tk, opened) : opened;
  const closed = tk > TAKE.slam + 0.25 && angle < 3;
  const light = tk > TAKE.slam + 0.15 ? lightAt(to) * (1 - smooth(TAKE.slam + 0.15, TAKE.slam + 0.4, tk)) : lightAt(to);
  const led = to < OPEN.latch ? 0 : closed && tk > TAKE.slam + 0.7 ? 2 : 1;
  const parcel = tk < 0 ? 0 : easeInOut(seg(tk, TAKE.lift[0], TAKE.fly[1]));
  const sheet = springStep(tk - TAKE.receipt, SPRING.receipt.duration, SPRING.receipt.ratio) * (1 - easeInOut(seg(td, DONE.fold[0], DONE.fold[1])));
  const digits = digitsAt(to) * (1 - seg(td, 0, 0.3));
  const fold = easeInOut(seg(td, DONE.fold[0], DONE.fold[1]));
  return { press, digits, led, angle, light, parcel, sheet, takeAge: tk, fold };
}
