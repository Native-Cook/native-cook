import { Canvas, PaintStyle, Picture, Skia, StrokeCap, TileMode, type SkCanvas, type SkPath } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The token wall of an invented arcade, Token Hall: a long row of chunky metal-rimmed tokens
// stands in grey slots, each turned 66 deg so they read as a tight run of coloured ovals, and the
// row slides slowly past. Every 1.5 seconds the token in the middle pops up out of its slot,
// turns to face you — star, bolt, leaf, heart, moon or crown — hangs for a moment, and turns away
// as it sinks back in. Its name and count show under the title. Drag sideways to slide the row;
// tap to pop the token in the middle.
//
// Tokens are drawn in code: a silver rim (linear gradient) with its edge, a coloured face and a
// white icon, all squeezed horizontally by cos(yaw).
// Layout (393 x 852 pt): 34 pt top row; title 36 pt heavy; slots' top at 64 % of the height,
// 150 pt tall; tokens radius 64 pt, 36 pt apart, centres 30 pt above the slots; a popped token
// rises 150 pt and grows to 1.3x; name card and a 58 pt button at the bottom.
//
// autoPlay timeline (LOOP = 9 s): the row slides one token per 1.5 s (24 pt/s, linear), six per
// loop, so the frame repeats. Six pops, one per token, each 1.5 s: 0.0-0.4 rise and turn to face
// (ease-out back), 0.4-0.9 hold with a 2 deg wobble, 0.9-1.3 sink and turn away (ease-in-out
// cubic), 1.3-1.5 rest.
// Dragging slides the row with the finger; a tap pops the middle token; 2 s after the last touch
// the loop carries on.

/** Seconds. */
export const LOOP = 9;
const POP = 1.5;
const SLIDE = 6;
const GAP = 36;
const R = 64;
const YAW = 66;
const RISE = 150;
const HOLD = 2;

type Token = { name: string; face: string; deep: string; icon: string };
const TOKENS: Token[] = [
  { name: 'Star token', face: '#FFB23F', deep: '#E8650F', icon: 'star' },
  { name: 'Bolt token', face: '#5AA8FF', deep: '#1F4FD8', icon: 'bolt' },
  { name: 'Leaf token', face: '#3FD58A', deep: '#0F8A4A', icon: 'leaf' },
  { name: 'Heart token', face: '#FF6A6A', deep: '#C8202E', icon: 'heart' },
  { name: 'Moon token', face: '#A98BFF', deep: '#5B2BD8', icon: 'moon' },
  { name: 'Crown token', face: '#E8C27A', deep: '#A8762A', icon: 'crown' },
];
const COUNTS = [12, 7, 21, 4, 9, 2];

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function easeOutBack(x: number) {
  'worklet';
  const k = 1.4;
  return 1 + (k + 1) * Math.pow(x - 1, 3) + k * Math.pow(x - 1, 2);
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** How far a pop that started `x` seconds ago has risen (0..1+) and wobbles. */
function popAt(x: number) {
  'worklet';
  if (x < 0 || x > 1.3) return { up: 0, wobble: 0 };
  if (x < 0.4) return { up: easeOutBack(x / 0.4), wobble: 0 };
  if (x < 0.9) return { up: 1, wobble: 2 * Math.sin((x - 0.4) * 14) * Math.exp(-(x - 0.4) * 2) };
  return { up: 1 - easeInOutCubic(seg(x, 0.9, 1.3)), wobble: 0 };
}

function iconPath(kind: string): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  if (kind === 'star') {
    for (let i = 0; i < 10; i++) {
      const a = -Math.PI / 2 + (i * Math.PI) / 5;
      const r = i % 2 === 0 ? 1 : 0.45;
      if (i === 0) p.moveTo(Math.cos(a) * r, Math.sin(a) * r);
      else p.lineTo(Math.cos(a) * r, Math.sin(a) * r);
    }
    p.close();
  } else if (kind === 'bolt') {
    p.moveTo(0.2, -1);
    p.lineTo(-0.55, 0.12);
    p.lineTo(-0.02, 0.12);
    p.lineTo(-0.25, 1);
    p.lineTo(0.55, -0.15);
    p.lineTo(0.02, -0.15);
    p.close();
  } else if (kind === 'leaf') {
    p.moveTo(-0.7, 0.75);
    p.cubicTo(-0.9, -0.3, 0.0, -0.95, 0.85, -0.85);
    p.cubicTo(0.95, 0.0, 0.3, 0.9, -0.7, 0.75);
    p.close();
  } else if (kind === 'heart') {
    p.moveTo(0, 0.9);
    p.cubicTo(-1.1, 0.1, -0.9, -0.95, 0, -0.45);
    p.cubicTo(0.9, -0.95, 1.1, 0.1, 0, 0.9);
    p.close();
  } else if (kind === 'moon') {
    p.moveTo(0.2, -0.95);
    p.cubicTo(-0.75, -0.85, -1.0, 0.35, -0.35, 0.8);
    p.cubicTo(0.15, 1.1, 0.75, 0.85, 0.95, 0.35);
    p.cubicTo(0.3, 0.55, -0.35, 0.05, 0.2, -0.95);
    p.close();
  } else {
    p.moveTo(-0.9, 0.6);
    p.lineTo(-0.95, -0.55);
    p.lineTo(-0.45, -0.05);
    p.lineTo(0, -0.8);
    p.lineTo(0.45, -0.05);
    p.lineTo(0.95, -0.55);
    p.lineTo(0.9, 0.6);
    p.close();
  }
  return p;
}

