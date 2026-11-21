// Text Effect Keys: tokens. Positions are points on the 402 × 874 pt canvas (iPhone 17 Pro): the thread, the
// composer and the panel are anchored to the bottom edge, the nav row to the top.

export const C = {
  page: '#0F0D0B',
  panel: '#1A1714',
  keyTop: '#34302B',
  keyBottom: '#2A2622',
  skirt: '#14110F',
  ink: '#F4F1EA',
  mute: '#7E786F',
  glyph: '#8C857B',
  lime: '#D8F34A',
  limePressed: '#C4DE3E',
  onLime: '#16190A',
  received: '#1E1B18',
  disc: '#1E1B18',
  fieldStroke: '#2B2723',
  avatar: '#2B2723',
} as const;

/** One damped spring (mass 1): damping, stiffness. */
export type Spring = { d: number; k: number };

/** Design-system springs. */
export const SPRING = {
  glide: { d: 22.1, k: 157.9 },
  pop: { d: 19, k: 195 },
  snap: { d: 30.5, k: 322.3 },
  chain: { d: 16, k: 180 },
} as const;

/** Geometry (pt). */
export const G = {
  // thread (before the send; it rises by `rise` when a new bubble arrives)
  rise: 44,
  stampY: 424,
  received: [
    { y: 300, text: 'guess who got us front row' },
    { y: 340, text: 'FRONT. ROW.' },
  ],
  receivedX: 16,
  bubbleH: 32,
  bubbleR: 16,
  bubbleTail: 5,
  bubblePad: 11,
  bubbleRight: 384,
  oldY: 443, // the sent bubble at rest; a new one takes this place and the old one rises
  seenY: 487, // centre of `Seen`
  // composer
  plus: { x: 13, y: 506, d: 31 },
  field: { x: 56, y: 505, w: 328, h: 34, r: 17 },
  fieldTextX: 69,
  send: { x: 354, y: 510, d: 25 },
  // panel
  panelY: 554,
  fmtY: 579,
  fmtX: [60, 153, 248, 341],
  keyX: [15, 206],
  keyY: [606, 665, 724, 783],
  keyW: 181,
  keyH: 47,
  keyR: 14,
  skirt: 4,
  // the stage the word lifts to for Ripple and Bloom (baseline)
  stageY: 240,
  // nav row
  navTop: 62,
  avatarD: 32,
} as const;

export const EFFECTS = ['Big', 'Small', 'Shake', 'Nod', 'Explode', 'Ripple', 'Bloom', 'Jitter'] as const;
export type EffectName = (typeof EFFECTS)[number];
export const EF = { big: 0, small: 1, shake: 2, nod: 3, explode: 4, ripple: 5, bloom: 6, jitter: 7 } as const;

export const COPY = {
  stamp: 'Today 21:47',
  old: 'no way',
  word: 'Whaaaaat',
  seen: 'Seen',
  name: 'Odile',
  placeholder: 'Message',
} as const;

/** Type sizes (pt). */
export const TYPE = {
  body: 15,
  label: 17,
  small: 13,
  hero: 4, // thrown glyphs: 4 × 15 pt = 60 pt
  stage: 56 / 15,
} as const;

/** Font aliases registered with the paragraph builder. */
export const FONT = {
  regular: 'TEK-Geist-Regular',
  medium: 'TEK-Geist-Medium',
  semibold: 'TEK-Geist-SemiBold',
  bold: 'TEK-Geist-Bold',
  italic: 'TEK-Geist-Italic',
} as const;
