/**
 * Clock and shapes of the proofing loaf. Every value is a pure function of the timeline t
 * (seconds in [0, LOOP)); the finger scrubs the same timeline. Coordinates are local to the hero:
 * (0, 0) is the middle of the bowl's rim line, y down, pt.
 */

export const LOOP = 9;

// Beats (s).
export const PROOF_START = 1.3;
export const PROOF_END = 4.0;
export const SHAPE_END = 4.8;
export const BAKE_END = 7.0;
const READY_END = 7.8;
const RESET_END = 8.6;
/** Where a released scrub settles: raw, risen, shaped, baked. */
export const STOPS = [PROOF_START, PROOF_END, SHAPE_END, BAKE_END];

// The glass bowl: rim line at y = RIM_Y, a half-ellipse below it.
export const RIM_Y = 0;
export const BOWL_RX = 128;
export const BOWL_RY = 146;
const WALL = 6;
/** Raw dough sits this far below the rim; risen dough domes this far above it. */
const RAW_LEVEL = 66;
const RAW_DOME = 22;
const RISEN_DOME = 72;
// The boule on the stone.
export const BASE_Y = 140;
const BOULE_RX = 118;
const BOULE_TOP = 112;
const BOULE_FOOT = 18;
export const BOULE_CY = BASE_Y - BOULE_FOOT;
/** Points on each half of the dough's outline (the bottom and the dome). */
export const HALF = 22;

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

/** Ease-out that passes the target by about 6 % and settles: the dough "pops" into a boule. */
export function easeOutBack(x: number) {
  'worklet';
  const c = 1.3;
  return 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2);
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

export type Bread = {
  /** Proof rise 0…1, shape morph 0…1 (overshoots), bake 0…1, ready badge 0…1 (overshoots). */
  rise: number;
  shape: number;
  bake: number;
  ready: number;
  /** Bowl offset (pt down) and opacity; stone and oven opacity. */
  bowlY: number;
  bowlA: number;
  stone: number;
  oven: number;
  steam: number;
  /** Seconds on the stage timer. */
  timer: number;
  /** Stage for the segmented control (0 proof … 2 bake, then 3 = proof again) and the status (0…4). */
  stage: number;
  status: number;
  /** Overall progress 0…1. */
  progress: number;
  t: number;
};

/** The whole loop, or a scrubbed position on it. */
export function breadAt(t: number): Bread {
  'worklet';
  const back = easeInOut(seg(t, READY_END, RESET_END));
  const rise = easeInOut(seg(t, PROOF_START, PROOF_END)) * (1 - back);
  const shape = (t < SHAPE_END ? easeOutBack(seg(t, PROOF_END, SHAPE_END)) : 1) * (1 - back);
  const bake = easeInOut(seg(t, SHAPE_END + 0.1, BAKE_END)) * (1 - back);
  const leave = easeInOut(seg(t, PROOF_END, PROOF_END + 0.5));
  const come = easeOut(seg(t, READY_END + 0.3, RESET_END + 0.2));
  const bowlGone = leave * (1 - come);
  const oven = easeInOut(seg(t, 4.6, 5.3)) * (1 - easeInOut(seg(t, READY_END, READY_END + 0.6)));
  const steam = easeInOut(seg(t, 5.4, 6.3)) * (1 - easeInOut(seg(t, 7.3, 8.0)));
  const ready = easeOutBack(seg(t, BAKE_END, BAKE_END + 0.38)) * (1 - easeInOut(seg(t, READY_END, READY_END + 0.25)));
  // Timer: proof 1:30:00 counts down, the bake timer 0:45:00 rolls in and counts down, then the
  // proof timer rolls back in.
  let timer = 5400;
  if (t >= PROOF_START && t < PROOF_END) timer = 5400 * (1 - easeInOut(seg(t, PROOF_START, PROOF_END)));
  else if (t >= PROOF_END && t < SHAPE_END) timer = 2700 * easeInOut(seg(t, PROOF_END + 0.1, SHAPE_END));
  else if (t >= SHAPE_END && t < READY_END) timer = 2700 * (1 - easeInOut(seg(t, SHAPE_END + 0.1, BAKE_END)));
  else if (t >= READY_END) timer = 5400 * easeInOut(seg(t, READY_END, RESET_END));
  const stage = easeInOut(seg(t, PROOF_END, PROOF_END + 0.3)) + easeInOut(seg(t, SHAPE_END, SHAPE_END + 0.3)) + easeInOut(seg(t, READY_END, READY_END + 0.4));
  const status = stage + easeInOut(seg(t, BAKE_END, BAKE_END + 0.3));
  const progress = (0.5 * easeInOut(seg(t, PROOF_START, PROOF_END)) + 0.1 * seg(t, PROOF_END, SHAPE_END) + 0.4 * easeInOut(seg(t, SHAPE_END, BAKE_END))) * (1 - back);
  return {
    rise,
    shape,
    bake,
    ready,
    bowlY: 90 * bowlGone,
    bowlA: 1 - bowlGone,
    stone: Math.min(1, leave) * (1 - easeInOut(seg(t, READY_END + 0.2, RESET_END))),
    oven,
    steam,
    timer,
    stage,
    status,
    progress,
    t,
  };
}

