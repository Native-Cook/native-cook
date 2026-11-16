import * as Haptics from 'expo-haptics';

/** Cue -> impact: a stop crossed is weighted by the mood (Medium at the ends, Soft in the middle, Light elsewhere). */
export type Cue = 'onset' | 'detent' | 'release' | 'limit' | 'next' | 'pulse';
let lastCue = 0;
/** Guards against a burst of cues inside one Taptic event; short enough that every stop of a quick drag still ticks. */
const MIN_GAP_MS = 35;
export function haptic(cue: Cue, weight = 1) {
  const now = Date.now();
  if (now - lastCue < MIN_GAP_MS) return;
  lastCue = now;
  const style =
    cue === 'limit' || (cue === 'detent' && weight === 2)
      ? Haptics.ImpactFeedbackStyle.Medium
      : cue === 'pulse' || (cue === 'detent' && weight === 0)
        ? Haptics.ImpactFeedbackStyle.Soft
        : Haptics.ImpactFeedbackStyle.Light;
  void Haptics.impactAsync(style).catch(() => undefined);
}
