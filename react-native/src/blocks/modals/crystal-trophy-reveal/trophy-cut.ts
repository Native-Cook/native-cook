// A convex optical cut: seven silhouette sides, nine faces, fourteen bevels, and a moving slot cap.
import { CRYSTAL, PLINTH, projectTrophy, type TrophyFrame } from './trophy-contract';

type Plane = readonly [number, number, number, number];
export const OUTLINE: readonly (readonly [number, number])[] = [
  [0.05, 2.90], [0.76, 2.46], [0.86, 1.36], [0.305, -0.28],
  [-0.305, -0.28], [-0.86, 1.34], [-0.74, 2.52],
];

function normalized(p: Plane): number[] {
  const magnitude = Math.hypot(p[0], p[1], p[2]);
  return [p[0] / magnitude, p[1] / magnitude, p[2] / magnitude, p[3] / magnitude];
}

// The kite spans y=.60..1.40, x=±.329 at its side corners. Two upper flanks close the diamond.
// Face indices are stable: upper front, lower left/right, kite, back, lower back left/right.
const faces: Plane[] = [
  [0, 0.075, 1, 0.39], [0.25, -0.40, 1, -0.0956], [-0.25, -0.40, 1, -0.0956],
  [0, -0.176, 1, 0.0386], [0, 0.025, -1, 0.28],
  [0.13, -0.13, -1, 0.26], [-0.13, -0.13, -1, 0.26],
  [0.10, -0.10, 1, 0.145], [-0.10, -0.10, 1, 0.145],
];
const planes: Plane[] = [...faces];
for (let i = 0; i < OUTLINE.length; i++) {
  const a = OUTLINE[i];
  const b = OUTLINE[(i + 1) % OUTLINE.length];
  const dx = b[0] - a[0];
  const dy = b[1] - a[1];
  const norm = Math.hypot(dx, dy);
  const side: Plane = [-dy / norm, dx / norm, 0, (-dy * a[0] + dx * a[1]) / norm];
  planes.push(side);
}
for (let i = 0; i < OUTLINE.length; i++) {
  const a = OUTLINE[i];
  const b = OUTLINE[(i + 1) % OUTLINE.length];
  const mx = (a[0] + b[0]) / 2;
  const my = (a[1] + b[1]) / 2;
  const side = planes[faces.length + i];
  let front = faces[0];
  let back = faces[4];
  for (let j = 1; j < faces.length; j++) {
    if (j >= 4 && j <= 6) continue;
    if (faces[j][3] - faces[j][0] * mx - faces[j][1] * my < front[3] - front[0] * mx - front[1] * my) front = faces[j];
  }
  for (let j = 5; j < 7; j++) {
    if (faces[j][3] - faces[j][0] * mx - faces[j][1] * my < back[3] - back[0] * mx - back[1] * my) back = faces[j];
  }
  planes.push([side[0] + front[0] * 0.65, side[1] + front[1] * 0.65, 0.65, side[3] + front[3] * 0.65 - 0.065]);
  planes.push([side[0] + back[0] * 0.65, side[1] + back[1] * 0.65, -0.65, side[3] + back[3] * 0.65 - 0.04]);
}
/** Last plane is updated in the shader to clip the rising crystal at world y = 0. */
planes.push([0, -1, 0, 0]);
export const CRYSTAL_PLANE_COUNT = planes.length;
export const CRYSTAL_PLANES: number[] = planes.flatMap(normalized);

const base: Plane[] = [[0, 1, 0, 0], [0, -1, 0, PLINTH.height]];
for (let i = 0; i < 8; i++) {
  const angle = (i * Math.PI) / 4;
  const nx = Math.sin(angle);
  const nz = Math.cos(angle);
  base.push([nx, 0, nz, PLINTH.apothem]);
  base.push([nx, 1, nz, PLINTH.apothem - PLINTH.chamfer]);
}
export const PLINTH_PLANES: number[] = base.flatMap(normalized);
export const PLINTH_PLANE_COUNT = base.length;
export const CRYSTAL_TRAVEL = CRYSTAL.travel;

