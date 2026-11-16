// Woven Speaker Player — the haptics map (no sound: AGENTS.md has no audio in a block; the concept's 8 s loop is left to the host).
// Only touches play them; autoplay is silent.
//
//   finger lands on the mesh .................... light   (onset: the cloth answers before any drag)
//   play: the first ring leaves the display ..... medium  (120 ms after the tap, the "first note")
//   every 5 s of track crossed while turning .... selection (at most every 100 ms)
//   start or end of the track reached ........... rigid   (once per approach)
//   the position snaps to the second after a coast  light
//   pause: the last ring reaches the rim ........ soft
//   track change (swipe on the title, previous / next)  light
// `haptics={false}` swaps the whole map for silence.

import * as Haptics from 'expo-haptics';

export const haptic = {
  onset: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
  firstNote: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium),
  tick: () => void Haptics.selectionAsync(),
  limit: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid),
  settle: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
  pause: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft),
  track: () => void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
};

const quiet = () => undefined;
export const silence: typeof haptic = {
  onset: quiet,
  firstNote: quiet,
  tick: quiet,
  limit: quiet,
  settle: quiet,
  pause: quiet,
  track: quiet,
};

/** The first ring leaves 120 ms after the tap; the medium beat is delayed to match. */
export const FIRST_NOTE_DELAY_MS = 120;
/** The last ring reaches the rim 0.55 s after the pause. */
export const PAUSE_BEAT_DELAY_MS = 550;
