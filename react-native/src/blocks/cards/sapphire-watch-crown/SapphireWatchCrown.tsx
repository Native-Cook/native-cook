import { Canvas, Circle, RadialGradient, vec } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle, Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { SwcGpu } from './gpu/SwcGpu';
import { frameAt, HOURS_PER_PT, LOOP, REST_HOURS } from './swc-motion';
import { COLOR, COPY, D, FONT, makeLayout, METALS, REST_METAL, SIZES } from './swc-theme';
import { useSwcAudio } from './use-swc-audio';

/*
 Sapphire Watch Crown — the store page of an invented watchmaker, laid out from the reference pin
 (275071489737550382): back and the cart; the watch lying on a disc inside two rings; its name and
 rating; colours and sizes; the price and Buy Online.

 The watch is sphere-traced on WebGPU (TypeGPU-resolved WGSL): case, lugs, a knurled crown and the
 strap, its dial (mother-of-pearl, a date ring, applied indices, a moon phase, blued lance hands) and,
 behind a sapphire back, a movement whose wheels turn, balance swings four times a second and rotor
 goes round. Turn the crown with a finger and the hands wind on, a detent every two minutes. Swipe the
 watch and it turns over about its 12–6 axis to show the movement; swipe again to turn it back. The
 colour swatches recast the case in another metal.

 autoPlay: LOOP = 10 s (swc-motion.ts); frame 10 s equals frame 0, the reference's screen at 10:08. A
 touch takes over; 2 s after the last one the watch turns back to its dial in rose gold at 10:08 and
 the loop restarts. Reduced motion: no loop; the watch turns over and recolours with a short fade.
*/

const pixelRatio = PixelRatio.get();
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
// where the crown sits on the screen, relative to the case's centre, in case radii (the watch's rest
// pose in the shader: laid at 0.72 rad, tipped 0.32 rad)
const CROWN = { x: 0.749, y: -0.655 };

