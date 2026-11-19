import { Canvas, Circle, Group, RadialGradient, vec } from "@shopify/react-native-skia";
import type { JSX } from "react";
import { StyleSheet } from "react-native";
import { useDerivedValue } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import { sceneAt, shadowAt } from "./vinylMotion";

const SHADOW_COLORS = ["rgba(58,44,30,1)", "rgba(58,44,30,0.55)", "rgba(58,44,30,0)"];
const SHADOW_STOPS = [0, 0.45, 1];

/** One soft shadow on the paper: a unit disc with a radial falloff, stretched to the projected footprint. `shapes`
 * holds both shadows' [x, y, rx, ry, angle, alpha], k picks one. */
function Shadow({ k, shapes }: { k: number; shapes: SharedValue<number[]> }) {
  const transform = useDerivedValue(() => {
    const o = shapes.get();
    const b = k * 6;
    return [{ translateX: o[b] }, { translateY: o[b + 1] }, { rotate: o[b + 4] }, { scaleX: o[b + 2] }, { scaleY: o[b + 3] }];
  });
  const opacity = useDerivedValue(() => shapes.get()[k * 6 + 5]);
  return (
    <Group opacity={opacity} transform={transform}>
      <Circle cx={0} cy={0} r={1}>
        <RadialGradient c={vec(0, 0)} colors={SHADOW_COLORS} positions={SHADOW_STOPS} r={1} />
      </Circle>
    </Group>
  );
}

/**
 * The soft contact shadows of the floating record and sleeve on the studio paper, under the transparent 3D view.
 * Each is a radial falloff stretched to its footprint as the stage's own camera projects it, so it slides, narrows
 * and swings with the record's turn. No blur pass: one gradient disc each.
 */
export function VinylShadows({ stage, ret, width, height }: { stage: SharedValue<number>; ret: SharedValue<number>; width: number; height: number }): JSX.Element {
  // the scene is solved once a frame for both shadows
  const shapes = useDerivedValue(() => {
    const sc = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
    sceneAt(stage.get(), ret.get(), sc);
    const out = [0, 0, 1, 1, 0, 0, 0, 0, 1, 1, 0, 0];
    const o = [0, 0, 1, 1, 0, 0];
    for (let k = 0; k < 2; k++) {
      if (!shadowAt(sc, k, width, height, o)) o[5] = 0;
      for (let i = 0; i < 6; i++) out[k * 6 + i] = o[i];
    }
    return out;
  });
  return (
    <Canvas pointerEvents="none" style={[StyleSheet.absoluteFill, { width, height }]}>
      <Shadow k={1} shapes={shapes} />
      <Shadow k={0} shapes={shapes} />
    </Canvas>
  );
}
