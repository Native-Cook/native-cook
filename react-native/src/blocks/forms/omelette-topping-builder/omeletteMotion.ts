/*
 Omelette Topping Builder — the model, the seeded pieces and the autoPlay script
 (a port of OmeletteToppingBuilderMotion.swift). Worklets; helpers first.

 The screen is a pure function of a few events and the time: each topping is an add time (and, once
 lifted off, a remove time), the shelf is a chain of analytic springs. Every piece's flight, hop, spin
 and squash is computed from those times and its seeded layout, so the loop is exact and the three
 stacks match frame for frame.

 autoPlay: LOOP = 9 s
   0.0 – 1.2   rest: the plain omelette, Red Bell Pepper centred, $7.50
   1.2         add the pepper: it is tossed onto the omelette (lands at 1.82) and bursts into 22 cubes;
               the shelf slides to Baby Spinach at 2.1
   3.2         add the spinach (14 leaves), shelf → Smoked Bacon at 4.1
   5.2         add the bacon (7 strips), shelf → Cherry Tomato at 6.1
   7.4 – 7.64  the three toppings lift off and fade, last added first
   7.5         the shelf glides on two more items, back to the pepper
   8.6 – 9.0   rest (the frame at 9 is the frame at 0)
*/

export const LOOP = 9;

// The shelf ring.
export const KINDS = 5;
export const NAMES = ['Red Bell Pepper', 'Baby Spinach', 'Smoked Bacon', 'Cherry Tomato', 'Button Mushroom'];
export const CENTS = [80, 90, 160, 70, 90];
export const BASE_CENTS = 750;
const COUNTS = [22, 14, 7, 12, 10];
const MIN_GAP = [17, 22, 30, 22, 24];
/** Each kind's piece scale. */
export const PIECE_SCALE = [1, 1.2, 1.1, 1, 1];

// The omelette: a half disc of radius R, its fold edge turned to -40°, its centroid on the plate's centre.
export const R = 110;
export const ANGLE = (-40 * Math.PI) / 180;
export const CENTROID_Y = (4 * R) / (3 * Math.PI);

// One add, from the tap.
export const PRESS = 0.08;
export const LAND = 0.62;
export const COLLAPSE = 0.14;
const ADVANCE_AT = 0.9;
export const RESTOCK_AT = 1.3;
export const RESTOCK_DUR = 0.35;
const PRICE_DELAY = 0.1;
export const PRICE_ROLL = 0.35;
export const LIFT_DUR = 0.42;
const LIFT_STAGGER = 0.12;
export const SHAKE_DUR = 0.4;

export type Spring = { response: number; damping: number };
const SHELF_SPRING: Spring = { response: 0.55, damping: 0.8 };
const REWIND_SPRING: Spring = { response: 0.8, damping: 0.88 };
const SNAP_SPRING: Spring = { response: 0.45, damping: 0.82 };
const CALM: Spring = { response: 0.3, damping: 1 };

// The script.
const ADD_AT = [1.2, 3.2, 5.2];
const ADD_KIND = [0, 1, 2];
const CLEAR_AT = 7.4;
const CLEAR_STEP = 0.12;
const REWIND_AT = 7.5;

const IDLE_BEFORE_BACK = 2;
const SETTLE = 1.6;

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

export function smooth(x: number) {
  'worklet';
  const c = clamp01(x);
  return c * c * (3 - 2 * c);
}

export function easeOut(x: number) {
  'worklet';
  const c = 1 - clamp01(x);
  return 1 - c * c * c;
}

export function easeIn(x: number) {
  'worklet';
  const c = clamp01(x);
  return c * c * c;
}

export function easeInOut(x: number) {
  'worklet';
  const c = clamp01(x);
  return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
}

export function easeOutBack(x: number) {
  'worklet';
  const c = clamp01(x);
  const k = 1.70158;
  return 1 + (k + 1) * Math.pow(c - 1, 3) + k * Math.pow(c - 1, 2);
}

/** Rounds half away from zero, like Swift's rounded(). */
export function round(x: number) {
  'worklet';
  return x >= 0 ? Math.floor(x + 0.5) : -Math.floor(-x + 0.5);
}

/** The ring index of a shelf position. */
export function wrap(i: number) {
  'worklet';
  return ((i % KINDS) + KINDS) % KINDS;
}

/** Signed distance of item i from the shelf position s, in items, within (-2.5, 2.5]. */
export function slot(i: number, s: number) {
  'worklet';
  let d = (i - s) % KINDS;
  if (d > 2.5) d -= KINDS;
  if (d <= -2.5) d += KINDS;
  return d;
}

