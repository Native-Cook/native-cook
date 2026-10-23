/**
 * Vitals Chip Morph — every colour, size and position of the three screens.
 *
 * Positions are in "ref px": pixels of the pin's screens at 1206 × 2622 (an iPhone 17 Pro at 3×,
 * 402 pt wide). The component multiplies them by `screenWidth / 1206`, so on a 402 pt screen they
 * land on the pin's pixels. Font sizes are in pt for that 402 pt screen and scale the same way.
 * Screen 0 is Readiness (pin 11a, the rest frame), 1 is Fitness (11c), 2 is Heart (11b / pin 05).
 */

export const REF_W = 1206;
/** The pin's phone has a 62 pt top safe area (186 ref px); content below it is laid out from there. */
export const REF_INSET = 186;
/** Font scale reference: 1206 ref px = 402 pt. */
export const REF_PT = 402;

export const colors = {
  page: '#F2F1F7',
  chip: '#F4F3F8',
  chipBorder: '#D2D1D8',
  chipActive: '#FFFFFF',
  ink: '#000000',
  muted: '#88878D',
  faint: '#B9B8C0',
  hairline: '#D9D8DF',
  // Readiness
  track: '#DFDFE9',
  segment: '#B2D3F2',
  blue: '#1592EA',
  blueGlow: '#D4E9FB',
  link: '#1E88E5',
  check: '#1E8DE6',
  iconGrey: '#6E6E73',
  band: '#CFE3F8',
  bandLine: '#D7D7DD',
  // Fitness rings: start → end of each progress gradient, and the ring's own track.
  moveStart: '#FA0232',
  moveEnd: '#FD0172',
  moveTrack: '#F6C0CE',
  exerciseStart: '#05DD04',
  exerciseEnd: '#78E402',
  exerciseTrack: '#C4EDC4',
  standStart: '#02C1DB',
  standEnd: '#02D4B9',
  standTrack: '#B5E5F1',
  glyph: '#14060B',
  movePink: '#E4365F',
  standCyan: '#14B4CF',
  exerciseText: '#4BD12D',
  // Heart
  heart: '#F42E59',
  heartFlat: '#FB4F78',
  heartHalo: '#F7C3CF',
  heartDark: '#E52650',
  chartPink: '#F09CAF',
  bpmUnit: '#55343D',
  supportValue: '#55545A',
  // Chip icons
  sleepTeal: '#1FCFB0',
  sleepCoral: '#F47A5C',
  sleepBlue: '#3D63F0',
  vitalsPink: '#DF61D2',
  vitalsBlue: '#378DDD',
  summaryHeartTop: '#FF5C95',
  summaryHeartBottom: '#F0246A',
  // Tab bar
  tabGlass: 'rgba(248,248,252,0.78)',
  /** The Liquid Glass tab bar's tint (iOS 26): as light as the pin's bar. */
  tabGlassTint: 'rgba(250,250,253,0.6)',
  tabRim: 'rgba(255,255,255,0.9)',
  tabActive: 'rgba(118,118,128,0.14)',
  tabBlue: '#1F8BF0',
} as const;

/** Text styles: size in pt (at 402 pt), weight, colour, letter spacing in pt. */
export const type = {
  chipTitle: { size: 15.2, weight: '400', color: colors.muted, ls: 0 },
  chipValue: { size: 15, weight: '600', color: colors.ink, ls: 0 },
  readiness: { size: 77, weight: '600', color: colors.blue, ls: 0, rounded: true },
  bpm: { size: 76, weight: '600', color: colors.ink, ls: -2.2 },
  bpmUnit: { size: 16, weight: '500', color: colors.bpmUnit, ls: 0 },
  heading: { size: 30.8, weight: '700', color: colors.ink, ls: 0 },
  body: { size: 21.8, weight: '400', color: colors.muted, ls: 0 },
  section: { size: 21.1, weight: '700', color: colors.ink, ls: 0 },
  link: { size: 15.2, weight: '600', color: colors.link, ls: 0 },
  value: { size: 18.4, weight: '700', color: colors.ink, ls: 0 },
  stat: { size: 15.5, weight: '600', color: colors.movePink, ls: 0 },
  metricLink: { size: 14.5, weight: '600', color: colors.heartDark, ls: 0 },
  metricValue: { size: 20.5, weight: '600', color: colors.ink, ls: -0.2 },
  metricSub: { size: 14, weight: '400', color: colors.faint, ls: 0 },
  axis: { size: 11, weight: '400', color: colors.faint, ls: 0 },
  chartTitle: { size: 15, weight: '600', color: colors.ink, ls: 0 },
  tab: { size: 11.2, weight: '500', color: colors.ink, ls: 0 },
} as const;

