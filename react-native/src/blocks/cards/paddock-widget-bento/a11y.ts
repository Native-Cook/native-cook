import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { AccessibilityInfo, Platform } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';

/** Light / medium impact; nothing on the web, and a refused haptic is ignored. */
export function impact(style: Haptics.ImpactFeedbackStyle = Haptics.ImpactFeedbackStyle.Light) {
  if (Platform.OS === 'web') return;
  Haptics.impactAsync(style).catch(() => {});
}

/** A selection tick. */
export function selection() {
  if (Platform.OS === 'web') return;
  Haptics.selectionAsync().catch(() => {});
}

export const ImpactStyle = Haptics.ImpactFeedbackStyle;

/** Reduce Motion, followed live (Reanimated's hook gives the first frame, the listener every change after it). */
export function useReduceMotion() {
  const [reduced, setReduced] = useState(useReducedMotion());
  useEffect(() => {
    let mounted = true;
    AccessibilityInfo.isReduceMotionEnabled()
      .then((on) => mounted && setReduced(on))
      .catch(() => {});
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    return () => {
      mounted = false;
      sub.remove();
    };
  }, []);
  return reduced;
}
