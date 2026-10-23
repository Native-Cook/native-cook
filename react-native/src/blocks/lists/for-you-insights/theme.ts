/**
 * For You Insights — every colour, type style, spacing token and measured position.
 *
 * Measured on the pin's screen (`qa/ref-06.png`, 1206 × 2622 px = 402 pt at 3×, top safe area 62 pt).
 * Positions are pt from the top of a 402 pt screen whose safe area is 62 pt; the component
 * scales them by `screenWidth / 402` and shifts them by `safeTop - 62`. Text positions are the
 * top of the Text box (calibrated so the glyphs land on the pin's glyphs, not on a grid line).
 */

export const REF_WIDTH = 402;
export const REF_SAFE_TOP = 62;

export const colors = {
  page: '#F2F1F6',
  ink: '#111113',
  secondary: 'rgba(60,60,67,0.60)',
  tertiary: 'rgba(60,60,67,0.58)',
  /** Read time row: the pin's clock and text integrate darker than the summary. */
  meta: 'rgba(60,60,67,0.66)',
  railTitle: 'rgba(60,60,67,0.72)',
  /** The pin's dividers are soft 1 pt lines (peak Δ ≈ 22 over 4–5 px at 3×): 1 pt at 0.13 integrates to the same. */
  separator: 'rgba(60,60,67,0.13)',
  pink: '#E648A5',
  violet: '#8B5CF6',
  blue: '#1E8BEA',
  white: '#FFFFFF',
  tile: '#F5F4FA',
  tileBorder: 'rgba(60,60,67,0.22)',
  tileHeart: '#FFFFFF',
  heart: '#F23A7E',
  heartLine: 'rgba(60,60,67,0.20)',
  sleepTeal: '#21C7B4',
  sleepOrange: '#F3826A',
  sleepBlue: '#4E63F8',
  readyTrack: 'rgba(60,60,67,0.13)',
  readyLight: '#A9CCEB',
  readyStrong: '#3C8FDA',
  fitMove: '#EE2A6B',
  fitExercise: '#64D23C',
  fitStand: '#21C7D9',
  ellipsisFill: 'rgba(60,60,67,0.08)',
  pillFill: 'rgba(118,118,128,0.12)',
  pillEdge: 'rgba(255,255,255,0.70)',
  tabGlassTint: 'rgba(255,255,255,0.62)',
  tabFallback: 'rgba(250,250,252,0.82)',
  tabEdge: 'rgba(255,255,255,0.80)',
  closeDisc: 'rgba(255,255,255,0.86)',
  shadow: '#1B1530',
  caretGlow: '#F062B8',
  shimmer: ['rgba(255,255,255,0)', 'rgba(255,214,240,0.85)', 'rgba(255,255,255,0.95)', 'rgba(205,226,255,0.85)', 'rgba(255,255,255,0)'],
  ghost: 'rgba(17,17,19,0.16)',
} as const;

/** Text styles: size, line height and tracking in pt at 402 pt, SF Pro weight. */
export const type = {
  heading: { size: 28, weight: '600', tracking: -0.2 },
  label: { size: 12, weight: '700', tracking: 0.46 },
  tip: { size: 21, line: 27, weight: '700', tracking: -0.24 },
  rowTitle: { size: 18, line: 22.2, weight: '600', tracking: -0.5 },
  rowSummary: { size: 15.5, line: 20, weight: '400', tracking: -0.15 },
  rowMeta: { size: 14, weight: '500', tracking: 0 },
  railTitle: { size: 14, line: 18, weight: '400', tracking: -0.1 },
  railValue: { size: 14.5, line: 19, weight: '700', tracking: -0.3 },
  railNumber: { size: 12.5, weight: '700', tracking: 0 },
  tab: { size: 10.5, weight: '600', tracking: 0 },
  readerTitle: { size: 30, line: 36, weight: '700', tracking: -0.6 },
  readerMeta: { size: 13, weight: '600', tracking: 0.3 },
  readerLead: { size: 18, line: 26, weight: '500', tracking: -0.2 },
  readerHeading: { size: 19, line: 25, weight: '700', tracking: -0.3 },
  readerBody: { size: 16, line: 24, weight: '400', tracking: -0.1 },
  sectionSubhead: { size: 15, weight: '500', tracking: -0.1 },
  habitTitle: { size: 18, weight: '600', tracking: -0.2 },
  habitBody: { size: 15, line: 21, weight: '400', tracking: -0.1 },
} as const;

