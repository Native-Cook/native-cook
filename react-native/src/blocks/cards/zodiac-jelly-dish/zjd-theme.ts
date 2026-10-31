// Zodiac Jelly Dish — palette and layout (pt), measured from the pin on a 393 × 852 screen.

export const C = {
  page: '#07080d',
  sheet: '#15161c',
  sheetLine: 'rgba(255,255,255,0.06)',
  close: '#24252c',
  title: '#ffffff',
  titleGlow: '#ff5fb4',
  sign: '#ffd23a',
  signGlow: '#ff9a1f',
  body: 'rgba(255,255,255,0.78)',
  buy: '#f26a1b',
  buyHi: '#ff8a3d',
  save: '#2a2b31',
  white: '#ffffff',
} as const;

/** The glass slab and its window, relative to the slab's centre. */
export const SLAB = {
  w: 287,
  h: 419,
  corner: 64,
  bevel: 30,
  window: { x: 16 - 62, y: -18 - 72, w: 124, h: 145, r: 14 },
} as const;

export const LAYOUT = {
  sheetTop: 61, // safe top → sheet (it starts under the status bar)
  slabFromTop: 352 - 59, // safe top → slab centre (pin: status bar ends at ~59 pt)
  bodyFromBottom: 852 - 644 - 34, // safe bottom → first line of the description
  buyFromBottom: 852 - 697 - 34,
  btnH: 46,
  btnGap: 15,
  btnW: 285,
} as const;

export const COPY = {
  title: ['CHEESE', 'PANCAKES'],
  sign: 'VIRGO',
  dates: '1.09–7.09',
  body: "Virgos, don't let anyone crush your raspberries — keep them safe inside.",
  price: '$3.20',
  was: '$4.00',
  added: 'Added to your order',
  save: 'Save for later',
} as const;
