import { PHYSICS, clamp } from './pjg-motion';

export type Vessel =
  { width: number; height: number; topRadius: number; bottomRadius: number } | { path: string; width: number; height: number };
export const VESSELS = {
  jar: { width: 222, height: 422, topRadius: 68, bottomRadius: 70 },
  bottle: {
    width: 180,
    height: 420,
    path: 'M62 0H118Q128 0 128 10V62C128 96 180 105 180 152V380Q180 420 140 420H40Q0 420 0 380V152C0 105 52 96 52 62V10Q52 0 62 0Z',
  },
  capsule: { width: 180, height: 420, topRadius: 90, bottomRadius: 90 },
} satisfies Record<string, Vessel>;
export type VesselTable = {
  left: number[];
  right: number[];
  volume: number[];
  total: number;
  width: number;
  height: number;
  custom: number;
  topRadius: number;
  bottomRadius: number;
};

// Called once per outline, on JS. A closed, y-monotone SVG path is sampled with Skia.contains.
export function buildVessel(vessel: Vessel, contains?: (x: number, y: number) => boolean): VesselTable {
  const { width, height } = vessel;
  if (!(width > 0 && height > 0)) throw new Error('LiquidGauge: vessel dimensions must be positive.');
  if ('path' in vessel && !contains) throw new Error('LiquidGauge: a valid closed SVG vessel is required.');
  const left: number[] = [],
    right: number[] = [],
    volume: number[] = Array(PHYSICS.rows).fill(0);
  for (let row = 0; row < PHYSICS.rows; row++) {
    const y = ((row + 0.5) / PHYSICS.rows) * height;
    let l = 0,
      r = width;
    if ('path' in vessel && contains) {
      let first = -1,
        last = -1;
      for (let n = 0; n <= 256; n++)
        if (contains((n / 256) * width, y)) {
          if (first < 0) first = n;
          last = n;
        }
      if (first < 0) {
        l = width / 2;
        r = l;
      } else {
        let lo = (Math.max(0, first - 1) / 256) * width,
          hi = (first / 256) * width;
        for (let n = 0; n < 12; n++) {
          const mid = (lo + hi) / 2;
          if (contains(mid, y)) hi = mid;
          else lo = mid;
        }
        l = (lo + hi) / 2;
        lo = (last / 256) * width;
        hi = (Math.min(256, last + 1) / 256) * width;
        for (let n = 0; n < 12; n++) {
          const mid = (lo + hi) / 2;
          if (contains(mid, y)) lo = mid;
          else hi = mid;
        }
        r = (lo + hi) / 2;
      }
    } else if ('topRadius' in vessel) {
      const tr = Math.min(width / 2, vessel.topRadius),
        br = Math.min(width / 2, vessel.bottomRadius);
      if (y < tr) l = tr - Math.sqrt(Math.max(0, tr * tr - (y - tr) ** 2));
      if (y > height - br) l = br - Math.sqrt(Math.max(0, br * br - (y - height + br) ** 2));
      r = width - l;
    }
    left.push(l);
    right.push(r);
  }
  let total = 0;
  for (let row = 127; row >= 0; row--) {
    total += ((right[row] - left[row]) * height) / 128;
    volume[row] = total;
  }
  return {
    left,
    right,
    volume,
    total,
    width,
    height,
    custom: 'path' in vessel ? 1 : 0,
    topRadius: 'topRadius' in vessel ? vessel.topRadius : 0,
    bottomRadius: 'bottomRadius' in vessel ? vessel.bottomRadius : 0,
  };
}
export function volumeToY(fraction: number, table: VesselTable) {
  'worklet';
  const target = clamp(fraction) * table.total;
  if (target <= 0) return table.height;
  if (target >= table.total) return 0;
  if (target <= table.volume[127]) return ((128 - target / table.volume[127]) / 128) * table.height;
  let lo = 0,
    hi = 127;
  while (hi - lo > 1) {
    const m = (lo + hi) >> 1;
    if (table.volume[m] > target) lo = m;
    else hi = m;
  }
  const a = table.volume[lo],
    b = table.volume[hi];
  return ((lo + (a - target) / Math.max(a - b, 0.001)) / 128) * table.height;
}
export function yToVolume(y: number, table: VesselTable) {
  'worklet';
  const row = clamp((y / table.height) * 128, 0, 128),
    i = Math.min(127, Math.floor(row));
  const next = i === 127 ? 0 : table.volume[i + 1];
  return (table.volume[i] + (next - table.volume[i]) * (row - i)) / table.total;
}
