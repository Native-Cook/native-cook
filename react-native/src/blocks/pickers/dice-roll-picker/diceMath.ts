/*
 Dice Roll Picker — 3D math and the analytic tumble. World: x right, y away from the player across
 the table, z up; the felt is the plane z = 0, the table's centre the origin, units in points.
 A die is a rounded cube (edge EDGE, corner radius ROUND): an inner cube of half size HALF − ROUND
 dilated by ROUND. Its orientation is a row-major 3×3 matrix whose columns are its local axes.

 Faces: +Z 1, −Z 6, +X 3, −X 4, +Y 2, −Y 5 (opposite faces add up to 7).

 A throw is closed-form (no simulation): the path runs through a first landing, a bounce off the
 far rim and on to the resting spot; the height is three parabolic hops; the orientation is
 R(s) = Rw(w, 2πk·(1 − e)) · R1 · Rl(a, φ0·(1 − e)), e = ease-out of the time, where Rl(a, φ0) takes
 the end orientation R1 back to the start R0, and Rw spins k whole turns about the axis the die
 rolls on. Whole turns are the identity, so s = 0 is R0 and s = end is R1 exactly. While it turns,
 the die's centre rides on its lowest point, so it tips over edges as it rolls.
 */

export type Mat = number[];
export type V3 = [number, number, number];

export const EDGE = 66;
export const HALF = EDGE / 2;
export const ROUND = EDGE * 0.15;
export const INNER = HALF - ROUND;

/** Camera: elevation above the table (degrees) and distance (pt, also the focal length). */
const ELEV = (60 * Math.PI) / 180;
const SIN_E = Math.sin(ELEV);
const COS_E = Math.cos(ELEV);
export const CAM_D = 1100;
/** Unit vector from a point to the light (up, left, far). */
export const TO_LIGHT: V3 = [-0.33, 0.42, 0.845];

export const TABLE = { x0: -170, x1: 170, y0: -130, y1: 175, rim: 16, wall: 16 };

export function clamp(v: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max(v, a), b);
}

/** A seeded value in [0, 1). */
export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

export function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function mul(a: Mat, b: Mat): Mat {
  'worklet';
  const r = [0, 0, 0, 0, 0, 0, 0, 0, 0];
  for (let i = 0; i < 3; i += 1) for (let j = 0; j < 3; j += 1) r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
  return r;
}

export function transpose(a: Mat): Mat {
  'worklet';
  return [a[0], a[3], a[6], a[1], a[4], a[7], a[2], a[5], a[8]];
}

/** Rotation about a unit axis by an angle (radians). */
export function axisAngle(ax: V3, ang: number): Mat {
  'worklet';
  const c = Math.cos(ang);
  const s = Math.sin(ang);
  const t = 1 - c;
  const [x, y, z] = ax;
  return [t * x * x + c, t * x * y - s * z, t * x * z + s * y, t * x * y + s * z, t * y * y + c, t * y * z - s * x, t * x * z - s * y, t * y * z + s * x, t * z * z + c];
}

/** The axis and angle of a rotation matrix (robust near 0 and π). */
export function toAxisAngle(m: Mat): [V3, number] {
  'worklet';
  const cos = clamp((m[0] + m[4] + m[8] - 1) / 2, -1, 1);
  const ang = Math.acos(cos);
  if (ang < 1e-4) return [[0, 0, 1], 0];
  if (Math.PI - ang < 1e-3) {
    const xx = Math.sqrt(Math.max(0, (m[0] + 1) / 2));
    const yy = Math.sqrt(Math.max(0, (m[4] + 1) / 2));
    const zz = Math.sqrt(Math.max(0, (m[8] + 1) / 2));
    if (xx >= yy && xx >= zz) return [[xx, m[1] / (2 * xx), m[2] / (2 * xx)], ang];
    if (yy >= zz) return [[m[1] / (2 * yy), yy, m[5] / (2 * yy)], ang];
    return [[m[2] / (2 * zz), m[5] / (2 * zz), zz], ang];
  }
  const s = 2 * Math.sin(ang);
  return [[(m[7] - m[5]) / s, (m[2] - m[6]) / s, (m[3] - m[1]) / s], ang];
}

