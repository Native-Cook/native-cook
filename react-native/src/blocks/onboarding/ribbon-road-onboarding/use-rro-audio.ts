import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Three short cues synthesised for this block: a ribbon pressed, a ribbon let go, the ribbons racing away.
// autoPlay stays silent. The ringer switch is respected (no playback in silent mode).
const SOURCES = {
  stretch: require('./assets/sounds/rro-stretch.wav'),
  twang: require('./assets/sounds/rro-twang.wav'),
  zip: require('./assets/sounds/rro-zip.wav'),
};

export type Cue = keyof typeof SOURCES;

export function useRroAudio(enabled: boolean) {
  const players = useRef<Partial<Record<Cue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<Cue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as Cue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'stretch' ? 0.5 : 0.75;
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
