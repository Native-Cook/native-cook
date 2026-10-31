import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Three cues synthesised for this block: a crystalline tick when the plan thumb crosses into a plan,
// a struck-crystal ting when a plan lands or the gem is tapped, a rising shimmer when Pro lands.
// Only a finger plays them (autoPlay is silent), and silent mode is respected.
const SOURCES = {
  tick: require('./assets/sounds/gcp-tick.wav'),
  ting: require('./assets/sounds/gcp-ting.wav'),
  shimmer: require('./assets/sounds/gcp-shimmer.wav'),
};

export type GcpCue = keyof typeof SOURCES;

export function useGcpAudio(enabled: boolean) {
  const players = useRef<Partial<Record<GcpCue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<GcpCue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as GcpCue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'tick' ? 0.5 : 0.7;
      made[key] = p;
    }
    players.current = made;
    setAudioModeAsync({ playsInSilentMode: false, interruptionMode: 'mixWithOthers' }).catch(() => {});
    return () => {
      for (const p of Object.values(made)) p?.remove();
      players.current = {};
    };
  }, [enabled]);

  const play = useCallback((cue: GcpCue) => {
    const p = players.current[cue];
    if (!p) return;
    p.seekTo(0).catch(() => {});
    p.play();
  }, []);
  return { play };
}
