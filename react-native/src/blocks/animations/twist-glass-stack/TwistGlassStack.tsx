import { BlurMask, Canvas, Circle, Group, LinearGradient, Oval, Picture, RadialGradient, Rect, Skia, TileMode, PaintStyle, vec, type SkCanvas } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, Image as RNImage, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// An image generator's "thinking" screen under a large Instrument Serif "Dreaming up / your
// scene…", on a pale lilac gradient with a violet aura: a stack of five thick glass squircles, violet at the
// top fading to sky blue at the bottom, seen from slightly above. While it works the stack twists
// like a wrung cloth (each layer turned a little more than the one below), opens up, wobbles,
// then untwists past straight and settles. The whole stack turns a quarter turn per loop (a
// squircle looks the same after 90 degrees). Drag sideways to wring it yourself.
//
// A layer is the squircle |x|^4 + |z|^4 = 1 (half-width 104 pt, middle layers wider), turned by
// its angle round the vertical axis and drawn foreshortened (z * 0.36) as a 14 pt thick slab:
// the bottom outline filled darker, the top filled with a vertical gradient and a white rim.
//
// autoPlay timeline (LOOP = 8 s), t in seconds; twist T in radians between neighbouring layers:
//   0.0-0.8  rest: aligned, gap 38 pt
//   0.8-3.2  T 0 -> 0.42 (ease-in-out cubic), the gap opens to 52 pt
//   3.2-4.8  peak: T wobbles +-0.05 (one sine)
//   4.8-7.2  untwist: T 0.42 (1 - u)^3 cos(1.6 pi u), u = smoothstep -- past straight and back
//   7.2-8.0  rest, identical to t = 0
// The base angle is 90 deg * elapsed / LOOP, continuous across touches.
// Layout (393 x 852 pt): title Instrument Serif italic 50/54 pt 54 pt under the top; stack centred
// at 48 % of the height over a 220 pt violet aura; a frosted prompt card with a soft 60 pt
// thumbnail of the draft (assets/harbour.jpg, generated, blurred 6) and the progress glint.

/** Seconds. */
export const LOOP = 8;
const LAYERS = 5;
const PEAK = 0.42;
const HALF = 104;
const THICK = 14;
const FORE = 0.36;
const POINTS = 72;
const HOLD = 2;
const RETURN = 0.8;

const SERIF = require('./assets/InstrumentSerif-Italic.ttf');
const SERIF_FAMILY = 'TwistGlassStack-InstrumentSerifItalic';
const HARBOUR = require('./assets/harbour.jpg');

const C = {
  background: '#ECECF1',
  top: '#F5F2FF',
  bottom: '#DCE2F6',
  aura: 'rgba(140,110,255,0.22)',
  ink: '#17161F',
  soft: '#73717F',
  card: '#FFFFFF',
  track: '#E2E1EA',
  bar: '#7C5CFF',
};
// Top to bottom.
const TOPS = ['#B49BFF', '#A7A2FF', '#A2B3FF', '#9CC6FF', '#A6DBFF'];
const BODIES = ['#7C4DFF', '#7F68FF', '#7D86FF', '#6FA2FF', '#6FC0FF'];
const WIDTHS = [0.86, 1.0, 1.06, 0.98, 0.84];

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
function smooth(x: number) {
  'worklet';
  const u = clamp01(x);
  return u * u * (3 - 2 * u);
}

/** The twist between neighbouring layers, `t` seconds into the loop. */
function twistAt(t: number) {
  'worklet';
  if (t < 4.8) return PEAK * easeInOutCubic(seg(t, 0.8, 3.2)) + 0.05 * Math.sin(Math.PI * 2 * seg(t, 3.2, 4.8));
  const u = smooth(seg(t, 4.8, 7.2));
  return PEAK * Math.pow(1 - u, 3) * Math.cos(1.6 * Math.PI * u);
}

