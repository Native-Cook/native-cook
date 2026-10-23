/* The voices, the loop and the curves of the voice picker. Times in seconds. A port of WatercolorVoiceOrbsModel.swift. */

export type Voice = {
  name: string;
  line: string;
  /** Deep, mid and light colours of the orb (0…1 RGB). */
  deep: number[];
  mid: number[];
  light: number[];
  /** The direction the orb's bright side faces (y down), where in the noise it sits, and how much its middle is twisted. */
  band: number[];
  offset: number[];
  swirl: number;
};

export const VOICES: Voice[] = [
  {
    name: 'Lagoon',
    line: 'Calm and clear',
    deep: [0.063, 0.341, 0.89],
    mid: [0.267, 0.659, 0.969],
    light: [0.925, 0.976, 1.0],
    band: [0, -1],
    offset: [0, 0],
    swirl: 0,
  },
  {
    name: 'Dune',
    line: 'Warm and unhurried',
    deep: [0.878, 0.345, 0.212],
    mid: [0.973, 0.643, 0.4],
    light: [1.0, 0.953, 0.89],
    band: [0.7, 0.7],
    offset: [3.1, 1.7],
    swirl: 1.2,
  },
  {
    name: 'Moss',
    line: 'Bright and curious',
    deep: [0.039, 0.494, 0.4],
    mid: [0.341, 0.831, 0.627],
    light: [0.937, 1.0, 0.961],
    band: [-1, 0.2],
    offset: [-2.3, 4.2],
    swirl: -0.9,
  },
  {
    name: 'Dusk',
    line: 'Low and thoughtful',
    deep: [0.329, 0.22, 0.859],
    mid: [0.749, 0.475, 0.937],
    light: [1.0, 0.925, 0.984],
    band: [0.3, 1],
    offset: [5.2, -3.3],
    swirl: 1.6,
  },
];

export const LOOP = 10;
/** autoPlay steps: start, from, to, duration. The last one glides back to the first voice. */
const STEPS = [
  { at: 1.4, from: 0, to: 1, dur: 0.85 },
  { at: 3.6, from: 1, to: 2, dur: 0.85 },
  { at: 5.8, from: 2, to: 3, dur: 0.85 },
  { at: 7.8, from: 3, to: 0, dur: 1.0 },
];
/** The orb breathes four times a loop. */
export const BREATH = 2.5;
/** The preview a voice "speaks" after it lands. */
export const SPEAK_LENGTH = 1.1;

export function clamp(x: number, a = 0, b = 1) {
  'worklet';
  return Math.min(Math.max(x, a), b);
}

export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = clamp((x - a) / (b - a));
  return t * t * (3 - 2 * t);
}

export function easeInOut(x: number) {
  'worklet';
  const t = clamp(x);
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  const t = clamp(x);
  return 1 - Math.pow(1 - t, 3);
}

/** 1 → 1.02 → 1, four times a loop. */
export function breathing(clock: number) {
  'worklet';
  return 1 + 0.02 * (0.5 - 0.5 * Math.cos((2 * Math.PI * clock) / BREATH));
}

/** The orb's squash after a swipe starts (u seconds ago): it gives to about 0.94, then overshoots to about 1.02 and settles. */
export function squash(u: number) {
  'worklet';
  return u <= 0 ? 1 : 1 - 0.11 * Math.exp(-2.4 * u) * Math.sin(7.5 * u);
}

const SYLLABLES = [
  [0.15, 0.8, 0.08],
  [0.38, 1.0, 0.1],
  [0.62, 0.7, 0.08],
  [0.85, 0.9, 0.1],
];

/** A short spoken preview: four syllables over 1.1 s, 0…1. */
export function speak(u: number) {
  'worklet';
  if (u <= 0 || u >= SPEAK_LENGTH) return 0;
  let v = 0;
  for (let i = 0; i < SYLLABLES.length; i++) {
    const d = (u - SYLLABLES[i][0]) / SYLLABLES[i][2];
    v += SYLLABLES[i][1] * Math.exp(-d * d);
  }
  return Math.min(1, v);
}

/** autoPlay at loop time e: v, seconds since the last swipe started, and since the last voice landed. */
export function auto(e: number) {
  'worklet';
  // Before the first step of a loop, the last step of the previous one is the latest.
  let last = STEPS[STEPS.length - 1];
  let lastAt = last.at - LOOP;
  for (let i = 0; i < STEPS.length; i++) {
    if (e >= STEPS[i].at) {
      last = STEPS[i];
      lastAt = STEPS[i].at;
    }
  }
  const u = e - lastAt;
  return { v: last.from + (last.to - last.from) * easeInOut(u / last.dur), sinceSwipe: u, sinceLanding: u - last.dur };
}

/** The two voices the picker is between and how far (0…1), the blended texture, and the mixed mid colour for the glow. */
export function blend(v: number) {
  'worklet';
  const x = clamp(v, 0, VOICES.length - 1);
  const i = Math.min(Math.floor(x), VOICES.length - 2);
  const f = x - i;
  const a = VOICES[i];
  const b = VOICES[i + 1];
  const fc = smoothstep(0, 1, f);
  const mix = (p: number[], q: number[], t: number) => p.map((pv, k) => pv + (q[k] - pv) * t);
  return {
    a,
    b,
    f,
    mid: mix(a.mid, b.mid, fc),
    band: mix(a.band, b.band, f),
    offset: mix(a.offset, b.offset, f),
    swirl: a.swirl + (b.swirl - a.swirl) * f,
  };
}
