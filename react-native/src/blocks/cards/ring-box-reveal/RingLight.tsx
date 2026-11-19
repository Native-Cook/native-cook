import { BlendMode, BlurStyle, Canvas, ClipOp, createPicture, PaintStyle, Picture, Skia, StrokeCap, TileMode, vec } from "@shopify/react-native-skia";
import type { SkCanvas, SkPaint } from "@shopify/react-native-skia";
import type { JSX } from "react";
import { StyleSheet } from "react-native";
import { useDerivedValue } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import { cameraAt, cameraBasis, clamp01, facet, glint, hash, LOOP, project, sparkleOf } from "./ringMotion";
import { BASE_H, BASE_R, BG_BOTTOM, BG_TOP, BOX_D, BOX_RV, BOX_W, CARAT_MODEL, HFOV_DEG, STONE_C, STONE_CROWN, STONE_R, STONES } from "./ringScene";
import type { Vec3 } from "./ringScene";

const FACETS = 40;
/** The box's footprint on the floor: four corner arcs of FP_ARC points each. */
const FP_ARC = 9;
const FP_N = FP_ARC * 4;
/** How much floor covers the reflection where it meets the box (it shows 1 - this), solid a box height away. */
const FLOOR_NEAR = 0.6;
/** Stars stop growing past this radius (pt) once the stone is large on screen (the macro). */
const STAR_MAX = 26;

/** Each stone's glint tint (sRGB 0..255) and how much spectral fire it throws. */
const TINTS: readonly (readonly number[])[] = [
  [255, 255, 255, 1],
  [170, 196, 255, 0.2],
  [255, 214, 200, 0.35],
];
/** Fire: the colours a diamond's dispersion splits the glints into. */
const FIRE: readonly (readonly number[])[] = [
  [255, 120, 110],
  [255, 214, 120],
  [140, 255, 190],
  [120, 190, 255],
  [210, 150, 255],
];

export type RingLightProps = {
  width: number;
  height: number;
  /** Where the screen's top is in stage coordinates (minus the stage's top) and the screen's height: the paper's
   * gradient runs over the whole screen, so the stage draws its slice of it. */
  paperTop: number;
  paperH: number;
  t: SharedValue<number>;
  open: SharedValue<number>;
  shot: SharedValue<number>;
  yaw: SharedValue<number>;
  burst: SharedValue<number>;
  sFrom: SharedValue<number>;
  sTo: SharedValue<number>;
  sProg: SharedValue<number>;
  carat: SharedValue<number>;
};

// ---------------------------------------------------------------- floor helpers (worklets, declared before callers)

/** The box's footprint, turned with the box and projected: out = [x0, y0, x1, y1, ...] (FP_N points) followed by
 * [centre x, centre y, half width, half height, box height on screen]. False when it is not in front of the lens. */
function floorGeom(c: number[], b: number[], w: number, h: number, yaw: number, out: number[]): boolean {
  "worklet";
  const ins = BASE_R * 0.8;
  const hw = BOX_W / 2 - ins;
  const hd = BOX_D / 2 - ins;
  const r = BOX_RV - ins;
  const cy = Math.cos(yaw);
  const sy = Math.sin(yaw);
  const pt = [0, 0, 0];
  let x0 = 1e9;
  let x1 = -1e9;
  let y0 = 1e9;
  let y1 = -1e9;
  for (let j = 0; j < FP_N; j++) {
    const corner = Math.floor(j / FP_ARC);
    const k = j % FP_ARC;
    const a = ((corner + k / (FP_ARC - 1)) * Math.PI) / 2;
    const ccx = corner === 0 || corner === 3 ? hw - r : r - hw;
    const ccz = corner === 0 || corner === 1 ? hd - r : r - hd;
    const x = ccx + r * Math.cos(a);
    const z = ccz + r * Math.sin(a);
    const p: Vec3 = [x * cy + z * sy, 0, -x * sy + z * cy];
    if (!project(c, b, w, h, p, pt)) return false;
    out[2 * j] = pt[0];
    out[2 * j + 1] = pt[1];
    if (pt[0] < x0) x0 = pt[0];
    if (pt[0] > x1) x1 = pt[0];
    if (pt[1] < y0) y0 = pt[1];
    if (pt[1] > y1) y1 = pt[1];
  }
  const o = [0, 0, 0];
  if (!project(c, b, w, h, [0, 0, 0], o)) return false;
  const top = [0, 0, 0];
  if (!project(c, b, w, h, [0, BASE_H, 0], top)) return false;
  out[2 * FP_N] = (x0 + x1) / 2;
  out[2 * FP_N + 1] = (y0 + y1) / 2;
  out[2 * FP_N + 2] = Math.max(1, (x1 - x0) / 2);
  out[2 * FP_N + 3] = Math.max(1, (y1 - y0) / 2);
  out[2 * FP_N + 4] = Math.hypot(top[0] - o[0], top[1] - o[1]);
  return true;
}

