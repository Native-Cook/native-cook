import { BlurStyle, Canvas, PaintStyle, Picture, Skia, StrokeCap, TileMode, type SkCanvas } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The launch of an invented streak app, Ember. The whole screen is warm orange with a little face
// in the middle — that orange is the mascot, too close to see: it shrinks back from the edges of
// the screen into a small flame with a round body and three flickering tips, blinks and looks
// about, then rises as the welcome page grows in under a soft aurora: "Welcome to Ember", two
// ticks and Continue. Later the flame sinks back to the middle and swells until it fills the
// screen again. Tap the flame to make it hop.
//
// The flame is one path in a unit box (body radius 0.5, tips up to -0.8) drawn with a vertical
// gradient and a warm inner glow; its tips flicker with sines periodic in the loop. It is scaled
// about its face, so at 3000 pt it covers the screen.
// Layout (393 x 852 pt): face at 50 % of the height; the small flame 150 pt; on the welcome page
// 190 pt at 34 %; heading 30 pt heavy at 54 %; two check rows and a 58 pt button at the bottom.
//
// autoPlay timeline (LOOP = 8 s):
//   0.0-1.0  rest: the screen is orange; a blink at 0.55 s
//   1.0-1.8  the flame shrinks 3000 -> 150 pt (log scale, ease-in-out cubic)
//   1.8-3.0  it flickers, looks left and right, blinks
//   3.0-3.6  it rises to 34 % and grows to 190 pt (ease-out back); the aurora and the page fade up
//            (the rows 0.08 s apart)
//   3.6-6.5  the welcome page
//   6.5-7.0  the page fades, the flame sinks back to 50 % and 150 pt
//   7.0-7.8  it swells 150 -> 3000 pt (log scale, ease-in cubic) and covers the screen
//   7.8-8.0  rest, identical to t = 0
// Tapping the flame makes it hop (light haptic).

/** Seconds. */
export const LOOP = 8;
const BIG = 3000;
const SMALL = 150;
const HERO = 190;
/** The face's point in the flame's unit box. */
const FACE_Y = 0.12;

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
  const k = 1.5;
  return 1 + (k + 1) * Math.pow(x - 1, 3) + k * Math.pow(x - 1, 2);
}
function logLerp(a: number, b: number, u: number) {
  'worklet';
  return Math.exp(Math.log(a) + (Math.log(b) - Math.log(a)) * u);
}

/** Flame size, its face's y (fraction of height), page amount, blink and gaze `t` s into the loop. */
function stageAt(t: number) {
  'worklet';
  let size = BIG;
  let y = 0.5;
  if (t >= 1.0 && t < 3.0) size = logLerp(BIG, SMALL, easeInOutCubic(seg(t, 1.0, 1.8)));
  else if (t >= 3.0 && t < 6.5) {
    const u = easeOutBack(seg(t, 3.0, 3.6));
    size = SMALL + (HERO - SMALL) * u;
    y = 0.5 + (0.34 - 0.5) * u;
  } else if (t >= 6.5 && t < 7.0) {
    const u = easeInOutCubic(seg(t, 6.5, 7.0));
    size = HERO + (SMALL - HERO) * u;
    y = 0.34 + (0.5 - 0.34) * u;
  } else if (t >= 7.0) size = logLerp(SMALL, BIG, Math.pow(seg(t, 7.0, 7.8), 3));
  const page = seg(t, 3.0, 3.6) * (1 - seg(t, 6.5, 6.8));
  const blinkAt = [0.55, 2.4, 4.8];
  let blink = 0;
  for (let i = 0; i < blinkAt.length; i++) {
    const x = t - blinkAt[i];
    if (x > 0 && x < 0.16) blink = Math.sin((Math.PI * x) / 0.16);
  }
  const gaze = Math.sin(Math.PI * 2 * seg(t, 1.9, 3.0)) * 0.6 + Math.sin(Math.PI * 2 * seg(t, 4.0, 5.6)) * 0.4;
  return { size, y, page, blink, gaze };
}

