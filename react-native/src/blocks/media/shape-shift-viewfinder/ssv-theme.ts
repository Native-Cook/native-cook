// Shape Shift Viewfinder: tokens. Positions are points on the 402 × 874 pt canvas (iPhone 17 Pro) and scale with
// the screen width (S = width / 402). Everything drawn on the stage uses stage coordinates: the stage's top-left
// corner is (0, 0) and it is 390 wide.

export const C = {
  barTop: '#000000',
  barBottom: '#060606',
  disc: '#1A1A1C',
  shutterRing: '#1C1C1E',
  ink: '#FAFAF8',
  inkDark: '#141416',
  teal: '#118169',
  forest: '#2E6E54',
  olive: '#4A6B38',
  sage: '#7FB174',
  sand: '#A9A77E',
  clay: '#B9836B',
  pink: '#F02FA6',
  segPill: '#2B2B2E',
} as const;

/** The swatches of the Dynamic row: tones read from the picture (the first one is "auto", the live colour). */
export const DYNAMIC_SWATCHES = [C.teal, C.forest, '#4E4A5A', '#555274', '#5B4A55', '#3F5A4E', '#A9C9B4', C.olive] as const;

/** Geometry (pt). Stage coordinates unless noted. */
export const G = {
  stageX: 6, // screen
  stageW: 390,
  stageR: 44,
  stageTop: 65, // screen, on the 874-tall canvas: safe-area top + 3
  stageGapBelow: 60, // stage bottom → controls' centre
  ctrlFromBottom: 80, // controls' centre above the safe-area bottom
  R: 150, // shape radius at k = 1
  N: 192, // outline samples
  restX: 195,
  hintAbove: 175, // hint centre above the rest centre
  chipFromBottom: 43, // zoom chip centre above the stage bottom
  chipW: 66,
  chip: 38,
  btn: 44,
  dot: 26,
  shutter: 80,
  shutterRing: 6,
  shutterDisc: 64,
  ctrlX: [80, 201, 322] as const, // screen
  sheet: { x: 14, w: 374, h: 210, r: 28, pad: 19, title: 35, seg: 72, segH: 36, row: 128, swW: 42, swH: 52, swR: 14, swGap: 8, bottomGap: 5 },
  drag: { a: [85, -82] as const, b: [-71, 88] as const },
} as const;

/** Design-system springs (damping, stiffness, mass 1). */
export const SPRING = {
  glide: { d: 22.1, k: 157.9 },
  pop: { d: 19, k: 195 },
  snap: { d: 30.5, k: 322.3 },
  weighty: { d: 18, k: 120 },
} as const;

/** The jelly: ζ 0.35, response 0.30 s. */
const JW = (2 * Math.PI) / 0.3;
export const JELLY = { d: 2 * 0.35 * JW, k: JW * JW, perSpeed: 1 / 5600, max: 0.16, leanPerSpeed: 0.012, leanMax: 9 } as const;

/** The six morph bands: response 0.42 / (1 + 0.35 b) s, ζ 0.50. */
export const BAND_SPRINGS = [0, 1, 2, 3, 4, 5].map((b) => {
  const w = (2 * Math.PI) / (0.42 / (1 + 0.35 * b));
  return { d: 2 * 0.5 * w, k: w * w };
});

/** Pinch limits: the cut-out never drops below 0.45× (a recognisable hero) or grows past the stage's width (1.3 × R 150). */
export const LIMITS = { kMin: 0.45, kMax: 1.3 } as const;

/** The stage's wall for the squash: the stage's rounded rect inset by 4 pt. */
export const WALL = { inset: 4 } as const;

export const FONT = { semibold: 'SSV-Inter-SemiBold', medium: 'SSV-Inter-Medium' } as const;

export const SHAPE_NAMES = ['clover', 'heart', 'flower', 'cookie', 'squircle', 'circle'] as const;
export type ShapeName = (typeof SHAPE_NAMES)[number];
