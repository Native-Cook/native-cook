// Listening Buddy Paywall — layout, colour and copy, measured on the pin (a 736 × 1309 screen).
// Drawn in the pin's pixels: one point of the 402-point screen is 1 / S pixels, and the pin's y = 0
// sits DY points down (the sky goes on above it).

export const REF_W = 402;
export const REF_H = 874;
export const S = 402 / 736;
export const DY = 120;
export const PIN_W = 736;

export const C = {
  sky: '#FAF0E4',
  cloud: '#FDF8F2',
  hillA: '#F5E5D5',
  hillB: '#FDF4EB',
  body: '#F18E4B',
  bodyLight: '#F6A56E',
  bodyShade: '#E27A36',
  cheek: '#EE7B33',
  leaf: '#7DB36A',
  leafDark: '#5E9A55',
  phones: '#555B75',
  phonesLight: '#737A96',
  face: '#4D5268',
  bushA: '#828DAB',
  bushB: '#A9B0DA',
  title: '#5F6680',
  sub: '#504B45',
  card: '#F7F3EF',
  border: '#F0A25E',
  header: '#959DB8',
  label: '#5F6680',
  per: '#6C6E7B',
  note: '#5F6680',
  fine: '#B5B3B0',
  bar: '#6E7389',
  close: '#5F6377',
  white: '#FFFFFF',
  dot: '#CAC8C9',
  dotOff: '#E8DFD6',
};

export const PLANS = [
  { name: 'MONTHLY', price: '12,99 €', per: 'PER MONTH', lines: ['Recurring billing. Cancel anytime.', "*That's 12,99 € a month."], perMonth: 1299 },
  { name: 'ANNUAL', price: '94,99 €*', per: 'PER YEAR', lines: ['Recurring billing. Cancel anytime.', "*That's 7,92 € a month."], perMonth: 792 },
  { name: 'LIFETIME', price: '449,99 €', per: 'ONE PAYMENT', lines: ['No subscription. Pay once.', '*Yours for good, every update included.'], perMonth: -1 },
];

export const COPY = {
  title: 'Subscribe now',
  sub: ['Get access to hundreds of guided meditations in', 'the Stillwater Library.'],
  popular: ['MOST', 'POPULAR'],
  links: ['Restore Purchase', 'Terms & Conditions'],
  cta: 'CONTINUE',
};

export const UI = {
  close: { x: 668, y: 115, r: 44 },
  title: { y: 222 },
  sub: [296, 343],
  dots: { y: 593, x: [336, 368, 400] },
  buddy: { x: 128, y: 640 },
  white: 655,
  cards: { y: 752, h: 253, x: [49, 275, 501], w: 186, r: 10 },
  header: { h: 66 },
  frame: { pad: 10, r: 20, w: 3 }, // outer radius = the card's 10 + the 10 of padding
  lines: [1063, 1101],
  links: 1152,
  bar: { y: 1183, h: 126 },
};
