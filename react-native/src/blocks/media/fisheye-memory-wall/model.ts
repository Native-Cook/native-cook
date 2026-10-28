/*
 The wall, its memories, the lens and the timeline of Fisheye Memory Wall (a port of
 FisheyeMemoryWallModel.swift). Every frame is a pure function of time (autoPlay) or of the touch state.
 Everything the UI thread calls is a worklet, declared above its callers.
*/

/** One period of the autoPlay loop, in seconds. */
export const LOOP = 9;

/** One cell of the wall grid, the gutter between tiles, and the repeating period (7 × 10 cells). */
export const CELL = 44;
export const GUTTER = 5;
export const PERIOD_W = CELL * 7;
export const PERIOD_H = CELL * 10;
/** Atlas pixels per point. */
export const ATLAS_SCALE = 4;

/** The lens centre rests in the middle of the cake tile (index 11). */
export const REST_X = 132;
export const REST_Y = 132;

export type Tile = {
  x: number;
  y: number;
  width: number;
  height: number;
  radius: number;
  design: number;
  tint: number;
  flip: boolean;
  month: number;
  day: number;
};

/** col, row, w, h, design, tint, flip, month (0 = Jan), day — packed once from a seeded greedy fill. */
const LAYOUT: number[][] = [
        [0, 0, 1, 1, 0, 1, 1, 6, 15], [1, 0, 2, 1, 12, 3, 1, 6, 25], [3, 0, 1, 1, 11, 0, 0, 7, 12],
        [4, 0, 1, 1, 10, 2, 1, 2, 21], [5, 0, 2, 2, 6, 0, 1, 0, 17], [0, 1, 1, 1, 13, 2, 1, 1, 13],
        [1, 1, 1, 1, 1, 1, 1, 4, 18], [2, 1, 1, 1, 2, 0, 0, 11, 13], [3, 1, 2, 1, 3, 1, 0, 7, 17],
        [0, 2, 1, 1, 9, 1, 0, 1, 21], [1, 2, 1, 1, 7, 2, 0, 4, 16], [2, 2, 2, 2, 4, 2, 1, 4, 11],
        [4, 2, 1, 1, 5, 0, 0, 11, 22], [5, 2, 2, 2, 1, 2, 0, 11, 24], [0, 3, 2, 2, 0, 0, 1, 5, 21],
        [4, 3, 1, 1, 9, 0, 0, 9, 26], [2, 4, 1, 1, 3, 1, 1, 4, 16], [3, 4, 1, 1, 10, 0, 1, 1, 14],
        [4, 4, 1, 1, 11, 2, 1, 11, 26], [5, 4, 2, 2, 7, 0, 0, 10, 14], [0, 5, 2, 1, 5, 3, 1, 4, 8],
        [2, 5, 1, 1, 13, 3, 1, 9, 18], [3, 5, 2, 2, 8, 2, 1, 6, 7], [0, 6, 1, 1, 6, 0, 1, 0, 19],
        [1, 6, 1, 1, 4, 1, 1, 11, 27], [2, 6, 1, 2, 2, 0, 0, 7, 13], [5, 6, 2, 2, 12, 1, 0, 10, 5],
        [0, 7, 1, 1, 11, 2, 1, 6, 13], [1, 7, 1, 2, 5, 0, 1, 1, 18], [3, 7, 1, 1, 10, 1, 0, 5, 19],
        [4, 7, 1, 1, 6, 0, 1, 5, 19], [0, 8, 1, 2, 3, 1, 0, 2, 11], [2, 8, 2, 2, 13, 1, 0, 1, 5],
        [4, 8, 1, 1, 8, 3, 1, 11, 18], [5, 8, 2, 2, 2, 2, 1, 2, 18], [1, 9, 1, 1, 9, 1, 1, 11, 18],
        [4, 9, 1, 1, 12, 1, 0, 3, 15],
    ];

