import { BlurStyle, Canvas, PaintStyle, Picture, Skia, TileMode, type SkCanvas } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The welcome of an invented sleep app, Lull: an arched bedroom window over a small city. Through
// it the day runs down — the sun sinks into an orange haze, the sky deepens to blue-black, stars
// come out, windows light up across the rooftops and a crescent moon rises — and then dawn brings
// it back. A tag hanging from the sill on a string swings gently and reads "Wake 07:00" by day,
// "Bedtime 22:30" by night; the room's light follows the sky. Drag across the window to scrub
// the hours yourself.
//
// Drawn in code: sky gradients mixed between day, sunset and night; a sun and a moon with glows;
// 28 seeded stars; a skyline of 11 blocks with seeded lit windows; a cream frame with a cross bar.
// Layout (393 x 852 pt): 34 pt top row; "Meet Lull" 44 pt heavy; window 236 x 300 pt (round top)
// centred at 45 % of the height; the tag 64 pt under the sill on a 70 pt string; a 58 pt button.
//
// autoPlay timeline (LOOP = 9 s), sky phase p (0 day, 0.5 sunset, 1 night):
//   0.0-1.2  rest: day
//   1.2-4.2  the sun sets, p 0 -> 0.5 (ease-in-out sine)
//   4.2-6.0  p 0.5 -> 1: stars and lit windows fade in; the moon rises 5.0-6.4
//   6.4-7.6  night
//   7.6-9.0  dawn: p 1 -> 0, the moon sets, the sun comes back up
// The tag swings 7 deg with a 3 s period (three swings a loop). Dragging sideways sets p (0.004
// per pt); 2 s after release the loop picks up at the same p.

/** Seconds. */
export const LOOP = 9;
const WW = 236;
const WH = 300;
const HOLD = 2;

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function easeInOutSine(x: number) {
  'worklet';
  return -(Math.cos(Math.PI * x) - 1) / 2;
}
function hash(n: number) {
  'worklet';
  const x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return x - Math.floor(x);
}
function mix3(a: number[], b: number[], u: number) {
  'worklet';
  return [a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u];
}
function rgb(c: number[], alpha = 1) {
  'worklet';
  return Skia.Color(`rgba(${Math.round(c[0])}, ${Math.round(c[1])}, ${Math.round(c[2])}, ${alpha})`);
}
const DAY = [
  [62, 123, 255],
  [159, 211, 255],
];
const DUSK = [
  [59, 63, 154],
  [255, 154, 77],
];
const NIGHT = [
  [8, 12, 52],
  [32, 40, 112],
];
function skyAt(p: number) {
  'worklet';
  if (p < 0.5) {
    const u = p / 0.5;
    return [mix3(DAY[0], DUSK[0], u), mix3(DAY[1], DUSK[1], u)];
  }
  const u = (p - 0.5) / 0.5;
  return [mix3(DUSK[0], NIGHT[0], u), mix3(DUSK[1], NIGHT[1], u)];
}

/** Sky phase and moon rise `t` seconds into the loop. */
function phaseAt(t: number) {
  'worklet';
  let p = 0;
  if (t < 4.2) p = 0.5 * easeInOutSine(seg(t, 1.2, 4.2));
  else if (t < 7.6) p = 0.5 + 0.5 * easeInOutSine(seg(t, 4.2, 6.0));
  else p = 1 - easeInOutSine(seg(t, 7.6, 9.0));
  return p;
}

function windowPath(x: number, y: number) {
  'worklet';
  const p = Skia.Path.Make();
  const r = WW / 2;
  p.moveTo(x, y + r);
  p.arcToOval(Skia.XYWHRect(x, y, WW, WW), 180, 180, false);
  p.lineTo(x + WW, y + WH - 18);
  p.quadTo(x + WW, y + WH, x + WW - 18, y + WH);
  p.lineTo(x + 18, y + WH);
  p.quadTo(x, y + WH, x, y + WH - 18);
  p.close();
  return p;
}

