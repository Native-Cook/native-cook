// The blob's state and its timeline: a plain object on the UI thread, read by the shader's
// uniforms. In autoPlay every value is a pure function of the loop time t (so t = LOOP draws the
// same frame as t = 0); under a finger the shape weights and the held droplet run on springs.
//
// Units: points and seconds. Positions are on screen; `cx`, `cy` is the blob's centre and `R`
// its size (the drop's round end is 0.9 R across, the ring's centre line 0.8 R from the middle).

export const LOOP = 9;

/** drop → star, star → ring, ring → drop (s). Rest is 8.4 → 9 → 1.6 (a 2.2 s still drop). */
const TRANSITIONS = [
  [1.6, 3.2],
  [4.6, 6.2],
  [7.0, 8.4],
];
// After each morph the metal jiggles: a 5-lobe ripple of JIGGLE_AMP pt, period JIGGLE_PERIOD,
// fading to nothing (value and slope) over JIGGLE_FOR.
const JIGGLE_AMP = 3.2;
const JIGGLE_PERIOD = 0.32;
const JIGGLE_FOR = 0.6;
/** The resting wobble (pt), two lobed sines of 2 and 3 cycles per loop. */
export const WOBBLE_AMP = 2.2;
// Satellites: four droplets leave the surface during each morph, reach SAT_OUT × R from the
// centre at mid-morph (where the gap is wider than the smooth-min reach, so they pinch off) and
// fall back in. Each is staggered by SAT_STAGGER of the morph.
const SAT_OUT = 0.62;
const SAT_STAGGER = 0.08;
const SAT_K = 22;

// Under a finger: the shape weights follow the chosen shape on a playful spring (rad/s, ratio),
// the held droplet follows the finger closely and flies home on a looser one.
const SHAPE_W = 9;
const SHAPE_Z = 0.62;
const HOLD_W = 26;
const HOLD_Z = 0.9;
const HOME_W = 13;
const HOME_Z = 0.5;
/** The droplet's tether: smooth-min reach (pt). Its radius shrinks with distance from home. */
export const TETHER_K = 26;
/** Light haptic when the droplet is pulled this far from where it was grabbed (pt). */
export const DETACH_AT = 120;
/** Back to rest: wait this long after the last touch, then ease home over BACK_FOR (s). */
export const IDLE_BEFORE_BACK = 2;
const BACK_FOR = 0.85;

export const MODE_AUTO = 0;
export const MODE_LIVE = 1;
export const MODE_BACK = 2;

export type Metal = {
  mode: number;
  /** Loop time (s) and the frame clock (s since mount). */
  t: number;
  now: number;
  lastTouch: number;
  /** Shape weights (drop, star, ring), their velocities, and the shape a chip asked for. */
  w: number[];
  v: number[];
  target: number;
  /** Wobble phases (rad) and amplitude (pt); jiggle (pt). */
  p1: number;
  p2: number;
  amp: number;
  jiggle: number;
  /** Four satellites: x, y (from the centre), radius, smooth-min reach. */
  sats: number[];
  /** The held droplet: finger, droplet, its velocity, home (where it was grabbed). */
  held: boolean;
  shown: boolean;
  fx: number;
  fy: number;
  dx: number;
  dy: number;
  dvx: number;
  dvy: number;
  hx: number;
  hy: number;
  detached: boolean;
  /** Easing back: when it began, and the weights and phases it began from. */
  backAt: number;
  backW: number[];
  backP1: number;
  backP2: number;
  /** Set when a haptic is due; the component reads and clears it. */
  buzz: boolean;
};

export function makeMetal(auto: boolean, reduced: boolean): Metal {
  return {
    mode: auto ? MODE_AUTO : MODE_LIVE,
    t: 0,
    now: 0,
    lastTouch: -100,
    w: [1, 0, 0],
    v: [0, 0, 0],
    target: 0,
    p1: 0,
    p2: 0,
    amp: reduced ? 0 : WOBBLE_AMP,
    jiggle: 0,
    sats: new Array(16).fill(0),
    held: false,
    shown: false,
    fx: 0,
    fy: 0,
    dx: 0,
    dy: 0,
    dvx: 0,
    dvy: 0,
    hx: 0,
    hy: 0,
    detached: false,
    backAt: 0,
    backW: [1, 0, 0],
    backP1: 0,
    backP2: 0,
    buzz: false,
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

function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898) * 43758.5453;
  return s - Math.floor(s);
}

/** Shortest signed angle from a to b. */
function angleTo(a: number, b: number) {
  'worklet';
  const d = (((b - a) % (2 * Math.PI)) + 3 * Math.PI) % (2 * Math.PI);
  return d - Math.PI;
}

function wobblePhases(t: number): [number, number] {
  'worklet';
  return [(2 * Math.PI * 2 * t) / LOOP, (2 * Math.PI * 3 * t) / LOOP];
}

