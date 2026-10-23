import { Image, StyleSheet, Text } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { BLADE_COLOR, BLADE_COPY, BLADE_FONT, BLADE_SPACING, headerMetrics } from './blade-theme';

export function BladeHeader({ width, topInset, opacity, ready }: {
  width: number; topInset: number; opacity: SharedValue<number>; ready: boolean;
}) {
  const metrics = headerMetrics(width, topInset);
  const fade = useAnimatedStyle(() => ({ opacity: opacity.get() }));
  return <Animated.View pointerEvents="none" style={[styles.root, { top: metrics.top }, fade]}
    accessibilityElementsHidden={!ready} importantForAccessibility={ready ? 'auto' : 'no-hide-descendants'}>
    <Image source={require('./assets/vane-mark.png')} style={styles.mark} accessibilityLabel="Vane" accessible />
    <Text allowFontScaling={false} style={styles.wordmark}>{BLADE_COPY.wordmark}</Text>
    <Text allowFontScaling={false} accessibilityRole="header" numberOfLines={2}
      style={[styles.headline, { fontSize: metrics.fontSize, lineHeight: metrics.lineHeight }]}>{BLADE_COPY.headline}</Text>
    <Text allowFontScaling={false} numberOfLines={1} style={styles.subtitle}>{BLADE_COPY.subtitle}</Text>
  </Animated.View>;
}

const styles = StyleSheet.create({
  root: { position: 'absolute', left: 0, right: 0, paddingHorizontal: BLADE_SPACING.horizontal, alignItems: 'center' },
  mark: { width: BLADE_SPACING.mark, height: BLADE_SPACING.mark },
  wordmark: { fontFamily: BLADE_FONT.regular, fontSize: 12, lineHeight: 16, letterSpacing: 0.4,
    color: BLADE_COLOR.wordmark, marginTop: BLADE_SPACING.wordmarkGap, includeFontPadding: false },
  headline: { fontFamily: BLADE_FONT.regular, letterSpacing: -1, color: BLADE_COLOR.ink,
    textAlign: 'center', marginTop: BLADE_SPACING.headlineGap, includeFontPadding: false },
  subtitle: { fontFamily: BLADE_FONT.regular, fontSize: 13, lineHeight: 18, color: BLADE_COLOR.subtitle,
    textAlign: 'center', marginTop: BLADE_SPACING.subtitleGap, includeFontPadding: false },
});
