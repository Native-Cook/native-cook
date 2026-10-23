import type { ImageSourcePropType } from 'react-native';

export type CardData = {
  id: string;
  name: string;
  date: string;
  by: string;
  avatar?: ImageSourcePropType;
  art: 'mark' | 'figo' | 'loop';
  image: [string, string, string, string]; // picture: gradient top / middle / bottom, then the glow behind the icon (sampled from the pin)
};

// The pin shows a startup app, a food app and a third card cut off at the left edge; all three
// names, marks and people here are invented, with the same length and letter shapes as the pin's.
export const CARDS: CardData[] = [
  {
    id: 'loop',
    name: 'Halo',
    date: '12 Apr 25',
    by: 'Ira Koshal',
    art: 'loop',
    image: ['#B0CAD0', '#B4CFD4', '#ADC7CB', '#C6E2E8'],
  },
  {
    id: 'beehive',
    name: 'Beehive',
    date: '16 Oct 25',
    by: 'Beehive',
    art: 'mark',
    image: ['#83F2B2', '#A6EEBF', '#74E69B', '#E4FBEB'],
  },
  {
    id: 'figo',
    name: 'aro',
    date: '29 May 25',
    by: 'Anita Dhaliya',
    avatar: require('./assets/avatar-anika.png'),
    art: 'figo',
    image: ['#79B3C8', '#78BBD1', '#7EB3C9', '#CFCBAF'],
  },
];
