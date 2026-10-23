/**
 * State and input for the gift slot. The carousel and the pull are springs under the finger; once the slot takes a
 * card, everything that follows (sink, read, rise, turn) is a pure function of one clock and the moment the card was
 * released, so the loop repeats exactly. Fingers and the demo script call the same worklets.
 */
import { useMemo } from 'react';
import { useFrameCallback, useSharedValue, withSpring } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { CLOSE, DRAG, LOOP_MS, REDUCED_MS, RESUME_MS, SCRIPT, SEQ, SPRINGS } from './motion';
import { GIFTS, LAYOUT, START_INDEX, type Layout } from './theme';

export const PHASE = { choosing: 0, pulling: 1, swiping: 2, inserting: 3, reading: 4, offer: 5, closing: 6 } as const;
export type Haptic = 'select' | 'armed' | 'insert' | 'success' | 'light';

/** Milestones after a release, in ms from the release. */
export function milestones(reduced: boolean) {
  'worklet';
  if (reduced) {
    const read = REDUCED_MS + SEQ.read;
    return { sunk: REDUCED_MS, read, risen: read, turned: read + REDUCED_MS, shown: read + REDUCED_MS };
  }
  const sunk = SEQ.sink;
  const read = sunk + SEQ.read;
  const risen = read + SEQ.rise;
  const turned = risen + SEQ.pause + SEQ.flip;
  return { sunk, read, risen, turned, shown: turned + SEQ.detailsIn };
}

type Callbacks = { onPhase: (p: number) => void; onArmed: (armed: boolean) => void; onIndex: (i: number) => void; onHaptic: (k: Haptic) => void };

