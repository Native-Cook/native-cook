/**
 * Text that rolls to a new value: the old one rises out and fades, the new one rises in from below (translate and
 * opacity only, never the size). Two slots swap on each change; a monotonic `phase` drives them, so the frame the
 * new text first renders still shows the old one (no flash). Reduce Motion: a cross-fade in place.
 */
import { useEffect, useState, type ReactNode } from 'react';
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import Animated, { ReduceMotion, useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';

import { EASE_OUT, TIMING } from './motion';

type Props = {
  /** What is shown; a change of `id` rolls to the new children. */
  id: string;
  children: ReactNode;
  reduceMotion: boolean;
  /** 'up': the new value comes from below (a number going down rolls up). */
  direction?: 'up' | 'down';
  duration?: number;
  rise?: number;
  align?: 'left' | 'right';
  /** Must give the box a width (and a position): both values are laid out inside it, aligned to one side. */
  style?: StyleProp<ViewStyle>;
};

type Slots = { version: number; a: { id: string; node: ReactNode }; b: { id: string; node: ReactNode } };

export function RollText({ id, children, reduceMotion, direction = 'up', duration = TIMING.roll, rise = TIMING.rollRise, align = 'right', style }: Props) {
  const [slots, setSlots] = useState<Slots>({ version: 0, a: { id, node: children }, b: { id: '', node: null } });
  const current = slots.version % 2 === 0 ? slots.a : slots.b;
  if (current.id !== id) {
    const next = { id, node: children };
    const version = slots.version + 1;
    setSlots(version % 2 === 0 ? { version, a: next, b: slots.b } : { version, a: slots.a, b: next });
  } else if (current.node !== children) {
    // Same value, new element (a parent render): keep the slot's content current without rolling.
    const node = { id, node: children };
    setSlots(slots.version % 2 === 0 ? { ...slots, a: node } : { ...slots, b: node });
  }
  const phase = useSharedValue(slots.version);
  const { version } = slots;
  useEffect(() => {
    phase.set(reduceMotion
      ? withTiming(version, { duration: TIMING.reduced, reduceMotion: ReduceMotion.Never })
      : withTiming(version, { duration, easing: EASE_OUT }));
  }, [duration, phase, reduceMotion, version]);

  const sign = direction === 'up' ? 1 : -1;
  const style0 = useAnimatedStyle(() => slotStyle(phase.get(), version, version % 2 === 0, sign * rise, reduceMotion));
  const style1 = useAnimatedStyle(() => slotStyle(phase.get(), version, version % 2 === 1, sign * rise, reduceMotion));
  const side = align === 'right' ? styles.right : styles.left;
  return (
    <View style={style}>
      <Animated.View style={[styles.over, side, style0]}>{slots.a.node}</Animated.View>
      <Animated.View style={[styles.over, side, style1]}>{slots.b.node}</Animated.View>
    </View>
  );
}

/** A slot is the current one (coming in) or the previous one (going out); p runs 0 → 1 over the roll. */
function slotStyle(phase: number, version: number, isCurrent: boolean, rise: number, reduce: boolean) {
  'worklet';
  const p = Math.min(Math.max(phase - (version - 1), 0), 1);
  if (version === 0) return { opacity: isCurrent ? 1 : 0, transform: [{ translateY: 0 }] };
  // The old value is gone before the new one is half in, so no frame reads as two numbers on top of each other.
  const o = isCurrent ? Math.min(Math.max((p - 0.35) / 0.65, 0), 1) : 1 - Math.min(p / 0.45, 1);
  const y = reduce ? 0 : isCurrent ? (1 - p) * rise : -p * rise;
  return { opacity: o, transform: [{ translateY: y }] };
}

const styles = StyleSheet.create({
  over: { position: 'absolute', top: 0, left: 0, right: 0 },
  right: { alignItems: 'flex-end' },
  left: { alignItems: 'flex-start' },
});
