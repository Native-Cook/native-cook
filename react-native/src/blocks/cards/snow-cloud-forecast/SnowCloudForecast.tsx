import { Canvas, Group, Image, LinearGradient, PaintStyle, Picture, Rect, Skia, StrokeCap, useImage, vec, type SkCanvas } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// A weather screen on a deep night-blue gradient (#060914 -> #1B2547) for a snowy afternoon: a soft 3D cloud floats over a huge hairline
// "−8°" (150 pt, weight 100), breathes in, and lets a flurry of six-armed snowflakes drift down past the reading,
// swaying as they fall, until the flurry thins out and the cloud rests again. Drag the cloud
// sideways and it snows wherever you take it.
//
// Snow: 72 flakes, each falling 380 pt in 2 s on its own phase, so flake i is at progress
// p = fract(tau / 2 + phase_i). It shows only if the snow intensity at the moment it left the
// cloud (tau - 2p) is above its own threshold, and it starts from where the cloud was then
// (a 3 s history of the cloud's x and the intensity, 30 samples a second).
//
// autoPlay timeline (LOOP = 8 s), t in seconds:
//   0.0-0.8  rest: the cloud bobs (4 pt, one sine per loop), no snow
//   0.8-1.4  the cloud breathes in: scale 1 -> 1.04 -> 1.02 (damped)
//   1.2-2.0  intensity 0 -> 1 (smoothstep): the flurry starts
//   2.0-4.0  full snow
//   4.0-4.8  intensity 1 -> 0
//   4.8-6.8  the last flakes land and fade
//   6.8-8.0  rest, identical to t = 0
// Dragging: the cloud follows the finger (within 110 pt of the middle) and it snows fully; after
// release it stays 2 s, glides back over 0.8 s and the loop starts again.

/** Seconds. */
export const LOOP = 8;
const FALL = 2;
const DROP = 380;
const FLAKES = 72;
const CLOUD = 320;
const HISTORY = 90;
const RATE = 30;
const HOLD = 2;
const RETURN = 0.8;

const CLOUD_IMAGE = require('./assets/cloud.png');

const C = {
  background: '#070B18',
  skyTop: '#060914',
  skyBottom: '#1B2547',
  text: '#FFFFFF',
  dim: '#8D8F98',
  flake: '#DCEBFF',
  line: '#1F2026',
  now: '#17181D',
};

const HOURS = [
  { time: 'Now', temp: '−8°', snow: true },
  { time: '15', temp: '−8°', snow: true },
  { time: '16', temp: '−9°', snow: true },
  { time: '17', temp: '−9°', snow: false },
  { time: '18', temp: '−10°', snow: false },
  { time: '19', temp: '−11°', snow: true },
  { time: '20', temp: '−11°', snow: false },
];

