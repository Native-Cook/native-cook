/**
 * The dome, the magnifier and the opening, on the UI thread. A finger and the demo's scripted finger call the same
 * worklets: grab → drag → release (opens out past the threshold, drops back into the dome short of it), and back
 * (the map gathers into a magnifier and drops into the dome). React only hears whether the map is open.
 */
import * as Haptics from 'expo-haptics';
import { useCallback, useMemo, useState } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import { Easing, useFrameCallback, useSharedValue, withDelay, withSequence, withSpring, withTiming } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { DEMO, EASE_IN_OUT, EASE_OUT, LOOP_MS, PULL, RESUME_MS, SPRING, TIMING } from './motion';
import { LAYOUT, type Layout } from './theme';

const easeOut = Easing.bezier(EASE_OUT[0], EASE_OUT[1], EASE_OUT[2], EASE_OUT[3]);
const easeInOut = Easing.bezier(EASE_IN_OUT[0], EASE_IN_OUT[1], EASE_IN_OUT[2], EASE_IN_OUT[3]);

const REST = 0;
const PULLING = 1;
const OPENING = 2;
const OPEN = 3;
const CLOSING = 4;

function buzz(kind: 'grab' | 'commit' | 'back') {
  const ignore = () => {};
  if (kind === 'grab') void Haptics.selectionAsync().catch(ignore);
  else if (kind === 'commit') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(ignore);
  else void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(ignore);
}

type Options = { lay: Layout; autoPlay: boolean; reduced: boolean };

