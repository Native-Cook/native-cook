import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Four cues synthesised for this block: the balloon inflating, a jelly bloop when the shape
// changes, a soft squish when it lands, a tiny rubber tick per quarter of inflation. Only a finger
// plays them (autoPlay is silent), and silent mode is respected.
const SOURCES = {
  inflate: require('./assets/sounds/pst-inflate.wav'),
  morph: require('./assets/sounds/pst-morph.wav'),
  land: require('./assets/sounds/pst-land.wav'),
  tick: require('./assets/sounds/pst-tick.wav'),
};

export type PstCue = keyof typeof SOURCES;

export function usePstAudio(enabled: boolean) {
  const players = useRef<Partial<Record<PstCue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<PstCue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as PstCue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'tick' ? 0.35 : key === 'inflate' ? 0.55 : 0.7;
      made[key] = p;
    }
    players.current = made;
    setAudioModeAsync({ playsInSilentMode: false, interruptionMode: 'mixWithOthers' }).catch(() => {});
    return () => {
      for (const p of Object.values(made)) p?.remove();
      players.current = {};
    };
  }, [enabled]);

  const play = useCallback((cue: PstCue) => {
    const p = players.current[cue];
    if (!p) return;
    p.seekTo(0).catch(() => {});
    p.play();
  }, []);
  const stop = useCallback((cue: PstCue) => {
    players.current[cue]?.pause();
  }, []);
  return { play, stop };
}
