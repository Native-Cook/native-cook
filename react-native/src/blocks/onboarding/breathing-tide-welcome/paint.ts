import { PaintStyle, type SkCanvas, Skia, StrokeCap } from '@shopify/react-native-skia';

import { DISC, FACE, FACE_DISC, ORNAMENTS, PHONE, SMILE_GROW, breath, crest, drop, floated, tideAlpha } from './tide';

function rgb(color: number, alpha: number) {
  'worklet';
  return Float32Array.of(((color >> 16) & 0xff) / 255, ((color >> 8) & 0xff) / 255, (color & 0xff) / 255, alpha);
}

/**
 * The face: two closed eyes and a smile under them, as three arcs. (x, y) is the middle of the
 * line the eyes hang from; at `scale` 1 an eye is 34 wide and the smile 130.
 */
function face(canvas: SkCanvas, x: number, y: number, scale: number, stroke: number, wide: number, alpha: number) {
  'worklet';
  const pen = Skia.Paint();
  pen.setAntiAlias(true);
  pen.setStyle(PaintStyle.Stroke);
  pen.setStrokeWidth(stroke);
  pen.setStrokeCap(StrokeCap.Round);
  pen.setColor(rgb(FACE, alpha));
  const half = 65 * scale * wide;
  const path = Skia.Path.Make();
  path.moveTo(x - 41 * scale, y);
  path.quadTo(x - 24 * scale, y + 24 * scale, x - 7 * scale, y);
  path.moveTo(x + 7 * scale, y);
  path.quadTo(x + 24 * scale, y + 24 * scale, x + 41 * scale, y);
  path.moveTo(x - half, y + 16 * scale);
  path.quadTo(x, y + 72 * scale, x + half, y + 16 * scale);
  canvas.drawPath(path, pen);
}

/** The tide at scene time `s`: a fill under an arched edge, and the face that rides on it. */
export function paintTide(canvas: SkCanvas, s: number, calm: boolean, width: number, height: number, color: number) {
  'worklet';
  const alpha = tideAlpha(s, calm);
  const y = crest(s, height, calm);
  if (alpha <= 0.003 || y >= height) return;
  const fall = drop(s, calm);
  const path = Skia.Path.Make();
  path.moveTo(0, y + fall);
  // The edge is an arc through the crest: the curve's middle is half way to its control point.
  path.quadTo(width / 2, y - fall, width, y + fall);
  path.lineTo(width, height);
  path.lineTo(0, height);
  path.close();
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setColor(rgb(color, alpha));
  canvas.drawPath(path, fill);
  const full = calm ? 0.5 : breath(s);
  face(canvas, width / 2, y + 36, 1, 5, 1 + SMILE_GROW * full * full, alpha);
}

/** The picture on the welcome round (cx, cy): a phone, the face on a disc, and the ornaments, which float. */
export function paintPicture(canvas: SkCanvas, clock: number | null, cx: number, cy: number) {
  'worklet';
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setColor(rgb(0xffffff, 1));
  const phone = { x: cx - PHONE.width / 2, y: cy - PHONE.height / 2, width: PHONE.width, height: PHONE.height };
  canvas.drawRRect({ rect: phone, rx: PHONE.radius, ry: PHONE.radius }, fill);
  fill.setColor(rgb(FACE_DISC, 1));
  canvas.drawCircle(cx + DISC.x, cy + DISC.y, DISC.size / 2, fill);
  face(canvas, cx + DISC.x, cy + DISC.y - 14, 0.6, 3, 1, 1);

  for (let n = 0; n < ORNAMENTS.length; n++) {
    const x = cx + ORNAMENTS[n].x;
    const y = cy + ORNAMENTS[n].y - (clock === null ? 0 : floated(n, clock));
    const parts = ORNAMENTS[n].parts;
    for (let p = 0; p < parts.length; p++) {
      const part = parts[p];
      fill.setColor(rgb(part[1], 1));
      if (part[0] === 2) canvas.drawCircle(x + part[2], y + part[3], part[4], fill);
      else if (part[0] === 1) {
        canvas.drawRRect({ rect: { x: x + part[2], y: y + part[3], width: part[4], height: part[5] }, rx: part[6], ry: part[6] }, fill);
      } else {
        const path = Skia.Path.Make();
        for (let i = 2; i + 1 < part.length; i += 2) {
          if (i === 2) path.moveTo(x + part[i], y + part[i + 1]);
          else path.lineTo(x + part[i], y + part[i + 1]);
        }
        path.close();
        canvas.drawPath(path, fill);
      }
    }
  }
}
