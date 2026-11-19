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
  withDelay,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN } from "react-native-worklets";

import { useReducedMotionPreference } from "@/shared/useReducedMotionPreference";

import { RingBackdrop, RingFloor, RingSparkle } from "./RingLight";
import {
  BLEND_S,
  BOOKED_MS,
  filmBooked,
  filmBurst,
  filmCarat,
  filmCaratStep,
  filmOpen,
  filmShot,
  filmSteps,
  filmYaw,
  IDLE_MS,
  LOOP,
  METAL_STEPS,
  RETURN_MS,
  STONE_STEPS,
} from "./ringMotion";
import { RingPanel } from "./RingPanel";
import { BG_BOTTOM, BG_TOP, CARAT_MODEL, METALS, STONES } from "./ringScene";
import { RingStage3D } from "./RingStage3D";

/** The options panel's height (title, two pickers, slider, button) and the header row's. */
const PANEL_H = 262;
const HEADER_H = 42;
/** The stage window fades into the paper over its last FADE pt (and a short TOP_FADE at the top). */
const FADE = 40;
const TOP_FADE = 14;

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

const LID_PT = 110;
const YAW_PER_PT = 0.008;
const YAW_LIMIT = 0.7;
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
/** The hinge's snap: stiff, a little under-damped (about 5 % past, one small dip). */
const SNAP = { damping: 12, stiffness: 340, mass: 0.6 } as const;

/** Publishes the film's clock anchor for the Filament thread (only when it changes): [1 while the film plays, the UI
 * frame time (ms) of its t = 0, metal home, stone home, carat home]. */
function syncAnchor(anchor: SharedValue<number[]>, kind: number, start: number, mh: number, sh: number, ch: number): void {
  "worklet";
  const a = anchor.get();
  if (a[0] !== kind || a[1] !== start || a[2] !== mh || a[3] !== sh || a[4] !== ch) anchor.set([kind, start, mh, sh, ch]);
}

function snapTap(): void {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid);
}

function landTap(): void {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft);
}

function bookTap(): void {
  void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
}

export type RingBoxRevealProps = {
  autoPlay?: boolean;
};

