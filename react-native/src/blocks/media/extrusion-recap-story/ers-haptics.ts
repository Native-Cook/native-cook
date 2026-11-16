import * as Haptics from 'expo-haptics';
export const CUES = { grab: 'Light', wall: 'Medium', landed: 'Light' } as const;
export function cue(kind: keyof typeof CUES) {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle[CUES[kind]]).catch(() => undefined);
}
