import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { frameAt, LOOP, STEPS, yawOf } from './csn-motion';
import { CSN_SKSL } from './csn-shader';

/*
 Chrome Step Numerals — the three steps of a savings app's onboarding, numbered by one chrome balloon.

 The screen: Back and Skip, a large chrome balloon numeral on a warm grey page, the step's title and
 line, three dots and a button (Next, then Get started).

 Signature: the number is one object. Swipe the pages or tap Next and the balloon turns once on itself
 while its tubes melt from 1 into 2 into 3 — the drag scrubs the turn and the melt 1:1, holding half-way
 if the finger stops — reflecting a warm studio as it turns. Let go and it settles on the nearest step
 at the finger's speed; a tick at each step.

 autoPlay: LOOP = 9 s (Next: 1 → 2; drag to 3, holding half-way; swipe back to 1); frame 9 s equals
 frame 0. A touch takes over; 2 s after the last one the pages go back to the first and the loop
 starts again.
 Reduced motion: no loop; a step change is a short cross-melt without the turn or the bob.
*/

const effect = Skia.RuntimeEffect.Make(CSN_SKSL);
if (!effect) throw new Error('Chrome Step Numerals: the shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const NEVER = ReduceMotion.Never;
const C = { page: '#EEEDEA', ink: '#1E1C1A', mute: '#8A847C', accent: '#C08A57', accentInk: '#FFFFFF', dot: '#D4CFC8' };
const FONT = { regular: 'CsnInter-Regular', medium: 'CsnInter-Medium', semibold: 'CsnInter-SemiBold' };
const PAGES = [
  { title: 'Link your bank', line: 'Connect an account in a minute. We only ever read balances.' },
  { title: 'Pick a goal', line: 'A trip, a bike, a rainy-day fund — name it and set a date.' },
  { title: 'Watch it grow', line: 'Round-ups and payday transfers fill it without thinking.' },
];
const LAST = STEPS - 1;

function tick() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.selectionAsync().catch(() => {});
}

type Props = { autoPlay?: boolean };

export function ChromeStepNumerals({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
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
  const [dims, setDims] = useState({ w: 402, h: 874 });
  const L = useMemo(() => {
    const S = Math.round(dims.w * 0.3);
    const cy = insets.top + Math.round((dims.h - insets.top - insets.bottom) * 0.33);
    const titleTop = cy + S * 1.45;
    const buttonTop = dims.h - Math.max(insets.bottom, 16) - 8 - 52;
    return { w: dims.w, h: dims.h, top: insets.top, S, cy, floorY: cy + S * 1.15, titleTop, dotsTop: buttonTop - 40, buttonTop };
  }, [dims, insets.top, insets.bottom]);
  const [page, setPage] = useState(0);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const stay = useSharedValue(0);
  const r = useSharedValue(0);
  const bob = useSharedValue(0);
  const press = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerAt = useSharedValue(0);
  const dragging = useSharedValue(0);
  const moving = useSharedValue(0);
  const r0 = useSharedValue(0);

  /* ------------------------------------------------------------ moving (above the clock) */
  function goTo(k: number, velocity: number) {
    'worklet';
    moving.set(1);
    const done = (fin?: boolean) => {
      'worklet';
      if (fin) moving.set(0);
    };
    if (reduced) r.set(withTiming(k, { duration: 300, easing: EASE_IN_OUT, reduceMotion: NEVER }, done));
    else if (velocity === 0) r.set(withTiming(k, { duration: 1100 + 300 * Math.abs(k - r.get()), easing: EASE_IN_OUT }, done));
    else r.set(withSpring(k, { duration: 900, dampingRatio: 0.9, velocity }, done));
  }

  /* ------------------------------------------------------------ clock */
  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      r.set(s.r);
      bob.set(s.bob);
      press.set(s.press);
      finger.set(s.finger);
      fingerX.set(s.fingerX);
      fingerAt.set(s.fingerAt);
      return;
    }
    bob.set(reduced ? 0 : bob.get() * (1 - Math.min(1, dt * 3)));
    if (dragging.get() === 1 || moving.get() === 1) lastTouch.set(clock.get());
    if (!auto || stay.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      if (r.get() > 0.001) goTo(0, 0);
    } else if (moving.get() === 0 && r.get() < 0.001) {
      resumeAsked.set(0);
      r.set(0);
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
    (globalThis as Record<string, unknown>).__csn = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      stay: (v: boolean) => {
        stay.set(v ? 1 : 0);
        if (v) mode.set(1);
      },
      state: () => JSON.stringify({ mode: mode.get(), r: +r.get().toFixed(3) }),
    };
  }, [pin, stay, mode, r]);

  useAnimatedReaction(
    () => Math.round(Math.max(0, Math.min(LAST, r.get()))),
    (now, before) => {
      if (before === null || now === before) return;
      scheduleOnRN(setPage, now);
      if (mode.get() === 1) scheduleOnRN(tick);
    },
  );

  function takeOver() {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    press.set(0);
  }

  const screenTouch = Gesture.Manual().onTouchesDown(() => {
    takeOver();
  });
  const pager = Gesture.Pan()
    .activeOffsetX([-10, 10])
    .onBegin(() => {
      takeOver();
    })
    .onStart(() => {
      dragging.set(1);
      moving.set(0);
      r.set(r.get());
      r0.set(r.get());
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      let v = r0.get() - e.translationX / L.w;
      if (v < 0) v = -0.1 * (1 - Math.exp(v / 0.1));
      if (v > LAST) v = LAST + 0.1 * (1 - Math.exp(-(v - LAST) / 0.1));
      r.set(v);
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      if (dragging.get() === 0) return;
      dragging.set(0);
      const v = -e.velocityX / L.w;
      goTo(Math.max(0, Math.min(LAST, Math.round(r.get() + 0.25 * v))), v === 0 ? 0.0001 : v);
    });

  const next = () => {
    takeOver();
    const k = Math.round(r.get());
    goTo(k >= LAST ? 0 : k + 1, 0);
  };

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => {
    const k = r.get();
    return {
      uView: [L.w / 2, L.cy, L.S, pixelRatio],
      uStep: [k, reduced ? 0.3 : yawOf(k), 0, bob.get()],
      uLight: [0, L.floorY, 0, 0],
    };
  });

  const buttonStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.04 * press.get() }] }));
  const fingerStyle = useAnimatedStyle(() => {
    const onButton = fingerAt.get() === 0;
    const x = onButton ? L.w / 2 : L.w * fingerX.get();
    const y = onButton ? L.buttonTop + 26 : L.cy + L.S * 0.4;
    return { opacity: 0.85 * finger.get(), transform: [{ translateX: x - 22 }, { translateY: y - 22 }, { scale: 1 - 0.12 * press.get() }] };
  });

  const onRoot = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setDims((d) => (d.w === width && d.h === height ? d : { w: width, h: height }));
  }, []);

  if (!fontsLoaded) return <View style={styles.root} onLayout={onRoot} />;

  return (
    <GestureDetector gesture={screenTouch}>
      <GestureDetector gesture={pager}>
        <View style={styles.root} onLayout={onRoot}>
          <StatusBar style="dark" />
          <View style={[styles.head, { top: L.top + 8 }]}>
            <Text style={styles.back} accessibilityRole="button">← Back</Text>
            <Text style={styles.skip} accessibilityRole="button">Skip</Text>
          </View>
          {active ? (
            <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
              <Fill>
                <Shader source={effect!} uniforms={uniforms} />
              </Fill>
            </Canvas>
          ) : null}
          {PAGES.map((p, i) => (
            <PageWords key={p.title} index={i} title={p.title} line={p.line} r={r} top={L.titleTop} w={L.w} />
          ))}
          <View style={[styles.dots, { top: L.dotsTop }]} accessible accessibilityLabel={`Step ${page + 1} of ${STEPS}`}>
            {PAGES.map((p, i) => (
              <Dot key={p.title} index={i} r={r} />
            ))}
          </View>
          <Animated.View style={[styles.buttonWrap, buttonStyle, { top: L.buttonTop }]}>
            <Pressable onPress={next} style={styles.button} accessibilityRole="button" accessibilityLabel={page === LAST ? 'Get started' : 'Next'}>
              <Text style={styles.buttonText}>{page === LAST ? 'Get started' : 'Next'}</Text>
            </Pressable>
          </Animated.View>
          <Animated.View pointerEvents="none" style={[styles.finger, fingerStyle]} />
        </View>
      </GestureDetector>
    </GestureDetector>
  );
}