/** The pin's spacing scale (4 pt base). Every gap in the component names one of these. */
export const space = {
  xxs: 4,
  xs: 8,
  sm: 12,
  md: 16,
  lg: 20,
  xl: 24,
  xxl: 28,
  screen: 32,
} as const;

export const radius = {
  tile: 20,
  image: 26,
  hero: 28,
  tabBar: 31,
  tabPill: 26,
  ellipsis: 10,
  close: 18,
  habitIcon: 14,
} as const;

/** Measured positions (pt at 402 wide, safe top 62). */
export const layout = {
  /** Context rail: tiles 58 tall at y 62, inset 16, 4 apart; the rail's divider 8 below. */
  railTop: 62,
  railHeight: 58,
  railInset: space.md,
  railGap: space.xxs,
  railDivider: 127.7,
  tiles: [
    { kind: 'heart', width: 62.7 },
    { kind: 'sleep', width: 108.3, title: 'Sleep', value: 'High' },
    { kind: 'ready', width: 134, title: 'Readiness', value: 'Ready' },
    { kind: 'fitness', width: 112, title: 'Fitness', value: '5 of 7' },
  ],
  railRing: 33,
  railRingPad: space.sm,
  railTextGap: 10,
  railTitleTop: 71.8,
  railValueTop: 90,
  heartTop: 76,
  heartSize: 20,
  heartLines: [{ width: 21, top: 97.5 }, { width: 16, top: 104 }],
  /** Text column: x 32, 338 wide; article text 164 wide; art at x 216, 154 square. */
  column: space.screen,
  columnWidth: 338,
  headingTop: 150.7,
  headingDivider: 200.7,
  labelTop: 218,
  labelHeight: 20,
  labelIcon: 12,
  labelInset: 1.5,
  labelIconGap: space.xxs,
  ellipsisCenter: { x: 358.8, y: 227.2 },
  ellipsisSize: 20,
  ellipsisGlyph: 15,
  tipTop: 243.7,
  tipLines: 5,
  tipDivider: 394.7,
  rowTop: 411,
  /** Browse lists the same rows under its subhead: 218 + 20 + 16. */
  browseRowTop: 254,
  habitTop: 254,
  habitPitch: 120,
  habitIcon: 44,
  rowPitch: 187,
  /** Wraps like the pin: 'Unlock All the Best' (147 pt) fits, '5 Tricks That Unlock' (≈155 pt) does not. */
  rowTextWidth: 151,
  rowImageX: 216,
  rowImageSize: 154,
  rowSummaryOffset: 70.3,
  rowMetaOffset: 135.7,
  rowMetaIcon: 14,
  rowMetaGap: 5,
  rowDividerOffset: 170.7,
  /** Floating tab bar: 290 × 62 at y 791, a 97 × 52 pill, tab centres 91.5 apart. */
  tabBarTop: 791,
  tabBarWidth: 290,
  tabBarHeight: 62,
  tabPillWidth: 97,
  tabPillHeight: 52,
  tabPitch: 91.5,
  tabIcon: 27,
  tabIconCenter: 25,
  tabLabelCenter: 47.3,
  /** Reader (ours; the pin shows only the feed). Built from the same scale. */
  heroTop: 70,
  heroInset: space.lg,
  heroHeight: 300,
  closeSize: 36,
  closeInset: space.sm,
  readerTextInset: space.xxl,
  readerTitleGap: space.xl,
  readerMetaGap: space.sm,
  readerSectionGap: space.xl,
  readerParagraphGap: space.xs,
  readerBottom: space.screen,
  divider: 1,
} as const;
