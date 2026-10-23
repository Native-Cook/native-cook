import { useCallback, useMemo } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import { Easing, ReduceMotion, useAnimatedReaction, useFrameCallback, useSharedValue, withDelay, withSpring, withTiming } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import { D, designFrame } from './metacard-layout';
import { clamp, loopState, reducedLoopState } from './metacard-timeline';

type Options = {
  autoPlay: boolean; reduced: boolean; width: number; height: number;
  onHaptic: (heavy: boolean) => void; onScene: (hero: boolean) => void;
};
export function useMetacardMotion({ autoPlay, reduced, width, height, onHaptic, onScene }: Options) {
  const clock = useSharedValue(0), spin = useSharedValue<number>(D.spin), u = useSharedValue(0), forward = useSharedValue(1);
  const anchorSpin = useSharedValue<number>(D.spin);
  const fanPress = useSharedValue(0), objectPress = useSharedValue(0);
  const dragYaw = useSharedValue(0), dragPitch = useSharedValue(0), fanVelocity = useSharedValue(0);
  const epoch = useSharedValue(-1), now = useSharedValue(0), paused = useSharedValue(false);
  const lastTouch = useSharedValue(-10000), down = useSharedValue(false), spinOffset = useSharedValue(0);
  const userTransition = useSharedValue(false), locked = useSharedValue(false);
  const hit = useSharedValue(0), lastAngle = useSharedValue(0), initialYaw = useSharedValue(0), initialPitch = useSharedValue(0);
  const frame = designFrame(width, height);

  const toggle = useCallback(() => {
    'worklet';
    const progress = u.get();
    if (progress > 0.0001 && progress < 0.9999) return;
    paused.set(true); lastTouch.set(now.get()); userTransition.set(true); locked.set(false);
    const target = progress < 0.5 ? 1 : 0;
    anchorSpin.set(spin.get());
    forward.set(target);
    scheduleOnRN(onHaptic, false);
    const animation = withTiming(target, { duration: reduced ? 320 : target ? 1900 : 1400,
      easing: Easing.linear, reduceMotion: ReduceMotion.Never });
    u.set(reduced ? withDelay(120, animation, ReduceMotion.Never) : animation);
  }, [anchorSpin, forward, lastTouch, locked, now, onHaptic, paused, reduced, spin, u, userTransition]);

  const release = useCallback(() => {
    'worklet';
    down.set(false); lastTouch.set(now.get());
    fanPress.set(withTiming(0, { duration: 180 })); objectPress.set(withTiming(0, { duration: 180 }));
  }, [down, fanPress, lastTouch, now, objectPress]);

  useFrameCallback((event) => {
    'worklet';
    const stamp = event.timestamp;
    now.set(stamp);
    if (epoch.get() < 0) epoch.set(stamp);
    const dt = Math.min((event.timeSincePreviousFrame ?? 0) / 1000, 0.1);
    if (autoPlay && paused.get() && !down.get() && stamp - lastTouch.get() > 3500 && (u.get() <= 0 || u.get() >= 1)) {
      const base = u.get() > 0.5 ? 4.9 : 0;
      epoch.set(stamp - base * 1000);
      spinOffset.set(spin.get() - loopState(base).spin);
      paused.set(false); userTransition.set(false);
    }
    if (autoPlay && !paused.get()) {
      const seconds = (stamp - epoch.get()) / 1000, local = ((seconds % 12) + 12) % 12;
      const state = loopState(seconds);
      // Select each card's next landscape orientation once, even after a freely spun fan resumes.
      anchorSpin.set(loopState(state.forward ? 3 : 9.4).spin + spinOffset.get());
      let progress = state.u;
      if (reduced) {
        const still = reducedLoopState(local);
        progress = still.u; objectPress.set(still.press);
      }
      u.set(progress); forward.set(state.forward); spin.set(reduced ? D.spin : state.spin + spinOffset.get());
      clock.set(reduced ? 0 : local);
    } else {
      clock.set(reduced ? 0 : clock.get() + dt);
      if (!reduced && !down.get() && u.get() === 0) {
        const velocity = fanVelocity.get(), decay = Math.exp(-dt / 0.6);
        spin.set(spin.get() + D.drift * dt + velocity * 0.6 * (1 - decay));
        fanVelocity.set(velocity * decay);
      }
    }
    if (userTransition.get() && forward.get() === 1 && !locked.get() && u.get() * 1900 >= 1025) {
      locked.set(true); scheduleOnRN(onHaptic, true);
    }
  });
  useAnimatedReaction(() => u.get() >= 0.5, (hero, previous) => {
    if (hero !== previous) scheduleOnRN(onScene, hero);
  });

  // Gesture builders only register closures here; their work runs on the UI thread.
  const gesture = useMemo(() => {
    const pan = Gesture.Pan().minDistance(3).maxPointers(1)
      .onTouchesDown((event) => {
        const touch = event.allTouches[0]; if (!touch) return;
        const x = (touch.x - frame.x) / frame.k, y = (touch.y - frame.y) / frame.k;
        const dx = x - D.fanX, dy = y - D.fanY, progress = u.get();
        const fanHit = dx * dx + dy * dy < D.fanHitRadius * D.fanHitRadius;
        const heroHit = Math.abs(x - D.heroX) < 175 && Math.abs(y - D.heroY) < 140;
        hit.set(progress === 0 && fanHit ? (dx * dx + dy * dy < D.hubHitRadius * D.hubHitRadius ? 1 : 2) : progress === 1 && heroHit ? 3 : 0);
        down.set(true); lastTouch.set(now.get());
        if (!paused.get() && progress > 0 && progress < 1) {
          const target = forward.get();
          u.set(withTiming(target, { duration: reduced ? 320 : (target ? 1900 * (1 - progress) : 1400 * progress),
            easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
        }
        paused.set(true);
        fanVelocity.set(0);
        fanPress.set(withTiming(fanHit ? 1 : 0, { duration: 120 }));
        objectPress.set(withTiming((heroHit || dx * dx + dy * dy < D.hubHitRadius * D.hubHitRadius) ? 1 : 0, { duration: 120 }));
        lastAngle.set(Math.atan2(dy, dx)); initialYaw.set(dragYaw.get()); initialPitch.set(dragPitch.get());
      })
      .onUpdate((event) => {
        if (reduced) return;
        const kind = hit.get();
        if (kind === 2) {
          // The hub (kind 1) is a button: a finger resting on the centre must not spin the fan,
          // and the angle under a finger is meaningless that close to the axis.
          const dx = (event.x - frame.x) / frame.k - D.fanX, dy = (event.y - frame.y) / frame.k - D.fanY;
          if (dx * dx + dy * dy < 900) return;
          const angle = Math.atan2(dy, dx), previous = lastAngle.get();
          const delta = Math.atan2(Math.sin(angle - previous), Math.cos(angle - previous));
          spin.set(spin.get() + delta * 180 / Math.PI); lastAngle.set(angle);
          fanVelocity.set(clamp((dx * event.velocityY / frame.k - dy * event.velocityX / frame.k) / Math.max(900, dx * dx + dy * dy) * 180 / Math.PI, -600, 600));
        } else if (kind === 3) {
          dragYaw.set(26 * Math.tanh((initialYaw.get() + event.translationX / frame.k * 0.14) / 26));
          dragPitch.set(26 * Math.tanh((initialPitch.get() - event.translationY / frame.k * 0.14) / 26));
        }
      })
      .onEnd((event) => {
        if (hit.get() === 3 && !reduced) {
          dragYaw.set(withSpring(0, { stiffness: 140, damping: 14, velocity: event.velocityX / frame.k * 0.14 }));
          dragPitch.set(withSpring(0, { stiffness: 140, damping: 14, velocity: -event.velocityY / frame.k * 0.14 }));
        }
      })
      .onTouchesUp(release).onTouchesCancelled(release).onFinalize((_event, success) => {
        release();
        if (!success && hit.get() === 3 && !reduced) {
          dragYaw.set(withSpring(0, { stiffness: 140, damping: 14 }));
          dragPitch.set(withSpring(0, { stiffness: 140, damping: 14 }));
        }
      });
    const tap = Gesture.Tap().maxDistance(8).onEnd((_event, success) => {
      if (success && (hit.get() === 1 || hit.get() === 3)) toggle();
    });
    return Gesture.Simultaneous(pan, tap);
  }, [down, dragPitch, dragYaw, fanPress, fanVelocity, forward, frame.k, frame.x, frame.y, hit, initialPitch, initialYaw,
    lastAngle, lastTouch, now, objectPress, paused, reduced, release, spin, toggle, u]);
  const activate = useCallback(() => scheduleOnUI(toggle), [toggle]);
  return { clock, spin, anchorSpin, u, forward, fanPress, objectPress, dragYaw, dragPitch, fanVelocity, gesture, activate };
}
export type MetacardMotion = ReturnType<typeof useMetacardMotion>;
export type MetacardValues = Pick<MetacardMotion, 'clock' | 'spin' | 'anchorSpin' | 'u' | 'forward' | 'fanPress' | 'objectPress' | 'dragYaw' | 'dragPitch'>;
