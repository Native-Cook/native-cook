// Product Relight: one 16-second film, every value a pure function of the loop time t. It is the tour of the Light
// tool: every control on the screen is used once, in the order a person would.
//
//   0.00 –  1.00  poster: the watch on a white sweep, the Studio softbox high on the left
//   1.00 –  3.80  the softbox is dragged over the top and round to the right; the highlights and the shadow follow
//   4.10          Golden hour is tapped; 4.2 – 5.0 the paper warms and the sun drops low
//   4.40 –  7.00  the low sun is dragged across from the right to the left: the shadow swings over the floor
//   7.30          Moody blue is tapped; 7.4 – 8.2 the sweep goes slate blue, a cool key, a warm hair light
//   8.30 –  9.70  the intensity slider goes up and back
//  10.00          Compare is tapped: the divider slides in (10.0 – 10.6) and is dragged right and left (10.7 – 11.75)
//  11.90          Compare is tapped again: the divider slides away (11.9 – 12.5)
//  12.70          Studio is tapped; 12.8 – 13.6 the rig turns back, 12.9 – 14.8 the softbox is dragged home
//  15.00 – 16.00  rest = the poster

export const LOOP = 16;
export const IDLE_MS = 2000;
export const RETURN_MS = 1200;

/** How far the light may go from the photo's centre (in photo widths). */
export const LIGHT_REACH = 0.42;
/** The intensity slider's resting value (0..1) and the intensity it stands for at full. */
export const LEVEL = 0.5;
export const MAX_INTENSITY = 1.6;

export const PHASE = {
  drag1A: 1.0,
  drag1B: 3.8,
  golden: 4.1,
  goldenA: 4.2,
  goldenB: 5.0,
  drag2A: 4.4,
  drag2B: 7.0,
  moody: 7.3,
  moodyA: 7.4,
  moodyB: 8.2,
  levelUpA: 8.3,
  levelUpB: 8.9,
  levelDownA: 9.1,
  levelDownB: 9.7,
  compareOn: 10.0,
  splitInB: 10.6,
  splitRightA: 10.7,
  splitRightB: 11.2,
  splitLeftA: 11.25,
  splitLeftB: 11.75,
  compareOff: 11.9,
  splitOutB: 12.5,
  studio: 12.7,
  studioA: 12.8,
  studioB: 13.6,
  drag3A: 12.9,
  drag3B: 14.8,
} as const;

export function clamp01(x: number): number {
  "worklet";
  return Math.min(1, Math.max(0, x));
}
export function lerp(a: number, b: number, k: number): number {
  "worklet";
  return a + (b - a) * k;
}
export function easeInOutSine(x: number): number {
  "worklet";
  return 0.5 - 0.5 * Math.cos(Math.PI * clamp01(x));
}
/** Eased 0..1 across [a, b]. */
export function seg(t: number, a: number, b: number): number {
  "worklet";
  return easeInOutSine((t - a) / (b - a));
}

// ---------------------------------------------------------------- the three rigs

export const PRESET_NAMES = ["Studio", "Golden hour", "Moody blue"] as const;
export const PRESET_HINTS = [
  "A large softbox on white paper.",
  "A low, warm sun and long shadows.",
  "Slate paper, a cool key, a warm edge.",
] as const;

/**
 * Each rig: key colour (3), key power, light height (toward the viewer, photo widths), hair light colour (3), its
 * power, ambient (3), paper (3, sRGB), shadow softness.
 */
export const RIGS = [
  [1.0, 0.97, 0.93, 6.2, 0.8, 0.9, 0.94, 1.0, 0.5, 0.25, 0.25, 0.26, 0.93, 0.92, 0.9, 1.0],
  [1.0, 0.8, 0.58, 6.0, 0.34, 1.0, 0.88, 0.7, 0.6, 0.12, 0.125, 0.15, 0.93, 0.88, 0.8, 0.55],
  [0.86, 0.9, 1.0, 5.0, 0.6, 1.0, 0.78, 0.55, 0.9, 0.05, 0.055, 0.07, 0.22, 0.26, 0.31, 0.85],
] as const;
export const RIG_SIZE = 16;

/** The rig's weights at t: out = [studio, golden, moody]. */
export function weightsAt(t: number, out: number[]): void {
  "worklet";
  const a = seg(t, PHASE.goldenA, PHASE.goldenB);
  const b = seg(t, PHASE.moodyA, PHASE.moodyB);
  const c = seg(t, PHASE.studioA, PHASE.studioB);
  out[0] = 1 - a + c;
  out[1] = a * (1 - b);
  out[2] = b * (1 - c);
}

