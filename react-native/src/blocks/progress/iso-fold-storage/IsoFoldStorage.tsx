import { Canvas, Picture, Skia, type SkCanvas } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// Choosing a storage plan (type: Archivo Expanded Black for the title and the 54 pt size,
// Archivo SemiBold for the rest), shown as one isometric white block on salmon paper with a hard red
// shadow. Lite is a small cube; Plus extrudes it along the floor into a long bar; Max raises the
// bar into a tall wall whose shadow sweeps across the floor. The plan name, size and price
// cross-fade with the block, and a white thumb slides along the Lite / Plus / Max switch.
// Drag sideways to build or fold the block yourself.
//
// The block follows one value, `stage` s (0 Lite, 1 Plus, 2 Max):
//   length (x) = 32 + 116 min(s, 1) pt,  height (z) = 32 + 116 max(0, s - 1) pt,  depth (y) = 32 pt
// Isometric projection: (x, y, z) -> (ox + 0.866 (x - y), oy + 0.5 (x + y) - z). The shadow is
// the block swept along the floor by (-0.18 z, 0.9 z).
//
// autoPlay timeline (LOOP = 8 s), t in seconds:
//   0.0-1.0  rest at Lite
//   1.0-2.2  s 0 -> 1 (ease-out back, overshoot 1.4): the cube extrudes into a bar
//   2.2-3.0  hold at Plus
//   3.0-4.2  s 1 -> 2 (ease-out back): the bar rises into a wall
//   4.2-5.6  hold at Max
//   5.6-6.6  s 2 -> 1 (ease-in-out cubic): the wall folds down
//   6.6-7.4  s 1 -> 0 (ease-in-out cubic): the bar retracts
//   7.4-8.0  rest, identical to t = 0
// Dragging: s follows the finger (120 pt per stage); release snaps to the nearest stage over
// 0.35 s, stays 2 s, then returns to Lite over 0.8 s and the loop starts again.

/** Seconds. */
export const LOOP = 8;
const UNIT = 32;
const GROW = 116;
const PER_STAGE = 120;
const HOLD = 2;
const SNAP = 0.35;
const RETURN = 0.8;

const C = {
  background: '#F2A3A3',
  ink: '#4A0F14',
  soft: '#8C3B40',
  top: '#FFFFFF',
  left: '#F6E6E6',
  right: '#EBCFCF',
  edge: '#E9C4C4',
  shadow: '#E3262E',
  track: '#E68B8D',
  thumb: '#FFFFFF',
};

const BLACK = require('./assets/ArchivoExpanded-Black.ttf');
const SEMIBOLD = require('./assets/Archivo-SemiBold.ttf');
const BLACK_FAMILY = 'IsoFoldStorage-ArchivoExpandedBlack';
const SEMIBOLD_FAMILY = 'IsoFoldStorage-ArchivoSemiBold';

const PLANS = [
  { name: 'Lite', size: '50 GB', price: 'Free forever' },
  { name: 'Plus', size: '200 GB', price: '$2.99 a month' },
  { name: 'Max', size: '2 TB', price: '$9.99 a month' },
];

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
function easeOutBack(x: number) {
  'worklet';
  const k = 1.4;
  return 1 + (k + 1) * Math.pow(x - 1, 3) + k * Math.pow(x - 1, 2);
}

/** The stage `t` seconds into the loop. */
function stageAt(t: number) {
  'worklet';
  if (t < 3.0) return easeOutBack(seg(t, 1.0, 2.2));
  if (t < 5.6) return 1 + easeOutBack(seg(t, 3.0, 4.2));
  if (t < 6.6) return 2 - easeInOutCubic(seg(t, 5.6, 6.6));
  return 1 - easeInOutCubic(seg(t, 6.6, 7.4));
}

/** The readout of plan k: shown at its stage, fading and sliding away from it. */
function planStyle(s: number, k: number) {
  'worklet';
  return { opacity: 1 - smooth(Math.abs(s - k) / 0.5), transform: [{ translateY: (s - k) * -14 }] };
}

