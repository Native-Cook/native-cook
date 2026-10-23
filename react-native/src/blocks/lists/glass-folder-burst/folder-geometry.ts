import { Skia } from '@shopify/react-native-skia';

/** Plain snapshots, not shared values: safe to call from a UI worklet or an offline renderer. */
export type FolderValues = {
  cx: number; cy: number; scale: number; open: number; press: number; squash: number;
};

export function folderPlacement(v: FolderValues) {
  'worklet';
  const squeeze = Math.max(-1, Math.min(1, v.squash)) * 0.06;
  const size = Math.max(0.001, v.scale) * (1 - 0.03 * v.press);
  return { x: v.cx, y: v.cy + 52 * squeeze * size, sx: size * (1 + squeeze), sy: size * (1 - squeeze) };
}

export function folderPoint(v: FolderValues, x: number, y: number) {
  'worklet';
  const p = folderPlacement(v);
  return { x: p.x + x * p.sx, y: p.y + y * p.sy };
}

export function mouthPoint(v: FolderValues) {
  'worklet';
  return folderPoint(v, 0, -34 + 12 * v.open);
}

/** Matches the shader's storedDisc coverage, for the small caustic on the floor. */
export function folderStoredLight(v: FolderValues, x: number, y: number, radius: number, glow: number) {
  'worklet';
  const p = folderPlacement(v);
  const qx = (x - p.x) / p.sx;
  const qy = (y - p.y) / p.sy;
  const rr = Math.max(0.01, radius / p.sy);
  const start = -28 - 3 * v.open;
  const u = Math.max(0, Math.min(1, (qx - start) / (-6 - start)));
  const top = -46 + 12 * v.open + 12 * u * u * (3 - 2 * u);
  const covered = Math.max(0, Math.min(1, (qy - top + rr) / (2 * rr)));
  const side = Math.max(0, Math.min(1, (Math.abs(qx) - 60) / (6 + rr)));
  const bottom = Math.max(0, Math.min(1, (qy - 52) / rr));
  const visible = Math.max(0, Math.min(1, radius / 0.1));
  return covered * covered * (3 - 2 * covered) * (1 - side * side * (3 - 2 * side))
    * (1 - bottom * bottom * (3 - 2 * bottom)) * glow * visible * visible * (3 - 2 * visible);
}

/** Includes both panels, including the small allowed spring overshoot. */
export function folderBounds(v: FolderValues) {
  'worklet';
  const p = folderPlacement(v);
  const half = 66 + Math.max(0, v.open) * 6;
  return { x: p.x - half * p.sx, y: p.y - 52 * p.sy, width: half * 2 * p.sx, height: 104 * p.sy };
}

/** Cubic smoothstep is shared with the shader's top field; no union seam at the tab. */
export function frontPanelPath(v: FolderValues) {
  'worklet';
  const p = folderPlacement(v);
  const o = v.open;
  const y = -34 + 12 * o;
  const wide = 6 * o;
  const b = Skia.PathBuilder.Make();
  b.moveTo(p.x + (-66 - wide) * p.sx, p.y + (y) * p.sy);
  b.cubicTo(p.x + (-66 - wide) * p.sx, p.y + (y - 6.6274) * p.sy,
    p.x + (-60.6274 - wide) * p.sx, p.y + (y - 12) * p.sy,
    p.x + (-54 - wide) * p.sx, p.y + (y - 12) * p.sy);
  b.lineTo(p.x + (-28 - wide * 0.5) * p.sx, p.y + (y - 12) * p.sy);
  b.cubicTo(p.x + (-20.6667 - wide / 3) * p.sx, p.y + (y - 12) * p.sy,
    p.x + (-13.3333 - wide / 6) * p.sx, p.y + y * p.sy,
    p.x - 6 * p.sx, p.y + y * p.sy);
  b.lineTo(p.x + (54 + wide) * p.sx, p.y + y * p.sy);
  b.cubicTo(p.x + (60.6274 + wide) * p.sx, p.y + y * p.sy,
    p.x + (66 + wide) * p.sx, p.y + (y + 5.3726) * p.sy,
    p.x + (66 + wide) * p.sx, p.y + (y + 12) * p.sy);
  b.lineTo(p.x + 66 * p.sx, p.y + 36 * p.sy);
  b.cubicTo(p.x + 66 * p.sx, p.y + 44.8366 * p.sy, p.x + 58.8366 * p.sx,
    p.y + 52 * p.sy, p.x + 50 * p.sx, p.y + 52 * p.sy);
  b.lineTo(p.x - 50 * p.sx, p.y + 52 * p.sy);
  b.cubicTo(p.x - 58.8366 * p.sx, p.y + 52 * p.sy, p.x - 66 * p.sx,
    p.y + 44.8366 * p.sy, p.x - 66 * p.sx, p.y + 36 * p.sy);
  b.close();
  return b.build();
}
