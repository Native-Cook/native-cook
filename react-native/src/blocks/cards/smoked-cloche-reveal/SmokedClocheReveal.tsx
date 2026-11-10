import { Canvas, Circle, Group, LinearGradient, RadialGradient, Rect, vec } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withDelay, withSequence, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle, Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { ScrGpu } from './gpu/ScrGpu';
import { frameAt, LAND, LOOP, SEAL, T } from './scr-motion';
import { COLOR, COPY, D, FONT, makeLayout } from './scr-theme';

/*
 Smoked Cloche Reveal — the dish page of an invented restaurant's delivery app, laid out from the
 reference pin (652318327267831262): back and more; the bowl of eggs curry seen from above, two basil
 leaves; its name and time, a line about it; the total, a heart and Add to Cart running off the edge.

 Over the bowl sits a glass cloche full of applewood smoke, seen from above, on WebGPU
 (TypeGPU-resolved WGSL): a dome that magnifies the dish under it, with a rim, window reflections and a
 glass knob. Draw the knob up: the cloche resists for a moment (the smoke's seal), then follows,
 growing and blurring as it rises towards the eye; let go high enough, or flick, and it is gone while
 3,072 puffs of smoke (a compute pass with state) spill out from under its rim, swell and thin. Run a
 finger through the smoke and it follows and curls round it. Tap the dish to bring the cloche back
 down, full of smoke again. Add to Cart sends the dish into the cart.

 autoPlay: LOOP = 10 s (scr-motion.ts); frame 10 s equals frame 0 (the cloche on, full of smoke). A
 touch takes over; 2 s after the last one the cloche comes back down and the loop restarts. Reduced
 motion: no loop; the cloche fades off and on, the smoke stands still and fades.
*/

const pixelRatio = PixelRatio.get();
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const DISH = require('./assets/scr-bowl.png');
const LEAVES = [require('./assets/scr-leaf-a.png'), require('./assets/scr-leaf-b.png')];

