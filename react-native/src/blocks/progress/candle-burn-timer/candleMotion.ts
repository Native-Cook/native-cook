// The candle's state: a plain object on the UI thread, read by the shader and the timer digits.
//
// `remain` is the session left (0…1 of 25:00) and sets the wax height. autoPlay derives every
// value from the loop time (a compressed 25-minute burn); under a finger the timer runs in real
// time, the flame bends on a spring with the wind of a drag, a hard flick blows it out and a tap
// relights it. All times are seconds on the frame clock.

export const LOOP = 9;
export const SESSION = 25 * 60;
/** The stub left at the end, as a share of the full height. */
export const STUB = 0.24;
const BURN = [1.4, 6.0];
const OUT = [6.0, 6.35];
const SMOKE_AT = 6.05;
const DONE = [6.6, 7.4];
const RISE = [7.4, 8.4];
const RELIGHT = 8.4;
const DRIP_FADE = [7.4, 7.8];
/** Two drips in the loop: x offset (share of the half width), birth (s), length (pt). */
const SCRIPT_DRIPS = [
  [-0.36, 2.4, 44],
  [0.44, 4.2, 32],
];
const DRIP_RUN = 1.2;
const SMOKE_LIFE = 2.4;
// Wind: the flame's lean follows the drag's speed (pt/s per unit lean) on a wobbly spring; a flick
// faster than BLOW_AT blows it out.
const WIND_PER = 900;
const BEND_W = 10;
const BEND_Z = 0.32;
export const BLOW_AT = 1500;
export const IDLE_BEFORE_BACK = 2;
const BACK_FOR = 0.9;

export const MODE_AUTO = 0;
export const MODE_LIVE = 1;
export const MODE_BACK = 2;

/** Status lines under the timer. */
export const STATUS_FOCUS = 0;
export const STATUS_PAUSED = 1;
export const STATUS_OUT = 2;
export const STATUS_DONE = 3;

export type Candle = {
  mode: number;
  t: number;
  now: number;
  lastTouch: number;
  remain: number;
  lit: number;
  /** Frame times: when the flame went out (smoke) and when it was lit again (-100 = never). */
  outAt: number;
  igniteAt: number;
  bend: number;
  bendV: number;
  wind: number;
  flick: number;
  /** Turbulence phase (rad), loop-periodic in autoPlay. */
  phase: number;
  /** Drips: x offset (share of the half width), birth time, full length (pt). */
  dx: number[];
  db: number[];
  dl: number[];
  dripAlpha: number;
  running: boolean;
  status: number;
  /** Status crossfade weights (one per status). */
  statusW: number[];
  /** Reset / back: wax rising from `riseFrom` since `riseAt`. */
  riseAt: number;
  riseFrom: number;
  backAt: number;
  buzz: boolean;
};

export function makeCandle(auto: boolean): Candle {
  return {
    mode: auto ? MODE_AUTO : MODE_LIVE,
    t: 0,
    now: 0,
    lastTouch: -100,
    remain: 1,
    lit: 1,
    outAt: -100,
    igniteAt: -100,
    bend: 0,
    bendV: 0,
    wind: 0,
    flick: 0,
    phase: 0,
    dx: [0, 0],
    db: [-100, -100],
    dl: [0, 0],
    dripAlpha: 1,
    running: true,
    status: STATUS_FOCUS,
    statusW: [1, 0, 0, 0],
    riseAt: -100,
    riseFrom: 1,
    backAt: 0,
    buzz: false,
  };
}

function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

/** A lit flame catching: 0 → 1 with a small overshoot, settled after ~0.5 s. */
function ignite(tau: number) {
  'worklet';
  if (tau <= 0) return 0;
  return 1 - Math.exp(-9 * tau) * Math.cos(10 * tau);
}

/** Flicker of whole-loop sines (−1…1). */
function flickerAt(t: number) {
  'worklet';
  const w = (2 * Math.PI) / LOOP;
  return 0.5 * Math.sin(w * 7 * t) + 0.3 * Math.sin(w * 13 * t + 1.3) + 0.2 * Math.sin(w * 23 * t + 2.1);
}

export function scriptFrame(c: Candle, t: number) {
  'worklet';
  const start = c.now - t;
  const burn = seg(t, BURN[0], BURN[1]);
  const rise = easeInOut(seg(t, RISE[0], RISE[1]));
  c.remain = t < RISE[0] ? 1 - burn : rise;
  if (t < OUT[0]) c.lit = 1;
  else if (t < RELIGHT) c.lit = 1 - easeInOut(seg(t, OUT[0], OUT[1]));
  else c.lit = ignite(t - RELIGHT);
  c.outAt = t >= SMOKE_AT ? start + SMOKE_AT : -100;
  c.igniteAt = start + RELIGHT;
  c.flick = flickerAt(t);
  c.phase = ((2 * Math.PI * 6) / LOOP) * t;
  c.bend = 0.08 * Math.sin((2 * Math.PI * 2 * t) / LOOP) + 0.04 * Math.sin((2 * Math.PI * 5 * t) / LOOP);
  c.bendV = 0;
  for (let i = 0; i < 2; i += 1) {
    c.dx[i] = SCRIPT_DRIPS[i][0];
    c.db[i] = t >= SCRIPT_DRIPS[i][1] ? start + SCRIPT_DRIPS[i][1] : -100;
    c.dl[i] = SCRIPT_DRIPS[i][2];
  }
  c.dripAlpha = 1 - easeOut(seg(t, DRIP_FADE[0], DRIP_FADE[1]));
  // Status: focus, then "done" from the moment the flame is out until the new candle rises.
  const done = seg(t, OUT[1], DONE[0]) * (1 - seg(t, DONE[1], RISE[1]));
  c.statusW[STATUS_FOCUS] = 1 - done;
  c.statusW[STATUS_PAUSED] = 0;
  c.statusW[STATUS_OUT] = 0;
  c.statusW[STATUS_DONE] = done;
  c.status = done > 0.5 ? STATUS_DONE : STATUS_FOCUS;
  c.running = true;
}

