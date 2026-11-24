/** Ledger's activity history (invented): day sections with their payments, newest first. */

export type Txn = {
  id: string;
  name: string;
  meta: string;
  /** Cents; negative = money out, positive = money in. */
  cents: number;
  /** Glyph colour pair for the merchant disc. */
  tint: readonly [string, string];
  initial: string;
};

export type Section = {
  id: string;
  label: string;
  month: number;
  txns: Txn[];
};

export type Item =
  | { type: 'header'; id: string; label: string; count: number; spent: number }
  | { type: 'row'; id: string; txn: Txn; last: boolean };

export const MONTHS = ['Nov', 'Oct', 'Sep', 'Aug', 'Jul', 'Jun'] as const;
export const MONTH_NAMES = ['November', 'October', 'September', 'August', 'July', 'June'] as const;

const T = {
  food: ['#f3a65a', '#d9733a'],
  coffee: ['#b98a64', '#8a5d3f'],
  transit: ['#6aa3e8', '#3b6fc4'],
  home: ['#8fc5a2', '#4f936b'],
  fun: ['#e58fb0', '#bf5683'],
  health: ['#7fcbd0', '#3e979f'],
  bills: ['#a7a1e6', '#6c63c4'],
  income: ['#9fd88a', '#4ea33c'],
} as const;

function txn(id: string, name: string, meta: string, dollars: number, tint: readonly [string, string]): Txn {
  return { id, name, meta, cents: Math.round(dollars * 100), tint, initial: name[0] };
}

