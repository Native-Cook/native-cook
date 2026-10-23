import type { ImageSourcePropType } from 'react-native';

export const CARD_HOLDER = { name: 'Rohan Kesari', last4: '5678', network: 'ZIVA', valid: '05/29' } as const;

export const BALANCE = { label: 'Total Balance', amount: '25,867.40', currency: 'SAR' } as const;

export type Person = { name: string; photo: ImageSourcePropType; tint: string };

export const PEOPLE: Record<'warren' | 'edwards' | 'ingrid' | 'sofia', Person> = {
  warren: { name: 'Warren', photo: require('./assets/warren.jpg'), tint: '#A8E6C6' },
  edwards: { name: 'Edwards', photo: require('./assets/edwards.jpg'), tint: '#A7CCF8' },
  ingrid: { name: 'Ingrid', photo: require('./assets/ingrid.jpg'), tint: '#F7DC74' },
  sofia: { name: 'Sofia', photo: require('./assets/sofia.jpg'), tint: '#D8C4F4' },
};

export const QUICK = [PEOPLE.warren, PEOPLE.edwards, PEOPLE.ingrid, PEOPLE.sofia];

export type Payment = { person: Person; date: string; amount: string; incoming: boolean };

export const LATEST: Payment[] = [
  { person: PEOPLE.sofia, date: 'April 10,2025', amount: '-36.95 SAR', incoming: false },
  { person: PEOPLE.edwards, date: 'April 05,2025', amount: '-5.00 SAR', incoming: false },
  { person: PEOPLE.warren, date: 'March 25,2025', amount: '+175.75 SAR', incoming: true },
];

/** The next row of tiles, mostly under the tab bar as in the pin; every entry is its own payment. */
export const EARLIER: Payment[] = [
  { person: PEOPLE.ingrid, date: 'March 18,2025', amount: '-120.00 SAR', incoming: false },
  { person: PEOPLE.warren, date: 'March 12,2025', amount: '+48.50 SAR', incoming: true },
  { person: PEOPLE.sofia, date: 'March 02,2025', amount: '-12.75 SAR', incoming: false },
];
