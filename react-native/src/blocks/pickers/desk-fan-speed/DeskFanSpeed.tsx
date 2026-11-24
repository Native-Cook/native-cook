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
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useReducedMotionPreference } from "@/shared/useReducedMotionPreference";

import { FanAirflow } from "./FanAirflow";
import {
  AIM_MAX,
  AIM_SPAN,
  aimFromDrag,
  filmKey,
  filmLevel,
  filmSpeed,
  filmTurns,
  filmYaw,
  LOOP,
  SPEEDS,
  SPIN_DOWN_TAU,
  SPIN_UP_TAU,
  YAW_REST,
} from "./fanMotion";
import { FanPanel, GUTTER, PANEL_H, PANEL_R } from "./FanPanel";
import { APP, BACKDROP, INK } from "./fanScene";
import { FanStage3D } from "./FanStage3D";

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

/** This long after the last touch the fan goes back to Off, and RETURN_MS later the film restarts. */
const IDLE_MS = 2000;
const RETURN_MS = 1300;
const STALL_MS = 100;
const FRAME_MS = 1000 / 60;
const ANCHOR_BEAT = 30;
const KEY_RATE = 11;
const AIM_SPRING = { mass: 1, stiffness: 150, damping: 18 };
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
/** Reduced motion's still: level 2, the head at its rest angle. */
const REDUCED_LEVEL = 2;

function tick(): void {
  void Haptics.selectionAsync();
}

function syncAnchor(anchor: SharedValue<number[]>, playing: number, start: number): void {
  "worklet";
  const a = anchor.get();
  if (a[0] !== playing || a[1] !== start) anchor.set([playing, start]);
}

export type DeskFanSpeedProps = {
  autoPlay?: boolean;
  /** A finger picked a level (0 = off, 1..3). */
  onLevelChange?: (level: number) => void;
};

