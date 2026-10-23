/**
 * Taste Profile — every colour, size and position of the screen.
 *
 * Positions are "ref px": pixels of the pin rectified to 1206 × 2622 (an iPhone 17 Pro at 3×,
 * 402 pt wide), measured from `qa/ref-19.png`. The component multiplies them by `width / 1206`,
 * so on a 402 pt screen they land on the pin's pixels. `y` is from the top of the screen, status
 * bar included; the component moves it by `insets.top - REF_INSET` on other devices. Font sizes
 * and tracking are in pt for the 402 pt screen and scale the same way.
 */

export const REF_W = 1206;
/** Font scale reference: 1206 ref px = 402 pt. */
export const REF_PT = 402;
/** The pin was taken on a phone whose top safe area is 62 pt (186 ref px). */
export const REF_INSET = 186;

/** Anybody's ascent (hhea): the baseline of a one-line Text sits `ASCENT` em under its top. */
export const ASCENT = 0.795;

export const FONT = {
  body: 'AnybodyNC-Body', // wght 500, wdth 100
  semi: 'AnybodyNC-SemiBold', // wght 600, wdth 100
  bold: 'AnybodyNC-Bold', // wght 700, wdth 100
  extra: 'AnybodyNC-ExtraBold', // wght 800, wdth 100
} as const;

export const colors = {
  ink: '#000000',
  white: '#FFFFFF',
  hairline: '#D2D2D2',
  tileFill: '#F8F8F8',
  tileHairline: '#DEDEDE',
  chipFill: '#FEFEFE',
  chipText: '#8A8A89',
  pinIcon: '#525252',
  dotOn: '#1A1A18',
  dotOff: '#979895',
  /** Page background: a vertical grey. y in ref px → colour. */
  pageStops: [
    [0, '#F6F6F6'],
    [90, '#F6F6F6'],
    [180, '#F3F3F3'],
    [270, '#F1F1F1'],
    [360, '#EFEFEF'],
    [450, '#ECECEC'],
    [630, '#ECECEC'],
    [900, '#EBEBEB'],
    [1500, '#EBEBEB'],
    [1800, '#E9EBE9'],
    [2250, '#E9EAE8'],
    [2520, '#E8E8E8'],
    [2622, '#E8E8E8'],
  ] as readonly (readonly [number, string])[],
} as const;

/** Spacing tokens in ref px (3 ref px = 1 pt). Snapped to the 4 pt grid only where the pin is within 1 pt of it. */
export const space = {
  edge: 36.5, // screen edge → chips, photo strip (12.2 pt)
  tileEdge: 38.5, // screen edge → first tile
  tileGap: 19.5, // tile → tile (6.5 pt)
  navGap: 25, // nav button → nav button (8.3 pt)
  photoGap: 21, // gap between the two large photos (7 pt)
  chipGap: 24, // chip → chip (8 pt; measured 23.5 / 30 / 31)
} as const;

/**
 * The bar chart a held tile grows into (not in the pin): two tiles wide, built from the tile's own
 * tokens (inset = space.tileEdge, radius = radius.tile, hairline and fill of a tile). Ref px.
 */
export const chart = {
  w: 267.5 * 2 + 19.5, // two tiles and the gap between them
  h: 560,
  pad: 38.5, // = space.tileEdge
  barW: 54,
  barMax: 196,
  barsBottom: 560 - 84, // from the card's top
  monthBaseline: 560 - 40,
  captionBaseline: 228,
  valueGap: 14, // value label baseline above its bar
  barColor: '#DCDCDC',
  barOn: '#000000',
  caption: '#6A6A6A',
  veil: 'rgba(235,235,235,0.55)',
} as const;

export const radius = {
  nav: 28, // nav buttons (9.3 pt)
  tile: 39, // stat tiles (13 pt)
  photo: 54, // photo strip and the two large photos (18 pt)
  pill: 999, // chips and the portrait capsule
} as const;

