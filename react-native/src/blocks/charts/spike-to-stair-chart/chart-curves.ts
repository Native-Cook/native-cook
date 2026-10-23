import { G, TREADS } from './chart-theme';
export const CURVES = {
  M: {
    start: [0.0, 0.0],
    segs: [
      [
        [0.06, 0.45],
        [0.19, 0.925],
        [0.28, 0.925],
      ],
      [
        [0.345, 0.925],
        [0.37, 0.52],
        [0.455, 0.52],
      ],
      [
        [0.52, 0.52],
        [0.535, 0.825],
        [0.595, 0.825],
      ],
      [
        [0.63, 0.825],
        [0.65, 0.718],
        [0.682, 0.718],
      ],
      [
        [0.71, 0.718],
        [0.72, 0.79],
        [0.75, 0.79],
      ],
      [
        [0.81, 0.79],
        [0.87, 0.42],
        [0.957, 0.0],
      ],
    ],
  },
  D: {
    start: [0.0, 0.0],
    segs: [
      [
        [0.03, 0.045],
        [0.06, 0.085],
        [0.1, 0.085],
      ],
      [
        [0.135, 0.085],
        [0.142, 0.069],
        [0.17, 0.069],
      ],
      [
        [0.21, 0.069],
        [0.185, 0.24],
        [0.235, 0.24],
      ],
      [
        [0.305, 0.24],
        [0.319, 0.221],
        [0.375, 0.221],
      ],
      [
        [0.415, 0.221],
        [0.39, 0.397],
        [0.44, 0.397],
      ],
      [
        [0.484, 0.397],
        [0.4928, 0.419],
        [0.528, 0.431],
      ],
      [
        [0.568, 0.443],
        [0.543, 0.602],
        [0.593, 0.602],
      ],
      [
        [0.6255, 0.602],
        [0.632, 0.62],
        [0.658, 0.632],
      ],
      [
        [0.698, 0.644],
        [0.673, 0.835],
        [0.723, 0.835],
      ],
      [
        [0.85, 0.835],
        [0.975, 0.92],
        [1.06, 1.14],
      ],
      [
        [1.105, 1.26],
        [1.15, 1.45],
        [1.185, 1.66],
      ],
      [
        [1.205, 1.78],
        [1.225, 1.95],
        [1.24, 2.08],
      ],
    ],
  },
};
export type Sample = { u: number; v: number; x: number; y: number; a: number };
// Precompute arc-length samples and joint ranges from the supplied cubic curves.
function sampleCurve(curve: typeof CURVES.M) {
  const points: Sample[] = [{ u: 0, v: 0, x: G.px, y: G.ay, a: 0 }];
  const joints = [0];
  let start = curve.start;
  let length = 0;
  for (const seg of curve.segs) {
    for (let j = 1; j <= 64; j++) {
      const t = j / 64,
        q = 1 - t;
      const u =
        q * q * q * start[0] + 3 * q * q * t * seg[0][0] + 3 * q * t * t * seg[1][0] + t * t * t * seg[2][0];
      const v =
        q * q * q * start[1] + 3 * q * q * t * seg[0][1] + 3 * q * t * t * seg[1][1] + t * t * t * seg[2][1];
      const x = G.px + u * G.pw,
        y = G.ay - v * G.ph,
        last = points[points.length - 1];
      length += Math.hypot(x - last.x, y - last.y);
      points.push({ u, v, x, y, a: length });
    }
    joints.push(length);
    start = seg[2];
  }
  for (const p of points) p.a /= length;
  return { points, joints: joints.map((a) => a / length), length };
}
export const M = sampleCurve(CURVES.M);
export const D = sampleCurve(CURVES.D);
export const STAIR_END = D.joints[9];
// Bound scalar values without importing a runtime into numeric worklets.
export function clamp(n: number, lo = 0, hi = 1) {
  'worklet';
  return Math.max(lo, Math.min(hi, n));
}
// Interpolate scalars for shared curve and film calculations.
export function mix(a: number, b: number, p: number) {
  'worklet';
  return a + (b - a) * p;
}
/** Binary search over monotonic x / arc coordinates; no callback in UI worklets. */
export function lookup(points: Sample[], n: number, axis: 'u' | 'a') {
  'worklet';
  let lo = 0,
    hi = points.length - 1;
  n = clamp(n, points[0][axis], points[hi][axis]);
  while (hi - lo > 1) {
    const m = (hi + lo) >> 1;
    if (points[m][axis] < n) lo = m;
    else hi = m;
  }
  const a = points[lo],
    b = points[hi],
    p = (n - a[axis]) / (b[axis] - a[axis] || 1);
  return {
    u: mix(a.u, b.u, p),
    v: mix(a.v, b.v, p),
    x: mix(a.x, b.x, p),
    y: mix(a.y, b.y, p),
    a: mix(a.a, b.a, p),
    angle: Math.atan2(b.y - a.y, b.x - a.x),
  };
}
// Resolve position and tangent from normalized distance along a curve.
export function atArc(d: number, a: number) {
  'worklet';
  return lookup(d ? D.points : M.points, a, 'a');
}
// Resolve position and tangent from the finger or film horizontal coordinate.
export function atU(d: number, u: number) {
  'worklet';
  return lookup(d ? D.points : M.points, u, 'u');
}
// Choose the nearest resting step centre for a released marker.
export function nearestTread(u: number) {
  'worklet';
  const centres = TREADS;
  let best = centres[0];
  for (let i = 1; i < centres.length; i++)
    if (Math.abs(centres[i] - u) < Math.abs(best - u)) best = centres[i];
  return best;
}
// Build static SVG paths once from sampled design-space points.
function pathFor(points: Sample[]) {
  return points.map((p, i) => `${i ? 'L' : 'M'}${p.x.toFixed(4)} ${p.y.toFixed(4)}`).join(' ');
}
export const LINE_PATHS = [pathFor(M.points), pathFor(D.points)];
export const AREA_PATHS = [0, 1].map((d) => {
  const pts = (d ? D : M).points.filter((p) => p.u <= (d ? 0.968 : 0.957));
  const last = atU(d, d ? 0.968 : 0.957);
  return pathFor([...pts, last]) + ` L${last.x} ${G.ay} L${G.px} ${G.ay} Z`;
});
export const PIECES = M.joints.slice(0, -1).map((start, i) => {
  const pts = M.points.slice(i * 64, i * 64 + 65),
    ys = pts.map((p) => p.y);
  return {
    start,
    end: M.joints[i + 1],
    path: pathFor(pts),
    cx: (pts[0].x + pts[64].x) / 2,
    cy: (Math.min(...ys) + Math.max(...ys)) / 2,
    bottom: Math.max(...ys),
    left: pts[0].x,
    right: pts[64].x,
  };
});
// Optical glyphs preserve clean silhouettes at the switch's 24 by 14 point size.
export const GLYPH_PATHS = ['M-12 7L-6 -7L0 3L5 -2L12 7', 'M-12 7H-6V3H0V-1H6V-5H9L12 -7'];
export const GRID_PATH = Array.from({ length: 13 }, (_, i) => {
  const n = ((i + 1) * G.pw) / 14;
  return `M${G.px + n} ${G.py}V${G.ay} M${G.px} ${G.py + n}H${G.px + G.pw}`;
}).join(' ');
export const DOT_PATH = Array.from({ length: 90 }, (_, i) => {
  const x = G.px + (0.13 + 0.1 * (i % 9)) * G.pw,
    y = G.ay - (0.04 + 0.1 * Math.floor(i / 9)) * G.ph;
  return `M${x - 0.9} ${y}a.9 .9 0 1 0 1.8 0a.9 .9 0 1 0 -1.8 0`;
}).join(' ');
export const AXIS_PATH = `M64 236V510H338`;
