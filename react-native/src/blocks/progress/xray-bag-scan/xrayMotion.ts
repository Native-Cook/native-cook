/*
 Every moving value of the bag scan as a pure function of the loop time `t` (0 ..< LOOP) or of the
 belt travel `d`. The belt carries every bag the same distance: bag k sits at `REST_X + d − SPACING·k`,
 so one loop moves the belt exactly one bag spacing (16 slats) and frame P equals frame 0.
 Helpers are declared above their callers (worklets).
 */

export const LOOP = 9;

// Layout (units of the 393-wide canvas).
export const REST_X = 57;
export const CENTRE_X = 196.5;
export const SPACING = 400;
export const LEG1 = CENTRE_X - REST_X;
export const PITCH = 25;
export const WIN_L = 126.5;
export const WIN_R = 266.5;
export const WIN_T = 332;
export const WIN_B = 556;
export const BELT_TOP = 528;
export const BELT_Y = 542;
export const SKIRT_TOP = 552;
export const SKIRT_BOTTOM = 580;
export const BAG_W = 98;
export const BAG_H = 184;

export const GO1 = 1.0;
export const STOP1 = 3.4;
export const GO2 = 5.6;
export const STOP2 = 8.2;
export const CLEAR_TIME = 5.2;
const STATUS_TIMES = [1.0, 3.5, 5.2, 7.6];
const BOX_TIMES = [3.8, 4.1, 4.4];

export const STATUSES = ['Ready to scan', 'Scanning…', 'Checking contents', 'Bag cleared'];

export function clamp(x: number, a = 0, b = 1) {
  'worklet';
  return Math.min(Math.max(x, a), b);
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a));
}

export function mod(x: number, m: number) {
  'worklet';
  const r = x % m;
  return r < 0 ? r + m : r;
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInOutSine(x: number) {
  'worklet';
  return -(Math.cos(Math.PI * x) - 1) / 2;
}

export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const u = clamp((x - a) / (b - a));
  return u * u * (3 - 2 * u);
}

export function inverseEaseInOutCubic(y: number) {
  'worklet';
  const v = clamp(y);
  return v < 0.5 ? Math.cbrt(v / 4) : 1 - Math.cbrt(2 * (1 - v)) / 2;
}

/** Under-damped spring step 0 → 1 (ζ 0.55, ω 14), 0 before τ = 0. Peaks about 12 % over. */
export function pop(tau: number) {
  'worklet';
  if (tau <= 0) return 0;
  const z = 0.55;
  const w = 14;
  const wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * tau) * (Math.cos(wd * tau) + ((z * w) / wd) * Math.sin(wd * tau));
}

/** Belt travel at loop time t: 0 → LEG1 (into the window), dwell, → SPACING (out, next bag in). */
export function belt(t: number) {
  'worklet';
  return LEG1 * easeInOutCubic(seg(t, GO1, STOP1)) + (SPACING - LEG1) * easeInOutCubic(seg(t, GO2, STOP2));
}

/** The loop time whose belt travel is `d` (0 … SPACING), for a release after a scrub. */
export function timeForBelt(d: number) {
  'worklet';
  if (d <= 0) return GO1;
  if (d < LEG1) return GO1 + (STOP1 - GO1) * inverseEaseInOutCubic(d / LEG1);
  if (d < LEG1 + 0.5) return STOP1;
  return GO2 + (STOP2 - GO2) * inverseEaseInOutCubic((d - LEG1) / (SPACING - LEG1));
}

/** One damped rock of the bag after the belt starts or stops; 0 outside 0 ..< 0.8 s. */
export function rock(tau: number) {
  'worklet';
  if (tau <= 0 || tau >= 0.8) return 0;
  return Math.sin(11 * tau) * Math.exp(-4 * tau) * (1 - smoothstep(0.5, 0.8, tau));
}

/** Lean of the bags in degrees (clockwise positive). */
export function leanAt(t: number) {
  'worklet';
  return 2.6 * (-rock(t - GO1) + rock(t - STOP1) - rock(t - GO2) + rock(t - STOP2));
}

export function scanning(t: number) {
  'worklet';
  return easeInOutCubic(seg(t, GO1, GO1 + 0.2)) * (1 - easeInOutCubic(seg(t, CLEAR_TIME, CLEAR_TIME + 0.2)));
}

export function beacon(t: number) {
  'worklet';
  return scanning(t) * (0.55 + 0.45 * Math.sin((2 * Math.PI * 18 * t) / LOOP));
}

export function cleared(t: number) {
  'worklet';
  return easeInOutCubic(seg(t, CLEAR_TIME, CLEAR_TIME + 0.25)) * (1 - easeInOutCubic(seg(t, 7.6, 8.2)));
}

export function sweepY(t: number) {
  'worklet';
  return WIN_T + (WIN_B - WIN_T) * easeInOutSine(seg(t, 3.5, 4.9));
}

export function sweepAlpha(t: number) {
  'worklet';
  return Math.sin(Math.PI * seg(t, 3.45, 4.95));
}

/** Glow of a window edge at `x`: 0.35 at rest, up to 1 where a bag crosses it. */
export function curtain(x: number, bagX: number) {
  'worklet';
  const left = bagX - BAG_W / 2;
  const right = bagX + BAG_W / 2;
  return 0.35 + 0.65 * clamp(Math.min(x - left, right - x) / 14);
}

