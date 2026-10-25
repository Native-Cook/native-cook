import { BlendMode, BlurStyle, ClipOp, PaintStyle, Skia, TileMode, type SkCanvas, type SkImage } from '@shopify/react-native-skia';

import { type BalloonPose, GORES, LOOP, PATTERN, clamp01, flicker, sway } from './balloonMotion';
import { BELOW, type BalloonArt, type BalloonGeo, CLOUDS } from './balloonScenery';

/*
 Hot Air Balloon Welcome — everything drawn per frame: the baked layers at their parallax offsets,
 and the balloon itself (gores, seams, light, the burner's glow and flame, rig and basket).
 */

// The balloon, drawn every frame -----------------------------------------------------------------

const GORE_RGB = [
  [243, 230, 207],
  [229, 102, 79],
  [47, 127, 134],
  [232, 176, 74],
];

/** Envelope radius at v (0 = crown, 1 = mouth). */
function radiusAt(g: BalloonGeo, v: number) {
  'worklet';
  if (v <= 0.55) {
    const u = (0.55 - v) / 0.55;
    return g.R * Math.sqrt(Math.max(0, 1 - u * u));
  }
  const u = (v - 0.55) / 0.45;
  return g.R - (g.R - g.rm) * Math.pow(u, 1.25);
}

function envelopePath(g: BalloonGeo) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const n = 28;
  b.moveTo(0, -g.E);
  for (let i = 1; i <= n; i++) {
    const v = i / n;
    b.lineTo(radiusAt(g, v), -g.E * (1 - v));
  }
  for (let i = n; i >= 0; i--) {
    const v = i / n;
    b.lineTo(-radiusAt(g, v), -g.E * (1 - v));
  }
  b.close();
  return b.build();
}

function gorePath(g: BalloonGeo, a: number, b2: number) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const n = 20;
  for (let i = 0; i <= n; i++) {
    const v = i / n;
    const x = radiusAt(g, v) * Math.sin(a);
    if (i === 0) b.moveTo(x, -g.E);
    else b.lineTo(x, -g.E * (1 - v));
  }
  for (let i = n; i >= 0; i--) {
    const v = i / n;
    b.lineTo(radiusAt(g, v) * Math.sin(b2), -g.E * (1 - v));
  }
  b.close();
  return b.build();
}

function rgb(c: number[], k: number) {
  'worklet';
  return `rgb(${Math.round(Math.min(255, c[0] * k))}, ${Math.round(Math.min(255, c[1] * k))}, ${Math.round(Math.min(255, c[2] * k))})`;
}

