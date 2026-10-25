/**
 * Clock and plant geometry. Every value is a pure function of the loop time t (or of the growth G
 * the finger's watering has given the plant), so the frame at t = LOOP equals the frame at 0.
 * Coordinates are local to the pot: (0, 0) is the middle of the soil, y down, pt.
 */

export const LOOP = 10;

/** Stem height at full growth, and the share of growth G that is the sprout (the rest is bloom). */
export const STEM = 205;
const SPROUT = 0.55;
/** The can's resting place and where it pours from (its body's centre), and its pouring tilt. */
export const PARK = { x: 120, y: -266 };
export const POUR = { x: 112, y: -196 };
export const TILT = -0.55;
/** The spout's rose, in the can's own coordinates. */
export const TIP = { x: -97, y: -24 };

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOutBack(x: number) {
  'worklet';
  const c = 1.4;
  return 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2);
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

export type Scene = {
  /** Growth 0 (seed) … 1 (full bloom). */
  g: number;
  /** Can position, tilt, and how hard it pours 0…1. */
  cx: number;
  cy: number;
  tilt: number;
  pour: number;
  /** Wet soil 0…1, petal fall 0…1 (each petal staggers inside it). */
  wet: number;
  fall: number;
  /** Page 0 … 2 for the copy and the dots. */
  page: number;
  t: number;
};

/** Page from growth: seed, sprout, bloom. */
export function pageOf(g: number) {
  'worklet';
  return easeInOut(seg(g, 0.14, 0.32)) + easeInOut(seg(g, 0.64, 0.82));
}

/** The whole loop. */
export function sceneAt(t: number): Scene {
  'worklet';
  const go = easeInOut(seg(t, 1.4, 2.0));
  const tip = easeInOut(seg(t, 1.8, 2.1)) * (1 - easeInOut(seg(t, 2.8, 3.05)));
  const home = easeInOut(seg(t, 3.0, 3.55));
  const k = go * (1 - home);
  const g =
    SPROUT * easeInOut(seg(t, 2.6, 4.6)) + (1 - SPROUT) * easeInOut(seg(t, 5.0, 6.8)) - easeInOut(seg(t, 8.2, 9.6));
  return {
    g: clamp01(g),
    cx: PARK.x + (POUR.x - PARK.x) * k,
    cy: PARK.y + (POUR.y - PARK.y) * k,
    tilt: TILT * tip,
    pour: clamp01((tip - 0.6) / 0.4),
    wet: easeOut(seg(t, 2.05, 2.7)) * (1 - easeInOut(seg(t, 4.2, 6.4))),
    fall: seg(t, 8.0, 9.4),
    page: pageOf(clamp01(g)),
    t,
  };
}

/** The spout's rose in pot coordinates for a can at (cx, cy) tilted by `tilt`. */
export function spout(cx: number, cy: number, tilt: number) {
  'worklet';
  const c = Math.cos(tilt);
  const s = Math.sin(tilt);
  return { x: cx + TIP.x * c - TIP.y * s, y: cy + TIP.x * s + TIP.y * c };
}

export const DROPS = 64;
const FALL = 0.68;
const GRAVITY = 900;

/**
 * Drop i of the shower at time `wt` (a free-running clock): released from the rose `life` seconds
 * ago with a seeded spread, it falls until it reaches the soil. Returns null when not in the air.
 */
export function dropAt(i: number, wt: number, x0: number, y0: number, tilt: number) {
  'worklet';
  const life = (((wt + hash(i * 1.7) * FALL) % FALL) + FALL) % FALL;
  // Out along the spout (pointing left and a little up in the can's frame), with a seeded spread.
  const c = Math.cos(tilt);
  const s = Math.sin(tilt);
  const dir = Math.atan2(-0.96 * s - 0.27 * c, -0.96 * c + 0.27 * s) + (hash(i * 5.9) - 0.5) * 0.8;
  const speed = 60 + 60 * hash(i * 3.1);
  const vx = Math.cos(dir) * speed;
  const vy = Math.sin(dir) * speed;
  const x = x0 + vx * life;
  const y = y0 + vy * life + 0.5 * GRAVITY * life * life;
  if (y > -2 + 6 * hash(i * 7.3)) return null;
  return { x, y, r: 1.5 + 1.6 * hash(i * 2.2), life };
}

/** The stem, as a cubic from the soil up to STEM; `sway` bends the top sideways (pt). */
export function stemPoint(u: number, sway: number) {
  'worklet';
  const p0x = 0;
  const p0y = 0;
  const p1x = -20;
  const p1y = -STEM * 0.36;
  const p2x = 24 + sway * 0.4;
  const p2y = -STEM * 0.7;
  const p3x = sway;
  const p3y = -STEM;
  const a = (1 - u) * (1 - u) * (1 - u);
  const b = 3 * (1 - u) * (1 - u) * u;
  const c = 3 * (1 - u) * u * u;
  const d = u * u * u;
  const x = a * p0x + b * p1x + c * p2x + d * p3x;
  const y = a * p0y + b * p1y + c * p2y + d * p3y;
  // Tangent.
  const tx = 3 * (1 - u) * (1 - u) * (p1x - p0x) + 6 * (1 - u) * u * (p2x - p1x) + 3 * u * u * (p3x - p2x);
  const ty = 3 * (1 - u) * (1 - u) * (p1y - p0y) + 6 * (1 - u) * u * (p2y - p1y) + 3 * u * u * (p3y - p2y);
  return { x, y, angle: Math.atan2(ty, tx) };
}

/** How far up the stem has grown (0…1) and how open the bloom is (0…1), from growth g. */
export function plantOf(g: number) {
  'worklet';
  return { stem: clamp01(g / SPROUT), bloom: clamp01((g - SPROUT - 0.04) / (1 - SPROUT - 0.04)) };
}

export const LEAVES = [
  { u: 0.24, side: -1, len: 50 },
  { u: 0.38, side: 1, len: 56 },
  { u: 0.55, side: -1, len: 48 },
  { u: 0.7, side: 1, len: 40 },
];

/** Leaf k: 0 folded … 1 open, with a springy overshoot as it unfurls. */
export function leafOpen(stem: number, k: number) {
  'worklet';
  const g = clamp01((stem - LEAVES[k].u - 0.04) / 0.26);
  return { len: g <= 0 ? 0 : easeOutBack(g), wide: easeOut(clamp01((g - 0.2) / 0.8)) };
}

export const PETALS = 7;

/** Mixes two '#rrggbb' colours. */
export function mixHex(a: string, b: string, t: number) {
  'worklet';
  const pa = parseInt(a.slice(1), 16);
  const pb = parseInt(b.slice(1), 16);
  const r = Math.round(((pa >> 16) & 255) * (1 - t) + ((pb >> 16) & 255) * t);
  const g = Math.round(((pa >> 8) & 255) * (1 - t) + ((pb >> 8) & 255) * t);
  const bl = Math.round((pa & 255) * (1 - t) + (pb & 255) * t);
  return `rgb(${r},${g},${bl})`;
}
