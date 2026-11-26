import type { SkCanvas, SkFont, SkPaint, SkTypeface } from '@shopify/react-native-skia';
import { Skia } from '@shopify/react-native-skia';

import type { Frame } from './stt-motion';

/**
 * Everything over the shader: the copy, the timer, the best chip and the pill, as one picture per frame. Numbers are
 * the designer's (`ref.html` and the spec in #54) in the 393 × 852 design: text centred across (`ox`), vertical
 * positions × `sy`, sizes kept. Lines that change with the time are laid out once for 0.0–9.9 s.
 */

export type Line = { ids: number[]; xs: number[]; width: number };
export type Ui = {
  eyebrow: Line;
  /** By tenths of a second, 0.0–9.9: the timer, the line while it is up, the line once down, the best chip. */
  timer: Line[];
  keep: Line[];
  downLine: Line[];
  best: Line[];
  unit: Line;
  pill: Line;
  fonts: { eyebrow: SkFont; timer: SkFont; unit: SkFont; line: SkFont; chip: SkFont; pill: SkFont };
};
export type Layout = { ox: number; sy: number; w: number; h: number };

export const CX = 196.5;
export const PILL = { y: 799, w: 329, h: 58 };
export const CHIP = { x: 139, y: 700, w: 115, h: 30 };
export const TENTHS = 100;

/**
 * Glyphs of `text` from x = 0 with `tracking` after every glyph (as a Canvas 2D `letterSpacing` does), placed as the
 * reference's browser shapes them: with the font's kerning. The kerned positions come from a Skia paragraph laid out
 * once; without one, the raw advances. `typeface` is the one the font was made from: Skia 2.6.2's `font.getTypeface()`
 * returns it without taking a reference, so each wrapper it hands out frees the typeface once when collected.
 */
export function setLine(text: string, font: SkFont, typeface: SkTypeface, tracking = 0): Line {
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  if (ids.length === text.length) {
    const fonts = Skia.TypefaceFontProvider.Make();
    fonts.registerFont(typeface, 'Line');
    const builder = Skia.ParagraphBuilder.Make({}, fonts);
    builder.pushStyle({ fontFamilies: ['Line'], fontSize: font.getSize(), letterSpacing: tracking });
    builder.addText(text);
    const para = builder.build();
    para.layout(1e5);
    const xs = ids.map((_, i) => para.getRectsForRange(i, i + 1)[0]?.x ?? Number.NaN);
    if (xs.every((x) => Number.isFinite(x))) return { ids, xs: xs.map((x) => x - xs[0]), width: para.getMaxIntrinsicWidth() };
  }
  let x = 0;
  const xs = ids.map((_, i) => {
    const at = x;
    x += widths[i] + tracking;
    return at;
  });
  return { ids, xs, width: x };
}

function drawLine(canvas: SkCanvas, line: Line, font: SkFont, x: number, y: number, paint: SkPaint) {
  'worklet';
  canvas.drawGlyphs(line.ids, line.xs.map((v) => ({ x: v, y: 0 })), x, y, font, paint);
}

export function tenthsOf(secs: number) {
  'worklet';
  return Math.min(TENTHS - 1, Math.max(0, Math.round(secs * 10)));
}

export function drawUi(canvas: SkCanvas, F: Frame, ui: Ui, L: Layout) {
  'worklet';
  const { ox, sy } = L;
  const cx = ox + CX;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);

  paint.setColor(Skia.Color('#3A322C'));
  drawLine(canvas, ui.eyebrow, ui.fonts.eyebrow, cx - ui.eyebrow.width / 2, 90 * sy, paint);

  // The timer, centred at x 184.5, and its 's' 4 pt after it.
  const n = tenthsOf(F.secs);
  const timer = ui.timer[n];
  const tx = ox + 184.5;
  paint.setColor(Skia.Color('#17130F'));
  drawLine(canvas, timer, ui.fonts.timer, tx - timer.width / 2, 172 * sy, paint);
  drawLine(canvas, ui.unit, ui.fonts.unit, tx + timer.width / 2 + 4, 172 * sy, paint);

  const line = F.down > 0 ? ui.downLine[n] : ui.keep[tenthsOf(F.best)];
  paint.setColor(Skia.Color('#2F2925'));
  drawLine(canvas, line, ui.fonts.line, cx - line.width / 2, 214 * sy, paint);

  // Best chip, popping when beaten.
  const chipY = CHIP.y * sy;
  canvas.save();
  canvas.translate(ox + CHIP.x + CHIP.w / 2, chipY + CHIP.h / 2);
  canvas.scale(F.bestPop, F.bestPop);
  paint.setColor(Skia.Color('rgba(255,255,255,0.5)'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-CHIP.w / 2, -CHIP.h / 2, CHIP.w, CHIP.h), 15, 15), paint);
  const best = ui.best[tenthsOf(F.best)];
  paint.setColor(Skia.Color('#2B2622'));
  drawLine(canvas, best, ui.fonts.chip, -best.width / 2, 19 - CHIP.h / 2, paint);
  canvas.restore();

  // The pill, pressed to 0.97.
  canvas.save();
  canvas.translate(cx, PILL.y * sy);
  if (F.pill > 0) canvas.scale(0.97, 0.97);
  paint.setColor(Skia.Color('#141210'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-PILL.w / 2, -PILL.h / 2, PILL.w, PILL.h), PILL.h / 2, PILL.h / 2), paint);
  paint.setColor(Skia.Color('#FFFFFF'));
  drawLine(canvas, ui.pill, ui.fonts.pill, -ui.pill.width / 2, 6, paint);
  canvas.restore();
}
