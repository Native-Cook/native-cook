import { Canvas, Circle, Rect, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cubicBezier,
  Easing,
  ReduceMotion,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { frameAt, inflateAfter, LOOP, MORPH } from './pst-motion';
import { PST_SKSL } from './pst-shader';
import { COLOR, FONT, makeLayout, SHAPES, SPACE } from './pst-theme';
import { type PstCue, usePstAudio } from './use-pst-audio';

/*
 Puffy Sticker — the sticker maker of an invented chat app.

 The screen: Cancel, "New sticker" and Done on top; the sticker large in the middle of a near-black
 page; a Flat / Puffy switch; a tray of four shapes. The effect is a known one (Messages offers a
 "Puffy" sticker effect); the screen, the shapes and every motion are ours.

 Signature: the sticker is a flat die-cut print that inflates into a glossy vinyl balloon in the
 shader, from its own outline. Hold it and it fills while your finger stays down (a tick per quarter);
 let go and it wobbles. The tray's shape is one continuous value, so a tap glides the balloon
 through to the new shape (heart, star, cloud, smiley) and a drag across the tray scrubs it. Drag the
 sticker and it squashes along your direction and springs home with your speed.

 autoPlay: LOOP = 9.6 s (hold to puff, three shape changes, Flat, back to the heart); frame 9.6 s
 equals frame 0. A touch takes over where the loop is; 2 s after the last one the loop eases back.
 Reduced motion: no loop, no wobble or flight; puff and shape change by short fades.
*/

const effect = Skia.RuntimeEffect.Make(PST_SKSL);
if (!effect) throw new Error('Puffy Sticker: the sticker shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const PRESS = { transitionProperty: 'transform', transitionDuration: 120, transitionTimingFunction: cubicBezier(0.23, 1, 0.32, 1) } as const;
const NEVER = ReduceMotion.Never;
const LAST = SHAPES.length - 1;

function buzz(kind: 'select' | 'light' | 'success') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'select') Haptics.selectionAsync().catch(() => {});
  else if (kind === 'success') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function PuffySticker({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Inter-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
  });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);
  const audio = usePstAudio(!reduced);
  const cue = useCallback((c: PstCue) => audio.play(c), [audio]);
  const hush = useCallback((c: PstCue) => audio.stop(c), [audio]);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);

  const puff = useSharedValue(0);
  const shape = useSharedValue(0);
  const squish = useSharedValue(0);
  const squishAxis = useSharedValue(0);
  const posX = useSharedValue(0);
  const posY = useSharedValue(0);
  const touch = useSharedValue(0); // the scripted finger (autoPlay)
  const tapAt = useSharedValue(-1);
  const tapPress = useSharedValue(0);
  const flatPress = useSharedValue(0);
  // finger state
  const holding = useSharedValue(0);
  const holdStart = useSharedValue(0);
  const holdFrom = useSharedValue(0);
  const dragging = useSharedValue(0);
  const quarter = useSharedValue(0);
  const [selected, setSelected] = useState(0);
  const [puffy, setPuffy] = useState(false);

  const onQuarter = useCallback(() => {
    cue('tick');
    buzz('select');
  }, [cue]);

  /* ------------------------------------------------------------ clock */
  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      puff.set(s.puff);
      shape.set(s.shape);
      squish.set(s.squish);
      squishAxis.set(0);
      touch.set(s.touch);
      tapAt.set(s.tapAt);
      tapPress.set(s.tapPress);
      flatPress.set(s.flatPress);
      return;
    }
    if (holding.get() === 1 && dragging.get() === 0) {
      // the balloon fills while the finger stays down, from wherever it was
      const held = clock.get() - holdStart.get() - 0.12;
      if (held > 0) {
        const from = holdFrom.get();
        const p = from + (1 - from) * inflateAfter(held);
        puff.set(p);
        const q = Math.floor(p * 4 + 0.001);
        if (q > quarter.get()) {
          quarter.set(q);
          scheduleOnRN(onQuarter);
        }
      }
    } else if (auto && resumeAsked.get() === 0 && holding.get() === 0 && clock.get() - lastTouch.get() > 2) {
      resumeAsked.set(1);
      puff.set(withTiming(0, { duration: 700, easing: EASE_IN_OUT }));
      shape.set(withTiming(0, { duration: 700 + 260 * shape.get(), easing: EASE_IN_OUT }));
      posX.set(withSpring(0, { duration: 500, dampingRatio: 0.8 }));
      posY.set(withSpring(0, { duration: 500, dampingRatio: 0.8 }));
    } else if (resumeAsked.get() === 1 && puff.get() < 0.002 && shape.get() < 0.002 && clock.get() - lastTouch.get() > 3.4) {
      resumeAsked.set(0);
      autoStart.set(clock.get());
      mode.set(0);
    }
  }, active);

  useEffect(() => {
    mode.set(auto ? 0 : 1);
    autoStart.set(clock.get());
  }, [auto, mode, autoStart, clock]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__puffy = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      state: () => JSON.stringify({ mode: mode.get(), puff: +puff.get().toFixed(3), shape: +shape.get().toFixed(3), squish: +squish.get().toFixed(3) }),
    };
  }, [pin, mode, puff, shape, squish]);

  // the tray and the switch follow the sticker (React state only on change)
  useAnimatedReaction(
    () => Math.round(shape.get()),
    (now, before) => {
      if (now !== before) scheduleOnRN(setSelected, now);
    },
  );
  useAnimatedReaction(
    () => puff.get() > 0.5,
    (now, before) => {
      if (now !== before) scheduleOnRN(setPuffy, now);
    },
  );

  /* ------------------------------------------------------------ actions */

  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() === 0) {
      mode.set(1);
      touch.set(withTiming(0, { duration: 150 }));
      tapPress.set(0);
      flatPress.set(0);
      // the loop's wobble settles from where it is
      squish.set(withSpring(0, { duration: 500, dampingRatio: 0.35 }));
    }
  };
  // the same hand-over from a press handler on the JS thread (shared values accept writes there)
  const takeOverJS = () => takeOver();

  const goToShape = useCallback((i: number) => {
    takeOverJS();
    const from = shape.get();
    if (Math.abs(from - i) < 0.01) return;
    cue('morph');
    buzz('light');
    if (reduced) {
      shape.set(withTiming(i, { duration: 180, reduceMotion: NEVER }));
      return;
    }
    shape.set(withTiming(i, { duration: MORPH * 1000 * (0.7 + 0.3 * Math.abs(i - from)), easing: EASE_IN_OUT }, (done) => {
      if (done) squish.set(withSequence(withTiming(0.05, { duration: 90 }), withSpring(0, { duration: 600, dampingRatio: 0.3 })));
    }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reduced, cue]);

  const setPuff = useCallback((on: boolean) => {
    takeOverJS();
    buzz('light');
    if (on) cue('inflate');
    if (reduced) {
      puff.set(withTiming(on ? 1 : 0, { duration: 200, reduceMotion: NEVER }));
      return;
    }
    puff.set(withSpring(on ? 1 : 0, { duration: 700, dampingRatio: on ? 0.55 : 0.8 }));
    squish.set(withSequence(withTiming(on ? -0.05 : 0.07, { duration: 140 }), withSpring(0, { duration: 700, dampingRatio: 0.3 })));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reduced, cue]);

  const done = useCallback(() => {
    takeOverJS();
    buzz('success');
    cue('land');
    if (reduced) return;
    // a happy hop: squat, stretch up, land with a squish
    squishAxis.set(0);
    squish.set(withSequence(
      withTiming(0.08, { duration: 110 }),
      withTiming(-0.1, { duration: 140 }),
      withTiming(0.06, { duration: 160 }),
      withSpring(0, { duration: 600, dampingRatio: 0.35 }),
    ));
    // up on a strong ease-out, then caught by a bouncy spring (no ease-in on the way down)
    posY.set(withSequence(withTiming(0, { duration: 110 }), withTiming(-38, { duration: 220, easing: Easing.bezier(0.23, 1, 0.32, 1) }), withSpring(0, { duration: 520, dampingRatio: 0.45 })));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reduced, cue]);

  // Cancel starts over: a flat heart
  const cancel = useCallback(() => {
    takeOverJS();
    puff.set(withTiming(0, { duration: reduced ? 180 : 420, easing: EASE_IN_OUT, reduceMotion: NEVER }));
    shape.set(withTiming(0, { duration: reduced ? 180 : 420 + 200 * shape.get(), easing: EASE_IN_OUT, reduceMotion: NEVER }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reduced]);

  /* ------------------------------------------------------------ gestures */
  const onHoldStart = useCallback(() => {
    if (puff.get() < 0.98) cue('inflate');
  }, [cue, puff]);
  const onHoldEnd = useCallback(() => {
    hush('inflate');
  }, [hush]);

  const sticker = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      takeOver();
      holding.set(1);
      dragging.set(0);
      holdStart.set(clock.get());
      holdFrom.set(puff.get());
      quarter.set(Math.floor(puff.get() * 4 + 0.001));
      if (!reduced) squish.set(withTiming(-0.03, { duration: 120 }));
      scheduleOnRN(onHoldStart);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      if (dragging.get() === 0 && Math.hypot(e.translationX, e.translationY) > 10) {
        dragging.set(1);
        scheduleOnRN(onHoldEnd);
      }
      if (dragging.get() === 1) {
        posX.set(e.translationX);
        posY.set(e.translationY);
        // stretch along the motion, a little, like something soft being pulled
        if (!reduced) {
          const v = Math.hypot(e.velocityX, e.velocityY);
          squish.set(-Math.min(0.14, v / 9000));
          squishAxis.set(Math.atan2(e.velocityY, e.velocityX));
        }
      }
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      holding.set(0);
      scheduleOnRN(onHoldEnd);
      const v = Math.hypot(e.velocityX, e.velocityY);
      if (dragging.get() === 1) {
        posX.set(withSpring(0, { velocity: e.velocityX, duration: 560, dampingRatio: 0.6 }));
        posY.set(withSpring(0, { velocity: e.velocityY, duration: 560, dampingRatio: 0.6 }));
        squish.set(withSequence(withTiming(Math.min(0.1, v / 12000 + 0.03), { duration: 120 }), withSpring(0, { duration: 700, dampingRatio: 0.3 })));
      } else if (!reduced) {
        // let go of a held balloon: it wobbles; a quick tap is a poke
        const held = clock.get() - holdStart.get();
        squish.set(withSequence(withTiming(held < 0.15 ? 0.09 : 0.07, { duration: 100 }), withSpring(0, { duration: 800, dampingRatio: 0.25 })));
      }
      dragging.set(0);
    });

  const trayLeft = L.w / 2 - (SHAPES.length * L.thumb + (SHAPES.length - 1) * SPACE.l) / 2;
  const trayStep = L.thumb + SPACE.l;
  const trayScrub = Gesture.Pan()
    .activeOffsetX([-6, 6])
    .onBegin(() => takeOver())
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      const x = (e.x - L.thumb / 2) / trayStep;
      shape.set(Math.max(0, Math.min(LAST, x)));
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      const target = Math.round(shape.get());
      shape.set(withSpring(target, { duration: 420, dampingRatio: 0.7 }));
    });

  /* ------------------------------------------------------------ drawing */
  const rect = useMemo(() => Skia.XYWHRect(0, 0, L.w, L.h), [L]);
  const uniforms = useDerivedValue(() => {
    const s = Math.max(0, Math.min(LAST, shape.get()));
    const a = Math.min(Math.floor(s), LAST - 1);
    const k = squish.get();
    return {
      uC: [L.stageX + posX.get(), L.stageY + posY.get()],
      uS: L.sticker,
      uPuff: puff.get(),
      uA: a,
      uB: a + 1,
      uW: s - a,
      uScale: [1 + k, 1 / (1 + k)],
      uRot: squishAxis.get(),
      uLight: [-posX.get() / 900, posY.get() / 900],
      uPixelRatio: pixelRatio,
    };
  });
  const thumbs = useMemo(
    () =>
      SHAPES.map((_, i) => {
        const cx = trayLeft + i * trayStep + L.thumb / 2;
        const cy = L.trayTop + L.thumb / 2;
        return {
          rect: Skia.XYWHRect(cx - L.thumb / 2, cy - L.thumb / 2, L.thumb, L.thumb),
          uniforms: { uC: [cx, cy], uS: L.thumb * 0.34, uPuff: 0.85, uA: i, uB: i, uW: 0, uScale: [1, 1], uRot: 0, uLight: [0, 0], uPixelRatio: pixelRatio },
        };
      }),
    [L, trayLeft, trayStep],
  );
  const fingerR = useDerivedValue(() => 30 * (0.8 + 0.2 * touch.get()));
  const fingerOpacity = useDerivedValue(() => touch.get() * 0.75);
  const fingerFill = useDerivedValue(() => touch.get() * 0.14);

  const segStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: withTiming(puffy ? 96 : 0, { duration: 200, easing: Easing.bezier(0.23, 1, 0.32, 1) }) }, { scale: 1 - 0.04 * flatPress.get() }],
  }));

  const ready = fontsLoaded && size !== null;
  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {ready && (
        <>
          <View style={[styles.bar, { top: L.barTop }]}>
            <Pressable hitSlop={12} accessibilityRole="button" onPress={cancel}>
              <Text style={styles.cancel}>Cancel</Text>
            </Pressable>
            <Text style={styles.title} accessibilityRole="header">New sticker</Text>
            <Pressable style={styles.done} accessibilityRole="button" onPress={done}>
              <Text style={styles.doneText}>Done</Text>
            </Pressable>
          </View>

          <GestureDetector gesture={sticker}>
            <View
              style={{ position: 'absolute', left: L.stageX - L.sticker * 1.1, top: L.stageY - L.sticker * 1.1, width: L.sticker * 2.2, height: L.sticker * 2.2 }}
              accessible
              accessibilityRole="adjustable"
              accessibilityLabel={`${SHAPES[selected].label} sticker, ${puffy ? 'puffy' : 'flat'}`}
              accessibilityHint="Hold to puff it up, drag to move it."
              accessibilityActions={[{ name: 'activate' }]}
              onAccessibilityAction={() => setPuff(!puffy)}
            />
          </GestureDetector>

          <Text style={[styles.hint, { top: L.hintTop }]}>{puffy ? 'Drag it, or pick another shape' : 'Hold the sticker to puff it up'}</Text>

          <View style={[styles.switch, { top: L.switchTop, left: L.w / 2 - 100 }]} accessibilityRole="radiogroup">
            <Animated.View style={[styles.segThumb, segStyle]} />
            {(['Flat', 'Puffy'] as const).map((label, i) => {
              const on = (i === 1) === puffy;
              return (
                <Pressable
                  key={label}
                  style={styles.seg}
                  onPress={() => setPuff(i === 1)}
                  accessibilityRole="radio"
                  accessibilityState={{ selected: on }}
                >
                  <Text style={[styles.segText, on && styles.segTextOn]}>{label}</Text>
                </Pressable>
              );
            })}
          </View>

          <GestureDetector gesture={trayScrub}>
            <View style={{ position: 'absolute', left: trayLeft, top: L.trayTop, width: SHAPES.length * trayStep - SPACE.l, height: L.thumb }}>
              {SHAPES.map((s, i) => (
                <Thumb key={s.id} index={i} selected={i === selected} left={i * trayStep} size={L.thumb} tapAt={tapAt} tapPress={tapPress} label={s.label} onPress={() => goToShape(i)} />
              ))}
            </View>
          </GestureDetector>

          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            {thumbs.map((t, i) => (
              <Rect key={i} rect={t.rect}>
                <Shader source={effect!} uniforms={t.uniforms} />
              </Rect>
            ))}
            <Rect rect={rect}>
              <Shader source={effect!} uniforms={uniforms} />
            </Rect>
            {/* the scripted finger in autoPlay: a soft ring where the thumb rests */}
            <Circle cx={L.stageX} cy={L.stageY + L.sticker * 0.15} r={fingerR} color="#FFFFFF" opacity={fingerFill} />
            <Circle cx={L.stageX} cy={L.stageY + L.sticker * 0.15} r={fingerR} color="#FFFFFF" opacity={fingerOpacity} style="stroke" strokeWidth={2} />
          </Canvas>
        </>
      )}
    </View>
  );
}