/** Text style table: origin x (ref px, ink-left minus the glyph's bearing), baseline y, size/tracking in pt. */
export type TextSpec = {
  text: string;
  font: string;
  x: number;
  baseline: number;
  size: number;
  ls: number;
  color: string;
};

export const text = {
  name: { text: 'Sean Zhang', font: FONT.extra, x: 428.2, baseline: 431, size: 21.66, ls: 0.52, color: '#000000' },
  handle: { text: '@seanzhang', font: FONT.body, x: 429.4, baseline: 503, size: 15.24, ls: 0.65, color: '#505051' },
  following: { text: 'following', font: FONT.body, x: 497.4, baseline: 590, size: 14.35, ls: 0.39, color: '#262527' },
  followers: { text: 'followers', font: FONT.body, x: 825.4, baseline: 590, size: 14.35, ls: 0.07, color: '#262527' },
  mutual2: { text: '2', font: FONT.body, x: 550.7, baseline: 678, size: 12.3, ls: 0, color: '#221F22' },
  mutual: { text: 'mutual', font: FONT.body, x: 583.2, baseline: 678, size: 12.28, ls: 0.24, color: '#221F22' },
  friends: { text: 'friends', font: FONT.body, x: 725.4, baseline: 678, size: 12.24, ls: 0.0, color: '#221F22' },
  rankA: { text: 'new york', font: FONT.body, x: 565.5, baseline: 785, size: 9.74, ls: -0.04, color: '#474C4F' },
  rankB: { text: 'rank', font: FONT.body, x: 565.5, baseline: 822, size: 9.74, ls: -0.24, color: '#474C4F' },
  weekA: { text: 'week', font: FONT.body, x: 978.1, baseline: 785, size: 9.74, ls: -0.01, color: '#474C4F' },
  weekB: { text: 'streak', font: FONT.body, x: 977.8, baseline: 822, size: 9.74, ls: -0.32, color: '#474C4F' },
  loc: { text: 'New York', font: FONT.body, x: 109.2, baseline: 953, size: 13.97, ls: 0.18, color: '#4E4F4E' },
  bio: { text: 'Dabbling in the arts', font: FONT.body, x: 39.2, baseline: 1033, size: 14.39, ls: 0.16, color: '#262725' },
  chipA: { text: 'new york', font: FONT.extra, x: 328.1, baseline: 1206, size: 15.71, ls: 0.11, color: '#8A8A89' },
  chipB: { text: 'london', font: FONT.extra, x: 702.5, baseline: 1206, size: 15.66, ls: 0.2, color: '#8A8A89' },
  chipC: { text: 'paris', font: FONT.extra, x: 1006.1, baseline: 1206, size: 15.81, ls: -0.86, color: '#8A8A89' },
  lfav: { text: 'favorites', font: FONT.body, x: 93.5, baseline: 1428, size: 11.39, ls: -0.22, color: '#6A6A6A' },
  lrec: { text: 'recommend', font: FONT.body, x: 356.3, baseline: 1428, size: 11.43, ls: 0.09, color: '#6A6A6A' },
  lok: { text: 'okay', font: FONT.body, x: 706.9, baseline: 1428, size: 11.43, ls: 0.03, color: '#6A6A6A' },
  ldis: { text: 'disliked', font: FONT.body, x: 968.9, baseline: 1428, size: 11.43, ls: 0.19, color: '#6A6A6A' },
  viewAll: { text: 'view all', font: FONT.body, x: 756.2, baseline: 1983, size: 12.28, ls: 0.33, color: '#000000' },
  photos: { text: 'photos', font: FONT.body, x: 1013.2, baseline: 1983, size: 12.28, ls: 0.03, color: '#000000' },
  follow: { text: 'follow', font: FONT.semi, x: 760.4, baseline: 263, size: 12.24, ls: -0.35, color: '#000000' },
} as const satisfies Record<string, TextSpec>;

