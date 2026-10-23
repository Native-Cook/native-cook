/** Discrete tactile events only; never called while the demo plays. */
import * as Haptics from 'expo-haptics';

export type ResizeHaptic = 'lift' | 'detent' | 'tap';

export function playResizeHaptic(kind: ResizeHaptic): void {
  const ignore = () => {};
  if (kind === 'lift') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(ignore);
  else if (kind === 'detent') void Haptics.selectionAsync().catch(ignore);
  else void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(ignore);
}
