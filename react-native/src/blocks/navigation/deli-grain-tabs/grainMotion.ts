/*
 Timing and touch model of Deli Grain Tabs, ported from DeliGrainTabsMotion.swift. In the loop every
 value is a pure function of the loop time, so the loop's last frame equals its first. The touch
 model is a plain object stepped once per frame on the UI thread.
*/

export const LOOP = 9.6;
export const HOLD = 0.9; // rest before each sift
export const SIFT = 1.5; // one shape pouring into the next
const ORDER = [0, 2, 6, 3]; // Balsamico, Flour & Pizza, Pasta & Risotto, Sweets & Coffee
export const RESUME_AFTER = 2.0;

// Geometry (points of a 393-wide screen)

export const HERO_SCALE = 2.2; // points per unit
export const ICON_SCALE = 0.34;
const COLUMN_GAP = 58;

export type Layout = {
  height: number;
  topRow: number;
  bottomRow: number;
  /** The hero silhouettes stand on this line (unit y = 96). */
  baseline: number;
  nameY: number;
  countY: number;
  /** The grains' swirl turns about this point. */
  swirlX: number;
  swirlY: number;
};

export function makeLayout(height: number, top: number, bottom: number): Layout {
  const topRow = top + 42;
  const bottomRow = height - bottom - 46;
  const baseline = (topRow + bottomRow) / 2 - 12 + 92;
  return {
    height,
    topRow,
    bottomRow,
    baseline,
    nameY: baseline + 46,
    countY: baseline + 68,
    swirlX: 196.5,
    swirlY: baseline - 48 * HERO_SCALE + 30,
  };
}

export function heroX(u: number) {
  'worklet';
  return 196.5 + (u - 50) * HERO_SCALE;
}

export function heroY(l: Layout, v: number) {
  'worklet';
  return l.baseline + (v - 96) * HERO_SCALE;
}

export function tabX(i: number) {
  'worklet';
  return 196.5 + ((i % 6) - 2.5) * COLUMN_GAP;
}

export function tabY(l: Layout, i: number) {
  'worklet';
  return i < 6 ? l.topRow : l.bottomRow;
}

export const GRAIN_COUNT = 1400;
export const GRID = 110;

