import { useEffect, useState } from 'react';
import { AccessibilityInfo } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';

/**
 * Reduce Motion and the screen reader, followed live. Seeded from Reanimated's launch-time value so
 * the first frame is already right; both stop the decorative loop (touch keeps working).
 */
export function useCalmMotion() {
  const initial = useReducedMotion();
  const [reduceMotion, setReduceMotion] = useState(initial);
  const [screenReader, setScreenReader] = useState(false);
  useEffect(() => {
    let alive = true;
    AccessibilityInfo.isReduceMotionEnabled().then((on) => alive && setReduceMotion(on)).catch(() => {});
    AccessibilityInfo.isScreenReaderEnabled().then((on) => alive && setScreenReader(on)).catch(() => {});
    const motion = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion);
    const reader = AccessibilityInfo.addEventListener('screenReaderChanged', setScreenReader);
    return () => {
      alive = false;
      motion.remove();
      reader.remove();
    };
  }, []);
  return { reduceMotion, screenReader };
}
