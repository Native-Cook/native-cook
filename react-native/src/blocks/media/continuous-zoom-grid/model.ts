/*
 The numbers behind Continuous Zoom Grid (a port of ContinuousZoomGridModel.swift; the numbers are
 the same): the library's 3,000 drawn photos (each one generated from its index), its months, the
 zoom levels, and the autoPlay timeline. Every function can run on the UI thread.
 */

export type RGB = [number, number, number];

/** The screen's view of the library: the zoom (photos per row), the photo it is anchored on and that photo's centre on screen (y). */
export type GridView = { z: number; anchor: number; fy: number };

export const COUNT = 3000;
/** The layouts the zoom blends between, in photos per row. */
export const LEVELS = [3, 5, 9, 14, 20];
export const MIN_Z = 3;
export const MAX_Z = 20;
export const LOOP = 8;

/** A small integer hash in 0…1, the same in every language. */
export function hash(a: number, b: number, c: number) {
  'worklet';
  let x = (Math.imul(a, 73856093) ^ Math.imul(b, 19349663) ^ Math.imul(c, 83492791)) >>> 0;
  x = (x ^ (x >>> 13)) >>> 0;
  x = Math.imul(x, 1274126177) >>> 0;
  x = (x ^ (x >>> 16)) >>> 0;
  return (x & 0xffff) / 65535;
}

