import { BlendMode, ClipOp, type SkCanvas, Skia } from '@shopify/react-native-skia';

import { type ClothPose, LOOP, clamp, jiggle } from './clothMotion';
import { fill, linear, radial, rgba, stroke } from './clothPaint';
import { type Geo, planeMatrix, project } from './clothScene';

/*
 The place settings for six, drawn every frame in table-plane coordinates (each through the plane's
 affine matrix at its spot, so a round plate is a true perspective ellipse) or, for upright things
 (glasses, candles), in screen space from their projected base. Every item carries its own jiggle.
 */

/** kind: 0 plate, 1 glass, 2 fork, 3 knife, 4 candle. */
type Item = { kind: number; x: number; y: number; id: number };

const SETTINGS = [
  [-110, 250],
  [0, 250],
  [110, 250],
  [-106, 82],
  [0, 82],
  [106, 82],
];

function buildItems(): Item[] {
  const items: Item[] = [];
  SETTINGS.forEach(([x, y], i) => {
    items.push({ kind: 0, x, y, id: i });
    items.push({ kind: 1, x: x + 34, y: y + 36, id: 10 + i });
    items.push({ kind: 2, x: x - 44, y, id: 20 + i });
    items.push({ kind: 3, x: x + 44, y: y - 4, id: 30 + i });
  });
  items.push({ kind: 4, x: -55, y: 168, id: 40 });
  items.push({ kind: 4, x: 55, y: 162, id: 41 });
  // Farthest first.
  return items.sort((a, b) => b.y - a.y);
}

export const ITEMS = buildItems();

/** Where a dish sits for a pose: [x, y, spin, lean, hop, shadow x, shadow y]. The shadow keeps
 to the table and trails the jiggle, so a hop reads as a lift. */
function placed(it: Item, p: ClothPose) {
  'worklet';
  const j = jiggle(p, it.id, it.y);
  const weight = it.kind === 4 ? 0.4 : 1;
  const y0 = it.y - p.slide * (0.8 + (0.2 * ((it.id * 7) % 5)) / 4);
  return [it.x + j[0] * weight, y0 + j[1] * weight, j[2] * weight, j[3] * weight + (it.kind === 1 ? p.tip : 0), j[4] * weight, it.x + j[0] * weight * 0.4, y0 + j[1] * weight * 0.4];
}

function drawPlate(canvas: SkCanvas, g: Geo, x: number, y: number, spin: number, hop: number, sx: number, sy: number) {
  'worklet';
  const lift = clamp(hop / 9, 0, 1);
  canvas.save();
  canvas.concat(planeMatrix(g, sx, sy, 0));
  canvas.drawCircle(3, -5, 35 + 2 * lift, fill(0x2a1508, 0.3 - 0.1 * lift, 5 + 4 * lift));
  canvas.drawCircle(1, -1.5, 34.5, fill(0x2a1508, 0.3 * (1 - lift), 1.5 + 3 * lift));
  canvas.restore();
  canvas.save();
  canvas.concat(planeMatrix(g, x, y, 1.6 + hop));
  canvas.drawCircle(0, 0, 34, radial(0, 0, 34, [rgba(0xf6f2ea), rgba(0xfbf9f3), rgba(0xe2dacb), rgba(0xfaf7f0), rgba(0xd8cfbf)], [0, 0.6, 0.66, 0.82, 1]));
  canvas.drawCircle(0, 0, 30.5, stroke(0xc9a45a, 0.9, 0.9));
  canvas.drawCircle(-10, 12, 9, fill(0xffffff, 0.55, 5));
  // A folded napkin on the plate: its turn shows the plate's spin.
  canvas.rotate((spin * 180) / Math.PI + 18, 0, 0);
  const nap = Skia.RRectXY(Skia.XYWHRect(-15, -9, 30, 18), 2.5, 2.5);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-14, -10.5, 30, 18), 2.5, 2.5), fill(0x5a2a12, 0.25, 1.5));
  canvas.drawRRect(nap, linear(0, -9, 0, 9, [rgba(0xd27a52), rgba(0xc0603a), rgba(0x9e4a2a)], [0, 0.5, 1]));
  canvas.drawLine(-15, 1, 15, 1, stroke(0x8a3a1e, 0.8, 0.5));
  canvas.drawLine(-14, -7.5, 14, -7.5, stroke(0xf0a882, 0.7, 0.6));
  canvas.restore();
}

