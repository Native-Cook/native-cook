import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import {
  ReduceMotion, useFrameCallback, useReducedMotion, useSharedValue, withDelay, withSequence, withSpring, withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { BALANCE } from './data';
import { EASE_IN_OUT, EASE_OUT, FADE_MS, HIDE, PULL, pose, RESUME_AFTER_MS, rubber, SPRING, SWAP_TURN, TURN } from './motion';
import { Header, LatestTransactions, QuickTopUp, TabBar } from './Sections';
import { COLOR, topOffset } from './theme';
import { Wallet } from './Wallet';

export type WalletCardStackProps = {
  autoPlay?: boolean;
  /** Dev only: show the screen still, as the pin (any value), for pixel comparisons. */
  freezeAt?: number;
};

const HIDE_MS = HIDE.charStagger * (BALANCE.amount.length - 1) + HIDE.charDuration;

export function WalletCardStack({ autoPlay = false, freezeAt }: WalletCardStackProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const oy = topOffset(insets.top);
  const reduced = useReducedMotion();
  const playScript = autoPlay && !reduced && freezeAt === undefined;

  const frontLift = useSharedValue(0);
  const backLift = useSharedValue(0);
  const tilt = useSharedValue(0);
  const squish = useSharedValue(0);
  const hidden = useSharedValue(0);
  const swapTurn = useSharedValue(0);
  const turn = useSharedValue(0);
  const grab = useSharedValue(0);
  // The loop's one clock (ms). Every animated value of the autoplay is pose(clock).
  const clock = useSharedValue(freezeAt ?? 0);
  const playing = useSharedValue(false);
  const [hiddenNow, setHiddenNow] = useState(false);
  const hiddenRef = useRef(false);
  const touching = useRef(false);
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  const silent = useRef(playScript); // no haptics while the script plays

  const buzz = useCallback((style: Haptics.ImpactFeedbackStyle) => {
    if (!silent.current) void Haptics.impactAsync(style);
  }, []);

  // --- The pin's own elements, set in motion ---
  const pull = useCallback((lift: number) => {
    if (reduced) return;
    frontLift.set(withSpring(lift, SPRING.pull));
    backLift.set(withSpring(PULL.backLift * (lift / PULL.lift), SPRING.pull));
    tilt.set(withSpring(PULL.tilt * (lift / PULL.lift), SPRING.pull));
  }, [backLift, frontLift, reduced, tilt]);

  const drop = useCallback((velocity = 0) => {
    'worklet';
    if (reduced) return;
    frontLift.set(withSpring(0, { ...SPRING.drop, velocity }));
    backLift.set(withSpring(0, SPRING.drop));
    tilt.set(withSpring(0, SPRING.drop));
    turn.set(withSpring(0, SPRING.drop));
    // The leather gives as the card bottoms out (~110 ms into the drop).
    squish.set(withDelay(110, withSequence(
      withTiming(1, { duration: 90, easing: EASE_OUT }),
      withSpring(0, SPRING.squish),
    )));
  }, [backLift, frontLift, reduced, squish, tilt, turn]);

  const setHidden = useCallback((next: boolean) => {
    hiddenRef.current = next;
    setHiddenNow(next);
    hidden.set(withTiming(next ? 1 : 0, reduced
      ? { duration: FADE_MS, reduceMotion: ReduceMotion.Never }
      : { duration: HIDE_MS, easing: EASE_IN_OUT }));
  }, [hidden, reduced]);

  const toggleEye = useCallback(() => {
    buzz(Haptics.ImpactFeedbackStyle.Light);
    setHidden(!hiddenRef.current);
  }, [buzz, setHidden]);

  const swap = useCallback(() => {
    buzz(Haptics.ImpactFeedbackStyle.Light);
    if (reduced) return;
    swapTurn.set(withTiming(Math.round(swapTurn.get()) + 1, { duration: SWAP_TURN, easing: EASE_IN_OUT }));
    // The front card is nudged down into the pocket and springs back, as if the cards were shuffled.
    frontLift.set(withSequence(withTiming(-9, { duration: 130, easing: EASE_OUT }), withSpring(0, SPRING.drop)));
    squish.set(withSequence(withTiming(0.6, { duration: 110, easing: EASE_OUT }), withSpring(0, SPRING.squish)));
  }, [buzz, frontLift, reduced, squish, swapTurn]);

  const tapCard = useCallback(() => {
    // One haptic per action: the landing below.
    pull(PULL.tapLift);
    timers.current.push(setTimeout(() => {
      drop();
      buzz(Haptics.ImpactFeedbackStyle.Medium);
    }, 520));
  }, [buzz, drop, pull]);

  // --- Autoplay: every frame is pose(t) of one clock, so the Swift and Kotlin ports match it frame for frame ---
  const clearTimers = useCallback(() => {
    timers.current.forEach(clearTimeout);
    timers.current = [];
  }, []);

  const apply = useCallback((t: number) => {
    'worklet';
    const p = pose(t, reduced);
    frontLift.set(p.lift);
    backLift.set(p.back);
    tilt.set(p.lean);
    turn.set(p.turn);
    squish.set(p.squish);
    hidden.set(p.hidden);
  }, [backLift, frontLift, hidden, reduced, squish, tilt, turn]);

  const frameDriver = useFrameCallback(frame => {
    if (!playing.get()) return;
    clock.set(clock.get() + (frame.timeSincePreviousFrame ?? 0));
    apply(clock.get());
  }, false);
  const playScriptRef = useRef(playScript);
  useLayoutEffect(() => {
    playScriptRef.current = playScript;
  }, [playScript]);

  useEffect(() => {
    let resetHiddenTimer: ReturnType<typeof setTimeout> | null = null;
    clearTimers();
    // The same screen can be reopened with or without a freeze: restart the clock from rest either way.
    clock.set(freezeAt ?? 0);
    playing.set(false);
    frameDriver.setActive(false);
    apply(freezeAt ?? 0);
    if (playScript) {
      silent.current = true;
      playing.set(true);
      frameDriver.setActive(true);
    } else if (reduced) {
      silent.current = false;
      hiddenRef.current = false;
      resetHiddenTimer = setTimeout(() => setHiddenNow(false), 0);
    }
    return () => {
      if (resetHiddenTimer) clearTimeout(resetHiddenTimer);
      frameDriver.setActive(false);
    };
  }, [apply, clearTimers, clock, frameDriver, freezeAt, playScript, playing, reduced]);

  useEffect(() => clearTimers, [clearTimers]);

  const onTouchStart = useCallback(() => {
    if (touching.current) return;
    touching.current = true;
    silent.current = false;
    playing.set(false);
    clearTimers();
  }, [clearTimers, playing]);

  const onTouchEnd = useCallback(() => {
    touching.current = false;
    if (!playScript) return;
    clearTimers();
    timers.current.push(setTimeout(() => {
      if (!playScriptRef.current) return;
      // Back to the resting screen, then the script starts over.
      if (hiddenRef.current) setHidden(false);
      drop();
      timers.current.push(setTimeout(() => {
        if (!playScriptRef.current) return;
        silent.current = true;
        clock.set(0);
        playing.set(true);
      }, Math.max(HIDE_MS, 900)));
    }, RESUME_AFTER_MS));
  }, [clearTimers, clock, drop, playScript, playing, setHidden]);

  // Any finger on the screen hands control back from the script. A manual gesture sees every touch
  // without ever activating, so it never steals one from the buttons or the card.
  // The callbacks touch refs, but only when a finger lands, never while rendering.
  /* eslint-disable react-hooks/refs */
  const watcher = useMemo(() => Gesture.Manual()
    .onTouchesDown(() => scheduleOnRN(onTouchStart))
    .onTouchesUp(e => {
      if (e.numberOfTouches === 0) scheduleOnRN(onTouchEnd);
    })
    .onTouchesCancelled(() => scheduleOnRN(onTouchEnd)), [onTouchEnd, onTouchStart]);

  // The card follows the finger up out of the pocket (live value on grab, rubber band) and drops home
  // with the release velocity.
  const pullGesture = useMemo(() => {
    const pan = Gesture.Pan()
      .activeOffsetY([-6, 6])
      .maxPointers(1)
      .onBegin(() => {
        grab.set(frontLift.get());
      })
      .onUpdate(e => {
        if (reduced) return;
        const lift = Math.max(0, grab.get() + rubber(-e.translationY, PULL.limit));
        frontLift.set(lift);
        backLift.set(PULL.backLift * Math.min(1, lift / PULL.lift));
        tilt.set(PULL.tilt * Math.min(1.2, lift / PULL.lift));
        turn.set(Math.max(-TURN.max, Math.min(TURN.max, e.translationX * TURN.perDx)));
      })
      .onEnd(e => {
        drop(-e.velocityY);
        scheduleOnRN(buzz, Haptics.ImpactFeedbackStyle.Medium);
      });
    const tap = Gesture.Tap().onEnd(() => scheduleOnRN(tapCard));
    return Gesture.Exclusive(pan, tap);
  }, [backLift, buzz, drop, frontLift, grab, reduced, tapCard, tilt, turn]);
  /* eslint-enable react-hooks/refs */

  return (
    <GestureHandlerRootView style={styles.screen}>
      <GestureDetector gesture={watcher}>
        <View style={styles.screen}>
          <StatusBar style="dark" />
          <Header width={width} oy={oy} />
          <QuickTopUp width={width} oy={oy} />
          <LatestTransactions width={width} oy={oy} />
          <Wallet oy={oy} frontLift={frontLift} backLift={backLift} tilt={tilt} turn={turn} squish={squish} hidden={hidden}
            swapTurn={swapTurn} reduced={reduced} hiddenNow={hiddenNow} pullGesture={pullGesture}
            onEye={toggleEye} onSwap={swap} onAdd={() => buzz(Haptics.ImpactFeedbackStyle.Light)} />
          <TabBar width={width} height={height} oy={oy} />
        </View>
      </GestureDetector>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.screen },
});