/** The floor: the paper's gradient, a soft studio light, and (when `g` holds a footprint) the box's broad shadow and
 * its tight contact shadow. Drawn twice: behind the 3D view, and inside the reflection's fade over it. */
function drawFloor(canvas: SkCanvas, paint: SkPaint, w: number, h: number, paperTop: number, paperH: number, g: number[] | null): void {
  "worklet";
  paint.setStyle(PaintStyle.Fill);
  paint.setMaskFilter(null);
  paint.setShader(Skia.Shader.MakeLinearGradient(vec(0, paperTop), vec(0, paperTop + paperH), [Skia.Color(BG_TOP), Skia.Color(BG_BOTTOM)], null, TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(0, 0, w, h), paint);
  paint.setShader(
    Skia.Shader.MakeRadialGradient(vec(w * 0.42, h * 0.22), w * 0.9, [Skia.Color("rgba(255,253,249,0.8)"), Skia.Color("rgba(255,253,249,0)")], null, TileMode.Clamp),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, w, h), paint);
  if (g == null) {
    paint.setShader(null);
    return;
  }
  const cx = g[2 * FP_N];
  const cy = g[2 * FP_N + 1];
  const a = g[2 * FP_N + 2];
  const bb = g[2 * FP_N + 3];
  // broad shadow: a soft pool under and a little in front of the box
  const sx = cy + bb * 0.12;
  const m = Skia.Matrix();
  m.translate(cx, sx);
  m.scale(1, (bb / a) * 1.15);
  m.translate(-cx, -sx);
  paint.setShader(
    Skia.Shader.MakeRadialGradient(
      vec(cx, sx),
      a * 1.4,
      [Skia.Color("rgba(22,24,38,0.2)"), Skia.Color("rgba(22,24,38,0.07)"), Skia.Color("rgba(22,24,38,0)")],
      [0, 0.6, 1],
      TileMode.Clamp,
      m,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, w, h), paint);
  paint.setShader(null);
  // contact: the footprint itself, dark and blurred
  const path = Skia.PathBuilder.Make();
  path.moveTo(g[0], g[1]);
  for (let j = 1; j < FP_N; j++) path.lineTo(g[2 * j], g[2 * j + 1]);
  path.close();
  paint.setColor(Skia.Color("rgba(14,16,28,0.55)"));
  paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, Math.max(1.2, a * 0.03), true));
  canvas.drawPath(path.build(), paint);
  paint.setMaskFilter(null);
}

/** The floor below the box's front edge (where its reflection can show), as a path: the footprint's front chain from
 * its leftmost to its rightmost point, out to the stage's sides and down to its bottom. */
function frontRegion(g: number[], w: number, h: number) {
  "worklet";
  let iL = 0;
  let iR = 0;
  let iB = 0;
  for (let j = 1; j < FP_N; j++) {
    if (g[2 * j] < g[2 * iL]) iL = j;
    if (g[2 * j] > g[2 * iR]) iR = j;
    if (g[2 * j + 1] > g[2 * iB + 1]) iB = j;
  }
  // which way round from the leftmost point passes the lowest (front) point before the rightmost one
  let passes = iB === iR;
  let j = iL;
  while (j !== iR) {
    if (j === iB) passes = true;
    j = (j + 1) % FP_N;
  }
  const dir = passes ? 1 : -1;
  const path = Skia.PathBuilder.Make();
  path.moveTo(-2, g[2 * iL + 1]);
  j = iL;
  for (let guard = 0; guard <= FP_N; guard++) {
    path.lineTo(g[2 * j], g[2 * j + 1]);
    if (j === iR) break;
    j = (j + dir + FP_N) % FP_N;
  }
  path.lineTo(w + 2, g[2 * iR + 1]);
  path.lineTo(w + 2, h + 2);
  path.lineTo(-2, h + 2);
  path.close();
  return path.build();
}

/** Behind the 3D view, inside the stage window: the paper, the studio light and the box's shadows. */
export function RingBackdrop(p: RingLightProps): JSX.Element {
  const { width: w, height: h, paperTop, paperH } = p;
  const picture = useDerivedValue(() => {
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(p.open.get(), p.shot.get(), p.t.get(), c);
    const b = [0, 0, 0, 0, 0, 0, 0, 0, 0];
    cameraBasis(c, w, HFOV_DEG, b);
    const g: number[] = [];
    for (let i = 0; i < 2 * FP_N + 5; i++) g.push(0);
    const ok = floorGeom(c, b, w, h, p.yaw.get(), g);
    return createPicture((canvas) => {
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      drawFloor(canvas, paint, w, h, paperTop, paperH, ok ? g : null);
    });
  });
  return (
    <Canvas pointerEvents="none" style={[StyleSheet.absoluteFill, { width: w, height: h }]}>
      <Picture picture={picture} />
    </Canvas>
  );
}

