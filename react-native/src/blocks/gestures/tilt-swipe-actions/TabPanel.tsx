/** The source's pale tab panel and its FAB, which opens Income / Expense / Transfer. */
import { Feather } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { useState, type ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  type DerivedValue, type SharedValue, ReduceMotion, useAnimatedReaction, useAnimatedStyle, useDerivedValue, withSpring, withTiming } from 'react-native-reanimated';

import { QA_STATE, SPRING, TIMING } from './motion';
import { PressScale } from './PressScale';
import { TabIcon } from './TabIcons';
import { COLOR, FLAT, LAYOUT, TYPE } from './theme';

const TABS = [
  { icon: 'pie', label: 'Overview' },
  { icon: 'wallet', label: 'Wallet' },
  { icon: 'person', label: 'Spending' },
] as const;
// The source's menu: three items, each with a tinted round glyph on the right.
const MENU = [
  { label: 'Income', icon: 'plus', tint: COLOR.income },
  { label: 'Expense', icon: 'minus', tint: COLOR.pink },
  { label: 'Transfer', icon: 'arrow-up-right', tint: COLOR.transfer },
] as const;

export function TabPanel({ reduceMotion, tab, onTab, clock, menuShown }: { reduceMotion: boolean; tab: number; onTab: (i: number) => void; clock: SharedValue<number>; menuShown: SharedValue<number> }) {
  const [open, setOpen] = useState(QA_STATE === 'menu');
  const progress = useDerivedValue<number>(() => (reduceMotion
    ? withTiming(open ? 1 : 0, { duration: TIMING.reduced, reduceMotion: ReduceMotion.Never })
    : withSpring(open ? 1 : 0, SPRING.menu)));
  // The list behind the open menu fades (the source's rows read at ~44 %), driven from here.
  useAnimatedReaction(() => progress.get(), (p) => menuShown.set(p));
  // The source's FAB shows a pink +; open, it turns plum.
  const cross = useAnimatedStyle(() => ({ opacity: 1 - Math.min(progress.get(), 1) }));
  const plus = useAnimatedStyle(() => ({ opacity: Math.min(progress.get(), 1) }));
  // A tinted glass lens slides under the active tab (Liquid Glass), on a spring.
  const lensX = useDerivedValue(() => (reduceMotion
    ? withTiming(FLAT.tabs[tab], { duration: TIMING.reduced, reduceMotion: ReduceMotion.Never })
    : withSpring(FLAT.tabs[tab], SPRING.lens)));
  const lens = useAnimatedStyle(() => ({ transform: [{ translateX: lensX.get() - FLAT.panel.left - LAYOUT.lens / 2 }] }));
  // Ambient: a pink glow under the FAB breathes on the loop clock.
  const fabGlow = useAnimatedStyle(() => {
    const b = reduceMotion ? 0.5 : (Math.sin((2 * Math.PI * clock.get()) / TIMING.breath) + 1) / 2;
    return { opacity: 0.35 + 0.35 * b, transform: [{ scale: 0.9 + 0.15 * b }] };
  });
  const scrim = useAnimatedStyle(() => ({ opacity: Math.min(progress.get(), 1) }));
  const menu = useAnimatedStyle(() => ({
    opacity: Math.min(progress.get(), 1),
    transform: reduceMotion ? [] : [{ scale: 0.8 + 0.2 * progress.get() }],
  }));
  return (
    <View style={styles.layer} pointerEvents="box-none">
      {/* Left of the screen's edge (X < 14) the source shows the desk, not the slab. */}
      <View style={styles.edgeMask} pointerEvents="none" />
      <View style={styles.panel}>
        {/* The source's panel edge is soft (a rendered bevel at its resolution): the fill fades in over its top 10 pt. */}
        <LinearGradient colors={PANEL_FADE} locations={[0, 10 / 78]} style={StyleSheet.absoluteFill} pointerEvents="none" />
        <Animated.View style={[styles.lens, lens]} pointerEvents="none">
          <View style={styles.lensEdge} />
        </Animated.View>
        {TABS.map((item, i) => (
          <PressScale
            key={item.icon}
            scale={0.88}
            reduceMotion={reduceMotion}
            style={[styles.tab, { left: FLAT.tabs[i] - FLAT.panel.left - 22 }]}
            accessibilityRole="tab"
            accessibilityLabel={item.label}
            accessibilityState={{ selected: i === tab }}
            onPress={() => onTab(i)}
          >
            <TabIcon kind={item.icon} color={i === tab ? COLOR.panelIconActive : COLOR.panelIcon} />
          </PressScale>
        ))}
      </View>
      <Animated.View style={[styles.scrim, scrim]} pointerEvents={open ? 'auto' : 'none'}>
        <Pressable style={StyleSheet.absoluteFill} onPress={() => setOpen(false)} accessibilityLabel="Close menu" />
      </Animated.View>
      <Animated.View style={[styles.menu, menu]} pointerEvents={open ? 'auto' : 'none'}>
        {MENU.map((item, k) => (
          <MenuRow key={item.label} k={k} progress={progress} reduceMotion={reduceMotion} onPress={() => setOpen(false)}>
            <Text style={styles.menuText}>{item.label}</Text>
            <View style={[styles.menuIcon, { backgroundColor: `${item.tint}1A` }]}>
              <Feather name={item.icon} size={14} color={item.tint} />
            </View>
          </MenuRow>
        ))}
      </Animated.View>
      <Animated.View style={[styles.fabGlow, fabGlow]} pointerEvents="none" />
      <PressScale
        style={styles.fab}
        reduceMotion={reduceMotion}
        onPress={() => setOpen((v) => !v)}
        accessibilityRole="button"
        accessibilityLabel={open ? 'Close menu' : 'Add transaction'}
        accessibilityState={{ expanded: open }}
      >
        <Animated.View style={[styles.fabGlyph, cross]}><Feather name="plus" size={38} color={COLOR.pink} /></Animated.View>
        <Animated.View style={[styles.fabGlyph, plus]}><Feather name="plus" size={38} color={COLOR.menuPlus} /></Animated.View>
      </PressScale>
    </View>
  );
}

