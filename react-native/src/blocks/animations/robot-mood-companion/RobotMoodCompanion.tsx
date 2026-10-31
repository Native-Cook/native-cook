import { BlurStyle, Canvas, PaintStyle, Picture, Skia, StrokeCap, useImage, type SkCanvas, type SkImage } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The home screen of an invented desk companion, Pip: a teal robot head whose black visor shows a
// glowing cyan face. Pip runs through its moods — happy, surprised, sleepy, proud, sad, grumpy —
// blinking from one to the next; each mood has its own face (eye shapes cut by lids, a mouth,
// tears that run, a halo, drifting z's), its own way of holding the head and its own room colour.
// Tap Pip to change its mood.
//
// Artwork: assets/robot-head.png (generated, prompt in assets/PROMPTS.md); the face is drawn in
// code on the visor: centre (0.502, 0.502) of the head square, 0.64 x 0.41 of its size.
// Layout (393 x 852 pt): 34 pt top row; mood name Bricolage Grotesque 64 pt and a line; head 300
// pt square centred at 52 % of the height; six mood dots and a 58 pt button at the bottom.
//
// autoPlay timeline (LOOP = 8.4 s): six moods of 1.4 s; the switches are at 1.2, 2.6, 4.0, 5.4,
// 6.8 and 8.2 s. At each switch the eyes close over 0.12 s, the face changes, and they open over
// 0.14 s; the colour and the head's pose cross-fade over the same 0.26 s. Inside a mood: happy
// bobs, surprised hops, sleepy sways with rising z's, proud floats under a halo, sad droops while
// tears run, grumpy shakes.
// Tapping the head moves to the next mood (light haptic); 2 s later the loop carries on from it.

/** Seconds. */
export const LOOP = 8.4;
const STOP = 1.4;
const FIRST = 1.2;
const CLOSE = 0.12;
const OPEN = 0.14;
const HEAD = 300;
const HOLD = 2;

type Mood = { name: string; line: string; bg: string; ink: string };
const MOODS: Mood[] = [
  { name: 'Happy', line: 'Pip had a good day. So did you?', bg: '#F8E6A0', ink: '#3A2E0A' },
  { name: 'Surprised', line: 'Three new messages, all at once.', bg: '#F0957F', ink: '#3E150B' },
  { name: 'Sleepy', line: 'Past eleven. Pip is winding down.', bg: '#B49E9E', ink: '#2E2222' },
  { name: 'Proud', line: 'You finished every task today.', bg: '#55CF9C', ink: '#0D3B27' },
  { name: 'Sad', line: 'You skipped your walk again.', bg: '#1F4652', ink: '#E6F6FA' },
  { name: 'Grumpy', line: 'Four hours of meetings? Really?', bg: '#E8206E', ink: '#FFF0F5' },
];
const CYAN = '#3FE0F0';

const BRICOLAGE = require('./assets/BricolageHeadline.ttf');
const FAMILY = 'RobotMoodCompanion-Bricolage';

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function smooth(x: number) {
  'worklet';
  const u = clamp01(x);
  return u * u * (3 - 2 * u);
}
function mixColor(a: string, b: string, u: number) {
  'worklet';
  const pa = parseInt(a.slice(1), 16);
  const pb = parseInt(b.slice(1), 16);
  const r = Math.round(((pa >> 16) & 255) * (1 - u) + ((pb >> 16) & 255) * u);
  const g = Math.round(((pa >> 8) & 255) * (1 - u) + ((pb >> 8) & 255) * u);
  const bl = Math.round((pa & 255) * (1 - u) + (pb & 255) * u);
  return `rgb(${r}, ${g}, ${bl})`;
}

/** Mood shown, the previous one, cross-fade weight, eye openness and seconds into the mood. */
function moodAt(t: number) {
  'worklet';
  const k = Math.floor((t - FIRST) / STOP) + 1;
  const b = FIRST + (k - 1) * STOP;
  const since = t - b;
  // Before the first switch the loop is still in mood 0, which began at 8.2 - 8.4 = -0.2 s.
  const s = k === 0 ? t + (LOOP - (FIRST + 5 * STOP)) : since;
  const toNext = FIRST + k * STOP - t;
  let open = 1;
  if (s < OPEN) open = smooth(s / OPEN);
  if (toNext < CLOSE) open = smooth(toNext / CLOSE);
  const cur = ((k % 6) + 6) % 6;
  const prev = (cur + 5) % 6;
  const fade = smooth((s + CLOSE) / (CLOSE + OPEN));
  return { cur, prev, fade, open, s };
}

