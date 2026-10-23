/** Invented spending of a private-banking statement, and the friends a bill is split with. */
import type { GlyphName } from './Glyphs';

export type Transaction = {
  key: string;
  title: string;
  category: string;
  day: string;
  amount: number; // dollars, spends negative
  icon: GlyphName;
};

export const BALANCE = 1148.9;
export const CARD_NUMBER = '4021';

export const SPENDING: Transaction[] = [
  { key: 'greenleaf', title: 'Greenleaf Market', category: 'Groceries', day: 'Today', amount: -34, icon: 'bag' },
  { key: 'harbour', title: 'Harbour Electric', category: 'Utilities', day: 'Today', amount: -114, icon: 'bolt' },
  { key: 'saigon', title: 'Little Saigon Kitchen', category: 'Dining', day: 'Yesterday', amount: -96.6, icon: 'dining' },
  { key: 'metro', title: 'Metro card', category: 'Transport', day: 'Yesterday', amount: -25, icon: 'tram' },
  { key: 'cinema', title: 'Lumen Cinema', category: 'Entertainment', day: 'Sat', amount: -18, icon: 'ticket' },
  { key: 'boulder', title: 'Boulder Hall', category: 'Fitness', day: 'Fri', amount: -45, icon: 'dumbbell' },
];

export type Person = { name: string; initials: string };

/** You and the people you split with last time, in the order a bill breaks. Monograms, never portraits. */
export const PEOPLE: Person[] = [
  { name: 'You', initials: 'LT' },
  { name: 'Mai', initials: 'MP' },
  { name: 'Leo', initials: 'LN' },
  { name: 'Kai', initials: 'KO' },
  { name: 'Ana', initials: 'AR' },
];

export const MAX_PEOPLE = PEOPLE.length;

/** Equal shares in cents; the first share (yours) takes the rounding, so the shares always add up to the bill. */
export function shares(amount: number, n: number): number[] {
  'worklet';
  const total = Math.round(Math.abs(amount) * 100);
  const each = Math.floor(total / n);
  const out: number[] = [];
  for (let k = 0; k < n; k++) out.push((k === 0 ? total - each * (n - 1) : each) / 100);
  return out;
}

/** "Mai, Leo" / "Mai and Leo" / "4 people". */
export function others(n: number, joiner: ', ' | ' and '): string {
  const names = PEOPLE.slice(1, n).map((p) => p.name);
  if (names.length <= 1) return names.join('');
  if (names.length > 3) return `${names.length} people`;
  if (joiner === ', ') return names.join(', ');
  return `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`;
}

/**
 * One money style everywhere: an en-dash minus (Montserrat has no U+2212), the dollar sign before the number,
 * thousands grouped, always two decimals. Returns the whole part and the cents apart, so the cents can be smaller.
 */
export function money(value: number, signed = true): { whole: string; cents: string } {
  'worklet';
  const cents = Math.round(Math.abs(value) * 100);
  const digits = String(Math.floor(cents / 100));
  let grouped = '';
  for (let i = 0; i < digits.length; i++) {
    if (i > 0 && (digits.length - i) % 3 === 0) grouped += ',';
    grouped += digits[i];
  }
  const rest = cents % 100;
  return { whole: `${signed && value < 0 && cents > 0 ? '–' : ''}$${grouped}`, cents: `.${rest < 10 ? '0' : ''}${rest}` };
}

export function moneyText(value: number, signed = true): string {
  'worklet';
  const m = money(value, signed);
  return m.whole + m.cents;
}

/** The week's total of the list, as the section header shows it. */
export function weekTotal(amounts: number[]): number {
  return Math.round(amounts.reduce((sum, a) => sum + a, 0) * 100) / 100;
}
