import { Canvas, Rect, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';
import { NEON_ECLIPSE_SHADER } from './neonEclipseShader';

// The display settings of an invented picture frame, Lumo Frame. "Art mode" is a plain switch
// with a white pearl knob; turning it on eclipses it: the knob slides over, goes black like the
// moon over the sun and a corona of turning colour flares round it, while neon plasma floods the
// track from the knob outwards and keeps flowing, lighting the dark room and its glossy floor.
// Turning it off drains the light away. Tap or drag the switch.
//
// The switch, its glow and its reflection are one SkSL shader (neonEclipseShader.ts).
// Layout (393 x 852 pt): 34 pt top row; title 44 pt heavy; the switch 236 x 108 pt centred at
// 46 % of the height, the floor 26 pt under it; "ART MODE" label; a card with the schedule.
//
// autoPlay timeline (LOOP = 8 s):
//   0.0-1.2  rest: off, white knob on the left
//   1.2-1.7  the knob slides right (ease-out back) and goes dark (1.2-1.6)
//   1.4-2.4  the plasma floods leftwards (ease-out cubic), the corona flares
//   2.4-6.2  on: the colours flow (phase = 2 pi t / LOOP, so they are periodic)
//   6.2-6.9  off: the plasma drains (ease-in cubic), the knob slides back (6.4-6.9) and turns white
//   6.9-8.0  rest, identical to t = 0
// Tapping toggles; dragging moves the knob and snaps to the nearer side; 2 s later the loop
// starts again from that side.

/** Seconds. */
export const LOOP = 8;
const TW = 236;
const TH = 108;
const TRAVEL = TW - TH;
const HOLD = 2;

const effect = Skia.RuntimeEffect.Make(NEON_ECLIPSE_SHADER);
const P3 = Platform.OS === 'ios' ? 1 : 0;

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function easeOutBack(x: number) {
  'worklet';
  const k = 1.4;
  return 1 + (k + 1) * Math.pow(x - 1, 3) + k * Math.pow(x - 1, 2);
}
function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** Knob position (0 off .. 1 on), plasma fill and knob darkness `t` s into the loop. */
function switchAt(t: number) {
  'worklet';
  const pos = easeOutBack(seg(t, 1.2, 1.7)) - easeInOutCubic(seg(t, 6.4, 6.9));
  const fill = easeOutCubic(seg(t, 1.4, 2.4)) * (1 - Math.pow(seg(t, 6.2, 6.8), 3));
  const dark = easeInOutCubic(seg(t, 1.2, 1.6)) * (1 - easeInOutCubic(seg(t, 6.5, 6.9)));
  return { pos, fill, dark };
}

type Props = {
  brand?: string;
  title?: string;
  /** Turns art mode on and off by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function NeonEclipseToggle({ brand = 'Lumo Frame', title = 'Make the\nwall glow', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const x0 = (width - TW) / 2;
  const y0 = height * 0.46 - TH / 2;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const phase = useSharedValue(0);
  const pos = useSharedValue(0);
  const fill = useSharedValue(0);
  const dark = useSharedValue(0);
  const touching = useSharedValue(false);
  const manual = useSharedValue(false);
  const released = useSharedValue(-1);
  const target = useSharedValue(0);
  const grab = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    const t = (now - origin.get()) % LOOP;
    phase.set((t / LOOP) * Math.PI * 2);
    if (touching.get() || manual.get()) {
      // Ease the knob, fill and darkness towards the chosen side.
      const k = 1 - Math.exp(-10 * dt);
      const on = target.get();
      if (!touching.get()) pos.set(pos.get() + (on - pos.get()) * k);
      dark.set(dark.get() + (on - dark.get()) * (1 - Math.exp(-8 * dt)));
      fill.set(fill.get() + (on - fill.get()) * (1 - Math.exp(-(on > 0.5 ? 4 : 7) * dt)));
      if (!touching.get() && now - released.get() > HOLD) {
        manual.set(false);
        // Pick up the loop where it shows this side at rest.
        origin.set(on > 0.5 ? now - 3.0 : now);
      }
      return;
    }
    if (!autoPlay) return;
    const s = switchAt(t);
    pos.set(s.pos);
    fill.set(s.fill);
    dark.set(s.dark);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const uniforms = useDerivedValue(() => ({
    uP3: P3,
    uBox: [x0, y0, TW, TH],
    uKnob: [x0 + TH / 2 + TRAVEL * pos.get(), fill.get(), dark.get(), phase.get()],
  }));

  const bgStyle = useAnimatedStyle(() => {
    const f = fill.get();
    const r = Math.round(16 - 9 * f);
    const g = Math.round(16 - 6 * f);
    const b = Math.round(17 + 10 * f);
    return { backgroundColor: `rgb(${r}, ${g}, ${b})` };
  });
  const onStyle = useAnimatedStyle(() => ({ opacity: clamp01(fill.get() * 2 - 0.4) }));
  const offStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp01(fill.get() * 2 - 0.4) }));

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const choose = (on: number) => {
    'worklet';
    touching.set(false);
    manual.set(true);
    target.set(on);
    released.set(elapsed.get());
    scheduleOnRN(haptic);
  };
  const tap = Gesture.Tap()
    .enabled(!reduced)
    .onEnd(() => choose(pos.get() > 0.5 ? 0 : 1));
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(6)
    .onBegin(() => grab.set(pos.get()))
    .onStart(() => {
      touching.set(true);
      manual.set(true);
    })
    .onUpdate((e) => {
      const p = clamp01(grab.get() + e.translationX / TRAVEL);
      pos.set(p);
      target.set(p > 0.5 ? 1 : 0);
    })
    .onEnd(() => choose(pos.get() > 0.5 ? 1 : 0));

  return (
    <Animated.View style={[styles.screen, bgStyle]}>
      <StatusBar style="light" />
      <View style={[styles.head, { top: insets.top + 8 }]} pointerEvents="none">
        <View style={styles.row}>
          <Text style={styles.brand}>{brand}</Text>
          <Text style={styles.meta}>Living room</Text>
        </View>
        <Text style={styles.title}>{title}</Text>
      </View>

      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        {effect ? (
          <Rect x={0} y={y0 - 160} width={width} height={TH + 360}>
            <Shader source={effect} uniforms={uniforms} />
          </Rect>
        ) : null}
      </Canvas>
      <GestureDetector gesture={Gesture.Exclusive(pan, tap)}>
        <View style={[styles.hit, { left: x0 - 12, top: y0 - 16, width: TW + 24, height: TH + 32 }]} accessible accessibilityRole="switch" accessibilityLabel="Art mode" />
      </GestureDetector>

      <View style={[styles.labels, { top: y0 - 46 }]} pointerEvents="none">
        <Text style={styles.label}>ART MODE</Text>
      </View>
      <View style={[styles.labels, { top: y0 + TH + 92 }]} pointerEvents="none">
        <Animated.Text style={[styles.state, offStyle]}>Off · the frame sleeps</Animated.Text>
        <Animated.Text style={[styles.state, styles.over, onStyle]}>On · showing “Solar Bloom”</Animated.Text>
      </View>

      <View style={[styles.footer, { paddingBottom: insets.bottom + 16 }]} pointerEvents="none">
        <View style={styles.card}>
          <View style={styles.cardRow}>
            <Text style={styles.cardTitle}>Glow schedule</Text>
            <Text style={styles.cardValue}>18:00 – 23:30</Text>
          </View>
          <View style={styles.divider} />
          <View style={styles.cardRow}>
            <Text style={styles.cardTitle}>Brightness</Text>
            <Text style={styles.cardValue}>70 %</Text>
          </View>
        </View>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  head: { position: 'absolute', left: 24, right: 24 },
  row: { height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontSize: 17, fontWeight: '800', color: '#FFFFFF', letterSpacing: 0.2 },
  meta: { fontSize: 14, fontWeight: '600', color: 'rgba(255,255,255,0.55)' },
  title: { marginTop: 16, fontSize: 44, lineHeight: 48, fontWeight: '900', color: '#FFFFFF', letterSpacing: -1.5 },
  hit: { position: 'absolute' },
  labels: { position: 'absolute', left: 24, right: 24, alignItems: 'center' },
  label: { fontSize: 14, fontWeight: '800', color: '#FFFFFF', letterSpacing: 3 },
  state: { fontSize: 16, fontWeight: '600', color: 'rgba(255,255,255,0.75)' },
  over: { position: 'absolute' },
  footer: { position: 'absolute', left: 20, right: 20, bottom: 0 },
  card: { paddingHorizontal: 18, borderRadius: 22, backgroundColor: 'rgba(255,255,255,0.07)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.1)' },
  cardRow: { height: 56, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  cardTitle: { fontSize: 16, fontWeight: '600', color: '#FFFFFF' },
  cardValue: { fontSize: 16, color: 'rgba(255,255,255,0.6)' },
  divider: { height: 1, backgroundColor: 'rgba(255,255,255,0.1)' },
});
