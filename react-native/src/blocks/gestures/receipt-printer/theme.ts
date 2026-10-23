/**
 * Colours, type and layout, measured on the source (rectified to 402 × 874 pt). Receipt rows are placed by the
 * top of their capitals (`cap`), in points from the top of the screen when the receipt has just been printed
 * (frame 017); rows further down come from frame 030 plus its scroll (623 pt).
 */

export const COLOR = {
  bg: '#EFEFEF',
  card: '#E5E5E5',
  cardPressed: '#FFFFFF',
  ink: '#1F2226', // thermal print: a blue-black, never pure black
  inkSoft: '#8A8A8A',
  date: '#767676',
  inkFaint: '#A8A8A8',
  icon: '#3C3C3C',
  slot: '#1E1E1E',
  slotSheen: '#3A3A3A',
  slit: '#8E8E8E',
  paper: '#FAF8F2', // thermal paper: a warm white
  paperShade: 'rgba(0,0,0,0.05)',
  dash: '#BDBDBD',
  grid: '#D9D9D9',
  gridFaint: '#F4F4F4',
  axis: '#3A3A3A',
  zone: '#7EE06E',
  tag: '#111111',
  tagInk: '#FFFFFF',
  menu: '#FFFFFF', // a solid surface: nothing reads through it
  shadow: '#000000',
  buttonDark: '#111111',
  buttonLight: '#FFFFFF',
  buttonInk: '#333333',
  led: '#7EE06E',
  develop: 'rgba(255,255,255,0.9)',
  indicator: 'rgba(0,0,0,0.28)',
} as const;

// JetBrains Mono (OFL, bundled in assets/), the face of the source, in four weights.
export const FONT = {
  regular: 'ReceiptPrinter-Mono',
  medium: 'ReceiptPrinter-MonoMedium',
  bold: 'ReceiptPrinter-MonoBold',
  heavy: 'ReceiptPrinter-MonoExtraBold',
} as const;
export const mono = (bold?: boolean) => (bold ? FONT.bold : FONT.regular);
// JetBrains Mono: capitals start (1020 - 730) / 1000 em below the top of a line box without lineHeight.
export const CAP_TOP = 0.29;

export const TYPE = {
  title: 27.5,
  subtitle: 12.5,
  pill: 16.5,
  cardDate: 12,
  cardKm: 16.5,
  menu: 12.5,
  rcTitle: 22,
  body: 13.3,
  head: 12.5,
  total: 19,
  small: 11.2,
  tiny: 10,
  chartNote: 8.6,
  button: 11.5,
} as const;

/**
 * Spacing scale (step 0, 20 Oct), derived from the source's measured gaps: 4 pt base; 12 = card radius and date
 * inset, 16 = screen gutter, 24 = the slot's inner inset. The source's own 13/14 pt gaps stay as measured; only
 * elements the upgrade added are placed on this scale.
 */
export const SPACE = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const;

export const GRID = {
  gutter: 16,
  gap: 14,
  cardW: 114,
  titleCap: 78.5,
  subtitleCap: 111.5,
  info: { cx: 372, cy: 88, r: 12 },
  pillTop: 144.7,
  pillH: 40.6,
  rowTop: 203,
  rowPitch: 158.3,
  cardH: 140.7,
  radius: 12,
  dateInset: 12,
  dateCap: 14.5,
  iconTop: 50,
  iconW: 46,
  iconH: 33,
  kmCapFromBottom: 29,
  // The long-press menu (frames 3–7), over the pressed card.
  menu: { x: 16, y: 203, w: 254, h: 93, radius: 18, rowH: 38, iconX: 22, textX: 52 },
} as const;

export const SLOT = {
  // A black stadium ring with a deep slit; the paper leaves the slit and lies over the lower lip.
  x0: 16, x1: 386, y0: 70.5, y1: 111, ring: 14.5,
  slitY0: 85.5, slitY1: 96.5,
  paperX0: 35, paperX1: 368,
  // The paper's top edge, just under the upper lip.
  paperTop: 86,
  shadowH: 16,
  // A status light on the right of the slot's upper lip.
  // 24 pt (SPACE.xl) in from the ring's right end, centred on the upper lip (70.5–85.5).
  led: { x: 386 - SPACE.xl, y: (70.5 + 85.5) / 2, r: 2.6, glow: 9 },
} as const;

