import type { Matrix4 } from '@shopify/react-native-skia';
import { FOLDER } from './burst-contract';
import type { BurstLayout, FolderPlacement } from './burst-layout';
export function clamp01(x: number): number {
  'worklet';
  return Math.max(0, Math.min(1, x));
}
export function smooth01(x: number): number {
  'worklet';
  const t = clamp01(x);
  return t * t * (3 - 2 * t);
}
export function rowJitter(i: number): number {
  'worklet';
  return (((i * 7 + 3) % 11) / 10 - 0.5) * 52;
}
/** Every strip shares the narrow first 200 points of the jet, then peels off. */
export function rowCenterX(p: number, m: number, i: number, layout: BurstLayout, cx: number, scale: number): number {
  'worklet';
  const t = clamp01(p);
  const split = 0.45 - m * 0.05;
  const jet = Math.min(1, t / split);
  const lean = 80 * Math.min(1, layout.width / 402);
  const x = cx + lean * smooth01(jet) + rowJitter(i) * scale;
  const peel = smooth01((t - split) / (0.82 - split));
  const end = layout.slots[i].x + layout.slots[i].w / 2;
  return x + (end - x) * peel;
}
export function rowCenterY(p: number, m: number, i: number, layout: BurstLayout, cy: number, scale: number): number {
  'worklet';
  const t = clamp01(p);
  const split = 0.45 - m * 0.05;
  const start = cy + FOLDER.storeY * scale;
  const jetTop = cy + FOLDER.mouthY * scale - 200;
  const end = layout.slots[i].y + 26;
  if (t <= split)
    return start + (jetTop - start) * (t / split);
  // The first slot has only 16 pt above it: keep its rim clear of the subtitle.
  const clearance = (i === 0 ? 12 : 24 + i * 2) * (1 - m);
  const peak = end - clearance;
  if (t < 0.85)
    return jetTop + (peak - jetTop) * smooth01((t - split) / (0.85 - split));
  // The intentional rise over the slot supplies weight, followed by spring overshoot.
  return peak + clearance * smooth01((t - 0.85) / 0.15) + Math.max(0, p - 1) * 90;
}
/** Row-major T(center) · P(900) · Rz · Ry · Rx · S · T(-w/2,-26).
* Skia reduces this to [0,1,3; 4,5,7; 12,13,15], retaining the perspective divide.
* Written directly so the hot pose only allocates its output matrix and object.
*/
export function rowMatrix(cx: number, cy: number, w: number, rx: number, ry: number, rz: number, sx: number, sy: number): Matrix4 {
  'worklet';
  const ax = Math.cos(rx), bx = Math.sin(rx), ay = Math.cos(ry), by = Math.sin(ry);
  const az = Math.cos(rz), bz = Math.sin(rz);
  const r00 = az * ay * sx, r01 = (az * by * bx - bz * ax) * sy, r02 = az * by * ax + bz * bx;
  const r10 = bz * ay * sx, r11 = (bz * by * bx + az * ax) * sy, r12 = bz * by * ax - az * bx;
  const r20 = -by * sx, r21 = ay * bx * sy, r22 = ay * ax;
  const q0 = -r20 / 900, q1 = -r21 / 900, q2 = -r22 / 900;
  const a = r00 + cx * q0, b = r01 + cx * q1;
  const c = r10 + cy * q0, d = r11 + cy * q1;
  return [
    a, b, r02 + cx * q2, cx - a * w / 2 - b * 26,
    c, d, r12 + cy * q2, cy - c * w / 2 - d * 26,
    r20, r21, r22, -r20 * w / 2 - r21 * 26,
    q0, q1, q2, 1 - q0 * w / 2 - q1 * 26,
  ];
}
export type RowPose = {
  matrix: Matrix4;
  opacity: number;
  glow: number;
  face: number;
  landing: number;
};
export function rowPose(p: number, v: number, m: number, i: number, n: number, layout: BurstLayout, folder: FolderPlacement, reduced = false, fade = 1, press = 1, landing = 0): RowPose {
  'worklet';
  const slot = layout.slots[i];
  if (reduced)
    return {
      matrix: rowMatrix(slot.x + slot.w / 2, slot.y + 26, slot.w, 0, 0, 0, press, press),
      opacity: fade, glow: 0.12, face: 1, landing: 0,
    };
  const t = clamp01(p);
  const radians = Math.PI / 180;
  const turn = i === 2 ? 62 : i === 6 ? 78 : i === 4 ? -85 : -55 - (i % 4) * 7;
  const unwind = t < 0.7 ? 1 - 0.64 * smooth01((t - 0.32) / 0.38)
    : 0.36 * (1 - smooth01((t - 0.7) / 0.10));
  const flash = t < 0.20 ? 78 * (1 - smooth01(t / 0.20))
    : t < 0.38 ? 0
      : t < 0.58 ? -22 * smooth01((t - 0.38) / 0.20)
        : -22 * (1 - smooth01((t - 0.58) / 0.22));
  const fold = 1 - smooth01(t / 0.20);
  const rx = (flash + (78 * fold - flash) * m) * radians;
  const ry = (i % 2 ? -35 : 35) * (1 - smooth01((t - 0.35) / 0.45)) * radians;
  const rz = turn * unwind * radians;
  const outScale = t < 0.60 ? 0.42 + 0.22 * smooth01(t / 0.45)
    : 0.64 + 0.36 * smooth01((t - 0.60) / 0.40);
  const stretch = m * Math.min(1, Math.abs(v) / 8) * Math.sin(Math.PI * t);
  const sx = outScale * press * (1 + 0.06 * stretch);
  const sy = outScale * press * (1 - 0.08 * stretch);
  const flight = 1 - smooth01((t - 0.55) / 0.45);
  return {
    matrix: rowMatrix(rowCenterX(p, m, i, layout, folder.cx, folder.scale), rowCenterY(p, m, i, layout, folder.cy, folder.scale), slot.w, rx, ry, rz, sx, sy),
    opacity: smooth01((p - 0.015) / 0.055),
    glow: 0.12 + 0.88 * flight,
    landing,
    face: smooth01((Math.abs(Math.cos(rx) * Math.cos(ry)) - 0.12) / 0.65),
  };
}
