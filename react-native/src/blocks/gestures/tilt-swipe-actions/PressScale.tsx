/**
 * Press feedback within a frame (review-animations: 100–160 ms, asymmetric, strong ease-out):
 * the target scales down on touch-down and springs back on release. Transform only, on the UI thread.
 */
import type { ReactNode } from 'react';
import { Pressable, type PressableProps, type StyleProp, type ViewStyle } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';

import { EASE_OUT, TIMING } from './motion';

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

type Props = Omit<PressableProps, 'style' | 'children'> & { style?: StyleProp<ViewStyle>; scale?: number; reduceMotion?: boolean; children: ReactNode };

export function PressScale({ style, scale = TIMING.pressScale, reduceMotion = false, children, onPressIn, onPressOut, ...rest }: Props) {
  const down = useSharedValue(0);
  const anim = useAnimatedStyle(() => ({ transform: [{ scale: 1 - (1 - scale) * down.get() }] }));
  return (
    <AnimatedPressable
      {...rest}
      onPressIn={(e) => {
        if (!reduceMotion) down.set(withTiming(1, { duration: TIMING.pressIn, easing: EASE_OUT }));
        onPressIn?.(e);
      }}
      onPressOut={(e) => {
        down.set(withTiming(0, { duration: TIMING.pressOut, easing: EASE_OUT }));
        onPressOut?.(e);
      }}
      style={[style, anim]}
    >
      {children}
    </AnimatedPressable>
  );
}
