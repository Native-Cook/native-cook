/*
 Slinky Stairs — the motion. The slinky is 40 rings. Each frame places every ring (centre and the
 direction of its long axis) from a pure function of the loop time: a ring is in the stack it is
 leaving, in the arch, or in the stack it is building. The arch is a cubic Bézier between the two
 stacks' tops whose end controls follow the end rings' axes, so the flipping end turns over smoothly.

 autoPlay (LOOP = 9 s):
   0.0 – 1.2   rest on the top step.
   1.2 – 6.4   three steps, 1.733 s each: tip over (30 %: the top end arcs over and lands on the next
               step, its axis turning 180°), pour (52 %: coils flow through the arch), flip (18 %: the
               last end leaves the old step and lands on top of the new stack). Ease-in-out per phase.
   6.4 – 7.0   settle on the bottom step (a damped squash, 5.5 Hz).
   7.0 – 8.4   lifted back to the top along an arc by an unseen hand holding the top ring: the coils
               hang and stretch (up to 1.9x) and swing behind the motion; set down with a squash.
   8.4 – 9.0   rest. 9.0 is the same frame as 0.0.
*/

export const LOOP = 9;
export const N = 40;
export const STEPS = 3;
export const WALK_FROM = 1.2;
export const STEP_FOR = (6.4 - 1.2) / 3;
export const LIFT_FROM = 7.0;
export const LIFT_FOR = 1.4;
/** Coils that live in the arch while it pours. */
const ARCH = 12;
const TIP = 0.3;
const POUR = 0.82;

export type Geo = {
  w: number;
  h: number;
  /** Stack-bottom centre on each step (0 = top step). */
  baseX: number[];
  baseY: number[];
  /** Ring radius (pt), stacked pitch (pt), and the camera's tilt (minor / major). */
  R: number;
  pitch: number;
  tilt: number;
  tread: number;
  riser: number;
};

export type Pose = {
  /** Per ring: centre x, y and the unit long axis nx, ny (the short axis is screen-up, R * tilt). */
  rings: number[];
  /** Contact shadows: x, y, half width, alpha. */
  shadows: number[];
  /** The label's position on its roll (0 Ready, 1 – 3 Step k, 4 Landed, 5 Ready again). */
  label: number;
};

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOutCubic(x: number) {
  'worklet';
  const k = clamp01(x);
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
}

export function easeOutCubic(x: number) {
  'worklet';
  const k = 1 - clamp01(x);
  return 1 - k * k * k;
}

/** Point and tangent of a cubic Bézier at w: [x, y, tx, ty]. */
function bez(ax: number, ay: number, bx: number, by: number, cx: number, cy: number, dx: number, dy: number, w: number, out: number[]) {
  'worklet';
  const u = 1 - w;
  out[0] = u * u * u * ax + 3 * u * u * w * bx + 3 * u * w * w * cx + w * w * w * dx;
  out[1] = u * u * u * ay + 3 * u * u * w * by + 3 * u * w * w * cy + w * w * w * dy;
  let tx = 3 * u * u * (bx - ax) + 6 * u * w * (cx - bx) + 3 * w * w * (dx - cx);
  let ty = 3 * u * u * (by - ay) + 6 * u * w * (cy - by) + 3 * w * w * (dy - cy);
  const l = Math.hypot(tx, ty);
  if (l < 1e-4) {
    tx = 0;
    ty = -1;
  } else {
    tx /= l;
    ty /= l;
  }
  out[2] = tx;
  out[3] = ty;
}

/** Pushes one ring whose axis (from the slinky's first end to its last) is (tx, ty). */
function ring(out: number[], x: number, y: number, tx: number, ty: number) {
  'worklet';
  // The long axis is perpendicular to the slinky's axis.
  out.push(x, y, -ty, tx);
}

/** A stack standing at (x, y): ring i of n at height i * pitch * squash. */
function stackRings(out: number[], x: number, y: number, count: number, pitch: number) {
  'worklet';
  for (let i = 0; i < count; i += 1) ring(out, x, y - i * pitch, 0, -1);
}

/**
 * The general walking slinky: `n1` coils stacked at A (first end at the bottom), `n2` coils stacked
 * at B (last end at the bottom), and the rest in an arch from the top of A (or from `freeA`, the
 * first end in the air) to the top of B (or `freeB`, the last end in the air). Directions are the
 * end rings' axes.
 */