// From the slab's own colour at the panel's top (gradient at Y 808), so nothing under the panel shows through.
/** A menu row: it rises 8 pt into place as the menu opens, each one a beat after the last (stagger with intent: top to bottom). */
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

const PANEL_FADE = [COLOR.panelEdge, COLOR.panel] as const;

const styles = StyleSheet.create({
  edgeMask: { position: 'absolute', left: 0, width: FLAT.panel.left, top: FLAT.panel.top - 30, bottom: 0, backgroundColor: COLOR.deskEdge },
  layer: { ...StyleSheet.absoluteFill },
  // The source's pale panel runs from the left edge of the screen to the right, under the FAB.
  panel: {
    // From the screen's left edge: its bottom corner is the screen's own (one corner in the source, not two).
    position: 'absolute', left: FLAT.panel.left, right: 0, top: FLAT.panel.top, bottom: 0,
    borderTopLeftRadius: FLAT.panel.radiusTop, borderBottomLeftRadius: FLAT.panel.radiusBottom, overflow: 'hidden',
  },
  tab: { position: 'absolute', top: FLAT.tabY - FLAT.panel.top - 22, width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  lens: {
    position: 'absolute', top: FLAT.tabY - FLAT.panel.top - LAYOUT.lens / 2, width: LAYOUT.lens, height: LAYOUT.lens, borderRadius: LAYOUT.lens / 2,
    backgroundColor: COLOR.lens, borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.lensRim,
    shadowColor: COLOR.slabShadow, shadowOpacity: 0.18, shadowRadius: 10, shadowOffset: { width: 0, height: 4 },
  },
  lensEdge: { position: 'absolute', left: 10, right: 10, top: 1, height: 1.5, borderRadius: 1, backgroundColor: COLOR.glassEdge },
  fabGlow: {
    position: 'absolute', left: FLAT.fab.left - 14, top: FLAT.fab.top - 6, width: FLAT.fab.size + 28, height: FLAT.fab.size + 28,
    borderRadius: FLAT.fab.radius + 14, backgroundColor: COLOR.pinkGlow, shadowColor: COLOR.pink, shadowOpacity: 1, shadowRadius: 26, shadowOffset: { width: 0, height: 10 },
  },
  fab: {
    position: 'absolute', left: FLAT.fab.left, top: FLAT.fab.top,
    width: FLAT.fab.size, height: FLAT.fab.size, borderRadius: FLAT.fab.radius,
    backgroundColor: COLOR.white, alignItems: 'center', justifyContent: 'center',
    shadowColor: COLOR.slabShadow, shadowOpacity: 0.22, shadowRadius: 18, shadowOffset: { width: 0, height: 8 },
  },
  scrim: { ...StyleSheet.absoluteFill, backgroundColor: COLOR.menuScrim },
  // One white shape that the FAB sits in: it grows out of the FAB's corner, as in the source.
  // Measured on the source's open menu (rectified to the flat app): one white panel over the FAB.
  menu: {
    position: 'absolute', left: FLAT.menu.left, top: FLAT.menu.top, width: FLAT.menu.width, height: FLAT.menu.height,
    borderRadius: FLAT.menu.radius, backgroundColor: COLOR.white, paddingTop: FLAT.menu.padTop, paddingLeft: FLAT.menu.padLeft, paddingRight: FLAT.menu.padRight,
    transformOrigin: 'right bottom',
    shadowColor: COLOR.slabShadow, shadowOpacity: 0.22, shadowRadius: 24, shadowOffset: { width: 0, height: 12 },
  },
  menuItem: { height: FLAT.menu.item },
  menuItemInner: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  menuText: { ...TYPE.menu, color: COLOR.menuInk },
  menuIcon: { width: FLAT.menu.icon, height: FLAT.menu.icon, borderRadius: FLAT.menu.icon / 2, alignItems: 'center', justifyContent: 'center' },
  fabGlyph: { position: 'absolute' },
});
