/*
 Chalkboard Search — the loop script and the menu.

 LOOP = 9 s
   0.0 – 1.2   rest: the board, a faint "search the menu…" in chalk on the search line.
   1.0 – 1.2   the placeholder rubs away.
   1.2 – 3.3   "lemon tart" is chalked letter by letter (0.21 s a glyph, the pen easing through
               each), dust falling from under every stroke (gravity 320 pt/s², 0.9 s).
   3.4 – 4.45  three menu lines are chalked in, 0.3 s apart, 0.55 s each.
   4.45 – 6.0  hold.
   6.0 – 7.2   a felt eraser sweeps the board in four passes (ease-in-out), wiping the chalk and
               leaving a haze where it went.
   7.2 – 8.6   the haze clears; 7.6 – 8.4 the placeholder comes back.
   8.6 – 9.0   rest.
*/

export const LOOP = 9;
export const QUERY = 'lemon tart';
export const Q_AT = 1.2;
export const Q_STEP = 0.21;
export const SUG_AT = 3.4;
export const SUG_STEP = 0.3;
export const SUG_LEN = 0.55;
export const ERASE_AT = 6.0;
export const ERASE_END = 7.2;
const HAZE_END = 8.6;
export const DUST_LIFE = 0.9;
export const DUST_PER_GLYPH = 8;

export type Item = { name: string; price: number };

export const MENU: Item[] = [
  { name: 'Lemon tart', price: 6 },
  { name: 'Lemon curd scone', price: 4 },
  { name: 'Tarte au citron', price: 7 },
  { name: 'Flat white', price: 4 },
  { name: 'Cardamom bun', price: 5 },
  { name: 'Oat milk latte', price: 5 },
  { name: 'Sourdough toast', price: 8 },
  { name: 'Almond croissant', price: 5 },
  { name: 'Rhubarb crumble', price: 6 },
  { name: 'Fresh mint tea', price: 3 },
  { name: 'Mushroom tartine', price: 11 },
  { name: 'Spiced chai', price: 4 },
  { name: 'Pear & ginger cake', price: 6 },
  { name: 'Iced lemonade', price: 4 },
  { name: 'Shakshuka', price: 12 },
  { name: 'Butter scone', price: 3 },
];

export function itemLine(i: Item) {
  return `${i.name} · $${i.price}`;
}

/** Up to three menu lines for a query: items sharing a word stem, best matches first. */
export function suggest(query: string): string[] {
  const words = query.toLowerCase().split(/[^a-zà-ÿ]+/).filter((w) => w.length >= 2);
  if (words.length === 0) return [];
  const scored = MENU.map((item) => {
    const name = item.name.toLowerCase();
    let score = 0;
    for (const w of words) {
      if (name.startsWith(w)) score += 3;
      else if (name.split(/\s+/).some((p) => p.startsWith(w.slice(0, Math.max(3, w.length - 1))))) score += 2;
      else if (name.includes(w)) score += 1;
    }
    return { item, score };
  }).filter((s) => s.score > 0);
  scored.sort((a, b) => b.score - a.score);
  return scored.slice(0, 3).map((s) => itemLine(s.item));
}

export const AUTO_SUGGESTIONS = [itemLine(MENU[0]), itemLine(MENU[1]), itemLine(MENU[2])];

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOutCubic(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeInOutSine(x: number) {
  'worklet';
  return 0.5 - 0.5 * Math.cos(Math.PI * x);
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** When glyph i of the loop's query starts to be chalked. */
export function glyphAt(i: number) {
  'worklet';
  return Q_AT + i * Q_STEP;
}

/** The pen's x on the query line at loop time t, from the glyph edges. */
export function autoPen(t: number, lefts: number[], rights: number[]) {
  'worklet';
  if (lefts.length === 0 || t < Q_AT) return lefts.length ? lefts[0] : 0;
  const i = Math.min(lefts.length - 1, Math.floor((t - Q_AT) / Q_STEP));
  const u = easeInOutSine(clamp01((t - glyphAt(i)) / Q_STEP));
  return lefts[i] + (rights[i] - lefts[i]) * u;
}

export type Board = {
  /** Pen x on the query line, and each suggestion line's reveal 0…1. */
  pen: number;
  sug: number[];
  /** How far along its path the eraser has wiped 0…1, the eraser's opacity, the haze's opacity. */
  trail: number;
  eraserOn: number;
  haze: number;
  placeholder: number;
};

export function autoBoard(t: number, lefts: number[], rights: number[]): Board {
  'worklet';
  const sug = [0, 1, 2].map((j) => easeInOutSine(seg(t, SUG_AT + j * SUG_STEP, SUG_AT + j * SUG_STEP + SUG_LEN)));
  const erasing = t >= ERASE_AT;
  return {
    pen: t >= ERASE_END ? (lefts.length ? lefts[0] : 0) : autoPen(t, lefts, rights),
    sug: t >= ERASE_END ? [0, 0, 0] : sug,
    trail: erasing && t < HAZE_END ? easeInOutCubic(seg(t, ERASE_AT, ERASE_END)) : 0,
    eraserOn: erasing ? clamp01((t - ERASE_AT) / 0.15) * (1 - seg(t, ERASE_END - 0.15, ERASE_END)) : 0,
    haze: erasing ? clamp01((t - ERASE_AT) / 0.2) * (1 - easeInOutSine(seg(t, ERASE_END, HAZE_END))) : 0,
    placeholder: Math.max(1 - seg(t, 1.0, 1.2), easeInOutSine(seg(t, 7.6, 8.4))),
  };
}
