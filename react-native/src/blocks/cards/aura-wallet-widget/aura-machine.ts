import { useCallback, useEffect, useRef, useState } from "react";
import { AccessibilityInfo } from "react-native";
import {
  Easing,
  ReduceMotion,
  cancelAnimation,
  useAnimatedReaction,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import { scheduleOnRN, scheduleOnUI } from "react-native-worklets";
import type { AuraDrive } from "./aura-drive";
import {
  AuraPhase,
  type AuraPhaseValue,
  auraAmountAt,
  auraDepthFor,
  auraEnterLength,
  auraFlow,
  auraLipPose,
  auraNudgeLength,
  auraPullLimit,
  auraRubber,
  auraSendMoments,
  auraSinkDepth,
  auraSlotX,
} from "./aura-flow";
import { auraLandingHaptic, auraNudgeHaptic, auraTick } from "./aura-haptics";
import { auraDesign as D, auraMotion } from "./aura-palette";
import { FRIENDS, LAST } from "./aura-people";
import { usePreferences } from "./i18n";

// The UI clock is the score's conductor. RN only receives changed choices,
// amount steps and milestones; cents never make the round trip through a float.
export type AuraReceipt = { id: number; friend: number; cents: number };
export type AuraHintKind = "rest" | "armed" | "pulling" | "idle-pull" | "sending" | "sent";
export type AuraHint = { text: string; kind: AuraHintKind };
const initialReceipt: AuraReceipt = { id: 0, friend: -1, cents: Math.round(LAST.amount * 100) };
const form = { ...auraFlow.drop.form, reduceMotion: ReduceMotion.Never };
const morph = { ...auraFlow.pills.morph, reduceMotion: ReduceMotion.Never };
const linear = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };
const zeroOffsets = Array<number>(D.lip.count * 4).fill(0);

