/*
 Timing and touch model of Pickle Flavour Wipe, ported from PickleFlavourWipeMotion.swift. Every
 value is a pure function of the loop time (autoPlay) or of the clock since a touch event, so the
 loop's last frame equals its first. Helpers are declared above their callers (worklets).
 */

export const LOOP = 10;
export const PAGES = 4;

/** Loop time at which each wipe starts: Mango → Lime, Lime → Garlic, Garlic → Trio, Trio → Mango. */
const WIPE_AT = [1.0, 3.25, 5.5, 7.75];
/** How long the edge takes to cross the screen. */
const SWEEP = 0.8;
/** Loop time at which each page is on screen and settled (where the loop resumes after a touch). */
const SETTLED_AT = [9.6, 3.1, 5.35, 7.6];
/** Entrance age of a page that has fully arrived. */
export const SETTLED_AGE = 4;

const IDLE = 2;
const DRAG_SPAN = 300; // pt of horizontal drag for a full wipe

/** What the art needs for one frame: page `from` lies under, page `to` is revealed by the edge. */
export type Scene = {
  from: number;
  fromAge: number; // entrance age of `from` (seconds since its own wipe began)
  to: number;
  toAge: number;
  reveal: number; // 0 … 1: how far the edge has crossed
  dir: number; // +1: the edge moves right → left (next page); −1: left → right (previous)
  fade: number; // reduced motion: cross-fade 0 → 1 instead of the edge (negative: none)
};

