/*
 Jelly Cube Squish — the cube's geometry, soft-body deformation and camera.

 World: x right, y away, z up; the plate is the plane z = 0 and the cube stands on it, half size
 HALF, edges rounded by ROUND (each face is an N × N grid pushed onto a rounded box). The camera
 turns YAW about z and looks down ELEV, so the front (−y), left (−x) and top faces show.

 A deformation is a few numbers, applied to every rest point p (h = z / 2·HALF, 0 at the plate):
   squash sq   z shrinks by sq, the sides bulge to keep the volume (most at mid height);
   lean lx, ly the top shears sideways by h²·(lx, ly) — the cube's wobble modes;
   dent        a Gaussian push (radius DENT_R) at point (px, py, pz) along the face normal;
   stretch     (sx, sy, sz) a pull at the same point, falling off over GRAB_R and with height.
 */

export const HALF = 72;
export const ROUND = 20;
export const N = 12;
export const DENT_R = 34;
export const GRAB_R = 92;
const YAW = (38 * Math.PI) / 180;
const ELEV = (24 * Math.PI) / 180;
const CY = Math.cos(YAW);
const SY = Math.sin(YAW);
const CE = Math.cos(ELEV);
const SE = Math.sin(ELEV);
const CAM_D = 900;
const ZOOM = 1.45;
/** The camera looks at this height (the cube's middle). */
const LOOK_Z = HALF;

export type V3 = [number, number, number];

export type Deform = {
  sq: number;
  lx: number;
  ly: number;
  dent: number;
  /** Dent / grab point and the dent's direction (unit, into the cube). */
  px: number;
  py: number;
  pz: number;
  nx: number;
  ny: number;
  nz: number;
  sx: number;
  sy: number;
  sz: number;
};

export const REST: Deform = { sq: 0, lx: 0, ly: 0, dent: 0, px: 0, py: -HALF, pz: HALF, nx: 0, ny: 1, nz: 0, sx: 0, sy: 0, sz: 0 };

export function clamp(v: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max(v, a), b);
}

/** Screen point and scale of a world point, the cube's foot centred at (cx, cy). */
export function project(cx: number, cy: number, p: V3): [number, number, number] {
  'worklet';
  const x1 = p[0] * CY - p[1] * SY;
  const y1 = p[0] * SY + p[1] * CY;
  const z = p[2] - LOOK_Z;
  const yc = y1 * SE + z * CE;
  const s = (ZOOM * CAM_D) / (CAM_D + y1 * CE - z * SE);
  return [cx + x1 * s, cy - yc * s, s];
}

/** Unit vector from the cube toward the camera, in world space. */
export const TO_EYE: V3 = [-SY * CE, -CY * CE, SE];
/** Camera right and up in world space (for mapping a drag to a pull). */
export const RIGHT: V3 = [CY, -SY, 0];
export const UP: V3 = [SY * SE, CY * SE, CE];
/** Unit vector toward the light (up, left, front). */
export const TO_LIGHT: V3 = [-0.45, -0.55, 0.7];

/** The six faces: origin corner, u and v axes (each 2·HALF long), outward normal. */
const FACES: number[][] = [
  // front (−y), left (−x), top (+z), back (+y), right (+x), bottom (−z)
  [-1, -1, 0, 1, 0, 0, 0, 0, 1, 0, -1, 0],
  [-1, 1, 0, 0, -1, 0, 0, 0, 1, -1, 0, 0],
  [-1, -1, 2, 1, 0, 0, 0, 1, 0, 0, 0, 1],
  [1, 1, 0, -1, 0, 0, 0, 0, 1, 0, 1, 0],
  [1, -1, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0],
  [-1, 1, 0, 1, 0, 0, 0, -1, 0, 0, 0, -1],
];
export const FACE_COUNT = 6;

/** Rest points of every face grid (rounded box), face-major, row-major: [x, y, z] each. */
export function restPoints(): number[] {
  const out: number[] = [];
  const inner = HALF - ROUND;
  for (let f = 0; f < FACE_COUNT; f += 1) {
    const F = FACES[f];
    for (let j = 0; j <= N; j += 1) {
      for (let i = 0; i <= N; i += 1) {
        const a = (i / N) * 2;
        const b = (j / N) * 2;
        const x = (F[0] + F[3] * a + F[6] * b) * HALF;
        const y = (F[1] + F[4] * a + F[7] * b) * HALF;
        const z = (F[2] + F[5] * a + F[8] * b) * HALF;
        // Push onto the rounded box: nearest point of the inner box plus ROUND outward.
        const cx = clamp(x, -inner, inner);
        const cy = clamp(y, -inner, inner);
        const cz = clamp(z, ROUND, 2 * HALF - ROUND);
        const dx = x - cx;
        const dy = y - cy;
        const dz = z - cz;
        const l = Math.hypot(dx, dy, dz) || 1;
        out.push(cx + (dx / l) * ROUND, cy + (dy / l) * ROUND, Math.max(0, cz + (dz / l) * ROUND));
      }
    }
  }
  return out;
}

/** Triangle indices for one face's grid (offset by face). */
export function faceIndices(): number[] {
  const idx: number[] = [];
  for (let j = 0; j < N; j += 1) {
    for (let i = 0; i < N; i += 1) {
      const a = j * (N + 1) + i;
      idx.push(a, a + 1, a + N + 1, a + 1, a + N + 2, a + N + 1);
    }
  }
  return idx;
}

/** A deformed point. */
export function deform(d: Deform, x: number, y: number, z: number): V3 {
  'worklet';
  const h = clamp(z / (2 * HALF), 0, 1);
  const sq = clamp(d.sq, -0.25, 0.4);
  const bulge = (1 / Math.sqrt(1 - sq) - 1) * (0.55 + 0.45 * Math.sin(Math.PI * h));
  let X = x * (1 + bulge);
  let Y = y * (1 + bulge);
  let Z = z * (1 - sq);
  X += d.lx * h * h * HALF * 0.01;
  Y += d.ly * h * h * HALF * 0.01;
  const ex = x - d.px;
  const ey = y - d.py;
  const ez = z - d.pz;
  const r2 = ex * ex + ey * ey + ez * ez;
  if (d.dent !== 0) {
    const w = Math.exp(-r2 / (2 * DENT_R * DENT_R));
    // The dent, and a soft rim pushed out around it.
    const rim = Math.exp(-r2 / (2 * (DENT_R * 1.9) ** 2)) - w;
    const k = d.dent * (w - 0.35 * rim);
    X += d.nx * k;
    Y += d.ny * k;
    Z += d.nz * k * (z > 2 ? 1 : 0);
  }
  if (d.sx !== 0 || d.sy !== 0 || d.sz !== 0) {
    const w = Math.exp(-r2 / (2 * GRAB_R * GRAB_R)) * (0.25 + 0.75 * h);
    X += d.sx * w;
    Y += d.sy * w;
    Z += d.sz * w;
  }
  return [X, Y, Math.max(0, Z)];
}
