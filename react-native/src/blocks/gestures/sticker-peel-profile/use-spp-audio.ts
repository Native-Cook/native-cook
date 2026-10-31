import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Three short cues synthesised for this block: a sticker peeling off, a sticker slapped down, a tap.
// autoPlay stays silent. The ringer switch is respected (no playback in silent mode).
const SOURCES = {
  peel: require('./assets/sounds/spp-peel.wav'),
  slap: require('./assets/sounds/spp-slap.wav'),
  tap: require('./assets/sounds/spp-tap.wav'),
};

export type Cue = keyof typeof SOURCES;

export function useSppAudio(enabled: boolean) {
  const players = useRef<Partial<Record<Cue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<Cue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as Cue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'tap' ? 0.5 : 0.75;
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
