// Print Line Scrub — the haptics map (touch only; autoplay is silent). One sound, if any, would be the stepper purr; the
// block ships none: nothing visible depends on sound.
//
//   finger lands on the capsule ................ soft (the servo wakes)
//   the line crosses every 10th layer ........... selection (at most every 60 ms)
//   the line hits either end of its range ....... rigid (once per contact)
//   the line is back on the live layer .......... success (the dots flash)

import * as Haptics from 'expo-haptics';

export const haptic = {
  onset: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft),
  tick: () => void Haptics.selectionAsync(),
  limit: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid),
  success: () => void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success),
};
