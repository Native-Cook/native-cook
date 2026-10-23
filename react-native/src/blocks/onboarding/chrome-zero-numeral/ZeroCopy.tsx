// Chrome Zero — staggered system typography and a lit, tactile periwinkle glass button.
import { LinearGradient } from 'expo-linear-gradient';
import { Blur, Canvas, RoundedRect } from '@shopify/react-native-skia';
import { type ReactNode } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';
import type { ZeroMotion } from './use-zero-motion';
import { type ZeroLayout, ZERO_TONE } from './zero-theme';
import type { ZeroFrame } from './zero-timeline';

function CopyBeat({ frame, index, top, children, reduced }: {
  frame: SharedValue<ZeroFrame>; index: number; top: number; children: ReactNode; reduced: boolean;
}) {
  const style = useAnimatedStyle(() => {
    const f = frame.get();
    return { opacity: f.copy[index], transform: [{ translateY: reduced ? 0 : f.copyY[index] }] };
  });
  return <Animated.View pointerEvents="box-none" style={[styles.row, { top }, style]}>{children}</Animated.View>;
}

function StartButton({ layout, frame, gesture, activate, reduced }: {
  layout: ZeroLayout; frame: SharedValue<ZeroFrame>; gesture: ZeroMotion['buttonGesture'];
  activate: () => void; reduced: boolean;
}) {
  const { width, height, radius } = layout.button;
  const pressStyle = useAnimatedStyle(() => ({
    transform: [{ scale: 1 - 0.03 * frame.get().buttonPress }],
  }));
  const glowAlpha = useDerivedValue(() => 0.35 - 0.09 * frame.get().buttonPress);
  const glowBlur = useDerivedValue(() => 24 - 8 * frame.get().buttonPress);
  const glowY = useDerivedValue(() => 80 - 4 * frame.get().buttonPress);
  const bandStyle = useAnimatedStyle(() => {
    const p = frame.get().sweep;
    return { opacity: p <= -0.999 || p >= 0.999 ? 0 : 0.46,
      transform: [{ translateX: ((p + 1) / 2) * (width + 160) - 100 }, { rotate: '-22deg' }] };
  });
  const arrowStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: reduced ? 0 : frame.get().arrow }, { translateY: reduced ? 0 : -frame.get().arrow }],
  }));
  // Compact phones keep the chip's 8 pt inset and the control's 48 pt touch target.
  const chipSize = height - 16;
  return <GestureDetector gesture={gesture}>
    <Animated.View accessible accessibilityRole="button" accessibilityLabel="Start now"
      accessibilityActions={[{ name: 'activate' }]} onAccessibilityTap={activate}
      onAccessibilityAction={(event) => { if (event.nativeEvent.actionName === 'activate') activate(); }}
      style={[{ width, height, borderRadius: radius }, pressStyle]}>
      <Canvas pointerEvents="none" style={{ position: 'absolute', left: -72, top: -72,
        width: width + 144, height: height + 144 }}>
        <RoundedRect x={72} y={glowY} width={width} height={height} r={radius}
          color={ZERO_TONE.glow} opacity={glowAlpha}><Blur blur={glowBlur} /></RoundedRect>
      </Canvas>
      <View pointerEvents="none" style={[StyleSheet.absoluteFill, { borderRadius: radius, overflow: 'hidden' }]}>
        <LinearGradient colors={[ZERO_TONE.buttonTop, ZERO_TONE.buttonBottom]} style={StyleSheet.absoluteFill} />
        <View style={[StyleSheet.absoluteFill, { borderRadius: radius, borderWidth: 1,
          borderTopColor: 'rgba(255,255,255,0.7)', borderColor: 'rgba(220,228,255,0.25)' }]} />
        <Animated.View style={[{ position: 'absolute', top: -32, width: 56, height: 128 }, bandStyle]}>
          <LinearGradient colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.9)', 'rgba(255,255,255,0)']}
            start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} style={StyleSheet.absoluteFill} />
        </Animated.View>
        <Text style={[styles.buttonLabel, { lineHeight: height, fontSize: 17 * layout.typeScale }]}>Start now</Text>
        <Animated.View style={[{ position: 'absolute', right: 8, top: 8, width: chipSize, height: chipSize,
          borderRadius: 12, overflow: 'hidden' }, arrowStyle]}>
          <LinearGradient colors={[ZERO_TONE.chipTop, ZERO_TONE.chipBottom]} style={styles.chip}>
            <Image source={require('./assets/zero-arrow.png')} style={{ width: 20, height: 20 }} />
            <View style={[StyleSheet.absoluteFill, { borderRadius: 12, borderWidth: 1,
              borderColor: 'rgba(255,255,255,0.13)', borderTopColor: 'rgba(255,255,255,0.45)' }]} />
          </LinearGradient>
        </Animated.View>
      </View>
    </Animated.View>
  </GestureDetector>;
}

