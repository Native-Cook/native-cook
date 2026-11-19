import { BlendMode, Canvas, ClipOp, createPicture, PaintStyle, Picture, Skia, TileMode } from "@shopify/react-native-skia";
import type { SkPath } from "@shopify/react-native-skia";
import type { JSX } from "react";
import { useMemo } from "react";
import { StyleSheet } from "react-native";
import { useDerivedValue } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import { cameraAt, clamp01, filmFinger, handoffCamera, lerp, markGleam, markRise, markVisible, project, YAW_PER_PT } from "./spinMotion";
import { ARCHES, HFOV_DEG, MARK, PEARL_R } from "./spinScene";

/** Where the mark lands on the welcome screen: the outer arch's radius and its baseline (pt). */
export const MARK_FINAL_R = 40;

export type MarkGeometry = {
  paths: SkPath[];
  colors: string[];
  union: SkPath;
  /** Baseline centre and outer radius of the mark as the 3D stage shows it at the hand-off. */
  cx: number;
  cy: number;
  r: number;
  /** Bounds for the gleam. */
  left: number;
  right: number;
  top: number;
};

/** The flat mark, traced from the 3D arches through the stage's camera at the hand-off, so it lands on them
 * exactly: each arch's outline at its widest (mid-depth) and the pearl's disc. */
export function buildMark(w: number, h: number): MarkGeometry {
  const c = [0, 0, 0, 0, 0, 0];
  handoffCamera(c);
  const q = [0, 0, 0];
  const paths: SkPath[] = [];
  const union = Skia.PathBuilder.Make();
  const N = 72;
  let left = w;
  let right = 0;
  let top = h;
  for (let i = 0; i < ARCHES.length; i++) {
    const { r, a, b } = ARCHES[i];
    const z = b * 0.35;
    const p = Skia.PathBuilder.Make();
    for (let s = 0; s <= N; s++) {
      const th = (s / N) * Math.PI;
      project((r + a) * Math.cos(th), (r + a) * Math.sin(th), z, c, w, h, q);
      if (s === 0) p.moveTo(q[0], q[1]);
      else p.lineTo(q[0], q[1]);
      left = Math.min(left, q[0]);
      right = Math.max(right, q[0]);
      top = Math.min(top, q[1]);
    }
    for (let s = N; s >= 0; s--) {
      const th = (s / N) * Math.PI;
      project((r - a) * Math.cos(th), (r - a) * Math.sin(th), z, c, w, h, q);
      p.lineTo(q[0], q[1]);
    }
    p.close();
    const arch = p.build();
    paths.push(arch);
    union.addPath(arch);
  }
  project(0, PEARL_R, 0, c, w, h, q);
  const px = q[0];
  const py = q[1];
  const pz = q[2];
  const focal = w / 2 / Math.tan((HFOV_DEG * Math.PI) / 360);
  const pr = (PEARL_R / pz) * focal;
  const pearl = Skia.PathBuilder.Make();
  pearl.addCircle(px, py, pr);
  const disc = pearl.build();
  paths.push(disc);
  union.addPath(disc);
  project(0, 0, ARCHES[0].b * 0.35, c, w, h, q);
  const cx = q[0];
  const cy = q[1];
  project(ARCHES[0].r + ARCHES[0].a, 0, ARCHES[0].b * 0.35, c, w, h, q);
  return {
    paths,
    colors: [MARK.outer, MARK.middle, MARK.inner, MARK.pearl],
    union: union.build(),
    cx,
    cy,
    r: Math.abs(q[0] - cx),
    left,
    right,
    top,
  };
}

export type SpinMarkProps = {
  width: number;
  height: number;
  t: SharedValue<number>;
  /** The ghost finger's visibility multiplier (0 once a real finger has taken over). */
  live: SharedValue<number>;
  /** The mark's baseline on the welcome screen (pt from the top). */
  finalBase: number;
};

/** Over the 3D view: the ghost finger of the film, and the flat brand mark that takes over from the sculpture, catches
 * a gleam, and rises to its place on the welcome screen. */