export type TextSpec = (typeof type)[keyof typeof type];

/**
 * The block's spacing scale, derived from the pin (ref px; ÷ 3 for pt). Base grid: 4 pt; the
 * pin's own values that are not on the grid keep their measured size (each ≤ 1 pt from it is snapped).
 */
export const space = {
  /** Left edge of every text column (32 pt). */
  screenInset: 96,
  /** Gap between two chips (7.5 pt, measured; not on the grid). */
  chipGap: 22.5,
  /** Chip padding before the icon centre (28 pt) and before the label (54.7 pt). */
  chipIconCentre: 84,
  chipLabel: 161,
  /** Cap tops of a chip's title and value, from the chip's top. */
  chipTitleCap: 39,
  chipValueCap: 97,
  /** A link row: the check's left edge to the label. */
  linkLabel: 50,
  /** Body line height (26 pt) and heading line height (36 pt). */
  bodyLine: 78,
  headingLine: 108,
  /** A metric block: label cap top → value cap top (23 pt) → sub-label cap top (48.7 pt). */
  metricValue: 69,
  metricSub: 146,
  /** Distance a page slides while it hands over to the next one (24 pt). */
  pageSlide: 72,
} as const;

/** Radii (ref px). Nested radii are outer − padding. */
export const radius = {
  chip: 66,
  tabBar: 93,
  tabPill: 81,
} as const;

/** A chip in the metric row, in row coordinates (ref px, before the row's scroll offset). */
export type ChipSpec = { key: string; title: string; value: string; x: number; w: number };

const CHIP_W = [175, 315.5, 420, 446, 355, 339];
const CHIP_X: number[] = [];
CHIP_W.reduce((x, w) => {
  CHIP_X.push(x);
  return x + w + space.chipGap;
}, 51);

export const chips: readonly ChipSpec[] = [
  { key: 'summary', title: 'Summary', value: '', x: CHIP_X[0], w: CHIP_W[0] },
  { key: 'sleep', title: 'Sleep', value: 'High', x: CHIP_X[1], w: CHIP_W[1] },
  { key: 'readiness', title: 'Readiness', value: 'Ready', x: CHIP_X[2], w: CHIP_W[2] },
  { key: 'fitness', title: 'Fitness', value: '5,253 steps', x: CHIP_X[3], w: CHIP_W[3] },
  { key: 'heart', title: 'Heart', value: '62 BPM', x: CHIP_X[4], w: CHIP_W[4] },
  { key: 'vitals', title: 'Vitals', value: 'Typical', x: CHIP_X[5], w: CHIP_W[5] },
];

/** Chip index of each screen (Readiness, Fitness, Heart). */
export const SCREEN_CHIP = [2, 3, 4] as const;

/**
 * The screen each chip opens. The block has three screens, so the other chips go to the one that carries their metric:
 * Summary and Sleep to Readiness (its "Sleep Score" row and summary copy), Vitals to Heart. The selection then lands on that
 * screen's chip, as a tap on Heart does.
 */
export const CHIP_SCREEN = [0, 0, 0, 1, 2, 2] as const;

