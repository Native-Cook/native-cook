/** The black island shape (Skia) and what the pill says: "Send to" at rest, a receipt with Undo after a send. */
import { Canvas, Path } from '@shopify/react-native-skia';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';
import Svg, { Path as SvgPath } from 'react-native-svg';

import { clamp01, islandPath, pillAt } from './island-path';
import { PULL } from './motion';
import { COLORS, FAVOURITES, LAYOUT, TYPE } from './theme';
import { Avatar } from './SendSheet';
import type { SendValues } from './use-send-motion';

type Props = {
  width: number;
  height: number;
  v: SendValues;
  reduced: boolean;
  receipt: string;
  receiptLabel: string;
  canUndo: boolean;
  onUndo: () => void;
};

export function IslandShape({ width, height, v, reduced }: Pick<Props, 'width' | 'height' | 'v' | 'reduced'>) {
  // Reduce Motion: no liquid, the rest pill and the open sheet simply cross-fade.
  const path = useDerivedValue(() => islandPath(reduced ? 0 : v.g.get(), width, height));
  const sheetPath = useDerivedValue(() => islandPath(reduced ? 2 : 0, width, height));
  const sheetOpacity = useDerivedValue(() => (reduced ? v.veil.get() : 0));
  // Dark glass while the drop grows (the contacts show through), solid once it is the sheet.
  const restOpacity = useDerivedValue(() => {
    if (reduced) return 1 - v.veil.get() * 0.999;
    const g = v.g.get();
    const glass = Math.min(1, Math.max(0, (g - 0.05) / 0.3)) * (1 - Math.min(1, Math.max(0, (g - 1.1) / 0.5)));
    return 1 - (1 - PULL.dropOpacity) * glass;
  });
  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <Path path={path} color={COLORS.island} opacity={restOpacity} />
      {reduced && <Path path={sheetPath} color={COLORS.island} opacity={sheetOpacity} />}
    </Canvas>
  );
}

export function IslandPill({ width, v, receipt, receiptLabel, canUndo, onUndo }: Omit<Props, 'height' | 'reduced'>) {
  // The pill's words leave before the pill starts giving its width to the sheet.
  // The pill's words stay with the narrowing pill (clipped), then leave before it becomes the island.
  const content = useAnimatedStyle(() => {
    const p = pillAt(v.g.get(), width);
    return { opacity: 1 - clamp01((v.g.get() - 0.9) / 0.2), left: p.x, width: p.w };
  });
  // Keep the words centred on the pill while its sides close in.
  const inner = useAnimatedStyle(() => ({ transform: [{ translateX: (pillAt(v.g.get(), width).w - LAYOUT.pillWidth) / 2 }] }));
  const rest = useAnimatedStyle(() => ({ opacity: 1 - v.sent.get() }));
  const done = useAnimatedStyle(() => ({ opacity: v.sent.get() }));
  const bar = useAnimatedStyle(() => ({ transform: [{ scaleX: Math.max(0.0001, v.undo.get()) }], opacity: v.sent.get() }));
  return (
    <Animated.View style={[styles.pill, content]} pointerEvents="box-none">
      <Animated.View style={[styles.inner, inner]} pointerEvents="box-none">
      <Animated.View style={[styles.side, rest]} pointerEvents="none" accessible={!canUndo}
        accessibilityLabel="Send to recent people">
        <Text style={styles.pillText}>Send <Text style={styles.pillBold}>to</Text></Text>
        <View style={styles.stack}>
          {FAVOURITES.map((p, i) => (
            <View key={p.id} style={[styles.stackItem, { marginLeft: i === 0 ? 0 : -LAYOUT.avatarOverlap, zIndex: 3 - i }]}>
              <Avatar person={p} size={LAYOUT.avatarSmall} />
            </View>
          ))}
        </View>
      </Animated.View>
      <Animated.View style={[styles.side, done]} pointerEvents={canUndo ? 'box-none' : 'none'}>
        <View style={styles.receipt} accessible accessibilityLabel={receiptLabel}>
          <View style={styles.tick}>
            <Svg width={10} height={10} viewBox="0 0 12 12">
              <SvgPath d="M2.5 6.2 L5 8.6 L9.6 3.6" stroke={COLORS.island} strokeWidth={2.2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
            </Svg>
          </View>
          <Text style={styles.pillBold}>{receipt}</Text>
        </View>
        <Pressable onPress={onUndo} disabled={!canUndo} style={styles.undo} hitSlop={8} accessibilityRole="button"
          accessibilityLabel="Undo the payment">
          <Text style={styles.undoText}>Undo</Text>
        </Pressable>
      </Animated.View>
      </Animated.View>
      <Animated.View style={[styles.bar, bar]} pointerEvents="none" />
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  pill: { position: 'absolute', top: LAYOUT.pillTop, height: LAYOUT.pillHeight, overflow: 'hidden' },
  inner: { position: 'absolute', top: 0, bottom: 0, left: 0, width: LAYOUT.pillWidth },
  side: {
    position: 'absolute', top: 0, bottom: 0, left: 0, width: LAYOUT.pillWidth, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingLeft: 16, paddingRight: 10,
  },
  pillText: { color: 'rgba(255,255,255,0.72)', fontSize: TYPE.pill, fontWeight: '500' },
  pillBold: { color: '#FFFFFF', fontSize: TYPE.pill, fontWeight: '700', fontVariant: ['tabular-nums'] },
  stack: { flexDirection: 'row' },
  stackItem: { borderRadius: LAYOUT.avatarSmall / 2 + 2, borderWidth: 2, borderColor: COLORS.island },
  receipt: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  tick: { width: 18, height: 18, borderRadius: 9, backgroundColor: COLORS.success, alignItems: 'center', justifyContent: 'center' },
  undo: { height: 30, paddingHorizontal: 14, borderRadius: 15, backgroundColor: '#26272C', justifyContent: 'center' },
  undoText: { color: '#FFFFFF', fontSize: 14, fontWeight: '600' },
  bar: {
    position: 'absolute', left: 24, right: 24, bottom: 4, height: 2, borderRadius: 1,
    backgroundColor: 'rgba(255,255,255,0.5)', transformOrigin: 'left',
  },
});
