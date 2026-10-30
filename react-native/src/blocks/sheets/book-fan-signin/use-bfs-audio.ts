import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Three short cues, synthesised for this block: the covers flying, the sheet settling, and a tap
// on the button. They only answer a finger: autoPlay stays silent. The ringer switch
// is respected (no playback in silent mode).
const SOURCES = {
  flight: require('./assets/sounds/bfs-flight.wav'),
  settle: require('./assets/sounds/bfs-settle.wav'),
  tap: require('./assets/sounds/bfs-tap.wav'),
};

export type Cue = keyof typeof SOURCES;

export function useBfsAudio(enabled: boolean) {
  const players = useRef<Partial<Record<Cue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<Cue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as Cue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'tap' ? 0.6 : 0.85;
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
