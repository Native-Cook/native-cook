import * as Haptics from "expo-haptics";
import { StatusBar } from "expo-status-bar";
import { useCallback } from "react";
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
import { scheduleOnRN } from "react-native-worklets";

import { useReducedMotionPreference } from "@/shared/useReducedMotionPreference";

import { VisorBackdrop } from "./VisorBackdrop";
import { VisorHud } from "./VisorHud";
import { IDLE_MS, LOOP, pageOfFilm, RETURN_MS, TAKEOVER_MS } from "./visorMotion";
import { VisorOverlay } from "./VisorOverlay";
import { VisorVoiceCaption } from "./VisorVoiceCaption";
import { C, PAGES } from "./visorScene";
import { VisorStage3D } from "./VisorStage3D";

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

/** Orbit: degrees per point of drag; pitch is held to a band and rubber-bands past it. */
const YAW_PER_PT = 0.36;
const PITCH_PER_PT = 0.16;
const PITCH_MIN = -10;
const PITCH_MAX = 24;
const DETENT_DEG = 45;
/** A page swipe: this share of the width is one page; past either end it rubber-bands. */
const PAGE_SPAN = 0.8;
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
const PAGE_SPRING = { duration: 700, dampingRatio: 0.86 } as const;

function lightTap(): void {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

function startTap(): void {
  void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
}

/** Rubber band past [lo, hi]: limit + over / (1 + over / k). */
function rubber(x: number, lo: number, hi: number, k: number): number {
  "worklet";
  if (x < lo) {
    const over = lo - x;
    return lo - over / (1 + over / k);
  }
  if (x > hi) {
    const over = x - hi;
    return hi + over / (1 + over / k);
  }
  return x;
}

export type VisorBootOnboardingProps = {
  autoPlay?: boolean;
};

export function VisorBootOnboarding({ autoPlay = true }: VisorBootOnboardingProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = !reduced && autoPlay;

  // ---- one clock, on the UI thread; the stage, the Skia layers and the copy all read these values
  const t = useSharedValue(0);
  const page = useSharedValue(0);
  const m = useSharedValue(film ? 0 : 1);
  const yaw = useSharedValue(0);
  const pitch = useSharedValue(0);
  const run = useSharedValue(0);
  const runBase = useSharedValue(0);
  const runFrom = useSharedValue(-1);
  const now = useSharedValue(0);
  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const loopStart = useSharedValue(-1);
  const ready = useSharedValue(false);
  const lastActivity = useSharedValue(-1);
  const dragging = useSharedValue(false);
  const spinning = useSharedValue(false);
  const yawFrom = useSharedValue(0);
  const pitchFrom = useSharedValue(0);
  const pageFrom = useSharedValue(0);

  useFrameCallback((frame) => {
    "worklet";
    const ts = frame.timestamp;
    now.set(ts);
    const md = mode.get();
    if (md === MODE_AUTO) {
      if (!film) {
        mode.set(MODE_MANUAL);
        m.set(1);
        return;
      }
      // hold the poster (t = 0) until the helmet has loaded, so the first visible frame is the poster
      if (!ready.get()) {
        t.set(0);
        run.set(0);
        return;
      }
      if (loopStart.get() < 0) loopStart.set(ts);
      const tt = ((ts - loopStart.get()) / 1000) % LOOP;
      t.set(tt);
      run.set(tt);
      return;
    }
    // under a finger the periodic motion (radar, compass, voice) keeps running from where the film left it
    if (runFrom.get() < 0) runFrom.set(ts);
    run.set(runBase.get() + (ts - runFrom.get()) / 1000);
    if (md !== MODE_MANUAL) return;
    if (lastActivity.get() < 0 || dragging.get() || spinning.get()) {
      lastActivity.set(ts);
      return;
    }
    if (!film || ts - lastActivity.get() < IDLE_MS) return;
    // 2 s after the last touch: back to the poster page, then the film starts again from 0
    mode.set(MODE_RETURN);
    const timing = { duration: RETURN_MS, easing: EASE_IN_OUT };
    yaw.set(withTiming(Math.round(yaw.get() / 360) * 360, timing));
    pitch.set(withTiming(0, timing));
    page.set(
      withTiming(0, timing, (finished) => {
        "worklet";
        if (!finished) return;
        yaw.set(0);
        t.set(0);
        run.set(0);
        loopStart.set(-1);
        m.set(0);
        mode.set(MODE_AUTO);
      }),
    );
  });

  /** Any touch hands the film over to the pages, at the page the film was on. */
  const pause = useCallback(() => {
    "worklet";
    const md = mode.get();
    if (md === MODE_RETURN) {
      cancelAnimation(page);
      cancelAnimation(yaw);
      cancelAnimation(pitch);
    }
    if (md === MODE_AUTO) {
      page.set(pageOfFilm(t.get()));
      runBase.set(run.get());
      runFrom.set(-1);
      m.set(withTiming(1, { duration: TAKEOVER_MS, easing: EASE_IN_OUT }));
    }
    mode.set(MODE_MANUAL);
    lastActivity.set(-1);
  }, [lastActivity, m, mode, page, pitch, run, runBase, runFrom, t, yaw]);

  const goPage = useCallback(
    (i: number) => {
      pause();
      const target = Math.max(0, Math.min(PAGES - 1, i));
      if (reduced) page.set(withTiming(target, { duration: 220 }));
      else page.set(withSpring(target, PAGE_SPRING));
    },
    [page, pause, reduced],
  );

  // a light tick as a page lands under the finger, and every 45 deg of orbit (never during the film)
  useAnimatedReaction(
    () => (mode.get() === MODE_MANUAL ? Math.round(page.get()) : -1),
    (cur, prev) => {
      if (prev == null || prev < 0 || cur < 0 || cur === prev) return;
      scheduleOnRN(lightTap);
    },
  );
  useAnimatedReaction(
    () => Math.floor(yaw.get() / DETENT_DEG),
    (cur, prev) => {
      if (prev == null || cur === prev) return;
      if (dragging.get() || spinning.get()) scheduleOnRN(lightTap);
    },
  );

  // drag on the helmet orbits the camera about it
  const orbit = Gesture.Pan()
    .minDistance(6)
    .onStart(() => {
      "worklet";
      pause();
      cancelAnimation(yaw);
      cancelAnimation(pitch);
      dragging.set(true);
      spinning.set(false);
      yawFrom.set(yaw.get());
      pitchFrom.set(pitch.get());
    })
    .onUpdate((e) => {
      "worklet";
      yaw.set(yawFrom.get() - e.translationX * YAW_PER_PT);
      pitch.set(rubber(pitchFrom.get() + e.translationY * PITCH_PER_PT, PITCH_MIN, PITCH_MAX, 10));
    })
    .onEnd((e) => {
      "worklet";
      dragging.set(false);
      const back = Math.max(PITCH_MIN, Math.min(PITCH_MAX, pitch.get()));
      pitch.set(withSpring(back, { duration: 400, dampingRatio: 0.8, velocity: e.velocityY * PITCH_PER_PT }));
      if (reduced) return;
      spinning.set(true);
      yaw.set(
        withDecay({ velocity: -e.velocityX * YAW_PER_PT, deceleration: 0.995 }, () => {
          "worklet";
          spinning.set(false);
        }),
      );
    })
    .onFinalize(() => {
      "worklet";
      dragging.set(false);
    });

  // swipe sideways on the copy pages the onboarding (the camera springs to that page's shot)
  const pager = Gesture.Pan()
    .activeOffsetX([-10, 10])
    .failOffsetY([-30, 30])
    .onStart(() => {
      "worklet";
      pause();
      cancelAnimation(page);
      dragging.set(true);
      pageFrom.set(page.get());
    })
    .onUpdate((e) => {
      "worklet";
      page.set(rubber(pageFrom.get() - e.translationX / (width * PAGE_SPAN), 0, PAGES - 1, 0.35));
    })
    .onEnd((e) => {
      "worklet";
      dragging.set(false);
      const base = Math.round(pageFrom.get());
      const dx = e.translationX;
      let target = base;
      if (dx < -50 || e.velocityX < -500) target = base + 1;
      else if (dx > 50 || e.velocityX > 500) target = base - 1;
      target = Math.max(0, Math.min(PAGES - 1, target));
      const v = -e.velocityX / (width * PAGE_SPAN);
      page.set(reduced ? withTiming(target, { duration: 220 }) : withSpring(target, { ...PAGE_SPRING, velocity: v }));
    })
    .onFinalize(() => {
      "worklet";
      dragging.set(false);
    });

  const onReady = useCallback(() => {
    ready.set(true);
  }, [ready]);

  const onNext = useCallback(() => {
    lightTap();
    goPage(Math.round(page.get()) + 1);
  }, [goPage, page]);
  const onSkip = useCallback(() => {
    lightTap();
    goPage(PAGES - 1);
  }, [goPage]);
  const onPage = useCallback(
    (i: number) => {
      lightTap();
      goPage(i);
    },
    [goPage],
  );
  const onStart = useCallback(() => {
    pause();
    startTap();
  }, [pause]);

  const compact = height < 760;
  const copyHeight = compact ? 168 : 206;
  const rowBottom = insets.bottom + 20;
  // where the copy begins: the voice module and the orbit area stop there
  const copyTop = height - (rowBottom + 56 + 22 + copyHeight);
  const filmProps = { t, page, m };

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <VisorBackdrop height={height} m={m} page={page} pitch={pitch} t={t} width={width} yaw={yaw} />
      <VisorStage3D height={height} m={m} onReady={onReady} page={page} pitch={pitch} t={t} width={width} yaw={yaw} />
      <VisorHud height={height} limitY={copyTop} m={m} page={page} pitch={pitch} run={run} t={t} width={width} yaw={yaw} />
      <GestureDetector gesture={orbit}>
        <View style={[styles.orbitArea, { height: copyTop }]} />
      </GestureDetector>
      <GestureDetector gesture={pager}>
        <View style={[styles.pagerArea, { top: copyTop }]} />
      </GestureDetector>
      <VisorVoiceCaption height={height} limitY={copyTop} m={m} page={page} pitch={pitch} t={t} width={width} yaw={yaw} />
      <VisorOverlay
        bottom={rowBottom}
        compact={compact}
        copyHeight={copyHeight}
        film={filmProps}
        onNext={onNext}
        onPage={onPage}
        onSkip={onSkip}
        onStart={onStart}
        reduced={reduced}
        top={insets.top}
        width={width}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.bgEdge },
  orbitArea: { position: "absolute", left: 0, right: 0, top: 0 },
  pagerArea: { position: "absolute", left: 0, right: 0, bottom: 0 },
});
