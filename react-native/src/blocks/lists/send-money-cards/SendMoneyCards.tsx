import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing, ReduceMotion, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue,
  withDelay, withSequence, withSpring, withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { CARD_DATA } from './data';
import { pillBox, sheetBox, tabBox } from './geometry';
import { HeaderButtons, HomeColumn, PaymentCard } from './HomeContent';
import { IslandGoo } from './IslandGoo';
import { IslandPill } from './IslandPill';
import {
  DURATION, easeFall, easeInOutCubic, easeOutCubic, FLICK_VELOCITY, LOOP_MS, OPEN_DRAG, PRESS_LIFT,
  easeReturn, HINT_FOLLOW, HINT_LAG, reducedPose, RESUME_AFTER_MS, RETURN_LIFT, RETURN_MS, rubber, scriptPose, SCRIPT, SETTLE_SPRING,
} from './motion';
import { SendSheet } from './SendSheet';
import { COLOR, FAMILY, islandOffset } from './theme';

export type SendMoneyCardsProps = {
  autoPlay?: boolean;
  /** Dev only: hold the autoplay still at this time (ms into the loop), for pixel comparisons. */
  freezeAt?: number;
};

const NEVER = ReduceMotion.Never;
const SNAP_BACK = { damping: 18, stiffness: 220 } as const;
const FONTS = {
  [FAMILY.regular]: require('./assets/fonts/Poppins-Regular.ttf'),
  [FAMILY.medium]: require('./assets/fonts/Poppins-Medium.ttf'),
  [FAMILY.semibold]: require('./assets/fonts/Poppins-SemiBold.ttf'),
};

