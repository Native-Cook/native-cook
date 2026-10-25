/*
 Iron Filings Magnet — layout, the magnets' script and the field.

 Each bar magnet is two poles (N +1 at the right end in its own frame, S −1 at the left), and the
 field at a point is the sum over poles of q·r / (|r|² + ε²)^1.5. A filing lines up with the field
 (it is a needle: θ and θ + π are the same), more firmly where the field is strong, and is pulled a
 few points towards the nearest pole, so filings crowd at the ends.

 autoPlay timeline (LOOP = 9 s):
   0.0 – 1.4   rest: magnet A in the middle under the paper, filings along its dipole lines.
   1.4 – 5.4   A traces a figure eight (x = sin 2πs, y = sin 4πs, s ease-in-out sine) and turns
               ±17° with it; each filing follows 30 – 520 ms late, later the farther it is.
   5.4 – 6.4   magnet B slides in from the right edge, north end first (ease-out cubic): two north
               poles face each other; A is pushed 40 pt left with a small overshoot (5.55 – 6.6).
   6.4 – 7.2   hold: the repulsion pattern, field lines bending away from the gap.
   7.2 – 8.2   B slides back out (ease-in-out cubic); A returns to the middle (7.3 – 8.2).
   8.2 – 9.0   rest.
 */

export const LOOP = 9;
export const FILINGS = 900;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOutCubic(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeInOutSine(x: number) {
  'worklet';
  return 0.5 - 0.5 * Math.cos(Math.PI * x);
}

export function easeOutBack(x: number) {
  'worklet';
  const c1 = 1.2;
  const c3 = c1 + 1;
  const u = x - 1;
  return 1 + c3 * u * u * u + c1 * u * u;
}

export type Geo = {
  w: number;
  h: number;
  u: number;
  paperX: number;
  paperY: number;
  paperW: number;
  paperH: number;
  /** The scattered field: top and bottom of the filings' area. */
  fieldTop: number;
  fieldBottom: number;
  cx: number;
  cy: number;
  /** Half length and half thickness of a bar magnet. */
  half: number;
  thick: number;
  /** Magnet B's resting x (mostly past the right edge). */
  parkX: number;
  footerY: number;
};

export function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const u = Math.min(w / 393, (h - top - bottom) / 759);
  const paperX = 12;
  const paperY = top + 4;
  const paperW = w - 24;
  const paperH = h - bottom - 8 - paperY;
  const fieldTop = paperY + 118 * u;
  const fieldBottom = paperY + paperH - 96 * u;
  return {
    w,
    h,
    u,
    paperX,
    paperY,
    paperW,
    paperH,
    fieldTop,
    fieldBottom,
    cx: w / 2,
    cy: (fieldTop + fieldBottom) / 2,
    half: 44 * u,
    thick: 13 * u,
    parkX: w + 30 * u,
    footerY: paperY + paperH - 64 * u,
  };
}

/** Magnet poses at loop time t: [ax, ay, aAngle, bx, by, bAngle]. B's angle π puts its N on the left. */
export function magnetsAt(g: Geo, t: number): number[] {
  'worklet';
  const s = easeInOutSine(seg(t, 1.4, 5.4));
  const ax0 = g.cx + g.paperW * 0.24 * Math.sin(2 * Math.PI * s);
  const ay = g.cy + (g.fieldBottom - g.fieldTop) * 0.13 * Math.sin(4 * Math.PI * s);
  const aAngle = 0.3 * Math.sin(2 * Math.PI * s);
  const push = 40 * g.u * (easeOutBack(seg(t, 5.55, 6.6)) - easeInOutCubic(seg(t, 7.3, 8.2)));
  const inX = g.cx + 104 * g.u;
  const bx = g.parkX + (inX - g.parkX) * (easeOutCubic(seg(t, 5.4, 6.4)) - easeInOutCubic(seg(t, 7.2, 8.2)));
  return [ax0 - push, ay, aAngle, bx, g.cy, Math.PI];
}

export const REST = (g: Geo) => magnetsAt(g, 0);

/** Field at (x, y) from both magnets: [bx, by]. */
export function fieldAt(x: number, y: number, m: number[], half: number, soft: number): [number, number] {
  'worklet';
  let bx = 0;
  let by = 0;
  for (let k = 0; k < 2; k += 1) {
    const mx = m[k * 3];
    const my = m[k * 3 + 1];
    const ca = Math.cos(m[k * 3 + 2]) * half;
    const sa = Math.sin(m[k * 3 + 2]) * half;
    for (let q = -1; q <= 1; q += 2) {
      const px = mx + q * ca;
      const py = my + q * sa;
      const dx = x - px;
      const dy = y - py;
      const r2 = dx * dx + dy * dy + soft;
      const inv = q / (r2 * Math.sqrt(r2));
      bx += dx * inv;
      by += dy * inv;
    }
  }
  return [bx, by];
}

/** The nearest pole to (x, y): [dx, dy, distance] pointing from the point to the pole. */
export function nearestPole(x: number, y: number, m: number[], half: number): [number, number, number] {
  'worklet';
  let best = 1e9;
  let bx = 0;
  let by = 0;
  for (let k = 0; k < 2; k += 1) {
    const ca = Math.cos(m[k * 3 + 2]) * half * 0.9;
    const sa = Math.sin(m[k * 3 + 2]) * half * 0.9;
    for (let q = -1; q <= 1; q += 2) {
      const dx = m[k * 3] + q * ca - x;
      const dy = m[k * 3 + 1] + q * sa - y;
      const d = Math.hypot(dx, dy);
      if (d < best) {
        best = d;
        bx = dx;
        by = dy;
      }
    }
  }
  return [bx, by, best];
}

/** Wraps an angle difference into (−π/2, π/2]: a filing has no front or back. */
export function axial(d: number) {
  'worklet';
  let a = d % Math.PI;
  if (a > Math.PI / 2) a -= Math.PI;
  if (a <= -Math.PI / 2) a += Math.PI;
  return a;
}

function hash(i: number) {
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** Filings scattered on a jittered grid over the field: x, y, resting angle, length (pt). */
export type Filings = { x: number[]; y: number[]; a0: number[]; len: number[] };

export function scatter(g: Geo): Filings {
  const left = g.paperX + 14;
  const right = g.paperX + g.paperW - 14;
  const w = right - left;
  const h = g.fieldBottom - g.fieldTop;
  const cols = Math.round(Math.sqrt((FILINGS * w) / h));
  const rows = Math.ceil(FILINGS / cols);
  const out: Filings = { x: [], y: [], a0: [], len: [] };
  for (let i = 0; i < FILINGS; i += 1) {
    const c = i % cols;
    const r = Math.floor(i / cols);
    out.x.push(left + ((c + 0.15 + 0.7 * hash(i)) * w) / cols);
    out.y.push(g.fieldTop + ((r + 0.15 + 0.7 * hash(i + 1000)) * h) / rows);
    out.a0.push(hash(i + 2000) * Math.PI);
    out.len.push((7 + 5 * hash(i + 3000)) * g.u);
  }
  return out;
}
