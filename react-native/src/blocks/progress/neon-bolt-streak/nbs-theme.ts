// Neon Bolt Streak: tokens. Positions are points on the 402 × 874 pt canvas (iPhone 17 Pro); the scene scales with
// the screen width and is centred vertically.

export const C = {
  page: '#26272B',
  panel: '#17181B',
  border: '#3B3C41',
  ink: '#FFFFFF',
  word: '#8C8D92',
  wordLit: '#C9CACF',
  dayDead: '#6D6E73',
  dayLit: '#F2CF2A',
  dayAfter: '#9A6A2A',
  core: '#FFF8C9',
  tube: '#FFD91A',
  bloom: '#F5C400',
  amber: '#7A4A12',
  heroTop: '#F5D21F',
  heroMid: '#C9A516',
  zincShade: '#55544F',
  zincBase: '#8B8A84',
  zincEdge: '#C9C8C0',
} as const;

export const FONT = {
  count: 'NBS-Inter-ExtraBold',
  word: 'NBS-Inter-Bold',
  day: 'NBS-Inter-Medium',
} as const;

/** Scene geometry (pt). */
export const G = {
  heroX: 106,
  heroY: 150,
  heroW: 190,
  heroH: 292,
  countSize: 128,
  countBase: 440, // baseline of the count
  countX: 207, // digit centre: 6 pt right of the bolt's axis
  countStep: 128, // drum spacing between two digits
  wordSize: 28,
  wordBase: 495,
  panelX: 16,
  panelY: 560,
  panelW: 370,
  panelH: 168,
  panelR: 20,
  cell: 370 / 7,
  boltCY: 629, // day bolt centre y (40 × 60 silhouette)
  labelSize: 13,
  labelBase: 689,
  ghostX: [24, 92, 310, 378],
  ghostY: 296,
  ghostScale: 0.62,
  // the week shader's rect: the panel with 48 pt round it for the bloom
  weekX: -32,
  weekY: 512,
  weekW: 466,
  weekH: 264,
  // the hero shader's rect: hero, ghosts and the hero's bloom
  heroRX: -70,
  heroRY: 86,
  heroRW: 542,
  heroRH: 420,
  // close-up camera: 3.5 × (addendum 3.2 ×, raised so today's bolt is ≥ 128 × 192 pt after the tilt) about today's bolt, the bolt brought to (focusSX, focusSY) on the 402-wide screen
  zoom: 3.5,
  rotX: 14,
  rotZ: -3,
  persp: 900,
  focusDX: -20, // focus point left of today's centre, so the lit neighbour stays in the frame
  focusSX: 201,
  focusSY: 470,
} as const;

export const cellX = (i: number) => {
  'worklet';
  return 16 + (370 / 7) * (i + 0.5);
};

/** The day bolt's tube, opened at the lower tip (bolt-day.svg #tube), in its 40 × 60 box. */
export const TUBE_D =
  'M14.114 58.001 A3.700 3.700 0 0 0 16.660 56.833 L37.136 34.956 A3.200 3.200 0 0 0 35.856 29.748 L27.364 26.779 A2.000 2.000 0 0 1 26.060 24.514 L29.607 6.040 A3.400 3.400 0 0 0 23.746 3.118 L2.827 26.245 A3.200 3.200 0 0 0 4.180 31.425 L13.024 34.398 A2.200 2.200 0 0 1 14.458 37.016 L10.368 53.409 A3.700 3.700 0 0 0 10.769 56.181';
/** The top tip of the day bolt's tube (where a strike lands) and the strike bolt's own lower tip, in the 40 × 60 box. */
export const TIP_TOP = { x: 27.0, y: 1.6 };
export const TIP_LOW = { x: 12.4, y: 58.6 };
export const STRIKE_SCALE = 0.35;

export const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
export const TODAY = 3;
export const REST_DONE = 3;

/** Closed-form spring presets (mass 1). */
export const SPRING = {
  glide: { d: 26, k: 170 },
} as const;
