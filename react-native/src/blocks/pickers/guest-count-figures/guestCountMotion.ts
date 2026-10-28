/*
 Guest Count Figures — the model, the springs and the autoPlay script (a port of
 GuestCountFiguresMotion.swift, same numbers). Every function is a worklet: the frame callback and
 the gesture run them on the UI thread. Helpers are declared above their callers.

 The screen is a pure function of an event list and the time: each event sets the state
 (adults, children, rows or the big counter) at a time and may name the control a finger pressed.
 Positions are chained analytic springs: every event restarts the spring from the value and the
 velocity it had, towards the new target. A figure that appears starts at its slot and hops in;
 one that leaves crouches, rises and fades.

 autoPlay: LOOP = 10 s
   0.0 – 1.2 rest: one adult, the big counter · 1.2 big + → 2 adults · 2.5 Add children → rows ·
   3.6, 4.6 children + → 1, 2 (at 4 people both + disable) · 4.6 – 6.2 peak hold ·
   6.2, 6.9 children − → 1, 0 · 7.6 Adults only → big counter · 8.5 big − → 1 adult ·
   9.1 – 10.0 rest (the frame at 10 is the frame at 0)
*/

export const LOOP = 10;
export const MAX_PEOPLE = 4;
const IDLE_BEFORE_BACK = 2;
const SETTLE = 1.6;

export type GState = { adults: number; children: number; rows: boolean };

/** Controls a finger can press: adult −, adult +, child −, child +, the pill. */
export const NONE = -1;
export const ADULT_MINUS = 0;
export const ADULT_PLUS = 1;
export const CHILD_MINUS = 2;
export const CHILD_PLUS = 3;
export const PILL = 4;

export type GEvent = { t: number; s: GState; press: number };

export type FigureFrame = { index: number; x: number; lift: number; sx: number; sy: number; alpha: number };

export type Frame = {
  figures: FigureFrame[];
  /** 0 = the big counter, 1 = the two rows. */
  m: number;
  adultsV: number;
  childrenV: number;
  /** Disabled amount of adult −, adult +, child −, child +. */
  disabled: number[];
  /** Press amount of adult −, adult +, child −, child +, the pill. */
  press: number[];
};

export type Geo = {
  w: number;
  h: number;
  top: number;
  bottom: number;
  cx: number;
  ground: number;
  numY: number;
  rowA: number;
  rowC: number;
  divider: number;
  pillY: number;
  footY: number;
  progressY: number;
  rowPlusX: number;
  rowNumX: number;
  rowMinusX: number;
};

export function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  return {
    w,
    h,
    top,
    bottom,
    cx: w / 2,
    ground: top + 296,
    numY: top + 405,
    rowA: top + 385,
    rowC: top + 463,
    divider: top + 424,
    pillY: top + 566,
    footY: h - bottom - 40,
    progressY: h - bottom - 84,
    rowPlusX: w - 42,
    rowNumX: w - 80,
    rowMinusX: w - 118,
  };
}

export const REST: GState = { adults: 1, children: 0, rows: false };
export const PEAK: GState = { adults: 2, children: 2, rows: true };

export const SCRIPT: GEvent[] = [
  { t: 0.0, s: REST, press: NONE },
  { t: 1.2, s: { adults: 2, children: 0, rows: false }, press: ADULT_PLUS },
  { t: 2.5, s: { adults: 2, children: 0, rows: true }, press: PILL },
  { t: 3.6, s: { adults: 2, children: 1, rows: true }, press: CHILD_PLUS },
  { t: 4.6, s: { adults: 2, children: 2, rows: true }, press: CHILD_PLUS },
  { t: 6.2, s: { adults: 2, children: 1, rows: true }, press: CHILD_MINUS },
  { t: 6.9, s: { adults: 2, children: 0, rows: true }, press: CHILD_MINUS },
  { t: 7.6, s: { adults: 2, children: 0, rows: false }, press: PILL },
  { t: 8.5, s: REST, press: ADULT_MINUS },
];

// Figures: 0…3 adults, 4…6 children, in that order along the ground.
export const FIGURES = 7;
const ADULT_SLOT = 46;
const CHILD_SLOT = 38;
const GAP = 12;
/** Every figure is drawn 10 % larger than its art (guestCountArt.ts), slots included. */
export const FIGURE_SCALE = 1.1;
const EXIT_TIME = 0.34;

export function present(i: number, s: GState): boolean {
  'worklet';
  return i < 4 ? i < s.adults : i - 4 < s.children;
}

export function slot(i: number): number {
  'worklet';
  return (i >= 4 ? CHILD_SLOT : ADULT_SLOT) * FIGURE_SCALE;
}

