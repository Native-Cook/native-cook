import { Asset } from 'expo-asset';
import { type AudioPlayer, createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';
import { AppState } from 'react-native';

// Original synthesised cues. Completion has one ting per cut; the other cues are
// finger-driven. Preparing an asset creates a player without starting playback.
const SOURCES = {
  claim0: require('./assets/sounds/fhr-claim0.wav'),
  claim1: require('./assets/sounds/fhr-claim1.wav'),
  claim2: require('./assets/sounds/fhr-claim2.wav'),
  claim3: require('./assets/sounds/fhr-claim3.wav'),
  frost: require('./assets/sounds/fhr-frost.wav'),
  tinkle: require('./assets/sounds/fhr-tinkle.wav'),
  cutTing: require('./assets/sounds/fhr-cut-ting.wav'),
  level: require('./assets/sounds/fhr-level.wav'),
};

export type FhrSound = keyof typeof SOURCES;

const VOLUME: Record<FhrSound, number> = {
  claim0: 0.75, claim1: 0.75, claim2: 0.75, claim3: 0.75,
  frost: 0.36, tinkle: 0.55, level: 0.65, cutTing: 0.52,
};

export function useFhrAudio() {
  const players = useRef<Partial<Record<FhrSound, AudioPlayer>>>({});
  const pending = useRef<Partial<Record<FhrSound, Promise<AudioPlayer | undefined>>>>({});
  const mode = useRef<Promise<void> | null>(null);
  const requests = useRef<Partial<Record<FhrSound, number>>>({});
  const mounted = useRef(true);
  const lifetime = useRef(0);
  const generation = useRef(0);

  const stop = useCallback(() => {
    generation.current += 1;
    for (const player of Object.values(players.current)) {
      player?.pause();
      player?.seekTo(0).catch(() => {});
    }
  }, []);

  useEffect(() => {
    mounted.current = true;
    const subscription = AppState.addEventListener('change', (state) => {
      if (state !== 'active') stop();
    });
    return () => {
      mounted.current = false;
      lifetime.current += 1;
      generation.current += 1;
      subscription.remove();
      for (const player of Object.values(players.current)) player?.remove();
      players.current = {};
      pending.current = {};
      mode.current = null;
    };
  }, [stop]);

  const load = useCallback((name: FhrSound) => {
    const existing = players.current[name];
    if (existing) return Promise.resolve(existing);
    const loading = pending.current[name];
    if (loading) return loading;
    const thisLifetime = lifetime.current;
    const asset = Asset.fromModule(SOURCES[name]);
    mode.current ??= setAudioModeAsync({
      playsInSilentMode: false,
      interruptionMode: 'mixWithOthers',
    }).catch(() => {});
    const task = Promise.all([asset.downloadAsync(), mode.current]).then(() => {
      if (!mounted.current || thisLifetime !== lifetime.current) return;
      const player = createAudioPlayer({ uri: asset.localUri ?? asset.uri }, { updateInterval: 250 });
      player.volume = VOLUME[name];
      players.current[name] = player;
      return player;
    }).catch(() => undefined).finally(() => {
      if (pending.current[name] === task) delete pending.current[name];
    });
    pending.current[name] = task;
    return task;
  }, []);

  const prepare = useCallback((name: FhrSound) => load(name).then(() => undefined), [load]);

  const play = useCallback((name: FhrSound) => {
    if (!mounted.current || AppState.currentState !== 'active') return;
    const thisGeneration = generation.current;
    const request = (requests.current[name] ?? 0) + 1;
    requests.current[name] = request;
    const current = () => mounted.current && thisGeneration === generation.current &&
      request === requests.current[name];
    void load(name).then(async (player) => {
      if (!player || !current()) return;
      // A new player is already at zero; play can queue while the native asset finishes loading.
      if (player.currentTime > 0) await player.seekTo(0);
      if (current()) player.play();
    }).catch(() => {});
  }, [load]);

  return { play, prepare, stop };
}
