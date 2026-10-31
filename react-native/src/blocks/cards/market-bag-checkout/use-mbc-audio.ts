import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Short cues synthesised for this block. Only a finger plays them; autoPlay stays silent.
const SOURCES = {
  lift: require('./assets/sounds/mbc-lift.wav'),
  drop: require('./assets/sounds/mbc-drop.wav'),
  cinch: require('./assets/sounds/mbc-cinch.wav'),
  paid: require('./assets/sounds/mbc-paid.wav'),
};

export type Cue = keyof typeof SOURCES;

const VOLUME: Record<Cue, number> = { lift: 0.35, drop: 0.6, cinch: 0.5, paid: 0.6 };

export function useMbcAudio(enabled: boolean) {
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
