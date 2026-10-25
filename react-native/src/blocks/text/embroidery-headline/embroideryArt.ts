import {
  BlurStyle,
  PaintStyle,
  type SkCanvas,
  type SkPath,
  type SkPathBuilder,
  Skia,
  StrokeCap,
  TileMode,
} from '@shopify/react-native-skia';

/*
 Embroidery Headline — what is drawn. The table, hoop and linen are baked once (2 px per pt). The
 headline is turned into satin stitch once, on the JS thread: every letter is rasterised, then cut by
 parallel lines at the letter's own angle into thread segments that run edge to edge. Each frame
 builds paths of the laid threads only (a shadow, two alternating thread shades, a sheen).
 */

export type RGB = [number, number, number];

export type Geo = { w: number; h: number; cx: number; cy: number; hoopR: number; fabricR: number };

export function makeGeo(w: number, h: number, hero: { x: number; y: number; width: number; height: number }): Geo {
  const hoopR = Math.min(hero.width - 8, hero.height - 12, 400) / 2;
  return { w, h, cx: hero.x + hero.width / 2, cy: hero.y + hero.height / 2, hoopR, fabricR: hoopR - 15 };
}

/** One letter in satin stitch: segments (x1, y1, x2, y2 flat, pt), thread colour, sheen 0..1. */
export type SatinGlyph = { segs: number[]; count: number; rgb: RGB; sheen: number; angle: number };
export type Satin = { glyphs: SatinGlyph[]; counts: number[]; spacing: number; knot: number[]; knotRGB: RGB };

export const MADDER: RGB = [0.64, 0.22, 0.17];
export const INDIGO: RGB = [0.17, 0.3, 0.42];
/** Thread angle per letter (degrees from horizontal), set across each letter's main strokes. */
export const ANGLES: Record<string, number> = { S: 58, l: 12, o: 64, w: 72, m: 16, a: 56, d: 20, e: 62 };
/** Where the light comes from for the sheen (degrees). */
export const LIGHT = 38;
export const SPACING = 1.15;

function col(c: RGB, a: number) {
  'worklet';
  return Float32Array.of(c[0], c[1], c[2], a);
}

function tone(c: RGB, k: number): RGB {
  'worklet';
  if (k < 0) return [c[0] * (1 + k), c[1] * (1 + k), c[2] * (1 + k)];
  return [c[0] + (1 - c[0]) * k, c[1] + (0.97 - c[1]) * k, c[2] + (0.92 - c[2]) * k];
}

/**
 * Lays the satin: adds the first `present` threads of glyph g to the builders (the one being laid
 * is cut short). `even`/`odd` alternate so neighbouring threads differ a little in shade; `sheen`
 * gets the middle 70 % of each thread, so the ends, where it dives into the cloth, stay darker.
 */
function layGlyph(
  g: SatinGlyph,
  present: number,
  even: SkPathBuilder,
  odd: SkPathBuilder,
  sheen: SkPathBuilder,
  all: SkPathBuilder,
) {
  'worklet';
  const n = Math.min(g.count, Math.ceil(present));
  for (let k = 0; k < n; k += 1) {
    const f = Math.min(1, present - k);
    const x1 = g.segs[k * 4];
    const y1 = g.segs[k * 4 + 1];
    // Satin goes back and forth: odd threads are laid from the far edge.
    const fromEnd = k % 2 === 1;
    const ax = fromEnd ? g.segs[k * 4 + 2] : x1;
    const ay = fromEnd ? g.segs[k * 4 + 3] : y1;
    const bx = fromEnd ? x1 : g.segs[k * 4 + 2];
    const by = fromEnd ? y1 : g.segs[k * 4 + 3];
    const ex = ax + (bx - ax) * f;
    const ey = ay + (by - ay) * f;
    const target = k % 2 === 0 ? even : odd;
    target.moveTo(ax, ay);
    target.lineTo(ex, ey);
    all.moveTo(ax, ay);
    all.lineTo(ex, ey);
    if (f > 0.3) {
      sheen.moveTo(ax + (ex - ax) * 0.15, ay + (ey - ay) * 0.15);
      sheen.lineTo(ax + (ex - ax) * 0.85, ay + (ey - ay) * 0.85);
    }
  }
}

