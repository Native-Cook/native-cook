import { useCallback, useEffect, useRef } from 'react';
import { createAudioPlayer, type AudioPlayer } from 'expo-audio';

export type CoinSoundCue = 'clink' | 'thunk';
const SOURCES = {
  clink: require('./assets/sounds/clink.wav'),
  clink2: require('./assets/sounds/clink2.wav'),
  thunk: require('./assets/sounds/thunk.wav'),
};

/** Optional, touch-only. Does not change the host app's audio session or the silent-switch policy. */
export function useCoinSound(enabled: boolean) {
  const players = useRef<Record<string, AudioPlayer>>({});
  const flip = useRef(0);
  useEffect(() => {
    if (!enabled) return;
    const pool = Object.fromEntries(Object.entries(SOURCES).map(([key, source]) => [key, createAudioPlayer(source)]));
    players.current = pool;
    return () => {
      players.current = {};
      Object.values(pool).forEach((player) => player.remove());
    };
  }, [enabled]);
  return useCallback((cue: CoinSoundCue) => {
    // two clinks alternate so a run of them does not sound like one sample; the pitch is part of each file
    let key: string = cue;
    if (cue === 'clink') {
      flip.current = 1 - flip.current;
      key = flip.current ? 'clink' : 'clink2';
    }
    const player = players.current[key];
    if (player)
      void player
        .seekTo(0)
        .then(() => player.play())
        .catch(() => {});
  }, []);
}
