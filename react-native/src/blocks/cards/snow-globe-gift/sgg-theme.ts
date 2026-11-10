// Snow Globe Gift — tokens and layout (402 × 874 pt design, 4 pt grid).

export const COLOR = {
  page: '#F8F1EF',
  ink: '#2A1D1C',
  mute: '#8C7B78',
  hair: 'rgba(42,29,28,0.14)',
  gold: '#B98A3E',
  cta: '#2A1D1C',
  ctaInk: '#FCF8F6',
} as const;

export const FONT = {
  display: 'SggInstrumentSerif',
  regular: 'SggInter-Regular',
  medium: 'SggInter-Medium',
  semibold: 'SggInter-SemiBold',
} as const;

export const SPACE = { xs: 4, s: 8, m: 12, l: 16, xl: 24, xxl: 32 } as const;

export const COPY = {
  from: 'From Mira',
  title: 'A gift for you',
  note: '“Happy birthday! The coffee is on me.”',
  amount: '$50',
  amountNote: 'Kinto gift balance',
  open: 'Open Gift',
  add: 'Add $50 to Wallet',
} as const;

export const BAR_H = 44;
export const CTA_H = 56;

export type Layout = {
  w: number;
  h: number;
  barTop: number;
  heroX: number;
  heroY: number;
  heroS: number;
  amountY: number;
  fromTop: number;
  titleTop: number;
  noteTop: number;
  ctaTop: number;
};

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const barTop = top;
  const ctaTop = h - Math.max(bottom, 16) - SPACE.s - CTA_H;
  const noteTop = ctaTop - SPACE.xxl - 44;
  const titleTop = noteTop - SPACE.s - 42;
  const fromTop = titleTop - SPACE.xs - 18;
  const room0 = barTop + BAR_H + SPACE.l;
  const room = fromTop - SPACE.xl - room0;
  // the globe (radius 1) and its base reach from −1 to +1.35 radii
  const heroS = Math.min(w * 0.34, room / 2.45, 132);
  const heroY = room0 + (room - heroS * 2.35) / 2 + heroS * 1.0;
  return {
    w,
    h,
    barTop,
    heroX: w / 2,
    heroY,
    heroS,
    amountY: heroY - heroS * 0.64,
    fromTop,
    titleTop,
    noteTop,
    ctaTop,
  };
}
