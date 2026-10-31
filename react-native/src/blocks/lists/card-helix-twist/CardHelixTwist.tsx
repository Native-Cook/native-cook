import { Canvas, PaintStyle, Picture, Skia, TileMode, useFont, type SkCanvas, type SkFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The collection screen of an invented art-print app, Holo: fourteen holographic print cards lie
// in a neat stack on a dark violet stage; then the stack winds up into a helix — each card turned
// a little more than the one below it around the vertical axis and lifted along it — the whole
// ribbon makes one full turn in 3D, showing the cards' iridescent backs as they swing past, and
// unwinds into the stack again. Drag sideways to wind the ribbon yourself.
//
// Cards are drawn in code (gradient art, blobs, title, number), 150 x 210 pt; each is mapped onto
// its projected corners with a homography (focal 900 pt, the stage seen 10 deg from above).
//
// autoPlay timeline (LOOP = 8 s), t in seconds; twist w (rad between neighbours), lift (pt):
//   0.0-1.0  rest: the stack (w = 0, cards 3 pt apart)
//   1.0-3.4  w 0 -> 0.42, lift 3 -> 16 pt (ease-in-out cubic)
//   1.0-6.8  the ribbon turns once (2 pi, ease-in-out sine)
//   4.6-6.8  w and lift back (ease-in-out cubic)
//   6.8-8.0  rest, identical to t = 0
// Dragging: w = 0.42 clamp(dx / 220) and the turn follows the finger; 2 s after release both
// ease back over 0.9 s and the loop starts again.

/** Seconds. */
export const LOOP = 8;
const N = 14;
const CW = 150;
const CH = 210;
const FOCAL = 900;
const TILT = (-10 * Math.PI) / 180;
const PEAK = 0.42;
const HOLD = 2;
const RETURN = 0.9;

const HUES = [
  ['#FF6FD8', '#3813C2'],
  ['#21D4FD', '#B721FF'],
  ['#FAD961', '#F76B1C'],
  ['#43E97B', '#38F9D7'],
  ['#FA709A', '#FEE140'],
  ['#4FACFE', '#00F2FE'],
  ['#A18CD1', '#FBC2EB'],
];
const TITLES = ['Prism', 'Halo', 'Ember', 'Verdant', 'Bloom', 'Tidal', 'Lilac'];

const ARCHIVO = require('./assets/ArchivoExpanded-Black.ttf');
const BLACK = 'CardHelixTwist-ArchivoExpandedBlack';

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

/** Twist, lift and turn `t` seconds into the loop. */
function helixAt(t: number) {
  'worklet';
  const wind = easeInOutCubic(seg(t, 1.0, 3.4)) * (1 - easeInOutCubic(seg(t, 4.6, 6.8)));
  return { twist: PEAK * wind, lift: 3 + 13 * wind, turn: Math.PI * 2 * easeInOutSine(seg(t, 1.0, 6.8)) };
}

function project(x: number, y: number, z: number, cx: number, cy: number) {
  'worklet';
  const ct = Math.cos(TILT);
  const st = Math.sin(TILT);
  const y1 = y * ct - z * st;
  const z1 = y * st + z * ct;
  const s = FOCAL / (FOCAL - z1);
  return { x: cx + x * s, y: cy + y1 * s, z: z1 };
}

/** The 3x3 matrix (row-major) mapping the w x h rectangle onto the quad p (TL TR BR BL). */
function quadMatrix(w: number, h: number, p: number[]) {
  'worklet';
  const dx1 = p[2] - p[4];
  const dx2 = p[6] - p[4];
  const dx3 = p[0] - p[2] + p[4] - p[6];
  const dy1 = p[3] - p[5];
  const dy2 = p[7] - p[5];
  const dy3 = p[1] - p[3] + p[5] - p[7];
  const den = dx1 * dy2 - dx2 * dy1;
  const g = den === 0 ? 0 : (dx3 * dy2 - dx2 * dy3) / den;
  const k = den === 0 ? 0 : (dx1 * dy3 - dx3 * dy1) / den;
  const a = p[2] - p[0] + g * p[2];
  const b = p[6] - p[0] + k * p[6];
  const d = p[3] - p[1] + g * p[3];
  const e = p[7] - p[1] + k * p[7];
  return [a / w, b / h, p[0], d / w, e / h, p[1], g / w, k / h, 1];
}

function drawFront(c: SkCanvas, j: number, font: SkFont) {
  'worklet';
  const hue = HUES[j % HUES.length];
  const card = Skia.RRectXY(Skia.XYWHRect(0, 0, CW, CH), 14, 14);
  const bg = Skia.Paint();
  bg.setAntiAlias(true);
  bg.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: CW, y: CH }, [Skia.Color(hue[0]), Skia.Color(hue[1])], null, TileMode.Clamp));
  c.drawRRect(card, bg);
  c.save();
  c.clipRRect(card, 1, true);
  const blob = Skia.Paint();
  blob.setAntiAlias(true);
  blob.setShader(Skia.Shader.MakeRadialGradient({ x: CW * 0.7, y: CH * 0.32 }, 70, [Skia.Color('rgba(255,255,255,0.75)'), Skia.Color('rgba(255,255,255,0)')], null, TileMode.Clamp));
  c.drawCircle(CW * 0.7, CH * 0.32, 70, blob);
  const ring = Skia.Paint();
  ring.setAntiAlias(true);
  ring.setStyle(PaintStyle.Stroke);
  ring.setStrokeWidth(10);
  ring.setColor(Skia.Color('rgba(255,255,255,0.35)'));
  c.drawCircle(CW * 0.3, CH * 0.62, 38 + (j % 3) * 8, ring);
  c.restore();
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(Skia.Color('#FFFFFF'));
  c.save();
  c.translate(12, CH - 30);
  c.scale(0.16, 0.16);
  c.drawText(TITLES[j % TITLES.length].toUpperCase(), 0, 0, ink, font);
  c.restore();
  c.save();
  c.translate(12, CH - 14);
  c.scale(0.09, 0.09);
  c.drawText(`No. ${String(421 + j * 37).padStart(4, '0')}`, 0, 0, ink, font);
  c.restore();
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1.5);
  edge.setColor(Skia.Color('rgba(255,255,255,0.55)'));
  c.drawRRect(card, edge);
}

