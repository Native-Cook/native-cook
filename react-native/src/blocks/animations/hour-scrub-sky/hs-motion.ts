// Hour Scrub Sky: the autoplay timeline (spec §4) as a pure function of the loop time, and the sky's colour maths that
// the shader and the inks share. One value drives everything: h, the hours since 12:00 (0..24). Times are in seconds.

import { CLOUD_DAY, CLOUD_NIGHT, CONDITIONS, DAY, L, NIGHT, SPRING, SUN, TEMPS, type Stops } from './hs-theme';

export const LOOP = 12;

export const clamp01 = (v: number) => {
  'worklet';
  return Math.min(Math.max(v, 0), 1);
};
/** 0 before a, 1 after b. */
export const seg = (t: number, a: number, b: number) => {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
};
export const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
export const smooth = (a: number, b: number, v: number) => {
  'worklet';
  const k = Math.min(Math.max((v - a) / (b - a), 0), 1);
  return k * k * (3 - 2 * k);
};
export const easeOut = (k: number) => {
  'worklet';
  return 1 - (1 - k) * (1 - k) * (1 - k);
};
export const easeIn = (k: number) => {
  'worklet';
  return k * k * k;
};
export const easeInOut = (k: number) => {
  'worklet';
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
};

/**
 * A damped spring (mass 1) released at time 0 from `from` towards `to` with initial velocity v0 (units/s): its
 * position at time t. Closed form, so the loop stays a pure function of time.
 */
export const springAt = (t: number, from: number, to: number, v0: number, d: number, k: number) => {
  'worklet';
  if (t <= 0) return from;
  const x0 = from - to;
  const w0 = Math.sqrt(k);
  const z = d / (2 * w0);
  if (z < 1) {
    const wd = w0 * Math.sqrt(1 - z * z);
    const e = Math.exp(-z * w0 * t);
    return to + e * (x0 * Math.cos(wd * t) + ((v0 + z * w0 * x0) / wd) * Math.sin(wd * t));
  }
  const s = Math.sqrt(Math.max(z * z - 1, 1e-6));
  const r1 = -w0 * (z - s);
  const r2 = -w0 * (z + s);
  const c2 = (v0 - r1 * x0) / (r2 - r1);
  const c1 = x0 - c2;
  return to + c1 * Math.exp(r1 * t) + c2 * Math.exp(r2 * t);
};

// ---------------------------------------------------------------------------------------------------------------
// colour: sRGB <-> OKLab, keyframes interpolated in OKLab (done on the UI thread; the shader gets sRGB stops)

const hexRgb = (h: string): [number, number, number] => [parseInt(h.slice(1, 3), 16) / 255, parseInt(h.slice(3, 5), 16) / 255, parseInt(h.slice(5, 7), 16) / 255];

