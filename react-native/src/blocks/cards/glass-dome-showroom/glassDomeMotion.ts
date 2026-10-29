/*
 Time and touch model of the showroom. Everything on screen is a function of φ, the carousel's
 position in cars (φ = 2 puts car 2 in the middle; it wraps every COUNT cars), and φ is a function
 of the clock. All functions are worklets; helpers are declared above their callers.
 */

export const LOOP = 10;
export const COUNT = 5;
/** Distance (pt) between two jar centres; a drag of this length moves the carousel one car. */
export const SPACING = 272;
/** Seconds after the last touch before the carousel springs back to the first car. */
export const IDLE = 2;
/** Duration of that return; the loop restarts at its end. */
export const BACK = 1.6;
/** Start of each step A→B, B→C, C→D, D→E, E→A in the loop. */
const STEP_1 = 1.5;
const STEP_2 = 3.2;
const STEP_3 = 5.3;
const STEP_4 = 7.0;
const STEP_5 = 8.7;
const STEP_SETTLE = 0.85;
/** The loop likes car C (index 2) at this time. */
export const HEART_INDEX = 2;
export const HEART_AT = 4.3;

// Layout in the 393-wide design space.
export const CENTRE_X = 196.5;
export const JAR_WIDTH = 260;
export const JAR_HEIGHT = 318;
export const JAR_RADIUS = 64;
export const CAR_WIDTH = 248;
export const HEADLINE_PITCH = 66;
/** The heart's centre in a jar's local space (origin at the floor line under the jar's middle). */
export const HEART_X = JAR_WIDTH / 2 - 24 - 19;
export const HEART_Y = 4 - JAR_HEIGHT + 30 + 19;

export function floorYOf(height: number, safeBottom: number) {
  'worklet';
  return height - safeBottom - 136;
}

/** Rounds halves away from zero, like Swift's `rounded()`. */
export function roundAway(x: number) {
  'worklet';
  return x >= 0 ? Math.floor(x + 0.5) : -Math.floor(-x + 0.5);
}

/**
 Step response of a damped spring (response = period in s), from `from` with `velocity` (units/s);
 exactly `to` after `settle` s.
 */
export function spring(t: number, from: number, to: number, velocity: number, response: number, damping: number, settle: number) {
  'worklet';
  if (t <= 0) return from;
  if (t >= settle) return to;
  const w = (2 * Math.PI) / response;
  const zw = damping * w;
  const wd = w * Math.sqrt(1 - damping * damping);
  const a = from - to;
  const b = (velocity + zw * a) / wd;
  return to + Math.exp(-zw * t) * (a * Math.cos(wd * t) + b * Math.sin(wd * t));
}

export function loopTime(time: number) {
  'worklet';
  return time - Math.floor(time / LOOP) * LOOP;
}

function stepAt(i: number) {
  'worklet';
  if (i === 0) return STEP_1;
  if (i === 1) return STEP_2;
  if (i === 2) return STEP_3;
  if (i === 3) return STEP_4;
  return STEP_5;
}

/** The loop: rest on A, then one car every ~1.9 s, all the way round back to A. */
export function loopPhi(time: number) {
  'worklet';
  const t = loopTime(time);
  let phi = 0;
  for (let i = 0; i < COUNT; i++) {
    const s = stepAt(i);
    if (t >= s) phi = spring(t - s, i, i + 1, 0, 0.55, 0.8, STEP_SETTLE);
  }
  return phi === COUNT ? 0 : phi;
}

/** d wrapped into [-COUNT/2, COUNT/2). */
export function wrap(d: number) {
  'worklet';
  const n = COUNT;
  const m = d + n / 2 - Math.floor((d + n / 2) / n) * n;
  return m - n / 2;
}

export function indexOf(phi: number) {
  'worklet';
  const i = roundAway(phi) % COUNT;
  return i < 0 ? i + COUNT : i;
}

export type Jar = { index: number; d: number; x: number; scale: number };

