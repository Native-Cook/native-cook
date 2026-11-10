import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Four short cues, synthesised for this block: the object changing form, the hold charging, the pill
// breaking into beads and the tick. They only answer a finger: autoPlay stays silent, and the ringer
// switch is respected (no playback in silent mode).
const SOURCES = {
  morph: require('./assets/sounds/vcd-morph.wav'),
  fill: require('./assets/sounds/vcd-fill.wav'),
  take: require('./assets/sounds/vcd-take.wav'),
  done: require('./assets/sounds/vcd-done.wav'),
};
const VOLUME = { morph: 0.5, fill: 0.45, take: 0.7, done: 0.6 };

export type Cue = keyof typeof SOURCES;

export function useVcdAudio(enabled: boolean) {
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
  return useCallback((cue: Cue, stop?: boolean) => {
    const p = players.current[cue];
    if (!p) return;
    if (stop) {
      p.pause();
      return;
    }
    p.seekTo(0).catch(() => {});
    p.play();
  }, []);
}
