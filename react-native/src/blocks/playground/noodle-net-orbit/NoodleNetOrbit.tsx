import { BlurStyle, Canvas, PaintStyle, Picture, RadialGradient, Rect, Skia, StrokeCap, StrokeJoin, TileMode, vec, type SkCanvas } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// An evening breathing session, unwind, on warm black: one thin cream circle and a glowing red
// bead. The bead rolls round the inside of the rim; breathing in, it presses the rim in front of
// it into a band of woven noodles — the band's inner edge dents inwards round the bead, its outer
// edge bulges a little out, and wavy strands along it cross gently bent strands across it — while
// an amber warmth swells inside the circle; holding keeps the weave taut with a slight tremble;
// breathing out lets the rim spring back. The phase is set huge in the middle in Instrument Serif. Drag the bead anywhere inside
// the circle to pull the net yourself.
//
// Layout (393 x 852 pt): warm black #0D0B0A with a #1E1915 radial lift; top row (wordmark in
// Instrument Serif italic 28 pt, a 4·7·8 pill); "ROUND 2 OF 4" 150 pt above the centre; circle
// radius min(0.42 w, 166) pt centred at 47 % of the height; phase word Instrument Serif italic
// 56 pt in the middle; four round markers and the session line under the circle; outline button.
//
// autoPlay timeline (LOOP = 8 s), t in seconds; the bead's angle runs once round the circle
// (from the top, clockwise) over 0.6-7.2 with ease-in-out sine:
//   0.0-0.6  rest: bead just inside the rim at the top, no band, "Inhale"
//   0.6-3.0  breathe in: press 0 -> 1 (ease-in-out cubic), the weave opens, the warmth swells
//   3.0-4.6  hold: press 1 with a 3 pt tremble, "Hold"
//   4.6-7.2  breathe out: press 1 -> 0 (ease-in-out cubic), "Exhale"
//   7.2-8.0  rest, identical to t = 0
// The phase words cross-fade over 0.3 s at 3.0, 4.6 and 7.2 (rising 8 pt as they come in).
// The band spans 2 x (0.55 + 0.25 press) rad round the bead; at s along it (0-1), with
// env = sin^2(pi s), its inner edge is at R - 64 press env and its outer edge at R + 12 press env;
// the bead sits 9 pt inside the inner edge. Five strands run along it at q = k / 5 +
// 0.17 sin(3 pi s + 1.7 k + P) and eight across it at s = (j + 0.5) / 8 + 0.035 sin(2 pi q + 1.1 j + P),
// where P = 4 pi t / LOOP (the waves turn twice per loop). They fade in over press 0-0.25.
// Dragging: the bead follows the finger (angle, and press from its distance to the rim, up to
// 1.15), light haptic on
// touch-down; 2 s after release it eases back to the top over 0.8 s and the loop restarts.

/** Seconds. */
export const LOOP = 8;
/** How far the band's inner edge is pushed in at full breath, and its outer edge bulges out. */
const INWARD = 64;
const BULGE = 12;
/** Gap between the bead and the band's inner edge. */
const BEAD_GAP = 9;
const MAX_PRESS = 1.15;
const ALONG = 6;
const ACROSS = 8;
const HOLD = 2;
const RETURN = 0.8;
const REST_ANGLE = -Math.PI / 2;

const C = {
  background: '#0D0B0A',
  lift: '#1E1915',
  cream: '#F3EDE4',
  dim: 'rgba(243,237,228,0.5)',
  faint: 'rgba(243,237,228,0.18)',
  bead: '#FF3B2F',
  warm: '#FF7A3D',
};

const SERIF = require('./assets/InstrumentSerif-Regular.ttf');
const SERIF_ITALIC = require('./assets/InstrumentSerif-Italic.ttf');
const SERIF_FAMILY = 'NoodleNetOrbit-InstrumentSerif';
const ITALIC_FAMILY = 'NoodleNetOrbit-InstrumentSerifItalic';

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
function easeInOutSine(x: number) {
  'worklet';
  return -(Math.cos(Math.PI * x) - 1) / 2;
}
function smooth(x: number) {
  'worklet';
  const u = clamp01(x);
  return u * u * (3 - 2 * u);
}

