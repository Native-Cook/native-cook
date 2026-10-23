/**
 * The block's clock, ported from VinylSwapScratchMotion.swift: every value on screen is a function
 * of time, in closed form. Angles are degrees, clockwise positive; times are seconds.
 */

export const LOOP = 10;
/** About 33⅓ rpm; the real speed is rounded so the platter turns a whole number of times a loop. */
const NOMINAL_SPEED = 200;

// The scripted scratch.
export const STOP_AT = 1.5;
export const STOP_FOR = 0.12;
const STROKES = [
  [1.62, 0.6, 55],
  [2.22, 0.5, 75],
  [2.72, 0.44, 95],
];
export const RELEASE_AT = 3.2;

/** The two record changes: A to B, then B back to A ([start, from, to]). */
const SWAPS = [
  [4.6, 0, 1],
  [7.7, 1, 0],
];
export const SWAP_LENGTH = 1.8;

/** Waveform bars that pass the playhead per turn of the platter. */
export const BARS_PER_TURN = 18;
/** Every ninth bar is a kick (twice a turn). */
const KICK_EVERY = 9;

// The motor: a spring of ω 12, ζ 0.45 on the platter's speed (about 20 % overshoot).
const MOTOR_A = 0.45 * 12;
const MOTOR_B = 12 * Math.sqrt(1 - 0.45 * 0.45);

/** e^(−at)(cos bt + a/b sin bt): 1 at 0, decaying to 0 with an overshoot. */
export function motorResponse(t: number) {
  'worklet';
  return Math.exp(-MOTOR_A * t) * (Math.cos(MOTOR_B * t) + (MOTOR_A / MOTOR_B) * Math.sin(MOTOR_B * t));
}

function motorF(x: number) {
  'worklet';
  const a = MOTOR_A;
  const b = MOTOR_B;
  return (Math.exp(-a * x) * ((b - (a * a) / b) * Math.sin(b * x) - 2 * a * Math.cos(b * x))) / (a * a + b * b);
}

/** ∫₀ᵗ motorResponse. */
export function motorIntegral(t: number) {
  'worklet';
  return motorF(t) - motorF(0);
}

/** The motor pulling the platter from speed v0 to target: [angle travelled, speed] after t. */
export function approach(v0: number, target: number, t: number): [number, number] {
  'worklet';
  return [target * t + (v0 - target) * motorIntegral(t), target + (v0 - target) * motorResponse(t)];
}

/** The platter braking from v0 to rest over duration (speed falls as (1 − u)²). */
export function brake(v0: number, duration: number, t: number): [number, number] {
  'worklet';
  const u = Math.min(Math.max(t / duration, 0), 1);
  return [(v0 * duration * (1 - Math.pow(1 - u, 3))) / 3, v0 * (1 - u) * (1 - u)];
}

/** The loop's platter, in units of the motor speed: [∫ speed, speed]. */
export function shape(t: number): [number, number] {
  'worklet';
  if (t < STOP_AT) return [t, 1];
  if (t < RELEASE_AT) {
    const b = brake(1, STOP_FOR, t - STOP_AT);
    return [STOP_AT + b[0], b[1]];
  }
  const r = approach(0, 1, t - RELEASE_AT);
  return [STOP_AT + STOP_FOR / 3 + r[0], r[1]];
}

/** Whole turns of the platter per loop. */
export const TURNS = Math.round((NOMINAL_SPEED * shape(LOOP)[0]) / 360);
/** The motor speed, in degrees a second. */
export const OMEGA = (TURNS * 360) / shape(LOOP)[0];
/** Bars before the waveform repeats: one loop's worth. */
export const PERIOD = BARS_PER_TURN * TURNS;

/** The scripted hand: three back-and-forth strokes, pulling the record back and pushing it on. */
export function scratch(t: number): [number, number] {
  'worklet';
  for (let i = 0; i < STROKES.length; i++) {
    const s = STROKES[i];
    if (t >= s[0] && t < s[0] + s[1]) {
      const u = (t - s[0]) / s[1];
      return [(-s[2] * (1 - Math.cos(2 * Math.PI * u))) / 2, ((-s[2] * Math.PI) / s[1]) * Math.sin(2 * Math.PI * u)];
    }
  }
  return [0, 0];
}

