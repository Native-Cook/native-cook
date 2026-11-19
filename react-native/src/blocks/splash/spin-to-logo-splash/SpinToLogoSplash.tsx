import * as Haptics from "expo-haptics";
import { StatusBar } from "expo-status-bar";
import { useCallback, useMemo } from "react";
import { StyleSheet, useWindowDimensions, View } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import {
  cancelAnimation,
  Easing,
  useAnimatedReaction,
  useFrameCallback,
  useSharedValue,
  withDecay,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN, scheduleOnUI } from "react-native-worklets";

import { useReducedMotionPreference } from "@/shared/useReducedMotionPreference";

import { SpinFloor } from "./SpinFloor";
import { SpinMark } from "./SpinMark";
import { cameraAt, filmYaw, IDLE_MS, LOOP, PHASE, project, RETURN_MS, YAW_PER_PT } from "./spinMotion";
import { SpinOverlay } from "./SpinOverlay";
import { PAPER_BOTTOM, TRACK_R } from "./spinScene";
import { SpinStage3D } from "./SpinStage3D";

/** The film plays. */
const MODE_AUTO = 0;
/** A finger turns the sculpture; the film's clock is held. */
const MODE_MANUAL = 1;
/** The turn is complete: the fold, the hand-off and the welcome screen play on their own clock, then hold. */
const MODE_STORY = 2;
/** Easing the finger's turn back to the poster before the film starts again. */
const MODE_RETURN = 3;

/** Where the story clock starts after a finger completes the turn (just before the track's pop). */
const STORY_T0 = PHASE.turnEnd - 0.05;
/** Reduced motion: the welcome screen, still. */
const REDUCED_T = 10;
const DETENT_DEG = 45;
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);