export const SECTIONS: Section[] = [
  {
    id: 'd1124',
    label: 'Today',
    month: 0,
    txns: [
      txn('a1', 'Lumen Coffee', 'Coffee · 8:12 am', -5.4, T.coffee),
      txn('a2', 'Northway Transit', 'Transport · 8:40 am', -2.9, T.transit),
      txn('a3', 'Fernhill Market', 'Groceries · 12:05 pm', -46.35, T.food),
      txn('a4', 'Saltgrass Kitchen', 'Dining · 7:48 pm', -38.6, T.food),
    ],
  },
  {
    id: 'd1123',
    label: 'Yesterday',
    month: 0,
    txns: [
      txn('b1', 'Cinder Gym', 'Health · 7:02 am', -24.0, T.health),
      txn('b2', 'Quill Books', 'Shopping · 3:20 pm', -18.99, T.fun),
      txn('b3', 'Lumen Coffee', 'Coffee · 4:10 pm', -4.8, T.coffee),
    ],
  },
  {
    id: 'd1121',
    label: 'Sat, Nov 21',
    month: 0,
    txns: [
      txn('c1', 'Harbor Pharmacy', 'Health · 10:30 am', -12.75, T.health),
      txn('c2', 'Bolt & Thread', 'Clothing · 1:15 pm', -64.0, T.fun),
      txn('c3', 'Fernhill Market', 'Groceries · 5:40 pm', -31.2, T.food),
      txn('c4', 'Paper Lantern', 'Cinema · 8:00 pm', -26.5, T.fun),
      txn('c5', 'Northway Transit', 'Transport · 11:20 pm', -14.3, T.transit),
    ],
  },
  {
    id: 'd1118',
    label: 'Wed, Nov 18',
    month: 0,
    txns: [
      txn('e1', 'Brightline Studio', 'Salary · 9:00 am', 3240.0, T.income),
      txn('e2', 'Pebble Mobile', 'Bills · 9:30 am', -29.0, T.bills),
      txn('e3', 'Saltgrass Kitchen', 'Dining · 1:05 pm', -17.45, T.food),
    ],
  },
  {
    id: 'd1030',
    label: 'Fri, Oct 30',
    month: 1,
    txns: [
      txn('f1', 'Orchard Lane Homes', 'Rent · 8:00 am', -860.0, T.home),
      txn('f2', 'Lumen Coffee', 'Coffee · 8:25 am', -5.4, T.coffee),
      txn('f3', 'Fernhill Market', 'Groceries · 6:10 pm', -52.8, T.food),
      txn('f4', 'Northway Transit', 'Transport · 6:45 pm', -2.9, T.transit),
    ],
  },
  {
    id: 'd1025',
    label: 'Sun, Oct 25',
    month: 1,
    txns: [
      txn('g1', 'Sundial Bakery', 'Dining · 10:40 am', -14.2, T.food),
      txn('g2', 'Paper Lantern', 'Cinema · 6:30 pm', -26.5, T.fun),
      txn('g3', 'Quill Books', 'Shopping · 7:55 pm', -22.0, T.fun),
    ],
  },
  {
    id: 'd1020',
    label: 'Tue, Oct 20',
    month: 1,
    txns: [
      txn('h1', 'Cinder Gym', 'Health · 7:00 am', -24.0, T.health),
      txn('h2', 'Gridwell Energy', 'Bills · 9:00 am', -71.36, T.bills),
      txn('h3', 'Fernhill Market', 'Groceries · 5:20 pm', -39.65, T.food),
      txn('h4', 'Lumen Coffee', 'Coffee · 5:35 pm', -4.8, T.coffee),
    ],
  },
  {
    id: 'd0928',
    label: 'Mon, Sep 28',
    month: 2,
    txns: [
      txn('i1', 'Harbor Pharmacy', 'Health · 11:10 am', -9.6, T.health),
      txn('i2', 'Saltgrass Kitchen', 'Dining · 8:15 pm', -44.9, T.food),
      txn('i3', 'Northway Transit', 'Transport · 10:40 pm', -12.1, T.transit),
    ],
  },
  {
    id: 'd0917',
    label: 'Thu, Sep 17',
    month: 2,
    txns: [
      txn('j1', 'Pebble Mobile', 'Bills · 9:30 am', -29.0, T.bills),
      txn('j2', 'Bolt & Thread', 'Clothing · 2:00 pm', -118.0, T.fun),
      txn('j3', 'Sundial Bakery', 'Dining · 4:45 pm', -8.75, T.food),
      txn('j4', 'Fernhill Market', 'Groceries · 6:30 pm', -27.4, T.food),
    ],
  },
  {
    id: 'd0829',
    label: 'Sat, Aug 29',
    month: 3,
    txns: [
      txn('k1', 'Kestrel Air', 'Travel · 6:05 am', -212.0, T.transit),
      txn('k2', 'Lumen Coffee', 'Coffee · 6:40 am', -6.2, T.coffee),
      txn('k3', 'Seabright Inn', 'Travel · 3:00 pm', -168.0, T.home),
      txn('k4', 'Saltgrass Kitchen', 'Dining · 8:30 pm', -52.3, T.food),
    ],
  },
  {
    id: 'd0812',
    label: 'Wed, Aug 12',
    month: 3,
    txns: [
      txn('l1', 'Gridwell Energy', 'Bills · 9:00 am', -64.12, T.bills),
      txn('l2', 'Cinder Gym', 'Health · 6:15 pm', -24.0, T.health),
      txn('l3', 'Fernhill Market', 'Groceries · 7:05 pm', -33.9, T.food),
    ],
  },
  {
    id: 'd0726',
    label: 'Sun, Jul 26',
    month: 4,
    txns: [
      txn('m1', 'Tidewater Market', 'Groceries · 11:20 am', -19.5, T.food),
      txn('m2', 'Paper Lantern', 'Cinema · 7:10 pm', -26.5, T.fun),
      txn('m3', 'Northway Transit', 'Transport · 10:05 pm', -3.4, T.transit),
    ],
  },
  {
    id: 'd0709',
    label: 'Thu, Jul 9',
    month: 4,
    txns: [
      txn('n1', 'Harbor Pharmacy', 'Health · 9:40 am', -15.25, T.health),
      txn('n2', 'Quill Books', 'Shopping · 1:30 pm', -31.0, T.fun),
      txn('n3', 'Saltgrass Kitchen', 'Dining · 7:45 pm', -41.8, T.food),
    ],
  },
  {
    id: 'd0630',
    label: 'Tue, Jun 30',
    month: 5,
    txns: [
      txn('o1', 'Orchard Lane Homes', 'Rent · 8:00 am', -860.0, T.home),
      txn('o2', 'Pebble Mobile', 'Bills · 9:30 am', -29.0, T.bills),
      txn('o3', 'Sundial Bakery', 'Dining · 10:15 am', -11.4, T.food),
      txn('o4', 'Lumen Coffee', 'Coffee · 4:20 pm', -4.8, T.coffee),
    ],
  },
  {
    id: 'd0612',
    label: 'Fri, Jun 12',
    month: 5,
    txns: [
      txn('p1', 'Cinder Gym', 'Health · 7:00 am', -24.0, T.health),
      txn('p2', 'Fernhill Market', 'Groceries · 6:00 pm', -48.15, T.food),
      txn('p3', 'Paper Lantern', 'Cinema · 9:20 pm', -26.5, T.fun),
    ],
  },
];

export function formatMoney(cents: number, signed: boolean) {
  const abs = Math.abs(cents);
  const dollars = Math.floor(abs / 100)
    .toString()
    .replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  const body = `$${dollars}.${String(abs % 100).padStart(2, '0')}`;
  if (!signed) return body;
  return cents < 0 ? `−${body}` : `+${body}`;
}

/** Money out only (a day's "spent"), in cents. */
export function daySpent(s: Section) {
  return s.txns.reduce((a, t) => a + (t.cents < 0 ? -t.cents : 0), 0);
}

/** The flat list FlashList renders (built once per data set). */
export function buildItems(sections: Section[]): Item[] {
  const out: Item[] = [];
  for (const s of sections) {
    out.push({ type: 'header', id: `h-${s.id}`, label: s.label, count: s.txns.length, spent: daySpent(s) });
    s.txns.forEach((t, i) => out.push({ type: 'row', id: t.id, txn: t, last: i === s.txns.length - 1 }));
  }
  return out;
}
