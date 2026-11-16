import * as Haptics from 'expo-haptics';
/** User gestures only. Autoplay and decorative pulses are always silent. */
export const HAPTIC_CUES = { onset: 'Light', kilometre: 'Selection', heatThreshold: 'Rigid' } as const;
export function onsetHaptic() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}
export function kilometreHaptic() {
  void Haptics.selectionAsync().catch(() => {});
}
export function peakHaptic() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
}
