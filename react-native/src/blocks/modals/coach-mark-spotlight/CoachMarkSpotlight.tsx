// Penny, a budgeting app, on its first run: the home screen, the tour over it and the autoPlay script.
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo } from 'react';
import { StyleSheet, useWindowDimensions, View, type GestureResponderEvent } from 'react-native';
import Animated, { useAnimatedStyle, useFrameCallback, useSharedValue, withTiming, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import {
  adoptTour,
  CoachMarks,
  FOOT_H,
  NEXT_W,
  openTour,
  SKIP_W,
  TIP_PAD,
  tipPlaces,
  tourAtRest,
  useCoachMotion,
  type CoachMotion,
} from './CoachMarks';
import { STEPS, type CoachStep } from './data';
import {
  DRAG_SPRING,
  easeInOutCubic,
  easeOutCubic,
  GHOST_DRAG,
  HOLDS,
  IRIS_SPRING,
  LOOP,
  mix,
  MORPH,
  PAD,
  PULSE_PERIOD,
  RESUME_AFTER,
  seg,
  springAt,
  T,
  TIP_H,
  TIP_LAG,
  TIP_MAX_W,
  type Box,
} from './motion';
import { PAPER, PennyHome, pennyLayout } from './PennyHome';

const GHOST = 44;

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

/** Writes only real changes (to 1e-4), so nothing downstream runs while the pose holds still. */
function put(value: SharedValue<number>, next: number) {
  'worklet';
  const q = Math.round(next * 10000) / 10000;
  if (value.get() !== q) value.set(q);
}

/** A tap's press: in 100 ms before the finger lifts at `at`, out 120 ms after. */
function pressAt(t: number, at: number) {
  'worklet';
  const d = t - at;
  if (d <= -0.1 || d >= 0.12) return 0;
  return d < 0 ? 1 + d / 0.1 : 1 - d / 0.12;
}

/** The ring's breathing in the holds: phase and how much shows (fades at each hold's edges). */
function pulseAt(t: number) {
  'worklet';
  for (let i = 0; i < HOLDS.length; i += 1) {
    const start = HOLDS[i][0];
    const end = HOLDS[i][1];
    const tt = end > LOOP && t < end - LOOP ? t + LOOP : t;
    if (tt >= start && tt <= end) {
      const phase = ((tt - start) / PULSE_PERIOD) % 1;
      const gate = seg(tt, start, 0.15) * (1 - seg(tt, end - 0.15, 0.15));
      return { phase, gate };
    }
  }
  return { phase: 0, gate: 0 };
}

/** The tour at loop time t. */
function applyPose(m: CoachMotion, t: number) {
  'worklet';
  const before = t < T.reset;
  const m1 = easeInOutCubic(seg(t, T.morph1, MORPH));
  const m2 = easeInOutCubic(seg(t, T.morph2, MORPH));
  const m3 = easeInOutCubic(seg(t, T.morph3, MORPH));
  put(m.p, before ? m1 + m2 - m3 : 0);
  // The tooltip rides the cutout's curve TIP_LAG behind it, so it never arrives first.
  const s1 = easeInOutCubic(seg(t, T.morph1 + TIP_LAG, MORPH));
  const s2 = easeInOutCubic(seg(t, T.morph2 + TIP_LAG, MORPH));
  const s3 = easeInOutCubic(seg(t, T.morph3 + TIP_LAG, MORPH));
  put(m.tipP, before ? s1 + s2 - s3 : 0);

  let x = 0;
  if (t > T.dragStart && t < T.dragEnd) x = GHOST_DRAG * easeInOutCubic((t - T.dragStart) / (T.dragEnd - T.dragStart));
  else if (t >= T.dragEnd) x = GHOST_DRAG * (1 - springAt(t - T.dragEnd, DRAG_SPRING));
  put(m.tipX, x);

  const shown = t < T.intro ? 1 - easeInOutCubic(seg(t, T.fadeOut, T.fadeOutSpan)) : easeOutCubic(seg(t, T.intro, 0.4));
  put(m.shown, shown);
  // The spring's tail is forced to exactly 0 over the rest, so the loop's last frame is its first.
  put(m.iris, before ? 0 : (1 - springAt(t - T.intro, IRIS_SPRING)) * (1 - seg(t, T.rest, 0.4)));
  put(m.tipShow, t < T.tipIn ? 1 - easeInOutCubic(seg(t, T.fadeOut, 0.25)) : easeOutCubic(seg(t, T.tipIn, T.tipInSpan)));
  put(m.visible, shown > 0.5 ? 1 : 0);

  const pulse = pulseAt(t);
  put(m.pulse, pulse.phase);
  put(m.pulseGate, pulse.gate);
  put(m.nextPress, pressAt(t, T.next1));
  put(m.targetPress, pressAt(t, T.tapCard));
  put(m.skipPress, pressAt(t, T.skip));
}

/** The resting tour: first step, shown, still. */
function restPose(m: CoachMotion) {
  'worklet';
  m.p.set(0);
  m.tipP.set(0);
  m.tipX.set(0);
  m.shown.set(1);
  m.tipShow.set(1);
  m.iris.set(0);
  m.pulse.set(0);
  m.pulseGate.set(0);
  m.nextPress.set(0);
  m.skipPress.set(0);
  m.targetPress.set(0);
  m.step.set(0);
  m.visible.set(1);
}

type Point = { t: number; x: number; y: number };

function ghostAt(path: Point[], t: number, axis: 'x' | 'y') {
  'worklet';
  if (t <= path[0].t) return path[0][axis];
  for (let i = 1; i < path.length; i += 1) {
    if (t <= path[i].t) {
      const u = easeInOutCubic((t - path[i - 1].t) / (path[i].t - path[i - 1].t));
      return mix(u, path[i - 1][axis], path[i][axis]);
    }
  }
  return path[path.length - 1][axis];
}

function ghostOpacity(t: number) {
  'worklet';
  const a = seg(t, T.ghostIn, 0.18) * (1 - seg(t, T.next1 + 0.15, 0.2));
  const b = seg(t, T.tapCard - 0.4, 0.2) * (1 - seg(t, T.tapCard + 0.15, 0.2));
  const c = seg(t, T.dragStart - 0.4, 0.2) * (1 - seg(t, T.dragEnd + 0.08, 0.2));
  const d = seg(t, T.skip - 0.4, 0.2) * (1 - seg(t, T.skip + 0.15, 0.2));
  return Math.max(a, b, c, d);
}

function ghostPress(t: number) {
  'worklet';
  const drag = seg(t, T.dragStart - 0.08, 0.08) * (1 - seg(t, T.dragEnd, 0.1));
  return Math.max(pressAt(t, T.next1), pressAt(t, T.tapCard), pressAt(t, T.skip), drag);
}

export type CoachMarkSpotlightProps = {
  /** Plays the 9 s loop until a finger takes over; it resumes 2 s after the last touch. */
  autoPlay?: boolean;
  /** One step per highlighted element: the add button, the month card, the Insights tab. */
  steps?: CoachStep[];
};

export function CoachMarkSpotlight({ autoPlay = true, steps = STEPS }: CoachMarkSpotlightProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const playing = autoPlay && !reduced;
  const count = Math.min(steps.length, 3);
  const shownSteps = useMemo(() => steps.slice(0, count), [count, steps]);

  const m = useCoachMotion();
  const clock = useSharedValue(0);
  const mode = useSharedValue(playing ? MODE_AUTO : MODE_MANUAL);
  const seen = useSharedValue(0);
  const idle = useSharedValue(0);
  const rootHeld = useSharedValue(0);
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  const ghostO = useSharedValue(0);
  const ghostS = useSharedValue(0);

  const layout = useMemo(() => pennyLayout(width, height, insets.top, insets.bottom), [height, insets.bottom, insets.top, width]);

  /** The three things the tour explains, padded into cutouts: a circle, a wide card, a tab item. */
  const targets = useMemo<Box[]>(() => {
    const { fab, card, tab } = layout;
    const d = fab.w + PAD * 2;
    return [
      { x: fab.x - PAD, y: fab.y - PAD, w: d, h: d, r: d / 2 },
      { x: card.x - 6, y: card.y - 6, w: card.w + 12, h: card.h + 12, r: card.r + 6 },
      { x: tab.x, y: tab.y, w: tab.w, h: tab.h, r: tab.r },
    ].slice(0, count);
  }, [count, layout]);

  const tipW = Math.min(width - 32, TIP_MAX_W);
  const places = useMemo(() => tipPlaces(targets, width, height, tipW), [height, targets, tipW, width]);

  const ghostPath = useMemo<Point[]>(() => {
    const at = (i: number) => places[Math.min(i, places.length - 1)];
    const nextX = (i: number) => at(i).x + tipW - TIP_PAD - NEXT_W / 2;
    const footY = (i: number) => at(i).y + TIP_H - TIP_PAD - FOOT_H / 2;
    const skipX = (i: number) => at(i).x + tipW - TIP_PAD - NEXT_W - 6 - SKIP_W / 2;
    const card = targets[Math.min(1, targets.length - 1)];
    const cardX = card.x + card.w * 0.62;
    const cardY = card.y + card.h * 0.55;
    const grabX = at(2).x + tipW * 0.36;
    const grabY = at(2).y + 64;
    return [
      { t: T.ghostIn, x: nextX(0) + 24, y: footY(0) + 40 },
      { t: T.next1 - 0.08, x: nextX(0), y: footY(0) },
      { t: T.next1 + 0.12, x: nextX(0), y: footY(0) },
      { t: T.next1 + 0.45, x: nextX(0) + 12, y: footY(0) + 30 },
      { t: T.tapCard - 0.4, x: cardX + 20, y: cardY + 40 },
      { t: T.tapCard - 0.08, x: cardX, y: cardY },
      { t: T.tapCard + 0.12, x: cardX, y: cardY },
      { t: T.tapCard + 0.45, x: cardX + 10, y: cardY + 30 },
      { t: T.dragStart - 0.4, x: grabX + 20, y: grabY + 36 },
      { t: T.dragStart - 0.04, x: grabX, y: grabY },
      { t: T.dragStart, x: grabX, y: grabY },
      // While the finger drags, its x is the tooltip's (below).
      { t: T.dragEnd, x: grabX + GHOST_DRAG, y: grabY },
      { t: T.dragEnd + 0.3, x: grabX + GHOST_DRAG + 12, y: grabY + 24 },
      { t: T.skip - 0.4, x: skipX(1) + 20, y: footY(1) + 36 },
      { t: T.skip - 0.08, x: skipX(1), y: footY(1) },
      { t: T.skip + 0.12, x: skipX(1), y: footY(1) },
      { t: T.skip + 0.45, x: skipX(1) + 10, y: footY(1) + 30 },
    ];
  }, [places, targets, tipW]);
  const grabX = places[Math.min(2, places.length - 1)].x + tipW * 0.36;

  const onTouch = useCallback((event: GestureResponderEvent) => {
    rootHeld.set(event.nativeEvent.touches.length);
    m.touches.set(m.touches.get() + 1);
  }, [m.touches, rootHeld]);

  const frameLoop = useFrameCallback(frame => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 48) / 1000;
    if (dt <= 0) return;

    if (m.touches.get() !== seen.get()) {
      seen.set(m.touches.get());
      idle.set(0);
      if (mode.get() !== MODE_MANUAL) {
        adoptTour(m, count);
        ghostO.set(withTiming(0, { duration: 120 }));
        mode.set(MODE_MANUAL);
      }
    }
    if (!playing) return;

    if (mode.get() !== MODE_AUTO) {
      // The ring keeps breathing on a settled step while the finger has the tour.
      const settled = Math.abs(m.p.get() - m.step.get()) < 0.02 && Math.abs(m.tipX.get()) < 2 ? 1 : 0;
      const gate = m.pulseGate.get();
      m.pulse.set((m.pulse.get() + dt / PULSE_PERIOD) % 1);
      m.pulseGate.set(gate + (settled * m.shown.get() - gate) * Math.min(1, dt * 8));
    }

    if (mode.get() === MODE_MANUAL) {
      if (m.held.get() > 0 || rootHeld.get() > 0) {
        idle.set(0);
        return;
      }
      idle.set(idle.get() + dt);
      if (idle.get() >= RESUME_AFTER) {
        openTour(m);
        mode.set(MODE_RETURN);
      }
      return;
    }
    if (mode.get() === MODE_RETURN) {
      if (tourAtRest(m)) {
        clock.set(0);
        m.driven.set(1);
        mode.set(MODE_AUTO);
      }
      return;
    }

    let t = clock.get() + dt;
    if (t >= LOOP) t -= LOOP;
    clock.set(t);
    applyPose(m, t);
    const inDrag = t > T.dragStart && t < T.dragEnd;
    ghostX.set(inDrag ? grabX + m.tipX.get() : ghostAt(ghostPath, t, 'x'));
    ghostY.set(ghostAt(ghostPath, t, 'y'));
    ghostO.set(ghostOpacity(t));
    ghostS.set(ghostPress(t));
  }, playing);

  useEffect(() => {
    frameLoop.setActive(playing);
    if (playing) {
      clock.set(0);
      mode.set(MODE_AUTO);
      m.driven.set(1);
      return;
    }
    // Reduce Motion or autoPlay off: the first step, shown and still.
    mode.set(MODE_MANUAL);
    m.driven.set(0);
    ghostO.set(0);
    restPose(m);
  }, [clock, frameLoop, ghostO, m, mode, playing]);

  const ghostStyle = useAnimatedStyle(() => ({
    opacity: mode.get() === MODE_AUTO ? ghostO.get() * 0.9 : 0,
    transform: [
      { translateX: ghostX.get() - GHOST / 2 },
      { translateY: ghostY.get() - GHOST / 2 },
      { scale: 1 - 0.16 * ghostS.get() },
    ],
  }));

  return (
    <View style={styles.screen} onTouchStart={onTouch} onTouchEnd={onTouch} onTouchCancel={onTouch}>
      <StatusBar style="dark" />
      <PennyHome layout={layout} width={width} />
      <CoachMarks steps={shownSteps} targets={targets} motion={m} width={width} height={height} reducedMotion={reduced} />
      <Animated.View style={[styles.ghost, ghostStyle]} pointerEvents="none" />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAPER[1], overflow: 'hidden' },
  ghost: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: GHOST,
    height: GHOST,
    borderRadius: GHOST / 2,
    backgroundColor: 'rgba(236,240,236,0.34)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.8)',
  },
});