/** A 32-bit integer hash (lowbias32) in [0, 1): the same bits in Swift, Kotlin and JavaScript. */
export function hash(n: number) {
  'worklet';
  let x = n >>> 0;
  x = (x ^ (x >>> 16)) >>> 0;
  x = Math.imul(x, 0x7feb352d) >>> 0;
  x = (x ^ (x >>> 15)) >>> 0;
  x = Math.imul(x, 0x846ca68b) >>> 0;
  x = (x ^ (x >>> 16)) >>> 0;
  return x / 4294967296;
}

/** Where a kind's ingredient lands, in omelette coordinates. */
export function landingX(k: number) {
  'worklet';
  return (hash(90000 + k * 2) - 0.5) * 24;
}

export function landingY(k: number) {
  'worklet';
  return CENTROID_Y + (hash(90001 + k * 2) - 0.5) * 16;
}

export function bezier(a: number, c: number, b: number, u: number) {
  'worklet';
  return (1 - u) * (1 - u) * a + 2 * (1 - u) * u * c + u * u * b;
}

/** One seeded piece, in the omelette's own coordinates (pt, fold edge on y = 0, bulge towards +y). */
export type Piece = { x: number; y: number; rot: number; size: number; spin: number; delay: number; lift: number; phase: number };

function layout(k: number): Piece[] {
  const out: Piece[] = [];
  let j = 0;
  while (out.length < COUNTS[k] && j < 2000) {
    const h1 = hash(k * 10000 + j * 4);
    const h2 = hash(k * 10000 + j * 4 + 1);
    j += 1;
    const th = Math.PI * (0.05 + 0.9 * h1);
    const rho = (R - 15) * Math.sqrt(0.06 + 0.94 * h2);
    const x = Math.cos(th) * rho;
    const y = Math.sin(th) * rho;
    if (y < 10) continue;
    const gap = j > 1200 ? 0 : MIN_GAP[k];
    let ok = true;
    for (const p of out) {
      if (Math.hypot(p.x - x, p.y - y) < gap) {
        ok = false;
        break;
      }
    }
    if (!ok) continue;
    const b = 50000 + k * 1000 + out.length * 8;
    out.push({
      x,
      y,
      rot: hash(b) * 2 * Math.PI,
      size: 0.85 + 0.3 * hash(b + 1),
      spin: (hash(b + 2) - 0.5) * 5,
      delay: 0.02 * hash(b + 3),
      lift: LIFT_STAGGER * hash(b + 4),
      phase: hash(b + 5) * 2 * Math.PI,
    });
  }
  return out;
}

/** The seeded spots of each kind's pieces on the omelette, spaced by a minimum gap. */
export const PIECES: Piece[][] = [0, 1, 2, 3, 4].map(layout);

// Springs.

type Segment = { t0: number; x0: number; v0: number; target: number; response: number; damping: number };
type Channel = { segs: Segment[] };

function segEval(s: Segment, t: number, out: { x: number; v: number }) {
  'worklet';
  const tau = t - s.t0;
  if (tau <= 0) {
    out.x = s.x0;
    out.v = s.v0;
    return;
  }
  const z = Math.min(s.damping, 0.999);
  const w0 = (2 * Math.PI) / s.response;
  const wd = w0 * Math.sqrt(1 - z * z);
  const a = s.x0 - s.target;
  const b = (s.v0 + z * w0 * a) / wd;
  const e = Math.exp(-z * w0 * tau);
  // Settled: land exactly on the target, so a rest frame is the same frame every loop.
  if (e < 1e-4) {
    out.x = s.target;
    out.v = 0;
    return;
  }
  const c = Math.cos(wd * tau);
  const sn = Math.sin(wd * tau);
  out.x = s.target + e * (a * c + b * sn);
  out.v = e * ((b * wd - z * w0 * a) * c - (a * wd + z * w0 * b) * sn);
}

function chanEval(ch: Channel, t: number, out: { x: number; v: number }) {
  'worklet';
  let seg = ch.segs[0];
  for (let i = 0; i < ch.segs.length; i++) if (ch.segs[i].t0 <= t) seg = ch.segs[i];
  segEval(seg, t, out);
}

function chanPush(ch: Channel, seg: Segment) {
  'worklet';
  const keep: Segment[] = [];
  for (let i = 0; i < ch.segs.length; i++) if (ch.segs[i].t0 <= seg.t0) keep.push(ch.segs[i]);
  keep.push(seg);
  ch.segs = keep.length > 6 ? keep.slice(keep.length - 6) : keep;
}

/** Starts a spring towards `target` at time t; segments planned after t are dropped. */
function chanGo(ch: Channel, target: number, t: number, sp: Spring) {
  'worklet';
  const o = { x: 0, v: 0 };
  chanEval(ch, t, o);
  chanPush(ch, { t0: t, x0: o.x, v0: o.v, target, response: sp.response, damping: sp.damping });
}