export const TILES: Tile[] = LAYOUT.map((t) => ({
  x: t[0] * CELL + GUTTER / 2,
  y: t[1] * CELL + GUTTER / 2,
  width: t[2] * CELL - GUTTER,
  height: t[3] * CELL - GUTTER,
  radius: 5 + 2 * Math.min(t[2], t[3]),
  design: t[4],
  tint: t[5],
  flip: t[6] === 1,
  month: t[7],
  day: t[8],
}));

export const MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];

/** Title and place of each design. */
export const CAPTIONS: string[][] = [
  ['Sunday shakshuka', 'Home, Alfama'],
  ['Last light', 'Pelican Bay'],
  ['Ridge walk, 6.2 km', 'Sella Pass'],
  ['Flat white, window seat', 'Café Lune'],
  ['Mila turns seven', 'Our kitchen'],
  ['Night tram home', 'Old Town'],
  ['First tulips', 'Market Street'],
  ['Beach day, finally', 'Praia do Sol'],
  ['Golden hour', 'Rooftop'],
  ['Blue hour', 'Harbour walk'],
  ['A day worth marking', 'Journal'],
  ['Up at dawn', 'Valley of Mist'],
  ['Lemon tart, first try', 'Home'],
  ['Camp under the stars', 'Lake Vire'],
];

/** Per-instance tints laid over a tile at 12 % (0 = none). */
export const TINTS = [0x000000, 0xff9a5a, 0x5aa0ff, 0xff6f91];

// Artwork
//
// Each design is a list of shape ops in a unit square (x right, y down), cover-fitted to the tile:
// 0 gradient c1 c2 c3 (whole tile) · 1 circle x y r color alpha · 2 ellipse x y rx ry color alpha ·
// 3 rect x y w h radius color alpha · 4 polygon color alpha x1 y1 x2 y2 … ·
// 5 line x1 y1 x2 y2 width color alpha (round caps) · 6 blob x y r color alpha (radial fade) ·
// 7 the tile's date (month over day).

