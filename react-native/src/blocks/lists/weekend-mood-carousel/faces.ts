import { type SkCanvas, Skia, StrokeCap, StrokeJoin, PaintStyle, TileMode } from '@shopify/react-native-skia';
import { interpolateColor } from 'react-native-reanimated';

import type { FaceKind } from './moods';
import { EXPRESSION } from './motion';

// The characters' faces, drawn as clean vectors. Every position and size is measured on the pin's frames
// (pt; x from the character's centre line, y from the screen top). Expressions move single parts: the Zzz pop,
// the foodie mouth opens, the cross reddens, the 3D lenses swap, the heart beats, the tired lids lift and the
// butterflies flap.

const C = {
  sleepyInk: '#29254E',
  sleepyEyes: '#1E1D24',
  sleepyMouth: '#532802',
  sleepyTongue: '#DA4C3C',
  foodieInk: '#2B1703',
  foodieMouth: '#331700',
  foodieTongue: '#D03B34',
  drool: '#44C2F8',
  ink: '#1A1615',
  white: '#FBFBF8',
  frame: '#F6F5F3',
  frameSide: '#D9D7D3',
  frameShadow: 'rgba(80,20,110,0.28)',
  lensRed: ['#FA4250', '#C91F2A'] as const,
  lensBlue: ['#2A4BC2', '#0640A6'] as const,
  bingeMouth: '#3D1F36',
  bingeTongue: '#CF242B',
  heart: '#FB5754',
  scrollInk: '#181514',
  lid: '#E9393E',
  socialInk: '#1B1B1B',
  crescent: '#9A9AA2',
  wing: '#E8FAA4',
  wingBand: '#3CD43C',
  wingDot: '#8A8AE6',
  bfInk: '#14296B',
  bfBody: '#9DB7F2',
};

function fill(color: string) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  return p;
}

function stroke(color: string, width: number) {
  'worklet';
  const p = fill(color);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

/** A quadratic stroke from (x0, y0) to (x1, y1) through control (cx, cy). */
function curve(c: SkCanvas, x0: number, y0: number, cx: number, cy: number, x1: number, y1: number, w: number, color: string) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  b.moveTo(x0, y0);
  b.quadTo(cx, cy, x1, y1);
  c.drawPath(b.build(), stroke(color, w));
}

function ellipse(c: SkCanvas, cx: number, cy: number, rx: number, ry: number, color: string) {
  'worklet';
  c.drawOval(Skia.XYWHRect(cx - rx, cy - ry, rx * 2, ry * 2), fill(color));
}

function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

/** ease-out-back: the small overshoot of the pin's letters. */
function pop(t: number) {
  'worklet';
  const p = clamp01(t);
  const s = 1.7;
  const q = p - 1;
  return p <= 0 ? 0 : 1 + (s + 1) * q * q * q + s * q * q;
}

/** A "Z" in the box (x0, y0)-(x1, y1), tilted like the pin's. */
function zed(c: SkCanvas, x0: number, y0: number, x1: number, y1: number, w: number, scale: number) {
  'worklet';
  if (scale <= 0.001) return;
  const cx = (x0 + x1) / 2;
  const cy = (y0 + y1) / 2;
  const hw = (x1 - x0) / 2 - w / 2;
  const hh = (y1 - y0) / 2 - w / 2;
  c.save();
  c.translate(cx, cy);
  c.scale(scale, scale);
  c.rotate(EXPRESSION.zzzTilt, 0, 0);
  const b = Skia.PathBuilder.Make();
  b.moveTo(-hw * 0.82, -hh);
  b.lineTo(hw * 0.82, -hh);
  b.lineTo(-hw * 0.82, hh);
  b.lineTo(hw * 0.82, hh);
  const p = stroke(C.sleepyInk, w);
  p.setStrokeCap(StrokeCap.Square);
  p.setStrokeJoin(StrokeJoin.Miter);
  c.drawPath(b.build(), p);
  c.restore();
}

function heart(c: SkCanvas, cx: number, cy: number, w: number, h: number) {
  'worklet';
  const s = w / 2;
  const top = cy - h / 2;
  const bot = cy + h / 2;
  const b = Skia.PathBuilder.Make();
  b.moveTo(cx, bot);
  b.cubicTo(cx - s * 0.3, bot - h * 0.22, cx - s, cy + h * 0.02, cx - s, top + h * 0.3);
  b.cubicTo(cx - s, top - h * 0.02, cx - s * 0.15, top - h * 0.06, cx, top + h * 0.2);
  b.cubicTo(cx + s * 0.15, top - h * 0.06, cx + s, top - h * 0.02, cx + s, top + h * 0.3);
  b.cubicTo(cx + s, cy + h * 0.02, cx + s * 0.3, bot - h * 0.22, cx, bot);
  b.close();
  c.drawPath(b.build(), fill(C.heart));
}

