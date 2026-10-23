import { Skia } from '@shopify/react-native-skia';

/** One candle: open, high, low, close, in the chart's price units. */
export type Candle = [open: number, high: number, low: number, close: number];

/**
 * The chart the walkthrough tells, one candle per row: a falling channel of four lower highs, a
 * break up through the line that joins them, a dip back to the line, a confirming candle and the
 * rally to the target. The indices below point into this table, so change them together.
 */
export const CANDLES: Candle[] = [
  [38, 42.8, 37.2, 42],
  [42, 47.6, 41.3, 47],
  [47, 48.2, 44.1, 45],
  [45, 58, 44.6, 56],
  [56, 56.8, 48.2, 49],
  [49, 50.1, 42, 43],
  [43, 44, 37.1, 38],
  [38, 41.2, 37.4, 40],
  [40, 40.9, 36.2, 37],
  [37, 42.9, 36.5, 42],
  [42, 47.8, 41.1, 47],
  [47, 51.74, 46.3, 50.5],
  [50.5, 50.6, 44.2, 45],
  [45, 46.1, 40.3, 41],
  [41, 41.8, 35.4, 36],
  [36, 39, 35.2, 38],
  [38, 38.7, 33.4, 34],
  [34, 39.8, 33.6, 39],
  [39, 42.9, 38.3, 42],
  [42, 45.48, 41.4, 44.5],
  [44.5, 44.6, 39.2, 40],
  [40, 40.7, 34.3, 35],
  [35, 35.6, 30.2, 31],
  [31, 34.8, 30.4, 34],
  [34, 34.9, 32.1, 33],
  [33, 37.9, 32.5, 37],
  [37, 40, 36.4, 38.5],
  [38.5, 38.9, 34.3, 35],
  // The break.
  [35, 38.2, 34.4, 37.8],
  [37.8, 43.2, 37.3, 42.6],
  [42.6, 46.9, 41.9, 46.2],
  [46.2, 49.8, 45.4, 49],
  // The re-test.
  [49, 49.6, 44.2, 44.8],
  [44.8, 45.4, 40.1, 40.6],
  [40.6, 41.2, 34.2, 36.4],
  // The confirmation.
  [36.4, 42.6, 35.9, 42],
  // The rally.
  [42, 47.8, 41.4, 47],
  [47, 47.9, 44.2, 45],
  [45, 52.6, 44.6, 52],
  [52, 58.7, 51.4, 58],
  [58, 58.6, 55.2, 56],
  [56, 62.8, 55.6, 62],
  [62, 67.4, 61.5, 66.8],
  [66.8, 71.2, 66.1, 70.5],
];

/** Candles on screen at rest; the rest print during the walkthrough. */
export const BASE = 28;
export const SWING_HIGHS = [3, 11, 19, 26];
/** The arrow of the breakout runs from below this candle's low to the peak after the break. */
export const BREAK_FROM = 27;
export const PEAK = 31;
export const RETEST = 34;
export const CONFIRM = 35;
export const LAST = CANDLES.length - 1;
/** Entry at the confirming close, stop under the re-test, target three times the risk away. */
export const ENTRY = 42;
export const STOP = 34;
export const RATIO = 3;
/** The risk box starts just right of the confirming candle and is this many candles wide. */
export const ZONE_FROM = CONFIRM + 0.6;
export const ZONE_CANDLES = 10;
/** The trend line runs on to this index, off the right of every view. */
export const LINE_TO = 48;

const FIRST = SWING_HIGHS[0];
const FINAL = SWING_HIGHS[SWING_HIGHS.length - 1];
const SLOPE = (CANDLES[FINAL][1] - CANDLES[FIRST][1]) / (FINAL - FIRST);

/** The trend line through the first and the last swing high, at a candle index. */
export function lineAt(index: number) {
  'worklet';
  return CANDLES[FIRST][1] + SLOPE * (index - FIRST);
}

// The timeline, in seconds from the start of a loop.
export const LOOP = 11;
export const SWING_AT = 0.8;
export const SWING_STEP = 0.3;
export const CIRCLE_IN = 0.25;
export const SWING_OUT = [2.9, 3.2];
export const TREND_DRAW = [2.3, 2.9];
export const TREND_TYPE = [2.9, 3.3];
export const PRINT_GROW = 0.1;
export const BREAK_PRINT = 3.6;
export const PRINT_STEP = 0.12;
export const ARROW_DRAW = [4.1, 4.45];
export const BREAK_OUT = [4.6, 4.9];
export const RETEST_PRINT = 5;
export const ZIGZAG_DRAW = [5.4, 5.85];
export const RETEST_LABEL = 5.6;
export const CONFIRM_PRINT = 6;
export const CONFIRM_MARK = 6.15;
export const NOTES_OUT = [6.6, 6.85];
export const BOX_IN = 6.85;
export const ZONE_RIGHT = [7.05, 7.3];
export const ZONE_UP = [7.3, 7.9];
export const RALLY_PRINT = 7.95;
export const RALLY_STEP = 0.13;
export const BURST = 8.95;
export const BURST_FOR = 0.9;
export const FADE = [9.7, 10.1];
export const REPRINT = 10.15;
export const REPRINT_STEP = 0.022;

