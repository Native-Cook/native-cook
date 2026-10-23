/**
 * Browsing: back, the tray and "Someday ⌄", the round chevron, on an opaque bar that hides the first row.
 * Selecting (as in the source): the bar goes away, the first row shows through, and a blue Done sits on its line.
 */
import { Feather } from '@expo/vector-icons';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { useAnimatedStyle, type SharedValue } from 'react-native-reanimated';

import { COLOR, LAYOUT, TYPE } from './theme';

type Props = { selecting: SharedValue<number>; isSelecting: boolean; topInset: number; reduceMotion: boolean; onDone: () => void };

export function NavBar({ selecting, isSelecting, topInset, onDone }: Props) {
  const browse = useAnimatedStyle(() => ({ opacity: 1 - selecting.get() }));
  const select = useAnimatedStyle(() => ({ opacity: selecting.get() }));
  const barHeight = topInset + LAYOUT.navTop + LAYOUT.nav + 2;
  return (
    <View style={{ height: barHeight }} pointerEvents="box-none">
      <Animated.View style={[StyleSheet.absoluteFill, styles.bar, browse]} pointerEvents={isSelecting ? 'none' : 'auto'}>
        <View style={[styles.row, { marginTop: topInset + LAYOUT.navTop }]}>
          <Pressable style={styles.side} accessibilityRole="button" accessibilityLabel="Back" hitSlop={8}>
            <Feather name="chevron-left" size={28} color={COLOR.nav} />
          </Pressable>
          <View style={styles.title} accessibilityRole="header">
            <View style={styles.tray}>
              <View style={styles.trayLid} />
            </View>
            <Text style={styles.titleText}>Someday</Text>
            <Feather name="chevron-down" size={15} color={COLOR.nav} />
          </View>
          <Pressable style={[styles.side, styles.right]} accessibilityRole="button" accessibilityLabel="More" hitSlop={8}>
            <View style={styles.more}><Feather name="chevron-down" size={20} color={COLOR.nav} /></View>
          </Pressable>
        </View>
      </Animated.View>
      <Animated.View style={[styles.row, styles.selectRow, { marginTop: topInset + LAYOUT.navTop }, select]} pointerEvents={isSelecting ? 'box-none' : 'none'}>
        <Pressable style={({ pressed }) => [styles.done, pressed && styles.pressed]} onPress={onDone} accessibilityRole="button" accessibilityLabel="Done selecting">
          <Text style={styles.doneText}>Done</Text>
        </Pressable>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { backgroundColor: COLOR.background },
  row: { height: LAYOUT.nav, flexDirection: 'row', alignItems: 'center', paddingHorizontal: LAYOUT.gutter - 12 },
  selectRow: { position: 'absolute', left: 0, right: 0, justifyContent: 'flex-end', paddingRight: LAYOUT.gutter - 4 },
  side: { width: 80, height: 44, justifyContent: 'center' },
  right: { alignItems: 'flex-end' },
  title: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6 },
  titleText: { ...TYPE.nav, color: COLOR.ink },
  // The source's yellow archive tray, drawn flat as it is there.
  tray: { width: 18, height: 14, borderRadius: 3, backgroundColor: COLOR.tray, marginRight: 2, alignItems: 'center' },
  trayLid: { position: 'absolute', top: 4, width: 8, height: 2, borderRadius: 1, backgroundColor: COLOR.trayLine },
  more: { width: LAYOUT.more, height: LAYOUT.more, borderRadius: LAYOUT.more / 2, borderWidth: 1.5, borderColor: COLOR.nav, alignItems: 'center', justifyContent: 'center' },
  done: { width: LAYOUT.done.width, height: LAYOUT.done.height, borderRadius: LAYOUT.done.radius, backgroundColor: COLOR.blue, alignItems: 'center', justifyContent: 'center' },
  pressed: { opacity: 0.7 },
  doneText: { ...TYPE.done, color: COLOR.white },
});
