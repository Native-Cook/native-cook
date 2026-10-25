import { Canvas, Fill, ImageShader, Picture, Shader, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { type Fonts, buildContent, drawDots, drawLamp, drawTower } from './lighthouseArt';
import {
  LOOP,
  PAGES,
  PAGE_BACK,
  PAGE_OUT,
  TRAIL,
  aimAt,
  facing,
  makeGeo,
  pageAt,
  phiAt,
  phiBefore,
} from './lighthouseMotion';
import { LIGHTHOUSE_SKSL } from './lighthouseShader';

/*
 Lighthouse Beam Welcome — the onboarding of "Beacon", a navigation and safety app. Night sea at
 dusk, a lighthouse on its rocks, and onboarding text hidden in the fog that shows only where the
 turning beam falls (with a 0.8 s afterglow).

 Touch: press and drag anywhere above the controls and the beam turns to point at the finger
 (azimuth solved from the finger's position, followed by a spring, ζ 0.85); a quick horizontal
 swipe (> 650 pt/s) changes page (0.6 s, ease-in-out cubic). "Get started" goes to the next page.
 2 s after the last touch the lantern settles back across the text and the page returns to the
 first (0.8 s), then the loop runs from t = 0. Reduced motion: no loop and no turning; the beam
 rests on the text and pages cross-fade.
 */

type Props = { autoPlay?: boolean };

const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const P3 = Platform.OS === 'ios' ? 1 : 0;
/** The lantern's spring under the finger. */
const AIM_K = 140;
const AIM_C = 2 * 0.85 * Math.sqrt(AIM_K);
const RETURN_MS = 800;
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);

