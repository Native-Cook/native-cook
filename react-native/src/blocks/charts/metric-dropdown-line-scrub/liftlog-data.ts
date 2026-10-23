/** One measure of the lift, with a value for every logged session. */
export type Metric = {
  label: string;
  /** The line under the value in the tooltip. */
  caption: string;
  unit: string;
  /** The value axis, bottom and top. */
  min: number;
  max: number;
  decimals: number;
  /** One value per session, in the order of `sessions`. */
  values: number[];
};

/** How far back the chart looks; `days: null` is the whole history. */
export type Range = { label: string; days: number | null };

/** When a session was logged, as `YYYY-MM-DDTHH:MM`. */
export type Session = string;

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const DAY = 86400000;

// The demo history: forty sessions ten days apart, the last one today.
const SESSION_COUNT = 40;
const SESSION_GAP_DAYS = 10;
const LAST_SESSION = Date.UTC(2026, 9, 13);
const SESSION_TIMES = ['11:33', '07:10', '18:45', '12:05', '19:20', '06:55', '17:30'];

/** A fixed value in [0, 1) for a session and a series, so the demo data is the same on every run. */
function hash(index: number, seed: number) {
  const x = Math.sin(index * 127.1 + seed * 311.7) * 43758.5453;
  return x - Math.floor(x);
}

function round(value: number, decimals: number) {
  const scale = 10 ** decimals;
  return Math.round(value * scale) / scale;
}

/** A rise from `from` to `to` over the history, with a slow swell and session-to-session noise of up to `jitter`. */
function series(seed: number, from: number, to: number, jitter: number, decimals: number) {
  const values: number[] = [];
  for (let i = 0; i < SESSION_COUNT; i++) {
    const trend = from + ((to - from) * i) / (SESSION_COUNT - 1);
    const swell = Math.sin(i * 0.55 + seed * 2.1) * 0.45;
    const noise = (hash(i, seed) - 0.5) * 1.1;
    values.push(round(trend + (swell + noise) * jitter, decimals));
  }
  return values;
}

function iso(time: number, clock: string) {
  const date = new Date(time);
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  const day = String(date.getUTCDate()).padStart(2, '0');
  return `${date.getUTCFullYear()}-${month}-${day}T${clock}`;
}

export const SESSIONS: Session[] = Array.from({ length: SESSION_COUNT }, (_, i) =>
  iso(LAST_SESSION - (SESSION_COUNT - 1 - i) * SESSION_GAP_DAYS * DAY, SESSION_TIMES[i % SESSION_TIMES.length]),
);

export const METRICS: Metric[] = [
  { label: '1 Rep Max', caption: 'Theoretical max for 1 rep', unit: 'kg', min: 40, max: 75, decimals: 1, values: series(1, 45, 66, 3.4, 1) },
  { label: 'Heaviest weight', caption: 'Heaviest weight used', unit: 'kg', min: 35, max: 60, decimals: 1, values: series(2, 37, 54.5, 0.4, 1) },
  { label: 'Total weight', caption: 'Total weight lifted', unit: 't', min: 1, max: 5, decimals: 2, values: series(3, 2.1, 3.9, 0.45, 2) },
  { label: 'Reps', caption: 'Most reps in one set', unit: 'reps', min: 0, max: 16, decimals: 0, values: series(4, 6, 11, 2.4, 0) },
];

export const RANGES: Range[] = [
  { label: '1 month', days: 30 },
  { label: '3 months', days: 90 },
  { label: '1 year', days: 365 },
  { label: 'All time', days: null },
];

function dayOf(session: Session) {
  return Date.UTC(Number(session.slice(0, 4)), Number(session.slice(5, 7)) - 1, Number(session.slice(8, 10)));
}

/** The first session the range shows. */
export function rangeStart(sessions: Session[], range: Range) {
  if (range.days === null || sessions.length === 0) return 0;
  const from = dayOf(sessions[sessions.length - 1]) - range.days * DAY;
  const index = sessions.findIndex((session) => dayOf(session) >= from);
  return index < 0 ? sessions.length - 1 : index;
}

/** "20 Mar" */
export function shortDate(session: Session) {
  const date = new Date(dayOf(session));
  return `${date.getUTCDate()} ${MONTHS[date.getUTCMonth()].slice(0, 3)}`;
}

/** "11:33 · Wednesday 11 September" */
export function longDate(session: Session) {
  const date = new Date(dayOf(session));
  return `${session.slice(11, 16)} · ${WEEKDAYS[date.getUTCDay()]} ${date.getUTCDate()} ${MONTHS[date.getUTCMonth()]}`;
}

/** An axis value with at most one decimal: 48.75 → "48.8", 75 → "75". */
export function axisValue(value: number) {
  return String(round(value, 1));
}

/**
 * The chart's points for one metric and range, laid out on `slots` places so that any two sets can
 * morph into each other point for point: x and y in 0…1, flattened as [x0, y0, x1, y1, …]. A set
 * with fewer sessions than slots repeats its points, so the extra slots sit on real points and
 * split apart when the set grows.
 */
export function layoutPoints(metric: Metric, start: number, slots: number) {
  const values = metric.values.slice(start);
  const count = values.length;
  const points: number[] = [];
  for (let slot = 0; slot < slots; slot++) {
    const index = count <= 1 ? 0 : Math.round((slot * (count - 1)) / Math.max(1, slots - 1));
    const x = count <= 1 ? 0.5 : index / (count - 1);
    const y = (values[index] - metric.min) / (metric.max - metric.min);
    points.push(x, Math.min(1, Math.max(0, y)));
  }
  return points;
}
