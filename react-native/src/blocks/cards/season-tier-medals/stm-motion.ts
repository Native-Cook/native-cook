// Season Tier Medals — the carousel's physics and the autoplay script. Helpers above callers.

export const LOOP = 8;
export const TIERS = 3;
export const CURRENT = 1; // the visitor is Silver
/** Autoplay: rest on Silver, glide to Gold at 1.4 s, back to Silver at 4.4 s, rest to the end. */
export const SCRIPT = { toGold: 1.4, toSilver: 4.4, sweep: [6.0, 7.1] as const } as const;
/** A medal's spin when it is chosen: one turn about its vertical axis, landing with an overshoot. */
export const SPIN = 0.95;

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
export const easeOutBack = (k: number, c1 = 1.2) => {
  'worklet';
  const x = clamp01(k);
  return 1 + (c1 + 1) * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
};

/** The yaw a medal adds while it spins (radians, -2π → 0 with a small overshoot). */
export const spinYaw = (since: number) => {
  'worklet';
  if (since < 0 || since >= SPIN) return 0;
  return 2 * Math.PI * (easeOutBack(since / SPIN) - 1);
};

/** Rubber band past the first and last medal. */
export const rubber = (p: number) => {
  'worklet';
  const lo = 0;
  const hi = TIERS - 1;
  if (p < lo) return lo - 0.35 * Math.tanh((lo - p) / 0.35);
  if (p > hi) return hi + 0.35 * Math.tanh((p - hi) / 0.35);
  return p;
};

export type Rail = {
  p: number; // position: 0 bronze, 1 silver, 2 gold (fractional while moving)
  v: number; // velocity, medals per second
  target: number;
  mode: number; // 0 spring to target, 1 finger
  finger: number; // the finger's position while dragging
  spin0: number; // clock at which each medal began its spin (-10 none)
  spin1: number;
  spin2: number;
  landed: number; // which medal has played its landing cue for the current spin (-1 none)
  stiff: number; // spring stiffness in use (autoplay is softer than a release)
};

export const makeRail = (): Rail => {
  'worklet';
  return { p: CURRENT, v: 0, target: CURRENT, mode: 0, finger: CURRENT, spin0: -10, spin1: -10, spin2: -10, landed: -1, stiff: 150 };
};

export const spinStart = (r: Rail, i: number) => {
  'worklet';
  return i === 0 ? r.spin0 : i === 1 ? r.spin1 : r.spin2;
};
export const setSpin = (r: Rail, i: number, at: number) => {
  'worklet';
  if (i === 0) r.spin0 = at;
  else if (i === 1) r.spin1 = at;
  else r.spin2 = at;
  r.landed = -1;
};

/** One step: the finger sets the position directly; otherwise a near-critical spring to the target. */
export const stepRail = (r: Rail, dt: number) => {
  'worklet';
  if (r.mode === 1) {
    const np = r.finger;
    r.v = dt > 0 ? r.v * 0.6 + ((np - r.p) / dt) * 0.4 : r.v;
    r.p = np;
    return;
  }
  const k = r.stiff;
  const c = 2 * Math.sqrt(k) * 0.9;
  const n = Math.max(1, Math.ceil(dt / (1 / 240)));
  const h = dt / n;
  for (let i = 0; i < n; i++) {
    r.v += (k * (r.target - r.p) - c * r.v) * h;
    r.p += r.v * h;
  }
};

/** Where autoplay wants the rail at loop time t. */
export const scriptTarget = (t: number) => {
  'worklet';
  return t >= SCRIPT.toGold && t < SCRIPT.toSilver ? 2 : 1;
};
