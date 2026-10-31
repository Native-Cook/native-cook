import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, TextInput, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolateColor,
  ReduceMotion,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { frameAt, gapAt, LOOP, poseAt, smooth, steerAt } from './pas-motion';
import { PAS_SKSL } from './pas-shader';
import { C, CAMERA, FONT, makeLayout, SPEED } from './pas-theme';

/*
 Park Assist Scrub — reversing into a parking bay, the manoeuvre scrubbed by holding a button.

 The screen: a car park seen from above and behind (asphalt, painted bays, two grey cars, a low wall),
 an orange clay car in the aisle, the gap to the wall, and a Hold to park button.

 Signature: the manoeuvre is the finger's. Hold the button and the car reverses along its path into the
 bay — the front wheels turned, the park-assist arcs painted on the ground behind it lighting up from
 green through amber to red as the bumper nears the wall, and a haptic beep that quickens with it; let
 go and it stops where it is, brake lights on, holding the manoeuvre half-way. At 30 cm it is parked:
 the hazard lights flash twice and the button offers Drive out.

 autoPlay: LOOP = 10 s (hold; let go half-way; hold again into the bay; parked and flashed; drive out);
 frame 10 s equals frame 0. A touch takes over; 2 s after the last one the car drives out and the loop
 starts again.
 Reduced motion: no loop; the car still moves only while the button is held (that is the control), the
 hazard flash is a single steady blink and the beeps stay.
*/

const effect = Skia.RuntimeEffect.Make(PAS_SKSL);
if (!effect) throw new Error('Park Assist Scrub: the shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const NEVER = ReduceMotion.Never;
const AnimatedInput = Animated.createAnimatedComponent(TextInput);

function buzz(kind: 'beep' | 'parked' | 'press') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'beep') Haptics.selectionAsync().catch(() => {});
  else if (kind === 'parked') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function gapText(g: number) {
  'worklet';
  return g >= 10 ? '10 m' : `${g.toFixed(2)} m`;
}

type Props = { autoPlay?: boolean };

