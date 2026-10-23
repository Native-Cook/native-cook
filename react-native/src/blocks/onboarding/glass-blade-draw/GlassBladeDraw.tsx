import { useFonts } from 'expo-font';
import { useCallback, useMemo, useState } from 'react';
import { StyleSheet, useWindowDimensions, View } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BladeCanvas } from './BladeCanvas';
import { BladeCta } from './BladeCta';
import { BladeHeader } from './BladeHeader';
import { computeFanLayout } from './blade-contract';
import { BLADE_COLOR, BLADE_FONT } from './blade-theme';
import { useBladeMotion } from './use-blade-motion';

export function GlassBladeDraw({ autoPlay = false }: { autoPlay?: boolean }) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const layout = useMemo(() => computeFanLayout(width, height, insets.bottom), [height, insets.bottom, width]);
  const [fontsLoaded] = useFonts({
    [BLADE_FONT.regular]: require('./assets/Inter-Regular.ttf'),
    [BLADE_FONT.bold]: require('./assets/Inter-Bold.ttf'),
  });
  const [canvasReady, setCanvasReady] = useState(false);
  const onReady = useCallback(() => setCanvasReady(true), []);
  const ready = fontsLoaded && canvasReady;
  const { motion, opacity, fanGesture, barGesture, drawing, step, draw, heroText } = useBladeMotion({ layout, ready, autoPlay });
  return <GestureHandlerRootView style={styles.root}>
    <View pointerEvents="none" accessible={false} accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants" style={StyleSheet.absoluteFill}>
      <BladeCanvas layout={layout} motion={motion} onReady={onReady} />
    </View>
    <BladeHeader width={width} topInset={insets.top} opacity={opacity} ready={ready} />
    <GestureDetector gesture={fanGesture}>
      <View collapsable={false} accessible={ready} accessibilityRole="adjustable" accessibilityLabel="Blades"
        accessibilityValue={{ text: heroText }} accessibilityElementsHidden={!ready}
        importantForAccessibility={ready ? 'auto' : 'no-hide-descendants'}
        accessibilityActions={[{ name: 'increment', label: 'Next blade' }, { name: 'decrement', label: 'Previous blade' }]}
        onAccessibilityAction={(event) => {
          if (event.nativeEvent.actionName === 'increment') step(1);
          if (event.nativeEvent.actionName === 'decrement') step(-1);
        }}
        style={{ position: 'absolute', left: 0, width, top: layout.ctaY - 8 - 300 * layout.scale,
          height: 300 * layout.scale }} />
    </GestureDetector>
    <BladeCta layout={layout} motion={motion} opacity={opacity} gesture={barGesture}
      drawing={drawing} ready={ready} onAccessibilityPress={draw} />
  </GestureHandlerRootView>;
}

const styles = StyleSheet.create({ root: { flex: 1, backgroundColor: BLADE_COLOR.page, overflow: 'hidden' } });
