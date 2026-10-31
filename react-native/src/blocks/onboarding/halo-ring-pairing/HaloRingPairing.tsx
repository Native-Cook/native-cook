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
  withDecay,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { bump, frameAt, LOOP, scaleOf, SEARCH_S, SIZES, smooth, START_SIZE, T, YAW0 } from './hrp-motion';
import { HRP_SKSL } from './hrp-shader';
import { C, CHIP_S, FONT, makeLayout, RING_S, STATS, WORDS } from './hrp-theme';

/*
 Halo Ring Pairing — pairing a smart ring: the search light condenses into the ring itself.

 The screen: while the phone looks for the ring, a green glow hangs in the dark with ripples spreading
 across its plane. When the ring is found the light condenses into a brushed titanium ring with a black
 inner coating, three sensor domes and a green light, and the screen offers its size and Pair ring.

 Signature: the ring is the search light made solid — the glowing line thickens into the band and the
 last of the light runs over the metal. Drag across the ring to spin it: it follows the finger and,
 let go, glides on with the finger's speed. Pick a size and the ring grows or shrinks a little with a
 bump. Pair ring and it flies up into the header's chip as the dashboard (readiness, sleep, resting
 heart, steps) fades in; tap the chip to bring it back.

 autoPlay: LOOP = 11 s (search; found; a finger spins the ring; size 10; Pair; the dashboard; the ring
 comes back and dissolves into light, and the search starts again); frame 11 s equals frame 0. A touch
 takes over; 2 s after the last one the ring dissolves into the search and the loop starts again.
 Reduced motion: no loop; the ripples hold still, the ring appears by a short fade, does not glide or
 bump, and moves to the header in a 0.3 s step.
*/

