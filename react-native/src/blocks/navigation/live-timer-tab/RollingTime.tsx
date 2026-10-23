import { StyleSheet, type TextStyle, View } from 'react-native';
import Animated, { Easing, withTiming } from 'react-native-reanimated';

import { EASE_OUT, ROLL, ROLL_SHIFT } from './motion';

// A digit's cell and a colon's, as fractions of the font size: fixed, so the time never changes width.
const DIGIT = 0.62;
const COLON = 0.3;
const LINE = 1.3;

const roll = { duration: ROLL * 1000, easing: Easing.bezier(...EASE_OUT) };

/** A new character drops in from above… */
function rollIn() {
  'worklet';
  return {
    initialValues: { opacity: 0, transform: [{ translateY: -ROLL_SHIFT }] },
    animations: { opacity: withTiming(1, roll), transform: [{ translateY: withTiming(0, roll) }] },
  };
}

/** …while the one it replaces falls away. */
function rollOut() {
  'worklet';
  return {
    initialValues: { opacity: 1, transform: [{ translateY: 0 }] },
    animations: { opacity: withTiming(0, roll), transform: [{ translateY: withTiming(ROLL_SHIFT, roll) }] },
  };
}

type Props = {
  /** The time as text, "14:59". */
  text: string;
  size: number;
  color: string;
  weight: TextStyle['fontWeight'];
};

/**
 * A time whose characters sit in cells of fixed width; a character that changes rolls: the old one
 * falls and fades while the new one drops in. Reanimated leaves the roll out under reduced motion.
 */
export function RollingTime({ text, size, color, weight }: Props) {
  const height = Math.round(size * LINE);
  return (
    <View style={styles.row} accessible accessibilityLabel={text}>
      {text.split('').map((character, index) => (
        <View key={index} style={{ width: size * (character === ':' ? COLON : DIGIT), height }}>
          <Animated.Text
            key={character}
            entering={rollIn}
            exiting={rollOut}
            style={[styles.character, { color, fontSize: size, lineHeight: height, fontWeight: weight }]}
          >
            {character}
          </Animated.Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
  },
  character: {
    position: 'absolute',
    left: -4,
    right: -4,
    top: 0,
    textAlign: 'center',
    fontVariant: ['tabular-nums'],
    includeFontPadding: false,
  },
});
