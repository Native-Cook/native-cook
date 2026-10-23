import { StyleSheet, Text, View, type TextStyle } from 'react-native';
import Animated from 'react-native-reanimated';

// Milliseconds for a digit to roll to its new value.
const ROLL = 260;
const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9'];

type Props = {
  /** The number as shown, for example "64.7"; digits roll, other characters are set. */
  text: string;
  /** Font settings of the number; `lineHeight` is the height of one digit's window. */
  style: TextStyle & { lineHeight: number };
  still: boolean;
};

/** A digit in a window one line tall: a column of 0–9 slides to show it. */
function Digit({ digit, style, still }: { digit: number; style: Props['style']; still: boolean }) {
  return (
    <View style={[styles.window, { height: style.lineHeight }]}>
      <Animated.View
        style={{
          transform: [{ translateY: -digit * style.lineHeight }],
          transitionProperty: 'transform',
          transitionDuration: still ? 0 : ROLL,
          transitionTimingFunction: 'ease-out',
        }}
      >
        {DIGITS.map((d) => (
          <Text key={d} style={style}>
            {d}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

/** A number whose digits roll like an odometer when it changes. */
export function Roller({ text, style, still }: Props) {
  const characters = text.split('');
  return (
    <View style={styles.row} accessible accessibilityLabel={text}>
      {characters.map((character, i) => {
        // Counted from the right, so units stay units when the number gets longer.
        const key = characters.length - i;
        const digit = DIGITS.indexOf(character);
        return digit < 0 ? (
          <Text key={key} style={style}>
            {character}
          </Text>
        ) : (
          <Digit key={key} digit={digit} style={style} still={still} />
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
  },
  window: {
    overflow: 'hidden',
  },
});
