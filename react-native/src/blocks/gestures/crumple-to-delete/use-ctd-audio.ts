import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Short cues synthesised for this block. Only a finger plays them; autoPlay stays silent.
const SOURCES = {
  rustle: require('./assets/sounds/ctd-rustle.wav'),
  toss: require('./assets/sounds/ctd-toss.wav'),
  bin: require('./assets/sounds/ctd-bin.wav'),
  pop: require('./assets/sounds/ctd-pop.wav'),
  open: require('./assets/sounds/ctd-open.wav'),
};

export type Cue = keyof typeof SOURCES;

const VOLUME: Record<Cue, number> = { rustle: 0.5, toss: 0.4, bin: 0.8, pop: 0.5, open: 0.5 };

export function useCtdAudio(enabled: boolean) {
  const players = useRef<Partial<Record<Cue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
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
