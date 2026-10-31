import { Canvas, Picture, Skia, TileMode, PaintStyle, useFont, type SkCanvas, type SkFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The "find your crew" screen of an invented festival companion app, Beacon. A green radar
// sweeps round a dark scope; each friend's blip flashes as the beam passes over it and fades
// until the next pass, labelled with their initials and distance. On the second turn the radar
// locks on to Maya: her blip grows into a ringed avatar, a dashed line runs out to her from the
// centre and a card slides up with how far away she is. Drag round the scope to steer the beam
// yourself; lifting your finger near a friend locks on to them.
//
// Layout (393 x 852 pt): 34 pt top row; title JetBrains Mono Bold 30 pt; scope radius 168 pt
// centred at 46 % of the height (four rings, a cross); six friends at fixed bearings; a 92 pt
// card and a 58 pt button at the bottom.
//
// autoPlay timeline (LOOP = 6 s): the beam turns twice per loop (3 s a turn, linear) with a
// 60 deg green wake; a blip's glow is exp(-2.2 x seconds since the beam passed it), so the loop is
// seamless. Lock-on to Maya: 3.4-3.9 s her avatar grows (ease-out back), the line draws and the
// card slides up (3.6-4.1); 5.4-5.9 s they go away again; 5.9-6.0 rest.
// Dragging steers the beam to the finger's bearing; release locks on to the nearest friend
// within 30 deg (light haptic) for 2 s, then the loop starts again.

/** Seconds. */
export const LOOP = 6;
const TURN = 3;
const SCOPE = 168;
const HOLD = 2;

type Friend = { initials: string; name: string; bearing: number; range: number; meters: number; color: string };
const FRIENDS: Friend[] = [
  { initials: 'MA', name: 'Maya', bearing: 0.95, range: 0.55, meters: 120, color: '#FF7AB6' },
  { initials: 'JO', name: 'Jonah', bearing: 2.4, range: 0.78, meters: 260, color: '#7CC8FF' },
  { initials: 'RI', name: 'Rina', bearing: 3.3, range: 0.4, meters: 90, color: '#FFD45C' },
  { initials: 'TE', name: 'Teo', bearing: 4.25, range: 0.86, meters: 310, color: '#B79CFF' },
  { initials: 'SA', name: 'Sami', bearing: 5.2, range: 0.62, meters: 180, color: '#7CFFB2' },
  { initials: 'LU', name: 'Luz', bearing: 0.15, range: 0.9, meters: 340, color: '#FF9E6B' },
];
const LOCK = 0;

const MONO_BOLD = require('./assets/JetBrainsMono-Bold.ttf');
const MONO = require('./assets/JetBrainsMono-Medium.ttf');
const BOLD = 'RadarFriendFinder-MonoBold';
const MEDIUM = 'RadarFriendFinder-MonoMedium';

const GREEN = '#2BFF88';

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
  const k = 1.6;
  return 1 + (k + 1) * Math.pow(x - 1, 3) + k * Math.pow(x - 1, 2);
}
function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

/** Lock-on amount for Maya, `t` seconds into the loop. */
function lockAt(t: number) {
  'worklet';
  return seg(t, 3.4, 3.9) * (1 - seg(t, 5.4, 5.9));
}

function paint(color: string, alpha: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  p.setAlphaf(clamp01(alpha));
  return p;
}

