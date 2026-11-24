// The reusable piece: a round + button that morphs into a composer card, and collapses into a pill that flies to a row.
// It owns no list. The parent gives it the landing row's position and receives the draft in onSubmit.
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useState } from 'react';
import { Keyboard, Platform, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  interpolateColor,
  useAnimatedReaction,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import {
  clamp01,
  COLLAPSE_SPRING,
  CONTENT_DELAY,
  CONTENT_IN,
  CONTENT_ITEM,
  CONTENT_OUT,
  CONTENT_RISE,
  CONTENT_STAGGER,
  DRAG_CLOSE_DISTANCE,
  DRAG_CLOSE_VELOCITY,
  DRAG_UP_RUBBER,
  FAB_DELAY,
  FLY_ARC,
  HEIGHT_DELAY,
  HEIGHT_SPRING,
  LAND_DELAY,
  LAND_SPAN,
  mix,
  PLUS_TURN,
  RADIUS,
  seg,
  springConfig,
  T,
  WIDTH_SPRING,
} from './motion';

export type Rect = { x: number; y: number; width: number; height: number };
export type ComposerGeometry = { fab: Rect; card: Rect; pill: Rect; rowX: number; rowWidth: number; rowHeight: number };
export type ComposerDraft = { title: string; today: boolean; priority: boolean; list: string };

export const INK = '#211E1A';
export const CARD = '#FFFDF9';
export const PAPER = '#F3EFE7';
export const ACCENT = '#1F6F5C';
export const MUTE = '#857D71';
export const AMBER = '#C9772A';
const FILL = '#F1ECE3';
const CREAM = '#F7F3EA';

// Inside the card, in points.
export const CARD_HEIGHT = 170;
export const PAD = 16;
export const FIELD_TOP = 14;
export const FIELD_HEIGHT = 40;
export const CHIPS_TOP = 62;
export const CHIP_HEIGHT = 34;
export const CHIP_GAP = 8;
export const CHIP_WIDTHS = [72, 88, 92];
export const ADD_TOP = 108;
export const ADD_HEIGHT = 48;
const ICON = 24;
const ICON_INSET = 18;
const BAR = 16;
const LABEL_LINE = 18;

const WIDTH_CONFIG = springConfig(WIDTH_SPRING);
const HEIGHT_CONFIG = springConfig(HEIGHT_SPRING);
const COLLAPSE_CONFIG = springConfig(COLLAPSE_SPRING);
const CHIP_CONFIG = springConfig({ response: 0.3, ratio: 0.8 });
const FLY_MS = T.flySpan * 1000;

export function useComposerMotion() {
  const active = useSharedValue(0); // 1 while the morphing view exists
  const openW = useSharedValue(0);
  const openH = useSharedValue(0);
  const content = useSharedValue(0);
  const collapse = useSharedValue(0);
  const fly = useSharedValue(0);
  const fabIn = useSharedValue(1);
  const press = useSharedValue(0);
  const addPress = useSharedValue(0);
  const drag = useSharedValue(0);
  const lift = useSharedValue(0);
  const rowY = useSharedValue(0);
  const landed = useSharedValue(0);
  const landId = useSharedValue(-1);
  const typed = useSharedValue(0);
  const scripted = useSharedValue(1);
  const today = useSharedValue(1);
  const priority = useSharedValue(0);
  const listRoll = useSharedValue(0);
  const adoptTick = useSharedValue(0);
  const resetTick = useSharedValue(0);
  return {
    active, openW, openH, content, collapse, fly, fabIn, press, addPress, drag, lift, rowY,
    landed, landId, typed, scripted, today, priority, listRoll, adoptTick, resetTick,
  };
}
export type ComposerMotion = ReturnType<typeof useComposerMotion>;

function light() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** Back to the resting button: called when a flight lands or a close finishes. */
function finishMorph(c: ComposerMotion) {
  'worklet';
  c.active.set(0);
  c.openW.set(0);
  c.openH.set(0);
  c.content.set(0);
  c.collapse.set(0);
  c.fly.set(0);
  c.drag.set(0);
  c.fabIn.set(1);
  c.landId.set(-1);
  c.landed.set(0);
}

/** The height's spring back to the button; the morph ends when it settles. */
export function springClosed(c: ComposerMotion, reduced = false) {
  'worklet';
  if (reduced) {
    c.openH.set(
      withTiming(0, { duration: 180 }, finished => {
        if (finished) finishMorph(c);
      }),
    );
    return;
  }
  c.openH.set(
    withSpring(0, { ...WIDTH_CONFIG, overshootClamping: true }, finished => {
      if (finished) finishMorph(c);
    }),
  );
}

function flyAndFinish(c: ComposerMotion, lead: number, duration: number) {
  'worklet';
  c.fly.set(
    withDelay(lead, withTiming(1, { duration }, finished => {
      if (finished) finishMorph(c);
    })),
  );
}

function Chip({ label, width, on, onPress }: { label: string; width: number; on: SharedValue<number>; onPress: () => void }) {
  const fill = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(clamp01(on.get()), [0, 1], [FILL, ACCENT]),
    transform: [{ scale: 1 + 0.05 * Math.sin(clamp01(on.get()) * Math.PI) }],
  }));
  const ink = useAnimatedStyle(() => ({ color: interpolateColor(clamp01(on.get()), [0, 1], [INK, CREAM]) }));
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={label} hitSlop={4}>
      <Animated.View style={[styles.chip, { width }, fill]}>
        <Animated.Text style={[styles.chipText, ink]} numberOfLines={1}>{label}</Animated.Text>
      </Animated.View>
    </Pressable>
  );
}

