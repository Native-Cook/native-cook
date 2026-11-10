import { Canvas, Circle, Group, LinearGradient, RadialGradient, Rect, vec } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, Image, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle, Defs, LinearGradient as SvgLinearGradient, Path as SvgPath, Stop } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { frameAt, LOOP, POUR } from './elt-motion';
import { COLOR, COPY, D, FONT, makeLayout, REST_TINT, TINTS } from './elt-theme';
import { EltGpu } from './gpu/EltGpu';

/*
 Eyewear Lens Tint — the product page of an invented eyewear shop, laid out from the reference pin
 (9499849195024471): a portrait filling the screen, the model in round glasses; the menu and search; a
 card with the name, price, rating, a line, Add to bag and the wishlist. The portrait is generated for
 the block; four tints wait as glass droplets down its right edge.

 The lenses are drawn on WebGPU (TypeGPU-resolved WGSL) over the portrait: the face behind each, a
 little smaller through a minus lens, the light the tint keeps (a gradient, darker at the top), the
 studio's window, the tint's own glow low in the lens and a bright rim. Carry a droplet onto a lens
 and let go: the tint pours in from there, its wavering bright front running down under gravity, and
 crosses the bridge into the other lens. Drag across the face and a glare sweeps over the lenses.

 autoPlay: LOOP = 10 s (elt-motion.ts); frame 10 s equals frame 0, the midnight lenses. A touch takes
 over; 2 s after the last one midnight is poured back in and the loop restarts. Reduced motion: no
 loop; a tint fades in without pouring, and the droplet jumps to the lens.
*/

const pixelRatio = PixelRatio.get();
const PORTRAIT = require('./assets/elt-portrait.jpg');

