// Sunrise Cocktail Pour — tokens and layout (402 × 874 pt design, 4 pt grid). A dark bar page.

export const COLOR = {
  page: '#100D0B',
  ink: '#F5EEE4',
  mute: '#9A8E80',
  brass: '#C9A46A',
  line: 'rgba(245,238,228,0.14)',
  cta: '#F5EEE4',
  ctaInk: '#14100C',
} as const;

export const FONT = {
  display: 'ScpInstrumentSerif',
  regular: 'ScpInter-Regular',
  medium: 'ScpInter-Medium',
  semibold: 'ScpInter-SemiBold',
} as const;

export const SPACE = { xs: 4, s: 8, m: 12, l: 16, xl: 24, xxl: 32 } as const;

export const STEPS = [
  { cta: 'Pour Grenadine', note: 'Pour slowly down the side. It sinks.' },
  { cta: 'Stir', note: 'Or leave it: the sunrise is the point.' },
  { cta: 'Serve', note: 'Garnish with an orange wheel.' },
] as const;

export const COPY = { eyebrow: 'NO. 07 · HOUSE CLASSIC', name: 'Tequila Sunrise', recipe: 'Blanco tequila, fresh orange, grenadine', price: '$14' } as const;

export const BAR_H = 44;
export const CTA_H = 56;

export type Layout = {
  w: number;
  h: number;
  barTop: number;
  heroX: number;
  heroY: number;
  heroS: number;
  eyebrowTop: number;
  nameTop: number;
  recipeTop: number;
  noteTop: number;
  ctaTop: number;
};

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const barTop = top;
  const ctaTop = h - Math.max(bottom, 16) - SPACE.s - CTA_H;
  const noteTop = ctaTop - SPACE.l - 20;
  const recipeTop = noteTop - SPACE.xl - 20;
  const nameTop = recipeTop - SPACE.xs - 44;
  const eyebrowTop = nameTop - SPACE.xs - 16;
  const room0 = barTop + BAR_H + SPACE.xl;
  const room = eyebrowTop - SPACE.xl - room0;
  // the glass is 2.3 units tall and the stream falls 1.5 units above its rim
  const heroS = Math.min(w * 0.3, room / 2.75, 118);
  const heroY = room0 + room - heroS * 1.32;
  return { w, h, barTop, heroX: w / 2, heroY, heroS, eyebrowTop, nameTop, recipeTop, noteTop, ctaTop };
}
