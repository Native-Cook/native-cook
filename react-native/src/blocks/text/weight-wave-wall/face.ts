import { Skia, type SkFont, type SkPath } from '@shopify/react-native-skia';

/** Weight steps from 100 to 900, 25 apart. */
export const LEVELS = 33;
const UNIT = 100;

export type Wall = {
  /** Per weight step, per letter of the word: the outline (baseline at y = 0) and the advance. */
  paths: SkPath[][];
  advances: number[][];
  size: number;
  xHeight: number;
  /** Row to row. */
  pitch: number;
  /** The room one word gets at its heaviest, the gap between the two words and the side margin. */
  column: number;
  gap: number;
  margin: number;
};

/**
 * The word's glyphs at each weight step, sized so two words of the heaviest weight fit a row.
 *
 * React Native Skia cannot set a variable font's axes, so the block ships Epilogue's three masters
 * (100, 400 and 900) as static fonts. Epilogue has no other masters and no avar table, so a weight
 * between two masters is exactly the linear blend of their outlines and advances, which is what
 * `SkPath.interpolate` computes. `thin`, `regular` and `black` are the masters at 100 pt.
 */
export function layoutWall(thin: SkFont, regular: SkFont, black: SkFont, word: string, width: number): Wall {
  const chars = Array.from(word);
  const glyphs = (font: SkFont) =>
    chars.map((ch) => ({
      path: Skia.Path.MakeFromText(ch, 0, 0, font) ?? Skia.Path.Make(),
      advance: font.getGlyphWidths(font.getGlyphIDs(ch))[0],
    }));
  const masters = [glyphs(thin), glyphs(regular), glyphs(black)];
  const heavyWidth = masters[2].reduce((sum, g) => sum + g.advance, 0);
  // Two heaviest words and a 0.2 em gap fill the width between 16 pt margins.
  const size = (width - 32) / ((2 * heavyWidth) / UNIT + 0.2);
  const scale = size / UNIT;
  const xHeight = (Skia.Path.MakeFromText('x', 0, 0, regular)?.computeTightBounds().height ?? 53) * scale;

  const paths: SkPath[][] = [];
  const advances: number[][] = [];
  for (let level = 0; level < LEVELS; level++) {
    const weight = 100 + (800 * level) / (LEVELS - 1);
    // Between which two masters, and how far along.
    const [from, to, t] = weight <= 400 ? [0, 1, (weight - 100) / 300] : [1, 2, (weight - 400) / 500];
    paths.push(
      chars.map((_, i) => {
        const a = masters[from][i].path;
        const b = masters[to][i].path;
        // interpolate(end, w) = this · w + end · (1 − w)
        const mixed = b.isInterpolatable(a) ? (b.interpolate(a, t) ?? (t < 0.5 ? a : b)) : t < 0.5 ? a : b;
        return mixed.copy().transform(Skia.Matrix().scale(scale, scale));
      }),
    );
    advances.push(chars.map((_, i) => (masters[from][i].advance + (masters[to][i].advance - masters[from][i].advance) * t) * scale));
  }
  return { paths, advances, size, xHeight, pitch: xHeight / 0.7, column: (heavyWidth / UNIT) * size, gap: 0.2 * size, margin: 16 };
}