type SV = { get: () => number };

function Thumb({ index, selected, left, size, tapAt, tapPress, label, onPress }: {
  index: number; selected: boolean; left: number; size: number; tapAt: SV; tapPress: SV; label: string; onPress: () => void;
}) {
  const [down, setDown] = useState(false);
  const style = useAnimatedStyle(() => ({
    transform: [{ scale: down ? 0.96 : 1 - 0.04 * (tapAt.get() === index ? tapPress.get() : 0) }],
  }));
  return (
    <Animated.View style={[styles.thumb, PRESS, { left, width: size, height: size }, selected && styles.thumbOn, style]}>
      <Pressable
        style={StyleSheet.absoluteFill}
        onPressIn={() => setDown(true)}
        onPressOut={() => setDown(false)}
        onPress={onPress}
        accessibilityRole="button"
        accessibilityLabel={label}
        accessibilityState={{ selected }}
      />
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.page, overflow: 'hidden' },
  bar: { position: 'absolute', left: SPACE.l, right: SPACE.l, height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  cancel: { fontFamily: FONT.regular, fontSize: 16, color: COLOR.ink },
  title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.semibold, fontSize: 17, color: COLOR.ink, zIndex: -1 },
  done: { height: 32, paddingHorizontal: 16, borderRadius: 16, backgroundColor: COLOR.accent, alignItems: 'center', justifyContent: 'center' },
  doneText: { fontFamily: FONT.semibold, fontSize: 15, color: COLOR.accentInk },
  hint: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.medium, fontSize: 13, color: COLOR.mute },
  switch: { position: 'absolute', width: 200, height: 40, flexDirection: 'row', padding: 4, borderRadius: 20, backgroundColor: COLOR.surface },
  segThumb: { position: 'absolute', left: 4, top: 4, width: 96, height: 32, borderRadius: 16, backgroundColor: COLOR.surfaceOn },
  seg: { width: 96, height: 32, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  segText: { fontFamily: FONT.medium, fontSize: 14, color: COLOR.mute },
  segTextOn: { color: COLOR.ink },
  thumb: { position: 'absolute', top: 0, borderRadius: 18, backgroundColor: COLOR.surface },
  thumbOn: { borderWidth: 2, borderColor: COLOR.ink },
});
