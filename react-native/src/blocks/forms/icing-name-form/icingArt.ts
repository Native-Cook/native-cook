import {
  BlurStyle,
  PaintStyle,
  type SkCanvas,
  type SkImage,
  type SkPath,
  type SkPathBuilder,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
} from '@shopify/react-native-skia';

import type { Lettering } from './icingFont';

/*
 Icing Name Form — what is drawn. The marble counter and the cake (gold drum, fondant top, a ring
 of piped rosettes and dragées) are baked once at 2×. Per frame: the cake image under its turn and
 slide, the lettering as layered strokes (shadow, body, a lighter core, a glossy highlight that
 stays lit from the upper left whatever the cake's angle), the knife and the piping bag.
 */

export type RGB = [number, number, number];

export type Geo = { w: number; h: number; cx: number; cy: number; R: number; boardR: number; boxW: number; boxH: number };

export function makeGeo(w: number, h: number, hero: { x: number; y: number; width: number; height: number }): Geo {
  const R = (Math.min(hero.width, hero.height) / 2) * 0.86;
  return { w, h, cx: hero.x + hero.width / 2, cy: hero.y + hero.height / 2, R, boardR: R * 1.1, boxW: R * 1.36, boxH: R * 0.95 };
}

export const ICINGS: { name: string; rgb: RGB; hex: string }[] = [
  { name: 'Chocolate', rgb: [0.36, 0.2, 0.13], hex: '#5C3321' },
  { name: 'Rose', rgb: [0.84, 0.38, 0.49], hex: '#D6617D' },
  { name: 'Pistachio', rgb: [0.53, 0.66, 0.39], hex: '#87A863' },
  { name: 'Blueberry', rgb: [0.3, 0.37, 0.66], hex: '#4D5EA8' },
  { name: 'Lemon', rgb: [0.88, 0.71, 0.22], hex: '#E0B538' },
];

function col(c: RGB, a: number) {
  'worklet';
  return Float32Array.of(c[0], c[1], c[2], a);
}

function tone(c: RGB, k: number): RGB {
  'worklet';
  if (k < 0) return [c[0] * (1 + k), c[1] * (1 + k), c[2] * (1 + k)];
  return [c[0] + (1 - c[0]) * k, c[1] + (0.97 - c[1]) * k, c[2] + (0.94 - c[2]) * k];
}

export function mixRGB(a: RGB, b: RGB, t: number): RGB {
  'worklet';
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

/** Adds stroke s, from its start to `upto` pt of its length, shifted by (dx, dy), to the builder. */
function addStroke(b: SkPathBuilder, pts: number[], len: number[], upto: number, dx: number, dy: number) {
  'worklet';
  if (upto <= 0) return;
  b.moveTo(pts[0] + dx, pts[1] + dy);
  const n = len.length;
  // A dot (zero-length stroke) still needs a tiny segment for the round cap.
  if (len[n - 1] < 0.6) {
    b.lineTo(pts[0] + dx + 0.3, pts[1] + dy + 0.2);
    return;
  }
  for (let k = 1; k < n; k += 1) {
    if (len[k] <= upto) b.lineTo(pts[k * 2] + dx, pts[k * 2 + 1] + dy);
    else {
      const f = (upto - len[k - 1]) / Math.max(0.0001, len[k] - len[k - 1]);
      b.lineTo(pts[k * 2 - 2] + (pts[k * 2] - pts[k * 2 - 2]) * f + dx, pts[k * 2 - 1] + (pts[k * 2 + 1] - pts[k * 2 - 1]) * f + dy);
      return;
    }
  }
}

/** The point `upto` pt along a stroke. */
export function strokePoint(pts: number[], len: number[], upto: number): number[] {
  'worklet';
  const n = len.length;
  for (let k = 1; k < n; k += 1) {
    if (len[k] >= upto) {
      const f = (upto - len[k - 1]) / Math.max(0.0001, len[k] - len[k - 1]);
      return [pts[k * 2 - 2] + (pts[k * 2] - pts[k * 2 - 2]) * f, pts[k * 2 - 1] + (pts[k * 2 + 1] - pts[k * 2 - 1]) * f];
    }
  }
  return [pts[n * 2 - 2], pts[n * 2 - 1]];
}

/** One layered tube of icing along `body`, with the start bulges in `blobs`. */
function drawTube(canvas: SkCanvas, body: SkPath, blobs: SkPath, rgb: RGB, w: number, lx: number, ly: number, alpha: number, shadow: boolean) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  const f = Skia.Paint();
  f.setAntiAlias(true);
  if (shadow) {
    p.setStrokeWidth(w * 1.12);
    p.setColor(Float32Array.of(0.28, 0.15, 0.08, 0.34 * alpha));
    p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, w * 0.3, true));
    f.setColor(Float32Array.of(0.28, 0.15, 0.08, 0.34 * alpha));
    f.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, w * 0.3, true));
    canvas.save();
    canvas.translate(-lx * w * 0.5, -ly * w * 0.75);
    canvas.drawPath(body, p);
    canvas.drawPath(blobs, f);
    canvas.restore();
    p.setMaskFilter(null);
    f.setMaskFilter(null);
  }
  // Body: the darker rim of the tube, a lighter core shifted toward the light, then the gloss.
  p.setStrokeWidth(w);
  p.setColor(col(tone(rgb, -0.28), alpha));
  canvas.drawPath(body, p);
  f.setColor(col(tone(rgb, -0.28), alpha));
  canvas.drawPath(blobs, f);
  canvas.save();
  canvas.translate(lx * w * 0.1, ly * w * 0.1);
  p.setStrokeWidth(w * 0.7);
  p.setColor(col(rgb, alpha));
  canvas.drawPath(body, p);
  canvas.restore();
  canvas.save();
  canvas.translate(lx * w * 0.24, ly * w * 0.24);
  p.setStrokeWidth(w * 0.26);
  p.setColor(col(tone(rgb, 0.55), 0.8 * alpha));
  canvas.drawPath(body, p);
  canvas.restore();
  canvas.save();
  canvas.translate(lx * w * 0.3, ly * w * 0.3);
  p.setStrokeWidth(w * 0.1);
  p.setColor(Float32Array.of(1, 0.99, 0.97, 0.85 * alpha));
  canvas.drawPath(body, p);
  canvas.restore();
}

