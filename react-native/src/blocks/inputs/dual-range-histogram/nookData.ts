export const PRICE_MIN = 1000;
export const PRICE_MAX = 5000;
export const PRICE_STEP = 50;
export const PRICE_GAP = 300;
export const PRICE_DEFAULT = [1800, 3600] as const;
export const BIN_COUNT = 36;

export const PLACE_TYPES = ['Entire home', 'Private room'] as const;
export const BEDROOMS = ['Any', '1', '2', '3+'] as const;
export const AMENITIES = [
  { label: 'Pets allowed', share: 0.46 },
  { label: 'Furnished', share: 0.62 },
  { label: 'Balcony or garden', share: 0.38 },
] as const;

// Where the prices of each kind of listing cluster, as shares of the axis.
const SHAPES = [
  [
    { peak: 0.34, wide: 0.13, weight: 1, total: 940 },
    { peak: 0.26, wide: 0.08, weight: 1, total: 410 },
    { peak: 0.38, wide: 0.1, weight: 1, total: 460 },
    { peak: 0.62, wide: 0.13, weight: 1, total: 310 },
  ],
  [
    { peak: 0.13, wide: 0.08, weight: 1, total: 520 },
    { peak: 0.11, wide: 0.06, weight: 1, total: 340 },
    { peak: 0.2, wide: 0.07, weight: 1, total: 130 },
    { peak: 0.29, wide: 0.07, weight: 1, total: 50 },
  ],
] as const;

function hash(i: number) {
  const x = Math.sin(i * 12.9898 + 4.1) * 43758.5453;
  return x - Math.floor(x);
}

/** A believable, slightly ragged price distribution: a main hump, a smaller expensive one and a seeded jitter. */
export function makeBins(type: number, bedrooms: number, share: number) {
  const s = SHAPES[type][bedrooms];
  const raw: number[] = [];
  for (let i = 0; i < BIN_COUNT; i += 1) {
    const x = (i + 0.5) / BIN_COUNT;
    const main = Math.exp(-(((x - s.peak) / s.wide) ** 2));
    const tail = 0.34 * Math.exp(-(((x - (s.peak + 0.3)) / (s.wide * 1.5)) ** 2));
    const jitter = 0.72 + 0.56 * hash(i + type * 40 + bedrooms * 7);
    raw.push((main + tail + 0.025) * jitter);
  }
  const sum = raw.reduce((a, b) => a + b, 0);
  return raw.map((v) => (v / sum) * s.total * share);
}
