import { Canvas, LinearGradient, RoundedRect, Shader, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';
import { AGENT_STATUS_SHADER } from './agentStatusShader';

// The chat of an invented travel agent, Atlas, working on a request. Its status is one glass
// pill whose inside is alive and changes with what it is doing: "Thinking…" is an aurora wave
// over deep violet, "Searching…" smoky panels with a light sweeping through, "Planning…" blue and
// red with a swirling edge between them, "Writing…" teal-to-lime stripes drifting by. Under it the
// steps tick off one by one. Tap the pill to skip to the next step.
//
// The pill's inside is one SkSL shader (agentStatusShader.ts) with the four looks cross-faded.
// Layout (393 x 852 pt): 34 pt top row; the user's message bubble; the pill 300 x 84 pt (radius
// 42) at 40 % of the height with its label 22 pt semibold; four step rows; the input bar at the
// bottom.
//
// autoPlay timeline (LOOP = 8.8 s): four steps of 2.2 s — thinking, searching, planning,
// writing; each look cross-fades into the next over 0.4 s at the step's end (smoothstep); the
// label cross-fades with it; each finished step gets a tick; over the last 0.4 s the ticks clear
// as the pill goes back to thinking. All motion inside is periodic in the loop.
// Tapping the pill jumps to the next step (light haptic).

/** Seconds. */
export const LOOP = 8.8;
const STEP = 2.2;
const FADE = 0.4;
const PW = 300;
const PH = 84;

const effect = Skia.RuntimeEffect.Make(AGENT_STATUS_SHADER);
const P3 = Platform.OS === 'ios' ? 1 : 0;

const STEPS = [
  { label: 'Thinking…', row: 'Reading your request' },
  { label: 'Searching…', row: 'Searching flights and stays' },
  { label: 'Planning…', row: 'Planning a three-day route' },
  { label: 'Writing…', row: 'Writing your itinerary' },
];

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function smooth(x: number) {
  'worklet';
  const u = clamp01(x);
  return u * u * (3 - 2 * u);
}

/** Weight of each look `t` seconds into the loop. */
function modesAt(t: number) {
  'worklet';
  const w = [0, 0, 0, 0];
  const k = Math.floor(t / STEP) % 4;
  const u = t - Math.floor(t / STEP) * STEP;
  const f = smooth((u - (STEP - FADE)) / FADE);
  w[k] += 1 - f;
  w[(k + 1) % 4] += f;
  return w;
}

function Label({ k, modes }: { k: number; modes: SharedValue<number[]> }) {
  const style = useAnimatedStyle(() => {
    const o = modes.get()[k];
    return { opacity: o, transform: [{ translateY: 6 * (1 - o) }] };
  });
  return <Animated.Text style={[styles.label, style]}>{STEPS[k].label}</Animated.Text>;
}

function StepRow({ k, clock }: { k: number; clock: SharedValue<number> }) {
  const doneStyle = useAnimatedStyle(() => {
    const t = clock.get();
    // Done once its step is over; cleared again in the last 0.4 s of the loop.
    const done = t > (k + 1) * STEP - FADE / 2 ? 1 : 0;
    const clear = smooth((t - (LOOP - FADE)) / FADE);
    return { opacity: done * (1 - clear) };
  });
  const activeStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const on = t >= k * STEP && t < (k + 1) * STEP ? 1 : 0;
    return { opacity: 0.4 + 0.6 * on, transform: [{ rotate: `${(t / LOOP) * 360 * 8}deg` }] };
  });
  const textStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const reached = t >= k * STEP ? 1 : 0;
    return { opacity: 0.4 + 0.6 * reached };
  });
  return (
    <View style={styles.stepRow}>
      <View style={styles.stepIcon}>
        <Animated.View style={[styles.spinner, activeStyle]} />
        <Animated.View style={[styles.tick, doneStyle]}>
          <Text style={styles.tickText}>✓</Text>
        </Animated.View>
      </View>
      <Animated.Text style={[styles.stepText, textStyle]}>{STEPS[k].row}</Animated.Text>
    </View>
  );
}