function drawToken(c: SkCanvas, k: number, x: number, y: number, yawDeg: number, scale: number) {
  'worklet';
  const tok = TOKENS[k];
  const yaw = (yawDeg * Math.PI) / 180;
  const sx = Math.max(0.06, Math.cos(yaw));
  const r = R * scale;
  // The rim's edge shows on the side the token turns away from.
  const edge = 14 * scale * Math.sin(yaw);
  const side = Skia.Paint();
  side.setAntiAlias(true);
  side.setShader(Skia.Shader.MakeLinearGradient({ x: x, y: y - r }, { x: x, y: y + r }, [Skia.Color('#8D8F99'), Skia.Color('#3C3E46'), Skia.Color('#A7A9B3')], null, TileMode.Clamp));
  c.save();
  c.translate(x + edge, y);
  c.scale(sx, 1);
  c.drawCircle(0, 0, r, side);
  c.restore();
  c.save();
  c.translate(x, y);
  c.scale(sx, 1);
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setShader(Skia.Shader.MakeLinearGradient({ x: -r, y: -r }, { x: r, y: r }, [Skia.Color('#F4F5F8'), Skia.Color('#9EA1AC'), Skia.Color('#E6E7EC'), Skia.Color('#6E717C')], [0, 0.4, 0.6, 1], TileMode.Clamp));
  c.drawCircle(0, 0, r, rim);
  const face = Skia.Paint();
  face.setAntiAlias(true);
  face.setShader(Skia.Shader.MakeRadialGradient({ x: -r * 0.3, y: -r * 0.35 }, r * 1.3, [Skia.Color(tok.face), Skia.Color(tok.deep)], null, TileMode.Clamp));
  c.drawCircle(0, 0, r * 0.82, face);
  const inner = Skia.Paint();
  inner.setAntiAlias(true);
  inner.setStyle(PaintStyle.Stroke);
  inner.setStrokeWidth(2);
  inner.setColor(Skia.Color('rgba(255,255,255,0.35)'));
  c.drawCircle(0, 0, r * 0.7, inner);
  const ic = Skia.Paint();
  ic.setAntiAlias(true);
  ic.setColor(Skia.Color('#FFFFFF'));
  c.save();
  c.scale(r * 0.38, r * 0.38);
  c.drawPath(iconPath(tok.icon), ic);
  c.restore();
  const gloss = Skia.Paint();
  gloss.setAntiAlias(true);
  gloss.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: -r }, { x: 0, y: 0 }, [Skia.Color('rgba(255,255,255,0.35)'), Skia.Color('rgba(255,255,255,0)')], null, TileMode.Clamp));
  c.drawCircle(0, 0, r * 0.82, gloss);
  c.restore();
}

