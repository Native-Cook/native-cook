import * as Haptics from "expo-haptics";
import { LinearGradient } from "expo-linear-gradient";
import { StatusBar } from "expo-status-bar";
import { useCallback, useMemo } from "react";
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
  withDelay,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN } from "react-native-worklets";

import { useReducedMotionPreference } from "@/shared/useReducedMotionPreference";

import {
  capOnScreen,
  deskGhost,
  faderFromDrag,
  filmPose,
  filmPreset,
  GLIDE,
  leanAt,
  LOOP,
  MUTE_MOVE,
  muteOnScreen,
  POSE,
  PRESETS,
  SNAP_V,
  SNAP_ZONE,
  STAGGER,
} from "./mixerMotion";
import { GUTTER, MixerPanel, PANEL_H, PANEL_R } from "./MixerPanel";
import { APP, BACKDROP, CHANNELS, INK, MUTE } from "./mixerScene";
import { MixerStage3D } from "./MixerStage3D";

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

/** This long after the last touch the desk recalls Intro, and RETURN_MS later the film restarts. */
const IDLE_MS = 2000;
const RETURN_MS = 1500;
const STALL_MS = 100;
const FRAME_MS = 1000 / 60;
const ANCHOR_BEAT = 30;
/** A finger finds a fader within this distance (pt) of its track, and a mute button within MUTE_REACH. */
const FADER_REACH = 34;
const MUTE_REACH = 30;
/** A released fader: how far ahead its speed carries it (s), the spring it lands on, and the detent's catch. */
const FLICK_S = 0.08;
const FADER_SPRING = { mass: 1, stiffness: 240, damping: 26 };
const SNAP_CATCH = SNAP_ZONE * 1.3;
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
const DOT = 44;
/** Reduced motion's still: the Interview mix. */
const REDUCED_PRESET = 1;

function tick(): void {
  void Haptics.selectionAsync();
}

