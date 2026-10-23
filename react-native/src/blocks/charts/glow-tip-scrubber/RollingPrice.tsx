import { useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, type TextStyle, View } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

// Each change of a digit rolls its reel this long, the short way round: 9 to 0 is one step. A
// digit that changes again sooner (a fast scrub) snaps instead, so it stays readable.
const ROLL = 140;
const ROLL_EASING = Easing.out(Easing.cubic);
// Digits 0 to 9 and a second 0, so a reel can roll on from 9 to 0 and wrap without a visible jump.
const REEL = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];

type Font = Pick<TextStyle, 'fontSize' | 'fontWeight' | 'color' | 'letterSpacing'> & { lineHeight: number };

function Digit({ value, scale, place, width, font, still }: { value: SharedValue<number>; scale: number; place: number; width: number; font: Font; still: boolean }) {
  const digit = useDerivedValue(() => Math.floor(Math.round(Math.abs(value.get()) * scale) / place) % 10);
  // Where the reel is, unbounded: it only ever moves by the shortest step to the next digit.
  const goal = useSharedValue(-1);
  const reel = useSharedValue(0);
  const changed = useSharedValue(0);

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
      const quick = now - changed.get() < ROLL;
      changed.set(now);
      reel.set(still || quick ? goal.get() : withTiming(goal.get(), { duration: ROLL, easing: ROLL_EASING }));
    },
  );

  const reelStyle = useAnimatedStyle(() => {
    const at = ((reel.get() % 10) + 10) % 10;
    return { transform: [{ translateY: -at * font.lineHeight }] };
  });

  return (
    <View style={{ width, height: font.lineHeight, overflow: 'hidden' }}>
      <Animated.View style={reelStyle}>
        {REEL.map((label, index) => (
          <Text key={index} style={[font, styles.digit, { width }]}>
            {label}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

type Props = {
  value: SharedValue<number>;
  /** The shape of the number: each 0 is a digit on a reel, anything else is printed, e.g. "£00,000.00". */
  pattern: string;
  font: Font;
  /** Reduced motion: digits change in place. */
  still: boolean;
};

/** A price whose every digit sits on a reel that rolls to its next value, like a meter. */
export function RollingPrice({ value, pattern, font, still }: Props) {
  // The width of one tabular digit, measured once from a hidden zero.
  const [width, setWidth] = useState(0);
  const measure = (event: LayoutChangeEvent) => setWidth(Math.ceil(event.nativeEvent.layout.width));
  const point = pattern.indexOf('.');
  const decimals = point < 0 ? 0 : pattern.length - point - 1;
  const scale = Math.pow(10, decimals);
  const digits = pattern.replace(/[^0]/g, '').length;

  let seen = 0;
  const parts = pattern.split('').map((char, index) => {
    if (char !== '0') {
      return (
        <Text key={index} style={font}>
          {char}
        </Text>
      );
    }
    seen += 1;
    const place = Math.pow(10, digits - seen);
    return <Digit key={index} value={value} scale={scale} place={place} width={width} font={font} still={still} />;
  });

  return (
    <View style={styles.row}>
      <Text style={[font, styles.digit, styles.measure]} onLayout={measure}>
        0
      </Text>
      {width > 0 && parts}
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