function drawWall(c: SkCanvas, shift: number, popSlot: number, up: number, wobble: number, width: number, height: number) {
  'worklet';
  const cx = width / 2;
  const top = height * 0.64;
  const baseY = top - 30;
  // Spotlight.
  const spot = Skia.Paint();
  spot.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: top - 60 }, width * 0.8, [Skia.Color('rgba(120,130,170,0.28)'), Skia.Color('rgba(0,0,0,0)')], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, width, height), spot);
  const first = Math.floor((shift - cx - 80) / GAP);
  const last = Math.ceil((shift + cx + 80) / GAP);
  // Right to left, so the tokens on the left sit in front.
  let popped = -1e9;
  for (let j = last; j >= first; j--) {
    const x = cx + j * GAP - shift;
    const k = ((j % 6) + 6) % 6;
    if (j === popSlot && up > 0.001) {
      popped = j;
      continue;
    }
    drawToken(c, k, x, baseY, YAW, 1);
  }
  // Slots: grey blocks in front of the tokens, one per two tokens, with seams.
  const block = Skia.Paint();
  block.setAntiAlias(true);
  block.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: top }, { x: 0, y: top + 150 }, [Skia.Color('#B9BBC3'), Skia.Color('#8A8D96')], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, top, width, 150), block);
  const seam = Skia.Paint();
  seam.setAntiAlias(true);
  seam.setStyle(PaintStyle.Stroke);
  seam.setStrokeWidth(1.5);
  seam.setStrokeCap(StrokeCap.Butt);
  seam.setColor(Skia.Color('rgba(40,42,50,0.35)'));
  for (let j = first; j <= last; j += 1) {
    if (((j % 2) + 2) % 2 !== 0) continue;
    const x = cx + j * GAP - shift - GAP / 2;
    c.drawLine(x, top, x, top + 150, seam);
  }
  const lip = Skia.Paint();
  lip.setColor(Skia.Color('#D9DBE2'));
  c.drawRect(Skia.XYWHRect(0, top, width, 3), lip);
  // The popped token above it all.
  if (popped > -1e9) {
    const x = cx + popped * GAP - shift;
    const k = ((popped % 6) + 6) % 6;
    const lift = Math.max(0, up);
    drawToken(c, k, x, baseY - RISE * up, YAW * (1 - Math.min(1, lift)) + wobble, 1 + 0.3 * Math.min(1.1, lift));
  }
}

function NameCard({ k, shown }: { k: number; shown: SharedValue<number[]> }) {
  const style = useAnimatedStyle(() => {
    const [slot, up] = shown.get();
    const on = ((slot % 6) + 6) % 6 === k ? clamp01(up * 1.4) : 0;
    return { opacity: on, transform: [{ translateY: 10 * (1 - on) }] };
  });
  return (
    <Animated.View style={[styles.nameCard, style]}>
      <View style={[styles.swatch, { backgroundColor: TOKENS[k].face }]} />
      <Text style={styles.tokenName}>{TOKENS[k].name}</Text>
      <Text style={styles.count}>{`× ${COUNTS[k]}`}</Text>
    </Animated.View>
  );
}

