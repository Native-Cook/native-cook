import { BlurStyle, Canvas, PaintStyle, Picture, Skia, StrokeCap, TileMode, type SkCanvas } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The launch of an invented trip planner, Farside. In the dark a thin glowing blue arc — the rim
// of a planet just above the screen — sweeps down and away; "Every trip starts with a horizon"
// fades up word by word; a second rim rises from below and settles, breathing; then the planet
// swells up into a lit blue surface under a slanting beam of light, meteors streak down, and the
// real welcome — "Plan the trips beyond the map" — appears with its button. It all dims back to
// the dark before the first arc returns. Tap to send a meteor shower.
//
// Drawn in code: rims are huge circles stroked three times (wide blur, mid blur, sharp core) in
// #2E7BFF / #8EC5FF; the planet is a radial gradient; the beam a rotated linear gradient.
// Layout (393 x 852 pt): rims radius 250 pt (the planet grows to 2.2x); the first line at 42 % of the height; the risen rim's
// top at 62 %; the planet's top at 70 %; second heading 34 pt heavy at 16 %; a 58 pt button.
//
// autoPlay timeline (LOOP = 8.5 s):
//   0.0-1.4  the top rim sweeps from y = 40 pt down past the bottom (ease-in cubic)
//   0.9-1.9  "Every trip starts with a horizon" fades up, 0.12 s per word
//   1.5-2.5  the bottom rim rises to 62 % (ease-out cubic); 2.5-3.9 it breathes (glow +-20 %)
//   3.9-4.7  the line fades; the planet swells up to 70 % and fills with light (ease-in-out cubic);
//            the beam fades in
//   4.4-7.2  meteors (12, staggered, 0.9 s each, periodic); the heading and button fade up 4.6-5.2
//   7.2-7.9  everything dims to black
//   7.9-8.5  the top rim fades in at y = 40 pt, identical to t = 0
// Tapping fires a shower of meteors (light haptic).

/** Seconds. */
export const LOOP = 8.5;
const RIM = 250;
const BLUE = '#2E7BFF';
const ICE = '#8EC5FF';
const WORDS = ['Every', 'trip', 'starts', 'with', 'a', 'horizon'];

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function easeInCubic(x: number) {
  'worklet';
  return x * x * x;
}
function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** A glowing rim: the circle of radius RIM centred at (cx, cy), at `alpha`. */
function rim(c: SkCanvas, cx: number, cy: number, alpha: number, glow: number) {
  'worklet';
  if (alpha <= 0.001) return;
  const layers: [number, number, string, number][] = [
    [26, 22, BLUE, 0.55 * glow],
    [9, 6, BLUE, 0.9],
    [2.5, 0.8, ICE, 1],
  ];
  for (let i = 0; i < layers.length; i++) {
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(layers[i][0]);
    p.setColor(Skia.Color(layers[i][2]));
    p.setAlphaf(clamp01(alpha * layers[i][3]));
    p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, layers[i][1], true));
    c.drawCircle(cx, cy, RIM, p);
  }
}

function drawScene(c: SkCanvas, t: number, shower: number, width: number, height: number) {
  'worklet';
  const cx = width / 2;
  const dim = 1 - seg(t, 7.2, 7.9);
  // 1. The top rim: the bottom of a planet above, sweeping down.
  const fadeIn = seg(t, 7.9, 8.5);
  if (t < 1.4) {
    const y = 40 + (height + 120) * easeInCubic(seg(t, 0, 1.4));
    rim(c, cx, y - RIM, 1, 1);
  } else if (fadeIn > 0) rim(c, cx, 40 - RIM, fadeIn, 1);
  // 2. The bottom rim rising and breathing.
  const rise = easeOutCubic(seg(t, 1.5, 2.5));
  const swell = easeInOutCubic(seg(t, 3.9, 4.7));
  if (rise > 0 && dim > 0) {
    const top = height + 30 - (height + 30 - height * 0.62) * rise + (height * 0.7 - height * 0.62) * swell;
    const breathe = 1 + 0.2 * Math.sin(Math.max(0, t - 2.5) * Math.PI * 1.4) * (1 - swell);
    const r = RIM * (1 + 1.2 * swell);
    const pcy = top + r;
    // The lit planet.
    if (swell > 0) {
      const planet = Skia.Paint();
      planet.setAntiAlias(true);
      planet.setAlphaf(swell * dim);
      planet.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: top }, r * 0.55, [Skia.Color('#3D8BFF'), Skia.Color('#1A3FD6'), Skia.Color('#08155C')], [0, 0.45, 1], TileMode.Clamp));
      c.drawCircle(cx, pcy, r, planet);
      // The beam: a soft slanted band of light.
      const beam = Skia.Paint();
      beam.setAntiAlias(true);
      beam.setAlphaf(swell * dim * 0.85);
      beam.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 40, true));
      beam.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: height }, [Skia.Color('rgba(46,123,255,0)'), Skia.Color('rgba(46,123,255,0.55)')], null, TileMode.Clamp));
      c.save();
      c.rotate(-32, width, 0);
      c.drawRect(Skia.XYWHRect(width * 0.35, -100, width * 0.5, height * 1.4), beam);
      c.restore();
    }
    rim(c, cx, pcy, dim, breathe);
  }
  // 3. Meteors.
  const on = seg(t, 4.4, 4.8) * (1 - seg(t, 7.0, 7.4));
  const count = 12;
  for (let i = 0; i < count; i++) {
    const phase = ((t * 1.0 + (i * 0.9) / count * 3.1) % 0.9) / 0.9;
    let a = on;
    if (shower > 0) a = Math.max(a, shower);
    if (a <= 0.01) continue;
    const sx = width * (0.55 + 0.45 * ((i * 0.37) % 1)) + 60;
    const sy = height * (0.02 + 0.3 * ((i * 0.61) % 1)) - 40;
    const d = 260 * phase;
    const x = sx - d * 0.5;
    const y = sy + d * 0.87;
    const tail = 46 + 30 * ((i * 0.23) % 1);
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeCap(StrokeCap.Round);
    p.setStrokeWidth(2);
    p.setShader(Skia.Shader.MakeLinearGradient({ x: x + tail * 0.5, y: y - tail * 0.87 }, { x, y }, [Skia.Color('rgba(255,255,255,0)'), Skia.Color('#FFFFFF')], null, TileMode.Clamp));
    p.setAlphaf(a * Math.sin(Math.PI * phase) * dim);
    c.drawLine(x + tail * 0.5, y - tail * 0.87, x, y, p);
    const head = Skia.Paint();
    head.setAntiAlias(true);
    head.setColor(Skia.Color('#FFFFFF'));
    head.setAlphaf(a * Math.sin(Math.PI * phase) * dim);
    c.drawCircle(x, y, 3.2, head);
  }
}