export function still(page: number, age: number): Scene {
  'worklet';
  return { from: page, fromAge: age, to: page, toAge: age, reveal: 0, dir: 1, fade: -1 };
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

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** A released spring's remaining share of its start value (zero start velocity). */
export function springDecay(tau: number, damping: number, response: number) {
  'worklet';
  if (tau <= 0) return 1;
  const w = (2 * Math.PI) / response;
  const wd = w * Math.sqrt(1 - damping * damping);
  return Math.exp(-damping * w * tau) * (Math.cos(wd * tau) + ((damping * w) / wd) * Math.sin(wd * tau));
}

/** A spring stepping from 0 to 1. */
export function springStep(tau: number, damping: number, response: number) {
  'worklet';
  return tau <= 0 ? 0 : 1 - springDecay(tau, damping, response);
}

export function hash(x: number) {
  'worklet';
  const s = Math.sin(x * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** A sine that repeats a whole number of times per loop. */
export function wave(t: number, k: number, phase: number) {
  'worklet';
  return Math.sin((2 * Math.PI * k * t) / LOOP + phase);
}

// Entrance of a page (age = seconds since its wipe began)

/** The jar: rises 300 pt from below, turning from +14°, on a spring. */
export function jarIn(age: number) {
  'worklet';
  return springStep(age - 0.22, 0.62, 0.6);
}

/** Ingredient `i` pops out from behind the jar. */
export function ingredientIn(age: number, i: number) {
  'worklet';
  return springStep(age - 0.45 - 0.07 * i, 0.55, 0.5);
}

/** The title slides in from the left. */
export function titleIn(age: number) {
  'worklet';
  return springStep(age - 0.5, 0.75, 0.45);
}

/** The title's speed (share of its travel per second), for the smear. */
export function titleSpeed(age: number) {
  'worklet';
  return (titleIn(age) - titleIn(age - 1 / 60)) * 60;
}

/** Subtitle and description fade up. */
export function copyIn(age: number) {
  'worklet';
  return easeOutCubic(seg(age, 0.85, 1.2));
}

/** Trio page: jar `j` of the line-up rises, staggered. */
export function trioJarIn(age: number, j: number) {
  'worklet';
  return springStep(age - 0.22 - 0.09 * j, 0.62, 0.6);
}

// The loop (P = 10 s)

/**
 Rest on Mango 0–1 s → the edge wipes Lime in (1.0–1.8 s), the jar, ingredients and title arrive →
 hold → Garlic (3.25 s) → hold → the three jars line up on the Trio page (5.5 s) → hold → Mango
 wipes back in (7.75 s) and settles by 9.5 s → rest.
 */
export function loopScene(t: number): Scene {
  'worklet';
  const n = WIPE_AT.length;
  let k = n - 1;
  while (k >= 0 && t < WIPE_AT[k]) k -= 1;
  // Before the first wipe: the last wipe (→ Mango) of the previous loop.
  const start = k >= 0 ? WIPE_AT[k] : WIPE_AT[n - 1] - LOOP;
  const wipe = k >= 0 ? k : n - 1;
  const age = t - start;
  const to = (wipe + 1) % PAGES;
  if (age < SWEEP) {
    return { from: wipe, fromAge: SETTLED_AGE, to, toAge: age, reveal: easeInOutCubic(age / SWEEP), dir: 1, fade: -1 };
  }
  return still(to, age);
}

// Touch: swipe left for the next flavour, right for the previous one; the edge follows the finger,
// release completes the wipe or springs it back. Two seconds after the last touch the loop resumes
// from the page on screen.

export type Touch = {
  touching: boolean;
  page: number; // the page that is (or is becoming) the settled one
  pageAgeBase: number; // clock at which `page`'s entrance began
  lastClock: number;
  startS: number;
  s: number; // signed wipe while dragging: + towards the next page, − towards the previous
  releaseClock: number;
  releaseFrom: number;
  releaseTo: number;
  releaseReveal: number;
  releaseDir: number;
  releaseCommit: boolean;
  releaseAge: number;
  hapticAt: number; // clock of a due haptic (negative: none)
  fadeClock: number;
  fadeFrom: number;
};

export function initialTouch(): Touch {
  'worklet';
  return {
    touching: false,
    page: 0,
    pageAgeBase: -100,
    lastClock: -100,
    startS: 0,
    s: 0,
    releaseClock: -100,
    releaseFrom: 0,
    releaseTo: 0,
    releaseReveal: 0,
    releaseDir: 1,
    releaseCommit: false,
    releaseAge: 0,
    hapticAt: -1,
    fadeClock: -100,
    fadeFrom: 0,
  };
}

function copyTouch(s: Touch): Touch {
  'worklet';
  return {
    touching: s.touching,
    page: s.page,
    pageAgeBase: s.pageAgeBase,
    lastClock: s.lastClock,
    startS: s.startS,
    s: s.s,
    releaseClock: s.releaseClock,
    releaseFrom: s.releaseFrom,
    releaseTo: s.releaseTo,
    releaseReveal: s.releaseReveal,
    releaseDir: s.releaseDir,
    releaseCommit: s.releaseCommit,
    releaseAge: s.releaseAge,
    hapticAt: s.hapticAt,
    fadeClock: s.fadeClock,
    fadeFrom: s.fadeFrom,
  };
}

export function restsAfter(s: Touch) {
  'worklet';
  return s.lastClock + IDLE;
}

/** Loop time, or a negative number while the finger owns the scene. */
function loopTime(s: Touch, clock: number, autoPlay: boolean) {
  'worklet';
  if (s.touching || clock < s.lastClock + IDLE) return -1;
  if (!autoPlay) return SETTLED_AT[s.page];
  const base = s.lastClock > -50 ? s.lastClock + IDLE - SETTLED_AT[s.page] : 0;
  return mod(clock - base, LOOP);
}

export function touchScene(s: Touch, clock: number, autoPlay: boolean): Scene {
  'worklet';
  const t = loopTime(s, clock, autoPlay);
  if (t >= 0) return loopScene(t);
  if (s.touching) {
    const reveal = Math.abs(s.s);
    if (reveal < 1e-4) return still(s.page, clock - s.pageAgeBase);
    const dir = s.s > 0 ? 1 : -1;
    const to = (s.page + (s.s > 0 ? 1 : PAGES - 1)) % PAGES;
    return { from: s.page, fromAge: clock - s.pageAgeBase, to, toAge: 0.9 * Math.min(reveal, 1), reveal, dir, fade: -1 };
  }
  const tau = clock - s.releaseClock;
  if (s.releaseCommit) {
    const reveal = 1 + (s.releaseReveal - 1) * springDecay(tau, 0.86, 0.42);
    if (tau > 1.2) return still(s.releaseTo, s.releaseAge + tau);
    return { from: s.releaseFrom, fromAge: SETTLED_AGE, to: s.releaseTo, toAge: s.releaseAge + tau, reveal, dir: s.releaseDir, fade: -1 };
  }
  const reveal = s.releaseReveal * springDecay(tau, 0.86, 0.38);
  if (tau > 1.2 || (Math.abs(reveal) < 1e-4 && tau > 0.3)) return still(s.releaseFrom, clock - s.pageAgeBase);
  return {
    from: s.releaseFrom,
    fromAge: clock - s.pageAgeBase,
    to: s.releaseTo,
    toAge: 0.9 * Math.max(0, reveal),
    reveal,
    dir: s.releaseDir,
    fade: -1,
  };
}

export function beginTouch(prev: Touch, clock: number, autoPlay: boolean): Touch {
  'worklet';
  // Carry the scene as it is on screen into the drag.
  const now = touchScene(prev, clock, autoPlay);
  const s = copyTouch(prev);
  if (now.from === now.to || now.reveal > 0.5) {
    s.page = now.to;
    s.pageAgeBase = clock - now.toAge;
    s.startS = now.from === now.to ? 0 : -now.dir * (1 - now.reveal);
  } else {
    s.page = now.from;
    s.pageAgeBase = clock - now.fromAge;
    s.startS = now.dir * now.reveal;
  }
  s.s = s.startS;
  s.touching = true;
  s.lastClock = clock;
  return s;
}

/** `dx` is the finger's horizontal travel in pt (negative = left). */
export function dragTouch(prev: Touch, clock: number, dx: number): Touch {
  'worklet';
  if (!prev.touching) return prev;
  const s = copyTouch(prev);
  const raw = s.startS - dx / DRAG_SPAN;
  const mag = Math.abs(raw);
  s.s = mag > 1 ? (raw > 0 ? 1 : -1) * (1 + (mag - 1) / (1 + (mag - 1) / 0.12)) : raw;
  s.lastClock = clock;
  return s;
}

/** `vx` is the finger's horizontal speed in pt/s. */
export function endTouch(prev: Touch, clock: number, vx: number): Touch {
  'worklet';
  if (!prev.touching) return prev;
  const s = copyTouch(prev);
  s.touching = false;
  const speed = -vx / DRAG_SPAN;
  const dir = s.s > 0 || (s.s === 0 && speed > 0) ? 1 : -1;
  const reveal = Math.abs(s.s);
  const fling = speed * dir;
  s.releaseClock = clock;
  s.releaseFrom = s.page;
  s.releaseTo = (s.page + (dir > 0 ? 1 : PAGES - 1)) % PAGES;
  s.releaseDir = dir;
  s.releaseReveal = reveal;
  s.releaseCommit = reveal > 0.35 || (fling > 2 && reveal > 0.04);
  s.releaseAge = 0.9 * Math.min(reveal, 1);
  if (s.releaseCommit) {
    s.page = s.releaseTo;
    s.pageAgeBase = clock - s.releaseAge;
    s.hapticAt = clock;
  }
  s.lastClock = clock;
  return s;
}

// Reduced motion: a swipe changes the page with a cross-fade.

export function fadeScene(s: Touch, clock: number): Scene {
  'worklet';
  const x = seg(clock - s.fadeClock, 0, 0.3);
  if (x >= 1) return still(s.page, SETTLED_AGE);
  const scene = still(s.fadeFrom, SETTLED_AGE);
  scene.to = s.page;
  scene.fade = x;
  return scene;
}

export function fadeSwipe(prev: Touch, clock: number, dx: number): Touch {
  'worklet';
  if (Math.abs(dx) <= 50) return prev;
  const s = copyTouch(prev);
  s.fadeFrom = s.page;
  s.page = (s.page + (dx < 0 ? 1 : PAGES - 1)) % PAGES;
  s.fadeClock = clock;
  s.lastClock = clock;
  s.hapticAt = clock;
  return s;
}

export function fading(s: Touch, clock: number) {
  'worklet';
  return clock - s.fadeClock < 0.35;
}

/** Fires once per due haptic. */
export function takeHaptic(prev: Touch, clock: number): { next: Touch; fire: boolean } {
  'worklet';
  if (prev.hapticAt < 0 || prev.hapticAt > clock) return { next: prev, fire: false };
  const s = copyTouch(prev);
  s.hapticAt = -1;
  return { next: s, fire: true };
}
