import type { Frame } from './peak-contract';

export type NumberMetrics = { ids: number[]; widths: number[]; cell: number };
export type PositionedGlyph = { id: number; pos: { x: number; y: number } };

/** Smooth finite interpolation shared by native type and the offline proof. */
export function softRange(v: number, lo: number, hi: number) {
  'worklet';
  const a = Math.max(0, Math.min(1, (v - lo) / (hi - lo)));
  return a * a * (3 - 2 * a);
}

/** Projects a plane around (201,416); positive Y tilt increases depth on the right. */
export function peakMatrix(f: Frame): number[] {
  'worklet';
  const sx = Math.sin(f.tiltX), cx = Math.cos(f.tiltX);
  const sy = Math.sin(f.tiltY), cy = Math.cos(f.tiltY);
  const s = f.cardScale;
  const a = cy * s, b = sy * sx * s, e = cx * s;
  const g = sy * s / 900, h = -cy * sx * s / 900;
  return [a + 201 * g, b + 201 * h, 201 - (a + 201 * g) * 201 - (b + 201 * h) * 416,
    416 * g, e + 416 * h, 416 - 416 * g * 201 - (e + 416 * h) * 416,
    g, h, 1 - g * 201 - h * 416];
}

/** Centers fixed-width digit cells while their leading slots grow continuously. */
export function numberLayout(value: number, metrics: NumberMetrics) {
  'worklet';
  const v = Math.max(0, Math.min(999, value));
  const rounded = Math.round(v);
  const tens = softRange(v, 8, 12), hundreds = softRange(v, 90, 110);
  const total = metrics.cell * (1 + tens + hundreds) + metrics.widths[10] - 2;
  const left = 201 - total / 2;
  const cells = [hundreds, tens, 1];
  const digits = [Math.floor(rounded / 100), Math.floor(rounded / 10) % 10, rounded % 10];
  const glyphs: PositionedGlyph[][] = [];
  const alpha: number[] = [];
  const all: PositionedGlyph[] = [];
  let x = left;
  for (let i = 0; i < 3; i++) {
    const visible = i === 2 || (i === 0 ? rounded >= 100 : rounded >= 10);
    const glyph = { id: metrics.ids[digits[i]], pos: { x: x + (metrics.cell * cells[i] - metrics.widths[digits[i]]) / 2, y: 576 } };
    glyphs.push(visible ? [glyph] : []);
    alpha.push(visible ? (i === 0 ? softRange(v, 99.5, 100) : i === 1 ? softRange(v, 9.5, 10) : 1) : 0);
    if (visible) all.push(glyph);
    x += metrics.cell * cells[i];
  }
  const percent = { id: metrics.ids[10], pos: { x: x - 2, y: 576 } };
  glyphs.push([percent]);
  all.push(percent);
  alpha.push(1);
  return { glyphs, alpha, all };
}

/** Gives the old/new label rolls an overlapping, restrained six- or eight-point travel. */
export function labelRoll(mix: number, travel: number) {
  'worklet';
  return { oldAlpha: 1 - softRange(mix, 0, .6), newAlpha: softRange(mix, .4, 1), oldY: -travel * mix, newY: travel * (1 - mix) };
}
