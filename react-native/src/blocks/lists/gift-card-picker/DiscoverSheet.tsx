import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { Orb } from './Orb';
import { PRESS } from './motion';
import { BUTTON, COLORS, DISCOVER, TOUCH } from './theme';

// The light step of the sheet: discover the amount, wait, then go on to the stocks.
type Props = {
  width: number;
  height: number; // sheet height
  ky: number;
  stage: SharedValue<number>;
  time: SharedValue<number>;
  press: SharedValue<number>;
  reduceMotion: boolean;
  onDiscover: () => void;
  onChoose: () => void;
  onPressIn: () => void;
  onPressOut: () => void;
};

export function DiscoverSheet({ width, height, ky, stage, time, press, reduceMotion, onDiscover, onChoose, onPressIn, onPressOut }: Props) {
  // Swaps in the pin are a single frame; 70 ms of stage keeps them crisp without popping.
  const first = useAnimatedStyle(() => ({ opacity: 1 - Math.min(1, Math.max(0, stage.get() / 0.1)) }));
  const caption = useAnimatedStyle(() => {
    const v = stage.get();
    const inn = Math.min(1, Math.max(0, v / 0.1));
    const out = 1 - Math.min(1, Math.max(0, (v - 1) / 0.05));
    return { opacity: Math.min(inn, out) };
  });
  const second = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.max(0, (stage.get() - 1) / 0.05)) }));
  const btnStyle = useAnimatedStyle(() => {
    const v = stage.get();
    const shown = Math.max(1 - Math.min(1, v / 0.1), Math.min(1, Math.max(0, (v - 1) / 0.05)));
    return { opacity: shown, transform: [{ scale: 1 - (1 - PRESS.scale) * press.get() }] };
  });

  const btnTop = height - BUTTON.bottom - BUTTON.height;
  return (
    <View style={[StyleSheet.absoluteFill, { width, height }]}>
      <Orb width={width} height={height} ky={ky} stage={stage} time={time} reduceMotion={reduceMotion} />

      <Animated.View style={[styles.terms, { bottom: DISCOVER.termsBottom - 10 }, first]} pointerEvents="none">
        <Text style={styles.termsText}>Terms and Conditions</Text>
      </Animated.View>

      <Animated.View style={[styles.caption, { bottom: DISCOVER.captionBottom - 11 }, caption]} pointerEvents="none">
        <Text style={styles.captionText} accessibilityLiveRegion="polite">
          Discovering your gift share value...
        </Text>
      </Animated.View>

      <Animated.View style={[styles.button, { top: btnTop }, btnStyle]}>
        <Pressable pressRetentionOffset={TOUCH.retention}
          style={styles.hit}
          onPress={() => (stage.get() < 0.5 ? onDiscover() : stage.get() > 1.9 ? onChoose() : undefined)}
          onPressIn={onPressIn}
          onPressOut={onPressOut}
          accessibilityRole="button"
          accessibilityLabel="Discover amount, then choose a gift stock"
        >
          <Animated.Text style={[styles.buttonLabel, first]}>Discover amount</Animated.Text>
          <Animated.Text style={[styles.buttonLabel, styles.stacked, second]}>Choose a gift stock</Animated.Text>
        </Pressable>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  terms: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  termsText: { fontSize: 13, lineHeight: 20, color: COLORS.terms, textDecorationLine: 'underline' },
  caption: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  captionText: { fontSize: 17, lineHeight: 22, color: COLORS.caption },
  button: {
    position: 'absolute',
    left: BUTTON.side,
    right: BUTTON.side,
    height: BUTTON.height,
    borderRadius: BUTTON.radius,
    borderCurve: 'continuous',
    backgroundColor: COLORS.orange,
  },
  hit: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  buttonLabel: { fontSize: BUTTON.font, fontWeight: '500', color: COLORS.white },
  stacked: { position: 'absolute' },
});