function ListChip({ labels, current, roll, width, onPress }: { labels: [string, string]; current: string; roll: SharedValue<number>; width: number; onPress: () => void }) {
  const track = useAnimatedStyle(() => ({ transform: [{ translateY: -clamp01(roll.get()) * LABEL_LINE }] }));
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={`List, ${current}`} hitSlop={4}>
      <View style={[styles.chip, styles.chipList, { width }]}>
        <View style={styles.rollClip}>
          <Animated.View style={track}>
            <View style={styles.rollLine}><Text style={styles.chipText} numberOfLines={1}>{labels[0]}</Text></View>
            <View style={styles.rollLine}><Text style={styles.chipText} numberOfLines={1}>{labels[1]}</Text></View>
          </Animated.View>
        </View>
        <View style={styles.caret} />
      </View>
    </Pressable>
  );
}

export type MorphComposerProps = {
  motion: ComposerMotion;
  geometry: ComposerGeometry;
  lists: string[];
  /** The text autoPlay types into the field. */
  scriptTitle: string;
  /** The title the pill carries while it flies. */
  pillTitle: string;
  reducedMotion?: boolean;
  placeholder?: string;
  /** Called when Add is pressed with a non-empty title; the pill then flies to `motion.rowY`. */
  onSubmit: (draft: ComposerDraft) => void;
  onActivity?: () => void;
  focused: SharedValue<number>;
  /** Distance from the button's bottom edge to the screen's: the keyboard lifts the card by what exceeds it. */
  keyboardInset: number;
};

