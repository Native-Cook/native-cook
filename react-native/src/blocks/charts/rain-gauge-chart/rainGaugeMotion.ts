/**
 * Rain Gauge Chart — data and the autoPlay script. Every value is a closed-form function of the
 * loop time t (seconds); the rain's drops are pure functions of t and a per-drop hash, falling a
 * whole number of times per loop, so the frame at t = LOOP equals the frame at t = 0.
 *
 * Every helper is a module-level named worklet declared above its callers.
 */

export const LOOP = 9;
export const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
export const FULL_DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
/** This week's rainfall per day (mm); a tube holds CAPACITY mm. */
export const MM = [3.2, 0.8, 6.4, 11.6, 7.9, 2.1, 6.6];
export const NOTES = [
  'Drizzle through the morning',
  'A passing shower at 09:40',
  'Steady rain after lunch',
  'Heaviest 15:00 – 17:00',
  'Showers on and off',
  'Light rain at dusk',
  'Overnight rain, easing at 06:00',
];
export const CAPACITY = 15;
export const TUBES = 7;
/** Thursday, the tube the loop lifts. */
export const LOOP_LIFT = 3;

const DRAIN_AT = 1.4;
const FILL_AT = 2.5;
const FILL_STEP = 0.45;
const FILL_FOR = 0.9;
const LIFT_AT = 6.0;
const LOWER_AT = 7.0;

export const DROPS = 170;

export function clamp01(x: number) {
  'worklet';
  return Math.min(Math.max(x, 0), 1);
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOut(x: number) {
  'worklet';
  const t = clamp01(x);
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  const t = clamp01(x);
  return 1 - Math.pow(1 - t, 3);
}

export function hash(n: number) {
  'worklet';
  const x = Math.sin(n * 12.9898 + 78.233) * 43758.5453;
  return x - Math.floor(x);
}

export type RainPose = {
  /** Water in each tube (mm). */
  levels: number[];
  /** Surface tilt of each tube's water (pt at the walls, signed). */
  slosh: number[];
  /** The tube lifted out of the rack and how far (0..1). */
  liftIdx: number;
  lift: number;
  /** Where the rain falls (continuous tube index) and how hard (0..1). */
  cursor: number;
  rain: number;
  /** The time the drops are drawn at (s). */
  rainT: number;
};

export function loopPose(t: number): RainPose {
  'worklet';
  const levels: number[] = [];
  const slosh: number[] = [];
  const cursor = Math.min(TUBES - 1, Math.max(0, (t - FILL_AT - 0.2) / FILL_STEP));
  const rain = seg(t, 2.25, 2.7) * (1 - seg(t, 5.7, 6.2));
  const lift = easeOut(seg(t, LIFT_AT, LIFT_AT + 0.45)) * (1 - easeInOut(seg(t, LOWER_AT, LOWER_AT + 0.4)));
  for (let i = 0; i < TUBES; i++) {
    let level = MM[i];
    if (t >= DRAIN_AT && t < FILL_AT) level = MM[i] * (1 - easeInOut(seg(t, DRAIN_AT + i * 0.04, DRAIN_AT + 0.8 + i * 0.04)));
    else if (t >= FILL_AT) level = MM[i] * easeOut(seg(t, FILL_AT + i * FILL_STEP, FILL_AT + i * FILL_STEP + FILL_FOR));
    // The water rocks while the rain hits it (11 rocks a loop) and when Thursday is lifted.
    const near = Math.exp(-Math.pow((i - cursor) / 0.9, 2)) * rain;
    let tilt = 1.6 * near * Math.sin((2 * Math.PI * 11 * t) / LOOP + i * 1.3);
    if (i === LOOP_LIFT && t > LIFT_AT) {
      const e = t - LIFT_AT;
      tilt += 3.2 * Math.exp(-e * 2.2) * Math.sin(e * 2 * Math.PI * 1.7) * (1 - seg(t, 8.0, 8.6));
    }
    levels.push(level);
    slosh.push(tilt);
  }
  return { levels, slosh, liftIdx: LOOP_LIFT, lift, cursor, rain, rainT: t };
}

export function totalOf(levels: number[]) {
  'worklet';
  let v = 0;
  for (let i = 0; i < TUBES; i++) v += levels[i];
  return v;
}

/** A finger's slosh: a kick that rocks and dies away (seconds since the kick). */
export function kick(e: number, size: number) {
  'worklet';
  if (e < 0 || e > 4) return 0;
  return size * Math.exp(-e * 2.4) * Math.sin(e * 2 * Math.PI * 1.7);
}
