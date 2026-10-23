/**
 * A pressable that answers on press-in: scale 0.97 on the strong ease-out, 150 ms down and 100 ms back (a Reanimated
 * CSS transition, so a quick tap retargets instead of restarting). Under Reduce Motion it dims instead of scaling.
 */
import { useState, type ReactNode } from 'react';
import { Pressable, type GestureResponderEvent, type PressableProps, type StyleProp, type ViewStyle } from 'react-native';
import Animated, { cubicBezier, useAnimatedReaction, type SharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { CURVE, TIMING } from './motion';

const CSS_EASE_OUT = cubicBezier(CURVE.out[0], CURVE.out[1], CURVE.out[2], CURVE.out[3]);

type Props = Omit<PressableProps, 'style' | 'children'> & {
  // The visual (what scales); `containerStyle` sizes the touch area around it.
  style: StyleProp<ViewStyle>;
  containerStyle?: StyleProp<ViewStyle>;
  reduced: boolean;
  // When given: a second finger (a pinch starting here) lets the press go.
  fingers?: SharedValue<number>;
  children: ReactNode;
};

/** Clears `setPressed` the moment a second finger lands (the press is a pinch). */
export function useReleaseOnPinch(fingers: SharedValue<number> | undefined, setPressed: (v: boolean) => void) {
  useAnimatedReaction(() => (fingers ? fingers.get() > 1 : false), (multi, was) => {
    if (multi && !was) scheduleOnRN(setPressed, false);
  });
}

export function PressScale({ style, containerStyle, reduced, fingers, children, onPressIn, onPressOut, ...pressable }: Props) {
  const [pressed, setPressed] = useState(false);
  useReleaseOnPinch(fingers, setPressed);
  const pressIn = (e: GestureResponderEvent) => { setPressed(!fingers || fingers.get() < 2); onPressIn?.(e); };
  const pressOut = (e: GestureResponderEvent) => { setPressed(false); onPressOut?.(e); };
  return <Pressable pressRetentionOffset={16} {...pressable} style={containerStyle} onPressIn={pressIn} onPressOut={pressOut}>
    <Animated.View style={[style, {
      opacity: pressed && reduced ? TIMING.pressDim : 1,
      transform: [{ scale: pressed && !reduced ? TIMING.pressScale : 1 }],
      transitionProperty: ['transform', 'opacity'],
      transitionDuration: pressed ? TIMING.pressInMs : TIMING.pressOutMs,
      transitionTimingFunction: CSS_EASE_OUT,
    }]}>{children}</Animated.View>
  </Pressable>;
}
