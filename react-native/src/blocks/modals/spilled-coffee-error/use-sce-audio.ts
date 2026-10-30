import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Five short cues, synthesised for this block: the rewind, the cup set down, the cup tipping over,
// the splash and a tap. They only answer a finger: autoPlay stays silent.
const SOURCES = {
  rewind: require('./assets/sounds/sce-rewind.wav'),
  clack: require('./assets/sounds/sce-clack.wav'),
  tip: require('./assets/sounds/sce-tip.wav'),
  splash: require('./assets/sounds/sce-splash.wav'),
  tap: require('./assets/sounds/sce-tap.wav'),
};

export type Cue = keyof typeof SOURCES;

export function useSceAudio(enabled: boolean) {
  const players = useRef<Partial<Record<Cue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<Cue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as Cue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'rewind' ? 0.6 : 0.85;
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
