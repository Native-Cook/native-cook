/**
 * Runs the cluster on the UI thread. A finger and the autoplay's scripted finger call the same
 * touchDown / touchMove / touchUp, so the preview shows exactly what a hand does.
 */
import * as Haptics from 'expo-haptics';
import { useCallback, useMemo, useState } from 'react';
import { AccessibilityInfo } from 'react-native';
import { Gesture } from 'react-native-gesture-handler';
import { useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { closeFocus, COUNT, doneCount, ENTITIES, EVENT, initialSim, openFocus, setDone, setTab, stepSim, touchDown, touchMove, touchUp } from './bubble-sim';
import { AUTOPLAY, DETAIL, LOOP_MS, QA_HOLD, QA_HOLD_AT } from './motion';

type Point = { x: number; y: number };
type Options = { originX: number; originY: number; width: number; height: number; points: { back: Point; social: Point; personal: Point }; ready: boolean; autoPlay: boolean; reduceMotion: boolean };

function index(id: string): number {
  'worklet';
  for (let i = 0; i < COUNT; i++) if (ENTITIES[i].id === id) return i;
  return -1;
}

function haptic(kind: number) {
  if (kind === EVENT.tick || kind === EVENT.grab) void Haptics.selectionAsync().catch(() => {});
  else if (kind === EVENT.done) void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else if (kind === EVENT.undo) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function useBubbleCluster({ originX, originY, width, height, points, ready, autoPlay, reduceMotion }: Options) {
  const sim = useSharedValue(initialSim());
  const finger = useSharedValue({ x: 0, y: 0, alpha: 0 });
  const script = useSharedValue({ time: 0, paused: false, realDown: false, quiet: 0, segment: 0, scriptedDown: false });
  const [count, setCount] = useState(() => ({ done: 0, total: ENTITIES.filter((e) => e.set === 0 && !e.quiet).length }));
  const [doneIds, setDoneIds] = useState<string[]>([]);
  const [tab, setTabState] = useState(0);
  const [detail, setDetail] = useState(-1);

  const report = useCallback((done: number, total: number, ids: string[], announce: string) => {
    setCount({ done, total });
    setDoneIds(ids);
    if (announce) AccessibilityInfo.announceForAccessibility(announce);
  }, []);

  /** Sends what happened this frame to React (count, haptics), never anything per frame. */
  const flush = useCallback((scripted: boolean) => {
    'worklet';
    const s = sim.get();
    if (s.events.length === 0) return;
    let counted = false;
    let announce = '';
    for (let e = 0; e < s.events.length; e++) {
      const kind = s.events[e];
      if (kind === EVENT.count) counted = true;
      else if (!scripted) scheduleOnRN(haptic, kind);
      if (kind === EVENT.done) announce = 'Habit completed';
      if (kind === EVENT.undo) announce = 'Habit reopened';
    }
    if (counted) {
      const c = doneCount(s);
      const ids: string[] = [];
      for (let i = 0; i < COUNT; i++) if (s.doneTarget[i] > 0.5) ids.push(ENTITIES[i].id);
      scheduleOnRN(report, c.done, c.total, ids, announce);
    }
    s.events.length = 0;
  }, [report, sim]);

  const act = useCallback((kind: 0 | 1 | 2, x: number, y: number, vx: number, vy: number, scripted: boolean) => {
    'worklet';
    if (!ready) return;
    let tapped = -1;
    sim.modify((s) => {
      'worklet';
      if (kind === 0) touchDown(s, x, y);
      else if (kind === 1) touchMove(s, x, y);
      else tapped = touchUp(s, vx, vy, reduceMotion);
      return s;
    });
    flush(scripted);
    if (tapped >= 0) {
      // As in the source, a tap opens the habit: the bubble moves up and grows, the others leave.
      sim.modify((s) => {
        'worklet';
        openFocus(s, tapped, width / 2 - originX, height * DETAIL.y - originY, DETAIL.radius);
        return s;
      });
      scheduleOnRN(setDetail, tapped);
    }
  }, [flush, height, originX, originY, ready, reduceMotion, sim, width]);

  const closeDetail = useCallback(() => {
    'worklet';
    sim.modify((s) => {
      'worklet';
      closeFocus(s);
      return s;
    });
    scheduleOnRN(setDetail, -1);
  }, [sim]);

  const switchTab = useCallback((next: number) => {
    'worklet';
    sim.modify((s) => {
      'worklet';
      setTab(s, next);
      return s;
    });
    scheduleOnRN(setTabState, next);
    flush(false);
  }, [flush, sim]);

  /** A real touch: stop the script now, lifting its finger if it is down. */
  const touch = useCallback((down: boolean) => {
    'worklet';
    const s = script.get();
    if (down && !s.paused && s.scriptedDown) act(2, 0, 0, 0, 0, true);
    if (down) finger.set({ ...finger.get(), alpha: 0 });
    script.set({ ...s, paused: true, realDown: down, quiet: 0, segment: 0, scriptedDown: false });
  }, [act, finger, script]);

  const runScript = useCallback((dt: number) => {
    'worklet';
    const s = script.get();
    const t = s.time;
    const A = AUTOPLAY;
    const at = (start: number) => t >= start && t < start + A.tapMs;
    const holding = t >= A.holdStart && t < A.holdEnd;
    const segment = holding ? 5 : at(A.tapAt) ? 6 : at(A.openAt) ? 1 : at(A.backAt) ? 2 : at(A.personalAt) ? 3 : at(A.socialAt) ? 4 : 0;
    let down = s.scriptedDown;
    const world = sim.get();
    if (segment !== s.segment) {
      if (down) {
        act(2, 0, 0, 0, 0, true);
        down = false;
      }
      if (segment === 5 || segment === 6) {
        // Hold the star until its glass fills (5), or tap it again to reopen it (6).
        const i = index(A.holdId);
        finger.set({ x: world.x[i], y: world.y[i], alpha: 1 });
        act(0, world.x[i], world.y[i], 0, 0, true);
        down = true;
      } else if (segment === 1) {
        const i = index(A.openId);
        finger.set({ x: world.x[i], y: world.y[i], alpha: 1 });
        act(0, world.x[i], world.y[i], 0, 0, true);
        down = true;
      } else if (segment !== 0) {
        const p = segment === 2 ? points.back : segment === 3 ? points.personal : points.social;
        finger.set({ x: p.x - originX, y: p.y - originY, alpha: 1 });
        if (segment === 2) closeDetail();
        else switchTab(segment === 3 ? 1 : 0);
      } else finger.set({ ...finger.get(), alpha: 0 });
    }
    let next = t + dt;
    if (QA_HOLD !== null && t >= QA_HOLD_AT[QA_HOLD]) next = t;
    if (next >= LOOP_MS) {
      next -= LOOP_MS;
      sim.modify((w) => {
        'worklet';
        w.clock = next;
        return w;
      });
    }
    script.set({ ...s, time: next, segment, scriptedDown: down });
  }, [act, closeDetail, finger, originX, originY, points, script, sim, switchTab]);

  useFrameCallback((frame) => {
    'worklet';
    if (!ready) return;
    const dt = frame.timeSincePreviousFrame ?? 0;
    if (autoPlay) {
      const s = script.get();
      if (s.paused) {
        const quiet = s.realDown ? 0 : s.quiet + dt;
        const w = sim.get();
        const settled = w.held < 0 && w.focus < 0 && w.tab === 0 && doneCount(w).done === 0;
        if (quiet >= AUTOPLAY.resumeDelayMs && settled) script.set({ ...s, paused: false, time: 0, quiet: 0, segment: 0, scriptedDown: false });
        else script.set({ ...s, quiet });
      } else runScript(dt);
    }
    sim.modify((s) => {
      'worklet';
      stepSim(s, dt, reduceMotion);
      return s;
    });
    flush(autoPlay && !script.get().paused);
  });

  const gesture = useMemo(() => Gesture.Pan()
    .enabled(ready)
    .minDistance(0)
    .maxPointers(1)
    .onTouchesDown(() => { touch(true); })
    .onBegin((e) => { act(0, e.absoluteX - originX, e.absoluteY - originY, 0, 0, false); })
    .onUpdate((e) => { act(1, e.absoluteX - originX, e.absoluteY - originY, 0, 0, false); })
    .onEnd((e) => { act(2, 0, 0, e.velocityX, e.velocityY, false); })
    .onFinalize((_e, success) => {
      if (!success) act(2, 0, 0, 0, 0, false);
      touch(false);
    }), [act, originX, originY, ready, touch]);

  const selectTab = useCallback((next: number) => {
    setTabState(next);
    scheduleOnUI(() => {
      'worklet';
      touch(false);
      sim.modify((s) => {
        'worklet';
        setTab(s, next);
        return s;
      });
      flush(false);
    });
  }, [flush, sim, touch]);

  /** Accessible path: complete or reopen a habit without holding. */
  const toggle = useCallback((id: string) => {
    scheduleOnUI(() => {
      'worklet';
      touch(false);
      const i = index(id);
      sim.modify((s) => {
        'worklet';
        setDone(s, i, s.doneTarget[i] < 0.5);
        return s;
      });
      flush(false);
    });
  }, [flush, sim, touch]);

  const back = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touch(false);
      closeDetail();
    });
  }, [closeDetail, touch]);

  return { sim, finger, gesture, count, doneIds, tab, detail, selectTab, toggle, back };
}
