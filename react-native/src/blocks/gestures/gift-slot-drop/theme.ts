/**
 * A wallet's welcome gift, at night. A warm charcoal page lit from one place only: the slot in the glass shelf under
 * the cards, where a gift goes in and comes back activated. Four gift cards from invented shops, each its own
 * material, drawn in code. Inter for words, JetBrains Mono for codes and small labels. 4 pt grid, 16 pt edges.
 */
export const COLOR = {
  page: '#0D0B09',
  ink: '#F6F2EC',
  mute: 'rgba(246,242,236,0.58)',
  faint: 'rgba(246,242,236,0.36)',
  amber: '#FFB54A',
  amberInk: '#FFD08A',
  slotDeep: '#050403',
  lip: 'rgba(255,236,200,0.55)',
  button: '#F6F2EC',
  buttonInk: '#16120D',
} as const;

/** Glass tints, linear [r, g, b, strength]. */
export const TINT = {
  chrome: [0.95, 0.9, 0.84, 0.06],
  shelf: [1.0, 0.86, 0.68, 0.07],
  code: [1.0, 0.95, 0.88, 0.1],
} as const;

export const TYPE = {
  eyebrow: { face: 'mono', size: 11 },
  title: { face: 'semibold', size: 28 },
  sub: { face: 'regular', size: 15 },
  nav: { face: 'medium', size: 15 },
  hint: { face: 'medium', size: 13 },
  mark: { face: 'semibold', size: 14 },
  offer: { face: 'semibold', size: 54 },
  offerLine: { face: 'medium', size: 15 },
  cap: { face: 'regular', size: 13 },
  tag: { face: 'mono', size: 10 },
  codeLabel: { face: 'mono', size: 11 },
  code: { face: 'mono', size: 22 },
  step: { face: 'regular', size: 15 },
  stepNum: { face: 'mono', size: 13 },
  button: { face: 'semibold', size: 16 },
} as const;
export type TypeRole = keyof typeof TYPE;

export const LAYOUT = {
  edge: 16,
  nav: 44,
  /** Below the safe area: eyebrow baseline, then the two title lines. */
  eyebrowY: 70,
  titleY: 106,
  titleLine: 34,
  /** The carousel: card centre below the safe area. */
  cardY: 356,
  cardW: 212,
  cardH: 272,
  cardR: 22,
  /** Neighbours: smaller, turned away, kept clear of the centre card by `gap`. */
  sideScale: 0.84,
  sideTurn: 20,
  gap: 16,
  /** The glass shelf and the slot in it, below the safe area. */
  shelfY: 612,
  shelfW: 304,
  shelfH: 64,
  slotW: 236,
  slotH: 12,
  hintY: 548,
  /** The activated gift: a larger card that rises out of the slot and turns over. */
  giftW: 248,
  giftH: 318,
  giftY: 330,
  /** Under the activated gift: how to use it and the button. */
  stepsY: 522,
  buttonH: 52,
} as const;

export type Gift = {
  id: string;
  shop: string;
  /** What the shop is, for VoiceOver and the steps. */
  kind: string;
  offer: string;
  line: string;
  cap: string;
  code: string;
  steps: [string, string];
  action: string;
  bg: string;
  bg2: string;
  ink: string;
  sub: string;
  /** Which emblem the card carries (drawn in code). */
  mark: 'bowl' | 'rails' | 'leaf' | 'bean';
};

/** Four invented shops, four materials: fired clay, a rail line's livery, a plant shop's paper, roasted coffee. */
export const GIFTS: Gift[] = [
  {
    id: 'kiln', shop: 'Kiln & Clay', kind: 'studio ceramics', offer: '20%', line: 'off your first order', cap: 'up to $30',
    code: 'KILN-20-H7QM', steps: ['Add pieces worth $40 or more', 'Paste the code at checkout'], action: 'Shop Kiln & Clay',
    bg: '#B9573A', bg2: '#8E3B24', ink: '#FFF4EA', sub: 'rgba(255,244,234,0.72)', mark: 'bowl',
  },
  {
    id: 'northline', shop: 'Northline', kind: 'rail tickets', offer: '$15', line: 'off your first trip', cap: 'any route, any day',
    code: 'NORTH-15-K4QZ', steps: ['Book any single or return', 'Paste the code before paying'], action: 'Book with Northline',
    bg: '#1F4D40', bg2: '#123128', ink: '#EAF4EE', sub: 'rgba(234,244,238,0.7)', mark: 'rails',
  },
  {
    id: 'fernhouse', shop: 'Fernhouse', kind: 'houseplants', offer: '2 for 1', line: 'on any houseplant', cap: 'pots included',
    code: 'FERN-241-P2LD', steps: ['Pick two plants, pay for one', 'Paste the code at checkout'], action: 'Shop Fernhouse',
    bg: '#EDE6D6', bg2: '#D9CFB9', ink: '#1F2A22', sub: 'rgba(31,42,34,0.66)', mark: 'leaf',
  },
  {
    id: 'ember', shop: 'Ember Roasters', kind: 'coffee', offer: 'Free', line: 'flat white for a week', cap: 'one a day, 7 days',
    code: 'EMBER-7-W9TB', steps: ['Show the code at the counter', 'One flat white a day, 7 days'], action: 'Find a café',
    bg: '#2C1E17', bg2: '#1A110C', ink: '#F1D9BC', sub: 'rgba(241,217,188,0.66)', mark: 'bean',
  },
];

/** The screen opens on Northline. */
export const START_INDEX = 1;

/** Where things sit for a screen `width` × `height` with safe-area insets `top` / `bottom`. */
export function makeLayout(width: number, height: number, top: number, bottom: number) {
  const cx = width / 2;
  const sideW = LAYOUT.cardW * LAYOUT.sideScale;
  return {
    width,
    height,
    top,
    bottom,
    cx,
    cardY: top + LAYOUT.cardY,
    /** Centre-to-centre distance: the neighbour's near edge sits `gap` from the centre card. */
    spacing: LAYOUT.cardW / 2 + sideW / 2 + LAYOUT.gap,
    slotY: top + LAYOUT.shelfY,
    hintY: top + LAYOUT.hintY,
    giftY: top + LAYOUT.giftY,
    stepsY: top + LAYOUT.stepsY,
    buttonY: height - Math.max(bottom, LAYOUT.edge) - 8 - LAYOUT.buttonH,
    /** Under the shelf: which gift of four, and the fine print on the screen's last line. */
    dotsY: top + LAYOUT.shelfY + 76,
    printY: height - Math.max(bottom, LAYOUT.edge) - 20,
  };
}
export type Layout = ReturnType<typeof makeLayout>;
