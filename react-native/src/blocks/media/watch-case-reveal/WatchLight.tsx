import {
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Path,
  RadialGradient,
  Skia,
  vec,
} from "@shopify/react-native-skia";
import type { JSX } from "react";
import { StyleSheet } from "react-native";
import { useDerivedValue } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import { cameraAt, cameraBasis, clamp01, liftOf, project, tipOf } from "./watchMotion";
import { CASE_W, CUSHION_Y, DIAL_C, HFOV_DEG, LIFT, TILT } from "./watchScene";
import type { Vec3 } from "./watchScene";

const HIDDEN = [{ translateX: -9999 }, { translateY: 0 }, { scale: 1 }];
const RIM = 28;
const DIAL_R = 0.0162;

export type WatchLightProps = {
  width: number;
  height: number;
  t: SharedValue<number>;
  open: SharedValue<number>;
  yaw: SharedValue<number>;
  macro: SharedValue<number>;
};

/** Behind the 3D view, inside the stage window: the studio light and the case's broad soft shadow on the table (the
 * paper itself is the screen's gradient, so the window has no seam). */
export function WatchBackdrop(p: WatchLightProps): JSX.Element {
  const { width: w, height: h } = p;
  const floor = useDerivedValue(() => {
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(p.open.get(), p.macro.get(), p.t.get(), c);
    const b = [0, 0, 0, 0, 0, 0, 0, 0, 0];
    cameraBasis(c, w, HFOV_DEG, b);
    const o = [0, 0, 0];
    if (!project(c, b, w, h, [0, 0, 0], o)) return HIDDEN;
    const s = (CASE_W * b[8]) / o[2];
    return [{ translateX: o[0] }, { translateY: o[1] + s * 0.05 }, { scaleX: s * 1.05 }, { scaleY: s * 0.36 }];
  });
  return (
    <Canvas pointerEvents="none" style={[StyleSheet.absoluteFill, { width: w, height: h }]}>
      <Circle cx={w * 0.4} cy={h * 0.26} r={w * 0.9}>
        <RadialGradient c={vec(w * 0.4, h * 0.26)} colors={["rgba(255,253,248,0.8)", "rgba(255,253,248,0)"]} r={w * 0.9} />
      </Circle>
      <Group transform={floor}>
        <Circle cx={0} cy={0} r={1}>
          <RadialGradient c={vec(0, 0)} colors={["rgba(52,40,30,0.28)", "rgba(52,40,30,0.1)", "rgba(52,40,30,0)"]} positions={[0, 0.5, 1]} r={1} />
        </Circle>
      </Group>
    </Canvas>
  );
}

/** A point in cushion space to world space: tipped by tilt about x, turned by yaw about y, lifted. */
function cushionToWorld(q: Vec3, tilt: number, yaw: number, y: number): Vec3 {
  "worklet";
  const ct = Math.cos(tilt);
  const st = Math.sin(tilt);
  const x1 = q[0];
  const y1 = q[1] * ct - q[2] * st;
  const z1 = q[1] * st + q[2] * ct;
  const cy = Math.cos(yaw);
  const sy = Math.sin(yaw);
  return [x1 * cy + z1 * sy, y1 + y, -x1 * sy + z1 * cy];
}

/**
 * Over the 3D view: the sapphire crystal catching the softbox. A soft bright band, clipped to the dial's projected
 * outline, slides across the glass as the watch turns, so the turn reads as light moving over a real crystal.
 */
export function WatchGlint(p: WatchLightProps): JSX.Element {
  const { width: w, height: h } = p;
  const geo = useDerivedValue(() => {
    const o = p.open.get();
    const lift = liftOf(o);
    const k = clamp01((lift - 0.2) / 0.8);
    const path = Skia.PathBuilder.Make();
    if (k <= 0.001) return { path: path.build(), k: 0, x0: 0, y0: 0, x1: 0, y1: 0 };
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(o, p.macro.get(), p.t.get(), c);
    const b = [0, 0, 0, 0, 0, 0, 0, 0, 0];
    cameraBasis(c, w, HFOV_DEG, b);
    const tilt = TILT * tipOf(o);
    const yaw = p.yaw.get();
    const y = CUSHION_Y + LIFT * lift;
    const pt = [0, 0, 0];
    let minX = 1e9;
    let maxX = -1e9;
    let minY = 1e9;
    let maxY = -1e9;
    for (let i = 0; i < RIM; i++) {
      const a = (2 * Math.PI * i) / RIM;
      const q: Vec3 = [DIAL_C[0] + Math.cos(a) * DIAL_R, DIAL_C[1], DIAL_C[2] + Math.sin(a) * DIAL_R];
      if (!project(c, b, w, h, cushionToWorld(q, tilt, yaw, y), pt)) continue;
      if (i === 0) path.moveTo(pt[0], pt[1]);
      else path.lineTo(pt[0], pt[1]);
      minX = Math.min(minX, pt[0]);
      maxX = Math.max(maxX, pt[0]);
      minY = Math.min(minY, pt[1]);
      maxY = Math.max(maxY, pt[1]);
    }
    path.close();
    // the band's centre runs across the dial with the turn (and a little with the tip)
    const s = clamp01(0.5 + Math.sin(yaw * 2.4) * 0.75 + (tipOf(o) - 1) * 0.3);
    const cx = minX + (maxX - minX) * s;
    const cy = minY + (maxY - minY) * (1 - s * 0.6);
    const r = (maxX - minX) * 0.35;
    return { path: path.build(), k, x0: cx - r, y0: cy - r * 1.4, x1: cx + r, y1: cy + r * 1.4 };
  });
  const clip = useDerivedValue(() => geo.get().path);
  const opacity = useDerivedValue(() => 0.5 * geo.get().k);
  const start = useDerivedValue(() => vec(geo.get().x0, geo.get().y0));
  const end = useDerivedValue(() => vec(geo.get().x1, geo.get().y1));
  return (
    <Canvas pointerEvents="none" style={[StyleSheet.absoluteFill, { width: w, height: h }]}>
      <Group blendMode="plus" clip={clip} opacity={opacity}>
        <Path path={clip}>
          <LinearGradient
            colors={["rgba(255,255,255,0)", "rgba(255,255,255,0.18)", "rgba(255,255,255,0.55)", "rgba(255,255,255,0.18)", "rgba(255,255,255,0)"]}
            end={end}
            positions={[0, 0.38, 0.5, 0.62, 1]}
            start={start}
          />
        </Path>
      </Group>
    </Canvas>
  );
}