export function useAuraMachine(drive: AuraDrive, reduced: boolean) {
  const { t } = usePreferences();
  const { phase, chosen, formed, leaving, unformed, pullX, pullY, amount, clock,
    fromX, fromY, wide, nudge, weight, entered } = drive;
  const touched = useSharedValue(false);
  // The views' own presence, 0 until the glass is there to carry them.
  const shown = useSharedValue(0);
  const cueTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const cueStopped = useRef(false);
  const sinking = useSharedValue(false);
  const sinkClock = useSharedValue(0);
  const wasArmed = useSharedValue(false);
  const pullOwner = useSharedValue(-1);
  const landed = useSharedValue(false);
  const receiptClock = useSharedValue<number>(auraFlow.send.rippleLife);
  const receiptX = useSharedValue(0);
  // A rapid third choice has more history than the score's two drop slots.
  // Carry that history only in view transforms, decaying to the shared pose.
  const offsets = useSharedValue(zeroOffsets);
  const [state, setState] = useState({
    phase: AuraPhase.rest as AuraPhaseValue,
    chosen: -1,
    amount: auraFlow.pull.amountDefault as number,
    landed: false,
    balanceCents: Math.round(LAST.balance * 100),
    receipt: initialReceipt,
    previous: null as AuraReceipt | null,
  });
  const [sent, setSent] = useState<{ name: string; amount: number } | null>(null);
  const confirmation = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => () => {
    if (confirmation.current) clearTimeout(confirmation.current);
  }, []);

  const stopCue = useCallback(() => {
    cueStopped.current = true;
    if (cueTimer.current !== null) clearTimeout(cueTimer.current);
    cueTimer.current = null;
  }, []);
  const touch = useCallback(() => {
    "worklet";
    if (touched.get()) return;
    touched.set(true);
    cancelAnimation(nudge);
    nudge.set(-1);
    scheduleOnRN(stopCue);
  }, [touched, nudge, stopCue]);
  const cue = useCallback(() => {
    "worklet";
    if (reduced || touched.get() || entered.get() >= 0 || phase.get() !== AuraPhase.rest) return;
    nudge.set(0);
    nudge.set(withTiming(auraNudgeLength(), { duration: auraNudgeLength(), ...linear }));
  }, [reduced, touched, entered, phase, nudge]);

  // Arrival shares its clock with the ring holes. The invitation is silent;
  // the first touch retires it even if that touch arrives before the faces do.
  // Until the hold is over the lip is closed and nothing of ours shows: the
  // glass must be on screen before type and faces are, or they flash bare.
  // A timer would run out while a slow first frame is still being made, so
  // the hold is counted on the UI thread, in frames that were really shown.
  const warm = useSharedValue(0);
  const arrived = useSharedValue(false);
  // The frame callback has one job: once it is done, do not keep a frame loop alive for it.
  const arrivalLoop = useRef<{ setActive: (active: boolean) => void } | null>(null);
  const rest = useCallback(() => arrivalLoop.current?.setActive(false), []);
  const arrival = useFrameCallback((frame) => {
    "worklet";
    if (arrived.get()) return;
    const step = frame.timeSincePreviousFrame ?? 0;
    if (step > 0 && step < auraFlow.enter.smooth) warm.set(warm.get() + step);
    if (warm.get() < auraFlow.enter.hold) return;
    arrived.set(true);
    shown.set(withTiming(1, {
      duration: auraFlow.enter.reveal,
      easing: Easing.bezier(...auraMotion.easeOut),
      reduceMotion: ReduceMotion.Never,
    }));
    // Under Reduce Motion nobody grows: the rings are simply there as the faces fade in.
    if (reduced) entered.set(-1);
    else entered.set(withTiming(auraEnterLength(), { duration: auraEnterLength(), ...linear }));
    scheduleOnRN(rest);
  });
  useEffect(() => {
    arrivalLoop.current = arrival;
  }, [arrival]);
  useEffect(() => () => {
    cancelAnimation(shown);
    cancelAnimation(entered);
    cancelAnimation(nudge);
  }, [shown, entered, nudge]);
  useEffect(() => {
    if (reduced || cueStopped.current) return;
    const invite = () => {
      if (cueStopped.current) return;
      scheduleOnUI(cue);
      cueTimer.current = setTimeout(invite, auraFlow.cue.every);
    };
    cueTimer.current = setTimeout(invite, auraFlow.cue.first);
    return () => {
      if (cueTimer.current !== null) clearTimeout(cueTimer.current);
      cueTimer.current = null;
    };
  }, [reduced, cue]);

  const sync = useCallback((nextPhase: AuraPhaseValue, nextChosen: number, nextAmount: number, hasLanded: boolean) => {
    setState((before) => ({ ...before, phase: nextPhase, chosen: nextChosen, amount: nextAmount, landed: hasLanded }));
    if (nextPhase === AuraPhase.armed || nextPhase === AuraPhase.pulling) setSent(null);
  }, []);
  const commit = useCallback((index: number, dollars: number, writing: boolean) => {
    const name = FRIENDS[index].name;
    setState((before) => ({
      ...before,
      landed: true,
      balanceCents: before.balanceCents - dollars * 100,
      previous: writing ? before.receipt : null,
      receipt: { id: before.receipt.id + 1, friend: index, cents: dollars * 100 },
    }));
    setSent({ name, amount: dollars });
    if (confirmation.current) clearTimeout(confirmation.current);
    confirmation.current = setTimeout(() => setSent(null), auraFlow.views.hint.sentHold);
    AccessibilityInfo.announceForAccessibility(t(`Sent ${dollars} dollars to ${name}`, `Đã gửi ${dollars} đô la cho ${name}`));
  }, [t]);
  const finishReceipt = useCallback(() => {
    setState((before) => before.previous ? { ...before, previous: null } : before);
  }, []);

  const reset = useCallback(() => {
    "worklet";
    cancelAnimation(clock);
    phase.set(AuraPhase.rest);
    chosen.set(-1);
    formed.set(0);
    leaving.set(-1);
    unformed.set(0);
    pullX.set(0);
    pullY.set(0);
    clock.set(0);
    landed.set(false);
    sinking.set(false);
    pullOwner.set(-1);
  }, [clock, phase, chosen, formed, leaving, unformed, pullX, pullY, landed, sinking, pullOwner]);

  const isLocked = useCallback(() => {
    "worklet";
    return entered.get() >= 0 || sinking.get() || (phase.get() === AuraPhase.sending && clock.get() < auraSendMoments(fromY.get()).unlock);
  }, [entered, sinking, phase, clock, fromY]);

  const available = useCallback(() => {
    "worklet";
    if (entered.get() >= 0 || sinking.get()) return false;
    if (phase.get() !== AuraPhase.sending) return phase.get() !== AuraPhase.pulling;
    if (clock.get() < auraSendMoments(fromY.get()).unlock) return false;
    // A new touch after unlock owns the lip. The receipt keeps its own clock
    // so accepting it cannot rewind the previous landing's write.
    reset();
    return true;
  }, [entered, sinking, phase, clock, fromY, reset]);

  const arm = useCallback((index: number) => {
    "worklet";
    const oldChosen = chosen.get();
    if (oldChosen !== index) {
      const oldFormed = formed.get();
      const oldLeaving = leaving.get();
      const oldUnformed = unformed.get();
      const oldOffsets = offsets.get();
      const nextFormed = index === oldLeaving ? oldUnformed : 0;
      const nextLeaving = oldChosen;
      const nextOffsets = [];
      for (let i = 0; i < D.lip.count; i++) {
        const before = auraLipPose(i, oldChosen, oldFormed, oldLeaving, oldUnformed);
        const after = auraLipPose(i, index, nextFormed, nextLeaving, oldFormed);
        const at = i * 4;
        nextOffsets.push(before.dx + oldOffsets[at] - after.dx);
        nextOffsets.push(before.dy + oldOffsets[at + 1] - after.dy);
        nextOffsets.push(before.scale + oldOffsets[at + 2] - after.scale);
        nextOffsets.push(before.opacity + oldOffsets[at + 3] - after.opacity);
      }
      offsets.set(nextOffsets);
      offsets.set(withSpring(zeroOffsets, form));
      leaving.set(nextLeaving);
      unformed.set(oldFormed);
      unformed.set(withSpring(0, form));
      chosen.set(index);
      formed.set(nextFormed);
    }
    formed.set(withSpring(1, form));
    wide.set(withSpring(1, morph));
    amount.set(auraFlow.pull.amountDefault);
    phase.set(AuraPhase.armed);
  }, [chosen, formed, leaving, unformed, offsets, wide, amount, phase]);

  const dismiss = useCallback(() => {
    "worklet";
    if (phase.get() !== AuraPhase.armed || sinking.get()) return;
    phase.set(AuraPhase.rest);
    formed.set(withSpring(0, form));
    unformed.set(withSpring(0, form));
    wide.set(withSpring(0, morph));
  }, [phase, sinking, formed, unformed, wide]);

  const pick = useCallback((index: number) => {
    "worklet";
    touch();
    if (!available()) return;
    if (phase.get() === AuraPhase.armed && chosen.get() === index) dismiss();
    else arm(index);
  }, [touch, available, phase, chosen, dismiss, arm]);

  const beginPull = useCallback((index: number) => {
    "worklet";
    if (!available()) return;
    wasArmed.set(phase.get() === AuraPhase.armed);
    arm(index);
    phase.set(AuraPhase.pulling);
    pullOwner.set(index);
    amount.set(0);
    pullX.set(0);
    pullY.set(0);
  }, [available, wasArmed, phase, arm, pullOwner, amount, pullX, pullY]);

  const movePull = useCallback((index: number, dx: number, dy: number) => {
    "worklet";
    if (phase.get() !== AuraPhase.pulling || pullOwner.get() !== index) return;
    const y = auraRubber(Math.max(dy, 0), auraPullLimit());
    pullX.set(Math.sign(dx) * auraRubber(Math.abs(dx), auraFlow.pull.sideways));
    pullY.set(y);
    const nextAmount = auraAmountAt(y);
    if (nextAmount !== amount.get()) {
      amount.set(nextAmount);
      scheduleOnRN(auraTick);
    }
  }, [phase, pullOwner, pullX, pullY, amount]);

  const release = useCallback(() => {
    "worklet";
    fromX.set(auraSlotX(chosen.get()) + pullX.get());
    fromY.set(D.lip.cy + auraFlow.drop.sag * formed.get() + pullY.get());
    pullX.set(0);
    pullY.set(0);
    sinking.set(false);
    landed.set(false);
    phase.set(AuraPhase.sending);
    clock.set(0);
    const end = auraSendMoments(fromY.get()).end;
    clock.set(withTiming(end, { duration: end, ...linear }));
  }, [fromX, chosen, pullX, fromY, formed, pullY, sinking, landed, phase, clock]);

  const endPull = useCallback((index: number, vx: number, vy: number, cancelled: boolean) => {
    "worklet";
    if (phase.get() !== AuraPhase.pulling || pullOwner.get() !== index) return;
    pullOwner.set(-1);
    if (!cancelled && amount.get() > 0) {
      release();
      return;
    }
    pullX.set(withSpring(0, { ...form, velocity: vx }));
    pullY.set(withSpring(0, { ...form, velocity: vy }));
    if (wasArmed.get()) {
      amount.set(auraFlow.pull.amountDefault);
      phase.set(AuraPhase.armed);
    } else {
      phase.set(AuraPhase.armed);
      dismiss();
    }
  }, [phase, pullOwner, amount, release, pullX, pullY, wasArmed, dismiss]);

  const send = useCallback(() => {
    "worklet";
    touch();
    if (!available()) return;
    if (phase.get() === AuraPhase.rest) {
      nudge.set(0);
      nudge.set(withTiming(auraNudgeLength(), { duration: auraNudgeLength(), ...linear }));
      scheduleOnRN(auraNudgeHaptic);
      return;
    }
    sinking.set(true);
    sinkClock.set(0);
    cancelAnimation(pullY);
    pullY.set(0);
    sinkClock.set(withTiming(auraFlow.send.sink, { duration: auraFlow.send.sink, ...linear }));
  }, [touch, available, phase, nudge, sinking, sinkClock, pullY]);

  useAnimatedReaction(
    () => amount.get(),
    (next) => {
      weight.set(withSpring(next / auraFlow.pull.amountMax, { ...auraFlow.drop.weigh, reduceMotion: ReduceMotion.Never }));
    },
  );
  useAnimatedReaction(
    () => entered.get() >= auraEnterLength(),
    (done) => { if (done) entered.set(-1); },
  );

  useAnimatedReaction(
    () => ({ p: phase.get(), i: chosen.get(), a: amount.get(), l: landed.get() }),
    (next, previous) => {
      if (!previous || next.p !== previous.p || next.i !== previous.i || next.a !== previous.a || next.l !== previous.l) {
        scheduleOnRN(sync, next.p, next.i, next.a, next.l);
      }
    },
  );
  useAnimatedReaction(
    () => sinking.get() ? sinkClock.get() : -1,
    (elapsed) => {
      if (elapsed < 0) return;
      pullY.set(auraSinkDepth(elapsed, auraDepthFor(amount.get())));
      if (elapsed >= auraFlow.send.sink) release();
    },
  );
  useAnimatedReaction(
    () => {
      if (phase.get() !== AuraPhase.sending) return 0;
      const elapsed = clock.get();
      const moments = auraSendMoments(fromY.get());
      if (elapsed >= moments.end) return 2;
      return elapsed >= moments.impact ? 1 : 0;
    },
    (milestone) => {
      if (milestone >= 1 && !landed.get()) {
        landed.set(true);
        wide.set(withSpring(0, morph));
        formed.set(withSpring(0, form));
        receiptX.set(fromX.get());
        const late = clock.get() - auraSendMoments(fromY.get()).impact;
        const duration = reduced ? auraFlow.views.receiptFade : auraFlow.send.rippleLife;
        receiptClock.set(Math.min(late, duration));
        receiptClock.set(withTiming(duration, { duration: Math.max(0, duration - late), ...linear }));
        scheduleOnRN(auraLandingHaptic);
        scheduleOnRN(commit, chosen.get(), amount.get(), late < duration);
      }
      if (milestone === 2) reset();
    },
  );
  useAnimatedReaction(
    () => receiptClock.get() >= (reduced ? auraFlow.views.receiptFade : auraFlow.send.rippleLife),
    (done, before) => { if (done && before === false) scheduleOnRN(finishReceipt); },
  );
  useAnimatedReaction(
    () => nudge.get() >= auraNudgeLength(),
    (done) => { if (done) nudge.set(-1); },
  );
  useAnimatedReaction(
    () => phase.get() === AuraPhase.rest && formed.get() === 0 && unformed.get() === 0,
    (settled) => {
      if (settled) {
        chosen.set(-1);
        leaving.set(-1);
      }
    },
  );

  const active = state.phase !== AuraPhase.rest;
  const preview = active && state.amount > 0 && !(state.phase === AuraPhase.sending && state.landed);
  const balanceCents = state.balanceCents - (preview ? state.amount * 100 : 0);
  const name = state.chosen < 0 ? "" : FRIENDS[state.chosen].name;
  let hint = t("Pull a friend down to send.", "Kéo một người bạn xuống để gửi.");
  let hintKind: AuraHintKind = "rest";
  if (sent) {
    hintKind = "sent";
    hint = t(`Sent $${sent.amount} to ${sent.name}.`, `Đã gửi $${sent.amount} cho ${sent.name}.`);
  }
  else if (state.phase === AuraPhase.sending) {
    hintKind = "sending";
    // The brief leaves the airborne instruction open; never invite a second
    // send while the first is locked.
    hint = t(`Sending $${state.amount} to ${name}.`, `Đang gửi $${state.amount} cho ${name}.`);
  } else if (state.phase === AuraPhase.armed) {
    hintKind = "armed";
    hint = t("Send, or pull to change the amount.", "Gửi, hoặc kéo để đổi số tiền.");
  } else if (state.phase === AuraPhase.pulling) {
    hintKind = state.amount > 0 ? "pulling" : "idle-pull";
    hint = state.amount > 0
      ? t(`Let go to send $${state.amount} to ${name}.`, `Thả để gửi $${state.amount} cho ${name}.`)
      : t("Pull further, or let go to cancel.", "Kéo thêm, hoặc thả để huỷ.");
  }

  return {
    state, balanceCents, preview, hint, hintKind, reduced, offsets, receiptClock, receiptX, shown,
    pick, beginPull, movePull, endPull, isLocked, touch,
    onTouch: () => scheduleOnUI(touch),
    dismiss: () => scheduleOnUI(dismiss),
    send: () => scheduleOnUI(send),
    accessiblePick: (index: number) => scheduleOnUI(pick, index),
  };
}

export type AuraMachine = ReturnType<typeof useAuraMachine>;
