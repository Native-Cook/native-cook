export type Category = {
  name: string;
  /** An emoji drawn by the system font. */
  icon: string;
  color: string;
};

export type Month = {
  label: string;
  /** Spent per category, in the order of `categories`; 0 leaves the category out of that month. */
  amounts: number[];
  /** How many payments make up each amount. */
  counts: number[];
  /** The change from the month before, shown under the total. */
  change: { amount: number; percent: number; up: boolean };
};

// The ring keeps this order clockwise from the top, so a category always grows out of the same place.
export const CATEGORIES: Category[] = [
  { name: 'Eating Out', icon: '🍽️', color: '#7165f0' },
  { name: 'Subscriptions', icon: '📅', color: '#f0506a' },
  { name: 'Groceries', icon: '🥦', color: '#fba05a' },
  { name: 'Clothing', icon: '👕', color: '#c4c4cc' },
  { name: 'Coffee', icon: '☕', color: '#dadae0' },
  { name: 'Drinks', icon: '🍹', color: '#e6e6ea' },
];

export const MONTHS: Month[] = [
  {
    label: 'November 2024',
    amounts: [100, 104, 0, 0, 0, 0],
    counts: [4, 4, 0, 0, 0, 0],
    change: { amount: 51, percent: 20, up: false },
  },
  {
    label: 'December 2024',
    amounts: [100, 104, 133, 63, 9, 0],
    counts: [4, 4, 6, 2, 3, 0],
    change: { amount: 205, percent: 100, up: true },
  },
  {
    label: 'January 2025',
    amounts: [100, 104, 35, 63, 0, 42],
    counts: [5, 4, 2, 1, 0, 3],
    change: { amount: 65, percent: 15, up: false },
  },
];

// autoPlay, in seconds from the start of a loop: November rests, the pager swipes to December and
// January, one segment is tapped there, then it swipes back twice.
export const LOOP = 8.6;
/** Each swipe: when it starts, the page it leaves for, and how long it takes. */
export const SWIPES = [
  { at: 0.9, to: 1, duration: 0.7 },
  { at: 3.4, to: 2, duration: 0.7 },
  { at: 6.0, to: 1, duration: 0.8 },
  { at: 6.8, to: 0, duration: 0.8 },
];
/** In January the second category is tapped and let go. */
export const FOCUS_ON = 4.6;
export const FOCUS_OFF = 5.6;
export const FOCUS_CATEGORY = 1;
/** After a touch autoPlay waits this long, settles back to the first page, and plays again. */
export const USER_HOLD = 3;
export const RETURN_FOR = 0.6;

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function easeInOut(t: number) {
  'worklet';
  const u = clamp01(t);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

/** 0 to 1 with one soft overshoot, the shape of the pop spring, for autoPlay's tap. */
export function springOut(t: number) {
  'worklet';
  const u = clamp01(t);
  return 1 - Math.pow(1 - u, 3) * Math.cos(u * 4);
}

/** Where the pager is `t` seconds into a loop: a page number, fractional during a swipe. */
export function pageAt(t: number) {
  'worklet';
  let page = 0;
  for (let i = 0; i < SWIPES.length; i++) {
    const swipe = SWIPES[i];
    if (t < swipe.at) break;
    const from = i === 0 ? 0 : SWIPES[i - 1].to;
    page = from + (swipe.to - from) * easeInOut((t - swipe.at) / swipe.duration);
  }
  return page;
}

/** Each month's share of every category, from 0 to 1. */
export function shares(months: Month[]) {
  return months.map((month) => {
    let total = 0;
    for (const amount of month.amounts) total += amount;
    return month.amounts.map((amount) => (total > 0 ? amount / total : 0));
  });
}

export function total(month: Month) {
  let sum = 0;
  for (const amount of month.amounts) sum += amount;
  return sum;
}
