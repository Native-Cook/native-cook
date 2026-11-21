import * as Haptics from 'expo-haptics';

// grab: Soft on a touch of the knob or the pool; detent: a selection tick; hour: Light on a whole hour; limit: Rigid at
// 4 or 12 h; close: Medium when the last light has entered the knob (not at the toggle); bloom: Soft when the pool
// starts to come back; full: Light when it is whole again; success: the CTA.
export type GdrCue = 'grab' | 'detent' | 'hour' | 'limit' | 'close' | 'bloom' | 'full' | 'success';
// Autoplay never calls this map. The UI-thread gate also applies to detents before crossing to JS.
let lastCue = -Infinity;
const history: { cue: GdrCue; at: number }[] = [];
export const gdrHapticHistory = () => history.slice();
export function gdrHaptic(cue: GdrCue) {
  const now = Date.now();
  if (now - lastCue < 60) return;
  lastCue = now;
  if (__DEV__) {
    history.push({ cue, at: now });
    if (history.length > 100) history.shift();
  }
  const result = cue === 'detent' ? Haptics.selectionAsync()
    : cue === 'success' ? Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success)
      : Haptics.impactAsync(cue === 'limit' ? Haptics.ImpactFeedbackStyle.Rigid
        : cue === 'close' ? Haptics.ImpactFeedbackStyle.Medium
          : cue === 'hour' || cue === 'full' ? Haptics.ImpactFeedbackStyle.Light : Haptics.ImpactFeedbackStyle.Soft);
  void result.catch(() => {});
}

// The pour takes a while: the Medium of "the last light entered the knob" and the Light of "whole again" are sent when the
// pigment actually arrives, and dropped if the toggle is moved again before then.
const pending: ReturnType<typeof setTimeout>[] = [];
export function gdrHapticCancel() {
  while (pending.length) clearTimeout(pending.pop());
}
export function gdrHapticLater(cue: GdrCue, ms: number) {
  pending.push(setTimeout(() => gdrHaptic(cue), ms));
}