function drawCutlery(canvas: SkCanvas, g: Geo, x: number, y: number, spin: number, knife: boolean, hop: number, sx: number, sy: number) {
  'worklet';
  for (const pass of [0, 1]) {
    canvas.save();
    canvas.concat(pass === 0 ? planeMatrix(g, sx + 1.5, sy - 2.5, 0) : planeMatrix(g, x, y, 0.8 + hop));
    canvas.rotate((spin * 180) / Math.PI, 0, 0);
    const p = pass === 0 ? fill(0x1a0c04, 0.35, 1.5) : linear(-3, 0, 3, 0, [rgba(0x8d939b), rgba(0xf2f4f6), rgba(0xa9afb7)], [0, 0.45, 1]);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-1.9, -24, 3.8, 26), 1.9, 1.9), p);
    if (knife) {
      const blade = Skia.PathBuilder.Make().moveTo(-1.6, 2).lineTo(1.9, 2).lineTo(1.9, 24).quadTo(0, 27, -1.6, 22).close().build();
      canvas.drawPath(blade, p);
    } else {
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-3.6, 2, 7.2, 6), 2, 2), p);
      for (let k = 0; k < 4; k += 1) canvas.drawRect(Skia.XYWHRect(-3.6 + k * 2.2, 7, 1.2, 12), p);
    }
    canvas.restore();
  }
}

function drawGlass(canvas: SkCanvas, g: Geo, x: number, y: number, lean: number, hop: number, sx: number, sy: number) {
  'worklet';
  const b = project(g, x, y, hop);
  const s = b[2];
  const c = 0.72 * s;
  // Shadow on the cloth (it stays down when the glass hops), then the glass leaning about its foot.
  canvas.save();
  canvas.concat(planeMatrix(g, sx + 4, sy - 10, 0));
  canvas.drawOval(Skia.XYWHRect(-12, -16, 24, 30), fill(0x2a1508, 0.22, 6 + hop * 0.6));
  canvas.restore();
  canvas.save();
  canvas.translate(b[0], b[1]);
  canvas.rotate(lean, 0, 0);
  const ry = 0.72;
  canvas.drawOval(Skia.XYWHRect(-11 * s, -11 * s * ry, 22 * s, 22 * s * ry), linear(-11 * s, 0, 11 * s, 0, [rgba(0xffffff, 0.35), rgba(0xd9e4e8, 0.18), rgba(0xffffff, 0.4)], [0, 0.5, 1]));
  canvas.drawOval(Skia.XYWHRect(-11 * s, -11 * s * ry, 22 * s, 22 * s * ry), stroke(0xffffff, 0.8, 0.5));
  canvas.drawRect(Skia.XYWHRect(-1.1 * s, -26 * c, 2.2 * s, 26 * c), linear(-1.1 * s, 0, 1.1 * s, 0, [rgba(0xffffff, 0.75), rgba(0xbfd0d6, 0.35)], null));
  const top = 66 * c;
  const bot = 26 * c;
  const R = 15 * s;
  const bowl = Skia.PathBuilder.Make()
    .moveTo(-R, -top)
    .cubicTo(-R * 1.05, -top + 22 * c, -R * 0.55, -bot, 0, -bot)
    .cubicTo(R * 0.55, -bot, R * 1.05, -top + 22 * c, R, -top)
    .close()
    .build();
  // Wine: clipped to the bowl, its surface kept level against the lean.
  canvas.save();
  canvas.clipPath(bowl, ClipOp.Intersect, true);
  canvas.save();
  canvas.translate(0, -44 * c);
  canvas.rotate(-lean * 0.85, 0, 0);
  canvas.drawRect(Skia.XYWHRect(-40 * s, 0, 80 * s, 40 * s), linear(0, 0, 0, 20 * c, [rgba(0x7a1424, 0.92), rgba(0x4a0812, 0.95)], null));
  canvas.drawOval(Skia.XYWHRect(-11 * s, -3.5 * s * ry, 22 * s, 7 * s * ry), fill(0x9c2436, 0.9));
  canvas.restore();
  canvas.restore();
  canvas.drawPath(bowl, fill(0xe8f2f4, 0.14));
  canvas.drawPath(bowl, stroke(0xffffff, 0.9, 0.55));
  canvas.drawOval(Skia.XYWHRect(-R, -top - R * ry * 0.5, R * 2, R * ry), stroke(0xffffff, 1, 0.75));
  canvas.drawLine(-R * 0.62, -top + 6 * c, -R * 0.45, -bot - 8 * c, stroke(0xffffff, 1.6, 0.6));
  canvas.restore();
}

