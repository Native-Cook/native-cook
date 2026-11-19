// Soft Weather: the forecast, the sky palettes and the film, as pure functions.
// Everything the screen shows is a function of two numbers: `hour` (a fractional index into HOURS,
// what the hourly row is scrolled to) and the ambient clock (seconds, wrapped to LOOP). The film only
// moves `hour`; a finger moves it too, by scrolling the row.
//
// No React Native imports here, so the module also runs in Node for offline previews.

export const LOOP = 16; // seconds

// ---------------------------------------------------------------- the forecast

export type Hour = { label: string; temp: number; symbol: string; cond: number; rain: number; wind: number };

/** Conditions, in the order their labels are stacked. */
export const CONDITIONS = ['Clear', 'Sunny', 'Partly Cloudy', 'Cloudy', 'Light Rain', 'Rain'] as const;

export const HOURS: Hour[] = [
  { label: 'Now', temp: 13, symbol: 'sunrise.fill', cond: 0, rain: 0, wind: 6 },
  { label: '8 AM', temp: 14, symbol: 'sun.max.fill', cond: 1, rain: 0, wind: 7 },
  { label: '9 AM', temp: 16, symbol: 'sun.max.fill', cond: 1, rain: 0, wind: 8 },
  { label: '10 AM', temp: 18, symbol: 'sun.max.fill', cond: 1, rain: 0, wind: 9 },
  { label: '11 AM', temp: 19, symbol: 'cloud.sun.fill', cond: 2, rain: 5, wind: 11 },
  { label: '12 PM', temp: 20, symbol: 'cloud.sun.fill', cond: 2, rain: 10, wind: 12 },
  { label: '1 PM', temp: 19, symbol: 'cloud.fill', cond: 3, rain: 30, wind: 15 },
  { label: '2 PM', temp: 17, symbol: 'cloud.drizzle.fill', cond: 4, rain: 70, wind: 18 },
  { label: '3 PM', temp: 16, symbol: 'cloud.rain.fill', cond: 5, rain: 90, wind: 21 },
  { label: '4 PM', temp: 16, symbol: 'cloud.rain.fill', cond: 5, rain: 80, wind: 19 },
  { label: '5 PM', temp: 16, symbol: 'cloud.drizzle.fill', cond: 4, rain: 40, wind: 14 },
  { label: '6 PM', temp: 16, symbol: 'cloud.sun.fill', cond: 2, rain: 10, wind: 11 },
  { label: '7 PM', temp: 15, symbol: 'sunset.fill', cond: 0, rain: 0, wind: 9 },
  { label: '8 PM', temp: 14, symbol: 'cloud.moon.fill', cond: 2, rain: 0, wind: 8 },
  { label: '9 PM', temp: 13, symbol: 'moon.stars.fill', cond: 0, rain: 0, wind: 7 },
  { label: '10 PM', temp: 12, symbol: 'moon.stars.fill', cond: 0, rain: 0, wind: 6 },
  { label: '11 PM', temp: 12, symbol: 'moon.fill', cond: 0, rain: 0, wind: 6 },
  { label: '12 AM', temp: 11, symbol: 'moon.fill', cond: 0, rain: 0, wind: 5 },
];
export const LAST_HOUR = HOURS.length - 1;
export const TEMPS = HOURS.map((h) => h.temp);
export const RAIN_PCT = HOURS.map((h) => h.rain);
export const WIND = HOURS.map((h) => h.wind);
export const CONDS = HOURS.map((h) => h.cond);

// ---------------------------------------------------------------- colour

/** sRGB hex -> OKLab [L, a, b]. */
export function hexToLab(hex: string): [number, number, number] {
  const n = parseInt(hex.slice(1), 16);
  const lin = (v: number) => {
    const c = v / 255;
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  };
  const r = lin((n >> 16) & 255);
  const g = lin((n >> 8) & 255);
  const b = lin(n & 255);
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  return [
    0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
  ];
}

/** 16 hex colours, rows top to bottom -> 48 OKLab numbers. */
function palette(rows: string[]): number[] {
  const out: number[] = [];
  for (const hex of rows) out.push(...hexToLab(hex));
  return out;
}

