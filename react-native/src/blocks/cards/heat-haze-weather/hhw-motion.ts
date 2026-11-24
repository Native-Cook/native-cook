// Heat Haze Weather — the loop, the forecast's numbers and the scripted finger, in closed form.
//
// Everything on screen is a function of one number, the hour H (12 … 21, a float). The film moves H
// with the hour slider; the scene shader (hhw-shader.ts) reads the sun's place, the heat and the dusk
// derived from H here. A Swift or Kotlin port needs only this file and the shader.

export const LOOP = 10;

/** the slider's range, hours (12 PM … 9 PM) */
export const H_MIN = 12;
export const H_MAX = 21;
/** the hour the screen rests at, the hottest hour the film visits, and the evening it ends on */
export const H_REST = 13;
export const H_PEAK = 15.5;
export const H_EVENING = 20;

/** the loop's phases, seconds */
export const T = {
  /** the slider is dragged from 1 PM to 3:30 PM: the heat builds, the temperature counts up */
  toPeak: [1.2, 3.4],
  /** the ghost finger rests on the road's mirage, adding a local shimmer */
  shimmer: [3.7, 4.75],
  /** then dragged to 8 PM: the haze calms and the colours cool */
  toEvening: [5.0, 7.6],
  /** and back to 1 PM */
  back: [8.3, 9.5],
} as const;

/** the horizon, as a fraction of the screen's height */
export const HORIZON = 0.54;

/** the haze field */
export const HAZE = {
  /** the haze's widest displacement at full heat, pt */
  amp: 3.4,
  /** the band it lives in: its centre below the horizon and its e-fold, fractions of the height */
  bandOffset: 0.015,
  bandFold: 0.07,
  /** noise cell, pt (x, y): wide and flat, like rising air */
  cellX: 13,
  cellY: 6,
  /** how far the field rises in one flow period, noise cells */
  rise: 8,
  /** the flow repeats every FLOW seconds (LOOP is a whole multiple of it) */
  flow: 2.5,
  /** the finger's own shimmer: extra displacement, pt, and reach (Gaussian), pt */
  touchAmp: 7,
  touchRadius: 74,
} as const;

/** a touch hands the screen back this long after it ends, seconds */
export const IDLE = 2;
/** then the hour eases back to rest over this long, seconds */
export const RETURN = 1.2;
/** the finger's shimmer comes and goes at this rate, 1/s */
export const TOUCH_EASE = 9;
/** the slider's thumb, pt */
export const THUMB = 28;

export type Forecast = {
  /** whole hours from H_MIN to H_MAX, in order */
  hours: number[];
  /** °, one per hour */
  temps: number[];
  /** UV index 0 … 11, one per hour */
  uv: number[];
  /** sunset as an hour, e.g. 19.87 = 7:52 PM */
  sunset: number;
};

export const FORECAST: Forecast = {
  hours: [12, 13, 14, 15, 16, 17, 18, 19, 20, 21],
  temps: [33, 36, 39, 41, 41, 39, 36, 33, 31, 29],
  uv: [10, 10, 9, 8, 6, 4, 2, 1, 0, 0],
  sunset: 19.87,
};

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

/** 0 before a, 1 after b */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function smooth(v: number) {
  'worklet';
  return v * v * (3 - 2 * v);
}

export function easeInOutCubic(v: number) {
  'worklet';
  return v < 0.5 ? 4 * v * v * v : 1 - Math.pow(-2 * v + 2, 3) / 2;
}

/** the film's hour at loop time t */
export function hourAt(t: number) {
  'worklet';
  const up = easeInOutCubic(seg(t, T.toPeak[0], T.toPeak[1]));
  const eve = easeInOutCubic(seg(t, T.toEvening[0], T.toEvening[1]));
  const back = easeInOutCubic(seg(t, T.back[0], T.back[1]));
  return H_REST + (H_PEAK - H_REST) * up + (H_EVENING - H_PEAK) * eve - (H_EVENING - H_REST) * back;
}

/** a forecast column read at hour h, linear between whole hours */
export function sample(values: number[], h: number) {
  'worklet';
  const x = Math.min(Math.max(h - H_MIN, 0), values.length - 1);
  const i = Math.min(Math.floor(x), values.length - 2);
  const f = x - i;
  return values[i] + (values[i + 1] - values[i]) * f;
}

/** how far into the evening the light is, 0 … 1 */
export function duskAt(h: number, sunset: number) {
  'worklet';
  return smooth(seg(h, sunset - 3.4, sunset + 0.6));
}

/** the haze's strength, 0 … 1: the heat of the air, calmed by the evening */
export function heatAt(temp: number, dusk: number) {
  'worklet';
  return Math.pow(clamp01((temp - 29) / 12), 1.2) * (1 - 0.8 * dusk);
}

/** the sun's place in pt: high in the early afternoon, on the hills at sunset */
export function sunAt(h: number, sunset: number, width: number, height: number): [number, number] {
  'worklet';
  const e = clamp01((sunset + 0.15 - h) / (sunset + 0.15 - 12.5));
  const lift = Math.sin((e * Math.PI) / 2);
  const hy = height * HORIZON;
  return [width * (0.72 - (0.34 * (h - H_MIN)) / (H_MAX - H_MIN)), hy - lift * (hy - height * 0.13)];
}

/** a counter that rolls to the next whole number in the last part of each step */
export function rolled(v: number, from = 0.6) {
  'worklet';
  const whole = Math.floor(v);
  return whole + smooth(seg(v - whole, from, 1));
}

/** the ghost finger on the slider: how much shows, and how hard it presses */
export function sliderGhostAt(t: number): [number, number] {
  'worklet';
  let alpha = 0;
  let press = 0;
  const drags = [T.toPeak, T.toEvening, T.back];
  for (let i = 0; i < drags.length; i++) {
    const a = drags[i][0];
    const b = drags[i][1];
    alpha += smooth(seg(t, a - 0.35, a - 0.12)) * (1 - smooth(seg(t, b + 0.05, b + 0.28)));
    press += smooth(seg(t, a - 0.15, a)) * (1 - smooth(seg(t, b, b + 0.15)));
  }
  return [clamp01(alpha), clamp01(press)];
}

/** the ghost finger on the road: x, y as fractions of the screen, how much shows, and its shimmer */
export function sceneGhostAt(t: number): [number, number, number, number] {
  'worklet';
  const a = T.shimmer[0];
  const b = T.shimmer[1];
  const u = smooth(seg(t, a + 0.15, b - 0.15));
  const alpha = smooth(seg(t, a, a + 0.2)) * (1 - smooth(seg(t, b - 0.2, b)));
  const on = smooth(seg(t, a + 0.12, a + 0.4)) * (1 - smooth(seg(t, b - 0.35, b + 0.05)));
  return [0.57 - 0.14 * u, HORIZON + 0.032, alpha, on];
}
