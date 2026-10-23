/**
 * State and input for the gift slot. The carousel and the pull are springs under the finger; once the
 * slot takes a card, everything that follows (insert, activation, print, confetti) is a pure function
 * of one clock and the moment the card was released, so the loop repeats exactly.
 */
import { useMemo } from 'react';
import { useFrameCallback, useSharedValue, withSpring } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { ACTIVATE, CLOSE, DRAG, INSERT, LOOP_MS, PRINT, QA_HOLD_AT, REDUCED_MS, RESUME_AFTER_MS, SCRIPT, SPRINGS } from './motion';
import { GIFTS, LAYOUT, START_INDEX } from './theme';

export const PHASE = { choosing: 0, pulling: 1, swiping: 2, inserting: 3, activating: 4, offer: 5, closing: 6 } as const;
export type Haptic = 'select' | 'armed' | 'insert' | 'success' | 'light';

/** Milestones after a release, in ms from the release. */
export function milestones(reduced: boolean) {
  'worklet';
  if (reduced) {
    return { sunk: REDUCED_MS, printStart: REDUCED_MS + ACTIVATE.ms, printed: REDUCED_MS + ACTIVATE.ms + REDUCED_MS, risen: REDUCED_MS + ACTIVATE.ms + REDUCED_MS };
  }
  const sunk = INSERT.centreMs + INSERT.sinkMs;
  const printStart = sunk + ACTIVATE.ms;
  const printed = printStart + PRINT.feeds * (PRINT.feedMs + PRINT.feedGap);
  return { sunk, printStart, printed, risen: printed + 700 };
}

const hash = (n: number, salt: number) => {
  'worklet';
  const x = Math.sin(n * 127.1 + salt * 311.7) * 43758.5453;
  return x - Math.floor(x);
};

type Callbacks = { onPhase: (p: number) => void; onArmed: (armed: boolean) => void; onIndex: (i: number) => void; onHaptic: (k: Haptic) => void };