/** The chapters a tap steps through: where each starts, and the stop after the last. */
export const CHAPTER_STARTS = [SWING_AT, TREND_DRAW[0], 3.3, BREAK_OUT[0], 5.95, NOTES_OUT[0], 7.8];
export const STOPS = [TREND_DRAW[0], 3.3, BREAK_OUT[0], 5.95, NOTES_OUT[0], 7.8, FADE[0], LOOP];

/** The chapter shown at a time: the rest counts as the first, the fade and reprint as the last. */
export function chapterAt(time: number) {
  'worklet';
  let found = 0;
  for (let k = 0; k < CHAPTER_STARTS.length; k++) if (time >= CHAPTER_STARTS[k]) found = k;
  return found;
}

/** When each candle after the base prints. */
const PRINT_AT: number[] = [];
for (let i = 0; i < CANDLES.length; i++) {
  if (i < BASE) PRINT_AT.push(0);
  else if (i <= PEAK) PRINT_AT.push(BREAK_PRINT + (i - BASE) * PRINT_STEP);
  else if (i <= RETEST) PRINT_AT.push(RETEST_PRINT + (i - PEAK - 1) * PRINT_STEP);
  else if (i === CONFIRM) PRINT_AT.push(CONFIRM_PRINT);
  else PRINT_AT.push(RALLY_PRINT + (i - CONFIRM - 1) * RALLY_STEP);
}

/**
 * The camera at rest and at each beat: time, the candle index at the centre, the price at the
 * centre, and the zoom. Between two rows at the same view it holds; between two different views
 * it eases.
 */
const VIEWS = [
  [0, 13.5, 44, 1],
  [3.3, 13.5, 44, 1],
  [3.9, 21, 43, 1.08],
  [4.6, 21, 43, 1.08],
  [5.2, 28, 41, 1.45],
  [6.6, 28, 41, 1.45],
  [7.2, 34, 49, 1.12],
  [7.8, 34, 49, 1.12],
  [9.1, 38, 52, 1.2],
];

