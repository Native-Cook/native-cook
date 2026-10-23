/** Discrete tactile events only, each at the frame the mechanism makes it; never called while the demo plays. */
import * as Haptics from 'expo-haptics';

export type LeverHaptic = 'tick' | 'full' | 'done';

export function playLeverHaptic(kind: LeverHaptic): void {
  const ignore = () => {};
  if (kind === 'tick') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(ignore); // a charge drawn: +3 bar
  else if (kind === 'full') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(ignore); // 9 bar: let go
  else void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(ignore); // the shot is done
}
