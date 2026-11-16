import { useCallback, useEffect, useRef } from 'react';
import { createAudioPlayer, type AudioPlayer } from 'expo-audio';

export type GlassSoundCue = 'tap' | 'plip' | 'merge';
const SOURCES = {
  tap: require('./assets/sounds/tap.wav'),
  plip: require('./assets/sounds/plip.wav'),
  merge: require('./assets/sounds/merge.wav'),
};

/** Optional, touch-only. Do not change the host app's global audio session or silent-switch policy. */
export function useGlassSound(enabled: boolean) {
  const players = useRef<Partial<Record<GlassSoundCue, AudioPlayer>>>({});
  useEffect(() => {
    if (!enabled) return;
    const pool = Object.fromEntries(Object.entries(SOURCES).map(([key, source]) => [key, createAudioPlayer(source)]));
    players.current = pool;
    return () => {
      players.current = {};
      Object.values(pool).forEach((player) => player.remove());
    };
  }, [enabled]);
  return useCallback((cue: GlassSoundCue) => {
    const player = players.current[cue];
    if (player)
      void player
        .seekTo(0)
        .then(() => player.play())
        .catch(() => {});
  }, []);
}
