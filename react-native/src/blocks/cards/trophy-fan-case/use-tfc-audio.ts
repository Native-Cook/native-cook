import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Four short cues synthesised for this block: the hand fanning out, a card turning over, a card lifted out, a detent tick.
// autoPlay stays silent. The ringer switch is respected (no playback in silent mode).
const SOURCES = {
  fan: require('./assets/sounds/tfc-fan.wav'),
  flip: require('./assets/sounds/tfc-flip.wav'),
  lift: require('./assets/sounds/tfc-lift.wav'),
  tick: require('./assets/sounds/tfc-tick.wav'),
};

export type Cue = keyof typeof SOURCES;

export function useTfcAudio(enabled: boolean) {
  const players = useRef<Partial<Record<Cue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<Cue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as Cue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'tick' ? 0.45 : 0.75;
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
