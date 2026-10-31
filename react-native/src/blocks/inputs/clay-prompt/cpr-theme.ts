// Clay Prompt — tokens and layout (402 × 874 pt design, 4 pt grid).

export const COLOR = {
  page: '#121018',
  surface: '#1E1B26',
  surfaceHi: '#28243326',
  line: '#2E2A3A',
  ink: '#F6F3FA',
  mute: '#9792A6',
  faint: '#5E596D',
  accent: '#FFB86B',
  accentInk: '#1E1206',
} as const;

export const FONT = { regular: 'CprInter-Regular', medium: 'CprInter-Medium', semibold: 'CprInter-SemiBold' } as const;

export const SPACE = { xs: 4, s: 8, m: 12, l: 16, xl: 24, xxl: 32 } as const;

/** The things the prompt knows how to sculpt, and the words that call them. */
export const KINDS = [
  { id: 'cat', words: ['cat', 'cats', 'kitten', 'kitty'], tint: '#FFA15A' },
  { id: 'sofa', words: ['sofa', 'couch', 'sofas'], tint: '#6FA4FF' },
  { id: 'moon', words: ['moon', 'crescent'], tint: '#FFD86A' },
  { id: 'plant', words: ['plant', 'plants', 'cactus', 'fern'], tint: '#73D883' },
] as const;
export type Kind = 0 | 1 | 2 | 3;

export function kindOfWord(word: string): Kind | -1 {
  const w = word.toLowerCase().replace(/[^a-z]/g, '');
  for (let i = 0; i < KINDS.length; i++) if ((KINDS[i].words as readonly string[]).includes(w)) return i as Kind;
  return -1;
}

export const PROMPT = 'a cat on a sofa under the moon';
export const SLOTS = 4;

export type Layout = {
  w: number;
  h: number;
  barTop: number;
  card: { x: number; y: number; w: number; h: number };
  captionTop: number;
  tokenY: number;
  tokenSize: number;
  tokenGap: number;
  composerTop: number;
  composerH: number;
};

export const COMPOSER_H = 120;

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const barTop = top + 8;
  const composerTop = h - bottom - 12 - COMPOSER_H;
  const tokenY = composerTop - 56;
  const cardY = barTop + 44 + 12;
  const cardW = w - 32;
  const cardH = Math.min(cardW * 1.18, tokenY - 60 - cardY);
  return {
    w,
    h,
    barTop,
    card: { x: 16, y: cardY, w: cardW, h: cardH },
    captionTop: cardY + cardH + 16,
    tokenY,
    tokenSize: 34,
    tokenGap: 88,
    composerTop,
    composerH: COMPOSER_H,
  };
}