export const layout = {
  chipTop: 186,
  chipH: 174,
  /** Row scroll offset per screen: the active chip sits where the pin shows it (x 585.5 / 377 / 427). */
  rowOffset: [0, 377 - CHIP_X[3], 427 - CHIP_X[4]] as const,

  /** The hero canvas spans the full width from y 400 to 1420 (ref px). */
  heroTop: 400,
  heroH: 1020,
  /** The hero's touch area (it stops above the headline). */
  heroTouchH: 800,

  // Readiness gauge (screen coordinates)
  gauge: { cx: 603, cy: 1072, r: 455, stroke: 130, from: 167.5, to: 12.5 },
  /** The light segment: butt ends with 40 px corners (t0 at 97.6°, t1 at 54.8°); the dot (60°) sits 0.0335 behind t1. */
  segment: { t0: 0.4510, t1: 0.7303, corner: 40 },
  dot: { lag: 0.0335, d: 76, glow: 150 },
  seven: { cx: 601.5, capTop: 861 },

  // Fitness rings (screen coordinates); progress starts at 12 o'clock and runs clockwise.
  rings: { cx: 608, cy: 851.5, stroke: 88, r: [359, 262, 165], progress: [0.75, 0.633, 0.333] },

  // Heart: the rendered artwork's canvas, registered to the pin (silhouette bbox x 244–959, y 518–1208).
  heart: { x: 213, y: 488, w: 780, h: 750, cx: 601.5, cy: 863 },
  bpm: { capTop: 718, unitCapTop: 963 },

  // Readiness page
  checks: {
    cy: 1206,
    groups: [
      { icon: 280, check: 370 },
      { icon: 562, check: 642 },
      { icon: 836, check: 915 },
    ],
    dividers: [465, 742],
    checkD: 50,
    iconD: 56,
    dividerH: 60,
  },
  pageHeadingCapTop: 1366,
  readyBodyTop: 1480,
  raisingCapTop: 1891,
  vitalsLinkCapTop: 2021,
  vitalsChevronX: 520,
  sleepChevronX: 412,
  typicalCapTop: 2085,
  miniChart: { x: 668, w: 444, lineTop: 2006, bandTop: 2023, bandH: 106, lineBottom: 2137, iconsCy: 2186,
    dots: [[720, 2099], [802, 2059], [887, 2097], [972, 2087], [1057, 2074]] as const, dotD: 26 },
  sleepLinkCapTop: 2284,
  highCapTop: 2342,
  sleepRing: { cx: 1003, cy: 2372, r: 80, stroke: 30 },

  // Fitness page
  fitBodyTop: 1590,
  /** Fitness mini chart (ref px, from the chart's left edge): bars rise from rowCy + base, 10 px apart. */
  fitChart: { x: 96, w: 450, rowsCy: [1852, 1940, 2006], labelsTop: 2052, base: 28, barX: 130, barPitch: 10, barW: 4.2,
    dotX: 146, dotPitch: 20, dotR: 7, labelPitch: 135, gridW: 0.88 },
  fitStats: { x: 620, capTops: [1852, 1919, 1987] },
  fitMetricsCapTop: 2160,

  // Heart page (copied by value from pulse-heart-card)
  heartBodyTop: 1578,
  heartChart: { titleTop: 1907, axisCapTop: 2169, axisRightX: 1040 },
  /** Right-hand metric column on the Heart screen (pin 05). */
  heartRightColumn: 636,
  heartMetricsTop: 2247,

  /** Content that has slid under the tab bar's glass is soft (Gaussian σ in ref px). */
  underGlassBlur: 7,
  tabBar: { x: 169, y: 2369, w: 867, h: 189, pill: { x: 177, y: 2386, w: 295, h: 164 }, iconCy: 2441, labelCapTop: 2499, centres: [326, 600, 876] },
} as const;

/**
 * The liquid material (round 2). Colours are sRGB 0–255 (converted to P3 in the shader); lengths are
 * ref px. The studio is what the glass reflects at its rims; the rest is the bevel, the wobble and
 * the heartbeat's ripple.
 */
export const liquidLook = {
  skyTop: [255, 255, 255],
  skyLow: [216, 220, 232],
  horizon: [118, 122, 140],
  floorNear: [198, 201, 214],
  floorFar: [240, 241, 246],
  /** Blur σ of the height field / melt; parts closer than ~1.5σ neck together. */
  softSigma: 15,
  /** Where the shape closes (arc caps → ring bottom) and opens (the heart's tip): the melt's centre and reach. */
  gooCentre: [605, 1195],
  gooRadius: 260,
  /** Largest offset any child sample can take (wobble + ripple + refraction + slope step). */
  sampleRadius: 64,
  slopeStep: 3,
  /** Bevel steepness: alpha slope × this = the normal's tilt. */
  slope: 70,
  refractPx: 12,
  wobblePx: 7,
  ripSpeed: 820,
  ripWidth: 70,
  ripDecay: 0.5,
  ripBirth: 60,
  /** 2π / 64 px wavelength. */
  ripK: 0.0982,
  /** Small: the edge barely moves; the ripple reads through its glints. */
  ripPx: 3.5,
  ripSlope: 0.95,
  ripLight: 1,
} as const;

/** The chip row's Liquid Glass lens: it lifts off the white pill while the selection travels. */
export const lens = {
  /** Scale while lifted. */
  liftScale: 1.06,
  /** A white veil in the glass, so the capsule still reads as the selection. */
  tint: 'rgba(255,255,255,0.28)',
  /** Selection progress that counts as "travelling" (lens on) between the two ends. */
  onFrom: 0.02,
  offAt: 0.97,
  /** The glass's own appear / leave fade (s). */
  fade: 0.22,
} as const;