/**
 * Pipes a lettering in cake-local coordinates. `frac` per stroke (null = all piped), `shift`
 * per glyph (pt), colour A everywhere and colour B flowed in over the first `flow` of the ink.
 * (lx, ly) is the direction toward the light in these local coordinates.
 */
export function drawLettering(
  canvas: SkCanvas,
  L: Lettering,
  frac: number[] | null,
  shiftX: number[] | null,
  shiftY: number[] | null,
  a: RGB,
  b: RGB,
  flow: number,
  lx: number,
  ly: number,
  alpha: number,
) {
  'worklet';
  if (alpha <= 0.002 || L.strokes.length === 0) return;
  const w = Math.max(4.2, Math.min(9, L.size * 0.17));
  const body = Skia.PathBuilder.Make();
  const blobs = Skia.PathBuilder.Make();
  const bodyB = Skia.PathBuilder.Make();
  const blobsB = Skia.PathBuilder.Make();
  const flowLen = flow * L.total;
  let acc = 0;
  for (let i = 0; i < L.strokes.length; i += 1) {
    const s = L.strokes[i];
    const full = s.len[s.len.length - 1];
    const upto = frac ? full * frac[i] : full;
    const dx = shiftX ? shiftX[s.glyph] : 0;
    const dy = shiftY ? shiftY[s.glyph] : 0;
    if (frac === null || frac[i] > 0) {
      addStroke(body, s.pts, s.len, Math.max(upto, 0.01), dx, dy);
      // Pressure bulge where the nozzle first squeezes (grows over the first 5 pt).
      blobs.addCircle(s.pts[0] + dx, s.pts[1] + dy, w * (0.5 + 0.1 * Math.min(1, upto / 5)));
      const bUpto = Math.min(upto, flowLen - acc);
      if (bUpto > 0) {
        addStroke(bodyB, s.pts, s.len, bUpto, dx, dy);
        blobsB.addCircle(s.pts[0] + dx, s.pts[1] + dy, w * (0.5 + 0.1 * Math.min(1, upto / 5)));
      }
    }
    acc += Math.max(full, 0.5);
  }
  drawTube(canvas, body.build(), blobs.build(), a, w, lx, ly, alpha, true);
  if (flowLen > 0) drawTube(canvas, bodyB.build(), blobsB.build(), b, w, lx, ly, alpha, false);
}

/** The cake image under its turn (rad) and slide (pt), plus the fondant's sheen, which stays put. */
export function drawCake(canvas: SkCanvas, g: Geo, cake: SkImage, x: number, y: number, turn: number, scale: number) {
  'worklet';
  const side = g.boardR * 2 + 48;
  canvas.save();
  canvas.translate(x, y);
  canvas.rotate((turn * 180) / Math.PI, 0, 0);
  canvas.drawImageRect(cake, Skia.XYWHRect(0, 0, cake.width(), cake.height()), Skia.XYWHRect(-side / 2, -side / 2, side, side), Skia.Paint());
  canvas.restore();
  const sheen = Skia.Paint();
  sheen.setAntiAlias(true);
  sheen.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(x - g.R * 0.34, y - g.R * 0.4),
      g.R * 0.9,
      [Float32Array.of(1, 1, 1, 0.34), Float32Array.of(1, 0.99, 0.97, 0.1), Float32Array.of(1, 1, 1, 0)],
      [0, 0.45, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(x, y, g.R * 0.8 * scale, sheen);
}

/** The palette knife lifting the writing off, its blade at x (pt), slightly angled. */
export function drawKnife(canvas: SkCanvas, g: Geo, x: number, y: number) {
  'worklet';
  canvas.save();
  canvas.translate(x, y);
  canvas.rotate(7, 0, 0);
  const top = -g.R * 1.7;
  const bottom = g.R * 1.05;
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Float32Array.of(0.25, 0.15, 0.1, 0.25));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(4, top + 10, 30, bottom - top), 15, 15), shadow);
  const blade = Skia.Paint();
  blade.setAntiAlias(true);
  blade.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(-14, 0),
      Skia.Point(14, 0),
      [Float32Array.of(0.62, 0.65, 0.68, 1), Float32Array.of(0.95, 0.96, 0.97, 1), Float32Array.of(0.74, 0.77, 0.8, 1), Float32Array.of(0.5, 0.53, 0.57, 1)],
      [0, 0.3, 0.65, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-14, top + g.R * 0.45, 28, bottom - top - g.R * 0.45), 14, 14), blade);
  // A smear of the lifted icing along the leading edge.
  const smear = Skia.Paint();
  smear.setAntiAlias(true);
  smear.setColor(Float32Array.of(0.4, 0.23, 0.15, 0.55));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(8, -g.R * 0.25, 5, g.R * 0.6), 2.5, 2.5), smear);
  const handle = Skia.Paint();
  handle.setAntiAlias(true);
  handle.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(-9, 0),
      Skia.Point(9, 0),
      [Float32Array.of(0.5, 0.3, 0.18, 1), Float32Array.of(0.72, 0.48, 0.3, 1), Float32Array.of(0.42, 0.24, 0.14, 1)],
      [0, 0.4, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-9, top - 40, 18, g.R * 0.7), 8, 8), handle);
  canvas.restore();
}