function quad(c: SkCanvas, pts: number[], color: string) {
  'worklet';
  const p = Skia.Path.Make();
  p.moveTo(pts[0], pts[1]);
  p.lineTo(pts[2], pts[3]);
  p.lineTo(pts[4], pts[5]);
  p.lineTo(pts[6], pts[7]);
  p.close();
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(color));
  c.drawPath(p, paint);
}

function drawBlock(c: SkCanvas, ox: number, oy: number, s: number) {
  'worklet';
  const length = UNIT + GROW * (s <= 1 ? s : 1);
  const h = UNIT + GROW * Math.max(0, s - 1);
  const d = UNIT;
  const px = (x: number, y: number) => ox + 0.866 * (x - y);
  const py = (x: number, y: number, z: number) => oy + 0.5 * (x + y) - z;

  // The shadow: the floor rectangle, the same rectangle pushed by the height, and the bands
  // between their edges, all one red.
  const sx = -0.18 * h;
  const sy = 0.9 * h;
  const floor = [0, 0, length, 0, length, d, 0, d];
  const pushed = floor.map((v, i) => v + (i % 2 === 0 ? sx : sy));
  const toScreen = (pts: number[]) => {
    const out: number[] = [];
    for (let i = 0; i < pts.length; i += 2) {
      out.push(px(pts[i], pts[i + 1]), py(pts[i], pts[i + 1], 0));
    }
    return out;
  };
  const a = toScreen(floor);
  const b = toScreen(pushed);
  quad(c, a, C.shadow);
  quad(c, b, C.shadow);
  for (let i = 0; i < 4; i++) {
    const j = (i + 1) % 4;
    quad(c, [a[2 * i], a[2 * i + 1], a[2 * j], a[2 * j + 1], b[2 * j], b[2 * j + 1], b[2 * i], b[2 * i + 1]], C.shadow);
  }

  // The block: its +y face (left), +x face (right) and top.
  quad(c, [px(0, d), py(0, d, 0), px(length, d), py(length, d, 0), px(length, d), py(length, d, h), px(0, d), py(0, d, h)], C.left);
  quad(c, [px(length, 0), py(length, 0, 0), px(length, d), py(length, d, 0), px(length, d), py(length, d, h), px(length, 0), py(length, 0, h)], C.right);
  quad(c, [px(0, 0), py(0, 0, h), px(length, 0), py(length, 0, h), px(length, d), py(length, d, h), px(0, d), py(0, d, h)], C.top);
}

