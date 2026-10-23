/**
 * Pit Stop Review — tokens. Points of the pin's 402 pt screen, measured on the pin at 3× and kept to a
 * third of a point; content y is relative to the end of the status-bar safe area (62 pt on the pin).
 * Base grid 4 pt; values that are not on it are the pin's own measurements (named so).
 */

export const colors = {
  ink: '#F8F5FA',
  muted: '#B2ADB8',
  /** The pin's page is pure black below the hero (sampled 0–1). */
  bg: '#000000',
  panel: '#1A181C',
  violet: '#361CFF',
  orange: '#FF4A08',
  yellow: '#FFE62E',
  light: '#FF3B30',
  lightCore: '#FFD2C2',
  lightOff: '#2B0C0B',
  /** Brake disc heat: core and bloom. */
  discHot: '#FF6A1A',
  discBloom: '#E2340C',
} as const;

/** Top bar: the Club Pass pill and the avatar share one centre line. */
export const BAR_CENTER = 25.1;

export const clubPass = {
  left: 28.7,
  height: 28,
  /** Pill 94.3 × 28 (283 × 84 px on the pin). The white badge is concentric with the pill's left cap: padding = (height − badge) / 2. */
  badge: 17.7,
  padLeft: 5.15,
  /** Label box → pill end; with the glyph side bearing the visible gap is 10.7 (32 px), close to the space above and below the capitals ((28 − 8.3) / 2 ≈ 9.9). */
  padRight: 9.5,
  gap: 5.3,
  /** Four-point sparkle of the badge, measured 8.0 × 8.7 on the pin (fuller than a glyph star). */
  sparkleW: 8,
  sparkleH: 8.7,
  fontSize: 12,
  /** Semibold matches the pin's stroke (bold carried 13 % more ink); −0.2 tracking keeps the pin's 55.3 pt run (166 px). */
  fontWeight: '600' as const,
  letterSpacing: -0.2,
  lineHeight: 15,
} as const;

export const avatar = {
  /** Touch target; the ring is drawn inside it. */
  hit: 44,
  /** Distance from the screen's right edge to the touch target (ring centre sits 22 pt inside it, at x = 361). */
  right: 19,
  ring: 40.6,
  photo: 33.3,
} as const;

/** Hero: a 402 × 220 design box at content y 32; the car canvas inside it. */
export const hero = {
  top: 32,
  width: 402,
  height: 220,
  /** Car canvas (the 820 × 320 px render); y puts the render's body centre on the pin's car centre (hero y 85). */
  car: { x: 70, y: 26, w: 273.33, h: 106.67 },
  /** Light trail behind the car, measured on the pin's hero (design pt, car at rest). */
  trail: {
    blooms: [
      { x: -80, y: 36, w: 220, h: 140, blur: 20, color: '#FF2A0A', alpha: 0.95 },
      { x: -60, y: 54, w: 180, h: 104, blur: 12, color: '#FF5A1A', alpha: 0.7 },
    ],
    core: { x: -50, y: 70, w: 150, h: 36, blur: 6 },
    streaks: 22,
    streakTop: 62,
    streakSpan: 50,
  },
} as const;

/** Start-light gantry under the parked car (hero coordinates). */
export const gantry = {
  cx: 201,
  cy: 178,
  pitch: 20,
  radius: 6.5,
  housingH: 20,
  housingPad: 7,
} as const;

/** Story pips under the CTA (pin: centre y 488.3 on screen, active 24.7 × 4, dots 3.3, gaps 8.7 / 9.0). */
export const pager = {
  top: 422.3,
  height: 8,
  active: 24.7,
  dot: 3.3,
  gap: 8.8,
} as const;

/** Liquid Glass tab bar (pin: 358 × 64, 20 above the bottom; five equal cells). */
export const tabBar = {
  /** Pin: 359 × 61.6 with its bottom 25.7 above the screen edge; lens 74 × 52 on the active cell (Paddock's dock). */
  width: 358,
  height: 62,
  bottom: 25.5,
  padding: 7,
  icon: 22,
  lensWidth: 74,
  lensHeight: 52,
  iconColor: 'rgba(255,255,255,0.94)',
  /** Lens highlight over the glass (pin: the active cell reads ~62 grey over the ~28 bar). */
  lensFill: 'rgba(255,255,255,0.16)',
} as const;

/** Composer star row: 250 × 48, five 50 pt cells, the star drawn in a 28 pt box. */
export const stars = {
  row: 250,
  cell: 50,
  height: 48,
  box: 28,
  radius: 11,
} as const;

/** Montserrat (OFL, `assets/Montserrat-OFL.txt`, the same files as the sibling Paddock block) is the pin's face. */
export const FONT_FILES = {
  'PitStopMontserrat-Medium': require('./assets/PitStopReview-Montserrat-Medium.ttf'),
  'PitStopMontserrat-SemiBold': require('./assets/PitStopReview-Montserrat-SemiBold.ttf'),
  'PitStopMontserrat-Bold': require('./assets/PitStopReview-Montserrat-Bold.ttf'),
  'PitStopMontserrat-ExtraBold': require('./assets/PitStopReview-Montserrat-ExtraBold.ttf'),
  'PitStopMontserrat-Black': require('./assets/PitStopReview-Montserrat-Black.ttf'),
} as const;
export const fonts = {
  medium: 'PitStopMontserrat-Medium',
  semibold: 'PitStopMontserrat-SemiBold',
  bold: 'PitStopMontserrat-Bold',
  extraBold: 'PitStopMontserrat-ExtraBold',
  black: 'PitStopMontserrat-Black',
} as const;
/** Montserrat's ascender per point of size: a line's top is `baseline − ASCENT · size`; cap height is 0.70 em. */
export const ASCENT = 0.968;

