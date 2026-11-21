import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// The cords' sounds (Codex, original string-mode synthesis, ≤ −18 dBFS): one pluck per string (C4 / E4 / G4, left
// to right), the C-major resolve of a correct answer and the dry snap of a wrong one. Only a finger plays them
// (autoplay is silent) and the silent switch is respected.
const SOURCES = {
  p0: require('./assets/pluck-c4.wav'),
  p1: require('./assets/pluck-e4.wav'),
  p2: require('./assets/pluck-g4.wav'),
  resolve: require('./assets/resolve.wav'),
  snap: require('./assets/snap.wav'),
};

export type EsqCue = keyof typeof SOURCES;

export function useEsqAudio(enabled: boolean) {
  const players = useRef<Partial<Record<EsqCue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<EsqCue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as EsqCue[]) {
      made[key] = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
    }
    players.current = made;
    setAudioModeAsync({ playsInSilentMode: false, interruptionMode: 'mixWithOthers' }).catch(() => {});
    return () => {
      for (const p of Object.values(made)) p?.remove();
      players.current = {};
    };
  }, [enabled]);

  /** level 0..1 scales a pluck by how hard the string was struck */
  const play = useCallback((cue: EsqCue, level = 1) => {
    const p = players.current[cue];
    if (!p) return;
    p.volume = Math.min(Math.max(level, 0.15), 1);
    p.seekTo(0).catch(() => {});
    p.play();
  }, []);
  return { play };
}