function walkRings(
  g: Geo,
  ax: number,
  ay: number,
  bx: number,
  by: number,
  n1: number,
  n2: number,
  startX: number,
  startY: number,
  startDir: number,
  endX: number,
  endY: number,
  endDir: number,
  out: number[],
) {
  'worklet';
  const p = g.pitch;
  const tmp = [0, 0, 0, 0];
  const archCount = N - n1 - n2;
  const span = Math.hypot(endX - startX, endY - startY);
  // Controls grow with the arch, so a short arch (an end just lifting) never loops back on itself.
  const h = Math.max(1, Math.min(170, span * 0.55 + archCount * 1.5));
  const sdx = Math.cos(startDir);
  const sdy = Math.sin(startDir);
  const edx = Math.cos(endDir);
  const edy = Math.sin(endDir);
  for (let i = 0; i < N; i += 1) {
    if (i < n1 - 0.5) {
      ring(out, ax, ay - i * p, 0, -1);
    } else if (i > N - 1 - n2 + 0.5) {
      ring(out, bx, by - (N - 1 - i) * p, 0, -1);
    } else {
      const lo = Math.max(0, n1 - 0.5);
      const hi = Math.min(N - 1, N - 1 - n2 + 0.5);
      const w = hi - lo < 1e-3 ? 0.5 : clamp01((i - lo) / (hi - lo));
      bez(startX, startY, startX + sdx * h, startY + sdy * h, endX - edx * h, endY - edy * h, endX, endY, w, tmp);
      ring(out, tmp[0], tmp[1], tmp[2], tmp[3]);
    }
  }
}

/** One step from step k to k + 1, u in 0..1. */
export function stepRings(g: Geo, k: number, u: number, out: number[]) {
  'worklet';
  const p = g.pitch;
  const ax = g.baseX[k];
  const ay = g.baseY[k];
  const bx = g.baseX[k + 1];
  const by = g.baseY[k + 1];
  const H = (N - 1) * p;
  const UP = -Math.PI / 2;
  if (u < TIP) {
    // The top end tips over and lands on the next step.
    const v = easeInOutCubic(u / TIP);
    const nA = ARCH * Math.min(1, v * 1.4);
    const n1 = N - nA;
    const x0 = ax;
    const y0 = ay - H;
    const ex = x0 + (bx - x0) * (3 * (1 - v) * v * v * 0.95 + v * v * v) + (1 - v) * (1 - v) * v * 3 * g.tread * 0.25;
    const ey = Math.pow(1 - v, 3) * y0 + 3 * (1 - v) * (1 - v) * v * (y0 - g.riser * 0.9) + 3 * (1 - v) * v * v * (by - g.riser * 1.25) + v * v * v * by;
    walkRings(g, ax, ay, bx, by, n1, 0, ax, ay - (n1 - 1) * p, UP, ex, ey, UP + Math.PI * v, out);
    return;
  }
  if (u < POUR) {
    // Coils pour through the arch.
    const v = easeInOutCubic((u - TIP) / (POUR - TIP));
    const n1 = (N - ARCH) * (1 - v);
    const n2 = (N - ARCH) * v;
    walkRings(g, ax, ay, bx, by, n1, n2, ax, ay - Math.max(0, n1 - 1) * p, UP, bx, by - Math.max(0, n2 - 1) * p, Math.PI / 2, out);
    return;
  }
  // The first end leaves the old step and flips over onto the new stack.
  const v = easeInOutCubic((u - POUR) / (1 - POUR));
  const n2 = N - ARCH * (1 - v);
  const topB = by - (N - 1) * p;
  const q = 1 - v;
  const fx = q * q * q * ax + 3 * q * q * v * (ax + g.tread * 0.12) + 3 * q * v * v * (bx - g.tread * 0.35) + v * v * v * bx;
  const fy = q * q * q * ay + 3 * q * q * v * (ay - g.riser * 0.55) + 3 * q * v * v * (topB - 34) + v * v * v * topB;
  walkRings(g, ax, ay, bx, by, 0, n2, fx, fy, UP + Math.PI * v, bx, by - Math.max(0, n2 - 1) * p, Math.PI / 2, out);
}

/** A stack squashing after a landing: e seconds since. */
export function squash(e: number) {
  'worklet';
  if (e < 0 || e > 0.8) return 1;
  return 1 - 0.2 * Math.exp(-e / 0.14) * Math.cos(2 * Math.PI * 5.5 * e);
}

