/** Discrete tactile events only; never called while the demo plays. */
import * as Haptics from 'expo-haptics';

export type SelectHaptic = 'enter' | 'tick' | 'exit' | 'edge' | 'commit';

export function playSelectHaptic(kind: SelectHaptic): void {
  const ignore = () => {};
  if (kind === 'tick') void Haptics.selectionAsync().catch(ignore);
  else if (kind === 'edge') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(ignore);
  else if (kind === 'commit') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy).catch(ignore);
  else if (kind === 'enter') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(ignore);
  else void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(ignore);
}
