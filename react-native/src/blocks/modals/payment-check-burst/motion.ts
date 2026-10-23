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

/** A number in [0, 1) that is always the same for the same pair: the confetti table is built from it. */
export function hash(index: number, salt: number) {
  const x = Math.sin(index * 127.1 + salt * 311.7) * 43758.5453;
  return x - Math.floor(x);
}

/**
 * The screen's clock, in seconds from 0 to `loop`. With `autoPlay` it runs by itself; `play` (a
 * touch) takes over, jumps to a time of the loop, runs to the end and rests there; `autoPlay` then starts
 * again after a pause. The state at 0 and at `loop` is the same.
 */
export function useLoopClock(loop: number, tapAt: number, autoPlay: boolean) {
  const clock = useSharedValue(0);
  const [touches, setTouches] = useState(0);
  const busyUntil = useRef(0);
  // How long after the last touch the clock reaches the end of the loop, in seconds.
  const remaining = useRef(loop - tapAt);

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
    const timer = setTimeout(run, (remaining.current + RESUME_AFTER) * 1000);
    return () => clearTimeout(timer);
  }, [autoPlay, touches, loop, tapAt, clock]);

  useEffect(() => () => cancelAnimation(clock), [clock]);

  /** Runs the loop from `from` to its end. A run in progress is left alone unless `force`. */
  const play = (from = tapAt, force = false) => {
    if (!force && Date.now() < busyUntil.current) return false;
    remaining.current = loop - from;
    busyUntil.current = Date.now() + remaining.current * 1000;
    cancelAnimation(clock);
    clock.set(from);
    clock.set(
      withSequence(
        withTiming(loop, { duration: remaining.current * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }),
        withTiming(0, { duration: 0 }),
      ),
    );
    setTouches((n) => n + 1);
    return true;
  };

  return { clock, play };
}
