import {
  BlendMode,
  BlurStyle,
  ClipOp,
  FilterMode,
  MipmapMode,
  PaintStyle,
  type SkCanvas,
  type SkImage,
  type SkPaint,
  type SkShader,
  Skia,
  StrokeCap,
  TileMode,
} from '@shopify/react-native-skia';

import { type Geo, type Pose, hash } from './popcornMotion';

/*
 Popcorn Pop Button — the drawing, one Skia canvas. Baked once (at 2-3x): the velvet room with the
 movie card, the button's plate with its thermometer track, the copper pan, the glass lid, the
 striped bowl, and an atlas of 4 kernels and 6 corns. Per frame: the heat (arc, glow under the pan,
 warm tint inside it), then kernels, hull flecks and corns from the atlas, the lid and the bowl,
 each under its own transform.
*/

export type Baked = { img: SkImage; x: number; y: number; w: number; h: number };
export type Layers = { bg: Baked; plate: Baked; pan: Baked; lid: Baked; bowl: Baked; atlas: SkImage; cell: number; arc: SkShader };
export type Layout = Geo & { cardX: number; cardY: number; cardW: number; cardH: number; footerY: number; arcR: number };

/** Thermometer arc: starts at 125° (Skia degrees, clockwise from +x) and sweeps 290°, leaving the label's gap at the bottom. */
export const ARC_START = 125;
export const ARC_SWEEP = 290;

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const cardX = 20;
  const cardY = top + 104;
  const cardW = w - 40;
  const cardH = 96;
  const footerY = h - Math.max(bottom, 14) - 58;
  const a0 = cardY + cardH + 22;
  const a1 = footerY - 18;
  const D = Math.min(w - 44, a1 - a0, 344);
  const bx = w / 2;
  const by = (a0 + a1) / 2;
  return {
    w,
    h,
    bx,
    by,
    D,
    px: bx,
    py: by - D * 0.06,
    rx: D * 0.3,
    ry: D * 0.19,
    wall: D * 0.055,
    dome: D * 0.11,
    bowlX: cardX + cardW - 42,
    bowlY: cardY + cardH / 2 - 4,
    cardX,
    cardY,
    cardW,
    cardH,
    footerY,
    arcR: D / 2 - 15,
  };
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
  return p;
}

function oval(cx: number, cy: number, rx: number, ry: number) {
  'worklet';
  return Skia.XYWHRect(cx - rx, cy - ry, rx * 2, ry * 2);
}

function colors(list: string[]) {
  'worklet';
  const out = [];
  for (let i = 0; i < list.length; i += 1) out.push(Skia.Color(list[i]));
  return out;
}

function radial(x: number, y: number, r: number, list: string[], pos: number[] | null = null) {
  'worklet';
  return Skia.Shader.MakeRadialGradient(Skia.Point(x, y), Math.max(0.1, r), colors(list), pos, TileMode.Clamp);
}

function linear(x0: number, y0: number, x1: number, y1: number, list: string[], pos: number[] | null = null, mode = TileMode.Clamp) {
  'worklet';
  return Skia.Shader.MakeLinearGradient(Skia.Point(x0, y0), Skia.Point(x1, y1), colors(list), pos, mode);
}

function blurred(color: string, alpha: number, sigma: number) {
  'worklet';
  const p = paint(color, alpha);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

export function drawBaked(c: SkCanvas, b: Baked, p: SkPaint) {
  'worklet';
  c.drawImageRectOptions(b.img, Skia.XYWHRect(0, 0, b.img.width(), b.img.height()), Skia.XYWHRect(b.x, b.y, b.w, b.h), FilterMode.Linear, MipmapMode.None, p);
}

function grain(alpha: number, seed: number) {
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 2, seed, 0, 0));
  p.setColorFilter(Skia.ColorFilter.MakeMatrix([0.33, 0.33, 0.33, 0, 0, 0.33, 0.33, 0.33, 0, 0, 0.33, 0.33, 0.33, 0, 0, 0, 0, 0, 0, alpha]));
  p.setBlendMode(BlendMode.Overlay);
  return p;
}