export function hash(x: number) {
  'worklet';
  const s = Math.sin(x * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** One silhouette as grains, in units: positions and each grain's height share (0 top, 1 bottom). */
export type Grains = { x: number[]; y: number[]; r: number[]; top: number; bottom: number };


/** A sample of the finger's trail; each pushes the grains near it, then lets them settle back. */
export type Stir = { x: number; y: number; vx: number; vy: number; at: number; live: boolean };

/** Grain positions captured when a tab is tapped mid-flight (points, plus each grain's height share). */
export type Buffer = { x: number[]; y: number[]; r: number[] };

/** What the art needs for one frame. */
export type Frame = {
  /** Shape the grains leave (−1: from `buffer`, wherever they were when a tab was tapped). */
  from: number;
  to: number;
  /** The tab selected before (for the tab colours, the indicator and the label). */
  fromTab: number;
  /** Seconds into the sift; at SIFT or later the grains rest on `to`. */
  p: number;
  buffer: Buffer | null;
  stirs: Stir[];
  clock: number;
};

// Curves

export function mod(a: number, b: number) {
  'worklet';
  const r = a % b;
  return r < 0 ? r + b : r;
}

export function clamp(v: number, lo: number, hi: number) {
  'worklet';
  return Math.min(Math.max(v, lo), hi);
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInCubic(x: number) {
  'worklet';
  return x * x * x;
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function resting(f: Frame) {
  'worklet';
  return f.p >= SIFT;
}

// The loop (P = 9.6 s): four segments of 0.9 s rest + 1.5 s sift

export function loopFrame(t: number): Frame {
  'worklet';
  const segment = HOLD + SIFT;
  const k = Math.min(ORDER.length - 1, Math.floor(t / segment));
  const tau = t - k * segment;
  const a = ORDER[k];
  const b = ORDER[(k + 1) % ORDER.length];
  if (tau < HOLD) return { from: a, to: a, fromTab: a, p: SIFT, buffer: null, stirs: [], clock: 0 };
  return { from: a, to: b, fromTab: a, p: tau - HOLD, buffer: null, stirs: [], clock: 0 };
}

// Grains

/** Per-grain constants, seeded: six per grain. */
export function makeSeeds(): number[] {
  const s: number[] = [];
  for (let i = 0; i < GRAIN_COUNT; i += 1) for (let k = 1; k <= 6; k += 1) s.push(hash(i * 1.733 + k * 9.17));
  return s;
}

/** Where grain i is (points) into out: x, y, flight share (0 at rest, 1 mid-air). */
function grain(
  seeds: number[],
  i: number,
  sx: number,
  sy: number,
  rFrom: number,
  tx: number,
  ty: number,
  rTo: number,
  p: number,
  cx: number,
  cy: number,
  out: number[],
) {
  'worklet';
  if (p >= SIFT) {
    out[0] = tx;
    out[1] = ty;
    out[2] = 0;
    return;
  }
  const o = i * 6;
  const h0 = seeds[o];
  const h1 = seeds[o + 1];
  const h2 = seeds[o + 2];
  const h3 = seeds[o + 3];
  const h4 = seeds[o + 4];
  const h5 = seeds[o + 5];
  const start = 0.36 * (1 - rFrom) + 0.1 * h0;
  const land = 1.0 + 0.4 * (1 - rTo) + 0.08 * h1;
  const u = clamp((p - start) / (land - start), 0, 1);
  if (u <= 0) {
    out[0] = sx;
    out[1] = sy;
    out[2] = 0;
    return;
  }
  if (u >= 1) {
    out[0] = tx;
    out[1] = ty;
    out[2] = 0;
    return;
  }
  const e = easeInOutCubic(u);
  // Falls out of the old shape, swings round and rains onto the new one.
  const c1x = sx + 50 * (2 * h2 - 1);
  const c1y = sy + 150 + 50 * h3;
  const c2x = tx + 40 * (2 * h4 - 1);
  const c2y = ty - 160 - 60 * h5;
  const v = 1 - e;
  let x = v * v * v * sx + 3 * v * v * e * c1x + 3 * v * e * e * c2x + e * e * e * tx;
  let y = v * v * v * sy + 3 * v * v * e * c1y + 3 * v * e * e * c2y + e * e * e * ty;
  const lift = Math.sin(Math.PI * e);
  const angle = lift * (0.55 + 0.7 * h5);
  const dx = x - cx;
  const dy = y - cy;
  x = cx + dx * Math.cos(angle) - dy * Math.sin(angle);
  y = cy + dx * Math.sin(angle) + dy * Math.cos(angle);
  x += 10 * lift * Math.sin(2 * Math.PI * (2 * e + h2));
  y += 7 * lift * Math.cos(2 * Math.PI * (2 * e + h3));
  out[0] = x;
  out[1] = y;
  out[2] = Math.sin(Math.PI * u);
}

/**
 How the finger's trail pushes a grain at (x, y): away from each sample and along its motion, fading
 with a springy settle; capped softly at 34 pt. Adds to out[0], out[1].
 */
function stirOffset(x: number, y: number, stirs: Stir[], clock: number, out: number[]) {
  'worklet';
  let ox = 0;
  let oy = 0;
  for (let k = 0; k < stirs.length; k += 1) {
    const s = stirs[k];
    let env = 1;
    if (!s.live) {
      const a = clock - s.at;
      if (a > 1.6) continue;
      env = Math.exp(-3.2 * a) * Math.cos(4.5 * a);
    }
    const dx = x - s.x;
    const dy = y - s.y;
    const d2 = dx * dx + dy * dy;
    if (d2 > 8100) continue;
    const w = Math.exp(-d2 / 1800) * env;
    const d = Math.sqrt(d2) + 0.5;
    ox += (dx / d) * 26 * w + clamp(s.vx * 0.05, -30, 30) * w;
    oy += (dy / d) * 26 * w + clamp(s.vy * 0.05, -30, 30) * w;
  }
  const m = Math.sqrt(ox * ox + oy * oy);
  if (m < 0.01) return;
  const capped = 34 * Math.tanh(m / 34);
  out[0] += (ox / m) * capped;
  out[1] += (oy / m) * capped;
}

/** 0 when no stir is near, 1 while the finger is down; drives the solid fill's fade. */
export function stirEnergy(stirs: Stir[], clock: number) {
  'worklet';
  let e = 0;
  for (let k = 0; k < stirs.length; k += 1) {
    const s = stirs[k];
    e = Math.max(e, s.live ? 1 : clamp(Math.exp(-3.2 * (clock - s.at)) * 1.6, 0, 1));
  }
  return clamp(e, 0, 1);
}

/** Fills xs, ys and fly with every grain's position (points) and flight share. */
export function positions(
  f: Frame,
  l: Layout,
  grains: Grains[],
  seeds: number[],
  xs: number[],
  ys: number[],
  fly: number[],
) {
  'worklet';
  const target = grains[f.to];
  const source = f.from >= 0 ? grains[f.from] : null;
  const buffer = f.buffer;
  const still = f.p >= SIFT;
  const out = [0, 0, 0];
  const push = [0, 0];
  for (let i = 0; i < GRAIN_COUNT; i += 1) {
    const tx = heroX(target.x[i]);
    const ty = heroY(l, target.y[i]);
    let px = tx;
    let py = ty;
    let share = 0;
    if (!still) {
      let sx = tx;
      let sy = ty;
      let rFrom = target.r[i];
      if (source) {
        sx = heroX(source.x[i]);
        sy = heroY(l, source.y[i]);
        rFrom = source.r[i];
      } else if (buffer) {
        sx = buffer.x[i];
        sy = buffer.y[i];
        rFrom = buffer.r[i];
      }
      grain(seeds, i, sx, sy, rFrom, tx, ty, target.r[i], f.p, l.swirlX, l.swirlY, out);
      px = out[0];
      py = out[1];
      share = out[2];
    }
    if (f.stirs.length > 0) {
      push[0] = 0;
      push[1] = 0;
      stirOffset(px, py, f.stirs, f.clock, push);
      px += push[0];
      py += push[1];
    }
    xs[i] = px;
    ys[i] = py;
    fly[i] = share;
  }
}

function makeBuffer(x: number[], y: number[]): Buffer {
  'worklet';
  let top = Infinity;
  let bottom = -Infinity;
  for (let i = 0; i < y.length; i += 1) {
    top = Math.min(top, y[i]);
    bottom = Math.max(bottom, y[i]);
  }
  const span = Math.max(bottom - top, 1);
  const r: number[] = new Array(y.length);
  for (let i = 0; i < y.length; i += 1) r[i] = (y[i] - top) / span;
  return { x, y, r };
}

// Touch: taps sift to a tab, drags stir the grains; 2 s after the last touch the grains sift back to
// Balsamico and the loop restarts.

export type Touch = {
  manual: boolean;
  from: number;
  to: number;
  fromTab: number;
  start: number;
  buffer: Buffer | null;
  loopOrigin: number;
  lastTouch: number;
  trail: Stir[];
  finger: Stir | null;
  touching: boolean;
};

export function initialTouch(): Touch {
  'worklet';
  return {
    manual: false,
    from: 0,
    to: 0,
    fromTab: 0,
    start: -100,
    buffer: null,
    loopOrigin: 0,
    lastTouch: -100,
    trail: [],
    finger: null,
    touching: false,
  };
}

/** The clock time after which nothing moves any more (for a block that does not autoplay). */
export function restsAfter(s: Touch) {
  'worklet';
  return Math.max(s.start + SIFT, s.lastTouch + 1.6);
}

/** Once per frame: hands a finished touch back to the loop, and drops old trail samples. */
export function stepTouch(s: Touch, clock: number, autoPlay: boolean): Touch {
  'worklet';
  let n = s;
  if (s.manual && autoPlay && !s.touching && clock > s.lastTouch + RESUME_AFTER && clock >= s.start + SIFT) {
    if (s.to !== ORDER[0]) {
      // Sift back to the loop's first shape, then hand over to the loop.
      n = { ...s, from: s.to, fromTab: s.to, to: ORDER[0], buffer: null, start: clock };
    } else {
      n = { ...s, manual: false, loopOrigin: clock, trail: [] };
    }
  }
  if (n.trail.length > 0 && clock - n.trail[0].at > 1.6) {
    n = { ...n, trail: n.trail.filter((t) => clock - t.at <= 1.6) };
  }
  return n;
}

export function touchFrame(s: Touch, clock: number, autoPlay: boolean): Frame {
  'worklet';
  let f: Frame;
  if (s.manual || !autoPlay) {
    f = { from: s.from, to: s.to, fromTab: s.fromTab, p: clock - s.start, buffer: s.buffer, stirs: [], clock };
  } else {
    f = loopFrame(mod(clock - s.loopOrigin, LOOP));
  }
  const stirs = s.trail.slice();
  if (s.finger) {
    // A finger that stops stops dragging the grains along.
    const calm = Math.exp(-8 * Math.max(0, clock - s.finger.at));
    stirs.push({ ...s.finger, vx: s.finger.vx * calm, vy: s.finger.vy * calm });
  }
  f.stirs = stirs;
  f.clock = clock;
  return f;
}

/** Freezes the loop where it is so a touch can take over. */
function takeOver(s: Touch, clock: number, autoPlay: boolean): Touch {
  'worklet';
  if (s.manual || !autoPlay) return s;
  const f = loopFrame(mod(clock - s.loopOrigin, LOOP));
  return { ...s, manual: true, from: f.from, to: f.to, fromTab: f.fromTab, buffer: null, start: clock - Math.min(f.p, SIFT) };
}

export function tapTouch(
  s0: Touch,
  tab: number,
  clock: number,
  autoPlay: boolean,
  still: boolean,
  l: Layout,
  grains: Grains[],
  seeds: number[],
): Touch {
  'worklet';
  const s = { ...takeOver(s0, clock, autoPlay), manual: true, lastTouch: clock };
  const current = touchFrame(s, clock, autoPlay);
  if (tab === s.to && resting(current)) return s;
  if (still) {
    // Reduced motion: a cross-fade from the shape on screen.
    return { ...s, from: s.to, fromTab: s.to, to: tab, buffer: null, start: clock };
  }
  const xs: number[] = new Array(GRAIN_COUNT);
  const ys: number[] = new Array(GRAIN_COUNT);
  const fly: number[] = new Array(GRAIN_COUNT);
  positions(current, l, grains, seeds, xs, ys, fly);
  // The pushes are baked into the buffer.
  return { ...s, buffer: makeBuffer(xs, ys), from: -1, fromTab: s.to, to: tab, start: clock, trail: [] };
}

export function beginTouch(s: Touch, x: number, y: number, clock: number, autoPlay: boolean): Touch {
  'worklet';
  return {
    ...takeOver(s, clock, autoPlay),
    manual: true,
    touching: true,
    lastTouch: clock,
    finger: { x, y, vx: 0, vy: 0, at: clock, live: true },
  };
}

export function moveTouch(s: Touch, x: number, y: number, clock: number): Touch {
  'worklet';
  const f = s.finger;
  if (!f) return s;
  const dt = Math.max(clock - f.at, 1 / 120);
  const dx = x - f.x;
  const dy = y - f.y;
  if (dx * dx + dy * dy < 36) return { ...s, lastTouch: clock };
  const trail = s.trail.concat([{ x: f.x, y: f.y, vx: f.vx, vy: f.vy, at: clock, live: false }]);
  while (trail.length > 10) trail.shift();
  return {
    ...s,
    trail,
    lastTouch: clock,
    finger: { x, y, vx: f.vx * 0.4 + (dx / dt) * 0.6, vy: f.vy * 0.4 + (dy / dt) * 0.6, at: clock, live: true },
  };
}

export function endTouch(s: Touch, clock: number): Touch {
  'worklet';
  const f = s.finger;
  const trail = f ? s.trail.concat([{ x: f.x, y: f.y, vx: f.vx, vy: f.vy, at: clock, live: false }]) : s.trail;
  return { ...s, trail, finger: null, touching: false, lastTouch: clock };
}
