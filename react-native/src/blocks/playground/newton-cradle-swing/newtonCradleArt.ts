import { BlendMode, BlurStyle, ClipOp, type SkCanvas, type SkPicture, Skia, TileMode } from '@shopify/react-native-skia';

import { BALLS } from './newtonCradleMotion';

/*
 Everything drawn in the cradle's canvas. The room (wall, walnut desk, the cradle's base, the back
 posts and rail) and the front posts and rail are recorded once into pictures; one chrome ball is
 recorded once and drawn five times. Per frame only the strings, the balls' positions, their
 contact shadows, the neighbour reflections and the fingertip are drawn.

 Light: a lamp off screen at the upper left. Highlights sit at the balls' upper left, shadows fall
 to the right on the base.
 */

export type Geo = {
  w: number;
  h: number;
  /** Ball radius and string length (pivot to ball centre), pt. */
  R: number;
  L: number;
  cx: number;
  /** Rest x of each ball's pivot. */
  xs: number[];
  pivotY: number;
  restY: number;
  /** Front and back rails (y), and where their posts stand (x from each side). */
  yF: number;
  yB: number;
  frontX: number;
  backX: number;
  /** Top of the base's top face, its front face, and the desk's far edge. */
  baseTop: number;
  baseFront: number;
  baseBottom: number;
  deskY: number;
};

export function makeGeo(w: number, h: number): Geo {
  // Big chrome balls; the strings and the pull angle are chosen so the outer ball, at the
  // furthest pull, still clears the front posts on a 375 pt screen.
  const R = Math.min(Math.max(w * 0.066, 22), 30);
  const L = Math.min(Math.max(w * 0.5, 170), 200);
  const baseTop = h * 0.68;
  const restY = baseTop - 30 - R;
  const pivotY = restY - L;
  const yF = pivotY + 7;
  const cx = w / 2;
  const xs: number[] = [];
  for (let i = 0; i < BALLS; i++) xs.push(cx + (i - 2) * 2 * R);
  return {
    w,
    h,
    R,
    L,
    cx,
    xs,
    pivotY,
    restY,
    yF,
    yB: yF - 14,
    frontX: 8,
    backX: 22,
    baseTop,
    baseFront: baseTop + 16,
    baseBottom: baseTop + 30,
    deskY: baseTop - 22,
  };
}

function hexA(hex: number, a = 1) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 0xff) / 255, ((hex >> 8) & 0xff) / 255, (hex & 0xff) / 255, a);
}

function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898) * 43758.5453;
  return s - Math.floor(s);
}

function blurPaint(color: Float32Array, sigma: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function gradientPaint(x0: number, y0: number, x1: number, y1: number, colors: Float32Array[], pos: number[]) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors, pos, TileMode.Clamp));
  return p;
}

/** A brushed-steel bar: a cylinder lit from the upper left, along x (horizontal) or y (vertical). */
function steelBar(canvas: SkCanvas, x: number, y: number, w: number, h: number, vertical: boolean, dim: number) {
  const colors = [hexA(0x3c4047), hexA(0xdfe3e8), hexA(0xa7adb5), hexA(0x6b7078), hexA(0x2c2f34)].map((c) => {
    c[0] *= dim;
    c[1] *= dim;
    c[2] *= dim;
    return c;
  });
  const pos = [0, 0.28, 0.5, 0.78, 1];
  const p = vertical ? gradientPaint(x, 0, x + w, 0, colors, pos) : gradientPaint(0, y, 0, y + h, colors, pos);
  const r = Math.min(w, h) / 2;
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r), p);
  // Brushing: a few faint lines along the bar.
  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setStyle(1);
  line.setStrokeWidth(0.5);
  for (let k = 0; k < 3; k++) {
    line.setColor(hexA(0xffffff, 0.06 + 0.04 * k));
    const f = 0.3 + k * 0.18;
    if (vertical) canvas.drawLine(x + w * f, y + 2, x + w * f, y + h - 2, line);
    else canvas.drawLine(x + 2, y + h * f, x + w - 2, y + h * f, line);
  }
}

