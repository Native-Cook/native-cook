/**
 * The envelope's state. Before the reveal commits, the finger owns `p` (0 sealed → 1 card out).
 * After it commits, every pose is a function of one clock and the commit time, so the loop repeats.
 */
import { useMemo } from 'react';
import { useFrameCallback, useSharedValue, withSpring } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { LOOP_MS, OPEN, QA_HOLD_AT, REDUCED_MS, RESET, RESUME_AFTER_MS, REVEAL, SCRIPT, SPRINGS } from './motion';

/** The scripted finger starts on the envelope's lower half. */
const LAYOUT_ENV_FINGER = 170;

export const PHASE = { sealed: 0, dragging: 1, revealing: 2, revealed: 3, resetting: 4 } as const;
export type Haptic = 'flap' | 'commit' | 'success' | 'light' | 'crack';

/** How long the commit takes to finish opening from progress p0. */
export function finishMs(p0: number, reduced: boolean) {
  'worklet';
  return reduced ? REDUCED_MS : 260 + REVEAL.finishMs * (1 - p0);
}

const hash = (n: number, salt: number) => {
  'worklet';
  const x = Math.sin(n * 91.7 + salt * 47.3) * 43758.5453;
  return x - Math.floor(x);
};

type Callbacks = { onPhase: (p: number) => void; onHaptic: (k: Haptic) => void };

