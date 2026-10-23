import { StyleSheet } from "react-native";
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
} from "react-native-reanimated";
import {
  Blur,
  BlurMask,
  Canvas,
  Group,
  LinearGradient,
  Oval,
  Paint,
  Rect,
  interpolateColors,
  vec,
} from "@shopify/react-native-skia";
import { tactile } from "./tactile-theme";
import { referenceGeometry as g } from "./reference-motion";
import { DeviceArt, specimen, type Tilt } from "./device-art";
import type { Effects } from "./effects";
import type { TactileControl } from "./use-tactile-control";

const TRANSFER_AT = tactile.transferAt;
// Drawing units below the specimen box available to the floor layers.
const FLOOR = 210;
// Glass floor: the mirror line sits just under the housing; the reflection is
// squashed slightly for perspective, softened a touch and faded with depth.
export const GAP = 6;
export const SQUASH = 0.9;
export const DEPTH = 0.7;
// Visibility of the reflection from the glass edge down: strong at contact,
// then an eased falloff to nothing. Applied as a page-colored "fog" over the
// mirrored device (opacity = 1 - visibility); a Skia Mask dropped most of
// the device's layers here, so it is not used.
export const VISIBLE = [0.42, 0.2, 0.06, 0];
export const VISIBLE_AT = [0, 0.25, 0.55, 1];

// Brightness is a "world" setting: its level dims the page around the
// device (≤10% black at 0), whichever channel is being edited.
export function StageAmbient({
  control,
  fx,
}: {
  control: TactileControl;
  fx: Effects;
}) {
  const { brightness } = control;
  const on = fx.ambient && !control.reduced;
  const style = useAnimatedStyle(() => ({
    opacity: on ? (1 - Math.max(0, Math.min(1, brightness.get()))) * 0.1 : 0,
  }));
  return (
    <Animated.View
      pointerEvents="none"
      style={[StyleSheet.absoluteFill, { backgroundColor: "#000000" }, style]}
    />
  );
}

// The surface under the device: a glass floor that mirrors the live device
// (same light, knob and spill, drawn by DeviceArt), and the colored pool
// where the light falls on it.
export function StageFloor({
  control,
  fx,
  tilt,
  scale,
}: {
  control: TactileControl;
  fx: Effects;
  tilt: SharedValue<Tilt>;
  scale: number;
}) {
  const { fill, mix, lightAlpha, brightness } = control;
  // The fog must match the page as seen, including the brightness ambience.
  const dimmed = fx.ambient && !control.reduced;
  const fog = useDerivedValue(() => {
    const dim = dimmed
      ? (1 - Math.max(0, Math.min(1, brightness.get()))) * 0.1
      : 0;
    const c = Math.round(255 * (1 - dim));
    return VISIBLE.map((v) => `rgba(${c},${c},${c},${1 - v})`);
  });
  const bottom = specimen.top + g.height;
  const mirror = bottom + GAP / 2;
  const depth = g.height * SQUASH * DEPTH;
  const extent = useDerivedValue(() => g.track.width * fill.get());
  const tipColor = useDerivedValue(() =>
    interpolateColors(mix.get(), TRANSFER_AT, [...tactile.transfer[1]]),
  );
  const pool = useDerivedValue(() =>
    fx.spill
      ? lightAlpha.get() *
        Math.min(1, extent.get() / 8) *
        (0.18 + 0.32 * fill.get())
      : 0,
  );
  const poolX = useDerivedValue(
    () => g.track.x + extent.get() * 0.7 - (160 + 300 * fill.get()) / 2,
  );
  const poolW = useDerivedValue(() => 160 + 300 * fill.get());
  return (
    <Canvas
      pointerEvents="none"
      style={{
        position: "absolute",
        left: 0,
        right: 0,
        top: 0,
        height: (specimen.height + FLOOR) * scale,
      }}
    >
      <Group transform={[{ scale }]}>
        {fx.reflection && (
          <>
            <Group
              layer={
                <Paint>
                  <Blur blur={1.2} />
                </Paint>
              }
              transform={[
                { translateY: mirror },
                { scaleY: -SQUASH },
                { translateY: -mirror },
                { translateY: specimen.top },
              ]}
            >
              <DeviceArt control={control} fx={fx} tilt={tilt} baked />
            </Group>
            <Rect x={0} y={mirror} width={1000} height={g.height}>
              <LinearGradient
                start={vec(0, mirror)}
                end={vec(0, mirror + depth)}
                colors={fog}
                positions={VISIBLE_AT}
              />
            </Rect>
            {/* The glass edge: a hairline catching the light where the
                device meets its reflection, brightest in the middle. */}
            <Rect x={120} y={mirror - 0.6} width={760} height={1.2}>
              <LinearGradient
                start={vec(120, 0)}
                end={vec(880, 0)}
                colors={["#FFFFFF00", "#FFFFFFB0", "#FFFFFF00"]}
              />
            </Rect>
          </>
        )}
        {fx.spill && (
          <Oval
            x={poolX}
            y={bottom + 6}
            width={poolW}
            height={44}
            color={tipColor}
            opacity={pool}
          >
            <BlurMask blur={26} style="normal" />
          </Oval>
        )}
      </Group>
    </Canvas>
  );
}
