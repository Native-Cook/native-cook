import { type ReactNode, useState } from 'react';
import { type StyleProp, StyleSheet, Text, type ViewStyle } from 'react-native';
import { Pressable } from 'react-native-gesture-handler';
import Animated, { cubicBezier } from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

export function TourButton({ children, onPress, label, subtle = false, disabled = false, reduced = false, style, color = '#EAF1FF', background = '#256BFF', onPressIn, onPressOut }: { children: ReactNode; onPress: () => void; label?: string; subtle?: boolean; disabled?: boolean; reduced?: boolean; style?: StyleProp<ViewStyle>; color?: string; background?: string; onPressIn?: () => void; onPressOut?: () => void }) {
  const [pressed, setPressed] = useState(false);
  return <Animated.View style={[{ minHeight: 48, minWidth: 48, flexShrink: 0, opacity: disabled ? .35 : 1, transform: [{ scale: pressed && !reduced ? .97 : 1 }], transitionProperty: 'transform', transitionDuration: reduced ? 0 : 120, transitionTimingFunction: cubicBezier(.23, 1, .32, 1) }, style]}>
    <Pressable accessibilityRole="button" accessibilityLabel={label} accessibilityState={{ disabled }} disabled={disabled} onPress={onPress} onPressIn={() => { setPressed(true); onPressIn?.(); }} onPressOut={() => { setPressed(false); onPressOut?.(); }} pressRetentionOffset={16} style={[styles.press, { backgroundColor: subtle ? 'transparent' : background }]}>
      {typeof children === 'string' ? <Text style={[styles.label, { color, fontSize: subtle ? 13 : 16, lineHeight: subtle ? 18 : 22 }]}>{children}</Text> : children}
    </Pressable>
  </Animated.View>;
}

const styles = StyleSheet.create({ press: { minHeight: 48, alignSelf: 'stretch', borderRadius: 18, paddingHorizontal: 15, paddingVertical: 14, alignItems: 'center', justifyContent: 'center' }, label: { flexShrink: 1, maxWidth: '100%', fontWeight: '600', textAlign: 'center' } });

/** A fixed-size navigation glyph; the enclosing button owns the accessible name. */
export function TourArrow({ direction, color = '#EAF1FF' }: { direction: 'previous' | 'next'; color?: string }) {
  return <Svg width={18} height={18} viewBox="0 0 18 18" accessible={false}><Path d={direction === 'previous' ? 'M11.5 3.5 6 9l5.5 5.5' : 'M6.5 3.5 12 9l-5.5 5.5'} fill="none" stroke={color} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" /></Svg>;
}
