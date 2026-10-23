/**
 * Saving a place: hold a photo until it lifts, carry it onto the saved button (it is pulled in and shrinks as it gets
 * near), let go and the button swallows it; tap the button and the collection opens with the new place on top.
 * A finger and the demo script call the same worklets; React only hears about the count and whether the sheet is
 * open, never per frame.
 */
import { useCallback, useMemo, useState } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import {
  Easing, useFrameCallback, useSharedValue, withDelay, withSequence, withSpring, withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { DEMO, EASE_IN_OUT, EASE_OUT, LOOP_MS, RESUME_MS, SAVE, SPRING, TIMING } from './motion';
import { playSaveHaptic, type SaveHaptic } from './save-haptics';
import type { Layout } from './theme';

const easeOut = Easing.bezier(EASE_OUT[0], EASE_OUT[1], EASE_OUT[2], EASE_OUT[3]);
const easeInOut = Easing.bezier(EASE_IN_OUT[0], EASE_IN_OUT[1], EASE_IN_OUT[2], EASE_IN_OUT[3]);

type Options = { lay: Layout; autoPlay: boolean; reduced: boolean };

export function useSave({ lay, autoPlay, reduced }: Options) {
  const [count, setCount] = useState<number>(SAVE.countBefore);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [carried, setCarried] = useState(-1);

  // The carried photo.
  const carry = useSharedValue(-1);
  const cx = useSharedValue(0); // its centre, before the magnet
  const cy = useSharedValue(0);
  const offX = useSharedValue(0); // finger to centre
  const offY = useSharedValue(0);
  const lift = useSharedValue(0);
  const lean = useSharedValue(0);
  const pull = useSharedValue(0);
  const drop = useSharedValue(0);
  const lastX = useSharedValue(0);
  const lastT = useSharedValue(0);
  const near = useSharedValue(false);
  // The saved button and its badge.
  const gulp = useSharedValue(1);
  const roll = useSharedValue(1); // 0 → 1 as the badge's new figure rolls in
  // The photo's mark in the feed, the toast, the sheet and what happens inside it.
  const marked = useSharedValue(-1);
  const mark = useSharedValue(0);
  const toast = useSharedValue(0);
  const sheet = useSharedValue(0);
  const arrive = useSharedValue(1);
  const press = useSharedValue(0); // the saved button under a finger
  // The demo.
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  const ghostO = useSharedValue(0);
  const ghostP = useSharedValue(0);
  const silent = useSharedValue(autoPlay);
  const paused = useSharedValue(!autoPlay);
  const clock = useSharedValue(0);
  const quiet = useSharedValue(0);
  const touching = useSharedValue(false);
  const tidy = useSharedValue(-1);

  const save = lay.save;
  const tiles = lay.tiles;
  const R = reduced;

  // ——— Helpers (declared before every worklet that calls them) ———
  const haptic = (h: SaveHaptic) => {
    'worklet';
    if (!silent.get()) scheduleOnRN(playSaveHaptic, h);
  };

  const smooth = (e0: number, e1: number, x: number) => {
    'worklet';
    const t = Math.min(Math.max((x - e0) / (e1 - e0), 0), 1);
    return t * t * (3 - 2 * t);
  };

  /** Distance from the carried photo's centre to the button's. */
  const distance2 = () => {
    'worklet';
    const dx = cx.get() - save.x;
    const dy = cy.get() - save.y;
    return Math.sqrt(dx * dx + dy * dy);
  };

  const tileAt = (x: number, y: number) => {
    'worklet';
    for (let i = 0; i < tiles.length; i++) {
      const t = tiles[i];
      if (x >= t.x && x <= t.x + t.w && y >= t.y && y <= t.y + t.h && y < lay.barY - 8) return i;
    }
    return -1;
  };

  const pickUp = (i: number, x: number, y: number) => {
    'worklet';
    if (i < 0 || carry.get() >= 0 || sheet.get() > 0.01) return;
    const t = tiles[i];
    carry.set(i);
    scheduleOnRN(setCarried, i);
    cx.set(t.x + t.w / 2);
    cy.set(t.y + t.h / 2);
    offX.set(t.x + t.w / 2 - x);
    offY.set(t.y + t.h / 2 - y);
    lastX.set(x);
    lastT.set(Date.now());
    drop.set(0);
    pull.set(0);
    near.set(false);
    lift.set(R ? 1 : withSpring(1, SPRING.lift));
    haptic('lift');
  };

  const moveTo = (x: number, y: number) => {
    'worklet';
    if (carry.get() < 0 || drop.get() > 0) return;
    cx.set(x + offX.get());
    cy.set(y + offY.get());
    // Lean with the sideways speed, sprung so it settles when the finger stops.
    const now = Date.now();
    const dt = Math.max(now - lastT.get(), 8) / 1000;
    const v = (x - lastX.get()) / dt;
    lastX.set(x);
    lastT.set(now);
    if (!R) lean.set(withSpring(Math.max(-SAVE.leanMax, Math.min(SAVE.leanMax, v * SAVE.lean)), SPRING.home));
    const p = smooth(SAVE.magnetFar, SAVE.magnetNear, distance2());
    pull.set(p);
    const isNear = p > 0.5;
    if (isNear !== near.get()) {
      near.set(isNear);
      if (isNear) haptic('magnet');
    }
  };

  const landed = () => {
    'worklet';
    const i = carry.get();
    carry.set(-1);
    scheduleOnRN(setCarried, -1);
    lift.set(0);
    lean.set(0);
    pull.set(0);
    gulp.set(R ? 1 : withSequence(withTiming(0.86, { duration: 70, easing: easeOut }), withSpring(1, SPRING.gulp)));
    roll.set(0);
    roll.set(withTiming(1, { duration: R ? 0 : TIMING.roll, easing: easeOut }));
    scheduleOnRN(setCount, SAVE.countBefore + 1);
    marked.set(i);
    mark.set(withTiming(1, { duration: TIMING.mark, easing: easeOut }));
    toast.set(withTiming(1, { duration: TIMING.toastIn, easing: easeOut }));
    haptic('success');
  };

  const release = () => {
    'worklet';
    const i = carry.get();
    if (i < 0 || drop.get() > 0) return;
    if (distance2() < SAVE.dropRadius || pull.get() > 0.5) {
      drop.set(withTiming(1, { duration: R ? 120 : TIMING.drop, easing: easeInOut }, (done) => {
        if (done) landed();
      }));
      return;
    }
    // Not over the button: home, carrying the finger's lean out.
    const t = tiles[i];
    pull.set(withTiming(0, { duration: 200, easing: easeOut }));
    lean.set(withSpring(0, SPRING.home));
    cx.set(withSpring(t.x + t.w / 2, R ? SPRING.reduced : SPRING.home));
    cy.set(withSpring(t.y + t.h / 2, R ? SPRING.reduced : SPRING.home));
    lift.set(withSpring(0, R ? SPRING.reduced : SPRING.home, (done) => {
      if (done) {
        carry.set(-1);
        scheduleOnRN(setCarried, -1);
      }
    }));
  };

  const openSheet = () => {
    'worklet';
    if (carry.get() >= 0) return;
    scheduleOnRN(setSheetOpen, true);
    toast.set(withTiming(0, { duration: TIMING.toastOut, easing: easeOut }));
    sheet.set(R ? withTiming(1, { duration: 200 }) : withSpring(1, SPRING.sheet));
    // A place saved since the sheet last opened: the grid rises with its first cell free and the new place settles
    // into it, on top, once the sheet is up.
    const fresh = marked.get() >= 0;
    arrive.set(fresh ? 0 : 1);
    if (fresh) arrive.set(withDelay(TIMING.arriveDelay, withTiming(1, { duration: R ? 160 : TIMING.arrive, easing: easeOut })));
    haptic('select');
  };

  const closeSheet = () => {
    'worklet';
    sheet.set(withTiming(0, { duration: TIMING.sheetOut, easing: easeOut }, (done) => {
      if (done) scheduleOnRN(setSheetOpen, false);
    }));
  };

  /** Back to the start: no mark, the badge at its first count, the sheet down, nothing carried. */
  const resetAll = () => {
    'worklet';
    if (sheet.get() > 0) closeSheet();
    toast.set(withTiming(0, { duration: TIMING.toastOut, easing: easeOut }));
    mark.set(withTiming(0, { duration: TIMING.mark, easing: easeOut }, (done) => {
      if (done) marked.set(-1);
    }));
    if (marked.get() >= 0) {
      roll.set(0);
      roll.set(withTiming(1, { duration: R ? 0 : TIMING.roll, easing: easeOut }));
    }
    scheduleOnRN(setCount, SAVE.countBefore);
  };

  const pauseDemo = () => {
    'worklet';
    quiet.set(0);
    tidy.set(-1);
    silent.set(false);
    if (paused.get()) return;
    paused.set(true);
    ghostO.set(withTiming(0, { duration: TIMING.finger }));
    ghostP.set(0);
    // A scripted carry is let go where it is: it goes home.
    if (carry.get() >= 0 && drop.get() === 0) release();
  };

  const at = (t0: number, t1: number, t: number) => {
    'worklet';
    return t0 < t && t1 >= t;
  };

  /** One step of the scripted finger, from t0 to t1 seconds into the loop. */
  const script = (t0: number, t1: number) => {
    'worklet';
    const tile = tiles[DEMO.tile];
    const fx = tile.x + tile.w * 0.5;
    const fy = tile.y + tile.h * 0.42;
    if (at(t0, t1, DEMO.fingerIn)) {
      ghostX.set(fx);
      ghostY.set(fy);
      ghostO.set(withTiming(1, { duration: TIMING.finger }));
    }
    if (at(t0, t1, DEMO.down)) ghostP.set(withTiming(1, { duration: TIMING.finger }));
    if (at(t0, t1, DEMO.lift)) pickUp(DEMO.tile, fx, fy);
    const [c0, c1] = DEMO.carry;
    if (carry.get() >= 0 && drop.get() === 0 && t1 >= c0[0] && t1 <= c1[0] + 0.12) {
      const k = Math.min((t1 - c0[0]) / (c1[0] - c0[0]), 1);
      const u = (1 - Math.cos(Math.PI * k)) / 2;
      // A gentle arc, bowed toward the middle of the screen, then down onto the button.
      const tx = save.x - offX.get();
      const ty = save.y - offY.get();
      const x = fx + (tx - fx) * u - Math.sin(u * Math.PI) * 28;
      const y = fy + (ty - fy) * u;
      ghostX.set(x);
      ghostY.set(y);
      moveTo(x, y);
    }
    if (at(t0, t1, DEMO.release)) {
      release();
      ghostP.set(withTiming(0, { duration: TIMING.finger }));
      ghostO.set(withTiming(0, { duration: TIMING.finger }));
    }
    if (at(t0, t1, DEMO.tapIn)) {
      ghostX.set(save.x);
      ghostY.set(save.y);
      ghostO.set(withTiming(1, { duration: TIMING.finger }));
    }
    if (at(t0, t1, DEMO.tap - 0.1)) {
      ghostP.set(withTiming(1, { duration: 100 }));
      press.set(withTiming(1, { duration: 100, easing: easeOut }));
    }
    if (at(t0, t1, DEMO.tap)) {
      ghostP.set(withTiming(0, { duration: TIMING.pressOut }));
      ghostO.set(withDelay(80, withTiming(0, { duration: TIMING.finger })));
      press.set(withTiming(0, { duration: TIMING.pressOut, easing: easeOut }));
      openSheet();
    }
    if (at(t0, t1, DEMO.close)) closeSheet();
    if (at(t0, t1, DEMO.reset)) resetAll();
  };

  useFrameCallback((frame) => {
    'worklet';
    if (!autoPlay) return;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    if (paused.get()) {
      if (!touching.get() && carry.get() < 0) quiet.set(quiet.get() + dt);
      if (tidy.get() < 0 && quiet.get() >= RESUME_MS / 1000) {
        tidy.set(0);
        silent.set(true);
        resetAll();
      }
      if (tidy.get() >= 0) {
        tidy.set(tidy.get() + dt);
        if (tidy.get() >= DEMO.tidy) {
          tidy.set(-1);
          clock.set(0);
          paused.set(false);
        }
      }
      return;
    }
    const t0 = clock.get();
    const t1 = t0 + dt;
    script(t0, t1);
    clock.set(t1 >= LOOP_MS / 1000 ? t1 - LOOP_MS / 1000 : t1);
  });

  const touchStart = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touching.set(true);
      pauseDemo();
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const gesture = useMemo(() => Gesture.Pan()
    .activateAfterLongPress(SAVE.holdMs)
    .onBegin(() => {
      touching.set(true);
      pauseDemo();
    })
    .onStart((e) => pickUp(tileAt(e.x, e.y), e.x, e.y))
    .onUpdate((e) => moveTo(e.x, e.y))
    .onFinalize(() => {
      touching.set(false);
      quiet.set(0);
      release();
    }),
  // The worklets read shared values and the fixed layout only.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  []);

  const onSavePress = useCallback((down: boolean) => scheduleOnUI(() => {
    'worklet';
    pauseDemo();
    press.set(withTiming(down ? 1 : 0, { duration: down ? 100 : TIMING.pressOut, easing: easeOut }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);
  const onSaveTap = useCallback(() => scheduleOnUI(() => {
    'worklet';
    pauseDemo();
    if (sheet.get() > 0.5) closeSheet();
    else openSheet();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);
  const onClose = useCallback(() => scheduleOnUI(() => {
    'worklet';
    pauseDemo();
    closeSheet();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);

  return {
    count, sheetOpen, carried, gesture, touchStart, touchEnd, onSavePress, onSaveTap, onClose,
    values: { carry, cx, cy, lift, lean, pull, drop, gulp, roll, marked, mark, toast, sheet, arrive, press, ghostX, ghostY, ghostO, ghostP },
  };
}
