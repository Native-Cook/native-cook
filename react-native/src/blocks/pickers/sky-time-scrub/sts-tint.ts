/**
 * Sky tint: what the sky looks like at 16 elevations, so the UI can be lit by it without reading a pixel back from the GPU.
 * Measured from the exact shader with `.work/sky-time-scrub/preview/tint-table.cjs` (zenith row, and the horizon row 8 pt
 * above the ground averaged away from the sun, steady-state exposure). Columns: elevation, zenith rgb, horizon rgb, sun rgb.
 */
const TINT = [
  -18, 27, 24, 47, 9, 8, 16, 224, 48, 26, -12, 28, 25, 50, 12, 11, 19, 224, 48, 26, -6, 19, 21, 45, 82, 57, 26, 224, 48, 26, -3, 28, 41, 57,
  114, 73, 24, 224, 48, 26, -1, 31, 44, 54, 116, 64, 18, 224, 48, 26, 0, 31, 43, 51, 115, 61, 16, 255, 106, 30, 1, 33, 46, 54, 118, 63, 16,
  255, 129, 41, 2, 36, 51, 58, 122, 68, 16, 255, 171, 63, 3, 37, 54, 59, 128, 77, 18, 255, 194, 74, 4.5, 38, 54, 58, 137, 94, 22, 255, 220,
  146, 6, 38, 54, 58, 145, 115, 27, 255, 246, 218, 10, 34, 48, 55, 153, 154, 40, 255, 246, 218, 18, 22, 33, 55, 106, 141, 141, 255, 246,
  218, 30, 20, 32, 61, 106, 153, 177, 255, 246, 218, 45, 21, 33, 64, 106, 154, 182, 255, 246, 218, 62, 21, 34, 66, 105, 154, 183, 255, 246,
  218,
];
const ROW = 10;
const COUNT = 16;

/** [zenith r,g,b, horizon r,g,b, sun r,g,b] in 0-255 for a sun elevation in degrees (clamped to -18..62). */
export function skyTintAt(elevation: number): number[] {
  'worklet';
  const first = TINT[0];
  const last = TINT[(COUNT - 1) * ROW];
  const e = Math.min(last, Math.max(first, elevation));
  let i = 0;
  while (i < COUNT - 2 && e > TINT[(i + 1) * ROW]) i++;
  const a = i * ROW;
  const b = a + ROW;
  const span = TINT[b] - TINT[a];
  const t = span > 0 ? (e - TINT[a]) / span : 0;
  const out: number[] = [];
  for (let k = 1; k < ROW; k++) out.push(TINT[a + k] + (TINT[b + k] - TINT[a + k]) * t);
  return out;
}
export function rgbString(r: number, g: number, b: number, alpha = 1) {
  'worklet';
  return `rgba(${Math.round(r)},${Math.round(g)},${Math.round(b)},${alpha})`;
}
function hex2(v: number) {
  'worklet';
  return Math.round(Math.min(255, Math.max(0, v)))
    .toString(16)
    .padStart(2, '0');
}
export function hexString(r: number, g: number, b: number) {
  'worklet';
  return `#${hex2(r)}${hex2(g)}${hex2(b)}`;
}
/** Perceived brightness 0-1 (gamma-encoded luma) of an sRGB colour given in 0-255. */
export function luminance(r: number, g: number, b: number) {
  'worklet';
  return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
}