/** `centreY` is where the card lands (confetti origin); `envTop` places the scripted finger on the envelope. */
export function useEnvelope(width: number, centreY: number, envTop: number, autoPlay: boolean, reduced: boolean, cb: Callbacks) {
  const clock = useSharedValue(0);
  const p = useSharedValue(0);
  const tilt = useSharedValue(0);
  const phase = useSharedValue<number>(PHASE.sealed);
  const commit = useSharedValue({ at: -1, p0: 0, tilt0: 0 });
  const resetAt = useSharedValue(-1);
  const breath = useSharedValue(0);
  const confetti = useSharedValue<number[]>(new Array(REVEAL.confetti * 8).fill(0));
  const touch = useSharedValue({ active: false, scripted: false, x0: 0, y0: 0, p0: 0, flapped: false, cracked: false });
  const script = useSharedValue({ on: autoPlay, t: 0, quiet: 0, touching: false });
  const fired = useSharedValue(false);
  const { onPhase, onHaptic } = cb;

  const setPhase = (ph: number) => {
    'worklet';
    if (phase.get() === ph) return;
    phase.set(ph);
    scheduleOnRN(onPhase, ph);
  };
  const haptic = (k: Haptic) => {
    'worklet';
    if (!touch.get().scripted) scheduleOnRN(onHaptic, k);
  };

  const doCommit = () => {
    'worklet';
    commit.set({ at: clock.get(), p0: p.get(), tilt0: tilt.get() });
    fired.set(false);
    setPhase(PHASE.revealing);
    haptic('commit');
  };

  /** Interruption: a touch while the envelope is still opening (before the card leaves) reverses it. */
  const cancelReveal = () => {
    'worklet';
    const c = commit.get();
    const e = clock.get() - c.at;
    const fin = finishMs(c.p0, reduced);
    if (c.at < 0 || e >= fin) return false;
    const u = Math.min(1, Math.max(0, e / fin));
    const now = c.p0 + (1 - c.p0) * (1 - Math.pow(1 - u, 3));
    commit.set({ at: -1, p0: 0, tilt0: 0 });
    p.set(now);
    p.set(withSpring(0, SPRINGS.close));
    tilt.set(withSpring(0, SPRINGS.close));
    setPhase(PHASE.sealed);
    haptic('light');
    return true;
  };

  const down = (x: number, y: number, scripted: boolean) => {
    'worklet';
    if (phase.get() === PHASE.revealing && !scripted && cancelReveal()) return;
    if (phase.get() !== PHASE.sealed) return;
    touch.set({ active: true, scripted, x0: x, y0: y, p0: p.get(), flapped: false, cracked: false });
    p.set(p.get());
    setPhase(PHASE.dragging);
  };
  const move = (x: number, y: number) => {
    'worklet';
    const t = touch.get();
    if (!t.active || phase.get() !== PHASE.dragging) return;
    let next = t.p0 + (t.y0 - y) / OPEN.travel;
    if (next < 0) next = 0;
    if (next > 1) next = 1 + (next - 1) * 0.2;
    p.set(next);
    const out = Math.max(0, (next - OPEN.flapShare) / (1 - OPEN.flapShare));
    tilt.set(reduced ? 0 : Math.max(-OPEN.tiltMax, Math.min(OPEN.tiltMax, (x - t.x0) * 0.08)) * Math.min(1, out * 2));
    // The seal cracks early in the lift (rigid tap), the flap clears at the fold (light tap).
    if (!t.cracked && next >= OPEN.flapShare * 0.22) { touch.set({ ...touch.get(), cracked: true }); haptic('crack'); }
    if (!t.flapped && next >= OPEN.flapShare) { touch.set({ ...touch.get(), flapped: true }); haptic('flap'); }
  };
  const up = (vy: number) => {
    'worklet';
    const t = touch.get();
    if (!t.active) return;
    touch.set({ ...t, active: false });
    if (phase.get() !== PHASE.dragging) return;
    if (p.get() >= OPEN.commit || (vy < -900 && p.get() > 0.15)) { doCommit(); return; }
    setPhase(PHASE.sealed);
    if (p.get() > 0.08) haptic('light');
    p.set(withSpring(0, { ...SPRINGS.close, velocity: -vy / OPEN.travel }));
    tilt.set(withSpring(0, SPRINGS.close));
  };
  /** The button, and the accessible alternative: open without dragging. */
  const open = () => {
    'worklet';
    if (phase.get() !== PHASE.sealed) return;
    touch.set({ ...touch.get(), scripted: false });
    doCommit();
  };
  const reset = () => {
    'worklet';
    const ph = phase.get();
    if (ph !== PHASE.revealed && ph !== PHASE.revealing) return;
    resetAt.set(clock.get());
    setPhase(PHASE.resetting);
  };

  const spawn = () => {
    'worklet';
    if (reduced) return;
    confetti.modify((c) => {
      'worklet';
      for (let n = 0; n < REVEAL.confetti; n += 1) {
        const o = n * 8;
        const a = -Math.PI / 2 + (hash(n, 1) - 0.5) * Math.PI * 1.5;
        const s = 260 + hash(n, 2) * 620;
        c[o] = width / 2 + (hash(n, 3) - 0.5) * 120;
        c[o + 1] = centreY + (hash(n, 4) - 0.5) * 60;
        c[o + 2] = Math.cos(a) * s;
        c[o + 3] = Math.sin(a) * s;
        c[o + 4] = hash(n, 5) * Math.PI;
        c[o + 5] = (hash(n, 6) - 0.5) * 16;
        c[o + 6] = REVEAL.confettiLife * (0.6 + 0.4 * hash(n, 7));
        c[o + 7] = Math.floor(hash(n, 8) * 6);
      }
      return c;
    });
  };

  const takeOver = () => {
    'worklet';
    const s = script.get();
    if (s.on && touch.get().scripted && touch.get().active) {
      // The scripted finger lets go without opening: the flap falls closed.
      touch.set({ ...touch.get(), active: false });
      setPhase(PHASE.sealed);
      p.set(withSpring(0, SPRINGS.close));
      tilt.set(withSpring(0, SPRINGS.close));
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
    const c = commit.get();
    if (c.at >= 0) {
      const done = finishMs(c.p0, reduced) + (reduced ? 0 : REVEAL.flyMs);
      if (!fired.get() && now - c.at >= finishMs(c.p0, reduced) + REVEAL.flyMs * 0.45) {
        fired.set(true);
        spawn();
        haptic('success');
      }
      if (phase.get() === PHASE.revealing && now - c.at >= done) setPhase(PHASE.revealed);
    }
    const r = resetAt.get();
    if (r >= 0 && now - r >= (reduced ? REDUCED_MS : RESET.fadeMs)) {
      resetAt.set(-1);
      commit.set({ at: -1, p0: 0, tilt0: 0 });
      p.set(0);
      tilt.set(0);
      setPhase(PHASE.sealed);
    }
    if (!reduced) {
      const k = dt / 1000;
      confetti.modify((cf) => {
        'worklet';
        for (let n = 0; n < REVEAL.confetti; n += 1) {
          const o = n * 8;
          if (cf[o + 6] <= 0) continue;
          cf[o + 2] *= REVEAL.drag;
          cf[o + 3] = cf[o + 3] * REVEAL.drag + REVEAL.gravity * k;
          cf[o] += cf[o + 2] * k + Math.sin(cf[o + 4] * 2) * 0.4;
          cf[o + 1] += cf[o + 3] * k;
          cf[o + 4] += cf[o + 5] * k;
          cf[o + 6] -= k;
        }
        return cf;
      });
    }

    const s = script.get();
    // The flap lifts a little now and then while sealed, to say it opens.
    breath.set(((s.on ? s.t : now) + OPEN.breathLead) % OPEN.breathMs);
    if (!autoPlay) return;
    if (!s.on) {
      const idle = !s.touching && !touch.get().active;
      const quiet = idle ? s.quiet + dt : 0;
      if (quiet >= RESUME_AFTER_MS && phase.get() === PHASE.revealed) { reset(); script.set({ ...s, quiet: 0 }); return; }
      script.set({ ...s, quiet, on: quiet >= RESUME_AFTER_MS && phase.get() === PHASE.sealed && p.get() < 0.01, t: 0 });
      return;
    }
    const prev = s.t;
    const t = QA_HOLD_AT !== null && prev >= QA_HOLD_AT ? prev : prev + dt;
    const x0 = width / 2 + 10;
    const y0 = envTop + LAYOUT_ENV_FINGER;
    if (prev < SCRIPT.down && t >= SCRIPT.down) down(x0, y0, true);
    if (t > SCRIPT.down && prev < SCRIPT.up) {
      const u = Math.min(1, Math.max(0, (t - SCRIPT.down - 80) / (SCRIPT.up - SCRIPT.down - 150)));
      const e = u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
      // Up with a slight drift to the right and back, so the card swings as it comes out.
      move(x0 + 34 * Math.sin(Math.PI * e), y0 - SCRIPT.rise * e);
    }
    if (prev < SCRIPT.up && t >= SCRIPT.up) up(0);
    if (prev < SCRIPT.reset && t >= SCRIPT.reset) reset();
    script.set({ ...script.get(), t: t >= LOOP_MS ? t - LOOP_MS : t });
  });

  return useMemo(() => ({
    values: { clock, p, tilt, phase, commit, resetAt, breath, confetti },
    input: { down, move, up, open, reset, takeOver, letGo },
  // Worklets close over shared values whose identity never changes.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);
}

export type EnvelopeValues = ReturnType<typeof useEnvelope>['values'];
