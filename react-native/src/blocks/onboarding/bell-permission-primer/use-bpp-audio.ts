import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Three short cues, synthesised for this block: the bell's clapper striking, the permission granted
// and the notification arriving. They only answer a finger: autoPlay stays silent, and the ringer
// switch is respected (no playback in silent mode).
const SOURCES = {
  ding: require('./assets/sounds/bpp-ding.wav'),
  allow: require('./assets/sounds/bpp-allow.wav'),
  banner: require('./assets/sounds/bpp-banner.wav'),
};
const VOLUME = { ding: 0.55, allow: 0.6, banner: 0.6 };

export type Cue = keyof typeof SOURCES;

export function useBppAudio(enabled: boolean) {
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