/** Detection boxes: [scale, alpha] per item, flattened. While scrubbing they follow the item's place. */
export function boxes(t: number, bagX: number, scrubbing: boolean, items: number[]) {
  'worklet';
  const out: number[] = [];
  for (let i = 0; i < items.length; i++) {
    if (scrubbing) {
      const x = bagX + items[i];
      const a = clamp((x - WIN_L - 12) / 18) * clamp((WIN_R - 12 - x) / 18);
      out.push(0.85 + 0.15 * a, a);
    } else {
      const tau = t - BOX_TIMES[i];
      const fade = 1 - easeInOutCubic(seg(t, 5.9, 6.3));
      out.push(0.6 + 0.4 * pop(tau), clamp(tau / 0.12) * fade);
    }
  }
  return out;
}

/** Status stage 0 … 4 (4 ≡ 0), each step a 0.25 s ease. */
export function stage(t: number) {
  'worklet';
  let s = 0;
  for (let i = 0; i < STATUS_TIMES.length; i++) s += easeOutCubic(seg(t, STATUS_TIMES[i], STATUS_TIMES[i] + 0.25));
  return s;
}

export function fill(t: number) {
  'worklet';
  const up =
    0.333 * easeInOutCubic(seg(t, GO1, STOP1)) +
    0.334 * easeInOutCubic(seg(t, STOP1, CLEAR_TIME)) +
    0.333 * easeOutCubic(seg(t, CLEAR_TIME, CLEAR_TIME + 0.4));
  return up * (1 - easeInOutCubic(seg(t, 7.6, 8.2)));
}

export function checkScale(t: number) {
  'worklet';
  return pop(t - CLEAR_TIME) * (1 - easeInOutCubic(seg(t, 7.6, 7.85)));
}

export function checkStroke(t: number) {
  'worklet';
  return easeOutCubic(seg(t, CLEAR_TIME + 0.08, CLEAR_TIME + 0.4)) * (1 - easeInOutCubic(seg(t, 7.6, 7.85)));
}

/** Spinner angle (radians), 4 turns a loop. */
export function spin(t: number) {
  'worklet';
  return (2 * Math.PI * 4 * t) / LOOP;
}

// MARK: Touch (an immutable record kept in a shared value)

export type Touch = {
  touching: boolean;
  started: boolean;
  base: number;
  baseClock: number;
  startD: number;
  liveD: number;
  offset: number;
  offsetClock: number;
  velocity: number;
  leanAtRelease: number;
  wasInside: boolean;
};

export function initialTouch(): Touch {
  'worklet';
  return {
    touching: false,
    started: false,
    base: 0,
    baseClock: 0,
    startD: 0,
    liveD: 0,
    offset: 0,
    offsetClock: -10,
    velocity: 0,
    leanAtRelease: 0,
    wasInside: false,
  };
}

export function loopTime(s: Touch, clock: number, autoPlay: boolean) {
  'worklet';
  if (s.touching) return timeForBelt(mod(s.liveD, SPACING));
  if (!autoPlay && !s.started) return 0;
  const raw = s.base + Math.max(0, clock - s.baseClock);
  return mod(autoPlay ? raw : Math.min(raw, LOOP), LOOP);
}

export function beltAt(s: Touch, clock: number, autoPlay: boolean) {
  'worklet';
  if (s.touching) return s.liveD < 0 ? s.liveD : mod(s.liveD, SPACING);
  const tau = Math.max(0, clock - s.offsetClock);
  return belt(loopTime(s, clock, autoPlay)) + s.offset * (1 + 10 * tau) * Math.exp(-10 * tau);
}

export function leanOf(s: Touch, clock: number, t: number) {
  'worklet';
  if (s.touching) return clamp(-s.velocity / 260, -1, 1) * 4;
  const tau = Math.max(0, clock - s.offsetClock);
  return leanAt(t) + s.leanAtRelease * Math.exp(-5 * tau) * Math.cos(11 * tau);
}

export function beginTouch(s: Touch, clock: number, autoPlay: boolean): Touch {
  'worklet';
  if (s.touching) return s;
  const d = beltAt(s, clock, autoPlay);
  return { ...s, startD: d, liveD: d, velocity: 0, wasInside: false, touching: true };
}

/** Moves the belt with the finger; `hit` is true when the bag has just come fully into the window. */
export function moveTouch(s: Touch, dx: number, velocityX: number): { next: Touch; hit: boolean } {
  'worklet';
  const raw = s.startD + dx;
  const liveD = raw < 0 ? -(-raw) / (1 + -raw / 120) : raw;
  const d = liveD < 0 ? liveD : mod(liveD, SPACING);
  const inside = Math.abs(d - LEG1) < 16;
  return { next: { ...s, liveD, velocity: velocityX, wasInside: inside }, hit: inside && !s.wasInside };
}

export function endTouch(s: Touch, clock: number): Touch {
  'worklet';
  if (!s.touching) return s;
  const d = s.liveD < 0 ? s.liveD : mod(s.liveD, SPACING);
  const t = timeForBelt(d);
  return {
    ...s,
    leanAtRelease: clamp(-s.velocity / 260, -1, 1) * 4,
    touching: false,
    started: true,
    base: t,
    baseClock: clock,
    offset: d - belt(t),
    offsetClock: clock,
  };
}

/** "Scan again": the pass starts over from the rest frame. */
export function restartTouch(s: Touch, clock: number): Touch {
  'worklet';
  return { ...s, touching: false, started: true, base: 0, baseClock: clock, offset: 0, leanAtRelease: 0 };
}

/** Clock time after which nothing moves any more (outside autoPlay). */
export function restsAfter(s: Touch) {
  'worklet';
  return s.started ? s.baseClock + (LOOP - s.base) + 0.3 : 0;
}
