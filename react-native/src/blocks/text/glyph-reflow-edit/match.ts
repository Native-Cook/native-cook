// Decides which letters of the old text become which letters of the new one. Pure, so each stack can port it as is.
export type GlyphPair = [from: number, to: number];
type Word = { start: number; end: number; key: string };

function wordsOf(text: string): Word[] {
  const out: Word[] = [];
  const pattern = /\S+/g;
  let found = pattern.exec(text);
  while (found) {
    out.push({ start: found.index, end: found.index + found[0].length, key: found[0].toLowerCase().replace(/[^\p{L}\p{N}]/gu, '') });
    found = pattern.exec(text);
  }
  return out;
}

/** Longest common subsequence, as index pairs in increasing order. */
function commonRun<T>(a: T[], b: T[]): GlyphPair[] {
  const row = b.length + 1;
  const table = new Uint16Array((a.length + 1) * row);
  for (let i = a.length - 1; i >= 0; i -= 1) {
    for (let j = b.length - 1; j >= 0; j -= 1) {
      table[i * row + j] = a[i] === b[j] ? table[(i + 1) * row + j + 1] + 1 : Math.max(table[(i + 1) * row + j], table[i * row + j + 1]);
    }
  }
  const out: GlyphPair[] = [];
  let i = 0;
  let j = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      out.push([i, j]);
      i += 1;
      j += 1;
    } else if (table[(i + 1) * row + j] >= table[i * row + j + 1]) {
      i += 1;
    } else {
      j += 1;
    }
  }
  return out;
}

function lettersIn(text: string, from: number, to: number) {
  const at: number[] = [];
  for (let i = from; i < to; i += 1) if (text[i].trim() !== '') at.push(i);
  return at;
}

function pairLetters(a: string, b: string, aFrom: number, aTo: number, bFrom: number, bTo: number, out: GlyphPair[]) {
  const aAt = lettersIn(a, aFrom, aTo);
  const bAt = lettersIn(b, bFrom, bTo);
  const run = commonRun(aAt.map((i) => a[i]), bAt.map((i) => b[i]));
  for (const [i, j] of run) out.push([aAt[i], bAt[j]]);
}

/**
 * Whole words that survive the edit are anchors, so they travel together. In the gaps between anchors, the letters
 * the two versions share are paired in reading order. Everything unpaired fades.
 */
export function matchGlyphs(a: string, b: string): GlyphPair[] {
  const aWords = wordsOf(a);
  const bWords = wordsOf(b);
  const anchors = commonRun(aWords.map((word) => word.key), bWords.map((word) => word.key));
  const out: GlyphPair[] = [];
  let aCursor = 0;
  let bCursor = 0;
  for (const [i, j] of anchors) {
    pairLetters(a, b, aCursor, aWords[i].start, bCursor, bWords[j].start, out);
    pairLetters(a, b, aWords[i].start, aWords[i].end, bWords[j].start, bWords[j].end, out);
    aCursor = aWords[i].end;
    bCursor = bWords[j].end;
  }
  pairLetters(a, b, aCursor, a.length, bCursor, b.length, out);
  return out;
}
