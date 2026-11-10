// Serum Dropper Routine — tokens and layout, measured from the reference (Pinterest pin
// 1030902171247987141, the daily routine screen of a skincare app). The pin's screen (393 × 852 pt, a
// 54 pt status bar, a 34 pt home indicator) was rectified onto a 402 × 874 device (top inset 62, bottom
// 34); D holds those device points and makeLayout maps them onto other devices. The products (photos
// generated for the block), their house and the copy are invented; type is Poppins, as in the pin.

export const COLOR = {
  page: '#FFFFFF',
  top: '#F6F6F7',
  tile: '#FFFFFF',
  ink: '#151518',
  mute: '#B3B3B8',
  blue: '#E7EDF8',
  peach: '#FBEDE3',
  lilac: '#EEEAF8',
  oil: '#E9A23B',
} as const;

export const FONT = { regular: 'SdrPoppins-Regular', medium: 'SdrPoppins-Medium', semibold: 'SdrPoppins-SemiBold' } as const;

export const DAYS = [
  { n: '5', d: 'Mon' },
  { n: '6', d: 'Tue' },
  { n: '7', d: 'Wed' },
  { n: '8', d: 'Thu' },
  { n: '9', d: 'Fri' },
] as const;

export const STEPS = [
  { name: '100% Organic\nFruit Oil', step: 'Step 1', bg: COLOR.blue, done: true },
  { name: 'Mineral UV\nFilters SPF', step: 'Step 2', bg: COLOR.peach, done: true },
  { name: 'Niacinamide\n10% + Zinc 1%', step: 'Step 3', bg: COLOR.lilac, done: false },
  { name: 'Hyaluronic\nAcid Powder', step: 'Step 4', bg: COLOR.peach, done: false },
] as const;

/** the reference's positions, in device points (402 × 874, top inset 62) */
export const D = {
  back: { x: 46, y: 98, s: 44 },
  more: { x: 352, y: 98, s: 44 },
  title: { x: 80, y: 100, size: 16 },
  topBottom: 270,
  days: { y: 150, h: 100, xs: [25, 105, 185, 264, 345], w: 75, n: 185, d: 212, sel: 1 },
  cards: [
    { x: 26, y: 308, w: 165, h: 210 },
    { x: 211, y: 378, w: 164, h: 210 },
    { x: 26, y: 610, w: 165, h: 200 },
    { x: 211, y: 680, w: 164, h: 200 },
  ],
  labels: [
    { x: 26, y: 538 },
    { x: 210, y: 314 },
    { x: 210, y: 617 },
    { x: 26, y: 840 },
  ],
  check: { dx: 134, dy: 32, r: 15 },
  /** the dropper bottle in card 1: the bottle image's centre x, top and height; how far the dropper rises
   * to clear the neck, and where its tip is carried to; the card's floor, where the oil pools */
  bottle: { x: 110, top: 384, h: 124 },
  rise: 100,
  lift: { x: 165, tipY: 412 },
  floor: 504,
  products: { tube: { x: 293, y: 483, h: 150 }, jar: { x: 108, y: 712, w: 122 }, white: { x: 293, y: 782, h: 140 } },
  nav: { y: 780, h: 94, xs: [66, 122, 276, 326], fab: { x: 200, y: 795, r: 36 } },
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
