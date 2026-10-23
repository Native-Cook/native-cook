import { useCallback, useEffect, useRef } from "react";
import {
  type FrameCallback,
  type SharedValue,
  useAnimatedReaction,
  useFrameCallback,
  useSharedValue,
} from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";
import { referenceGeometry as g } from "./reference-motion";
import { sloshTarget, springSettled, springStep } from "./motion-logic";

// Frames of stillness (light unchanged, spring at rest) before the per-frame
// simulation switches itself off: ~0.5s at 60Hz, ~0.25s at 120Hz.
const QUIET_FRAMES = 30;

// Liquid meniscus: the light's leading edge is a damped spring driven by the
// light's own speed, so it stretches while moving and wobbles to rest. The
// frame callback only runs while the light moves or the spring rings; any
// change of the light wakes it, and it parks itself once everything is still.
export function useMeniscus(fill: SharedValue<number>, on: boolean) {
  const slosh = useSharedValue(0),
    velocity = useSharedValue(0),
    lastFill = useSharedValue(-1),
    quiet = useSharedValue(0),
    live = useSharedValue(false);
  const frameRef = useRef<FrameCallback | null>(null);
  const start = useCallback(() => frameRef.current?.setActive(true), []);
  const park = useCallback(() => frameRef.current?.setActive(false), []);
  const frame = useFrameCallback((info) => {
    "worklet";
    const dt = (info.timeSincePreviousFrame ?? 16) / 1000;
    const current = fill.get();
    const previous = lastFill.get();
    lastFill.set(current);
    if (previous < 0) return;
    const speed =
      ((current - previous) * g.track.width) / Math.max(dt, 1 / 240);
    const next = springStep(
      slosh.get(),
      velocity.get(),
      sloshTarget(speed),
      dt,
    );
    slosh.set(next.position);
    velocity.set(next.velocity);
    if (current === previous && springSettled(next.position, next.velocity)) {
      quiet.set(quiet.get() + 1);
      if (quiet.get() > QUIET_FRAMES) {
        slosh.set(0);
        velocity.set(0);
        live.set(false);
        scheduleOnRN(park);
      }
    } else quiet.set(0);
  }, false);
  useEffect(() => {
    frameRef.current = frame;
  }, [frame]);
  useAnimatedReaction(
    () => fill.get(),
    (current, previous) => {
      if (!on || previous === null || current === previous || live.get())
        return;
      live.set(true);
      lastFill.set(-1);
      quiet.set(0);
      scheduleOnRN(start);
    },
    [on],
  );
  useEffect(() => {
    if (on) return;
    frame.setActive(false);
    live.set(false);
    slosh.set(0);
    velocity.set(0);
  }, [on, frame, live, slosh, velocity]);
  return slosh;
}