function buzz(kind: 'detent' | 'flip' | 'select') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'detent') Haptics.selectionAsync().catch(() => {});
  else if (kind === 'flip') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function SapphireWatchCrown({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Poppins-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Poppins-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Poppins-SemiBold.ttf'),
  });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const [gpuOk, setGpuOk] = useState(false);
  const play = useSwcAudio(true);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);
  const geo = useMemo(() => {
    const r = D.watch.r * L.sx;
    const cx = D.watch.x * L.sx;
    const cy = L.Y(D.watch.y);
    const sy = L.Y(D.swatches.y);
    return { r, cx, cy, crownX: cx + CROWN.x * r, crownY: cy + CROWN.y * r, swatchRose: { x: D.swatches.xs[1] * L.sx, y: sy }, swatchGold: { x: D.swatches.xs[2] * L.sx, y: sy } };
  }, [L]);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const rate = useSharedValue(1);

  const hours = useSharedValue(REST_HOURS);
  const crown = useSharedValue(0);
  const flip = useSharedValue(0);
  const metalFrom = useSharedValue(REST_METAL);
  const metalTo = useSharedValue(REST_METAL);
  const metalK = useSharedValue(0);
  const turning = useSharedValue(0);
  const hours0 = useSharedValue(REST_HOURS);
  const lastDetent = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerAt = useSharedValue(0);
  const fingerU = useSharedValue(0);
  const fingerPress = useSharedValue(0);
  const frame = useSharedValue<number[]>([]);
  const [swatch, setSwatch] = useState<number>(REST_METAL);
  const [sizeAt, setSizeAt] = useState(1);
  const [back, setBack] = useState(false);

  /* ------------------------------------------------------------ actions */
  const turnOver = (to: number) => {
    'worklet';
    flip.set(withTiming(to, { duration: reduced ? 250 : 850, easing: EASE_IN_OUT }));
    scheduleOnRN(buzz, 'flip');
    if (to > 0.5) scheduleOnRN(play, 'tick');
  };
  const recast = (to: number) => {
    'worklet';
    const now = metalK.get() > 0.5 ? metalTo.get() : metalFrom.get();
    if (now === to && metalK.get() === 0) return;
    metalFrom.set(now);
    metalTo.set(to);
    metalK.set(0);
    metalK.set(
      withTiming(1, { duration: reduced ? 200 : 600, easing: EASE_IN_OUT }, (fin) => {
        if (!fin) return;
        metalFrom.set(to);
        metalK.set(0);
      }),
    );
  };
  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    fingerPress.set(0);
    // the loop's turn and recolour finish where they were going
    const f = flip.get();
    if (f > 0.001 && f < 0.999) flip.set(withTiming(f > 0.5 ? 1 : 0, { duration: 400 }));
    if (metalK.get() > 0) recast(metalK.get() > 0.5 ? metalTo.get() : metalFrom.get());
    hours.set(((hours.get() % 12) + 12) % 12);
  };

  /* ------------------------------------------------------------ clock */
  const ticker = useFrameCallback((fi) => {
    const dt = Math.min(0.05, (fi.timeSincePreviousFrame ?? 16) / 1000) * rate.get();
    clock.set(clock.get() + dt);
    const lt = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
    if (mode.get() === 0) {
      const s = frameAt(lt);
      hours.set(s.hours);
      crown.set(s.crown);
      flip.set(s.flip);
      metalFrom.set(s.metalFrom);
      metalTo.set(s.metalTo);
      metalK.set(s.metalK);
      finger.set(s.finger);
      fingerAt.set(s.fingerAt);
      fingerU.set(s.fingerU);
      fingerPress.set(s.press);
    }
    frame.set([
      L.w, L.h, pixelRatio, clock.get() % 600,
      geo.cx, geo.cy, geo.r, flip.get(),
      ((hours.get() % 12) + 12) % 12, crown.get(), metalFrom.get(), metalTo.get(),
      metalK.get(), mode.get() === 0 ? lt : clock.get() % LOOP, 0, 0,
    ]);
    if (mode.get() === 0) return;
    if (!auto || turning.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      if (flip.get() > 0.001) turnOver(0);
      if ((metalK.get() > 0.5 ? metalTo.get() : metalFrom.get()) !== REST_METAL) recast(REST_METAL);
      // the hands find their way back to 10:08 the short way
      let d = REST_HOURS - (((hours.get() % 12) + 12) % 12);
      if (d > 6) d -= 12;
      if (d < -6) d += 12;
      hours.set(withTiming(hours.get() + d, { duration: 900, easing: EASE_IN_OUT }));
      crown.set(withTiming(crown.get() + d * Math.PI, { duration: 900, easing: EASE_IN_OUT }));
      return;
    }
    const hh = ((hours.get() % 12) + 12) % 12;
    const still = flip.get() < 0.001 && metalK.get() === 0 && metalFrom.get() === REST_METAL && Math.abs(hh - REST_HOURS) < 0.002;
    if (still && clock.get() - lastTouch.get() > 3.2) {
      resumeAsked.set(0);
      autoStart.set(clock.get());
      crown.set(0);
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
      hours.set(REST_HOURS);
      crown.set(0);
      flip.set(0);
      metalFrom.set(REST_METAL);
      metalTo.set(REST_METAL);
      metalK.set(0);
      finger.set(0);
      fingerPress.set(0);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__sapphireWatch = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      rate: (k: number) => rate.set(k),
      restart: () => {
        autoStart.set(clock.get());
        pin.set(-1);
      },
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), hours: +hours.get().toFixed(3), flip: +flip.get().toFixed(3), metal: [metalFrom.get(), metalTo.get(), +metalK.get().toFixed(2)] }),
    };
  }, [pin, rate, autoStart, clock, mode, hours, flip, metalFrom, metalTo, metalK]);

  useAnimatedReaction(
    () => (metalK.get() > 0.5 ? metalTo.get() : metalFrom.get()),
    (now, before) => {
      if (now !== before) scheduleOnRN(setSwatch, now);
    },
  );
  useAnimatedReaction(
    () => flip.get() > 0.5,
    (now, before) => {
      if (now !== before) scheduleOnRN(setBack, now);
    },
  );

  /* ------------------------------------------------------------ gestures */
  // the crown: a slide along it winds the hands, a detent every two minutes
  const wind = Gesture.Pan()
    .minDistance(2)
    .onBegin(() => {
      takeOver();
    })
    .onStart(() => {
      turning.set(1);
      hours0.set(hours.get());
      lastDetent.set(Math.round(hours.get() * 30));
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      const along = (e.translationX - e.translationY) * 0.7;
      hours.set(hours0.get() + along * HOURS_PER_PT);
      crown.set(along * 0.12);
      const det = Math.round(hours.get() * 30);
      if (det !== lastDetent.get()) {
        lastDetent.set(det);
        scheduleOnRN(buzz, 'detent');
        scheduleOnRN(play, 'detent');
      }
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      turning.set(0);
    });
  // a swipe across the watch turns it over
  const over = Gesture.Pan()
    .activeOffsetX([-14, 14])
    .onBegin(() => {
      takeOver();
    })
    .onEnd((e) => {
      if (Math.abs(e.translationX) > 40 || Math.abs(e.velocityX) > 500) turnOver(flip.get() > 0.5 ? 0 : 1);
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
    });

  /* ------------------------------------------------------------ drawing */
  const ringsStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${flip.get() * 180}deg` }] }));
  const fingerPos = useDerivedValue(() => {
    const at = fingerAt.get();
    const u = fingerU.get();
    if (at === 0) return { x: geo.crownX + 14 - 30 * u, y: geo.crownY + 10 + 30 * u };
    if (at === 1) return { x: geo.cx + 70 - 140 * u, y: geo.cy + 40 };
    return at === 2 ? geo.swatchGold : geo.swatchRose;
  });
  const fingerStyle = useAnimatedStyle(() => ({
    opacity: finger.get(),
    transform: [{ translateX: fingerPos.get().x - 22 }, { translateY: fingerPos.get().y - 22 }, { scale: 1 - 0.12 * fingerPress.get() }],
  }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };
  const Y = L.Y;
  const sx = L.sx;
  const ringC = { x: geo.cx, y: L.Y(355) };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {/* the disc and its rings */}
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Circle cx={ringC.x} cy={ringC.y} r={D.rings[0] * sx} color={COLOR.ring} style="stroke" strokeWidth={1} />
        <Circle cx={ringC.x} cy={ringC.y} r={D.rings[1] * sx} color="#111113" style="stroke" strokeWidth={1} />
        <Circle cx={ringC.x} cy={ringC.y} r={D.rings[2] * sx}>
          <RadialGradient c={vec(ringC.x - 30, ringC.y - 40)} r={D.rings[2] * sx * 1.3} colors={['#2A2A2E', '#141416']} />
        </Circle>
      </Canvas>
      <Animated.View style={[styles.abs, { left: ringC.x - D.rings[0] * sx, top: ringC.y - D.rings[0] * sx, width: D.rings[0] * 2 * sx, height: D.rings[0] * 2 * sx }, ringsStyle]} pointerEvents="none">
        <Svg width={D.rings[0] * 2 * sx} height={D.rings[0] * 2 * sx} viewBox={`0 0 ${D.rings[0] * 2} ${D.rings[0] * 2}`}>
          <SvgPath d={`M ${175 - 66} ${175 - 171 + 6} l -8 4 l 8 4`} stroke="rgba(255,255,255,0.7)" strokeWidth={1.4} fill="none" strokeLinecap="round" transform={`rotate(-20 ${175 - 66} ${175 - 165})`} />
          <SvgPath d={`M ${175 + 172} ${175 - 66} l 4 -7 l 4 7`} stroke="rgba(255,255,255,0.7)" strokeWidth={1.4} fill="none" strokeLinecap="round" transform={`rotate(-15 ${175 + 176} ${175 - 66})`} />
        </Svg>
      </Animated.View>

      {!gpuOk && (
        <View style={[styles.flatWatch, { left: geo.cx - geo.r, top: geo.cy - geo.r, width: geo.r * 2, height: geo.r * 2, borderRadius: geo.r, borderColor: METALS[swatch].swatch }]} pointerEvents="none" />
      )}
      {size && <SwcGpu frame={frame} width={L.w} height={L.h} disabled={false} onAvailable={setGpuOk} />}

      <GestureDetector gesture={over}>
        <View
          style={[styles.abs, { left: geo.cx - geo.r * 1.2, top: geo.cy - geo.r * 1.2, width: geo.r * 2.4, height: geo.r * 2.4, borderRadius: geo.r * 1.2 }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel={back ? 'The watch from behind, its movement running' : 'The watch. Swipe to turn it over'}
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={() =>
            scheduleOnUI(() => {
              'worklet';
              takeOver();
              turnOver(flip.get() > 0.5 ? 0 : 1);
            })
          }
        />
      </GestureDetector>
      <GestureDetector gesture={wind}>
        <View
          style={[styles.crownHit, { left: geo.crownX - 30, top: geo.crownY - 30 }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Crown. Slide along it to set the time"
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(ev) =>
            scheduleOnUI(() => {
              'worklet';
              takeOver();
              const d = ev.nativeEvent.actionName === 'increment' ? 0.25 : -0.25;
              hours.set(withTiming(hours.get() + d, { duration: 300 }));
            })
          }
        />
      </GestureDetector>

      <PressScale style={[styles.square, { left: D.back.x * sx, top: Y(D.back.y), width: D.back.s, height: D.back.s * L.k }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Back">
        <Svg width={10} height={18} viewBox="0 0 10 18">
          <SvgPath d="M8.5 1.5 L1.5 9 L8.5 16.5" stroke="#FFFFFF" strokeWidth={1.8} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </Svg>
      </PressScale>
      <PressScale style={[styles.square, { left: D.cart.x * sx, top: Y(D.cart.y), width: D.cart.s, height: D.cart.s * L.k }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Cart, 1 item">
        <Svg width={26} height={24} viewBox="0 0 26 24">
          <SvgPath d="M1.5 3 H5 L7.5 15 H19 L21.5 7 H6.2" stroke="#FFFFFF" strokeWidth={1.7} fill="none" strokeLinejoin="round" strokeLinecap="round" />
          <SvgCircle cx={9} cy={19.5} r={1.6} fill="#FFFFFF" />
          <SvgCircle cx={17.5} cy={19.5} r={1.6} fill="#FFFFFF" />
          <SvgCircle cx={21} cy={4} r={3.8} fill="#E6B76B" />
        </Svg>
      </PressScale>

      {fontsLoaded && (
        <>
          <Text style={[styles.title, { left: D.title.x * sx, top: Y(D.title.y) - 13 }]} allowFontScaling={false} accessibilityRole="header">
            {COPY.title}
          </Text>
          <Text style={[styles.sub, { left: D.sub.x * sx, top: Y(D.sub.y) - 9 }]} allowFontScaling={false}>
            {COPY.sub}
          </Text>
          <View style={[styles.nameRow, { left: D.name.x * sx, top: Y(D.name.y) - 15 }]}>
            <Text style={styles.name} allowFontScaling={false}>
              {COPY.name}
            </Text>
            <Text style={styles.made} allowFontScaling={false}>
              {COPY.made}
            </Text>
          </View>
          <Text style={[styles.rating, { right: L.w - D.rating.right * sx, top: Y(D.rating.y) - 14 }]} allowFontScaling={false}>
            {COPY.rating}
          </Text>
          <View style={[styles.hr, { top: Y(D.divider) }]} />
          <View style={[styles.vr, { left: 213 * sx, top: Y(D.divider) + 8, height: Y(720) - Y(D.divider) - 8 }]} />
          <Text style={[styles.label, { left: D.labels.color * sx, top: Y(D.labels.y) - 10 }]} allowFontScaling={false}>
            {COPY.color}
          </Text>
          <Text style={[styles.label, { left: D.labels.sizing * sx, top: Y(D.labels.y) - 10 }]} allowFontScaling={false}>
            {COPY.sizing}
          </Text>
          {METALS.map((m, i) => (
            <Pressable
              key={m.name}
              style={[styles.swatchHit, { left: D.swatches.xs[i] * sx - 20, top: Y(D.swatches.y) - 20 }]}
              accessibilityRole="radio"
              accessibilityLabel={m.name}
              accessibilityState={{ selected: swatch === i }}
              onPress={() => {
                buzz('select');
                scheduleOnUI(() => {
                  'worklet';
                  takeOver();
                  recast(i);
                });
              }}
            >
              {swatch === i && <View style={styles.swatchRing} />}
              <View style={[styles.swatch, { backgroundColor: m.swatch, width: swatch === i ? 26 : 18, height: swatch === i ? 26 : 18, borderRadius: swatch === i ? 13 : 9 }]} />
            </Pressable>
          ))}
          {SIZES.map((s, i) => (
            <Pressable key={s} style={[styles.sizeHit, { left: D.sizes.xs[i] * sx - 22, top: Y(D.sizes.y) - 20 }]} onPress={() => setSizeAt(i)} accessibilityRole="radio" accessibilityState={{ selected: sizeAt === i }} accessibilityLabel={`${s} millimetres`}>
              <Text style={[styles.size, sizeAt === i && styles.sizeOn]} allowFontScaling={false}>
                {s}
              </Text>
            </Pressable>
          ))}
          <View style={[styles.bar, { left: D.bar.x * sx, top: Y(D.bar.y), width: D.bar.w * sx, height: D.bar.h * L.k, borderRadius: D.bar.r }]}>
            <Text style={[styles.price, { width: (D.bar.buyX - D.bar.x) * sx }]} allowFontScaling={false}>
              {COPY.price}
            </Text>
            <PressScale style={[styles.buy, { borderRadius: D.bar.r }]} scale={0.97} reduced={reduced} accessibilityRole="button" accessibilityLabel={COPY.buy}>
              <Text style={styles.buyText} allowFontScaling={false}>
                {COPY.buy}
              </Text>
            </PressScale>
          </View>
        </>
      )}

      <Animated.View style={[styles.finger, fingerStyle]} pointerEvents="none" />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.page, overflow: 'hidden' },
  abs: { position: 'absolute' },
  flatWatch: { position: 'absolute', borderWidth: 8, backgroundColor: '#EEEDEA' },
  crownHit: { position: 'absolute', width: 60, height: 60, borderRadius: 30 },
  square: { position: 'absolute', borderRadius: 16, backgroundColor: COLOR.well, alignItems: 'center', justifyContent: 'center' },
  title: { position: 'absolute', fontFamily: FONT.semibold, fontSize: D.title.size, color: COLOR.ink },
  sub: { position: 'absolute', fontFamily: FONT.regular, fontSize: D.sub.size, color: COLOR.mute },
  nameRow: { position: 'absolute', flexDirection: 'row', alignItems: 'baseline', gap: 8 },
  name: { fontFamily: FONT.semibold, fontSize: D.name.size, color: COLOR.ink },
  made: { fontFamily: FONT.regular, fontSize: 11, color: COLOR.dim },
  rating: { position: 'absolute', fontFamily: FONT.medium, fontSize: D.rating.size, color: COLOR.ink },
  hr: { position: 'absolute', left: 26, right: 26, height: StyleSheet.hairlineWidth, backgroundColor: COLOR.line },
  vr: { position: 'absolute', width: StyleSheet.hairlineWidth, backgroundColor: COLOR.line },
  label: { position: 'absolute', fontFamily: FONT.regular, fontSize: D.labels.size, color: COLOR.mute },
  swatchHit: { position: 'absolute', width: 40, height: 40, alignItems: 'center', justifyContent: 'center' },
  swatchRing: { position: 'absolute', width: 36, height: 36, borderRadius: 18, borderWidth: 1.2, borderColor: 'rgba(255,255,255,0.75)' },
  swatch: {},
  sizeHit: { position: 'absolute', width: 44, height: 40, alignItems: 'center', justifyContent: 'center' },
  size: { fontFamily: FONT.regular, fontSize: 11.5, color: COLOR.dim },
  sizeOn: { fontFamily: FONT.semibold, fontSize: 13, color: COLOR.ink },
  bar: { position: 'absolute', backgroundColor: COLOR.well, flexDirection: 'row', alignItems: 'stretch', overflow: 'hidden' },
  price: { alignSelf: 'center', textAlign: 'center', fontFamily: FONT.regular, fontSize: 15, color: COLOR.ink },
  buy: { flex: 1, backgroundColor: COLOR.cta, alignItems: 'center', justifyContent: 'center' },
  buyText: { fontFamily: FONT.semibold, fontSize: 16, color: COLOR.ctaInk },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.6)', backgroundColor: 'rgba(255,255,255,0.1)' },
});
