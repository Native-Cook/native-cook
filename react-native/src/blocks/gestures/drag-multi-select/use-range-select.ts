/**
 * Selection engine on the UI thread. A finger and the demo script call the same worklets:
 * enter(row) → dragBegin(row) → dragMove() as `fingerY` changes → dragEnd(), toggle(row), exit().
 * During a drag a row takes the anchor's target state while it is inside [anchor, current] and gets its
 * old state back when it leaves the range, so dragging back undoes.
 */
import { useCallback, useMemo, useState } from 'react';
import { useWindowDimensions } from 'react-native';
import { Gesture } from 'react-native-gesture-handler';
import Animated, {
  ReduceMotion,
  scrollTo,
  useAnimatedProps,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { DEMO, EASE, LOOP_MS, RESUME_MS, SELECT, SPRING, TIMING } from './motion';
import { playSelectHaptic, type SelectHaptic } from './select-haptics';
import { LAYOUT } from './theme';

function ease(u: number): number {
  'worklet';
  const k = Math.min(Math.max(u, 0), 1);
  return (1 - Math.cos(Math.PI * k)) / 2;
}

type Options = {
  bottomInset: number;
  listTop: number; // where the first row starts, in the list's coordinates (rows run under the nav bar)
  count: number; // rows
  width: number;
  autoPlay: boolean;
  reduceMotion: boolean;
  onScriptDelete: () => void; // the demo's finger taps delete
  onScriptRestore: () => void; // the demo puts the deleted rows back before the loop wraps
};

export function useRangeSelect({ bottomInset, listTop, count, width, autoPlay, reduceMotion, onScriptDelete, onScriptRestore }: Options) {
  const listRef = useAnimatedRef<Animated.ScrollView>();
  const { height: windowHeight } = useWindowDimensions();
  const [tally, setTally] = useState({ count: 0, direction: 1 });
  const [isSelecting, setIsSelecting] = useState(false);
  const [selectedRows, setSelectedRows] = useState<number[]>([]);

  const sel = useSharedValue<number[]>(new Array(count).fill(0));
  const mode = useSharedValue(0);
  const selectedCount = useSharedValue(0);
  const scrollY = useSharedValue(0);
  const viewport = useSharedValue(0); // list height
  const content = useSharedValue(0);
  const lockScroll = useSharedValue(false);
  const fingerY = useSharedValue(0); // in list coordinates
  const dragging = useSharedValue(false);
  const anchor = useSharedValue(0);
  const target = useSharedValue(1);
  const current = useSharedValue(-1);
  const base = useSharedValue<number[]>([]);
  const touchStartY = useSharedValue(0);
  // Upgrade.
  const pressRow = useSharedValue(-1); // anticipation: the row under a finger that is about to long-press
  const pressP = useSharedValue(0);
  const lastSpeed = useSharedValue(0); // edge auto-scroll speed, −1..1
  const coast = useSharedValue(0); // pt/s the list keeps after a drag that ends while auto-scrolling
  const breath = useSharedValue(0); // ambient, −1..1
  const clockA = useSharedValue(0);
  const trashKick = useSharedValue(0); // the trash button's bounce on delete

  // Autoplay.
  const t = useSharedValue(0);
  const paused = useSharedValue(!autoPlay);
  const touching = useSharedValue(false);
  const quiet = useSharedValue(0);
  const tidy = useSharedValue(-1);
  const tidyFrom = useSharedValue(0);
  const scriptDown = useSharedValue(false);
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  const ghostO = useSharedValue(0);
  const ghostPress = useSharedValue(0);

  const shown = useDerivedValue(() => (reduceMotion
    ? withTiming(mode.get(), { duration: TIMING.reduced, reduceMotion: ReduceMotion.Never })
    : withSpring(mode.get(), SPRING.toolbar)));
  const navFade = useDerivedValue(() => withTiming(mode.get(), { duration: TIMING.reduced, easing: EASE.out, reduceMotion: ReduceMotion.Never }));

  const haptic = (kind: SelectHaptic, silent: boolean) => {
    'worklet';
    if (!silent) scheduleOnRN(playSelectHaptic, kind);
  };

  const publish = (next: number[]) => {
    'worklet';
    sel.set(next);
    let n = 0;
    const rows: number[] = [];
    for (let i = 0; i < next.length; i++) if (next[i] === 1) { n++; rows.push(i); }
    const before = selectedCount.get();
    selectedCount.set(n);
    scheduleOnRN(setTally, { count: n, direction: n >= before ? 1 : -1 });
    scheduleOnRN(setSelectedRows, rows);
  };

  const rowAt = (y: number) => {
    'worklet';
    const i = Math.floor((y + scrollY.get() - listTop) / LAYOUT.row);
    return Math.min(Math.max(i, 0), sel.get().length - 1);
  };

  const enter = (row: number, silent: boolean) => {
    'worklet';
    if (mode.get() === 1) return;
    mode.set(1);
    scheduleOnRN(setIsSelecting, true);
    const next = sel.get().slice();
    next[row] = 1;
    publish(next);
    haptic('enter', silent);
  };

  const exit = (silent: boolean) => {
    'worklet';
    if (mode.get() === 0) return;
    mode.set(0);
    dragging.set(false);
    scheduleOnRN(setIsSelecting, false);
    publish(new Array(sel.get().length).fill(0));
    haptic('exit', silent);
  };

  const toggle = (row: number, silent: boolean) => {
    'worklet';
    if (mode.get() !== 1) return;
    const next = sel.get().slice();
    next[row] = next[row] === 1 ? 0 : 1;
    publish(next);
    haptic('tick', silent);
  };

  const dragBegin = (row: number, forceTarget: number) => {
    'worklet';
    dragging.set(true);
    anchor.set(row);
    current.set(-1);
    base.set(sel.get().slice());
    target.set(forceTarget >= 0 ? forceTarget : sel.get()[row] === 1 ? 0 : 1);
  };

  const dragMove = (silent: boolean) => {
    'worklet';
    if (!dragging.get()) return;
    const row = rowAt(fingerY.get());
    if (row === current.get()) return;
    current.set(row);
    const from = Math.min(anchor.get(), row);
    const to = Math.max(anchor.get(), row);
    const was = sel.get();
    const next = base.get().slice();
    for (let i = from; i <= to; i++) next[i] = target.get();
    let changed = false;
    for (let i = 0; i < next.length; i++) if (next[i] !== was[i]) changed = true;
    if (!changed) return;
    publish(next);
    haptic('tick', silent);
  };

  const dragEnd = () => {
    'worklet';
    if (dragging.get() && lastSpeed.get() !== 0 && !reduceMotion) {
      // Velocity handoff: let go while the list is auto-scrolling and it keeps gliding.
      coast.set(lastSpeed.get() * SELECT.autoScrollMax);
    }
    lastSpeed.set(0);
    dragging.set(false);
    lockScroll.set(false);
  };

  /** Edge auto-scroll, every frame while a drag holds the finger near the top or the toolbar. */
  const autoScroll = (dt: number, silent: boolean) => {
    'worklet';
    if (!dragging.get()) return;
    const y = fingerY.get();
    const bottomEdge = viewport.get() - bottomInset - LAYOUT.toolbar.height - LAYOUT.toolbar.bottom - 8;
    let speed = 0;
    if (y > bottomEdge - LAYOUT.edge) speed = Math.min((y - (bottomEdge - LAYOUT.edge)) / LAYOUT.edge, 1);
    else if (y < listTop + LAYOUT.edge) speed = -Math.min((listTop + LAYOUT.edge - y) / LAYOUT.edge, 1);
    if (speed !== 0 && lastSpeed.get() === 0) haptic('edge', silent);
    lastSpeed.set(speed);
    if (speed === 0) return;
    const max = Math.max(content.get() - viewport.get(), 0);
    const next = Math.min(Math.max(scrollY.get() + speed * SELECT.autoScrollMax * dt, 0), max);
    if (next === scrollY.get()) return;
    scrollY.set(next);
    scrollTo(listRef, 0, next, false);
    dragMove(silent);
  };

  // ——— Autoplay ———
  const show = (on: boolean) => {
    'worklet';
    ghostO.set(withTiming(on ? 1 : 0, { duration: DEMO.fingerFade * 1000 }));
  };
  const rowY = (row: number) => {
    'worklet';
    return listTop + LAYOUT.row * (row + 0.5) - scrollY.get();
  };
  const anticipate = (row: number) => {
    'worklet';
    if (reduceMotion || mode.get() === 1) return;
    pressRow.set(row);
    pressP.set(0);
    pressP.set(withTiming(1, { duration: SELECT.longPress, easing: EASE.out }));
  };
  const kickTrash = () => {
    'worklet';
    if (reduceMotion) return;
    trashKick.set(withSequence(withTiming(1, { duration: TIMING.trashUp, easing: EASE.out }), withSpring(0, SPRING.trash)));
  };
  const trashPoint = { x: width / 2 + LAYOUT.toolbar.width / 2 - 8 - 44 - 22, y: windowHeight - bottomInset - LAYOUT.toolbar.bottom - LAYOUT.toolbar.height / 2 };

  useFrameCallback((frame) => {
    'worklet';
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    autoScroll(dt, !paused.get());
    // Momentum after an auto-scroll drag: exponential decay.
    if (!dragging.get() && coast.get() !== 0) {
      const max = Math.max(content.get() - viewport.get(), 0);
      const next = Math.min(Math.max(scrollY.get() + coast.get() * dt, 0), max);
      scrollY.set(next);
      scrollTo(listRef, 0, next, false);
      const v = coast.get() * Math.exp(-dt * SELECT.coastDecay);
      coast.set(Math.abs(v) < SELECT.coastStop || next === 0 || next === max ? 0 : v);
    }
    // Ambient: the add button breathes; in the demo on the loop clock so the loop stays seamless.
    clockA.set(autoPlay && !paused.get() ? t.get() : clockA.get() + dt);
    breath.set(reduceMotion ? 0 : Math.sin((2 * Math.PI * clockA.get()) / TIMING.breathPeriod));
    if (!autoPlay || viewport.get() === 0) return;
    if (paused.get()) {
      if (!touching.get()) quiet.set(quiet.get() + dt);
      if (tidy.get() < 0 && quiet.get() >= RESUME_MS / 1000 && !dragging.get()) {
        tidy.set(0);
        tidyFrom.set(scrollY.get());
        exit(true);
      }
      if (tidy.get() >= 0) {
        tidy.set(tidy.get() + dt);
        const y = tidyFrom.get() * (1 - ease(tidy.get() / DEMO.tidy));
        scrollY.set(y);
        scrollTo(listRef, 0, y, false);
        if (tidy.get() >= DEMO.tidy) {
          tidy.set(-1);
          t.set(0);
          paused.set(false);
        }
      }
      return;
    }
    const t0 = t.get();
    const t1 = t0 + dt;
    const cross = (at: number) => t0 < at && t1 >= at;
    const midX = width * 0.42;
    const circleX = width - LAYOUT.circleRight - LAYOUT.bandInset - LAYOUT.circle / 2;

    // 1. Long press, then drag the range down, back up, and down again.
    const h = DEMO.hold;
    if (cross(h.down)) {
      ghostX.set(midX);
      ghostY.set(rowY(h.row));
      show(true);
      ghostPress.set(withTiming(1, { duration: SELECT.longPress }));
      anticipate(h.row);
    }
    if (cross(h.down + SELECT.longPress / 1000)) {
      pressP.set(0);
      enter(h.row, true);
      dragBegin(h.row, 1);
      scriptDown.set(true);
      fingerY.set(rowY(h.row));
    }
    const d = DEMO.drag;
    if (scriptDown.get() && t1 >= d[0].at && t1 < DEMO.release) {
      let y = rowY(d[d.length - 1].row);
      for (let i = 0; i < d.length - 1; i++) {
        if (t1 >= d[i].at && t1 < d[i + 1].at) {
          const u = ease((t1 - d[i].at) / (d[i + 1].at - d[i].at));
          y = rowY(d[i].row) + (rowY(d[i + 1].row) - rowY(d[i].row)) * u;
        }
      }
      fingerY.set(y);
      ghostY.set(y);
      dragMove(true);
    }
    if (cross(DEMO.release)) {
      scriptDown.set(false);
      dragEnd();
      ghostPress.set(withTiming(0, { duration: TIMING.press }));
      show(false);
    }

    // 2. Tap one more row's circle: a second block.
    const tap = DEMO.tap;
    if (cross(tap.at - 0.25)) {
      ghostX.set(circleX);
      ghostY.set(rowY(tap.row));
      show(true);
    }
    if (cross(tap.at)) {
      toggle(tap.row, true);
      show(false);
    }

    // 3. Drag the circles into the toolbar edge and hold: the list scrolls under the finger.
    const e = DEMO.edge;
    const edgeY = viewport.get() - bottomInset - LAYOUT.toolbar.height - LAYOUT.toolbar.bottom - 8 - LAYOUT.edge * 0.3;
    if (cross(e.down)) {
      ghostX.set(circleX);
      ghostY.set(rowY(e.row));
      show(true);
      fingerY.set(rowY(e.row));
      dragBegin(rowAt(rowY(e.row)), -1);
      scriptDown.set(true);
    }
    if (scriptDown.get() && t1 >= e.down && t1 < e.up) {
      const start = listTop + LAYOUT.row * (e.row + 0.5);
      const y = start + (edgeY - start) * ease((t1 - e.down) / (e.reach - e.down));
      fingerY.set(y);
      ghostY.set(y);
      dragMove(true);
    }
    if (cross(e.up)) {
      scriptDown.set(false);
      dragEnd();
      show(false);
    }

    // 4. Done, and the list goes home.
    if (cross(DEMO.done.at - 0.3)) {
      ghostX.set(width - LAYOUT.gutter - LAYOUT.done.width / 2);
      ghostY.set(listTop + LAYOUT.row / 2); // the Done pill sits on the first row's line
      show(true);
    }
    if (cross(DEMO.done.at)) {
      exit(true);
      show(false);
      tidyFrom.set(scrollY.get());
    }
    const home = DEMO.scrollHome;
    if (t1 >= home && t1 < home + TIMING.scrollHome / 1000 + dt) {
      coast.set(0);
      const y = tidyFrom.get() * (1 - ease((t1 - home) / (TIMING.scrollHome / 1000)));
      scrollY.set(y);
      scrollTo(listRef, 0, y, false);
    }

    // 5. Select two rows and delete them: they stream out to the trash (the payoff); later they come back.
    const k = DEMO.remove;
    if (cross(k.down)) {
      ghostX.set(midX);
      ghostY.set(rowY(k.from));
      show(true);
      ghostPress.set(withTiming(1, { duration: SELECT.longPress }));
      anticipate(k.from);
    }
    if (cross(k.down + SELECT.longPress / 1000)) {
      pressP.set(0);
      enter(k.from, true);
      dragBegin(k.from, 1);
      scriptDown.set(true);
      fingerY.set(rowY(k.from));
    }
    if (scriptDown.get() && t1 >= k.down + SELECT.longPress / 1000 && t1 < k.up) {
      const s0 = k.down + SELECT.longPress / 1000;
      const y = rowY(k.from) + (rowY(k.to) - rowY(k.from)) * ease((t1 - s0) / (k.up - 0.1 - s0));
      fingerY.set(y);
      ghostY.set(y);
      dragMove(true);
    }
    if (cross(k.up)) {
      scriptDown.set(false);
      dragEnd();
      ghostPress.set(withTiming(0, { duration: TIMING.press }));
      show(false);
    }
    if (cross(k.trash - 0.3)) {
      ghostX.set(trashPoint.x);
      ghostY.set(trashPoint.y);
      show(true);
    }
    if (cross(k.trash)) {
      show(false);
      kickTrash();
      scheduleOnRN(onScriptDelete);
    }
    if (cross(k.restore)) scheduleOnRN(onScriptRestore);
    t.set(t1 >= LOOP_MS / 1000 ? t1 - LOOP_MS / 1000 : t1);
  });

  const touchStart = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touching.set(true);
      quiet.set(0);
      tidy.set(-1);
      if (paused.get()) return;
      paused.set(true);
      show(false);
      if (scriptDown.get()) {
        scriptDown.set(false);
        dragEnd();
      }
    });
    // show/dragEnd only touch shared values.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const touchEnd = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touching.set(false);
      quiet.set(0);
    });
  }, [quiet, touching]);

  const onScroll = useAnimatedScrollHandler({ onScroll: (e) => { if (!dragging.get()) scrollY.set(e.contentOffset.y); } });
  const scrollProps = useAnimatedProps(() => ({ scrollEnabled: !lockScroll.get() }));

  const gesture = useMemo(() => {
    // Long press on any row: enter selection (or start a range) and keep dragging.
    const press = Gesture.Pan()
      .activateAfterLongPress(SELECT.longPress)
      .onBegin((e) => anticipate(rowAt(e.y)))
      .onStart((e) => {
        pressP.set(0);
        coast.set(0);
        lockScroll.set(true);
        const row = rowAt(e.y);
        fingerY.set(e.y);
        if (mode.get() === 0) {
          enter(row, false);
          dragBegin(row, 1);
        } else dragBegin(row, -1);
      })
      .onUpdate((e) => {
        fingerY.set(e.y);
        dragMove(false);
      })
      .onFinalize(() => {
        pressP.set(withTiming(0, { duration: TIMING.press, easing: EASE.out }));
        dragEnd();
      });
    // In selection, a vertical drag that starts on the circles is a range drag at once.
    const circles = Gesture.Pan()
      .manualActivation(true)
      .onTouchesDown((e, state) => {
        const touch = e.changedTouches[0];
        if (mode.get() !== 1 || touch.x < width - LAYOUT.circleColumn) {
          state.fail();
          return;
        }
        lockScroll.set(true);
        touchStartY.set(touch.y);
      })
      .onTouchesMove((e, state) => {
        if (Math.abs(e.changedTouches[0].y - touchStartY.get()) > SELECT.dragSlop) state.activate();
      })
      .onTouchesUp((_e, state) => state.end())
      .onStart(() => {
        coast.set(0);
        fingerY.set(touchStartY.get());
        dragBegin(rowAt(touchStartY.get()), -1);
      })
      .onUpdate((e) => {
        fingerY.set(e.y);
        dragMove(false);
      })
      .onFinalize(() => dragEnd());
    const tap = Gesture.Tap().onEnd((e) => {
      if (mode.get() === 1) toggle(rowAt(e.y), false);
    });
    return Gesture.Race(circles, press, tap);
    // The worklets read shared values only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [width]);

  const done = useCallback(() => scheduleOnUI(() => {
    'worklet';
    exit(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);

  const setViewport = useCallback((h: number) => viewport.set(h), [viewport]);
  const setContent = useCallback((h: number) => content.set(h), [content]);
  const resetSelection = useCallback((rows: number) => {
    scheduleOnUI(() => {
      'worklet';
      sel.set(new Array(rows).fill(0));
    });
  }, [sel]);

  return {
    listRef, onScroll, scrollProps, gesture, done, touchStart, touchEnd, setViewport, setContent, resetSelection,
    selected: tally.count, direction: tally.direction, selectedRows, isSelecting,
    kickTrash: () => scheduleOnUI(kickTrash),
    values: { sel, mode, shown, navFade, ghostX, ghostY, ghostO, ghostPress, pressRow, pressP, breath, trashKick },
  };
}