/** The bead's angle (radians) and how hard it presses the band (0-1), `t` seconds into the loop. */
function beadAt(t: number) {
  'worklet';
  const angle = REST_ANGLE + Math.PI * 2 * easeInOutSine(seg(t, 0.6, 7.2));
  const inhale = easeInOutCubic(seg(t, 0.6, 3.0));
  const exhale = easeInOutCubic(seg(t, 4.6, 7.2));
  const tremble = Math.sin(seg(t, 3.0, 4.6) * Math.PI) * Math.sin(t * 19) * 3;
  return { angle, press: inhale * (1 - exhale) + tremble / INWARD };
}

/** The opacities of the three phase words. */
function labelsAt(t: number) {
  'worklet';
  const toHold = smooth((t - 3.0) / 0.3);
  const toOut = smooth((t - 4.6) / 0.3);
  const toIn = smooth((t - 7.2) / 0.3);
  return { inhale: 1 - toHold + toIn, hold: toHold * (1 - toOut), exhale: toOut * (1 - toIn) };
}

/** A point of the band: s along it (0-1), q across it (0 inner edge, 1 outer edge). */
function bandPoint(cx: number, cy: number, R: number, angle: number, half: number, inward: number, bulge: number, s: number, q: number) {
  'worklet';
  const env = Math.pow(Math.sin(Math.PI * Math.min(1, Math.max(0, s))), 2);
  const inner = R - inward * env;
  const outer = R + bulge * env;
  const phi = angle - half + 2 * half * s;
  const r = inner + (outer - inner) * q;
  return [cx + Math.cos(phi) * r, cy + Math.sin(phi) * r];
}

function drawNet(c: SkCanvas, cx: number, cy: number, R: number, angle: number, press: number, phase: number) {
  'worklet';
  const p = Math.max(0, press);
  const half = 0.55 + 0.25 * Math.min(1, p);
  const inward = INWARD * p;
  const bulge = BULGE * p;

  // The warmth inside the circle, swelling with the breath.
  const warm = Skia.Paint();
  warm.setAntiAlias(true);
  warm.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: cx, y: cy },
      R,
      [Skia.Color('rgba(255,122,61,0.30)'), Skia.Color('rgba(255,122,61,0.08)'), Skia.Color('rgba(255,122,61,0)')],
      [0, 0.6, 1],
      TileMode.Clamp,
    ),
  );
  const breath = Math.min(1, p);
  warm.setAlphaf(0.25 + 0.75 * breath);
  c.drawCircle(cx, cy, R * (0.7 + 0.3 * breath), warm);

  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setColor(Skia.Color(C.cream));
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeWidth(1.6);
  line.setStrokeCap(StrokeCap.Round);
  line.setStrokeJoin(StrokeJoin.Round);

  // The circle outside the band.
  const rim = Skia.Path.Make();
  for (let i = 0; i <= 160; i++) {
    const phi = angle + half + ((Math.PI * 2 - 2 * half) * i) / 160;
    const x = cx + Math.cos(phi) * R;
    const y = cy + Math.sin(phi) * R;
    if (i === 0) rim.moveTo(x, y);
    else rim.lineTo(x, y);
  }
  // The band's inner and outer edges, which meet the circle at both ends.
  for (let e = 0; e < 2; e++) {
    for (let i = 0; i <= 60; i++) {
      const pt = bandPoint(cx, cy, R, angle, half, inward, bulge, i / 60, e);
      if (i === 0) rim.moveTo(pt[0], pt[1]);
      else rim.lineTo(pt[0], pt[1]);
    }
  }
  c.drawPath(rim, line);

  // The weave: wavy strands along the band and gently bent ones across it.
  const show = smooth(p / 0.25);
  if (show > 0.01) {
    const weave = Skia.Path.Make();
    for (let k = 1; k < ALONG - 1; k++) {
      const base = k / (ALONG - 1);
      for (let i = 0; i <= 60; i++) {
        const s = i / 60;
        const q = Math.min(1, Math.max(0, base + 0.17 * Math.sin(Math.PI * 3 * s + k * 1.7 + phase)));
        const pt = bandPoint(cx, cy, R, angle, half, inward, bulge, s, q);
        if (i === 0) weave.moveTo(pt[0], pt[1]);
        else weave.lineTo(pt[0], pt[1]);
      }
    }
    for (let j = 0; j < ACROSS; j++) {
      const s0 = (j + 0.5) / ACROSS;
      for (let i = 0; i <= 20; i++) {
        const q = i / 20;
        const s = s0 + 0.035 * Math.sin(Math.PI * 2 * q + j * 1.1 + phase);
        const pt = bandPoint(cx, cy, R, angle, half, inward, bulge, s, q);
        if (i === 0) weave.moveTo(pt[0], pt[1]);
        else weave.lineTo(pt[0], pt[1]);
      }
    }
    const thread = Skia.Paint();
    thread.setAntiAlias(true);
    thread.setColor(Skia.Color(C.cream));
    thread.setAlphaf(0.85 * show);
    thread.setStyle(PaintStyle.Stroke);
    thread.setStrokeWidth(1.2);
    thread.setStrokeCap(StrokeCap.Round);
    c.drawPath(weave, thread);
  }

  // The bead presses the band's inner edge from inside, with a glow that grows with the breath.
  const r = R - BEAD_GAP - inward;
  const bx = cx + Math.cos(angle) * r;
  const by = cy + Math.sin(angle) * r;
  const glow = Skia.Paint();
  glow.setAntiAlias(true);
  glow.setColor(Skia.Color(C.bead));
  glow.setAlphaf(0.45 + 0.35 * breath);
  glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10 + 8 * breath, true));
  c.drawCircle(bx, by, 10, glow);
  const bead = Skia.Paint();
  bead.setAntiAlias(true);
  bead.setColor(Skia.Color(C.bead));
  c.drawCircle(bx, by, 7, bead);
}

