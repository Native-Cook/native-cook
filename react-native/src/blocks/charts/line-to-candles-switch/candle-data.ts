// Every range has the same number of points, and every candle view is built from them: a 30m
// candle per point, a 1h candle per two, a 4h candle per four. So every view of every range has
// POINTS candles to draw, and any view can morph into any other candle by candle.
export const POINTS = 96;

export type Range = {
  label: string;
  /** Prices, oldest first, at even steps; any length is resampled to POINTS. */
  prices: number[];
  /** Milliseconds (UTC) of the first and last price, for the date under the cursor. */
  start: number;
  end: number;
  /** Show the time of day under the cursor, not only the date. */
  hours: boolean;
};

export type View = { label: string; /** Points per candle; 0 is the line. */ group: number };

export const VIEWS: View[] = [
  { label: 'Line', group: 0 },
  { label: '30m', group: 1 },
  { label: '1h', group: 2 },
  { label: '4h', group: 4 },
];

/** One way of drawing one range: a candle per point (collapsed onto the line in the line view). */
export type Frame = {
  o: number[];
  h: number[];
  l: number[];
  c: number[];
  /** Each candle's centre and body width, as fractions of the chart's width. */
  x: number[];
  w: number[];
  /** The line through the closes, and the price span the chart's height shows. */
  line: number[];
  low: number;
  high: number;
  /** 1 in the line view: the line and its area are drawn. */
  lineAlpha: number;
  /** 1 when any candle has a body. */
  candles: number;
};

const HOUR = 3600 * 1000;
const DAY = 24 * HOUR;
const NOW = Date.UTC(2025, 3, 14, 20, 0);
const NOW_PRICE = 198.87;
// A body takes this share of its slot; the rest is the gap to the next candle.
const BODY = 0.62;

/** A small seeded generator (mulberry32): the same seed always gives the same series. */
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

/**
 * A jagged price from [time, level] stops: flat stretches with noise and sudden steps, as a
 * stock moves on news. Scaled so it opens at `open` and closes at the price now.
 */
function series(stops: number[][], noise: number, seed: number, open: number) {
  const random = seeded(seed);
  const levels: number[] = [];
  let stop = 0;
  for (let i = 0; i < POINTS; i++) {
    const t = i / (POINTS - 1);
    while (stop < stops.length - 2 && stops[stop + 1][0] < t) stop++;
    const [t0, v0] = stops[stop];
    const [t1, v1] = stops[stop + 1];
    const jitter = i === 0 || i === POINTS - 1 ? 0 : (random() - 0.5) * noise;
    levels.push(v0 + ((v1 - v0) * (t - t0)) / (t1 - t0) + jitter);
  }
  const first = levels[0];
  const last = levels[POINTS - 1];
  return levels.map((level) => Math.round((open + ((level - first) / (last - first)) * (NOW_PRICE - open)) * 100) / 100);
}

export const RANGES: Range[] = [
  {
    label: '1D',
    prices: series([[0, 0.3], [0.2, 0.1], [0.35, 0.45], [0.5, 0.35], [0.7, 0.8], [0.85, 0.6], [1, 0.9]], 0.12, 4, 196.4),
    start: NOW - DAY,
    end: NOW,
    hours: true,
  },
  {
    label: '1W',
    prices: series([[0, 0.02], [0.03, 0.1], [0.2, 0.12], [0.205, 0.45], [0.42, 0.42], [0.47, 0.4], [0.6, 0.48], [0.64, 0.66], [0.75, 0.7], [0.76, 0.86], [0.8, 0.84], [0.84, 0.73], [0.88, 0.85], [1, 0.82]], 0.05, 12, 181.48),
    start: NOW - 7 * DAY,
    end: NOW,
    hours: true,
  },
  {
    label: '1M',
    prices: series([[0, 0.3], [0.15, 0.1], [0.3, 0.25], [0.32, 0.5], [0.5, 0.42], [0.65, 0.6], [0.7, 0.55], [0.8, 0.85], [0.9, 0.78], [1, 0.95]], 0.09, 27, 172.15),
    start: NOW - 30 * DAY,
    end: NOW,
    hours: true,
  },
  {
    label: '1Y',
    prices: series([[0, 0.1], [0.2, 0.25], [0.3, 0.15], [0.45, 0.4], [0.55, 0.3], [0.7, 0.6], [0.8, 0.5], [1, 0.95]], 0.07, 31, 120.3),
    start: NOW - 365 * DAY,
    end: NOW,
    hours: false,
  },
  {
    label: 'ALL',
    prices: series([[0, 0], [0.5, 0.02], [0.7, 0.06], [0.8, 0.12], [0.88, 0.3], [0.93, 0.55], [0.96, 0.5], [1, 1]], 0.015, 55, 4.12),
    start: NOW - 10 * 365 * DAY,
    end: NOW,
    hours: false,
  },
];

function resampled(prices: number[]) {
  if (prices.length === POINTS) return prices;
  const out: number[] = [];
  for (let i = 0; i < POINTS; i++) out.push(prices[Math.round((i / (POINTS - 1)) * (prices.length - 1))]);
  return out;
}

