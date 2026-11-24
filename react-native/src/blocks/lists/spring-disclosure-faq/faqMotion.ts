// Spring Disclosure FAQ: the film and the spring, as pure worklet functions.
//
// Every row has one number, `open` (0 closed, 1 open, a little past either while the spring
// overshoots). Everything on screen is drawn from the six `open` values sampled at a time:
//   - a row's answer is revealed by the rows below sliding down by H * open,
//   - each row below samples the same value FOLLOW_LAG later than the row above it (follow-through),
//   - line k of an answer samples it LINE_STAGGER * k later (the lines rise one after another),
//   - the "+" turns into a "−" with it.
// The film is a sum of spring steps at fixed times, so it is a function of the loop time alone and
// the frame at LOOP equals the frame at 0. A finger uses the same spring from wherever a row is.
//
// No React Native imports here, so the module also runs in Node.

export const LOOP = 9; // seconds

export const ROW_COUNT = 6;
export const GROUP_SIZE = 3; // two grouped cards of three rows
export const REST_ROW = 0; // the row that is open at rest

// The spring (SwiftUI: .spring(response: 0.46, dampingFraction: 0.72)); about 4 % overshoot.
export const SPRING_RESPONSE = 0.46; // seconds
export const SPRING_DAMPING = 0.72; // damping ratio
const OMEGA = (2 * Math.PI) / SPRING_RESPONSE;
const OMEGA_D = OMEGA * Math.sqrt(1 - SPRING_DAMPING * SPRING_DAMPING);
const DECAY = SPRING_DAMPING * OMEGA;

export const FOLLOW_LAG = 0.03; // s, each row below starts this much after the one above it
export const LINE_STAGGER = 0.035; // s between the lines of an answer
export const LINE_FROM = 0.15; // an answer line fades in while its sample of `open` goes
export const LINE_TO = 0.85; //   from LINE_FROM to LINE_TO
export const LINE_RISE = 10; // pt a line travels up
export const LINE_BLUR = 6; // pt of blur a line starts from
export const REDUCED_FADE = 0.18; // s, the plain linear change under Reduce Motion

// The film: at EVENT_T[k] the ghost finger taps EVENT_ROW[k]; that row opens and EVENT_PREV[k] closes.
export const EVENT_T = [1.1, 3.7, 6.3];
export const EVENT_ROW = [2, 4, 0];
export const EVENT_PREV = [0, 2, 4];

// The ghost finger around an event at time e.
export const TOUCH_IN = 0.3; // s before e the dot starts to appear
export const TOUCH_DOWN = 0.08; // s before e it presses
export const TOUCH_UP = 0.12; // s after e it lifts
export const TOUCH_OUT = 0.4; // s after e it is gone

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

/** 0 before a, 1 after b. */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function smooth(x: number) {
  'worklet';
  const c = clamp01(x);
  return c * c * (3 - 2 * c);
}

/**
 * An underdamped spring from `from` towards `to`, `s` seconds after it was released with velocity
 * `v0` (units per second). Before the release (s <= 0) it is still at `from`.
 */
export function springTo(from: number, to: number, v0: number, s: number) {
  'worklet';
  if (s <= 0) return from;
  const a = from - to;
  const b = (v0 + DECAY * a) / OMEGA_D;
  return to + Math.exp(-DECAY * s) * (a * Math.cos(OMEGA_D * s) + b * Math.sin(OMEGA_D * s));
}

/** `open` of row j at loop time t (t may be negative for a lagged sample: the rest state). */
export function filmOpen(j: number, t: number) {
  'worklet';
  let v = j === REST_ROW ? 1 : 0;
  for (let k = 0; k < EVENT_T.length; k++) {
    const s = t - EVENT_T[k];
    if (s <= 0) continue;
    const step = springTo(0, 1, 0, s);
    if (EVENT_ROW[k] === j) v += step;
    else if (EVENT_PREV[k] === j) v -= step;
  }
  return v;
}

/** Where row j is heading at loop time t: 1 when it is the open row, else 0. */
export function filmTarget(j: number, t: number) {
  'worklet';
  let row = REST_ROW;
  for (let k = 0; k < EVENT_T.length; k++) {
    if (t >= EVENT_T[k]) row = EVENT_ROW[k];
  }
  return row === j ? 1 : 0;
}

/** Opacity of the ghost finger's dot for event k at loop time t. */
export function touchAlpha(k: number, t: number) {
  'worklet';
  const e = EVENT_T[k];
  return smooth(seg(t, e - TOUCH_IN, e - TOUCH_DOWN)) * (1 - smooth(seg(t, e + TOUCH_UP, e + TOUCH_OUT)));
}

