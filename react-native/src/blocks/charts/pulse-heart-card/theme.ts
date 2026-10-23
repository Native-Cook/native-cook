/**
 * Pulse Heart Card — colours and layout tokens.
 *
 * Every position and size is in "ref pt": points of the pin's 402 pt wide screen (ref px ÷ 3).
 * The component multiplies them by `screenWidth / 402`. Y values are measured from the top of the
 * screen; the pin's safe area ends at 62 pt.
 */

export const COLORS = {
  background: '#F3F2F8',
  card: '#F8F7FB',
  border: '#D4D3D9',
  ink: '#000000',
  muted: '#88878D',
  faint: '#B9B8C0',
  heart: '#F42E59',
  heartDark: '#E52650',
  bpmUnit: '#55343D',
  flash: '#FFFFFF',
  chartPink: '#F09CAF',
  activeBlue: '#167FE8',
  metricBlue: '#378DDD',
  metricPink: '#DF61D2',
  metricGreen: '#3ACA9B',
  ringRed: '#EE2462',
  ringYellow: '#F1C13A',
  supportValue: '#55545A',
  tabBar: 'rgba(248,248,252,0.72)',
  tabGlassTint: 'rgba(250,250,253,0.55)',
  tabBorder: 'rgba(255,255,255,0.9)',
  shadow: 'rgba(0,0,0,0.12)',
} as const;

/** Spacing scale of the pin (4 pt base; values within 1 pt of the grid are snapped). */
export const SPACE = {
  hairline: 0.8,
  chipGap: 4,
  chipPadding: 10,
  chipIconGap: 10,
  tabPadding: 4,
  screenInset: 32,
  metricGap: 26,
} as const;

export const LAYOUT = {
  /** Metric chips strip (y 62–120). */
  strip: { top: 62, height: 58, radius: 24 },
  chips: { fitness: 137, heart: 118, vitals: 116, icon: 30 },
  /** The heart canvas spans the screen width from under the strip to behind the headline. */
  heartCanvas: { top: 120, bottom: 500 },
  /** The reading inside the heart. Its centre of scale is the heart's centre (y 290). */
  reading: { centreX: 201, numberTop: 231, numberSize: 76, numberLine: 84, numberTracking: -2.2, unitTop: 314, unitSize: 16, unitLine: 20, heartCentreY: 290 },
  headline: { top: 448.5, left: 33, width: 338, height: 72, size: 31, line: 36 },
  body: { top: 524.8, left: 33, width: 338, height: 82, size: 22, line: 26 },
  chart: { top: 631, left: 32, width: 338, height: 100, titleSize: 13.8, titleLine: 19, titleInset: 1.3, plotTop: 19, plotHeight: 64, barBlur: 0.8, labelTop: 83, labelSize: 11, labelLine: 14, origin: 650 },
  support: { top: 749, width: 156, titleSize: 14.5, titleLine: 18, valueSize: 21.5, valueLine: 26, valueGap: 6.8, noteSize: 14.5, noteLine: 18, nextTop: 840 },
  /** Floating tab bar, measured on the pin: 290 × 60 pt, its bottom 22 pt above the screen's. */
  tabBar: { width: 290, height: 60, radius: 30, bottom: 22, item: 94, itemHeight: 52, icon: 22, iconCentre: 20.5, labelSize: 11.5, labelLine: 14, labelTop: 35 },
} as const;

/** Selection lens: glass tinted to the pin's #E2E2E9 pill, a white rim and a sheen over its top half catching the light. */
export const LENS = {
  glassTint: 'rgba(214,214,224,0.55)',
  blur: 24,
  fallback: 'rgba(226,226,233,0.72)',
  sheen: 'rgba(255,255,255,0.55)',
  sheenTo: 0.5,
  rim: 'rgba(255,255,255,0.9)',
  rimWidth: 1,
} as const;

/** Tab bar lens: a spring slide that stretches 12 % on the way. */
export const TAB_MOTION = {
  lens: { duration: 400, dampingRatio: 0.8 },
  stretch: 1.12,
  stretchMs: 120,
  settle: { duration: 300, dampingRatio: 0.7 },
} as const;
