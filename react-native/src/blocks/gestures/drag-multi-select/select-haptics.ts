/**
 * One tactile event per action; never called while the demo plays (the model drops them when it is silent). A row
 * crossed ticks; the crossing that covers the flight is a detent catching instead (a light impact, in place of the tick).
 */
import * as Haptics from 'expo-haptics';

import type { Haptic } from './model';

export function playSelectHaptic(kind: Haptic): void {
  const ignore = () => {};
  if (kind === 'tick') void Haptics.selectionAsync().catch(ignore);
  else if (kind === 'enter') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(ignore);
  else if (kind === 'cover') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(ignore);
  else if (kind === 'commit') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(ignore);
  else void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(ignore);
}
