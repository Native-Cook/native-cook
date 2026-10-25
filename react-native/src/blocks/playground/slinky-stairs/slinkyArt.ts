import { BlendMode, BlurStyle, ClipOp, FilterMode, MipmapMode, PaintStyle, type SkCanvas, type SkImage, Skia, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';

import { type Geo, N, type Pose } from './slinkyMotion';

/*
 Slinky Stairs — the drawing, one Skia canvas. Baked once: a slate wall lit by a warm desk lamp from
 the upper left, a three-step maple staircase standing on a walnut desk (top faces, front faces,
 grain, lit edges, a vignette and grain) and the bottom panel's plate. Per frame: soft contact
 shadows, then the 40 rings as four batched strokes — the far half of every ring in dark steel, then
 the near halves in steel, a cool highlight where the lamp catches them and a thin warm rim.
*/

export type Layout = Geo & { stepY: number[]; left: number; panelY: number; panelH: number; deskEdge: number };

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const left = 12;
  const tread = (w - left * 2) / 4;
  const panelH = 120;
  const panelY = h - Math.max(bottom, 12) - panelH;
  const R = Math.min(36, tread * 0.4);
  const pitch = 1.7;
  const H = (N - 1) * pitch;
  let y0 = top + 120 + H + 56;
  let y3 = panelY - 96;
  const riser = Math.max(60, Math.min(92, (y3 - y0) / 3));
  // Centre the stairs in the room left between the header and the desk's front edge.
  y0 = Math.max(y0, (y0 + y3) / 2 - riser * 1.5);
  y3 = y0 + riser * 3;
  const stepY = [y0, y0 + riser, y0 + riser * 2, y3];
  const deskEdge = Math.min(panelY - 24, y3 + 64);
  const baseX: number[] = [];
  const baseY: number[] = [];
  for (let k = 0; k < 4; k += 1) {
    baseX.push(left + tread * (k + 0.5));
    baseY.push(stepY[k] - 8);
  }
  return { w, h, baseX, baseY, R, pitch, tilt: 0.3, tread, riser, stepY, left, panelY, panelH, deskEdge };
}

function paint(color: string, alpha = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  if (alpha < 1) p.setAlphaf(alpha);
  return p;
}

function stroke(color: string, width: number, alpha = 1) {
  'worklet';
  const p = paint(color, alpha);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

function linear(x0: number, y0: number, x1: number, y1: number, list: string[], pos: number[] | null = null) {
  const cs = list.map((c) => Skia.Color(c));
  return Skia.Shader.MakeLinearGradient(Skia.Point(x0, y0), Skia.Point(x1, y1), cs, pos, TileMode.Clamp);
}

function radial(x: number, y: number, r: number, list: string[], pos: number[] | null = null) {
  const cs = list.map((c) => Skia.Color(c));
  return Skia.Shader.MakeRadialGradient(Skia.Point(x, y), r, cs, pos, TileMode.Clamp);
}

function grain(alpha: number, seed: number) {
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 2, seed, 0, 0));
  p.setColorFilter(Skia.ColorFilter.MakeMatrix([0.33, 0.33, 0.33, 0, 0, 0.33, 0.33, 0.33, 0, 0, 0.33, 0.33, 0.33, 0, 0, 0, 0, 0, 0, alpha]));
  p.setBlendMode(BlendMode.Overlay);
  return p;
}

