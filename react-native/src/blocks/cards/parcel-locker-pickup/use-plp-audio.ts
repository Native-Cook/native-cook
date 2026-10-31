import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Short cues synthesised for this block. Only a finger plays them; autoPlay stays silent.
const SOURCES = {
  beep: require('./assets/sounds/plp-beep.wav'),
  latch: require('./assets/sounds/plp-latch.wav'),
  creak: require('./assets/sounds/plp-creak.wav'),
  hum: require('./assets/sounds/plp-hum.wav'),
  lift: require('./assets/sounds/plp-lift.wav'),
  thud: require('./assets/sounds/plp-thud.wav'),
  done: require('./assets/sounds/plp-done.wav'),
  fold: require('./assets/sounds/plp-fold.wav'),
};

export type Cue = keyof typeof SOURCES;

const VOLUME: Record<Cue, number> = { beep: 0.3, latch: 0.7, creak: 0.35, hum: 0.25, lift: 0.4, thud: 0.8, done: 0.6, fold: 0.4 };

export function usePlpAudio(enabled: boolean) {
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
