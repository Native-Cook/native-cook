// Portable reward screen: all geometry comes from its measured container and safe area.
import { useCallback, useMemo, useState } from 'react';
import { StyleSheet, View, type LayoutChangeEvent } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import { useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { CollectCopy } from './CollectCopy';
import { GemScene } from './GemScene';
import { COPY, layout, type Layout } from './gem-contract';
import { useGemFonts } from './gem-fonts';
import { useGemCollect } from './use-gem-collect';

function MeasuredScene({ frame, autoPlay, fontsReady }: { frame: Layout; autoPlay: boolean; fontsReady: boolean }) {
  const [canvasReady, setCanvasReady] = useState(false);
  const reduced = useReducedMotion(), ready = fontsReady && canvasReady;
  const c = useGemCollect(frame, autoPlay, reduced, ready);
  const r = frame.gemRadius * 1.25, disabled = c.text.phase !== 'before' && c.text.phase !== 'after';
  return <GestureDetector gesture={c.rootGesture}>
    <View style={styles.root} collapsable={false}>
      <GemScene layout={frame} controller={c} onReady={setCanvasReady} />
      <View style={StyleSheet.absoluteFill} pointerEvents="box-none" accessibilityElementsHidden={!ready} importantForAccessibility={ready ? 'auto' : 'no-hide-descendants'}>
        <CollectCopy layout={frame} controller={c} reduced={reduced} />
        <GestureDetector gesture={c.gemGesture}>
          <View collapsable={false} accessible={ready} accessibilityRole="button" accessibilityLabel="Crystal"
            accessibilityHint="Hold to collect the Lumens. Drag to turn it." onAccessibilityTap={c.activateGem}
            style={{ position: 'absolute', left: frame.gemX - r, top: frame.gemY - r, width: r * 2, height: r * 2, borderRadius: r }} />
        </GestureDetector>
        <GestureDetector gesture={c.barGesture}>
          <View collapsable={false} accessible={ready} accessibilityRole="button" accessibilityLabel={c.text.phase === 'after' ? COPY.buttonAfter : COPY.buttonBefore}
            accessibilityState={{ disabled: disabled || !ready }} onAccessibilityTap={c.activateBar}
            style={{ position: 'absolute', left: frame.button.x, top: frame.button.y, width: frame.button.width, height: frame.button.height, borderRadius: frame.button.height / 2 }} />
        </GestureDetector>
      </View>
    </View>
  </GestureDetector>;
}
export function GemOrbCollect({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets(), fontsReady = useGemFonts();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const onLayout = useCallback((event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setSize(old => old.width === width && old.height === height ? old : { width, height });
  }, []);
  const frame = useMemo(() => layout(size.width, size.height, insets.top, insets.bottom), [insets.bottom, insets.top, size.height, size.width]);
  return <GestureHandlerRootView onLayout={onLayout} style={styles.root}>
    {size.width > 0 && size.height > 0 && fontsReady && <MeasuredScene key={`${size.width}:${size.height}:${insets.top}:${insets.bottom}`} frame={frame} autoPlay={autoPlay} fontsReady={fontsReady} />}
  </GestureHandlerRootView>;
}
const styles = StyleSheet.create({ root: { flex: 1, backgroundColor: '#000000', overflow: 'hidden' } });