// Static mesh connectivity is used only to project the halo outline; the optical solid stays unchanged.
const hullPlanes = planes.slice(0, -1);
const hullVertices: number[][] = [];
for (let i = 0; i < hullPlanes.length; i++) {
  for (let j = i + 1; j < hullPlanes.length; j++) {
    for (let k = j + 1; k < hullPlanes.length; k++) {
      const a = hullPlanes[i], b = hullPlanes[j], c = hullPlanes[k];
      const bc = [b[1] * c[2] - b[2] * c[1], b[2] * c[0] - b[0] * c[2], b[0] * c[1] - b[1] * c[0]];
      const ca = [c[1] * a[2] - c[2] * a[1], c[2] * a[0] - c[0] * a[2], c[0] * a[1] - c[1] * a[0]];
      const ab = [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
      const det = a[0] * bc[0] + a[1] * bc[1] + a[2] * bc[2];
      if (Math.abs(det) < 1e-8) continue;
      const point = [0, 1, 2].map(axis => (bc[axis] * a[3] + ca[axis] * b[3] + ab[axis] * c[3]) / det);
      if (hullPlanes.some(pl => pl[0] * point[0] + pl[1] * point[1] + pl[2] * point[2] > pl[3] + 1e-7)) continue;
      if (hullVertices.some(v => Math.hypot(v[0] - point[0], v[1] - point[1], v[2] - point[2]) < 1e-6)) continue;
      hullVertices.push(point);
    }
  }
}
const hullEdges: number[][] = [];
for (let i = 0; i < hullVertices.length; i++) {
  for (let j = i + 1; j < hullVertices.length; j++) {
    const a = hullVertices[i], b = hullVertices[j];
    let shared = 0;
    for (const pl of hullPlanes) {
      if (Math.abs(pl[0] * a[0] + pl[1] * a[1] + pl[2] * a[2] - pl[3]) < 1e-6
        && Math.abs(pl[0] * b[0] + pl[1] * b[1] + pl[2] * b[2] - pl[3]) < 1e-6) shared++;
    }
    if (shared >= 2) hullEdges.push([i, j]);
  }
}

export const CRYSTAL_RIM_SEGMENTS = 32;

/** Clip at the marble, project, then form the silhouette. Index loops only on the UI thread. */
export function crystalRim(yaw: number, rise: number, frame: TrophyFrame): number[] {
  'worklet';
  const drop = (1 - rise) * CRYSTAL.travel;
  const points: number[][] = [];
  for (let i = 0; i < hullVertices.length; i++) {
    const v = hullVertices[i];
    if (v[1] >= drop) {
      const p = projectTrophy(v[0], v[1] - drop, v[2], yaw, frame);
      points.push([p.x, p.y]);
    }
  }
  for (let i = 0; i < hullEdges.length; i++) {
    const a = hullVertices[hullEdges[i][0]], b = hullVertices[hullEdges[i][1]];
    if ((a[1] - drop) * (b[1] - drop) < 0) {
      const t = (drop - a[1]) / (b[1] - a[1]);
      const p = projectTrophy(a[0] + (b[0] - a[0]) * t, 0, a[2] + (b[2] - a[2]) * t, yaw, frame);
      points.push([p.x, p.y]);
    }
  }
  // Insertion sort avoids a comparator callback in the compiled worklet.
  for (let i = 1; i < points.length; i++) {
    const p = points[i];
    let j = i - 1;
    for (; j >= 0; j--) {
      if (points[j][0] < p[0] || (points[j][0] === p[0] && points[j][1] <= p[1])) break;
      points[j + 1] = points[j];
    }
    points[j + 1] = p;
  }
  const hull: number[][] = [];
  for (let side = 0; side < 2; side++) {
    const start = hull.length;
    for (let i = 0; i < points.length; i++) {
      const p = points[side === 0 ? i : points.length - 1 - i];
      for (; hull.length >= start + 2;) {
        const a = hull[hull.length - 2], b = hull[hull.length - 1];
        if ((b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]) > 1e-7) break;
        hull.pop();
      }
      hull.push(p);
    }
    hull.pop();
  }
  const result: number[] = [];
  for (let i = 0; i < CRYSTAL_RIM_SEGMENTS; i++) {
    if (i < hull.length) {
      const a = hull[i], b = hull[(i + 1) % hull.length];
      result.push(a[0], a[1], b[0], b[1]);
    } else result.push(-10000, -10000, -10000, -10000);
  }
  return result;
}


export const CRYSTAL_SPARKLE_COUNT = 8;
/** Pin-light projections and narrow yaw lobes are uniform work, not fragment work. */
export function crystalSparkles(yaw: number, rise: number, flash: number, frame: TrophyFrame): number[] {
  'worklet';
  const result: number[] = [];
  const drop = (1 - rise) * CRYSTAL.travel;
  for (let i = 0; i < CRYSTAL_SPARKLE_COUNT; i++) {
    let x = 0.05, y = 2.90, z = 0.17, phase = 0;
    if (i === 1) { x = -0.74; y = 2.52; z = 0.15; phase = -0.045; }
    if (i === 2) { x = 0.86; y = 1.36; z = 0.04; phase = 0.035; }
    if (i === 3) { x = -0.86; y = 1.34; z = 0.04; phase = 0.075; }
    if (i === 4) { x = 0.329; y = 0.967; z = 0.209; phase = -0.025; }
    if (i === 5) { x = -0.329; y = 0.967; z = 0.209; phase = 0.015; }
    if (i === 6) { x = 0; y = 1.40; z = 0.285; phase = -0.075; }
    if (i === 7) { x = 0; y = 0.60; z = 0.1442; phase = 0.055; }
    // Using sin/cos of yaw keeps arbitrarily many full spins periodic without a phase subtraction.
    const alignment = Math.cos(yaw) * Math.cos(phase) + Math.sin(yaw) * Math.sin(phase);
    let gain = Math.pow(Math.max(0, alignment), i === 0 ? 95 : 2600) * (i === 0 ? 1.1 : 3.0);
    if (i === 0) gain += flash * 1.8;
    const p = projectTrophy(x, y - drop, z, yaw, frame);
    const size = (i === 0 ? 17 * (1 + flash) : 5.2) * frame.unit / 108;
    result.push(p.x, p.y, y - drop > 0.01 ? gain : 0, size);
  }
  return result;
}
