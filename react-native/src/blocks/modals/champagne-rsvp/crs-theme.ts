// Champagne RSVP — tokens and layout (402 × 874 pt design, 4 pt grid).

export const COLOR = {
  page: '#FAF3F1',
  ink: '#2A1F1D',
  mute: '#8F7F7A',
  gold: '#B4893F',
  hair: 'rgba(42,31,29,0.14)',
  cta: '#2A1F1D',
  ctaInk: '#FCF8F6',
} as const;

export const FONT = {
  display: 'CrsInstrumentSerif',
  italic: 'CrsInstrumentSerif-Italic',
  regular: 'CrsInter-Regular',
  medium: 'CrsInter-Medium',
  semibold: 'CrsInter-SemiBold',
} as const;

export const SPACE = { xs: 4, s: 8, m: 12, l: 16, xl: 24, xxl: 32 } as const;

export const COPY = {
  venue: 'THE GLASSHOUSE · LISBON',
  names: 'Clara & Theo',
  date: 'Saturday 14 June · 6 pm',
  idle: { line: 'Will you raise a glass with us?', cta: 'Joyfully Accept', link: 'Regretfully Decline' },
  done: { line: 'See you there, Ana. Dancing till late.', cta: 'Add to Calendar', link: 'Change my reply' },
} as const;

export const CTA_H = 56;
export const LINK_H = 44;

export type Layout = {
  w: number;
  h: number;
  venueTop: number;
  namesTop: number;
  dateTop: number;
  heroS: number;
  feetY: number;
  feetX: [number, number];
  lineTop: number;
  ctaTop: number;
  linkTop: number;
};

/** the lean that brings the two rims together, for feet `gap` units apart (rims 0.42 units out) */
export function clinkLean(gap: number) {
  'worklet';
  return Math.asin(Math.min(0.9, (gap - 0.84) / (2 * 2.62)));
}
export const FEET_GAP = 1.7;

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const linkTop = h - Math.max(bottom, 16) - SPACE.xs - LINK_H;
  const ctaTop = linkTop - SPACE.xs - CTA_H;
  const lineTop = ctaTop - SPACE.xxl - 22;
  const venueTop = top + SPACE.xxl;
  const namesTop = venueTop + 16 + SPACE.s;
  const dateTop = namesTop + 48 + SPACE.xs;
  const room0 = dateTop + 22 + SPACE.xl;
  const room1 = lineTop - SPACE.xl;
  const room = room1 - room0;
  const heroS = Math.min(w * 0.26, room / 3.0, 104);
  const feetY = room1 - SPACE.s;
  const cx = w / 2;
  return { w, h, venueTop, namesTop, dateTop, heroS, feetY, feetX: [cx - (FEET_GAP / 2) * heroS, cx + (FEET_GAP / 2) * heroS], lineTop, ctaTop, linkTop };
}
