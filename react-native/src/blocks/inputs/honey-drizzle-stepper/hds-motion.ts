/**
 * The clocks of the screen, stepped on the UI thread once per frame.
 *
 * In the loop every value is a pure function of the loop time t (`loopFrame`): the amount (2 → 5 → 2 tsp), the flow
 * clock (period 1.5 s, four per loop) and the number's pop: each time the number shown changes it pops 1.12 → 1.0 over 0.25 s
 * (the designer's answer 1 on #52; the reference clip gated it instead). A drag above the saucer, the − / + buttons or an
 * accessibility step take over at the frame on screen; a release or a step eases to a whole tsp in 0.3 s. 2 s after the
 * last touch the amount eases back to 2 and the loop restarts at t = 0, the flow carrying on from its phase and the
 * number's pop easing on to 1.0. Reduce Motion: the original photograph (no flow, growth or warps), no pop; the amount
 * still eases.
 */

/** Loop period, s. */
export const LOOP = 6.0;
/** The flow's period, s: four per loop, so the loop closes. */
const FLOW = 1.5;
const MIN = 1;
const MAX = 5;
const REST = 2;
/** Drag: one tsp per 60 pt. */
const DRAG_TSP = 60;
const EASE = 0.3;
const IDLE = 2.0;
const POP = 0.12;
const POP_TIME = 0.25;

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function io(x: number) {
  'worklet';
  return x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
}
function oc(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}
export function amountAt(t: number) {
  'worklet';
  if (t < 0.6) return 2;
  if (t < 2.4) return 2 + 3 * io(seg(t, 0.6, 2.4));
  if (t < 3.4) return 5;
  if (t < 5.0) return 5 - 3 * io(seg(t, 3.4, 5.0));
  return 2;
}

/** The inverse of `io`. */
function ioInv(y: number) {
  'worklet';
  const v = clamp01(y);
  return v < 0.5 ? Math.sqrt(v / 2) : 1 - Math.sqrt((1 - v) / 2);
}

/** When the loop's number changes: the amount crosses 2.5, 3.5 and 4.5 on the way up (0.6–2.4 s) and down (3.4–5.0 s). */
const CHANGES = [1 / 6, 1 / 2, 5 / 6].map((y) => 0.6 + 1.8 * ioInv(y)).concat([1 / 6, 1 / 2, 5 / 6].map((y) => 3.4 + 1.6 * ioInv(y)));

/** The number's scale `age` s after it changed. */
function popOf(age: number) {
  'worklet';
  return age < POP_TIME ? 1 + POP * (1 - oc(age / POP_TIME)) : 1;
}

/** How long ago the loop's number last changed at loop time t (s; large if it has not yet in this loop). */
function changedAgo(t: number) {
  'worklet';
  let ago = 1e3;
  for (let i = 0; i < CHANGES.length; i++) if (CHANGES[i] <= t) ago = Math.min(ago, t - CHANGES[i]);
  return ago;
}

export type Frame = {
  /** Amount, tsp (continuous); the flow clock, s; Reduce Motion 0 / 1. */
  amt: number;
  T: number;
  still: number;
  /** The number shown and its scale. */
  n: number;
  pop: number;
};

/** carried: the age of a pop the loop carries from before it restarted, s (large: none). */
export function loopFrame(t: number, flowFrom = 0, carried = 1e3): Frame {
  'worklet';
  const a = amountAt(t);
  return { amt: a, T: (t + flowFrom) % LOOP, still: 0, n: Math.round(a), pop: popOf(Math.min(changedAgo(t), carried)) };
}

export const MODE = { auto: 0, live: 1, still: 2 } as const;

export type Core = {
  mode: number;
  last: number;
  start: number;
  /** Where the flow clock was when the loop restarted, s, and the age of the number's pop then (large: none). */
  flowFrom: number;
  carry: number;
  /** Live: the amount, the flow clock, and how long ago the number shown changed. */
  amt: number;
  T: number;
  popAge: number;
  dragging: boolean;
  dragFrom: number;
  /** Easing from `e0` to `e1` (age −1: not easing). */
  e0: number;
  e1: number;
  age: number;
  /** The gallery is easing back: no ticks. */
  homing: boolean;
  touches: number;
  idle: number;
  /** The number shown, and the ticks for the JS side (one per change under the finger or a step). */
  shown: number;
  ticks: number;
  out: Frame;
};

export function newCore(): Core {
  return {
    mode: MODE.auto, last: -1, start: -1, flowFrom: 0, carry: 1e3, amt: REST, T: 0, popAge: 1e3, dragging: false, dragFrom: REST,
    e0: REST, e1: REST, age: -1, homing: false, touches: 0, idle: 0, shown: REST, ticks: 0, out: loopFrame(0),
  };
}