export const DESIGNS: number[][][] = [
        // 0 shakshuka in a pan, on a wooden table
        [[0, 0xc9b79c, 0xb9a385, 0xa48c6e], [5, 0.84, 0.66, 1.05, 0.86, 0.09, 0x1e2228, 1],
         [1, 0.5, 0.53, 0.41, 0x14171c, 0.45], [1, 0.5, 0.5, 0.4, 0x2b3038, 1], [1, 0.5, 0.5, 0.34, 0xc8442c, 1],
         [6, 0.42, 0.4, 0.3, 0xe8683e, 0.6],
         [1, 0.37, 0.42, 0.095, 0xfff6e8, 1], [1, 0.37, 0.42, 0.042, 0xf2b530, 1],
         [1, 0.61, 0.58, 0.095, 0xfff6e8, 1], [1, 0.61, 0.58, 0.042, 0xf2b530, 1],
         [1, 0.59, 0.34, 0.028, 0x3f8a3a, 1], [1, 0.41, 0.65, 0.024, 0x3f8a3a, 1], [1, 0.68, 0.43, 0.022, 0x4fa048, 1],
         [1, 0.3, 0.58, 0.02, 0x4fa048, 1]],
        // 1 sunset over the sea
        [[0, 0x2b3a67, 0xe8735a, 0xf6b26b], [6, 0.5, 0.6, 0.45, 0xffc27a, 0.7], [1, 0.5, 0.6, 0.15, 0xffe3a8, 1],
         [3, 0, 0.62, 1, 0.4, 0, 0x1f3e5c, 1], [3, 0, 0.62, 1, 0.05, 0, 0x2c5476, 1],
         [3, 0.24, 0.66, 0.52, 0.022, 0.011, 0xffd08a, 0.75], [3, 0.33, 0.72, 0.34, 0.018, 0.009, 0xffd08a, 0.55],
         [3, 0.42, 0.78, 0.16, 0.014, 0.007, 0xffd08a, 0.4]],
        // 2 mountain ridge
        [[0, 0x8db8e2, 0xc6ddf0, 0xeaf2f6], [1, 0.78, 0.2, 0.07, 0xfff7e0, 1],
         [4, 0x4b5d78, 1, 0, 0.8, 0.34, 0.3, 0.55, 0.58, 0.72, 0.4, 1, 0.76, 1, 1, 0, 1],
         [4, 0xf4f7fb, 1, 0.34, 0.3, 0.25, 0.43, 0.31, 0.41, 0.36, 0.46, 0.42, 0.4],
         [4, 0xf4f7fb, 1, 0.72, 0.4, 0.66, 0.48, 0.71, 0.47, 0.75, 0.5, 0.79, 0.47],
         [4, 0x2f4a3f, 1, 0, 0.86, 0.3, 0.68, 0.6, 0.84, 1, 0.7, 1, 1, 0, 1]],
        // 3 a flat white on a saucer
        [[0, 0xe9dccb, 0xdccab4, 0xcdb79c], [2, 0.52, 0.64, 0.36, 0.14, 0xa8927a, 0.45],
         [2, 0.5, 0.6, 0.35, 0.14, 0xf7f3ee, 1], [5, 0.74, 0.5, 0.86, 0.5, 0.07, 0xfbf8f4, 1],
         [1, 0.5, 0.5, 0.26, 0xfbf8f4, 1], [1, 0.5, 0.5, 0.21, 0x8a5a3b, 1], [1, 0.5, 0.5, 0.18, 0xb07b52, 1],
         [1, 0.455, 0.47, 0.06, 0xf3e6d6, 1], [1, 0.545, 0.47, 0.06, 0xf3e6d6, 1],
         [4, 0xf3e6d6, 1, 0.398, 0.49, 0.602, 0.49, 0.5, 0.6]],
        // 4 a birthday cake with one candle
        [[0, 0xf7c9d2, 0xf2b3c2, 0xe89bb0], [6, 0.5, 0.28, 0.3, 0xfff1c2, 0.8], [2, 0.5, 0.84, 0.42, 0.06, 0xffffff, 1],
         [3, 0.2, 0.5, 0.6, 0.33, 0.05, 0xfff4ec, 1], [3, 0.2, 0.5, 0.6, 0.09, 0.05, 0xe05a7a, 1],
         [1, 0.3, 0.585, 0.032, 0xe05a7a, 1], [1, 0.47, 0.6, 0.038, 0xe05a7a, 1], [1, 0.66, 0.585, 0.03, 0xe05a7a, 1],
         [3, 0.2, 0.7, 0.6, 0.03, 0, 0xf6b8c6, 1], [5, 0.5, 0.34, 0.5, 0.5, 0.036, 0x7fc6e8, 1],
         [2, 0.5, 0.28, 0.032, 0.055, 0xffc74a, 1], [2, 0.5, 0.29, 0.014, 0.025, 0xfff4d0, 1]],
        // 5 a skyline at night
        [[0, 0x0e1430, 0x1e2a5a, 0x3b3f7a], [6, 0.75, 0.22, 0.2, 0xf4f1de, 0.35], [1, 0.75, 0.22, 0.06, 0xf4f1de, 1],
         [3, 0, 0.55, 0.18, 0.45, 0, 0x121733, 1], [3, 0.16, 0.42, 0.16, 0.58, 0, 0x171c3d, 1],
         [3, 0.3, 0.6, 0.14, 0.4, 0, 0x10142c, 1], [3, 0.42, 0.35, 0.18, 0.65, 0, 0x1a2046, 1],
         [3, 0.58, 0.52, 0.15, 0.48, 0, 0x121733, 1], [3, 0.71, 0.45, 0.14, 0.55, 0, 0x161b3a, 1],
         [3, 0.83, 0.62, 0.17, 0.38, 0, 0x10142c, 1],
         [3, 0.2, 0.48, 0.03, 0.03, 0, 0xffd27a, 1], [3, 0.26, 0.56, 0.03, 0.03, 0, 0xffd27a, 0.8],
         [3, 0.47, 0.42, 0.03, 0.03, 0, 0xffd27a, 1], [3, 0.53, 0.5, 0.03, 0.03, 0, 0xffd27a, 0.7],
         [3, 0.47, 0.62, 0.03, 0.03, 0, 0xffd27a, 0.9], [3, 0.62, 0.6, 0.03, 0.03, 0, 0xffd27a, 0.8],
         [3, 0.75, 0.53, 0.03, 0.03, 0, 0xffd27a, 1], [3, 0.88, 0.7, 0.03, 0.03, 0, 0xffd27a, 0.8],
         [3, 0.05, 0.64, 0.03, 0.03, 0, 0xffd27a, 0.7]],
        // 6 a tulip
        [[0, 0xf3efd8, 0xe7e9c9, 0xd6e2b8], [6, 0.5, 0.42, 0.3, 0xffffff, 0.6], [5, 0.5, 0.5, 0.5, 1.05, 0.04, 0x4e8b4a, 1],
         [4, 0x5e9e55, 1, 0.5, 0.88, 0.28, 0.62, 0.36, 0.82], [4, 0x5e9e55, 1, 0.5, 0.82, 0.74, 0.6, 0.66, 0.8],
         [1, 0.5, 0.43, 0.14, 0xe8506a, 1],
         [4, 0xe8506a, 1, 0.36, 0.44, 0.36, 0.25, 0.45, 0.35, 0.5, 0.23, 0.55, 0.35, 0.64, 0.25, 0.64, 0.44],
         [2, 0.45, 0.4, 0.03, 0.08, 0xff8da0, 0.7]],
        // 7 a beach umbrella
        [[0, 0x6ec0e8, 0xa9ddf0, 0xbfe6f2], [3, 0, 0.62, 1, 0.12, 0, 0x2e8fb8, 1], [3, 0, 0.72, 1, 0.28, 0, 0xf2d9a6, 1],
         [5, 0.5, 0.36, 0.56, 0.86, 0.025, 0xffffff, 1],
         [4, 0xf25c54, 1, 0.16, 0.42, 0.24, 0.31, 0.36, 0.24, 0.5, 0.2, 0.64, 0.24, 0.76, 0.31, 0.84, 0.42],
         [4, 0xfff4e6, 1, 0.42, 0.42, 0.44, 0.25, 0.5, 0.2, 0.56, 0.25, 0.58, 0.42],
         [3, 0.2, 0.82, 0.28, 0.08, 0.015, 0xf7b538, 1], [3, 0.2, 0.845, 0.28, 0.02, 0, 0xffffff, 0.8]],
        // 8 golden-hour gradient
        [[0, 0xff8a5b, 0xffb86e, 0xffe3a3], [6, 0.28, 0.72, 0.45, 0xff5e7e, 0.55], [6, 0.78, 0.28, 0.4, 0xfff1c2, 0.7]],
        // 9 blue-hour gradient
        [[0, 0x1f3b73, 0x4c6fb8, 0x9cb7e8], [6, 0.72, 0.76, 0.5, 0xb78cf2, 0.65], [6, 0.24, 0.24, 0.42, 0x7fe0e0, 0.5]],
        // 10 a date card
        [[0, 0xf4efe6, 0xf4efe6, 0xeae2d3], [7]],
        // 11 a hot-air balloon at dawn
        [[0, 0xf6bf94, 0xf6d8c0, 0xe9e3e6], [6, 0.5, 0.95, 0.5, 0xffe9b0, 0.6],
         [4, 0xc9a79e, 1, 0, 0.86, 0.35, 0.78, 0.7, 0.88, 1, 0.8, 1, 1, 0, 1],
         [1, 0.2, 0.62, 0.06, 0x4c8fd6, 1], [5, 0.2, 0.68, 0.2, 0.71, 0.01, 0x5a4636, 1],
         [5, 0.42, 0.5, 0.465, 0.66, 0.014, 0x5a4636, 1], [5, 0.58, 0.5, 0.535, 0.66, 0.014, 0x5a4636, 1],
         [1, 0.5, 0.35, 0.2, 0xe2574c, 1], [4, 0xe2574c, 1, 0.33, 0.45, 0.5, 0.56, 0.67, 0.45],
         [2, 0.5, 0.36, 0.075, 0.2, 0xf6c34a, 1], [3, 0.45, 0.65, 0.1, 0.07, 0.015, 0x7a5a3e, 1]],
        // 12 a lemon tart
        [[0, 0xbfd8d2, 0xa9cbc4, 0x93bcb4], [1, 0.52, 0.55, 0.37, 0x6f958d, 0.4], [1, 0.5, 0.52, 0.36, 0xd9a15b, 1],
         [1, 0.5, 0.52, 0.31, 0xf7d44c, 1], [2, 0.42, 0.42, 0.12, 0.06, 0xfff0a0, 0.7],
         [1, 0.63, 0.6, 0.085, 0xffffff, 1], [1, 0.63, 0.6, 0.068, 0xf2d23a, 1],
         [5, 0.63, 0.54, 0.63, 0.66, 0.006, 0xfff6c8, 1], [5, 0.57, 0.6, 0.69, 0.6, 0.006, 0xfff6c8, 1],
         [2, 0.39, 0.61, 0.04, 0.022, 0x5faf6a, 1], [2, 0.44, 0.63, 0.035, 0.02, 0x4e9a5a, 1]],
        // 13 a tent under the stars
        [[0, 0x0b1026, 0x1c2550, 0x2e2f5e], [1, 0.2, 0.18, 0.012, 0xffffff, 1], [1, 0.42, 0.1, 0.009, 0xffffff, 0.8],
         [1, 0.66, 0.22, 0.012, 0xffffff, 1], [1, 0.84, 0.12, 0.009, 0xffffff, 0.8], [1, 0.32, 0.32, 0.008, 0xffffff, 0.7],
         [1, 0.56, 0.36, 0.01, 0xffffff, 0.9], [1, 0.9, 0.4, 0.008, 0xffffff, 0.7], [1, 0.1, 0.42, 0.008, 0xffffff, 0.7],
         [4, 0x151b3a, 1, 0, 0.72, 0.25, 0.55, 0.5, 0.7, 0.78, 0.5, 1, 0.68, 1, 1, 0, 1],
         [3, 0, 0.84, 1, 0.16, 0, 0x0d1430, 1], [6, 0.5, 0.78, 0.3, 0xf29e4c, 0.45],
         [4, 0xf29e4c, 1, 0.3, 0.86, 0.5, 0.52, 0.7, 0.86], [4, 0xffe1a8, 1, 0.45, 0.86, 0.5, 0.66, 0.55, 0.86]],
    ];

