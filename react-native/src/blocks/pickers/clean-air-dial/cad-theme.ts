// Clean Air Dial — layout, colour and copy, measured on the pin (a phone mock-up, its screen 670 px
// across from x = 265). Drawn in the pin's pixels: one point of the 402-point screen is 1 / S pixels,
// and the pin's y = 0 sits Y0 points down, under a header of ours.

export const REF_W = 402;
export const REF_H = 874;
export const S = 402 / 670;
export const X0 = 265; // the pin's x of the screen's left edge
export const Y0 = 220;

export const C = {
  bg: '#121212',
  tick: '#4A4A4A',
  green: '#29AB65',
  diskTop: '#070707',
  diskBot: '#101010',
  rim: '#1A1A1A',
  divider: '#202020',
  label: '#868686',
  white: '#F4F4F4',
  unit: '#9A9A9A',
  sub: '#6B6B6B',
  chip: '#1D1D1D',
  blue: '#3F6AF5',
  purple: '#5E55F2',
  power: '#F1F1F1',
  dust: '#D8C6A8',
  capText: '#0B2E14',
};

/** Air quality bands: the cap's colour and word. */
export const QUALITY = [
  { max: 6.5, word: 'Good', color: '#4FD6C8' },
  { max: 15.5, word: 'Moderate', color: '#18B063' },
  { max: 999, word: 'Poor', color: '#F2994A' },
];

export const LEVELS = [
  { name: 'Low', ticks: 6, omega: 0.35, target: -0.5 },
  { name: 'Medium', ticks: 12, omega: 0.7, target: 0 },
  { name: 'High', ticks: 21, omega: 1.6, target: 0.62 },
  { name: 'Turbo', ticks: 30, omega: 2.5, target: 0.88 },
];

export const COPY = {
  title: 'Air Purifier',
  room: 'Living room',
  pm: 'PM 2.5',
  stats: [
    { v: '7', u: '°C' },
    { v: '64', u: '%' },
    { v: '12', u: '%' },
  ],
  chips: ['Speed', 'Timer', 'Scenes'],
  timerOn: '30 mins · On',
  timerOff: '30 mins · Off',
};

export const UI = {
  header: { y: -200, sub: -160 },
  dial: { x: 600, y: 282, r0: 247, r1: 270, ticks: 60, disk: 230, tickW: 4.6 },
  pm: { y: 140, value: 212 },
  divider: 283,
  stats: { x: [470, 600, 728], v: 335, icon: 388, div: [537, 665], divY: [313, 407] },
  cap: { cy: 622, r: 185, text: 474 },
  chips: { x: [400, 600, 800], y: 648, w: 142, h: 78, label: 716, sub: 748 },
  bottom: { x: [400, 600, 800], y: 845 },
};

export const DUST = 300; // particles: the first 260 are the room's dust, the rest come in when it gets worse
export const BASE_DUST = 260;
