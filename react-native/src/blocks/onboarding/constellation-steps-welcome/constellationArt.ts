import { BlendMode, BlurStyle, PaintStyle, Skia, StrokeCap, StrokeJoin, TileMode, type SkCanvas, type SkFont, type SkPath } from '@shopify/react-native-skia';

import { CONSTELLATIONS, LOOP, STEP_ANGLE, type SkyPose, clamp01 } from './constellationMotion';

/** Screen geometry: the pole the sky turns round (above the screen), the hero centre, the scale. */
export type SkyGeo = { w: number; h: number; px: number; py: number; cx: number; cy: number; scale: number };

export function makeSkyGeo(w: number, h: number, top: number, bottom: number): SkyGeo {
  const cy = (top + bottom) / 2;
  return { w, h, px: w / 2, py: -h * 0.12, cx: w / 2, cy, scale: Math.max(0.6, Math.min(1, (bottom - top - 40) / 300, (w - 40) / 280)) };
}

/** Faint line art behind each constellation: the Swan, the Queen's throne and crown, the Hunter. */
export function buildOutlines(): SkPath[] {
  const swan = Skia.PathBuilder.Make();
  // Tail fan, body, neck, head and beak.
  swan.moveTo(0, -112);
  swan.lineTo(-14, -134);
  swan.moveTo(0, -112);
  swan.lineTo(0, -140);
  swan.moveTo(0, -112);
  swan.lineTo(14, -134);
  swan.moveTo(-12, -100);
  swan.cubicTo(-20, -60, -18, -10, -5, 40);
  swan.cubicTo(-2, 62, 4, 88, 6, 102);
  swan.moveTo(12, -100);
  swan.cubicTo(22, -62, 20, -12, 11, 40);
  swan.cubicTo(10, 62, 14, 88, 18, 102);
  swan.addCircle(12, 110, 7);
  swan.moveTo(15, 117);
  swan.lineTo(19, 132);
  // Wings: leading edge to the tip, trailing edge back, with a few feathers.
  swan.moveTo(-15, -32);
  swan.cubicTo(-52, -64, -102, -44, -132, -6);
  swan.cubicTo(-98, 12, -52, 10, -16, 2);
  swan.moveTo(16, -40);
  swan.cubicTo(54, -74, 98, -62, 126, -28);
  swan.cubicTo(96, -8, 54, -6, 18, -6);
  for (let i = 0; i < 4; i++) {
    const x = -34 - i * 22;
    swan.moveTo(x, 8 - i * 1.5);
    swan.lineTo(x - 8, 20 - i * 3);
    const xr = 36 + i * 22;
    swan.moveTo(xr, -6 - i * 3);
    swan.lineTo(xr + 8, 6 - i * 5);
  }

  const throne = Skia.PathBuilder.Make();
  throne.moveTo(-62, 44);
  throne.lineTo(-62, -52);
  throne.cubicTo(-62, -86, 62, -86, 62, -52);
  throne.lineTo(62, 44);
  throne.moveTo(-86, 44);
  throne.lineTo(86, 44);
  throne.moveTo(-86, 54);
  throne.lineTo(86, 54);
  throne.moveTo(-78, 54);
  throne.lineTo(-82, 104);
  throne.moveTo(78, 54);
  throne.lineTo(82, 104);
  throne.moveTo(-86, 44);
  throne.lineTo(-86, 10);
  throne.lineTo(-62, 10);
  throne.moveTo(86, 44);
  throne.lineTo(86, 10);
  throne.lineTo(62, 10);
  // The crown above.
  throne.moveTo(-30, -96);
  throne.lineTo(-30, -112);
  throne.lineTo(-16, -102);
  throne.lineTo(0, -122);
  throne.lineTo(16, -102);
  throne.lineTo(30, -112);
  throne.lineTo(30, -96);
  throne.close();
  throne.addCircle(0, -128, 3);

  const hunter = Skia.PathBuilder.Make();
  hunter.addCircle(0, -124, 11);
  hunter.moveTo(-62, -86);
  hunter.cubicTo(-20, -96, 20, -92, 56, -78);
  // Raised arm with a club.
  hunter.moveTo(-62, -86);
  hunter.cubicTo(-82, -104, -92, -126, -84, -150);
  hunter.addOval(Skia.XYWHRect(-96, -186, 22, 40));
  // Shield held out on the right.
  hunter.moveTo(72, -118);
  hunter.cubicTo(104, -104, 104, -52, 72, -38);
  hunter.moveTo(56, -78);
  hunter.lineTo(72, -80);
  // Torso, tunic and legs.
  hunter.moveTo(-58, -80);
  hunter.cubicTo(-40, -40, -30, -10, -24, 2);
  hunter.moveTo(52, -72);
  hunter.cubicTo(36, -40, 28, -20, 24, -14);
  hunter.moveTo(-24, 2);
  hunter.cubicTo(-44, 30, -54, 44, -58, 54);
  hunter.lineTo(64, 50);
  hunter.cubicTo(52, 30, 36, 6, 24, -14);
  hunter.moveTo(-30, 54);
  hunter.lineTo(-46, 98);
  hunter.lineTo(-58, 112);
  hunter.moveTo(34, 52);
  hunter.lineTo(62, 104);
  hunter.lineTo(76, 114);
  hunter.moveTo(0, -4);
  hunter.lineTo(5, 44);

  return [swan.build(), throne.build(), hunter.build()];
}

/** How much of edge i of n is drawn when the constellation is `d` drawn. */
function edgeProgress(d: number, i: number, n: number) {
  'worklet';
  return clamp01(d * n - i);
}

