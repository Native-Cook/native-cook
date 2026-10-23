import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import {
  cancelAnimation,
  Easing,
  ReduceMotion,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import { atU, clamp, nearestTread } from './chart-curves';
import { film, smooth } from './chart-film';
import { EXTREMA, G, RISERS, SPRING, T, THUMB_SPRING } from './chart-theme';

// Send a selection haptic only when a scrub marker crosses a threshold.
function tick() {
  void Haptics.selectionAsync();
}
// Mark a user-started crash or breakout with a heavy haptic.
function impact() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
}
// Acknowledge switch press-in on the JavaScript runtime.
function pressHaptic() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}
const PRESS_EASE = Easing.bezier(0.23, 1, 0.32, 1);
// Coordinate the playhead, queued transitions, direct scrub, and accessible stills.
export function useChartFilm(autoPlay: boolean, reduced: boolean, ready: boolean, k: number) {
  const [selected, setSelected] = useState(reduced || autoPlay ? 1 : 0);
  const announceSelected = useCallback((d: number) => setSelected(d), []);
  const [a11y, setA11y] = useState('Day 0, 0');
  const L = useSharedValue(reduced || autoPlay ? 0 : 4.15);
  const mode = useSharedValue(reduced ? 2 : autoPlay ? 0 : 1); // loop, transition, rest
  const destination = useSharedValue(reduced || autoPlay ? 1 : 0);
  const queue = useSharedValue(-1);
  const end = useSharedValue(6.9);
  const elapsed = useSharedValue(0);
  const idleElapsed = useSharedValue(0);
  const touchedAt = useSharedValue(0);
  const paused = useSharedValue(false);
  const started = useSharedValue(false);
  const userRun = useSharedValue(false);
  const presented = useSharedValue(0);
  const sceneOpacity = useSharedValue(1);
  const thumb = useSharedValue(reduced || autoPlay ? 1 : 0);
  const pressed = useSharedValue(-1);
  const pressAmount = useSharedValue(0);
  const scrubU = useSharedValue(0);
  const scrubAlpha = useSharedValue(0);
  const dragging = useSharedValue(false);
  const released = useSharedValue(-100);
  const columnTimes = useSharedValue([-100, -100, -100, -100, -100]);
  const intro = useSharedValue(reduced || autoPlay ? 1 : 0);
  // Update the spoken marker value only after an interaction completes.
  const announce = useCallback((u: number, d: number) => {
    const p = atU(d, u);
    setA11y(`Day ${Math.round((30 * u) / (d ? 1 : 0.957))}, ${Math.round(clamp(p.v) * 100)}`);
  }, []);
  // Start advancing the film only after the readiness fade finishes.
  const startReady = useCallback(
    (finished?: boolean) => {
      'worklet';
      if (finished) started.set(true);
    },
    [started],
  );
  useEffect(() => {
    if (ready) presented.set(withTiming(1, { duration: 300, reduceMotion: ReduceMotion.Never }, startReady));
  }, [ready, presented, startReady]);
  // Start the requested transition while preserving its fixed film interval.
  const begin = useCallback(
    (d: number) => {
      'worklet';
      destination.set(d);
      end.set(d ? 10.45 : 6.9);
      L.set(d ? 7.05 : 3.6);
      mode.set(1);
      userRun.set(true);
      thumb.set(withSpring(d, THUMB_SPRING));
      scheduleOnRN(announceSelected, d);
    },
    [destination, end, L, mode, userRun, thumb, announceSelected],
  );
  // Pause autoplay at the next rest and retain the current transition.
  const touch = useCallback(() => {
    'worklet';
    touchedAt.set(elapsed.get());
    paused.set(true);
    if (mode.get() === 0) {
      const t = L.get();
      if (t >= 3.6 && t < 6.9) {
        destination.set(0);
        end.set(6.9);
        mode.set(1);
      } else if (t >= 7.05 && t < 10.45) {
        destination.set(1);
        end.set(10.45);
        mode.set(1);
      } else {
        destination.set(t >= 6.35 && t < 7.05 ? 0 : 1);
        mode.set(2);
      }
      // The film drove the thumb until now: start the spring from where the film left it.
      thumb.set(film(t, reduced).thumb);
      thumb.set(withSpring(destination.get(), THUMB_SPRING));
      scheduleOnRN(announceSelected, destination.get());
    }
  }, [touchedAt, elapsed, paused, mode, L, destination, end, thumb, announceSelected, reduced]);
  // Swap reduced-motion stills between the two explicitly enabled opacity fades.
  const swapStill = useCallback(
    (finished?: boolean) => {
      'worklet';
      if (!finished) return;
      L.set(destination.get() ? 0 : 6.9);
      thumb.set(destination.get());
      sceneOpacity.set(withTiming(1, { duration: 160, reduceMotion: ReduceMotion.Never }));
      scheduleOnRN(announceSelected, destination.get());
    },
    [L, destination, thumb, sceneOpacity, announceSelected],
  );
  // Queue a reversal or select a still without interrupting a running transition.
  const go = useCallback(
    (d: number) => {
      'worklet';
      touch();
      scrubAlpha.set(0);
      if (d === destination.get()) return;
      if (reduced) {
        destination.set(d);
        sceneOpacity.set(withTiming(0, { duration: 120, reduceMotion: ReduceMotion.Never }, swapStill));
        return;
      }
      if (mode.get() === 1) {
        queue.set(d);
        return;
      }
      begin(d);
    },
    [touch, scrubAlpha, destination, reduced, sceneOpacity, swapStill, mode, queue, begin],
  );
  useFrameCallback((frame) => {
    'worklet';
    if (!started.get()) return;
    const dt = Math.min(T.maxDt, (frame.timeSincePreviousFrame ?? 0) / 1000);
    const now = elapsed.get() + dt;
    elapsed.set(now);
    if (mode.get() === 2) idleElapsed.set(idleElapsed.get() + dt);
    else idleElapsed.set(0);
    if (!reduced && intro.get() < 1) intro.set(Math.min(1, intro.get() + dt / 0.9));
    if (mode.get() === 0) {
      const old = L.get(),
        next = (old + dt) % 12;
      L.set(next);
      if (old < 3.6 && next >= 3.6) scheduleOnRN(announceSelected, 0);
      if (old < 6.95 && next >= 6.95) scheduleOnRN(announceSelected, 1);
    } else if (mode.get() === 1) {
      const old = L.get(),
        next = Math.min(end.get(), old + dt * (queue.get() >= 0 ? 2.5 : 1));
      L.set(next);
      if (userRun.get() && ((old < 6.35 && next >= 6.35) || (old < 9.41 && next >= 9.41)))
        scheduleOnRN(impact);
      if (next >= end.get()) {
        mode.set(2);
        userRun.set(false);
        // Autoplay resumes 3.5 s after the picture came to rest, not after the tap that started it.
        touchedAt.set(now);
        if (queue.get() >= 0) {
          const q = queue.get();
          queue.set(-1);
          if (q !== destination.get()) begin(q);
        }
      }
    }
    if (
      autoPlay &&
      !reduced &&
      paused.get() &&
      mode.get() === 2 &&
      !dragging.get() &&
      now - touchedAt.get() >= 3.5
    ) {
      L.set(destination.get() ? 0 : 6.75);
      mode.set(0);
      paused.set(false);
      scrubAlpha.set(0);
    }
    if (!dragging.get() && scrubAlpha.get() > 0 && now - released.get() > 0.9) {
      scrubAlpha.set(withTiming(0, { duration: 220 }));
      released.set(1e9);
    }
  });
  // Map the finger directly to the curve and emit threshold-crossing haptics.
  const move = useCallback(
    (x: number) => {
      'worklet';
      if (mode.get() !== 2) return;
      const d = destination.get(),
        u = clamp((x / k - 24) / G.pw, 0, d ? 0.968 : 0.957),
        previous = scrubU.get();
      scrubU.set(u);
      const cuts = d ? RISERS : EXTREMA;
      for (let i = 0; i < cuts.length; i++) {
        if ((previous < cuts[i] && u >= cuts[i]) || (previous >= cuts[i] && u < cuts[i])) {
          scheduleOnRN(tick);
          if (d) {
            const old = columnTimes.get();
            const times = [old[0], old[1], old[2], old[3], old[4]];
            times[i] = elapsed.get();
            columnTimes.set(times);
          }
        }
      }
    },
    [mode, destination, k, scrubU, columnTimes, elapsed],
  );
  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .onBegin((e) => {
          touch();
          if (mode.get() !== 2) return;
          dragging.set(true);
          cancelAnimation(scrubU);
          // Initial contact never fires a haptic for the distance from the previous marker.
          scrubU.set(clamp((e.x / k - 24) / G.pw, 0, destination.get() ? 0.968 : 0.957));
          scrubAlpha.set(withTiming(1, { duration: 120 }));
        })
        .onUpdate((e) => {
          move(e.x);
          touchedAt.set(elapsed.get());
        })
        .onFinalize((e) => {
          if (!dragging.get()) return;
          dragging.set(false);
          released.set(elapsed.get());
          touchedAt.set(elapsed.get());
          const d = destination.get(),
            u = scrubU.get(),
            target = d ? nearestTread(u) : u;
          scrubU.set(reduced ? u : withSpring(target, { ...SPRING, velocity: e.velocityX / k / G.pw }));
          scheduleOnRN(announce, reduced ? u : target, d);
        }),
    [
      touch,
      mode,
      dragging,
      scrubU,
      k,
      destination,
      scrubAlpha,
      move,
      touchedAt,
      elapsed,
      released,
      reduced,
      announce,
    ],
  );
  // Animate switch compression and send its press-in haptic.
  const press = useCallback(
    (d: number, on: boolean) => {
      if (on) {
        pressHaptic();
        scheduleOnUI(touch);
        pressed.set(d);
      }
      pressAmount.set(withTiming(on ? 1 : 0, { duration: 120, easing: PRESS_EASE }));
    },
    [touch, pressed, pressAmount],
  );
  const state = useDerivedValue(() => {
    const f = film(L.get(), reduced);
    if (mode.get() === 2 && !reduced) {
      f.life = f.L + idleElapsed.get();
      if (f.d) {
        const phase = (f.life % 4) / 1.6;
        f.glow = 1 + 0.06 * Math.sin((f.life * Math.PI) / 2);
        f.packet = smooth(phase / 0.08) * (1 - smooth((phase - 0.92) / 0.08));
        f.packetStart = clamp(phase - 0.06);
        f.packetEnd = clamp(phase);
      } else f.glow = 1 + 0.1 * Math.sin(f.life * 53) * Math.sin(f.life * 19);
    }
    return f;
  });
  const marker = useDerivedValue(() => {
    const f = state.get(),
      demo = !paused.get() && mode.get() === 0;
    const alpha = demo ? f.markerAlpha : scrubAlpha.get(),
      u = clamp(demo ? f.markerU : scrubU.get(), 0, f.d ? 0.968 : 0.957);
    const p = atU(f.d, u);
    return {
      x: p.x,
      y: p.y,
      u,
      alpha,
      scale: 0.9 + 0.1 * alpha,
      day: Math.round((30 * u) / (f.d ? 1 : 0.957)),
      value: Math.round(clamp(p.v) * 100),
    };
  });
  const thumbPosition = useDerivedValue(() => (mode.get() === 0 ? state.get().thumb : thumb.get()));
  // Expose discrete marker movement to accessibility actions.
  const adjust = useCallback(
    (direction: number) => {
      scheduleOnUI(touch);
      const d = selected,
        u = clamp(scrubU.get() + direction * 0.1, 0, d ? 0.968 : 0.957);
      scrubU.set(u);
      scrubAlpha.set(1);
      released.set(elapsed.get());
      announce(u, d);
      tick();
    },
    [touch, selected, scrubU, scrubAlpha, released, elapsed, announce],
  );
  return {
    state,
    marker,
    presented,
    sceneOpacity,
    thumbPosition,
    pressed,
    pressAmount,
    intro,
    elapsed,
    columnTimes,
    gesture,
    selected,
    a11y,
    press,
    adjust,
    go: (d: number) => scheduleOnUI(go, d),
  };
}
export type ChartMotion = ReturnType<typeof useChartFilm>;
