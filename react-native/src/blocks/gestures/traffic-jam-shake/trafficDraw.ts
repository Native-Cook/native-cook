import { PaintStyle, Skia, StrokeCap, StrokeJoin, type SkCanvas, type SkPaint } from '@shopify/react-native-skia';

import { DIR_X, DIR_Y, HONK_LIFE, smooth, type Car } from './trafficSim';

// The car drawing of TrafficJamShake.swift: a flat car in oblique projection (front face flat to
// the viewer, the body extruded 34° up-right), one solid colour per face, thin dark outlines and a
// hard ground shadow. Coordinates are design points; the caller scales the canvas. Worklets, each
// declared above its callers.

export const C = {
  background: '#ECEBE7',
  ink: '#1B1B1F',
  outline: '#1E1E24',
  shadow: '#141418',
  window: '#121216',
  light: '#FFE01F',
  hub: '#F4F1E8',
  card: '#FFFFFF',
  red: '#D7263D',
  redSoft: '#FBE3E6',
  green: '#22A447',
  orange: '#F28A0C',
  jam: '#E0102A',
};

/** Per body colour: front, top (14 % white), side (12 % black). Pink, blue, green, orange, red. */
const BODIES = [
  ['#F38BF0', '#F59BF2', '#D67AD3'],
  ['#139FE6', '#34ACEA', '#118CCA'],
  ['#17A833', '#37B450', '#14942D'],
  ['#F48C10', '#F69C31', '#D77B0E'],
  ['#E1121F', '#E5333E', '#C6101B'],
];

export function makePaints() {
  'worklet';
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeJoin(StrokeJoin.Round);
  return { fill, line };
}
type Paints = { fill: SkPaint; line: SkPaint };

function setColor(p: SkPaint, hex: string, alpha: number) {
  'worklet';
  p.setColor(Skia.Color(hex));
  p.setAlphaf(alpha);
}

/** A closed polygon from flat [x0, y0, x1, y1, …]; filled, and outlined unless `outline` is false. */
function poly(canvas: SkCanvas, paints: Paints, pts: number[], color: string, alpha: number, outline: boolean) {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  builder.moveTo(pts[0], pts[1]);
  for (let i = 2; i < pts.length; i += 2) builder.lineTo(pts[i], pts[i + 1]);
  builder.close();
  const path = builder.build();
  setColor(paints.fill, color, alpha);
  canvas.drawPath(path, paints.fill);
  if (outline) {
    setColor(paints.line, C.outline, 1);
    paints.line.setStrokeWidth(1);
    canvas.drawPath(path, paints.line);
  }
}

/** The quad shrunk towards its centre by k. */
function inset(pts: number[], k: number) {
  'worklet';
  let cx = 0;
  let cy = 0;
  const n = pts.length / 2;
  for (let i = 0; i < pts.length; i += 2) {
    cx += pts[i] / n;
    cy += pts[i + 1] / n;
  }
  const out: number[] = [];
  for (let i = 0; i < pts.length; i += 2) out.push(cx + (pts[i] - cx) * k, cy + (pts[i + 1] - cy) * k);
  return out;
}

function oval(canvas: SkCanvas, paints: Paints, x: number, y: number, w: number, h: number, color: string) {
  'worklet';
  setColor(paints.fill, color, 1);
  canvas.drawOval(Skia.XYWHRect(x, y, w, h), paints.fill);
}

/**
 * One car in its own frame (the canvas is already at the ground point, turned by its angle).
 * `e` stretches the width and squashes the heights; wheels and lights keep their size.
 */
