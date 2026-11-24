// Measures every glyph once and builds the tables the canvas draws from. Nothing here runs per frame.
import type { SkFont } from '@shopify/react-native-skia';

import { type GlyphPair, matchGlyphs } from './match';
import { GLYPH } from './motion';

export type Laid = { ids: number[]; x: number[]; y: number[]; line: number[]; lines: number; height: number };

export type Morph = {
  // Glyphs both versions share.
  ids: number[];
  ax: number[];
  ay: number[];
  bx: number[];
  by: number[];
  arc: number[];
  delay: number[];
  // Glyphs only the original has.
  outIds: number[];
  outX: number[];
  outY: number[];
  outDelay: number[];
  // Glyphs only the rewrite has.
  inIds: number[];
  inX: number[];
  inY: number[];
  inDelay: number[];
};

/** Greedy word wrap on real glyph advances (`getGlyphWidths`, never rounded `measureText`). */
export function layOut(font: SkFont, text: string, width: number, lineHeight: number): Laid {
  const ids = font.getGlyphIDs(text);
  const advances = font.getGlyphWidths(ids);
  const metrics = font.getMetrics();
  const baseline = (lineHeight - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent;
  const x = new Array<number>(ids.length).fill(0);
  const y = new Array<number>(ids.length).fill(0);
  const line = new Array<number>(ids.length).fill(0);
  let row = 0;
  let pen = 0;
  let i = 0;
  while (i < text.length) {
    let end = i;
    while (end < text.length && text[end] !== ' ') end += 1;
    let wordWidth = 0;
    for (let k = i; k < end; k += 1) wordWidth += advances[k];
    if (pen > 0 && pen + wordWidth > width) {
      row += 1;
      pen = 0;
    }
    for (let k = i; k < end; k += 1) {
      x[k] = pen;
      y[k] = row * lineHeight + baseline;
      line[k] = row;
      pen += advances[k];
    }
    if (end < text.length) {
      x[end] = pen;
      y[end] = row * lineHeight + baseline;
      line[end] = row;
      pen += advances[end];
    }
    i = end + 1;
  }
  return { ids, x, y, line, lines: row + 1, height: (row + 1) * lineHeight };
}

function hash(i: number) {
  const v = Math.sin(i * 12.9898) * 43758.5453;
  return v - Math.floor(v);
}

function orderOf(laid: Laid, i: number, width: number) {
  const rows = Math.max(laid.lines - 1, 1);
  return Math.min((laid.line[i] / rows) * 0.62 + (laid.x[i] / width) * 0.38, 1);
}

export function buildMorph(fromText: string, toText: string, from: Laid, to: Laid, width: number): Morph {
  const pairs: GlyphPair[] = matchGlyphs(fromText, toText);
  const usedFrom = new Set<number>();
  const usedTo = new Set<number>();
  const morph: Morph = { ids: [], ax: [], ay: [], bx: [], by: [], arc: [], delay: [], outIds: [], outX: [], outY: [], outDelay: [], inIds: [], inX: [], inY: [], inDelay: [] };
  // Letters landing in one word share its arc and its start, so words stay readable in flight.
  const wordOf = new Array<number>(toText.length).fill(0);
  for (let i = 1; i < toText.length; i += 1) wordOf[i] = wordOf[i - 1] + (toText[i] !== ' ' && toText[i - 1] === ' ' ? 1 : 0);
  const flights = new Map<number, { arc: number; delay: number; seen: number }>();
  pairs.forEach(([a, b]) => {
    usedFrom.add(a);
    usedTo.add(b);
    const distance = Math.hypot(to.x[b] - from.x[a], to.y[b] - from.y[a]);
    let flight = flights.get(wordOf[b]);
    if (!flight) {
      const height = Math.min(GLYPH.arcMin + distance * GLYPH.arcPerPoint, GLYPH.arcMax) * (0.8 + hash(wordOf[b]) * 0.2);
      flight = { arc: -height, delay: orderOf(to, b, width), seen: 0 };
      flights.set(wordOf[b], flight);
    }
    morph.ids.push(from.ids[a]);
    morph.ax.push(from.x[a]);
    morph.ay.push(from.y[a]);
    morph.bx.push(to.x[b]);
    morph.by.push(to.y[b]);
    morph.arc.push(distance < 0.5 ? 0 : flight.arc);
    morph.delay.push(Math.min(flight.delay + flight.seen * GLYPH.letterLag, 1));
    flight.seen += 1;
  });
  for (let i = 0; i < fromText.length; i += 1) {
    if (usedFrom.has(i) || fromText[i] === ' ') continue;
    morph.outIds.push(from.ids[i]);
    morph.outX.push(from.x[i]);
    morph.outY.push(from.y[i]);
    morph.outDelay.push(orderOf(from, i, width));
  }
  for (let i = 0; i < toText.length; i += 1) {
    if (usedTo.has(i) || toText[i] === ' ') continue;
    morph.inIds.push(to.ids[i]);
    morph.inX.push(to.x[i]);
    morph.inY.push(to.y[i]);
    morph.inDelay.push(orderOf(to, i, width));
  }
  return morph;
}

export function statsOf(original: string, text: string) {
  const words = text.trim().split(/\s+/).length;
  const base = original.trim().split(/\s+/).length;
  const change = Math.round(((words - base) / base) * 100);
  return { words: String(words), read: `${Math.max(Math.round(words / 3.6), 1)} s`, change: change === 0 ? '0%' : `${change > 0 ? '+' : '−'}${Math.abs(change)}%` };
}
