// Gear Puck Speed Line: tokens. Positions are points on the 402 × 874 pt canvas and scale with the screen width.

export const C = {
  page: '#070A10',
  pageLift: '#0C1119',
  ink: '#F4F7FF',
  ink2: '#8B909A',
  letter: '#A7ABB3',
  capsule: '#9AA0AA',
  glassLetter: '#FFE9EC',
} as const;

/** Speed bands (mph), drawn as fronts that spread from the crest. */
export const BANDS = [
  { upTo: 25, color: '#93E63F' },
  { upTo: 120, color: '#2F9BFF' },
  { upTo: 165, color: '#FFA51F' },
  { upTo: 1000, color: '#FF2A3C' },
] as const;

export const hex3 = (h: string): [number, number, number] => [
  parseInt(h.slice(1, 3), 16) / 255,
  parseInt(h.slice(3, 5), 16) / 255,
  parseInt(h.slice(5, 7), 16) / 255,
];
export const BAND_RGB = BANDS.map((b) => hex3(b.color));

/** Layout (pt). */
export const G = {
  rest: 330, // the line at rest
  core: 3.5,
  hump: { a0: 96, aPerMph: 0.12, sigma: 68, lean: 0.3, x: 56, span: 270, maxSpeed: 200 },
  curtain: { pitch: 6, width: 2, base: 40, sigma: 110 },
  numeral: { size: 76, cell: 0.62 * 76, digitH: 0.703 * 76, humpBase: 326, unitGap: 18 },
  parked: { x: 22, base: 368, scale: 34 / 76 },
  dropped: { base: 372, scale: 0.6 },
  unit: { size: 13, tracking: 0.08 },
  capsule: { x: 24, y: 628, w: 354, h: 72 },
  gears: ['P', 'R', 'D', 'S', 'M'] as const,
  pitch: 66,
  firstX: 69,
  ballY: 664,
  ballR: 44,
  ringR: 52,
  letterSize: 20,
} as const;

export const gearX = (g: number) => {
  'worklet';
  return G.firstX + G.pitch * g;
};

/** Gear speed caps (mph): P R D S M. */
export const CAPS = [0, 0, 132, 197, 197] as const;

/** Design-system springs (damping, stiffness, mass 1). */
export const SPRING = {
  glide: { d: 22.1, k: 157.9 },
  pop: { d: 19, k: 195 },
  snap: { d: 30.5, k: 322.3 },
  detent: { d: 24, k: 420 },
} as const;
