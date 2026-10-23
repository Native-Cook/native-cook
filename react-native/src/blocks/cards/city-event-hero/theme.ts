/**
 * City Event Hero — every colour, size and position.
 *
 * Positions are "ref px": pixels of the Pinterest screen at 1206 × 2622 (an iPhone 17 Pro at 3×,
 * 402 pt wide). The component multiplies them by `screenWidth / 1206`, so on a 402 pt screen they
 * land on the same pixels as the pin. Font sizes are pt at 402 pt and scale the same way.
 */

export const REF_W = 1206;
export const REF_H = 2622;
/** The pin was taken on a phone whose top safe area is 62 pt (186 ref px). */
export const REF_INSET = 186;
/** Font scale reference: 1206 ref px = 402 pt. */
export const REF_PT = 402;
/** Ref y at or above which an item is anchored to the top safe area, below it to the bottom edge. */
export const TOP_GROUP_MAX = 1000;

/** SF Pro: the baseline sits 0.953 em under the top of a Text with no lineHeight. */
export const ASCENT_SF = 0.953;
export const ASCENT_HEADLINE = 0.93;

export const colors = {
  page: '#000000',
  white: '#FFFFFF',
  glassTop: 'rgba(138,138,138,0.485)',
  glassBottom: 'rgba(99,99,99,0.75)',
  wordOutline: '#8A9FA7',
  wordFill: '#274450',
  ink: '#FFFFFF',
  inkSoft: 'rgba(255,255,255,0.60)',
  inkDate: 'rgba(255,255,255,0.62)',
  inkLabel: 'rgba(255,255,255,0.67)',
  chevCard: 'rgba(255,255,255,0.38)',
  chevView: 'rgba(255,255,255,0.64)',
  scrimTop: 'rgba(4,9,11,0.62)',
  scrimTopClear: 'rgba(4,9,11,0)',
  ring: '#FFFFFF',
  /** States the pin does not show, in its own white-on-glass language. */
  heartFill: '#FFFFFF',
  burst: '#FFFFFF',
  sweep: 'rgba(255,255,255,0.30)',
  sweepSoft: 'rgba(255,255,255,0.08)',
  sweepClear: 'rgba(255,255,255,0)',
  /** Haze drifting over the sky: the pin's cyan-grey, linear 0..1 RGB for the shader. */
  mist: [0.8, 0.9, 0.93],
} as const;

export const fonts = {
  headline: 'BricolageHeadline',
  wordmark: 'ArchivoWordmark',
} as const;

/** Text styles: size in pt at 402 pt, weight; `font` unset = system (SF Pro). */
export const type = {
  mute: { size: 16, weight: '400', color: colors.inkSoft, ls: 0.2 },
  headline: { size: 34.33, weight: '400', color: colors.ink, ls: 0, font: fonts.headline },
  date: { size: 15.85, weight: '400', color: colors.inkDate, ls: 0.24 },
  label: { size: 13.85, weight: '400', color: colors.inkLabel, ls: 0.2 },
  title: { size: 15.85, weight: '500', color: colors.ink, ls: 0.2 },
  pill: { size: 14, weight: '500', color: colors.ink, ls: 0.15 },
  pillSmall: { size: 13.85, weight: '500', color: colors.ink, ls: 0.2 },
} as const;

/** `ls` is extra letter spacing in pt at 402 pt (SF Pro tracks a little tighter on device than in a font fit). */
export type TextStyleSpec = { size: number; weight: '400' | '500'; color: string; ls: number; font?: string };

export type Box = { x: number; y: number; w: number; h: number };

export const space = {
  edge: 49,
  pill: 99,
  ring: 6,
} as const;

export const radius = {
  card: 75,
  glassCard: 48,
  thumb: 36,
} as const;

export const layout = {
  card: { x: 0, y: 181, w: 1206, h: 2195 },
  wordmark: { x: 63, base: 306, cap: 52, outline: 11.5, size: 75.8 },
  topPills: {
    gift: { x: 598, y: 231, w: 98, h: 98 },
    byDate: { x: 720, y: 230, w: 314, h: 100 },
    search: { x: 1059, y: 231, w: 98, h: 98 },
  },
  byDate: { x: 835, base: 295 },
  /** Outer diameter and centre; drawn outermost first. */
  guests: [
    { cx: 291, cy: 1539, d: 106, img: 1 },
    { cx: 915, cy: 1539, d: 106, img: 6 },
    { cx: 381, cy: 1533, d: 131, img: 0 },
    { cx: 826, cy: 1533, d: 131, img: 5 },
    { cx: 488, cy: 1526, d: 155, img: 2 },
    { cx: 718, cy: 1526, d: 155, img: 4 },
    { cx: 603, cy: 1520, d: 183, img: 3 },
  ],
  mute: { cx: 609, base: 1692 },
  headline: { cx: 605, base: 1803 },
  date: { cx: 609, base: 1895 },
  presented: { x: 49, y: 1957, w: 1108, h: 222 },
  thumb: { x: 86, y: 1994, w: 148, h: 148 },
  presentedBy: { x: 274, base: 2053 },
  presentedTitle: { x: 275, base: 2114 },
  chevCard: { x: 1070, y: 2050 },
  row: { y: 2228, h: 99 },
  like: { x: 49, w: 180 },
  share: { x: 254, w: 98 },
  more: { x: 376, w: 99 },
  view: { x: 787, w: 370 },
  likeCount: { x: 163, base: 2292 },
  viewText: { x: 838, base: 2292 },
  tab: { y: 2461 },
  /** Ink-left to text-origin correction in ref px (side bearing of the first glyph). */
  bearing: { headline: 5, mute: 2, date: 3, label: 4, title: 4, pill: 3 },
  /** Top of the sky's touch area (ref px): just under the top bar. */
  skyBand: { y0: 345 },
  /** Touch area of the guest stack and its line (ref px). */
  guestBand: { y0: 1400, y1: 1720 },
  /** Canvas square around the heart for its ring burst (ref px, half size). */
  burstHalf: 70,
  /** Inset of the like count's clip from the pill's top and bottom (ref px). */
  countInset: 18,
  /** Share of the photo's overflow cropped from its top edge. */
  photoShift: 0.57,
  /** Thumbnail crop of the photo, in photo px. */
  thumbCrop: { x: 180, y: 300, size: 500 },
} as const;