function buzz(kind: 'pick' | 'pour') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'pick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function EyewearLensTint({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/PTSans-Regular.ttf'),
    [FONT.bold]: require('./assets/fonts/PTSans-Bold.ttf'),
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
  const geo = useMemo(() => {
    const sx = L.sx;
    const ly = L.Y(D.lenses[0].y);
    const lenses = D.lenses.map((l) => ({ x: l.x * sx, y: ly, r: l.r * sx }));
    const photo = { x: D.photo.x * sx, y: ly - (D.lenses[0].y - D.photo.y) * sx, w: D.photo.w * sx, h: D.photo.h * sx };
    const drops = TINTS.map((_, i) => ({ x: D.drops.x * sx, y: L.Y(D.drops.y0 + i * D.drops.gap) }));
    // where a droplet is let go on each lens: high on its outer side
    const pours = lenses.map((l, i) => ({ x: l.x + (i === 0 ? -0.45 : 0.45) * l.r, y: l.y - 0.55 * l.r }));
    return { lenses, photo, drops, pours };
  }, [L]);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const rate = useSharedValue(1);

  const from = useSharedValue(REST_TINT);
  const to = useSharedValue(REST_TINT);
  const pour = useSharedValue(0);
  const lens = useSharedValue(1);
  const glare = useSharedValue(0);
  const carryX = useSharedValue(-100);
  const carryY = useSharedValue(-100);
  const prevX = useSharedValue(-100);
  const prevY = useSharedValue(-100);
  const stretch = useSharedValue(0);
  const heading = useSharedValue(0);
  const carryTint = useSharedValue(0);
  const carryOn = useSharedValue(0);
  const grab = useSharedValue(-1); // the droplet a finger holds (−1 none, −2 the glare)
  const finger = useSharedValue(0);
  const frame = useSharedValue<number[]>([]);
  const [shownTint, setShownTint] = useState(REST_TINT);

  /* ------------------------------------------------------------ actions */
  const pourInto = (tint: number, which: number) => {
    'worklet';
    const now = pour.get() > 0.5 ? to.get() : from.get();
    from.set(pour.get() > 0 ? now : to.get());
    to.set(tint);
    lens.set(which);
    pour.set(0);
    pour.set(
      withTiming(1, { duration: reduced ? 250 : POUR * 1000, easing: Easing.linear }, (fin) => {
        if (!fin) return;
        from.set(tint);
        pour.set(0);
      }),
    );
    scheduleOnRN(buzz, 'pour');
  };
  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    carryOn.set(withTiming(0, { duration: 150 }));
    glare.set(withTiming(0, { duration: 300 }));
    // a pour of the loop finishes where it was going
    const p = pour.get();
    if (p > 0) {
      const target = to.get();
      pour.set(
        withTiming(1, { duration: (1 - p) * POUR * 1000, easing: Easing.linear }, (fin) => {
          if (!fin) return;
          from.set(target);
          pour.set(0);
        }),
      );
    }
  };

  /* ------------------------------------------------------------ clock */
  const ticker = useFrameCallback((fi) => {
    const dt = Math.min(0.05, (fi.timeSincePreviousFrame ?? 16) / 1000) * rate.get();
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      from.set(s.from);
      to.set(s.to);
      pour.set(s.pour);
      lens.set(s.lens);
      glare.set(s.glare);
      finger.set(s.finger);
      if (s.carry >= 0) {
        // the droplet's way from the edge to the lens, in an arc
        const a = geo.drops[s.carryTint];
        const b = geo.pours[s.lens];
        const u = s.carry;
        carryX.set(a.x + (b.x - a.x) * u);
        carryY.set(a.y + (b.y - a.y) * u - Math.sin(Math.PI * u) * 60);
        carryTint.set(s.carryTint);
        carryOn.set(1);
      } else carryOn.set(0);
    }
    // the carried droplet stretches along its way with its speed and rounds up again when it slows,
    // like a bead of liquid (a pinned frame is still)
    if (dt > 0 && !reduced) {
      const vx = (carryX.get() - prevX.get()) / dt;
      const vy = (carryY.get() - prevY.get()) / dt;
      const sp = Math.hypot(vx, vy);
      const want = carryOn.get() > 0.5 && sp < 6000 ? Math.min(0.4, sp / 2400) : 0;
      stretch.set(stretch.get() + (want - stretch.get()) * Math.min(1, dt * 16));
      if (sp > 60) heading.set(Math.atan2(vy, vx));
    }
    prevX.set(carryX.get());
    prevY.set(carryY.get());
    frame.set([
      L.w, L.h, pixelRatio, clock.get() % 600,
      geo.lenses[0].x, geo.lenses[0].y, geo.lenses[0].r, 0,
      geo.lenses[1].x, geo.lenses[1].y, geo.lenses[1].r, 0,
      from.get(), to.get(), reduced ? (pour.get() > 0 ? 1 : 0) : pour.get(), lens.get(),
      glare.get(), 0, 0, 0,
      geo.photo.x, geo.photo.y, geo.photo.w, geo.photo.h,
    ]);
    if (mode.get() === 0) return;
    if (!auto || grab.get() !== -1 || pour.get() > 0 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      glare.set(withTiming(0, { duration: 400 }));
      if (to.get() !== REST_TINT) pourInto(REST_TINT, 1);
      return;
    }
    if (pour.get() === 0 && from.get() === REST_TINT && Math.abs(glare.get()) < 0.001 && clock.get() - lastTouch.get() > 3.6) {
      resumeAsked.set(0);
      autoStart.set(clock.get());
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
      from.set(REST_TINT);
      to.set(REST_TINT);
      pour.set(0);
      glare.set(0);
      finger.set(0);
      carryOn.set(0);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__lensTint = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      rate: (k: number) => rate.set(k),
      restart: () => {
        autoStart.set(clock.get());
        pin.set(-1);
      },
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), from: from.get(), to: to.get(), pour: +pour.get().toFixed(3), glare: +glare.get().toFixed(2) }),
    };
  }, [pin, rate, autoStart, clock, mode, from, to, pour, glare]);

  useAnimatedReaction(
    () => (pour.get() > 0.5 ? to.get() : from.get()),
    (now, before) => {
      if (now !== before) scheduleOnRN(setShownTint, now);
    },
  );

  /* ------------------------------------------------------------ gestures */
  // a finger on a droplet carries it; anywhere else on the face it moves the glare
  const touch = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      takeOver();
      let hit = -2;
      for (let i = 0; i < geo.drops.length; i++) {
        if (Math.hypot(e.absoluteX - geo.drops[i].x, e.absoluteY - geo.drops[i].y) < 26) hit = i;
      }
      grab.set(hit);
      if (hit >= 0) {
        carryTint.set(hit);
        carryX.set(e.absoluteX);
        carryY.set(e.absoluteY);
        carryOn.set(withTiming(1, { duration: 120 }));
        scheduleOnRN(buzz, 'pick');
      }
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      if (grab.get() >= 0) {
        // the droplet rides a little above the finger so the finger never hides it
        carryX.set(e.absoluteX);
        carryY.set(e.absoluteY - 28);
      } else glare.set(Math.max(-1, Math.min(1, e.translationX / 140)));
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      const g = grab.get();
      grab.set(-1);
      if (g === -2) {
        glare.set(withSpring(0, { duration: 900, dampingRatio: 0.6, velocity: Math.abs(glare.get()) < 1 ? e.velocityX / 140 : 0 }));
        return;
      }
      if (g < 0) return;
      let target = -1;
      for (let i = 0; i < geo.lenses.length; i++) {
        if (Math.hypot(carryX.get() - geo.lenses[i].x, carryY.get() - geo.lenses[i].y) < geo.lenses[i].r * 1.25) target = i;
      }
      if (target >= 0) {
        carryOn.set(withTiming(0, { duration: 160 }));
        pourInto(g, target);
      } else {
        // not on a lens: it springs back to its place, thrown with the finger's speed, and settles in
        // its slot with a small overshoot
        carryX.set(withSpring(geo.drops[g].x, { duration: 520, dampingRatio: 0.62, velocity: e.velocityX }));
        carryY.set(
          withSpring(geo.drops[g].y, { duration: 520, dampingRatio: 0.62, velocity: e.velocityY }, (fin) => {
            if (fin) carryOn.set(withTiming(0, { duration: 140 }));
          }),
        );
      }
    });

  /* ------------------------------------------------------------ drawing */
  const carryR = useDerivedValue(() => D.drops.r * L.sx * (1 + 0.25 * carryOn.get()) * carryOn.get());
  const carryColor = useDerivedValue(() => TINTS[Math.round(carryTint.get())].swatch);
  const carryShape = useDerivedValue(() => {
    const k = 1 + stretch.get();
    return [{ translateX: carryX.get() }, { translateY: carryY.get() }, { rotate: heading.get() }, { scaleX: k }, { scaleY: 1 / k }];
  });
  const carryHi = useDerivedValue(() => `rgba(255,255,255,${0.75 * carryOn.get()})`);
  const carryHiX = useDerivedValue(() => carryX.get() - carryR.get() * 0.35);
  const carryHiY = useDerivedValue(() => carryY.get() - carryR.get() * 0.35);
  const carryHiR = useDerivedValue(() => carryR.get() * 0.28);
  const fingerStyle = useAnimatedStyle(() => ({
    opacity: finger.get() * (mode.get() === 0 ? 1 : 0),
    transform: [{ translateX: carryX.get() - 22 }, { translateY: carryY.get() + 6 }, { scale: 0.9 }],
  }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };
  const Y = L.Y;
  const sx = L.sx;
  const cardTop = Y(D.card.y);

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <Image source={PORTRAIT} style={[styles.abs, { left: geo.photo.x, top: geo.photo.y, width: geo.photo.w, height: geo.photo.h }]} accessibilityIgnoresInvertColors />
      {!gpuOk && (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          {geo.lenses.map((l, i) => (
            <Circle key={i} cx={l.x} cy={l.y} r={l.r} color={TINTS[shownTint].swatch} opacity={0.88} />
          ))}
        </Canvas>
      )}
      {size && <EltGpu frame={frame} width={L.w} height={L.h} photoAsset={PORTRAIT} disabled={false} onAvailable={setGpuOk} />}

      <GestureDetector gesture={touch}>
        <View
          style={[styles.abs, { left: 0, top: Y(140), width: L.w, height: cardTop - Y(140) - 60 }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={`Lenses tinted ${TINTS[shownTint].name}`}
          accessibilityHint="Drag a tint from the right onto a lens"
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(ev) =>
            scheduleOnUI(() => {
              'worklet';
              takeOver();
              const cur = pour.get() > 0.5 ? to.get() : from.get();
              const n = (cur + (ev.nativeEvent.actionName === 'increment' ? 1 : TINTS.length - 1)) % TINTS.length;
              pourInto(n, 1);
            })
          }
        />
      </GestureDetector>

      {/* the tints, as glass droplets */}
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        {geo.drops.map((d, i) => (
          <Circle key={i} cx={d.x} cy={d.y} r={D.drops.r * sx}>
            <RadialGradient c={vec(d.x - 4, d.y - 4)} r={D.drops.r * sx * 1.4} colors={['#FFFFFF', TINTS[i].swatch, TINTS[i].swatch]} positions={[0, 0.35, 1]} />
          </Circle>
        ))}
        {geo.drops.map((d, i) => (
          <Circle key={`r${i}`} cx={d.x} cy={d.y} r={D.drops.r * sx + 3} color={i === shownTint ? 'rgba(30,30,32,0.8)' : 'rgba(255,255,255,0.7)'} style="stroke" strokeWidth={1.2} />
        ))}
        <Group transform={carryShape}>
          <Circle cx={0} cy={0} r={carryR} color={carryColor} />
        </Group>
        <Circle cx={carryHiX} cy={carryHiY} r={carryHiR} color={carryHi} />
      </Canvas>

      <PressScale style={[styles.icon, { left: D.menu.x * sx - 22, top: Y(D.menu.y) - 22 }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Menu">
        <Svg width={22} height={16} viewBox="0 0 22 16">
          <SvgPath d="M1 1.5 H21 M1 8 H14 M1 14.5 H21" stroke={COLOR.ink} strokeWidth={1.8} strokeLinecap="round" />
        </Svg>
      </PressScale>
      <PressScale style={[styles.icon, { left: D.search.x * sx - 22, top: Y(D.search.y) - 22 }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Search">
        <Svg width={20} height={20} viewBox="0 0 20 20">
          <SvgCircle cx={8.5} cy={8.5} r={6.5} stroke={COLOR.ink} strokeWidth={1.8} fill="none" />
          <SvgPath d="M13.5 13.5 L18.5 18.5" stroke={COLOR.ink} strokeWidth={1.8} strokeLinecap="round" />
        </Svg>
      </PressScale>

      {/* the soft white veil the card rises out of */}
      <Canvas style={[styles.abs, { left: 0, top: cardTop - 90, width: L.w, height: 90 }]} pointerEvents="none">
        <Rect x={0} y={0} width={L.w} height={90}>
          <LinearGradient start={vec(0, 0)} end={vec(0, 90)} colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.75)']} />
        </Rect>
      </Canvas>
      <View style={[styles.card, { left: D.card.x * sx, top: cardTop, width: D.card.w * sx, height: L.h - cardTop - insets.bottom + 6 }]} />
      <PressScale style={[styles.down, { left: D.down.x * sx - D.down.r, top: Y(D.down.y) - D.down.r, width: D.down.r * 2, height: D.down.r * 2, borderRadius: D.down.r }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="More about these sunglasses">
        <Svg width={14} height={9} viewBox="0 0 14 9">
          <SvgPath d="M1.5 1.5 L7 7 L12.5 1.5" stroke="#FFFFFF" strokeWidth={1.8} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </Svg>
      </PressScale>

      {fontsLoaded && (
        <>
          <Text style={[styles.title, { left: D.title.x * sx, top: Y(D.title.y) - 16 }]} allowFontScaling={false} accessibilityRole="header">
            {COPY.title}
          </Text>
          <Text style={[styles.price, { right: L.w - D.price.right * sx, top: Y(D.price.y) - 14 }]} allowFontScaling={false}>
            {COPY.price}
          </Text>
          <View style={[styles.stars, { left: D.stars.x * sx, top: Y(D.stars.y) - 8 }]} accessible accessibilityLabel="4.5 stars, 18 reviews">
            {[0, 1, 2, 3, 4].map((i) => (
              <Svg key={i} width={15} height={15} viewBox="0 0 24 24">
                {/* 4.5: four whole stars and the left half of the fifth */}
                <Defs>
                  <SvgLinearGradient id="eltHalfStar" x1="0" y1="0" x2="1" y2="0">
                    <Stop offset="0.5" stopColor={COLOR.ink} />
                    <Stop offset="0.5" stopColor="#D6D6DB" />
                  </SvgLinearGradient>
                </Defs>
                <SvgPath d="M12 1.8 L15.1 8.3 L22.2 9.2 L17 14.1 L18.3 21.2 L12 17.8 L5.7 21.2 L7 14.1 L1.8 9.2 L8.9 8.3 Z" fill={i < 4 ? COLOR.ink : 'url(#eltHalfStar)'} />
              </Svg>
            ))}
            <Text style={styles.reviews} allowFontScaling={false}>
              {`  ${COPY.rating}   |   ${COPY.reviews}`}
            </Text>
          </View>
          <Text style={[styles.body, { left: D.body.x * sx, top: Y(D.body.y) - 10, width: D.body.w * sx }]} allowFontScaling={false}>
            {COPY.body}
          </Text>
          <PressScale style={[styles.bag, { left: D.bag.x * sx, top: Y(D.bag.y), width: D.bag.w * sx, height: D.bag.h }]} scale={0.97} reduced={reduced} accessibilityRole="button" accessibilityLabel="Add to bag">
            <Text style={styles.bagText} allowFontScaling={false}>
              {COPY.bag}
            </Text>
          </PressScale>
          <View style={[styles.wish, { right: L.w - D.wish.right * sx, top: Y(D.wish.y) - 8 }]}>
            <Svg width={14} height={13} viewBox="0 0 22 20">
              <SvgPath d="M11 18.2 C 4 13.4 1.6 10.2 1.6 6.6 A 4.8 4.8 0 0 1 11 4.6 A 4.8 4.8 0 0 1 20.4 6.6 C 20.4 10.2 18 13.4 11 18.2 Z" fill="#C9C9CE" />
            </Svg>
            <Text style={styles.wishText} allowFontScaling={false}>
              {COPY.wish}
            </Text>
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
  icon: { position: 'absolute', width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  card: { position: 'absolute', backgroundColor: COLOR.card, shadowColor: '#000', shadowOpacity: 0.08, shadowRadius: 12, shadowOffset: { width: 0, height: 4 } },
  down: { position: 'absolute', backgroundColor: COLOR.cta, alignItems: 'center', justifyContent: 'center' },
  title: { position: 'absolute', fontFamily: FONT.bold, fontSize: D.title.size, letterSpacing: 0.5, color: COLOR.ink },
  price: { position: 'absolute', fontFamily: FONT.regular, fontSize: D.price.size, color: COLOR.ink },
  stars: { position: 'absolute', flexDirection: 'row', alignItems: 'center', gap: 1 },
  reviews: { fontFamily: FONT.regular, fontSize: 10, color: COLOR.mute, letterSpacing: 0.4 },
  body: { position: 'absolute', fontFamily: FONT.regular, fontSize: D.body.size, lineHeight: D.body.line, color: COLOR.body },
  bag: { position: 'absolute', backgroundColor: COLOR.cta, alignItems: 'center', justifyContent: 'center' },
  bagText: { fontFamily: FONT.bold, fontSize: 15, letterSpacing: 0.6, color: COLOR.ctaInk },
  wish: { position: 'absolute', flexDirection: 'row', alignItems: 'center', gap: 6 },
  wishText: { fontFamily: FONT.regular, fontSize: 10, letterSpacing: 0.4, color: COLOR.mute },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, borderWidth: 1.5, borderColor: 'rgba(20,20,20,0.35)', backgroundColor: 'rgba(20,20,20,0.06)' },
});
