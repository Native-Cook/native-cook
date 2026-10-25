import { BlurStyle, type SkCanvas, type SkFont, type SkImage, Skia, TileMode } from '@shopify/react-native-skia';

import { type Pose } from './dymoMotion';

/*
 Dymo Label Input — what is drawn. Baked once at 2×: the pantry wall and shelf, a fresh jar of oats,
 the embosser's body and its character wheel. Per frame: the jars where the swap has them, the wheel
 image at its angle (its gloss stays put), the trigger, the tape with its embossed letters, the
 cutter, and the strip in flight. Letters are pressed in: a dark lower-right edge, a bright
 upper-left edge and a stress-whitened face that turns from the tape's colour to near white.
 */

export type Geo = {
  w: number;
  h: number;
  cx: number;
  shelfY: number;
  jarW: number;
  jarH: number;
  /** Centre of the label on the jar. */
  labelX: number;
  labelY: number;
  wheelX: number;
  wheelY: number;
  wheelR: number;
  /** The head (where letters are pressed) and the tape's centre line. */
  headX: number;
  tapeY: number;
  tapeH: number;
  pitch: number;
  /** How far a jar travels when it is swapped. */
  swap: number;
};

export function makeGeo(w: number, h: number, hero: { x: number; y: number; width: number; height: number }): Geo {
  const shelfY = hero.y + hero.height * 0.43;
  const jarW = Math.min(146, hero.width * 0.4);
  const jarH = Math.min(jarW * 1.18, hero.height * 0.37);
  const wheelR = Math.min(76, hero.width * 0.2, hero.height * 0.175);
  const wheelX = hero.x + hero.width * 0.4;
  const wheelY = hero.y + hero.height * 0.8;
  const tapeY = wheelY - wheelR - 17;
  return {
    w,
    h,
    cx: hero.x + hero.width / 2,
    shelfY,
    jarW,
    jarH,
    labelX: hero.x + hero.width / 2,
    labelY: shelfY - jarH * 0.42,
    wheelX,
    wheelY,
    wheelR,
    headX: wheelX,
    tapeY,
    tapeH: 24,
    pitch: 15,
    swap: w * 0.62 + jarW,
  };
}

function col(c: number[], a: number) {
  'worklet';
  return Float32Array.of(c[0], c[1], c[2], a);
}

function tone(c: number[], k: number): number[] {
  'worklet';
  if (k < 0) return [c[0] * (1 + k), c[1] * (1 + k), c[2] * (1 + k)];
  return [c[0] + (1 - c[0]) * k, c[1] + (1 - c[1]) * k, c[2] + (1 - c[2]) * k];
}

export function mixRGB(a: number[], b: number[], t: number): number[] {
  'worklet';
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

/** A run of glossy tape from x0 to x1 on centre line y. */
function drawTape(canvas: SkCanvas, x0: number, x1: number, y: number, h: number, rgb: number[], alpha: number) {
  'worklet';
  if (x1 <= x0) return;
  const r = Skia.RRectXY(Skia.XYWHRect(x0, y - h / 2, x1 - x0, h), 2.5, 2.5);
  const sh = Skia.Paint();
  sh.setAntiAlias(true);
  sh.setColor(Float32Array.of(0.1, 0.06, 0.04, 0.3 * alpha));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.6, true));
  canvas.save();
  canvas.translate(0.6, 1.6);
  canvas.drawRRect(r, sh);
  canvas.restore();
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, y - h / 2),
      Skia.Point(0, y + h / 2),
      [col(tone(rgb, 0.22), alpha), col(rgb, alpha), col(tone(rgb, -0.12), alpha), col(tone(rgb, -0.32), alpha)],
      [0, 0.3, 0.75, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRRect(r, p);
  // The gloss: a bright band near the top edge and a hairline on the lower edge.
  const g = Skia.Paint();
  g.setAntiAlias(true);
  g.setColor(Float32Array.of(1, 1, 1, 0.32 * alpha));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0 + 1.5, y - h / 2 + 2, x1 - x0 - 3, 2.6), 1.3, 1.3), g);
  g.setColor(Float32Array.of(1, 1, 1, 0.12 * alpha));
  canvas.drawRect(Skia.XYWHRect(x0 + 1, y + h / 2 - 1.6, x1 - x0 - 2, 0.8), g);
}