function sameState(a: GState, b: GState): boolean {
  'worklet';
  return a.adults === b.adults && a.children === b.children && a.rows === b.rows;
}

/** Centre x of every figure in state s (NaN when absent), and its rank along the row. */
function layout(s: GState, cx: number, xs: number[], rank: number[]) {
  'worklet';
  let total = 0;
  let n = 0;
  for (let i = 0; i < FIGURES; i++) {
    if (present(i, s)) {
      total += slot(i);
      n++;
    }
  }
  total += GAP * Math.max(0, n - 1);
  let cursor = cx - total / 2;
  let r = 0;
  for (let i = 0; i < FIGURES; i++) {
    if (present(i, s)) {
      xs[i] = cursor + slot(i) / 2;
      rank[i] = r;
      cursor += slot(i) + GAP;
      r++;
    } else {
      xs[i] = NaN;
      rank[i] = -1;
    }
  }
}

// Curves.

export function clamp01(x: number): number {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

function easeOut(x: number): number {
  'worklet';
  const k = 1 - x;
  return 1 - k * k * k;
}

function easeIn(x: number): number {
  'worklet';
  return x * x * x;
}

export function smooth(x: number): number {
  'worklet';
  const c = clamp01(x);
  return c * c * (3 - 2 * c);
}

/** Advances an underdamped spring (displacement d from its target, velocity v) by dt. */
function step(d: number, v: number, dt: number, response: number, damping: number, out: number[]) {
  'worklet';
  if (dt <= 0) {
    out[0] = d;
    out[1] = v;
    return;
  }
  const w0 = (2 * Math.PI) / response;
  const a = damping * w0;
  const b = w0 * Math.sqrt(1 - damping * damping);
  const e = Math.exp(-a * dt);
  if (e < 1e-9) {
    out[0] = 0;
    out[1] = 0;
    return;
  }
  const c = Math.cos(b * dt);
  const s = Math.sin(b * dt);
  const bb = (v + a * d) / b;
  out[0] = e * (d * c + bb * s);
  out[1] = e * (v * c - ((a * v + w0 * w0 * d) / b) * s);
}

// Channels: 0…6 figure x, 7 mode, 8 adults value, 9 children value, 10…13 disabled.
const CHANNELS = 14;
const RESPONSE = [0.55, 0.55, 0.55, 0.55, 0.55, 0.55, 0.55, 0.5, 0.42, 0.42, 0.3, 0.3, 0.3, 0.3];
const DAMPING = [0.72, 0.72, 0.72, 0.72, 0.72, 0.72, 0.72, 0.86, 0.72, 0.72, 0.95, 0.95, 0.95, 0.95];

function targets(s: GState, cx: number, out: number[], rank: number[]) {
  'worklet';
  layout(s, cx, out, rank);
  const total = s.adults + s.children;
  out[7] = s.rows ? 1 : 0;
  out[8] = s.adults;
  out[9] = s.children;
  out[10] = s.adults <= 1 ? 1 : 0;
  out[11] = total >= MAX_PEOPLE ? 1 : 0;
  out[12] = s.children <= 0 ? 1 : 0;
  out[13] = total >= MAX_PEOPLE ? 1 : 0;
}

// Figure moves: out = [lift, scale, sx, sy, alpha].

/** A figure hopping in, τ seconds after it appeared. */
function enter(tau: number, out: number[]) {
  'worklet';
  out[4] = clamp01(tau / 0.12);
  out[1] = 0.55 + 0.45 * easeOut(clamp01(tau / 0.3));
  out[0] = 0;
  out[2] = 1;
  out[3] = 1;
  if (tau < 0.44) {
    out[0] = 36 * Math.sin((Math.PI * Math.max(0, tau)) / 0.44);
  } else {
    const t2 = tau - 0.44;
    const s = 0.12 * Math.exp(-7 * t2) * Math.cos((2 * Math.PI * t2) / 0.26);
    out[3] = 1 - s;
    out[2] = 1 + 0.6 * s;
  }
}

/** A figure leaving, τ seconds after it was removed. */
function exit(tau: number, out: number[]) {
  'worklet';
  if (tau < 0.08) {
    const k = tau / 0.08;
    out[0] = 0;
    out[1] = 1;
    out[2] = 1 + 0.03 * k;
    out[3] = 1 - 0.06 * k;
    out[4] = 1;
    return;
  }
  const u = clamp01((tau - 0.08) / 0.26);
  out[0] = 22 * easeOut(u);
  out[1] = 1 - 0.45 * easeIn(u);
  out[2] = 1.03 - 0.03 * u;
  out[3] = 0.94 + 0.06 * u;
  out[4] = 1 - easeIn(u);
}

/** The frame at time t of an event list (sorted by time, the first one is the base). */
export function frameAt(ev: GEvent[], t: number, cx: number): Frame {
  'worklet';
  const f: Frame = { figures: [], m: 0, adultsV: 1, childrenV: 0, disabled: [1, 0, 1, 0], press: [0, 0, 0, 0, 0] };
  if (ev.length === 0) return f;
  const rank = new Array<number>(FIGURES).fill(0);
  const tg = new Array<number>(CHANNELS).fill(0);
  const nt = new Array<number>(CHANNELS).fill(0);
  const x = new Array<number>(CHANNELS).fill(0);
  const v = new Array<number>(CHANNELS).fill(0);
  const o = [0, 0];

  // Chained springs.
  targets(ev[0].s, cx, tg, rank);
  for (let c = 0; c < CHANNELS; c++) x[c] = isNaN(tg[c]) ? cx : tg[c];
  let last = ev[0].t;
  let k = 1;
  while (k < ev.length && ev[k].t <= t) {
    const dt = ev[k].t - last;
    for (let c = 0; c < CHANNELS; c++) {
      step(x[c] - tg[c], v[c], dt, RESPONSE[c], DAMPING[c], o);
      x[c] = tg[c] + o[0];
      v[c] = o[1];
    }
    targets(ev[k].s, cx, nt, rank);
    for (let c = 0; c < CHANNELS; c++) {
      if (isNaN(nt[c])) continue; // an absent figure keeps where it was
      if (c < FIGURES && !present(c, ev[k - 1].s)) {
        x[c] = nt[c]; // an arriving figure starts at its slot
        v[c] = 0;
      }
      tg[c] = nt[c];
    }
    last = ev[k].t;
    k++;
  }
  const dtEnd = t - last;
  for (let c = 0; c < CHANNELS; c++) {
    step(x[c] - tg[c], v[c], dtEnd, RESPONSE[c], DAMPING[c], o);
    x[c] = tg[c] + o[0];
    v[c] = o[1];
  }
  const applied = k;
  const now = ev[applied - 1].s;

  f.m = x[7];
  f.adultsV = x[8];
  f.childrenV = x[9];
  for (let c = 0; c < 4; c++) f.disabled[c] = x[10 + c];

  // Presses.
  for (let j = 0; j < ev.length; j++) {
    const e = ev[j];
    if (e.press === NONE) continue;
    const d = t - e.t;
    if (d <= -0.1 || d >= 0.18) continue;
    const amount = d < 0 ? smooth((d + 0.1) / 0.1) : 1 - smooth(d / 0.18);
    f.press[e.press] = Math.max(f.press[e.press], amount);
  }

  // Figures: the last time each one arrived or left.
  const changeAt = new Array<number>(FIGURES).fill(-Infinity);
  const entered = new Array<boolean>(FIGURES).fill(false);
  for (let j = 1; j < applied; j++) {
    for (let i = 0; i < FIGURES; i++) {
      const was = present(i, ev[j - 1].s);
      const isIn = present(i, ev[j].s);
      if (was !== isIn) {
        changeAt[i] = ev[j].t;
        entered[i] = isIn;
      }
    }
  }

  // Welcome hops: when someone arrives, the others bounce, nearest first.
  const welcome = new Array<number>(FIGURES).fill(0);
  const xs = new Array<number>(FIGURES).fill(0);
  for (let j = applied - 1; j >= 1 && t - ev[j].t < 1.2; j--) {
    layout(ev[j].s, cx, xs, rank);
    for (let n = 0; n < FIGURES; n++) {
      if (!present(n, ev[j].s) || present(n, ev[j - 1].s)) continue;
      for (let i = 0; i < FIGURES; i++) {
        if (i === n || !present(i, ev[j].s) || !present(i, now)) continue;
        const dist = Math.abs(rank[i] - rank[n]);
        const u = (t - ev[j].t - 0.3 - 0.05 * dist) / 0.3;
        if (u > 0 && u < 1) welcome[i] = Math.max(welcome[i], 7 * Math.sin(Math.PI * u));
      }
    }
  }

  const mv = [0, 1, 1, 1, 1];
  for (let i = 0; i < FIGURES; i++) {
    const isIn = present(i, now);
    const tau = t - changeAt[i];
    if (isIn) {
      if (isFinite(changeAt[i]) && entered[i]) enter(tau, mv);
      else {
        mv[0] = 0;
        mv[1] = 1;
        mv[2] = 1;
        mv[3] = 1;
        mv[4] = 1;
      }
    } else {
      if (!(isFinite(changeAt[i]) && !entered[i] && tau < EXIT_TIME)) continue;
      exit(tau, mv);
    }
    f.figures.push({ index: i, x: x[i], lift: mv[0] + welcome[i], sx: mv[1] * mv[2], sy: mv[1] * mv[3], alpha: mv[4] });
  }
  return f;
}

// The live model: the autoPlay clock, and the session a touch starts.

export type Sim = {
  loopStart: number;
  /** Event list of the touch session, with absolute times; null while the loop plays. */
  session: GEvent[] | null;
  touching: boolean;
  lastTouch: number;
  restQueued: boolean;
};

export function makeSim(): Sim {
  return { loopStart: NaN, session: null, touching: false, lastTouch: -Infinity, restQueued: false };
}

function loopTime(sim: Sim, now: number): number {
  'worklet';
  const start = isNaN(sim.loopStart) ? now : sim.loopStart;
  const t = (now - start) % LOOP;
  return t < 0 ? t + LOOP : t;
}

export function simFrame(sim: Sim, now: number, cx: number, auto: boolean, reduced: boolean): Frame {
  'worklet';
  if (reduced) {
    return frameAt(sim.session ?? [{ t: 0, s: PEAK, press: NONE }], now + 100, cx);
  }
  const ev = sim.session;
  if (ev) {
    if (auto && !sim.touching) {
      if (!sim.restQueued && now >= sim.lastTouch + IDLE_BEFORE_BACK) {
        ev.push({ t: sim.lastTouch + IDLE_BEFORE_BACK, s: REST, press: NONE });
        sim.restQueued = true;
      }
      if (sim.restQueued && now >= sim.lastTouch + IDLE_BEFORE_BACK + SETTLE) {
        sim.session = null;
        sim.loopStart = sim.lastTouch + IDLE_BEFORE_BACK + SETTLE;
        return frameAt(SCRIPT, loopTime(sim, now), cx);
      }
    }
    return frameAt(ev, now, cx);
  }
  if (!auto) return frameAt([{ t: 0, s: REST, press: NONE }], 100, cx);
  if (isNaN(sim.loopStart)) sim.loopStart = now;
  return frameAt(SCRIPT, loopTime(sim, now), cx);
}

/** The state the screen is heading to now. */
export function simState(sim: Sim, now: number, auto: boolean, reduced: boolean): GState {
  'worklet';
  const ev = sim.session;
  if (ev) return ev[ev.length - 1].s;
  if (reduced) return PEAK;
  if (!auto || isNaN(sim.loopStart)) return REST;
  const t = loopTime(sim, now);
  let s = REST;
  for (let i = 0; i < SCRIPT.length; i++) if (SCRIPT[i].t <= t) s = SCRIPT[i].s;
  return s;
}

/** Starts a session that carries on from what the screen shows. */
function begin(sim: Sim, now: number, auto: boolean, reduced: boolean) {
  'worklet';
  if (sim.session) return;
  if (reduced) {
    sim.session = [{ t: now - 10, s: PEAK, press: NONE }];
  } else if (auto && !isNaN(sim.loopStart)) {
    const base = sim.loopStart + Math.floor((now - sim.loopStart) / LOOP) * LOOP;
    const ev: GEvent[] = [];
    for (let i = 0; i < SCRIPT.length; i++) ev.push({ t: base - LOOP + SCRIPT[i].t, s: SCRIPT[i].s, press: SCRIPT[i].press });
    for (let i = 0; i < SCRIPT.length; i++) {
      if (base + SCRIPT[i].t <= now) ev.push({ t: base + SCRIPT[i].t, s: SCRIPT[i].s, press: SCRIPT[i].press });
    }
    sim.session = ev;
  } else {
    sim.session = [{ t: now - 10, s: REST, press: NONE }];
  }
}

export function simTouch(sim: Sim, now: number, auto: boolean, reduced: boolean) {
  'worklet';
  begin(sim, now, auto, reduced);
  sim.lastTouch = now;
  sim.restQueued = false;
}

/** Applies a new state from a finger. Returns true when something changed. */
export function simApply(sim: Sim, s: GState, press: number, now: number, auto: boolean, reduced: boolean): boolean {
  'worklet';
  simTouch(sim, now, auto, reduced);
  const ev = sim.session;
  if (!ev || sameState(ev[ev.length - 1].s, s)) return false;
  ev.push({ t: now, s, press });
  // Everything older than 3 s has settled: keep the last such event as the base.
  let j = -1;
  for (let i = 0; i < ev.length; i++) if (ev[i].t < now - 3) j = i;
  if (j > 0) ev.splice(0, j);
  return true;
}
