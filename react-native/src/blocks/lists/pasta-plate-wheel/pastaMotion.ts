/*
 Pasta Plate Wheel — time and touch model. Everything on screen is a function of φ, the wheel's
 position in plate steps (φ = 2 puts dish 2 at the front), and φ is a function of the clock.
 Every worklet here is declared above the worklets that call it.
 */

export const LOOP = 8;
/** Seconds after the last touch before the wheel springs back to the first dish. */
export const IDLE = 2;
/** Duration of that return; the loop restarts at its end. */
export const BACK = 1.6;
/** Drag distance (design units) that turns the wheel by one plate. */
export const STEP_POINTS = 160;
export const COUNT = 8;
export const CENTRE_X = 14;
export const ORBIT = 226;
export const DISC_RADIUS = 242;

/** Rounds half away from zero, like Swift's `rounded()`. */
export function roundAway(x: number) {
  'worklet';
  return x >= 0 ? Math.floor(x + 0.5) : -Math.floor(-x + 0.5);
}

/** Step response of a damped spring (response = period in s); exactly `to` after `settle` s. */
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

/** The loop: rest on A, turn to B, hold, turn to C, hold, turn back to A, rest. */
export function loopPhi(time: number) {
  'worklet';
  const t = time - Math.floor(time / LOOP) * LOOP;
  if (t < 1.0) return 0;
  if (t < 2.9) return spring(t - 1.0, 0, 1, 0, 0.6, 0.7, 1.5);
  if (t < 4.8) return spring(t - 2.9, 1, 2, 0, 0.6, 0.7, 1.5);
  return spring(t - 4.8, 2, 0, 0, 0.8, 0.78, 1.6);
}

/** d wrapped into [-COUNT/2, COUNT/2). */
export function wrap(d: number) {
  'worklet';
  const n = COUNT;
  const m = d + n / 2 - Math.floor((d + n / 2) / n) * n;
  return m - n / 2;
}

export type Plate = { index: number; x: number; y: number; size: number; front: number; spin: number };

/** The eight plates for φ, ordered back to front. */
export function plates(phi: number, cy: number, popIndex: number, pop: number): Plate[] {
  'worklet';
  const out: Plate[] = [];
  for (let i = 0; i < COUNT; i++) {
    const d = wrap(i - phi);
    const theta = (d * Math.PI) / 4;
    const f = Math.exp(-2.2 * d * d);
    let size = 84 + 80 * Math.exp(-1.1 * d * d);
    if (i === popIndex) size *= pop;
    const r = ORBIT + 22 * f;
    out.push({ index: i, x: CENTRE_X + r * Math.cos(theta), y: cy + r * Math.sin(theta), size, front: f, spin: 3 * theta });
  }
  out.sort((p, q) => p.front - q.front);
  return out;
}

/** Scale of a plate popped by a tap, t s after the tap. */
export function popScale(t: number) {
  'worklet';
  if (t < 0 || t > 1.2) return 1;
  return 1 + 0.1 * Math.exp(-5 * t) * Math.sin(13 * t);
}

export function infoTop(height: number, safeBottom: number) {
  'worklet';
  return height - safeBottom - 160;
}

export function centreY(safeTop: number, height: number, safeBottom: number) {
  'worklet';
  return (safeTop + 60 + infoTop(height, safeBottom)) / 2;
}

/** Touch state, kept in a shared value; release < 0 means no touch yet. */
export type Touch = {
  touching: boolean;
  live: number;
  startPhi: number;
  downClock: number;
  moved: number;
  detent: number;
  release: number;
  from: number;
  velocity: number;
  target: number;
  home: number;
  popClock: number;
  popIndex: number;
};

export function initialTouch(): Touch {
  'worklet';
  return {
    touching: false, live: 0, startPhi: 0, downClock: 0, moved: 0, detent: 0, release: -1,
    from: 0, velocity: 0, target: 0, home: 0, popClock: -10, popIndex: -1,
  };
}

function copyTouch(t: Touch): Touch {
  'worklet';
  return {
    touching: t.touching, live: t.live, startPhi: t.startPhi, downClock: t.downClock, moved: t.moved, detent: t.detent,
    release: t.release, from: t.from, velocity: t.velocity, target: t.target, home: t.home, popClock: t.popClock, popIndex: t.popIndex,
  };
}

/** φ at a clock time (seconds since the screen appeared). */
export function phiAt(t: Touch, clock: number, autoPlay: boolean) {
  'worklet';
  if (t.touching) return t.live;
  if (t.release < 0) return autoPlay ? loopPhi(clock) : 0;
  const dt = clock - t.release;
  if (dt < IDLE) return spring(dt, t.from, t.target, t.velocity, 0.55, 0.78, 1.6);
  const b = dt - IDLE;
  if (b < BACK) return spring(b, t.target, t.home, 0, 0.8, 0.78, BACK);
  return autoPlay ? t.home + loopPhi(b - BACK) : t.home;
}

export function restsAfter(t: Touch) {
  'worklet';
  return Math.max(0, t.release) + IDLE + BACK + 0.2;
}

export function beginTouch(t: Touch, clock: number, autoPlay: boolean): Touch {
  'worklet';
  const current = phiAt(t, clock, autoPlay);
  const n = copyTouch(t);
  n.touching = true;
  n.live = current;
  n.startPhi = current;
  n.downClock = clock;
  n.moved = 0;
  n.detent = roundAway(current);
  return n;
}

/** The next state, and whether the nearest plate changed (a detent haptic). */
export function moveTouch(t: Touch, dx: number, dy: number): { next: Touch; detent: boolean } {
  'worklet';
  const n = copyTouch(t);
  n.moved = Math.max(n.moved, Math.sqrt(dx * dx + dy * dy));
  if (n.moved < 8) return { next: n, detent: false };
  n.live = n.startPhi - dy / STEP_POINTS;
  const d = roundAway(n.live);
  if (d !== n.detent) {
    n.detent = d;
    return { next: n, detent: true };
  }
  return { next: n, detent: false };
}

/** feedback: 0 none, 1 snap, 2 pop. */
export function endTouch(t: Touch, clock: number, velocityY: number, x: number, y: number, cy: number): { next: Touch; feedback: number } {
  'worklet';
  const n = copyTouch(t);
  let feedback = 1;
  if (n.moved < 8 && clock - n.downClock < 0.5) {
    n.velocity = 0;
    n.target = roundAway(n.live);
    const list = plates(n.live, cy, -1, 1);
    let hit = -1;
    for (let k = list.length - 1; k >= 0; k--) {
      const p = list[k];
      if (Math.sqrt((p.x - x) * (p.x - x) + (p.y - y) * (p.y - y)) < p.size / 2) {
        hit = p.index;
        break;
      }
    }
    if (hit >= 0) {
      const delta = wrap(hit - n.live);
      if (Math.abs(delta) < 0.5) {
        n.popClock = clock;
        n.popIndex = hit;
        feedback = 2;
      } else {
        n.target = roundAway(n.live + delta);
      }
    } else {
      feedback = 0;
    }
  } else {
    n.velocity = Math.min(12, Math.max(-12, -velocityY / STEP_POINTS));
    const nearest = roundAway(n.live);
    n.target = Math.min(nearest + 3, Math.max(nearest - 3, roundAway(n.live + 0.22 * n.velocity)));
  }
  n.from = n.live;
  n.release = clock;
  n.home = 8 * roundAway(n.target / 8);
  n.touching = false;
  return { next: n, feedback };
}