/** One embossed letter centred at (x, y): w = how white it has become, s = scale. */
function drawLetter(canvas: SkCanvas, ch: string, x: number, y: number, font: SkFont, width: number, rgb: number[], w: number, s: number, alpha: number) {
  'worklet';
  if (w <= 0.01 || ch === ' ') return;
  canvas.save();
  canvas.translate(x, y);
  canvas.scale(0.84 * s, s);
  const bx = -width / 2;
  const by = 6;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(col(tone(rgb, -0.55), 0.7 * w * alpha));
  canvas.drawText(ch, bx + 0.9, by + 1.1, p, font);
  p.setColor(Float32Array.of(1, 1, 1, 0.75 * w * alpha));
  canvas.drawText(ch, bx - 0.55, by - 0.65, p, font);
  p.setColor(col(mixRGB(rgb, [0.97, 0.95, 0.93], 0.88 * w), alpha));
  canvas.drawText(ch, bx, by, p, font);
  canvas.restore();
}

/** A strip of tape with letters, laid out from its left edge x0 (lead before the first letter). */
function drawStrip(canvas: SkCanvas, g: Geo, text: string, x0: number, y: number, lead: number, rgb: number[], font: SkFont, widths: Record<string, number>, alpha: number) {
  'worklet';
  const len = lead * 2 + text.length * g.pitch;
  drawTape(canvas, x0, x0 + len, y, g.tapeH, rgb, alpha);
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    drawLetter(canvas, ch, x0 + lead + (i + 0.5) * g.pitch, y, font, widths[ch] ?? 9, rgb, 1, 1, alpha);
  }
}

export function stripLength(g: Geo, n: number) {
  'worklet';
  return 2 * 14 + n * g.pitch;
}