function hash(i: number) {
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** Wood grain: long, slightly wavy strokes inside a rect. */
function woodGrain(c: SkCanvas, x: number, y: number, w: number, h: number, seed: number, vertical: boolean) {
  c.save();
  c.clipRect(Skia.XYWHRect(x, y, w, h), ClipOp.Intersect, true);
  const n = Math.round((vertical ? w : h) / 5);
  for (let i = 0; i < n; i += 1) {
    const b = Skia.PathBuilder.Make();
    const off = (i + hash(seed + i) * 0.8) * 5;
    const amp = 1 + 2 * hash(seed + i * 3);
    const len = vertical ? h : w;
    for (let s = 0; s <= 12; s += 1) {
      const a = (s / 12) * len;
      const wv = Math.sin((s / 12) * Math.PI * (1 + hash(seed + i * 5) * 2) + i) * amp;
      if (vertical) {
        if (s === 0) b.moveTo(x + off + wv, y + a);
        else b.lineTo(x + off + wv, y + a);
      } else if (s === 0) b.moveTo(x + a, y + off + wv);
      else b.lineTo(x + a, y + off + wv);
    }
    c.drawPath(b.build(), stroke(i % 3 === 0 ? '#5A3618' : '#7A4F28', 0.7, 0.18 + 0.12 * hash(seed + i * 7)));
  }
  c.restore();
}

function drawScene(c: SkCanvas, g: Layout, top: number) {
  const { w, h } = g;
  const wall = Skia.Paint();
  wall.setShader(linear(0, 0, 0, h, ['#344241', '#2A3635', '#212B2A', '#1A2221'], [0, 0.4, 0.75, 1]));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), wall);
  const lamp = Skia.Paint();
  lamp.setShader(radial(w * 0.08, top + 40, w * 1.25, ['rgba(255,214,150,0.42)', 'rgba(240,180,110,0.16)', 'rgba(200,150,90,0)'], [0, 0.45, 1]));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), lamp);
  c.drawRect(Skia.XYWHRect(0, 0, w, h), grain(0.06, 2));
  const y3 = g.stepY[3];
  // The desk: a walnut top that runs towards us from under the stairs, then its front edge.
  const e = g.deskEdge;
  const deskTop = Skia.Paint();
  deskTop.setShader(linear(0, y3 - 18, 0, e, ['#5E412C', '#6E4D34', '#7A563A'], [0, 0.5, 1]));
  c.drawRect(Skia.XYWHRect(0, y3 - 18, w, e - y3 + 18), deskTop);
  woodGrain(c, 0, y3 - 18, w, e - y3 + 18, 40, false);
  const pool = Skia.Paint();
  pool.setShader(radial(w * 0.3, y3 + 10, w * 0.7, ['rgba(255,210,150,0.16)', 'rgba(255,210,150,0)']));
  c.drawRect(Skia.XYWHRect(0, y3 - 18, w, e - y3 + 18), pool);
  const deskFront = Skia.Paint();
  deskFront.setShader(linear(0, e, 0, h, ['#3A271A', '#2B1C12', '#1E140D']));
  c.drawRect(Skia.XYWHRect(0, e, w, h - e), deskFront);
  c.drawLine(0, e + 0.5, w, e + 0.5, stroke('#D9A47A', 1.2, 0.5));
  c.drawLine(0, y3 - 18, w, y3 - 18, stroke('#2A1A0E', 1, 0.4));
  // Shadow the stairs cast to the right on the desk.
  c.drawRect(Skia.XYWHRect(g.left + g.tread * 3 - 6, y3 - 16, 40, 14), (() => {
    const p = Skia.Paint();
    p.setShader(linear(g.left + g.tread * 3, 0, g.left + g.tread * 3 + 40, 0, ['rgba(10,6,3,0.45)', 'rgba(10,6,3,0)']));
    return p;
  })());
  // Three steps, each a maple block from its tread down to the desk.
  for (let k = 0; k < 3; k += 1) {
    const x = g.left + g.tread * k;
    const yk = g.stepY[k];
    const tone = ['#C8915A', '#BD8650', '#B27C47'][k];
    const face = Skia.Paint();
    face.setShader(linear(x, yk, x, y3, [tone, '#9A6838', '#7E522A'], [0, 0.6, 1]));
    c.drawRect(Skia.XYWHRect(x, yk, g.tread, y3 - yk), face);
    woodGrain(c, x, yk, g.tread, y3 - yk, 10 + k * 7, true);
    // Top face (seen from a little above), lit by the lamp.
    const topFace = Skia.Paint();
    topFace.setShader(linear(x, yk - 16, x + g.tread, yk, ['#F0C48C', '#DDAA70', '#C99459']));
    c.drawRect(Skia.XYWHRect(x, yk - 16, g.tread, 16), topFace);
    woodGrain(c, x, yk - 16, g.tread, 16, 30 + k, false);
    c.drawLine(x, yk + 0.5, x + g.tread, yk + 0.5, stroke('#FFE4BC', 1.2, 0.7));
    c.drawLine(x, yk - 16, x + g.tread, yk - 16, stroke('#4A2E16', 1, 0.35));
    // The crease where this block meets the next, lower one.
    const crease = Skia.Paint();
    crease.setShader(linear(x + g.tread - 10, 0, x + g.tread, 0, ['rgba(40,20,8,0)', 'rgba(40,20,8,0.45)']));
    c.drawRect(Skia.XYWHRect(x + g.tread - 10, yk, 10, y3 - yk), crease);
    c.drawLine(x + 0.5, yk - 16, x + 0.5, y3, stroke('#FFE2B8', 1, k === 0 ? 0.5 : 0.18));
  }
  // Ambient occlusion where the steps meet the desk.
  const ao = Skia.Paint();
  ao.setShader(linear(0, y3 - 24, 0, y3, ['rgba(20,10,4,0)', 'rgba(20,10,4,0.35)']));
  c.drawRect(Skia.XYWHRect(g.left, y3 - 24, g.tread * 3, 24), ao);
  // The panel's plate.
  const px = 16;
  const pw = w - 32;
  const rr = Skia.RRectXY(Skia.XYWHRect(px, g.panelY, pw, g.panelH), 22, 22);
  const sh = paint('#0A0705', 0.5);
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(px, g.panelY + 10, pw, g.panelH), 22, 22), sh);
  const plate = Skia.Paint();
  plate.setShader(linear(0, g.panelY, 0, g.panelY + g.panelH, ['#2E2A27', '#24201D', '#1D1A17']));
  c.drawRRect(rr, plate);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(px + 0.5, g.panelY + 0.5, pw - 1, g.panelH - 1), 21.5, 21.5), stroke('#FFEBD2', 1, 0.1));
  const vig = Skia.Paint();
  vig.setShader(radial(w / 2, h * 0.45, Math.hypot(w, h) * 0.62, ['rgba(8,10,10,0)', 'rgba(8,10,10,0.25)', 'rgba(6,8,8,0.6)'], [0, 0.6, 1]));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), vig);
}

