/**
 * Tilt on the UI thread from three sources: the finger (while held), the device's rotation and a slow idle
 * drift. The autoplay's scripted finger calls the same holdBegin / holdMove / holdEnd as a real one.
 */
import { useCallback, useMemo } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import {
  ReduceMotion, SensorType, useAnimatedSensor, useFrameCallback, useSharedValue, withSpring, withTiming,
} from 'react-native-reanimated';

import * as Haptics from 'expo-haptics';
import { scheduleOnRN } from 'react-native-worklets';

import { AUTOPLAY, FLIP, LOOP_MS, QA_HOLD, QA_HOLD_AT, SPRING, TILT, TIMING } from './motion';

function tapHaptic() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}
// The face that shows has changed: the turn is committed.
function faceHaptic() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}
// The finger has reached the card's limit and it starts to resist.
function edgeHaptic() {
  void Haptics.selectionAsync().catch(() => {});
}

type Options = {
  half: { x: number; y: number };
  ready: boolean;
  autoPlay: boolean;
  reduceMotion: boolean;
  // The Watchlist button's centre in the card's coordinates, and what the scripted finger's tap on it does.
  watchPoint: { x: number; y: number };
  /** The scripted finger's tap on Watchlist: the first adds, the second removes, so the loop ends unsaved. */
  onScriptWatch: (on: boolean) => void;
  /** The demo starts again after a touch: the page goes back to how it began (not saved). */
  onResume: () => void;
};
const never = ReduceMotion.Never;

function soften(v: number, max: number, rubber: number): number {
  'worklet';
  const a = Math.abs(v);
  if (a <= max) return v;
  return Math.sign(v) * (max + (a - max) * rubber);
}

