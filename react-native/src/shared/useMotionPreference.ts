import { useEffect, useState } from 'react';
import { AccessibilityInfo, AppState } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';

/** Live accessibility preference, including changes made while this screen is mounted. */
export function useMotionPreference() {
  const initial = useReducedMotion();
  const [reduced, setReduced] = useState(initial);
  useEffect(() => {
    let active = true;
    let revision = 0;
    const refresh = () => {
      const request = ++revision;
      void AccessibilityInfo.isReduceMotionEnabled().then(value => {
        if (active && revision === request) setReduced(value);
      }).catch(() => undefined);
    };
    refresh();
    const motion = AccessibilityInfo.addEventListener('reduceMotionChanged', value => {
      revision++;
      setReduced(value);
    });
    const app = AppState.addEventListener('change', state => { if (state === 'active') refresh(); });
    return () => { active = false; revision++; motion.remove(); app.remove(); };
  }, []);
  return reduced;
}
