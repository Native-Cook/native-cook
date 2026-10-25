import { BlurView } from 'expo-blur';
import { SymbolView } from 'expo-symbols';
import { memo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, interpolateColor, useAnimatedStyle } from 'react-native-reanimated';

import { CARD_H, type ENTRIES } from './aquariumScene';

/** Which card the fish are feeding at, how strongly (0–1), and its press pulse (0–1). */
export type Focus = { card: number; amt: number; press: number };

type Props = {
  index: number;
  entry: (typeof ENTRIES)[number];
  focus: SharedValue<Focus>;
  onPress: (index: number) => void;
};

/**
 One log entry: a pane of frosted glass in the water (fish swim behind it), an icon tile, the title and its detail, the
 time and a tag. It never re-renders while the list scrolls; the feeding glow and the press are
 animated styles read on the UI thread.
*/
export const LogCard = memo(function LogCard({ index, entry, focus, onPress }: Props) {
  const glass = useAnimatedStyle(() => {
    const f = focus.get();
    const on = f.card === index ? f.amt : 0;
    const press = f.card === index ? f.press : 0;
    return {
      transform: [{ scale: 1 - 0.025 * press }],
      borderColor: interpolateColor(on, [0, 1], ['rgba(220,248,255,0.16)', 'rgba(255,214,128,0.7)']),
      backgroundColor: interpolateColor(on, [0, 1], ['rgba(8,46,60,0.5)', 'rgba(42,76,64,0.56)']),
    };
  });
  return (
    <Pressable onPressIn={() => onPress(index)} accessibilityRole="button" accessibilityLabel={`${entry.title}, ${entry.detail}, ${entry.when}`}>
      <Animated.View style={[styles.card, glass]}>
        {/* Frosted glass: the fish behind it blur and dim; a still blur, never animated. */}
        <BlurView intensity={18} tint="dark" style={StyleSheet.absoluteFill} />
        {/* The glass's lit top edge. */}
        <View style={styles.edge} />
        <View style={[styles.tile, { backgroundColor: `${entry.tint}26` }]}>
          <SymbolView name={{ ios: entry.ios as never, android: entry.android as never, web: entry.android as never }} size={20} weight="semibold" tintColor={entry.tint} />
        </View>
        <View style={styles.body}>
          <View style={styles.row}>
            <Text style={styles.title} numberOfLines={1}>{entry.title}</Text>
            <Text style={styles.when}>{entry.when}</Text>
          </View>
          <Text style={styles.detail} numberOfLines={1}>{entry.detail}</Text>
          <Text style={[styles.tag, { color: entry.tint }]}>{entry.tag.toUpperCase()}</Text>
        </View>
      </Animated.View>
    </Pressable>
  );
});

const styles = StyleSheet.create({
  card: {
    height: CARD_H,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    paddingHorizontal: 16,
    borderRadius: 22,
    borderCurve: 'continuous',
    borderWidth: 1,
    overflow: 'hidden',
    boxShadow: '0 1px 2px rgba(1,16,24,0.3), 0 14px 30px rgba(1,16,24,0.28), inset 0 -1px 0 rgba(0,20,30,0.25)',
  },
  edge: { position: 'absolute', top: 0, left: 14, right: 14, height: 1, backgroundColor: 'rgba(225,250,255,0.42)' },
  tile: { width: 44, height: 44, borderRadius: 14, borderCurve: 'continuous', alignItems: 'center', justifyContent: 'center' },
  body: { flex: 1, gap: 3 },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', gap: 8 },
  title: { flexShrink: 1, color: '#F0FBFC', fontSize: 17, fontWeight: '700' },
  when: { color: 'rgba(222,246,250,0.55)', fontSize: 12, fontWeight: '600', fontVariant: ['tabular-nums'] },
  detail: { color: 'rgba(222,246,250,0.78)', fontSize: 14, fontWeight: '500' },
  tag: { fontSize: 10, fontWeight: '800', letterSpacing: 1.4, marginTop: 3 },
});
