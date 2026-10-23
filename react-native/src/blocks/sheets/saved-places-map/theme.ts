/**
 * Saved Places Map — every colour, size and position of the screen.
 *
 * Source numbers are ref px: the pin rectified to 1206 × 2622 (a 402 pt phone at 3×). `pt()` turns them into
 * points on that 402 pt screen; the component multiplies by `screenWidth / 402`, so on a 402 pt simulator
 * everything lands on the pin's pixels. Text is placed by baseline (`top = baseline − ascent · size`).
 */

export const REF_W = 402;
export const pt = (px: number) => px / 3;

export const FONT = {
  archivo: 'SavedPlacesArchivo',
  archivoMedium: 'SavedPlacesArchivoMedium',
  archivoSemi: 'SavedPlacesArchivoSemiBold',
  archivoBold: 'SavedPlacesArchivoExtraBold',
  barlow: 'SavedPlacesBarlow',
  barlowSemi: 'SavedPlacesBarlowSemiBold',
  barlowItalic: 'SavedPlacesBarlowItalic',
} as const;

/** hhea ascent / descent per em. */
export const METRICS: Record<string, { asc: number; desc: number }> = {
  [FONT.archivo]: { asc: 0.878, desc: 0.21 },
  [FONT.archivoMedium]: { asc: 0.878, desc: 0.21 },
  [FONT.archivoSemi]: { asc: 0.878, desc: 0.21 },
  [FONT.archivoBold]: { asc: 0.878, desc: 0.21 },
  [FONT.barlow]: { asc: 1, desc: 0.2 },
  [FONT.barlowSemi]: { asc: 1, desc: 0.2 },
  [FONT.barlowItalic]: { asc: 1, desc: 0.2 },
};

export const colors = {
  ink: '#000000',
  meta: '#6E7479',
  tileLabel: '#474C4F',
  slash: '#707479',
  tile0: '#EAF0F7',
  tile0Border: '#CDD4DF',
  tile: '#FDFFFF',
  tileShade: '#EDEDF5',
  card: '#FFFFFF',
  cardSub: '#8E8E93',
  chip: '#FDFCFE',
  chipShade: '#EBE8EC',
  grabber: '#C5C7C9',
  highlight: '#F7FFF7',
  city: '#101010',
  borough: '#474858',
  water: '#6EA1C1',
  faded: '#A5A7A8',
  markerInk: '#000000',
  markerSub: '#2B2B2B',
  bookmark: '#0E77EA',
  thumb: '#8FCAFC',
  star: '#FFBD20',
  locate: '#0263FF',
  shieldBlue: '#3C62AC',
  shieldRed: '#C8514F',
  badge: '#707274',
  white: '#FFFFFF',
  checkbox: '#8A8A8D',
  shadow: '#4B7391',
} as const;

/** Spacing and radius tokens, in pt (derived from the measurements; see spec.md §4). */
export const space = {
  margin: 16,
  cardMargin: 26 / 3,
  tileGap: 8,
  cardPad: 38 / 3,
} as const;

export const radius = {
  sheet: 28,
  card: 22,
  tile: 50 / 3,
  photoTop: 8,
  photoList: 12,
  badge: 14 / 3,
} as const;

export const sheet = { top: pt(1548), grabber: { x: pt(526), y: pt(1572), w: pt(153), h: pt(15) } };

export const copy = {
  title: 'NYC cafes for chatting',
  places: '59 places',
  saves: '11 saves',
  views: '252 views',
  handle: '@ines.vale',
  visitedNum: '0',
  visitedNext: '1',
  visitedDen: '/59',
  visited: 'you visited',
  save: 'save list',
  share: 'share list',
  similar: 'similar lists',
  listTitle: "Mabel's Corner Café",
  listSub: 'piano singalong',
  allPlaces: 'all places',
  eat: 'eat',
  cafe: 'café',
  bar: 'bar',
  /** Screen reader. */
  a11yBy: 'by',
  a11ySheetHint: 'Swipe up or down to expand or collapse the list.',
  a11yExpanded: 'expanded',
  a11yCollapsed: 'collapsed',
  a11yOf: 'of',
  a11yVisitHint: "Double-tap to mark Mabel's Corner Café visited.",
  a11yVisitedBox: 'Visited',
  a11yVisited: 'visited',
  a11yMarkerHint: 'Double-tap to highlight it on the map.',
} as const;

/** Text styles: size in px (ref), font, colour. */
export const type = {
  title: { size: 66.7, font: FONT.archivoBold, color: colors.ink },
  meta: { size: 42, font: FONT.archivo, color: colors.meta },
  handle: { size: 36.8, font: FONT.archivoMedium, color: colors.ink },
  visitedNum: { size: 57, font: FONT.archivoBold, color: colors.ink },
  visitedDen: { size: 57, font: FONT.archivoSemi, color: colors.slash },
  visited: { size: 39.5, font: FONT.archivo, color: colors.tileLabel },
  tile: { size: 40.5, font: FONT.archivo, color: colors.tileLabel },
  listTitle: { size: 54.1, font: FONT.archivoBold, color: colors.ink },
  listSub: { size: 45.3, font: FONT.archivo, color: colors.cardSub },
  chip: { size: 48.5, font: FONT.archivoSemi, color: colors.ink },
} as const;