function drawBack(c: SkCanvas, j: number, turn: number) {
  'worklet';
  const card = Skia.RRectXY(Skia.XYWHRect(0, 0, CW, CH), 14, 14);
  const bg = Skia.Paint();
  bg.setAntiAlias(true);
  // An iridescent foil whose sheen slides with the turn.
  const s = (turn * 0.5 + j * 0.2) % 1;
  bg.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: -CW + s * CW * 2, y: 0 },
      { x: s * CW * 2, y: CH },
      [Skia.Color('#1B1530'), Skia.Color('#7C5CFF'), Skia.Color('#38F9D7'), Skia.Color('#FF6FD8'), Skia.Color('#1B1530')],
      [0, 0.3, 0.5, 0.7, 1],
      TileMode.Mirror,
    ),
  );
  c.drawRRect(card, bg);
  const mark = Skia.Paint();
  mark.setAntiAlias(true);
  mark.setStyle(PaintStyle.Stroke);
  mark.setStrokeWidth(2);
  mark.setColor(Skia.Color('rgba(255,255,255,0.6)'));
  c.drawCircle(CW / 2, CH / 2, 26, mark);
  c.drawCircle(CW / 2, CH / 2, 14, mark);
}

function drawHelix(c: SkCanvas, twist: number, lift: number, turn: number, cx: number, cy: number, font: SkFont) {
  'worklet';
  const items: { j: number; z: number; quad: number[]; facing: boolean }[] = [];
  for (let j = 0; j < N; j++) {
    const th = turn + twist * (j - (N - 1) / 2);
    const ct = Math.cos(th);
    const st = Math.sin(th);
    const yc = ((N - 1) / 2 - j) * lift;
    const corners = [
      [-CW / 2, -CH / 2],
      [CW / 2, -CH / 2],
      [CW / 2, CH / 2],
      [-CW / 2, CH / 2],
    ];
    const quad: number[] = [];
    let z = 0;
    for (let q = 0; q < 4; q++) {
      const x = corners[q][0] * ct;
      const zz = -corners[q][0] * st - j * 0.6;
      const pr = project(x, yc + corners[q][1], zz, cx, cy);
      quad.push(pr.x, pr.y);
      z += pr.z / 4;
    }
    const cross = (quad[2] - quad[0]) * (quad[7] - quad[1]) - (quad[3] - quad[1]) * (quad[6] - quad[0]);
    items.push({ j, z: z - j * 0.01, quad, facing: cross > 0 });
  }
  // Lower cards first (they are further back in the stack), then by depth.
  items.sort((a, b) => a.z - b.z);
  for (let n = 0; n < items.length; n++) {
    const it = items[n];
    c.save();
    c.concat(quadMatrix(CW, CH, it.quad));
    if (it.facing) drawFront(c, it.j, font);
    else drawBack(c, it.j, turn);
    c.restore();
  }
}

