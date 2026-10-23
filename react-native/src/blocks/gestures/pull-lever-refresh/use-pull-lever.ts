/**
 * The lever's state machine. A finger and the autoplay script call the same worklets:
 * pullBegin → pullMove(drop) → pullEnd, then finishRefresh when the request resolves.
 * idle(0) → pulling(1) ⇄ armed(2) → refreshing(3) → done(4) → idle.
 *
 * Replica motion (the source): the pull turns the lever and fills it dot by dot; released when full it is
 * thrown up to −44°, drains one dot at a time, eases off, falls back to rest, goes dark and lights again.
 * Upgrade: a sway while the request outlasts the drain, a bloom when the cap relights, a caption.
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import {
  cancelAnimation,
  Easing,
  ReduceMotion,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
  type WithSpringConfig,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { playLeverHaptic, type LeverHaptic } from './lever-haptics';
import { DEMO, FETCH_MS, LIGHT, LOOP_MS, PULL, REPLICA_ONLY, RESUME_MS, SPRING, TIMING } from './motion';
import { LEVER } from './theme';

export type LeverLabel = 'none' | 'refreshing' | 'updated';

const IDLE = 0;
const PULLING = 1;
const ARMED = 2;
const REFRESHING = 3;
const DONE = 4;
const NEVER = { reduceMotion: ReduceMotion.Never };

function ease(u: number): number {
  'worklet';
  const k = Math.min(Math.max(u, 0), 1);
  return (1 - Math.cos(Math.PI * k)) / 2;
}

type Options = { autoPlay: boolean; reduceMotion: boolean; onFetch?: () => Promise<void> };

const wait = () => new Promise<void>((resolve) => setTimeout(resolve, FETCH_MS));

export function usePullLever({ autoPlay, reduceMotion, onFetch = wait }: Options) {
  const [label, setLabel] = useState<LeverLabel>('none');
  const angle = useSharedValue<number>(PULL.restAngle);
  const sway = useSharedValue(0);
  const fill = useSharedValue(0);
  const lit = useSharedValue(1);
  const bloom = useSharedValue(1);
  const glow = useSharedValue(0);
  const pulse = useSharedValue(-1);
  const knobLit = useSharedValue(0);
  const ripple = useSharedValue(0);
  const breath = useSharedValue(0); // the loading halo's breathing, −1..1
  // The tick arc: 7 dots that light with the fill's ticks (9 lever dots → 7 arc dots).
  const arcLit = useDerivedValue(() => Math.round((Math.floor(Math.max(fill.get(), 0) * LEVER.dots + 1e-3) * LIGHT.arc.dots) / LEVER.dots));
  const shake = useSharedValue(0);
  const idle = useSharedValue(0); // ambient breathing, degrees
  const idleAmp = useSharedValue(1);
  const clock = useSharedValue(0);
  const phase = useSharedValue(IDLE);
  const ticks = useSharedValue(0);
  const grabFrom = useSharedValue(0); // finger travel the lever already had when it was grabbed
  const stretchTicks = useSharedValue(0);
  const refreshId = useSharedValue(0); // a pull during loading bumps it, so the old request is ignored

  // Autoplay.
  const demoT = useSharedValue(0);
  const demoPaused = useSharedValue(!autoPlay);
  const realDown = useSharedValue(false);
  const quiet = useSharedValue(0);
  const scriptPulling = useSharedValue(false);
  const scriptedRefresh = useSharedValue(false);

  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => () => { if (timer.current) clearTimeout(timer.current); }, []);

  const haptic = useCallback((kind: LeverHaptic, silent: boolean) => {
    'worklet';
    if (!silent) scheduleOnRN(playLeverHaptic, kind);
  }, []);

  const spring = useCallback((target: number, config: WithSpringConfig) => {
    'worklet';
    return reduceMotion ? withTiming(target, { duration: TIMING.reduced, ...NEVER }) : withSpring(target, config);
  }, [reduceMotion]);

  const announceDone = useCallback((silent: boolean) => {
    setLabel('updated');
    if (!silent) playLeverHaptic('success');
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => setLabel('none'), TIMING.doneLabel);
  }, []);

  const finishRefresh = useCallback((silent: boolean, id: number) => {
    'worklet';
    if (phase.get() !== REFRESHING || id !== refreshId.get()) return;
    scriptedRefresh.set(false);
    phase.set(DONE);
    sway.set(withTiming(0, { duration: TIMING.swayFade, easing: TIMING.easeOut }));
    cancelAnimation(fill);
    // Replica: last dot out, ease off, fall back, dark, the cap lights again. The glow keeps burning through the dark.
    fill.set(withTiming(0, { duration: TIMING.dotMs, easing: Easing.linear, ...NEVER }));
    angle.set(withSequence(
      withTiming(reduceMotion ? PULL.restAngle : TIMING.relaxAngle, { duration: TIMING.relaxMs, easing: TIMING.easeInOut, ...NEVER }),
      spring(PULL.restAngle, SPRING.fall),
    ));
    lit.set(withDelay(TIMING.relaxMs, withSequence(
      withTiming(0, { duration: TIMING.lightMs, easing: TIMING.easeOut, ...NEVER }),
      withDelay(TIMING.dimMs, withTiming(1, { duration: TIMING.lightMs, easing: TIMING.easeOut, ...NEVER }, (done) => {
        if (!done) return;
        // Hero: the glow collapses into a spark that runs down the shaft and lights the knob.
        glow.set(withTiming(0, { duration: TIMING.pulse / 2, easing: TIMING.easeOut, ...NEVER }));
        if (reduceMotion) {
          knobLit.set(withSequence(withTiming(1, { duration: TIMING.knobFlash, easing: TIMING.easeOut, ...NEVER }), withTiming(0, { duration: TIMING.knobFade, easing: TIMING.easeOut, ...NEVER })));
        } else {
          pulse.set(0);
          pulse.set(withTiming(1, { duration: TIMING.pulse, easing: TIMING.easeInOut }, (arrived) => {
            if (!arrived) return;
            pulse.set(-1);
            knobLit.set(withSequence(withTiming(1, { duration: TIMING.knobFlash, easing: TIMING.easeOut }), withTiming(0, { duration: TIMING.knobFade, easing: TIMING.easeOut })));
            ripple.set(0);
            ripple.set(withTiming(1, { duration: LIGHT.rippleMs, easing: TIMING.easeOut }));
          }));
          bloom.set(withSequence(withTiming(TIMING.bloomScale, { duration: 90, easing: TIMING.easeOut }), withSpring(1, SPRING.bloom)));
          const st = TIMING.shakeStep;
          const k = TIMING.shake;
          shake.set(withSequence(withTiming(k[0], { duration: st }), withTiming(k[1], { duration: st }), withTiming(k[2], { duration: st }), withTiming(k[3], { duration: st }), withTiming(k[4], { duration: st })));
        }
        scheduleOnRN(announceDone, silent);
        phase.set(IDLE);
      })),
    )));
  }, [angle, announceDone, bloom, fill, glow, knobLit, lit, ripple, phase, pulse, reduceMotion, refreshId, scriptedRefresh, shake, spring, sway]);

  const runFetch = useCallback(async (id: number) => {
    try {
      await onFetch();
    } finally {
      scheduleOnUI(finishRefresh, false, id);
    }
  }, [finishRefresh, onFetch]);

  const startRefresh = useCallback((silent: boolean, velocity: number) => {
    'worklet';
    phase.set(REFRESHING);
    refreshId.set(refreshId.get() + 1);
    stretchTicks.set(0);
    scheduleOnRN(setLabel, 'refreshing');
    haptic('release', silent);
    // Velocity handoff: the faster the release, the harder the throw.
    const v = -Math.min(Math.abs(velocity) * PULL.degPerPt * PULL.throwBoost, PULL.maxThrow);
    angle.set(reduceMotion ? spring(TIMING.upAngle, SPRING.throwUp) : withSpring(TIMING.upAngle, { ...SPRING.throwUp, velocity: v }));
    glow.set(withTiming(1, { duration: reduceMotion ? TIMING.reduced : TIMING.glowRise, easing: TIMING.easeOut, ...NEVER }));
    const keep = 1 / LEVER.dots + 1e-3; // the last dot holds until the request resolves
    fill.set(withTiming(keep, { duration: (LEVER.dots - 1) * TIMING.dotMs, easing: Easing.linear, ...NEVER }));
    if (!reduceMotion && !REPLICA_ONLY) {
      const a = TIMING.swayAmplitude;
      const half = TIMING.swayHalfPeriod;
      const swing = { duration: half, easing: Easing.inOut(Easing.sin) };
      sway.set(withDelay(half, withSequence(
        withTiming(a, { duration: half / 2, easing: Easing.out(Easing.sin) }),
        withRepeat(withSequence(withTiming(-a, swing), withTiming(a, swing)), -1),
      )));
    }
    scriptedRefresh.set(silent);
    if (!silent) scheduleOnRN(runFetch, refreshId.get());
  }, [angle, fill, glow, haptic, phase, reduceMotion, refreshId, runFetch, scriptedRefresh, spring, stretchTicks, sway]);

  /** A grab at any moment takes the lever from where it is; a grab during loading cancels the refresh. */
  const pullBegin = useCallback((silent: boolean) => {
    'worklet';
    const state = phase.get();
    if (state === REFRESHING || state === DONE) {
      refreshId.set(refreshId.get() + 1); // the request in flight will be ignored
      scriptedRefresh.set(false);
      haptic('cancel', silent);
      scheduleOnRN(setLabel, 'none');
      sway.set(withTiming(0, { duration: TIMING.swayFade, easing: TIMING.easeOut }));
      glow.set(withTiming(0, { duration: TIMING.swayFade, easing: TIMING.easeOut }));
      cancelAnimation(lit);
      lit.set(1);
    }
    cancelAnimation(angle);
    cancelAnimation(fill);
    lit.set(1);
    // Resume the pull from the lever's current angle, so nothing jumps.
    const p = Math.min(Math.max((angle.get() - PULL.restAngle) / (PULL.maxAngle - PULL.restAngle), 0), 1);
    grabFrom.set(p * PULL.leverPull);
    fill.set(p);
    ticks.set(Math.floor(p * LEVER.dots + 1e-3));
    stretchTicks.set(0);
    phase.set(PULLING);
  }, [angle, fill, glow, grabFrom, haptic, lit, phase, refreshId, scriptedRefresh, stretchTicks, sway, ticks]);

  /** `drop` is the finger's travel down from where it started. */
  const pullMove = useCallback((drop: number, silent: boolean) => {
    'worklet';
    const state = phase.get();
    if (state !== PULLING && state !== ARMED) return;
    const progress = Math.max(drop + grabFrom.get(), 0) / PULL.leverPull;
    const p = Math.min(progress, 1);
    const over = Math.max(progress - 1, 0) * PULL.leverPull;
    // Past full the lever rubber-bands: more pull, less give, a rigid tick every few degrees.
    const stretch = REPLICA_ONLY ? 0 : (PULL.overAngle * over) / (over + PULL.overSoftness);
    angle.set(PULL.restAngle + (PULL.maxAngle - PULL.restAngle) * p + stretch);
    const st = Math.floor(stretch / PULL.overTick);
    if (st > stretchTicks.get()) haptic('stretch', silent);
    stretchTicks.set(st);
    fill.set(p);
    const covered = Math.floor(p * LEVER.dots + 1e-3);
    if (covered > ticks.get() && p < 1) haptic('tick', silent);
    ticks.set(covered);
    if (p >= 1 && state === PULLING) {
      phase.set(ARMED);
      haptic('armed', silent);
    } else if (p < 1 && state === ARMED) phase.set(PULLING);
  }, [angle, fill, grabFrom, haptic, phase, stretchTicks, ticks]);

  const pullEnd = useCallback((silent: boolean, cancelled: boolean, velocity: number) => {
    'worklet';
    const state = phase.get();
    if (state === ARMED && !cancelled) {
      startRefresh(silent, velocity);
      return;
    }
    if (state === PULLING || state === ARMED) {
      phase.set(IDLE);
      // Velocity handoff: the finger's speed carries into the spring home.
      angle.set(reduceMotion ? spring(PULL.restAngle, SPRING.home) : withSpring(PULL.restAngle, { ...SPRING.home, velocity: velocity * PULL.degPerPt }));
      fill.set(withTiming(0, { duration: Math.max(fill.get() * LEVER.dots, 1) * (TIMING.dotMs / 3), easing: Easing.linear, ...NEVER }));
    }
  }, [angle, fill, phase, reduceMotion, spring, startRefresh]);

  // ——— Autoplay: the source's finger is the knob itself, so no extra finger is drawn ———
  const scriptLever = useCallback((t0: number, t1: number, s: { down: number; moveEnd: number; up: number; depth: number }) => {
    'worklet';
    if (t0 < s.down && t1 >= s.down) {
      scriptPulling.set(true);
      pullBegin(true);
    }
    const at = (t: number) => {
      'worklet';
      return PULL.leverPull * s.depth * ease((t - s.down) / (s.moveEnd - s.down));
    };
    if (scriptPulling.get() && t1 >= s.down && t1 < s.up) pullMove(at(t1), true);
    if (scriptPulling.get() && t0 < s.up && t1 >= s.up) {
      scriptPulling.set(false);
      // A scripted flick: the last part of the pull is fast, as a real release is.
      pullEnd(true, false, (at(s.moveEnd) - at(s.moveEnd - 0.08)) / 0.08 + 600);
    }
  }, [pullBegin, pullEnd, pullMove, scriptPulling]);

  useFrameCallback((frame) => {
    'worklet';
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    // Ambient: the lever breathes at rest. In the demo its clock is the loop clock, so the loop stays seamless.
    clock.set(autoPlay && !demoPaused.get() ? demoT.get() : clock.get() + dt);
    const target = phase.get() === IDLE && !realDown.get() && !scriptPulling.get() && !reduceMotion && !REPLICA_ONLY ? 1 : 0;
    idleAmp.set(idleAmp.get() + (target - idleAmp.get()) * Math.min(dt * 4, 1));
    idle.set(TIMING.idleAmplitude * idleAmp.get() * Math.sin((2 * Math.PI * clock.get()) / TIMING.idlePeriod));
    breath.set(reduceMotion ? 0 : Math.sin((2 * Math.PI * clock.get()) / LIGHT.haloBreath));
    if (!autoPlay) return;
    if (demoPaused.get()) {
      if (!realDown.get()) quiet.set(quiet.get() + dt);
      if (quiet.get() >= RESUME_MS / 1000 && phase.get() === IDLE) {
        demoT.set(0);
        demoPaused.set(false);
      }
      return;
    }
    const t0 = demoT.get();
    const t1 = t0 + dt;
    scriptLever(t0, t1, DEMO.full);
    if (t0 < DEMO.resolve && t1 >= DEMO.resolve) finishRefresh(true, refreshId.get());
    scriptLever(t0, t1, DEMO.partial);
    demoT.set(t1 >= LOOP_MS / 1000 ? t1 - LOOP_MS / 1000 : t1);
  });

  /** A real touch stops the script at once. */
  const interrupt = useCallback(() => {
    'worklet';
    realDown.set(true);
    quiet.set(0);
    if (demoPaused.get()) return;
    demoPaused.set(true);
    if (scriptPulling.get()) {
      scriptPulling.set(false);
      pullEnd(true, true, 0);
    }
    // A scripted request never resolves on its own: make it real.
    if (phase.get() === REFRESHING && scriptedRefresh.get()) {
      scriptedRefresh.set(false);
      scheduleOnRN(runFetch, refreshId.get());
    }
  }, [demoPaused, phase, pullEnd, quiet, realDown, refreshId, runFetch, scriptPulling, scriptedRefresh]);

  const release = useCallback(() => {
    'worklet';
    realDown.set(false);
    quiet.set(0);
  }, [quiet, realDown]);

  // Pull anywhere on the artboard, as the source's finger does on the knob.
  const gesture = useMemo(() => Gesture.Pan()
    .activeOffsetY(6)
    .onBegin(interrupt)
    .onStart(() => pullBegin(false))
    .onUpdate((e) => pullMove(e.translationY, false))
    .onFinalize((e, ok) => {
      release();
      pullEnd(false, !ok, e.velocityY);
    }), [interrupt, pullBegin, pullEnd, pullMove, release]);

  /** Accessible alternative to the pull. */
  const refreshNow = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      interrupt();
      release();
      if (phase.get() !== IDLE) return;
      fill.set(1);
      startRefresh(false, 0);
    });
  }, [fill, interrupt, phase, release, startRefresh]);

  return { gesture, refreshNow, label, values: { angle, sway, idle, shake, fill, lit, bloom, glow, breath, pulse, ripple, knobLit, arcLit } };
}
