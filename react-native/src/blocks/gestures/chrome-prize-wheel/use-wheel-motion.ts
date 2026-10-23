// Chrome Prize Wheel — one UI frame clock; only phase changes and throttled haptics cross to RN.
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Gesture, type GestureType } from 'react-native-gesture-handler';
import { useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PHASE, type WheelLayout, type WheelMotion } from './wheel-contract';
import {
  actWheel, advanceScene, cloneScene, initialScene, MOTION_EVENT,
  type SceneConfig, type WheelAction, type WheelScene,
} from './wheel-sim';

export type WheelMotionOptions = {
  layout: WheelLayout | null;
  /** All coordinates share the measured wrapping View's local origin. */
  buttonCentre: { x: number; y: number } | null;
  heroCentre: { x: number; y: number } | null;
  autoPlay: boolean;
  ready: boolean;
};
export type WheelMotionApi = {
  motion: WheelMotion;
  /** Attach to the measured full-container View (which covers the wheel); outside-circle touches
   * cancel the demo but fail recognition. This also catches touches in the quiet backdrop. */
  wheelGesture: GestureType;
  /** Attach to the hero's own hit View; translations are relative to the finger's touch-down. */
  heroGesture: GestureType;
  collect: { onPressIn(): void; onPressOut(): void; onPress(): void };
  spinFromHub(): void;
  status: { state: 'idle' | 'spinning' | 'won'; winner: number };
};

