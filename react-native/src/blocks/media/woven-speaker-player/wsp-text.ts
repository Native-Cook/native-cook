// Woven Speaker Player — text for the canvas. Skia draws a run of glyph ids at positions, so the tracking, the centring and the
// widths (getGlyphWidths, not measureText: it rounds each glyph to whole points) are computed once per string, here.
import { Skia, type SkFont, type SkTypeface } from '@shopify/react-native-skia';

export type Run = {
  font: SkFont;
  glyphs: { id: number; pos: { x: number; y: number } }[];
  /** advance width of the whole run, design pt */
  width: number;
  size: number;
};

/** One run: `tracking` in em, added after every glyph but the last. */
export function makeRun(face: SkTypeface, size: number, text: string, tracking = 0): Run {
  const font = Skia.Font(face, size);
  // Skia 2.6.2's JSI setSubpixel rejects a boolean ("Value is true, expected a number"); the default is fine at 3x.
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  const glyphs: Run['glyphs'] = [];
  let x = 0;
  for (let i = 0; i < ids.length; i++) {
    glyphs.push({ id: ids[i], pos: { x, y: 0 } });
    x += widths[i] + (i < ids.length - 1 ? tracking * size : 0);
  }
  return { font, glyphs, width: x, size };
}

/** The advance of one character, for the tabular time label. */
export function cellWidth(face: SkTypeface, size: number, ch: string): number {
  const font = Skia.Font(face, size);
  const ids = font.getGlyphIDs(ch);
  return font.getGlyphWidths(ids)[0] ?? size * 0.6;
}
