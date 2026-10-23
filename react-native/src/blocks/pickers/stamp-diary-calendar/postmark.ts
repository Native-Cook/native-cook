import { BlurStyle, matchFont, PaintStyle, Skia, StrokeCap, type SkFont, type SkImage, type SkPaint, type SkCanvas } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

import { MONTH_NAMES, POSTMARK } from './theme';

const FAMILY = Platform.select(POSTMARK.font);

/**
 * Draws a postmark once per day into a small CPU image: a double ring with "MAY · 2026" and
 * "STAMP DIARY" around it, the day in the middle and four wavy cancellation lines to its left.
 * Only the alpha is used; strokes carry a baked soft edge so the shader can thin them (fresh,
 * crisp ink) or thicken them (ink bleeding into the paper) by moving one threshold.
 */
function glyphWidth(font: SkFont, text: string): number {
  return font.getGlyphWidths(font.getGlyphIDs(text)).reduce((sum, w) => sum + w, 0);
}

function arcText(canvas: SkCanvas, text: string, radius: number, centerAngle: number, font: SkFont, paint: SkPaint, bottom: boolean) {
  const P = POSTMARK;
  const widths = Array.from(text).map((ch) => glyphWidth(font, ch));
  const tracking = 2;
  const total = widths.reduce((s, w) => s + w + tracking, -tracking);
  // Arc length → angle. Top text runs clockwise, bottom text counter-clockwise, both upright.
  let along = -total / 2;
  Array.from(text).forEach((ch, i) => {
    const mid = along + widths[i] / 2;
    const angle = centerAngle + (bottom ? -1 : 1) * (mid / radius);
    canvas.save();
    canvas.translate(P.ringX + Math.cos(angle) * radius, P.ringY + Math.sin(angle) * radius);
    canvas.rotate(((angle + (bottom ? -Math.PI / 2 : Math.PI / 2)) * 180) / Math.PI, 0, 0);
    const capHalf = font.getSize() * 0.36;
    canvas.drawText(ch, -widths[i] / 2, capHalf, paint, font);
    canvas.restore();
    along += widths[i] + tracking;
  });
}

export function makePostmark(day: number, month: number, year: number): SkImage | null {
  const P = POSTMARK;
  const surface = Skia.Surface.Make(P.width, P.height);
  if (!surface) return null;
  const canvas = surface.getCanvas();
  const soft = Skia.MaskFilter.MakeBlur(BlurStyle.Normal, P.softness, true);

  const stroke = Skia.Paint();
  stroke.setAntiAlias(true);
  stroke.setColor(Skia.Color('#000000'));
  stroke.setStyle(PaintStyle.Stroke);
  stroke.setStrokeCap(StrokeCap.Round);
  stroke.setMaskFilter(soft);

  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setColor(Skia.Color('#000000'));
  fill.setMaskFilter(soft);

  stroke.setStrokeWidth(P.outerStroke);
  canvas.drawCircle(P.ringX, P.ringY, P.outer, stroke);
  stroke.setStrokeWidth(P.innerStroke);
  canvas.drawCircle(P.ringX, P.ringY, P.inner, stroke);

  const arcFont = matchFont({ fontFamily: FAMILY, fontSize: P.arcFont, fontWeight: 'bold' });
  const textRadius = (P.outer + P.inner) / 2;
  const top = MONTH_NAMES[month - 1].slice(0, 3).toUpperCase() + ' · ' + year;
  arcText(canvas, top, textRadius, -Math.PI / 2, arcFont, fill, false);
  arcText(canvas, 'STAMP DIARY', textRadius, Math.PI / 2, arcFont, fill, true);

  const dayFont = matchFont({ fontFamily: FAMILY, fontSize: P.dayFont, fontWeight: 'bold' });
  const label = String(day);
  canvas.drawText(label, P.ringX - glyphWidth(dayFont, label) / 2, P.ringY + P.dayFont * 0.36, fill, dayFont);

  stroke.setStrokeWidth(P.waveStroke);
  for (let i = 0; i < P.waves; i += 1) {
    const y = P.ringY + (i - (P.waves - 1) / 2) * P.waveGap;
    const path = Skia.PathBuilder.Make();
    path.moveTo(P.waveFrom, y);
    for (let x = P.waveFrom; x <= P.waveTo; x += 4) {
      path.lineTo(x, y + Math.sin(((x - P.waveFrom) / P.waveLength) * 2 * Math.PI) * P.waveAmp);
    }
    canvas.drawPath(path.build(), stroke);
  }
  surface.flush();
  return surface.makeImageSnapshot();
}
