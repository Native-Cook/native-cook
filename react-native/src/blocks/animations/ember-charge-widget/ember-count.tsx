import { useCallback, useEffect, useState } from "react";
import { StyleSheet } from "react-native";
import { type SharedValue, useAnimatedReaction } from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";
import { NumericText } from "expo-content-transition";
import { E, emberHeartTone, emberTone as T } from "./ember-theme";
import { face } from "./ui";

// Running values tick with animations disabled. A separate native view
// owns each landing: mount on the last tick, then roll to the endpoint.
// Never switch transition settings on a view with outgoing glyphs alive.

const STRIDE = 97;

export function EmberCount({ steps, goal, reduced }: { steps: SharedValue<number>; goal: number; reduced: boolean }) {
  const [shown, setShown] = useState({ value: E.restSteps as number, from: E.restSteps as number });
  const show = useCallback((value: number) => setShown((before) => ({ value, from: before.value })), []);
  useAnimatedReaction(
    () => {
      const v = steps.get();
      if (v >= goal - 0.5) return goal;
      if (v <= E.restSteps + 0.5) return E.restSteps;
      // Whole strides inside the ends; only the landing returns an endpoint.
      return Math.min(goal - 1, Math.max(E.restSteps + 1, Math.floor(v / STRIDE) * STRIDE));
    },
    (next, previous) => {
      if (next !== previous) scheduleOnRN(show, next);
    },
  );
  const landed = shown.value === E.restSteps || shown.value === goal;
  if (landed && !reduced) {
    return <LandingCount key={shown.value} from={shown.from} to={shown.value} />;
  }
  return <NumericText value={shown.value.toLocaleString("en-US")} animated={false} alignment="start" style={styles.count} />;
}

// A full line-height roll, with a small 15 % rebound. At 240 ms it has
// time to settle in the 300 ms landing check; blur stays below 1.5 pt.
function LandingCount({ from, to }: { from: number; to: number }) {
  const [value, setValue] = useState(from);
  useEffect(() => {
    // Two frames give the native mount its initial glyphs before the one
    // animated update. Cleanup also covers a press interrupting the roll.
    let frame = requestAnimationFrame(() => {
      frame = requestAnimationFrame(() => setValue(to));
    });
    return () => cancelAnimationFrame(frame);
  }, [to]);
  return (
    <NumericText
      value={value.toLocaleString("en-US")}
      duration={240}
      bounce={0.15}
      travel={1}
      maxBlurRadius={1.5}
      alignment="start"
      style={styles.count}
    />
  );
}

/** The heart rate in the badge: it rolls when the goal changes it. */
export function EmberBpm({ value, reduced }: { value: number; reduced: boolean }) {
  return (
    <NumericText
      value={value}
      animated={!reduced}
      duration={320}
      maxBlurRadius={1.5}
      bounce={0.2}
      alignment="center"
      monospacedDigits
      style={styles.bpm}
    />
  );
}

// NumericText uses explicit point sizes, without Dynamic Type scaling. Like
// the other instrument labels, its content is in the card's accessible label.
const styles = StyleSheet.create({
  count: {
    height: E.count.h,
    fontFamily: face.semibold,
    fontSize: E.count.size,
    // Proportional figures, tight: the narrow 1s are what keep the count compact.
    letterSpacing: -2.2,
    color: T.ink,
  },
  bpm: {
    width: E.heart.w,
    height: E.line.bpm,
    fontFamily: face.semibold,
    fontSize: 15,
    letterSpacing: -0.2,
    color: emberHeartTone.ink,
  },
});
