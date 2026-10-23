import { useEffect, useMemo } from "react";
import { AccessibilityInfo, PixelRatio, Platform, StyleSheet } from "react-native";
import { Canvas, Fill, Shader, Skia } from "@shopify/react-native-skia";
import {
  cancelAnimation,
  Easing,
  useDerivedValue,
  useSharedValue,
  withRepeat,
  withTiming,
} from "react-native-reanimated";
import { HORIZON_LIGHT, horizonLightFrame, horizonLightGeometry } from "./horizon-light";
import { horizonMotion, horizonSize } from "./horizon-palette";
import { cardScale, type HorizonFx } from "./horizon-state";

const EFFECT = Skia.RuntimeEffect.Make(HORIZON_LIGHT);
if (!EFFECT) throw new Error("Horizon Upload: the scene shader did not compile.");

export function HorizonScene({ width, height, card, fx, animated }: {
  /** The screen, in points. The canvas fills it. */
  width: number;
  height: number;
  /** The card's resting top left and width. Its height is horizonSize.cardHeight. Drag displacement comes from fx. */
  card: { x: number; y: number; width: number };
  fx: HorizonFx;
  /** False under Reduce Motion: the shimmer stands still and the completion has no sweep. */
  animated: boolean;
}) {
  const shimmer = useSharedValue(0);
  const opaque = useSharedValue(0);

  useEffect(() => {
    if (Platform.OS !== "ios") return;
    let active = true;
    let changed = false;
    const subscription = AccessibilityInfo.addEventListener("reduceTransparencyChanged", (enabled) => {
      changed = true;
      if (active) opaque.set(enabled ? 1 : 0);
    });
    void AccessibilityInfo.isReduceTransparencyEnabled().then((enabled) => {
      // A setting changed while the query was in flight is the newer answer.
      if (active && !changed) opaque.set(enabled ? 1 : 0);
    }).catch(() => {
      // Keep glass if the platform cannot answer the initial query.
    });
    return () => {
      active = false;
      subscription.remove();
    };
  }, [opaque]);

  useEffect(() => {
    cancelAnimation(shimmer);
    shimmer.set(0);
    if (animated) {
      shimmer.set(withRepeat(withTiming(1, {
        duration: horizonMotion.shimmer,
        easing: Easing.linear,
      }), -1, false));
    }
    return () => cancelAnimation(shimmer);
  }, [animated, shimmer]);

  const { x, y, width: cardWidth } = card;
  // Never include displacement here: the planet belongs to the resting scene.
  const geometry = useMemo(
    () => horizonLightGeometry(width, { x, y, width: cardWidth }, PixelRatio.get(), height),
    [width, height, x, y, cardWidth],
  );
  const uniforms = useDerivedValue(() => {
    const crest = animated ? fx.crest.get() : 0;
    const press = fx.press.get();
    return {
      ...geometry,
      slab: [geometry.rest[0] + fx.shiftX.get(), geometry.rest[1] + fx.shiftY.get() - fx.lift.get() * horizonSize.lift, geometry.rest[2], geometry.rest[3]],
      ...horizonLightFrame(fx.progress.get(), fx.power.get(), fx.pulse.get(), crest, fx.done.get(), animated),
      slabScale: animated ? cardScale(crest, press) : 1 - 0.008 * press,
      closePress: fx.closePress.get(),
      shimmer: animated ? shimmer.get() : 0,
      opaque: opaque.get(),
    };
  });

  return (
    <Canvas pointerEvents="none" style={[styles.canvas, { width, height }]}>
      <Fill>
        <Shader source={EFFECT!} uniforms={uniforms} />
      </Fill>
    </Canvas>
  );
}

const styles = StyleSheet.create({
  canvas: { position: "absolute", top: 0, right: 0, bottom: 0, left: 0 },
});