// Curves.

export function clamp01(x: number) {
  'worklet';
  return Math.min(Math.max(x, 0), 1);
}
export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = clamp01((x - a) / (b - a));
  return t * t * (3 - 2 * t);
}
function easeInOut(x: number) {
  'worklet';
  const t = clamp01(x);
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}
function easeIn(x: number) {
  'worklet';
  return Math.pow(clamp01(x), 3);
}
function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(x), 3);
}

/** A spring step from 0 to 1 (ω 11, ζ 0.62): the new record lands with a small overshoot. */
function landing(t: number) {
  'worklet';
  const w = 11;
  const z = 0.62;
  const a = z * w;
  const b = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-a * t) * (Math.cos(b * t) + (a / b) * Math.sin(b * t));
}

/** What the screen shows at a moment. */
export type Pose = {
  /** The platter's angle and speed (degrees, degrees a second). */
  angle: number;
  speed: number;
  /** Each record's place: 0 on the platter, −1 gone off the left edge, 1 waiting off the right. */
  discX: number[];
  discAlpha: number[];
  /** 0 with the stylus in the groove, 1 swung out beside the record. */
  arm: number;
  /** 0 down, 1 raised (a bigger, softer shadow). */
  lift: number;
  /** Colours: 0 album A, 1 album B. */
  mix: number;
  waveMix: number;
  /** The waveform's loudness: low while the stylus is out of the groove. */
  needle: number;
  titleAlpha: number[];
  titleX: number[];
  /** The scripted finger on the record: opacity, scale and how far it has turned the record. */
  fingerAlpha: number;
  fingerScale: number;
  fingerTurn: number;
  /** The pills pressed by the script: pause, previous, next. */
  press: number[];
  /** The time used for flickers. */
  clock: number;
};

export function restPose(album: number): Pose {
  'worklet';
  const a = album === 0;
  return {
    angle: 0,
    speed: 0,
    discX: a ? [0, 1] : [1, 0],
    discAlpha: a ? [1, 0] : [0, 1],
    arm: 0,
    lift: 0,
    mix: album,
    waveMix: album,
    needle: 1,
    titleAlpha: a ? [1, 0] : [0, 1],
    titleX: [0, 0],
    fingerAlpha: 0,
    fingerScale: 1,
    fingerTurn: 0,
    press: [0, 0, 0],
    clock: 0,
  };
}

/** A record change e seconds in. Reduced motion: a 0.3 s cross-fade, nothing moves. */
export function swapInto(p: Pose, e: number, from: number, to: number, still: boolean) {
  'worklet';
  const dir = to > from ? 1 : -1;
  const toward = (m: number) => from + dir * m;
  if (still) {
    const a = smoothstep(0, 0.3, e);
    p.discX[from] = 0;
    p.discX[to] = 0;
    p.discAlpha[from] = 1 - a;
    p.discAlpha[to] = a;
    p.mix = toward(a);
    p.waveMix = toward(a);
    p.titleAlpha[from] = 1 - a;
    p.titleAlpha[to] = a;
    p.titleX = [0, 0];
    return;
  }
  p.arm = e < 1.3 ? easeInOut(e / 0.4) : 1 - easeOut((e - 1.3) / 0.5);
  p.lift = smoothstep(0, 0.25, e) * (1 - smoothstep(1.55, 1.8, e));
  p.needle = clamp01(1 - smoothstep(0.05, 0.3, e) + smoothstep(1.6, 1.8, e));
  p.discX[from] = e < 0.3 ? 0 : -easeIn((e - 0.3) / 0.5);
  p.discX[to] = e < 0.6 ? 1 : e >= 1.5 ? 0 : 1 - landing(e - 0.6);
  p.discAlpha[from] = 1;
  p.discAlpha[to] = 1;
  p.mix = toward(smoothstep(0.4, 1.4, e));
  p.waveMix = toward(smoothstep(0.6, 1.3, e));
  const out = smoothstep(0.45, 0.75, e);
  p.titleAlpha[from] = 1 - out;
  p.titleX[from] = -14 * out;
  p.titleAlpha[to] = smoothstep(0.7, 1.05, e);
  p.titleX[to] = 14 * (1 - easeOut((e - 0.7) / 0.35));
}

