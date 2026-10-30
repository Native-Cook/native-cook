import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Five short cues, synthesised for this block: a marimba note per plan (the happiest for Annual), an
// arpeggio for Continue and a boop for poking the buddy. They only answer a finger.
const SOURCES = {
  pick1: require('./assets/sounds/lbp-pick1.wav'),
  pick2: require('./assets/sounds/lbp-pick2.wav'),
  pick3: require('./assets/sounds/lbp-pick3.wav'),
  notes: require('./assets/sounds/lbp-notes.wav'),
  boop: require('./assets/sounds/lbp-boop.wav'),
};

export type Cue = keyof typeof SOURCES;

export function useLbpAudio(enabled: boolean) {
  const players = useRef<Partial<Record<Cue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<Cue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as Cue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'notes' ? 0.7 : 0.85;
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
