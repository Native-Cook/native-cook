import { Canvas, Fill, Picture, Shader, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useMemo } from 'react';
import { Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedReaction,
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

import { buildOutlines, drawConstellations, makeSkyGeo } from './constellationArt';
import { RollStack } from './ConstellationLabels';
import { LOOP, STEPS, STEP_ANGLE, type SkyPose, loopPose } from './constellationMotion';
import { SKY_SKSL } from './constellationShader';

/*
 Constellation Steps Welcome — the onboarding of "Orrery", a stargazing app. A deep night sky (a
 runtime shader: navy gradient, a Milky Way of fbm dust with a dark rift, two twinkling layers of
 hashed stars) turns round the pole, which sits above the screen. Each step brings a constellation
 to the centre and draws it: its stars bloom as glowing lines reach them one after another, the
 brightest is named, and a faint line drawing of its figure fades in (the Swan, the Queen's throne,
 the Hunter). Constellations, lines and labels are Skia drawing in the same Canvas
 (constellationArt.ts); the text is views.

 autoPlay (LOOP = 10 s, constellationMotion.ts):
   0.0 – 1.6   rest: step 1, Cygnus's stars waiting.
   1.6 – 3.6   Cygnus is drawn (seven lines in turn), Deneb named, the swan fades in.
   3.6 – 4.4   the sky turns 48° (ease-in-out) to Cassiopeia; the text rolls; 4.4 – 6.2 it is drawn.
   6.2 – 7.0   on to Orion; 7.0 – 8.4 drawn.
   8.4 – 9.4   the sky turns back home through both steps while every line and figure fades
               (8.4 – 9.2); the text rolls back. Rest to 10.0.

 Touch: swipe sideways and the sky turns with the finger (rubber-banding past the first and last
 steps); let go and it settles on the nearest step (spring 800 ms, damping 0.85, carrying the
 flick), whose constellation then draws itself (1.4 s). "Continue" steps on (the last one starts
 over). A selection haptic per step. 2 s after the last touch the sky turns home and fades (900 ms)
 and the loop restarts. Reduced motion: step 1 drawn, no loop; steps change with short fades.
 */

const INK = '#F3EEE4';
const MUTED = 'rgba(243, 238, 228, 0.62)';
const GOLD = '#F2D48A';
const PANEL_H = 256;
const IDLE_S = 2;
const RETURN_MS = 900;
const DRAW_MS = 1400;
const P3 = Platform.OS === 'ios' ? 1 : 0;
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const FAMILY = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });

const TITLES = ['Meet the Swan', 'Find the Queen', 'Follow the Hunter'];
const BODIES = [
  'Cygnus flies down the Milky Way. Its brightest star, Deneb, marks the tail.',
  "Five bright stars draw Cassiopeia's W, circling the pole all year.",
  "Orion's belt of three stars points the way to the brightest star of all.",
];
const CAPTIONS = ['CYGNUS · HIGH IN THE EAST', 'CASSIOPEIA · NORTH, ALL NIGHT', 'ORION · RISING AFTER 22:00'];

function light() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function tickHaptic() {
  Haptics.selectionAsync().catch(() => {});
}

