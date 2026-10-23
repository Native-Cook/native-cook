import { useCallback, useMemo } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import { useSharedValue } from 'react-native-reanimated';

import { type VaultLayout } from './vault-layout';
import { flickSpin, resistDrag } from './vault-motion';
import { type VaultMotion } from './use-vault-motion';
import { copyEngine } from './vault-state';

type Drag = { translationX: number; translationY: number; velocityX: number; velocityY: number };

/** Keep gesture decisions independent of native recognition so they can be exercised on the UI runtime. */
export function useVaultGestureHandlers(motion: VaultMotion, layout: VaultLayout) {
  const { engine, hero, dispatchUI, reduced } = motion;
  const axis = useSharedValue(0);
  const startSpin = useSharedValue(0);
  const begin = useCallback(() => {
    'worklet';
    axis.set(0); startSpin.set(hero.get().pose.spin); dispatchUI('touch', -1, 0, 0);
  }, [axis, dispatchUI, hero, startSpin]);
  const update = useCallback((event: Drag) => {
    'worklet';
    if (axis.get() === 0) axis.set(Math.abs(event.translationX) > Math.abs(event.translationY) ? 1 : 2);
    if (reduced) return;
    const e = copyEngine(engine.get());
    if (e.phase === 'closing' || e.selected < 0) return;
    if (axis.get() === 1) {
      e.turnMode = 'drag'; e.turn.x = startSpin.get() + event.translationX / (2 * layout.heldR) * Math.PI;
    } else { e.dragging = true; e.drag = { x: resistDrag(event.translationY), v: 0 }; }
    engine.set(e);
  }, [axis, engine, layout.heldR, reduced, startSpin]);
  const end = useCallback((event: Drag) => {
    'worklet';
    const e = copyEngine(engine.get());
    if (e.phase === 'closing' || e.selected < 0) return;
    if (axis.get() === 1 && !reduced) {
      e.turn = flickSpin(e.turn.x, event.velocityX / (2 * layout.heldR) * Math.PI, false);
      e.turnMode = 'free'; engine.set(e);
    } else if (axis.get() === 2) {
      e.dragging = false; e.drag.v = event.velocityY; engine.set(e);
      if (event.translationY > 90 || event.velocityY > 700) dispatchUI('close', -1, 0, event.velocityY);
    }
  }, [axis, dispatchUI, engine, layout.heldR, reduced]);
  const finalize = useCallback(() => {
    'worklet';
    const e = copyEngine(engine.get()); e.dragging = false; engine.set(e); dispatchUI('touch-end', -1, 0, 0);
  }, [dispatchUI, engine]);
  const tap = useCallback((event: { x: number; y: number }, success: boolean) => {
    'worklet';
    if (!success) return;
    const p = hero.get().pose;
    const dx = event.x - p.cx; const dy = event.y - p.cy;
    const onBadge = dx * dx + dy * dy < p.r * p.r * 1.5;
    const onShare = event.x >= layout.share.x && event.x <= layout.share.x + layout.share.width &&
      event.y >= layout.share.y - 4 && event.y <= layout.share.y + layout.share.height + 4;
    const onClose = event.x >= layout.close.x - 4 && event.x <= layout.close.x + 40 &&
      event.y >= layout.close.y - 4 && event.y <= layout.close.y + 40;
    if (!onBadge && !onShare && !onClose) dispatchUI('close', -1, 0, 0);
  }, [dispatchUI, hero, layout]);
  return { begin, update, end, finalize, tap };
}

export function useVaultGesture(motion: VaultMotion, layout: VaultLayout) {
  const { begin, update, end, finalize, tap } = useVaultGestureHandlers(motion, layout);
  return useMemo(() => Gesture.Race(
    Gesture.Pan().minDistance(10).onBegin(begin).onUpdate(update).onEnd(end).onFinalize(finalize),
    Gesture.Tap().maxDistance(10).onEnd(tap),
  ), [begin, end, finalize, tap, update]);
}