type Props = {
  brand?: string;
  pattern?: string;
  round?: string;
  session?: string;
  action?: string;
  /** Breathes by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function NoodleNetOrbit({
  brand = 'unwind',
  pattern = '4 · 7 · 8',
  round = 'ROUND 2 OF 4',
  session = 'Evening wind-down · 6 min',
  action = 'End session',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [SERIF_FAMILY]: SERIF, [ITALIC_FAMILY]: SERIF_ITALIC });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const R = Math.min(width * 0.42, 166);
  const cx = width / 2;
  const cy = height * 0.47;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const time = useSharedValue(0);
  const touching = useSharedValue(false);
  const released = useSharedValue(-1);
  const userAngle = useSharedValue(REST_ANGLE);
  const userPress = useSharedValue(0);
  const angle = useSharedValue(REST_ANGLE);
  const press = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    if (touching.get()) {
      angle.set(userAngle.get());
      press.set(userPress.get());
      time.set(0);
      return;
    }
    const since = released.get() >= 0 ? now - released.get() : -1;
    if (since >= 0 && since < HOLD + RETURN) {
      // Hold where the finger left it for 2 s, then spring back to rest at the top.
      const k = easeInOutCubic(clamp01((since - HOLD) / RETURN));
      const delta = Math.atan2(Math.sin(REST_ANGLE - userAngle.get()), Math.cos(REST_ANGLE - userAngle.get()));
      angle.set(userAngle.get() + delta * k);
      press.set(userPress.get() * (1 - k));
      time.set(0);
      origin.set(now);
      return;
    }
    if (since >= 0) released.set(-1);
    const t = autoPlay ? (now - origin.get()) % LOOP : 0;
    time.set(t);
    const b = beadAt(t);
    angle.set(b.angle);
    press.set(b.press);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    // The weave's waves turn twice per loop, so the loop is seamless.
    drawNet(canvas, cx, cy, R, angle.get(), press.get(), (Math.PI * 4 * (elapsed.get() - origin.get())) / LOOP);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, R]);

  const word = (o: number) => {
    'worklet';
    return { opacity: o, transform: [{ translateY: 8 * (1 - o) }] };
  };
  const inStyle = useAnimatedStyle(() => word(touching.get() ? 0 : labelsAt(time.get()).inhale));
  const holdStyle = useAnimatedStyle(() => word(touching.get() ? 1 : labelsAt(time.get()).hold));
  const outStyle = useAnimatedStyle(() => word(touching.get() ? 0 : labelsAt(time.get()).exhale));

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const follow = (x: number, y: number) => {
    'worklet';
    const dx = x - cx;
    const dy = y - cy;
    userAngle.set(Math.atan2(dy, dx));
    userPress.set(Math.min(MAX_PRESS, Math.max(0, (R - BEAD_GAP - Math.hypot(dx, dy)) / INWARD)));
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin((e) => {
      touching.set(true);
      follow(e.x, e.y);
      scheduleOnRN(haptic);
    })
    .onUpdate((e) => follow(e.x, e.y))
    .onFinalize(() => {
      touching.set(false);
      released.set(elapsed.get());
    });

  if (!fontsLoaded) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="Breathing circle. Drag the red bead to pull the net.">
            <Rect x={0} y={0} width={width} height={height}>
              <RadialGradient c={vec(cx, cy)} r={height * 0.6} colors={[C.lift, C.background]} />
            </Rect>
            <Picture picture={picture} />
          </Canvas>
          <View style={[styles.header, { top: insets.top + 8 }]} pointerEvents="none">
            <Text style={styles.brand}>{brand}</Text>
            <View style={styles.pill}>
              <Text style={styles.pattern}>{pattern}</Text>
            </View>
          </View>
          <Text style={[styles.round, { top: cy - R - 56 }]} pointerEvents="none">
            {round}
          </Text>
          <View style={[styles.phase, { top: cy - 34 }]} pointerEvents="none">
            <Animated.Text style={[styles.word, inStyle]}>Inhale</Animated.Text>
            <Animated.Text style={[styles.word, holdStyle]}>Hold</Animated.Text>
            <Animated.Text style={[styles.word, outStyle]}>Exhale</Animated.Text>
          </View>
          <View style={[styles.under, { top: cy + R + 36 }]} pointerEvents="none">
            <View style={styles.marks}>
              {[0, 1, 2, 3].map((k) => (
                <View key={k} style={[styles.mark, k < 2 && styles.markOn]} />
              ))}
            </View>
            <Text style={styles.session}>{session}</Text>
          </View>
        </View>
      </GestureDetector>
      <View style={[styles.footer, { bottom: insets.bottom + 18 }]}>
        <View style={styles.button} accessible accessibilityRole="button">
          <Text style={styles.action}>{action}</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.background },
  header: { position: 'absolute', left: 24, right: 24, height: 36, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontFamily: ITALIC_FAMILY, fontSize: 30, lineHeight: 36, color: C.cream },
  pill: { height: 30, paddingHorizontal: 14, borderRadius: 15, borderWidth: 1, borderColor: C.faint, justifyContent: 'center' },
  pattern: { fontSize: 13, fontWeight: '600', color: C.cream, letterSpacing: 1 },
  round: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontSize: 12, lineHeight: 16, fontWeight: '600', color: C.dim, letterSpacing: 3 },
  phase: { position: 'absolute', left: 0, right: 0, height: 68, alignItems: 'center' },
  word: { position: 'absolute', fontFamily: ITALIC_FAMILY, fontSize: 58, lineHeight: 68, color: C.cream },
  under: { position: 'absolute', left: 24, right: 24, alignItems: 'center' },
  marks: { flexDirection: 'row', gap: 8 },
  mark: { width: 28, height: 3, borderRadius: 2, backgroundColor: C.faint },
  markOn: { backgroundColor: C.cream },
  session: { marginTop: 14, fontFamily: SERIF_FAMILY, fontSize: 20, lineHeight: 26, color: C.dim },
  footer: { position: 'absolute', left: 24, right: 24, alignItems: 'center' },
  button: { height: 52, paddingHorizontal: 34, borderRadius: 26, borderWidth: 1, borderColor: C.faint, alignItems: 'center', justifyContent: 'center' },
  action: { fontSize: 15, fontWeight: '600', color: C.cream, letterSpacing: 0.3 },
});
