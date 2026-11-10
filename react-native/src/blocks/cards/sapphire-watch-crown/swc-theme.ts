// Sapphire Watch Crown — tokens and layout, measured from the reference (Pinterest pin
// 275071489737550382, the store screen of a watch shop). The pin's screen (375 × 812 pt, a 44 pt
// status bar, a 34 pt home indicator) was rectified onto a 402 × 874 device (top inset 62, bottom 34);
// D holds those device points and makeLayout maps them onto other devices. The maker, its watch and
// the copy are invented; type is Poppins, as in the pin.

export const COLOR = {
  page: '#050505',
  well: '#1C1C1E',
  ink: '#FFFFFF',
  mute: 'rgba(255,255,255,0.45)',
  dim: 'rgba(255,255,255,0.32)',
  line: 'rgba(255,255,255,0.1)',
  cta: '#FFC56E',
  ctaInk: '#111111',
  ring: '#1A1A1C',
  disc: '#1D1D20',
} as const;

export const FONT = {
  regular: 'SwcPoppins-Regular',
  medium: 'SwcPoppins-Medium',
  semibold: 'SwcPoppins-SemiBold',
} as const;

export const COPY = {
  title: 'Store Online',
  sub: 'Easy Shopping',
  name: 'Maison Aurel',
  made: '(Swiss Made)',
  rating: '7.9/10',
  color: 'Color',
  sizing: 'Sizing',
  price: '$ 450.23',
  buy: 'Buy Online',
} as const;

/** the case metals, in the swatches' order: dark, rose gold, yellow gold, white */
export const METALS = [
  { name: 'Black', swatch: '#2E2E31' },
  { name: 'Rose gold', swatch: '#B9977D' },
  { name: 'Yellow gold', swatch: '#E8B33D' },
  { name: 'White gold', swatch: '#F4F4F2' },
] as const;
export const REST_METAL = 1;
export const SIZES = ['11.5', '12.3', '13.6', '14.79'] as const;

/** the reference's positions, in device points (402 × 874, top inset 62) */
export const D = {
  back: { x: 26, y: 62, s: 68 },
  cart: { x: 307, y: 62, s: 68 },
  title: { x: 118, y: 77, size: 17 },
  sub: { x: 118, y: 112, size: 12 },
  /** the watch's centre and the case's radius */
  watch: { x: 195, y: 345, r: 84 },
  rings: [175, 146, 128],
  name: { x: 26, y: 580, size: 19 },
  rating: { right: 376, y: 580, size: 17 },
  divider: 612,
  labels: { y: 640, color: 26, sizing: 228, size: 13 },
  swatches: { y: 683, xs: [35, 73, 112, 145], r: 9 },
  sizes: { y: 683, xs: [236, 273, 311, 354] },
  bar: { x: 26, y: 768, w: 350, h: 72, r: 30, buyX: 175 },
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
