/**
 * Signed distance field of a glyph, O(n²): two separable squared Euclidean transforms of the anti-aliased
 * coverage mask give the far field, and within `BAND` texels of the outline the distance is exact (to the
 * flattened path), so the silhouette and its rim have no stair-steps or wobble. Stored as 16 bits
 * (R high byte, G low byte); bilinear filtering interpolates the pair exactly.
 */
export const SDF_SIZE = 256;
export const SDF_PAD = 16;
/** Distance range, in texels, encoded either side of the outline. */
export const SDF_RANGE = 96;
const BAND = 3;
const INF = 1e20;
function lineTransform(f: Float64Array, out: Float64Array, n: number, v: Int32Array, z: Float64Array) {
  let k = 0;
  v[0] = 0;
  z[0] = -INF;
  z[1] = INF;
  for (let q = 1; q < n; q++) {
    let d = (f[q] + q * q - (f[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k]);
    while (d <= z[k] && k > 0) {
      k--;
      d = (f[q] + q * q - (f[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k]);
    }
    k++;
    v[k] = q;
    z[k] = d;
    z[k + 1] = INF;
  }
  k = 0;
  for (let q = 0; q < n; q++) {
    while (z[k + 1] < q) k++;
    out[q] = (q - v[k]) ** 2 + f[v[k]];
  }
}
function transform(grid: Float64Array) {
  const n = SDF_SIZE,
    f = new Float64Array(n),
    out = new Float64Array(n),
    v = new Int32Array(n),
    z = new Float64Array(n + 1);
  for (let y = 0; y < n; y++) {
    for (let x = 0; x < n; x++) f[x] = grid[y * n + x];
    lineTransform(f, out, n, v, z);
    for (let x = 0; x < n; x++) grid[y * n + x] = out[x];
  }
  for (let x = 0; x < n; x++) {
    for (let y = 0; y < n; y++) f[y] = grid[y * n + x];
    lineTransform(f, out, n, v, z);
    for (let y = 0; y < n; y++) grid[y * n + x] = out[y];
  }
}
/** Unsigned distance from each texel centre to the nearest segment, up to BAND (else Infinity). */
export function exactBand(segments: number[]) {
  const n = SDF_SIZE,
    out = new Float32Array(n * n).fill(Infinity);
  for (let s = 0; s < segments.length; s += 4) {
    const ax = segments[s],
      ay = segments[s + 1],
      bx = segments[s + 2],
      by = segments[s + 3];
    const dx = bx - ax,
      dy = by - ay,
      len2 = dx * dx + dy * dy;
    const x0 = Math.max(0, Math.floor(Math.min(ax, bx) - BAND - 0.5)),
      x1 = Math.min(n - 1, Math.ceil(Math.max(ax, bx) + BAND - 0.5));
    const y0 = Math.max(0, Math.floor(Math.min(ay, by) - BAND - 0.5)),
      y1 = Math.min(n - 1, Math.ceil(Math.max(ay, by) + BAND - 0.5));
    for (let y = y0; y <= y1; y++)
      for (let x = x0; x <= x1; x++) {
        const px = x + 0.5 - ax,
          py = y + 0.5 - ay;
        const t = len2 > 0 ? Math.max(0, Math.min(1, (px * dx + py * dy) / len2)) : 0;
        const ex = px - t * dx,
          ey = py - t * dy;
        const d = Math.sqrt(ex * ex + ey * ey),
          i = y * n + x;
        if (d < out[i]) out[i] = d;
      }
  }
  return out;
}
/** `coverage`: 0..255 anti-aliased alpha of the glyph; `exact`: see exactBand. Positive distance is outside. */
export function makeDistanceField(coverage: Uint8Array, exact?: Float32Array) {
  const count = SDF_SIZE * SDF_SIZE,
    outer = new Float64Array(count),
    inner = new Float64Array(count),
    out = new Uint8Array(count * 4);
  for (let i = 0; i < count; i++) {
    const a = coverage[i] / 255;
    if (a >= 1) {
      outer[i] = 0;
      inner[i] = INF;
    } else if (a <= 0) {
      outer[i] = INF;
      inner[i] = 0;
    } else {
      const d = 0.5 - a;
      outer[i] = d > 0 ? d * d : 0;
      inner[i] = d < 0 ? d * d : 0;
    }
  }
  transform(outer);
  transform(inner);
  for (let i = 0; i < count; i++) {
    let distance = Math.sqrt(outer[i]) - Math.sqrt(inner[i]);
    const e = exact ? exact[i] : Infinity;
    // Exact near the outline; an interior edge of overlapping contours (|far field| much larger) is ignored.
    if (e < BAND && Math.abs(distance) < e + 1.5) {
      const signed = coverage[i] >= 128 ? -e : e,
        w = Math.max(0, Math.min(1, e - (BAND - 1)));
      distance = signed + (distance - signed) * w * w * (3 - 2 * w);
    }
    const v = Math.round(Math.max(0, Math.min(1, 0.5 + distance / (2 * SDF_RANGE))) * 65535);
    out[i * 4] = v >> 8;
    out[i * 4 + 1] = v & 255;
    out[i * 4 + 2] = 0;
    out[i * 4 + 3] = 255;
  }
  return out;
}
