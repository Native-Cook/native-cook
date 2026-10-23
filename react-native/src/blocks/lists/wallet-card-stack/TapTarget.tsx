import { type ReactNode, useMemo } from 'react';
import type { AccessibilityState, StyleProp, ViewStyle } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { EASE_OUT, PRESS } from './motion';

/** A button driven by a native tap gesture, with a quick press-in and a springy release. */
export function TapTarget({ onTap, label, state, style, depth = 1 - PRESS.scale, children }: {
  onTap: () => void; label: string; state?: AccessibilityState; style?: StyleProp<ViewStyle>; depth?: number; children: ReactNode;
}) {
  const pressed = useSharedValue(0);
  const gesture = useMemo(() => Gesture.Tap()
    .maxDuration(800)
    .hitSlop(8)
    .onBegin(() => {
      pressed.set(withTiming(1, { duration: PRESS.in, easing: EASE_OUT }));
    })
    .onFinalize(() => {
      pressed.set(withTiming(0, { duration: PRESS.in, easing: EASE_OUT }));
    })
    .onEnd(() => {
      scheduleOnRN(onTap);
    }), [onTap, pressed]);
  const anim = useAnimatedStyle(() => ({ transform: [{ scale: 1 - depth * pressed.get() }] }));
  return (
    <GestureDetector gesture={gesture}>
      <Animated.View accessible accessibilityRole="button" accessibilityLabel={label} accessibilityState={state}
        onAccessibilityTap={onTap} style={[style, anim]}>
        {children}
      </Animated.View>
    </GestureDetector>
  );
}
