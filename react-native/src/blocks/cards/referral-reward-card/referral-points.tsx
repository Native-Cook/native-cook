import { useEffect, useRef, useState } from "react";
import { StyleSheet, View } from "react-native";
import Animated, {
  cancelAnimation,
  type SharedValue,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import { referralMotion as M, referralSize as S, referralTone } from "./referral-palette";
import { EASE_IN_OUT, gentle, systemFace } from "./ui";

// The points, as a counter of drums. Each column is a drum of the ten digits
// and turns by the shortest way up (or, when the points go down, back) to its
// next digit; a digit fades and flattens as it leaves the window, so nothing
// has to clip it. A column can be retargeted while it turns.

const COLUMNS = 3;
const DIGITS = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];

export function ReferralPoints({
  value,
  delay,
  bump,
  label,
  reduced,
}: {
  value: number;
  // How long after `value` changes the drums start, so the roll can wait for
  // the comet to land.
  delay: number;
  // 0 at rest; the counter swells with it as the points arrive.
  bump: SharedValue<number>;
  label: string;
  reduced: boolean;
}) {
  // Tabular digits share one advance; measured once from a hidden zero.
  const [advance, setAdvance] = useState(Math.ceil(S.pointsFont * 0.62 / 2) * 2);
  // The direction the last change went in, so the drums turn the same way.
  const [seen, setSeen] = useState({ value, rising: true });
  if (seen.value !== value) setSeen({ value, rising: value > seen.value });

  const text = String(value);
  const count = Math.min(text.length, COLUMNS);
  // Recentres the row when a leading digit appears or goes.
  const columns = useSharedValue(count);
  useEffect(() => {
    columns.set(
      reduced
        ? withDelay(delay, gentle(count))
        : withDelay(delay, withSpring(count, { duration: 460, dampingRatio: 0.85 })),
    );
  }, [count, delay, reduced, columns]);

  const row = useAnimatedStyle(() => ({
    transform: [
      { translateX: -((COLUMNS - columns.get()) * advance) / 2 },
      { scale: 1 + 0.1 * bump.get() },
    ],
  }));

  return (
    <View style={styles.box} accessible accessibilityRole="text" accessibilityLabel={label}>
      <Animated.View
        style={[styles.row, { width: advance * COLUMNS }, row]}
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
      >
        {Array.from({ length: COLUMNS }, (_, order) => {
          // order 0 is the units column, drawn rightmost.
          const char = text[text.length - 1 - order];
          return (
            <Drum
              key={order}
              digit={char === undefined ? null : Number(char)}
              rising={seen.rising}
              delay={delay + order * M.rollStagger}
              right={order * advance}
              width={advance}
              reduced={reduced}
            />
          );
        })}
      </Animated.View>
      <Animated.Text
        style={[styles.digit, styles.probe]}
        onLayout={(e) => setAdvance(Math.ceil(e.nativeEvent.layout.width / 2) * 2)}
        accessible={false}
      >
        0
      </Animated.Text>
    </View>
  );
}

function Drum({
  digit,
  rising,
  delay,
  right,
  width,
  reduced,
}: {
  digit: number | null;
  rising: boolean;
  delay: number;
  right: number;
  width: number;
  reduced: boolean;
}) {
  // How far the drum has turned, in digits; it only ever accumulates, so a
  // roll from 7 to 0 goes on through 8 and 9.
  const turn = useSharedValue(digit ?? 0);
  const shown = useSharedValue(digit === null ? 0 : 1);
  const target = useRef(digit ?? 0);
  const last = useRef(digit);

  useEffect(() => {
    if (last.current === digit) return;
    const from = last.current;
    last.current = digit;
    // A replacement press holds the drums where they are until its head lands.
    cancelAnimation(turn);
    cancelAnimation(shown);
    shown.set(withDelay(delay, gentle(digit === null ? 0 : 1, 240)));
    // A leading digit that goes keeps what it showed while it fades.
    if (digit === null) return;
    if (from === null) {
      // One that appears is already on its digit: it has no zero to roll from.
      target.current = digit;
      turn.set(digit);
      return;
    }
    target.current += rising ? (digit - from + 10) % 10 : -((from - digit + 10) % 10);
    turn.set(
      reduced
        ? withDelay(delay, gentle(target.current, 220))
        : withDelay(delay, withTiming(target.current, { duration: M.roll, easing: EASE_IN_OUT })),
    );
  }, [digit, rising, delay, reduced, turn, shown]);

  // A leading digit rises into place as it appears.
  const column = useAnimatedStyle(() => ({
    opacity: shown.get(),
    transform: [{ translateY: reduced ? 0 : (1 - shown.get()) * S.pointsLine * 0.4 }],
  }));

  return (
    <Animated.View style={[styles.column, { right, width }, column]}>
      {DIGITS.map((n) => (
        <Glyph key={n} n={n} turn={turn} reduced={reduced} />
      ))}
    </Animated.View>
  );
}

function Glyph({ n, turn, reduced }: { n: number; turn: SharedValue<number>; reduced: boolean }) {
  const style = useAnimatedStyle(() => {
    // Digits from the window: below it when positive, gone above when negative.
    const away = ((((n - turn.get() + 5) % 10) + 10) % 10) - 5;
    const far = Math.min(Math.abs(away), 1);
    // A digit is gone three quarters of a step from the window, so two
    // neighbors in mid-roll read as one blur rather than two numbers.
    const opacity = Math.pow(Math.max(1 - far / 0.75, 0), 1.4);
    return {
      opacity,
      // It also goes out of focus as it leaves, like the system's own numeric
      // transition; a digit that is not showing carries no filter.
      filter: `blur(${reduced || opacity <= 0 ? 0 : (5 * far).toFixed(2)}px)`,
      transform: reduced
        ? []
        : [{ translateY: away * S.pointsLine * 0.8 }, { scaleY: 1 - 0.3 * far }],
    };
  });
  return <Animated.Text style={[styles.digit, styles.glyph, style]}>{n}</Animated.Text>;
}

const styles = StyleSheet.create({
  box: { height: S.pointsLine, alignItems: "center", justifyContent: "center" },
  row: { height: S.pointsLine },
  column: { position: "absolute", top: 0, bottom: 0 },
  digit: {
    fontFamily: systemFace,
    fontSize: S.pointsFont,
    lineHeight: S.pointsLine,
    fontWeight: "700",
    letterSpacing: 0,
    fontVariant: ["tabular-nums"],
    color: referralTone.ink,
    textAlign: "center",
  },
  glyph: { position: "absolute", left: -8, right: -8, top: 0 },
  probe: { position: "absolute", opacity: 0 },
});
