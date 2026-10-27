import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppState, type GestureResponderEvent } from 'react-native';
import { Easing, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { useMotionPreference } from '@/shared/useMotionPreference';

export function clamp(value: number, min = 0, max = 1) {
  'worklet';
  return Math.min(max, Math.max(min, value));
}

export function segment(time: number, start: number, end: number) {
  'worklet';
  return clamp((time - start) / (end - start));
}

const moveCurve = Easing.bezierFn(.77, 0, .175, 1);
const enterCurve = Easing.bezierFn(.23, 1, .32, 1);

export function ease(value: number) {
  'worklet';
  return moveCurve(value);
}

export function enter(value: number) {
  'worklet';
  return enterCurve(value);
}

export function rubber(value: number, max: number) {
  'worklet';
  if (value < 0) return value / (1 + Math.abs(value) * 4);
  if (value > max) return max + (value - max) / (1 + (value - max) * 4);
  return value;
}

/** A single UI clock; React runs only for lifecycle and deliberate user actions. */
export function usePlayback(loop: number, autoPlay: boolean, restTime: number, previewTime?: number) {
  const remainder = previewTime !== undefined && Number.isFinite(previewTime) ? previewTime % loop : 0;
  const previewPhase = previewTime === undefined ? undefined : remainder < 0 ? remainder + loop : remainder;
  const previewing = previewPhase !== undefined;
  const reduced = useMotionPreference();
  const previousReduced = useRef(reduced);
  const [playing, setPlaying] = useState(autoPlay);
  const [foreground, setForeground] = useState(AppState.currentState === 'active');
  const time = useSharedValue(previewPhase ?? (autoPlay && !reduced ? 0 : restTime));
  // A controlled inspection pose cannot be displaced by a queued interaction callback.
  const displayedTime = useDerivedValue(() => previewPhase ?? time.get());
  const epoch = useSharedValue(-1);
  const enabled = useSharedValue(false);
  const settledTime = useRef(restTime);
  const stopped = useRef(false);
  const readingTouches = useRef(0);
  const readingDrag = useRef(false);
  const readingMomentum = useRef(false);
  const gestureHeld = useRef(false);
  const hasOwnership = useCallback(() => readingTouches.current > 0 || readingDrag.current || readingMomentum.current || gestureHeld.current, []);
  const clearOwnership = useCallback(() => {
    readingTouches.current = 0;
    readingDrag.current = false;
    readingMomentum.current = false;
    gestureHeld.current = false;
  }, []);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const clearQuietTimer = useCallback(() => {
    if (timer.current !== null) clearTimeout(timer.current);
    timer.current = null;
  }, []);

  const frame = useFrameCallback(({ timestamp }) => {
    if (!enabled.get()) return;
    if (epoch.get() < 0) epoch.set(timestamp - time.get() * 1000);
    time.set(((timestamp - epoch.get()) / 1000) % loop);
  }, false);

  useEffect(() => {
    epoch.set(-1);
    if (previewPhase !== undefined) { clearQuietTimer(); time.set(previewPhase); }
    const running = playing && foreground && !reduced && !previewing;
    enabled.set(running);
    frame.setActive(running);
    return () => {
      enabled.set(false);
      frame.setActive(false);
    };
  }, [clearQuietTimer, enabled, epoch, foreground, frame, playing, previewPhase, previewing, reduced, time]);

  useEffect(() => {
    const app = AppState.addEventListener('change', state => {
      setForeground(state === 'active');
      clearQuietTimer();
      if (state === 'active') {
        if (!stopped.current && !hasOwnership()) setPlaying(autoPlay);
      } else clearOwnership();
    });
    return () => {
      app.remove();
      clearQuietTimer();
      clearOwnership();
    };
  }, [autoPlay, clearOwnership, clearQuietTimer, hasOwnership]);

  useEffect(() => {
    if (reduced && !previewing) {
      clearQuietTimer();
      time.set(settledTime.current);
    }
  }, [clearQuietTimer, previewing, reduced, time]);

  useEffect(() => {
    clearQuietTimer();
    if (!stopped.current && !hasOwnership()) setPlaying(autoPlay);
  }, [autoPlay, clearQuietTimer, hasOwnership, previewing]);

  const pause = useCallback(() => {
    clearQuietTimer();
    enabled.set(false);
    setPlaying(false);
  }, [clearQuietTimer, enabled]);

  const settle = useCallback((anchor: number) => {
    pause();
    if (previewPhase !== undefined) { time.set(previewPhase); return; }
    settledTime.current = anchor;
    time.set(anchor);
    epoch.set(-1);
    if (autoPlay && foreground && !reduced && !stopped.current && !hasOwnership()) {
      timer.current = setTimeout(() => {
        timer.current = null;
        if (!stopped.current && !hasOwnership() && AppState.currentState === 'active') setPlaying(true);
      }, 2000);
    }
  }, [autoPlay, epoch, foreground, hasOwnership, pause, previewPhase, reduced, time]);

  useEffect(() => {
    const restoredMotion = previousReduced.current && !reduced;
    previousReduced.current = reduced;
    if (restoredMotion && autoPlay && foreground && !previewing && !stopped.current && !hasOwnership()) {
      // Resume from the selected phase only after the usual reading quiet period.
      settle(time.get());
    }
  }, [autoPlay, foreground, hasOwnership, previewing, reduced, settle, time]);

  const beginGesture = useCallback(() => {
    gestureHeld.current = true;
    pause();
  }, [pause]);
  const endGesture = useCallback(() => {
    gestureHeld.current = false;
  }, []);
  const releaseReading = useCallback(() => {
    // Reading never seeks to a page anchor or emits a landing haptic.
    if (!stopped.current && !hasOwnership()) settle(time.get());
  }, [hasOwnership, settle, time]);
  const readingHandlers = useMemo(() => ({
    onTouchStart: (event: GestureResponderEvent) => {
      readingTouches.current = Math.max(1, event.nativeEvent.touches.length);
      pause();
    },
    onTouchEnd: (event: GestureResponderEvent) => {
      readingTouches.current = event.nativeEvent.touches.length;
      releaseReading();
    },
    onTouchCancel: (event: GestureResponderEvent) => {
      // Other fingers, a native drag, momentum, or the horizontal pan may still own the view.
      readingTouches.current = event.nativeEvent.touches.length;
      releaseReading();
    },
    onScrollBeginDrag: () => { readingDrag.current = true; pause(); },
    onScrollEndDrag: () => { readingDrag.current = false; releaseReading(); },
    onMomentumScrollBegin: () => { readingMomentum.current = true; pause(); },
    onMomentumScrollEnd: () => { readingMomentum.current = false; releaseReading(); },
  }), [pause, releaseReading]);

  const stop = useCallback(() => {
    stopped.current = true;
    clearOwnership();
    pause();
  }, [clearOwnership, pause]);

  const remember = useCallback((anchor: number) => {
    settledTime.current = anchor;
  }, []);

  const restart = useCallback((anchor: number) => {
    stopped.current = false;
    settle(anchor);
  }, [settle]);

  return { time: displayedTime, enabled, playing: playing && foreground && !reduced && !previewing, reduced, pause, settle, stop, restart, remember, readingHandlers, beginGesture, endGesture, previewing, previewPhase };
}
