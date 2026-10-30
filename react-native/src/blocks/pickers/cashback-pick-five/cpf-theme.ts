// Cashback Pick Five — layout, colour and copy, measured on the pin (two screens of a recording,
// 736 × 1600: nothing chosen yet, and one category chosen). Drawn in the pin's pixels.

export const REF_W = 402;
export const REF_H = 874;
export const S = 402 / 736;
export const PIN_W = 736;

export const C = {
  bg: '#EFF2FB',
  ink: '#10131C',
  grey: '#8A8E9C',
  card: '#FFFFFF',
  bannerA: '#DCCEFF',
  bannerB: '#F6F0FF',
  chip: '#DCD3FA',
  box: '#E6E8EF',
  boxOn: '#3E414D',
  pay: '#EF6155',
  purple: '#8528FF',
  pink: '#F795C6',
  button: '#1F222B',
  disabled: '#C9CCD6',
  disabledText: '#F2F3F7',
  slot: '#C9CDD8',
  fine: '#9DA1AE',
  link: '#7A4BE0',
  panel: '#FFFFFF',
  star: '#FFFFFF',
};

export type Cat = { pct: string; name: string; sub?: string; icon: number; color: string };

export const CATS: Cat[] = [
  { pct: '2%', name: 'All purchases', icon: 0, color: '#E9B8F2' },
  { pct: '10%', name: 'Cafés, bars and restaurants', icon: 1, color: '#F9AE1F' },
  { pct: '3%', name: 'Supermarkets', icon: 2, color: '#F2B232' },
  { pct: '10%', name: 'Clothes and shoes', icon: 3, color: '#CC7ACE' },
  { pct: '10%', name: 'Rideo Taxi', sub: 'Cashback on rides in every fare', icon: 4, color: '#FFD60A' },
  { pct: '30%', name: 'Reelhouse', sub: 'Tickets in the app or on the site', icon: 5, color: '#16161A' },
  { pct: '5%', name: 'Pharmacies', icon: 6, color: '#3CC48A' },
  { pct: '7%', name: 'Beauty', icon: 7, color: '#F7849E' },
  { pct: '4%', name: 'Fuel', icon: 8, color: '#4E8CF5' },
];

export const COPY = {
  brand: 'ORBIT PLUS',
  title: ['Choose 5 of 9 categories', 'for June'],
  banner: '100 000 Plus points',
  bannerSub: 'Raffle on June 30. Join in?',
  section: 'With your card',
  pay: 'Pay',
  connect: ['Connect Orbit Plus to start', 'spending your points'],
  choose: 'Choose',
  fine: ['14 days free, then $4.99 a month. By tapping the button you agree', 'to the promotion terms and the Orbit Plus subscription terms'],
  picks: 'Your five',
};

export const UI = {
  back: { x: 64, y: 172 },
  logo: { x: 364, y: 168 },
  title: { x: 44, y: [288, 346] },
  banner: { x: 30, y: 456, w: 676, h: 134 },
  section: { y: 660 },
  // measured on the pin: 29 px of padding inside the card on both sides (icon from x 58, box to x 678)
  list: { x: 29, y: 708, w: 677, top: 774, pitch: 102, sub: 32, iconX: 91, iconR: 33, textX: 156, boxX: 654, box: 48 },
  disabled: { x: 44, y: 1394, w: 648, h: 102 },
  panel: { y: 1190, tray: 1172, toggle: 1262, button: { x: 44, y: 1326, w: 648, h: 102 }, fine: [1462, 1486] },
  tray: { x0: 214, pitch: 84, r: 32 },
};

export const MAX_PICKS = 5;