function bake(x: number, y: number, w: number, h: number, scale: number, draw: (c: SkCanvas) => void): Baked | null {
  const surface = Skia.Surface.Make(Math.ceil(w * scale), Math.ceil(h * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  c.translate(-x, -y);
  draw(c);
  surface.flush();
  return { img: surface.makeImageSnapshot(), x, y, w, h };
}

/** The room: a wine velvet wall with a curtain over the header, a warm spot behind the button, the movie card. */
function drawRoom(c: SkCanvas, g: Layout, top: number) {
  const { w, h } = g;
  const bg = Skia.Paint();
  bg.setShader(linear(0, 0, 0, h, ['#2B1016', '#200B11', '#16070C', '#110509'], [0, 0.35, 0.75, 1]));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), bg);
  // Curtain folds behind the header, fading out downwards.
  const band = top + 150;
  c.saveLayer(undefined, Skia.XYWHRect(0, 0, w, band));
  const folds = Skia.Paint();
  folds.setShader(linear(0, 0, 31, 0, ['#2A0C12', '#5C1D27', '#7A2A33', '#4A161E', '#2A0C12'], [0, 0.3, 0.45, 0.7, 1], TileMode.Repeat));
  c.drawRect(Skia.XYWHRect(0, 0, w, band), folds);
  const fade = Skia.Paint();
  fade.setShader(linear(0, 0, 0, band, ['rgba(0,0,0,0.85)', 'rgba(0,0,0,0.55)', 'rgba(0,0,0,0)'], [0, 0.5, 1]));
  fade.setBlendMode(BlendMode.DstIn);
  c.drawRect(Skia.XYWHRect(0, 0, w, band), fade);
  c.restore();
  // A warm spot behind the button.
  const spot = Skia.Paint();
  spot.setShader(radial(g.bx, g.by - g.D * 0.05, g.D * 0.95, ['rgba(140,58,34,0.55)', 'rgba(96,34,26,0.25)', 'rgba(60,18,20,0)'], [0, 0.55, 1]));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), spot);
  // Vignette.
  const vig = Skia.Paint();
  vig.setShader(radial(w / 2, h * 0.52, Math.hypot(w, h) * 0.62, ['rgba(10,3,5,0)', 'rgba(10,3,5,0.25)', 'rgba(8,2,4,0.62)'], [0, 0.6, 1]));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), vig);
  c.drawRect(Skia.XYWHRect(0, 0, w, h), grain(0.07, 3));
  // The movie card: two shadows, a plum face, a lit top edge.
  const { cardX: x, cardY: y, cardW: cw, cardH: ch } = g;
  const rr = Skia.RRectXY(Skia.XYWHRect(x, y, cw, ch), 20, 20);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y + 14, cw, ch), 20, 20), blurred('#050102', 0.5, 16));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y + 1.5, cw, ch), 20, 20), blurred('#050102', 0.45, 2));
  const face = Skia.Paint();
  face.setShader(linear(x, y, x, y + ch, ['#43202A', '#33171F', '#2A1219'], [0, 0.5, 1]));
  c.drawRRect(rr, face);
  const edge = Skia.Paint();
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1);
  edge.setShader(linear(x, y, x, y + ch, ['rgba(255,214,190,0.22)', 'rgba(255,214,190,0.05)', 'rgba(0,0,0,0.25)'], [0, 0.4, 1]));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 0.5, y + 0.5, cw - 1, ch - 1), 19.5, 19.5), edge);
  // The poster: a harbour at night under a big moon.
  const px = x + 12;
  const py = y + 12;
  const pw = 60;
  const ph = ch - 24;
  c.save();
  c.clipRRect(Skia.RRectXY(Skia.XYWHRect(px, py, pw, ph), 10, 10), ClipOp.Intersect, true);
  const sky = Skia.Paint();
  sky.setShader(linear(0, py, 0, py + ph, ['#16223F', '#2C3F66', '#9A5A55', '#E39A5E'], [0, 0.4, 0.62, 0.7]));
  c.drawRect(Skia.XYWHRect(px, py, pw, ph), sky);
  c.drawCircle(px + pw * 0.66, py + ph * 0.3, 15, blurred('#FBE7C0', 0.35, 8));
  const moon = Skia.Paint();
  moon.setShader(radial(px + pw * 0.62, py + ph * 0.26, 11, ['#FFF6DF', '#F3DDB0', '#D9B880']));
  c.drawCircle(px + pw * 0.66, py + ph * 0.3, 9.5, moon);
  const sea = Skia.Paint();
  sea.setShader(linear(0, py + ph * 0.7, 0, py + ph, ['#233A5C', '#14213B', '#0C1528']));
  c.drawRect(Skia.XYWHRect(px, py + ph * 0.7, pw, ph * 0.3), sea);
  for (let i = 0; i < 7; i += 1) {
    const ly = py + ph * (0.74 + i * 0.035);
    const lw = 6 + 10 * hash(i + 3);
    c.drawLine(px + pw * 0.66 - lw / 2, ly, px + pw * 0.66 + lw / 2, ly, stroke('#F6D9A8', 1, 0.55 - i * 0.06));
  }
  // A lighthouse on the left headland.
  const land = Skia.PathBuilder.Make()
    .moveTo(px, py + ph * 0.7)
    .lineTo(px + pw * 0.34, py + ph * 0.68)
    .lineTo(px + pw * 0.4, py + ph * 0.72)
    .lineTo(px, py + ph * 0.74)
    .close()
    .build();
  c.drawPath(land, paint('#0D1424'));
  c.drawRect(Skia.XYWHRect(px + pw * 0.16, py + ph * 0.5, 4, ph * 0.19), paint('#E9E0CF'));
  c.drawCircle(px + pw * 0.16 + 2, py + ph * 0.5, 3, blurred('#FFE7A8', 0.9, 3));
  c.drawRect(Skia.XYWHRect(px, py, pw, ph), grain(0.08, 9));
  c.restore();
  // The bowl's place: a soft round well on the card's right.
  c.drawCircle(g.bowlX, g.bowlY + 12, 30, paint('#1E0B11', 0.55));
  const well = Skia.Paint();
  well.setStyle(PaintStyle.Stroke);
  well.setStrokeWidth(1);
  well.setShader(linear(0, g.bowlY - 18, 0, g.bowlY + 42, ['rgba(0,0,0,0.35)', 'rgba(255,214,190,0.12)']));
  c.drawCircle(g.bowlX, g.bowlY + 12, 30, well);
  // Footer rule and dividers.
  c.drawLine(24, g.footerY - 10, w - 24, g.footerY - 10, stroke('#F3D9C8', 1, 0.08));
  for (let i = 1; i < 3; i += 1) c.drawLine((w * i) / 3, g.footerY + 6, (w * i) / 3, g.footerY + 40, stroke('#F3D9C8', 1, 0.1));
}

