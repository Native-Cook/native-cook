// Shape Shift Viewfinder: the outlines as radial functions r(θ)/R (θ from the top, clockwise), sampled at N angles,
// and each one split into six frequency bands. A morph springs every band on its own (band 0, the size and centre,
// first; band 5, the finest detail, last), so the outline is smooth on every frame by construction.

import { G, SHAPE_NAMES } from './ssv-theme';

const N = G.N;
const TAU = Math.PI * 2;

/** Harmonic ranges of the six bands (inclusive). */
const BAND_K: [number, number][] = [
  [0, 0],
  [1, 1],
  [2, 3],
  [4, 7],
  [8, 15],
  [16, N / 2],
];
export const BANDS_N = BAND_K.length;

/** A positive radius r(θ)/R, or exactly 192 radii sampled clockwise from the top. */
export type Outline = ((theta: number) => number) | readonly number[];

const squircle = (th: number) => 0.86 / Math.pow(Math.pow(Math.abs(Math.sin(th)), 5) + Math.pow(Math.abs(Math.cos(th)), 5), 1 / 5);

/** Four round lobes, on the diagonals (creative spec §6). */
const clover = (th: number) => 0.74 + 0.26 * Math.pow(Math.abs(Math.cos(2 * (th - Math.PI / 4))), 0.55);
/** Six round lobes. */
const flower = (th: number) => 0.8 + 0.2 * Math.pow(Math.abs(Math.cos(3 * th)), 0.6);
/** A squircle with twelve scallops, three a side, creases at the corners (the reference's scalloped square). */
const cookie = (th: number) => squircle(th) * (1 + 0.035 * Math.cos(12 * th));
const circle = () => 1;

/**
 * The classic parametric heart, re-sampled radially about a point 18 % below its bounding box's centre, with the bottom tip
 * rounded to r 6 pt and the top notch to r 4 pt at R = 150 pt (a morphological opening and closing of the raster, read
 * out radially; `.work/shape-shift-viewfinder/build_heart.py`). Starts at the top (the notch), clockwise.
 */
const HEART: number[] = [
  0.6704, 0.6864, 0.7255, 0.7687, 0.8061, 0.8391, 0.8685, 0.8946, 0.9175, 0.9377, 0.9555, 0.9708,
  0.9837, 0.9945, 1.0032, 1.0099, 1.0148, 1.0180, 1.0195, 1.0192, 1.0171, 1.0133, 1.0077, 1.0001,
  0.9906, 0.9790, 0.9651, 0.9489, 0.9301, 0.9085, 0.8839, 0.8561, 0.8254, 0.7920, 0.7561, 0.7185,
  0.6800, 0.6417, 0.6045, 0.5688, 0.5357, 0.5058, 0.4784, 0.4533, 0.4308, 0.4105, 0.3922, 0.3758,
  0.3610, 0.3477, 0.3359, 0.3254, 0.3157, 0.3069, 0.2990, 0.2917, 0.2849, 0.2787, 0.2734, 0.2687,
  0.2642, 0.2601, 0.2566, 0.2536, 0.2509, 0.2485, 0.2465, 0.2448, 0.2432, 0.2419, 0.2411, 0.2406,
  0.2404, 0.2405, 0.2409, 0.2417, 0.2427, 0.2439, 0.2457, 0.2480, 0.2504, 0.2531, 0.2563, 0.2599,
  0.2641, 0.2689, 0.2743, 0.2805, 0.2873, 0.2950, 0.3040, 0.3142, 0.3261, 0.3387, 0.3486, 0.3540,
  0.3556, 0.3540, 0.3487, 0.3392, 0.3271, 0.3152, 0.3047, 0.2956, 0.2877, 0.2807, 0.2746, 0.2693,
  0.2647, 0.2606, 0.2568, 0.2536, 0.2509, 0.2485, 0.2464, 0.2446, 0.2433, 0.2423, 0.2414, 0.2408,
  0.2406, 0.2409, 0.2416, 0.2424, 0.2435, 0.2452, 0.2471, 0.2491, 0.2514, 0.2540, 0.2572, 0.2608,
  0.2647, 0.2692, 0.2741, 0.2794, 0.2853, 0.2919, 0.2992, 0.3072, 0.3162, 0.3260, 0.3366, 0.3484,
  0.3616, 0.3764, 0.3928, 0.4111, 0.4313, 0.4537, 0.4788, 0.5062, 0.5363, 0.5696, 0.6052, 0.6424,
  0.6809, 0.7196, 0.7571, 0.7927, 0.8261, 0.8568, 0.8845, 0.9092, 0.9308, 0.9495, 0.9657, 0.9796,
  0.9913, 1.0007, 1.0081, 1.0138, 1.0175, 1.0193, 1.0196, 1.0183, 1.0151, 1.0100, 1.0032, 0.9945,
  0.9837, 0.9708, 0.9555, 0.9377, 0.9173, 0.8940, 0.8678, 0.8385, 0.8054, 0.7678, 0.7245, 0.6857,
];

