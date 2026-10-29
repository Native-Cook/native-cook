/*
 Timing and touch model of Moka Flood Welcome, ported from MokaFloodWelcomeMotion.swift. Every loop
 value is a pure function of the loop time, so the loop's last frame equals its first. The touch
 model is a plain object stepped once per frame on the UI thread.
*/

export const LOOP = 9.5;

const REST_ANGLE = -6;
const POUR_ANGLE = -50;
const GRAVITY = 2200;
const POUR_THRESHOLD = -36;

const STREAM_AT = 1.2;
const FILL_AT = 1.72;
const BREAK_AT = 5.4;
const DRAIN_AT = 8.1;
const DRAIN_END = 9.0;

const IDLE = 2.0;
const DRAIN_TIME = 0.9;

/** What the art needs for one frame. */
export type Frame = {
  angle: number;
  /** 0 empty glass, 1 at the rim, 1–1.75 dome, spills and puddle, 1.75–3 the flood rises. */
  volume: number;
  /** 0–1 while the flood drains from above the top to below the bottom; -1 otherwise. */
  drain: number;
  title: number;
  sub: number;
  stream: boolean;
  head: number; // pt the stream's head has fallen from the spout
  tail: number; // pt its broken tail has fallen (0 while attached)
  width: number; // its width share
  time: number;
  slosh: number;
};

// Curves

export function mod(a: number, b: number) {
  'worklet';
  const r = a % b;
  return r < 0 ? r + b : r;
}