/** Writes the autoPlay frame at loop time t into m. */
export function scriptFrame(m: Metal, t: number, R: number) {
  'worklet';
  const a = easeInOut(seg(t, TRANSITIONS[0][0], TRANSITIONS[0][1]));
  const b = easeInOut(seg(t, TRANSITIONS[1][0], TRANSITIONS[1][1]));
  const c = easeInOut(seg(t, TRANSITIONS[2][0], TRANSITIONS[2][1]));
  m.w[0] = 1 - a + c;
  m.w[1] = a - b;
  m.w[2] = b - c;
  const [p1, p2] = wobblePhases(t);
  m.p1 = p1;
  m.p2 = p2;
  m.jiggle = 0;
  for (let k = 0; k < 3; k += 1) {
    const tau = t - TRANSITIONS[k][1];
    if (tau >= 0 && tau < JIGGLE_FOR) {
      const fade = 1 - tau / JIGGLE_FOR;
      m.jiggle = JIGGLE_AMP * Math.sin((2 * Math.PI * tau) / JIGGLE_PERIOD) * fade * fade;
    }
  }
  for (let i = 0; i < 16; i += 1) m.sats[i] = 0;
  for (let k = 0; k < 3; k += 1) {
    const [s0, s1] = TRANSITIONS[k];
    if (t <= s0 || t >= s1) continue;
    const s = (t - s0) / (s1 - s0);
    for (let i = 0; i < 4; i += 1) {
      const si = seg(s, SAT_STAGGER * i, 1 - SAT_STAGGER * (3 - i));
      if (si <= 0 || si >= 1) continue;
      const bump = Math.sin(Math.PI * si);
      const h = hash(i * 7 + k * 31 + 1);
      const angle = k * 0.9 + i * (Math.PI / 2) + 0.42 + (h - 0.5) * 0.7 + 0.6 * si;
      const dist = R * (0.9 + SAT_OUT * bump * (0.75 + 0.5 * h));
      m.sats[i * 4] = Math.cos(angle) * dist;
      m.sats[i * 4 + 1] = Math.sin(angle) * dist;
      m.sats[i * 4 + 2] = R * (0.1 + 0.08 * hash(i * 3 + k * 11 + 5)) * Math.min(1, bump * 3);
      m.sats[i * 4 + 3] = SAT_K * Math.min(1, bump * 4);
    }
  }
}

/** One step of a damped spring toward x0: returns [x, v]. */
function spring(x: number, v: number, x0: number, w: number, z: number, dt: number): [number, number] {
  'worklet';
  const nv = v + (-w * w * (x - x0) - 2 * z * w * v) * dt;
  return [x + nv * dt, nv];
}

/** Finger-driven frame: shape springs, the droplet, the wobble running on its own clock. */
export function liveFrame(m: Metal, dt: number) {
  'worklet';
  for (let i = 0; i < 3; i += 1) {
    const [x, v] = spring(m.w[i], m.v[i], i === m.target ? 1 : 0, SHAPE_W, SHAPE_Z, dt);
    m.w[i] = x;
    m.v[i] = v;
  }
  m.p1 += ((2 * Math.PI * 2) / LOOP) * dt;
  m.p2 += ((2 * Math.PI * 3) / LOOP) * dt;
  m.jiggle *= Math.exp(-6 * dt);
  for (let i = 0; i < 16; i += 1) m.sats[i] = 0;
  dropletStep(m, dt);
}

export function dropletStep(m: Metal, dt: number) {
  'worklet';
  if (!m.shown) return;
  const tx = m.held ? m.fx : m.hx;
  const ty = m.held ? m.fy : m.hy;
  const w = m.held ? HOLD_W : HOME_W;
  const z = m.held ? HOLD_Z : HOME_Z;
  // Substeps keep the stiff spring stable on a slow frame.
  const n = 4;
  for (let i = 0; i < n; i += 1) {
    const [x, vx] = spring(m.dx, m.dvx, tx, w, z, dt / n);
    const [y, vy] = spring(m.dy, m.dvy, ty, w, z, dt / n);
    m.dx = x;
    m.dvx = vx;
    m.dy = y;
    m.dvy = vy;
  }
  if (!m.held) {
    const far = Math.hypot(m.dx - m.hx, m.dy - m.hy);
    if (far < 0.6 && Math.hypot(m.dvx, m.dvy) < 8) m.shown = false;
  }
}

/** The held droplet's radius: full at home, thinner as it is pulled away. */
export function dropletRadius(m: Metal, R: number) {
  'worklet';
  const dist = Math.hypot(m.dx - m.hx, m.dy - m.hy);
  return Math.min(Math.max(0.34 * R - dist * 0.09, 0.13 * R), 0.34 * R);
}

export function beginBack(m: Metal) {
  'worklet';
  m.mode = MODE_BACK;
  m.backAt = m.now;
  m.backW = [m.w[0], m.w[1], m.w[2]];
  m.backP1 = m.p1;
  m.backP2 = m.p2;
  m.held = false;
}

/** Easing back to the loop's first frame; returns true when done. */
export function backFrame(m: Metal, dt: number) {
  'worklet';
  const k = easeInOut(Math.min((m.now - m.backAt) / BACK_FOR, 1));
  m.w[0] = m.backW[0] + (1 - m.backW[0]) * k;
  m.w[1] = m.backW[1] * (1 - k);
  m.w[2] = m.backW[2] * (1 - k);
  m.v[0] = 0;
  m.v[1] = 0;
  m.v[2] = 0;
  m.p1 = m.backP1 + angleTo(m.backP1, 0) * k;
  m.p2 = m.backP2 + angleTo(m.backP2, 0) * k;
  m.jiggle *= Math.exp(-6 * dt);
  for (let i = 0; i < 16; i += 1) m.sats[i] = 0;
  dropletStep(m, dt);
  if (k >= 1) {
    m.shown = false;
    m.jiggle = 0;
    m.target = 0;
    return true;
  }
  return false;
}