/** The piping bag: a metal star tip at (x, y), the bag rising to the upper right. */
export function drawNozzle(canvas: SkCanvas, x: number, y: number, lift: number, rgb: RGB) {
  'worklet';
  const L = 200;
  const s = 1 + lift * 0.06;
  // Shadow on the cake, further away when lifted.
  const sh = Skia.Paint();
  sh.setAntiAlias(true);
  sh.setColor(Float32Array.of(0.25, 0.14, 0.08, 0.22 - lift * 0.06));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6 + lift * 6, true));
  canvas.save();
  canvas.translate(x + 10 + lift * 14, y + 16 + lift * 18);
  canvas.rotate(-48, 0, 0);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, -14, L, 46), 23, 23), sh);
  canvas.restore();

  canvas.save();
  canvas.translate(x, y);
  canvas.scale(s, s);
  canvas.rotate(-48, 0, 0);
  // The bag: translucent plastic over icing, pleated at the top.
  const bag = Skia.PathBuilder.Make();
  bag.moveTo(14, -7);
  bag.cubicTo(70, -16, 140, -38, L, -36);
  bag.quadTo(L + 14, 0, L, 36);
  bag.cubicTo(140, 38, 70, 16, 14, 7);
  bag.close();
  const bagPath = bag.build();
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, -36),
      Skia.Point(0, 36),
      [col(tone(rgb, 0.35), 1), col(tone(rgb, 0.05), 1), col(tone(rgb, -0.2), 1), col(tone(rgb, -0.38), 1)],
      [0, 0.35, 0.7, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawPath(bagPath, fill);
  const film = Skia.Paint();
  film.setAntiAlias(true);
  film.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, -36),
      Skia.Point(0, 36),
      [Float32Array.of(1, 1, 1, 0.55), Float32Array.of(1, 1, 1, 0.08), Float32Array.of(1, 1, 1, 0), Float32Array.of(1, 1, 1, 0.18)],
      [0, 0.3, 0.75, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawPath(bagPath, film);
  const pleat = Skia.Paint();
  pleat.setAntiAlias(true);
  pleat.setStyle(PaintStyle.Stroke);
  pleat.setStrokeWidth(1);
  pleat.setColor(Float32Array.of(1, 1, 1, 0.35));
  for (let k = 0; k < 4; k += 1) {
    const b = Skia.PathBuilder.Make();
    b.moveTo(60 + k * 30, -12 - k * 6);
    b.quadTo(110 + k * 22, -4 - k * 3, L - 8, -24 + k * 14);
    canvas.drawPath(b.build(), pleat);
  }
  // Coupler ring and the star tip.
  const metal = Skia.Paint();
  metal.setAntiAlias(true);
  metal.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, -8),
      Skia.Point(0, 8),
      [Float32Array.of(0.96, 0.96, 0.95, 1), Float32Array.of(0.66, 0.68, 0.7, 1), Float32Array.of(0.42, 0.44, 0.47, 1), Float32Array.of(0.82, 0.83, 0.84, 1)],
      [0, 0.35, 0.7, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(12, -9, 12, 18), 3, 3), metal);
  const tip = Skia.PathBuilder.Make();
  tip.moveTo(0, -3.5);
  tip.lineTo(13, -7);
  tip.lineTo(13, 7);
  tip.lineTo(0, 3.5);
  tip.close();
  canvas.drawPath(tip.build(), metal);
  canvas.restore();
  // The star opening, seen from above at the tip.
  const hole = Skia.Paint();
  hole.setAntiAlias(true);
  hole.setColor(col(tone(rgb, -0.3), 1));
  canvas.drawCircle(x, y, 2.6 * s, hole);
}
