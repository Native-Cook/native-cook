import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import {
  cancelAnimation, Easing, ReduceMotion, useAnimatedReaction, useFrameCallback,
  useReducedMotion, useSharedValue, withSpring, withTiming
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import { autoplayDynamics, bezier, clamp, D, M, mod, releaseTarget, T } from './holo-theme';
const ease = Easing.bezier(...T.uiCurve);

function tick() {
  void Haptics.selectionAsync().catch(() => { });
}

function impact() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => { });
}
/** Only state changes cross to React. Clock, gestures, shaders and transforms stay on the UI thread. */
export function useHoloPager(autoPlay: boolean, k: number) {
  const reduced = useReducedMotion();
  const pos = useSharedValue(0);
  const vel = useSharedValue(autoPlay && !reduced ? autoplayDynamics(0).velocity : 0);
  const clock = useSharedValue(0);
  const active = useSharedValue(false);
  const elapsed = useSharedValue(0);
  const timeline = useSharedValue(0);
  const playing = useSharedValue(autoPlay && !reduced);
  const held = useSharedValue(false);
  const lastTouch = useSharedValue(0);
  const resuming = useSharedValue(false);
  const previous = useSharedValue(0);
  const centre = useSharedValue(0);
  const arrive = useSharedValue(autoPlay && !reduced ? autoplayDynamics(0).arrival : 0);
  const arrivedAt = useSharedValue(-T.arrival);
  const arrivalAge = useSharedValue<number>(T.arrival);
  // Each time a card takes the centre it owes one palette cycle, paid out fast and then slower:
  // the gradient rushes as the card arrives and settles while it rests (the reference's hero).
  const surge = useSharedValue([0, 0, 0, 0]);
  const owed = useSharedValue([0, 0, 0, 0]);
  const dragging = useSharedValue(false);
  const start = useSharedValue(0);
  const pressed = useSharedValue(-1);
  const press = useSharedValue(1);
  const squeeze = useSharedValue(1);
  const buttonScale = useSharedValue(1);
  const added = useSharedValue(0);
  const demoAdded = useSharedValue(false);
  const [selection, setSelection] = useState({ index: 0, selected: false });

  const report = useCallback((index: number, selected: boolean) => setSelection({ index, selected }), []);

  const settle = useCallback((target: number, velocity = 0) => {
    'worklet';
    pos.set(reduced ? withTiming(target, {
      duration: T.settle, easing: ease,
      reduceMotion: ReduceMotion.Never
    })
      : withSpring(target, { ...M.spring, velocity }));
  }, [pos, reduced]);

  const touch = useCallback(() => {
    'worklet';
    if (demoAdded.get())
      added.set(added.get() | 4);
    demoAdded.set(false);
    playing.set(false);
    resuming.set(false);
    held.set(true);
    lastTouch.set(elapsed.get());
    cancelAnimation(pos);
    squeeze.set(1);
    buttonScale.set(1);
  }, [added, buttonScale, demoAdded, elapsed, held, lastTouch, playing, pos, resuming, squeeze]);

  const release = useCallback(() => {
    'worklet';
    held.set(false);
    lastTouch.set(elapsed.get());
  }, [elapsed, held, lastTouch]);

  const move = useCallback((delta: number) => {
    'worklet';
    touch();
    settle(Math.round(pos.get()) + delta);
    release();
  }, [pos, release, settle, touch]);

  const toggle = useCallback(() => {
    'worklet';
    touch();
    const bit = 1 << mod(Math.round(pos.get()));
    added.set(added.get() ^ bit);
    release();
    if (!reduced)
      scheduleOnRN(impact);
  }, [added, pos, reduced, release, touch]);

  useEffect(() => {
    playing.set(autoPlay && !reduced);
  }, [autoPlay, playing, reduced]);

  useFrameCallback((frame) => {
    'worklet';
    if (!active.get())
      return;
    const dt = Math.max(frame.timeSincePreviousFrame ?? 0, 0);
    elapsed.set(elapsed.get() + dt);
    if (!reduced)
      clock.set(mod(clock.get() + dt, T.loop));
    if (playing.get()) {
      timeline.set(timeline.get() + dt);
      const state = autoplayDynamics(timeline.get());
      vel.set(state.velocity);
      arrive.set(state.arrival);
      arrivalAge.set(state.arrivalAge);
      pos.set(state.pos);
      squeeze.set(state.squeeze);
      buttonScale.set(state.buttonScale);
      demoAdded.set(state.demoAdded);
    }
    else if (autoPlay && !reduced && !held.get() && elapsed.get() - lastTouch.get() >= T.resume) {
      if (!resuming.get()) {
        settle(Math.round(pos.get()));
        resuming.set(true);
      }
      else if (Math.abs(pos.get() - Math.round(pos.get())) < 0.001 && Math.abs(vel.get()) < 0.01) {
        const nearest = Math.round(pos.get());
        cancelAnimation(pos);
        pos.set(nearest);
        timeline.set(Math.floor(nearest / 4) * T.loop + mod(nearest) * T.beat);
        playing.set(true);
        resuming.set(false);
      }
    }
    const current = pos.get();
    if (dt > 0 && !playing.get())
      vel.set(reduced ? 0 : vel.get() + (clamp((current - previous.get()) * 1000 / dt,
        -M.velocityMax, M.velocityMax) - vel.get()) * (1 - Math.exp(-dt / T.velocityTau)));
    previous.set(current);
    const rounded = Math.round(current);
    if (centre.get() !== rounded) {
      centre.set(rounded);
      arrivedAt.set(elapsed.get());
      if (!reduced) {
        const next = owed.get().slice();
        next[mod(rounded)] += 1;
        owed.set(next);
      }
      if (!playing.get() && !reduced)
        scheduleOnRN(tick);
    }
    const debt = owed.get();
    if (dt > 0 && (debt[0] > 0 || debt[1] > 0 || debt[2] > 0 || debt[3] > 0)) {
      const paid = surge.get().slice();
      const left = debt.slice();
      for (let i = 0; i < 4; i++) {
        // A quadratic ease-out: the rate falls with the square root of what is still owed.
        const step = Math.min(left[i], 2 * Math.sqrt(left[i]) * dt / T.surge);
        paid[i] = (paid[i] + step) % 1;
        left[i] -= step;
      }
      surge.set(paid);
      owed.set(left);
    }
    const age = clamp((elapsed.get() - arrivedAt.get()) / T.arrival);
    if (!playing.get()) {
      arrive.set(reduced ? 0 : 1 - bezier(age, ...T.uiCurve));
      arrivalAge.set(reduced ? T.arrival : age * T.arrival);
    }
  });

  useAnimatedReaction(() => {
    const index = mod(Math.round(pos.get()));
    return index * 2 + (((added.get() & (1 << index)) !== 0 || (index === 2 && demoAdded.get())) ? 1 : 0);
  }, (next, previousValue) => {
    if (next !== previousValue)
      scheduleOnRN(report, Math.floor(next / 2), next % 2 === 1);
  });
  // Gesture registration stores callbacks; it does not execute them during React render.

  const gesture = useMemo(() => {
    const pan = Gesture.Pan().activeOffsetX([-8, 8]).failOffsetY([-14, 14]).maxPointers(1)
      .onBegin((e) => {
        touch();
        dragging.set(false);
        start.set(pos.get());
        const dx = e.x - D.width * k / 2;
        pressed.set(mod(Math.round(pos.get()) + (Math.abs(dx) < D.card * k / 2 ? 0 : Math.sign(dx))));
        press.set(withTiming(M.press, {
          duration: T.press, easing: ease,
          reduceMotion: ReduceMotion.Never
        }));
      })
      .onStart(() => {
        dragging.set(true);
      })
      .onUpdate((e) => {
        lastTouch.set(elapsed.get());
        pos.set(start.get() - e.translationX
          / (M.drag * k));
      })
      .onEnd((e) => {
        const v = -e.velocityX / (M.drag * k);
        const origin = start.get();
        const target = releaseTarget(pos.get(), origin, v);
        settle(target, v);
      })
      .onFinalize((_e, success) => {
        if (!success && dragging.get() && Math.abs(pos.get() - Math.round(pos.get())) > 0.001)
          settle(Math.round(pos.get()));
        press.set(withSpring(1, M.spring));
        dragging.set(false);
        release();
      });
    const tap = Gesture.Tap().maxDistance(8).onEnd((e, success) => {
      if (!success)
        return;
      const dx = e.x - D.width * k / 2;
      settle(Math.round(pos.get()) + (Math.abs(dx) >= D.card * k / 2 ? Math.sign(dx) : 0));
    }).onFinalize((_e, success) => {
      if (!success && !dragging.get())
        settle(Math.round(pos.get()));
    });
    return Gesture.Race(pan, tap);
  }, [dragging, elapsed, k, lastTouch, pos, press, pressed, release, settle, start, touch]);
  // Observe all touches without becoming the responder or competing with the stage/button.

  const observer = useMemo(() => Gesture.Manual()
    .onTouchesDown(() => {
      touch();
    })
    .onTouchesUp((e, manager) => {
      if (e.numberOfTouches <= 1) {
        release();
        manager.fail();
      }
    })
    .onTouchesCancelled((_e, manager) => {
      release();
      manager.fail();
    })
    .simultaneousWithExternalGesture(...gesture.toGestureArray()), [gesture, release, touch]);

  const accessibilityMove = useCallback((delta: number) => scheduleOnUI(move, delta), [move]);

  const beginTouch = useCallback(() => scheduleOnUI(touch), [touch]);

  const endTouch = useCallback(() => scheduleOnUI(release), [release]);

  const toggleAgenda = useCallback(() => scheduleOnUI(toggle), [toggle]);
  return {
    gesture, observer, accessibilityMove, toggleAgenda, beginTouch, endTouch, selection, reduced,
    values: {
      pos, vel, clock, active, centre, arrive, arrivalAge, surge, pressed, press, squeeze,
      buttonScale, added, demoAdded
    }
  };
}

export type HoloValues = ReturnType<typeof useHoloPager>['values'];
