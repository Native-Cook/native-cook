// Chrome Zero — one UI-thread clock owns the loop, touch physics and return to autoplay.
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import { AppState } from 'react-native';
import { ReduceMotion, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { REST } from './zero-contract';
import { ZERO_DRAG_POINTS, ZERO_RESUME } from './zero-theme';
import {
  bezier, clamp01, coastYaw, cubicOut, glintPulse, landedSpring, lerp, loosen,
  nearestRest, progress, rubberPitch, touchRipple, TAU, ZERO_FUSE, ZERO_SETTLE, ZERO_MOUNT_START, zeroEntrance, zeroLoop, zeroRest, type ZeroFrame,
} from './zero-timeline';

// Actions: idle, finger down, dragging, holding, coasting, tap, refusing, button down, button turn.
type Action = 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8;
type TouchState = {
  action: Action; started: number; touched: number; yaw: number; pitch: number; fuse: number;
  touchX: number; touchY: number; rippleX: number; rippleY: number;
  scale: number; velocity: number; holdable: boolean; snapshot: ZeroFrame; caught: number;
};

function tactile(kind: 'Light' | 'Soft' | 'Rigid' | 'Medium') {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle[kind]).catch(() => {});
}

/** All manual physics are evaluated from an immutable gesture snapshot, never integrated per frame. */
function touchFrame(s: TouchState, now: number, reduced: boolean): ZeroFrame {
  'worklet';
  const f = zeroRest(now, reduced);
  const t = Math.max(0, now - s.started);
  f.yaw = s.yaw; f.pitch = s.pitch;
  if (s.action === 0) {
    f.yaw += reduced ? 0 : 0.17 * Math.sin(TAU * t / 6);
  } else if (s.action === 1 || s.action === 2) {
    f.fuse = s.fuse; f.scale = s.scale;
  } else if (s.action === 3) {
    f.fuse = reduced ? lerp(s.fuse, 0.22, clamp01(t / 0.2)) : s.fuse - 1 + loosen(t);
    f.fuse = Math.max(0.22, f.fuse);
    f.scale = reduced ? 1 : lerp(s.scale, 0.97, bezier(t / 0.22));
  } else if (s.action === 4) {
    f.yaw = reduced ? lerp(s.yaw, nearestRest(s.yaw), bezier(t / 0.3)) : coastYaw(s.yaw, s.velocity, t);
    f.pitch = reduced ? lerp(s.pitch, REST.pitch, bezier(t / 0.3))
      : landedSpring(t, s.pitch, REST.pitch, 160, 22, 0.8);
    f.fuse = lerp(s.fuse, 1, bezier(t / 0.3));
    f.scale = lerp(s.scale, 1, bezier(t / 0.3));
  } else if (s.action === 5 || s.action === 6) {
    const dip = s.action === 5 ? 0.1 : 0;
    const from = s.action === 5 ? 0.55 : s.fuse;
    const release = Math.max(0, t - dip);
    f.fuse = t < dip ? lerp(s.fuse, 0.55, bezier(t / dip))
      : reduced ? lerp(from, 1, progress(release, 0, 0.2))
        : Math.min(1, landedSpring(release, from, 1, 220, 18, 0.7));
    f.scale = reduced ? 1 : landedSpring(t, s.scale, 1, 260, 14, 0.7);
    f.rippleX = s.rippleX; f.rippleY = s.rippleY;
    f.ripple = release > 0 && release < 0.7 ? cubicOut(release / 0.7) : 0;
    f.flash = (s.action === 5 ? 0.3 : 0.35) * (1 - cubicOut(release / 0.6));
    f.glint = glintPulse(release);
  } else if (s.action === 7) {
    f.buttonPress = bezier(t / 0.12);
  } else if (s.action === 8) {
    const p = clamp01(t / 1.1);
    f.yaw = s.yaw + (reduced ? 0 : TAU * bezier(p, 0.77, 0, 0.175, 1));
    f.buttonPress = reduced ? 0 : landedSpring(t, 1, 0, 240, 20, 0.65);
    f.arrow = reduced ? 0 : 4 * Math.sin(Math.PI * clamp01(t / 0.5));
    f.sweep = p < 1 ? lerp(-1, 1, p) : -1; f.glint = glintPulse(t - 0.37);
    for (let i = 0; i < 3; i++) f.twinkle[i] += reduced ? 0 : 0.12 * Math.sin(Math.PI * p);
  }
  // A touch during the entrance gently finishes bringing the surrounding UI into place.
  const catchUp = bezier((now - s.caught) / 0.3);
  f.build = lerp(s.snapshot.build, 1, catchUp);
  f.drain = lerp(s.snapshot.drain, 0, catchUp);
  f.percent = lerp(s.snapshot.percent, 1, catchUp);
  f.dim = lerp(s.snapshot.dim, 0, catchUp);
  f.card = lerp(s.snapshot.card, 1, catchUp);
  f.cardOpacity = lerp(s.snapshot.cardOpacity, 1, catchUp);
  for (let i = 0; i < 3; i++) {
    f.sparkles[i] = lerp(s.snapshot.sparkles[i], 1, catchUp);
    f.sparkleRotation[i] = lerp(s.snapshot.sparkleRotation[i], 0, catchUp);
    f.sparkleOpacity[i] = lerp(s.snapshot.sparkleOpacity[i], 1, catchUp);
  }
  for (let i = 0; i < 5; i++) {
    f.copy[i] = lerp(s.snapshot.copy[i], 1, catchUp);
    f.copyY[i] = lerp(s.snapshot.copyY[i], 0, catchUp);
    f.farDustOpacity[i] *= 1 - f.dim;
  }
  for (let i = 0; i < 3; i++) f.nearDustOpacity[i] *= 1 - f.dim;
  return f;
}

