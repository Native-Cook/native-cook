import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Three short cues, synthesised for this block: the sun's jelly boing, a tick per hour, and the
// shades sliding on. They only answer a finger: autoPlay stays silent.
// The ringer switch is respected (no playback in silent mode).
const SOURCES = {
  boing: require('./assets/sounds/suv-boing.wav'),
  tick: require('./assets/sounds/suv-tick.wav'),
  shades: require('./assets/sounds/suv-shades.wav'),
};

export type Cue = keyof typeof SOURCES;

export function useSuvAudio(enabled: boolean) {
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
