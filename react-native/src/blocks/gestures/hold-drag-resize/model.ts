/**
 * The day as one plain object, stepped once a frame on the UI thread. A finger, the demo script and the offline
 * renderer call the same worklets, so a frame on the phone and a frame rendered headless match.
 *
 * Times are minutes since midnight. `start`/`end` are the plan; `top`/`len` are what is drawn, sprung toward the
 * layout the plan (and the edge under the finger) asks for.
 *
 * Rules:
 * - Meetings never move. Tasks and focus blocks keep their own time unless the block before them has grown into it:
 *   then they sit right after it (a rigid chain), and they go back to their own time when it shrinks again.
 * - The dragged edge clicks to quarter hours. Past the last free minute before a meeting it rubber-bands, and the
 *   tasks in between squash to make the room.
 *
 * Every helper is declared above the worklets that call it: worklets capture what exists when they are created.
 */
import { KIND, PLAN } from './day';
import { DEMO, EASE_IN_OUT, EASE_OUT, ELEVATION, RESIZE, SPRINGS, TIMING, type Spring } from './motion';
import { DAY } from './theme';

export type Haptic = 'lift' | 'tick' | 'wall' | 'drop';

export type Geometry = { gridTop: number; ppm: number; cardX: number; cardW: number };

export type Model = {
  n: number;
  kind: number[];
  start: number[];
  end: number[];
  top: number[];
  topV: number[];
  len: number[];
  lenV: number[];
  elev: number[];
  elevV: number[];
  lock: number[]; // the meeting's lock, tint and give, 0..1 (tweened toward lockT)
  lockT: number[];
  push: number[]; // a pushed task's old time, 0..1 (tweened)
  was: number[]; // that old time, kept while it fades (TIMING.wasOut)
  geo: Geometry;
  reduced: boolean;
  silent: boolean; // the script is driving: no haptics
  active: number; // the card being resized, or -1
  edge: number; // 1 its end, 0 its start
  grabEdge: number;
  grabY: number;
  target: number; // the edge's time, on a quarter hour
  over: number; // minutes the edge is pushed past its limit, rubber-banded (negative under the shortest length)
  atWall: boolean;
  wall: number; // the meeting the chain runs into (n: the end of the day)
  shown: number; // the card whose pill and grip are drawn: the active one, then the last one while it settles
  refused: number; // a meeting someone tried to resize
  pressCard: number;
  pressShown: number; // the card the press belongs to, kept while it lets go
  press: number; // the hold's progress: linear in, eased out; stays 1 while the card is held
  lift: number;
  liftV: number;
  focus: number; // the header's total, sprung
  focusV: number;
  open: number; // the bar's free time, sprung
  openV: number;
  fx: number; // where the finger is (or the demo's), for the light it makes in the glass
  fy: number;
  delta: number; // the "+1 h" beside the grip, 0..1 (tweened)
  deltaLast: number; // its minutes, kept while it fades out
  // Demo
  scriptDown: boolean;
  ghostX: number;
  ghostY: number;
  ghostO: number;
  ghostOT: number;
  ghostP: number;
  ghostPT: number;
  /** Tween bookkeeping, three numbers a slot (from, goal, age s); see `tween` for the slots. */
  tw: number[];
  // Out
  events: Haptic[];
  version: number;
  moving: boolean;
};

export const MEMO = PLAN.findIndex((p) => p.id === 'memo');

function focusTotal(kind: number[], start: number[], end: number[]): number {
  let s = 0;
  for (let i = 0; i < kind.length; i++) if (kind[i] === KIND.focus) s += end[i] - start[i];
  return s;
}

function openTotal(start: number[], end: number[]): number {
  let s = DAY.end - DAY.start;
  for (let i = 0; i < start.length; i++) s -= end[i] - start[i];
  return s;
}

