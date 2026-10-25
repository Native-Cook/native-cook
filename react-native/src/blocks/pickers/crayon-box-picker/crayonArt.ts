import { BlendMode, BlurStyle, ClipOp, PaintStyle, type SkCanvas, type SkImage, Skia, StrokeCap, StrokeJoin } from '@shopify/react-native-skia';

import { CRAYONS, easeInOut } from './crayonMotion';

/*
 Crayon Box Picker — what is drawn. Baked once at 2×: the desk with the paper and the open box's
 back, the box's printed front, and each of the twelve crayons (wax tip worn its own way, a paper
 wrapper with its name, cylinder shading). Per frame: the swatches (wax strokes with paper tooth
 knocked out of them), the crayons at their poses, the box front over them, and the crayon in flight
 on top.
 */

export type Geo = {
  w: number;
  h: number;
  cx: number;
  boxX: number;
  boxW: number;
  rimY: number;
  frontH: number;
  pitch: number;
  cw: number;
  cl: number;
  /** Visible length above the rim, front and back row. */
  showFront: number;
  showBack: number;
  paperY: number;
  paperH: number;
  /** Swatch centre and radii. */
  sx: number;
  sy: number;
  rx: number;
  ry: number;
};

export function makeGeo(w: number, h: number, hero: { x: number; y: number; width: number; height: number }): Geo {
  // Everything scales with the hero's height (designed for 470 pt) so a pulled crayon always
  // stays inside it: a crayon comes fully out of the box at the top of the hero.
  const u = Math.min(1, hero.height / 470);
  const boxW = Math.min(330, hero.width - 16);
  const frontH = 92 * u;
  const showFront = 84 * u;
  const rimY = hero.y + 182 * u;
  const pitch = (boxW - 28) / 6;
  const paperY = rimY + frontH + 22 * u;
  const paperH = Math.max(80, hero.y + hero.height - paperY - 6);
  return {
    w,
    h,
    cx: hero.x + hero.width / 2,
    boxX: hero.x + (hero.width - boxW) / 2,
    boxW,
    rimY,
    frontH,
    pitch,
    cw: pitch * 0.6,
    cl: frontH - 8 + showFront,
    showFront,
    showBack: showFront + 24 * u,
    paperY,
    paperH,
    sx: hero.x + hero.width / 2 + 8,
    sy: paperY + paperH * 0.5,
    rx: Math.min(112, hero.width * 0.3),
    ry: Math.min(44, paperH * 0.3),
  };
}

/** A crayon's slot: [centre x, centre y, row (0 back, 1 front), visible length]. */
export function slot(g: Geo, i: number): number[] {
  'worklet';
  const back = i < 6;
  const k = back ? i : i - 6;
  const x = g.boxX + 14 + (k + 0.5) * g.pitch + (back ? -g.pitch * 0.22 : g.pitch * 0.22);
  const show = back ? g.showBack : g.showFront;
  return [x, g.rimY - show + g.cl / 2, back ? 0 : 1, show];
}

/** Vector from a crayon's centre to its tip for a rotation of `deg`. */
export function tipOffset(g: Geo, deg: number): number[] {
  'worklet';
  const a = (deg * Math.PI) / 180;
  return [(g.cl / 2) * Math.sin(a), -(g.cl / 2) * Math.cos(a)];
}

/** The scribble's path on the paper, sampled: points (flat) and running lengths. */
export function scribblePath(g: Geo): { pts: number[]; len: number[] } {
  const pts: number[] = [];
  const len: number[] = [];
  const rows = 15;
  let seed = 3;
  const rnd = () => {
    seed = (seed * 16807) % 2147483647;
    return seed / 2147483647;
  };
  let side = -1;
  const slant = Math.tan((14 * Math.PI) / 180);
  for (let k = 0; k < rows; k += 1) {
    const v = -1 + (2 * k) / (rows - 1);
    const y = v * g.ry * 0.92;
    const hw = g.rx * Math.sqrt(Math.max(0.05, 1 - v * v)) * (0.86 + rnd() * 0.16);
    // From one side to the other in a slight curve, each row a bit slanted.
    const x0 = side * hw;
    const x1 = -side * hw;
    for (let s = 0; s <= 10; s += 1) {
      const u = s / 10;
      const x = x0 + (x1 - x0) * u;
      const yy = y + Math.sin(u * Math.PI) * (2 + rnd() * 1.5) + x * slant * 0.3 + (g.ry * 1.85) / (rows - 1) * (u - 0.5) * 0.5;
      pts.push(g.sx + x, g.sy + yy);
    }
    side = -side;
  }
  let run = 0;
  for (let i = 0; i < pts.length; i += 2) {
    if (i > 0) run += Math.hypot(pts[i] - pts[i - 2], pts[i + 1] - pts[i - 1]);
    len.push(run);
  }
  return { pts, len };
}

