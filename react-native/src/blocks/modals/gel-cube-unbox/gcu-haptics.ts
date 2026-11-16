import { useAudioPlayer } from 'expo-audio';
import { useCallback } from 'react';
import * as Haptics from 'expo-haptics';

/** Only user-caused cues. Autoplay is silent; no cue is fired on every frame. */
export const GCU_CUES = {
  grab: 'Soft, touch-down; core 1 → 1.25',
  seal: 'Selection, first upward 6 pt; hairline leak',
  threshold: 'Light, first crossing of p=.45',
  close: 'Medium, closed contact after the two-frame +20% flash',
} as const;
export function gelHaptic(cue: keyof typeof GCU_CUES) {
  const promise =
    cue === 'seal'
      ? Haptics.selectionAsync()
      : Haptics.impactAsync(
          cue === 'grab'
            ? Haptics.ImpactFeedbackStyle.Soft
            : cue === 'close'
              ? Haptics.ImpactFeedbackStyle.Medium
              : Haptics.ImpactFeedbackStyle.Light,
        );
  void promise.catch(() => undefined);
}

/** Optional micro Foley; expo-audio owns disposal and the existing app audio-session policy. */
export function useGelFeedback(sound: boolean) {
  const air = useAudioPlayer(sound ? require('./assets/sounds/air.wav') : null, { updateInterval: 1000 });
  const contact = useAudioPlayer(sound ? require('./assets/sounds/contact.wav') : null, { updateInterval: 1000 });
  return useCallback(
    (cue: keyof typeof GCU_CUES) => {
      gelHaptic(cue);
      if (!sound || (cue !== 'seal' && cue !== 'close')) return;
      const player = cue === 'seal' ? air : contact;
      void player
        .seekTo(0)
        .then(() => player.play())
        .catch(() => undefined);
    },
    [air, contact, sound],
  );
}
