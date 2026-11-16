import { REST_RADII } from './bvl-rest';
import { clamp, smooth, thresholdAt } from './bvl-motion';

export type Preset = 'lungs' | 'battery' | 'bottle' | 'heart';
export type FieldGrid = {
  cols: number;
  rows: number;
  width: number;
  bytes: Uint8Array;
  thresholds: number[];
  conservationError: number;
};
export const GRID = { rows: 40, tableSize: 64, top: 7, bottom: 36, radius: 2.4, soft: 0.22 } as const;

function lobe(x: number, y: number, cx: number, cy: number, rx: number, ry: number, n: number) {
  const rho = Math.pow(Math.pow(Math.abs((x - cx) / rx), n) + Math.pow(Math.abs((y - cy) / ry), n), 1 / n);
  return clamp(1 - rho * rho);
}
/** Pro: centres (163,245)/(239,245), 96 × 150 pt, converted to lattice coordinates. The bridge parts at p ≈ .62. */
export function lungDensity(x: number, y: number) {
  return Math.max(
    lobe(x, y, 12.475, 14.006, 3.821, 5.97, 2.7),
    lobe(x, y, 18.525, 14.006, 3.821, 5.97, 2.7),
    0.65 * lobe(x, y, 15.5, 14.7, 5.2, 6, 2.4),
  );
}
/**
 * Exhale order for the traced lungs. Each half deflates towards its own outlet, the lower outer point of the lobe
 * (lattice columns 11 and 20, row 17) where its stream leaves, so a half-empty lobe is the same lobe at a smaller
 * size and the two lobes part along the midline. The trachea (columns 14–17, the top four rows) stays until the
 * lobes are nearly empty and drains from its foot.
 * Returns a density for the threshold walk: 0.98 is the last cell lit, 0.3 the first lung cell to go.
 */