/** The rig blended by weights: out has RIG_SIZE values. */
export function rigMix(w0: number, w1: number, w2: number, out: number[]): void {
  "worklet";
  const s = Math.max(1e-4, w0 + w1 + w2);
  for (let i = 0; i < RIG_SIZE; i++) out[i] = (RIGS[0][i] * w0 + RIGS[1][i] * w1 + RIGS[2][i] * w2) / s;
}

// ---------------------------------------------------------------- the light's path (x right, y up, photo widths)

const KEYS = [
  [0.0, -0.26, 0.26],
  [1.0, -0.26, 0.26],
  [2.4, 0.02, 0.36],
  [3.8, 0.3, 0.12],
  [4.4, 0.3, 0.12],
  [5.4, 0.34, -0.1],
  [7.0, -0.34, 0.02],
  [12.9, -0.34, 0.02],
  [13.9, -0.36, 0.14],
  [14.8, -0.26, 0.26],
  [16.0, -0.26, 0.26],
] as const;

function keyAt(t: number, c: number): number {
  "worklet";
  let i = 0;
  while (i < KEYS.length - 2 && t >= KEYS[i + 1][0]) i++;
  const k0 = KEYS[Math.max(0, i - 1)];
  const k1 = KEYS[i];
  const k2 = KEYS[i + 1];
  const k3 = KEYS[Math.min(KEYS.length - 1, i + 2)];
  const x = easeInOutSine((t - k1[0]) / (k2[0] - k1[0]));
  const p0 = k0[c];
  const p1 = k1[c];
  const p2 = k2[c];
  const p3 = k3[c];
  // Catmull-Rom tangents, zeroed at a turn so nothing overshoots its key
  let m1 = (p2 - p0) / 2;
  let m2 = (p3 - p1) / 2;
  if ((p1 - p0) * (p2 - p1) <= 0) m1 = 0;
  if ((p2 - p1) * (p3 - p2) <= 0) m2 = 0;
  const x2 = x * x;
  const x3 = x2 * x;
  return (2 * x3 - 3 * x2 + 1) * p1 + (x3 - 2 * x2 + x) * m1 + (-2 * x3 + 3 * x2) * p2 + (x3 - x2) * m2;
}

/** The light at t: out = [x, y]. */
export function lightAt(t: number, out: number[]): void {
  "worklet";
  out[0] = keyAt(t, 1);
  out[1] = keyAt(t, 2);
}

function hold(t: number, a: number, b: number): number {
  "worklet";
  return clamp01((t - a + 0.2) / 0.2) * clamp01((b - t + 0.15) / 0.2);
}

/** 0..1 while the film's finger holds the softbox (the gizmo shows and grows a little). */
export function grabAt(t: number): number {
  "worklet";
  return Math.max(hold(t, PHASE.drag1A, PHASE.drag1B), hold(t, PHASE.drag2A, PHASE.drag2B), hold(t, PHASE.drag3A, PHASE.drag3B));
}

/** 0..1 while the film's finger holds the divider. */
export function splitGrabAt(t: number): number {
  "worklet";
  return hold(t, PHASE.splitRightA, PHASE.splitLeftB);
}

/** A pressed look for the Compare button: 1 for a moment as the film taps it. */
export function compareDipAt(t: number): number {
  "worklet";
  const a = clamp01(1 - Math.abs(t - PHASE.compareOn) / 0.16);
  const b = clamp01(1 - Math.abs(t - PHASE.compareOff) / 0.16);
  return Math.max(a, b);
}

/** The intensity slider (0..1) in the film: up and back. */
export function levelAt(t: number): number {
  "worklet";
  return LEVEL + 0.35 * seg(t, PHASE.levelUpA, PHASE.levelUpB) * (1 - seg(t, PHASE.levelDownA, PHASE.levelDownB));
}

/** The before | after divider (0 = off, all after): in, dragged right, left, then away. */
export function splitAt(t: number): number {
  "worklet";
  const inK = seg(t, PHASE.compareOn, PHASE.splitInB);
  const right = seg(t, PHASE.splitRightA, PHASE.splitRightB);
  const left = seg(t, PHASE.splitLeftA, PHASE.splitLeftB);
  const out = seg(t, PHASE.compareOff, PHASE.splitOutB);
  return (0.5 * inK + 0.2 * right - 0.35 * left) * (1 - out);
}
