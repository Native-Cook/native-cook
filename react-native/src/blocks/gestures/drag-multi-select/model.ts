/**
 * The whole selection as one plain object, stepped once a frame on the UI thread. A finger, the demo script and the
 * offline renderer all call the same worklets, so a frame on the phone and a frame rendered headless match.
 *
 * Geometry is in list coordinates: y = 0 is the top of the first row. Positions on screen are added by the caller.
 *
 * Range rule (as in Photos and Mail): a drag takes the anchor's target state into every row in [anchor, current] and
 * gives a row its old state back when it leaves the range, so dragging back undoes. An episode already on the phone
 * cannot be picked: a range passes over it, a tap leaves it alone. So a row is one of three things, never two at once:
 * not downloaded (arrow), picked (filled disk, accent arrow, on the sheet) or downloaded (check, a disk that is off).
 *
 * Every helper is declared above the worklets that call it: worklets capture what exists when they are created.
 */
import { DEMO, DOWNLOAD, EASE_IN_OUT, EASE_OUT, LOOP_MS, SELECT, SHEET_FADE_MS, SPRINGS, TIMING, type Spring } from './motion';
import { LAYOUT } from './theme';

export const PHASE = { idle: 0, selecting: 1, downloading: 2, done: 3, ready: 4 } as const;
export type Haptic = 'enter' | 'tick' | 'cover' | 'commit' | 'done';

export type Model = {
  n: number;
  dur: number[]; // minutes per row
  flight: number; // minutes to cover
  reduced: boolean;
  silent: boolean; // the script is driving: no haptics
  mode: number; // 0 browsing, 1 selecting
  modeA: number;
  modeV: number;
  phase: number;
  sel: number[]; // 0/1 targets
  pres: number[]; // presence in the slab, sprung per row
  presV: number[];
  /** Presence as drawn: within a run, the rows still growing or shrinking at its ends share one front (see `shape`). */
  shown: number[];
  prog: number[]; // download fill 0..1
  done: number[]; // downloaded (0/1)
  mark: number[]; // the check glyph, 0..1 toward `done`
  lift: number;
  liftV: number;
  /** Leaving selection, the sheet keeps these rows whole and fades as one piece (instead of each row shrinking). */
  fadeSel: number[];
  sheetA: number;
  dragging: boolean;
  anchor: number;
  target: number;
  current: number;
  base: number[];
  fingerY: number;
  dlT: number;
  dlRows: number[];
  doneT: number;
  // Demo
  t: number;
  scriptDown: boolean;
  ghostX: number;
  ghostY: number;
  ghostO: number;
  ghostOT: number;
  ghostP: number;
  ghostPT: number;
  // Out
  events: Haptic[];
  version: number;
  moving: boolean;
};

export type Geometry = { width: number; listTop: number; buttonY: number };

const ROW = LAYOUT.row;

export function createModel(dur: number[], flight: number, reduced: boolean): Model {
  const n = dur.length;
  const zeros = () => new Array<number>(n).fill(0);
  return {
    n, dur: dur.slice(), flight, reduced, silent: false,
    mode: 0, modeA: 0, modeV: 0, phase: PHASE.idle,
    sel: zeros(), pres: zeros(), presV: zeros(), shown: zeros(), prog: zeros(), done: zeros(), mark: zeros(),
    lift: 0, liftV: 0, fadeSel: [], sheetA: 1,
    dragging: false, anchor: 0, target: 1, current: -1, base: zeros(), fingerY: 0,
    dlT: 0, dlRows: [], doneT: 0,
    t: 0, scriptDown: false, ghostX: 0, ghostY: 0, ghostO: 0, ghostOT: 0, ghostP: 0, ghostPT: 0,
    events: [], version: 0, moving: true,
  };
}

export function cloneModel(m: Model): Model {
  'worklet';
  return {
    ...m,
    dur: m.dur.slice(), sel: m.sel.slice(), pres: m.pres.slice(), presV: m.presV.slice(), shown: m.shown.slice(), prog: m.prog.slice(),
    done: m.done.slice(), mark: m.mark.slice(), base: m.base.slice(), fadeSel: m.fadeSel.slice(), dlRows: m.dlRows.slice(), events: m.events.slice(),
  };
}

// ——— Numbers ———

