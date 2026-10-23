/** Habit icons as vector glyphs over the canvas, each riding its bubble's position and size; done, a check in the liquid's ink. */
import { Ionicons } from '@expo/vector-icons';
import { StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle, type SharedValue } from 'react-native-reanimated';

import { COUNT, ENTITIES, QUIET_INDEX, type Sim } from './bubble-sim';
import { HUE, LAYOUT } from './theme';

const BOX = 64;

function BubbleIcon({ i, sim, originX, originY }: { i: number; sim: SharedValue<Sim>; originX: number; originY: number }) {
  const e = ENTITIES[i];
  const h = HUE[e.hue];
  const base = (e.r * 2 * LAYOUT.iconScale) / BOX;
  const icon = useAnimatedStyle(() => {
    const s = sim.get();
    const k = s.r[i] / e.r;
    return {
      // The icon goes under as the liquid rises past it.
      opacity: Math.min(1, s.r[i] / 8) * (1 - s.done[i]) * (s.ring === i ? 1 - 0.6 * s.charge : 1),
      transform: [{ translateX: originX + s.x[i] - BOX / 2 }, { translateY: originY + s.y[i] - BOX / 2 }, { scale: base * k }],
    };
  });
  const check = useAnimatedStyle(() => {
    const s = sim.get();
    const k = s.r[i] / (e.r * 0.62);
    return {
      opacity: s.done[i],
      transform: [{ translateX: originX + s.x[i] - BOX / 2 }, { translateY: originY + s.y[i] - BOX / 2 }, { scale: base * Math.min(1.1, k) * (0.6 + 0.4 * s.done[i]) }],
    };
  });
  return <>
    <Animated.View style={[styles.box, icon]}><Ionicons name={e.icon} size={BOX} color={h.icon} /></Animated.View>
    <Animated.View style={[styles.box, check]}><Ionicons name="checkmark" size={BOX} color={h.ink} /></Animated.View>
  </>;
}

export function BubbleIcons({ sim, originX, originY }: { sim: SharedValue<Sim>; originX: number; originY: number }) {
  const items = [];
  for (let i = 0; i < COUNT; i++) if (i !== QUIET_INDEX) items.push(<BubbleIcon key={ENTITIES[i].id} i={i} sim={sim} originX={originX} originY={originY} />);
  return <View style={StyleSheet.absoluteFill} pointerEvents="none" importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
    {items}
  </View>;
}

const styles = StyleSheet.create({ box: { position: 'absolute', left: 0, top: 0, width: BOX, height: BOX, alignItems: 'center', justifyContent: 'center' } });
