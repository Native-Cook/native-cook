import { type SkFont, type SkPicture, Skia, BlendMode, BlurStyle, PaintStyle, StrokeCap } from '@shopify/react-native-skia';

import { DAY, H_MAX, H_MIN, NOW, SUNRISE, SUNSET, TIDE_HIGH_AT, TIDE_PERIOD, hourToX, levelToY, type Plot } from './tideGaugeMotion';

/*
 Static layers of the chart card, recorded once per size into Pictures: the card's backdrop
 (night bands before sunrise and after sunset, grid, axis labels, sun markers, the high and low
 tags) and the buoy, drawn at the origin so a per-frame transform moves it.
*/

export const INK = '#E6F0F3';
export const INK_SOFT = 'rgba(200,222,230,0.62)';
export const INK_FAINT = 'rgba(200,222,230,0.16)';
const NIGHT = 'rgba(3,10,18,0.32)';
const SUN = '#F6C46A';

function textWidth(font: SkFont, text: string) {
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  let w = 0;
  for (const v of widths) w += v;
  return w;
}

function sunMark(canvas: ReturnType<ReturnType<typeof Skia.PictureRecorder>['beginRecording']>, x: number, y: number, rising: boolean) {
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(SUN));
  // Half a sun on a horizon line, with five short rays and an arrow for up or down.
  const half = Skia.PathBuilder.Make();
  half.addArc(Skia.XYWHRect(x - 6, y - 6, 12, 12), 180, 180);
  half.close();
  canvas.drawPath(half.build(), paint);
  const stroke = Skia.Paint();
  stroke.setAntiAlias(true);
  stroke.setStyle(PaintStyle.Stroke);
  stroke.setStrokeWidth(1.4);
  stroke.setStrokeCap(StrokeCap.Round);
  stroke.setColor(Skia.Color(SUN));
  for (let i = 0; i < 5; i += 1) {
    const a = Math.PI + (Math.PI * (i + 0.5)) / 5;
    canvas.drawLine(x + Math.cos(a) * 8.5, y + Math.sin(a) * 8.5, x + Math.cos(a) * 11, y + Math.sin(a) * 11, stroke);
  }
  stroke.setColor(Skia.Color('rgba(246,196,106,0.55)'));
  canvas.drawLine(x - 12, y + 0.5, x + 12, y + 0.5, stroke);
  const arrow = Skia.PathBuilder.Make();
  const dir = rising ? -1 : 1;
  arrow.moveTo(x - 2.5, y + 5 - dir * 1.5);
  arrow.lineTo(x, y + 5 + dir * 1.5);
  arrow.lineTo(x + 2.5, y + 5 - dir * 1.5);
  stroke.setColor(Skia.Color(SUN));
  stroke.setStrokeWidth(1.2);
  canvas.drawPath(arrow.build(), stroke);
}