const sample = (f: (th: number) => number) => {
  const out: number[] = [];
  for (let j = 0; j < N; j++) out.push(f((j / N) * TAU));
  return out;
};

export const DEFAULT_SHAPES: Readonly<Record<(typeof SHAPE_NAMES)[number], Outline>> = {
  clover,
  heart: HEART,
  flower,
  cookie,
  squircle,
  circle,
};

/** Splits one outline into its band components (each a length-N array; their sum is the outline). */
const splitBands = (r: number[]): number[][] => {
  const a: number[] = [];
  const b: number[] = [];
  for (let k = 0; k <= N / 2; k++) {
    let sa = 0;
    let sb = 0;
    for (let j = 0; j < N; j++) {
      const ph = (TAU * k * j) / N;
      sa += r[j] * Math.cos(ph);
      sb += r[j] * Math.sin(ph);
    }
    const norm = k === 0 || k === N / 2 ? 1 / N : 2 / N;
    a.push(sa * norm);
    b.push(sb * norm);
  }
  return BAND_K.map(([k0, k1]) => {
    const out: number[] = new Array(N).fill(0);
    for (let k = k0; k <= k1; k++) {
      for (let j = 0; j < N; j++) {
        const ph = (TAU * k * j) / N;
        out[j] += a[k] * Math.cos(ph) + b[k] * Math.sin(ph);
      }
    }
    return out;
  });
};

/** Validate and precompute a named outline collection once on the JS thread. */
export const createShapeBank = (shapes: Readonly<Record<string, Outline>>): { names: string[]; bands: number[]; count: number } => {
  const names = Object.keys(shapes);
  if (names.length === 0) throw new Error('Shape Shift Viewfinder requires at least one outline.');
  const bands: number[] = [];
  for (const name of names) {
    const outline = shapes[name];
    if (typeof outline !== 'function' && (!Array.isArray(outline) || outline.length !== N)) {
      throw new Error(`Outline "${name}" must be a radial function or exactly ${N} radii.`);
    }
    const radii = typeof outline === 'function' ? sample(outline) : Array.from(outline);
    for (let j = 0; j < N; j++) {
      if (!Number.isFinite(radii[j]) || radii[j] <= 0) {
        throw new Error(`Outline "${name}" must have finite positive radii (sample ${j}).`);
      }
    }
    for (const band of splitBands(radii)) bands.push(...band);
  }
  return { names, bands, count: names.length };
};

/**
 * Every shape's bands, flat: index ((shape · BANDS_N + band) · N + j). An outline is Σ_b Σ_s w[b·S + s] · band,
 * where w holds, per band, a weight per shape (one-hot at rest).
 */
export const BANDS: number[] = createShapeBank(DEFAULT_SHAPES).bands;

export const SHAPES_N = SHAPE_NAMES.length;

/** Weights (BANDS_N × count) of one shape at rest. */
export const restWeights = (shape: number, count = 6): number[] => {
  'worklet';
  const w: number[] = [];
  for (let b = 0; b < 6; b++) for (let s = 0; s < count; s++) w.push(s === shape ? 1 : 0);
  return w;
};

