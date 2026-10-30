import { Canvas, Picture, Skia, type SkCanvas } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// A habit app's "What do you want to build first?" step. One big glossy icon in the middle spins
// a full turn while it morphs into the next focus — a drop, a leaf, a sun, a cloud, a heart, a
// rounded triangle — its colour following, while the whole screen washes into that focus's tint,
// the focus's name is set huge in Bricolage Grotesque below it, and a strip of six small icons
// marks the choice. Swipe sideways to spin through them yourself.
//
// Every icon is stored as its radius in 120 directions round its centre (ray-cast from a closed
// outline), so any two blend point by point: r = mix(rA, rB, u), drawn rotated by 2 pi u and
// scaled by 1 - 0.22 sin(pi u). The fill is a diagonal gradient (35 % lighter -> the colour ->
// 18 % darker) with a soft white highlight, over a blurred shadow in the focus's deep colour.
//
// Layout (393 x 852 pt): step label and Bricolage 30/34 pt question at the top; icon radius
// 96 pt centred at 44 % of the height; name Bricolage 64 pt 150 pt below the centre with a 16 pt
// line under it; the strip of six 26 pt icons (selected 1.35 x on a white disc) above a 56 pt
// button in the focus's deep colour.
//
// autoPlay timeline (LOOP = 7.2 s): six steps of 1.2 s; in each, hold 0.55 s, then morph to the
// next icon over 0.65 s with ease-in-out cubic. The sixth morph lands back on the drop at 7.2 s.
// Dragging: 90 pt per icon; release snaps to the nearest icon over 0.35 s, it stays 2 s, then the
// loop carries on from that icon.

/** Seconds. */
export const LOOP = 7.2;
const STEP = 1.2;
const MORPH_START = 0.55;
const RAYS = 120;
const SIZE = 96;
const MINI = 13;
const PER_ICON = 90;
const HOLD = 2;
const SNAP = 0.35;

type Focus = { name: string; line: string; color: string; deep: string; tint: string };
const FOCUSES: Focus[] = [
  { name: 'Hydrate', line: 'Eight glasses, one at a time.', color: '#19B5AE', deep: '#0B5D59', tint: '#DDF4F2' },
  { name: 'Grow', line: 'Ten minutes of something new.', color: '#3DB66A', deep: '#1D5E35', tint: '#E2F3E7' },
  { name: 'Energy', line: 'Move before you sit down.', color: '#FF9F1C', deep: '#8A4B00', tint: '#FFF0DA' },
  { name: 'Rest', line: 'Lights out by eleven.', color: '#8F7BF2', deep: '#3F2F9C', tint: '#ECE8FD' },
  { name: 'Care', line: 'Check in with someone you love.', color: '#FF5A64', deep: '#9C1C27', tint: '#FFE5E7' },
  { name: 'Focus', line: 'One task, phone face down.', color: '#3D7BFF', deep: '#153D9C', tint: '#E2EBFF' },
];

const BRICOLAGE = require('./assets/BricolageHeadline.ttf');
const INTER = require('./assets/Inter-Medium.ttf');
const HEAD_FAMILY = 'SpinMorphIcons-Bricolage';
const BODY_FAMILY = 'SpinMorphIcons-InterMedium';

// ---- Outlines, built once on the JS thread ----

function sampleCurve(f: (t: number) => number[], n: number) {
  const pts: number[][] = [];
  for (let i = 0; i < n; i++) pts.push(f((i / n) * Math.PI * 2));
  return pts;
}

/** The farthest hit of a ray from (0, 0) in direction a on a closed polygon. */
function castPolygon(pts: number[][], a: number) {
  const dx = Math.cos(a);
  const dy = Math.sin(a);
  let best = 0;
  for (let i = 0; i < pts.length; i++) {
    const p = pts[i];
    const q = pts[(i + 1) % pts.length];
    const ex = q[0] - p[0];
    const ey = q[1] - p[1];
    const den = dx * ey - dy * ex;
    if (Math.abs(den) < 1e-9) continue;
    const t = (p[0] * ey - p[1] * ex) / den;
    const s = (p[0] * dy - p[1] * dx) / den;
    if (t > 0 && s >= 0 && s <= 1) best = Math.max(best, t);
  }
  return best;
}

