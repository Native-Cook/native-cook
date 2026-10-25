// The card's warmth and tilt: a plain object on the UI thread.
//
// Heat lives in a ring of HEAT_SLOTS points (card points, and the frame-clock time each was laid
// down). A point's strength falls from HEAT_PEAK to 0 over HEAT_LIFE seconds, so the reveal follows
// the thumb and closes behind it. In autoPlay the ring is rebuilt every frame from the loop time
// alone: a thumb sweeps an S-curve down the card (one point every EMIT_EVERY s), then the whole
// card warms and cools back. A touch keeps the same ring and goes on adding to it, so nothing jumps.

export const LOOP = 9;
export const HEAT_SLOTS = 24;
const HEAT_LIFE = 1.4;
const HEAT_PEAK = 0.42;
/** Gaussian radius of one warm spot (pt). */
export const HEAT_RADIUS = 30;
/** A resting thumb keeps laying points down this often (s), so warmth builds under it. */
const EMIT_EVERY = HEAT_LIFE / (HEAT_SLOTS - 1);
// The thumb's sweep and the whole-card warmth (s).
const SWEEP = [1.5, 5.5];
const GLOB_UP = [4.6, 5.5];
const GLOB_DOWN = [6.4, 8.2];
/** Card tilt limit (deg) and the drag that reaches it (deg per pt). */
export const TILT_MAX = 8;
export const TILT_PER_PT = 0.07;
export const IDLE_BEFORE_BACK = 2;
const BACK_FOR = 0.8;

export const MODE_AUTO = 0;
export const MODE_LIVE = 1;
export const MODE_BACK = 2;

export type Thermal = {
  mode: number;
  t: number;
  now: number;
  lastTouch: number;
  /** Ring of heat points: x, y, time laid down. */
  hx: number[];
  hy: number[];
  ht: number[];
  /** Whole-card warmth (0…1). */
  glob: number;
  /** A finger warming the card (card points), and when it last laid a point. */
  warming: boolean;
  fx: number;
  fy: number;
  lastEmit: number;
  /** Where the last point was laid. */
  ex: number;
  ey: number;
  /** Reduced motion: the card stays warm (map shown) and does not cool. */
  steady: boolean;
  /** Tilt (deg) about the vertical and horizontal axes, their velocities, and a drag's target. */
  rx: number;
  ry: number;
  vrx: number;
  vry: number;
  tiltX: number;
  tiltY: number;
  tilting: boolean;
  /** Gloss band position (across the card, 0…1). */
  gloss: number;
  /** Which coves are currently revealed (for one haptic each). */
  found: boolean[];
  buzz: boolean;
  backAt: number;
  backGlob: number;
  backGloss: number;
  /** Strength scale while easing back (1 → 0). */
  fade: number;
};

export function makeThermal(auto: boolean, reduced: boolean): Thermal {
  return {
    mode: auto ? MODE_AUTO : MODE_LIVE,
    t: 0,
    now: 0,
    lastTouch: -100,
    hx: new Array(HEAT_SLOTS).fill(0),
    hy: new Array(HEAT_SLOTS).fill(0),
    ht: new Array(HEAT_SLOTS).fill(-100),
    glob: reduced ? 1 : 0,
    warming: false,
    fx: 0,
    fy: 0,
    lastEmit: -100,
    ex: 0,
    ey: 0,
    steady: reduced,
    rx: 0,
    ry: 0,
    vrx: 0,
    vry: 0,
    tiltX: 0,
    tiltY: 0,
    tilting: false,
    gloss: 0.3,
    found: [false, false, false],
    buzz: false,
    backAt: 0,
    backGlob: 0,
    backGloss: 0.3,
    fade: 1,
  };
}

function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

/** A point's warmth at age a (s): a quick rise, then a slow fall to nothing at HEAT_LIFE. */
export function heatStrength(age: number) {
  'worklet';
  if (age < 0 || age >= HEAT_LIFE) return 0;
  const rise = Math.min(age / 0.08, 1);
  const fall = 1 - age / HEAT_LIFE;
  return HEAT_PEAK * rise * Math.pow(fall, 1.5);
}

/** The thumb's S-curve over the card at sweep progress u (0…1), as fractions of the card. */
function thumbAt(u: number): [number, number] {
  'worklet';
  return [0.5 - 0.22 * Math.cos(2 * Math.PI * u), 0.14 + 0.74 * u];
}