export function DeskFanSpeed({ autoPlay = true, onLevelChange }: DeskFanSpeedProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = !reduced && autoPlay;

  const t = useSharedValue(0);
  const anchor = useSharedValue<number[]>([0, -1]);
  const turns = useSharedValue(0);
  const speed = useSharedValue(0);
  const yaw = useSharedValue(YAW_REST);
  const keys = useSharedValue<number[]>([1, 0, 0, 0]);
  const level = useSharedValue(0);
  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const loopStart = useSharedValue(-1);
  const ready = useSharedValue(false);
  const shown = useSharedValue(0);
  const nowMs = useSharedValue(-1);
  const beat = useSharedValue(0);
  const lastTouch = useSharedValue(-1);
  const returnAt = useSharedValue(-1);
  const dragging = useSharedValue(false);
  const dragFrom = useSharedValue(0);

  const stageH = Math.max(260, height - PANEL_H - insets.bottom + PANEL_R);

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
    const md = mode.get();
    if (md === MODE_AUTO) {
      if (!film) {
        mode.set(MODE_MANUAL);
        syncAnchor(anchor, 0, -1);
        if (reduced) {
          // a still: the level is shown, the blades stand, nothing moves
          level.set(REDUCED_LEVEL);
          keys.set([0, 0, 1, 0]);
        }
        return;
      }
      if (!ready.get()) {
        if (t.get() !== 0) t.set(0);
        syncAnchor(anchor, 0, -1);
        loopStart.set(-1);
        return;
      }
      if (loopStart.get() < 0) {
        loopStart.set(now);
      } else if (gap > STALL_MS) {
        loopStart.set(loopStart.get() + gap - FRAME_MS);
      }
      let el = (now - loopStart.get()) / 1000;
      if (el >= LOOP) {
        loopStart.set(loopStart.get() + LOOP * 1000);
        el -= LOOP;
      }
      syncAnchor(anchor, 1, loopStart.get());
      t.set(el);
      turns.set(filmTurns(el));
      speed.set(filmSpeed(el));
      yaw.set(filmYaw(el));
      keys.set([filmKey(el, 0), filmKey(el, 1), filmKey(el, 2), filmKey(el, 3)]);
      const lv = filmLevel(el);
      if (level.get() !== lv) level.set(lv);
      return;
    }
    syncAnchor(anchor, 0, -1);
    if (md === MODE_RETURN) {
      if (now - returnAt.get() >= RETURN_MS) {
        turns.set(0);
        speed.set(0);
        t.set(0);
        loopStart.set(-1);
        mode.set(MODE_AUTO);
      }
      return;
    }
    // the motor has inertia: the speed chases the level, and the blades integrate it
    const dt = Math.min(0.05, gap / 1000);
    if (!reduced && dt > 0) {
      const target = SPEEDS[level.get()];
      const s = speed.get();
      const tau = target > s ? SPIN_UP_TAU : SPIN_DOWN_TAU;
      const next = target + (s - target) * Math.exp(-dt / tau);
      speed.set(Math.abs(next - target) < 0.002 ? target : next);
      if (speed.get() > 0) turns.set(turns.get() + speed.get() * dt);
      const k = keys.get();
      const blend = 1 - Math.exp(-dt * KEY_RATE * 3);
      const lv = level.get();
      keys.set([k[0] + ((lv === 0 ? 1 : 0) - k[0]) * blend, k[1] + ((lv === 1 ? 1 : 0) - k[1]) * blend, k[2] + ((lv === 2 ? 1 : 0) - k[2]) * blend, k[3] + ((lv === 3 ? 1 : 0) - k[3]) * blend]);
    }
    if (lastTouch.get() < 0 || dragging.get()) {
      lastTouch.set(now);
      return;
    }
    if (!film || now - lastTouch.get() < IDLE_MS) return;
    // 2 s after the last touch: Off, the blades coast to a stop on a quarter turn (the four blades look the same), the
    // head comes home, and the film restarts from t = 0
    mode.set(MODE_RETURN);
    returnAt.set(now);
    level.set(0);
    keys.set([1, 0, 0, 0]);
    const coast = turns.get() + speed.get() * 0.42;
    const stop = Math.ceil(coast * 4) / 4;
    turns.set(withTiming(stop, { duration: RETURN_MS - 60, easing: EASE_OUT }));
    speed.set(withTiming(0, { duration: RETURN_MS - 60, easing: EASE_OUT }));
    yaw.set(withTiming(YAW_REST, { duration: RETURN_MS - 60, easing: EASE_IN_OUT }));
  });

  /** A touch takes over from wherever the film (or the return) is. */
  const pause = useCallback(() => {
    "worklet";
    const md = mode.get();
    if (md === MODE_RETURN) {
      cancelAnimation(turns);
      cancelAnimation(speed);
      cancelAnimation(yaw);
    }
    mode.set(MODE_MANUAL);
    syncAnchor(anchor, 0, -1);
    lastTouch.set(-1);
  }, [anchor, lastTouch, mode, speed, turns, yaw]);

  const userLevel = useCallback(
    (v: number) => {
      "worklet";
      pause();
      level.set(v);
      if (reduced) keys.set([v === 0 ? 1 : 0, v === 1 ? 1 : 0, v === 2 ? 1 : 0, v === 3 ? 1 : 0]);
    },
    [keys, level, pause, reduced],
  );
  const onLevel = useCallback(
    (v: number) => {
      tick();
      onLevelChange?.(v);
      runOnUI(userLevel)(v);
    },
    [onLevelChange, userLevel],
  );

  // drag sideways to aim the head; it stays where it is let go, inside its range
  const pan = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .failOffsetY([-28, 28])
    .onStart(() => {
      "worklet";
      pause();
      dragging.set(true);
      cancelAnimation(yaw);
      dragFrom.set(yaw.get());
    })
    .onUpdate((e) => {
      "worklet";
      yaw.set(aimFromDrag(dragFrom.get(), e.translationX, width));
    })
    .onEnd((e) => {
      "worklet";
      const v = (e.velocityX / width) * AIM_SPAN;
      const to = Math.max(-AIM_MAX, Math.min(AIM_MAX, yaw.get() + v * 0.12));
      yaw.set(reduced ? to : withSpring(to, { ...AIM_SPRING, velocity: v }));
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
  const filming = useDerivedValue<number>(() => (mode.get() === MODE_AUTO ? 1 : 0));

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View accessibilityHint="Drag sideways to aim the fan" accessibilityLabel="Desk fan" collapsable={false} style={[styles.stage, { height: stageH }]}>
          <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, stage]}>
            <FanStage3D anchor={anchor} height={stageH} initialYaw={YAW_REST} keys={keys} onReady={onReady} speed={speed} turns={turns} width={width} yaw={yaw} />
          </Animated.View>
          <LinearGradient colors={[BACKDROP, "rgba(228,215,186,0.6)", "rgba(228,215,186,0)"]} locations={[0, 0.55, 1]} pointerEvents="none" style={[styles.topShade, { height: insets.top + 64 }]} />
        </View>
      </GestureDetector>
      <View pointerEvents="none" style={[styles.header, { top: insets.top + 10, left: GUTTER, right: GUTTER }]}>
        <Text style={styles.app}>{APP}</Text>
      </View>
      <FanPanel bottom={insets.bottom} filming={filming} level={level} onLevel={onLevel} speed={speed} t={t} width={width} />
      <FanAirflow height={stageH + 40} speed={speed} turns={turns} width={width} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BACKDROP },
  stage: { position: "absolute", left: 0, right: 0, top: 0, overflow: "hidden" },
  topShade: { position: "absolute", left: 0, right: 0, top: 0 },
  header: { position: "absolute", flexDirection: "row", justifyContent: "space-between", alignItems: "baseline" },
  app: { fontSize: 17, lineHeight: 22, fontWeight: "600", color: INK },
});
