import { useEffect } from 'react';
import { StyleSheet } from 'react-native';
import Animated, { ReduceMotion, useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';

export function FocusScrim({ active }: { active: boolean }) {
  const opacity = useSharedValue(active ? 0.06 : 0);
  const animatedStyle = useAnimatedStyle(() => ({
    opacity: opacity.get(),
  }));

  useEffect(() => {
    opacity.set(
      withTiming(active ? 0.06 : 0, {
        duration: 240,
        reduceMotion: ReduceMotion.System,
      }),
    );
  }, [active, opacity]);

  return (
    <Animated.View
      pointerEvents="none"
      style={[StyleSheet.absoluteFill, styles.focusScrim, animatedStyle]}
    />
  );
}

const styles = StyleSheet.create({
  focusScrim: {
    backgroundColor: '#000000',
  },
});
