import type { SkCanvas, SkFont, SkPaint, SkTypeface } from '@shopify/react-native-skia';
import { BlurStyle, PaintStyle, Skia, StrokeJoin, TileMode } from '@shopify/react-native-skia';

import type { Frame } from './jsp-motion';

/**
 * Everything over the shader: the scrim, the copy and the player, as one picture per frame. Numbers are the
 * designer's (`ref.html` and the spec in #53) in the 393 × 852 design: centred across (`ox`), vertical positions
 * × `sy`, sizes kept. Canvas 2D `shadowBlur` b is a Skia blur of sigma b / 2.
 */

export type Line = { ids: number[]; xs: number[]; width: number };
export type Track = { title: Line; meta: Line };
export type Ui = {
  eyebrow: Line;
  headline: Line;
  tracks: Track[];
  start: Line;
  end: Line;
  fonts: { eyebrow: SkFont; headline: SkFont; title: SkFont; meta: SkFont; time: SkFont };
};
export type Layout = { ox: number; sy: number; w: number; h: number };

export const CX = 196.5;
export const TRACKS = [
  { name: 'Moon Jelly', meta: 'Ocean hum · 42 min · rain under it', colour: '#FF8AD8' },
  { name: 'Glass Tide', meta: 'Slow surf · 38 min · far gulls', colour: '#6FE6FF' },
  { name: 'Amber Drift', meta: 'Warm drone · 45 min · low wind', colour: '#FFB45A' },
];
const COLOURS = TRACKS.map((t) => t.colour);
export const PLAY = { x: 156.5, y: 776, w: 80, h: 56 };