/** Every view of a range, from its prices alone: the same prices always give the same candles. */
export function frames(range: Range, seed: number): Frame[] {
  const p = resampled(range.prices);
  const random = seeded(seed);
  // Wicks reach past the body by up to a third of the average move between two points.
  let move = 0;
  for (let i = 1; i < POINTS; i++) move += Math.abs(p[i] - p[i - 1]);
  const reach = (move / (POINTS - 1)) * 0.9;
  const fine = p.map((close, i) => {
    const open = i === 0 ? close - reach * 0.5 : p[i - 1];
    return { o: open, c: close, h: Math.max(open, close) + random() * reach, l: Math.min(open, close) - random() * reach };
  });
  let low = Infinity;
  let high = -Infinity;
  for (const candle of fine) {
    low = Math.min(low, candle.l);
    high = Math.max(high, candle.h);
  }
  const slot = 1 / POINTS;
  return VIEWS.map((view) => {
    const k = Math.max(1, view.group);
    const frame: Frame = { o: [], h: [], l: [], c: [], x: [], w: [], line: p, low, high, lineAlpha: view.group === 0 ? 1 : 0, candles: view.group === 0 ? 0 : 1 };
    for (let i = 0; i < POINTS; i++) {
      if (view.group === 0) {
        // Collapsed onto the line: a candle grows out of the line from here.
        frame.o.push(p[i]);
        frame.c.push(p[i]);
        frame.h.push(p[i]);
        frame.l.push(p[i]);
        frame.x.push((i + 0.5) * slot);
        frame.w.push(slot * BODY);
        continue;
      }
      const first = Math.floor(i / k) * k;
      const last = Math.min(POINTS - 1, first + k - 1);
      let h = -Infinity;
      let l = Infinity;
      for (let j = first; j <= last; j++) {
        h = Math.max(h, fine[j].h);
        l = Math.min(l, fine[j].l);
      }
      frame.o.push(fine[first].o);
      frame.c.push(fine[last].c);
      frame.h.push(h);
      frame.l.push(l);
      frame.x.push(((first + last) / 2 + 0.5) * slot);
      frame.w.push(slot * k * BODY);
    }
    return frame;
  });
}

// autoPlay, in seconds from the start of a loop. Each step is the state the screen is due in from
// that moment: range, view, and the menu (-1 closed, or the row under the finger, 4 for none).
export const LOOP = 10;
export const STEPS = [
  { at: 0, range: 1, view: 0, menu: -1 },
  { at: 0.8, range: 2, view: 0, menu: -1 },
  { at: 4.3, range: 2, view: 0, menu: 4 },
  { at: 4.6, range: 2, view: 0, menu: 1 },
  { at: 4.75, range: 2, view: 1, menu: -1 },
  { at: 6.0, range: 2, view: 1, menu: 4 },
  { at: 6.3, range: 2, view: 1, menu: 3 },
  { at: 6.45, range: 2, view: 3, menu: -1 },
  { at: 7.3, range: 2, view: 3, menu: 4 },
  { at: 7.6, range: 2, view: 3, menu: 0 },
  { at: 7.75, range: 2, view: 0, menu: -1 },
  { at: 8.9, range: 1, view: 0, menu: -1 },
];
/** The finger on the chart: down, sweeping right, back to the middle, up. */
export const PRESS_AT = 1.7;
export const SWEEP_TO = 3.2;
export const BACK_TO = 3.8;
export const LIFT_AT = 4.0;
export const PRESS_X = 0.12;
export const TURN_X = 0.94;
export const REST_X = 0.55;
export const HELD_FOR = 0.2;
/** After the last touch autoPlay waits this long, then plays again from its first state. */
export const USER_HOLD = 3;

// Morphs, in seconds. Line to candles: the line and area fade, then bodies grow out of the line
// from left to right; the way back mirrors it. Candles to candles and range to range are one move.
export const FADE = 0.2;
export const SPREAD = 0.3;
export const GROW = 0.4;
export const MOVE = 0.5;

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function easeInOut(t: number) {
  'worklet';
  const u = clamp01(t);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

/** The finger's place and the cursor's strength `t` seconds into a loop. */
export function scrubAt(t: number) {
  'worklet';
  let x = PRESS_X;
  if (t >= PRESS_AT) x = PRESS_X + (TURN_X - PRESS_X) * easeInOut((t - PRESS_AT) / (SWEEP_TO - PRESS_AT));
  if (t >= SWEEP_TO) x = TURN_X + (REST_X - TURN_X) * easeInOut((t - SWEEP_TO) / (BACK_TO - SWEEP_TO));
  const held = clamp01((t - PRESS_AT) / HELD_FOR) - clamp01((t - LIFT_AT) / HELD_FOR);
  return { x, held, down: t >= PRESS_AT && t < LIFT_AT ? 1 : 0 };
}

/** How long a morph from one state to the next takes. */
export function duration(from: Frame, to: Frame) {
  'worklet';
  if (from.candles === 0 && to.candles === 0) return MOVE;
  if (from.lineAlpha !== to.lineAlpha) return FADE + SPREAD + GROW;
  return MOVE;
}

/** The price at `x` across the chart, between the two nearest points of the line. */
export function lineAt(line: number[], x: number) {
  'worklet';
  const at = clamp01((x * POINTS - 0.5) / (POINTS - 1)) * (POINTS - 1);
  const i = Math.min(POINTS - 2, Math.floor(at));
  return line[i] + (line[i + 1] - line[i]) * (at - i);
}

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** "Apr 13 at 9:28 PM", or "Apr 13, 2024" for long ranges. */
export function dateLabel(ms: number, hours: boolean) {
  'worklet';
  const date = new Date(Math.round(ms / 60000) * 60000);
  const day = `${MONTH_NAMES[date.getUTCMonth()]} ${date.getUTCDate()}`;
  if (!hours) return `${day}, ${date.getUTCFullYear()}`;
  const h = date.getUTCHours();
  const m = date.getUTCMinutes();
  return `${day} at ${h % 12 === 0 ? 12 : h % 12}:${m < 10 ? '0' : ''}${m} ${h < 12 ? 'AM' : 'PM'}`;
}