export function createModel(geo: Geometry, reduced: boolean): Model {
  const n = PLAN.length;
  const kind = PLAN.map((p) => p.kind as number);
  const start = PLAN.map((p) => p.start);
  const end = PLAN.map((p) => p.end);
  const zeros = () => new Array<number>(n).fill(0);
  const focus = focusTotal(kind, start, end);
  const open = openTotal(start, end);
  return {
    n, kind, start, end,
    top: start.slice(), topV: zeros(), len: PLAN.map((p) => p.end - p.start), lenV: zeros(),
    elev: new Array<number>(n).fill(ELEVATION.rest), elevV: zeros(), lock: zeros(), lockT: zeros(),
    push: zeros(), was: start.slice(),
    geo, reduced, silent: false,
    active: -1, edge: 1, grabEdge: 0, grabY: 0, target: 0, over: 0, atWall: false, wall: n, shown: -1, refused: -1,
    pressCard: -1, pressShown: -1, press: 0, lift: 0, liftV: 0, focus, focusV: 0, open, openV: 0, fx: 0, fy: 0,
    delta: 0, deltaLast: 0,
    scriptDown: false, ghostX: 0, ghostY: 0, ghostO: 0, ghostOT: 0, ghostP: 0, ghostPT: 0,
    tw: new Array<number>((2 * n + 4) * 3).fill(0),
    events: [], version: 0, moving: true,
  };
}

export function cloneModel(m: Model): Model {
  'worklet';
  return {
    ...m,
    kind: m.kind.slice(), start: m.start.slice(), end: m.end.slice(), top: m.top.slice(), topV: m.topV.slice(),
    len: m.len.slice(), lenV: m.lenV.slice(), elev: m.elev.slice(), elevV: m.elevV.slice(), lock: m.lock.slice(),
    lockT: m.lockT.slice(), push: m.push.slice(), was: m.was.slice(), tw: m.tw.slice(), events: m.events.slice(),
  };
}

// ——— Numbers ———

export function clamp(x: number, lo: number, hi: number): number {
  'worklet';
  return x < lo ? lo : x > hi ? hi : x;
}

/** CSS cubic-bezier(x1, y1, x2, y2) at u, solved for x by Newton then bisection. */
export function bezier(x1: number, y1: number, x2: number, y2: number, u: number): number {
  'worklet';
  const k = clamp(u, 0, 1);
  const cx = 3 * x1;
  const bx = 3 * (x2 - x1) - cx;
  const ax = 1 - cx - bx;
  const cy = 3 * y1;
  const by = 3 * (y2 - y1) - cy;
  const ay = 1 - cy - by;
  let t = k;
  for (let i = 0; i < 6; i++) {
    const x = ((ax * t + bx) * t + cx) * t - k;
    const dx = (3 * ax * t + 2 * bx) * t + cx;
    if (Math.abs(x) < 1e-5 || Math.abs(dx) < 1e-6) break;
    t -= x / dx;
  }
  if (t < 0 || t > 1) {
    let lo = 0;
    let hi = 1;
    t = k;
    for (let i = 0; i < 20; i++) {
      const x = ((ax * t + bx) * t + cx) * t;
      if (x < k) lo = t;
      else hi = t;
      t = (lo + hi) / 2;
    }
  }
  return ((ay * t + by) * t + cy) * t;
}

export function easeInOut(u: number): number {
  'worklet';
  return bezier(EASE_IN_OUT[0], EASE_IN_OUT[1], EASE_IN_OUT[2], EASE_IN_OUT[3], u);
}

export function easeOut(u: number): number {
  'worklet';
  return bezier(EASE_OUT[0], EASE_OUT[1], EASE_OUT[2], EASE_OUT[3], u);
}

/** Tween slots: a lock per card, a pushed task's old time per card, then the demo finger, its press, the delta, the press. */
const FINGER = 0;
const FINGER_PRESS = 1;
const DELTA = 2;
const PRESS = 3;

function slotOf(m: Model, what: number): number {
  'worklet';
  return 2 * m.n + what;
}

