// Raffle Drum Spin — colours, copy and layout.
// Layout numbers are points on the 402 × 874 pt screen the design was measured on (x and sizes
// scale with the width, y with the height).

export const REF_W = 402;
export const REF_H = 874;

export const COPY = {
  title: 'Raffle Rumble',
  sub1: 'Buy tickets to enter the raffle',
  sub2: 'for awesome prizes.',
  chips: ['15+', '$1+', 'unlimited'],
  fav: 'Favorite Games',
  cards: [
    'A single chance to enter the Raffle and compete for prizes',
    'Double your chances to win a prize with two Raffle Entries',
    'Triple your odds with three Raffle Entries in the drum',
  ],
  play: 'Play',
  claim: 'Claim ticket',
  winner: 'WINNING ENTRY',
  entry: '0847',
  stubTop: 'ENTRIES',
} as const;

export const C = {
  bgTop: 0x6c5590, // the light behind the drum
  bgGlow: 0x8a63d2,
  bgMid: 0x22104a,
  bgDeep: 0x13023a,
  bgLow: 0x020106,
  bgFoot: 0x03041a,
  violet: 0x6d3ff0, // selection
  violetHi: 0x9b7bff,
  violetDeep: 0x4a1aa8,
  confetti: 0x7b4cf0,
  confettiHi: 0xa58aff,
  card: 0x0f0a1c,
  cardEdge: 0x2a2140,
  textHi: 0xf1eefa,
  textCard: 0xbcb4cc,
  textDim: 0x8c849e,
  chipLine: 0x6e6880,
  playTop: 0x6a1cd6,
  playLow: 0x4a0ca8,
  gold: 0xffd27a,
} as const;

export const L = {
  closeX: 362, closeY: 88, closeD: 44, // y from the screen top (below the status bar)
  drumX: 205, drumY: 178, drumR: 70, // screen centre and radius of the cage's sphere
  titleBase: 386, titleW: 272, sparkL: [50, 346] as const, sparkR: [345, 388] as const,
  sub1Y: 431, sub2Y: 453, subSize: 15.5,
  chipsY: 492,
  cardY: 561, cardW: 167, cardH: 202, cardR: 18, cardGap: 11, cardX0: 17,
  notchW: 58, notchD: 7,
  checkDX: 143, checkDY: 23, checkD: 24, // from the card's top-left
  ballDY: 60, // ball row centre from the card's top
  textDX: 13, textDY: 124, textSize: 15, textLine: 18,
  playX: 17, playY: 789, playW: 368, playH: 48,
  revealX: 201, revealY: 430, revealR: 60,
} as const;

/** Ball icons per card: centres from the card's left and radius (pt). */
export const ICON_BALLS: readonly (readonly [number, number])[][] = [
  [[84, 28]],
  [[55, 24], [107, 24]],
  [[42, 20], [83, 20], [124, 20]],
];

export function rgba(v: number, a: number) {
  'worklet';
  return `rgba(${(v >> 16) & 255},${(v >> 8) & 255},${v & 255},${a})`;
}
