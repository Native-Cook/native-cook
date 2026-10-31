import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
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
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { frameAt, HOLD, look, LOOP, sway } from './lms-motion';
import { LMS_SKSL } from './lms-shader';
import { BUTTON_H, CARD_GAP, CARD_H, COLOR, FONT, makeLayout, PAGE_RGB, RATE, ROW_H, SPACE, type Token, TOKENS } from './lms-theme';
import { type LmsCue, useLmsAudio } from './use-lms-audio';

/*
 Liquid Metal Swap — the swap screen of an invented wallet.

 The screen: the title and the slippage; the token you hold, a chrome coin standing on a dark floor;
 what you pay and what you receive; the rate, the price impact, the fee; Hold to swap.

 Signature: holding the button is the swap. The silver Prism coin sinks into a pool of mercury that
 spreads round it, the pool turns gold, and the gold Spark coin rises out of it. The hold is the
 clock: let go half way and the metal runs back at the speed it had; hold again and it carries on
 from where it is, in the same direction (the direction is fixed when a hold starts from rest). Once
 a hold completes, the cards trade places and the next hold swaps back. Turn the coin with a finger;
 it coasts and settles face on. One Skia shader ray-marches the metal; it reflects a studio with a
 hard horizon, one sample per channel, which is what makes it read as chrome.

 autoPlay: LOOP = 10 s (hold to Spark, turn the coin, a hold let go half way, hold back to Prism);
 frame 10 s equals frame 0. A touch takes over; 2 s after the last one the loop eases back.
 Reduced motion: no sway, no turning; the hold still fills the button and the coin changes at once.
*/

