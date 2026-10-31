// Coin Drop Reward — colours, type, the missions and the layout.

export const C = {
  page: '#FBFAFF',
  ink: '#191A23',
  mute: '#8B8D9A',
  line: '#EEEDF5',
  accent: '#6A5CFF',
  blue: '#2F6BFF',
  blueSoft: '#EAF0FF',
  tile: '#FFFFFF',
  stamp: '#F1F0F7',
} as const;

export const FONT = {
  regular: 'CdrInter-Regular',
  medium: 'CdrInter-Medium',
  semibold: 'CdrInter-SemiBold',
} as const;

export type Mission = { title: string; sub: string };
export const MISSIONS: Mission[] = [
  { title: 'Cedar desk lamp', sub: 'Visit the shop · 5 P' },
  { title: 'Linen throw blanket', sub: 'Visit the shop · 5 P' },
];
export const BONUS = { title: 'Mystery product', when: 'Opens at 1 PM' };
export const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
export const TODAY = 5; // Saturday
export const STAMPED = [0, 1, 2, 4]; // days already collected this week

export const ROW_H = 72;
export const COIN_D = 44; // the row's coin
export const COIN_S = 22; // the 3D coin's unit size on the row (pt): its radius
export const HERO_S = 66; // … in the toast
export const TILE = 236;
export const WALLET = { w: 124, h: 40 } as const;

export function makeLayout(w: number, h: number, top: number, bottom: number) {
  const side = 20;
  const walletTop = top + 8;
  const titleTop = walletTop + WALLET.h + 12;
  const sectionTop = titleTop + 30 + 4 + 20 + 28;
  const rowsTop = sectionTop + 18 + 8;
  const rowCoin = MISSIONS.map((_, i) => ({ x: side + COIN_D / 2, y: rowsTop + i * ROW_H + ROW_H / 2 }));
  const bonusLabelTop = rowsTop + MISSIONS.length * ROW_H + 20;
  const bonusTop = bonusLabelTop + 18 + 8;
  const tileCx = w / 2;
  const walletLeft = w - side - WALLET.w;
  const walletIcon = { x: walletLeft + 24, y: walletTop + WALLET.h / 2 };
  const daysTop = h - Math.max(bottom, 16) - 76;
  // the toast stands in the open space between the bonus mission and the week
  const tileCy = Math.round((bonusTop + ROW_H + daysTop) / 2);
  const toastCoin = { x: tileCx, y: tileCy - 24 };
  return { w, h, side, titleTop, sectionTop, rowsTop, rowCoin, bonusLabelTop, bonusTop, tileCx, tileCy, toastCoin, walletLeft, walletTop, walletIcon, daysTop };
}
