// Glass Aperture Camera — the street photo's depth, by hand, the same function as depthAt in
// gpu/gac-wgsl.ts (keep them in step): 0 near … 1 far, at a point of the photo (uv 0 … 1). A tap reads
// it to know what to focus on.

function smooth(a: number, b: number, x: number) {
  'worklet';
  const t = Math.max(0, Math.min(1, (x - a) / (b - a)));
  return t * t * (3 - 2 * t);
}

export function depthAt(u: number, v: number) {
  'worklet';
  const dx = (u - 0.36) * 1.5;
  const dy = v - 0.64;
  let d = 1 - Math.max(0, Math.min(1, Math.hypot(dx, dy) * 1.9)) * 0.8;
  const sky = smooth(0.2, 0.26, u) * (1 - smooth(0.56, 0.62, u + (0.5 - v) * 0.5)) * (1 - smooth(0.46, 0.52, v));
  d = Math.max(d, sky * 0.97);
  const right = smooth(0.52, 0.6, u) * (1 - smooth(0.55, 0.6, v));
  d = d + (0.55 + 0.35 * (1 - u) + 0.15 * (0.55 - v) - d) * right * 0.9;
  const van = smooth(0.48, 0.52, u) * smooth(0.565, 0.59, v) * (1 - smooth(0.89, 0.92, v));
  d = d + (0.2 - d) * van;
  d = d + (0.04 - d) * smooth(0.82, 0.98, v) * (1 - van);
  return Math.max(0, Math.min(1, d));
}