/**
 * Over the 3D view: the reflection's fade. The stage draws the box's base and lid mirrored under the floor; here the
 * floor in front of the box is drawn again over them, see-through where it meets the box (it covers FLOOR_NEAR of
 * the reflection there) and solid a box height away, so the reflection reads as a soft, polished surface. It is the
 * backdrop's own drawing, so where there is no reflection under it nothing changes.
 */
export function RingFloor(p: RingLightProps): JSX.Element {
  const { width: w, height: h, paperTop, paperH } = p;
  const picture = useDerivedValue(() => {
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(p.open.get(), p.shot.get(), p.t.get(), c);
    const b = [0, 0, 0, 0, 0, 0, 0, 0, 0];
    cameraBasis(c, w, HFOV_DEG, b);
    const g: number[] = [];
    for (let i = 0; i < 2 * FP_N + 5; i++) g.push(0);
    const ok = floorGeom(c, b, w, h, p.yaw.get(), g);
    return createPicture((canvas) => {
      if (!ok) return;
      const cx = g[2 * FP_N];
      const cy = g[2 * FP_N + 1];
      const a = g[2 * FP_N + 2];
      const bb = g[2 * FP_N + 3];
      if (cy - bb > h) return;
      const k = Math.max(0.3, (0.9 * g[2 * FP_N + 4]) / bb);
      canvas.save();
      canvas.clipPath(frontRegion(g, w, h), ClipOp.Intersect, true);
      canvas.saveLayer();
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      drawFloor(canvas, paint, w, h, paperTop, paperH, g);
      // keep the floor where it should hide the reflection: none at the footprint, FLOOR_NEAR just outside it, all
      // of it a box height away (an ellipse fitted to the footprint)
      const m = Skia.Matrix();
      m.translate(cx, cy);
      m.scale(1, bb / a);
      m.translate(-cx, -cy);
      paint.setMaskFilter(null);
      paint.setBlendMode(BlendMode.DstIn);
      paint.setShader(
        Skia.Shader.MakeRadialGradient(
          vec(cx, cy),
          a * (1 + k),
          [Skia.Color("rgba(0,0,0,0)"), Skia.Color("rgba(0,0,0,0)"), Skia.Color(`rgba(0,0,0,${FLOOR_NEAR})`), Skia.Color("rgba(0,0,0,1)")],
          [0, 0.98 / (1 + k), 1.06 / (1 + k), 1],
          TileMode.Clamp,
          m,
        ),
      );
      canvas.drawRect(Skia.XYWHRect(0, 0, w, h), paint);
      canvas.restore();
      canvas.restore();
    });
  });
  return (
    <Canvas pointerEvents="none" style={[StyleSheet.absoluteFill, { width: w, height: h }]}>
      <Picture picture={picture} />
    </Canvas>
  );
}

/**
 * Over the 3D view: the stone's glints. Forty crown facets, each with its own normal, are turned with the box and
 * projected with the stage's camera; a facet flashes when it mirrors one of the studio's lights into the lens (the
 * same reflection the 3D stone shows, made sharp), drawn as a four-point star with a soft core and, for a diamond,
 * spectral fire. The snap of the lid sets off a burst.
 */
