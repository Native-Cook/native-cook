import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { BEANS, frameAt, LOOP, POINTS_PER_BEAN, POUR_RATE, sinkOf, smooth, TILT, wobbleAt } from './jse-motion';
import { JSE_SKSL } from './jse-shader';

/*
 Jelly Scale Exchange — a rewards app's jelly shop: pour jelly beans onto a kitchen scale, then
 exchange them for a coin.

 The screen: the shop's title and a Points pill, a periwinkle clay kitchen scale with a white dish and
 a dark display, and two buttons, Pour (hold) and Exchange.

 Signature: hold Pour and jelly beans fall one after another into the dish, bouncing as they land; the
 pad sinks with the weight and the display counts them. Exchange melts the pile: the beans slide
 together and run into one iridescent gummy, which rises out of the dish, is pressed into a gold coin
 with a raised star, turns once and flies into the Points pill, which wobbles as "+80" rises from it.

 autoPlay: LOOP = 10 s (hold Pour: sixteen beans; Exchange: melt, coin, flight, "+80"); frame 10 s
 equals frame 0. A touch takes over; 2 s after the dish is empty and at rest the loop starts again.
 Reduced motion: no loop; the beans appear without the fall, the melt and the coin are short fades and
 the coin moves to the pill without the turn.
*/

