import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Four short cues synthesised for this block: the reader's tick, the gate's chime, the ticket printing,
// the ticket sliding back. Only a finger plays them; autoPlay stays silent. Silent mode is respected.
const SOURCES = {
  tick: require('./assets/sounds/trp-tick.wav'),
  chime: require('./assets/sounds/trp-chime.wav'),
  print: require('./assets/sounds/trp-print.wav'),
  close: require('./assets/sounds/trp-close.wav'),
};

export type Cue = keyof typeof SOURCES;

const VOLUME: Record<Cue, number> = { tick: 0.35, chime: 0.7, print: 0.45, close: 0.5 };

export function useTrpAudio(enabled: boolean) {
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