function joint(canvas: SkCanvas, x: number, y: number, r: number, dim: number) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  const c = (hex: number) => {
    const v = hexA(hex);
    v[0] *= dim;
    v[1] *= dim;
    v[2] *= dim;
    return v;
  };
  p.setShader(Skia.Shader.MakeRadialGradient({ x: x - r * 0.4, y: y - r * 0.45 }, r * 1.6, [c(0xf4f6f8), c(0x9aa0a8), c(0x2a2d32)], [0, 0.45, 1], TileMode.Clamp));
  canvas.drawCircle(x, y, r, p);
}

/** The wall, the desk, the base, and the back half of the frame. */
export function recordBack(g: Geo): SkPicture {
  const rec = Skia.PictureRecorder();
  const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, g.w, g.h));
  const { w, h, deskY } = g;

  // Wall: warm charcoal, lit from the upper left.
  canvas.drawRect(Skia.XYWHRect(0, 0, w, deskY), gradientPaint(0, 0, 0, deskY, [hexA(0x221b16), hexA(0x18130f), hexA(0x100d0b)], [0, 0.55, 1]));
  const glow = Skia.Paint();
  glow.setShader(Skia.Shader.MakeRadialGradient({ x: w * 0.06, y: h * 0.1 }, w * 1.05, [hexA(0xffc184, 0.2), hexA(0xffa860, 0.07), hexA(0xff9a50, 0)], [0, 0.45, 1], TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(0, 0, w, deskY), glow);

  // Walnut desk: darker far away, grain lines spreading out toward the viewer.
  canvas.drawRect(
    Skia.XYWHRect(0, deskY, w, h - deskY),
    gradientPaint(0, deskY, 0, h, [hexA(0x24160e), hexA(0x3a2417), hexA(0x4a2e1c), hexA(0x3b2416), hexA(0x1f130c)], [0, 0.18, 0.45, 0.75, 1]),
  );
  const grain = Skia.Paint();
  grain.setAntiAlias(true);
  grain.setStyle(1);
  const LINES = 72;
  for (let k = 0; k < LINES; k++) {
    const f = Math.pow(k / LINES, 1.55);
    const y0 = deskY + 3 + f * (h - deskY);
    const a = hash(k + 1) * 6.28;
    const b = hash(k + 31) * 6.28;
    const pathB = Skia.PathBuilder.Make();
    for (let x = -10; x <= w + 10; x += 14) {
      const y = y0 + Math.sin(x * 0.012 + a) * (1 + f * 2.5) + Math.sin(x * 0.047 + b) * (0.4 + f);
      if (x === -10) pathB.moveTo(x, y);
      else pathB.lineTo(x, y);
    }
    grain.setStrokeWidth(0.5 + f * 1.4 * hash(k + 7));
    grain.setColor(k % 3 === 0 ? hexA(0xb07a4c, 0.08 + 0.05 * f) : hexA(0x120904, 0.22 + 0.18 * hash(k + 3)));
    const path = pathB.build();
    canvas.drawPath(path, grain);
  }
  // The lamp's pool on the desk, and the shade where the desk meets the wall.
  const pool = Skia.Paint();
  pool.setShader(Skia.Shader.MakeRadialGradient({ x: w * 0.22, y: deskY + 40 }, w * 0.9, [hexA(0xffc28a, 0.17), hexA(0xffb070, 0.05), hexA(0xffb070, 0)], [0, 0.5, 1], TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(0, deskY, w, h - deskY), pool);
  canvas.drawRect(Skia.XYWHRect(0, deskY, w, 18), gradientPaint(0, deskY, 0, deskY + 18, [hexA(0x000000, 0.4), hexA(0x000000, 0)], [0, 1]));

  // Base: a smoked-steel slab with a soft shadow on the desk.
  const left = 6;
  const right = w - 6;
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(left + 6, g.baseBottom - 6, right - left - 12, 16), 8, 8), blurPaint(hexA(0x000000, 0.55), 9));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(left + 2, g.baseBottom - 3, right - left - 4, 5), 3, 3), blurPaint(hexA(0x000000, 0.6), 2));
  canvas.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(left, g.baseTop, right - left, g.baseBottom - g.baseTop), 5, 5),
    gradientPaint(0, g.baseTop, 0, g.baseBottom, [hexA(0x2b2d31), hexA(0x3a3d43), hexA(0x33363b), hexA(0x1a1b1e), hexA(0x121315)], [0, 0.4, 0.53, 0.56, 1]),
  );
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(1);
  edge.setStrokeWidth(0.8);
  edge.setColor(hexA(0xffffff, 0.16));
  canvas.drawLine(left + 4, g.baseFront + 0.5, right - 4, g.baseFront + 0.5, edge);
  edge.setColor(hexA(0xffffff, 0.08));
  canvas.drawLine(left + 5, g.baseTop + 0.5, right - 5, g.baseTop + 0.5, edge);

  // Back posts and rail (dimmer: further from the lamp).
  for (const x of [g.backX, w - g.backX]) steelBar(canvas, x - 2.5, g.yB, 5, g.baseTop + 4 - g.yB, true, 0.72);
  steelBar(canvas, g.backX - 2.5, g.yB - 2.5, w - 2 * g.backX + 5, 5, false, 0.72);
  joint(canvas, g.backX, g.yB, 3.6, 0.72);
  joint(canvas, w - g.backX, g.yB, 3.6, 0.72);

  // Film grain over everything (4 %).
  const noise = Skia.Paint();
  noise.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 2, 7, 0, 0));
  noise.setAlphaf(0.045);
  noise.setBlendMode(BlendMode.Overlay);
  canvas.drawRect(Skia.XYWHRect(0, 0, w, h), noise);
  // Vignette.
  const vig = Skia.Paint();
  vig.setShader(Skia.Shader.MakeRadialGradient({ x: w * 0.45, y: h * 0.45 }, Math.hypot(w, h) * 0.62, [hexA(0x000000, 0), hexA(0x000000, 0), hexA(0x000000, 0.45)], [0, 0.6, 1], TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(0, 0, w, h), vig);
  return rec.finishRecordingAsPicture();
}

