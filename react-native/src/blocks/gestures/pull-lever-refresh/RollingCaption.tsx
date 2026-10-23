/** The caption morphs letter by letter: the old line rolls up and out, the new one rolls in from below. */
import { StyleSheet, View } from 'react-native';
import Animated, { FadeInDown, FadeOut, FadeOutUp, ReduceMotion } from 'react-native-reanimated';

import { TIMING } from './motion';
import { COLOR, TYPE } from './theme';

export function RollingCaption({ text, reduceMotion }: { text: string; reduceMotion: boolean }) {
  const chars = Array.from(text);
  return (
    <View style={styles.row} accessibilityLabel={text}>
      {chars.map((c, i) => {
        const delay = i * TIMING.charStagger;
        const enter = reduceMotion
          ? FadeInDown.duration(TIMING.reduced).withInitialValues({ transform: [{ translateY: 0 }] })
          : FadeInDown.duration(TIMING.charIn).delay(delay).springify().damping(16);
        const exit = reduceMotion ? FadeOut.duration(TIMING.reduced) : FadeOutUp.duration(TIMING.charOut).delay(i * (TIMING.charStagger / 2));
        return (
          <Animated.Text
            key={`${text}-${i}`}
            entering={enter.reduceMotion(ReduceMotion.Never)}
            exiting={exit.reduceMotion(ReduceMotion.Never)}
            style={styles.char}
          >
            {c === ' ' ? ' ' : c}
          </Animated.Text>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { position: 'absolute', right: 0, flexDirection: 'row' },
  char: { ...TYPE.caption, color: COLOR.mute },
});
