/** A balance that rolls to its new value (an animated TextInput, so no React render per frame). */
import { StyleSheet, TextInput, type TextStyle } from 'react-native';
import Animated, { useAnimatedProps, useDerivedValue, withTiming, type SharedValue } from 'react-native-reanimated';

import { EASE_OUT, TIMING } from './motion';

const AnimatedInput = Animated.createAnimatedComponent(TextInput);

export function RollingAmount({ value, style, reduceMotion }: { value: SharedValue<number>; style: TextStyle; reduceMotion: boolean }) {
  const shown = useDerivedValue(() => (reduceMotion ? value.get() : withTiming(value.get(), { duration: TIMING.roll, easing: EASE_OUT })));
  const props = useAnimatedProps(() => {
    const text = (Math.round(shown.get() * 10) / 10).toFixed(1).replace(/\.0$/, '');
    return { text, defaultValue: text } as never;
  });
  return <AnimatedInput editable={false} underlineColorAndroid="transparent" style={[styles.input, style]} animatedProps={props} />;
}

const styles = StyleSheet.create({ input: { padding: 0, margin: 0 } });