/**
 * A CSS-like transition of slot k: value x heads for `goal` over `dIn` s (up) or `dOut` s (down) on EASE_OUT. A new
 * goal restarts it from x, the value as drawn, so a reversal mid-way never jumps. `linearIn`: the way up is linear.
 */
function tween(m: Model, k: number, x: number, goal: number, dt: number, dIn: number, dOut: number, linearIn = false): number {
  'worklet';
  const j = k * 3;
  const tw = m.tw;
  if (tw[j + 1] !== goal) {
    tw[j] = x;
    tw[j + 1] = goal;
    tw[j + 2] = 0;
  }
  const from = tw[j];
  const up = goal > from;
  const d = up ? dIn : dOut;
  const age = Math.min(tw[j + 2] + dt, d);
  tw[j + 2] = age;
  const u = d > 0 ? age / d : 1;
  if (u >= 1) return goal;
  return from + (goal - from) * (up && linearIn ? u : easeOut(u));
}

/** Slot k at v at once (no tween). */
function snap(m: Model, k: number, v: number) {
  'worklet';
  m.tw[k * 3] = v;
  m.tw[k * 3 + 1] = v;
  m.tw[k * 3 + 2] = 0;
}

/** One damped spring, (duration, damping ratio), stepped semi-implicitly in ≤ 4 ms slices. Returns [x, v]. */
export function springStep(x: number, v: number, target: number, dt: number, s: Spring): [number, number] {
  'worklet';
  const w = (2 * Math.PI) / (s.duration / 1000);
  const k = w * w;
  const c = 2 * s.dampingRatio * w;
  const n = Math.max(1, Math.ceil(dt / 0.004));
  const h = dt / n;
  let px = x;
  let pv = v;
  for (let i = 0; i < n; i++) {
    pv += (-k * (px - target) - c * pv) * h;
    px += pv * h;
  }
  if (Math.abs(px - target) < 0.002 && Math.abs(pv) < 0.01) return [target, 0];
  return [px, pv];
}

/** Rubber band: slope `rubber` at first, never past `rubberMax`. */
export function rubber(x: number): number {
  'worklet';
  return RESIZE.rubberMax * (1 - Math.exp((-x * RESIZE.rubber) / RESIZE.rubberMax));
}

export function quarter(x: number): number {
  'worklet';
  return Math.round(x / RESIZE.quarter) * RESIZE.quarter;
}

export function yAt(m: Model, min: number): number {
  'worklet';
  return m.geo.gridTop + (min - DAY.start) * m.geo.ppm;
}

// ——— Layout ———

/** The first meeting after card i (n when the day ends first). */
export function wallAfter(m: Model, i: number): number {
  'worklet';
  for (let j = i + 1; j < m.n; j++) if (m.kind[j] === KIND.meeting) return j;
  return m.n;
}

/**
 * Where every card should be, in minutes: [tops, lengths]. The active card takes the edge under the finger; the
 * tasks after it are pushed as a chain, and squashed in proportion to their length when it is pushed past the wall.
 * Without the over-push (`withOver` false) it is the layout the edge would settle to: what the labels read.
 */
export function layout(m: Model, withOver = true): [number[], number[]] {
  'worklet';
  const over = withOver ? m.over : 0;
  const T = new Array<number>(m.n).fill(0);
  const L = new Array<number>(m.n).fill(0);
  let floor = DAY.start;
  for (let i = 0; i < m.n; i++) {
    let s = m.start[i];
    let e = m.end[i];
    if (i === m.active) {
      if (m.edge === 1) e = m.target + over;
      else s = m.target + over;
    }
    if (m.kind[i] === KIND.meeting || i === m.active) T[i] = s;
    else T[i] = Math.max(s, floor);
    L[i] = e - s;
    floor = T[i] + L[i];
  }
  const a = m.active;
  if (a >= 0 && m.edge === 1 && over > 0) {
    const w = m.wall;
    const wallTop = w < m.n ? m.start[w] : DAY.end;
    const bottom = T[a] + L[a];
    let sum = 0;
    for (let j = a + 1; j < w; j++) sum += L[j];
    if (sum > 0) {
      const scale = Math.max((wallTop - bottom) / sum, 0.3);
      let y = bottom;
      for (let j = a + 1; j < w; j++) {
        T[j] = y;
        L[j] *= scale;
        y += L[j];
      }
    } else {
      L[a] = wallTop - T[a]; // nothing to squash: a hard stop
    }
  }
  return [T, L];
}

