// First Loot Reward — palette and geometry, measured from the reference recording
// (a 393 × 852 pt screen, scaled to the phone's width).

export const C = {
  page: '#090909',
  ray: '#130f1c',
  aura: '#2c1a52',
  // badge
  rimHi: '#e3c9ff',
  outerTop: '#c697f8',
  outerMid: '#ad80ea',
  outerBot: '#7f5aca',
  faceTop: '#b386ef',
  faceBot: '#8a62d2',
  innerCenter: '#6f4cc4',
  innerEdge: '#3f2a88',
  innerShadow: '#24125c',
  innerLight: '#a383ec',
  glow: '#8e5cf0',
  bloom: '#c39bff',
  // chest
  chestLine: '#4e2aa0',
  lidTop: '#eadfff',
  lidBot: '#c7aefb',
  bodyTop: '#c8b0fc',
  bodyBot: '#a587ee',
  gap: '#7b4fd8',
  band: '#ddcdff',
  lock: '#f3ecff',
  lockStar: '#7b4fe0',
  // sparkles
  starPurple: '#6a38c8',
  starLight: '#cdb9f7',
  dot: '#7a64a0',
  // type and controls
  title: '#ffffff',
  sub: '#9d9d9d',
  cardL: '#4d2586',
  cardR: '#8459c3',
  chip: 'rgba(255,255,255,0.13)',
  cardBody: 'rgba(240,232,255,0.84)',
  swirl: 'rgba(214,190,255,0.16)',
  btnDark: '#363638',
  btnLight: '#fdfdfd',
} as const;

/** Reference width the measurements were taken at. */
export const DESIGN_W = 393;

export const BADGE = {
  outerW: 155, // flat-to-flat (the hexagon stands on a point)
  innerW: 102,
  outerCorner: 15,
  innerCorner: 11,
  seed: 33, // radius of the ring the reveal starts with
  fromTop: 182, // safe-area top → badge centre
} as const;

/** Sparkles around the badge, relative to its centre (pt): x, y, size, kind (0 dot, 1 star). */
export const SPARKLES: readonly (readonly [number, number, number, number, number])[] = [
  // x, y, size, kind, tone (0 purple, 1 light)
  [3, -106, 9, 1, 0],
  [111, -12, 9, 1, 1],
  [-126, 20, 4.5, 1, 1],
  [75, 70, 4.5, 1, 0],
  [-75, -76, 3, 0, 0],
  [93, -60, 3, 0, 0],
  [-71, 83, 3, 0, 1],
  [-111, -87, 1.5, 0, 1],
  [50, -100, 1.5, 0, 1],
  [105, -49, 1.5, 0, 1],
  [119, 10, 1.5, 0, 1],
  [-89, 58, 1.5, 0, 1],
  [90, 44, 1.5, 0, 1],
  [-115, -2, 1, 0, 1],
  [-45, -102, 1, 0, 1],
];

export const COPY = {
  titleWords: ['You', 'just', 'earned', 'your', 'first', 'Loot.'],
  sub: 'Nice start! Keep playing, keep winning, and watch your rewards stack up.',
  chip: 'Announcement',
  heading: 'Something big is coming 🎉',
  body: 'Ember Rift drops October 24. Get ready to play, compete, and earn exclusive Loot.',
  keep: 'Keep playing',
  view: 'View loot',
} as const;
