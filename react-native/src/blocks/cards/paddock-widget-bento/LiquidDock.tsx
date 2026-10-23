import React, { useEffect } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { BlurView } from 'expo-blur';
import { GlassView, isLiquidGlassAvailable } from 'expo-glass-effect';
import Animated, { Easing, useAnimatedStyle, useSharedValue, withSequence, withSpring, withTiming } from 'react-native-reanimated';
import { DOCK } from './motion';
import { colors, layout, radius, space, symbols, type } from './theme';
import { Sym, type SymbolSpec } from './ui';
import type { Destination } from './StaticViews';
import { selection } from './a11y';

/** iOS 26 Liquid Glass; older systems get a dark blur under a translucent fill. */
const GLASS = isLiquidGlassAvailable();
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

export const DOCK_TABS: { tab: Destination; title: string; symbol: SymbolSpec }[] = [
  { tab: 'home', title: 'Home', symbol: symbols.home },
  { tab: 'calendar', title: 'Calendar', symbol: symbols.calendar },
  { tab: 'results', title: 'Results', symbol: symbols.results },
  { tab: 'circuits', title: 'Circuits', symbol: symbols.circuits },
  { tab: 'customize', title: 'Customize', symbol: symbols.customize },
];

/**
 * The pin's floating dock as Liquid Glass: the feed refracts through the capsule, and a lighter glass lens sits on the
 * active tab. Tapping a tab springs the lens across, stretching it on the way like a drop of glass, with a selection
 * haptic. Tabs are peers, so nothing else slides.
 */
export function LiquidDock({ s, tab, quiet, onSelect }: { s: number; tab: Destination; quiet: boolean; onSelect: (tab: Destination) => void }) {
  const D = layout.dock;
  const index = Math.max(0, DOCK_TABS.findIndex((item) => item.tab === tab));
  const pitch = (D.width - 2 * space.dockPad) / DOCK_TABS.length;
  const lensX = useSharedValue(index);
  const stretch = useSharedValue(1);

  useEffect(() => {
    if (quiet) {
      lensX.set(withTiming(index, { duration: 160, easing: EASE_OUT }));
      return;
    }
    lensX.set(withSpring(index, DOCK.lens));
    stretch.set(withSequence(withTiming(DOCK.stretch, { duration: DOCK.stretchMs, easing: EASE_OUT }), withSpring(1, DOCK.settle)));
  }, [index, quiet, lensX, stretch]);

  const lensStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: lensX.get() * pitch * s }, { scaleX: stretch.get() }, { scaleY: 1 / Math.sqrt(stretch.get()) }],
  }));

  return (
    <View pointerEvents="box-none" style={{ position: 'absolute', left: 0, right: 0, bottom: D.bottom * s, alignItems: 'center' }}>
      <View
        accessibilityRole="tablist"
        style={{ width: D.width * s, height: D.height * s, borderRadius: radius.dock * s, overflow: 'hidden', borderWidth: StyleSheet.hairlineWidth, borderColor: colors.dockStroke }}
      >
        {GLASS ? (
          <GlassView glassEffectStyle="clear" tintColor={colors.dockGlassTint} style={[StyleSheet.absoluteFill, { borderRadius: radius.dock * s }]} />
        ) : (
          <>
            <BlurView tint="systemUltraThinMaterialDark" intensity={80} style={StyleSheet.absoluteFill} />
            <View style={[StyleSheet.absoluteFill, { backgroundColor: colors.dockFallback }]} />
          </>
        )}
        <Animated.View
          pointerEvents="none"
          style={[
            {
              position: 'absolute',
              left: (space.dockPad + (pitch - D.lensWidth) / 2) * s,
              top: space.dockLensInset * s,
              width: D.lensWidth * s,
              height: D.lensHeight * s,
              borderRadius: radius.lens * s,
              overflow: 'hidden',
              borderWidth: StyleSheet.hairlineWidth,
              borderColor: colors.dockLensStroke,
              backgroundColor: colors.dockLens,
            },
            lensStyle,
          ]}
        >
          {GLASS ? <GlassView glassEffectStyle="regular" style={StyleSheet.absoluteFill} /> : null}
        </Animated.View>
        <View style={{ flex: 1, flexDirection: 'row', paddingHorizontal: space.dockPad * s }}>
          {DOCK_TABS.map((item) => {
            const selected = item.tab === tab;
            return (
              <Pressable
                key={item.tab}
                accessibilityRole="tab"
                accessibilityLabel={item.title}
                accessibilityState={{ selected }}
                onPress={() => {
                  if (selected) return;
                  selection();
                  onSelect(item.tab);
                }}
                style={({ pressed }) => ({ width: pitch * s, height: D.height * s, alignItems: 'center', justifyContent: 'center', transform: [{ scale: pressed && !quiet ? DOCK.press : 1 }] })}
              >
                <Sym symbol={item.symbol} size={type.dockIcon * s} color={selected ? colors.dockIconActive : colors.dockIcon} weight={selected ? 'semibold' : 'medium'} />
              </Pressable>
            );
          })}
        </View>
      </View>
    </View>
  );
}