/** Boxes in ref px: x, y, w, h. */
export const box = {
  chevron: { x: 45, y: 220, w: 33, h: 58, stroke: 8 },
  follow: { x: 648.5, y: 200, w: 262, h: 97.7 },
  followIcon: { x: 685, y: 229, w: 49, h: 41 },
  send: { x: 935.5, y: 200, w: 98, h: 97.7 },
  sendIcon: { x: 965, y: 228, w: 42, h: 42 },
  share: { x: 1058.7, y: 200, w: 97.5, h: 97.7 },
  shareIcon: { x: 1086, y: 228, w: 43, h: 43 },
  /** Portrait capsule: outer box with its 6 px white border. */
  capsule: { x: 34.5, y: 365, w: 373, h: 500, border: 6 },
  /** Mutual-friends avatars: diameter incl. the 3 px white ring, centre. */
  avatars: [
    { d: 59, cx: 460, cy: 665 },
    { d: 59, cx: 509.5, cy: 665 },
  ],
  /** The rank badge and the streak badge (Blender renders, sprites in `sprite`). */
  rank: { x: 425, y: 736, w: 120, h: 122 },
  streak: { x: 800, y: 731, w: 160, h: 125 },
  pin: { x: 47, y: 914, w: 42, h: 51 },
  /** Chips: all (black), new york, london, paris. */
  chips: [
    { x: 36.5, w: 226 },
    { x: 286, w: 344 },
    { x: 660, w: 271 },
    { x: 962, w: 228 },
  ],
  chipY: 1136,
  chipH: 105,
  globe: { x: 88, y: 1165, w: 50, h: 50 },
  /** Stat tiles: x of each; the first partial tile ends at x = 19.5. */
  tiles: [38.5, 325, 612, 899.5],
  tileW: 267.5,
  tileY: 1296,
  tileH: 171,
  tileIcons: [
    { x: 101, y: 1334, w: 40, h: 40 },
    { x: 381, y: 1334, w: 40, h: 40 },
    { x: 676, y: 1334, w: 40, h: 40 },
    { x: 984, y: 1334, w: 39, h: 40 },
  ],
  /** Photo strip: one rounded box holding four photos side by side. */
  strip: { x: 36.5, y: 1516, w: 1131, h: 419.5 },
  dots: [
    { cx: 70, d: 19, color: colors.dotOn },
    { cx: 100.5, d: 19, color: colors.dotOff },
    { cx: 131.5, d: 19, color: '#959593' },
    { cx: 162.5, d: 11, color: '#9A9997' },
  ],
  dotsY: 1969.5,
  /** The two large photos; the next photo peeks in from the right edge. */
  photoL: { x: 35, y: 2063.5, w: 557.5, h: 557.5 },
  photoR: { x: 614, y: 2063.5, w: 556, h: 557.5 },
  peek: { x: 1193.5, y: 2063.5, w: 80, h: 557.5 },
} as const;

/** Anybody ExtraBold: [ink-left bearing, ink width] of the glyphs drawn one by one, in em. */
export const GLYPH_EM: Record<string, readonly [number, number]> = {
  '0': [0.028, 0.572],
  '2': [0.035, 0.582],
  '3': [0.032, 0.59],
  '4': [0.022, 0.598],
  '5': [0.028, 0.58],
  '6': [0.028, 0.585],
  '7': [0.012, 0.56],
  '8': [0.032, 0.575],
  '9': [0.035, 0.588],
  a: [0.028, 0.625],
  l: [0.052, 0.2],
};

/**
 * Text drawn glyph by glyph, because the pin's face has proportional numerals (a "1" half as wide as
 * a "2") and Anybody's are tabular. Each glyph is [char, ink-left, ink width] in ref px; Anybody's
 * glyph is squeezed horizontally to the measured width. "1" is drawn as a shape (no foot, as in the pin).
 */
