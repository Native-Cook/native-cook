/**
 * Health Age Dial — every colour, size and position of the screen, in points on the
 * 402 pt wide iPhone the pin was taken on (pin px ÷ 3). Vertical positions are measured
 * from the top of the screen (the pin's top safe area is 62 pt) or, for the part below the
 * dial, from the top of the scroll content. Insets stay fixed on other widths; widths that
 * span the screen (chips, cards, chart) absorb the difference.
 */

export const PIN_WIDTH = 402;
/** The pin's top safe area; content positions below assume it and shift with the real one. */
export const PIN_SAFE_TOP = 62;

export const colors = {
  page: '#F1EFF8',
  ink: '#0E0E10',
  secondary: '#8B8A92',
  label: '#8E8D94',
  years: '#8D91A2',
  blue: '#0091FF',
  chipFill: 'rgba(250,249,253,0.72)',
  chipBorder: 'rgba(0,0,0,0.12)',
  chipSelected: '#FDFDFE',
  info: '#D0CFD5',
  infoGlyph: '#1F1E23',
  tick: '#A0A8D6',
  halo: '#B9DBFA',
  card: '#FDFDFE',
  sleep: '#7366D8',
  sleepTint: '#F3F1FD',
  sleepGlyph: '#6452E4',
  hrv: '#D2376D',
  hrvTint: '#FDEEF3',
  hrvGlyph: '#E2477A',
  cardBody: '#2E2E33',
  grid: '#DCDBE3',
  baseline: '#BDBCC2',
  axis: '#C6C5CC',
  guide: '#CDCCD3',
  line: '#A5B3DD',
  lineEnd: '#9EC3EC',
  fillTop: 'rgba(206,214,245,0)',
  fillBottom: '#B6D5F7',
  chartDot: '#0A9ADB',
  tabText: '#141416',
  tabSelected: 'rgba(200,199,206,0.7)',
  tabBlue: '#1E86EE',
  white: '#FFFFFF',
} as const;

/** The dial ring's colour stops, by angle in degrees clockwise from 12 o'clock (sampled on the pin). */
export const ringStops: readonly (readonly [number, string])[] = [
  [-164, '#C3E3E7'],
  [-152, '#A9DBE2'],
  [-138, '#96D7E3'],
  [-122, '#83D1EA'],
  [-104, '#7AC7F2'],
  [-88, '#7BC0F5'],
  [-70, '#83BBF7'],
  [-50, '#8DB5F6'],
  [-30, '#9CB7F2'],
  [-10, '#ACB9F3'],
  [8, '#B8C0EF'],
  [28, '#C9C7EC'],
  [50, '#D5D1E7'],
  [72, '#DBD9E8'],
  [96, '#DDDDE3'],
  [130, '#DEDDE3'],
  [164, '#E1DFE5'],
];

/** Spacing scale derived from the pin (pt). The base is 4 pt; values off the grid are the pin's own. */
export const space = {
  textInset: 31, // text column left edge (pin 93–99 px ink; glyph bearings measured on device)
  cardInset: 16, // factor cards (pin 15.0–15.3, snapped)
  chipInset: 18.67, // chip strip outer inset (pin 56 px)
  chipGap: 6, // between the two chips (pin 18 px)
  chipInsetTrailing: 20, // the strip is not symmetric on the pin (60 px on the right)
  chipPadLeading: 13.67, // chip edge → icon
  chipIconGap: 9.3, // icon → text (ink starts 11 pt after the icon)
  statColumn: 211.3, // second stat column (pin 634 px)
  cardGap: 10.33, // between factor cards (pin 31 px)
  cardIconLeading: 16.67, // card edge → icon circle
  cardTextLeading: 77, // card edge → text column
  tabBottom: 23, // tab bar capsule above the screen bottom
} as const;

export const radius = {
  chip: 20,
  card: 24,
  tab: 31.5,
  tabPill: 28,
} as const;

/** Text styles: size, weight, line height (pt). */
export const type = {
  chipTitle: { size: 15, weight: '400', color: colors.secondary },
  chipValue: { size: 14.3, weight: '600', color: colors.ink },
  number: { size: 80, weight: '700', color: colors.blue, tracking: -3.0 },
  unit: { size: 15.2, weight: '600', color: colors.years, tracking: 0 },
  title: { size: 32.7, weight: '700', color: colors.ink, line: 41, tracking: 0 },
  body: { size: 21.85, weight: '400', color: colors.secondary, line: 28, tracking: 0 },
  statLabel: { size: 15.3, weight: '500', color: colors.label },
  statValue: { size: 19.5, weight: '700', color: colors.ink, tracking: -0.4 },
  heading: { size: 21, weight: '600', color: colors.ink },
  axis: { size: 14, weight: '400', color: colors.axis },
  guide: { size: 13, weight: '400', color: colors.guide },
  cardTitle: { size: 15, weight: '600' },
  cardValue: { size: 28, weight: '500', color: colors.ink },
  cardBody: { size: 15, weight: '400', color: colors.cardBody, line: 20 },
  tab: { size: 9.8, weight: '600' },
} as const;

/** Baselines (pt from the top of the screen at scroll 0, pin safe area). */
export const baseline = {
  chipTitle: 87.3,
  chipValue: 106.7,
  number: 325.8,
  unit: 355.3,
  title: 512,
  body: 586.8,
  statLabel: 718.9,
  statValue: 744.5,
} as const;

