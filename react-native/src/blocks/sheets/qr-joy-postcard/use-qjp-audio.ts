import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Three short cues, synthesised for this block: the modules swirling into a postcard, the tulip
// sticker landing, and the heart with its sparkles. They only answer a finger: autoPlay stays
// silent. The ringer switch is respected (no playback in silent mode).
const SOURCES = {
  flip: require('./assets/sounds/qjp-flip.wav'),
  sticker: require('./assets/sounds/qjp-sticker.wav'),
  sparkle: require('./assets/sounds/qjp-sparkle.wav'),
};

export type Cue = keyof typeof SOURCES;

export function useQjpAudio(enabled: boolean) {
  const players = useRef<Partial<Record<Cue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<Cue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as Cue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'sparkle' ? 0.7 : 0.9;
      made[key] = p;
    }
    players.current = made;
    setAudioModeAsync({ playsInSilentMode: false, interruptionMode: 'mixWithOthers' }).catch(() => {});
    return () => {
      for (const p of Object.values(made)) p?.remove();
      players.current = {};
    };
  }, [enabled]);

  return useCallback((cue: Cue) => {
    const p = players.current[cue];
    if (!p) return;
    p.seekTo(0).catch(() => {});
    p.play();
  }, []);
}
