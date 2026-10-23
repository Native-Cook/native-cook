// Timed from the pin's frames (30 fps, frame n at (n - 1) / 30 s). Every value is a pure function of
// the loop time, so the loop is exact and any moment can be frozen for a pixel comparison.
import { FIRST, ITEMS } from './items';

const LAST = ITEMS.length - 1;

export const LOOP_MS = 15267; // the pin's own length
export const RESUME_MS = 3500;
export const HOLD_MS = 9300; // where a touch holds the loop: the last card at rest

// the carousel moves one card left at each of these times (the pin's finger taps); each move follows
// the curve measured frame by frame on the pin (progress every 33.3 ms from the onset)
export const SHIFTS = [367, 1733, 2900, 4067, 5300, 6500, 7900];
const STEP_MS = 1000 / 30;
const SHIFT_CURVE = [0, 0.024, 0.09, 0.19, 0.32, 0.46, 0.6, 0.73, 0.85, 0.94, 0.99, 1];

export const SNAP = { duration: 400, dampingRatio: 0.8 };
export const RUBBER = 0.3;
export const PROJECT_S = 0.15;

function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}
function quadOut(u: number) {
  'worklet';
  const x = clamp01(u);
  return 1 - (1 - x) * (1 - x);
}
function quadIn(u: number) {
  'worklet';
  const x = clamp01(u);
  return x * x;
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
// piecewise-linear through keyframes [t, v]
function keys(t: number, k: number[][]) {
  'worklet';
  if (t <= k[0][0]) return k[0][1];
  for (let i = 1; i < k.length; i++) {
    if (t <= k[i][0]) {
      const u = (t - k[i - 1][0]) / (k[i][0] - k[i - 1][0]);
      return k[i - 1][1] + (k[i][1] - k[i - 1][1]) * u;
    }
  }
  return k[k.length - 1][1];
}

function shiftProgress(dt: number) {
  'worklet';
  if (dt <= 0) return 0;
  const k = dt / STEP_MS;
  const i = Math.floor(k);
  if (i >= SHIFT_CURVE.length - 1) return 1;
  return SHIFT_CURVE[i] + (SHIFT_CURVE[i + 1] - SHIFT_CURVE[i]) * (k - i);
}

// the continuous index of the card at the centre
export function carouselAt(t: number) {
  'worklet';
  let s = FIRST;
  for (let i = 0; i < SHIFTS.length; i++) s += shiftProgress(t - SHIFTS[i]);
  return Math.min(s, LAST);
}

// The carousel's exit: it draws in (0.83), springs back (0.95), then collapses into the centre.
const GROUP_SCALE = [
  [9420, 1],
  [9670, 0.83],
  [9830, 0.95],
  [10030, 0.04],
];
// The end logo pops in from the centre, dips once and settles (sizes measured frame by frame).
const END_LOGO = [
  [12667, 0.02],
  [12700, 0.16],
  [12767, 0.34],
  [12833, 0.59],
  [12900, 0.91],
  [12967, 0.9],
  [13033, 0.85],
  [13100, 0.85],
  [13167, 0.92],
  [13233, 0.98],
  [13367, 1],
];

export const OUTRO = {
  wordsFrom: -477, // pt: TAKE A / BITE slide in from off the left edge
  wordsIn: [10433, 11033],
  wordsOut: [12233, 12566],
  wordsOutBy: -614, // pt, accelerating off to the left
  dontIn: [10170, 10370],
  dontOut: [12370, 12500],
  logoOut: [12230, 12370],
};

export function outroAt(t: number) {
  'worklet';
  const g = keys(t, GROUP_SCALE);
  return {
    groupScale: g,
    groupOpacity: 1 - seg(t, 9930, 10030),
    dont: seg(t, OUTRO.dontIn[0], OUTRO.dontIn[1]) * (1 - seg(t, OUTRO.dontOut[0], OUTRO.dontOut[1])),
    wordsX:
      OUTRO.wordsFrom * (1 - quadOut(seg(t, OUTRO.wordsIn[0], OUTRO.wordsIn[1]))) +
      OUTRO.wordsOutBy * quadIn(seg(t, OUTRO.wordsOut[0], OUTRO.wordsOut[1])),
    wordsShown: t >= OUTRO.wordsIn[0] && t < OUTRO.wordsOut[1] ? 1 : 0,
    topLogo: 1 - seg(t, OUTRO.logoOut[0], OUTRO.logoOut[1]),
    endLogo: t >= END_LOGO[0][0] ? keys(t, END_LOGO) : 0,
  };
}
