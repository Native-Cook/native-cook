import { BlendMode, BlurStyle, ClipOp, type SkCanvas, type SkFont, type SkPicture, Skia, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';

import { DOCK_H, type Geo, ICON, TOOLS } from './magnetSnapDockMotion';

/*
 Everything drawn in the dock's two canvases.
 Static (recorded once, in the canvas under the panels): the graphite backdrop and the artboard
 with a harbour sketch in progress (gouache sun, ink water strokes, a boat, a selected pen path).
 Live (the canvas over the panels): the dock, the plate under the active tool, six enamel tool
 tiles (each face recorded once), their shadows, the field lines and the fingertip.
 */

export const TOOL_COLORS = [0x5b606b, 0x2f7d76, 0xc4532f, 0xcf9a32, 0x57683c, 0x3f5f8a];
const GLYPH = 0xf6f1e8;
const R = ICON * 0.27;

function hexA(hex: number, a = 1) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 0xff) / 255, ((hex >> 8) & 0xff) / 255, (hex & 0xff) / 255, a);
}

function shade(hex: number, k: number) {
  const c = hexA(hex);
  const f = (v: number) => (k >= 0 ? v + (1 - v) * k : v * (1 + k));
  return Float32Array.of(f(c[0]), f(c[1]), f(c[2]), 1);
}

function fill(c: Float32Array) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(c);
  return p;
}

function stroke(c: Float32Array, width: number) {
  'worklet';
  const p = fill(c);
  p.setStyle(1);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

function blurred(c: Float32Array, sigma: number) {
  'worklet';
  const p = fill(c);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: Float32Array[], pos: number[]) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors, pos, TileMode.Clamp));
  return p;
}

function rr(x: number, y: number, w: number, h: number, r: number) {
  'worklet';
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}

function poly(points: number[], close = true) {
  const p = Skia.PathBuilder.Make();
  p.moveTo(points[0], points[1]);
  for (let i = 2; i < points.length; i += 2) p.lineTo(points[i], points[i + 1]);
  if (close) p.close();
  return p.build();
}

/** One glyph, centred on the origin, about 28 pt across. */
function glyph(canvas: SkCanvas, tool: number, font: SkFont) {
  const ink = fill(hexA(GLYPH));
  const line = stroke(hexA(GLYPH), 2);
  if (tool === 0) {
    canvas.drawPath(poly([-6, -12, 9, 3, 2.5, 4, 6.5, 11.5, 3.2, 13, -0.6, 5.6, -6, 10.5]), ink);
  } else if (tool === 1) {
    const nib = poly([0, -12, 8.5, 1, 3.4, 11, -3.4, 11, -8.5, 1]);
    canvas.drawPath(nib, ink);
    canvas.drawLine(0, -11, 0, 2, stroke(hexA(TOOL_COLORS[1]), 1.4));
    canvas.drawCircle(0, 2.5, 2.2, fill(hexA(TOOL_COLORS[1])));
    canvas.drawRRect(rr(-4.5, 12.5, 9, 2.6, 1.3), ink);
  } else if (tool === 2) {
    canvas.save();
    canvas.rotate(-38, 0, 0);
    canvas.drawRRect(rr(-2.4, -15, 4.8, 15, 2.4), ink);
    canvas.drawRRect(rr(-3.6, -0.5, 7.2, 5, 1.2), linear(-3.6, 0, 3.6, 0, [hexA(0xb9bec6), hexA(0xffffff), hexA(0x8d939b)], [0, 0.45, 1]));
    const tipB = Skia.PathBuilder.Make();
    tipB.moveTo(-3.6, 4.5);
    tipB.cubicTo(-4.2, 10, -1.5, 13.5, 0.6, 16);
    tipB.cubicTo(2.4, 12.5, 4.4, 9.5, 3.6, 4.5);
    tipB.close();
    const tip = tipB.build();
    canvas.drawPath(tip, ink);
    canvas.restore();
  } else if (tool === 3) {
    canvas.drawRRect(rr(-1, -1, 12, 12, 2.5), fill(hexA(GLYPH, 0.62)));
    canvas.drawCircle(-3.5, -3.5, 7.5, line);
  } else if (tool === 4) {
    const s = 'Aa';
    const widths = font.getGlyphWidths(font.getGlyphIDs(s));
    const wsum = widths.reduce((a, b) => a + b, 0);
    canvas.drawText(s, -wsum / 2, 7, ink, font);
  } else {
    for (let k = 2; k >= 0; k--) {
      const y = (k - 1) * 6;
      const p = poly([0, y - 6, 11, y, 0, y + 6, -11, y]);
      if (k === 0) canvas.drawPath(p, ink);
      else {
        canvas.drawPath(p, fill(hexA(TOOL_COLORS[5])));
        canvas.drawPath(p, stroke(hexA(GLYPH, 0.75 - k * 0.15), 1.6));
      }
    }
  }
}