function poly(pts: number[]) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  b.moveTo(pts[0], pts[1]);
  for (let i = 2; i < pts.length; i += 2) b.lineTo(pts[i], pts[i + 1]);
  b.close();
  return b.build();
}

// Butterfly in its own frame: body centre at (0, 0), head up. Wing outlines are rounded with a corner effect.
const UPPER = [-3, -10, -36, -12, -38, -5, -31, 3, -4, 2];
const LOWER = [-4, 4, -30, 7, -27, 16, -19, 19, -6, 13];

function mirror(pts: number[]) {
  'worklet';
  const out: number[] = [];
  for (let i = 0; i < pts.length; i += 2) {
    out.push(-pts[i]);
    out.push(pts[i + 1]);
  }
  return out;
}

function butterfly(c: SkCanvas, x: number, y: number, angle: number, flap: number) {
  'worklet';
  c.save();
  c.translate(x, y);
  c.rotate(angle, 0, 0);
  const corner = Skia.PathEffect.MakeCorner(4);
  const ink = stroke(C.bfInk, 2.6);
  for (let side = 0; side < 2; side++) {
    c.save();
    c.scale(flap, 1);
    const up = poly(side ? mirror(UPPER) : UPPER);
    const lo = poly(side ? mirror(LOWER) : LOWER);
    const wf = fill(C.wing);
    if (corner) {
      wf.setPathEffect(corner);
      ink.setPathEffect(corner);
    }
    c.drawPath(lo, wf);
    c.save();
    c.clipPath(lo, 1, true);
    c.drawRect(Skia.XYWHRect(side ? 4 : -34, 6, 30, 5), fill(C.wingBand));
    c.restore();
    c.drawPath(lo, ink);
    c.drawPath(up, wf);
    c.drawPath(up, ink);
    const sx = side ? -1 : 1;
    ellipse(c, -24 * sx, -3, 3, 3, C.wingDot);
    ellipse(c, -17 * sx, 11, 3, 3, C.wingDot);
    ellipse(c, -15 * sx, -2, 1.3, 1.3, C.bfInk);
    c.restore();
  }
  // body, head, antennae
  const body = Skia.RRectXY(Skia.XYWHRect(-2.8, -11, 5.6, 35), 2.8, 2.8);
  c.drawRRect(body, fill(C.bfBody));
  c.drawRRect(body, stroke(C.bfInk, 2.2));
  ellipse(c, 0, -14.5, 4.6, 4.6, C.bfBody);
  c.drawCircle(0, -14.5, 4.6, stroke(C.bfInk, 2.2));
  curve(c, -1.5, -18.5, -2.5, -23, -7.5, -23.5, 2.2, C.bfInk);
  curve(c, 1.5, -18.5, 2.5, -23, 7.5, -23.5, 2.2, C.bfInk);
  c.restore();
}

/**
 * Draws the face of `kind`; the canvas origin is the character's centre line (x) and the screen top (y).
 * act: 0 at rest, 1 selected. age: ms since selected. phase: free clock (ms). still: Reduce Motion.
 */
