import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// Three cues made for this block: the shutter, the feed motor while the print comes out, the print landing on the
// gallery. Only a finger plays them (autoPlay is silent) and silent mode is respected.
const SOURCES = {
  shutter: require('./assets/shutter.wav'),
  motor: require('./assets/motor.wav'),
  land: require('./assets/land.wav'),
};

export type IpcCue = keyof typeof SOURCES;

export function useIpcAudio(enabled: boolean) {
  const players = useRef<Partial<Record<IpcCue, AudioPlayer>>>({});

  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<IpcCue, AudioPlayer>> = {};
    for (const key of Object.keys(SOURCES) as IpcCue[]) {
      const p = createAudioPlayer(SOURCES[key], { updateInterval: 250 });
      p.volume = key === 'motor' ? 0.4 : key === 'land' ? 0.5 : 0.7;
      made[key] = p;
    }
    players.current = made;
    setAudioModeAsync({ playsInSilentMode: false, interruptionMode: 'mixWithOthers' }).catch(() => {});
    return () => {
      for (const p of Object.values(made)) p?.remove();
      players.current = {};
    };
  }, [enabled]);

  const play = useCallback((cue: IpcCue) => {
    const p = players.current[cue];
    if (!p) return;
    p.seekTo(0).catch(() => {});
    p.play();
  }, []);
  return { play };
}
