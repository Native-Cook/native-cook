import * as Haptics from 'expo-haptics';
export type Cue = 'onset' | 'detent' | 'impact' | 'success' | 'limit' | 'undo';
export const CUES = {
  onset: 'light / touch-down',
  detent: 'selection / step or quarter crossed, max 10 Hz',
  impact: 'medium / drop lands',
  success: 'success / commit toast',
  limit: 'rigid / rim reached once',
  undo: 'light / reverse',
} as const;
// Factory keeps throttling local to an instance; autoplay never calls this map.
export function makeHaptics() {
  let last = -1000;
  return (cue: Cue) => {
    const now = Date.now();
    if (now - last < 85) return;
    last = now;
    const action =
      cue === 'detent'
        ? Haptics.selectionAsync()
        : cue === 'success'
          ? Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success)
          : Haptics.impactAsync(
              cue === 'impact'
                ? Haptics.ImpactFeedbackStyle.Medium
                : cue === 'limit'
                  ? Haptics.ImpactFeedbackStyle.Rigid
                  : Haptics.ImpactFeedbackStyle.Light,
            );
    void action.catch(() => undefined);
  };
}