export function bakeScene(g: Layout, top: number, scale: number): SkImage | null {
  const surface = Skia.Surface.Make(Math.ceil(g.w * scale), Math.ceil(g.h * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  drawScene(c, g, top);
  surface.flush();
  return surface.makeImageSnapshot();
}

/** Adds the arc of every ring between angles a0 and a1 to one path. */
function arcs(g: Geo, rings: number[], a0: number, a1: number, steps: number) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const R = g.R;
  const m = g.R * g.tilt;
  for (let i = 0; i < rings.length; i += 4) {
    const cx = rings[i];
    const cy = rings[i + 1];
    const nx = rings[i + 2];
    const ny = rings[i + 3];
    for (let s = 0; s <= steps; s += 1) {
      const a = a0 + ((a1 - a0) * s) / steps;
      const x = cx + nx * R * Math.cos(a);
      const y = cy + ny * R * Math.cos(a) - m * Math.sin(a);
      if (s === 0) b.moveTo(x, y);
      else b.lineTo(x, y);
    }
  }
  return b.build();
}

export function drawFrame(c: SkCanvas, g: Layout, scene: SkImage, p: Pose, touch: number[]) {
  'worklet';
  c.drawImageRectOptions(scene, Skia.XYWHRect(0, 0, scene.width(), scene.height()), Skia.XYWHRect(0, 0, g.w, g.h), FilterMode.Linear, MipmapMode.None, Skia.Paint());
  for (let i = 0; i < p.shadows.length; i += 4) {
    const a = p.shadows[i + 3];
    if (a < 0.01) continue;
    const sh = paint('#120905', 0.5 * a);
    sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
    const r = p.shadows[i + 2];
    c.drawOval(Skia.XYWHRect(p.shadows[i] - r * 1.08 + 3, p.shadows[i + 1] - r * g.tilt * 0.7 + 2, r * 2.16, r * g.tilt * 1.6), sh);
  }
  const rings = p.rings;
  const PI = Math.PI;
  c.drawPath(arcs(g, rings, 0, PI, 10), stroke('#68727A', 2.6));
  c.drawPath(arcs(g, rings, PI, 2 * PI, 10), stroke('#A6B0B9', 3));
  c.drawPath(arcs(g, rings, PI * 1.18, PI * 1.62, 5), stroke('#EEF3F7', 1.4, 0.9));
  c.drawPath(arcs(g, rings, PI * 1.04, PI * 1.2, 3), stroke('#F3C88E', 1.1, 0.65));
  c.drawPath(arcs(g, rings, PI * 1.8, PI * 1.95, 3), stroke('#2E343A', 1.2, 0.5));
  if (touch[2] > 0.01) {
    const glow = paint('#FFFFFF', 0.14 * touch[2]);
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8, true));
    c.drawCircle(touch[0], touch[1], 26, glow);
    c.drawCircle(touch[0], touch[1], 22, stroke('#FFFFFF', 1.2, 0.3 * touch[2]));
  }
}
