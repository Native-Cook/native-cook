// Clay Relight Settings — the evening as a function of one number, and the autoPlay loop. Helpers
// first: worklets capture what is above them.

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function smooth(a: number, b: number, x: number) {
  'worklet';
  const u = clamp01((x - a) / (b - a));
  return u * u * (3 - 2 * u);
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}
/** A critically damped step from 0 to 1, rescaled so it lands exactly on 1 at x = 1. */
export function settleStep(x: number) {
  'worklet';
  const k = 7;
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  return (1 - (1 + k * x) * Math.exp(-k * x)) / (1 - (1 + k) * Math.exp(-k));
}

/* ------------------------------------------------------------ the evening, m = 0 … 1 */
// m = 0: early afternoon, the sun high on the left · 0.5: the sun has just set behind the far hill
// on the right · 1: night, the moon high on the left. Every light, colour and shadow on the screen
// is a function of m, so the switch, the finger on the sky and the loop all drive the same thing.
const PI = Math.PI;

/** The evening's pace: the same m spends twice as long around sunset (0.38 … 0.62) as a straight line. */
export function evening(m: number) {
  'worklet';
  const x = clamp01(m);
  return x + 0.13 * Math.sin(2 * PI * x);
}

/** The sun's angle on its arc (radians, 0 = right horizon, π/2 = overhead). */
export function sunAngle(m: number) {
  'worklet';
  return 2.12 - 3.6 * evening(m);
}
/** The moon's angle: up from the left horizon after the sun has gone. */
export function moonAngle(m: number) {
  'worklet';
  return PI + 0.16 - 1.18 * smooth(0.42, 1, evening(m));
}
/** How far into the night, for colours and the sky (0 day … 1 night). */
export function night(m: number) {
  'worklet';
  return smooth(0.42, 0.86, evening(m));
}
/** The warm band around sunset (0 … 1, peaking as the sun touches the hill). */
export function dusk(m: number) {
  'worklet';
  const e = evening(m);
  return smooth(0.12, 0.44, e) * (1 - smooth(0.5, 0.8, e));
}
/** The lamp in each icon (row 0 … 4) comes on one after the other as night falls. */
export function lamp(m: number, row: number) {
  'worklet';
  const e = evening(m);
  return smooth(0.5 + 0.045 * row, 0.6 + 0.045 * row, e);
}
/** The house's windows come on first. */
export function windows(m: number) {
  'worklet';
  return smooth(0.44, 0.56, evening(m));
}

export const LOOP = 10;

/* ------------------------------------------------------------ autoPlay */
export const T = {
  tap1: 0.9, // the switch on: sunset, night
  dusk1: 3.9,
  drag0: 4.7, // a finger pulls the sky back to dusk
  drag1: 5.6,
  release: 6.0, // let go: night returns
  back: 7.3,
  tap2: 7.7, // the switch off: sunrise
  dawn: 9.8,
} as const;
const DRAG_M = 0.52;

export type Frame = {
  m: number;
  theme: number; // the screen's chrome, 0 day … 1 night
  on: number; // the switch's state, 0 or 1
  knob: number; // the knob's travel, 0 … 1
  press: number; // the switch's press
  finger: number; // the scripted finger's presence
  fingerAt: number; // 0 the switch, 1 the sky
  fingerX: number; // along the drag, 0 … 1
};

export function mAt(t: number) {
  'worklet';
  if (t < T.tap1) return 0;
  if (t < T.drag0) return smooth(0, 1, seg(t, T.tap1, T.dusk1));
  if (t < T.release) return 1 - (1 - DRAG_M) * easeInOut(seg(t, T.drag0, T.drag1));
  if (t < T.tap2) return DRAG_M + (1 - DRAG_M) * settleStep(seg(t, T.release, T.back));
  return 1 - smooth(0, 1, seg(t, T.tap2, T.dawn));
}

// The screen's chrome steps to night when m passes THEME_UP and back when it falls under THEME_DOWN,
// as its own short fade, so text never sits at half contrast while a finger holds the sky at dusk.
// Under the finger a reaction does it; in the loop the steps are found once, at load.
export const THEME_UP = 0.62;
export const THEME_DOWN = 0.54;
export const THEME_FADE = 0.3; // s
function findSteps() {
  const out: number[] = [];
  let state = 0;
  for (let t = 0; t < LOOP; t += 0.001) {
    const m = mAt(t);
    if (state === 0 && m > THEME_UP) {
      state = 1;
      out.push(t);
    } else if (state === 1 && m < THEME_DOWN) {
      state = 0;
      out.push(t);
    }
  }
  while (out.length < 4) out.push(LOOP + 10);
  return out;
}
const STEPS = findSteps();
const STEP0 = STEPS[0];
const STEP1 = STEPS[1];
const STEP2 = STEPS[2];
const STEP3 = STEPS[3];

/** 0 day chrome … 1 night chrome, at loop time t. */
export function themeAt(t: number) {
  'worklet';
  const up = (a: number) => easeInOut(seg(t, a, a + THEME_FADE));
  return clamp01(up(STEP0) - up(STEP1) + up(STEP2) - up(STEP3));
}

export function frameAt(t: number): Frame {
  'worklet';
  const m = mAt(t);
  const on = t >= T.tap1 && t < T.tap2 ? 1 : 0;
  const knob = easeOut(seg(t, T.tap1, T.tap1 + 0.28)) * (1 - easeOut(seg(t, T.tap2, T.tap2 + 0.28)));
  const tapPress = (a: number) => easeOut(seg(t, a - 0.08, a)) * (1 - easeOut(seg(t, a + 0.06, a + 0.2)));
  const near = (a: number, b: number) => easeOut(seg(t, a - 0.25, a)) * (1 - easeOut(seg(t, b, b + 0.25)));
  const onSwitch = near(T.tap1 - 0.05, T.tap1 + 0.2) + near(T.tap2 - 0.05, T.tap2 + 0.2);
  const onSky = near(T.drag0, T.release);
  return {
    m,
    theme: themeAt(t),
    on,
    knob,
    press: clamp01(tapPress(T.tap1) + tapPress(T.tap2)),
    finger: clamp01(onSwitch + onSky),
    fingerAt: onSky > 0.001 ? 1 : 0,
    fingerX: easeInOut(seg(t, T.drag0, T.drag1)),
  };
}
