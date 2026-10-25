/*
 The dock's magnetism, the scripted finger and the selection, as functions of time.

 Magnet layout (`magnetLayout`): each icon is pulled toward the finger along the vector to it,
 with a Gaussian falloff (130 pt); the icon nearest the finger in x grows to 1.35×, its
 neighbours less; then four relaxation passes along x push overlapping icons apart, so they never
 touch. Strength `s` (0–1) is how present the finger is.

 autoPlay (LOOP = 8 s):
   0.0 – 1.4   rest: Pen on the plate.
   1.4 – 2.2   a finger lands above the canvas and glides down toward the dock (ease-out).
   2.2 – 3.8   sweeps left → right along the dock (ease-in-out, dipping in the middle).
   3.8 – 4.6   comes back to Brush and lowers onto it.
   4.6 – 5.0   presses Brush; releases at 5.0: Brush clicks onto the plate (spring ω 20, ζ 0.45),
               the plate slides over (ω 14, ζ 0.62), the panel swaps (5.1 – 5.7).
   6.25 – 6.75 a short second touch lands on Pen and releases: Pen clicks back, the panel swaps back.
   7.4 – 8.0   rest.
 In autoPlay the icons follow the finger through a short weighted history of the layout (now and
 50 / 100 / 150 ms ago, heavier for icons further from the finger), which reads like springs with
 follow-through yet stays a pure function of t. Under the finger they ride real springs.
 */

export const LOOP = 8;
export const TOOLS = 6;
export const ICON = 48;
export const DOCK_H = 84;
export const REST_TOOL = 1;
export const PULL_RADIUS = 130;
export const GROW = 0.35;

export type Geo = {
  w: number;
  h: number;
  dockX: number;
  dockY: number;
  dockW: number;
  /** Rest centres of the icons, and the dock's centre line. */
  xs: number[];
  cy: number;
  panelTop: number;
  panelH: number;
  boardTop: number;
};

export function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const dockW = Math.min(w - 24, 372);
  const dockX = (w - dockW) / 2;
  const dockY = h - Math.max(bottom, 12) - 12 - DOCK_H;
  const pad = 10;
  const pitch = (dockW - 2 * pad) / TOOLS;
  const xs: number[] = [];
  for (let i = 0; i < TOOLS; i++) xs.push(dockX + pad + pitch * (i + 0.5));
  const panelH = 196;
  const panelTop = dockY - 16 - panelH;
  return { w, h, dockX, dockY, dockW, xs, cy: dockY + DOCK_H / 2, panelTop, panelH, boardTop: top + 100 };
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function springStep(tau: number, w: number, z: number) {
  'worklet';
  if (tau <= 0) return 0;
  const wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * tau) * (Math.cos(wd * tau) + ((z * w) / wd) * Math.sin(wd * tau));
}

function mix(a: number, b: number, k: number) {
  'worklet';
  return a + (b - a) * k;
}

/** The scripted finger: [x, y, strength 0–1, ring alpha 0–1]. */
export function loopFinger(g: Geo, t: number) {
  'worklet';
  const hover = g.dockY - 62;
  let x = g.w * 0.62;
  let y = g.boardTop + 120;
  let s = 0;
  let ring = 0;
  if (t >= 1.4 && t < 5.4) {
    const a = easeOut(seg(t, 1.4, 2.2));
    x = mix(g.w * 0.62, g.xs[0] - 4, a);
    y = mix(g.boardTop + 120, hover, a);
    if (t >= 2.2) {
      const b = easeInOut(seg(t, 2.2, 3.8));
      x = mix(g.xs[0] - 4, g.xs[5] + 4, b);
      y = hover + 12 * Math.sin(Math.PI * b);
    }
    if (t >= 3.8) {
      const c = easeInOut(seg(t, 3.8, 4.6));
      x = mix(g.xs[5] + 4, g.xs[2], c);
      y = mix(hover, g.dockY - 26, c);
    }
    if (t >= 4.6) {
      const d = easeInOut(seg(t, 4.6, 4.9));
      x = g.xs[2];
      y = mix(g.dockY - 26, g.cy, d);
    }
    const on = easeOut(seg(t, 1.4, 1.65));
    const off = easeOut(seg(t, 5.0, 5.35));
    s = on * (1 - off);
    ring = s;
  } else if (t >= 6.2 && t < 7.2) {
    const a = easeInOut(seg(t, 6.25, 6.6));
    x = mix(g.xs[1] + 18, g.xs[1], a);
    y = mix(g.dockY - 30, g.cy, a);
    s = 0.75 * easeOut(seg(t, 6.25, 6.4)) * (1 - easeOut(seg(t, 6.75, 7.1)));
    ring = s / 0.75;
  }
  return [x, y, s, ring];
}