// Lens and timeline

/** Inverse zoom at the centre: the bulge magnifies the middle by 1 + 0.6 k. */
export function centre(k: number) {
  'worklet';
  return 1 / (1 + 0.6 * k);
}

export function clamp(x: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max(x, a), b);
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function wrap(v: number, p: number) {
  'worklet';
  const r = v % p;
  return r < 0 ? r + p : r;
}

/** Rounds half away from zero, like Swift's rounded(). */
function roundAway(v: number) {
  'worklet';
  return v >= 0 ? Math.floor(v + 0.5) : -Math.floor(-v + 0.5);
}

/** v moved by whole periods to lie within half a period of 0. */
export function nearest(v: number, p: number) {
  'worklet';
  return v - roundAway(v / p) * p;
}

/**
 * Travel 0 → 1 over u 0 → 1 like a dolly: a soft start (the first 20 %), a steady cruise, and a soft
 * stop (the last 24 %); the speed eases in and out on half a cosine.
 */
export function cruise(u: number) {
  'worklet';
  const a = 0.2;
  const b = 0.24;
  const v = 1 / (1 - a / 2 - b / 2);
  if (u <= 0) return 0;
  if (u >= 1) return 1;
  if (u < a) return v * (u / 2 - (a / (2 * Math.PI)) * Math.sin((Math.PI * u) / a));
  if (u > 1 - b) {
    const r = 1 - u;
    return 1 - v * (r / 2 - (b / (2 * Math.PI)) * Math.sin((Math.PI * r) / b));
  }
  return v * (a / 2 + (u - a));
}