/** The button's plate: shadows, a deep cocoa disc with a lit rim, the thermometer's track and ticks. */
function drawPlate(c: SkCanvas, g: Layout) {
  const R = g.D / 2;
  const { bx, by } = g;
  c.drawCircle(bx, by + 20, R * 0.98, blurred('#040102', 0.55, 26));
  c.drawCircle(bx, by + 2, R, blurred('#040102', 0.5, 3));
  const disc = Skia.Paint();
  disc.setShader(radial(bx - R * 0.3, by - R * 0.45, R * 1.6, ['#56281E', '#3A1914', '#24100D', '#170A08'], [0, 0.35, 0.7, 1]));
  c.drawCircle(bx, by, R, disc);
  c.save();
  c.clipPath(Skia.PathBuilder.Make().addCircle(bx, by, R).build(), ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(bx - R, by - R, R * 2, R * 2), grain(0.06, 5));
  c.restore();
  const rim = Skia.Paint();
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(1.5);
  rim.setShader(linear(0, by - R, 0, by + R, ['rgba(255,200,165,0.42)', 'rgba(255,200,165,0.08)', 'rgba(0,0,0,0.4)'], [0, 0.45, 1]));
  c.drawCircle(bx, by, R - 0.75, rim);
  // Recessed track.
  const ar = g.arcR;
  const arcOval = oval(bx, by, ar, ar);
  const track = stroke('#0D0504', 9);
  c.drawArc(arcOval, ARC_START, ARC_SWEEP, false, track);
  c.drawArc(oval(bx, by + 1.2, ar, ar), ARC_START + 4, ARC_SWEEP - 8, false, stroke('#FFC8A8', 1, 0.07));
  for (let i = 0; i <= 10; i += 1) {
    const a = ((ARC_START + (ARC_SWEEP * i) / 10) * Math.PI) / 180;
    const r0 = ar + 9;
    const r1 = ar + (i % 5 === 0 ? 14 : 12);
    c.drawLine(bx + Math.cos(a) * r0, by + Math.sin(a) * r0, bx + Math.cos(a) * r1, by + Math.sin(a) * r1, stroke('#F2C9A8', 1.2, i % 5 === 0 ? 0.4 : 0.2));
  }
}

