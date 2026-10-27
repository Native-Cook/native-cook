import { useEffect, useState } from 'react';
import { AppState } from 'react-native';
import { useMotionPreference } from '@/shared/useMotionPreference';
import { useFrameCallback, useSharedValue } from 'react-native-reanimated';

export function phase(t: number, start: number, end: number) {
  'worklet';
  const x = Math.min(1, Math.max(0, (t - start) / (end - start)));
  return x * x * (3 - 2 * x);
}

/** Avoid adding/subtracting a period for positive poses: that changes boundary ulps. */
export function normalizeSceneTime(value: number, period: number) {
  'worklet';
  if (!Number.isFinite(value) || !Number.isFinite(period) || period <= 0) return 0;
  const remainder = value % period;
  return remainder < 0 ? remainder + period : remainder;
}

/** One UI-runtime clock. Pauses retain phase; no React work occurs per frame. */
export function useSceneClock(period: number, running: boolean, settled: number, initial = 0, previewTime?: number) {
  const reduced = useMotionPreference();
  const [foreground, setForeground] = useState(AppState.currentState === 'active');
  const time = useSharedValue(previewTime !== undefined ? normalizeSceneTime(previewTime, period) : reduced ? settled : initial);
  const anchor = useSharedValue(-1);
  useEffect(() => {
    const app = AppState.addEventListener('change', state => setForeground(state === 'active'));
    return () => app.remove();
  }, []);
  const frame = useFrameCallback(info => {
    if (anchor.get() < 0) anchor.set(info.timestamp - time.get() * 1000);
    time.set(((info.timestamp - anchor.get()) / 1000) % period);
  }, false);
  useEffect(() => {
    anchor.set(-1);
    if (previewTime !== undefined) time.set(normalizeSceneTime(previewTime, period));
    else if (reduced) time.set(settled);
    frame.setActive(running && foreground && !reduced && previewTime === undefined);
    return () => frame.setActive(false);
  }, [anchor, foreground, frame, period, previewTime, reduced, running, settled, time]);
  return { time, anchor, foreground, reduced };
}