/** Pan offset from rest (wall points), bulge strength and the focus ring's opacity. */
export type Pose = { x: number; y: number; k: number; ring: number };

export function makePose(x: number, y: number, k: number, ring: number): Pose {
  'worklet';
  return { x, y, k, ring };
}

/** The autoPlay pose at loop time t: the pan offset from rest and the bulge strength. */
export function autoPose(t: number): Pose {
  'worklet';
  // Back at rest the wall is one period on, which is rest itself: use 0 so the frames match exactly.
  const travel = cruise(seg(t, 1.0, 7.6));
  const e = travel >= 1 ? 0 : travel;
  // The bulge swells while the wall travels, relaxes a little past rest as it stops, and settles.
  const k =
    1 +
    0.42 * (easeInOut(seg(t, 1.0, 2.8)) - easeInOut(seg(t, 5.6, 7.6))) -
    0.12 * Math.sin(Math.PI * easeInOut(seg(t, 7.0, 8.6)));
  // The focus ring around the memory under the centre fades while the wall travels.
  const ring = 1 - easeInOut(seg(t, 1.0, 1.4)) + easeInOut(seg(t, 7.8, 8.4));
  // One whole period along x (the wall repeats), with an S-shaped bob in y.
  return makePose(PERIOD_W * e, 90 * Math.sin(2 * Math.PI * e), k, ring);
}