/** A drip's length now (pt): it runs down from the rim over DRIP_RUN s. */
export function dripLength(c: Candle, i: number) {
  'worklet';
  const age = c.now - c.db[i];
  if (age < 0) return 0;
  return c.dl[i] * easeOut(Math.min(age / DRIP_RUN, 1));
}

/** Smoke: seconds since the flame went out, and its strength. */
export function smokeAt(c: Candle): [number, number] {
  'worklet';
  const age = c.now - c.outAt;
  if (c.outAt < -50 || age < 0) return [0, 0];
  const fade = 1 - seg(age, SMOKE_LIFE * 0.55, SMOKE_LIFE);
  const relit = c.lit > 0.05 ? 1 - seg(c.now - c.igniteAt, 0, 0.3) : 1;
  return [age, fade * Math.max(relit, 0)];
}

function spring(x: number, v: number, x0: number, w: number, z: number, dt: number): [number, number] {
  'worklet';
  const nv = v + (-w * w * (x - x0) - 2 * z * w * v) * dt;
  return [x + nv * dt, nv];
}

function setStatus(c: Candle, status: number, dt: number) {
  'worklet';
  c.status = status;
  const k = Math.min(dt / 0.18, 1);
  for (let i = 0; i < 4; i += 1) c.statusW[i] += ((i === status ? 1 : 0) - c.statusW[i]) * k;
}

/** Real-time frame under a finger (or with autoPlay off). */
export function liveFrame(c: Candle, dt: number) {
  'worklet';
  const lean = Math.max(-1, Math.min(1, c.wind / WIND_PER));
  [c.bend, c.bendV] = spring(c.bend, c.bendV, lean, BEND_W, BEND_Z, dt);
  c.wind *= Math.exp(-10 * dt);
  c.phase += ((2 * Math.PI * 6) / LOOP) * dt * (1 + Math.abs(c.bend) * 2);
  c.flick = flickerAt(c.now) * (1 + Math.abs(c.bend) * 1.5);
  // The flame: going out over 0.25 s, catching again with a small overshoot.
  if (c.outAt > c.igniteAt) c.lit = Math.max(0, 1 - (c.now - c.outAt) / 0.25);
  else c.lit = c.igniteAt > -50 ? ignite(c.now - c.igniteAt) : 1;
  if (c.riseAt > -50) {
    const k = easeInOut(Math.min((c.now - c.riseAt) / 0.9, 1));
    c.remain = c.riseFrom + (1 - c.riseFrom) * k;
    c.dripAlpha = Math.min(c.dripAlpha, 1 - k);
    if (k >= 1) {
      c.riseAt = -100;
      c.db[0] = -100;
      c.db[1] = -100;
      c.dripAlpha = 1;
    }
  } else if (c.running && c.lit > 0.5 && c.remain > 0) {
    c.remain = Math.max(0, c.remain - dt / SESSION);
  }
  const status = c.lit < 0.5 ? STATUS_OUT : c.remain <= 0 ? STATUS_DONE : c.running ? STATUS_FOCUS : STATUS_PAUSED;
  setStatus(c, status, dt);
}

export function blowOut(c: Candle) {
  'worklet';
  if (c.outAt > c.igniteAt) return false;
  c.outAt = c.now;
  return true;
}

export function relight(c: Candle) {
  'worklet';
  if (c.outAt <= c.igniteAt && c.lit > 0.5) return false;
  c.igniteAt = c.now;
  return true;
}

export function resetCandle(c: Candle) {
  'worklet';
  c.riseAt = c.now;
  c.riseFrom = c.remain;
  c.running = true;
  relight(c);
}

export function beginBack(c: Candle) {
  'worklet';
  c.mode = MODE_BACK;
  c.backAt = c.now;
  c.riseAt = c.now;
  c.riseFrom = c.remain;
  c.running = true;
  relight(c);
}

/** Back to the loop's first frame: the wax rises, the flame catches; true when done. */
export function backFrame(c: Candle, dt: number) {
  'worklet';
  c.wind = 0;
  liveFrame(c, dt);
  // The loop's flicker and turbulence start from their t = 0 values.
  const k = easeInOut(Math.min((c.now - c.backAt) / BACK_FOR, 1));
  c.phase *= 1 - k;
  c.flick *= 1 - k;
  if (k >= 1 && c.riseAt < -50 && c.now - c.igniteAt > 0.6) {
    c.bend = 0;
    c.bendV = 0;
    return true;
  }
  return false;
}
