/** Invented spending. Icons are Feather names. */
import type { Feather } from '@expo/vector-icons';

import { ICON_TINT } from './theme';

export type Transaction = {
  key: string;
  title: string;
  when: string;
  amount: string;
  icon: keyof typeof Feather.glyphMap;
  tint: string;
};

export const SPENDING: Transaction[] = [
  { key: 'groceries', title: 'Groceries', when: 'Today', amount: '-34$', icon: 'shopping-cart', tint: ICON_TINT.yellow },
  { key: 'rent', title: 'Rent', when: 'Today', amount: '-114$', icon: 'home', tint: ICON_TINT.lilac },
  { key: 'coffee', title: 'Corner Café', when: 'Yesterday', amount: '-4.80$', icon: 'coffee', tint: ICON_TINT.peach },
  { key: 'metro', title: 'Metro card', when: 'Yesterday', amount: '-25$', icon: 'navigation', tint: ICON_TINT.sky },
  { key: 'cinema', title: 'Cinema', when: 'Sat', amount: '-18$', icon: 'film', tint: ICON_TINT.rose },
  { key: 'gym', title: 'Climbing gym', when: 'Fri', amount: '-45$', icon: 'activity', tint: ICON_TINT.mint },
  { key: 'books', title: 'Bookshop', when: 'Thu', amount: '-22$', icon: 'book-open', tint: ICON_TINT.yellow },
  { key: 'pharmacy', title: 'Pharmacy', when: 'Wed', amount: '-12.60$', icon: 'plus-square', tint: ICON_TINT.mint },
];