export function drawFace(c: SkCanvas, kind: FaceKind, act: number, age: number, phase: number, still: boolean) {
  'worklet';
  const E = EXPRESSION;
  if (kind === 'sleepy') {
    curve(c, -58.5, 196, -42.8, 211.5, -27.1, 196, 5.6, C.sleepyEyes);
    curve(c, 18.5, 196, 33.8, 211.5, 49.1, 196, 5.6, C.sleepyEyes);
    const mouth = Skia.PathBuilder.Make();
    mouth.addOval(Skia.XYWHRect(-25.3, 218.7, 41.6, 30.3));
    const mp = mouth.build();
    c.drawPath(mp, fill(C.sleepyMouth));
    c.save();
    c.clipPath(mp, 1, true);
    ellipse(c, -4.3, 250, 16.5, 8.6, C.sleepyTongue);
    c.restore();
    const on = act > 0 ? 1 : 0;
    const a = still ? 9999 : age;
    zed(c, 44.7, 161.3, 57, 176.3, 3.2, on * pop(a / E.zzzPopMs));
    zed(c, 67, 138, 90.3, 164, 4.6, on * pop((a - E.zzzStaggerMs) / E.zzzPopMs));
    zed(c, 96, 110, 126, 139.3, 5.6, on * pop((a - E.zzzStaggerMs * 2) / E.zzzPopMs));
  } else if (kind === 'foodie') {
    curve(c, -47, 215.5, -30.4, 197.5, -13.7, 215.5, 5.4, C.foodieInk);
    curve(c, 21.3, 214.5, 37.8, 196.5, 54.3, 214.5, 5.4, C.foodieInk);
    // the mouth opens downward from its top edge; then the drop of drool grows
    const open = Math.max(E.mouthClosed, still ? act : clamp01(age / E.mouthMs) * act);
    c.save();
    c.translate(0, 230);
    c.scale(1, open);
    c.translate(0, -230);
    const m = Skia.PathBuilder.Make();
    m.moveTo(-18.7, 233);
    m.quadTo(7.8, 225, 34.3, 233);
    m.cubicTo(34.3, 262, -18.7, 262, -18.7, 233);
    m.close();
    const mp = m.build();
    c.drawPath(mp, fill(C.foodieMouth));
    c.save();
    c.clipPath(mp, 1, true);
    ellipse(c, 8.5, 260, 19.5, 9.5, C.foodieTongue);
    c.restore();
    c.restore();
    const drool = still ? act : clamp01((age - E.droolDelayMs) / E.droolMs) * act;
    if (drool > 0.01) {
      const top = 261.3;
      const len = 17 * drool;
      const b = Skia.PathBuilder.Make();
      b.moveTo(18.5, top);
      b.lineTo(24.5, top);
      b.cubicTo(27, top + len * 0.55, 27, top + len, 21.5, top + len);
      b.cubicTo(16, top + len, 16, top + len * 0.55, 18.5, top);
      b.close();
      c.drawPath(b.build(), fill(C.drool));
    }
  } else if (kind === 'recovery') {
    curve(c, -47, 219, -28.2, 192, -9.4, 219, 7.6, C.ink);
    curve(c, 44.4, 219, 63.5, 192, 82.6, 219, 7.6, C.ink);
    ellipse(c, -22.2, 253.4, 22.9, 25.4, C.white);
    ellipse(c, 54.9, 253.5, 24.9, 26.2, C.white);
    curve(c, -39, 252.6, -22.9, 269.5, -6.8, 252.6, 5.6, C.ink);
    curve(c, 38.4, 252.3, 54.5, 269.2, 70.6, 252.3, 5.6, C.ink);
    curve(c, -16.3, 301, 18.2, 323, 52.7, 301, 6.4, C.ink);
    const cross = interpolateColor(act, [0, 1], [E.crossRest, E.crossColor]) as string;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-2, 184.4, 33.7, 10.8), 1.5, 1.5), fill(cross));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(9.5, 173.3, 10.8, 33), 1.5, 1.5), fill(cross));
  } else if (kind === 'binge') {
    // shadow of the frame on the face, then the frame's underside and face
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-76, 236, 174, 22), 9, 9), fill(C.frameShadow));
    const frame = Skia.RRectXY(Skia.XYWHRect(-77.7, 197.7, 176.4, 51.3), 5, 5);
    c.drawRRect(frame, fill(C.frameSide));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-77.7, 197.7, 176.4, 46.5), 5, 5), fill(C.frame));
    // nose cut-out
    c.drawOval(Skia.XYWHRect(-2.5, 237, 27, 30), fill(E.bingeBody));
    const flick = Math.floor(phase / E.glassesFlickerMs);
    const swap = act > 0.5 && !still && (((flick + 1) * 7919) % 5) % 2 === 1;
    const lens = (x0: number, x1: number, colors: readonly [string, string], inner: number) => {
      const path = poly([x0, 207, x1, 207, x1 - (inner > 0 ? 4 : 0), 242, x0 + (inner < 0 ? 4 : 0), 242]);
      const lp = Skia.Paint();
      lp.setAntiAlias(true);
      lp.setShader(
        Skia.Shader.MakeLinearGradient(
          { x: x0, y: 207 },
          { x: x1, y: 242 },
          [Skia.Color(colors[0]), Skia.Color(colors[1])],
          null,
          TileMode.Clamp,
        ),
      );
      const corner = Skia.PathEffect.MakeCorner(3);
      if (corner) lp.setPathEffect(corner);
      c.drawPath(path, lp);
    };
    lens(-61.3, -0.3, swap ? C.lensBlue : C.lensRed, 1);
    lens(22, 83, swap ? C.lensRed : C.lensBlue, -1);
    const m = Skia.PathBuilder.Make();
    m.moveTo(-35, 263);
    m.lineTo(55, 263);
    m.cubicTo(55, 324, -35, 324, -35, 263);
    m.close();
    const mp = m.build();
    c.drawPath(mp, fill(C.bingeMouth));
    c.save();
    c.clipPath(mp, 1, true);
    const teeth = Skia.PathBuilder.Make();
    teeth.moveTo(-36, 259);
    teeth.lineTo(56, 259);
    teeth.lineTo(56, 274);
    teeth.quadTo(10, 281, -36, 274);
    teeth.close();
    c.drawPath(teeth.build(), fill('#FFFFFE'));
    ellipse(c, 10, 311, 31, 18, C.bingeTongue);
    c.restore();
  } else if (kind === 'shopping') {
    curve(c, -42.6, 220, -29.2, 200, -15.8, 220, 5.6, C.ink);
    curve(c, 20.8, 220, 34.2, 200, 47.6, 220, 5.6, C.ink);
    curve(c, -28.4, 239.4, 2.2, 267, 32.8, 239.4, 6.2, C.ink);
    const beat = still ? 1 : 1 + E.heartGain * act * Math.max(0, Math.sin((phase / E.heartMs) * Math.PI * 2));
    heart(c, 3.85, 312.4, 49.7 * beat, 42.7 * beat);
  } else if (kind === 'scroll') {
    curve(c, -32, 210, -16.8, 189, -1.6, 210, 6.6, C.scrollInk);
    curve(c, 41, 210, 56.7, 189, 72.4, 210, 6.6, C.scrollInk);
    ellipse(c, -12.2, 240.2, 18.5, 17.5, C.white);
    ellipse(c, 49.3, 240.3, 20.3, 18, C.white);
    ellipse(c, -12.5, 239.2, 8.8, 9.9, C.scrollInk);
    ellipse(c, 44, 239.4, 9, 9.7, C.scrollInk);
    // tired red lids: low at rest, lifted to a sliver while selected
    const lid = E.lidRest + (E.lidSelected - E.lidRest) * act;
    for (let i = 0; i < 2; i++) {
      const ex = i === 0 ? -12.2 : 49.3;
      const rx = i === 0 ? 18.5 : 20.3;
      const top = i === 0 ? 222.7 : 222.3;
      const eye = Skia.PathBuilder.Make();
      eye.addOval(Skia.XYWHRect(ex - rx, top, rx * 2, 36));
      c.save();
      c.clipPath(eye.build(), 1, true);
      c.drawRect(Skia.XYWHRect(ex - rx - 1, top - 1, rx * 2 + 2, 36 * lid + 1), fill(C.lid));
      c.restore();
    }
    curve(c, -6.5, 276.5, 22, 295, 47, 276.5, 6.2, C.scrollInk);
    ellipse(c, -75.2, 249.4, 5.5, 5.5, C.scrollInk);
  } else {
    // social
    curve(c, -41.5, 212, -36, 194, -21.5, 192.5, 6.4, C.socialInk);
    curve(c, 38.5, 190, 60, 188, 70.5, 212, 6.4, C.socialInk);
    for (let i = 0; i < 2; i++) {
      const ex = i === 0 ? -21 : 41;
      const ey = i === 0 ? 238.5 : 237.8;
      ellipse(c, ex + 1, ey + 21, 15, 4, C.crescent);
      ellipse(c, ex, ey, 23.7, 22.2, C.socialInk);
      c.save();
      c.translate(ex - 5.6, ey - 8.4);
      c.rotate(-25, 0, 0);
      ellipse(c, 0, 0, 13.5, 10.6, C.white);
      c.restore();
      ellipse(c, ex + 5.3, ey + 8.3, 5.1, 4.6, C.white);
    }
    curve(c, -14, 283.5, 9.2, 299.5, 32.5, 283.5, 5.6, C.socialInk);
    let flapA = 1;
    let flapB = 1;
    if (!still && act > 0) {
      const t = Math.max(0, age) / E.wingMs;
      flapA = E.wingMin + (1 - E.wingMin) * Math.abs(Math.cos(t * Math.PI));
      flapB = E.wingMin + (1 - E.wingMin) * Math.abs(Math.cos(t * Math.PI + E.wingLag));
    }
    butterfly(c, -80, 201, 0, flapA);
    butterfly(c, 86.5, 299.5, -43, flapB);
  }
}
