import { ClipOp, PaintStyle, type SkCanvas, type SkFont, Skia } from '@shopify/react-native-skia';

import { type ClothPose, DEPTH, clamp } from './clothMotion';
import { fill, spaced, textWidth } from './clothPaint';
import { type Geo, project } from './clothScene';

/** The clean-pull counter (an odometer) in the header and the "Clean pull" badge over the table. */

export type HudFonts = { label: SkFont; count: SkFont; badge: SkFont };

function softRound(v: number) {
  'worklet';
  const f = Math.floor(v);
  const x = clamp((v - f - 0.35) / 0.3, 0, 1);
  return f + x * x * (3 - 2 * x);
}

export function drawHud(canvas: SkCanvas, g: Geo, f: HudFonts, top: number, p: ClothPose) {
  'worklet';
  // Counter, right-aligned in the header: label over a rolling number.
  const right = g.w - 24;
  spaced(canvas, 'CLEAN PULLS', right, top + 28, f.label, fill(0xf4e7d4, 0.5), 1.4, 1);
  const r = softRound(Math.max(0, p.count));
  const i = Math.floor(r);
  const fr = r - i;
  const lineH = 34;
  const base = top + 64;
  canvas.save();
  canvas.clipRect(Skia.XYWHRect(right - 80, base - lineH + 4, 80, lineH + 2), ClipOp.Intersect, true);
  for (let j = 0; j < 2; j += 1) {
    const a = 1 - Math.min(1, Math.abs(j - fr));
    if (a <= 0.01) continue;
    const s = `${i + j}`;
    canvas.drawText(s, right - textWidth(f.count, s), base + (j - fr) * lineH, fill(0xfff1dc, a), f.count);
  }
  canvas.restore();
  // Badge: drops in over the far edge of the table.
  if (p.badge > 0.01) {
    const back = project(g, 0, DEPTH, 0);
    const label = 'Clean pull · dishes stayed';
    const w = textWidth(f.badge, label) + 40;
    const y = back[1] - 46 - (1 - p.badge) * 10;
    const r2 = Skia.RRectXY(Skia.XYWHRect(g.cx - w / 2, y, w, 30), 15, 15);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.cx - w / 2, y + 6, w, 30), 15, 15), fill(0x000000, 0.35 * p.badge, 10));
    canvas.drawRRect(r2, fill(0xf3e2c4, p.badge));
    canvas.drawCircle(g.cx - w / 2 + 17, y + 15, 6.5, fill(0x4f7a3a, p.badge));
    const tick = Skia.PathBuilder.Make().moveTo(g.cx - w / 2 + 14, y + 15).lineTo(g.cx - w / 2 + 16.3, y + 17.4).lineTo(g.cx - w / 2 + 20.2, y + 12.8).build();
    const tp = fill(0xf3e2c4, p.badge);
    tp.setStyle(PaintStyle.Stroke);
    tp.setStrokeWidth(1.6);
    canvas.drawPath(tick, tp);
    canvas.drawText(label, g.cx - w / 2 + 30, y + 19.5, fill(0x3a2412, p.badge), f.badge);
  }
}