export function ParkAssistScrub({ autoPlay = false }: Props) {
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
  const L = useMemo(() => makeLayout(dims.w, dims.h, insets.top, insets.bottom), [dims, insets.top, insets.bottom]);
  const [isParked, setIsParked] = useState(false);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const stay = useSharedValue(0);

  const s = useSharedValue(0); // along the path, 0 in the aisle … 1 parked
  const vel = useSharedValue(0);
  const holding = useSharedValue(0);
  const assist = useSharedValue(0);
  const brake = useSharedValue(0);
  const hazard = useSharedValue(1); // the flash's progress under the finger (1 = over)
  const hz = useSharedValue(0); // the hazard lights now, 0 … 1
  const parked = useSharedValue(0);
  const leaving = useSharedValue(0);
  const finger = useSharedValue(0);
  const lastBeep = useSharedValue(0);

  /* ------------------------------------------------------------ steps (above the clock) */
  const onBeep = useCallback(() => buzz('beep'), []);
  const onParked = useCallback(() => buzz('parked'), []);

  function becomeParked() {
    'worklet';
    s.set(1);
    vel.set(0);
    parked.set(withTiming(1, { duration: 300, reduceMotion: NEVER }));
    hazard.set(0);
    hazard.set(withTiming(1, { duration: 700, easing: Easing.linear, reduceMotion: NEVER }));
    scheduleOnRN(onParked);
  }

  function driveOut() {
    'worklet';
    leaving.set(1);
    parked.set(withTiming(0, { duration: 300, reduceMotion: NEVER }));
    assist.set(withTiming(0, { duration: 300, reduceMotion: NEVER }));
    s.set(
      withTiming(0, { duration: reduced ? 600 : 1800, easing: EASE_IN_OUT, reduceMotion: NEVER }, (done) => {
        if (done) leaving.set(0);
      }),
    );
  }

  /* ------------------------------------------------------------ clock */
  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const fr = frameAt(t);
      s.set(fr.s);
      assist.set(fr.assist);
      brake.set(fr.brake);
      hz.set(fr.hazard);
      parked.set(fr.parked);
      holding.set(fr.holding);
      finger.set(fr.finger);
      leaving.set(fr.out);
      return;
    }
    // under the finger: the car rolls while the button is held, and stops softly when it is let go
    if (leaving.get() === 0 && parked.get() < 0.5) {
      const want = holding.get() === 1 ? SPEED : 0;
      vel.set(vel.get() + (want - vel.get()) * Math.min(1, dt * (want > 0 ? 5 : 9)));
      const k = s.get() + vel.get() * dt * (1 - 0.65 * smooth(0.8, 1, s.get()));
      if (k >= 0.999) becomeParked();
      else s.set(Math.max(0, k));
      if (holding.get() === 1) assist.set(Math.min(1, assist.get() + dt * 4));
      // the beep quickens as the gap closes
      const gap = gapAt(s.get());
      if (holding.get() === 1 && vel.get() > 0.02 && gap < 1.4 && clock.get() - lastBeep.get() > 0.1 + gap * 0.45) {
        lastBeep.set(clock.get());
        scheduleOnRN(onBeep);
      }
    }
    // two blinks over 0.7 s (one steady blink with Reduce Motion)
    const u = hazard.get();
    if (u >= 1) hz.set(0);
    else if (reduced) hz.set(1);
    else hz.set(Math.max(0, Math.sin(Math.PI * Math.min(1, u * 2))) + Math.max(0, Math.sin(Math.PI * Math.max(0, u * 2 - 1))));
    const moving = Math.abs(vel.get()) > 0.01 || leaving.get() === 1;
    const wantBrake = !moving && s.get() > 0.02 ? 1 : 0;
    brake.set(brake.get() + (wantBrake - brake.get()) * Math.min(1, dt * 12));
    if (holding.get() === 1 || moving) lastTouch.set(clock.get());
    if (!auto || stay.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      if (s.get() > 0.001) driveOut();
    } else if (leaving.get() === 0 && s.get() < 0.001) {
      resumeAsked.set(0);
      s.set(0);
      vel.set(0);
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
    (globalThis as Record<string, unknown>).__pas = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      stay: (v: boolean) => {
        stay.set(v ? 1 : 0);
        if (v) mode.set(1);
      },
      state: () => JSON.stringify({ mode: mode.get(), s: +s.get().toFixed(3), gap: +gapAt(s.get()).toFixed(2), parked: +parked.get().toFixed(2), holding: holding.get(), vel: +vel.get().toFixed(3), leaving: leaving.get() }),
    };
  }, [pin, stay, mode, s, parked, holding, vel, leaving]);

  useAnimatedReaction(
    () => (parked.get() > 0.5 ? 1 : 0),
    (now, before) => {
      if (now !== before) scheduleOnRN(setIsParked, now === 1);
    },
  );

  function takeOver() {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    holding.set(0);
    vel.set(0);
    hazard.set(1);
    hz.set(0);
    if (leaving.get() > 0.5) driveOut();
    else if (s.get() > 0.999) parked.set(1);
    else parked.set(0);
  }

  const screenTouch = Gesture.Manual().onTouchesDown(() => {
    takeOver();
  });

  const hold = Gesture.LongPress()
    .minDuration(0)
    .maxDistance(600)
    .onBegin(() => {
      takeOver();
      if (parked.get() > 0.5 || leaving.get() === 1) return;
      holding.set(1);
      scheduleOnRN(buzz, 'press');
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      holding.set(0);
    });
  const out = Gesture.Tap().onEnd(() => {
    if (parked.get() > 0.5) driveOut();
  });
  const button = Gesture.Exclusive(out, hold);

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => {
    const k = s.get();
    const p = poseAt(k);
    return {
      uView: [L.w, L.stageH, CAMERA.ppm, pixelRatio],
      uCar: [p.x, p.y, p.heading, leaving.get() > 0.5 ? -steerAt(k) : steerAt(k)],
      uAssist: [gapAt(k), assist.get(), brake.get(), Math.min(1, hz.get())],
      uCam: [CAMERA.x, CAMERA.y, CAMERA.tilt, 0],
    };
  });

  const gapProps = useAnimatedProps(() => {
    const g = gapAt(s.get());
    return { text: gapText(g), defaultValue: gapText(g) } as never;
  });
  const gapStyle = useAnimatedStyle(() => {
    const g = gapAt(s.get());
    const warn = Math.max(0, Math.min(1, 1 - g / 1.2));
    return { color: interpolateColor(warn, [0, 0.35, 0.7, 1], [C.ink, C.green, C.amber, C.red]) };
  });
  const readoutStyle = useAnimatedStyle(() => ({ opacity: 0.35 + 0.65 * assist.get() }));
  const fillStyle = useAnimatedStyle(() => ({ transform: [{ scaleX: Math.max(0.0001, s.get()) }] }));
  const buttonStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.03 * holding.get() }] }));
  const holdWords = useAnimatedStyle(() => ({ opacity: 1 - parked.get() }));
  const outWords = useAnimatedStyle(() => ({ opacity: parked.get() }));
  const fingerStyle = useAnimatedStyle(() => ({
    opacity: 0.85 * finger.get(),
    transform: [{ translateX: L.w * 0.5 - 22 }, { translateY: L.buttonTop + 32 - 22 }, { scale: 1 - 0.12 * holding.get() }],
  }));

  const onRoot = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setDims((d) => (d.w === width && d.h === height ? d : { w: width, h: height }));
  }, []);

  if (!fontsLoaded) return <View style={styles.root} onLayout={onRoot} />;

  return (
    <GestureDetector gesture={screenTouch}>
      <View style={styles.root} onLayout={onRoot}>
        <StatusBar style="light" />

        <View style={[styles.head, { top: L.headTop }]}>
          <Text style={styles.title} accessibilityRole="header">Park Assist</Text>
          <Text style={styles.sub}>{isParked ? 'Parked · 30 cm to the wall' : 'Hold the button to reverse into the bay'}</Text>
        </View>

        <View style={[styles.stage, { top: L.stageTop, height: L.stageH }]}>
          {active ? (
            <Canvas style={{ width: L.w, height: L.stageH }} pointerEvents="none">
              <Fill>
                <Shader source={effect!} uniforms={uniforms} />
              </Fill>
            </Canvas>
          ) : null}
          <Animated.View style={[styles.readout, readoutStyle]} accessible accessibilityLabel="Gap to the wall" accessibilityLiveRegion="polite">
            <Text style={styles.readLabel}>REAR</Text>
            <AnimatedInput editable={false} style={[styles.readValue, gapStyle]} animatedProps={gapProps} allowFontScaling={false} />
          </Animated.View>
        </View>

        <GestureDetector gesture={button}>
          <Animated.View
            style={[styles.button, buttonStyle, { top: L.buttonTop }]}
            accessible
            accessibilityRole="button"
            accessibilityLabel={isParked ? 'Drive out' : 'Hold to park'}
            accessibilityHint={isParked ? undefined : 'Reverses the car while held'}
          >
            <Animated.View style={[styles.fill, fillStyle]} />
            <Animated.Text style={[styles.buttonText, holdWords]}>Hold to park</Animated.Text>
            <Animated.Text style={[styles.buttonText, styles.over, outWords]}>Drive out</Animated.Text>
          </Animated.View>
        </GestureDetector>

        <Animated.View pointerEvents="none" style={[styles.finger, fingerStyle]} />
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page },
  head: { position: 'absolute', left: 20, right: 20, gap: 2 },
  title: { fontFamily: FONT.semibold, fontSize: 24, lineHeight: 30, color: C.ink },
  sub: { fontFamily: FONT.regular, fontSize: 14, lineHeight: 20, color: C.mute },
  stage: { position: 'absolute', left: 0, right: 0, overflow: 'hidden' },
  readout: { position: 'absolute', left: 16, top: 12, paddingHorizontal: 14, paddingVertical: 8, borderRadius: 16, backgroundColor: 'rgba(14,15,18,0.72)', borderWidth: 1, borderColor: C.line },
  readLabel: { fontFamily: FONT.semibold, fontSize: 11, letterSpacing: 0.8, color: C.mute },
  readValue: { fontFamily: FONT.semibold, fontSize: 28, lineHeight: 34, padding: 0, minWidth: 110, fontVariant: ['tabular-nums'] },
  button: { position: 'absolute', left: 20, right: 20, height: 64, borderRadius: 32, backgroundColor: C.card, borderWidth: 1, borderColor: C.line, overflow: 'hidden', alignItems: 'center', justifyContent: 'center' },
  fill: { position: 'absolute', left: 0, top: 0, bottom: 0, width: '100%', backgroundColor: 'rgba(255,138,61,0.28)', transformOrigin: 'left' },
  buttonText: { fontFamily: FONT.semibold, fontSize: 17, color: C.ink },
  over: { position: 'absolute' },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(255,255,255,0.35)', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.8)' },
});