export function MorphComposer({
  motion: c,
  geometry: g,
  lists,
  scriptTitle,
  pillTitle,
  reducedMotion = false,
  placeholder = 'New task',
  onSubmit,
  onActivity,
  focused,
  keyboardInset,
}: MorphComposerProps) {
  const [title, setTitle] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const [today, setToday] = useState(true);
  const [priority, setPriority] = useState(false);
  const [listIndex, setListIndex] = useState(0);
  const [labels, setLabels] = useState<[string, string]>([lists[0], lists[1] ?? lists[0]]);

  const to = useCallback(
    (value: number, config: { stiffness: number; damping: number; mass: number }) => {
      return reducedMotion ? withTiming(value, { duration: 180 }) : withSpring(value, config);
    },
    [reducedMotion],
  );

  const resetDraft = useCallback(() => {
    setTitle('');
    setIsOpen(false);
    setToday(true);
    setPriority(false);
    setListIndex(0);
    setLabels([lists[0], lists[1] ?? lists[0]]);
  }, [lists]);

  const adopt = useCallback((nextToday: boolean, nextPriority: boolean, nextList: number) => {
    setIsOpen(true);
    setToday(nextToday);
    setPriority(nextPriority);
    setListIndex(nextList);
  }, []);

  // autoPlay types into the value without focusing, so no keyboard shows.
  useAnimatedReaction(
    () => c.typed.get(),
    (count, previous) => {
      if (count !== previous && c.scripted.get() === 1) scheduleOnRN(setTitle, scriptTitle.slice(0, count));
    },
  );
  useAnimatedReaction(
    () => c.resetTick.get(),
    (tick, previous) => {
      if (previous !== null && tick !== previous) scheduleOnRN(resetDraft);
    },
  );
  useAnimatedReaction(
    () => c.adoptTick.get(),
    (tick, previous) => {
      if (previous !== null && tick !== previous) {
        scheduleOnRN(adopt, c.today.get() > 0.5, c.priority.get() > 0.5, c.listRoll.get() > 0.5 ? 1 : 0);
      }
    },
  );

  useEffect(() => {
    const showEvent = Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow';
    const hideEvent = Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide';
    const show = Keyboard.addListener(showEvent, event => {
      c.lift.set(withTiming(Math.max(0, event.endCoordinates.height - keyboardInset), { duration: event.duration || 250 }));
    });
    const hide = Keyboard.addListener(hideEvent, event => {
      c.lift.set(withTiming(0, { duration: event.duration || 250 }));
    });
    return () => {
      show.remove();
      hide.remove();
    };
  }, [c.lift, keyboardInset]);

  const open = useCallback(() => {
    onActivity?.();
    light();
    setIsOpen(true);
    c.active.set(1);
    c.fabIn.set(0);
    c.drag.set(0);
    c.openW.set(to(1, WIDTH_CONFIG));
    c.openH.set(withDelay(reducedMotion ? 0 : HEIGHT_DELAY * 1000, to(1, HEIGHT_CONFIG)));
    c.content.set(withDelay(reducedMotion ? 0 : CONTENT_DELAY * 1000, withTiming(1, { duration: CONTENT_IN * 1000 })));
  }, [c, onActivity, reducedMotion, to]);

  const close = useCallback(() => {
    onActivity?.();
    Keyboard.dismiss();
    setIsOpen(false);
    c.content.set(withTiming(0, { duration: CONTENT_OUT * 1000 }));
    c.drag.set(to(0, HEIGHT_CONFIG));
    c.openW.set(to(0, HEIGHT_CONFIG));
    springClosed(c, reducedMotion);
  }, [c, onActivity, reducedMotion, to]);

  const submit = useCallback(() => {
    const clean = title.trim();
    if (!clean) return;
    light();
    Keyboard.dismiss();
    onSubmit({ title: clean, today, priority, list: lists[listIndex] });
    setIsOpen(false);
    setTitle('');
    setToday(true);
    setPriority(false);
    c.today.set(withTiming(1, { duration: 200 }));
    c.priority.set(withTiming(0, { duration: 200 }));
    c.content.set(withTiming(0, { duration: CONTENT_OUT * 1000 }));
    c.collapse.set(to(1, COLLAPSE_CONFIG));
    const lead = reducedMotion ? 0 : (T.fly - T.add) * 1000;
    flyAndFinish(c, lead, reducedMotion ? 180 : FLY_MS);
    c.landed.set(withDelay(lead + LAND_DELAY * 1000, withTiming(1, { duration: LAND_SPAN * 1000 })));
    c.fabIn.set(withDelay(lead + FAB_DELAY * 1000, to(1, WIDTH_CONFIG)));
  }, [c, listIndex, lists, onSubmit, priority, reducedMotion, title, to, today]);

  const toggleToday = useCallback(() => {
    onActivity?.();
    light();
    const next = !today;
    setToday(next);
    c.today.set(to(next ? 1 : 0, CHIP_CONFIG));
  }, [c.today, onActivity, to, today]);

  const togglePriority = useCallback(() => {
    onActivity?.();
    light();
    const next = !priority;
    setPriority(next);
    c.priority.set(to(next ? 1 : 0, CHIP_CONFIG));
  }, [c.priority, onActivity, priority, to]);

  // The roll has two slots: the next name goes into the hidden one, then the roll moves to it.
  const cycleList = useCallback(() => {
    onActivity?.();
    light();
    const next = (listIndex + 1) % lists.length;
    const showingSecond = c.listRoll.get() > 0.5;
    setListIndex(next);
    setLabels(current => (showingSecond ? [lists[next], current[1]] : [current[0], lists[next]]));
    c.listRoll.set(to(showingSecond ? 0 : 1, CHIP_CONFIG));
  }, [c.listRoll, listIndex, lists, onActivity, to]);

  const pan = Gesture.Pan()
    .enabled(isOpen)
    .activeOffsetY([-8, 8])
    .failOffsetX([-18, 18])
    .onUpdate(event => {
      const y = event.translationY;
      c.drag.set(y > 0 ? y : y * DRAG_UP_RUBBER);
    })
    .onEnd(event => {
      if (event.translationY > DRAG_CLOSE_DISTANCE || event.velocityY > DRAG_CLOSE_VELOCITY) {
        scheduleOnRN(close);
      } else {
        c.drag.set(withSpring(0, { ...HEIGHT_CONFIG, velocity: event.velocityY }));
      }
    });

  const fabStyle = useAnimatedStyle(() => {
    const shown = c.active.get() === 1 ? c.fabIn.get() : 1;
    return {
      opacity: clamp01(shown),
      transform: [{ scale: (0.6 + 0.4 * shown) * (1 - 0.08 * c.press.get()) }],
    };
  });

  const cardStyle = useAnimatedStyle(() => {
    const w = c.openW.get();
    const h = c.openH.get();
    const co = clamp01(c.collapse.get());
    const f = clamp01(c.fly.get());
    const on = c.active.get();
    const bottom = g.fab.y + g.fab.height;

    // The button's rect to the card's: anchored at the bottom right, width and height on their own springs.
    const width0 = mix(w, g.fab.width, g.card.width);
    const height0 = mix(h, g.fab.height, g.card.height);
    const x0 = g.fab.x + g.fab.width - width0;
    const y0 = bottom - height0 - c.lift.get() * clamp01(h) + Math.max(c.drag.get(), -40);
    // The card's rect to the pill's.
    const x1 = mix(co, x0, g.pill.x);
    const y1 = mix(co, y0, g.pill.y);
    const width1 = mix(co, width0, g.pill.width);
    const height1 = mix(co, height0, g.pill.height);
    // The pill's rect to the row's, on a slight bow.
    const x = mix(f, x1, g.rowX) - Math.sin(f * Math.PI) * FLY_ARC;
    const y = mix(f, y1, c.rowY.get());
    const width = mix(f, width1, g.rowWidth);
    const height = mix(f, height1, g.rowHeight);

    const radius = f > 0
      ? mix(f, RADIUS.pill, RADIUS.row)
      : co > 0
        ? mix(co, RADIUS.card, RADIUS.pill)
        : mix(clamp01(h), RADIUS.fab, RADIUS.card);

    const opened = interpolateColor(clamp01(h), [0, 0.6], [INK, CARD]);
    const pill = interpolateColor(co, [0, 1], [opened as string, ACCENT]);
    const color = interpolateColor(seg(f, 0.62, 0.3), [0, 1], [pill as string, CARD]);

    return {
      opacity: on === 1 ? 1 - seg(f, 0.88, 0.12) : 0,
      width,
      height,
      borderRadius: radius,
      backgroundColor: color,
      transform: [{ translateX: x }, { translateY: y }, { scale: 1 - 0.08 * c.press.get() * (1 - clamp01(h)) }],
    };
  });

  const innerStyle = useAnimatedStyle(() => ({ opacity: c.content.get() > 0.001 ? 1 : 0 }));

  const iconStyle = useAnimatedStyle(() => ({
    opacity: 1 - clamp01(c.collapse.get() * 3),
    transform: [{ rotate: `${clamp01(c.openW.get()) * PLUS_TURN}deg` }],
  }));
  const barStyle = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(clamp01(c.openH.get()), [0, 0.6], [CREAM, INK]),
  }));

  const fieldStyle = useAnimatedStyle(() => {
    const u = seg(c.content.get(), CONTENT_STAGGER[0], CONTENT_ITEM);
    return { opacity: u, transform: [{ translateY: (1 - u) * CONTENT_RISE }] };
  });
  const chipsStyle = useAnimatedStyle(() => {
    const u = seg(c.content.get(), CONTENT_STAGGER[1], CONTENT_ITEM);
    return { opacity: u, transform: [{ translateY: (1 - u) * CONTENT_RISE }] };
  });
  const addStyle = useAnimatedStyle(() => {
    const u = seg(c.content.get(), CONTENT_STAGGER[2], CONTENT_ITEM);
    return { opacity: u, transform: [{ translateY: (1 - u) * CONTENT_RISE }, { scale: 1 - 0.04 * c.addPress.get() }] };
  });
  const pillLabelStyle = useAnimatedStyle(() => ({
    opacity: seg(c.collapse.get(), 0.5, 0.4) * (1 - seg(c.fly.get(), 0.66, 0.24)),
  }));

  const canAdd = title.trim().length > 0;

  return (
    <>
      <Animated.View style={[styles.fab, { left: g.fab.x, top: g.fab.y, width: g.fab.width, height: g.fab.height }, fabStyle]}>
        <Pressable
          style={StyleSheet.absoluteFill}
          onPress={open}
          accessibilityRole="button"
          accessibilityLabel="New task"
        >
          <View style={styles.fabIcon}>
            <View style={[styles.bar, styles.barCream]} />
            <View style={[styles.bar, styles.barCream, styles.barTurn]} />
          </View>
        </Pressable>
      </Animated.View>

      <GestureDetector gesture={pan}>
        <Animated.View style={[styles.card, cardStyle]} pointerEvents={isOpen ? 'auto' : 'none'}>
          <Animated.View style={[styles.inner, { width: g.card.width, height: g.card.height }, innerStyle]}>
            <Animated.View style={[styles.fieldRow, fieldStyle]}>
              <TextInput
                value={title}
                onChangeText={value => {
                  onActivity?.();
                  setTitle(value);
                }}
                onFocus={() => focused.set(1)}
                onBlur={() => focused.set(0)}
                placeholder={placeholder}
                placeholderTextColor={MUTE}
                style={styles.input}
                returnKeyType="done"
                onSubmitEditing={submit}
                maxLength={40}
                accessibilityLabel="Task title"
              />
            </Animated.View>
            <Animated.View style={[styles.chips, chipsStyle]}>
              <Chip label="Today" width={CHIP_WIDTHS[0]} on={c.today} onPress={toggleToday} />
              <Chip label="Priority" width={CHIP_WIDTHS[1]} on={c.priority} onPress={togglePriority} />
              <ListChip labels={labels} current={lists[listIndex]} roll={c.listRoll} width={CHIP_WIDTHS[2]} onPress={cycleList} />
            </Animated.View>
            <Animated.View style={[styles.addWrap, addStyle]}>
              <Pressable
                onPress={submit}
                disabled={!canAdd}
                accessibilityRole="button"
                accessibilityLabel="Add task"
                accessibilityState={{ disabled: !canAdd }}
                style={({ pressed }) => [styles.add, { opacity: canAdd ? (pressed ? 0.85 : 1) : 0.42 }]}
              >
                <Text style={styles.addText}>Add</Text>
              </Pressable>
            </Animated.View>
          </Animated.View>

          <Animated.View style={[styles.pillLabel, pillLabelStyle]} pointerEvents="none">
            <Text style={styles.pillText} numberOfLines={1}>{pillTitle}</Text>
          </Animated.View>

          <Animated.View style={[styles.icon, iconStyle]} pointerEvents="none">
            <Animated.View style={[styles.bar, barStyle]} />
            <Animated.View style={[styles.bar, styles.barTurn, barStyle]} />
          </Animated.View>
          {isOpen ? (
            <Pressable style={styles.closeHit} onPress={close} accessibilityRole="button" accessibilityLabel="Close" hitSlop={8} />
          ) : null}
        </Animated.View>
      </GestureDetector>
    </>
  );
}

