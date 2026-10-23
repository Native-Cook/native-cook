import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AppState } from "react-native";
import * as Haptics from "expo-haptics";
import {
  cancelAnimation, Easing, ReduceMotion, useAnimatedReaction, useDerivedValue,
  useFrameCallback, useReducedMotion, useSharedValue, withSpring, withTiming,
} from "react-native-reanimated";
import { scheduleOnRN, scheduleOnUI } from "react-native-worklets";
import { HORIZON_LOOP, horizonMotion as M } from "./horizon-palette";
import { HERO, part, type HorizonFx, type HorizonPhase } from "./horizon-state";

// Millisecond boundaries make the transfer reproducible, including its two
// hesitations. Every burst eases out; the gaps really hold their last byte.
export const UPLOAD_CHUNKS = [
  { at: 0, span: 300, to: 0.12 },
  { at: 400, span: 400, to: 0.27 },
  { at: 900, span: 450, to: 0.40 },
  { at: 1850, span: 550, to: 0.53 },
  { at: 2450, span: 650, to: 0.68 },
  { at: 3150, span: 550, to: 0.81 },
  { at: 3800, span: 400, to: 0.90 },
  { at: 4650, span: 250, to: 0.96 },
  { at: 4950, span: 250, to: 1 },
] as const;

export function uploadAt(clock: number) {
  "worklet";
  const ms = Math.max(0, Math.min(1, clock)) * M.transfer;
  let progress = 0;
  let arrival = -M.pulse;
  let chunkIndex = -1;
  for (const chunk of UPLOAD_CHUNKS) {
    if (ms < chunk.at) break;
    chunkIndex += 1;
    const t = part(ms, chunk.at, chunk.at + chunk.span);
    progress += (chunk.to - progress) * (1 - (1 - t) * (1 - t));
    arrival = chunk.at;
    if (t < 1) break;
  }
  return { progress, pulse: Math.max(0, 1 - (ms - arrival) / M.pulse), chunkIndex };
}

const POWER = { idle: 0.2, uploading: 1, paused: 0.4, done: 1, stopped: 0.1 };
const RESET = 300;
const DRAIN = 420;
type Step = "rest" | "transfer" | "hero" | "reset" | "drain";
type Command = "close" | "toggle" | "stop" | "restart" | "touch" | "complete" | "reset-end" | "auto-start" | "auto-clear" | "auto-idle" | "sleep" | "wake" | "dispose";

function impact(kind: "light" | "medium" | "success" | "soft") {
  // Unsupported hardware (and rejected native promises) never break a transfer.
  try {
    const result = kind === "success"
      ? Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success)
      : Haptics.impactAsync(kind === "soft" ? Haptics.ImpactFeedbackStyle.Soft : kind === "light" ? Haptics.ImpactFeedbackStyle.Light : Haptics.ImpactFeedbackStyle.Medium);
    void result.catch(() => {});
  } catch { /* Haptics are optional. */ }
}

export const HORIZON_LIFT = { dip: -0.08, rise: 620, dampingRatio: 0.6, hoverPeriod: 4200, hoverAmplitude: 0.06, reducedFade: 200 };

export const HORIZON_DRAG = { threshold: 8, limit: 110, gain: 0.75, duration: 560, dampingRatio: 0.62 };

export function rubberShift(pull: number) {
  "worklet";
  return HORIZON_DRAG.gain * pull / (1 + HORIZON_DRAG.gain * Math.abs(pull) / HORIZON_DRAG.limit);
}

export function rubberPull(shift: number) {
  "worklet";
  const bounded = Math.max(-109.999, Math.min(109.999, shift));
  return bounded / (HORIZON_DRAG.gain * (1 - Math.abs(bounded) / HORIZON_DRAG.limit));
}

export function rubberVelocity(pull: number, velocity: number) {
  "worklet";
  return velocity * HORIZON_DRAG.gain / (1 + HORIZON_DRAG.gain * Math.abs(pull) / HORIZON_DRAG.limit) ** 2;
}

