import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState } from 'react-native';
import { Gesture } from 'react-native-gesture-handler';
import { useAnimatedReaction, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import type { SheetLayout } from './sheet-theme';
import {
  bezier, bounceAt, clamp, mix, sheetContentFade, openingStage, progress, releaseEase, rollSheetLabels, sheetEase,
  sheetHeight, sheetLoop, sheetRest, spring, stepEase, CLOSE_DURATION, STEP_DURATION, type SheetFrame,
} from './sheet-timeline';

type Action = 'idle' | 'pan' | 'step' | 'settle' | 'open' | 'close' | 'iconHold' | 'iconRelease'
  | 'buttonHold' | 'puckHold' | 'return';
type Touch = {
  action: Action; snapshot: SheetFrame; started: number; touched: number;
  target: number; duration: number; speed: number; amplitude: number;
  axis: number; x: number; y: number; lastX: number; lastTime: number; lean: number; icon: boolean;
};
function tactile(kind: 'Light' | 'Soft' | 'Medium') {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle[kind]).catch(() => {});
}
function touchState(f: SheetFrame, now: number, action: Action): Touch {
  'worklet';
  return { action, snapshot: f, started: now, touched: now, target: Math.round(f.position), duration: STEP_DURATION,
    speed: 0, amplitude: 1, axis: 0, x: 0, y: 0, lastX: 0, lastTime: now, lean: f.lean, icon: false };
}
function manualFrame(s: Touch, now: number, reduced: boolean): SheetFrame {
  'worklet';
  const t = Math.max(0, now - s.started); const a = s.snapshot; const f = { ...a };
  f.markerOpacity = a.markerOpacity * (1 - progress(t, 0, 0.2));
  f.markerPress = a.markerPress * (1 - progress(t, 0, 0.12));
  f.buttonPress = spring(t, a.buttonPress, 0); f.puckPress = spring(t, a.puckPress, 0);
  f.press = clamp(spring(t, a.press, 0));
  f.bounce = reduced ? 0 : a.bounce * (1 - bezier(t / 0.2));
  f.lean = reduced ? 0 : spring(t, a.lean, 0, 260, 26);
  f.edge = spring(t, a.edge, 0, 260, 26);
  if (s.action === 'pan') {
    if (s.axis === 1) {
      const raw = a.position - s.x / 280;
      f.position = clamp(raw, 0, 3); f.edge = (raw - f.position) * 280 * 0.2;
      f.lean = reduced ? 0 : s.lean;
      if (!reduced && f.edge !== 0) f.bounce = clamp(f.edge / 56, -1, 1) * 0.2;
    } else if (s.axis === 2) f.drag = a.drag + (s.y > 0 ? s.y : s.y * 0.15);
    if (s.icon && s.axis === 0) f.press = mix(a.press, 1, bezier(t / 0.11));
  } else if (s.action === 'step' || s.action === 'settle') {
    const p = progress(t, 0, s.duration);
    const e = s.action === 'step' ? stepEase(p) : releaseEase(p, s.target - a.position, s.speed, s.duration);
    f.position = mix(a.position, s.target, e);
    f.drag = spring(t, a.drag, 0, 260, 26, s.axis === 2 ? s.speed : 0);
    if (t >= s.duration) f.bounce = reduced ? 0 : bounceAt(t - s.duration, s.amplitude);
  } else if (s.action === 'open' || s.action === 'close') {
    const opening = s.action === 'open'; const target = opening ? 1 : 0;
    const e = reduced ? progress(t, 0, 0.2) : s.speed > 0
      ? releaseEase(progress(t, 0, s.duration), 1, s.speed, s.duration) : sheetEase(progress(t, 0, s.duration));
    f.open = mix(a.open, target, e); f.drag = mix(a.drag, 0, e);
    f.contents = reduced ? mix(a.contents, target, e) : sheetContentFade(f.open);
    f.stage = reduced ? f.open : opening ? mix(a.stage, 1, openingStage(t)) : a.stage;
    rollSheetLabels(f, a, progress(t, 0, 0.26), opening);
    f.running = opening && t >= (reduced ? 0.2 : 0.6) ? 1 : 0;
    if (!opening && e === 1) { f.position = 0; f.stage = 0; }
  } else if (s.action === 'buttonHold') f.buttonPress = mix(a.buttonPress, 1, bezier(t / 0.12));
  else if (s.action === 'puckHold') f.puckPress = mix(a.puckPress, 1, bezier(t / 0.12));
  else if (s.action === 'iconHold') f.press = mix(a.press, 1, bezier(t / 0.11));
  else if (s.action === 'iconRelease') f.bounce = reduced ? 0 : bounceAt(t, 0.6);
  else if (s.action === 'return') {
    const p = bezier(t / 0.6);
    f.position = mix(a.position, 0, p); f.open = mix(a.open, 1, p);
    f.contents = reduced ? mix(a.contents, 1, p) : sheetContentFade(f.open);
    f.stage = reduced ? f.open : mix(a.stage, 1, openingStage(t));
    rollSheetLabels(f, a, progress(t, 0, 0.6), true);
    f.drag = mix(a.drag, 0, p); f.edge = mix(a.edge, 0, p); f.running = t >= 0.6 ? 1 : 0;
  }
  return f;
}