type Props = {
  title?: string;
  /** Slides the row and pops tokens by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function DiscRingStack({ title = 'Your token wall', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  // Row shift (pt), the popped slot, its rise and wobble.
  const wall = useSharedValue({ shift: 0, slot: 0, up: 0, wobble: 0 });
  const manual = useSharedValue(false);
  const touching = useSharedValue(false);
  const lastTouch = useSharedValue(-10);
  const grabbed = useSharedValue(0);
  const userShift = useSharedValue(0);
  const userPop = useSharedValue(-10);
  const userSlot = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    if (manual.get()) {
      const x = now - userPop.get();
      const p = popAt(x);
      wall.set({ shift: userShift.get(), slot: userSlot.get(), up: p.up, wobble: p.wobble });
      if (!touching.get() && now - lastTouch.get() > HOLD && (x > 1.3 || x < 0)) {
        manual.set(false);
        // Pick the loop up at the same row position (mod one loop of sliding), at rest.
        const per = (SLIDE * GAP) / LOOP;
        const s = ((userShift.get() % (SLIDE * GAP)) + SLIDE * GAP) % (SLIDE * GAP);
        let t = s / per;
        // Start just after a pop so nothing jumps.
        t = Math.floor(t / POP) * POP + 1.31;
        origin.set(now - t);
        userShift.set(0);
      }
      return;
    }
    if (!autoPlay) return;
    const t = (((now - origin.get()) % LOOP) + LOOP) % LOOP;
    const shift = ((SLIDE * GAP) / LOOP) * t;
    const k = Math.floor(t / POP);
    const start = k * POP;
    const slot = Math.round((((SLIDE * GAP) / LOOP) * start) / GAP);
    const p = popAt(t - start);
    wall.set({ shift, slot, up: p.up, wobble: p.wobble });
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const shown = useDerivedValue(() => [wall.get().slot, wall.get().up]);
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const w = wall.get();
    drawWall(canvas, w.shift, w.slot, w.up, w.wobble, width, height);
    return recorder.finishRecordingAsPicture();
  }, [width, height]);

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const takeOver = () => {
    'worklet';
    if (!manual.get()) {
      manual.set(true);
      userShift.set(wall.get().shift);
      userSlot.set(wall.get().slot);
      userPop.set(wall.get().up > 0.001 ? elapsed.get() - 0.9 : -10);
    }
    lastTouch.set(elapsed.get());
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(6)
    .onStart(() => {
      takeOver();
      touching.set(true);
      grabbed.set(userShift.get());
    })
    .onUpdate((e) => {
      userShift.set(grabbed.get() - e.translationX);
      lastTouch.set(elapsed.get());
    })
    .onFinalize(() => {
      touching.set(false);
      lastTouch.set(elapsed.get());
    });
  const tap = Gesture.Tap()
    .enabled(!reduced)
    .onEnd(() => {
      takeOver();
      userSlot.set(Math.round(userShift.get() / GAP));
      userPop.set(elapsed.get());
      scheduleOnRN(haptic);
    });

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={Gesture.Exclusive(pan, tap)}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="A row of arcade tokens. Drag to slide, tap to pop one.">
            <Picture picture={picture} />
          </Canvas>
          <View style={[styles.head, { top: insets.top + 8 }]} pointerEvents="none">
            <View style={styles.row}>
              <Text style={styles.brand}>Token Hall</Text>
              <Text style={styles.meta}>55 tokens</Text>
            </View>
            <Text style={styles.title}>{title}</Text>
            <Text style={styles.sub}>Every game you win drops one in. Something to watch forever.</Text>
          </View>
        </View>
      </GestureDetector>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]} pointerEvents="none">
        <View style={styles.names}>
          {TOKENS.map((t, k) => (
            <NameCard key={t.name} k={k} shown={shown} />
          ))}
        </View>
        <View style={styles.button}>
          <Text style={styles.action}>Trade tokens</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#07070A' },
  head: { position: 'absolute', left: 24, right: 24 },
  row: { height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontSize: 18, fontWeight: '800', color: '#FFFFFF' },
  meta: { fontSize: 14, fontWeight: '600', color: 'rgba(255,255,255,0.55)' },
  title: { marginTop: 16, fontSize: 36, lineHeight: 40, fontWeight: '900', color: '#FFFFFF', letterSpacing: -1 },
  sub: { marginTop: 8, fontSize: 16, lineHeight: 22, color: 'rgba(255,255,255,0.6)' },
  footer: { position: 'absolute', left: 20, right: 20, bottom: 0 },
  names: { height: 56, marginBottom: 12 },
  nameCard: { position: 'absolute', left: 0, right: 0, height: 56, borderRadius: 18, backgroundColor: 'rgba(255,255,255,0.08)', flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, gap: 12 },
  swatch: { width: 22, height: 22, borderRadius: 11 },
  tokenName: { flex: 1, fontSize: 17, fontWeight: '700', color: '#FFFFFF' },
  count: { fontSize: 17, fontWeight: '800', color: 'rgba(255,255,255,0.7)' },
  button: { height: 58, borderRadius: 29, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center' },
  action: { fontSize: 17, fontWeight: '700', color: '#07070A' },
});
