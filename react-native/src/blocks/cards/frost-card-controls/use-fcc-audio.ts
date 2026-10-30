import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Four short cues, synthesised for this block: ice growing, ice melting, the card shredded and a
// new card arriving. They only answer a finger: autoPlay stays silent. The ringer switch is
// respected (no playback in silent mode).
const SOURCES = {
  freeze: require('./assets/sounds/fcc-freeze.wav'),
  melt: require('./assets/sounds/fcc-melt.wav'),
  shred: require('./assets/sounds/fcc-shred.wav'),
  reissue: require('./assets/sounds/fcc-reissue.wav'),
};

export type Cue = keyof typeof SOURCES;

export function useFccAudio(enabled: boolean) {
  const players = useRef<Partial<Record<Cue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<Cue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as Cue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'reissue' ? 0.7 : 0.85;
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
