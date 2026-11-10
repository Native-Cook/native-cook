import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Two short cues, synthesised for this block: a detent of the crown, and the movement ticking when the
// watch is turned over. They only answer a finger: autoPlay stays silent, and the ringer switch is
// respected (no playback in silent mode).
const SOURCES = {
  detent: require('./assets/sounds/swc-detent.wav'),
  tick: require('./assets/sounds/swc-tick.wav'),
};
const VOLUME = { detent: 0.5, tick: 0.55 };

export type Cue = keyof typeof SOURCES;

export function useSwcAudio(enabled: boolean) {
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
