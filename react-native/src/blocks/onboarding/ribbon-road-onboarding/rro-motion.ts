// Ribbon Road Onboarding — the two ribbons, their elastic field and the autoplay script.
// Helpers above callers.

export const LOOP = 6.4;
export const RIBBON = 46; // ribbon width, pt
export const N = 170; // samples along each ribbon
export const FONT = 21;
export const PATTERN = 'NEW EVENTS FOR YOU  ·  ';
/** Text speed along the ribbons, pt/s (the white ribbon runs the other way). */
export const SPEED = 64;

/** Autoplay marks (s). */
export const S = {
  draw: [0, 1.1] as const, // the ribbons unroll from the left
  press: [1.75, 2.5] as const, // a finger pushes the blue ribbon and lets go
  go: 3.8, // Get started is pressed
} as const;
/** After Get started: the text races, the tail chases the head off the screen. */
export const Z = { boost: [0.1, 0.7] as const, tail: [0.25, 1.45] as const, again: 2.2 } as const;

// Centre lines in screen fractions (x of width, y of height), smoothed by Catmull-Rom.
export const BLUE: [number, number][] = [
  [-0.2, 0.5],
  [0.04, 0.49],
  [0.17, 0.44],
  [0.25, 0.33],
  [0.37, 0.29],
  [0.47, 0.38],
  [0.5, 0.54],
  [0.6, 0.66],
  [0.74, 0.62],
  [0.8, 0.44],
  [0.86, 0.26],
  [0.95, 0.14],
  [1.15, 0.1],
];
export const WHITE: [number, number][] = [
  [-0.2, 0.62],
  [0.06, 0.61],
  [0.15, 0.55],
  [0.2, 0.42],
  [0.31, 0.4],
  [0.41, 0.5],
  [0.45, 0.64],
  [0.57, 0.73],
  [0.72, 0.71],
  [0.83, 0.57],
  [0.9, 0.4],
  [0.98, 0.27],
  [1.15, 0.22],
];

export const clamp01 = (v: number) => {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
export const seg = (t: number, a: number, b: number) => {
  'worklet';
  return clamp01((t - a) / (b - a));
};
export const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
export const smooth = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * (3 - 2 * x);
};
export const easeInOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
};
export const easeIn = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * x;
};

/** Samples a Catmull-Rom spline through the points, then resamples it to n points evenly spaced. */
export const centreLine = (pts: [number, number][], w: number, h: number, n: number) => {
  const P = pts.map(([x, y]) => [x * w, y * h]);
  const dense: number[][] = [];
  for (let i = 0; i < P.length - 1; i++) {
    const p0 = P[Math.max(0, i - 1)];
    const p1 = P[i];
    const p2 = P[i + 1];
    const p3 = P[Math.min(P.length - 1, i + 2)];
    for (let k = 0; k < 24; k++) {
      const t = k / 24;
      const t2 = t * t;
      const t3 = t2 * t;
      const f = (a: number, b: number, c: number, d: number) =>
        0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3);
      dense.push([f(p0[0], p1[0], p2[0], p3[0]), f(p0[1], p1[1], p2[1], p3[1])]);
    }
  }
  dense.push(P[P.length - 1]);
  const acc = [0];
  for (let i = 1; i < dense.length; i++) acc.push(acc[i - 1] + Math.hypot(dense[i][0] - dense[i - 1][0], dense[i][1] - dense[i - 1][1]));
  const total = acc[acc.length - 1];
  const xs: number[] = [];
  const ys: number[] = [];
  let j = 0;
  for (let i = 0; i < n; i++) {
    const s = (total * i) / (n - 1);
    while (j < acc.length - 2 && acc[j + 1] < s) j++;
    const k = (s - acc[j]) / Math.max(1e-6, acc[j + 1] - acc[j]);
    xs.push(dense[j][0] + (dense[j + 1][0] - dense[j][0]) * k);
    ys.push(dense[j][1] + (dense[j + 1][1] - dense[j][1]) * k);
  }
  return { xs, ys };
};

export type Field = { ox: number[]; oy: number[]; vx: number[]; vy: number[] };
export const makeField = (): Field => {
  'worklet';
  const z = () => {
    const a: number[] = [];
    for (let i = 0; i < N * 2; i++) a.push(0);
    return a;
  };
  return { ox: z(), oy: z(), vx: z(), vy: z() };
};

/**
 * One step of the elastic field. A grabbed ribbon is plucked: its samples near the grabbed one follow
 * the finger's pull with a Gaussian falloff along the ribbon (16 samples), the pull rubber-banded to
 * 110 pt; everything else springs back to rest on an underdamped spring, so a ribbon let go wobbles.
 * Samples 0..N-1 belong to the blue ribbon, N..2N-1 to the white one.
 */
export const stepField = (f: Field, grab: number, gi: number, dx: number, dy: number, hold: number, dt: number) => {
  'worklet';
  const len = Math.hypot(dx, dy);
  const sc = len > 0.001 ? (110 * Math.tanh(len / 110)) / len : 0;
  const px = dx * sc * hold;
  const py = dy * sc * hold;
  const n = Math.max(1, Math.ceil(dt / (1 / 120)));
  const h = dt / n;
  for (let s = 0; s < n; s++) {
    for (let i = 0; i < N * 2; i++) {
      const r = i < N ? 0 : 1;
      let tx = 0;
      let ty = 0;
      let k = 260;
      let z = 0.28;
      if (r === grab && hold > 0.001) {
        const u = (i - r * N - gi) / 16;
        const g = Math.exp(-u * u);
        tx = px * g;
        ty = py * g;
        k = 900;
        z = 0.9;
      }
      const c = 2 * Math.sqrt(k) * z;
      f.vx[i] += (k * (tx - f.ox[i]) - c * f.vx[i]) * h;
      f.vy[i] += (k * (ty - f.oy[i]) - c * f.vy[i]) * h;
      f.ox[i] += f.vx[i] * h;
      f.oy[i] += f.vy[i] * h;
    }
  }
};
