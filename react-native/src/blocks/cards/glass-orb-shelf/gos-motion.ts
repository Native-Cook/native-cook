// Glass Orb Shelf — tokens, layout and the loop. Helpers first: worklets capture what is above them.

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
/** ease-in-out with a small settle past the target, like a page that snaps */
export function snap(x: number) {
  'worklet';
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  const e = easeInOut(Math.min(1, x / 0.82));
  const over = x > 0.82 ? Math.sin(((x - 0.82) / 0.18) * Math.PI) * 0.025 : 0;
  return e + over;
}

/* ---------------------------------------------------------------- tokens */

// A dark shelf, so the orb is the brightest thing in a feed (the reference's page was light grey).
export const COLOR = {
  page: '#0D0E11',
  card: '#17181D',
  cardEdge: 'rgba(255,255,255,0.06)',
  ink: '#F2F3F5',
  body: '#A4A7AF',
  mute: '#6C707A',
  pill: '#F2F3F5',
  pillInk: '#0D0E11',
} as const;
export const PAGE_RGB: [number, number, number] = [0x0d / 255, 0x0e / 255, 0x11 / 255];

export const FONT = { regular: 'GosInter-Regular', semibold: 'GosInter-SemiBold', bold: 'GosInter-Bold' } as const;

export type App = { first: string; rest: string; tint: string; body: string };
/** Invented apps. The first word of each title takes the colour of its scene. */
export const APPS: App[] = [
  { first: 'Non-fiction', rest: 'summaries', tint: '#5DD39C', body: 'Detailed outlines of popular non-fiction books, to learn a topic in ten minutes.' },
  { first: 'Travel', rest: 'bucket list', tint: '#78B4FF', body: 'Keep track of places you’ve been and dream of visiting, on beautiful cards.' },
  { first: 'Film photo', rest: 'studio', tint: '#62D2DF', body: 'Turn your photos into soft film shots with grain, light leaks and classic stocks.' },
  { first: 'Receipt to', rest: 'recipe', tint: '#FF8A73', body: 'Snap your grocery bill and get three recipes made from what you bought.' },
];

/* ---------------------------------------------------------------- layout (pt) */

export type Layout = {
  w: number;
  headerTop: number;
  orbX: number;
  orbY: number;
  orbR: number;
  cardLeft: number;
  cardTop: number;
  cardW: number;
  cardH: number;
  radius: number;
  step: number;
  dotsTop: number;
};

// The Featured orb is the picture: 84 % of the width (the reference's balls were 44 %), fixed at the
// top while the cards page under it and its scene changes inside.
export function makeLayout(w: number, top: number): Layout {
  const headerTop = top + 12;
  const orbR = Math.min(172, w * 0.42);
  const orbY = headerTop + 44 + 24 + orbR;
  const cardW = Math.min(300, w - 102);
  const cardTop = orbY + orbR + 44;
  const cardH = 196;
  return {
    w,
    headerTop,
    orbX: w / 2,
    orbY,
    orbR,
    cardLeft: (w - cardW) / 2,
    cardTop,
    cardW,
    cardH,
    radius: 26,
    step: cardW + 14,
    dotsTop: cardTop + cardH + 18,
  };
}

/* ---------------------------------------------------------------- loop */

export const LOOP = 9.6;
const MOVES = [
  [1.2, 2.0, 0, 1],
  [3.4, 4.2, 1, 2],
  [5.6, 6.4, 2, 3],
  [7.6, 8.9, 3, 0],
] as const;

/** Carousel position (pages) at loop time t. */
export function offsetAt(t: number) {
  'worklet';
  let o = 0;
  for (let i = 0; i < MOVES.length; i++) {
    const m = MOVES[i];
    if (t >= m[0]) o = m[2] + (m[3] - m[2]) * (i === 3 ? easeInOut(seg(t, m[0], m[1])) : snap(seg(t, m[0], m[1])));
  }
  return o;
}
