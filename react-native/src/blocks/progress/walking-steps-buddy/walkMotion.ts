/*
 Time model of Walking Steps Buddy. Two clocks drive the screen:
 - D, the distance walked (pt): the parallax layers and the walk cycle are functions of D.
 - the loop time t (0 until LOOP): the step count, the bar, the milestone pop and the sun.
 In autoPlay D = SPEED · t, so one loop is exactly one tile of the near layer and twelve strides.
 Every function here is a worklet; helpers are declared above their callers.
 */

export const LOOP = 9;
export const SPEED = 96;
export const TILE = 864;
export const STRIDE = 72;
export const IDLE = 2;

export const HILL_FACTOR = 0.2;
export const HILL_TILE = 172.8;
export const BUSH_FACTOR = 0.5;
export const BUSH_TILE = 432;
export const FRONT_FACTOR = 1.25;
export const FRONT_TILE = 360;
export const CLOUD_DRIFT = 72;

export const COUNTS = [0, 164, 352, 538, 712, 905, 1093, 1270, 1462, 1641, 1838, 2016, 2203, 2391, 2570, 2764, 2952, 3146, 0];
export const COUNT_STRINGS = COUNTS.map((n) => n.toLocaleString('en-US'));
const FIRST_FOOTFALL = 1.125;
const FOOTFALL_GAP = 0.375;
const ROLL_TIME = 0.24;
export const RESET_START = 7.8;
const RESET_END = 8.4;
export const MILESTONES = [1000, 3000, 6000, 10000];
export const MILESTONE_LABELS = ['1k steps', '3k steps', '6k steps', '10k steps'];
export const REWARDS = ['+10', '+30', '+60', '+100'];
export const BAR_LEFT = 20;
export const BAR_RIGHT = 373;
export const TICK_X = [0, 1, 2, 3].map((i) => 20 + (353 * (i + 0.5)) / 4);
export const MASCOT_X = 176;
export const CELEBRATE = 3.05;