export function pathPoint(path: { pts: number[]; len: number[] }, d: number): number[] {
  'worklet';
  const n = path.len.length;
  for (let k = 1; k < n; k += 1) {
    if (path.len[k] >= d) {
      const f = (d - path.len[k - 1]) / Math.max(0.0001, path.len[k] - path.len[k - 1]);
      return [path.pts[k * 2 - 2] + (path.pts[k * 2] - path.pts[k * 2 - 2]) * f, path.pts[k * 2 - 1] + (path.pts[k * 2 + 1] - path.pts[k * 2 - 1]) * f];
    }
  }
  return [path.pts[n * 2 - 2], path.pts[n * 2 - 1]];
}

function col(c: number[], a: number) {
  'worklet';
  return Float32Array.of(c[0], c[1], c[2], a);
}

/** One swatch: the first `upto` pt of the scribble in crayon `c`, at `alpha`. */
export function drawSwatch(canvas: SkCanvas, path: { pts: number[]; len: number[] }, c: number, upto: number, alpha: number) {
  'worklet';
  if (upto <= 0 || alpha <= 0.003) return;
  const b = Skia.PathBuilder.Make();
  b.moveTo(path.pts[0], path.pts[1]);
  const n = path.len.length;
  for (let k = 1; k < n; k += 1) {
    if (path.len[k] <= upto) b.lineTo(path.pts[k * 2], path.pts[k * 2 + 1]);
    else {
      const p = pathPoint(path, upto);
      b.lineTo(p[0], p[1]);
      break;
    }
  }
  const stroke = b.build();
  const rgb = CRAYONS[c].rgb;
  const layer = Skia.Paint();
  layer.setAlphaf(alpha);
  canvas.saveLayer(layer);
  const wax = Skia.Paint();
  wax.setAntiAlias(true);
  wax.setStyle(PaintStyle.Stroke);
  wax.setStrokeCap(StrokeCap.Round);
  wax.setStrokeJoin(StrokeJoin.Round);
  wax.setStrokeWidth(12);
  wax.setColor(col(rgb, 0.9));
  wax.setPathEffect(Skia.PathEffect.MakeDiscrete(5, 1.1, 4));
  canvas.drawPath(stroke, wax);
  wax.setPathEffect(null);
  wax.setStrokeWidth(4);
  wax.setColor(Float32Array.of(1, 1, 1, 0.14));
  canvas.save();
  canvas.translate(-1.5, -2);
  canvas.drawPath(stroke, wax);
  canvas.restore();
  // Paper tooth: the wax skips over the grain, so speckles of paper show through.
  const tooth = Skia.Paint();
  tooth.setAntiAlias(true);
  tooth.setStyle(PaintStyle.Stroke);
  tooth.setStrokeCap(StrokeCap.Round);
  tooth.setStrokeWidth(14);
  tooth.setShader(Skia.Shader.MakeFractalNoise(0.55, 0.55, 2, 9, 0, 0));
  tooth.setColorFilter(Skia.ColorFilter.MakeMatrix([0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5, -3.1]));
  tooth.setBlendMode(BlendMode.DstOut);
  canvas.drawPath(stroke, tooth);
  canvas.restore();
}

/** A crayon image centred at (x, y), turned by `deg`, lifted by `lift` (bigger shadow). */
export function drawCrayon(canvas: SkCanvas, g: Geo, img: SkImage, x: number, y: number, deg: number, scale: number, lift: number) {
  'worklet';
  if (lift > 0.001) {
    const sh = Skia.Paint();
    sh.setAntiAlias(true);
    sh.setColor(Float32Array.of(0.2, 0.13, 0.06, 0.28 - 0.1 * lift));
    sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3 + 9 * lift, true));
    canvas.save();
    canvas.translate(x + 6 + 16 * lift, y + 8 + 22 * lift);
    canvas.rotate(deg, 0, 0);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-g.cw / 2, -g.cl / 2, g.cw, g.cl), g.cw / 2, g.cw / 2), sh);
    canvas.restore();
  }
  canvas.save();
  canvas.translate(x, y);
  canvas.rotate(deg, 0, 0);
  canvas.scale(scale, scale);
  canvas.drawImageRect(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(-g.cw / 2, -g.cl / 2, g.cw, g.cl), Skia.Paint());
  canvas.restore();
}

/** Crayon poses: per crayon [x, y, deg, scale, lift, onTop]. */
export type Poses = number[][];

