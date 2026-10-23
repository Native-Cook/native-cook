import { StyleSheet, View } from 'react-native';
import { GestureDetector, type TapGesture } from 'react-native-gesture-handler';
import Animated, { interpolateColor, type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import type { BladeMotion, FanLayout } from './blade-contract';
import { BLADE_COLOR, BLADE_COPY, BLADE_FONT } from './blade-theme';

export function BladeCta({ layout, motion, opacity, gesture, drawing, ready, onAccessibilityPress }: {
  layout: FanLayout; motion: BladeMotion; opacity: SharedValue<number>; gesture: TapGesture;
  drawing: boolean; ready: boolean; onAccessibilityPress: () => void;
}) {
  const { press, ctaEnergy } = motion;
  const labelStyle = useAnimatedStyle(() => ({
    opacity: opacity.get(),
    transform: [{ scale: 1 - 0.03 * press.get() }],
    color: interpolateColor(ctaEnergy.get(), [0.25, 0.6], [BLADE_COLOR.labelDark, BLADE_COLOR.labelLit]),
  }));
  return <GestureDetector gesture={gesture}>
    <View collapsable={false} accessible={ready} accessibilityRole="button" accessibilityLabel={BLADE_COPY.cta}
      accessibilityState={{ busy: drawing, disabled: !ready }} onAccessibilityTap={onAccessibilityPress}
      accessibilityActions={[{ name: 'activate', label: BLADE_COPY.cta }]}
      onAccessibilityAction={(event) => { if (event.nativeEvent.actionName === 'activate') onAccessibilityPress(); }}
      importantForAccessibility={ready ? 'auto' : 'no-hide-descendants'}
      style={[styles.target, { left: layout.ctaX, top: layout.ctaY, width: layout.ctaW, height: layout.ctaH }]}>
      <Animated.Text allowFontScaling={false} accessible={false} style={[styles.label, labelStyle]}>{BLADE_COPY.cta}</Animated.Text>
    </View>
  </GestureDetector>;
}

const styles = StyleSheet.create({
  target: { position: 'absolute', alignItems: 'center', justifyContent: 'center' },
  // Full width: a label measured before its font arrives would keep the narrower fallback width and clip.
  label: { alignSelf: 'stretch', textAlign: 'center', fontFamily: BLADE_FONT.bold, fontSize: 16, lineHeight: 20, letterSpacing: -0.2, includeFontPadding: false },
});