function Word({ i, clock }: { i: number; clock: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const t = clock.get();
    const u = seg(t, 0.9 + i * 0.12, 1.4 + i * 0.12) * (1 - seg(t, 3.9, 4.3));
    return { opacity: u, transform: [{ translateY: 8 * (1 - u) }] };
  });
  return <Animated.Text style={[styles.word, style]}>{`${WORDS[i]} `}</Animated.Text>;
}

type Props = {
  /** Plays the launch by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function HorizonArcSplash({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const elapsed = useSharedValue(0);
  const clock = useSharedValue(autoPlay && !reduced ? 0 : 6);
  const showerAt = useSharedValue(-10);
  const shower = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    const x = now - showerAt.get();
    shower.set(x > 0 && x < 1.8 ? Math.sin((Math.PI * x) / 1.8) : 0);
    if (autoPlay) clock.set(now % LOOP);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawScene(canvas, clock.get(), shower.get(), width, height);
    return recorder.finishRecordingAsPicture();
  }, [width, height]);

  const pageStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const u = seg(t, 4.6, 5.2) * (1 - seg(t, 7.2, 7.7));
    return { opacity: u, transform: [{ translateY: 14 * (1 - u) }] };
  });

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const tap = Gesture.Tap()
    .enabled(!reduced)
    .onEnd(() => {
      showerAt.set(elapsed.get());
      scheduleOnRN(haptic);
    });

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={tap}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="Farside. Tap for a meteor shower.">
            <Picture picture={picture} />
          </Canvas>
          <View style={[styles.line, { top: height * 0.42 }]} pointerEvents="none">
            {WORDS.map((w, i) => (
              <Word key={w} i={i} clock={clock} />
            ))}
          </View>
          <Animated.View style={[styles.page, { top: insets.top + 60 }, pageStyle]} pointerEvents="none">
            <Text style={styles.brand}>farside</Text>
            <Text style={styles.heading}>Plan the trips{'\n'}beyond the map</Text>
            <Text style={styles.sub}>Routes, stays and the long way round, in one quiet place.</Text>
          </Animated.View>
          <Animated.View style={[styles.footer, { paddingBottom: insets.bottom + 14 }, pageStyle]} pointerEvents="none">
            <View style={styles.button}>
              <Text style={styles.action}>Start exploring</Text>
            </View>
            <Text style={styles.foot}>I already have an account</Text>
          </Animated.View>
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#000005' },
  line: { position: 'absolute', left: 24, right: 24, flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center' },
  word: { fontSize: 24, fontWeight: '500', color: '#FFFFFF', letterSpacing: -0.2 },
  page: { position: 'absolute', left: 24, right: 24 },
  brand: { fontSize: 15, fontWeight: '800', color: ICE, letterSpacing: 2, textTransform: 'uppercase' },
  heading: { marginTop: 12, fontSize: 38, lineHeight: 42, fontWeight: '900', color: '#FFFFFF', letterSpacing: -1.2 },
  sub: { marginTop: 10, fontSize: 16, lineHeight: 22, color: 'rgba(255,255,255,0.7)', maxWidth: 280 },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0, alignItems: 'center' },
  button: { alignSelf: 'stretch', height: 58, borderRadius: 29, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center' },
  action: { fontSize: 17, fontWeight: '700', color: '#000005' },
  foot: { marginTop: 14, fontSize: 15, fontWeight: '600', color: 'rgba(255,255,255,0.75)' },
});
