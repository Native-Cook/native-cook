import { memo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { swapAt, TOOLS } from './magnetSnapDockMotion';

/*
 The tool panel above the dock. The card stays; its six contents (one per tool) are stacked and
 the selection cross-fades them: the old one lifts 8 pt and fades, the new one rises 8 pt into
 place, the title shrinking from 1.04× as it lands. `sel` is [from, to, snap time, clock now].
 */

export const TOOL_NAMES = ['Select', 'Pen', 'Brush', 'Shapes', 'Type', 'Layers'];
const SUBTITLES = ['Move, scale and rotate', 'Bézier paths · round caps', 'Gouache round · wet edges', 'Rectangle · 12 pt corners', 'Grotesk Bold · 48 pt', '6 layers · 2 hidden'];
const ROWS: [string, string, number][][] = [
  [
    ['Snap to grid', '8 px', 0.4],
    ['Nudge', '1 px', 0.15],
    ['Rotate step', '15°', 0.25],
  ],
  [
    ['Stroke', '2 pt', 0.2],
    ['Smoothing', '40 %', 0.4],
    ['Pressure', 'Off', 0.02],
  ],
  [
    ['Size', '12 px', 0.48],
    ['Flow', '68 %', 0.68],
    ['Wetness', '30 %', 0.3],
  ],
  [
    ['Corner', '12 pt', 0.5],
    ['Fill', '80 %', 0.8],
    ['Stroke', '0 pt', 0.02],
  ],
  [
    ['Size', '48 pt', 0.6],
    ['Tracking', '−1 %', 0.45],
    ['Leading', '1.1', 0.55],
  ],
  [
    ['Opacity', '100 %', 1],
    ['Visible', '4 of 6', 0.66],
    ['Locked', '2', 0.33],
  ],
];
const COLORS = ['#5b606b', '#2f7d76', '#c4532f', '#cf9a32', '#57683c', '#3f5f8a'];
const INK = '#f1ede6';
const INK_SOFT = '#9a9ca3';

const Content = memo(function Content({ index, sel }: { index: number; sel: SharedValue<number[]> }) {
  const style = useAnimatedStyle(() => {
    const s = sel.get();
    const q = swapAt(s, s[3]);
    let a = 0;
    let dy = 0;
    if (s[0] === s[1]) a = index === s[1] ? 1 : 0;
    else if (index === s[1]) {
      a = q;
      dy = 8 * (1 - q);
    } else if (index === s[0]) {
      a = 1 - q;
      dy = -8 * q;
    }
    return { opacity: a, transform: [{ translateY: dy }] };
  });
  const title = useAnimatedStyle(() => {
    const s = sel.get();
    const q = s[0] === s[1] ? 1 : swapAt(s, s[3]);
    return { transform: [{ scale: index === s[1] ? 1.04 - 0.04 * q : 1 }] };
  });
  return (
    <Animated.View style={[StyleSheet.absoluteFill, styles.content, style]} pointerEvents="none">
      <View style={styles.head}>
        <View style={[styles.swatch, { backgroundColor: COLORS[index] }]} />
        <View style={styles.headText}>
          <Animated.Text style={[styles.title, title]}>{TOOL_NAMES[index]}</Animated.Text>
          <Text style={styles.subtitle} numberOfLines={1}>
            {SUBTITLES[index]}
          </Text>
        </View>
        <View style={styles.pill}>
          <View style={[styles.led, { backgroundColor: COLORS[index] }]} />
          <Text style={styles.pillText}>Active</Text>
        </View>
      </View>
      {ROWS[index].map(([label, value, frac]) => (
        <View key={label} style={styles.row}>
          <View style={styles.rowTop}>
            <Text style={styles.rowLabel}>{label}</Text>
            <Text style={styles.rowValue}>{value}</Text>
          </View>
          <View style={styles.track}>
            <View style={[styles.fill, { width: `${Math.round(frac * 100)}%`, backgroundColor: COLORS[index] }]} />
          </View>
        </View>
      ))}
    </Animated.View>
  );
});

export function MagnetSnapDockPanel({ sel, top, height }: { sel: SharedValue<number[]>; top: number; height: number }) {
  return (
    <View style={[styles.card, { top, height }]} pointerEvents="none" accessible accessibilityLabel="Tool settings">
      {Array.from({ length: TOOLS }, (_, i) => (
        <Content key={i} index={i} sel={sel} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    position: 'absolute',
    left: 16,
    right: 16,
    borderRadius: 24,
    borderCurve: 'continuous',
    backgroundColor: '#1f2126',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.1)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.4), 0 18px 40px rgba(0,0,0,0.35), inset 0 1px 0 rgba(255,255,255,0.06)',
    overflow: 'hidden',
  },
  content: { padding: 18, gap: 10 },
  head: { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 2 },
  swatch: { width: 36, height: 36, borderRadius: 10, borderCurve: 'continuous', boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.25), 0 2px 6px rgba(0,0,0,0.3)' },
  headText: { flex: 1, gap: 1 },
  title: { color: INK, fontSize: 22, fontWeight: '700', letterSpacing: -0.3, transformOrigin: 'left center' },
  subtitle: { color: INK_SOFT, fontSize: 13, fontWeight: '500' },
  pill: { flexDirection: 'row', alignItems: 'center', gap: 6, height: 26, paddingHorizontal: 10, borderRadius: 13, backgroundColor: 'rgba(255,255,255,0.06)' },
  led: { width: 6, height: 6, borderRadius: 3 },
  pillText: { color: INK, fontSize: 12, fontWeight: '600' },
  row: { gap: 6 },
  rowTop: { flexDirection: 'row', justifyContent: 'space-between' },
  rowLabel: { color: INK_SOFT, fontSize: 14, fontWeight: '500' },
  rowValue: { color: INK, fontSize: 14, fontWeight: '600', fontVariant: ['tabular-nums'] },
  track: { height: 4, borderRadius: 2, backgroundColor: 'rgba(255,255,255,0.08)', overflow: 'hidden' },
  fill: { height: 4, borderRadius: 2 },
});
