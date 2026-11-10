import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// The piece the speaker plays: 8 s of wind chimes, synthesised for this block, looping. It only plays
// when a finger presses play: autoPlay stays silent, and the ringer switch is respected (no playback in
// silent mode).
const PIECE = require('./assets/sounds/wct-chimes.wav');

export function useWctAudio(enabled: boolean) {
  const player = useRef<AudioPlayer | null>(null);
  useEffect(() => {
    if (!enabled || process.env.EXPO_OS === 'web') return;
    const p = createAudioPlayer(PIECE, { updateInterval: 250 });
    p.loop = true;
    p.volume = 0.8;
    player.current = p;
    setAudioModeAsync({ playsInSilentMode: false, interruptionMode: 'mixWithOthers' }).catch(() => {});
    return () => {
      p.remove();
      player.current = null;
    };
  }, [enabled]);
  return useCallback((on: boolean, at: number) => {
    const p = player.current;
    if (!p) return;
    if (!on) {
      p.pause();
      return;
    }
    p.seekTo(at).catch(() => {});
    p.play();
  }, []);
}
