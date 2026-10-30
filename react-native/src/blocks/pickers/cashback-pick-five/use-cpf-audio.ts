import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Five short cues, synthesised for this block: an icon popping out of its row, landing in the tray,
// going back, a refused pick and the banner's sparkle. They only answer a finger: autoPlay stays silent.
const SOURCES = {
  pop: require('./assets/sounds/cpf-pop.wav'),
  drop: require('./assets/sounds/cpf-drop.wav'),
  back: require('./assets/sounds/cpf-back.wav'),
  nope: require('./assets/sounds/cpf-nope.wav'),
  sparkle: require('./assets/sounds/cpf-sparkle.wav'),
};

export type Cue = keyof typeof SOURCES;

export function useCpfAudio(enabled: boolean) {
  const players = useRef<Partial<Record<Cue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<Cue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as Cue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'sparkle' ? 0.6 : 0.85;
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