export function SendMoneyCards({ autoPlay = false, freezeAt }: SendMoneyCardsProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const oy = islandOffset(insets.top);
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts(FONTS);
  const offscreen = height + 40;
  const frozen = freezeAt !== undefined;
  const scripted = autoPlay && !reduced && !frozen && fontsLoaded;

  // One pose drives everything: lift (the column's travel), arm (tab + hint), sheet (bloom 0..1),
  // fade (Reduce Motion cross-fade). `drag` is the finger's share of the travel.
  const lift = useSharedValue(0);
  const drag = useSharedValue(0);
  const arm = useSharedValue(0);
  const sheet = useSharedValue(0);
  const fade = useSharedValue(0);
  const hintLift = useSharedValue(0);
  const closing = useSharedValue(0); // the content shrinks later on the way back than it grows on the way out
  const playing = useSharedValue(false);
  const clock = useSharedValue(freezeAt ?? 0);
  const [open, setOpen] = useState(false);
  const resumeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const applyTime = useCallback((t: number) => {
    'worklet';
    const pose = reduced ? reducedPose(t) : { ...scriptPose(t, offscreen), fade: 0 };
    hintLift.set(reduced ? 0 : t >= SCRIPT.close ? scriptPose(t - HINT_LAG, offscreen).lift : pose.lift);
    lift.set(pose.lift);
    arm.set(pose.arm);
    sheet.set(pose.sheet);
    fade.set(pose.fade);
    closing.set(t >= SCRIPT.close ? 1 : 0);
  }, [arm, closing, fade, hintLift, lift, offscreen, reduced, sheet]);
  const frameDriver = useFrameCallback(({ timeSincePreviousFrame }) => {
    if (!playing.get()) return;
    const t = (clock.get() + Math.min(timeSincePreviousFrame ?? 16, 50)) % LOOP_MS;
    clock.set(t);
    applyTime(t);
  }, false);
  const scriptedRef = useRef(scripted);
  useLayoutEffect(() => {
    scriptedRef.current = scripted;
  }, [scripted]);
  useEffect(() => {
    if (resumeTimer.current) {
      clearTimeout(resumeTimer.current);
      resumeTimer.current = null;
    }
    playing.set(false);
    frameDriver.setActive(false);
    if (frozen) {
      clock.set(freezeAt);
      applyTime(freezeAt);
      return;
    }
    if (scripted) {
      clock.set(0);
      applyTime(0);
      playing.set(true);
      frameDriver.setActive(true);
    } else if (reduced) {
      lift.set(0); drag.set(0); arm.set(0); sheet.set(0); fade.set(0); hintLift.set(0); closing.set(0);
      const resetOpen = setTimeout(() => setOpen(false), 0);
      return () => clearTimeout(resetOpen);
    }
    return () => frameDriver.setActive(false);
  }, [applyTime, arm, clock, closing, drag, fade, frameDriver, freezeAt, frozen, hintLift, lift, playing, reduced, scripted, sheet]);

  const travel = useDerivedValue(() => lift.get() + drag.get());
  // Under a finger, the hint follows the card through a stiff spring, so it trails it like the pin's.
  useAnimatedReaction(() => travel.get(), (v) => {
    if (!playing.get()) hintLift.set(withSpring(v, HINT_FOLLOW));
  });
  const p = useDerivedValue(() => (reduced ? (fade.get() > 0.001 ? 1 : 0) : sheet.get()));
  const pill = useDerivedValue(() => pillBox(reduced ? 0 : p.get(), width, oy));
  const tab = useDerivedValue(() => tabBox(reduced ? 0 : arm.get(), p.get(), pill.get()));
  const bloom = useDerivedValue(() => sheetBox(p.get(), width, height, oy, closing.get() > 0.5));
  const homeStyle = useAnimatedStyle(() => ({ opacity: reduced ? 1 - fade.get() : 1 }));

  const haptic = useCallback((kind: 'arm' | 'send' | 'close') => {
    if (kind === 'arm') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    else if (kind === 'send') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    else void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft);
  }, []);

  // --- Touch paths (the same curves as the script) ---
  const press = useCallback(() => {
    'worklet';
    if (reduced) {
      arm.set(withTiming(1, { duration: DURATION.fade, reduceMotion: NEVER }));
      return;
    }
    lift.set(withTiming(PRESS_LIFT, { duration: DURATION.press, easing: easeOutCubic }));
    arm.set(withTiming(1, { duration: DURATION.arm, easing: easeOutCubic }));
  }, [arm, lift, reduced]);

  const release = useCallback(() => {
    'worklet';
    drag.set(withSpring(0, SNAP_BACK));
    lift.set(withTiming(0, { duration: DURATION.release, easing: easeInOutCubic }));
    arm.set(withTiming(0, { duration: reduced ? DURATION.fade : DURATION.arm, reduceMotion: NEVER }));
  }, [arm, drag, lift, reduced]);

  const send = useCallback(() => {
    'worklet';
    scheduleOnRN(setOpen, true);
    if (reduced) {
      fade.set(withTiming(1, { duration: DURATION.fade, reduceMotion: NEVER }));
      arm.set(withTiming(0, { duration: DURATION.fade, reduceMotion: NEVER }));
      return;
    }
    arm.set(1);
    lift.set(lift.get() + drag.get());
    drag.set(0);
    lift.set(withTiming(offscreen, { duration: DURATION.fall, easing: easeFall }));
    sheet.set(withDelay(DURATION.openDelay, withTiming(1, { duration: DURATION.open, easing: Easing.linear })));
    closing.set(0);
  }, [arm, closing, drag, fade, lift, offscreen, reduced, sheet]);

  const close = useCallback(() => {
    'worklet';
    scheduleOnRN(setOpen, false);
    if (reduced) {
      fade.set(withTiming(0, { duration: DURATION.fade, reduceMotion: NEVER }));
      return;
    }
    sheet.set(withTiming(0, { duration: DURATION.close, easing: Easing.linear }));
    // The measured approach from below the screen, then the fitted spring takes over with its velocity.
    lift.set(RETURN_LIFT[0]);
    lift.set(withDelay(DURATION.returnDelay, withSequence(
      withTiming(RETURN_LIFT[RETURN_LIFT.length - 1], { duration: RETURN_MS, easing: easeReturn }),
      withSpring(0, SETTLE_SPRING),
    )));
    closing.set(1);
    arm.set(withDelay(DURATION.tabOut, withTiming(0, { duration: DURATION.tabFade, easing: easeInOutCubic })));
  }, [arm, closing, fade, lift, reduced, sheet]);

  // --- Autoplay hand-off: a touch stops the script; 3.5 s after the last touch it resumes from rest ---
  const stopScript = useCallback(() => {
    if (frozen) return;
    if (resumeTimer.current) clearTimeout(resumeTimer.current);
    resumeTimer.current = null;
    if (playing.get()) {
      playing.set(false);
      setOpen(sheet.get() > 0.5 || fade.get() > 0.5);
    }
  }, [fade, frozen, playing, sheet]);

  const scheduleResume = useCallback(() => {
    if (!scripted) return;
    if (resumeTimer.current) clearTimeout(resumeTimer.current);
    resumeTimer.current = setTimeout(() => {
      if (!scriptedRef.current) return;
      const atRest = sheet.get() < 0.001 && fade.get() < 0.001 && Math.abs(lift.get()) < 0.5;
      const wait = atRest ? 0 : DURATION.close + 900;
      if (!atRest) {
        if (sheet.get() > 0.001 || fade.get() > 0.001) close();
        else release();
      }
      resumeTimer.current = setTimeout(() => {
        if (!scriptedRef.current) return;
        clock.set(0);
        drag.set(0);
        playing.set(true);
      }, wait);
    }, RESUME_AFTER_MS);
  }, [clock, close, drag, fade, lift, playing, release, scripted, sheet]);

  useEffect(() => () => {
    if (resumeTimer.current) clearTimeout(resumeTimer.current);
  }, []);

  const armHaptic = useCallback(() => {
    if (!playing.get()) haptic('arm');
  }, [haptic, playing]);
  const sendHaptic = useCallback(() => haptic('send'), [haptic]);
  const closeHaptic = useCallback(() => haptic('close'), [haptic]);

  // Memoised: a re-render mid-drag (autoplay hand-off) must not hand the detector a new gesture.
  const cardPan = useMemo(() => Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin(() => {
      playing.set(false);
      press();
      scheduleOnRN(armHaptic);
    })
    .onUpdate(e => {
      if (!reduced) drag.set(rubber(e.translationY, 120));
    })
    .onEnd(e => {
      if (e.translationY < -OPEN_DRAG || e.velocityY < -FLICK_VELOCITY) {
        send();
        scheduleOnRN(sendHaptic);
      } else {
        release();
      }
    })
    .onFinalize((_, success) => {
      if (!success && sheet.get() < 0.001 && fade.get() < 0.001) release();
    }), [armHaptic, drag, fade, playing, press, reduced, release, send, sendHaptic, sheet]);

  const sheetPan = useMemo(() => Gesture.Pan()
    .activeOffsetY([-12, 12])
    .onBegin(() => {
      playing.set(false);
    })
    .onEnd(e => {
      if (e.translationY < -40 || e.velocityY < -FLICK_VELOCITY) {
        close();
        scheduleOnRN(closeHaptic);
      }
    }), [close, closeHaptic, playing]);

  const onClose = useCallback(() => {
    close();
    haptic('close');
  }, [close, haptic]);

  const card = (
    <GestureDetector gesture={cardPan}>
      <View accessible accessibilityRole="button"
        accessibilityLabel={`Card ending ${CARD_DATA.last4}, balance ${CARD_DATA.balance.replace(' ', '')}`}
        accessibilityHint="Drag up to the top to send money"
        accessibilityActions={[{ name: 'activate', label: 'Send money' }]}
        onAccessibilityAction={() => send()}>
        <PaymentCard width={width} />
      </View>
    </GestureDetector>
  );

  if (!fontsLoaded) return <View style={styles.screen} />;

  return (
    <GestureHandlerRootView style={styles.screen}>
      <View style={styles.screen} onTouchStart={stopScript} onTouchEnd={scheduleResume} onTouchCancel={scheduleResume}>
        <StatusBar hidden />
        <Animated.View style={[StyleSheet.absoluteFill, homeStyle]} pointerEvents={open ? 'none' : 'box-none'}
          importantForAccessibility={open ? 'no-hide-descendants' : 'auto'} accessibilityElementsHidden={open}>
          <HeaderButtons width={width} oy={oy} sheet={sheet} />
          <HomeColumn width={width} oy={oy} lift={travel} hintLift={hintLift} arm={arm} sheet={sheet} cardSlot={card} />
        </Animated.View>
        <IslandGoo width={width} height={height} pill={pill} tab={tab} sheet={bloom} fade={fade} reduced={reduced} />
        <IslandPill width={width} oy={oy} pill={pill} bloom={bloom} arm={arm} sheet={sheet} fade={fade} reduced={reduced} />
        <GestureDetector gesture={sheetPan}>
          <Animated.View style={StyleSheet.absoluteFill} pointerEvents={open ? 'box-none' : 'none'}
            importantForAccessibility={open ? 'auto' : 'no-hide-descendants'} accessibilityElementsHidden={!open}
            accessibilityViewIsModal={open}>
            <SendSheet width={width} height={height} oy={oy} box={bloom} sheet={sheet} fade={fade} closing={closing}
              reduced={reduced} onClose={onClose} />
          </Animated.View>
        </GestureDetector>
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.screen },
});
