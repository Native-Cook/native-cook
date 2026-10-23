/** Rolling text. The caption rolls whole lines: the old line slides up and fades, the new one rises from below into
 * the same slot, so two captions of different widths never leave stray fragments. The title takes the long roll; the
 * sub line, which steps through the pressure, a small quick one; while the shot pours it ticks in place. (The shot
 * timer's reels are RollingNumber.tsx.) */
import { StyleSheet, View, type StyleProp, type TextStyle } from 'react-native';
import Animated, {
  Easing,
  FadeIn,
  FadeOut,
  ReduceMotion,
  withDelay,
  withTiming,
} from 'react-native-reanimated';

import { TIMING } from './motion';
import { TYPE } from './theme';

// The roll: a short rise with the system's ease-out; the new line starts once the old one is mostly gone.
const RISE = 8;
const OUT_MS = 160;
const IN_MS = 260;
const IN_DELAY = 90;
// The small roll: a shorter rise; out in 110 ms, in over 160 ms from 80 ms.
const SMALL_RISE = 6;
const EASE = Easing.bezier(0.23, 1, 0.32, 1);

function rollOut(rise: number, ms: number) {
  return () => {
    'worklet';
    return {
      initialValues: { opacity: 1, transform: [{ translateY: 0 }] },
      animations: {
        opacity: withTiming(0, { duration: ms, easing: EASE }),
        transform: [{ translateY: withTiming(-rise, { duration: ms, easing: EASE }) }],
      },
    };
  };
}

function rollIn(rise: number, ms: number, delay: number) {
  return () => {
    'worklet';
    return {
      initialValues: { opacity: 0, transform: [{ translateY: rise }] },
      animations: {
        opacity: withDelay(delay, withTiming(1, { duration: ms, easing: EASE })),
        transform: [{ translateY: withDelay(delay, withTiming(0, { duration: ms, easing: EASE })) }],
      },
    };
  };
}

const ROLL = {
  line: { out: rollOut(RISE, OUT_MS), in: rollIn(RISE, IN_MS, IN_DELAY) },
  // The old line is mostly gone before the new one rises, so two lines never share a frame at full strength.
  small: { out: rollOut(SMALL_RISE, 110), in: rollIn(SMALL_RISE, TIMING.subRoll, 80) },
} as const;

const calmIn = FadeIn.duration(TIMING.reduced).reduceMotion(ReduceMotion.Never);
const calmOut = FadeOut.duration(TIMING.reduced).reduceMotion(ReduceMotion.Never);

type Props = {
  text: string;
  reduceMotion: boolean;
  style: StyleProp<TextStyle>;
  align?: 'left' | 'right' | 'center';
  roll?: keyof typeof ROLL;
  /** Lines that share a key replace each other in place, without a roll (a running count). */
  rollKey?: string;
};

export function RollingCaption({ text, reduceMotion, style, align = 'left', roll = 'line', rollKey }: Props) {
  return (
    <View style={styles.slot} pointerEvents="none">
      <Animated.Text
        key={rollKey ?? text}
        entering={reduceMotion ? calmIn : ROLL[roll].in}
        exiting={reduceMotion ? calmOut : ROLL[roll].out}
        style={[style, styles.line, { textAlign: align }]}
        numberOfLines={1}
        maxFontSizeMultiplier={TYPE.maxScale}
      >
        {text}
      </Animated.Text>
    </View>
  );
}

const styles = StyleSheet.create({
  // Both lines share one anchor, so the old and the new line overlap while they roll.
  slot: { ...StyleSheet.absoluteFill, overflow: 'hidden' },
  line: { position: 'absolute', left: 0, right: 0, top: 0 },
});
