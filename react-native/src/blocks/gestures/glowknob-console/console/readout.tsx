import { useCallback, useState } from "react";
import { StyleSheet } from "react-native";
import Animated, {
  useAnimatedReaction,
  useAnimatedStyle,
} from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";
import { NumericText } from "expo-content-transition";
import { tactile } from "./tactile-theme";
import { referenceGeometry as g } from "./reference-motion";
import { specimen } from "./device-art";
import { rollsOnStep } from "./motion-logic";
import { artText } from "./typography";
import type { TactileControl } from "./use-tactile-control";

const TOP = specimen.top;
// Readout box in drawing units: right-aligned, wide enough for "100%". Kept
// centered on the same line as before while the glyphs grew.
const READ = g.readout;

// The percentage reads as one color at a time: light glyphs on the empty
// well, dark glyphs once the light covers nearly all of them. The whole number
// swaps (140ms crossfade, with hysteresis), so a light edge never splits a
// digit into two colors.
export function Readout({
  control,
  scale,
  highContrast = false,
}: {
  control: TactileControl;
  scale: number;
  highContrast?: boolean;
}) {
  const { readout, numberRolls, numberQuick, numberAlpha, numberInk, reduced } =
    control;
  // NumericText animates natively per glyph; it only needs the integer, so the
  // UI thread hands over a value only when it changes. Discrete jumps (tap,
  // step, replay) roll at 240ms; under a dragging finger each step is a short,
  // low slide with no scale or blur so the digits stay legible. A channel swap
  // does not roll: it happens unseen under the fade.
  const [shown, setShown] = useState({ value: 52, roll: false, quick: false });
  const show = useCallback(
    (value: number, roll: boolean, quick: boolean) =>
      setShown({ value, roll, quick }),
    [],
  );
  useAnimatedReaction(
    () => Math.round(readout.get()),
    (next, previous) => {
      if (next === previous) return;
      const quick = numberQuick.get();
      scheduleOnRN(
        show,
        next,
        rollsOnStep(previous, next, quick, numberRolls.get()),
        quick,
      );
    },
  );
  const paperStyle = useAnimatedStyle(() => ({
    opacity: 1 - numberInk.get(),
  }));
  const inkStyle = useAnimatedStyle(() => ({ opacity: numberInk.get() }));
  const fade = useAnimatedStyle(() => ({ opacity: numberAlpha.get() }));
  const left = READ.x * scale,
    width = READ.width * scale,
    line = READ.line * scale;
  const number = (tint: string) => (
    <NumericText
      value={`${shown.value}%`}
      animated={shown.roll && !reduced}
      duration={shown.quick ? 110 : 240}
      blur={!shown.quick}
      maxBlurRadius={1.5}
      bounce={shown.quick ? 0 : 0.12}
      travel={shown.quick ? 0.28 : 0.4}
      enterScale={shown.quick ? 1 : 0.7}
      style={{
        ...StyleSheet.flatten(artText.number),
        fontWeight: highContrast ? "600" : "500",
        position: "absolute",
        top: 0,
        right: 0,
        width,
        height: line,
        fontSize: READ.size * scale,
        color: tint,
      }}
    />
  );
  return (
    <Animated.View
      testID="tactile-readout"
      style={[
        {
          position: "absolute",
          left,
          top: (TOP + READ.y) * scale,
          width,
          height: line,
        },
        fade,
      ]}
    >
      <Animated.View style={[StyleSheet.absoluteFill, paperStyle]}>
        {number(highContrast ? "#FFFFFF" : tactile.text)}
      </Animated.View>
      <Animated.View style={[StyleSheet.absoluteFill, inkStyle]}>
        {number(tactile.ink)}
      </Animated.View>
    </Animated.View>
  );
}