export function useZeroMotion({ autoPlay, reduced, centreX, centreY, unit }: {
  autoPlay: boolean; reduced: boolean; centreX: number; centreY: number; unit: number;
}) {
  const initial = reduced ? { ...zeroRest(0, true), dim: 1 } : zeroEntrance(ZERO_MOUNT_START);
  const frame = useSharedValue(initial);
  const yaw = useSharedValue(initial.yaw); const pitch = useSharedValue(initial.pitch);
  const scale = useSharedValue(initial.scale); const build = useSharedValue(initial.build);
  const drain = useSharedValue(initial.drain); const fuse = useSharedValue(initial.fuse);
  const rippleX = useSharedValue(initial.rippleX); const rippleY = useSharedValue(initial.rippleY);
  const ripple = useSharedValue(initial.ripple); const sweep = useSharedValue(initial.sweep);
  const percent = useSharedValue(initial.percent); const flash = useSharedValue(initial.flash);
  const visible = useSharedValue(0); const ready = useSharedValue(false); const active = useSharedValue(true);
  const clock = useSharedValue(0); const playhead = useSharedValue(ZERO_MOUNT_START);
  // 0: first entrance, 1: loop, 2: interactive, 3: spring to rest before restarting.
  const mode = useSharedValue(0);
  const lastTick = useSharedValue(-1);
  const touch = useSharedValue<TouchState>({
    action: 0, started: 0, touched: 0, yaw: REST.yaw, pitch: REST.pitch, fuse: 1,
    touchX: centreX, touchY: centreY, rippleX: 0, rippleY: 0,
    scale: 1, velocity: 0, holdable: false, snapshot: zeroRest(0, reduced), caught: -1,
  });

  const onReady = useCallback((value: boolean) => {
    ready.set(value);
    visible.set(withTiming(value ? 1 : 0, { duration: reduced ? 300 : 0, reduceMotion: ReduceMotion.Never }));
  }, [ready, reduced, visible]);
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => active.set(state === 'active'));
    return () => subscription.remove();
  }, [active]);

  const catchTouch = useCallback((action: Action, holdable = true, touchX = centreX, touchY = centreY) => {
    'worklet';
    if (!ready.get()) return;
    const f = frame.get(); const now = clock.get();
    touch.set({ action, started: now, touched: now, yaw: f.yaw, pitch: f.pitch, fuse: f.fuse,
      touchX, touchY, rippleX: 0, rippleY: 0,
      scale: f.scale, velocity: 0, holdable, snapshot: f, caught: now });
    mode.set(2);
  }, [centreX, centreY, clock, frame, mode, ready, touch]);

  const release = useCallback((velocity: number, cancelled: boolean, touchX?: number, touchY?: number) => {
    'worklet';
    const s = touch.get(); const f = frame.get(); const now = clock.get();
    const origin = touchRipple(touchX ?? s.touchX, touchY ?? s.touchY, f.yaw, centreX, centreY, unit);
    let action: Action = 4;
    if (!cancelled && s.holdable && s.action === 1) { action = 5; scheduleOnRN(tactile, 'Light'); }
    if (s.action === 3) { action = 6; if (!cancelled) scheduleOnRN(tactile, 'Rigid'); }
    touch.set({ ...s, action, started: now, touched: now, yaw: f.yaw, pitch: f.pitch,
      rippleX: origin.x, rippleY: origin.y,
      fuse: f.fuse, scale: f.scale, velocity: cancelled ? 0 : Math.max(-35, Math.min(35, velocity)) });
  }, [centreX, centreY, clock, frame, touch, unit]);

  const tap = useCallback(() => {
    'worklet'; catchTouch(5); scheduleOnRN(tactile, 'Light');
  }, [catchTouch]);
  const pressButton = useCallback(() => { 'worklet'; catchTouch(7, false); }, [catchTouch]);
  const releaseButton = useCallback((success: boolean) => {
    'worklet';
    const s = touch.get(); const f = frame.get(); const now = clock.get();
    touch.set({ ...s, action: success ? 8 : 4, started: now, touched: now, yaw: f.yaw, pitch: f.pitch,
      fuse: f.fuse, scale: f.scale, velocity: 0 });
    if (success) scheduleOnRN(tactile, 'Medium');
  }, [clock, frame, touch]);

  useFrameCallback((info) => {
    'worklet';
    if (!ready.get() || !active.get()) return;
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    const now = clock.get() + dt; clock.set(now);
    const previous = frame.get(); let f: ZeroFrame;
    const currentMode = mode.get();
    if (currentMode < 2) {
      if (reduced) {
        f = zeroRest(0, true);
        const lightTime = now % 6;
        if (autoPlay && now >= 6 && lightTime < 1.1) f.sweep = lerp(-1, 1, lightTime / 1.1);
      } else {
        const before = playhead.get(); const next = before + dt;
        f = currentMode === 0 ? zeroEntrance(next) : zeroLoop(next); playhead.set(next % 12);
        if (currentMode === 0) {
          if (before < ZERO_FUSE && next >= ZERO_FUSE) scheduleOnRN(tactile, 'Rigid');
          if (before < ZERO_FUSE + 0.16 && next >= ZERO_FUSE + 0.16) scheduleOnRN(tactile, 'Light');
          if (next >= ZERO_SETTLE - 1e-9) {
            mode.set(autoPlay ? 1 : 2);
            if (!autoPlay) {
              const settled = zeroLoop(ZERO_SETTLE);
              const landedAt = now - Math.max(0, next - ZERO_SETTLE);
              f = settled;
              touch.set({ ...touch.get(), yaw: settled.yaw, started: landedAt, touched: landedAt });
            }
          }
        }
      }
    } else if (currentMode === 3) {
      const s = touch.get(); const t = now - s.started;
      f = zeroRest(0, reduced);
      f.yaw = landedSpring(t, s.yaw, nearestRest(s.yaw), 100, 18, 0.7);
      f.pitch = landedSpring(t, s.pitch, REST.pitch, 160, 22, 0.7);
      f.fuse = lerp(s.fuse, 1, bezier(t / 0.3)); f.scale = lerp(s.scale, 1, bezier(t / 0.3));
      if (t >= 0.7) { mode.set(1); playhead.set(0); f = zeroRest(0, reduced); }
    } else {
      let s = touch.get();
      if (s.action === 1 && s.holdable && now - s.started >= 0.18) {
        s = { ...s, action: 3, started: now }; touch.set(s); scheduleOnRN(tactile, 'Soft');
      }
      f = touchFrame(s, now, reduced);
      const busy = s.action === 1 || s.action === 2 || s.action === 3 || s.action === 7;
      const finished = (s.action === 4 && now - s.started >= (reduced ? 0.3 : 1.88))
        || ((s.action === 5 || s.action === 6) && now - s.started >= 0.8)
        || (s.action === 8 && now - s.started >= 1.1);
      if (finished) touch.set({ ...s, action: 0, started: now, yaw: f.yaw, pitch: f.pitch, fuse: 1, scale: 1 });
      if (autoPlay && !busy && now - s.touched >= ZERO_RESUME) {
        mode.set(3); touch.set({ ...s, started: now, yaw: f.yaw, pitch: f.pitch, fuse: f.fuse, scale: f.scale });
      }
      const fast = dt > 0 && Math.abs(f.yaw - previous.yaw) / dt > 1.4;
      if (!reduced && (s.action === 2 || s.action === 4) && fast
        && Math.floor(f.yaw / (Math.PI / 2)) !== Math.floor(previous.yaw / (Math.PI / 2))
        && now - lastTick.get() >= 1 / 12) {
        lastTick.set(now); scheduleOnRN(tactile, 'Light');
      }
    }
    frame.set(f); yaw.set(f.yaw); pitch.set(f.pitch); scale.set(f.scale); build.set(f.build);
    drain.set(f.drain); fuse.set(f.fuse); ripple.set(f.ripple); rippleX.set(f.rippleX); rippleY.set(f.rippleY); sweep.set(f.sweep);
    percent.set(f.percent); flash.set(f.flash);
  });

  // The pan's three steps are named worklets so the recognizer stays a thin shell around them.
  const panBegin = useCallback((x: number, y: number) => {
    'worklet';
    const dx = (x - centreX) / unit; const dy = (y - centreY) / unit;
    catchTouch(1, dx * dx / 0.9 + dy * dy / 1.6 < 1, x, y);
  }, [catchTouch, centreX, centreY, unit]);
  const panChange = useCallback((x: number, y: number, translationX: number, translationY: number) => {
    'worklet';
    const s = touch.get();
    const travel = Math.hypot(translationX, translationY);
    if (travel < 6 && s.action !== 2) {
      touch.set({ ...s, touchX: x, touchY: y });
      return;
    }
    const f = s.snapshot;
    touch.set({ ...s, touchX: x, touchY: y, action: 2, touched: clock.get(), holdable: false,
      yaw: f.yaw + translationX / ZERO_DRAG_POINTS,
      pitch: rubberPitch(f.pitch - translationY / (ZERO_DRAG_POINTS * 3)),
      fuse: 1, scale: 1 });
  }, [clock, touch]);
  const panFinalize = useCallback((velocityX: number, success: boolean, x: number, y: number) => {
    'worklet';
    release(velocityX / ZERO_DRAG_POINTS, !success, x, y);
  }, [release]);

  // One recognizer catches an in-flight spin immediately; manipulation starts after 6 pt.
  // Classifying the stationary 180 ms hold in the frame clock avoids competing recognizers.
  const gesture = useMemo(() => Gesture.Pan().minDistance(0).maxPointers(1)
    .onBegin((event) => { panBegin(event.x, event.y); })
    .onChange((event) => { panChange(event.x, event.y, event.translationX, event.translationY); })
    .onFinalize((event, success) => { panFinalize(event.velocityX, success, event.x, event.y); }),
  [panBegin, panChange, panFinalize]);
  const buttonGesture = useMemo(() => Gesture.Tap().maxDuration(20000).maxDistance(24)
    .onBegin(() => { pressButton(); })
    .onEnd((_event, success) => { if (success) releaseButton(true); })
    .onFinalize((_event, success) => { if (!success) releaseButton(false); }), [pressButton, releaseButton]);
  const activateHero = useCallback(() => scheduleOnUI(tap), [tap]);
  const activateButton = useCallback(() => {
    scheduleOnUI(pressButton); scheduleOnUI(releaseButton, true);
  }, [pressButton, releaseButton]);

  return { frame, yaw, pitch, scale, build, drain, fuse, ripple, rippleX, rippleY, sweep, percent, flash,
    visible, onReady, gesture, buttonGesture, activateHero, activateButton };
}
export type ZeroMotion = ReturnType<typeof useZeroMotion>;