function drawScope(c: SkCanvas, beam: number, lock: number, locked: number, cx: number, cy: number, small: SkFont) {
  'worklet';
  // Scope face.
  const face = Skia.Paint();
  face.setAntiAlias(true);
  face.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, SCOPE, [Skia.Color('#0E2A1C'), Skia.Color('#081A11')], null, TileMode.Clamp));
  c.drawCircle(cx, cy, SCOPE, face);
  const ring = paint(GREEN, 0.18);
  ring.setStyle(PaintStyle.Stroke);
  ring.setStrokeWidth(1);
  for (let r = 1; r <= 4; r++) c.drawCircle(cx, cy, (SCOPE * r) / 4, ring);
  c.drawLine(cx - SCOPE, cy, cx + SCOPE, cy, ring);
  c.drawLine(cx, cy - SCOPE, cx, cy + SCOPE, ring);

  // The beam's wake: a sweep gradient trailing 60 deg behind the leading edge.
  const wake = Skia.Paint();
  wake.setAntiAlias(true);
  wake.setShader(
    Skia.Shader.MakeSweepGradient(cx, cy, [Skia.Color('rgba(43,255,136,0)'), Skia.Color('rgba(43,255,136,0)'), Skia.Color('rgba(43,255,136,0.42)')], [0, 0.833, 1], TileMode.Clamp, Skia.Matrix().translate(cx, cy).rotate(beam).translate(-cx, -cy)),
  );
  c.drawCircle(cx, cy, SCOPE, wake);
  const edge = paint(GREEN, 0.95);
  edge.setStrokeWidth(2);
  c.drawLine(cx, cy, cx + Math.cos(beam) * SCOPE, cy + Math.sin(beam) * SCOPE, edge);

  // Friends.
  for (let i = 0; i < FRIENDS.length; i++) {
    const f = FRIENDS[i];
    const x = cx + Math.cos(f.bearing) * f.range * SCOPE;
    const y = cy + Math.sin(f.bearing) * f.range * SCOPE;
    // Seconds since the beam last passed this bearing.
    let ago = (((beam - f.bearing) % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
    ago = (ago / (Math.PI * 2)) * TURN;
    const glow = Math.exp(-2.2 * ago);
    const isLock = i === locked ? lock : 0;
    const halo = paint(GREEN, 0.35 * glow);
    halo.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 8, true));
    c.drawCircle(x, y, 10, halo);
    c.drawCircle(x, y, 4 + 2 * glow, paint(GREEN, 0.35 + 0.65 * glow));
    if (isLock > 0) {
      const s = easeOutBack(clamp01(isLock));
      // Dashed line from the centre.
      const dash = paint(f.color, 0.8 * isLock);
      dash.setStyle(PaintStyle.Stroke);
      dash.setStrokeWidth(1.5);
      dash.setPathEffect(Skia.PathEffect.MakeDash([5, 5], 0));
      const lp = Skia.Path.Make();
      lp.moveTo(cx, cy);
      lp.lineTo(cx + (x - cx) * easeOutCubic(isLock), cy + (y - cy) * easeOutCubic(isLock));
      c.drawPath(lp, dash);
      const ringP = paint(f.color, isLock);
      ringP.setStyle(PaintStyle.Stroke);
      ringP.setStrokeWidth(2);
      c.drawCircle(x, y, 26 * s + 6 * Math.sin(beam * 4) * isLock, ringP);
      c.drawCircle(x, y, 20 * s, paint(f.color, 1));
      c.save();
      c.translate(x - 9 * s, y + 4.5 * s);
      c.scale(s, s);
      c.drawText(f.initials, 0, 0, paint('#06110C', 1), small);
      c.restore();
    } else {
      c.drawText(f.initials, x + 9, y - 6, paint(GREEN, 0.4 + 0.6 * glow), small);
    }
  }
  c.drawCircle(cx, cy, 5, paint(GREEN, 1));
}

