import { Canvas, Circle, Group, RoundedRect, useFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { Easing, useDerivedValue, useFrameCallback, useSharedValue, withSequence, withSpring, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle, Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { PibGpu } from './gpu/PibGpu';
import { DROPS } from './gpu/pib-wgsl';
import { clamp01, easeInOut, frameAt, LOOP, SLOSH_W, SLOSH_Z } from './pib-motion';
import { makeNotes } from './pib-notes';
import { COLOR, COPY, D, FONT, makeLayout } from './pib-theme';
import { usePibAudio } from './use-pib-audio';

/*
 Perfume Ink Bloom — the detail page of an invented perfume, laid out from the reference pin
 (656470083165924831): back and the bag; a round bottle of blue perfume under a chrome atomiser with a
 ring; its name and kind; reviews and price; a line about it; a heart and Buy now.

 The bottle and its mist are drawn on WebGPU (TypeGPU-resolved WGSL): a glass ellipsoid full of
 perfume, traced analytically, the page's light absorbed along each ray's path through the liquid; a
 chrome collar, cap and ring, sphere-traced. Press the cap: it dips, the atomiser hisses, and 6,144
 droplets (a compute pass) cloud out of the nozzle, swirl, and are drawn onto the perfume's notes,
 which stand spelled in droplets for a few seconds and evaporate. Drag the bottle: it leans under the
 finger and the perfume sloshes after it (a damped spring); let go and it rocks back.

 autoPlay: LOOP = 10 s (pib-motion.ts); frame 10 s equals frame 0, the reference's screen. A touch takes
 over; 2 s after the last one, once the mist has gone, the loop restarts. Reduced motion: no loop and
 no swirl; the notes fade in and out without mist, and the bottle does not lean.
*/

const pixelRatio = PixelRatio.get();
// where on the bottle's spray an age falls (s since the press), as in the loop
const CONDENSE = [1.15, 2.45] as const;
const EVAPORATE = [4.85, 6.05] as const;
const MIST_END = 6.9;

function buzz(kind: 'press' | 'light') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'press') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  else Haptics.selectionAsync().catch(() => {});
}

type Props = { autoPlay?: boolean };

