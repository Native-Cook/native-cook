import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Two short cues, synthesised for this block: the metal melting, and the new medal being stamped into
// it. They only answer a finger: autoPlay stays silent, and the ringer switch is respected (no
// playback in silent mode).
const SOURCES = {
  melt: require('./assets/sounds/gem-melt.wav'),
  stamp: require('./assets/sounds/gem-stamp.wav'),
};
const VOLUME = { melt: 0.55, stamp: 0.65 };

export type Cue = keyof typeof SOURCES;

export function useGemAudio(enabled: boolean) {
  const players = useRef<Partial<Record<Cue, AudioPlayer>>>({});
  useEffect(() => {
    if (!enabled || process.env.EXPO_OS === 'web') return;
    const made: Partial<Record<Cue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as Cue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = VOLUME[key];
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
