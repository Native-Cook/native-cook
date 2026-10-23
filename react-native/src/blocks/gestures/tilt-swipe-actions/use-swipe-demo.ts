/**
 * Autoplay: a scripted finger on one clock. It writes ScriptTouch events (one per frame at most) that the named row
 * handles exactly as it handles a real pan, and taps Undo through the screen's own handler. Any real touch stops it;
 * it starts again from rest RESUME_MS after the last one.
 */
import { useCallback, useEffect } from 'react';
import { useFrameCallback, useSharedValue, withTiming, type SharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { DEMO, LOOP_MS, QA_HOLD_AT, RESUME_MS } from './motion';
import type { ScriptTouch } from './SwipeRow';
import { LAYOUT } from './theme';
import { SPENDING } from './transactions';

type Keys = readonly (readonly [number, number])[];

function smooth(u: number): number {
  'worklet';
  const k = Math.min(Math.max(u, 0), 1);
  return (1 - Math.cos(Math.PI * k)) / 2;
}

/** The finger's travel at time t along eased key frames [time, dx]. */
function along(keys: Keys, t: number): number {
  'worklet';
  if (t <= keys[0][0]) return keys[0][1];
  for (let i = 1; i < keys.length; i++) {
    const [t1, v1] = keys[i];
    const [t0, v0] = keys[i - 1];
    if (t <= t1) return v0 + (v1 - v0) * smooth((t - t0) / (t1 - t0));
  }
  return keys[keys.length - 1][1];
}

type Options = {
  autoPlay: boolean;
  ready: boolean; // the rows are on screen (fonts loaded): the clock starts then
  rowsTop: number; // the first row's top on the screen
  rowsLeft: number; // the rows' left edge on the screen
  undoPoint: { x: number; y: number };
  script: SharedValue<ScriptTouch>;
  onUndo: () => void;
  onReset: () => void;
};

export function useSwipeDemo({ autoPlay, ready, rowsTop, rowsLeft, undoPoint, script, onUndo, onReset }: Options) {
  const t = useSharedValue(0);
  const live = useSharedValue(false);
  useEffect(() => { live.set(ready); }, [live, ready]);
  // It starts the way it restarts: through the tidy step (every row and value reset, then a beat at rest), so the
  // first loop after a cold open is the same as every other one.
  const paused = useSharedValue(true);
  const touching = useSharedValue(false);
  const quiet = useSharedValue(RESUME_MS / 1000);
  const tidy = useSharedValue(-1);
  const down = useSharedValue('');
  const lastDx = useSharedValue(0);
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  const ghostO = useSharedValue(0);
  const ghostPress = useSharedValue(0);

  const send = (key: string, kind: ScriptTouch['kind'], dx: number, vx: number, x: number) => {
    'worklet';
    script.set({ seq: script.get().seq + 1, key, kind, dx, vx, x });
  };

  const show = (on: boolean) => {
    'worklet';
    ghostO.set(withTiming(on ? 1 : 0, { duration: DEMO.fingerFade * 1000 }));
  };

  /** One drag on a row along key frames, from `downAt` to `upAt`. */
  const drag = (t0: number, t1: number, dt: number, row: number, downAt: number, upAt: number, x0: number, dy: number, keys: Keys) => {
    'worklet';
    const key = SPENDING[row].key;
    const y = rowsTop + LAYOUT.row * (row + 0.5) + dy;
    if (t0 < downAt && t1 >= downAt) {
      down.set(key);
      lastDx.set(0);
      ghostX.set(rowsLeft + x0);
      ghostY.set(y);
      show(true);
      send(key, 'down', 0, 0, x0);
      return;
    }
    if (down.get() !== key) return;
    if (t1 >= downAt && t1 < upAt) {
      const dx = along(keys, t1);
      send(key, 'move', dx, (dx - lastDx.get()) / Math.max(dt, 0.001), x0 + dx);
      lastDx.set(dx);
      ghostX.set(rowsLeft + x0 + dx);
      return;
    }
    if (t0 < upAt && t1 >= upAt) {
      const dx = along(keys, upAt);
      send(key, 'up', dx, (dx - lastDx.get()) / Math.max(dt, 0.001), x0 + dx);
      down.set('');
      show(false);
    }
  };

  /** A tap: the finger shows, presses, and lets go at `upAt`. */
  const tap = (t0: number, t1: number, downAt: number, upAt: number, x: number, y: number, fire: () => void) => {
    'worklet';
    if (t0 < downAt && t1 >= downAt) {
      ghostX.set(x);
      ghostY.set(y);
      show(true);
      ghostPress.set(withTiming(1, { duration: (upAt - downAt) * 1000 }));
    }
    if (t0 < upAt && t1 >= upAt) {
      ghostPress.set(withTiming(0, { duration: 120 }));
      show(false);
      fire();
    }
  };

  useFrameCallback((frame) => {
    'worklet';
    if (!autoPlay || !live.get() || rowsTop === 0) return;
    const dt = QA_HOLD_AT !== null && t.get() >= QA_HOLD_AT ? 0 : Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    if (paused.get()) {
      if (!touching.get()) quiet.set(quiet.get() + dt);
      if (tidy.get() < 0 && quiet.get() >= RESUME_MS / 1000) {
        tidy.set(0);
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
    const s = DEMO.split;
    drag(t0, t1, dt, s.row, s.down, s.up, s.x, s.y, s.keys);
    const o = DEMO.open;
    drag(t0, t1, dt, o.row, o.down, o.up, o.x, o.y, o.keys);
    const u = DEMO.undo;
    tap(t0, t1, u.down, u.up, undoPoint.x, undoPoint.y, () => {
      'worklet';
      scheduleOnRN(onUndo);
    });
    const c = DEMO.close;
    const cy = rowsTop + LAYOUT.row * (c.row + 0.5);
    tap(t0, t1, c.down, c.up, rowsLeft + c.x, cy, () => {
      'worklet';
      send(SPENDING[c.row].key, 'tap', 0, 0, c.x);
    });
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
      // The scripted finger is lifted without committing what it was doing (a split half pulled is not sent).
      if (down.get() !== '') {
        send(down.get(), 'cancel', 0, 0, 0);
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

  return { touchStart, touchEnd, paused, ghost: { ghostX, ghostY, ghostO, ghostPress } };
}
