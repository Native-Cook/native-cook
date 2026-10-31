import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Five short 8-bit cues synthesised for this block: the stopper popping, the file gulped down, the
// emerald clicking home, the upload's chime and the spider's skitter. They only answer a finger:
// autoPlay stays silent. The ringer switch is respected (no playback in silent mode).
const SOURCES = {
  pop: require('./assets/sounds/pvu-pop.wav'),
  gulp: require('./assets/sounds/pvu-gulp.wav'),
  clink: require('./assets/sounds/pvu-clink.wav'),
  chime: require('./assets/sounds/pvu-chime.wav'),
  skitter: require('./assets/sounds/pvu-skitter.wav'),
};

export type Cue = keyof typeof SOURCES;

export function usePvuAudio(enabled: boolean) {
  const players = useRef<Partial<Record<Cue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<Cue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as Cue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'skitter' || key === 'clink' ? 0.6 : 0.85;
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
