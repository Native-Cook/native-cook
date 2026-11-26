import type { SkCanvas, SkFont, SkPaint, SkTypeface } from '@shopify/react-native-skia';
import { Skia } from '@shopify/react-native-skia';

import type { Frame } from './hds-motion';

/**
 * Everything over the shader: the copy, the number and the stepper, as one picture per frame. Numbers are the
 * designer's (`ref.html` and the spec in #52) in the 393 × 852 design: x kept, vertical positions × `sy`, sizes kept.
 */

export type Line = { ids: number[]; xs: number[]; width: number };
export type Ui = {
  eyebrow: Line;
  how: Line;
  sweet: Line;
  hint: Line;
  unit: Line;
  minus: Line;
  plus: Line;
  /** Anton glyphs for '0'…'9'. */
  digits: number[];
  fonts: { eyebrow: SkFont; headline: SkFont; number: SkFont; hint: SkFont; unit: SkFont; sign: SkFont };
};
export type Layout = { sy: number; w: number; h: number };

export const STEPPER = { x: 239, y: 776, w: 122, h: 58 };
/** The saucer's top in the design (image y 1075 → 10 + 1075 × 520 / 1024 pt): the drag area ends there. */
export const SAUCER_TOP = 556;

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

export function drawUi(canvas: SkCanvas, F: Frame, ui: Ui, L: Layout) {
  'worklet';
  const { sy } = L;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);

  paint.setColor(Skia.Color('#FFE3C7'));
  drawLine(canvas, ui.eyebrow, ui.fonts.eyebrow, 28, 318 * sy, paint);
  paint.setColor(Skia.Color('#FFF6EA'));
  drawLine(canvas, ui.how, ui.fonts.headline, 26, 376 * sy, paint);
  drawLine(canvas, ui.sweet, ui.fonts.headline, 26, 431 * sy, paint);

  // The number, scaled about its baseline-left while it pops.
  canvas.save();
  canvas.translate(30, 804 * sy);
  canvas.scale(F.pop, F.pop);
  canvas.drawGlyphs([ui.digits[F.n]], [{ x: 0, y: 0 }], 0, 0, ui.fonts.number, paint);
  canvas.restore();

  paint.setColor(Skia.Color('#FFD1A8'));
  drawLine(canvas, ui.hint, ui.fonts.hint, 104, 760 * sy, paint);
  paint.setColor(Skia.Color('#FFE3C7'));
  drawLine(canvas, ui.unit, ui.fonts.unit, 104, 786 * sy, paint);

  // The stepper: − and + centred at x 269 and 331, a divider between them.
  const top = STEPPER.y * sy;
  paint.setColor(Skia.Color('#FFF6EA'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(STEPPER.x, top, STEPPER.w, STEPPER.h), 29, 29), paint);
  paint.setColor(Skia.Color('#C23A00'));
  drawLine(canvas, ui.minus, ui.fonts.sign, 269 - ui.minus.width / 2, top + 38, paint);
  drawLine(canvas, ui.plus, ui.fonts.sign, 331 - ui.plus.width / 2, top + 38, paint);
  paint.setColor(Skia.Color('#F2C9A8'));
  canvas.drawRect(Skia.XYWHRect(299.5, top + 16, 1, 26), paint);
}