/** The copper pan seen from above at an angle: front wall, rim, dark seasoned floor, a walnut handle. */
function drawPan(c: SkCanvas, g: Layout) {
  const { px, py, rx, ry, wall, D } = g;
  // Contact shadow on the plate.
  c.drawOval(oval(px + 2, py + wall + ry * 0.2, rx * 1.02, ry * 0.9), blurred('#050102', 0.55, 8));
  // Handle: brass collar and walnut grip, angled up to the left.
  c.save();
  c.translate(px - rx * 0.96, py + ry * 0.12);
  c.rotate(-10, 0, 0);
  const len = D * 0.17;
  const grip = Skia.Paint();
  grip.setShader(linear(0, -7, 0, 7, ['#8A5634', '#5B3320', '#3A1F13'], [0, 0.45, 1]));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-len, -6.5, len - 8, 13), 6.5, 6.5), grip);
  c.drawLine(-len + 7, -3.5, -14, -3.5, stroke('#D9A47A', 1.2, 0.35));
  const collar = Skia.Paint();
  collar.setShader(linear(0, -5, 0, 5, ['#F2D08A', '#B8863C', '#6E4A1C']));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-12, -5, 14, 10), 2, 2), collar);
  c.drawCircle(-6, 0, 1.6, paint('#FFF0C8', 0.7));
  c.restore();
  // Front wall: from the rim down to the base ellipse.
  const body = Skia.PathBuilder.Make()
    .moveTo(px - rx - 3, py)
    .lineTo(px - rx * 0.95, py + wall)
    .arcToOval(oval(px, py + wall, rx * 0.95, ry * 0.95), 180, -180, false)
    .lineTo(px + rx + 3, py)
    .close()
    .build();
  const wallP = Skia.Paint();
  wallP.setShader(linear(px - rx, 0, px + rx, 0, ['#5E2A15', '#B8693F', '#E7A475', '#A9592F', '#4E220F'], [0, 0.28, 0.42, 0.7, 1]));
  c.drawPath(body, wallP);
  c.drawPath(body, (() => {
    const p = Skia.Paint();
    p.setShader(linear(0, py, 0, py + wall + ry, ['rgba(0,0,0,0)', 'rgba(20,6,2,0.45)']));
    return p;
  })());
  // Rim: a thick rolled copper lip.
  const rim = Skia.Paint();
  rim.setShader(linear(px - rx, py - ry, px + rx, py + ry, ['#F6C79B', '#C9794A', '#8C4423', '#D88B58', '#F0B888'], [0, 0.3, 0.55, 0.8, 1]));
  c.drawOval(oval(px, py, rx + 3, ry + 2.5), rim);
  // Floor: dark seasoned steel with an oil sheen.
  const floor = Skia.Paint();
  floor.setShader(radial(px - rx * 0.15, py - ry * 0.2, rx * 1.1, ['#4A4340', '#2E2927', '#1B1716', '#141110'], [0, 0.4, 0.8, 1]));
  c.drawOval(oval(px, py + 1, rx - 3, ry - 2.5), floor);
  for (let i = 1; i <= 4; i += 1) c.drawOval(oval(px, py + 1, (rx - 3) * (i / 5), (ry - 2.5) * (i / 5)), stroke('#8D817A', 0.6, 0.06));
  // Inner wall: the far side of the pan's inside catches light, the near side is in shadow.
  const inner = Skia.Paint();
  inner.setStyle(PaintStyle.Stroke);
  inner.setStrokeWidth(4);
  inner.setShader(linear(0, py - ry, 0, py + ry, ['#C47A4E', '#6B3418', '#2A120A'], [0, 0.45, 1]));
  c.drawOval(oval(px, py + 1.5, rx - 4.5, ry - 4), inner);
  c.drawArc(oval(px, py, rx + 1.5, ry + 1.2), 200, 110, false, stroke('#FFE2C4', 1.4, 0.55));
  c.drawArc(oval(px - rx * 0.1, py - ry * 0.05, rx * 0.55, ry * 0.4), 200, 70, false, stroke('#FFFFFF', 3, 0.05));
}

