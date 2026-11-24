// Penny, a budgeting app. Names, places and amounts are invented.

export type CoachStep = { title: string; body: string };

/** One step per highlighted element, in tour order: the add button, the month card, the Insights tab. */
export const STEPS: CoachStep[] = [
  { title: 'Log a spend in seconds', body: 'Tap + after you pay. Penny picks the category, you just check the amount.' },
  { title: 'Your month at a glance', body: 'See what’s left before payday. It updates every time you log a spend.' },
  { title: 'Find where it goes', body: 'Insights breaks your spending down by week, so surprises show up early.' },
];

export type Category = { name: string; spent: number; color: string };

export const CATEGORIES: Category[] = [
  { name: 'Groceries', spent: 412.3, color: '#3E8E6A' },
  { name: 'Eating out', spent: 286.9, color: '#E58A5B' },
  { name: 'Transport', spent: 164.0, color: '#5B8BD9' },
  { name: 'Home', spent: 421.4, color: '#C9A23E' },
];

export const BUDGET = 2000;

export type Spend = { place: string; category: string; amount: number; time: string; color: string; glyph: 'cart' | 'cup' | 'train' | 'leaf' };

export const RECENT: Spend[] = [
  { place: 'Greenway Market', category: 'Groceries', amount: 38.45, time: 'Today, 6:12 PM', color: '#3E8E6A', glyph: 'cart' },
  { place: 'Little Fern Café', category: 'Eating out', amount: 6.8, time: 'Today, 8:41 AM', color: '#E58A5B', glyph: 'cup' },
  { place: 'Metro card top-up', category: 'Transport', amount: 25.0, time: 'Yesterday', color: '#5B8BD9', glyph: 'train' },
  { place: 'Hollis Plant Co.', category: 'Home', amount: 19.9, time: 'Sun, Nov 22', color: '#C9A23E', glyph: 'leaf' },
];

export const TABS = ['Home', 'Budgets', 'Insights', 'Profile'] as const;
/** The tab the tour points at. */
export const TOUR_TAB = 2;
