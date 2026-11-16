import type { ExtrusionPage } from './ers-types';
export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;
// Measurement row ids live in .work/extrusion-recap-story/design.md.
export const ref = {
  color: {
    ink: '#F4F83C',
    lightInk: '#FFF3A0',
    page: '#080407',
    rim: '#FFF5F7',
    caption: '#F7F1B2',
    peekInk: '#14070F',
    peekMute: 'rgba(20,7,15,0.62)',
  },
  type: { display: 'ERSOnestBlack', body: 'ERSOnest', size: 54, line: 51, tracking: -2.1, caption: 15, brand: 22, count: 14 },
  light: { az: 35, el: 42, intensity: 1 },
  radius: { card: 28 },
  space: { edge: 20, peek: 48, gap: 8 },
  hero: { x: 198, y: 495, width: 360, height: 360, depth: 610, bevel: 1.5, rays: 9 },
} as const;
export const sys = { surface: { page: ref.color.page }, ink: { primary: ref.color.ink, secondary: ref.color.caption } } as const;
export const comp = { header: { y: 64, h: 44 }, title: { y: 136, w: 370 }, caption: { y: 258 }, card: { radius: 28, peek: 48 } } as const;
export const COPY = {
  brand: 'Lilt',
  next: 'Next page',
  previous: 'Previous page',
  close: 'Close recap',
  sound: 'Sound off',
  upNext: 'Up next',
  replay: 'Replay',
} as const;
export const GLYPHS = {
  note: 'M530 455 L486 627 C469 689 419 715 382 690 L336 641 L308 715 C330 770 290 842 232 873 C152 924 65 901 70 800 C39 731 74 682 131 674 C167 650 218 645 254 661 L277 698 L143 577 L155 510 L335 539 C364 523 368 499 356 480 Z',
  heart: 'M128 236 C110 218 12 150 12 83 C12 18 90 0 128 57 C166 0 244 18 244 83 C244 150 146 218 128 236Z',
  bolt: 'M138 9 L236 9 L161 96 L241 96 L60 247 L99 143 L24 143 Z',
} as const;
export const RECAP: ExtrusionPage[] = [
  {
    glyph: GLYPHS.note,
    field: ['#080205', '#F26AB4'],
    face: '#D8004A',
    direction: [0.43, 0.9],
    headline: 'That very\nmusic',
    caption: 'Tracks that captured your year like nothing else',
  },
  {
    glyph: GLYPHS.heart,
    field: ['#D51B39', '#F34455'],
    face: '#D8183B',
    direction: [-0.16, 0.98],
    headline: 'new discoveries',
    caption: 'Each time your heart said yes, a track found its way in.',
    stat: { value: 264 },
  },
  {
    glyph: GLYPHS.bolt,
    field: ['#020B05', '#3DF03C'],
    face: '#32DF31',
    direction: [0.52, 0.85],
    headline: 'Remember\nthat moment?',
    caption: 'You found new music. And a little more of yourself.',
  },
];
export function rgb(hex: string): [number, number, number] {
  'worklet';
  const n = parseInt(hex.replace('#', ''), 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}
export function makeLayout(w: number, h: number, top: number, bottom: number) {
  const s = w / REF.w;
  const cardBottom = h - Math.max(bottom, REF.safeBottom * s) - ref.space.peek * s;
  const titleY = top + (comp.title.y - REF.safeTop) * s;
  return {
    s,
    titleY,
    headerY: top,
    cardBottom,
    heroY: Math.min(h * (ref.hero.y / REF.h), cardBottom - 145 * s),
    captionY: titleY + 122 * s,
    /** Visible part of the next card above the home indicator. */
    peekRow: Math.max(h - Math.max(bottom, REF.safeBottom * s) - cardBottom - ref.space.gap * s, 32 * s),
  };
}