// Six skies, each a 4 x 4 grid read row by row from the top of the screen. Gentle: low chroma, the top
// rows mid-tone so white type reads, the sun's warmth in the upper right where it would be.
const DAWN = palette([
  '#7C86C8', '#8D8BCB', '#A592C8', '#B79BC6',
  '#A49ACB', '#D6A3BE', '#EBAFAE', '#D9A6B9',
  '#E9B6AA', '#F4C3A6', '#F6C59F', '#EDB7A8',
  '#F7D3B6', '#FADBBD', '#F5CFB5', '#EEC6B8',
]);
const DAY = palette([
  '#5E97DA', '#6FA3DE', '#93B9E0', '#E9CF9E',
  '#73A7E0', '#8BB6E6', '#A9C8E9', '#DCD3B8',
  '#9CC0E8', '#B1CDEC', '#C3D7EE', '#CCDBEA',
  '#BFD6EE', '#CBDDF0', '#D4E2F0', '#D9E4EE',
]);
const OVERCAST = palette([
  '#6F7F99', '#7A89A0', '#8693A8', '#929CAE',
  '#8291A8', '#8E9BB0', '#9AA5B6', '#A2ABBA',
  '#9AA6B8', '#A6B0BF', '#AEB6C3', '#B3BAC5',
  '#AEB7C3', '#B6BEC8', '#BBC2CB', '#BEC4CC',
]);
const RAIN = palette([
  '#4B586E', '#536077', '#5B677D', '#636D82',
  '#5A677E', '#647189', '#6C788E', '#6A7489',
  '#6E7B91', '#78849A', '#7F8A9D', '#7A8598',
  '#7D889B', '#8590A1', '#8A94A4', '#86909F',
]);
const DUSK = palette([
  '#4F4E8E', '#5E5396', '#705797', '#7F5B95',
  '#7C5F9D', '#B06E93', '#C77D88', '#A06E94',
  '#D38C83', '#E2A07F', '#E6A87B', '#D99383',
  '#EAB585', '#EEBF8C', '#EAB185', '#E0A388',
]);
const NIGHT = palette([
  '#161B3E', '#1A2048', '#202552', '#262551',
  '#1E2854', '#283669', '#30356A', '#2A2A5A',
  '#263563', '#30447A', '#38467A', '#2F3769',
  '#212E57', '#283962', '#2E3D69', '#262F59',
]);

/** Sky keyframes on the hour index: [hour, palette]. Between two, an eased blend in OKLab. */
const SKY: [number, number[]][] = [
  [0, DAWN],
  [3, DAY],
  [5, DAY],
  [6.5, OVERCAST],
  [8, RAIN],
  [9.2, RAIN],
  [10.6, OVERCAST],
  [12, DUSK],
  [14, NIGHT],
];
const SKY_H = SKY.map((k) => k[0]);
const SKY_P = SKY.map((k) => k[1]);

/** Rain on the glass, by hour. */
const RAIN_K: [number, number][] = [
  [6, 0],
  [7, 0.45],
  [8, 1],
  [9.3, 1],
  [10.3, 0.35],
  [11, 0],
];
const RAIN_H = RAIN_K.map((k) => k[0]);
const RAIN_V = RAIN_K.map((k) => k[1]);

// ---------------------------------------------------------------- worklets (declared before their callers)

