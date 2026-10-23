/** "Groceries deleted · Undo", above the tab panel. */
import { Feather } from '@expo/vector-icons';
import { Pressable, StyleSheet, Text } from 'react-native';
import Animated, { useAnimatedStyle, type SharedValue } from 'react-native-reanimated';

import { COLOR, LAYOUT, TYPE, SPACE } from './theme';

type Props = { shown: SharedValue<number>; fromY: SharedValue<number>; title: string; bottom: number; reduceMotion: boolean; onUndo: () => void };

export function UndoPill({ shown, fromY, title, bottom, reduceMotion, onUndo }: Props) {
  // Hero morph: the pill grows out of the deleted row's Delete tile — it starts at the row's height,
  // tile-sized at the right, and travels down while it widens into the pill.
  const style = useAnimatedStyle(() => {
    const p = Math.min(Math.max(shown.get(), 0), 1.06);
    if (reduceMotion) return { opacity: Math.min(shown.get(), 1) };
    return {
      opacity: Math.min(p * 3, 1),
      transform: [
        { translateY: (1 - p) * fromY.get() },
        { scaleX: 0.22 + 0.78 * p },
        { scaleY: 1 + 0.08 * (1 - p) },
      ],
    };
  });
  return (
    <Animated.View style={[styles.pill, { bottom }, style]} pointerEvents="box-none" accessibilityLiveRegion="polite">
      <Text style={styles.text} numberOfLines={1}>{title} deleted</Text>
      <Pressable style={styles.undo} onPress={onUndo} accessibilityRole="button" accessibilityLabel={`Undo, restore ${title}`} hitSlop={8}>
        <Feather name="rotate-ccw" size={16} color={COLOR.pink} />
        <Text style={styles.undoText}>Undo</Text>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  pill: {
    transformOrigin: 'right',
    position: 'absolute', left: LAYOUT.gutter, right: LAYOUT.gutter + LAYOUT.fab + 12,
    height: LAYOUT.pillHeight, borderRadius: LAYOUT.pillHeight / 2, backgroundColor: COLOR.plum,
    flexDirection: 'row', alignItems: 'center', paddingLeft: SPACE.xl, paddingRight: SPACE.s,
    shadowColor: COLOR.slabShadow, shadowOpacity: 0.3, shadowRadius: 18, shadowOffset: { width: 0, height: 8 },
  },
  text: { ...TYPE.pill, color: COLOR.white, flex: 1 },
  undo: { height: 44, flexDirection: 'row', alignItems: 'center', gap: SPACE.s, paddingHorizontal: SPACE.m },
  undoText: { ...TYPE.pillAction, color: COLOR.pink },
});
