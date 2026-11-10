import { Canvas, LinearGradient, Rect, vec } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, useAnimatedReaction, useAnimatedStyle, useFrameCallback, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle, Path as SvgPath, Rect as SvgRect } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { CAST, frameAt, LOOP } from './gem-motion';
import { COLOR, D, FONT, makeLayout, PRIVILEGES, STATS, TIERS } from './gem-theme';
import { GemGpu } from './gpu/GemGpu';
import { useGemAudio } from './use-gem-audio';

/*
 Gold Emblem Membership — the member page of an invented bank, laid out from the reference pin
 (584412489138772843): the member card with its medal between the edges of the tiers beside it; three
 figures; a gift coupon; My privilege; Member benefits.

 The card is metal, drawn on WebGPU (TypeGPU-resolved WGSL) as a height field: a bevelled, brushed
 plate with two soft swells and a medal in relief under a ribbon, shaded in a studio whose light
 sweeps as the card is dragged. Swipe it to the next tier and it is cast again: a heat front runs in
 from the side it was swiped from, the metal glows red to white-hot and goes liquid (ripples, a churn,
 drips running off its lower edge), changes metal while molten, cools back the other way and is
 stamped with its new medal. Gold, Platinum, Black; a fling past the end goes straight to the far tier.

 autoPlay: LOOP = 10 s (gem-motion.ts); frame 10 s equals frame 0, the gold card. A touch takes over;
 2 s after the last one the card is cast back in gold and the loop restarts. Reduced motion: no loop;
 the card's metal and text cross-fade without melting.
*/

const pixelRatio = PixelRatio.get();
const MARGIN = 10;
const BELOW = 50;

function buzz(kind: 'heavy' | 'stamp' | 'tick') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'heavy') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy).catch(() => {});
  else if (kind === 'stamp') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.selectionAsync().catch(() => {});
}

type Props = { autoPlay?: boolean };

