/**
 * The Fuse Burn Button's clock and geometry. Every value is a closed-form function of the loop
 * time t (seconds), so t = LOOP draws the same frame as t = 0. Lengths in points.
 *
 *   0.0 – 1.4   rest: a braided fuse wrapped round the "Light it up" capsule.
 *   1.4 – 1.8   a match flares at the fuse's start (the capsule's left end).
 *   1.8 – 6.2   the spark runs clockwise round the capsule: it catches slowly, speeds up,
 *               sputters at 40 % (dims and flickers, nearly stops), then runs on, easing in and
 *               out. Behind it the cord turns to crinkled charcoal and smoke rises.
 *   6.2 – 6.9   the spark meets the start: 30 sparks burst from the capsule, it lights up amber,
 *               the label becomes "You're in", the count rolls 211 → 212.
 *   6.9 – 7.8   hold.
 *   7.8 – 8.7   the cord re-weaves from the start (drawn back on), the charcoal fades, the label
 *               and the count go back.
 *   8.7 – 9.0   rest.
 */

export const LOOP = 9;
const TAU = Math.PI * 2;

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
  const u = 1 - clamp01(x);
  return 1 - u * u * u;
}

export function easeInOut(x: number) {
  'worklet';
  const u = clamp01(x);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

/** Quadratic in-out: gentler top speed than the cubic. */
export function easeInOut2(x: number) {
  'worklet';
  const u = clamp01(x);
  return u < 0.5 ? 2 * u * u : 1 - Math.pow(-2 * u + 2, 2) / 2;
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** Where the burn has reached along the fuse (0…1) at loop time t. */
export function burnAt(t: number) {
  'worklet';
  const u = seg(t, 1.8, 6.2);
  if (u < 0.38) return 0.4 * easeInOut2(u / 0.38);
  if (u < 0.5) return 0.4 + 0.012 * ((u - 0.38) / 0.12);
  return 0.412 + 0.588 * easeInOut2((u - 0.5) / 0.5);
}

export type Pose = {
  /** Burnt up to b (0…1 of the fuse); the cord re-woven over [0, w]. */
  b: number;
  w: number;
  /** The spark's brightness (0 off … 1), the match flare (0…1), its speed along the fuse (pt/s). */
  spark: number;
  flare: number;
  speed: number;
  /** Seconds since the burst (negative = none). */
  burstAge: number;
  /** Capsule lit amber (0…1), label "You're in" (0…1), count rolled to 212 (0…1). */
  lit: number;
  label: number;
  count: number;
  /** The time particles are animated by (loop time in the loop, the frame clock under a finger). */
  time: number;
};

export function restPose(): Pose {
  'worklet';
  return { b: 0, w: 0, spark: 0, flare: 0, speed: 0, burstAge: -1, lit: 0, label: 0, count: 0, time: 0 };
}

export function loopPose(t: number, length: number): Pose {
  'worklet';
  const b = burnAt(t);
  const speed = ((burnAt(t + 0.01) - burnAt(t - 0.01)) / 0.02) * length;
  const u = seg(t, 1.8, 6.2);
  // The sputter: around 40 % the spark dims and flickers at 5 Hz.
  const dip = Math.sin(Math.PI * seg(u, 0.36, 0.52));
  const flicker = 0.6 + 0.4 * Math.sin((TAU * 45 * t) / LOOP);
  let spark = easeOut(seg(t, 1.4, 1.55)) * (1 - 0.7 * dip * flicker);
  spark *= 1 - seg(t, 6.2, 6.32);
  const flare = Math.sin(Math.PI * seg(t, 1.4, 1.8));
  const back = easeInOut(seg(t, 7.8, 8.4));
  return {
    b,
    w: easeInOut(seg(t, 7.8, 8.7)),
    spark,
    flare,
    speed,
    burstAge: t >= 6.2 ? t - 6.2 : -1,
    lit: easeOut(seg(t, 6.2, 6.5)) * (1 - back),
    label: easeOut(seg(t, 6.3, 6.8)) * (1 - back),
    count: easeInOut(seg(t, 6.35, 6.9)) * (1 - back),
    time: t,
  };
}

/** Points sampled evenly along the fuse. */
export const SAMPLES = 480;

export type Fuse = {
  xs: number[];
  ys: number[];
  /** Outward normal. */
  nx: number[];
  ny: number[];
  length: number;
};

/**
 * The fuse: a stadium (capsule outline) of half-height r round the capsule, starting at its left
 * end and running clockwise (up over the top first).
 */
export function makeFuse(x: number, y: number, w: number, h: number, offset: number): Fuse {
  const r = h / 2 + offset;
  const cy = y + h / 2;
  const left = x + h / 2;
  const right = x + w - h / 2;
  const straight = right - left;
  const arc = Math.PI * r;
  const length = 2 * straight + 2 * arc;
  const xs: number[] = [];
  const ys: number[] = [];
  const nx: number[] = [];
  const ny: number[] = [];
  for (let i = 0; i < SAMPLES; i += 1) {
    let s = (i / (SAMPLES - 1)) * length;
    let a: number;
    let px: number;
    let py: number;
    if (s < arc / 2) {
      a = Math.PI + s / r;
      px = left + Math.cos(a) * r;
      py = cy + Math.sin(a) * r;
    } else if ((s -= arc / 2) < straight) {
      a = -Math.PI / 2;
      px = left + s;
      py = cy - r;
    } else if ((s -= straight) < arc) {
      a = -Math.PI / 2 + s / r;
      px = right + Math.cos(a) * r;
      py = cy + Math.sin(a) * r;
    } else if ((s -= arc) < straight) {
      a = Math.PI / 2;
      px = right - s;
      py = cy + r;
    } else {
      s -= straight;
      a = Math.PI / 2 + s / r;
      px = left + Math.cos(a) * r;
      py = cy + Math.sin(a) * r;
    }
    xs.push(px);
    ys.push(py);
    nx.push(Math.cos(a));
    ny.push(Math.sin(a));
  }
  return { xs, ys, nx, ny, length };
}

/** A point on the fuse at u (0…1): [x, y, normal x, normal y]. */
export function fuseAt(f: Fuse, u: number): [number, number, number, number] {
  'worklet';
  const k = clamp01(u) * (SAMPLES - 1);
  const i = Math.min(SAMPLES - 2, Math.floor(k));
  const fr = k - i;
  return [
    f.xs[i] + (f.xs[i + 1] - f.xs[i]) * fr,
    f.ys[i] + (f.ys[i + 1] - f.ys[i]) * fr,
    f.nx[i] + (f.nx[i + 1] - f.nx[i]) * fr,
    f.ny[i] + (f.ny[i + 1] - f.ny[i]) * fr,
  ];
}

/** The fuse's u nearest to (x, y), and the distance to it. */
export function nearestOnFuse(f: Fuse, x: number, y: number): [number, number] {
  'worklet';
  let best = 0;
  let bestD = 1e9;
  for (let i = 0; i < SAMPLES; i += 2) {
    const dx = f.xs[i] - x;
    const dy = f.ys[i] - y;
    const d = dx * dx + dy * dy;
    if (d < bestD) {
      bestD = d;
      best = i;
    }
  }
  return [best / (SAMPLES - 1), Math.sqrt(bestD)];
}
