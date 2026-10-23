// Measurements taken from the pin (phone screen scaled to 402 pt wide, 874 pt tall).
// Top-anchored values are screen y in points; bottom-anchored ones are distances from the bottom edge.

export const SCREEN = { width: 402, height: 874 };

export const COLORS = {
  page: '#f2f2f6',
  tile: '#fbfbfd',
  ink: '#111114',
  ink2: '#5c5c62',
  mute: '#8a8a90',
  dim: 'rgba(0, 0, 0, 0.19)',
  sheetLight: '#f5f5f9',
  orange: '#fc4d00',
  caption: '#74747a',
  terms: '#6e6e73',
  skeletonBg: '#19191c',
  skeleton: '#28282e',
  white: '#ffffff',
  choose: '#ecebf0',
  chooseInk: '#1c1c1e',
  down: '#ff4f86',
  up: '#5ee06a',
  badge: '#ff3b30',
};

export const FONT = {
  // SF Pro on iOS, the platform sans elsewhere.
  skia: '.AppleSystemUIFont',
};

// Bottom sheet that the gift picker lives in.
export const SHEET = {
  top: 62,
  radius: 30,
  closeSize: 44,
  closeLeft: 15,
  closeTop: 16,
};

// The orange/white action button shared by every step.
export const BUTTON = { side: 40, height: 55, bottom: 55, radius: 20, font: 17 };

export const DISCOVER = {
  titleSize: 32,
  titleLine: 41,
  titleY: [405, 446], // line centres
  subSize: 17,
  subY: 488,
  termsBottom: 143, // centre, from bottom
  captionBottom: 64,
  orbCenter: { x: 201, y: 425 },
  sphere: { x: 201, y: 452, r: 151.5 },
  amountSize: 46,
  amountY: 431,
  nowY: [480, 502],
};

export const STOCK = {
  titleSize: 28,
  titleY: [169, 203],
  cardTop: 328,
  cardW: 196,
  cardH: 260,
  cardRadius: 18,
  // a neighbour, measured on the pin's skeleton frame: 229 pt tall (88 %), its near edge at
  // x 342 and leaning (13 pt further out at the bottom than at the top)
  spacing: 227, // centre to centre at rest
  sideSkew: -3.3, // degrees (skewX), mirrored on the left
  sideScale: 0.88,
  logo: 96,
  logoY: 93, // centre, from card top
  nameSize: 28,
  nameY: 178,
  pctSize: 15,
  pctY: 207,
  dotsY: 634,
  dot: 7,
  dotGap: 16,
  descSize: 16,
  descLine: 21,
  descBottom: 168, // first line centre, from bottom
};

// Grey placeholder shown while the dark sheet loads (positions from the pin's frame at 8.6 s).
export const SKELETON = {
  title: [
    { y: 157, w: 165 },
    { y: 191, w: 210 },
  ],
  titleH: 23,
  dots: { y: 631, w: 44, h: 7 },
  desc: [
    { y: 703, w: 235 },
    { y: 721, w: 195 },
  ],
  descH: 6,
};

export const PORTFOLIO = {
  back: { x: 37, y: 84, size: 44 },
  labelY: 146,
  totalY: 189,
  totalSize: 43,
  changeY: 235,
  tilesTop: 277,
  tileH: 87,
  tileGap: 20,
  side: 20,
  radius: 24,
  cashTop: 383,
  cashH: 150,
  giftTop: 557,
  giftH: 87,
  artW: 142,
  txTop: 665,
  txH: 85,
  promoTop: 771,
  edgeTop: 768,
  edgeBlur: 22,
  tabTop: 793,
  tabH: 58,
  tabW: 291,
  chat: 61,
};

// A finger drifting this far off a pressable does not cancel the press.
export const TOUCH = { retention: 16 };

