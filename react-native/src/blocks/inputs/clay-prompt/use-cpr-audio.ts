import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Three cues synthesised for this block: a soft clay "blup" when a word pops out as clay, a squelch
// when the pieces melt into one blob, a warm bloom when the picture opens.
// Only a finger plays them (autoPlay is silent), and silent mode is respected.
const SOURCES = {
  pop: require('./assets/sounds/cpr-pop.wav'),
  melt: require('./assets/sounds/cpr-melt.wav'),
  bloom: require('./assets/sounds/cpr-bloom.wav'),
};

export type CprCue = keyof typeof SOURCES;

export function useCprAudio(enabled: boolean) {
  const players = useRef<Partial<Record<CprCue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<CprCue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as CprCue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'pop' ? 0.6 : 0.7;
      made[key] = p;
    }
    players.current = made;
    setAudioModeAsync({ playsInSilentMode: false, interruptionMode: 'mixWithOthers' }).catch(() => {});
    return () => {
      for (const p of Object.values(made)) p?.remove();
      players.current = {};
    };
  }, [enabled]);

  const play = useCallback((cue: CprCue) => {
    const p = players.current[cue];
    if (!p) return;
    p.seekTo(0).catch(() => {});
    p.play();
  }, []);
  return { play };
}