function profileOf(radius: (a: number) => number, blur: number) {
  const raw: number[] = [];
  for (let i = 0; i < RAYS; i++) raw.push(radius((i / RAYS) * Math.PI * 2 - Math.PI / 2));
  if (blur <= 0) return raw;
  const out: number[] = [];
  for (let i = 0; i < RAYS; i++) {
    let sum = 0;
    for (let k = -blur; k <= blur; k++) sum += raw[(i + k + RAYS) % RAYS];
    out.push(sum / (2 * blur + 1));
  }
  return out;
}

function polygonProfile(pts: number[][], blur: number) {
  return profileOf((a) => castPolygon(pts, a), blur);
}

/** Radii from direction -90 deg (straight up), clockwise, in units of SIZE. */
const PROFILES: number[][] = [
  // Drop: a teardrop with its tip up.
  polygonProfile(
    sampleCurve((t) => [0.82 * Math.sin(t) * Math.sin(t / 2), -0.98 * Math.cos(t) + 0.05], 240),
    1,
  ),
  // Leaf: a lens with tips at 45 degrees.
  polygonProfile(
    (() => {
      const c = 0.634;
      const R = 1.184;
      const pts: number[][] = [];
      // Right arc (centre (-c, 0)) from the bottom tip to the top tip, then the left arc back.
      const half = Math.atan2(1, c);
      for (let i = 0; i <= 60; i++) {
        const a = -half + (2 * half * i) / 60;
        pts.push([-c + R * Math.cos(a), -R * Math.sin(a)]);
      }
      for (let i = 0; i <= 60; i++) {
        const a = Math.PI - half + (2 * half * i) / 60;
        pts.push([c + R * Math.cos(a), -R * Math.sin(a)]);
      }
      const k = Math.SQRT1_2;
      return pts.map((p) => [(p[0] * k - p[1] * k) * 0.95, (p[0] * k + p[1] * k) * 0.95]);
    })(),
    1,
  ),
  // Sun: a disc with eight rounded rays.
  profileOf((a) => 0.7 + 0.2 * Math.pow(Math.max(0, Math.cos(8 * a)), 2), 0),
  // Cloud: three puffs on a flat base.
  profileOf((a) => {
    const dx = Math.cos(a);
    const dy = Math.sin(a);
    const puffs = [
      [-0.5, 0.12, 0.42],
      [0.02, -0.16, 0.58],
      [0.52, 0.14, 0.4],
      [0, 0.2, 0.42],
    ];
    let best = 0;
    for (const p of puffs) {
      const b = dx * p[0] + dy * p[1];
      const disc = b * b - (p[0] * p[0] + p[1] * p[1] - p[2] * p[2]);
      if (disc >= 0) best = Math.max(best, b + Math.sqrt(disc));
    }
    if (dy > 0) best = Math.min(best, 0.54 / dy);
    return best;
  }, 1),
  // Heart.
  polygonProfile(
    sampleCurve((t) => {
      const x = 16 * Math.pow(Math.sin(t), 3);
      const y = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
      return [x / 17, -y / 17 - 0.1];
    }, 240),
    1,
  ),
  // Focus: a triangle pointing up, corners rounded by blurring the radii.
  polygonProfile(
    [
      [0, -0.95],
      [0.95, 0.68],
      [-0.95, 0.68],
    ],
    4,
  ),
];


// ---- Animation ----

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
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

/** The icon index (fractional while morphing), `t` seconds into the loop. */
function indexAt(t: number) {
  'worklet';
  const k = Math.floor(t / STEP);
  const local = t - k * STEP;
  return k + easeInOutCubic(clamp01((local - MORPH_START) / (STEP - MORPH_START)));
}

function mixColor(a: string, b: string, u: number) {
  'worklet';
  const pa = parseInt(a.slice(1), 16);
  const pb = parseInt(b.slice(1), 16);
  const r = Math.round(((pa >> 16) & 255) * (1 - u) + ((pb >> 16) & 255) * u);
  const g = Math.round(((pa >> 8) & 255) * (1 - u) + ((pb >> 8) & 255) * u);
  const bl = Math.round((pa & 255) * (1 - u) + (pb & 255) * u);
  return `#${((1 << 24) + (r << 16) + (g << 8) + bl).toString(16).slice(1)}`;
}

/** Index modulo the six icons, always in [0, 6). */
function wrap(i: number) {
  'worklet';
  const n = FOCUSES.length;
  return ((i % n) + n) % n;
}

/** A focus property blended at fractional index i. */
function blend(i: number, key: 'color' | 'deep' | 'tint') {
  'worklet';
  const base = Math.floor(i);
  return mixColor(FOCUSES[wrap(base)][key], FOCUSES[wrap(base + 1)][key], i - base);
}