function drawEnvelope(canvas: SkCanvas, g: BalloonGeo, turn: number, heat: number) {
  'worklet';
  const step = (2 * Math.PI) / GORES;
  const light = 0.75;
  const outline = envelopePath(g);
  const seams: number[] = [];
  for (let k = 0; k < GORES; k++) {
    let a = k * step + turn;
    a = a - 2 * Math.PI * Math.floor((a + Math.PI) / (2 * Math.PI));
    let b = a + step;
    const lo = Math.max(a, -Math.PI / 2);
    const hi = Math.min(b, Math.PI / 2);
    if (hi <= lo) continue;
    a = lo;
    b = hi;
    const mid = (a + b) / 2;
    const shade = 0.5 + 0.62 * Math.max(0, Math.cos(mid - light)) + 0.08 * Math.cos(mid);
    const col = GORE_RGB[k % PATTERN];
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setShader(
      Skia.Shader.MakeLinearGradient({ x: 0, y: -g.E }, { x: 0, y: 0 }, [Skia.Color(rgb(col, shade * 1.06)), Skia.Color(rgb(col, shade)), Skia.Color(rgb(col, shade * 0.62))], [0, 0.5, 1], TileMode.Clamp),
    );
    canvas.drawPath(gorePath(g, a, b), p);
    if (a > -Math.PI / 2 + 0.01) seams.push(a);
  }
  const seam = Skia.Paint();
  seam.setAntiAlias(true);
  seam.setStyle(PaintStyle.Stroke);
  seam.setStrokeWidth(0.7);
  seam.setColor(Skia.Color('rgba(50, 24, 20, 0.28)'));
  for (let k = 0; k < seams.length; k++) {
    const m = Skia.PathBuilder.Make();
    for (let i = 0; i <= 20; i++) {
      const v = i / 20;
      const x = radiusAt(g, v) * Math.sin(seams[k]);
      if (i === 0) m.moveTo(x, -g.E);
      else m.lineTo(x, -g.E * (1 - v));
    }
    canvas.drawPath(m.build(), seam);
  }
  canvas.save();
  canvas.clipPath(outline, ClipOp.Intersect, true);
  // Cool shadow on the side away from the sun, warm highlight toward it.
  const shadow = Skia.Paint();
  shadow.setShader(Skia.Shader.MakeLinearGradient({ x: -g.R, y: 0 }, { x: g.R * 0.4, y: 0 }, [Skia.Color('rgba(40, 30, 80, 0.5)'), Skia.Color('rgba(40, 30, 80, 0.12)'), Skia.Color('rgba(40, 30, 80, 0)')], [0, 0.5, 1], TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(-g.R - 2, -g.E - 2, g.R * 2 + 4, g.E + 4), shadow);
  const hl = Skia.Paint();
  hl.setBlendMode(BlendMode.Screen);
  hl.setShader(Skia.Shader.MakeRadialGradient({ x: g.R * 0.36, y: -g.E * 0.7 }, g.R * 0.8, [Skia.Color('rgba(255, 236, 214, 0.42)'), Skia.Color('rgba(255, 220, 190, 0.1)'), Skia.Color('rgba(255, 220, 190, 0)')], [0, 0.5, 1], TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(-g.R - 2, -g.E - 2, g.R * 2 + 4, g.E + 4), hl);
  // Load tapes round the envelope and the dark skirt at the mouth.
  const tape = Skia.Paint();
  tape.setAntiAlias(true);
  tape.setStyle(PaintStyle.Stroke);
  tape.setStrokeWidth(0.8);
  tape.setColor(Skia.Color('rgba(60, 30, 24, 0.22)'));
  const vs = [0.22, 0.38, 0.55, 0.7, 0.84];
  for (let i = 0; i < vs.length; i++) {
    const r = radiusAt(g, vs[i]);
    const y = -g.E * (1 - vs[i]);
    const b = Skia.PathBuilder.Make();
    for (let j = 0; j <= 16; j++) {
      const x = -r + (2 * r * j) / 16;
      const yy = y + 0.07 * r * Math.sqrt(Math.max(0, 1 - (x / r) * (x / r)));
      if (j === 0) b.moveTo(x, yy);
      else b.lineTo(x, yy);
    }
    canvas.drawPath(b.build(), tape);
  }
  const skirt = Skia.Paint();
  skirt.setColor(Skia.Color('rgba(48, 28, 30, 0.6)'));
  canvas.drawRect(Skia.XYWHRect(-g.R, -g.E * 0.09, g.R * 2, g.E * 0.1), skirt);
  // The burner lights the fabric from inside.
  if (heat > 0.01) {
    const glow = Skia.Paint();
    glow.setBlendMode(BlendMode.Plus);
    glow.setShader(Skia.Shader.MakeRadialGradient({ x: 0, y: -g.E * 0.12 }, g.E * 0.9, [Skia.Color(`rgba(255, 160, 70, ${0.55 * heat})`), Skia.Color(`rgba(255, 120, 50, ${0.18 * heat})`), Skia.Color('rgba(255, 110, 40, 0)')], [0, 0.45, 1], TileMode.Clamp));
    canvas.drawRect(Skia.XYWHRect(-g.R - 2, -g.E - 2, g.R * 2 + 4, g.E + 4), glow);
  }
  canvas.restore();
  // Rim light on the sunward edge and the crown ring.
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(1.2);
  rim.setShader(Skia.Shader.MakeLinearGradient({ x: -g.R, y: 0 }, { x: g.R, y: 0 }, [Skia.Color('rgba(255, 200, 160, 0)'), Skia.Color('rgba(255, 200, 160, 0.1)'), Skia.Color('rgba(255, 214, 170, 0.75)')], [0, 0.6, 1], TileMode.Clamp));
  canvas.drawPath(outline, rim);
  const mouth = Skia.Paint();
  mouth.setAntiAlias(true);
  mouth.setColor(Skia.Color(heat > 0.01 ? `rgb(${Math.round(60 + 180 * heat)}, ${Math.round(30 + 90 * heat)}, ${Math.round(30 + 20 * heat)})` : '#2A1A1C'));
  canvas.drawOval(Skia.XYWHRect(-g.rm, -3, g.rm * 2, 6), mouth);
}

function drawFlame(canvas: SkCanvas, heat: number, time: number) {
  'worklet';
  if (heat < 0.01) return;
  const base = 30;
  const f = flicker(time);
  const hf = (8 + 42 * heat) * f;
  const wf = 4 + 4 * heat;
  const bloom = Skia.Paint();
  bloom.setBlendMode(BlendMode.Plus);
  bloom.setShader(Skia.Shader.MakeRadialGradient({ x: 0, y: base - hf * 0.5 }, 26 + 46 * heat, [Skia.Color(`rgba(255, 180, 90, ${0.55 * heat})`), Skia.Color(`rgba(255, 130, 60, ${0.18 * heat})`), Skia.Color('rgba(255, 120, 50, 0)')], [0, 0.4, 1], TileMode.Clamp));
  canvas.drawCircle(0, base - hf * 0.5, 26 + 46 * heat, bloom);
  const outer = Skia.PathBuilder.Make();
  outer.moveTo(-wf, base);
  outer.cubicTo(-wf * 1.3, base - hf * 0.45, -wf * 0.2, base - hf * 0.75, 0, base - hf);
  outer.cubicTo(wf * 0.2, base - hf * 0.75, wf * 1.3, base - hf * 0.45, wf, base);
  outer.close();
  const op = Skia.Paint();
  op.setAntiAlias(true);
  op.setBlendMode(BlendMode.Plus);
  op.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: base }, { x: 0, y: base - hf }, [Skia.Color('rgba(255, 120, 40, 0.95)'), Skia.Color('rgba(255, 190, 80, 0.85)'), Skia.Color('rgba(255, 220, 140, 0)')], [0, 0.45, 1], TileMode.Clamp));
  canvas.drawPath(outer.build(), op);
  const core = Skia.PathBuilder.Make();
  const hc = hf * 0.55;
  core.moveTo(-wf * 0.45, base);
  core.cubicTo(-wf * 0.6, base - hc * 0.5, -wf * 0.1, base - hc * 0.8, 0, base - hc);
  core.cubicTo(wf * 0.1, base - hc * 0.8, wf * 0.6, base - hc * 0.5, wf * 0.45, base);
  core.close();
  const cp = Skia.Paint();
  cp.setAntiAlias(true);
  cp.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: base }, { x: 0, y: base - hc }, [Skia.Color('rgba(150, 190, 255, 0.95)'), Skia.Color('rgba(255, 250, 230, 0.95)'), Skia.Color('rgba(255, 240, 200, 0)')], [0, 0.3, 1], TileMode.Clamp));
  canvas.drawPath(core.build(), cp);
}