/**
 * The printer is the Dynamic Island. At rest a black pill lies just inside the island (iPhone 18 Pro's island measures
 * 94.7 × 36.7 pt, 14 pt from the top, in the Simulator's recordings; older Pro islands are wider and hide it too), so it
 * is never seen apart from the island; to print it widens like a Live Activity
 * into the printer's mouth and the paper leaves its lower edge. Devices without an island get the same pill under
 * the status bar.
 */
export const ISLAND = {
  top: 14, w: 94, h: 36,
  // Without an island: the pill's top, under the status bar.
  noIslandGap: 6,
  // Open: the paper's width plus a 14 pt lip on each side, and a little taller so it reads as a mouth.
  openW: SLOT.paperX1 - SLOT.paperX0 + 28, openH: 46,
  // The paper's top edge is tucked this far up under the island's lower edge.
  tuck: 4,
  // The status light sits this far in from the open island's right end.
  ledInset: 22,
  color: '#000000',
} as const;

/** Receipt rows: cap top (screen pt at scroll 0), text, alignment, size, weight, colour. */
export type Row = { cap: number; text: string; align: 'left' | 'center' | 'right'; x?: number; size: number; bold?: boolean; color?: string };

const L = 60;
const R = 340;
const C = 201.5;

export const RECEIPT = {
  length: 1281,
  left: L,
  right: R,
  center: C,
  logo: { top: 153, w: 73, h: 44 },
  dashes: [363, 410, 505, 751, 947, 1181],
  rows: [
    { cap: 222.3, text: 'RUN RECEIPT', align: 'center', size: TYPE.rcTitle, bold: true },
    { cap: 272, text: 'RUN #0418', align: 'center', size: TYPE.body },
    { cap: 296.3, text: 'WED, JANUARY 28, 2026', align: 'center', size: TYPE.body },
    { cap: 321.3, text: 'RIVERSIDE LOOP – TEMPO', align: 'center', size: TYPE.body },
    { cap: 381.7, text: 'ITEM', align: 'left', x: L, size: TYPE.head, bold: true },
    { cap: 381.7, text: 'PACE', align: 'left', x: 172, size: TYPE.head, bold: true },
    { cap: 381.7, text: 'ZONE', align: 'left', x: 217, size: TYPE.head, bold: true },
    { cap: 381.7, text: 'TIME', align: 'left', x: 262, size: TYPE.head, bold: true },
    { cap: 381.7, text: 'HR', align: 'right', x: R, size: TYPE.head, bold: true },
    { cap: 435.3, text: '5 KM', align: 'left', x: L, size: TYPE.body },
    { cap: 435.3, text: '05\'28"', align: 'left', x: 163, size: TYPE.body },
    { cap: 435.3, text: '27:21', align: 'left', x: 258, size: TYPE.body },
    { cap: 435.3, text: '154', align: 'right', x: R, size: TYPE.body },
    { cap: 470.5, text: '1.21 KM', align: 'left', x: L, size: TYPE.body },
    { cap: 470.5, text: '05\'11"', align: 'left', x: 163, size: TYPE.body },
    { cap: 470.5, text: '06:17', align: 'left', x: 258, size: TYPE.body },
    { cap: 470.5, text: '168', align: 'right', x: R, size: TYPE.body },
    { cap: 558.7, text: 'PACE', align: 'left', x: L, size: TYPE.head, bold: true },
    { cap: 561, text: '05\'04"–05\'42"/KM | AVG 05\'25"', align: 'right', x: R, size: TYPE.chartNote, color: COLOR.inkSoft },
    { cap: 715, text: '0KM', align: 'left', x: L, size: TYPE.tiny, color: COLOR.inkSoft },
    { cap: 715, text: '6.2KM', align: 'right', x: R, size: TYPE.tiny, color: COLOR.inkSoft },
    { cap: 789, text: 'TOTAL:', align: 'left', x: L, size: TYPE.total, bold: true },
    { cap: 789, text: '6.21 KM', align: 'right', x: R, size: TYPE.total, bold: true },
    { cap: 827, text: 'DURATION', align: 'left', x: L, size: TYPE.body },
    { cap: 827, text: '00:33:38', align: 'right', x: R, size: TYPE.body },
    { cap: 851.7, text: 'AVG PACE', align: 'left', x: L, size: TYPE.body },
    { cap: 851.7, text: '05\'25"/KM', align: 'right', x: R, size: TYPE.body },
    { cap: 876.3, text: 'BEST 1K', align: 'left', x: L, size: TYPE.body },
    { cap: 876.3, text: '05\'09"/KM', align: 'right', x: R, size: TYPE.body },
    { cap: 901.3, text: 'AVG HR', align: 'left', x: L, size: TYPE.body },
    { cap: 901.3, text: '156 BPM', align: 'right', x: R, size: TYPE.body },
    { cap: 983, text: '*** DUPLICATE COPY ***', align: 'center', size: TYPE.body, bold: true },
    { cap: 1018.3, text: 'KEEP FOR YOUR RECORDS', align: 'center', size: TYPE.small, color: COLOR.inkSoft },
    { cap: 1210.3, text: 'PRINTED', align: 'center', size: TYPE.small, bold: true },
    { cap: 1223.7, text: 'WED 28 JAN 18:51', align: 'center', size: TYPE.small, bold: true },
    { cap: 1261.7, text: 'EVEN PACE, STRONG FINISH.', align: 'center', size: TYPE.small, bold: true },
    { cap: 1294.7, text: 'GENERATED LOCALLY VIA STRIDE OS.', align: 'center', size: TYPE.tiny, color: COLOR.inkFaint },
  ] as Row[],
  paragraph: {
    cap: 1053,
    pitch: 15.6,
    lines: [
      'Every run leaves a trace. Run Receipt',
      'turns it into something you can keep and',
      'share, capturing distance, pace, and',
      'effort as a simple receipt of movement –',
      'a small record of where you ran and how',
      'the moment felt. Learn more at',
      'receipt.strideos.run.',
    ],
  },
  // The FAST tag marks the finishing kick, after "1.21 KM" on the second row.
  fast: { x: 120, top: 466.5, w: 34, h: 17.7, radius: 3.5, size: 9.5 },
  zone: { x: 231.3, ys: [440.2, 475.3], r: 4.2 },
  chart: { top: 585, bottom: 705, base: 689.6, baseW: 2.4, columns: 6, dotted: 4 },
  // The torn edge: scallops pointing down, as on the source's bottom edge.
  edge: { period: 12.8, depth: 6 },
} as const;

