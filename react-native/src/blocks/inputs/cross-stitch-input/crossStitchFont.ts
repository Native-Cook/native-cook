/*
 A 5 × 7 sampler alphabet, the kind printed on cross-stitch charts, and the layout of a name on the
 aida grid. Plain JS (runs once per text change on the JS thread); the result is flat number arrays
 the UI thread draws from.

 Capitals and digits use rows 0–6; lower case uses the same rows with ascenders in rows 0–1 and
 descenders in rows 7–8. 'X' is a stitch.
 */

const GLYPHS: Record<string, string> = {
  A: '.XXX.|X...X|X...X|XXXXX|X...X|X...X|X...X',
  B: 'XXXX.|X...X|X...X|XXXX.|X...X|X...X|XXXX.',
  C: '.XXX.|X...X|X....|X....|X....|X...X|.XXX.',
  D: 'XXXX.|X...X|X...X|X...X|X...X|X...X|XXXX.',
  E: 'XXXXX|X....|X....|XXXX.|X....|X....|XXXXX',
  F: 'XXXXX|X....|X....|XXXX.|X....|X....|X....',
  G: '.XXX.|X...X|X....|X.XXX|X...X|X...X|.XXXX',
  H: 'X...X|X...X|X...X|XXXXX|X...X|X...X|X...X',
  I: 'XXX|.X.|.X.|.X.|.X.|.X.|XXX',
  J: '..XXX|...X.|...X.|...X.|...X.|X..X.|.XX..',
  K: 'X...X|X..X.|X.X..|XX...|X.X..|X..X.|X...X',
  L: 'X....|X....|X....|X....|X....|X....|XXXXX',
  M: 'X...X|XX.XX|X.X.X|X.X.X|X...X|X...X|X...X',
  N: 'X...X|XX..X|X.X.X|X..XX|X...X|X...X|X...X',
  O: '.XXX.|X...X|X...X|X...X|X...X|X...X|.XXX.',
  P: 'XXXX.|X...X|X...X|XXXX.|X....|X....|X....',
  Q: '.XXX.|X...X|X...X|X...X|X.X.X|X..X.|.XX.X',
  R: 'XXXX.|X...X|X...X|XXXX.|X.X..|X..X.|X...X',
  S: '.XXXX|X....|X....|.XXX.|....X|....X|XXXX.',
  T: 'XXXXX|..X..|..X..|..X..|..X..|..X..|..X..',
  U: 'X...X|X...X|X...X|X...X|X...X|X...X|.XXX.',
  V: 'X...X|X...X|X...X|X...X|X...X|.X.X.|..X..',
  W: 'X...X|X...X|X...X|X.X.X|X.X.X|X.X.X|.X.X.',
  X: 'X...X|X...X|.X.X.|..X..|.X.X.|X...X|X...X',
  Y: 'X...X|X...X|.X.X.|..X..|..X..|..X..|..X..',
  Z: 'XXXXX|....X|...X.|..X..|.X...|X....|XXXXX',
  a: '.....|.....|.XXX.|....X|.XXXX|X...X|.XXXX',
  b: 'X....|X....|XXXX.|X...X|X...X|X...X|XXXX.',
  c: '.....|.....|.XXX.|X....|X....|X...X|.XXX.',
  d: '....X|....X|.XXXX|X...X|X...X|X...X|.XXXX',
  e: '.....|.....|.XXX.|X...X|XXXXX|X....|.XXX.',
  f: '..XX.|.X..X|.X...|XXX..|.X...|.X...|.X...',
  g: '.....|.....|.XXXX|X...X|X...X|X...X|.XXXX|....X|.XXX.',
  h: 'X....|X....|XXXX.|X...X|X...X|X...X|X...X',
  i: '.X.|...|XX.|.X.|.X.|.X.|XXX',
  j: '...X|....|..XX|...X|...X|...X|...X|X..X|.XX.',
  k: 'X...|X...|X..X|X.X.|XX..|X.X.|X..X',
  l: 'XX.|.X.|.X.|.X.|.X.|.X.|XXX',
  m: '.....|.....|XX.X.|X.X.X|X.X.X|X.X.X|X.X.X',
  n: '.....|.....|XXXX.|X...X|X...X|X...X|X...X',
  o: '.....|.....|.XXX.|X...X|X...X|X...X|.XXX.',
  p: '.....|.....|XXXX.|X...X|X...X|X...X|XXXX.|X....|X....',
  q: '.....|.....|.XXXX|X...X|X...X|X...X|.XXXX|....X|....X',
  r: '.....|.....|X.XX.|XX..X|X....|X....|X....',
  s: '.....|.....|.XXXX|X....|.XXX.|....X|XXXX.',
  t: '.X...|.X...|XXX..|.X...|.X...|.X..X|..XX.',
  u: '.....|.....|X...X|X...X|X...X|X..XX|.XX.X',
  v: '.....|.....|X...X|X...X|X...X|.X.X.|..X..',
  w: '.....|.....|X...X|X...X|X.X.X|X.X.X|.X.X.',
  x: '.....|.....|X...X|.X.X.|..X..|.X.X.|X...X',
  y: '.....|.....|X...X|X...X|X...X|X...X|.XXXX|....X|.XXX.',
  z: '.....|.....|XXXXX|...X.|..X..|.X...|XXXXX',
  '0': '.XXX.|X...X|X..XX|X.X.X|XX..X|X...X|.XXX.',
  '1': '..X..|.XX..|..X..|..X..|..X..|..X..|.XXX.',
  '2': '.XXX.|X...X|....X|...X.|..X..|.X...|XXXXX',
  '3': 'XXXX.|....X|....X|.XXX.|....X|....X|XXXX.',
  '4': '...X.|..XX.|.X.X.|X..X.|XXXXX|...X.|...X.',
  '5': 'XXXXX|X....|XXXX.|....X|....X|X...X|.XXX.',
  '6': '.XXX.|X....|X....|XXXX.|X...X|X...X|.XXX.',
  '7': 'XXXXX|....X|...X.|..X..|.X...|.X...|.X...',
  '8': '.XXX.|X...X|X...X|.XXX.|X...X|X...X|.XXX.',
  '9': '.XXX.|X...X|X...X|.XXXX|....X|....X|.XXX.',
  '.': 'X|X',
  '!': 'X|X|X|X|X|.|X',
  '-': '...|...|...|XXX|...|...|...',
  "'": 'X|X',
  '&': '.XX..|X..X.|.XX..|.X...|X.X.X|X..X.|.XX.X',
};

