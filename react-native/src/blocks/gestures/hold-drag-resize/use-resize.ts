/**
 * Hold the preview, then drag: its height follows the finger and everything below reflows with it.
 * The finger and the demo script call the same worklets: holdBegin → holdMove(dy) → holdEnd(vy).
 * Release projects the velocity and springs to the nearest of three sizes.
 */
import { useCallback, useMemo, useState } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import {
  cancelAnimation,
  Easing,
  ReduceMotion,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { DEMO, LOOP_MS, QA_SLOWMO, QA_STATE, QA_T, RESIZE, RESUME_MS, SIZES, SPRING, TIMING, UPGRADE } from './motion';
import { playResizeHaptic, type ResizeHaptic } from './resize-haptics';
import { CLIPS } from './reel';

const MIN = SIZES[0];
const MAX = SIZES[SIZES.length - 1];
const LOOP = LOOP_MS / 1000;

function ease(u: number): number {
  'worklet';
  const k = Math.min(Math.max(u, 0), 1);
  return (1 - Math.cos(Math.PI * k)) / 2;
}

function rubber(h: number): number {
  'worklet';
  if (h < MIN) return MIN - Math.min((MIN - h) * RESIZE.rubber, RESIZE.rubberMax);
  if (h > MAX) return MAX + Math.min((h - MAX) * RESIZE.rubber, RESIZE.rubberMax);
  return h;
}

type Options = { autoPlay: boolean; reduceMotion: boolean; width: number; previewCentreY: number; gotIt: { x: number; y: number } };

export function useResize({ autoPlay, reduceMotion, width, previewCentreY, gotIt }: Options) {
  const [coachOn, setCoachOn] = useState(true);
  const [clip, setClip] = useState(0);
  const [second, setSecond] = useState(0);
  const [playing, setPlaying] = useState(true);
  const [sizeIndex, setSizeIndex] = useState(0);

  const h = useSharedValue<number>(MIN);
  const startH = useSharedValue<number>(MIN);
  const held = useSharedValue(0);
  const holding = useSharedValue(false);
  const clock = useSharedValue(0);
  const play = useSharedValue(true);
  const coach = useSharedValue(1);
  const coachP = useSharedValue(0); // the coach mark's hand: 0 up, 1 down
  const lastClip = useSharedValue(0);
  const lastSecond = useSharedValue(0);
  const press = useSharedValue(0); // touch-down anticipation
  const scriptOrUser = useSharedValue(false); // true while a finger (not the demo) holds the preview
  const [nearState, setNear] = useState(0);
  const lag = useSharedValue<number>(SIZES[0]); // the rows under the preview follow its edge with a little delay
  const near = useSharedValue(0);

  // Autoplay.
  const t = useSharedValue(0);
  const paused = useSharedValue(!autoPlay);
  const touching = useSharedValue(false);
  const quiet = useSharedValue(0);
  const tidy = useSharedValue(-1);
  const scriptHeld = useSharedValue(false);
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  const ghostO = useSharedValue(0);
  const ghostPress = useSharedValue(0);

  /** Height the screen lays out with: the resized height plus the coach mark's demonstration. */
  const shown = useDerivedValue(() => h.get() + DEMO.coachHand.grow * coachP.get() * coach.get());

  const haptic = (kind: ResizeHaptic, silent: boolean) => {
    'worklet';
    if (!silent) scheduleOnRN(playResizeHaptic, kind);
  };

  const nearest = (value: number) => {
    'worklet';
    let best = 0;
    for (let i = 1; i < SIZES.length; i++) if (Math.abs(SIZES[i] - value) < Math.abs(SIZES[best] - value)) best = i;
    return best;
  };

  const snapTo = (index: number, velocity: number) => {
    'worklet';
    h.set(reduceMotion
      ? withTiming(SIZES[index], { duration: TIMING.reduced, reduceMotion: ReduceMotion.Never })
      : withSpring(SIZES[index], { ...SPRING.snap, velocity }));
    scheduleOnRN(setSizeIndex, index);
  };

  const holdBegin = (silent: boolean) => {
    'worklet';
    cancelAnimation(h);
    startH.set(h.get());
    holding.set(true);
    if (!reduceMotion) held.set(withSpring(1, SPRING.lift));
    haptic('lift', silent);
  };

  const holdMove = (dy: number, silent: boolean) => {
    'worklet';
    if (!holding.get()) return;
    const before = h.get();
    let next = rubber(startH.get() + dy * RESIZE.gain);
    // Magnetic sizes: inside UPGRADE.magnet the drag is pulled toward the size, like a detent in a real slider.
    for (let i = 0; i < SIZES.length; i++) {
      const d = SIZES[i] - next;
      if (Math.abs(d) < UPGRADE.magnet) next += d * UPGRADE.magnetPull * (1 - Math.abs(d) / UPGRADE.magnet);
    }
    h.set(next);
    const n = nearest(next);
    if (n !== near.get()) { near.set(n); scheduleOnRN(setNear, n); }
    for (let i = 0; i < SIZES.length; i++) {
      if ((before - SIZES[i]) * (next - SIZES[i]) < 0) haptic('detent', silent);
    }
  };

  const holdEnd = (vy: number) => {
    'worklet';
    if (!holding.get()) return;
    holding.set(false);
    held.set(withSpring(0, SPRING.drop));
    const v = vy * RESIZE.gain;
    snapTo(nearest(h.get() + v * RESIZE.project), v);
    haptic('tap', !scriptOrUser.get());
  };

  const dismissCoach = () => {
    'worklet';
    cancelAnimation(coachP);
    coachP.set(withTiming(0, { duration: TIMING.coachFade }));
    coach.set(withTiming(0, { duration: TIMING.coachFade, reduceMotion: ReduceMotion.Never }));
    scheduleOnRN(setCoachOn, false);
  };

  const showCoach = (duration: number) => {
    'worklet';
    coach.set(withTiming(1, { duration, reduceMotion: ReduceMotion.Never }));
    scheduleOnRN(setCoachOn, true);
  };

  // Outside the demo the coach mark demonstrates by itself until it is dismissed.
  const startCoachLoop = useCallback(() => {
    if (autoPlay || reduceMotion) return;
    coachP.set(withRepeat(withSequence(
      withTiming(1, { duration: 700, easing: Easing.inOut(Easing.cubic) }),
      withTiming(0, { duration: 800, easing: Easing.inOut(Easing.cubic) }),
      withTiming(0, { duration: 500 }),
    ), -1));
  }, [autoPlay, coachP, reduceMotion]);

  const show = (on: boolean) => {
    'worklet';
    ghostO.set(withTiming(on ? 1 : 0, { duration: DEMO.fingerFade * 1000 }));
  };

  useFrameCallback((frame) => {
    'worklet';
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    // Follow-through: the rows under the preview catch up with its edge (exponential, frame-rate independent).
    lag.set(reduceMotion ? shown.get() : lag.get() + (shown.get() - lag.get()) * (1 - Math.exp(-UPGRADE.follow * dt)));
    // The reel: in the demo its clock is the loop clock; otherwise it runs while playing.
    if (autoPlay && !paused.get()) clock.set(t.get());
    else if (play.get()) clock.set((clock.get() + dt) % LOOP);
    const c = Math.floor(clock.get() / (TIMING.clip / 1000)) % CLIPS.length;
    if (c !== lastClip.get()) { lastClip.set(c); scheduleOnRN(setClip, c); }
    const s = Math.floor(clock.get());
    if (s !== lastSecond.get()) { lastSecond.set(s); scheduleOnRN(setSecond, s); }

    if (!autoPlay || width === 0) return;
    // QA only: hold one state of the demo for a screenshot.
    if (QA_STATE >= 0) {
      if (t.get() !== QA_T) {
        t.set(QA_T);
        if (QA_STATE !== 1) { dismissCoach(); coach.set(0); scheduleOnRN(setCoachOn, false); snapTo(QA_STATE === 2 ? 1 : 0, 0); }
      }
      return;
    }
    if (paused.get()) {
      if (!touching.get()) quiet.set(quiet.get() + dt);
      if (tidy.get() < 0 && quiet.get() >= RESUME_MS / 1000 && !holding.get()) {
        tidy.set(0);
        snapTo(0, 0);
        showCoach(DEMO.tidy * 1000);
        clock.set(0);
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
    const t1 = t0 + dt / QA_SLOWMO;
    const cross = (at: number) => t0 < at && t1 >= at;
    const x = width / 2;

    // 1. The coach mark's hand shows the gesture.
    const ch = DEMO.coachHand;
    if (t1 < DEMO.gotIt.tap) {
      coachP.set(t1 < ch.bottom ? ease((t1 - ch.down) / (ch.bottom - ch.down)) : 1 - ease((t1 - ch.bottom) / (ch.up - ch.bottom)));
    }
    // 2. Got it.
    if (cross(DEMO.gotIt.show)) {
      ghostX.set(gotIt.x);
      ghostY.set(gotIt.y);
      show(true);
      ghostPress.set(withTiming(1, { duration: (DEMO.gotIt.tap - DEMO.gotIt.show) * 1000 }));
    }
    if (cross(DEMO.gotIt.tap)) {
      dismissCoach();
      ghostPress.set(withTiming(0, { duration: 120 }));
      show(false);
    }
    // 3. Hold and drag down: standard, then full screen.
    const g = DEMO.grow;
    const growTo = MAX - MIN;
    if (cross(g.down)) {
      ghostX.set(x);
      ghostY.set(previewCentreY);
      show(true);
      ghostPress.set(withTiming(1, { duration: (g.lifted - g.down) * 1000 }));
    }
    if (cross(g.lifted)) { scriptOrUser.set(false); holdBegin(true); scriptHeld.set(true); }
    if (scriptHeld.get() && t1 >= g.lifted && t1 < g.up) {
      const um = (SIZES[1] - MIN) / (MAX - MIN);
      const u = t1 < g.mid ? um * ease((t1 - g.lifted) / (g.mid - g.lifted))
        : t1 < g.hold ? um : um + (1 - um) * ease((t1 - g.hold) / (g.end - g.hold));
      const dy = (growTo / RESIZE.gain) * u;
      holdMove(dy, true);
      ghostY.set(previewCentreY + dy);
    }
    if (cross(g.up)) { scriptHeld.set(false); holdEnd(0); ghostPress.set(withTiming(0, { duration: 120 })); show(false); }
    // 4. Hold and drag up: back to compact.
    const k = DEMO.shrink;
    const fullCentre = previewCentreY + growTo / RESIZE.gain;
    if (cross(k.down)) {
      ghostX.set(x);
      ghostY.set(fullCentre);
      show(true);
      ghostPress.set(withTiming(1, { duration: (k.lifted - k.down) * 1000 }));
    }
    if (cross(k.lifted)) { scriptOrUser.set(false); holdBegin(true); scriptHeld.set(true); }
    if (scriptHeld.get() && t1 >= k.lifted && t1 < k.up) {
      const dy = -(growTo / RESIZE.gain) * ease((t1 - k.lifted) / (k.end - k.lifted));
      holdMove(dy, true);
      ghostY.set(fullCentre + dy);
    }
    if (cross(k.up)) { scriptHeld.set(false); holdEnd(0); ghostPress.set(withTiming(0, { duration: 120 })); show(false); }
    // 5. The coach mark comes back, so the loop starts where it ends.
    if (cross(DEMO.coachBack)) showCoach((LOOP - DEMO.coachBack) * 1000);
    t.set(t1 >= LOOP ? t1 - LOOP : t1);
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
      if (scriptHeld.get()) { scriptHeld.set(false); holdEnd(0); }
    });
    // show/holdEnd only touch shared values.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const touchEnd = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touching.set(false);
      quiet.set(0);
    });
  }, [quiet, touching]);

  const gesture = useMemo(() => {
    const drag = Gesture.Pan()
      .activateAfterLongPress(RESIZE.holdMs)
      .onBegin(() => { press.set(withTiming(1, { duration: UPGRADE.pressInMs, easing: Easing.out(Easing.cubic) })); })
      .onStart(() => { scriptOrUser.set(true); holdBegin(false); })
      .onUpdate((e) => holdMove(e.translationY, false))
      .onEnd((e) => holdEnd(e.velocityY))
      .onFinalize(() => { press.set(withTiming(0, { duration: UPGRADE.pressOutMs, easing: Easing.out(Easing.cubic) })); holdEnd(0); });
    const tap = Gesture.Tap().maxDuration(RESIZE.holdMs - 20).onEnd(() => {
      play.set(!play.get());
      scheduleOnRN(setPlaying, play.get());
      haptic('tap', false);
    });
    return Gesture.Exclusive(drag, tap);
    // The worklets read shared values only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const stepSize = useCallback((delta: number) => {
    scheduleOnUI(() => {
      'worklet';
      const i = Math.min(Math.max(nearest(h.get()) + delta, 0), SIZES.length - 1);
      snapTo(i, 0);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const gotItPress = useCallback(() => scheduleOnUI(() => {
    'worklet';
    dismissCoach();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);
  const togglePlay = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      play.set(!play.get());
      scheduleOnRN(setPlaying, play.get());
    });
  }, [play]);

  return {
    gesture, touchStart, touchEnd, stepSize, gotItPress, togglePlay, startCoachLoop,
    coachOn, clip, second, playing, sizeIndex,
    near: nearState,
    values: { shown, held, clock, coach, coachP, ghostX, ghostY, ghostO, ghostPress, press, lag },
  };
}