export function PerfumeInkBloom({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Nunito-Regular.ttf'),
    [FONT.semibold]: require('./assets/fonts/Nunito-SemiBold.ttf'),
    [FONT.bold]: require('./assets/fonts/Nunito-Bold.ttf'),
  });
  const noteFont = useFont(require('./assets/fonts/Nunito-Bold.ttf'), 21);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const [gpuOk, setGpuOk] = useState(false);
  const play = usePibAudio(true);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);
  const geo = useMemo(() => {
    const sc = L.sx;
    const cx = D.bottle.x * sc;
    const cy = L.Y(D.bottle.y);
    // the cap's centre on the screen, at rest: up the bottle's axis, leaning clockwise
    const capX = cx + Math.sin(D.bottle.lean) * 138 * sc;
    const capY = cy - Math.cos(D.bottle.lean) * 138 * sc;
    return { sc, cx, cy, capX, capY };
  }, [L]);
  const notes = useMemo(() => (noteFont && size ? makeNotes(L, noteFont, DROPS) : null), [noteFont, size, L]);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const pin = useSharedValue(-1);
  const rate = useSharedValue(1);

  const press = useSharedValue(0);
  const sprayAt = useSharedValue(-100);
  const age = useSharedValue(-1);
  const condense = useSharedValue(0);
  const evaporate = useSharedValue(0);
  const tilt = useSharedValue(0);
  const slosh = useSharedValue(0);
  const sloshV = useSharedValue(0);
  const prevTilt = useSharedValue(0);
  const prevTiltV = useSharedValue(0);
  const leaning = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const fingerPress = useSharedValue(0);
  const frame = useSharedValue<number[]>([]);

  /* ------------------------------------------------------------ actions */
  const spray = () => {
    'worklet';
    press.set(withSequence(withTiming(1, { duration: 120, easing: Easing.out(Easing.cubic) }), withTiming(0, { duration: 260, easing: Easing.out(Easing.cubic) })));
    sprayAt.set(clock.get());
    scheduleOnRN(buzz, 'press');
    scheduleOnRN(play, 'spray');
  };
  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    fingerPress.set(0);
    // a spray of the loop carries on from where it was
    const a = age.get();
    sprayAt.set(a >= 0 ? clock.get() - a : -100);
    tilt.set(withSpring(0, { duration: 700, dampingRatio: 0.45 }));
  };

  /* ------------------------------------------------------------ clock */
  const ticker = useFrameCallback((fi) => {
    const dt = Math.min(0.05, (fi.timeSincePreviousFrame ?? 16) / 1000) * rate.get();
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      press.set(s.press);
      age.set(s.age);
      condense.set(s.condense);
      evaporate.set(s.evaporate);
      tilt.set(s.tilt);
      slosh.set(s.slosh);
      finger.set(s.finger);
      fingerPress.set(s.press2);
      if (s.fingerAt === 0) {
        fingerX.set(geo.capX);
        fingerY.set(geo.capY + 6 * s.press);
      } else {
        fingerX.set(geo.cx + 90 * s.fingerU * geo.sc);
        fingerY.set(geo.cy + 20 * geo.sc);
      }
    } else {
      const a = clock.get() - sprayAt.get();
      const on = a >= 0 && a < MIST_END;
      age.set(on ? (reduced ? 2.6 : a) : -1);
      condense.set(on ? easeInOut(clamp01((a - CONDENSE[0]) / (CONDENSE[1] - CONDENSE[0]))) : 0);
      evaporate.set(on ? clamp01((a - EVAPORATE[0]) / (EVAPORATE[1] - EVAPORATE[0])) : 0);
      // the perfume follows the bottle's lean as a damped spring, driven by its acceleration
      const v = dt > 0 ? (tilt.get() - prevTilt.get()) / dt : 0;
      const acc = dt > 0 ? (v - prevTiltV.get()) / dt : 0;
      prevTilt.set(tilt.get());
      prevTiltV.set(v);
      const sv = sloshV.get() + (-SLOSH_W * SLOSH_W * slosh.get() - 2 * SLOSH_Z * SLOSH_W * sloshV.get() - Math.max(-400, Math.min(400, acc)) * 0.12) * dt;
      sloshV.set(sv);
      slosh.set(reduced ? 0 : slosh.get() + sv * dt);
    }
    frame.set([
      L.w, L.h, pixelRatio, clock.get() % 600,
      geo.cx, geo.cy, geo.sc, tilt.get(),
      slosh.get(), press.get(), D.bottle.lean, 0,
      age.get(), condense.get(), evaporate.get(), 0,
    ]);
    if (mode.get() === 0) return;
    if (!auto || leaning.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    const settled = age.get() < 0 && Math.abs(tilt.get()) < 0.002 && Math.abs(slosh.get()) < 0.004 && press.get() < 0.001;
    if (settled) {
      autoStart.set(clock.get());
      slosh.set(0);
      sloshV.set(0);
      mode.set(0);
    }
  }, false);
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
      for (const v of [press, condense, evaporate, tilt, slosh, sloshV, finger, fingerPress]) v.set(0);
      sprayAt.set(-100);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__perfumeBloom = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      rate: (k: number) => rate.set(k),
      restart: () => {
        autoStart.set(clock.get());
        pin.set(-1);
      },
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), age: +age.get().toFixed(2), condense: +condense.get().toFixed(3), tilt: +tilt.get().toFixed(3), slosh: +slosh.get().toFixed(3) }),
    };
  }, [pin, rate, autoStart, clock, mode, age, condense, tilt, slosh]);

  /* ------------------------------------------------------------ gestures */
  // a tap on the bottle sprays; a drag leans it, and the perfume sloshes after it
  const lean = Gesture.Pan()
    .minDistance(6)
    .onBegin(() => {
      takeOver();
    })
    .onStart(() => {
      if (reduced) return;
      leaning.set(1);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      if (leaning.get() === 0) return;
      // it gives less and less the further it leans
      const x = e.translationX / (160 * geo.sc);
      tilt.set(0.38 * Math.tanh(x));
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      if (leaning.get() === 1) {
        leaning.set(0);
        if (Math.abs(tilt.get()) > 0.08) scheduleOnRN(play, 'slosh');
        // it rocks back upright carrying the finger's speed: d(tilt)/dt of 0.38 · tanh(x / 160)
        const u = 160 * geo.sc;
        const sech = 1 / Math.cosh(e.translationX / u);
        tilt.set(withSpring(0, { duration: 900, dampingRatio: 0.35, velocity: ((0.38 * sech * sech) / u) * e.velocityX }));
      }
    });
  const tap = Gesture.Tap()
    .maxDistance(10)
    .onEnd(() => {
      takeOver();
      spray();
    });
  const bottleTouch = Gesture.Exclusive(lean, tap);

  /* ------------------------------------------------------------ drawing */
  const fingerR = useDerivedValue(() => 22 * (1 - 0.12 * fingerPress.get()));
  const fingerFill = useDerivedValue(() => `rgba(20,30,60,${0.08 * finger.get()})`);
  const fingerRing = useDerivedValue(() => `rgba(20,30,60,${0.3 * finger.get()})`);
  const flatTilt = useDerivedValue(() => [{ rotate: D.bottle.lean + tilt.get() }]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };
  const R = D.bottle.r * geo.sc;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      {/* the flat bottle, while WebGPU is not up */}
      {!gpuOk && (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Group origin={{ x: geo.cx, y: geo.cy }} transform={flatTilt}>
            <RoundedRect x={geo.cx - 33 * geo.sc} y={geo.cy - 190 * geo.sc} width={66 * geo.sc} height={90 * geo.sc} r={12} color="#C9CDD6" />
            <Circle cx={geo.cx} cy={geo.cy} r={R} color="#5CCBF2" />
          </Group>
        </Canvas>
      )}
      {size && <PibGpu frame={frame} notes={notes} width={L.w} height={L.h} disabled={false} onAvailable={setGpuOk} />}

      <GestureDetector gesture={bottleTouch}>
        <View
          style={[styles.abs, { left: geo.cx - R - 10, top: geo.capY - 70 * geo.sc, width: R * 2 + 120 * geo.sc, height: geo.cy + R - geo.capY + 80 * geo.sc }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel={`${COPY.name}. Tap to spray it`}
          accessibilityHint="Drag sideways to tilt the bottle"
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={() =>
            scheduleOnUI(() => {
              'worklet';
              takeOver();
              spray();
            })
          }
        />
      </GestureDetector>

      <PressScale style={[styles.icon, { left: D.back.x * L.sx - 22, top: L.Y(D.back.y) - 22 }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Back">
        <Svg width={22} height={18} viewBox="0 0 22 18">
          <SvgPath d="M9 2 L2 9 L9 16 M2.6 9 H20.5" stroke={COLOR.ink} strokeWidth={1.9} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </Svg>
      </PressScale>
      <PressScale style={[styles.icon, { left: D.bag.x * L.sx - 22, top: L.Y(D.bag.y) - 22 }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Bag, 1 item">
        <Svg width={22} height={24} viewBox="0 0 22 24">
          <SvgPath d="M3.5 7.5 H16.5 V21 H3.5 Z M6.8 7.5 V5.6 A3.2 3.2 0 0 1 13.2 5.6 V7.5" stroke={COLOR.ink} strokeWidth={1.7} fill="none" strokeLinejoin="round" />
          <SvgCircle cx={17} cy={4.5} r={3.6} fill={COLOR.badge} />
        </Svg>
      </PressScale>

      {fontsLoaded && (
        <>
          <Text style={[styles.name, { left: D.name.x * L.sx, top: L.Y(D.name.y) - 21 }]} allowFontScaling={false} accessibilityRole="header">
            {COPY.name}
          </Text>
          <Text style={[styles.kind, { left: D.kind.x * L.sx, top: L.Y(D.kind.y) - 14 }]} allowFontScaling={false}>
            {COPY.kind}
          </Text>
          <Text style={[styles.label, { left: D.reviews.x * L.sx, top: L.Y(D.reviews.y) - 12 }]} allowFontScaling={false}>
            {COPY.reviews}
          </Text>
          <View style={[styles.stars, { left: D.stars.x * L.sx, top: L.Y(D.stars.y) - D.stars.size / 2 }]} accessible accessibilityLabel="4 out of 5 stars">
            {[0, 1, 2, 3, 4].map((i) => (
              <Svg key={i} width={D.stars.size} height={D.stars.size} viewBox="0 0 24 24" style={{ marginRight: D.stars.gap - D.stars.size }}>
                <SvgPath d="M12 1.8 L15.1 8.3 L22.2 9.2 L17 14.1 L18.3 21.2 L12 17.8 L5.7 21.2 L7 14.1 L1.8 9.2 L8.9 8.3 Z" fill={i < 4 ? COLOR.star : COLOR.starOff} />
              </Svg>
            ))}
          </View>
          <Text style={[styles.label, { left: D.price.x * L.sx, top: L.Y(D.price.y) - 12 }]} allowFontScaling={false}>
            {COPY.price}
          </Text>
          <Text style={[styles.amount, { left: D.amount.x * L.sx, top: L.Y(D.amount.y) - 15 }]} allowFontScaling={false}>
            {COPY.amount}
          </Text>
          <Text style={[styles.body, { left: D.body.x * L.sx, top: L.Y(D.body.y) - D.body.line / 2, width: D.body.w * L.sx }]} allowFontScaling={false}>
            {COPY.body}
          </Text>
        </>
      )}
      <PressScale style={[styles.heart, { left: D.heart.x * L.sx, top: L.Y(D.heart.y), width: D.heart.w * L.sx, height: D.heart.h * L.k }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Save">
        <Svg width={22} height={20} viewBox="0 0 22 20">
          <SvgPath d="M11 18.2 C 4 13.4 1.6 10.2 1.6 6.6 A 4.8 4.8 0 0 1 11 4.6 A 4.8 4.8 0 0 1 20.4 6.6 C 20.4 10.2 18 13.4 11 18.2 Z" stroke={COLOR.heart} strokeWidth={1.6} fill="none" strokeLinejoin="round" />
        </Svg>
      </PressScale>
      <PressScale style={[styles.buy, { left: D.buy.x * L.sx, top: L.Y(D.buy.y), width: D.buy.w * L.sx, height: D.buy.h * L.k }]} scale={0.97} reduced={reduced} accessibilityRole="button" accessibilityLabel={COPY.buy}>
        {fontsLoaded && (
          <Text style={styles.buyText} allowFontScaling={false}>
            {COPY.buy}
          </Text>
        )}
      </PressScale>

      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerFill} />
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerRing} style="stroke" strokeWidth={1.5} />
      </Canvas>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.page, overflow: 'hidden' },
  abs: { position: 'absolute' },
  icon: { position: 'absolute', width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  name: { position: 'absolute', fontFamily: FONT.semibold, fontSize: D.name.size, color: COLOR.ink },
  kind: { position: 'absolute', fontFamily: FONT.regular, fontSize: D.kind.size, color: COLOR.ink },
  label: { position: 'absolute', fontFamily: FONT.semibold, fontSize: D.reviews.size, color: COLOR.label },
  stars: { position: 'absolute', flexDirection: 'row' },
  amount: { position: 'absolute', fontFamily: FONT.bold, fontSize: D.amount.size, color: COLOR.ink },
  body: { position: 'absolute', fontFamily: FONT.regular, fontSize: D.body.size, lineHeight: D.body.line, color: COLOR.body },
  heart: { position: 'absolute', borderRadius: D.heart.r, borderWidth: 1.2, borderColor: COLOR.heart, alignItems: 'center', justifyContent: 'center' },
  buy: { position: 'absolute', borderRadius: D.buy.r, backgroundColor: COLOR.cta, alignItems: 'center', justifyContent: 'center' },
  buyText: { fontFamily: FONT.semibold, fontSize: 19, color: COLOR.ctaInk },
});
