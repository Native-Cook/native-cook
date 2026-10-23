/**
 * One tactile event per action, on the frame it happens; never while the demo plays (the model drops them when it is
 * silent). The wall is the heaviest: lift and drop are Light, each quarter a selection tick, the meeting Medium.
 */
import * as Haptics from 'expo-haptics';

import type { Haptic } from './model';

export function playResizeHaptic(kind: Haptic): void {
  const ignore = () => {};
  if (kind === 'tick') void Haptics.selectionAsync().catch(ignore);
  else if (kind === 'wall') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(ignore);
  else void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(ignore); // lift, drop
}