/** The outline blended at index i, radius scale s, turned by `spin`, centred on (cx, cy). */
function outline(i: number, s: number, spin: number, cx: number, cy: number) {
  'worklet';
  const base = Math.floor(i);
  const u = i - base;
  const a = PROFILES[wrap(base)];
  const b = PROFILES[wrap(base + 1)];
  const p = Skia.Path.Make();
  for (let k = 0; k < RAYS; k++) {
    const r = (a[k] * (1 - u) + b[k] * u) * s;
    const ang = (k / RAYS) * Math.PI * 2 - Math.PI / 2 + spin;
    const x = cx + Math.cos(ang) * r;
    const y = cy + Math.sin(ang) * r;
    if (k === 0) p.moveTo(x, y);
    else p.lineTo(x, y);
  }
  p.close();
  return p;
}

function drawIcon(c: SkCanvas, i: number, cx: number, cy: number) {
  'worklet';
  const u = i - Math.floor(i);
  const s = SIZE * (1 - 0.22 * Math.sin(Math.PI * u));
  const path = outline(i, s, Math.PI * 2 * u, cx, cy);
  const color = blend(i, 'color');

  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Skia.Color(blend(i, 'deep')));
  shadow.setAlphaf(0.32);
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 18, true));
  c.save();
  c.translate(0, 18);
  c.drawPath(path, shadow);
  c.restore();

  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: cx - SIZE, y: cy - SIZE },
      { x: cx + SIZE, y: cy + SIZE },
      [Skia.Color(mixColor(color, '#FFFFFF', 0.35)), Skia.Color(color), Skia.Color(mixColor(color, '#000000', 0.18))],
      [0, 0.5, 1],
      0,
    ),
  );
  c.drawPath(path, body);

  // A soft highlight in the upper left, kept inside the shape.
  c.save();
  c.clipPath(path, 1, true);
  const shine = Skia.Paint();
  shine.setAntiAlias(true);
  shine.setColor(Skia.Color('#FFFFFF'));
  shine.setAlphaf(0.32);
  shine.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 14, true));
  c.drawOval(Skia.XYWHRect(cx - SIZE * 0.75, cy - SIZE * 0.85, SIZE * 0.9, SIZE * 0.55), shine);
  c.restore();
}

function drawStrip(c: SkCanvas, i: number, cx: number, y: number) {
  'worklet';
  const n = FOCUSES.length;
  const gap = 50;
  for (let k = 0; k < n; k++) {
    let d = Math.abs(wrap(i) - k);
    d = Math.min(d, n - d);
    const sel = 1 - smooth(d / 0.6);
    const x = cx + (k - (n - 1) / 2) * gap;
    if (sel > 0.01) {
      const disc = Skia.Paint();
      disc.setAntiAlias(true);
      disc.setColor(Skia.Color('#FFFFFF'));
      disc.setAlphaf(sel);
      c.drawCircle(x, y, 23 * (0.7 + 0.3 * sel), disc);
    }
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setColor(Skia.Color(sel > 0.5 ? FOCUSES[k].color : FOCUSES[k].deep));
    p.setAlphaf(0.3 + 0.7 * sel);
    c.drawPath(outline(k, MINI * (1 + 0.35 * sel), 0, x, y), p);
  }
}

