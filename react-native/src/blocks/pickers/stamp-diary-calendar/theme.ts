/**
 * Stamp Diary Calendar — every colour, size and position of the screen.
 *
 * Positions are in reference points: the pin's screen at 402 × 874 pt (iPhone 17 Pro, 1206 × 2622 px
 * at 3×), whose top safe area is 62 pt. The component maps them with `x * s` and
 * `safeTop + (y - 62) * s` where `s = screenWidth / 402`, so on a 402 pt phone everything lands on
 * the pin's pixels. Measurements come from `qa/ref-04.png` (round 2; token tables in the block's research design.md).
 */

export const REF = { width: 402, height: 874, safeTop: 62 } as const;

export const colors = {
  /** Hero backdrop: near black, a touch lighter towards the calendar (#010101 → #060606). */
  heroTop: '#010101',
  heroBottom: '#060606',
  /** Graph-paper lines over the hero, every 28 pt. */
  heroLine: 'rgba(255,255,255,0.05)',
  /** Weekday band, dated cells and the page under the grid. */
  page: '#0B0B0B',
  /** Leading and trailing cells without a date are lighter tiles. */
  blankCell: '#111111',
  gridLine: '#222222',
  weekday: '#737373',
  day: '#FFFFFF',
  year: '#9A9A9A',
  month: '#FFFFFF',
  /** Header circles are smoked glass: a blur (`glass`) under this tint, dark over the backdrop, a soft stamp under it. */
  headerFill: 'rgba(36,36,36,0.55)',
  headerIcon: '#FFFFFF',
  /** Selection ring of the day whose stamp is out. */
  selection: 'rgba(255,255,255,0.72)',
} as const;

/** The pin's spacing, on a 4 pt base where it lands within 1 pt. */
export const space = {
  /** Screen edge to the right edge of "2026" / "May" (pin: 18.5). */
  titleInset: 18.5,
  /** Screen edge to the centre of a header circle (pin: 36). */
  headerCenterInset: 36,
  /** Header circle centre line (pin: y 96). */
  headerCenterY: 96,
  /** Hero graph paper pitch and its phase (pin lines at x 26 + 28k, y 1.3 + 28k). */
  paperPitch: 28,
  paperPhaseX: 26,
  paperPhaseY: 1.3,
  /** Selection ring inset inside its cell. */
  selectionInset: 4,
} as const;

export const radii = {
  header: 22,
  selection: 8,
} as const;

export const sizes = {
  header: 44,
  /** "×": a 14 pt cross with a 2 pt round stroke. */
  closeGlyph: 14,
  closeStroke: 2,
  /** "•••": three 3.6 pt dots, 7.6 pt apart. */
  dot: 3.6,
  dotGap: 7.6,
  gridLine: 1,
  selectionLine: 1,
} as const;

/** Smoked glass (expo-blur) under the header circles and the month menu. */
export const glass = { intensity: 24, tint: 'dark' as const } as const;

/** The month menu (opened from ••• or the title): a smoked-glass card under the ••• circle. */
export const menu = {
  width: 196,
  row: 44,
  radius: 16,
  /** Below the ••• circle's bottom edge. */
  gap: 8,
  padX: 16,
  textSize: 15,
  fill: 'rgba(30,30,30,0.6)',
  divider: 'rgba(255,255,255,0.1)',
  text: '#FFFFFF',
  /** Opens with ease-out from this scale (anchored top-right), closes faster. */
  scaleFrom: 0.96,
  inMs: 160,
  outMs: 120,
} as const;

/** Calendar grid (pin: weekday band 452.5–488.5, six rows 55.33 pt, seven equal columns). */
export const grid = {
  weekdayTop: 452.5,
  top: 488.5,
  rowHeight: 55.33,
  rows: 6,
  columnWidth: 402 / 7,
} as const;

/** The hero (collage) ends where the weekday band starts. */
export const HERO_BOTTOM = grid.weekdayTop;

export const type = {
  weekday: { size: 9.5, weight: '600' as const },
  day: { size: 11, weight: '600' as const },
  /** Baselines measured on the pin. */
  year: { size: 17, weight: '400' as const, baseline: 398.3 },
  month: { size: 30, weight: '700' as const, baseline: 432.3 },
} as const;

/** SF Pro metrics used to place a Text by its baseline. */
export const SF = { ascent: 0.952, lineHeight: 1.2 } as const;

