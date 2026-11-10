import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, Image, type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, useAnimatedReaction, useAnimatedStyle, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle, Path as SvgPath, Rect as SvgRect } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { SdrGpu } from './gpu/SdrGpu';
import { DROPS, frameAt, GROW, LOOP, NECK } from './sdr-motion';
import { COLOR, D, DAYS, FONT, makeLayout, STEPS } from './sdr-theme';

/*
 Serum Dropper Routine — the daily routine of an invented skincare app, laid out from the reference pin
 (1030902171247987141): back, Daily routine, more; a strip of days; four steps as tinted cards with
 their products (photos generated for the block), names and ticks.

 Hold the first step's bottle: its dropper rises out of the neck and leans over the card, the bulb is
 squeezed, and drops of oil (drawn on WebGPU, TypeGPU-resolved WGSL, as lit distance fields) swell at
 the tip, neck, let go and fall to the card's floor, where they land with a squash and a ring of
 splashes and spread into a glossy pool that soaks in. Once the oil is on, the step ticks itself. Let
 go and the dropper goes back into the bottle. Tap a step's tick to clear or set it.

 autoPlay: LOOP = 10 s (sdr-motion.ts); frame 10 s equals frame 0, the reference's screen. A touch takes
 over; 2 s after the last one, once the pool has soaked in, the loop restarts. Reduced motion: no loop;
 the dropper moves straight across and a single drop fades onto the card.
*/

const pixelRatio = PixelRatio.get();
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const IMG = {
  bottle: require('./assets/products/sdr-bottle.png'),
  dropper: require('./assets/products/sdr-dropper.png'),
  tube: require('./assets/products/sdr-tube.png'),
  jar: require('./assets/products/sdr-jar.png'),
  white: require('./assets/products/sdr-white.png'),
};
// the dropper image: its size against the bottle's, where its collar ends and where its bulb ends
const DROPPER = { h: 160 / 124, ar: 300 / 1100, collar: 520 / 1100, bulb: 0.3, tip: 1034 / 1100 };
const BOTTLE_AR = 700 / 1100;
/** where the dropper's top sits, seated, against the bottle image's top, in bottle heights */
const SEAT = -52 / 124;

