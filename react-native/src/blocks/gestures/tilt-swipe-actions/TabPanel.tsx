/**
 * The tab bar: three line tabs on a slightly deeper paper, a brass dot under the active one, and the ink FAB inside
 * the bar (so the toast never collides with it). The FAB opens Income / Expense / Transfer on a paper card.
 */
import { useState, type ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  type DerivedValue, type SharedValue, ReduceMotion, useAnimatedReaction, useAnimatedStyle, useDerivedValue, withTiming } from 'react-native-reanimated';

import { Glyph, type GlyphName } from './Glyphs';
import { EASE_OUT, QA_STATE, TIMING } from './motion';
import { PressScale } from './PressScale';
import { COLOR, ICON, LAYOUT, SHADOW, TYPE, type Scene } from './theme';

const TABS: { icon: GlyphName; label: string }[] = [
  { icon: 'home', label: 'Home' },
  { icon: 'chart', label: 'Insights' },
  { icon: 'card', label: 'Cards' },
];
const MENU: { label: string; icon: GlyphName }[] = [
  { label: 'Income', icon: 'plus' },
  { label: 'Expense', icon: 'bag' },
  { label: 'Transfer', icon: 'undo' },
];

type Props = { scene: Scene; reduceMotion: boolean; menuShown: SharedValue<number> };

export function TabPanel({ scene, reduceMotion, menuShown }: Props) {
  const [open, setOpen] = useState(QA_STATE === 'menu');
  const progress = useDerivedValue<number>(() => (reduceMotion
    ? withTiming(open ? 1 : 0, { duration: TIMING.reduced, reduceMotion: ReduceMotion.Never })
    : withTiming(open ? 1 : 0, { duration: open ? TIMING.menuIn : TIMING.menuOut, easing: EASE_OUT })));
  useAnimatedReaction(() => progress.get(), (p) => menuShown.set(p));
  const plus = useAnimatedStyle(() => ({ transform: reduceMotion ? [] : [{ rotate: `${45 * Math.min(progress.get(), 1)}deg` }] }));
  const scrim = useAnimatedStyle(() => ({ opacity: Math.min(progress.get(), 1) }));
  const menu = useAnimatedStyle(() => ({
    opacity: Math.min(progress.get(), 1),
    transform: reduceMotion ? [] : [{ translateY: (1 - progress.get()) * 8 }, { scale: 0.96 + 0.04 * progress.get() }],
  }));
  const M = LAYOUT.menu;
  const menuHeight = M.padY * 2 + MENU.length * M.item;
  return (
    <View style={styles.layer} pointerEvents="box-none">
      <Animated.View style={[styles.scrim, scrim]} pointerEvents={open ? 'auto' : 'none'}>
        <Pressable style={StyleSheet.absoluteFill} onPress={() => setOpen(false)} accessibilityLabel="Close menu" />
      </Animated.View>
      <View style={[styles.bar, { top: scene.barTop }]}>
        {TABS.map((item, i) => (
          <PressScale
            key={item.icon}
            reduceMotion={reduceMotion}
            style={[styles.tab, { left: scene.tabs[i] - LAYOUT.tab / 2 }]}
            accessibilityRole="tab"
            accessibilityLabel={item.label}
            accessibilityState={{ selected: i === 0 }}
          >
            <Glyph name={item.icon} color={i === 0 ? COLOR.ink : COLOR.ink3} size={ICON.tab} />
            {i === 0 && <View style={styles.dot} />}
          </PressScale>
        ))}
      </View>
      <Animated.View
        style={[styles.menu, { left: scene.width - LAYOUT.gutter - M.width, top: scene.barTop - 8 - menuHeight, height: menuHeight }, menu]}
        pointerEvents={open ? 'auto' : 'none'}
      >
        {MENU.map((item, k) => (
          <MenuRow key={item.label} k={k} progress={progress} reduceMotion={reduceMotion} onPress={() => setOpen(false)}>
            <Text style={styles.menuText}>{item.label}</Text>
            <Glyph name={item.icon} color={COLOR.ink2} size={ICON.row} />
          </MenuRow>
        ))}
      </Animated.View>
      <PressScale
        style={[styles.fab, { left: scene.fabLeft, top: scene.fabTop + 1 }]}
        reduceMotion={reduceMotion}
        onPress={() => setOpen((v) => !v)}
        accessibilityRole="button"
        accessibilityLabel={open ? 'Close menu' : 'Add transaction'}
        accessibilityState={{ expanded: open }}
      >
        <Animated.View style={plus}><Glyph name="plus" color={COLOR.ivory} size={ICON.fab} stroke={ICON.fabStroke} /></Animated.View>
      </PressScale>
    </View>
  );
}

/** A menu row: it rises 8 pt into place as the menu opens, each one a beat after the last. */
function MenuRow({ k, progress, reduceMotion, onPress, children }: { k: number; progress: DerivedValue<number>; reduceMotion: boolean; onPress: () => void; children: ReactNode }) {
  const style = useAnimatedStyle(() => {
    const u = Math.min(Math.max((progress.get() - k * TIMING.menuStagger) / (1 - 2 * TIMING.menuStagger), 0), 1);
    return { opacity: u, transform: reduceMotion ? [] : [{ translateY: (1 - u) * 8 }] };
  });
  return (
    <PressScale style={styles.menuItem} scale={0.97} reduceMotion={reduceMotion} onPress={onPress} accessibilityRole="menuitem">
      <Animated.View style={[styles.menuItemInner, style]}>{children}</Animated.View>
    </PressScale>
  );
}

const styles = StyleSheet.create({
  layer: { ...StyleSheet.absoluteFill },
  // 88 pt from the bottom, as in the mock; its children sit inside the 1 pt top rule, as CSS places them.
  bar: {
    position: 'absolute', left: 0, right: 0, bottom: 0, backgroundColor: COLOR.tabBar,
    borderTopWidth: 1, borderTopColor: COLOR.rule,
  },
  tab: { position: 'absolute', top: 8, width: LAYOUT.tab, height: LAYOUT.tabHeight, alignItems: 'center', justifyContent: 'center' },
  dot: { position: 'absolute', left: 26, bottom: -4, width: 4, height: 4, borderRadius: 2, backgroundColor: COLOR.brassDot },
  fab: {
    position: 'absolute', width: LAYOUT.fab, height: LAYOUT.fab, borderRadius: LAYOUT.fab / 2,
    backgroundColor: COLOR.ink, alignItems: 'center', justifyContent: 'center', boxShadow: SHADOW.fab,
  },
  scrim: { ...StyleSheet.absoluteFill, backgroundColor: COLOR.menuScrim },
  menu: {
    position: 'absolute', width: LAYOUT.menu.width, borderRadius: LAYOUT.menu.radius, backgroundColor: COLOR.stock,
    borderWidth: 1, borderColor: COLOR.pieceEdge, paddingVertical: LAYOUT.menu.padY, paddingHorizontal: 20,
    transformOrigin: 'right bottom', boxShadow: SHADOW.menu,
  },
  menuItem: { height: LAYOUT.menu.item },
  menuItemInner: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  menuText: { ...TYPE.menu, color: COLOR.ink },
});
