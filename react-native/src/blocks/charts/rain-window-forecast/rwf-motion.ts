// Rain Window Forecast — the loop. Helpers first: a worklet captures only what is declared above it.

import { HOURS } from './rwf-theme';

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export const LOOP = 10;
export const POINTS = 16;
/** how long the icon takes to turn into another hour's (s) */
export const MORPH = 0.8;
/** the glow behind the forecast for each hour */
export const GLOW = [1, 0.9, 0.55, 0.35] as const;

export type Weather = { sun: number; moon: number; rain: number; glow: number };

/** the icon between hour a and hour b, k 0 … 1 */
export function weatherBetween(a: number, b: number, k: number): Weather {
  'worklet';
  const A = HOURS[a];
  const B = HOURS[b];
  // the sun sinks first, then the moon rises: they never show together
  return {
    sun: A.sun + (B.sun - A.sun) * easeInOut(clamp01(B.sun > A.sun ? k * 2 - 1 : k * 2)),
    moon: A.moon + (B.moon - A.moon) * easeInOut(clamp01(B.moon > A.moon ? k * 2 - 1 : k * 2)),
    rain: A.rain + (B.rain - A.rain) * easeInOut(k),
    glow: GLOW[a] + (GLOW[b] - GLOW[a]) * easeInOut(k),
  };
}

export const T = { six: 1.4, wipe: [4.0, 4.9], seven: 5.8, now: 8.0, wet: [1.8, 3.4], dry: [8.0, 9.6] } as const;

/** a point on the loop's wipe, s 0 … 1, in shares of the screen: a sweep across the middle */
function strokeAt(s: number) {
  'worklet';
  return { x: 0.16 + 0.68 * s, y: 0.36 + 0.06 * Math.sin(s * Math.PI * 2) + 0.05 * s };
}

export type Frame = {
  weather: Weather;
  hour: number; // the hour the readings show
  selected: number; // the highlighted card, -1 none
  wet: number;
  wipe: number[];
  finger: number;
  fingerX: number;
  fingerY: number;
  fingerCard: number; // -1 the wipe, else a card
  fingerPress: number;
};

export function frameAt(t: number, w = 402, h = 874): Frame {
  'worklet';
  let weather = weatherBetween(0, 0, 0);
  let hour = 0;
  let selected = -1;
  if (t >= T.six && t < T.seven) {
    weather = weatherBetween(0, 2, seg(t, T.six + 0.05, T.six + 0.05 + MORPH));
    hour = 2;
    selected = 2;
  } else if (t >= T.seven && t < T.now) {
    weather = weatherBetween(2, 3, seg(t, T.seven + 0.05, T.seven + 0.05 + MORPH));
    hour = 3;
    selected = 3;
  } else if (t >= T.now) {
    weather = weatherBetween(3, 0, seg(t, T.now + 0.05, T.now + 0.05 + MORPH));
  }
  const wet = easeInOut(seg(t, T.wet[0], T.wet[1])) * (1 - easeInOut(seg(t, T.dry[0], T.dry[1])));
  // the wipe: 16 points timed to the eased finger
  const wipe: number[] = [];
  for (let i = 0; i < 16; i++) {
    const s = i / 15;
    let lo = 0;
    let hi = 1;
    for (let k = 0; k < 14; k++) {
      const mid = (lo + hi) / 2;
      if (easeInOut(mid) < s) lo = mid;
      else hi = mid;
    }
    const at = T.wipe[0] + (T.wipe[1] - T.wipe[0]) * lo;
    const p = strokeAt(s);
    wipe.push(p.x * w, p.y * h, t >= at ? t - at : -1);
  }
  // the scripted finger
  let finger = 0;
  let fingerX = 0;
  let fingerY = 0;
  let fingerCard = -1;
  let fingerPress = 0;
  const taps: [number, number][] = [[T.six, 2], [T.seven, 3], [T.now, 0]];
  for (let k = 0; k < 3; k++) {
    const [at, card] = taps[k];
    const f = easeOut(seg(t, at - 0.2, at - 0.05)) * (1 - easeOut(seg(t, at + 0.15, at + 0.32)));
    if (f > finger) {
      finger = f;
      fingerCard = card;
      fingerPress = easeOut(seg(t, at - 0.05, at + 0.05)) * (1 - easeOut(seg(t, at + 0.1, at + 0.22)));
    }
  }
  const onWipe = easeOut(seg(t, T.wipe[0] - 0.18, T.wipe[0] - 0.03)) * (1 - easeOut(seg(t, T.wipe[1], T.wipe[1] + 0.18)));
  if (onWipe > finger) {
    finger = onWipe;
    fingerCard = -1;
    fingerPress = onWipe;
    const p = strokeAt(easeInOut(seg(t, T.wipe[0], T.wipe[1])));
    fingerX = p.x * w;
    fingerY = p.y * h;
  }
  return { weather, hour, selected, wet, wipe, finger, fingerX, fingerY, fingerCard, fingerPress };
}