/** Head pose for mood m, s seconds into it. */
function poseOf(m: number, s: number) {
  'worklet';
  if (m === 0) return { y: -6 * Math.abs(Math.sin(s * Math.PI * 2.2)), rot: 0, x: 0 };
  if (m === 1) return { y: -16 * Math.sin(Math.PI * clamp01(s / 0.45)), rot: 0, x: 0 };
  if (m === 2) return { y: 6, rot: 7 * Math.sin(s * 1.6 + 0.4), x: 0 };
  if (m === 3) return { y: -10 - 4 * Math.sin(s * Math.PI * 1.4), rot: -3, x: 0 };
  if (m === 4) return { y: 10, rot: -5, x: 0 };
  return { y: 0, rot: 0, x: 3.5 * Math.sin(s * 42) * Math.exp(-2 * s) };
}

function cyan(alpha: number, glow: boolean) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(CYAN));
  p.setAlphaf(alpha * (glow ? 0.65 : 1));
  if (glow) p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
  return p;
}

/** One eye of mood m on side `side` (-1 left, 1 right), centred at (ex, ey). */
function drawEye(c: SkCanvas, m: number, side: number, ex: number, ey: number, open: number, glow: boolean) {
  'worklet';
  const rx = 16.5;
  const ry = Math.max(1.5, 15 * open);
  const fill = cyan(1, glow);
  if (m === 2 || m === 3) {
    // Closed arcs: sleepy hangs down, proud curves up.
    const p = cyan(1, glow);
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(4);
    p.setStrokeCap(StrokeCap.Round);
    const path = Skia.Path.Make();
    const oval = Skia.XYWHRect(ex - rx, ey - 9, rx * 2, 18);
    if (m === 2) path.addArc(oval, 20, 140);
    else path.addArc(oval, 200, 140);
    c.drawPath(path, p);
    return;
  }
  let erx = rx;
  let ery = ry;
  if (m === 1) {
    erx = 14;
    ery = Math.max(1.5, 20 * open);
  }
  c.save();
  const clip = Skia.Path.Make();
  if (m === 0) {
    // Happy: flat bottom.
    clip.addRect(Skia.XYWHRect(ex - 30, ey - 40, 60, 43));
  } else if (m === 4 || m === 5) {
    // A lid line: grumpy drops at the inner corner, sad at the outer one.
    const inner = m === 5 ? ey - 2 : ey - ery - 4;
    const outer = m === 5 ? ey - ery - 6 : ey - 3;
    const yl = side < 0 ? outer : inner;
    const yr = side < 0 ? inner : outer;
    clip.moveTo(ex - 30, yl - (yr - yl) * (14 / 60));
    clip.lineTo(ex + 30, yr + (yr - yl) * (14 / 60));
    clip.lineTo(ex + 30, ey + 40);
    clip.lineTo(ex - 30, ey + 40);
    clip.close();
  } else {
    clip.addRect(Skia.XYWHRect(ex - 30, ey - 40, 60, 80));
  }
  c.clipPath(clip, 1, true);
  c.drawOval(Skia.XYWHRect(ex - erx, ey - ery, erx * 2, ery * 2), fill);
  c.restore();
}

function drawFace(c: SkCanvas, m: number, open: number, s: number, vx: number, vy: number, glow: boolean) {
  'worklet';
  drawEye(c, m, -1, vx - 42, vy - 6, open, glow);
  drawEye(c, m, 1, vx + 42, vy - 6, open, glow);
  const stroke = cyan(1, glow);
  stroke.setStyle(PaintStyle.Stroke);
  stroke.setStrokeWidth(3.5);
  stroke.setStrokeCap(StrokeCap.Round);
  if (m === 1) {
    c.drawOval(Skia.XYWHRect(vx - 5, vy + 24, 10, 13), cyan(1, glow));
  } else if (m === 3) {
    const path = Skia.Path.Make();
    path.addArc(Skia.XYWHRect(vx - 9, vy + 16, 18, 12), 20, 140);
    c.drawPath(path, stroke);
  } else if (m === 4) {
    // Tears run from under each eye and loop every 0.9 s.
    for (let side = -1; side <= 1; side += 2) {
      const run = (s % 0.9) / 0.9;
      const len = 10 + 34 * run;
      const path = Skia.Path.Make();
      const x0 = vx + side * 46;
      path.moveTo(x0, vy + 6);
      for (let i = 1; i <= 8; i++) {
        const y = vy + 6 + (len * i) / 8;
        path.lineTo(x0 + Math.sin(i * 1.4 + s * 6) * 2.5, y);
      }
      const tear = cyan(1 - run * 0.3, glow);
      tear.setStyle(PaintStyle.Stroke);
      tear.setStrokeWidth(7);
      tear.setStrokeCap(StrokeCap.Round);
      c.drawPath(path, tear);
    }
  } else if (m === 5) {
    const path = Skia.Path.Make();
    path.moveTo(vx - 10, vy + 30);
    path.quadTo(vx, vy + 24, vx + 10, vy + 30);
    c.drawPath(path, stroke);
  }
}

