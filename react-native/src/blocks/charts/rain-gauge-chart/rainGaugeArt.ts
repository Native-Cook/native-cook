import { BlendMode, BlurStyle, ClipOp, PaintStyle, Skia, StrokeCap, TileMode, type SkCanvas, type SkPath, type SkPicture } from '@shopify/react-native-skia';

import { CAPACITY, DROPS, LOOP, type RainPose, TUBES, hash } from './rainGaugeMotion';

/** Hero geometry in screen points. */
export type RainGeo = {
  ox: number;
  oy: number;
  w: number;
  h: number;
  /** Tube centres, half width, top (y0) and bottom (y1). */
  cx: number[];
  pitch: number;
  hw: number;
  y0: number;
  y1: number;
  /** The rack: top board's top edge, the boards' left and right ends. */
  board: number;
  left: number;
  right: number;
  ground: number;
};

export const LIFT_PT = 40;
export const LIFT_SCALE = 0.1;

export function makeRainGeo(ox: number, oy: number, w: number, h: number): RainGeo {
  const x0 = ox + 52;
  const x1 = ox + w - 22;
  const pitch = (x1 - x0) / TUBES;
  const cx: number[] = [];
  for (let i = 0; i < TUBES; i++) cx.push(x0 + pitch * (i + 0.5));
  const hw = Math.min(15, pitch * 0.3);
  const y1 = oy + h - 50;
  const tubeH = Math.max(150, Math.min(300, h - 200));
  const y0 = y1 - tubeH;
  return { ox, oy, w, h, cx, pitch, hw, y0, y1, board: y0 + tubeH * 0.22, left: x0 - 16, right: x1 + 10, ground: y1 + 12 };
}

/** The water's top (screen y, before any lift) for a level in mm. */
export function levelY(g: RainGeo, mm: number) {
  'worklet';
  return g.y1 - 4 - (mm / CAPACITY) * (g.y1 - g.y0 - 26);
}

function tubePath(hw: number, top: number, closed: boolean): SkPath {
  'worklet';
  const k = 0.5523;
  const b = Skia.PathBuilder.Make();
  b.moveTo(-hw, top);
  b.lineTo(-hw, -hw);
  b.cubicTo(-hw, -hw + hw * k, -hw * k, 0, 0, 0);
  b.cubicTo(hw * k, 0, hw, -hw + hw * k, hw, -hw);
  b.lineTo(hw, top);
  if (closed) b.close();
  return b.build();
}

function woodBoard(canvas: SkCanvas, x: number, y: number, w: number, h: number, seed: number) {
  const r = Skia.RRectXY(Skia.XYWHRect(x, y, w, h), 3, 3);
  const shadow = Skia.Paint();
  shadow.setColor(Skia.Color('rgba(6, 12, 12, 0.45)'));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 2, y + 5, w, h), 3, 3), shadow);
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(
    Skia.Shader.MakeLinearGradient({ x, y }, { x, y: y + h }, [Skia.Color('#B07B4C'), Skia.Color('#8E5D36'), Skia.Color('#6A4226')], [0, 0.5, 1], TileMode.Clamp),
  );
  canvas.drawRRect(r, body);
  canvas.save();
  canvas.clipRRect(r, ClipOp.Intersect, true);
  const grain = Skia.Paint();
  grain.setAntiAlias(true);
  grain.setStyle(PaintStyle.Stroke);
  for (let i = 0; i < 9; i++) {
    const gy = y + 2 + (h - 4) * hash(seed + i * 1.7);
    const b = Skia.PathBuilder.Make();
    b.moveTo(x, gy);
    const n = 6;
    for (let j = 1; j <= n; j++) {
      const px = x + (w * j) / n;
      b.quadTo(px - w / n / 2, gy + (hash(seed + i * 3.1 + j) - 0.5) * 3, px, gy + (hash(seed + i * 5.3 + j) - 0.5) * 1.6);
    }
    grain.setStrokeWidth(0.5 + hash(seed + i) * 0.9);
    grain.setColor(Skia.Color(`rgba(58, 32, 16, ${0.18 + 0.22 * hash(seed + i * 9.1)})`));
    canvas.drawPath(b.build(), grain);
  }
  // A knot.
  const knot = Skia.Paint();
  knot.setAntiAlias(true);
  knot.setStyle(PaintStyle.Stroke);
  knot.setStrokeWidth(0.8);
  knot.setColor(Skia.Color('rgba(60, 34, 18, 0.4)'));
  canvas.drawOval(Skia.XYWHRect(x + w * (0.2 + 0.6 * hash(seed + 40)), y + h * 0.3, 7, h * 0.36), knot);
  canvas.restore();
  // Edges: a lit top edge and a dark bottom edge.
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStrokeWidth(1);
  edge.setColor(Skia.Color('rgba(255, 222, 176, 0.42)'));
  canvas.drawLine(x + 3, y + 0.6, x + w - 3, y + 0.6, edge);
  edge.setColor(Skia.Color('rgba(40, 22, 10, 0.55)'));
  canvas.drawLine(x + 3, y + h - 0.6, x + w - 3, y + h - 0.6, edge);
}