export type GlyphRun = {
  font: string;
  size: number;
  baseline: number;
  /** Height of a digit in ref px (cap height), used for the drawn "1". */
  digitH: number;
  color: string;
  glyphs: readonly (readonly [string, number, number])[];
};

export const glyphRuns = {
  n39: { font: FONT.extra, size: 15.64, baseline: 590, digitH: 32, color: '#2E2D30', glyphs: [['3', 431, 25], ['9', 459, 25]] },
  n60: { font: FONT.extra, size: 15.64, baseline: 590, digitH: 32, color: '#2E2D30', glyphs: [['6', 760, 24], ['0', 787, 25]] },
  t125: { font: FONT.extra, size: 17.58, baseline: 1370, digitH: 36, color: '#000000', glyphs: [['1', 156, 17], ['2', 175, 31], ['5', 209, 33]] },
  t764: { font: FONT.extra, size: 17.65, baseline: 1370, digitH: 36, color: '#000000', glyphs: [['7', 435, 31], ['6', 467, 34], ['4', 503, 35]] },
  t183: { font: FONT.extra, size: 17.58, baseline: 1370, digitH: 36, color: '#000000', glyphs: [['1', 731, 17], ['8', 750, 33], ['3', 786, 33]] },
  t17: { font: FONT.extra, size: 17.71, baseline: 1370, digitH: 36, color: '#000000', glyphs: [['1', 1038, 17], ['7', 1056, 30]] },
  c1470: { font: FONT.extra, size: 13.7, baseline: 1983, digitH: 28, color: '#000000', glyphs: [['1', 909, 20], ['4', 932, 22], ['7', 958, 20], ['0', 982, 21]] },
  chipAll: { font: FONT.extra, size: 16.14, baseline: 1207, digitH: 38, color: '#FFFFFF', glyphs: [['a', 155, 28], ['l', 187, 9], ['l', 200, 8]] },
} as const satisfies Record<string, GlyphRun>;

/** Blender renders: canvases in ref px (the object sits 24 px inside), frames side by side in one sprite. */
export const sprite = {
  /** Rank coin: 0–18 = 0°…180° of the 3 | 7 flip, 19–27 = 100°…180° with the 1 on its back. */
  coin: { x: 401, y: 712, w: 168, h: 170, frames: 28 },
  /** Streak gem: 12 frames of one turn of its sheen. */
  gem: { x: 776, y: 707, w: 208, h: 173, frames: 12 },
  /** Contact shadow under the coin while it is in the air. */
  coinShadow: { cx: 485, cy: 866, w: 96, h: 16 },
} as const;

/** Ink widths (ref px) of the pin's tile digits at a 36 px digit height; "1" is the drawn flag-and-stem. */
export const DIGIT_W: Record<string, number> = { '0': 33, '1': 17, '2': 31, '3': 33, '4': 35, '5': 33, '6': 34, '7': 31, '8': 33, '9': 33 };
/** Ink gap between two digits (the pin's are 2–4 px). */
export const DIGIT_GAP = 3;

/** Glyphs of a number set like the pin's: ink-left from `x0`, widths scaled to the digit height. */
export function layoutNumber(value: string, x0: number, digitH: number) {
  const k = digitH / 36;
  const glyphs: [string, number, number][] = [];
  let x = x0;
  for (const ch of value) {
    const w = Math.round(DIGIT_W[ch] * k);
    glyphs.push([ch, x, w]);
    x += w + DIGIT_GAP;
  }
  return glyphs;
}

/** Ink box of each stat tile's number (first ink, baseline) and its clip window. */
export const statNumbers = [glyphRuns.t125, glyphRuns.t764, glyphRuns.t183, glyphRuns.t17] as const;

/** "ing" of "following": origin x (ref px) right after "follow" before the pill grows. */
export const followIng = { x: 871.1 } as const;
