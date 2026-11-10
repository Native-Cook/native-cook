// Gold Emblem Membership — tokens and layout, measured from the reference (Pinterest pin
// 584412489138772843, a bank's member page). The pin's screen (375 × 812 pt, a 44 pt status bar, a
// 34 pt home indicator) was rectified onto a 402 × 874 device (top inset 62, bottom 34); D holds those
// device points and makeLayout maps them onto other devices. The bank, its tiers' figures and the
// copy are invented; type is Outfit, close to the pin's geometric sans.

export const COLOR = {
  dark: '#26272B',
  strip: '#37383D',
  white: '#FFFFFF',
  ink: '#1D1D1F',
  label: '#333336',
  mute: '#9A9A9E',
  gold: '#D4A866',
  statLabel: 'rgba(255,255,255,0.82)',
} as const;

export const FONT = {
  regular: 'GemOutfit-Regular',
  medium: 'GemOutfit-Medium',
  semibold: 'GemOutfit-SemiBold',
  bold: 'GemOutfit-Bold',
} as const;

/** the tiers a card can be cast in, in order */
export type Tier = { name: string; range: string; next: string; ink: string; sub: string; coupon: string; sliver: string };
export const TIERS: Tier[] = [
  { name: 'Gold member', range: '100,000 < assets < 300,000', next: 'Platinum is $80,889.00 away', ink: '#5A4426', sub: '#6E5634', coupon: '1', sliver: '#B8B9BC' },
  { name: 'Platinum member', range: '300,000 < assets < 800,000', next: 'Black is $412,560.00 away', ink: '#33414A', sub: '#4E5D66', coupon: '1.5', sliver: '#C9B08A' },
  { name: 'Black member', range: 'assets > 800,000', next: 'The highest tier', ink: '#F0CC86', sub: '#C9AE7C', coupon: '2', sliver: '#9FB0B8' },
];

export const STATS = [
  { value: '81.57', label: 'Withdraw', x: 68 },
  { value: '869.23', label: 'Interest expense', x: 205 },
  { value: '559.33', label: 'Reward', x: 333 },
] as const;

export const PRIVILEGES = [
  { name: 'Gift package', value: '$60', x: 66, icon: 0 },
  { name: 'Welfare day', value: '$150', x: 200, icon: 1 },
  { name: 'Redeem', value: '7.6%', x: 333, icon: 2 },
] as const;

/** the reference's positions, in device points (402 × 874, top inset 62) */
export const D = {
  card: { x: 17, y: 103, w: 367, h: 185, r: 10 },
  name: { x: 50, y: 140, size: 23 },
  range: { x: 50, y: 169, size: 12.5 },
  next: { x: 50, y: 254, size: 11.5 },
  /** the medal on the card, in card points */
  medal: { x: 313, y: 62, r: 24 },
  stats: { value: 333, label: 356 },
  coupon: { x: 17, y: 400, w: 367, h: 83, ticket: 70, pct: 112, label: 162, cy: 445 },
  receive: { x: 282, y: 425, w: 85, h: 33 },
  white: 483,
  privilege: { y: 529, icons: 580, names: 618, values: 641 },
  benefits: { y: 700, x: 17, box: { y: 735, h: 105 } },
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