function drawScene(c: SkCanvas, p: number, cx: number, cy: number) {
  'worklet';
  const x = cx - WW / 2;
  const y = cy - WH / 2;
  const path = windowPath(x, y);
  const sky = skyAt(p);
  const night = clamp01((p - 0.55) / 0.35);
  // Light from the window on the wall.
  const spill = Skia.Paint();
  spill.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, WH, [rgb(sky[1], 0.35 * (1 - night) + 0.12), rgb(sky[1], 0)], null, TileMode.Clamp));
  c.drawCircle(cx, cy, WH, spill);

  c.save();
  c.clipPath(path, 1, true);
  const skyPaint = Skia.Paint();
  skyPaint.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y }, { x: 0, y: y + WH * 0.75 }, [rgb(sky[0]), rgb(sky[1])], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(x, y, WW, WH), skyPaint);
  // Stars.
  for (let i = 0; i < 28; i++) {
    const sx = x + hash(i) * WW;
    const sy = y + hash(i + 40) * WH * 0.6;
    const star = Skia.Paint();
    star.setAntiAlias(true);
    star.setColor(Skia.Color('#FFFFFF'));
    star.setAlphaf(night * (0.4 + 0.6 * hash(i + 80)));
    c.drawCircle(sx, sy, 0.8 + 1.2 * hash(i + 120), star);
  }
  const horizon = y + WH * 0.72;
  // The sun: high by day, sinking into the horizon by sunset.
  const sunY = y + WH * 0.2 + (horizon - (y + WH * 0.2) + 30) * clamp01(p / 0.55);
  if (p < 0.6) {
    const glow = Skia.Paint();
    glow.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: sunY }, 90, [Skia.Color(p > 0.3 ? 'rgba(255,190,90,0.85)' : 'rgba(255,255,230,0.8)'), Skia.Color('rgba(255,190,90,0)')], null, TileMode.Clamp));
    c.drawCircle(cx, sunY, 90, glow);
    const sun = Skia.Paint();
    sun.setAntiAlias(true);
    sun.setColor(Skia.Color(p > 0.3 ? '#FFD27A' : '#FFFFFF'));
    c.drawCircle(cx, sunY, 15, sun);
  }
  // The moon: a crescent rising on the right once it is dark.
  const moon = clamp01((p - 0.7) / 0.3);
  if (moon > 0) {
    const mx = cx + 52;
    const my = horizon - 20 - 120 * moon;
    const mg = Skia.Paint();
    mg.setShader(Skia.Shader.MakeRadialGradient({ x: mx, y: my }, 50, [Skia.Color('rgba(200,210,255,0.4)'), Skia.Color('rgba(200,210,255,0)')], null, TileMode.Clamp));
    mg.setAlphaf(moon);
    c.drawCircle(mx, my, 50, mg);
    const m = Skia.Path.Make();
    m.addCircle(mx, my, 13);
    const cut = Skia.Path.Make();
    cut.addCircle(mx + 6, my - 4, 11);
    const crescent = Skia.Path.MakeFromOp(m, cut, 0);
    const mp = Skia.Paint();
    mp.setAntiAlias(true);
    mp.setColor(Skia.Color('#F4F1FF'));
    mp.setAlphaf(moon);
    if (crescent) c.drawPath(crescent, mp);
  }
  // Haze on the horizon at sunset.
  const haze = Skia.Paint();
  haze.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: horizon - 50 }, { x: 0, y: horizon }, [Skia.Color('rgba(255,140,80,0)'), Skia.Color('rgba(255,140,80,0.7)')], null, TileMode.Clamp));
  haze.setAlphaf(Math.max(0, 1 - Math.abs(p - 0.48) / 0.22));
  c.drawRect(Skia.XYWHRect(x, horizon - 50, WW, 50), haze);
  // Skyline with lit windows.
  const city = Skia.Paint();
  city.setAntiAlias(true);
  city.setColor(rgb(mix3([40, 52, 90], [6, 8, 22], clamp01(p * 1.4))));
  let bx = x - 6;
  for (let b = 0; b < 11; b++) {
    const bw = 18 + 20 * hash(b + 200);
    const bh = 24 + 70 * hash(b + 300);
    c.drawRect(Skia.XYWHRect(bx, horizon - bh + 30, bw, bh + WH), city);
    for (let r = 0; r < 6; r++) {
      for (let q = 0; q < 2; q++) {
        const n = b * 31 + r * 7 + q;
        if (hash(n + 500) > 0.45) continue;
        const lit = Skia.Paint();
        lit.setColor(Skia.Color('#FFD889'));
        lit.setAlphaf(clamp01((night - hash(n + 700) * 0.6) / 0.4));
        const wy = horizon - bh + 38 + r * 11;
        if (wy > y + WH - 6) continue;
        c.drawRect(Skia.XYWHRect(bx + 4 + q * (bw / 2 - 2), wy, 4, 5), lit);
      }
    }
    bx += bw + 2;
  }
  c.restore();

  // Frame: a cream border, a cross bar and a sill.
  const frame = Skia.Paint();
  frame.setAntiAlias(true);
  frame.setStyle(PaintStyle.Stroke);
  frame.setStrokeWidth(14);
  frame.setColor(Skia.Color('#E9E4F6'));
  c.drawPath(path, frame);
  const bar = Skia.Paint();
  bar.setAntiAlias(true);
  bar.setColor(Skia.Color('#E9E4F6'));
  c.drawRect(Skia.XYWHRect(cx - 4, y + 6, 8, WH - 6), bar);
  c.drawRect(Skia.XYWHRect(x, y + WH * 0.5 - 4, WW, 8), bar);
  const shade = Skia.Paint();
  shade.setColor(Skia.Color('rgba(0,0,20,0.25)'));
  shade.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8, true));
  c.drawRect(Skia.XYWHRect(x - 20, y + WH + 8, WW + 40, 8), shade);
  const sill = Skia.Paint();
  sill.setAntiAlias(true);
  sill.setColor(Skia.Color('#F4F0FB'));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 22, y + WH - 2, WW + 44, 14), 4, 4), sill);
  // Dim the frame at night.
  const dim = Skia.Paint();
  dim.setColor(Skia.Color('rgba(10,12,40,1)'));
  dim.setAlphaf(0.45 * night);
  dim.setStyle(PaintStyle.Stroke);
  dim.setStrokeWidth(14);
  c.drawPath(path, dim);
}

