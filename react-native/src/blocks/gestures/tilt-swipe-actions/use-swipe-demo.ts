/**
 * Autoplay: a scripted finger writes ScriptTouch events that the named row handles exactly as it
 * handles a real pan. Any real touch stops it; it starts again from rest RESUME_MS after the last one.
 */
import { useCallback } from 'react';
import { useFrameCallback, useSharedValue, withTiming, type SharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { DEMO, LOOP_MS, QA_HOLD_AT, RESUME_MS } from './motion';
import type { ScriptTouch } from './SwipeRow';
import { LAYOUT } from './theme';
import { SPENDING } from './transactions';

function ease(u: number): number {
  'worklet';
  const k = Math.min(Math.max(u, 0), 1);
  return (1 - Math.cos(Math.PI * k)) / 2;
}

function easeIn(u: number): number {
  'worklet';
  const k = Math.min(Math.max(u, 0), 1);
  return k * k * k;
}

type Options = {
  autoPlay: boolean;
  listTop: number;
  undoPoint: { x: number; y: number };
  activeKey: SharedValue<string>;
  script: SharedValue<ScriptTouch>;
  onUndo: () => void;
  onReset: () => void;
};

export function useSwipeDemo({ autoPlay, listTop, undoPoint, activeKey, script, onUndo, onReset }: Options) {
  const t = useSharedValue(0);
  const paused = useSharedValue(!autoPlay);
  const touching = useSharedValue(false);
  const quiet = useSharedValue(0);
  const tidy = useSharedValue(-1);
  const down = useSharedValue('');
  const lastTx = useSharedValue(0);
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  const ghostO = useSharedValue(0);
  const ghostPress = useSharedValue(0);

  const send = (key: string, down: boolean, tx: number, vx: number) => {
    'worklet';
    script.set({ seq: script.get().seq + 1, key, down, tx, vx });
  };

  const show = (on: boolean) => {
    'worklet';
    ghostO.set(withTiming(on ? 1 : 0, { duration: DEMO.fingerFade * 1000 }));
  };

  /** One drag on a row: begin at `down`, follow `path(u)` until `up`, release with the path's velocity. */
  const drag = (t0: number, t1: number, dt: number, row: number, downAt: number, upAt: number, x0: number, path: (time: number) => number) => {
    'worklet';
    const key = SPENDING[row].key;
    const y = listTop + LAYOUT.row * (row + 0.5);
    if (t0 < downAt && t1 >= downAt) {
      down.set(key);
      lastTx.set(0);
      show(true);
      send(key, true, 0, 0);
    }
    if (down.get() !== key) return;
    if (t1 >= downAt && t1 < upAt) {
      const tx = path(t1);
      send(key, true, tx, (tx - lastTx.get()) / dt);
      lastTx.set(tx);
      ghostX.set(x0 + tx);
      ghostY.set(y);
    }
    if (t0 < upAt && t1 >= upAt) {
      const tx = path(upAt);
      send(key, false, tx, (tx - lastTx.get()) / dt);
      down.set('');
      show(false);
    }
  };

  useFrameCallback((frame) => {
    'worklet';
    if (!autoPlay || listTop === 0) return;
    const dt = QA_HOLD_AT !== null && t.get() >= QA_HOLD_AT ? 0 : Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    if (paused.get()) {
      if (!touching.get()) quiet.set(quiet.get() + dt);
      if (tidy.get() < 0 && quiet.get() >= RESUME_MS / 1000) {
        tidy.set(0);
        activeKey.set('');
        scheduleOnRN(onReset);
      }
      if (tidy.get() >= 0) {
        tidy.set(tidy.get() + dt);
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
    const o = DEMO.open;
    drag(t0, t1, dt, o.row, o.down, o.up, o.x, (time) => o.to * ease((time - o.down) / (o.moveEnd - o.down)));
    const r = DEMO.remove;
    drag(t0, t1, dt, r.row, r.down, r.up, r.x, (time) => r.to * easeIn((time - r.down) / (r.moveEnd - r.down)));
    const l = DEMO.lean;
    drag(t0, t1, dt, l.row, l.down, l.up, l.x, (time) => (time < l.turn
      ? l.to * ease((time - l.down) / (l.turn - l.down))
      : l.to * (1 - ease((time - l.turn) / (l.moveEnd - l.turn)))));
    const u = DEMO.undo;
    if (t0 < u.down && t1 >= u.down) {
      ghostX.set(undoPoint.x);
      ghostY.set(undoPoint.y);
      show(true);
      ghostPress.set(withTiming(1, { duration: (u.up - u.down) * 1000 }));
    }
    if (t0 < u.up && t1 >= u.up) {
      ghostPress.set(withTiming(0, { duration: 120 }));
      show(false);
      scheduleOnRN(onUndo);
    }
    t.set(t1 >= LOOP_MS / 1000 ? t1 - LOOP_MS / 1000 : t1);
  });

  /** A real touch: cancel the scripted one and pause. */
  const touchStart = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touching.set(true);
      quiet.set(0);
      tidy.set(-1);
      if (paused.get()) return;
      paused.set(true);
      show(false);
      if (down.get() !== '') {
        send(down.get(), false, 0, 0);
        down.set('');
      }
    });
    // show/send are worklets declared in this hook; they only touch shared values.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const touchEnd = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touching.set(false);
      quiet.set(0);
    });
  }, [quiet, touching]);

  // Ambient clock: the loop clock while the demo runs, so the loop stays seamless.
  const clock = useSharedValue(0);
  useFrameCallback((frame) => {
    'worklet';
    clock.set(autoPlay && !paused.get() ? t.get() : clock.get() + Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05));
  });

  return { touchStart, touchEnd, paused, clock, ghost: { ghostX, ghostY, ghostO, ghostPress } };
}
