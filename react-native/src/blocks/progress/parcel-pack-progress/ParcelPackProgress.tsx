import * as Haptics from "expo-haptics";
import { LinearGradient } from "expo-linear-gradient";
import { StatusBar } from "expo-status-bar";
import { useCallback } from "react";
import { StyleSheet, Text, useWindowDimensions, View } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  cancelAnimation,
  Easing,
  runOnUI,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN } from "react-native-worklets";

import { useReducedMotionPreference } from "@/shared/useReducedMotionPreference";

import { DONE, ghostAt, JUMP, LOOP, ORBIT_FILM, ORBIT_SPAN, orbitFromDrag, PHASE, REDUCED_T } from "./parcelMotion";
import { GUTTER, PANEL_H, PANEL_R, ParcelPanel } from "./ParcelPanel";
import { BACKDROP, INK, ORDER, SHOP, STEP_N } from "./parcelScene";
import { ParcelStage3D } from "./ParcelStage3D";

const MODE_AUTO = 0;
const MODE_MANUAL = 1;

/** This long after the last touch the film carries on from where the clock stands. */
const IDLE_MS = 2000;
/** A tap on a step runs the clock there: this many ms per second of film, within these bounds. */
const JUMP_MS_PER_S = 280;
const JUMP_MIN_MS = 300;
const JUMP_MAX_MS = 1400;
/** A frame that arrives later than this (ms) is a stall: the film's clock does not count it. */
const STALL_MS = 100;
const FRAME_MS = 1000 / 60;
/** Frames between two publications of the clock's anchor to the Filament thread (its listener is added a moment
 * after mount, so a value set before that would never arrive). */
const ANCHOR_BEAT = 30;
/** The film's finger starts here on the stage (fractions of its width and height). */
const GHOST_X = 0.64;
const GHOST_Y = 0.62;
const DOT = 46;
const ORBIT_SPRING = { mass: 1, stiffness: 140, damping: 17 };
const JUMP_EASE = Easing.inOut(Easing.quad);

function tick(): void {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

function thud(): void {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft);
}

/** Publishes the film's clock for the Filament thread (only when it changes): [1 while it runs, the UI frame time
 * (ms) of its t = 0]. */
function syncAnchor(anchor: SharedValue<number[]>, playing: number, start: number): void {
  "worklet";
  const a = anchor.get();
  if (a[0] !== playing || a[1] !== start) anchor.set([playing, start]);
}

/** How long a tap's run over `span` seconds of film takes (ms). */
function jumpMs(span: number): number {
  "worklet";
  return Math.min(JUMP_MAX_MS, Math.max(JUMP_MIN_MS, Math.abs(span) * JUMP_MS_PER_S));
}

export type ParcelPackProgressProps = {
  autoPlay?: boolean;
};