function haptic(kind: number) {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 0) Haptics.selectionAsync().catch(() => {});
  else if (kind === 1) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function SmokedClocheReveal({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Outfit-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Outfit-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Outfit-SemiBold.ttf'),
    [FONT.bold]: require('./assets/fonts/Outfit-Bold.ttf'),
  });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const [gpuOk, setGpuOk] = useState(false);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);
  const sx = L.sx;
  const geo = useMemo(() => {
    // on a short screen the dish shrinks with the height too, so its rim stays clear of the header
    const s = Math.min(sx, L.k);
    const bowlR = D.bowl.r * s;
    return { cx: D.bowl.x * sx, cy: L.Y(D.bowl.y), half: bowlR / 0.92, domeR: D.dome.r * s, ctaX: (D.cta.x + 40) * sx, ctaY: L.Y(D.cta.y + D.cta.h / 2) };
  }, [L, sx]);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const rate = useSharedValue(1);
  const prevLoopT = useSharedValue(-1);

  const lift = useSharedValue(0);
  const cart = useSharedValue(0);
  const smoke = useSharedValue(0); // 0 held, 1 spilling, 2 refilled
  const spawnReq = useSharedValue(0);
  const lifting = useSharedValue(0);
  const fx = useSharedValue(-999);
  const fy = useSharedValue(-999);
  const pfx = useSharedValue(-999);
  const pfy = useSharedValue(-999);
  const fdown = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const fingerPress = useSharedValue(0);
  const frame = useSharedValue<number[]>([]);
  const [count, setCount] = useState(0);
  const [off, setOff] = useState(false);

  /* ------------------------------------------------------------ actions */
  const takeOff = () => {
    'worklet';
    lift.set(withTiming(1, { duration: reduced ? 250 : 420, easing: Easing.in(Easing.cubic) }));
    smoke.set(1);
    scheduleOnRN(haptic, 2);
  };
  const putOn = () => {
    'worklet';
    spawnReq.set(1);
    smoke.set(2);
    if (reduced) {
      lift.set(withTiming(0, { duration: 250, easing: EASE_IN_OUT }));
      scheduleOnRN(haptic, 1);
      return;
    }
    // lowered, then the rim touches the plate (the haptic) and rocks up a hair before it rests
    const from = lift.get();
    const down = 800 * LAND.at;
    const rock = (800 - down) / 2;
    lift.set(
      withSequence(
        withTiming(0, { duration: down, easing: EASE_IN_OUT }, (fin) => {
          if (fin) scheduleOnRN(haptic, 1);
        }),
        withTiming(from * LAND.hop, { duration: rock, easing: Easing.out(Easing.quad) }),
        withTiming(0, { duration: rock, easing: Easing.in(Easing.quad) }),
      ),
    );
  };
  const toCart = () => {
    'worklet';
    if (cart.get() > 0.01) return;
    cart.set(
      withTiming(1, { duration: reduced ? 1 : 700, easing: EASE_IN_OUT }, (fin) => {
        if (!fin) return;
        scheduleOnRN(addOne);
        cart.set(withDelay(500, withTiming(0, { duration: reduced ? 1 : 600, easing: Easing.out(Easing.cubic) })));
      }),
    );
    scheduleOnRN(haptic, 0);
  };
  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() === 0) {
      mode.set(1);
      finger.set(withTiming(0, { duration: 150 }));
      fingerPress.set(0);
      fdown.set(0);
      // the loop's cloche settles where it was going
      const l = lift.get();
      if (l > 0.02 && l < 0.98) lift.set(withTiming(l > 0.5 ? 1 : 0, { duration: 300 }));
      const c = cart.get();
      if (c > 0.01) cart.set(withTiming(0, { duration: 400 }));
    }
  };
  const addOne = () => setCount((n) => n + 1);

  /* ------------------------------------------------------------ clock */
  const ticker = useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000) * rate.get();
    clock.set(clock.get() + dt);
    const lt = clock.get() % LOOP;
    let smokeMode = smoke.get();
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      lift.set(s.lift);
      cart.set(s.cart);
      smokeMode = s.mode;
      if (prevLoopT.get() < T.refill && t >= T.refill) smokeMode = 3;
      prevLoopT.set(t);
      smoke.set(s.mode);
      finger.set(s.finger);
      fingerPress.set(s.press);
      if (s.fingerAt === 0) {
        fingerX.set(geo.cx);
        fingerY.set(geo.cy - 220 * Math.min(s.lift, 0.55) * 1.6);
      } else if (s.fingerAt === 1) {
        // a stroke through the smoke, left to right with a curl
        const u = s.fingerU;
        fingerX.set(geo.cx + (u - 0.5) * 260);
        fingerY.set(geo.cy + Math.sin(u * Math.PI * 2) * 50);
      } else {
        fingerX.set(geo.ctaX);
        fingerY.set(geo.ctaY);
      }
      fx.set(s.fingerAt === 1 && s.finger > 0.5 ? fingerX.get() : -999);
      fy.set(s.fingerAt === 1 && s.finger > 0.5 ? fingerY.get() : -999);
      fdown.set(s.fingerAt === 1 && s.finger > 0.5 ? 1 : 0);
    } else if (spawnReq.get() === 1) {
      smokeMode = 3;
      spawnReq.set(0);
    }
    const ddx = fdown.get() === 1 && pfx.get() > -900 ? fx.get() - pfx.get() : 0;
    const ddy = fdown.get() === 1 && pfy.get() > -900 ? fy.get() - pfy.get() : 0;
    pfx.set(fx.get());
    pfy.set(fy.get());
    frame.set([
      L.w, L.h, pixelRatio, lt,
      geo.cx, geo.cy, geo.half, geo.domeR,
      lift.get(), cart.get(), smokeMode, reduced ? 0 : dt,
      fx.get(), fy.get(), ddx, ddy,
      fdown.get(), 0, 0, 0,
    ]);
    if (mode.get() === 0) return;

    if (!auto || lifting.get() === 1 || fdown.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      if (lift.get() > 0.01) putOn();
      return;
    }
    if (lift.get() < 0.001 && cart.get() < 0.001 && clock.get() - lastTouch.get() > 3.4) {
      resumeAsked.set(0);
      autoStart.set(clock.get());
      prevLoopT.set(-1);
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
      lift.set(0);
      cart.set(0);
      smoke.set(0);
      finger.set(0);
      fingerPress.set(0);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__smokedCloche = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      rate: (k: number) => rate.set(k),
      restart: () => {
        autoStart.set(clock.get());
        prevLoopT.set(-1);
        pin.set(-1);
      },
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), lift: +lift.get().toFixed(3), cart: +cart.get().toFixed(3), smoke: smoke.get() }),
    };
  }, [pin, rate, autoStart, prevLoopT, clock, mode, lift, cart, smoke]);

  useAnimatedReaction(
    () => lift.get() > 0.98,
    (now, before) => {
      if (now !== before) scheduleOnRN(setOff, now);
    },
  );

  /* ------------------------------------------------------------ gestures */
  // draw the knob up: a seal first, then the cloche follows; high enough or flicked, it is gone. With
  // the cloche off, a finger runs through the smoke, and a tap brings the cloche back
  const domePan = Gesture.Pan()
    .minDistance(4)
    .onBegin((e) => {
      takeOver();
      fx.set(e.absoluteX);
      fy.set(e.absoluteY);
      pfx.set(e.absoluteX);
      pfy.set(e.absoluteY);
      if (lift.get() > 0.98) fdown.set(1);
    })
    .onStart(() => {
      if (lift.get() < 0.02) lifting.set(1);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      fx.set(e.absoluteX);
      fy.set(e.absoluteY);
      if (lifting.get() === 0) return;
      const raw = Math.max(0, -e.translationY / 260);
      // the seal: it barely moves at first, then lets go
      const l = raw < SEAL ? raw * 0.35 : SEAL * 0.35 + (raw - SEAL) * 1.1;
      // past 0.6 the glass gets heavier in the hand: a soft limit at 0.75, not a wall
      lift.set(l < 0.6 ? l : 0.6 + 0.15 * Math.tanh((l - 0.6) / 0.15));
      if (lift.get() > 0.12 && smoke.get() !== 1) {
        smoke.set(1);
        scheduleOnRN(haptic, 1);
      }
    })
    .onEnd((e) => {
      if (lifting.get() === 0) return;
      if (lift.get() > 0.35 || e.velocityY < -700) takeOff();
      else {
        // let go short, the glass drops back under its weight (a fall time that grows with the square
        // root of the height), touches the plate and rocks up a hair
        const from = lift.get();
        const fall = 420 * Math.sqrt(Math.min(1, from / 0.35));
        lift.set(
          withSequence(
            withTiming(0, { duration: Math.max(120, fall), easing: Easing.in(Easing.quad) }, (fin) => {
              if (fin) scheduleOnRN(haptic, 1);
            }),
            withTiming(from * LAND.hop, { duration: 110, easing: Easing.out(Easing.quad) }),
            withTiming(0, { duration: 110, easing: Easing.in(Easing.quad) }),
          ),
        );
      }
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      lifting.set(0);
      fdown.set(0);
    });
  const dishTap = Gesture.Tap()
    .maxDistance(8)
    .onEnd(() => {
      takeOver();
      if (lift.get() > 0.98) putOn();
    });
  const domeTouch = Gesture.Exclusive(domePan, dishTap);

  /* ------------------------------------------------------------ drawing */
  const dishStyle = useAnimatedStyle(() => {
    const c = cart.get();
    const dx = (geo.ctaX - geo.cx) * c;
    const dy = (geo.ctaY - geo.cy) * c - Math.sin(Math.PI * c) * 120;
    return { transform: [{ translateX: dx }, { translateY: dy }, { scale: 1 - 0.88 * c }, { rotate: `${c * 160}deg` }], opacity: 1 - Math.max(0, (c - 0.85) / 0.15) };
  });
  const flatOpacity = useDerivedValue(() => 1 - lift.get());
  const flatSmoke = useDerivedValue(() => (smoke.get() === 1 ? 0.6 * (1 - lift.get()) : 0.6));
  const fingerR = useDerivedValue(() => 22 * (1 - 0.12 * fingerPress.get()));
  const fingerFill = useDerivedValue(() => `rgba(20,20,20,${0.08 * finger.get()})`);
  const fingerRing = useDerivedValue(() => `rgba(255,255,255,${0.8 * finger.get()})`);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };
  const half = geo.half;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      {/* the frosted page */}
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={w} height={h}>
          <LinearGradient start={vec(0, 0)} end={vec(w * 0.3, h)} colors={[COLOR.top, '#DDD6CE', COLOR.bottom]} />
        </Rect>
        <Circle cx={-20} cy={h * 0.95} r={w * 0.7}>
          <RadialGradient c={vec(-20, h * 0.95)} r={w * 0.7} colors={['rgba(243,201,160,0.55)', 'rgba(243,201,160,0)']} />
        </Circle>
      </Canvas>

      <PressScale style={[styles.icon, { left: D.back.x * sx - 22, top: L.Y(D.back.y) - 22 }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Back">
        <Svg width={12} height={22} viewBox="0 0 12 22">
          <SvgPath d="M10 2 L2 11 L10 20" stroke="#3A3632" strokeWidth={2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </Svg>
      </PressScale>
      <PressScale style={[styles.icon, { left: D.more.x * sx - 22, top: L.Y(D.more.y) - 22 }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="More">
        <Svg width={6} height={22} viewBox="0 0 6 22">
          <SvgCircle cx={3} cy={4} r={2.4} fill="#3A3632" />
          <SvgCircle cx={3} cy={11} r={2.4} fill="#3A3632" />
          <SvgCircle cx={3} cy={18} r={2.4} fill="#3A3632" />
        </Svg>
      </PressScale>

      {D.leaves.map((l, i) => (
        <Animated.Image key={i} source={LEAVES[l.img]} style={[styles.abs, { left: l.x * sx - l.s / 2, top: L.Y(l.y) - l.s / 2, width: l.s, height: l.s, transform: [{ rotate: `${l.rot}deg` }] }]} />
      ))}
      <Animated.Image source={DISH} style={[styles.abs, { left: geo.cx - half, top: geo.cy - half, width: half * 2, height: half * 2 }, dishStyle]} accessibilityIgnoresInvertColors />

      {/* the flat cloche, while WebGPU is not up */}
      {!gpuOk && (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Group opacity={flatOpacity}>
            <Circle cx={geo.cx} cy={geo.cy} r={geo.domeR} color="rgba(255,255,255,0.18)" />
            <Circle cx={geo.cx} cy={geo.cy} r={geo.domeR} color="rgba(255,255,255,0.7)" style="stroke" strokeWidth={3} />
          </Group>
          <Circle cx={geo.cx} cy={geo.cy} r={geo.domeR * 0.92} opacity={flatSmoke}>
            <RadialGradient c={vec(geo.cx, geo.cy)} r={geo.domeR * 0.92} colors={['rgba(240,238,234,0.7)', 'rgba(240,238,234,0.25)']} />
          </Circle>
        </Canvas>
      )}
      {size && <ScrGpu frame={frame} width={L.w} height={L.h} dishAsset={DISH} disabled={reduced} onAvailable={setGpuOk} />}

      <GestureDetector gesture={domeTouch}>
        <View
          style={[styles.abs, { left: geo.cx - geo.domeR, top: geo.cy - geo.domeR, width: geo.domeR * 2, height: geo.domeR * 2, borderRadius: geo.domeR }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel={off ? 'Eggs Curry. Tap to cover it again' : 'Cloche. Draw it up to uncover the dish'}
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={() =>
            scheduleOnUI(() => {
              'worklet';
              takeOver();
              if (lift.get() > 0.5) putOn();
              else takeOff();
            })
          }
        />
      </GestureDetector>

      {fontsLoaded && (
        <>
          <Text style={[styles.name, { left: D.name.x * sx, top: L.Y(D.name.y) - 20 }]} allowFontScaling={false} accessibilityRole="header">
            {COPY.name}
          </Text>
          <View style={[styles.time, { right: L.w - D.time.right * sx, top: L.Y(D.time.y) - 12 }]}>
            <Svg width={18} height={18} viewBox="0 0 18 18">
              <SvgCircle cx={9} cy={9} r={7.6} stroke={COLOR.ink} strokeWidth={1.5} fill="none" />
              <SvgPath d="M9 4.8 V9 L11.8 10.6" stroke={COLOR.ink} strokeWidth={1.5} fill="none" strokeLinecap="round" />
            </Svg>
            <Text style={styles.timeText} allowFontScaling={false}>
              {COPY.time}
            </Text>
          </View>
          <Text style={[styles.body, { left: D.body.x * sx, top: L.Y(D.body.y) - 11, width: D.body.w * sx }]} allowFontScaling={false}>
            {COPY.body}
          </Text>
          <Text style={[styles.total, { left: D.total.x * sx, top: L.Y(D.total.y) - 9 }]} allowFontScaling={false}>
            {COPY.total}
          </Text>
          <Text style={[styles.price, { left: D.price.x * sx, top: L.Y(D.price.y) - 14 }]} allowFontScaling={false}>
            {COPY.price}
          </Text>
        </>
      )}
      <PressScale style={[styles.heart, { left: D.heart.x * sx - D.heart.r, top: L.Y(D.heart.y) - D.heart.r, width: D.heart.r * 2, height: D.heart.r * 2, borderRadius: D.heart.r }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Save">
        <Svg width={22} height={20} viewBox="0 0 22 20">
          <SvgPath d="M11 18.2 C 4 13.4 1.6 10.2 1.6 6.6 A 4.8 4.8 0 0 1 11 4.6 A 4.8 4.8 0 0 1 20.4 6.6 C 20.4 10.2 18 13.4 11 18.2 Z" stroke={COLOR.ink} strokeWidth={1.7} fill="none" strokeLinejoin="round" />
        </Svg>
      </PressScale>
      <PressScale
        style={[styles.cta, { left: D.cta.x * sx, top: L.Y(D.cta.y), width: D.cta.w * sx, height: D.cta.h * L.k, borderTopLeftRadius: D.cta.r, borderBottomLeftRadius: D.cta.r }]}
        scale={0.97}
        reduced={reduced}
        accessibilityRole="button"
        accessibilityLabel={count > 0 ? `Add to cart, ${count} in the cart` : 'Add to cart'}
        onPress={() =>
          scheduleOnUI(() => {
            'worklet';
            takeOver();
            toCart();
          })
        }
      >
        {fontsLoaded && (
          <Text style={styles.ctaText} allowFontScaling={false}>
            {COPY.cta}
          </Text>
        )}
        <View style={styles.plus}>
          <Svg width={14} height={14} viewBox="0 0 14 14">
            <SvgPath d="M7 1.5 V12.5 M1.5 7 H12.5" stroke="#050505" strokeWidth={2} strokeLinecap="round" />
          </Svg>
          {count > 0 && (
            <View style={styles.badge}>
              <Text style={styles.badgeText} allowFontScaling={false}>
                {count}
              </Text>
            </View>
          )}
        </View>
      </PressScale>

      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerFill} />
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerRing} style="stroke" strokeWidth={1.5} />
      </Canvas>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.top, overflow: 'hidden' },
  abs: { position: 'absolute' },
  icon: { position: 'absolute', width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  name: { position: 'absolute', fontFamily: FONT.bold, fontSize: D.name.size, lineHeight: 36, color: COLOR.ink },
  time: { position: 'absolute', flexDirection: 'row', alignItems: 'center', gap: 8 },
  timeText: { fontFamily: FONT.medium, fontSize: D.time.size, color: COLOR.ink },
  body: { position: 'absolute', fontFamily: FONT.regular, fontSize: D.body.size, lineHeight: D.body.line, color: COLOR.body },
  total: { position: 'absolute', fontFamily: FONT.regular, fontSize: D.total.size, color: COLOR.mute },
  price: { position: 'absolute', fontFamily: FONT.bold, fontSize: D.price.size, color: COLOR.ink },
  heart: { position: 'absolute', backgroundColor: COLOR.heart, alignItems: 'center', justifyContent: 'center' },
  cta: { position: 'absolute', backgroundColor: COLOR.cta, flexDirection: 'row', alignItems: 'center', paddingLeft: 22, gap: 12 },
  ctaText: { fontFamily: FONT.semibold, fontSize: 15, color: COLOR.ctaInk },
  plus: { width: 32, height: 32, borderRadius: 16, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center' },
  badge: { position: 'absolute', top: -6, right: -6, minWidth: 18, height: 18, borderRadius: 9, backgroundColor: '#F26B3A', alignItems: 'center', justifyContent: 'center', paddingHorizontal: 4 },
  badgeText: { fontFamily: FONT.semibold, fontSize: 11, color: '#FFFFFF' },
});