/** The visible jars for φ, ordered back to front. */
export function jars(phi: number): Jar[] {
  'worklet';
  const out: Jar[] = [];
  for (let i = 0; i < COUNT; i++) {
    const d = wrap(i - phi);
    const a = Math.abs(d);
    const scale = 1 - 0.16 * Math.min(a, 1) - 0.04 * Math.max(a - 1, 0);
    const x = CENTRE_X + d * SPACING;
    if (Math.abs(x - CENTRE_X) > CENTRE_X + (JAR_WIDTH * scale) / 2 + 4) continue;
    out.push({ index: i, d, x, scale });
  }
  out.sort((p, q) => Math.abs(q.d) - Math.abs(p.d));
  return out;
}

/** Scale of a heart popped `t` s ago. */
export function heartScale(t: number) {
  'worklet';
  if (t < 0 || t > 1.2) return 1;
  return 1 + 0.6 * Math.exp(-7 * t) * Math.sin(11 * t);
}

export type Touch = {
  touching: boolean;
  live: number;
  /** Drag speed in cars per second while the finger is down. */
  dragVelocity: number;
  startPhi: number;
  downClock: number;
  lastClock: number;
  lastPhi: number;
  moved: number;
  detent: number;
  /** Clock of the last release, -1 before the first touch. */
  release: number;
  from: number;
  velocity: number;
  target: number;
  home: number;
  /** Liked cars, one bit per car. */
  likes: number;
  popClock: number;
  popIndex: number;
};

export function initialTouch(): Touch {
  'worklet';
  return {
    touching: false, live: 0, dragVelocity: 0, startPhi: 0, downClock: 0, lastClock: 0, lastPhi: 0, moved: 0, detent: 0,
    release: -1, from: 0, velocity: 0, target: 0, home: 0, likes: 0, popClock: -10, popIndex: -1,
  };
}

function copyTouch(t: Touch): Touch {
  'worklet';
  return {
    touching: t.touching, live: t.live, dragVelocity: t.dragVelocity, startPhi: t.startPhi, downClock: t.downClock,
    lastClock: t.lastClock, lastPhi: t.lastPhi, moved: t.moved, detent: t.detent, release: t.release, from: t.from,
    velocity: t.velocity, target: t.target, home: t.home, likes: t.likes, popClock: t.popClock, popIndex: t.popIndex,
  };
}

/** φ at a clock time (seconds since the screen appeared). */
export function phiAt(t: Touch, clock: number, autoPlay: boolean) {
  'worklet';
  if (t.touching) return t.live;
  if (t.release < 0) return autoPlay ? loopPhi(clock) : 0;
  const dt = clock - t.release;
  if (dt < IDLE) return spring(dt, t.from, t.target, t.velocity, 0.55, 0.8, 1.4);
  const b = dt - IDLE;
  if (b < BACK) return spring(b, t.target, t.home, 0, 0.8, 0.8, BACK);
  return autoPlay ? t.home + loopPhi(b - BACK) : t.home;
}

/** The loop's own time when the loop is what moves the carousel, else -1. */
export function loopTimeAt(t: Touch, clock: number, autoPlay: boolean) {
  'worklet';
  if (!autoPlay || t.touching) return -1;
  if (t.release >= 0) {
    const b = clock - t.release - IDLE - BACK;
    return b >= 0 ? loopTime(b) : -1;
  }
  return loopTime(clock);
}

/** Clock time after which nothing moves any more (outside autoPlay). */
export function restsAfter(t: Touch) {
  'worklet';
  return Math.max(Math.max(0, t.release) + IDLE + BACK, t.popClock + 1.2) + 0.2;
}

export function isLiked(t: Touch, i: number, clock: number, autoPlay: boolean) {
  'worklet';
  if ((t.likes >> i) & 1) return true;
  if (i === HEART_INDEX) {
    const lt = loopTimeAt(t, clock, autoPlay);
    return lt >= 0 && lt >= HEART_AT;
  }
  return false;
}

