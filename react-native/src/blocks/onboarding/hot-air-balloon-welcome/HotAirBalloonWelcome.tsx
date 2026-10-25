import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useMemo } from 'react';
import { PixelRatio, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
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
import Svg, { Defs, Path, Stop, LinearGradient as SvgGradient } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { drawScene } from './balloonArt';
import { RollStack } from './BalloonLabels';
import { type BalloonPose, LOOP, PATTERN_ANGLE, STEPS, loopPose } from './balloonMotion';
import { bakeArt, makeBalloonGeo } from './balloonScenery';

/*
 Hot Air Balloon Welcome — the onboarding of "Drift", a travel planner. A striped hot-air balloon
 stands on a meadow at dawn over layered hills; each onboarding step is an altitude. Hold the
 burner button: the flame roars (with a bloom that lights the envelope from inside), the envelope
 swells a little and the balloon climbs, the hills slide away below with parallax, clouds come
 down past it (two of them in front), and the step text rolls. Everything in the scene is one
 Skia Canvas (balloonArt.ts): the sky, the four hill layers and three cloud sprites are baked once
 at up to 2×; the balloon (twelve gores turning slowly, load tapes, seams, rim light, the glow,
 the flame, basket and rig) is drawn every frame. The text panel is views.

 autoPlay (LOOP = 10 s, balloonMotion.ts):
   0.0 – 1.6   rest on the ground, step 1.
   1.6 – 4.0   burn (1.6 – 2.6, roar in 0.15 s, out in 0.35 s) and rise to step 2 (2.0 – 4.0,
               ease-in-out); the text rolls at 2.4.
   4.6 – 7.0   burn (4.6 – 5.4) and rise to step 3 (4.8 – 7.0); the text rolls at 5.2.
   7.0 – 8.8   a smooth descent to the ground (a short burn at 8.0 softens the landing); the text
               rolls back through step 2 to step 1 (7.6 – 8.4). Rest to 10.0.
   The envelope turns one colour pattern (120°) per loop; aloft it sways ±1.3° and bobs 2 pt.

 Touch: hold the burner button to heat (the flame follows within ~0.15 s) and climb 0.6 steps a
 second (rubber-banding past step 3); let go and the balloon settles on the nearest step (spring
 700 ms, damping 0.85). Drag the sky up or down to change altitude directly; a flick carries its
 velocity into the same spring. A selection haptic as each step is reached. 2 s after the last
 touch the balloon descends to the ground (900 ms) and the loop restarts.
 Reduced motion: the ground frame, no loop; the button and drag change the step with short fades.
 */

const INK = '#FBF1EA';
const MUTED = 'rgba(251, 241, 234, 0.64)';
const CORAL = '#E5664F';
const PANEL_H = 256;
const IDLE_S = 2;
const RETURN_MS = 900;
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);

const TITLES = ['Plan trips that float', 'Rise above the noise', 'See the whole route'];
const BODIES = [
  'Drift gathers flights, stays and day plans into one calm itinerary.',
  'We watch prices and delays, and only nudge you when it matters.',
  'Every leg on one offline map, from takeoff to landing.',
];

function light() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function tickHaptic() {
  Haptics.selectionAsync().catch(() => {});
}

function Flame() {
  return (
    <Svg width={18} height={22} viewBox="0 0 18 22">
      <Defs>
        <SvgGradient id="flame" x1="0" y1="1" x2="0" y2="0">
          <Stop offset="0" stopColor="#FFE7B0" />
          <Stop offset="0.5" stopColor="#FFB255" />
          <Stop offset="1" stopColor="#FF7A3D" />
        </SvgGradient>
      </Defs>
      <Path d="M9 1C10.5 5 15.5 8 15.5 13.5C15.5 17.6 12.6 21 9 21C5.4 21 2.5 17.6 2.5 13.5C2.5 10.6 4.2 8.6 5.6 7.4C5.8 9.6 6.8 11 8 11.6C7.6 7.6 8.2 4.2 9 1Z" fill="url(#flame)" />
    </Svg>
  );
}