/** The quarter hours the active edge may take: [lo, hi]. */
export function edgeLimits(m: Model, i: number, edge: number): [number, number] {
  'worklet';
  if (edge === 1) {
    let tasks = 0;
    let hi = DAY.end;
    for (let j = i + 1; j < m.n; j++) {
      if (m.kind[j] === KIND.meeting) {
        hi = m.start[j];
        break;
      }
      tasks += m.end[j] - m.start[j];
    }
    return [m.start[i] + RESIZE.minLength, hi - tasks];
  }
  // A start moves up only into free time: it never pushes earlier cards.
  let lo = DAY.start;
  if (i > 0) {
    const [T, L] = layout(m);
    lo = T[i - 1] + L[i - 1];
  }
  return [lo, m.end[i] - RESIZE.minLength];
}

// ——— Actions ———

function emit(m: Model, h: Haptic) {
  'worklet';
  if (!m.silent) m.events.push(h);
}

/** The card under a point, or -1. */
export function cardAt(m: Model, x: number, y: number): number {
  'worklet';
  if (x < m.geo.cardX - 4) return -1;
  for (let i = 0; i < m.n; i++) {
    const y0 = yAt(m, m.top[i]);
    const y1 = yAt(m, m.top[i] + m.len[i]);
    if (y >= y0 && y <= y1) return i;
  }
  return -1;
}

/** A finger rests on card i, before the hold completes: the card presses into the page. */
export function pressBegin(m: Model, i: number) {
  'worklet';
  if (m.active >= 0 || i < 0 || m.kind[i] === KIND.meeting) return;
  m.pressCard = i;
  if (m.pressShown !== i) {
    // The press belongs to a card: another card's (the demo's held block, say) starts from nothing, not from where
    // the last one was.
    m.pressShown = i;
    m.press = 0;
    snap(m, slotOf(m, PRESS), 0);
  }
  m.moving = true;
}

export function pressEnd(m: Model) {
  'worklet';
  m.pressCard = -1;
  m.moving = true;
}

/** The hold completed on card i at y: lift it and take the nearer edge. A meeting shows its lock instead. */
export function beginResize(m: Model, i: number, y: number) {
  'worklet';
  if (i < 0 || m.active >= 0) return;
  m.pressCard = -1;
  m.moving = true;
  if (m.kind[i] === KIND.meeting) {
    m.refused = i;
    m.lockT[i] = 1;
    emit(m, 'wall');
    return;
  }
  const [T, L] = layout(m);
  // A pushed task takes the place it was pushed to as its own.
  m.start[i] = T[i];
  m.end[i] = T[i] + L[i];
  m.edge = y > yAt(m, T[i] + L[i] / 2) ? 1 : 0;
  m.grabEdge = m.edge === 1 ? m.end[i] : m.start[i];
  m.grabY = y;
  m.target = m.grabEdge;
  m.over = 0;
  m.atWall = false;
  m.wall = wallAfter(m, i);
  m.active = i;
  m.shown = i;
  m.delta = 0;
  snap(m, slotOf(m, DELTA), 0);
  m.version++;
  emit(m, 'lift');
}