/** A glass lid: steel rim, a faint dome, two curved specular streaks, a bakelite knob. */
function drawLid(c: SkCanvas, g: Layout) {
  const { px, py, rx, ry, dome } = g;
  const lx = rx * 1.05;
  const ly = ry * 1.05;
  const glass = Skia.Paint();
  glass.setShader(radial(px - lx * 0.3, py - dome - ly * 0.2, lx * 1.4, ['rgba(255,244,230,0.16)', 'rgba(220,235,245,0.07)', 'rgba(200,220,235,0.03)'], [0, 0.5, 1]));
  const domePath = Skia.PathBuilder.Make()
    .moveTo(px - lx, py)
    .cubicTo(px - lx, py - (ly + dome * 0.45) * 1.33, px + lx, py - (ly + dome * 0.45) * 1.33, px + lx, py)
    .arcToOval(oval(px, py, lx, ly), 0, 180, false)
    .close()
    .build();
  c.drawPath(domePath, glass);
  // A broad soft reflection of the room across the far half of the dome.
  c.save();
  c.clipPath(domePath, ClipOp.Intersect, true);
  c.drawOval(oval(px - lx * 0.2, py - ly * 0.75 - dome * 0.4, lx * 0.85, ly * 0.55), blurred('#FFF1E4', 0.12, 10));
  c.restore();
  // The dome's silhouette and a darker band where it meets the rim.
  const outline = Skia.PathBuilder.Make()
    .moveTo(px - lx, py)
    .cubicTo(px - lx, py - (ly + dome * 0.45) * 1.33, px + lx, py - (ly + dome * 0.45) * 1.33, px + lx, py)
    .build();
  c.drawPath(outline, stroke('#F4F8FF', 1.2, 0.22));
  c.drawArc(oval(px, py - dome * 0.18, lx * 0.96, ly * 0.96), 185, 170, false, stroke('#0B0E12', 2.4, 0.12));
  // Streaks follow the dome's curve.
  const streak = Skia.PathBuilder.Make()
    .moveTo(px - lx * 0.78, py - ly * 0.1)
    .cubicTo(px - lx * 0.74, py - dome - ly * 0.45, px - lx * 0.25, py - dome * 1.15 - ly * 0.45, px + lx * 0.08, py - dome * 1.05 - ly * 0.42)
    .build();
  c.drawPath(streak, (() => {
    const p = stroke('#FFFFFF', 3.2, 0.42);
    p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.1, true));
    return p;
  })());
  const streak2 = Skia.PathBuilder.Make()
    .moveTo(px + lx * 0.5, py - dome * 0.7 - ly * 0.3)
    .quadTo(px + lx * 0.72, py - dome * 0.35 - ly * 0.2, px + lx * 0.8, py - ly * 0.05)
    .build();
  c.drawPath(streak2, stroke('#FFFFFF', 1.6, 0.3));
  // Rim.
  const rim = Skia.Paint();
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(3.2);
  rim.setShader(linear(px - lx, py - ly, px + lx, py + ly, ['#F4F6F8', '#9AA1A9', '#5D646C', '#C3C9CF', '#EEF1F4'], [0, 0.3, 0.55, 0.8, 1]));
  c.drawOval(oval(px, py, lx, ly), rim);
  c.drawArc(oval(px, py - 1, lx, ly), 190, 120, false, stroke('#FFFFFF', 1, 0.6));
  // Knob on top.
  const kx = px;
  const ky = py - dome * 1.15;
  c.drawOval(oval(kx, ky + 5, 11, 4), blurred('#000000', 0.35, 2.5));
  c.drawRect(Skia.XYWHRect(kx - 3.5, ky - 1, 7, 6), paint('#8E959C'));
  const knob = Skia.Paint();
  knob.setShader(radial(kx - 3, ky - 6, 13, ['#5A5352', '#2A2423', '#151111'], [0, 0.5, 1]));
  c.drawOval(oval(kx, ky - 3, 11, 6.5), knob);
  c.drawArc(oval(kx, ky - 4, 8, 4), 200, 90, false, stroke('#FFFFFF', 1.2, 0.45));
}

