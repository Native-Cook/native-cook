/** Discrete tactile events only; never called while the demo plays. */
import * as Haptics from 'expo-haptics';

export type SaveHaptic = 'lift' | 'magnet' | 'land' | 'select' | 'commit' | 'success';

export function playSaveHaptic(kind: SaveHaptic): void {
  const ignore = () => {};
  if (kind === 'lift') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(ignore);
  else if (kind === 'magnet') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(ignore);
  else if (kind === 'commit') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy).catch(ignore);
  else if (kind === 'success') void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(ignore);
  else if (kind === 'land') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(ignore);
  else void Haptics.selectionAsync().catch(ignore);
}
