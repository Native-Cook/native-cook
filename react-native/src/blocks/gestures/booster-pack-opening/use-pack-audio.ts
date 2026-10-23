import { createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { useEffect, useMemo, useRef } from 'react';
import { AppState } from 'react-native';

import { CARD_AUDIO_ASSETS } from './card-audio-assets';
import { cardAudioCues, NO_CARD_RARITIES, type CardRarity } from './card-rarity';

import {
  createPackAudioController,
  type PackAudioActions,
  type PackAudioController,
} from './pack-audio-controller';

const SWIPE = require('./assets/booster-tear.wav');
const OPEN = require('./assets/booster-open.wav');
const BACKGROUND = require('./assets/booster-ambience-loop.wav');

export function usePackAudio(enabled: boolean, rarities: readonly CardRarity[] = NO_CARD_RARITIES): PackAudioActions {
  const controllerRef = useRef<PackAudioController | null>(null);
  const cues = useMemo(() => cardAudioCues(rarities), [rarities]);

  useEffect(() => {
    if (!enabled) return;
    // Creating the players loads the bundled assets before the first gesture.
    const swipe = createAudioPlayer(SWIPE, { updateInterval: 100 });
    const open = createAudioPlayer(OPEN, { updateInterval: 100 });
    const background = createAudioPlayer(BACKGROUND, { updateInterval: 500 });
    const cardPlayers = Object.fromEntries(cues.map((cue) => [cue,
      createAudioPlayer(CARD_AUDIO_ASSETS[cue], { updateInterval: 100 }),
    ]));
    const controller = createPackAudioController(swipe, open, background, Date.now, cardPlayers);
    controllerRef.current = controller;
    let mounted = true;
    let audioModeReady = false;
    const updateEnabled = () => controller.setEnabled(audioModeReady &&
      (AppState.currentState === 'active' || AppState.currentState === null));
    const subscription = AppState.addEventListener('change', updateEnabled);
    const backgroundStatus = background.addListener('playbackStatusUpdate', (status) => {
      if (mounted && status.isLoaded) controller.refreshBackground();
    });
    void setAudioModeAsync({
      playsInSilentMode: true,
      interruptionMode: 'mixWithOthers',
      allowsRecording: false,
      shouldPlayInBackground: false,
      allowsBackgroundRecording: false,
    }).then(() => {
      if (!mounted) return;
      audioModeReady = true;
      updateEnabled();
    }).catch(() => {
      // Audio setup failure must not prevent interaction with the pack.
    });
    return () => {
      mounted = false;
      subscription.remove();
      backgroundStatus.remove();
      controllerRef.current = null;
      controller.dispose();
    };
  }, [enabled, cues]);

  // Worklet bridges keep these identities across focus and AppState changes.
  return useMemo(() => ({
    beginTear: () => controllerRef.current?.beginTear(),
    setTearing: (moving: boolean) => controllerRef.current?.setTearing(moving),
    commitCut: () => controllerRef.current?.commitCut(),
    beginCardAdvance: (fromIndex: number, requestedAt?: number) =>
      controllerRef.current?.beginCardAdvance(fromIndex, requestedAt),
    revealCard: (index: number, rarity: CardRarity, requestedAt?: number) =>
      controllerRef.current?.revealCard(index, rarity, requestedAt),
    reset: () => controllerRef.current?.reset(),
  }), []);
}
