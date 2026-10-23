/** The sleep stages a night is split into, in the order of `STAGE_COLORS`. */
export type Stage = 'awake' | 'rem' | 'core' | 'deep';

/** One night: when sleep started and ended, in minutes from midnight (negative before it). */
export type Night = {
  weekday: string;
  day: number;
  asleep: number;
  awake: number;
  /** Stage runs from `asleep` on, each with its length in minutes; generated when left out. */
  stages?: { stage: Stage; minutes: number }[];
};

export const STAGE_COLORS: Record<Stage, string> = {
  awake: '#FF8A3D',
  rem: '#B000FF',
  core: '#1E96FF',
  deep: '#1DE9B6',
};

// Two weeks of nights, the last one tonight's (00:42–08:23).
export const NIGHTS: Night[] = [
  { weekday: 'Th', day: 2, asleep: 40, awake: 413 },
  { weekday: 'Fr', day: 3, asleep: 75, awake: 505 },
  { weekday: 'Sa', day: 4, asleep: 38, awake: 460 },
  { weekday: 'Su', day: 5, asleep: 110, awake: 435 },
  { weekday: 'Mo', day: 6, asleep: 3, awake: 502 },
  { weekday: 'Tu', day: 7, asleep: 50, awake: 380 },
  { weekday: 'We', day: 8, asleep: 15, awake: 470 },
  { weekday: 'Th', day: 9, asleep: 26, awake: 520 },
  { weekday: 'Fr', day: 10, asleep: 48, awake: 492 },
  { weekday: 'Sa', day: 11, asleep: 62, awake: 405 },
  { weekday: 'Su', day: 12, asleep: 34, awake: 345 },
  { weekday: 'Mo', day: 13, asleep: -18, awake: 455 },
  { weekday: 'Tu', day: 14, asleep: 8, awake: 300 },
  { weekday: 'We', day: 15, asleep: 42, awake: 503 },
];

// Stage runs last from RUN_MIN to RUN_MIN + RUN_SPREAD minutes; REM and awake runs are shorter.
const RUN_MIN = 4;
const RUN_SPREAD = 26;
const SHORT_RUN = 0.45;
// How likely each stage is: awake always this rare, deep fading through the night, REM growing.
const AWAKE = 0.03;
const DEEP_EARLY = 0.5;
const REM_EARLY = 0.06;
const REM_LATE = 0.2;

/** A fixed value in [0, 1) for a night and a run, so every platform draws the same nights. */
export function hash(a: number, b: number) {
  const x = Math.sin(a * 127.1 + b * 311.7) * 43758.5453;
  return x - Math.floor(x);
}

/**
 * The stage runs of a night: deep sleep comes early, REM late, core all night, and a short awake
 * now and then. Each run's length and stage come from `hash(night, run)`.
 */
export function stagesOf(night: Night, index: number) {
  if (night.stages) return night.stages;
  const runs: { stage: Stage; minutes: number }[] = [];
  const length = night.awake - night.asleep;
  let at = 0;
  for (let run = 0; at < length; run++) {
    const through = at / length;
    const pick = hash(index, run * 2 + 1);
    const deep = DEEP_EARLY * (1 - through);
    const rem = REM_EARLY + (REM_LATE - REM_EARLY) * through;
    const stage: Stage = pick < AWAKE ? 'awake' : pick < AWAKE + deep ? 'deep' : pick < AWAKE + deep + rem ? 'rem' : 'core';
    const span = RUN_MIN + hash(index, run * 2) * RUN_SPREAD;
    const minutes = Math.min(length - at, stage === 'rem' || stage === 'awake' ? span * SHORT_RUN : span);
    runs.push({ stage, minutes });
    at += minutes;
  }
  return runs;
}

/** 42 → "00:42", -18 → "23:42". */
export function clock(minutes: number) {
  const m = ((Math.round(minutes) % 1440) + 1440) % 1440;
  return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
}

export function mean(values: number[]) {
  let sum = 0;
  for (const value of values) sum += value;
  return values.length ? sum / values.length : 0;
}

/** The standard deviation, in the values' unit. */
export function spread(values: number[]) {
  const middle = mean(values);
  return Math.sqrt(mean(values.map((value) => (value - middle) ** 2)));
}
