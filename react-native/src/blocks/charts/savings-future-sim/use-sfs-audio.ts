import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Four short cues, synthesised for this block: the curve unfurling and curling back, a tick per year
// or quarter point, and a tap. They only answer a finger: autoPlay stays silent.
const SOURCES = {
  unfurl: require('./assets/sounds/sfs-unfurl.wav'),
  furl: require('./assets/sounds/sfs-furl.wav'),
  tick: require('./assets/sounds/sfs-tick.wav'),
  tap: require('./assets/sounds/sfs-tap.wav'),
};

export type Cue = keyof typeof SOURCES;

export function useSfsAudio(enabled: boolean) {
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