export function HotAirBalloonWelcome({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const { width, height } = useWindowDimensions();
  const panelTop = height - insets.bottom - PANEL_H;
  const g = useMemo(() => makeBalloonGeo(width, height, panelTop - 8, insets.top + 70), [width, height, panelTop, insets.top]);
  const art = useMemo(() => bakeArt(g, PixelRatio.get()), [g]);

  const t = useSharedValue(0);
  const manual = useSharedValue(auto ? 0 : 1);
  const clock = useSharedValue(0);
  const resumeAt = useSharedValue(-1);
  const alt = useSharedValue(0);
  const heat = useSharedValue(0);
  const turn = useSharedValue(0);
  const holding = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  const mtime = useSharedValue(0);

  function settle(velocity: number) {
    'worklet';
    const a = alt.get();
    const target = Math.max(0, Math.min(STEPS - 1, Math.round(a + velocity * 0.2)));
    alt.set(reduced ? withTiming(target, { duration: 200 }) : withSpring(target, { duration: 700, dampingRatio: 0.85, velocity }));
  }

  function backToRest() {
    'worklet';
    const k = Math.round(turn.get() / PATTERN_ANGLE);
    turn.set(withTiming(k * PATTERN_ANGLE, { duration: RETURN_MS, easing: EASE_IN_OUT }));
    heat.set(withTiming(0, { duration: 300 }));
    alt.set(
      withTiming(0, { duration: RETURN_MS, easing: EASE_IN_OUT }, (finished) => {
        if (finished && auto) {
          t.set(0);
          turn.set(0);
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
    if (holding.get() === 1) {
      const h = Math.min(1, heat.get() + dt * 6.5);
      heat.set(h);
      if (!reduced) {
        const a = alt.get();
        const over = Math.max(0, a - (STEPS - 1));
        alt.set(a + (dt * 0.6 * h) / (1 + over * 6));
      }
    }
    const at = resumeAt.get();
    if (at > 0 && clock.get() >= at && holding.get() === 0) {
      resumeAt.set(-1);
      backToRest();
    }
  });

  const pose = useDerivedValue<BalloonPose>(() => {
    if (manual.get() === 0) return loopPose(t.get());
    const a = alt.get();
    return { alt: a, heat: heat.get(), step: Math.max(0, Math.min(STEPS - 1, a)), turn: turn.get(), time: mtime.get() };
  });
  const step = useDerivedValue(() => pose.get().step);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawScene(canvas, g, art, pose.get());
    return recorder.finishRecordingAsPicture();
  });

  // A selection tick as a finger brings the balloon to each step.
  useAnimatedReaction(
    () => (manual.get() === 1 ? Math.round(alt.get()) : -1),
    (now, before) => {
      if (now >= 0 && before !== null && before >= 0 && now !== before) scheduleOnRN(tickHaptic);
    },
  );

  function takeOver() {
    'worklet';
    resumeAt.set(-1);
    if (manual.get() === 0) {
      const p = loopPose(t.get());
      alt.set(p.alt);
      heat.set(p.heat);
      heat.set(withTiming(0, { duration: 350 }));
      turn.set(p.turn);
      mtime.set(t.get());
      manual.set(1);
    } else {
      alt.set(alt.get());
      turn.set(turn.get());
    }
  }

  const burner = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .onBegin(() => {
          takeOver();
          holding.set(1);
          if (reduced) {
            const next = Math.min(STEPS - 1, Math.round(alt.get()) + 1);
            alt.set(withTiming(next, { duration: 200 }));
          }
          scheduleOnRN(light);
        })
        .onFinalize(() => {
          holding.set(0);
          heat.set(withTiming(0, { duration: 350 }));
          if (!reduced) settle(0.6);
          if (auto) resumeAt.set(clock.get() + IDLE_S);
        }),
    // The shared values are stable.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [reduced, auto],
  );

  const sky = useMemo(
    () =>
      Gesture.Pan()
        .activeOffsetY([-8, 8])
        .onBegin(() => {
          takeOver();
          dragFrom.set(alt.get());
        })
        .onUpdate((e) => {
          let a = dragFrom.get() - e.translationY / g.rise;
          if (a < 0) a = -(-a / (1 - a)) * 0.35;
          if (a > STEPS - 1) {
            const over = a - (STEPS - 1);
            a = STEPS - 1 + (over / (1 + over)) * 0.35;
          }
          alt.set(reduced ? Math.round(Math.max(0, Math.min(STEPS - 1, a))) : a);
        })
        .onEnd((e) => {
          settle(-e.velocityY / g.rise);
        })
        .onFinalize(() => {
          if (auto) resumeAt.set(clock.get() + IDLE_S);
        }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [g, reduced, auto],
  );

  const dot = useAnimatedStyle(() => ({ transform: [{ translateX: step.get() * 16 }] }));
  const glow = useAnimatedStyle(() => ({ opacity: pose.get().heat }));

  return (
    <View style={styles.screen}>
      <GestureDetector gesture={sky}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="A hot-air balloon at dawn. Drag up or down to change altitude.">
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>

      <View style={[styles.header, { top: insets.top + 12 }]} pointerEvents="box-none">
        <Text style={styles.brand}>drift</Text>
        <Text style={styles.skip} accessibilityRole="button">
          Skip
        </Text>
      </View>

      <View style={[styles.panel, { top: panelTop, paddingBottom: insets.bottom + 16 }]}>
        <LinearGradient colors={['rgba(40, 30, 66, 0.96)', 'rgba(30, 22, 52, 0.98)', '#1A1430']} locations={[0, 0.4, 1]} style={[StyleSheet.absoluteFill, styles.panelBg]} />
        <View style={styles.panelEdge} />
        <View style={styles.dots}>
          {[0, 1, 2].map((i) => (
            <View key={i} style={styles.dot} />
          ))}
          <Animated.View style={[styles.dotOn, dot]} />
        </View>
        <RollStack items={TITLES} index={step} height={36} style={styles.title} align="flex-start" />
        <RollStack items={BODIES} index={step} height={44} style={styles.body} align="flex-start" lines={2} />
        <GestureDetector gesture={burner}>
          <View style={styles.button} accessible accessibilityRole="button" accessibilityLabel="Hold to rise">
            <Animated.View style={[StyleSheet.absoluteFill, styles.buttonGlow, glow]} />
            <Flame />
            <Text style={styles.buttonText}>Hold to rise</Text>
          </View>
        </GestureDetector>
        <Text style={styles.footnote}>Already planning with Drift? Sign in</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#1A1430', overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24, height: 36, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { color: INK, fontSize: 26, fontWeight: '800', letterSpacing: -0.8 },
  skip: { color: MUTED, fontSize: 15, fontWeight: '600', paddingVertical: 8, paddingLeft: 12 },
  panel: { position: 'absolute', left: 0, right: 0, bottom: 0, paddingTop: 20, paddingHorizontal: 24, gap: 8 },
  panelBg: { borderTopLeftRadius: 30, borderTopRightRadius: 30, borderCurve: 'continuous', boxShadow: '0 -1px 2px rgba(0,0,0,0.2), 0 -16px 40px rgba(10, 6, 30, 0.35)' },
  panelEdge: { position: 'absolute', top: 0, left: 30, right: 30, height: StyleSheet.hairlineWidth, backgroundColor: 'rgba(255, 220, 200, 0.3)' },
  dots: { flexDirection: 'row', gap: 8, height: 8, marginBottom: 2 },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: 'rgba(251, 241, 234, 0.22)' },
  dotOn: { position: 'absolute', left: 0, top: 0, width: 8, height: 8, borderRadius: 4, backgroundColor: CORAL },
  title: { color: INK, fontSize: 28, fontWeight: '700', letterSpacing: -0.5 },
  body: { color: MUTED, fontSize: 15, lineHeight: 21 },
  button: {
    marginTop: 8,
    height: 56,
    borderRadius: 28,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
    backgroundColor: '#C9533F',
    overflow: 'hidden',
    boxShadow: '0 1px 2px rgba(0,0,0,0.35), 0 12px 28px rgba(229, 102, 79, 0.35), inset 0 1px 0 rgba(255, 230, 210, 0.35)',
  },
  buttonGlow: { backgroundColor: '#FF9A55', borderRadius: 28 },
  buttonText: { color: '#FFF6EE', fontSize: 17, fontWeight: '700' },
  footnote: { color: 'rgba(251, 241, 234, 0.45)', fontSize: 13, textAlign: 'center', marginTop: 4 },
});
