/** A to-do list: press and drag down the list to select many rows at once. */
import { useCallback, useEffect, useRef, useState } from 'react';
import { StyleSheet, View, useWindowDimensions } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { FadeIn, FadeInDown, FadeOut, LinearTransition, ReduceMotion, SlideOutRight, useAnimatedStyle, useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { QA_HIDE_FINGER, SPRING, TIMING } from './motion';
import { NavBar } from './NavBar';
import { playSelectHaptic } from './select-haptics';
import { SelectToolbar } from './SelectToolbar';
import { TaskRow } from './TaskRow';
import { TASKS, type Task } from './tasks';
import { COLOR, LAYOUT } from './theme';
import { useRangeSelect } from './use-range-select';

export type DragMultiSelectProps = { autoPlay?: boolean };

export function DragMultiSelect({ autoPlay = false }: DragMultiSelectProps) {
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const reduceMotion = useReducedMotion();
  const [tasks, setTasks] = useState<Task[]>(TASKS);
  // The first row sits on the nav bar's line, hidden under it while browsing (as in the source).
  const listTop = insets.top + LAYOUT.navTop + LAYOUT.nav / 2 - LAYOUT.row / 2;
  const [restored, setRestored] = useState<Set<string>>(() => new Set());
  // The selection the delete acts on, kept in a ref so the demo's scripted tap can reach it.
  const selectedRef = useRef<number[]>([]);
  const deleteRef = useRef<() => void>(() => {});
  const onScriptDelete = useCallback(() => deleteRef.current(), []);
  // The demo puts the deleted rows back where they were, growing in.
  const onScriptRestore = useCallback(() => {
    setTasks((list) => {
      const ids = new Set(list.map((t) => t.id));
      setRestored(new Set(TASKS.filter((t) => !ids.has(t.id)).map((t) => t.id)));
      return TASKS;
    });
  }, []);
  const {
    listRef, onScroll, scrollProps, gesture, done, touchStart, touchEnd, setViewport, setContent, resetSelection, kickTrash,
    selectedRows, isSelecting, values,
  } = useRangeSelect({ bottomInset: insets.bottom, listTop, count: tasks.length, width, autoPlay, reduceMotion, onScriptDelete, onScriptRestore });
  const { sel, mode, shown, navFade, ghostX, ghostY, ghostO, ghostPress, pressRow, pressP, breath, trashKick } = values;
  const leave = done;
  useEffect(() => { selectedRef.current = selectedRows; }, [selectedRows]);
  useEffect(() => { resetSelection(tasks.length); }, [resetSelection, tasks.length]);
  // Payoff: the selected rows stream out to the right, one after another, toward the trash; the gap closes.
  const onDelete = useCallback(() => {
    const gone = new Set(selectedRef.current);
    if (gone.size === 0) return;
    playSelectHaptic('commit');
    kickTrash();
    leave();
    setRestored(new Set());
    setTasks((list) => list.filter((_, i) => !gone.has(i)));
  }, [kickTrash, leave]);
  useEffect(() => { deleteRef.current = onDelete; }, [onDelete]);

  const ghost = useAnimatedStyle(() => ({
    opacity: ghostO.get(),
    transform: [
      { translateX: ghostX.get() - LAYOUT.finger / 2 },
      { translateY: ghostY.get() - LAYOUT.finger / 2 },
      { scale: 1 + 0.25 * ghostPress.get() },
    ],
  }));

  return (
    <GestureHandlerRootView style={styles.root}>
      <View style={styles.root} onTouchStart={touchStart} onTouchEnd={touchEnd} onTouchCancel={touchEnd}>
        <GestureDetector gesture={gesture}>
          <View style={styles.list} collapsable={false} onLayout={(e) => setViewport(e.nativeEvent.layout.height)}>
            <Animated.ScrollView
              ref={listRef}
              animatedProps={scrollProps}
              onScroll={onScroll}
              scrollEventThrottle={16}
              showsVerticalScrollIndicator={false}
              onContentSizeChange={(_w, h) => setContent(h)}
              contentContainerStyle={{ paddingTop: listTop, paddingBottom: insets.bottom + LAYOUT.toolbar.height + 32 }}
            >
              {tasks.map((task, i) => (
                <Animated.View
                  key={task.id}
                  layout={reduceMotion ? undefined : LinearTransition.springify().damping(SPRING.collapse.damping).stiffness(SPRING.collapse.stiffness)}
                  exiting={exitFor(selectedRows.indexOf(i), reduceMotion)}
                  entering={restored.has(task.id) ? enterFor(reduceMotion) : undefined}
                >
                  <TaskRow
                    task={task}
                    index={i}
                    selected={selectedRows.includes(i)}
                    selecting={isSelecting}
                    sel={sel}
                    mode={mode}
                    pressRow={pressRow}
                    pressP={pressP}
                    reduceMotion={reduceMotion}
                  />
                </Animated.View>
              ))}
            </Animated.ScrollView>
          </View>
        </GestureDetector>
        <View style={styles.nav} pointerEvents="box-none">
          <NavBar selecting={navFade} isSelecting={isSelecting} topInset={insets.top} reduceMotion={reduceMotion} onDone={leave} />
        </View>
        <SelectToolbar
          shown={shown}
          breath={breath}
          trashKick={trashKick}
          isSelecting={isSelecting}
          count={selectedRows.length}
          bottomInset={insets.bottom}
          reduceMotion={reduceMotion}
          onWhen={leave}
          onMove={leave}
          onDelete={onDelete}
        />
        {autoPlay && !QA_HIDE_FINGER && <Animated.View style={[styles.ghost, ghost]} pointerEvents="none" />}
      </View>
    </GestureHandlerRootView>
  );
}

function exitFor(order: number, reduceMotion: boolean) {
  if (order < 0) return undefined;
  if (reduceMotion) return FadeOut.duration(TIMING.reduced).reduceMotion(ReduceMotion.Never);
  return SlideOutRight.duration(TIMING.streamOut).delay(order * TIMING.streamStagger);
}

function enterFor(reduceMotion: boolean) {
  if (reduceMotion) return FadeIn.duration(TIMING.reduced).reduceMotion(ReduceMotion.Never);
  return FadeInDown.duration(TIMING.restore).springify().damping(18);
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.background },
  list: { flex: 1 },
  nav: { position: 'absolute', left: 0, right: 0, top: 0 },
  ghost: {
    position: 'absolute', left: 0, top: 0, width: LAYOUT.finger, height: LAYOUT.finger, borderRadius: LAYOUT.finger / 2,
    backgroundColor: COLOR.finger, borderWidth: 1, borderColor: COLOR.fingerEdge,
  },
});