export type TextStyleSpec = { size: number; font: string; color: string };

export const tiles = {
  y: 2068,
  h: 200,
  w: 259.5,
  xs: [48, 331.5, 615, 898.5],
  baseline: [2222, 2219, 2216, 2217],
  centre: [177.5, 461.5, 744, 1027.5],
};

export const chips = {
  y: 1440,
  h: 82,
  baseline: 1498,
  items: [
    { x: 41, w: 365, text: copy.allPlaces, textX: 120, icon: 'globe', iconX: 62 },
    { x: 446, w: 184, text: copy.eat, textX: 528, emoji: '🍽️', emojiX: 491 },
    { x: 666, w: 211, text: copy.cafe, textX: 749, emoji: '☕', emojiX: 712 },
    { x: 917, w: 188, text: copy.bar, textX: 999, emoji: '🍸', emojiX: 964 },
    { x: 1143, w: 120, text: '', textX: 0, emoji: '👜', emojiX: 1180 },
  ],
} as const;

/** Map labels. `base` is the baseline (ref px); `x` the left of the ink. */
export const mapLabels = {
  north: { text: 'North Bergen', x: 195, base: 262, size: 41, color: colors.faded, opacity: 0.8 },
  union: { text: 'Union City', x: 148, base: 428, size: 47.3, color: colors.city },
  jersey: { text: 'Jersey City', x: 18, base: 828, size: 45.7, color: colors.city },
  manhattan: { text: 'MANHATTAN', x: 336, base: 573, size: 31, color: colors.borough, ls: 5.2 },
  brooklyn: { text: 'BROOKLYN', x: 537, base: 1296, size: 31, color: colors.borough, ls: 5.2 },
  east: { text: 'EAST NEW', cx: 984.5, base: 1180, size: 31, color: colors.borough, ls: 5.2 },
  york: { text: 'YORK', cx: 984.5, base: 1218, size: 31, color: colors.borough, ls: 5.2 },
  upper: { text: 'Upper New', x: 0, base: 1148, size: 44, color: colors.water, ls: 6.2 },
  bay: { text: 'York Bay', x: 26, base: 1208, size: 44, color: colors.water, ls: 6.2 },
} as const;

export type Marker = {
  key: string;
  x: number; // left of the label ink
  base: number; // baseline of the name
  name: string;
  sub: string;
  tag: 'saved' | 'liked';
  emoji: string;
  emojiSize: number;
};

export const markers: Marker[] = [
  { key: 'lumen', x: 845, base: 444, name: 'Lumen Coffee', sub: 'coffee & pastries', tag: 'saved', emoji: '☕', emojiSize: 15.6 },
  { key: 'mabel', x: 419, base: 742.5, name: "Mabel's Corner Café", sub: 'piano singalong', tag: 'saved', emoji: '🎭', emojiSize: 17 },
  { key: 'linden', x: 500, base: 946, name: 'Linden Wick', sub: 'pourover coffee', tag: 'saved', emoji: '☕', emojiSize: 15.6 },
  { key: 'fern', x: 847, base: 926, name: 'Pale Fern', sub: 'nordic roastery', tag: 'liked', emoji: '☕', emojiSize: 15.6 },
  { key: 'aurelle', x: 564, base: 1141, name: 'Cafe Aurelle', sub: 'parisian cafe', tag: 'saved', emoji: '☕', emojiSize: 15.6 },
];

export const marker = {
  disc: 100,
  discDx: 63, // label x − disc centre x
  discDy: 12, // name baseline − disc centre y (measured by template match)
  iconDx: 36, // bookmark left = label x − 36
  bookmarkDy: 58, // name baseline − bookmark top
  savedDy: 39, // name baseline − saved baseline
  subDy: 31, // sub baseline − name baseline
  nameSize: 29.9,
  subSize: 25.8,
  /** Tap target to the right of the disc (label width), ref px. */
  hitW: 240,
};

export const back = { chevron: { x: 46, y: 221, w: 31, h: 56 }, badge: { x: 44, y: 283, w: 54, h: 55 } };
export const locate = { cx: 1094, cy: 1338, d: 152, arrow: { cx: 1092, cy: 1335, s: 50 } };
export const shield = { x: 918, y: 738, w: 74, h: 57, text: '495', size: 25 };
export const fadedChip = { x: 1105, y: 190, w: 67, h: 42 };