export function usePosterTilt({ half, ready, autoPlay, reduceMotion, watchPoint, onScriptWatch, onResume }: Options) {
  const sensor = useAnimatedSensor(SensorType.ROTATION, { interval: 'auto' });
  const tiltX = useSharedValue(0);
  const tiltY = useSharedValue(0);
  // Finger tilt while held; after release, the offset that springs back to the ambient tilt.
  const holdX = useSharedValue(0);
  const holdY = useSharedValue(0);
  const offsetX = useSharedValue(0);
  const offsetY = useSharedValue(0);
  const lift = useSharedValue(1);
  // 0 = poster, 1 = back (credits). A tap flips it on a spring that keeps the finger's spin.
  const flip = useSharedValue(0);
  const flipped = useSharedValue(false);
  const pressAt = useSharedValue(0);
  const face = useSharedValue(0);
  const clock = useSharedValue(0);
  const ambient = useSharedValue({ x: 0, y: 0, baseRoll: 0, basePitch: 0, based: false, clock: 0 });
  const hold = useSharedValue({ active: false, x0: 0, y0: 0, lastX: 0, lastY: 0, px: 0, py: 0, travel: 0, edge: false, scripted: false });
  const finger = useSharedValue({ x: 0, y: 0, alpha: 0 });
  const script = useSharedValue({ time: 0, paused: false, realDown: false, quiet: 0, down: false });

  const fingerTilt = useCallback((dx: number, dy: number) => {
    'worklet';
    const max = reduceMotion ? TILT.reducedMax : TILT.max;
    // Dragging right turns the card's right edge away (rotateY +); dragging down tips its top towards you.
    return {
      x: soften((dx / half.x) * TILT.perHalfCard, max, reduceMotion ? 0 : TILT.rubber),
      y: soften((dy / half.y) * TILT.perHalfCard, max, reduceMotion ? 0 : TILT.rubber),
    };
  }, [half.x, half.y, reduceMotion]);

  const holdBegin = useCallback((x: number, y: number, scripted: boolean) => {
    'worklet';
    if (!ready) return;
    const a = ambient.get();
    // Start from wherever the card is now, so catching a wobble never jumps.
    const cx = tiltX.get() - a.x;
    const cy = tiltY.get() - a.y;
    pressAt.set(Date.now());
    hold.set({ active: true, x0: x - (cx / TILT.perHalfCard) * half.x, y0: y - (cy / TILT.perHalfCard) * half.y, lastX: x, lastY: y, px: x, py: y, travel: 0, edge: false, scripted });
    holdX.set(cx);
    holdY.set(cy);
    offsetX.set(0);
    offsetY.set(0);
    if (!reduceMotion) lift.set(withSpring(TILT.lift, { ...SPRING.lift, reduceMotion: never }));
  }, [ambient, half.x, half.y, hold, holdX, holdY, lift, offsetX, offsetY, pressAt, ready, reduceMotion, tiltX, tiltY]);

  const holdMove = useCallback((x: number, y: number) => {
    'worklet';
    const h = hold.get();
    if (!h.active) return;
    const t = fingerTilt(x - h.x0, y - h.y0);
    holdX.set(t.x);
    holdY.set(t.y);
    const max = reduceMotion ? TILT.reducedMax : TILT.max;
    const edge = Math.max(Math.abs(t.x), Math.abs(t.y)) > max;
    if (edge && !h.edge && !h.scripted) scheduleOnRN(edgeHaptic);
    hold.set({ ...h, lastX: x, lastY: y, edge, travel: Math.max(h.travel, Math.hypot(x - h.px, y - h.py)) });
  }, [fingerTilt, hold, holdX, holdY, reduceMotion]);

  const setFlip = useCallback((next: boolean) => {
    'worklet';
    flipped.set(next);
    flip.set(reduceMotion
      ? withTiming(next ? 1 : 0, { duration: TIMING.reducedMs, reduceMotion: never })
      : withSpring(next ? 1 : 0, { ...SPRING.flip, reduceMotion: never }));
    // Turned over, the card stays presented (grown); turned back, it settles into the page.
    lift.set(withSpring(next && !reduceMotion ? TILT.lift : 1, { ...SPRING.lift, reduceMotion: never }));
  }, [flip, flipped, lift, reduceMotion]);

  /** VoiceOver's activate on the poster. */
  const toggleFlip = useCallback(() => {
    'worklet';
    setFlip(!flipped.get());
  }, [flipped, setFlip]);

  const holdEnd = useCallback((vx: number, vy: number, scriptedFlip?: boolean) => {
    'worklet';
    const h = hold.get();
    if (!h.active) return;
    hold.set({ ...h, active: false });
    // Scripted card taps commit their intended face on release. A real finger still has to pass the tap test.
    if (scriptedFlip !== undefined) {
      setFlip(scriptedFlip);
    } else if (Date.now() - pressAt.get() < FLIP.tapMs && h.travel < FLIP.slop && Math.abs(vx) + Math.abs(vy) < FLIP.tapSpeed) {
      if (!h.scripted) scheduleOnRN(tapHaptic);
      setFlip(!flipped.get());
    } else lift.set(withSpring(flipped.get() && !reduceMotion ? TILT.lift : 1, { ...SPRING.lift, reduceMotion: never }));
    offsetX.set(holdX.get());
    offsetY.set(holdY.get());
    if (reduceMotion) {
      offsetX.set(withTiming(0, { duration: TIMING.reducedMs, reduceMotion: never }));
      offsetY.set(withTiming(0, { duration: TIMING.reducedMs, reduceMotion: never }));
      return;
    }
    // Hand the finger's speed to the spring: a flick swings through the centre and wobbles once.
    offsetX.set(withSpring(0, { ...SPRING.release, velocity: (vx / half.x) * TILT.perHalfCard, reduceMotion: never }));
    offsetY.set(withSpring(0, { ...SPRING.release, velocity: (vy / half.y) * TILT.perHalfCard, reduceMotion: never }));
  }, [flipped, half.x, half.y, hold, holdX, holdY, lift, offsetX, offsetY, pressAt, reduceMotion, setFlip]);

  const touch = useCallback((down: boolean) => {
    'worklet';
    const s = script.get();
    if (down && !s.paused && s.down) holdEnd(0, 0);
    if (down) finger.set({ ...finger.get(), alpha: 0 });
    script.set({ ...s, paused: true, realDown: down, quiet: 0, down: false });
  }, [finger, holdEnd, script]);

  /** The scripted finger: a figure-eight that ends off-centre, so the release shows the wobble. */
  const path = useCallback((t: number) => {
    'worklet';
    const A = AUTOPLAY;
    const span = A.holdEnd - A.holdStart;
    const u = Math.min(1, Math.max(0, (t - A.holdStart) / span));
    const ramp = Math.min(1, (t - A.holdStart) / A.ramp);
    const eased = ramp * ramp * (3 - 2 * ramp);
    const angle = Math.PI * 2 * 1.15 * u;
    return { x: half.x * A.ampX * Math.sin(angle) * eased, y: half.y * A.ampY * Math.sin(2 * angle) * eased };
  }, [half.x, half.y]);

  const runScript = useCallback((dt: number, cx: number, cy: number) => {
    'worklet';
    const s = script.get();
    const t = s.time;
    const inside = t >= AUTOPLAY.holdStart && t < AUTOPLAY.holdEnd;
    let down = s.down;
    if (inside && !down) {
      const p = path(t);
      holdBegin(cx + p.x, cy + p.y, true);
      down = true;
    }
    if (inside) {
      const p = path(t);
      finger.set({ x: cx + p.x, y: cy + p.y, alpha: Math.min(1, finger.get().alpha + dt / 140) });
      holdMove(cx + p.x, cy + p.y);
    } else if (down) {
      const a = path(AUTOPLAY.holdEnd - 16);
      const b = path(AUTOPLAY.holdEnd);
      holdMove(cx + b.x, cy + b.y);
      holdEnd(((b.x - a.x) / 16) * 1000, ((b.y - a.y) / 16) * 1000);
      down = false;
    } else {
      // The taps: the finger fades in over its target, presses, lifts and fades out.
      let target = null as null | { x: number; y: number; alpha: number };
      for (const tap of AUTOPLAY.taps) {
        const p = tap.on === 'card' ? { x: cx, y: cy } : watchPoint;
        const from = tap.at - AUTOPLAY.fingerLeadMs;
        const to = tap.at + AUTOPLAY.pressMs + AUTOPLAY.fingerLeadMs;
        if (t >= from && t < to) target = { ...p, alpha: Math.min(1, (t - from) / 140, (to - t) / 140) };
        const up = tap.at + AUTOPLAY.pressMs;
        if (tap.on === 'card' && t < tap.at && t + dt >= tap.at) holdBegin(p.x, p.y, true);
        if (tap.on === 'card' && t < up && t + dt >= up) {
          holdEnd(0, 0, tap.at === AUTOPLAY.taps[0].at);
        }
        if (tap.on === 'watch' && t < tap.at && t + dt >= tap.at) scheduleOnRN(onScriptWatch, tap.at === AUTOPLAY.taps[2].at);
      }
      if (target) finger.set(target);
      else if (finger.get().alpha > 0) finger.set({ ...finger.get(), alpha: Math.max(0, finger.get().alpha - dt / 140) });
    }
    let next = t + dt;
    if (QA_HOLD !== null && t >= QA_HOLD_AT[QA_HOLD]) next = t;
    if (next >= LOOP_MS) next -= LOOP_MS;
    script.set({ ...s, time: next, down });
  }, [finger, holdBegin, holdEnd, holdMove, onScriptWatch, path, script, watchPoint]);

  // The card's centre in the gesture's coordinates (the card view itself).
  const centre = useMemo(() => ({ x: half.x, y: half.y }), [half.x, half.y]);

  useFrameCallback((frame) => {
    'worklet';
    if (!ready) return;
    const dt = Math.min(frame.timeSincePreviousFrame ?? 0, TIMING.maxFrameMs);
    const a = ambient.get();
    let ax = 0;
    let ay = 0;
    if (!reduceMotion) {
      const r = sensor.sensor.get();
      const based = a.based || r.roll !== 0 || r.pitch !== 0;
      const baseRoll = a.based ? a.baseRoll : r.roll;
      const basePitch = a.based ? a.basePitch : r.pitch;
      const sx = Math.max(-TILT.sensorMax, Math.min(TILT.sensorMax, (r.roll - baseRoll) * TILT.sensorGain));
      const sy = Math.max(-TILT.sensorMax, Math.min(TILT.sensorMax, -(r.pitch - basePitch) * TILT.sensorGain));
      const c = autoPlay && !script.get().paused ? script.get().time : a.clock + dt;
      const phase = (c / TILT.driftPeriodMs) * Math.PI * 2;
      // The drift's period divides the loop, so the preview repeats exactly.
      const dx = Math.sin(phase) * TILT.driftAmplitude;
      const dy = Math.sin(phase * 2) * TILT.driftAmplitude * 0.6;
      ax = a.x + (sx + dx - a.x) * TILT.sensorSmoothing;
      ay = a.y + (sy + dy - a.y) * TILT.sensorSmoothing;
      ambient.set({ x: ax, y: ay, baseRoll, basePitch, based, clock: c });
      clock.set(c);
    }
    // A real turn commits when the other face comes round.
    const f = Math.cos(tiltX.get() + flip.get() * Math.PI) >= 0 ? 0 : 1;
    if (f !== face.get()) {
      face.set(f);
      if (!(autoPlay && !script.get().paused)) scheduleOnRN(faceHaptic);
    }
    if (hold.get().active) {
      tiltX.set(holdX.get() + ax);
      tiltY.set(holdY.get() + ay);
    } else {
      tiltX.set(offsetX.get() + ax);
      tiltY.set(offsetY.get() + ay);
    }
    if (!autoPlay) return;
    const s = script.get();
    if (s.paused) {
      const quiet = s.realDown ? 0 : s.quiet + dt;
      if (quiet >= AUTOPLAY.resumeDelayMs) {
        // Two seconds after the last touch: face up, not saved, and the demo starts again from rest.
        if (flipped.get()) setFlip(false);
        scheduleOnRN(onResume);
        script.set({ time: 0, paused: false, realDown: false, quiet: 0, down: false });
      }
      else script.set({ ...s, quiet });
      return;
    }
    runScript(dt, centre.x, centre.y);
  });

  const gesture = useMemo(() => Gesture.Pan()
    .enabled(ready)
    .minDistance(0)
    .maxPointers(1)
    .onTouchesDown(() => { touch(true); })
    .onBegin((e) => { holdBegin(e.x, e.y, false); })
    .onUpdate((e) => { holdMove(e.x, e.y); })
    .onEnd((e) => { holdEnd(e.velocityX, e.velocityY); })
    .onFinalize((_e, success) => {
      if (!success) holdEnd(0, 0);
      touch(false);
    }), [holdBegin, holdEnd, holdMove, ready, touch]);

  return { tiltX, tiltY, lift, flip, clock, finger, gesture, toggleFlip };
}