/** The rack's posts and boards, drawn over the tubes (the tubes stand in holes through them). */
export function recordRackFront(g: RainGeo, width: number, height: number): SkPicture {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
  const postW = 10;
  woodBoard(canvas, g.left - 2, g.board - 4, postW, g.ground - g.board + 4, 3);
  woodBoard(canvas, g.right - postW + 2, g.board - 4, postW, g.ground - g.board + 4, 7);
  woodBoard(canvas, g.left, g.board, g.right - g.left, 18, 11);
  woodBoard(canvas, g.left, g.y1 - 4, g.right - g.left, 16, 23);
  // Holes: a dark lip where each tube passes through the top board.
  const hole = Skia.Paint();
  hole.setAntiAlias(true);
  hole.setColor(Skia.Color('rgba(30, 16, 8, 0.75)'));
  const lip = Skia.Paint();
  lip.setAntiAlias(true);
  lip.setStyle(PaintStyle.Stroke);
  lip.setStrokeWidth(0.8);
  lip.setColor(Skia.Color('rgba(255, 220, 170, 0.3)'));
  for (let i = 0; i < TUBES; i++) {
    canvas.drawOval(Skia.XYWHRect(g.cx[i] - g.hw - 2.5, g.board - 2, g.hw * 2 + 5, 4.5), hole);
    canvas.drawOval(Skia.XYWHRect(g.cx[i] - g.hw - 2.5, g.board - 1.6, g.hw * 2 + 5, 4.5), lip);
  }
  return recorder.finishRecordingAsPicture();
}

/** The garden bed under the rack, a far hedge, grain and a vignette. */
export function recordBackdrop(g: RainGeo, width: number, height: number): SkPicture {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
  const hedge = Skia.PathBuilder.Make();
  hedge.moveTo(0, g.ground - 40);
  for (let i = 0; i <= 12; i++) {
    const x = (width * i) / 12;
    hedge.quadTo(x - width / 24, g.ground - 64 - 22 * hash(i * 2.3), x, g.ground - 44 - 14 * hash(i * 4.1));
  }
  hedge.lineTo(width, height);
  hedge.lineTo(0, height);
  hedge.close();
  const hp = Skia.Paint();
  hp.setAntiAlias(true);
  hp.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: g.ground - 90 }, { x: 0, y: g.ground }, [Skia.Color('rgba(28, 52, 46, 0)'), Skia.Color('rgba(24, 46, 40, 0.7)'), Skia.Color('#172B26')], [0, 0.5, 1], TileMode.Clamp),
  );
  canvas.drawPath(hedge.build(), hp);
  const soil = Skia.Paint();
  soil.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: g.ground }, { x: 0, y: height }, [Skia.Color('#2B231C'), Skia.Color('#211A15'), Skia.Color('#17120F')], [0, 0.4, 1], TileMode.Clamp),
  );
  canvas.drawRect(Skia.XYWHRect(0, g.ground, width, height - g.ground), soil);
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.85, 0.85, 2, 3, 0, 0));
  grain.setAlphaf(0.07);
  grain.setBlendMode(BlendMode.Overlay);
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), grain);
  const vignette = Skia.Paint();
  vignette.setShader(
    Skia.Shader.MakeRadialGradient({ x: width / 2, y: height * 0.45 }, Math.max(width, height) * 0.7, [Skia.Color('rgba(0,0,0,0)'), Skia.Color('rgba(2,8,10,0.2)'), Skia.Color('rgba(2,8,10,0.55)')], [0, 0.62, 1], TileMode.Clamp),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), vignette);
  return recorder.finishRecordingAsPicture();
}