/** The six tool faces, each centred on the origin. */
export function recordFaces(font: SkFont): SkPicture[] {
  const out: SkPicture[] = [];
  for (let i = 0; i < TOOLS; i++) {
    const rec = Skia.PictureRecorder();
    const canvas = rec.beginRecording(Skia.XYWHRect(-ICON, -ICON, ICON * 2, ICON * 2));
    const h = ICON / 2;
    const base = TOOL_COLORS[i];
    canvas.drawRRect(rr(-h, -h, ICON, ICON, R), linear(-h, -h, h * 0.4, h, [shade(base, 0.28), shade(base, 0.05), shade(base, -0.22)], [0, 0.5, 1]));
    // Enamel gloss: a soft sheen across the top half.
    canvas.drawRRect(rr(-h + 3, -h + 2, ICON - 6, ICON * 0.46, R - 3), linear(0, -h, 0, 0, [hexA(0xffffff, 0.22), hexA(0xffffff, 0.02)], [0, 1]));
    canvas.drawRRect(rr(-h + 0.6, -h + 0.6, ICON - 1.2, ICON - 1.2, R - 0.6), stroke(hexA(0xffffff, 0.26), 1));
    canvas.drawRRect(rr(-h + 1.2, -h + 2.4, ICON - 2.4, ICON - 3.2, R - 1), stroke(hexA(0x000000, 0.14), 1));
    canvas.save();
    canvas.translate(0, 1.2);
    // Glyph shadow, then the glyph.
    const shadowRec = Skia.PictureRecorder();
    const sc = shadowRec.beginRecording(Skia.XYWHRect(-ICON, -ICON, ICON * 2, ICON * 2));
    glyph(sc, i, font);
    const glyphPic = shadowRec.finishRecordingAsPicture();
    const layer = Skia.Paint();
    layer.setImageFilter(Skia.ImageFilter.MakeBlur(1.2, 1.2, TileMode.Decal, null));
    layer.setColorFilter(Skia.ColorFilter.MakeBlend(hexA(0x000000, 0.35), BlendMode.SrcIn));
    canvas.saveLayer(layer);
    canvas.drawPicture(glyphPic);
    canvas.restore();
    canvas.restore();
    canvas.drawPicture(glyphPic);
    out.push(rec.finishRecordingAsPicture());
  }
  return out;
}

