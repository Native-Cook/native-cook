/**
 * The selection engine on the UI thread: gestures, edge auto-scroll, the demo clock and one frame step of the model.
 * Fingers and the script call the same model worklets; React only hears about what it renders as text (which rows,
 * which phase), never per frame.
 */
import { useCallback, useMemo, useState } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import Animated, {
  scrollTo,
  useAnimatedProps,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useFrameCallback,
  useSharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { FLIGHT, MINUTES } from './episodes';
import {
  PHASE, cloneModel, createModel, dragBegin, dragEnd, dragMove, easeInOut, enter, exit, resetDone, rowAt, script,
  startDownload, step, toggle, type Geometry, type Model,
} from './model';
import { DEMO, LOOP_MS, RESUME_MS, SELECT } from './motion';
import { playSelectHaptic } from './select-haptics';
import { LAYOUT, type Layout } from './theme';

export type Ui = { rows: number[]; done: number[]; phase: number };

type Options = { lay: Layout; autoPlay: boolean; reduced: boolean };

export function useRangeSelect({ lay, autoPlay, reduced }: Options) {
  const listRef = useAnimatedRef<Animated.ScrollView>();
  const model = useSharedValue<Model>(createModel(MINUTES, FLIGHT.minutes, reduced));
  const [ui, setUi] = useState<Ui>({ rows: [], done: [], phase: PHASE.idle });

  const scrollY = useSharedValue(0);
  const viewport = useSharedValue(0);
  const content = useSharedValue(0);
  const lockScroll = useSharedValue(false);
  const fingerV = useSharedValue(0); // the finger's y in the list view (= screen y)
  const touchStartY = useSharedValue(0);
  const lastSpeed = useSharedValue(0);
  const coast = useSharedValue(0);
  const published = useSharedValue(-1);
  // Autoplay hand-over.
  const paused = useSharedValue(!autoPlay);
  const touching = useSharedValue(false);
  const quiet = useSharedValue(0);
  const tidy = useSharedValue(-1);
  const tidyFrom = useSharedValue(0);
  /** The script's clock (s into the loop). Kept outside the model so an idle stretch of the demo sets nothing. */
  const clock = useSharedValue(0);

  const listTop = lay.listTop;
  const geo: Geometry = {
    width: lay.width,
    listTop,
    buttonY: lay.panelTop + LAYOUT.panelHeight - LAYOUT.panelPad - LAYOUT.button / 2,
  };
  const bottomEdge = lay.panelTop - LAYOUT.edge / 2;

  // ——— Helpers (declared before every worklet that calls them) ———
  const listY = (vy: number) => {
    'worklet';
    return vy + scrollY.get() - listTop;
  };

  /** Haptics and the React-side summary go out as soon as the model has them. */
  const flush = (m: Model) => {
    'worklet';
    for (let i = 0; i < m.events.length; i++) scheduleOnRN(playSelectHaptic, m.events[i]);
    m.events = [];
    if (m.version !== published.get()) {
      published.set(m.version);
      const rows: number[] = [];
      const done: number[] = [];
      for (let i = 0; i < m.n; i++) {
        if (m.sel[i] === 1) rows.push(i);
        if (m.done[i] === 1) done.push(i);
      }
      scheduleOnRN(setUi, { rows, done, phase: m.phase });
    }
  };

  const apply = (m: Model) => {
    'worklet';
    flush(m);
    model.set(m);
  };

  const scrollToY = (y: number) => {
    'worklet';
    scrollY.set(y);
    scrollTo(listRef, 0, y, false);
  };

  /** Edge auto-scroll while a finger drags near the header or the panel; the range follows the scrolled rows. */
  const autoScroll = (m: Model, dt: number) => {
    'worklet';
    const y = fingerV.get();
    const zone = LAYOUT.edgeZone;
    let speed = 0;
    if (y > bottomEdge - zone) speed = Math.min((y - (bottomEdge - zone)) / zone, 1);
    else if (y < listTop + zone) speed = -Math.min((listTop + zone - y) / zone, 1);
    lastSpeed.set(speed);
    if (speed === 0) return;
    const max = Math.max(content.get() - viewport.get(), 0);
    const next = Math.min(Math.max(scrollY.get() + speed * SELECT.autoScrollMax * dt, 0), max);
    if (next === scrollY.get()) return;
    scrollToY(next);
    dragMove(m, listY(y));
  };

  /** A finger let go: if the list was auto-scrolling, it keeps that speed and glides to a stop. */
  const release = (m: Model) => {
    'worklet';
    if (m.dragging && lastSpeed.get() !== 0 && !reduced) coast.set(lastSpeed.get() * SELECT.autoScrollMax);
    lastSpeed.set(0);
    dragEnd(m);
    lockScroll.set(false);
  };

  useFrameCallback((frame) => {
    'worklet';
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const cur = model.get();
    const scripting = autoPlay && viewport.get() > 0;
    if (!cur.moving && !cur.dragging && coast.get() === 0 && tidy.get() < 0 && !scripting) return;
    const m = cloneModel(cur);

    if (m.dragging && !m.scriptDown) autoScroll(m, dt);
    if (!m.dragging && coast.get() !== 0) {
      const max = Math.max(content.get() - viewport.get(), 0);
      const next = Math.min(Math.max(scrollY.get() + coast.get() * dt, 0), max);
      scrollToY(next);
      const v = coast.get() * Math.exp(-dt * SELECT.coastDecay);
      coast.set(Math.abs(v) < SELECT.coastStop || next === 0 || next === max ? 0 : v);
    }

    if (scripting) {
      if (paused.get()) {
        m.silent = false;
        if (!touching.get()) quiet.set(quiet.get() + dt);
        const busy = m.dragging || m.phase === PHASE.downloading || m.phase === PHASE.done;
        if (tidy.get() < 0 && quiet.get() >= RESUME_MS / 1000 && !busy) {
          tidy.set(0);
          tidyFrom.set(scrollY.get());
          m.silent = true;
          resetDone(m);
        }
        if (tidy.get() >= 0) {
          tidy.set(tidy.get() + dt);
          scrollToY(tidyFrom.get() * (1 - easeInOut(tidy.get() / DEMO.tidy)));
          if (tidy.get() >= DEMO.tidy) {
            tidy.set(-1);
            clock.set(0);
            m.t = 0;
            paused.set(false);
          }
        }
      } else {
        m.silent = true;
        const t0 = clock.get();
        const t1 = t0 + dt;
        script(m, geo, t0, t1);
        clock.set(t1 >= LOOP_MS / 1000 ? t1 - LOOP_MS / 1000 : t1);
        m.t = clock.get();
      }
    }

    step(m, dt);
    // Nothing moved and nothing was published (the demo between its beats): leave the model as it is, so the rows,
    // the sheet and the gauge do not recompute and redraw an identical frame.
    if (!cur.moving && !m.moving && m.version === cur.version && m.events.length === 0) return;
    apply(m);
  });

  /** A touch anywhere stops the script where it is; the demo picks up again after a quiet spell. */
  const touchStart = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touching.set(true);
      quiet.set(0);
      tidy.set(-1);
      if (paused.get()) return;
      paused.set(true);
      const m = cloneModel(model.get());
      m.silent = false;
      m.ghostOT = 0;
      m.ghostPT = 0;
      if (m.scriptDown) {
        m.scriptDown = false;
        dragEnd(m);
      }
        m.moving = true;
      apply(m);
    });
    // The worklet only touches shared values.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const touchEnd = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touching.set(false);
      quiet.set(0);
    });
    // The worklet only touches shared values.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onScroll = useAnimatedScrollHandler({
    onScroll: (e) => {
      if (!model.get().dragging) scrollY.set(e.contentOffset.y);
    },
  });
  const scrollProps = useAnimatedProps(() => ({ scrollEnabled: !lockScroll.get() }));

  const gesture = useMemo(() => {
    // Hold any row: at 300 ms selection starts and the drag sweeps a range.
    const press = Gesture.Pan()
      .activateAfterLongPress(SELECT.longPress)
      .onStart((e) => {
        const m = cloneModel(model.get());
        if (m.phase === PHASE.downloading || m.phase === PHASE.done) return;
        coast.set(0);
        lockScroll.set(true);
        fingerV.set(e.y);
        const row = rowAt(m, listY(e.y));
        if (m.mode === 0) {
          enter(m, row);
          dragBegin(m, row, 1);
        } else dragBegin(m, row, -1);
        apply(m);
      })
      .onUpdate((e) => {
        fingerV.set(e.y);
        const m = cloneModel(model.get());
        dragMove(m, listY(e.y));
        apply(m);
      })
      .onFinalize(() => {
        const m = cloneModel(model.get());
        release(m);
        apply(m);
      });
    // While selecting, a vertical drag that starts on the number column sweeps at once, no hold needed.
    const column = Gesture.Pan()
      .manualActivation(true)
      .onTouchesDown((e, state) => {
        const touch = e.changedTouches[0];
        const m = model.get();
        if (m.mode !== 1 || m.phase !== PHASE.selecting || touch.x > LAYOUT.textX + 4) {
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
        fingerV.set(touchStartY.get());
        const m = cloneModel(model.get());
        dragBegin(m, rowAt(m, listY(touchStartY.get())), -1);
        apply(m);
      })
      .onUpdate((e) => {
        fingerV.set(e.y);
        const m = cloneModel(model.get());
        dragMove(m, listY(e.y));
        apply(m);
      })
      .onFinalize(() => {
        const m = cloneModel(model.get());
        release(m);
        apply(m);
      });
    const tap = Gesture.Tap().onEnd((e) => {
      const m = cloneModel(model.get());
      if (m.mode !== 1) return;
      toggle(m, rowAt(m, listY(e.y)));
      apply(m);
    });
    return Gesture.Race(column, press, tap);
    // The worklets read shared values only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const select = useCallback(() => scheduleOnUI(() => {
    'worklet';
    const m = cloneModel(model.get());
    if (m.mode === 1) exit(m);
    else enter(m, -1);
    apply(m);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);
  const download = useCallback(() => scheduleOnUI(() => {
    'worklet';
    const m = cloneModel(model.get());
    startDownload(m);
    apply(m);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);
  /** VoiceOver's activate on a row: start selecting with it, or toggle it. */
  const activateRow = useCallback((row: number) => scheduleOnUI(() => {
    'worklet';
    const m = cloneModel(model.get());
    if (m.mode === 0) enter(m, row);
    else toggle(m, row);
    apply(m);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);

  const setViewport = useCallback((h: number) => viewport.set(h), [viewport]);
  const setContent = useCallback((h: number) => content.set(h), [content]);

  return {
    listRef, onScroll, scrollProps, gesture, model, scrollY, ui,
    select, download, activateRow, touchStart, touchEnd, setViewport, setContent,
  };
}
