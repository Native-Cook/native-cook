import * as Haptics from "expo-haptics";
import { LinearGradient } from "expo-linear-gradient";
import { StatusBar } from "expo-status-bar";
import { useCallback, useEffect, useRef, useState } from "react";
import { StyleSheet, useWindowDimensions, View } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import {
  cancelAnimation,
  Easing,
  type SharedValue,
  useAnimatedReaction,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN } from "react-native-worklets";

import { useReducedMotionPreference } from "@/shared/useReducedMotionPreference";

import { WatchBackdrop, WatchGlint } from "./WatchLight";
import { DIAL_BLEND, filmDial, filmMacro, filmOpen, filmReserved, filmYaw, IDLE_MS, LOOP, RESERVED_MS, RETURN_MS } from "./watchMotion";
import { WatchPanel } from "./WatchPanel";
import { BG_BOTTOM, BG_TOP, DIALS } from "./watchScene";
import { WatchStage3D } from "./WatchStage3D";

/** The product panel's height (title, specs, picker, button) and the header row's. */
const PANEL_H = 236;
const HEADER_H = 44;
/** The stage window fades into the paper over its last FADE pt (and a short TOP_FADE at the top). */
const FADE = 48;
const TOP_FADE = 16;

function hexRgb(hex: string): number[] {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

/** The paper's colour at screen y (the screen gradient runs BG_TOP to BG_BOTTOM), with an alpha. */
function paperAt(y: number, h: number, a: number): string {
  const k = Math.min(1, Math.max(0, y / h));
  const t = hexRgb(BG_TOP);
  const b = hexRgb(BG_BOTTOM);
  const c = [0, 1, 2].map((i) => Math.round(t[i] + (b[i] - t[i]) * k));
  return `rgba(${c[0]},${c[1]},${c[2]},${a})`;
}

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

const KIND_NONE = -1;
const KIND_LID = 0;
const KIND_TURN = 1;

/** Points of vertical drag per unit of `open` (the lid is one unit, the pillow another). */
const LID_PT = 150;
const YAW_PER_PT = 0.009;
const YAW_LIMIT = 0.8;
const DETENT = (12 * Math.PI) / 180;
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
/** A damped hinge: quick to start, a long soft settle. */
const EASE_HINGE = Easing.bezier(0.25, 0.8, 0.3, 1);

function latch(): void {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

function soft(): void {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft);
}

function tick(): void {
  void Haptics.selectionAsync();
}

/** Publishes the clocks for the Filament thread (only when they change): [1 while the film plays, the UI frame time
 * (ms) of its t = 0, dial home, the second hand's seconds at the UI frame time (ms) in the last slot (-1: not set)]. */
function syncAnchor(anchor: SharedValue<number[]>, kind: number, start: number, home: number, sAt: number, sMs: number): void {
  "worklet";
  const a = anchor.get();
  if (a[0] !== kind || a[1] !== start || a[2] !== home || a[3] !== sAt || a[4] !== sMs) anchor.set([kind, start, home, sAt, sMs]);
}

function reserveTap(): void {
  void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
}

export type WatchCaseRevealProps = {
  autoPlay?: boolean;
};

export function WatchCaseReveal({ autoPlay = true }: WatchCaseRevealProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = !reduced && autoPlay;

  const t = useSharedValue(0);
  const open = useSharedValue(reduced ? 2 : 0);
  const yaw = useSharedValue(0);
  const macro = useSharedValue(0);
  const dFrom = useSharedValue(0);
  const dTo = useSharedValue(0);
  const dProg = useSharedValue(0);
  const home = useSharedValue(0);
  const secs = useSharedValue(0);
  /** Outside the film the second hand runs on from secsAt at the frame time secsMs (-1 until the film is left). */
  const secsAt = useSharedValue(0);
  const secsMs = useSharedValue(-1);
  /** The clocks for the Filament thread, which computes the film from its own frame time (see syncAnchor). */
  const anchor = useSharedValue<number[]>([0, -1, 0, 0, -1]);
  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const loopStart = useSharedValue(-1);
  const ready = useSharedValue(false);
  const lastActivity = useSharedValue(-1);
  const lastFrame = useSharedValue(-1);
  const dragging = useSharedValue(false);
  const busy = useSharedValue(false);
  const kind = useSharedValue(KIND_NONE);
  const from = useSharedValue(0);
  const yawFrom = useSharedValue(0);

  const [dial, setDial] = useState(0);
  const [reserved, setReserved] = useState(false);
  const reserveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(
    () => () => {
      if (reserveTimer.current != null) clearTimeout(reserveTimer.current);
    },
    [],
  );

  useFrameCallback((frame) => {
    "worklet";
    const now = frame.timestamp;
    const prev = lastFrame.get();
    lastFrame.set(now);
    const md = mode.get();
    if (md === MODE_AUTO) {
      if (!film) {
        mode.set(MODE_MANUAL);
        return;
      }
      secsMs.set(-1);
      if (!ready.get()) {
        t.set(0);
        syncAnchor(anchor, 0, -1, home.get(), 0, -1);
        return;
      }
      if (loopStart.get() < 0) loopStart.set(now);
      syncAnchor(anchor, 1, loopStart.get(), home.get(), 0, -1);
      const tt = ((now - loopStart.get()) / 1000) % LOOP;
      t.set(tt);
      open.set(filmOpen(tt));
      yaw.set(filmYaw(tt));
      macro.set(filmMacro(tt));
      const f = [0, 0, 0];
      filmDial(tt, f);
      const h = home.get();
      dFrom.set((h + f[0]) % DIALS.length);
      dTo.set((h + f[1]) % DIALS.length);
      dProg.set(f[2]);
      secs.set(tt);
      return;
    }
    // the second hand runs on from where the film left it, as a function of the frame time (the Filament thread runs
    // the same from the anchor)
    if (secsMs.get() < 0) {
      secsAt.set(secs.get());
      secsMs.set(prev < 0 ? now : prev);
    }
    secs.set(secsAt.get() + (now - secsMs.get()) / 1000);
    syncAnchor(anchor, 0, -1, home.get(), secsAt.get(), secsMs.get());
    if (md !== MODE_MANUAL) return;
    const blending = dProg.get() > 0 && dProg.get() < 1;
    if (lastActivity.get() < 0 || dragging.get() || busy.get() || blending) {
      lastActivity.set(now);
      return;
    }
    if (!film || now - lastActivity.get() < IDLE_MS) return;
    // 2 s after the last touch: the pillow sinks, the lid closes, then the film starts again in the chosen dial
    mode.set(MODE_RETURN);
    const timing = { duration: RETURN_MS, easing: EASE_IN_OUT };
    yaw.set(withTiming(0, timing));
    macro.set(withTiming(0, timing));
    const h = home.get();
    if (dTo.get() !== h || dProg.get() < 1) {
      dFrom.set(dProg.get() >= 0.5 ? dTo.get() : dFrom.get());
      dTo.set(h);
      dProg.set(0);
      dProg.set(withTiming(1, { duration: DIAL_BLEND * 1000 }));
    }
    open.set(
      withTiming(0, timing, (finished) => {
        "worklet";
        if (!finished) return;
        t.set(0);
        secs.set(0);
        loopStart.set(-1);
        mode.set(MODE_AUTO);
      }),
    );
  });

  // the native picker shows the dial being put on (it switches as a blend starts)
  useAnimatedReaction(
    () => (dProg.get() <= 0.001 ? Math.round(dFrom.get()) : Math.round(dTo.get())),
    (cur, prev) => {
      if (prev == null || cur === prev) return;
      scheduleOnRN(setDial, cur);
    },
  );

  // the film presses the reserve button
  useAnimatedReaction(
    () => mode.get() === MODE_AUTO && filmReserved(t.get()),
    (cur, prev) => {
      if (prev == null || cur === prev) return;
      scheduleOnRN(setReserved, cur);
      if (cur && !reduced) scheduleOnRN(reserveTap);
    },
  );

  // haptics: the clasp, the lid at rest, the pillow at the top, the lid landing shut
  useAnimatedReaction(
    () => open.get(),
    (cur, prev) => {
      if (prev == null || reduced) return;
      if (prev < 0.015 && cur >= 0.015) scheduleOnRN(latch);
      if (prev < 0.97 && cur >= 0.97) scheduleOnRN(soft);
      if (prev < 1.95 && cur >= 1.95) scheduleOnRN(latch);
      if (prev > 0.02 && cur <= 0.02) scheduleOnRN(soft);
    },
  );
  // a light detent every 12 deg while a finger turns the watch
  useAnimatedReaction(
    () => Math.floor(yaw.get() / DETENT),
    (cur, prev) => {
      if (prev == null || cur === prev) return;
      if (dragging.get() && kind.get() === KIND_TURN) scheduleOnRN(tick);
    },
  );

  const pause = useCallback(() => {
    "worklet";
    if (mode.get() === MODE_RETURN) {
      cancelAnimation(open);
      cancelAnimation(yaw);
      cancelAnimation(macro);
    }
    mode.set(MODE_MANUAL);
    // the Filament thread stops computing the film at once and shows the shared values from here on
    syncAnchor(anchor, 0, -1, home.get(), secsAt.get(), secsMs.get());
    lastActivity.set(-1);
  }, [anchor, home, lastActivity, macro, mode, open, secsAt, secsMs, yaw]);

  const animateOpen = useCallback(
    (target: number) => {
      "worklet";
      busy.set(true);
      const ms = reduced ? 220 : Math.max(500, Math.abs(target - open.get()) * 1150);
      open.set(
        withTiming(target, { duration: ms, easing: target > open.get() ? EASE_HINGE : EASE_IN_OUT }, () => {
          "worklet";
          busy.set(false);
        }),
      );
    },
    [busy, open, reduced],
  );

  const pan = Gesture.Pan()
    .minDistance(6)
    .onStart(() => {
      "worklet";
      pause();
      cancelAnimation(open);
      busy.set(false);
      dragging.set(true);
      kind.set(KIND_NONE);
      from.set(open.get());
      yawFrom.set(yaw.get());
    })
    .onUpdate((e) => {
      "worklet";
      if (kind.get() === KIND_NONE) {
        if (Math.abs(e.translationX) < 8 && Math.abs(e.translationY) < 8) return;
        const sideways = Math.abs(e.translationX) > Math.abs(e.translationY);
        kind.set(sideways && from.get() > 1.5 ? KIND_TURN : KIND_LID);
        if (kind.get() === KIND_TURN) cancelAnimation(yaw);
      }
      if (kind.get() === KIND_LID) {
        const v = from.get() - e.translationY / LID_PT;
        open.set(v < 0 ? 0 : v > 2 ? 2 : v);
      } else {
        // turn, rubber-banded past the limit
        let v = yawFrom.get() + e.translationX * YAW_PER_PT;
        const over = Math.abs(v) - YAW_LIMIT;
        if (over > 0) v = Math.sign(v) * (YAW_LIMIT + over / (1 + over * 2.5));
        yaw.set(v);
      }
    })
    .onEnd((e) => {
      "worklet";
      dragging.set(false);
      if (kind.get() === KIND_LID) {
        const projected = open.get() - (e.velocityY / LID_PT) * 0.25;
        animateOpen(projected > 0.6 ? 2 : 0);
      } else if (kind.get() === KIND_TURN) {
        yaw.set(withSpring(0, { damping: 11, stiffness: 55, velocity: e.velocityX * YAW_PER_PT }));
      }
    })
    .onFinalize(() => {
      "worklet";
      dragging.set(false);
    });

  const tap = Gesture.Tap()
    .maxDuration(300)
    .onEnd((_e, success) => {
      "worklet";
      if (!success) return;
      pause();
      if (open.get() < 0.5) animateOpen(2);
    });

  const gesture = Gesture.Race(pan, tap);

  const pickDial = useCallback(
    (i: number) => {
      pause();
      void Haptics.selectionAsync();
      home.set(i);
      const current = dProg.get() >= 0.5 ? dTo.get() : dFrom.get();
      if (current === i && (dProg.get() >= 1 || dProg.get() === 0)) return;
      cancelAnimation(dProg);
      dFrom.set(current);
      dTo.set(i);
      dProg.set(0);
      dProg.set(withTiming(1, { duration: reduced ? 200 : DIAL_BLEND * 1000 }));
      setDial(i);
    },
    [dFrom, dProg, dTo, home, pause, reduced, setDial],
  );

  const reserve = () => {
    pause();
    reserveTap();
    setReserved(true);
    if (reserveTimer.current != null) clearTimeout(reserveTimer.current);
    reserveTimer.current = setTimeout(() => setReserved(false), RESERVED_MS);
  };

  const onReady = useCallback(() => {
    ready.set(true);
  }, [ready]);

  // the controls take the bottom; the 3D stage is the window between the header row and the panel
  const panelTop = height - (insets.bottom + 12 + PANEL_H);
  const stageTop = insets.top + HEADER_H;
  const stageH = Math.max(120, panelTop - 8 - stageTop);
  const stageBottom = stageTop + stageH;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <LinearGradient colors={[BG_TOP, BG_BOTTOM]} pointerEvents="none" style={StyleSheet.absoluteFill} />
      <View pointerEvents="none" style={[styles.stage, { top: stageTop, height: stageH }]}>
        <WatchBackdrop height={stageH} macro={macro} open={open} t={t} width={width} yaw={yaw} />
        <WatchStage3D
          anchor={anchor}
          dFrom={dFrom}
          dProg={dProg}
          dTo={dTo}
          height={stageH}
          macro={macro}
          onReady={onReady}
          open={open}
          secs={secs}
          t={t}
          width={width}
          yaw={yaw}
        />
        <WatchGlint height={stageH} macro={macro} open={open} t={t} width={width} yaw={yaw} />
        {/* the window's edges fade into the paper, so a close shot never runs behind the type */}
        <LinearGradient colors={[paperAt(stageTop, height, 1), paperAt(stageTop + TOP_FADE, height, 0)]} style={[styles.fadeTop, { height: TOP_FADE }]} />
        <LinearGradient colors={[paperAt(stageBottom - FADE, height, 0), paperAt(stageBottom, height, 1)]} style={[styles.fadeBottom, { height: FADE }]} />
      </View>
      <GestureDetector gesture={gesture}>
        <View accessibilityHint="Drag up to open the case, sideways to turn the watch" accessibilityLabel="Watch case" style={[styles.stageArea, { top: stageTop, height: stageH }]} />
      </GestureDetector>
      <WatchPanel bottom={insets.bottom} dial={dial} onDial={pickDial} onReserve={reserve} open={open} reserved={reserved} top={insets.top} width={width} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BG_BOTTOM },
  stage: { position: "absolute", left: 0, right: 0, overflow: "hidden" },
  stageArea: { position: "absolute", left: 0, right: 0 },
  fadeTop: { position: "absolute", left: 0, right: 0, top: 0 },
  fadeBottom: { position: "absolute", left: 0, right: 0, bottom: 0 },
});