export function rotZ(a: number): Mat {
  'worklet';
  return axisAngle([0, 0, 1], a);
}

/** Orientation showing `face` on top, turned by `yaw` (radians) about the vertical. */
export function faceUp(face: number, yaw: number): Mat {
  'worklet';
  const H = Math.PI / 2;
  let base: Mat;
  if (face === 1) base = [1, 0, 0, 0, 1, 0, 0, 0, 1];
  else if (face === 6) base = axisAngle([1, 0, 0], Math.PI);
  else if (face === 3) base = axisAngle([0, 1, 0], -H);
  else if (face === 4) base = axisAngle([0, 1, 0], H);
  else if (face === 2) base = axisAngle([1, 0, 0], H);
  else base = axisAngle([1, 0, 0], -H);
  return mul(rotZ(yaw), base);
}

export function apply(m: Mat, v: V3): V3 {
  'worklet';
  return [m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2], m[6] * v[0] + m[7] * v[1] + m[8] * v[2]];
}

/** The face on top of an orientation. */
export function topFace(m: Mat) {
  'worklet';
  // Column k is local axis k in world; its z is m[6 + k].
  const faces = [3, 2, 1];
  const opposite = [4, 5, 6];
  let best = 0;
  let face = 1;
  for (let k = 0; k < 3; k += 1) {
    const z = m[6 + k];
    if (Math.abs(z) > best) {
      best = Math.abs(z);
      face = z > 0 ? faces[k] : opposite[k];
    }
  }
  return face;
}

/** The orientation nearest to `m` with a face flat on the table. */
export function snapFlat(m: Mat): Mat {
  'worklet';
  let k = 0;
  for (let i = 1; i < 3; i += 1) if (Math.abs(m[6 + i]) > Math.abs(m[6 + k])) k = i;
  const s = m[6 + k] > 0 ? 1 : -1;
  const n: V3 = [m[k] * s, m[3 + k] * s, m[6 + k] * s];
  // Axis n × z, angle between n and z.
  const ax: V3 = [n[1], -n[0], 0];
  const len = Math.hypot(ax[0], ax[1]);
  if (len < 1e-5) return m;
  const ang = Math.acos(clamp(n[2], -1, 1));
  return mul(axisAngle([ax[0] / len, ax[1] / len, 0], ang), m);
}

/** Height of the centre above the felt when the die rests on its lowest point. */
export function support(m: Mat) {
  'worklet';
  // Lowest corner of the inner cube: −Σ|column z| · INNER.
  return (Math.abs(m[6]) + Math.abs(m[7]) + Math.abs(m[8])) * INNER + ROUND;
}

/** Screen point of a world point; s = the perspective scale there. */
export function project(cx: number, cy: number, p: V3): [number, number, number] {
  'worklet';
  const yc = p[1] * SIN_E + p[2] * COS_E;
  const dc = CAM_D + p[1] * COS_E - p[2] * SIN_E;
  const s = CAM_D / dc;
  return [cx + p[0] * s, cy - yc * s, s];
}

/** Table-plane point under a screen point at height z (inverse of project). */
export function unproject(cx: number, cy: number, sx: number, sy: number, z: number): [number, number] {
  'worklet';
  // sy − cy = −(y sinE + z cosE)·D / (D + y cosE − z sinE); solve for y, then x.
  const k = (cy - sy) / CAM_D;
  const y = (k * (CAM_D - z * SIN_E) - z * COS_E) / (SIN_E - k * COS_E);
  const dc = CAM_D + y * COS_E - z * SIN_E;
  return [((sx - cx) * dc) / CAM_D, y];
}

/** Direction from a world point toward the camera. */
export function toCamera(p: V3): V3 {
  'worklet';
  const c: V3 = [0, -CAM_D * COS_E, CAM_D * SIN_E];
  const d: V3 = [c[0] - p[0], c[1] - p[1], c[2] - p[2]];
  const l = Math.hypot(d[0], d[1], d[2]);
  return [d[0] / l, d[1] / l, d[2] / l];
}

