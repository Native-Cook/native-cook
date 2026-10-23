import { BOARD } from './theme';

// A tiny camera for the sign boards: yaw about the pole, a fixed downward pitch, perspective.
// Each board is projected around its own height, so every sign shows the same sliver of top
// face, as in the pin.

export type P2 = { x: number; y: number };
const DEG = Math.PI / 180;
const SIN_P = Math.sin(BOARD.pitch * DEG);
const COS_P = Math.cos(BOARD.pitch * DEG);

/** Local board point (x along the arrow, y up, z towards the viewer at yaw 0) → screen. */
export function project(x: number, y: number, z: number, yawDeg: number, cx: number, cy: number): P2 {
  'worklet';
  const c = Math.cos(yawDeg * DEG);
  const s = Math.sin(yawDeg * DEG);
  const X = x * c + z * s;
  const Z = -x * s + z * c;
  const Y2 = y * COS_P - Z * SIN_P;
  const Z2 = Z * COS_P + y * SIN_P;
  const k = BOARD.focal / (BOARD.focal - Z2);
  return { x: cx + X * k, y: cy - Y2 * k };
}

/** How much a facet with local normal (nx, ny, nz) faces the viewer (> 0 = visible). */
export function facing(nx: number, ny: number, nz: number, yawDeg: number) {
  'worklet';
  const c = Math.cos(yawDeg * DEG);
  const s = Math.sin(yawDeg * DEG);
  const Z = -nx * s + nz * c;
  return Z * COS_P + ny * SIN_P;
}

/** World x of a facet normal after yaw (negative = turned to the light on the left). */
export function normalX(nx: number, nz: number, yawDeg: number) {
  'worklet';
  return nx * Math.cos(yawDeg * DEG) + nz * Math.sin(yawDeg * DEG);
}

/** Row-major 3×3 homography taking the rect (0,0)–(w,h) onto the quad p0 p1 p2 p3 (clockwise from top-left). */
export function rectToQuad(w: number, h: number, p0: P2, p1: P2, p2: P2, p3: P2): number[] {
  'worklet';
  const dx1 = p1.x - p2.x;
  const dx2 = p3.x - p2.x;
  const dx3 = p0.x - p1.x + p2.x - p3.x;
  const dy1 = p1.y - p2.y;
  const dy2 = p3.y - p2.y;
  const dy3 = p0.y - p1.y + p2.y - p3.y;
  let g = 0;
  let hh = 0;
  if (Math.abs(dx3) > 1e-6 || Math.abs(dy3) > 1e-6) {
    const det = dx1 * dy2 - dx2 * dy1;
    if (Math.abs(det) > 1e-9) {
      g = (dx3 * dy2 - dx2 * dy3) / det;
      hh = (dx1 * dy3 - dx3 * dy1) / det;
    }
  }
  const a = p1.x - p0.x + g * p1.x;
  const b = p3.x - p0.x + hh * p3.x;
  const d = p1.y - p0.y + g * p1.y;
  const e = p3.y - p0.y + hh * p3.y;
  return [a / w, b / h, p0.x, d / w, e / h, p0.y, g / w, hh / h, 1];
}

/** Shade a #rrggbb colour by a factor (lighter above 1). */
export function shade(hex: string, k: number) {
  const n = parseInt(hex.slice(1), 16);
  const f = (v: number) => Math.max(0, Math.min(255, Math.round(k >= 1 ? v + (255 - v) * (k - 1) : v * k)));
  const r = f((n >> 16) & 255);
  const g = f((n >> 8) & 255);
  const b = f(n & 255);
  return `#${((r << 16) | (g << 8) | b).toString(16).padStart(6, '0')}`;
}
