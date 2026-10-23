import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import {
  cancelAnimation, ReduceMotion, useAnimatedReaction, useFrameCallback, useReducedMotion,
  useSharedValue, withSpring, withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { BLADE_CARDS, BLADE_PERIOD, type BladeMotion, type FanLayout } from './blade-contract';
import { drawDuration, drawState, entrance, flareStart, loopState, move, STEP_SECONDS } from './blade-timeline';

type Mode = 'waiting' | 'opening' | 'loop' | 'idle' | 'draw' | 'settling' | 'returning';
type LaunchLight = { charge: number; energy: number; press: number; flare: number };
const FADE = { duration: 300, easing: entrance, reduceMotion: ReduceMotion.Never };
const PRESS_SPRING = { stiffness: 380, damping: 26, reduceMotion: ReduceMotion.Never };

function lightHaptic() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}
function landingHaptic() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}

/** All clocks and gesture ownership live on UI. RN receives discrete accessibility/haptic events. */
export function useBladeMotion({ layout, ready, autoPlay }: { layout: FanLayout; ready: boolean; autoPlay: boolean }) {
  const reduced = useReducedMotion();
  const slotPx = 78 * layout.scale;
  const width = layout.width;
  const pos = useSharedValue(0);
  const charge = useSharedValue(0);
  const flare = useSharedValue(0);
  const press = useSharedValue(0);
  const ctaEnergy = useSharedValue(1);
  const intro = useSharedValue(0);
  const still = useSharedValue(reduced ? 1 : 0);
  const opacity = useSharedValue(0);
  const available = useSharedValue(false);
  const plays = useSharedValue(false);
  const started = useSharedValue(false);
  const mode = useSharedValue<Mode>('waiting');
  const elapsed = useSharedValue(0);
  const lastFrame = useSharedValue(-1);
  const idleElapsed = useSharedValue(0);
  const base = useSharedValue(0);
  const origin = useSharedValue(0);
  const target = useSharedValue(0);
  const distance = useSharedValue(4);
  const launchLight = useSharedValue<LaunchLight>({ charge: 1, energy: 1, press: 1, flare: 0 });
  const drawingUI = useSharedValue(false);
  const tickHaptics = useSharedValue(false);
  const landed = useSharedValue(false);
  // 0 = no owner; 1 = fan; 2 = bar. A second finger never steals the first touch.
  const owner = useSharedValue(0);
  const dragOrigin = useSharedValue(0);
  const dragVelocity = useSharedValue(0);
  const dragged = useSharedValue(false);
  const [hero, setHero] = useState(0);
  const [drawing, setDrawing] = useState(false);

  const reportHero = useCallback((index: number, tactile: boolean) => {
    setHero(index);
    if (tactile) void Haptics.selectionAsync().catch(() => {});
  }, []);
  const reportDrawing = useCallback((busy: boolean) => setDrawing(busy), []);
  useAnimatedReaction(() => Math.round(pos.get()), (index, previous) => {
    if (index !== previous) scheduleOnRN(reportHero, index, previous !== null && tickHaptics.get());
  });
  useAnimatedReaction(() => drawingUI.get(), (busy, previous) => {
    if (busy !== previous) scheduleOnRN(reportDrawing, busy);
  });

  const finishMotion = useCallback((finished?: boolean) => {
    'worklet';
    if (!finished) return;
    mode.set('idle');
    drawingUI.set(false);
    tickHaptics.set(false);
    idleElapsed.set(0);
  }, [drawingUI, idleElapsed, mode, tickHaptics]);

  const finishFadeOut = useCallback((finished?: boolean) => {
    'worklet';
    if (!finished) return;
    pos.set(target.get());
    charge.set(withTiming(1, { duration: 240, easing: entrance, reduceMotion: ReduceMotion.Never }, finishMotion));
  }, [charge, finishMotion, pos, target]);

  const finishReturn = useCallback((finished?: boolean) => {
    'worklet';
    if (!finished) return;
    base.set(target.get());
    elapsed.set(0);
    charge.set(1);
    flare.set(0);
    press.set(0);
    ctaEnergy.set(1);
    intro.set(1);
    mode.set(plays.get() ? 'loop' : 'idle');
    idleElapsed.set(0);
  }, [base, charge, ctaEnergy, elapsed, flare, idleElapsed, intro, mode, plays, press, target]);

  /** Cancel animations without writing their destinations: the next owner sees the exact current frame. */
  const freeze = useCallback(() => {
    'worklet';
    cancelAnimation(pos);
    cancelAnimation(charge);
    cancelAnimation(flare);
    cancelAnimation(press);
    cancelAnimation(ctaEnergy);
    cancelAnimation(intro);
    mode.set('idle');
    elapsed.set(0);
    idleElapsed.set(0);
    drawingUI.set(false);
    tickHaptics.set(false);
  }, [charge, ctaEnergy, drawingUI, elapsed, flare, idleElapsed, intro, mode, pos, press, tickHaptics]);

  const restoreLight = useCallback(() => {
    'worklet';
    charge.set(withSpring(1, PRESS_SPRING));
    flare.set(withTiming(0, { duration: 180, easing: entrance, reduceMotion: ReduceMotion.Never }));
    ctaEnergy.set(withTiming(1, FADE));
    intro.set(withTiming(1, FADE));
    press.set(withSpring(0, PRESS_SPRING));
  }, [charge, ctaEnergy, flare, intro, press]);

  const fadeTo = useCallback((next: number, isDraw = false) => {
    'worklet';
    cancelAnimation(pos);
    cancelAnimation(charge);
    target.set(next);
    mode.set('settling');
    drawingUI.set(isDraw);
    flare.set(0);
    ctaEnergy.set(withTiming(1, FADE));
    intro.set(1);
    press.set(withSpring(0, PRESS_SPRING));
    charge.set(withTiming(0, { duration: 160, easing: entrance, reduceMotion: ReduceMotion.Never }, finishFadeOut));
  }, [charge, ctaEnergy, drawingUI, finishFadeOut, flare, intro, mode, pos, press, target]);

  const stepTo = useCallback((next: number) => {
    'worklet';
    idleElapsed.set(0);
    if (reduced) { fadeTo(next); return; }
    restoreLight();
    mode.set('settling');
    pos.set(withTiming(next, { duration: STEP_SECONDS * 1000 * Math.sqrt(Math.max(1, Math.abs(next - pos.get()))),
      easing: move, reduceMotion: ReduceMotion.Never }, finishMotion));
  }, [fadeTo, finishMotion, idleElapsed, mode, pos, reduced, restoreLight]);

  const startDraw = useCallback(() => {
    'worklet';
    // A draw lands on a whole card even when the user caught the loop between cards.
    const next = Math.round(pos.get()) + 3 + Math.floor(Math.random() * 3);
    tickHaptics.set(true);
    if (reduced) { fadeTo(next, true); return; }
    origin.set(pos.get());
    distance.set(next - pos.get());
    launchLight.set({ charge: charge.get(), energy: ctaEnergy.get(), press: press.get(), flare: flare.get() });
    cancelAnimation(press);
    intro.set(withTiming(1, FADE));
    elapsed.set(0);
    landed.set(false);
    drawingUI.set(true);
    mode.set('draw');
  }, [charge, ctaEnergy, distance, drawingUI, elapsed, fadeTo, flare, intro, landed, launchLight, mode, origin, pos, press, reduced, tickHaptics]);

  const endFan = useCallback((x: number, cancelled: boolean) => {
    'worklet';
    if (owner.get() !== 1) return;
    owner.set(0);
    idleElapsed.set(0);
    if (cancelled || dragged.get()) {
      const v = cancelled ? 0 : dragVelocity.get() / slotPx;
      const travel = Math.max(-4, Math.min(4, v * 0.16));
      const next = Math.round(pos.get() + travel);
      if (reduced) { fadeTo(next); return; }
      restoreLight();
      mode.set('settling');
      pos.set(withSpring(next, { stiffness: 150, damping: 21, mass: 1, velocity: v,
        reduceMotion: ReduceMotion.Never }, finishMotion));
      return;
    }
    const slots = Math.max(-3, Math.min(3, Math.round((x - width / 2) / slotPx)));
    if (slots !== 0 || reduced) {
      stepTo(Math.round(pos.get()) - slots);
    } else {
      restoreLight();
      mode.set('settling');
      // The frame clock schedules the two pulse phases; there is no worklet-local callback.
      elapsed.set(0);
      charge.set(withTiming(1.15, { duration: 120, easing: entrance, reduceMotion: ReduceMotion.Never }));
      target.set(pos.get());
      landed.set(true);
    }
  }, [charge, dragVelocity, dragged, elapsed, fadeTo, finishMotion, idleElapsed, landed, mode, owner,
    pos, reduced, restoreLight, slotPx, stepTo, target, width]);

  const syncReady = useCallback((canStart: boolean, autoplay: boolean) => {
    'worklet';
    available.set(canStart);
    plays.set(autoplay && !reduced);
    if (!canStart) return;
    if (!started.get()) {
      started.set(true);
      opacity.set(withTiming(1, FADE));
      elapsed.set(0);
      mode.set('opening');
      if (reduced) { intro.set(1); charge.set(1); }
    } else if (!autoplay && mode.get() === 'loop') {
      freeze();
      stepTo(Math.round(pos.get()));
    }
  }, [available, charge, elapsed, freeze, intro, mode, opacity, plays, pos, reduced, started, stepTo]);

  const stop = useCallback(() => {
    'worklet';
    available.set(false);
    owner.set(0);
    freeze();
    cancelAnimation(opacity);
    started.set(false);
  }, [available, freeze, opacity, owner, started]);
  useEffect(() => { scheduleOnUI(syncReady, ready, autoPlay); }, [autoPlay, ready, syncReady]);
  useEffect(() => () => { scheduleOnUI(stop); }, [stop]);

  useFrameCallback((frame) => {
    'worklet';
    // Timed from the frame's own timestamp: a re-render re-registers this callback, and
    // timeSincePreviousFrame is then empty for a frame, which made each loop about ten frames long.
    const before = lastFrame.get();
    lastFrame.set(frame.timestamp);
    if (!available.get()) return;
    const dt = Math.min(100, Math.max(0, before < 0 ? 0 : frame.timestamp - before)) / 1000;
    const current = mode.get();
    if (current === 'opening') {
      const t = elapsed.get() + dt;
      elapsed.set(t);
      if (!reduced) {
        intro.set(entrance(t / 0.9));
        charge.set(entrance((t - 0.4) / 0.5));
      }
      if (t >= (reduced ? 0.3 : 0.9)) {
        intro.set(1);
        charge.set(1);
        elapsed.set(0);
        mode.set(plays.get() ? 'loop' : 'idle');
      }
    } else if (current === 'loop') {
      let t = elapsed.get() + dt;
      let b = base.get();
      if (t >= 10) { t -= 10; b += BLADE_PERIOD; base.set(b); }
      elapsed.set(t);
      const state = loopState(t, b);
      pos.set(state.pos);
      charge.set(state.charge);
      flare.set(state.flare);
      press.set(state.press);
      ctaEnergy.set(state.ctaEnergy);
    } else if (current === 'draw') {
      const t = elapsed.get() + dt;
      elapsed.set(t);
      const n = distance.get();
      const light = launchLight.get();
      const state = drawState(t, n, 1, light.charge, light.energy, light.press, light.flare);
      pos.set(origin.get() + state.pos);
      charge.set(state.charge);
      flare.set(state.flare);
      press.set(state.press);
      ctaEnergy.set(state.ctaEnergy);
      if (!landed.get() && t >= flareStart(n)) { landed.set(true); scheduleOnRN(landingHaptic); }
      if (t >= drawDuration(n)) { landed.set(false); finishMotion(true); }
    } else if (current === 'settling' && landed.get()) {
      const t = elapsed.get() + dt;
      elapsed.set(t);
      if (t >= 0.12) {
        landed.set(false);
        charge.set(withTiming(1, { duration: 400, easing: entrance, reduceMotion: ReduceMotion.Never }, finishMotion));
      }
    } else if (current === 'idle' && owner.get() === 0 && plays.get()) {
      const atRest = Math.abs(charge.get() - 1) < 0.001 && Math.abs(flare.get()) < 0.001
        && Math.abs(press.get()) < 0.001 && Math.abs(ctaEnergy.get() - 1) < 0.001 && intro.get() > 0.999;
      idleElapsed.set(atRest ? idleElapsed.get() + dt : 0);
      if (idleElapsed.get() >= 3.5) {
        const next = Math.round(pos.get() / BLADE_PERIOD) * BLADE_PERIOD;
        target.set(next);
        mode.set('returning');
        tickHaptics.set(false);
        pos.set(withTiming(next, { duration: 900, easing: move, reduceMotion: ReduceMotion.Never }, finishReturn));
      }
    }
  });

  // Gesture builders register deferred callbacks; no shared value is read during render.
  const fanGesture = useMemo(() => Gesture.Pan().enabled(ready).minDistance(0).maxPointers(1)
    .onTouchesDown(() => {
      if (!available.get() || owner.get() !== 0) return;
      freeze();
      landed.set(false);
      owner.set(1);
      dragOrigin.set(pos.get());
      dragVelocity.set(0);
      dragged.set(false);
    })
    .onUpdate((event) => {
      if (owner.get() !== 1) return;
      dragVelocity.set(event.velocityX);
      if (!dragged.get() && Math.abs(event.translationX) <= 3 && Math.abs(event.translationY) <= 3) return;
      if (!dragged.get()) { dragged.set(true); tickHaptics.set(true); restoreLight(); }
      pos.set(dragOrigin.get() + event.translationX / slotPx);
    })
    .onTouchesUp((event) => {
      const touch = event.changedTouches[0];
      if (touch) endFan(touch.x, false);
    })
    .onFinalize((event) => {
      // Also handles FAILED pans, cancelled touches, and OS interruption.
      if (owner.get() === 1) endFan(event.x, true);
    }), [available, dragOrigin, dragVelocity, dragged, endFan, freeze, landed, owner, pos, ready, restoreLight, slotPx, tickHaptics]);

  const barGesture = useMemo(() => Gesture.Tap().enabled(ready).maxDuration(60000).maxDistance(10000)
    .shouldCancelWhenOutside(true)
    .onBegin(() => {
      if (!available.get() || owner.get() !== 0) return;
      freeze();
      landed.set(false);
      owner.set(2);
      press.set(withTiming(1, { duration: 110, easing: entrance, reduceMotion: ReduceMotion.Never }));
      scheduleOnRN(lightHaptic);
    })
    .onEnd((_event, success) => {
      if (owner.get() !== 2 || !success) return;
      owner.set(0);
      startDraw();
    })
    .onFinalize(() => {
      if (owner.get() !== 2) return;
      owner.set(0);
      // A release outside cancels, including a finger that never became a pan.
      stepTo(Math.round(pos.get()));
    }), [available, freeze, landed, owner, pos, press, ready, startDraw, stepTo]);

  const accessibilityStepUI = useCallback((delta: number) => {
    'worklet';
    if (!available.get() || owner.get() !== 0) return;
    freeze();
    landed.set(false);
    stepTo(Math.round(pos.get()) + delta);
  }, [available, freeze, landed, owner, pos, stepTo]);
  const accessibilityDrawUI = useCallback(() => {
    'worklet';
    if (!available.get() || owner.get() !== 0) return;
    freeze();
    scheduleOnRN(lightHaptic);
    startDraw();
  }, [available, freeze, owner, startDraw]);
  const step = useCallback((delta: number) => scheduleOnUI(accessibilityStepUI, delta), [accessibilityStepUI]);
  const draw = useCallback(() => scheduleOnUI(accessibilityDrawUI), [accessibilityDrawUI]);
  const motion = useMemo<BladeMotion>(() => ({ pos, charge, flare, press, ctaEnergy, intro, still }),
    [charge, ctaEnergy, flare, intro, pos, press, still]);
  const card = BLADE_CARDS[((hero % BLADE_PERIOD) + BLADE_PERIOD) % BLADE_PERIOD];
  return { motion, opacity, fanGesture, barGesture, drawing, step, draw, heroText: `${card.value}, ${card.hue}` };
}
