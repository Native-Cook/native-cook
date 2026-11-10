// Smoked Cloche Reveal — tokens and layout, measured from the reference (Pinterest pin
// 652318327267831262, a food app's dish detail). The pin's screen (375 × 811 pt, no status bar drawn)
// was rectified onto a 402 × 874 device (top inset 62, bottom 34); D holds those device points and
// makeLayout maps them onto other devices. Type is Outfit, close to the pin's geometric sans; the
// restaurant and its copy are invented.

export const COLOR = {
  top: '#D9D4CE',
  bottom: '#EDD7BF',
  glow: '#F3C9A0',
  ink: '#1D1B19',
  body: '#4A4541',
  mute: '#5E5853',
  heart: '#E3DCD5',
  cta: '#050505',
  ctaInk: '#FFFFFF',
} as const;

export const FONT = {
  regular: 'ScrOutfit-Regular',
  medium: 'ScrOutfit-Medium',
  semibold: 'ScrOutfit-SemiBold',
  bold: 'ScrOutfit-Bold',
} as const;

export const COPY = {
  name: 'Eggs Curry',
  time: '25 Mins',
  body: 'Eggs Curry with tomato and cucumbers, our chefs’ healthy, low-fat dish, smoked over applewood before it reaches you.',
  total: 'Total Price',
  price: '$ 15.00',
  cta: 'Add to Cart',
} as const;

/** the reference's positions, in device points (402 × 874, top inset 62) */
export const D = {
  back: { x: 37, y: 63 },
  more: { x: 364, y: 63 },
  bowl: { x: 200, y: 304, r: 206 },
  dome: { r: 188 },
  leaves: [
    { x: 18, y: 560, s: 64, rot: -20, img: 0 },
    { x: 380, y: 425, s: 58, rot: 35, img: 1 },
  ],
  name: { x: 33, y: 636, size: 28 },
  time: { right: 360, y: 637, size: 16.5 },
  body: { x: 33, y: 682, size: 14.2, line: 22.5, w: 330 },
  total: { x: 33, y: 780, size: 13.2 },
  price: { x: 33, y: 812, size: 20.5 },
  heart: { x: 200, y: 808, r: 23 },
  // the pin lets Add to Cart run off the phone; here it runs off the edge with its label and plus still on screen
  cta: { x: 236, y: 772, w: 190, h: 70, r: 26 },
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