function flamePath(t: number) {
  'worklet';
  const w = (Math.PI * 2) / LOOP;
  const a = 0.05 * Math.sin(5 * w * t);
  const b = 0.06 * Math.sin(7 * w * t + 1.3);
  const c = 0.05 * Math.sin(6 * w * t + 2.1);
  const sway = 0.04 * Math.sin(4 * w * t);
  const p = Skia.Path.Make();
  p.moveTo(0, 0.66);
  p.cubicTo(-0.36, 0.66, -0.53, 0.4, -0.5, 0.1);
  p.cubicTo(-0.48, -0.1, -0.42, -0.24, -0.34 + sway, -0.4 + a);
  p.cubicTo(-0.26, -0.28, -0.18, -0.22, -0.13, -0.24);
  p.cubicTo(-0.12, -0.46, -0.02, -0.64, 0.08 + sway, -0.82 + b);
  p.cubicTo(0.12, -0.62, 0.2, -0.5, 0.31 + sway, -0.5 + c);
  p.cubicTo(0.44, -0.34, 0.53, -0.12, 0.5, 0.1);
  p.cubicTo(0.53, 0.4, 0.36, 0.66, 0, 0.66);
  p.close();
  return p;
}

function drawScene(c: SkCanvas, t: number, hop: number, width: number, height: number) {
  'worklet';
  const st = stageAt(t);
  const cx = width / 2;
  const fy = height * st.y - hop;
  // Aurora behind the welcome page.
  if (st.page > 0) {
    const blobs: [number, number, number, string][] = [
      [0.15, 0.05, 190, 'rgba(255,190,140,0.9)'],
      [0.75, 0.02, 210, 'rgba(214,150,255,0.85)'],
      [0.95, 0.3, 170, 'rgba(160,205,255,0.85)'],
      [0.1, 0.35, 140, 'rgba(255,220,180,0.6)'],
    ];
    for (let i = 0; i < blobs.length; i++) {
      const p = Skia.Paint();
      p.setAntiAlias(true);
      p.setColor(Skia.Color(blobs[i][3]));
      p.setAlphaf(st.page * 0.8);
      p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 70, true));
      c.drawCircle(width * blobs[i][0], height * blobs[i][1], blobs[i][2], p);
    }
  }
  // Flame, scaled about its face.
  const s = st.size;
  c.save();
  c.translate(cx, fy);
  c.scale(s, s);
  c.translate(0, -FACE_Y);
  const path = flamePath(t);
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0.66 }, { x: 0, y: -0.82 }, [Skia.Color('#FF9B22'), Skia.Color('#FF7A12'), Skia.Color('#FF4E12')], [0, 0.55, 1], TileMode.Clamp));
  c.drawPath(path, fill);
  const glow = Skia.Paint();
  glow.setAntiAlias(true);
  glow.setShader(Skia.Shader.MakeRadialGradient({ x: 0, y: 0.32 }, 0.42, [Skia.Color('rgba(255,214,90,0.75)'), Skia.Color('rgba(255,214,90,0)')], null, TileMode.Clamp));
  c.save();
  c.clipPath(path, 1, true);
  c.drawCircle(0, 0.32, 0.42, glow);
  c.restore();
  c.restore();

  // Face: kept a constant size on screen, so it reads both on the full screen and on the flame.
  const k = Math.min(1, s / SMALL) * (st.page > 0 ? 1 + 0.2 * st.page : 1);
  const eyeR = 9 * k;
  const gx = st.gaze * 3 * k;
  for (let side = -1; side <= 1; side += 2) {
    const ex = cx + side * 22 * k;
    const ey = fy - 3 * k;
    const white = Skia.Paint();
    white.setAntiAlias(true);
    white.setColor(Skia.Color('#FFFFFF'));
    const h = eyeR * (1 - 0.9 * st.blink);
    c.drawOval(Skia.XYWHRect(ex - eyeR, ey - h, eyeR * 2, h * 2), white);
    if (st.blink < 0.6) {
      const pupil = Skia.Paint();
      pupil.setAntiAlias(true);
      pupil.setColor(Skia.Color('#1A0E08'));
      c.drawCircle(ex + gx + side * 1.2 * k, ey + 0.5 * k, 5.2 * k * (1 - st.blink), pupil);
    }
  }
  const smile = Skia.Paint();
  smile.setAntiAlias(true);
  smile.setStyle(PaintStyle.Stroke);
  smile.setStrokeWidth(3.2 * k);
  smile.setStrokeCap(StrokeCap.Round);
  smile.setColor(Skia.Color('#1A0E08'));
  const mouth = Skia.Path.Make();
  mouth.addArc(Skia.XYWHRect(cx - 9 * k, fy - 2 * k, 18 * k, 14 * k), 25, 130);
  c.drawPath(mouth, smile);
}

