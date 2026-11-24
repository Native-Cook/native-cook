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
import Svg, { Defs, Ellipse, RadialGradient, Stop } from "react-native-svg";
import { scheduleOnRN } from "react-native-worklets";

import { useReducedMotionPreference } from "@/shared/useReducedMotionPreference";

import {
  angleDelta,
  DEG_PER_NUMBER,
  dialGhost,
  dialNumber,
  DIAL_N,
  DOOR_DEG,
  doorAt,
  filmDial,
  filmEntered,
  filmUnlock,
  LOOP,
  project,
  seg,
  UNLOCK,
} from "./safeMotion";
import { GUTTER, PANEL_H, PANEL_R, SafePanel } from "./SafePanel";
import { APP, BACKDROP, BORE_RADIUS, DEMO_CODE, DIAL_FACE_Z, DIAL_PIVOT, DOOR_CENTRE, DOOR_HINGE, DOOR_RADIUS, FRONT_Z, GLOW, INK } from "./safeScene";
import { SafeStage3D } from "./SafeStage3D";

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

/** This long after the last touch the safe goes back to rest, and RETURN_MS later the film restarts. */
const IDLE_MS = 2000;
const RETURN_MS = 1250;
const STALL_MS = 100;
const FRAME_MS = 1000 / 60;
const ANCHOR_BEAT = 30;
/** A release: how far ahead the dial's speed carries it (s), and the spring that lands it on a number. */
const FLICK_S = 0.16;
const DIAL_SPRING = { mass: 1, stiffness: 170, damping: 22 };
const SETTLED = 0.012;
/** The wrong code: the safe shakes this far (m), and the entries clear this long after. */
const SHAKE_M = 0.007;
const SHAKE_STEP_MS = 55;
const WRONG_HOLD_MS = 520;
const RELOCK_MS = 1200;
const LINEAR = Easing.linear;
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
/** The film's finger circles the dial at this radius (world metres) from this starting angle (deg clockwise). */
const GHOST_RADIUS = 0.066;
const GHOST_START = 58;
const DOT = 44;
const GLOW_W = 240;
const GLOW_H = 360;

const DIAL_WORLD_Y = DOOR_HINGE[1] + DIAL_PIVOT[1];

function buzzTick(): void {
  void Haptics.selectionAsync();
}

function buzzRight(): void {
  void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
}

function buzzWrong(): void {
  void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
}

function syncAnchor(anchor: SharedValue<number[]>, playing: number, start: number): void {
  "worklet";
  const a = anchor.get();
  if (a[0] !== playing || a[1] !== start) anchor.set([playing, start]);
}

export type CombinationSafeDialProps = {
  /** The combination: three numbers, 0..39. */
  code?: readonly [number, number, number];
  autoPlay?: boolean;
  /** A finger entered the right combination. */
  onUnlock?: () => void;
};