const effect = Skia.RuntimeEffect.Make(LMS_SKSL);
if (!effect) throw new Error('Liquid Metal Swap: the metal shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const NEVER = ReduceMotion.Never;
const COIN_R = 78;

function buzz(kind: 'light' | 'medium' | 'success') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'success') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(kind === 'medium' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function LiquidMetalSwap({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsLoaded] = useFonts({
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
  const audio = useLmsAudio(!reduced);
  const cue = useCallback((c: LmsCue) => audio.play(c), [audio]);
  const [role, setRole] = useState(0); // 0 you pay Prism, 1 you pay Spark

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1); // 0 the loop, 1 the finger
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const stay = useSharedValue(0); // dev: keep the finger's turn (no resume), for testing touches

  const fill = useSharedValue(0); // how far the hold has gone
  const start = useSharedValue(0); // the end the hold started from, latched at rest
  const holding = useSharedValue(0);
  const flip = useSharedValue(0);
  const swapped = useSharedValue(0);
  const doneText = useSharedValue(0);
  const press = useSharedValue(0);
  const yaw = useSharedValue(0); // the finger's turn of the coin
  const yawVel = useSharedValue(0);
  const turning = useSharedValue(0);
  const lift = useSharedValue(0); // the studio, raised or lowered by the finger
  const finger = useSharedValue(0);
  const fingerAt = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const prevX = useSharedValue(0);

  const m = useDerivedValue(() => {
    if (reduced) return start.get();
    return start.get() === 0 ? fill.get() : 1 - fill.get();
  });

  /* ------------------------------------------------------------ completing a swap */
  const onDone = useCallback(() => {
    cue('cast');
    buzz('success');
  }, [cue]);

  const complete = () => {
    'worklet';
    holding.set(0);
    const next = 1 - start.get();
    start.set(next);
    fill.set(0);
    flip.set(withTiming(next, { duration: 350, easing: EASE_IN_OUT, reduceMotion: NEVER }));
    swapped.set(1);
    swapped.set(withDelay(1100, withTiming(0, { duration: 300 })));
    doneText.set(withTiming(1, { duration: 200 }, (finished) => {
      if (finished) doneText.set(withDelay(900, withTiming(0, { duration: 300 })));
    }));
    scheduleOnRN(onDone);
  };

  /* ------------------------------------------------------------ clock */
  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      fill.set(s.fill);
      start.set(s.start);
      flip.set(s.flip);
      swapped.set(s.swapped);
      doneText.set(s.doneText);
      press.set(s.press);
      yaw.set(s.yaw);
      finger.set(s.finger);
      fingerAt.set(s.fingerAt);
      fingerX.set(s.fingerX);
      return;
    }
    if (holding.get() === 1) {
      fill.set(Math.min(1, fill.get() + dt / HOLD));
      if (fill.get() >= 1) complete();
    }
    // the coin coasts, then settles face on (either face carries the mark)
    if (turning.get() === 0 && !reduced) {
      let v = yawVel.get() * Math.exp(-2.2 * dt);
      if (Math.abs(v) < 2.5) {
        const target = Math.round(yaw.get() / Math.PI) * Math.PI;
        v += (-14 * (yaw.get() - target) - 5 * v) * dt;
      }
      yawVel.set(v);
      yaw.set(yaw.get() + v * dt);
    }
    if (auto && stay.get() === 0 && resumeAsked.get() === 0 && holding.get() === 0 && turning.get() === 0 && clock.get() - lastTouch.get() > 2 && fill.get() < 0.002 && Math.abs(yawVel.get()) < 0.05) {
      resumeAsked.set(1);
      // the loop starts holding Prism: swap back first if Spark is in hand
      if (start.get() === 1) holding.set(1);
    } else if (resumeAsked.get() === 1 && holding.get() === 0 && start.get() === 0 && fill.get() < 0.002 && Math.abs(yaw.get() - Math.round(yaw.get() / (2 * Math.PI)) * 2 * Math.PI) < 0.02) {
      resumeAsked.set(0);
      yaw.set(0);
      autoStart.set(clock.get());
      mode.set(0);
    }
  }, active);

  useEffect(() => {
    mode.set(auto ? 0 : 1);
    autoStart.set(clock.get());
  }, [auto, mode, autoStart, clock]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__lms = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      stay: (on: boolean) => {
        stay.set(on ? 1 : 0);
        if (on) mode.set(1);
      },
      state: () => JSON.stringify({ mode: mode.get(), fill: +fill.get().toFixed(3), start: start.get(), m: +m.get().toFixed(3), yaw: +yaw.get().toFixed(3) }),
    };
  }, [pin, stay, mode, fill, start, m, yaw]);

  // which way the quote reads follows the cards, in the loop and under the finger
  useAnimatedReaction(
    () => (flip.get() > 0.5 ? 1 : 0),
    (now, before) => {
      if (before !== null && now !== before) scheduleOnRN(setRole, now);
    },
  );

  // the coin going under: a glug and a thud, from a finger only
  const onUnder = useCallback(() => {
    cue('pour');
    buzz('medium');
  }, [cue]);
  useAnimatedReaction(
    () => (look(m.get()).sink > 0.96 ? 1 : 0),
    (now, before) => {
      if (before === null || now === before || now === 0) return;
      if (mode.get() === 1 && !reduced) scheduleOnRN(onUnder);
    },
  );

  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() === 0) {
      mode.set(1);
      finger.set(withTiming(0, { duration: 150 }));
      press.set(0);
      yawVel.set(0);
      // a hold the loop had under way carries on under the finger's own clock
      swapped.set(withTiming(0, { duration: 200 }));
      doneText.set(withTiming(0, { duration: 200 }));
    }
  };

  const onPressStart = useCallback(() => {
    cue('press');
    buzz('light');
  }, [cue]);

  // the button: the hold is the clock
  const hold = Gesture.Pan()
    .minDistance(0)
    .shouldCancelWhenOutside(false)
    .onBegin(() => {
      takeOver();
      fill.set(fill.get()); // catch a run home mid-way and carry on from there
      holding.set(1);
      press.set(withTiming(1, { duration: 100, easing: EASE_OUT }));
      scheduleOnRN(onPressStart);
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      press.set(withTiming(0, { duration: 150, easing: EASE_OUT }));
      if (holding.get() === 0) return;
      holding.set(0);
      // let go early: the metal runs home at the speed it had
      fill.set(reduced ? withTiming(0, { duration: 150, reduceMotion: NEVER }) : withSpring(0, { duration: 600, dampingRatio: 1, velocity: 1 / HOLD }));
    });

  // the coin: turn it; drag up or down to raise or lower the studio's lights
  const turn = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      takeOver();
      turning.set(1);
      yawVel.set(0);
      prevX.set(0);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      const dx = e.translationX - prevX.get();
      prevX.set(e.translationX);
      if (reduced) return;
      yaw.set(yaw.get() + dx / (COIN_R * 0.9));
      lift.set(Math.max(-0.12, Math.min(0.12, -e.translationY / 900)));
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      turning.set(0);
      if (reduced) return;
      yawVel.set(Math.max(-14, Math.min(14, e.velocityX / (COIN_R * 0.9))));
      lift.set(withSpring(0, { duration: 700, dampingRatio: 0.8 }));
    });

  const swapNow = useCallback(() => {
    // VoiceOver's double tap: the whole swap, no hold needed
    takeOver();
    if (reduced) {
      fill.set(1);
      complete();
      return;
    }
    holding.set(0);
    fill.set(withTiming(1, { duration: 900, easing: EASE_IN_OUT }, (finished) => {
      if (finished) complete();
    }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reduced, onDone]);

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => {
    const k = look(m.get());
    const t = clock.get();
    const swayNow = reduced ? 0 : mode.get() === 0 ? sway(((t - autoStart.get()) % LOOP + LOOP) % LOOP) : sway(t);
    const s = TOKENS[0].tint;
    const g = TOKENS[1].tint;
    return {
      uStage: [L.w, L.stageH, pixelRatio, t],
      uCoin: [k.kind, k.sink, k.soft, swayNow + yaw.get()],
      uPool: [k.poolR, k.poolH, k.wobble, reduced ? 0 : k.drops],
      uLight: [0, lift.get()],
      uTint: [s[0] + (g[0] - s[0]) * k.gold, s[1] + (g[1] - s[1]) * k.gold, s[2] + (g[2] - s[2]) * k.gold],
      uBg: PAGE_RGB,
    };
  });

  // the token's name under the coin, crossfading as the metal changes
  const nameA = useAnimatedStyle(() => {
    const k = look(m.get());
    return { opacity: k.kind === 0 ? 1 - k.sink : 0 };
  });
  const nameB = useAnimatedStyle(() => {
    const k = look(m.get());
    return { opacity: k.kind === 1 ? 1 - k.sink : 0 };
  });

  // the two token rows trade places between the cards
  const slot = CARD_H + CARD_GAP;
  // they dip out of sight as they pass each other, so the amounts never cross over the labels
  const rowA = useAnimatedStyle(() => ({ opacity: 1 - 0.92 * Math.sin(Math.PI * flip.get()), transform: [{ translateY: flip.get() * slot }] }));
  const rowB = useAnimatedStyle(() => ({ opacity: 1 - 0.92 * Math.sin(Math.PI * flip.get()), transform: [{ translateY: (1 - flip.get()) * slot }] }));

  // the button: the hold fills it with the colour of the coin it is casting
  const fillW = L.cardW;
  const fillStyle = useAnimatedStyle(() => ({ width: Math.max(0, Math.min(1, fill.get())) * fillW }));
  const fillTint = useAnimatedStyle(() => ({ backgroundColor: start.get() === 0 ? COLOR.gold : COLOR.silver }));
  const buttonPress = useAnimatedStyle(() => ({ transform: [{ scale: reduced ? 1 : 1 - 0.04 * press.get() }] }));
  const holdLabel = useAnimatedStyle(() => ({ opacity: 1 - swapped.get() }));
  const doneHold = useAnimatedStyle(() => ({ opacity: Math.min(swapped.get(), 1 - doneText.get()) }));
  const doneLabel = useAnimatedStyle(() => ({ opacity: Math.min(swapped.get(), doneText.get()) }));
  const doneBg = useAnimatedStyle(() => ({ opacity: swapped.get(), backgroundColor: start.get() === 1 ? COLOR.gold : COLOR.silver }));

  const fingerStyle = useAnimatedStyle(() => {
    const onCoin = fingerAt.get() === 1;
    const x = onCoin ? L.w / 2 - 40 + 90 * fingerX.get() : L.w / 2;
    const y = onCoin ? L.stageTop + L.stageH * 0.42 : L.buttonTop + BUTTON_H / 2;
    return { opacity: 0.85 * finger.get(), transform: [{ translateX: x - 22 }, { translateY: y - 22 }, { scale: 1 - 0.12 * press.get() }] };
  });

  const onLayout = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize((s) => (s && s.w === width && s.h === height ? s : { w: width, h: height }));
  }, []);

  if (!fontsLoaded) return <View style={styles.root} onLayout={onLayout} />;
  const pay = TOKENS[role];
  const get = TOKENS[1 - role];

  return (
    <View style={styles.root} onLayout={onLayout}>
      <StatusBar style="light" />

      {/* title */}
      <View style={[styles.bar, { top: L.barTop, left: L.side, right: L.side }]}>
        <Text style={styles.title} accessibilityRole="header">Swap</Text>
        <View style={styles.pill}>
          <Text style={styles.pillText}>Slippage 0.5%</Text>
        </View>
      </View>

      {/* the coin */}
      <GestureDetector gesture={turn}>
        <View
          style={{ position: 'absolute', left: 0, top: L.stageTop, width: L.w, height: L.stageH }}
          accessible
          accessibilityRole="image"
          accessibilityLabel={`${pay.name} coin, the token you hold`}
          accessibilityHint={reduced ? undefined : 'Drag sideways to turn it'}
        >
          {active ? (
            <Canvas style={{ width: L.w, height: L.stageH }}>
              <Fill>
                <Shader source={effect!} uniforms={uniforms} />
              </Fill>
            </Canvas>
          ) : null}
          <View style={styles.nameWrap} pointerEvents="none">
            <Animated.Text style={[styles.coinName, nameA]} allowFontScaling={false}>{TOKENS[0].name} · {TOKENS[0].symbol}</Animated.Text>
            <Animated.Text style={[styles.coinName, styles.coinNameOver, nameB]} allowFontScaling={false}>{TOKENS[1].name} · {TOKENS[1].symbol}</Animated.Text>
          </View>
        </View>
      </GestureDetector>

      {/* you pay, you receive */}
      <View style={{ position: 'absolute', left: L.side, top: L.cardsTop, width: L.cardW, height: 2 * CARD_H + CARD_GAP }}>
        <View style={[styles.card, { top: 0 }]}>
          <Text style={styles.cardLabel}>You pay</Text>
        </View>
        <View style={[styles.card, { top: slot }]}>
          <Text style={styles.cardLabel}>You receive</Text>
        </View>
        <Animated.View style={[styles.row, rowA]} accessible accessibilityLabel={`${role === 0 ? 'You pay' : 'You receive'} ${TOKENS[0].amount} ${TOKENS[0].symbol}, ${TOKENS[0].fiat}`}>
          <TokenRow token={TOKENS[0]} />
        </Animated.View>
        <Animated.View style={[styles.row, rowB]} accessible accessibilityLabel={`${role === 1 ? 'You pay' : 'You receive'} ${TOKENS[1].amount} ${TOKENS[1].symbol}, ${TOKENS[1].fiat}`}>
          <TokenRow token={TOKENS[1]} />
        </Animated.View>
        <View style={[styles.divider, { top: CARD_H + CARD_GAP / 2 - 18, left: L.cardW / 2 - 18 }]} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          <Svg width={16} height={16} viewBox="0 0 16 16">
            <SvgPath d="M8 2.5v11M3.5 9l4.5 4.5L12.5 9" stroke={COLOR.mute} strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" fill="none" />
          </Svg>
        </View>
      </View>

      {/* the quote */}
      <View style={{ position: 'absolute', left: L.side + SPACE.xs, right: L.side + SPACE.xs, top: L.rowsTop }}>
        <QuoteRow label="Rate" value={RATE[role]} />
        <QuoteRow label="Price impact" value="0.08%" />
        <QuoteRow label="Network fee" value="$0.12" />
      </View>

      {/* hold to swap */}
      <GestureDetector gesture={hold}>
        <Animated.View
          style={[styles.button, { left: L.side, top: L.buttonTop, width: L.cardW }, buttonPress]}
          accessible
          accessibilityRole="button"
          accessibilityLabel={`Hold to swap ${pay.amount} ${pay.symbol} for ${get.amount} ${get.symbol}`}
          accessibilityHint="Hold for a second and a half. With VoiceOver, double tap."
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={(e) => {
            if (e.nativeEvent.actionName === 'activate') swapNow();
          }}
        >
          <Animated.View style={[StyleSheet.absoluteFill, styles.doneBg, doneBg]} />
          <Animated.View style={[styles.labelWrap, holdLabel]} pointerEvents="none">
            <Text style={styles.buttonText} allowFontScaling={false}>Hold to swap</Text>
          </Animated.View>
          <Animated.View style={[styles.fill, fillTint, fillStyle, holdLabel]}>
            {/* the same label in dark ink, revealed by the fill */}
            <View style={[styles.labelWrap, { width: L.cardW }]}>
              <Text style={[styles.buttonText, styles.doneText]} allowFontScaling={false}>Hold to swap</Text>
            </View>
          </Animated.View>
          <Animated.View style={[styles.labelWrap, doneHold]} pointerEvents="none">
            <Text style={[styles.buttonText, styles.doneText]} allowFontScaling={false}>Hold to swap</Text>
          </Animated.View>
          <Animated.View style={[styles.labelWrap, doneLabel]} pointerEvents="none">
            <Svg width={18} height={18} viewBox="0 0 18 18">
              <SvgPath d="M4 9.5l3.2 3.2L14 5.8" stroke={COLOR.silverInk} strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
            </Svg>
            <Text style={[styles.buttonText, styles.doneText]} allowFontScaling={false}>Swapped</Text>
          </Animated.View>
        </Animated.View>
      </GestureDetector>

      {/* the loop's finger */}
      <Animated.View pointerEvents="none" style={[styles.finger, fingerStyle]} />
    </View>
  );
}