/** Holds a value (and velocity) from time t. */
function chanSet(ch: Channel, x: number, v: number, t: number) {
  'worklet';
  chanPush(ch, { t0: t, x0: x, v0: v, target: x, response: 1, damping: 1 });
}

// The session.

/** One topping on the omelette: when it was added, where its flight started, when it was lifted off (-1 = on). */
export type Topping = { kind: number; ta: number; sx: number; tr: number };

export type Sim = {
  shelf: Channel;
  drag: number | null;
  toppings: Topping[];
  shakeAt: number;
  shakeKind: number;
  reduced: boolean;
};

export type Frame = { t: number; s: number; toppings: Topping[]; shakeAt: number; shakeKind: number; reduced: boolean };

export function makeSim(): Sim {
  'worklet';
  return {
    shelf: { segs: [{ t0: -1e9, x0: 0, v0: 0, target: 0, response: 1, damping: 1 }] },
    drag: null,
    toppings: [],
    shakeAt: -1e9,
    shakeKind: 0,
    reduced: false,
  };
}

function shelfAt(sim: Sim, t: number) {
  'worklet';
  if (sim.drag !== null) return sim.drag;
  const o = { x: 0, v: 0 };
  chanEval(sim.shelf, t, o);
  return o.x;
}

function isOn(sim: Sim, k: number) {
  'worklet';
  for (let i = 0; i < sim.toppings.length; i++) if (sim.toppings[i].kind === k && sim.toppings[i].tr < 0) return true;
  return false;
}

/** Adds kind k from the shelf's centre (offset sx); the shelf moves on to the next item. */
function simAdd(sim: Sim, k: number, t: number, sx: number) {
  'worklet';
  const keep: Topping[] = [];
  for (let i = 0; i < sim.toppings.length; i++) {
    const tp = sim.toppings[i];
    if (!(tp.tr >= 0 && t > tp.tr + LIFT_DUR + LIFT_STAGGER + 0.1)) keep.push(tp);
  }
  keep.push({ kind: k, ta: t, sx, tr: -1 });
  sim.toppings = keep;
  const s = shelfAt(sim, t);
  if (sim.reduced) chanGo(sim.shelf, round(s) + 1, t + 0.4, CALM);
  else chanGo(sim.shelf, round(s) + 1, t + ADVANCE_AT, SHELF_SPRING);
}

/** A tap on the centred ingredient: adds it, or shakes it when it is already on. True when added. */
export function simTapCentre(sim: Sim, t: number) {
  'worklet';
  const s = shelfAt(sim, t);
  const k = wrap(round(s));
  if (isOn(sim, k)) {
    sim.shakeAt = t;
    sim.shakeKind = k;
    return false;
  }
  simAdd(sim, k, t, slot(k, s));
  return true;
}

/** Centres the item d slots away from the current one. */
export function simSelect(sim: Sim, d: number, t: number) {
  'worklet';
  const s = shelfAt(sim, t);
  chanGo(sim.shelf, round(s) + d, t, sim.reduced ? CALM : SNAP_SPRING);
}

/** Lifts the last landed topping off. True when there was one. */
export function simRemoveLast(sim: Sim, t: number) {
  'worklet';
  for (let i = sim.toppings.length - 1; i >= 0; i--) {
    const tp = sim.toppings[i];
    if (tp.tr < 0 && (sim.reduced || t >= tp.ta + LAND)) {
      tp.tr = t;
      return true;
    }
  }
  return false;
}

/** Lifts every topping off, last added first, `step` apart. */
function simClearAll(sim: Sim, t: number, step: number) {
  'worklet';
  let n = 0;
  for (let i = sim.toppings.length - 1; i >= 0; i--) {
    if (sim.toppings[i].tr < 0) {
      sim.toppings[i].tr = t + step * n;
      n += 1;
    }
  }
}

/** Springs the shelf to the nearest place where the pepper is centred. */
function simRewind(sim: Sim, t: number, sp: Spring) {
  'worklet';
  const s = shelfAt(sim, t);
  chanGo(sim.shelf, round(s / KINDS) * KINDS, t, sim.reduced ? CALM : sp);
}

export function simBeginDrag(sim: Sim, t: number) {
  'worklet';
  const o = { x: 0, v: 0 };
  chanEval(sim.shelf, t, o);
  sim.drag = o.x;
}

export function simDragTo(sim: Sim, s: number) {
  'worklet';
  sim.drag = s;
}

