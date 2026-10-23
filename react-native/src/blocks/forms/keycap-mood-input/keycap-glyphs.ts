import { WORD_CHOICES } from './keycap-theme';

export const WORD_BITMAPS: Record<string, string[]> = {
  i: ['.X.', '...', 'XX.', '.X.', '.X.', 'XXX'],
  f: ['..XX', '.X..', 'XXXX', '.X..', '.X..', '.X..'],
  e: ['....', '....', '.XX.', 'XXXX', 'X...', '.XXX'],
  l: ['XX.', '.X.', '.X.', '.X.', '.X.', 'XXX'],
  s: ['....', '....', '.XXX', 'XX..', '..XX', 'XXX.'],
  o: ['....', '....', '.XX.', 'X..X', 'X..X', '.XX.'],
  w: ['.....', '.....', 'X...X', 'X.X.X', 'X.X.X', '.X.X.'],
  t: ['....', '.X..', 'XXXX', '.X..', '.X..', '..XX'],
  v: ['.....', '.....', 'X...X', 'X...X', '.X.X.', '..X..'],
  r: ['....', '....', 'X.XX', 'XX..', 'X...', 'X...'],
  y: ['....', '....', 'X..X', 'X..X', 'X..X', '.XXX', '...X', '.XX.'],
  a: ['....', '....', '.XXX', 'X..X', 'X..X', '.XXX'],
  b: ['X...', 'X...', 'XXX.', 'X..X', 'X..X', 'XXX.'],
  u: ['....', '....', 'X..X', 'X..X', 'X..X', '.XXX'],
};
export const FACE_BITMAPS = [
  ['.XXX....XXX.', 'X...X..X...X', 'X...X..X...X', '............', '............', 'X..........X', 'X..........X', '.X........X.', '..X......X..', '...XXXXXX...'],
  ['.X.X....X.X.', 'XXXXX..XXXXX', 'XXXXX..XXXXX', '.XXX....XXX.', '..X......X..', '............', 'X..........X', '.X........X.', '..X......X..', '...XXXXXX...'],
  ['.XXX....XXX.', '.X.X....X.X.', '.XXX....XXX.', '............', '............', '....XXXX....', '...X....X...', '...X....X...', '...X....X...', '....XXXX....'],
  ['............', 'X...X..X...X', '.XXX....XXX.', '............', '............', '............', '............', '..X......X..', '...XXXXXX...', '............'],
  ['...X....X...', '..X......X..', '............', '.XX......XX.', '.XX......XX.', '............', '............', '...XXXXXX...', '..X......X..', '.X........X.'],
  ['X..........X', '.XX......XX.', '...X....X...', '.XX......XX.', '.XX......XX.', '............', '............', '............', '..XXXXXXXX..', '............'],
];

/** Flat [x,y,...], in dot units about the top-face centre. Word row 3.5 is optical y=0. */
function wordDots(word: string): number[] {
  let width = 0;
  for (let i = 0; i < word.length; i++) {
    width += word[i] === ' ' ? 3 : WORD_BITMAPS[word[i]][0].length;
    if (word[i] !== ' ' && i < word.length - 1 && word[i + 1] !== ' ') width++;
  }
  const dots: number[] = [];
  let cursor = 0;
  for (let i = 0; i < word.length; i++) {
    if (word[i] === ' ') { cursor += 3; continue; }
    const rows = WORD_BITMAPS[word[i]];
    for (let y = 0; y < rows.length; y++) {
      for (let x = 0; x < rows[y].length; x++) if (rows[y][x] === 'X') dots.push(cursor + x - (width - 1) / 2, y - 3.5);
    }
    cursor += rows[0].length + (i < word.length - 1 && word[i + 1] !== ' ' ? 1 : 0);
  }
  return dots;
}
function faceDots(rows: string[]): number[] {
  const dots: number[] = [];
  for (let y = 0; y < 10; y++) {
    for (let x = 0; x < 12; x++) if (rows[y][x] === 'X') dots.push(x - 5.5, y - 4.5);
  }
  return dots;
}
export const LEGENDS: number[][][] = [
  WORD_CHOICES[0].map(wordDots), WORD_CHOICES[1].map(wordDots), WORD_CHOICES[2].map(wordDots), FACE_BITMAPS.map(faceDots),
];
// [key][from][to][stay|off|on]. Each list is flat [x,y,delayMs,...].
export const FLIP_PLANS: number[][][][][] = [];
for (let key = 0; key < LEGENDS.length; key++) {
  const pairs: number[][][][] = [];
  for (let from = 0; from < LEGENDS[key].length; from++) {
    const targets: number[][][] = [];
    for (let to = 0; to < LEGENDS[key].length; to++) {
      const before = LEGENDS[key][from];
      const after = LEGENDS[key][to];
      const lists: number[][] = [[], [], []];
      for (let side = 0; side < 2; side++) {
        const a = side === 0 ? before : after;
        const b = side === 0 ? after : before;
        for (let i = 0; i < a.length; i += 2) {
          let found = false;
          for (let j = 0; j < b.length; j += 2) if (a[i] === b[j] && a[i + 1] === b[j + 1]) found = true;
          if (side === 1 && found) continue;
          const delay = key === 3 ? Math.hypot(a[i], a[i + 1]) * (16 / 3) * 0.9 : (a[i + 1] + 3.5) * 14;
          lists[found ? 0 : side + 1].push(a[i], a[i + 1], found ? 0 : delay);
        }
      }
      targets.push(lists);
    }
    pairs.push(targets);
  }
  FLIP_PLANS.push(pairs);
}

/** Radial plans above are centred at origin, u=1; call for the actual touch and snapped pitch. */
export function flipDotDelay(key: number, x: number, y: number, pitch: number, ox: number, oy: number): number {
  'worklet';
  return key === 3 ? Math.hypot(x * pitch - ox, y * pitch - oy) * 0.9 : (y + 3.5) * 14;
}
