import * as Haptics from 'expo-haptics';
import { useCallback, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Gesture, type ComposedGesture, type GestureType } from 'react-native-gesture-handler';
import { type SharedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { EVENT, initialState, step, type KeycapInput, type KeycapState, type LegendFlip, type MoodMix } from './keycap-sim';
import { keycapLayout, MOOD_RGB } from './keycap-theme';

export type { LegendFlip, MoodMix } from './keycap-sim';
export type KeycapMotion = {
  gesture: ComposedGesture | GestureType;
  ready: SharedValue<boolean>;
  travel: SharedValue<number[]>; rim: SharedValue<number[]>; dim: SharedValue<number>;
  legends: SharedValue<LegendFlip>[]; mood: SharedValue<MoodMix>; scene: SharedValue<number[]>;
  charge: SharedValue<number>; burst: SharedValue<number>; ring: SharedValue<number>;
  fan: SharedValue<number[]>; miniTravel: SharedValue<number[]>; selected: SharedValue<number>;
  choices: number[]; moodIndex: number; fanOpen: boolean;
  actions: { next(key: number): void; previous(key: number): void; selectMood(m: number): void; toggleFan(): void };
};
function publishArray(shared: SharedValue<number[]>, next: number[]) {
  'worklet';
  const previous = shared.get();
  for (let i = 0; i < next.length; i++) {
    if (previous[i] !== next[i]) { shared.set(next.slice()); return; }
  }
}
function publishNumber(shared: SharedValue<number>, next: number) {
  'worklet';
  if (shared.get() !== next) shared.set(next);
}
function publishLegend(shared: SharedValue<LegendFlip>, next: LegendFlip) {
  'worklet';
  const p = shared.get();
  if (p.from !== next.from || p.to !== next.to || p.t !== next.t || p.mode !== next.mode) shared.set({ ...next });
}
function haptic(mask: number) {
  if (mask & 7) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  if (mask & 8) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
  if (mask & EVENT.burst) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  if (mask & EVENT.fanOpen) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  if (mask & EVENT.selection) void Haptics.selectionAsync().catch(() => {});
}

export function useKeycapMotion(options: {
  width: number; height: number; autoPlay?: boolean; reduced: boolean; onTouch?: () => void;
}): KeycapMotion {
  const { width, height, autoPlay = false, reduced, onTouch } = options;
  const config = useMemo(() => ({ layout: keycapLayout(width, height), reduced, autoPlay }), [width, height, reduced, autoPlay]);
  const configuration = useSharedValue(config);
  const state = useSharedValue(initialState(config));
  const ready = useSharedValue(false);
  const travel = useSharedValue([0, 0, 0, 0]);
  const rim = useSharedValue([0, 0, 0, 0]);
  const dim = useSharedValue(1);
  const legend0 = useSharedValue<LegendFlip>({ from: 0, to: 0, t: -1, mode: reduced ? 2 : 0 });
  const legend1 = useSharedValue<LegendFlip>({ from: 0, to: 0, t: -1, mode: reduced ? 2 : 0 });
  const legend2 = useSharedValue<LegendFlip>({ from: 0, to: 0, t: -1, mode: reduced ? 2 : 0 });
  const legend3 = useSharedValue<LegendFlip>({ from: 0, to: 0, t: -1, mode: reduced ? 2 : 1 });
  const mood = useSharedValue<MoodMix>({ from: 0, to: 0, p: 1, ox: 0, oy: 0, radial: 0 });
  const scene = useSharedValue(MOOD_RGB[0].slice());
  const charge = useSharedValue(0);
  const burst = useSharedValue(1);
  const ring = useSharedValue(0);
  const fan = useSharedValue([0, 0, 0, 0, 0, 0]);
  const miniTravel = useSharedValue([0, 0, 0, 0, 0, 0]);
  const selected = useSharedValue(0);
  const lastTimestamp = useSharedValue(-1);
  const touchId = useSharedValue(-1);
  const [accessible, setAccessible] = useState({ choices: [0, 0, 0], moodIndex: 0, fanOpen: false });
  const callback = useRef(onTouch);
  const mounted = useRef(false);
  useLayoutEffect(() => { callback.current = onTouch; }, [onTouch]);
  useLayoutEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useLayoutEffect(() => { configuration.set(config); }, [config, configuration]);
  const touched = useCallback(() => { if (mounted.current) callback.current?.(); }, []);
  const report = useCallback((log: number[], choices: number[], open: boolean) => {
    if (!mounted.current) return;
    let changed = false;
    for (let i = 0; i < log.length; i += 3) {
      haptic(log[i]);
      if (log[i] & (EVENT.changed | EVENT.fanOpen | EVENT.fanClose | EVENT.reset | EVENT.wrap)) changed = true;
    }
    if (changed) setAccessible(previous => {
      if (previous.choices[0] === choices[0] && previous.choices[1] === choices[1] && previous.choices[2] === choices[2]
        && previous.moodIndex === choices[3] && previous.fanOpen === open) return previous;
      return { choices: choices.slice(0, 3), moodIndex: choices[3], fanOpen: open };
    });
  }, []);
  const publish = useCallback((s: KeycapState) => {
    'worklet';
    state.set(s);
    publishArray(travel, s.x); publishArray(rim, s.rim); publishArray(scene, s.scene);
    publishArray(fan, s.out); publishArray(miniTravel, s.miniX);
    publishNumber(dim, s.dim); publishNumber(charge, s.charge); publishNumber(burst, s.burst);
    publishNumber(ring, s.ring); publishNumber(selected, s.choices[3]);
    publishLegend(legend0, s.legends[0]); publishLegend(legend1, s.legends[1]);
    publishLegend(legend2, s.legends[2]); publishLegend(legend3, s.legends[3]);
    const m = mood.get(); const n = s.mood;
    if (m.from !== n.from || m.to !== n.to || m.p !== n.p || m.ox !== n.ox || m.oy !== n.oy || m.radial !== n.radial) mood.set({ ...n });
    if (s.events) scheduleOnRN(report, s.eventLog, s.choices.slice(), s.fan.open === 1);
  }, [state, travel, rim, scene, fan, miniTravel, dim, charge, burst, ring, selected, legend0, legend1, legend2, legend3, mood, report]);
  const act = useCallback((input: KeycapInput) => {
    'worklet';
    if (!ready.get()) return;
    const s = state.get(); s.config = configuration.get();
    publish(step(s, input, 0));
  }, [configuration, publish, ready, state]);
  // Stable dependencies are shared-value handles and stable callbacks, never options or React state.
  const frame = useCallback((info: { timestamp: number }) => {
    'worklet';
    const last = lastTimestamp.get(); lastTimestamp.set(info.timestamp);
    if (!ready.get() || last < 0) return;
    const s = state.get(); s.config = configuration.get();
    publish(step(s, { ready: true }, info.timestamp - last));
  }, [configuration, lastTimestamp, publish, ready, state]);
  useFrameCallback(frame);

  // RNGH installs these worklets; this memo never executes them during React render.
  /* eslint-disable react-hooks/refs */
  const gesture = useMemo(() => Gesture.Pan().manualActivation(true).maxPointers(1)
    .onTouchesDown((event, manager) => {
      if (!ready.get() || touchId.get() >= 0 || !event.changedTouches.length) return;
      const t = event.changedTouches[0]; touchId.set(t.id);
      manager.activate();
      act({ ready: true, pointer: { down: true, x: t.x, y: t.y } });
      scheduleOnRN(touched);
    })
    .onTouchesMove(event => {
      const id = touchId.get();
      for (let i = 0; i < event.changedTouches.length; i++) {
        const t = event.changedTouches[i];
        if (t.id === id) act({ ready: true, pointer: { down: true, x: t.x, y: t.y } });
      }
    })
    .onTouchesUp((event, manager) => {
      const id = touchId.get();
      for (let i = 0; i < event.changedTouches.length; i++) {
        const t = event.changedTouches[i];
        if (t.id === id) { act({ ready: true, pointer: { down: false, x: t.x, y: t.y } }); touchId.set(-1); manager.end(); }
      }
    })
    .onTouchesCancelled((_event, manager) => {
      if (touchId.get() >= 0) {
        const p = state.get().pointer;
        act({ ready: true, cancelled: true, pointer: { down: false, x: p.x, y: p.y } }); touchId.set(-1);
      }
      manager.end();
    }), [act, ready, state, touchId, touched]);
  /* eslint-enable react-hooks/refs */
  const next = useCallback((key: number) => { scheduleOnUI(act, { ready: true, action: { type: 'next', key } }); touched(); }, [act, touched]);
  const previous = useCallback((key: number) => { scheduleOnUI(act, { ready: true, action: { type: 'previous', key } }); touched(); }, [act, touched]);
  const selectMood = useCallback((m: number) => { scheduleOnUI(act, { ready: true, action: { type: 'selectMood', mood: m } }); touched(); }, [act, touched]);
  const toggleFan = useCallback(() => { scheduleOnUI(act, { ready: true, action: { type: 'toggleFan' } }); touched(); }, [act, touched]);
  return {
    gesture, ready, travel, rim, dim, legends: [legend0, legend1, legend2, legend3], mood, scene, charge, burst, ring,
    fan, miniTravel, selected, ...accessible, actions: { next, previous, selectMood, toggleFan },
  };
}