export function useGiftSlot(width: number, autoPlay: boolean, reduced: boolean, cb: Callbacks) {
  const clock = useSharedValue(0);
  const offset = useSharedValue<number>(START_INDEX);
  const pullX = useSharedValue(0);
  const pullY = useSharedValue(0);
  const lift = useSharedValue(1);
  const phase = useSharedValue<number>(PHASE.choosing);
  const selected = useSharedValue<number>(START_INDEX);
  const armed = useSharedValue(false);
  const releasedAt = useSharedValue(-1);
  const releasedFrom = useSharedValue({ x: 0, y: 0, s: 1 });
  const closeAt = useSharedValue(-1);
  const touch = useSharedValue({ active: false, scripted: false, mode: 0, x0: 0, y0: 0, o0: 0, onCard: false });
  /** Confetti: x, y, vx, vy, angle, spin, life, colour per piece. */
  const confetti = useSharedValue<number[]>(new Array(PRINT.confetti * 8).fill(0));
  const fired = useSharedValue({ confetti: false, offer: false, done: false });
  const script = useSharedValue({ on: autoPlay, t: 0, quiet: 0, touching: false, lastX: 0, lastT: 0 });

  const { onPhase, onArmed, onIndex, onHaptic } = cb;
  const centreX = width / 2;

  const setPhase = (p: number) => {
    'worklet';
    if (phase.get() === p) return;
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
    armed.set(a);
    scheduleOnRN(onArmed, a);
    if (a) haptic('armed');
  };
  const pullScale = (y: number) => {
    'worklet';
    return 1 - (1 - DRAG.minScale) * Math.min(1, Math.max(0, y / DRAG.full));
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
    releasedFrom.set({ x: pullX.get(), y: pullY.get(), s: pullScale(pullY.get()) * lift.get() });
    releasedAt.set(clock.get());
    fired.set({ confetti: false, offer: false, done: false });
    setPhase(PHASE.inserting);
    haptic('insert');
  };

  const down = (x: number, y: number, scripted: boolean) => {
    'worklet';
    const ph = phase.get();
    if (ph !== PHASE.choosing) return;
    const onCard = Math.abs(x - centreX) < LAYOUT.cardW / 2 + 12 && Math.abs(y - LAYOUT.cardY) < LAYOUT.cardH / 2 + 12;
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
      let o = t.o0 - dx / LAYOUT.spacing;
      // Rubber band past the first and last gift.
      if (o < 0) o *= 0.3;
      if (o > GIFTS.length - 1) o = GIFTS.length - 1 + (o - GIFTS.length + 1) * 0.3;
      offset.set(o);
    } else if (mode === 1) {
      pullX.set(dx * 0.3);
      pullY.set(dy < 0 ? dy * DRAG.upResist : dy);
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
      const o = offset.get();
      const fling = Math.abs(vx) > DRAG.swipeVelocity ? -Math.sign(vx) * 0.5 : 0;
      snapTo(Math.round(o + fling), -vx / LAYOUT.spacing);
      setPhase(PHASE.choosing);
    } else if (t.mode === 1) {
      if (armed.get()) { setArmed(false); release(); return; }
      pullX.set(withSpring(0, { ...SPRINGS.back, velocity: vx }));
      pullY.set(withSpring(0, { ...SPRINGS.back, velocity: vy }));
      setPhase(PHASE.choosing);
    } else {
      setPhase(PHASE.choosing);
    }
  };

  /** Accessible alternative: activate the selected gift without dragging. */
  const activate = () => {
    'worklet';
    if (phase.get() !== PHASE.choosing) return;
    touch.set({ ...touch.get(), scripted: false });
    pullX.set(0);
    pullY.set(0);
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

  const spawnConfetti = () => {
    'worklet';
    if (reduced) return;
    confetti.modify((c) => {
      'worklet';
      for (let n = 0; n < PRINT.confetti; n += 1) {
        const o = n * 8;
        const a = -Math.PI / 2 + (hash(n, 1) - 0.5) * 1.9;
        const s = 380 + hash(n, 2) * 520;
        c[o] = centreX + (hash(n, 3) - 0.5) * LAYOUT.slotW * 0.8;
        c[o + 1] = LAYOUT.slotY - 4;
        c[o + 2] = Math.cos(a) * s;
        c[o + 3] = Math.sin(a) * s;
        c[o + 4] = hash(n, 4) * Math.PI;
        c[o + 5] = (hash(n, 5) - 0.5) * 18;
        c[o + 6] = PRINT.confettiLife * (0.7 + 0.3 * hash(n, 6));
        c[o + 7] = Math.floor(hash(n, 7) * 3);
      }
      return c;
    });
  };

  const takeOver = () => {
    'worklet';
    const s = script.get();
    if (s.on && touch.get().scripted && touch.get().active) {
      // Disarm first so the scripted finger's release cancels instead of dropping the card in.
      setArmed(false);
      up(0, 0);
    }
    script.set({ ...s, on: false, quiet: 0, touching: true });
  };
  const letGo = () => {
    'worklet';
    script.set({ ...script.get(), touching: false, quiet: 0 });
  };

  useFrameCallback((info) => {
    const dt = Math.min(info.timeSincePreviousFrame ?? 16, 64);
    const now = clock.get() + dt;
    clock.set(now);

    // Timeline after a release.
    const at = releasedAt.get();
    if (at >= 0) {
      const m = milestones(reduced);
      const f = fired.get();
      const ph = phase.get();
      if (ph === PHASE.inserting && now - at >= m.sunk) setPhase(PHASE.activating);
      if (ph === PHASE.activating && now - at >= m.printStart) setPhase(PHASE.offer);
      if (!f.confetti && now - at >= m.printed) {
        fired.set({ ...f, confetti: true });
        spawnConfetti();
        haptic('success');
      }
    }
    const c0 = closeAt.get();
    if (c0 >= 0 && now - c0 >= (reduced ? REDUCED_MS : CLOSE.ms + CLOSE.returnMs)) {
      closeAt.set(-1);
      releasedAt.set(-1);
      pullX.set(0);
      pullY.set(0);
      setPhase(PHASE.choosing);
    }

    if (!reduced) {
      const k = dt / 1000;
      confetti.modify((c) => {
        'worklet';
        for (let n = 0; n < PRINT.confetti; n += 1) {
          const o = n * 8;
          if (c[o + 6] <= 0) continue;
          c[o + 3] += PRINT.gravity * k;
          c[o + 2] *= 0.985;
          c[o] += c[o + 2] * k;
          c[o + 1] += c[o + 3] * k;
          c[o + 4] += c[o + 5] * k;
          c[o + 6] -= k;
        }
        return c;
      });
    }

    if (!autoPlay) return;
    const s = script.get();
    if (!s.on) {
      const resting = phase.get() === PHASE.offer || (phase.get() === PHASE.choosing && Math.abs(offset.get() - Math.round(offset.get())) < 0.01);
      const idle = !s.touching && resting;
      const quiet = idle ? s.quiet + dt : 0;
      if (quiet >= RESUME_AFTER_MS && phase.get() === PHASE.offer && !s.touching) {
        // Left on the offer: close it, then the carousel comes back and the demo can start again.
        close();
        script.set({ ...s, quiet: 0 });
        return;
      }
      if (quiet >= RESUME_AFTER_MS && selected.get() !== START_INDEX) {
        // Back to the first gift before the demo starts again.
        snapTo(START_INDEX, 0);
        script.set({ ...s, quiet: 0 });
        return;
      }
      script.set({ ...s, quiet, on: quiet >= RESUME_AFTER_MS && phase.get() === PHASE.choosing, t: 0 });
      return;
    }
    const prev = s.t;
    const t = QA_HOLD_AT !== null && prev >= QA_HOLD_AT ? prev : prev + dt;
    let lastX = s.lastX;
    const ease = (u: number) => {
      'worklet';
      const c = Math.min(1, Math.max(0, u));
      return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
    };
    const swipe = (from: number, to: number, x0: number, x1: number) => {
      'worklet';
      if (prev < from && t >= from) { down(x0, LAYOUT.cardY, true); lastX = x0; }
      if (t > from && prev < to) {
        const x = x0 + (x1 - x0) * ease((t - from) / (to - from));
        move(x, LAYOUT.cardY + 4 * Math.sin(Math.PI * Math.min(1, (t - from) / (to - from))));
        lastX = x;
      }
      if (prev < to && t >= to) up(((x1 - x0) / (to - from)) * 1000 * 0.6, 0);
    };
    for (let n = 0; n < SCRIPT.swipes.length; n += 1) {
      const w = SCRIPT.swipes[n];
      swipe(w.at, w.at + w.ms, centreX - w.dx / 2, centreX + w.dx / 2);
    }
    if (prev < SCRIPT.pullDown && t >= SCRIPT.pullDown) down(centreX + 12, LAYOUT.cardY + 30, true);
    if (t > SCRIPT.pullDown && prev < SCRIPT.pullUp) {
      // A beat of stillness, then a pull that accelerates and slows into the slot.
      const u = ease((t - SCRIPT.pullDown - 120) / (SCRIPT.pullUp - SCRIPT.pullDown - 220));
      move(centreX + 12 - 23 * u, LAYOUT.cardY + 30 + SCRIPT.pullBy * u);
    }
    if (prev < SCRIPT.pullUp && t >= SCRIPT.pullUp) up(0, 0);
    if (prev < SCRIPT.done && t >= SCRIPT.done) close();
    script.set({ ...script.get(), t: t >= LOOP_MS ? t - LOOP_MS : t, lastX });
  });

  return useMemo(() => ({
    values: { clock, offset, pullX, pullY, lift, phase, selected, armed, releasedAt, releasedFrom, closeAt, confetti },
    input: { down, move, up, takeOver, letGo, activate, step, close },
  // Worklets close over shared values whose identity never changes.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);
}

export type GiftValues = ReturnType<typeof useGiftSlot>['values'];
