import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { Easing, useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { SpringReorderChecklist } from './SpringReorderChecklist';
import { STOWE_ITEMS } from './stoweData';

const PAGE = '#f5f2ea';
const INK = '#26302b';
const MUTE = '#7b8078';
const GREEN = '#2f7d5b';
const SIDE = 20;
const START = STOWE_ITEMS.filter((i) => i.packed).length;

/** The packing list of Stowe, an invented travel app, around the checklist. */
export function StoweListScreen({ autoPlay = true }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const [packed, setPacked] = useState(START);
  const progress = useSharedValue(START / STOWE_ITEMS.length);

  useEffect(() => {
    progress.set(withTiming(packed / STOWE_ITEMS.length, { duration: 320, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
  }, [packed, progress]);

  const barStyle = useAnimatedStyle(() => ({ transform: [{ scaleX: progress.get() }] }));

  return (
    <View style={[styles.screen, { paddingTop: insets.top + 8 }]}>
      <StatusBar style="dark" />
      <View style={styles.head}>
        <Text style={styles.eyebrow}>Leaving Friday, 7:40 am</Text>
        <Text accessibilityRole="header" style={styles.title}>
          Porto, 6 days
        </Text>
        <View style={styles.progressRow}>
          <View style={styles.track}>
            <Animated.View style={[styles.fill, barStyle]} />
          </View>
          <Text style={styles.count}>
            {packed} of {STOWE_ITEMS.length} packed
          </Text>
        </View>
      </View>
      <SpringReorderChecklist autoPlay={autoPlay} items={STOWE_ITEMS} onPackedChange={setPacked} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE },
  head: { paddingHorizontal: SIDE, paddingBottom: 12 },
  eyebrow: { color: MUTE, fontSize: 14, fontWeight: '500' },
  title: { color: INK, fontSize: 32, fontWeight: '700', letterSpacing: -0.5, marginTop: 2 },
  progressRow: { flexDirection: 'row', alignItems: 'center', gap: 12, marginTop: 12 },
  track: { flex: 1, height: 5, borderRadius: 3, overflow: 'hidden', backgroundColor: 'rgba(38,48,43,0.12)' },
  fill: { width: '100%', height: 5, borderRadius: 3, backgroundColor: GREEN, transformOrigin: 'left' },
  count: { color: INK, fontSize: 13, fontWeight: '600', fontVariant: ['tabular-nums'] },
});
