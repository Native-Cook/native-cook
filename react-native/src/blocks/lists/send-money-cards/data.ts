import type { ImageSourcePropType } from 'react-native';

export const CARD_DATA = {
  network: 'NUVA',
  balance: '$ 15,000.75',
  last4: '1234',
  valid: '12/35',
  holder: 'RAYAN ALMASRI',
} as const;

export type Favourite = { name: string; photo: ImageSourcePropType };

export const FAVOURITES: Favourite[] = [
  { name: 'Farid', photo: require('./assets/farid.jpg') },
  { name: 'Shadi', photo: require('./assets/shadi.jpg') },
  { name: 'Cyrus', photo: require('./assets/cyrus.jpg') },
];

export type Contact = { name: string; face: ImageSourcePropType };

export const CONTACTS: Contact[] = [
  { name: 'Noor Rahman', face: require('./assets/emoji-annette.png') },
  { name: 'Isabella Montgomery', face: require('./assets/emoji-cameron.png') },
  { name: 'Imani Brooks', face: require('./assets/emoji-jane.png') },
  { name: 'Yara Haddad', face: require('./assets/emoji-wade.png') },
  { name: 'Ravi Menon', face: require('./assets/emoji-devon.png') },
];

export type Payment = { merchant: string; date: string; amount: string; incoming: boolean };

// The pin's own rows, with the merchants swapped for invented names of the same length.
export const PAYMENTS: Payment[] = [
  { merchant: 'Orchard', date: 'May 24 , 2024', amount: '- $103.56', incoming: false },
  { merchant: 'Maple', date: 'May 24 , 2024', amount: '+ $1,000.97', incoming: true },
  { merchant: 'Orchard', date: 'May 24 , 2024', amount: '- $103.56', incoming: false },
  { merchant: 'Orchard', date: 'May 24 , 2024', amount: '+ $103.56', incoming: true },
];
