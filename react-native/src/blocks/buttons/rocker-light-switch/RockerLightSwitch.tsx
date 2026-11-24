import * as Haptics from "expo-haptics";
import { LinearGradient } from "expo-linear-gradient";
import { StatusBar } from "expo-status-bar";
import { useCallback, useMemo } from "react";
import { StyleSheet, Text, useWindowDimensions, View } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  cancelAnimation,
  Easing,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN } from "react-native-worklets";

import { useReducedMotionPreference } from "@/shared/useReducedMotionPreference";

import { SwitchLight } from "./SwitchLight";
import {
  BRIGHT_MIN,
  cameraAt,
  COOL,
  detent,
  detentInverse,
  filmBright,
  filmGhost,
  filmLevel,
  filmOrbit,
  filmPress,
  IDLE_MS,
  LAG,
  LAG_OFF,
  LOOP,
  PRESS_DEPTH,
  project,
  RETURN_MS,
  SNAP_SPRING,
  WARM,
} from "./switchMotion";
import { GUTTER, PANEL_H, PANEL_R, SwitchPanel } from "./SwitchPanel";
import { APP, DUSK, PLATE_HALF_H, PLATE_HALF_W, ROCKER_FACE_Z, ROCKER_HALF_H, ROCKER_PIVOT } from "./switchScene";
import type { Vec3 } from "./switchScene";
import { SwitchStage3D } from "./SwitchStage3D";

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

/** Points of vertical drag for the rocker's whole travel. */
const DRAG_PT = 84;
/** A finger's press reaches the tipping point in this long (ms); the film's finger is slower. */
const TAP_PRESS_MS = 130;
/** The hit area: the plate's outline on screen, grown by this much. */
const HIT_PAD = 18;
const DOT = 46;
const EASE_OUT = Easing.out(Easing.cubic);
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);

