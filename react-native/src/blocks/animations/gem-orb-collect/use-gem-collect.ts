// One UI-thread frame callback owns physics, input, demo arbitration, and renderer values.
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import { Easing, ReduceMotion, useFrameCallback, useSharedValue, withTiming, type SharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import { initialDemo, pauseDemo, stepDemo } from './collect-demo';
import { emptyInput, EVENT, restCollect, stepCollect, type Phase } from './collect-sim';
import { REST, TIMING, type BackdropLayerProps, type CrystalPillLayerProps, type GemLayerProps, type Layout } from './gem-contract';

/** Buffers keep their identity; force notification after the UI thread updates them. */
function publishArray(shared: SharedValue<number[]>, source: number[]) {
  'worklet';
  const buffer = shared.get();
  for (let i = 0; i < buffer.length; i++) buffer[i] = source[i];
  shared.modify(undefined, true);
}
function publishNumber(shared: SharedValue<number>, next: number) {
  'worklet'; if (shared.get() !== next) shared.set(next);
}
function impact(events: number, hits: number) {
  const style = events & EVENT.burst ? Haptics.ImpactFeedbackStyle.Heavy
    : events & EVENT.land ? Haptics.ImpactFeedbackStyle.Rigid
      : events & EVENT.rewind ? Haptics.ImpactFeedbackStyle.Medium
        : events & EVENT.hit && hits > 8 ? Haptics.ImpactFeedbackStyle.Soft : Haptics.ImpactFeedbackStyle.Light;
  void Haptics.impactAsync(style).catch(() => {});
}
export function useGemCollect(l: Layout, autoPlay: boolean, reduced: boolean, ready: boolean) {
  const initial = useMemo(() => restCollect(l, reduced), [l, reduced]);
  const initialScript = useMemo(() => initialDemo(initial), [initial]);
  const state = useSharedValue(initial), demo = useSharedValue(initialScript);
  const pending = useSharedValue(emptyInput(reduced)), touching = useSharedValue(false);
  const contact = useSharedValue({ x: 0, y: 0, startX: 0, startY: 0, at: 0, moved: 0, vx: 0, vy: 0, lastAt: 0 });
  const active = useSharedValue(false), visible = useSharedValue(0), lastHaptic = useSharedValue(-10);
  const [text, setText] = useState({ tally: 0, phase: 'before' as Phase, balance: 1400 });
  const report = useCallback((tally: number, phase: Phase, balance: number, events: number, hits: number) => {
    setText(previous => previous.tally === tally && previous.phase === phase && previous.balance === balance ? previous : { tally, phase, balance });
    if (events) impact(events, hits);
  }, []);
  useEffect(() => {
    active.set(ready);
    visible.set(ready ? withTiming(1, { duration: 240, easing: Easing.bezier(0.23, 1, 0.32, 1), reduceMotion: ReduceMotion.Never }) : 0);
    return () => { active.set(false); };
  }, [active, ready, visible]);
  const reportState = useSharedValue({ tally: 0, phase: 'before' as Phase, balance: 1400 });
  const copySpeed = useSharedValue(0), bodyCopySpeed = useSharedValue(0), barLabelSpeed = useSharedValue(0);
  // Dedicated renderer values avoid dozens of mappers reading the full simulation.
  const buffers = useMemo(() => ({ lights: [...initial.lights], sprites: [...initial.sprites] }), [initial]);
  const time = useSharedValue(initial.clock);
  const centreX = useSharedValue(initial.centreX);
  const centreY = useSharedValue(initial.centreY);
  const radius = useSharedValue(initial.radius);
  const yaw = useSharedValue(initial.yaw);
  const pitch = useSharedValue(initial.pitch);
  const stretch = useSharedValue(initial.stretch);
  const charge = useSharedValue(initial.charge);
  const flare = useSharedValue(initial.flare);
  const lights = useSharedValue(buffers.lights);
  const miniYaw = useSharedValue(initial.miniYaw);
  const miniFlare = useSharedValue(initial.miniFlare);
  const hush = useSharedValue(initial.hush);
  const shock = useSharedValue(initial.shock);
  const beams = useSharedValue(initial.beams);
  const barPress = useSharedValue(initial.barPress);
  const barGlow = useSharedValue(initial.barGlow);
  const barSheen = useSharedValue(initial.barSheen);
  const barOpacity = useSharedValue(initial.barOpacity);
  const barScale = useSharedValue(initial.barScale);
  const capsuleScale = useSharedValue(initial.capsuleScale);
  const capsuleGlow = useSharedValue(initial.capsuleGlow);
  const capsuleSheen = useSharedValue(initial.capsuleSheen);
  const sprites = useSharedValue(buffers.sprites);
  const value = useSharedValue(initial.value);
  const rollFade = useSharedValue(initial.rollFade);
  const copyTarget = useSharedValue(initial.copyTarget);
  const barLabelTarget = useSharedValue(initial.barLabelTarget);
  const copy = useSharedValue(initial.copy);
  const bodyCopy = useSharedValue(initial.bodyCopy);
  const barLabel = useSharedValue(initial.barLabel);
  const tallyX = useSharedValue(initial.tallyX);
  const tallyY = useSharedValue(initial.tallyY);
  const tallyScale = useSharedValue(initial.tallyScale);
  const tallyOpacity = useSharedValue(initial.tallyOpacity);
  useFrameCallback(frame => {
    if (!active.get() || visible.get() < 1) return;
    const dt = Math.min(0.05, (frame.timeSincePreviousFrame ?? 0) / 1000);
    if (dt <= 0) return;
    const input = pending.get();
    const next = state.get(), previousClock = next.clock;
    let silent = false;
    if (autoPlay) {
      const script = demo.get();
      stepDemo(next, script, dt, l, reduced, input, touching.get()); silent = script.silent;
    } else stepCollect(next, dt, input, l);
    if (next.clock < previousClock) lastHaptic.set(-10);
    publishNumber(time, next.clock);
    publishNumber(centreX, next.centreX);
    publishNumber(centreY, next.centreY);
    publishNumber(radius, next.radius);
    publishNumber(yaw, next.yaw);
    publishNumber(pitch, next.pitch);
    publishNumber(stretch, next.stretch);
    publishNumber(charge, next.charge);
    publishNumber(flare, next.flare);
    publishArray(lights, next.lights);
    publishNumber(miniYaw, next.miniYaw);
    publishNumber(miniFlare, next.miniFlare);
    publishNumber(hush, next.hush);
    publishNumber(shock, next.shock);
    publishNumber(beams, next.beams);
    publishNumber(barPress, next.barPress);
    publishNumber(barGlow, next.barGlow);
    publishNumber(barSheen, next.barSheen);
    publishNumber(barOpacity, next.barOpacity);
    publishNumber(barScale, next.barScale);
    publishNumber(capsuleScale, next.capsuleScale);
    publishNumber(capsuleGlow, next.capsuleGlow);
    publishNumber(capsuleSheen, next.capsuleSheen);
    publishArray(sprites, next.sprites);
    publishNumber(value, next.value);
    publishNumber(rollFade, next.rollFade);
    publishNumber(copyTarget, next.copyTarget);
    publishNumber(barLabelTarget, next.barLabelTarget);
    publishNumber(copy, next.copy);
    publishNumber(bodyCopy, next.bodyCopy);
    publishNumber(barLabel, next.barLabel);
    publishNumber(tallyX, next.tallyX);
    publishNumber(tallyY, next.tallyY);
    publishNumber(tallyScale, next.tallyScale);
    publishNumber(tallyOpacity, next.tallyOpacity);
    publishNumber(copySpeed, next.copySpeed); publishNumber(bodyCopySpeed, next.bodyCopySpeed); publishNumber(barLabelSpeed, next.barLabelSpeed);
    input.dragX = 0; input.dragY = 0; input.releaseVX = 0; input.releaseVY = 0;
    input.press = false; input.tap = false; input.collect = false; input.replay = false;
    let events = silent ? 0 : next.audibleEvents;
    if (events & EVENT.hit) {
      if (next.clock - lastHaptic.get() < 0.045) events &= ~EVENT.hit;
      else lastHaptic.set(next.clock);
    }
    const balance = next.phase === 'after' || next.landed ? 1600 : next.phase === 'rewind' || next.phase === 'before' ? 1400 : reportState.get().balance;
    const previous = reportState.get();
    if (events || previous.tally !== next.tally || previous.phase !== next.phase || previous.balance !== balance) {
      reportState.set({ tally: next.tally, phase: next.phase, balance });
      scheduleOnRN(report, next.tally, next.phase, balance, events, next.hits);
    }
  });
  const interrupt = useCallback(() => {
    'worklet';
    pauseDemo(demo.get());
    // Lift only the scripted finger. A real finger already owns pending input.
    touching.set(true);
  }, [demo, touching]);
  const rootGesture = useMemo(() => Gesture.Manual()
    .onTouchesDown((_event, manager) => { manager.begin(); interrupt(); })
    .onTouchesUp((_event, manager) => { touching.set(false); manager.fail(); })
    .onTouchesCancelled(() => { touching.set(false); }), [interrupt, touching]);
  const hitRadius = l.gemRadius * 1.25;
  // Named worklets are shared by the native recognizers and removable on-device QA drivers.
  const gemDown = useCallback((x: number, y: number) => {
    'worklet';
    if (!active.get()) return;
    interrupt();
    if (Math.hypot(x - hitRadius, y - hitRadius) > hitRadius) return;
    const now = state.get().clock;
    contact.set({ x, y, startX: x, startY: y, at: now, moved: 0, vx: 0, vy: 0, lastAt: now });
    pending.set({ ...pending.get(), gemDown: true, holdMoved: false, press: true, touchX: (x - hitRadius) / l.gemRadius, touchY: -(y - hitRadius) / l.gemRadius });
  }, [active, contact, hitRadius, interrupt, l.gemRadius, pending, state]);
  const gemMove = useCallback((x: number, y: number) => {
    'worklet';
    if (!pending.get().gemDown) return;
    const c = contact.get(), now = state.get().clock, dt = Math.max(1 / 240, now - c.lastAt), dx = x - c.x, dy = y - c.y;
    const moved = Math.max(c.moved, Math.hypot(x - c.startX, y - c.startY));
    contact.set({ ...c, x, y, moved, vx: dx / dt, vy: dy / dt, lastAt: now });
    const old = pending.get();
    pending.set({ ...old, dragX: old.dragX + dx, dragY: old.dragY + dy, holdMoved: !!old.holdMoved || moved > TIMING.holdSlop });
  }, [contact, pending, state]);
  const gemVelocity = useCallback((vx: number, vy: number) => {
    'worklet'; contact.set({ ...contact.get(), vx, vy });
  }, [contact]);
  const gemUp = useCallback(() => {
    'worklet';
    const old = pending.get(); if (!old.gemDown) return;
    const c = contact.get(), age = state.get().clock - c.at;
    pending.set({ ...old, gemDown: false, tap: age < TIMING.holdDelay && c.moved <= TIMING.holdSlop, releaseVX: reduced || state.get().clock - c.lastAt > 0.1 ? 0 : c.vx, releaseVY: reduced || state.get().clock - c.lastAt > 0.1 ? 0 : c.vy });
    touching.set(false);
  }, [contact, pending, reduced, state, touching]);
  const gemCancel = useCallback(() => {
    'worklet'; const old = pending.get(); if (old.gemDown) pending.set({ ...old, gemDown: false }); touching.set(false);
  }, [pending, touching]);
  const gemGesture = useMemo(() => Gesture.Pan().minDistance(0).maxPointers(1)
    .simultaneousWithExternalGesture(rootGesture)
    .onTouchesDown(event => { const p = event.changedTouches[0]; if (p) gemDown(p.x, p.y); })
    .onTouchesMove(event => { const p = event.changedTouches[0]; if (p) gemMove(p.x, p.y); })
    .onUpdate(event => gemVelocity(event.velocityX, event.velocityY))
    .onTouchesUp(gemUp).onFinalize(gemCancel),
  [gemCancel, gemDown, gemMove, gemUp, gemVelocity, rootGesture]);
  const bw = l.button.width, bh = l.button.height;
  const barDown = useCallback(() => {
    'worklet';
    interrupt(); if (!active.get()) return; const phase = state.get().phase;
    if (phase === 'before' || phase === 'after') pending.set({ ...pending.get(), barDown: true, press: true });
  }, [active, interrupt, pending, state]);
  const barUp = useCallback((x: number, y: number) => {
    'worklet';
    const old = pending.get(), phase = state.get().phase;
    const inside = x >= 0 && x <= bw && y >= 0 && y <= bh;
    pending.set({ ...old, barDown: false, collect: inside && !!old.barDown && phase === 'before', replay: inside && !!old.barDown && phase === 'after' }); touching.set(false);
  }, [bh, bw, pending, state, touching]);
  const barCancel = useCallback(() => {
    'worklet'; pending.set({ ...pending.get(), barDown: false }); touching.set(false);
  }, [pending, touching]);
  const barGesture = useMemo(() => Gesture.Pan().minDistance(0).maxPointers(1)
    .simultaneousWithExternalGesture(rootGesture).onTouchesDown(barDown)
    .onTouchesUp(event => { const p = event.changedTouches[0]; barUp(p?.x ?? -1, p?.y ?? -1); })
    .onFinalize(barCancel), [barCancel, barDown, barUp, rootGesture]);
  const activate = useCallback((bar: boolean) => {
    'worklet';
    if (!active.get()) return;
    pauseDemo(demo.get()); const phase = state.get().phase, old = pending.get();
    pending.set({ ...old, press: phase === 'before', collect: phase === 'before', replay: bar && phase === 'after', tap: !bar && phase !== 'before' });
  }, [active, demo, pending, state]);
  const activateGem = useCallback(() => scheduleOnUI(activate, false), [activate]);
  const activateBar = useCallback(() => scheduleOnUI(activate, true), [activate]);
  const roll = useSharedValue<number>(REST.roll), one = useSharedValue(1), zero = useSharedValue(0), emptyLights = useSharedValue<number[]>(Array(24).fill(0));
  const miniX = useSharedValue(l.miniGemX), miniY = useSharedValue(l.miniGemY), miniRadius = useSharedValue(l.miniGemRadius), miniPitch = useSharedValue<number>(REST.pitch);
  const gem: GemLayerProps = { centreX, centreY, radius, yaw, pitch, roll, stretch, charge, flare, lights, time, opacity: one };
  const mini: GemLayerProps = { centreX: miniX, centreY: miniY, radius: miniRadius, yaw: miniYaw, pitch: miniPitch, roll, stretch: one, charge, flare: miniFlare, lights: emptyLights, time, opacity: one };
  const backdrop: BackdropLayerProps = { width: l.width, height: l.height, gemX: centreX, gemY: centreY, gemRadius: l.gemRadius, yaw, pitch, charge, flare, hush, shock, beams, time };
  const bar: CrystalPillLayerProps = { ...l.button, tone: 'bar', press: barPress, glow: barGlow, sheen: barSheen, opacity: barOpacity, scale: barScale, time };
  const capsule: CrystalPillLayerProps = { ...l.capsule, tone: 'capsule', press: zero, glow: capsuleGlow, sheen: capsuleSheen, opacity: one, scale: capsuleScale, time };
  return { copySpeed, bodyCopySpeed, barLabelSpeed, ready: active, gem, mini, backdrop, bar, capsule, sprites, value, rollFade, copy, bodyCopy, barLabel, copyTarget, barLabelTarget, tallyX, tallyY, tallyScale, tallyOpacity, visible, text, rootGesture, gemGesture, barGesture, activateGem, activateBar };
}
export type GemCollectController = ReturnType<typeof useGemCollect>;