export type Memory = { tile: number; depth: number };

/** The memory under a wall point, and how deep (pt) the point is inside it (negative in a gutter). */
export function memoryAt(x: number, y: number): Memory {
  'worklet';
  const px = wrap(x, PERIOD_W);
  const py = wrap(y, PERIOD_H);
  let best = 0;
  let bestDepth = -Infinity;
  for (let i = 0; i < TILES.length; i++) {
    const t = TILES[i];
    const depth = Math.min(px - t.x, t.x + t.width - px, py - t.y, t.y + t.height - py);
    if (depth > bestDepth) {
      bestDepth = depth;
      best = i;
    }
  }
  return { tile: best, depth: bestDepth };
}

// Touch, momentum and the return to rest.

const DECAY = 4;
const IDLE = 2;
const BACK = 1.2;
const NONE = -1;

/** 0 autoPlay, 1 touch, 2 coasting and returning. */
export type Driver = {
  phase: number;
  start: number;
  lastFrame: number;
  pose: Pose;
  dragFrom: Pose;
  pinchFrom: number;
  dragging: boolean;
  pinching: boolean;
  releasePose: Pose;
  vx: number;
  vy: number;
  releaseAt: number;
  lastMemory: number;
};

export function makeDriver(): Driver {
  'worklet';
  return {
    phase: 0,
    start: NONE,
    lastFrame: NONE,
    pose: makePose(0, 0, 1, 1),
    dragFrom: makePose(0, 0, 1, 1),
    pinchFrom: 1,
    dragging: false,
    pinching: false,
    releasePose: makePose(0, 0, 1, 1),
    vx: 0,
    vy: 0,
    releaseAt: 0,
    lastMemory: -1,
  };
}

