/** Discrete tactile events only; never called while the demo plays. */
import * as Haptics from 'expo-haptics';

export type SwipeHaptic = 'open' | 'arm' | 'disarm' | 'undo' | 'tap' | 'commit';

export function playSwipeHaptic(kind: SwipeHaptic): void {
  const ignore = () => {};
  if (kind === 'open') void Haptics.selectionAsync().catch(ignore);
  else if (kind === 'arm') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(ignore);
  else if (kind === 'disarm') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(ignore);
  else if (kind === 'commit') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy).catch(ignore);
  else if (kind === 'undo') void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(ignore);
  else void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(ignore);
}
