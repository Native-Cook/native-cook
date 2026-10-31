import { BlurStyle, Canvas, PaintStyle, Picture, Skia, StrokeCap, TileMode, type SkCanvas } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The first screen of an invented assistant app, Halo. Eight dark glass tiles with neon icons
// (photo, eye, note, search, spark, squiggle, heart, chat) circle in a small ring over a violet
// glow; the ring swells and tips towards you until the tiles sweep past the edges of the screen
// in 3D round the words "Any idea", then "Any need"; then everything is drawn into one tile in
// the middle whose three dots think for a moment and turn into an arrow — the start button —
// before it sinks back and the ring opens out again. Drag sideways to spin the ring.
//
// Tiles and icons are drawn in code: rounded squares with a neon gradient rim and inner glow,
// icons as gradient SVG paths in a 64 pt box.
// Layout (393 x 852 pt): 34 pt top row; ring centred at 45 % of the height, 8 tiles of 68 pt on
// radius 92 (small) or 250 (big, tipped 68 deg) with perspective (focal 520); the centre tile 92
// pt; a heading, a line and a 58 pt button at the bottom.
//
// autoPlay timeline (LOOP = 9 s); spin = 2 pi t / LOOP (one turn a loop):
//   0.0-1.2  rest: small ring
//   1.2-2.4  the ring swells to 250 and tips (ease-in-out cubic); "Any idea" fades in
//   2.4-4.2  the big ring turns round the words ("Any need" from 3.3)
//   4.2-5.1  the tiles are drawn into the middle (ease-in cubic) and fade; the centre tile grows in
//            (ease-out back) with a blue glow
//   5.1-6.2  its three dots bounce (0.36 s apart)
//   6.2-6.6  the dots fold into an arrow
//   6.6-7.8  hold
//   7.8-9.0  the centre tile sinks away and the small ring opens out again (ease-out cubic)
// Dragging spins the ring with the finger; 2 s after release it carries on with the loop.

/** Seconds. */
export const LOOP = 9;
const TILE = 68;
const SMALL_R = 92;
const BIG_R = 250;
const FOCAL = 520;
const HOLD = 2;

const ICONS = [
  { kind: 'photo', a: '#3CF0B4', b: '#2D7BFF', rim: ['#8A5CFF', '#3DA2FF'] },
  { kind: 'eye', a: '#E9FFF6', b: '#47C9FF', rim: ['#6E7BFF', '#C05CFF'] },
  { kind: 'note', a: '#FFE15A', b: '#FF6AD5', rim: ['#7A5CFF', '#3DA2FF'] },
  { kind: 'search', a: '#4DF5C8', b: '#2D9BFF', rim: ['#3DA2FF', '#8A5CFF'] },
  { kind: 'spark', a: '#FF7BD8', b: '#4D7BFF', rim: ['#C05CFF', '#3DA2FF'] },
  { kind: 'squiggle', a: '#FFB03A', b: '#4D6BFF', rim: ['#8A5CFF', '#FF8A3D'] },
  { kind: 'heart', a: '#FF8AD0', b: '#8A6BFF', rim: ['#FF8A3D', '#8A5CFF'] },
  { kind: 'chat', a: '#C9B8FF', b: '#6B4DFF', rim: ['#FF8A3D', '#6E7BFF'] },
];

