import * as Haptics from "expo-haptics";
import { LinearGradient } from "expo-linear-gradient";
import { StatusBar } from "expo-status-bar";
import { useCallback } from "react";
import { StyleSheet, Text, useWindowDimensions, View } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  cancelAnimation,
  runOnUI,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN } from "react-native-worklets";

import { useReducedMotionPreference } from "@/shared/useReducedMotionPreference";

import { filmDose, LOOP, ORBIT_MAX, ORBIT_SPAN, orbitFromDrag, PHASE, statusAt } from "./espressoMotion";
import { EspressoPanel, GUTTER, PANEL_H, PANEL_R } from "./EspressoPanel";
import { APP, BACKDROP, INK, MACHINE, MUTE } from "./espressoScene";
import { EspressoStage3D } from "./EspressoStage3D";

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

/** This long after the last touch the machine goes back to rest, and RETURN_MS later the film restarts. */
const IDLE_MS = 2000;
const RETURN_MS = 900;
const STALL_MS = 100;
const FRAME_MS = 1000 / 60;
const ANCHOR_BEAT = 30;
const DOSE_MS = 420;
const ORBIT_SPRING = { mass: 1, stiffness: 140, damping: 18 };

function buzzBrew(): void {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

function buzzDone(): void {
  void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
}

function syncAnchor(anchor: SharedValue<number[]>, playing: number, start: number): void {
  "worklet";
  const a = anchor.get();
  if (a[0] !== playing || a[1] !== start) anchor.set([playing, start]);
}

export type EspressoBrewProgressProps = {
  autoPlay?: boolean;
  /** A finger started a shot: 0 = single, 1 = double. */
  onBrew?: (dose: number) => void;
  /** A finger's shot is done. */
  onShotReady?: () => void;
};

export function EspressoBrewProgress({ autoPlay = true, onBrew, onShotReady }: EspressoBrewProgressProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = !reduced && autoPlay;
  // reduced motion rests on the film's result: a double, poured, the cup full
  const initialTime = reduced ? PHASE.full : 0;
  const initialDose = reduced ? 1 : 0;

  const t = useSharedValue(initialTime);
  const anchor = useSharedValue<number[]>([0, -1]);
  const dose = useSharedValue(initialDose);
  const doseSel = useSharedValue(initialDose);
  const orbit = useSharedValue(0);
  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  /** A finger's shot runs t forward to runTo (-1: holds); `chain` starts a shot when it gets there (after a cup change). */
  const runTo = useSharedValue(-1);
  const chain = useSharedValue(false);
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
      const d = filmDose(el);
      if (doseSel.get() !== d) {
        doseSel.set(d);
        dose.set(d);
      }
      return;
    }
    syncAnchor(anchor, 0, -1);
    // a finger's shot (or a cup change) plays the film's timeline in real time
    const to = runTo.get();
    if (to >= 0) {
      const dt = Math.min(0.05, gap / 1000);
      const next = t.get() + dt;
      if (next >= to) {
        if (chain.get()) {
          chain.set(false);
          t.set(PHASE.brew);
          runTo.set(PHASE.full);
        } else {
          t.set(to);
          runTo.set(-1);
        }
      } else {
        t.set(next);
      }
    }
    if (md === MODE_RETURN) {
      if (runTo.get() < 0 && now - returnAt.get() >= RETURN_MS) {
        t.set(0);
        loopStart.set(-1);
        mode.set(MODE_AUTO);
      }
      return;
    }
    if (lastTouch.get() < 0 || dragging.get() || runTo.get() >= 0) {
      lastTouch.set(now);
      return;
    }
    if (!film || now - lastTouch.get() < IDLE_MS) return;
    // 2 s after the last touch: a full cup is changed for an empty one and the camera comes home; the film's restart
    // puts Single back (the cup is empty by then, so the dose changes nothing on screen)
    mode.set(MODE_RETURN);
    returnAt.set(now);
    const cur = t.get();
    if (cur >= PHASE.brew && cur < PHASE.cupInEnd) runTo.set(PHASE.cupInEnd);
    orbit.set(withSpring(0, ORBIT_SPRING));
  });

  // a finger's shot is done: one tap of the haptics, and the host hears of it
  useAnimatedReaction(
    () => statusAt(t.get()),
    (cur, prev) => {
      if (prev == null || cur === prev || mode.get() === MODE_AUTO || cur !== 3) return;
      scheduleOnRN(buzzDone);
      if (onShotReady != null) scheduleOnRN(onShotReady);
    },
  );

  /** A touch takes over from wherever the film (or the return) is; a shot in progress keeps pouring. */
  const pause = useCallback(() => {
    "worklet";
    const md = mode.get();
    if (md === MODE_RETURN) {
      cancelAnimation(orbit);
      cancelAnimation(dose);
      chain.set(false);
    }
    if (md === MODE_AUTO) {
      const cur = t.get();
      chain.set(false);
      if (cur >= PHASE.cupInEnd) {
        t.set(0);
        runTo.set(-1);
      } else if (cur >= PHASE.brew && cur < PHASE.full) {
        runTo.set(PHASE.full);
      } else if (cur >= PHASE.cupOut) {
        runTo.set(PHASE.cupInEnd);
      } else {
        runTo.set(-1);
      }
    }
    mode.set(MODE_MANUAL);
    syncAnchor(anchor, 0, -1);
    lastTouch.set(-1);
  }, [anchor, chain, dose, lastTouch, mode, orbit, runTo, t]);

  const userBrew = useCallback(() => {
    "worklet";
    pause();
    const cur = t.get();
    if (reduced) {
      // no pour: the shot is simply there
      runTo.set(-1);
      t.set(PHASE.full);
      return;
    }
    if (runTo.get() >= 0) {
      // already pouring, or changing the cup: brew when the fresh cup is in
      if (runTo.get() === PHASE.cupInEnd) chain.set(true);
      return;
    }
    if (cur >= PHASE.brew && cur < PHASE.cupInEnd) {
      // the cup is full: change it, then brew
      runTo.set(PHASE.cupInEnd);
      chain.set(true);
      return;
    }
    t.set(PHASE.brew);
    runTo.set(PHASE.full);
  }, [chain, pause, reduced, runTo, t]);

  const userDose = useCallback(
    (v: number) => {
      "worklet";
      pause();
      doseSel.set(v);
      dose.set(reduced ? v : withTiming(v, { duration: DOSE_MS }));
    },
    [dose, doseSel, pause, reduced],
  );

  const onBrewPress = useCallback(() => {
    buzzBrew();
    onBrew?.(doseSel.get());
    runOnUI(userBrew)();
  }, [doseSel, onBrew, userBrew]);

  const onDosePress = useCallback(
    (v: number) => {
      void Haptics.selectionAsync();
      runOnUI(userDose)(v);
    },
    [userDose],
  );

  // drag sideways to walk round the machine a little; it springs back inside its range
  const pan = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .failOffsetY([-24, 24])
    .onStart(() => {
      "worklet";
      pause();
      dragging.set(true);
      cancelAnimation(orbit);
      dragFrom.set(orbit.get());
    })
    .onUpdate((e) => {
      "worklet";
      orbit.set(orbitFromDrag(dragFrom.get(), e.translationX, width));
    })
    .onEnd((e) => {
      "worklet";
      const v = (e.velocityX / width) * ORBIT_SPAN;
      const to = Math.max(-ORBIT_MAX, Math.min(ORBIT_MAX, orbit.get() + v * 0.1));
      orbit.set(reduced ? to : withSpring(to, { ...ORBIT_SPRING, velocity: v }));
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
        <View accessibilityHint="Drag sideways to look round the machine" accessibilityLabel="Espresso machine" collapsable={false} style={[styles.stage, { height: stageH }]}>
          <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, stage]}>
            <EspressoStage3D anchor={anchor} dose={dose} height={stageH} initialDose={initialDose} initialTime={initialTime} onReady={onReady} orbit={orbit} time={t} width={width} />
          </Animated.View>
          <LinearGradient colors={[BACKDROP, "rgba(232,223,208,0.6)", "rgba(232,223,208,0)"]} locations={[0, 0.55, 1]} pointerEvents="none" style={[styles.topShade, { height: insets.top + 64 }]} />
        </View>
      </GestureDetector>
      <View pointerEvents="none" style={[styles.header, { top: insets.top + 10, left: GUTTER, right: GUTTER }]}>
        <Text style={styles.app}>{APP}</Text>
        <Text style={styles.machine}>{MACHINE}</Text>
      </View>
      <EspressoPanel
        bottom={insets.bottom}
        dose={dose}
        doseSel={doseSel}
        filming={filming}
        initialDose={initialDose}
        initialStatus={statusAt(initialTime)}
        onBrew={onBrewPress}
        onDose={onDosePress}
        t={t}
        width={width}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BACKDROP },
  stage: { position: "absolute", left: 0, right: 0, top: 0, overflow: "hidden" },
  topShade: { position: "absolute", left: 0, right: 0, top: 0 },
  header: { position: "absolute", flexDirection: "row", justifyContent: "space-between", alignItems: "baseline" },
  app: { fontSize: 17, lineHeight: 22, fontWeight: "600", color: INK },
  machine: { fontSize: 15, lineHeight: 20, color: MUTE },
});
