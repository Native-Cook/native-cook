// Pearl Shell Premium — tokens and layout, measured from the reference (Pinterest pin
// 636414991126737386, a "gift premium" sheet). The pin's screen (390 × 844 pt, with a status bar) was
// rectified onto a 402 × 874 device (top inset 62, bottom 34); D holds those device points and
// makeLayout maps them onto other devices. Type is the system font, as in the pin.

export const COLOR = {
  page: '#000000',
  card: '#1C1C1E',
  sep: '#38383A',
  ink: '#FFFFFF',
  mute: '#ABABAF',
  link: '#5A8CF9',
  radio: '#8E8E93',
  accent: '#8F7AF4',
  panel: '#1F1F1F',
  ctaA: '#817EFF',
  ctaB: '#D66DD6',
} as const;

export const BRAND = 'Nacre';
export const FRIEND = 'Yasmine';

export const PLANS = [
  { name: '1 Year', badge: '-39%', was: '$59.99', price: '$35.99', month: '$2.99/month' },
  { name: '6 Months', badge: '-33%', was: '$29.99', price: '$19.99', month: '$3.33/month' },
  { name: '3 Months', badge: '', was: '', price: '$14.97', month: '$4.99/month' },
] as const;

export const FEATURES = [
  { title: 'Upgraded Stories', desc: 'Priority order, stealth mode, permanent view history and more.', color: '#F47A33' },
  { title: 'Doubled Limits', desc: 'Up to 1000 channels, 20 folders, 10 pins, 20 public links, 4 accounts and more.', color: '#E1694B' },
  { title: 'Voice-to-Text Conversion', desc: 'Ability to read the transcript of any incoming voice message.', color: '#E05A6B' },
] as const;

/** the reference's positions, in device points (402 × 874, top inset 62) */
export const D = {
  cancel: { x: 18, y: 104 },
  orb: { x: 201.5, y: 162.5, r: 55 },
  title: { y: 246.5 },
  sub: { y: 288.5 },
  plans: { x: 17, y: 344, w: 369, h: 168, r: 13, rows: [375, 436, 487], sepY: [405, 466], textX: 84, radioX: 50 },
  feats: { x: 17, y: 537, w: 369, r: 13, rows: [568, 647, 726], sepY: [615, 694], icon: 33 },
  panel: { y: 772 },
  cta: { x: 17, y: 785, w: 369, h: 52, r: 12 },
} as const;

export type Layout = { w: number; h: number; sx: number; k: number; Y: (y: number) => number };

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const sx = w / 402;
  const k = (h - top - bottom) / (874 - 62 - 34);
  return { w, h, sx, k, Y: (y: number) => (y < 62 ? (y * top) / 62 : top + (y - 62) * k) };
}
