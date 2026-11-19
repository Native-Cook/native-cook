import { Canvas, createPicture, PaintStyle, Picture, Skia, StrokeCap, StrokeJoin, TileMode } from "@shopify/react-native-skia";
import type { SkCanvas, SkPaint } from "@shopify/react-native-skia";
import type { JSX } from "react";
import { useMemo } from "react";
import { StyleSheet } from "react-native";
import { useDerivedValue } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import { archYaw, cameraAt, clamp01, PHASE, pearlY, posterUi, project, seg, stageVisible, trackFill, trackPop } from "./spinMotion";
import { ARCHES, INK, PAPER_BOTTOM, PAPER_TOP, PEARL_FLOAT, PEARL_R, TRACK_R } from "./spinScene";

export type SpinFloorProps = {
  width: number;
  height: number;
  t: SharedValue<number>;
  yaw: SharedValue<number>;
};

const TRACK_STEPS = 96;

/** A soft elliptical shadow: radial gradient squashed vertically (no blur filter, nothing costly per frame). */
function shadowSpot(canvas: SkCanvas, paint: SkPaint, x: number, y: number, rx: number, ry: number, alpha: number): void {
  "worklet";
  if (alpha < 0.004 || rx < 0.5) return;
  canvas.save();
  canvas.translate(x, y);
  canvas.scale(1, Math.max(ry, 0.3) / rx);
  paint.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: 0, y: 0 },
      rx,
      [Skia.Color(`rgba(40,32,24,${alpha})`), Skia.Color(`rgba(40,32,24,${alpha * 0.45})`), Skia.Color("rgba(40,32,24,0)")],
      [0, 0.45, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(0, 0, rx, paint);
  canvas.restore();
}

/**
 * Under the 3D view: the warm paper backdrop, the sculpture's floor shadows (one soft spot under the whole cage, a
 * tight one under each foot, one under the pearl that firms up as it drops), and the progress track on the floor
 * round it, projected with the stage's own camera so the sculpture hides the far side of it.
 */
