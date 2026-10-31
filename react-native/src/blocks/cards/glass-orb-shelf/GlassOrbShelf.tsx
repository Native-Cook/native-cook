import { Canvas, Rect, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
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
import { APPS, COLOR, FONT, LOOP, makeLayout, offsetAt, PAGE_RGB } from './gos-motion';
import { GOS_SKSL } from './gos-shader';

/*
 Glass Orb Shelf — the "Featured" shelf of an invented mini-app store.

 Look (the reference, a recording of a shipped app's home screen): "Featured", a row of cards that
 page sideways with the next one peeking, each topped by a glass ball holding a small scene, a
 two-tone title and a few lines of copy.

 Ours, after the viral pass: one large Featured orb (84 % of the width) stands still at the top of
 a dark shelf and the cards page under it. Its scene is clay — a book, a globe, a film camera, a
 bowl in front of a receipt — and it melts from one app's scene into the next inside the orb as the
 cards move, scrubbed by the finger (swipe the cards or the orb). A flick makes the contents lean,
 a tap on the orb makes it wobble and spin.

 autoPlay: LOOP = 9.6 s. Rest on the book, page to the globe, the camera, the bowl, then one long
 slide back to the book. A touch takes over; 2 s after the last one the shelf slides home and the
 loop starts again. Reduced motion: no loop; paging still works, the scene changes without a blend.
*/

const effect = Skia.RuntimeEffect.Make(GOS_SKSL);
if (!effect) throw new Error('Glass Orb Shelf: the orb shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const LAST = APPS.length - 1;

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}
function bump() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function GlassOrbShelf({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [width, setWidth] = useState(0);
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
    [FONT.bold]: require('./assets/fonts/Inter-Bold.ttf'),
  });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const L = useMemo(() => makeLayout(width || 402, insets.top), [width, insets.top]);

  const offset = useSharedValue(0); // pages
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const dragging = useSharedValue(0);
  const startOffset = useSharedValue(0);
  const prevOffset = useSharedValue(0);
  const lean = useSharedValue(0);
  const spin = useSharedValue(0);
  const pop = useSharedValue(0);
  const page = useSharedValue(0);
  const pin = useSharedValue(-1);
  const [current, setCurrent] = useState(0);

  const goHome = useCallback(() => {
    offset.set(withTiming(0, { duration: 900, easing: EASE_IN_OUT }));
  }, [offset]);

  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      offset.set(offsetAt(t));
      spin.set((t / LOOP) * Math.PI * 2);
    } else {
      spin.set(spin.get() + dt * 0.35);
      if (auto && resumeAsked.get() === 0 && dragging.get() === 0 && clock.get() - lastTouch.get() > 2) {
        resumeAsked.set(1);
        scheduleOnRN(goHome);
      }
      if (resumeAsked.get() === 1 && Math.abs(offset.get()) < 0.0005 && clock.get() - lastTouch.get() > 3) {
        resumeAsked.set(0);
        autoStart.set(clock.get());
        // continue the globe from where it is
        mode.set(0);
      }
    }
    // the contents lean against the scroll, and settle like something heavy in liquid
    const v = (offset.get() - prevOffset.get()) / Math.max(dt, 0.001);
    prevOffset.set(offset.get());
    const target = Math.max(-0.5, Math.min(0.5, -v * 0.16));
    lean.set(lean.get() + (target - lean.get()) * Math.min(1, dt * 9));
    const p = Math.round(offset.get());
    if (p !== page.get()) {
      page.set(p);
      scheduleOnRN(setCurrent, p);
      if (mode.get() === 1) scheduleOnRN(tick);
    }
  }, active);

  useEffect(() => {
    mode.set(auto ? 0 : 1);
    autoStart.set(clock.get());
  }, [auto, mode, autoStart, clock]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__glassOrb = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      state: () => JSON.stringify({ mode: mode.get(), offset: +offset.get().toFixed(3), lean: +lean.get().toFixed(3) }),
    };
  }, [pin, mode, offset, lean]);

  /* ------------------------------------------------------------ gestures */
  const step = L.step;
  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    mode.set(1);
  };
  const pan = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .failOffsetY([-14, 14])
    .onBegin(() => {
      takeOver();
      dragging.set(1);
      startOffset.set(offset.get());
      offset.set(offset.get()); // stop any glide where it is
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      let o = startOffset.get() - e.translationX / step;
      if (o < 0) o = -(-o / (1 + -o * 1.6)) * 0.6;
      if (o > LAST) o = LAST + ((o - LAST) / (1 + (o - LAST) * 1.6)) * 0.6;
      offset.set(o);
    })
    .onFinalize((e) => {
      dragging.set(0);
      lastTouch.set(clock.get());
      const v = -e.velocityX / step;
      const projected = offset.get() + v * 0.18;
      const target = Math.max(0, Math.min(LAST, Math.round(projected)));
      offset.set(reduced ? withTiming(target, { duration: 0 }) : withSpring(target, { velocity: v, duration: 420, dampingRatio: 0.82 }));
    });

  const tap = Gesture.Tap().onEnd((e) => {
    takeOver();
    const dx = e.absoluteX - L.orbX;
    const dy = e.absoluteY - L.orbY;
    if (dx * dx + dy * dy < L.orbR * L.orbR * 1.2) {
      pop.set(withSequence(withTiming(1, { duration: 90 }), withSpring(0, { duration: 700, dampingRatio: 0.3 })));
      spin.set(withTiming(spin.get() + Math.PI * 0.6, { duration: 900, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
      scheduleOnRN(bump);
      return;
    }
    // a tap on the peeking card brings it in
    const cur = Math.round(offset.get());
    if (e.absoluteY < L.cardTop) return;
    if (e.absoluteX > L.cardLeft + L.cardW && cur < LAST) {
      offset.set(withSpring(cur + 1, { duration: 480, dampingRatio: 0.85 }));
    } else if (e.absoluteX < L.cardLeft && cur > 0) {
      offset.set(withSpring(cur - 1, { duration: 480, dampingRatio: 0.85 }));
    }
  });
  const gesture = Gesture.Exclusive(pan, tap);

  /* ------------------------------------------------------------ orb */
  const rect = useMemo(
    () => Skia.XYWHRect(L.orbX - L.orbR * 1.3, L.orbY - L.orbR * 1.25, L.orbR * 2.6, L.orbR * 2.6),
    [L],
  );
  const uniforms = useDerivedValue(() => {
    const o = offset.get();
    const frac = o - Math.round(o);
    return {
      uCentre: [L.orbX, L.orbY],
      // the ball breathes a little as the scene inside changes
      uRadius: L.orbR * (1 - 0.03 * Math.sin(Math.min(1, Math.abs(frac) * 2) * Math.PI * 0.5)),
      uPixelRatio: pixelRatio,
      uScene: reduced ? Math.round(Math.max(0, o)) : Math.max(0, Math.min(LAST, o)),
      uSpin: spin.get(),
      uTilt: [lean.get(), 0.12 + lean.get() * 0.3],
      uPop: pop.get(),
      uTime: clock.get() % LOOP,
      uPage: PAGE_RGB,
    };
  });

  const ready = fontsLoaded && width > 0;
  const onLayout = (e: LayoutChangeEvent) => setWidth(e.nativeEvent.layout.width);
  const shelfTop = L.orbY - L.orbR - 8;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {ready && (
        <>
          <Text style={[styles.h1, { top: L.headerTop, left: 24 }]} accessibilityRole="header" maxFontSizeMultiplier={1.3}>
            Featured
          </Text>
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Rect rect={rect}>
              <Shader source={effect!} uniforms={uniforms} />
            </Rect>
          </Canvas>
          <GestureDetector gesture={gesture}>
            <View
              style={[styles.strip, { top: shelfTop, height: L.cardTop + L.cardH + 12 - shelfTop }]}
              accessible
              accessibilityRole="adjustable"
              accessibilityLabel={`${APPS[current].first} ${APPS[current].rest}. ${APPS[current].body}`}
              accessibilityHint="Swipe up or down with one finger to see other apps."
              accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
              onAccessibilityAction={(e) => {
                const next = e.nativeEvent.actionName === 'increment' ? Math.min(LAST, current + 1) : Math.max(0, current - 1);
                offset.set(withTiming(next, { duration: reduced ? 0 : 420, easing: EASE_IN_OUT }));
              }}
            >
              {APPS.map((app, i) => (
                <Card key={i} index={i} app={app} offset={offset} L={L} top={L.cardTop - shelfTop} />
              ))}
            </View>
          </GestureDetector>
          <View style={[styles.dots, { top: L.dotsTop }]} pointerEvents="none">
            {APPS.map((_, i) => (
              <Dot key={i} index={i} offset={offset} />
            ))}
          </View>
        </>
      )}
    </View>
  );
}

type SV = { get: () => number };

function Dot({ index, offset }: { index: number; offset: SV }) {
  const style = useAnimatedStyle(() => {
    const k = Math.max(0, 1 - Math.abs(index - offset.get()));
    return { width: 6 + 12 * k, opacity: 0.35 + 0.65 * k };
  });
  return <Animated.View style={[styles.dot, style]} />;
}

function Card({ index, app, offset, L, top }: { index: number; app: (typeof APPS)[number]; offset: SV; L: ReturnType<typeof makeLayout>; top: number }) {
  const style = useAnimatedStyle(() => {
    const d = index - offset.get();
    return { opacity: 1 - 0.45 * Math.min(1, Math.abs(d)), transform: [{ translateX: d * L.step }, { scale: 1 - 0.06 * Math.min(1, Math.abs(d)) }] };
  });
  return (
    <Animated.View
      style={[styles.card, { left: L.cardLeft, top, width: L.cardW, height: L.cardH, borderRadius: L.radius }, style]}
      importantForAccessibility="no-hide-descendants"
      accessibilityElementsHidden
    >
      <Text style={styles.title} maxFontSizeMultiplier={1.2}>
        <Text style={{ color: app.tint }}>{app.first}</Text> {app.rest}
      </Text>
      <Text style={styles.body} maxFontSizeMultiplier={1.25} numberOfLines={3}>
        {app.body}
      </Text>
      <View style={styles.cardRow}>
        <Text style={styles.meta}>Mini app · Free</Text>
        <View style={styles.pill}>
          <Text style={styles.pillText}>Open</Text>
        </View>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.page, overflow: 'hidden' },
  h1: { position: 'absolute', fontFamily: FONT.bold, fontSize: 28, letterSpacing: -0.6, color: COLOR.ink },
  strip: { position: 'absolute', left: 0, right: 0 },
  card: {
    position: 'absolute',
    backgroundColor: COLOR.card,
    borderWidth: 1,
    borderColor: COLOR.cardEdge,
    padding: 20,
    gap: 8,
  },
  title: { fontFamily: FONT.semibold, fontSize: 22, lineHeight: 26, letterSpacing: -0.45, color: COLOR.ink },
  body: { fontFamily: FONT.regular, fontSize: 15, lineHeight: 20, color: COLOR.body },
  cardRow: { marginTop: 'auto', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  meta: { fontFamily: FONT.regular, fontSize: 13, color: COLOR.mute },
  pill: { height: 36, paddingHorizontal: 18, borderRadius: 18, backgroundColor: COLOR.pill, alignItems: 'center', justifyContent: 'center' },
  pillText: { fontFamily: FONT.semibold, fontSize: 15, color: COLOR.pillInk },
  dots: { position: 'absolute', left: 0, right: 0, height: 6, flexDirection: 'row', justifyContent: 'center', gap: 6 },
  dot: { height: 6, borderRadius: 3, backgroundColor: COLOR.ink },
});