/** The front posts and rail, drawn over the strings. */
export function recordFront(g: Geo): SkPicture {
  const rec = Skia.PictureRecorder();
  const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, g.w, g.h));
  const { w } = g;
  for (const x of [g.frontX, w - g.frontX]) {
    // Foot plate and post.
    canvas.drawOval(Skia.XYWHRect(x - 7, g.baseFront - 4, 14, 6), blurPaint(hexA(0x000000, 0.45), 1.5));
    steelBar(canvas, x - 3, g.yF, 6, g.baseFront - g.yF, true, 1);
  }
  steelBar(canvas, g.frontX - 3, g.yF - 3, w - 2 * g.frontX + 6, 6, false, 1);
  joint(canvas, g.frontX, g.yF, 4.4, 1);
  joint(canvas, w - g.frontX, g.yF, 4.4, 1);
  return rec.finishRecordingAsPicture();
}

/** One chrome ball centred on the origin. */
export function recordBall(R: number): SkPicture {
  const rec = Skia.PictureRecorder();
  const canvas = rec.beginRecording(Skia.XYWHRect(-R - 4, -R - 4, 2 * R + 8, 2 * R + 8));
  const circleB = Skia.PathBuilder.Make();
  circleB.addCircle(0, 0, R);
  // The room reflected: lit ceiling and wall above, the dark horizon, the walnut desk below.
  canvas.drawCircle(
    0,
    0,
    R,
    gradientPaint(0, -R, 0, R, [hexA(0xf3f5f7), hexA(0xd2d7dd), hexA(0x8d939b), hexA(0x3e3d3c), hexA(0x221c18)], [0, 0.2, 0.38, 0.47, 0.52]),
  );
  canvas.save();
  const circle = circleB.build();
  canvas.clipPath(circle, ClipOp.Intersect, true);
  // The desk's reflection is bounded by a curved horizon (a sphere bends straight lines).
  canvas.drawOval(
    Skia.XYWHRect(-R * 1.3, R * 0.02, R * 2.6, R * 2.1),
    gradientPaint(0, R * 0.02, 0, R, [hexA(0x2a1b12), hexA(0x6c4630), hexA(0x8a5a3a), hexA(0x3a2416), hexA(0x150d08)], [0, 0.14, 0.32, 0.7, 1]),
  );
  // Rim: the reflection compresses and darkens toward the edge.
  const rim = Skia.Paint();
  rim.setShader(Skia.Shader.MakeRadialGradient({ x: -R * 0.08, y: -R * 0.1 }, R * 1.08, [hexA(0x000000, 0), hexA(0x000000, 0), hexA(0x0a0806, 0.42), hexA(0x050403, 0.7)], [0, 0.68, 0.9, 1], TileMode.Clamp));
  canvas.drawCircle(0, 0, R, rim);
  // Warm bounce from the desk along the lower right rim.
  const bounce = Skia.Paint();
  bounce.setAntiAlias(true);
  bounce.setStyle(1);
  bounce.setStrokeWidth(R * 0.13);
  bounce.setColor(hexA(0xffbf8a, 0.32));
  bounce.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, R * 0.06, true));
  canvas.drawArc(Skia.XYWHRect(-R * 0.9, -R * 0.9, R * 1.8, R * 1.8), 10, 70, false, bounce);
  canvas.restore();
  // The window: a soft glow, then four sharp panes, tilted with the sphere's curvature.
  canvas.save();
  canvas.translate(-R * 0.36, -R * 0.44);
  canvas.rotate(-26, 0, 0);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-R * 0.3, -R * 0.2, R * 0.6, R * 0.42), R * 0.12, R * 0.12), blurPaint(hexA(0xffffff, 0.42), R * 0.14));
  const pane = blurPaint(hexA(0xfffdf8, 0.95), 0.45);
  const pw = R * 0.2;
  const ph = R * 0.14;
  for (let r = 0; r < 2; r++)
    for (let c = 0; c < 2; c++) canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-R * 0.22 + c * (pw + R * 0.04), -R * 0.16 + r * (ph + R * 0.04), pw, ph), R * 0.03, R * 0.03), pane);
  canvas.restore();
  // A pin-point of the lamp itself.
  canvas.drawCircle(-R * 0.12, -R * 0.66, R * 0.05, blurPaint(hexA(0xffffff, 0.85), 0.6));
  // A hairline where the ball meets the dark.
  const outline = Skia.Paint();
  outline.setAntiAlias(true);
  outline.setStyle(1);
  outline.setStrokeWidth(0.7);
  outline.setColor(hexA(0x000000, 0.55));
  canvas.drawCircle(0, 0, R - 0.35, outline);
  return rec.finishRecordingAsPicture();
}

