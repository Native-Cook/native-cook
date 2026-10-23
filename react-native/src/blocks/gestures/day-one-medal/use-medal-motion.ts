import * as Haptics from 'expo-haptics';
import { useCallback, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Gesture, type ComposedGesture } from 'react-native-gesture-handler';
import { type SharedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { COMPLETION_EVENTS, demoCompletions, pictureCanAdvance, type DemoEvent } from './medal-demo';
import { EVENT, M } from './medal-motion-constants';
import { medalRotation } from './medal-orientation';
import {
  actMotion,
  initialMotion,
  medalMoment,
  sparkSpot,
  stepMotion,
  type MedalMoment,
  type MotionAction,
  type MotionConfig,
  type MotionState,
} from './medal-sim';

export type { MedalMoment } from './medal-sim';
export type MedalMotionOptions = MotionConfig & {
  active?: SharedValue<boolean>;
  gpuDriving?: SharedValue<boolean>;
  presented?: SharedValue<number>;
  touchOrigin?: { x: number; y: number };
  onTouch?: () => void;
  onChange?: (state: MedalMoment) => void;
  onDemoEvent?: (event: DemoEvent) => void;
};
export type MedalMotion = ReturnType<typeof useMedalMotion>;

type Textures = { from: number; to: number; revision: number };

function haptics(events: number) {
  // Unsupported haptics must not turn a successful gesture into an unhandled rejection.
  if (events & EVENT.tick) void Haptics.selectionAsync().catch(() => {});
  if (events & EVENT.land) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  if (events & EVENT.heavy) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy).catch(() => {});
  if (events & EVENT.rigid) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
  if (events & EVENT.soft) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  if (events & EVENT.success) void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  if (events & EVENT.warning) void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning).catch(() => {});
}

