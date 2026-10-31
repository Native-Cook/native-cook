import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Three short cues synthesised for this block: coins chinking off a press, the button melting
// into the bell, and the bell's ding. They only answer a finger: autoPlay stays silent. The
// ringer switch is respected (no playback in silent mode).
const SOURCES = {
  coin: require('./assets/sounds/tec-coin.wav'),
  morph: require('./assets/sounds/tec-morph.wav'),
  ding: require('./assets/sounds/tec-ding.wav'),
};

export type TecCue = keyof typeof SOURCES;

export function useTecAudio(enabled: boolean) {
  const players = useRef<Partial<Record<TecCue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<TecCue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as TecCue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'morph' ? 0.5 : key === 'coin' ? 0.75 : 0.8;
      made[key] = p;
    }
    players.current = made;
    setAudioModeAsync({ playsInSilentMode: false, interruptionMode: 'mixWithOthers' }).catch(() => {});
    return () => {
      for (const p of Object.values(made)) p?.remove();
      players.current = {};
    };
  }, [enabled]);

  /** rate > 1 plays the cue faster and higher: quick taps climb in pitch. */
  return useCallback((cue: TecCue, rate = 1) => {
    const p = players.current[cue];
    if (!p) return;
    p.shouldCorrectPitch = false;
    p.setPlaybackRate(rate);
    p.seekTo(0).catch(() => {});
    p.play();
  }, []);
}