/**
 * Poses for the box with crayon `active` in `phase` ([phase, amount, wobble], see pickPhase).
 * `pen0`/`pen` are the scribble's start and the pen's current point; `pullOut` > 1 rubber-bands.
 */
export function crayonPoses(g: Geo, active: number, phase: number[], pen0: number[], pen: number[], penEnd: number[]): Poses {
  'worklet';
  const out: Poses = [];
  const WRITE = -120;
  const ph = phase[0];
  const a = phase[1];
  let lean = 0;
  if (ph === 1) lean = Math.min(1, a);
  else if (ph >= 2 && ph <= 4) lean = 1;
  else if (ph === 5) lean = a;
  for (let i = 0; i < 12; i += 1) {
    const s = slot(g, i);
    let x = s[0];
    let y = s[1];
    let deg = 0;
    let scale = 1;
    let lift = 0;
    let top = 0;
    const out0 = g.cl - s[3] + 6;
    if (i === active) {
      const px = s[0];
      const py = s[1] - out0;
      if (ph === 1 || ph === 5) {
        y = s[1] - out0 * a;
        deg = 4 * phase[2];
      } else {
        const tw = tipOffset(g, WRITE);
        const tipFrom = ph === 4 ? penEnd : pen0;
        const wx = tipFrom[0] - tw[0];
        const wy = tipFrom[1] - tw[1];
        if (ph === 3) {
          const tip = tipOffset(g, WRITE + 3 * Math.sin(a * Math.PI * 22));
          x = pen[0] - tip[0];
          y = pen[1] - tip[1];
          deg = WRITE + 3 * Math.sin(a * Math.PI * 22);
        } else {
          // ph 2 flies to the paper, ph 4 flies home: same arc, reversed.
          const f = ph === 2 ? a : 1 - a;
          const mx = (px + wx) / 2 + 30;
          const my = Math.min(py, wy) - 50;
          x = (1 - f) * (1 - f) * px + 2 * f * (1 - f) * mx + f * f * wx;
          y = (1 - f) * (1 - f) * py + 2 * f * (1 - f) * my + f * f * wy;
          deg = WRITE * easeInOut(f);
          lift = Math.sin(Math.PI * f);
          scale = 1 + 0.08 * lift;
        }
        top = 1;
      }
    } else if (active >= 0 && lean > 0) {
      // Neighbours in the same row lean a little into the gap (pivoting at their foot).
      const sameRow = active < 6 === i < 6;
      const d = i - active;
      if (sameRow && Math.abs(d) === 1) {
        deg = -d * 3.2 * lean;
        const r = (deg * Math.PI) / 180;
        const foot = g.cl / 2;
        x += foot * Math.sin(r);
        y += foot * (1 - Math.cos(r));
      }
    }
    out.push([x, y, deg, scale, lift, top]);
  }
  return out;
}

export function drawBox(
  canvas: SkCanvas,
  g: Geo,
  art: { desk: SkImage; front: SkImage; crayons: SkImage[] },
  poses: Poses,
  swatch: number[],
  path: { pts: number[]; len: number[] },
  total: number,
  alpha: number,
) {
  'worklet';
  const p = Skia.Paint();
  p.setAlphaf(alpha);
  canvas.drawImageRect(art.desk, Skia.XYWHRect(0, 0, art.desk.width(), art.desk.height()), Skia.XYWHRect(0, 0, g.w, g.h), p);
  drawSwatch(canvas, path, swatch[0], total, swatch[1] * alpha);
  drawSwatch(canvas, path, swatch[2], total * swatch[3], alpha);
  // Crayons inside the box are clipped to above its floor; the front panel covers their feet.
  canvas.save();
  canvas.clipRect(Skia.XYWHRect(0, 0, g.w, g.rimY + g.frontH - 6), ClipOp.Intersect, false);
  for (let row = 0; row < 2; row += 1) {
    for (let i = row * 6; i < row * 6 + 6; i += 1) {
      const q = poses[i];
      if (q[5] === 0) drawCrayon(canvas, g, art.crayons[i], q[0], q[1], q[2], q[3], 0);
    }
  }
  canvas.restore();
  const side = art.front.width() / 2;
  canvas.drawImageRect(art.front, Skia.XYWHRect(0, 0, art.front.width(), art.front.height()), Skia.XYWHRect(g.boxX - 20, g.rimY - 20, side, art.front.height() / 2), p);
  for (let i = 0; i < 12; i += 1) {
    const q = poses[i];
    if (q[5] === 1) drawCrayon(canvas, g, art.crayons[i], q[0], q[1], q[2], q[3], q[4]);
  }
}
