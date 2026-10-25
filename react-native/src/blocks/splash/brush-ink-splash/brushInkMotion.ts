/**
 * The Brush Ink Splash's clock and brush strokes. Every value is a closed-form function of the
 * loop time t (seconds), so t = LOOP draws the frame of t = 0. Lengths in points.
 *
 *   0.0 – 1.2   rest: blank rice paper.
 *   1.2 – 1.45  the brush loads: it presses down at the start of the circle (a swelling blot).
 *   1.45 – 3.0  it paints the ensō clockwise, easing in and out; the stroke thins and runs dry
 *               toward its end, bristle streaks opening up in it.
 *   3.0 – 4.2   the wordmark "Sumi", stroke after stroke, with the same brush.
 *   3.4 – 4.8   the ink bleeds into the paper's fibres at every edge (ease-out).
 *   4.8 – 6.2   hold.
 *   6.2 – 7.0   the paper slides up (ease-in-out), revealing the journal's home screen.
 *   7.0 – 7.25  the home screen alone.
 *   7.25 – 7.7  a fresh sheet fades back in over it.
 *   7.7 – 8.0   rest.
 */

export const LOOP = 8;
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

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 1.618) * 43758.5453;
  return s - Math.floor(s);
}

/** A brush stroke: centreline points, half-widths, and how dry its end runs (0 … 1). */
export type Stroke = { xs: number[]; ys: number[]; hw: number[]; dry: number };

/** Stroke lengths and where each starts on the shared paint clock (pt, with gaps between). */
export type Plan = { starts: number[]; lengths: number[]; dry: number[]; caps: number[]; ends: number[]; total: number; ensoEnd: number };

/** A gap of travel between strokes, while the brush is off the paper. */
const GAP = 40;
export const MAX_STROKES = 8;

function length(s: Stroke) {
  let L = 0;
  for (let i = 1; i < s.xs.length; i += 1) L += Math.hypot(s.xs[i] - s.xs[i - 1], s.ys[i] - s.ys[i - 1]);
  return L;
}

export function plan(strokes: Stroke[]): Plan {
  const starts: number[] = [];
  const lengths: number[] = [];
  const dry: number[] = [];
  const caps: number[] = [];
  const ends: number[] = [];
  let at = 0;
  let ensoEnd = 0;
  strokes.forEach((s, i) => {
    const L = length(s);
    starts.push(at);
    lengths.push(L);
    dry.push(s.dry);
    caps.push(s.hw[0] * 1.2);
    ends.push(s.hw[s.hw.length - 1] * 1.2);
    at += L;
    if (i === 0) ensoEnd = at;
    at += GAP;
  });
  while (starts.length < MAX_STROKES) {
    starts.push(1e6);
    lengths.push(1);
    dry.push(0);
    caps.push(1);
    ends.push(1);
  }
  return { starts, lengths, dry, caps, ends, total: at - GAP, ensoEnd };
}

/** The ensō: a near circle of radius R round (cx, cy), clockwise from about eight o'clock. */
export function enso(cx: number, cy: number, R: number): Stroke {
  const xs: number[] = [];
  const ys: number[] = [];
  const hw: number[] = [];
  const N = 150;
  const a0 = (150 / 180) * Math.PI;
  const sweep = (332 / 180) * Math.PI;
  for (let i = 0; i < N; i += 1) {
    const s = i / (N - 1);
    const a = a0 + sweep * s;
    // A hand-drawn circle: a little off round, curling inward at the end.
    const r = R * (1 + 0.03 * Math.sin(2 * a + 0.5) + 0.012 * Math.sin(5 * a) - 0.06 * s * s * s);
    xs.push(cx + Math.cos(a) * r);
    ys.push(cy + Math.sin(a) * r);
    // Pressure: a heavy blot where the brush lands, full through the first half, then lifting.
    const blot = 1 + 0.6 * Math.exp(-((s - 0.012) * (s - 0.012)) / 0.0005);
    const body = 0.8 + 0.3 * Math.sin(Math.PI * Math.min(1, s * 1.5)) + 0.06 * Math.sin(s * 23);
    const lift = 1 - 0.78 * Math.pow(Math.max(0, (s - 0.62) / 0.38), 1.4);
    hw.push(R * 0.125 * blot * body * lift);
  }
  return { xs, ys, hw, dry: 1 };
}

