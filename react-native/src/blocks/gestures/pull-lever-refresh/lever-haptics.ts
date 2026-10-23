/** Discrete tactile events only; never called while the demo plays. */
import * as Haptics from 'expo-haptics';

export type LeverHaptic = 'tick' | 'armed' | 'release' | 'success' | 'nudge' | 'stretch' | 'cancel';

export function playLeverHaptic(kind: LeverHaptic): void {
  const done = () => {};
  if (kind === 'tick') void Haptics.selectionAsync().catch(done);
  else if (kind === 'armed') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(done);
  else if (kind === 'release') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy).catch(done); // the commit
  else if (kind === 'stretch') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(done);
  else if (kind === 'cancel') void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning).catch(done);
  else if (kind === 'nudge') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(done);
  else void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(done);
}