export type Frame = {
  back: SkPicture;
  front: SkPicture;
  ball: SkPicture;
};

/**
 Draws one frame. `angles` are the five balls' angles from the vertical (radians); `finger` (0–1)
 shows the scripted fingertip on ball 1.
 */
export function drawCradle(canvas: SkCanvas, g: Geo, art: Frame, angles: number[], finger: number) {
  'worklet';
  canvas.drawPicture(art.back);
  const { R } = g;
  const bx: number[] = [];
  const by: number[] = [];
  for (let i = 0; i < BALLS; i++) {
    bx.push(g.xs[i] + g.L * Math.sin(angles[i]));
    by.push(g.pivotY + g.L * Math.cos(angles[i]));
  }

  // Contact shadows on the base's top face: softer and fainter the higher the ball.
  for (let i = 0; i < BALLS; i++) {
    const lift = Math.max(0, g.baseTop - (by[i] + R));
    const sigma = 2.2 + lift * 0.09;
    const alpha = 0.5 * Math.min(1, 34 / (lift + 4));
    canvas.drawOval(Skia.XYWHRect(bx[i] - R * 1.02 + lift * 0.25, g.baseTop + 4, R * 2.04, 8), blurPaint(hexA(0x000000, alpha), sigma));
  }

  // Strings: two per ball, to the back rail (behind) and the front rail (in front).
  const str = Skia.Paint();
  str.setAntiAlias(true);
  str.setStyle(1);
  str.setStrokeWidth(0.8);
  const attach: number[] = [];
  for (let i = 0; i < BALLS; i++) {
    const ux = g.xs[i] - bx[i];
    const uy = g.pivotY - by[i];
    const ul = Math.hypot(ux, uy);
    attach.push(bx[i] + (ux / ul) * (R + 2), by[i] + (uy / ul) * (R + 2));
  }
  str.setColor(hexA(0xb9b2a6, 0.42));
  for (let i = 0; i < BALLS; i++) canvas.drawLine(attach[2 * i], attach[2 * i + 1], g.cx + (g.xs[i] - g.cx) * 0.93, g.yB, str);

  // Balls, with a dark mirrored neighbour where another ball is close, and the string's eyelet.
  const near = Skia.Paint();
  near.setAntiAlias(true);
  const spot = blurPaint(hexA(0xffffff, 1), 0.5);
  const cap = Skia.Paint();
  cap.setAntiAlias(true);
  for (let i = 0; i < BALLS; i++) {
    canvas.save();
    canvas.translate(bx[i], by[i]);
    canvas.drawPicture(art.ball);
    const clipB = Skia.PathBuilder.Make();
    clipB.addCircle(0, 0, R * 0.97);
    canvas.save();
    const clip = clipB.build();
    canvas.clipPath(clip, ClipOp.Intersect, true);
    for (const j of [i - 1, i + 1]) {
      if (j < 0 || j >= BALLS) continue;
      const dx = bx[j] - bx[i];
      const dy = by[j] - by[i];
      const d = Math.hypot(dx, dy);
      const c = Math.min(Math.max((3.4 * R - d) / (1.4 * R), 0), 1);
      if (c <= 0.01) continue;
      const a = Math.atan2(dy, dx);
      canvas.save();
      canvas.rotate((a * 180) / Math.PI, 0, 0);
      near.setColor(hexA(0x14100d, 0.72 * c));
      canvas.drawOval(Skia.XYWHRect(R * 0.68, -R * 0.34, R * 0.3, R * 0.68), near);
      spot.setAlphaf(0.55 * c);
      canvas.drawCircle(R * 0.8, -R * 0.12, R * 0.045, spot);
      canvas.restore();
    }
    canvas.restore();
    // Eyelet, along the string: rotate so the local −y axis points at the pivot.
    const ang = Math.atan2(g.xs[i] - bx[i], by[i] - g.pivotY);
    canvas.rotate((ang * 180) / Math.PI, 0, 0);
    cap.setShader(Skia.Shader.MakeLinearGradient({ x: -2.5, y: 0 }, { x: 2.5, y: 0 }, [hexA(0x5a5f66), hexA(0xeef0f3), hexA(0x7d838b)], [0, 0.4, 1], TileMode.Clamp));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-R * 0.11, -R - R * 0.2, R * 0.22, R * 0.26), R * 0.06, R * 0.06), cap);
    canvas.restore();
  }

  str.setColor(hexA(0xd9d2c6, 0.58));
  for (let i = 0; i < BALLS; i++) canvas.drawLine(attach[2 * i], attach[2 * i + 1], g.xs[i], g.yF, str);
  canvas.drawPicture(art.front);

  // The scripted fingertip on ball 1.
  if (finger > 0.001) {
    const r = g.R * 1.1 * (1.12 - 0.12 * finger);
    const tip = Skia.Paint();
    tip.setAntiAlias(true);
    tip.setColor(hexA(0xfff1e2, 0.16 * finger));
    canvas.drawCircle(bx[0] - 2, by[0], r, tip);
    tip.setStyle(1);
    tip.setStrokeWidth(1.4);
    tip.setColor(hexA(0xfff1e2, 0.5 * finger));
    canvas.drawCircle(bx[0] - 2, by[0], r, tip);
  }
}
