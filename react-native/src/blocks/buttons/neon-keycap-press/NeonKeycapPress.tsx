import { BlurStyle, Canvas, PaintStyle, Picture, Skia, StrokeCap, TileMode, matchFont, type SkCanvas, type SkFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The pad of an invented macro keyboard app, Glowpad: nine glass keycaps with neon violet rims
// float in a dark room. A fingertip presses one: the key sinks, rings of light ripple out inside
// it from the touch, it fills with a hot pink-violet glow that lights the whole room, and the
// action it runs appears below — then the light ebbs and the finger moves to the next key.
// Tap any key to press it.
//
// Drawn in code: keys are rounded squares (radius 22) with a dark violet glass fill, a blurred
// and a sharp neon stroke, and white icon paths (or "AI" in the system font).
// Layout (393 x 852 pt): 34 pt top row; title 36 pt heavy; 3 x 3 keys of 92 pt, 16 pt apart,
// centred at 48 % of the height; the action card and a hint at the bottom.
//
// autoPlay timeline (LOOP = 7.2 s): three presses of 2.4 s — Play (centre), AI (top middle) and
// Moon (bottom left). In each: 0.0-0.35 the finger glides from the last key (ease-in-out cubic);
// 0.35-0.5 the key sinks to 0.92 and comes back by 0.75; 0.4-1.5 three rings ripple out
// (0.18 s apart, radius 0 -> 80 pt, fading); 0.45-0.75 the glow rises, holds, and fades
// 1.6-2.3; the room glow follows it.
// Tapping a key presses it (light haptic); 2 s later the loop carries on.

/** Seconds. */
export const LOOP = 7.2;
const PRESS = 2.4;
const KEY = 92;
const GAP = 16;
const PRESSES = [4, 1, 6];
const HOLD = 2;

const KEYS = ['send', 'ai', 'bolt', 'mic', 'play', 'heart', 'moon', 'star', 'plus'];
const ACTIONS: Record<string, string> = {
  send: 'Send the draft',
  ai: 'Ask the assistant',
  bolt: 'Boost mode on',
  mic: 'Start dictation',
  play: 'Play focus mix',
  heart: 'Save to favourites',
  moon: 'Night mode on',
  star: 'Star this thread',
  plus: 'New note',
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

/** A press that began `x` seconds ago: sink scale, glow (0..1) and ripple age. */
function pressAt(x: number) {
  'worklet';
  if (x < 0 || x > 2.4) return { sink: 1, glow: 0, ripple: -1 };
  const sink = x < 0.35 ? 1 : x < 0.5 ? 1 - 0.08 * seg(x, 0.35, 0.5) : 0.92 + 0.08 * seg(x, 0.5, 0.75);
  const glow = seg(x, 0.45, 0.75) * (1 - easeInOutCubic(seg(x, 1.6, 2.3)));
  return { sink, glow, ripple: x - 0.4 };
}

function iconPath(kind: string) {
  'worklet';
  const p = Skia.Path.Make();
  if (kind === 'send') {
    p.moveTo(-1, -0.1);
    p.lineTo(1, -0.9);
    p.lineTo(0.35, 0.95);
    p.lineTo(0.05, 0.2);
    p.close();
  } else if (kind === 'bolt') {
    p.moveTo(0.2, -1);
    p.lineTo(-0.55, 0.12);
    p.lineTo(-0.02, 0.12);
    p.lineTo(-0.25, 1);
    p.lineTo(0.55, -0.15);
    p.lineTo(0.02, -0.15);
    p.close();
  } else if (kind === 'mic') {
    p.addRRect(Skia.RRectXY(Skia.XYWHRect(-0.32, -1, 0.64, 1.2), 0.32, 0.32));
  } else if (kind === 'play') {
    p.moveTo(-0.55, -0.85);
    p.lineTo(0.85, 0);
    p.lineTo(-0.55, 0.85);
    p.close();
  } else if (kind === 'heart') {
    p.moveTo(0, 0.85);
    p.cubicTo(-1.1, 0.1, -0.9, -0.95, 0, -0.45);
    p.cubicTo(0.9, -0.95, 1.1, 0.1, 0, 0.85);
    p.close();
  } else if (kind === 'moon') {
    p.moveTo(0.2, -0.95);
    p.cubicTo(-0.75, -0.85, -1.0, 0.35, -0.35, 0.8);
    p.cubicTo(0.15, 1.1, 0.75, 0.85, 0.95, 0.35);
    p.cubicTo(0.3, 0.55, -0.35, 0.05, 0.2, -0.95);
    p.close();
  } else if (kind === 'star') {
    for (let i = 0; i < 10; i++) {
      const a = -Math.PI / 2 + (i * Math.PI) / 5;
      const r = i % 2 === 0 ? 1 : 0.45;
      if (i === 0) p.moveTo(Math.cos(a) * r, Math.sin(a) * r);
      else p.lineTo(Math.cos(a) * r, Math.sin(a) * r);
    }
    p.close();
  } else {
    p.addRect(Skia.XYWHRect(-0.12, -0.85, 0.24, 1.7));
    p.addRect(Skia.XYWHRect(-0.85, -0.12, 1.7, 0.24));
  }
  return p;
}

function drawPad(c: SkCanvas, glows: number[], sinks: number[], ripples: number[], finger: number[], showFinger: boolean, cx: number, cy: number, width: number, height: number, font: SkFont | null) {
  'worklet';
  // Room light: the strongest glow lights the room around its key.
  let room = 0;
  for (let i = 0; i < 9; i++) room = Math.max(room, glows[i]);
  const bg = Skia.Paint();
  bg.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, width * 1.1, [Skia.Color('#3A1670'), Skia.Color('#07020F')], [0, 1], TileMode.Clamp));
  bg.setAlphaf(0.35 + 0.65 * room);
  c.drawRect(Skia.XYWHRect(0, 0, width, height), bg);
  const span = KEY * 3 + GAP * 2;
  for (let i = 0; i < 9; i++) {
    const kx = cx - span / 2 + (i % 3) * (KEY + GAP) + KEY / 2;
    const ky = cy - span / 2 + Math.floor(i / 3) * (KEY + GAP) + KEY / 2;
    const g = glows[i];
    const s = KEY * sinks[i];
    const rect = Skia.RRectXY(Skia.XYWHRect(kx - s / 2, ky - s / 2, s, s), 22 * sinks[i], 22 * sinks[i]);
    // Outer neon glow.
    const halo = Skia.Paint();
    halo.setAntiAlias(true);
    halo.setColor(Skia.Color('#B266FF'));
    halo.setAlphaf(0.35 + 0.55 * g);
    halo.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10 + 16 * g, true));
    halo.setStyle(PaintStyle.Stroke);
    halo.setStrokeWidth(6 + 6 * g);
    c.drawRRect(rect, halo);
    // Glass body.
    const body = Skia.Paint();
    body.setAntiAlias(true);
    body.setShader(Skia.Shader.MakeLinearGradient({ x: kx, y: ky - s / 2 }, { x: kx, y: ky + s / 2 }, [Skia.Color('rgba(120,70,220,0.38)'), Skia.Color('rgba(50,20,110,0.55)')], null, TileMode.Clamp));
    c.drawRRect(rect, body);
    // Pressed: a hot fill and ripples, clipped to the key.
    if (g > 0.001 || ripples[i] >= 0) {
      c.save();
      c.clipRRect(rect, 1, true);
      const fill = Skia.Paint();
      fill.setAntiAlias(true);
      fill.setShader(Skia.Shader.MakeRadialGradient({ x: kx, y: ky + 10 }, s * 0.75, [Skia.Color('#FF9CF5'), Skia.Color('#C04BFF'), Skia.Color('#6A2BD8')], [0, 0.5, 1], TileMode.Clamp));
      fill.setAlphaf(g * 0.85);
      c.drawRRect(rect, fill);
      for (let r = 0; r < 3; r++) {
        const age = ripples[i] - r * 0.18;
        if (age < 0 || age > 1.0) continue;
        const ring = Skia.Paint();
        ring.setAntiAlias(true);
        ring.setStyle(PaintStyle.Stroke);
        ring.setStrokeWidth(5 * (1 - age) + 1);
        ring.setColor(Skia.Color('#FFE3FF'));
        ring.setAlphaf((1 - age) * 0.9);
        ring.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2, true));
        c.drawCircle(kx, ky + 8, 80 * Math.sqrt(age), ring);
      }
      c.restore();
    }
    // Rim.
    const rim = Skia.Paint();
    rim.setAntiAlias(true);
    rim.setStyle(PaintStyle.Stroke);
    rim.setStrokeWidth(2);
    rim.setColor(Skia.Color(g > 0.3 ? '#FFD6FF' : '#C59BFF'));
    c.drawRRect(rect, rim);
    // Icon.
    const ink = Skia.Paint();
    ink.setAntiAlias(true);
    ink.setColor(Skia.Color('#FFFFFF'));
    ink.setAlphaf(0.85 + 0.15 * g);
    if (KEYS[i] === 'ai') {
      if (font) {
        const w = font.measureText('AI').width;
        c.drawText('AI', kx - w / 2, ky + 11, ink, font);
      }
    } else {
      c.save();
      c.translate(kx, ky);
      const k = 22 * sinks[i];
      c.scale(k, k);
      c.drawPath(iconPath(KEYS[i]), ink);
      if (KEYS[i] === 'mic') {
        const stand = Skia.Paint();
        stand.setAntiAlias(true);
        stand.setStyle(PaintStyle.Stroke);
        stand.setStrokeWidth(0.16);
        stand.setStrokeCap(StrokeCap.Round);
        stand.setColor(Skia.Color('#FFFFFF'));
        const arc = Skia.Path.Make();
        arc.addArc(Skia.XYWHRect(-0.6, -0.5, 1.2, 1.1), 0, 180);
        arc.moveTo(0, 0.6);
        arc.lineTo(0, 0.95);
        c.drawPath(arc, stand);
      }
      c.restore();
    }
  }
  if (showFinger) {
    const dot = Skia.Paint();
    dot.setAntiAlias(true);
    dot.setColor(Skia.Color('rgba(255,255,255,0.45)'));
    c.drawCircle(finger[0], finger[1], 18, dot);
    const ring = Skia.Paint();
    ring.setAntiAlias(true);
    ring.setStyle(PaintStyle.Stroke);
    ring.setStrokeWidth(2);
    ring.setColor(Skia.Color('rgba(255,255,255,0.85)'));
    c.drawCircle(finger[0], finger[1], 18, ring);
  }
}

