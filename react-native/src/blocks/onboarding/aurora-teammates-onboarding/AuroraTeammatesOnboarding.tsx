// Aurora Teammates Onboarding — a three-page onboarding for an invented AI workspace, after Crevio
// Agency's still on Pinterest/Dribbble (layout, type and colour copied from it; the motion is ours).
//
// Layers, bottom to top:
//   1. AtoStage    tgfx (react-native-tgfx) draws the whole screen in one canvas: the aurora shader,
//                  the glass objects, the sheet, the title, the subtitles, the dots and the Next pill,
//                  every moving number a value slot written from the UI thread.
//   2. AtoMotes    WebGPU + TypeGPU: light dust rising out of the notch (masked to the sheet's edge),
//                  a burst when the light flares.
//   3. Touch       an invisible Pressable over the pill and VoiceOver's pager (React Native views).
// One page position drives them all (ato-motion.ts). Reanimated owns time and touch.
//
// autoPlay: LOOP = 9 s. Page 1 rests 1.5 s, changes to page 2 in 1.5 s, rests, changes to 3, rests,
// changes back to 1; the frame at 9 s is the frame at 0. The current dot is the timer: dim, filling
// while the page rests, its fill flowing on with the page change. A resting finger freezes it (and
// lifts the glass under it); lifting the finger lets it run on.
// Drag sideways to scrub a page change with the finger (the notch and its light follow the finger
// and spring back; let go and it plays out at its own tempo); tap Next to play the next one. Two
// seconds after a drag or a Next the loop resumes from the page on screen. Next on page 3 plays the
// finale (the tiles' light streams into the button, "You're all set", `onComplete`), then the
// onboarding starts over. Tilting the phone moves the glass over the aurora (on a simulator, a slow
// drift stands in for the gyroscope). Reduce Motion: no loop, no travel, no tilt, no flare, no dust;
// Next cross-fades the pages and the finale only changes the button's words.

import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  cancelAnimation,
  ReduceMotion,
  type SharedValue,
  Easing,
  SensorType,
  useAnimatedReaction,
  useAnimatedSensor,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import { AtoNextHit } from './AtoControls';
import { AtoStage } from './AtoStage';
import { PAGE_COPY } from './ato-content';
import {
  clockAt,
  computeScene,
  dragToPos,
  GL,
  HOLD,
  type Input,
  LOOP,
  PLAY_TRANS,
  posAt,
  RESUME,
  SCENE_SIZE,
  SEG,
  wrapPos,
} from './ato-motion';
import { C, makeLayout, PAGES } from './ato-theme';
import { AtoMotes } from './gpu/AtoMotes';

type Props = {
  autoPlay?: boolean;
  /** Called when the finale (Next on the last page) has played: the place to leave onboarding. */
  onComplete?: () => void;
};

/** The finale's length (s). */
const FINALE = 2.1;
/** A finger that only rested lets the timer run on this soon after it lifts (s). */
const HOLD_RESUME = 0.25;

type Gyro = { init: number; pitch: number; roll: number; x: number; y: number };

