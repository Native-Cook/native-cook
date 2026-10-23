// Copied from charts/line-to-candles-switch/RollingNumber.tsx; adapted for the shot timer: fixed cells, kept leading zeros, no Dynamic Type.
// Copied again from gestures/pull-lever-refresh/RollingNumber.tsx for pinch-to-summarize: the roll's duration and curve
// are props (a slow 600 ms reading-time roll and a 520 ms price), and a thousands separator ("1,899").
import { useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, type TextStyle, View } from 'react-native';
import Animated, {
  Easing,
  type DerivedValue,
  type EasingFunction,
  type EasingFunctionFactory,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

// Each change of a digit rolls its reel this long; a reel rolls the short way round, 9 to 0 is one
// step. A digit that changes again sooner (a fast scrub) snaps instead, so it stays readable.
const ROLL = 150;
// The block's strong ease-out (motion.ts CURVE.out).
const ROLL_EASING = Easing.bezier(0.23, 1, 0.32, 1);
// Digits 0 to 9 and a second 0, so a reel can roll on from 9 to 0 and wrap without a visible jump.
const REEL = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];

type Font = Pick<TextStyle, 'fontSize' | 'fontWeight' | 'color' | 'letterSpacing'> & { lineHeight: number };

type DigitProps = {
  value: SharedValue<number> | DerivedValue<number>;
  /** 10 to the number of decimals, and to the place of this digit in the whole number. */
  scale: number;
  place: number;
  /** A leading digit: hidden while the number is smaller than its place. */
  leading: boolean;
  width: number;
  font: Font;
  still: boolean;
  roll: number;
  easing: EasingFunction | EasingFunctionFactory;
};

function Digit({ value, scale, place, leading, width, font, still, roll, easing }: DigitProps) {
  const digit = useDerivedValue(() => Math.floor(Math.round(Math.abs(value.get()) * scale) / place) % 10);
  const present = useDerivedValue(() => (!leading || Math.round(Math.abs(value.get()) * scale) >= place ? 1 : 0));
  // Where the reel is, unbounded: it only ever moves by the shortest step to the next digit.
  const goal = useSharedValue(-1);
  const reel = useSharedValue(0);
  const changed = useSharedValue(0);
  const shown = useSharedValue(1);

  useAnimatedReaction(
    () => digit.get(),
    (next) => {
      if (goal.get() < 0) {
        goal.set(next);
        reel.set(next);
        return;
      }
      const from = ((goal.get() % 10) + 10) % 10;
      const step = ((next - from + 15) % 10) - 5;
      if (step === 0) return;
      goal.set(goal.get() + step);
      const now = Date.now();
      const quick = now - changed.get() < roll;
      changed.set(now);
      reel.set(still || quick ? goal.get() : withTiming(goal.get(), { duration: roll, easing }));
    },
  );
  useAnimatedReaction(
    () => present.get(),
    (next) => shown.set(still ? next : withTiming(next, { duration: roll * 1.5, easing })),
  );

  const columnStyle = useAnimatedStyle(() => ({ width: width * shown.get(), opacity: shown.get() }));
  const reelStyle = useAnimatedStyle(() => {
    const at = ((reel.get() % 10) + 10) % 10;
    return { transform: [{ translateY: -at * font.lineHeight }] };
  });

  return (
    <Animated.View style={[{ height: font.lineHeight, overflow: 'hidden' }, columnStyle]}>
      <Animated.View style={reelStyle}>
        {REEL.map((label, index) => (
          <Text key={index} style={[font, styles.digit, { width }]} maxFontSizeMultiplier={1}>
            {label}
          </Text>
        ))}
      </Animated.View>
    </Animated.View>
  );
}

type Props = {
  value: SharedValue<number> | DerivedValue<number>;
  decimals: number;
  /** The most digits before the point; the ones beyond the first fold away while they are zero. */
  wholeDigits: number;
  font: Font;
  /** Reduced motion: digits change in place. */
  still: boolean;
  /** Read out instead of the reels, which a screen reader cannot follow. */
  accessibilityLabel?: string;
  /** A fixed width for every digit's cell and the gap between cells (the timer's drums); otherwise one tabular digit. */
  cell?: number;
  gap?: number;
  /** Keep leading zeros instead of folding them away (a timer reads 07). */
  padded?: boolean;
  /** How long one change of a digit rolls, and on which curve. */
  rollMs?: number;
  rollEasing?: EasingFunction | EasingFunctionFactory;
  /** Drawn before the thousands digit ("1,899"). */
  separator?: string;
};

/** A number whose every digit sits on a reel that rolls to its next value, like a meter. */
export function RollingNumber({
  value, decimals, wholeDigits, font, still, accessibilityLabel, cell, gap = 0, padded = false, rollMs = ROLL, rollEasing = ROLL_EASING, separator,
}: Props) {
  // The width of one tabular digit, measured once from a hidden zero.
  const [measured, setWidth] = useState(0);
  const measure = (event: LayoutChangeEvent) => setWidth(Math.ceil(event.nativeEvent.layout.width));
  const width = cell ?? measured;
  const scale = Math.pow(10, decimals);
  const places: number[] = [];
  for (let i = wholeDigits + decimals - 1; i >= 0; i--) places.push(Math.pow(10, i));

  return (
    <View style={[styles.row, { gap }]} accessible={accessibilityLabel !== undefined} accessibilityLabel={accessibilityLabel}>
      {cell === undefined && (
        <Text style={[font, styles.digit, styles.measure]} onLayout={measure} maxFontSizeMultiplier={1}>
          0
        </Text>
      )}
      {width > 0 &&
        places.map((place) => (
          <View key={place} style={styles.row}>
            {place === scale / 10 && <Text style={font} maxFontSizeMultiplier={1}>.</Text>}
            {separator !== undefined && place === scale * 100 && <Text style={font} maxFontSizeMultiplier={1}>{separator}</Text>}
            <Digit value={value} scale={scale} place={place} leading={!padded && place > scale} width={width} font={font} still={still}
              roll={rollMs} easing={rollEasing} />
          </View>
        ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
  },
  digit: {
    fontVariant: ['tabular-nums'],
    textAlign: 'center',
  },
  measure: {
    position: 'absolute',
    opacity: 0,
  },
});