function TokenRow({ token }: { token: Token }) {
  return (
    <View style={styles.rowInner}>
      <View style={styles.rowLeft}>
        <Text style={styles.amount} allowFontScaling={false}>{token.amount}</Text>
        <Text style={styles.fiat}>{token.fiat}</Text>
      </View>
      <View style={styles.rowRight}>
        <View style={styles.tokenPill}>
          <View style={[styles.tokenDot, { backgroundColor: token.colour }]} />
          <Text style={styles.tokenSymbol}>{token.symbol}</Text>
        </View>
        <Text style={styles.balance}>{token.balance}</Text>
      </View>
    </View>
  );
}

function QuoteRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.quote}>
      <Text style={styles.quoteLabel}>{label}</Text>
      <Text style={styles.quoteValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.page },
  bar: { position: 'absolute', height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  title: { fontFamily: FONT.semibold, fontSize: 28, color: COLOR.ink },
  pill: { height: 32, paddingHorizontal: SPACE.m, borderRadius: 16, backgroundColor: COLOR.card, borderWidth: 1, borderColor: COLOR.cardEdge, justifyContent: 'center' },
  pillText: { fontFamily: FONT.medium, fontSize: 13, color: COLOR.mute, fontVariant: ['tabular-nums'] },
  nameWrap: { position: 'absolute', left: 0, right: 0, bottom: SPACE.m, alignItems: 'center' },
  coinName: { fontFamily: FONT.medium, fontSize: 13, color: COLOR.mute, letterSpacing: 0.4 },
  coinNameOver: { position: 'absolute' },
  card: { position: 'absolute', left: 0, right: 0, height: CARD_H, borderRadius: 24, backgroundColor: COLOR.card, borderWidth: 1, borderColor: COLOR.cardEdge, paddingHorizontal: SPACE.l, paddingTop: SPACE.m },
  cardLabel: { fontFamily: FONT.medium, fontSize: 13, color: COLOR.mute },
  row: { position: 'absolute', left: 0, right: 0, top: 0, height: CARD_H, paddingHorizontal: SPACE.l, paddingTop: 34 },
  rowInner: { flexDirection: 'row', justifyContent: 'space-between' },
  rowLeft: { flexShrink: 1 },
  rowRight: { alignItems: 'flex-end', gap: SPACE.s },
  amount: { fontFamily: FONT.semibold, fontSize: 32, lineHeight: 38, color: COLOR.ink, fontVariant: ['tabular-nums'] },
  fiat: { fontFamily: FONT.regular, fontSize: 13, color: COLOR.mute, marginTop: 2, fontVariant: ['tabular-nums'] },
  tokenPill: { height: 36, paddingLeft: 6, paddingRight: SPACE.m, borderRadius: 18, backgroundColor: '#20232B', flexDirection: 'row', alignItems: 'center', gap: SPACE.s },
  tokenDot: { width: 24, height: 24, borderRadius: 12 },
  tokenSymbol: { fontFamily: FONT.semibold, fontSize: 15, color: COLOR.ink },
  balance: { fontFamily: FONT.regular, fontSize: 13, color: COLOR.mute, fontVariant: ['tabular-nums'] },
  divider: { position: 'absolute', width: 36, height: 36, borderRadius: 18, backgroundColor: COLOR.card, borderWidth: 4, borderColor: COLOR.page, alignItems: 'center', justifyContent: 'center' },
  quote: { height: ROW_H, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  quoteLabel: { fontFamily: FONT.regular, fontSize: 14, color: COLOR.mute },
  quoteValue: { fontFamily: FONT.medium, fontSize: 14, color: COLOR.ink, fontVariant: ['tabular-nums'] },
  button: { position: 'absolute', height: BUTTON_H, borderRadius: BUTTON_H / 2, backgroundColor: '#23262E', overflow: 'hidden', justifyContent: 'center' },
  fill: { position: 'absolute', left: 0, top: 0, bottom: 0, overflow: 'hidden' },
  doneBg: { borderRadius: BUTTON_H / 2 },
  labelWrap: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: SPACE.s },
  buttonText: { fontFamily: FONT.semibold, fontSize: 17, color: COLOR.ink },
  doneText: { color: COLOR.silverInk },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(255,255,255,0.35)', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.7)' },
});
