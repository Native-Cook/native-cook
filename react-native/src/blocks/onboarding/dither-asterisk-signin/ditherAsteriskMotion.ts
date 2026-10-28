import { Skia, type SkPath, type SkPathBuilder } from '@shopify/react-native-skia';

/*
 Dither Asterisk Sign In — the pixel mark, the autoPlay script and the touch springs (a port of
 DitherAsteriskSigninMotion.swift, the reference).

 The mark is a 31 × 31 grid of cells (an asterisk of four bars). Its 31 columns are grouped into
 8 vertical slices. A frame is, per column, an offset in cells (drawn rounded to whole cells) and
 an energy 0…1 that thins the column's cells into a glyph dither (full → ■ → 1 → 0 → + → empty),
 plus a sideways shift for three row bands. Cells between a slice's old and new place are drawn as
 a fading streak of glyphs.

 autoPlay: LOOP = 7 s
   0.00 – 0.80  rest, the whole mark
   0.80 – 1.98  slices slide out one after another, right to left, and dither (build); while out
                they drift by a cell or so (3 sine periods per loop)
   2.70 – 4.05  slices jump to a second set of offsets, row bands shear sideways (peak scramble)
   4.05 – 5.55  slices spring home left to right with a one-cell overshoot, cells fill back in
   5.10 – 6.10  a faint glow behind the mark as it is whole again
   5.55 – 7.00  rest (the frame at 7 is the frame at 0)

 Helpers are declared above the worklets that call them (a worklet declared further down is
 undefined on the UI thread).
*/

export const LOOP = 7;
export const N = 31;
export const PITCH = 22 / 3;
export const CELL = 22 / 3 - 1;
const ROW_MIN = -8;
const ROW_MAX = 38;
const ROWS = ROW_MAX - ROW_MIN + 1;
const IDLE_BEFORE_BACK = 2;

const WIDTHS = [4, 3, 5, 2, 4, 3, 6, 4];
const BUILD = [0, -2, 1, -5, 2, 6, 3, -5];
const PEAK = [1, 3, -2, 2, -6, -4, 5, 2];
const ENERGY = [0, 0.3, 0, 1, 0.5, 1, 0.2, 0.8];
/** Row bands that shear sideways at the peak: first row, last row, shift in cells. */
const BAND_FIRST = [6, 14, 21];
const BAND_LAST = [9, 16, 24];
const BAND_SHIFT = [2, -3, 4];
const BAYER = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5];

/** The slice each column belongs to. */
const SLICE_OF: number[] = (() => {
  const out: number[] = [];
  for (let i = 0; i < WIDTHS.length; i++) for (let k = 0; k < WIDTHS[i]; k++) out.push(i);
  return out;
})();

/** The asterisk, row-major, N × N (1 = on). */
const MASK: number[] = (() => {
  const out: number[] = new Array(N * N).fill(0);
  const k = 1 / Math.sqrt(2);
  for (let r = 0; r < N; r++) {
    for (let c = 0; c < N; c++) {
      const x = c - 15;
      const y = r - 15;
      let on = Math.abs(y) <= 3.2 && Math.abs(x) <= 15.5;
      on = on || (Math.abs(x) <= 2.6 && Math.abs(y) <= 15.5);
      for (const s of [1, -1]) {
        const along = (x + s * y) * k;
        const across = (y - s * x) * k;
        if (Math.abs(across) <= 2.3 && Math.abs(along) <= 14.4) on = true;
        const ex = 14.4 * k;
        const ey = 14.4 * k * s;
        if ((x - ex) * (x - ex) + (y - ey) * (y - ey) <= 2.3 * 2.3) on = true;
        if ((x + ex) * (x + ex) + (y + ey) * (y + ey) <= 2.3 * 2.3) on = true;
      }
      out[r * N + c] = on ? 1 : 0;
    }
  }
  return out;
})();

export type Frame = {
  offset: number[];
  energy: number[];
  shift: number[];
  glow: number;
};

function zeros(n: number): number[] {
  'worklet';
  const out: number[] = [];
  for (let i = 0; i < n; i++) out.push(0);
  return out;
}

