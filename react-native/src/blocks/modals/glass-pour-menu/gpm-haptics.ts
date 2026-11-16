// Glass Pour Menu — the haptics map (DESIGN-SYSTEM §9). Only touches play them; autoplay is silent.
//
//   finger grabs the drop ................ soft (onset)
//   the bead pinches off (p ↑ P_PINCH) ... medium (impact)
//   the bead merges back (p ↓ P_PINCH) ... light
//   the bar crosses onto another row ..... selection (detent)
//   a row is chosen ...................... success (Delete: warning)
//   pulled past open (rubber band end) ... rigid, once
//
// Optional local glass cues live in gpm-sound.ts; autoplay is always silent.

import * as Haptics from 'expo-haptics';

export const haptic = {
  grab: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft),
  pinch: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium),
  merge: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
  row: () => void Haptics.selectionAsync(),
  pick: () => void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success),
  pickDanger: () => void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning),
  limit: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid),
};