/** The finger is at y: the edge clicks to the nearest allowed quarter hour, rubber-banding past the limits. */
export function moveResize(m: Model, y: number) {
  'worklet';
  const i = m.active;
  if (i < 0) return;
  const raw = m.grabEdge + (y - m.grabY) / m.geo.ppm;
  const [lo, hi] = edgeLimits(m, i, m.edge);
  const q = clamp(quarter(raw), lo, hi);
  let over = 0;
  if (!m.reduced) {
    if (m.edge === 1) {
      if (raw > hi) over = rubber(raw - hi);
      else if (raw < lo) over = -rubber(lo - raw) * 0.5;
    } else if (raw > hi) over = rubber(raw - hi) * 0.5; // a start pushed under the shortest length gives a little
  }
  m.over = over;
  const ticked = q !== m.target;
  if (ticked) {
    m.target = q;
    m.version++;
  }
  const wasWall = m.atWall;
  m.atWall = m.edge === 1 && raw > hi + 1;
  // One haptic a frame: reaching the last quarter and the wall at once is the wall.
  if (m.atWall && !wasWall) {
    emit(m, 'wall');
    if (m.wall < m.n) m.lockT[m.wall] = 1;
  } else {
    if (ticked) emit(m, 'tick');
    if (!m.atWall && wasWall && m.wall < m.n) m.lockT[m.wall] = 0;
  }
  m.moving = true;
}

/** Let go: the edge keeps its quarter hour, the over-push springs back. */
export function endResize(m: Model) {
  'worklet';
  m.pressCard = -1;
  if (m.refused >= 0) {
    m.lockT[m.refused] = 0;
    m.refused = -1;
  }
  const i = m.active;
  if (i < 0) return;
  if (m.edge === 1) m.end[i] = m.target;
  else m.start[i] = m.target;
  m.over = 0;
  m.atWall = false;
  if (m.wall < m.n) m.lockT[m.wall] = 0;
  m.active = -1;
  m.version++;
  m.moving = true;
  emit(m, 'drop');
}

/** VoiceOver's adjust on card i: its end a quarter hour later (+1) or earlier (−1), pushing tasks as a drag would. */
export function nudgeEnd(m: Model, i: number, delta: number) {
  'worklet';
  if (i < 0 || m.active >= 0 || m.kind[i] === KIND.meeting) return;
  const [T, L] = layout(m);
  m.start[i] = T[i];
  m.end[i] = T[i] + L[i];
  const [lo, hi] = edgeLimits(m, i, 1);
  const next = clamp(m.end[i] + delta * RESIZE.quarter, lo, hi);
  if (next === m.end[i]) return;
  m.end[i] = next;
  m.shown = i;
  m.version++;
  m.moving = true;
  emit(m, 'tick');
}

/** Everything back to the plan (the cards spring home). */
export function resetPlan(m: Model) {
  'worklet';
  endResize(m);
  for (let i = 0; i < m.n; i++) {
    m.start[i] = PLAN[i].start;
    m.end[i] = PLAN[i].end;
    m.lockT[i] = 0;
  }
  m.version++;
  m.moving = true;
}

// ——— Frame ———

/**
 * The minutes the plans take, live while a block is being resized (its edge on its quarter hour, never the
 * over-push): the focus blocks' alone (`focusOnly`), or every block's.
 */
function liveLength(m: Model, focusOnly: boolean): number {
  'worklet';
  let s = 0;
  for (let i = 0; i < m.n; i++) {
    if (focusOnly && m.kind[i] !== KIND.focus) continue;
    if (i === m.active) s += m.edge === 1 ? m.target - m.start[i] : m.end[i] - m.target;
    else s += m.end[i] - m.start[i];
  }
  return s;
}