function drawCandle(canvas: SkCanvas, g: Geo, x: number, y: number, lean: number, time: number, id: number) {
  'worklet';
  const b = project(g, x, y, 0);
  const s = b[2];
  const c = 0.72 * s;
  canvas.save();
  canvas.concat(planeMatrix(g, x + 6, y - 14, 0));
  canvas.drawOval(Skia.XYWHRect(-16, -22, 32, 40), fill(0x2a1508, 0.28, 8));
  canvas.restore();
  canvas.save();
  canvas.translate(b[0], b[1]);
  canvas.rotate(lean * 0.5, 0, 0);
  const brass = linear(-14 * s, 0, 14 * s, 0, [rgba(0x7a5420), rgba(0xf0cf86), rgba(0xa77a34), rgba(0x5e3e14)], [0, 0.35, 0.7, 1]);
  canvas.drawOval(Skia.XYWHRect(-14 * s, -14 * s * 0.36, 28 * s, 28 * s * 0.72), brass);
  canvas.drawRect(Skia.XYWHRect(-3 * s, -30 * c, 6 * s, 30 * c), brass);
  canvas.drawOval(Skia.XYWHRect(-8 * s, -32 * c - 4 * s, 16 * s, 8 * s), brass);
  canvas.drawRect(Skia.XYWHRect(-5 * s, -96 * c, 10 * s, 64 * c), linear(-5 * s, 0, 5 * s, 0, [rgba(0xe9dcc2), rgba(0xfff6e2), rgba(0xd2c19f)], [0, 0.4, 1]));
  canvas.drawOval(Skia.XYWHRect(-5 * s, -96 * c - 2 * s, 10 * s, 4 * s), fill(0xfff1d2));
  // Flame: a teardrop that breathes and leans with whole-loop sines.
  const TAU = 2 * Math.PI;
  const flick = 1 + 0.08 * Math.sin((TAU * 27 * time) / LOOP + id) + 0.05 * Math.sin((TAU * 41 * time) / LOOP + 1 + id);
  const sway = 1.2 * Math.sin((TAU * 7 * time) / LOOP + id * 2);
  const fy = -96 * c - 3 * s;
  const fh = 15 * s * flick;
  const flame = Skia.PathBuilder.Make()
    .moveTo(0, fy)
    .cubicTo(-4.5 * s, fy - fh * 0.3, -1.5 * s + sway, fy - fh * 0.8, sway, fy - fh)
    .cubicTo(1.5 * s + sway, fy - fh * 0.8, 4.5 * s, fy - fh * 0.3, 0, fy)
    .close()
    .build();
  canvas.drawPath(flame, linear(0, fy, 0, fy - fh, [rgba(0xff8a2a), rgba(0xffd36a), rgba(0xfff6d8)], [0, 0.4, 1]));
  canvas.drawOval(Skia.XYWHRect(-1.3 * s, fy - 4 * s, 2.6 * s, 4 * s), fill(0x3a6ad8, 0.5, 1));
  const glow = radial(0, fy - fh * 0.5, 70 * s * flick, [rgba(0xffb35a, 0.32), rgba(0xff8a3a, 0.1), rgba(0xff8a3a, 0)], [0, 0.4, 1]);
  glow.setBlendMode(BlendMode.Plus);
  canvas.drawCircle(0, fy - fh * 0.5, 70 * s * flick, glow);
  canvas.restore();
}

/** Candlelight pooled on the cloth, under the dishes. */
export function drawCandlePools(canvas: SkCanvas, g: Geo, time: number) {
  'worklet';
  const TAU = 2 * Math.PI;
  for (let i = 0; i < 2; i += 1) {
    const flick = 1 + 0.06 * Math.sin((TAU * 27 * time) / LOOP + 40 + i);
    canvas.save();
    canvas.concat(planeMatrix(g, i === 0 ? -55 : 55, i === 0 ? 168 : 162, 0));
    const p = radial(0, 0, 150 * flick, [rgba(0xffb35a, 0.2), rgba(0xffb35a, 0.06), rgba(0xffb35a, 0)], [0, 0.45, 1]);
    p.setBlendMode(BlendMode.Plus);
    canvas.drawCircle(0, 0, 150 * flick, p);
    canvas.restore();
  }
}

export function drawDishes(canvas: SkCanvas, g: Geo, p: ClothPose) {
  'worklet';
  for (let i = 0; i < ITEMS.length; i += 1) {
    const it = ITEMS[i];
    const [x, y, spin, lean, hop, sx, sy] = placed(it, p);
    if (it.kind === 0) drawPlate(canvas, g, x, y, spin, hop, sx, sy);
    else if (it.kind === 1) drawGlass(canvas, g, x, y, clamp(lean, -24, 24), hop, sx, sy);
    else if (it.kind === 2 || it.kind === 3) drawCutlery(canvas, g, x, y, spin * 1.6, it.kind === 3, hop, sx, sy);
    else drawCandle(canvas, g, x, y, lean, p.time, it.id);
  }
}