type Props = {
  brand?: string;
  title?: string;
  action?: string;
  /** Winds the cards by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function CardHelixTwist({ brand = 'Holo', title = 'Spring prints', action = 'Open collection', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [BLACK]: ARCHIVO });
  const font = useFont(ARCHIVO, 100);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.52;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const twist = useSharedValue(0);
  const lift = useSharedValue(3);
  const turn = useSharedValue(0);
  const touching = useSharedValue(false);
  const manual = useSharedValue(false);
  const released = useSharedValue(-1);
  const userW = useSharedValue(0);
  const userTurn = useSharedValue(0);
  const fromTurn = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    if (touching.get() || manual.get()) {
      let k = 0;
      if (!touching.get()) {
        const since = now - released.get();
        k = easeInOutCubic(clamp01((since - HOLD) / RETURN));
        if (since > HOLD + RETURN) {
          manual.set(false);
          origin.set(now);
        }
      }
      const w = userW.get() * (1 - k);
      twist.set(w);
      lift.set(3 + 13 * (Math.abs(w) / PEAK));
      // Turn back to the nearest whole turn.
      const tgt = Math.round(fromTurn.get() / (Math.PI * 2)) * Math.PI * 2;
      turn.set(touching.get() ? userTurn.get() : fromTurn.get() + (tgt - fromTurn.get()) * k);
      return;
    }
    const h = autoPlay ? helixAt((now - origin.get()) % LOOP) : { twist: 0, lift: 3, turn: 0 };
    twist.set(h.twist);
    lift.set(h.lift);
    turn.set(h.turn);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const glow = Skia.Paint();
    glow.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, 260, [Skia.Color('rgba(124,92,255,0.35)'), Skia.Color('rgba(124,92,255,0)')], null, TileMode.Clamp));
    canvas.drawCircle(cx, cy, 260, glow);
    if (font) drawHelix(canvas, twist.get(), lift.get(), turn.get(), cx, cy, font);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, font]);

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
      userTurn.set(turn.get());
      fromTurn.set(turn.get());
      scheduleOnRN(haptic);
    })
    .onUpdate((e) => {
      userW.set(PEAK * Math.max(-1, Math.min(1, e.translationX / 220)));
      userTurn.set(fromTurn.get() + e.translationX / 120);
    })
    .onFinalize(() => {
      touching.set(false);
      fromTurn.set(userTurn.get());
      released.set(elapsed.get());
    });

  if (!fontsLoaded) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <View style={[styles.head, { top: insets.top + 8 }]} pointerEvents="none">
            <View style={styles.row}>
              <Text style={styles.brand}>{brand}</Text>
              <Text style={styles.count}>14 cards</Text>
            </View>
            <Text style={styles.title}>{title}</Text>
            <Text style={styles.sub}>Seven artists, two of each, printed on holographic foil.</Text>
          </View>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="A stack of print cards winding into a helix. Drag to wind it.">
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]}>
        <View style={styles.stats}>
          <View>
            <Text style={styles.statValue}>2 / 7</Text>
            <Text style={styles.statLabel}>Sets complete</Text>
          </View>
          <View>
            <Text style={styles.statValue}>Rare</Text>
            <Text style={styles.statLabel}>Next pull</Text>
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
  screen: { flex: 1, backgroundColor: '#0B0A12' },
  head: { position: 'absolute', left: 24, right: 24 },
  row: { height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontFamily: BLACK, fontSize: 20, color: '#FFFFFF' },
  count: { fontSize: 13, fontWeight: '600', color: 'rgba(255,255,255,0.55)' },
  title: { marginTop: 14, fontFamily: BLACK, fontSize: 34, lineHeight: 40, color: '#FFFFFF', letterSpacing: -1 },
  sub: { marginTop: 6, fontSize: 15, lineHeight: 20, color: 'rgba(255,255,255,0.6)' },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0 },
  stats: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 16 },
  statValue: { fontFamily: BLACK, fontSize: 20, color: '#FFFFFF' },
  statLabel: { marginTop: 2, fontSize: 13, color: 'rgba(255,255,255,0.55)' },
  button: { height: 58, borderRadius: 29, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center' },
  action: { fontFamily: BLACK, fontSize: 15, color: '#0B0A12' },
});
