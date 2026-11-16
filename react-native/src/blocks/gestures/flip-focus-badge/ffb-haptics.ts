// Flip Focus Badge — the haptics map (no sound: a focus app is silent while face down). Only touches play them; the
// autoplay loop and a device turn between detents are silent. `haptics={false}` swaps the whole map for silence.
//
//   finger lands on the badge .............. impact Light (onset)
//   the badge passes a face-on angle ....... selection (a detent; at most every 60 ms)
//   it lands on its back (session starts) .. impact Medium (impact: a medal turned over)
//   it lands on its front (session ends) ... impact Medium
//   an hour is reached (the new numeral) ... notification Success, when the numeral has punched out
//   the numeral pressed into the plate ..... impact Soft, and Soft again when it pops back
//   a session shorter than a minute ........ impact Light (the soft tick of a discarded session)
//   the satellite completes a lap .......... selection

import * as Haptics from 'expo-haptics';

export const haptic = {
  onset: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
  detent: () => void Haptics.selectionAsync(),
  land: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium),
  success: () => void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success),
  press: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft),
  discard: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
  lap: () => void Haptics.selectionAsync(),
};

const quiet = () => undefined;
export const silence: typeof haptic = {
  onset: quiet,
  detent: quiet,
  land: quiet,
  success: quiet,
  press: quiet,
  discard: quiet,
  lap: quiet,
};

/** closer than this to the previous cue, a cue is dropped (DESIGN-SYSTEM §9: 60 ms; never above 10 a second) */
export const MIN_GAP_S = 0.06;