/** One glass gauge with its water, in local coordinates (bottom centre = 0, 0). */
function drawTube(canvas: SkCanvas, g: RainGeo, i: number, level: number, slosh: number) {
  'worklet';
  const hw = g.hw;
  const top = g.y0 - g.y1;
  const outer = tubePath(hw, top, true);
  const inner = tubePath(hw - 2, top, true);
  const glass = Skia.Paint();
  glass.setAntiAlias(true);
  glass.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: -hw, y: 0 },
      { x: hw, y: 0 },
      [Skia.Color('rgba(214, 238, 246, 0.2)'), Skia.Color('rgba(214, 238, 246, 0.05)'), Skia.Color('rgba(214, 238, 246, 0.02)'), Skia.Color('rgba(214, 238, 246, 0.08)'), Skia.Color('rgba(214, 238, 246, 0.24)')],
      [0, 0.25, 0.55, 0.8, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawPath(outer, glass);

  // Water, clipped to the bore; the surface climbs the walls (meniscus) and tilts with the slosh.
  const wy = levelY(g, level) - g.y1;
  if (level > 0.02) {
    canvas.save();
    canvas.clipPath(inner, ClipOp.Intersect, true);
    const wl = wy - slosh - 2.4;
    const wr = wy + slosh - 2.4;
    const surf = Skia.PathBuilder.Make();
    surf.moveTo(-hw, wl);
    surf.quadTo(0, wy + 2.4, hw, wr);
    const surface = surf.build();
    const body = Skia.PathBuilder.Make();
    body.moveTo(-hw, wl);
    body.quadTo(0, wy + 2.4, hw, wr);
    body.lineTo(hw, 2);
    body.lineTo(-hw, 2);
    body.close();
    const water = body.build();
    const wp = Skia.Paint();
    wp.setAntiAlias(true);
    wp.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: -hw, y: 0 },
        { x: hw, y: 0 },
        [Skia.Color('#1C5468'), Skia.Color('#3D8BA6'), Skia.Color('#86CBDF'), Skia.Color('#4C9DB7'), Skia.Color('#1A4C60')],
        [0, 0.22, 0.38, 0.72, 1],
        TileMode.Clamp,
      ),
    );
    wp.setAlphaf(0.92);
    canvas.drawPath(water, wp);
    const depth = Skia.Paint();
    depth.setShader(
      Skia.Shader.MakeLinearGradient({ x: 0, y: wy }, { x: 0, y: 0 }, [Skia.Color('rgba(200, 245, 255, 0.28)'), Skia.Color('rgba(10, 40, 52, 0)'), Skia.Color('rgba(6, 26, 36, 0.4)')], [0, 0.18, 1], TileMode.Clamp),
    );
    canvas.drawPath(water, depth);
    // Two small bubbles stuck to the glass.
    const bub = Skia.Paint();
    bub.setAntiAlias(true);
    bub.setStyle(PaintStyle.Stroke);
    bub.setStrokeWidth(0.7);
    bub.setColor(Skia.Color('rgba(235, 252, 255, 0.45)'));
    for (let k = 0; k < 2; k++) {
      const by = -6 - hash(i * 7 + k) * 40;
      if (by > wy + 4) canvas.drawCircle((hash(i * 3 + k * 5) - 0.5) * hw, by, 0.9 + hash(i + k) * 0.7, bub);
    }
    const men = Skia.Paint();
    men.setAntiAlias(true);
    men.setStyle(PaintStyle.Stroke);
    men.setStrokeWidth(1.2);
    men.setColor(Skia.Color('rgba(228, 250, 255, 0.9)'));
    canvas.drawPath(surface, men);
    canvas.restore();
  }

  // Front glass: the bore's thickness, a bright left stripe, a thin right one, ticks and the rim.
  const wall = Skia.Paint();
  wall.setAntiAlias(true);
  wall.setStyle(PaintStyle.Stroke);
  wall.setStrokeWidth(1.4);
  wall.setColor(Skia.Color('rgba(8, 26, 34, 0.22)'));
  canvas.drawPath(inner, wall);
  const stripe = Skia.Paint();
  stripe.setAntiAlias(true);
  stripe.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: top }, { x: 0, y: -hw }, [Skia.Color('rgba(255,255,255,0.62)'), Skia.Color('rgba(255,255,255,0.22)'), Skia.Color('rgba(255,255,255,0.08)')], [0, 0.5, 1], TileMode.Clamp),
  );
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-hw + 3, top + 6, 2.4, -top - hw - 4), 1.2, 1.2), stripe);
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1);
  edge.setColor(Skia.Color('rgba(226, 244, 250, 0.42)'));
  canvas.drawPath(tubePath(hw, top, false), edge);
  edge.setColor(Skia.Color('rgba(255, 255, 255, 0.18)'));
  canvas.drawLine(hw - 3, top + 10, hw - 3, -hw - 2, edge);
  const ticks = Skia.PathBuilder.Make();
  for (let mm = 1; mm <= CAPACITY; mm++) {
    const ty = levelY(g, mm) - g.y1;
    const len = mm % 5 === 0 ? 7 : 3.5;
    ticks.moveTo(hw - 1.5 - len, ty);
    ticks.lineTo(hw - 1.5, ty);
  }
  const tp = Skia.Paint();
  tp.setAntiAlias(true);
  tp.setStyle(PaintStyle.Stroke);
  tp.setStrokeWidth(0.8);
  tp.setStrokeCap(StrokeCap.Round);
  tp.setColor(Skia.Color('rgba(240, 250, 252, 0.55)'));
  canvas.drawPath(ticks.build(), tp);
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(1.3);
  rim.setColor(Skia.Color('rgba(236, 248, 252, 0.6)'));
  canvas.drawOval(Skia.XYWHRect(-hw - 0.8, top - 2.2, hw * 2 + 1.6, 4.4), rim);
}

