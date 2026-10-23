import { useFont, useImage } from '@shopify/react-native-skia';
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import { useReducedMotion } from 'react-native-reanimated';
import { ChartCanvas } from './ChartCanvas';
import { useChartFilm } from './use-chart-film';

// Gate the canvas on loaded assets and overlay transparent accessible touch targets.
export function SpikeToStairChart({ autoPlay = false }: { autoPlay?: boolean }) {
  const [layout, setLayout] = useState({ width: 0, height: 0 });
  const reduced = useReducedMotion();
  const font = useFont(require('./assets/ArchivoExpanded-Regular.ttf'), 15);
  const small = useFont(require('./assets/ArchivoExpanded-Regular.ttf'), 9);
  const digits = useFont(require('./assets/ArchivoExpanded-Black.ttf'), 13);
  const streaks = useImage(require('./assets/floor-streaks.png'));
  const glints = useImage(require('./assets/floor-glints.png'));
  const shards = useImage(require('./assets/shards.png'));
  const { width, height } = layout,
    k = Math.min(width / 402, height / 874) || 1,
    ox = (width - 402 * k) / 2,
    oy = (height - 874 * k) / 2;
  const ready = !!(width && height && font && small && digits && streaks && glints && shards);
  const motion = useChartFilm(autoPlay, reduced, ready, k);
  return (
    <GestureHandlerRootView style={styles.root}>
      <View style={styles.root} onLayout={(e) => setLayout(e.nativeEvent.layout)}>
        {font && small && digits && streaks && glints && shards && (
          <ChartCanvas
            width={width}
            height={height}
            k={k}
            ox={ox}
            oy={oy}
            motion={motion}
            still={reduced}
            assets={{ font, small, digits, streaks, glints, shards }}
          />
        )}
        <GestureDetector gesture={motion.gesture}>
          <View
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel="Chart scrubber"
            accessibilityValue={{ text: motion.a11y }}
            accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
            onAccessibilityAction={(e) => motion.adjust(e.nativeEvent.actionName === 'increment' ? 1 : -1)}
            style={{
              position: 'absolute',
              left: ox + 40 * k,
              top: oy + 180 * k,
              width: 322 * k,
              height: 380 * k,
            }}
          />
        </GestureDetector>
        {[0, 1].map((d) => (
          <Pressable
            key={d}
            accessibilityRole="button"
            accessibilityLabel={d ? 'Show discipline' : 'Show motivation'}
            accessibilityState={{ selected: motion.selected === d }}
            onPressIn={() => motion.press(d, true)}
            onPressOut={() => motion.press(d, false)}
            onPress={() => motion.go(d)}
            style={{
              position: 'absolute',
              left: ox + (133 + 68 * d) * k,
              top: oy + 792 * k - Math.max(44, 44 * k) / 2,
              width: 68 * k,
              height: Math.max(44, 44 * k),
            }}
          />
        ))}
      </View>
    </GestureHandlerRootView>
  );
}
const styles = StyleSheet.create({ root: { flex: 1, backgroundColor: '#000000' } });
