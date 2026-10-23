import * as Haptics from 'expo-haptics';
import { useCallback, useMemo } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import {
  Easing, ReduceMotion, useDerivedValue, useFrameCallback, useSharedValue, withDelay, withSpring, withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { mat3AxisAngle, mat3Identity, mat3Multiply, type Mat3 } from './prism-math';
import { prismPose } from './prism-pose';
import {
  ANTICIPATION, BURST, CLOCK_WRAP, LOOP, TUMBLE, modulo, prismAge, prismIdleLight,
  prismReturn, prismSinceTouch, prismTimeline, prismTrackball, prismTranspose,
} from './prism-timeline';

function pulseHaptic() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}
const ENTER = Easing.bezier(0.23, 1, 0.32, 1);
const CHARGE_EASE = Easing.out(Easing.cubic);

export function usePrismMotion({ autoPlay, reduced, radius, touchRadius, k }: {
  autoPlay: boolean; reduced: boolean; radius: number; touchRadius: number; k: number;
}) {
  const clock = useSharedValue(0);
  const epoch = useSharedValue(-1);
  const ready = useSharedValue(false);
  const canvasOpacity = useSharedValue(0);
  const headlineOpacity = useSharedValue(0);
  const entrance = useSharedValue(reduced ? 1 : 0);
  const headlineTravel = useSharedValue(reduced ? 1 : 0);
  const offset = useSharedValue<Mat3>(mat3Identity());
  const velocity = useSharedValue([0, 0]);
  const down = useSharedValue(false);
  const dragging = useSharedValue(false);
  const lastX = useSharedValue(0);
  const lastY = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const heldAt = useSharedValue(0);
  const heldCharge = useSharedValue(0);
  const relaxing = useSharedValue(false);
  const burstAt = useSharedValue(0);
  const burstGain = useSharedValue(1);
  const releaseCharge = useSharedValue(1);
  const surgeActive = useSharedValue(false);
  const scripted = useSharedValue(false);
  const lightOffset = useSharedValue([0, 0]);
  const lightMix = useSharedValue(0);

  const pose = useDerivedValue(() => prismPose(reduced ? 0.58 : modulo(clock.get(), TUMBLE) / TUMBLE));
  const values = useDerivedValue(() => {
    const now = clock.get();
    const surgeTime = surgeActive.get() ? prismAge(now, burstAt.get()) + 0.55 : -1;
    const pressing = down.get() || relaxing.get();
    return prismTimeline(now, pose.get().fan, surgeTime, burstGain.get(), reduced,
      pressing ? heldCharge.get() : -1, prismAge(now, heldAt.get()), releaseCharge.get());
  });
  const rotation = useDerivedValue(() => mat3Multiply(offset.get(),
    mat3Multiply(pose.get().rotation, mat3AxisAngle(0, 1, 0, values.get().spin))));
  const fan = useDerivedValue(() => values.get().fan);
  const charge = useDerivedValue(() => values.get().charge);
  const scale = useDerivedValue(() => values.get().scale * (0.94 + 0.06 * entrance.get()));
  const flash = useDerivedValue(() => values.get().flash);
  const ring = useDerivedValue(() => values.get().ring);
  const energy = useDerivedValue(() => values.get().energy);
  const sweep = useDerivedValue(() => values.get().sweep);
  const sweepPower = useDerivedValue(() => values.get().sweepPower);
  const light = useDerivedValue(() => {
    const idle = reduced ? [0, 0] : prismIdleLight(clock.get());
    const shift = lightOffset.get();
    const mix = lightMix.get();
    return [idle[0] * (1 - mix) + shift[0], idle[1] * (1 - mix) + shift[1]];
  });
  const moteClock = useDerivedValue(() => reduced ? 0 : clock.get());
  // Shader time is frozen too: Reduce Motion must not leave a moving material behind.
  const sceneClock = useDerivedValue(() => reduced ? 0 : clock.get());
  const burstAge = useDerivedValue(() => reduced || !surgeActive.get() ? -1 : prismAge(clock.get(), burstAt.get()));

  const beginEntrance = useCallback(() => {
    if (ready.get()) return;
    ready.set(true);
    canvasOpacity.set(withTiming(1, { duration: 600, reduceMotion: ReduceMotion.Never }));
    headlineOpacity.set(withDelay(100, withTiming(1, { duration: 700, easing: ENTER, reduceMotion: ReduceMotion.Never })));
    entrance.set(withTiming(1, { duration: reduced ? 0 : 800, easing: ENTER, reduceMotion: ReduceMotion.Never }));
    headlineTravel.set(withDelay(100, withTiming(1, { duration: reduced ? 0 : 700, easing: ENTER, reduceMotion: ReduceMotion.Never })));
  }, [canvasOpacity, entrance, headlineOpacity, headlineTravel, ready, reduced]);

  // Both kinds of burst use these same shared values and the same derived timeline.
  const fire = useCallback((gain: number, compression: number) => {
    'worklet';
    surgeActive.set(true);
    scripted.set(false);
    burstAt.set(clock.get());
    burstGain.set(reduced ? 0.5 : gain);
    releaseCharge.set(compression);
    relaxing.set(false);
    heldCharge.set(0);
    scheduleOnRN(pulseHaptic);
  }, [burstAt, burstGain, clock, heldCharge, reduced, relaxing, releaseCharge, scripted, surgeActive]);

  const releaseLight = useCallback(() => {
    'worklet';
    lightOffset.set(withSpring([0, 0], { stiffness: 120, damping: 18, reduceMotion: ReduceMotion.Never }));
    lightMix.set(withSpring(0, { stiffness: 120, damping: 18, reduceMotion: ReduceMotion.Never }));
  }, [lightMix, lightOffset]);

  useFrameCallback((frame) => {
    if (!ready.get()) return;
    if (epoch.get() < 0) { epoch.set(frame.timestamp); return; }
    const dt = (frame.timeSincePreviousFrame ?? 0) / 1000;
    // Derive time from the frame timestamp, avoiding accumulated rounding at loop boundaries.
    const unwrapped = (frame.timestamp - epoch.get()) / 1000;
    const previous = unwrapped - dt;
    const now = modulo(unwrapped, CLOCK_WRAP);
    clock.set(now);
    if (down.get()) lastTouch.set(now);
    if (surgeActive.get() && !scripted.get() && (prismAge(now, burstAt.get()) > 10 || dt > 10)) {
      surgeActive.set(false);
    }
    // Decide once at the anticipation boundary; a skipped surge never restarts mid-way.
    if (scripted.get() && Math.floor(previous / LOOP) !== Math.floor(unwrapped / LOOP)) {
      surgeActive.set(false);
      scripted.set(false);
    }
    const crossed = Math.floor((previous - ANTICIPATION) / LOOP) !== Math.floor((unwrapped - ANTICIPATION) / LOOP);
    const anticipationAt = Math.floor((unwrapped - ANTICIPATION) / LOOP) * LOOP + ANTICIPATION;
    if (autoPlay && !reduced && crossed && !down.get() && prismSinceTouch(modulo(anticipationAt, CLOCK_WRAP), lastTouch.get()) >= 3.5) {
      burstAt.set(modulo(anticipationAt + BURST - ANTICIPATION, CLOCK_WRAP));
      burstGain.set(1);
      releaseCharge.set(1);
      surgeActive.set(true);
      scripted.set(true);
    }
    if (!down.get()) {
      const v = velocity.get();
      const speed = Math.hypot(v[0], v[1]);
      if (!reduced && speed >= 0.02) {
        const decay = Math.exp(-dt / 0.85);
        // Integrate exponential drag analytically, so 60 and 120 Hz travel equally far.
        const distance = 0.85 * (1 - decay);
        offset.set(prismTrackball(offset.get(), v[0] * distance, v[1] * distance, 1));
        velocity.set(speed * decay < 0.02 ? [0, 0] : [v[0] * decay, v[1] * decay]);
      }
      const sinceTouch = prismSinceTouch(now, lastTouch.get());
      if (autoPlay && !reduced && sinceTouch >= 3.5) {
        velocity.set([0, 0]);
        offset.set(prismReturn(offset.get(), Math.min(dt, sinceTouch - 3.5)));
      }
      if (sinceTouch >= 10 || dt > 10) lastTouch.set(-100);
      if (relaxing.get() && heldCharge.get() === 0) relaxing.set(false);
    }
  });

  // The builder only registers worklets; it never reads a shared value during render.
  const gesture = useMemo(() => Gesture.Pan().minDistance(0).maxPointers(1)
    .onBegin((event) => {
      const x = event.x - touchRadius, y = event.y - touchRadius;
      if (!ready.get() || Math.hypot(x, y) > touchRadius) return;
      // Preserve the current orientation if a press catches a scripted/tapped spin.
      offset.set(mat3Multiply(rotation.get(), prismTranspose(pose.get().rotation)));
      velocity.set([0, 0]);
      surgeActive.set(false);
      scripted.set(false);
      relaxing.set(false);
      down.set(true);
      dragging.set(false);
      lastX.set(0); lastY.set(0);
      lastTouch.set(clock.get()); heldAt.set(clock.get());
      heldCharge.set(0);
      heldCharge.set(withTiming(1, { duration: 550, easing: CHARGE_EASE, reduceMotion: ReduceMotion.Never }));
      lightOffset.set([Math.max(-1, Math.min(1, x / radius)), Math.max(-1, Math.min(1, y / radius))]);
      lightMix.set(1);
    })
    .onUpdate((event) => {
      if (!down.get()) return;
      lastTouch.set(clock.get());
      lightOffset.set([Math.max(-1, Math.min(1, (event.x - touchRadius) / radius)),
        Math.max(-1, Math.min(1, (event.y - touchRadius) / radius))]);
      if (!dragging.get() && Math.hypot(event.translationX, event.translationY) >= 8 * k) {
        dragging.set(true);
        heldCharge.set(withTiming(0, { duration: 200, easing: CHARGE_EASE, reduceMotion: ReduceMotion.Never }));
      }
      if (dragging.get()) {
        offset.set(prismTrackball(offset.get(), event.translationX - lastX.get(), event.translationY - lastY.get(), radius));
        lastX.set(event.translationX); lastY.set(event.translationY);
      }
    })
    .onFinalize((event, success) => {
      if (!down.get()) return;
      down.set(false);
      lastTouch.set(clock.get());
      if (success && !dragging.get()) fire(0.85 + 0.15 * heldCharge.get(), heldCharge.get());
      else {
        velocity.set(success && !reduced ? [event.velocityX / radius, event.velocityY / radius] : [0, 0]);
        relaxing.set(true);
        heldCharge.set(withTiming(0, { duration: 200, easing: CHARGE_EASE, reduceMotion: ReduceMotion.Never }));
      }
      releaseLight();
    }), [clock, down, dragging, fire, heldAt, heldCharge, k, lastTouch, lastX, lastY, lightMix, lightOffset,
    offset, pose, radius, ready, reduced, relaxing, releaseLight, rotation, scripted, surgeActive, touchRadius, velocity]);

  const activateOnUI = useCallback(() => {
    'worklet';
    if (!ready.get()) return;
    lastTouch.set(clock.get());
    offset.set(mat3Multiply(rotation.get(), prismTranspose(pose.get().rotation)));
    velocity.set([0, 0]);
    down.set(false);
    fire(0.85, 0);
    releaseLight();
  }, [clock, down, fire, lastTouch, offset, pose, ready, releaseLight, rotation, velocity]);
  const activate = useCallback(() => scheduleOnUI(activateOnUI), [activateOnUI]);

  // Consumers must destructure shared values before using them inside worklets.
  return { clock, sceneClock, moteClock, rotation, fan, charge, scale, flash, ring, energy, sweep, sweepPower, light,
    burstAge, burstGain, canvasOpacity, headlineOpacity, headlineTravel, gesture, activate, beginEntrance };
}
