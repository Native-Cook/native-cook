import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
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
import { backOut, CLAIM_AT, frameAt, LOOP, PULL_PT, smooth, tearOf } from './sct-motion';
import { SCT_SKSL } from './sct-shader';

/*
 Star Coupon Tear — a shopping app's flash coupon: pull the glowing star down and it becomes the coupon.

 The screen: "Flash coupon · today only", the title, a large puffy gold star glowing with sparkles,
 "Pull the star down to claim", a Claim coupon button, and a Coupons chip in the corner.

 Signature: the star is the coupon. Pull it down and it stretches and its points draw into the corners
 of a ticket — a coral coupon with a torn notch on each side, a perforation and a raised percent sign —
 the finger scrubbing the change and holding it half-way; let go past 60 % and it finishes, settles, and
 flies into the Coupons chip, which pulses "+1"; let go sooner and it springs back into a star. A new
 star pops in for the next one.

 autoPlay: LOOP = 9 s (a finger pulls the star half-way, holds, pulls on, lets go; the ticket flies to
 the chip; a new star pops in); frame 9 s equals frame 0. A touch takes over; 2 s after everything
 rests the loop starts again.
 Reduced motion: no loop; the claim is a short cross-melt without the stretch, the flight is a fade.
*/

const effect = Skia.RuntimeEffect.Make(SCT_SKSL);
if (!effect) throw new Error('Star Coupon Tear: the shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const NEVER = ReduceMotion.Never;
const C = { page: '#15123A', ink: '#FFFFFF', mute: '#A49FD0', pill: '#262150', line: '#3A3470', gold: '#FFD23F', goldInk: '#2B1E00' };
const FONT = { regular: 'SctInter-Regular', medium: 'SctInter-Medium', semibold: 'SctInter-SemiBold' };

function buzz(kind: 'grab' | 'done') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'grab') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function StarCouponTear({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
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
  const [dims, setDims] = useState({ w: 402, h: 874 });
  const L = useMemo(() => {
    const S = Math.round(dims.w * 0.3);
    const cy = insets.top + Math.round((dims.h - insets.top - insets.bottom) * 0.43);
    const chip = { x: dims.w - 20 - 56, y: insets.top + 10 + 20 };
    const buttonTop = dims.h - Math.max(insets.bottom, 16) - 8 - 56;
    return { w: dims.w, h: dims.h, top: insets.top, S, cy, chip, buttonTop, hintTop: cy + S * 1.5 };
  }, [dims, insets.top, insets.bottom]);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const pin = useSharedValue(-1);
  const stay = useSharedValue(0);
  const pull = useSharedValue(0);
  const tear = useSharedValue(0);
  const fly = useSharedValue(0);
  const pop = useSharedValue(1);
  const chip = useSharedValue(1);
  const finger = useSharedValue(0);
  const phase = useSharedValue(0); // the twinkle and sway: one turn per loop, never jumping
  const busy = useSharedValue(0);
  const dragging = useSharedValue(0);

  /* ------------------------------------------------------------ the claim (above the clock) */
  const onDone = useCallback(() => buzz('done'), []);
  const onGrab = useCallback(() => buzz('grab'), []);

  function newStar() {
    'worklet';
    tear.set(0);
    fly.set(0);
    pull.set(0);
    pop.set(0);
    pop.set(withDelay(500, withTiming(1, { duration: reduced ? 200 : 600, easing: Easing.linear, reduceMotion: NEVER }, (fin) => {
      if (fin) busy.set(0);
    })));
  }

  function claim() {
    'worklet';
    if (busy.get() === 1 && fly.get() > 0) return;
    busy.set(1);
    pull.set(withSpring(0, { duration: 400, dampingRatio: 0.6 }));
    tear.set(withTiming(1, { duration: reduced ? 250 : 400, easing: EASE_OUT, reduceMotion: NEVER }, (a) => {
      if (!a) return;
      fly.set(withDelay(reduced ? 0 : 450, withTiming(1, { duration: reduced ? 300 : 700, easing: EASE_IN_OUT, reduceMotion: NEVER }, (b) => {
        if (!b) return;
        chip.set(0);
        chip.set(withTiming(1, { duration: 800, easing: Easing.linear, reduceMotion: NEVER }));
        scheduleOnRN(onDone);
        newStar();
      })));
    }));
  }

  /* ------------------------------------------------------------ clock */
  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      phase.set((2 * Math.PI * t) / LOOP);
      pull.set(s.pull);
      tear.set(s.tear);
      fly.set(s.fly);
      pop.set(s.pop);
      chip.set(s.chip);
      finger.set(s.finger);
      return;
    }
    if (!reduced) phase.set(phase.get() + (2 * Math.PI * dt) / LOOP);
    if (busy.get() === 1 || dragging.get() === 1 || chip.get() < 1) lastTouch.set(clock.get());
    if (!auto || stay.get() === 1 || clock.get() - lastTouch.get() < 2 || tear.get() > 0.001) return;
    autoStart.set(clock.get());
    phase.set(0);
    mode.set(0);
  }, active);

  useEffect(() => {
    mode.set(auto ? 0 : 1);
    autoStart.set(clock.get());
  }, [auto, mode, autoStart, clock]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__sct = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      stay: (v: boolean) => {
        stay.set(v ? 1 : 0);
        if (v) mode.set(1);
      },
      state: () => JSON.stringify({ mode: mode.get(), pull: +pull.get().toFixed(2), tear: +tear.get().toFixed(2), fly: +fly.get().toFixed(2), busy: busy.get() }),
    };
  }, [pin, stay, mode, pull, tear, fly, busy]);

  function takeOver() {
    'worklet';
    lastTouch.set(clock.get());
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    // a claim under way carries on; a pull in the loop springs back
    if (fly.get() > 0.001 || tear.get() > 0.95) {
      busy.set(0);
      claim();
    } else if (pop.get() < 1) {
      pop.set(withTiming(1, { duration: 300 }));
    } else {
      pull.set(withSpring(0, { duration: 400, dampingRatio: 0.6 }));
      tear.set(withSpring(0, { duration: 400, dampingRatio: 0.8 }));
    }
  }

  const screenTouch = Gesture.Manual().onTouchesDown(() => {
    takeOver();
  });
  const pan = Gesture.Pan()
    .activeOffsetY(6)
    .onBegin(() => {
      takeOver();
    })
    .onStart(() => {
      if (busy.get() === 1) return;
      dragging.set(1);
      scheduleOnRN(onGrab);
    })
    .onUpdate((e) => {
      if (dragging.get() !== 1) return;
      lastTouch.set(clock.get());
      let v = e.translationY / PULL_PT;
      if (v < 0) v = 0;
      if (v > 1) v = 1 + 0.15 * (1 - Math.exp(-(v - 1) / 0.15));
      pull.set(reduced ? 0 : v);
      tear.set(tearOf(Math.min(1, v)));
    })
    .onFinalize((e) => {
      if (dragging.get() !== 1) return;
      dragging.set(0);
      lastTouch.set(clock.get());
      const p = e.translationY / PULL_PT + (e.velocityY / PULL_PT) * 0.15;
      if (p > CLAIM_AT) claim();
      else {
        pull.set(withSpring(0, { duration: 450, dampingRatio: 0.55 }));
        tear.set(withSpring(0, { duration: 450, dampingRatio: 0.8 }));
      }
    });

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => {
    const k = pull.get();
    const f = fly.get();
    const arc = Math.sin(Math.PI * f);
    const baseY = L.cy + k * 36;
    const x = L.w / 2 + (L.chip.x - 30 - L.w / 2) * f + 30 * arc;
    const y = baseY + (L.chip.y - baseY) * f - 40 * arc;
    const p = backOut(pop.get());
    const size = L.S * (1 - 0.85 * f) * p;
    const t = phase.get();
    return {
      uObj: [x, y, size, 1 - smooth(0.85, 1, f)],
      uShape: [tear.get(), reduced ? 0.2 : 0.2 + 0.25 * Math.sin(t) * (1 - tear.get()) + 2 * Math.PI * f, 0.1, k],
      uLive: [t * 3, 1 - 0.5 * f, p * (1 - f), pixelRatio],
    };
  });

  const chipStyle = useAnimatedStyle(() => {
    const u = chip.get();
    const w = u >= 1 ? 0 : Math.exp(-u * 5) * Math.sin(u * 24);
    return { transform: [{ scale: 1 + 0.1 * w }] };
  });
  const plusStyle = useAnimatedStyle(() => ({ opacity: Math.sin(Math.PI * chip.get()), transform: [{ translateY: 8 - 18 * chip.get() }] }));
  const hintStyle = useAnimatedStyle(() => ({ opacity: 1 - tear.get() }));
  const fingerStyle = useAnimatedStyle(() => ({
    opacity: 0.85 * finger.get(),
    transform: [{ translateX: L.w / 2 - 22 }, { translateY: L.cy + pull.get() * PULL_PT * 0.85 - 22 }],
  }));

  const onRoot = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setDims((d) => (d.w === width && d.h === height ? d : { w: width, h: height }));
  }, []);

  if (!fontsLoaded) return <View style={styles.root} onLayout={onRoot} />;

  return (
    <GestureDetector gesture={screenTouch}>
      <GestureDetector gesture={pan}>
        <View style={styles.root} onLayout={onRoot}>
          <StatusBar style="light" />
          <View style={[styles.head, { top: L.top + 10 }]}>
            <View style={styles.flash}>
              <Text style={styles.flashText}>Flash coupon</Text>
            </View>
            <Text style={styles.left}>Today only · 3 left</Text>
          </View>
          <Animated.View style={[styles.chip, chipStyle, { left: L.chip.x - 56, top: L.chip.y - 20 }]} accessible accessibilityLabel="My coupons">
            <Svg width={18} height={14} viewBox="0 0 18 14">
              <SvgPath d="M2 1h14a1 1 0 011 1v3a2 2 0 000 4v3a1 1 0 01-1 1H2a1 1 0 01-1-1V9a2 2 0 000-4V2a1 1 0 011-1z" fill={C.gold} />
            </Svg>
            <Text style={styles.chipText}>Coupons</Text>
          </Animated.View>
          <Animated.Text style={[styles.plus, plusStyle, { left: L.chip.x - 20, top: L.chip.y + 18 }]} accessibilityLiveRegion="polite">+1</Animated.Text>
          <Text style={[styles.title, { top: L.top + 64 }]} accessibilityRole="header">Half price on your next order</Text>

          {active ? (
            <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
              <Fill>
                <Shader source={effect!} uniforms={uniforms} />
              </Fill>
            </Canvas>
          ) : null}

          <Animated.Text style={[styles.hint, hintStyle, { top: L.hintTop }]}>Pull the star down to claim</Animated.Text>
          <Pressable onPress={() => { takeOver(); claim(); }} style={[styles.button, { top: L.buttonTop }]} accessibilityRole="button" accessibilityLabel="Claim coupon">
            <Text style={styles.buttonText}>Claim coupon</Text>
          </Pressable>
          <Animated.View pointerEvents="none" style={[styles.finger, fingerStyle]} />
        </View>
      </GestureDetector>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page },
  head: { position: 'absolute', left: 20, flexDirection: 'row', alignItems: 'center', gap: 10 },
  flash: { height: 26, paddingHorizontal: 10, borderRadius: 13, backgroundColor: C.gold, justifyContent: 'center' },
  flashText: { fontFamily: FONT.semibold, fontSize: 13, color: C.goldInk },
  left: { fontFamily: FONT.medium, fontSize: 14, color: C.mute },
  chip: { position: 'absolute', width: 112, height: 40, borderRadius: 20, backgroundColor: C.pill, borderWidth: 1, borderColor: C.line, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  chipText: { fontFamily: FONT.semibold, fontSize: 14, color: C.ink },
  plus: { position: 'absolute', fontFamily: FONT.semibold, fontSize: 14, color: C.gold },
  title: { position: 'absolute', left: 24, right: 24, fontFamily: FONT.semibold, fontSize: 28, lineHeight: 34, color: C.ink },
  hint: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.medium, fontSize: 15, color: C.mute },
  button: { position: 'absolute', left: 20, right: 20, height: 56, borderRadius: 28, backgroundColor: C.gold, alignItems: 'center', justifyContent: 'center' },
  buttonText: { fontFamily: FONT.semibold, fontSize: 17, color: C.goldInk },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(255,255,255,0.35)', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.8)' },
});
