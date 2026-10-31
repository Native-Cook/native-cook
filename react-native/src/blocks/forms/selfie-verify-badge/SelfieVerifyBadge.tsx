import { Canvas, DashPathEffect, Fill, Group, Path, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolateColor,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { COLOR, detectedAt, flashAt, FONT, linkAt, LOOP, makeLayout, scanAt, shutterAt, verified } from './svb-motion';
import { BUST_SKSL, CUSHION_SKSL } from './svb-shader';

/*
 Selfie Verify Badge — the last step of an invented wallet's identity check.

 Look: the selfie screen is a Dribbble shot (a dark "Selfie Verification" page, dashed corner
 brackets round a head that turn lime on "Face detected", a round shutter that turns lime);
 the reward is a Pinterest pin's motion (a scalloped badge flips, a soft white grainy cushion of the
 same shape swells over the screen, a dark verified badge flips in on white).

 Ours: one continuous story. The shutter fires, the brackets close into the frame's outline, the
 outline becomes the scalloped cushion, inflates and grows until the dark screen is white, and the
 badge flips in with its check drawing itself; Continue runs it back. The head is a face mesh, the
 way a face scanner draws one: a head ray-marched in a shader and shown only as points and lines on
 its surface, white, then lime once the face is found; drag it to turn it. When the shutter fires the
 mesh spins and gathers into the frame's centre, where the badge will land, and the cushion
 inflates slowly (about a second).

 autoPlay: LOOP = 9.6 s (rest, scan, face detected, shutter, verified, back); frame 9.6 s equals 0.
 A touch takes over; 2 s after the last one it eases back and the loop starts again.
 Reduced motion: no loop, no flight; the shutter cross-fades to the verified screen.
*/

const bustFx = Skia.RuntimeEffect.Make(BUST_SKSL);
const cushionFx = Skia.RuntimeEffect.Make(CUSHION_SKSL);
if (!bustFx || !cushionFx) throw new Error('Selfie Verify Badge: a shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);

function buzz(kind: 'light' | 'medium' | 'success') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'success') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(kind === 'medium' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function SelfieVerifyBadge({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
    [FONT.bold]: require('./assets/fonts/Inter-Bold.ttf'),
  });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);

  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const link = useSharedValue(0);
  const detected = useSharedValue(0);
  const scan = useSharedValue(-9);
  const flash = useSharedValue(0);
  const shutter = useSharedValue(0);
  const yaw = useSharedValue(0); // the bust, under the finger
  const spin = useSharedValue(0); // the badge, after a tap
  const fade = useSharedValue(0); // reduced motion cross-fade
  const [done, setDone] = useState(false);

  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      link.set(linkAt(t));
      detected.set(detectedAt(t));
      scan.set(scanAt(t));
      flash.set(flashAt(t));
      shutter.set(shutterAt(t));
      yaw.set(Math.sin((t / LOOP) * Math.PI * 2) * 0.32);
    } else if (auto && resumeAsked.get() === 0 && clock.get() - lastTouch.get() > 2) {
      resumeAsked.set(1);
      link.set(withTiming(0, { duration: link.get() > 0.01 ? 1300 : 0, easing: EASE_IN_OUT }));
      detected.set(withTiming(0, { duration: 400 }));
      yaw.set(withTiming(0, { duration: 700, easing: EASE_IN_OUT }));
    } else if (resumeAsked.get() === 1 && link.get() < 0.001 && clock.get() - lastTouch.get() > 3.4) {
      resumeAsked.set(0);
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
    (globalThis as Record<string, unknown>).__selfie = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      state: () => JSON.stringify({ mode: mode.get(), link: +link.get().toFixed(3), detected: +detected.get().toFixed(2) }),
    };
  }, [pin, mode, link, detected]);

  useAnimatedReaction(
    () => link.get() > 0.5,
    (now, before) => {
      if (before !== null && now !== before) scheduleOnRN(setDone, now);
    },
  );
  useAnimatedReaction(
    () => link.get() > 0.86,
    (now, before) => {
      if (now && before === false && mode.get() === 1) scheduleOnRN(buzz, 'success');
    },
  );

  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    mode.set(1);
  };
  const takeOverJS = () => {
    mode.set(1);
    lastTouch.set(clock.get());
    resumeAsked.set(0);
  };

  const capture = useCallback(() => {
    takeOverJS();
    if (link.get() > 0.02) return;
    buzz('medium');
    if (reduced) {
      detected.set(1);
      fade.set(withTiming(1, { duration: 280 }));
      link.set(1);
      return;
    }
    const wait = detected.get() > 0.95 ? 0 : 700;
    if (wait) {
      scan.set(-0.8);
      scan.set(withSequence(withTiming(0.95, { duration: 520, easing: EASE_IN_OUT }), withTiming(-9, { duration: 0 })));
      detected.set(withTiming(1, { duration: 220 }));
    }
    setTimeout(() => {
      flash.set(withSequence(withTiming(1, { duration: 60 }), withTiming(0, { duration: 260 })));
      link.set(withTiming(1, { duration: 3200, easing: Easing.linear }));
    }, wait);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reduced]);

  const back = useCallback(() => {
    takeOverJS();
    if (reduced) {
      fade.set(withTiming(0, { duration: 280 }));
      link.set(0);
      detected.set(0);
      return;
    }
    link.set(withTiming(0, { duration: 1300, easing: EASE_IN_OUT }));
    detected.set(withTiming(0, { duration: 400 }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reduced]);

  /* ------------------------------------------------------------ gestures */
  const turn = Gesture.Pan()
    .onBegin(() => takeOver())
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      yaw.set(Math.max(-1.1, Math.min(1.1, e.translationX / 120)));
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      yaw.set(withSpring(0, { velocity: e.velocityX / 120, duration: 700, dampingRatio: 0.45 }));
    });
  const flipBadge = Gesture.Tap().onEnd(() => {
    takeOver();
    if (link.get() < 0.9) return;
    spin.set(withSequence(withTiming(0, { duration: 0 }), withTiming(Math.PI * 2, { duration: 900, easing: Easing.bezier(0.23, 1, 0.32, 1) })));
    scheduleOnRN(buzz, 'light');
  });

  /* ------------------------------------------------------------ drawing */
  const v = useDerivedValue(() => verified(L, link.get()));
  const bustU = useDerivedValue(() => {
    const s = v.get();
    return {
      // the head sits a little low in the frame; it gathers to the frame's centre, where the badge lands
      uCentre: [L.frameX, L.frameY + L.frame * 0.04 * (1 - s.collapse)],
      uUnit: L.frame * 1.55,
      // the head turns a little by itself, so the mesh reads as a face in 3D
      uYaw: yaw.get() + (reduced ? 0 : 0.38 * Math.sin((2 * Math.PI * clock.get()) / LOOP)),
      uAlpha: s.bust,
      uScan: scan.get(),
      uPixelRatio: pixelRatio,
      uDetect: detected.get(),
      uCollapse: s.collapse,
    };
  });
  const cushionU = useDerivedValue(() => {
    const s = v.get();
    return {
      uCentre: [L.frameX, L.frameY],
      uSize: s.size,
      uShape: s.shape,
      uPuff: s.puff,
      uBadge: s.badge,
      uYaw: s.yaw + spin.get(),
      uCheck: s.check,
      uPixelRatio: pixelRatio,
    };
  });
  const cushionOpacity = useDerivedValue(() => Math.min(1, link.get() / 0.04));
  const brackets = useMemo(() => {
    const f = L.frame;
    const a = 30;
    const r = 10;
    const p = Skia.PathBuilder.Make();
    const corners: [number, number, number, number][] = [[-1, -1, 1, 1], [1, -1, -1, 1], [-1, 1, 1, -1], [1, 1, -1, -1]];
    for (const [sx, sy, dx, dy] of corners) {
      const x = L.frameX + sx * f;
      const y = L.frameY + sy * f;
      p.moveTo(x, y + dy * a);
      p.lineTo(x, y + dy * r);
      p.quadTo(x, y, x + dx * r, y);
      p.lineTo(x + dx * a, y);
    }
    return p.build();
  }, [L]);
  const bracketColor = useDerivedValue(() => interpolateColor(detected.get(), [0, 1], [COLOR.bracket, COLOR.lime]));
  const bracketOpacity = useDerivedValue(() => v.get().brackets);
  // the brackets close in toward the outline as the shutter fires
  const bracketTransform = useDerivedValue(() => {
    const s = 1 - 0.18 * (1 - v.get().brackets);
    return [{ translateX: L.frameX }, { translateY: L.frameY }, { scale: s }, { translateX: -L.frameX }, { translateY: -L.frameY }];
  });

  const darkCopy = useAnimatedStyle(() => ({ opacity: 1 - v.get().light }));
  const lightCopy = useAnimatedStyle(() => ({ opacity: v.get().light }));
  const hint = useAnimatedStyle(() => ({ opacity: (1 - v.get().light) * (1 - v.get().puff) }));
  const detectedHint = useAnimatedStyle(() => ({ opacity: detected.get() }));
  const centerHint = useAnimatedStyle(() => ({ opacity: 1 - detected.get() }));
  const shutterStyle = useAnimatedStyle(() => ({
    opacity: 1 - v.get().light,
    transform: [{ scale: (1 - 0.06 * shutter.get()) * (1 - 0.2 * v.get().light) }],
    backgroundColor: interpolateColor(detected.get(), [0, 1], [COLOR.button, COLOR.lime]),
  }));
  const continueStyle = useAnimatedStyle(() => ({
    opacity: v.get().light,
    transform: [{ translateY: (1 - v.get().light) * 16 }],
  }));
  const flashStyle = useAnimatedStyle(() => ({ opacity: flash.get() * 0.7 }));

  const ready = fontsLoaded && size !== null;
  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style={done ? 'dark' : 'light'} />
      {ready && (
        <>
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Fill>
              <Shader source={bustFx!} uniforms={bustU} />
            </Fill>
            <Group opacity={bracketOpacity} transform={bracketTransform}>
              <Path path={brackets} style="stroke" strokeWidth={2.5} strokeCap="round" color={bracketColor}>
                <DashPathEffect intervals={[9, 6]} />
              </Path>
            </Group>
            <Group opacity={cushionOpacity}>
              <Fill>
                <Shader source={cushionFx!} uniforms={cushionU} />
              </Fill>
            </Group>
          </Canvas>

          <GestureDetector gesture={Gesture.Race(turn, flipBadge)}>
            <View
              style={{ position: 'absolute', left: L.frameX - L.frame, top: L.frameY - L.frame, width: L.frame * 2, height: L.frame * 2 }}
              accessible
              accessibilityLabel={done ? 'Verified badge' : 'Camera preview'}
              accessibilityHint={done ? 'Tap to spin it.' : 'Drag to turn the head.'}
            />
          </GestureDetector>

          <Animated.View style={[styles.copy, { top: L.titleTop }, darkCopy]} pointerEvents="none">
            <Text style={styles.title} accessibilityRole="header">Selfie verification</Text>
            <Text style={styles.body}>Take a clear selfie to confirm your identity and keep your account safe.</Text>
          </Animated.View>
          <Animated.View style={[styles.copy, { top: L.titleTop }, lightCopy]} pointerEvents="none">
            <Text style={[styles.title, { color: COLOR.ink }]}>You’re verified</Text>
            <Text style={[styles.body, { color: COLOR.inkBody }]}>Your identity is confirmed. Sending and receiving have no limits now.</Text>
          </Animated.View>

          <Animated.View style={[styles.hintRow, { top: L.hintTop }, hint]} pointerEvents="none">
            <Animated.Text style={[styles.hint, centerHint]}>Center your face in the frame</Animated.Text>
            <Animated.Text style={[styles.hint, styles.detected, detectedHint]} accessibilityLiveRegion="polite">Face detected</Animated.Text>
          </Animated.View>

          <Animated.View style={[styles.shutter, { left: L.w / 2 - L.shutter / 2, top: L.shutterY - L.shutter / 2, width: L.shutter, height: L.shutter }, shutterStyle]}>
            <Pressable
              onPress={capture}
              disabled={done}
              style={styles.fill}
              accessibilityRole="button"
              accessibilityLabel="Take selfie"
            >
              <Svg width={26} height={26} viewBox="0 0 24 24">
                <SvgPath d="M4 8.5A2.5 2.5 0 0 1 6.5 6h1.2l1.3-2h6l1.3 2h1.2A2.5 2.5 0 0 1 20 8.5v8a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 16.5z" fill="none" stroke={COLOR.title} strokeWidth={1.7} />
                <Circle cx={12} cy={12.5} r={3.4} fill="none" stroke={COLOR.title} strokeWidth={1.7} />
              </Svg>
            </Pressable>
          </Animated.View>

          <Animated.View style={[styles.continue, { top: L.shutterY - 28 }, continueStyle]} pointerEvents={done ? 'auto' : 'none'}>
            <Pressable onPress={back} style={styles.fill} accessibilityRole="button">
              <Text style={styles.continueText}>Continue</Text>
            </Pressable>
          </Animated.View>

          <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: '#FFFFFF' }, flashStyle]} pointerEvents="none" />
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.dark, overflow: 'hidden' },
  fill: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  copy: { position: 'absolute', left: 40, right: 40, alignItems: 'center', gap: 10 },
  title: { fontFamily: FONT.semibold, fontSize: 20, letterSpacing: -0.4, color: COLOR.title, textAlign: 'center' },
  body: { fontFamily: FONT.regular, fontSize: 15, lineHeight: 21, color: COLOR.body, textAlign: 'center' },
  hintRow: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  hint: { fontFamily: FONT.semibold, fontSize: 14, color: COLOR.body },
  detected: { position: 'absolute', color: COLOR.lime },
  shutter: { position: 'absolute', borderRadius: 999 },
  continue: { position: 'absolute', left: 32, right: 32, height: 56, borderRadius: 28, backgroundColor: COLOR.ink },
  continueText: { fontFamily: FONT.semibold, fontSize: 16, color: '#FFFFFF' },
});
