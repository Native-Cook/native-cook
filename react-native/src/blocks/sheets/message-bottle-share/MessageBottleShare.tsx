import { Canvas, Circle, Rect, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { cubicBezier, Easing, useAnimatedReaction, useDerivedValue, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { bob, driftAt, DRIFT, flightAt, FLIGHT, frameAt, LOOP, REST_ANGLE, riseAt } from './mbs-motion';
import { MBS_SKSL } from './mbs-shader';
import { BAR_H, COLOR, COPY, CTA_H, FONT, makeLayout, SPACE } from './mbs-theme';
import { makeUniforms } from './mbs-uniforms';

/*
 Message Bottle Share — sending a letter in an invented journaling app, as a message in a bottle.

 The screen: Cancel; "Cast your letter" in a serif and who it is for; a dawn sea with the sun on the
 horizon and, floating at the shore, a glass bottle with the letter rolled inside, tied with red twine;
 Cast Off, and a hint that the bottle can be thrown.

 Signature: the bottle is the send button. Take it and throw it towards the horizon: it leaves the
 water, turns over in a high arc and shrinks with the distance, lands with a ring of ripples near the
 sun and drifts away; "On its way". Cast Off throws it for you. Write Another brings a new bottle up
 out of the water at the shore. The bottle bobs on the swell; its glass bends the sea behind it and
 the part under the water line is darker.

 autoPlay: LOOP = 10 s (a throw, the landing and drift, Write Another, a new bottle rising); the swell
 and the glitter repeat a whole number of times per loop, so frame 10 s equals frame 0. A touch takes
 over; 2 s after the last one, with a bottle at the shore, the loop comes round again. Reduced motion:
 no loop and no flight; the bottle fades out and the next fades in.
*/

const effect = Skia.RuntimeEffect.Make(MBS_SKSL);
if (!effect) throw new Error('Message Bottle Share: the shader failed to compile.');
const pixelRatio = PixelRatio.get();
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const RISE = 1.4;

function lift() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}
function thrown() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}
function landed() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function MessageBottleShare({ autoPlay = false }: Props) {
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
  const lastTouch = useSharedValue(-100);
  const pin = useSharedValue(-1);
  // the sea's own time runs on in every mode, so the swell never jumps
  const seaT = useSharedValue(0);
  const prevT = useSharedValue(0);

  // the bottle as drawn
  const bx = useSharedValue(0);
  const by = useSharedValue(0);
  const ba = useSharedValue(REST_ANGLE);
  const bs = useSharedValue(1);
  const bf = useSharedValue(1);
  const bal = useSharedValue(1);
  const splash = useSharedValue(-1);
  const sent = useSharedValue(0);
  // touch mode: 0 floating, 1 held, 2 flying, 3 drifting, 4 gone, 5 rising
  const phase = useSharedValue(0);
  const phaseAt = useSharedValue(0);
  const fromX = useSharedValue(0);
  const fromY = useSharedValue(0);
  const fromA = useSharedValue(REST_ANGLE);
  const dragX = useSharedValue(0);
  const dragY = useSharedValue(0);
  const held = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const fingerPress = useSharedValue(0);
  const [isSent, setSent] = useState(false);

  const setBottle = (b: { x: number; y: number; angle: number; scale: number; float: number; alpha: number }) => {
    'worklet';
    bx.set(b.x);
    by.set(b.y);
    ba.set(b.angle);
    bs.set(b.scale);
    bf.set(b.float);
    bal.set(b.alpha);
  };
  const go = (p: number) => {
    'worklet';
    phase.set(p);
    phaseAt.set(clock.get());
  };
  const throwFrom = (x: number, y: number, a: number) => {
    'worklet';
    fromX.set(x);
    fromY.set(y);
    fromA.set(a);
    scheduleOnRN(thrown);
    if (reduced) {
      bal.set(withTiming(0, { duration: 200 }));
      go(4);
      sent.set(1);
      return;
    }
    go(2);
  };
  const again = () => {
    'worklet';
    sent.set(0);
    splash.set(-1);
    if (reduced) {
      setBottle({ x: L.homeX, y: L.homeY, angle: REST_ANGLE, scale: L.S, float: 1, alpha: 0 });
      bal.set(withTiming(1, { duration: 200 }));
      go(0);
      return;
    }
    go(5);
  };

  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    if (mode.get() === 0) {
      mode.set(1);
      finger.set(withTiming(0, { duration: 150 }));
      fingerPress.set(0);
      // carry on from the loop's moment: floating, gone, or a new bottle rising
      if (sent.get() === 1 || (bs.get() < L.S * 0.5 && bal.get() < 0.99)) {
        go(4);
        sent.set(1);
        bal.set(0);
      } else if (bf.get() > 0.5) go(0);
      else go(4);
    }
  };

  /* ------------------------------------------------------------ clock */
  const ticker = useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    prevT.set(seaT.get());
    if (pin.get() >= 0) seaT.set(pin.get());
    else if (!reduced) seaT.set((seaT.get() + dt) % LOOP);
    const t = seaT.get();
    if (mode.get() === 0) {
      const s = frameAt(t, L);
      setBottle(s);
      splash.set(s.splash);
      sent.set(s.sent);
      finger.set(s.finger);
      fingerX.set(s.fingerX);
      fingerY.set(s.fingerY);
      fingerPress.set(s.fingerPress);
      return;
    }
    const p = phase.get();
    const tau = clock.get() - phaseAt.get();
    const b = bob(t);
    if (p === 0) setBottle({ x: L.homeX, y: L.homeY + b.dy, angle: REST_ANGLE + b.da, scale: L.S, float: 1, alpha: bal.get() });
    else if (p === 1) setBottle({ x: L.homeX + dragX.get(), y: L.homeY + b.dy + dragY.get(), angle: REST_ANGLE + b.da + dragX.get() * 0.002, scale: L.S, float: Math.max(0, 1 + dragY.get() / 40), alpha: 1 });
    else if (p === 2) {
      setBottle(flightAt(L, fromX.get(), fromY.get(), fromA.get(), Math.min(1, tau / FLIGHT)));
      if (tau >= FLIGHT) {
        go(3);
        scheduleOnRN(landed);
        sent.set(1);
      }
    } else if (p === 3) {
      setBottle(driftAt(L, tau));
      splash.set(tau);
      if (tau >= DRIFT + 0.6) go(4);
    } else if (p === 4) {
      splash.set(-1);
      if (!reduced) bal.set(0);
    } else if (p === 5) {
      setBottle(riseAt(L, t, Math.min(1, tau / RISE)));
      if (tau >= RISE) go(0);
    }
    // back to the loop: a bottle at the shore, 2 s since the last touch, and the sea's loop coming round
    if (auto && phase.get() === 0 && clock.get() - lastTouch.get() > 2 && t < prevT.get()) mode.set(0);
  }, false);
  // autostart only counts at mount, and a block opened from a link mounts while the app is inactive
  useEffect(() => {
    ticker.setActive(active);
  }, [ticker, active]);

  useEffect(() => {
    if (auto) {
      mode.set(0);
      return;
    }
    scheduleOnUI(() => {
      'worklet';
      // settle where the loop was: a bottle at the shore, or the letter sent
      mode.set(1);
      finger.set(0);
      fingerPress.set(0);
      if (sent.get() === 1 || bf.get() < 0.5) {
        sent.set(1);
        bal.set(0);
        go(4);
      } else {
        bal.set(1);
        go(0);
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__messageBottle = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), phase: phase.get(), t: +seaT.get().toFixed(2), sent: sent.get() }),
    };
  }, [pin, clock, mode, phase, seaT, sent]);

  useAnimatedReaction(
    () => sent.get(),
    (now, before) => {
      if (now !== before) scheduleOnRN(setSent, now === 1);
    },
  );

  /* ------------------------------------------------------------ gestures */
  const drag = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      takeOver();
      const p = phase.get();
      if (p !== 0 && p !== 1) return;
      // a bottle still floating home can be taken again where it is
      if (p === 0) {
        dragX.set(0);
        dragY.set(0);
      } else {
        dragX.set(dragX.get());
        dragY.set(dragY.get());
      }
      held.set(1);
      go(1);
      scheduleOnRN(lift);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      if (held.get() !== 1) return;
      dragX.set(e.translationX);
      dragY.set(Math.min(e.translationY, 30));
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      if (held.get() !== 1) return;
      held.set(0);
      if (e.velocityY < -500 || e.translationY < -70) throwFrom(bx.get(), by.get(), ba.get());
      else {
        // let go short of a throw: it floats home, then is the floating bottle again
        dragX.set(withTiming(0, { duration: 350, easing: EASE_OUT }));
        dragY.set(withTiming(0, { duration: 350, easing: EASE_OUT }));
      }
    });

  useAnimatedReaction(
    () => phase.get() === 1 && held.get() === 0 && Math.abs(dragX.get()) < 0.5 && Math.abs(dragY.get()) < 0.5,
    (home) => {
      if (home) phase.set(0);
    },
  );

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => makeUniforms(L, { x: bx.get(), y: by.get(), angle: ba.get(), scale: bs.get(), float: bf.get(), alpha: bal.get(), splash: splash.get(), t: seaT.get() }, pixelRatio));
  const fingerR = useDerivedValue(() => 22 * (1 - 0.12 * fingerPress.get()));
  const fingerFill = useDerivedValue(() => `rgba(42,34,56,${0.12 * finger.get()})`);
  const fingerRing = useDerivedValue(() => `rgba(255,255,255,${0.6 * finger.get()})`);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };
  const swap = (on: boolean) => ({
    opacity: on ? 1 : 0,
    transform: [{ translateY: on ? 0 : 6 }],
    transitionProperty: ['opacity', 'transform'] as ['opacity', 'transform'],
    transitionDuration: 320,
    transitionDelay: on ? 120 : 0,
    transitionTimingFunction: CSS_EASE_OUT,
  });

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={w} height={h}>
          <Shader source={effect!} uniforms={uniforms} />
        </Rect>
      </Canvas>

      <GestureDetector gesture={drag}>
        <View
          style={[styles.bottle, { left: L.homeX - L.S * 1.3, top: L.homeY - L.S * 0.8, width: L.S * 2.6, height: L.S * 1.6 }]}
          pointerEvents={isSent ? 'none' : 'auto'}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Bottle with your letter"
          accessibilityHint="Throws the bottle out to sea and sends the letter"
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={() =>
            scheduleOnUI(() => {
              'worklet';
              takeOver();
              if (phase.get() === 0) throwFrom(bx.get(), by.get(), ba.get());
            })
          }
        />
      </GestureDetector>

      <View style={[styles.bar, { top: L.top }]}>
        <PressScale style={styles.cancel} reduced={reduced} accessibilityRole="button" accessibilityLabel="Cancel" hitSlop={4}>
          {fontsLoaded && (
            <Text style={styles.cancelText} allowFontScaling={false}>
              Cancel
            </Text>
          )}
        </PressScale>
      </View>

      {fontsLoaded && (
        <>
          <View style={[styles.head, { top: L.titleTop }]}>
            <Animated.View style={[styles.headBlock, swap(!isSent)]} accessible={!isSent}>
              <Text style={styles.title} allowFontScaling={false} accessibilityRole="header">
                {COPY.title}
              </Text>
              <Text style={styles.sub} allowFontScaling={false}>
                {COPY.sub}
              </Text>
            </Animated.View>
            <Animated.View style={[styles.headBlock, swap(isSent)]} accessible={isSent}>
              <Text style={styles.title} allowFontScaling={false} accessibilityRole="header">
                {COPY.sent}
              </Text>
              <Text style={styles.sub} allowFontScaling={false}>
                {COPY.sentSub}
              </Text>
            </Animated.View>
          </View>

          <PressScale
            style={[styles.cta, { top: L.ctaTop }]}
            reduced={reduced}
            accessibilityRole="button"
            accessibilityLabel={isSent ? COPY.again : COPY.cta}
            onPress={() =>
              scheduleOnUI(() => {
                'worklet';
                takeOver();
                if (phase.get() === 0) throwFrom(bx.get(), by.get(), ba.get());
                else if (phase.get() === 4) again();
              })
            }
          >
            <Svg width={18} height={18} viewBox="0 0 18 18">
              <SvgPath d={isSent ? 'M9 3 V15 M3 9 H15' : 'M3 15 L15 3 M7 3 H15 V11'} stroke={COLOR.ctaInk} strokeWidth={1.8} fill="none" strokeLinecap="round" strokeLinejoin="round" />
            </Svg>
            <Text style={styles.ctaText} allowFontScaling={false}>
              {isSent ? COPY.again : COPY.cta}
            </Text>
          </PressScale>
          <Animated.Text style={[styles.hint, { top: L.hintTop }, swap(!isSent)]} allowFontScaling={false} pointerEvents="none">
            {COPY.hint}
          </Animated.Text>
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
  screen: { flex: 1, backgroundColor: '#C9C3E2', overflow: 'hidden' },
  bottle: { position: 'absolute' },
  bar: { position: 'absolute', left: SPACE.s, right: SPACE.s, height: BAR_H, flexDirection: 'row', alignItems: 'center' },
  cancel: { height: 44, justifyContent: 'center', paddingHorizontal: SPACE.s },
  cancelText: { fontFamily: FONT.regular, fontSize: 17, color: COLOR.ink },
  head: { position: 'absolute', left: SPACE.xl, right: SPACE.xl, height: 76 },
  headBlock: { position: 'absolute', left: 0, right: 0, top: 0 },
  title: { fontFamily: FONT.display, fontSize: 42, lineHeight: 48, color: COLOR.ink },
  sub: { fontFamily: FONT.regular, fontSize: 14, lineHeight: 20, color: COLOR.mute, marginTop: 2 },
  cta: { position: 'absolute', left: SPACE.xl, right: SPACE.xl, height: CTA_H, borderRadius: CTA_H / 2, backgroundColor: COLOR.cta, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: SPACE.s },
  ctaText: { fontFamily: FONT.semibold, fontSize: 16, color: COLOR.ctaInk },
  hint: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.regular, fontSize: 13, lineHeight: 20, color: 'rgba(255,255,255,0.82)' },
});
