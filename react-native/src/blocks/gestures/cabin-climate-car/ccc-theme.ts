// Cabin Climate Car — layout, colour and copy, measured on the pin (a phone screen 720 px across).
// Drawn in the pin's pixels: one point of the 402-point screen is 1 / S pixels.

export const REF_W = 402;
export const REF_H = 874;
export const S = 402 / 720;
export const PIN_W = 720;

export const C = {
  bgTop: '#D6DEF3',
  bg: '#E9ECF1',
  card: '#FFFFFF',
  ink: '#0C0E1A',
  label: '#4C5361',
  mute: '#757479',
  pill: '#0C0D21',
  teal: '#22D3A0',
  green: '#00E09A',
  blue: '#2563EB',
  chip: '#E9F7C2',
  tabSel: '#E6E8ED',
  seat: '#25272C',
  seatHi: '#4A4D55',
  cold: '#3B82F6',
  mild: '#22C7A9',
  warm: '#F97316',
};

export const COPY = {
  initials: 'RL',
  edit: 'Edit',
  cabin: 'Cabin controls',
  legroom: ['Distributed legroom', 'Extra rear legroom'],
  fan: ['Auto', 'Low', 'High'],
  prefs: 'Preferences',
  announce: 'In-car announcements',
  all: 'All',
  beta: 'Beta feature',
  betaRow: 'Quiet mode on longer rides',
  tabs: ['My trip', 'My car', 'Help', 'Account'],
};

export const TEMP = { min: 60, max: 80, rest: 70 };

export const UI = {
  pill: { x: 360, y: 181, w: 170, h: 58 },
  car: { x: 362, y: 318, w: 540 },
  cabin: { x: 73, y: 532 },
  temp: { x: 30, y: 580, w: 322, h: 490, r: 40, plus: 670, value: 825, minus: 978 },
  legroom: { x: 369, y: 580, w: 321, h: 235, r: 40, seats: [445, 559], seatY: 662, label: 765 },
  fan: { x: 369, y: 833, w: 321, h: 237, r: 40, icon: 918, label: 1010 },
  prefs: { y: 1131 },
  announce: { x: 30, y: 1187, w: 660, h: 113, r: 40 },
  beta: { x: 30, y: 1312, w: 660, h: 230, r: 40 },
  tab: { x: 40, y: 1418, w: 640, h: 106, xs: [130, 283, 436, 589] },
};
