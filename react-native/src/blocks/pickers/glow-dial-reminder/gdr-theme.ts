// Glow Dial Reminder: tokens. Every position is a point on the 402 × 874 pt canvas measured on the flat board
// (creative spec §2, rows quoted in the comments) and scales with the screen; colours are sRGB hex.

export const C = {
  page: '#0A090A', // §2 page (the board reads #050405, lifted off pure black)
  backDisc: '#1B1B1B', // §2 back disc
  backArrow: '#E5E5E5',
  title: '#F2F0F2', // ink on page
  card: '#C8A1C0', // dominant lilac
  ink: '#FBF3FA', // ink on card
  inkSoft: '#F6E3F0', // "hours", "tue", "25"
  inkCold: '#56314F', // Contrast fix for small text on bare lilac.
  trackOn: '#865F7D', // Small lightness correction for the 13 pt On label.
  trackOff: '#A58FA1', // cold track
  toggleLabel: '#F3E6F0',
  thumb: '#FCF8FB',
  thumbShadow: '#5A2F4F',
  dot: '#E9C6DF', // numeral dots, α 0.92
  tick: '#F3C3DA', // ruler ticks, α 0.55
  tickLit: '#FBF3FA', // the quarter ticks passed since the last whole hour
  marker: '#F4E84A', // the only yellow
  markerCold: '#F4E84A', // Gate §11.5 wins over the grey Off marker in the choreography: one yellow anchor throughout.
  pageDotOn: '#FFFFFF',
  pageDotOff: '#F1DCEB',
  hourPie: '#FCF8FB',
  pieHand: '#9A8F99',
  minutePie: '#E0338A',
  knobTick: '#F7E9F3',
  halo: '#5A1D45',
  capLight: '#FBFAFB',
  capShade: '#E4DDE3',
  capPip: '#9A8F99',
  capShadow: '#4A1238',
  sheenLight: '#FFFFFF', // sweep sheen, fixed in screen space
  sheenShade: '#DCD3DA',
  slitOn: '#C12F6E', // the slit that shows the turn
  slitHold: '#FF6FB5', // ... while the knob holds the light
  ember0: '#E0338A', // underglow of the cap while it holds the light
  ember1: '#8A1E4F',
  rim: '#E0338A', // the pool lights the upper-left of the cap's rim
  tickHold: '#F2A6CC', // knob ticks that have taken the light
  bead: '#F2A6CC',
  stain: '#B88FB0', // where the pool was
  pieCold: '#B48AAB', // minute pie in Off
  cta: '#80617B', // Contrast correction from measured #8B6C86.
  ctaOn: '#B6236A', // Keep the magenta fill while preserving label contrast.
  ctaDisc: '#FCF8FB',
  ctaGlyph: '#3A2436',
} as const;

/** The glow ramp (§6), radius as a fraction of the pool's falloff ellipse. */
export const RAMP: readonly (readonly [number, string])[] = [
  [0.0, '#460C32'],
  [0.28, '#8A1E4F'],
  [0.45, '#C12F6E'],
  [0.62, '#D4589E'],
  [0.82, '#CC87B4'],
  [1.0, '#C8A1C0'],
];

export const G = {
  back: { cx: 56, cy: 99, r: 24, arrow: 14 },
  title: { x: 104, capTop: 88, size: 24 }, // Pro-bar primary type minimum
  card: { x: 4, y: 143, w: 394, r: 46 },
  reminder: { x: 54, capTop: 204, size: 24 },
  track: { x: 262, y: 195, w: 90, h: 42, r: 21 },
  thumb: { onX: 318, offX: 268, y: 201, d: 30, stretch: 36 },
  toggleLabel: { onX: 276, offRight: 338, size: 13 },
  glow: { cx: 201, cy: 400, rx: 170, ry: 236 }, // falloff ellipse 340 × 486 at 8 h (1 : 1.38)
  // 1.4x the replica's grid (8.6 / 6.6 / 50) so the number is the hero; its centre sits on the glow's (201, 400).
  numeral: { top: 359.3, pitch: 12.04, dot: 9.24, pairGap: 70 },
  hours: { cx: 201, xTop: 477, size: 15 },
  ruler: { x0: 77, x1: 328, bottom: 535, tall: 26, short: 16, pitch: 6, fade: 60, perHour: 72 },
  marker: { x: 201, y: 505, w: 2.5, h: 34 },
  pageDots: { x: 187, cy: 605.5, pitch: 9, on: 5, off: 3 },
  tue: { x: 53, xTop: 655, size: 15 },
  date: { x: 50, capTop: 690, size: 22 },
  hourPie: { cx: 135, cy: 665, r: 15 },
  minutePie: { cx: 135, cy: 703, r: 15 },
  time: { x: 166, hourCap: 657, minCap: 695, size: 16, win: 24 },
  knob: { cx: 306, cy: 680, tickR0: 50, tickR1: 58, tickLong: 63, ticks: 36, tickW: 2, halo: 35, haloOff: 48, cap: 32, hit: 66 },
  slit: { r0: 17, r1: 28, w: 3 },
  cta: { x: 108, y: 787, w: 200, h: 48, r: 24, labelX: 140, size: 14 },
  plus: { cx: 286, cy: 811, r: 17, arm: 6 },
  // the vein: the knob pumps the pool's light along this quadratic, and the drain and the bloom run through it
  vein: { x0: 306, y0: 680, cx: 322, cy: 548, x1: 201, y1: 400, core: 9, soft: 22, plug: 16, beadSigma: 9, beadSpeed: 1600 },
  ctaPath: { c1x: 150, c1y: 640 },
} as const;