function press(): void {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

function syncAnchor(anchor: SharedValue<number[]>, playing: number, start: number): void {
  "worklet";
  const a = anchor.get();
  if (a[0] !== playing || a[1] !== start) anchor.set([playing, start]);
}

export type MixingDeskFaderProps = {
  autoPlay?: boolean;
  /** A finger moved a fader (channel, 0..1 of its travel; 0.75 is 0 dB). */
  onFader?: (channel: number, value: number) => void;
  /** A finger muted or unmuted a channel. */
  onMute?: (channel: number, muted: boolean) => void;
};

export function MixingDeskFader({ autoPlay = true, onFader, onMute }: MixingDeskFaderProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = !reduced && autoPlay;
  const initialPose = useMemo(() => [...PRESETS[reduced ? REDUCED_PRESET : 0]], [reduced]);

  const t = useSharedValue(0);
  const anchor = useSharedValue<number[]>([0, -1]);
  const pose = useSharedValue<number[]>(initialPose);
  const lean = useSharedValue(0);
  const preset = useSharedValue(reduced ? REDUCED_PRESET : 0);
  const f0 = useSharedValue(initialPose[0]);
  const f1 = useSharedValue(initialPose[1]);
  const f2 = useSharedValue(initialPose[2]);
  const f3 = useSharedValue(initialPose[3]);
  const k0 = useSharedValue(initialPose[4]);
  const k1 = useSharedValue(initialPose[5]);
  const k2 = useSharedValue(initialPose[6]);
  const k3 = useSharedValue(initialPose[7]);
  const m0 = useSharedValue(initialPose[8]);
  const m1 = useSharedValue(initialPose[9]);
  const m2 = useSharedValue(initialPose[10]);
  const m3 = useSharedValue(initialPose[11]);
  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const loopStart = useSharedValue(-1);
  const ready = useSharedValue(false);
  const shown = useSharedValue(0);
  const nowMs = useSharedValue(-1);
  const beat = useSharedValue(0);
  const lastTouch = useSharedValue(-1);
  const returnAt = useSharedValue(-1);
  const dragging = useSharedValue(false);
  /** The fader a finger holds (-1: none), its value at the touch, its track on screen (dx, dy for the whole travel). */
  const grab = useSharedValue(-1);
  const grabFrom = useSharedValue(0);
  const axisX = useSharedValue(0);
  const axisY = useSharedValue(1);
  const inDetent = useSharedValue(false);

  const stageH = Math.max(260, height - PANEL_H - insets.bottom + PANEL_R);

  /** Fader c's shared value (the hooks above are fixed, so a switch keeps this a plain worklet). */
  const faderSV = useCallback(
    (c: number): SharedValue<number> => {
      "worklet";
      return c === 0 ? f0 : c === 1 ? f1 : c === 2 ? f2 : f3;
    },
    [f0, f1, f2, f3],
  );
  const knobSV = useCallback(
    (c: number): SharedValue<number> => {
      "worklet";
      return c === 0 ? k0 : c === 1 ? k1 : c === 2 ? k2 : k3;
    },
    [k0, k1, k2, k3],
  );
  const muteSV = useCallback(
    (c: number): SharedValue<number> => {
      "worklet";
      return c === 0 ? m0 : c === 1 ? m1 : c === 2 ? m2 : m3;
    },
    [m0, m1, m2, m3],
  );

  /** Glide every fader, knob and mute to preset p, as a recall does. */
  const recall = useCallback(
    (p: number) => {
      "worklet";
      const target = PRESETS[p];
      for (let c = 0; c < CHANNELS; c++) {
        const f = faderSV(c);
        const k = knobSV(c);
        const m = muteSV(c);
        cancelAnimation(f);
        cancelAnimation(k);
        cancelAnimation(m);
        if (reduced) {
          f.set(target[c]);
          k.set(target[4 + c]);
          m.set(target[8 + c]);
          continue;
        }
        const glide = { duration: GLIDE * 1000, easing: EASE_IN_OUT };
        f.set(withDelay(c * STAGGER * 1000, withTiming(target[c], glide)));
        k.set(withDelay(c * STAGGER * 1000, withTiming(target[4 + c], glide)));
        m.set(withDelay(100, withTiming(target[8 + c], { duration: MUTE_MOVE * 1000 })));
      }
    },
    [faderSV, knobSV, muteSV, reduced],
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
      const p = new Array<number>(POSE);
      filmPose(el, p);
      pose.set(p);
      lean.set(leanAt(el));
      const pr = filmPreset(el);
      if (preset.get() !== pr) preset.set(pr);
      return;
    }
    syncAnchor(anchor, 0, -1);
    // the meters and the REC light keep their own time
    if (!reduced) t.set((t.get() + Math.min(0.05, gap / 1000)) % LOOP);
    const cur = pose.get();
    const next = [f0.get(), f1.get(), f2.get(), f3.get(), k0.get(), k1.get(), k2.get(), k3.get(), m0.get(), m1.get(), m2.get(), m3.get()];
    let moved = false;
    for (let i = 0; i < POSE; i++) {
      if (cur[i] !== next[i]) moved = true;
    }
    if (moved) pose.set(next);
    if (md === MODE_RETURN) {
      if (now - returnAt.get() >= RETURN_MS) {
        t.set(0);
        loopStart.set(-1);
        mode.set(MODE_AUTO);
      }
      return;
    }
    if (lastTouch.get() < 0 || dragging.get()) {
      lastTouch.set(now);
      return;
    }
    if (!film || now - lastTouch.get() < IDLE_MS) return;
    // 2 s after the last touch: Intro is recalled, the camera comes home, and the film restarts from t = 0
    mode.set(MODE_RETURN);
    returnAt.set(now);
    preset.set(0);
    recall(0);
    lean.set(withTiming(0, { duration: RETURN_MS - 100, easing: EASE_IN_OUT }));
  });

  /** A touch takes over from wherever the film (or the return) is: the desk keeps the pose it had. */
  const pause = useCallback(() => {
    "worklet";
    const md = mode.get();
    if (md === MODE_AUTO) {
      const p = new Array<number>(POSE);
      filmPose(t.get(), p);
      for (let c = 0; c < CHANNELS; c++) {
        faderSV(c).set(p[c]);
        knobSV(c).set(p[4 + c]);
        muteSV(c).set(Math.round(p[8 + c]));
      }
    }
    if (md === MODE_RETURN) cancelAnimation(lean);
    mode.set(MODE_MANUAL);
    syncAnchor(anchor, 0, -1);
    lastTouch.set(-1);
  }, [anchor, faderSV, knobSV, lastTouch, lean, mode, muteSV, t]);

  const userPreset = useCallback(
    (p: number) => {
      "worklet";
      pause();
      preset.set(p);
      recall(p);
    },
    [pause, preset, recall],
  );
  const onPreset = useCallback(
    (p: number) => {
      tick();
      runOnUI(userPreset)(p);
    },
    [userPreset],
  );

  const reportFader = useCallback((c: number, v: number) => onFader?.(c, v), [onFader]);
  const reportMute = useCallback((c: number, m: boolean) => onMute?.(c, m), [onMute]);

  // drag a fader: the nearest track to the finger, moved along its own direction on screen
  const pan = Gesture.Pan()
    .activeOffsetY([-6, 6])
    .failOffsetX([-40, 40])
    .onBegin((e) => {
      "worklet";
      const k = lean.get();
      let best = -1;
      let bestD = FADER_REACH;
      const a = [0, 0];
      const b = [0, 0];
      const p = pose.get();
      for (let c = 0; c < CHANNELS; c++) {
        capOnScreen(c, 0, k, width, stageH, a);
        capOnScreen(c, 1, k, width, stageH, b);
        const dx = b[0] - a[0];
        const dy = b[1] - a[1];
        const l2 = dx * dx + dy * dy;
        const u = Math.max(-0.1, Math.min(1.1, ((e.x - a[0]) * dx + (e.y - a[1]) * dy) / l2));
        const px = a[0] + dx * u - e.x;
        const py = a[1] + dy * u - e.y;
        const d = Math.sqrt(px * px + py * py);
        if (d < bestD) {
          bestD = d;
          best = c;
          axisX.set(dx);
          axisY.set(dy);
        }
      }
      grab.set(best);
      if (best >= 0) grabFrom.set(p[best]);
    })
    .onStart(() => {
      "worklet";
      const c = grab.get();
      if (c < 0) return;
      pause();
      dragging.set(true);
      const f = faderSV(c);
      cancelAnimation(f);
      grabFrom.set(f.get());
      inDetent.set(Math.abs(f.get() - SNAP_V) < SNAP_ZONE * 0.5);
    })
    .onUpdate((e) => {
      "worklet";
      const c = grab.get();
      if (c < 0 || !dragging.get()) return;
      const ax = axisX.get();
      const ay = axisY.get();
      const raw = grabFrom.get() + (e.translationX * ax + e.translationY * ay) / (ax * ax + ay * ay);
      const v = faderFromDrag(raw);
      faderSV(c).set(v);
      const caught = Math.abs(v - SNAP_V) < SNAP_ZONE * 0.5;
      if (caught && !inDetent.get()) scheduleOnRN(tick);
      inDetent.set(caught);
    })
    .onEnd((e) => {
      "worklet";
      const c = grab.get();
      if (c < 0 || !dragging.get()) return;
      const ax = axisX.get();
      const ay = axisY.get();
      const vel = (e.velocityX * ax + e.velocityY * ay) / (ax * ax + ay * ay);
      const f = faderSV(c);
      let to = Math.max(0, Math.min(1, f.get() + vel * FLICK_S));
      if (Math.abs(to - SNAP_V) < SNAP_CATCH) to = SNAP_V;
      f.set(reduced ? to : withSpring(to, { ...FADER_SPRING, velocity: vel }));
      scheduleOnRN(reportFader, c, to);
    })
    .onFinalize(() => {
      "worklet";
      dragging.set(false);
      grab.set(-1);
    });

  // tap a mute button: it sinks and its light goes out (or comes back)
  const tap = Gesture.Tap()
    .maxDistance(10)
    .onEnd((e) => {
      "worklet";
      const k = lean.get();
      const s = [0, 0];
      let best = -1;
      let bestD = MUTE_REACH;
      for (let c = 0; c < CHANNELS; c++) {
        muteOnScreen(c, k, width, stageH, s);
        const d = Math.sqrt((s[0] - e.x) * (s[0] - e.x) + (s[1] - e.y) * (s[1] - e.y));
        if (d < bestD) {
          bestD = d;
          best = c;
        }
      }
      if (best < 0) return;
      pause();
      const m = muteSV(best);
      const to = m.get() > 0.5 ? 0 : 1;
      m.set(reduced ? to : withTiming(to, { duration: MUTE_MOVE * 1000 }));
      scheduleOnRN(press);
      scheduleOnRN(reportMute, best, to === 1);
    });

  const gesture = Gesture.Race(pan, tap);

  const onReady = useCallback(() => {
    ready.set(true);
    shown.set(withTiming(1, { duration: 320 }));
  }, [ready, shown]);

  const stage = useAnimatedStyle(() => ({ opacity: shown.get() }));
  const filming = useDerivedValue<number>(() => (mode.get() === MODE_AUTO ? 1 : 0));

  // the film's finger on the desk, on the cap it rides or the button it taps
  const ghost = useAnimatedStyle(() => {
    const g = [0, 0, 0, 0];
    deskGhost(t.get(), g);
    const s = [0, 0];
    const c = g[2];
    if (g[3] > 0.5) muteOnScreen(c, lean.get(), width, stageH, s);
    else capOnScreen(c, pose.get()[c], lean.get(), width, stageH, s);
    return {
      opacity: mode.get() === MODE_AUTO ? g[0] * 0.9 : 0,
      transform: [{ translateX: s[0] - DOT / 2 }, { translateY: s[1] - DOT / 2 }, { scale: 1 - 0.16 * g[1] }],
    };
  });

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={gesture}>
        <View accessibilityHint="Drag a fader up or down; tap a mute button" accessibilityLabel="Mixing desk" collapsable={false} style={[styles.stage, { height: stageH }]}>
          <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, stage]}>
            <MixerStage3D anchor={anchor} height={stageH} initialPose={initialPose} lean={lean} onReady={onReady} pose={pose} still={reduced} width={width} />
          </Animated.View>
          <Animated.View pointerEvents="none" style={[styles.dot, ghost]} />
          <LinearGradient colors={[BACKDROP, "rgba(36,33,30,0.6)", "rgba(36,33,30,0)"]} locations={[0, 0.55, 1]} pointerEvents="none" style={[styles.topShade, { height: insets.top + 64 }]} />
        </View>
      </GestureDetector>
      <View pointerEvents="none" style={[styles.header, { top: insets.top + 10, left: GUTTER, right: GUTTER }]}>
        <Text style={styles.app}>{APP}</Text>
        <Text style={styles.sub}>Studio B · Live</Text>
      </View>
      <MixerPanel bottom={insets.bottom} filming={filming} onPreset={onPreset} pose={pose} preset={preset} t={t} width={width} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BACKDROP },
  stage: { position: "absolute", left: 0, right: 0, top: 0, overflow: "hidden" },
  dot: { position: "absolute", left: 0, top: 0, width: DOT, height: DOT, borderRadius: DOT / 2, backgroundColor: "rgba(243,240,234,0.18)", borderWidth: 1.5, borderColor: "rgba(243,240,234,0.6)" },
  topShade: { position: "absolute", left: 0, right: 0, top: 0 },
  header: { position: "absolute", flexDirection: "row", justifyContent: "space-between", alignItems: "baseline" },
  app: { fontSize: 17, lineHeight: 22, fontWeight: "600", color: INK },
  sub: { fontSize: 15, lineHeight: 20, color: MUTE },
});