/** A die's pose: orientation, table position, hop height (above its support). */
export type Pose = { m: Mat; x: number; y: number; hop: number };

/**
 A throw from (R0, p0, starting hop h0) to (R1, p1), `k` whole turns, the far rim at rimY.
 Times in seconds from the release: the path ends at 1.8, the spin at 1.85, a rock until 2.2.
 */
export type Throw = { r0: Mat; r1: Mat; p0: number[]; p1: number[]; h0: number; k: number; w: V3; a: V3; phi: number; rimY: number };

export const THROW_END = 2.2;

export function makeThrow(r0: Mat, r1: Mat, p0: number[], p1: number[], h0: number, k: number, rimY: number): Throw {
  'worklet';
  const [a, phi] = toAxisAngle(mul(transpose(r1), r0));
  const dx = p1[0] - p0[0];
  const dy = rimY - p0[1];
  const l = Math.hypot(dx, dy) || 1;
  // Rolls forward toward the rim: spin axis −(z × direction), turned the other way by the decay.
  return { r0, r1, p0, p1, h0, k, w: [dy / l, -dx / l, 0], a, phi, rimY };
}

function hopArc(u: number, a: number, b: number, h0: number, peak: number) {
  'worklet';
  const x = seg(u, a, b);
  return h0 * (1 - x) + 4 * peak * x * (1 - x);
}

export function throwPose(th: Throw, s: number): Pose {
  'worklet';
  // Path: start → first landing (0.55 s) → far rim (0.85 s) → rest (1.8 s, easing out).
  // Spread sideways in flight, away from the table's centre line, so two dice don't touch.
  const side = th.p1[0] >= 0 ? 1 : -1;
  const land = [mix(th.p0[0], th.p1[0], 0.55) + side * 10, mix(th.p0[1], th.rimY, 0.7)];
  const rim = [mix(th.p0[0], th.p1[0], 0.8) + side * 20, th.rimY];
  let x: number;
  let y: number;
  if (s < 0.55) {
    const u = s / 0.55;
    x = mix(th.p0[0], land[0], u);
    y = mix(th.p0[1], land[1], u);
  } else if (s < 0.85) {
    const u = (s - 0.55) / 0.3;
    x = mix(land[0], rim[0], u);
    y = mix(land[1], rim[1], u);
  } else {
    const u = easeOut(seg(s, 0.85, 1.8));
    x = mix(rim[0], th.p1[0], u);
    y = mix(rim[1], th.p1[1], u);
  }
  let hop = 0;
  if (s < 0.55) hop = hopArc(s, 0, 0.55, th.h0, 96);
  else if (s < 1.0) hop = hopArc(s, 0.55, 1.0, 0, 34);
  else if (s < 1.28) hop = hopArc(s, 1.0, 1.28, 0, 10);
  const e = easeOut(seg(s, 0, 1.85));
  let m = mul(mul(axisAngle(th.w, 2 * Math.PI * th.k * (1 - e)), th.r1), axisAngle(th.a, th.phi * (1 - e)));
  if (s > 1.85) {
    // Rock on the face after the spin: two damped wobbles about the roll axis.
    const r = s - 1.85;
    const rock = 0.11 * Math.sin((2 * Math.PI * r) / 0.2) * Math.pow(1 - seg(r, 0, THROW_END - 1.85), 2);
    m = mul(axisAngle(th.w, rock), m);
  }
  return { m, x, y, hop };
}

/** A slide home from (R1, p1) to (R0, p0) over `dur` seconds: tips over as it turns, small hop. */
export function slidePose(r1: Mat, r0: Mat, p1: number[], p0: number[], u: number): Pose {
  'worklet';
  const e = easeInOut(clamp(u, 0, 1));
  const [b, psi] = toAxisAngle(mul(transpose(r0), r1));
  return { m: mul(r0, axisAngle(b, psi * (1 - e))), x: mix(p1[0], p0[0], e), y: mix(p1[1], p0[1], e), hop: 8 * Math.sin(Math.PI * e) };
}