export const layout = {
  map: { w: 402, h: 569 },
  photo: { x: 48, y: 1646, w: 306, h: 390, r: 24 },
  titleX: 385,
  titleBase: 1760,
  meta: {
    base: 1840,
    pin: { x: 386, y: 1808, w: 31, h: 39 },
    places: 435,
    bookmark: { x: 650, y: 1808, w: 29, h: 39 },
    saves: 699,
    bars: { x: 895, y: 1808, w: 36, h: 39 },
    views: 946,
  },
  pill: { x: 386, y: 1893, w: 319, h: 91 },
  avatar: { x: 397, y: 1904, d: 71 },
  handleX: 485,
  handleBase: 1952,
  tile0: { numX: 101, denX: 144, numBase: 2155, labelBase: 2222 },
  tileIcon: [
    { x: 439, y: 2112, w: 45, h: 51 },
    { x: 720, y: 2112, w: 51, h: 51 },
    { x: 1002, y: 2113, w: 53, h: 52 },
  ],
  card: { x: 26, y: 2343, w: 1156, h: 500 },
  listPhoto: { x: 64, y: 2381, d: 252, r: 36 },
  listTitleX: 357,
  listTitleBase: 2500,
  listSubX: 358,
  listSubBase: 2563,
  checkbox: { x: 360, y: 2598, d: 29 },
  listAvatar: { x: 407, y: 2598, d: 48 },
};

/* ---- Tokens for the states the pin does not show (motion step). Same radius family and insets as the pin. ---- */

/**
 * The pin's progressive blur over the top of the map. Measured on the pin (ref px): Gaussian σ ≈ 11 px under
 * a white veil of α ≈ 0.55 at the top, σ ≈ 3 px / α ≈ 0.3 at 280 px, nothing below ≈ 420 px. Built from two
 * layers: a light-tinted BlurView at intensity 26 (its tint is part of the veil) masked opaque to `solid`
 * and gone at `h`, and a white fade of α 0.42 → 0.24 at 280 px → 0 at 420 px. The fade's alphas are below the
 * measured veil because the BlurView's light tint adds white of its own; the sum of the two has not been
 * re-measured against the pin. The status bar and back button sit above it, sharp.
 */
export const topBlur = {
  h: 330,
  /** BlurView intensity (0–100) and its mask: opaque to `solid`, gone at `h`. */
  intensity: 26,
  solid: 120,
  fade: {
    colors: ['rgba(255,255,255,0.42)', 'rgba(255,255,255,0.24)', 'rgba(255,255,255,0.1)', 'rgba(255,255,255,0)'],
    locations: [0, 280 / 420, 370 / 420, 1],
    h: 420,
  },
} as const;

/** The 3D state: sky behind the pitched map, haze at the horizon, pins' stalks, beams, shadows and ripples. */
export const depth = {
  sky: ['#E7EEF3', '#EEF2F5'] as const,
  fog: {
    colors: ['rgba(238,243,246,1)', 'rgba(238,243,246,0.85)', 'rgba(238,243,246,0)'],
    locations: [0, 0.55, 1],
    h: 560,
  },
  stalk: { w: 9, colors: ['#FFFFFF', '#D3DBE2'] as const },
  foot: { d: 16, color: '#3E5568' },
  beam: { saved: '14,119,234', liked: '96,170,240' },
  shadow: '#2E4153',
  ripple: { color: colors.bookmark, ring: 3 },
} as const;

/** Pins in depth order for the stand-up stagger (0 = farthest), by marker key. */
export const depthRank: Record<string, number> = { lumen: 0, mabel: 1, fern: 2, linden: 3, aurelle: 4 };

export const motionColors = {
  dim: '#0E2233',
  bubble: colors.bookmark,
  bubbleInk: '#FFFFFF',
  grabberTaken: '#9FA3A7',
  check: '#000000',
  checkInk: '#FFFFFF',
} as const;

/** Count bubble on a café disc's upper-left rim (ref px, centre offset from the disc centre). */
export const bubble = { dx: -36, dy: -36, h: 45, digit: 17, padX: 12, ring: 2, size: 30 };

/** Check badge on the visited café's disc (ref px, centre offset from the disc centre). */
export const badge = { dx: 32, dy: 32, d: 34, ring: 2 };

/** Checkbox tick, in the checkbox's own 29 px box. */
export const tick = { path: 'M7.5 15 L12.5 20 L21.5 9.5', length: 22, stroke: 3.4 };

/** The list under the first card (revealed at the half detent). Card height = photo + 2 × card padding. */
export const list = {
  cardH: 328,
  gap: 26,
  /** The sheet's bottom colour continues below the screen so the raised sheet has no edge. */
  sheetTail: '#DDEEF8',
  more: [
    { title: 'Lumen Coffee', sub: 'coffee & pastries', photo: 'cafe', zoom: 1.9, ox: -0.38, oy: -0.42 },
    { title: 'Linden Wick', sub: 'pourover coffee', photo: 'bar', zoom: 1.7, ox: -0.1, oy: -0.35 },
  ],
} as const;

/** The visited café (the list's first card). */
export const VISITED_KEY = 'mabel';

/** Digit roll window for "0/59" (ref px): clip box, and the centre the next digit is set on (tabular). */
export const roll = { x: 95, y: 2104, w: 52, h: 60, row: 60, centre: 125 };
