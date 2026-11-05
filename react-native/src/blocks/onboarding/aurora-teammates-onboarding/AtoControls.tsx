// Aurora Teammates Onboarding — the Next button's touch target. tgfx draws the pill (AtoStage); this is
// an invisible React Native Pressable laid exactly over it, so the button stays real for touch and
// VoiceOver. It rides with the pill and drives the press-in through a shared value.

import { Pressable, StyleSheet } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, withSpring } from 'react-native-reanimated';

import { GL } from './ato-motion';
import type { Layout } from './ato-theme';

type Props = { scene: SharedValue<number[]>; press: SharedValue<number>; L: Layout; onNext: () => void };

export function AtoNextHit({ scene, press, L, onNext }: Props) {
  const B = L.button;
  const place = useAnimatedStyle(() => ({ transform: [{ translateY: -scene.get()[GL.buttonBottom] }] }));
  return (
    <Animated.View style={[styles.wrap, { left: L.cx - B.w / 2, top: L.H - B.h, width: B.w, height: B.h }, place]}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Next"
        accessibilityHint="Shows the next onboarding page"
        onPressIn={() => press.set(withSpring(1, { duration: 120, dampingRatio: 1 }))}
        onPressOut={() => press.set(withSpring(0, { duration: 260, dampingRatio: 0.7 }))}
        onPress={onNext}
        style={[StyleSheet.absoluteFill, { borderRadius: B.h / 2 }]}
      />
    </Animated.View>
  );
}

const styles = StyleSheet.create({ wrap: { position: 'absolute' } });