/** Graphite backdrop and the artboard: a harbour study in progress. */
export function recordBackdrop(g: Geo): SkPicture {
  const rec = Skia.PictureRecorder();
  const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, g.w, g.h));
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), linear(0, 0, 0, g.h, [hexA(0x1d1f24), hexA(0x16181c), hexA(0x111215)], [0, 0.5, 1]));
  const vig = Skia.Paint();
  vig.setShader(Skia.Shader.MakeRadialGradient({ x: g.w / 2, y: g.h * 0.38 }, g.h * 0.75, [hexA(0x000000, 0), hexA(0x000000, 0.45)], [0.5, 1], TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), vig);

  const bx = 20;
  const by = g.boardTop;
  const bw = g.w - 40;
  const bh = Math.max(120, g.panelTop - 16 - by);
  canvas.drawRRect(rr(bx, by + 14, bw, bh, 18), blurred(hexA(0x000000, 0.4), 16));
  canvas.drawRRect(rr(bx, by + 2, bw, bh, 18), blurred(hexA(0x000000, 0.4), 2));
  canvas.drawRRect(rr(bx, by, bw, bh, 18), linear(0, by, 0, by + bh, [hexA(0xf5efe4), hexA(0xefe7d9), hexA(0xe7ddcc)], [0, 0.6, 1]));
  canvas.save();
  canvas.clipRRect(rr(bx, by, bw, bh, 18), ClipOp.Intersect, true);
  const paper = Skia.Paint();
  paper.setShader(Skia.Shader.MakeFractalNoise(0.8, 0.8, 3, 5, 0, 0));
  paper.setAlphaf(0.09);
  paper.setBlendMode(BlendMode.Multiply);
  canvas.drawRect(Skia.XYWHRect(bx, by, bw, bh), paper);
  // Sky wash, the gouache sun, the horizon.
  const horizon = by + bh * 0.56;
  canvas.drawRect(Skia.XYWHRect(bx, by, bw, horizon - by), linear(0, by, 0, horizon, [hexA(0xe9cfae, 0), hexA(0xeab894, 0.55), hexA(0xe7a27e, 0.6)], [0, 0.7, 1]));
  canvas.drawCircle(bx + bw * 0.66, horizon - bh * 0.17, Math.min(30, bh * 0.13), fill(hexA(0xd4572f, 0.88)));
  canvas.drawCircle(bx + bw * 0.66 - 6, horizon - bh * 0.17 - 6, Math.min(30, bh * 0.13) * 0.5, blurred(hexA(0xf08a5c, 0.6), 6));
  // Ink water: wavy dry-brush strokes, shorter toward the horizon.
  for (let k = 0; k < 9; k++) {
    const y = horizon + 8 + k * (bh * 0.42) / 9;
    const len = bw * (0.3 + 0.07 * k);
    const x0 = bx + 14 + ((k * 37) % 60);
    const pB = Skia.PathBuilder.Make();
    pB.moveTo(x0, y);
    for (let x = 0; x <= len; x += 12) pB.lineTo(x0 + x, y + Math.sin(x * 0.06 + k) * 1.8);
    const p = pB.build();
    canvas.drawPath(p, stroke(hexA(0x2a2f3a, 0.55 + 0.04 * k), 1.6 + k * 0.35));
  }
  canvas.drawLine(bx + 10, horizon, bx + bw - 10, horizon, stroke(hexA(0x2a2f3a, 0.8), 1.4));
  // A small sailing boat.
  const boatX = bx + bw * 0.3;
  canvas.drawPath(poly([boatX - 30, horizon + 4, boatX + 30, horizon + 4, boatX + 20, horizon + 14, boatX - 22, horizon + 14]), fill(hexA(0x2a2f3a)));
  canvas.drawLine(boatX, horizon + 4, boatX, horizon - 52, stroke(hexA(0x2a2f3a), 1.6));
  canvas.drawPath(poly([boatX + 2, horizon - 50, boatX + 2, horizon - 2, boatX + 30, horizon - 2]), fill(hexA(0xf7f2e8, 0.95)));
  canvas.drawPath(poly([boatX + 2, horizon - 50, boatX + 2, horizon - 2, boatX + 30, horizon - 2]), stroke(hexA(0x2a2f3a), 1.2));
  canvas.drawPath(poly([boatX - 2, horizon - 40, boatX - 2, horizon - 2, boatX - 22, horizon - 2]), fill(hexA(0x2f7d76, 0.85)));
  // The selected pen path, with anchors and a handle.
  const sel = hexA(0x3f8cff);
  const pathB = Skia.PathBuilder.Make();
  const p0 = [bx + 24, by + bh * 0.3];
  const p1 = [bx + bw * 0.42, by + bh * 0.14];
  const p2 = [bx + bw - 30, by + bh * 0.26];
  pathB.moveTo(p0[0], p0[1]);
  pathB.cubicTo(p0[0] + 40, p0[1] - 30, p1[0] - 60, p1[1], p1[0], p1[1]);
  pathB.cubicTo(p1[0] + 60, p1[1], p2[0] - 50, p2[1] - 30, p2[0], p2[1]);
  const path = pathB.build();
  canvas.drawPath(path, stroke(sel, 1.4));
  canvas.drawLine(p1[0] - 60, p1[1], p1[0] + 60, p1[1], stroke(hexA(0x3f8cff, 0.7), 1));
  for (const [x, y] of [[p1[0] - 60, p1[1]], [p1[0] + 60, p1[1]]]) canvas.drawCircle(x, y, 3, fill(sel));
  for (const [x, y] of [p0, p1, p2]) {
    canvas.drawRect(Skia.XYWHRect(x - 4, y - 4, 8, 8), fill(hexA(0xffffff)));
    canvas.drawRect(Skia.XYWHRect(x - 4, y - 4, 8, 8), stroke(sel, 1.4));
  }
  canvas.restore();
  return rec.finishRecordingAsPicture();
}

/** The dock: an anodised bar with two shadows and a lit top edge, recorded once. */
export function recordDock(g: Geo): SkPicture {
  const rec = Skia.PictureRecorder();
  const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, g.w, g.h));
  const { dockX: x, dockY: y, dockW: w } = g;
  canvas.drawRRect(rr(x + 6, y + 18, w - 12, DOCK_H - 4, 30), blurred(hexA(0x000000, 0.5), 18));
  canvas.drawRRect(rr(x, y + 2, w, DOCK_H, 30), blurred(hexA(0x000000, 0.45), 2.5));
  canvas.drawRRect(rr(x, y, w, DOCK_H, 30), linear(0, y, 0, y + DOCK_H, [hexA(0x34373e), hexA(0x2a2d33), hexA(0x212328), hexA(0x1a1c20)], [0, 0.3, 0.7, 1]));
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(1.4, 0.15, 2, 9, 0, 0));
  grain.setAlphaf(0.06);
  grain.setBlendMode(BlendMode.Overlay);
  canvas.save();
  canvas.clipRRect(rr(x, y, w, DOCK_H, 30), ClipOp.Intersect, true);
  canvas.drawRect(Skia.XYWHRect(x, y, w, DOCK_H), grain);
  canvas.restore();
  canvas.drawRRect(rr(x + 0.5, y + 0.5, w - 1, DOCK_H - 1, 29.5), stroke(hexA(0xffffff, 0.12), 1));
  return rec.finishRecordingAsPicture();
}

