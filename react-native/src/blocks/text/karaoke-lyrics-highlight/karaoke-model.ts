import type { SkFont } from '@shopify/react-native-skia';

/*
 Karaoke Lyrics Highlight — the lyrics, their timing, the layout and the pure timeline. A port of
 KaraokeLyricsHighlightModel.swift; the numbers are the same.

 The chorus has four lines and repeats, so the lyric list is endless: absolute line i shows
 chorus line i mod 4, and the list scrolls down it forever. Every line gets a 2.5 s slot: its
 words fill between 0.1 and 2.05 s (times below), and at 1.95 s the list starts scrolling to the
 next line, reaching it exactly at 2.5 s along an ease-out-back curve (a small overshoot, like a
 soft spring). Four slots make the 10 s loop, ending one chorus further down, which looks exactly
 like its start.
 */

export type KaraokeWord = {
  text: string;
  /** Seconds into the line's slot when the fill starts and ends. */
  start: number;
  end: number;
  /** A held note: its letters wave while it fills. */
  held?: boolean;
};

export const SONG = {
  title: 'Paper Satellites',
  artist: 'Lumen Fair',
  /** The chorus starts 1:12 into a 3:43 song. */
  chorusAt: 72,
  length: 223,
};

export const CHORUS: KaraokeWord[][] = [
  [
    { text: 'We', start: 0.15, end: 0.4 },
    { text: 'fold', start: 0.4, end: 0.7 },
    { text: 'the', start: 0.7, end: 0.88 },
    { text: 'night', start: 0.88, end: 1.25 },
    { text: 'into', start: 1.25, end: 1.52 },
    { text: 'paper', start: 1.52, end: 2.0 },
  ],
  [
    { text: 'and', start: 0.15, end: 0.33 },
    { text: 'throw', start: 0.33, end: 0.68 },
    { text: 'it', start: 0.68, end: 0.82 },
    { text: 'past', start: 0.82, end: 1.1 },
    { text: 'the', start: 1.1, end: 1.25 },
    { text: 'streetlights', start: 1.25, end: 2.0 },
  ],
  [
    { text: 'Ohhh,', start: 0.1, end: 1.05, held: true },
    { text: "we're", start: 1.05, end: 1.3 },
    { text: 'paper', start: 1.3, end: 1.62 },
    { text: 'satellites', start: 1.62, end: 2.05 },
  ],
  [
    { text: 'still', start: 0.15, end: 0.42 },
    { text: 'circling,', start: 0.42, end: 0.98 },
    { text: 'still', start: 1.0, end: 1.3 },
    { text: 'bright', start: 1.3, end: 2.0 },
  ],
];

export const SLOT = 2.5;
export const LINES = 4;
export const LOOP = 10;
export const SCROLL_START = 1.95;
/** The playhead glides from the end of the chorus back to its start over the last 0.5 s. */
export const REWIND = 0.5;

export function easeOutBack(t: number) {
  'worklet';
  const c1 = 1.2;
  const c3 = c1 + 1;
  const u = Math.min(1, Math.max(0, t)) - 1;
  return 1 + c3 * u * u * u + c1 * u * u;
}

export function easeInOut(t: number) {
  'worklet';
  const x = Math.min(1, Math.max(0, t));
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = Math.min(1, Math.max(0, (x - a) / (b - a)));
  return t * t * (3 - 2 * t);
}

/** Where the list is, in lines from the loop's first line, `e` seconds into the loop (0 ≤ e < 10). */
export function scrollAt(e: number) {
  'worklet';
  const k = Math.min(LINES - 1, Math.floor(e / SLOT));
  const within = e - k * SLOT;
  return k + easeOutBack(Math.max(0, (within - SCROLL_START) / (SLOT - SCROLL_START)));
}

/** The line being sung (0…3). */
export function singingLine(e: number) {
  'worklet';
  return Math.min(LINES - 1, Math.floor(e / SLOT));
}

export function wordFill(w: KaraokeWord, tau: number) {
  'worklet';
  return Math.min(1, Math.max(0, (tau - w.start) / (w.end - w.start)));
}