export function drawCar(canvas: SkCanvas, paints: Paints, c: Car, e: number, z: number) {
  'worklet';
  const colors = BODIES[c.color];
  const front = colors[0];
  const top = colors[1];
  const side = colors[2];
  const w = c.w * (1 + e);
  const k = Math.max(0.6, 1 - 0.6 * e);
  const hb = c.hb * k;
  const hc = c.hc * k;
  const dx = c.l * DIR_X;
  const dy = c.l * DIR_Y;

  // The hard ground shadow stays on the ground and pales as the car lifts.
  poly(canvas, paints, [-w / 2, 6, w / 2, 6, w / 2 + dx, 6 + dy, -w / 2 + dx, 6 + dy], C.shadow, Math.max(0.35, 1 - z / 50), false);

  canvas.save();
  canvas.translate(0, -z);
  const l = -w / 2;
  const r = w / 2;
  // Body side, then wheels on its bottom edge, then the top.
  poly(canvas, paints, [r, 0, r, -hb, r + dx, -hb + dy, r + dx, dy], side, 1, true);
  const rw = Math.min(8, Math.max(5, 0.3 * c.hb));
  for (let n = 0; n < 2; n++) {
    const f = n === 0 ? 0.22 : 0.78;
    const cx = r + f * dx;
    const cy = f * dy - rw * 0.15;
    oval(canvas, paints, cx - rw * 0.62, cy - rw, rw * 1.24, rw * 2, C.window);
    oval(canvas, paints, cx - rw * 0.3, cy - rw * 0.52, rw * 0.6, rw * 1.04, C.hub);
  }
  poly(canvas, paints, [l, -hb, r, -hb, r + dx, -hb + dy, l + dx, -hb + dy], top, 1, true);

  // Cabin: set back 18-82 % of the length, narrower at the roof.
  const c0x = 0.18 * dx;
  const c0y = 0.18 * dy;
  const cdx = 0.64 * dx;
  const cdy = 0.64 * dy;
  const fblX = -0.42 * w + c0x;
  const fblY = -hb + c0y;
  const fbrX = 0.42 * w + c0x;
  const fbrY = -hb + c0y;
  const ftrX = 0.33 * w + c0x;
  const ftrY = -hb - hc + c0y;
  const ftlX = -0.33 * w + c0x;
  const ftlY = -hb - hc + c0y;
  const sidePts = [fbrX, fbrY, ftrX, ftrY, ftrX + cdx, ftrY + cdy, fbrX + cdx, fbrY + cdy];
  poly(canvas, paints, sidePts, side, 1, true);
  const win = inset(sidePts, 0.7);
  poly(canvas, paints, win, C.window, 1, false);
  setColor(paints.line, side, 1);
  paints.line.setStrokeWidth(2.5);
  canvas.drawLine((win[0] + win[6]) / 2, (win[1] + win[7]) / 2, (win[2] + win[4]) / 2, (win[3] + win[5]) / 2, paints.line);
  poly(canvas, paints, [ftlX, ftlY, ftrX, ftrY, ftrX + cdx, ftrY + cdy, ftlX + cdx, ftlY + cdy], top, 1, true);
  const frontPts = [fblX, fblY, fbrX, fbrY, ftrX, ftrY, ftlX, ftlY];
  poly(canvas, paints, frontPts, front, 1, true);
  poly(canvas, paints, inset(frontPts, 0.78), C.window, 1, false);

  // Body front and its lights.
  poly(canvas, paints, [l, 0, r, 0, r, -hb, l, -hb], front, 1, true);
  const lw = Math.max(8, 0.12 * w);
  const lh = Math.max(4, 0.13 * hb);
  const ly = -0.55 * hb - lh / 2;
  for (let n = 0; n < 2; n++) {
    const x = n === 0 ? l + 0.08 * w : r - 0.08 * w - lw;
    const rect = Skia.XYWHRect(x, ly, lw, lh);
    setColor(paints.fill, C.light, 1);
    canvas.drawRect(rect, paints.fill);
    setColor(paints.line, C.outline, 1);
    paints.line.setStrokeWidth(0.8);
    canvas.drawRect(rect, paints.line);
  }
  canvas.restore();
}

/** Three strokes fanning out from the car's roof corner, `age` seconds after the honk. */
export function drawHonk(canvas: SkCanvas, paints: Paints, c: Car, e: number, z: number, age: number, alpha: number) {
  'worklet';
  if (age >= HONK_LIFE || alpha <= 0) return;
  const q = age / HONK_LIFE;
  const fade = (q < 0.15 ? q / 0.15 : 1 - smooth(q, 0.5, 1)) * alpha;
  if (fade <= 0) return;
  const w = c.w * (1 + e);
  const k = Math.max(0.6, 1 - 0.6 * e);
  const ox = -0.36 * w;
  const oy = -c.hb * k - c.hc * k * 0.7 - z;
  const r0 = 6 + 10 * q;
  const len = 12 * (1 - 0.4 * q);
  const dirs = [-0.99, -0.15, -0.75, -0.66, -0.3, -0.95];
  setColor(paints.line, C.ink, Math.min(1, fade));
  paints.line.setStrokeWidth(3.5);
  paints.line.setStrokeCap(StrokeCap.Round);
  for (let n = 0; n < dirs.length; n += 2) {
    const ux = dirs[n];
    const uy = dirs[n + 1];
    canvas.drawLine(ox + ux * r0, oy + uy * r0, ox + ux * (r0 + len), oy + uy * (r0 + len), paints.line);
  }
  paints.line.setStrokeCap(StrokeCap.Butt);
}

