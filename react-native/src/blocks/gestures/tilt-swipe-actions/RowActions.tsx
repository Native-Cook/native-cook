/**
 * Flag and Delete, two flush tiles the height of the row in the strip a left swipe uncovers (iOS Mail's layer, in
 * the ledger's materials). The strip is shared out as it opens: Flag starts at the row's edge, Delete halfway along,
 * each tile's mark centred in its share and clipped by it, so a mark is never cut by the row. Past the full-swipe
 * line Delete swallows the strip, its mark riding at the leading edge.
 */
import { StyleSheet, Text, View } from 'react-native';
import Animated, { useAnimatedStyle, type SharedValue } from 'react-native-reanimated';

import { Glyph } from './Glyphs';
import { BAR } from './split-geometry';
import { COLOR, ICON, LAYOUT, TYPE } from './theme';

type Props = {
  tx: SharedValue<number>;
  armP: SharedValue<number>;
  press: SharedValue<number>; // 1: Flag held, 2: Delete held
  flagged: boolean;
};

const MARK = 64;
const WIDE = BAR + 2 * LAYOUT.tile; // a tile's fill reaches past the row, which clips it

function clamp01(v: number): number {
  'worklet';
  return Math.min(Math.max(v, 0), 1);
}

export function RowActions({ tx, armP, press, flagged }: Props) {
  const flag = useAnimatedStyle(() => ({ transform: [{ translateX: BAR + Math.min(tx.get(), 0) }] }));
  const del = useAnimatedStyle(() => {
    const t = Math.min(tx.get(), 0);
    const left = BAR + t / 2 + (t / 2) * armP.get(); // halfway along the strip, or its whole width once armed
    return { transform: [{ translateX: left }] };
  });
  // Each mark sits at the centre of its share of the strip (w / 4 from the tile's left edge) and fades in once it fits.
  const flagMark = useAnimatedStyle(() => {
    const w = -Math.min(tx.get(), 0);
    const held = press.get() === 1 ? 0.94 : 1;
    return {
      opacity: clamp01((w - 2 * MARK * 0.75) / 32) * (1 - armP.get()),
      transform: [{ translateX: w / 4 - MARK / 2 }, { scale: held }],
    };
  });
  const delMark = useAnimatedStyle(() => {
    const w = -Math.min(tx.get(), 0);
    const a = armP.get();
    const held = press.get() === 2 ? 0.94 : 1;
    // Armed, the mark rides 36 pt in from the row's edge (the centre of a tile at the leading edge).
    const x = (w / 4) * (1 - a) + (LAYOUT.tile / 2) * a;
    return {
      opacity: Math.max(clamp01((w - 2 * MARK * 0.75) / 32), a),
      transform: [{ translateX: x - MARK / 2 }, { scale: held }],
    };
  });
  return (
    <View style={styles.strip} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <Animated.View style={[styles.tile, styles.flag, flag]}>
        <Animated.View style={[styles.mark, flagMark]}>
          <Glyph name="flag" color={COLOR.ivory} size={ICON.action} />
          <Text style={[TYPE.action, styles.label]}>{flagged ? 'UNFLAG' : 'FLAG'}</Text>
        </Animated.View>
      </Animated.View>
      <Animated.View style={[styles.tile, styles.delete, del]}>
        <Animated.View style={[styles.mark, delMark]}>
          <Glyph name="trash" color={COLOR.ivory} size={ICON.action} />
          <Text style={[TYPE.action, styles.label]}>DELETE</Text>
        </Animated.View>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  strip: { position: 'absolute', left: 0, top: 0, width: BAR, height: LAYOUT.row },
  tile: { position: 'absolute', left: 0, top: 0, width: WIDE, height: LAYOUT.row, overflow: 'hidden' },
  flag: { backgroundColor: COLOR.flag },
  delete: { backgroundColor: COLOR.delete },
  mark: { position: 'absolute', left: 0, top: 0, width: MARK, height: LAYOUT.row, alignItems: 'center', justifyContent: 'center', gap: 6 },
  label: { color: COLOR.ivory },
});