/** Ends a drag: snaps to the nearest item along the finger's speed (items per second). */
export function simRelease(sim: Sim, t: number, velocity: number) {
  'worklet';
  const s = shelfAt(sim, t);
  sim.drag = null;
  chanSet(sim.shelf, s, velocity, t);
  const aim = s + 0.18 * velocity;
  const target = Math.min(round(s) + 2, Math.max(round(s) - 2, round(aim)));
  chanGo(sim.shelf, target, t, sim.reduced ? CALM : SNAP_SPRING);
}

export function simFrame(sim: Sim, t: number): Frame {
  'worklet';
  const toppings: Topping[] = [];
  for (let i = 0; i < sim.toppings.length; i++) {
    const tp = sim.toppings[i];
    toppings.push({ kind: tp.kind, ta: tp.ta, sx: tp.sx, tr: tp.tr });
  }
  return { t, s: shelfAt(sim, t), toppings, shakeAt: sim.shakeAt, shakeKind: sim.shakeKind, reduced: sim.reduced };
}

/** Total in cents at time t. */
export function total(f: Frame, t: number) {
  'worklet';
  let c = BASE_CENTS;
  for (let i = 0; i < f.toppings.length; i++) {
    const tp = f.toppings[i];
    const on = f.reduced ? tp.ta : tp.ta + LAND + PRICE_DELAY;
    const off = tp.tr >= 0 && t >= tp.tr;
    if (t >= on && !off) c += CENTS[tp.kind];
  }
  return c;
}

/** The latest time at or before t when the total changed (or -1e9). */
export function lastChange(f: Frame, t: number) {
  'worklet';
  let last = -1e9;
  for (let i = 0; i < f.toppings.length; i++) {
    const tp = f.toppings[i];
    const on = f.reduced ? tp.ta : tp.ta + LAND + PRICE_DELAY;
    if (on <= t) last = Math.max(last, on);
    if (tp.tr >= 0 && tp.tr <= t && tp.tr > on) last = Math.max(last, tp.tr);
  }
  return last;
}

export function money(cents: number) {
  'worklet';
  const c = cents % 100;
  return '$' + Math.floor(cents / 100) + '.' + (c < 10 ? '0' : '') + c;
}

// The autoPlay clock.

export type Driver = { origin: number; live: Sim | null; lastTouch: number; returned: boolean; touching: boolean };

export function makeDriver(): Driver {
  'worklet';
  return { origin: -1, live: null, lastTouch: -1e9, returned: false, touching: false };
}

/** The scripted loop at `now`, for the loop that started at `base`: only events that have happened. */
function scripted(base: number, now: number): Sim {
  'worklet';
  const s = makeSim();
  for (let i = 0; i < ADD_AT.length; i++) if (now >= base + ADD_AT[i]) simAdd(s, ADD_KIND[i], base + ADD_AT[i], 0);
  if (now >= base + CLEAR_AT) simClearAll(s, base + CLEAR_AT, CLEAR_STEP);
  if (now >= base + REWIND_AT) simRewind(s, base + REWIND_AT, REWIND_SPRING);
  return s;
}

export function driverFrame(d: Driver, now: number, auto: boolean, reduced: boolean): Frame {
  'worklet';
  if (d.origin < 0) d.origin = now;
  const live = d.live;
  if (live) {
    live.reduced = reduced;
    if (!auto || d.touching || now < d.lastTouch + IDLE_BEFORE_BACK) return simFrame(live, now);
    if (!d.returned) {
      d.returned = true;
      const at = d.lastTouch + IDLE_BEFORE_BACK;
      if (live.drag !== null) simRelease(live, at, 0);
      simClearAll(live, at, 0.08);
      simRewind(live, at, REWIND_SPRING);
    }
    if (now < d.lastTouch + IDLE_BEFORE_BACK + SETTLE) return simFrame(live, now);
    d.live = null;
    d.origin = d.lastTouch + IDLE_BEFORE_BACK + SETTLE;
  }
  if (!auto) {
    const s = makeSim();
    s.reduced = reduced;
    d.live = s;
    return simFrame(s, now);
  }
  const base = d.origin + Math.floor((now - d.origin) / LOOP) * LOOP;
  return simFrame(scripted(base, now), now);
}

/** The session a touch acts on (the loop's current state when it was playing). */
export function driverSession(d: Driver, now: number, auto: boolean, reduced: boolean): Sim {
  'worklet';
  if (!d.live) {
    d.live = auto && d.origin >= 0 ? scripted(d.origin + Math.floor((now - d.origin) / LOOP) * LOOP, now) : makeSim();
  }
  d.live.reduced = reduced;
  d.returned = false;
  return d.live;
}

export function driverTouched(d: Driver, now: number) {
  'worklet';
  d.lastTouch = now;
  d.returned = false;
}
