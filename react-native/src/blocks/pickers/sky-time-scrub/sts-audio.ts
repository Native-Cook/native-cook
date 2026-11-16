import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';

/**
 * Four synthesised cues (assets/sounds, made by .work/sky-time-scrub/tools/make-sounds.py; no third-party material):
 * a tick every 15 minutes (two semitones lower once the sun is down), a clack when the ruler lands on a detent,
 * and a 400 ms warm swell as the sun crosses the horizon. Silent unless the `sound` prop is set.
 */
const SOURCES = {
  tick: require('./assets/sounds/sts-tick.wav'),
  tickNight: require('./assets/sounds/sts-tick-night.wav'),
  clack: require('./assets/sounds/sts-clack.wav'),
  swell: require('./assets/sounds/sts-swell.wav'),
};
export type StsCue = keyof typeof SOURCES;
const VOLUME: Record<StsCue, number> = { tick: 0.22, tickNight: 0.22, clack: 0.5, swell: 0.4 };

export function useStsAudio(enabled: boolean) {
  const players = useRef<Partial<Record<StsCue, AudioPlayer>>>({});
  useEffect(() => {
    if (!enabled) return;
    const made: Partial<Record<StsCue, AudioPlayer>> = {};
    for (const cue of Object.keys(SOURCES) as StsCue[]) {
      const player = createAudioPlayer(SOURCES[cue], { updateInterval: 250 });
      player.volume = VOLUME[cue];
      made[cue] = player;
    }
    players.current = made;
    setAudioModeAsync({ playsInSilentMode: false, interruptionMode: 'mixWithOthers' }).catch(() => {});
    return () => {
      players.current = {};
      for (const player of Object.values(made)) player?.remove();
    };
  }, [enabled]);
  const play = useCallback((cue: StsCue) => {
    const player = players.current[cue];
    if (!player) return;
    player
      .seekTo(0)
      .then(() => {
        if (players.current[cue] === player) player.play();
      })
      .catch(() => {});
  }, []);
  return { play };
}
