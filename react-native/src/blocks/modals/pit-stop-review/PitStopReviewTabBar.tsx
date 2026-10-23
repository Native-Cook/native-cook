import React, { useEffect } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { BlurView } from 'expo-blur';
import { GlassView, isLiquidGlassAvailable } from 'expo-glass-effect';
import { LinearGradient } from 'expo-linear-gradient';
import * as Haptics from 'expo-haptics';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, useAnimatedStyle, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { Sym, type SymbolSpec } from './PitStopReviewBento';
import { tabBar as TB } from './theme';

/**
 * iOS 26 Liquid Glass tab bar: a static regular-glass capsule that refracts the feed scrolling under it, and a
 * lens highlight (a plain view moved by transform only, so the glass never re-lays out) that springs to the active tab. Dragging along the bar lifts the lens (it swells like
 * a magnifier) and it follows the finger; on release it snaps to the nearest tab and selects it.
 * Older systems get a blur under a translucent fill and the same lens drawn as a light pill.
 */
const GLASS = isLiquidGlassAvailable();
/** Lens spring and lift (Reanimated duration/dampingRatio springs). */
export const LENS = {
  /** Same lens spring as the sibling Paddock dock, so the app's tab bars move alike. */
  spring: { duration: 420, dampingRatio: 0.72 },
  /** Reduce Motion: a short ease-out slide, no swell or stretch. */
  quietMs: 160,
  lift: 1.12,
  liftIn: 160,
  liftOut: 260,
  /** Stretch along the travel while the lens moves between tabs. */
  stretch: 0.14,
} as const;

export type TabKey = 'Home' | 'Calendar' | 'Results' | 'Circuits' | 'Customize';
export const TABS: TabKey[] = ['Home', 'Calendar', 'Results', 'Circuits', 'Customize'];
/** The sibling Paddock dock's symbols (same app), fitted like SwiftUI's font-sized images. */
const ICONS: Record<TabKey, SymbolSpec> = {
  Home: { ios: 'house.fill', fallback: 'home', fit: 1.4 },
  Calendar: { ios: 'calendar', fallback: 'calendar-month', fit: 1.35 },
  Results: { ios: 'trophy', fallback: 'trophy-outline', fit: 1.4 },
  Circuits: { ios: 'globe', fallback: 'web', fit: 1.35 },
  Customize: { ios: 'plus.rectangle.on.rectangle', fallback: 'card-plus-outline', fit: 1.4 },
};

type Props = { active: TabKey; onSelect: (tab: TabKey) => void; scale: number; disabled: boolean; quiet: boolean };
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

export function PitStopReviewTabBar({ active, onSelect, scale, disabled, quiet }: Props) {
  const width = TB.width * scale, height = TB.height * scale, pad = TB.padding * scale;
  const cell = (width - pad * 2) / TABS.length;
  const index = TABS.indexOf(active);
  const x = useSharedValue(index * cell);
  const lift = useSharedValue(1);
  const dragging = useSharedValue(false);

  useEffect(() => {
    if (dragging.get()) return;
    x.set(quiet ? withTiming(index * cell, { duration: LENS.quietMs, easing: EASE_OUT }) : withSpring(index * cell, LENS.spring));
  }, [index, cell, x, dragging, quiet]);

  const commit = (i: number) => { void Haptics.selectionAsync(); onSelect(TABS[i]); };
  const pan = Gesture.Pan()
    .enabled(!disabled)
    .activeOffsetX([-8, 8])
    .onBegin(() => { 'worklet'; if (!quiet) lift.set(withTiming(LENS.lift, { duration: LENS.liftIn, easing: EASE_OUT })); })
    .onStart(() => { 'worklet'; dragging.set(true); })
    .onUpdate((e) => { 'worklet'; x.set(Math.min(cell * (TABS.length - 1), Math.max(0, e.x - pad - cell / 2))); })
    .onEnd(() => {
      'worklet';
      const i = Math.round(x.get() / cell);
      x.set(withSpring(i * cell, LENS.spring));
      scheduleOnRN(commit, i);
    })
    .onFinalize(() => { 'worklet'; dragging.set(false); lift.set(withSpring(1, LENS.spring)); });

  const lensStyle = useAnimatedStyle(() => {
    // Stretch with distance from the nearest cell (travel), swell with the lift.
    const travel = quiet ? 0 : Math.min(1, 2 * Math.abs(x.get() / cell - Math.round(x.get() / cell)));
    const l = quiet ? 1 : lift.get();
    return { transform: [{ translateX: x.get() }, { scaleX: l * (1 + LENS.stretch * travel) }, { scaleY: l * (1 - LENS.stretch * 0.4 * travel) }] };
  }, [quiet]);

  return (
    <GestureDetector gesture={pan}>
      <View style={[styles.bar, { width, height, borderRadius: height / 2, bottom: TB.bottom * scale }]} accessibilityRole="tablist">
        {GLASS ? (
          <GlassView glassEffectStyle="regular" colorScheme="dark" tintColor="rgba(16,14,18,0.42)" style={[StyleSheet.absoluteFill, { borderRadius: height / 2 }]} />
        ) : (
          <>
            <BlurView intensity={38} tint="dark" style={[StyleSheet.absoluteFill, { borderRadius: height / 2, overflow: 'hidden' }]} />
            <View style={[StyleSheet.absoluteFill, styles.fallback, { borderRadius: height / 2 }]} />
          </>
        )}
        <Animated.View pointerEvents="none" style={[styles.lens, { left: pad + (cell - TB.lensWidth * scale) / 2, top: (height - TB.lensHeight * scale) / 2, width: TB.lensWidth * scale, height: TB.lensHeight * scale, borderRadius: (TB.lensHeight * scale) / 2 }, lensStyle]}>
          {/* A plain highlight moved by transform only: a moving GlassView re-renders the glass every frame. */}
          <View style={[StyleSheet.absoluteFill, { borderRadius: (TB.lensHeight * scale) / 2, backgroundColor: TB.lensFill }]} />
          {/* Specular rim: a bright top edge fading down, like light caught by the lens. */}
          <LinearGradient pointerEvents="none" colors={['rgba(255,255,255,0.30)', 'rgba(255,255,255,0.04)', 'rgba(255,255,255,0.10)']} locations={[0, 0.5, 1]} style={[StyleSheet.absoluteFill, styles.rim, { borderRadius: (TB.lensHeight * scale) / 2 }]} />
        </Animated.View>
        <View style={[styles.row, { paddingHorizontal: pad }]}>
          {TABS.map((tab) => {
            const selected = tab === active;
            return (
              <Pressable key={tab} disabled={disabled} accessibilityRole="tab" accessibilityLabel={tab} accessibilityState={{ selected }} onPress={() => commit(TABS.indexOf(tab))} style={[styles.cell, { width: cell }]}>
                <Sym symbol={ICONS[tab]} size={TB.icon * scale} color={TB.iconColor} />
              </Pressable>
            );
          })}
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  bar: { position: 'absolute', alignSelf: 'center', borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(255,255,255,0.14)' },
  fallback: { backgroundColor: 'rgba(15,13,16,0.68)' },
  lens: { position: 'absolute', overflow: 'hidden' },
  rim: { opacity: 0.6 },
  row: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, flexDirection: 'row', alignItems: 'center' },
  cell: { height: '100%', alignItems: 'center', justifyContent: 'center' },
});
