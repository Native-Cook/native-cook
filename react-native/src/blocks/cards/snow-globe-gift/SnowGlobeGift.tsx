import { Canvas, Circle, Rect, Shader, Skia } from '@shopify/react-native-skia';
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
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { frameAt, LOOP } from './sgg-motion';
import { SGG_SKSL } from './sgg-shader';
import { BAR_H, COLOR, COPY, CTA_H, FONT, makeLayout, SPACE } from './sgg-theme';
import { makeUniforms } from './sgg-uniforms';

/*
 Snow Globe Gift — a gift received in an invented wallet, Kinto.

 The screen: back; a glass snow globe on a gold base, a small gift box inside on a mound of snow;
 "From Mira", "A gift for you" and her note; Open Gift.

 Signature: the globe is a real snow globe. Shake it with a finger and it rocks on its base while the
 snow lifts into the water and swirls (faster the harder you shake), then settles slowly over the
 box. Open Gift pops the box's lid inside the globe, it glows, and the amount rises out of it.

 autoPlay: LOOP = 10 s (a shake, the snow settling, Open, the amount, the lid shut); frame 10 s equals
 frame 0. A touch takes over; 2 s after the last one the lid shuts, the snow settles and the loop
 restarts. Reduced motion: no loop, no rocking and no snow in the water; the lid opens in 250 ms.
*/

