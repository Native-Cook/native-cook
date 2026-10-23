import { useCallback, useEffect, useRef } from 'react';
import { useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import * as Haptics from 'expo-haptics';

import { F, type BeamMotion } from './beam-theme';
import { command, createState, publish, step, type SimEvent } from './beam-sim';
import { createDemo, focusDemo, restartedDemo, stepDemo, touchDemo, type DemoEvent } from './beam-demo';

/** Connects the deterministic engine to frame callbacks, event handlers, and native haptics. */
export function useBeamMotion(autoPlay: boolean, onEvent: (event: SimEvent) => void, onDemo: (event: DemoEvent) => void) {
  const still = useReducedMotion();
  const state = useSharedValue(createState(still));
  const transport = useSharedValue(createDemo(autoPlay));
  const clock = useSharedValue(0);
  const frame = useSharedValue(createState(still).frame);
  const labelMix = useSharedValue(1);
  const awaitingDelivery = useSharedValue(false);
  const alive = useRef(true);
  const handlers = useRef({ onEvent, onDemo });
  useEffect(() => { handlers.current = { onEvent, onDemo }; }, [onEvent, onDemo]);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  const deliver = useCallback((events: SimEvent[], scripted: DemoEvent[]) => {
    if (!alive.current) return;
    for (const event of events) {
      if (event.type !== 'haptic') handlers.current.onEvent(event);
      else if (!event.silent) {
        let result: Promise<void>;
        if (event.value === 0) result = Haptics.selectionAsync();
        else if (event.value <= 2) result = Haptics.impactAsync(event.value === 1 ? Haptics.ImpactFeedbackStyle.Soft : Haptics.ImpactFeedbackStyle.Rigid);
        else result = Haptics.notificationAsync(event.value === 3 ? Haptics.NotificationFeedbackType.Error : Haptics.NotificationFeedbackType.Success);
        void result.catch(() => {});
      }
    }
    for (const event of scripted) handlers.current.onDemo(event);
    // Commands above enter the UI queue first; only then may the transport advance.
    scheduleOnUI(function acknowledgeDelivery() {
      'worklet';
      awaitingDelivery.set(false);
    });
  }, [awaitingDelivery]);
  useFrameCallback((info) => {
    const s = state.get(); const d = transport.get();
    if (!s.ready || (d.active && awaitingDelivery.get())) return;
    const dt = Math.min((info.timeSincePreviousFrame ?? 1000 / 60) / 1000, 1 / 30);
    // Process the transport at every physical substep, independent of display refresh rate.
    const remaining = s.remainder + dt;
    const count = Math.floor((remaining + 1e-10) * 240);
    s.remainder = 0;
    for (let i = 0; i < count; i++) {
      step(s, 1 / 240); stepDemo(d, s, 1 / 240);
      for (let j = 0; j < s.events.length; j++) if (s.events[j].type === 'restarted') restartedDemo(d);
    }
    s.remainder = remaining - count / 240;
    clock.set(s.time);
    labelMix.set(s.labelMix);
    const previous = frame.get(); let changed = false;
    for (let i = 0; i < F.SIZE; i++) if (Math.abs(s.frame[i] - previous[i]) > 0.0001) { changed = true; break; }
    if (changed) frame.set(publish(s));
    if (s.events.length > 0 || d.queue.length > 0) {
      awaitingDelivery.set(true);
      scheduleOnRN(deliver, s.events, d.queue); s.events = []; d.queue = [];
    }
    state.set(s); transport.set(d);
  });
  const send = useCallback((type: string, a = 0, b = 0, silent = false) => {
    scheduleOnUI(() => {
      const s = state.get();
      command(s, type, a, b, silent); state.set(s);
    });
  }, [state]);
  const touch = useCallback((down: boolean) => {
    scheduleOnUI(() => { const d = transport.get(); const s = state.get(); touchDemo(d, s, down); state.set(s); transport.set(d); });
  }, [state, transport]);
  const focused = useCallback((value: boolean) => {
    scheduleOnUI(() => {
      const d = transport.get(); const s = state.get();
      if (value) { touchDemo(d, s, true); touchDemo(d, s, false); }
      focusDemo(d, value); state.set(s); transport.set(d);
    });
  }, [transport, state]);
  const motion: BeamMotion = { clock, frame };
  return { motion, state, transport, send, touch, focused, still, labelMix };
}
