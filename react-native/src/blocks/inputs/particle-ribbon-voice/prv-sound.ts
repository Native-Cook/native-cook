import { useCallback, useEffect, useRef } from 'react';
import { createAudioPlayer, type AudioPlayer } from 'expo-audio';

export type RibbonCue = 'open' | 'close';
const SOURCES = {
  open: require('./assets/sounds/listen-open.wav'), // C5 → G5, 240 ms, −20 dBFS: listening starts
  close: require('./assets/sounds/listen-close.wav'), // G4 → C4, 200 ms, −20 dBFS: the press is released
};

/** Two soft tones, touch-only (autoplay is silent; nothing visible depends on them). Does not touch the host's audio session policy. */
export function useRibbonSound(enabled: boolean) {
  const players = useRef<Partial<Record<RibbonCue, AudioPlayer>>>({});
  useEffect(() => {
    if (!enabled) return;
    const pool = Object.fromEntries(Object.entries(SOURCES).map(([key, source]) => [key, createAudioPlayer(source)]));
    players.current = pool;
    return () => {
      players.current = {};
      Object.values(pool).forEach((player) => player.remove());
    };
  }, [enabled]);
  return useCallback((cue: RibbonCue) => {
    const player = players.current[cue];
    if (player)
      void player
        .seekTo(0)
        .then(() => player.play())
        .catch(() => {});
  }, []);
}