export function SpinMark({ width: w, height: h, t, live, finalBase }: SpinMarkProps): JSX.Element {
  const mark = useMemo(() => buildMark(w, h), [w, h]);
  const anchor = useMemo(() => {
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(0, c);
    const q = [0, 0, 0];
    project(0, 0.2, 0.6, c, w, h, q);
    return { x: w / 2, y: q[1] + 6 };
  }, [w, h]);

  const picture = useDerivedValue(() => {
    const tt = t.get();
    const vis = markVisible(tt);
    const f = [0, 0, 0];
    filmFinger(tt, f);
    const fv = f[1] * clamp01(live.get());
    const g = [0, 0];
    markGleam(tt, g);
    const k = clamp01(markRise(tt));
    return createPicture((canvas) => {
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      if (vis > 0.004) {
        const s = lerp(1, MARK_FINAL_R / mark.r, k);
        const x = lerp(mark.cx, w / 2, k);
        const y = lerp(mark.cy, finalBase, k);
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(s, s);
        canvas.translate(-mark.cx, -mark.cy);
        for (let i = 0; i < mark.paths.length; i++) {
          paint.setColor(Skia.Color(mark.colors[i]));
          paint.setAlphaf(vis);
          canvas.drawPath(mark.paths[i], paint);
        }
        if (g[1] > 0.01) {
          canvas.save();
          canvas.clipPath(mark.union, ClipOp.Intersect, true);
          const gx = lerp(mark.left, mark.right, g[0]);
          const span = (mark.right - mark.left) * 0.22;
          const gp = Skia.Paint();
          gp.setBlendMode(BlendMode.Screen);
          gp.setShader(
            Skia.Shader.MakeLinearGradient(
              { x: gx - span, y: mark.top },
              { x: gx + span, y: mark.cy },
              [Skia.Color("rgba(255,250,240,0)"), Skia.Color(`rgba(255,250,240,${0.55 * g[1] * vis})`), Skia.Color("rgba(255,250,240,0)")],
              [0, 0.5, 1],
              TileMode.Clamp,
            ),
          );
          canvas.drawRect(Skia.XYWHRect(mark.left - 4, mark.top - 4, mark.right - mark.left + 8, mark.cy - mark.top + 8), gp);
          canvas.restore();
        }
        canvas.restore();
      }
      if (fv > 0.004) {
        const fx = anchor.x + f[0] / YAW_PER_PT;
        const fy = anchor.y;
        const lift = 1 - f[2];
        const r = 22 * (1 + 0.12 * lift);
        // soft shadow, a translucent body and a bright rim: the usual touch indicator
        paint.setShader(
          Skia.Shader.MakeRadialGradient(
            { x: fx, y: fy + 3 + 4 * lift },
            r * 1.5,
            [Skia.Color(`rgba(40,32,24,${0.16 * fv})`), Skia.Color("rgba(40,32,24,0)")],
            [0.4, 1],
            TileMode.Clamp,
          ),
        );
        canvas.drawCircle(fx, fy + 3 + 4 * lift, r * 1.5, paint);
        paint.setShader(null);
        paint.setColor(Skia.Color(`rgba(255,255,255,${0.62 * fv})`));
        canvas.drawCircle(fx, fy, r, paint);
        paint.setColor(Skia.Color(`rgba(29,28,26,${0.1 * fv})`));
        canvas.drawCircle(fx, fy, r * 0.62, paint);
        paint.setStyle(PaintStyle.Stroke);
        paint.setStrokeWidth(1);
        paint.setColor(Skia.Color(`rgba(29,28,26,${0.14 * fv})`));
        canvas.drawCircle(fx, fy, r, paint);
        paint.setStyle(PaintStyle.Fill);
      }
    });
  });

  return (
    <Canvas pointerEvents="none" style={[StyleSheet.absoluteFill, { width: w, height: h }]}>
      <Picture picture={picture} />
    </Canvas>
  );
}