export function clamp(v: number, lo: number, hi: number) {
  'worklet';
  return Math.min(Math.max(v, lo), hi);
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

function easeInCubic(x: number) {
  'worklet';
  return x * x * x;
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

function easeInOutSine(x: number) {
  'worklet';
  return -(Math.cos(Math.PI * x) - 1) / 2;
}

function springDecay(tau: number, z: number, response: number) {
  'worklet';
  if (tau <= 0) return 1;
  const w = (2 * Math.PI) / response;
  const wd = w * Math.sqrt(1 - z * z);
  return Math.exp(-z * w * tau) * (Math.cos(wd * tau) + ((z * w) / wd) * Math.sin(wd * tau));
}

function springStep(tau: number, z: number, response: number) {
  'worklet';
  return tau <= 0 ? 0 : 1 - springDecay(tau, z, response);
}

export function hash(x: number) {
  'worklet';
  const s = Math.sin(x * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** Write progress of title line `i` for a title value 0–1. */
export function lineProgress(title: number, i: number) {
  'worklet';
  return easeOutCubic(clamp((title - 0.29 * i) / 0.42, 0, 1));
}

export function restFrame(): Frame {
  'worklet';
  return { angle: REST_ANGLE, volume: 0, drain: -1, title: 0, sub: 0, stream: false, head: 0, tail: 0, width: 1, time: 0, slosh: 0 };
}

export function stillFrame(): Frame {
  'worklet';
  return { angle: REST_ANGLE, volume: 3, drain: -1, title: 1, sub: 1, stream: false, head: 0, tail: 0, width: 1, time: 0, slosh: 0 };
}

// The loop (P = 9.5 s)

function loopVolume(t: number) {
  'worklet';
  if (t < FILL_AT) return 0;
  if (t < 3.6) return easeInOutSine(seg(t, FILL_AT, 3.6));
  if (t < 4.7) return 1 + 0.75 * seg(t, 3.6, 4.7);
  return 1.75 + 1.25 * easeInOutSine(seg(t, 4.7, 6.5));
}

/**
 Rest 0–1 s → the pot tilts on a spring (1.0) and pours (1.2) → the glass fills (1.72–3.6) → domes,
 spills and puddles (3.6–4.7) → the flood rises (4.7–6.5) while the pot rights itself (5.15) and the
 stream breaks (5.4) → the title is written (6.55–7.55) → the flood drains (8.1–9.0) → rest.
 */
export function loopFrame(t: number): Frame {
  'worklet';
  const f = restFrame();
  f.time = t;
  const up = springStep(t - 1.0, 0.62, 0.6);
  const back = springStep(t - 5.15, 0.6, 0.6);
  const anticipation = 4 * Math.sin(Math.PI * seg(t, 0.85, 1.05));
  f.angle = REST_ANGLE + (POUR_ANGLE - REST_ANGLE) * (up - back) + anticipation;
  if (t >= STREAM_AT) {
    const tau = t - STREAM_AT;
    const tail = t > BREAK_AT ? 0.5 * GRAVITY * (t - BREAK_AT) * (t - BREAK_AT) : 0;
    if (tail < 1200) {
      f.stream = true;
      f.head = 0.5 * GRAVITY * tau * tau;
      f.tail = tail;
      f.width = 1 - 0.65 * easeInCubic(seg(t, 5.1, BREAK_AT));
    }
  }
  f.slosh = seg(t, FILL_AT, FILL_AT + 0.25) * (1 - seg(t, BREAK_AT, 6.0));
  if (t < DRAIN_END) {
    f.volume = loopVolume(t);
    f.title = seg(t, 6.55, 7.55);
    f.sub = easeOutCubic(seg(t, 7.25, 7.65));
    if (t >= DRAIN_AT) f.drain = easeInOutCubic(seg(t, DRAIN_AT, DRAIN_END));
  }
  return f;
}

// Touch: hold to tilt the pot and pour, drag left or down to pour harder; release to let it settle.
// Two seconds after the last touch the coffee drains and the loop starts again from rest.

export type TouchState = {
  touching: boolean;
  lastClock: number; // last touch event
  lastStep: number;
  angle: number;
  spin: number; // deg/s
  volume: number; // 0…4: 3–4 writes the title
  drag: number;
  streaming: boolean;
  streamStart: number;
  broken: boolean;
  streamBreak: number;
  drainSweep: boolean;
  hapticAt: number; // a due haptic's clock, or -1
};

export function initialTouch(): TouchState {
  'worklet';
  return {
    touching: false,
    lastClock: -100,
    lastStep: -1,
    angle: REST_ANGLE,
    spin: 0,
    volume: 0,
    drag: 0,
    streaming: false,
    streamStart: 0,
    broken: false,
    streamBreak: 0,
    drainSweep: false,
    hapticAt: -1,
  };
}

export function restsAfter(s: TouchState) {
  'worklet';
  return s.lastClock + IDLE + DRAIN_TIME;
}

/** Loop time, or -1 while the finger owns the scene. */
export function loopTime(s: TouchState, clock: number, autoPlay: boolean) {
  'worklet';
  if (s.touching || clock < restsAfter(s)) return -1;
  if (!autoPlay) return 0;
  const base = s.lastClock > -50 ? restsAfter(s) : 0;
  return mod(clock - base, LOOP);
}

export function touchFrame(s: TouchState, clock: number, autoPlay: boolean): Frame {
  'worklet';
  const t = loopTime(s, clock, autoPlay);
  if (t >= 0) return autoPlay ? loopFrame(t) : restFrame();
  const f = restFrame();
  f.time = clock;
  f.angle = s.angle;
  f.volume = Math.min(s.volume, 3);
  f.title = clamp(s.volume - 3, 0, 1);
  f.sub = easeOutCubic(clamp((f.title - 0.7) / 0.3, 0, 1));
  if (s.streaming) {
    const tau = clock - s.streamStart;
    let width = 1;
    let tail = 0;
    if (s.broken) {
      const k = clock - s.streamBreak;
      width = 1 - 0.65 * easeInCubic(clamp(k / 0.22, 0, 1));
      if (k > 0.22) tail = 0.5 * GRAVITY * (k - 0.22) * (k - 0.22);
    }
    if (tail < 1200) {
      f.stream = true;
      f.head = 0.5 * GRAVITY * tau * tau;
      f.tail = tail;
      f.width = width;
    }
  }
  f.slosh = f.stream ? 1 : 0.4;
  const drainStart = s.lastClock + IDLE;
  if (!s.touching && clock > drainStart) {
    const d = easeInOutCubic(clamp((clock - drainStart) / DRAIN_TIME, 0, 1));
    if (s.drainSweep) f.drain = d;
    else f.volume *= 1 - d;
  }
  return f;
}

/** Integrates the pot's spring and the poured volume (touch only; the loop is closed-form). */
export function stepTouch(s: TouchState, clock: number, autoPlay: boolean): TouchState {
  'worklet';
  if (loopTime(s, clock, autoPlay) >= 0) return s;
  const dt = clamp(clock - s.lastStep, 0, 1 / 30);
  const n = { ...s, lastStep: clock };
  if (dt <= 0) return n;
  const target = n.touching ? REST_ANGLE + (POUR_ANGLE - REST_ANGLE) * (0.85 + 0.25 * n.drag) : REST_ANGLE;
  const w = (2 * Math.PI) / 0.6;
  const z = n.touching ? 0.62 : 0.6;
  let sub = 0;
  while (sub < dt) {
    const h = Math.min(1 / 240, dt - sub);
    n.spin += (-w * w * (n.angle - target) - 2 * z * w * n.spin) * h;
    n.angle += n.spin * h;
    sub += h;
  }
  const pouring = n.angle < POUR_THRESHOLD;
  if (pouring && (!n.streaming || n.broken)) {
    n.streaming = true;
    n.streamStart = clock;
    n.broken = false;
  } else if (!pouring && n.streaming && !n.broken) {
    n.broken = true;
    n.streamBreak = clock;
  }
  if (pouring && n.streaming && clock - n.streamStart > 0.4) {
    const before = n.volume;
    n.volume = Math.min(4, n.volume + dt * (0.45 + 0.6 * n.drag));
    if ((before <= 0 && n.volume > 0) || (before <= 1 && n.volume > 1) || (before <= 3 && n.volume > 3)) n.hapticAt = clock;
  }
  return n;
}

export function beginTouch(s: TouchState, clock: number, autoPlay: boolean): TouchState {
  'worklet';
  const n = { ...s };
  if (!s.touching) {
    const t = loopTime(s, clock, autoPlay);
    if (t >= 0 && autoPlay) {
      // Carry the loop as it is on screen into the touch.
      const f = loopFrame(t);
      n.angle = f.angle;
      n.spin = 0;
      n.volume = f.drain >= 0 ? 0 : f.title > 0 ? 3 + f.title : f.volume;
      n.streaming = t >= STREAM_AT && t < BREAK_AT;
      n.streamStart = clock - (t - STREAM_AT);
      n.broken = false;
    } else if (clock > s.lastClock + IDLE) {
      // Touched while the last pour drains: start again from what is left.
      const d = easeInOutCubic(clamp((clock - s.lastClock - IDLE) / DRAIN_TIME, 0, 1));
      n.volume = s.drainSweep ? (d > 0.5 ? 0 : s.volume) : s.volume * (1 - d);
    }
    n.lastStep = clock;
  }
  n.touching = true;
  n.drag = 0;
  n.lastClock = clock;
  return n;
}

/** `dx`, `dy`: the finger's travel in pt; left or down tilts the pot further. */
export function moveTouch(s: TouchState, clock: number, dx: number, dy: number): TouchState {
  'worklet';
  if (!s.touching) return s;
  return { ...s, drag: clamp((-dx + dy) / 160, 0, 1), lastClock: clock };
}

export function endTouch(s: TouchState, clock: number): TouchState {
  'worklet';
  if (!s.touching) return s;
  const n = stepTouch(s, clock, false);
  return { ...n, touching: false, drag: 0, lastClock: clock, drainSweep: n.volume >= 2.9 };
}
