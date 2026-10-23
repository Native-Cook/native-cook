/**
 * The planner on the UI thread: the hold-and-drag gesture, the demo clock and one frame step of the model. Fingers and
 * the script call the same model worklets; React only hears about what it reads out to VoiceOver, never per frame.
 */
import { useCallback, useMemo, useState } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import { useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { playResizeHaptic } from './haptics';
import {
  beginResize, cardAt, cloneModel, createModel, endResize, layout, moveResize, nudgeEnd, pressBegin, pressEnd,
  resetPlan, script, step, type Model,
} from './model';
import { DEMO, LOOP_MS, RESIZE, RESUME_MS } from './motion';
import type { Layout } from './theme';

/** Each card's settled times, for VoiceOver. */
export type Ui = { starts: number[]; ends: number[] };

type Options = { lay: Layout; autoPlay: boolean; reduced: boolean };

export function useDayResize({ lay, autoPlay, reduced }: Options) {
  const geo = { gridTop: lay.gridTop, ppm: lay.ppm, cardX: lay.cardX, cardW: lay.cardW };
  const model = useSharedValue<Model>(createModel(geo, reduced));
  const [ui, setUi] = useState<Ui>(() => {
    const m = createModel(geo, reduced);
    return { starts: m.start, ends: m.end };
  });

  const published = useSharedValue(-1);
  const paused = useSharedValue(!autoPlay);
  const touching = useSharedValue(false);
  const quiet = useSharedValue(0);
  const tidy = useSharedValue(-1);
  const clock = useSharedValue(0); // the script's time, s into the loop

  // ——— Helpers (declared before every worklet that calls them) ———
  /** Haptics and the VoiceOver summary go out as soon as the model has them. */
  const flush = (m: Model) => {
    'worklet';
    for (let i = 0; i < m.events.length; i++) scheduleOnRN(playResizeHaptic, m.events[i]);
    m.events = [];
    if (m.version !== published.get() && m.active < 0) {
      published.set(m.version);
      const [T, L] = layout(m, false);
      scheduleOnRN(setUi, { starts: T, ends: T.map((t, i) => t + L[i]) });
    }
  };

  const apply = (m: Model) => {
    'worklet';
    flush(m);
    model.set(m);
  };

  /**
   * Someone is here: the script stops where it is (a scripted hold is let go silently, its finger fades), the quiet
   * spell starts over, and from now on the model speaks (haptics). Safe to call twice.
   */
  const pauseDemo = (m: Model) => {
    'worklet';
    quiet.set(0);
    tidy.set(-1);
    m.silent = false;
    if (paused.get()) return;
    paused.set(true);
    m.ghostOT = 0;
    m.ghostPT = 0;
    if (m.scriptDown) {
      m.scriptDown = false;
      m.silent = true;
      endResize(m);
      m.silent = false;
    }
    pressEnd(m);
  };

  useFrameCallback((frame) => {
    'worklet';
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const cur = model.get();
    // Only a frame where the script acts or something moves is drawn: a still screen records no picture.
    let m: Model | null = null;
    if (autoPlay) {
      if (paused.get()) {
        // The quiet spell counts only with no finger down and nothing pressed or held.
        if (!touching.get() && cur.active < 0 && cur.pressCard < 0) quiet.set(quiet.get() + dt);
        if (tidy.get() < 0 && quiet.get() >= RESUME_MS / 1000 && cur.active < 0) {
          tidy.set(0);
          m = cloneModel(cur);
          m.silent = true;
          resetPlan(m);
        }
        if (tidy.get() >= 0) {
          tidy.set(tidy.get() + dt);
          if (tidy.get() >= DEMO.tidy) {
            tidy.set(-1);
            clock.set(0);
            paused.set(false);
          }
        }
      } else {
        m = cloneModel(cur);
        m.silent = true;
        const t0 = clock.get();
        const t1 = t0 + dt;
        script(m, t0, t1);
        clock.set(t1 >= LOOP_MS / 1000 ? t1 - LOOP_MS / 1000 : t1);
        if (!cur.moving && !m.moving) return; // the script did nothing and nothing moves
      }
    }
    if (!m) {
      if (!cur.moving) return;
      m = cloneModel(cur);
    }
    step(m, dt);
    apply(m);
  });

  /** A touch anywhere stops the script where it is; the demo picks up again after a quiet spell. */
  const touchStart = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touching.set(true);
      const m = cloneModel(model.get());
      pauseDemo(m);
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

  const gesture = useMemo(() => Gesture.Pan()
    .activateAfterLongPress(RESIZE.holdMs)
    .onBegin((e) => {
      // On the UI thread at touch-down, before RN's onTouchStart arrives: the demo is paused before the press begins,
      // so the late pause finds nothing to undo.
      touching.set(true);
      const m = cloneModel(model.get());
      pauseDemo(m);
      m.fx = e.x;
      m.fy = e.y;
      pressBegin(m, cardAt(m, e.x, e.y));
      apply(m);
    })
    .onStart((e) => {
      const m = cloneModel(model.get());
      beginResize(m, cardAt(m, e.x, e.y), e.y);
      apply(m);
    })
    .onUpdate((e) => {
      const m = cloneModel(model.get());
      m.fx = e.x;
      m.fy = e.y;
      moveResize(m, e.y);
      apply(m);
    })
    .onFinalize(() => {
      touching.set(false);
      quiet.set(0);
      const m = cloneModel(model.get());
      endResize(m);
      pressEnd(m);
      apply(m);
    }),
  // The worklets read shared values only.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  []);

  /** VoiceOver's increment / decrement on a card. */
  const adjust = useCallback((i: number, delta: number) => scheduleOnUI(() => {
    'worklet';
    const m = cloneModel(model.get());
    pauseDemo(m);
    nudgeEnd(m, i, delta);
    apply(m);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);

  return { model, gesture, ui, adjust, touchStart, touchEnd };
}