/** Rows of a glyph: [row][col] as booleans, already trimmed of empty side columns. */
type Glyph = { rows: boolean[][]; width: number; top: number };

const CACHE: Record<string, Glyph> = {};

function glyph(ch: string): Glyph | null {
  if (ch === ' ') return { rows: [], width: 3, top: 0 };
  const cached = CACHE[ch];
  if (cached) return cached;
  const src = GLYPHS[ch];
  if (!src) return null;
  let rows = src.split('|').map((r) => r.split('').map((c) => c === 'X'));
  // '.' and "'" sit low and high: a two-row glyph is placed by hand below.
  const top = ch === '.' ? 5 : 0;
  const width = Math.max(...rows.map((r) => r.length));
  rows = rows.map((r) => [...r, ...Array(width - r.length).fill(false)]);
  const g = { rows, width, top };
  CACHE[ch] = g;
  return g;
}

export const ROWS = 9;
/** Columns of grid a line may use before the name wraps to a second line. */
export const LINE_COLS = 24;
export const MAX_LETTERS = 10;

/**
 * A laid-out name: the cell of every stitch (in grid units, the fabric's centre at 0), the glyph
 * each stitch belongs to, and per glyph its first stitch, count and offset from its own cells.
 * Stitches are listed in working order: glyph by glyph, rows top to bottom, each row worked away
 * from where the last one ended (serpentine), so the needle's path is short.
 */