export function ZeroCopy({ layout, frame, buttonGesture, activateButton, reduced }: {
  layout: ZeroLayout; frame: SharedValue<ZeroFrame>; buttonGesture: ZeroMotion['buttonGesture'];
  activateButton: () => void; reduced: boolean;
}) {
  const heading = { fontSize: layout.headlineSize, lineHeight: layout.headlineLine };
  return <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
    <CopyBeat frame={frame} index={0} top={layout.pillTop} reduced={reduced}>
      <View style={styles.pill} accessible accessibilityLabel="nought">
        <Image source={require('./assets/zero-mark.png')} style={{ width: 14, height: 14 }} accessible={false} />
        <Text style={[styles.brand, { fontSize: 13 * layout.typeScale }]}>nought</Text>
      </View>
    </CopyBeat>
    <View accessible accessibilityRole="header" accessibilityLabel="0% deposit fees. Every time."
      style={{ position: 'absolute', top: layout.headlineTop, width: layout.width, height: layout.headlineLine * 2 }}>
      <CopyBeat frame={frame} index={1} top={0} reduced={reduced}>
        <Text accessible={false} numberOfLines={1} style={[styles.headline, heading]}>0% deposit fees.</Text>
      </CopyBeat>
      <CopyBeat frame={frame} index={2} top={layout.headlineLine} reduced={reduced}>
        <Text accessible={false} numberOfLines={1} style={[styles.headline, heading]}>Every time.</Text>
      </CopyBeat>
    </View>
    <CopyBeat frame={frame} index={3} top={layout.bodyTop} reduced={reduced}>
      <Text numberOfLines={1} style={[styles.body, { fontSize: layout.bodySize, lineHeight: layout.bodyLine }]}>
        Zero to get in. Everything to gain.
      </Text>
    </CopyBeat>
    <CopyBeat frame={frame} index={4} top={layout.button.y} reduced={reduced}>
      <StartButton layout={layout} frame={frame} gesture={buttonGesture} activate={activateButton} reduced={reduced} />
    </CopyBeat>
  </View>;
}

const styles = StyleSheet.create({
  row: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  pill: { height: 28, borderRadius: 14, paddingHorizontal: 12, flexDirection: 'row', gap: 6,
    alignItems: 'center', backgroundColor: 'rgba(255,255,255,0.06)',
    borderWidth: 1, borderColor: 'rgba(185,200,255,0.22)' },
  brand: { fontSize: 13, fontWeight: '500', color: '#E1E6FF', letterSpacing: 0.1 },
  headline: { color: ZERO_TONE.headline, fontWeight: '600', letterSpacing: -0.6, textAlign: 'center' },
  body: { color: ZERO_TONE.body, fontWeight: '400', textAlign: 'center' },
  buttonLabel: { width: '100%', color: ZERO_TONE.ink, textAlign: 'center', fontWeight: '600', includeFontPadding: false },
  chip: { flex: 1, alignItems: 'center', justifyContent: 'center' },
});
