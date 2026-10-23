import { useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect } from 'react';
import {
  cancelAnimation, Easing, ReduceMotion, useAnimatedReaction, useDerivedValue,
  useSharedValue, withSpring, withTiming, type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import {
  CARD_COUNT, CARD_EXIT_MS, PACK_CARD_RARITIES, RARE_INTERACTION_MS, RARE_REVEAL_MS,
  dragCardTilt, isRareCard, shouldAdvanceCard,
} from './card-reveal-motion';
import { clamp, OPENING_DURATION_MS, REFERENCE_LAYOUT } from './model';
import type { PackAudioActions } from './pack-audio-controller';

export const REVEAL_CARDS = [
  { name: 'Bulbasaur', source: require('./assets/booster-card-bulbasaur.webp') },
  { name: 'Squirtle', source: require('./assets/booster-card-squirtle.webp') },
  { name: 'Snorlax', source: require('./assets/booster-card-snorlax.webp') },
  { name: 'Wigglytuff ex', source: require('./assets/booster-card-wigglytuff-ex.webp') },
  { name: 'Pikachu ex', source: require('./assets/booster-card-pikachu-ex.webp') },
] as const;

type DragEvent = Readonly<{
  x: number; y: number; translationX: number; translationY: number;
  velocityX: number; velocityY: number;
}>;

function cardHaptic() {
  Haptics.selectionAsync().catch(() => undefined);
}

export function useCardReveal({
  width, height, postCutMs, enabled, reducedMotion, scripted,
  playNextCard, playCardReveal,
}: Readonly<{
  width: number; height: number; postCutMs: SharedValue<number>;
  enabled: boolean; reducedMotion: boolean;
  /** 1 while the autoPlay script drives the cards: its advances give no haptic. */
  scripted: SharedValue<number>;
  playNextCard: PackAudioActions['beginCardAdvance'];
  playCardReveal: PackAudioActions['revealCard'];
}>) {
  // Fixed hooks preload the entire pack, including the back, before interaction.
  const first = useImage(REVEAL_CARDS[0].source);
  const second = useImage(REVEAL_CARDS[1].source);
  const third = useImage(REVEAL_CARDS[2].source);
  const fourth = useImage(REVEAL_CARDS[3].source);
  const fifth = useImage(REVEAL_CARDS[4].source);
  const back = useImage(require('./assets/booster-card-back.webp'));
  const assetsReady = Boolean(first && second && third && fourth && fifth && back);
  const loaded = useSharedValue(false);
  const index = useSharedValue(0);
  const announcedIndex = useSharedValue(-1);
  const yaw = useSharedValue(0);
  const pitch = useSharedValue(0);
  const startYaw = useSharedValue(0);
  const startPitch = useSharedValue(0);
  const dragging = useSharedValue(0);
  const exiting = useSharedValue(0);
  const exit = useSharedValue(0);
  const exitDirection = useSharedValue<1 | -1>(1);
  const revealMs = useSharedValue(RARE_REVEAL_MS);
  const cardWidth = width * REFERENCE_LAYOUT.card.width;
  const cardHeight = height * REFERENCE_LAYOUT.card.height;

  const ready = useDerivedValue(() => enabled && loaded.get() &&
    postCutMs.get() >= OPENING_DURATION_MS - 1);
  const busy = useDerivedValue(() => exiting.get() === 1 ||
    (isRareCard(index.get()) && !reducedMotion && revealMs.get() < RARE_INTERACTION_MS));

  // One event at the actual reveal boundary, including the first card after
  // opening. Keeping this marker through focus changes prevents stale replays.
  useAnimatedReaction(() => ready.get() && !exiting.get() ? index.get() : -1, (next) => {
      if (next < 0 || next === announcedIndex.get()) return;
      announcedIndex.set(next);
      scheduleOnRN(playCardReveal, next, PACK_CARD_RARITIES[next], Date.now());
      // The first card has no exit before it; give it the same arrival beat.
      if (next === 0 && !reducedMotion) {
        revealMs.set(0);
        revealMs.set(withTiming(RARE_REVEAL_MS, {
          duration: RARE_REVEAL_MS, easing: Easing.linear,
          reduceMotion: ReduceMotion.Never,
        }));
      }
    }, [playCardReveal, reducedMotion]);

  useEffect(() => {
    loaded.set(Boolean(first && second && third && fourth && fifth && back));
  }, [first, second, third, fourth, fifth, back, loaded]);

  const reset = useCallback(() => {
    'worklet';
    // Cancellation runs BEFORE resets. A stale exit callback cannot skip a card
    // or bring the previous pack back after replay, resize, or losing focus.
    cancelAnimation(exit);
    cancelAnimation(yaw);
    cancelAnimation(pitch);
    cancelAnimation(revealMs);
    index.set(0);
    announcedIndex.set(-1);
    yaw.set(0);
    pitch.set(0);
    dragging.set(0);
    exiting.set(0);
    exit.set(0);
    exitDirection.set(1);
    revealMs.set(RARE_REVEAL_MS);
  }, [announcedIndex, dragging, exit, exitDirection, exiting, index, pitch, revealMs, yaw]);

  useAnimatedReaction(() => postCutMs.get() < OPENING_DURATION_MS - 1,
    (opening, previous) => { if (opening && !previous) reset(); },
    [reset]);

  useEffect(() => {
    reset();
    return reset;
  }, [width, height, reset]);

  useEffect(() => {
    if (enabled) return;
    // Keep the currently displayed card when a screen loses focus.
    cancelAnimation(exit);
    cancelAnimation(yaw);
    cancelAnimation(pitch);
    cancelAnimation(revealMs);
    dragging.set(0);
    exiting.set(0);
    exit.set(0);
    yaw.set(0);
    pitch.set(0);
    revealMs.set(RARE_REVEAL_MS);
  }, [enabled, dragging, exiting, exit, yaw, pitch, revealMs]);

  const settle = () => {
    'worklet';
    dragging.set(0);
    const config = { duration: 400, dampingRatio: 0.8 };
    yaw.set(withSpring(0, config));
    pitch.set(withSpring(0, config));
  };

  const advance = (direction: 1 | -1 = 1, velocity = 0) => {
    'worklet';
    if (!ready.get() || busy.get() || index.get() >= CARD_COUNT - 1) return;
    dragging.set(0);
    cancelAnimation(yaw);
    cancelAnimation(pitch);
    cancelAnimation(revealMs);
    exitDirection.set(direction);
    exiting.set(1);
    exit.set(0);
    scheduleOnRN(playNextCard, index.get(), Date.now());
    if (scripted.get() === 0) scheduleOnRN(cardHaptic);
    const finish = (finished?: boolean) => {
      'worklet';
      if (!finished) return;
      index.set(index.get() + 1);
      exit.set(0);
      exiting.set(0);
      yaw.set(0);
      pitch.set(0);
      revealMs.set(0);
      revealMs.set(withTiming(RARE_REVEAL_MS, {
        duration: RARE_REVEAL_MS, easing: Easing.linear,
        reduceMotion: ReduceMotion.Never,
      }));
    };
    exit.set(velocity !== 0 && !reducedMotion
      ? withSpring(1, {
          duration: 280, dampingRatio: 1, overshootClamping: true,
          velocity: Math.min(8, Math.abs(velocity) / width),
        }, finish)
      : withTiming(1, {
          duration: reducedMotion ? 160 : CARD_EXIT_MS,
          easing: Easing.bezier(0.23, 1, 0.32, 1),
          reduceMotion: ReduceMotion.Never,
        }, finish));
  };

  const begin = (event: DragEvent) => {
    'worklet';
    if (!ready.get() || busy.get()) return;
    const top = (height - cardHeight) / 2;
    if (Math.abs(event.x - width / 2) > cardWidth / 2 + 18 ||
      event.y < top - 18 || event.y > top + cardHeight + 18) return;
    cancelAnimation(yaw);
    cancelAnimation(pitch);
    startYaw.set(yaw.get());
    startPitch.set(pitch.get());
    dragging.set(1);
  };
  const update = (event: DragEvent) => {
    'worklet';
    if (!dragging.get() || busy.get() || !ready.get()) return;
    const tilt = dragCardTilt(event.translationX, event.translationY, cardWidth);
    yaw.set(reducedMotion ? 0 : clamp(startYaw.get() + tilt.yaw, -0.56, 0.56));
    pitch.set(reducedMotion ? 0 : clamp(startPitch.get() + tilt.pitch, -0.16, 0.16));
  };
  const end = (event: DragEvent, success: boolean) => {
    'worklet';
    if (!dragging.get()) return;
    if (success && ready.get() && index.get() < CARD_COUNT - 1 && shouldAdvanceCard(
      event.translationX, event.translationY, event.velocityX, cardWidth,
    )) {
      advance(event.translationX >= 0 ? 1 : -1, event.velocityX);
    } else settle();
  };
  const cancel = () => {
    'worklet';
    if (dragging.get()) settle();
  };

  const opacity = useDerivedValue(() => clamp((postCutMs.get() - 1490) / 320, 0, 1));

  return {
    index, yaw, pitch, dragging, exiting, exit, exitDirection, revealMs,
    ready, busy, opacity, images: [first, second, third, fourth, fifth], back,
    assetsReady,
    begin, update, end, cancel, advance, reset,
  };
}

export type CardRevealState = ReturnType<typeof useCardReveal>;