/** A scripted press of a pill around time at. */
function bump(t: number, at: number) {
  'worklet';
  return smoothstep(at - 0.18, at - 0.05, t) * (1 - smoothstep(at + 0.05, at + 0.25, t));
}

/** The loop at time t (0 ≤ t < LOOP). */
export function loopPose(t: number): Pose {
  'worklet';
  let p = restPose(0);
  const s = shape(t);
  const k = scratch(t);
  const a = SWAPS[0];
  const b = SWAPS[1];
  if (t >= a[0] && t < a[0] + SWAP_LENGTH) {
    swapInto(p, t - a[0], a[1], a[2], false);
  } else if (t >= a[0] + SWAP_LENGTH && t < b[0]) {
    p = restPose(1);
  } else if (t >= b[0] && t < b[0] + SWAP_LENGTH) {
    swapInto(p, t - b[0], b[1], b[2], false);
  }
  p.angle = OMEGA * s[0] + k[0];
  p.speed = OMEGA * s[1] + k[1];
  p.clock = t;
  p.fingerAlpha = smoothstep(1.3, 1.5, t) * (1 - smoothstep(RELEASE_AT, RELEASE_AT + 0.2, t));
  p.fingerScale = 1.25 - 0.25 * smoothstep(1.3, 1.5, t) + 0.15 * smoothstep(RELEASE_AT, RELEASE_AT + 0.2, t);
  p.fingerTurn = k[0];
  p.press = [0, bump(t, b[0]), bump(t, a[0])];
  return p;
}

/** Which record is on the platter (or leaving it) at loop time t, and the change in progress ([start, from, to] or null). */
export function loopState(t: number): { album: number; swap: number[] | null } {
  'worklet';
  for (let i = 0; i < SWAPS.length; i++) {
    const s = SWAPS[i];
    if (t >= s[0] && t < s[0] + SWAP_LENGTH) return { album: s[1], swap: s };
  }
  return { album: t >= SWAPS[0][0] && t < SWAPS[1][0] ? 1 : 0, swap: null };
}

/** A small integer hash (PCG), the same in every stack: 0 to 1. */
export function hash(x: number, salt: number) {
  'worklet';
  let h = (Math.imul(x, 747796405) + Math.imul(salt, 2891336453 | 0) + 1) | 0;
  h = Math.imul((h >>> ((h >>> 28) + 4)) ^ h, 277803737);
  h = (h >>> 22) ^ h;
  return (h >>> 0) / 4294967295;
}

/** Bar k's height (0 to 1) in album's waveform; repeats every PERIOD bars. */
export function bar(album: number, k: number) {
  'worklet';
  const i = ((k % PERIOD) + PERIOD) % PERIOD;
  if (i % KICK_EVERY === 0) return 0.95;
  const x = i / PERIOD;
  const seed = album === 0 ? 0 : 2.1;
  const env = 0.5 + 0.22 * Math.sin(2 * Math.PI * 2 * x + seed) + 0.14 * Math.sin(2 * Math.PI * 5 * x + seed * 1.7);
  const h = env * (0.45 + 0.55 * hash(i, album + 1));
  return Math.min(Math.max(h, 0.1), 0.88);
}

export function isKick(k: number) {
  'worklet';
  return (((k % PERIOD) + PERIOD) % PERIOD) % KICK_EVERY === 0;
}

/** How strongly the record is being pushed: drives the flare, the smear and the rim arcs. */
export function intensity(speed: number) {
  'worklet';
  return clamp01((Math.abs(speed) - 250) / 350);
}