type Props = {
  step?: string;
  question?: string;
  action?: string;
  /** Spins through the icons by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function SpinMorphIcons({
  step = 'STEP 2 OF 3',
  question = 'What do you want\nto build first?',
  action = 'Choose this focus',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [HEAD_FAMILY]: BRICOLAGE, [BODY_FAMILY]: INTER });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.44;
  const stripY = height - insets.bottom - 16 - 56 - 46;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const index = useSharedValue(0);
  const touching = useSharedValue(false);
  const manual = useSharedValue(false);
  const released = useSharedValue(-1);
  const grabbed = useSharedValue(0);
  const from = useSharedValue(0);
  const snapTo = useSharedValue(0);
  const lastStep = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    if (touching.get()) return;
    if (manual.get()) {
      const since = now - released.get();
      if (since < SNAP) index.set(from.get() + (snapTo.get() - from.get()) * easeInOutCubic(since / SNAP));
      else {
        index.set(snapTo.get());
        if (since > SNAP + HOLD) {
          // Carry on from this icon: its hold starts the next loop step.
          manual.set(false);
          origin.set(now - wrap(snapTo.get()) * STEP);
        }
      }
      return;
    }
    index.set(autoPlay ? indexAt((now - origin.get()) % LOOP) : 0);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawIcon(canvas, index.get(), cx, cy);
    drawStrip(canvas, index.get(), cx, stripY);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, stripY]);

  const screenStyle = useAnimatedStyle(() => ({ backgroundColor: blend(index.get(), 'tint') }));
  const buttonStyle = useAnimatedStyle(() => ({ backgroundColor: blend(index.get(), 'deep') }));
  const headStyle = useAnimatedStyle(() => ({ color: blend(index.get(), 'deep') }));
  const labelStyle = (i: number, k: number) => {
    'worklet';
    let d = Math.abs(wrap(i) - k);
    d = Math.min(d, FOCUSES.length - d);
    const o = 1 - smooth(d / 0.45);
    return { opacity: o, transform: [{ translateY: 14 * (1 - o) }, { scale: 0.94 + 0.06 * o }] };
  };
  const l0 = useAnimatedStyle(() => labelStyle(index.get(), 0));
  const l1 = useAnimatedStyle(() => labelStyle(index.get(), 1));
  const l2 = useAnimatedStyle(() => labelStyle(index.get(), 2));
  const l3 = useAnimatedStyle(() => labelStyle(index.get(), 3));
  const l4 = useAnimatedStyle(() => labelStyle(index.get(), 4));
  const l5 = useAnimatedStyle(() => labelStyle(index.get(), 5));
  const labelStyles = useMemo(() => [l0, l1, l2, l3, l4, l5], [l0, l1, l2, l3, l4, l5]);

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.selectionAsync().catch(() => {});
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(4)
    .onBegin(() => {
      touching.set(true);
      manual.set(true);
      grabbed.set(Math.round(index.get() * 100) / 100);
      lastStep.set(Math.round(index.get()));
    })
    .onUpdate((e) => {
      const i = grabbed.get() - e.translationX / PER_ICON;
      index.set(i);
      if (Math.round(i) !== lastStep.get()) {
        lastStep.set(Math.round(i));
        scheduleOnRN(haptic);
      }
    })
    .onFinalize(() => {
      touching.set(false);
      from.set(index.get());
      snapTo.set(Math.round(index.get()));
      released.set(elapsed.get());
    });

  if (!fontsLoaded) return <View style={[styles.screen, { backgroundColor: FOCUSES[0].tint }]} />;

  const labelTop = cy + 150;

  return (
    <Animated.View style={[styles.screen, screenStyle]}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <View style={[styles.head, { top: insets.top + 14 }]} pointerEvents="none">
            <Animated.Text style={[styles.step, headStyle]}>{step}</Animated.Text>
            <Animated.Text style={[styles.question, headStyle]}>{question}</Animated.Text>
          </View>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="Focus icon. Swipe to change.">
            <Picture picture={picture} />
          </Canvas>
          <View style={[styles.labels, { top: labelTop }]} pointerEvents="none">
            {FOCUSES.map((f, k) => (
              <Animated.View key={f.name} style={[styles.label, labelStyles[k]]}>
                <Text style={[styles.name, { color: f.deep }]}>{f.name}</Text>
                <Text style={[styles.line, { color: f.deep }]}>{f.line}</Text>
              </Animated.View>
            ))}
          </View>
        </View>
      </GestureDetector>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 16 }]}>
        <Animated.View style={[styles.button, buttonStyle]} accessible accessibilityRole="button">
          <Text style={styles.action}>{action}</Text>
        </Animated.View>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  head: { position: 'absolute', left: 24, right: 24 },
  step: { fontFamily: BODY_FAMILY, fontSize: 12, letterSpacing: 2, opacity: 0.7 },
  question: { marginTop: 10, fontFamily: HEAD_FAMILY, fontSize: 32, lineHeight: 36, letterSpacing: -0.6 },
  labels: { position: 'absolute', left: 20, right: 20, height: 100 },
  label: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  name: { fontFamily: HEAD_FAMILY, fontSize: 64, lineHeight: 72, letterSpacing: -1.5 },
  line: { marginTop: 2, fontFamily: BODY_FAMILY, fontSize: 16, lineHeight: 22, opacity: 0.8 },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0 },
  button: { height: 56, borderRadius: 28, alignItems: 'center', justifyContent: 'center' },
  action: { fontFamily: BODY_FAMILY, fontSize: 17, color: '#FFFFFF' },
});