export const BAR = {
  // Save / Tear off / Signature, 56 pt pills along the bottom (frame 030).
  top: 792,
  h: 56,
  x: 33.7,
  w: 106.3,
  gap: 13,
  icon: 18,
  iconCap: 10,
  labelCap: 37,
} as const;

/** This year's runs, newest first: weekday, date, kilometres, and a seed for the route drawn on the tile. */
export const CARDS = [
  { date: ['WED', '28 JAN'], km: '6.21 KM', seed: 3 }, { date: ['SUN', '25 JAN'], km: '12.40 KM', seed: 11 },
  { date: ['THU', '22 JAN'], km: '5.02 KM', seed: 5 }, { date: ['TUE', '20 JAN'], km: '8.15 KM', seed: 8 },
  { date: ['SAT', '17 JAN'], km: '10.33 KM', seed: 2 }, { date: ['WED', '14 JAN'], km: '6.18 KM', seed: 3 },
  { date: ['MON', '12 JAN'], km: '4.40 KM', seed: 14 }, { date: ['SAT', '10 JAN'], km: '9.87 KM', seed: 6 },
  { date: ['THU', '8 JAN'], km: '5.55 KM', seed: 9 }, { date: ['TUE', '6 JAN'], km: '7.20 KM', seed: 12 },
  { date: ['SUN', '4 JAN'], km: '11.06 KM', seed: 4 }, { date: ['FRI', '2 JAN'], km: '5.00 KM', seed: 7 },
  { date: ['WED', '31 DEC'], km: '6.42 KM', seed: 10 }, { date: ['MON', '29 DEC'], km: '8.90 KM', seed: 1 },
  { date: ['SAT', '27 DEC'], km: '3.25 KM', seed: 13 },
] as const;

/** The year in three figures across the top. */
export const PILLS = ['91.4 KM', '12 RUNS', '5\'29"/KM'] as const;

export const COPY = {
  title: 'RUN RECEIPT',
  subtitle: '2026 so far · tap a run to print it',
  long: 'LONG RECEIPT',
  short: 'SHORT RECEIPT',
  save: 'SAVE',
  tear: 'TEAR OFF',
  sign: 'SIGNATURE',
} as const;
