// Tumble Card Routine: tokens. Every value is a point on the 402 × 874 pt canvas (iPhone 17 Pro) measured on the
// reference (spec §2, ±2 pt), scaled at runtime by S = width / 402. Top-anchored y values move with the safe area's
// top inset, bottom-anchored ones with the bottom inset (`yt` / `yb` in the component).

export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;

export const C = {
  under: '#0F0F11',
  stage: '#FBFBFB',
  ink: '#111113',
  ink2: '#8E8E93',
  ink3: '#C0C2C2',
  field: '#262628',
  fieldInk: '#9A9AA0',
  chipInk: '#B0B0B5',
  statFill: '#F1F1F1',
  plusFill: '#0F0F11',
  avatarRing: '#E6E6E6',
  // food
  foodFrame: '#F56419',
  foodFrameLit: '#FF8C52',
  foodInk: '#3A1606',
  paper: '#FBFBFB',
  paperInk: '#211D12',
  // weight
  glassTop: '#16191D',
  glassBottom: '#111316',
  barOff: '#2A2D31',
  barOn: '#3FD05A',
  chipFill: '#6FCC82',
  chipInkDark: '#0D2B14',
  goalInk: '#7C8088',
  white: '#FFFFFF',
} as const;

/** Design-system springs (damping, stiffness, mass 1). */
export const SPRING = {
  weighty: { d: 15.1, k: 93.4 },
  glide: { d: 22.1, k: 157.9 },
  pop: { d: 19, k: 195 },
  snap: { d: 30.5, k: 322.3 },
  detent: { d: 26, k: 260 },
} as const;

/** Screen chrome (pt). */
export const G = {
  dateY: 97,
  titleY: 133,
  titleGap: 52, // between day titles in the scrolling row (Friday's left edge lands at ~338)
  panelBottom: 719,
  panelR: 32,
  rowY: 667,
  stat: { x: 41, d: 40 },
  plus: { x: 201, d: 64 },
  me: { x: 364, d: 40 },
  ask: { x: 19, y: 736, w: 364, h: 52, r: 14 },
  chips: { x: 19, y: 800, h: 34, r: 10, gap: 8, pad: 12 },
  // the waiting slot behind the "+" button
  slotTop: 520,
  slotScale: 0.86,
  slotTilt: 0.6,
  slotHidden: 182, // below the slot: the next card waits here, under the panel's bottom edge
  fogTop: 600,
  fogA: 0.9,
  sliverTop: 515,
  // one hand-off per 240 pt of finger travel
  pull: 240,
  // every card is drawn 10 % larger than its measured size, about its centre
  cardScale: 1.1,
} as const;

export type LineSpec = { v: string; u: string };
export type CardSpec = {
  id: 'food' | 'water' | 'weight' | 'act';
  name: string;
  w: number;
  h: number;
  r: number;
  cx: number;
  cy: number;
  tilt: number; // degrees at rest, clockwise positive
  bump: number; // landing overshoot of the tilt (deg)
  shadow: string; // tint of its ambient shadow (the stage takes the card's colour)
  shadowA: number;
  lines: LineSpec[];
  numY: number; // cap centre of the first number line (card-local)
  numLH: number;
  numSize: number;
  numColor: string;
  dots: { n: number; on: number; color: string; off: number } | null;
  photo: boolean;
};

export const CARDS: CardSpec[] = [
  {
    id: 'food',
    name: 'Food',
    w: 250,
    h: 286,
    r: 14,
    cx: 163,
    cy: 342,
    tilt: -5,
    bump: 1.8,
    shadow: '#C2410C',
    shadowA: 0.22,
    lines: [{ v: '1640', u: 'kcal' }],
    numY: 150,
    numLH: 48,
    numSize: 40,
    numColor: C.paperInk,
    dots: { n: 5, on: 3, color: C.foodInk, off: 0.3 },
    photo: false,
  },
  {
    id: 'water',
    name: 'Water',
    w: 228,
    h: 290,
    r: 20,
    cx: 255,
    cy: 367,
    tilt: 11,
    bump: 2.2,
    shadow: '#12302D',
    shadowA: 0.26,
    lines: [{ v: '450', u: 'ml' }],
    numY: 151,
    numLH: 48,
    numSize: 40,
    numColor: C.white,
    dots: { n: 5, on: 2, color: C.white, off: 0.35 },
    photo: true,
  },
  {
    id: 'weight',
    name: 'Weight',
    w: 222,
    h: 270,
    r: 20,
    cx: 205,
    cy: 362,
    tilt: -6,
    bump: 1.8,
    shadow: '#0B0C0E',
    shadowA: 0.3,
    lines: [{ v: '64', u: 'kg' }],
    numY: 98,
    numLH: 48,
    numSize: 40,
    numColor: C.white,
    dots: null,
    photo: false,
  },
  {
    id: 'act',
    name: 'Activity',
    w: 300,
    h: 300,
    r: 16,
    cx: 201,
    cy: 359,
    tilt: 0,
    bump: 2,
    shadow: '#2A251B',
    shadowA: 0.24,
    lines: [
      { v: '310', u: 'kcal' },
      { v: '5.2', u: 'km' },
    ],
    numY: 144,
    numLH: 50,
    numSize: 40,
    numColor: C.white,
    dots: { n: 4, on: 4, color: C.white, off: 0.35 },
    photo: true,
  },
];

/** The weight card's bars (card-local): four of them, the last one green. */
export const BARS = { w: 40, h: 56, gap: 6, top: 140, r: 9 } as const;

export const COPY = {
  date: 'Nov 12, 2026',
  days: ["Today's routine", 'Friday', 'Saturday', 'Sunday'],
  ask: 'Ask me…',
  chips: ['Plan a high-protein day', 'Easy ways to drink more', 'Recover after a long ride'],
  foodLeft: 'left 960',
  foodPct: '63% of ',
  foodGoal: 'daily goal',
  foodTotal: '2600 kcal',
  waterOf: 'water drunk of ',
  waterGoal: '2000 ml',
  waterDaily: 'your daily goal',
  weightChip: '− 1.8 (2.7%)',
  weightGoal: 'Your goal',
  weightLose: 'Lose weight',
  actBurned: 'You burned',
} as const;

export const FONT = {
  regular: 'TCR-Geist-Regular',
  semibold: 'TCR-Geist-SemiBold',
} as const;
