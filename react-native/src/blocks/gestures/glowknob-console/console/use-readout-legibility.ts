import {
  type SharedValue,
  useAnimatedReaction,
  useDerivedValue,
  useSharedValue,
  withTiming,
} from "react-native-reanimated";
import { motion } from "../theme";
import { referenceGeometry as g } from "./reference-motion";
import { glyphRun, inkTarget, runCoverage } from "./motion-logic";

// The percentage reads as one color at a time: light glyphs on the empty
// well, dark glyphs once the light covers more than half of the glyph run
// (back to light below 40%). The whole number swaps with a 140ms crossfade, so
// a light edge never splits a digit into two colors.
export function useReadoutLegibility(
  readout: SharedValue<number>,
  fill: SharedValue<number>,
  reduced: boolean,
) {
  const numberRun = useDerivedValue(() =>
    glyphRun(Math.round(readout.get()), g.readout.size),
  );
  const coverage = useDerivedValue(() =>
    runCoverage(
      g.track.x + g.track.width * fill.get(),
      g.readout.x + g.readout.width,
      numberRun.get(),
    ),
  );
  const numberInk = useSharedValue(0);
  useAnimatedReaction(
    () => coverage.get(),
    (c, previous) => {
      const dark = numberInk.get() >= 0.5;
      const target = inkTarget(c, dark);
      if (previous === null) numberInk.set(target);
      else if ((target === 1) !== dark)
        numberInk.set(
          withTiming(target, {
            duration: reduced ? 0 : 140,
            easing: motion.easeOut,
          }),
        );
    },
  );
  return { numberRun, numberInk };
}
