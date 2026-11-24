// Tally, a tasks app: the screen, the list and the autoPlay script around the reusable MorphComposer.
import { LinearGradient } from 'expo-linear-gradient';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, useWindowDimensions, View, type GestureResponderEvent } from 'react-native';
import Animated, {
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import { LISTS, SCRIPT, TASKS, type Task } from './data';
import {
  ACCENT,
  ADD_HEIGHT,
  ADD_TOP,
  AMBER,
  CARD,
  CARD_HEIGHT,
  CHIP_GAP,
  CHIP_HEIGHT,
  CHIP_WIDTHS,
  CHIPS_TOP,
  FIELD_HEIGHT,
  FIELD_TOP,
  INK,
  MorphComposer,
  MUTE,
  PAD,
  PAPER,
  useComposerMotion,
  type ComposerDraft,
  type ComposerGeometry,
  springClosed,
  type ComposerMotion,
} from './MorphComposer';
import {
  clamp01,
  COLLAPSE_SPRING,
  CONTENT_DELAY,
  CONTENT_IN,
  CONTENT_OUT,
  easeInOutCubic,
  easeOutCubic,
  FAB_DELAY,
  FAB_SPAN,
  HEIGHT_DELAY,
  HEIGHT_SPRING,
  LAND_DELAY,
  LAND_SPAN,
  LOOP,
  mix,
  PART_DELAY,
  PART_SPRING,
  RESUME_AFTER,
  seg,
  springAt,
  springConfig,
  T,
  WIDTH_SPRING,
} from './motion';

const AnimatedPath = Animated.createAnimatedComponent(Path);

// Layout, in points.
const SIDE = 16;
const HEADER = 118;
const ROW = 64;
const ROW_HEIGHT = 56;
const INSERT = 1; // new tasks land in this slot
const FAB = 60;
const FAB_SIDE = 20;
const FAB_BOTTOM = 12;
const PILL_WIDTH = 188;
const PILL_HEIGHT = 44;
const CARD_MAX = 440;
const CHECK = 26;
const CHECK_LEFT = 14;
const CHECK_LENGTH = 20;
const GHOST = 44;

const ROW_CONFIG = springConfig(PART_SPRING);
const DONE_CONFIG = springConfig({ response: 0.34, ratio: 0.82 });

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

const TAPS = [T.open, T.priority, T.list, T.add, T.check];

function ghostPress(t: number) {
  'worklet';
  let press = 0;
  for (let i = 0; i < TAPS.length; i += 1) {
    const d = t - TAPS[i];
    if (d > -0.1 && d < 0.12) press = Math.max(press, d < 0 ? 1 + d / 0.1 : 1 - d / 0.12);
  }
  return press;
}

function ghostOpacity(t: number) {
  'worklet';
  const a = seg(t, T.ghostIn, 0.2) * (1 - seg(t, T.open + 0.3, 0.22));
  const b = seg(t, T.priority - 0.38, 0.2) * (1 - seg(t, T.add + 0.14, 0.2));
  const c = seg(t, T.check - 0.45, 0.22) * (1 - seg(t, T.check + 0.2, 0.2));
  return Math.max(a, b, c);
}

type Point = { t: number; x: number; y: number };

function ghostAt(path: Point[], t: number, axis: 'x' | 'y') {
  'worklet';
  if (t <= path[0].t) return path[0][axis];
  for (let i = 1; i < path.length; i += 1) {
    if (t <= path[i].t) {
      const u = easeInOutCubic((t - path[i - 1].t) / (path[i].t - path[i - 1].t));
      return mix(u, path[i - 1][axis], path[i][axis]);
    }
  }
  return path[path.length - 1][axis];
}

/** Writes only real changes, so nothing downstream runs while the pose holds still. */
function put(value: SharedValue<number>, next: number) {
  'worklet';
  if (value.get() !== next) value.set(next);
}

/** Every animated value of the composer at loop time t. */
function applyPose(c: ComposerMotion, part: SharedValue<number>, scriptDone: SharedValue<number>, t: number, chars: number) {
  'worklet';
  const on = t >= T.open && t < T.fly + T.flySpan;
  put(c.active, on ? 1 : 0);
  if (on) {
    const since = t - T.open;
    put(c.openW, springAt(since, WIDTH_SPRING.response, WIDTH_SPRING.ratio));
    put(c.openH, springAt(since - HEIGHT_DELAY, HEIGHT_SPRING.response, HEIGHT_SPRING.ratio));
    put(c.collapse, clamp01(springAt(t - T.add - 0.06, COLLAPSE_SPRING.response, COLLAPSE_SPRING.ratio)));
    put(c.content, easeOutCubic(seg(since, CONTENT_DELAY, CONTENT_IN)) * (1 - seg(t, T.add + 0.02, CONTENT_OUT)));
    put(c.fly, easeInOutCubic(seg(t, T.fly, T.flySpan)));
  } else {
    put(c.openW, 0);
    put(c.openH, 0);
    put(c.collapse, 0);
    put(c.content, 0);
    put(c.fly, 0);
  }
  put(c.fabIn, t < T.open ? 1 : t < T.fly + FAB_DELAY ? 0 : Math.min(1.04, springAt(t - T.fly - FAB_DELAY, FAB_SPAN, 0.8)));
  put(c.press, t < T.open + 0.05 ? ghostPress(t) : 0);
  put(c.addPress, t > T.add - 0.2 && t < T.add + 0.2 ? ghostPress(t) : 0);

  const fall = easeInOutCubic(seg(t, T.remove, T.removeSpan));
  put(part, springAt(t - T.fly - PART_DELAY, PART_SPRING.response, PART_SPRING.ratio) * (1 - fall));
  put(c.landed, easeOutCubic(seg(t, T.fly + LAND_DELAY, LAND_SPAN)) * (1 - fall));
  put(scriptDone, t < T.remove + T.removeSpan ? easeInOutCubic(seg(t, T.check, T.checkSpan)) : 0);

  const count = t < T.typeStart || t >= T.fly ? 0 : Math.min(chars, Math.floor((t - T.typeStart) / T.typeStep) + 1);
  put(c.typed, count);
  put(c.today, 1);
  put(c.priority, t >= T.fly ? 0 : easeOutCubic(seg(t, T.priority, 0.22)));
  put(c.listRoll, t >= T.fly ? 0 : easeInOutCubic(seg(t, T.list, 0.26)));
}

function restPose(c: ComposerMotion, part: SharedValue<number>, scriptDone: SharedValue<number>) {
  'worklet';
  c.active.set(0);
  c.openW.set(0);
  c.openH.set(0);
  c.content.set(0);
  c.collapse.set(0);
  c.fly.set(0);
  c.fabIn.set(1);
  c.press.set(0);
  c.addPress.set(0);
  c.landed.set(0);
  c.typed.set(0);
  c.priority.set(0);
  c.listRoll.set(0);
  part.set(0);
  scriptDone.set(0);
}

type RowProps = {
  task: Task;
  index: number;
  slots: SharedValue<number[]>;
  part: SharedValue<number>;
  motion: ComposerMotion;
  external?: SharedValue<number>;
  script?: boolean;
  initialSlot: number;
  width: number;
  onToggle?: (id: string, done: boolean) => void;
};

const TaskRow = memo(function TaskRow({ task, index, slots, part, motion, external, script = false, initialSlot, width, onToggle }: RowProps) {
  const [checked, setChecked] = useState(!!task.done);
  const own = useSharedValue(task.done ? 1 : 0);
  const done = external ?? own;
  const y = useSharedValue(initialSlot * ROW);

  useAnimatedReaction(
    () => (script ? INSERT : slots.get()[index] ?? initialSlot),
    (slot, previous) => {
      if (previous !== null && slot !== previous) y.set(withSpring(slot * ROW, ROW_CONFIG));
    },
  );

  const rowStyle = useAnimatedStyle(() => {
    const slot = script ? INSERT : slots.get()[index] ?? initialSlot;
    const parted = !script && slot >= INSERT ? part.get() * ROW : 0;
    const id = motion.landId.get();
    // The scripted row only stands in for the autoPlay add (landId < 0), never under a real one.
    const opacity = script ? (id < 0 ? motion.landed.get() : 0) : id === index ? motion.landed.get() : 1;
    return { opacity, transform: [{ translateY: y.get() + parted }] };
  });
  const fillStyle = useAnimatedStyle(() => {
    const d = clamp01(done.get());
    return { opacity: d, transform: [{ scale: 0.7 + 0.3 * d }] };
  });
  const strokeProps = useAnimatedProps(() => ({ strokeDashoffset: CHECK_LENGTH * (1 - clamp01(done.get())) }));
  const strikeStyle = useAnimatedStyle(() => ({
    transform: [{ scaleX: clamp01(done.get()) }],
    opacity: clamp01(done.get()),
  }));
  const titleStyle = useAnimatedStyle(() => ({ opacity: 1 - 0.5 * clamp01(done.get()) }));

  const toggle = () => {
    const next = !checked;
    setChecked(next);
    done.set(withSpring(next ? 1 : 0, DONE_CONFIG));
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onToggle?.(task.id, next);
  };

  return (
    <Animated.View
      style={[styles.row, { width }, rowStyle]}
      pointerEvents={script ? 'none' : 'auto'}
      accessibilityElementsHidden={script}
      importantForAccessibility={script ? 'no-hide-descendants' : 'auto'}
    >
      <Pressable
        onPress={script ? undefined : toggle}
        hitSlop={10}
        style={styles.checkHit}
        accessibilityRole="checkbox"
        accessibilityLabel={task.title}
        accessibilityState={{ checked }}
      >
        <View style={styles.checkRing}>
          <Animated.View style={[styles.checkFill, fillStyle]} />
          <Svg width={CHECK} height={CHECK} viewBox="0 0 26 26" style={StyleSheet.absoluteFill}>
            <AnimatedPath
              d="M7.5 13.6 L11.4 17.4 L18.6 9.2"
              fill="none"
              stroke={CARD}
              strokeWidth={2.2}
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeDasharray={CHECK_LENGTH}
              animatedProps={strokeProps}
            />
          </Svg>
        </View>
      </Pressable>
      <View style={styles.rowText}>
        <View style={styles.titleWrap}>
          <Animated.Text style={[styles.rowTitle, titleStyle]} numberOfLines={1}>{task.title}</Animated.Text>
          <Animated.View style={[styles.strike, strikeStyle]} />
        </View>
        <Text style={styles.rowMeta} numberOfLines={1}>{task.meta}</Text>
      </View>
      {task.priority ? <View style={styles.flag} accessibilityLabel="Priority" /> : null}
    </Animated.View>
  );
});

export type QuickAddMorphProps = {
  /** Plays the 9 s loop until a finger takes over; it resumes 2 s after the last touch. */
  autoPlay?: boolean;
  tasks?: Task[];
  lists?: string[];
  onAdd?: (task: Task) => void;
};

export function QuickAddMorph({ autoPlay = true, tasks: initialTasks = TASKS, lists = LISTS, onAdd }: QuickAddMorphProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const playing = autoPlay && !reduced;

  const motion = useComposerMotion();
  const [tasks, setTasks] = useState(initialTasks);
  const [doneCount, setDoneCount] = useState(() => initialTasks.filter(task => task.done).length);
  const [pillTitle, setPillTitle] = useState(SCRIPT.title);

  const slots = useSharedValue<number[]>(initialTasks.map((_, index) => index));
  const pendingSlots = useSharedValue<number[] | null>(null);
  const part = useSharedValue(0);
  const scriptDone = useSharedValue(0);
  const scrollY = useSharedValue(0);
  const focused = useSharedValue(0);

  const clock = useSharedValue(0);
  const mode = useSharedValue(playing ? MODE_AUTO : MODE_MANUAL);
  const touches = useSharedValue(0);
  const seen = useSharedValue(0);
  const idle = useSharedValue(0);
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  const ghostO = useSharedValue(0);
  const ghostS = useSharedValue(0);

  const scrollRef = useAnimatedRef<Animated.ScrollView>();
  const nextId = useRef(0);

  const listTop = insets.top + HEADER;
  const bottom = Math.max(insets.bottom, 16) + FAB_BOTTOM;

  const geometry = useMemo<ComposerGeometry>(() => {
    const fab = { x: width - FAB_SIDE - FAB, y: height - bottom - FAB, width: FAB, height: FAB };
    const cardWidth = Math.min(width - SIDE * 2, CARD_MAX);
    const card = { x: width - SIDE - cardWidth, y: height - bottom - CARD_HEIGHT, width: cardWidth, height: CARD_HEIGHT };
    const pill = {
      x: card.x + (cardWidth - PILL_WIDTH) / 2,
      y: card.y + card.height - PILL_HEIGHT - 14,
      width: PILL_WIDTH,
      height: PILL_HEIGHT,
    };
    return { fab, card, pill, rowX: SIDE, rowWidth: width - SIDE * 2, rowHeight: ROW_HEIGHT };
  }, [bottom, height, width]);

  const ghostPath = useMemo<Point[]>(() => {
    const { fab, card } = geometry;
    const fabX = fab.x + FAB / 2;
    const fabY = fab.y + FAB / 2;
    const chipY = card.y + CHIPS_TOP + CHIP_HEIGHT / 2;
    const priorityX = card.x + PAD + CHIP_WIDTHS[0] + CHIP_GAP + CHIP_WIDTHS[1] / 2;
    const listX = card.x + PAD + CHIP_WIDTHS[0] + CHIP_WIDTHS[1] + CHIP_GAP * 2 + CHIP_WIDTHS[2] / 2;
    const addY = card.y + ADD_TOP + ADD_HEIGHT / 2;
    const addX = card.x + card.width / 2;
    const fieldY = card.y + FIELD_TOP + FIELD_HEIGHT / 2;
    const checkX = SIDE + CHECK_LEFT + CHECK / 2;
    const checkY = listTop + INSERT * ROW + ROW_HEIGHT / 2;
    return [
      { t: T.ghostIn, x: fabX + 26, y: fabY + 34 },
      { t: T.open - 0.08, x: fabX, y: fabY },
      { t: T.open + 0.12, x: fabX, y: fabY },
      { t: T.open + 0.52, x: card.x + 120, y: fieldY },
      { t: T.priority - 0.38, x: priorityX + 16, y: chipY + 26 },
      { t: T.priority - 0.06, x: priorityX, y: chipY },
      { t: T.priority + 0.1, x: priorityX, y: chipY },
      { t: T.list - 0.06, x: listX, y: chipY },
      { t: T.list + 0.1, x: listX, y: chipY },
      { t: T.add - 0.06, x: addX, y: addY },
      { t: T.add + 0.34, x: addX, y: addY },
      { t: T.check - 0.45, x: checkX + 30, y: checkY + 34 },
      { t: T.check - 0.06, x: checkX, y: checkY },
      { t: T.check + 0.4, x: checkX, y: checkY },
    ];
  }, [geometry, listTop]);

  const scriptTask = useMemo<Task>(() => ({ id: 'script', title: SCRIPT.title, meta: SCRIPT.meta, priority: SCRIPT.priority }), []);

  const held = useSharedValue(0);
  const bump = useCallback(() => {
    touches.set(touches.get() + 1);
  }, [touches]);
  const onTouch = useCallback((event: GestureResponderEvent) => {
    held.set(event.nativeEvent.touches.length);
    touches.set(touches.get() + 1);
  }, [held, touches]);

  // A touch mid-loop: an open composer stays open and becomes real; anything else settles back to rest.
  const takeOver = useCallback(() => {
    'worklet';
    const c = motion;
    c.scripted.set(0);
    ghostO.set(withTiming(0, { duration: 120 }));
    c.press.set(0);
    c.addPress.set(0);
    part.set(withSpring(0, ROW_CONFIG));
    c.landed.set(withTiming(0, { duration: 160 }));
    scriptDone.set(withTiming(0, { duration: 160 }));
    const composerOpen = c.active.get() === 1 && c.collapse.get() < 0.05 && c.openH.get() > 0.5;
    if (composerOpen) {
      c.openW.set(withSpring(1, springConfig(WIDTH_SPRING)));
      c.openH.set(withSpring(1, springConfig(HEIGHT_SPRING)));
      c.content.set(withTiming(1, { duration: 160 }));
      // A chip caught mid-change settles to the state the draft adopts, not a half tint.
      c.priority.set(withTiming(c.priority.get() > 0.5 ? 1 : 0, { duration: 160 }));
      c.listRoll.set(withTiming(c.listRoll.get() > 0.5 ? 1 : 0, { duration: 160 }));
      c.adoptTick.set(c.adoptTick.get() + 1);
    } else if (c.active.get() === 1) {
      c.content.set(withTiming(0, { duration: CONTENT_OUT * 1000 }));
      c.collapse.set(withTiming(0, { duration: 160 }));
      c.fly.set(withTiming(0, { duration: 160 }));
      c.openW.set(withSpring(0, springConfig(HEIGHT_SPRING)));
      springClosed(c);
      c.typed.set(0);
      c.priority.set(withTiming(0, { duration: 160 }));
      c.listRoll.set(withTiming(0, { duration: 160 }));
      c.resetTick.set(c.resetTick.get() + 1);
    } else {
      c.fabIn.set(withTiming(1, { duration: 160 }));
      c.typed.set(0);
      c.priority.set(withTiming(0, { duration: 160 }));
      c.listRoll.set(withTiming(0, { duration: 160 }));
      c.resetTick.set(c.resetTick.get() + 1);
    }
  }, [ghostO, motion, part, scriptDone]);

  const closeForReturn = useCallback(() => {
    'worklet';
    const c = motion;
    c.content.set(withTiming(0, { duration: CONTENT_OUT * 1000 }));
    c.openW.set(withSpring(0, springConfig(HEIGHT_SPRING)));
    springClosed(c);
    c.priority.set(withTiming(0, { duration: 160 }));
    c.listRoll.set(withTiming(0, { duration: 160 }));
  }, [motion]);

  const restoreScriptPill = useCallback(() => setPillTitle(SCRIPT.title), []);

  const frameLoop = useFrameCallback(frame => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 48) / 1000;
    if (dt <= 0) return;

    if (touches.get() !== seen.get()) {
      seen.set(touches.get());
      idle.set(0);
      if (mode.get() !== MODE_MANUAL) {
        if (mode.get() === MODE_AUTO) takeOver();
        mode.set(MODE_MANUAL);
      }
    }
    if (!playing) return;

    if (mode.get() === MODE_MANUAL) {
      if (focused.get() === 1 || held.get() > 0) {
        idle.set(0);
        return;
      }
      // A flight in progress finishes first.
      if (motion.fly.get() > 0) return;
      idle.set(idle.get() + dt);
      if (idle.get() >= RESUME_AFTER) {
        if (motion.active.get() === 1) closeForReturn();
        mode.set(MODE_RETURN);
      }
      return;
    }
    if (mode.get() === MODE_RETURN) {
      if (motion.active.get() === 0 && part.get() < 0.01) {
        clock.set(0);
        motion.scripted.set(1);
        motion.resetTick.set(motion.resetTick.get() + 1);
        scheduleOnRN(restoreScriptPill);
        mode.set(MODE_AUTO);
      }
      return;
    }

    let t = clock.get() + dt;
    if (t >= LOOP) t -= LOOP;
    clock.set(t);
    applyPose(motion, part, scriptDone, t, SCRIPT.title.length);
    ghostX.set(ghostAt(ghostPath, t, 'x'));
    ghostY.set(ghostAt(ghostPath, t, 'y'));
    ghostO.set(ghostOpacity(t));
    ghostS.set(ghostPress(t));
  }, playing);

  // The row the pill lands on, on screen.
  useAnimatedReaction(
    () => listTop + INSERT * ROW - scrollY.get(),
    y => motion.rowY.set(y),
  );

  // Rows part once the pill is on its way.
  useAnimatedReaction(
    () => motion.fly.get() > 0.2,
    (past, previous) => {
      if (past && !previous) {
        const next = pendingSlots.get();
        if (next) {
          slots.set(next);
          pendingSlots.set(null);
        }
      }
    },
  );

  const add = useCallback((draft: ComposerDraft) => {
    nextId.current += 1;
    const list = draft.list;
    const task: Task = {
      id: `new-${nextId.current}`,
      title: draft.title,
      meta: draft.today ? `${list} · Today` : list,
      priority: draft.priority,
    };
    const current = slots.get();
    const index = current.length;
    pendingSlots.set([...current.map(slot => (slot >= INSERT ? slot + 1 : slot)), INSERT]);
    motion.landId.set(index);
    setPillTitle(draft.title);
    setTasks(list0 => [...list0, task]);
    scrollRef.current?.scrollTo({ y: 0, animated: true });
    onAdd?.(task);
  }, [motion.landId, onAdd, pendingSlots, scrollRef, slots]);

  const onToggle = useCallback((_id: string, done: boolean) => {
    setDoneCount(count => count + (done ? 1 : -1));
  }, []);

  const onScroll = useAnimatedScrollHandler(event => {
    scrollY.set(event.contentOffset.y);
  });

  const ghostStyle = useAnimatedStyle(() => ({
    opacity: mode.get() === MODE_AUTO ? ghostO.get() * 0.9 : 0,
    transform: [
      { translateX: ghostX.get() - GHOST / 2 },
      { translateY: ghostY.get() - GHOST / 2 },
      { scale: 1 - 0.16 * ghostS.get() },
    ],
  }));

  useEffect(() => {
    frameLoop.setActive(playing);
    if (playing) return;
    // Reduced motion or autoPlay off: the resting screen, nothing scripted.
    const wasScripted = mode.get() === MODE_AUTO;
    mode.set(MODE_MANUAL);
    motion.scripted.set(0);
    ghostO.set(0);
    if (wasScripted) {
      restPose(motion, part, scriptDone);
      motion.resetTick.set(motion.resetTick.get() + 1);
    }
  }, [frameLoop, ghostO, mode, motion, part, playing, scriptDone]);

  const rowWidth = width - SIDE * 2;
  const total = tasks.length;

  return (
    <View style={styles.screen} onTouchStart={onTouch} onTouchEnd={onTouch} onTouchCancel={onTouch}>
      <StatusBar style="dark" />
      <View style={[styles.header, { paddingTop: insets.top + 8 }]}>
        <View style={styles.brandRow}>
          <Text style={styles.brand}>Tally</Text>
          <Text style={styles.count}>{`${doneCount} of ${total} done`}</Text>
        </View>
        <Text style={styles.title}>Today</Text>
        <Text style={styles.date}>Tuesday, November 24</Text>
      </View>

      <Animated.ScrollView
        ref={scrollRef}
        style={[styles.list, { top: listTop }]}
        contentContainerStyle={{ height: (total + 1) * ROW + bottom + FAB + 24 }}
        onScroll={onScroll}
        scrollEventThrottle={16}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        {tasks.map((task, index) => (
          <TaskRow
            key={task.id}
            task={task}
            index={index}
            slots={slots}
            part={part}
            motion={motion}
            initialSlot={index < initialTasks.length ? index : INSERT}
            width={rowWidth}
            onToggle={onToggle}
          />
        ))}
        <TaskRow
          task={scriptTask}
          index={-2}
          slots={slots}
          part={part}
          motion={motion}
          external={scriptDone}
          script
          initialSlot={INSERT}
          width={rowWidth}
        />
      </Animated.ScrollView>

      <LinearGradient
        colors={['rgba(243,239,231,0)', 'rgba(243,239,231,0.82)', PAPER]}
        locations={[0, 0.55, 1]}
        style={[styles.fade, { height: bottom + FAB + 44 }]}
        pointerEvents="none"
      />

      <MorphComposer
        motion={motion}
        geometry={geometry}
        lists={lists}
        scriptTitle={SCRIPT.title}
        pillTitle={pillTitle}
        reducedMotion={reduced}
        onSubmit={add}
        onActivity={bump}
        focused={focused}
        keyboardInset={bottom - 8}
      />

      <Animated.View style={[styles.ghost, ghostStyle]} pointerEvents="none" />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAPER },
  header: { paddingHorizontal: SIDE + 4 },
  brandRow: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' },
  brand: { fontSize: 15, fontWeight: '700', color: ACCENT },
  count: { fontSize: 14, fontWeight: '500', color: MUTE, fontVariant: ['tabular-nums'] },
  title: { marginTop: 2, fontSize: 34, lineHeight: 40, fontWeight: '700', color: INK },
  date: { marginTop: 1, fontSize: 15, color: MUTE },
  list: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  row: {
    position: 'absolute',
    left: SIDE,
    top: 0,
    height: ROW_HEIGHT,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    flexDirection: 'row',
    alignItems: 'center',
    paddingLeft: CHECK_LEFT,
    paddingRight: 16,
    boxShadow: '0 1px 2px rgba(58,44,28,0.07), 0 6px 14px rgba(58,44,28,0.05), inset 0 1px 0 rgba(255,255,255,0.8)',
  },
  checkHit: { width: CHECK, height: CHECK },
  checkRing: {
    width: CHECK,
    height: CHECK,
    borderRadius: CHECK / 2,
    borderWidth: 1.6,
    borderColor: 'rgba(33,30,26,0.22)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkFill: { position: 'absolute', left: -1.6, top: -1.6, width: CHECK, height: CHECK, borderRadius: CHECK / 2, backgroundColor: ACCENT },
  rowText: { flex: 1, marginLeft: 12, minWidth: 0 },
  titleWrap: { alignSelf: 'flex-start', maxWidth: '100%', justifyContent: 'center' },
  rowTitle: { fontSize: 16, fontWeight: '600', color: INK },
  strike: { position: 'absolute', left: 0, right: 0, height: 1.5, backgroundColor: MUTE, transformOrigin: 'left center' },
  rowMeta: { marginTop: 1, fontSize: 13, color: MUTE },
  flag: { width: 8, height: 8, borderRadius: 4, backgroundColor: AMBER, marginLeft: 10 },
  fade: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  ghost: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: GHOST,
    height: GHOST,
    borderRadius: GHOST / 2,
    backgroundColor: 'rgba(120,114,104,0.34)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.72)',
  },
});
