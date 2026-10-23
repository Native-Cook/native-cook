// Timed from the pin's frames (46 fps; t = 0 at the cut to the splash). Every value on screen is a
// pure function of loop time, so the loop is exact and any moment can be frozen for a pixel check.
export const LOOP_MS = 4630; // the pin's own cycle
export const RESUME_MS = 3500;
export const HOLD_MS = 3700; // where a touch holds the loop: the buttons are up and settled

export const CUE = {
  intro: 1600, // title in: fades and slides down, sharp (as the pin's frames show)
  markOut: 1480, // splash mark out
  cards: [1870, 2035, 2146], // left, centre, right rise in
  morph: 2420, // "Someone" -> "Something"
  cta: 3140, // row and title go up, buttons come in
  titleOut: 3050, // the headline fades just before the row moves
  buttons: 3110,
};

// The headline's lines slide down into place, the second one further (8.7 and 18 pt), fading in.
export const INTRO = { markOutMs: 150, titleInMs: 140, dropAt: 1660, dropMs: 100, drop1: 8.7, drop2: 18 };
// The pin's springs, measured: the cards rise with about 2 % overshoot (omega 17.3, zeta 0.81);
// the lift (tracked frame by frame) starts at 3.16 s and peaks 4.6 % over at 3.38 s (omega 20, zeta 0.70).
export const RISE = { grow: 0.12, from: 260, omega: 17.3, zeta: 0.81, fadeMs: 220 };
export const MORPH = { ms: 220, blur: 6 };
// the cards lift left to right, 37-39 ms apart (the pin's frames show them staggered)
export const LIFT = {
  stagger: [-37, 0, 39],
  omega: 20,
  zeta: 0.7,
  titleFadeMs: 140,
  buttonsFrom: 34,
  buttonsFadeMs: 60,
};

export const SNAP = { duration: 400, dampingRatio: 0.8 }; // the row after a drag, with its velocity
export const PROJECT_S = 0.15; // release target = position + velocity x this
export const RUBBER = 0.3;
export const PRESS = { scale: 0.97, ms: 120 };
export const FADE_MS = 200; // Reduce Motion

// ---- pure time functions ----
function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}
// Reanimated's default timing curve (ease in-out quad), as the copy used.
function ease(u: number) {
  'worklet';
  const x = clamp01(u);
  return x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
}
function ramp(t: number, at: number, ms: number) {
  'worklet';
  return ease((t - at) / ms);
}
function easeOut(u: number) {
  'worklet';
  const x = clamp01(u);
  return 1 - (1 - x) * (1 - x) * (1 - x);
}

// Underdamped spring released from 1 toward 0 at time `at` (1 before it).
function spring(t: number, at: number, omega: number, zeta: number) {
  'worklet';
  const s = (t - at) / 1000;
  if (s <= 0) return 1;
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  return Math.exp(-zeta * omega * s) * (Math.cos(wd * s) + ((zeta * omega) / wd) * Math.sin(wd * s));
}

export type Beat = {
  mark: number;
  titleIn: number;
  line1Drop: number;
  line2Drop: number;
  morph: number;
  lift: number;
  titleOut: number;
  buttonsIn: number;
  buttonsY: number;
};

export function beatAt(t: number): Beat {
  'worklet';
  return {
    mark: 1 - ramp(t, CUE.markOut, INTRO.markOutMs),
    titleIn: ramp(t, CUE.intro, INTRO.titleInMs),
    line1Drop: -INTRO.drop1 * (1 - easeOut((t - INTRO.dropAt) / INTRO.dropMs)),
    line2Drop: -INTRO.drop2 * (1 - easeOut((t - INTRO.dropAt) / INTRO.dropMs)),
    morph: ramp(t, CUE.morph, MORPH.ms),
    lift: 1 - spring(t, CUE.cta, LIFT.omega, LIFT.zeta),
    titleOut: 1 - ramp(t, CUE.titleOut, LIFT.titleFadeMs),
    buttonsIn: ramp(t, CUE.buttons, LIFT.buttonsFadeMs),
    buttonsY: LIFT.buttonsFrom * spring(t, CUE.buttons, LIFT.omega, LIFT.zeta),
  };
}

// Card i (0 left, 1 centre, 2 right): its rise offset (pt) and opacity at time t.
export function cardAt(t: number, i: number) {
  'worklet';
  const at = CUE.cards[i];
  const rest = spring(t, at, RISE.omega, RISE.zeta);
  return {
    rise: RISE.from * rest,
    scale: 1 - RISE.grow * rest, // the cards grow from 0.88 as they rise
    shown: Math.pow(clamp01((t - at) / RISE.fadeMs), 1.5), // the pin's cards stay see-through while they rise
    lift: 1 - spring(t, CUE.cta + LIFT.stagger[i], LIFT.omega, LIFT.zeta),
  };
}
