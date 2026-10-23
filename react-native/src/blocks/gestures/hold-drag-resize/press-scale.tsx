/** Press feedback for the editor's buttons: 0.96 in 100 ms ease-out on press-in, back in 160 ms (opacity under Reduce Motion). */
import type { ReactNode } from 'react';
import { Pressable, type PressableProps, type StyleProp, type ViewStyle } from 'react-native';
import Animated, { Easing, useAnimatedStyle, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';

import { UPGRADE } from './motion';

// The Pressable itself is animated, so absolute positioning in `style` keeps its touch area.
const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

type Props = Omit<PressableProps, 'style' | 'children'> & { style?: StyleProp<ViewStyle>; children: ReactNode };

export function PressScale({ style, children, onPressIn, onPressOut, ...rest }: Props) {
  const reduce = useReducedMotion();
  const p = useSharedValue(0);
  const anim = useAnimatedStyle(() => (reduce
    ? { opacity: 1 - 0.3 * p.get() }
    : { transform: [{ scale: 1 - (1 - UPGRADE.buttonPress) * p.get() }] }));
  return (
    <AnimatedPressable
      {...rest}
      style={[style, anim]}
      onPressIn={(e) => { p.set(withTiming(1, { duration: UPGRADE.pressInMs, easing: Easing.out(Easing.cubic) })); onPressIn?.(e); }}
      onPressOut={(e) => { p.set(withTiming(0, { duration: UPGRADE.pressOutMs, easing: Easing.out(Easing.cubic) })); onPressOut?.(e); }}>
      {children}
    </AnimatedPressable>
  );
}
