import type { SkCanvas, SkFont, SkPaint, SkTypeface } from '@shopify/react-native-skia';
import { Skia } from '@shopify/react-native-skia';

import type { Frame } from './ppp-motion';

/**
 * Everything over the shader: the title, the percent, the meta line and the bar, as one picture per frame. Numbers are
 * the designer's (`ref.html` and the spec in #55) in the 393 × 852 design: x kept, vertical positions × `sy`, sizes
 * kept. The percent and the time left are laid out once for every value.
 */

export type Line = { ids: number[]; xs: number[]; width: number };
export type Ui = {
  folding: Line;
  exportLine: Line;
  exported: Line;
  /** 0 %…100 %. */
  percent: Line[];
  /** 'N s left' for N = 0…25, and the saved line. */
  left: Line[];
  saved: Line;
  fonts: { title: SkFont; percent: SkFont; meta: SkFont };
};
export type Layout = { sy: number; w: number; h: number };

export const BAR = { x: 30, y: 806, w: 333, h: 4 };
export const SECONDS = 25;

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

  paint.setColor(Skia.Color('#2B2420'));
  if (F.done > 0) {
    drawLine(canvas, ui.exported, ui.fonts.title, 30, 140 * sy, paint);
  } else {
    drawLine(canvas, ui.folding, ui.fonts.title, 30, 140 * sy, paint);
    drawLine(canvas, ui.exportLine, ui.fonts.title, 30, 199 * sy, paint);
  }

  paint.setColor(Skia.Color('#E0613F'));
  drawLine(canvas, ui.percent[Math.round(100 * F.pct)], ui.fonts.percent, 30, 754 * sy, paint);

  paint.setColor(Skia.Color('#7B6E64'));
  const left = Math.max(0, Math.min(SECONDS, Math.ceil((1 - F.pct) * SECONDS)));
  drawLine(canvas, F.done > 0 ? ui.saved : ui.left[left], ui.fonts.meta, 30, 786 * sy, paint);

  const y = BAR.y * sy;
  paint.setColor(Skia.Color('rgba(43,36,32,0.12)'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(BAR.x, y, BAR.w, BAR.h), 2, 2), paint);
  if (F.pct > 0) {
    paint.setColor(Skia.Color('#E0613F'));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(BAR.x, y, BAR.w * F.pct, BAR.h), 2, 2), paint);
  }
}
