// Trail Parallax Tracker: one trail = one day. Each biome's flat fills are relit for the hour in OKLab (lightness,
// a/b shift, chroma scale relative to the hour the art is painted at); fills stay perfectly flat. The shader gets the
// colours already encoded for its output (Display P3 on iOS), plain paints get sRGB strings.

import { Platform } from 'react-native';

import { BIOMES, HOUR_KEYS, HOURS } from './tpt-theme';

const P3 = Platform.OS === 'ios';

const hexRgb = (h: string): [number, number, number] => [parseInt(h.slice(1, 3), 16) / 255, parseInt(h.slice(3, 5), 16) / 255, parseInt(h.slice(5, 7), 16) / 255];

const lin = (c: number) => {
  'worklet';
  return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
};
const enc = (c: number) => {
  'worklet';
  const v = Math.min(Math.max(c, 0), 1);
  return v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
};

const toLab = (rgb: [number, number, number]): [number, number, number] => {
  const r = lin(rgb[0]);
  const g = lin(rgb[1]);
  const b = lin(rgb[2]);
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  return [0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s, 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s, 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s];
};

/** OKLab → linear sRGB. */
const labLin = (L: number, a: number, b: number) => {
  'worklet';
  const l = Math.pow(L + 0.3963377774 * a + 0.2158037573 * b, 3);
  const m = Math.pow(L - 0.1055613458 * a - 0.0638541728 * b, 3);
  const s = Math.pow(L - 0.0894841775 * a - 1.291485548 * b, 3);
  return [4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s, -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s, -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s];
};

/** Linear sRGB → what the shader must output (P3-encoded on iOS). */
const outOf = (r: number, g: number, b: number) => {
  'worklet';
  if (!P3) return [enc(r), enc(g), enc(b)];
  return [enc(0.8225 * r + 0.1774 * g), enc(0.0332 * r + 0.9669 * g), enc(0.0171 * r + 0.0724 * g + 0.9108 * b)];
};

type BiomeLight = { fills: number[][]; src: number[][]; sky: number[][]; native: number };

const LIGHTS: BiomeLight[] = BIOMES.map((b) => {
  const fills = b.fills.map((h) => toLab(hexRgb(h)));
  const src = b.fills.map((h) => {
    const c = hexRgb(h);
    return outOf(lin(c[0]), lin(c[1]), lin(c[2]));
  });
  while (src.length < 6) src.push([9, 9, 9]);
  return { fills, src, sky: b.sky.map((h) => toLab(hexRgb(h))), native: b.native };
});
/** Blue hour (20:30) is a sky of its own, not a darker cream: top, mid, horizon. */
const BLUE_SKY = ['#1E2850', '#27335E', '#3A4675'].map((h) => toLab(hexRgb(h)));
/** Afterglow offsets (OKLab a, b) per sky stop, top → horizon, at the middle of the blue-hour mix. */
const GLOW_A = [0.035, 0.05, 0.07];
const GLOW_B = [-0.02, 0, 0.035];
const HK_D = HOUR_KEYS.map((k) => k[0]);
const HK_R = HOUR_KEYS.map((k) => k[1]);

/** The hour row (0 07:00 … 4 20:30) at d miles walked. */
export const hourRow = (d: number) => {
  'worklet';
  if (d <= HK_D[0]) return HK_R[0];
  for (let i = 1; i < HK_D.length; i++) {
    if (d <= HK_D[i]) {
      const k = (d - HK_D[i - 1]) / (HK_D[i] - HK_D[i - 1]);
      return HK_R[i - 1] + (HK_R[i] - HK_R[i - 1]) * k;
    }
  }
  return HK_R[HK_R.length - 1];
};

const lightAt = (row: number) => {
  'worklet';
  const i = Math.min(Math.floor(row), HOURS.length - 2);
  const k = Math.min(Math.max(row - i, 0), 1);
  const a = HOURS[i];
  const b = HOURS[i + 1];
  return [a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k, a[3] + (b[3] - a[3]) * k];
};

const relight = (lab: number[], L: number[], N: number[]) => {
  'worklet';
  const cs = L[3] / N[3];
  return labLin(lab[0] + L[0] - N[0], lab[1] * cs + L[1] - N[1], lab[2] * cs + L[2] - N[2]);
};

export type Palette = {
  n: number;
  src: number[][];
  dst: number[][];
  sky: number[][]; // top, mid, horizon (shader output)
  horizon: string; // the horizon colour as an sRGB paint
};

/** Biome `bi`'s fills and sky relit for `row`. */
export const paletteAt = (bi: number, row: number): Palette => {
  'worklet';
  const B = LIGHTS[bi];
  const L = lightAt(row);
  const N = HOURS[B.native];
  const dst: number[][] = [];
  for (let i = 0; i < 6; i++) {
    if (i < B.fills.length) {
      const c = relight(B.fills[i], L, N);
      dst.push(outOf(c[0], c[1], c[2]));
    } else dst.push([0, 0, 0]);
  }
  const sky: number[][] = [];
  let horizon = '#ffffff';
  const blue = Math.min(Math.max(row - 3, 0), 1);
  const L3 = lightAt(Math.min(row, 3));
  for (let i = 0; i < 3; i++) {
    let c = relight(B.sky[i], blue > 0 ? L3 : L, N);
    if (blue > 0) {
      // mix towards blue hour in OKLab
      const r0 = Math.cbrt(0.4122214708 * c[0] + 0.5363325363 * c[1] + 0.0514459929 * c[2]);
      const m0 = Math.cbrt(0.2119034982 * c[0] + 0.6806995451 * c[1] + 0.1073969566 * c[2]);
      const s0 = Math.cbrt(0.0883024619 * c[0] + 0.2817188376 * c[1] + 0.6299787005 * c[2]);
      const lab = [0.2104542553 * r0 + 0.793617785 * m0 - 0.0040720468 * s0, 1.9779984951 * r0 - 2.428592205 * m0 + 0.4505937099 * s0, 0.0259040371 * r0 + 0.7827717662 * m0 - 0.808675766 * s0];
      const t = BLUE_SKY[i];
      // afterglow: half-way the sky passes through rose and a peach horizon, not the grey a straight mix gives
      const glow = Math.sin(Math.PI * blue);
      c = labLin(lab[0] + (t[0] - lab[0]) * blue, lab[1] + (t[1] - lab[1]) * blue + GLOW_A[i] * glow, lab[2] + (t[2] - lab[2]) * blue + GLOW_B[i] * glow);
    }
    sky.push(outOf(c[0], c[1], c[2]));
    if (i === 2) horizon = `rgb(${Math.round(enc(c[0]) * 255)},${Math.round(enc(c[1]) * 255)},${Math.round(enc(c[2]) * 255)})`;
  }
  return { n: B.fills.length, src: B.src, dst, sky, horizon };
};
