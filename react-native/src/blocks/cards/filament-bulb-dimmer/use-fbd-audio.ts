import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Three short cues, synthesised for this block: the socket's pull switch clicking, the cord springing
// back, and a fingertip on the glass. They only answer a finger: autoPlay stays silent, and the ringer
// switch is respected (no playback in silent mode).
const SOURCES = {
  pull: require('./assets/sounds/fbd-pull.wav'),
  release: require('./assets/sounds/fbd-release.wav'),
  tink: require('./assets/sounds/fbd-tink.wav'),
};
const VOLUME = { pull: 0.85, release: 0.6, tink: 0.35 };

export type Cue = keyof typeof SOURCES;

export function useFbdAudio(enabled: boolean) {
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