const PATHS: Record<string, { d: string; stroke: boolean }[]> = {
  photo: [
    { d: 'M18 18 H46 A6 6 0 0 1 52 24 V42 A6 6 0 0 1 46 48 H18 A6 6 0 0 1 12 42 V24 A6 6 0 0 1 18 18 Z', stroke: true },
    { d: 'M14 46 L27 32 L35 40 L41 34 L50 46 Z', stroke: false },
    { d: 'M44 27 A4 4 0 1 1 36 27 A4 4 0 1 1 44 27 Z', stroke: false },
  ],
  eye: [
    { d: 'M10 32 C19 19 45 19 54 32 C45 45 19 45 10 32 Z', stroke: false },
    { d: 'M40 32 A8 8 0 1 1 24 32 A8 8 0 1 1 40 32 Z', stroke: false },
  ],
  note: [
    { d: 'M27 44 V20 L45 16 V40', stroke: true },
    { d: 'M28 44 A6 6 0 1 1 16 44 A6 6 0 1 1 28 44 Z', stroke: false },
    { d: 'M46 40 A6 6 0 1 1 34 40 A6 6 0 1 1 46 40 Z', stroke: false },
  ],
  search: [
    { d: 'M41 29 A12 12 0 1 1 17 29 A12 12 0 1 1 41 29 Z', stroke: true },
    { d: 'M38 38 L48 48', stroke: true },
  ],
  spark: [{ d: 'M32 10 C34 26 38 30 54 32 C38 34 34 38 32 54 C30 38 26 34 10 32 C26 30 30 26 32 10 Z', stroke: false }],
  squiggle: [{ d: 'M13 38 C19 20 26 21 28 34 C30 47 37 46 40 32 C43 23 48 25 51 31', stroke: true }],
  heart: [{ d: 'M32 49 C13 36 13 19 24 19 C28 19 31 22 32 26 C33 22 36 19 40 19 C51 19 51 36 32 49 Z', stroke: false }],
  chat: [{ d: 'M14 22 A6 6 0 0 1 20 16 H44 A6 6 0 0 1 50 22 V36 A6 6 0 0 1 44 42 H30 L22 49 V42 H20 A6 6 0 0 1 14 36 Z', stroke: false }],
};

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
function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}
function easeOutBack(x: number) {
  'worklet';
  const k = 1.6;
  return 1 + (k + 1) * Math.pow(x - 1, 3) + k * Math.pow(x - 1, 2);
}

/** Ring radius and tilt, gather (tiles drawn in), centre tile size and its dots/arrow state. */
function stageAt(t: number) {
  'worklet';
  const swell = easeInOutCubic(seg(t, 1.2, 2.4));
  const gather = Math.pow(seg(t, 4.2, 5.1), 3);
  const reopen = easeOutCubic(seg(t, 7.8, 9.0));
  const radius = gather < 1 ? SMALL_R + (BIG_R - SMALL_R) * swell * (1 - gather) - SMALL_R * gather : SMALL_R * reopen;
  const tilt = 68 * swell * (1 - gather);
  const ringAlpha = gather < 1 ? 1 - gather : reopen;
  const centre = easeOutBack(seg(t, 4.5, 5.1)) * (1 - easeInOutCubic(seg(t, 7.8, 8.5)));
  const arrow = easeInOutCubic(seg(t, 6.2, 6.6));
  return { radius, tilt, ringAlpha, centre, arrow };
}

function rimPaint(x: number, y: number, s: number, rim: string[], alpha: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(1.6);
  p.setAlphaf(alpha);
  p.setShader(Skia.Shader.MakeLinearGradient({ x, y }, { x: x + s, y: y + s }, [Skia.Color(rim[0]), Skia.Color('rgba(255,255,255,0.15)'), Skia.Color(rim[1])], [0, 0.5, 1], TileMode.Clamp));
  return p;
}

/** A glass tile of size s with its top-left at (x, y). */
function drawTileBody(c: SkCanvas, x: number, y: number, s: number, rim: string[], alpha: number) {
  'worklet';
  const rr = Skia.RRectXY(Skia.XYWHRect(x, y, s, s), s * 0.28, s * 0.28);
  const glow = Skia.Paint();
  glow.setAntiAlias(true);
  glow.setColor(Skia.Color(rim[0]));
  glow.setAlphaf(0.35 * alpha);
  glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, s * 0.12, true));
  c.drawRRect(rr, glow);
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setAlphaf(alpha);
  body.setShader(Skia.Shader.MakeLinearGradient({ x, y }, { x, y: y + s }, [Skia.Color('#1A1826'), Skia.Color('#07070C')], null, TileMode.Clamp));
  c.drawRRect(rr, body);
  const inner = Skia.Paint();
  inner.setAntiAlias(true);
  inner.setAlphaf(alpha * 0.5);
  inner.setShader(Skia.Shader.MakeRadialGradient({ x: x + s * 0.5, y: y + s * 1.1 }, s * 0.7, [Skia.Color(rim[1]), Skia.Color('rgba(0,0,0,0)')], null, TileMode.Clamp));
  c.drawRRect(rr, inner);
  c.drawRRect(rr, rimPaint(x, y, s, rim, alpha));
}

