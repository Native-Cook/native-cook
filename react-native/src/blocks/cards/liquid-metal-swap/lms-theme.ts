// Liquid Metal Swap — the swap screen of an invented wallet, its tokens and layout.

export const COLOR = {
  page: '#0B0C10',
  card: '#15171D',
  cardEdge: 'rgba(255,255,255,0.06)',
  ink: '#F2F3F5',
  mute: '#8A8F9C',
  faint: '#5C616D',
  silver: '#E6E9EF',
  gold: '#E8C27A',
  goldInk: '#2A1E08',
  silverInk: '#0B0C10',
  good: '#7BD88F',
} as const;

/** The page colour as the shader's floats (sRGB 0 … 1). */
export const PAGE_RGB: [number, number, number] = [0x0b / 255, 0x0c / 255, 0x10 / 255];

export const FONT = {
  regular: 'LmsInter-Regular',
  medium: 'LmsInter-Medium',
  semibold: 'LmsInter-SemiBold',
} as const;

export const SPACE = { xs: 4, s: 8, m: 12, l: 16, xl: 24, xxl: 32 } as const;

export type Token = {
  symbol: string;
  name: string;
  amount: string;
  fiat: string;
  balance: string;
  /** The coin's metal at normal incidence (linear-ish sRGB). */
  tint: [number, number, number];
  colour: string;
};

// role 0: you hold Prism (the silver coin) and swap it for Spark; role 1: the other way round
export const TOKENS: [Token, Token] = [
  { symbol: 'PRSM', name: 'Prism', amount: '250', fiat: '$412.50', balance: 'Balance 1,820', tint: [0.8, 0.8, 0.79], colour: COLOR.silver },
  { symbol: 'SPRK', name: 'Spark', amount: '1,204.83', fiat: '$412.17', balance: 'Balance 96.4', tint: [1.0, 0.74, 0.36], colour: COLOR.gold },
];

export const RATE = ['1 PRSM = 4.8193 SPRK', '1 SPRK = 0.2075 PRSM'] as const;

export const CARD_H = 112;
export const CARD_GAP = 8;
export const ROW_H = 36;
export const BUTTON_H = 56;

export type Layout = {
  w: number;
  h: number;
  barTop: number;
  stageTop: number;
  stageH: number;
  cardsTop: number;
  rowsTop: number;
  buttonTop: number;
  side: number;
  cardW: number;
};

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const side = SPACE.l;
  const barTop = top + SPACE.xs;
  const stageTop = barTop + 44;
  const buttonTop = h - Math.max(bottom, SPACE.l) - SPACE.s - BUTTON_H;
  const rowsTop = buttonTop - SPACE.l - 3 * ROW_H;
  const cardsTop = rowsTop - SPACE.l - (2 * CARD_H + CARD_GAP);
  const stageH = Math.max(200, cardsTop - SPACE.s - stageTop);
  return { w, h, barTop, stageTop, stageH, cardsTop, rowsTop, buttonTop, side, cardW: w - 2 * side };
}