/** Lifted along an arc from step k to step 0 (v in 0..1): the coils hang from the top ring. */
export function liftRings(g: Geo, k: number, v: number, out: number[]) {
  'worklet';
  const e = easeInOutCubic(v);
  const H = (N - 1) * g.pitch;
  const x0 = g.baseX[k];
  const y0 = g.baseY[k] - H;
  const x1 = g.baseX[0];
  const y1 = g.baseY[0] - H;
  const apexY = Math.min(y0, y1) - g.riser * 1.3;
  const topX = x0 + (x1 - x0) * e;
  const topY = (1 - e) * (1 - e) * y0 + 2 * (1 - e) * e * apexY + e * e * y1;
  // Velocity of the hand (for the swing) from the curve's derivative.
  const de = 6 * v * (1 - v);
  const vx = (x1 - x0) * de;
  const stretch = 1 + 0.9 * Math.sin(Math.PI * e);
  for (let i = 0; i < N; i += 1) {
    const d = (N - 1 - i) / (N - 1);
    const lag = -vx * 0.09 * d * d;
    ring(out, topX + lag, topY + (N - 1 - i) * g.pitch * stretch, lag * 0.004, -1);
  }
}

/** The finger holding the top ring at (px, py): the slinky stretches from its stack at step k. */
export function stretchRings(g: Geo, k: number, px: number, py: number, out: number[]) {
  'worklet';
  const bx = g.baseX[k];
  const by = g.baseY[k];
  const L = Math.hypot(px - bx, py - by);
  const H = (N - 1) * g.pitch;
  const tmp = [0, 0, 0, 0];
  if (L <= H) {
    // Squashed (or only leaning): a straight, tilted stack.
    const tx = L > 1e-3 ? (px - bx) / L : 0;
    const ty = L > 1e-3 ? (py - by) / L : -1;
    for (let i = 0; i < N; i += 1) {
      const k = i / (N - 1);
      ring(out, bx + (px - bx) * k, by + (py - by) * k, tx, ty);
    }
    return;
  }
  const h = L * 0.32;
  for (let i = 0; i < N; i += 1) {
    const w = Math.pow(i / (N - 1), 1.5);
    bez(bx, by, bx, by - h, px, py + h, px, py, w, tmp);
    ring(out, tmp[0], tmp[1], tmp[2], tmp[3]);
  }
}

/** Step label value for the loop. */
export function loopLabel(t: number) {
  'worklet';
  let v = 0;
  for (let s = 0; s < STEPS; s += 1) v += easeInOutCubic(seg(t, WALK_FROM + s * STEP_FOR, WALK_FROM + s * STEP_FOR + 0.3));
  v += easeInOutCubic(seg(t, 6.4, 6.7));
  v += easeInOutCubic(seg(t, LIFT_FROM, LIFT_FROM + 0.3));
  return v;
}

function addShadow(out: number[], x: number, y: number, r: number, a: number) {
  'worklet';
  out.push(x, y, r, a);
}

export function loopPose(t: number, g: Geo): Pose {
  'worklet';
  const rings: number[] = [];
  const shadows: number[] = [];
  if (t < WALK_FROM) {
    stackRings(rings, g.baseX[0], g.baseY[0], N, g.pitch * squash(t + LOOP - 8.4));
    addShadow(shadows, g.baseX[0], g.baseY[0], g.R, 1);
  } else if (t < 6.4) {
    const s = Math.min(STEPS - 1, Math.floor((t - WALK_FROM) / STEP_FOR));
    const u = (t - WALK_FROM - s * STEP_FOR) / STEP_FOR;
    stepRings(g, s, u, rings);
    const leave = u < POUR ? 1 - seg(u, TIP, POUR) * 0.6 : 0.4 * (1 - seg(u, POUR, POUR + 0.08));
    const arrive = u < TIP ? seg(u, TIP * 0.8, TIP) * 0.5 : 0.5 + 0.5 * seg(u, TIP, 1);
    addShadow(shadows, g.baseX[s], g.baseY[s], g.R, leave);
    addShadow(shadows, g.baseX[s + 1], g.baseY[s + 1], g.R, arrive);
  } else if (t < LIFT_FROM) {
    stackRings(rings, g.baseX[STEPS], g.baseY[STEPS], N, g.pitch * squash(t - 6.4));
    addShadow(shadows, g.baseX[STEPS], g.baseY[STEPS], g.R, 1);
  } else if (t < LIFT_FROM + LIFT_FOR) {
    const v = (t - LIFT_FROM) / LIFT_FOR;
    liftRings(g, STEPS, v, rings);
    const e = easeInOutCubic(v);
    addShadow(shadows, g.baseX[STEPS], g.baseY[STEPS], g.R, 1 - seg(v, 0, 0.25));
    addShadow(shadows, g.baseX[0], g.baseY[0], g.R * (1.4 - 0.4 * e), seg(v, 0.7, 1));
  } else {
    stackRings(rings, g.baseX[0], g.baseY[0], N, g.pitch * squash(t - 8.4));
    addShadow(shadows, g.baseX[0], g.baseY[0], g.R, 1);
  }
  return { rings, shadows, label: loopLabel(t) };
}