export function step(m: Model, dt: number) {
  'worklet';
  const R = m.reduced;
  const [T, L] = layout(m);
  let busy = false;
  for (let i = 0; i < m.n; i++) {
    if (m.kind[i] === KIND.meeting) {
      m.top[i] = T[i];
      m.len[i] = L[i];
      continue;
    }
    // The block under the finger clicks; a squashed chain keeps pace with it; a task going home ripples.
    let s: Spring = SPRINGS.detent;
    if (R) s = SPRINGS.reduced;
    else if (i !== m.active && !(m.active >= 0 && m.over > 0)) {
      const k = m.shown >= 0 ? Math.max(1, i - m.shown) : 1;
      s = i === m.shown ? SPRINGS.detent : { duration: SPRINGS.ripple.duration + RESIZE.rippleStep * (k - 1), dampingRatio: SPRINGS.ripple.dampingRatio };
    }
    const [tp, tv] = springStep(m.top[i], m.topV[i], T[i], dt, s);
    const [lp, lv] = springStep(m.len[i], m.lenV[i], L[i], dt, s);
    m.top[i] = tp;
    m.topV[i] = tv;
    m.len[i] = lp;
    m.lenV[i] = lv;
    if (tv !== 0 || lv !== 0) busy = true;
  }
  // Contact: a card can never be inside the one before it. Pushing is rigid (no lag on the way out); the pushed card
  // keeps the place and speed it was pushed to, so on the way back it springs home from there.
  let floor = -1e9;
  let floorV = 0;
  for (let i = 0; i < m.n; i++) {
    if (m.kind[i] !== KIND.meeting && i !== m.active && m.top[i] < floor) {
      m.top[i] = floor;
      m.topV[i] = Math.max(m.topV[i], floorV);
    }
    floor = m.top[i] + m.len[i];
    floorV = m.topV[i] + m.lenV[i];
  }
  // A meeting is a wall: anything sprung into it is squashed back to its top.
  let ceil = 1e9;
  for (let i = m.n - 1; i >= 0; i--) {
    if (m.kind[i] !== KIND.meeting && m.top[i] + m.len[i] > ceil) m.len[i] = Math.max(ceil - m.top[i], 4);
    ceil = m.kind[i] === KIND.meeting ? m.top[i] : Math.min(ceil, m.top[i]);
  }

  // The hold's progress: linear while the finger waits (it ends with the hold), held at 1 while the card is up, eased
  // out when it lets go.
  const pressGoal = m.pressCard >= 0 || (m.active >= 0 && m.active === m.pressShown) ? 1 : 0;
  m.press = tween(m, slotOf(m, PRESS), m.press, pressGoal, dt, RESIZE.holdMs / 1000, TIMING.pressOut, true);
  if (m.press !== pressGoal) busy = true;
  // A pushed task's old time fades in beside the new one, and goes with its own time coming back.
  const settledAt = layout(m, false)[0];
  // Elevation: pressed in during the hold, lifted while held, settled after.
  for (let i = 0; i < m.n; i++) {
    let target: number = ELEVATION.rest;
    if (i === m.active) target = R ? ELEVATION.held * 0.5 : ELEVATION.held;
    else if (i === m.pressCard) target = ELEVATION.rest + (ELEVATION.pressed - ELEVATION.rest) * m.press;
    const s = R ? SPRINGS.reduced : target > m.elev[i] ? SPRINGS.lift : SPRINGS.drop;
    const [e, ev] = springStep(m.elev[i], m.elevV[i], target, dt, s);
    m.elev[i] = e;
    m.elevV[i] = ev;
    if (ev !== 0) busy = true;
    m.lock[i] = tween(m, i, m.lock[i], m.lockT[i], dt, TIMING.lockIn, TIMING.lockOut);
    if (m.lock[i] !== m.lockT[i]) busy = true;
    const pushed = m.kind[i] === KIND.task && i !== m.active && settledAt[i] > m.start[i] + 0.5 ? 1 : 0;
    if (pushed) m.was[i] = m.start[i];
    m.push[i] = tween(m, m.n + i, m.push[i], pushed, dt, TIMING.fadeIn, TIMING.wasOut);
    if (m.push[i] !== pushed) busy = true;
  }
  const liftTarget = m.active >= 0 ? 1 : 0;
  const [l, lv] = springStep(m.lift, m.liftV, liftTarget, dt, R ? SPRINGS.reduced : liftTarget ? SPRINGS.lift : SPRINGS.drop);
  m.lift = Math.max(l, 0);
  m.liftV = lv;
  if (m.active < 0 && m.lift < 0.002) {
    m.lift = 0;
    m.liftV = 0;
  }
  const [f, fv] = springStep(m.focus, m.focusV, liveLength(m, true), dt, R ? SPRINGS.reduced : SPRINGS.roll);
  m.focus = f;
  m.focusV = fv;
  const [o, ov] = springStep(m.open, m.openV, DAY.end - DAY.start - liveLength(m, false), dt, R ? SPRINGS.reduced : SPRINGS.roll);
  m.open = o;
  m.openV = ov;

  // What the held edge changed ("+1 h"), shown from the first quarter on and faded out on its last value.
  const settled = m.shown < 0 ? 0 : m.edge === 1 ? m.target - m.grabEdge : m.grabEdge - m.target;
  const deltaGoal = Math.abs(settled) >= RESIZE.quarter ? 1 : 0;
  if (deltaGoal) m.deltaLast = settled;
  m.delta = tween(m, slotOf(m, DELTA), m.delta, deltaGoal, dt, TIMING.fadeIn, TIMING.fadeOut);
  if (m.delta !== deltaGoal) busy = true;

  m.ghostO = tween(m, slotOf(m, FINGER), m.ghostO, m.ghostOT, dt, TIMING.fingerIn, TIMING.fingerOut);
  m.ghostP = tween(m, slotOf(m, FINGER_PRESS), m.ghostP, m.ghostPT, dt, TIMING.fingerIn, TIMING.fingerOut);
  m.moving = busy || lv !== 0 || fv !== 0 || ov !== 0 || m.active >= 0 || m.pressCard >= 0
    || m.ghostO !== m.ghostOT || m.ghostP !== m.ghostPT;
}

