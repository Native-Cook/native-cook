import { useEffect, useState } from 'react';
import { AccessibilityInfo } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';

/**
 * Reduce Motion and the screen reader, followed live (both can be switched while the screen is
 * open). Seeded from Reanimated's synchronous reading so the first frame is already right.
 * `calm`: either is on — the loop settles to the pin's frame and stops (touch and the accessibility
 * actions still work; Reduce Motion alone also flattens the 3D and shortens the moves).
 */
export function useCalmMotion() {
  const initial = useReducedMotion();
  const [reduceMotion, setReduceMotion] = useState(initial);
  const [screenReader, setScreenReader] = useState(false);
  useEffect(() => {
    let alive = true;
    AccessibilityInfo.isReduceMotionEnabled()
      .then((v) => alive && setReduceMotion(v))
      .catch(() => {});
    AccessibilityInfo.isScreenReaderEnabled()
      .then((v) => alive && setScreenReader(v))
      .catch(() => {});
    const a = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion);
    const b = AccessibilityInfo.addEventListener('screenReaderChanged', setScreenReader);
    return () => {
      alive = false;
      a.remove();
      b.remove();
    };
  }, []);
  return { reduceMotion, screenReader, calm: reduceMotion || screenReader };
}
