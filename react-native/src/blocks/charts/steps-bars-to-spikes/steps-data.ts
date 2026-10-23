/** One switch position of the chart: a bar per day or week, the last one being now. */
export type StepsView = {
  /** The switch's letter. */
  key: string;
  /** Steps per bar, oldest first. */
  values: number[];
  /** A label under each bar; an empty one leaves the bar unlabelled. */
  labels: string[];
  /** Each bar's name, shown under the number while a finger is on it. */
  names: string[];
  /** The bar that is now, drawn solid. */
  current: number;
  /** The top of the value axis. */
  max: number;
  /** Steps in the same stretch of the period before, for the change chip. */
  previous: number;
  /** The total of this period, which the big number shows. */
  total: number;
  changeLabel: string;
  /** The small figure at the top left and what it means. */
  note: number;
  noteLabel: string;
  /** Shown at the top right of the chart, e.g. the month. */
  badge?: string;
};

// October 2026 up to today, the 13th.
const OCTOBER = [4400, 5900, 7600, 6200, 9100, 7800, 7020, 8730, 4050, 3600, 0, 4950, 2369];
const MONTH_DAYS = 31;
const DAILY_GOAL = 5132;

function sum(values: number[]) {
  let total = 0;
  for (const value of values) total += value;
  return total;
}

const lastWeek = OCTOBER.slice(-7);
const thisWeek = sum(OCTOBER.slice(11));

export const VIEWS: StepsView[] = [
  {
    key: 'D',
    values: lastWeek,
    labels: ['Wed', 'Thu', 'Fri', 'Sat', 'Sun', 'Mon', 'Tue'],
    names: ['Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday', 'Monday', 'Today'],
    current: 6,
    max: 9000,
    previous: lastWeek[5],
    total: lastWeek[6],
    changeLabel: 'than previous day',
    note: DAILY_GOAL - lastWeek[6],
    noteLabel: 'Steps behind',
  },
  {
    key: 'W',
    values: [46200, 51800, 39400, 55600, 48900, sum(OCTOBER.slice(4, 11)), thisWeek],
    labels: ['8/31', '9/7', '9/14', '9/21', '9/28', '10/5', '10/12'],
    names: ['Week of Aug 31', 'Week of Sep 7', 'Week of Sep 14', 'Week of Sep 21', 'Week of Sep 28', 'Week of Oct 5', 'This week'],
    current: 6,
    max: 63000,
    previous: sum(OCTOBER.slice(4, 6)),
    total: thisWeek,
    changeLabel: 'than previous week',
    note: Math.round(thisWeek / 2),
    noteLabel: 'Avg. steps per day',
  },
  {
    key: 'M',
    values: [...OCTOBER, ...Array.from({ length: MONTH_DAYS - OCTOBER.length }, () => 0)],
    labels: Array.from({ length: MONTH_DAYS }, (_, i) => (i % 4 === 0 ? String(i + 1).padStart(2, '0') : '')),
    names: Array.from({ length: MONTH_DAYS }, (_, i) => (i === OCTOBER.length - 1 ? 'Today' : `Oct ${i + 1}`)),
    current: OCTOBER.length - 1,
    max: 10000,
    previous: 84600,
    total: sum(OCTOBER),
    changeLabel: 'than previous month',
    note: Math.round(sum(OCTOBER) / OCTOBER.length),
    noteLabel: 'Avg. steps per day',
    badge: "OCT '26",
  },
];

// What the steps come to, at an average stride and weight.
const KM_PER_STEP = 0.00071;
const KCAL_PER_STEP = 0.0904;
const STEPS_PER_FLOOR = 590;

export function statsOf(steps: number) {
  return {
    distance: (steps * KM_PER_STEP).toFixed(2),
    calories: grouped((steps * KCAL_PER_STEP).toFixed(1)),
    floors: String(Math.round(steps / STEPS_PER_FLOOR)),
  };
}

/** "87291" → "87,291", keeping any decimals. */
export function grouped(value: number | string) {
  const [whole, fraction] = String(value).split('.');
  return whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + (fraction === undefined ? '' : `.${fraction}`);
}

/** An axis value: 8100 → "8.1K", 900 → "900". */
export function axisLabel(value: number) {
  if (value < 1000) return String(Math.round(value));
  return `${Number((value / 1000).toFixed(1))}K`;
}
