// Perfume Ink Bloom — tokens and layout, measured from the reference (Pinterest pin
// 656470083165924831, a perfume's detail page). The pin's screen (375 × 812 pt, a 44 pt status bar, a
// 34 pt home indicator) was rectified onto a 402 × 874 device (top inset 62, bottom 34); D holds those
// device points and makeLayout maps them onto other devices. The perfume, its house and the copy are
// invented; type is Nunito, as in the pin.

export const COLOR = {
  page: '#FFFFFF',
  ink: '#0B0B0F',
  label: '#4B4B55',
  body: '#8C8C96',
  star: '#FF9C1A',
  starOff: '#E6E6EC',
  cta: '#0A5CFF',
  ctaInk: '#FFFFFF',
  heart: '#8F96BC',
  badge: '#0A5CFF',
  note: '#1D63F2',
} as const;

export const FONT = {
  regular: 'PibNunito-Regular',
  semibold: 'PibNunito-SemiBold',
  bold: 'PibNunito-Bold',
} as const;

export const COPY = {
  name: 'Azure Tide Bloom',
  kind: 'Eau de Parfum',
  reviews: 'Reviews',
  price: 'Price',
  amount: '$148',
  body: 'Bottled by a small house on the coast: bright citrus over a soft vanilla heart, worn close and lasting into the evening.',
  buy: 'Buy now',
  /** the notes the mist condenses into */
  notes: ['bergamot', 'sea salt', 'vanilla'],
} as const;

/** the reference's positions, in device points (402 × 874, top inset 62) */
export const D = {
  back: { x: 39, y: 83 },
  bag: { x: 358, y: 85 },
  /** the bottle's body: its centre and radius; the bottle leans this far (rad, clockwise) */
  bottle: { x: 163, y: 300, r: 100, lean: 0.375 },
  notes: { x: 330, y: 322, size: 21, line: 33 },
  name: { x: 32, y: 476, size: 30 },
  kind: { x: 32, y: 513, size: 19.5 },
  reviews: { x: 32, y: 568, size: 16 },
  stars: { x: 32, y: 603, size: 21, gap: 28 },
  price: { x: 288, y: 567, size: 16 },
  amount: { x: 288, y: 603, size: 22 },
  body: { x: 32, y: 657, size: 13.2, line: 25.5, w: 312 },
  heart: { x: 31, y: 754, w: 77, h: 76, r: 20 },
  buy: { x: 135, y: 754, w: 235, h: 76, r: 22 },
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