function lightTap(): void {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

function unlocked(): void {
  void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
}

export type SpinToLogoSplashProps = {
  autoPlay?: boolean;
};

export function SpinToLogoSplash({ autoPlay = true }: SpinToLogoSplashProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = !reduced && autoPlay;

  // ---- one clock on the UI thread; the stage, the Skia layers and the words read these values
  const t = useSharedValue(reduced ? REDUCED_T : 0);
  const yaw = useSharedValue(0);
  /** The ghost finger's visibility (the film's finger; 0 once a real one has taken over). */
  const live = useSharedValue(film ? 1 : 0);
  const mode = useSharedValue(reduced ? MODE_STORY : film ? MODE_AUTO : MODE_MANUAL);
  const loopStart = useSharedValue(-1);
  const storyStart = useSharedValue(-1);
  const ready = useSharedValue(false);
  const lastActivity = useSharedValue(-1);
  const dragging = useSharedValue(false);
  const spinning = useSharedValue(false);
  const yawFrom = useSharedValue(0);
  /** The frame clock's latest timestamp (ms), so touches can be placed on the same time base. */
  const clock = useSharedValue(0);

  /** The turn is complete (by a finger or Skip): fold, hand off and show the welcome screen. */
  const unlock = useCallback(
    () => {
      "worklet";
      const now = clock.get();
      cancelAnimation(yaw);
      dragging.set(false);
      spinning.set(false);
      const target = yaw.get() < 0 ? -360 : 360;
      yaw.set(withSpring(target, { duration: 500, dampingRatio: 0.85 }));
      t.set(STORY_T0);
      storyStart.set(now);
      lastActivity.set(now);
      mode.set(MODE_STORY);
      scheduleOnRN(unlocked);
    },
    [clock, dragging, lastActivity, mode, spinning, storyStart, t, yaw],
  );

  useFrameCallback((frame) => {
    "worklet";
    const now = frame.timestamp;
    clock.set(now);
    const md = mode.get();
    if (md === MODE_AUTO) {
      if (!film) {
        mode.set(MODE_MANUAL);
        live.set(0);
        return;
      }
      // hold the poster (t = 0) until the sculpture has loaded, so the first visible frame is the poster
      if (!ready.get()) {
        t.set(0);
        return;
      }
      if (loopStart.get() < 0) loopStart.set(now);
      const tt = ((now - loopStart.get()) / 1000) % LOOP;
      t.set(tt);
      yaw.set(filmYaw(tt));
      return;
    }
    if (md === MODE_STORY) {
      if (reduced) {
        t.set(REDUCED_T);
        return;
      }
      if (lastActivity.get() < 0) lastActivity.set(now);
      const tt = Math.min(STORY_T0 + (now - storyStart.get()) / 1000, PHASE.holdEnd);
      t.set(tt);
      // the welcome screen holds while touches come; 2 s after the last one the film's own return plays
      if (tt >= PHASE.holdEnd && film && now - lastActivity.get() >= IDLE_MS) {
        yaw.set(360);
        loopStart.set(now - PHASE.holdEnd * 1000);
        live.set(1);
        mode.set(MODE_AUTO);
      }
      return;
    }
    if (md !== MODE_MANUAL) return;
    if (lastActivity.get() < 0 || dragging.get() || spinning.get()) {
      lastActivity.set(now);
    }
    if (Math.abs(yaw.get()) >= 360) {
      unlock();
      return;
    }
    if (!film || now - lastActivity.get() < IDLE_MS) return;
    // 2 s after the last touch: the sculpture turns back to the poster (the track empties), then the film restarts
    mode.set(MODE_RETURN);
    t.set(withTiming(0, { duration: RETURN_MS, easing: EASE_IN_OUT }));
    yaw.set(
      withTiming(0, { duration: RETURN_MS, easing: EASE_IN_OUT }, (finished) => {
        "worklet";
        if (!finished) return;
        t.set(0);
        loopStart.set(-1);
        live.set(1);
        mode.set(MODE_AUTO);
      }),
    );
  });

  /** A touch: the film pauses where it is while the sculpture is still whole; once it has folded, the story runs on
   * and the welcome screen holds for as long as touches come. */
  const touch = useCallback(
    () => {
      "worklet";
      const now = clock.get();
      const md = mode.get();
      // the frame clock stamps the activity on its next frame
      lastActivity.set(-1);
      if (md === MODE_RETURN) {
        cancelAnimation(t);
        cancelAnimation(yaw);
        mode.set(MODE_MANUAL);
        return;
      }
      if (md !== MODE_AUTO) return;
      const tt = t.get();
      if (tt < PHASE.turnEnd) {
        live.set(withTiming(0, { duration: 220 }));
        mode.set(MODE_MANUAL);
      } else if (tt < PHASE.holdEnd) {
        storyStart.set(now - (tt - STORY_T0) * 1000);
        mode.set(MODE_STORY);
      }
    },
    [clock, lastActivity, live, mode, storyStart, t, yaw],
  );

  // a light detent every 45 deg while a finger turns the sculpture (never during the film)
  useAnimatedReaction(
    () => Math.floor(yaw.get() / DETENT_DEG),
    (cur, prev) => {
      if (prev == null || cur === prev) return;
      if (mode.get() === MODE_MANUAL && (dragging.get() || spinning.get())) scheduleOnRN(lightTap);
    },
  );

  const pan = Gesture.Pan()
    .minDistance(4)
    .onBegin(() => {
      "worklet";
      touch();
    })
    .onStart(() => {
      "worklet";
      if (mode.get() !== MODE_MANUAL) return;
      cancelAnimation(yaw);
      dragging.set(true);
      spinning.set(false);
      yawFrom.set(yaw.get());
    })
    .onUpdate((e) => {
      "worklet";
      if (mode.get() !== MODE_MANUAL) return;
      yaw.set(yawFrom.get() + e.translationX * YAW_PER_PT);
    })
    .onEnd((e) => {
      "worklet";
      dragging.set(false);
      if (mode.get() !== MODE_MANUAL || reduced) return;
      spinning.set(true);
      yaw.set(
        withDecay({ velocity: e.velocityX * YAW_PER_PT, deceleration: 0.996 }, () => {
          "worklet";
          spinning.set(false);
        }),
      );
    })
    .onFinalize(() => {
      "worklet";
      dragging.set(false);
    });

  const onReady = useCallback(() => {
    ready.set(true);
  }, [ready]);

  const skip = useCallback(() => {
    scheduleOnUI(() => {
      "worklet";
      touch();
      if (mode.get() === MODE_MANUAL) unlock();
    });
  }, [mode, touch, unlock]);

  const start = useCallback(() => {
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
  }, []);

  const compact = height < 760;
  const finalBase = insets.top + (compact ? 96 : 132);
  const trackFront = useMemo(() => {
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(0, c);
    const q = [0, 0, 0];
    project(0, 0, TRACK_R, c, width, height, q);
    return q[1];
  }, [width, height]);

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <SpinFloor height={height} t={t} width={width} yaw={yaw} />
      <SpinStage3D height={height} onReady={onReady} t={t} width={width} yaw={yaw} />
      <SpinMark finalBase={finalBase} height={height} live={live} t={t} width={width} />
      <GestureDetector gesture={pan}>
        <View style={styles.panArea} />
      </GestureDetector>
      <SpinOverlay
        bottom={insets.bottom}
        compact={compact}
        finalBase={finalBase}
        height={height}
        onSignIn={lightTap}
        onSkip={skip}
        onStart={start}
        reduced={reduced}
        t={t}
        top={insets.top}
        trackFront={trackFront}
        width={width}
        yaw={yaw}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAPER_BOTTOM },
  panArea: { position: "absolute", left: 0, right: 0, top: 0, bottom: 0 },
});
