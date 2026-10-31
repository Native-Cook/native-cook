import { BackdropBlur, BlurMask, Canvas, Circle, Group, LinearGradient, Path, RadialGradient, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The launch card of an invented running app, Stride. A dark frosted-glass app icon floats in the
// night; then colour floods into it from its lower-left corner, a runner draws himself on it in one
// stroke, his head pops in, and a blue glow blooms round the icon; a sheen slides over the glass,
// and later it all drains back to clear glass. Tap the icon to light it again.
//
// Drawn in code: a 200 pt rounded square (radius 50) — a backdrop blur over two soft blobs for the
// glass, a gradient disc from the corner (clipped to the icon) for the colour, a stroked path with
// a trimmed end for the runner, a circle for his head.
// Layout (393 x 852 pt): heading 34 pt heavy and the app's name at 22 %; icon centred at 47 %;
// rating, button and a note at the bottom.
//
// autoPlay timeline (LOOP = 7 s):
//   0.0-1.2  rest: clear glass
//   1.2-2.6  colour floods from the corner (radius 0 -> 300 pt, ease-in-out cubic)
//   1.8-3.0  the runner draws in (trim 0 -> 1, ease-in-out cubic); 2.9-3.3 his head pops (ease-out back)
//   2.4-3.4  the glow blooms
//   4.2-5.0  a sheen slides across
//   5.8-6.6  colour, runner and glow drain away (ease-in-out cubic)
//   6.6-7.0  rest, identical to t = 0
// Tapping the icon lights it again from the start of the flood (light haptic).

/** Seconds. */
export const LOOP = 7;
const ICON = 200;

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
function easeOutBack(x: number) {
  'worklet';
  const k = 1.8;
  return 1 + (k + 1) * Math.pow(x - 1, 3) + k * Math.pow(x - 1, 2);
}

type Props = {
  /** Lights the icon by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function GlassIconReveal({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.47;
  const x0 = cx - ICON / 2;
  const y0 = cy - ICON / 2;
  const icon = useMemo(() => Skia.RRectXY(Skia.XYWHRect(x0, y0, ICON, ICON), 50, 50), [x0, y0]);
  const iconPath = useMemo(() => {
    const p = Skia.Path.Make();
    p.addRRect(icon);
    return p;
  }, [icon]);
  // The runner: one stroke from the trailing arm, over the shoulders, down the body to the legs.
  const runner = useMemo(() => {
    const p = Skia.Path.MakeFromSVGString('M -52 -30 C -30 -40 0 -42 18 -30 C 34 -18 14 0 -4 6 C 14 14 34 26 44 46 M -4 6 C -18 22 -36 34 -54 34');
    const m = Skia.Matrix();
    m.translate(cx - 4, cy + 4);
    p?.transform(m);
    return p ?? Skia.Path.Make();
  }, [cx, cy]);

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const clock = useSharedValue(autoPlay && !reduced ? 0 : 4);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    if (autoPlay) clock.set((((elapsed.get() - origin.get()) % LOOP) + LOOP) % LOOP);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const drain = useDerivedValue(() => 1 - easeInOutCubic(seg(clock.get(), 5.8, 6.6)));
  const flood = useDerivedValue(() => 300 * easeInOutCubic(seg(clock.get(), 1.2, 2.6)) * drain.get());
  const floodOpacity = useDerivedValue(() => drain.get());
  const trim = useDerivedValue(() => easeInOutCubic(seg(clock.get(), 1.8, 3.0)));
  const runnerOpacity = useDerivedValue(() => drain.get());
  const head = useDerivedValue(() => 11 * Math.max(0, easeOutBack(seg(clock.get(), 2.9, 3.3))) * drain.get());
  const glow = useDerivedValue(() => easeInOutCubic(seg(clock.get(), 2.4, 3.4)) * drain.get());
  const glowR = useDerivedValue(() => ICON * (0.6 + 0.35 * glow.get()));
  const sheenX = useDerivedValue(() => x0 - 120 + (ICON + 240) * seg(clock.get(), 4.2, 5.0));
  const sheenOpacity = useDerivedValue(() => {
    const u = seg(clock.get(), 4.2, 5.0);
    return u > 0 && u < 1 ? 0.5 : 0;
  });

  const nameStyle = useAnimatedStyle(() => ({ opacity: 0.55 + 0.45 * glow.get() }));

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const tap = Gesture.Tap()
    .enabled(!reduced && autoPlay)
    .onEnd(() => {
      origin.set(elapsed.get() - 1.2);
      scheduleOnRN(haptic);
    });

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        {/* Glow behind the icon. */}
        <Circle cx={cx} cy={cy} r={glowR} opacity={glow}>
          <RadialGradient c={vec(cx, cy)} r={ICON} colors={['rgba(80,120,255,0.75)', 'rgba(80,120,255,0)']} />
        </Circle>
        {/* Soft shapes the glass blurs. */}
        <Group>
          <BlurMask blur={30} style="normal" />
          <Circle cx={x0 + 30} cy={y0 + ICON - 20} r={60} color="rgba(90,110,200,0.35)" />
          <Circle cx={x0 + ICON - 30} cy={y0 + 30} r={50} color="rgba(120,140,180,0.25)" />
        </Group>
        <BackdropBlur blur={16} clip={icon}>
          <RoundedRect x={x0} y={y0} width={ICON} height={ICON} r={50}>
            <LinearGradient start={vec(x0, y0)} end={vec(x0 + ICON, y0 + ICON)} colors={['rgba(120,140,170,0.32)', 'rgba(60,75,110,0.4)']} />
          </RoundedRect>
        </BackdropBlur>
        {/* Colour flooding in from the lower-left corner. */}
        <Group clip={iconPath} opacity={floodOpacity}>
          <Circle cx={x0} cy={y0 + ICON} r={flood}>
            <LinearGradient start={vec(x0, y0 + ICON)} end={vec(x0 + ICON, y0)} colors={['#6A5CFF', '#5B8CFF', '#62C6FF']} />
            <BlurMask blur={6} style="normal" />
          </Circle>
          <RoundedRect x={sheenX} y={y0 - 40} width={50} height={ICON + 80} r={0} color="rgba(255,255,255,0.9)" opacity={sheenOpacity} transform={[{ rotate: 0.35 }]} origin={vec(cx, cy)}>
            <BlurMask blur={14} style="normal" />
          </RoundedRect>
        </Group>
        {/* The runner. */}
        <Group opacity={runnerOpacity}>
          <Path path={runner} style="stroke" strokeWidth={17} strokeCap="round" strokeJoin="round" color="rgba(255,255,255,0.95)" start={0} end={trim} />
          <Circle cx={cx + 42} cy={cy - 52} r={head} color="#FFFFFF" />
        </Group>
        {/* Rim. */}
        <RoundedRect x={x0 + 0.75} y={y0 + 0.75} width={ICON - 1.5} height={ICON - 1.5} r={49} style="stroke" strokeWidth={1.5}>
          <LinearGradient start={vec(x0, y0)} end={vec(x0 + ICON, y0 + ICON)} colors={['rgba(255,255,255,0.55)', 'rgba(255,255,255,0.08)', 'rgba(160,190,255,0.6)']} />
        </RoundedRect>
      </Canvas>

      <View style={[styles.head, { top: height * 0.17 }]} pointerEvents="none">
        <Text style={styles.title}>Now available</Text>
        <Animated.Text style={[styles.name, nameStyle]}>Stride · Running coach</Animated.Text>
      </View>
      <GestureDetector gesture={tap}>
        <View style={[styles.hit, { left: x0, top: y0, width: ICON, height: ICON }]} accessible accessibilityRole="button" accessibilityLabel="Stride app icon. Tap to light it." />
      </GestureDetector>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]} pointerEvents="none">
        <Text style={styles.rating}>★★★★★  4.9 · 12k ratings</Text>
        <View style={styles.button}>
          <Text style={styles.action}>Get it free</Text>
        </View>
        <Text style={styles.note}>Couch to 5K in eight weeks, at your own pace.</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#05070C' },
  head: { position: 'absolute', left: 24, right: 24, alignItems: 'center' },
  title: { fontSize: 34, fontWeight: '900', color: '#FFFFFF', letterSpacing: -1 },
  name: { marginTop: 6, fontSize: 17, fontWeight: '700', color: '#FFFFFF' },
  hit: { position: 'absolute' },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0, alignItems: 'center' },
  rating: { fontSize: 15, fontWeight: '700', color: 'rgba(255,255,255,0.75)', marginBottom: 16 },
  button: { alignSelf: 'stretch', height: 58, borderRadius: 29, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center' },
  action: { fontSize: 17, fontWeight: '700', color: '#05070C' },
  note: { marginTop: 12, fontSize: 14, color: 'rgba(255,255,255,0.55)' },
});
