import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { BASE, FLIGHT, frameAt, heartAt, LANES, LOOP, pulse, SLOTS, SWALLOW, THROWS } from './llb-motion';
import { LLB_SKSL } from './llb-shader';

/*
 Live Like Bubble — the live likes of a stream, counted by a glossy 3D like bubble that swallows the
 hearts thrown at it.

 The screen: a pale studio floor, "LIVE · 2.4K watching", the red like bubble with a white heart and the
 likes of the last minute, and a heart button.

 Signature: tap the heart button and a small puffy heart is thrown from your finger in an arc at the
 bubble; it lands on the bubble and melts into it — a gooey neck pulls out of the bubble to meet it —
 the bubble swells with a wobble and the count rolls up. Hold the button and the hearts stream like a
 live audience. When nobody taps, the likes-per-minute fall back.

 autoPlay: LOOP = 9 s (three taps, then a held stream of eleven hearts, then the count falls back);
 frame 9 s equals frame 0. A touch takes over; 2 s after the last heart has landed and the count is
 back to its rest the loop starts again.
 Reduced motion: no loop; hearts are thrown without the bubble's wobble and the count changes at once.
*/

const effect = Skia.RuntimeEffect.Make(LLB_SKSL);
if (!effect) throw new Error('Live Like Bubble: the shader failed to compile.');
const pixelRatio = PixelRatio.get();
const C = { page: '#E9E9EE', ink: '#16161B', mute: '#6F6F7A', live: '#FF2E55', card: '#FFFFFF', line: '#D9D9E0' };
const FONT = { regular: 'LlbInter-Regular', medium: 'LlbInter-Medium', semibold: 'LlbInter-SemiBold' };
const TILT = 0.3;
const STREAM = 0.15; // s between hearts while the button is held