type Props = {
  /** Runs a day through the window, every LOOP seconds. */
  autoPlay?: boolean;
};

export function NightWindowSleep({ autoPlay = false }: Props) {
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
  const clock = useSharedValue(0);
  const phase = useSharedValue(reduced ? 1 : 0);
  const touching = useSharedValue(false);
  const manual = useSharedValue(false);
  const released = useSharedValue(-10);
  const grabbed = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    const t = (((now - origin.get()) % LOOP) + LOOP) % LOOP;
    clock.set(t);
    if (touching.get()) return;
    if (manual.get()) {
      if (now - released.get() > HOLD) {
        manual.set(false);
        // Find the loop time that shows this phase on the way down (sunset side).
        const p = phase.get();
        let best = 0;
        let bestD = 9;
        for (let s = 0; s <= 76; s++) {
          const ts = s * 0.1;
          const d = Math.abs(phaseAt(ts) - p);
          if (d < bestD) {
            bestD = d;
            best = ts;
          }
        }
        origin.set(now - best);
      }
      return;
    }
    if (autoPlay) phase.set(phaseAt(t));
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawScene(canvas, phase.get(), cx, cy);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy]);

  const roomStyle = useAnimatedStyle(() => {
    const p = phase.get();
    const sky = skyAt(p);
    const k = 0.32;
    return { backgroundColor: `rgb(${Math.round(sky[0][0] * k)}, ${Math.round(sky[0][1] * k)}, ${Math.round(sky[0][2] * k + 10)})` };
  });
  const tagStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${7 * Math.sin((clock.get() / 3) * Math.PI * 2)}deg` }] }));
  const dayStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp01((phase.get() - 0.45) / 0.15) }));
  const nightStyle = useAnimatedStyle(() => ({ opacity: clamp01((phase.get() - 0.45) / 0.15) }));

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
      grabbed.set(phase.get());
      scheduleOnRN(haptic);
    })
    .onUpdate((e) => phase.set(clamp01(grabbed.get() + e.translationX * 0.004)))
    .onFinalize(() => {
      touching.set(false);
      released.set(elapsed.get());
    });

  const sillY = cy + WH / 2 + 10;

  return (
    <Animated.View style={[styles.screen, roomStyle]}>
      <StatusBar style="light" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <View style={[styles.head, { top: insets.top + 8 }]} pointerEvents="none">
            <Text style={styles.meet}>Meet</Text>
            <Text style={styles.title}>Lull</Text>
            <Text style={styles.sub}>A window to wind down by.</Text>
          </View>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="A window from day to night. Drag across it to change the hour.">
            <Picture picture={picture} />
          </Canvas>
          <Animated.View style={[styles.tagWrap, { top: sillY, left: cx - 90 }, tagStyle]} pointerEvents="none">
            <View style={styles.string} />
            <View style={styles.tag}>
              <Animated.Text style={[styles.tagText, dayStyle]}>Wake 07:00</Animated.Text>
              <Animated.Text style={[styles.tagText, styles.over, nightStyle]}>Bedtime 22:30</Animated.Text>
            </View>
          </Animated.View>
        </View>
      </GestureDetector>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]} pointerEvents="none">
        <View style={styles.button}>
          <Text style={styles.action}>Set my bedtime</Text>
        </View>
        <Text style={styles.note}>Lull follows your local sunset.</Text>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  head: { position: 'absolute', left: 24, right: 24, alignItems: 'center' },
  meet: { marginTop: 8, fontSize: 15, fontWeight: '600', color: 'rgba(255,255,255,0.65)' },
  title: { fontSize: 46, fontWeight: '900', color: '#FFFFFF', letterSpacing: -1.5 },
  sub: { marginTop: 4, fontSize: 16, color: 'rgba(255,255,255,0.7)' },
  tagWrap: { position: 'absolute', width: 180, alignItems: 'center', transformOrigin: 'top' },
  string: { width: 2, height: 70, backgroundColor: '#F2E66B' },
  tag: { height: 40, paddingHorizontal: 18, minWidth: 150, borderRadius: 14, borderWidth: 2, borderColor: '#F2E66B', backgroundColor: 'rgba(30,30,30,0.85)', alignItems: 'center', justifyContent: 'center' },
  tagText: { fontSize: 16, fontWeight: '800', color: '#F2E66B' },
  over: { position: 'absolute' },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0, alignItems: 'center' },
  button: { alignSelf: 'stretch', height: 58, borderRadius: 29, backgroundColor: '#F2E66B', alignItems: 'center', justifyContent: 'center' },
  action: { fontSize: 17, fontWeight: '800', color: '#1A1A1A' },
  note: { marginTop: 12, fontSize: 14, color: 'rgba(255,255,255,0.6)' },
});
