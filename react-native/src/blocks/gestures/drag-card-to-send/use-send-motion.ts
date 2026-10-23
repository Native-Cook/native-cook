/**
 * Every moving value of the block and the inputs that move them: down, move, up, Undo.
 * A real finger and the autoplay script call the same functions, so the demo is the interaction.
 *
 * rest ─pull─▶ pulling ─let go with an amount─▶ falling ─drop meets the person─▶ sent ─Undo / 4 s─▶ rest
 *                 └─let go without one─▶ rest (the drop goes back into the island)
 */
import { useMemo } from 'react';
import {
  Easing, ReduceMotion, cancelAnimation, useFrameCallback, useSharedValue, withDelay, withSequence, withSpring, withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { clamp01, hangAt, stepAt, type Flight } from './liquid-look';
import { FLIGHT, LOOP_MS, PULL, RESUME_AFTER_MS, SCRIPT, SEND, SPRINGS, dropRadius, easeInOut, easeOut } from './motion';
import { LAYOUT, PEOPLE, STEPS, type Layout } from './theme';

export const PHASE = { rest: 0, pulling: 1, falling: 2, sent: 3 } as const;
export type Haptic = 'tick' | 'success' | 'light';

type Callbacks = {
  onPhase: (phase: number) => void;
  /** The amount (detent index) and the person the pull points at, -1 for none. Called per detent, never per frame. */
  onDraft: (step: number, person: number) => void;
  onSent: (person: number, cents: number) => void;
  onUndo: () => void;
  onHaptic: (kind: Haptic) => void;
};

const TETHER_FALLOFF = 80;
/** The tether: the card follows the pull less and less, and never past `PULL.tether`. */
export const tether = (d: number) => {
  'worklet';
  return d >= 0 ? PULL.tether * (1 - Math.exp(-d / TETHER_FALLOFF)) : d * PULL.upResist;
};
/** The pull that puts the card at `y`: a finger grabbing a card on its way back takes it from where it is. */
const untether = (y: number) => {
  'worklet';
  if (y < 0) return y / PULL.upResist;
  return -TETHER_FALLOFF * Math.log(1 - Math.min(y / PULL.tether, 0.999));
};
/** How fast the card moves per pt of pull at `d`: hands the finger's release velocity to the card's spring. */
const tetherSlope = (d: number) => {
  'worklet';
  return d >= 0 ? (PULL.tether / TETHER_FALLOFF) * Math.exp(-d / TETHER_FALLOFF) : PULL.upResist;
};
/** The scripted finger: a human pull, slow in, slow out. */
const smoother = (t: number) => {
  'worklet';
  const c = clamp01(t);
  return c * c * c * (c * (c * 6 - 15) + 10);
};

export function useSendMotion(lay: Layout, autoPlay: boolean, reduced: boolean, cb: Callbacks) {
  const phase = useSharedValue<number>(PHASE.rest);
  const active = useSharedValue(false);
  const scripted = useSharedValue(false);
  /** Where the finger came down, the pull the card already had then (`base`), and the finger's travel so far. */
  const down0 = useSharedValue({ x: 0, y: 0, base: 0, travel: 0 });
  const cardY = useSharedValue(0);
  const press = useSharedValue(0);
  const step = useSharedValue(-1);
  /** The hanging drop's radius. */
  const r = useSharedValue(0);
  const sel = useSharedValue(-1);
  const ringX = useSharedValue(lay.avatarX[0]);
  const ringOn = useSharedValue(0);
  /** The flight clock (-1 when nothing falls) and its path. */
  const t = useSharedValue(-1);
  const flight = useSharedValue<Flight>({ fromX: 0, fromY: 0, r: 0, toX: 0, toY: 0 });
  const contact = useSharedValue(0);
  const target = useSharedValue(-1);
  const ripple = useSharedValue(0);
  const catchAmt = useSharedValue(0);
  const receipt = useSharedValue(0);
  const undo = useSharedValue(0);
  const script = useSharedValue({ on: autoPlay, t: 0, quiet: 0, touching: false, x: 0, y: 0 });

  const { onPhase, onDraft, onSent, onUndo, onHaptic } = cb;
  const never = ReduceMotion.Never;

  const setPhase = (p: number) => {
    'worklet';
    if (phase.get() === p) return;
    phase.set(p);
    scheduleOnRN(onPhase, p);
  };
  const haptic = (kind: Haptic) => {
    'worklet';
    if (!scripted.get()) scheduleOnRN(onHaptic, kind);
  };
  const draft = () => {
    'worklet';
    scheduleOnRN(onDraft, step.get(), sel.get());
  };
  const cardTop = () => {
    'worklet';
    return lay.cardTop + cardY.get();
  };

  const setStep = (s: number) => {
    'worklet';
    const to = s >= 0 ? dropRadius(STEPS[s]) : 0;
    // Each detent is one gulp of liquid; Reduce Motion takes the new size without the wobble.
    if (reduced) r.set(withTiming(to, { duration: 90, easing: easeOut, reduceMotion: never }));
    else r.set(withSpring(to, SPRINGS.gulp));
    step.set(s);
  };
  const setSel = (i: number) => {
    'worklet';
    if (i === sel.get()) return;
    if (i >= 0) {
      if (sel.get() < 0 || ringOn.get() < 0.05) ringX.set(lay.avatarX[i]);
      else ringX.set(reduced ? lay.avatarX[i] : withSpring(lay.avatarX[i], SPRINGS.ring));
      ringOn.set(withTiming(1, { duration: SEND.ringInMs, easing: easeOut, reduceMotion: never }));
    } else {
      ringOn.set(withTiming(0, { duration: SEND.ringOutMs, easing: easeOut, reduceMotion: never }));
    }
    sel.set(i);
  };

  const endSent = () => {
    'worklet';
    cancelAnimation(undo);
    // A morph back into the hardware island: ease-in-out, whose slow start fades the content before the pill folds.
    const fold = { duration: SEND.closeMs, easing: easeInOut, reduceMotion: never };
    undo.set(withTiming(0, fold));
    receipt.set(withTiming(0, fold));
    if (phase.get() === PHASE.sent) setPhase(PHASE.rest);
  };

  /** The drop meets the person: they catch it, the ring rings out, the island becomes the receipt. */
  const land = (i: number, s: number) => {
    'worklet';
    if (!scripted.get()) scheduleOnRN(onHaptic, 'success');
    if (!reduced) {
      catchAmt.set(1);
      catchAmt.set(withSpring(0, SPRINGS.catch));
    }
    ripple.set(0);
    ripple.set(withTiming(1, { duration: SEND.rippleMs, easing: easeOut, reduceMotion: never }, (done) => {
      if (!done) return;
      sel.set(-1);
      ringOn.set(0);
      ripple.set(0);
    }));
    receipt.set(reduced
      ? withTiming(1, { duration: SEND.reducedMs, easing: easeOut, reduceMotion: never })
      : withDelay(60, withSpring(1, SPRINGS.receipt)));
    setPhase(PHASE.sent);
    scheduleOnRN(onSent, i, STEPS[s] * 100);
    undo.set(1);
    undo.set(withTiming(0, { duration: SEND.undoMs, easing: Easing.linear, reduceMotion: never }, (done) => {
      if (done) endSent();
    }));
  };

  /** The card back to its slot, continuing at `velocity` (pt/s of the card, not of the finger). */
  const cardHome = (velocity: number) => {
    'worklet';
    cardY.set(withSpring(0, reduced
      ? { ...SPRINGS.cardReduced, velocity, reduceMotion: never }
      : { ...SPRINGS.card, velocity }));
  };
  const release = () => {
    'worklet';
    press.set(withTiming(0, { duration: SEND.pressOutMs, easing: easeOut }));
  };

  const settle = () => {
    'worklet';
    t.set(-1);
    step.set(-1);
    draft();
  };

  const send = (i: number, s: number, velocity: number) => {
    'worklet';
    setPhase(PHASE.falling);
    target.set(i);
    cardHome(velocity);
    release();
    if (reduced) {
      // No fall: the drop goes back into the island and the island turns into the receipt.
      r.set(withTiming(0, { duration: SEND.reducedMs, easing: easeOut, reduceMotion: never }, (done) => {
        if (!done) return;
        land(i, s);
        settle();
      }));
      return;
    }
    const rad = Math.max(r.get(), 1);
    const from = hangAt(lay, rad);
    flight.set({ fromX: from.x, fromY: from.y, r: rad, toX: lay.avatarX[i], toY: lay.avatarY });
    r.set(0);
    t.set(0);
    t.set(withTiming(1, { duration: FLIGHT.ms, easing: Easing.linear }, (done) => {
      if (done) settle();
    }));
    contact.set(0);
    contact.set(withDelay(FLIGHT.contact * FLIGHT.ms, withTiming(1, { duration: 1 }, (done) => {
      if (done) land(i, s);
    })));
  };

  const cancel = (tap: boolean, velocity: number) => {
    'worklet';
    setSel(-1);
    setStep(-1);
    draft();
    setPhase(PHASE.rest);
    const out = { duration: SEND.nodMs, easing: easeOut };
    if (tap && !reduced) {
      // A tap: the card nods down to teach the pull, and the island lets out a bead of liquid.
      cardY.set(withSequence(withTiming(SEND.nod, out), withSpring(0, SPRINGS.card)));
      r.set(withSequence(withTiming(SEND.bead, out), withSpring(0, SPRINGS.gulp)));
      return;
    }
    if (tap) {
      // Reduce Motion: the card stays put; the bead still shows where the liquid comes from.
      r.set(withSequence(
        never,
        withTiming(SEND.bead, { ...out, reduceMotion: never }),
        withTiming(0, { duration: SEND.reducedMs, easing: easeOut, reduceMotion: never }),
      ));
    }
    cardHome(velocity);
  };

  const down = (x: number, y: number, fromScript: boolean) => {
    'worklet';
    active.set(false);
    const ph = phase.get();
    if (ph === PHASE.falling || ph === PHASE.pulling) return;
    const top = cardTop();
    if (x < LAYOUT.edge || x > LAYOUT.edge + lay.cardW || y < top || y > top + lay.cardH) return;
    if (ph === PHASE.sent) endSent();
    if (t.get() >= 0) { cancelAnimation(t); settle(); }
    scripted.set(fromScript);
    active.set(true);
    cancelAnimation(cardY);
    down0.set({ x, y, base: untether(cardY.get()), travel: 0 });
    press.set(withTiming(1, { duration: SEND.pressInMs, easing: easeOut }));
    setPhase(PHASE.pulling);
    // A new pull starts with no choice: the hint must not show the last one.
    sel.set(-1);
    draft();
  };

  const move = (x: number, y: number) => {
    'worklet';
    if (!active.get() || phase.get() !== PHASE.pulling) return;
    const d0 = down0.get();
    const d = y - d0.y;
    down0.set({ ...d0, travel: Math.max(d0.travel, Math.abs(x - d0.x) + Math.abs(d)) });
    cardY.set(tether(d0.base + d));
    // Detents count the finger's own travel, so a touch on a card still on its way back never sends by itself.
    const s = stepAt(d, PULL.dead, PULL.step);
    let changed = false;
    let ticked = false;
    if (s !== step.get()) {
      setStep(s);
      haptic('tick');
      changed = true;
      ticked = true;
    }
    // Sideways chooses who: the face nearest the finger's column, once there is an amount.
    const col = s >= 0 ? Math.min(PEOPLE.length - 1, Math.max(0, Math.round((x - lay.avatarX[0]) / lay.pitch))) : -1;
    if (col !== sel.get()) {
      setSel(col);
      // One tick per change: a detent and a new person in the same frame share it.
      if (col >= 0 && !ticked) haptic('tick');
      changed = true;
    }
    if (changed) draft();
  };

  /** The finger lifts, moving at `vy` pt/s; the card's spring carries on at the speed the tether gave it. */
  const up = (vy: number, y: number) => {
    'worklet';
    if (!active.get()) return;
    active.set(false);
    if (phase.get() !== PHASE.pulling) return;
    release();
    const d0 = down0.get();
    const velocity = tetherSlope(d0.base + y - d0.y) * vy;
    const s = step.get();
    const i = sel.get();
    if (s >= 0 && i >= 0) send(i, s, velocity);
    else cancel(d0.travel < PULL.tap, velocity);
  };

  /** Accessibility (and any caller without a finger): send an amount to a person at once. */
  const sendTo = (i: number, s: number) => {
    'worklet';
    const ph = phase.get();
    if (ph === PHASE.falling || ph === PHASE.pulling) return;
    if (ph === PHASE.sent) endSent();
    scripted.set(false);
    setStep(s);
    if (!reduced) r.set(dropRadius(STEPS[s]));
    setSel(i);
    draft();
    send(i, s, 0);
  };

  const tapUndo = (fromScript: boolean) => {
    'worklet';
    if (phase.get() !== PHASE.sent) return;
    // The haptic of a real tap fires in the button's own handler, on the press's frame.
    scripted.set(fromScript);
    endSent();
    scheduleOnRN(onUndo);
  };

  /** A touch on the screen: the script lets go at once, and comes back 3.5 s after the screen is at rest. */
  const takeOver = () => {
    'worklet';
    const s = script.get();
    if (s.on && scripted.get() && active.get()) {
      // The scripted finger lets go without sending.
      active.set(false);
      release();
      cancel(false, 0);
    }
    script.set({ ...s, on: false, quiet: 0, touching: true });
  };
  const letGo = () => {
    'worklet';
    script.set({ ...script.get(), touching: false, quiet: 0 });
  };

  useFrameCallback((info) => {
    if (!autoPlay) return;
    const dt = Math.min(info.timeSincePreviousFrame ?? 16, 64);
    const s = script.get();
    if (!s.on) {
      const resting = !s.touching && phase.get() === PHASE.rest && receipt.get() < 0.01 && t.get() < 0;
      const quiet = resting ? s.quiet + dt : 0;
      script.set({ ...s, quiet, on: quiet >= RESUME_AFTER_MS, t: 0 });
      return;
    }
    const prev = s.t;
    const now = prev + dt;
    let { x, y } = s;
    const x0 = LAYOUT.edge + SCRIPT.at.x;
    const y0 = lay.cardTop + SCRIPT.at.y;
    if (prev < SCRIPT.down && now >= SCRIPT.down) {
      x = x0;
      y = y0;
      down(x, y, true);
    }
    if (now > SCRIPT.pullStart && prev < SCRIPT.pullEnd) {
      y = y0 + SCRIPT.pullBy * smoother((now - SCRIPT.pullStart) / (SCRIPT.pullEnd - SCRIPT.pullStart));
      move(x, y);
    }
    if (now > SCRIPT.slideStart && prev < SCRIPT.slideEnd) {
      const u = smoother((now - SCRIPT.slideStart) / (SCRIPT.slideEnd - SCRIPT.slideStart));
      x = x0 + (lay.avatarX[SCRIPT.slideTo] - x0) * u;
      move(x, y);
    }
    if (prev < SCRIPT.release && now >= SCRIPT.release) up(0, y);
    if (prev < SCRIPT.undo && now >= SCRIPT.undo) tapUndo(true);
    script.set({ ...s, t: now >= LOOP_MS ? now - LOOP_MS : now, x, y });
  });

  return useMemo(() => ({
    values: { phase, cardY, press, step, r, sel, ringX, ringOn, t, flight, target, ripple, catchAmt, receipt, undo },
    input: { down, move, up, sendTo, tapUndo, takeOver, letGo },
  // The worklets close over shared values that never change identity.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);
}

export type SendMotion = ReturnType<typeof useSendMotion>;
export type SendValues = SendMotion['values'];