/** The outline of a squircle layer turned by `angle`, centred on (cx, cy). */
function layerPath(cx: number, cy: number, half: number, angle: number) {
  'worklet';
  const p = Skia.Path.Make();
  const ca = Math.cos(angle);
  const sa = Math.sin(angle);
  for (let i = 0; i < POINTS; i++) {
    const t = (i / POINTS) * Math.PI * 2;
    const c = Math.cos(t);
    const s = Math.sin(t);
    const x = Math.sign(c) * Math.sqrt(Math.abs(c)) * half;
    const z = Math.sign(s) * Math.sqrt(Math.abs(s)) * half;
    const xr = x * ca - z * sa;
    const zr = x * sa + z * ca;
    const px = cx + xr;
    const py = cy + zr * FORE;
    if (i === 0) p.moveTo(px, py);
    else p.lineTo(px, py);
  }
  p.close();
  return p;
}

function drawStack(c: SkCanvas, cx: number, cy: number, base: number, twist: number) {
  'worklet';
  const open = Math.min(1, Math.abs(twist) / PEAK);
  const gap = 38 + 14 * open;
  // Bottom layer first, so the glass above lies over it.
  for (let i = LAYERS - 1; i >= 0; i--) {
    const y = cy + (i - (LAYERS - 1) / 2) * gap;
    const half = HALF * WIDTHS[i] * (1 + 0.04 * open * Math.sin(i * 1.7));
    const angle = base + twist * (i - (LAYERS - 1) / 2);
    const depth = half * FORE;

    const body = Skia.Paint();
    body.setAntiAlias(true);
    body.setColor(Skia.Color(BODIES[i]));
    body.setAlphaf(0.78);
    c.drawPath(layerPath(cx, y + THICK, half, angle), body);

    const top = Skia.Paint();
    top.setAntiAlias(true);
    top.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: cx, y: y - depth * 1.2 },
        { x: cx, y: y + depth * 1.2 },
        [Skia.Color('#FFFFFF'), Skia.Color(TOPS[i]), Skia.Color(BODIES[i])],
        [0, 0.45, 1],
        TileMode.Clamp,
      ),
    );
    top.setAlphaf(0.86);
    const face = layerPath(cx, y, half, angle);
    c.drawPath(face, top);

    const rim = Skia.Paint();
    rim.setAntiAlias(true);
    rim.setStyle(PaintStyle.Stroke);
    rim.setStrokeWidth(1.4);
    rim.setColor(Skia.Color('#FFFFFF'));
    rim.setAlphaf(0.75);
    c.drawPath(face, rim);
  }
}

