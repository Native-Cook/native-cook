// Every range has the same number of points, so a morph moves each point straight up or down.
export const POINTS = 120;

export type Range = {
  label: string;
  /** Percent change over the range; its sign colours the line. */
  change: number;
  /** One value per point, oldest first; any scale, the chart fits it to its height. */
  series: number[];
};

/** A small seeded generator (mulberry32): the same seed always gives the same walk. */
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

type Walk = {
  seed: number;
  change: number;
  /** How far the walk drifts against its noise: 0 is flat, 1 climbs about its own wobble. */
  drift: number;
  /** How jagged it is: the share of steps that jump instead of stepping. */
  spikes: number;
  /** Stretches where the price did not move (markets closed), as [first, last] points. */
  holds?: [number, number][];
  /** A hump in the first quarter, which makes the morph into this range seem to roll. */
  bump?: number;
};

function walk({ seed, drift, spikes, holds = [], bump = 0 }: Walk) {
  const random = seeded(seed);
  const raw: number[] = [];
  let value = 0;
  for (let i = 0; i < POINTS; i++) {
    const jump = random() < spikes ? 4 : 1;
    value += (random() - 0.5) * jump;
    raw.push(value);
  }
  // Take the walk's own trend out, then put the range's in, so the sign of the change is honest.
  const first = raw[0];
  const last = raw[POINTS - 1];
  let spread = 0;
  for (const v of raw) spread = Math.max(spread, Math.abs(v - first));
  const out = raw.map((v, i) => {
    const t = i / (POINTS - 1);
    const hump = bump * Math.exp(-Math.pow((t - 0.16) / 0.07, 2));
    return v - first - (last - first) * t + drift * spread * t + hump * spread;
  });
  for (const [from, to] of holds) for (let i = from + 1; i <= to; i++) out[i] = out[from];
  return out;
}

const WALKS: (Walk & { label: string })[] = [
  { label: '1D', seed: 11, change: -0.18, drift: -0.5, spikes: 0.05 },
  { label: '1W', seed: 7, change: 0.32, drift: 1.1, spikes: 0.12, holds: [[86, 104], [106, 116]] },
  { label: '1M', seed: 23, change: -1.69, drift: -0.9, spikes: 0.18 },
  { label: '3M', seed: 41, change: -0.65, drift: -0.6, spikes: 0.14, bump: 0.9 },
  { label: '6M', seed: 5, change: 0.62, drift: 0.3, spikes: 0.16 },
  { label: '1Y', seed: 63, change: 0.97, drift: 0.5, spikes: 0.2 },
  { label: '2Y', seed: 87, change: 8.53, drift: 1.4, spikes: 0.2 },
];

export const RANGES: Range[] = WALKS.map((w) => ({ label: w.label, change: w.change, series: walk(w) }));

/** A series fitted to 0 (its lowest point) to 1 (its highest), with exactly POINTS values. */
export function normalized(series: number[]) {
  const out: number[] = [];
  let low = Infinity;
  let high = -Infinity;
  for (const v of series) {
    low = Math.min(low, v);
    high = Math.max(high, v);
  }
  for (let i = 0; i < POINTS; i++) {
    const at = series.length === POINTS ? i : Math.round((i / (POINTS - 1)) * (series.length - 1));
    out.push(high > low ? (series[at] - low) / (high - low) : 0.5);
  }
  return out;
}
