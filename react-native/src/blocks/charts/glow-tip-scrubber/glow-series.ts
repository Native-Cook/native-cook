// Every range is drawn from the same number of points.
export const POINTS = 160;

export type Range = {
  label: string;
  /** Said under the price while nobody scrubs. */
  span: string;
  /** Prices, oldest first, at even steps up to `end`. */
  prices: number[];
  /** Milliseconds (UTC) of the first and last price, for the date under the cursor. */
  start: number;
  end: number;
  /** Show the time of day under the cursor, not only the date. */
  hours: boolean;
};

const HOUR = 3600 * 1000;
const DAY = 24 * HOUR;
/** The last price of every range: the price now. */
const NOW_PRICE = 84283.52;
const NOW = Date.UTC(2025, 0, 31, 9, 0);

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

/** Scaled so the lowest point is 0 and the highest 1. */
function unit(values: number[]) {
  let low = Infinity;
  let high = -Infinity;
  for (const v of values) {
    low = Math.min(low, v);
    high = Math.max(high, v);
  }
  return values.map((v) => (v - low) / (high - low));
}

/** Three passes of a [1 2 1] blur: the jitter of a real price without single-point needles. */
function soften(values: number[]) {
  let out = values;
  for (let pass = 0; pass < 3; pass++) {
    const next = out.slice();
    for (let i = 1; i < out.length - 1; i++) next[i] = (out[i - 1] + out[i] * 2 + out[i + 1]) / 4;
    out = next;
  }
  return out;
}

/**
 * The week of the design, as [time, level] stops: a first peak, a long plateau, a crash in the
 * middle about two thirds of the range deep, a ragged recovery and a climb at the end.
 */
const WEEK = [
  [0, 0.78], [0.02, 0.72], [0.05, 0.88], [0.07, 0.85], [0.085, 0.87], [0.1, 1], [0.13, 0.82], [0.16, 0.79],
  [0.22, 0.83], [0.28, 0.82], [0.31, 0.84], [0.35, 0.8], [0.4, 0.83], [0.45, 0.84], [0.47, 0.8], [0.5, 0.35],
  [0.52, 0.02], [0.535, 0.15], [0.55, 0], [0.575, 0.3], [0.6, 0.1], [0.63, 0.42], [0.66, 0.55], [0.68, 0.45],
  [0.7, 0.5], [0.73, 0.33], [0.76, 0.45], [0.8, 0.28], [0.84, 0.42], [0.88, 0.38], [0.9, 0.32], [0.92, 0.62],
  [0.95, 0.7], [0.98, 0.84], [1, 0.8],
];

function week() {
  const random = seeded(19);
  const out: number[] = [];
  let stop = 0;
  for (let i = 0; i < POINTS; i++) {
    const t = i / (POINTS - 1);
    while (stop < WEEK.length - 2 && WEEK[stop + 1][0] < t) stop++;
    const [t0, v0] = WEEK[stop];
    const [t1, v1] = WEEK[stop + 1];
    out.push(v0 + ((v1 - v0) * (t - t0)) / (t1 - t0) + (random() - 0.5) * 0.05);
  }
  return unit(soften(out));
}

function walk(seed: number, drift: number) {
  const random = seeded(seed);
  const out: number[] = [];
  let value = 0;
  for (let i = 0; i < POINTS; i++) {
    value += random() - 0.5 + drift;
    out.push(value);
  }
  return unit(soften(out));
}

/** Prices for a series: `spread` from its lowest to its highest point, ending on the price now. */
function priced(levels: number[], spread: number) {
  const last = levels[levels.length - 1];
  return levels.map((level) => Math.round((NOW_PRICE + (level - last) * spread) * 100) / 100);
}

export const RANGES: Range[] = [
  { label: '1D', span: 'Past day', prices: priced(walk(3, 0.02), 820), start: NOW - DAY, end: NOW, hours: true },
  { label: '1W', span: 'Past week', prices: priced(week(), 7174.58), start: NOW - 7 * DAY, end: NOW, hours: true },
  { label: '1M', span: 'Past month', prices: priced(walk(29, 0.04), 11800), start: NOW - 30 * DAY, end: NOW, hours: true },
  { label: '6M', span: 'Past 6 months', prices: priced(walk(8, 0.09), 31000), start: NOW - 182 * DAY, end: NOW, hours: false },
  { label: '1Y', span: 'Past year', prices: priced(walk(44, 0.12), 46000), start: NOW - 365 * DAY, end: NOW, hours: false },
  { label: '5Y', span: 'Past 5 years', prices: priced(walk(71, 0.16), 66000), start: NOW - 5 * 365 * DAY, end: NOW, hours: false },
  { label: 'All', span: 'All time', prices: priced(walk(90, 0.2), 72000), start: NOW - 8 * 365 * DAY, end: NOW, hours: false },
];

