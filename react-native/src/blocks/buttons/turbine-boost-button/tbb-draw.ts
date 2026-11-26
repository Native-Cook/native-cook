import type { SkCanvas, SkFont, SkPaint, SkTypeface } from '@shopify/react-native-skia';
import { BlurStyle, PaintStyle, Skia } from '@shopify/react-native-skia';

import type { Frame } from './tbb-motion';

/**
 * Everything over the shader: the copy, the percent and the pill, as one picture per frame. Numbers are the
 * designer's (`ref.html` and the spec in #50) in the 393 × 852 design: centred across (`ox`), vertical positions
 * × `sy`, sizes kept. Canvas 2D `shadowBlur` b is a Skia blur of sigma b / 2.
 */

export type Line = { ids: number[]; xs: number[]; width: number };
export type Glyphs = { ids: number[]; adv: number[] };
export type Ui = {
  eyebrow: Line;
  headline: Line;
  spool: Line;
  full: Line;
  rest: Line;
  boosting: Line;
  /** Inter Display Bold glyphs for '0'…'9' and '%'. */
  digits: Glyphs;
  fonts: { eyebrow: SkFont; headline: SkFont; sub: SkFont; percent: SkFont; label: SkFont };
};
export type Layout = { ox: number; sy: number; w: number; h: number };

export const CX = 196.5;
export const PILL = { y: 793, w: 329, h: 58 };
const PERCENT_TRACKING = -1;

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

export function setGlyphs(text: string, font: SkFont): Glyphs {
  const ids = font.getGlyphIDs(text);
  return { ids, adv: font.getGlyphWidths(ids) };
}

function drawLine(canvas: SkCanvas, line: Line, font: SkFont, cx: number, y: number, paint: SkPaint) {
  'worklet';
  canvas.drawGlyphs(line.ids, line.xs.map((x) => ({ x, y: 0 })), cx - line.width / 2, y, font, paint);
}

/** `64%`: glyphs 0–9 are the digits, 10 the percent sign. */
function drawPercent(canvas: SkCanvas, pct: number, g: Glyphs, font: SkFont, cx: number, y: number, paint: SkPaint) {
  'worklet';
  const ids: number[] = [];
  const xs: number[] = [];
  let x = 0;
  const s = `${pct}`;
  for (let i = 0; i <= s.length; i++) {
    const k = i < s.length ? s.charCodeAt(i) - 48 : 10;
    ids.push(g.ids[k]);
    xs.push(x);
    x += g.adv[k] + PERCENT_TRACKING;
  }
  canvas.drawGlyphs(ids, xs.map((v) => ({ x: v, y: 0 })), cx - x / 2, y, font, paint);
}

export function drawUi(canvas: SkCanvas, F: Frame, ui: Ui, L: Layout) {
  'worklet';
  const { ox, sy } = L;
  const cx = ox + CX;
  const b = F.B;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);

  paint.setColor(Skia.Color('#6F9BFF'));
  drawLine(canvas, ui.eyebrow, ui.fonts.eyebrow, cx, 92 * sy, paint);

  // BOOST, with its blue glow.
  if (b > 0.002) {
    const glow = Skia.Paint();
    glow.setAntiAlias(true);
    glow.setColor(Skia.Color('rgb(61,123,255)'));
    glow.setAlphaf(0.9 * b);
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 15 * b, true));
    drawLine(canvas, ui.headline, ui.fonts.headline, cx, 166 * sy, glow);
  }
  paint.setColor(Skia.Color('#FFFFFF'));
  drawLine(canvas, ui.headline, ui.fonts.headline, cx, 166 * sy, paint);

  paint.setColor(Skia.Color('#8EA6D6'));
  drawLine(canvas, b > 0.98 ? ui.full : ui.spool, ui.fonts.sub, cx, 204 * sy, paint);

  paint.setColor(Skia.Color('#CFE0FF'));
  drawPercent(canvas, Math.round(100 * F.s), ui.digits, ui.fonts.percent, cx, 726 * sy, paint);

  // The pill: pressed, it shrinks to 0.97 and lights up.
  const held = F.held > 0;
  canvas.save();
  canvas.translate(cx, PILL.y * sy);
  if (held) canvas.scale(0.97, 0.97);
  const pill = Skia.RRectXY(Skia.XYWHRect(-PILL.w / 2, -PILL.h / 2, PILL.w, PILL.h), PILL.h / 2, PILL.h / 2);
  paint.setColor(Skia.Color(held ? '#1A3BA8' : '#0E1A36'));
  canvas.drawRRect(pill, paint);
  const stroke = Skia.Paint();
  stroke.setAntiAlias(true);
  stroke.setStyle(PaintStyle.Stroke);
  stroke.setStrokeWidth(1);
  stroke.setColor(Skia.Color(held ? '#5C8DFF' : '#2B4A8F'));
  canvas.drawRRect(pill, stroke);
  const dot = Skia.Paint();
  dot.setAntiAlias(true);
  dot.setColor(Skia.Color('#3D7BFF'));
  dot.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6 + 10 * b, true));
  canvas.drawCircle(-58, 0, 5, dot);
  paint.setColor(Skia.Color('#3D7BFF'));
  canvas.drawCircle(-58, 0, 5, paint);
  paint.setColor(Skia.Color('#E4EDFF'));
  drawLine(canvas, held ? ui.boosting : ui.rest, ui.fonts.label, 8, 6, paint);
  canvas.restore();
}