export function recordBackdrop(width: number, height: number, plot: Plot, small: SkFont, tiny: SkFont): SkPicture {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
  const paint = Skia.Paint();
  paint.setAntiAlias(true);

  // Night before sunrise and after sunset.
  paint.setColor(Skia.Color(NIGHT));
  canvas.drawRect(Skia.XYWHRect(plot.x, plot.y, hourToX(SUNRISE, plot) - plot.x, plot.h), paint);
  canvas.drawRect(Skia.XYWHRect(hourToX(SUNSET, plot), plot.y, plot.x + plot.w - hourToX(SUNSET, plot), plot.h), paint);

  // Level lines every metre, labelled at the right edge.
  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeWidth(1);
  line.setColor(Skia.Color(INK_FAINT));
  const label = Skia.Paint();
  label.setAntiAlias(true);
  label.setColor(Skia.Color(INK_SOFT));
  for (let m = 0; m <= Math.floor(H_MAX); m += 1) {
    if (m < H_MIN) continue;
    const y = levelToY(m, plot);
    canvas.drawLine(plot.x, y, plot.x + plot.w, y, line);
    const text = `${m} m`;
    canvas.drawText(text, plot.x + plot.w - textWidth(tiny, text) - 4, y - 4, label, tiny);
  }

  // Hour ticks every 3 h, labels every 6 h.
  line.setColor(Skia.Color('rgba(200,222,230,0.10)'));
  for (let h = 0; h <= DAY; h += 3) {
    const x = hourToX(h, plot);
    canvas.drawLine(x, plot.y, x, plot.y + plot.h, line);
    if (h % 6 === 0) {
      const text = `${h < 10 ? '0' : ''}${h}:00`;
      const w = textWidth(small, text);
      const tx = Math.min(Math.max(x - w / 2, plot.x), plot.x + plot.w - w);
      canvas.drawText(text, tx, plot.y + plot.h + 18, label, small);
    }
  }

  // Sunrise and sunset under the axis.
  sunMark(canvas, hourToX(SUNRISE, plot), plot.y + plot.h + 32, true);
  sunMark(canvas, hourToX(SUNSET, plot), plot.y + plot.h + 32, false);
  const sr = '06:12';
  const ss = '18:47';
  canvas.drawText(sr, hourToX(SUNRISE, plot) + 15, plot.y + plot.h + 36, label, tiny);
  canvas.drawText(ss, hourToX(SUNSET, plot) - 15 - textWidth(tiny, ss), plot.y + plot.h + 36, label, tiny);

  // "Now" marker above the plot.
  const nx = hourToX(NOW, plot);
  const tag = 'NOW 10:40';
  const tw = textWidth(tiny, tag);
  const pill = Skia.RRectXY(Skia.XYWHRect(nx - tw / 2 - 7, plot.y - 24, tw + 14, 16), 8, 8);
  paint.setColor(Skia.Color('rgba(230,240,243,0.10)'));
  canvas.drawRRect(pill, paint);
  label.setColor(Skia.Color(INK));
  canvas.drawText(tag, nx - tw / 2, plot.y - 12.5, label, tiny);
  line.setColor(Skia.Color('rgba(230,240,243,0.28)'));
  line.setPathEffect(Skia.PathEffect.MakeDash([2, 3], 0));
  canvas.drawLine(nx, plot.y - 8, nx, plot.y + plot.h, line);
  line.setPathEffect(null);

  // High and low tags just above the curve's turning points.
  label.setColor(Skia.Color(INK_SOFT));
  const tags: [number, string][] = [
    [TIDE_HIGH_AT - TIDE_PERIOD, 'H'],
    [TIDE_HIGH_AT - TIDE_PERIOD / 2, 'L'],
    [TIDE_HIGH_AT, 'H'],
    [TIDE_HIGH_AT + TIDE_PERIOD / 2, 'L'],
  ];
  for (const [hour, kind] of tags) {
    if (hour < 0.5 || hour > DAY - 0.5) continue;
    const x = hourToX(hour, plot);
    const y = levelToY(kind === 'H' ? 2.4 : 0.3, plot) - 14;
    const text = kind === 'H' ? 'H 2.4' : 'L 0.3';
    const w = textWidth(tiny, text);
    canvas.drawText(text, x - w / 2, y, label, tiny);
  }
  return recorder.finishRecordingAsPicture();
}

/** The buoy at the origin: waterline at y = 0, mast up, light at (0, -31). */
export function recordBuoy(): SkPicture {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(-30, -50, 60, 70));
  const p = Skia.Paint();
  p.setAntiAlias(true);

  // Shadow ring on the water.
  p.setColor(Skia.Color('rgba(4,24,40,0.45)'));
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.5, true));
  canvas.drawOval(Skia.XYWHRect(-17, -2, 34, 7), p);
  p.setMaskFilter(null);

  // Hull: a rounded float, red with a white band, lit from the upper left.
  const hull = Skia.PathBuilder.Make();
  hull.moveTo(-13, 1);
  hull.cubicTo(-13, -9, -8, -13, 0, -13);
  hull.cubicTo(8, -13, 13, -9, 13, 1);
  hull.cubicTo(8, 4, -8, 4, -13, 1);
  hull.close();
  p.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: -13, y: -13 },
      { x: 13, y: 3 },
      [Skia.Color('#FF8A5C'), Skia.Color('#E5462B'), Skia.Color('#8E2117')],
      [0, 0.5, 1],
      0,
    ),
  );
  canvas.drawPath(hull.build(), p);
  p.setShader(null);
  const band = Skia.PathBuilder.Make();
  band.addRect(Skia.XYWHRect(-14, -8, 28, 3.5));
  canvas.save();
  canvas.clipPath(hull.build(), 1, true);
  p.setColor(Skia.Color('rgba(244,240,232,0.92)'));
  canvas.drawPath(band.build(), p);
  canvas.restore();
  // Specular on the hull.
  p.setColor(Skia.Color('rgba(255,236,226,0.55)'));
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.2, true));
  canvas.drawOval(Skia.XYWHRect(-9, -11.5, 6, 3.5), p);
  p.setMaskFilter(null);

  // Mast, cage and lamp housing.
  p.setColor(Skia.Color('#2B3540'));
  canvas.drawRect(Skia.XYWHRect(-1.2, -27, 2.4, 14), p);
  const cage = Skia.Paint();
  cage.setAntiAlias(true);
  cage.setStyle(PaintStyle.Stroke);
  cage.setStrokeWidth(1.2);
  cage.setColor(Skia.Color('#3A4652'));
  canvas.drawLine(-6, -13, -1, -27, cage);
  canvas.drawLine(6, -13, 1, -27, cage);
  p.setColor(Skia.Color('#E9E2D2'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-3.5, -34, 7, 7), 2, 2), p);
  p.setColor(Skia.Color('#FFD27A'));
  p.setBlendMode(BlendMode.SrcOver);
  canvas.drawCircle(0, -30.5, 2.2, p);
  return recorder.finishRecordingAsPicture();
}