const styles = StyleSheet.create({
  fab: {
    position: 'absolute',
    borderRadius: RADIUS.fab,
    borderCurve: 'continuous',
    backgroundColor: INK,
    boxShadow: '0 1px 2px rgba(33,30,26,0.28), 0 10px 22px rgba(33,30,26,0.24), inset 0 1px 0 rgba(255,255,255,0.14)',
  },
  fabIcon: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  card: {
    position: 'absolute',
    left: 0,
    top: 0,
    overflow: 'hidden',
    borderCurve: 'continuous',
    boxShadow: '0 1px 3px rgba(33,30,26,0.14), 0 16px 36px rgba(33,30,26,0.20)',
  },
  inner: { position: 'absolute', right: 0, bottom: 0 },
  fieldRow: { position: 'absolute', left: PAD, right: PAD + ICON + 6, top: FIELD_TOP, height: FIELD_HEIGHT, justifyContent: 'center' },
  input: { height: FIELD_HEIGHT, padding: 0, fontSize: 19, fontWeight: '600', color: INK },
  chips: { position: 'absolute', left: PAD, right: PAD, top: CHIPS_TOP, height: CHIP_HEIGHT, flexDirection: 'row', gap: CHIP_GAP },
  chip: {
    height: CHIP_HEIGHT,
    borderRadius: CHIP_HEIGHT / 2,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: FILL,
  },
  chipList: { flexDirection: 'row', gap: 6, paddingHorizontal: 10 },
  chipText: { fontSize: 14, fontWeight: '600', color: INK },
  rollClip: { height: LABEL_LINE, overflow: 'hidden', flexShrink: 1 },
  rollLine: { height: LABEL_LINE, justifyContent: 'center' },
  caret: {
    width: 6,
    height: 6,
    marginTop: -3,
    borderRightWidth: 1.6,
    borderBottomWidth: 1.6,
    borderColor: MUTE,
    transform: [{ rotate: '45deg' }],
  },
  addWrap: { position: 'absolute', left: PAD, right: PAD, top: ADD_TOP, height: ADD_HEIGHT },
  add: {
    flex: 1,
    borderRadius: ADD_HEIGHT / 2,
    borderCurve: 'continuous',
    backgroundColor: ACCENT,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(20,70,58,0.30), inset 0 1px 0 rgba(255,255,255,0.16)',
  },
  addText: { color: CREAM, fontSize: 17, fontWeight: '600' },
  pillLabel: { ...StyleSheet.absoluteFill, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 16 },
  pillText: { color: CREAM, fontSize: 15, fontWeight: '600' },
  icon: {
    position: 'absolute',
    top: ICON_INSET,
    right: ICON_INSET,
    width: ICON,
    height: ICON,
    alignItems: 'center',
    justifyContent: 'center',
  },
  bar: { position: 'absolute', width: BAR, height: 2.4, borderRadius: 1.2 },
  barCream: { backgroundColor: CREAM },
  barTurn: { transform: [{ rotate: '90deg' }] },
  closeHit: { position: 'absolute', top: 8, right: 8, width: 44, height: 44 },
});