export function ParcelPackProgress({ autoPlay = true }: ParcelPackProgressProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = !reduced && autoPlay;
  const initialT = reduced ? REDUCED_T : 0;

  /** The film's time (s). The film writes it; a tap on a step runs it to that step; nothing else holds state. */
  const t = useSharedValue(initialT);
  const anchor = useSharedValue<number[]>([0, -1]);
  /** A finger's orbit (deg), on top of the film's. */
  const orbit = useSharedValue(0);
  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const loopStart = useSharedValue(-1);
  const ready = useSharedValue(false);
  const shown = useSharedValue(0);
  const nowMs = useSharedValue(-1);
  const beat = useSharedValue(0);
  const lastTouch = useSharedValue(-1);
  const dragging = useSharedValue(false);
  const dragFrom = useSharedValue(0);

  // the 3D stage is the window above the sheet (it runs a little way under the sheet's round corners)
  const stageH = Math.max(200, height - PANEL_H - insets.bottom + PANEL_R);

  useFrameCallback((frame) => {
    "worklet";
    const now = frame.timestamp;
    const gap = nowMs.get() < 0 ? 0 : now - nowMs.get();
    nowMs.set(now);
    beat.set(beat.get() + 1);
    if (beat.get() % ANCHOR_BEAT === 0) {
      const a = anchor.get();
      anchor.set([a[0], a[1]]);
    }
    if (mode.get() === MODE_AUTO) {
      if (!film) {
        // reduced motion (or no autoplay): a still; taps still show each step
        mode.set(MODE_MANUAL);
        syncAnchor(anchor, 0, -1);
        cancelAnimation(t);
        t.set(reduced ? REDUCED_T : 0);
        return;
      }
      if (!ready.get()) {
        // the film waits at its first frame until the model is there
        if (t.get() !== 0) t.set(0);
        syncAnchor(anchor, 0, -1);
        loopStart.set(-1);
        return;
      }
      if (loopStart.get() < 0) {
        // it starts (or carries on after a touch) from where the clock stands
        loopStart.set(now - t.get() * 1000);
      } else if (gap > STALL_MS) {
        // the UI thread was held up: the film picks up where it was instead of skipping what the stall covered
        loopStart.set(loopStart.get() + gap - FRAME_MS);
      }
      let el = (now - loopStart.get()) / 1000;
      if (el >= LOOP) {
        loopStart.set(loopStart.get() + LOOP * 1000);
        el -= LOOP;
      }
      syncAnchor(anchor, 1, loopStart.get());
      t.set(el);
      return;
    }
    syncAnchor(anchor, 0, -1);
    if (lastTouch.get() < 0 || dragging.get()) {
      lastTouch.set(now);
      return;
    }
    if (!film || now - lastTouch.get() < IDLE_MS) return;
    // 2 s after the last touch the film carries on from the step on show (the same film: nothing has to ease back)
    cancelAnimation(t);
    const at = ((t.get() % LOOP) + LOOP) % LOOP;
    t.set(at);
    loopStart.set(-1);
    mode.set(MODE_AUTO);
  });

  // haptics: a light tick as each step is done, a soft one as the label lands
  useAnimatedReaction(
    () => t.get(),
    (cur, prev) => {
      if (prev == null || cur <= prev || cur - prev > 1) return;
      if (prev < PHASE.labelLand && cur >= PHASE.labelLand) {
        scheduleOnRN(thud);
        return;
      }
      for (let i = 0; i < STEP_N; i++) {
        if (prev < DONE[i] && cur >= DONE[i]) {
          scheduleOnRN(tick);
          return;
        }
      }
    },
  );

  /** A touch takes over: the film stops where it is (the Filament thread shows `t` from here on). */
  const pause = useCallback(() => {
    "worklet";
    if (mode.get() === MODE_AUTO) {
      mode.set(MODE_MANUAL);
      syncAnchor(anchor, 0, -1);
    }
    lastTouch.set(-1);
  }, [anchor, lastTouch, mode]);

  /** A tap on step i: the clock runs (forward or back) to the moment that step is done. */
  const jumpTo = useCallback(
    (i: number) => {
      "worklet";
      pause();
      cancelAnimation(t);
      const to = JUMP[i];
      if (reduced) {
        t.set(to);
        return;
      }
      let from = ((t.get() % LOOP) + LOOP) % LOOP;
      if (from >= PHASE.rest) {
        // the rest at the loop's end is the same frame as t = 0
        from = 0;
        t.set(0);
      }
      if (from >= PHASE.swap) {
        // a new carton is on its way in: let it arrive, then run forward from the start
        t.set(
          withSequence(
            withTiming(PHASE.rest, { duration: jumpMs(PHASE.rest - from), easing: Easing.out(Easing.quad) }),
            withTiming(0, { duration: 0 }),
            withTiming(to, { duration: jumpMs(to), easing: JUMP_EASE }),
          ),
        );
        return;
      }
      t.set(withTiming(to, { duration: jumpMs(to - from), easing: JUMP_EASE }));
    },
    [pause, reduced, t],
  );
  const onStep = useCallback(
    (i: number) => {
      runOnUI(jumpTo)(i);
    },
    [jumpTo],
  );

  // drag across the stage to look round the carton; it swings back when the finger lets go
  const pan = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .failOffsetY([-28, 28])
    .onStart(() => {
      "worklet";
      pause();
      dragging.set(true);
      cancelAnimation(orbit);
      // pick the carton up from wherever its swing back had got to
      dragFrom.set((-orbit.get() / ORBIT_SPAN) * width);
    })
    .onUpdate((e) => {
      "worklet";
      orbit.set(orbitFromDrag(dragFrom.get() + e.translationX, width));
    })
    .onEnd((e) => {
      "worklet";
      orbit.set(withSpring(0, { ...ORBIT_SPRING, velocity: (-e.velocityX / width) * ORBIT_SPAN }));
    })
    .onFinalize(() => {
      "worklet";
      dragging.set(false);
    });

  const onReady = useCallback(() => {
    ready.set(true);
    shown.set(withTiming(1, { duration: 320 }));
  }, [ready, shown]);

  const stage = useAnimatedStyle(() => ({ opacity: shown.get() }));
  // the film's finger: a soft dot that drags the carton round (only while the film plays)
  const dot = useAnimatedStyle(() => {
    const g = [0, 0, 0];
    ghostAt(t.get(), g);
    const on = mode.get() === MODE_AUTO ? 1 : 0;
    const dx = (-ORBIT_FILM / ORBIT_SPAN) * width * g[2];
    return {
      opacity: g[0] * 0.9 * on,
      transform: [{ translateX: width * GHOST_X + dx - DOT / 2 }, { translateY: stageH * GHOST_Y - DOT / 2 }, { scale: 1 - 0.16 * g[1] }],
    };
  });

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View accessibilityHint="Drag sideways to look round the parcel" accessibilityLabel="Parcel" collapsable={false} style={[styles.stage, { height: stageH }]}>
          <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, stage]}>
            <ParcelStage3D anchor={anchor} height={stageH} initialT={initialT} onReady={onReady} orbit={orbit} t={t} width={width} />
          </Animated.View>
          {/* the backdrop's colour over the top of the floor, so the status bar and the shop's name sit on one tone */}
          <LinearGradient colors={[BACKDROP, "rgba(228,220,206,0.6)", "rgba(228,220,206,0)"]} locations={[0, 0.55, 1]} pointerEvents="none" style={[styles.topShade, { height: insets.top + 72 }]} />
          <Animated.View pointerEvents="none" style={[styles.dot, dot]} />
        </View>
      </GestureDetector>
      <View pointerEvents="none" style={[styles.header, { top: insets.top + 10, left: GUTTER, right: GUTTER }]}>
        <Text style={styles.shop}>{SHOP}</Text>
        <Text style={styles.order}>{ORDER}</Text>
      </View>
      <ParcelPanel bottom={insets.bottom} onStep={onStep} t={t} width={width} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BACKDROP },
  stage: { position: "absolute", left: 0, right: 0, top: 0, overflow: "hidden" },
  topShade: { position: "absolute", left: 0, right: 0, top: 0 },
  header: { position: "absolute", flexDirection: "row", justifyContent: "space-between", alignItems: "baseline" },
  shop: { fontSize: 17, lineHeight: 22, fontWeight: "600", color: INK },
  order: { fontSize: 15, lineHeight: 20, color: "rgba(31,29,26,0.62)", fontVariant: ["tabular-nums"] },
  dot: {
    position: "absolute",
    left: 0,
    top: 0,
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
    backgroundColor: "rgba(255,255,255,0.42)",
    borderWidth: 1.5,
    borderColor: "rgba(255,255,255,0.85)",
    shadowColor: "#3B2F1E",
    shadowOpacity: 0.28,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 },
  },
});
