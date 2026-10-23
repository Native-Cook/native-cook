import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  ReduceMotion,
  Easing,
  cancelAnimation,
  type FrameInfo,
  useAnimatedReaction,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { Folder } from './Folder';
import {
  createMemoCardFanLifecycle,
  shouldEndMemoCardFanManualGesture,
  type MemoCardFanLifecycle,
} from './lifecycle';
import { MEMOS } from './memos';
import {
  CLOSE,
  FLING,
  LOOP_MS,
  OPEN,
  PRESS,
  RESUME_AFTER_MS,
  RM_FADE,
  PULL,
  bottomOpenAt,
  pullAt,
  spreadAt,
  topOpenAt,
} from './motion';
import { C, DRAG, FOLDERS_AT, FONT, PIN, S } from './theme';

type Props = { autoPlay?: boolean; freezeAt?: number };
const NEVER = ReduceMotion.Never;

function lightImpact() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

export function MemoCardFan({ autoPlay = false, freezeAt }: Props) {
  'use no memo';

  const [loaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Inter-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
  });
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  const frozen = freezeAt !== undefined;
  const initialTime = frozen ? freezeAt : 0;

  const open0 = useSharedValue(topOpenAt(initialTime));
  const open1 = useSharedValue(bottomOpenAt(initialTime));
  const press0 = useSharedValue(0);
  const press1 = useSharedValue(0);
  // hero values: only the open (bottom) folder's prints use them
  const pull1 = useSharedValue(pullAt(initialTime));
  const spread1 = useSharedValue(spreadAt(initialTime));
  const still0 = useSharedValue(0);
  const pulling = useSharedValue(false);
  const clock = useSharedValue(initialTime);
  const loopStart = useSharedValue(-1);
  const playing = useSharedValue(false);
  const dragStart = useSharedValue(0);
  const canAutoplay = autoPlay && !frozen && !reduceMotion;

  const frameStep = useCallback((frame: FrameInfo) => {
    'worklet';
    if (!playing.get()) return;
    if (loopStart.get() < 0) loopStart.set(frame.timestamp);
    const time = (frame.timestamp - loopStart.get()) % LOOP_MS;
    clock.set(time);
    open0.set(topOpenAt(time));
    open1.set(bottomOpenAt(time));
    pull1.set(pullAt(time));
    spread1.set(spreadAt(time));
  }, [clock, loopStart, open0, open1, playing, pull1, spread1]);
  const frameDriver = useFrameCallback(frameStep, false);

  const pause = useCallback(() => {
    playing.set(false);
    frameDriver.setActive(false);
    cancelAnimation(open0);
    cancelAnimation(open1);
    cancelAnimation(pull1);
    cancelAnimation(spread1);
  }, [frameDriver, open0, open1, playing, pull1, spread1]);
  const startAutoplay = useCallback(() => {
    clock.set(0);
    loopStart.set(-1);
    playing.set(true);
    frameDriver.setActive(true);
  }, [clock, frameDriver, loopStart, playing]);
  const returnToRest = useCallback((complete: () => void) => {
    const timing = { duration: 320, easing: Easing.out(Easing.cubic), reduceMotion: NEVER };
    open0.set(withTiming(0, timing));
    open1.set(withTiming(1, timing));
    pull1.set(withTiming(0, timing));
    spread1.set(withTiming(0, timing, (finished) => {
      if (finished) scheduleOnRN(complete);
    }));
  }, [open0, open1, pull1, spread1]);
  const lifecycle: MemoCardFanLifecycle = useMemo(
    () => createMemoCardFanLifecycle({ pause, returnToRest, startAutoplay }, RESUME_AFTER_MS),
    [pause, returnToRest, startAutoplay],
  );

  useEffect(() => {
    lifecycle.activate();
    return () => lifecycle.dispose();
  }, [lifecycle]);

  useEffect(() => {
    lifecycle.updateFlags({ autoPlay, reducedMotion: reduceMotion, frozen });
    if (frozen) {
      clock.set(freezeAt);
      open0.set(topOpenAt(freezeAt));
      open1.set(bottomOpenAt(freezeAt));
      pull1.set(pullAt(freezeAt));
      spread1.set(spreadAt(freezeAt));
      pause();
      return;
    }
    if (canAutoplay) {
      if (lifecycle.state().contacts !== 0) {
        pause();
        return;
      }
      open0.set(0);
      open1.set(1);
      pull1.set(0);
      spread1.set(0);
      startAutoplay();
    } else {
      pause();
      clock.set(0);
      open0.set(0);
      open1.set(1);
      pull1.set(0);
      spread1.set(0);
    }
  }, [autoPlay, canAutoplay, clock, freezeAt, frozen, lifecycle, open0, open1, pause, pull1, reduceMotion, spread1, startAutoplay]);

  const [openStates, setOpenStates] = useState([topOpenAt(initialTime) > 0.5, bottomOpenAt(initialTime) > 0.5]);
  const reportOpen = useCallback((index: number, value: boolean) => {
    setOpenStates((current) => current[index] === value ? current : current.map((item, i) => i === index ? value : item));
  }, []);
  useAnimatedReaction(() => open0.get() > 0.5, (value, previous) => {
    if (value !== previous) scheduleOnRN(reportOpen, 0, value);
  }, [reportOpen]);
  useAnimatedReaction(() => open1.get() > 0.5, (value, previous) => {
    if (value !== previous) scheduleOnRN(reportOpen, 1, value);
  }, [reportOpen]);

  const setOpen = (index: number, target: number) => {
    const value = index === 0 ? open0 : open1;
    value.set(
      reduceMotion
        ? withTiming(target, { duration: RM_FADE, reduceMotion: NEVER })
        : withSpring(target, target > value.get() ? OPEN : CLOSE),
    );
    lightImpact();
  };

  const pan0 = useMemo(
    () =>
      Gesture.Pan()
        .enabled(!frozen && !reduceMotion)
        .maxPointers(1)
        .activeOffsetY([-10, 10])
        .failOffsetX([-18, 18])
        .onBegin(() => {
          cancelAnimation(open0);
          dragStart.set(open0.get());
        })
        .onUpdate((event) => {
          let value = dragStart.get() - event.translationY / DRAG.range;
          if (value < 0) value *= DRAG.resist;
          if (value > 1) value = 1 + (value - 1) * DRAG.resist;
          open0.set(value);
        })
        .onEnd((event, success) => {
          if (!success) {
            open0.set(withSpring(open0.get() > DRAG.commit ? 1 : 0, { ...FLING, velocity: 0 }));
            return;
          }
          const velocity = -event.velocityY / DRAG.range;
          const target =
            event.velocityY < -DRAG.velocity
              ? 1
              : event.velocityY > DRAG.velocity
                ? 0
                : open0.get() > DRAG.commit
                  ? 1
                  : 0;
          open0.set(withSpring(target, { ...FLING, velocity }));
          scheduleOnRN(lightImpact);
        }),
    [dragStart, frozen, open0, reduceMotion],
  );

  const pan1 = useMemo(
    () =>
      Gesture.Pan()
        .enabled(!frozen && !reduceMotion)
        .maxPointers(1)
        .activeOffsetY([-10, 10])
        .failOffsetX([-18, 18])
        .onBegin(() => {
          cancelAnimation(open1);
          cancelAnimation(pull1);
          cancelAnimation(spread1);
          dragStart.set(open1.get());
          pulling.set(false);
        })
        .onUpdate((event) => {
          // open folder + finger going up: the front print follows the finger out of the pocket
          if (dragStart.get() > 0.5 && (pulling.get() || event.translationY < 0)) {
            pulling.set(true);
            let p = -event.translationY / (PULL.rise * S);
            if (p > 1) p = 1 + (p - 1) * DRAG.resist;
            if (p < 0) p *= DRAG.resist;
            pull1.set(p);
            spread1.set(Math.max(0, Math.min(1.08, p)));
            return;
          }
          let value = dragStart.get() - event.translationY / DRAG.range;
          if (value < 0) value *= DRAG.resist;
          if (value > 1) value = 1 + (value - 1) * DRAG.resist;
          open1.set(value);
        })
        .onEnd((event, success) => {
          if (!success) {
            pull1.set(withSpring(0, { ...CLOSE, velocity: 0 }));
            spread1.set(withSpring(0, { duration: 420, dampingRatio: 1, velocity: 0 }));
            open1.set(withSpring(open1.get() > DRAG.commit ? 1 : 0, { ...FLING, velocity: 0 }));
            return;
          }
          if (pulling.get()) {
            // let go: it slides home into the pocket, carrying the finger's speed
            const velocity = -event.velocityY / (PULL.rise * S);
            pull1.set(withSpring(0, { ...CLOSE, velocity }));
            spread1.set(withSpring(0, { duration: 420, dampingRatio: 1 }));
            scheduleOnRN(lightImpact);
            return;
          }
          const velocity = -event.velocityY / DRAG.range;
          const target =
            event.velocityY < -DRAG.velocity
              ? 1
              : event.velocityY > DRAG.velocity
                ? 0
                : open1.get() > DRAG.commit
                  ? 1
                  : 0;
          open1.set(withSpring(target, { ...FLING, velocity }));
          scheduleOnRN(lightImpact);
        }),
    [dragStart, frozen, open1, pull1, pulling, reduceMotion, spread1],
  );

  // Contact ownership and timers live on JS; both pan gestures stay on the UI thread.
  const contact0 = useMemo(() => Gesture.Manual().runOnJS(true).enabled(!frozen).shouldCancelWhenOutside(false).cancelsTouchesInView(false)
    .onTouchesDown((event, manager) => { lifecycle.beginContacts(event.changedTouches.length); manager.activate(); })
    .onTouchesUp((event, manager) => {
      lifecycle.endContacts(event.changedTouches.length);
      if (shouldEndMemoCardFanManualGesture(event.numberOfTouches)) manager.end();
    })
    .onTouchesCancelled((event, manager) => {
      lifecycle.cancelInteraction();
      open0.set(withSpring(open0.get() > DRAG.commit ? 1 : 0, { ...FLING, velocity: 0 }));
      lifecycle.endContacts(event.changedTouches.length);
      manager.fail();
    }), [frozen, lifecycle, open0]);
  const contact1 = useMemo(() => Gesture.Manual().runOnJS(true).enabled(!frozen).shouldCancelWhenOutside(false).cancelsTouchesInView(false)
    .onTouchesDown((event, manager) => { lifecycle.beginContacts(event.changedTouches.length); manager.activate(); })
    .onTouchesUp((event, manager) => {
      lifecycle.endContacts(event.changedTouches.length);
      if (shouldEndMemoCardFanManualGesture(event.numberOfTouches)) manager.end();
    })
    .onTouchesCancelled((event, manager) => {
      lifecycle.cancelInteraction();
      pull1.set(withSpring(0, { ...CLOSE, velocity: 0 }));
      spread1.set(withSpring(0, { duration: 420, dampingRatio: 1, velocity: 0 }));
      open1.set(withSpring(open1.get() > DRAG.commit ? 1 : 0, { ...FLING, velocity: 0 }));
      lifecycle.endContacts(event.changedTouches.length);
      manager.fail();
    }), [frozen, lifecycle, open1, pull1, spread1]);
  const gestures = [Gesture.Simultaneous(contact0, pan0), Gesture.Simultaneous(contact1, pan1)];

  if (!loaded) return <View style={styles.root} />;

  const midY = insets.top + (height - insets.top - insets.bottom) / 2;
  const toX = (x: number) => width / 2 + (x - PIN.cx) * S;
  const toY = (y: number) => midY + (y - PIN.cy) * S;
  const opens = [open0, open1];
  const presses = [press0, press1];

  return (
    <View style={styles.root}>
      <StatusBar style="light" />
      {MEMOS.map((memo, index) => {
        const open = opens[index];
        const press = presses[index];
        const frozenOpen = index === 0 ? topOpenAt(initialTime) : bottomOpenAt(initialTime);
        return (
          <GestureDetector key={`${memo.title}-${index}`} gesture={gestures[index]}>
            <Folder
              memo={memo}
              left={toX(FOLDERS_AT[index].x)}
              top={toY(FOLDERS_AT[index].y)}
              open={open}
              press={press}
              pull={index === 1 ? pull1 : still0}
              spread={index === 1 ? spread1 : still0}
              reduceMotion={reduceMotion}
              isOpen={frozen ? frozenOpen > 0.5 : openStates[index]}
              onPressIn={() => {
                press.set(withTiming(1, { duration: PRESS.in, reduceMotion: NEVER }));
              }}
              onPressOut={() => {
                press.set(withTiming(0, { duration: PRESS.out, reduceMotion: NEVER }));
              }}
              onPress={() => {
                const syntheticContact = lifecycle.state().contacts === 0;
                if (syntheticContact) lifecycle.beginContacts();
                setOpen(index, open.get() > 0.5 ? 0 : 1);
                if (syntheticContact) lifecycle.endContacts();
              }}
            />
          </GestureDetector>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: C.page },
});