type Props = {
  /** Plays the launch by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function FlameMascotSplash({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const elapsed = useSharedValue(0);
  // Reduced motion (and no autoPlay) shows the welcome page.
  const clock = useSharedValue(autoPlay && !reduced ? 0 : 4.5);
  const hopAt = useSharedValue(-10);
  const hop = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    const x = now - hopAt.get();
    hop.set(x > 0 && x < 0.5 ? 26 * Math.sin((Math.PI * x) / 0.5) : 0);
    if (autoPlay) clock.set(now % LOOP);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawScene(canvas, clock.get(), hop.get(), width, height);
    return recorder.finishRecordingAsPicture();
  }, [width, height]);

  const page = useDerivedValue(() => stageAt(clock.get()).page);
  // Each style reads the shared value itself so that it is tracked.
  const rowStyle = (p: number, delay: number) => {
    'worklet';
    const u = clamp01((p - delay) / (1 - delay));
    return { opacity: u, transform: [{ translateY: 18 * (1 - u) }] };
  };
  const headStyle = useAnimatedStyle(() => rowStyle(page.get(), 0));
  const row1Style = useAnimatedStyle(() => rowStyle(page.get(), 0.13));
  const row2Style = useAnimatedStyle(() => rowStyle(page.get(), 0.26));
  const buttonStyle = useAnimatedStyle(() => rowStyle(page.get(), 0.39));

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const tap = Gesture.Tap()
    .enabled(!reduced)
    .onEnd(() => {
      hopAt.set(elapsed.get());
      scheduleOnRN(haptic);
    });

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <GestureDetector gesture={tap}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="Ember, a little flame. Tap to make it hop.">
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>
      <Animated.View style={[styles.welcome, { top: height * 0.54 }, headStyle]} pointerEvents="none">
        <Text style={styles.heading}>Welcome to Ember</Text>
        <Text style={styles.sub}>Your streaks, kept warm. One small habit a day is enough to keep the fire going.</Text>
      </Animated.View>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]} pointerEvents="none">
        <Animated.View style={[styles.check, row1Style]}>
          <View style={styles.box}>
            <Text style={styles.tick}>✓</Text>
          </View>
          <Text style={styles.checkText}>I agree to the terms and the privacy notice</Text>
        </Animated.View>
        <Animated.View style={[styles.check, row2Style]}>
          <View style={styles.box}>
            <Text style={styles.tick}>✓</Text>
          </View>
          <Text style={styles.checkText}>Send me a gentle nudge at 8 pm</Text>
        </Animated.View>
        <Animated.View style={[styles.button, buttonStyle]}>
          <Text style={styles.action}>Continue</Text>
        </Animated.View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F3F4F8' },
  welcome: { position: 'absolute', left: 32, right: 32, alignItems: 'center' },
  heading: { fontSize: 30, fontWeight: '900', color: '#16141A', letterSpacing: -0.8, textAlign: 'center' },
  sub: { marginTop: 10, fontSize: 16, lineHeight: 22, color: 'rgba(22,20,26,0.6)', textAlign: 'center' },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0 },
  check: { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 12 },
  box: { width: 24, height: 24, borderRadius: 7, backgroundColor: '#FF7A12', alignItems: 'center', justifyContent: 'center' },
  tick: { fontSize: 14, fontWeight: '900', color: '#FFFFFF' },
  checkText: { flex: 1, fontSize: 15, color: 'rgba(22,20,26,0.75)' },
  button: { marginTop: 8, height: 58, borderRadius: 29, backgroundColor: '#16141A', alignItems: 'center', justifyContent: 'center' },
  action: { fontSize: 17, fontWeight: '700', color: '#FFFFFF' },
});
