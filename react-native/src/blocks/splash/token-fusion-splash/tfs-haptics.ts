import * as Haptics from 'expo-haptics';

export const HAPTIC_MAP = {
  1: 'Touch-down: light',
  2: 'Neck snaps at gap 110 pt: soft',
  3: 'Capture below 1500 pt/s: medium',
  4: 'Capture above 1500 pt/s: heavy',
  5: 'Brand lands after a user-initiated flood: success',
} as const;
let lastCue = -Infinity;
/** Never invoked for autoplay. Native unavailability must not reject the interaction. */
export function haptic(cue: number) {
  const now = Date.now();
  if (now - lastCue < 60) return;
  lastCue = now;
  const p =
    cue === 5
      ? Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success)
      : Haptics.impactAsync(
          cue === 1
            ? Haptics.ImpactFeedbackStyle.Light
            : cue === 2
              ? Haptics.ImpactFeedbackStyle.Soft
              : cue === 4
                ? Haptics.ImpactFeedbackStyle.Heavy
                : Haptics.ImpactFeedbackStyle.Medium,
        );
  void p.catch(() => undefined);
}