/**
 * A stamp in the collage: top-left corner (x, y), width w, height h, and dy, how much lower its
 * right edge sits (the pin's stamps are sheared, their vertical edges stay vertical).
 * Back to front: the moon peeks over the top right, the cat is cut by the screen and out of focus.
 */
export type StampQuad = { readonly x: number; readonly y: number; readonly w: number; readonly h: number; readonly dy: number };

/**
 * Depth of field: a stamp may go soft towards the bottom of the hero. blur = [top, bottom] sigma
 * in points; the bottom blur fades in from `soft` (share of the height) over 50 pt.
 */
export const PILE: readonly { readonly art: number; readonly quad: StampQuad; readonly blur: readonly [number, number]; readonly soft: number }[] = [
  { art: 5, quad: { x: 288, y: -34, w: 120, h: 150, dy: 10 }, blur: [0, 0], soft: 1 },
  { art: 0, quad: { x: 233, y: 48, w: 122, h: 152, dy: 11 }, blur: [0, 0], soft: 1 },
  { art: 1, quad: { x: 181, y: 100, w: 119, h: 150, dy: 12 }, blur: [0, 0], soft: 1 },
  { art: 2, quad: { x: 129, y: 150, w: 121, h: 160, dy: 12 }, blur: [0, 0], soft: 1 },
  { art: 4, quad: { x: 78, y: 229, w: 119, h: 130, dy: 9 }, blur: [0, 0], soft: 1 },
  { art: 3, quad: { x: 24, y: 291, w: 122, h: 168, dy: 10 }, blur: [0, 2.6], soft: 0.55 },
  { art: 6, quad: { x: -30, y: 355, w: 122, h: 150, dy: 10 }, blur: [1.6, 5], soft: 0.25 },
];

/**
 * Days of May 2026 that hold a stamp: the art, the stamp's size in its cell (square 44 or portrait
 * 35 × 44, as on the pin) and the pile stamp it is a copy of (-1 when the collage has none).
 */
export const MEMORIES: readonly { readonly day: number; readonly art: number; readonly w: number; readonly h: number; readonly twin: number }[] = [
  { day: 5, art: 0, w: 44, h: 44, twin: 1 },
  { day: 9, art: 1, w: 35, h: 44, twin: 2 },
  { day: 10, art: 2, w: 35, h: 44, twin: 3 },
  { day: 12, art: 4, w: 44, h: 44, twin: 4 },
  { day: 19, art: 6, w: 44, h: 44, twin: -1 },
  { day: 23, art: 7, w: 35, h: 44, twin: -1 },
  { day: 30, art: 8, w: 35, h: 44, twin: -1 },
];

/** Where a stamp without a sharp twin in the collage lands: the open corner right of the pile. */
export const FREE_SPOT: StampQuad = { x: 236, y: 214, w: 120, h: 150, dy: 10 };

/** Perforation: ten holes along the width, the same pitch down the sides, hole radius 0.265 × pitch. */
export const PERFORATION = { across: 10, radius: 0.265 } as const;

/** The postmark texture (an alpha field, drawn once per day). */
export const POSTMARK = {
  width: 512,
  height: 256,
  ringX: 384,
  ringY: 128,
  outer: 112,
  inner: 78,
  outerStroke: 9,
  innerStroke: 6,
  dayFont: 62,
  arcFont: 25,
  waves: 4,
  waveFrom: 18,
  waveTo: 262,
  waveGap: 27,
  waveAmp: 7,
  waveLength: 66,
  waveStroke: 7,
  /** Soft edge baked into the alpha so the shader can thin or thicken the strokes. */
  softness: 2.2,
  /** A bold grotesque that exists on each platform (Helvetica is iOS-only; Android's sans-serif is Roboto). */
  font: { ios: 'Helvetica', android: 'sans-serif', default: 'Helvetica' },
} as const;

/** Where the postmark sits on a stamp: ring centre (share of w, h from the centre), radius (share of w). */
export const INK_MARK = { x: 0.17, y: -0.11, radius: 0.27, rotation: -0.17 } as const;

/** Ink and paper colours used inside the shader (sRGB, converted to P3 there). */
export const shaderColors = {
  paperBack: [0.93, 0.905, 0.85],
  inkDry: [0.13, 0.14, 0.2],
  inkWet: [0.16, 0.2, 0.38],
} as const;

export const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
] as const;
export const WEEKDAYS = ['S', 'M', 'T', 'W', 'T', 'F', 'S'] as const;
