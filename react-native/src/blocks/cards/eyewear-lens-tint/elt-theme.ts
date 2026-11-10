// Eyewear Lens Tint — tokens and layout, measured from the reference (Pinterest pin
// 9499849195024471, the product screen of an eyewear shop). The pin's screen (375 × 667 pt, a 20 pt
// status bar, no home indicator) was rectified onto a 402 × 874 device (top inset 62); D holds those
// device points and makeLayout maps them onto other devices. The model (a generated portrait), the
// shop and the copy are invented; type is PT Sans, as in the pin.

export const COLOR = {
  page: '#E9EAEE',
  card: '#FFFFFF',
  ink: '#1C1C1E',
  body: '#5E5E64',
  mute: '#A2A2A8',
  cta: '#1E1E20',
  ctaInk: '#FFFFFF',
} as const;

export const FONT = { regular: 'EltPTSans-Regular', bold: 'EltPTSans-Bold' } as const;

export const COPY = {
  title: 'SUNGLASSES',
  price: '$220',
  rating: '4.5',
  reviews: '18 REVIEWS',
  body: 'Round titanium frames with lenses tinted by hand. Drag a tint onto a lens to try it on.',
  bag: 'ADD TO BAG',
  wish: 'ADD TO WISHLIST',
} as const;

/** the lens tints, the colour the light through them keeps (linear), and a swatch */
export type Tint = { name: string; keep: [number, number, number]; swatch: string; glow: [number, number, number] };
export const TINTS: Tint[] = [
  { name: 'Midnight', keep: [0.05, 0.07, 0.11], swatch: '#1B2433', glow: [0.25, 0.55, 0.6] },
  { name: 'Rose', keep: [0.75, 0.32, 0.38], swatch: '#D97C8A', glow: [1.0, 0.75, 0.8] },
  { name: 'Amber', keep: [0.72, 0.42, 0.1], swatch: '#D69A3A', glow: [1.0, 0.85, 0.5] },
  { name: 'Sea', keep: [0.12, 0.42, 0.4], swatch: '#3E8F8A', glow: [0.5, 0.95, 0.9] },
];
export const REST_TINT = 0;

/** the reference's positions, in device points (402 × 874, top inset 62) */
export const D = {
  menu: { x: 32, y: 90 },
  search: { x: 372, y: 90 },
  /** the portrait (1206 × 1800) shown 536 pt wide, so its glasses sit where the reference's do */
  photo: { x: -73.7, y: -126, w: 536, h: 800 },
  lenses: [
    { x: 130, y: 303, r: 52 },
    { x: 274.3, y: 303.2, r: 52 },
  ],
  /** the tints' droplets, down the right edge of the photo */
  drops: { x: 370, y0: 400, gap: 44, r: 13 },
  down: { x: 200, y: 562, r: 24 },
  card: { x: 20, y: 600, w: 362, h: 220 },
  title: { x: 56, y: 622, size: 25 },
  price: { right: 345, y: 622, size: 22 },
  stars: { x: 56, y: 649, size: 13 },
  body: { x: 56, y: 681, size: 12.5, line: 21, w: 290 },
  bag: { x: 56, y: 748, w: 122, h: 37 },
  wish: { right: 345, y: 768 },
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
