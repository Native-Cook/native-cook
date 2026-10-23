import { useCallback, useEffect, useMemo, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { GestureDetector } from 'react-native-gesture-handler';
import { ReduceMotion, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';

import { KeycapCanvas } from './KeycapCanvas';
import { LOOK } from './keycap-scene';
import { FAN_ORDER, MOODS, WORD_CHOICES, keycapLayout } from './keycap-theme';
import { useKeycapMotion } from './use-keycap-motion';

const LABELS = ['Subject', 'Verb', 'Intensity', 'Mood'];
export function KeycapMoodInput({ autoPlay = false, onTouch }: { autoPlay?: boolean; onTouch?: () => void }) {
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [visible, setVisible] = useState(false);
  const reduced = useReducedMotion();
  const motion = useKeycapMotion({ ...size, autoPlay, reduced, onTouch });
  const { ready } = motion;
  const reveal = useSharedValue(0);
  const layout = useMemo(() => keycapLayout(size.width, size.height), [size.width, size.height]);
  const onReady = useCallback((next: boolean) => {
    ready.set(next);
    reveal.set(next ? withTiming(1, { duration: LOOK.revealMs, reduceMotion: ReduceMotion.Never }) : 0);
    setVisible(next);
  }, [ready, reveal]);
  useEffect(() => () => { ready.set(false); }, [ready]);
  return <View style={styles.page} onLayout={event => {
    const { width, height } = event.nativeEvent.layout;
    setSize(previous => previous.width === width && previous.height === height ? previous : { width, height });
  }}>
    {size.width > 0 && size.height > 0 && <GestureDetector gesture={motion.gesture}>
      <View collapsable={false} style={styles.page}>
        <KeycapCanvas layout={layout} motion={motion} reduced={reduced} reveal={reveal} onReady={onReady} />
        {visible && <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
          {layout.hits.map((rect, index) => <View key={index} accessible pointerEvents="none" accessibilityRole="adjustable"
            accessibilityLabel={LABELS[index]} accessibilityValue={{ text: index === 3 ? MOODS[motion.moodIndex].accessibilityName : WORD_CHOICES[index][motion.choices[index]] }}
            accessibilityActions={index === 3
              ? [{ name: 'increment' }, { name: 'decrement' }, { name: 'activate' }, { name: 'showMoods', label: 'Show moods' }]
              : [{ name: 'increment' }, { name: 'decrement' }, { name: 'activate' }]}
            onAccessibilityAction={event => {
              const action = event.nativeEvent.actionName;
              if (action === 'showMoods') motion.actions.toggleFan();
              else if (action === 'decrement') motion.actions.previous(index);
              else if (action === 'increment' || action === 'activate') motion.actions.next(index);
            }}
            style={{ position: 'absolute', left: rect.x, top: rect.y, width: rect.width, height: rect.height }} />)}
          {motion.fanOpen && layout.minis.map((centre, index) => {
            const mood = FAN_ORDER[index]; const name = MOODS[mood].accessibilityName;
            return <View key={index} accessible pointerEvents="none" accessibilityRole="button"
              accessibilityLabel={name.charAt(0).toUpperCase() + name.slice(1)} accessibilityState={{ selected: mood === motion.moodIndex }}
              accessibilityActions={[{ name: 'activate' }]} onAccessibilityTap={() => motion.actions.selectMood(mood)}
              onAccessibilityAction={event => { if (event.nativeEvent.actionName === 'activate') motion.actions.selectMood(mood); }}
              style={{ position: 'absolute', left: centre.x - layout.M / 2, top: centre.y - layout.M / 2, width: layout.M, height: layout.M }} />;
          })}
        </View>}
      </View>
    </GestureDetector>}
  </View>;
}
const styles = StyleSheet.create({ page: { flex: 1, backgroundColor: '#000' } });