export function clamp01(x: number): number {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number): number {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOut(x: number): number {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function smooth(x: number): number {
  'worklet';
  return x * x * (3 - 2 * x);
}

/**
 * Blend two OKLab grids (count colours) and keep the chroma: a straight Lab blend of a cool and a warm
 * colour passes through grey, so each blended colour's a,b are rescaled to the blended chroma.
 */
export function mixLab(a: number[], b: number[], f: number, count: number, out: number[]): void {
  'worklet';
  for (let i = 0; i < count; i++) {
    const k = i * 3;
    const ca = Math.sqrt(a[k + 1] * a[k + 1] + a[k + 2] * a[k + 2]);
    const cb = Math.sqrt(b[k + 1] * b[k + 1] + b[k + 2] * b[k + 2]);
    const L = a[k] + (b[k] - a[k]) * f;
    const x = a[k + 1] + (b[k + 1] - a[k + 1]) * f;
    const y = a[k + 2] + (b[k + 2] - a[k + 2]) * f;
    const c = Math.sqrt(x * x + y * y);
    const s = c > 0.0001 ? (ca + (cb - ca) * f) / c : 1;
    out[k] = L;
    out[k + 1] = x * s;
    out[k + 2] = y * s;
  }
}

/** Linear lookup in a table of fractional-index values (temps, percentages). */
export function sampleTable(table: number[], h: number): number {
  'worklet';
  const n = table.length - 1;
  const x = h < 0 ? 0 : h > n ? n : h;
  const i = Math.floor(x);
  if (i >= n) return table[n];
  const f = x - i;
  return table[i] + (table[i + 1] - table[i]) * f;
}

/** Piecewise eased curve through keys (xs ascending). */
function keyed(xs: number[], ys: number[], x: number): number {
  'worklet';
  if (x <= xs[0]) return ys[0];
  const n = xs.length - 1;
  if (x >= xs[n]) return ys[n];
  let i = 0;
  while (i < n - 1 && x > xs[i + 1]) i++;
  const f = smooth((x - xs[i]) / (xs[i + 1] - xs[i]));
  return ys[i] + (ys[i + 1] - ys[i]) * f;
}

export function rainAt(h: number): number {
  'worklet';
  return keyed(RAIN_H, RAIN_V, h);
}

/** The sky's 48 OKLab numbers at hour h, written into out. */
export function skyAt(h: number, out: number[]): void {
  'worklet';
  const n = SKY_H.length - 1;
  let i = 0;
  if (h >= SKY_H[n]) i = n - 1;
  else while (i < n - 1 && h > SKY_H[i + 1]) i++;
  const span = SKY_H[i + 1] - SKY_H[i];
  const f = h <= SKY_H[0] ? 0 : h >= SKY_H[n] ? 1 : smooth(clamp01((h - SKY_H[i]) / span));
  const a = SKY_P[i];
  const b = SKY_P[i + 1];
  mixLab(a, b, f, 16, out);
}

/**
 * Control-point offsets (32 numbers, x and y per point, in screen fractions) at hour h and ambient
 * time `amb` (seconds). Corners stay put; edge points slide along their edge; inner points drift on
 * whole-number frequencies of the loop, sway with the hour (so scrubbing visibly moves the mesh),
 * lean towards the sun by day and sag a little under rain.
 */
export function offsetsAt(h: number, amb: number, rain: number, out: number[]): void {
  'worklet';
  const tau = (amb / LOOP) * Math.PI * 2;
  const sun = h < 3 ? h / 3 : h < 6 ? 1 : h < 8 ? 1 - (h - 6) / 2 : 0;
  for (let j = 0; j < 4; j++) {
    for (let i = 0; i < 4; i++) {
      const k = (j * 4 + i) * 2;
      const edgeX = i === 0 || i === 3;
      const edgeY = j === 0 || j === 3;
      const s = (i * 7 + j * 13) * 0.37;
      let dx = 0.05 * Math.sin(tau * (1 + ((i + j) & 1)) + s) + 0.055 * Math.sin(h * 0.75 + i * 1.3 + j * 0.6);
      let dy = 0.03 * Math.cos(tau * (1 + (i & 1)) + s * 1.7) + 0.035 * Math.cos(h * 0.6 + j * 1.7 + i * 0.4);
      // the sun pushes its neighbours away so its warm corner spreads in; rain weighs the middle down
      dx += -0.07 * sun * (i / 3) * (1 - j / 3);
      dy += 0.05 * sun * (i / 3) * (1 - j / 3) + 0.04 * rain * (edgeY ? 0 : 1);
      if (edgeX) dx = 0;
      if (edgeY) dy = 0;
      out[k] = dx;
      out[k + 1] = dy;
    }
  }
}

/** The film: the hour the row is scrolled to at loop time t (seconds). Each leg starts and ends at
 * rest, like a finger that scrubs, pauses and scrubs on. */
export const PHASE = {
  toNoon: [2.0, 4.6], // Now -> 11 AM
  noon: [4.6, 5.6], // a slow look at the sun, 11 AM -> 12 PM
  toRain: [5.6, 7.6], // -> 3 PM
  rain: [7.6, 8.8], // a slow look at the rain, 3 PM -> 4 PM
  toNight: [8.8, 11.2], // -> 10 PM
  back: [13.0, 14.6], // back to Now
} as const;

export function filmHour(t: number): number {
  'worklet';
  if (t < PHASE.toNoon[0]) return 0;
  if (t < PHASE.toNoon[1]) return 4 * easeInOut(seg(t, PHASE.toNoon[0], PHASE.toNoon[1]));
  if (t < PHASE.noon[1]) return 4 + smooth(seg(t, PHASE.noon[0], PHASE.noon[1]));
  if (t < PHASE.toRain[1]) return 5 + 3 * easeInOut(seg(t, PHASE.toRain[0], PHASE.toRain[1]));
  if (t < PHASE.rain[1]) return 8 + smooth(seg(t, PHASE.rain[0], PHASE.rain[1]));
  if (t < PHASE.back[0]) return 9 + 6 * easeInOut(seg(t, PHASE.toNight[0], PHASE.toNight[1]));
  return 15 * (1 - easeInOut(seg(t, PHASE.back[0], PHASE.back[1])));
}

/** Rain is kept off the glass while the row rewinds to Now, so drops do not flash for a frame or two. */
export function filmRainGate(t: number): number {
  'worklet';
  return 1 - smooth(seg(t, PHASE.back[0] - 0.2, PHASE.back[0] + 0.1)) * (1 - smooth(seg(t, PHASE.back[1] - 0.1, PHASE.back[1] + 0.2)));
}

/** Weight of condition label c at hour h: a tent around each hour that has it (sums to 1). */
export function condWeight(c: number, h: number): number {
  'worklet';
  let w = 0;
  for (let i = 0; i < CONDS.length; i++) {
    if (CONDS[i] !== c) continue;
    const d = Math.abs(h - i);
    if (d < 1) w += 1 - d;
  }
  return smooth(clamp01(w));
}
