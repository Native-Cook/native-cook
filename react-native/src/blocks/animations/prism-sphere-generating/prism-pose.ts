import type { PrismPose } from './prism-contract';
import { mat3AxisAngle, mat3Multiply } from './prism-math';

/**
 * The shell's turn, read off the reference recording: its orientation every half second of the
 * 5 s loop, as quaternions (x, y, z, w), and how far the fan is open (in units of PI).
 * The core's +Z is the mouth of the half shell and its +Y the hinge.
 */
const KEYS = [0.29652, -0.71587, 0.24191, 0.58403, 0.21481, -0.19785, 0.51165, 0.80804, 0.28132, -0.02138, 0.83629, 0.47013, 0.49844, -0.04916, 0.86135, -0.08496, 0.61271, 0.25379, 0.69147, -0.28642, 0.66980, 0.14530, 0.61236, -0.39404, 0.69015, -0.15393, 0.37917, -0.59685, 0.86123, -0.20013, 0.43839, -0.16136, 0.73369, -0.22904, 0.62698, -0.12700, 0.74419, -0.21550, 0.63197, 0.01908];
const FANS = [1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.3500, 1.9750, 1.0000, 1.0000, 1.0000];
const COUNT = 10;
const SIDE = 1;

/** Catmull-Rom through four values; with the keys taken round the loop it is periodic and C1. */
function spline(a: number, b: number, c: number, d: number, u: number) {
  'worklet';
  return b + 0.5 * u * (c - a + u * (2 * a - 5 * b + 4 * c - d + u * (3 * (b - c) + d - a)));
}

/** A smooth, exactly periodic path through the keys: prismPose(0) equals prismPose(1). */
export function prismPose(phase: number): PrismPose {
  'worklet';
  const at = (phase - Math.floor(phase)) * COUNT;
  const index = Math.min(COUNT - 1, Math.floor(at));
  const u = at - index;
  const i0 = (index + COUNT - 1) % COUNT, i1 = index, i2 = (index + 1) % COUNT, i3 = (index + 2) % COUNT;
  const x = spline(KEYS[i0 * 4], KEYS[i1 * 4], KEYS[i2 * 4], KEYS[i3 * 4], u);
  const y = spline(KEYS[i0 * 4 + 1], KEYS[i1 * 4 + 1], KEYS[i2 * 4 + 1], KEYS[i3 * 4 + 1], u);
  const z = spline(KEYS[i0 * 4 + 2], KEYS[i1 * 4 + 2], KEYS[i2 * 4 + 2], KEYS[i3 * 4 + 2], u);
  const w = spline(KEYS[i0 * 4 + 3], KEYS[i1 * 4 + 3], KEYS[i2 * 4 + 3], KEYS[i3 * 4 + 3], u);
  const n = 1 / Math.sqrt(x * x + y * y + z * z + w * w);
  const qx = x * n, qy = y * n, qz = z * n, qw = w * n;
  const fan = Math.PI * Math.min(1.9750, Math.max(1, spline(FANS[i0], FANS[i1], FANS[i2], FANS[i3], u)));
  const turned = [
    1 - 2 * (qy * qy + qz * qz), 2 * (qx * qy - qz * qw), 2 * (qx * qz + qy * qw),
    2 * (qx * qy + qz * qw), 1 - 2 * (qx * qx + qz * qz), 2 * (qy * qz - qx * qw),
    2 * (qx * qz - qy * qw), 2 * (qy * qz + qx * qw), 1 - 2 * (qx * qx + qy * qy),
  ];
  // The fan opens from one side: one end leaf keeps its place and the other travels round to meet it.
  return { rotation: mat3Multiply(turned, mat3AxisAngle(0, 1, 0, SIDE * (fan - Math.PI) / 2)), fan };
}
