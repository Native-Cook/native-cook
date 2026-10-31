// Gem Cut Paywall — tokens and layout (402 × 874 pt design, 4 pt grid).

export const COLOR = {
  page: '#07060C',
  surface: '#15131F',
  line: '#262236',
  ink: '#F5F3FA',
  mute: '#8F8AA3',
  thumb: '#E9E4FF',
  thumbInk: '#140F26',
  cta: '#FFFFFF',
  ctaInk: '#0B0914',
} as const;

export const FONT = { regular: 'GcpInter-Regular', medium: 'GcpInter-Medium', semibold: 'GcpInter-SemiBold' } as const;

export const SPACE = { xs: 4, s: 8, m: 12, l: 16, xl: 24, xxl: 32 } as const;

export type Tier = { id: 'free' | 'plus' | 'pro'; name: string; price: string; cta: string; note: string };
export const TIERS: Tier[] = [
  { id: 'free', name: 'Free', price: '0.00', cta: 'Continue with Free', note: 'Five edits a day, forever.' },
  { id: 'plus', name: 'Plus', price: '4.99', cta: 'Start 7-day free trial', note: 'Then $4.99 a month. Cancel anytime.' },
  { id: 'pro', name: 'Pro', price: '9.99', cta: 'Start 7-day free trial', note: 'Then $9.99 a month. Cancel anytime.' },
];
/** What each plan unlocks, and from which plan (0 Free, 1 Plus, 2 Pro). */
export const FEATURES = [
  { label: 'Unlimited edits', from: 1 },
  { label: '4K and RAW export', from: 1 },
  { label: 'AI relight and retouch', from: 2 },
] as const;

export type Layout = {
  w: number;
  h: number;
  barTop: number;
  gemX: number;
  gemY: number;
  gemR: number; // girdle radius, pt
  floorY: number;
  titleTop: number;
  priceTop: number;
  planTop: number;
  planX: number;
  planW: number;
  featTop: number;
  noteTop: number;
  ctaTop: number;
};

export const PLAN_H = 48;
export const ROW = 24;
export const CTA_H = 56;

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const ctaTop = h - bottom - 8 - CTA_H;
  const noteTop = ctaTop - 12 - 18;
  const featTop = noteTop - 24 - (3 * ROW + 2 * 12);
  const planTop = featTop - 24 - PLAN_H;
  const priceTop = planTop - 24 - 22;
  const titleTop = priceTop - 4 - 34;
  const barTop = top + 8;
  const gemR = Math.min(w * 0.36, 160);
  // the gem and its caustic fill the room between the bar and the title
  const room0 = barTop + 44;
  const gemY = room0 + (titleTop - room0) * 0.42;
  return {
    w,
    h,
    barTop,
    gemX: w / 2,
    gemY,
    gemR,
    floorY: gemY + gemR * 0.86,
    titleTop,
    priceTop,
    planTop,
    planX: 16,
    planW: w - 32,
    featTop,
    noteTop,
    ctaTop,
  };
}
