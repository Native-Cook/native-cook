import { useEffect } from 'react';
import { StyleSheet, Text, View, type TextStyle } from 'react-native';
import Animated, { Easing, ReduceMotion, useAnimatedStyle, useSharedValue, withDelay, withTiming } from 'react-native-reanimated';

const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9'];
// Each digit rolls for ROLL plus SLOWER per place to its right, after waiting LATER per place:
// the last digits settle last.
const ROLL = 420;
const SLOWER = 40;
const LATER = 55;
const SETTLE = Easing.bezier(0.2, 0.8, 0.2, 1);

type Props = {
  /** The number as shown, e.g. "$29,924.95"; digits roll, other characters fade in. */
  text: string;
  style: TextStyle & { lineHeight: number };
  /** Milliseconds before the first digit moves. */
  delay?: number;
  still: boolean;
};

function Digit({ digit, place, style, delay, still }: { digit: number; place: number; style: Props['style']; delay: number; still: boolean }) {
  // A new digit starts at 0, unseen, and shows as it rolls up to its value.
  const at = useSharedValue(still ? digit : 0);
  const seen = useSharedValue(still ? 1 : 0);
  useEffect(() => {
    seen.set(still ? 1 : withDelay(delay + LATER * place, withTiming(1, { duration: ROLL / 2, reduceMotion: ReduceMotion.Never })));
  }, [seen, delay, place, still]);
  useEffect(() => {
    at.set(
      still
        ? digit
        : withDelay(
            delay + LATER * place,
            withTiming(digit, { duration: ROLL + SLOWER * place, easing: SETTLE, reduceMotion: ReduceMotion.Never }),
          ),
    );
  }, [at, digit, delay, place, still]);
  const column = useAnimatedStyle(() => ({ opacity: seen.get(), transform: [{ translateY: -at.get() * style.lineHeight }] }));
  return (
    <View style={[styles.window, { height: style.lineHeight }]}>
      <Animated.View style={column}>
        {DIGITS.map((d) => (
          <Text key={d} style={style}>
            {d}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

function Mark({ character, place, style, delay, still }: { character: string; place: number; style: Props['style']; delay: number; still: boolean }) {
  const shown = useSharedValue(still ? 1 : 0);
  useEffect(() => {
    shown.set(still ? 1 : withDelay(delay + LATER * place, withTiming(1, { duration: ROLL, reduceMotion: ReduceMotion.Never })));
  }, [shown, delay, place, still]);
  const fade = useAnimatedStyle(() => ({ opacity: shown.get() }));
  return <Animated.Text style={[style, fade]}>{character}</Animated.Text>;
}

/** A figure that rolls in from zeros when it appears and rolls to every new value, digit by digit. */
export function Odometer({ text, style, delay = 0, still }: Props) {
  const characters = text.split('');
  return (
    <View style={styles.row} accessible accessibilityLabel={text}>
      {characters.map((character, i) => {
        const key = characters.length - i;
        const digit = DIGITS.indexOf(character);
        return digit < 0 ? (
          <Mark key={key} character={character} place={i} style={style} delay={delay} still={still} />
        ) : (
          <Digit key={key} digit={digit} place={i} style={style} delay={delay} still={still} />
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
