import {
  BlendMode,
  BlurStyle,
  ClipOp,
  PaintStyle,
  type SkImage,
  Skia,
  TileMode,
} from '@shopify/react-native-skia';

import { type Geo, BORDER_RGB, CELLS, drawStitches } from './crossStitchArt';

/* Cross Stitch Input — the table, hoop, cloth and printed border, baked once on the JS thread. */

function hexColor(hex: string, a = 1) {
  const v = parseInt(hex.slice(1), 16);
  return Float32Array.of(((v >> 16) & 255) / 255, ((v >> 8) & 255) / 255, (v & 255) / 255, a);
}

/** The table, hoop, cloth and printed border, at `scale` px per pt. */
export function bakeScene(g: Geo, scale: number, border: { cx: number[]; cy: number[] }): SkImage | null {
  const surface = Skia.Surface.Make(Math.ceil(g.w * scale), Math.ceil(g.h * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);

  // Warm paper backdrop with a three-stop wash and grain.
  const bg = Skia.Paint();
  bg.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, 0),
      Skia.Point(g.w * 0.3, g.h),
      [hexColor('#F3EBDD'), hexColor('#EADFCB'), hexColor('#E2D3BC')],
      [0, 0.55, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), bg);
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 3, 7, 0, 0));
  grain.setAlphaf(0.05);
  grain.setBlendMode(BlendMode.Multiply);
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), grain);

  // The hoop's two shadows on the table.
  const amb = Skia.Paint();
  amb.setAntiAlias(true);
  amb.setColor(hexColor('#5A4128', 0.2));
  amb.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 18, true));
  c.drawCircle(g.cx, g.cy + 16, g.hoopR + 2, amb);
  const contact = Skia.Paint();
  contact.setAntiAlias(true);
  contact.setColor(hexColor('#4A3220', 0.32));
  contact.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.5, true));
  c.drawCircle(g.cx, g.cy + 2, g.hoopR, contact);

  // Cloth: aida's square blocks with holes at the corners, a slight sheen, grain and dimming at
  // the hoop's inner edge.
  const fr = g.fabricR + 3;
  const clip = Skia.PathBuilder.Make().addCircle(g.cx, g.cy, fr).build();
  c.save();
  c.clipPath(clip, ClipOp.Intersect, true);
  const cloth = Skia.Paint();
  cloth.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(g.cx - fr * 0.35, g.cy - fr * 0.45),
      fr * 1.6,
      [hexColor('#FBF7EE'), hexColor('#F2EBDD'), hexColor('#E6DCC8')],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  c.drawCircle(g.cx, g.cy, fr, cloth);
  const weave = Skia.Paint();
  weave.setAntiAlias(true);
  weave.setStyle(PaintStyle.Stroke);
  weave.setStrokeWidth(g.cell * 0.18);
  const lines = Skia.PathBuilder.Make();
  const half = CELLS / 2 + 1;
  for (let k = -half; k <= half; k += 1) {
    for (const off of [-0.25, 0.25]) {
      const v = (k + 0.5 + off) * g.cell;
      lines.moveTo(g.cx - fr, g.cy + v);
      lines.lineTo(g.cx + fr, g.cy + v);
      lines.moveTo(g.cx + v, g.cy - fr);
      lines.lineTo(g.cx + v, g.cy + fr);
    }
  }
  weave.setColor(hexColor('#FFFFFF', 0.32));
  c.drawPath(lines.build(), weave);
  const holes = Skia.PathBuilder.Make();
  for (let i = -half; i <= half; i += 1) {
    for (let j = -half; j <= half; j += 1) holes.addCircle(g.cx + i * g.cell, g.cy + j * g.cell, g.cell * 0.11);
  }
  const holePaint = Skia.Paint();
  holePaint.setAntiAlias(true);
  holePaint.setColor(hexColor('#8C7A62', 0.38));
  c.drawPath(holes.build(), holePaint);
  const tooth = Skia.Paint();
  tooth.setShader(Skia.Shader.MakeFractalNoise(0.35, 0.35, 3, 3, 0, 0));
  tooth.setAlphaf(0.08);
  tooth.setBlendMode(BlendMode.Multiply);
  c.drawCircle(g.cx, g.cy, fr, tooth);
  const rim = Skia.Paint();
  rim.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(g.cx, g.cy),
      fr,
      [hexColor('#5B4630', 0), hexColor('#5B4630', 0), hexColor('#5B4630', 0.24)],
      [0, 0.82, 1],
      TileMode.Clamp,
    ),
  );
  c.drawCircle(g.cx, g.cy, fr, rim);
  // The border motif, already worked.
  drawStitches(c, g, border.cx, border.cy, border.cx.length, BORDER_RGB, 1, null, null, null, null, null);
  c.restore();

  // Hoop: the inner ring just shows, the outer ring is a lathe-turned beech band with a bevel.
  const ring = (r0: number, r1: number, stops: string[]) => {
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(r1 - r0);
    p.setShader(
      Skia.Shader.MakeRadialGradient(
        Skia.Point(g.cx, g.cy),
        r1,
        stops.map((s) => hexColor(s)),
        [r0 / r1, (r0 + (r1 - r0) * 0.3) / r1, (r0 + (r1 - r0) * 0.7) / r1, 1],
        TileMode.Clamp,
      ),
    );
    c.drawCircle(g.cx, g.cy, (r0 + r1) / 2, p);
  };
  ring(g.fabricR - 1, g.fabricR + 4, ['#9A6B3A', '#C9995F', '#D8AE76', '#B07C45']);
  ring(g.fabricR + 3, g.hoopR, ['#8E5F31', '#D9AD73', '#E8C38E', '#A8733E']);
  const grainRing = Skia.Paint();
  grainRing.setAntiAlias(true);
  grainRing.setStyle(PaintStyle.Stroke);
  grainRing.setStrokeWidth(g.hoopR - g.fabricR - 3);
  grainRing.setShader(Skia.Shader.MakeFractalNoise(0.02, 0.45, 3, 11, 0, 0));
  grainRing.setAlphaf(0.18);
  grainRing.setBlendMode(BlendMode.Multiply);
  c.drawCircle(g.cx, g.cy, (g.fabricR + 3 + g.hoopR) / 2, grainRing);
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1);
  edge.setColor(hexColor('#FFF4E0', 0.45));
  c.drawCircle(g.cx, g.cy - 0.5, g.hoopR - 1.2, edge);
  edge.setColor(hexColor('#5E3D1E', 0.45));
  c.drawCircle(g.cx, g.cy + 0.5, g.fabricR + 3.2, edge);

  // The brass screw clamp at the top: two wooden lugs and a turned screw through them.
  const top = g.cy - g.hoopR;
  const lug = Skia.Paint();
  lug.setAntiAlias(true);
  lug.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, top - 12),
      Skia.Point(0, top + 12),
      [hexColor('#E7C18B'), hexColor('#C4915A'), hexColor('#9C6B39')],
      [0, 0.55, 1],
      TileMode.Clamp,
    ),
  );
  const lugShadow = Skia.Paint();
  lugShadow.setAntiAlias(true);
  lugShadow.setColor(hexColor('#4A3220', 0.3));
  lugShadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
  for (const dx of [-11, 3]) {
    const r = Skia.RRectXY(Skia.XYWHRect(g.cx + dx, top - 13, 8, 22), 2.5, 2.5);
    c.save();
    c.translate(0.5, 2.5);
    c.drawRRect(r, lugShadow);
    c.restore();
    c.drawRRect(r, lug);
  }
  const brass = Skia.Paint();
  brass.setAntiAlias(true);
  brass.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, top - 9),
      Skia.Point(0, top - 1),
      [hexColor('#FFF0B8'), hexColor('#D9A93E'), hexColor('#8D6418'), hexColor('#C99733')],
      [0, 0.35, 0.75, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.cx - 22, top - 9, 44, 8), 3, 3), brass);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.cx + 20, top - 12, 9, 14), 3, 3), brass);
  const thread = Skia.Paint();
  thread.setAntiAlias(true);
  thread.setStyle(PaintStyle.Stroke);
  thread.setStrokeWidth(0.7);
  thread.setColor(hexColor('#6E4E14', 0.6));
  for (let k = 0; k < 9; k += 1) {
    const x = g.cx - 20 + k * 4.6;
    c.drawLine(x, top - 8.5, x + 1.6, top - 1.5, thread);
  }

  surface.flush();
  const image = surface.makeImageSnapshot();
  return image;
}
