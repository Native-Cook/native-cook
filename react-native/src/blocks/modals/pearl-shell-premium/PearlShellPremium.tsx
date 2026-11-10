import { Canvas, Circle, Rect, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { cubicBezier, useAnimatedReaction, useDerivedValue, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle, Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { frameAt, giftAt, GIFT, LOOP, PICK, pickAt } from './pps-motion';
import { PPS_SKSL } from './pps-shader';
import { BRAND, COLOR, D, FEATURES, FRIEND, makeLayout, PLANS } from './pps-theme';
import { makeUniforms } from './pps-uniforms';

/*
 Pearl Shell Premium — a "gift premium" sheet of an invented messenger, Nacre, laid out from the
 reference pin (636414991126737386): Cancel; a round hero in a field of sparkles; "Gift Nacre
 Premium" and a line about the friend; three plans with radio buttons, savings badges and prices per
 month; three features with coloured icons; the gift button on its own panel.

 Where the pin shows the friend's photo, the disc holds a pearl of glass in a violet tunnel of neon
 light: it bends the arcs behind it and carries a nacre sheen. Choose a plan and the pearl turns a
 third of a turn while the sparkles flare, the check moves and the button's price follows; press the
 button and the pearl rises and glows, the sparkles are thrown out and drift back (a success haptic).

 autoPlay: LOOP = 10 s (6 Months, 3 Months, 1 Year, the gift) that starts and ends on the pin's
 state. A touch takes over; 2 s after the last one the plan goes back to 1 Year and the loop comes
 round. Reduced motion: no loop, the sparkles still, the pearl does not turn; the check moves at once.
*/

const effect = Skia.RuntimeEffect.Make(PPS_SKSL);
if (!effect) throw new Error('Pearl Shell Premium: the shader failed to compile.');
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

function tick() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.selectionAsync().catch(() => {});
}
function gifted() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function PearlShellPremium({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);
  const sx = L.sx;
  const X = (x: number) => x * sx;
  const Y = L.Y;
  const geo = useMemo(() => ({ w, h, orb: [D.orb.x * sx, L.Y(D.orb.y), D.orb.r * sx] as [number, number, number] }), [w, h, sx, L]);
  // where the scripted finger goes, as plain numbers for the UI thread
  const spots = useMemo(() => ({ rowX: (D.plans.radioX + 150) * sx, rows: D.plans.rows.map((y) => L.Y(y)), ctaY: L.Y(D.cta.y + D.cta.h / 2) }), [sx, L]);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const lastTouch = useSharedValue(-100);
  const pin = useSharedValue(-1);
  const loopT = useSharedValue(0);
  const prevT = useSharedValue(0);
  const plan = useSharedValue(0);
  const spinBase = useSharedValue(0);
  const pickedAt = useSharedValue(-100);
  const giftedAt = useSharedValue(-100);
  const spin = useSharedValue(0);
  const lift = useSharedValue(0);
  const burst = useSharedValue(0);
  const glow = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const fingerPress = useSharedValue(0);
  const [chosen, setChosen] = useState(0);
  const [pressed, setPressed] = useState(false);

  const choose = (i: number) => {
    'worklet';
    if (i === plan.get()) return;
    plan.set(i);
    // carry on the turn from wherever it is
    spinBase.set(spin.get());
    pickedAt.set(clock.get());
    scheduleOnRN(setChosen, i);
    scheduleOnRN(tick);
  };
  const gift = () => {
    'worklet';
    giftedAt.set(clock.get());
    scheduleOnRN(gifted);
  };
  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    if (mode.get() === 0) {
      mode.set(1);
      finger.set(withTiming(0, { duration: 150 }));
      fingerPress.set(0);
      const s = frameAt(loopT.get());
      plan.set(s.plan);
      spinBase.set(s.spin);
      pickedAt.set(-100);
      giftedAt.set(-100);
      // what the loop showed fades out from here
      lift.set(withTiming(0, { duration: 500 }));
      burst.set(withTiming(0, { duration: 500 }));
      glow.set(withTiming(0, { duration: 500 }));
    }
  };

  /* ------------------------------------------------------------ clock */
  const ticker = useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    prevT.set(loopT.get());
    if (pin.get() >= 0) loopT.set(pin.get());
    else if (!reduced) loopT.set((loopT.get() + dt) % LOOP);
    if (mode.get() === 0) {
      const s = frameAt(loopT.get());
      if (s.plan !== plan.get()) {
        plan.set(s.plan);
        scheduleOnRN(setChosen, s.plan);
      }
      spin.set(s.spin);
      lift.set(s.lift);
      burst.set(s.burst);
      glow.set(s.glow);
      finger.set(s.finger);
      fingerPress.set(s.fingerPress);
      if (s.fingerAt < 3) {
        fingerX.set(spots.rowX);
        fingerY.set(spots.rows[s.fingerAt]);
      } else {
        fingerX.set(w / 2);
        fingerY.set(spots.ctaY);
      }
      return;
    }
    if (!reduced) {
      const p = pickAt(clock.get() - pickedAt.get());
      spin.set(spinBase.get() + (clock.get() - pickedAt.get() < PICK ? p.spin : (Math.PI * 2) / 3) * (pickedAt.get() > 0 ? 1 : 0));
      const g = giftAt(clock.get() - giftedAt.get());
      const flare = clock.get() - pickedAt.get() < PICK ? p.flare : 0;
      if (clock.get() - giftedAt.get() < GIFT) {
        lift.set(g.lift);
        glow.set(g.glow);
        burst.set(Math.max(g.burst, flare));
      } else if (clock.get() - pickedAt.get() < PICK) burst.set(flare);
    }
    if (!auto || clock.get() - lastTouch.get() < 2) return;
    if (plan.get() !== 0) {
      choose(0);
      return;
    }
    // the loop starts on 1 Year, its turn a whole turn: wait for the pearl to settle and the loop to come round
    const busy = clock.get() - pickedAt.get() < PICK || clock.get() - giftedAt.get() < GIFT;
    if (!busy && loopT.get() < prevT.get()) mode.set(0);
  }, false);
  // autostart only counts at mount, and a block opened from a link mounts while the app is inactive
  useEffect(() => {
    ticker.setActive(active);
  }, [ticker, active]);

  useEffect(() => {
    mode.set(auto ? 0 : 1);
    if (!auto)
      scheduleOnUI(() => {
        'worklet';
        finger.set(0);
        fingerPress.set(0);
        lift.set(0);
        burst.set(0);
        glow.set(0);
      });
  }, [auto, mode, finger, fingerPress, lift, burst, glow]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__pearlShell = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), plan: plan.get(), spin: +spin.get().toFixed(2), lift: +lift.get().toFixed(2) }),
    };
  }, [pin, clock, mode, plan, spin, lift]);

  useAnimatedReaction(
    () => (mode.get() === 0 ? frameAt(loopT.get()).pressed : -1),
    (now, before) => {
      if (now >= 0 && now !== before) scheduleOnRN(setPressed, now === 1);
    },
  );

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => makeUniforms(geo, { t: loopT.get(), spin: spin.get(), lift: lift.get(), burst: burst.get(), glow: glow.get() }));
  const fingerR = useDerivedValue(() => 22 * (1 - 0.12 * fingerPress.get()));
  const fingerFill = useDerivedValue(() => `rgba(255,255,255,${0.14 * finger.get()})`);
  const fingerRing = useDerivedValue(() => `rgba(255,255,255,${0.5 * finger.get()})`);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };
  const P = D.plans;
  const F = D.feats;
  const panelTop = Y(D.panel.y);

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={w} height={Y(260)}>
          <Shader source={effect!} uniforms={uniforms} />
        </Rect>
      </Canvas>

      <Pressable style={[styles.abs, { left: X(D.cancel.x) - 8, top: Y(D.cancel.y) - 22, height: 44, justifyContent: 'center', paddingHorizontal: 8 }]} accessibilityRole="button" accessibilityLabel="Cancel">
        <Text style={styles.cancel} allowFontScaling={false}>
          Cancel
        </Text>
      </Pressable>
      <View style={[styles.abs, { left: geo.orb[0] - geo.orb[2], top: geo.orb[1] - geo.orb[2], width: geo.orb[2] * 2, height: geo.orb[2] * 2 }]} accessible accessibilityRole="image" accessibilityLabel={`${BRAND} Premium pearl`} />
      <Text style={[styles.title, { top: Y(D.title.y) - 17 }]} allowFontScaling={false} accessibilityRole="header">
        Gift {BRAND} Premium
      </Text>
      <Text style={[styles.sub, { top: Y(D.sub.y) - 9, left: X(24), right: X(24) }]} allowFontScaling={false}>
        Give <Text style={styles.bold}>{FRIEND}</Text> access to exclusive features with <Text style={styles.bold}>{BRAND} Premium</Text>.
      </Text>

      {/* the plans */}
      <View style={[styles.card, { left: X(P.x), top: Y(P.y), width: X(P.w), height: P.h * L.k, borderRadius: P.r }]} accessibilityRole="radiogroup">
        {P.sepY.map((y) => (
          <View key={y} style={[styles.sep, { left: X(P.textX - P.x), right: 0, top: Y(y) - Y(P.y) }]} />
        ))}
        {PLANS.map((p, i) => {
          const cy = Y(P.rows[i]) - Y(P.y);
          const on = i === chosen;
          return (
            <Pressable
              key={p.name}
              style={[styles.abs, { left: 0, right: 0, top: cy - 30 * L.k, height: 60 * L.k }]}
              accessibilityRole="radio"
              accessibilityState={{ checked: on }}
              accessibilityLabel={`${p.name}, ${p.price}${p.badge ? `, save ${p.badge.slice(1)}` : ''}, ${p.month}`}
              onPress={() =>
                scheduleOnUI(() => {
                  'worklet';
                  takeOver();
                  choose(i);
                })
              }
            >
              <View style={[styles.radio, { left: X(P.radioX - P.x) - 12, top: 30 * L.k - 12 }]}>
                <Animated.View style={[styles.radioOn, { opacity: on ? 1 : 0, transform: [{ scale: on ? 1 : 0.6 }], transitionProperty: ['opacity', 'transform'], transitionDuration: reduced ? 0 : 200, transitionTimingFunction: CSS_EASE_OUT }]}>
                  <Svg width={14} height={14} viewBox="0 0 14 14">
                    <SvgPath d="M3 7.4 L5.9 10.2 L11 4" stroke="#FFFFFF" strokeWidth={2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
                  </Svg>
                </Animated.View>
              </View>
              <View style={[styles.abs, { left: X(P.textX - P.x), top: (p.was ? 4 : 18) * L.k, flexDirection: 'row', alignItems: 'center', gap: 6 }]}>
                <Text style={styles.planName} allowFontScaling={false}>
                  {p.name}
                </Text>
                {!!p.badge && (
                  <View style={styles.badge}>
                    <Text style={styles.badgeText} allowFontScaling={false}>
                      {p.badge}
                    </Text>
                  </View>
                )}
              </View>
              {!!p.was && (
                <Text style={[styles.was, { left: X(P.textX - P.x), top: 30 * L.k + 3 }]} allowFontScaling={false}>
                  <Text style={styles.strike}>{p.was}</Text> {p.price}
                </Text>
              )}
              <Text style={[styles.month, { right: X(402 - 366 - P.x) - X(0), top: 30 * L.k - 13 }]} allowFontScaling={false}>
                {p.month}
              </Text>
            </Pressable>
          );
        })}
      </View>

      {/* the features */}
      <View style={[styles.card, { left: X(F.x), top: Y(F.y), width: X(F.w), height: h - Y(F.y), borderTopLeftRadius: F.r, borderTopRightRadius: F.r, borderBottomLeftRadius: 0, borderBottomRightRadius: 0 }]}>
        {F.sepY.map((y) => (
          <View key={y} style={[styles.sep, { left: X(84 - F.x), right: 0, top: Y(y) - Y(F.y) }]} />
        ))}
        {FEATURES.map((f, i) => {
          const cy = Y(F.rows[i]) - Y(F.y);
          return (
            <View key={f.title} style={[styles.abs, { left: 0, right: 0, top: cy - 36, height: 72 }]} accessible accessibilityLabel={`${f.title}. ${f.desc}`}>
              <View style={[styles.featIcon, { left: X(34 - F.x), top: 36 - F.icon / 2, backgroundColor: f.color }]}>
                <FeatureGlyph i={i} />
              </View>
              <Text style={[styles.featTitle, { left: X(84 - F.x), top: 36 - 25 }]} allowFontScaling={false}>
                {f.title}
              </Text>
              <Text style={[styles.featDesc, { left: X(84 - F.x), right: X(30), top: 36 - 2 }]} allowFontScaling={false}>
                {f.desc}
              </Text>
              <View style={[styles.abs, { right: X(386 - 372), top: 36 - 8 }]}>
                <Svg width={9} height={16} viewBox="0 0 9 16">
                  <SvgPath d="M1.5 1.5 L7.5 8 L1.5 14.5" stroke="#5A5A5E" strokeWidth={2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
                </Svg>
              </View>
            </View>
          );
        })}
      </View>

      {/* the gift button on its own panel */}
      <View style={[styles.panel, { top: panelTop }]} />
      <PressScale
        style={[styles.abs, { left: X(D.cta.x), top: Y(D.cta.y), width: X(D.cta.w), height: D.cta.h * L.k }]}
        reduced={reduced}
        accessibilityRole="button"
        accessibilityLabel={`Gift subscription for ${PLANS[chosen].price}`}
        onPress={() =>
          scheduleOnUI(() => {
            'worklet';
            takeOver();
            gift();
          })
        }
      >
        <Animated.View style={[styles.ctaWrap, { transform: [{ scale: pressed ? 0.97 : 1 }], transitionProperty: 'transform', transitionDuration: 150, transitionTimingFunction: CSS_EASE_OUT }]}>
          <LinearGradient colors={[COLOR.ctaA, '#A274E7', COLOR.ctaB]} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={[styles.cta, { borderRadius: D.cta.r }]}>
            <Text style={styles.ctaText} allowFontScaling={false}>
              Gift Subscription for {PLANS[chosen].price}
            </Text>
          </LinearGradient>
        </Animated.View>
      </PressScale>

      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerFill} />
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerRing} style="stroke" strokeWidth={1.5} />
      </Canvas>
    </View>
  );
}

