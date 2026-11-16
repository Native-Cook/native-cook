// Card Vacuum Seal — haptic cues (DESIGN-SYSTEM §9). Autoplay is silent; these fire only for a real finger.
import * as Haptics from 'expo-haptics';

export const CUES = {
  onset: 'finger lands on the Protect tile: impact Light',
  detent: 'p crosses 0.45 / 0.6 / 0.75 while held (a new crease forms): selection; at 0.9: impact Rigid',
  impact: 'the film locks (p reaches 1): impact Medium',
  airBack: 'the air returns (p reaches 0 when unsealing): two impact Soft taps 60 ms apart',
  dimple: 'a finger lands on the sealed film: impact Soft',
} as const;

export function hapticOnset() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}
export function hapticDetent() {
  Haptics.selectionAsync();
}
export function hapticRigid() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid);
}
export function hapticLock() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
}
export function hapticAirBack() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft);
  setTimeout(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft), 60);
}
export function hapticDimple() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft);
}
