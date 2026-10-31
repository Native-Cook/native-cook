// Tap Ride Pass: every timing, spring and distance of the block, and the autoplay script.
// The script is a pure function of the loop time, so one period cut from a recording loops exactly.

export const LOOP = 9;

/** Springs as { duration, ratio }: k = (2π / duration)², c = 2ζ√k. */
export const SPRING = {
  follow: { duration: 0.12, ratio: 0.95 }, // the card under the finger: 1:1, only smooths the steps
  home: { duration: 0.5, ratio: 0.8 }, // let go away from the reader, or lifted off after the tap
  dock: { duration: 0.3, ratio: 0.66 }, // the last centimetres: it lies down on the glass with a small overshoot
  assist: { duration: 0.7, ratio: 0.88 }, // a tap sends the card to the reader by itself
  tilt: { duration: 0.5, ratio: 0.7 },
} as const;

export const NEAR = {
  snap: 46, // docks inside this distance (pt between the card's centre and the reader's)
  pull: 150, // the magnet takes over inside this distance
  pullMax: 0.6,
  dwell: 0.55, // seconds on the reader before it's read and the ticket prints
};

/** The reader's ticks (and the haptic ticks under a finger), faster as the card nears. */
export const CADENCE = { idle: 1.2, max: 8.5 };

/** Ticket: 0 inside the slot, PRINTED standing out of the slot, 1 full screen. */
export const TICKET = { printed: 0.36, close: 0.75 };

export const FARE = 2.75;
export const START_BALANCE = 18.4;

/** After the card is read (seconds since the tap): it's lifted away, the ticket prints and rises. */
export const AFTER = { away: 0.43, print: [0.58, 1.33], rise: [1.33, 2.03], roll: [2.23, 2.83] } as const;

/** Autoplay beats, seconds into the loop. */
export const S = {
  lift: [1.2, 1.45],
  approach: [1.45, 2.8],
  pull: 2.8,
  tap: 3.12, // the card is read: flash, chime
  close: [7.05, 7.8],
  rest: 8.3, // from here every value is exactly the resting one
} as const;

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

/** Closed-form step response of a spring released from rest at 0 toward 1. */
export function springStep(t: number, duration: number, ratio: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / duration;
  if (ratio >= 1) return 1 - Math.exp(-w * t) * (1 + w * t);
  const wd = w * Math.sqrt(1 - ratio * ratio);
  return 1 - Math.exp(-ratio * w * t) * (Math.cos(wd * t) + ((ratio * w) / wd) * Math.sin(wd * t));
}

/** One semi-implicit step of a damped spring toward `target`; returns [x, v]. */
export function springTo(x: number, v: number, target: number, duration: number, ratio: number, h: number) {
  'worklet';
  const w = (2 * Math.PI) / duration;
  const nv = v + (w * w * (target - x) - 2 * ratio * w * v) * h;
  return [x + nv * h, nv];
}

export function bez(a: number, b: number, c: number, d: number, u: number) {
  'worklet';
  const k = 1 - u;
  return k * k * k * a + 3 * k * k * u * b + 3 * k * u * u * c + u * u * u * d;
}

/** The ticket after the tap: 0 in the slot → TICKET.printed standing out of it → 1 full screen. */
export function ticketAfter(tt: number) {
  'worklet';
  const print = easeOut(seg(tt, AFTER.print[0], AFTER.print[1])) * TICKET.printed;
  const rise = easeInOut(seg(tt, AFTER.rise[0], AFTER.rise[1])) * (1 - TICKET.printed);
  return print + rise;
}

export function rollAfter(tt: number) {
  'worklet';
  return easeInOut(seg(tt, AFTER.roll[0], AFTER.roll[1]));
}

export type Geo = { hx: number; hy: number; rx: number; ry: number };
export type Pose = { cx: number; cy: number; lift: number; m: number; roll: number; tapAge: number };

/** The whole autoplay pose at loop time t. */
export function script(t: number, g: Geo): Pose {
  'worklet';
  if (t >= S.rest || t < S.lift[0]) return { cx: g.hx, cy: g.hy, lift: 0, m: 0, roll: 0, tapAge: -10 };
  const ax = g.rx + 26;
  const ay = g.ry + 88; // hovering just short of the reader, the magnet about to take it
  let cx = g.hx;
  let cy = g.hy;
  if (t >= S.approach[0] && t < S.pull) {
    const u = easeInOut(seg(t, S.approach[0], S.approach[1]));
    cx = bez(g.hx, g.hx - 40, ax + 60, ax, u);
    cy = bez(g.hy, g.hy - 110, ay + 90, ay, u);
  } else if (t >= S.pull) {
    const k = springStep(t - S.pull, SPRING.dock.duration, SPRING.dock.ratio);
    const dx = ax + (g.rx - ax) * k;
    const dy = ay + (g.ry - ay) * k;
    const back = springStep(t - S.tap - AFTER.away, SPRING.home.duration, SPRING.home.ratio);
    cx = dx + (g.hx - dx) * back;
    cy = dy + (g.hy - dy) * back;
  }
  const lift = easeOut(seg(t, S.lift[0], S.lift[1])) * (1 - easeInOut(seg(t, S.pull, S.pull + 0.3)));
  const tt = t - S.tap;
  const close = easeInOut(seg(t, S.close[0], S.close[1]));
  const m = ticketAfter(tt) * (1 - close);
  const roll = rollAfter(tt) * (1 - seg(t, S.close[1] - 0.05, S.close[1]));
  return { cx, cy, lift, m, roll, tapAge: tt };
}