export function GoldEmblemMembership({ autoPlay = false }: Props) {
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
  const play = useGemAudio(true);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);
  const card = useMemo(() => ({ x: D.card.x * L.sx, y: L.Y(D.card.y), w: D.card.w * L.sx, h: D.card.h * L.k }), [L]);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const rate = useSharedValue(1);

  const from = useSharedValue(0);
  const to = useSharedValue(0);
  const cast = useSharedValue(0);
  const side = useSharedValue(1);
  const drag = useSharedValue(0);
  const dragging = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerPress = useSharedValue(0);
  const stamped = useSharedValue(0);
  const frame = useSharedValue<number[]>([]);
  const [shown, setShown] = useState(0);

  /* ------------------------------------------------------------ actions */
  const castTo = (next: number, dir: number) => {
    'worklet';
    if (next === to.get() && cast.get() === 0 && from.get() === next) return;
    from.set(cast.get() > 0 && cast.get() < 0.5 ? from.get() : to.get());
    to.set(next);
    side.set(dir);
    stamped.set(0);
    cast.set(0);
    cast.set(
      withTiming(1, { duration: reduced ? 400 : CAST * 1000, easing: Easing.linear }, (fin) => {
        if (!fin) return;
        from.set(next);
        cast.set(0);
      }),
    );
    scheduleOnRN(buzz, 'heavy');
    if (!reduced) scheduleOnRN(play, 'melt');
  };
  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    fingerPress.set(0);
    drag.set(withSpring(0, { duration: 400, dampingRatio: 0.7 }));
    // a cast of the loop finishes where it was going
    const c = cast.get();
    if (c > 0) {
      const target = to.get();
      cast.set(
        withTiming(1, { duration: (1 - c) * CAST * 1000, easing: Easing.linear }, (fin) => {
          if (!fin) return;
          from.set(target);
          cast.set(0);
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
      cast.set(s.cast);
      side.set(s.side);
      drag.set(s.drag);
      finger.set(s.finger);
      fingerX.set(s.fingerX);
      fingerPress.set(s.press);
    } else if (cast.get() > 0.86 && stamped.get() === 0) {
      stamped.set(1);
      scheduleOnRN(buzz, 'stamp');
      if (!reduced) scheduleOnRN(play, 'stamp');
    }
    frame.set([
      L.w, card.h + MARGIN + BELOW, pixelRatio, clock.get() % 600,
      card.x, MARGIN, card.w, card.h,
      from.get(), to.get(), reduced ? 0 : cast.get(), side.get(),
      drag.get() / 60, 0, 0, 0,
    ]);
    if (mode.get() === 0) return;
    if (!auto || dragging.get() === 1 || cast.get() > 0 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      if (to.get() !== 0) castTo(0, -1);
      return;
    }
    if (cast.get() === 0 && from.get() === 0 && to.get() === 0 && Math.abs(drag.get()) < 0.3 && clock.get() - lastTouch.get() > 2.4) {
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
      from.set(0);
      to.set(0);
      cast.set(0);
      drag.set(0);
      finger.set(0);
      fingerPress.set(0);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__goldEmblem = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      rate: (k: number) => rate.set(k),
      restart: () => {
        autoStart.set(clock.get());
        pin.set(-1);
      },
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), from: from.get(), to: to.get(), cast: +cast.get().toFixed(3), drag: +drag.get().toFixed(1) }),
    };
  }, [pin, rate, autoStart, clock, mode, from, to, cast, drag]);

  // the tier whose words are on the card: the old one until the metal is molten, then the new one
  useAnimatedReaction(
    () => (cast.get() >= 0.5 ? to.get() : from.get()),
    (now, before) => {
      if (now !== before) scheduleOnRN(setShown, now);
    },
  );

  /* ------------------------------------------------------------ gestures */
  const swipe = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .failOffsetY([-14, 14])
    .onBegin(() => {
      takeOver();
    })
    .onStart(() => {
      dragging.set(1);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      // the card gives under the finger, less and less
      drag.set(34 * Math.tanh(e.translationX / 110));
    })
    .onEnd((e) => {
      const cur = to.get();
      const far = Math.abs(e.velocityX) > 1800;
      let next = cur;
      if (e.translationX < -60 || e.velocityX < -600) next = far ? TIERS.length - 1 : Math.min(TIERS.length - 1, cur + 1);
      else if (e.translationX > 60 || e.velocityX > 600) next = far ? 0 : Math.max(0, cur - 1);
      if (next !== cur && cast.get() === 0) castTo(next, next > cur ? 1 : -1);
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      dragging.set(0);
      // the card springs home carrying the finger's speed: d(drag)/dt of 34 · tanh(x / 110)
      const sech = 1 / Math.cosh(e.translationX / 110);
      drag.set(withSpring(0, { duration: 600, dampingRatio: 0.55, velocity: ((34 * sech * sech) / 110) * e.velocityX }));
    });

  /* ------------------------------------------------------------ drawing */
  const cardStyle = useAnimatedStyle(() => ({ transform: [{ translateX: drag.get() }] }));
  // the words on the card go with the metal: gone while it is molten, back once it is cast
  const wordsStyle = useAnimatedStyle(() => {
    const c = cast.get();
    const o = c <= 0 ? 1 : c < 0.3 ? 1 - c / 0.3 : c < 0.82 ? 0 : (c - 0.82) / 0.18;
    return { opacity: o, transform: [{ translateY: c > 0 && c < 0.5 ? (c / 0.3) * 4 : 0 }] };
  });
  const fingerStyle = useAnimatedStyle(() => ({
    opacity: finger.get(),
    transform: [{ translateX: card.x + card.w / 2 + fingerX.get() - 22 }, { translateY: card.y + card.h / 2 - 22 }, { scale: 1 - 0.12 * fingerPress.get() }],
  }));
  const tier = TIERS[shown];
  const prev = TIERS[Math.max(0, shown - 1)];
  const next = TIERS[Math.min(TIERS.length - 1, shown + 1)];
  const flatColor = ['#D5B985', '#C4CDD1', '#2C2D31'][shown];

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };
  const Y = L.Y;
  const sx = L.sx;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <View style={[styles.dark, { height: Y(D.white) }]} />

      {/* the tiers beside this one, their edges showing */}
      <View style={[styles.sliver, { left: 0, top: card.y + 17, height: card.h - 34, backgroundColor: shown > 0 ? prev.sliver : '#4A4B50' }]} />
      <View style={[styles.sliver, { right: 0, top: card.y + 17, height: card.h - 34, backgroundColor: shown < TIERS.length - 1 ? next.sliver : '#4A4B50' }]} />

      <GestureDetector gesture={swipe}>
        <Animated.View
          style={[styles.abs, { left: 0, top: card.y - MARGIN, width: L.w, height: card.h + MARGIN + BELOW }, cardStyle]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={`${tier.name}, ${tier.range}`}
          accessibilityHint="Swipe to see the other tiers"
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(ev) =>
            scheduleOnUI(() => {
              'worklet';
              takeOver();
              const cur = to.get();
              const n = ev.nativeEvent.actionName === 'increment' ? Math.min(TIERS.length - 1, cur + 1) : Math.max(0, cur - 1);
              if (n !== cur) castTo(n, n > cur ? 1 : -1);
            })
          }
        >
          {!gpuOk && <View style={[styles.flat, { left: card.x, top: MARGIN, width: card.w, height: card.h, backgroundColor: flatColor }]} />}
          {size && <GemGpu frame={frame} width={L.w} height={card.h + MARGIN + BELOW} disabled={false} onAvailable={setGpuOk} />}
          {fontsLoaded && (
            <Animated.View style={[styles.abs, { left: card.x, top: MARGIN, width: card.w, height: card.h }, wordsStyle]} pointerEvents="none">
              <Text style={[styles.name, { color: tier.ink, left: (D.name.x - D.card.x) * sx, top: (D.name.y - D.card.y) * L.k - 16 }]} allowFontScaling={false}>
                {tier.name}
              </Text>
              <Text style={[styles.range, { color: tier.sub, left: (D.range.x - D.card.x) * sx, top: (D.range.y - D.card.y) * L.k - 9 }]} allowFontScaling={false}>
                {tier.range}
              </Text>
              <Text style={[styles.next, { color: tier.sub, left: (D.next.x - D.card.x) * sx, top: (D.next.y - D.card.y) * L.k - 8 }]} allowFontScaling={false}>
                {tier.next}
              </Text>
            </Animated.View>
          )}
        </Animated.View>
      </GestureDetector>

      {fontsLoaded && (
        <>
          {STATS.map((s) => (
            <View key={s.label} style={[styles.stat, { left: s.x * sx - 70, top: Y(D.stats.value) - 11 }]}>
              <Text style={styles.statValue} allowFontScaling={false}>
                {s.value}
              </Text>
              <Text style={styles.statLabel} allowFontScaling={false}>
                {s.label}
              </Text>
            </View>
          ))}

          <View style={[styles.strip, { left: D.coupon.x * sx, top: Y(D.coupon.y), width: D.coupon.w * sx, height: Y(D.white) - Y(D.coupon.y) }]}>
            <Svg width={46} height={32} viewBox="0 0 46 32" style={[styles.abs, { left: (D.coupon.ticket - D.coupon.x) * sx - 23, top: Y(D.coupon.cy) - Y(D.coupon.y) - 16 }]}>
              <SvgPath d="M6 6 H40 A2 2 0 0 1 42 8 V12 A4 4 0 0 0 42 20 V24 A2 2 0 0 1 40 26 H6 A2 2 0 0 1 4 24 V20 A4 4 0 0 0 4 12 V8 A2 2 0 0 1 6 6 Z" fill="#E9C27E" />
              <SvgPath d="M16 8 V24" stroke="#C99A55" strokeWidth={1.2} strokeDasharray="2 2" />
              <SvgCircle cx={29} cy={16} r={5} stroke="#C99A55" strokeWidth={1.4} fill="none" />
            </Svg>
            <Text style={[styles.pct, { left: (D.coupon.pct - D.coupon.x) * sx, top: Y(D.coupon.cy) - Y(D.coupon.y) - 22 }]} allowFontScaling={false}>
              {tier.coupon}
              <Text style={styles.pctSign}>%</Text>
            </Text>
            <Text style={[styles.couponLabel, { left: (D.coupon.label - D.coupon.x) * sx + (tier.coupon.length - 1) * 12, top: Y(D.coupon.cy) - Y(D.coupon.y) - 3 }]} allowFontScaling={false}>
              Gift coupon
            </Text>
            <PressScale style={[styles.receive, { left: (D.receive.x - D.coupon.x) * sx, top: Y(D.receive.y) - Y(D.coupon.y), width: D.receive.w * sx, height: D.receive.h }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Receive the gift coupon">
              <Text style={styles.receiveText} allowFontScaling={false}>
                Receive
              </Text>
            </PressScale>
          </View>

          <Text style={[styles.h2, { left: 18 * sx, top: Y(D.privilege.y) - 12 }]} allowFontScaling={false} accessibilityRole="header">
            My privilege
          </Text>
          <Text style={[styles.viewAll, { right: 26 * sx, top: Y(D.privilege.y) - 9 }]} allowFontScaling={false}>
            View all ›
          </Text>
          {PRIVILEGES.map((p) => (
            <View key={p.name} style={[styles.priv, { left: p.x * sx - 60, top: Y(D.privilege.icons) - 16 }]}>
              <PrivilegeIcon kind={p.icon} />
              <Text style={[styles.privName, { marginTop: Y(D.privilege.names) - Y(D.privilege.icons) - 26 }]} allowFontScaling={false}>
                {p.name}
              </Text>
              <Text style={styles.privValue} allowFontScaling={false}>
                {p.value}
              </Text>
            </View>
          ))}

          <Text style={[styles.h2, { left: D.benefits.x * sx, top: Y(D.benefits.y) - 12 }]} allowFontScaling={false} accessibilityRole="header">
            Member benefits
          </Text>
          <View style={[styles.benefit, { left: D.benefits.x * sx, top: Y(D.benefits.box.y), width: D.card.w * sx, height: D.benefits.box.h * L.k }]}>
            <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
              <Rect x={0} y={0} width={D.card.w * sx} height={D.benefits.box.h * L.k}>
                <LinearGradient start={vec(0, 0)} end={vec(D.card.w * sx, D.benefits.box.h * L.k)} colors={['#F3D79F', '#DDB373']} />
              </Rect>
            </Canvas>
            <View style={styles.badge}>
              <Svg width={36} height={40} viewBox="0 0 36 40">
                <SvgPath d="M10 22 L4 36 L11 33 L14 39 L19 26 Z M26 22 L32 36 L25 33 L22 39 L17 26 Z" fill="#F07A34" />
                <SvgCircle cx={18} cy={16} r={12} fill="#F7A341" />
                <SvgPath d="M18 9.5 L19.9 13.6 L24.3 14.1 L21 17.1 L21.9 21.5 L18 19.3 L14.1 21.5 L15 17.1 L11.7 14.1 L16.1 13.6 Z" fill="#FFFFFF" />
              </Svg>
            </View>
            <View style={styles.benefitText}>
              <Text style={styles.amount} allowFontScaling={false}>
                <Text style={styles.amountSign}>$</Text>10
              </Text>
              <Text style={styles.benefitLabel} allowFontScaling={false}>
                Member package
              </Text>
            </View>
            <PressScale style={styles.receiveWhite} reduced={reduced} accessibilityRole="button" accessibilityLabel="Receive the member package">
              <Text style={styles.receiveGold} allowFontScaling={false}>
                Receive
              </Text>
            </PressScale>
          </View>
        </>
      )}

      <Animated.View style={[styles.finger, fingerStyle]} pointerEvents="none" />
    </View>
  );
}