/** The outline (r/R at N angles) for a set of band weights. Zero weights are skipped. */
export const radiiOf = (w: number[], bands: number[], count = bands.length / (6 * 192)): number[] => {
  'worklet';
  const n = 192;
  const out: number[] = new Array(n).fill(0);
  let deviation = 0;
  for (let b = 0; b < 6; b++) {
    let weightSquares = 0;
    for (let s = 0; s < count; s++) {
      const ws = w[b * count + s];
      weightSquares += ws * ws;
      if (ws > -1e-5 && ws < 1e-5) continue;
      const base = (s * 6 + b) * n;
      for (let j = 0; j < n; j++) out[j] += ws * bands[base + j];
    }
    deviation = Math.max(deviation, Math.abs(1 - weightSquares));
  }
  // Gate-driven anti-kink guard: band springs can briefly sharpen a tip beyond either endpoint.
  // Depart from the exact linear band sum only during a morph, blending a small periodic [1,2,1]/4
  // convolution by the weights' distance from one-hot. Settled outlines remain bit-identical.
  const strength = Math.min(1, 0.625 * deviation);
  if (strength < 1e-8) return out;
  const guarded: number[] = new Array(n);
  for (let j = 0; j < n; j++) {
    const smooth = (out[(j + n - 1) % n] + 2 * out[j] + out[(j + 1) % n]) / 4;
    guarded[j] = out[j] + strength * (smooth - out[j]);
  }
  return guarded;
};

/**
 * A tap starts the morph where the finger is: the outline keeps QN copies of its band weights, each QSTEP later than the
 * last, and a sample at angular distance |Δ| from the tap reads the copy |Δ| / 180° of the way down the list (|Δ| / 900°/s, 0 to
 * 200 ms, the new shape running both ways round the rim to meet on the far side), the copies lerped.
 */
export const QN = 12;
export const QSTEP = 0.2 / (QN - 1);

/** Weights of one shape at rest in every delay copy. */
export const restWeightsQ = (shape: number, count = 6): number[] => {
  'worklet';
  const one = restWeights(shape, count);
  const out: number[] = [];
  for (let q = 0; q < QN; q++) for (let i = 0; i < one.length; i++) out.push(one[i]);
  return out;
};

const DENT_SIGMA = (26 * Math.PI) / 180;

/** The outline for the QN weight copies; `tapA` is the tap's angle (rad, clockwise from the top), `dent` the rim's dent in r/R. */
export const radiiDelayed = (w: number[], bands: number[], count: number, tapA: number, dent: number): number[] => {
  'worklet';
  const n = 192;
  const wn = count * 6;
  let same = true;
  for (let q = 1; q < QN && same; q++) {
    for (let i = 0; i < wn; i++) {
      if (Math.abs(w[q * wn + i] - w[(q - 1) * wn + i]) > 2e-5) {
        same = false;
        break;
      }
    }
  }
  let out: number[];
  if (same) out = radiiOf(w.slice(0, wn), bands, count);
  else {
    const rq: number[][] = [];
    for (let q = 0; q < QN; q++) rq.push(radiiOf(w.slice(q * wn, (q + 1) * wn), bands, count));
    out = new Array(n);
    for (let j = 0; j < n; j++) {
      const d = Math.abs(Math.atan2(Math.sin((j / n) * TAU - tapA), Math.cos((j / n) * TAU - tapA)));
      const qf = (d / Math.PI) * (QN - 1);
      const i0 = Math.min(Math.floor(qf), QN - 2);
      const u = qf - i0;
      out[j] = rq[i0][j] + (rq[i0 + 1][j] - rq[i0][j]) * u;
    }
  }
  if (dent > 1e-4) {
    for (let j = 0; j < n; j++) {
      const d = Math.atan2(Math.sin((j / n) * TAU - tapA), Math.cos((j / n) * TAU - tapA));
      out[j] -= dent * Math.exp(-(d * d) / (2 * DENT_SIGMA * DENT_SIGMA));
    }
  }
  return out;
};
