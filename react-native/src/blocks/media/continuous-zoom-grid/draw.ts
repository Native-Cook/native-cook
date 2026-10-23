import { ClipOp, Skia, type SkCanvas, type SkFont, type SkPicture, TileMode } from '@shopify/react-native-skia';

import { COUNT, type GridView, MONTHS, type Photo, type RGB, layers, monthIndex, photo, row, smoothstep, tiny } from './model';

/*
 Drawing for Continuous Zoom Grid (a port of ContinuousZoomGridDraw in ContinuousZoomGrid.swift).
 Every function runs on the UI thread.
 */

const PAPER: RGB = [0.98, 0.98, 0.98];

/** Every photo's tiny look, worked out once: sky r g b, ground r g b, split (7 numbers a photo). */
const TINY = (() => {
  const out = new Float32Array(COUNT * 7);
  for (let i = 0; i < COUNT; i++) {
    const t = tiny(i);
    out.set([t.sky[0], t.sky[1], t.sky[2], t.ground[0], t.ground[1], t.ground[2], t.split], i * 7);
  }
  return out;
})();
/** Every photo's length in seconds (0 for a still). */
const DURATION = (() => {
  const out = new Int32Array(COUNT);
  for (let i = 0; i < COUNT; i++) out[i] = photo(i).duration;
  return out;
})();

function color(c: RGB, a = 1) {
  'worklet';
  const f = (x: number) => Math.min(1, Math.max(0, x));
  return Float32Array.of(f(c[0]), f(c[1]), f(c[2]), f(a));
}

function picture(canvas: SkCanvas, ph: Photo, rx: number, ry: number, rs: number, opacity: number) {
  'worklet';
  const X = (x: number) => rx + x * rs;
  const Y = (y: number) => ry + y * rs;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  for (const op of ph.ops) {
    p.setShader(null);
    if (op.t === 'rect') {
      p.setColor(color(op.c, op.a * opacity));
      canvas.drawRect(Skia.XYWHRect(X(op.x), Y(op.y), op.w * rs, op.h * rs), p);
    } else if (op.t === 'grad') {
      p.setColor(Float32Array.of(1, 1, 1, 1));
      p.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: Y(op.y) }, { x: 0, y: Y(op.y + op.h) }, [color(op.c, opacity), color(op.c2, opacity)], null, TileMode.Clamp));
      canvas.drawRect(Skia.XYWHRect(X(op.x), Y(op.y), op.w * rs, op.h * rs), p);
    } else if (op.t === 'circle') {
      p.setColor(color(op.c, op.a * opacity));
      canvas.drawCircle(X(op.x), Y(op.y), op.r * rs, p);
    } else if (op.t === 'glow') {
      const rr = op.r * rs;
      p.setColor(Float32Array.of(1, 1, 1, 1));
      p.setShader(
        Skia.Shader.MakeRadialGradient(
          { x: X(op.x), y: Y(op.y) },
          rr,
          [color(op.c, op.a * opacity), color(op.c, op.a * 0.45 * opacity), color(op.c, 0)],
          null,
          TileMode.Clamp,
        ),
      );
      canvas.drawCircle(X(op.x), Y(op.y), rr, p);
    } else {
      p.setColor(color(op.c, op.a * opacity));
      const path = Skia.Path.Make();
      for (let j = 0; j < op.pts.length; j += 2) {
        if (j === 0) path.moveTo(X(op.pts[j]), Y(op.pts[j + 1]));
        else path.lineTo(X(op.pts[j]), Y(op.pts[j + 1]));
      }
      path.close();
      canvas.drawPath(path, p);
    }
  }
  // A lens's falloff towards the corners.
  p.setColor(Float32Array.of(1, 1, 1, 1));
  p.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: X(0.5), y: Y(0.5) },
      rs * 0.75,
      [Float32Array.of(0, 0, 0, 0), Float32Array.of(0, 0, 0, 0.22 * opacity)],
      [0.35 / 0.75, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(rx, ry, rs, rs), p);
  if (ph.duration > 0) {
    p.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: Y(0.7) }, { x: 0, y: Y(1) }, [Float32Array.of(0, 0, 0, 0), Float32Array.of(0, 0, 0, 0.35 * opacity)], null, TileMode.Clamp));
    canvas.drawRect(Skia.XYWHRect(rx, Y(0.7), rs, rs * 0.3), p);
  }
}

/**
 * A photo's picture recorded once in unit space (0…1) and kept on the UI thread, so a frame that
 * shows hundreds of photos replays pictures instead of rebuilding and drawing every shape.
 */
function photoPicture(i: number): SkPicture {
  'worklet';
  const g = globalThis as unknown as { __continuousZoomGridPhotos?: SkPicture[] };
  if (!g.__continuousZoomGridPhotos) g.__continuousZoomGridPhotos = [];
  const cache = g.__continuousZoomGridPhotos;
  let pic = cache[i];
  if (!pic) {
    const recorder = Skia.PictureRecorder();
    picture(recorder.beginRecording(Skia.XYWHRect(0, 0, 1, 1)), photo(i), 0, 0, 1, 1);
    pic = recorder.finishRecordingAsPicture();
    cache[i] = pic;
  }
  return pic;
}

function alphaLayer(canvas: SkCanvas, alpha: number) {
  'worklet';
  if (alpha >= 0.999) return false;
  const p = Skia.Paint();
  p.setAlphaf(Math.max(0, alpha));
  canvas.saveLayer(p);
  return true;
}