function haptic(kind: 'tick' | 'light') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function LighthouseBeamWelcome({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const effect = useMemo(() => Skia.RuntimeEffect.Make(LIGHTHOUSE_SKSL), []);
  const g = useMemo(
    () => (size.width > 0 ? makeGeo(size.width, size.height, insets.top, insets.bottom) : null),
    [size, insets.top, insets.bottom],
  );
  const fonts = useMemo<Fonts | null>(() => {
    if (!g) return null;
    const s = Math.min(1, g.u);
    return {
      kicker: matchFont({ fontFamily: SANS, fontSize: 11, fontWeight: '700' }),
      title: matchFont({ fontFamily: SANS, fontSize: Math.round(25 * s), fontWeight: '700' }),
      body: matchFont({ fontFamily: SANS, fontSize: 15, fontWeight: '400' }),
    };
  }, [g]);
  const ratio = Math.min(3, PixelRatio.get());
  const content = useMemo(() => (g && fonts ? buildContent(g, fonts, ratio) : null), [g, fonts, ratio]);
  const tower = useMemo(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, size.width), Math.max(1, size.height)));
    if (g) drawTower(canvas, g);
    return recorder.finishRecordingAsPicture();
  }, [g, size]);

  const now = useSharedValue(-1);
  const start = useSharedValue(-1);
  const origin = useSharedValue(0);
  const resumeAt = useSharedValue(-1);
  // Hand-driven state: the lantern's azimuth (unwrapped), its spring, the trail, the page.
  const uPhi = useSharedValue(0);
  const uPhiV = useSharedValue(0);
  const uTarget = useSharedValue(0);
  const uTrail = useSharedValue<number[]>([0, 0, 0, 0, 0]);
  const uPage = useSharedValue(0);
  const pressed = useSharedValue(0);

  useFrameCallback((frame) => {
    const n = frame.timestamp / 1000;
    const prev = now.get();
    if (prev < 0) {
      origin.set(n);
      if (auto) start.set(n);
    }
    now.set(n);
    const dt = prev < 0 ? 0 : Math.min(n - prev, 1 / 30);
    const s = start.get();
    if (s < 0 || n < s) {
      const a = AIM_K * (uTarget.get() - uPhi.get()) - AIM_C * uPhiV.get();
      const v = uPhiV.get() + a * dt;
      uPhiV.set(v);
      const phi = uPhi.get() + v * dt;
      uPhi.set(phi);
      // The afterglow follows the beam through low-pass copies with the trail's delays.
      const trail = uTrail.get().slice();
      for (let i = 0; i < trail.length; i += 1) trail[i] += (phi - trail[i]) * (1 - Math.exp(-dt / TRAIL[i]));
      uTrail.set(trail);
    }
    const r = resumeAt.get();
    if (r >= 0 && n >= r) {
      resumeAt.set(-1);
      uTarget.set(Math.round(uPhi.get() / (2 * Math.PI)) * 2 * Math.PI);
      uPage.set(withTiming(0, { duration: RETURN_MS, easing: EASE_IN_OUT }));
      start.set(n + RETURN_MS / 1000 + 0.1);
    }
  }, !reduced);

  function takeOver() {
    'worklet';
    const n = now.get();
    const s = start.get();
    if (s >= 0 && n >= s) {
      const t = (n - s) % LOOP;
      const phi = phiAt(t);
      uPhi.set(phi);
      uPhiV.set(0);
      uTarget.set(phi);
      uTrail.set(TRAIL.map((b) => phiBefore(t, b)));
      uPage.set(pageAt(t, PAGE_OUT, PAGE_BACK));
    } else if (s >= 0) {
      uPage.set(uPage.get());
    }
    start.set(-1);
    resumeAt.set(-1);
  }

  function released() {
    'worklet';
    if (auto) resumeAt.set(now.get() + 2);
  }

  function goTo(page: number) {
    'worklet';
    const next = Math.max(0, Math.min(PAGES - 1, page));
    if (next === Math.round(uPage.get())) return;
    uPage.set(withTiming(next, { duration: reduced ? 300 : 600, easing: EASE_IN_OUT }));
    scheduleOnRN(haptic, 'tick');
  }

  const scene = useDerivedValue(() => {
    const n = now.get();
    const s = start.get();
    if (s >= 0 && n >= s) {
      const t = (n - s) % LOOP;
      return { phi: [phiAt(t), ...TRAIL.map((b) => phiBefore(t, b))], page: pageAt(t, PAGE_OUT, PAGE_BACK) };
    }
    return { phi: [uPhi.get(), ...uTrail.get()], page: uPage.get() };
  });

  const uniforms = useDerivedValue(() => {
    const sc = scene.get();
    const n = now.get();
    const ambient = n < 0 ? 0 : (((n - origin.get()) % LOOP) + LOOP) % LOOP;
    return {
      size: [g?.w ?? 1, g?.h ?? 1],
      p3: P3,
      unit: g?.u ?? 1,
      lamp: [g?.lampX ?? 0, g?.lampY ?? 0],
      horizon: g?.horizon ?? 1,
      phiA: [sc.phi[0], sc.phi[1], sc.phi[2], sc.phi[3]],
      phiB: [sc.phi[4], sc.phi[5]],
      clk: [ambient, LOOP],
      text: [sc.page * (g?.w ?? 0), g?.contentY ?? 0, ratio, 0],
    };
  });

  const dotsY = g ? g.h - insets.bottom - 16 - 56 - 30 : 0;
  const overlay = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, size.width), Math.max(1, size.height)));
    if (g) {
      const sc = scene.get();
      drawLamp(canvas, g, facing(sc.phi[0]));
      drawDots(canvas, g.w / 2, dotsY, sc.page);
    }
    return recorder.finishRecordingAsPicture();
  });

  const aim = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      if (!g) return;
      takeOver();
      uTarget.set(aimAt(e.x - g.lampX, e.y - g.lampY, uPhi.get()));
      if (reduced) {
        uPhi.set(uTarget.get());
        uTrail.set([uPhi.get(), uPhi.get(), uPhi.get(), uPhi.get(), uPhi.get()]);
      }
    })
    .onUpdate((e) => {
      if (!g) return;
      uTarget.set(aimAt(e.x - g.lampX, e.y - g.lampY, uTarget.get()));
      if (reduced) {
        uPhi.set(uTarget.get());
        uTrail.set([uPhi.get(), uPhi.get(), uPhi.get(), uPhi.get(), uPhi.get()]);
      }
    })
    .onEnd((e) => {
      if (Math.abs(e.velocityX) > 650 && Math.abs(e.translationX) > 40) {
        goTo(Math.round(uPage.get()) + (e.velocityX < 0 ? 1 : -1));
      }
    })
    .onFinalize(() => {
      released();
    });

  const next = () => {
    haptic('light');
    scheduleOnUI(() => {
      'worklet';
      takeOver();
      goTo(Math.round(uPage.get()) + 1);
      released();
    });
  };

  const buttonStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.03 * pressed.get() }] }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      {g && effect && content ? (
        <>
          <Canvas style={StyleSheet.absoluteFill}>
            <Fill>
              <Shader source={effect} uniforms={uniforms}>
                <ImageShader
                  image={content}
                  fit="fill"
                  rect={{ x: 0, y: 0, width: content.width(), height: content.height() }}
                  tx="decal"
                  ty="decal"
                />
              </Shader>
            </Fill>
            <Picture picture={tower} />
            <Picture picture={overlay} />
          </Canvas>

          <GestureDetector gesture={aim}>
            <View
              style={[styles.touch, { height: dotsY - 16 }]}
              accessible
              accessibilityLabel="A lighthouse beam reveals the app's features. Drag to aim the beam, swipe to change page."
            />
          </GestureDetector>

          <View pointerEvents="box-none" style={[styles.header, { top: insets.top + 12 }]}>
            <Text style={styles.brand}>BEACON</Text>
            <Text style={styles.skip} accessibilityRole="button">
              Skip
            </Text>
          </View>

          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Get started"
            onPress={next}
            onPressIn={() => pressed.set(withTiming(1, { duration: 110 }))}
            onPressOut={() => pressed.set(withSpring(0, { duration: 350, dampingRatio: 0.7 }))}
            style={[styles.buttonWrap, { bottom: insets.bottom + 16 }]}
          >
            <Animated.View style={[styles.button, buttonStyle]}>
              <Text style={styles.buttonText}>Get started</Text>
            </Animated.View>
          </Pressable>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#0A1430', overflow: 'hidden' },
  touch: { position: 'absolute', left: 0, right: 0, top: 0 },
  header: {
    position: 'absolute',
    left: 24,
    right: 24,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  brand: { color: '#F2E6C8', fontSize: 13, fontWeight: '700', letterSpacing: 3.2 },
  skip: { color: 'rgba(242,230,200,0.62)', fontSize: 15, fontWeight: '500' },
  buttonWrap: { position: 'absolute', left: 24, right: 24, height: 56 },
  button: {
    flex: 1,
    borderRadius: 28,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#F3CF86',
    experimental_backgroundImage: 'linear-gradient(180deg, #FBE2A8 0%, #F3CF86 55%, #E5B566 100%)',
    boxShadow: '0 1px 2px rgba(5,8,25,0.35), 0 12px 30px rgba(243,200,120,0.22), inset 0 1px 0 rgba(255,255,255,0.55)',
  },
  buttonText: { color: '#1B1E3D', fontSize: 17, fontWeight: '600' },
});