/** Where a drop falling at x stops: the water in a tube, the rack's top board, or the bed. */
function landing(g: RainGeo, x: number, levels: number[], liftIdx: number, lift: number) {
  'worklet';
  for (let i = 0; i < TUBES; i++) {
    if (Math.abs(x - g.cx[i]) < g.hw - 2.5) {
      const up = i === liftIdx ? lift * LIFT_PT : 0;
      return [levelY(g, levels[i]) - up, 1];
    }
  }
  if (x > g.left && x < g.right) return [g.board, 0];
  return [g.ground + 4, 0];
}

function drawRain(canvas: SkCanvas, g: RainGeo, p: RainPose) {
  'worklet';
  if (p.rain < 0.01) return;
  const i0 = Math.max(0, Math.min(TUBES - 1, Math.floor(p.cursor)));
  const i1 = Math.min(TUBES - 1, i0 + 1);
  const xc = g.cx[i0] + (g.cx[i1] - g.cx[i0]) * (p.cursor - i0);
  const spread = g.pitch * 1.25;
  const buckets = [Skia.PathBuilder.Make(), Skia.PathBuilder.Make(), Skia.PathBuilder.Make()];
  const splash = Skia.PathBuilder.Make();
  const ripple = Skia.PathBuilder.Make();
  for (let i = 0; i < DROPS; i++) {
    const x = g.ox + 10 + hash(i * 3.1) * (g.w - 20);
    const w = Math.exp(-Math.pow((x - xc) / spread, 2));
    const alpha = Math.min(1, Math.max(0, (p.rain * (0.1 + 0.9 * w) - hash(i * 5.3) * 0.85) * 5));
    if (alpha < 0.03) continue;
    const k = 13 + Math.floor(hash(i * 7.7) * 4);
    const cyc = hash(i * 1.9) + (k * p.rainT) / LOOP;
    const f = cyc - Math.floor(cyc);
    const land = landing(g, x, p.levels, p.liftIdx, p.lift);
    const start = g.oy - 40;
    const y = start + f * (land[0] - start);
    const len = 12 + 9 * hash(i * 2.7);
    const b = buckets[alpha > 0.66 ? 2 : alpha > 0.33 ? 1 : 0];
    b.moveTo(x + len * 0.12, Math.max(start, y - len));
    b.lineTo(x, y);
    // The drop that landed a moment ago (this lane's previous fall) splashes or ripples.
    const e = (f * LOOP) / k;
    if (e < 0.16) {
      const u = e / 0.16;
      if (land[1] === 1) {
        ripple.addOval(Skia.XYWHRect(x - 2 - 4 * u, land[0] - 0.8 - 0.6 * u, 4 + 8 * u, 1.6 + 1.2 * u));
      } else {
        const hgt = 7 * 4 * u * (1 - u);
        splash.addCircle(x - 2 - 6 * u, land[0] - hgt, 0.9 * (1 - 0.5 * u));
        splash.addCircle(x + 2 + 5 * u, land[0] - hgt * 0.8, 0.8 * (1 - 0.5 * u));
        splash.addOval(Skia.XYWHRect(x - 1.5 - 3 * u, land[0] - 0.8, 3 + 6 * u, 1.6));
      }
    }
  }
  const sp = Skia.Paint();
  sp.setAntiAlias(true);
  sp.setStyle(PaintStyle.Stroke);
  sp.setStrokeWidth(1.25);
  sp.setStrokeCap(StrokeCap.Round);
  for (let b = 0; b < 3; b++) {
    sp.setColor(Skia.Color(`rgba(206, 232, 244, ${(0.3 + 0.24 * b) * p.rain})`));
    canvas.drawPath(buckets[b].build(), sp);
  }
  const dp = Skia.Paint();
  dp.setAntiAlias(true);
  dp.setColor(Skia.Color(`rgba(214, 238, 248, ${0.55 * p.rain})`));
  canvas.drawPath(splash.build(), dp);
  sp.setStrokeWidth(0.8);
  sp.setColor(Skia.Color(`rgba(220, 248, 255, ${0.6 * p.rain})`));
  canvas.drawPath(ripple.build(), sp);
}

