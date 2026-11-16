import * as Haptics from 'expo-haptics';
/** Autoplay is silent. Contact fires once at the 4 pt cushion stop; glyph/arrival only after a real interaction. */
export const CUES = {
  contact: { gap: 4, style: 'rigid', duration: 0.12 },
  glyph: { progress: 0.9, style: 'selection' },
  arrival: { progress: 1, style: 'light' },
  limit: { resistance: 0.25, style: 'rigid' },
} as const;
let lastCue = -Infinity;
function cueAllowed() {
  const now = Date.now();
  if (now - lastCue < 100) return false;
  lastCue = now;
  return true;
}
export function contactHaptic() {
  if (!cueAllowed()) return;
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => undefined);
}
export function tickHaptic() {
  if (!cueAllowed()) return;
  void Haptics.selectionAsync().catch(() => undefined);
}
export function arrivalHaptic() {
  if (!cueAllowed()) return;
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => undefined);
}