export function AuroraTeammatesOnboarding({ autoPlay = false, onComplete }: Props) {
  const reduced = useReducedMotionPreference();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');

  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const L = useMemo(() => makeLayout(size?.width ?? 402, size?.height ?? 874), [size]);

  // time and position: pos runs 0..3 round a circle, clock is the loop's own time
  const pos = useSharedValue(0);
  const clock = useSharedValue(0);
  const amb = useSharedValue(0);
  const auto = useSharedValue(autoPlay && !reduced);
  const held = useSharedValue(false);
  const playing = useSharedValue(false);
  const resumeAt = useSharedValue(0);
  const now = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  const resumeFromPos = useSharedValue(false);
  // the finger: where it is, how fast it drags, and how much it is down (eased)
  const fx = useSharedValue(0);
  const fy = useSharedValue(0);
  const fvx = useSharedValue(0);
  const press = useSharedValue(0);
  const fdown = useSharedValue(0);
  /** dragging 0..1, springing back past 0 on release (the notch swings home) */
  const fdrag = useSharedValue(0);
  // the autoplay timer's visibility, and whether a finger took over (drag or Next) since it last ran
  const autoVis = useSharedValue(0);
  const took = useSharedValue(false);
  /** finale progress 0..1 (Next on the last page) */
  const fin = useSharedValue(0);
  // the phone's attitude, relative to a rest pose that slowly follows the hand
  const rotation = useAnimatedSensor(SensorType.ROTATION, { interval: 'auto' });
  const gyro = useSharedValue<Gyro>({ init: 0, pitch: 0, roll: 0, x: 0, y: 0 });
  const hasGyro = rotation.isAvailable;
  /** development only: values that override the inputs, for pinned frames */
  const devInput = useSharedValue<Partial<Input> | null>(null);
  /** development only: freezes the ambient clock so a pinned frame can be compared with the pin */
  const frozen = useSharedValue(false);

  useEffect(() => {
    auto.set(autoPlay && !reduced);
  }, [auto, autoPlay, reduced]);

  const frame = useFrameCallback((f) => {
    const dt = Math.min(f.timeSincePreviousFrame ?? 16, 50) / 1000;
    now.set(f.timestamp);
    if (reduced || frozen.get()) return;
    amb.set((amb.get() + dt) % LOOP);
    if (hasGyro) {
      const r = rotation.sensor.get();
      const g = gyro.get();
      if (g.init === 0 && (r.pitch !== 0 || r.roll !== 0)) {
        g.init = 1;
        g.pitch = r.pitch;
        g.roll = r.roll;
      }
      const kb = Math.min(1, dt * 0.5); // the rest pose drifts after the hand
      g.pitch += (r.pitch - g.pitch) * kb;
      g.roll += (r.roll - g.roll) * kb;
      const ks = 1 - Math.exp(-dt * 9);
      g.x += (Math.max(-1, Math.min(1, (r.roll - g.roll) / 0.35)) - g.x) * ks;
      g.y += (Math.max(-1, Math.min(1, (r.pitch - g.pitch) / 0.35)) - g.y) * ks;
      gyro.set({ ...g });
    }
    const ka = 1 - Math.exp(-dt / 0.22);
    autoVis.set(autoVis.get() + ((auto.get() && !took.get() ? 1 : 0) - autoVis.get()) * ka);
    if (!auto.get() || held.get() || playing.get() || f.timestamp < resumeAt.get()) return;
    took.set(false);
    if (resumeFromPos.get()) {
      clock.set(clockAt(pos.get()));
      resumeFromPos.set(false);
    }
    clock.set((clock.get() + dt) % LOOP);
    pos.set(posAt(clock.get()));
  }, false);

  useEffect(() => {
    frame.setActive(active && size != null);
  }, [active, frame, size]);

  // development hook (debugger): __ato.pin(pos, ambient s) holds a frame, __ato.play() lets go
  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as { __ato?: unknown }).__ato = {
      // extra: input overrides for the frame (tiltX/Y, drag, x, vx, holdQ, autoVis, fin, drift)
      pin: (p: number, a = 0, extra: Partial<Input> | null = null) =>
        scheduleOnUI(() => {
          'worklet';
          cancelAnimation(pos);
          frozen.set(true);
          held.set(true);
          pos.set(p);
          amb.set(a);
          devInput.set(extra);
        }),
      // the autoplay frame at loop time t (s), frozen: what a port's debug hook must show at the same t
      at: (t: number) =>
        scheduleOnUI(() => {
          'worklet';
          cancelAnimation(pos);
          frozen.set(true);
          held.set(true);
          const tt = ((t % LOOP) + LOOP) % LOOP;
          clock.set(tt);
          pos.set(posAt(tt));
          amb.set(tt);
          devInput.set({ autoVis: 1 });
        }),
      press: (v: number) =>
        scheduleOnUI(() => {
          'worklet';
          press.set(v);
        }),
      state: () => JSON.stringify({ down: fdown.get(), x: fx.get(), y: fy.get(), pos: pos.get(), held: held.get() }),
      play: () =>
        scheduleOnUI(() => {
          'worklet';
          frozen.set(false);
          held.set(false);
          devInput.set(null);
          resumeAt.set(0);
          resumeFromPos.set(true);
        }),
    };
  }, [amb, clock, devInput, fdown, frozen, fx, fy, held, pos, press, resumeAt, resumeFromPos]);

  const scene = useDerivedValue(() => {
    const out: number[] = new Array(SCENE_SIZE).fill(0);
    const tau = clock.get() % SEG;
    const g = gyro.get();
    const input: Input = {
      vx: fvx.get(),
      x: fx.get(),
      y: fy.get(),
      down: fdown.get(),
      drag: fdrag.get(),
      tiltX: g.x,
      tiltY: g.y,
      drift: hasGyro ? 0 : 1,
      holdQ: tau < HOLD ? tau / HOLD : 1,
      autoVis: autoVis.get(),
      fin: fin.get(),
    };
    const dev = devInput.get();
    computeScene(pos.get(), amb.get(), L, reduced, dev ? { ...input, ...dev } : input, out);
    return out;
  });

  // the haptic tick when a page change lands
  const tick = useCallback(() => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.selectionAsync().catch(() => {});
  }, []);

  // a soft tick when the finger comes to rest on a card, tile or icon
  const touchTick = useCallback(() => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  }, []);
  useAnimatedReaction(
    () => scene.get()[GL.touched],
    (now, before) => {
      if (now >= 0 && now !== before) scheduleOnRN(touchTick);
    },
  );

  // the finale lands: the light reaches the button and its words turn
  const celebrate = useCallback(() => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  }, []);
  useAnimatedReaction(
    () => scene.get()[GL.finCheck] > 0.5,
    (now, before) => {
      if (now && before === false) scheduleOnRN(celebrate);
    },
  );
  const complete = useCallback(() => onComplete?.(), [onComplete]);

  /** Play the page position to `target` at the designed tempo, then hand back to the loop later. */
  const playTo = (target: number, speed: number) => {
    'worklet';
    const dist = Math.abs(target - pos.get());
    playing.set(true);
    pos.set(
      withTiming(
        target,
        reduced
          ? {
              duration: 450,
              easing: Easing.inOut(Easing.quad),
              reduceMotion: ReduceMotion.Never,
            }
          : {
              duration: Math.max(120, dist * PLAY_TRANS * 1000 * speed),
              easing: Easing.linear,
            },
        (done) => {
          if (!done) return;
          pos.set(wrapPos(Math.round(pos.get())));
          fin.set(0);
          playing.set(false);
          resumeAt.set(now.get() + RESUME * 1000);
          resumeFromPos.set(true);
          scheduleOnRN(tick);
        },
      ),
    );
  };

  /** Next on the last page: the tiles' light streams into the button, then the onboarding starts over. */
  const finale = () => {
    'worklet';
    playing.set(true);
    fin.set(0);
    fin.set(
      withTiming(
        1,
        {
          duration: (reduced ? 0.9 : FINALE) * 1000,
          easing: Easing.linear,
          reduceMotion: ReduceMotion.Never,
        },
        (done) => {
          if (!done) return;
          scheduleOnRN(complete);
          playTo(Math.round(pos.get()) + 1, 1);
        },
      ),
    );
  };

  const next = () => {
    scheduleOnUI(() => {
      'worklet';
      took.set(true);
      if (fin.get() > 0) return; // the finale is playing
      if (!playing.get() && Math.abs(wrapPos(pos.get()) - (PAGES - 1)) < 1e-3) {
        cancelAnimation(pos);
        finale();
        return;
      }
      const base = playing.get() ? Math.round(pos.get() + 0.5) : Math.floor(pos.get() + 1e-4);
      cancelAnimation(pos);
      playTo(base + 1, playing.get() ? 0.7 : 1);
    });
  };

  const pan = Gesture.Pan()
    .activeOffsetX([-12, 12])
    .failOffsetY([-24, 24])
    .onBegin((e) => {
      held.set(true);
      fx.set(e.x);
      fy.set(e.y);
      fdown.set(withTiming(1, { duration: 220, easing: Easing.out(Easing.cubic) }));
    })
    .onStart(() => {
      cancelAnimation(pos);
      if (fin.get() > 0) {
        cancelAnimation(fin);
        fin.set(withTiming(0, { duration: 220 }));
      }
      playing.set(false);
      took.set(true);
      dragFrom.set(pos.get());
      fdrag.set(withTiming(1, { duration: 200, easing: Easing.out(Easing.cubic) }));
    })
    .onUpdate((e) => {
      fx.set(e.x);
      fy.set(e.y);
      fvx.set(e.velocityX);
      const p = dragFrom.get() + dragToPos(e.translationX, L.W);
      const lo = Math.floor(dragFrom.get() + 1e-4) - 1;
      // one page either way; past that the screen resists like a rubber band
      const over = p > lo + 2 ? p - lo - 2 : p < lo ? p - lo : 0;
      const give = (Math.sign(over) * (1 - Math.exp(-Math.abs(over) * 3))) / 6;
      pos.set(Math.min(lo + 2, Math.max(lo, p)) + give);
    })
    .onEnd((e) => {
      const projected = pos.get() + dragToPos(e.velocityX * 0.2, L.W);
      const start = Math.floor(dragFrom.get() + 1e-4);
      const target = Math.min(start + 1, Math.max(start - 1, Math.round(projected)));
      playTo(target, 0.85);
    })
    .onFinalize((_e, success) => {
      held.set(false);
      // a touch that never became a drag: a page change it caught half-way plays out from there
      const p = pos.get();
      if (!success && !playing.get() && Math.abs(p - Math.round(p)) > 1e-3) playTo(Math.ceil(p), 1);
      fdown.set(withTiming(0, { duration: 520, easing: Easing.out(Easing.cubic) }));
      fvx.set(withSpring(0, { duration: 700, dampingRatio: 0.55 }));
      // the notch swings back past the centre and settles
      if (fdrag.get() !== 0) fdrag.set(withSpring(0, { duration: 900, dampingRatio: 0.38 }));
      if (success || playing.get()) {
        resumeAt.set(now.get() + RESUME * 1000);
        resumeFromPos.set(true);
      } else {
        // a finger that only rested: the timer runs on from where it froze
        resumeAt.set(now.get() + HOLD_RESUME * 1000);
      }
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  const step = (dir: number) => {
    scheduleOnUI(() => {
      'worklet';
      cancelAnimation(pos);
      playTo(Math.round(pos.get()) + dir, 1);
    });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {size != null && (
        <GestureDetector gesture={pan}>
          <View style={StyleSheet.absoluteFill} accessible={false}>
            <AtoStage scene={scene} L={L} reduced={reduced} press={press} />
            <AtoMotes scene={scene} L={L} disabled={reduced || !active} />
          </View>
        </GestureDetector>
      )}
      {size != null && <AtoPager pos={pos} onStep={step} />}
      {size != null && <AtoNextHit scene={scene} press={press} L={L} onNext={next} />}
    </View>
  );
}

/** VoiceOver's view of the pager: the page's words, "Page n of 3", swipe up or down to change page.
 * It holds the page number itself, so a page change re-renders only this. */
function AtoPager({ pos, onStep }: { pos: SharedValue<number>; onStep: (dir: number) => void }) {
  const [page, setPage] = useState(0);
  useAnimatedReaction(
    () => Math.round(wrapPos(pos.get())) % PAGES,
    (p, prev) => {
      if (prev != null && p !== prev) scheduleOnRN(setPage, p);
    },
  );
  return (
    <View
      pointerEvents="none"
      style={StyleSheet.absoluteFill}
      accessible
      accessibilityRole="adjustable"
      accessibilityLabel={PAGE_COPY[page].a11y}
      accessibilityValue={{ text: `Page ${page + 1} of ${PAGES}` }}
      accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
      onAccessibilityAction={(ev) => onStep(ev.nativeEvent.actionName === 'increment' ? 1 : -1)}
    />
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.navy, overflow: 'hidden' },
});