type Flake = { phase: number; dx: number; size: number; sway: number; threshold: number; spin: number };
const FLAKE_LIST: Flake[] = Array.from({ length: FLAKES }, (_, i) => {
  const h = (n: number) => {
    const x = Math.sin((i + 1) * 91.345 + n * 47.853) * 43758.5453;
    return x - Math.floor(x);
  };
  return {
    phase: h(1),
    dx: (h(2) - 0.5) * 260,
    size: 3.5 + 5 * h(3),
    sway: 5 + 10 * h(4),
    threshold: 0.05 + 0.9 * h(5),
    spin: (h(6) - 0.5) * 4,
  };
});

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function smooth(x: number) {
  'worklet';
  const u = clamp01(x);
  return u * u * (3 - 2 * u);
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** Snow intensity, `t` seconds into the loop. */
function intensityAt(t: number) {
  'worklet';
  return smooth(seg(t, 1.2, 2.0)) * (1 - smooth(seg(t, 4.0, 4.8)));
}
/** The cloud's breath: a damped swell after 0.8 s. */
function breathAt(t: number) {
  'worklet';
  const x = seg(t, 0.8, 1.4);
  return x <= 0 || x >= 1 ? 0.02 * smooth(seg(t, 1.4, 1.6)) * (1 - smooth(seg(t, 4.8, 6.0))) : 0.04 * Math.sin(x * Math.PI) + 0.02 * smooth(x);
}

/** A six-armed snowflake of radius r, centred on (0, 0). */
function flakePath(r: number) {
  'worklet';
  const p = Skia.Path.Make();
  for (let k = 0; k < 6; k++) {
    const a = (k * Math.PI) / 3;
    const ca = Math.cos(a);
    const sa = Math.sin(a);
    p.moveTo(0, 0);
    p.lineTo(ca * r, sa * r);
    // Two little barbs on each arm.
    const bx = ca * r * 0.6;
    const by = sa * r * 0.6;
    const b = r * 0.32;
    p.moveTo(bx, by);
    p.lineTo(bx + Math.cos(a + 0.7) * b, by + Math.sin(a + 0.7) * b);
    p.moveTo(bx, by);
    p.lineTo(bx + Math.cos(a - 0.7) * b, by + Math.sin(a - 0.7) * b);
  }
  return p;
}

function drawSnow(c: SkCanvas, tau: number, now: number, top: number, xs: number[], is: number[]) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(C.flake));
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeCap(StrokeCap.Round);
  for (let i = 0; i < FLAKES; i++) {
    const f = FLAKE_LIST[i];
    const q = tau / FALL + f.phase;
    const p = q - Math.floor(q);
    // When this flake left the cloud, as a slot of the history ring.
    const born = now - p * FALL;
    const slot = ((Math.floor(born * RATE) % HISTORY) + HISTORY) % HISTORY;
    const intensity = is[slot];
    if (intensity < f.threshold) continue;
    const alpha = smooth(p / 0.1) * (1 - smooth((p - 0.75) / 0.25)) * clamp01((intensity - f.threshold) * 6);
    if (alpha <= 0.01) continue;
    const x = xs[slot] + f.dx * (0.55 + 0.45 * p) + Math.sin(p * Math.PI * 2.6 + f.phase * 6.28) * f.sway;
    const y = top + p * DROP;
    paint.setAlphaf(alpha * 0.95);
    paint.setStrokeWidth(Math.max(1, f.size * 0.22));
    c.save();
    c.translate(x, y);
    c.rotate((p * f.spin * 180) / Math.PI, 0, 0);
    c.drawPath(flakePath(f.size), paint);
    c.restore();
  }
}