export function RingSparkle(p: RingLightProps): JSX.Element {
  const { width: w, height: h } = p;
  const picture = useDerivedValue(() => {
    const o = p.open.get();
    const t = p.t.get();
    const yaw = p.yaw.get();
    const vis = sparkleOf(o);
    const burst = clamp01(p.burst.get());
    const sa = Math.round(p.sFrom.get());
    const sb = Math.round(p.sTo.get());
    const sk = clamp01(p.sProg.get());
    const fire = TINTS[sa][3] + (TINTS[sb][3] - TINTS[sa][3]) * sk;
    const tr = TINTS[sa][0] + (TINTS[sb][0] - TINTS[sa][0]) * sk;
    const tg = TINTS[sa][1] + (TINTS[sb][1] - TINTS[sa][1]) * sk;
    const tb = TINTS[sa][2] + (TINTS[sb][2] - TINTS[sa][2]) * sk;
    const glow = (STONES[sa].glow + (STONES[sb].glow - STONES[sa].glow) * sk) * 0.1;
    const k = Math.cbrt(p.carat.get() / CARAT_MODEL);
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(o, p.shot.get(), t, c);
    const b = [0, 0, 0, 0, 0, 0, 0, 0, 0];
    cameraBasis(c, w, HFOV_DEG, b);
    return createPicture((canvas) => {
      if (vis <= 0.002) return;
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      paint.setBlendMode(BlendMode.Plus);
      paint.setStrokeCap(StrokeCap.Round);
      const cy = Math.cos(yaw);
      const sy = Math.sin(yaw);
      const f = [0, 0, 0, 0, 0, 0];
      const n = [0, 0, 0];
      const v = [0, 0, 0];
      const pt = [0, 0, 0];
      const cen = [0, 0, 0];
      const sc: Vec3 = [STONE_C[0], STONE_C[1], STONE_C[2]];
      if (!project(c, b, w, h, sc, cen)) return;
      const rpx = (STONE_R * k * b[8]) / cen[2];
      const rs = Math.min(rpx, STAR_MAX);
      // a soft white halo round the stone, stronger in the burst
      const halo = vis * (0.1 + glow + 0.45 * burst);
      paint.setStyle(PaintStyle.Fill);
      paint.setShader(
        Skia.Shader.MakeRadialGradient(
          vec(cen[0], cen[1] - rpx * 0.3),
          rpx * (2.2 + 2.5 * burst),
          [Skia.Color(`rgba(255,255,255,${0.5 * halo})`), Skia.Color("rgba(255,255,255,0)")],
          null,
          TileMode.Clamp,
        ),
      );
      canvas.drawCircle(cen[0], cen[1] - rpx * 0.3, rpx * (2.2 + 2.5 * burst), paint);
      paint.setShader(null);
      for (let i = 0; i < FACETS; i++) {
        facet(i, STONE_R * k, STONE_CROWN * k, f);
        // turn the facet with the box (about y), then to world
        n[0] = f[0] * cy + f[2] * sy;
        n[1] = f[1];
        n[2] = -f[0] * sy + f[2] * cy;
        const wp: Vec3 = [STONE_C[0] + f[3] * cy + f[5] * sy, STONE_C[1] + f[4], STONE_C[2] - f[3] * sy + f[5] * cy];
        v[0] = c[0] - wp[0];
        v[1] = c[1] - wp[1];
        v[2] = c[2] - wp[2];
        const vl = Math.hypot(v[0], v[1], v[2]) || 1;
        v[0] /= vl;
        v[1] /= vl;
        v[2] /= vl;
        // twinkle: each facet breathes on its own whole number of cycles a loop (so the film repeats)
        const cyc = 2 + Math.floor(hash(i + 29) * 5);
        const tw = 0.75 + 0.25 * Math.sin((2 * Math.PI * cyc * t) / LOOP + hash(i + 31) * 6.28);
        const g = glint(n, v) * tw * vis * (1 + 2.4 * burst) + burst * 0.5 * hash(i + 41) * (i % 3 === 0 ? 1 : 0);
        if (g < 0.04) continue;
        if (!project(c, b, w, h, wp, pt)) continue;
        const s = Math.min(1.6, g);
        const len = rs * (0.5 + 1.5 * s);
        // the star's colour: the stone's tint, a diamond's fire on every other facet
        let r = tr;
        let gg = tg;
        let bb = tb;
        const fc = FIRE[i % FIRE.length];
        const fk = fire * 0.6 * (i % 2);
        r = r + (fc[0] - r) * fk;
        gg = gg + (fc[1] - gg) * fk;
        bb = bb + (fc[2] - bb) * fk;
        const a = Math.min(1, 0.35 + 0.65 * s);
        paint.setStyle(PaintStyle.Stroke);
        paint.setColor(Skia.Color(`rgba(${Math.round(r)},${Math.round(gg)},${Math.round(bb)},${a})`));
        paint.setStrokeWidth(Math.max(0.6, rs * 0.06));
        canvas.drawLine(pt[0] - len, pt[1], pt[0] + len, pt[1], paint);
        canvas.drawLine(pt[0], pt[1] - len, pt[0], pt[1] + len, paint);
        paint.setStrokeWidth(Math.max(0.5, rs * 0.04));
        const d = len * 0.42;
        canvas.drawLine(pt[0] - d, pt[1] - d, pt[0] + d, pt[1] + d, paint);
        canvas.drawLine(pt[0] - d, pt[1] + d, pt[0] + d, pt[1] - d, paint);
        paint.setStyle(PaintStyle.Fill);
        paint.setColor(Skia.Color(`rgba(255,255,255,${0.9 * a})`));
        canvas.drawCircle(pt[0], pt[1], Math.max(0.8, rs * 0.09 * (0.6 + s)), paint);
      }
    });
  });
  return (
    <Canvas pointerEvents="none" style={[StyleSheet.absoluteFill, { width: w, height: h }]}>
      <Picture picture={picture} />
    </Canvas>
  );
}
