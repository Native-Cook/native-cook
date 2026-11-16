// Particle Ribbon Voice — the haptics map. Only touches play them; autoplay is silent (DESIGN-SYSTEM §9).
//
//   cue      | when                                           | frame / state           | expo-haptics
//   onset    | a finger lands to talk                          | the twist leaves the left edge | impact Light
//   release  | the finger leaves after a real press            | the sheet collapses to a thread | selection
//   part     | a finger takes hold of the ribbon               | the window opens        | impact Light
//   cross    | a crossing passes under a second finger         | two windows, one rides over the other | selection
//   answer   | the reply's first word appears                  | the first twist crosses x = 201 | notification Success
//   (none)   | a press shorter than 250 ms, the loop, scrubbing the level, the finger's drag
// A cue never fires closer than 60 ms to the previous one (`cueGate`, asked by the component's `cue`).

import * as Haptics from 'expo-haptics';

export const haptic = {
  onset: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
  release: () => void Haptics.selectionAsync(),
  part: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
  cross: () => void Haptics.selectionAsync(),
  answer: () => void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success),
};

export const CUE_GAP_MS = 60;

let lastCueAt = 0;
/** True when a cue may fire now (at least `CUE_GAP_MS` after the last one that did). */
export function cueGate() {
  const now = Date.now();
  if (now - lastCueAt < CUE_GAP_MS) return false;
  lastCueAt = now;
  return true;
}
