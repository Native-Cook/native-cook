// Shape Shift Viewfinder: the Dynamic background colour. The mean colour of the picture under the outline, moved to
// OKLCH, chroma × 0.8 and lightness held in 0.50–0.70, so the stage always reads as a colour field, never as mud.

import { hexOf, rgbOf } from './ssv-motion';

const toLin = (c: number) => {
  'worklet';
  const v = c / 255;
  return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
};
const toGamma = (v: number) => {
  'worklet';
  const c = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(Math.max(v, 0), 1 / 2.4) - 0.055;
  return Math.min(Math.max(c, 0), 1) * 255;
};

export const oklabOf = (r8: number, g8: number, b8: number): [number, number, number] => {
  'worklet';
  const r = toLin(r8);
  const g = toLin(g8);
  const b = toLin(b8);
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  return [
    0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
  ];
};

export const hexOfOklab = (L: number, a: number, b: number) => {
  'worklet';
  const l = Math.pow(L + 0.3963377774 * a + 0.2158037573 * b, 3);
  const m = Math.pow(L - 0.1055613458 * a - 0.0638541728 * b, 3);
  const s = Math.pow(L - 0.0894841775 * a - 1.291485548 * b, 3);
  return hexOf(
    toGamma(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
    toGamma(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
    toGamma(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s),
  );
};

/** The stage colour for a mean picture colour (0–255 channels). */
export const dynamicTone = (r: number, g: number, b: number) => {
  'worklet';
  const lab = oklabOf(r, g, b);
  const L = Math.min(Math.max(lab[0], 0.5), 0.7);
  return hexOfOklab(L, lab[1] * 0.8, lab[2] * 0.8);
};

/** OKLab lightness of a hex colour (0–1). */
export const lightnessOf = (hex: string) => {
  'worklet';
  const c = rgbOf(hex);
  return oklabOf(c[0], c[1], c[2])[0];
};

/** The colour with its OKLab lightness raised by dL (hue and chroma kept, clipped to sRGB). */
export const liftTone = (hex: string, dL: number) => {
  'worklet';
  const c = rgbOf(hex);
  const lab = oklabOf(c[0], c[1], c[2]);
  return hexOfOklab(Math.min(Math.max(lab[0] + dL, 0), 1), lab[1], lab[2]);
};

/** The stage colour's shadow tone: its hue at 25 % lightness. */
export const shadowTone = (hex: string) => {
  'worklet';
  const c = rgbOf(hex);
  const lab = oklabOf(c[0], c[1], c[2]);
  return hexOfOklab(0.25, lab[1] * 0.55, lab[2] * 0.55);
};

const labCurve = (v: number) => {
  'worklet';
  return v > 216 / 24389 ? Math.cbrt(v) : ((24389 / 27) * v + 16) / 116;
};

/** CIELAB from sRGB, using the sRGB D65 reference white (no chromatic adaptation). */
const cieLabOf = (r8: number, g8: number, b8: number): [number, number, number] => {
  'worklet';
  const r = toLin(r8);
  const g = toLin(g8);
  const b = toLin(b8);
  const x = labCurve((0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047);
  const y = labCurve(0.2126729 * r + 0.7151522 * g + 0.072175 * b);
  const z = labCurve((0.0193339 * r + 0.119192 * g + 0.9503041 * b) / 1.08883);
  return [116 * y - 16, 500 * (x - y), 200 * (y - z)];
};

/** CIEDE2000 with unit parametric factors; handles achromatic colours and the hue wrap explicitly. */
const deltaE00 = (p: [number, number, number], q: [number, number, number]) => {
  'worklet';
  const rad = Math.PI / 180;
  const cMean = (Math.hypot(p[1], p[2]) + Math.hypot(q[1], q[2])) / 2;
  const cMean7 = Math.pow(cMean, 7);
  const g = 0.5 * (1 - Math.sqrt(cMean7 / (cMean7 + 6103515625))); // 25^7
  const a1 = (1 + g) * p[1];
  const a2 = (1 + g) * q[1];
  const c1 = Math.hypot(a1, p[2]);
  const c2 = Math.hypot(a2, q[2]);
  let h1 = c1 === 0 ? 0 : Math.atan2(p[2], a1) / rad;
  let h2 = c2 === 0 ? 0 : Math.atan2(q[2], a2) / rad;
  if (h1 < 0) h1 += 360;
  if (h2 < 0) h2 += 360;

  let dh = h2 - h1;
  if (c1 * c2 === 0) dh = 0;
  else if (dh > 180) dh -= 360;
  else if (dh < -180) dh += 360;
  const dL = q[0] - p[0];
  const dC = c2 - c1;
  const dH = 2 * Math.sqrt(c1 * c2) * Math.sin((dh * rad) / 2);
  const lMean = (p[0] + q[0]) / 2;
  const cpMean = (c1 + c2) / 2;
  let hMean = h1 + h2;
  if (c1 * c2 !== 0) {
    if (Math.abs(h1 - h2) <= 180) hMean /= 2;
    else hMean = (hMean + (hMean < 360 ? 360 : -360)) / 2;
  }

  const t =
    1 -
    0.17 * Math.cos((hMean - 30) * rad) +
    0.24 * Math.cos(2 * hMean * rad) +
    0.32 * Math.cos((3 * hMean + 6) * rad) -
    0.2 * Math.cos((4 * hMean - 63) * rad);
  const lOffset2 = (lMean - 50) * (lMean - 50);
  const sl = 1 + (0.015 * lOffset2) / Math.sqrt(20 + lOffset2);
  const sc = 1 + 0.045 * cpMean;
  const sh = 1 + 0.015 * cpMean * t;
  const cpMean7 = Math.pow(cpMean, 7);
  const theta = 30 * Math.exp(-Math.pow((hMean - 275) / 25, 2));
  const rt = -2 * Math.sqrt(cpMean7 / (cpMean7 + 6103515625)) * Math.sin(2 * theta * rad);
  const dl = dL / sl;
  const dc = dC / sc;
  const dhScaled = dH / sh;
  return Math.sqrt(Math.max(0, dl * dl + dc * dc + dhScaled * dhScaled + rt * dc * dhScaled));
};

/** Perceptual colour difference ΔE00, from sRGB colours under the D65 reference white. */
export const deltaE = (x: string, y: string) => {
  'worklet';
  const p = rgbOf(x);
  const q = rgbOf(y);
  return deltaE00(cieLabOf(p[0], p[1], p[2]), cieLabOf(q[0], q[1], q[2]));
};
