import { clamp } from './pjg-motion';
// Scalar packing into alpha is invariant under sRGB/P3 conversion and premultiplication.
export function packRows(rows: number[][], low: number, high: number) {
  'worklet';
  const width = rows[0].length * 2,
    height = rows.length;
  const bytes = new Uint8Array(width * height * 4);
  for (let y = 0; y < height; y++)
    for (let x = 0; x < rows[y].length; x++) {
      const encoded = Math.round(clamp((rows[y][x] - low) / (high - low)) * 65535);
      const i = (y * width + 2 * x) * 4;
      bytes[i + 3] = encoded >> 8;
      bytes[i + 7] = encoded & 255;
    }
  return { width, height, bytes };
}