type Props = {
  city?: string;
  other?: string;
  temperature?: string;
  range?: string;
  summary?: string;
  /** Lets it snow by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function SnowCloudForecast({
  city = 'Northfield',
  other = 'Halden',
  temperature = '−8°',
  range = '↑ 2°   ↓ −11°',
  summary = 'Snow is on its way',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const cloud = useImage(CLOUD_IMAGE);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cloudTop = insets.top + 20;
  // Flakes leave from under the cloud's belly.
  const snowTop = cloudTop + CLOUD * 0.66;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const tau = useSharedValue(0);
  const cloudX = useSharedValue(cx);
  const bob = useSharedValue(0);
  const breath = useSharedValue(0);
  const touching = useSharedValue(false);
  const manual = useSharedValue(false);
  const released = useSharedValue(-1);
  const fingerX = useSharedValue(cx);
  const xs = useSharedValue<number[]>(new Array(HISTORY).fill(cx));
  const is = useSharedValue<number[]>(new Array(HISTORY).fill(0));

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    let x = cx;
    let intensity = 0;
    if (touching.get()) {
      x = fingerX.get();
      intensity = 1;
    } else if (manual.get()) {
      const since = now - released.get();
      const k = easeInOutCubic(clamp01((since - HOLD) / RETURN));
      x = fingerX.get() + (cx - fingerX.get()) * k;
      intensity = 1 - smooth(since / 0.6);
      if (since > HOLD + RETURN) {
        manual.set(false);
        origin.set(now);
      }
    } else if (autoPlay) {
      const t = (now - origin.get()) % LOOP;
      intensity = intensityAt(t);
      breath.set(breathAt(t));
    }
    const t = (now - origin.get()) % LOOP;
    tau.set(t);
    bob.set(Math.sin((t / LOOP) * Math.PI * 2) * 4);
    cloudX.set(x);
    const slot = Math.floor(now * RATE) % HISTORY;
    xs.modify((list) => {
      'worklet';
      list[slot] = x;
      return list;
    });
    is.modify((list) => {
      'worklet';
      list[slot] = intensity;
      return list;
    });
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const snow = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawSnow(canvas, tau.get(), elapsed.get(), snowTop, xs.get(), is.get());
    return recorder.finishRecordingAsPicture();
  }, [width, height, snowTop]);
  const cloudTransform = useDerivedValue(() => {
    const s = 1 + breath.get();
    return [
      { translateX: cloudX.get() },
      { translateY: cloudTop + CLOUD / 2 + bob.get() },
      { scale: s },
      { translateX: -CLOUD / 2 },
      { translateY: -CLOUD / 2 },
    ];
  });

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin((e) => {
      touching.set(true);
      manual.set(true);
      fingerX.set(Math.max(cx - 110, Math.min(cx + 110, e.x)));
      scheduleOnRN(haptic);
    })
    .onUpdate((e) => {
      fingerX.set(Math.max(cx - 110, Math.min(cx + 110, e.x)));
    })
    .onFinalize(() => {
      touching.set(false);
      released.set(elapsed.get());
    });

  const readingTop = snowTop + 60;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Rect x={0} y={0} width={width} height={height}>
              <LinearGradient start={vec(0, 0)} end={vec(0, height)} colors={[C.skyTop, C.skyBottom]} />
            </Rect>
          </Canvas>
          <View style={[styles.reading, { top: readingTop }]} pointerEvents="none">
            <Text style={styles.temperature} numberOfLines={1}>
              {temperature}
            </Text>
            <Text style={styles.range}>{range}</Text>
            <Text style={styles.summary}>{summary}</Text>
          </View>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel={`${city}, ${temperature}, ${summary}`}>
            <Picture picture={snow} />
            <Group transform={cloudTransform}>{cloud ? <Image image={cloud} x={0} y={0} width={CLOUD} height={CLOUD} fit="contain" /> : null}</Group>
          </Canvas>
          <View style={[styles.header, { top: insets.top + 10 }]} pointerEvents="none">
            <Text style={styles.city}>{city}</Text>
            <Text style={styles.other}>{other}</Text>
          </View>
        </View>
      </GestureDetector>

      <View style={[styles.hours, { bottom: insets.bottom + 14 }]}>
        <Text style={styles.hoursTitle}>Snow from 14:00 · 4 cm by evening</Text>
        <View style={styles.rule} />
        <View style={styles.row}>
          {HOURS.map((h, i) => (
            <View key={h.time} style={[styles.hour, i === 0 && styles.hourNow]}>
              <Text style={[styles.hourTime, i === 0 && { color: C.text }]}>{h.time}</Text>
              <View style={[styles.mark, h.snow ? styles.markSnow : styles.markCloud]} />
              <Text style={styles.hourTemp}>{h.temp}</Text>
            </View>
          ))}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.background },
  header: { position: 'absolute', left: 0, right: 0, height: 24, flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 22 },
  city: { fontSize: 17, fontWeight: '700', color: C.text },
  other: { fontSize: 17, fontWeight: '500', color: C.dim },
  reading: { position: 'absolute', left: 24, right: 24 },
  temperature: { fontSize: 150, lineHeight: 156, fontWeight: '100', color: C.text, letterSpacing: -8, marginLeft: -6 },
  range: { marginTop: -4, fontSize: 16, lineHeight: 22, color: C.text, fontWeight: '600' },
  summary: { marginTop: 4, fontSize: 30, lineHeight: 36, color: C.text, fontWeight: '300', letterSpacing: -0.5 },
  hours: { position: 'absolute', left: 14, right: 14, padding: 14, borderRadius: 26, backgroundColor: 'rgba(255,255,255,0.07)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.10)' },
  hoursTitle: { fontSize: 13, fontWeight: '600', color: C.dim, marginBottom: 10, marginLeft: 4 },
  rule: { height: StyleSheet.hairlineWidth, backgroundColor: 'rgba(255,255,255,0.12)', marginBottom: 8 },
  row: { flexDirection: 'row', justifyContent: 'space-between' },
  hour: { width: 46, paddingVertical: 10, borderRadius: 23, alignItems: 'center' },
  hourNow: { backgroundColor: 'rgba(255,255,255,0.12)' },
  hourTime: { fontSize: 13, color: C.dim, fontWeight: '500' },
  mark: { marginVertical: 10, width: 14, height: 14, borderRadius: 7 },
  markSnow: { borderWidth: 2, borderColor: C.flake },
  markCloud: { backgroundColor: '#5B5E68' },
  hourTemp: { fontSize: 14, color: C.text, fontWeight: '500' },
});