/** Seconds since car i's heart popped (large when it did not). */
export function popAge(t: Touch, i: number, clock: number, autoPlay: boolean) {
  'worklet';
  if (i === t.popIndex && clock - t.popClock < 1.2) return clock - t.popClock;
  if (i === HEART_INDEX && !((t.likes >> i) & 1)) {
    const lt = loopTimeAt(t, clock, autoPlay);
    if (lt >= 0) return lt - HEART_AT;
  }
  return 10;
}

export function beginTouch(t: Touch, clock: number, autoPlay: boolean): Touch {
  'worklet';
  const current = phiAt(t, clock, autoPlay);
  const n = copyTouch(t);
  n.touching = true;
  n.live = current;
  n.startPhi = current;
  n.downClock = clock;
  n.lastClock = clock;
  n.lastPhi = current;
  n.dragVelocity = 0;
  n.moved = 0;
  n.detent = roundAway(current);
  return n;
}

/** The next state, and whether the car in the middle changed (a detent haptic). */
export function moveTouch(t: Touch, dx: number, dy: number, clock: number): { next: Touch; detent: boolean } {
  'worklet';
  const n = copyTouch(t);
  n.moved = Math.max(n.moved, Math.sqrt(dx * dx + dy * dy));
  if (n.moved < 8) return { next: n, detent: false };
  n.live = n.startPhi - dx / SPACING;
  const dt = clock - n.lastClock;
  if (dt > 0.004) {
    n.dragVelocity = 0.6 * n.dragVelocity + (0.4 * (n.live - n.lastPhi)) / dt;
    n.lastClock = clock;
    n.lastPhi = n.live;
  }
  const d = roundAway(n.live);
  if (d !== n.detent) {
    n.detent = d;
    return { next: n, detent: true };
  }
  return { next: n, detent: false };
}

/** feedback: 0 none, 1 snap, 2 like, 3 unlike. `x`, `y` are in the design space. */
export function endTouch(t: Touch, clock: number, velocityX: number, x: number, y: number, floorY: number): { next: Touch; feedback: number } {
  'worklet';
  const n = copyTouch(t);
  let feedback = 1;
  n.dragVelocity = 0;
  if (n.moved < 8 && clock - n.downClock < 0.5) {
    n.velocity = 0;
    n.target = roundAway(n.live);
    feedback = 0;
    const list = jars(n.live);
    for (let q = list.length - 1; q >= 0; q--) {
      const jar = list[q];
      const s = jar.scale;
      const hx = jar.x + HEART_X * s;
      const hy = floorY + HEART_Y * s;
      if (Math.abs(jar.d) < 0.5 && (x - hx) * (x - hx) + (y - hy) * (y - hy) < 30 * 30) {
        n.likes = n.likes ^ (1 << jar.index);
        if ((n.likes >> jar.index) & 1) {
          n.popClock = clock;
          n.popIndex = jar.index;
          feedback = 2;
        } else {
          if (n.popIndex === jar.index) n.popIndex = -1;
          feedback = 3;
        }
        break;
      }
      const half = (JAR_WIDTH * s) / 2;
      if (x > jar.x - half && x < jar.x + half && y > floorY + (4 - JAR_HEIGHT) * s && y < floorY + 40) {
        if (Math.abs(jar.d) >= 0.5) {
          n.target = roundAway(n.live + jar.d);
          feedback = 1;
        }
        break;
      }
    }
  } else {
    n.velocity = Math.min(12, Math.max(-12, -velocityX / SPACING));
    const nearest = roundAway(n.live);
    n.target = Math.min(nearest + 2, Math.max(nearest - 2, roundAway(n.live + 0.22 * n.velocity)));
  }
  n.from = n.live;
  n.release = clock;
  n.home = COUNT * roundAway(n.target / COUNT);
  n.touching = false;
  return { next: n, feedback };
}