function drawIcon(c: SkCanvas, k: number, x: number, y: number, s: number, alpha: number) {
  'worklet';
  const icon = ICONS[k];
  drawTileBody(c, x, y, s, icon.rim, alpha);
  const parts = PATHS[icon.kind];
  c.save();
  c.translate(x, y);
  c.scale(s / 64, s / 64);
  for (let n = 0; n < parts.length; n++) {
    const path = Skia.Path.MakeFromSVGString(parts[n].d);
    if (!path) continue;
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setAlphaf(alpha);
    p.setShader(Skia.Shader.MakeLinearGradient({ x: 12, y: 12 }, { x: 52, y: 52 }, [Skia.Color(icon.a), Skia.Color(icon.b)], null, TileMode.Clamp));
    if (parts[n].stroke) {
      p.setStyle(PaintStyle.Stroke);
      p.setStrokeWidth(5);
      p.setStrokeCap(StrokeCap.Round);
    }
    // The eye's pupil is dark.
    if (icon.kind === 'eye' && n === 1) {
      p.setShader(null);
      p.setColor(Skia.Color('#0A0A12'));
      p.setAlphaf(alpha);
    }
    c.drawPath(path, p);
  }
  c.restore();
}

function drawCentre(c: SkCanvas, cx: number, cy: number, size: number, arrow: number, t: number) {
  'worklet';
  if (size <= 0.01) return;
  const s = 92 * size;
  const halo = Skia.Paint();
  halo.setAntiAlias(true);
  halo.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, s * 1.6, [Skia.Color('rgba(70,60,255,0.7)'), Skia.Color('rgba(70,60,255,0)')], null, TileMode.Clamp));
  c.drawCircle(cx, cy, s * 1.6, halo);
  drawTileBody(c, cx - s / 2, cy - s / 2, s, ['#FF8A3D', '#5B5CFF'], 1);
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(Skia.Color('#FFFFFF'));
  const k = s / 92;
  if (arrow < 1) {
    for (let i = 0; i < 3; i++) {
      const bounce = t > 5.1 && t < 6.2 ? Math.max(0, Math.sin((t - 5.1 - i * 0.12) * Math.PI * 2.8)) * 7 : 0;
      // Folding into the arrow: dots slide to the arrow's tips and fade.
      const x = cx + (i - 1) * 16 * k * (1 - arrow);
      const y = cy + 6 * k - bounce * k - (i === 1 ? 18 * arrow * k : 0);
      ink.setAlphaf(1 - arrow);
      c.drawCircle(x, y, 4.2 * k, ink);
    }
  }
  if (arrow > 0) {
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(5 * k);
    p.setStrokeCap(StrokeCap.Round);
    p.setColor(Skia.Color('#FFFFFF'));
    p.setAlphaf(arrow);
    const path = Skia.Path.Make();
    path.moveTo(cx, cy + 20 * k);
    path.lineTo(cx, cy - 20 * k);
    path.moveTo(cx - 16 * k * arrow, cy - 4 * k);
    path.lineTo(cx, cy - 20 * k);
    path.lineTo(cx + 16 * k * arrow, cy - 4 * k);
    c.drawPath(path, p);
  }
}

function drawOrbit(c: SkCanvas, t: number, spin: number, cx: number, cy: number) {
  'worklet';
  const st = stageAt(t);
  // Violet glow behind the ring.
  const g = Skia.Paint();
  g.setAntiAlias(true);
  const gr = 150 + st.radius * 0.6;
  g.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, gr, [Skia.Color('rgba(80,50,255,0.55)'), Skia.Color('rgba(40,20,160,0.18)'), Skia.Color('rgba(0,0,0,0)')], [0, 0.55, 1], TileMode.Clamp));
  c.drawCircle(cx, cy, gr, g);
  if (st.ringAlpha > 0.01) {
    const tilt = (st.tilt * Math.PI) / 180;
    const items: { k: number; x: number; y: number; s: number; z: number }[] = [];
    for (let k = 0; k < ICONS.length; k++) {
      const th = spin + (k * Math.PI * 2) / ICONS.length;
      const x = st.radius * Math.cos(th);
      const y0 = st.radius * Math.sin(th);
      // Tip the ring about the x axis towards the viewer.
      const y = y0 * Math.cos(tilt);
      const z = y0 * Math.sin(tilt);
      const sc = FOCAL / (FOCAL - z);
      items.push({ k, x: cx + x * sc, y: cy + y * sc, s: TILE * sc, z });
    }
    items.sort((a, b) => a.z - b.z);
    for (let n = 0; n < items.length; n++) {
      const it = items[n];
      drawIcon(c, it.k, it.x - it.s / 2, it.y - it.s / 2, it.s, st.ringAlpha);
    }
  }
  drawCentre(c, cx, cy, st.centre, st.arrow, t);
}