export function useLens({ lay, autoPlay, reduced }: Options) {
  const [open, setOpen] = useState(false);
  const R = reduced;
  const cx = useSharedValue(lay.domeX);
  const cy = useSharedValue(lay.domeY);
  const r = useSharedValue<number>(LAYOUT.domeR);
  const mag = useSharedValue<number>(LAYOUT.mag);
  const rim = useSharedValue(1);
  const hint = useSharedValue(1);
  const route = useSharedValue(0);
  const phase = useSharedValue(REST);
  const grabY = useSharedValue(0);
  const pull = useSharedValue(0);
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

  // ——— Helpers (declared before every worklet that calls them) ———
  const haptic = (k: 'grab' | 'commit' | 'back') => {
    'worklet';
    if (!silent.get()) scheduleOnRN(buzz, k);
  };

  const smooth = (x: number) => {
    'worklet';
    const t = Math.min(Math.max(x, 0), 1);
    return t * t * (3 - 2 * t);
  };

  const grab = (x: number, y: number) => {
    'worklet';
    if (phase.get() !== REST) return false;
    const dx = x - lay.domeX;
    const dy = y - lay.domeY;
    if (dx * dx + dy * dy > LAYOUT.domeR * LAYOUT.domeR) return false;
    phase.set(PULLING);
    grabY.set(y);
    pull.set(0);
    haptic('grab');
    return true;
  };

  const drag = (x: number, y: number) => {
    'worklet';
    if (phase.get() !== PULLING) return;
    const span = grabY.get() - lay.lensY;
    const p = (grabY.get() - y) / span;
    pull.set(p);
    if (p <= 0) {
      // Pushed down, the dome gives a little and no more.
      cx.set(lay.domeX);
      cy.set(lay.domeY + (y - grabY.get()) * PULL.rubber);
      r.set(LAYOUT.domeR);
      hint.set(1);
      return;
    }
    const s = smooth(p);
    cx.set(lay.domeX + (x - lay.domeX) * s);
    cy.set(lay.domeY + (y - lay.domeY) * s);
    r.set(LAYOUT.domeR + (LAYOUT.lensR - LAYOUT.domeR) * s);
    hint.set(Math.max(0, 1 - p * 3));
  };

  const finishOpen = () => {
    'worklet';
    phase.set(OPEN);
    scheduleOnRN(setOpen, true);
    route.set(withDelay(TIMING.routeDelay, withTiming(1, { duration: R ? 200 : TIMING.route, easing: easeInOut })));
  };

  const openOut = () => {
    'worklet';
    phase.set(OPENING);
    haptic('commit');
    hint.set(withTiming(0, { duration: TIMING.hint }));
    const d = R ? 220 : TIMING.open;
    mag.set(withTiming(1, { duration: d, easing: easeInOut }));
    rim.set(withTiming(0, { duration: d, easing: easeInOut }));
    r.set(withTiming(lay.cover + 40, { duration: d, easing: easeInOut }, (done) => {
      if (done) finishOpen();
    }));
  };

  const dropHome = (vy: number) => {
    'worklet';
    phase.set(CLOSING);
    const s = R ? SPRING.reduced : { ...SPRING.home, velocity: vy };
    cx.set(withSpring(lay.domeX, R ? SPRING.reduced : SPRING.home));
    r.set(withSpring(LAYOUT.domeR, R ? SPRING.reduced : SPRING.home));
    hint.set(withDelay(200, withTiming(1, { duration: TIMING.hint, easing: easeOut })));
    cy.set(withSpring(lay.domeY, s, (done) => {
      if (done) phase.set(REST);
    }));
  };

  const release = (vy: number) => {
    'worklet';
    if (phase.get() !== PULLING) return;
    if (pull.get() >= PULL.commit || vy < -PULL.flick) openOut();
    else dropHome(vy);
  };

  /** Back: the map gathers into a magnifier in the middle, then drops into the dome. */
  const back = () => {
    'worklet';
    if (phase.get() !== OPEN) return;
    phase.set(CLOSING);
    scheduleOnRN(setOpen, false);
    haptic('back');
    route.set(withTiming(0, { duration: 160, easing: easeOut }));
    const d = R ? 200 : TIMING.gather;
    cx.set(withTiming(lay.domeX, { duration: d, easing: easeInOut }));
    mag.set(withTiming(LAYOUT.mag, { duration: d, easing: easeInOut }));
    rim.set(withTiming(1, { duration: d, easing: easeInOut }));
    r.set(withSequence(
      withTiming(LAYOUT.lensR, { duration: d, easing: easeInOut }),
      withSpring(LAYOUT.domeR, R ? SPRING.reduced : SPRING.home),
    ));
    cy.set(withSequence(
      withTiming(lay.lensY, { duration: d, easing: easeInOut }),
      withSpring(lay.domeY, R ? SPRING.reduced : SPRING.home, (done) => {
        if (done) phase.set(REST);
      }),
    ));
    hint.set(withDelay(d + 200, withTiming(1, { duration: TIMING.hint, easing: easeOut })));
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
    if (phase.get() === PULLING) dropHome(0);
  };

  const at = (t0: number, t1: number, t: number) => {
    'worklet';
    return t0 < t && t1 >= t;
  };

  const script = (t0: number, t1: number) => {
    'worklet';
    const P = DEMO.path;
    const W = lay.width;
    const H = lay.height;
    if (at(t0, t1, DEMO.fingerIn)) {
      ghostX.set(P[0][1] * W);
      ghostY.set(P[0][2] * H);
      ghostO.set(withTiming(1, { duration: TIMING.finger }));
    }
    if (at(t0, t1, DEMO.down)) {
      ghostP.set(withTiming(1, { duration: TIMING.finger }));
      grab(P[0][1] * W, P[0][2] * H);
    }
    if (phase.get() === PULLING && t1 >= P[0][0] && t1 <= P[P.length - 1][0] + 0.05) {
      let x = P[P.length - 1][1];
      let y = P[P.length - 1][2];
      for (let i = 0; i < P.length - 1; i++) {
        if (t1 >= P[i][0] && t1 <= P[i + 1][0]) {
          const u = (t1 - P[i][0]) / (P[i + 1][0] - P[i][0]);
          const e = (1 - Math.cos(Math.PI * u)) / 2;
          x = P[i][1] + (P[i + 1][1] - P[i][1]) * e;
          y = P[i][2] + (P[i + 1][2] - P[i][2]) * e;
        }
      }
      ghostX.set(x * W);
      ghostY.set(y * H);
      drag(x * W, y * H);
    }
    if (at(t0, t1, DEMO.release)) {
      release(0);
      ghostP.set(withTiming(0, { duration: TIMING.finger }));
      ghostO.set(withTiming(0, { duration: TIMING.finger }));
    }
    const bx = LAYOUT.edge + LAYOUT.searchH / 2;
    const by = lay.top + LAYOUT.searchY + LAYOUT.searchH / 2;
    if (at(t0, t1, DEMO.tapIn)) {
      ghostX.set(bx);
      ghostY.set(by);
      ghostO.set(withTiming(1, { duration: TIMING.finger }));
      ghostP.set(withDelay(80, withTiming(1, { duration: 100 })));
    }
    if (at(t0, t1, DEMO.tap)) {
      ghostP.set(withTiming(0, { duration: TIMING.finger }));
      ghostO.set(withDelay(60, withTiming(0, { duration: TIMING.finger })));
      back();
    }
  };

  useFrameCallback((frame) => {
    'worklet';
    if (!autoPlay) return;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    if (paused.get()) {
      if (!touching.get() && phase.get() !== PULLING) quiet.set(quiet.get() + dt);
      if (tidy.get() < 0 && quiet.get() >= RESUME_MS / 1000) {
        tidy.set(0);
        silent.set(true);
        if (phase.get() === OPEN) back();
      }
      if (tidy.get() >= 0) {
        tidy.set(tidy.get() + dt);
        if (tidy.get() >= DEMO.tidy && phase.get() === REST) {
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
    .manualActivation(true)
    .maxPointers(1)
    .onTouchesDown((e, manager) => {
      touching.set(true);
      pauseDemo();
      const t = e.changedTouches[0];
      if (grab(t.x, t.y)) manager.activate();
      else manager.fail();
    })
    .onUpdate((e) => drag(e.x, e.y))
    .onEnd((e) => release(e.velocityY))
    .onFinalize(() => {
      touching.set(false);
      quiet.set(0);
      if (phase.get() === PULLING) release(0);
    }),
  // The worklets read shared values and the fixed layout only.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  []);

  const onBack = useCallback(() => scheduleOnUI(() => {
    'worklet';
    pauseDemo();
    back();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);

  return { open, gesture, touchStart, touchEnd, onBack, values: { cx, cy, r, mag, rim, hint, route, ghostX, ghostY, ghostO, ghostP } };
}