function drawConstellation(canvas: SkCanvas, g: SkyGeo, k: number, outline: SkPath, p: SkyPose, font: SkFont | null) {
  'worklet';
  const c = CONSTELLATIONS[k];
  const d = p.drawn[k];
  const fade = p.fade;
  const n = c.edges.length;
  const lit: number[] = [];
  for (let s = 0; s < c.stars.length; s++) lit.push(0);
  canvas.save();
  canvas.rotate(((p.theta - k * STEP_ANGLE) * 180) / Math.PI, g.px, g.py);
  canvas.translate(g.cx, g.cy);
  canvas.scale(g.scale, g.scale);

  // Line art, fading in once the lines are mostly drawn.
  const art = 0.26 * clamp01((d - 0.55) / 0.45) * fade;
  if (art > 0.005) {
    const op = Skia.Paint();
    op.setAntiAlias(true);
    op.setStyle(PaintStyle.Stroke);
    op.setStrokeWidth(1 / g.scale);
    op.setStrokeCap(StrokeCap.Round);
    op.setStrokeJoin(StrokeJoin.Round);
    op.setColor(Skia.Color(`rgba(196, 214, 255, ${art})`));
    canvas.drawPath(outline, op);
  }

  // The lines, star to star: a soft glow under a bright core, each drawn on in turn.
  if (d > 0.001 && fade > 0.005) {
    const glow = Skia.PathBuilder.Make();
    for (let i = 0; i < n; i++) {
      const e = edgeProgress(d, i, n);
      if (e <= 0) continue;
      const a = c.stars[c.edges[i][0]];
      const b = c.stars[c.edges[i][1]];
      glow.moveTo(a[0], a[1]);
      glow.lineTo(a[0] + (b[0] - a[0]) * e, a[1] + (b[1] - a[1]) * e);
      lit[c.edges[i][0]] = Math.max(lit[c.edges[i][0]], clamp01(e * 5));
      lit[c.edges[i][1]] = Math.max(lit[c.edges[i][1]], clamp01((e - 0.85) / 0.15));
    }
    const path = glow.build();
    const gp = Skia.Paint();
    gp.setAntiAlias(true);
    gp.setStyle(PaintStyle.Stroke);
    gp.setStrokeWidth(4.5 / g.scale);
    gp.setStrokeCap(StrokeCap.Round);
    gp.setColor(Skia.Color(`rgba(255, 214, 140, ${0.32 * fade})`));
    gp.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, false));
    canvas.drawPath(path, gp);
    const cp = Skia.Paint();
    cp.setAntiAlias(true);
    cp.setStyle(PaintStyle.Stroke);
    cp.setStrokeWidth(1.2 / g.scale);
    cp.setStrokeCap(StrokeCap.Round);
    cp.setColor(Skia.Color(`rgba(255, 238, 200, ${0.92 * fade})`));
    canvas.drawPath(path, cp);
  }

  // Stars: always a little brighter than the field; lit ones bloom.
  const w = (2 * Math.PI) / LOOP;
  for (let s = 0; s < c.stars.length; s++) {
    const st = c.stars[s];
    const l = lit[s] * fade;
    const tw = 0.85 + 0.15 * Math.sin(w * (3 + (s % 3)) * p.time + s * 1.7 + k);
    const r = st[2] * (1 + 0.35 * l);
    const halo = Skia.Paint();
    halo.setBlendMode(BlendMode.Plus);
    halo.setShader(
      Skia.Shader.MakeRadialGradient({ x: st[0], y: st[1] }, r * (3.5 + 3 * l), [Skia.Color(`rgba(255, 236, 200, ${(0.34 + 0.34 * l) * tw})`), Skia.Color('rgba(255, 220, 170, 0)')], [0, 1], TileMode.Clamp),
    );
    canvas.drawCircle(st[0], st[1], r * (3.5 + 3 * l), halo);
    const core = Skia.Paint();
    core.setAntiAlias(true);
    core.setColor(Skia.Color(`rgba(255, 250, 238, ${(0.82 + 0.18 * l) * tw})`));
    canvas.drawCircle(st[0], st[1], r * (0.72 - 0.1 * l), core);
    if (l > 0.02 && st[2] > 2.3) {
      // A short four-point sparkle on the bright ones.
      const sp = Skia.Paint();
      sp.setAntiAlias(true);
      sp.setStrokeWidth(0.8 / g.scale);
      sp.setStrokeCap(StrokeCap.Round);
      sp.setColor(Skia.Color(`rgba(255, 244, 220, ${0.55 * l})`));
      const len = r * 3.2;
      canvas.drawLine(st[0] - len, st[1], st[0] + len, st[1], sp);
      canvas.drawLine(st[0], st[1] - len, st[0], st[1] + len, sp);
    }
  }
  if (font) {
    const a = lit[c.alpha] * fade;
    if (a > 0.01) {
      const st = c.stars[c.alpha];
      const tp = Skia.Paint();
      tp.setAntiAlias(true);
      tp.setColor(Skia.Color(`rgba(242, 222, 180, ${0.85 * a})`));
      canvas.drawText(c.label, st[0] + 10, st[1] + 4, tp, font);
    }
  }
  canvas.restore();
}

/** Every constellation that can be on screen at this turn of the sky. */
export function drawConstellations(canvas: SkCanvas, g: SkyGeo, outlines: SkPath[], p: SkyPose, font: SkFont | null) {
  'worklet';
  for (let k = 0; k < CONSTELLATIONS.length; k++) {
    if (Math.abs(p.theta - k * STEP_ANGLE) > STEP_ANGLE * 1.2) continue;
    drawConstellation(canvas, g, k, outlines[k], p, font);
  }
}
