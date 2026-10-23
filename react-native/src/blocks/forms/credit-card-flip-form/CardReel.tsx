import { useEffect } from 'react';
import { StyleSheet, Text, View, type TextStyle } from 'react-native';
import Animated, { Easing, useAnimatedStyle, useSharedValue, withDelay, withSequence, withTiming } from 'react-native-reanimated';

// Each character on the card sits on a reel like a slot machine's: the placeholder, then the digits
// 0–9 twice. A typed digit spins the reel down through the first run of digits to its place in the
// second (11 + digit), so even a 0 rolls a full turn; deleting it spins the reel back up to the
// placeholder. The spin takes SPIN_MS on a curve that overshoots by a hair and settles, like a reel
// catching on its detent.
const DIGITS = '0123456789';
const SPIN_MS = 560;
const BACK_MS = 420;
const SPIN = Easing.bezier(0.16, 0.9, 0.3, 1.06);
// Glyphs blur into each other while they pass: the reel dims to MOVING_ALPHA at full speed.
const MOVING_ALPHA = 0.55;

function stop(char: string) {
  'worklet';
  const digit = char.length === 1 ? DIGITS.indexOf(char) : -1;
  return digit < 0 ? 0 : 11 + digit;
}

type Props = {
  /** The digit shown, or '' for the placeholder. */
  char: string;
  placeholder: string;
  width: number;
  /** One glyph's line height: the reel's window is this tall. */
  lineHeight: number;
  textStyle: TextStyle;
  /** Delay before the reel turns (ms), so a row of reels clears in a ripple. */
  delay?: number;
  still?: boolean;
};

/** One character of the card number, the expiry or the CVV. */
export function CardReel({ char, placeholder, width, lineHeight, textStyle, delay = 0, still = false }: Props) {
  const roll = useSharedValue(stop(char));
  const speed = useSharedValue(0);

  useEffect(() => {
    const target = stop(char);
    if (still) {
      roll.set(target);
      return;
    }
    if (target === roll.get()) return;
    const back = target < roll.get();
    roll.set(withDelay(back ? delay : 0, withTiming(target, { duration: back ? BACK_MS : SPIN_MS, easing: SPIN })));
    speed.set(
      withDelay(
        back ? delay : 0,
        withSequence(
          withTiming(1, { duration: 40 }),
          withTiming(0, { duration: (back ? BACK_MS : SPIN_MS) * 0.8, easing: Easing.out(Easing.quad) }),
        ),
      ),
    );
  }, [char, delay, still, roll, speed]);

  const reelStyle = useAnimatedStyle(() => ({
    opacity: 1 - (1 - MOVING_ALPHA) * speed.get(),
    transform: [{ translateY: -roll.get() * lineHeight }],
  }));

  const glyphs = [placeholder, ...DIGITS, ...DIGITS].join('\n');
  return (
    <View style={[styles.window, { width, height: lineHeight }]}>
      <Animated.View style={[styles.reel, reelStyle]}>
        <Text allowFontScaling={false} style={[textStyle, styles.glyphs, { width, lineHeight }]}>
          {glyphs}
        </Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  window: {
    overflow: 'hidden',
  },
  reel: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  glyphs: {
    textAlign: 'center',
    fontVariant: ['tabular-nums'],
  },
});