export function clamp01(x: number): number {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

/** CSS cubic-bezier(x1, y1, x2, y2) at u, solved for x by Newton then bisection. */
export function bezier(x1: number, y1: number, x2: number, y2: number, u: number): number {
  'worklet';
  const k = clamp01(u);
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

/**
 * Leaving selection, how much of the sheet is left (1 → 0). `sheetA` runs linearly over SHEET_FADE_MS; the sheet, the
 * gauge's segments and the hairlines all read it through this one ease-out, so they leave together and at once.
 */
export function sheetAlpha(m: Model): number {
  'worklet';
  return m.fadeSel.length ? 1 - easeOut(1 - m.sheetA) : 1;
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
  if (Math.abs(px - target) < 0.0005 && Math.abs(pv) < 0.002) return [target, 0];
  return [px, pv];
}

export function rowAt(m: Model, y: number): number {
  'worklet';
  const i = Math.floor(y / ROW);
  return i < 0 ? 0 : i > m.n - 1 ? m.n - 1 : i;
}

export function rowCenter(i: number): number {
  'worklet';
  return (i + 0.5) * ROW;
}

export function minutesOf(m: Model, sel: number[]): number {
  'worklet';
  let s = 0;
  for (let i = 0; i < sel.length; i++) if (sel[i] === 1) s += m.dur[i];
  return s;
}

export function countOf(sel: number[]): number {
  'worklet';
  let s = 0;
  for (let i = 0; i < sel.length; i++) if (sel[i] === 1) s++;
  return s;
}

// ——— Selection ———

function emit(m: Model, h: Haptic) {
  'worklet';
  if (!m.silent) m.events.push(h);
}

/** New targets; one haptic: crossing the flight wins over the row's tick. */
function publish(m: Model, next: number[], kind: Haptic) {
  'worklet';
  const before = minutesOf(m, m.sel);
  const after = minutesOf(m, next);
  m.sel = next;
  m.version++;
  m.moving = true;
  if (kind === 'tick' && before < m.flight && after >= m.flight) emit(m, 'cover');
  else emit(m, kind);
}

export function enter(m: Model, row: number) {
  'worklet';
  if (m.mode === 1 || m.phase === PHASE.downloading) return;
  m.mode = 1;
  m.phase = PHASE.selecting;
  if (m.fadeSel.length) {
    for (let i = 0; i < m.n; i++) if (m.fadeSel[i] === 1) m.pres[i] = 0;
    m.fadeSel = [];
    m.sheetA = 1;
  }
  for (let i = 0; i < m.n; i++) m.prog[i] = 0;
  const next = new Array<number>(m.n).fill(0);
  if (row >= 0 && m.done[row] !== 1) next[row] = 1;
  publish(m, next, 'enter');
}

export function exit(m: Model) {
  'worklet';
  if (m.mode === 0) return;
  m.mode = 0;
  m.dragging = false;
  m.phase = m.phase === PHASE.done ? PHASE.ready : PHASE.idle;
  let any = false;
  for (let i = 0; i < m.n; i++) if (m.pres[i] > 0.05) any = true;
  if (any) {
    m.fadeSel = m.sel.slice();
    m.sheetA = 1;
  }
  m.sel = new Array<number>(m.n).fill(0);
  m.version++;
  m.moving = true;
}

export function toggle(m: Model, row: number) {
  'worklet';
  if (m.mode !== 1 || m.phase !== PHASE.selecting || m.done[row] === 1) return;
  const next = m.sel.slice();
  next[row] = next[row] === 1 ? 0 : 1;
  publish(m, next, 'tick');
}

/** forceTarget 1 selects, 0 deselects, -1 flips the anchor row's state. */
export function dragBegin(m: Model, row: number, forceTarget: number) {
  'worklet';
  if (m.mode !== 1 || m.phase !== PHASE.selecting) return;
  m.dragging = true;
  m.anchor = row;
  m.current = row;
  m.base = m.sel.slice();
  m.target = forceTarget >= 0 ? forceTarget : m.sel[row] === 1 ? 0 : 1;
  m.fingerY = rowCenter(row);
  m.moving = true;
  if (m.sel[row] !== m.target && m.done[row] !== 1) {
    const next = m.sel.slice();
    next[row] = m.target;
    publish(m, next, 'tick');
  }
}

export function dragMove(m: Model, y: number) {
  'worklet';
  if (!m.dragging) return;
  m.fingerY = y;
  m.moving = true;
  const row = rowAt(m, y);
  if (row === m.current) return;
  m.current = row;
  const from = Math.min(m.anchor, row);
  const to = Math.max(m.anchor, row);
  const next = m.base.slice();
  for (let i = from; i <= to; i++) if (m.done[i] !== 1) next[i] = m.target;
  let changed = false;
  for (let i = 0; i < m.n; i++) if (next[i] !== m.sel[i]) changed = true;
  if (changed) publish(m, next, 'tick');
}

export function dragEnd(m: Model) {
  'worklet';
  m.dragging = false;
  m.moving = true;
}

export function startDownload(m: Model) {
  'worklet';
  if (m.phase !== PHASE.selecting || countOf(m.sel) === 0) return;
  m.dragging = false;
  m.phase = PHASE.downloading;
  const rows: number[] = [];
  for (let i = 0; i < m.n; i++) if (m.sel[i] === 1) rows.push(i);
  m.dlRows = rows;
  m.dlT = 0;
  m.version++;
  m.moving = true;
  emit(m, 'commit');
}

/** Back to the start state: downloaded marks go, the panel is idle again. */
export function resetDone(m: Model) {
  'worklet';
  if (m.mode === 1) exit(m);
  for (let i = 0; i < m.n; i++) {
    m.done[i] = 0;
    m.prog[i] = 0;
  }
  if (m.phase === PHASE.ready || m.phase === PHASE.done) m.phase = PHASE.idle;
  m.version++;
  m.moving = true;
}

// ——— Frame ———

const FULL = 0.98;
const GONE = 0.004;

/**
 * Presence as drawn. A fast sweep has several rows on their springs at once; drawn one by one they would be loose
 * strips of paper. Instead the rows still moving at either end of a run pool their presence and fill in order from
 * the run's solid part outward, so the sheet has one edge that travels. Rows in the middle of a run (one being tapped
 * out or in between two others) keep their own presence and part or fuse in place.
 */
export function shape(m: Model) {
  'worklet';
  const p = m.pres;
  const out = m.shown;
  let i = 0;
  while (i < m.n) {
    if (p[i] < GONE) {
      out[i] = 0;
      i++;
      continue;
    }
    const s = i;
    while (i < m.n && p[i] >= GONE) i++;
    const e = i - 1;
    let first = -1;
    let last = -1;
    for (let k = s; k <= e; k++) {
      if (p[k] >= FULL) {
        if (first < 0) first = k;
        last = k;
      }
    }
    for (let k = s; k <= e; k++) out[k] = p[k];
    if (first < 0) {
      // Nothing solid yet: fill from the end the finger started at.
      let total = 0;
      for (let k = s; k <= e; k++) total += p[k];
      const up = m.dragging && m.current < m.anchor;
      for (let j = 0; j <= e - s; j++) out[up ? e - j : s + j] = clamp01(total - j);
      continue;
    }
    let tail = 0;
    for (let k = last + 1; k <= e; k++) tail += p[k];
    for (let k = last + 1; k <= e; k++) out[k] = clamp01(tail - (k - last - 1));
    let head = 0;
    for (let k = s; k < first; k++) head += p[k];
    for (let k = first - 1; k >= s; k--) out[k] = clamp01(head - (first - 1 - k));
  }
}

/**
 * The presence each row is pulled toward this frame: in the sheet or not. Nothing in between is ever a target, so the
 * sheet never shows a half-made piece of paper (no hint under a held row, no reach toward the finger).
 */
function presenceTarget(m: Model, i: number): number {
  'worklet';
  if (m.fadeSel.length && m.fadeSel[i] === 1) return 1;
  return m.sel[i];
}

function springFor(m: Model, target: number, x: number): Spring {
  'worklet';
  if (m.reduced) return SPRINGS.reduced;
  return target >= 0.99 && target >= x ? SPRINGS.join : SPRINGS.leave;
}

export function step(m: Model, dt: number) {
  'worklet';
  let moving = false;
  const [ma, mv] = springStep(m.modeA, m.modeV, m.mode, dt, m.reduced ? SPRINGS.reduced : SPRINGS.mode);
  if (ma !== m.modeA || mv !== m.modeV) moving = true;
  m.modeA = ma;
  m.modeV = mv;

  for (let i = 0; i < m.n; i++) {
    const tg = presenceTarget(m, i);
    const [x, v] = springStep(m.pres[i], m.presV[i], tg, dt, springFor(m, tg, m.pres[i]));
    if (x !== m.pres[i] || v !== m.presV[i]) moving = true;
    m.pres[i] = x;
    m.presV[i] = v;
  }

  if (m.fadeSel.length) {
    moving = true;
    m.sheetA -= dt / (SHEET_FADE_MS / 1000);
    if (m.sheetA <= 0) {
      for (let i = 0; i < m.n; i++) {
        if (m.fadeSel[i] === 1) {
          m.pres[i] = 0;
          m.presV[i] = 0;
        }
      }
      m.fadeSel = [];
      m.sheetA = 1;
    }
  }
  // After the fade's last step, so the frame that ends it draws no sheet (not one whole frame of it at full alpha).
  shape(m);

  let liftT = 0;
  if (m.mode === 1) liftT = m.dragging && !m.reduced ? 1 : SELECT.restLift;
  const [l, lv] = springStep(m.lift, m.liftV, liftT, dt, m.reduced ? SPRINGS.reduced : SPRINGS.lift);
  if (l !== m.lift || lv !== m.liftV) moving = true;
  m.lift = l;
  m.liftV = lv;

  if (m.phase === PHASE.downloading) {
    moving = true;
    m.dlT += dt;
    const rows = m.dlRows;
    for (let k = 0; k < rows.length; k++) {
      const g = easeInOut((m.dlT - k * DOWNLOAD.stagger) / DOWNLOAD.row);
      m.prog[rows[k]] = g;
      // Each episode turns to its check as it lands (the ease's last 3 % is invisible), not when the last one does: a
      // full ring never waits on screen looking like an empty radio button.
      if (g >= 0.97) m.done[rows[k]] = 1;
    }
    if (m.dlT >= (rows.length - 1) * DOWNLOAD.stagger + DOWNLOAD.row) {
      for (let k = 0; k < rows.length; k++) m.done[rows[k]] = 1;
      m.phase = PHASE.done;
      m.doneT = 0;
      m.version++;
      emit(m, 'done');
    }
  } else if (m.phase === PHASE.done) {
    moving = true;
    m.doneT += dt;
    if (m.doneT >= DOWNLOAD.settle) exit(m);
  }

  const rate = dt / DOWNLOAD.mark;
  for (let i = 0; i < m.n; i++) {
    const to = m.done[i];
    const x = m.mark[i];
    if (x !== to) {
      moving = true;
      m.mark[i] = x < to ? Math.min(to, x + rate) : Math.max(to, x - rate);
    }
  }

  const fr = dt / TIMING.fingerFade;
  if (m.ghostO !== m.ghostOT) {
    moving = true;
    m.ghostO = m.ghostO < m.ghostOT ? Math.min(m.ghostOT, m.ghostO + fr) : Math.max(m.ghostOT, m.ghostO - fr);
  }
  const pr = dt / (TIMING.press / 1000);
  if (m.ghostP !== m.ghostPT) {
    moving = true;
    m.ghostP = m.ghostP < m.ghostPT ? Math.min(m.ghostPT, m.ghostP + pr) : Math.max(m.ghostPT, m.ghostP - pr);
  }
  m.moving = moving || m.dragging;
}

// ——— Demo ———

function ghostTo(m: Model, x: number, y: number, on: boolean) {
  'worklet';
  m.ghostX = x;
  m.ghostY = y;
  m.ghostOT = on ? 1 : 0;
}

/** Runs the script's events between t0 and t1 (seconds into the loop). The ghost is in screen coordinates. */
export function script(m: Model, g: Geometry, t0: number, t1: number) {
  'worklet';
  const cross = (at: number) => t0 < at && t1 >= at;
  const screenY = (row: number) => g.listTop + rowCenter(row);
  const midX = g.width * 0.5;
  const diskX = LAYOUT.numberX + LAYOUT.numberW / 2;
  const lp = SELECT.longPress / 1000;

  // 1. Hold #48: after the long press, selection starts.
  const h = DEMO.hold;
  if (cross(h.down)) {
    ghostTo(m, midX, screenY(h.row), true);
    m.ghostPT = 1;
  }
  if (cross(h.down + lp)) {
    enter(m, h.row);
    dragBegin(m, h.row, 1);
    m.scriptDown = true;
  }
  // 2. Sweep down to the fifth row: 42 minutes short.
  const d = DEMO.drag;
  if (m.scriptDown && t1 >= d.start && t1 < DEMO.release) {
    const u = easeInOut((t1 - d.start) / (d.end - d.start));
    const y = rowCenter(d.from) + (rowCenter(d.to) - rowCenter(d.from)) * u;
    dragMove(m, y);
    m.ghostY = g.listTop + y;
  }
  if (cross(DEMO.release)) {
    m.scriptDown = false;
    dragEnd(m);
    m.ghostPT = 0;
    m.ghostOT = 0;
  }
  // 3. Tap the sixth episode in: the timeline covers the flight and the landing drops in.
  const taps = DEMO.taps;
  for (let i = 0; i < taps.length; i++) {
    const tp = taps[i];
    if (cross(tp.at - 0.3)) {
      ghostTo(m, diskX + 6, screenY(tp.row), true);
      m.ghostPT = 0;
    }
    if (cross(tp.at - 0.08)) m.ghostPT = 1;
    if (cross(tp.at)) {
      toggle(m, tp.row);
      m.ghostPT = 0;
      m.ghostOT = 0;
    }
  }
  // 4. Download.
  const dl = DEMO.download;
  if (cross(dl.at - 0.3)) {
    ghostTo(m, midX, g.buttonY, true);
    m.ghostPT = 0;
  }
  if (cross(dl.at - 0.08)) m.ghostPT = 1;
  if (cross(dl.at)) {
    startDownload(m);
    m.ghostPT = 0;
    m.ghostOT = 0;
  }
  // 5. The marks go and the panel rests, so the loop starts where it ends.
  if (cross(DEMO.reset.at)) resetDone(m);
  if (cross(LOOP_MS / 1000 - 0.05)) {
    resetDone(m);
    }
}
