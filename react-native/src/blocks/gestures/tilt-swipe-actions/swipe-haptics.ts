/** One tactile event per cause, fired in the frame of the visual. Never called while the demo plays. */
import * as Haptics from 'expo-haptics';

export type SwipeHaptic = 'detent' | 'split' | 'arm' | 'flag' | 'undo';

export function playSwipeHaptic(kind: SwipeHaptic): void {
  const ignore = () => {};
  if (kind === 'detent') void Haptics.selectionAsync().catch(ignore); // the bar breaks (or heals) one piece
  else if (kind === 'split' || kind === 'arm') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(ignore); // the split is sent / Delete arms
  else void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(ignore); // flag, Undo
}