/**
 * Hero copy block, content coordinates (screen − 62). The pin centres it at x = 191, not on the screen's middle;
 * sizes come from cap heights (headline E 21.3 pt, subtitle S 10.0, button W 10.7) over Montserrat's 0.70 em caps.
 */
export const heroText = {
  centerX: 191,
  title: { size: 30.4, baseline: 263, tracking: -0.3 },
  subtitle: { size: 14.3, baseline: 293, lineHeight: 20.3, width: 330 },
  cta: { top: 332.7, width: 177.4, height: 44.3, size: 15.3, tracking: -0.1 },
} as const;

/** Shortcut rail (pin: 278 × 60.7 at content y 449.3, fill rgb 19, hairline rgb 45; icon centres at x 101.3 / 167.7 / 234 / 300.5). */
export const rail = {
  top: 449.3,
  width: 278,
  height: 60.7,
  fill: 'rgb(19,19,19)',
  stroke: 'rgb(45,45,45)',
  iconY: 30.4,
  iconX: [101.3, 167.7, 234, 300.5],
  /** Ink sizes measured on the pin. */
  calendar: 25.7,
  controller: 25.7,
  shapes: 21.4,
  sparkle: 24,
  divider: { height: 27, color: 'rgba(255,255,255,0.2)' },
} as const;

/**
 * Bento under the rail: the sibling Paddock block's tiles (same app), at this pin's footprint. Tiles 184 × 60.3 at
 * x 10.7, rows 73.8 apart from content y 529; the pitcast card 183.3 wide at x 207.7. Colours sampled on the pin
 * equal Paddock's. Inner values (baselines, marks, chips) are Paddock's round-2 measurements.
 */
export const bento = {
  top: 529,
  left: 10.7,
  tile: { width: 184, height: 60.3, pitch: 73.8, radius: 30.15 },
  pitcast: { left: 207.7, width: 183.3, height: 210, radius: 28, inset: 14.5, arrowInset: 12.5, mic: 50, micRadius: 13.5, arrow: 18.5, glyphRow: 179.8, glyphX: [25.7, 62, 99], glyph: 20 },
  colors: { violet: 'rgb(50,33,252)', orange: 'rgb(254,63,3)', yellow: 'rgb(255,218,38)', cream: 'rgb(242,238,223)', micInk: 'rgb(255,196,178)' },
  type: { eyebrow: 12.5, eyebrowTracking: -0.3, title: 16.5, titleTracking: -0.2, violet: 19.5, violetTracking: -0.9, badge: 13, chipIcon: 13, cardIcon: 24, pitcastTitle: 32, pitcastSubtitle: 12.2, pitcastNames: 10.2, pitcastIcon: 26, pitcastArrow: 8 },
  baseline: { eyebrow: 25.8, title: 44.8, violet: [32, 47.5], violetX: 71.7, pitcastTitle: 106.8, pitcastSubtitle: 131.4, pitcastNames: 152.5 },
  violetBadge: { width: 47.5, height: 28.5, centerX: 38.5, stroke: 2.5 },
  orangeChip: { width: 30, height: 19, trailing: 14.7, radius: 5.5 },
  /** Paddock's stats card (382 × 144 there; this pin's columns make it 380.3 wide). */
  stats: { width: 380.3, height: 144, radius: 28, glow: 'rgb(42,40,40)', shade: 'rgb(20,18,21)', icon: { x: 25, y: 27.5, width: 41, height: 51 }, iconSize: 50, textX: 85.3, eyebrow: 16, eyebrowBaseline: 18.3, word: 32.5, wordBaseline: 54.8, captionX: 21.3, captionSize: 12, captionBaselines: [123.4, 138.2], caption: ['Stats, records & stories', 'from Turn Nine'] },
  tileInset: 16,
  tileTextAfterMark: 60.5,
  tileMarkCenter: 31,
} as const;

/**
 * Cool, darker grade of the rendered car (Skia ColorMatrix, 4 × 5, offsets in 0…1): the flat render-white drops to a
 * photographic grey so only clear-coat highlights read white, and the whole car leans blue-grey like the pin's.
 * out = gain · in + offset per channel: white 1 → 0.97, mid 0.6 → 0.51, shadow 0.3 → 0.17.
 */
export const carGrade = [
  1.12, 0, 0, 0, -0.17,
  0, 1.14, 0, 0, -0.17,
  0, 0, 1.19, 0, -0.16,
  0, 0, 0, 1, 0,
];

/**
 * Page tone behind the hero, sampled on the pin (neutral grey): 19 at the top bar, 18 at the headline,
 * 5 under the subtitle, 1 by the button, black below. Positions are content y over the gradient's 450 pt.
 */
export const pageTone = {
  height: 450,
  colors: ['rgb(19,19,19)', 'rgb(18,18,18)', 'rgb(5,5,5)', 'rgb(1,1,1)', 'rgb(0,0,0)'] as const,
  locations: [0, 0.53, 0.71, 0.86, 1] as const,
};
