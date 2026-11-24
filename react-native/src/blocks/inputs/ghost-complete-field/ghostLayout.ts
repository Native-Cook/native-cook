import type { SkFont } from '@shopify/react-native-skia';

/*
 Ghost Complete Field — measuring and wrapping.

 The field draws its own text, so it needs to know where every glyph goes. `makeGhostLayout`
 wraps the typed text and the suggestion after it as ONE paragraph: accepting the suggestion
 then moves nothing, the grey glyphs just take ink where they already are. Glyph advances come
 from the font (getGlyphWidths), never from measureText, which rounds each glyph.
*/

/** One stretch of suggested text inside the paragraph. */
export type GhostRun = {
  /** Glyph range [start, end). */
  start: number;
  end: number;
  /** Length of the run along the reading direction, pt (the ink sweep travels this far). */
  length: number;
  /**
   Its words, each on one line. `x0` is where the run starts on that word's line and `off`
   how much of the run came before that point, so one sweep distance gives every line its front.
  */
  words: { a: number; b: number; x0: number; off: number }[];
  /** Where the keycap chip sits: left edge and the top of its line. */
  chipX: number;
  chipY: number;
};

export type GhostLayout = {
  /** Every glyph of the typed text and the suggestions: id, pen x and baseline y. */
  ids: number[];
  xs: number[];
  ys: number[];
  /** Where the caret stands after k glyphs (k = 0 .. ids.length): x and the top of its line. */
  cx: number[];
  cy: number[];
  runs: GhostRun[];
  /** Lines the paragraph takes, the keycap's line included. */
  lines: number;
};

export const EMPTY_LAYOUT: GhostLayout = { ids: [], xs: [], ys: [], cx: [0], cy: [0], runs: [], lines: 1 };

/** Most words a suggestion may have: one drawn slot each. */
export const MAX_WORDS = 8;
/** Width kept free after a suggestion for the keycap chip. */
export const KEYCAP_W = 70;
const KEYCAP_GAP = 10;

/** What the field can draw: one glyph per UTF-16 unit, no line breaks unless asked. */
export function cleanText(text: string, multiline: boolean) {
  let out = '';
  for (let i = 0; i < text.length; i += 1) {
    const c = text.charCodeAt(i);
    if (c >= 0xd800 && c <= 0xdfff) continue;
    if (c === 10 && !multiline) continue;
    if (c === 13 || c === 9) continue;
    out += text[i];
  }
  return out;
}

/** The first baseline below a line's top, where a native Text of this line height puts it. */
export function firstBaseline(font: SkFont, lineHeight: number) {
  const m = font.getMetrics();
  return (lineHeight - (m.descent - m.ascent)) / 2 - m.ascent;
}

export function textWidth(font: SkFont, text: string) {
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let total = 0;
  for (let i = 0; i < widths.length; i += 1) total += widths[i];
  return total;
}

