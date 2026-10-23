import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { makeMutable, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { act, pillAmount, type Action } from './vault-actions';
import { advanceFollowers, cellFrame, sameCell } from './vault-cells';
import { BADGES } from './vault-contract';
import { advanceDemo, demoScroll } from './vault-demo';
import { heroFrame } from './vault-hero';
import { type VaultLayout } from './vault-layout';
import { copyEngine, initialEngine, initialHero, initialVisual, type CellState, type VaultEvent } from './vault-state';

const ignoreHapticFailure = () => {};
function haptic(kind: VaultEvent['kind']) {
  if (kind === 'selection') void Haptics.selectionAsync().catch(ignoreHapticFailure);
  else if (kind === 'medium') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(ignoreHapticFailure);
  else if (kind === 'light') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(ignoreHapticFailure);
  else if (kind === 'warning') void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning).catch(ignoreHapticFailure);
  else if (kind === 'success') void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(ignoreHapticFailure);
}
export function useVaultMotion(layout: VaultLayout, autoPlay: boolean, ready: boolean) {
  const reduced = useReducedMotion();
  const config = useSharedValue({ layout, ready, reduced, autoPlay });
  const engine = useSharedValue(initialEngine(autoPlay && !reduced));
  const hero = useSharedValue(initialHero(layout));
  const scrollY = useSharedValue(0);
  const scrollCommand = useSharedValue({ y: 0, serial: 0 });
  const pillPress = useSharedValue(0);
  const [cells] = useState(() => BADGES.map((_, i) => makeMutable(initialVisual(layout, i))));
  const [selected, setSelected] = useState(-1);
  const [snapshot, setSnapshot] = useState(() => initialEngine(false).cells);
  const report = useCallback((events: VaultEvent[], current: CellState[]) => {
    for (let i = 0; i < events.length; i++) {
      const event = events[i];
      if (event.kind === 'open') setSelected(event.index);
      else if (event.kind === 'close') setSelected(-1);
      else if (event.kind === 'progress' || event.kind === 'reset') setSnapshot(current);
      haptic(event.kind);
    }
  }, []);
  useEffect(() => { config.set({ layout, ready, reduced, autoPlay }); }, [autoPlay, config, layout, ready, reduced]);

  const frame = useCallback((info: { timestamp: number; timeSincePreviousFrame: number | null }) => {
    'worklet';
    const settings = config.get();
    const e = copyEngine(engine.get());
    const dtMs = Math.max(0, info.timeSincePreviousFrame ?? 0);
    e.now += dtMs;
    e.demo.enabled = settings.autoPlay && !settings.reduced;
    if (!settings.ready) { engine.set(e); return; }
    const l = settings.layout;
    let y = scrollY.get();
    advanceDemo(e, dtMs, settings.ready, l, y, hero.get());
    const nextScroll = demoScroll(e);
    if (nextScroll !== null) {
      y = nextScroll; scrollY.set(y);
      scrollCommand.set({ y, serial: scrollCommand.get().serial + 1 });
    }
    if (!e.demo.enabled && !settings.reduced && e.phase === 'rest' && e.now - e.lastIdleBand >= 6000) {
      e.bandAt = e.now; e.lastIdleBand = e.now;
    }
    advanceFollowers(e, dtMs / 1000, y, settings.reduced);
    const nextHero = heroFrame(e, l, y, Math.min(dtMs / 1000, 0.064), settings.reduced);
    // A resting overlay has no changing shared values, so Skia has no redraw work.
    if (nextHero.index >= 0 || hero.get().index >= 0) hero.set(nextHero);
    for (let i = 0; i < cells.length; i++) {
      const pose = cellFrame(e, l, i, y, settings.reduced ? 0 : nextHero.recede, settings.reduced);
      e.poses[i] = pose;
      if (!sameCell(pose, cells[i].get())) cells[i].set(pose);
    }
    const pill = pillAmount(e);
    if (pillPress.get() !== pill) pillPress.set(pill);
    if (e.events.length) {
      scheduleOnRN(report, e.events, e.cells);
      e.events = [];
    }
    engine.set(e);
  }, [cells, config, engine, hero, pillPress, report, scrollCommand, scrollY]);
  useFrameCallback(frame);

  const dispatchUI = useCallback((action: Action, index: number, x: number, y: number) => {
    'worklet';
    const e = copyEngine(engine.get()); const settings = config.get();
    if (!settings.ready && action !== 'touch' && action !== 'touch-end') return;
    act(e, action, index, x, y, settings.layout, scrollY.get(), hero.get(), settings.reduced);
    engine.set(e);
  }, [config, engine, hero, scrollY]);
  const dispatch = useCallback((action: Action, index = -1, x = 0, y = 0) => {
    scheduleOnUI(dispatchUI, action, index, x, y);
  }, [dispatchUI]);
  const touched = useCallback(() => dispatch('touch'), [dispatch]);
  const touchEnded = useCallback(() => dispatch('touch-end'), [dispatch]);
  const close = useCallback(() => dispatch('close'), [dispatch]);
  const log = useCallback(() => dispatch('log'), [dispatch]);
  const pillIn = useCallback(() => dispatch('pill-in'), [dispatch]);
  const pillOut = useCallback(() => dispatch('pill-out'), [dispatch]);
  return useMemo(() => ({ cells, hero, engine, scrollY, scrollCommand, pillPress, reduced, selected,
    snapshot, dispatch, dispatchUI, touched, touchEnded, close, log, pillIn, pillOut }),
  [cells, hero, engine, scrollY, scrollCommand, pillPress, reduced, selected, snapshot,
    dispatch, dispatchUI, touched, touchEnded, close, log, pillIn, pillOut]);
}
export type VaultMotion = ReturnType<typeof useVaultMotion>;