const effect = Skia.RuntimeEffect.Make(HRP_SKSL);
if (!effect) throw new Error('Halo Ring Pairing: the shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const NEVER = ReduceMotion.Never;
const PITCH = 1.05;

function buzz(kind: 'tick' | 'found' | 'pair') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else if (kind === 'found') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function HaloRingPairing({ autoPlay = false }: Props) {
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
  const L = useMemo(() => makeLayout(dims.w, dims.h, insets.top, insets.bottom), [dims, insets.top, insets.bottom]);
  const [size, setSize] = useState(START_SIZE);
  const [step, setStep] = useState(0); // 0 searching, 1 found, 2 paired (touch targets and VoiceOver)

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const phase = useSharedValue(0); // the lights' phase: one turn per loop
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const stay = useSharedValue(0);

  const stage = useSharedValue(0); // 0 searching, 1 found, 2 paired
  const condense = useSharedValue(0);
  const search = useSharedValue(1);
  const yaw = useSharedValue(YAW0);
  const scale = useSharedValue(1);
  const sizeBump = useSharedValue(1); // progress of the size bump (bump(sizeBump))
  const fly = useSharedValue(0);
  const paired = useSharedValue(0);
  const found = useSharedValue(0);
  const pressPair = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerAt = useSharedValue(0);
  const fingerDx = useSharedValue(0);
  const searchStart = useSharedValue(0);
  const spinning = useSharedValue(0);
  const yaw0 = useSharedValue(0);
  const sizeV = useSharedValue(START_SIZE);

  /* ------------------------------------------------------------ steps (declared above the clock) */
  const onFound = useCallback(() => buzz('found'), []);
  const onPairBuzz = useCallback(() => buzz('pair'), []);

  function becomeFound() {
    'worklet';
    stage.set(1);
    condense.set(withTiming(1, { duration: reduced ? 250 : 1200, easing: EASE_IN_OUT, reduceMotion: NEVER }));
    search.set(withTiming(0, { duration: 600, easing: EASE_OUT, reduceMotion: NEVER }));
    found.set(withDelay(reduced ? 0 : 700, withTiming(1, { duration: 400, easing: EASE_OUT, reduceMotion: NEVER })));
    scheduleOnRN(onFound);
  }

  function pair() {
    'worklet';
    if (stage.get() !== 1) return;
    stage.set(2);
    yaw.set(yaw.get());
    fly.set(withTiming(1, { duration: reduced ? 300 : 800, easing: EASE_IN_OUT, reduceMotion: NEVER }));
    paired.set(withDelay(reduced ? 0 : 300, withTiming(1, { duration: 500, easing: EASE_OUT, reduceMotion: NEVER })));
    found.set(withTiming(0, { duration: 250, reduceMotion: NEVER }));
    scheduleOnRN(onPairBuzz);
  }

  function unpair() {
    'worklet';
    if (stage.get() !== 2) return;
    stage.set(1);
    fly.set(withTiming(0, { duration: reduced ? 300 : 700, easing: EASE_IN_OUT, reduceMotion: NEVER }));
    paired.set(withTiming(0, { duration: 300, reduceMotion: NEVER }));
    found.set(withDelay(reduced ? 0 : 400, withTiming(1, { duration: 400, reduceMotion: NEVER })));
  }

  /* ------------------------------------------------------------ clock */
  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      phase.set((2 * Math.PI * t) / LOOP);
      condense.set(s.condense);
      search.set(s.search);
      yaw.set(s.yaw);
      scale.set(s.scale);
      sizeV.set(s.size);
      sizeBump.set(t > T.size ? Math.min(1, (t - T.size) / 0.7) : 1);
      fly.set(s.fly);
      paired.set(s.paired);
      found.set(s.found);
      pressPair.set(s.pressPair);
      finger.set(s.finger);
      fingerAt.set(s.fingerAt);
      fingerDx.set(s.fingerDx);
      stage.set(s.fly > 0.5 ? 2 : s.condense > 0.5 ? 1 : 0);
      return;
    }
    if (!reduced) phase.set(phase.get() + (2 * Math.PI * dt) / LOOP);
    // the phone "finds" the ring a little after the search starts
    if (stage.get() === 0 && resumeAsked.get() === 0 && clock.get() - searchStart.get() > SEARCH_S) becomeFound();
    if (spinning.get() === 1 || (stage.get() === 0 && resumeAsked.get() === 0)) lastTouch.set(clock.get());
    if (!auto || stay.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      // back to the search: the ring comes home, dissolves into light, size 9
      stage.set(0);
      fly.set(withTiming(0, { duration: 700, easing: EASE_IN_OUT }));
      paired.set(withTiming(0, { duration: 300 }));
      found.set(withTiming(0, { duration: 300 }));
      const turns = Math.round((yaw.get() - YAW0) / (2 * Math.PI));
      yaw.set(withTiming(YAW0 + 2 * Math.PI * turns, { duration: 900, easing: EASE_IN_OUT }));
      scale.set(withTiming(1, { duration: 500 }));
      sizeV.set(START_SIZE);
      condense.set(withDelay(600, withTiming(0, { duration: 800, easing: EASE_IN_OUT })));
      search.set(withDelay(1000, withTiming(1, { duration: 700 })));
    } else if (condense.get() < 0.001 && search.get() > 0.999 && fly.get() < 0.001) {
      resumeAsked.set(0);
      autoStart.set(clock.get());
      phase.set(0);
      yaw.set(YAW0);
      mode.set(0);
    }
  }, active);

  useEffect(() => {
    mode.set(auto ? 0 : 1);
    autoStart.set(clock.get());
    searchStart.set(clock.get());
  }, [auto, mode, autoStart, clock, searchStart]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__hrp = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      stay: (s: boolean) => {
        stay.set(s ? 1 : 0);
        if (s) mode.set(1);
      },
      state: () => JSON.stringify({ mode: mode.get(), stage: stage.get(), condense: +condense.get().toFixed(2), fly: +fly.get().toFixed(2), yaw: +yaw.get().toFixed(2) }),
    };
  }, [pin, stay, mode, stage, condense, fly, yaw]);

  useAnimatedReaction(
    () => stage.get(),
    (now, before) => {
      if (now !== before) scheduleOnRN(setStep, now);
    },
  );
  useAnimatedReaction(
    () => sizeV.get(),
    (now, before) => {
      if (now !== before) scheduleOnRN(setSize, now);
    },
  );

  function takeOver() {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    pressPair.set(withTiming(0, { duration: 150 }));
    const s = stage.get();
    if (s === 0) {
      // still searching: find it now
      searchStart.set(clock.get() - SEARCH_S);
    } else if (s === 1) {
      condense.set(withTiming(1, { duration: 300 }));
      search.set(withTiming(0, { duration: 300 }));
      found.set(withTiming(1, { duration: 300 }));
      fly.set(withTiming(0, { duration: 300 }));
      paired.set(withTiming(0, { duration: 300 }));
    } else {
      fly.set(withTiming(1, { duration: 300 }));
      paired.set(withTiming(1, { duration: 300 }));
      condense.set(withTiming(1, { duration: 300 }));
    }
  }

  const screenTouch = Gesture.Manual().onTouchesDown(() => {
    takeOver();
  });

  // spin the ring: it follows the finger, then glides on
  const spin = Gesture.Pan()
    .minDistance(3)
    .onBegin(() => {
      takeOver();
    })
    .onStart(() => {
      if (stage.get() !== 1) return;
      spinning.set(1);
      yaw.set(yaw.get());
      yaw0.set(yaw.get());
    })
    .onUpdate((e) => {
      if (spinning.get() !== 1) return;
      lastTouch.set(clock.get());
      yaw.set(yaw0.get() + e.translationX * 0.012);
    })
    .onFinalize((e) => {
      if (spinning.get() !== 1) return;
      spinning.set(0);
      lastTouch.set(clock.get());
      // a flick spins it at most about two turns a second, and it glides for a second or so
      if (!reduced) yaw.set(withDecay({ velocity: Math.max(-12, Math.min(12, e.velocityX * 0.012)), deceleration: 0.991 }));
    });

  const choose = (s: number) => {
    takeOver();
    if (stage.get() !== 1 || s === sizeV.get()) return;
    sizeV.set(s);
    scale.set(reduced ? withTiming(scaleOf(s), { duration: 200 }) : withSpring(scaleOf(s), { duration: 450, dampingRatio: 0.6 }));
    if (!reduced) {
      sizeBump.set(0);
      sizeBump.set(withTiming(1, { duration: 700, easing: Easing.linear }));
    }
    buzz('tick');
  };

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => {
    const f = fly.get();
    const arc = Math.sin(Math.PI * f);
    const x = L.w / 2 + (L.chip.x - L.w / 2) * f - 30 * arc;
    const y = L.stageY + (L.chip.y - L.stageY) * f - 20 * arc;
    const S = RING_S + (CHIP_S - RING_S) * f;
    const b = bump(sizeBump.get() * 0.7);
    return {
      uRing: [x, y, S, pixelRatio],
      uState: [condense.get(), yaw.get(), PITCH + (0.3 - PITCH) * f, scale.get() * (1 + 0.04 * b) * (1 - f) + f],
      uLive: [phase.get(), search.get() * (reduced ? 0.6 : 1), 1, 0],
    };
  });

  const searchWords = useAnimatedStyle(() => ({ opacity: (1 - smooth(0.2, 0.7, condense.get())) * (1 - paired.get()) }));
  const foundWords = useAnimatedStyle(() => ({ opacity: found.get(), transform: [{ translateY: 8 * (1 - found.get()) }] }));
  const dash = useAnimatedStyle(() => ({ opacity: paired.get(), transform: [{ translateY: 12 * (1 - paired.get()) }] }));
  const chipStyle = useAnimatedStyle(() => ({ opacity: smooth(0.6, 1, fly.get()) }));
  const pairStyle = useAnimatedStyle(() => ({ opacity: found.get(), transform: [{ scale: 1 - 0.04 * pressPair.get() }] }));
  const fingerStyle = useAnimatedStyle(() => {
    const at = fingerAt.get();
    const x = at === 0 ? L.w * 0.3 + fingerDx.get() : at === 1 ? L.w / 2 + 28 : L.w / 2;
    const y = at === 0 ? L.stageY + 40 : at === 1 ? L.sizesTop + 18 : L.buttonTop + 28;
    return { opacity: 0.85 * finger.get(), transform: [{ translateX: x - 22 }, { translateY: y - 22 }] };
  });

  const onRoot = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setDims((d) => (d.w === width && d.h === height ? d : { w: width, h: height }));
  }, []);

  if (!fontsLoaded) return <View style={styles.root} onLayout={onRoot} />;

  return (
    <GestureDetector gesture={screenTouch}>
      <View style={styles.root} onLayout={onRoot}>
        <StatusBar style="light" />

        {/* the stage: drag to spin */}
        <GestureDetector gesture={spin}>
          <View
            style={[styles.stage, { top: L.stageY - 150 }]}
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel={step === 0 ? WORDS.searchTitle : 'Halo Ring, titanium. Drag to turn it'}
          />
        </GestureDetector>

        {/* search words */}
        <Animated.View pointerEvents="none" style={[styles.words, searchWords, { top: L.titleTop }]}>
          <Text style={styles.title}>{WORDS.searchTitle}</Text>
          <Text style={styles.sub}>{WORDS.searchSub}</Text>
        </Animated.View>

        {/* found: the words, the sizes, Pair */}
        <Animated.View pointerEvents={step === 1 ? 'auto' : 'none'} style={[styles.words, foundWords, { top: L.titleTop }]}>
          <Text style={styles.title} accessibilityRole="header">{WORDS.foundTitle}</Text>
          <Text style={styles.sub}>{`${WORDS.foundSub} · Size ${size}`}</Text>
        </Animated.View>
        <Animated.View pointerEvents={step === 1 ? 'auto' : 'none'} style={[styles.sizes, foundWords, { top: L.sizesTop }]} accessibilityLabel={WORDS.sizeLabel}>
          {SIZES.map((s) => (
            <Pressable key={s} onPress={() => choose(s)} style={[styles.size, s === size ? styles.sizeOn : null]} accessibilityRole="button" accessibilityLabel={`Size ${s}`} accessibilityState={{ selected: s === size }}>
              <Text style={[styles.sizeText, s === size ? styles.sizeTextOn : null]}>{s}</Text>
            </Pressable>
          ))}
        </Animated.View>
        <Animated.View pointerEvents={step === 1 ? 'auto' : 'none'} style={[styles.pairWrap, pairStyle, { top: L.buttonTop }]}>
          <Pressable onPress={() => pair()} style={styles.pair} accessibilityRole="button" accessibilityLabel={WORDS.pair}>
            <Text style={styles.pairText}>{WORDS.pair}</Text>
          </Pressable>
        </Animated.View>

        {/* paired: the dashboard */}
        <Animated.View pointerEvents={step === 2 ? 'auto' : 'none'} style={[styles.dash, dash, { top: L.top + 10 }]}>
          <View style={styles.dashHead}>
            <Pressable onPress={() => unpair()} style={styles.chip} accessibilityRole="button" accessibilityLabel="Halo Ring, connected. Show the ring" />
            <View>
              <Text style={styles.dashName}>Halo</Text>
              <Text style={styles.dashState}>Connected</Text>
            </View>
          </View>
          <Text style={styles.greeting} accessibilityRole="header">Good morning</Text>
          <View style={styles.grid}>
            {STATS.map((s) => (
              <View key={s.label} style={styles.stat} accessible accessibilityLabel={`${s.label} ${s.value} ${s.unit}`}>
                <Text style={styles.statLabel}>{s.label}</Text>
                <Text style={styles.statValue}>
                  {s.value}
                  {s.unit ? <Text style={styles.statUnit}>{` ${s.unit}`}</Text> : null}
                </Text>
              </View>
            ))}
          </View>
        </Animated.View>
        <Animated.View pointerEvents="none" style={[styles.chipRing, chipStyle, { left: L.chip.x - 22, top: L.chip.y - 22 }]} />

        {/* the ring and its light */}
        {active ? (
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Fill>
              <Shader source={effect!} uniforms={uniforms} />
            </Fill>
          </Canvas>
        ) : null}

        <Animated.View pointerEvents="none" style={[styles.finger, fingerStyle]} />
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page },
  stage: { position: 'absolute', left: 0, right: 0, height: 300 },
  words: { position: 'absolute', left: 24, right: 24, alignItems: 'center', gap: 6 },
  title: { fontFamily: FONT.semibold, fontSize: 26, lineHeight: 32, color: C.ink, textAlign: 'center' },
  sub: { fontFamily: FONT.regular, fontSize: 15, lineHeight: 20, color: C.mute, textAlign: 'center' },
  sizes: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', gap: 8 },
  size: { width: 46, height: 36, borderRadius: 18, borderWidth: 1, borderColor: C.line, alignItems: 'center', justifyContent: 'center' },
  sizeOn: { borderColor: C.green, backgroundColor: 'rgba(59,227,138,0.12)' },
  sizeText: { fontFamily: FONT.medium, fontSize: 15, color: C.mute, fontVariant: ['tabular-nums'] },
  sizeTextOn: { color: C.ink },
  pairWrap: { position: 'absolute', left: 20, right: 20 },
  pair: { height: 56, borderRadius: 28, backgroundColor: C.green, alignItems: 'center', justifyContent: 'center' },
  pairText: { fontFamily: FONT.semibold, fontSize: 17, color: C.greenInk },
  dash: { position: 'absolute', left: 20, right: 20 },
  dashHead: { flexDirection: 'row', alignItems: 'center', gap: 12, height: 44 },
  chip: { width: 44, height: 44, borderRadius: 22 },
  dashName: { fontFamily: FONT.semibold, fontSize: 16, lineHeight: 21, color: C.ink },
  dashState: { fontFamily: FONT.medium, fontSize: 13, lineHeight: 17, color: C.green },
  greeting: { fontFamily: FONT.semibold, fontSize: 30, lineHeight: 38, color: C.ink, marginTop: 28, marginBottom: 16 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  stat: { width: '47.5%', height: 108, borderRadius: 22, backgroundColor: C.card, padding: 16, justifyContent: 'space-between' },
  statLabel: { fontFamily: FONT.medium, fontSize: 14, color: C.mute },
  statValue: { fontFamily: FONT.semibold, fontSize: 28, color: C.ink, fontVariant: ['tabular-nums'] },
  statUnit: { fontFamily: FONT.medium, fontSize: 15, color: C.mute },
  chipRing: { position: 'absolute', width: 44, height: 44, borderRadius: 22, backgroundColor: C.card, borderWidth: 1, borderColor: C.line },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(255,255,255,0.35)', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.8)' },
});
