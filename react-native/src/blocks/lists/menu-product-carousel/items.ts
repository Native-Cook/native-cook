import type { ImageSourcePropType } from 'react-native';

import { px, pxX } from './theme';

// One menu card. Measured per card on the pin's rest frames, in pt: x = card left at rest;
// t = title font size, horizontal squeeze, centre x and glyph top (from the card's corner);
// img = the photo's frame in the card, set so the product's own outline lands where the pin's does.
type Frame = { l: number; t: number; w: number; h: number };
export type Item = {
  id: string;
  title: string;
  image: ImageSourcePropType;
  x: number;
  t: { size: number; sx: number; cx: number; top: number };
  img: Frame;
  gap: [number, number]; // centre-to-centre distance to the left and right neighbour at rest, pt
};

const DISH = {
  coffee: { src: require('./assets/coffee.png'), img: { l: 42.2, t: 102.5, w: 163.1, h: 184.3 } },
  burger: { src: require('./assets/burger.png'), img: { l: 28.3, t: 86.7, w: 167.1, h: 188.9 } },
  fries: { src: require('./assets/fries.png'), img: { l: 16.8, t: 55.0, w: 225.2, h: 254.6 } },
  pizza: { src: require('./assets/pizza.png'), img: { l: 30.1, t: 78.9, w: 184.1, h: 208.1 } },
  coldrink: { src: require('./assets/coldrink.png'), img: { l: 1.5, t: 41.8, w: 220.8, h: 249.6 } },
  pasta: { src: require('./assets/pasta.png'), img: { l: 19.2, t: 78.0, w: 180.2, h: 203.7 } },
  taco: { src: require('./assets/taco.png'), img: { l: 30.1, t: 83.7, w: 176.3, h: 199.3 } },
  sandwich: { src: require('./assets/sandwich.png'), img: { l: 21.9, t: 82.7, w: 186.4, h: 210.7 } },
};

function item(
  id: string,
  dish: keyof typeof DISH,
  title: string,
  cardX: number,
  size: number,
  sx: number,
  cx: number,
  top: number,
  gap: [number, number],
): Item {
  return {
    id,
    title,
    image: DISH[dish].src,
    img: DISH[dish].img,
    x: pxX(cardX),
    t: { size, sx, cx: px(cx), top: px(top) },
    gap,
  };
}

// The pin's list: Coldrink sits left of Coffee at the start and right of Sandwich at the end.
export const ITEMS: Item[] = [
  item('coldrink-0', 'coldrink', 'Coldrink', 178, 56.5, 0.819, 176.5, 57.3, [292, 267.4]),
  item('coffee', 'coffee', 'Coffee', 187, 65.9, 0.78, 170.0, 59.3, [258.9, 272.6]),
  item('burger', 'burger', 'Burger', 167, 54.0, 0.890, 179.3, 62.8, [274.3, 281.8]),
  item('fries', 'fries', 'French Fries', 167, 46.4, 0.856, 174.5, 77.3, [284.5, 283.1]),
  item('pizza', 'pizza', 'Pizza', 189, 56.4, 0.823, 171.0, 72.3, [285.9, 290]),
  item('coldrink', 'coldrink', 'Coldrink', 178, 56.5, 0.819, 176.5, 57.3, [292, 267.4]),
  item('pasta', 'pasta', 'Pasta', 159, 46.9, 1.025, 166.7, 107.3, [269.5, 264.7]),
  item('taco', 'taco', 'Taco', 191, 66.1, 0.956, 164.2, 85.8, [267.4, 257.9]),
  item('sandwich', 'sandwich', 'Sandwich', 165, 54.3, 0.831, 170.2, 93.3, [260.6, 264.7]),
  item('coldrink-2', 'coldrink', 'Coldrink', 178, 56.5, 0.819, 176.5, 57.3, [292, 267.4]),
];
export const FIRST = 1; // the loop opens on Coffee