function scaled(c: RGB, k: number): RGB {
  'worklet';
  return [c[0] * k, c[1] * k, c[2] * k];
}
function mixRGB(a: RGB, b: RGB, t: number): RGB {
  'worklet';
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

// MARK: Photos

type Palette = { top: RGB; bottom: RGB; ground: RGB; accent: RGB };
const PALETTES: Palette[] = [
  { top: [0.98, 0.56, 0.38], bottom: [1.0, 0.84, 0.6], ground: [0.34, 0.2, 0.26], accent: [1.0, 0.94, 0.72] }, // golden hour
  { top: [0.2, 0.46, 0.8], bottom: [0.7, 0.86, 0.96], ground: [0.1, 0.28, 0.42], accent: [1.0, 0.98, 0.92] }, // sea
  { top: [0.58, 0.76, 0.7], bottom: [0.9, 0.94, 0.84], ground: [0.14, 0.32, 0.22], accent: [1.0, 0.9, 0.56] }, // forest
  { top: [0.46, 0.4, 0.78], bottom: [0.96, 0.72, 0.8], ground: [0.24, 0.18, 0.4], accent: [1.0, 0.88, 0.8] }, // lavender
  { top: [0.9, 0.72, 0.56], bottom: [0.99, 0.9, 0.76], ground: [0.72, 0.46, 0.3], accent: [1.0, 0.62, 0.36] }, // desert
  { top: [0.04, 0.06, 0.16], bottom: [0.2, 0.14, 0.34], ground: [0.05, 0.05, 0.1], accent: [1.0, 0.76, 0.42] }, // night
  { top: [0.94, 0.54, 0.62], bottom: [1.0, 0.86, 0.78], ground: [0.52, 0.24, 0.34], accent: [1.0, 0.96, 0.88] }, // rose
  { top: [0.56, 0.62, 0.68], bottom: [0.88, 0.89, 0.9], ground: [0.22, 0.25, 0.28], accent: [0.98, 0.96, 0.9] }, // overcast
];

/**
 * rect [x, y, w, h, colour, opacity]; grad [x, y, w, h, top, bottom] (vertical); circle [x, y, r, colour, opacity];
 * glow [x, y, r, colour, opacity] (a radial gradient to clear); poly [points, colour, opacity].
 */
export type Op =
  | { t: 'rect'; x: number; y: number; w: number; h: number; c: RGB; a: number }
  | { t: 'grad'; x: number; y: number; w: number; h: number; c: RGB; c2: RGB }
  | { t: 'circle'; x: number; y: number; r: number; c: RGB; a: number }
  | { t: 'glow'; x: number; y: number; r: number; c: RGB; a: number }
  | { t: 'poly'; pts: number[]; c: RGB; a: number };

export type Photo = { ops: Op[]; /** Seconds, for a video; 0 for a still. */ duration: number };

/** Photos come in runs, like a library's days out: most share their run's kind (runs of 4) and palette (runs of 8). */
export function kind(i: number) {
  'worklet';
  return Math.floor(hash(hash(i, 8, 0) < 0.4 ? Math.floor(i / 4) : i + 5000, 2, 0) * 7.999);
}
function palette(i: number): Palette {
  'worklet';
  // Lights out of focus are always shot at night or at dusk.
  if (kind(i) === 2) return PALETTES[hash(i, 1, 0) < 0.6 ? 5 : 3];
  return PALETTES[Math.floor(hash(hash(i, 9, 0) < 0.6 ? Math.floor(i / 8) : i + 5000, 1, 0) * 7.999)];
}

/** A band filled below a wavy line: y = base + amp · sin(2π · (freq · x + phase)), 13 points. */
function wave(base: number, amp: number, freq: number, phase: number) {
  'worklet';
  const pts: number[] = [];
  for (let k = 0; k <= 12; k++) {
    const x = k / 12;
    pts.push(x, base + amp * Math.sin(2 * Math.PI * (freq * x + phase)));
  }
  pts.push(1, 1, 0, 1);
  return pts;
}

/** The photo at an index: a palette, one of eight kinds of picture and a few shape parameters, all from the hash of the index. */
export function photo(i: number): Photo {
  'worklet';
  const p = palette(i);
  const a = hash(i, 3, 0);
  const b = hash(i, 4, 0);
  const cc = hash(i, 7, 0);
  const ops: Op[] = [];
  const k0 = kind(i);
  if (k0 === 0) {
    // Sun over the sea.
    const sx = 0.25 + 0.5 * a;
    const h = 0.56 + 0.1 * b;
    ops.push({ t: 'grad', x: 0, y: 0, w: 1, h, c: p.top, c2: p.bottom });
    ops.push({ t: 'glow', x: sx, y: h - 0.04, r: 0.42, c: p.accent, a: 0.7 });
    ops.push({ t: 'circle', x: sx, y: h - 0.03, r: 0.065, c: p.accent, a: 1 });
    ops.push({ t: 'grad', x: 0, y: h, w: 1, h: 1 - h, c: scaled(p.ground, 1.4), c2: scaled(p.ground, 0.7) });
    for (let k = 0; k < 4; k++) {
      const wk = 0.2 - 0.04 * k;
      ops.push({ t: 'rect', x: sx - wk / 2, y: h + 0.04 + 0.07 * k, w: wk, h: 0.018, c: p.accent, a: 0.7 - 0.12 * k });
    }
  } else if (k0 === 1) {
    // Hills fading into haze.
    ops.push({ t: 'grad', x: 0, y: 0, w: 1, h: 1, c: p.top, c2: p.bottom });
    ops.push({ t: 'glow', x: 0.2 + 0.6 * a, y: 0.3, r: 0.5, c: p.accent, a: 0.45 });
    for (let k = 0; k < 4; k++) {
      ops.push({
        t: 'poly',
        pts: wave(0.42 + 0.14 * k, 0.05 + 0.02 * b, 0.8 + 0.5 * (k % 2), a + 0.3 * k),
        c: mixRGB(mixRGB(p.bottom, p.ground, 0.35), scaled(p.ground, 0.75), k / 3),
        a: 1,
      });
    }
  } else if (k0 === 2) {
    // City lights out of focus.
    ops.push({ t: 'grad', x: 0, y: 0, w: 1, h: 1, c: p.top, c2: p.bottom });
    for (let k = 0; k < 9; k++) {
      const col = k % 3 === 0 ? scaled(p.bottom, 1.6) : p.accent;
      ops.push({ t: 'glow', x: hash(i, 10 + k, 1), y: 0.15 + 0.8 * hash(i, 10 + k, 2), r: 0.07 + 0.12 * hash(i, 10 + k, 3), c: col, a: 0.55 + 0.35 * hash(i, 10 + k, 4) });
    }
  } else if (k0 === 3) {
    // Dunes.
    ops.push({ t: 'grad', x: 0, y: 0, w: 1, h: 1, c: p.top, c2: p.bottom });
    ops.push({ t: 'glow', x: 0.3 + 0.4 * a, y: 0.22, r: 0.3, c: p.accent, a: 0.5 });
    for (let k = 0; k < 3; k++) {
      ops.push({ t: 'poly', pts: wave(0.5 + 0.16 * k, 0.06, 0.5 + 0.25 * k, b + 0.37 * k), c: mixRGB(scaled(p.accent, 0.9), p.ground, 0.35 + 0.3 * k), a: 1 });
    }
  } else if (k0 === 4) {
    // Pines in mist.
    ops.push({ t: 'grad', x: 0, y: 0, w: 1, h: 1, c: p.bottom, c2: scaled(p.top, 0.9) });
    for (let row = 0; row < 2; row++) {
      const base = 0.62 + 0.22 * row;
      const col = mixRGB(p.top, scaled(p.ground, 0.6), 0.45 + 0.5 * row);
      for (let k = 0; k < 7; k++) {
        const x = (k + 0.5 * row + 0.3 * hash(i, k, row)) / 6.5;
        const hgt = 0.26 + 0.14 * hash(i, k + 9, row);
        ops.push({ t: 'poly', pts: [x - 0.07, base, x, base - hgt, x + 0.07, base], c: col, a: 1 });
      }
      ops.push({ t: 'rect', x: 0, y: base - 0.01, w: 1, h: 1, c: col, a: 1 });
      if (row === 0) ops.push({ t: 'rect', x: 0, y: base - 0.16, w: 1, h: 0.5, c: p.bottom, a: 0.45 });
    }
  } else if (k0 === 5) {
    // Petals, close up.
    ops.push({ t: 'grad', x: 0, y: 0, w: 1, h: 1, c: scaled(p.ground, 0.9), c2: scaled(p.ground, 0.5) });
    const cols = [p.accent, p.top, p.bottom];
    for (let k = 0; k < 6; k++) {
      ops.push({ t: 'glow', x: hash(i, 20 + k, 1), y: hash(i, 20 + k, 2), r: 0.22 + 0.2 * hash(i, 20 + k, 3), c: cols[k % 3], a: 0.75 });
    }
  } else if (k0 === 6) {
    // A building in low sun.
    const x = 0.16 + 0.3 * a;
    const top = 0.18 + 0.2 * b;
    ops.push({ t: 'grad', x: 0, y: 0, w: 1, h: 1, c: p.top, c2: p.bottom });
    ops.push({ t: 'glow', x: 1 - a, y: 0.2, r: 0.4, c: p.accent, a: 0.5 });
    ops.push({ t: 'rect', x, y: top, w: 0.4, h: 1, c: mixRGB(p.bottom, p.accent, 0.5), a: 1 });
    ops.push({ t: 'poly', pts: [x + 0.4, top, x + 0.56, top + 0.08, x + 0.56, 1, x + 0.4, 1], c: scaled(p.ground, 1.3), a: 1 });
    for (let r = 0; r < 6; r++) {
      for (let q = 0; q < 3; q++) ops.push({ t: 'rect', x: x + 0.05 + q * 0.12, y: top + 0.06 + r * 0.12, w: 0.07, h: 0.07, c: p.ground, a: 0.55 });
    }
  } else {
    // A beach.
    const h = 0.46 + 0.08 * cc;
    ops.push({ t: 'grad', x: 0, y: 0, w: 1, h, c: p.top, c2: p.bottom });
    ops.push({ t: 'glow', x: 0.2 + 0.6 * a, y: h - 0.1, r: 0.3, c: p.accent, a: 0.45 });
    ops.push({ t: 'grad', x: 0, y: h, w: 1, h: 0.2, c: scaled(p.top, 0.8), c2: scaled(p.top, 1.1) });
    ops.push({ t: 'poly', pts: wave(h + 0.18, 0.025, 1.4, b), c: scaled(p.accent, 0.97), a: 1 });
    ops.push({ t: 'poly', pts: wave(h + 0.22, 0.03, 1.4, b + 0.05), c: mixRGB(p.accent, p.ground, 0.25), a: 1 });
  }
  const duration = hash(i, 5, 0) > 0.84 ? 3 + Math.floor(hash(i, 6, 0) * 55) : 0;
  return { ops, duration };
}

/** What a photo shows when it is too small for its picture: its sky over its ground, split at a height (1 for one colour). */
export function tiny(i: number): { sky: RGB; ground: RGB; split: number } {
  'worklet';
  const p = palette(i);
  const sky = mixRGB(p.top, p.bottom, 0.5);
  switch (kind(i)) {
    case 0:
      return { sky, ground: p.ground, split: 0.6 };
    case 1:
      return { sky, ground: mixRGB(p.ground, p.bottom, 0.4), split: 0.5 };
    case 2:
      return { sky: mixRGB(p.top, p.bottom, 0.4), ground: sky, split: 1 };
    case 3:
      return { sky, ground: mixRGB(scaled(p.accent, 0.9), p.ground, 0.5), split: 0.55 };
    case 4:
      return { sky: p.bottom, ground: mixRGB(p.top, scaled(p.ground, 0.6), 0.7), split: 0.55 };
    case 5:
      return { sky: mixRGB(scaled(p.ground, 0.7), p.accent, 0.25), ground: sky, split: 1 };
    case 6:
      return { sky, ground: mixRGB(p.bottom, p.accent, 0.5), split: 0.45 };
    default:
      return { sky, ground: mixRGB(p.accent, p.ground, 0.2), split: 0.62 };
  }
}

// MARK: Months

const NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** The first index of each month, newest first, and its label. */
function buildMonths() {
  const starts: number[] = [];
  const labels: string[] = [];
  let i = 0;
  let m = 9;
  let y = 2026;
  while (i < COUNT) {
    starts.push(i);
    labels.push(`${NAMES[m]} ${y}`);
    i += 30 + Math.floor(hash(m, y, 9) * 80);
    m -= 1;
    if (m < 0) {
      m = 11;
      y -= 1;
    }
  }
  return { starts, labels };
}
export const MONTHS = buildMonths();

export function monthIndex(i: number) {
  'worklet';
  let lo = 0;
  let hi = MONTHS.starts.length - 1;
  while (lo < hi) {
    const mid = Math.floor((lo + hi + 1) / 2);
    if (MONTHS.starts[mid] <= i) lo = mid;
    else hi = mid - 1;
  }
  return lo;
}

// MARK: Easing

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = clamp01((x - a) / (b - a));
  return t * t * (3 - 2 * t);
}
export function easeInOut(x: number) {
  'worklet';
  const u = clamp01(x);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}