function drawRig(canvas: SkCanvas, g: BalloonGeo) {
  'worklet';
  const cable = Skia.Paint();
  cable.setAntiAlias(true);
  cable.setStrokeWidth(0.9);
  cable.setColor(Skia.Color('rgba(40, 26, 30, 0.85)'));
  canvas.drawLine(-g.rm, 0, -14, 43, cable);
  canvas.drawLine(g.rm, 0, 14, 43, cable);
  canvas.drawLine(-g.rm * 0.4, 1, -5, 43, cable);
  canvas.drawLine(g.rm * 0.4, 1, 5, 43, cable);
  // Burner frame.
  const steel = Skia.Paint();
  steel.setAntiAlias(true);
  steel.setShader(Skia.Shader.MakeLinearGradient({ x: -9, y: 0 }, { x: 9, y: 0 }, [Skia.Color('#4A4E55'), Skia.Color('#C8CDD2'), Skia.Color('#3A3D42')], [0, 0.45, 1], TileMode.Clamp));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-9, 30, 18, 6), 2, 2), steel);
  canvas.drawRect(Skia.XYWHRect(-1.2, 36, 2.4, 7), steel);
  // Two passengers and the wicker basket with a leather rim.
  const head = Skia.Paint();
  head.setAntiAlias(true);
  head.setColor(Skia.Color('#3A2A2E'));
  canvas.drawCircle(-6, 41, 3.2, head);
  canvas.drawCircle(5.5, 40.5, 3, head);
  const basket = Skia.RRectXY(Skia.XYWHRect(-15, 44, 30, 22), 4, 4);
  const wick = Skia.Paint();
  wick.setAntiAlias(true);
  wick.setShader(Skia.Shader.MakeLinearGradient({ x: -15, y: 0 }, { x: 15, y: 0 }, [Skia.Color('#6B4420'), Skia.Color('#A8763E'), Skia.Color('#C08A4C'), Skia.Color('#5E3B1C')], [0, 0.45, 0.7, 1], TileMode.Clamp));
  canvas.drawRRect(basket, wick);
  canvas.save();
  canvas.clipRRect(basket, ClipOp.Intersect, true);
  const weave = Skia.Paint();
  weave.setAntiAlias(true);
  weave.setStrokeWidth(0.6);
  weave.setColor(Skia.Color('rgba(50, 28, 10, 0.4)'));
  for (let y = 47; y < 66; y += 3) canvas.drawLine(-15, y, 15, y, weave);
  for (let x = -13; x < 15; x += 4) {
    for (let y = 44; y < 66; y += 6) canvas.drawLine(x + ((y / 6) % 2) * 2, y, x + ((y / 6) % 2) * 2, y + 3, weave);
  }
  canvas.restore();
  const leather = Skia.Paint();
  leather.setAntiAlias(true);
  leather.setColor(Skia.Color('#4B2C18'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-16, 42, 32, 4), 2, 2), leather);
  const hi = Skia.Paint();
  hi.setStrokeWidth(0.8);
  hi.setColor(Skia.Color('rgba(255, 210, 170, 0.35)'));
  canvas.drawLine(-14, 42.6, 14, 42.6, hi);
}