function buzz() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function LiveLikeBubble({ autoPlay = false }: Props) {
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
    const ppu = Math.round(dims.w * 0.32);
    const centreY = insets.top + Math.round((dims.h - insets.top - insets.bottom) * 0.42);
    const buttonY = dims.h - Math.max(insets.bottom, 16) - 8 - 36;
    return { w: dims.w, h: dims.h, top: insets.top, ppu, centreY, buttonY };
  }, [dims, insets.top, insets.bottom]);
  const [count, setCount] = useState(BASE);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const pin = useSharedValue(-1);
  const stay = useSharedValue(0);
  const now = useSharedValue(0); // the time the hearts are drawn at (loop time, or the clock)

  // six slots: when each heart was thrown and from where
  const s0 = useSharedValue(-10), s1 = useSharedValue(-10), s2 = useSharedValue(-10), s3 = useSharedValue(-10), s4 = useSharedValue(-10), s5 = useSharedValue(-10);
  const l0 = useSharedValue(0), l1 = useSharedValue(0), l2 = useSharedValue(0), l3 = useSharedValue(0), l4 = useSharedValue(0), l5 = useSharedValue(0);
  const next = useSharedValue(0);
  const likes = useSharedValue(BASE);
  const swell = useSharedValue(0);
  const lastLand = useSharedValue(-10);
  const holding = useSharedValue(0);
  const holdX = useSharedValue(0);
  const lastThrow = useSharedValue(-10);
  const press = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerX = useSharedValue(0);

  /* ------------------------------------------------------------ throwing (above the clock) */
  const onLand = useCallback(() => buzz(), []);

  function throwHeart(lane: number) {
    'worklet';
    const i = next.get() % SLOTS;
    next.set(next.get() + 1);
    const t = clock.get();
    if (i === 0) { s0.set(t); l0.set(lane); }
    else if (i === 1) { s1.set(t); l1.set(lane); }
    else if (i === 2) { s2.set(t); l2.set(lane); }
    else if (i === 3) { s3.set(t); l3.set(lane); }
    else if (i === 4) { s4.set(t); l4.set(lane); }
    else { s5.set(t); l5.set(lane); }
    lastThrow.set(t);
  }

  /* ------------------------------------------------------------ clock */
  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const fr = frameAt(t);
      now.set(t);
      likes.set(fr.count);
      swell.set(fr.swell);
      press.set(fr.press);
      finger.set(fr.finger);
      fingerX.set(fr.fingerX);
      return;
    }
    const t = clock.get();
    now.set(t);
    if (holding.get() === 1 && t - lastThrow.get() > STREAM) throwHeart(holdX.get() + (Math.sin(t * 7) * 0.25));
    // hearts that have just been swallowed count, and swell the bubble
    const starts = [s0.get(), s1.get(), s2.get(), s3.get(), s4.get(), s5.get()];
    let sw = 0;
    for (let i = 0; i < SLOTS; i++) {
      const landed = starts[i] + FLIGHT + SWALLOW;
      if (landed <= t && landed > t - dt) {
        likes.set(likes.get() + 1);
        lastLand.set(t);
        scheduleOnRN(onLand);
      }
      if (!reduced) sw += pulse(t - starts[i] - FLIGHT - SWALLOW * 0.5);
    }
    swell.set(Math.max(-1, Math.min(1, sw)));
    // with nobody tapping the likes per minute fall back, one every quarter second
    if (t - lastLand.get() > 1.5 && likes.get() > BASE && t - lastTouch.get() > 0.25) {
      likes.set(likes.get() - 1);
      lastTouch.set(t - 1.75 + 0.25);
    }
    if (holding.get() === 1 || t - lastThrow.get() < FLIGHT + SWALLOW + 0.1) lastTouch.set(t);
    if (!auto || stay.get() === 1 || likes.get() !== BASE || t - lastTouch.get() < 2) return;
    autoStart.set(clock.get());
    mode.set(0);
  }, active);

  useEffect(() => {
    mode.set(auto ? 0 : 1);
    autoStart.set(clock.get());
  }, [auto, mode, autoStart, clock]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__llb = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      stay: (v: boolean) => {
        stay.set(v ? 1 : 0);
        if (v) mode.set(1);
      },
      state: () => JSON.stringify({ mode: mode.get(), likes: likes.get(), next: next.get() }),
    };
  }, [pin, stay, mode, likes, next]);

  useAnimatedReaction(
    () => likes.get(),
    (n, before) => {
      if (n !== before) scheduleOnRN(setCount, n);
    },
  );

  function takeOver() {
    'worklet';
    lastTouch.set(clock.get());
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    press.set(0);
    // the loop's hearts in the air are dropped; the count carries on from what the loop showed
    s0.set(-10); s1.set(-10); s2.set(-10); s3.set(-10); s4.set(-10); s5.set(-10);
    lastLand.set(clock.get());
  }

  const screenTouch = Gesture.Manual().onTouchesDown(() => {
    takeOver();
  });
  const heartButton = Gesture.LongPress()
    .minDuration(0)
    .maxDistance(600)
    .onBegin((e) => {
      takeOver();
      const lane = Math.max(-0.9, Math.min(0.9, (e.absoluteX - L.w / 2) / L.ppu));
      holdX.set(lane);
      press.set(withTiming(1, { duration: 90, easing: Easing.out(Easing.quad) }));
      throwHeart(lane);
      holding.set(0);
    })
    .onStart(() => {
      holding.set(1);
    })
    .onFinalize(() => {
      holding.set(0);
      lastTouch.set(clock.get());
      press.set(withTiming(0, { duration: 150 }));
    });

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => {
    const t = now.get();
    const loop = mode.get() === 0;
    const h = (i: number, start: number, lane: number) => {
      const p = heartAt(t, start, lane, i);
      return [p.x, p.y, p.z, p.s];
    };
    let hs: number[][];
    if (loop) {
      // in the loop the hearts come from the script: the six latest throws
      hs = [];
      for (let k = 0; k < SLOTS; k++) hs.push([0, 0, 0, 0]);
      for (let i = 0; i < THROWS.length; i++) {
        if (t >= THROWS[i] && t <= THROWS[i] + FLIGHT + SWALLOW) hs[i % SLOTS] = h(i, THROWS[i], LANES[i]);
      }
    } else {
      hs = [h(0, s0.get(), l0.get()), h(1, s1.get(), l1.get()), h(2, s2.get(), l2.get()), h(3, s3.get(), l3.get()), h(4, s4.get(), l4.get()), h(5, s5.get(), l5.get())];
    }
    return {
      uView: [L.w, L.h, L.ppu, pixelRatio],
      uBubble: [L.centreY, Math.max(0, swell.get()), reduced ? 0 : swell.get(), TILT],
      uH0: hs[0],
      uH1: hs[1],
      uH2: hs[2],
      uH3: hs[3],
      uH4: hs[4],
      uH5: hs[5],
    };
  });

  const countStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 + 0.07 * Math.max(0, swell.get()) }] }));
  const buttonStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.08 * press.get() }] }));
  const fingerStyle = useAnimatedStyle(() => ({
    opacity: 0.85 * finger.get(),
    transform: [{ translateX: L.w / 2 + fingerX.get() * L.ppu * 0.4 - 22 }, { translateY: L.buttonY - 22 }, { scale: 1 - 0.12 * press.get() }],
  }));

  const onRoot = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setDims((d) => (d.w === width && d.h === height ? d : { w: width, h: height }));
  }, []);

  if (!fontsLoaded) return <View style={styles.root} onLayout={onRoot} />;

  const fs = Math.round(L.ppu * 0.5);
  return (
    <GestureDetector gesture={screenTouch}>
      <View style={styles.root} onLayout={onRoot}>
        <StatusBar style="dark" />
        {active ? (
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Fill>
              <Shader source={effect!} uniforms={uniforms} />
            </Fill>
          </Canvas>
        ) : null}

        <View style={[styles.head, { top: L.top + 10 }]}>
          <View style={styles.live}>
            <Text style={styles.liveText}>LIVE</Text>
          </View>
          <Text style={styles.watching}>2.4K watching</Text>
        </View>

        {/* the count on the bubble's face, right of its heart */}
        <Animated.View pointerEvents="none" style={[styles.countBox, countStyle, { left: L.w / 2 + 0.02 * L.ppu, top: L.centreY - fs * 0.75, width: 0.95 * L.ppu, height: fs * 1.5 }]} accessible accessibilityLabel={`${count} likes in the last minute`} accessibilityLiveRegion="polite">
          <Text style={[styles.count, { fontSize: fs, lineHeight: fs * 1.2 }]} allowFontScaling={false}>{count}</Text>
        </Animated.View>
        <Text style={[styles.caption, { top: L.centreY + L.ppu * 1.35 }]}>likes in the last minute</Text>

        <GestureDetector gesture={heartButton}>
          <Animated.View style={[styles.button, buttonStyle, { left: L.w / 2 - 36, top: L.buttonY - 36 }]} accessible accessibilityRole="button" accessibilityLabel="Send a like" accessibilityHint="Hold to send many">
            <Svg width={30} height={28} viewBox="0 0 24 22">
              <SvgPath d="M12 21s-8.5-5.3-10.6-10.4C-0.1 6 3 1.5 7.1 1.5c2.2 0 3.8 1.2 4.9 2.8 1.1-1.6 2.7-2.8 4.9-2.8 4.1 0 7.2 4.5 5.7 9.1C20.5 15.7 12 21 12 21z" fill={C.live} />
            </Svg>
          </Animated.View>
        </GestureDetector>

        <Animated.View pointerEvents="none" style={[styles.finger, fingerStyle]} />
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page },
  head: { position: 'absolute', left: 20, flexDirection: 'row', alignItems: 'center', gap: 10 },
  live: { height: 26, paddingHorizontal: 10, borderRadius: 8, backgroundColor: C.live, justifyContent: 'center' },
  liveText: { fontFamily: FONT.semibold, fontSize: 13, color: '#FFFFFF', letterSpacing: 0.6 },
  watching: { fontFamily: FONT.medium, fontSize: 15, color: C.ink },
  countBox: { position: 'absolute', alignItems: 'center', justifyContent: 'center' },
  count: { fontFamily: FONT.semibold, color: '#FFFFFF', fontVariant: ['tabular-nums'], letterSpacing: -1 },
  caption: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.medium, fontSize: 15, color: C.mute },
  button: { position: 'absolute', width: 72, height: 72, borderRadius: 36, backgroundColor: C.card, borderWidth: 1, borderColor: C.line, alignItems: 'center', justifyContent: 'center', boxShadow: '0 8px 24px rgba(30,30,60,0.12)' },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(30,30,60,0.18)', borderWidth: 1.5, borderColor: 'rgba(30,30,60,0.35)' },
});
