/*
 Veg Mode Toggle — the springs, the model and the autoPlay script (a port of
 VegModeToggleMotion.swift, same numbers). Every function is a worklet: the frame callback and the
 gesture run them on the UI thread. Helpers are declared above their callers.

 Every moving value is an analytic spring chained on events: an event restarts the spring from the
 value and the velocity it has at that moment, towards a new target. The screen is a pure function of
 those springs and the time, so the loop is exact and the three stacks match frame for frame.

 Channels: p (knob, 0 = Veg with the knob on the right, 1 = Non veg with the knob on the left),
 b / b2 (broccoli, and its front floret and bulb 60 ms late), m / m2 (drumstick, and its egg),
 q (the dish list), press (the knob pressed in).

 autoPlay: LOOP = 7 s
   0.0 – 1.0   rest, Veg
   1.0         tap → Non veg: the knob slides, the broccoli sinks, the drumstick pops up and wobbles,
               the count rolls 24 → 38 and the rows swap
   2.2 – 3.5   hold, Non veg
   3.5 – 4.3   a finger drags the knob back to 0.3: the drumstick sinks, the broccoli peeks out
   4.3         release → Veg: the knob snaps, the broccoli pops, the list swaps back
   5.5 – 7.0   rest, Veg (the frame at 7 is the frame at 0)
*/

export const LOOP = 7;

type Spring = { response: number; damping: number };

const KNOB: Spring = { response: 0.42, damping: 0.78 };
const ENTER: Spring = { response: 0.52, damping: 0.52 };
const LEAVE: Spring = { response: 0.24, damping: 1 };
const LIST: Spring = { response: 0.5, damping: 0.9 };
const PRESS_IN: Spring = { response: 0.12, damping: 1 };
const PRESS_OUT: Spring = { response: 0.3, damping: 0.6 };
const CALM: Spring = { response: 0.2, damping: 1 };
const HOLD: Spring = { response: 1, damping: 1 };

export const ENTER_DELAY = 0.08;
const PART_DELAY = 0.06;
const LIST_DELAY = 0.05;
/** How fast the gap between the art and the knob's position closes while dragging. */
const DRAG_BLEND = 0.12;

const IDLE_BEFORE_BACK = 2;
const SETTLE = 1.6;

const TAP_AT = 1;
const DRAG_FROM = 3.5;
const DRAG_TO = 4.3;
const DRAG_END = 0.3;

type Segment = { t0: number; x0: number; v0: number; target: number; spring: Spring };
type Channel = Segment[];

type Drag = { t0: number; p: number; db: number; db2: number; dm: number; dm2: number };

export type Sim = {
  p: Channel;
  b: Channel;
  b2: Channel;
  m: Channel;
  m2: Channel;
  q: Channel;
  press: Channel;
  mode: number;
  drag: Drag | null;
  reduced: boolean;
};

export type Frame = {
  p: number;
  pv: number;
  b: number;
  bv: number;
  b2: number;
  m: number;
  mv: number;
  m2: number;
  q: number;
  /** Where the list is heading (the rows stagger from the top either way). */
  qTarget: number;
  press: number;
  /** The broccoli (or the drumstick) is drawn over the knob, not under it. */
  bFront: boolean;
  mFront: boolean;
  reduced: boolean;
};

export type Driver = {
  origin: number;
  live: Sim | null;
  lastTouch: number;
  returned: boolean;
  touching: boolean;
};

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

export function smooth(x: number) {
  'worklet';
  const c = clamp01(x);
  return c * c * (3 - 2 * c);
}

export function smoothRange(a: number, b: number, x: number) {
  'worklet';
  return smooth((x - a) / (b - a));
}

/** How far a dish's art is out while the knob sits at p (1 at its own end). */
function reach(p: number) {
  'worklet';
  return smoothRange(0.2, 1, p);
}

/** The knob's position during the scripted drag. */
function scriptP(t: number) {
  'worklet';
  return 1 - (1 - DRAG_END) * smooth((t - DRAG_FROM) / (DRAG_TO - DRAG_FROM));
}

// Springs.

function segEval(s: Segment, t: number): [number, number] {
  'worklet';
  const tau = t - s.t0;
  if (tau <= 0) return [s.x0, s.v0];
  const z = Math.min(s.spring.damping, 0.999);
  const w0 = (2 * Math.PI) / s.spring.response;
  const wd = w0 * Math.sqrt(1 - z * z);
  const a = s.x0 - s.target;
  const b = (s.v0 + z * w0 * a) / wd;
  const e = Math.exp(-z * w0 * tau);
  const c = Math.cos(wd * tau);
  const sn = Math.sin(wd * tau);
  return [s.target + e * (a * c + b * sn), e * ((b * wd - z * w0 * a) * c - (a * wd + z * w0 * b) * sn)];
}