function fireHaptics(events: number): void {
  if (events & MOTION_EVENT.success) void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else if (events & MOTION_EVENT.medium) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  else if (events & MOTION_EVENT.rigid) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
  else if (events & MOTION_EVENT.light) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function useWheelMotion(options: WheelMotionOptions): WheelMotionApi {
  const reduced = useReducedMotion();
  const { ready, autoPlay, layout, buttonCentre, heroCentre } = options;
  const cx = layout?.cx ?? 0;
  const cy = layout?.cy ?? 0;
  const radius = layout?.radius ?? 0;
  const buttonX = buttonCentre?.x ?? cx;
  const buttonY = buttonCentre?.y ?? cy;
  const canAdvance = ready && radius > 0 && (!autoPlay || reduced || buttonCentre !== null);
  const heroReady = heroCentre !== null;
  const config = useMemo<SceneConfig>(() => ({ reduced, autoPlay, cx, cy, radius, buttonX, buttonY }),
    [reduced, autoPlay, cx, cy, radius, buttonX, buttonY]);
  const scene = useSharedValue(initialScene());
  const publishedPhase = useSharedValue<number>(PHASE.idle);
  const publishedWinner = useSharedValue(-1);
  const theta = useSharedValue<number>(0);
  const omega = useSharedValue<number>(0);
  const tiltX = useSharedValue<number>(0);
  const tiltY = useSharedValue<number>(0);
  const envTurn = useSharedValue<number>(0);
  const swayA = useSharedValue<number>(0);
  const swayB = useSharedValue<number>(0);
  const winner = useSharedValue<number>(-1);
  const lift = useSharedValue<number>(0);
  const glow = useSharedValue<number>(0);
  const winnerPrize = useSharedValue<number>(1);
  const winnerPrizeScale = useSharedValue<number>(1);
  const dim = useSharedValue<number>(0);
  const comet = useSharedValue<number>(0);
  const hubPress = useSharedValue<number>(0);
  const phase = useSharedValue<number>(0);
  const swing = useSharedValue<number>(0);
  const tick = useSharedValue<number>(0);
  const strikes = useSharedValue<number>(0);
  const wheelScale = useSharedValue<number>(1);
  const wheelOpacity = useSharedValue<number>(1);
  const swirl = useSharedValue<number>(0);
  const hero = useSharedValue<number>(0);
  const heroSquash = useSharedValue<number>(0);
  const heroX = useSharedValue<number>(0);
  const heroY = useSharedValue<number>(0);
  const heroTilt = useSharedValue<number>(0);
  const rays = useSharedValue<number>(0);
  const burst = useSharedValue<number>(-1);
  const burstPower = useSharedValue<number>(0.6);
  const badge = useSharedValue<number>(0);
  const caption = useSharedValue<number>(0);
  const count = useSharedValue<number>(0);
  const button = useSharedValue<number>(0);
  const buttonPress = useSharedValue<number>(0);
  const collect = useSharedValue<number>(0);
  const copySwap = useSharedValue<number>(0);
  const hint = useSharedValue<number>(1);
  const hintNudge = useSharedValue<number>(0);
  const touchX = useSharedValue<number>(0);
  const touchY = useSharedValue<number>(0);
  const touchOn = useSharedValue<number>(0);
  const [status, setStatus] = useState<WheelMotionApi['status']>({ state: 'idle', winner: -1 });
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const report = useCallback((nextPhase: number, nextWinner: number, events: number, changed: boolean) => {
    if (!mounted.current) return;
    fireHaptics(events);
    if (changed) setStatus({
      state: nextPhase === PHASE.reveal || nextPhase === PHASE.collecting ? 'won'
        : nextPhase === PHASE.idle || nextPhase === PHASE.held ? 'idle' : 'spinning',
      winner: nextWinner,
    });
  }, []);
  const publish = useCallback((s: WheelScene) => {
    'worklet';
    scene.set(s);
    const v = s.values;
    theta.set(v.theta);
    omega.set(v.omega);
    tiltX.set(v.tiltX);
    tiltY.set(v.tiltY);
    envTurn.set(v.envTurn);
    swayA.set(v.swayA);
    swayB.set(v.swayB);
    winner.set(v.winner);
    lift.set(v.lift);
    glow.set(v.glow);
    winnerPrize.set(v.winnerPrize);
    winnerPrizeScale.set(v.winnerPrizeScale);
    dim.set(v.dim);
    comet.set(v.comet);
    hubPress.set(v.hubPress);
    phase.set(v.phase);
    swing.set(v.swing);
    tick.set(v.tick);
    strikes.set(v.strikes);
    wheelScale.set(v.wheelScale);
    wheelOpacity.set(v.wheelOpacity);
    swirl.set(v.swirl);
    hero.set(v.hero);
    heroSquash.set(v.heroSquash);
    heroX.set(v.heroX);
    heroY.set(v.heroY);
    heroTilt.set(v.heroTilt);
    rays.set(v.rays);
    burst.set(v.burst);
    burstPower.set(v.burstPower);
    badge.set(v.badge);
    caption.set(v.caption);
    count.set(v.count);
    button.set(v.button);
    buttonPress.set(v.buttonPress);
    collect.set(v.collect);
    copySwap.set(v.copySwap);
    hint.set(v.hint);
    hintNudge.set(v.hintNudge);
    touchX.set(v.touchX);
    touchY.set(v.touchY);
    touchOn.set(v.touchOn);
    const changed = publishedPhase.get() !== v.phase || publishedWinner.get() !== v.winner;
    if (changed || s.events) {
      publishedPhase.set(v.phase); publishedWinner.set(v.winner);
      scheduleOnRN(report, v.phase, v.winner, s.events, changed);
    }
  }, [scene, publishedPhase, publishedWinner, report,
    theta, omega, tiltX, tiltY, envTurn, swayA, swayB, winner, lift, glow, winnerPrize, winnerPrizeScale, dim, comet, hubPress, phase, swing, tick, strikes, wheelScale, wheelOpacity, swirl, hero, heroSquash, heroX, heroY, heroTilt, rays, burst, burstPower, badge, caption, count, button, buttonPress, collect, copySwap, hint, hintNudge, touchX, touchY, touchOn]);

  const dispatch = useCallback((action: WheelAction) => {
    'worklet';
    if (!canAdvance) return;
    const next = cloneScene(scene.get());
    actWheel(next, action, config);
    publish(next);
  }, [canAdvance, config, publish, scene]);

  useFrameCallback((frame) => {
    'worklet';
    if (!canAdvance || frame.timeSincePreviousFrame === null) return;
    const next = cloneScene(scene.get());
    advanceScene(next, frame.timeSincePreviousFrame / 1000, config);
    publish(next);
  });

  const finishWheel = useCallback((event: { x: number; y: number; velocityX: number; velocityY: number }, success: boolean) => {
    'worklet';
    dispatch({ type: 'release', x: event.x - cx, y: event.y - cy,
      vx: event.velocityX, vy: event.velocityY, cancelled: !success, random: Math.random() });
  }, [cx, cy, dispatch]);

  const beginWheel = useCallback((x: number, y: number) => {
    'worklet';
    if (!canAdvance) return false;
    const dx = x - cx;
    const dy = y - cy;
    dispatch({ type: 'down', x: dx, y: dy });
    return dx * dx + dy * dy <= radius * radius && phase.get() !== PHASE.reveal && phase.get() !== PHASE.collecting;
  }, [canAdvance, cx, cy, dispatch, phase, radius]);
  const moveWheel = useCallback((x: number, y: number) => {
    'worklet';
    dispatch({ type: 'move', x: x - cx, y: y - cy });
  }, [cx, cy, dispatch]);
  const beginHero = useCallback(() => {
    'worklet';
    dispatch({ type: 'heroDown' });
  }, [dispatch]);
  const moveHero = useCallback((x: number, y: number, vx: number) => {
    'worklet';
    dispatch({ type: 'heroMove', x, y, vx });
  }, [dispatch]);
  const finishHero = useCallback(() => {
    'worklet';
    dispatch({ type: 'heroRelease' });
  }, [dispatch]);

  // The compiler sees only shared values/data and the dispatch worklet in these callbacks.
  // Never capture the returned API: it also contains non-serialisable gesture instances.
  /* eslint-disable react-hooks/refs -- RNGH registers these callbacks; it never invokes them during render. */
  const wheelGesture = useMemo(() => Gesture.Pan().minDistance(0).maxPointers(1)
    .onTouchesDown((event, manager) => {
      const finger = event.changedTouches[0];
      if (!finger || !beginWheel(finger.x, finger.y)) manager.fail();
    })
    .onUpdate((event) => {
      moveWheel(event.x, event.y);
    })
    .onFinalize(finishWheel), [beginWheel, moveWheel, finishWheel]);
  const heroGesture = useMemo(() => Gesture.Pan().minDistance(0).maxPointers(1)
    .enabled(heroReady)
    .onBegin(beginHero)
    .onUpdate((event) => {
      moveHero(event.translationX, event.translationY, event.velocityX);
    })
    .onFinalize(finishHero), [beginHero, moveHero, finishHero, heroReady]);
  /* eslint-enable react-hooks/refs */
  const onPressIn = useCallback(() => scheduleOnUI(dispatch, { type: 'pressIn' }), [dispatch]);
  const onPressOut = useCallback(() => scheduleOnUI(dispatch, { type: 'pressOut' }), [dispatch]);
  const onPress = useCallback(() => scheduleOnUI(dispatch, { type: 'collect' }), [dispatch]);
  const spinFromHub = useCallback(() => scheduleOnUI(dispatch, { type: 'hub', random: Math.random() }), [dispatch]);
  const collectHandlers = useMemo(() => ({ onPressIn, onPressOut, onPress }), [onPress, onPressIn, onPressOut]);
  const motion = useMemo<WheelMotion>(() => ({
    theta, omega, tiltX, tiltY, envTurn, swayA, swayB, winner, lift, glow, winnerPrize, winnerPrizeScale, dim, comet, hubPress, phase, swing, tick, strikes, wheelScale, wheelOpacity, swirl, hero, heroSquash, heroX, heroY, heroTilt, rays, burst, burstPower, badge, caption, count, button, buttonPress, collect, copySwap, hint, hintNudge, touchX, touchY, touchOn
  }), [theta, omega, tiltX, tiltY, envTurn, swayA, swayB, winner, lift, glow, winnerPrize, winnerPrizeScale, dim, comet, hubPress, phase, swing, tick, strikes, wheelScale, wheelOpacity, swirl, hero, heroSquash, heroX, heroY, heroTilt, rays, burst, burstPower, badge, caption, count, button, buttonPress, collect, copySwap, hint, hintNudge, touchX, touchY, touchOn]);
  return { motion, wheelGesture, heroGesture, collect: collectHandlers, spinFromHub, status };
}