/**
 * One layout of n photos per row, scaled by n / z about its anchor photo, at an opacity. The
 * layer's opacity and the pictures' fade-in are applied to the layer as a whole (the SwiftUI
 * version applies them to each shape; the difference only shows on half-faded photos mid-zoom).
 */
function drawLayer(canvas: SkCanvas, w: number, h: number, n: number, v: GridView, opacity: number, fonts: { chip: SkFont; length: SkFont }) {
  'worklet';
  const cell = w / n;
  const k = n / v.z;
  const s = cell * k;
  const ra = row(v.anchor, n);
  const ca = v.anchor % n;
  const ax = (ca + 0.5) * cell;
  const gap = Math.min(3, Math.max(0.8, s * 0.022));
  const first = Math.max(0, ra + Math.floor((0 - v.fy) / s) - 1);
  const last = Math.min(Math.floor((COUNT - 1) / n), ra + Math.ceil((h - v.fy) / s) + 1);
  if (first > last) return;
  const detail = smoothstep(22, 44, s);
  const chipA = 1 - smoothstep(34, 64, s);
  const lengthA = smoothstep(60, 90, s);
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  const layered = alphaLayer(canvas, opacity);
  // The visible cells: index, x, y, size.
  const cells: number[] = [];
  for (let r = first; r <= last; r++) {
    const y = v.fy + (r - ra) * s;
    for (let c = 0; c < n; c++) {
      const i = r * n + c;
      if (i >= COUNT) break;
      const x = ax + ((c + 0.5) * cell - ax) * k;
      if (x + s / 2 < 0 || x - s / 2 > w) continue;
      cells.push(i, x - s / 2 + gap / 2, y - s / 2 + gap / 2, s - gap);
    }
  }
  if (detail < 0.999) {
    // Up to a thousand tiny photos a frame: precomputed colours read into one reused buffer.
    const rgba = Float32Array.of(0, 0, 0, 1);
    for (let j = 0; j < cells.length; j += 4) {
      const i = cells[j];
      const rx = cells[j + 1];
      const ry = cells[j + 2];
      const rs = cells[j + 3];
      const o = i * 7;
      rgba[0] = TINY[o];
      rgba[1] = TINY[o + 1];
      rgba[2] = TINY[o + 2];
      paint.setColor(rgba);
      canvas.drawRect({ x: rx, y: ry, width: rs, height: rs }, paint);
      const split = TINY[o + 6];
      if (split < 1) {
        rgba[0] = TINY[o + 3];
        rgba[1] = TINY[o + 4];
        rgba[2] = TINY[o + 5];
        paint.setColor(rgba);
        canvas.drawRect({ x: rx, y: ry + rs * split, width: rs, height: rs * (1 - split) }, paint);
      }
    }
  }
  if (detail > 0.001) {
    const faded = alphaLayer(canvas, detail);
    paint.setColor(Float32Array.of(1, 1, 1, lengthA));
    for (let j = 0; j < cells.length; j += 4) {
      const i = cells[j];
      const rx = cells[j + 1];
      const ry = cells[j + 2];
      const rs = cells[j + 3];
      canvas.save();
      canvas.clipRect({ x: rx, y: ry, width: rs, height: rs }, ClipOp.Intersect, true);
      canvas.translate(rx, ry);
      canvas.scale(rs, rs);
      canvas.drawPicture(photoPicture(i));
      canvas.restore();
      const duration = DURATION[i];
      if (duration > 0 && lengthA > 0.01) {
        const label = `0:${String(duration).padStart(2, '0')}`;
        const tw = fonts.length.measureText(label).width;
        canvas.drawText(label, rx + rs - 7 - tw, ry + rs - 6 - 2, paint, fonts.length);
      }
    }
    if (faded) canvas.restore();
  }
  // Month chips over the first photo of each month.
  if (chipA > 0.01) {
    for (let j = 0; j < cells.length; j += 4) {
      const i = cells[j];
      const m = monthIndex(i);
      if (MONTHS.starts[m] !== i) continue;
      const px = cells[j + 1] + 4;
      const py = cells[j + 2] + 4;
      const label = MONTHS.labels[m];
      const tw = fonts.chip.measureText(label).width;
      const bw = tw + 14;
      paint.setColor(Float32Array.of(1, 1, 1, 0.94 * chipA));
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(px, py, bw, 20), 10, 10), paint);
      paint.setColor(Float32Array.of(0.07, 0.07, 0.08, chipA));
      canvas.drawText(label, px + 7, py + 14, paint, fonts.chip);
    }
  }
  if (layered) canvas.restore();
}

export function drawGrid(canvas: SkCanvas, w: number, h: number, top: number, v: GridView, fonts: { chip: SkFont; length: SkFont }) {
  'worklet';
  const bg = Skia.Paint();
  bg.setColor(color(PAPER));
  canvas.drawRect(Skia.XYWHRect(0, 0, w, h), bg);
  const ls = layers(v.z);
  for (let k = 0; k < ls.length; k += 2) if (ls[k + 1] > 0.001) drawLayer(canvas, w, h, ls[k], v, ls[k + 1], fonts);
  // White fades under the header and the control.
  const fade = Skia.Paint();
  fade.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: top + 84 }, [color(PAPER, 0.96), color(PAPER, 0.85), color(PAPER, 0)], [0, 0.55, 1], TileMode.Clamp),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, w, top + 84), fade);
  const fadeB = Skia.Paint();
  fadeB.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: h - 140 }, { x: 0, y: h }, [color(PAPER, 0), color(PAPER, 0.92)], null, TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(0, h - 140, w, 140), fadeB);
}