/** One serialisable simulation owns motion; RN receives only state transitions and tactile events. */
export function useMedalMotion({ reduced, stage, pivot, engravePath, onTouch, onChange, onDemoEvent, active, gpuDriving, presented, touchOrigin }: MedalMotionOptions) {
  const originX = touchOrigin?.x ?? 0;
  const originY = touchOrigin?.y ?? 0;
  const centreX = stage.centre.x;
  const centreY = stage.centre.y;
  const half = stage.half;
  const pivotX = pivot.x;
  const pivotY = pivot.y;
  const config = useMemo<MotionConfig>(() => ({
    reduced,
    stage: { centre: { x: centreX, y: centreY }, half },
    pivot: { x: pivotX, y: pivotY },
    // Copy data only: callers may have obtained this from a module or a mutable JSON decoder.
    engravePath: engravePath?.filter((p) => p.length >= 3 && p.every(Number.isFinite))
      .map((p) => p.slice(0, 3)).sort((a, b) => a[0] - b[0]),
  }), [centreX, centreY, engravePath, half, pivotX, pivotY, reduced]);

  const state = useSharedValue(initialMotion());
  const rotation = useSharedValue(medalRotation(0, M.pitch, 0));
  const offset = useSharedValue({ x: 0, y: 0 });
  const light = useSharedValue({ x: 0, y: 0 });
  const heat = useSharedValue(0);
  const charge = useSharedValue(0);
  const stamp = useSharedValue(0);
  const morph = useSharedValue(0);
  const sweep = useSharedValue<number>(M.sweep.to);
  const engrave = useSharedValue(0);
  const glint = useSharedValue(0);
  const yaw = useSharedValue(0);
  const spin = useSharedValue(0);
  const pressed = useSharedValue(0);
  const fade = useSharedValue(1);
  const clock = useSharedValue(0);
  const struck = useSharedValue<number>(M.unstruck);
  const spitting = useSharedValue(0);
  const spot = useSharedValue({ x: centreX, y: centreY, on: 0 });
  const worn = useSharedValue(0);
  const attach = useSharedValue({ x: centreX, y: centreY - half * Math.cos(M.pitch) });
  const [textures, setTextures] = useState<Textures>({ from: 0, to: 0, revision: 0 });
  const callbacks = useRef({ onTouch, onChange, onDemoEvent });
  const mounted = useRef(false);
  useLayoutEffect(() => {
    callbacks.current = { onTouch, onChange, onDemoEvent };
  }, [onChange, onTouch, onDemoEvent]);
  useLayoutEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  const report = useCallback((events: number, moment: MedalMoment, pair: Textures) => {
    if (!mounted.current) return;
    haptics(events);
    if (events & EVENT.changed) callbacks.current.onChange?.(moment);
    if (events & EVENT.textures) {
      setTextures((previous) => pair.revision > previous.revision ? pair : previous);
    }
  }, []);
  const touched = useCallback(() => {
    if (mounted.current) callbacks.current.onTouch?.();
  }, []);

  const demoEvent = useCallback((event: DemoEvent) => {
    if (mounted.current) callbacks.current.onDemoEvent?.(event);
  }, []);
  const settled = useSharedValue(true);
  const publish = useCallback((s: MotionState) => {
    'worklet';
    const previous = state.get();
    state.set(s);
    settled.set(!s.down && !s.hold && s.mode === 'rest' && s.heat < 0.001 && s.morphPhase === 'idle');
    const completed = demoCompletions(previous, s);
    for (let i = 0; i < COMPLETION_EVENTS.length; i++) {
      if (completed & (1 << i)) scheduleOnRN(demoEvent, COMPLETION_EVENTS[i]);
    }
    const matrix = medalRotation(s.yaw, s.pitch, s.roll);
    rotation.set(matrix);
    offset.set({ x: s.offsetX, y: s.offsetY });
    light.set({ x: s.lightX, y: s.lightY });
    heat.set(s.heat);
    charge.set(s.charge);
    stamp.set(s.stamp);
    morph.set(s.morph);
    sweep.set(s.sweep);
    engrave.set(s.engrave);
    glint.set(M.glint * s.heat);
    yaw.set(s.yaw);
    spin.set(s.spin);
    pressed.set(s.pressed);
    fade.set(s.fade);
    clock.set(s.clock);
    struck.set(s.struck);
    const point = sparkSpot(s, config);
    spot.set(point);
    spitting.set(s.cutting ? M.engrave.spitting : s.charge);
    worn.set(s.worn);
    // The second matrix column is the transformed object-up axis; screen y points down.
    attach.set({
      x: config.stage.centre.x + s.offsetX + config.stage.half * matrix[3],
      y: config.stage.centre.y + s.offsetY - config.stage.half * matrix[4],
    });
    if (s.events) {
      scheduleOnRN(report, s.events, medalMoment(s), { from: s.from, to: s.to, revision: s.revision });
    }
  }, [attach, charge, clock, config, engrave, fade, glint, heat, light, morph, offset, pressed,
    report, rotation, spin, spitting, spot, stamp, state, struck, sweep, worn, yaw, demoEvent, settled]);

  const act = useCallback((action: MotionAction) => {
    'worklet';
    publish(actMotion(state.get(), action, config));
    if (action.type === 'reset') scheduleOnRN(demoEvent, 'reset');
  }, [config, publish, state, demoEvent]);

  useFrameCallback((frame) => {
    'worklet';
    if (active && !active.get()) return;
    // Back-pressure the simulation while the JS renderer is late. Skia flight is exempt.
    if (presented && !pictureCanAdvance(gpuDriving?.get() ?? false, clock.get(), presented.get())) return;
    publish(stepMotion(state.get(), (frame.timeSincePreviousFrame ?? M.step * M.milliseconds)
      / M.milliseconds, config));
  });

  useLayoutEffect(() => {
    // At completion morph stays 1 while React installs {new, new}. Only this post-commit
    // acknowledgement clears it. At the start, the same barrier installs {old, new} before
    // the clock advances. A revision rejects late acknowledgements after reset or a newer pair.
    scheduleOnUI(act, { type: 'textures', revision: textures.revision });
  }, [act, textures]);

  const touchUI = useCallback((x: number, y: number) => {
    'worklet';
    if (active && !active.get()) return;
    act({ type: 'touch', x: x + originX, y: y + originY });
    scheduleOnRN(touched);
  }, [act, active, originX, originY, touched]);
  const moveUI = useCallback((x: number, y: number, dx: number, dy: number) => {
    'worklet';
    if (active && !active.get()) return;
    act({ type: 'move', x: x + originX, y: y + originY, dx, dy });
  }, [act, active, originX, originY]);
  const releaseUI = useCallback((vx: number, vy: number, success: boolean) => {
    'worklet';
    act({ type: 'release', vx, vy, cancelled: !success });
  }, [act]);

  // Named callbacks are also exercised by the temporary on-device QA driver.
  /* eslint-disable react-hooks/refs */
  const gesture = useMemo<ComposedGesture>(() => Gesture.Simultaneous(Gesture.Pan()
    .minDistance(0).maxPointers(1)
    .onBegin(event => { 'worklet'; touchUI(event.x, event.y); })
    .onChange(event => { 'worklet'; moveUI(event.x, event.y, event.changeX, event.changeY); })
    .onFinalize((event, success) => { 'worklet'; releaseUI(event.velocityX, event.velocityY, success); })),
  [moveUI, releaseUI, touchUI]);
  /* eslint-enable react-hooks/refs */

  const enterUI = useCallback(() => {
    'worklet';
    act({ type: 'enter' });
  }, [act]);
  const flip = useCallback(() => scheduleOnUI(act, { type: 'flip' }), [act]);
  const lap = useCallback(() => scheduleOnUI(act, { type: 'lap' }), [act]);
  const wear = useCallback(() => scheduleOnUI(act, { type: 'wear' }), [act]);
  const nextDay = useCallback((delta: 1 | -1) => scheduleOnUI(act, { type: 'day', delta }), [act]);
  const reset = useCallback(() => scheduleOnUI(act, { type: 'reset' }), [act]);
  const forge = useCallback(() => scheduleOnUI(act, { type: 'forge' }), [act]);
  const flick = useCallback(() => scheduleOnUI(act, { type: 'flick' }), [act]);
  const drift = useCallback(() => scheduleOnUI(act, { type: 'drift' }), [act]);
  const demo = useMemo(() => ({ forge, flick, drift }), [drift, flick, forge]);

  // Never close over this returned object in another worklet: it contains the native gesture.
  return {
    rotation, offset, light, heat, charge, stamp, morph, sweep, engrave, glint,
    from: textures.from, to: textures.to,
    settled, yaw, spin, pressed, fade, clock, struck, spitting, spot, worn, attach, gesture,
    enterUI, flip, lap, wear, nextDay, reset, demo,
  };
}