export function SpinFloor({ width: w, height: h, t, yaw }: SpinFloorProps): JSX.Element {
  const backdrop = useMemo(() => {
    const rec = Skia.PictureRecorder();
    const cv = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const p = Skia.Paint();
    p.setShader(
      Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: h }, [Skia.Color(PAPER_TOP), Skia.Color("#EFEBE4"), Skia.Color(PAPER_BOTTOM)], [0, 0.55, 1], TileMode.Clamp),
    );
    cv.drawRect(Skia.XYWHRect(0, 0, w, h), p);
    // a light pool behind the sculpture and a faint vignette
    const pool = Skia.Paint();
    pool.setShader(
      Skia.Shader.MakeRadialGradient({ x: w * 0.5, y: h * 0.4 }, w * 0.85, [Skia.Color("rgba(255,253,249,0.75)"), Skia.Color("rgba(255,253,249,0)")], [0, 1], TileMode.Clamp),
    );
    cv.drawRect(Skia.XYWHRect(0, 0, w, h), pool);
    const vig = Skia.Paint();
    vig.setShader(
      Skia.Shader.MakeRadialGradient({ x: w * 0.5, y: h * 0.45 }, Math.max(w, h) * 0.75, [Skia.Color("rgba(60,48,36,0)"), Skia.Color("rgba(60,48,36,0.07)")], [0.6, 1], TileMode.Clamp),
    );
    cv.drawRect(Skia.XYWHRect(0, 0, w, h), vig);
    return rec.finishRecordingAsPicture();
  }, [w, h]);

  const floor = useDerivedValue(() => {
    const tt = t.get();
    const yw = yaw.get();
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(tt, c);
    const ui = clamp01(posterUi(tt));
    const vis = clamp01(stageVisible(tt));
    const fill = trackFill(tt, yw);
    const pop = trackPop(tt);
    return createPicture((canvas) => {
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      const q = [0, 0, 0];
      const q2 = [0, 0, 0];

      // shadows (they leave with the 3D view)
      if (vis > 0.01) {
        project(0, 0, 0, c, w, h, q);
        project(1.1, 0, 0, c, w, h, q2);
        const rx = Math.abs(q2[0] - q[0]);
        project(0, 0, 1.1, c, w, h, q2);
        const ry = Math.abs(q2[1] - q[1]);
        shadowSpot(canvas, paint, q[0], q[1], rx * 1.05, ry * 1.05, 0.1 * vis);
        const base = (yw * Math.PI) / 180;
        for (let i = 0; i < ARCHES.length; i++) {
          const ang = base + (archYaw(tt, i) * Math.PI) / 180;
          const ca = Math.cos(ang);
          const sa = Math.sin(ang);
          for (let side = -1; side <= 1; side += 2) {
            const r = ARCHES[i].r * side;
            // foot centre (x along the arch's plane, rotated about y)
            const fx = r * ca;
            const fz = -r * sa;
            project(fx, 0, fz, c, w, h, q);
            project(fx + 0.17, 0, fz, c, w, h, q2);
            const sx = Math.abs(q2[0] - q[0]);
            project(fx, 0, fz + 0.17, c, w, h, q2);
            const sy = Math.abs(q2[1] - q[1]);
            shadowSpot(canvas, paint, q[0], q[1] + sy * 0.15, sx, sy + 1.5, 0.42 * vis);
          }
        }
        // the pearl: a wide pale spot while it floats, small and dark once it rests
        const py = pearlY(tt);
        const k = clamp01((PEARL_FLOAT - py) / (PEARL_FLOAT - PEARL_R));
        project(0, 0, 0, c, w, h, q);
        const rr = PEARL_R * (1.9 - 0.9 * k);
        project(rr, 0, 0, c, w, h, q2);
        const sx = Math.abs(q2[0] - q[0]);
        project(0, 0, rr, c, w, h, q2);
        const sy = Math.abs(q2[1] - q[1]);
        shadowSpot(canvas, paint, q[0], q[1], sx, sy + 1, (0.14 + 0.36 * k) * vis);
      }

      // the floor track: full ring, 12 ticks, the filled arc from the front, a head dot
      if (ui > 0.01) {
        const path = Skia.PathBuilder.Make();
        for (let s = 0; s <= TRACK_STEPS; s++) {
          const a = (s / TRACK_STEPS) * Math.PI * 2;
          project(TRACK_R * Math.sin(a), 0, TRACK_R * Math.cos(a), c, w, h, q);
          if (s === 0) path.moveTo(q[0], q[1]);
          else path.lineTo(q[0], q[1]);
        }
        paint.setShader(null);
        paint.setStyle(PaintStyle.Stroke);
        paint.setStrokeWidth(1.25);
        paint.setColor(Skia.Color(`rgba(29,28,26,${0.13 * ui})`));
        canvas.drawPath(path.build(), paint);
        paint.setStrokeWidth(1.25);
        for (let k = 0; k < 12; k++) {
          const a = (k / 12) * Math.PI * 2;
          project((TRACK_R + 0.03) * Math.sin(a), 0, (TRACK_R + 0.03) * Math.cos(a), c, w, h, q);
          project((TRACK_R + 0.07) * Math.sin(a), 0, (TRACK_R + 0.07) * Math.cos(a), c, w, h, q2);
          paint.setColor(Skia.Color(`rgba(29,28,26,${0.2 * ui})`));
          canvas.drawLine(q[0], q[1], q2[0], q2[1], paint);
        }
        if (fill > 0.002) {
          const dir = yw < 0 ? -1 : 1;
          const arc = Skia.PathBuilder.Make();
          const n = Math.max(2, Math.ceil(TRACK_STEPS * fill));
          for (let s = 0; s <= n; s++) {
            const a = dir * (s / n) * fill * Math.PI * 2;
            project(TRACK_R * Math.sin(a), 0, TRACK_R * Math.cos(a), c, w, h, q);
            if (s === 0) arc.moveTo(q[0], q[1]);
            else arc.lineTo(q[0], q[1]);
          }
          paint.setStrokeWidth(3 + 2 * pop);
          paint.setStrokeCap(StrokeCap.Round);
          paint.setStrokeJoin(StrokeJoin.Round);
          paint.setColor(Skia.Color(INK));
          paint.setAlphaf(ui);
          canvas.drawPath(arc.build(), paint);
          // the head: a dot with a paper ring
          const a = dir * fill * Math.PI * 2;
          project(TRACK_R * Math.sin(a), 0, TRACK_R * Math.cos(a), c, w, h, q);
          paint.setStyle(PaintStyle.Fill);
          paint.setColor(Skia.Color(PAPER_TOP));
          paint.setAlphaf(ui);
          canvas.drawCircle(q[0], q[1], 7 + 3 * pop, paint);
          paint.setColor(Skia.Color(INK));
          paint.setAlphaf(ui);
          canvas.drawCircle(q[0], q[1], 4.5 + 2 * pop, paint);
        }
        // the pop when the track closes: a ring that widens and fades
        if (pop > 0.01) {
          const u = seg(tt, PHASE.turnEnd - 0.05, PHASE.turnEnd + 0.4);
          const rr = TRACK_R + 0.12 * u;
          const ring = Skia.PathBuilder.Make();
          for (let s = 0; s <= TRACK_STEPS; s++) {
            const a = (s / TRACK_STEPS) * Math.PI * 2;
            project(rr * Math.sin(a), 0, rr * Math.cos(a), c, w, h, q);
            if (s === 0) ring.moveTo(q[0], q[1]);
            else ring.lineTo(q[0], q[1]);
          }
          paint.setStyle(PaintStyle.Stroke);
          paint.setStrokeWidth(2);
          paint.setColor(Skia.Color(INK));
          paint.setAlphaf(0.3 * (1 - u) * ui);
          canvas.drawPath(ring.build(), paint);
        }
      }
    });
  });

  return (
    <Canvas pointerEvents="none" style={[StyleSheet.absoluteFill, { width: w, height: h }]}>
      <Picture picture={backdrop} />
      <Picture picture={floor} />
    </Canvas>
  );
}
