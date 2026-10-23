// UI-thread ownership ends at shared values; only haptics and accessibility changes reach RN.
import * as Haptics from 'expo-haptics';
import { useCallback, useMemo, useState } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import { useFrameCallback, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import { EVENT, M } from './supercharge-constants';
import { act, initialState, step, type Action, type Config, type State } from './supercharge-sim';
import { emptyLightning } from './supercharge-lightning';
import type { SuperchargeValues } from './supercharge-types';
function haptics(events: number) {
  if (events & EVENT.selection) void Haptics.selectionAsync().catch(() => {});
  if (events & EVENT.light) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  if (events & EVENT.medium) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  if (events & EVENT.rigid) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
  if (events & EVENT.soft) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  if (events & EVENT.heavy) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy).catch(() => {});
  if (events & EVENT.success) void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}
export function useSupercharge(config: Config, frame: { x: number; y: number; k: number }, active: SharedValue<boolean>) {
  const state = useSharedValue(initialState());
  const clock = useSharedValue(0), card = useSharedValue([0, 0, 0, 1]), sink = useSharedValue(0);
  const energy = useSharedValue(0), glow = useSharedValue(0), glyph = useSharedValue(0.12), glyphScale = useSharedValue(1);
  const ring = useSharedValue(0), ringLit = useSharedValue(0), flash = useSharedValue(0), tree = useSharedValue(0);
  const dim = useSharedValue(1);
  const treeGlow = useSharedValue(0), pulse = useSharedValue(-1), shock = useSharedValue(0), shaft = useSharedValue([240, 0]);
  const touch = useSharedValue([201, 392, 0]), meter = useSharedValue(0), charged = useSharedValue(0), caption = useSharedValue(0);
  const lightning = useSharedValue(emptyLightning()), sparks = useSharedValue<number[]>([]);
  const reported = useSharedValue(-1);
  const reportedAt = useSharedValue(-1);
  const [percent, setPercent] = useState(0);
  const report = useCallback((next: number) => setPercent(next), []);
  const publish = useCallback((s: State) => {
    'worklet';
    state.set(s); clock.set(s.clock); card.set(s.card); sink.set(s.sink);
    energy.set(s.energy); glow.set(s.glow); glyph.set(s.glyph); glyphScale.set(s.glyphScale);
    ring.set(s.ring); ringLit.set(s.ringLit); dim.set(s.dim); flash.set(s.flash); tree.set(s.tree); treeGlow.set(s.treeGlow);
    pulse.set(s.pulse); shock.set(s.shock); shaft.set(s.shaft); touch.set(s.touch); meter.set(s.meter);
    charged.set(s.charged); caption.set(s.caption); lightning.set(s.lightning); sparks.set(s.sparks);
    if (s.events) scheduleOnRN(haptics, s.events);
    const next = Math.round(s.energy * 100);
    if (reported.get() !== next && (next === 0 || next === 100 || s.clock < reportedAt.get() || s.clock - reportedAt.get() >= M.accessibilityInterval)) {
      reported.set(next); reportedAt.set(s.clock); scheduleOnRN(report, next);
    }
  }, [caption, card, charged, clock, dim, energy, flash, glow, glyph, glyphScale, lightning, meter, pulse, report, reported, reportedAt, ring, ringLit, shaft, shock, sink, sparks, state, touch, tree, treeGlow]);
  const reduced = config.reduced, autoPlay = config.autoPlay;
  const input = useCallback((action: Action) => {
    'worklet';
    if (active.get()) publish(act(state.get(), action, { reduced, autoPlay }));
  }, [active, autoPlay, publish, reduced, state]);
  useFrameCallback((f) => {
    'worklet';
    if (active.get()) publish(step(state.get(), (f.timeSincePreviousFrame ?? 0) / 1000, { reduced, autoPlay }));
  });
  const x = frame.x, y = frame.y, k = frame.k;
  // Capture data and the action worklet, never the returned object that contains this gesture.
  // A finger that never moves leaves a pan un-activated, and its finalize reports failure: that is
  // still a release. So the touch callbacks say what the finger did; finalize only catches a touch
  // the system took away (after a real release it finds nothing down and does nothing).
  const gesture = useMemo(() => Gesture.Pan().minDistance(0).maxPointers(1)
    .onBegin((e) => { input({ type: 'down', x: (e.x - x) / k, y: (e.y - y) / k }); })
    .onTouchesMove((e) => {
      const finger = e.changedTouches[0];
      if (finger) input({ type: 'move', x: (finger.x - x) / k, y: (finger.y - y) / k });
    })
    .onTouchesUp(() => { input({ type: 'up' }); })
    .onFinalize(() => { input({ type: 'cancel' }); }), [input, k, x, y]);
  const activate = useCallback(() => scheduleOnUI(input, { type: 'activate' }), [input]);
  const values: SuperchargeValues = { clock, card, sink, energy, glow, glyph, glyphScale, ring, ringLit, dim, flash,
    tree, treeGlow, pulse, shock, shaft, touch, meter, charged, caption, lightning, sparks };
  return { values, gesture, activate, valueText: percent === 0 ? 'Empty' : percent === 100 ? 'Fully charged' : `${percent} percent` };
}
