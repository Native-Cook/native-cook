/*
 Timing and touch model of Pineapple Lift Product, ported from PineappleLiftProductMotion.swift.
 Every value is a pure function of the loop time (autoPlay) or of the clock since a touch event, so
 the loop's last frame equals its first. Helpers are declared above their callers (worklets).
 */

export const LOOP = 7;

export type Burst = { age: number; strength: number };

/** What the art needs for one frame. */
export type Frame = {
  open: number; // the top half: 0 closed, 1 lifted up-right and tilted
  rise: number; // the bottle: 0 hidden in the fruit, 1 hovering above it
  squash: number; // pt the top half presses down (anticipation) or hops up (negative)
  glow: number; // warm glow behind the bottle
  drift: number; // hibiscus flowers drifting outward
  sheen: number; // a light band crossing the bottle, 0 → 1 (negative: none)
  bursts: Burst[];
};

export function emptyFrame(): Frame {
  'worklet';
  return { open: 0, rise: 0, squash: 0, glow: 0, drift: 0, sheen: -1, bursts: [] };
}

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

export function easeInCubic(x: number) {
  'worklet';
  return x * x * x;
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeInOutSine(x: number) {
  'worklet';
  return -(Math.cos(Math.PI * x) - 1) / 2;
}

export function smoothstep(x: number) {
  'worklet';
  return x * x * (3 - 2 * x);
}

/** A released spring's remaining share of its start value (zero start velocity). */
export function springDecay(tau: number, z: number, response: number) {
  'worklet';
  if (tau <= 0) return 1;
  const w = (2 * Math.PI) / response;
  const wd = w * Math.sqrt(1 - z * z);
  return Math.exp(-z * w * tau) * (Math.cos(wd * tau) + ((z * w) / wd) * Math.sin(wd * tau));
}

/** A spring stepping from 0 to 1. */
export function springStep(tau: number, z: number, response: number) {
  'worklet';
  return tau <= 0 ? 0 : 1 - springDecay(tau, z, response);
}

export function hash(x: number) {
  'worklet';
  const s = Math.sin(x * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

// The loop (P = 7 s)

export const BURST_AT = 1.45;
export const LAND_AT = 5.75;

/** The landing hop of the top half, in pt. */
export function hop(tau: number) {
  'worklet';
  return tau < 0 || tau > 0.9 ? 0 : 7 * Math.exp(-7 * tau) * Math.abs(Math.sin(11 * tau));
}

/**
 Rest 0–1 s → the top half presses, then lifts on a spring (1.15 s) → the bottle rises out of the
 fruit (1.3 s) in a ring of juice drops (1.45 s) → it hovers → sinks back (4.6–5.5 s) → the top
 closes (5.0–5.75 s) and lands with a small hop and a few drops → rest 6.3–7 s.
 */
export function loopFrame(t: number): Frame {
  'worklet';
  const f = emptyFrame();
  const press = easeInOutSine(seg(t, 1.0, 1.15)) * (1 - easeOutCubic(seg(t, 1.15, 1.35)));
  const up = springStep(t - 1.15, 0.62, 0.62);
  f.open = up * (1 - easeInCubic(seg(t, 5.0, 5.75)));
  const lift = springStep(t - 1.3, 0.55, 0.7);
  f.rise = lift * (1 - easeInOutCubic(seg(t, 4.6, 5.5)));
  f.squash = 5 * press - hop(t - LAND_AT) * (1 - smoothstep(seg(t, 6.2, 6.6)));
  f.glow = easeOutCubic(seg(t, 1.3, 2.2)) * (1 - easeInOutCubic(seg(t, 4.6, 5.4)));
  f.drift = easeOutCubic(seg(t, 1.3, 2.6)) * (1 - easeInOutCubic(seg(t, 4.6, 6.0)));
  if (t > 2.7 && t < 3.6) f.sheen = easeInOutSine(seg(t, 2.7, 3.6));
  const a = t - BURST_AT;
  if (a >= 0 && a < 1.9) f.bursts.push({ age: a, strength: 1 });
  const b = t - LAND_AT;
  if (b >= 0 && b < 1.2) f.bursts.push({ age: b, strength: 0.45 });
  return f;
}

// Juice drops

export const DROP_COUNT = 54;
export const GLOB_COUNT = 7;
const GRAVITY = 1300;

export type Drop = { x: number; y: number; vx: number; vy: number; r: number; alpha: number };

/** Drop `i` of a burst at `age` s (null once it has faded); rx, ry = the cut ellipse's radii in pt. */
export function dropAt(i: number, age: number, strength: number, rx: number, ry: number): Drop | null {
  'worklet';
  const glob = i >= DROP_COUNT;
  const h1 = hash(i * 1.37 + 0.11);
  const h2 = hash(i * 2.71 + 0.37);
  const h3 = hash(i * 0.93 + 0.53);
  const h4 = hash(i * 3.17 + 0.71);
  const h5 = hash(i * 1.91 + 0.29);
  const life = (glob ? 1.25 + 0.35 * h5 : 0.95 + 0.6 * h5) * (strength < 0.7 ? 0.72 : 1);
  if (age > life) return null;
  const n = glob ? GLOB_COUNT : DROP_COUNT;
  const k = glob ? i - DROP_COUNT : i;
  const th = (2 * Math.PI * (k + 0.6 * h1)) / n + (glob ? 0.4 : 0);
  const sx = Math.cos(th) * rx * 0.92;
  const sy = Math.sin(th) * ry * 0.92;
  const speed = strength * (glob ? 0.62 : 1);
  const vx0 = Math.cos(th) * (70 + 190 * h2) * speed;
  const vy0 = -(250 + 330 * h3) * speed * (0.8 + 0.2 * Math.abs(Math.cos(th)));
  const r = (glob ? 5.5 + 3.5 * h4 : 2.0 + 3.8 * h4 * h4) * (0.75 + 0.25 * strength);
  const fadeIn = Math.min(1, age / 0.05);
  const fadeOut = 1 - Math.pow(age / life, 3);
  return {
    x: sx + vx0 * age,
    y: sy + vy0 * age + 0.5 * GRAVITY * age * age,
    vx: vx0,
    vy: vy0 + GRAVITY * age,
    r,
    alpha: fadeIn * fadeOut,
  };
}

// The finger: drag up to lift (the bottle follows), release to spring back, tap for a hop and a
// splash. Two seconds after the last touch the loop starts again from rest.

export const IDLE = 2;
export const DRAG_SPAN = 200; // pt of upward drag for open = 1

export type TouchBurst = { clock: number; strength: number };

export type Touch = {
  touching: boolean;
  startOpen: number;
  dragOpen: number;
  armed: boolean;
  releaseClock: number;
  releaseOpen: number;
  releaseRise: number;
  landClock: number;
  tapClock: number;
  lastClock: number; // last touch event (press, release or tap)
  bursts: TouchBurst[];
  hapticDue: number[];
};

export function initialTouch(): Touch {
  'worklet';
  return {
    touching: false,
    startOpen: 0,
    dragOpen: 0,
    armed: true,
    releaseClock: -100,
    releaseOpen: 0,
    releaseRise: 0,
    landClock: -100,
    tapClock: -100,
    lastClock: -100,
    bursts: [],
    hapticDue: [],
  };
}

function copyTouch(s: Touch): Touch {
  'worklet';
  const bursts: TouchBurst[] = [];
  for (let i = 0; i < s.bursts.length; i++) bursts.push({ clock: s.bursts[i].clock, strength: s.bursts[i].strength });
  const due: number[] = [];
  for (let i = 0; i < s.hapticDue.length; i++) due.push(s.hapticDue[i]);
  return {
    touching: s.touching,
    startOpen: s.startOpen,
    dragOpen: s.dragOpen,
    armed: s.armed,
    releaseClock: s.releaseClock,
    releaseOpen: s.releaseOpen,
    releaseRise: s.releaseRise,
    landClock: s.landClock,
    tapClock: s.tapClock,
    lastClock: s.lastClock,
    bursts,
    hapticDue: due,
  };
}

export function restsAfter(s: Touch) {
  'worklet';
  return s.lastClock + IDLE;
}

/** Loop time, or −1 while the finger owns the scene. */
export function loopTime(s: Touch, clock: number, autoPlay: boolean) {
  'worklet';
  if (s.touching || clock < s.lastClock + IDLE) return -1;
  if (!autoPlay) return 0;
  const base = s.lastClock > -50 ? s.lastClock + IDLE : 0;
  return mod(clock - base, LOOP);
}

/** A quick hop of the top half for a tap: up and down in 0.42 s. */
function tapPulse(tau: number) {
  'worklet';
  return tau < 0 || tau > 0.42 ? 0 : 0.3 * Math.sin((Math.PI * tau) / 0.42);
}

export function touchFrame(s: Touch, clock: number, autoPlay: boolean): Frame {
  'worklet';
  const t = loopTime(s, clock, autoPlay);
  if (t >= 0) return autoPlay ? loopFrame(t) : emptyFrame();
  const f = emptyFrame();
  const tap = tapPulse(clock - s.tapClock);
  if (s.touching) {
    f.open = s.dragOpen;
    f.rise = Math.max(0, s.dragOpen);
  } else {
    const tau = clock - s.releaseClock;
    f.open = s.releaseOpen * springDecay(tau, 0.78, 0.5) + tap;
    f.rise = s.releaseRise * springDecay(tau, 0.62, 0.62) + 0.55 * tap;
  }
  f.squash = -hop(clock - s.landClock) * 0.8;
  f.glow = clamp(f.rise, 0, 1);
  f.drift = clamp(f.open, 0, 1);
  for (let i = 0; i < s.bursts.length; i++) {
    const b = s.bursts[i];
    if (clock >= b.clock && clock - b.clock < 1.9) f.bursts.push({ age: clock - b.clock, strength: b.strength });
  }
  return f;
}

function withBurst(s: Touch, clock: number, strength: number) {
  'worklet';
  const kept: TouchBurst[] = [];
  for (let i = 0; i < s.bursts.length; i++) if (clock - s.bursts[i].clock <= 2) kept.push(s.bursts[i]);
  kept.push({ clock, strength });
  s.bursts = kept;
  s.hapticDue.push(clock);
}

export function beginTouch(prev: Touch, clock: number, autoPlay: boolean): Touch {
  'worklet';
  // Carry the scene as it is on screen into the drag.
  const now = touchFrame(prev, clock, autoPlay);
  const s = copyTouch(prev);
  s.bursts = [];
  for (let i = 0; i < now.bursts.length; i++) s.bursts.push({ clock: clock - now.bursts[i].age, strength: now.bursts[i].strength });
  s.startOpen = clamp(now.open, 0, 1.2);
  s.dragOpen = s.startOpen;
  s.armed = s.startOpen < 0.55;
  s.touching = true;
  s.lastClock = clock;
  return s;
}

/** `dy` is the finger's vertical travel in pt (negative = up). */
export function dragTouch(prev: Touch, clock: number, dy: number): Touch {
  'worklet';
  if (!prev.touching) return prev;
  const s = copyTouch(prev);
  const raw = s.startOpen - dy / DRAG_SPAN;
  if (raw > 1) {
    const over = raw - 1;
    s.dragOpen = 1 + over / (1 + over / 0.35);
  } else if (raw < 0) {
    s.dragOpen = raw / (1 - raw / 0.04);
  } else {
    s.dragOpen = raw;
  }
  if (s.armed && s.dragOpen > 0.55) {
    s.armed = false;
    withBurst(s, clock, 1);
  } else if (s.dragOpen < 0.3) {
    s.armed = true;
  }
  s.lastClock = clock;
  return s;
}

export function endTouch(prev: Touch, clock: number, tapped: boolean): Touch {
  'worklet';
  if (!prev.touching) return prev;
  const s = copyTouch(prev);
  s.touching = false;
  s.releaseClock = clock;
  s.releaseOpen = s.dragOpen;
  s.releaseRise = Math.max(0, s.dragOpen);
  s.lastClock = clock;
  if (tapped) {
    s.tapClock = clock;
    withBurst(s, clock + 0.08, 0.7);
    s.landClock = clock + 0.42;
    s.hapticDue.push(clock + 0.42);
    s.lastClock = clock + 0.42;
  } else if (s.releaseOpen > 0.3) {
    // When the closing spring reaches the fruit: a hop, a few drops and a light tap.
    let tau = 0;
    while (tau < 1.5 && s.releaseOpen * springDecay(tau, 0.78, 0.5) > 0.03) tau += 1 / 120;
    s.landClock = clock + tau;
    withBurst(s, s.landClock, 0.45);
  }
  return s;
}

/** Removes due haptics; `fire` tells whether one was due. */
export function takeHaptic(prev: Touch, clock: number): { next: Touch; fire: boolean } {
  'worklet';
  let fire = false;
  for (let i = 0; i < prev.hapticDue.length; i++) if (prev.hapticDue[i] <= clock) fire = true;
  if (!fire) return { next: prev, fire };
  const s = copyTouch(prev);
  const left: number[] = [];
  for (let i = 0; i < prev.hapticDue.length; i++) if (prev.hapticDue[i] > clock) left.push(prev.hapticDue[i]);
  s.hapticDue = left;
  return { next: s, fire };
}