/** One corn: craggy puffs in cream and butter, a sliver of hull, a soft shadow. */
function drawCorn(c: SkCanvas, R: number, v: number) {
  c.drawCircle(R * 0.06, R * 0.2, R * 0.9, blurred('#1A0703', 0.3, R * 0.16));
  c.save();
  c.scale(1 + 0.16 * hash(v + 41), 1 - 0.08 * hash(v + 43));
  const n = 7 + (v % 3);
  const lobes: number[] = [];
  for (let k = 0; k < n; k += 1) {
    const a = (k / n) * Math.PI * 2 + hash(v * 13 + k) * 0.9;
    const d = R * (0.3 + 0.32 * hash(v * 7 + k + 1));
    lobes.push(Math.cos(a) * d, Math.sin(a) * d * 0.9, R * (0.22 + 0.3 * hash(v * 5 + k + 2)));
  }
  lobes.push(-R * 0.05, -R * 0.08, R * 0.4);
  const idx: number[] = [];
  for (let k = 0; k < lobes.length / 3; k += 1) idx.push(k);
  idx.sort((a, b) => lobes[a * 3 + 1] + lobes[a * 3 + 2] * 0.3 - (lobes[b * 3 + 1] + lobes[b * 3 + 2] * 0.3));
  idx.forEach((k, j) => {
    const x = lobes[k * 3];
    const y = lobes[k * 3 + 1];
    const r = lobes[k * 3 + 2];
    if (j === Math.floor(idx.length * 0.6)) {
      const hull = Skia.Paint();
      hull.setShader(linear(R * 0.05, R * 0.2, R * 0.4, R * 0.44, ['#D58E36', '#94501A', '#6E3810']));
      c.save();
      c.rotate(-18 + 40 * hash(v + 3), R * 0.2, R * 0.3);
      c.drawOval(Skia.XYWHRect(R * 0.0, R * 0.2, R * 0.42, R * 0.22), hull);
      c.restore();
    }
    const butter = (v + k) % 3 === 0;
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setShader(
      radial(x - r * 0.4, y - r * 0.45, r * 1.55, butter ? ['#FFF6D6', '#F5DE9C', '#E2BC72', '#B88C4A'] : ['#FFFBF1', '#F4E5C6', '#E2C691', '#B99560'], [0, 0.38, 0.72, 1]),
    );
    c.drawCircle(x, y, r, p);
    // A darker crease on the lower right where the next puff folds in.
    c.drawArc(Skia.XYWHRect(x - r, y - r, r * 2, r * 2), 10, 120, false, stroke('#8E6232', Math.max(0.5, r * 0.12), 0.28));
    c.drawCircle(x - r * 0.38, y - r * 0.42, r * 0.2, blurred('#FFFFFF', 0.45, r * 0.12));
  });
  c.restore();
}

/** One kernel: an amber teardrop with a pale tip. */
function drawKernel(c: SkCanvas, L: number, v: number) {
  const w = L * (0.74 + 0.08 * (v % 2));
  c.drawOval(Skia.XYWHRect(-w * 0.55, L * 0.15, w * 1.1, L * 0.5), blurred('#050101', 0.5, L * 0.12));
  const drop = Skia.PathBuilder.Make()
    .moveTo(0, -L / 2)
    .cubicTo(w * 0.62, -L * 0.28, w * 0.52, L / 2, 0, L / 2)
    .cubicTo(-w * 0.52, L / 2, -w * 0.62, -L * 0.28, 0, -L / 2)
    .close()
    .build();
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(linear(-w / 2, 0, w / 2, L * 0.2, v === 3 ? ['#F2B957', '#D78A2A', '#9C5416'] : ['#F9CF63', '#E59B30', '#AE5F18'], [0, 0.5, 1]));
  c.drawPath(drop, p);
  c.drawOval(Skia.XYWHRect(-w * 0.2, -L * 0.5, w * 0.4, L * 0.26), paint('#F8EBC8', 0.75));
  c.drawLine(-w * 0.22, -L * 0.18, -w * 0.18, L * 0.22, stroke('#FFFFFF', L * 0.08, 0.45));
}