/** The bowl's inner wall: half-width at height y (0 above the bottom). */
function innerHalf(y: number) {
  'worklet';
  const ry = BOWL_RY - WALL;
  const k = (y - RIM_Y) / ry;
  return (BOWL_RX - WALL) * Math.sqrt(Math.max(0, 1 - k * k));
}

/**
 * The dough's outline as 2·HALF points (x, y interleaved): the bottom from left to right, then
 * the dome from right to left. In the bowl it follows the glass; as a boule it is a flat-footed
 * dome. `shape` blends the two point for point.
 */
export function doughPoints(rise: number, shape: number, out: number[]) {
  'worklet';
  const level = RIM_Y + RAW_LEVEL * (1 - rise);
  const dome = RAW_DOME + (RISEN_DOME - RAW_DOME) * rise * rise;
  const xl = Math.max(innerHalf(level), 20) * 0.98;
  const ry = BOWL_RY - WALL;
  let n = 0;
  for (let k = 0; k < HALF; k += 1) {
    const u = k / (HALF - 1);
    // In the bowl: along the glass.
    const bx = -xl + 2 * xl * u;
    const bk = bx / (BOWL_RX - WALL);
    const by = RIM_Y + ry * Math.sqrt(Math.max(0, 1 - bk * bk));
    // As a boule: a flat foot with rounded ends.
    const a = Math.PI + Math.PI * u;
    const s = Math.abs(Math.sin(a));
    const px = BOULE_RX * Math.cos(a);
    const py = BOULE_CY + BOULE_FOOT * Math.pow(s, 0.3);
    out[n] = bx + (px - bx) * shape;
    out[n + 1] = by + (py - by) * shape;
    n += 2;
  }
  for (let k = 0; k < HALF; k += 1) {
    const a = (Math.PI * (k + 0.5)) / HALF;
    const s = Math.sin(a);
    const bx = xl * Math.cos(a);
    const by = level - dome * Math.pow(s, 0.75);
    const px = BOULE_RX * Math.cos(a);
    const py = BOULE_CY - BOULE_TOP * Math.pow(s, 0.85);
    out[n] = bx + (px - bx) * shape;
    out[n + 1] = by + (py - by) * shape;
    n += 2;
  }
  return out;
}

/** Top of the dough (smallest y), for gradients and the steam. */
export function doughTop(rise: number, shape: number) {
  'worklet';
  const level = RIM_Y + RAW_LEVEL * (1 - rise);
  const dome = RAW_DOME + (RISEN_DOME - RAW_DOME) * rise * rise;
  const bowl = level - dome;
  const boule = BOULE_CY - BOULE_TOP;
  return bowl + (boule - bowl) * shape;
}

export const BUBBLES = 12;

/**
 * Bubble i on the rising dome: where it sits (a point on the surface), its radius, and its pop
 * ring (radius and alpha) as it bursts. Bubbles live only during the proof.
 */
export function bubbleAt(i: number, t: number, rise: number) {
  'worklet';
  const born = PROOF_START + 0.3 + 2.1 * hash(i * 3.3);
  const life = 0.55 + 0.35 * hash(i * 7.7);
  const g = (t - born) / life;
  const a = Math.PI * (0.18 + 0.64 * hash(i * 5.1));
  const level = RIM_Y + RAW_LEVEL * (1 - rise);
  const dome = RAW_DOME + (RISEN_DOME - RAW_DOME) * rise * rise;
  const xl = Math.max(innerHalf(level), 20) * 0.98;
  const x = xl * Math.cos(a) * 0.96;
  const y = level - dome * Math.pow(Math.sin(a), 0.75) + 2.5;
  const rmax = 3.2 + 4.2 * hash(i * 9.1);
  if (g <= 0 || g >= 1 || t > PROOF_END) return { x, y, r: 0, ring: 0, ringA: 0 };
  const r = g < 0.82 ? rmax * easeOut(g / 0.82) : 0;
  const p = (g - 0.82) / 0.18;
  return { x, y, r, ring: g >= 0.82 ? rmax * (1 + 1.4 * p) : 0, ringA: g >= 0.82 ? 1 - p : 0 };
}

/** One odometer column: unit in seconds, base 10 or 6. Rests on whole digits at whole seconds. */
export function column(v: number, unit: number, base: number) {
  'worklet';
  const whole = Math.floor(v / unit);
  const within = v - whole * unit;
  const roll = unit === 1 ? within : clamp01(within - (unit - 1));
  const pos = whole + roll;
  return pos - Math.floor(pos / base) * base;
}

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