function Action({ k, last }: { k: number; last: SharedValue<number[]> }) {
  const style = useAnimatedStyle(() => {
    const [key, glow] = last.get();
    // The last key's action stays; it brightens while the key glows.
    const on = key === k ? 1 : 0;
    return { opacity: on * (0.55 + 0.45 * clamp01(glow * 1.5)), transform: [{ translateY: 6 * (1 - clamp01(glow * 3)) * on }] };
  });
  return <Animated.Text style={[styles.actionText, style]}>{ACTIONS[KEYS[k]]}</Animated.Text>;
}

type Props = {
  title?: string;
  /** Presses keys by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function NeonKeycapPress({ title = 'Your glow pad', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const font = useMemo(() => matchFont({ fontFamily: Platform.select({ ios: 'Helvetica', default: 'sans-serif' }), fontSize: 30, fontWeight: 'bold' }), []);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.48;
  const span = KEY * 3 + GAP * 2;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const pad = useSharedValue({ glows: KEYS.map(() => 0), sinks: KEYS.map(() => 1), ripples: KEYS.map(() => -1), finger: [0, 0], ghost: true });
  // The key and glow the action card shows.
  const last = useSharedValue([PRESSES[0], 0]);
  const manual = useSharedValue(false);
  const tappedKey = useSharedValue(0);
  const tappedAt = useSharedValue(-10);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    const glows: number[] = [];
    const sinks: number[] = [];
    const ripples: number[] = [];
    for (let i = 0; i < 9; i++) {
      glows.push(0);
      sinks.push(1);
      ripples.push(-1);
    }
    if (manual.get()) {
      // The tapped key's press starts at its sink (0.35 s into the press curve).
      const x = now - tappedAt.get() + 0.35;
      const p = pressAt(x);
      const k = tappedKey.get();
      glows[k] = p.glow;
      sinks[k] = p.sink;
      ripples[k] = p.ripple;
      pad.set({ glows, sinks, ripples, finger: [0, 0], ghost: false });
      last.set([k, p.glow]);
      if (now - tappedAt.get() > HOLD + 0.4) {
        manual.set(false);
        origin.set(now);
      }
      return;
    }
    if (!autoPlay) {
      pad.set({ glows, sinks, ripples, finger: [0, 0], ghost: false });
      return;
    }
    const t = (((now - origin.get()) % LOOP) + LOOP) % LOOP;
    const n = Math.floor(t / PRESS);
    const x = t - n * PRESS;
    const k = PRESSES[n];
    const p = pressAt(x);
    glows[k] = p.glow;
    sinks[k] = p.sink;
    ripples[k] = p.ripple;
    const j = PRESSES[(n + PRESSES.length - 1) % PRESSES.length];
    const x0 = cx - span / 2 + KEY / 2;
    const y0 = cy - span / 2 + KEY / 2;
    const fx = x0 + (j % 3) * (KEY + GAP);
    const fy = y0 + Math.floor(j / 3) * (KEY + GAP);
    const tx = x0 + (k % 3) * (KEY + GAP);
    const ty = y0 + Math.floor(k / 3) * (KEY + GAP);
    const m = easeInOutCubic(seg(x, 0, 0.35));
    pad.set({ glows, sinks, ripples, finger: [fx + (tx - fx) * m + 14, fy + (ty - fy) * m + 22], ghost: true });
    last.set([k, p.glow]);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const p = pad.get();
    drawPad(canvas, p.glows, p.sinks, p.ripples, p.finger, p.ghost, cx, cy, width, height, font);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, font]);

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const tap = Gesture.Tap()
    .enabled(!reduced)
    .onEnd((e) => {
      const col = Math.floor((e.x - (cx - span / 2)) / (KEY + GAP));
      const row = Math.floor((e.y - (cy - span / 2)) / (KEY + GAP));
      if (col < 0 || col > 2 || row < 0 || row > 2) return;
      tappedKey.set(row * 3 + col);
      tappedAt.set(elapsed.get());
      manual.set(true);
      scheduleOnRN(haptic);
    });

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={tap}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="Nine neon keys. Tap one to press it.">
            <Picture picture={picture} />
          </Canvas>
          <View style={[styles.head, { top: insets.top + 8 }]} pointerEvents="none">
            <View style={styles.row}>
              <Text style={styles.brand}>Glowpad</Text>
              <Text style={styles.meta}>Connected</Text>
            </View>
            <Text style={styles.title}>{title}</Text>
          </View>
        </View>
      </GestureDetector>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 18 }]} pointerEvents="none">
        <View style={styles.card}>
          <Text style={styles.cardLabel}>Last action</Text>
          <View style={styles.actions}>
            {KEYS.map((key, k) => (
              <Action key={key} k={k} last={last} />
            ))}
          </View>
        </View>
        <Text style={styles.hint}>Tap a key to run its macro</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#07020F' },
  head: { position: 'absolute', left: 24, right: 24 },
  row: { height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontSize: 18, fontWeight: '800', color: '#FFFFFF' },
  meta: { fontSize: 14, fontWeight: '600', color: '#C59BFF' },
  title: { marginTop: 16, fontSize: 36, lineHeight: 40, fontWeight: '900', color: '#FFFFFF', letterSpacing: -1 },
  footer: { position: 'absolute', left: 20, right: 20, bottom: 0 },
  card: { padding: 18, borderRadius: 22, backgroundColor: 'rgba(178,102,255,0.1)', borderWidth: 1, borderColor: 'rgba(197,155,255,0.3)' },
  cardLabel: { fontSize: 13, fontWeight: '700', color: 'rgba(255,255,255,0.55)', letterSpacing: 1, textTransform: 'uppercase' },
  actions: { height: 30, marginTop: 6 },
  actionText: { position: 'absolute', fontSize: 22, fontWeight: '800', color: '#FFFFFF' },
  hint: { marginTop: 12, textAlign: 'center', fontSize: 14, fontWeight: '600', color: 'rgba(255,255,255,0.5)' },
});
