// Message Bottle Share — tokens and layout (402 × 874 pt design, 4 pt grid).

export const COLOR = {
  ink: '#2A2238',
  mute: 'rgba(42,34,56,0.62)',
  hair: 'rgba(42,34,56,0.14)',
  cta: '#2A2238',
  ctaInk: '#FBF4EE',
  onSea: '#FFFFFF',
} as const;

export const FONT = {
  display: 'MbsInstrumentSerif',
  regular: 'MbsInter-Regular',
  medium: 'MbsInter-Medium',
  semibold: 'MbsInter-SemiBold',
} as const;

export const SPACE = { xs: 4, s: 8, m: 12, l: 16, xl: 24, xxl: 32 } as const;

export const COPY = {
  title: 'Cast your letter',
  sub: 'To Maya · 214 words · written at the lake',
  sent: 'On its way',
  sentSub: 'Maya will find it at dawn.',
  cta: 'Cast Off',
  hint: 'or throw the bottle',
  again: 'Write Another',
} as const;

export const BAR_H = 44;
export const CTA_H = 56;

export type Layout = {
  w: number;
  h: number;
  top: number;
  titleTop: number;
  subTop: number;
  horizon: number;
  homeX: number;
  homeY: number;
  S: number;
  landX: number;
  landY: number;
  ctaTop: number;
  hintTop: number;
};

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const hintTop = h - Math.max(bottom, 16) - 20;
  const ctaTop = hintTop - SPACE.s - CTA_H;
  const titleTop = top + BAR_H + SPACE.s;
  const subTop = titleTop + 48;
  const horizon = h * 0.42;
  const S = w * 0.3;
  return { w, h, top, titleTop, subTop, horizon, homeX: w * 0.47, homeY: ctaTop - SPACE.xxl - S * 0.55, S, landX: w * 0.66, landY: horizon + 18, ctaTop, hintTop };
}
