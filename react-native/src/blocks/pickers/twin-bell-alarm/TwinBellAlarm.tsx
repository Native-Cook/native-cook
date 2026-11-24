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
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN } from "react-native-worklets";

import { useReducedMotionPreference } from "@/shared/useReducedMotionPreference";

import {
  filmArmed,
  filmHour,
  filmMinute,
  filmPush,
  filmRing,
  filmWheel,
  hourDeg,
  LOOP,
  minuteDeg,
  ORBIT_SPAN,
  orbitFromDrag,
  RING,
  RING_OUT,
  shortest,
} from "./alarmMotion";
import { AlarmPanel, GUTTER, PANEL_H, PANEL_R } from "./AlarmPanel";
import { APP, BACKDROP, FILM_MIN, INK, REPEATS, REST_MIN } from "./alarmScene";
import { AlarmStage3D } from "./AlarmStage3D";

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

/** This long after the last touch the alarm goes back to its rest (7:00, off), and RETURN_MS later the film restarts. */
const IDLE_MS = 2000;
const RETURN_MS = 1000;
/** A frame that arrives later than this (ms) is a stall: the film's clock does not count it. */
const STALL_MS = 100;
const FRAME_MS = 1000 / 60;
/** Frames between two publications of the clock's anchor to the Filament thread (its listener is added a moment
 * after mount, so a value set before that would never arrive). */
const ANCHOR_BEAT = 30;
/** The ring's haptic: one firm tap as it starts, then a light one every RING_TICK seconds. */
const RING_TICK = 0.3;
/** The hands follow the wheel on this spring (a small overshoot); the view swings back on ORBIT_SPRING. */
const HAND_SPRING = { mass: 1, stiffness: 150, damping: 15 };
const ORBIT_SPRING = { mass: 1, stiffness: 140, damping: 17 };
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
/** A finger's ring moves the camera in for this long (ms each way, with a hold between). */
const PUSH_MS = 650;
const PUSH_HOLD_MS = 900;
const PUSH_BACK_MS = 1100;

function buzz(): void {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
}

