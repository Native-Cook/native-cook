import { useEffect, useState } from 'react';
import { AccessibilityInfo } from 'react-native';

/** System "Reduce Transparency": glass turns near-solid and glows are dropped. */
export function useReduceTransparency() {
  const [on, setOn] = useState(false);
  useEffect(() => {
    AccessibilityInfo.isReduceTransparencyEnabled().then(setOn).catch(() => {});
    const sub = AccessibilityInfo.addEventListener('reduceTransparencyChanged', setOn);
    return () => sub.remove();
  }, []);
  return on;
}
