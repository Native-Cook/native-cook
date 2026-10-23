/**
 * 3 × 3 rotation helpers shared by the scene and the motion. A matrix is nine numbers, row-major,
 * and takes a vector from the core's space to the view's: v_view = M · v_core.
 * View space: +x right, +y down the screen, +z towards the viewer.
 * Every function is a worklet and uses index arithmetic only (no callbacks).
 */
export type Mat3 = number[];

export function mat3Identity(): Mat3 {
  'worklet';
  return [1, 0, 0, 0, 1, 0, 0, 0, 1];
}

/** a · b: b is applied first. */
export function mat3Multiply(a: Mat3, b: Mat3): Mat3 {
  'worklet';
  return [
    a[0] * b[0] + a[1] * b[3] + a[2] * b[6], a[0] * b[1] + a[1] * b[4] + a[2] * b[7], a[0] * b[2] + a[1] * b[5] + a[2] * b[8],
    a[3] * b[0] + a[4] * b[3] + a[5] * b[6], a[3] * b[1] + a[4] * b[4] + a[5] * b[7], a[3] * b[2] + a[4] * b[5] + a[5] * b[8],
    a[6] * b[0] + a[7] * b[3] + a[8] * b[6], a[6] * b[1] + a[7] * b[4] + a[8] * b[7], a[6] * b[2] + a[7] * b[5] + a[8] * b[8],
  ];
}

/** A turn of `angle` radians about the axis (x, y, z), which need not be unit length. */
export function mat3AxisAngle(x: number, y: number, z: number, angle: number): Mat3 {
  'worklet';
  const n = Math.sqrt(x * x + y * y + z * z);
  if (n < 1e-9 || angle === 0) return [1, 0, 0, 0, 1, 0, 0, 0, 1];
  const ax = x / n;
  const ay = y / n;
  const az = z / n;
  const c = Math.cos(angle);
  const s = Math.sin(angle);
  const t = 1 - c;
  return [
    t * ax * ax + c, t * ax * ay - s * az, t * ax * az + s * ay,
    t * ax * ay + s * az, t * ay * ay + c, t * ay * az - s * ax,
    t * ax * az - s * ay, t * ay * az + s * ax, t * az * az + c,
  ];
}

/** Gram–Schmidt on the rows, so a matrix that has been multiplied many times stays a rotation. */
export function mat3Orthonormalise(m: Mat3): Mat3 {
  'worklet';
  let ax = m[0];
  let ay = m[1];
  let az = m[2];
  let n = Math.sqrt(ax * ax + ay * ay + az * az) || 1;
  ax /= n; ay /= n; az /= n;
  let bx = m[3];
  let by = m[4];
  let bz = m[5];
  const d = ax * bx + ay * by + az * bz;
  bx -= d * ax; by -= d * ay; bz -= d * az;
  n = Math.sqrt(bx * bx + by * by + bz * bz) || 1;
  bx /= n; by /= n; bz /= n;
  return [ax, ay, az, bx, by, bz, ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx];
}