function tick(): void {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

/** Publishes the film's clock for the Filament thread (only when it changes): [1 while the film plays, the UI frame
 * time (ms) of its t = 0]. */
function syncAnchor(anchor: SharedValue<number[]>, playing: number, start: number): void {
  "worklet";
  const a = anchor.get();
  if (a[0] !== playing || a[1] !== start) anchor.set([playing, start]);
}

export type TwinBellAlarmProps = {
  autoPlay?: boolean;
};

export function TwinBellAlarm({ autoPlay = true }: TwinBellAlarmProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = !reduced && autoPlay;
  // reduced motion rests on the film's result: 6:30, alarm on, nothing moving
  const initialMinutes = reduced ? FILM_MIN : REST_MIN;
  const initialHour = hourDeg(initialMinutes);
  const initialMinute = minuteDeg(initialMinutes);

  const t = useSharedValue(0);
  const anchor = useSharedValue<number[]>([0, -1]);
  /** The hands (deg clockwise from 12, not wrapped). */
  const hour = useSharedValue(initialHour);
  const minute = useSharedValue(initialMinute);
  /** Seconds into a ring (negative: not ringing), the camera's push, a finger's orbit (deg). */
  const ring = useSharedValue(-1);
  const push = useSharedValue(0);
  const orbit = useSharedValue(0);
  /** What the native controls show: the time (minutes of the day) and the switch (0 / 1). */
  const wheel = useSharedValue(initialMinutes);
  const armed = useSharedValue(reduced ? 1 : 0);
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
  /** A finger's ring runs on its own clock: the UI frame time (ms) it started at, or -1. */
  const ringStart = useSharedValue(-1);

  // the 3D stage is the window above the sheet (it runs a little way under the sheet's round corners)
  const stageH = Math.max(200, height - PANEL_H - insets.bottom + PANEL_R);

  /** The hands go to a time by the shortest way round, each on its own spring. */
  const sweepTo = useCallback(
    (min: number) => {
      "worklet";
      const h = hour.get() + shortest(hourDeg(min) - hour.get());
      const m = minute.get() + shortest(minuteDeg(min) - minute.get());
      if (reduced) {
        hour.set(h);
        minute.set(m);
        return;
      }
      hour.set(withSpring(h, HAND_SPRING));
      minute.set(withSpring(m, HAND_SPRING));
    },
    [hour, minute, reduced],
  );

  /** The ring stops: what is left of it is its dying away, and the camera eases back. */
  const quiet = useCallback(() => {
    "worklet";
    if (ringStart.get() >= 0 && nowMs.get() >= 0) {
      const tau = (nowMs.get() - ringStart.get()) / 1000;
      if (tau < RING - RING_OUT) ringStart.set(nowMs.get() - (RING - RING_OUT) * 1000);
    }
    cancelAnimation(push);
    push.set(withTiming(0, { duration: PUSH_BACK_MS, easing: EASE_IN_OUT }));
  }, [nowMs, push, ringStart]);

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
          // the film's result, still
          wheel.set(FILM_MIN);
          armed.set(1);
          hour.set(hourDeg(FILM_MIN));
          minute.set(minuteDeg(FILM_MIN));
          ring.set(-1);
          push.set(0);
        }
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
        loopStart.set(now);
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
      // the same values the Filament thread computes for itself, kept here so a touch can take over from them
      hour.set(filmHour(el));
      minute.set(filmMinute(el));
      push.set(filmPush(el));
      const rt = filmRing(el);
      if (ring.get() !== rt) ring.set(rt);
      const w = filmWheel(el);
      if (wheel.get() !== w) wheel.set(w);
      const a = filmArmed(el);
      if (armed.get() !== a) armed.set(a);
      return;
    }
    syncAnchor(anchor, 0, -1);
    // a finger's ring, on its own clock
    if (ringStart.get() >= 0) {
      const tau = (now - ringStart.get()) / 1000;
      if (tau <= RING) {
        ring.set(tau);
      } else {
        ring.set(-1);
        ringStart.set(-1);
      }
    } else if (ring.get() !== -1) {
      ring.set(-1);
    }
    if (md === MODE_RETURN) {
      // the alarm is back at 7:00 and off, the camera home: the film starts over
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
    // 2 s after the last touch: the wheel and the hands go back to 7:00, the switch goes off, the camera eases home
    mode.set(MODE_RETURN);
    returnAt.set(now);
    wheel.set(REST_MIN);
    armed.set(0);
    sweepTo(REST_MIN);
    quiet();
    orbit.set(withTiming(0, { duration: 700, easing: EASE_IN_OUT }));
  });

  // the ring's haptic: a firm tap as it starts, then light ones
  useAnimatedReaction(
    () => (ring.get() < 0 ? -1 : Math.floor(ring.get() / RING_TICK)),
    (cur, prev) => {
      if (prev == null || cur === prev || cur < 0 || reduced) return;
      scheduleOnRN(cur === 0 ? buzz : tick);
    },
  );

  /** A touch takes over from wherever the film (or the return) is. */
  const pause = useCallback(() => {
    "worklet";
    const md = mode.get();
    if (md === MODE_AUTO) {
      // a ring in progress carries on, on its own clock; the camera eases back after it
      const rt = ring.get();
      if (rt >= 0 && nowMs.get() >= 0) {
        ringStart.set(nowMs.get() - rt * 1000);
        push.set(withDelay(Math.max(0, (RING - rt) * 1000), withTiming(0, { duration: PUSH_BACK_MS, easing: EASE_IN_OUT })));
      } else if (push.get() > 0) {
        push.set(withTiming(0, { duration: PUSH_BACK_MS, easing: EASE_IN_OUT }));
      }
      // the hands may be mid-sweep: they finish at the time the wheel shows
      sweepTo(wheel.get());
    }
    if (md === MODE_RETURN) cancelAnimation(orbit);
    mode.set(MODE_MANUAL);
    // the Filament thread stops computing the film at once and shows the shared values from here on
    syncAnchor(anchor, 0, -1);
    lastTouch.set(-1);
  }, [anchor, lastTouch, mode, nowMs, orbit, push, ring, ringStart, sweepTo, wheel]);

  /** A finger moved the wheel. */
  const userMinutes = useCallback(
    (min: number) => {
      "worklet";
      pause();
      wheel.set(min);
      sweepTo(min);
    },
    [pause, sweepTo, wheel],
  );
  /** A finger flipped the switch: on rings (the camera moves in, holds, comes back), off stops it. */
  const userToggle = useCallback(
    (on: boolean) => {
      "worklet";
      pause();
      armed.set(on ? 1 : 0);
      if (on && !reduced && nowMs.get() >= 0) {
        ringStart.set(nowMs.get());
        cancelAnimation(push);
        push.set(
          withSequence(
            withTiming(1, { duration: PUSH_MS, easing: EASE_IN_OUT }),
            withDelay(PUSH_HOLD_MS, withTiming(0, { duration: PUSH_BACK_MS, easing: EASE_IN_OUT })),
          ),
        );
        return;
      }
      quiet();
    },
    [armed, nowMs, pause, push, quiet, reduced, ringStart],
  );
  const onMinutes = useCallback(
    (min: number) => {
      runOnUI(userMinutes)(min);
    },
    [userMinutes],
  );
  const onToggle = useCallback(
    (on: boolean) => {
      runOnUI(userToggle)(on);
    },
    [userToggle],
  );

  // drag across the clock to look round it; it swings back when the finger lets go
  const pan = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .failOffsetY([-28, 28])
    .onStart(() => {
      "worklet";
      pause();
      dragging.set(true);
      cancelAnimation(orbit);
      // pick the clock up from wherever its swing back had got to
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
  const filming = useDerivedValue<number>(() => (mode.get() === MODE_AUTO ? 1 : 0));

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View accessibilityHint="Drag sideways to look round the clock" accessibilityLabel="Alarm clock" collapsable={false} style={[styles.stage, { height: stageH }]}>
          <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, stage]}>
            <AlarmStage3D
              anchor={anchor}
              height={stageH}
              hour={hour}
              initialHour={initialHour}
              initialMinute={initialMinute}
              minute={minute}
              onReady={onReady}
              orbit={orbit}
              push={push}
              ring={ring}
              width={width}
            />
          </Animated.View>
          {/* the backdrop's colour over the top of the sweep, so the status bar and the app's name sit on one tone */}
          <LinearGradient colors={[BACKDROP, "rgba(185,197,218,0.6)", "rgba(185,197,218,0)"]} locations={[0, 0.55, 1]} pointerEvents="none" style={[styles.topShade, { height: insets.top + 72 }]} />
        </View>
      </GestureDetector>
      <View pointerEvents="none" style={[styles.header, { top: insets.top + 10, left: GUTTER, right: GUTTER }]}>
        <Text style={styles.app}>{APP}</Text>
        <Text style={styles.repeats}>{REPEATS}</Text>
      </View>
      <AlarmPanel
        armed={armed}
        bottom={insets.bottom}
        filming={filming}
        initialMinutes={initialMinutes}
        initialOn={reduced}
        onMinutes={onMinutes}
        onToggle={onToggle}
        t={t}
        wheel={wheel}
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
  repeats: { fontSize: 15, lineHeight: 20, color: "rgba(28,29,34,0.62)" },
});
