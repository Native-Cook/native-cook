// Measured from the pin, its phone screen rectified to 1206×2622 (iPhone 17 Pro, 3×), so every value
// below is in points from the top-left of the screen. Vertical values assume the safe top of 62 pt;
// `topOffset()` moves the layout for another safe area. See out/wallet-transactions/pixel/measure.md.

export const COLOR = {
  white: '#FFFFFF',
  ink: '#1F1F1E',
  figure: '#272726',
  label: '#737272',
  meta: '#787777',
  grey: '#A6A6A5',
  icon: '#6F6E6D',
  pill: '#8FA1FD',
  pillRim: '#8394F2',
  band: '#1F1F1F',
  actionText: '#F2F2F2',
  actionIcon: '#FFFFFF',
  dots: '#7D7D7C',
  grabber: '#F1F1F0',
  chipText: '#737372',
  chipActiveText: '#EF60D9',
  invest: '#F25EEA',
  shared: '#F8B000',
  rowIconEdge: '#EFEFEF',
} as const;

/** The pin's soft, sunk-in fills: darker at the top edge, lighter at the bottom. */
export const FILL = {
  tile: ['#EFF0EF', '#F7F7F6', '#FAFAF9'],
  tileEdge: '#F2F2F1',
  button: ['#EEEEEE', '#FDFDFD'],
  iconSquare: ['#D8D8D7', '#E9E9E8'],
  chip: ['#EFEFEF', '#F8F8F7', '#FAFAF9'],
  chipActive: ['#EEE6EC', '#F6E8F3', '#F6ECF4'],
  actionCircle: ['#2A2A29', '#383837'],
  actionRimTop: '#6A6968',
  actionRimBottom: '#5A5958',
  rowGrey: ['#B4B4B3', '#BEBEBD'],
} as const;

export const SCREEN = { safeTop: 62, inset: 16 } as const;

export const HEADER = {
  markX: 16, markY: 72.7, markW: 27, markH: 29.3,
  wordX: 47, wordCapTop: 79, wordSize: 23,
  buttons: [306.9, 364.3], buttonY: 86.8, button: 44, glyph: 22,
} as const;

export const BALANCE = {
  labelX: 14.7, labelCapTop: 148, labelSize: 17,
  pill: { x: 301, y: 138.7, w: 85.7, h: 29, textSize: 16 },
  dollarX: 15, dollarTop: 198, dollarSize: 22,
  amountX: 35.4, amountCapTop: 192.7, amountSize: 44,
} as const;

export const TOP_SHEET = { bottom: 411.3, radius: 30 } as const;

export const TILES = {
  top: 264, height: 133, radius: 22,
  xs: [15, 152, 305], widths: [127, 143.5, 73],
  icon: { x: 13.3, y: 13.5, size: 27.4, radius: 7, glyph: 16 },
  labelX: 49, labelCapTop: 21.3, loansLabelX: 14.3, loansCapTop: 16.7, labelSize: 15,
  holdings: { x: 13.2, cy: 67, size: 23.7, step: 21.9, ring: 1.3 },
  dollarX: 13.7, dollarTop: 94, dollarSize: 12,
  amountX: 25.7, amountCapTop: 96.7, amountSize: 26,
  arrow: { x: 16, y: 98.3, w: 18, h: 15 },
} as const;

export const ACTIONS = {
  y: 427.8, height: 44, circle: 30, circleInset: 7, textGap: 8, textSize: 15,
  items: [{ x: 15.3, w: 105 }, { x: 129, w: 107 }, { x: 247.7, w: 80 }, { x: 340, w: 45 }],
} as const;

export const SHEET = {
  top: 489.3, radius: 30,
  grabber: { y: 499.8, w: 38, h: 6 },
  titleX: 15.7, titleCapTop: 520.7, titleSize: 19,
  viewAll: { x: 300.7, y: 514.1, w: 77, h: 28.5, textX: 8.6, textSize: 14.5 },
  chipsY: 551, chipH: 36, chipSize: 14.5,
  rowsTop: 604.6, pitch: 62.8, icon: 49.7, iconRadius: 12, nameX: 73.8,
  nameSize: 18, metaSize: 13, amountSize: 18, amountRight: 380.7,
  fadeTop: 785, // the last row fades into the sheet, as the pin shows
} as const;

export const CHIPS = [{ x: 15.7, w: 43.6 }, { x: 67.3, w: 91.3 }, { x: 167.3, w: 75.7 }] as const;

export function topOffset(safeTop: number) {
  return safeTop > 0 ? safeTop - SCREEN.safeTop : -40;
}

// System font metrics (SF Pro): the cap height is 0.705 of the size; a Text box puts the cap top
// about 0.25 of the size below its top at the default line height.
export const CAP = 0.705;
