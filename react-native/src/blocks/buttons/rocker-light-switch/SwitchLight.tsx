import { Canvas, Circle, Group, RadialGradient, Rect, vec } from "@shopify/react-native-skia";
import type { JSX } from "react";
import { useMemo } from "react";
import { StyleSheet } from "react-native";
import { useDerivedValue } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import { cameraAt, lightOut, project } from "./switchMotion";
import { DUSK, GLOBE, GLOBE_R } from "./switchScene";

/** The halo reaches this many globe radii. */
const HALO = 4.2;
const HIDDEN = [{ translateX: -9999 }, { translateY: 0 }, { scale: 1 }];

export type SwitchLightProps = {
  width: number;
  height: number;
  t: SharedValue<number>;
  level: SharedValue<number>;
  bright: SharedValue<number>;
  orbit: SharedValue<number>;
};

/**
 * Over the 3D view: the dusk (a blue veil with darker corners that lifts as the light comes on) and the globe's
 * halo in the room's air, pinned to the globe with the stage's own camera maths. Two gradient fills, no blur.
 */
export function SwitchLight(p: SwitchLightProps): JSX.Element {
  const { width: w, height: h } = p;
  const dusk = useDerivedValue(() => 1 - 0.92 * lightOut(p.level.get(), p.bright.get()));
  const glow = useDerivedValue(() => 0.55 * lightOut(p.level.get(), p.bright.get()));
  const halo = useDerivedValue(() => {
    if (lightOut(p.level.get(), p.bright.get()) <= 0.002) return HIDDEN;
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(p.orbit.get(), p.t.get(), c);
    const o = [0, 0, 0];
    project(c, w, h, GLOBE, o);
    return [{ translateX: o[0] }, { translateY: o[1] }, { scale: GLOBE_R * o[2] * HALO }];
  });
  // the dusk falls away from the lamp: clear round the globe (at rest), deepest in the far corner
  const lamp = useMemo(() => {
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(0, 0, c);
    const o = [0, 0, 0];
    project(c, w, h, GLOBE, o);
    return { x: o[0], y: o[1] };
  }, [h, w]);
  const far = Math.hypot(Math.max(lamp.x, w - lamp.x), Math.max(lamp.y, h - lamp.y));
  return (
    <Canvas pointerEvents="none" style={[StyleSheet.absoluteFill, { width: w, height: h }]}>
      <Group opacity={dusk}>
        <Rect color={DUSK} height={h} opacity={0.3} width={w} x={0} y={0} />
        <Rect height={h} width={w} x={0} y={0}>
          <RadialGradient c={vec(lamp.x, lamp.y)} colors={["rgba(8,14,34,0)", "rgba(8,14,34,0.16)", "rgba(8,14,34,0.4)", "rgba(8,14,34,0.6)"]} positions={[0.08, 0.4, 0.75, 1]} r={far} />
        </Rect>
      </Group>
      <Group opacity={glow} transform={halo}>
        <Circle cx={0} cy={0} r={1}>
          <RadialGradient c={vec(0, 0)} colors={["rgba(255,214,150,0.5)", "rgba(255,196,120,0.2)", "rgba(255,188,110,0.06)", "rgba(255,188,110,0)"]} positions={[0, 0.3, 0.62, 1]} r={1} />
        </Circle>
      </Group>
    </Canvas>
  );
}
