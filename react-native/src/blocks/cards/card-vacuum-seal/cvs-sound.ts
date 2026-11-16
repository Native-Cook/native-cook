// Card Vacuum Seal — sound (Pro upgrade 7). Touch-only: the loop's autoplay is silent. Three synthesised cues
// (.work/card-vacuum-seal/tools/synth_sounds.py): an air hiss loop at −30 dB whose rate follows the film's state,
// a 70 ms "tup" when the film locks, a 200 ms inhale when the air comes back. The host app's audio session is left alone.
import { createAudioPlayer, type AudioPlayer } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

export const SOUND_CUES = {
  air: 'hiss loop at −30 dB while a finger moves the film; rate 0.8 → 1.4 with p',
  tup: '70 ms thump at the lock',
  inhale: '200 ms inhale when the air comes back (revert)',
} as const;

const SOURCES = {
  air: require('./assets/sounds/air.wav'),
  tup: require('./assets/sounds/tup.wav'),
  inhale: require('./assets/sounds/inhale.wav'),
};

export const AIR_VOLUME = 0.032; // −30 dB
export const AIR_RATE = { min: 0.8, max: 1.4 } as const;

export type VacuumSound = {
  /** start or retune the hiss (rate 0.8 … 1.4); `null` stops it */
  air: (rate: number | null) => void;
  tup: () => void;
  inhale: () => void;
};

export function useVacuumSound(enabled: boolean): VacuumSound {
  const players = useRef<Partial<Record<keyof typeof SOURCES, AudioPlayer>>>({});
  const airOn = useRef(false);
  useEffect(() => {
    if (!enabled) return;
    const pool: Partial<Record<keyof typeof SOURCES, AudioPlayer>> = {};
    pool.air = createAudioPlayer(SOURCES.air);
    pool.air.loop = true;
    pool.air.volume = AIR_VOLUME;
    pool.tup = createAudioPlayer(SOURCES.tup);
    pool.tup.volume = 0.12; // Peak stays below -18 dBFS with the bundled 0.85-peak cue.
    pool.inhale = createAudioPlayer(SOURCES.inhale);
    pool.inhale.volume = 0.12;
    players.current = pool;
    return () => {
      players.current = {};
      airOn.current = false;
      Object.values(pool).forEach((player) => player.remove());
    };
  }, [enabled]);
  const air = useCallback((rate: number | null) => {
    const player = players.current.air;
    if (!player) return;
    if (rate === null) {
      if (airOn.current) {
        airOn.current = false;
        player.pause();
        void player.seekTo(0).catch(() => {});
      }
      return;
    }
    player.setPlaybackRate(rate);
    if (!airOn.current) {
      airOn.current = true;
      player.play();
    }
  }, []);
  const shot = useCallback((cue: 'tup' | 'inhale') => {
    const player = players.current[cue];
    if (player)
      void player
        .seekTo(0)
        .then(() => player.play())
        .catch(() => {});
  }, []);
  return {
    air,
    tup: useCallback(() => shot('tup'), [shot]),
    inhale: useCallback(() => shot('inhale'), [shot]),
  };
}