/** Where the needle is on glyph g at `present`: [x, y, thread-from x, y]. */
export function satinFront(g: SatinGlyph, present: number): number[] {
  'worklet';
  if (g.count === 0) return [0, 0, 0, 0];
  const k = Math.min(g.count - 1, Math.max(0, Math.floor(present)));
  const f = Math.min(1, Math.max(0, present - k));
  const x1 = g.segs[k * 4];
  const y1 = g.segs[k * 4 + 1];
  const x2 = g.segs[k * 4 + 2];
  const y2 = g.segs[k * 4 + 3];
  const fromEnd = k % 2 === 1;
  const ax = fromEnd ? x2 : x1;
  const ay = fromEnd ? y2 : y1;
  const bx = fromEnd ? x1 : x2;
  const by = fromEnd ? y1 : y2;
  // The needle rides near the middle of the thread being laid (it swings edge to edge too fast to
  // follow at 160 threads a second), leaning toward the end it is heading for.
  const mx = (x1 + x2) / 2;
  const my = (y1 + y2) / 2;
  const lean = 0.35 * (f - 0.5) * 2;
  return [mx + (bx - mx) * lean * 0.5, my + (by - my) * lean * 0.5, ax, ay];
}

export function drawSatin(canvas: SkCanvas, s: Satin, present: number[], alpha: number) {
  'worklet';
  if (alpha <= 0.002) return;
  const all = Skia.PathBuilder.Make();
  const evens: SkPath[] = [];
  const odds: SkPath[] = [];
  const sheens: SkPath[] = [];
  for (let i = 0; i < s.glyphs.length; i += 1) {
    const e = Skia.PathBuilder.Make();
    const o = Skia.PathBuilder.Make();
    const sh = Skia.PathBuilder.Make();
    layGlyph(s.glyphs[i], present[i], e, o, sh, all);
    evens.push(e.build());
    odds.push(o.build());
    sheens.push(sh.build());
  }
  const allPath = all.build();
  const w = s.spacing * 1.25;

  // The cloth puckers a little around the stitching: a wide, soft darkening, then the threads'
  // own shadow.
  const pucker = Skia.Paint();
  pucker.setAntiAlias(true);
  pucker.setStyle(PaintStyle.Stroke);
  pucker.setStrokeWidth(w + 5);
  pucker.setStrokeCap(StrokeCap.Round);
  pucker.setColor(Float32Array.of(0.3, 0.22, 0.13, 0.12 * alpha));
  pucker.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3.2, true));
  canvas.save();
  canvas.translate(0.5, 1.6);
  canvas.drawPath(allPath, pucker);
  canvas.restore();
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setStyle(PaintStyle.Stroke);
  shadow.setStrokeWidth(w + 0.6);
  shadow.setStrokeCap(StrokeCap.Round);
  shadow.setColor(Float32Array.of(0.18, 0.1, 0.06, 0.4 * alpha));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 0.8, true));
  canvas.save();
  canvas.translate(0.45, 0.9);
  canvas.drawPath(allPath, shadow);
  canvas.restore();

  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeCap(StrokeCap.Round);
  for (let i = 0; i < s.glyphs.length; i += 1) {
    const g = s.glyphs[i];
    p.setStrokeWidth(w);
    p.setColor(col(tone(g.rgb, -0.24), alpha));
    canvas.drawPath(evens[i], p);
    p.setColor(col(tone(g.rgb, -0.04), alpha));
    canvas.drawPath(odds[i], p);
    p.setStrokeWidth(w * 0.5);
    p.setColor(col(tone(g.rgb, 0.5 + 0.2 * g.sheen), (0.28 + 0.55 * g.sheen) * alpha));
    canvas.save();
    canvas.translate(-0.25, -0.3);
    canvas.drawPath(sheens[i], p);
    canvas.restore();
  }
}