function channel(x: number): Channel {
  'worklet';
  return [{ t0: -1e9, x0: x, v0: 0, target: x, spring: HOLD }];
}

function segAt(ch: Channel, t: number): Segment {
  'worklet';
  let seg = ch[0];
  for (let i = 0; i < ch.length; i++) if (ch[i].t0 <= t) seg = ch[i];
  return seg;
}

function chEval(ch: Channel, t: number): [number, number] {
  'worklet';
  return segEval(segAt(ch, t), t);
}

/** The target of the segment running at time t. */
function chTarget(ch: Channel, t: number) {
  'worklet';
  return segAt(ch, t).target;
}

function push(ch: Channel, seg: Segment) {
  'worklet';
  for (let i = ch.length - 1; i >= 0; i--) if (ch[i].t0 > seg.t0) ch.splice(i, 1);
  ch.push(seg);
  while (ch.length > 6) ch.shift();
}

/** Starts a spring towards `target` at time t; segments planned after t are dropped. */
function go(ch: Channel, target: number, t: number, spring: Spring) {
  'worklet';
  const xv = chEval(ch, t);
  push(ch, { t0: t, x0: xv[0], v0: xv[1], target, spring });
}

/** Holds a value (and velocity) from time t. */
function hold(ch: Channel, x: number, t: number, v = 0) {
  'worklet';
  push(ch, { t0: t, x0: x, v0: v, target: x, spring: HOLD });
}

// The springs of one toggle and a drag in progress.

export function makeSim(mode = 0): Sim {
  'worklet';
  return {
    p: channel(mode),
    b: channel(1 - mode),
    b2: channel(1 - mode),
    m: channel(mode),
    m2: channel(mode),
    q: channel(mode),
    press: channel(0),
    mode,
    drag: null,
    reduced: false,
  };
}

export function simFrame(s: Sim, t: number): Frame {
  'worklet';
  const f: Frame = {
    p: 0, pv: 0, b: 1, bv: 0, b2: 1, m: 0, mv: 0, m2: 0,
    q: chEval(s.q, t)[0], qTarget: chTarget(s.q, t), press: chEval(s.press, t)[0],
    bFront: true, mFront: false, reduced: s.reduced,
  };
  const d = s.drag;
  if (d) {
    const k = Math.exp(-Math.max(0, t - d.t0) / DRAG_BLEND);
    f.p = d.p;
    f.b = reach(1 - d.p) + d.db * k;
    f.b2 = reach(1 - d.p) + d.db2 * k;
    f.m = reach(d.p) + d.dm * k;
    f.m2 = reach(d.p) + d.dm2 * k;
    f.bFront = d.p < 0.5;
    f.mFront = !f.bFront;
    return f;
  }
  // No array destructuring in worklets: Babel turns it into a helper the UI runtime lacks.
  const p = chEval(s.p, t);
  f.p = p[0];
  f.pv = p[1];
  const b = chEval(s.b, t);
  f.b = b[0];
  f.bv = b[1];
  f.b2 = chEval(s.b2, t)[0];
  const m = chEval(s.m, t);
  f.m = m[0];
  f.mv = m[1];
  f.m2 = chEval(s.m2, t)[0];
  f.bFront = chTarget(s.b, t) >= 0.5;
  f.mFront = chTarget(s.m, t) >= 0.5;
  return f;
}

/** Commits a mode: the knob slides, the old art leaves, the new one pops up after `delay`. */
// `delay` has no default: a worklet does not capture constants named in default parameters.
export function simFlip(s: Sim, target: number, t: number, delay: number) {
  'worklet';
  s.mode = target;
  const veg = target === 0;
  if (s.reduced) {
    go(s.p, target, t, CALM);
    go(s.b, veg ? 1 : 0, t, CALM);
    go(s.b2, veg ? 1 : 0, t, CALM);
    go(s.m, veg ? 0 : 1, t, CALM);
    go(s.m2, veg ? 0 : 1, t, CALM);
    go(s.q, target, t, CALM);
    return;
  }
  go(s.p, target, t, KNOB);
  if (veg) {
    go(s.m, 0, t, LEAVE);
    go(s.m2, 0, t, LEAVE);
    go(s.b, 1, t + delay, ENTER);
    go(s.b2, 1, t + delay + PART_DELAY, ENTER);
  } else {
    go(s.b, 0, t, LEAVE);
    go(s.b2, 0, t, LEAVE);
    go(s.m, 1, t + delay, ENTER);
    go(s.m2, 1, t + delay + PART_DELAY, ENTER);
  }
  go(s.q, target, t + LIST_DELAY, LIST);
}

