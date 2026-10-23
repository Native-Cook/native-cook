import { Skia } from '@shopify/react-native-skia';

export type LineRange = {
  key: string;
  /** The segmented control's label. */
  name: string;
  /** The line under the title. */
  caption: string;
  /** Spread evenly under the chart, first at the left edge, last at the right. */
  ticks: string[];
  /** Percent values, oldest first: the high series and the low series. */
  high: number[];
  low: number[];
};

/** A small seeded generator (mulberry32): the same walk on every run and every stack. */
function seeded(seed: number) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** A random walk with a drift, kept inside [lo, hi] by bouncing off its edges. */
export function walk(seed: number, count: number, start: number, drift: number, step: number, lo: number, hi: number) {
  const random = seeded(seed);
  const out: number[] = [];
  let v = start;
  for (let i = 0; i < count; i++) {
    out.push(Math.round(v * 10) / 10);
    v += drift + (random() * 2 - 1) * step;
    if (v > hi) v = 2 * hi - v;
    if (v < lo) v = 2 * lo - v;
  }
  return out;
}

export const RANGES: LineRange[] = [
  {
    key: 'day',
    name: 'Day',
    caption: 'Last 24 hours',
    ticks: ['00:00', '06:00', '12:00', '18:00', 'Now'],
    // A day is the most restless.
    high: walk(31, 90, 70, 0.05, 6.5, 45, 95),
    low: walk(32, 90, 28, 0, 5, 10, 45),
  },
  {
    key: 'week',
    name: 'Week',
    caption: 'Last 14 days',
    ticks: ['Mar 16', 'Mar 19', 'Mar 22', 'Mar 25', 'Mar 29'],
    high: walk(11, 90, 60, 0.3, 4, 45, 95),
    low: walk(12, 90, 40, -0.28, 3, 10, 45),
  },
  {
    key: 'year',
    name: 'Year',
    caption: '1999 – 2024',
    ticks: ['1999', '2005', '2011', '2017', '2024'],
    // Years drift more smoothly.
    high: walk(21, 60, 52, 0.62, 2.2, 45, 95),
    low: walk(22, 60, 42, -0.3, 2, 10, 45),
  },
];

/** The point of a series at a fraction of the chart's width, in canvas points. */
export function pointAt(values: number[], fraction: number, width: number, height: number) {
  'worklet';
  const n = values.length;
  const f = Math.min(1, Math.max(0, fraction)) * (n - 1);
  const i = Math.min(n - 2, Math.floor(f));
  const v = values[i] + (values[i + 1] - values[i]) * (f - i);
  return [(f / (n - 1)) * width, height - (v / 100) * height];
}

/** The series between two fractions of the chart's width, cut exactly at both ends. */
export function linePart(values: number[], from: number, to: number, width: number, height: number) {
  'worklet';
  const path = Skia.Path.Make();
  if (to - from <= 0.0005) return path;
  const n = values.length;
  const [sx, sy] = pointAt(values, from, width, height);
  path.moveTo(sx, sy);
  const first = Math.floor(from * (n - 1)) + 1;
  const last = Math.ceil(to * (n - 1)) - 1;
  for (let i = first; i <= last; i++) path.lineTo((i / (n - 1)) * width, height - (values[i] / 100) * height);
  const [ex, ey] = pointAt(values, to, width, height);
  path.lineTo(ex, ey);
  return path;
}
