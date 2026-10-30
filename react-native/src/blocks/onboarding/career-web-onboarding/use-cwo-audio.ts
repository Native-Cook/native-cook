import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Three short cues, synthesised for this block: a link plucked like a string, a link attaching
// to a role, and the felt marker. They only answer a finger: autoPlay stays silent. The ringer
// switch is respected (no playback in silent mode).
const SOURCES = {
  pluck: require('./assets/sounds/cwo-pluck.wav'),
  attach: require('./assets/sounds/cwo-attach.wav'),
  marker: require('./assets/sounds/cwo-marker.wav'),
};

export type Cue = keyof typeof SOURCES;

export function useCwoAudio(enabled: boolean) {
  const players = useRef<Partial<Record<Cue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<Cue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as Cue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'marker' ? 0.6 : 0.85;
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