/** The whole scene for a pose. */
export function drawScene(
  canvas: SkCanvas,
  g: Geo,
  art: { wall: SkImage; jar: SkImage; body: SkImage; wheel: SkImage },
  pose: Pose,
  font: SkFont,
  widths: Record<string, number>,
  alpha: number,
) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAlphaf(alpha);
  canvas.drawImageRect(art.wall, Skia.XYWHRect(0, 0, art.wall.width(), art.wall.height()), Skia.XYWHRect(0, 0, g.w, g.h), paint);

  // Jars: the one on the shelf (labelled once the strip lands) and, during the swap, the next one.
  const jarSide = g.jarW + 60;
  const jarTop = g.shelfY - g.jarH - 30;
  const oldX = g.cx - pose.slide * g.swap;
  const newX = g.cx + (1 - pose.slide) * g.swap;
  if (pose.slide < 1) canvas.drawImageRect(art.jar, Skia.XYWHRect(0, 0, art.jar.width(), art.jar.height()), Skia.XYWHRect(oldX - jarSide / 2, jarTop, jarSide, g.jarH + 50), paint);
  if (pose.slide > 0) canvas.drawImageRect(art.jar, Skia.XYWHRect(0, 0, art.jar.width(), art.jar.height()), Skia.XYWHRect(newX - jarSide / 2, jarTop, jarSide, g.jarH + 50), paint);

  // The embosser.
  const bodySide = art.body.width() / 2;
  canvas.drawImageRect(art.body, Skia.XYWHRect(0, 0, art.body.width(), art.body.height()), Skia.XYWHRect(g.wheelX - bodySide / 2, g.wheelY - bodySide / 2, bodySide, bodySide), paint);
  // The trigger lever under the handle, pivoting at its left end.
  const tx = g.wheelX + g.wheelR * 0.45;
  const ty = g.wheelY + g.wheelR * 0.62;
  canvas.save();
  canvas.rotate(-7 * pose.trigger, tx, ty);
  const lever = Skia.Paint();
  lever.setAntiAlias(true);
  lever.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, ty - 11),
      Skia.Point(0, ty + 11),
      [Float32Array.of(0.36, 0.38, 0.42, alpha), Float32Array.of(0.22, 0.23, 0.26, alpha), Float32Array.of(0.12, 0.12, 0.14, alpha)],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  const leverShadow = Skia.Paint();
  leverShadow.setAntiAlias(true);
  leverShadow.setColor(Float32Array.of(0.1, 0.06, 0.03, 0.3 * alpha));
  leverShadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
  const lr = Skia.RRectXY(Skia.XYWHRect(tx - 6, ty - 10, g.wheelR * 2.55, 22), 11, 11);
  canvas.save();
  canvas.translate(1, 4);
  canvas.drawRRect(lr, leverShadow);
  canvas.restore();
  canvas.drawRRect(lr, lever);
  const grip = Skia.Paint();
  grip.setAntiAlias(true);
  grip.setColor(Float32Array.of(1, 1, 1, 0.1 * alpha));
  for (let k = 0; k < 7; k += 1) canvas.drawRect(Skia.XYWHRect(tx + g.wheelR * 1.1 + k * 7, ty - 6, 2, 12), grip);
  canvas.restore();

  // Wheel, turned; its gloss and the head's index stay put.
  const ws = g.wheelR * 2 + 8;
  canvas.save();
  canvas.rotate(pose.wheel, g.wheelX, g.wheelY);
  canvas.drawImageRect(art.wheel, Skia.XYWHRect(0, 0, art.wheel.width(), art.wheel.height()), Skia.XYWHRect(g.wheelX - ws / 2, g.wheelY - ws / 2, ws, ws), paint);
  canvas.restore();
  const gloss = Skia.Paint();
  gloss.setAntiAlias(true);
  gloss.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(g.wheelX - g.wheelR * 0.35, g.wheelY - g.wheelR * 0.45),
      g.wheelR * 1.1,
      [Float32Array.of(1, 1, 1, 0.22 * alpha), Float32Array.of(1, 1, 1, 0.05 * alpha), Float32Array.of(1, 1, 1, 0)],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(g.wheelX, g.wheelY, g.wheelR, gloss);
  const index = Skia.PathBuilder.Make();
  const iy = g.wheelY - g.wheelR - 2;
  index.moveTo(g.headX - 5, iy - 6);
  index.lineTo(g.headX + 5, iy - 6);
  index.lineTo(g.headX, iy + 1);
  index.close();
  const red = Skia.Paint();
  red.setAntiAlias(true);
  red.setColor(Float32Array.of(0.85, 0.2, 0.2, alpha));
  canvas.drawPath(index.build(), red);

  // Tape: blank lead, then the pressed letters; the newest sits under the head and pops as it is
  // struck, then the tape steps one pitch to the left.
  const half = g.pitch / 2;
  const leading = g.headX - pose.advance * g.pitch - half - pose.lead;
  drawTape(canvas, leading, g.headX + 70, g.tapeY, g.tapeH, pose.tapeRGB, alpha);
  for (let i = 0; i < pose.tape.length; i += 1) {
    const x = g.headX - (pose.advance - i) * g.pitch;
    const ch = pose.tape[i];
    drawLetter(canvas, ch, x, g.tapeY, font, widths[ch] ?? 9, pose.tapeRGB, pose.whiten[i] ?? 1, 1 + 0.12 * (pose.pop[i] ?? 0), alpha);
  }
  // Tape guide over the tape inside the machine, the punch head and the cutter.
  const guide = Skia.Paint();
  guide.setAntiAlias(true);
  guide.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, g.tapeY - 18),
      Skia.Point(0, g.tapeY + 18),
      [Float32Array.of(0.92, 0.9, 0.86, alpha), Float32Array.of(0.8, 0.77, 0.72, alpha), Float32Array.of(0.62, 0.59, 0.55, alpha)],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  const gshadow = Skia.Paint();
  gshadow.setAntiAlias(true);
  gshadow.setColor(Float32Array.of(0.1, 0.06, 0.03, 0.28 * alpha));
  gshadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
  const guideR = Skia.RRectXY(Skia.XYWHRect(g.headX + 11, g.tapeY - 17, 92, 34), 9, 9);
  canvas.save();
  canvas.translate(-1.5, 2.5);
  canvas.drawRRect(guideR, gshadow);
  canvas.restore();
  canvas.drawRRect(guideR, guide);
  const head = Skia.Paint();
  head.setAntiAlias(true);
  head.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(g.headX - 10, 0),
      Skia.Point(g.headX + 10, 0),
      [Float32Array.of(0.62, 0.64, 0.67, alpha), Float32Array.of(0.93, 0.94, 0.95, alpha), Float32Array.of(0.55, 0.57, 0.6, alpha)],
      [0, 0.45, 1],
      TileMode.Clamp,
    ),
  );
  const hy = g.tapeY - 19 + 3 * pose.trigger;
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.headX - 9, hy, 18, 9), 2.5, 2.5), head);
  canvas.save();
  canvas.rotate(-16 * pose.cutter, g.headX - 30, g.tapeY - 24);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.headX - 46, g.tapeY - 30, 30, 9), 4.5, 4.5), head);
  canvas.restore();

  // The cut strip: from where it was cut, along an arc with a turn and a lift, onto the jar.
  if (pose.strip.length > 0) {
    const len = stripLength(g, pose.strip.length);
    const sx = g.headX - half - 4 - len / 2;
    const sy = g.tapeY;
    const ex = g.labelX - pose.slide * g.swap;
    const ey = g.labelY;
    const f = pose.fly;
    const mx = (sx + ex) / 2;
    const my = Math.min(sy, ey) - 120;
    const x = (1 - f) * (1 - f) * sx + 2 * f * (1 - f) * mx + f * f * ex;
    const y = (1 - f) * (1 - f) * sy + 2 * f * (1 - f) * my + f * f * ey;
    const lift = Math.sin(Math.PI * f);
    const rot = -18 * lift - 2 * f;
    const scale = 1 + 0.16 * lift;
    const sh = Skia.Paint();
    sh.setAntiAlias(true);
    sh.setColor(Float32Array.of(0.1, 0.06, 0.03, (0.25 - 0.1 * lift) * alpha));
    sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2 + 8 * lift, true));
    canvas.save();
    canvas.translate(x + 4 + 16 * lift, y + 5 + 26 * lift);
    canvas.rotate(rot, 0, 0);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-len / 2, -g.tapeH / 2, len, g.tapeH), 3, 3), sh);
    canvas.restore();
    canvas.save();
    canvas.translate(x, y);
    canvas.rotate(rot, 0, 0);
    canvas.scale(scale * (1 + 0.05 * pose.squash), scale * (1 - 0.1 * pose.squash));
    drawStrip(canvas, g, pose.strip, -len / 2, 0, 14, pose.stripRGB, font, widths, alpha);
    // On the jar the strip wraps round the glass: darker toward its ends.
    if (f > 0.9) {
      const wrap = Skia.Paint();
      wrap.setAntiAlias(true);
      wrap.setShader(
        Skia.Shader.MakeLinearGradient(
          Skia.Point(-len / 2, 0),
          Skia.Point(len / 2, 0),
          [Float32Array.of(0, 0, 0, 0.3 * alpha), Float32Array.of(0, 0, 0, 0), Float32Array.of(1, 1, 1, 0.08 * alpha), Float32Array.of(0, 0, 0, 0.32 * alpha)],
          [0, 0.3, 0.55, 1],
          TileMode.Clamp,
        ),
      );
      wrap.setAlphaf(((f - 0.9) / 0.1) * alpha);
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-len / 2, -g.tapeH / 2, len, g.tapeH), 2.5, 2.5), wrap);
    }
    canvas.restore();
  }
}
