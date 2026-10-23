import { useEffect, useRef, useState } from 'react';
import {
  Easing,
  ReduceMotion,
  cancelAnimation,
  useSharedValue,
  withRepeat,
  withSequence,
  withTiming,
} from 'react-native-reanimated';

/** Seconds a touch is left alone before the autoPlay loop starts again. */
const RESUME_AFTER = 3;

/** 0 before `from`, 1 after `to`, a straight line between. */
export function seg(t: number, from: number, to: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - from) / (to - from)));
}

export function easeOut(p: number) {
  'worklet';
  return 1 - (1 - p) * (1 - p) * (1 - p);
}

export function easeInOut(p: number) {
  'worklet';
  return p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
}

export function easeIn(p: number) {
  'worklet';
  return p * p * p;
}

/**
 * A spring's position in closed form, from 0 at `elapsed` = 0 towards 1, for a mass of 1. Written
 * out so that the whole screen is a pure function of the clock and every loop is identical.
 */
export function spring(elapsed: number, stiffness: number, damping: number) {
  'worklet';
  if (elapsed <= 0) return 0;
  const omega = Math.sqrt(stiffness);
  const zeta = damping / (2 * omega);
  const decay = Math.exp(-zeta * omega * elapsed);
  const damped = omega * Math.sqrt(1 - zeta * zeta);
  return 1 - decay * (Math.cos(damped * elapsed) + ((zeta * omega) / damped) * Math.sin(damped * elapsed));
}

/**
 * The screen's clock, in seconds from 0 to `loop`. With `autoPlay` it runs by itself; `play` (a
 * touch) takes over, jumps to `tapAt`, runs to the end and rests there; `autoPlay` then starts
 * again after a pause. The state at 0 and at `loop` is the same.
 */
export function useLoopClock(loop: number, tapAt: number, autoPlay: boolean) {
  const clock = useSharedValue(0);
  const [touches, setTouches] = useState(0);
  const busyUntil = useRef(0);

  useEffect(() => {
    if (!autoPlay) return;
    const run = () => {
      clock.set(0);
      clock.set(
        withRepeat(withTiming(loop, { duration: loop * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }), -1, false),
      );
    };
    if (touches === 0) {
      run();
      return;
    }
    const timer = setTimeout(run, (loop - tapAt + RESUME_AFTER) * 1000);
    return () => clearTimeout(timer);
  }, [autoPlay, touches, loop, tapAt, clock]);

  useEffect(() => () => cancelAnimation(clock), [clock]);

  const play = () => {
    if (Date.now() < busyUntil.current) return false;
    busyUntil.current = Date.now() + (loop - tapAt) * 1000;
    cancelAnimation(clock);
    clock.set(tapAt);
    clock.set(
      withSequence(
        withTiming(loop, { duration: (loop - tapAt) * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }),
        withTiming(0, { duration: 0 }),
      ),
    );
    setTouches((n) => n + 1);
    return true;
  };

  return { clock, play };
}