/** The selection at loop time t: [from, to, time of the snap]. */
export function loopSelection(t: number) {
  'worklet';
  if (t >= 6.75) return [2, 1, 6.75];
  if (t >= 5.0) return [1, 2, 5.0];
  return [REST_TOOL, REST_TOOL, -10];
}

/**
 Where each icon wants to be for a finger at (fx, fy) with strength s: fills x, y, scale and the
 pull f (0–1) used for the field lines.
 */
export function magnetLayout(g: Geo, fx: number, fy: number, s: number, x: number[], y: number[], scale: number[], f: number[]) {
  'worklet';
  const above = Math.max(0, g.cy - fy - 24);
  const nearY = Math.exp(-(above / 150) * (above / 150));
  for (let i = 0; i < TOOLS; i++) {
    const dx = fx - g.xs[i];
    const dy = fy - g.cy;
    const d = Math.hypot(dx, dy);
    const pull = s * Math.exp(-(d / PULL_RADIUS) * (d / PULL_RADIUS));
    f[i] = pull;
    x[i] = g.xs[i] + dx * 0.16 * pull;
    y[i] = g.cy + Math.max(-30, dy * 0.2 * pull);
    scale[i] = 1 + GROW * s * nearY * Math.exp(-(dx / 62) * (dx / 62));
  }
  // Neighbours are pushed apart so the grown icons never overlap.
  for (let pass = 0; pass < 4; pass++) {
    for (let i = 0; i < TOOLS - 1; i++) {
      const need = (ICON * (scale[i] + scale[i + 1])) / 2 + 6;
      const gap = x[i + 1] - x[i] - need;
      if (gap < 0) {
        x[i] += gap / 2;
        x[i + 1] -= gap / 2;
      }
    }
  }
}

/** The loop's icon layout, with a short weighted history for follow-through. */
export function loopLayout(g: Geo, t: number, x: number[], y: number[], scale: number[], f: number[]) {
  'worklet';
  const lx = [0, 0, 0, 0, 0, 0];
  const ly = [0, 0, 0, 0, 0, 0];
  const ls = [0, 0, 0, 0, 0, 0];
  const lf = [0, 0, 0, 0, 0, 0];
  const now = loopFinger(g, t);
  for (let i = 0; i < TOOLS; i++) {
    x[i] = 0;
    y[i] = 0;
    scale[i] = 0;
  }
  magnetLayout(g, now[0], now[1], now[2], lx, ly, ls, f);
  let nearest = 0;
  for (let i = 1; i < TOOLS; i++) if (Math.abs(g.xs[i] - now[0]) < Math.abs(g.xs[nearest] - now[0])) nearest = i;
  const weights: number[][] = [];
  for (let i = 0; i < TOOLS; i++) {
    // Further from the finger, the more the icon lags (weights over now, −50, −100, −150 ms).
    const lag = Math.min(1, Math.abs(i - nearest) / 3);
    weights.push([0.55 - 0.25 * lag, 0.25, 0.13 + 0.12 * lag, 0.07 + 0.13 * lag]);
  }
  for (let k = 0; k < 4; k++) {
    if (k > 0) {
      const fp = loopFinger(g, Math.max(0, t - k * 0.05));
      magnetLayout(g, fp[0], fp[1], fp[2], lx, ly, ls, lf);
    }
    for (let i = 0; i < TOOLS; i++) {
      x[i] += lx[i] * weights[i][k];
      y[i] += ly[i] * weights[i][k];
      scale[i] += ls[i] * weights[i][k];
    }
  }
  return now;
}

/** The click of a tool landing on the plate: [y offset (pt), scale] at τ after the snap. */
export function clickAt(tau: number) {
  'worklet';
  if (tau < 0 || tau > 1.2) return [0, 1];
  const k = springStep(tau, 20, 0.45);
  return [-6 * (1 - k), 1 + 0.08 * (1 - k)];
}

/** The plate's x for a selection [from, to, at] at time t. */
export function plateX(g: Geo, sel: number[], t: number) {
  'worklet';
  return mix(g.xs[sel[0]], g.xs[sel[1]], springStep(t - sel[2], 14, 0.62));
}

/** The panel swap's progress (0 → 1) for a selection at time t. */
export function swapAt(sel: number[], t: number) {
  'worklet';
  if (sel[0] === sel[1]) return 1;
  return easeInOut(seg(t - sel[2], 0.1, 0.7));
}