function buzz(kind: 'tick' | 'drop' | 'done') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else if (kind === 'drop') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function SerumDropperRoutine({ autoPlay = false }: Props) {
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
  const [, setGpuOk] = useState(false);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);
  const geo = useMemo(() => {
    const sx = L.sx;
    const bh = D.bottle.h * sx;
    const bw = bh * BOTTLE_AR;
    const bx = D.bottle.x * sx;
    const btop = L.Y(D.bottle.top);
    const dh = bh * DROPPER.h;
    const dw = dh * DROPPER.ar;
    const dtop = btop + SEAT * bh;
    const seatTipY = dtop + DROPPER.tip * dh;
    return {
      sx,
      bh,
      bw,
      bx,
      btop,
      dh,
      dw,
      dtop,
      seatTipY,
      rise: D.rise * sx,
      liftX: D.lift.x * sx,
      liftTipY: L.Y(D.lift.tipY),
      floor: L.Y(D.floor),
      card: { x: D.cards[0].x * sx, y: L.Y(D.cards[0].y), w: D.cards[0].w * sx, h: D.cards[0].h * L.k },
    };
  }, [L]);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const pin = useSharedValue(-1);
  const rate = useSharedValue(1);

  const lift = useSharedValue(0);
  const squeeze = useSharedValue(0);
  const soak = useSharedValue(0);
  const done = useSharedValue(1);
  const holding = useSharedValue(0);
  const dropAt = useSharedValue<number[]>([-100, -100, -100]);
  const nextDrop = useSharedValue(0);
  const ages = useSharedValue<number[]>([-1, -1, -1]);
  const lastDropClock = useSharedValue(-100);
  const finger = useSharedValue(0);
  const fingerAt = useSharedValue(0);
  const fingerPress = useSharedValue(0);
  const tip = useSharedValue<number[]>([0, 0]);
  const frame = useSharedValue<number[]>([]);
  const [ticks, setTicks] = useState<boolean[]>(STEPS.map((s) => s.done));

  const setTick0 = (v: boolean) => setTicks((t) => [v, ...t.slice(1)]);

  /* ------------------------------------------------------------ actions */
  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    fingerPress.set(0);
    // the loop's drops carry on as they were
    const a = ages.get();
    const now = clock.get();
    dropAt.set(a.map((x) => (x >= 0 ? now - x : -100)));
    lastDropClock.set(now);
    if (holding.get() === 0) lift.set(withTiming(0, { duration: 500, easing: EASE_IN_OUT }));
    squeeze.set(withTiming(0, { duration: 200 }));
  };
  const setDone = (v: boolean) => {
    'worklet';
    done.set(withTiming(v ? 1 : 0, { duration: 300 }));
  };

  /* ------------------------------------------------------------ clock */
  const ticker = useFrameCallback((fi) => {
    const dt = Math.min(0.05, (fi.timeSincePreviousFrame ?? 16) / 1000) * rate.get();
    clock.set(clock.get() + dt);
    const now = clock.get();
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((now - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      lift.set(s.lift);
      squeeze.set(s.squeeze);
      soak.set(s.soak);
      done.set(s.done);
      ages.set(s.drops);
      finger.set(s.finger);
      fingerAt.set(s.fingerAt);
      fingerPress.set(s.press);
    } else {
      // held with the dropper up: a drop every 0.8 s, up to three; the bulb pulses with each
      const at = dropAt.get();
      if (holding.get() === 1 && lift.get() > 0.98 && nextDrop.get() < DROPS && now - lastDropClock.get() > 0.8) {
        const n = at.slice();
        n[nextDrop.get()] = now;
        dropAt.set(n);
        nextDrop.set(nextDrop.get() + 1);
        lastDropClock.set(now);
        soak.set(0);
      }
      const a = dropAt.get().map((x) => (x > -50 ? now - x : -1));
      ages.set(a);
      let pulse = 0;
      for (let i = 0; i < a.length; i++) if (a[i] >= 0 && a[i] < GROW + NECK) pulse = Math.max(pulse, Math.sin((Math.PI * a[i]) / (GROW + NECK)));
      if (holding.get() === 1 && lift.get() > 0.98) squeeze.set(0.55 + 0.45 * pulse);
      // the pool soaks in once no more drops come
      const last = Math.max(...a.filter((x) => x >= 0), -1);
      if (last > 2.2) soak.set(Math.min(1, (last - 2.2) / 3.8));
      if (last > 6.4) {
        dropAt.set([-100, -100, -100]);
        nextDrop.set(0);
        soak.set(0);
      }
    }
    // the dropper's tip: up out of the neck, then over to the side
    const l = lift.get();
    const up = Math.min(1, l / 0.55);
    const over = Math.max(0, (l - 0.55) / 0.45);
    const e1 = up < 0.5 ? 4 * up * up * up : 1 - Math.pow(-2 * up + 2, 3) / 2;
    const e2 = over < 0.5 ? 4 * over * over * over : 1 - Math.pow(-2 * over + 2, 3) / 2;
    const tipX = geo.bx + (geo.liftX - geo.bx) * e2;
    const tipY = geo.seatTipY - geo.rise * e1 + (geo.liftTipY - (geo.seatTipY - geo.rise)) * e2;
    tip.set([tipX, tipY]);
    const ag = ages.get();
    frame.set([L.w, L.h, pixelRatio, now % 600, tipX, tipY, geo.floor, soak.get(), ag[0], ag[1], ag[2], geo.sx, geo.liftX, 0, 0, 0]);
    if (mode.get() === 0) return;
    if (!auto || holding.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    const settled = lift.get() < 0.001 && nextDrop.get() === 0 && done.get() > 0.999;
    if (settled && clock.get() - lastTouch.get() > 2.6) {
      autoStart.set(now);
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
      squeeze.set(0);
      done.set(1);
      finger.set(0);
      dropAt.set([-100, -100, -100]);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__serumDropper = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      rate: (k: number) => rate.set(k),
      restart: () => {
        autoStart.set(clock.get());
        pin.set(-1);
      },
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), lift: +lift.get().toFixed(3), squeeze: +squeeze.get().toFixed(2), ages: ages.get().map((a) => +a.toFixed(2)), done: +done.get().toFixed(2) }),
    };
  }, [pin, rate, autoStart, clock, mode, lift, squeeze, ages, done]);

  // the first step's tick follows the oil; a drop's landing gives a soft tap
  useAnimatedReaction(
    () => done.get() > 0.5,
    (now, before) => {
      if (before !== null && now !== before) scheduleOnRN(setTick0, now);
    },
  );
  useAnimatedReaction(
    () => ages.get().filter((a) => a > GROW + NECK + 0.25).length,
    (now, before) => {
      if (mode.get() === 1 && before !== null && now > before) {
        scheduleOnRN(buzz, 'drop');
        if (now === 1 && done.get() < 0.5) {
          done.set(withTiming(1, { duration: 350 }));
          scheduleOnRN(buzz, 'done');
        }
      }
    },
  );

  /* ------------------------------------------------------------ gestures */
  const hold = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      takeOver();
      holding.set(1);
      nextDrop.set(ages.get().some((a) => a >= 0) ? nextDrop.get() : 0);
      lift.set(withTiming(1, { duration: reduced ? 200 : 650, easing: EASE_IN_OUT }));
      lastDropClock.set(clock.get() + (reduced ? 0 : 0.2));
      scheduleOnRN(buzz, 'tick');
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      holding.set(0);
      squeeze.set(withTiming(0, { duration: 200 }));
      lift.set(withTiming(0, { duration: reduced ? 200 : 600, easing: EASE_IN_OUT }));
    });

  const toggleTick = (i: number) => {
    buzz('tick');
    setTicks((t) => t.map((v, j) => (j === i ? !v : v)));
    if (i === 0)
      scheduleOnUI(() => {
        'worklet';
        takeOver();
        setDone(done.get() < 0.5);
      });
  };

  /* ------------------------------------------------------------ drawing */
  const dropperStyle = useAnimatedStyle(() => {
    const t = tip.get();
    return { transform: [{ translateX: t[0] - geo.bx }, { translateY: t[1] - geo.seatTipY }] };
  });
  const bulbStyle = useAnimatedStyle(() => {
    const s = squeeze.get();
    const bulbH = geo.dh * DROPPER.bulb;
    return { transform: [{ translateY: (bulbH * 0.2 * s) / 2 }, { scaleY: 1 - 0.2 * s }, { scaleX: 1 + 0.1 * s }] };
  });
  const tick0 = useAnimatedStyle(() => ({ opacity: done.get(), transform: [{ scale: 0.6 + 0.4 * done.get() }] }));
  const fingerStyle = useAnimatedStyle(() => {
    const at = fingerAt.get();
    const x = at === 0 ? geo.card.x + D.check.dx * geo.sx : geo.bx + 6;
    const y = at === 0 ? geo.card.y + D.check.dy : geo.btop + geo.bh * 0.55;
    return { opacity: finger.get(), transform: [{ translateX: x - 22 }, { translateY: y - 22 }, { scale: 1 - 0.12 * fingerPress.get() }] };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };
  const Y = L.Y;
  const sx = geo.sx;
  const bulbH = geo.dh * DROPPER.bulb;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <View style={[styles.top, { height: Y(D.topBottom) }]} />
      <PressScale style={[styles.square, { left: D.back.x * sx - 22, top: Y(D.back.y) - 22 }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Back">
        <Svg width={9} height={15} viewBox="0 0 9 15">
          <SvgPath d="M7.5 1.5 L1.8 7.5 L7.5 13.5" stroke={COLOR.ink} strokeWidth={1.8} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </Svg>
      </PressScale>
      <PressScale style={[styles.square, { left: D.more.x * sx - 22, top: Y(D.more.y) - 22 }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="More">
        <Svg width={14} height={4} viewBox="0 0 14 4">
          <SvgCircle cx={3} cy={2} r={1.8} fill={COLOR.ink} />
          <SvgCircle cx={11} cy={2} r={1.8} fill={COLOR.ink} />
        </Svg>
      </PressScale>

      {fontsLoaded && (
        <>
          <Text style={[styles.title, { left: D.title.x * sx, top: Y(D.title.y) - 12 }]} allowFontScaling={false} accessibilityRole="header">
            Daily routine
          </Text>
          {DAYS.map((d, i) => (
            <View key={d.n} style={[styles.day, i === D.days.sel && styles.daySel, { left: D.days.xs[i] * sx, top: Y(D.days.y), width: D.days.w * sx, height: D.days.h * L.k }]} accessible accessibilityLabel={`${d.d} ${d.n}${i === D.days.sel ? ', today' : ''}`}>
              <Text style={styles.dayN} allowFontScaling={false}>
                {d.n}
              </Text>
              <Text style={[styles.dayD, i === D.days.sel && styles.dayDSel]} allowFontScaling={false}>
                {d.d}
              </Text>
            </View>
          ))}
        </>
      )}

      {/* the steps' cards and their products */}
      {D.cards.map((c, i) => (
        <View key={i} style={[styles.card, { left: c.x * sx, top: Y(c.y), width: c.w * sx, height: c.h * L.k, backgroundColor: STEPS[i].bg }]} />
      ))}
      <Image source={IMG.tube} style={[styles.abs, { left: D.products.tube.x * sx - (D.products.tube.h * sx * 600) / 900 / 2, top: Y(D.products.tube.y) - (D.products.tube.h * sx) / 2, width: (D.products.tube.h * sx * 600) / 900, height: D.products.tube.h * sx }]} />
      <Image source={IMG.jar} style={[styles.abs, { left: D.products.jar.x * sx - (D.products.jar.w * sx) / 2, top: Y(D.products.jar.y) - (D.products.jar.w * sx * 600) / 800 / 2, width: D.products.jar.w * sx, height: (D.products.jar.w * sx * 600) / 800 }]} />
      <Image source={IMG.white} style={[styles.abs, { left: D.products.white.x * sx - (D.products.white.h * sx) / 4, top: Y(D.products.white.y) - (D.products.white.h * sx) / 2, width: (D.products.white.h * sx) / 2, height: D.products.white.h * sx }]} />

      {/* the dropper (behind the bottle while it is in it), its bulb apart so it can be squeezed */}
      <Animated.View style={[styles.abs, { left: geo.bx - geo.dw / 2, top: geo.dtop, width: geo.dw, height: geo.dh }, dropperStyle]} pointerEvents="none">
        <Animated.View style={[styles.clip, { height: bulbH }, bulbStyle]}>
          <Image source={IMG.dropper} style={{ width: geo.dw, height: geo.dh }} />
        </Animated.View>
        <View style={[styles.clip, { top: bulbH, height: geo.dh - bulbH }]}>
          <Image source={IMG.dropper} style={{ width: geo.dw, height: geo.dh, marginTop: -bulbH }} />
        </View>
      </Animated.View>
      <Image source={IMG.bottle} style={[styles.abs, { left: geo.bx - geo.bw / 2, top: geo.btop, width: geo.bw, height: geo.bh }]} />

      {size && <SdrGpu frame={frame} width={L.w} height={L.h} disabled={false} onAvailable={setGpuOk} />}

      <GestureDetector gesture={hold}>
        <View style={[styles.abs, { left: geo.card.x, top: geo.card.y + 40, width: geo.card.w - 46, height: geo.card.h - 40 }]} accessible accessibilityRole="button" accessibilityLabel="Fruit oil. Touch and hold to apply it" />
      </GestureDetector>

      {D.cards.map((c, i) => (
        <Pressable key={i} style={[styles.check, { left: c.x * sx + D.check.dx * sx - 22, top: Y(c.y) + D.check.dy - 22 }]} onPress={() => toggleTick(i)} accessibilityRole="checkbox" accessibilityState={{ checked: ticks[i] }} accessibilityLabel={`${STEPS[i].step} done`}>
          <View style={[styles.checkRing, !ticks[i] && styles.checkEmpty]} />
          {i === 0 ? (
            <Animated.View style={[styles.checkMark, tick0]}>
              <CheckIcon />
            </Animated.View>
          ) : (
            ticks[i] && (
              <View style={styles.checkMark}>
                <CheckIcon />
              </View>
            )
          )}
        </Pressable>
      ))}

      {fontsLoaded &&
        D.labels.map((l, i) => (
          <View key={i} style={[styles.abs, { left: l.x * sx, top: Y(l.y) - 10 }]}>
            <Text style={styles.stepName} allowFontScaling={false}>
              {STEPS[i].name}
            </Text>
            <Text style={styles.stepNo} allowFontScaling={false}>
              {STEPS[i].step}
            </Text>
          </View>
        ))}

      {/* the bar */}
      <View style={[styles.nav, { top: Y(D.nav.y), height: L.h - Y(D.nav.y) }]}>
        {D.nav.xs.map((x, i) => (
          <View key={i} style={[styles.navIcon, { left: x * sx - 18 }]}>
            <NavIcon kind={i} />
          </View>
        ))}
      </View>
      <PressScale style={[styles.fab, { left: D.nav.fab.x * sx - D.nav.fab.r, top: Y(D.nav.fab.y) - D.nav.fab.r, width: D.nav.fab.r * 2, height: D.nav.fab.r * 2, borderRadius: D.nav.fab.r }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Scan your face">
        <Svg width={26} height={26} viewBox="0 0 26 26">
          <SvgPath d="M2 8 V5 A3 3 0 0 1 5 2 H8 M18 2 H21 A3 3 0 0 1 24 5 V8 M24 18 V21 A3 3 0 0 1 21 24 H18 M8 24 H5 A3 3 0 0 1 2 21 V18" stroke="#FFFFFF" strokeWidth={1.8} fill="none" strokeLinecap="round" />
          <SvgCircle cx={9.5} cy={10.5} r={1.3} fill="#FFFFFF" />
          <SvgCircle cx={16.5} cy={10.5} r={1.3} fill="#FFFFFF" />
          <SvgPath d="M9 16 Q13 19 17 16" stroke="#FFFFFF" strokeWidth={1.6} fill="none" strokeLinecap="round" />
        </Svg>
      </PressScale>

      <Animated.View style={[styles.finger, fingerStyle]} pointerEvents="none" />
    </View>
  );
}

function CheckIcon() {
  return (
    <Svg width={13} height={10} viewBox="0 0 13 10">
      <SvgPath d="M1.5 5.2 L4.8 8.4 L11.5 1.6" stroke="#151518" strokeWidth={1.8} fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

function NavIcon({ kind }: { kind: number }) {
  const c = '#9A9AA4';
  if (kind === 0)
    return (
      <Svg width={22} height={22} viewBox="0 0 22 22">
        <SvgPath d="M11 2 L19.5 7 V16 A3 3 0 0 1 16.5 19 H5.5 A3 3 0 0 1 2.5 16 V7 Z" stroke={c} strokeWidth={1.6} fill="none" strokeLinejoin="round" />
        <SvgRect x={9.5} y={12} width={3} height={4} rx={1} fill={c} />
      </Svg>
    );
  if (kind === 1)
    return (
      <Svg width={22} height={22} viewBox="0 0 22 22">
        <SvgPath d="M3 4 H19 V15 H9 L5 19 V15 H3 Z" stroke={c} strokeWidth={1.6} fill="none" strokeLinejoin="round" />
        {[7, 11, 15].map((x) => (
          <SvgCircle key={x} cx={x} cy={9.5} r={1} fill={c} />
        ))}
      </Svg>
    );
  if (kind === 2)
    return (
      <Svg width={22} height={22} viewBox="0 0 22 22">
        <SvgPath d="M11 2 L18.8 6.5 V15.5 L11 20 L3.2 15.5 V6.5 Z" stroke={c} strokeWidth={1.6} fill="none" strokeLinejoin="round" />
        <SvgCircle cx={11} cy={11} r={3} stroke={c} strokeWidth={1.6} fill="none" />
      </Svg>
    );
  return (
    <Svg width={22} height={20} viewBox="0 0 22 20">
      <SvgPath d="M11 18.2 C 4 13.4 1.6 10.2 1.6 6.6 A 4.8 4.8 0 0 1 11 4.6 A 4.8 4.8 0 0 1 20.4 6.6 C 20.4 10.2 18 13.4 11 18.2 Z" stroke={c} strokeWidth={1.6} fill="none" strokeLinejoin="round" />
    </Svg>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.page, overflow: 'hidden' },
  abs: { position: 'absolute' },
  top: { position: 'absolute', left: 0, right: 0, top: 0, backgroundColor: COLOR.top, borderBottomLeftRadius: 28, borderBottomRightRadius: 28 },
  square: { position: 'absolute', width: 44, height: 44, borderRadius: 12, backgroundColor: COLOR.tile, alignItems: 'center', justifyContent: 'center' },
  title: { position: 'absolute', fontFamily: FONT.medium, fontSize: D.title.size, color: COLOR.ink },
  day: { position: 'absolute', borderRadius: 22, alignItems: 'center', justifyContent: 'center', gap: 2 },
  daySel: { backgroundColor: COLOR.tile, shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 10, shadowOffset: { width: 0, height: 4 } },
  dayN: { fontFamily: FONT.medium, fontSize: 24, color: COLOR.ink },
  dayD: { fontFamily: FONT.regular, fontSize: 12, color: COLOR.mute },
  dayDSel: { fontFamily: FONT.medium, color: COLOR.ink },
  card: { position: 'absolute', borderRadius: 22 },
  check: { position: 'absolute', width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  checkRing: { position: 'absolute', width: 30, height: 30, borderRadius: 15, backgroundColor: 'rgba(255,255,255,0.95)' },
  checkEmpty: { backgroundColor: 'transparent', borderWidth: 1.4, borderColor: 'rgba(255,255,255,0.95)' },
  checkMark: { position: 'absolute' },
  clip: { position: 'absolute', left: 0, right: 0, top: 0, overflow: 'hidden' },
  stepName: { fontFamily: FONT.regular, fontSize: 15, lineHeight: 20, color: COLOR.ink },
  stepNo: { marginTop: 4, fontFamily: FONT.regular, fontSize: 11, color: COLOR.mute },
  nav: { position: 'absolute', left: 0, right: 0, backgroundColor: COLOR.page, borderTopLeftRadius: 28, borderTopRightRadius: 28 },
  navIcon: { position: 'absolute', top: 22, width: 36, height: 36, alignItems: 'center', justifyContent: 'center' },
  fab: { position: 'absolute', backgroundColor: COLOR.ink, alignItems: 'center', justifyContent: 'center', borderWidth: 4, borderColor: '#FFFFFF' },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, borderWidth: 1.5, borderColor: 'rgba(20,20,20,0.35)', backgroundColor: 'rgba(20,20,20,0.06)' },
});