export function useGiftSlot(lay: Layout, autoPlay: boolean, reduced: boolean, cb: Callbacks) {
  const clock = useSharedValue(0);
  const offset = useSharedValue<number>(START_INDEX);
  const pullX = useSharedValue(0);
  const pullY = useSharedValue(0);
  const lift = useSharedValue(1);
  const phase = useSharedValue<number>(PHASE.choosing);
  const selected = useSharedValue<number>(START_INDEX);
  const armed = useSharedValue(false);
  /** When the title last changed (for its out-then-in), and which title it was before. */
  const titleAt = useSharedValue(-1e6);
  const titleWas = useSharedValue(0);
  const releasedAt = useSharedValue(-1);
  const releasedFrom = useSharedValue({ x: 0, y: 0 });
  const closeAt = useSharedValue(-1);
  const fired = useSharedValue({ success: false });
  const touch = useSharedValue({ active: false, scripted: false, mode: 0, x0: 0, y0: 0, o0: 0, onCard: false });
  const script = useSharedValue({ on: autoPlay, t: 0, quiet: 0, touching: false });
  const ghost = useSharedValue({ x: 0, y: 0, o: 0 });

  const { onPhase, onArmed, onIndex, onHaptic } = cb;
  const cx = lay.cx;

  // ——— Helpers (declared before every worklet that calls them) ———
  /** Which title the screen shows: 0 choose, 1 release, 2 reading, 3 ready. */
  const titleOf = (ph: number, a: boolean) => {
    'worklet';
    if (ph === PHASE.inserting || ph === PHASE.reading) return 2;
    if (ph === PHASE.offer) return 3;
    return a ? 1 : 0;
  };
  const retitle = (before: number, after: number) => {
    'worklet';
    if (before === after) return;
    titleWas.set(before);
    titleAt.set(clock.get());
  };
  const setPhase = (p: number) => {
    'worklet';
    const before = phase.get();
    if (before === p) return;
    retitle(titleOf(before, armed.get()), titleOf(p, armed.get()));
    phase.set(p);
    scheduleOnRN(onPhase, p);
  };
  const haptic = (k: Haptic) => {
    'worklet';
    if (!touch.get().scripted) scheduleOnRN(onHaptic, k);
  };
  const setArmed = (a: boolean) => {
    'worklet';
    if (armed.get() === a) return;
    retitle(titleOf(phase.get(), armed.get()), titleOf(phase.get(), a));
    armed.set(a);
    scheduleOnRN(onArmed, a);
    if (a) haptic('armed');
  };
  const snapTo = (i: number, velocity: number) => {
    'worklet';
    const target = Math.max(0, Math.min(GIFTS.length - 1, i));
    offset.set(withSpring(target, { ...SPRINGS.snap, velocity }));
    if (target !== selected.get()) {
      selected.set(target);
      scheduleOnRN(onIndex, target);
      haptic('select');
    }
  };
  const release = () => {
    'worklet';
    releasedFrom.set({ x: pullX.get(), y: pullY.get() });
    releasedAt.set(clock.get());
    fired.set({ success: false });
    setPhase(PHASE.inserting);
    haptic('insert');
  };

  const down = (x: number, y: number, scripted: boolean) => {
    'worklet';
    if (phase.get() !== PHASE.choosing) return;
    const onCard = Math.abs(x - cx) < LAYOUT.cardW / 2 + 12 && Math.abs(y - lay.cardY) < LAYOUT.cardH / 2 + 12;
    touch.set({ active: true, scripted, mode: 0, x0: x, y0: y, o0: offset.get(), onCard });
    offset.set(offset.get());
    if (onCard) lift.set(withSpring(DRAG.lift, SPRINGS.lift));
  };

  const move = (x: number, y: number) => {
    'worklet';
    const t = touch.get();
    if (!t.active) return;
    const dx = x - t.x0;
    const dy = y - t.y0;
    let mode = t.mode;
    if (mode === 0 && Math.hypot(dx, dy) > DRAG.decide) {
      mode = Math.abs(dx) > Math.abs(dy) || !t.onCard || dy < 0 ? 2 : 1;
      touch.set({ ...t, mode });
      setPhase(mode === 1 ? PHASE.pulling : PHASE.swiping);
      if (mode === 2) lift.set(withSpring(1, SPRINGS.lift));
    }
    if (mode === 2) {
      let o = t.o0 - dx / lay.spacing;
      // Rubber band past the first and last gift.
      if (o < 0) o *= 0.3;
      if (o > GIFTS.length - 1) o = GIFTS.length - 1 + (o - GIFTS.length + 1) * 0.3;
      offset.set(o);
    } else if (mode === 1) {
      pullX.set(dx * DRAG.sideResist);
      pullY.set(dy < 0 ? dy * DRAG.upResist : Math.min(dy, DRAG.full + (dy - DRAG.full) * 0.2));
      setArmed(dy >= DRAG.commit);
    }
  };

  const up = (vx: number, vy: number) => {
    'worklet';
    const t = touch.get();
    if (!t.active) return;
    touch.set({ ...t, active: false });
    lift.set(withSpring(1, SPRINGS.lift));
    if (t.mode === 2) {
      const fling = Math.abs(vx) > DRAG.swipeVelocity ? -Math.sign(vx) * 0.5 : 0;
      snapTo(Math.round(offset.get() + fling), -vx / lay.spacing);
      setPhase(PHASE.choosing);
    } else if (t.mode === 1) {
      if (armed.get()) {
        setArmed(false);
        release();
        return;
      }
      pullX.set(withSpring(0, { ...SPRINGS.back, velocity: vx }));
      pullY.set(withSpring(0, { ...SPRINGS.back, velocity: vy }));
      setPhase(PHASE.choosing);
    } else {
      setPhase(PHASE.choosing);
    }
  };

  /** VoiceOver: activate the centred gift without dragging, or move along the gifts. */
  const activate = () => {
    'worklet';
    if (phase.get() !== PHASE.choosing) return;
    touch.set({ ...touch.get(), scripted: false });
    release();
  };
  const step = (dir: number) => {
    'worklet';
    if (phase.get() !== PHASE.choosing) return;
    snapTo(selected.get() + dir, 0);
  };
  const close = () => {
    'worklet';
    if (phase.get() !== PHASE.offer) return;
    closeAt.set(clock.get());
    setPhase(PHASE.closing);
    haptic('light');
  };

  const takeOver = () => {
    'worklet';
    const s = script.get();
    if (s.on && touch.get().scripted && touch.get().active) {
      // Disarm first so the scripted finger's release cancels instead of dropping the card in.
      setArmed(false);
      up(0, 0);
    }
    ghost.set({ ...ghost.get(), o: 0 });
    script.set({ ...s, on: false, quiet: 0, touching: true });
  };
  const letGo = () => {
    'worklet';
    script.set({ ...script.get(), touching: false, quiet: 0 });
  };

  const ease = (u: number) => {
    'worklet';
    const c = Math.min(1, Math.max(0, u));
    return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
  };

  useFrameCallback((info) => {
    const dt = Math.min(info.timeSincePreviousFrame ?? 16, 64);
    const now = clock.get() + dt;
    clock.set(now);

    // The slot's sequence after a release.
    const at = releasedAt.get();
    if (at >= 0) {
      const m = milestones(reduced);
      const ph = phase.get();
      if (ph === PHASE.inserting && now - at >= m.sunk) setPhase(PHASE.reading);
      if (ph === PHASE.reading && now - at >= m.read) setPhase(PHASE.offer);
      if (!fired.get().success && now - at >= m.turned) {
        fired.set({ success: true });
        haptic('success');
      }
    }
    const c0 = closeAt.get();
    if (c0 >= 0 && now - c0 >= (reduced ? REDUCED_MS : CLOSE.out + CLOSE.back)) {
      closeAt.set(-1);
      releasedAt.set(-1);
      pullX.set(0);
      pullY.set(0);
      setPhase(PHASE.choosing);
    }

    if (!autoPlay) return;
    const s = script.get();
    if (!s.on) {
      const resting = phase.get() === PHASE.offer || (phase.get() === PHASE.choosing && Math.abs(offset.get() - Math.round(offset.get())) < 0.01);
      const quiet = !s.touching && resting ? s.quiet + dt : 0;
      if (quiet >= RESUME_MS && phase.get() === PHASE.offer) {
        // Left on the gift: close it; the cards come back and the demo can start again.
        close();
        script.set({ ...s, quiet: 0 });
        return;
      }
      if (quiet >= RESUME_MS && selected.get() !== START_INDEX) {
        snapTo(START_INDEX, 0);
        script.set({ ...s, quiet: 0 });
        return;
      }
      script.set({ ...s, quiet, on: quiet >= RESUME_MS && phase.get() === PHASE.choosing, t: 0 });
      return;
    }
    const prev = s.t;
    const t = prev + dt;
    let gx = ghost.get().x;
    let gy = ghost.get().y;
    let go = ghost.get().o;
    const swipe = (from: number, to: number, x0: number, x1: number) => {
      'worklet';
      if (prev < from && t >= from) {
        down(x0, lay.cardY, true);
        gx = x0;
        gy = lay.cardY;
        go = 1;
      }
      if (t > from && prev < to) {
        const u = Math.min(1, (t - from) / (to - from));
        gx = x0 + (x1 - x0) * ease(u);
        gy = lay.cardY + 4 * Math.sin(Math.PI * u);
        move(gx, gy);
      }
      if (prev < to && t >= to) {
        up(((x1 - x0) / (to - from)) * 1000 * 0.6, 0);
        go = 0;
      }
    };
    for (let n = 0; n < SCRIPT.swipes.length; n += 1) {
      const w = SCRIPT.swipes[n];
      swipe(w.at, w.at + w.ms, cx - w.dx / 2, cx + w.dx / 2);
    }
    if (prev < SCRIPT.pullDown && t >= SCRIPT.pullDown) {
      down(cx + 12, lay.cardY + 40, true);
      gx = cx + 12;
      gy = lay.cardY + 40;
      go = 1;
    }
    if (t > SCRIPT.pullDown && prev < SCRIPT.pullUp) {
      // A beat of stillness, then a pull that gathers speed and slows into the slot.
      const u = ease((t - SCRIPT.pullDown - 120) / (SCRIPT.pullUp - SCRIPT.pullDown - 220));
      gx = cx + 12 - 14 * u;
      gy = lay.cardY + 40 + SCRIPT.pullBy * u;
      move(gx, gy);
    }
    if (prev < SCRIPT.pullUp && t >= SCRIPT.pullUp) {
      up(0, 0);
      go = 0;
    }
    if (prev < SCRIPT.done && t >= SCRIPT.done) close();
    ghost.set({ x: gx, y: gy, o: go });
    script.set({ ...script.get(), t: t >= LOOP_MS ? t - LOOP_MS : t });
  });

  return useMemo(() => ({
    values: { clock, offset, pullX, pullY, lift, phase, selected, armed, titleAt, titleWas, releasedAt, releasedFrom, closeAt, ghost },
    input: { down, move, up, takeOver, letGo, activate, step, close },
  // Worklets close over shared values whose identity never changes.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);
}

export type GiftValues = ReturnType<typeof useGiftSlot>['values'];
