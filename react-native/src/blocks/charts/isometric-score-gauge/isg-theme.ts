// Isometric Score Gauge — layout, colour, copy and the scenarios, measured on the pin (a phone
// screen of 643 px across: x → (px − 47) · S, y → (px − 40) · S, S = 402 / 643).

export const REF_W = 402;
export const REF_H = 874;

export const C = {
  bg: '#050506',
  disc: '#111114',
  track: '#1D1D22',
  ink: '#F0F0F2',
  grey: '#5E5E66',
  caption: '#CFCFD4',
  card: '#17161B',
  cardEdge: '#2A2930',
  cardText: '#D9D9DE',
  glow: '#3B2A5E',
  up: '#3CCB6E',
  down: '#F0663A',
};

export const COPY = { logo: 'outlook', low: '300', high: '900', caption: 'MAKE A CHOICE. SEE WHERE IT TAKES YOU' };

export const GAUGE = {
  cx: 200,
  cy: 278,
  r: 121,
  width: 6,
  start: 156, // degrees, where 300 sits (screen space, y down) …
  sweep: 228, // … and the arc runs clockwise over the top to 900
  low: 300,
  high: 900,
  score: 765,
  numberY: 262,
  numberSize: 74,
  labels: { y: 335, x: [68.8, 327.5] },
};

export const UI = {
  back: { x: 45.6, y: 93.8 },
  logo: { x: 201, y: 93.8, size: 27 },
  captionY: 392.5,
  grid: { rows: [425, 623], w: 135, h: 183, pitch: 151.2, x0: 0.7 },
};

export type Scenario = { lines: string[]; icon: number; delta: number };

/** Columns of two scenario cards each, left to right; icons index the atlas (card, car, house, bag, paid, cut). */
export const COLUMNS: Scenario[][] = [
  [
    { lines: ['pay off my', 'credit card', 'outstanding'], icon: 4, delta: 26 },
    { lines: ['close oldest', 'credit card'], icon: 5, delta: -18 },
  ],
  [
    { lines: ['take a new', 'credit card'], icon: 0, delta: -12 },
    { lines: ['take a new', 'home loan'], icon: 2, delta: -22 },
  ],
  [
    { lines: ['take a new car', 'loan'], icon: 1, delta: -37 },
    { lines: ['take a new', 'loan'], icon: 3, delta: -29 },
  ],
  [
    { lines: ['raise my', 'credit limit'], icon: 0, delta: 9 },
    { lines: ['save up a', 'cushion'], icon: 3, delta: 14 },
  ],
];