function PrivilegeIcon({ kind }: { kind: number }) {
  const ink = '#2A2A2C';
  const gold = '#F1C987';
  if (kind === 0)
    return (
      <Svg width={34} height={34} viewBox="0 0 34 34">
        <SvgRect x={10} y={13} width={20} height={17} fill={gold} />
        <SvgRect x={6} y={10} width={22} height={6} stroke={ink} strokeWidth={1.8} fill="none" />
        <SvgRect x={8} y={16} width={18} height={13} stroke={ink} strokeWidth={1.8} fill="none" />
        <SvgPath d="M17 10 V29 M17 10 C 13 4 9 6 12 10 M17 10 C 21 4 25 6 22 10" stroke={ink} strokeWidth={1.8} fill="none" />
      </Svg>
    );
  if (kind === 1)
    return (
      <Svg width={34} height={34} viewBox="0 0 34 34">
        <SvgCircle cx={19} cy={21} r={9} fill={gold} />
        <SvgRect x={12} y={3} width={10} height={6} stroke={ink} strokeWidth={1.8} fill="none" />
        <SvgCircle cx={17} cy={19} r={10} stroke={ink} strokeWidth={1.8} fill="none" />
        <SvgPath d="M13 18 L17 22 L21 18" stroke={ink} strokeWidth={1.8} fill="none" strokeLinecap="round" strokeLinejoin="round" />
      </Svg>
    );
  return (
    <Svg width={34} height={34} viewBox="0 0 34 34">
      <SvgCircle cx={19} cy={15} r={9} fill={gold} />
      <SvgCircle cx={17} cy={13} r={9.5} stroke={ink} strokeWidth={1.8} fill="none" />
      <SvgPath d="M11 21 L7 28 L11 27.5 L13 31 L15.5 23 M23 21 L27 28 L23 27.5 L21 31 L18.5 23" stroke={ink} strokeWidth={1.8} fill="none" strokeLinejoin="round" />
      <SvgPath d="M17 8.5 L18.4 11.5 L21.6 11.9 L19.2 14 L19.9 17.2 L17 15.6 L14.1 17.2 L14.8 14 L12.4 11.9 L15.6 11.5 Z" stroke={ink} strokeWidth={1.4} fill="none" strokeLinejoin="round" />
    </Svg>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.white, overflow: 'hidden' },
  dark: { position: 'absolute', left: 0, right: 0, top: 0, backgroundColor: COLOR.dark },
  abs: { position: 'absolute' },
  sliver: { position: 'absolute', width: 9, borderRadius: 4 },
  flat: { position: 'absolute', borderRadius: D.card.r },
  name: { position: 'absolute', fontFamily: FONT.semibold, fontSize: D.name.size },
  range: { position: 'absolute', fontFamily: FONT.regular, fontSize: D.range.size },
  next: { position: 'absolute', fontFamily: FONT.regular, fontSize: D.next.size },
  stat: { position: 'absolute', width: 140, alignItems: 'center', gap: 6 },
  statValue: { fontFamily: FONT.semibold, fontSize: 15, color: COLOR.white, fontVariant: ['tabular-nums'] },
  statLabel: { fontFamily: FONT.regular, fontSize: 11.5, color: COLOR.statLabel },
  strip: { position: 'absolute', backgroundColor: COLOR.strip, borderTopLeftRadius: 10, borderTopRightRadius: 10, overflow: 'hidden' },
  pct: { position: 'absolute', fontFamily: FONT.bold, fontSize: 30, color: COLOR.white },
  pctSign: { fontFamily: FONT.semibold, fontSize: 15 },
  couponLabel: { position: 'absolute', fontFamily: FONT.regular, fontSize: 13, color: COLOR.white },
  receive: { position: 'absolute', borderRadius: 17, backgroundColor: '#E9C17F', alignItems: 'center', justifyContent: 'center' },
  receiveText: { fontFamily: FONT.medium, fontSize: 14, color: '#4A361C' },
  h2: { position: 'absolute', fontFamily: FONT.bold, fontSize: 17, color: COLOR.ink },
  viewAll: { position: 'absolute', fontFamily: FONT.regular, fontSize: 13, color: COLOR.mute },
  priv: { position: 'absolute', width: 120, alignItems: 'center' },
  privName: { fontFamily: FONT.regular, fontSize: 13, color: COLOR.label },
  privValue: { marginTop: 6, fontFamily: FONT.regular, fontSize: 11.5, color: COLOR.gold },
  benefit: { position: 'absolute', borderRadius: 10, overflow: 'hidden', flexDirection: 'row', alignItems: 'center', paddingHorizontal: 18 },
  badge: { width: 62, height: 61, borderRadius: 10, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center' },
  benefitText: { flex: 1, marginLeft: 16 },
  amount: { fontFamily: FONT.bold, fontSize: 26, color: '#2A1E10' },
  amountSign: { fontSize: 15 },
  benefitLabel: { marginTop: 2, fontFamily: FONT.regular, fontSize: 13, color: '#3A2A14' },
  receiveWhite: { height: 33, paddingHorizontal: 22, borderRadius: 17, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center' },
  receiveGold: { fontFamily: FONT.medium, fontSize: 14, color: '#D19A4A' },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.6)', backgroundColor: 'rgba(255,255,255,0.1)' },
});