/** Bottom backdrop fitted to the pin (ref px). */
export const backdrop = {
  fadeFrom: 1450,
  solidFrom: 1760,
  knots: [
    { y: 1760, c: [26, 40, 42] },
    { y: 1860, c: [21, 31, 32] },
    { y: 1960, c: [17, 23, 24] },
    { y: 2060, c: [3, 15, 15] },
    { y: 2160, c: [0, 9, 12] },
    { y: 2260, c: [0, 6, 7] },
    { y: 2330, c: [3, 7, 5] },
    { y: 2372, c: [6, 8, 8] },
  ],
  glows: [
    { cx: 407, cy: 2031, sx: 239, sy: 205, c: [56, 17, 1] },
    { cx: 1104, cy: 2084, sx: 269, sy: 600, c: [32, 9, 0] },
  ],
  topFadeTo: 880,
} as const;

export const copy = {
  wordmark: 'TOWNIE',
  byDate: 'By date',
  mute: 'Amira and 17 more',
  headline: 'Dubai Jan 2026',
  date: 'Thu, 9:00 AM – 17 Jan, 11:45 PM  ·  Dubai',
  presentedBy: 'Presented by',
  presentedTitle: 'Dubai in Jan 2026',
  likes: '1',
  likesNext: '2',
  /** One per guest image (guest-1 … guest-7); the guest line reads "<name> and 17 more". */
  names: ['Amira', 'Kwame', 'Mei', 'Karim', 'Priya', 'Tomás', 'Ingrid'],
  moreSuffix: ' and 17 more',
  view: 'View event',
} as const;

/**
 * Night (round 2). Shader colours are sRGB 0..1 (the shader converts from Display P3 and back);
 * y values are photo px of `assets/city.jpg`.
 */
export const night = {
  zenith: [0.025, 0.045, 0.11],
  horizon: [0.1, 0.11, 0.22],
  /** Sodium glow of the city under the clouds at the horizon. */
  cityGlow: [0.34, 0.17, 0.09],
  /** Dusk afterglow that passes through at mid-fall. */
  afterglow: [0.22, 0.08, 0.12],
  /** Buildings at night: the photo times this. */
  buildings: [0.13, 0.16, 0.25],
  warm: [1.0, 0.74, 0.42],
  cool: [0.8, 0.88, 1.0],
  /** Share of windows lit warm (the rest cool white). */
  warmShare: 0.8,
  bloom: [1.0, 0.62, 0.32],
  flash: [1.0, 0.78, 0.5],
  flare: [1.0, 0.9, 0.72],
  horizonY: 930,
  zenithY: 250,
  skyFrom: 940,
  lightGain: 1.5,
  edgeGain: 1.4,
  bloomGain: 0.55,
  /** Flare at the spire tip: core and halo radius, photo px. */
  flareCore: 9,
  flareHalo: 60,
  /** Window cell of the tower (photo px): the warm/cool pick is per cell. */
  cell: [5, 6],
  /** Fireworks' light on the guests' rings and the headline. */
  rim: '#FFC977',
  headlineGlow: 'rgba(255,190,110,0.95)',
  headlineGlowRadius: 18,
} as const;

/**
 * Round 3: sundial, LED façade, firework relight. Shader colours are sRGB 0..1.
 */
export const scene = {
  /** Sunlight high in the sky and at the horizon. */
  sunHigh: [1.0, 0.93, 0.8],
  sunLow: [1.0, 0.52, 0.24],
  /** How much the sun warms the whole photo, lights the clouds, glints on the glass, rims edges. */
  warm: 0.55,
  lift: 0.22,
  cloudLit: 0.8,
  /** Golden band along the horizon as the sun gets low. */
  horizonGlow: 0.55,
  glint: 1.5,
  glintPower: 60,
  rim: 0.4,
  /** The tower's shadow on the far city: darkening and its cool tint. */
  shadow: 0.62,
  shadowTint: [0.86, 0.92, 1.0],
  /** LED façade. */
  ledOff: 0.28, // the glass behind the LEDs is dimmed to this while the panel is on
  ledGain: 1.35,
  ledField: 0.3, // level of the colour field behind the message
  fieldA: [0.02, 0.62, 0.72],
  fieldB: [0.22, 0.14, 0.62],
  ledText: [1.0, 0.84, 0.52],
  ledHot: [1.0, 0.98, 0.94],
  ledScan: [0.82, 0.96, 1.0],
  ledRing: [1.0, 0.78, 0.36],
  /** Light the façade spills on the haze and the city around the tower. */
  spill: [0.5, 0.72, 0.85],
  spillText: [1.0, 0.78, 0.45],
  halo: 0.55,
  /** Smoke left by the bursts. */
  smoke: [0.3, 0.31, 0.37],
  smokeAlpha: 0.42,
  /** Hour title. */
  title: 'rgba(255,255,255,0.9)',
  titleSoft: 'rgba(255,255,255,0.62)',
} as const;