/** The pose dt seconds after release: the throw decays exponentially. */
function coast(d: Driver, dt: number): Pose {
  'worklet';
  const s = (1 - Math.exp(-DECAY * dt)) / DECAY;
  return makePose(d.releasePose.x + d.vx * s, d.releasePose.y + d.vy * s, d.releasePose.k, 0);
}

export function driverPose(d: Driver, now: number, auto: boolean): Pose {
  'worklet';
  if (d.phase === 1) return d.pose;
  if (d.phase === 2) {
    const dt = now - d.releaseAt;
    if (dt < IDLE) return coast(d, dt);
    const e = easeInOut(seg(dt, IDLE, IDLE + BACK));
    if (e >= 1) {
      d.phase = 0;
      d.start = now;
      d.lastFrame = now;
      return makePose(0, 0, 1, 1);
    }
    // Ease from where the wall drifted to, moved by whole periods to the copy of rest nearest it.
    const from = coast(d, IDLE);
    const fx = nearest(from.x, PERIOD_W);
    const fy = nearest(from.y, PERIOD_H);
    return makePose(fx * (1 - e), fy * (1 - e), from.k + (1 - from.k) * e, e);
  }
  if (!auto) return makePose(0, 0, 1, 1);
  if (d.start === NONE) d.start = now;
  // A stall (the first frame, the app in the background) pauses the clock instead of skipping part
  // of the loop.
  if (d.lastFrame !== NONE && now - d.lastFrame > 0.25) d.start += now - d.lastFrame;
  d.lastFrame = now;
  return autoPose(wrap(now - d.start, LOOP));
}

/** True while something still moves without autoPlay. */
export function driverLive(d: Driver) {
  'worklet';
  return d.phase !== 0;
}

function grab(d: Driver, now: number, auto: boolean) {
  'worklet';
  if (d.phase !== 1) {
    const p = driverPose(d, now, auto);
    // Keep the pan small so float precision never drifts.
    d.pose = makePose(nearest(p.x, PERIOD_W), nearest(p.y, PERIOD_H), p.k, 0);
    d.phase = 1;
  }
}

export function driverBeginDrag(d: Driver, now: number, auto: boolean) {
  'worklet';
  grab(d, now, auto);
  d.dragFrom = d.pose;
  d.dragging = true;
}

function crossed(d: Driver) {
  'worklet';
  const m = memoryAt(REST_X + d.pose.x, REST_Y + d.pose.y);
  if (m.depth <= 2 || m.tile === d.lastMemory) return false;
  d.lastMemory = m.tile;
  return true;
}

/** Moves the wall with the finger: the tile under the centre follows it 1 : 1. True when a new memory reaches the centre. */
export function driverDrag(d: Driver, dx: number, dy: number) {
  'worklet';
  const z = centre(d.pose.k);
  d.pose = makePose(d.dragFrom.x - dx * z, d.dragFrom.y - dy * z, d.pose.k, 0);
  return crossed(d);
}

export function driverBeginPinch(d: Driver, now: number, auto: boolean) {
  'worklet';
  grab(d, now, auto);
  d.pinchFrom = d.pose.k;
  d.pinching = true;
}

export function driverPinch(d: Driver, scale: number) {
  'worklet';
  d.pose = makePose(d.pose.x, d.pose.y, clamp(d.pinchFrom * scale, 0.25, 1.8), 0);
}

function release(d: Driver, vx: number, vy: number, now: number) {
  'worklet';
  d.releasePose = d.pose;
  d.vx = clamp(vx, -2400, 2400);
  d.vy = clamp(vy, -2400, 2400);
  d.releaseAt = now;
  d.phase = 2;
}

export function driverEndPinch(d: Driver, now: number) {
  'worklet';
  d.pinching = false;
  if (!d.dragging) release(d, 0, 0, now);
}

export function driverEndDrag(d: Driver, vx: number, vy: number, now: number) {
  'worklet';
  d.dragging = false;
  if (d.pinching) return;
  const z = centre(d.pose.k);
  release(d, -vx * z, -vy * z, now);
}
