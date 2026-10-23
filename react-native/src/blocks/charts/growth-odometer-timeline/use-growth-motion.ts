import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import {
  ReduceMotion,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import { advance, drag, initialState, nextMonth, release, touch } from './growth-sim';
const PRESENTATION_FADE_MS = 300;
const PAN_ACTIVATION_PT = 6;
const TAP_MAX_DURATION_MS = 280;
const TAP_MAX_DISTANCE_PT = 8;
const MILLISECONDS_PER_SECOND = 1000;

function tactile(events: number) {
  if (events & 4) {
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy).catch(() => {});
  } else if (events & 2) {
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  } else if (events & 1) {
    void Haptics.selectionAsync().catch(() => {});
  }
}

export function useGrowthMotion(autoPlay: boolean, ready: boolean) {
  const reduced = useReducedMotion();
  const state = useSharedValue(initialState(autoPlay, reduced));
  const active = useSharedValue(false);
  const presented = useSharedValue(0);
  const [settledMonth, setSettledMonth] = useState(0);
  const report = useCallback((events: number, month: number) => {
    tactile(events);
    if (events & 8) setSettledMonth(month);
  }, []);
  const activate = useCallback((enabled: boolean) => {
    'worklet';
    active.set(enabled);
    presented.set(withTiming(enabled ? 1 : 0, {
      duration: PRESENTATION_FADE_MS,
      reduceMotion: ReduceMotion.Never
    }));
  }, [active, presented]);
  useEffect(() => {
    scheduleOnUI(activate, ready);
  }, [activate, ready]);
  useFrameCallback(frame => {
    'worklet';
    if (!active.get() || presented.get() < 1) return;
    const next = advance(state.get(), (frame.timeSincePreviousFrame ?? 0) / MILLISECONDS_PER_SECOND);
    state.set(next);
    if (next.events) scheduleOnRN(report, next.events, next.announced);
  });
  const stepUI = useCallback((direction: number, rewind: boolean, fromGrab: boolean) => {
    'worklet';
    if (!active.get()) return;
    state.set(nextMonth(state.get(), direction, rewind, fromGrab));
  }, [active, state]);
  const adjust = useCallback(
    (direction: number) => scheduleOnUI(stepUI, direction, false, false), [stepUI],
  );
  const next = useCallback(() => scheduleOnUI(stepUI, 1, true, false), [stepUI]);
  const pan = useMemo(() => Gesture.Pan()
    .activeOffsetY([-PAN_ACTIVATION_PT, PAN_ACTIVATION_PT]).maxPointers(1)
    .onBegin(() => {
    if (active.get()) state.set(touch(state.get()));
  })
    .onUpdate(event => {
    if (active.get()) state.set(drag(state.get(), event.translationY, event.velocityY));
  })
    .onFinalize(event => {
    if (active.get()) state.set(release(state.get(), event.velocityY));
  }), [active, state]);
  const tap = useMemo(() => Gesture.Tap()
    .maxDuration(TAP_MAX_DURATION_MS).maxDistance(TAP_MAX_DISTANCE_PT)
    .onBegin(() => {
    if (active.get()) state.set({
      ...state.get(),
      pressed: true
    });
  })
    .onEnd((_e, success) => {
    if (success) stepUI(1, true, true);
  })
    .onFinalize(() => {
    state.set({
      ...state.get(),
      pressed: false
    });
  })
    .simultaneousWithExternalGesture(pan), [active, pan, state, stepUI]);
  return {
    state,
    presented,
    settledMonth,
    pan,
    tap,
    adjust,
    next,
    reduced
  };
}
export type GrowthMotion = ReturnType<typeof useGrowthMotion>;
