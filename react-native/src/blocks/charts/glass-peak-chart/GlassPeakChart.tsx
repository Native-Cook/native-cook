import { useCallback, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import { useReducedMotion } from 'react-native-reanimated';
import { PeakCanvas } from './PeakCanvas';
import { DESIGN, HIT, PERIODS } from './peak-contract';
import { usePeakMotion } from './use-peak-motion';

// Draw only through Skia and place transparent, accessible targets over its letterboxed design space.
export function GlassPeakChart({ autoPlay = false }: { autoPlay?: boolean }) {
  const [layout, setLayout] = useState({ width: 0, height: 0 });
  const [ready, setReady] = useState(false);
  const reduced = useReducedMotion();
  const { width, height } = layout;
  const k = Math.min(width / DESIGN.w, height / DESIGN.h) || 1;
  const ox = (width - DESIGN.w * k) / 2, oy = (height - DESIGN.h * k) / 2;
  const motion = usePeakMotion(autoPlay, reduced, ready, k);
  const onReady = useCallback(() => setReady(true), []);
  const controlHeight = Math.max(44, HIT.pill.h * k);
  return (
    <GestureHandlerRootView style={styles.root}>
      <GestureDetector gesture={motion.pageGesture}>
        <View style={styles.root} onLayout={(e) => setLayout(e.nativeEvent.layout)}>
          {width > 0 && height > 0 && (
            <PeakCanvas width={width} height={height} frame={motion.frame} still={reduced} onReady={onReady} />
          )}
          <GestureDetector gesture={motion.cardGesture}>
            <View
              collapsable={false}
              style={{ position: 'absolute', left: ox + HIT.card.x * k, top: oy + HIT.card.y * k, width: HIT.card.w * k, height: HIT.card.h * k }}
            >
              <View
                accessible
                accessibilityRole="adjustable"
                accessibilityLabel="Conversion rate chart"
                accessibilityValue={{ text: motion.a11y.text, min: 0, max: PERIODS[motion.a11y.period].value, now: motion.a11y.value }}
                accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
                onAccessibilityAction={(e) => motion.adjustAccessible(e.nativeEvent.actionName === 'increment' ? 1 : -1)}
                style={{ position: 'absolute', left: (HIT.plot.x - HIT.card.x) * k, top: (HIT.plot.y - HIT.card.y) * k, width: HIT.plot.w * k, height: Math.max(44, HIT.plot.h * k) }}
              />
            </View>
          </GestureDetector>
          <GestureDetector gesture={motion.refreshGesture}>
            <View
              accessible
              collapsable={false}
              accessibilityRole="button"
              accessibilityLabel="Refresh data"
              accessibilityState={{ busy: motion.a11y.busy }}
              accessibilityActions={[{ name: 'activate' }]}
              onAccessibilityTap={() => motion.activateAccessible('refresh')}
              onAccessibilityAction={() => motion.activateAccessible('refresh')}
              style={{ position: 'absolute', left: ox + HIT.refresh.x * k, top: oy + (HIT.refresh.y + HIT.refresh.h / 2) * k - controlHeight / 2, width: Math.max(44, HIT.refresh.w * k), height: controlHeight }}
            />
          </GestureDetector>
          <GestureDetector gesture={motion.pillGesture}>
            <View
              accessible
              collapsable={false}
              accessibilityRole="button"
              accessibilityLabel={`Period: ${PERIODS[motion.a11y.period].label}`}
              accessibilityHint="Shows the next period"
              accessibilityActions={[{ name: 'activate' }]}
              onAccessibilityTap={() => motion.activateAccessible('period')}
              onAccessibilityAction={() => motion.activateAccessible('period')}
              style={{ position: 'absolute', left: ox + HIT.pill.x * k, top: oy + (HIT.pill.y + HIT.pill.h / 2) * k - controlHeight / 2, width: Math.max(44, HIT.pill.w * k), height: controlHeight }}
            />
          </GestureDetector>
        </View>
      </GestureDetector>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({ root: { flex: 1, backgroundColor: '#000000' } });
