/** Haptics at the moments that matter only. Unsupported devices never reject into the UI. */
import * as Haptics from 'expo-haptics';

export type SummaryHaptic = 'lock' | 'level' | 'commit' | 'ready' | 'restore' | 'lift' | 'copied';

export function playSummaryHaptic(kind: SummaryHaptic): void {
  // A sentence locking in and a depth step are detents; the commit and the way back are light impacts.
  if (kind === 'lock' || kind === 'level') void Haptics.selectionAsync().catch(() => {});
  else if (kind === 'ready' || kind === 'copied') void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else void Haptics.impactAsync(kind === 'commit' ? Haptics.ImpactFeedbackStyle.Soft : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}