const effect = Skia.RuntimeEffect.Make(SGG_SKSL);
if (!effect) throw new Error('Snow Globe Gift: the shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const NEVER = ReduceMotion.Never;

function buzz(kind: 'select' | 'light' | 'success') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'select') Haptics.selectionAsync().catch(() => {});
  else if (kind === 'success') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function SnowGlobeGift({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsLoaded] = useFonts({
    [FONT.display]: require('./assets/fonts/InstrumentSerif-Regular.ttf'),
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

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);

  const wobble = useSharedValue(0);
  const wobbleV = useSharedValue(0);
  const swirl = useSharedValue(0);
  const spin = useSharedValue(0);
  const lid = useSharedValue(0);
  const glow = useSharedValue(0);
  const amount = useSharedValue(0);
  const dragging = useSharedValue(0);
  const prevX = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const fingerPress = useSharedValue(0);
  const [opened, setOpened] = useState(false);
  const [added, setAdded] = useState(false);

  /* ------------------------------------------------------------ the globe */
  const setOpen = (on: boolean) => {
    'worklet';
    const cfg = reduced ? { duration: 250, reduceMotion: NEVER } : { duration: on ? 500 : 600, easing: on ? EASE_OUT : Easing.bezier(0.77, 0, 0.175, 1) };
    lid.set(withTiming(on ? 1 : 0, cfg));
    glow.set(withTiming(on ? 1 : 0, cfg));
    amount.set(withTiming(on ? 1 : 0, reduced ? cfg : { duration: on ? 450 : 250, easing: EASE_OUT }));
    scheduleOnRN(setOpened, on);
  };

  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() === 0) {
      mode.set(1);
      finger.set(withTiming(0, { duration: 150 }));
      fingerPress.set(0);
      wobbleV.set(0);
      const l = lid.get();
      if (l > 0.001 && l < 0.999) setOpen(l > 0.5);
    }
  };

  /* ------------------------------------------------------------ clock and physics */
  const ticker = useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      wobble.set(s.wobble);
      swirl.set(s.swirl);
      spin.set(s.spin);
      lid.set(s.lid);
      glow.set(s.glow);
      amount.set(s.amount);
      finger.set(s.finger);
      fingerPress.set(s.fingerPress);
      if (s.fingerAt === 0) {
        fingerX.set(L.heroX + s.fingerX * L.heroS);
        fingerY.set(L.heroY + 0.1 * L.heroS);
      } else {
        fingerX.set(L.w / 2);
        fingerY.set(L.ctaTop + CTA_H / 2);
      }
      return;
    }
    if (!reduced) {
      // the globe rocks back on its base; the water keeps turning and the snow falls
      if (dragging.get() === 0) {
        const a = -90 * wobble.get() - 2 * 0.22 * 9.5 * wobbleV.get();
        wobbleV.set(wobbleV.get() + a * dt);
        wobble.set(wobble.get() + wobbleV.get() * dt);
      }
      swirl.set(swirl.get() * Math.exp(-dt / 1.3));
      if (swirl.get() < 0.002) swirl.set(0);
      spin.set(spin.get() + dt * 6 * swirl.get());
    }
    if (!auto || dragging.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      if (lid.get() > 0.001) setOpen(false);
      return;
    }
    if (lid.get() < 0.001 && swirl.get() === 0 && Math.abs(wobble.get()) < 0.003 && Math.abs(wobbleV.get()) < 0.03) {
      resumeAsked.set(0);
      wobble.set(0);
      autoStart.set(clock.get());
      mode.set(0);
    }
  }, false);
  // autostart only counts at mount, and a block opened from a link mounts while the app is inactive
  useEffect(() => {
    ticker.setActive(active);
  }, [ticker, active]);

  useEffect(() => {
    autoStart.set(clock.get());
    if (auto) {
      mode.set(0);
      return;
    }
    scheduleOnUI(() => {
      'worklet';
      mode.set(1);
      const on = lid.get() > 0.5;
      const cfg = { duration: 200, reduceMotion: NEVER };
      lid.set(withTiming(on ? 1 : 0, cfg));
      glow.set(withTiming(on ? 1 : 0, cfg));
      amount.set(withTiming(on ? 1 : 0, cfg));
      for (const v of [wobble, wobbleV, swirl, finger, fingerPress]) v.set(withTiming(0, cfg));
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__snowGlobe = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), swirl: +swirl.get().toFixed(3), wobble: +wobble.get().toFixed(3), lid: +lid.get().toFixed(3) }),
    };
  }, [pin, clock, mode, swirl, wobble, lid]);

  useAnimatedReaction(
    () => lid.get() > 0.5,
    (now, before) => {
      if (before !== null && now !== before) scheduleOnRN(setOpened, now);
    },
  );

  /* ------------------------------------------------------------ gestures */
  const shake = Gesture.Pan()
    .minDistance(2)
    .onBegin(() => {
      takeOver();
    })
    .onStart((e) => {
      if (reduced) return;
      dragging.set(1);
      prevX.set(e.translationX);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      if (dragging.get() === 0) return;
      // the globe leans with the finger; its speed stirs the water
      const lean = Math.max(-0.3, Math.min(0.3, e.translationX / (L.heroS * 3)));
      const prev = wobble.get();
      wobble.set(lean);
      wobbleV.set((lean - prev) * 60);
      const dx = Math.abs(e.translationX - prevX.get());
      prevX.set(e.translationX);
      swirl.set(Math.min(1, swirl.get() + dx * 0.004));
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      if (dragging.get() === 0) return;
      dragging.set(0);
      wobbleV.set(Math.max(-3, Math.min(3, e.velocityX / (L.heroS * 3))));
      if (swirl.get() > 0.3) scheduleOnRN(buzz, 'light');
    });
  const tap = Gesture.Tap().onEnd(() => {
    takeOver();
    if (reduced) return;
    wobbleV.set(wobbleV.get() + 1.4);
    swirl.set(Math.min(1, swirl.get() + 0.35));
    scheduleOnRN(buzz, 'light');
  });
  const globe = Gesture.Exclusive(shake, tap);

  const onCta = useCallback(() => {
    if (!opened) {
      scheduleOnUI(() => {
        'worklet';
        takeOver();
        setOpen(true);
      });
      buzz('success');
    } else {
      setAdded(true);
      buzz('success');
      setTimeout(() => setAdded(false), 1800);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [opened, reduced]);

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() =>
    makeUniforms(L, { wobble: wobble.get(), swirl: swirl.get(), spin: spin.get(), lid: lid.get(), glow: glow.get() }, pixelRatio),
  );
  const amountStyle = useAnimatedStyle(() => ({
    opacity: amount.get(),
    transform: [{ translateY: (1 - amount.get()) * 18 }, { scale: 0.9 + 0.1 * amount.get() }],
  }));
  const fingerR = useDerivedValue(() => 22 * (1 - 0.12 * fingerPress.get()));
  const fingerFill = useDerivedValue(() => `rgba(42,29,28,${0.1 * finger.get()})`);
  const fingerRing = useDerivedValue(() => `rgba(42,29,28,${0.32 * finger.get()})`);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={w} height={h}>
          <Shader source={effect!} uniforms={uniforms} />
        </Rect>
      </Canvas>

      <GestureDetector gesture={globe}>
        <View
          style={[styles.stage, { top: L.heroY - L.heroS * 1.05, height: L.heroS * 2.45, left: L.heroX - L.heroS * 1.1, width: L.heroS * 2.2 }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel="A snow globe with a gift inside"
          accessibilityHint="Shake it to stir the snow"
          onAccessibilityTap={() => scheduleOnUI(() => {
            'worklet';
            takeOver();
            if (!reduced) swirl.set(Math.min(1, swirl.get() + 0.6));
          })}
        />
      </GestureDetector>

      {fontsLoaded && (
        <Animated.View style={[styles.amountBox, { top: L.amountY - 40 }, amountStyle]} pointerEvents="none" accessibilityElementsHidden={!opened} importantForAccessibility={opened ? 'auto' : 'no-hide-descendants'}>
          <Text style={styles.amount} allowFontScaling={false}>
            {COPY.amount}
          </Text>
          <Text style={styles.amountNote} allowFontScaling={false}>
            {COPY.amountNote}
          </Text>
        </Animated.View>
      )}

      <View style={[styles.bar, { top: L.barTop }]}>
        <PressScale style={styles.back} reduced={reduced} accessibilityRole="button" accessibilityLabel="Back" hitSlop={4}>
          <Svg width={12} height={20} viewBox="0 0 12 20">
            <SvgPath d="M10 2 L2 10 L10 18" stroke={COLOR.ink} strokeWidth={2.2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
          </Svg>
        </PressScale>
      </View>

      {fontsLoaded && (
        <>
          <Text style={[styles.from, { top: L.fromTop }]} allowFontScaling={false}>
            {COPY.from.toUpperCase()}
          </Text>
          <Text style={[styles.title, { top: L.titleTop }]} allowFontScaling={false} accessibilityRole="header">
            {COPY.title}
          </Text>
          <Text style={[styles.note, { top: L.noteTop }]} allowFontScaling={false}>
            {COPY.note}
          </Text>
          <PressScale style={[styles.cta, { top: L.ctaTop }]} reduced={reduced} onPress={onCta} accessibilityRole="button" accessibilityLabel={opened ? (added ? 'Added to wallet' : COPY.add) : COPY.open}>
            <Text style={styles.ctaText} allowFontScaling={false}>
              {opened ? (added ? 'Added to Wallet' : COPY.add) : COPY.open}
            </Text>
          </PressScale>
        </>
      )}

      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerFill} />
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerRing} style="stroke" strokeWidth={1.5} />
      </Canvas>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.page, overflow: 'hidden' },
  stage: { position: 'absolute' },
  bar: { position: 'absolute', left: SPACE.s, right: SPACE.s, height: BAR_H, flexDirection: 'row', alignItems: 'center' },
  back: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  amountBox: { position: 'absolute', left: 0, right: 0, height: 80, alignItems: 'center', justifyContent: 'center' },
  amount: {
    fontFamily: FONT.display,
    fontSize: 56,
    lineHeight: 60,
    color: COLOR.gold,
    textShadowColor: 'rgba(255,240,210,0.9)',
    textShadowRadius: 14,
    textShadowOffset: { width: 0, height: 0 },
  },
  amountNote: { fontFamily: FONT.semibold, fontSize: 11, letterSpacing: 1, color: COLOR.gold },
  from: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.medium, fontSize: 12, lineHeight: 18, letterSpacing: 1.4, color: COLOR.mute },
  title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.display, fontSize: 36, lineHeight: 42, color: COLOR.ink },
  note: { position: 'absolute', left: 40, right: 40, textAlign: 'center', fontFamily: FONT.regular, fontSize: 15, lineHeight: 22, color: COLOR.mute },
  cta: { position: 'absolute', left: SPACE.l, right: SPACE.l, height: CTA_H, borderRadius: CTA_H / 2, backgroundColor: COLOR.cta, alignItems: 'center', justifyContent: 'center' },
  ctaText: { fontFamily: FONT.semibold, fontSize: 17, color: COLOR.ctaInk },
});
