import { useEffect, useState } from 'react';
import { AccessibilityInfo, AppState } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';

/** Keep the system preference current after Settings changes, without polling. */
export function useReducedMotionPreference() {
  const initial = useReducedMotion();
  const [reduced, setReduced] = useState(initial);

  useEffect(() => {
    let mounted = true;
    let version = 0;
    const update = (value: boolean) => {
      version += 1;
      if (mounted) setReduced(value);
    };
    const refresh = () => {
      const requestedVersion = ++version;
      void AccessibilityInfo.isReduceMotionEnabled().then(value => {
        if (mounted && requestedVersion === version) setReduced(value);
      }).catch(() => { /* Retain the last known preference. */ });
    };
    const preference = AccessibilityInfo.addEventListener('reduceMotionChanged', update);
    const lifecycle = AppState.addEventListener('change', state => {
      if (state === 'active') refresh();
    });
    refresh();
    return () => {
      mounted = false;
      preference.remove();
      lifecycle.remove();
    };
  }, []);

  return reduced;
}
