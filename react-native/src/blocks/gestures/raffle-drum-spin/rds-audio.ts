import { createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useCallback, useEffect, useRef } from 'react';
import { AppState } from 'react-native';

export type RdsCue = 'select' | 'land' | 'ratchet' | 'spin' | 'pop' | 'stitches' | 'morph' | 'burst' | 'tick' | 'tear' | 'landStub' | 'dust';

const ASSETS = {
  select: [require('./assets/sfx/select.wav')],
  land: [require('./assets/sfx/land1.wav'), require('./assets/sfx/land2.wav'), require('./assets/sfx/land3.wav')],
  ratchet: [require('./assets/sfx/ratchet.wav')],
  spin: [require('./assets/sfx/spin.wav')],
  pop: [require('./assets/sfx/pop.wav')],
  stitches: [require('./assets/sfx/stitches.wav')],
  morph: [require('./assets/sfx/morph.wav')],
  burst: [require('./assets/sfx/burst.wav')],
  tick: [require('./assets/sfx/tick.wav')],
  tear: [require('./assets/sfx/tear.wav')],
  landStub: [require('./assets/sfx/land-stub.wav')],
  dust: [require('./assets/sfx/dust.wav')],
};

const VOLUME: Record<RdsCue, number> = {
  select: 0.3, land: 0.46, ratchet: 0.14, spin: 0.12, pop: 0.42,
  stitches: 0.24, morph: 0.3, burst: 0.28, tick: 0.13, tear: 0.36,
  landStub: 0.42, dust: 0.22,
};

type Slot = { player: ReturnType<typeof createAudioPlayer>; generation: number };
type Bank = {
  pools: Partial<Record<RdsCue, Slot[]>>;
  next: Partial<Record<RdsCue, number>>;
  last: Partial<Record<RdsCue, number>>;
  ready: boolean;
  active: boolean;
  disposed: boolean;
};

// Catch synchronous native failures as well as rejected native promises.
async function safely(action: () => unknown) {
  try { await action(); } catch { /* Sound must never interrupt the screen. */ }
}

export function useRaffleAudio(enabled: boolean): (cue: RdsCue) => void {
  const bankRef = useRef<Bank | null>(null);

  useEffect(() => {
    if (!enabled) return;
    const bank: Bank = {
      pools: {}, next: {}, last: {}, ready: false, active: false, disposed: false,
    };
    bankRef.current = bank;
    const stop = () => {
      for (const slots of Object.values(bank.pools)) {
        for (const slot of slots) {
          slot.generation += 1;
          void safely(() => slot.player.pause());
        }
      }
    };
    const updateActive = () => {
      bank.active = AppState.currentState === 'active' || AppState.currentState === null;
      if (!bank.active) stop();
    };
    for (const cue of Object.keys(ASSETS) as RdsCue[]) {
      const sources = ASSETS[cue];
      const count = cue === 'ratchet' || cue === 'tick' ? 3 : sources.length;
      const slots: Slot[] = [];
      bank.pools[cue] = slots;
      for (let i = 0; i < count; i += 1) {
        try {
          const player = createAudioPlayer(sources[i % sources.length], { updateInterval: 500 });
          slots.push({ player, generation: 0 });
          player.volume = VOLUME[cue];
        } catch { /* Other cues can still load if this player fails. */ }
      }
    }
    updateActive();
    let subscription: ReturnType<typeof AppState.addEventListener> | undefined;
    try { subscription = AppState.addEventListener('change', updateActive); } catch { /* Optional audio. */ }
    void safely(async () => {
      await setAudioModeAsync({
        playsInSilentMode: false,
        interruptionMode: 'mixWithOthers',
        allowsRecording: false,
        shouldPlayInBackground: false,
        allowsBackgroundRecording: false,
      });
      if (!bank.disposed) bank.ready = true;
    });
    return () => {
      bank.disposed = true;
      bank.ready = false;
      if (bankRef.current === bank) bankRef.current = null;
      void safely(() => subscription?.remove());
      stop();
      for (const slots of Object.values(bank.pools)) {
        for (const slot of slots) void safely(() => slot.player.remove());
      }
    };
  }, [enabled]);

  return useCallback((cue: RdsCue) => {
    void safely(async () => {
      const bank = bankRef.current;
      if (!bank || !bank.ready || !bank.active || bank.disposed) return;
      const slots = bank.pools[cue];
      if (!slots?.length) return;
      const now = performance.now();
      if ((cue === 'ratchet' || cue === 'tick') && now - (bank.last[cue] ?? -Infinity) < 50) return;
      bank.last[cue] = now;
      const index = bank.next[cue] ?? 0;
      const slot = slots[index % slots.length];
      bank.next[cue] = (index + 1) % slots.length;
      const generation = ++slot.generation;
      await slot.player.seekTo(0);
      if (bankRef.current === bank && bank.ready && bank.active && !bank.disposed && slot.generation === generation) {
        slot.player.play();
      }
    });
  }, []);
}