export function RingBoxReveal({ autoPlay = true }: RingBoxRevealProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = !reduced && autoPlay;

  const t = useSharedValue(0);
  const open = useSharedValue(reduced ? 1 : 0);
  const shot = useSharedValue(reduced ? 1 : 0);
  const yaw = useSharedValue(0);
  const burst = useSharedValue(0);
  const mFrom = useSharedValue(0);
  const mTo = useSharedValue(0);
  const mProg = useSharedValue(0);
  const sFrom = useSharedValue(0);
  const sTo = useSharedValue(0);
  const sProg = useSharedValue(0);
  const carat = useSharedValue(CARAT_MODEL);
  /** The buyer's own choices: the film plays its tour from them and comes back to them. */
  const metalHome = useSharedValue(0);
  const stoneHome = useSharedValue(0);
  const caratHome = useSharedValue(CARAT_MODEL);
  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const loopStart = useSharedValue(-1);
  /** The film's clock for the Filament thread, which computes the film from its own frame time (see syncAnchor). */
  const anchor = useSharedValue<number[]>([0, -1, 0, 0, CARAT_MODEL]);
  const ready = useSharedValue(false);
  const lastActivity = useSharedValue(-1);
  const dragging = useSharedValue(false);
  const busy = useSharedValue(false);
  const kind = useSharedValue(KIND_NONE);
  const from = useSharedValue(0);
  const yawFrom = useSharedValue(0);

  const [metal, setMetal] = useState(0);
  const [stone, setStone] = useState(0);
  const [caratUi, setCaratUi] = useState(CARAT_MODEL);
  const [booked, setBooked] = useState(false);
  const bookTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(
    () => () => {
      if (bookTimer.current != null) clearTimeout(bookTimer.current);
    },
    [],
  );

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
        syncAnchor(anchor, 0, -1, metalHome.get(), stoneHome.get(), caratHome.get());
        return;
      }
      if (loopStart.get() < 0) loopStart.set(now);
      syncAnchor(anchor, 1, loopStart.get(), metalHome.get(), stoneHome.get(), caratHome.get());
      const tt = ((now - loopStart.get()) / 1000) % LOOP;
      t.set(tt);
      open.set(filmOpen(tt));
      shot.set(filmShot(tt));
      yaw.set(filmYaw(tt));
      burst.set(filmBurst(tt));
      const f = [0, 0, 0];
      filmSteps(tt, METAL_STEPS, f);
      const mh = metalHome.get();
      mFrom.set((mh + f[0]) % METALS.length);
      mTo.set((mh + f[1]) % METALS.length);
      mProg.set(f[2]);
      filmSteps(tt, STONE_STEPS, f);
      const sh = stoneHome.get();
      sFrom.set((sh + f[0]) % STONES.length);
      sTo.set((sh + f[1]) % STONES.length);
      sProg.set(f[2]);
      carat.set(filmCarat(tt, caratHome.get()));
      return;
    }
    syncAnchor(anchor, 0, -1, metalHome.get(), stoneHome.get(), caratHome.get());
    if (md !== MODE_MANUAL) return;
    const blending = (mProg.get() > 0 && mProg.get() < 1) || (sProg.get() > 0 && sProg.get() < 1);
    if (lastActivity.get() < 0 || dragging.get() || busy.get() || blending) {
      lastActivity.set(now);
      return;
    }
    if (!film || now - lastActivity.get() < IDLE_MS) return;
    // 2 s after the last touch: the camera pulls back, the lid closes, then the film starts again from the buyer's
    // choices
    mode.set(MODE_RETURN);
    const timing = { duration: RETURN_MS, easing: EASE_IN_OUT };
    shot.set(withTiming(0, { duration: RETURN_MS * 0.7, easing: EASE_IN_OUT }));
    yaw.set(withTiming(0, timing));
    burst.set(withTiming(0, { duration: 300 }));
    carat.set(withTiming(caratHome.get(), { duration: 400 }));
    const mh = metalHome.get();
    if (mTo.get() !== mh || mProg.get() < 1) {
      mFrom.set(mProg.get() >= 0.5 ? mTo.get() : mFrom.get());
      mTo.set(mh);
      mProg.set(0);
      mProg.set(withTiming(1, { duration: BLEND_S * 1000 }));
    }
    const sh = stoneHome.get();
    if (sTo.get() !== sh || sProg.get() < 1) {
      sFrom.set(sProg.get() >= 0.5 ? sTo.get() : sFrom.get());
      sTo.set(sh);
      sProg.set(0);
      sProg.set(withTiming(1, { duration: BLEND_S * 1000 }));
    }
    open.set(
      withDelay(
        RETURN_MS * 0.3,
        withTiming(0, { duration: RETURN_MS * 0.7, easing: EASE_IN_OUT }, (finished) => {
          "worklet";
          if (!finished) return;
          t.set(0);
          loopStart.set(-1);
          mode.set(MODE_AUTO);
        }),
      ),
    );
  });

  // the native pickers follow the film's steps (each switches as its blend starts)
  useAnimatedReaction(
    () => (mProg.get() <= 0.001 ? Math.round(mFrom.get()) : Math.round(mTo.get())),
    (cur, prev) => {
      if (prev == null || cur === prev) return;
      scheduleOnRN(setMetal, cur);
    },
  );
  useAnimatedReaction(
    () => (sProg.get() <= 0.001 ? Math.round(sFrom.get()) : Math.round(sTo.get())),
    (cur, prev) => {
      if (prev == null || cur === prev) return;
      scheduleOnRN(setStone, cur);
    },
  );
  // the native slider steps with the film's carat beats
  useAnimatedReaction(
    () => (mode.get() === MODE_AUTO ? filmCaratStep(t.get(), caratHome.get()) : -1),
    (cur, prev) => {
      if (prev == null || cur === prev || cur < 0) return;
      scheduleOnRN(setCaratUi, cur);
    },
  );
  // the film presses the booking button
  useAnimatedReaction(
    () => mode.get() === MODE_AUTO && filmBooked(t.get()),
    (cur, prev) => {
      if (prev == null || cur === prev) return;
      scheduleOnRN(setBooked, cur);
      if (cur && !reduced) scheduleOnRN(bookTap);
    },
  );

  // haptics: the snap, and the lid landing shut
  useAnimatedReaction(
    () => open.get(),
    (cur, prev) => {
      if (prev == null || reduced) return;
      if (prev < 0.5 && cur >= 0.5) scheduleOnRN(snapTap);
      if (prev > 0.01 && cur <= 0.01) scheduleOnRN(landTap);
    },
  );

  const pause = useCallback(() => {
    "worklet";
    if (mode.get() === MODE_RETURN) {
      cancelAnimation(open);
      cancelAnimation(shot);
      cancelAnimation(yaw);
    }
    mode.set(MODE_MANUAL);
    // the Filament thread stops computing the film at once and shows the shared values from here on
    syncAnchor(anchor, 0, -1, metalHome.get(), stoneHome.get(), caratHome.get());
    lastActivity.set(-1);
  }, [anchor, caratHome, lastActivity, metalHome, mode, open, shot, stoneHome, yaw]);

  const snapOpen = useCallback(() => {
    "worklet";
    busy.set(true);
    if (reduced) {
      open.set(withTiming(1, { duration: 200 }, () => {
        "worklet";
        busy.set(false);
      }));
      shot.set(withTiming(1, { duration: 200 }));
      return;
    }
    open.set(
      withSpring(1, SNAP, () => {
        "worklet";
        busy.set(false);
      }),
    );
    burst.set(1);
    burst.set(withDelay(150, withTiming(0, { duration: 1300, easing: Easing.out(Easing.quad) })));
    shot.set(withDelay(350, withTiming(1, { duration: 1200, easing: EASE_IN_OUT })));
  }, [burst, busy, open, reduced, shot]);

  const close = useCallback(() => {
    "worklet";
    busy.set(true);
    shot.set(withTiming(0, { duration: 700, easing: EASE_IN_OUT }));
    yaw.set(withTiming(0, { duration: 600, easing: EASE_IN_OUT }));
    open.set(
      withDelay(
        250,
        withTiming(0, { duration: reduced ? 200 : 650, easing: Easing.in(Easing.cubic) }, () => {
          "worklet";
          busy.set(false);
        }),
      ),
    );
  }, [busy, open, reduced, shot, yaw]);

  const pan = Gesture.Pan()
    .minDistance(6)
    .onStart(() => {
      "worklet";
      pause();
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
        kind.set(sideways && from.get() > 0.6 ? KIND_TURN : KIND_LID);
        if (kind.get() === KIND_LID) {
          cancelAnimation(open);
          busy.set(false);
        } else {
          cancelAnimation(yaw);
        }
      }
      if (kind.get() === KIND_LID) {
        const v = from.get() - e.translationY / LID_PT;
        open.set(v < 0 ? 0 : v > 1.08 ? 1.08 : v);
      } else {
        let v = yawFrom.get() + e.translationX * YAW_PER_PT;
        const over = Math.abs(v) - YAW_LIMIT;
        if (over > 0) v = Math.sign(v) * (YAW_LIMIT + over / (1 + over * 3));
        yaw.set(v);
      }
    })
    .onEnd((e) => {
      "worklet";
      dragging.set(false);
      if (kind.get() === KIND_LID) {
        const projected = open.get() - (e.velocityY / LID_PT) * 0.2;
        if (projected > 0.45) snapOpen();
        else close();
      } else if (kind.get() === KIND_TURN) {
        yaw.set(withSpring(0, { damping: 10, stiffness: 40, velocity: e.velocityX * YAW_PER_PT }));
      }
    })
    .onFinalize(() => {
      "worklet";
      dragging.set(false);
    });

  // a tap opens the box; on the open box it goes in to the stone and back out
  const tap = Gesture.Tap()
    .maxDuration(300)
    .onEnd((_e, success) => {
      "worklet";
      if (!success) return;
      pause();
      if (open.get() < 0.5) {
        snapOpen();
        return;
      }
      const target = shot.get() < 1.5 ? 2 : 1;
      shot.set(withTiming(target, { duration: reduced ? 200 : 1000, easing: EASE_IN_OUT }));
    });

  const gesture = Gesture.Race(pan, tap);

  const pickMetal = useCallback(
    (i: number) => {
      if (i === metal) return;
      pause();
      void Haptics.selectionAsync();
      metalHome.set(i);
      const current = mProg.get() >= 0.5 ? mTo.get() : mFrom.get();
      setMetal(i);
      cancelAnimation(mProg);
      mFrom.set(current);
      mTo.set(i);
      mProg.set(0);
      mProg.set(withTiming(1, { duration: reduced ? 200 : BLEND_S * 1000 }));
    },
    [metal, metalHome, mFrom, mProg, mTo, pause, reduced, setMetal],
  );

  const pickStone = useCallback(
    (i: number) => {
      if (i === stone) return;
      pause();
      void Haptics.selectionAsync();
      stoneHome.set(i);
      const current = sProg.get() >= 0.5 ? sTo.get() : sFrom.get();
      setStone(i);
      cancelAnimation(sProg);
      sFrom.set(current);
      sTo.set(i);
      sProg.set(0);
      sProg.set(withTiming(1, { duration: reduced ? 200 : BLEND_S * 1000 }));
    },
    [pause, reduced, sFrom, sProg, sTo, setStone, stone, stoneHome],
  );

  const pickCarat = useCallback(
    (ct: number) => {
      const v = Math.round(ct * 4) / 4;
      if (v === caratUi) return;
      pause();
      void Haptics.selectionAsync();
      caratHome.set(v);
      setCaratUi(v);
      carat.set(reduced ? v : withSpring(v, { damping: 14, stiffness: 180 }));
    },
    [carat, caratHome, caratUi, pause, reduced, setCaratUi],
  );

  const book = () => {
    pause();
    bookTap();
    setBooked(true);
    if (bookTimer.current != null) clearTimeout(bookTimer.current);
    bookTimer.current = setTimeout(() => setBooked(false), BOOKED_MS);
  };

  const onReady = useCallback(() => {
    ready.set(true);
  }, [ready]);

  // the options take the bottom; the 3D stage is the window between the header row and the panel
  const panelTop = height - (insets.bottom + 12 + PANEL_H);
  const stageTop = insets.top + HEADER_H;
  const stageH = Math.max(120, panelTop - 8 - stageTop);
  const stageBottom = stageTop + stageH;
  const light = { burst, carat, height: stageH, open, paperH: height, paperTop: -stageTop, sFrom, shot, sProg, sTo, t, width, yaw };

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <LinearGradient colors={[BG_TOP, BG_BOTTOM]} pointerEvents="none" style={StyleSheet.absoluteFill} />
      <View pointerEvents="none" style={[styles.stage, { top: stageTop, height: stageH }]}>
        <RingBackdrop {...light} />
        <RingStage3D
          anchor={anchor}
          carat={carat}
          height={stageH}
          mFrom={mFrom}
          mProg={mProg}
          mTo={mTo}
          onReady={onReady}
          open={open}
          sFrom={sFrom}
          shot={shot}
          sProg={sProg}
          sTo={sTo}
          t={t}
          width={width}
          yaw={yaw}
        />
        <RingFloor {...light} />
        <RingSparkle {...light} />
        {/* the window's edges fade into the paper, so a close shot never runs behind the type */}
        <LinearGradient colors={[paperAt(stageTop, height, 1), paperAt(stageTop + TOP_FADE, height, 0)]} style={[styles.fadeTop, { height: TOP_FADE }]} />
        <LinearGradient colors={[paperAt(stageBottom - FADE, height, 0), paperAt(stageBottom, height, 1)]} style={[styles.fadeBottom, { height: FADE }]} />
      </View>
      <GestureDetector gesture={gesture}>
        <View
          accessibilityHint="Tap or flick up to open the box, tap again to look at the stone, drag down to close it, drag sideways to turn it"
          accessibilityLabel="Ring box"
          style={[styles.stageArea, { top: stageTop, height: stageH }]}
        />
      </GestureDetector>
      <RingPanel
        booked={booked}
        bottom={insets.bottom}
        carat={caratUi}
        metal={metal}
        onBook={book}
        onCarat={pickCarat}
        onMetal={pickMetal}
        onStone={pickStone}
        stone={stone}
        top={insets.top}
        width={width}
      />
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
