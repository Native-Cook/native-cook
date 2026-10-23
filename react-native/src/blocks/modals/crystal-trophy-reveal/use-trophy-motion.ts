// One frame owner publishes the simulation; gestures only issue serialisable commands.
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import { useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { type StageValues, type TrophyFrame } from './trophy-contract';
import { advanceTrophy } from './trophy-demo';
import {
  close, dragTo, EVENT, initialState, interrupt, pressClose, pressShare, release, reveal, stagePoint, tapAt, touchDown,
  type Phase,
} from './trophy-timeline';

export type TrophyAction =
  | { type: 'begin'; x: number; y: number }
  | { type: 'move'; x: number; y: number; velocity: number }
  | { type: 'end'; cancelled?: boolean }
  | { type: 'access'; target: 'share' | 'close' | 'curtain' | 'trophy'; delta?: number };

function reportHaptics(events: number): void {
  if (events & EVENT.soft) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  if (events & EVENT.medium) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  if (events & EVENT.light) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  if (events & EVENT.selection) void Haptics.selectionAsync().catch(() => {});
  if (events & EVENT.success) void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}
export function trophyHitBox(frame: TrophyFrame) {
  return { x: frame.originX - frame.unit * 1.3 - 24, y: frame.originY - frame.unit * 3.05 - 24,
    width: frame.unit * 2.6 + 48, height: frame.unit * 3.65 + 48 };
}
export function useTrophyMotion({ frame, ready, autoPlay, reduced, onClose, onShare }: {
  frame: TrophyFrame; ready: boolean; autoPlay: boolean; reduced: boolean; onClose?: () => void; onShare?: () => void;
}) {
  const state = useSharedValue(initialState(autoPlay, reduced));
  const active = useSharedValue(false);
  const warmFrames = useSharedValue(0);
  const visible = useSharedValue(0);
  const canvasSize = useSharedValue({ width: 0, height: 0 });
  const [phase, setPhase] = useState<Phase>(autoPlay ? 'open' : 'closed');
  const reportedPhase = useSharedValue<Phase>(autoPlay ? 'open' : 'closed');
  const time = useSharedValue(0);
  const open = useSharedValue(autoPlay ? 1 : 0);
  const hem = useSharedValue(autoPlay ? 1 : 0);
  const shiver = useSharedValue(0); const seam = useSharedValue(autoPlay ? 0 : 0.37);
  const lamp = useSharedValue(autoPlay ? 1 : 0); const beam = useSharedValue(autoPlay ? 1 : 0);
  const under = useSharedValue(0); const rise = useSharedValue(autoPlay ? 1 : 0);
  const yaw = useSharedValue(0); const etch = useSharedValue(autoPlay ? 1 : 0);
  const touch = useSharedValue(0); const flash = useSharedValue(0);
  const touchX = useSharedValue(0); const touchY = useSharedValue(1.8);
  const pointer = useSharedValue({ mode: 'background', x: 0, y: 0, startX: 0, startY: 0, velocity: 0, active: false });
  const values: StageValues = { time, open, hem, shiver, seam, lamp, beam, under, rise, yaw, etch, touch, flash, touchX, touchY };
  const { originX, originY, unit, width } = frame;
  const hit = trophyHitBox(frame);
  const share = frame.share; const closeBox = frame.close;

  const report = useCallback((events: number, nextPhase: Phase, changed: boolean) => {
    reportHaptics(events);
    if (changed) setPhase(nextPhase);
    if (events & EVENT.closed) onClose?.();
    if (events & EVENT.shared) onShare?.();
  }, [onClose, onShare]);

  useEffect(() => {
    active.set(ready);
    if (!ready) warmFrames.set(0);
    return () => { active.set(false); };
  }, [active, ready, warmFrames]);

  const act = useCallback((action: TrophyAction) => {
    'worklet';
    if (!active.get() || visible.get() === 0) return;
    const s = { ...state.get() };
    const p = { ...pointer.get() };
    if (action.type === 'begin') {
      if (p.active) return;
      interrupt(s, true);
      p.x = action.x; p.y = action.y; p.startX = p.x; p.startY = p.y; p.velocity = 0; p.active = true;
      p.mode = 'background';
      if (s.phase === 'closed') p.mode = 'curtain';
      else if (s.phase === 'open') {
        if (p.x >= share.x && p.x <= share.x + share.width && p.y >= share.y && p.y <= share.y + share.height) p.mode = 'share';
        else if (p.x >= closeBox.x - 2 && p.x <= closeBox.x + 42 && p.y >= closeBox.y - 2 && p.y <= closeBox.y + 42) p.mode = 'close';
        else if (p.x >= hit.x && p.x <= hit.x + hit.width && p.y >= hit.y && p.y <= hit.y + hit.height) p.mode = 'trophy';
      }
      if (p.mode === 'trophy' || p.mode === 'curtain') {
        const point = stagePoint(s, p.x, p.y, frame);
        touchDown(s, (point.x - originX) / unit, (originY - point.y) / unit);
      }
      if (p.mode === 'share') pressShare(s, true, false, (p.x - share.x) / share.width, (p.y - share.y) / share.height);
      if (p.mode === 'close') pressClose(s, true, false, (p.x - closeBox.x - 2) / 36, (p.y - closeBox.y - 2) / 36);
    } else if (action.type === 'move' && p.active) {
      p.x = action.x; p.y = action.y; p.velocity = action.velocity;
      if (p.mode === 'trophy' || p.mode === 'curtain') {
        const point = stagePoint(s, p.x, p.y, frame);
        dragTo(s, p.x - p.startX, p.y - p.startY, (point.x - originX) / unit, (originY - point.y) / unit, width);
      }
      if (p.mode === 'share') pressShare(s, true, false, (p.x - share.x) / share.width, (p.y - share.y) / share.height);
      if (p.mode === 'close') pressClose(s, true, false, (p.x - closeBox.x - 2) / 36, (p.y - closeBox.y - 2) / 36);
    } else if (action.type === 'end' && p.active) {
      interrupt(s, false); p.active = false;
      if (p.mode === 'trophy' || p.mode === 'curtain') release(s, p.velocity, action.cancelled);
      if (p.mode === 'share') pressShare(s, false, !action.cancelled && p.x >= share.x && p.x <= share.x + share.width && p.y >= share.y && p.y <= share.y + share.height);
      if (p.mode === 'close') pressClose(s, false, !action.cancelled && p.x >= closeBox.x - 2 && p.x <= closeBox.x + 42 && p.y >= closeBox.y - 2 && p.y <= closeBox.y + 42);
    } else if (action.type === 'access') {
      interrupt(s, false);
      if (action.target === 'curtain') reveal(s);
      if (action.target === 'close') close(s);
      if (action.target === 'share') pressShare(s, false, true);
      if (action.target === 'trophy' && s.phase === 'open') {
        if (action.delta) {
          touchDown(s, 0, 1.8); dragTo(s, action.delta / 0.0062, 0, 0, 1.8, width);
          s.maxDrag = 20; release(s, 0);
        } else tapAt(s, 0, 1.8);
      }
    }
    pointer.set(p); state.set(s);
  }, [active, visible, state, pointer, share, closeBox, hit, frame, originX, originY, unit, width]);

  useFrameCallback((tick) => {
    'worklet';
    if (!active.get() || canvasSize.get().width <= 0) { visible.set(0); return; }
    if (warmFrames.get() < 2) { warmFrames.set(warmFrames.get() + 1); return; }
    visible.set(1);
    const previous = state.get();
    const s = advanceTrophy(previous, (tick.timeSincePreviousFrame ?? 16.667) / 1000, width);
    // Re-map a held finger each frame too: a camera punch must not displace its light.
    const p = pointer.get();
    if (p.active && p.mode === 'trophy') {
      const point = stagePoint(s, p.x, p.y, frame);
      dragTo(s, p.x - p.startX, p.y - p.startY, (point.x - originX) / unit, (originY - point.y) / unit, width);
    }
    // Scripted stage drift is phase-locked too; interactive time stays monotonic.
    time.set(reduced ? 0 : autoPlay && !s.paused ? s.demoTime : s.clock);
    open.set(s.open); hem.set(s.hem); shiver.set(s.shiver); seam.set(s.seam);
    lamp.set(s.lamp); beam.set(s.beam); under.set(s.under); rise.set(s.rise);
    yaw.set(s.yaw); etch.set(s.etch); touch.set(s.touch); flash.set(s.flash); touchX.set(s.touchX); touchY.set(s.touchY);
    const changed = reportedPhase.get() !== s.phase;
    if (s.events || changed) scheduleOnRN(report, s.events, s.phase, changed);
    reportedPhase.set(s.phase);
    s.events = 0;
    state.set(s);
  });

  const gesture = useMemo(() => Gesture.Pan().minDistance(0).maxPointers(1)
    .onTouchesDown((event) => {
      const point = event.changedTouches[0];
      if (point) act({ type: 'begin', x: point.x, y: point.y });
    })
    .onUpdate((event) => { act({ type: 'move', x: event.x, y: event.y, velocity: event.velocityX }); })
    .onTouchesUp(() => { act({ type: 'end' }); })
    .onTouchesCancelled(() => { act({ type: 'end', cancelled: true }); })
    .onFinalize(() => { act({ type: 'end', cancelled: true }); }), [act]);

  const accessibility = useCallback((target: 'share' | 'close' | 'curtain' | 'trophy', delta?: number) => {
    scheduleOnUI(act, { type: 'access', target, delta });
  }, [act]);
  return { values, state, visible, canvasSize, phase, gesture, accessibility };
}
export type TrophyMotion = ReturnType<typeof useTrophyMotion>;
