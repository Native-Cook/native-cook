/**
 * Owl Alarm Ruler — Skia drawing: the backdrop (two greys split by a diagonal that turns with the
 * time of day, and the owl's ground shadow) and the ruler (ticks under a fisheye, the needle).
 */
import { type SkCanvas, Skia } from '@shopify/react-native-skia';

import { clamp01, lerp, tickHeight, tickShade } from './motion';
import { colors, layout, space } from './theme';

/** Screen geometry in pt: width, height and the top safe-area edge. */
export type Geo = { W: number; H: number; top: number };

function rgb(c: readonly number[], a: number) {
  'worklet';
  return Skia.Color(`rgba(${Math.round(c[0])}, ${Math.round(c[1])}, ${Math.round(c[2])}, ${a.toFixed(4)})`);
}

/** sundial: extra turn of the diagonal (deg); lift: 0..1 how high the owl is off the ground. */
export function drawBackdrop(canvas: SkCanvas, g: Geo, sundial: number, lift: number) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(colors.page));
  canvas.drawRect(Skia.XYWHRect(0, 0, g.W, g.H), paint);

  // The darker band above the diagonal, turned around a point on it under the owl.
  const d = layout.diagonal;
  const px = g.W / 2 + layout.sundialPivot.dx;
  const py = g.top + layout.sundialPivot.y;
  const y0 = g.top + d.y0;
  const reach = g.W + g.H;
  const path = Skia.Path.Make();
  path.moveTo(-reach, y0 - reach * d.slope);
  path.lineTo(reach, y0 + reach * d.slope);
  path.lineTo(reach, -reach);
  path.lineTo(-reach, -reach);
  path.close();
  canvas.save();
  canvas.rotate(sundial, px, py);
  paint.setColor(Skia.Color(colors.band));
  canvas.drawPath(path, paint);
  canvas.restore();

  // The ground shadow shrinks and fades while the owl is in the air.
  const s = layout.groundShadow;
  const k = 1 - 0.3 * clamp01(lift);
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Skia.Color(colors.shadow));
  shadow.setAlphaf(1 - 0.45 * clamp01(lift));
  const cx = g.W / 2 + s.dx;
  const cy = g.top + s.cy;
  canvas.drawOval(Skia.XYWHRect(cx - s.rx * k, cy - s.ry * k, 2 * s.rx * k, 2 * s.ry * k), shadow);
}

/** The ruler band, drawn in its own canvas whose top is at `g.top + band.top`. */
export function drawRuler(canvas: SkCanvas, g: Geo, pos: number, bend: number) {
  'worklet';
  const r = layout.ruler;
  const cx = g.W / 2;
  const base = r.base - r.band.top;
  // Ticks are fully visible up to the screen inset and fade out over the next `fade` ticks.
  const limit = g.W / 2 - space.screenInset - r.tickW / 2 + 0.01;
  const reach = Math.ceil(limit / r.spacing + r.fade) + 1;
  const k0 = Math.round(pos);
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  for (let k = k0 - reach; k <= k0 + reach; k++) {
    const dist = k - pos;
    const x = cx + dist * r.spacing;
    const over = (Math.abs(x - cx) - limit) / (r.fade * r.spacing);
    const alpha = 1 - clamp01(over);
    if (alpha <= 0) continue;
    const h = tickHeight(dist);
    const t = tickShade(dist);
    paint.setColor(rgb([lerp(colors.tickDark[0], colors.tickLight[0], t), lerp(colors.tickDark[1], colors.tickLight[1], t), lerp(colors.tickDark[2], colors.tickLight[2], t)], alpha));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - r.tickW / 2, base - h, r.tickW, h), r.tickW / 2, r.tickW / 2), paint);
  }

  // The needle bends from its foot as the ticks run under it.
  const n = r.needle;
  const foot = n.cy - r.band.top + n.h / 2;
  canvas.save();
  canvas.rotate(bend, cx, foot);
  paint.setColor(Skia.Color(colors.needle));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - n.w / 2, foot - n.h, n.w, n.h), n.w / 2, n.w / 2), paint);
  canvas.restore();
}
