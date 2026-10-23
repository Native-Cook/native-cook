/** Discrete tactile events only. Unsupported device haptics never reject into the UI. */
import * as Haptics from 'expo-haptics';

import { HAPTIC } from './tear-motion-constants';

export function playTearHaptic(event: number): void {
  if (event === HAPTIC.tooth) void Haptics.selectionAsync().catch(() => {});
  else if (event === HAPTIC.face) void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else {
    const style = event === HAPTIC.open ? Haptics.ImpactFeedbackStyle.Medium
      : event === HAPTIC.peek || event === HAPTIC.land ? Haptics.ImpactFeedbackStyle.Soft
        : Haptics.ImpactFeedbackStyle.Light;
    void Haptics.impactAsync(style).catch(() => {});
  }
}
