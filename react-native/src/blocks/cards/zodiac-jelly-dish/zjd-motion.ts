// Zodiac Jelly Dish — the loop's timeline and the jelly's springs. Helpers above callers.

export const LOOP = 8;

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
export const easeOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return 1 - (1 - x) * (1 - x) * (1 - x);
};
export const easeInOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
};

/** Marks (s): a finger pulls the slab and lets go; the jelly swirls; Add is pressed. */
export const T = {
  poke: [1.1, 1.75] as const, // the pull grows…
  release: 1.75, // …and is let go: the slab jiggles
  swirl: [2.9, 4.5] as const, // the colours inside turn once, the light sweeps
  press: 4.75, // Add pressed
  release2: 4.95, // and released: the slab drops and bounces
  added: [5.0, 6.7] as const, // the button says "Added"
} as const;

/** Where the autoplay finger grabs (relative to the slab centre) and how far it pulls. */
export const POKE = { gx: 92, gy: 150, px: 26, py: 34 } as const;

export type Jelly = {
  px: number; // pull (pt), a 2D spring
  py: number;
  vx: number;
  vy: number;
  sq: number; // squash, a 1D spring
  sv: number;
  gx: number; // grab point (relative to the slab centre)
  gy: number;
  hold: number; // 1 while something holds the pull at a target
  tx: number;
  ty: number;
};

export const makeJelly = (): Jelly => {
  'worklet';
  return { px: 0, py: 0, vx: 0, vy: 0, sq: 0, sv: 0, gx: POKE.gx, gy: POKE.gy, hold: 0, tx: 0, ty: 0 };
};

/** Advance the jelly's springs by dt (sub-stepped). A held pull follows its target stiffly. */
export const stepJelly = (j: Jelly, dt: number) => {
  'worklet';
  const n = Math.max(1, Math.ceil(dt / (1 / 240)));
  const h = dt / n;
  for (let i = 0; i < n; i++) {
    const k = j.hold === 1 ? 420 : 210; // released: 2.3 Hz
    const c = j.hold === 1 ? 40 : 4.2; // released: damping ratio ≈ 0.15, a long jiggle
    const tx = j.hold === 1 ? j.tx : 0;
    const ty = j.hold === 1 ? j.ty : 0;
    j.vx += ((tx - j.px) * k - j.vx * c) * h;
    j.vy += ((ty - j.py) * k - j.vy * c) * h;
    j.px += j.vx * h;
    j.py += j.vy * h;
    j.sv += (-j.sq * 260 - j.sv * 6) * h;
    j.sq += j.sv * h;
  }
};
