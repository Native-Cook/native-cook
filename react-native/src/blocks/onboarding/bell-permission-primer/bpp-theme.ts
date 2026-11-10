// Bell Permission Primer — tokens and layout, measured from the reference (Pinterest pin
// 95771929563137692, a "Notifications" permission screen). The pin's screen (375 × 812 pt, a 44 pt
// status bar) was rectified onto a 402 × 874 device (top inset 62, bottom 34); D holds those device
// points and makeLayout maps them onto other devices. Type is Barlow, as in the pin; the app,
// Mosswell, is invented.

export const COLOR = {
  page: '#FDFDFE',
  top: '#F6F9FD',
  ink: '#25323B',
  body: '#545C68',
  blue: '#489BFF',
  skip: '#4A8BDF',
  deco: '#BFDCFE',
  skyA: '#6CC2FC',
  skyB: '#4C86FA',
  groundA: '#4A93FD',
  groundB: '#3550FB',
} as const;

export const FONT = {
  title: 'BppBarlowSemiCondensed-Bold',
  regular: 'BppBarlow-Regular',
  medium: 'BppBarlow-Medium',
  semibold: 'BppBarlow-SemiBold',
  bold: 'BppBarlow-Bold',
} as const;

export const COPY = {
  title: 'Notifications',
  body: 'One quiet reminder from Mosswell on the mornings your plants are thirsty.',
  allow: 'ALLOW',
  skip: 'SKIP',
  app: 'MOSSWELL',
  banner: 'Time to water your monstera',
  bannerSub: 'Its soil is dry 2 cm down. A glass of water will do.',
} as const;

/** the reference's positions, in device points (402 × 874, top inset 62) */
export const D = {
  top: { h: 190 },
  disc: { x: 200.5, y: 304, r: 114, horizon: 296 },
  bell: { pivotY: 206, unit: 116 },
  title: { y: 480, size: 43 },
  body: { y: 530, size: 22, line: 33, w: 312 },
  allow: { x: 144, y: 656, w: 114, h: 44, size: 17 },
  skip: { y: 769, size: 17 },
  island: { y: 14, w: 125, h: 36.3 },
  banner: { x: 10, h: 92, r: 34 },
  /** the pin's faint shapes round the page: x, y, size, kind (0 bell, 1 blob, 2 dot) */
  deco: [
    [34, 91, 14, 1],
    [367, 88, 12, 0],
    [218, 148, 12, 0],
    [122, 110, 6, 2],
    [327, 150, 7, 2],
    [25, 170, 5, 2],
    [110, 232, 10, 1],
    [330, 258, 13, 1],
    [32, 293, 7, 2],
    [346, 344, 6, 2],
    [301, 511, 13, 1],
    [370, 583, 9, 1],
    [323, 724, 11, 1],
    [180, 635, 6, 2],
    [70, 740, 5, 2],
  ],
} as const;

export type Layout = { w: number; h: number; sx: number; k: number; Y: (y: number) => number };

export function makeLayout(w: number, h: number, safeTop: number, safeBottom: number): Layout {
  // a phone without a notch (a 20 pt status bar) or without a home indicator still keeps the reference's
  // room at its edges, so the header clears the status bar and the bottom row stays off the edge
  const top = Math.max(safeTop, 44);
  const bottom = Math.max(safeBottom, 20);
  const sx = w / 402;
  const k = (h - top - bottom) / (874 - 62 - 34);
  return { w, h, sx, k, Y: (y: number) => (y < 62 ? (y * top) / 62 : top + (y - 62) * k) };
}