/** The French knot: a tight bead of thread wraps. */
export function drawKnot(canvas: SkCanvas, s: Satin, scale: number, alpha: number) {
  'worklet';
  if (alpha <= 0.002) return;
  const x = s.knot[0];
  const y = s.knot[1];
  const r = s.knot[2] * scale;
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Float32Array.of(0.2, 0.12, 0.06, 0.38 * alpha));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.6 + (scale - 1) * 4, true));
  canvas.drawCircle(x + 0.8, y + 1.6, r * 1.02, shadow);
  const bead = Skia.Paint();
  bead.setAntiAlias(true);
  bead.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(x - r * 0.35, y - r * 0.4),
      r * 1.5,
      [col(tone(s.knotRGB, 0.35), alpha), col(s.knotRGB, alpha), col(tone(s.knotRGB, -0.45), alpha)],
      [0, 0.45, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(x, y, r, bead);
  const wrap = Skia.Paint();
  wrap.setAntiAlias(true);
  wrap.setStyle(PaintStyle.Stroke);
  wrap.setStrokeCap(StrokeCap.Round);
  wrap.setStrokeWidth(r * 0.22);
  for (let k = 0; k < 5; k += 1) {
    const a0 = k * 72 + 20;
    const b = Skia.PathBuilder.Make();
    b.addArc(Skia.XYWHRect(x - r * 0.72, y - r * 0.72, r * 1.44, r * 1.44), a0, 110);
    wrap.setColor(col(tone(s.knotRGB, k % 2 === 0 ? -0.3 : 0.3), 0.75 * alpha));
    canvas.drawPath(b.build(), wrap);
  }
  wrap.setStrokeWidth(r * 0.16);
  wrap.setColor(Float32Array.of(1, 0.96, 0.9, 0.45 * alpha));
  const hi = Skia.PathBuilder.Make();
  hi.addArc(Skia.XYWHRect(x - r * 0.55, y - r * 0.6, r * 1.1, r * 1.1), 200, 70);
  canvas.drawPath(hi.build(), wrap);
}

/** Needle and thread (thread from the eye, sagging, to where it last went into the cloth). */
export function drawNeedle(canvas: SkCanvas, x: number, y: number, angle: number, lift: number, fromX: number, fromY: number, rgb: RGB, alpha: number) {
  'worklet';
  if (alpha <= 0.002) return;
  const L = 62;
  const ca = Math.cos(angle);
  const sa = Math.sin(angle);
  const eyeX = x + ca * (L - 5);
  const eyeY = y + sa * (L - 5);
  const thread = Skia.PathBuilder.Make();
  thread.moveTo(fromX, fromY);
  thread.quadTo((eyeX + fromX) / 2, Math.max(eyeY, fromY) + 14 + Math.hypot(eyeX - fromX, eyeY - fromY) * 0.2, eyeX, eyeY);
  const tp = thread.build();
  const t = Skia.Paint();
  t.setAntiAlias(true);
  t.setStyle(PaintStyle.Stroke);
  t.setStrokeCap(StrokeCap.Round);
  t.setStrokeWidth(2.4);
  t.setColor(Float32Array.of(0.12, 0.08, 0.05, 0.22 * alpha));
  t.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.4, true));
  canvas.save();
  canvas.translate(1, 3);
  canvas.drawPath(tp, t);
  canvas.restore();
  t.setMaskFilter(null);
  t.setStrokeWidth(1.9);
  t.setColor(col(rgb, alpha));
  canvas.drawPath(tp, t);
  t.setStrokeWidth(0.7);
  t.setColor(col(tone(rgb, 0.45), 0.6 * alpha));
  canvas.drawPath(tp, t);

  const nb = Skia.PathBuilder.Make();
  nb.moveTo(0, 0);
  nb.lineTo(L - 8, -1.8);
  nb.quadTo(L + 1.5, -2.1, L + 1.5, 0);
  nb.quadTo(L + 1.5, 2.1, L - 8, 1.8);
  nb.close();
  const needle = nb.build();
  const deg = (angle * 180) / Math.PI;
  const sh = Skia.Paint();
  sh.setAntiAlias(true);
  sh.setColor(Float32Array.of(0.14, 0.1, 0.06, 0.32 * alpha));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.2 + lift * 2.5, true));
  canvas.save();
  canvas.translate(x + 1.5 + lift * 6, y + 2.5 + lift * 8);
  canvas.rotate(deg, 0, 0);
  canvas.drawPath(needle, sh);
  canvas.restore();
  canvas.save();
  canvas.translate(x, y);
  canvas.rotate(deg, 0, 0);
  const steel = Skia.Paint();
  steel.setAntiAlias(true);
  steel.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, -2.1),
      Skia.Point(0, 2.1),
      [Float32Array.of(0.97, 0.97, 0.96, alpha), Float32Array.of(0.64, 0.67, 0.7, alpha), Float32Array.of(0.38, 0.4, 0.43, alpha), Float32Array.of(0.8, 0.82, 0.83, alpha)],
      [0, 0.35, 0.7, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawPath(needle, steel);
  const hole = Skia.Paint();
  hole.setAntiAlias(true);
  hole.setColor(Float32Array.of(0.28, 0.26, 0.25, alpha));
  canvas.drawPath(Skia.PathBuilder.Make().addOval(Skia.XYWHRect(L - 8, -0.7, 6.5, 1.4)).build(), hole);
  canvas.restore();
}
