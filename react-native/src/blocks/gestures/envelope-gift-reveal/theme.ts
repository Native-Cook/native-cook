/**
 * A letter on a linen table under one warm lamp (upper left): a cream cotton envelope with real grain, an oxblood wax
 * seal pressed with an N, and inside it a heavy card from a bookshop that turns over to Nora's note in her own hand.
 * Instrument Serif for what is printed, Covered By Your Grace for what is written, Inter for the interface. 4 pt grid.
 */
export const FONTS = {
  serif: 'EnvelopeInstrumentSerif',
  hand: 'EnvelopeCoveredByYourGrace',
  ui: 'EnvelopeInterMedium',
  uiBold: 'EnvelopeInterSemiBold',
} as const;

export const COLORS = {
  table: '#1D1B18',
  tableHigh: '#2C2823',
  lamp: 'rgba(255, 214, 160, 0.16)',
  ink: '#F3ECDF',
  mute: 'rgba(243, 236, 223, 0.58)',
  faint: 'rgba(243, 236, 223, 0.36)',
  paper: '#EFE6D5',
  paperHigh: '#F7F0E2',
  paperLow: '#DCCFB8',
  paperInside: '#CDBFA5',
  paperShade: 'rgba(84, 62, 30, 0.22)',
  crease: 'rgba(96, 74, 40, 0.30)',
  pen: '#22314B',
  wax: '#7C1E22',
  waxHigh: '#B23A3A',
  waxLow: '#4C0F13',
  card: '#F6F0E4',
  cardEdge: 'rgba(80, 60, 30, 0.18)',
  cardInk: '#2A2219',
  cardMute: 'rgba(42, 34, 25, 0.56)',
  letterpress: '#2E4A3C',
  shadow: 'rgba(0, 0, 0, 0.55)',
  cta: '#F3ECDF',
  ctaInk: '#1D1B18',
} as const;

export const SPACE = { xs: 4, sm: 8, md: 12, lg: 16, side: 20, xl: 24, xxl: 32 } as const;

export const LAYOUT = {
  side: 24,
  headline: 44,
  headlineLine: 48,
  envW: 336,
  envH: 232,
  sealD: 56,
  sealY: 0.62,
  flapTip: 0.66,
  cardW: 340,
  cardH: 214,
  cardR: 8,
  cardInScale: 0.92,
  title: 40,
  titleLine: 44,
  hintH: 52,
  buttonH: 52,
} as const;

/**
 * Vertical positions. Sealed: a kicker and the headline under the status bar, the envelope a little below the
 * middle, the hint above the home indicator. Revealed: the title where the headline was, the card under it, its
 * caption, the button at the bottom.
 */
export function screenLayout(height: number, top: number, bottom: number) {
  const headlineTop = top + 40;
  const envTop = Math.round(height * 0.5 - LAYOUT.envH / 2 + 24);
  const foot = height - Math.max(bottom, 16) - 16;
  const buttonY = foot - LAYOUT.hintH / 2;
  const titleTop = headlineTop;
  const ctaTop = foot - LAYOUT.buttonH;
  // The card and its caption (about 60 pt) centred between the title and the button.
  const titleBottom = titleTop + LAYOUT.titleLine * 2;
  const cardTop = Math.round((titleBottom + ctaTop) / 2 - (LAYOUT.cardH + 60) / 2);
  const messageTop = cardTop + LAYOUT.cardH + 24;
  return { headlineTop, envTop, buttonY, titleTop, cardTop, messageTop, ctaTop };
}
export type ScreenLayout = ReturnType<typeof screenLayout>;

export const GIFT = {
  amount: '$50',
  value: 50,
  shop: 'Fable & Fern',
  shopLine: 'BOOKSELLERS · EST. 1987',
  kicker: 'FROM NORA · 14 OCTOBER',
  headline: 'Something\nfor you',
  title: 'From Nora,\nwith love',
  to: 'for Sam',
  note: ['Sam —', 'for the long train rides.', 'Pick something you', 'would never buy yourself.', '— Nora x'],
  caption: '$50 at Fable & Fern · in store or online',
  captionSub: 'Valid until 14 October 2027',
  cta: 'Add to Wallet',
  hint: 'Lift the flap',
  message: 'Sam, for the long train rides. Pick something you would never buy yourself. Nora',
} as const;