function click(): void {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

/** Publishes the film's clock for the Filament thread (only when it changes): [1 while the film plays, the UI frame
 * time (ms) of its t = 0]. */
function syncAnchor(anchor: SharedValue<number[]>, playing: number, start: number): void {
  "worklet";
  const a = anchor.get();
  if (a[0] !== playing || a[1] !== start) anchor.set([playing, start]);
}

export type RockerLightSwitchProps = {
  autoPlay?: boolean;
};

export function RockerLightSwitch({ autoPlay = true }: RockerLightSwitchProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = !reduced && autoPlay;

  const t = useSharedValue(0);
  /** The rocker, 0 (off) .. 1 (on); the light is on from 0.5. Reduced motion rests with the light on. */
  const press = useSharedValue(reduced ? 1 : 0);
  const level = useSharedValue(reduced ? 1 : 0);
  const bright = useSharedValue(1);
  const orbit = useSharedValue(0);
  const anchor = useSharedValue<number[]>([0, -1]);
  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const loopStart = useSharedValue(-1);
  const ready = useSharedValue(false);
  const lastActivity = useSharedValue(-1);
  const returnAt = useSharedValue(-1);
  const dragging = useSharedValue(false);
  const sliding = useSharedValue(false);
  const dragMode = useSharedValue(false);
  const fromX = useSharedValue(0);
  const fromY = useSharedValue(0);
  /** The finger's own dot is not drawn; the film's is: [opacity, half, pressed]. */
  const ghost = useSharedValue<number[]>([0, 1, 0]);

  // the 3D stage is the window above the sheet (it runs a little way under the sheet's round corners)
  const panelH = PANEL_H + insets.bottom;
  const stageH = Math.max(160, height - panelH + PANEL_R);

  // the plate on screen with the camera at rest: the touch target, and where the film's finger lands
  const plate = useMemo(() => {
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(0, 0, c);
    const o = [0, 0, 0];
    project(c, width, stageH, [ROCKER_PIVOT[0], ROCKER_PIVOT[1], ROCKER_FACE_Z + ROCKER_PIVOT[2]] as Vec3, o);
    return { x: o[0], y: o[1], w: PLATE_HALF_W * 2 * o[2], h: PLATE_HALF_H * 2 * o[2], half: ROCKER_HALF_H * o[2] };
  }, [stageH, width]);

  useFrameCallback((frame) => {
    "worklet";
    const now = frame.timestamp;
    const md = mode.get();
    if (md === MODE_AUTO) {
      if (!film) {
        mode.set(MODE_MANUAL);
        return;
      }
      if (!ready.get()) {
        t.set(0);
        syncAnchor(anchor, 0, -1);
        return;
      }
      if (loopStart.get() < 0) loopStart.set(now);
      syncAnchor(anchor, 1, loopStart.get());
      const tt = ((now - loopStart.get()) / 1000) % LOOP;
      t.set(tt);
      press.set(filmPress(tt));
      level.set(filmLevel(tt));
      bright.set(filmBright(tt));
      orbit.set(filmOrbit(tt));
      const g = [0, 1, 0];
      filmGhost(tt, g);
      const cur = ghost.get();
      if (cur[0] !== g[0] || cur[1] !== g[1] || cur[2] !== g[2]) ghost.set(g);
      return;
    }
    syncAnchor(anchor, 0, -1);
    if (md === MODE_RETURN) {
      // the switch is off again and the camera home: the film starts over
      if (now - returnAt.get() >= RETURN_MS) {
        t.set(0);
        loopStart.set(-1);
        mode.set(MODE_AUTO);
      }
      return;
    }
    if (lastActivity.get() < 0 || dragging.get() || sliding.get()) {
      lastActivity.set(now);
      return;
    }
    if (!film || now - lastActivity.get() < IDLE_MS) return;
    // 2 s after the last touch: the rocker snaps off, the slider and the camera ease home, then the film restarts
    mode.set(MODE_RETURN);
    returnAt.set(now);
    if (press.get() >= 0.5) {
      press.set(withSequence(withTiming(1 - PRESS_DEPTH, { duration: TAP_PRESS_MS, easing: Easing.in(Easing.quad) }), withSpring(0, SNAP_SPRING)));
    } else {
      press.set(withSpring(0, SNAP_SPRING));
    }
    bright.set(withTiming(1, { duration: 500, easing: EASE_IN_OUT }));
    orbit.set(withTiming(0, { duration: 800, easing: EASE_IN_OUT }));
  });

  // the latch: a click, and (outside the film, which has its own curve) the filament warming up or dying
  useAnimatedReaction(
    () => press.get() >= 0.5,
    (cur, prev) => {
      if (prev == null || cur === prev) return;
      scheduleOnRN(click);
      if (mode.get() === MODE_AUTO) return;
      if (reduced) {
        level.set(withTiming(cur ? 1 : 0, { duration: 200 }));
        return;
      }
      level.set(
        cur
          ? withDelay(LAG * 1000, withTiming(1, { duration: WARM * 1000, easing: EASE_OUT }))
          : withDelay(LAG_OFF * 1000, withTiming(0, { duration: COOL * 1000, easing: EASE_OUT })),
      );
    },
  );

  const pause = useCallback(() => {
    "worklet";
    if (mode.get() === MODE_AUTO) {
      // leave the film where it is: the light settles to what the latch says
      level.set(withTiming(press.get() >= 0.5 ? 1 : 0, { duration: 300, easing: EASE_OUT }));
      ghost.set([0, 1, 0]);
    }
    if (mode.get() === MODE_RETURN) {
      cancelAnimation(bright);
      cancelAnimation(orbit);
    }
    mode.set(MODE_MANUAL);
    // the Filament thread stops computing the film at once and shows the shared values from here on
    syncAnchor(anchor, 0, -1);
    lastActivity.set(-1);
  }, [anchor, bright, ghost, lastActivity, level, mode, orbit, press]);

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      "worklet";
      pause();
      dragging.set(true);
      dragMode.set(false);
      // a press on the raised half tips the rocker; on the half already in, it only gives a little
      const wantOn = e.y < plate.h / 2 + HIT_PAD;
      const target = wantOn ? 1 : 0;
      const isOn = press.get() >= 0.5;
      cancelAnimation(press);
      if (wantOn !== isOn) {
        const tip = wantOn ? PRESS_DEPTH : 1 - PRESS_DEPTH;
        press.set(withSequence(withTiming(tip, { duration: reduced ? 60 : TAP_PRESS_MS, easing: Easing.in(Easing.quad) }), withSpring(target, SNAP_SPRING)));
      } else {
        const give = wantOn ? 1.05 : -0.05;
        press.set(withSequence(withTiming(give, { duration: 90, easing: EASE_OUT }), withSpring(target, SNAP_SPRING)));
      }
    })
    .onUpdate((e) => {
      "worklet";
      if (!dragMode.get()) {
        if (Math.abs(e.translationY) < 8) return;
        // a drag takes the rocker over from wherever the press left it
        dragMode.set(true);
        cancelAnimation(press);
        fromX.set(detentInverse(Math.min(1, Math.max(0, press.get()))));
        fromY.set(e.translationY);
      }
      // dragging up tips it on; rubber-banded past either rest
      let x = fromX.get() - (e.translationY - fromY.get()) / DRAG_PT;
      if (x > 1) x = 1 + (x - 1) / (1 + (x - 1) * 6) * 0.25;
      else if (x < 0) x = x / (1 - x * 6) * 0.25;
      press.set(x > 1 || x < 0 ? x : detent(x));
    })
    .onEnd((e) => {
      "worklet";
      if (!dragMode.get()) return;
      // released: it falls to the side it is on (a flick counts), with the finger's velocity
      const v = -e.velocityY / DRAG_PT;
      const target = press.get() + v * 0.08 >= 0.5 ? 1 : 0;
      press.set(withSpring(target, { ...SNAP_SPRING, velocity: v }));
    })
    .onFinalize(() => {
      "worklet";
      dragging.set(false);
      dragMode.set(false);
    });

  const onBrightness = useCallback(
    (v: number) => {
      pause();
      cancelAnimation(bright);
      bright.set(Math.min(1, Math.max(BRIGHT_MIN, v)));
    },
    [bright, pause],
  );
  const onSliding = useCallback(
    (active: boolean) => {
      if (active) pause();
      sliding.set(active);
    },
    [pause, sliding],
  );
  const onReady = useCallback(() => {
    ready.set(true);
  }, [ready]);

  // the film's finger: a soft dot on the half being pressed
  const dot = useAnimatedStyle(() => {
    const g = ghost.get();
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(orbit.get(), t.get(), c);
    const o = [0, 0, 0];
    const p: Vec3 = [ROCKER_PIVOT[0], ROCKER_PIVOT[1] + g[1] * ROCKER_HALF_H * 0.55, ROCKER_PIVOT[2] + ROCKER_FACE_Z];
    project(c, width, stageH, p, o);
    return {
      opacity: g[0] * 0.9,
      transform: [{ translateX: o[0] - DOT / 2 }, { translateY: o[1] - DOT / 2 }, { scale: 1 - 0.16 * g[2] }],
    };
  });

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <View pointerEvents="none" style={[styles.stage, { height: stageH }]}>
        <SwitchStage3D anchor={anchor} bright={bright} height={stageH} level={level} onReady={onReady} orbit={orbit} press={press} t={t} width={width} />
        <SwitchLight bright={bright} height={stageH} level={level} orbit={orbit} t={t} width={width} />
        {/* a little shade under the status bar and the app's name, so both read on the lit wall */}
        <LinearGradient colors={["rgba(12,14,22,0.42)", "rgba(12,14,22,0.14)", "rgba(12,14,22,0)"]} locations={[0, 0.6, 1]} style={[styles.topShade, { height: insets.top + 64 }]} />
        <Animated.View style={[styles.dot, dot]} />
      </View>
      <View pointerEvents="none" style={[styles.header, { top: insets.top + 10, left: GUTTER, right: GUTTER }]}>
        <Text style={styles.app}>{APP}</Text>
        <Text style={styles.headerNote}>Evening</Text>
      </View>
      <GestureDetector gesture={pan}>
        <View
          accessibilityHint="Press the top half to turn the light on, the bottom half to turn it off, or drag to tip it"
          accessibilityLabel="Light switch"
          accessibilityRole="button"
          style={[styles.hit, { left: plate.x - plate.w / 2 - HIT_PAD, top: plate.y - plate.h / 2 - HIT_PAD, width: plate.w + HIT_PAD * 2, height: plate.h + HIT_PAD * 2 }]}
        />
      </GestureDetector>
      <SwitchPanel bottom={insets.bottom} bright={bright} initialOn={reduced} onBrightness={onBrightness} onSliding={onSliding} press={press} sliding={sliding} width={width} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: DUSK },
  stage: { position: "absolute", left: 0, right: 0, top: 0, overflow: "hidden" },
  topShade: { position: "absolute", left: 0, right: 0, top: 0 },
  header: { position: "absolute", flexDirection: "row", justifyContent: "space-between", alignItems: "baseline" },
  app: { fontSize: 17, lineHeight: 22, fontWeight: "600", color: "rgba(255,252,246,0.96)" },
  headerNote: { fontSize: 15, lineHeight: 20, color: "rgba(255,252,246,0.72)" },
  hit: { position: "absolute" },
  dot: {
    position: "absolute",
    left: 0,
    top: 0,
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
    backgroundColor: "rgba(255,255,255,0.34)",
    borderWidth: 1.5,
    borderColor: "rgba(255,255,255,0.7)",
    shadowColor: "#000000",
    shadowOpacity: 0.25,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 },
  },
});