/** Ease in (cubic) for the first half, then out with a small overshoot (about 5 %). */
export function easeInOutBack(x: number) {
  'worklet';
  const u = clamp01(x);
  if (u < 0.5) return 4 * u * u * u;
  const v = 2 * u - 1;
  const c1 = 1.2;
  const c3 = c1 + 1;
  return 0.5 + 0.5 * (1 + c3 * Math.pow(v - 1, 3) + c1 * Math.pow(v - 1, 2));
}

// MARK: autoPlay

/** The photo the screen rests on (its centre at half the screen's height). */
export const REST_ANCHOR = 901;
/** The photo the zoom comes back in on: two rows lower, in the left column at 3 per row. */
export const RETURN_ANCHOR = 906;

export function row(i: number, n: number) {
  'worklet';
  return Math.floor(i / n);
}

/**
 * autoPlay (LOOP = 8 s): rest at 3 per row; 1.0 – 2.8 zoom out to 20 per row about the middle photo;
 * hold; 3.4 – 4.4 the grid is dragged down until the return photo sits where it will land; 4.6 – 6.4
 * zoom back in to 3 per row about that photo, landing with a small overshoot; rest to 8.0 (the same
 * frame as 0).
 */
export function viewAt(time: number, w: number, h: number): GridView {
  'worklet';
  let t = time % LOOP;
  if (t < 0) t += LOOP;
  const restFy = h * 0.5;
  // Where the return photo sits at rest (it lands there again).
  const target = restFy + ((row(RETURN_ANCHOR, 3) - row(REST_ANCHOR, 3)) * w) / 3;
  // At 20 per row both photos share a row, so dragging moves the return photo to its target.
  const dense = MAX_Z;
  const rowGap = ((row(RETURN_ANCHOR, dense) - row(REST_ANCHOR, dense)) * w) / dense;
  if (t < 1.0) return { z: 3, anchor: REST_ANCHOR, fy: restFy };
  if (t < 2.8) {
    const u = easeInOut((t - 1.0) / 1.8);
    return { z: 3 * Math.pow(dense / 3, u), anchor: REST_ANCHOR, fy: restFy };
  }
  if (t < 4.5) {
    const u = easeInOut((t - 3.4) / 1.0);
    return { z: dense, anchor: REST_ANCHOR, fy: restFy + (target - rowGap - restFy) * u };
  }
  if (t < 6.4) {
    const u = easeInOutBack((t - 4.6) / 1.8);
    return { z: dense * Math.pow(3 / dense, u), anchor: RETURN_ANCHOR, fy: target };
  }
  return { z: 3, anchor: REST_ANCHOR, fy: restFy };
}