/**
 * Glyphs of `text` from x = 0 with `tracking` after every glyph (as a Canvas 2D `letterSpacing` does), placed as the
 * reference's browser shapes them: with the font's kerning (raw advances put "Drift off." 3.4 pt wider). The kerned
 * positions come from a Skia paragraph laid out once; without one, the raw advances. `typeface` is the one the font was
 * made from: Skia 2.6.2's `font.getTypeface()` returns it without taking a reference, so each wrapper it hands out
 * frees the typeface once when collected.
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

function glowPaint(colour: string, blur: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(colour));
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur / 2, true));
  return p;
}

export function drawUi(canvas: SkCanvas, F: Frame, ui: Ui, L: Layout) {
  'worklet';
  const { ox, sy, w, h } = L;
  const cx = ox + CX;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);

  // Bottom scrim.
  const scrim = Skia.Paint();
  scrim.setShader(Skia.Shader.MakeLinearGradient(
    { x: 0, y: 540 * sy }, { x: 0, y: h },
    [Skia.Color('rgba(2,12,28,0)'), Skia.Color('rgba(2,12,28,0.88)'), Skia.Color('#020C1C')], [0, 0.45, 1], TileMode.Clamp,
  ));
  canvas.drawRect(Skia.XYWHRect(0, 540 * sy, w, h - 540 * sy), scrim);

  // Eyebrow in the track colour: the old and the new words crossfaded.
  const ey = cx - ui.eyebrow.width / 2;
  paint.setColor(Skia.Color(COLOURS[F.a]));
  paint.setAlphaf(1 - F.k);
  drawLine(canvas, ui.eyebrow, ui.fonts.eyebrow, ey, 90 * sy, paint);
  if (F.k > 0) {
    paint.setColor(Skia.Color(COLOURS[F.b]));
    paint.setAlphaf(F.k);
    drawLine(canvas, ui.eyebrow, ui.fonts.eyebrow, ey, 90 * sy, paint);
  }
  paint.setColor(Skia.Color('#FFF4FB'));
  drawLine(canvas, ui.headline, ui.fonts.headline, cx - ui.headline.width / 2, 170 * sy, paint);

  // Track title and meta: the old ones slide 30 pt left and fade, then the new ones slide in from 30 pt right.
  const drawTrack = (i: number, dx: number, alpha: number) => {
    paint.setColor(Skia.Color('#FFFFFF'));
    paint.setAlphaf(alpha);
    drawLine(canvas, ui.tracks[i].title, ui.fonts.title, ox + 32 + dx, 700 * sy, paint);
    paint.setColor(Skia.Color('#D7C6FF'));
    paint.setAlphaf(alpha);
    drawLine(canvas, ui.tracks[i].meta, ui.fonts.meta, ox + 32 + dx, 727 * sy, paint);
  };
  if (F.k > 0) {
    const k1 = Math.min(1, F.k * 2);
    const k2 = Math.max(0, F.k * 2 - 1);
    if (k1 < 1) drawTrack(F.a, -30 * k1, 1 - k1);
    if (k2 > 0) drawTrack(F.b, 30 * (1 - k2), k2);
  } else {
    drawTrack(F.a, 0, 1);
  }

  // Progress with a glow in the track colour.
  const cc = COLOURS[F.k > 0.5 ? F.b : F.a];
  paint.setColor(Skia.Color('rgba(255,255,255,0.18)'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(ox + 32, 748 * sy, 329, 3), 1.5, 1.5), paint);
  const fill = Skia.RRectXY(Skia.XYWHRect(ox + 32, 748 * sy, 329 * Math.max(0.02, F.prog), 3), 1.5, 1.5);
  canvas.drawRRect(fill, glowPaint(cc, 10));
  paint.setColor(Skia.Color(cc));
  canvas.drawRRect(fill, paint);

  paint.setColor(Skia.Color('#B7A8E0'));
  drawLine(canvas, ui.start, ui.fonts.time, ox + 32, 770 * sy, paint);
  drawLine(canvas, ui.end, ui.fonts.time, ox + 361 - ui.end.width, 770 * sy, paint);

  // Play / pause with a glow in the track colour.
  const top = PLAY.y * sy;
  const pill = Skia.RRectXY(Skia.XYWHRect(ox + PLAY.x, top, PLAY.w, PLAY.h), 28, 28);
  canvas.drawRRect(pill, glowPaint(cc, 34));
  paint.setColor(Skia.Color('#FFF4FB'));
  canvas.drawRRect(pill, paint);
  paint.setColor(Skia.Color('#2A0F3D'));
  if (F.playing > 0) {
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(ox + 187, top + 18, 6, 20), 2, 2), paint);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(ox + 197, top + 18, 6, 20), 2, 2), paint);
  } else {
    // Play: a rounded triangle, 18 × 22, its tip at (205.5, 804) (the designer's answer 2).
    const tri = Skia.PathBuilder.Make()
      .moveTo(ox + 187.5, top + 17).lineTo(ox + 205.5, top + 28).lineTo(ox + 187.5, top + 39).close().build();
    const round = Skia.Paint();
    round.setAntiAlias(true);
    round.setColor(Skia.Color('#2A0F3D'));
    round.setStyle(PaintStyle.Stroke);
    round.setStrokeWidth(3);
    round.setStrokeJoin(StrokeJoin.Round);
    canvas.drawPath(tri, paint);
    canvas.drawPath(tri, round);
  }

  // Previous and next.
  paint.setColor(Skia.Color('rgba(255,255,255,0.9)'));
  const y0 = top + 16;
  const prev = Skia.PathBuilder.Make().moveTo(ox + 108, y0).lineTo(ox + 108, y0 + 26).lineTo(ox + 94, y0 + 13).close().build();
  const next = Skia.PathBuilder.Make().moveTo(ox + 285, y0).lineTo(ox + 285, y0 + 26).lineTo(ox + 299, y0 + 13).close().build();
  canvas.drawPath(prev, paint);
  canvas.drawPath(next, paint);
  canvas.drawRect(Skia.XYWHRect(ox + 89, y0, 2.5, 26), paint);
  canvas.drawRect(Skia.XYWHRect(ox + 301.5, y0, 2.5, 26), paint);
}