/**
 Wraps `text` into `width` and describes the suggestion ranges in it.
 `ghosts` are [start, end) character ranges, in order and not overlapping.
*/
export function makeGhostLayout(font: SkFont, text: string, ghosts: [number, number][], width: number, lineHeight: number, keycap = true): GhostLayout {
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  const n = Math.min(ids.length, text.length);
  const base = firstBaseline(font, lineHeight);
  const xs: number[] = new Array(n);
  const lineOf: number[] = new Array(n);

  let x = 0;
  let line = 0;
  let i = 0;
  while (i < n) {
    const ch = text[i];
    if (ch === '\n') {
      xs[i] = x;
      lineOf[i] = line;
      widths[i] = 0;
      line += 1;
      x = 0;
      i += 1;
    } else if (ch === ' ') {
      xs[i] = x;
      lineOf[i] = line;
      x += widths[i];
      i += 1;
    } else {
      let j = i;
      let word = 0;
      while (j < n && text[j] !== ' ' && text[j] !== '\n') {
        word += widths[j];
        j += 1;
      }
      if (x > 0 && x + word > width) {
        line += 1;
        x = 0;
      }
      for (let k = i; k < j; k += 1) {
        if (x > 0 && x + widths[k] > width) {
          line += 1;
          x = 0;
        }
        xs[k] = x;
        lineOf[k] = line;
        x += widths[k];
      }
      i = j;
    }
  }

  const cx: number[] = [0];
  const cy: number[] = [0];
  for (let k = 1; k <= n; k += 1) {
    if (text[k - 1] === '\n') {
      cx.push(0);
      cy.push((lineOf[k - 1] + 1) * lineHeight);
    } else {
      cx.push(Math.min(width, xs[k - 1] + widths[k - 1]));
      cy.push(lineOf[k - 1] * lineHeight);
    }
  }

  let lines = n > 0 ? lineOf[n - 1] + (text[n - 1] === '\n' ? 2 : 1) : 1;
  const runs: GhostRun[] = [];
  for (const [g0, g1] of ghosts) {
    const start = Math.max(0, Math.min(n, g0));
    const end = Math.max(start, Math.min(n, g1));
    const words: GhostRun['words'] = [];
    let flow = 0;
    let lineStartX = 0;
    let lineStartFlow = 0;
    let current = -1;
    let a = start;
    for (let k = start; k < end; k += 1) {
      const newLine = lineOf[k] !== current;
      // A word ends where the next one starts (after its trailing spaces) or where the line does.
      const newWord = k > start && text[k] !== ' ' && text[k - 1] === ' ';
      if (k > a && (newLine || newWord) && words.length < MAX_WORDS - 1) {
        words.push({ a, b: k, x0: lineStartX, off: lineStartFlow });
        a = k;
      }
      if (newLine) {
        current = lineOf[k];
        lineStartX = xs[k];
        lineStartFlow = flow;
      }
      flow += widths[k];
    }
    if (end > a) words.push({ a, b: end, x0: lineStartX, off: lineStartFlow });

    let chipX = 0;
    let chipY = 0;
    if (end > start) {
      const last = end - 1;
      const after = xs[last] + widths[last];
      if (after + KEYCAP_GAP + KEYCAP_W <= width) {
        chipX = after + KEYCAP_GAP;
        chipY = lineOf[last] * lineHeight;
      } else {
        chipY = (lineOf[last] + 1) * lineHeight;
        if (keycap) lines = Math.max(lines, lineOf[last] + 2);
      }
    }
    runs.push({ start, end, length: flow, words, chipX, chipY });
  }

  const ys: number[] = new Array(n);
  for (let k = 0; k < n; k += 1) ys[k] = lineOf[k] * lineHeight + base;
  return { ids: ids.slice(0, n), xs, ys, cx, cy, runs, lines };
}

// ---- Suggestions -------------------------------------------------------------------------------------

/** Phrases the demo's mail app completes. A real app would ask its model instead. */
const PHRASES = [
  'Hi Mara, thanks for the notes — ',
  'can we walk through them Thursday?',
  'thanks for getting back to me so quickly.',
  'let me know what works for you.',
  'sounds good to me.',
  'see you on Thursday.',
  'happy to take a look this week.',
  'I will send the updated draft tonight.',
  'looking forward to it.',
  'Best, Ines',
];
/** Letters that must be typed before a phrase is offered. */
const MIN_MATCH = 3;

/**
 The rest of a known phrase that the end of `text` has started, or null. The match starts at a
 word boundary and ignores case; the longest start wins.
*/
export function suggestPhrase(text: string): string | null {
  if (text.length < MIN_MATCH || text.endsWith('\n')) return null;
  const from = Math.max(0, text.length - 40);
  for (let s = from; s <= text.length - MIN_MATCH; s += 1) {
    if (s > 0 && text[s - 1] !== ' ' && text[s - 1] !== '\n') continue;
    const tail = text.slice(s).toLowerCase();
    if (tail.trim().length < MIN_MATCH) continue;
    for (const phrase of PHRASES) {
      if (phrase.length > tail.length && phrase.toLowerCase().startsWith(tail)) return phrase.slice(tail.length);
    }
  }
  return null;
}

/** Mail domains offered after the @ of an address. Invented. */
export const DOMAINS = ['postbox.co', 'northmail.com', 'fernhill.org', 'studio-okafor.com'];

/** Up to two domains that continue what follows the @, or none when there is no @ yet. */
export function suggestDomains(address: string): string[] {
  const at = address.indexOf('@');
  if (at < 1 || address.indexOf('@', at + 1) >= 0 || address.includes(' ')) return [];
  const started = address.slice(at + 1).toLowerCase();
  return DOMAINS.filter((d) => d.startsWith(started) && d !== started).slice(0, 2);
}