// ——— Demo ———

/** The finger's travel at time t along keyframes [s, minutes], eased between them. */
function pathAt(path: readonly (readonly [number, number])[], t: number): number {
  'worklet';
  if (t <= path[0][0]) return path[0][1];
  for (let i = 1; i < path.length; i++) {
    const [t1, v1] = path[i];
    const [t0, v0] = path[i - 1];
    if (t <= t1) return v0 + (v1 - v0) * easeInOut((t - t0) / (t1 - t0));
  }
  return path[path.length - 1][1];
}

function gesture(m: Model, g: typeof DEMO.grow | typeof DEMO.shrink, t0: number, t1: number) {
  'worklet';
  const cross = (at: number) => t0 < at && t1 >= at;
  const x = m.geo.cardX + m.geo.cardW * DEMO.fingerX;
  if (cross(g.down)) {
    m.ghostX = x;
    m.ghostY = yAt(m, g.grab);
    m.fx = x;
    m.fy = m.ghostY;
    m.ghostOT = 1;
    m.ghostPT = 1;
    pressBegin(m, MEMO);
  }
  if (cross(g.lifted)) {
    beginResize(m, MEMO, yAt(m, g.grab));
    m.scriptDown = true;
  }
  if (m.scriptDown && t1 >= g.lifted && t1 < g.up) {
    const y = yAt(m, g.grab + pathAt(g.path, t1));
    m.ghostY = y;
    m.fy = y;
    moveResize(m, y);
  }
  if (cross(g.up)) {
    m.scriptDown = false;
    endResize(m);
    m.ghostOT = 0;
    m.ghostPT = 0;
  }
}

/** One step of the scripted finger, from t0 to t1 seconds into the loop. */
export function script(m: Model, t0: number, t1: number) {
  'worklet';
  gesture(m, DEMO.grow, t0, t1);
  gesture(m, DEMO.shrink, t0, t1);
}