/** The moving layer: tubes, the rack (a static picture), the lifted tube over it, the rain. */
export function drawScene(canvas: SkCanvas, g: RainGeo, p: RainPose, front: SkPicture) {
  'worklet';
  for (let i = 0; i < TUBES; i++) {
    if (i === p.liftIdx && p.lift > 0.001) continue;
    canvas.save();
    canvas.translate(g.cx[i], g.y1);
    drawTube(canvas, g, i, p.levels[i], p.slosh[i]);
    canvas.restore();
  }
  canvas.drawPicture(front);
  if (p.liftIdx >= 0 && p.lift > 0.001) {
    const i = p.liftIdx;
    const sc = 1 + LIFT_SCALE * p.lift;
    // Its shadow on the base board grows softer as it rises.
    const sh = Skia.Paint();
    sh.setColor(Skia.Color(`rgba(10, 6, 4, ${0.35 * p.lift})`));
    sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3 + 5 * p.lift, true));
    canvas.drawOval(Skia.XYWHRect(g.cx[i] - g.hw - 4, g.y1 - 6, g.hw * 2 + 8, 5), sh);
    canvas.save();
    canvas.translate(g.cx[i], g.y1 - LIFT_PT * p.lift);
    canvas.scale(sc, sc);
    drawTube(canvas, g, i, p.levels[i], p.slosh[i]);
    canvas.restore();
  }
  drawRain(canvas, g, p);
}