/** A red-and-cream striped bowl with a dome of popcorn on it. */
function drawBowl(c: SkCanvas, g: Layout) {
  const x = g.bowlX;
  const y = g.bowlY;
  const R = 6.5;
  const dome = [-11, 0, 1, -4, -5, 2, 4, -6, 3, 11, -1, 5, 0, 2, 0, -6, 3, 4, 7, 4, 1];
  for (let i = 0; i < dome.length; i += 3) {
    c.save();
    c.translate(x + dome[i], y + dome[i + 1] - 1);
    c.scale(0.95, 0.95);
    drawCorn(c, R, dome[i + 2]);
    c.restore();
  }
  const tw = 44;
  const bw = 30;
  const hh = 38;
  const body = Skia.PathBuilder.Make()
    .moveTo(x - tw / 2, y)
    .lineTo(x + tw / 2, y)
    .lineTo(x + bw / 2, y + hh)
    .quadTo(x, y + hh + 3, x - bw / 2, y + hh)
    .close()
    .build();
  c.drawOval(Skia.XYWHRect(x - bw / 2 - 2, y + hh - 3, bw + 4, 8), blurred('#050102', 0.5, 3));
  c.save();
  c.clipPath(body, ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(x - tw, y, tw * 2, hh + 6), paint('#F2E4CC'));
  for (let s = -3; s <= 3; s += 2) {
    const t0 = s * (tw / 7);
    const b0 = s * (bw / 7);
    const stripe = Skia.PathBuilder.Make()
      .moveTo(x + t0, y)
      .lineTo(x + t0 + tw / 7, y)
      .lineTo(x + b0 + bw / 7, y + hh + 4)
      .lineTo(x + b0, y + hh + 4)
      .close()
      .build();
    c.drawPath(stripe, paint('#C93A33'));
  }
  const shade = Skia.Paint();
  shade.setShader(linear(x - tw / 2, 0, x + tw / 2, 0, ['rgba(40,6,6,0.5)', 'rgba(255,255,255,0.12)', 'rgba(40,6,6,0.05)', 'rgba(40,6,6,0.55)'], [0, 0.3, 0.6, 1]));
  c.drawRect(Skia.XYWHRect(x - tw, y, tw * 2, hh + 6), shade);
  c.restore();
  const band = Skia.Paint();
  band.setShader(linear(0, y - 2, 0, y + 5, ['#FFF7E8', '#E6D2B0', '#B79C74']));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - tw / 2 - 2, y - 2, tw + 4, 6), 3, 3), band);
}

export function bakeLayers(g: Layout, top: number, scale: number): Layers | null {
  const bg = bake(0, 0, g.w, g.h, Math.min(scale, 2), (c) => drawRoom(c, g, top));
  const R = g.D / 2 + 34;
  const plate = bake(g.bx - R, g.by - R, R * 2, R * 2 + 16, scale, (c) => drawPlate(c, g));
  const panX = g.px - g.rx - g.D * 0.22;
  const pan = bake(panX, g.py - g.ry - 8, g.px + g.rx + 14 - panX, g.ry * 2 + g.wall + 30, scale, (c) => drawPan(c, g));
  const lw = g.rx * 1.05 + 6;
  const lid = bake(g.px - lw, g.py - g.dome - g.ry * 1.05 - 16, lw * 2, g.dome + g.ry * 2.1 + 26, scale, (c) => drawLid(c, g));
  const bowl = bake(g.bowlX - 28, g.bowlY - 22, 56, 70, scale, (c) => drawBowl(c, g));
  const cell = g.D * 0.125;
  const atlasSurface = Skia.Surface.Make(Math.ceil(cell * 10 * scale), Math.ceil(cell * scale));
  if (!bg || !plate || !pan || !lid || !bowl || !atlasSurface) return null;
  const ac = atlasSurface.getCanvas();
  ac.scale(scale, scale);
  for (let v = 0; v < 10; v += 1) {
    ac.save();
    ac.translate(cell * (v + 0.5), cell * 0.5);
    if (v < 4) drawKernel(ac, g.D * 0.03, v);
    else drawCorn(ac, g.D * 0.05, v - 4);
    ac.restore();
  }
  atlasSurface.flush();
  const atlas = atlasSurface.makeImageSnapshot();
  // The arc's gradient runs from its start (125°): gold, orange, red. A sweep starts at 0°, so rotate it.
  const a = (ARC_START * Math.PI) / 180;
  const ca = Math.cos(a);
  const sa = Math.sin(a);
  const turn = Skia.Matrix([ca, -sa, g.bx - ca * g.bx + sa * g.by, sa, ca, g.by - sa * g.bx - ca * g.by, 0, 0, 1]);
  const arc = Skia.Shader.MakeSweepGradient(
    g.bx,
    g.by,
    colors(['#FFD86E', '#FF9F3E', '#F45A2F', '#D8302A', '#D8302A']),
    [0, 110 / 360, 210 / 360, ARC_SWEEP / 360, 1],
    TileMode.Clamp,
    turn,
  );
  return { bg, plate, pan, lid, bowl, atlas, cell, arc };
}

