import * as Haptics from 'expo-haptics';
import { useCallback, useState } from 'react';
import { useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { enqueueSceneInput } from './burst-commands';
import type { BurstLayout } from './burst-layout';
import { createScene, SPRING, SCENE_EVENT, sceneFolder, sceneOrb, stepScene, type SceneInput } from './burst-timeline';

function haptic(events: number) {
  if (events & SCENE_EVENT.light) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  if (events & SCENE_EVENT.medium) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
  if (events & SCENE_EVENT.selection) void Haptics.selectionAsync();
  if (events & SCENE_EVENT.soft) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft);
}

export function useBurstMotion(layout: BurstLayout, reduced: boolean, autoPlay: boolean) {
  const state = useSharedValue(createScene(layout.n, reduced, autoPlay));
  const ready = useSharedValue(false);
  const commands = useSharedValue<SceneInput[]>([]);
  const serial = useSharedValue(0);
  const [accessible, setAccessible] = useState({ expanded: false, selected: -1, landed: 0 });
  const changed = useCallback((expanded: boolean, selected: number, landed: number) => {
    setAccessible({ expanded, selected, landed });
  }, []);
  useFrameCallback(({ timeSincePreviousFrame }) => {
    if (!ready.get()) return;
    let next = state.get();
    const pending = commands.get();
    let events = 0;
    for (let i = 0; i < pending.length; i++) {
      next = stepScene(next, 0, layout, pending[i]);
      events |= next.events;
      if (next.events & SCENE_EVENT.changed) scheduleOnRN(changed, next.expanded, next.selected, next.landed);
    }
    if (pending.length) commands.set([]);
    next = stepScene(next, Math.min(timeSincePreviousFrame ?? 0, 50) / 1000, layout);
    events |= next.events;
    state.set(next);
    if (next.events & SCENE_EVENT.changed) scheduleOnRN(changed, next.expanded, next.selected, next.landed);
    if ((!autoPlay || (next.paused && !next.recovering)) && events > 1) scheduleOnRN(haptic, events);
  });
  const send = useCallback((kind: SceneInput['kind'], index = -1) => {
    scheduleOnUI(enqueueSceneInput, commands, serial, kind, index);
  }, [commands, serial]);
  const folder = useDerivedValue(() => sceneFolder(state.get(), layout));
  const disc = useDerivedValue(() => sceneOrb(state.get(), layout));
  const cx = useDerivedValue(() => folder.get().cx);
  const cy = useDerivedValue(() => folder.get().cy);
  const scale = useDerivedValue(() => folder.get().scale);
  const open = useDerivedValue(() => folder.get().open);
  const press = useDerivedValue(() => folder.get().press);
  const squash = useDerivedValue(() => folder.get().squash);
  const flow = useDerivedValue(() => folder.get().flow);
  const x = useDerivedValue(() => disc.get().x);
  const y = useDerivedValue(() => disc.get().y);
  const r = useDerivedValue(() => disc.get().r);
  const glow = useDerivedValue(() => disc.get().glow);
  const beat = useDerivedValue(() => disc.get().beat);
  const mark = useDerivedValue(() => disc.get().mark);
  const rows = useDerivedValue(() => state.get().sim.rows);
  const selected = useDerivedValue(() => state.get().selected);
  const selection = useDerivedValue(() => state.get().selectedMix);
  const pressed = useDerivedValue(() => state.get().pressed);
  const pressAmount = useDerivedValue(() => state.get().springs[SPRING.rowPress]);
  const dock = useDerivedValue(() => state.get().dock);
  const bloomX = useDerivedValue(() => state.get().sim.bloomX || layout.docked.cx);
  const bloomY = useDerivedValue(() => state.get().sim.bloomY || layout.docked.cy - 180);
  const bloom = useDerivedValue(() => Math.max(state.get().sim.bloomStrength, state.get().expanded ? .12 : 0));
  const clock = useDerivedValue(() => reduced ? 0 : state.get().idleTime * 1000);
  const opacity = useDerivedValue(() => state.get().fade);
  return { ready, accessible, send, folder: { cx, cy, scale, open, press, squash, flow },
    orb: { x, y, r, glow, beat, mark }, rows, selected, selection, pressed, pressAmount, dock,
    bloomX, bloomY, bloom, clock, opacity };
}
