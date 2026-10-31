import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Three cues synthesised for this block: a dry click when the hold starts, a heavy glug when the coin
// goes under the mercury, a struck metal disc when the new coin stands up.
// Only a finger plays them (autoPlay is silent), and silent mode is respected.
const SOURCES = {
  press: require('./assets/sounds/lms-press.wav'),
  pour: require('./assets/sounds/lms-pour.wav'),
  cast: require('./assets/sounds/lms-cast.wav'),
};

export type LmsCue = keyof typeof SOURCES;

export function useLmsAudio(enabled: boolean) {
  const players = useRef<Partial<Record<LmsCue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<LmsCue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as LmsCue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'press' ? 0.5 : 0.7;
      made[key] = p;
    }
    players.current = made;
    setAudioModeAsync({ playsInSilentMode: false, interruptionMode: 'mixWithOthers' }).catch(() => {});
    return () => {
      for (const p of Object.values(made)) p?.remove();
      players.current = {};
    };
  }, [enabled]);

  const play = useCallback((cue: LmsCue) => {
    const p = players.current[cue];
    if (!p) return;
    p.seekTo(0).catch(() => {});
    p.play();
  }, []);
  return { play };
}
