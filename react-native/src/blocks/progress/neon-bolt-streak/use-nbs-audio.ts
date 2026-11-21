import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Two cues made for this block (≤ −18 dBFS): the strike's 90 ms zap on contact and the ballast's 30 ms tick when the
// tube catches. Only a finger plays them (autoplay is silent) and silent mode is respected.
const SOURCES = {
  zap: require('./assets/zap.wav'),
  tick: require('./assets/ballast-tick.wav'),
};

export type NbsCue = keyof typeof SOURCES;

export function useNbsAudio(enabled: boolean) {
  const players = useRef<Partial<Record<NbsCue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<NbsCue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as NbsCue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'zap' ? 0.7 : 0.6;
      made[key] = p;
    }
    players.current = made;
    setAudioModeAsync({ playsInSilentMode: false, interruptionMode: 'mixWithOthers' }).catch(() => {});
    return () => {
      for (const p of Object.values(made)) p?.remove();
      players.current = {};
    };
  }, [enabled]);

  const play = useCallback((cue: NbsCue) => {
    const p = players.current[cue];
    if (!p) return;
    p.seekTo(0).catch(() => {});
    p.play();
  }, []);
  return { play };
}
