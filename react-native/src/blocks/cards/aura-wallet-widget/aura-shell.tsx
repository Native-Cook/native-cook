import { useEffect, useMemo } from "react";
import { AccessibilityInfo, PixelRatio, Platform, StyleSheet } from "react-native";
import { Canvas, Fill, Shader, Skia } from "@shopify/react-native-skia";
import { useDerivedValue, useSharedValue } from "react-native-reanimated";
import type { AuraDrive } from "./aura-drive";
import { AURA_PAGE_SHADER, auraLightUniforms } from "./aura-light";
import { AURA_BLEED, type AuraSkin } from "./aura-palette";
import { AURA_SHELL_SHADER, auraMotionUniforms, auraShellUniforms } from "./aura-shader";

// Two canvases share one field of light. The page is a still; only the shell
// listens to the score and the four presses. Everything behind the views is one shader,
// so a pillow cannot lose its shadow or leave a seam as it gives under a finger.

const PAGE = Skia.RuntimeEffect.Make(AURA_PAGE_SHADER);
const SHELL = Skia.RuntimeEffect.Make(AURA_SHELL_SHADER);
if (!PAGE || !SHELL) throw new Error("Aura wallet: the material shaders did not compile.");

// Skia's own parser, called on Skia: handed over as a bare function it loses
// the object it belongs to and throws on the device.
const readColor = (color: string) => Skia.Color(color);

/** The light on the page behind the widget. Fills its parent. */
export function AuraPage({ skin, width, height, cardX, cardY, cardWidth }: { skin: AuraSkin; width: number; height: number; cardX: number; cardY: number; cardWidth: number }) {
  const uniforms = useMemo(() => ({
    ...auraLightUniforms(skin, readColor),
    origin: [cardX, cardY],
    cardWidth,
    pixelRatio: PixelRatio.get(),
  }), [skin, cardX, cardY, cardWidth]);
  return (
    <Canvas style={[StyleSheet.absoluteFill, { width, height }]} pointerEvents="none">
      <Fill><Shader source={PAGE!} uniforms={uniforms} /></Fill>
    </Canvas>
  );
}

/** The body and its shadow, reaching AURA_BLEED past the widget on every side. */
export function AuraShell({ width, skin, drive }: { width: number; skin: AuraSkin; drive: AuraDrive }) {
  const opaque = useSharedValue(0);
  useEffect(() => {
    if (Platform.OS !== "ios") return;
    let active = true;
    let changed = false;
    const subscription = AccessibilityInfo.addEventListener("reduceTransparencyChanged", (enabled) => {
      changed = true;
      opaque.set(enabled ? 1 : 0);
    });
    void AccessibilityInfo.isReduceTransparencyEnabled().then((enabled) => {
      // A setting changed while the query was in flight takes precedence.
      if (active && !changed) opaque.set(enabled ? 1 : 0);
    }).catch(() => {
      // With no answer, keep the normal glass surface.
    });
    return () => {
      active = false;
      subscription.remove();
    };
  }, [opaque]);

  const still = useMemo(() => auraShellUniforms(width, skin, PixelRatio.get(), readColor), [width, skin]);
  // Read one by one: a callback inside the worklet (a map over the array) is
  // lifted out by the React Compiler and is then no longer a worklet.
  const [settings, swap, receive, send] = drive.press;
  const uniforms = useDerivedValue(() => ({
    ...still,
    ...auraMotionUniforms(
      width, drive.phase.get(), drive.chosen.get(), drive.formed.get(),
      drive.leaving.get(), drive.unformed.get(), drive.pullX.get(), drive.pullY.get(),
      drive.clock.get(), drive.fromX.get(), drive.fromY.get(), drive.wide.get(), drive.nudge.get(),
      opaque.get(), settings.get(), swap.get(), receive.get(), send.get(),
      drive.weight.get(), drive.entered.get(),
    ),
  }));
  const side = width + AURA_BLEED * 2;
  return (
    <Canvas style={[styles.shell, { width: side, height: side }]} pointerEvents="none">
      <Fill><Shader source={SHELL!} uniforms={uniforms} /></Fill>
    </Canvas>
  );
}

const styles = StyleSheet.create({
  shell: { position: "absolute", left: -AURA_BLEED, top: -AURA_BLEED },
});