export const layout = {
  chip: { top: 61.33, height: 61.33, icon: 32.7, ring: 31, leftShare: 177.33 / 357.33 },
  dial: {
    cx: 200, // from the left edge, at 402 pt
    cy: 305.33,
    r: 133.33, // stroke centre line
    stroke: 40.33,
    start: -156, // arc ends, degrees clockwise from 12 o'clock (round caps go 8.7° further)
    end: 156,
    tickLength: 20,
    tickWidth: 4,
    dot: 26,
    /** Degrees of arc per year away from the actual age (the tick). −3.9 years sits at −56°. */
    degPerYear: 14.36,
    maxYears: 9.9,
    canvasPad: 70, // room round the ring for the halo and the comet's glow
    /** The pin's ring has soft edges (a 4 pt transition, unlike its crisp text): blur sigma in pt. */
    edgeSoftness: 1.8,
  },
  info: { cx: 357.67, cy: 173, d: 25 },
  numberCenterX: 188.1, // ink centre of "-3.9" (it sits left of the dial centre on the pin)
  unitCenterX: 200.5,
  halo: { cx: 160, cy: 300, r: 228 },
  // Below the dial (content coordinates: pt from the top of the scroll content, pin safe area).
  historyHeading: 802.3, // baseline
  chart: {
    top: 826,
    bottom: 946.67,
    left: 31,
    right: 316,
    columns: 6,
    base: 860.67, // the actual-age line
    baseRight: 371,
    labelBaseline: 966.3,
    labelOffset: 6.3,
    olderBaseline: 846.7,
    youngerBaseline: 906,
    guideX: 330,
    dotR: 5,
    dotRing: 1.6,
    lineWidth: 3,
  },
  raisingHeading: 1024.3, // baseline
  cards: [
    { top: 1047, height: 161.67 },
    { top: 1219, height: 161.67 },
  ],
  cardIcon: { cy: 43, d: 46.67, glyph: 24 },
  cardTitleBaseline: 31.6,
  cardValueBaseline: 64.6,
  cardBodyBaseline: 100.6,
  contentBottom: 1380.67,
  tab: {
    width: 292,
    height: 63,
    item: 91.67,
    pill: { width: 99, height: 55 },
    icon: { heart: 28, petal: 29, grid: 26.7, labelGap: 4 },
  },
  /** Scroll offset of the pin's second screen (Health Age History + factors). */
  historyOffset: 550,
} as const;

/**
 * The history line, traced from the pin: x in pt across the chart (0 = left grid line),
 * y in pt below the actual-age line. Ends at the dot (this month's health age).
 */
export const historyPoints: readonly (readonly [number, number])[] = [
  [0.0, 15.1], [5.3, 14.6], [10.7, 14.2], [16.0, 14.4], [21.3, 15.1], [26.7, 15.8], [32.0, 20.6], [37.3, 24.9],
  [42.7, 34.5], [48.0, 37.8], [53.3, 37.4], [58.7, 36.3], [64.0, 35.0], [69.3, 35.0], [74.7, 34.0], [80.0, 28.2],
  [85.3, 23.7], [90.7, 22.3], [96.0, 23.2], [101.3, 24.7], [106.7, 25.4], [112.0, 31.1], [117.3, 33.7], [122.7, 30.3],
  [128.0, 38.1], [133.3, 40.9], [138.7, 43.8], [144.0, 45.7], [149.3, 51.9], [154.7, 55.8], [160.0, 54.1], [165.3, 53.3],
  [170.7, 52.2], [176.0, 51.0], [181.3, 51.8], [186.7, 53.9], [192.0, 56.4], [197.3, 58.4], [202.7, 56.7], [208.0, 55.5],
  [213.3, 57.5], [218.7, 59.4], [224.0, 59.1], [233.0, 58.3],
];

export const months = ['Nov', 'Dec', 'Jan', 'Feb', 'Mar', 'Apr'] as const;

export const copy = {
  overviewTitle: 'Overview',
  overviewValue: '5 Excellent',
  chipTitle: 'Health Age',
  chipValue: '-3.9 years',
  unit: 'YEARS',
  title: 'Build on Your Healthy Habits',
  body: 'Your current habits are serving you well. Keep leaning into what’s working to help your body stay younger for years to come.',
  healthAgeLabel: 'Health Age',
  healthAgeValue: '26.1 years',
  actualAgeLabel: 'Actual Age',
  actualAgeValue: '30 years',
  history: 'Health Age History',
  older: 'Older',
  younger: 'Younger',
  raising: 'Raising Your Health Age',
  factors: [
    {
      title: 'Sleep Score',
      years: 1.3,
      body: 'Your average sleep score of 70 suggests you could benefit from more restorative rest to help support healthy aging.',
    },
    {
      title: 'Heart Rate Variability',
      years: 0.3,
      body: 'Your overnight HRV of 38.5 signals your body is carrying some stress. Focus on steady recovery to help it bounce back.',
    },
  ],
  tabs: ['Insights', 'Longevity', 'Browse'],
} as const;

/** The health age and the actual age (years); the dial shows their difference. */
export const AGES = { health: 26.1, actual: 30 } as const;
export const REST_YEARS = -3.9;