function emptyFrame(): Frame {
  'worklet';
  return { offset: zeros(N), energy: zeros(N), shift: zeros(3), glow: 0 };
}

function inMask(c: number, r: number): boolean {
  'worklet';
  return c >= 0 && c < N && r >= 0 && r < N && MASK[r * N + c] === 1;
}

function seg(t: number, a: number, b: number): number {
  'worklet';
  return t <= a ? 0 : t >= b ? 1 : (t - a) / (b - a);
}

function easeOut(x: number): number {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

function easeInOut(x: number): number {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** Whole cells, rounding halves up in every stack. */
export function cells(x: number): number {
  'worklet';
  return Math.floor(x + 0.5);
}

/** Step response of a spring from 1 to 0 (response 0.5 s, damping 0.55). */
function springHome(tau: number): number {
  'worklet';
  if (tau <= 0) return 1;
  const w0 = (2 * Math.PI) / 0.5;
  const z = 0.55;
  const wd = w0 * Math.sqrt(1 - z * z);
  return Math.exp(-z * w0 * tau) * (Math.cos(wd * tau) + ((z * w0) / wd) * Math.sin(wd * tau));
}

/** The autoPlay frame at loop time t. */
export function autoFrame(t: number): Frame {
  'worklet';
  const f = emptyFrame();
  const sliceOffset: number[] = [];
  const sliceEnergy: number[] = [];
  for (let i = 0; i < WIDTHS.length; i++) {
    const s = 0.8 + 0.09 * (7 - i);
    const p = 2.7 + 0.06 * ((5 * i) % 8);
    const h = 4.05 + 0.1 * i;
    const a = easeInOut(seg(t, s, s + 0.55));
    const b = easeInOut(seg(t, p, p + 0.45));
    const rise = easeOut(seg(t, s, s + 0.6));
    const fall = easeInOut(seg(t, h + 0.1, h + 0.8));
    // A slow drift while the slices are out (3 periods per loop), ticking by whole cells.
    const drift = 1.3 * Math.sin((2 * Math.PI * 3 * t) / LOOP + 1.7 * i) * rise * (1 - fall);
    sliceOffset.push((BUILD[i] * a * (1 - b) + PEAK[i] * b) * springHome(t - h) + drift);
    sliceEnergy.push(ENERGY[i] * rise * (1 - fall));
  }
  for (let c = 0; c < N; c++) {
    f.offset[c] = sliceOffset[SLICE_OF[c]];
    f.energy[c] = sliceEnergy[SLICE_OF[c]];
  }
  const shear = easeInOut(seg(t, 2.75, 3.05)) * (1 - easeInOut(seg(t, 3.75, 4.05)));
  for (let k = 0; k < 3; k++) f.shift[k] = BAND_SHIFT[k] * shear;
  const up = easeOut(seg(t, 5.1, 5.4));
  const down = easeInOut(seg(t, 5.4, 6.1));
  f.glow = 0.07 * up * (1 - down);
  return f;
}

/** Glyph level 0…5 from a density and the cell's Bayer threshold. */
function level(d: number, c: number, r: number): number {
  'worklet';
  const b = (BAYER[(((r % 4) + 4) % 4) * 4 + (((c % 4) + 4) % 4)] + 0.5) / 16;
  const q = d - b;
  if (q > 0) return 5;
  if (q > -0.15) return 4;
  if (q > -0.3) return 3;
  if (q > -0.45) return 2;
  if (q > -0.6) return 1;
  return 0;
}

function rect(p: SkPathBuilder, x: number, y: number, s: number, u0: number, v0: number, u1: number, v1: number) {
  'worklet';
  p.addRect(Skia.XYWHRect(x + u0 * s, y + v0 * s, (u1 - u0) * s, (v1 - v0) * s));
}

function addGlyph(p: SkPathBuilder, lv: number, x: number, y: number, s: number) {
  'worklet';
  if (lv === 5) {
    rect(p, x, y, s, 0, 0, 1, 1);
  } else if (lv === 4) {
    rect(p, x, y, s, 0.19, 0.19, 0.81, 0.81);
  } else if (lv === 3) {
    rect(p, x, y, s, 0.42, 0.12, 0.58, 0.88);
    rect(p, x, y, s, 0.26, 0.12, 0.42, 0.27);
  } else if (lv === 2) {
    rect(p, x, y, s, 0.24, 0.12, 0.76, 0.26);
    rect(p, x, y, s, 0.24, 0.74, 0.76, 0.88);
    rect(p, x, y, s, 0.24, 0.26, 0.38, 0.74);
    rect(p, x, y, s, 0.62, 0.26, 0.76, 0.74);
  } else if (lv === 1) {
    rect(p, x, y, s, 0.18, 0.43, 0.82, 0.57);
    rect(p, x, y, s, 0.43, 0.18, 0.57, 0.43);
    rect(p, x, y, s, 0.43, 0.57, 0.57, 0.82);
  }
}

/** The whole mark as one path, its grid's top-left at (left, top). */
export function markPath(f: Frame, left: number, top: number): SkPath {
  'worklet';
  const rowShift = zeros(ROWS);
  for (let k = 0; k < 3; k++) {
    const s = cells(f.shift[k]);
    for (let r = BAND_FIRST[k]; r <= BAND_LAST[k]; r++) rowShift[r - ROW_MIN] = s;
  }
  const p = Skia.PathBuilder.Make();
  for (let c = 0; c < N; c++) {
    const d = cells(f.offset[c]);
    const e = f.energy[c];
    const body = 1 - e;
    const span = Math.abs(d);
    const dir = d > 0 ? 1 : -1;
    const x = left + c * PITCH;
    for (let ri = 0; ri < ROWS; ri++) {
      const r = ri + ROW_MIN;
      const sc = c - rowShift[ri];
      const sr = r - d;
      let lv = 0;
      if (inMask(sc, sr)) {
        lv = level(body, c, r);
      } else if (span > 0) {
        for (let j = 1; j <= span; j++) {
          if (inMask(sc, sr + dir * j)) {
            const dens = 0.8 * e * (1 - (j - 1) / span);
            lv = Math.min(4, level(dens, c, r));
            break;
          }
        }
      }
      if (lv > 0) addGlyph(p, lv, x, top + r * PITCH, CELL);
    }
  }
  return p.detach();
}

/** The loop clock, and per-column springs while a finger drives the mark. */
export type Sim = {
  epoch: number;
  lastTime: number;
  lastAuto: Frame;
  live: boolean;
  x: number[];
  v: number[];
  e: number[];
  shift: number[];
  down: boolean;
  fingerCol: number;
  lastX: number;
  lastY: number;
  lastTouch: number;
  releasedAt: number;
  feltSlice: boolean;
  feltWhole: boolean;
  /** Bumped on each moment that deserves a haptic (the first slice of a drag, whole again). */
  feel: number;
};

export function makeSim(): Sim {
  'worklet';
  return {
    epoch: NaN,
    lastTime: NaN,
    lastAuto: emptyFrame(),
    live: false,
    x: zeros(N),
    v: zeros(N),
    e: zeros(N),
    shift: zeros(3),
    down: false,
    fingerCol: 0,
    lastX: 0,
    lastY: 0,
    lastTouch: NaN,
    releasedAt: NaN,
    feltSlice: false,
    feltWhole: true,
    feel: 0,
  };
}

const K = Math.pow((2 * Math.PI) / 0.55, 2);
const DAMP = 2 * 0.5 * ((2 * Math.PI) / 0.55);

function simStep(s: Sim, dt: number) {
  'worklet';
  const steps = Math.max(1, Math.ceil(dt * 240));
  const h = dt / steps;
  for (let n = 0; n < steps; n++) {
    for (let c = 0; c < N; c++) {
      // The spring of the columns under the finger lets go a little while it is down.
      const w = s.down ? Math.exp(-Math.pow((c - s.fingerCol) / 1.4, 2)) : 0;
      const a = -K * (1 - 0.65 * w) * s.x[c] - DAMP * s.v[c];
      s.v[c] += a * h;
      s.x[c] += s.v[c] * h;
      s.e[c] *= Math.exp(-h / 0.6);
    }
    for (let k = 0; k < 3; k++) s.shift[k] *= Math.exp(-h / 0.12);
  }
}
/** The frame at `now` (seconds). */
export function simFrame(s: Sim, now: number, auto: boolean): Frame {
  'worklet';
  if (isNaN(s.epoch)) s.epoch = now;
  if (!s.live) {
    const t = auto ? (now - s.epoch) % LOOP : 0;
    s.lastAuto = autoFrame(t);
    s.lastTime = now;
    return s.lastAuto;
  }
  const prev = isNaN(s.lastTime) ? now : s.lastTime;
  const dt = Math.min(Math.max(now - prev, 0), 1 / 20);
  s.lastTime = now;
  simStep(s, dt);
  const f = emptyFrame();
  let whole = true;
  for (let c = 0; c < N; c++) {
    f.offset[c] = 9 * Math.tanh(s.x[c] / 9);
    f.energy[c] = Math.min(1, s.e[c]);
    if (cells(f.offset[c]) !== 0 || s.e[c] > 0.04) whole = false;
  }
  for (let k = 0; k < 3; k++) {
    f.shift[k] = s.shift[k];
    if (cells(s.shift[k]) !== 0) whole = false;
  }
  if (!s.down) {
    if (whole && !s.feltWhole) {
      s.feltWhole = true;
      s.feel += 1;
    }
    if (whole && !isNaN(s.releasedAt) && now - s.releasedAt >= IDLE_BEFORE_BACK) {
      s.live = false;
      s.epoch = now;
      s.lastAuto = autoFrame(0);
      return s.lastAuto;
    }
  }
  return f;
}

/**
 * The finger at (px, py) pt, over column `col` (fractional, column centres at whole numbers).
 * Columns near it are dragged by the finger's movement since the last event: vertical motion pulls
 * them along, horizontal motion slices them up or down by their slice's sign; their dither follows
 * the finger's speed.
 */
export function simTouch(s: Sim, px: number, py: number, col: number, now: number) {
  'worklet';
  if (!s.live) {
    s.live = true;
    for (let c = 0; c < N; c++) {
      s.x[c] = s.lastAuto.offset[c];
      s.e[c] = s.lastAuto.energy[c];
      s.v[c] = 0;
    }
    for (let k = 0; k < 3; k++) s.shift[k] = s.lastAuto.shift[k];
    s.lastTime = now;
  }
  if (!s.down) {
    s.feltSlice = false;
    s.lastX = px;
    s.lastY = py;
    s.lastTouch = now;
  }
  s.down = true;
  s.feltWhole = false;
  s.fingerCol = col;
  const dx = px - s.lastX;
  const dy = py - s.lastY;
  const dt = Math.max(now - (isNaN(s.lastTouch) ? now : s.lastTouch), 1 / 240);
  const speed = Math.sqrt(dx * dx + dy * dy) / dt;
  for (let c = 0; c < N; c++) {
    const w = Math.exp(-Math.pow((c - col) / 1.4, 2));
    const sign = SLICE_OF[c] % 2 === 0 ? 1 : -1;
    s.x[c] = Math.min(Math.max(s.x[c] + (w * (dy + sign * Math.abs(dx) * 0.8)) / PITCH, -14), 14);
    s.e[c] = Math.max(s.e[c], w * Math.min(1, speed / 900));
  }
  s.lastX = px;
  s.lastY = py;
  s.lastTouch = now;
  if (!s.feltSlice) {
    for (let c = 0; c < N; c++) {
      if (Math.abs(s.x[c]) > 3) {
        s.feltSlice = true;
        s.feel += 1;
        break;
      }
    }
  }
}

export function simRelease(s: Sim, now: number) {
  'worklet';
  if (!s.down) return;
  s.down = false;
  s.releasedAt = now;
}
