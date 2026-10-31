import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolateColor,
  ReduceMotion,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { DRIFT, frameAt, LIFT, LOOP, STICK_R, T, TILT_MAX, YAW } from './duc-motion';
import { DUC_SKSL } from './duc-shader';

/*
 Drone Unfold Connect — adding a camera drone: it unfolds, spins up and flies under your thumb.

 The screen: "Add drone", a folded drone resting on the floor, its name and state, and Connect.

 Signature: Connect does to the drone what a pilot does by hand. Its four arms swing out from its sides
 to an X (the front pair forward, the rear pair back and up), the props spin up into faint discs, the
 status light turns from red to green, and it lifts off and hovers, bobbing. A joystick takes Connect's
 place: push it and the drone leans the way you push and drifts, a little late, like a mass; let go and
 it rights itself. Land brings it down, spins the props down and folds the arms back.

 autoPlay: LOOP = 10 s (Connect; unfold, spin up, lift off; the stick to the right and back, forward
 and back; land and fold); frame 10 s equals frame 0. A touch takes over; 2 s after the last one the
 drone lands, folds, and the loop starts again.
 Reduced motion: no loop; the drone unfolds and lifts in short steps without the bob, and tilts less.
*/

const effect = Skia.RuntimeEffect.Make(DUC_SKSL);
if (!effect) throw new Error('Drone Unfold Connect: the shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const NEVER = ReduceMotion.Never;
const C = { page: '#0D1014', ink: '#F1F3F5', mute: '#8A9099', card: '#171B21', line: '#262C34', green: '#3DDC84', red: '#FF5B4F', accent: '#E9FF70', accentInk: '#1A1F00' };
const FONT = { regular: 'DucInter-Regular', medium: 'DucInter-Medium', semibold: 'DucInter-SemiBold' };
const TILT = 0.38;

function buzz(kind: 'light' | 'done') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'light') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function DroneUnfoldConnect({ autoPlay = false }: Props) {
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
    const ppu = Math.round(dims.w * 0.36);
    const floorY = insets.top + Math.round((dims.h - insets.top - insets.bottom) * 0.52);
    const cardTop = floorY + 86;
    const bottomTop = dims.h - Math.max(insets.bottom, 16) - 8 - 120;
    return { w: dims.w, h: dims.h, top: insets.top, ppu, floorY, cardTop, bottomTop };
  }, [dims, insets.top, insets.bottom]);
  const [step, setStep] = useState(0); // 0 folded, 1 connecting, 2 flying, 3 landing

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const pin = useSharedValue(-1);
  const stay = useSharedValue(0);

  const stage = useSharedValue(0);
  const unfold = useSharedValue(0);
  const spin = useSharedValue(0);
  const lift = useSharedValue(0); // 0 … 1 of the hover height
  const pitch = useSharedValue(0);
  const roll = useSharedValue(0);
  const drift = useSharedValue(0);
  const vp = useSharedValue(0);
  const vr = useSharedValue(0);
  const vd = useSharedValue(0);
  const light = useSharedValue(0);
  const connected = useSharedValue(0);
  const stickX = useSharedValue(0);
  const stickY = useSharedValue(0);
  const pressConnect = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerAt = useSharedValue(0);
  const bob = useSharedValue(0);

  /* ------------------------------------------------------------ steps (above the clock) */
  const onLight = useCallback(() => buzz('light'), []);
  const onDone = useCallback(() => buzz('done'), []);

  function connect() {
    'worklet';
    if (stage.get() !== 0) return;
    stage.set(1);
    const k = reduced ? 0.25 : 1;
    unfold.set(withTiming(1, { duration: 1200 * k, easing: EASE_IN_OUT, reduceMotion: NEVER }, (a) => {
      if (!a) return;
      light.set(withTiming(1, { duration: 200, reduceMotion: NEVER }));
      scheduleOnRN(onLight);
      spin.set(withTiming(1, { duration: 700 * k, easing: EASE_OUT, reduceMotion: NEVER }, (b) => {
        if (!b) return;
        lift.set(withTiming(1, { duration: 900 * k, easing: EASE_OUT, reduceMotion: NEVER }, (c) => {
          if (!c) return;
          stage.set(2);
          connected.set(withTiming(1, { duration: 300, reduceMotion: NEVER }));
          scheduleOnRN(onDone);
        }));
      }));
    }));
  }

  function land() {
    'worklet';
    if (stage.get() !== 2) return;
    stage.set(3);
    const k = reduced ? 0.25 : 1;
    connected.set(withTiming(0, { duration: 250, reduceMotion: NEVER }));
    stickX.set(withSpring(0, { duration: 300, dampingRatio: 1 }));
    stickY.set(withSpring(0, { duration: 300, dampingRatio: 1 }));
    lift.set(withTiming(0, { duration: 1000 * k, easing: EASE_IN_OUT, reduceMotion: NEVER }, (a) => {
      if (!a) return;
      spin.set(withTiming(0, { duration: 600 * k, easing: EASE_OUT, reduceMotion: NEVER }, (b) => {
        if (!b) return;
        unfold.set(withTiming(0, { duration: 1000 * k, easing: EASE_IN_OUT, reduceMotion: NEVER }, (c) => {
          if (!c) return;
          light.set(withTiming(0, { duration: 200, reduceMotion: NEVER }));
          stage.set(0);
        }));
      }));
    }));
  }

  /* ------------------------------------------------------------ clock */
  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      unfold.set(s.unfold);
      spin.set(s.spin);
      lift.set(s.lift / LIFT);
      bob.set(0);
      pitch.set(s.pitch);
      roll.set(s.roll);
      drift.set(s.drift);
      light.set(s.light);
      connected.set(s.connected);
      stickX.set(s.stickX);
      stickY.set(s.stickY);
      pressConnect.set(s.pressConnect);
      finger.set(s.finger);
      fingerAt.set(s.fingerAt);
      stage.set(s.connected > 0.5 ? 2 : t > T.land[0] ? 3 : s.unfold > 0.001 ? 1 : 0);
      return;
    }
    // the drone follows the stick like a mass on a spring
    const k = reduced ? 0.5 : 1;
    const follow = (v: typeof pitch, vel: typeof vp, target: number) => {
      'worklet';
      const a = (target - v.get()) * 60 - vel.get() * 11;
      vel.set(vel.get() + a * dt);
      v.set(v.get() + vel.get() * dt);
    };
    follow(pitch, vp, -stickY.get() * TILT_MAX * k);
    follow(roll, vr, -stickX.get() * TILT_MAX * k);
    follow(drift, vd, stickX.get() * DRIFT);
    bob.set(reduced ? 0 : 0.05 * Math.sin(clock.get() * 1.9) * lift.get());
    // moving between steps, or under the thumb, counts as activity; hovering does not
    if (stage.get() === 1 || stage.get() === 3 || Math.abs(stickX.get()) + Math.abs(stickY.get()) > 0.01) lastTouch.set(clock.get());
    if (!auto || stay.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    // the loop starts folded on the floor: land first
    if (stage.get() === 2) land();
    else if (stage.get() === 0 && unfold.get() < 0.001) {
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
    (globalThis as Record<string, unknown>).__duc = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      stay: (v: boolean) => {
        stay.set(v ? 1 : 0);
        if (v) mode.set(1);
      },
      state: () => JSON.stringify({ mode: mode.get(), stage: stage.get(), unfold: +unfold.get().toFixed(2), lift: +lift.get().toFixed(2), roll: +roll.get().toFixed(2) }),
    };
  }, [pin, stay, mode, stage, unfold, lift, roll]);

  useAnimatedReaction(
    () => stage.get(),
    (now, before) => {
      if (now !== before) scheduleOnRN(setStep, now);
    },
  );

  function takeOver() {
    'worklet';
    lastTouch.set(clock.get());
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    pressConnect.set(withTiming(0, { duration: 150 }));
    vp.set(0);
    vr.set(0);
    vd.set(0);
    stickX.set(withSpring(0, { duration: 300, dampingRatio: 1 }));
    stickY.set(withSpring(0, { duration: 300, dampingRatio: 1 }));
    const s = stage.get();
    if (s === 1 && connected.get() < 0.5 && unfold.get() > 0.001) {
      // finish the connection from where it is
      stage.set(1);
      unfold.set(withTiming(1, { duration: 400, reduceMotion: NEVER }));
      spin.set(withTiming(1, { duration: 400, reduceMotion: NEVER }));
      light.set(withTiming(1, { duration: 200, reduceMotion: NEVER }));
      lift.set(withTiming(1, { duration: 500, reduceMotion: NEVER }, (fin) => {
        if (fin) {
          stage.set(2);
          connected.set(withTiming(1, { duration: 300, reduceMotion: NEVER }));
        }
      }));
    }
  }

  const screenTouch = Gesture.Manual().onTouchesDown(() => {
    takeOver();
  });

  const stick = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      takeOver();
    })
    .onUpdate((e) => {
      if (stage.get() !== 2) return;
      lastTouch.set(clock.get());
      let x = e.translationX / STICK_R;
      let y = e.translationY / STICK_R;
      const m = Math.hypot(x, y);
      if (m > 1) {
        x /= m;
        y /= m;
      }
      stickX.set(x);
      stickY.set(y);
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      stickX.set(withSpring(0, { duration: 350, dampingRatio: 0.9 }));
      stickY.set(withSpring(0, { duration: 350, dampingRatio: 0.9 }));
    });

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => ({
    uView: [L.w, L.h, L.ppu, pixelRatio],
    uDrone: [drift.get(), lift.get() * LIFT + bob.get(), pitch.get(), roll.get()],
    uState: [unfold.get(), spin.get(), YAW + 0.25 * roll.get(), clock.get() * 0.7],
    uFloor: [L.floorY, TILT, light.get(), 0],
  }));

  const knobStyle = useAnimatedStyle(() => ({ transform: [{ translateX: stickX.get() * STICK_R }, { translateY: stickY.get() * STICK_R }] }));
  const stickStyle = useAnimatedStyle(() => ({ opacity: connected.get(), transform: [{ scale: 0.9 + 0.1 * connected.get() }] }));
  const connectStyle = useAnimatedStyle(() => ({ opacity: (1 - connected.get()) * (1 - 0.55 * Math.min(1, unfold.get() * 3)), transform: [{ scale: 1 - 0.04 * pressConnect.get() }] }));
  const dotStyle = useAnimatedStyle(() => ({ backgroundColor: interpolateColor(light.get(), [0, 1], [C.red, C.green]) }));
  const fingerStyle = useAnimatedStyle(() => {
    const onStick = fingerAt.get() === 1;
    const x = L.w / 2 + (onStick ? stickX.get() * STICK_R : 0);
    const y = L.bottomTop + 60 + (onStick ? stickY.get() * STICK_R : 0);
    return { opacity: 0.85 * finger.get(), transform: [{ translateX: x - 22 }, { translateY: y - 22 }, { scale: 1 - 0.12 * pressConnect.get() }] };
  });

  const onRoot = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setDims((d) => (d.w === width && d.h === height ? d : { w: width, h: height }));
  }, []);

  if (!fontsLoaded) return <View style={styles.root} onLayout={onRoot} />;

  const status = step === 0 ? 'Folded · 92%' : step === 1 ? 'Connecting…' : step === 2 ? 'Connected · flying' : 'Landing…';
  return (
    <GestureDetector gesture={screenTouch}>
      <View style={styles.root} onLayout={onRoot}>
        <StatusBar style="light" />
        <View style={[styles.head, { top: L.top + 8 }]}>
          <Svg width={12} height={20} viewBox="0 0 12 20" accessibilityLabel="Back" accessibilityRole="button">
            <SvgPath d="M10 2L2 10l8 8" stroke={C.ink} strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round" fill="none" />
          </Svg>
          <Text style={styles.title} accessibilityRole="header">Add drone</Text>
          {step === 2 ? (
            <Pressable onPress={() => land()} hitSlop={12} accessibilityRole="button" accessibilityLabel="Land">
              <Text style={styles.land}>Land</Text>
            </Pressable>
          ) : (
            <View style={styles.landSpace} />
          )}
        </View>

        {active ? (
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Fill>
              <Shader source={effect!} uniforms={uniforms} />
            </Fill>
          </Canvas>
        ) : null}

        <View style={[styles.card, { top: L.cardTop }]} accessible accessibilityLabel={`Skyline Mini, ${status}`} accessibilityLiveRegion="polite">
          <Text style={styles.name}>Skyline Mini</Text>
          <View style={styles.stateRow}>
            <Animated.View style={[styles.dot, dotStyle]} />
            <Text style={styles.state}>{status}</Text>
          </View>
        </View>

        <View style={[styles.bottom, { top: L.bottomTop }]}>
          <Animated.View pointerEvents={step === 0 ? 'auto' : 'none'} style={[styles.connectWrap, connectStyle]}>
            <Pressable onPress={() => connect()} style={styles.connect} accessibilityRole="button" accessibilityLabel="Connect" accessibilityState={{ disabled: step !== 0 }}>
              <Text style={styles.connectText}>Connect</Text>
            </Pressable>
          </Animated.View>
          <GestureDetector gesture={stick}>
            <Animated.View pointerEvents={step === 2 ? 'auto' : 'none'} style={[styles.pad, stickStyle]} accessible accessibilityRole="adjustable" accessibilityLabel="Flight stick">
              <Animated.View style={[styles.knob, knobStyle]} />
            </Animated.View>
          </GestureDetector>
        </View>

        <Animated.View pointerEvents="none" style={[styles.finger, fingerStyle]} />
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page },
  head: { position: 'absolute', left: 20, right: 20, height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  title: { fontFamily: FONT.semibold, fontSize: 17, color: C.ink },
  land: { fontFamily: FONT.semibold, fontSize: 16, color: C.accent },
  landSpace: { width: 36 },
  card: { position: 'absolute', left: 0, right: 0, alignItems: 'center', gap: 6 },
  name: { fontFamily: FONT.semibold, fontSize: 26, lineHeight: 32, color: C.ink },
  stateRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  dot: { width: 8, height: 8, borderRadius: 4 },
  state: { fontFamily: FONT.medium, fontSize: 15, color: C.mute },
  bottom: { position: 'absolute', left: 20, right: 20, height: 120, alignItems: 'center', justifyContent: 'center' },
  connectWrap: { position: 'absolute', left: 0, right: 0, top: 32 },
  connect: { height: 56, borderRadius: 28, backgroundColor: C.accent, alignItems: 'center', justifyContent: 'center' },
  connectText: { fontFamily: FONT.semibold, fontSize: 17, color: C.accentInk },
  pad: { width: 120, height: 120, borderRadius: 60, backgroundColor: C.card, borderWidth: 1, borderColor: C.line, alignItems: 'center', justifyContent: 'center' },
  knob: { width: 52, height: 52, borderRadius: 26, backgroundColor: '#2C333D', borderWidth: 1, borderColor: '#3A434F' },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(255,255,255,0.35)', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.8)' },
});