/** Leave the loop at the frame on screen. */
function takeOver(c: Core) {
  'worklet';
  if (c.mode !== MODE.auto) return;
  const since = c.start < 0 ? 0 : (c.last - c.start) / 1000;
  const t = since % LOOP;
  c.mode = MODE.live;
  c.amt = amountAt(t);
  c.T = (t + c.flowFrom) % LOOP;
  c.popAge = Math.min(changedAgo(t), c.carry + since);
  c.shown = Math.round(c.amt);
  c.age = -1;
}

function easeTo(c: Core, to: number) {
  'worklet';
  c.e0 = c.amt;
  c.e1 = to;
  c.age = 0;
}

function liveFrame(c: Core): Frame {
  'worklet';
  const still = c.mode === MODE.still;
  return { amt: c.amt, T: still ? 0 : c.T, still: still ? 1 : 0, n: Math.round(c.amt), pop: still ? 1 : popOf(c.popAge) };
}

export function touchDown(c: Core) {
  'worklet';
  takeOver(c);
  c.touches += 1;
  c.idle = 0;
  c.homing = false;
}

export function touchUp(c: Core) {
  'worklet';
  c.touches = Math.max(0, c.touches - 1);
  c.idle = 0;
}

export function dragStart(c: Core) {
  'worklet';
  takeOver(c);
  c.dragging = true;
  c.homing = false;
  c.age = -1;
  c.dragFrom = c.amt;
}

/** dy: the finger's travel down since the drag began, pt. */
export function dragTo(c: Core, dy: number) {
  'worklet';
  if (!c.dragging) return;
  c.amt = Math.min(MAX, Math.max(MIN, c.dragFrom - dy / DRAG_TSP));
}

export function dragEnd(c: Core) {
  'worklet';
  if (!c.dragging) return;
  c.dragging = false;
  c.idle = 0;
  easeTo(c, Math.round(c.amt));
}

/** The − / + buttons and the accessibility steps: one tsp from where the amount is going. */
export function stepBy(c: Core, dir: number) {
  'worklet';
  takeOver(c);
  c.idle = 0;
  c.homing = false;
  const base = c.age >= 0 ? c.e1 : Math.round(c.amt);
  const to = Math.min(MAX, Math.max(MIN, base + dir));
  if (to !== c.amt) easeTo(c, to);
}

export function step(c: Core, now: number, autoPlay: boolean) {
  'worklet';
  const dt = c.last < 0 ? 0 : Math.min(Math.max((now - c.last) / 1000, 0), 0.05);

  if (c.mode === MODE.auto) {
    if (autoPlay) {
      c.last = now;
      if (c.start < 0) c.start = now;
      const since = (now - c.start) / 1000;
      c.out = loopFrame(since % LOOP, c.flowFrom, c.carry + since);
      return;
    }
    // Paused: leave the loop at the frame on screen; the step below moves it on by dt.
    takeOver(c);
  }
  c.last = now;

  if (c.mode !== MODE.still) c.T = (c.T + dt) % LOOP;
  if (!c.dragging && c.age >= 0) {
    c.age = Math.min(EASE, c.age + dt);
    c.amt = c.e0 + (c.e1 - c.e0) * oc(c.age / EASE);
    if (c.age >= EASE) {
      c.amt = c.e1;
      c.age = -1;
    }
  }
  const moving = c.dragging || c.age >= 0;
  c.popAge += dt;
  const n = Math.round(c.amt);
  if (n !== c.shown) {
    c.shown = n;
    c.popAge = 0;
    if (!c.homing) c.ticks += 1;
  }
  c.out = liveFrame(c);

  if (c.touches > 0 || moving) {
    if (!c.homing) c.idle = 0;
    if (moving) return;
  }
  if (c.touches > 0) return;
  c.idle += dt;
  if (!autoPlay || c.mode === MODE.still || c.idle < IDLE) return;
  if (c.amt !== REST) {
    c.homing = true;
    easeTo(c, REST);
    return;
  }
  // Back to the loop at t = 0 (2 tsp), the honey flowing on from its phase and the number's pop easing on.
  c.mode = MODE.auto;
  c.homing = false;
  c.start = now;
  c.flowFrom = c.T % FLOW;
  c.carry = c.popAge;
  c.out = loopFrame(0, c.flowFrom, c.carry);
}

export function setStill(c: Core, still: boolean) {
  'worklet';
  if (still && c.mode !== MODE.still) {
    c.amt = c.mode === MODE.auto ? REST : Math.round(c.amt);
    c.mode = MODE.still;
    c.age = -1;
    c.dragging = false;
    c.shown = Math.round(c.amt);
    c.out = liveFrame(c);
  } else if (!still && c.mode === MODE.still) {
    c.mode = MODE.auto;
    c.start = -1;
    c.flowFrom = 0;
    c.carry = 1e3;
    c.amt = REST;
    c.age = -1;
  }
}