function drawScene(c: SkCanvas, t: number, cx: number, cy: number, head: SkImage | null) {
  'worklet';
  const mo = moodAt(t);
  const a = poseOf(mo.prev, mo.s + STOP);
  const b = poseOf(mo.cur, mo.s);
  const w = mo.fade;
  const x = a.x * (1 - w) + b.x * w;
  const y = a.y * (1 - w) + b.y * w;
  const rot = a.rot * (1 - w) + b.rot * w;
  // Floor shadow.
  const sh = Skia.Paint();
  sh.setAntiAlias(true);
  sh.setColor(Skia.Color('rgba(0,0,0,0.16)'));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10, true));
  const lift = clamp01(-y / 20);
  c.drawOval(Skia.XYWHRect(cx - 100 * (1 - lift * 0.2), cy + 128, 200 * (1 - lift * 0.2), 22), sh);

  c.save();
  c.translate(cx + x, cy + y);
  c.rotate(rot, 0, 0);
  // Halo for proud.
  const halo = mo.cur === 3 ? smooth(mo.s / 0.3) : mo.prev === 3 ? 1 - w : 0;
  if (halo > 0) {
    const hp = Skia.Paint();
    hp.setAntiAlias(true);
    hp.setStyle(PaintStyle.Stroke);
    hp.setStrokeWidth(9);
    hp.setColor(Skia.Color('#FFF1C2'));
    hp.setAlphaf(halo);
    const hw = 150 * (0.4 + 0.6 * halo);
    c.drawOval(Skia.XYWHRect(-hw / 2, -128 - 12 * halo, hw, 26), hp);
  }
  if (head) c.drawImageRect(head, Skia.XYWHRect(0, 0, head.width(), head.height()), Skia.XYWHRect(-HEAD / 2, -HEAD / 2, HEAD, HEAD), Skia.Paint());
  const vx = 0.002 * HEAD;
  const vy = 0.002 * HEAD;
  c.save();
  c.clipRRect(Skia.RRectXY(Skia.XYWHRect(vx - 0.32 * HEAD, vy - 0.205 * HEAD, 0.64 * HEAD, 0.41 * HEAD), 50, 50), 1, true);
  drawFace(c, mo.cur, mo.open, mo.s, vx, vy, true);
  drawFace(c, mo.cur, mo.open, mo.s, vx, vy, false);
  c.restore();
  c.restore();

  // Sleepy z's drift up from the head.
  if (mo.cur === 2) {
    for (let i = 0; i < 3; i++) {
      const u = ((mo.s + i * 0.45) % 1.35) / 1.35;
      const zp = Skia.Paint();
      zp.setAntiAlias(true);
      zp.setStyle(PaintStyle.Stroke);
      zp.setStrokeWidth(3);
      zp.setStrokeCap(StrokeCap.Round);
      zp.setColor(Skia.Color('#FFFFFF'));
      zp.setAlphaf(Math.sin(Math.PI * u) * mo.open);
      const s = 8 + 8 * u;
      const zx = cx + 110 + 20 * u;
      const zy = cy - 110 - 70 * u;
      const z = Skia.Path.Make();
      z.moveTo(zx - s / 2, zy - s / 2);
      z.lineTo(zx + s / 2, zy - s / 2);
      z.lineTo(zx - s / 2, zy + s / 2);
      z.lineTo(zx + s / 2, zy + s / 2);
      c.drawPath(z, zp);
    }
  }
}

function MoodText({ k, mood, ink }: { k: number; mood: SharedValue<number[]>; ink: SharedValue<string> }) {
  const style = useAnimatedStyle(() => {
    const [cur, prev, fade] = mood.get();
    const o = cur === k ? fade : prev === k ? 1 - fade : 0;
    return { opacity: o, transform: [{ translateY: 12 * (1 - o) }] };
  });
  const color = useAnimatedStyle(() => ({ color: ink.get() }));
  return (
    <Animated.View style={[styles.moodBox, style]}>
      <Animated.Text style={[styles.mood, color]}>{MOODS[k].name}</Animated.Text>
      <Animated.Text style={[styles.line, color]}>{MOODS[k].line}</Animated.Text>
    </Animated.View>
  );
}

function Dot({ k, mood, ink }: { k: number; mood: SharedValue<number[]>; ink: SharedValue<string> }) {
  const style = useAnimatedStyle(() => {
    const [cur, prev, fade] = mood.get();
    const on = cur === k ? fade : prev === k ? 1 - fade : 0;
    return { width: 8 + 18 * on, backgroundColor: ink.get(), opacity: 0.3 + 0.7 * on };
  });
  return <Animated.View style={[styles.dot, style]} />;
}