function PageWords({ index, title, line, r, top, w }: { index: number; title: string; line: string; r: { get: () => number }; top: number; w: number }) {
  const style = useAnimatedStyle(() => {
    const d = r.get() - index;
    const k = Math.max(0, 1 - Math.abs(d) * 1.8);
    return { opacity: k * k, transform: [{ translateX: -d * w * 0.3 }] };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.words, style, { top }]} accessibilityElementsHidden={false}>
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.line}>{line}</Text>
    </Animated.View>
  );
}

function Dot({ index, r }: { index: number; r: { get: () => number } }) {
  const style = useAnimatedStyle(() => {
    const k = Math.max(0, 1 - Math.abs(r.get() - index));
    return { width: 8 + 16 * k, backgroundColor: k > 0.5 ? C.accent : C.dot };
  });
  return <Animated.View style={[styles.dot, style]} />;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page },
  head: { position: 'absolute', left: 20, right: 20, flexDirection: 'row', justifyContent: 'space-between' },
  back: { fontFamily: FONT.medium, fontSize: 15, color: C.accent },
  skip: { fontFamily: FONT.medium, fontSize: 15, color: C.mute },
  words: { position: 'absolute', left: 32, right: 32, alignItems: 'center', gap: 8 },
  title: { fontFamily: FONT.semibold, fontSize: 26, lineHeight: 32, color: C.ink, textAlign: 'center' },
  line: { fontFamily: FONT.regular, fontSize: 16, lineHeight: 23, color: C.mute, textAlign: 'center' },
  dots: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', gap: 6 },
  dot: { height: 8, borderRadius: 4 },
  buttonWrap: { position: 'absolute', left: 24, right: 24 },
  button: { height: 52, borderRadius: 26, backgroundColor: C.accent, alignItems: 'center', justifyContent: 'center' },
  buttonText: { fontFamily: FONT.semibold, fontSize: 17, color: C.accentInk },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(40,30,20,0.18)', borderWidth: 1.5, borderColor: 'rgba(40,30,20,0.35)' },
});
