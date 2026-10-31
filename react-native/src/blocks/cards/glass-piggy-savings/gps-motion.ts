// Glass Piggy Savings — timeline and springs. Helpers above callers.

export const LOOP = 7;

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
export const easeIn = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * x;
};
export const easeInOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
};
export const easeOutBack = (k: number, c1 = 1.7) => {
  'worklet';
  const x = clamp01(k);
  return 1 + (c1 + 1) * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
};
export const hash = (i: number) => {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
};

/** Marks (s): a coin pops off its chip and is tossed up past the pig (big while near the camera,
 * smaller as it flies off), turns over the top and drops edge-first into the slot, where it rests half
 * in, the pose of the pin, before it sinks with a clink; the total counts up. At the end of the loop
 * the total dips out and comes back at its starting value, so the loop closes without counting down. */
export const T = {
  pop: 0.9,
  arc: [1.0, 1.8] as const,
  seat: [1.8, 2.0] as const,
  sink: [2.32, 2.5] as const,
  land: 2.5,
  count: [2.52, 3.3] as const,
  float: [2.5, 3.6] as const,
  swap: [6.35, 6.85] as const,
} as const;

export const SAVED = 1236;
export const COIN = 20;
export const GOAL = 2000;

/** The total shown at loop time t (dollars, fractional while it counts). */
export const totalAt = (t: number) => {
  'worklet';
  if (t >= (T.swap[0] + T.swap[1]) / 2) return SAVED;
  return SAVED + COIN * easeInOut(seg(t, T.count[0], T.count[1]));
};

/** Opacity dip of the total while it swaps back to its starting value. */
export const swapDip = (t: number) => {
  'worklet';
  const k = seg(t, T.swap[0], T.swap[1]);
  return Math.sin(k * Math.PI);
};

export type Pig = {
  sq: number; // squash spring
  sv: number;
  wob: number; // wiggle (rotation) spring
  wv: number;
};

export const makePig = (): Pig => {
  'worklet';
  return { sq: 0, sv: 0, wob: 0, wv: 0 };
};

export const stepPig = (p: Pig, dt: number) => {
  'worklet';
  const n = Math.max(1, Math.ceil(dt / (1 / 240)));
  const h = dt / n;
  for (let i = 0; i < n; i++) {
    p.sv += (-p.sq * 330 - p.sv * 9) * h;
    p.sq += p.sv * h;
    p.wv += (-p.wob * 260 - p.wv * 7) * h;
    p.wob += p.wv * h;
  }
};
