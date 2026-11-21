// Wave Field Pad: tokens. Positions are points on the 402 × 874 pt canvas (iPhone 17 Pro) and scale with the
// screen width. The pad is larger than the reference's (298 × 292) so the hero covers 36 % of the poster (Pro bar).

export const C = {
  page: '#1A1F1B',
  pageLow: '#151216',
  pageDeep: '#131115',
  lift: '#22302A',
  amb: '#271C0E',
  pad: '#232227',
  padMid: '#222025',
  ember: '#F79A2E',
  emberCore: '#F4D28C',
  emberRim: '#EE7A1A',
  emberDeep: '#C85A14',
  emberLight: '#FFB25A',
  rim: '#FFC07A',
  rimTop: '#FFE2B0',
  halo: '#F0801E',
  plus: '#FFF3DC',
  sand: '#F6E2BE',
  ink: '#F2F4F1',
  ink2: '#E3E7E2',
  mute: '#7D857D',
  teal: '#3FD6A8',
  tabOn: '#F6F6F4',
  tabOnInk: '#17161A',
  tabOff: '#1F1C23',
  tabOffInk: '#E6E6E4',
} as const;

/** Layout (pt on the 402-wide canvas), per the Pro addendum: pad 354 × 354 (35.7 % of the screen), puck Ø 88. */
export const G = {
  close: { x: 360, y: 98, hit: 44 },
  wave: { x0: 30, x1: 372, y: 180, points: 96 },
  hz: { y: 170, size: 30, line: 36 },
  band: { y: 204, size: 15, line: 20 },
  title: { y: 268, size: 22, line: 28 },
  sub: { y: 298, size: 13, line: 18 },
  pad: { x: 24, y: 356, w: 354, h: 354, r: 28 },
  /** the shader's rect is larger than the pad: room for its shadow and the wall's bulge */
  padBleed: 28,
  grid: 14,
  puckR: 44,
  smallArc: 56,
  label: { size: 13, inset: 22 },
  tabs: { y: 736, h: 44, gap: 10 },
} as const;

/** Design-system springs (damping, stiffness, mass 1). */
export const SPRING = {
  detent: { d: 40.4, k: 503.5 },
  snap: { d: 30.5, k: 322.3 },
  glide: { d: 22.1, k: 157.9 },
  pop: { d: 19, k: 195 },
  weighty: { d: 15.1, k: 93.4 },
  drift: { d: 14, k: 48.7 },
  /** the sheet following the puck: ~100 ms behind (d / k), trails ~20 pt at speed and overshoots on a stop (addendum a5) */
  sheet: { d: 11, k: 110 },
} as const;

export const FONT = { regular: 'WFP-Outfit-Regular', medium: 'WFP-Outfit-Medium', tabular: 'WFP-OutfitTabular-Medium' } as const;