/** 0..1, how far the ghost finger is pressed for event k at loop time t. */
export function touchPress(k: number, t: number) {
  'worklet';
  const e = EVENT_T[k];
  return smooth(seg(t, e - TOUCH_DOWN, e)) * (1 - smooth(seg(t, e + TOUCH_UP, e + TOUCH_UP + 0.16)));
}

/** How visible answer line k is, from its (lagged) sample of the row's `open`. */
export function lineReveal(open: number) {
  'worklet';
  return smooth((open - LINE_FROM) / (LINE_TO - LINE_FROM));
}

// ---------------------------------------------------------------- a finger's transitions

/** The last transition of every row: from -> to, released at t0 (seconds) with velocity v0. */
export type Transitions = { from: number[]; to: number[]; v0: number[]; t0: number[] };

export const MODE_AUTO = 0; // the film; `tau` is the loop time
export const MODE_MANUAL = 1; // a finger; `tau` is the frame clock in seconds
export const MODE_RETURN = 2; // easing back to rest before the film restarts

export function restTransitions(): Transitions {
  'worklet';
  const from: number[] = [];
  const to: number[] = [];
  const v0: number[] = [];
  const t0: number[] = [];
  for (let j = 0; j < ROW_COUNT; j++) {
    const v = j === REST_ROW ? 1 : 0;
    from.push(v);
    to.push(v);
    v0.push(0);
    t0.push(0);
  }
  return { from, to, v0, t0 };
}

/** `open` of row j at time tau, in the film or under a finger. */
export function openAt(mode: number, tr: Transitions, reduced: boolean, j: number, tau: number) {
  'worklet';
  if (mode === MODE_AUTO) return filmOpen(j, tau);
  const s = tau - tr.t0[j];
  if (reduced) return tr.from[j] + (tr.to[j] - tr.from[j]) * clamp01(s / REDUCED_FADE);
  return springTo(tr.from[j], tr.to[j], tr.v0[j], s);
}

/**
 * How far (pt) the element at row position `target` is pushed down by the answers of rows
 * first..last above it: each answer's height times its `open`, sampled FOLLOW_LAG later per row of
 * distance, so the rows below follow one after another.
 */
export function pushAt(
  mode: number,
  tr: Transitions,
  reduced: boolean,
  heights: number[],
  first: number,
  last: number,
  target: number,
  tau: number,
) {
  'worklet';
  let sum = 0;
  for (let j = first; j <= last; j++) {
    const lag = reduced ? 0 : FOLLOW_LAG * (target - j);
    sum += heights[j] * openAt(mode, tr, reduced, j, tau - lag);
  }
  return sum;
}

/**
 * The transitions after a finger toggles row `tapped` at time `now`: every row whose target changes
 * starts a new spring from where it is, with the velocity it has. `mode`/`tau` describe the state
 * being left (the film or an earlier transition).
 */
export function retarget(mode: number, tr: Transitions, reduced: boolean, tau: number, now: number, tapped: number) {
  'worklet';
  const DT = 1 / 120;
  const from: number[] = [];
  const to: number[] = [];
  const v0: number[] = [];
  const t0: number[] = [];
  let openRow = -1;
  for (let j = 0; j < ROW_COUNT; j++) {
    const heading = mode === MODE_AUTO ? filmTarget(j, tau) : tr.to[j];
    if (heading > 0.5) openRow = j;
  }
  // tapped < 0: back to rest. Tapping the open row closes it (nothing open).
  const next = tapped < 0 ? REST_ROW : tapped === openRow ? -1 : tapped;
  for (let j = 0; j < ROW_COUNT; j++) {
    const heading = mode === MODE_AUTO ? filmTarget(j, tau) : tr.to[j];
    const goal = j === next ? 1 : 0;
    const cur = openAt(mode, tr, reduced, j, tau);
    if (goal === heading && mode !== MODE_AUTO) {
      // untouched: keep the spring it is on
      from.push(tr.from[j]);
      to.push(tr.to[j]);
      v0.push(tr.v0[j]);
      t0.push(tr.t0[j]);
    } else {
      const before = openAt(mode, tr, reduced, j, tau - DT);
      from.push(cur);
      to.push(goal);
      v0.push((cur - before) / DT);
      t0.push(now);
    }
  }
  return { from, to, v0, t0 };
}
