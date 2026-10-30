// Savings Future Sim — layout, colour and copy, measured on the pin (a phone screen 736 × 1600).
// Drawn in the pin's pixels: one point of the 402-point screen is 1 / S pixels.

export const REF_W = 402;
export const REF_H = 874;
export const S = 402 / 736;
export const PIN_W = 736;

export const C = {
  bgTop: '#17182C',
  bg: '#0E0F1B',
  card: '#232331',
  chip: '#2B2C3D',
  track: '#3A3A4A',
  purple: '#A39BF0',
  purpleDeep: '#7A70E0',
  white: '#FFFFFF',
  mute: '#9B9BAA',
  body: '#8E8E9E',
  divider: '#383846',
  button: '#3A3A4A',
  green: '#5AD27A',
  blue: '#4DA8F0',
};

export const COPY = {
  joined: 'Joined Waitlist',
  title: ['You’re on your way to the', 'future of Savings.'],
  rateLabel: 'Your Savings Rate',
  grow: 'See your savings grow.',
  body: ['Explore what 6,00% could look like over', 'time, and see how compounding can', 'help your savings grow.'],
  button: 'Simulate My Future',
  skip: 'Skip the Waitlist',
  more: 'Learn More',
};

/** What the simulation assumes: a starting balance, a monthly deposit and the rate (in %). */
export const PLAN = { start: 10000, monthly: 200, rate: 6, minRate: 4, maxRate: 8, years: 30 };

export const UI = {
  menu: { x: 84, y: 161, r: 40 },
  help: { x: 652, y: 161, r: 40 },
  joined: { y: 257 },
  title: [328, 383],
  card: { x: 38, y: 455, w: 660, h: 785, r: 56 },
  gauge: { x: 368, y: 770, r: 260, w: 28 },
  value: { y: 680 },
  label: { y: 760 },
  divider: 833,
  grow: 900,
  body: [955, 999, 1043],
  button: { x: 80, y: 1105, w: 576, h: 91 },
  chart: { x: 92, y: 632, w: 552, h: 150 }, // under the balance, above the divider
  valueChart: { y: 548, label: 604 },
  card2: { x: 38, y: 1278, w: 660, h: 340, r: 56 },
  share: { y: 1490, r: 52, x: [148, 295, 441, 588] },
};
