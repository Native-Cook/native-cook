import type { ImageSourcePropType } from 'react-native';

export type MarkKind = 'rings' | 'petals' | 'ribbon' | 'lens' | 'wordmark';

export type Stock = {
  id: string;
  name: string;
  change: number; // % over the past year
  // The pin shows two lines cut off before "more"; the tail fades out letter by letter.
  lines: [string, string];
  tail: string;
  mark: MarkKind;
  markBg: string;
  brand: string; // ink of the share certificate's engraving
  card: ImageSourcePropType;
  backdropTint: string; // placeholder colour while the image decodes
};

// Invented companies standing in for the five real ones in the pin, same length and look.
export const STOCKS: Stock[] = [
  {
    id: 'mira',
    name: 'Mira',
    change: -18,
    lines: ["Connects billions through the world's", 'most popular apps while using a'],
    tail: 'dv',
    mark: 'rings',
    markBg: '#1f6ff2',
    brand: '#1d5fd6',
    card: require('./assets/card-mira.jpg'),
    backdropTint: '#1b2a8a',
  },
  {
    id: 'tilecraft',
    name: 'Tilecraft',
    change: -16,
    lines: ['Equips the modern world with essential', 'software and massive cloud syste'],
    tail: 'm',
    mark: 'petals',
    markBg: '#f4f4f6',
    brand: '#1f7a5c',
    card: require('./assets/card-tilecraft.jpg'),
    backdropTint: '#9aa25c',
  },
  {
    id: 'reelbox',
    name: 'Reelbox',
    change: -33,
    lines: ['Entertains millions of households with an', 'endless library of hit movies, seri'],
    tail: 'e',
    mark: 'ribbon',
    markBg: '#2a0d10',
    brand: '#b3121b',
    card: require('./assets/card-reelbox.jpg'),
    backdropTint: '#3a0004',
  },
  {
    id: 'voltex',
    name: 'Voltex',
    change: 40,
    lines: ["Powers the brains of the world's most", 'advanced AI with unmatched ha'],
    tail: 'r',
    mark: 'lens',
    markBg: '#6fb51a',
    brand: '#4a8a12',
    card: require('./assets/card-voltex.jpg'),
    backdropTint: '#2c5a10',
  },
  {
    id: 'nova',
    name: 'Nova',
    change: -12,
    lines: ['Facilitates millions of daily payments as', 'the invisible backbone of global '],
    tail: 'c',
    mark: 'wordmark',
    markBg: '#1d4fb0',
    brand: '#1d4fb0',
    card: require('./assets/card-nova.jpg'),
    backdropTint: '#2c58c8',
  },
];

export const GIFT_AMOUNT = 'USD 3';