/** The recessed plate under the active tool, and its status light. */
export function drawPlate(canvas: SkCanvas, g: Geo, px: number, color: number) {
  'worklet';
  const s = ICON + 10;
  const y = g.cy - s / 2;
  canvas.drawRRect(rr(px - s / 2, y, s, s, 17), linear(0, y, 0, y + s, [hexA(0x0e0f12), hexA(0x16181c), hexA(0x1c1e23)], [0, 0.4, 1]));
  canvas.drawRRect(rr(px - s / 2 + 0.5, y + 0.5, s - 1, s - 1, 16.5), stroke(hexA(0x000000, 0.5), 1));
  canvas.drawLine(px - s / 2 + 12, y + s - 0.5, px + s / 2 - 12, y + s - 0.5, stroke(hexA(0xffffff, 0.12), 1));
  const ly = g.dockY + DOCK_H - 7;
  canvas.drawCircle(px, ly, 5, blurred(hexA(color, 0.6), 4));
  canvas.drawRRect(rr(px - 7, ly - 1.5, 14, 3, 1.5), fill(hexA(color)));
}

/** A tile's two shadows: tight contact and soft ambient, which grows as the tile lifts. */
export function drawTileShadow(canvas: SkCanvas, x: number, y: number, scale: number, lift: number) {
  'worklet';
  const s = ICON * scale;
  canvas.drawRRect(rr(x - s / 2 + 2, y - s / 2 + 2 + Math.min(2, lift * 0.1), s - 4, s, R * scale), blurred(hexA(0x000000, Math.max(0.12, 0.38 - lift * 0.012)), 2));
  canvas.drawRRect(rr(x - s / 2 + 4, y - s / 2 + 8 + lift * 0.45, s - 8, s, R * scale), blurred(hexA(0x000000, 0.3), 7 + lift * 0.35));
}

/**
 Field lines from each pulled tile to the finger: three bowed curves per tile, dashes flowing
 toward the finger (`phase` in pt), plus a faint glow.
 */
export function drawField(canvas: SkCanvas, xs: number[], ys: number[], scale: number[], f: number[], fx: number, fy: number, phase: number) {
  'worklet';
  const glow = Skia.Paint();
  glow.setAntiAlias(true);
  glow.setStyle(1);
  glow.setStrokeWidth(3);
  glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
  const dash = Skia.Paint();
  dash.setAntiAlias(true);
  dash.setStyle(1);
  dash.setStrokeWidth(1.1);
  dash.setStrokeCap(StrokeCap.Round);
  dash.setPathEffect(Skia.PathEffect.MakeDash([5, 7], phase));
  for (let i = 0; i < xs.length; i++) {
    if (f[i] < 0.04) continue;
    const top = ys[i] - (ICON * scale[i]) / 2 + 2;
    for (let k = -1; k <= 1; k++) {
      const sx = xs[i] + k * 9 * scale[i];
      const ex = fx + k * 5;
      const ey = fy + 12;
      const mx = (sx + ex) / 2;
      const my = (top + ey) / 2;
      const dx = ex - sx;
      const dy = ey - top;
      const len = Math.max(1, Math.hypot(dx, dy));
      const bow = k * 22 + (xs[i] - fx) * 0.12;
      const pathB = Skia.PathBuilder.Make();
      pathB.moveTo(sx, top);
      pathB.quadTo(mx + (-dy / len) * bow, my + (dx / len) * bow, ex, ey);
      glow.setColor(hexA(0x7fc8ff, 0.16 * f[i]));
      const path = pathB.build();
      canvas.drawPath(path, glow);
      dash.setColor(hexA(0xa9dcff, (k === 0 ? 0.75 : 0.45) * f[i]));
      canvas.drawPath(path, dash);
    }
  }
}

/** The fingertip: a soft disc with a cool magnetic core. */
export function drawFinger(canvas: SkCanvas, x: number, y: number, a: number) {
  'worklet';
  if (a < 0.01) return;
  const r = 22 * (1.12 - 0.12 * a);
  canvas.drawCircle(x, y, r + 8, blurred(hexA(0x7fc8ff, 0.14 * a), 10));
  canvas.drawCircle(x, y, r, fill(hexA(0xeaf4ff, 0.14 * a)));
  canvas.drawCircle(x, y, r, stroke(hexA(0xeaf4ff, 0.5 * a), 1.4));
  canvas.drawCircle(x, y, 4, blurred(hexA(0xbfe4ff, 0.8 * a), 2));
}
