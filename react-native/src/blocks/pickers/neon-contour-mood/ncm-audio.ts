import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

// One 70 ms cue, synthesised for this block (assets/sounds/ncm-strike.wav): the click of a tube catching and a low mains hum that
// dies away. It answers a finger only, once per strike, a little higher for the greedier moods; an untouched autoplay loop stays
// silent and the ringer switch is respected.
const STRIKE = require('./assets/sounds/ncm-strike.wav');
export const RATE = { low: 0.9, high: 1.15 } as const;

export function useNcmStrike(enabled: boolean) {
  const player = useRef<AudioPlayer | null>(null);
  useEffect(() => {
    if (!enabled) return;
    const p = createAudioPlayer(STRIKE, { updateInterval: 250 });
    p.volume = 0.7;
    p.shouldCorrectPitch = false;
    player.current = p;
    setAudioModeAsync({ playsInSilentMode: false, interruptionMode: 'mixWithOthers' }).catch(() => {});
    return () => {
      p.remove();
      player.current = null;
    };
  }, [enabled]);
  return useCallback((index: number, n: number) => {
    const p = player.current;
    if (!p) return;
    // playbackRate is read-only on expo-audio's player; the setter method changes it.
    p.setPlaybackRate(RATE.low + (RATE.high - RATE.low) * (n > 1 ? index / (n - 1) : 0.5));
    p.seekTo(0).catch(() => {});
    p.play();
  }, []);
}
