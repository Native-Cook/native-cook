/**
 * The add button and the selection toolbar are one object: entering selection, the blue circle stretches
 * into the dark toolbar (a container morph: position, size, radius and colour on one spring), the plus
 * fades out and the tools fade in one after another. Leaving runs it backwards. At rest the button breathes.
 */
import { Feather } from '@expo/vector-icons';
import type { ComponentProps } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, { interpolateColor, useAnimatedStyle, useSharedValue, withTiming, type SharedValue } from 'react-native-reanimated';

import { EASE, TIMING } from './motion';
import { COLOR, LAYOUT, TYPE } from './theme';

type Props = {
  shown: SharedValue<number>; // 0 add button … 1 toolbar
  breath: SharedValue<number>; // −1..1, ambient
  trashKick: SharedValue<number>; // 0..1, the trash bounce when rows are deleted
  isSelecting: boolean;
  count: number;
  bottomInset: number;
  reduceMotion: boolean;
  onWhen: () => void;
  onMove: () => void;
  onDelete: () => void;
};

function lerp(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

export function SelectToolbar({ shown, breath, trashKick, isSelecting, count, bottomInset, reduceMotion, onWhen, onMove, onDelete }: Props) {
  const { width } = useWindowDimensions();
  const fab = { left: width - LAYOUT.gutter - LAYOUT.fab, bottom: bottomInset + LAYOUT.fabBottom, w: LAYOUT.fab, h: LAYOUT.fab, r: LAYOUT.fab / 2 };
  const bar = { left: (width - LAYOUT.toolbar.width) / 2, bottom: bottomInset + LAYOUT.toolbar.bottom, w: LAYOUT.toolbar.width, h: LAYOUT.toolbar.height, r: LAYOUT.toolbar.radius };

  // The add button answers on press-in: 0.97 in 100 ms, back in 160 ms (it had no feedback).
  const press = useSharedValue(0);
  const shell = useAnimatedStyle(() => {
    const p = Math.min(Math.max(shown.get(), 0), 1.08);
    const q = reduceMotion ? (p > 0.5 ? 1 : 0) : p;
    const b = reduceMotion ? 0 : breath.get() * (1 - Math.min(p, 1));
    return {
      left: lerp(fab.left, bar.left, q),
      bottom: lerp(fab.bottom, bar.bottom, q),
      width: lerp(fab.w, bar.w, q),
      height: lerp(fab.h, bar.h, q),
      borderRadius: lerp(fab.r, bar.r, Math.min(q, 1)),
      backgroundColor: interpolateColor(Math.min(q, 1), [0, 0.6], [COLOR.blue, COLOR.toolbar]),
      shadowOpacity: 0.3 + 0.12 * b,
      shadowRadius: 12 + 4 * b,
      transform: [{ scale: (1 + 0.025 * b) * (1 - 0.03 * press.get()) }],
    };
  });
  const plus = useAnimatedStyle(() => ({ opacity: 1 - Math.min(shown.get() * 3, 1), transform: [{ rotate: `${45 * Math.min(shown.get(), 1)}deg` }] }));
  const tool = (i: number) => {
    'worklet';
    const start = 0.15 + i * TIMING.toolStagger;
    return Math.min(Math.max((shown.get() - start) / 0.25, 0), 1);
  };
  const t0 = useAnimatedStyle(() => ({ opacity: tool(0), transform: [{ translateY: (1 - tool(0)) * 6 }] }));
  const t1 = useAnimatedStyle(() => ({ opacity: tool(1), transform: [{ translateY: (1 - tool(1)) * 6 }] }));
  const t2 = useAnimatedStyle(() => ({
    opacity: tool(2),
    transform: [{ translateY: (1 - tool(2)) * 6 - 6 * trashKick.get() }, { scale: 1 + 0.35 * trashKick.get() }, { rotate: `${-14 * trashKick.get()}deg` }],
  }));
  const t3 = useAnimatedStyle(() => ({ opacity: tool(3), transform: [{ translateY: (1 - tool(3)) * 6 }] }));
  const none = count === 0;

  return (
    <Animated.View style={[styles.shell, shell]} pointerEvents="box-none">
      {/* The add button: the whole shell is its target while not selecting. */}
      <Pressable style={StyleSheet.absoluteFill} disabled={isSelecting} pressRetentionOffset={16}
        onPressIn={() => press.set(withTiming(1, { duration: 100, easing: EASE.out }))}
        onPressOut={() => press.set(withTiming(0, { duration: 160, easing: EASE.out }))}
        accessibilityRole="button" accessibilityLabel="New to-do" accessibilityElementsHidden={isSelecting}>
        <Animated.View style={[styles.plus, plus]}>
          <Feather name="plus" size={28} color={COLOR.white} />
        </Animated.View>
      </Pressable>
      <View style={styles.tools} pointerEvents={isSelecting ? 'box-none' : 'none'}>
        <Tool style={t0} at={LAYOUT.tools[0]} icon="calendar" label="When" onPress={onWhen} disabled={none} />
        <Tool style={t1} at={LAYOUT.tools[1]} icon="arrow-right" label="Move" onPress={onMove} disabled={none} />
        <Tool style={t2} at={LAYOUT.tools[2]} icon="trash-2" a11y={`Delete ${count}`} onPress={onDelete} disabled={none} />
        <Tool style={t3} at={LAYOUT.tools[3]} icon="more-horizontal" a11y="More actions" onPress={onMove} disabled={none} />
      </View>
    </Animated.View>
  );
}

type ToolProps = {
  style: ComponentProps<typeof Animated.View>['style'];
  at: number; // the tool's centre from the toolbar's left edge
  icon: 'calendar' | 'arrow-right' | 'trash-2' | 'more-horizontal';
  label?: string;
  a11y?: string;
  disabled: boolean;
  onPress: () => void;
};

function Tool({ style, at, icon, label, a11y, disabled, onPress }: ToolProps) {
  return (
    // Each tool is centred where the source puts it.
    <Animated.View style={[styles.slot, { left: at - SLOT / 2 }, style]}>
      <Pressable
        style={({ pressed }) => [styles.tool, (pressed || disabled) && styles.dim]}
        onPress={onPress}
        disabled={disabled}
        accessibilityRole="button"
        accessibilityLabel={a11y ?? label}
        accessibilityState={{ disabled }}
      >
        <Feather name={icon} size={LAYOUT.toolIcon} color={COLOR.toolbarInk} />
        {label && <Text style={styles.toolText}>{label}</Text>}
      </Pressable>
    </Animated.View>
  );
}

const SLOT = 120;

const styles = StyleSheet.create({
  shell: {
    position: 'absolute', overflow: 'hidden',
    shadowColor: '#000000', shadowOffset: { width: 0, height: 8 },
  },
  plus: { position: 'absolute', right: 0, bottom: 0, width: LAYOUT.fab, height: LAYOUT.fab, alignItems: 'center', justifyContent: 'center' },
  // Fixed at the toolbar's full width and pinned right, so the morphing shell clips the tools instead of squeezing them.
  tools: { position: 'absolute', right: 0, bottom: 0, width: LAYOUT.toolbar.width, height: LAYOUT.toolbar.height },
  tool: { height: 44, minWidth: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, paddingHorizontal: 10 },
  slot: { position: 'absolute', top: 0, bottom: 0, width: SLOT, alignItems: 'center', justifyContent: 'center' },
  dim: { opacity: 0.5 },
  toolText: { ...TYPE.tool, color: COLOR.toolbarInk },
});
