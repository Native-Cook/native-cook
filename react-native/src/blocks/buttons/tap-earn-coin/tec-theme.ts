// Tap Earn Coin — design tokens. Every number here is measured on the reference recording
// (720 × 1280 px, a Korean pay app's "keep pressing to earn" event) and converted to points with
// REF (402 / 720 pt per px at the reference width), then rounded to the 4 pt grid where the
// reference itself sits on it. Layout is computed from the screen size and safe-area insets.

export const REF = 402 / 720;

export const COLOR = {
  pageTop: '#FFF3DF', // sampled at y 0–200
  pageMid: '#FFF9F0', // y 640
  pageBottom: '#FFFFFF', // from y 900 down
  title: '#EC6E0C',
  ink: '#191B21', // subtitle, labels
  unit: '#3F444C',
  digit: '#F28D0E',
  digitBox: '#FEF2DC',
  labelNum: '#BF5700', // the reference's orange darkened to 4.6:1 on white: it is 14 pt text
  cta: '#F56D01',
  ctaPressed: '#DE6200',
  ctaInk: '#FFFFFF',
  dim: 'rgba(20, 16, 10, 0.62)',
  sheet: '#FFFFFF',
  sheetTitle: '#191F28',
  sheetBody: '#6B7684',
  sheetRow: '#4E5968',
  chevron: '#B0B8C1',
  blue: '#3182F6',
  bluePressed: '#2272EB',
  pill: 'rgba(52, 56, 64, 0.88)',
  pillInk: '#FFFFFF',
} as const;

export const FONT = {
  regular: 'TecInter-Regular',
  medium: 'TecInter-Medium',
  semibold: 'TecInter-SemiBold',
  bold: 'TecInter-Bold',
} as const;

export const TYPE = {
  title: { size: 28, line: 37, track: -0.6 },
  subtitle: { size: 16, line: 22, track: -0.2 },
  digit: { size: 27, track: -0.3 },
  unit: { size: 22 },
  label: { size: 14, line: 20, track: -0.1 },
  cta: { size: 17, track: -0.2 },
  sheetTitle: { size: 23, line: 32, track: -0.5 },
  sheetBody: { size: 15, line: 21 },
  sheetRow: { size: 14, line: 20 },
  pill: { size: 13 },
} as const;

/** The 4 pt spacing scale used by every gap below. */
export const SPACE = { xs: 4, s: 8, m: 12, l: 16, xl: 20, xxl: 24, x3: 28, x4: 32 } as const;

export type Layout = {
  w: number;
  h: number;
  /** points per reference pixel at this width (REF on a 402 pt phone) */
  s: number;
  titleTop: number;
  subtitleTop: number;
  /** centre of the gold button's base, and the base's half width */
  coinX: number;
  coinY: number;
  coinR: number;
  digitsTop: number;
  digitW: number;
  digitH: number;
  digitGap: number;
  labelTop: number;
  ctaTop: number;
  ctaH: number;
  side: number;
  sheetH: number;
  sheetTop: number;
  bellX: number;
  bellY: number;
  bellSize: number;
};

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const s = w / 720;
  // The reference starts under a cropped navigation bar: keep its height (52 pt) above the title.
  const titleTop = top + 52;
  const at = (refY: number) => titleTop + (refY - 70) * s;
  const ctaH = 56;
  const ctaBottom = h - bottom - 12;
  const sheetH = 398 + bottom;
  const sheetTop = h - sheetH;
  const bellSize = 99 * s;
  return {
    w,
    h,
    s,
    titleTop,
    subtitleTop: at(212) - 5,
    coinX: w / 2,
    coinY: at(560),
    coinR: 220 * s,
    digitsTop: at(777),
    digitW: 84 * s,
    digitH: 100 * s,
    digitGap: 15 * s,
    labelTop: ctaBottom - ctaH - 20 - TYPE.label.line,
    ctaTop: ctaBottom - ctaH,
    ctaH,
    side: 36 * s,
    sheetH,
    sheetTop,
    bellX: 50 * s + bellSize / 2,
    bellY: sheetTop + 64 * s + bellSize / 2,
    bellSize,
  };
}