export function useSheetMotion({ layout, autoPlay, reduced, ready }: {
  layout: SheetLayout; autoPlay: boolean; reduced: boolean; ready: boolean;
}) {
  const { width, height, stageTop, stageHeight, sheetShort, sheetTall, button } = layout;
  const frame = useSharedValue(sheetRest());
  const clock = useSharedValue(0); const position = useSharedValue(0); const running = useSharedValue(1);
  const bounce = useSharedValue(0); const lean = useSharedValue(0); const press = useSharedValue(0);
  const visible = useSharedValue(0); const gate = useSharedValue(false); const delay = useSharedValue(0);
  const active = useSharedValue(true); const freshFrame = useSharedValue(false); const mode = useSharedValue(autoPlay ? 0 : 1); const epoch = useSharedValue(0);
  const touch = useSharedValue(touchState(sheetRest(), 0, 'idle'));
  const touching = useSharedValue(false);
  const [semantics, setSemantics] = useState({ step: 0, open: true, label: 1, visible: false });
  useEffect(() => { gate.set(ready); if (!ready) { visible.set(0); delay.set(0); } }, [ready, gate, visible, delay]);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => {
      active.set(state === 'active'); freshFrame.set(true);
    });
    return () => sub.remove();
  }, [active, freshFrame]);
  // Discrete accessibility changes cross the bridge only when a label/step/visibility changes.
  useAnimatedReaction(() => {
    const f = frame.get();
    return { step: Math.round(f.position), open: f.open > 0.001, label: f.labelOpen < 0.5 ? 0 : f.position < 2.5 ? 1 : 2, visible: visible.get() === 1 };
  }, (next, previous) => {
    if (!previous || next.step !== previous.step || next.open !== previous.open
      || next.label !== previous.label || next.visible !== previous.visible) scheduleOnRN(setSemantics, next);
  });

  const catchTouch = useCallback((action: Action) => {
    'worklet';
    if (!visible.get()) return;
    touch.set(touchState(frame.get(), clock.get(), action)); mode.set(1);
  }, [clock, frame, mode, touch, visible]);
  // Passive simultaneous observer: even a touch on the page's empty space interrupts autoplay.
  const ambientDown = useCallback(() => {
    'worklet';
    if (!visible.get()) return;
    touching.set(true);
    if (mode.get() === 0 || touch.get().action === 'return') catchTouch('idle');
  }, [catchTouch, mode, touch, touching, visible]);
  const ambientUp = useCallback(() => {
    'worklet'; touching.set(false);
    touch.set({ ...touch.get(), touched: clock.get() });
  }, [clock, touch, touching]);
  const commitSheet = useCallback((opening: boolean, velocity = 0) => {
    'worklet';
    if (!visible.get()) return;
    const f = frame.get(); const s = touchState(f, clock.get(), opening ? 'open' : 'close');
    const distance = Math.max(1, sheetHeight(f.position, sheetShort, sheetTall) * f.open - f.drag);
    s.duration = reduced ? 0.2 : opening ? 0.56 : clamp(distance / Math.max(velocity, distance / CLOSE_DURATION), 0.22, CLOSE_DURATION);
    s.speed = !opening && velocity > 0 ? velocity / distance : 0;
    touch.set(s); mode.set(1); scheduleOnRN(tactile, opening ? 'Light' : 'Medium');
  }, [clock, frame, mode, reduced, sheetShort, sheetTall, touch, visible]);
  const iconDown = useCallback(() => {
    'worklet';
    catchTouch('iconHold');
  }, [catchTouch]);
  const iconUp = useCallback(() => {
    'worklet';
    const s = touch.get();
    if (s.action === 'iconHold' || (s.action === 'pan' && s.icon && s.axis === 0)) {
      const f = manualFrame(s, clock.get(), reduced);
      frame.set(f); touch.set(touchState(f, clock.get(), 'iconRelease')); scheduleOnRN(tactile, 'Soft');
    }
  }, [clock, frame, reduced, touch]);
  const panBegin = useCallback((x: number, y: number) => {
    'worklet';
    if (!visible.get()) return;
    const f = frame.get(); const h = sheetHeight(f.position, sheetShort, sheetTall);
    const travel = reduced ? 0 : (1 - f.open) * h + f.drag;
    if (f.open <= 0 || x < 0 || x > width || y < height - h + 56 + travel || y >= button.y) return;
    catchTouch('pan');
    const s = touch.get();
    // Public handler takes screen-container coordinates, including the below-icon swipe lane.
    const cy = stageTop + stageHeight / 2 + travel + (reduced ? 0 : 24 * (1 - f.stage));
    touch.set({ ...s, icon: Math.abs(x - width / 2) <= 90 && Math.abs(y - cy) <= 90 });
  }, [button.y, catchTouch, frame, height, reduced, sheetShort, sheetTall, stageHeight, stageTop, touch, visible, width]);
  const panChange = useCallback((translationX: number, translationY: number) => {
    'worklet';
    const s = touch.get(); if (s.action !== 'pan') return;
    const axis = s.axis || (Math.hypot(translationX, translationY) < 8 ? 0 : Math.abs(translationX) > Math.abs(translationY) ? 1 : 2);
    const now = clock.get(); const dt = Math.max(1 / 240, now - s.lastTime);
    const velocity = (translationX - s.lastX) / dt;
    const lowpass = 1 - Math.exp(-dt / 0.065);
    touch.set({ ...s, axis, x: translationX, y: translationY, lastX: translationX, lastTime: now,
      lean: mix(s.lean, clamp(velocity / 1000, -1, 1), lowpass), touched: now });
  }, [clock, touch]);
  const panFinalize = useCallback((velocityX: number, velocityY: number) => {
    'worklet';
    const s = touch.get(); if (s.action !== 'pan') return;
    // Sample the latest input, even when an up event arrives before the next display frame.
    const f = manualFrame(s, clock.get(), reduced); frame.set(f);
    if (s.axis === 2 && (f.drag > 120 || velocityY > 900)) { commitSheet(false, Math.max(0, velocityY)); return; }
    const next = touchState(f, clock.get(), 'settle'); next.axis = s.axis;
    if (s.axis === 1) {
      const base = Math.round(s.snapshot.position);
      const direction = Math.abs(velocityX) > 500 ? -Math.sign(velocityX) : -Math.sign(s.x);
      const commits = Math.abs(s.x / 280) > 0.35 || Math.abs(velocityX) > 500;
      next.target = clamp(base + (commits ? direction : 0), 0, 3);
      next.speed = -velocityX / 280;
      next.duration = reduced ? 0.45 : clamp(Math.abs(next.target - f.position) * 280 / Math.max(Math.abs(velocityX), 420), 0.28, 0.52);
      next.amplitude = clamp(Math.abs(velocityX) / 1200, 0.25, 1);
      if (next.target !== base) scheduleOnRN(tactile, 'Light');
    } else { next.speed = velocityY; next.duration = reduced ? 0.45 : 0.52; next.amplitude = 0; }
    touch.set(next);
  }, [clock, commitSheet, frame, reduced, touch]);
  const panCancel = useCallback(() => {
    'worklet';
    const s = touch.get(); if (s.action !== 'pan') return;
    const next = touchState(frame.get(), clock.get(), 'settle');
    next.duration = reduced ? 0.45 : 0.4; next.amplitude = 0; touch.set(next);
  }, [clock, frame, reduced, touch]);
  const buttonDown = useCallback(() => { 'worklet'; catchTouch('buttonHold'); }, [catchTouch]);
  const buttonUp = useCallback((inside: boolean) => {
    'worklet';
    if (touch.get().action !== 'buttonHold' || !visible.get()) return;
    const f = manualFrame(touch.get(), clock.get(), reduced); frame.set(f);
    if (!inside) { touch.set(touchState(f, clock.get(), 'idle')); return; }
    if (f.open < 0.5) { commitSheet(true); return; }
    if (f.position >= 2.5) { commitSheet(false); return; }
    const s = touchState(f, clock.get(), 'step'); s.target = Math.min(3, Math.round(f.position) + 1);
    s.duration = reduced ? 0.45 : STEP_DURATION; touch.set(s); scheduleOnRN(tactile, 'Light');
  }, [clock, commitSheet, frame, reduced, touch, visible]);
  const closeTap = useCallback(() => { 'worklet'; commitSheet(false); }, [commitSheet]);
  const infoTap = useCallback(() => { 'worklet'; commitSheet(true); }, [commitSheet]);
  const puckDown = useCallback((puck: number) => {
    'worklet'; catchTouch('puckHold');
    const s = touch.get(); touch.set({ ...s, snapshot: { ...s.snapshot, puck } });
  }, [catchTouch, touch]);
  const puckUp = useCallback((inside: boolean, puck: number) => {
    'worklet';
    if (touch.get().action !== 'puckHold') return;
    frame.set(manualFrame(touch.get(), clock.get(), reduced));
    if (inside && puck === 2) infoTap();
    else if (inside && frame.get().open > 0) closeTap();
    else touch.set(touchState(frame.get(), clock.get(), 'idle'));
  }, [clock, closeTap, frame, infoTap, reduced, touch]);

  useFrameCallback((info) => {
    if (!gate.get() || !active.get()) return;
    // Ignore only the foreground boundary; ordinary dropped frames keep real elapsed time.
    if (freshFrame.get()) { freshFrame.set(false); return; }
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    if (delay.get() < 0.3) { delay.set(delay.get() + dt); return; }
    if (!visible.get()) { visible.set(1); return; }
    const now = clock.get() + dt; clock.set(now);
    let f: SheetFrame;
    if (mode.get() === 0) f = sheetLoop(now - epoch.get(), reduced);
    else {
      let s = touch.get(); f = manualFrame(s, now, reduced);
      const held = touching.get() || s.action === 'pan' || s.action === 'buttonHold' || s.action === 'iconHold' || s.action === 'puckHold';
      if (s.action === 'return' && now - s.started >= 0.6) {
        mode.set(0); epoch.set(now); f = sheetRest();
      } else if (autoPlay && !held && s.action !== 'return' && now - s.touched >= 3.5) {
        s = touchState(f, now, 'return'); touch.set(s);
      }
    }
    frame.set(f); position.set(f.position); running.set(f.running);
    bounce.set(f.bounce); lean.set(f.lean); press.set(f.press);
  });

  const ambientGesture = useMemo(() => Gesture.Manual()
    .onTouchesDown(() => { ambientDown(); })
    .onTouchesUp((_e, manager) => { ambientUp(); manager.fail(); })
    .onTouchesCancelled((_e, manager) => { ambientUp(); manager.fail(); }), [ambientDown, ambientUp]);
  const panGesture = useMemo(() => Gesture.Pan().minDistance(8).maxPointers(1).simultaneousWithExternalGesture(ambientGesture)
    .onBegin((e) => {
      const f = frame.get(); const h = sheetHeight(f.position, sheetShort, sheetTall);
      const travel = reduced ? 0 : (1 - f.open) * h + f.drag;
      panBegin(e.x, e.y + height - h + 56 + travel);
    })
    .onUpdate((e) => { panChange(e.translationX, e.translationY); })
    .onTouchesUp(() => { iconUp(); })
    .onTouchesCancelled(() => { panCancel(); })
    .onFinalize((e, success) => { if (success) panFinalize(e.velocityX, e.velocityY); else panCancel(); }),
  [ambientGesture, frame, height, iconUp, panBegin, panCancel, panChange, panFinalize, reduced, sheetShort, sheetTall]);
  const buttonGesture = useMemo(() => Gesture.Tap().maxDuration(600000).maxDistance(28).simultaneousWithExternalGesture(ambientGesture)
    .onBegin(() => { buttonDown(); })
    .onEnd((_e, success) => { if (success) buttonUp(true); })
    .onFinalize((_e, success) => { if (!success) buttonUp(false); }), [ambientGesture, buttonDown, buttonUp]);
  const activateButton = useCallback(() => { scheduleOnUI(buttonDown); scheduleOnUI(buttonUp, true); }, [buttonDown, buttonUp]);
  const activateIcon = useCallback(() => { scheduleOnUI(iconDown); scheduleOnUI(iconUp); }, [iconDown, iconUp]);
  const activatePuck = useCallback((puck: number) => { scheduleOnUI(puckDown, puck); scheduleOnUI(puckUp, true, puck); }, [puckDown, puckUp]);
  return { frame, clock, position, running, bounce, lean, press, visible, semantics,
    panBegin, panChange, panFinalize, iconDown, iconUp, buttonDown, buttonUp, closeTap, infoTap,
    puckDown, puckUp, ambientGesture, panGesture, buttonGesture, activateButton, activateIcon, activatePuck };
}
export type SheetMotion = ReturnType<typeof useSheetMotion>;
