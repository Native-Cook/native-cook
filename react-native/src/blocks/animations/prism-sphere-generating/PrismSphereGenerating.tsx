import { Canvas } from '@shopify/react-native-skia';
import { StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useReducedMotion } from 'react-native-reanimated';

import { PrismCaption } from './PrismCaption';
import { PrismMotes } from './PrismMotes';
import { PrismBackdrop, PrismSphere } from './PrismScene';
import { PRISM, prismLayout } from './prism-theme';
import { usePrismMotion } from './use-prism-motion';

export function PrismSphereGenerating({ autoPlay = false }: { autoPlay?: boolean }) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const layout = prismLayout(width, height);
  const { k, cx, cy, radius, touchRadius } = layout;
  const { clock, sceneClock, moteClock, rotation, fan, charge, scale, flash, ring, energy, sweep, sweepPower, light,
    burstAge, burstGain, canvasOpacity, headlineOpacity, headlineTravel, gesture, activate, beginEntrance } =
    usePrismMotion({ autoPlay, reduced, radius, touchRadius, k });
  const canvasStyle = useAnimatedStyle(() => ({ opacity: canvasOpacity.get() }));
  const headlineStyle = useAnimatedStyle(() => ({ opacity: headlineOpacity.get(),
    transform: [{ translateY: reduced ? 0 : 12 * k * (1 - headlineTravel.get()) }] }));
  const scene = { width, height, cx, cy, radius, rotation, fan, clock: sceneClock, energy, sweep, sweepPower, charge, flash, ring, scale, light };
  const motes = { width, height, cx, cy, k, clock: moteClock, charge, flash, scale, burstAge, burstGain };
  return <View style={styles.page}>
    <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants"
      style={[StyleSheet.absoluteFill, canvasStyle]} onLayout={beginEntrance}>
      <Canvas style={StyleSheet.absoluteFill} accessible={false}>
        <PrismBackdrop {...scene} />
        <PrismMotes layer="back" {...motes} />
        <PrismSphere {...scene} />
        <PrismMotes layer="front" {...motes} />
      </Canvas>
    </Animated.View>
    <Animated.View pointerEvents="none" style={[styles.overlay, { left: layout.left, width: layout.frameWidth,
      top: layout.headlineTop, height: layout.headlineHeight }, headlineStyle]}
      accessible accessibilityRole="header" accessibilityLabel={PRISM.headlineLines.join(' ')}>
      <Text allowFontScaling={false} numberOfLines={1} accessible={false} style={[styles.headline,
        { fontSize: layout.headlineSize, lineHeight: layout.headlineLine, letterSpacing: PRISM.headline.tracking * k }]}>
        {PRISM.headlineLines[0]}</Text>
      <Text allowFontScaling={false} numberOfLines={1} accessible={false} style={[styles.headline,
        { fontSize: layout.headlineSize, lineHeight: layout.headlineLine, letterSpacing: PRISM.headline.tracking * k }]}>
        {PRISM.headlineLines[1]}</Text>
    </Animated.View>
    <Animated.View pointerEvents="none" style={[styles.overlay, { top: layout.captionTop, left: layout.left,
      width: layout.frameWidth, height: layout.captionLine }, canvasStyle]}>
      <PrismCaption clock={clock} reduced={reduced} fontSize={layout.captionSize} lineHeight={layout.captionLine} k={k} />
    </Animated.View>
    <GestureDetector gesture={gesture}>
      <View collapsable={false} accessible accessibilityRole="button" accessibilityLabel="Light sphere"
        accessibilityHint="Double tap to send a pulse of light through it"
        accessibilityActions={[{ name: 'activate' }]}
        onAccessibilityAction={(event) => { if (event.nativeEvent.actionName === 'activate') activate(); }}
        style={{ position: 'absolute', left: cx - touchRadius, top: cy - touchRadius,
          width: touchRadius * 2, height: touchRadius * 2, borderRadius: touchRadius }} />
    </GestureDetector>
  </View>;
}
const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: PRISM.page },
  overlay: { position: 'absolute' },
  headline: { color: PRISM.text, fontWeight: '500', textAlign: 'center', includeFontPadding: false },
});