type Props = {
  brand?: string;
  action?: string;
  /** Plays the intro by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function GlassIconOrbit({ brand = 'halo', action = 'Get started', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.45;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const clock = useSharedValue(reduced ? 7 : 0);
  const spin = useSharedValue(0);
  const extra = useSharedValue(0);
  const touching = useSharedValue(false);
  const released = useSharedValue(-10);
  const paused = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    // While touched (and for 2 s after) the loop clock stands still and the finger spins the ring.
    if (touching.get() || now - released.get() < HOLD) {
      origin.set(origin.get() + dt);
      paused.set(1);
    } else if (paused.get() > 0) {
      // Unwind the extra spin over 0.6 s so the loop picks up exactly.
      extra.set(extra.get() * Math.exp(-6 * dt));
      if (Math.abs(extra.get()) < 0.002) {
        extra.set(0);
        paused.set(0);
      }
    }
    const t = autoPlay ? (((now - origin.get()) % LOOP) + LOOP) % LOOP : 0;
    clock.set(t);
    spin.set((Math.PI * 2 * t) / LOOP + extra.get());
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawOrbit(canvas, clock.get(), spin.get(), cx, cy);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy]);

  const ideaStyle = useAnimatedStyle(() => {
    const t = clock.get();
    return { opacity: seg(t, 1.6, 2.2) * (1 - seg(t, 3.1, 3.4)) };
  });
  const needStyle = useAnimatedStyle(() => {
    const t = clock.get();
    return { opacity: seg(t, 3.3, 3.6) * (1 - seg(t, 4.2, 4.6)) };
  });

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.selectionAsync().catch(() => {});
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(4)
    .onStart(() => {
      touching.set(true);
      scheduleOnRN(haptic);
    })
    .onChange((e) => extra.set(extra.get() + e.changeX / 120))
    .onFinalize(() => {
      touching.set(false);
      released.set(elapsed.get());
    });

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="A ring of app icons. Drag to spin it.">
            <Picture picture={picture} />
          </Canvas>
          <View style={[styles.words, { top: cy - 24 }]} pointerEvents="none">
            <Animated.Text style={[styles.word, ideaStyle]}>Any idea</Animated.Text>
            <Animated.Text style={[styles.word, styles.over, needStyle]}>Any need</Animated.Text>
          </View>
          <View style={[styles.top, { top: insets.top + 8 }]} pointerEvents="none">
            <Text style={styles.brand}>{brand}</Text>
            <Text style={styles.skip}>Skip</Text>
          </View>
        </View>
      </GestureDetector>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]}>
        <Text style={styles.heading}>One assistant for all of it</Text>
        <Text style={styles.sub}>Photos, notes, songs and chats — ask Halo and it finds, writes or plans.</Text>
        <View style={styles.button} accessible accessibilityRole="button">
          <Text style={styles.action}>{action}</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#000000' },
  top: { position: 'absolute', left: 24, right: 24, height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontSize: 20, fontWeight: '800', color: '#FFFFFF', letterSpacing: -0.5 },
  skip: { fontSize: 15, fontWeight: '600', color: 'rgba(255,255,255,0.55)' },
  words: { position: 'absolute', left: 0, right: 0, height: 48, alignItems: 'center', justifyContent: 'center' },
  word: { fontSize: 38, fontWeight: '600', color: '#FFFFFF', letterSpacing: -0.8 },
  over: { position: 'absolute' },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0 },
  heading: { fontSize: 28, lineHeight: 32, fontWeight: '800', color: '#FFFFFF', letterSpacing: -0.8 },
  sub: { marginTop: 8, fontSize: 16, lineHeight: 22, color: 'rgba(255,255,255,0.6)' },
  button: { marginTop: 20, height: 58, borderRadius: 29, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center' },
  action: { fontSize: 17, fontWeight: '700', color: '#000000' },
});