type Props = {
  brand?: string;
  action?: string;
  /** Runs through Pip's moods by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function RobotMoodCompanion({ brand = 'pip', action = 'Talk to Pip', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [FAMILY]: BRICOLAGE });
  const head = useImage(require('./assets/robot-head.png'));
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.52;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const clock = useSharedValue(0.6);
  const touchedAt = useSharedValue(-10);
  const frozen = useSharedValue(false);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    if (!autoPlay && !frozen.get()) {
      clock.set(0.6);
      return;
    }
    // After a tap the mood holds for 2 s (the clock runs inside the mood, then waits).
    if (frozen.get()) {
      const t = clock.get() + dt;
      const mo = moodAt(t);
      if (mo.s < STOP - CLOSE - 0.02) clock.set(t);
      else origin.set(origin.get() + dt);
      if (now - touchedAt.get() > HOLD) frozen.set(false);
      return;
    }
    clock.set((((now - origin.get()) % LOOP) + LOOP) % LOOP);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const mood = useDerivedValue(() => {
    const mo = moodAt(clock.get());
    return [mo.cur, mo.prev, mo.fade];
  });
  const ink = useDerivedValue(() => {
    const [cur, prev, fade] = mood.get();
    return mixColor(MOODS[prev].ink, MOODS[cur].ink, fade);
  });
  const bgStyle = useAnimatedStyle(() => {
    const [cur, prev, fade] = mood.get();
    return { backgroundColor: mixColor(MOODS[prev].bg, MOODS[cur].bg, fade) };
  });
  const inkStyle = useAnimatedStyle(() => ({ color: ink.get() }));
  const buttonStyle = useAnimatedStyle(() => ({ backgroundColor: ink.get() }));
  const actionStyle = useAnimatedStyle(() => {
    const [cur, prev, fade] = mood.get();
    return { color: mixColor(MOODS[prev].bg, MOODS[cur].bg, fade) };
  });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawScene(canvas, clock.get(), cx, cy, head);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, head]);

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const tap = Gesture.Tap()
    .enabled(!reduced)
    .onEnd(() => {
      // Jump to just before the next switch, so Pip blinks into the next mood.
      const mo = moodAt(clock.get());
      const next = FIRST + mo.cur * STOP - CLOSE;
      const t = mo.cur === 0 && clock.get() > FIRST + 5 * STOP ? FIRST - CLOSE : ((next % LOOP) + LOOP) % LOOP;
      const now = elapsed.get();
      origin.set(now - t);
      clock.set(t);
      frozen.set(true);
      touchedAt.set(now);
      scheduleOnRN(haptic);
    });

  if (!fontsLoaded) return <View style={[styles.screen, { backgroundColor: MOODS[0].bg }]} />;

  return (
    <Animated.View style={[styles.screen, bgStyle]}>
      <StatusBar style="auto" />
      <View style={[styles.top, { top: insets.top + 8 }]} pointerEvents="none">
        <Animated.Text style={[styles.brand, inkStyle]}>{brand}</Animated.Text>
        <Animated.Text style={[styles.meta, inkStyle]}>Battery 82 %</Animated.Text>
      </View>
      <View style={[styles.moods, { top: insets.top + 64 }]} pointerEvents="none">
        {MOODS.map((m, k) => (
          <MoodText key={m.name} k={k} mood={mood} ink={ink} />
        ))}
      </View>
      <GestureDetector gesture={tap}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityRole="button" accessibilityLabel="Pip. Tap to change its mood.">
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]} pointerEvents="none">
        <View style={styles.dots}>
          {MOODS.map((m, k) => (
            <Dot key={m.name} k={k} mood={mood} ink={ink} />
          ))}
        </View>
        <Animated.View style={[styles.button, buttonStyle]}>
          <Animated.Text style={[styles.action, actionStyle]}>{action}</Animated.Text>
        </Animated.View>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  top: { position: 'absolute', left: 24, right: 24, height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontFamily: FAMILY, fontSize: 24 },
  meta: { fontSize: 14, fontWeight: '600', opacity: 0.7 },
  moods: { position: 'absolute', left: 24, right: 24, height: 130 },
  moodBox: { position: 'absolute', left: 0, right: 0 },
  mood: { fontFamily: FAMILY, fontSize: 64, lineHeight: 70, letterSpacing: -2 },
  line: { marginTop: 4, fontSize: 18, lineHeight: 24, opacity: 0.8 },
  footer: { position: 'absolute', left: 24, right: 24, bottom: 0 },
  dots: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 8, marginBottom: 20 },
  dot: { height: 8, borderRadius: 4 },
  button: { height: 58, borderRadius: 29, alignItems: 'center', justifyContent: 'center' },
  action: { fontFamily: FAMILY, fontSize: 18 },
});
