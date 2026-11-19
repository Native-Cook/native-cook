import * as Haptics from "expo-haptics";
import { StatusBar } from "expo-status-bar";
import { useCallback, useMemo, useState } from "react";
import { StyleSheet, useWindowDimensions, View } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import {
  cancelAnimation,
  Easing,
  useAnimatedReaction,
  useFrameCallback,
  useSharedValue,
  withDecay,
  withDelay,
  withSequence,
  withTiming,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN } from "react-native-worklets";

import { useReducedMotionPreference } from "@/shared/useReducedMotionPreference";

import { CourierLocate } from "./CourierActions";
import { ACTION, ACTION_GAP, QUICK_REPLIES } from "./CourierActions.types";
import { CITY, ROUTE_LEN, ROUTE_STEP, ROUTE_X, ROUTE_Y } from "./courierCity";
import { toWorld } from "./courierDraw";
import { CourierMap } from "./CourierMap";
import {
  clamp01,
  easeInOutCubic,
  filmArrived,
  filmCamera,
  filmDoorPulse,
  filmToast,
  filmCall,
  filmTravelled,
  filmVan,
  framing,
  IDLE_MS,
  LOOP,
  RETURN_MS,
  S_MAX,
  S_MIN,
  screenCam,
} from "./courierMotion";
import { CourierFingers, CourierToast } from "./CourierOverlay";
import { CourierPanel, PANEL_H, PANEL_PAD, ROW_CY } from "./CourierPanel";
import { CourierPins } from "./CourierPins";

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
const LOCATE_MS = 800;
const LZ_MIN = Math.log2(S_MIN);
const LZ_MAX = Math.log2(S_MAX * 1.2);

function selection(): void {
  void Haptics.selectionAsync();
}

function success(): void {
  void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
}

/** Keeps v inside [lo, hi] with a soft band past the edges. */
function rubber(v: number, lo: number, hi: number, band: number): number {
  "worklet";
  if (v < lo) {
    const o = lo - v;
    return lo - o / (1 + o / band);
  }
  if (v > hi) {
    const o = v - hi;
    return hi + o / (1 + o / band);
  }
  return v;
}

export type CourierZoomTrackerProps = {
  autoPlay?: boolean;
};

