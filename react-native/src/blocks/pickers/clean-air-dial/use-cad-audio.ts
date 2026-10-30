import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Four short cues, synthesised for this block: the dial's detent, the fan spinning up, a chime when
// the air turns Good and a button tap. They only answer a finger: autoPlay stays silent.
const SOURCES = {
  tick: require('./assets/sounds/cad-tick.wav'),
  whoosh: require('./assets/sounds/cad-whoosh.wav'),
  chime: require('./assets/sounds/cad-chime.wav'),
  tap: require('./assets/sounds/cad-tap.wav'),
};

export type Cue = keyof typeof SOURCES;

export function useCadAudio(enabled: boolean) {
  const players = useRef<Partial<Record<Cue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<Cue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as Cue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'tick' ? 0.5 : 0.85;
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