const effect = Skia.RuntimeEffect.Make(JSE_SKSL);
if (!effect) throw new Error('Jelly Scale Exchange: the shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const NEVER = ReduceMotion.Never;
const C = { page: '#14122B', ink: '#F3F1FF', mute: '#9894B8', card: '#211E3D', line: '#34305A', accent: '#FFC53D', accentInk: '#2A1D00', lcd: '#9CF2C5' };
const FONT = { regular: 'JseInter-Regular', medium: 'JseInter-Medium', semibold: 'JseInter-SemiBold' };
const RISE = { y: 1.05, z: 0.25 };

function buzz(kind: 'bean' | 'done') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'bean') Haptics.selectionAsync().catch(() => {});
  else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function JellyScaleExchange({ autoPlay = false }: Props) {
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
    const top = insets.top;
    const ppu = Math.round(dims.w * 0.4);
    const stageY = top + 96 + Math.round((dims.h - top - insets.bottom - 96 - 120) * 0.5);
    const buttonsTop = dims.h - Math.max(insets.bottom, 16) - 8 - 56;
    // the display on the scale's front, projected through the tilted camera
    const lcdY = stageY + (0.6 * Math.cos(TILT) + 0.78 * Math.sin(TILT)) * ppu;
    const pill = { x: dims.w - 20 - 60, y: top + 10 + 20 };
    return { w: dims.w, h: dims.h, top, ppu, stageY, buttonsTop, lcdY, lcdW: ppu * 0.92, lcdH: ppu * 0.25, pill };
  }, [dims, insets.top, insets.bottom]);
  const [count, setCount] = useState(0);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const pin = useSharedValue(-1);
  const stay = useSharedValue(0);

  const pour = useSharedValue(0);
  const melt = useSharedValue(0);
  const coin = useSharedValue(0);
  const spin = useSharedValue(0);
  const fly = useSharedValue(0);
  const wobbleU = useSharedValue(1);
  const plus = useSharedValue(1);
  const pouring = useSharedValue(0);
  const busy = useSharedValue(0); // an exchange is running
  const pressPour = useSharedValue(0);
  const pressSwap = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerAt = useSharedValue(0);

  /* ------------------------------------------------------------ the exchange (above the clock) */
  const onDone = useCallback(() => buzz('done'), []);
  const onBean = useCallback(() => buzz('bean'), []);

  function landed() {
    'worklet';
    wobbleU.set(0);
    wobbleU.set(withTiming(1, { duration: 700, easing: Easing.linear }));
    plus.set(0);
    plus.set(withTiming(1, { duration: 900, easing: Easing.linear, reduceMotion: NEVER }));
    pour.set(0);
    melt.set(0);
    coin.set(0);
    spin.set(0);
    fly.set(0);
    busy.set(0);
    scheduleOnRN(onDone);
  }

  function exchange() {
    'worklet';
    if (busy.get() === 1 || pour.get() < 0.5) return;
    busy.set(1);
    pouring.set(0);
    pour.set(withTiming(Math.ceil(pour.get()), { duration: 200 }));
    const m = reduced ? 300 : 1100;
    const c = reduced ? 300 : 800;
    melt.set(withTiming(1, { duration: m, easing: EASE_IN_OUT, reduceMotion: NEVER }, (done) => {
      if (!done) return;
      coin.set(withTiming(1, { duration: c, easing: EASE_IN_OUT, reduceMotion: NEVER }, (ok) => {
        if (!ok) return;
        if (!reduced) spin.set(withTiming(2 * Math.PI, { duration: 900, easing: EASE_IN_OUT }));
        fly.set(withTiming(1, { duration: reduced ? 300 : 700, easing: EASE_IN_OUT, reduceMotion: NEVER }, (fin) => {
          if (fin) landed();
        }));
      }));
    }));
  }

  /* ------------------------------------------------------------ clock */
  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      pour.set(s.pour);
      melt.set(s.melt);
      coin.set(s.coin);
      spin.set(s.spin);
      fly.set(s.fly);
      wobbleU.set(s.wobbleU);
      plus.set(s.plus);
      pressPour.set(s.pressPour);
      pressSwap.set(s.pressSwap);
      finger.set(s.finger);
      fingerAt.set(s.fingerAt);
      return;
    }
    if (pouring.get() === 1 && busy.get() === 0 && pour.get() < BEANS) {
      const before = Math.floor(pour.get());
      const next = Math.min(BEANS, pour.get() + (reduced ? POUR_RATE * 2 : POUR_RATE) * dt);
      pour.set(next);
      if (Math.floor(next) !== before) scheduleOnRN(onBean);
    }
    if (pouring.get() === 1 || busy.get() === 1 || wobbleU.get() < 1 || plus.get() < 1) lastTouch.set(clock.get());
    if (!auto || stay.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    // the loop starts with an empty dish: a pile left by a finger is exchanged first
    if (pour.get() > 0.001) exchange();
    else {
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
    (globalThis as Record<string, unknown>).__jse = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      stay: (v: boolean) => {
        stay.set(v ? 1 : 0);
        if (v) mode.set(1);
      },
      state: () => JSON.stringify({ mode: mode.get(), pour: +pour.get().toFixed(2), melt: +melt.get().toFixed(2), coin: +coin.get().toFixed(2), fly: +fly.get().toFixed(2), busy: busy.get(), pouring: pouring.get(), press: +pressPour.get().toFixed(2) }),
    };
  }, [pin, stay, mode, pour, melt, coin, fly, busy, pouring, pressPour]);

  // the display: the beans in the dish (0 once the coin has lifted out)
  useAnimatedReaction(
    () => (coin.get() > 0.6 ? 0 : Math.floor(pour.get() + 0.0001)),
    (now, before) => {
      if (now !== before) scheduleOnRN(setCount, now);
    },
  );

  function takeOver() {
    'worklet';
    lastTouch.set(clock.get());
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    pressPour.set(withTiming(0, { duration: 150 }));
    pressSwap.set(withTiming(0, { duration: 150 }));
    // an exchange under way carries on; else the dish keeps what has been poured
    if (melt.get() > 0.001 && fly.get() < 0.999) {
      busy.set(0);
      exchange();
    }
  }

  const screenTouch = Gesture.Manual().onTouchesDown(() => {
    takeOver();
  });
  const pourGesture = Gesture.LongPress()
    .minDuration(0)
    .maxDistance(400)
    .onBegin(() => {
      takeOver();
      if (busy.get() === 1) return;
      pouring.set(1);
      pressPour.set(withTiming(1, { duration: 100, easing: EASE_OUT }));
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      pouring.set(0);
      pressPour.set(withTiming(0, { duration: 150, easing: EASE_OUT }));
    });
  const swapGesture = Gesture.Tap()
    .onBegin(() => {
      takeOver();
      pressSwap.set(withTiming(1, { duration: 100, easing: EASE_OUT }));
    })
    .onEnd(() => {
      exchange();
    })
    .onFinalize(() => {
      pressSwap.set(withTiming(0, { duration: 150, easing: EASE_OUT }));
    });

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => {
    const f = fly.get();
    // the coin's flight, from its place above the dish to the Points pill, in the scene's units
    const riseSy = RISE.y * Math.cos(TILT) - RISE.z * Math.sin(TILT);
    const targetSx = (L.pill.x - 28 - L.w / 2) / L.ppu;
    const targetSy = -(L.pill.y - L.stageY) / L.ppu;
    const arc = Math.sin(Math.PI * f) * 0.6;
    const dx = targetSx * f;
    const dy = ((targetSy - riseSy) * f) / Math.cos(TILT) + arc;
    return {
      uView: [L.w, L.h, L.ppu, pixelRatio],
      uPour: [reduced ? Math.ceil(pour.get() - 0.001) : pour.get(), melt.get(), coin.get(), spin.get()],
      uCoin: [dx, dy, 1 - 0.78 * f, 1 - smooth(0.85, 1, f)],
      uScale: [sinkOf(pour.get(), coin.get()), TILT, 0, L.stageY],
    };
  });

  const pillStyle = useAnimatedStyle(() => {
    const w = wobbleAt(wobbleU.get());
    return { transform: [{ scaleX: 1 + 0.12 * w }, { scaleY: 1 - 0.1 * w }] };
  });
  const plusStyle = useAnimatedStyle(() => ({ opacity: Math.sin(Math.PI * plus.get()), transform: [{ translateY: 14 - 26 * plus.get() }] }));
  const pourStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.04 * pressPour.get() }] }));
  const swapStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.04 * pressSwap.get() }], opacity: pour.get() > 0.5 || busy.get() === 1 ? 1 : 0.45 }));
  const fingerStyle = useAnimatedStyle(() => {
    const x = fingerAt.get() === 0 ? 20 + (L.w - 52) / 4 : L.w - 20 - (L.w - 52) / 4;
    return { opacity: 0.85 * finger.get(), transform: [{ translateX: x - 22 }, { translateY: L.buttonsTop + 28 - 22 }] };
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
        <View style={[styles.head, { top: L.top + 10 }]}>
          <Text style={styles.title} accessibilityRole="header">Jelly shop</Text>
          <Text style={styles.sub}>Swap jellies for points</Text>
        </View>
        <Animated.View style={[styles.pill, pillStyle, { left: L.pill.x - 60, top: L.pill.y - 20 }]} accessible accessibilityLabel="Points">
          <Svg width={22} height={22} viewBox="0 0 22 22">
            <Circle cx={11} cy={11} r={10} fill="#FFC53D" />
            <SvgPath d="M11 5.5l1.6 3.4 3.7.4-2.8 2.5.8 3.7L11 13.6l-3.3 1.9.8-3.7-2.8-2.5 3.7-.4z" fill="#B97A00" />
          </Svg>
          <Text style={styles.pillText}>Points</Text>
        </Animated.View>
        <Animated.Text style={[styles.plus, plusStyle, { left: L.pill.x - 110, top: L.pill.y - 10 }]} accessibilityLiveRegion="polite">
          +{BEANS * POINTS_PER_BEAN}
        </Animated.Text>

        {active ? (
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Fill>
              <Shader source={effect!} uniforms={uniforms} />
            </Fill>
          </Canvas>
        ) : null}

        {/* the scale's display */}
        <View pointerEvents="none" style={[styles.lcd, { left: L.w / 2 - L.lcdW / 2, top: L.lcdY - L.lcdH / 2, width: L.lcdW, height: L.lcdH }]} accessible accessibilityLabel={`${count} jellies on the scale`}>
          <Text style={[styles.lcdText, { fontSize: L.lcdH * 0.62 }]}>{String(count).padStart(2, '0')}</Text>
        </View>

        <View style={[styles.buttons, { top: L.buttonsTop }]}>
          <GestureDetector gesture={pourGesture}>
            <Animated.View style={[styles.button, styles.pour, pourStyle]} accessible accessibilityRole="button" accessibilityLabel="Pour jellies" accessibilityHint="Pours while held">
              <Text style={styles.pourText}>Hold to pour</Text>
            </Animated.View>
          </GestureDetector>
          <GestureDetector gesture={swapGesture}>
            <Animated.View style={[styles.button, styles.swap, swapStyle]} accessible accessibilityRole="button" accessibilityLabel={`Exchange ${count} jellies`} accessibilityState={{ disabled: count === 0 }}>
              <Text style={styles.swapText}>Exchange</Text>
            </Animated.View>
          </GestureDetector>
        </View>

        <Animated.View pointerEvents="none" style={[styles.finger, fingerStyle]} />
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page },
  head: { position: 'absolute', left: 20, right: 150, gap: 2 },
  title: { fontFamily: FONT.semibold, fontSize: 26, lineHeight: 32, color: C.ink },
  sub: { fontFamily: FONT.regular, fontSize: 14, lineHeight: 20, color: C.mute },
  pill: { position: 'absolute', width: 120, height: 40, borderRadius: 20, backgroundColor: C.card, borderWidth: 1, borderColor: C.line, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  pillText: { fontFamily: FONT.semibold, fontSize: 15, color: C.ink },
  plus: { position: 'absolute', width: 44, textAlign: 'right', fontFamily: FONT.semibold, fontSize: 16, color: C.accent },
  lcd: { position: 'absolute', alignItems: 'center', justifyContent: 'center' },
  lcdText: { fontFamily: FONT.semibold, color: C.lcd, fontVariant: ['tabular-nums'], letterSpacing: 2 },
  buttons: { position: 'absolute', left: 20, right: 20, flexDirection: 'row', gap: 12 },
  button: { flex: 1, height: 56, borderRadius: 28, alignItems: 'center', justifyContent: 'center' },
  pour: { backgroundColor: C.card, borderWidth: 1, borderColor: C.line },
  pourText: { fontFamily: FONT.semibold, fontSize: 16, color: C.ink },
  swap: { backgroundColor: C.accent },
  swapText: { fontFamily: FONT.semibold, fontSize: 16, color: C.accentInk },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(255,255,255,0.35)', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.8)' },
});