export function clamp01(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

/** 0 before `from`, 1 after `to`, an S-curve between. */
export function smooth(time: number, from: number, to: number) {
  'worklet';
  const u = clamp01((time - from) / (to - from));
  return u * u * (3 - 2 * u);
}

/** A spring from 0 to 1, `elapsed` seconds after it was let go; the same curve on every stack. */
export function spring(elapsed: number, omega: number, damping: number) {
  'worklet';
  if (elapsed <= 0) return 0;
  const decay = damping * omega;
  const ringing = omega * Math.sqrt(1 - damping * damping);
  return 1 - Math.exp(-decay * elapsed) * (Math.cos(ringing * elapsed) + (decay / ringing) * Math.sin(ringing * elapsed));
}

/** The camera at a time: [centre index, centre price, zoom]. `still` cuts instead of moving. */
export function camera(time: number, still: boolean) {
  'worklet';
  const last = VIEWS[VIEWS.length - 1];
  // Under the fade the camera goes back to rest unseen.
  if (time >= FADE[1] || time < VIEWS[1][0]) return [VIEWS[0][1], VIEWS[0][2], VIEWS[0][3]];
  if (time >= last[0]) return [last[1], last[2], last[3]];
  let k = 0;
  while (time >= VIEWS[k + 1][0]) k++;
  const a = VIEWS[k];
  const b = VIEWS[k + 1];
  const u = (time - a[0]) / (b[0] - a[0]);
  const e = still ? (u < 0.5 ? 0 : 1) : u * u * (3 - 2 * u);
  return [a[1] + (b[1] - a[1]) * e, a[2] + (b[2] - a[2]) * e, a[3] + (b[3] - a[3]) * e];
}

/** How far a candle has grown from its open to its close at a time, 0 to 1. */
export function growth(index: number, time: number, still: boolean) {
  'worklet';
  let g;
  if (index < BASE) g = time < FADE[1] ? 1 : (time - REPRINT - index * REPRINT_STEP) / PRINT_GROW;
  else g = time >= FADE[1] ? 0 : (time - PRINT_AT[index]) / PRINT_GROW;
  g = clamp01(g);
  return still ? (g > 0 ? 1 : 0) : g;
}

/**
 * Where the chart sits on the canvas: the centre's x and y, points per candle and per price unit.
 * With a camera view it turns a candle index and a price into canvas points.
 */
export type Geometry = [cx: number, cy: number, pitch: number, unit: number];

export function toX(index: number, view: number[], geo: Geometry) {
  'worklet';
  return geo[0] + (index - view[0]) * geo[2] * view[2];
}

export function toY(price: number, view: number[], geo: Geometry) {
  'worklet';
  return geo[1] - (price - view[1]) * geo[3] * view[2];
}

/** The bodies and wicks of the up candles, or of the down ones, as they stand at a time. */
export function candlePath(time: number, view: number[], geo: Geometry, up: boolean, still: boolean, bodyRatio: number, wick: number) {
  'worklet';
  const path = Skia.Path.Make();
  const scale = geo[2] * view[2];
  const body = scale * bodyRatio;
  const wickWidth = Math.max(1, wick * view[2]);
  for (let i = 0; i < CANDLES.length; i++) {
    const [open, high, low, close] = CANDLES[i];
    if (close >= open !== up) continue;
    const g = growth(i, time, still);
    if (g <= 0) continue;
    const x = toX(i, view, geo);
    if (x < -scale || x > geo[0] * 2 + scale) continue;
    // A candle grows out of its open: the body to the close, the wicks to the high and the low.
    const now = open + (close - open) * g;
    const top = toY(Math.max(open, now), view, geo);
    const bottom = toY(Math.min(open, now), view, geo);
    const reachHigh = toY(open + (high - open) * g, view, geo);
    const reachLow = toY(open + (low - open) * g, view, geo);
    path.addRect(Skia.XYWHRect(x - body / 2, top, body, Math.max(1, bottom - top)));
    path.addRect(Skia.XYWHRect(x - wickWidth / 2, reachHigh, wickWidth, reachLow - reachHigh));
  }
  return path;
}

/**
 * The confetti from the last candle when the target is reached: velocity right and up in points
 * a second, shape (0 ring, 1 cross, 2 triangle, 3 square), colour slot, spin in turns a second,
 * size in points.
 */
export const CONFETTI = [
  [-260, 580, 0, 0, 0, 16],
  [-180, 680, 1, 3, 0.6, 11],
  [-95, 740, 3, 1, -0.5, 13],
  [-20, 620, 2, 2, 0.4, 16],
  [70, 780, 1, 0, -0.7, 12],
  [140, 660, 0, 3, 0, 9],
  [220, 580, 3, 2, 0.5, 12],
  [300, 490, 2, 0, -0.4, 14],
  [-330, 450, 2, 1, 0.3, 12],
  [25, 440, 3, 3, -0.6, 9],
  [-140, 500, 1, 2, 0.5, 12],
  [180, 400, 0, 1, 0, 11],
];
export const GRAVITY = 1200;

/** The confetti of one colour slot at a time after the burst, from an origin in canvas points. */
export function confettiPath(slot: number, elapsed: number, ox: number, oy: number) {
  'worklet';
  const path = Skia.Path.Make();
  for (let k = 0; k < CONFETTI.length; k++) {
    const [vx, vy, shape, colour, spin, size] = CONFETTI[k];
    if (colour !== slot) continue;
    const x = ox + vx * elapsed;
    const y = oy - vy * elapsed + 0.5 * GRAVITY * elapsed * elapsed;
    const turn = spin * elapsed * Math.PI * 2;
    const r = size / 2;
    if (shape === 0) {
      path.addCircle(x, y, r);
      continue;
    }
    // Crosses, triangles and squares are built from their corners, turned by the spin.
    const corners = shape === 2 ? 3 : 4;
    const start = shape === 1 ? Math.PI / 4 : shape === 2 ? -Math.PI / 2 : Math.PI / 4;
    for (let c = 0; c < corners; c++) {
      const a = turn + start + (c * Math.PI * 2) / corners;
      const px = x + Math.cos(a) * r * (shape === 3 ? 1.2 : 1);
      const py = y + Math.sin(a) * r * (shape === 3 ? 1.2 : 1);
      if (shape === 1) {
        // A cross: two strokes through the centre.
        if (c < 2) {
          path.moveTo(px, py);
          path.lineTo(2 * x - px, 2 * y - py);
        }
      } else if (c === 0) path.moveTo(px, py);
      else path.lineTo(px, py);
    }
    if (shape !== 1) path.close();
  }
  return path;
}