function drawImageAt(canvas: SkCanvas, image: SkImage, x: number, y: number, w: number, h: number, alpha: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAlphaf(alpha);
  canvas.drawImageRect(image, Skia.XYWHRect(0, 0, image.width(), image.height()), Skia.XYWHRect(x, y, w, h), p);
}

function drawClouds(canvas: SkCanvas, g: BalloonGeo, art: BalloonArt, p: BalloonPose, front: boolean) {
  'worklet';
  const w = (2 * Math.PI) / LOOP;
  for (let i = 0; i < CLOUDS.length; i++) {
    const c = CLOUDS[i];
    if (c.front !== front) continue;
    const cw = 160 * c.scale;
    const ch = 70 * c.scale;
    const x = c.x * g.w - cw / 2 + c.amp * Math.sin(w * p.time + c.phase);
    const y = c.y + p.alt * g.scroll * c.par;
    if (y > g.h || y + ch < 0) continue;
    drawImageAt(canvas, art.clouds[c.sprite], x, y, cw, ch, c.alpha);
  }
}

/** The whole scene for one frame. */
export function drawScene(canvas: SkCanvas, g: BalloonGeo, art: BalloonArt, p: BalloonPose) {
  'worklet';
  drawImageAt(canvas, art.sky, 0, 0, g.w, g.h, 1);
  for (let i = 0; i < 2; i++) {
    const L = art.hills[i];
    drawImageAt(canvas, L.image, 0, L.y + p.alt * g.scroll * L.par, g.w, L.height, 1);
  }
  drawClouds(canvas, g, art, p, false);
  for (let i = 2; i < 4; i++) {
    const L = art.hills[i];
    drawImageAt(canvas, L.image, 0, L.y + p.alt * g.scroll * L.par, g.w, L.height, 1);
  }
  // The balloon: its mouth at (bx, by); it rises on screen while the world scrolls down.
  const sw = sway(p.time, p.alt);
  const bx = g.w * 0.5;
  const by = g.ground - BELOW - p.alt * g.rise + sw[1];
  const ground = g.ground + p.alt * g.scroll;
  if (ground < g.h + 20) {
    const k = clamp01(p.alt);
    const sh = Skia.Paint();
    sh.setColor(Skia.Color(`rgba(10, 14, 20, ${0.4 * (1 - k)})`));
    sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3 + 10 * k, true));
    canvas.drawOval(Skia.XYWHRect(bx - 20 - 30 * k, ground - 3, 40 + 60 * k, 6 + 4 * k), sh);
  }
  canvas.save();
  canvas.translate(bx, by);
  canvas.rotate(sw[0], 0, -g.E * 0.5);
  canvas.save();
  canvas.scale(1 + 0.03 * p.heat, 1 + 0.015 * p.heat);
  drawEnvelope(canvas, g, p.turn, p.heat);
  canvas.restore();
  drawRig(canvas, g);
  drawFlame(canvas, p.heat, p.time);
  canvas.restore();
  drawClouds(canvas, g, art, p, true);
  // A bloom of warm light round the burner reaches the screen at full heat.
  if (p.heat > 0.01) {
    const glow = Skia.Paint();
    glow.setBlendMode(BlendMode.Plus);
    glow.setShader(Skia.Shader.MakeRadialGradient({ x: bx, y: by + 20 }, 150, [Skia.Color(`rgba(255, 150, 70, ${0.16 * p.heat})`), Skia.Color('rgba(255, 150, 70, 0)')], [0, 1], TileMode.Clamp));
    canvas.drawCircle(bx, by + 20, 150, glow);
  }
}