export const LUNG_OUTLET = { left: 11, right: 20, row: 17 } as const;
const TRACHEA = { from: 14, to: 17, rows: 4 } as const;
function restRadius(x: number, y: number) {
  return REST_RADII[Math.round(y) - 7]?.[Math.round(x)] ?? 0;
}
function inTrachea(col: number, row: number) {
  return col >= TRACHEA.from && col <= TRACHEA.to && row < TRACHEA.rows;
}
export function lungExhaleDensity(x: number, y: number) {
  const r = restRadius(x, y);
  if (r <= 0.12) return 0;
  if (r < 0.5) return 0.26;
  const col = Math.round(x);
  const row = Math.round(y) - 7;
  if (inTrachea(col, row)) return 0.62 + 0.36 * (1 - row / TRACHEA.rows);
  const left = col <= 15;
  const ax = left ? LUNG_OUTLET.left : LUNG_OUTLET.right;
  const ay = LUNG_OUTLET.row;
  const inLobe = (u: number, v: number) => {
    const c = Math.round(u);
    if (inTrachea(c, Math.round(v) - 7)) return false;
    return (left ? c <= 15 : c >= 16) && restRadius(u, v) >= 0.5;
  };
  // Smallest scale about the outlet whose copy of the lobe still covers this cell.
  let g = 1;
  for (let k = 1; k <= 50; k++) {
    const sc = k / 50;
    if (inLobe(ax + (x - ax) / sc, ay + (y - ay) / sc)) {
      g = sc;
      break;
    }
  }
  return 0.3 + 0.68 * (1 - Math.pow(g, 1.6));
}
function presetMask(preset: Preset, x: number, y: number) {
  if (preset === 'lungs') return lungDensity(x, y) > 0.04 ? 1 : 0;
  const u = (x - 15.5) / 9;
  const v = (y - 13) / 7;
  if (preset === 'battery') return Math.abs(u) < 0.75 && Math.abs(v) < 0.85 ? 1 : Math.abs(u) < 0.28 && v > -1 && v < -0.8 ? 1 : 0;
  if (preset === 'bottle') return (Math.abs(u) < 0.7 && v > -0.25 && v < 0.95) || (Math.abs(u) < 0.3 && v > -1 && v < 0) ? 1 : 0;
  const a = u * u + Math.pow(-v + 0.2, 2) - 0.65;
  return a * a * a - u * u * Math.pow(-v + 0.2, 3) < 0 ? 1 : 0;
}
/** Two-pass chamfer distance transform, O(cells), ≤ 6k cells including custom masks. */
export function distanceField(mask: number[], cols: number, rows: number, normalize = true) {
  const d: number[] = mask.map((a) => (a > 0.1 ? 1000 : 0));
  for (let y = 0; y < rows; y++)
    for (let x = 0; x < cols; x++) {
      const i = y * cols + x;
      if (x) d[i] = Math.min(d[i], d[i - 1] + 1);
      if (y) d[i] = Math.min(d[i], d[i - cols] + 1);
      if (x && y) d[i] = Math.min(d[i], d[i - cols - 1] + Math.SQRT2);
    }
  for (let y = rows - 1; y >= 0; y--)
    for (let x = cols - 1; x >= 0; x--) {
      const i = y * cols + x;
      if (x < cols - 1) d[i] = Math.min(d[i], d[i + 1] + 1);
      if (y < rows - 1) d[i] = Math.min(d[i], d[i + cols] + 1);
      if (x < cols - 1 && y < rows - 1) d[i] = Math.min(d[i], d[i + cols + 1] + Math.SQRT2);
    }
  const max = normalize ? Math.max(1, ...d) : 1;
  return d.map((v) => v / max);
}
/** Atlas channels: R pressure, G traced resting radius, B pile density, A opaque. Last row: 16-bit pile threshold. */
export function makeFieldGrid(preset: Preset = 'lungs', pitch = 402 / 32, customBody?: number[], customPile?: number[]): FieldGrid {
  // Preserve the whole instrument's aspect ratio at 6 pt; 69 × 86 is the 6k-cell ceiling.
  const cols = Math.max(16, Math.min(69, Math.round(402 / Math.max(402 / 69, pitch))));
  const rows = Math.round((GRID.rows * cols) / 32);
  const width = Math.max(cols * 2, GRID.tableSize);
  const body = new Array<number>(cols * rows);
  const radius = new Array<number>(body.length);
  const pile = new Array<number>(body.length);
  // Array.fill is intentional: a sparse Array.map would leave the mask empty.
  const resolvedMask =
    customBody ??
    Array.from({ length: body.length }, (_, i) => presetMask(preset, ((i % cols) / cols) * 32, (Math.floor(i / cols) / cols) * 32));
  const distance = preset === 'lungs' && !customBody ? null : distanceField(resolvedMask, cols, rows);
  const pileDistance = customPile ? distanceField(customPile, cols, rows) : null;
  for (let i = 0; i < body.length; i++) {
    const x = ((i % cols) / cols) * 32;
    const y = (Math.floor(i / cols) / cols) * 32;
    const r = preset === 'lungs' && !customBody ? (REST_RADII[Math.round(y) - 7]?.[Math.round(x)] ?? 0) : smooth((distance?.[i] ?? 0) * 5);
    radius[i] = r;
    body[i] = distance ? clamp((distance[i] + 0.08) * resolvedMask[i]) : r > 0.12 ? lungExhaleDensity(x, y) : 0;
    // A bottom-centre mound, enough room to receive all upper lit area, never touches the card.
    pile[i] =
      customPile && pileDistance
        ? clamp((pileDistance[i] * 0.4 + clamp((y - 24) / 12) * 0.6) * customPile[i])
        : y >= 29 && y <= 36 // row 29 is the first row clear of the "Liter" label (rows 27–28)
          ? clamp(1 - Math.pow((x - 15.5) / 15, 2) - Math.pow((36 - y) / 11, 1.4))
          : 0;
  }
  const bytes = new Uint8Array(width * (rows + 1) * 4);
  const quantBody = body.map((v) => Math.round(v * 255) / 255);
  const quantPile = pile.map((v) => Math.round(v * 255) / 255);
  const haloDistance = distanceField(
    radius.map((v) => (v > 0.12 ? 0 : 1)),
    cols,
    rows,
    false,
  );
  const lastAir = new Map(
    quantBody
      .map((d, i) => ({ d, i }))
      .filter(({ i }) => radius[i] > 0.7)
      .sort((a, b) => b.d - a.d)
      .slice(0, 12)
      .map(({ i }, rank) => [i, (rank + 1) / 12]),
  );
  // Source rest clusters: rows 29 / 33 / 36, including their faint outer cells.
  const sparse: number[] = body.map((_, i) => {
    const x = Math.round(((i % cols) * 32) / cols);
    const row = Math.floor(i / cols);
    if (row === Math.round((29 * cols) / 32)) return x >= 14 && x <= 17 ? [0.55, 1, 1, 0.65][x - 14] : 0;
    if (row === Math.round((33 * cols) / 32)) return x >= 14 && x <= 17 ? [0.3, 0.8, 0.88, 0.4][x - 14] : 0;
    if (row === Math.round((36 * cols) / 32)) return x >= 12 && x <= 19 ? [0.4, 1, 1, 1, 1, 1, 1, 0.5][x - 12] : 0;
    return 0;
  });
  // Histogram integration avoids scanning the whole grid at every threshold solve.
  // Work is bounded by 256 density bins, independent of silhouette complexity.
  const bodyWeights = new Float64Array(256);
  const pileNormal = new Float64Array(256);
  const sparseCells: { density: number; area: number }[] = [];
  for (let i = 0; i < body.length; i++) {
    const rr = Math.round(radius[i] * 255) / 255;
    bodyWeights[Math.round(quantBody[i] * 255)] += rr * rr;
    pileNormal[Math.round(quantPile[i] * 255)]++;
    if (sparse[i]) sparseCells.push({ density: quantPile[i], area: sparse[i] * sparse[i] });
  }
  const bodyBins = Array.from({ length: 256 }, (_, i) => i).filter((i) => bodyWeights[i] > 0);
  // Bin 0 is every cell outside the mound: a negative threshold must never count (or light) it as room.
  const pileBins = Array.from({ length: 256 }, (_, i) => i).filter((i) => i > 0 && pileNormal[i] > 0);
  const bodyInk = (th: number) => {
    let sum = 0;
    for (const i of bodyBins) {
      const rr = smooth((i / 255 - th) / GRID.soft);
      sum += rr * rr * bodyWeights[i];
    }
    return sum;
  };
  const pileInk = (th: number) => {
    let sum = 0;
    for (const i of pileBins) {
      const rr = smooth((i / 255 - th) / GRID.soft);
      sum += rr * rr * pileNormal[i];
    }
    for (const cell of sparseCells) {
      const rr = smooth((cell.density - th) / GRID.soft);
      sum += Math.max(0, cell.area - rr * rr);
    }
    return sum;
  };
  const total = bodyInk(0.04);
  const basePile = sparse.reduce((sum, r) => sum + r * r, 0);
  const thresholds: number[] = [];
  let conservationError = 0;
  for (let n = 0; n < GRID.tableSize; n++) {
    // Uniform samples in pressure (p^0.8), denser around the first halftone edge collapse.
    const p = Math.pow(n / (GRID.tableSize - 1), 1 / 0.8);
    const target = basePile + total - bodyInk(thresholdAt(p));
    let lo = -0.22;
    let hi = 1;
    for (let k = 0; k < 18; k++) {
      const m = (lo + hi) / 2;
      if (pileInk(m) > target) lo = m;
      else hi = m;
    }
    const th = (lo + hi) / 2;
    thresholds.push(th);
    conservationError = Math.max(conservationError, Math.abs(pileInk(th) - target));
    const code = Math.round(((th + 0.22) / 1.22) * 65535);
    const offset = (rows * width + n) * 4;
    bytes[offset] = code >> 8;
    bytes[offset + 1] = code & 255;
    bytes[offset + 3] = 255;
  }
  for (let i = 0; i < body.length; i++) {
    const off = (Math.floor(i / cols) * width + (i % cols)) * 4;
    bytes[off] = Math.round(quantBody[i] * 255);
    bytes[off + 1] = Math.round(radius[i] * 255);
    bytes[off + 2] = Math.round(quantPile[i] * 255);
    bytes[off + 3] = 255;
    const glowOff = off + cols * 4;
    bytes[glowOff] = haloDistance[i] > 0 && haloDistance[i] <= 4 ? Math.round((0.08 + 0.2 * (1 - haloDistance[i] / 4)) * 255) : 0;
    bytes[glowOff + 1] = Math.round((lastAir.get(i) ?? 0) * 255);
    bytes[glowOff + 2] = Math.round(sparse[i] * 255);
    bytes[glowOff + 3] = 255;
  }
  return { cols, rows, width, bytes, thresholds, conservationError };
}