/** Urbanist (2000 upm): ascent 0.95 em, cap 0.70 em, x-height 0.50 em; RN draws the line box 1.2 em tall. */
export const METRIC = { ascent: 0.95, cap: 0.7, x: 0.5, line: 1.2 } as const;
/** Doto (1000 upm): ascent 0.95 em, cap 0.70 em. */
export const DOTO = { ascent: 0.95, cap: 0.7 } as const;

export const FONT = {
  regular: 'GDR-Urbanist-Regular',
  medium: 'GDR-Urbanist-Medium',
  doto: 'GDR-Doto-Bold',
} as const;

/** Design-system springs (damping, stiffness, mass 1). */
export const SPRING = {
  detent: { d: 40.4, k: 503.5 },
  snap: { d: 30.5, k: 322.3 },
  glide: { d: 22.1, k: 157.9 },
  pop: { d: 19.0, k: 195.0 },
  drift: { d: 14.0, k: 48.7 },
} as const;

/** Named design-system easings (cubic-bezier control points). The first turn uses a Hermite spline through the
 * spec's exact reveal beats; an unmodified `emph` would rush all three reveals into its first 170 ms. */
export const EASE = {
  out: [0.2, 0.8, 0.2, 1],
  in: [0.32, 0, 0.67, 0],
  inOut: [0.65, 0, 0.35, 1],
  emph: [0.05, 0.7, 0.1, 1],
} as const;

export const COPY = {
  title: 'Complete setup',
  reminder: 'Reminder',
  on: 'On',
  off: 'Off',
  unit: (v: number) => (v === 1 ? 'hour' : 'hours'),
  day: 'tue',
  date: '25',
  hour: '10',
  minute: '00',
  cta: 'Start tracking',
  ctaDone: 'Tracking',
} as const;

export const RANGE = { min: 4, max: 12, step: 0.25, rest: 8 } as const;

/**
 * The hero numeral is not a font: a rounded seven-segment display on a 5 × 7 dot grid (pitch 12.04, dot Ø 9.24, 1.4x the
 * replica). The horizontal segments hold four dots on the half columns, the vertical ones two dots on the outer columns,
 * so the pin's 8 lights 20 dots and has no corner dots. The 1 is not two segments (that read as a colon): it is a full
 * column of seven dots on the centre column, with a flag dot up-left and a three-dot foot.
 */
export const SEGMENTS: Record<string, readonly (readonly [number, number])[]> = {
  a: [[0.5, 0], [1.5, 0], [2.5, 0], [3.5, 0]],
  b: [[4, 1], [4, 2]],
  c: [[4, 4], [4, 5]],
  d: [[0.5, 6], [1.5, 6], [2.5, 6], [3.5, 6]],
  e: [[0, 4], [0, 5]],
  f: [[0, 1], [0, 2]],
  g: [[0.5, 3], [1.5, 3], [2.5, 3], [3.5, 3]],
  // the join a 7 needs so its right column does not read as a colon
  h: [[4, 3]],
  // the 1: column, flag, foot
  o: [[2, 0], [2, 1], [2, 2], [2, 3], [2, 4], [2, 5], [2, 6], [1, 1], [1, 6], [3, 6]],
};

export const DIGITS = ['abcdef', 'o', 'abged', 'abgcd', 'fgbc', 'afgcd', 'afgedc', 'abch', 'abcdefg', 'abcdfg'] as const;