export type Layout = {
  cx: number[];
  cy: number[];
  glyphOf: number[];
  /** Per glyph: first stitch index, stitch count, column and row of its top-left cell. */
  first: number[];
  count: number[];
  col: number[];
  row: number[];
  total: number;
};

export function layoutName(text: string): Layout {
  const chars = Array.from(text).slice(0, MAX_LETTERS);
  type Word = { glyphs: (Glyph | null)[]; widths: number[] };
  // Measure, then split into at most two lines at the space or letter nearest the middle.
  const glyphs = chars.map((ch) => glyph(ch) ?? glyph(ch.toUpperCase()));
  const advance = (from: number, to: number) => {
    let w = 0;
    for (let i = from; i < to; i += 1) w += (glyphs[i]?.width ?? 0) + (i < to - 1 ? 1 : 0);
    return w;
  };
  let lines: [number, number][] = [[0, chars.length]];
  if (advance(0, chars.length) > LINE_COLS && chars.length > 1) {
    let best = 1;
    let bestScore = Infinity;
    for (let s = 1; s < chars.length; s += 1) {
      const a = advance(0, s);
      const b = advance(chars[s] === ' ' ? s + 1 : s, chars.length);
      const score = Math.max(a, b) - (chars[s] === ' ' ? 3 : 0);
      if (score < bestScore) {
        bestScore = score;
        best = s;
      }
    }
    lines = [
      [0, best],
      [chars[best] === ' ' ? best + 1 : best, chars.length],
    ];
  }
  const word: Word = { glyphs, widths: glyphs.map((g) => g?.width ?? 0) };
  const out: Layout = { cx: [], cy: [], glyphOf: [], first: [], count: [], col: [], row: [], total: 0 };
  // One line: capitals on rows -3..3 (centred). Two lines: capitals on rows -8..-2 and 1..7.
  const tops = lines.length === 1 ? [-3] : [-8, 1];
  for (let i = 0; i < chars.length; i += 1) {
    out.first.push(0);
    out.count.push(0);
    out.col.push(0);
    out.row.push(0);
  }
  lines.forEach(([from, to], li) => {
    const w = advance(from, to);
    let col = -Math.floor(w / 2);
    const rowTop = tops[li];
    for (let i = from; i < to; i += 1) {
      const g = word.glyphs[i];
      out.first[i] = out.cx.length;
      out.col[i] = col;
      out.row[i] = rowTop;
      if (g) {
        let leftToRight = true;
        for (let r = 0; r < g.rows.length; r += 1) {
          const cols: number[] = [];
          for (let c = 0; c < g.width; c += 1) if (g.rows[r][c]) cols.push(c);
          if (cols.length === 0) continue;
          if (!leftToRight) cols.reverse();
          for (const c of cols) {
            out.cx.push(col + c);
            out.cy.push(rowTop + g.top + r);
            out.glyphOf.push(i);
          }
          leftToRight = !leftToRight;
        }
        col += g.width + 1;
      }
      out.count[i] = out.cx.length - out.first[i];
    }
  });
  out.total = out.cx.length;
  return out;
}

/**
 * The sampler's printed border, worked before the name: a row of little hearts above and a
 * zig-zag below. Same cell units; 'top' or 'bottom' band.
 */
export function borderStitches(): { cx: number[]; cy: number[] } {
  const cx: number[] = [];
  const cy: number[] = [];
  const heart = ['.X.X.', 'XXXXX', '.XXX.', '..X..'];
  for (let k = -1; k <= 1; k += 1) {
    const ox = k * 7 - 2;
    heart.forEach((row, r) => {
      for (let c = 0; c < row.length; c += 1) if (row[c] === 'X') {
        cx.push(ox + c);
        cy.push(-12 + r);
      }
    });
  }
  for (let c = -9; c <= 9; c += 1) {
    cx.push(c);
    cy.push(10 + (Math.abs(c) % 2 === 0 ? 0 : 1));
  }
  return { cx, cy };
}
