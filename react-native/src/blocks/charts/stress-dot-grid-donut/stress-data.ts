/** 0: no data, 1: low, 2: normal, 3: high. */
export type Level = 0 | 1 | 2 | 3;

/** One view of the heat map: rows by columns of stress levels, with what the screen says about it. */
export type Period = {
  /** The tab's label. */
  label: string;
  range: string;
  rows: string[];
  columns: string[];
  /** One string per row, one character per column: '.' no data, 'L' low, 'N' normal, 'H' high. */
  levels: string[];
  /** The cell of now, ringed: [row, column]. */
  now: [number, number];
  /** What a counted cell is called, after its count. */
  unit: string;
  busiestTitle: string;
  busiest: string;
};

export const LEVEL_COLORS = {
  low: '#D4F53C',
  normal: '#8E8CF7',
  high: '#FF5A3C',
  none: '#3A3A40',
};

/** A day by two-hour periods: seven rows of twelve. */
export const DAYS: Period = {
  label: '7 Days',
  range: 'Oct 5, 2026 - Oct 11, 2026',
  rows: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
  columns: ['00', '02', '04', '06', '08', '10', '12', '14', '16', '18', '20', '22'],
  levels: [
    'HHL..NNN...N',
    'NHNH.HLLH.N.',
    'LLNHHLHNN...',
    'HNHLHLNL...N',
    'HHLNNLNLNH..',
    'LNL......HLL',
    'HNL.NH......',
  ],
  now: [6, 10],
  unit: 'periods',
  busiestTitle: 'Most stressful times',
  busiest: '0:00 and 2:00',
};

/** Four weeks by day: four rows of seven. */
export const WEEKS: Period = {
  label: '4 Weeks',
  range: 'Sep 14, 2026 - Oct 11, 2026',
  rows: ['09/14 - 09/20', '09/21 - 09/27', '09/28 - 10/04', '10/05 - 10/11'],
  columns: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
  levels: ['NNNLNNN', 'HNNNNLN', 'NNHNHHN', 'NNNNNLN'],
  now: [3, 6],
  unit: 'days',
  busiestTitle: 'Most stressful days',
  busiest: 'Monday and Friday',
};

export function levelAt(period: Period, row: number, column: number): Level {
  const mark = period.levels[row]?.[column];
  return mark === 'L' ? 1 : mark === 'N' ? 2 : mark === 'H' ? 3 : 0;
}

/** How many cells are low, normal and high. */
export function counts(period: Period) {
  const tally = [0, 0, 0];
  for (let r = 0; r < period.rows.length; r++) {
    for (let c = 0; c < period.columns.length; c++) {
      const level = levelAt(period, r, c);
      if (level > 0) tally[level - 1] += 1;
    }
  }
  return tally;
}