export function simTap(s: Sim, t: number) {
  'worklet';
  go(s.press, 1, t, PRESS_IN);
  go(s.press, 0, t + 0.1, PRESS_OUT);
  simFlip(s, 1 - s.mode, t, ENTER_DELAY);
}

export function simBeginDrag(s: Sim, t: number) {
  'worklet';
  const f = simFrame(s, t);
  s.drag = {
    t0: t,
    p: f.p,
    db: f.b - reach(1 - f.p),
    db2: f.b2 - reach(1 - f.p),
    dm: f.m - reach(f.p),
    dm2: f.m2 - reach(f.p),
  };
  go(s.press, 1, t, PRESS_IN);
}

export function simDragTo(s: Sim, p: number) {
  'worklet';
  if (s.drag) s.drag.p = p;
}

/** Ends a drag: everything springs on from where it is. `target` -1 = the nearest end. */
export function simRelease(s: Sim, t: number, velocity: number, target = -1, delay = 0) {
  'worklet';
  const f = simFrame(s, t);
  s.drag = null;
  hold(s.p, f.p, t, velocity);
  hold(s.b, f.b, t);
  hold(s.b2, f.b2, t);
  hold(s.m, f.m, t);
  hold(s.m2, f.m2, t);
  go(s.press, 0, t, PRESS_OUT);
  const to = target >= 0 ? target : f.p + 0.12 * velocity >= 0.5 ? 1 : 0;
  simFlip(s, to, t, delay);
}

// The autoPlay clock and the session a touch starts.

/** The scripted loop's springs at `now`, for the loop that started at `base`. */
function scripted(base: number, now: number): Sim {
  'worklet';
  const s = makeSim();
  // Only events that have happened: a touch takes over this state as it is.
  if (now >= base + TAP_AT) simTap(s, base + TAP_AT);
  if (now >= base + DRAG_FROM) {
    simBeginDrag(s, base + DRAG_FROM);
    simDragTo(s, scriptP(Math.min(now, base + DRAG_TO) - base));
    if (now >= base + DRAG_TO) simRelease(s, base + DRAG_TO, 0);
  }
  return s;
}

function loopBase(o: number, now: number) {
  'worklet';
  return o + Math.floor((now - o) / LOOP) * LOOP;
}

export function makeDriver(): Driver {
  'worklet';
  return { origin: NaN, live: null, lastTouch: -1e9, returned: false, touching: false };
}

export function driverFrame(d: Driver, now: number, auto: boolean, reduced: boolean): Frame {
  'worklet';
  if (isNaN(d.origin)) d.origin = now;
  const l = d.live;
  if (l) {
    l.reduced = reduced;
    if (!auto || d.touching || now < d.lastTouch + IDLE_BEFORE_BACK) return simFrame(l, now);
    if (!d.returned) {
      d.returned = true;
      const at = d.lastTouch + IDLE_BEFORE_BACK;
      if (l.drag) simRelease(l, at, 0, 0);
      else if (l.mode !== 0) simFlip(l, 0, at, 0);
    }
    if (now < d.lastTouch + IDLE_BEFORE_BACK + SETTLE) return simFrame(l, now);
    d.live = null;
    d.origin = d.lastTouch + IDLE_BEFORE_BACK + SETTLE;
  }
  if (!auto) {
    const s = makeSim();
    s.reduced = reduced;
    d.live = s;
    return simFrame(s, now);
  }
  return simFrame(scripted(loopBase(d.origin, now), now), now);
}

export function driverTouched(d: Driver, now: number) {
  'worklet';
  d.lastTouch = now;
  d.returned = false;
}

/** The springs a touch acts on (the loop's current state when it was playing). */
export function driverSession(d: Driver, now: number, auto: boolean, reduced: boolean): Sim {
  'worklet';
  if (!d.live) d.live = auto && !isNaN(d.origin) ? scripted(loopBase(d.origin, now), now) : makeSim();
  d.live.reduced = reduced;
  driverTouched(d, now);
  return d.live;
}