export function useHorizonUpload(options: { autoPlay?: boolean; onDone?: () => void } = {}) {
  const reduced = useReducedMotion();
  const [phase, setPhase] = useState<HorizonPhase>("idle");
  const [percent, setPercent] = useState(0);
  const onDone = useRef(options.onDone);
  useEffect(() => { onDone.current = options.onDone; }, [options.onDone]);
  const mounted = useRef(true);
  const reportPhase = useCallback((next: HorizonPhase) => { if (mounted.current) setPhase(next); }, []);
  const reportPercent = useCallback((next: number) => { if (mounted.current) setPercent(next); }, []);

  const clock = useSharedValue(0);
  const power = useSharedValue(POWER.idle);
  const crest = useSharedValue(0);
  const done = useSharedValue(0);
  const press = useSharedValue(0);
  const closePress = useSharedValue(0);
  const shiftX = useSharedValue(0);
  const shiftY = useSharedValue(0);
  const liftBase = useSharedValue(0);
  const hoverTime = useSharedValue(0);
  const hovering = useSharedValue(false);
  const rising = useSharedValue(false);
  const hoverSkip = useSharedValue(false);
  const beatEpoch = useSharedValue(0);
  // Add hover to the still-settling spring instead of replacing it at a
  // frame boundary. Its first sample is zero, so there is no height jump.
  const lift = useDerivedValue(() => liftBase.get() + (hovering.get()
    ? HORIZON_LIFT.hoverAmplitude * Math.sin(2 * Math.PI * hoverTime.get() / HORIZON_LIFT.hoverPeriod) : 0));
  const grabX = useSharedValue(0);
  const grabY = useSharedValue(0);
  const pulling = useSharedValue(false);
  const state = useSharedValue<HorizonPhase>("idle");
  const step = useSharedValue<Step>("rest");
  const stage = useSharedValue(0);
  const resetDuration = useSharedValue(RESET);
  const resetStartsUpload = useSharedValue(false);
  const active = useSharedValue(AppState.currentState !== "background" && AppState.currentState !== "inactive");
  const alive = useSharedValue(true);
  const automatic = useSharedValue(options.autoPlay ?? false);
  const elapsed = useSharedValue(0);
  const skipFrame = useSharedValue(true);
  const generation = useSharedValue(0);
  const flipped = useSharedValue(false);
  const notified = useSharedValue(-1);

  const notify = useCallback((token: number, kind: "light" | "medium" | "success") => {
    // A queued RN call from a replaced celebration must not reward the new run.
    if (!mounted.current || !alive.get() || generation.get() !== token) return;
    if (active.get()) impact(kind);
    if (kind === "success") onDone.current?.();
  }, [active, alive, generation]);

  const notifyChunk = useCallback((token: number, epoch: number, chunk: number) => {
    if (!mounted.current || !alive.get() || !active.get() || automatic.get() ||
      state.get() !== "uploading" || step.get() !== "transfer" ||
      generation.get() !== token || beatEpoch.get() !== epoch || uploadAt(clock.get()).chunkIndex !== chunk) return;
    impact("soft");
  }, [active, alive, automatic, beatEpoch, clock, generation, state, step]);

  const progress = useDerivedValue(() => uploadAt(clock.get()).progress);
  const pulse = useDerivedValue(() => state.get() === "uploading" && step.get() === "transfer" ? uploadAt(clock.get()).pulse : 0);
  const fx = useMemo<HorizonFx>(() => ({ progress, pulse, power, crest, done, press, closePress, shiftX, shiftY, lift }), [progress, pulse, power, crest, done, press, closePress, shiftX, shiftY, lift]);

  function fade(to: number, duration: number) {
    "worklet";
    return withTiming(to, { duration, easing: Easing.inOut(Easing.quad), reduceMotion: ReduceMotion.Never });
  }
  function linear(to: number, duration: number) {
    "worklet";
    return withTiming(to, { duration, easing: Easing.linear, reduceMotion: ReduceMotion.Never });
  }
  function parkLift() {
    "worklet";
    const height = lift.get();
    cancelAnimation(liftBase);
    hovering.set(false); hoverTime.set(0); rising.set(false);
    liftBase.set(height);
  }
  function freeze() {
    "worklet";
    beatEpoch.set(beatEpoch.get() + 1);
    cancelAnimation(liftBase);
    cancelAnimation(clock); cancelAnimation(crest); cancelAnimation(done);
    cancelAnimation(stage); cancelAnimation(power); cancelAnimation(press); cancelAnimation(closePress);
  }
  function setPhaseUI(next: HorizonPhase) {
    "worklet";
    state.set(next);
    power.set(fade(POWER[next], 400));
  }
  function resumeClocks() {
    "worklet";
    power.set(fade(POWER[state.get()], 400));
    if (!active.get()) return;
    if (state.get() === "paused") {
      liftBase.set(fade(0, reduced ? HORIZON_LIFT.reducedFade : RESET));
      return;
    }
    if (step.get() === "transfer") clock.set(linear(1, (1 - clock.get()) * M.transfer));
    if (step.get() === "hero") {
      if (reduced) liftBase.set(fade(1, HORIZON_LIFT.reducedFade));
      else if (rising.get()) liftBase.set(withSpring(1, { duration: HORIZON_LIFT.rise, dampingRatio: HORIZON_LIFT.dampingRatio }));
      else liftBase.set(fade(HORIZON_LIFT.dip, Math.max(0, (HERO.inhale[1] - crest.get()) * M.crest)));
      crest.set(reduced ? 1 : linear(1, (1 - crest.get()) * M.crest));
      if (flipped.get()) done.set(reduced ? fade(1, 200) : withSpring(1, { duration: 520, dampingRatio: 0.75 }));
    }
    if (step.get() === "drain") {
      const remaining = DRAIN * (1 - stage.get());
      liftBase.set(fade(0, Math.min(reduced ? HORIZON_LIFT.reducedFade : RESET, remaining)));
      clock.set(fade(0, remaining)); stage.set(linear(1, remaining));
    }
    if (step.get() === "reset") {
      const remaining = resetDuration.get() * (1 - stage.get());
      liftBase.set(fade(0, reduced ? Math.min(HORIZON_LIFT.reducedFade, remaining) : remaining));
      clock.set(fade(0, remaining)); crest.set(fade(0, Math.min(RESET, remaining)));
      done.set(fade(0, Math.min(RESET, remaining))); stage.set(linear(1, remaining));
    }
  }
  function startTransfer() {
    "worklet";
    parkLift(); liftBase.set(0);
    step.set("transfer"); setPhaseUI("uploading");
    if (active.get()) clock.set(linear(1, (1 - clock.get()) * M.transfer));
    scheduleOnRN(notify, generation.get(), "light");
  }
  function beginReset(startAfter: boolean, duration: number) {
    "worklet";
    freeze(); parkLift(); generation.set(generation.get() + 1); flipped.set(false);
    resetStartsUpload.set(startAfter); resetDuration.set(duration); stage.set(0);
    step.set("reset"); setPhaseUI(startAfter ? "uploading" : "done"); resumeClocks();
  }
  function command(action: Command) {
    "worklet";
    if (action === "dispose") { alive.set(false); automatic.set(false); freeze(); cancelAnimation(shiftX); cancelAnimation(shiftY); return; }
    if (!alive.get()) return;
    if (action === "sleep") { active.set(false); freeze(); closePress.set(0); press.set(0); cancelAnimation(shiftX); cancelAnimation(shiftY); shiftX.set(0); shiftY.set(0); pulling.set(false); return; }
    if (action === "wake") { active.set(true); skipFrame.set(true); hoverSkip.set(true); resumeClocks(); return; }
    if (action === "touch") { automatic.set(false); return; }
    if (action === "close") {
      automatic.set(false);
      if (state.get() === "done" && !flipped.get() && step.get() !== "reset") return;
      action = state.get() === "done" || state.get() === "stopped" ? "restart" : "stop";
    }
    if (action === "toggle" || action === "stop" || action === "restart") automatic.set(false);
    const current = state.get();
    if (action === "complete") {
      if (step.get() !== "transfer" || current !== "uploading") return;
      parkLift();
      liftBase.set(reduced ? fade(1, HORIZON_LIFT.reducedFade) : fade(HORIZON_LIFT.dip, HERO.inhale[1] * M.crest));
      clock.set(1); step.set("hero"); setPhaseUI("done");
      crest.set(reduced ? 1 : linear(1, M.crest));
      return;
    }
    if (action === "reset-end") {
      liftBase.set(0);
      clock.set(0); crest.set(0); done.set(0);
      if (resetStartsUpload.get()) startTransfer();
      else { step.set("rest"); setPhaseUI("idle"); }
      return;
    }
    if (action === "auto-clear") { beginReset(false, M.reset); return; }
    if (action === "auto-idle") {
      freeze(); parkLift(); liftBase.set(0); clock.set(0); crest.set(0); done.set(0); flipped.set(false);
      step.set("rest"); setPhaseUI("idle"); return;
    }
    if (action === "stop") {
      if (current !== "uploading" && current !== "paused") return;
      freeze(); parkLift(); generation.set(generation.get() + 1); step.set("drain"); stage.set(0);
      flipped.set(false); crest.set(fade(0, RESET)); done.set(fade(0, RESET));
      setPhaseUI("stopped"); resumeClocks(); scheduleOnRN(notify, generation.get(), "medium"); return;
    }
    if (action === "toggle" && current === "done" && step.get() === "hero" && crest.get() < 1) return;
    if (action === "toggle" && current === "uploading") {
      freeze(); parkLift(); liftBase.set(fade(0, reduced ? HORIZON_LIFT.reducedFade : RESET)); setPhaseUI("paused"); scheduleOnRN(notify, generation.get(), "light"); return;
    }
    if (action === "toggle" && current === "paused") {
      setPhaseUI("uploading"); resumeClocks(); scheduleOnRN(notify, generation.get(), "light"); return;
    }
    if (action === "restart" || current === "done" || current === "stopped") {
      beginReset(true, RESET); return;
    }
    if (current === "idle" || action === "auto-start") {
      generation.set(generation.get() + 1); flipped.set(false); startTransfer();
    }
  }

  useAnimatedReaction(() => state.get(), (now, before) => {
    if (now !== before) scheduleOnRN(reportPhase, now);
  });
  useAnimatedReaction(() => Math.min(100, Math.floor(progress.get() * 100 + 1e-7)), (now, before) => {
    if (now !== before) scheduleOnRN(reportPercent, now);
  });
  useAnimatedReaction(() => clock.get() === 1 && state.get() === "uploading" && step.get() === "transfer" && active.get(), (ready) => {
    if (ready && active.get()) command("complete");
  });
  useAnimatedReaction(() => stage.get() === 1 && step.get() === "reset" && state.get() !== "paused" && active.get(), (ready) => {
    if (ready && active.get()) command("reset-end");
  });
  useAnimatedReaction(() => step.get() === "hero" && crest.get() >= HERO.flip[0] && active.get(), (ready) => {
    if (!ready || flipped.get()) return;
    flipped.set(true);
    done.set(reduced ? fade(1, 200) : withSpring(1, { duration: 520, dampingRatio: 0.75 }));
    if (notified.get() !== generation.get()) {
      notified.set(generation.get()); scheduleOnRN(notify, generation.get(), "success");
    }
  });

  useAnimatedReaction(
    () => !reduced && active.get() && state.get() === "done" && step.get() === "hero" && crest.get() >= HERO.lift[0],
    (ready) => {
      if (!ready || rising.get()) return;
      rising.set(true);
      liftBase.set(withSpring(1, { duration: HORIZON_LIFT.rise, dampingRatio: HORIZON_LIFT.dampingRatio }));
    },
  );
  useAnimatedReaction(() => uploadAt(clock.get()).chunkIndex, (chunk, previous) => {
    // Observe the schedule even while muted. Touching autoplay or resuming
    // mid-chunk must never replay an arrival that already happened.
    if (previous === null || chunk <= 0 || chunk <= previous || !active.get() || automatic.get() ||
      state.get() !== "uploading" || step.get() !== "transfer") return;
    scheduleOnRN(notifyChunk, generation.get(), beatEpoch.get(), chunk);
  });
  useFrameCallback((frame) => {
    if (!alive.get() || !active.get() || reduced || state.get() !== "done" || step.get() !== "hero") return;
    if (hoverSkip.get()) { hoverSkip.set(false); return; }
    if (!rising.get() || crest.get() < HERO.settle[0]) return;
    if (!hovering.get()) { hoverTime.set(0); hovering.set(true); return; }
    hoverTime.set((hoverTime.get() + (frame.timeSincePreviousFrame ?? 0)) % HORIZON_LIFT.hoverPeriod);
  });

  // Absolute loop boundaries prevent animation callback/frame rounding from
  // accumulating across loops. This scheduler never supplies the byte count:
  // only the transfer clock does. Background time is deliberately excluded.
  useFrameCallback((frame) => {
    if (!alive.get() || !active.get() || !automatic.get()) return;
    if (skipFrame.get()) { skipFrame.set(false); return; }
    const before = elapsed.get();
    const after = before + (frame.timeSincePreviousFrame ?? 0);
    elapsed.set(after);
    const a = before % HORIZON_LOOP;
    const b = after % HORIZON_LOOP;
    const wrapped = Math.floor(before / HORIZON_LOOP) !== Math.floor(after / HORIZON_LOOP);
    if (wrapped) command("auto-idle");
    if ((a < M.lead || wrapped) && b >= M.lead) {
      command("auto-start");
      // Account for the fraction of a frame after the scheduled start.
      clock.set(Math.min(1, (b - M.lead) / M.transfer));
      clock.set(linear(1, Math.max(0, M.lead + M.transfer - b)));
    }
    const clearAt = HORIZON_LOOP - M.reset;
    if ((a < clearAt || wrapped) && b >= clearAt) command("auto-clear");
  });

  useEffect(() => {
    mounted.current = true;
    scheduleOnUI(() => { alive.set(true); automatic.set(options.autoPlay ?? false); });
    const listener = AppState.addEventListener("change", (next) => scheduleOnUI(command, next === "active" ? "wake" : "sleep"));
    return () => { mounted.current = false; listener.remove(); scheduleOnUI(command, "dispose"); };
    // All controller inputs are shared values; subscribing per percent would
    // cancel the transfer. Reduced Motion is fixed for this mount by its hook.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // The controls and visual cues share UI-thread state. RN can be busy
  // without delaying one member of the completion beat or dispatching a stale tap.
  const label = useDerivedValue<HorizonPhase>(() => state.get() === "done"
    ? (flipped.get() || step.get() === "reset" ? "done" : "uploading") : state.get());
  const retry = useDerivedValue<number>(() => state.get() === "stopped" ||
    (state.get() === "done" && (flipped.get() || step.get() === "reset")) ? 1 : 0);
  const closeEnabled = useDerivedValue<number>(() => state.get() !== "done" || retry.get() === 1 ? 1 : 0);
  const panelEnabled = useDerivedValue<number>(() => !(state.get() === "done" && step.get() === "hero" && crest.get() < 1) ? 1 : 0);

  const interaction = useMemo(() => ({
    touch: () => { "worklet"; automatic.set(false); },
    toggle: () => { "worklet"; command("toggle"); },
    close: () => { "worklet"; command("close"); },
    begin: () => {
      "worklet";
      if (!alive.get() || !active.get()) return;
      automatic.set(false);
      cancelAnimation(shiftX); cancelAnimation(shiftY);
      grabX.set(rubberPull(shiftX.get())); grabY.set(rubberPull(shiftY.get()));
      pulling.set(false);
    },
    start: () => {
      "worklet";
      if (!alive.get() || !active.get()) return;
      pulling.set(true);
      press.set(withTiming(0, { duration: 90, reduceMotion: ReduceMotion.Never }));
      scheduleOnRN(notify, generation.get(), "light");
    },
    move: (x: number, y: number) => {
      "worklet";
      if (!active.get() || !alive.get() || !pulling.get()) return;
      shiftX.set(rubberShift(grabX.get() + x));
      shiftY.set(rubberShift(grabY.get() + y));
    },
    release: (x: number, y: number, vx: number, vy: number) => {
      "worklet";
      if (!alive.get() || !active.get()) return;
      const config = { duration: HORIZON_DRAG.duration, dampingRatio: HORIZON_DRAG.dampingRatio,
        clamp: { min: -HORIZON_DRAG.limit, max: HORIZON_DRAG.limit } };
      shiftX.set(reduced ? fade(0, 200) : withSpring(0, { ...config, velocity: rubberVelocity(grabX.get() + x, vx) }));
      shiftY.set(reduced ? fade(0, 200) : withSpring(0, { ...config, velocity: rubberVelocity(grabY.get() + y, vy) }));
      pulling.set(false);
    },
    finalize: () => {
      "worklet";
      // Failed pans include ordinary taps and canceled gestures. A touch can
      // interrupt the return spring even if it never crosses the drag threshold.
      if (!alive.get() || !active.get()) return;
      if (pulling.get() || (shiftX.get() !== 0 || shiftY.get() !== 0)) {
        shiftX.set(reduced ? fade(0, 200) : withSpring(0, { duration: 560, dampingRatio: 0.62 }));
        shiftY.set(reduced ? fade(0, 200) : withSpring(0, { duration: 560, dampingRatio: 0.62 }));
        pulling.set(false);
      }
    },
  // Controller closures only capture stable shared values and notify; preserving
  // the object prevents percent updates from replacing an active recognizer.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);

  return {
    fx, phase, percent, interaction, label, retry, closeEnabled, panelEnabled,
    toggle: () => scheduleOnUI(command, "toggle"),
    stop: () => scheduleOnUI(command, "stop"),
    restart: () => scheduleOnUI(command, "restart"),
    close: () => scheduleOnUI(command, "close"),
    touch: () => scheduleOnUI(command, "touch"),
  };
}
