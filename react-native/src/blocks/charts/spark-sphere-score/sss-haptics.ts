// Spark Sphere Score — the haptics map. Touch only: autoPlay and the return spring are silent. At most one cue per 60 ms.
//
//   finger lands on the ruler ................ light (the cursor grows)
//   the cursor crosses every 0.1x ............. selection
//   the cursor crosses 1.0x or 0.0x ........... rigid (the label flips younger / older; the cell freezes)
//   the cursor is pulled past either end ...... rigid, once per contact
//   the cell has cooled back to its reading ... light
//   finger lands on the sphere ................ soft (a warm probe)
//   a tap on the crystal ...................... rigid (the dislocation ring)

import * as Haptics from 'expo-haptics';

export const haptic = {
  onset: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
  detent: () => void Haptics.selectionAsync(),
  cross: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid),
  limit: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid),
  settle: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
  probe: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft),
  tap: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid),
};