/** Catmull-Rom through points, sampled every ~2 pt, with a pressure curve along the stroke. */
function brush(pts: number[][], ox: number, oy: number, k: number, width: number, dry: number): Stroke {
  const xs: number[] = [];
  const ys: number[] = [];
  const hw: number[] = [];
  const P = pts.map(([x, y]) => [ox + x * k, oy + y * k]);
  const n = P.length;
  for (let i = 0; i < n - 1; i += 1) {
    const p0 = P[Math.max(0, i - 1)];
    const p1 = P[i];
    const p2 = P[i + 1];
    const p3 = P[Math.min(n - 1, i + 2)];
    const steps = Math.max(2, Math.ceil(Math.hypot(p2[0] - p1[0], p2[1] - p1[1]) / 2));
    for (let j = 0; j < steps; j += 1) {
      const t = j / steps;
      const t2 = t * t;
      const t3 = t2 * t;
      const f = (a: number, b: number, c: number, d: number) =>
        0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3);
      xs.push(f(p0[0], p1[0], p2[0], p3[0]));
      ys.push(f(p0[1], p1[1], p2[1], p3[1]));
    }
  }
  xs.push(P[n - 1][0]);
  ys.push(P[n - 1][1]);
  for (let i = 0; i < xs.length; i += 1) {
    const s = i / Math.max(1, xs.length - 1);
    // Lands with a press, carries, lifts off at the end.
    hw.push(width * (0.75 + 0.35 * Math.exp(-(s * s) / 0.01)) * (1 - 0.55 * s * s));
  }
  return { xs, ys, hw, dry };
}

/** The wordmark "Sumi" in brush strokes, about 125 × 60 units, scaled by k round (cx, top). */
export function wordmark(cx: number, top: number, k: number): Stroke[] {
  const ox = cx - 64 * k;
  const w = 4.4 * k;
  const S = [[31, 9], [20, 4], [9, 9], [9, 20], [20, 28], [30, 37], [28, 50], [16, 57], [3, 52]];
  const U = [[42, 24], [41, 42], [45, 54], [53, 57], [61, 50], [64, 38], [64, 24], [64.5, 42], [67, 57]];
  const M1 = [[78, 25], [78.5, 42], [78, 57]];
  const M2 = [[78, 39], [82, 29], [89, 26], [94, 33], [94.5, 57]];
  const M3 = [[94, 39], [98, 29], [105, 26], [110, 33], [110.5, 57]];
  const I = [[123, 28], [123.5, 43], [124, 57]];
  const DOT = [[121.5, 13], [124, 16]];
  return [
    brush(S, ox, top, k, w, 0.5),
    brush(U, ox, top, k, w, 0.45),
    brush(M1, ox, top, k, w, 0.2),
    brush(M2, ox, top, k, w, 0.4),
    brush(M3, ox, top, k, w, 0.55),
    brush(I, ox, top, k, w, 0.3),
    brush(DOT, ox, top, k, w * 1.15, 0),
  ];
}

export type Pose = {
  /** How far the brush has painted along the shared paint clock (pt). */
  head: number;
  /** Ink bled into the fibres (0 … 1) and the ink's opacity. */
  bleed: number;
  ink: number;
  /** Paper slid up (0 … 1) and its opacity (the fresh sheet fading back in). */
  slide: number;
  paper: number;
  /** Seconds since the brush landed (drops at the start) and since the ensō ended (flick). */
  landed: number;
  flicked: number;
};

export function loopPose(t: number, p: Plan): Pose {
  'worklet';
  let head = 0;
  if (t >= 1.2 && t < 1.45) head = 9 * easeOut(seg(t, 1.2, 1.45));
  else if (t >= 1.45 && t < 3.0) head = 9 + (p.ensoEnd - 9) * easeInOut(seg(t, 1.45, 3.0));
  else if (t >= 3.0) head = p.ensoEnd + (p.total + 2 - p.ensoEnd) * easeInOut(seg(t, 3.0, 4.2));
  const gone = t >= 7.0;
  return {
    head: gone ? 0 : head,
    bleed: gone ? 0 : easeOut(seg(t, 3.4, 4.8)),
    ink: gone ? 0 : 1,
    slide: gone ? 0 : easeInOut(seg(t, 6.2, 7.0)),
    paper: gone ? easeInOut(seg(t, 7.25, 7.7)) : 1,
    landed: !gone && t >= 1.25 ? t - 1.25 : -1,
    flicked: !gone && t >= 2.95 ? t - 2.95 : -1,
  };
}

/** The finished splash, still (reduced motion). */
export function paintedPose(p: Plan): Pose {
  'worklet';
  return { head: p.total + 2, bleed: 1, ink: 1, slide: 0, paper: 1, landed: 4, flicked: 4 };
}

/** Ink drops: [x, y, r] relative to their anchor, for the landing (set 0) and the flick (set 1). */
export function drops(set: number, angle: number): number[][] {
  const out: number[][] = [];
  const n = set === 0 ? 5 : 4;
  for (let i = 0; i < n; i += 1) {
    const h = hash(i + set * 17);
    const a = set === 0 ? hash(i + 3) * TAU : angle + (hash(i + 9) - 0.5) * 0.5;
    const d = set === 0 ? 14 + h * 22 : 10 + i * 9 + h * 6;
    out.push([Math.cos(a) * d, Math.sin(a) * d, set === 0 ? 1.2 + hash(i + 5) * 2.6 : 2.6 - i * 0.45]);
  }
  return out;
}
