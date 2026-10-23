/** Haptics at the moments that matter only. Unsupported devices never reject into the UI. */
import * as Haptics from 'expo-haptics';

export type SummaryHaptic = 'armed' | 'disarmed' | 'land' | 'ready' | 'restore';

export function playSummaryHaptic(kind: SummaryHaptic): void {
  if (kind === 'armed' || kind === 'disarmed') void Haptics.selectionAsync().catch(() => {});
  else if (kind === 'ready') void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else void Haptics.impactAsync(kind === 'land' ? Haptics.ImpactFeedbackStyle.Soft : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}