function FeatureGlyph({ i }: { i: number }) {
  if (i === 1)
    return (
      <Text style={styles.x2} allowFontScaling={false}>
        X2
      </Text>
    );
  return (
    <Svg width={20} height={20} viewBox="0 0 20 20">
      {i === 0 && (
        <>
          <SvgPath d="M10 3 A7 7 0 0 0 10 17" stroke="#FFFFFF" strokeWidth={2.2} fill="none" strokeLinecap="round" />
          <SvgCircle cx={15.2} cy={5.6} r={1.2} fill="#FFFFFF" />
          <SvgCircle cx={17} cy={10} r={1.2} fill="#FFFFFF" />
          <SvgCircle cx={15.2} cy={14.4} r={1.2} fill="#FFFFFF" />
        </>
      )}
      {i === 2 && <SvgPath d="M10 2.5 a3 3 0 0 1 3 3 V10 a3 3 0 0 1 -6 0 V5.5 a3 3 0 0 1 3 -3 Z M4.5 9.5 a5.5 5.5 0 0 0 11 0 M10 15 V18" stroke="#FFFFFF" strokeWidth={2} fill="#FFFFFF" fillOpacity={0} strokeLinecap="round" />}
    </Svg>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.page, overflow: 'hidden' },
  abs: { position: 'absolute' },
  cancel: { fontSize: 18, color: COLOR.link },
  title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontSize: 28, lineHeight: 34, fontWeight: '700', color: COLOR.ink, letterSpacing: 0.2 },
  sub: { position: 'absolute', textAlign: 'center', fontSize: 16, lineHeight: 21, color: COLOR.ink },
  bold: { fontWeight: '700' },
  card: { position: 'absolute', backgroundColor: COLOR.card, overflow: 'hidden' },
  sep: { position: 'absolute', height: StyleSheet.hairlineWidth, backgroundColor: COLOR.sep },
  radio: { position: 'absolute', width: 24, height: 24, borderRadius: 12, borderWidth: 1.5, borderColor: COLOR.radio },
  radioOn: { position: 'absolute', left: -1.5, top: -1.5, width: 24, height: 24, borderRadius: 12, backgroundColor: COLOR.accent, alignItems: 'center', justifyContent: 'center' },
  planName: { fontSize: 18, lineHeight: 24, color: COLOR.ink },
  badge: { borderRadius: 5, backgroundColor: COLOR.accent, paddingHorizontal: 4, height: 19, justifyContent: 'center' },
  badgeText: { fontSize: 15, fontWeight: '700', color: COLOR.ink },
  was: { position: 'absolute', fontSize: 15, lineHeight: 20, color: COLOR.mute },
  strike: { textDecorationLine: 'line-through' },
  month: { position: 'absolute', fontSize: 19, lineHeight: 26, color: COLOR.mute },
  featIcon: { position: 'absolute', width: D.feats.icon, height: D.feats.icon, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  x2: { fontSize: 15, fontWeight: '800', color: COLOR.ink },
  featTitle: { position: 'absolute', fontSize: 18, lineHeight: 24, color: COLOR.ink },
  featDesc: { position: 'absolute', fontSize: 14.5, lineHeight: 17.5, color: COLOR.mute },
  panel: { position: 'absolute', left: 0, right: 0, bottom: 0, backgroundColor: COLOR.panel },
  ctaWrap: { flex: 1 },
  cta: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  ctaText: { fontSize: 18, fontWeight: '600', color: COLOR.ink },
});