export function ConstellationStepsWelcome({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const { width, height } = useWindowDimensions();
  const panelTop = height - insets.bottom - PANEL_H;
  const g = useMemo(() => makeSkyGeo(width, height, insets.top + 84, panelTop), [width, height, insets.top, panelTop]);
  const effect = useMemo(() => Skia.RuntimeEffect.Make(SKY_SKSL), []);
  const outlines = useMemo(() => buildOutlines(), []);
  const font = useMemo(() => matchFont({ fontFamily: FAMILY, fontSize: 11, fontWeight: '600' }), []);
  const radius = g.cy - g.py;

  const t = useSharedValue(0);
  const manual = useSharedValue(auto ? 0 : 1);
  const clock = useSharedValue(0);
  const resumeAt = useSharedValue(-1);
  const mtime = useSharedValue(0);
  const theta = useSharedValue(0);
  const d0 = useSharedValue(reduced ? 1 : 0);
  const d1 = useSharedValue(0);
  const d2 = useSharedValue(0);
  const fade = useSharedValue(1);
  const dragFrom = useSharedValue(0);

  function drawStep(k: number) {
    'worklet';
    const d = k === 0 ? d0 : k === 1 ? d1 : d2;
    if (d.get() >= 0.999) return;
    d.set(reduced ? withTiming(1, { duration: 250 }) : withTiming(1, { duration: DRAW_MS, easing: Easing.inOut(Easing.quad) }));
  }

  function settleOn(k: number, velocity: number) {
    'worklet';
    theta.set(reduced ? withTiming(k * STEP_ANGLE, { duration: 250 }) : withSpring(k * STEP_ANGLE, { duration: 800, dampingRatio: 0.85, velocity }));
    drawStep(k);
  }

  function backToRest() {
    'worklet';
    fade.set(withTiming(0, { duration: RETURN_MS - 200 }));
    theta.set(
      withTiming(0, { duration: RETURN_MS, easing: EASE_IN_OUT }, (finished) => {
        if (finished && auto) {
          d0.set(0);
          d1.set(0);
          d2.set(0);
          fade.set(1);
          t.set(0);
          manual.set(0);
        }
      }),
    );
  }

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    clock.set(clock.get() + dt);
    if (manual.get() === 0) {
      t.set((t.get() + dt) % LOOP);
      return;
    }
    mtime.set((mtime.get() + dt) % (LOOP * 6));
    const at = resumeAt.get();
    if (at > 0 && clock.get() >= at) {
      resumeAt.set(-1);
      backToRest();
    }
  });

  const pose = useDerivedValue<SkyPose>(() => {
    if (manual.get() === 0) return loopPose(t.get());
    const th = theta.get();
    return { theta: th, drawn: [d0.get(), d1.get(), d2.get()], fade: fade.get(), step: Math.max(0, Math.min(STEPS - 1, th / STEP_ANGLE)), time: mtime.get() };
  });
  const step = useDerivedValue(() => pose.get().step);

  const uniforms = useDerivedValue(() => {
    const p = pose.get();
    return { res: [width, height], pole: [g.px, g.py], theta: p.theta, phase: (2 * Math.PI * p.time) / LOOP, p3: P3 };
  });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawConstellations(canvas, g, outlines, pose.get(), font);
    return recorder.finishRecordingAsPicture();
  });

  useAnimatedReaction(
    () => (manual.get() === 1 ? Math.round(theta.get() / STEP_ANGLE) : -1),
    (now, before) => {
      if (now >= 0 && before !== null && before >= 0 && now !== before) scheduleOnRN(tickHaptic);
    },
  );

  function takeOver() {
    'worklet';
    resumeAt.set(-1);
    if (manual.get() === 0) {
      const p = loopPose(t.get());
      theta.set(p.theta);
      d0.set(p.drawn[0]);
      d1.set(p.drawn[1]);
      d2.set(p.drawn[2]);
      fade.set(p.fade);
      mtime.set(t.get());
      manual.set(1);
    } else {
      theta.set(theta.get());
    }
    if (fade.get() < 1) fade.set(withTiming(1, { duration: 300 }));
  }

  const swipe = useMemo(
    () =>
      Gesture.Pan()
        .activeOffsetX([-10, 10])
        .onBegin(() => {
          takeOver();
          dragFrom.set(theta.get());
        })
        .onUpdate((e) => {
          // In steps, rubber-banding to at most 0.35 of a step past either end.
          let u = (dragFrom.get() - e.translationX / radius) / STEP_ANGLE;
          if (u < 0) u = -((-u / (1 - u)) * 0.35);
          if (u > STEPS - 1) {
            const over = u - (STEPS - 1);
            u = STEPS - 1 + (over / (1 + over)) * 0.35;
          }
          const th = u * STEP_ANGLE;
          if (!reduced) theta.set(th);
        })
        .onEnd((e) => {
          const v = -e.velocityX / radius;
          const k = Math.max(0, Math.min(STEPS - 1, Math.round((reduced ? dragFrom.get() - e.translationX / radius : theta.get() + v * 0.2) / STEP_ANGLE)));
          settleOn(k, v);
        })
        .onFinalize(() => {
          if (auto) resumeAt.set(clock.get() + IDLE_S);
        }),
    // The shared values are stable; the gesture depends on the geometry.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [radius, reduced, auto],
  );

  const onContinue = () => {
    light();
    scheduleOnUI(() => {
      'worklet';
      takeOver();
      const now = Math.round(theta.get() / STEP_ANGLE);
      settleOn(now >= STEPS - 1 ? 0 : now + 1, 0);
      if (auto) resumeAt.set(clock.get() + IDLE_S + 1.2);
    });
  };

  const dot = useAnimatedStyle(() => ({ transform: [{ translateX: step.get() * 16 }] }));

  return (
    <View style={styles.screen}>
      <GestureDetector gesture={swipe}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="The night sky. Swipe sideways to turn it to the next constellation.">
          <Canvas style={StyleSheet.absoluteFill}>
            {effect ? (
              <Fill>
                <Shader source={effect} uniforms={uniforms} />
              </Fill>
            ) : null}
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>

      <View style={[styles.header, { top: insets.top + 12 }]} pointerEvents="box-none">
        <View style={styles.brand}>
          <View style={styles.mark}>
            <View style={styles.markOrbit} />
            <View style={styles.markSun} />
          </View>
          <Text style={styles.brandText}>orrery</Text>
        </View>
        <Text style={styles.skip} accessibilityRole="button">
          Skip
        </Text>
      </View>
      <View style={[styles.caption, { top: insets.top + 58 }]} pointerEvents="none">
        <RollStack items={CAPTIONS} index={step} height={16} style={styles.captionText} />
      </View>

      <View style={[styles.panel, { top: panelTop, paddingBottom: insets.bottom + 16 }]}>
        <LinearGradient colors={['rgba(7, 10, 26, 0)', 'rgba(7, 10, 26, 0.82)', 'rgba(6, 8, 22, 0.96)']} locations={[0, 0.3, 1]} style={[StyleSheet.absoluteFill, { top: -40 }]} pointerEvents="none" />
        <View style={styles.dots}>
          {[0, 1, 2].map((i) => (
            <View key={i} style={styles.dot} />
          ))}
          <Animated.View style={[styles.dotOn, dot]} />
        </View>
        <RollStack items={TITLES} index={step} height={36} style={styles.title} align="flex-start" />
        <RollStack items={BODIES} index={step} height={44} style={styles.body} align="flex-start" lines={2} />
        <Pressable onPress={onContinue} style={({ pressed }) => [styles.button, pressed && styles.buttonPressed]} accessibilityRole="button">
          <Text style={styles.buttonText}>Continue</Text>
        </Pressable>
        <Text style={styles.footnote}>Uses your location only to face the right sky</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#070A1A', overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24, height: 36, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  mark: { width: 26, height: 26, alignItems: 'center', justifyContent: 'center' },
  markOrbit: { position: 'absolute', width: 26, height: 14, borderRadius: 13, borderWidth: 1.2, borderColor: GOLD, transform: [{ rotate: '-20deg' }] },
  markSun: { width: 8, height: 8, borderRadius: 4, backgroundColor: GOLD },
  brandText: { color: INK, fontSize: 24, fontWeight: '700', letterSpacing: -0.6 },
  skip: { color: MUTED, fontSize: 15, fontWeight: '600', paddingVertical: 8, paddingLeft: 12 },
  caption: { position: 'absolute', left: 24, right: 24 },
  captionText: { color: 'rgba(242, 212, 138, 0.8)', fontSize: 11, fontWeight: '700', letterSpacing: 2 },
  panel: { position: 'absolute', left: 0, right: 0, bottom: 0, paddingTop: 20, paddingHorizontal: 24, gap: 8 },
  dots: { flexDirection: 'row', gap: 8, height: 8, marginBottom: 2 },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: 'rgba(243, 238, 228, 0.22)' },
  dotOn: { position: 'absolute', left: 0, top: 0, width: 8, height: 8, borderRadius: 4, backgroundColor: GOLD },
  title: { color: INK, fontSize: 28, fontWeight: '700', letterSpacing: -0.5 },
  body: { color: MUTED, fontSize: 15, lineHeight: 21 },
  button: {
    marginTop: 8,
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: GOLD,
    boxShadow: '0 1px 2px rgba(0,0,0,0.35), 0 12px 30px rgba(242, 212, 138, 0.25), inset 0 1px 0 rgba(255, 250, 230, 0.7)',
  },
  buttonPressed: { transform: [{ scale: 0.97 }] },
  buttonText: { color: '#1C1A2E', fontSize: 17, fontWeight: '700' },
  footnote: { color: 'rgba(243, 238, 228, 0.42)', fontSize: 13, textAlign: 'center', marginTop: 4 },
});