/** Rebuilds the ring from loop time t: the points the scripted thumb laid in the last HEAT_LIFE s. */
export function scriptFrame(s: Thermal, t: number, w: number, h: number) {
  'worklet';
  const [a, b] = SWEEP;
  const count = Math.floor((b - a) / EMIT_EVERY);
  const newest = Math.floor((t - a) / EMIT_EVERY);
  for (let j = 0; j < HEAT_SLOTS; j += 1) {
    const k = newest - (HEAT_SLOTS - 1) + j;
    s.ht[j] = -100;
    if (k < 0 || k > count) continue;
    const te = a + k * EMIT_EVERY;
    const age = t - te;
    if (age < 0 || age >= HEAT_LIFE) continue;
    // The thumb slows at the ends of its stroke (ease in-out over the sweep).
    const [ux, uy] = thumbAt(easeInOut(seg(te, a, b)));
    s.hx[j] = ux * w;
    s.hy[j] = uy * h;
    // Stored as a time on the frame clock, so a touch can take over the ring as it is.
    s.ht[j] = s.now - age;
  }
  s.glob = easeOut(seg(t, GLOB_UP[0], GLOB_UP[1])) * (1 - easeInOut(seg(t, GLOB_DOWN[0], GLOB_DOWN[1])));
  // Idle gloss and a gentle sway of whole-loop sines.
  s.gloss = 0.3 + 0.22 * Math.sin((2 * Math.PI * t) / LOOP);
  s.rx = 1.6 * Math.sin((2 * Math.PI * t) / LOOP);
  s.ry = -1.1 * Math.sin((2 * Math.PI * 2 * t) / LOOP);
  s.fade = 1;
}

function spring(x: number, v: number, x0: number, w: number, z: number, dt: number): [number, number] {
  'worklet';
  const nv = v + (-w * w * (x - x0) - 2 * z * w * v) * dt;
  return [x + nv * dt, nv];
}

/** Lays one heat point at (x, y). */
export function emit(s: Thermal, x: number, y: number) {
  'worklet';
  // Overwrite the coldest slot, so a long stroke drops what has already faded.
  let slot = 0;
  let oldest = Infinity;
  for (let j = 0; j < HEAT_SLOTS; j += 1) {
    if (s.ht[j] < oldest) {
      oldest = s.ht[j];
      slot = j;
    }
  }
  s.hx[slot] = x;
  s.hy[slot] = y;
  s.ht[slot] = s.now;
  s.lastEmit = s.now;
  s.ex = x;
  s.ey = y;
}

export function liveFrame(s: Thermal, dt: number) {
  'worklet';
  if (s.warming && s.now - s.lastEmit >= EMIT_EVERY) emit(s, s.fx, s.fy);
  if (!s.steady) s.glob *= Math.exp(-1.4 * dt);
  const tx = s.tilting ? s.tiltX : 0;
  const ty = s.tilting ? s.tiltY : 0;
  const w = s.tilting ? 22 : 11;
  const z = s.tilting ? 0.95 : 0.55;
  [s.rx, s.vrx] = spring(s.rx, s.vrx, tx, w, z, dt);
  [s.ry, s.vry] = spring(s.ry, s.vry, ty, w, z, dt);
  s.gloss = 0.3 + (s.ry / TILT_MAX) * 0.35 - (s.rx / TILT_MAX) * 0.15;
}

export function beginBack(s: Thermal) {
  'worklet';
  s.mode = MODE_BACK;
  s.backAt = s.now;
  s.backGlob = s.glob;
  s.backGloss = s.gloss;
  s.warming = false;
  s.tilting = false;
}

/** Easing back to the first frame of the loop; true when done. */
export function backFrame(s: Thermal, dt: number) {
  'worklet';
  const k = easeInOut(Math.min((s.now - s.backAt) / BACK_FOR, 1));
  s.fade = 1 - k;
  s.glob = s.backGlob * (1 - k);
  s.gloss = s.backGloss + (0.3 - s.backGloss) * k;
  [s.rx, s.vrx] = spring(s.rx, s.vrx, 0, 11, 0.9, dt);
  [s.ry, s.vry] = spring(s.ry, s.vry, 0, 11, 0.9, dt);
  if (k >= 1) {
    s.rx = 0;
    s.ry = 0;
    s.vrx = 0;
    s.vry = 0;
    s.fade = 1;
    for (let j = 0; j < HEAT_SLOTS; j += 1) s.ht[j] = -100;
    return true;
  }
  return false;
}

/** Warmth at a card point, as the shader sums it (without the noise). */
export function heatAt(s: Thermal, x: number, y: number, w: number, h: number) {
  'worklet';
  let sum = 0;
  for (let j = 0; j < HEAT_SLOTS; j += 1) {
    const st = heatStrength(s.now - s.ht[j]) * s.fade;
    if (st <= 0) continue;
    const dx = x - s.hx[j];
    const dy = y - s.hy[j];
    sum += st * Math.exp(-(dx * dx + dy * dy) / (2 * HEAT_RADIUS * HEAT_RADIUS));
  }
  const cx = x / w - 0.5;
  const cy = y / h - 0.5;
  return sum + s.glob * (1.25 - 0.7 * Math.hypot(cx, cy));
}