export function CourierZoomTracker({ autoPlay = true }: CourierZoomTrackerProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = !reduced && autoPlay;
  const len = ROUTE_LEN;

  // ---- layout: the map runs under everything; the panel sits on the bottom edge
  const bottomInset = Math.max(insets.bottom, 16);
  const panelW = width >= 600 ? Math.min(480, width - 32) : width;
  const panelLeft = (width - panelW) / 2;
  const panelTop = height - PANEL_H - bottomInset;
  const top = insets.top;
  const lay = useMemo(
    () => [width, height, width / 2, top + (panelTop - top) * 0.55, Math.min(width, 520) - 40, panelTop - top - 70, panelTop],
    [width, height, top, panelTop],
  );
  const locateX = width - 16 - ACTION / 2;
  const locateY = panelTop - 16 - ACTION / 2;
  const msgX = panelLeft + panelW - PANEL_PAD - ACTION / 2;
  const buttons = useMemo(
    () => [locateX, locateY, msgX - ACTION - ACTION_GAP, panelTop + ROW_CY, msgX, panelTop + ROW_CY],
    [locateX, locateY, msgX, panelTop],
  );
  const rest = useMemo(() => {
    const c = [0, 0, 0, 0, 0];
    filmCamera(0, len, ROUTE_X, ROUTE_Y, ROUTE_STEP, lay, c);
    return c;
  }, [lay, len]);

  // ---- one clock on the UI thread; the map, the panel and the controls read these
  const t = useSharedValue(0);
  const live = useSharedValue(film ? 1 : 0);
  const camX = useSharedValue(rest[0]);
  const camY = useSharedValue(rest[1]);
  const camZ = useSharedValue(rest[2]);
  const camB = useSharedValue(rest[3]);
  const follow = useSharedValue(1);
  const vanD = useSharedValue(0);
  const vanA = useSharedValue(1);
  const travEnd = useSharedValue(0);
  const travA = useSharedValue(1);
  const arrived = useSharedValue(0);
  const toast = useSharedValue(0);
  const calling = useSharedValue(0);
  const doorPulse = useSharedValue(0);
  const dip = useSharedValue(0);
  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const loopStart = useSharedValue(-1);
  const lastActivity = useSharedValue(-1);
  const touching = useSharedValue(false);
  const dialogOpen = useSharedValue(false);
  const retK = useSharedValue(0);
  const retFrom = useSharedValue<number[]>([0, 0, 0, 0, 0, 0, 0, 0]);

  const [dialog, setDialog] = useState(0);
  const [following, setFollowing] = useState(true);
  const [sent, setSent] = useState<string>(QUICK_REPLIES[0]);

  const showDialog = useCallback(
    (d: number) => {
      dialogOpen.set(d !== 0);
      setDialog(d);
    },
    [dialogOpen],
  );
  /** Before the film starts again: no dialog, and the toast says the film's reply. */
  const resetForFilm = useCallback(() => {
    showDialog(0);
    setSent(QUICK_REPLIES[0]);
  }, [showDialog]);
  useAnimatedReaction(
    () => follow.get(),
    (cur, prev) => {
      if (cur !== prev) scheduleOnRN(setFollowing, cur === 1);
    },
  );

  useFrameCallback((frame) => {
    "worklet";
    const now = frame.timestamp;
    const md = mode.get();
    if (md === MODE_AUTO) {
      if (!film) {
        mode.set(MODE_MANUAL);
        live.set(0);
        return;
      }
      if (loopStart.get() < 0) loopStart.set(now);
      const tt = ((now - loopStart.get()) / 1000) % LOOP;
      t.set(tt);
      const c = [0, 0, 0, 0, 0];
      filmCamera(tt, len, ROUTE_X, ROUTE_Y, ROUTE_STEP, lay, c);
      camX.set(c[0]);
      camY.set(c[1]);
      camZ.set(c[2]);
      camB.set(c[3]);
      if (c[4] !== follow.get()) follow.set(c[4]);
      const v = [0, 0];
      filmVan(tt, len, v);
      vanD.set(v[0]);
      vanA.set(v[1]);
      const tr = [0, 0];
      filmTravelled(tt, len, tr);
      travEnd.set(tr[0]);
      travA.set(tr[1]);
      arrived.set(filmArrived(tt));
      toast.set(filmToast(tt));
      calling.set(filmCall(tt));
      doorPulse.set(filmDoorPulse(tt));
      return;
    }
    if (md === MODE_RETURN) {
      // back to the poster: the camera eases home while the van and the copy dip out and come back reset
      const k = retK.get();
      const e = easeInOutCubic(k);
      const f = retFrom.get();
      const r = [0, 0, 0, 0, 0];
      filmCamera(0, len, ROUTE_X, ROUTE_Y, ROUTE_STEP, lay, r);
      camX.set(f[0] + (r[0] - f[0]) * e);
      camY.set(f[1] + (r[1] - f[1]) * e);
      camZ.set(f[2] + (r[2] - f[2]) * e);
      camB.set(f[3] + (r[3] - f[3]) * e);
      const d = Math.sin(Math.PI * k);
      dip.set(d * d);
      vanA.set(1 - d * d);
      if (k >= 0.5) {
        vanD.set(0);
        travEnd.set(0);
        arrived.set(0);
      } else {
        vanD.set(f[4]);
        arrived.set(f[5] * (1 - clamp01(k * 2)));
      }
      travA.set(1 - d * d);
      toast.set(f[6] * (1 - clamp01(k * 3)));
      calling.set(f[7] * (1 - clamp01(k * 3)));
      return;
    }
    // manual: keep the idle timer; a dialog or a finger counts as activity
    if (lastActivity.get() < 0 || touching.get() || dialogOpen.get()) {
      lastActivity.set(now);
      return;
    }
    if (!film || now - lastActivity.get() < IDLE_MS) return;
    mode.set(MODE_RETURN);
    retFrom.set([camX.get(), camY.get(), camZ.get(), camB.get(), vanD.get(), arrived.get(), toast.get(), calling.get()]);
    scheduleOnRN(resetForFilm);
    follow.set(1);
    doorPulse.set(0);
    retK.set(0);
    retK.set(
      withTiming(1, { duration: RETURN_MS, easing: Easing.linear }, (finished) => {
        "worklet";
        if (!finished) return;
        dip.set(0);
        vanA.set(1);
        travA.set(1);
        t.set(0);
        loopStart.set(-1);
        live.set(1);
        mode.set(MODE_AUTO);
      }),
    );
  });

  /** A touch pauses the film where it is (the van stops; the map is the finger's). */
  const pause = useCallback(() => {
    "worklet";
    const md = mode.get();
    if (md === MODE_RETURN) {
      cancelAnimation(retK);
      dip.set(0);
      vanA.set(1);
      travA.set(1);
    }
    if (md === MODE_AUTO) {
      live.set(withTiming(0, { duration: 220 }));
      doorPulse.set(0);
    }
    cancelAnimation(camX);
    cancelAnimation(camY);
    cancelAnimation(camZ);
    cancelAnimation(camB);
    mode.set(MODE_MANUAL);
    lastActivity.set(-1);
  }, [camB, camX, camY, camZ, dip, doorPulse, lastActivity, live, mode, retK, travA, vanA]);

  /** From a native control (JS thread): pause, and close a dialog the film had opened. */
  const pauseFromControl = useCallback(() => {
    pause();
  }, [pause]);

  const closeDialog = useCallback(() => {
    showDialog(0);
  }, [showDialog]);

  const onCall = useCallback(() => {
    pauseFromControl();
    selection();
    showDialog(1);
  }, [pauseFromControl, showDialog]);
  const onMessage = useCallback(() => {
    pauseFromControl();
    selection();
    showDialog(2);
  }, [pauseFromControl, showDialog]);
  const onDismiss = useCallback(() => {
    // a dismissal the film made itself is already closed in our state
    if (!dialogOpen.get()) return;
    pauseFromControl();
    showDialog(0);
  }, [dialogOpen, pauseFromControl, showDialog]);
  const onConfirmCall = useCallback(() => {
    pauseFromControl();
    success();
    showDialog(0);
    calling.set(withSequence(withTiming(1, { duration: 300, easing: Easing.out(Easing.cubic) }), withDelay(1400, withTiming(0, { duration: 300 }))));
  }, [calling, pauseFromControl, showDialog]);
  const onSend = useCallback(
    (text: string) => {
      pauseFromControl();
      success();
      showDialog(0);
      setSent(text);
      toast.set(withSequence(withTiming(1, { duration: 300, easing: Easing.out(Easing.cubic) }), withDelay(1600, withTiming(0, { duration: 400 }))));
    },
    [pauseFromControl, showDialog, toast],
  );

  /** Locate: the camera glides back onto the van and follows it again. */
  const onLocate = useCallback(() => {
    pauseFromControl();
    selection();
    const c = [0, 0, 0, 0];
    framing(vanD.get(), len, ROUTE_X, ROUTE_Y, ROUTE_STEP, lay, c);
    const timing = { duration: reduced ? 200 : LOCATE_MS, easing: EASE_IN_OUT };
    camX.set(withTiming(c[0], timing));
    camY.set(withTiming(c[1], timing));
    camZ.set(withTiming(c[2], timing));
    camB.set(withTiming(c[3], timing));
    follow.set(1);
  }, [camB, camX, camY, camZ, follow, lay, len, pauseFromControl, reduced, vanD]);

  // ---- the map under a finger: drag to look round (with momentum), pinch to zoom about the fingers
  const startTouch = useCallback(() => {
    "worklet";
    pause();
    touching.set(true);
    follow.set(0);
    if (dialogOpen.get()) scheduleOnRN(closeDialog);
  }, [closeDialog, dialogOpen, follow, pause, touching]);

  const pan = Gesture.Pan()
    .minDistance(2)
    .onStart(() => {
      "worklet";
      startTouch();
    })
    .onChange((e) => {
      "worklet";
      const cam = [0, 0, 0, 0, 0, 0, 0, 0, 0];
      screenCam(camX.get(), camY.get(), camZ.get(), camB.get(), lay, cam);
      const a = [0, 0];
      const b = [0, 0];
      toWorld(lay[2], lay[3], cam, a);
      toWorld(lay[2] + e.changeX, lay[3] + e.changeY, cam, b);
      camX.set(rubber(camX.get() - (b[0] - a[0]), CITY.x0, CITY.x1, 300));
      camY.set(rubber(camY.get() - (b[1] - a[1]), CITY.y0, CITY.y1, 300));
    })
    .onEnd((e) => {
      "worklet";
      const cam = [0, 0, 0, 0, 0, 0, 0, 0, 0];
      screenCam(camX.get(), camY.get(), camZ.get(), camB.get(), lay, cam);
      const a = [0, 0];
      const b = [0, 0];
      toWorld(lay[2], lay[3], cam, a);
      toWorld(lay[2] + e.velocityX, lay[3] + e.velocityY, cam, b);
      camX.set(withDecay({ velocity: -(b[0] - a[0]), deceleration: 0.994, clamp: [CITY.x0, CITY.x1] }));
      camY.set(withDecay({ velocity: -(b[1] - a[1]), deceleration: 0.994, clamp: [CITY.y0, CITY.y1] }));
    })
    .onFinalize(() => {
      "worklet";
      touching.set(false);
    });

  const pinch = Gesture.Pinch()
    .onStart(() => {
      "worklet";
      startTouch();
    })
    .onChange((e) => {
      "worklet";
      const cam = [0, 0, 0, 0, 0, 0, 0, 0, 0];
      screenCam(camX.get(), camY.get(), camZ.get(), camB.get(), lay, cam);
      const f = [0, 0];
      toWorld(e.focalX, e.focalY, cam, f);
      const z0 = camZ.get();
      const z1 = rubber(z0 + Math.log2(Math.max(e.scaleChange, 0.01)), LZ_MIN, LZ_MAX, 0.4);
      const k = Math.pow(2, z0 - z1);
      // the point under the fingers stays put
      camX.set(f[0] - (f[0] - camX.get()) * k);
      camY.set(f[1] - (f[1] - camY.get()) * k);
      camZ.set(z1);
    })
    .onEnd(() => {
      "worklet";
      const z = camZ.get();
      if (z < LZ_MIN || z > LZ_MAX) camZ.set(withTiming(Math.min(Math.max(z, LZ_MIN), LZ_MAX), { duration: 300, easing: EASE_IN_OUT }));
    })
    .onFinalize(() => {
      "worklet";
      touching.set(false);
    });

  const gestures = Gesture.Simultaneous(pan, pinch);

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <CourierMap
        camB={camB}
        camX={camX}
        camY={camY}
        camZ={camZ}
        doorPulse={doorPulse}
        height={height}
        lay={lay}
        travA={travA}
        travEnd={travEnd}
        vanA={vanA}
        vanD={vanD}
        width={width}
      />
      <CourierPins camB={camB} camX={camX} camY={camY} camZ={camZ} lay={lay} />
      <GestureDetector gesture={gestures}>
        <View style={[styles.touchArea, { height: panelTop }]} />
      </GestureDetector>
      <CourierToast bottom={height - panelTop + 16} icon="checkmark.circle.fill" text={`Sent · “${sent}”`} tint="#1F9D55" toast={toast} />
      <CourierToast bottom={height - panelTop + 16} icon="phone.fill" text="Calling Marco…" tint="#2F6BFF" toast={calling} />
      <View style={[styles.locate, { left: locateX - ACTION / 2, top: locateY - ACTION / 2 }]}>
        <CourierLocate following={following} onPress={onLocate} />
      </View>
      <View pointerEvents="box-none" style={[styles.panelWrap, { left: panelLeft, width: panelW }]}>
        <CourierPanel
          arrived={arrived}
          bottom={bottomInset}
          dialog={dialog}
          dip={dip}
          len={len}
          onCall={onCall}
          onConfirmCall={onConfirmCall}
          onDismiss={onDismiss}
          onMessage={onMessage}
          onSend={onSend}
          vanD={vanD}
          width={panelW}
        />
      </View>
      <CourierFingers buttons={buttons} height={height} lay={lay} live={live} t={t} width={width} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#F2EFE9", overflow: "hidden" },
  touchArea: { position: "absolute", left: 0, right: 0, top: 0 },
  locate: { position: "absolute", width: ACTION, height: ACTION },
  panelWrap: { position: "absolute", top: 0, bottom: 0 },
});