function sprite(c: SkCanvas, L: Layers, v: number, x: number, y: number, rot: number, sx: number, sy: number, p: SkPaint) {
  'worklet';
  const k = L.atlas.width() / 10;
  c.save();
  c.translate(x, y);
  c.rotate(rot, 0, 0);
  c.scale(sx, sy);
  c.drawImageRectOptions(L.atlas, Skia.XYWHRect(k * v, 0, k, L.atlas.height()), Skia.XYWHRect(-L.cell / 2, -L.cell / 2, L.cell, L.cell), FilterMode.Linear, MipmapMode.None, p);
  c.restore();
}

export function drawFrame(c: SkCanvas, g: Layout, L: Layers, p: Pose) {
  'worklet';
  const ip = Skia.Paint();
  drawBaked(c, L.bg, ip);
  const s = 1 - 0.03 * p.press;
  c.save();
  c.translate(g.bx, g.by);
  c.scale(s, s);
  c.translate(-g.bx, -g.by);
  drawBaked(c, L.plate, ip);
  if (p.heat > 0.004) {
    const sweep = ARC_SWEEP * p.heat;
    const o = oval(g.bx, g.by, g.arcR, g.arcR);
    const glow = stroke('#FF7A2E', 13, 0.5 * p.heat);
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
    c.drawArc(o, ARC_START, sweep, false, glow);
    const fill = stroke('#FFFFFF', 6);
    fill.setShader(L.arc);
    c.drawArc(o, ARC_START, sweep, false, fill);
    const a = ((ARC_START + sweep) * Math.PI) / 180;
    c.drawCircle(g.bx + Math.cos(a) * g.arcR, g.by + Math.sin(a) * g.arcR, 2.2, paint('#FFF4DA', 0.9));
  }
  c.save();
  c.translate(p.shakeX, p.shakeY);
  if (p.heat > 0.004) {
    const glow = Skia.Paint();
    glow.setShader(radial(g.px, g.py + g.wall, g.rx * 1.35, ['rgba(255,128,46,0.9)', 'rgba(255,92,34,0.35)', 'rgba(255,60,20,0)'], [0, 0.5, 1]));
    glow.setAlphaf(0.75 * p.heat);
    c.drawOval(oval(g.px, g.py + g.wall, g.rx * 1.35, g.ry * 1.5), glow);
  }
  drawBaked(c, L.pan, ip);
  if (p.heat > 0.004) {
    const tint = Skia.Paint();
    tint.setShader(radial(g.px, g.py + g.ry * 0.2, g.rx, ['rgba(255,120,50,0.5)', 'rgba(255,90,40,0.15)', 'rgba(255,80,30,0)'], [0, 0.6, 1]));
    tint.setAlphaf(0.55 * p.heat);
    tint.setBlendMode(BlendMode.Screen);
    c.drawOval(oval(g.px, g.py + 1, g.rx - 3, g.ry - 2.5), tint);
  }
  const kp = Skia.Paint();
  for (let i = 0; i < p.kernels.length; i += 5) {
    kp.setAlphaf(p.kernels[i + 3]);
    sprite(c, L, p.kernels[i + 4], p.kernels[i], p.kernels[i + 1], p.kernels[i + 2], 1, 1, kp);
  }
  const fleck = paint('#8A4A18');
  for (let i = 0; i < p.flecks.length; i += 3) {
    fleck.setAlphaf(p.flecks[i + 2]);
    c.drawOval(Skia.XYWHRect(p.flecks[i] - 1.6, p.flecks[i + 1] - 1, 3.2, 2), fleck);
  }
  for (let i = 0; i < p.corns.length; i += 6) {
    sprite(c, L, 4 + p.corns[i + 5], p.corns[i], p.corns[i + 1], p.corns[i + 2], p.corns[i + 3], p.corns[i + 4], ip);
  }
  c.save();
  c.translate(g.px + p.lidX, g.py + p.lidY);
  c.rotate(p.lidRot, 0, 0);
  c.translate(-g.px, -g.py);
  drawBaked(c, L.lid, ip);
  c.restore();
  c.restore();
  c.restore();
  for (let i = 0; i < p.flying.length; i += 5) {
    const k = p.flying[i + 3];
    sprite(c, L, 4 + p.flying[i + 4], p.flying[i], p.flying[i + 1], p.flying[i + 2], k, k, ip);
  }
  const b = 1 + 0.08 * p.bowl;
  c.save();
  c.translate(g.bowlX, g.bowlY + 36);
  c.scale(2 - b, b);
  c.translate(-g.bowlX, -(g.bowlY + 36));
  drawBaked(c, L.bowl, ip);
  c.restore();
}