export const lin = (c: number) => {
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
  return [
    0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
  ];
};
const fromLab = (L0: number, a: number, b: number): [number, number, number] => {
  'worklet';
  const l = Math.pow(L0 + 0.3963377774 * a + 0.2158037573 * b, 3);
  const m = Math.pow(L0 - 0.1055613458 * a - 0.0638541728 * b, 3);
  const s = Math.pow(L0 - 0.0894841775 * a - 1.291485548 * b, 3);
  return [
    enc(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
    enc(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
    enc(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s),
  ];
};

type LabKeys = { h: number[]; c: number[][][] };
const labKeys = (keys: [number, string[]][]): LabKeys => ({ h: keys.map((k) => k[0]), c: keys.map((k) => k[1].map((x) => toLab(hexRgb(x)))) });
const DAY_K = labKeys(DAY as [number, Stops][]);
const NIGHT_K = labKeys(NIGHT as [number, Stops][]);
const CLOUD_K = labKeys(CLOUD_DAY as [number, string[]][]);
const SUN_K = labKeys(SUN.map(([h, c]) => [h, [c]]));

/** The keyframed colours at hour h: sRGB triples, one per entry of the keyframe. */
const keyAt = (k: LabKeys, h: number): number[][] => {
  'worklet';
  const hh = Math.min(Math.max(h, 0), 24);
  let i = 0;
  while (i < k.h.length - 2 && hh > k.h[i + 1]) i++;
  const f = clamp01((hh - k.h[i]) / (k.h[i + 1] - k.h[i]));
  const a = k.c[i];
  const b = k.c[i + 1];
  const out: number[][] = [];
  for (let j = 0; j < a.length; j++) out.push(fromLab(mix(a[j][0], b[j][0], f), mix(a[j][1], b[j][1], f), mix(a[j][2], b[j][2], f)));
  return out;
};
export const dayStops = (h: number) => {
  'worklet';
  return keyAt(DAY_K, h);
};
export const nightStops = (h: number) => {
  'worklet';
  return keyAt(NIGHT_K, h);
};
export const cloudDay = (h: number) => {
  'worklet';
  return keyAt(CLOUD_K, h);
};
export const sunColour = (h: number) => {
  'worklet';
  return keyAt(SUN_K, h)[0];
};
export const MAROON = hexRgb('#83213A');

// ---------------------------------------------------------------------------------------------------------------
// the sky's geometry

/** Hermite between (x0, y0, slope m0) and (x1, y1, slope m1), at x. */
const herm = (x: number, x0: number, x1: number, y0: number, y1: number, m0: number, m1: number) => {
  'worklet';
  const d = x1 - x0;
  const s = (x - x0) / d;
  const s2 = s * s;
  const s3 = s2 * s;
  return (2 * s3 - 3 * s2 + 1) * y0 + (s3 - 2 * s2 + s) * d * m0 + (-2 * s3 + 3 * s2) * y1 + (s3 - s2) * d * m1;
};

/**
 * The twilight front's y (pt): above it is night. Dusk brings it down fast through the zenith, then slowly through
 * the type (250 pt/h as it crosses the numeral at 18:30) and leaves a 120 pt ember line on the horizon at 19:00. Dawn
 * runs it back up. Outside the twilights it sits off screen, where day and night are the same colour anyway.
 */
export const frontY = (h: number) => {
  'worklet';
  if (h < 5.5) return -210;
  if (h < 6.5) return herm(h, 5.5, 6.5, -210, 536, 1300, 250);
  if (h < 7.0) return 536 + 250 * (h - 6.5);
  if (h < 7.25) return 661 + 960 * (h - 7.0);
  if (h < 17.25) return 1400;
  if (h < 19) return mix(1100, -210, (h - 17.25) / 1.75);
  return -210;
};
/** Half-width of the front's soft edge: 210 pt at dusk, 130 at dawn. */
export const frontW = (h: number) => {
  'worklet';
  return h < 12 ? 210 : 130;
};
/** Band riding the front: maroon under the dusk edge, a salmon leading band at dawn. [strength, centre offset, sigma] */
export const bandAt = (h: number) => {
  'worklet';
  if (h > 5.4 && h < 7.25) return { k: 0.35, dy: 60, s: 120, dawn: 0 };
  if (h > 17.25 && h < 19) return { k: 0.9 * (1 - seg(h, 18.7, 19)), dy: -50, s: 40, dawn: 1 };
  return { k: 0, dy: 0, s: 1, dawn: 0 };
};
/** Night factor at y: 1 above the front. */
export const nightAt = (y: number, yT: number, w: number) => {
  'worklet';
  return 1 - smooth(yT - w, yT + w, y);
};
export const SALMON = hexRgb('#F2A58C');
const CLOUD_N = ((a: number[], b: number[]) => [mix(b[0], a[0], 0.3), mix(b[1], a[1], 0.3), mix(b[2], a[2], 0.3)])(hexRgb(CLOUD_NIGHT[0]), hexRgb(CLOUD_NIGHT[1]));

/** The sun's centre (pt): an arc that crosses the haze line (y 540) at 06:30 and 18:30. */
export const sunAt = (h: number) => {
  'worklet';
  const a = ((h - 0.5) / 12) * Math.PI;
  return { x: 201 + 135 * Math.sin(a), y: 540 - 450 * Math.cos(a) };
};

/** The four-stop vertical gradient at y (fraction of the screen height), as the shader draws it. */
const grad = (s: number[][], f: number) => {
  'worklet';
  if (f <= 0.35) {
    const k = clamp01(f / 0.35);
    return [mix(s[0][0], s[1][0], k), mix(s[0][1], s[1][1], k), mix(s[0][2], s[1][2], k)];
  }
  if (f <= 0.62) {
    const k = smooth(0.35, 0.62, f);
    return [mix(s[1][0], s[2][0], k), mix(s[1][1], s[2][1], k), mix(s[1][2], s[2][2], k)];
  }
  const k = smooth(0.62, 0.8, f);
  return [mix(s[2][0], s[3][0], k), mix(s[2][1], s[3][1], k), mix(s[2][2], s[3][2], k)];
};

/** Everything the sky needs for hour h that does not depend on the pixel. */
export const skyParams = (h: number) => {
  'worklet';
  return { day: dayStops(h), night: nightStops(h), yT: frontY(h), w: frontW(h), band: bandAt(h), cloud: cloudDay(h) };
};
export type SkyParams = ReturnType<typeof skyParams>;

/** The sky (without clouds or sun) at y: sRGB 0..1. Mirrors the gradient part of SKY in hs-shaders.ts. */
export const skyAtP = (y: number, sp: SkyParams, ch: number) => {
  'worklet';
  const f = clamp01(y / ch);
  const day = grad(sp.day, f);
  const night = grad(sp.night, f);
  const n = nightAt(y, sp.yT, sp.w);
  const bz = (y - (sp.yT + sp.band.dy)) / sp.band.s;
  const b = sp.band.k * Math.exp(-bz * bz);
  const bc = sp.band.dawn > 0.5 ? SALMON : MAROON;
  const c = [mix(day[0], bc[0], b), mix(day[1], bc[1], b), mix(day[2], bc[2], b)];
  const sky = [mix(c[0], night[0], n), mix(c[1], night[1], n), mix(c[2], night[2], n)];
  // the cloud banks' average veil (upper bank above ~250 pt, lower bank below ~600 pt): the inks read the light the
  // eye sees behind the text, not the bare gradient
  const cover = y > 560 ? 0.8 * smooth(560, 700, y) : 0.6 * (1 - smooth(170, 280, y));
  const cd = [mix(sp.cloud[1][0], sp.cloud[0][0], 0.3), mix(sp.cloud[1][1], sp.cloud[0][1], 0.3), mix(sp.cloud[1][2], sp.cloud[0][2], 0.3)];
  const cc = [mix(cd[0], CLOUD_N[0], n), mix(cd[1], CLOUD_N[1], n), mix(cd[2], CLOUD_N[2], n)];
  return [mix(sky[0], cc[0], cover), mix(sky[1], cc[1], cover), mix(sky[2], cc[2], cover)];
};
export const skyAt = (y: number, h: number, ch: number) => {
  'worklet';
  return skyAtP(y, skyParams(h), ch);
};

/** CIE L* (0..1) of an sRGB colour. */
export const lightness = (c: number[]) => {
  'worklet';
  const Y = 0.2126 * lin(c[0]) + 0.7152 * lin(c[1]) + 0.0722 * lin(c[2]);
  return (Y > 0.008856 ? 116 * Math.cbrt(Y) - 16 : 903.3 * Y) / 100;
};

/** Small elements that flip their ink one by one (y of their centre, pt). The sentence and the numeral use yInk. */
export const INK_Y = [30, 84, 605, 651, 675, 705, 745, 825] as const;
export const INK_I = { status: 0, chip: 1, arrow: 2, loc: 3, detail: 4, pill: 5, controls: 6, tab: 7 } as const;
export const INK_LO = 0.42;
export const INK_HI = 0.5;

/**
 * The terminator for the type: the y where the sky's lightness crosses 0.46 (light ink above it, dark below).
 * -200 when the whole sky is light, ch + 200 when it is all dark. A 16 pt scan from the top, then five bisection
 * steps (0.5 pt).
 */
export const yInkAt = (sp: SkyParams, ch: number) => {
  'worklet';
  if (lightness(skyAtP(0, sp, ch)) >= 0.46) return -200;
  // the first crossing from the top (the terminator), then bisect inside that 16 pt step
  let prev = 0;
  for (let y = 16; y <= ch; y += 16) {
    if (lightness(skyAtP(y, sp, ch)) >= 0.46) {
      let a = prev;
      let b = y;
      for (let i = 0; i < 5; i++) {
        const m = (a + b) / 2;
        if (lightness(skyAtP(m, sp, ch)) < 0.46) a = m;
        else b = m;
      }
      return (a + b) / 2;
    }
    prev = y;
  }
  return ch + 200;
};

// ---------------------------------------------------------------------------------------------------------------
// data by hour

export const tempAt = (h: number) => {
  'worklet';
  const hh = Math.min(Math.max(h, 0), 24);
  let i = 0;
  while (i < TEMPS.length - 2 && hh > TEMPS[i + 1][0]) i++;
  const a = TEMPS[i];
  const b = TEMPS[i + 1];
  return mix(a[1], b[1], clamp01((hh - a[0]) / (b[0] - a[0])));
};

/**
 * Condition labels: for each of the three, its vertical offset in line units (0 shown, -1 rolled out above, +1
 * waiting below, 9 not involved). A change rolls over 0.15 h either side of its hour (never across a half-hour
 * detent, so a released scrub never rests half-rolled), and scrubbing back rolls it back.
 */
export const conditionRoll = (h: number): [number, number, number] => {
  'worklet';
  // 9 = not involved (not drawn)
  const out: [number, number, number] = [9, 9, 9];
  let cur = CONDITIONS[0][1];
  for (let i = 1; i < CONDITIONS.length; i++) if (h >= CONDITIONS[i][0] - 0.15) cur = CONDITIONS[i][1];
  // find the nearest change
  for (let i = 1; i < CONDITIONS.length; i++) {
    const hk = CONDITIONS[i][0];
    if (Math.abs(h - hk) < 0.15) {
      const k = easeInOut(seg(h, hk - 0.15, hk + 0.15));
      const from = CONDITIONS[i - 1][1];
      const to = CONDITIONS[i][1];
      out[from] = -k;
      out[to] = 1 - k;
      return out;
    }
  }
  out[cur] = 0;
  return out;
};
/** Index of the condition shown at h (for the location row's text). */
export const conditionAt = (h: number) => {
  'worklet';
  let cur = CONDITIONS[0][1];
  for (let i = 1; i < CONDITIONS.length; i++) if (h >= CONDITIONS[i][0]) cur = CONDITIONS[i][1];
  return cur;
};

/** The hour pill's label index: 0 = 12 PM, 1 = 1 PM … 23 = Tom 11 AM. */
export const pillIndex = (h: number) => {
  'worklet';
  return Math.min(Math.max(Math.round(h), 0), 23);
};
export const pillLabel = (i: number) => {
  const clock = (12 + i) % 24;
  const tom = 12 + i >= 24;
  const hr = clock % 12 === 0 ? 12 : clock % 12;
  return `${tom ? 'Tom ' : ''}${hr} ${clock < 12 ? 'AM' : 'PM'}`;
};

// ---------------------------------------------------------------------------------------------------------------
// the autoplay timeline

/**
 * Hermite knots of h(t): [t, h, dh/dt]. Play at 0.3 s; the afternoon at ~2.5 h/s; dusk slows to 0.7 h/s so the
 * terminator takes ~0.45 s to cross the numeral round 4.0 s (the poster: sun half-sunk on the haze line); the night at
 * up to 6.5 h/s (4.6 → 6.6 s); dawn at ~1 h/s; the morning at 2.9 h/s; noon again at 10.4 s.
 */
const KNOTS: [number, number, number][] = [
  [0.3, 0, 0],
  [0.6, 0.4, 2.6],
  [2.4, 5.0, 2.4],
  [3.0, 5.75, 1.2],
  [4.0, 6.5, 0.7],
  [4.6, 7.25, 2.0],
  [5.55, 12.25, 6.5],
  [6.6, 17.25, 1.4],
  [8.3, 19.0, 1.0],
  [8.7, 19.8, 2.9],
  [10.1, 23.6, 2.9],
  [10.4, 24, 0],
];

/** h and dh/dt at loop time t. */
export const hourAt = (t: number) => {
  'worklet';
  if (t <= KNOTS[0][0]) return { h: 0, rate: 0 };
  const last = KNOTS[KNOTS.length - 1];
  if (t >= last[0]) return { h: 24, rate: 0 };
  let i = 0;
  while (t > KNOTS[i + 1][0]) i++;
  const [t0, h0, v0] = KNOTS[i];
  const [t1, h1, v1] = KNOTS[i + 1];
  const dt = t1 - t0;
  const s = (t - t0) / dt;
  const s2 = s * s;
  const s3 = s2 * s;
  const h = (2 * s3 - 3 * s2 + 1) * h0 + (s3 - 2 * s2 + s) * dt * v0 + (-2 * s3 + 3 * s2) * h1 + (s3 - s2) * dt * v1;
  const dh = ((6 * s2 - 6 * s) * h0 + (3 * s2 - 4 * s + 1) * dt * v0 + (-6 * s2 + 6 * s) * h1 + (3 * s2 - 2 * s) * dt * v1) / dt;
  return { h, rate: dh };
};

/** The loop time at which the timeline reaches hour h (for the play disc: run on from where the finger left it). */
export const timeForHour = (h: number) => {
  'worklet';
  if (h <= 0) return 0.2;
  let a = 0.3;
  let b = 10.4;
  for (let i = 0; i < 30; i++) {
    const m = (a + b) / 2;
    if (hourAt(m).h < h) a = m;
    else b = m;
  }
  return (a + b) / 2;
};

/** Everything the screen draws, for one moment. */
export type Frame = {
  h: number; // hours since 12:00 (the sky)
  rate: number; // dh/dt (h/s), for trails and smear
  hp: number; // the playhead's hour (glides home on its own at the end)
  cs: number; // seconds since the sentence started to fold away (0..0.5)
  rs: number; // seconds since the sentence started to come back (0..0.8)
  pill: number; // hour pill grown out of its tick, 0..1 (pop overshoot allowed)
  pillDark: number; // pill colour, red tick (0) → pill (1)
  disc: number; // play disc scale
  gPlay: number; // glyph scales
  gPause: number;
  gReset: number;
  resetRot: number; // deg
  flare: number; // silver lining before the sun shows (0..1)
  sway: number; // wind sway / breathing clock (s)
};

/** Loop times at which the sun leaves (morning: enters) the upper cloud bank; tuned on the plates. */
export const FLARE = [1.4, 9.45] as const;
/** A 240 ms flare of the cloud rims round the hidden sun. */
const flareAt = (t: number) => {
  'worklet';
  const p = (a: number) => (t >= a && t < a + 0.36 ? Math.sin(Math.PI * seg(t, a, a + 0.36)) : 0);
  return Math.max(p(FLARE[0]), p(FLARE[1]));
};

export const frameAt = (tIn: number): Frame => {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const { h, rate } = hourAt(t);
  const hp = t < 11.2 ? h : t < 11.8 ? Math.max(0, springAt(t - 11.2, 24, 0, 0, SPRING.glide.d, SPRING.glide.k)) : 0;
  const cs = t < 0.3 ? 0 : Math.min(t - 0.3, 0.5);
  const rs = t < 10.6 ? 0 : Math.min(t - 10.6, 0.8);
  let pill = 0;
  if (t >= 0.3 && t < 11.2) pill = springAt(t - 0.3, 0, 1, 0, SPRING.pop.d, SPRING.pop.k);
  else if (t >= 11.2 && t < 11.8) pill = Math.max(0, springAt(t - 11.2, 1, 0, 0, SPRING.glide.d, SPRING.glide.k));
  const pillDark = t < 11.2 ? seg(t, 0.3, 0.46) : 1 - seg(t, 11.2, 11.36);
  let disc = 1;
  if (t >= 0.2 && t < 0.3) disc = mix(1, 0.9, easeIn(seg(t, 0.2, 0.3)));
  else if (t >= 0.3 && t < 1.2) disc = springAt(t - 0.3, 0.9, 1, 0, SPRING.pop.d, SPRING.pop.k);
  else if (t >= 10.4 && t < 10.7) disc = 1 + 0.06 * Math.sin(Math.PI * seg(t, 10.4, 10.7));
  // glyphs: play → pause at 0.3, pause → reset at 10.4, reset → play at 11.3
  const gPlay = t < 0.2 ? 1 : t < 0.3 ? 1 - easeIn(seg(t, 0.2, 0.3)) : t < 11.3 ? 0 : clamp01(springAt(t - 11.3, 0, 1, 0, SPRING.pop.d, SPRING.pop.k));
  const gPause = t < 0.3 ? 0 : t < 10.4 ? clamp01(springAt(t - 0.3, 0, 1, 0, SPRING.pop.d, SPRING.pop.k)) : t < 10.5 ? 1 - easeIn(seg(t, 10.4, 10.5)) : 0;
  const gReset = t < 10.4 ? 0 : t < 11.2 ? clamp01(springAt(t - 10.4, 0, 1, 0, SPRING.snap.d, SPRING.snap.k)) : t < 11.3 ? 1 - easeIn(seg(t, 11.2, 11.3)) : 0;
  const resetRot = t < 10.4 ? -90 : t < 11.3 ? springAt(t - 10.4, -90, 0, 0, SPRING.snap.d, SPRING.snap.k) : 0;
  return { h, rate, hp, cs, rs, pill, pillDark, disc, gPlay, gPause, gReset, resetRot, flare: flareAt(t), sway: t };
};

/** Position of a tick or the playhead (pt) for an hour. */
export const tickX = (h: number) => {
  'worklet';
  return L.ticks.x0 + ((L.ticks.x1 - L.ticks.x0) * h) / 24;
};
