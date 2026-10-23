/*
 The data behind Camera Carousel Fan (a port of CameraCarouselFanModel.swift; the numbers are the
 same): five invented cameras built from a few extruded shapes (rounded rectangles and circles
 pushed out along an axis), the eight drawn "photos" that orbit them, the film looks that tint
 those photos, and the autoPlay timeline.

 Units are points at a 393 pt wide screen; x right, y up, z towards the viewer. Vectors are
 [x, y, z] arrays so the worklets on the UI thread can read them.
 */

export type V3 = [number, number, number];
type V2 = [number, number];

export function add(a: V3, b: V3): V3 {
  'worklet';
  return [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
}
export function sub(a: V3, b: V3): V3 {
  'worklet';
  return [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
}
export function mul(a: V3, k: number): V3 {
  'worklet';
  return [a[0] * k, a[1] * k, a[2] * k];
}
export function dot(a: V3, b: V3) {
  'worklet';
  return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
}
export function cross(a: V3, b: V3): V3 {
  'worklet';
  return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
}
export function normalize(v: V3): V3 {
  'worklet';
  const l = Math.sqrt(dot(v, v));
  return l < 1e-9 ? [0, 0, 1] : [v[0] / l, v[1] / l, v[2] / l];
}

// MARK: Geometry

type Kind = { solid: true } | { glass: number } | { decal: number };

type Part = {
  profile: V2[];
  depth: number;
  origin: V3;
  color: V3;
  gloss: number;
  kind?: Kind;
  /** Alternate side quads are darkened, like a knurled focus ring. */
  stripes?: boolean;
  /** Added to the depth the faces are sorted by: lenses sit in front of the trim around them. */
  bias?: number;
  u?: V3;
  v?: V3;
  n?: V3;
};

export type Face = {
  /** Side quads: [back k, back k+1, front k+1, front k]. Caps: the profile. */
  pts: V3[];
  normal: V3;
  /** Side quads only: the smoothed normals of the quad's two long edges (k and k+1). */
  n0: V3 | null;
  n1: V3 | null;
  color: V3;
  gloss: number;
  /** −1 for a solid face, else the glass style of a lens's front. */
  glass: number;
  /** −1, or the index of the face this decal lies on (drawn right after it). */
  parent: number;
  center: V3;
  /** Caps: the profile's v axis and half its height, for the top-to-bottom sheen; glass: its radius. */
  axis: V3;
  half: number;
  bias: number;
};

export type Camera = { name: string; spec: string; accent: V3; look: number; faces: Face[] };

function roundedRect(w: number, h: number, r: number, seg = 4): V2[] {
  const cx = w / 2 - r;
  const cy = h / 2 - r;
  const centers: V2[] = [
    [cx, -cy],
    [cx, cy],
    [-cx, cy],
    [-cx, -cy],
  ];
  const pts: V2[] = [];
  for (let k = 0; k < 4; k++) {
    for (let s = 0; s <= seg; s++) {
      const a = ((k - 1 + s / seg) * Math.PI) / 2;
      pts.push([centers[k][0] + Math.cos(a) * r, centers[k][1] + Math.sin(a) * r]);
    }
  }
  return pts;
}

function circle(r: number, seg = 28): V2[] {
  const pts: V2[] = [];
  for (let i = 0; i < seg; i++) {
    const a = (i / seg) * 2 * Math.PI;
    pts.push([Math.cos(a) * r, Math.sin(a) * r]);
  }
  return pts;
}

/** Turns parts into faces. A decal keeps only its front, tied to the front of the part it names. */
function buildFaces(parts: Part[]): Face[] {
  const faces: Face[] = [];
  const frontOf: number[] = [];
  for (const part of parts) {
    const u = part.u ?? [1, 0, 0];
    const v = part.v ?? [0, 1, 0];
    const n = part.n ?? [0, 0, 1];
    const bias = part.bias ?? 0;
    const p = part.profile;
    const m = p.length;
    const point = (q: V2, z: number): V3 => add(add(add(part.origin, mul(u, q[0])), mul(v, q[1])), mul(n, z));
    const hz = part.depth / 2;
    const kind = part.kind ?? { solid: true };
    const glassStyle = 'glass' in kind ? kind.glass : -1;
    const parent = 'decal' in kind ? frontOf[kind.decal] : -1;
    const ys = p.map((q) => q[1]);
    const half = (Math.max(...ys) - Math.min(...ys)) / 2;
    if (parent < 0) {
      // Edge normals, then the smoothed normal at each profile point.
      const edge: V3[] = [];
      for (let k = 0; k < m; k++) {
        const a = p[k];
        const b = p[(k + 1) % m];
        edge.push(normalize(sub(mul(u, b[1] - a[1]), mul(v, b[0] - a[0]))));
      }
      for (let k = 0; k < m; k++) {
        const k1 = (k + 1) % m;
        const a = normalize(add(edge[(k + m - 1) % m], edge[k]));
        const b = normalize(add(edge[k], edge[k1]));
        const color = part.stripes && k % 2 === 1 ? mul(part.color, 0.62) : part.color;
        const quad = [point(p[k], -hz), point(p[k1], -hz), point(p[k1], hz), point(p[k], hz)];
        faces.push({
          pts: quad,
          normal: edge[k],
          n0: a,
          n1: b,
          color,
          gloss: part.gloss,
          glass: -1,
          parent: -1,
          center: mul(add(add(quad[0], quad[1]), add(quad[2], quad[3])), 0.25),
          axis: [0, 1, 0],
          half: 0,
          bias,
        });
      }
      faces.push({
        pts: [...p].reverse().map((q) => point(q, -hz)),
        normal: mul(n, -1),
        n0: null,
        n1: null,
        color: part.color,
        gloss: part.gloss,
        glass: -1,
        parent: -1,
        center: sub(part.origin, mul(n, hz)),
        axis: v,
        half,
        bias,
      });
    }
    frontOf.push(faces.length);
    const r = Math.max(...p.map((q) => Math.hypot(q[0], q[1])));
    faces.push({
      pts: p.map((q) => point(q, hz)),
      normal: n,
      n0: null,
      n1: null,
      color: part.color,
      gloss: part.gloss,
      glass: glassStyle,
      parent,
      center: add(part.origin, mul(n, hz)),
      axis: v,
      half: glassStyle >= 0 ? r : half,
      bias,
    });
  }
  return faces;
}

// MARK: The five cameras

/** A short cylinder standing on a top plate. */
function knob(r: number, h: number, x: number, y: number, z: number, color: V3, gloss: number): Part {
  return { profile: circle(r, 18), depth: h, origin: [x, y, z], color, gloss, u: [1, 0, 0], v: [0, 0, -1], n: [0, 1, 0] };
}

function decal(profile: V2[], x: number, y: number, z: number, color: V3, on: number, gloss = 0.6): Part {
  return { profile, depth: 0, origin: [x, y, z], color, gloss, kind: { decal: on } };
}

const silverA: V3 = [0.78, 0.79, 0.82];
const cream: V3 = [0.93, 0.89, 0.8];

export const CAMERAS: Camera[] = [
  // Orange top plate on a plum body, a big pearl lens.
  {
    name: 'OKA 35',
    spec: '35MM · F/2.8 · WARM 400',
    accent: [0.85, 0.38, 0.14],
    look: 0,
    faces: buildFaces([
      { profile: roundedRect(172, 78, 16), depth: 62, origin: [0, -16, 0], color: [0.2, 0.15, 0.23], gloss: 0.3 },
      { profile: roundedRect(172, 34, 12), depth: 64, origin: [0, 40, 0], color: [0.78, 0.33, 0.12], gloss: 0.85 },
      { profile: roundedRect(70, 26, 9), depth: 48, origin: [0, 68, -4], color: [0.72, 0.29, 0.1], gloss: 0.85 },
      knob(9, 8, 56, 60, 6, [0.84, 0.38, 0.15], 0.9),
      knob(13, 6, -54, 59, 4, [0.16, 0.13, 0.17], 0.5),
      { profile: circle(42), depth: 26, origin: [0, -14, 44], color: [0.1, 0.1, 0.11], gloss: 0.5, stripes: true, bias: 6 },
      { profile: circle(31), depth: 6, origin: [0, -14, 59], color: [0.08, 0.08, 0.09], gloss: 0.4, kind: { glass: 0 }, bias: 8 },
      decal(circle(5.5, 14), 62, 4, 31, [0.8, 0.12, 0.12], 0),
      decal(roundedRect(28, 12, 3), 0, 68, 20, [0.05, 0.05, 0.07], 2, 1),
    ]),
  },
  // A rangefinder: cognac leather under a black top plate, silver trim.
  {
    name: 'RF-2',
    spec: 'RANGEFINDER · F/2 · MONO',
    accent: [0.62, 0.36, 0.2],
    look: 1,
    faces: buildFaces([
      { profile: roundedRect(182, 70, 12), depth: 52, origin: [0, -14, 0], color: [0.46, 0.22, 0.12], gloss: 0.12 },
      { profile: roundedRect(182, 36, 12), depth: 54, origin: [0, 39, 0], color: [0.13, 0.13, 0.14], gloss: 0.95 },
      knob(8, 8, 64, 61, 4, silverA, 1),
      knob(14, 6, -52, 60, 2, [0.15, 0.15, 0.16], 0.7),
      { profile: circle(38), depth: 5, origin: [0, -10, 28], color: silverA, gloss: 1 },
      { profile: circle(35), depth: 30, origin: [0, -10, 44], color: [0.09, 0.09, 0.1], gloss: 0.6, stripes: true, bias: 6 },
      { profile: circle(25), depth: 4, origin: [0, -10, 60], color: [0.06, 0.06, 0.07], gloss: 0.4, kind: { glass: 1 }, bias: 8 },
      decal(roundedRect(32, 14, 3), -54, 40, 27, [0.12, 0.16, 0.24], 1, 1),
      decal(roundedRect(18, 14, 3), 44, 40, 27, [0.12, 0.16, 0.24], 1, 1),
    ]),
  },
  // A red plastic toy camera with a cream flash and lens.
  {
    name: 'PIP',
    spec: 'TOY · PLASTIC LENS · RED 200',
    accent: [0.88, 0.2, 0.16],
    look: 2,
    faces: buildFaces([
      { profile: roundedRect(158, 98, 28), depth: 60, origin: [0, -6, 0], color: [0.8, 0.15, 0.12], gloss: 0.7 },
      { profile: roundedRect(56, 24, 7), depth: 36, origin: [40, 54, -2], color: cream, gloss: 0.5 },
      knob(8, 8, -46, 46, 4, cream, 0.6),
      { profile: circle(40), depth: 18, origin: [0, -12, 39], color: cream, gloss: 0.6, bias: 6 },
      { profile: circle(26), depth: 5, origin: [0, -12, 50], color: [0.06, 0.08, 0.08], gloss: 0.4, kind: { glass: 2 }, bias: 8 },
      decal(roundedRect(44, 14, 4), 40, 54, 16, [0.98, 0.97, 0.92], 1, 1),
      decal(roundedRect(24, 16, 4), -52, 24, 30, [0.07, 0.07, 0.08], 0, 1),
      decal(roundedRect(150, 7, 3.4), 0, -44, 30, [0.97, 0.94, 0.86], 0),
    ]),
  },
  // A boxy olive medium-format camera with a waist-level finder.
  {
    name: 'MOSS 6',
    spec: '6×6 · F/3.5 · GREEN FADE',
    accent: [0.45, 0.56, 0.3],
    look: 3,
    faces: buildFaces([
      { profile: roundedRect(148, 108, 12), depth: 88, origin: [0, -10, 0], color: [0.3, 0.36, 0.22], gloss: 0.2 },
      { profile: roundedRect(148, 16, 6), depth: 90, origin: [0, 52, 0], color: [0.8, 0.81, 0.83], gloss: 1 },
      { profile: roundedRect(96, 22, 6), depth: 70, origin: [0, 68, -6], color: [0.12, 0.12, 0.13], gloss: 0.7 },
      { profile: circle(44), depth: 30, origin: [0, -14, 59], color: [0.09, 0.09, 0.1], gloss: 0.6, stripes: true, bias: 6 },
      { profile: circle(32), depth: 5, origin: [0, -14, 76], color: [0.06, 0.06, 0.07], gloss: 0.4, kind: { glass: 1 }, bias: 8 },
      decal(roundedRect(40, 10, 3), -44, 30, 44, [0.8, 0.81, 0.83], 0, 1),
    ]),
  },
  // A silver half-frame compact with a blue band.
  {
    name: 'HALF',
    spec: 'HALF FRAME · F/3.2 · BLUE HOUR',
    accent: [0.3, 0.46, 0.85],
    look: 4,
    faces: buildFaces([
      { profile: roundedRect(170, 86, 18), depth: 44, origin: [0, -4, 0], color: [0.76, 0.77, 0.8], gloss: 1 },
      knob(7, 6, 60, 42, 0, [0.76, 0.77, 0.8], 1),
      { profile: circle(31), depth: 22, origin: [-34, -8, 33], color: [0.76, 0.77, 0.8], gloss: 1, stripes: true, bias: 6 },
      { profile: circle(21), depth: 4, origin: [-34, -8, 45], color: [0.06, 0.06, 0.07], gloss: 0.4, kind: { glass: 1 }, bias: 8 },
      decal(roundedRect(164, 34, 4), 0, -12, 22, [0.17, 0.27, 0.5], 0, 0.15),
      decal(roundedRect(24, 14, 3), 52, 22, 22, [0.06, 0.07, 0.1], 0, 1),
    ]),
  },
];

// MARK: Photos

/** rect [x, y, w, h, colour], grad [x, y, w, h, top, bottom] (vertical), circle [x, y, r, colour], poly [points, colour]. */
export type Op =
  | { t: 'rect'; x: number; y: number; w: number; h: number; c: V3 }
  | { t: 'grad'; x: number; y: number; w: number; h: number; c: V3; c2: V3 }
  | { t: 'circle'; x: number; y: number; r: number; c: V3 }
  | { t: 'poly'; pts: number[]; c: V3 };

const rect = (x: number, y: number, w: number, h: number, c: V3): Op => ({ t: 'rect', x, y, w, h, c });
const grad = (x: number, y: number, w: number, h: number, c: V3, c2: V3): Op => ({ t: 'grad', x, y, w, h, c, c2 });
const circ = (x: number, y: number, r: number, c: V3): Op => ({ t: 'circle', x, y, r, c });
const poly = (pts: number[], c: V3): Op => ({ t: 'poly', pts, c });
const GOLD: V3 = [1, 0.82, 0.4];
const PANE: V3 = [0.3, 0.36, 0.46];

/** Eight little scenes in unit coordinates (0…1, y down). */
export const SCENES: Op[][] = [
  // Red flowers on dark leaves.
  [
    grad(0, 0, 1, 1, [0.11, 0.24, 0.13], [0.04, 0.11, 0.06]),
    poly([0.0, 0.55, 0.35, 0.42, 0.5, 0.6, 0.2, 0.72], [0.16, 0.36, 0.18]),
    poly([0.55, 0.95, 0.75, 0.5, 1.0, 0.45, 0.85, 0.8], [0.14, 0.32, 0.16]),
    circ(0.3, 0.34, 0.16, [0.86, 0.16, 0.12]),
    circ(0.66, 0.26, 0.13, [0.92, 0.22, 0.14]),
    circ(0.52, 0.64, 0.18, [0.8, 0.12, 0.1]),
    circ(0.84, 0.72, 0.11, [0.9, 0.28, 0.16]),
    circ(0.3, 0.34, 0.04, [0.98, 0.8, 0.3]),
    circ(0.66, 0.26, 0.035, [0.98, 0.8, 0.3]),
    circ(0.52, 0.64, 0.045, [0.98, 0.8, 0.3]),
  ],
  // A white building against a blue sky.
  [
    grad(0, 0, 1, 1, [0.28, 0.52, 0.9], [0.72, 0.85, 0.98]),
    poly([0.22, 1, 0.22, 0.36, 0.56, 0.18, 0.56, 1], [0.95, 0.95, 0.96]),
    poly([0.56, 1, 0.56, 0.18, 0.8, 0.32, 0.8, 1], [0.66, 0.7, 0.78]),
    rect(0.3, 0.48, 0.08, 0.08, PANE),
    rect(0.42, 0.44, 0.08, 0.08, PANE),
    rect(0.3, 0.68, 0.08, 0.08, PANE),
    rect(0.42, 0.66, 0.08, 0.08, PANE),
  ],
  // A portrait in silhouette.
  [
    grad(0, 0, 1, 1, [0.64, 0.62, 0.6], [0.36, 0.35, 0.34]),
    circ(0.5, 1.02, 0.36, [0.12, 0.12, 0.13]),
    circ(0.5, 0.46, 0.16, [0.14, 0.13, 0.13]),
    circ(0.44, 0.4, 0.05, [0.3, 0.28, 0.27]),
  ],
  // A sunset over the sea.
  [
    grad(0, 0, 1, 0.66, [0.98, 0.68, 0.38], [0.94, 0.42, 0.48]),
    circ(0.5, 0.6, 0.15, [1.0, 0.9, 0.62]),
    rect(0, 0.64, 1, 0.36, [0.18, 0.2, 0.34]),
    rect(0.38, 0.7, 0.24, 0.025, [1.0, 0.78, 0.52]),
    rect(0.43, 0.78, 0.14, 0.02, [0.96, 0.66, 0.5]),
    rect(0.46, 0.86, 0.08, 0.02, [0.9, 0.56, 0.5]),
  ],
  // Mountains with snow.
  [
    grad(0, 0, 1, 1, [0.58, 0.75, 0.92], [0.9, 0.93, 0.95]),
    poly([-0.05, 0.85, 0.34, 0.3, 0.72, 0.85], [0.44, 0.52, 0.62]),
    poly([0.28, 1, 0.68, 0.4, 1.08, 1], [0.24, 0.3, 0.37]),
    poly([0.68, 0.4, 0.59, 0.53, 0.66, 0.5, 0.7, 0.55, 0.77, 0.5], [0.97, 0.97, 0.98]),
    rect(0, 0.86, 1, 0.14, [0.2, 0.27, 0.22]),
  ],
  // A potted plant on a warm wall.
  [
    grad(0, 0, 1, 1, [0.96, 0.9, 0.76], [0.88, 0.78, 0.6]),
    poly([0.5, 0.74, 0.18, 0.4, 0.3, 0.2, 0.52, 0.66], [0.22, 0.5, 0.3]),
    poly([0.5, 0.74, 0.86, 0.34, 0.74, 0.16, 0.48, 0.64], [0.3, 0.6, 0.36]),
    poly([0.5, 0.74, 0.42, 0.24, 0.56, 0.08, 0.56, 0.66], [0.18, 0.44, 0.26]),
    poly([0.34, 0.72, 0.66, 0.72, 0.62, 1.0, 0.38, 1.0], [0.78, 0.42, 0.28]),
  ],
  // A city at night.
  [
    grad(0, 0, 1, 1, [0.05, 0.06, 0.17], [0.2, 0.11, 0.28]),
    circ(0.78, 0.2, 0.08, [0.96, 0.93, 0.84]),
    rect(0.04, 0.5, 0.2, 0.5, [0.08, 0.08, 0.13]),
    rect(0.27, 0.34, 0.19, 0.66, [0.1, 0.09, 0.16]),
    rect(0.49, 0.56, 0.2, 0.44, [0.08, 0.08, 0.13]),
    rect(0.72, 0.42, 0.24, 0.58, [0.1, 0.09, 0.16]),
    rect(0.09, 0.58, 0.04, 0.04, GOLD),
    rect(0.32, 0.42, 0.04, 0.04, GOLD),
    rect(0.38, 0.56, 0.04, 0.04, GOLD),
    rect(0.55, 0.64, 0.04, 0.04, GOLD),
    rect(0.78, 0.5, 0.04, 0.04, GOLD),
    rect(0.86, 0.66, 0.04, 0.04, GOLD),
  ],
  // Dunes under a low sun.
  [
    grad(0, 0, 1, 1, [0.98, 0.87, 0.68], [0.96, 0.72, 0.54]),
    circ(0.26, 0.3, 0.09, [1.0, 0.96, 0.82]),
    poly([0, 0.62, 0.4, 0.5, 1, 0.7, 1, 1, 0, 1], [0.86, 0.58, 0.36]),
    poly([0, 0.82, 0.55, 0.68, 1, 0.86, 1, 1, 0, 1], [0.68, 0.4, 0.25]),
  ],
];

/** The film look of each camera, applied to every colour of a photo. */
export function look(c: V3, lk: number): V3 {
  'worklet';
  const l = 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2];
  let o: V3;
  if (lk === 0) o = [c[0] * 1.06 + 0.05, c[1] * 0.97 + 0.02, c[2] * 0.8];
  else if (lk === 1) {
    const m = (l - 0.5) * 1.18 + 0.5;
    o = [m * 1.02, m, m * 0.96];
  } else if (lk === 2) o = [c[0] * 1.1 + 0.06, c[1] * 0.86, c[2] * 0.84];
  else if (lk === 3) o = [c[0] * 0.84 + 0.04, c[1] * 0.96 + 0.06, c[2] * 0.86 + 0.04];
  else o = [c[0] * 0.8, c[1] * 0.92 + 0.02, c[2] * 1.08 + 0.06];
  return [Math.min(1, Math.max(0, o[0])), Math.min(1, Math.max(0, o[1])), Math.min(1, Math.max(0, o[2]))];
}

/** Where each of the eight photos rides on the ring: height (pt) and in-plane tilt (degrees). */
export const HEIGHTS = [-36, -4, -60, -22, -50, 0, -68, -28];
export const ROLLS = [-16, 10, -6, 20, -12, 6, 14, -22];

// MARK: Timeline

/** autoPlay: rest on OKA 35, flip to RF-2, rest, flip to PIP, rest, spin back to OKA 35. */
export const LOOP = 8;
/** [start, end, from, to] of each move. */
const MOVES = [
  [1.2, 2.4, 0, 1],
  [3.6, 4.8, 1, 2],
  [5.9, 7.4, 2, 0],
];
/** Each photo flips this much later than the one before it. */
export const STAGGER = 0.06;

export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = Math.min(1, Math.max(0, (x - a) / (b - a)));
  return t * t * (3 - 2 * t);
}

