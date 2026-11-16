import * as Haptics from 'expo-haptics';

/**
 * Cue sheet (user input only; autoplay is silent). One cue per 100 ms at most, enforced by the controller.
 *   onset       Medium     the lens inflates under a long press, a drag, or a grab of the sun
 *   step        selection  every 15 minutes of scrub
 *   hour        Light      every whole hour (replaces the step cue on that minute)
 *   landing     Rigid      the ruler settles on a detent
 *   lowerLimb   Medium     the sun's lower limb touches the horizon (either direction), once per crossing
 *   upperLimb   Soft       the sun's upper limb is gone below the horizon (either direction), once per crossing
 */
export type HapticCue = 'onset' | 'step' | 'hour' | 'landing' | 'lowerLimb' | 'upperLimb';
export const HAPTIC_CUES = {
  onset: 'Medium',
  step: 'selection',
  hour: 'Light',
  landing: 'Rigid',
  lowerLimb: 'Medium',
  upperLimb: 'Soft',
} as const;
const STYLE = {
  onset: Haptics.ImpactFeedbackStyle.Medium,
  hour: Haptics.ImpactFeedbackStyle.Light,
  landing: Haptics.ImpactFeedbackStyle.Rigid,
  lowerLimb: Haptics.ImpactFeedbackStyle.Medium,
  upperLimb: Haptics.ImpactFeedbackStyle.Soft,
} as const;
export function haptic(cue: HapticCue) {
  if (cue === 'step') void Haptics.selectionAsync().catch(() => {});
  else void Haptics.impactAsync(STYLE[cue]).catch(() => {});
}