type Props = {
  brand?: string;
  title?: string;
  prompt?: string;
  action?: string;
  /** Twists the stack by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function TwistGlassStack({
  brand = 'Atelier',
  title = 'Dreaming up\nyour scene…',
  prompt = 'A quiet harbour at dawn, soft film grain, two boats',
  action = 'Cancel',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [SERIF_FAMILY]: SERIF });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.48;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const time = useSharedValue(0);
  const twist = useSharedValue(0);
  const touching = useSharedValue(false);
  const manual = useSharedValue(false);
  const released = useSharedValue(-1);
  const userTwist = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    if (touching.get()) {
      twist.set(userTwist.get());
      return;
    }
    if (manual.get()) {
      const since = now - released.get();
      if (since < HOLD) twist.set(userTwist.get());
      else if (since < HOLD + RETURN) twist.set(userTwist.get() * (1 - easeInOutCubic((since - HOLD) / RETURN)));
      else {
        twist.set(0);
        manual.set(false);
        origin.set(now);
      }
      return;
    }
    const t = autoPlay ? (now - origin.get()) % LOOP : 0;
    time.set(t);
    twist.set(autoPlay ? twistAt(t) : 0);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const base = autoPlay ? (Math.PI / 2) * (elapsed.get() / LOOP) : 0;
    drawStack(canvas, cx, cy, base, twist.get());
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, autoPlay]);

  // The progress shimmer: a highlight crossing the bar once per loop.
  const shimmer = useAnimatedStyle(() => ({
    transform: [{ translateX: -120 + (width - 48 - 28 + 240) * (time.get() / LOOP) }],
  }));

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin(() => {
      touching.set(true);
      manual.set(true);
      userTwist.set(twist.get());
      scheduleOnRN(haptic);
    })
    .onUpdate((e) => {
      userTwist.set(Math.max(-0.7, Math.min(0.7, e.translationX / 260)));
    })
    .onFinalize(() => {
      touching.set(false);
      released.set(elapsed.get());
    });

  if (!fontsLoaded) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="Glass layers twisting while the image is generated. Drag to twist them.">
            <Rect x={0} y={0} width={width} height={height}>
              <LinearGradient start={vec(0, 0)} end={vec(0, height)} colors={[C.top, C.bottom]} />
            </Rect>
            <Circle cx={cx} cy={cy} r={220}>
              <RadialGradient c={vec(cx, cy)} r={220} colors={[C.aura, 'rgba(140,110,255,0)']} />
            </Circle>
            <Group opacity={0.18}>
              <Oval x={cx - 130} y={cy + 2 * 52 + 40} width={260} height={40} color="#3B2A8C">
                <BlurMask blur={18} style="normal" />
              </Oval>
            </Group>
            <Picture picture={picture} />
          </Canvas>
          <View style={[styles.header, { top: insets.top + 10 }]} pointerEvents="none">
            <Text style={styles.brand}>{brand}</Text>
            <View style={styles.badge}>
              <Text style={styles.badgeText}>AI</Text>
            </View>
          </View>
          <Text style={[styles.title, { top: insets.top + 54 }]} pointerEvents="none">
            {title}
          </Text>
        </View>
      </GestureDetector>

      <View style={[styles.bottom, { paddingBottom: insets.bottom + 16 }]}>
        <View style={styles.card}>
          <View style={styles.cardRow}>
            {/* The draft so far: the harbour, still soft. */}
            <RNImage source={HARBOUR} style={styles.thumb} blurRadius={6} />
            <View style={styles.cardText}>
              <Text style={styles.cardLabel}>PROMPT · 62%</Text>
              <Text style={styles.prompt} numberOfLines={2}>
                {prompt}
              </Text>
            </View>
          </View>
          <View style={styles.track}>
            <Animated.View style={[styles.glint, shimmer]} />
          </View>
        </View>
        <View style={styles.button} accessible accessibilityRole="button">
          <Text style={styles.action}>{action}</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.background },
  header: { position: 'absolute', left: 24, right: 24, height: 30, flexDirection: 'row', alignItems: 'center' },
  brand: { fontSize: 20, fontWeight: '700', color: C.ink, letterSpacing: -0.3 },
  badge: { marginLeft: 8, paddingHorizontal: 8, height: 22, borderRadius: 11, backgroundColor: C.ink, justifyContent: 'center' },
  badgeText: { fontSize: 11, fontWeight: '700', color: '#FFFFFF', letterSpacing: 1 },
  bottom: { position: 'absolute', left: 24, right: 24, bottom: 0 },
  title: { position: 'absolute', left: 24, right: 24, fontFamily: SERIF_FAMILY, fontSize: 50, lineHeight: 54, color: C.ink, letterSpacing: -0.5 },
  card: { padding: 14, borderRadius: 24, backgroundColor: 'rgba(255,255,255,0.85)', shadowColor: '#3B2A8C', shadowOpacity: 0.12, shadowRadius: 20, shadowOffset: { width: 0, height: 8 } },
  cardRow: { flexDirection: 'row', alignItems: 'center' },
  thumb: { width: 60, height: 60, borderRadius: 14 },
  cardText: { flex: 1, marginLeft: 12 },
  cardLabel: { fontSize: 11, fontWeight: '700', letterSpacing: 1.5, color: C.soft },
  prompt: { marginTop: 4, fontSize: 15, lineHeight: 20, color: C.ink },
  track: { marginTop: 14, height: 6, borderRadius: 3, backgroundColor: C.track, overflow: 'hidden' },
  glint: { width: 120, height: 6, borderRadius: 3, backgroundColor: C.bar },
  button: { marginTop: 14, height: 54, borderRadius: 27, borderWidth: 1.5, borderColor: '#CFCDD9', alignItems: 'center', justifyContent: 'center' },
  action: { fontSize: 17, fontWeight: '600', color: C.ink },
});
