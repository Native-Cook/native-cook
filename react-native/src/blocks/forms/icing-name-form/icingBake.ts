import {
  BlendMode,
  BlurStyle,
  ClipOp,
  PaintStyle,
  type SkCanvas,
  type SkImage,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
} from '@shopify/react-native-skia';

import type { Geo } from './icingArt';

/* Icing Name Form — the marble counter and the cake, baked once on the JS thread. */

function hexColor(hex: string, a = 1) {
  const v = parseInt(hex.slice(1), 16);
  return Float32Array.of(((v >> 16) & 255) / 255, ((v >> 8) & 255) / 255, (v & 255) / 255, a);
}

/** The marble counter. */
export function bakeTable(g: Geo, scale: number): SkImage | null {
  const surface = Skia.Surface.Make(Math.ceil(g.w * scale), Math.ceil(g.h * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  const bg = Skia.Paint();
  bg.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(0, 0), Skia.Point(g.w * 0.4, g.h), [hexColor('#F7F1EA'), hexColor('#EFE6DC'), hexColor('#E6DACD')], [0, 0.5, 1], TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), bg);
  const vein = Skia.Paint();
  vein.setShader(Skia.Shader.MakeTurbulence(0.006, 0.018, 4, 21, 0, 0));
  vein.setAlphaf(0.1);
  vein.setBlendMode(BlendMode.Multiply);
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), vein);
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 3, 4, 0, 0));
  grain.setAlphaf(0.04);
  grain.setBlendMode(BlendMode.Multiply);
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), grain);
  surface.flush();
  return surface.makeImageSnapshot();
}

function rosette(c: SkCanvas, x: number, y: number, r: number, a0: number) {
  const b = Skia.PathBuilder.Make();
  const turns = 2.1;
  for (let k = 0; k <= 60; k += 1) {
    const u = k / 60;
    const ang = a0 + u * turns * Math.PI * 2;
    const rr = r * (0.12 + 0.72 * u);
    if (k === 0) b.moveTo(x + rr * Math.cos(ang), y + rr * Math.sin(ang));
    else b.lineTo(x + rr * Math.cos(ang), y + rr * Math.sin(ang));
  }
  const path = b.build();
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  const sh = Skia.Paint();
  sh.setAntiAlias(true);
  sh.setColor(hexColor('#6A3A2A', 0.3));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, r * 0.25, true));
  c.drawCircle(x + r * 0.18, y + r * 0.3, r * 0.92, sh);
  const layers: [number, string, number, number][] = [
    [0.46, '#D79AA6', 0, 1],
    [0.34, '#F0C3CB', 0.06, 1],
    [0.14, '#FCE7EA', 0.12, 0.85],
  ];
  for (const [w, hex, off, a] of layers) {
    p.setStrokeWidth(r * w);
    p.setColor(hexColor(hex, a));
    c.save();
    c.translate(-r * off, -r * off * 1.2);
    c.drawPath(path, p);
    c.restore();
  }
  // The star tip's grooves running along the swirl.
  p.setStrokeWidth(r * 0.045);
  for (const off of [-0.13, 0.13]) {
    p.setColor(hexColor(off < 0 ? '#FFF3F5' : '#B9707F', 0.55));
    c.save();
    c.translate(r * off * 0.6, r * off);
    c.drawPath(path, p);
    c.restore();
  }
}