type Props = {
  brand?: string;
  title?: string;
  action?: string;
  /** Sweeps and locks on by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function RadarFriendFinder({ brand = 'beacon', title = 'Find your\ncrew', action = 'Walk me there', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [BOLD]: MONO_BOLD, [MEDIUM]: MONO });
  const small = useFont(MONO_BOLD, 13);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.47;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const beam = useSharedValue(-Math.PI / 2);
  const lock = useSharedValue(0);
  const locked = useSharedValue(LOCK);
  const touching = useSharedValue(false);
  const manual = useSharedValue(false);
  const released = useSharedValue(-1);
  const lastNear = useSharedValue(-1);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    if (touching.get()) {
      lock.set(Math.max(0, lock.get() - dt * 4));
      return;
    }
    if (manual.get()) {
      const since = now - released.get();
      lock.set(lastNear.get() >= 0 ? (since < HOLD ? clamp01(since / 0.4) : 1 - clamp01((since - HOLD) / 0.4)) : 0);
      beam.set(beam.get() + (dt / TURN) * Math.PI * 2 * 0.25);
      if (since > HOLD + 0.4) {
        manual.set(false);
        locked.set(LOCK);
        origin.set(now - (((beam.get() + Math.PI / 2) / (Math.PI * 2)) % 1) * TURN);
      }
      return;
    }
    const t = (now - origin.get()) % LOOP;
    beam.set(autoPlay ? -Math.PI / 2 + (Math.PI * 2 * t) / TURN : -Math.PI / 2);
    lock.set(autoPlay ? lockAt(t) : 0);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    if (small) drawScope(canvas, beam.get(), lock.get(), locked.get(), cx, cy, small);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, small]);

  const cardStyle = useAnimatedStyle(() => {
    const k = easeOutCubic(clamp01(lock.get()));
    return { opacity: k, transform: [{ translateY: 24 * (1 - k) }] };
  });
  const searchStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp01(lock.get() * 2) }));

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const steer = (x: number, y: number) => {
    'worklet';
    beam.set(Math.atan2(y - cy, x - cx));
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin((e) => {
      touching.set(true);
      manual.set(true);
      steer(e.x, e.y);
    })
    .onUpdate((e) => steer(e.x, e.y))
    .onFinalize(() => {
      touching.set(false);
      // Lock on to the nearest friend within 30 deg of the beam.
      let best = -1;
      let bestD = 0.52;
      for (let i = 0; i < FRIENDS.length; i++) {
        let d = Math.abs((((beam.get() - FRIENDS[i].bearing) % (Math.PI * 2)) + Math.PI * 3) % (Math.PI * 2) - Math.PI);
        d = Math.min(d, Math.PI * 2 - d);
        if (d < bestD) {
          bestD = d;
          best = i;
        }
      }
      lastNear.set(best);
      if (best >= 0) {
        locked.set(best);
        scheduleOnRN(haptic);
      }
      released.set(elapsed.get());
    });

  if (!fontsLoaded) return <View style={styles.screen} />;

  const target = FRIENDS[LOCK];

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <View style={[styles.head, { top: insets.top + 8 }]} pointerEvents="none">
            <View style={styles.row}>
              <Text style={styles.brand}>{brand}</Text>
              <Text style={styles.live}>● LIVE · 6 friends</Text>
            </View>
            <Text style={styles.title}>{title}</Text>
          </View>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="Radar of nearby friends. Drag round the scope to steer the beam.">
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>

      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]}>
        <View style={styles.cardBox}>
          <Animated.Text style={[styles.searching, searchStyle]}>Scanning 400 m around you…</Animated.Text>
          <Animated.View style={[styles.card, cardStyle]}>
            <View style={[styles.avatar, { backgroundColor: target.color }]}>
              <Text style={styles.avatarText}>{target.initials}</Text>
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.cardName}>{target.name} is near the main stage</Text>
              <Text style={styles.cardMeta}>{`${target.meters} m · south-east · 2 min walk`}</Text>
            </View>
          </Animated.View>
        </View>
        <View style={styles.button} accessible accessibilityRole="button">
          <Text style={styles.action}>{action}</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#06110C' },
  head: { position: 'absolute', left: 24, right: 24 },
  row: { height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontFamily: BOLD, fontSize: 20, color: GREEN },
  live: { fontFamily: MEDIUM, fontSize: 12, color: 'rgba(43,255,136,0.7)' },
  title: { marginTop: 14, fontFamily: BOLD, fontSize: 34, lineHeight: 40, color: '#E8FFF2', letterSpacing: -1 },
  footer: { position: 'absolute', left: 20, right: 20, bottom: 0 },
  cardBox: { height: 84, justifyContent: 'center' },
  searching: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: MEDIUM, fontSize: 14, color: 'rgba(43,255,136,0.75)' },
  card: { flexDirection: 'row', alignItems: 'center', gap: 14, padding: 14, borderRadius: 22, backgroundColor: '#0F2A1D', borderWidth: 1, borderColor: 'rgba(43,255,136,0.25)' },
  avatar: { width: 44, height: 44, borderRadius: 22, alignItems: 'center', justifyContent: 'center' },
  avatarText: { fontFamily: BOLD, fontSize: 15, color: '#06110C' },
  cardName: { fontFamily: BOLD, fontSize: 15, color: '#E8FFF2' },
  cardMeta: { marginTop: 3, fontFamily: MEDIUM, fontSize: 13, color: 'rgba(232,255,242,0.6)' },
  button: { marginTop: 12, height: 58, borderRadius: 29, backgroundColor: GREEN, alignItems: 'center', justifyContent: 'center' },
  action: { fontFamily: BOLD, fontSize: 16, color: '#06110C' },
});