type Props = {
  title?: string;
  subtitle?: string;
  action?: string;
  /** Builds and folds the block by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function IsoFoldStorage({
  title = 'More room for\nyour memories',
  subtitle = 'Vault keeps every photo, file and note in sync.',
  action = 'Continue',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [BLACK_FAMILY]: BLACK, [SEMIBOLD_FAMILY]: SEMIBOLD });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  // The block's back corner: placed so the Max wall and its shadow sit centred.
  const ox = width / 2 - 34;
  const oy = height * 0.45;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const stage = useSharedValue(reduced ? 1 : 0);
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
      if (since < SNAP) stage.set(from.get() + (snapTo.get() - from.get()) * easeInOutCubic(since / SNAP));
      else if (since < SNAP + HOLD) stage.set(snapTo.get());
      else if (since < SNAP + HOLD + RETURN) stage.set(snapTo.get() * (1 - easeInOutCubic((since - SNAP - HOLD) / RETURN)));
      else {
        stage.set(0);
        manual.set(false);
        origin.set(now);
      }
      return;
    }
    stage.set(autoPlay ? stageAt((now - origin.get()) % LOOP) : 0);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawBlock(canvas, ox, oy, stage.get());
    return recorder.finishRecordingAsPicture();
  }, [width, height, ox, oy]);

  // Each reads `stage` itself, so the style follows it.
  const lite = useAnimatedStyle(() => planStyle(stage.get(), 0));
  const plus = useAnimatedStyle(() => planStyle(stage.get(), 1));
  const max = useAnimatedStyle(() => planStyle(stage.get(), 2));
  const planStyles = [lite, plus, max];
  const segment = (width - 48 - 8) / 3;
  const thumbStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: Math.min(2, Math.max(0, stage.get())) * segment }],
  }));

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(4)
    .onBegin(() => {
      touching.set(true);
      manual.set(true);
      grabbed.set(stage.get());
      lastStep.set(Math.round(stage.get()));
    })
    .onUpdate((e) => {
      const s = Math.min(2.15, Math.max(-0.15, grabbed.get() + e.translationX / PER_STAGE));
      stage.set(s);
      const step = Math.round(Math.min(2, Math.max(0, s)));
      if (step !== lastStep.get()) {
        lastStep.set(step);
        scheduleOnRN(haptic);
      }
    })
    .onFinalize(() => {
      touching.set(false);
      from.set(stage.get());
      snapTo.set(Math.round(Math.min(2, Math.max(0, stage.get()))));
      released.set(elapsed.get());
    });

  if (!fontsLoaded) return <View style={styles.screen} />;

  const readoutTop = height * 0.645;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="Storage block. Drag sideways to change the plan.">
            <Picture picture={picture} />
          </Canvas>
          <View style={[styles.head, { top: insets.top + 16 }]} pointerEvents="none">
            <Text style={styles.brand}>VAULT</Text>
            <Text style={styles.title}>{title}</Text>
            <Text style={styles.subtitle}>{subtitle}</Text>
          </View>
          <View style={[styles.readout, { top: readoutTop }]} pointerEvents="none">
            {PLANS.map((plan, k) => (
              <Animated.View key={plan.name} style={[styles.plan, planStyles[k]]}>
                <Text style={styles.size}>{plan.size}</Text>
                <Text style={styles.price}>{plan.price}</Text>
              </Animated.View>
            ))}
          </View>
        </View>
      </GestureDetector>

      <View style={[styles.footer, { paddingBottom: insets.bottom + 16 }]}>
        <View style={styles.track}>
          <Animated.View style={[styles.thumb, { width: segment }, thumbStyle]} />
          {PLANS.map((plan) => (
            <View key={plan.name} style={[styles.option, { width: segment }]}>
              <Text style={styles.optionText}>{plan.name}</Text>
            </View>
          ))}
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
  head: { position: 'absolute', left: 24, right: 24 },
  brand: { fontFamily: BLACK_FAMILY, fontSize: 14, letterSpacing: 2, color: C.ink },
  title: { marginTop: 16, fontFamily: BLACK_FAMILY, fontSize: 32, lineHeight: 37, color: C.ink, letterSpacing: -1 },
  subtitle: { marginTop: 10, fontFamily: SEMIBOLD_FAMILY, fontSize: 15, lineHeight: 21, color: C.soft },
  readout: { position: 'absolute', left: 0, right: 0, height: 86 },
  plan: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  size: { fontFamily: BLACK_FAMILY, fontSize: 54, lineHeight: 62, color: C.ink, letterSpacing: -2 },
  price: { marginTop: 2, fontFamily: SEMIBOLD_FAMILY, fontSize: 16, lineHeight: 21, color: C.soft },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0 },
  track: { height: 48, borderRadius: 24, backgroundColor: C.track, padding: 4, flexDirection: 'row' },
  thumb: { position: 'absolute', top: 4, left: 4, height: 40, borderRadius: 20, backgroundColor: C.thumb },
  option: { height: 40, alignItems: 'center', justifyContent: 'center' },
  optionText: { fontFamily: SEMIBOLD_FAMILY, fontSize: 15, color: C.ink },
  button: { marginTop: 12, height: 54, borderRadius: 27, backgroundColor: C.ink, alignItems: 'center', justifyContent: 'center' },
  action: { fontFamily: SEMIBOLD_FAMILY, fontSize: 17, color: '#FFFFFF' },
});