export function CombinationSafeDial({ code = DEMO_CODE, autoPlay = true, onUnlock }: CombinationSafeDialProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = !reduced && autoPlay;
  const c0 = code[0];
  const c1 = code[1];
  const c2 = code[2];
  // reduced motion rests on the film's result: the right numbers in, the door open
  const initialDial = reduced ? (c2 === 0 ? 0 : c2 - DIAL_N) : 0;
  const initialUnlock = reduced ? UNLOCK : 0;
  const initialCount = reduced ? 3 : 0;

  const t = useSharedValue(0);
  const anchor = useSharedValue<number[]>([0, -1]);
  const dial = useSharedValue(initialDial);
  const unlock = useSharedValue(initialUnlock);
  const shake = useSharedValue(0);
  const count = useSharedValue(initialCount);
  const e0 = useSharedValue(reduced ? c0 : 0);
  const e1 = useSharedValue(reduced ? c1 : 0);
  const e2 = useSharedValue(reduced ? c2 : 0);
  const wrong = useSharedValue(0);
  const canLock = useSharedValue(reduced ? 1 : 0);
  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const loopStart = useSharedValue(-1);
  const ready = useSharedValue(false);
  const shown = useSharedValue(0);
  const nowMs = useSharedValue(-1);
  const beat = useSharedValue(0);
  const lastTouch = useSharedValue(-1);
  const returnAt = useSharedValue(-1);
  const dragging = useSharedValue(false);
  const lastAngle = useSharedValue(0);
  const centreX = useSharedValue(0);
  const centreY = useSharedValue(0);
  /** The number a released dial is landing on (NaN: none), and whether the finger really turned it. */
  const landing = useSharedValue(Number.NaN);
  const turned = useSharedValue(false);
  const clearAt = useSharedValue(-1);

  const stageH = Math.max(240, height - PANEL_H - insets.bottom + PANEL_R);

  const clearEntries = useCallback(() => {
    "worklet";
    count.set(0);
    wrong.set(withTiming(0, { duration: 180 }));
    clearAt.set(-1);
  }, [clearAt, count, wrong]);

  /** A number is in: the third one decides. */
  const commit = useCallback(
    (n: number) => {
      "worklet";
      const i = count.get();
      if (i >= 3 || canLock.get() > 0.5 || clearAt.get() >= 0) return;
      if (i === 0) e0.set(n);
      if (i === 1) e1.set(n);
      if (i === 2) e2.set(n);
      count.set(i + 1);
      if (i < 2) return;
      const right = e0.get() === c0 && e1.get() === c1 && n === c2;
      if (right) {
        canLock.set(1);
        unlock.set(reduced ? UNLOCK : withTiming(UNLOCK, { duration: UNLOCK * 1000, easing: LINEAR }));
        scheduleOnRN(buzzRight);
        if (onUnlock != null) scheduleOnRN(onUnlock);
        return;
      }
      scheduleOnRN(buzzWrong);
      wrong.set(withTiming(1, { duration: 120 }));
      clearAt.set(nowMs.get() + WRONG_HOLD_MS);
      if (!reduced) {
        shake.set(
          withSequence(
            withTiming(SHAKE_M, { duration: SHAKE_STEP_MS }),
            withTiming(-SHAKE_M, { duration: SHAKE_STEP_MS * 2 }),
            withTiming(SHAKE_M * 0.6, { duration: SHAKE_STEP_MS * 2 }),
            withTiming(-SHAKE_M * 0.3, { duration: SHAKE_STEP_MS * 2 }),
            withTiming(0, { duration: SHAKE_STEP_MS }),
          ),
        );
      }
    },
    [c0, c1, c2, canLock, clearAt, count, e0, e1, e2, nowMs, onUnlock, reduced, shake, unlock, wrong],
  );

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
      dial.set(filmDial(el, c0, c1, c2));
      unlock.set(filmUnlock(el));
      const n = filmEntered(el);
      if (count.get() !== n) {
        e0.set(c0);
        e1.set(c1);
        e2.set(c2);
        count.set(n);
      }
      return;
    }
    syncAnchor(anchor, 0, -1);
    // a released dial has landed on its number
    const target = landing.get();
    if (!Number.isNaN(target) && !dragging.get() && Math.abs(dial.get() - target) < SETTLED) {
      landing.set(Number.NaN);
      if (turned.get()) {
        turned.set(false);
        commit(dialNumber(target));
      }
    }
    if (clearAt.get() >= 0 && now >= clearAt.get()) clearEntries();
    if (md === MODE_RETURN) {
      if (now - returnAt.get() >= RETURN_MS) {
        dial.set(0);
        t.set(0);
        loopStart.set(-1);
        mode.set(MODE_AUTO);
      }
      return;
    }
    if (lastTouch.get() < 0 || dragging.get() || !Number.isNaN(landing.get())) {
      lastTouch.set(now);
      return;
    }
    if (!film || now - lastTouch.get() < IDLE_MS) return;
    // 2 s after the last touch: the door shuts, the entries clear and the dial goes home by the short way
    mode.set(MODE_RETURN);
    returnAt.set(now);
    canLock.set(0);
    clearEntries();
    const home = Math.round(dial.get() / DIAL_N) * DIAL_N;
    dial.set(withTiming(home, { duration: 900, easing: EASE_IN_OUT }));
    unlock.set(withTiming(0, { duration: unlock.get() > 0 ? RETURN_MS - 80 : 1, easing: LINEAR }));
  });

  // a tick as a finger's dial passes each number (the film stays silent)
  useAnimatedReaction(
    () => dialNumber(dial.get()),
    (cur, prev) => {
      if (prev == null || cur === prev || reduced || mode.get() !== MODE_MANUAL) return;
      scheduleOnRN(buzzTick);
    },
  );

  /** A touch takes over from wherever the film (or the return) is. */
  const pause = useCallback(() => {
    "worklet";
    const md = mode.get();
    if (md === MODE_AUTO) {
      // the film's open door finishes the way it was going, and stays unlocked until the finger locks it
      const u = unlock.get();
      if (u > 0.01) {
        const opening = t.get() < 7.3;
        canLock.set(opening ? 1 : 0);
        const to = opening ? UNLOCK : 0;
        unlock.set(withTiming(to, { duration: Math.abs(to - u) * 1000, easing: LINEAR }));
        if (!opening) count.set(0);
      } else if (count.get() >= 3) {
        count.set(0);
      }
      // a dial caught between numbers lands on the nearest one, without entering it
      const near = Math.round(dial.get());
      dial.set(withSpring(near, DIAL_SPRING));
      landing.set(near);
      turned.set(false);
    }
    if (md === MODE_RETURN) {
      cancelAnimation(dial);
      landing.set(Math.round(dial.get()));
      dial.set(withSpring(Math.round(dial.get()), DIAL_SPRING));
      turned.set(false);
    }
    mode.set(MODE_MANUAL);
    syncAnchor(anchor, 0, -1);
    lastTouch.set(-1);
  }, [anchor, canLock, count, dial, landing, lastTouch, mode, t, turned, unlock]);

  const relock = useCallback(() => {
    "worklet";
    pause();
    canLock.set(0);
    count.set(0);
    unlock.set(reduced ? 0 : withTiming(0, { duration: RELOCK_MS, easing: LINEAR }));
    lastTouch.set(-1);
  }, [canLock, count, lastTouch, pause, reduced, unlock]);

  const onLock = useCallback(() => {
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    runOnUI(relock)();
  }, [relock]);

  // turn the dial: the finger's angle round the dial's centre, wherever on the stage it is
  const pan = Gesture.Pan()
    .minDistance(2)
    .onStart((e) => {
      "worklet";
      pause();
      if (canLock.get() > 0.5) return;
      dragging.set(true);
      cancelAnimation(dial);
      landing.set(Number.NaN);
      const c = [0, 0];
      project(0, DIAL_WORLD_Y, DIAL_FACE_Z, doorAt(unlock.get()), width, stageH, c);
      centreX.set(c[0]);
      centreY.set(c[1]);
      lastAngle.set(Math.atan2(e.y - c[1], e.x - c[0]));
    })
    .onUpdate((e) => {
      "worklet";
      if (!dragging.get()) return;
      const a = Math.atan2(e.y - centreY.get(), e.x - centreX.get());
      const d = angleDelta(lastAngle.get(), a);
      lastAngle.set(a);
      // on screen a positive angle is clockwise, and a clockwise dial counts down
      dial.set(dial.get() - (d * 180) / Math.PI / DEG_PER_NUMBER);
      if (Math.abs(d) > 0.001) turned.set(true);
    })
    .onEnd((e) => {
      "worklet";
      if (!dragging.get()) return;
      const rx = e.x - centreX.get();
      const ry = e.y - centreY.get();
      const r2 = Math.max(400, rx * rx + ry * ry);
      const omega = (rx * e.velocityY - ry * e.velocityX) / r2;
      const v = (-omega * 180) / Math.PI / DEG_PER_NUMBER;
      const target = Math.round(dial.get() + v * FLICK_S);
      landing.set(target);
      dial.set(reduced ? target : withSpring(target, { ...DIAL_SPRING, velocity: v }));
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

  // warm light from inside the safe, standing in the gap between the door's free edge and the bore
  const glow = useAnimatedStyle(() => {
    const open = doorAt(unlock.get());
    const a = (open * DOOR_DEG * Math.PI) / 180;
    const reach = DOOR_CENTRE[0] + DOOR_RADIUS;
    const edge = [0, 0];
    const bore = [0, 0];
    const top = [0, 0];
    project(DOOR_HINGE[0] + reach * Math.cos(a), DOOR_HINGE[1], DOOR_HINGE[2] + reach * Math.sin(a), open, width, stageH, edge);
    project(BORE_RADIUS, DOOR_HINGE[1], FRONT_Z, open, width, stageH, bore);
    project(BORE_RADIUS, DOOR_HINGE[1] + BORE_RADIUS, FRONT_Z, open, width, stageH, top);
    const span = Math.abs(bore[1] - top[1]) * 2.5;
    const w = Math.max(40, Math.abs(edge[0] - bore[0]) * 2.1);
    return {
      opacity: seg(open, 0.05, 0.42) * 0.82,
      transform: [
        { translateX: (edge[0] + bore[0]) / 2 - GLOW_W / 2 },
        { translateY: bore[1] - GLOW_H / 2 },
        { scaleX: w / GLOW_W },
        { scaleY: span / GLOW_H },
      ],
    };
  });

  // the film's finger, circling the dial with it
  const ghost = useAnimatedStyle(() => {
    const g = [0, 0];
    dialGhost(t.get(), g);
    const c = [0, 0];
    const e = [0, 0];
    project(0, DIAL_WORLD_Y, DIAL_FACE_Z, 0, width, stageH, c);
    project(GHOST_RADIUS, DIAL_WORLD_Y, DIAL_FACE_Z, 0, width, stageH, e);
    const r = Math.abs(e[0] - c[0]);
    const th = ((GHOST_START - dial.get() * DEG_PER_NUMBER) * Math.PI) / 180;
    return {
      opacity: mode.get() === MODE_AUTO ? g[0] * 0.9 : 0,
      transform: [{ translateX: c[0] + r * Math.sin(th) - DOT / 2 }, { translateY: c[1] - r * Math.cos(th) - DOT / 2 }, { scale: 1 - 0.16 * g[1] }],
    };
  });

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View accessibilityHint="Drag round the dial to turn it" accessibilityLabel="Safe dial" collapsable={false} style={[styles.stage, { height: stageH }]}>
          <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, stage]}>
            <SafeStage3D
              anchor={anchor}
              code={code}
              dial={dial}
              height={stageH}
              initialDial={initialDial}
              initialUnlock={initialUnlock}
              onReady={onReady}
              shake={shake}
              unlock={unlock}
              width={width}
            />
          </Animated.View>
          <Animated.View pointerEvents="none" style={[styles.glow, glow]}>
            <Svg height={GLOW_H} width={GLOW_W}>
              <Defs>
                <RadialGradient cx="50%" cy="50%" id="safeGlow" rx="50%" ry="50%">
                  <Stop offset="0" stopColor={GLOW} stopOpacity={0.85} />
                  <Stop offset="0.38" stopColor={GLOW} stopOpacity={0.34} />
                  <Stop offset="0.72" stopColor={GLOW} stopOpacity={0.08} />
                  <Stop offset="1" stopColor={GLOW} stopOpacity={0} />
                </RadialGradient>
              </Defs>
              <Ellipse cx={GLOW_W / 2} cy={GLOW_H / 2} fill="url(#safeGlow)" rx={GLOW_W / 2} ry={GLOW_H / 2} />
            </Svg>
          </Animated.View>
          <Animated.View pointerEvents="none" style={[styles.dot, ghost]} />
          <LinearGradient colors={[BACKDROP, "rgba(207,197,178,0.6)", "rgba(207,197,178,0)"]} locations={[0, 0.55, 1]} pointerEvents="none" style={[styles.topShade, { height: insets.top + 64 }]} />
        </View>
      </GestureDetector>
      <View pointerEvents="none" style={[styles.header, { top: insets.top + 10, left: GUTTER, right: GUTTER }]}>
        <Text style={styles.app}>{APP}</Text>
      </View>
      <SafePanel
        bottom={insets.bottom}
        canLock={canLock}
        count={count}
        dial={dial}
        e0={e0}
        e1={e1}
        e2={e2}
        initialCount={initialCount}
        initialEntries={code}
        onLock={onLock}
        unlock={unlock}
        width={width}
        wrong={wrong}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BACKDROP },
  stage: { position: "absolute", left: 0, right: 0, top: 0, overflow: "hidden" },
  glow: { position: "absolute", left: 0, top: 0, width: GLOW_W, height: GLOW_H },
  dot: { position: "absolute", left: 0, top: 0, width: DOT, height: DOT, borderRadius: DOT / 2, backgroundColor: "rgba(250,246,236,0.3)", borderWidth: 1.5, borderColor: "rgba(250,246,236,0.75)" },
  topShade: { position: "absolute", left: 0, right: 0, top: 0 },
  header: { position: "absolute", flexDirection: "row", justifyContent: "space-between", alignItems: "baseline" },
  app: { fontSize: 17, lineHeight: 22, fontWeight: "600", color: INK },
});
