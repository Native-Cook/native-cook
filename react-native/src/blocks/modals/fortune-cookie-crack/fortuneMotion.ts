/**
 * The fortune cookie's clock. Every value is a closed-form function of the loop time t, so the
 * frame at t = LOOP is the frame at 0, and a finger only moves t (press = play the squeeze, tap
 * when open = play the close). Times are seconds, lengths pt, angles radians.
 */

export const LOOP = 9;

export const SQUEEZE_AT = 1.4;
export const CRACK_AT = 2.0;
/** Where a quick tap starts playing (a shorter squeeze). */
export const TAP_FROM = 1.72;
/** The slip is fully unrolled here; the finger's playback pauses on it. */
export const OPEN_AT = 4.4;
export const CLOSE_AT = 6.4;

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** Ease-out that passes the target by about 5 % and settles back. */
export function easeOutBack(x: number) {
  'worklet';
  const c = 1.1;
  return 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2);
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

export type CookiePose = {
  /** Idle bob (pt) and tilt (rad), only while resting. */
  bob: number;
  tilt: number;
  /** Squash of the squeeze: scale x and y. */
  sx: number;
  sy: number;
  /** Shake before the snap (rad). */
  shake: number;
  /** Crack lines drawn 0…1, and how far the halves have opened 0…1 (overshoots a little). */
  crack: number;
  open: number;
  /** Rolled slip: emerging 0…1, dropped to its place 0…1, unrolled 0…1. */
  emerge: number;
  drop: number;
  unroll: number;
  /** Seconds since the snap (for the crumbs; < 0 before), and the crumbs' fade 0…1. */
  crumbT: number;
  crumbA: number;
  /** 0 closed … 1 open: drives the roll of the sub label. */
  stage: number;
  /** The loop time itself, for the lucky number chips' stagger. */
  t: number;
};

/** The whole loop as a function of t in [0, LOOP). */
export function cookieAt(t: number): CookiePose {
  'worklet';
  const rest = 1 - easeInOut(seg(t, 1.0, SQUEEZE_AT)) + easeInOut(seg(t, 8.4, 8.9));
  const w = Math.min(1, rest);
  const squeeze = easeInOut(seg(t, SQUEEZE_AT, CRACK_AT)) * (1 - easeOut(seg(t, CRACK_AT, 2.25)));
  const shakeEnv = Math.sin(Math.PI * seg(t, 1.72, CRACK_AT));
  const crack = easeOut(seg(t, CRACK_AT, 2.45)) * (1 - easeInOut(seg(t, 8.0, 8.4)));
  // A hairline gap as the crack runs, then the snap: halves swing apart and settle.
  const pre = 0.06 * easeOut(seg(t, 2.1, 2.5));
  const open = (pre + (1 - pre) * easeOutBack(seg(t, 2.5, 3.2))) * (1 - easeInOut(seg(t, 7.4, 8.0)));
  const emerge = easeOut(seg(t, 3.15, 3.5)) * (1 - easeInOut(seg(t, 7.25, 7.5)));
  const drop = easeInOut(seg(t, 3.45, 3.85)) * (1 - easeInOut(seg(t, 6.95, 7.35)));
  const unroll = easeInOut(seg(t, 3.8, OPEN_AT)) * (1 - easeInOut(seg(t, CLOSE_AT, 7.0)));
  const stage = easeInOut(seg(t, 3.3, 3.7)) * (1 - easeInOut(seg(t, 6.6, 7.0)));
  return {
    bob: 3 * Math.sin((2 * Math.PI * 4 * t) / LOOP) * w,
    tilt: 0.014 * Math.sin((2 * Math.PI * 2 * t) / LOOP + 0.6) * w,
    sx: 1 + 0.05 * squeeze,
    sy: 1 - 0.065 * squeeze,
    shake: 0.022 * shakeEnv * Math.sin((2 * Math.PI * 81 * t) / LOOP),
    crack,
    open,
    emerge,
    drop,
    unroll,
    crumbT: t >= 2.5 && t < 5 ? t - 2.5 : -1,
    crumbA: 1 - seg(t, 4.2, 4.8),
    stage,
    t,
  };
}

/** Lucky number chip i: 0 hidden … 1 shown (with a pop). */
export function chipAt(t: number, i: number) {
  'worklet';
  const a = 4.05 + 0.06 * i;
  return easeOutBack(seg(t, a, a + 0.36)) * (1 - easeInOut(seg(t, CLOSE_AT, CLOSE_AT + 0.3)));
}

export const CRUMBS = 26;
const GRAVITY = 1100;

/**
 * Crumb i, tau seconds after the snap: where it is, in cookie-local coordinates, with `floor` the
 * plate's surface (local y). Crumbs leave from the crack, fly out and land on the plate.
 */
export function crumbAt(i: number, tau: number, floor: number) {
  'worklet';
  const sx = (hash(i * 3.1) - 0.5) * 14;
  const sy = -30 + hash(i * 5.7) * 90;
  const side = hash(i * 1.3) < 0.5 ? -1 : 1;
  const vx = side * (40 + 160 * hash(i * 7.9));
  const vy = -120 - 260 * hash(i * 2.3);
  const delay = 0.08 * hash(i * 4.4);
  const tt = Math.max(0, tau - delay);
  let x = sx + vx * tt;
  let y = sy + vy * tt + 0.5 * GRAVITY * tt * tt;
  const land = floor - 4 + 12 * hash(i * 6.6);
  if (y > land) {
    // Time it reached the plate: from there it slides a little and stops.
    const hit = (-vy + Math.sqrt(vy * vy + 2 * GRAVITY * (land - sy))) / GRAVITY;
    x = sx + vx * hit + vx * 0.06 * (1 - Math.exp(-(tt - hit) * 8));
    y = land;
  }
  const r = 1.1 + 2.1 * hash(i * 8.8);
  return { x, y, r, on: tau >= delay ? 1 : 0 };
}
