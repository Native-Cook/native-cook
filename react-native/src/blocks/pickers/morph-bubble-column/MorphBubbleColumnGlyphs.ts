import { Skia, type SkPath } from '@shopify/react-native-skia';

// A small dot-matrix alphabet, seven pixels high, for the clock on the column screen. Each row is a
// string of '1' (lit) and '0' (dark); a glyph may be narrower than five pixels.
export const PIXEL_GLYPHS: Record<string, string[]> = {
  '0': ['01110', '10001', '10011', '10101', '11001', '10001', '01110'],
  '1': ['00100', '01100', '00100', '00100', '00100', '00100', '01110'],
  '2': ['01110', '10001', '00001', '00010', '00100', '01000', '11111'],
  '3': ['11110', '00001', '00001', '01110', '00001', '00001', '11110'],
  '4': ['00010', '00110', '01010', '10010', '11111', '00010', '00010'],
  '5': ['11111', '10000', '11110', '00001', '00001', '10001', '01110'],
  '6': ['00110', '01000', '10000', '11110', '10001', '10001', '01110'],
  '7': ['11111', '00001', '00010', '00100', '01000', '01000', '01000'],
  '8': ['01110', '10001', '10001', '01110', '10001', '10001', '01110'],
  '9': ['01110', '10001', '10001', '01111', '00001', '00010', '01100'],
  '+': ['00000', '00100', '00100', '11111', '00100', '00100', '00000'],
  '-': ['00000', '00000', '00000', '11111', '00000', '00000', '00000'],
  ':': ['0', '1', '1', '0', '1', '1', '0'],
  P: ['11110', '10001', '10001', '11110', '10000', '10000', '10000'],
  M: ['10001', '11011', '10101', '10101', '10001', '10001', '10001'],
  ' ': ['000', '000', '000', '000', '000', '000', '000'],
};

// A pixel globe, seven by seven, like the one beside the clock in the reference.
export const PIXEL_GLOBE = ['0011100', '0101010', '1111111', '1010101', '1111111', '0101010', '0011100'];

/**
 * The lit pixels of `rows` as filled squares, `px` wide with `gap` between them, its top left at
 * (x, y). Built once on the JS side for the static clock and globe.
 */
export function pixelBlockPath(rows: string[][], x: number, y: number, px: number, gap: number): SkPath {
  const builder = Skia.PathBuilder.Make();
  let left = x;
  for (const glyph of rows) {
    for (let r = 0; r < glyph.length; r += 1) {
      for (let c = 0; c < glyph[r].length; c += 1) {
        if (glyph[r][c] !== '1') continue;
        builder.addRect(Skia.XYWHRect(left + c * px + gap / 2, y + r * px + gap / 2, px - gap, px - gap));
      }
    }
    left += (glyph[0].length + 1) * px;
  }
  return builder.build();
}

/** The glyph rows for each character of `text`, for `pixelBlockPath`. */
export function pixelGlyphs(text: string) {
  return text.split('').map((ch) => PIXEL_GLYPHS[ch] ?? PIXEL_GLYPHS[' ']);
}
