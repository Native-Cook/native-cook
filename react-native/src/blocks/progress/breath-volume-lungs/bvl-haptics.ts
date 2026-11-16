import * as Haptics from 'expo-haptics';

export const CUES = { onset: 'light', litre: 'medium', result: 'success', minimumIntervalMs: 100 } as const;
let lastCue = 0;
/** Touch only. Autoplay never calls this map. Hardware failures are non-fatal. */
export function cue(kind: 'onset' | 'litre' | 'result') {
  const now = Date.now();
  if (now - lastCue < CUES.minimumIntervalMs) return;
  lastCue = now;
  const promise =
    kind === 'result'
      ? Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success)
      : Haptics.impactAsync(kind === 'litre' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light);
  void promise.catch(() => {});
}