/** Ease in and out with a small wind-up before and overshoot after (about 4 %). */
export function easeInOutBack(x: number) {
  'worklet';
  const u = Math.min(1, Math.max(0, x));
  const c2 = 1.0 * 1.525;
  if (u < 0.5) return (Math.pow(2 * u, 2) * ((c2 + 1) * 2 * u - c2)) / 2;
  return (Math.pow(2 * u - 2, 2) * ((c2 + 1) * (u * 2 - 2) + c2) + 2) / 2;
}

export function loopP(time: number) {
  'worklet';
  let t = time % LOOP;
  if (t < 0) t += LOOP;
  let value = 0;
  for (const m of MOVES) {
    if (t < m[0]) return value;
    if (t < m[1]) return m[2] + (m[3] - m[2]) * easeInOutBack((t - m[0]) / (m[1] - m[0]));
    value = m[3];
  }
  return value;
}

/** A damped spring (period 0.55 s, damping ratio 0.72) from x0 with velocity v0 towards `to`. */
export function springAt(t: number, x0: number, v0: number, to: number) {
  'worklet';
  const w = (2 * Math.PI) / 0.55;
  const z = 0.72;
  const wd = w * Math.sqrt(1 - z * z);
  const a = x0 - to;
  const b = (v0 + z * w * a) / wd;
  return to + Math.exp(-z * w * t) * (a * Math.cos(wd * t) + b * Math.sin(wd * t));
}