/** How much a word is lifted (0…1): in over 0.12 s as it starts, out over 0.2 s as it ends. */
export function wordLift(w: KaraokeWord, tau: number) {
  'worklet';
  return smoothstep(w.start, w.start + 0.12, tau) * (1 - smoothstep(w.end - 0.05, w.end + 0.15, tau));
}

/** The song time in seconds shown by the scrubber. */
export function playheadAt(e: number) {
  'worklet';
  const end = SONG.chorusAt + LOOP - REWIND;
  if (e < LOOP - REWIND) return SONG.chorusAt + e;
  return end + (SONG.chorusAt - end) * easeInOut((e - (LOOP - REWIND)) / REWIND);
}

/** Each line's look by its distance from the focus, d = line − scroll (negative: already sung). */
export function sungAlpha(d: number) {
  'worklet';
  return d >= 0 ? 1 : 1 - 0.75 * Math.min(1, -d);
}
export function unsungAlpha(d: number) {
  'worklet';
  return d <= 0 ? 0.35 : 0.35 - 0.1 * Math.min(d, 2);
}
export function lineBlur(d: number) {
  'worklet';
  return d < 0 ? 2.5 * Math.min(1, -d) : 1.5 * Math.min(d, 2);
}

export const SIZE = 32;
export const ROW_HEIGHT = 38;
export const GAP = 22;
export const MARGIN = 24;

export type PlacedWord = { word: KaraokeWord; x: number; row: number; width: number; letters: { ch: string; x: number }[] };
export type Line = { words: PlacedWord[]; height: number };
export type KaraokeLayout = { lines: Line[]; prefix: number[]; cycle: number };

/** The chorus laid out for a width: lines wrapped into rows by measured advances (the font at SIZE). */
export function buildLayout(width: number, font: SkFont): KaraokeLayout {
  const adv = (ch: string) => font.getGlyphWidths(font.getGlyphIDs(ch))[0];
  const maxWidth = width - 2 * MARGIN;
  const space = adv(' ');
  const lines: Line[] = CHORUS.map((words) => {
    const placed: PlacedWord[] = [];
    let x = 0;
    let row = 0;
    for (const w of words) {
      const chars = Array.from(w.text);
      const advances = chars.map(adv);
      const ww = advances.reduce((a, b) => a + b, 0);
      if (x > 0 && x + ww > maxWidth) {
        row += 1;
        x = 0;
      }
      let lx = 0;
      const letters = chars.map((ch, i) => {
        const l = { ch, x: lx };
        lx += advances[i];
        return l;
      });
      placed.push({ word: w, x: MARGIN + x, row, width: ww, letters });
      x += ww + space;
    }
    return { words: placed, height: (placed[placed.length - 1].row + 1) * ROW_HEIGHT };
  });
  const prefix: number[] = [];
  let y = 0;
  for (const l of lines) {
    prefix.push(y);
    y += l.height + GAP;
  }
  return { lines, prefix, cycle: y };
}

export function lineAt(l: KaraokeLayout, i: number): Line {
  'worklet';
  const n = l.lines.length;
  return l.lines[((i % n) + n) % n];
}

/** Top of absolute line i, from the top of line 0. */
export function lineTop(l: KaraokeLayout, i: number) {
  'worklet';
  const n = l.lines.length;
  const c = Math.floor(i / n);
  return c * l.cycle + l.prefix[i - c * n];
}

/** The scroll offset in points for a scroll position in lines. */
export function offsetOf(l: KaraokeLayout, s: number) {
  'worklet';
  const i = Math.floor(s);
  return lineTop(l, i) + (s - i) * (lineAt(l, i).height + GAP);
}

/** The scroll position in lines for an offset in points. */
export function positionOf(l: KaraokeLayout, y: number) {
  'worklet';
  const n = l.lines.length;
  const c = Math.floor(y / l.cycle);
  let r = y - c * l.cycle;
  for (let k = 0; k < n; k++) {
    const step = l.lines[k].height + GAP;
    if (r < step || k === n - 1) return c * n + k + Math.min(1, r / step);
    r -= step;
  }
  return c * n;
}
