import { useState, type ReactNode, type Ref } from 'react';
import { Pressable, type GestureResponderEvent, type PressableProps, type StyleProp, type View, type ViewStyle } from 'react-native';
import Animated, { cubicBezier } from 'react-native-reanimated';

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

type Props = Omit<PressableProps, 'style' | 'children'> & {
  children: ReactNode;
  /** Layout and surface of the control; the whole surface scales on press. */
  style?: StyleProp<ViewStyle>;
  /** Under Reduce Motion the press dims instead of scaling. */
  reduced?: boolean;
  /** 0.96 for buttons; larger surfaces can use a gentler 0.98. */
  scale?: number;
  ref?: Ref<View>;
};

/**
 * A button that answers the finger at once: it settles to 0.96 in 150 ms on a strong
 * ease-out and returns on the same curve, so a quick tap still reads as a press.
 * The transition retargets mid-flight; a disabled button never moves.
 */
export function PressScale({ children, style, reduced = false, scale = 0.96, disabled, onPressIn, onPressOut, ref, ...rest }: Props) {
  const [pressed, setPressed] = useState(false);
  const down = pressed && !disabled;
  return <AnimatedPressable
    {...rest}
    ref={ref}
    disabled={disabled}
    pressRetentionOffset={16}
    onPressIn={(event: GestureResponderEvent) => { setPressed(true); onPressIn?.(event); }}
    onPressOut={(event: GestureResponderEvent) => { setPressed(false); onPressOut?.(event); }}
    style={[style, {
      transform: [{ scale: down && !reduced ? scale : 1 }],
      ...(reduced ? { opacity: down ? 0.72 : 1 } : null),
      transitionProperty: reduced ? 'opacity' : 'transform',
      transitionDuration: 150,
      transitionTimingFunction: cubicBezier(0.23, 1, 0.32, 1),
    }]}
  >{children}</AnimatedPressable>;
}