type Props = {
  /** Runs through the agent's steps by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function AgentStatusPill({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const x0 = (width - PW) / 2;
  const y0 = height * 0.4 - PH / 2;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const clock = useSharedValue(0.6);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    if (autoPlay) clock.set((((elapsed.get() - origin.get()) % LOOP) + LOOP) % LOOP);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const modes = useDerivedValue(() => modesAt(clock.get()));
  const uniforms = useDerivedValue(() => {
    const m = modes.get();
    return { uP3: P3, uBox: [x0, y0, PW, PH], uMode: [m[0], m[1], m[2], m[3]], uPhase: (clock.get() / LOOP) * Math.PI * 2 };
  });

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const tap = Gesture.Tap()
    .enabled(!reduced && autoPlay)
    .onEnd(() => {
      // Jump to just before the end of this step, so the look fades into the next one.
      const t = clock.get();
      const next = (Math.floor(t / STEP) + 1) * STEP - FADE;
      if (next > t) origin.set(origin.get() - (next - t));
      scheduleOnRN(haptic);
    });

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <View style={[styles.top, { top: insets.top + 8 }]}>
        <Text style={styles.brand}>Atlas</Text>
        <Text style={styles.meta}>Trip assistant</Text>
      </View>
      <View style={[styles.bubble, { top: insets.top + 64 }]}>
        <Text style={styles.bubbleText}>Plan me three slow days in Lisbon in May — good coffee, no rush.</Text>
      </View>

      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        {effect ? (
          <RoundedRect x={x0} y={y0} width={PW} height={PH} r={PH / 2}>
            <Shader source={effect} uniforms={uniforms} />
          </RoundedRect>
        ) : null}
        <RoundedRect x={x0 + 1} y={y0 + 1} width={PW - 2} height={PH - 2} r={PH / 2 - 1} style="stroke" strokeWidth={2}>
          <LinearGradient start={vec(0, y0)} end={vec(0, y0 + PH)} colors={['rgba(255,255,255,0.85)', 'rgba(255,255,255,0.15)', 'rgba(255,255,255,0.6)']} />
        </RoundedRect>
      </Canvas>
      <GestureDetector gesture={tap}>
        <View style={[styles.pill, { left: x0, top: y0, width: PW, height: PH }]} accessible accessibilityRole="button" accessibilityLabel="Agent status. Tap to skip ahead.">
          {STEPS.map((s, k) => (
            <Label key={s.label} k={k} modes={modes} />
          ))}
        </View>
      </GestureDetector>

      <View style={[styles.steps, { top: y0 + PH + 34 }]}>
        {STEPS.map((s, k) => (
          <StepRow key={s.row} k={k} clock={clock} />
        ))}
      </View>

      <View style={[styles.footer, { paddingBottom: insets.bottom + 12 }]}>
        <View style={styles.input}>
          <Text style={styles.inputText}>Ask a follow-up…</Text>
          <View style={styles.send}>
            <Text style={styles.sendText}>↑</Text>
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#15171A' },
  top: { position: 'absolute', left: 20, right: 20, height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontSize: 20, fontWeight: '800', color: '#FFFFFF' },
  meta: { fontSize: 14, fontWeight: '600', color: 'rgba(255,255,255,0.5)' },
  bubble: { position: 'absolute', right: 20, maxWidth: 290, paddingHorizontal: 16, paddingVertical: 12, borderRadius: 22, borderBottomRightRadius: 6, backgroundColor: '#2A2D33' },
  bubbleText: { fontSize: 16, lineHeight: 22, color: '#FFFFFF' },
  pill: { position: 'absolute', alignItems: 'center', justifyContent: 'center' },
  label: { position: 'absolute', fontSize: 22, fontWeight: '600', color: '#FFFFFF', textShadowColor: 'rgba(0,0,0,0.35)', textShadowRadius: 6 },
  steps: { position: 'absolute', left: 40, right: 40, gap: 14 },
  stepRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  stepIcon: { width: 22, height: 22, alignItems: 'center', justifyContent: 'center' },
  spinner: { position: 'absolute', width: 18, height: 18, borderRadius: 9, borderWidth: 2, borderColor: 'rgba(255,255,255,0.2)', borderTopColor: '#FFFFFF' },
  tick: { position: 'absolute', width: 22, height: 22, borderRadius: 11, backgroundColor: '#3CCB7F', alignItems: 'center', justifyContent: 'center' },
  tickText: { fontSize: 13, fontWeight: '900', color: '#15171A' },
  stepText: { fontSize: 16, color: '#FFFFFF' },
  footer: { position: 'absolute', left: 16, right: 16, bottom: 0 },
  input: { height: 54, borderRadius: 27, backgroundColor: '#22252A', flexDirection: 'row', alignItems: 'center', paddingLeft: 20, paddingRight: 6 },
  inputText: { flex: 1, fontSize: 16, color: 'rgba(255,255,255,0.45)' },
  send: { width: 42, height: 42, borderRadius: 21, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center' },
  sendText: { fontSize: 20, fontWeight: '800', color: '#15171A' },
});
