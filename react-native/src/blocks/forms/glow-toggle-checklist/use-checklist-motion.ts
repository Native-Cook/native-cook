import * as Haptics from 'expo-haptics';
import { useCallback, useState } from 'react';
import { useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { clearEvents, createState, step, type ChecklistEvents, type ChecklistTouch } from './checklist-sim';
import type { ChecklistMotion } from './checklist-theme';

function emitHaptics(events: ChecklistEvents) {
  // Event-only bridge work. Scripted commits never set these haptic flags.
  if (events.cap) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  for (let i = 0; i < 4; i++) {
    if (events.onMask & (1 << i)) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid);
    if (events.offMask & (1 << i)) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft);
  }
  if (events.success) void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
}

export function useChecklistMotion(autoPlay: boolean) {
  const reduced = useReducedMotion();
  const simulation = useSharedValue(createState(autoPlay, reduced));
  const ready = useSharedValue(false);
  const touches = useSharedValue<ChecklistTouch[]>([]);
  const touchClock = useSharedValue(0);
  const opacity = useSharedValue(reduced ? 0 : 1);
  const clock = useSharedValue(0);
  const present = useSharedValue(0);
  const letters = useSharedValue([0, 0, 0, 0, 0, 0, 0]);
  const open = useSharedValue(0);
  const press = useSharedValue(0);
  const lead = useSharedValue([0, 0, 0, 0]);
  const trail = useSharedValue([0, 0, 0, 0]);
  const hold = useSharedValue([0, 0, 0, 0]);
  const fill = useSharedValue(0);
  const hue = useSharedValue(0);
  const tip = useSharedValue(0);
  const surge = useSharedValue(0);
  const flash = useSharedValue(0);
  const glare = useSharedValue(-0.3);
  const still = useSharedValue(reduced ? 1 : 0);
  const [canvasReady, setCanvasReady] = useState(false);
  const [accessibility, setAccessibility] = useState({ expanded: false, checked: [false, false, false, false] });
  const onReady = useCallback(() => { ready.set(true); setCanvasReady(true); }, [ready]);
  const changed = useCallback((expanded: boolean, checked: boolean[], events: ChecklistEvents) => {
    setAccessibility({ expanded, checked });
    emitHaptics(events);
  }, []);

  useFrameCallback((frame) => {
    'worklet';
    if (!ready.get()) return;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 0) / 1000, 1 / 30);
    const s = simulation.get();
    const remainder = s.remainder;
    clearEvents(s);
    const pending = touches.get();
    // Preserve down/up pairs even if both arrive between two overloaded frames.
    for (let i = 0; i < pending.length; i++) step(s, pending[i], 0);
    if (pending.length) touches.set([]);
    step(s, null, dt);
    simulation.set(s);
    touchClock.set(touchClock.get() + dt + remainder - s.remainder);
    clock.set(s.clock); present.set(s.present); letters.set([...s.letters]);
    open.set(s.open); press.set(s.press); fill.set(s.fill); hue.set(s.hue);
    tip.set(Math.max(0, s.tip)); surge.set(s.surge); flash.set(s.flash);
    glare.set(s.glare); opacity.set(s.opacity);
    const nextLead: number[] = []; const nextTrail: number[] = []; const nextHold: number[] = [];
    const checked: boolean[] = [];
    for (let i = 0; i < 4; i++) {
      nextLead.push(s.knobs[i].lead); nextTrail.push(s.knobs[i].trail);
      nextHold.push(s.knobs[i].hold); checked.push(s.knobs[i].on);
    }
    lead.set(nextLead); trail.set(nextTrail); hold.set(nextHold);
    if (s.events.changed) scheduleOnRN(changed, !!s.openTarget, checked, s.events);
  });

  const activateUI = useCallback((control: number) => {
    'worklet';
    touches.set([...touches.get(), { control, down: false, dx: 0, velocity: 0, k: 1, activate: true }]);
  }, [touches]);
  const activate = useCallback((control: number) => {
    scheduleOnUI(activateUI, control);
  }, [activateUI]);
  const motion: ChecklistMotion = { clock, present, letters, open, press, lead, trail, hold,
    fill, hue, tip, surge, flash, glare, still };
  return { motion, opacity, touches, touchClock, onReady, canvasReady, accessibility, activate };
}