/** The cake, centred in a square image of side (2 boardR + 48) pt. */
export function bakeCake(g: Geo, scale: number): SkImage | null {
  const side = g.boardR * 2 + 48;
  const surface = Skia.Surface.Make(Math.ceil(side * scale), Math.ceil(side * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  const o = side / 2;
  // Shadows of the drum and of the cake on it.
  const amb = Skia.Paint();
  amb.setAntiAlias(true);
  amb.setColor(hexColor('#5C3B25', 0.22));
  amb.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
  c.drawCircle(o + 3, o + 10, g.boardR, amb);
  // Gold cake drum with crinkled foil.
  const drum = Skia.Paint();
  drum.setAntiAlias(true);
  drum.setShader(
    Skia.Shader.MakeSweepGradient(
      o,
      o,
      [hexColor('#E9CF8E'), hexColor('#B98E3E'), hexColor('#F6E2A8'), hexColor('#A97C30'), hexColor('#E9CF8E')],
      [0, 0.28, 0.5, 0.78, 1],
      TileMode.Clamp,
    ),
  );
  c.drawCircle(o, o, g.boardR, drum);
  const crinkle = Skia.Paint();
  crinkle.setShader(Skia.Shader.MakeTurbulence(0.12, 0.12, 2, 8, 0, 0));
  crinkle.setAlphaf(0.18);
  crinkle.setBlendMode(BlendMode.Overlay);
  c.drawCircle(o, o, g.boardR, crinkle);
  const drumEdge = Skia.Paint();
  drumEdge.setAntiAlias(true);
  drumEdge.setStyle(PaintStyle.Stroke);
  drumEdge.setStrokeWidth(1.2);
  drumEdge.setColor(hexColor('#FFF4D0', 0.6));
  c.drawCircle(o, o, g.boardR - 0.8, drumEdge);
  const cakeShadow = Skia.Paint();
  cakeShadow.setAntiAlias(true);
  cakeShadow.setColor(hexColor('#4A2C18', 0.35));
  cakeShadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
  c.drawCircle(o + 2, o + 5, g.R, cakeShadow);
  // Fondant top with a soft rounded lip at the edge.
  const top = Skia.Paint();
  top.setAntiAlias(true);
  top.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(o - g.R * 0.2, o - g.R * 0.25),
      g.R * 1.25,
      [hexColor('#FBF4EA'), hexColor('#F4E8D9'), hexColor('#E9D8C3')],
      [0, 0.6, 1],
      TileMode.Clamp,
    ),
  );
  c.drawCircle(o, o, g.R, top);
  const lip = Skia.Paint();
  lip.setAntiAlias(true);
  lip.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(o, o),
      g.R,
      [hexColor('#C9AE92', 0), hexColor('#C9AE92', 0), hexColor('#FFFFFF', 0.4), hexColor('#B79878', 0.55)],
      [0, 0.86, 0.93, 1],
      TileMode.Clamp,
    ),
  );
  c.drawCircle(o, o, g.R, lip);
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.7, 0.7, 2, 12, 0, 0));
  grain.setAlphaf(0.05);
  grain.setBlendMode(BlendMode.Multiply);
  c.save();
  c.clipPath(Skia.PathBuilder.Make().addCircle(o, o, g.R).build(), ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(0, 0, side, side), grain);
  c.restore();
  // Rosettes round the edge, with gold dragées between them.
  const n = 16;
  const rr = g.R * 0.12;
  const ringR = g.R * 0.84;
  for (let k = 0; k < n; k += 1) {
    const a = (k / n) * Math.PI * 2;
    rosette(c, o + ringR * Math.cos(a), o + ringR * Math.sin(a), rr, a * 1.7);
  }
  const ball = Skia.Paint();
  ball.setAntiAlias(true);
  for (let k = 0; k < n; k += 1) {
    const a = ((k + 0.5) / n) * Math.PI * 2;
    const bx = o + ringR * 1.01 * Math.cos(a);
    const by = o + ringR * 1.01 * Math.sin(a);
    const br = g.R * 0.022;
    ball.setShader(
      Skia.Shader.MakeRadialGradient(Skia.Point(bx - br * 0.4, by - br * 0.4), br * 1.4, [hexColor('#FFF6D8'), hexColor('#D9B25A'), hexColor('#8A6420')], [0, 0.45, 1], TileMode.Clamp),
    );
    c.drawCircle(bx, by, br, ball);
  }
  surface.flush();
  return surface.makeImageSnapshot();
}