// autoPlay, in seconds from the start of a loop: a finger lands at the left edge, sweeps right,
// comes back to the crash, jumps to the right edge and lifts; then the line draws itself again
// behind the same glowing tip.
export const LOOP = 9;
export const PRESS_AT = 0.6;
export const BLOOM = 0.2;
export const SWEEP_TO = 4.0;
export const BACK_TO = 5.2;
export const JUMP_TO = 6.0;
export const LIFT = 0.25;
export const FADE_AT = 6.35;
export const FADE_FOR = 0.25;
export const DRAW_AT = 6.6;
export const DRAW_FOR = 1.4;
export const MARKS_IN = 0.3;
/** Where the finger starts, turns, and where it comes back to (the crash), across the chart. */
export const START_X = 0.015;
export const TURN_X = 0.93;
export const DIP_X = 0.545;
/** After the last touch autoPlay waits this long, then plays again. */
export const USER_HOLD = 3;

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function easeInOut(t: number) {
  'worklet';
  const u = clamp01(t);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

/** Ease out with a small overshoot: a quick throw of the finger that lands like a spring. */
export function backOut(t: number) {
  'worklet';
  const u = clamp01(t);
  const c = 1.2;
  return 1 + (c + 1) * Math.pow(u - 1, 3) + c * Math.pow(u - 1, 2);
}

export type Film = {
  /** Where the glowing tip is, across the chart (0 to 1). */
  tip: number;
  /** The cursor line and the dimmed line (0 to 1). */
  held: number;
  /** 1 while a finger is down: the header reads the price under it. */
  down: number;
  /** How bright the tip's glow is (0 to 1). */
  lit: number;
  /** How much of the line is drawn, from the left (0 to 1), and its opacity. */
  reveal: number;
  shown: number;
  /** The open-price rule and the high and low marks, shown at rest. */
  marks: number;
};

/** The chart `t` seconds into a loop. Reduced motion holds the cursor still and does not redraw. */
export function filmAt(t: number, still: boolean): Film {
  'worklet';
  const down = t >= PRESS_AT && t < JUMP_TO ? 1 : 0;
  const lift = clamp01((t - JUMP_TO) / LIFT);
  const marks = (1 - clamp01((t - PRESS_AT) / BLOOM)) + clamp01((t - DRAW_AT - DRAW_FOR) / MARKS_IN);
  if (still) {
    return { tip: DIP_X, held: down, down, lit: down, reveal: 1, shown: 1, marks: down ? 0 : 1 };
  }
  let tip = START_X;
  if (t >= PRESS_AT) tip = START_X + (TURN_X - START_X) * easeInOut((t - PRESS_AT) / (SWEEP_TO - PRESS_AT));
  if (t >= SWEEP_TO) tip = TURN_X + (DIP_X - TURN_X) * easeInOut((t - SWEEP_TO) / (BACK_TO - SWEEP_TO));
  // The throw to the right edge overshoots; the tip stops at the end of the line.
  if (t >= BACK_TO) tip = Math.min(1, DIP_X + (1 - DIP_X) * backOut((t - BACK_TO) / (JUMP_TO - BACK_TO)));
  const bloom = clamp01((t - PRESS_AT) / BLOOM);
  const held = t < JUMP_TO ? bloom : 1 - lift;
  let lit = held;
  let reveal = 1;
  let shown = 1 - clamp01((t - FADE_AT) / FADE_FOR);
  if (t >= DRAW_AT) {
    // The redraw moves at an even pace, with its glowing head leading.
    reveal = clamp01((t - DRAW_AT) / DRAW_FOR);
    tip = reveal;
    shown = 1;
    lit = clamp01((t - DRAW_AT) / BLOOM) * (1 - clamp01((t - DRAW_AT - DRAW_FOR) / LIFT));
  }
  return { tip, held, down, lit, reveal, shown, marks };
}

/** The price at `x` across the chart, between the two nearest points: what the line draws. */
export function priceAt(prices: number[], x: number) {
  'worklet';
  const at = clamp01(x) * (prices.length - 1);
  const i = Math.min(prices.length - 2, Math.floor(at));
  return prices[i] + (prices[i + 1] - prices[i]) * (at - i);
}

/** "84,283.52" from 84283.52. */
export function grouped(value: number) {
  'worklet';
  const fixed = Math.abs(value).toFixed(2);
  const whole = fixed.slice(0, fixed.length - 3);
  let out = '';
  for (let i = 0; i < whole.length; i++) {
    if (i > 0 && (whole.length - i) % 3 === 0) out += ',';
    out += whole[i];
  }
  return out + fixed.slice(fixed.length - 3);
}

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** "Jan 27, 9:15 AM" (to the quarter hour), or "Jan 27, 2024" for long ranges. */
export function dateLabel(ms: number, hours: boolean) {
  'worklet';
  const quarter = 15 * 60 * 1000;
  const date = new Date(Math.round(ms / quarter) * quarter);
  const day = `${MONTH_NAMES[date.getUTCMonth()]} ${date.getUTCDate()}`;
  if (!hours) return `${day}, ${date.getUTCFullYear()}`;
  const h = date.getUTCHours();
  const m = date.getUTCMinutes();
  return `${day}, ${h % 12 === 0 ? 12 : h % 12}:${m < 10 ? '0' : ''}${m} ${h < 12 ? 'AM' : 'PM'}`;
}