export function clamp(x: number, lo: number, hi: number) {
  'worklet';
  return Math.min(Math.max(x, lo), hi);
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function backOut(x: number) {
  'worklet';
  const c1 = 1.70158;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
}

export function smooth(a: number, b: number, x: number) {
  'worklet';
  const u = clamp((x - a) / (b - a), 0, 1);
  return u * u * (3 - 2 * u);
}

export function fract(x: number) {
  'worklet';
  return x - Math.floor(x);
}

export function mod(x: number, m: number) {
  'worklet';
  return x - Math.floor(x / m) * m;
}

export function hash(i: number) {
  'worklet';
  return fract(Math.sin(i * 12.9898) * 43758.5453);
}

function footfall(k: number) {
  'worklet';
  return FIRST_FOOTFALL + FOOTFALL_GAP * (k - 1);
}

/** Drum position of the count: k + roll progress (0 … 18; 18 shows the next day's 0). */
export function countPhase(t: number) {
  'worklet';
  let p = 0;
  for (let k = 1; k <= 17; k++) p += easeOut(seg(t, footfall(k), footfall(k) + ROLL_TIME));
  return p + easeInOut(seg(t, RESET_START, RESET_END));
}

export function steps(phase: number) {
  'worklet';
  const i = Math.floor(phase);
  if (i >= COUNTS.length - 1) return 0;
  const f = phase - i;
  return COUNTS[i] + (COUNTS[i + 1] - COUNTS[i]) * f;
}

function crossTime(milestone: number) {
  for (let k = 1; k <= 17; k++) if (COUNTS[k] >= milestone) return footfall(k) + 0.05;
  return -1;
}
/** Loop time at which each milestone is reached, -1 when the loop never reaches it. */
export const CROSS_TIMES = MILESTONES.map(crossTime);

export function tickPop(i: number, t: number) {
  'worklet';
  const c = CROSS_TIMES[i];
  if (c < 0) return 0;
  return backOut(seg(t, c, c + 0.4)) * (1 - easeInOut(seg(t, RESET_START, RESET_START + 0.5)));
}

export function pillMix(i: number, t: number) {
  'worklet';
  const c = CROSS_TIMES[i];
  if (c < 0) return 0;
  return easeOut(seg(t, c + 0.1, c + 0.35)) * (1 - easeInOut(seg(t, RESET_START, RESET_START + 0.5)));
}

/** Bar position (pt) for a step value: each milestone sits under its column. */
export function barX(s: number) {
  'worklet';
  if (s <= 0) return BAR_LEFT;
  let prevS = 0;
  let prevX = BAR_LEFT;
  for (let i = 0; i < 4; i++) {
    const m = MILESTONES[i];
    if (s <= m) return prevX + ((TICK_X[i] - prevX) * (s - prevS)) / (m - prevS);
    prevS = m;
    prevX = TICK_X[i];
  }
  return Math.min(BAR_RIGHT, prevX + ((BAR_RIGHT - prevX) * (s - prevS)) / 2000);
}

/** 0 at day, 1 when the sun has set. */
export function dusk(t: number) {
  'worklet';
  return easeInOut(seg(t, 6.2, 7.4)) * (1 - easeInOut(seg(t, RESET_START, RESET_START + 0.8)));
}

export function happyAt(t: number) {
  'worklet';
  return t >= CELEBRATE && t < CELEBRATE + 0.85;
}

/** The "+10 coins" bubble above the 1k tick: y relative to the bar centre, alpha, scale. */
export function bubble(t: number) {
  'worklet';
  const rise = backOut(seg(t, 3.1, 3.5));
  const out = easeInOut(seg(t, 4.3, 4.7));
  return { y: -40 * rise - 8 * out, alpha: easeOut(seg(t, 3.1, 3.25)) * (1 - out), scale: 0.6 + 0.4 * rise };
}

export type Foot = { x: number; lift: number; angle: number };

export function foot(u0: number): Foot {
  'worklet';
  const u = fract(u0);
  if (u < 0.5) return { x: 18 - 72 * u, lift: 0, angle: 0 };
  const v = (u - 0.5) / 0.5;
  return { x: -18 + 36 * easeInOut(v), lift: 12 * Math.sin(Math.PI * v), angle: -14 * Math.sin(Math.PI * v) };
}

export type Pose = {
  near: Foot;
  far: Foot;
  bob: number;
  sx: number;
  sy: number;
  lean: number;
  knot: number;
  arm: number;
  bag: number;
  lift: number;
};

export function pose(distance: number): Pose {
  'worklet';
  const w = fract(distance / STRIDE);
  const s2 = Math.pow(Math.sin(2 * Math.PI * w), 2);
  const c = Math.min(w, Math.abs(w - 0.5), 1 - w);
  const g = Math.exp(-Math.pow(c / 0.07, 2));
  return {
    near: foot(w),
    far: foot(w + 0.5),
    bob: -7 * s2,
    sx: 1 + 0.06 * g,
    sy: 1 - 0.07 * g + 0.025 * s2,
    lean: 4 + 1.5 * Math.sin(4 * Math.PI * w + 0.5),
    knot: -4 - 9 * Math.sin(4 * Math.PI * w - 0.9),
    arm: 16 * Math.sin(2 * Math.PI * w),
    bag: 12 * Math.sin(2 * Math.PI * w - 1.2),
    lift: 0,
  };
}

/** Hop after a tap, τ s after it: lift (pt) and vertical squash. */
export function hopLift(tau: number) {
  'worklet';
  if (tau < 0.08 || tau > 0.5) return 0;
  return 36 * Math.sin((Math.PI * (tau - 0.08)) / 0.42);
}

export function hopSquash(tau: number) {
  'worklet';
  if (tau < 0 || tau > 0.62) return 1;
  if (tau < 0.08) return 1 - 0.08 * Math.sin(((Math.PI / 2) * tau) / 0.08);
  if (tau < 0.5) {
    const u = (tau - 0.08) / 0.42;
    return u < 0.2 ? 0.92 + 0.13 * (u / 0.2) : 1.05 - 0.05 * ((u - 0.2) / 0.8);
  }
  return 1 - 0.07 * Math.sin((Math.PI * (tau - 0.5)) / 0.12);
}

export function ground(safeTop: number) {
  return safeTop + 362;
}

// MARK: Touch (a plain object kept in a shared value)

export type Touch = {
  touching: boolean;
  liveD: number;
  startD: number;
  downClock: number;
  moved: number;
  released: boolean;
  releaseClock: number;
  releaseD: number;
  releaseV: number;
  frozen: number;
  resume: number;
  hopClock: number;
};

export function initialTouch(): Touch {
  return {
    touching: false,
    liveD: 0,
    startD: 0,
    downClock: 0,
    moved: 0,
    released: false,
    releaseClock: 0,
    releaseD: 0,
    releaseV: 0,
    frozen: 0,
    resume: 0,
    hopClock: -10,
  };
}

/** Distance walked at a clock time (seconds since the screen appeared). */
export function distanceAt(s: Touch, clock: number, autoPlay: boolean) {
  'worklet';
  if (s.touching) return s.liveD;
  if (!s.released) return autoPlay ? SPEED * clock : 0;
  const tau = Math.max(0, clock - s.releaseClock);
  if (autoPlay) return s.releaseD + SPEED * tau + ((s.releaseV - SPEED) * (1 - Math.exp(-6 * tau))) / 6;
  // Without autoPlay the walk settles on the nearest footfall pose (critically damped).
  const w = 8;
  const half = STRIDE / 2;
  const target = Math.round((s.releaseD + s.releaseV / w) / half) * half;
  const a = s.releaseD - target;
  return target + (a + (s.releaseV + w * a) * tau) * Math.exp(-w * tau);
}

/** Loop time: paused while touching and for IDLE s after, then it resumes where it stopped. */
export function loopTimeAt(s: Touch, clock: number, autoPlay: boolean) {
  'worklet';
  if (!autoPlay) return 0;
  const t = s.touching ? s.frozen : s.frozen + Math.max(0, clock - s.resume);
  return mod(t, LOOP);
}

export function restsAfter(s: Touch) {
  'worklet';
  return Math.max(s.releaseClock + 1.2, s.hopClock + 0.7);
}

export function beginTouch(s: Touch, clock: number, autoPlay: boolean): Touch {
  'worklet';
  if (s.touching) return s;
  const d = distanceAt(s, clock, autoPlay);
  return { ...s, frozen: loopTimeAt(s, clock, autoPlay), startD: d, liveD: d, touching: true, downClock: clock, moved: 0 };
}

export function moveTouch(s: Touch, dx: number, dy: number): Touch {
  'worklet';
  const moved = Math.max(s.moved, Math.sqrt(dx * dx + dy * dy));
  if (moved < 8) return { ...s, moved };
  return { ...s, moved, liveD: s.startD - dx };
}

/** Ends a touch; `hopped` is true when it was a tap on the mascot. */
export function endTouch(s: Touch, clock: number, velocityX: number, x: number, y: number, g: number, autoPlay: boolean) {
  'worklet';
  let hopped = false;
  let releaseV: number;
  let hopClock = s.hopClock;
  if (s.moved < 8 && clock - s.downClock < 0.4) {
    releaseV = autoPlay ? SPEED : 0;
    const lift = hopLift(clock - s.hopClock);
    if (Math.abs(x - MASCOT_X) < 84 && y > g + 22 - 178 - lift && y < g + 30) {
      hopClock = clock;
      hopped = true;
    }
  } else {
    releaseV = clamp(-velocityX, -700, 700);
  }
  const next: Touch = {
    ...s,
    releaseV,
    hopClock,
    releaseD: s.liveD,
    releaseClock: clock,
    released: true,
    resume: clock + IDLE,
    touching: false,
  };
  return { next, hopped };
}