/** The two layouts either side of z and their opacities: [n, opacity, n, opacity]. */
export function layers(z: number): number[] {
  'worklet';
  const l = LEVELS;
  if (z <= l[0]) return [l[0], 1];
  if (z >= l[l.length - 1]) return [l[l.length - 1], 1];
  let k = 0;
  while (!(z >= l[k] && z < l[k + 1])) k += 1;
  const f = Math.log(z / l[k]) / Math.log(l[k + 1] / l[k]);
  const b = smoothstep(0.2, 0.8, f);
  return [l[k], Math.min(1, 2 * (1 - b)), l[k + 1], b];
}

/** The nearest layout to z, in log space. */
export function nearestLevel(z: number) {
  'worklet';
  let best = 0;
  for (let i = 0; i < LEVELS.length; i++) if (Math.abs(Math.log(LEVELS[i] / z)) < Math.abs(Math.log(LEVELS[best] / z))) best = i;
  return best;
}

/** The month of the photo in the middle of the screen (its index in MONTHS). */
export function centreMonth(w: number, h: number, v: GridView) {
  'worklet';
  const ls = layers(v.z);
  const n = ls.length > 2 && ls[3] >= 0.5 ? ls[2] : ls[0];
  const cell = (w / n) * (n / v.z);
  const ra = row(v.anchor, n);
  const r = ra + Math.floor((h / 2 - v.fy) / cell + 0.5);
  return monthIndex(Math.min(COUNT - 1, Math.max(0, r * n + Math.floor(n / 2))));
}
