import { Canvas, Circle, Group, RadialGradient, vec } from "@shopify/react-native-skia";
import type { JSX } from "react";
import { StyleSheet } from "react-native";
import { useDerivedValue } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import { shadowAt } from "./planeMotion";

const HIDDEN = [{ translateX: -9999 }, { translateY: 0 }, { scaleX: 1 }, { scaleY: 1 }];

export type PlaneShadowProps = {
  width: number;
  height: number;
  t: SharedValue<number>;
  geo: SharedValue<number[]>;
};

/** Under the 3D layer: the paper's soft shadow on the chat. One radial gradient, squashed and moved; no blur. */
export function PlaneShadow({ width, height, t, geo }: PlaneShadowProps): JSX.Element {
  const alpha = useDerivedValue(() => {
    const o = [0, 0, 0, 0, 0];
    shadowAt(t.get(), geo.get(), o);
    return o[4];
  });
  const place = useDerivedValue(() => {
    const o = [0, 0, 0, 0, 0];
    shadowAt(t.get(), geo.get(), o);
    if (o[4] <= 0.002) return HIDDEN;
    return [{ translateX: o[0] }, { translateY: o[1] }, { scaleX: o[2] }, { scaleY: o[3] }];
  });
  return (
    <Canvas pointerEvents="none" style={[StyleSheet.absoluteFill, { width, height }]}>
      <Group opacity={alpha} transform={place}>
        <Circle cx={0} cy={0} r={1}>
          <RadialGradient c={vec(0, 0)} colors={["rgba(0,0,0,0.95)", "rgba(0,0,0,0.6)", "rgba(0,0,0,0.18)", "rgba(0,0,0,0)"]} positions={[0, 0.45, 0.78, 1]} r={1} />
        </Circle>
      </Group>
    </Canvas>
  );
}
