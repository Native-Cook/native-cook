import { Canvas, Image, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { type Fonts, type Layout, bakeApp, bakeCard, drawAlert, makeLayout } from './parachuteArt';
import { type Canopy, LAND_AT, LIFT_AT, LOOP, type Pose, collapsing, hangingCanopy, loopPose } from './parachuteMotion';

/*
 Parachute Drop Alert — "Parcelpoint", a delivery app whose "your parcel is 4 minutes away" alert
 arrives by parachute.

 The card drops in from above the screen under a coral-and-cream canopy, swinging like a pendulum
 and drifting, lands on the screen with a squash; the canopy deflates, drapes over the card's top
 edge and the wind pulls it away. "Track" and "Later" both send it off: a fresh canopy blooms out of
 its top and lifts it away (parachuteMotion.ts has the timeline). Drag the card and it swings from
 its lines like a pendulum (g 2000 pt/s², 200 pt rope) under the finger; let go and it springs back
 to its place (8 rad/s, damping 0.6) while the swing dies out. 2 s after the last touch it lifts
 away and the loop starts again.
 Reduced motion: no loop; the alert sits in place, Track / Later fade it out (0.3 s), and it fades
 back 2 s later.
*/

type Props = {
  /** Plays the 9 s drop-land-lift loop until touched. */
  autoPlay?: boolean;
};

const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const AUTO = 0;
const TOUCH = 1;
const BACK = 2;
const BACK_FOR = 0.9;
const ROPE = 200;
const GRAVITY = 2000;
const INK = '#1D2B3D';
const MUTE = '#6A7482';
const CORAL = '#E2553C';

type Sim = {
  x: number;
  y: number;
  vx: number;
  vy: number;
  theta: number;
  omega: number;
  grab: number;
  offX: number;
  offY: number;
  lastTouch: number;
  chute: number;
  collapseAt: number;
  backAt: number;
  downAt: number;
  downX: number;
  downY: number;
  moved: number;
  pressed: number;
};

function makeSim(): Sim {
  'worklet';
  return { x: 0, y: 0, vx: 0, vy: 0, theta: 0, omega: 0, grab: 0, offX: 0, offY: 0, lastTouch: 0, chute: 0, collapseAt: -1, backAt: -1, downAt: 0, downX: 0, downY: 0, moved: 0, pressed: -1 };
}

/** Which button (0 Later, 1 Track) a point on the landed card is in, or -1. */
function buttonAt(g: Layout, x: number, y: number) {
  'worklet';
  const lx = x - (g.landX - g.cardW / 2);
  const ly = y - (g.landY - g.cardH / 2);
  const rects = [g.later, g.track];
  for (let i = 0; i < 2; i += 1) {
    const r = rects[i];
    if (lx >= r[0] - 4 && lx <= r[0] + r[2] + 4 && ly >= r[1] - 6 && ly <= r[1] + r[3] + 6) return i;
  }
  return -1;
}

function feel(kind: 'grab' | 'land' | 'send') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'send') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else if (kind === 'land') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  else Haptics.selectionAsync().catch(() => {});
}

export function ParachuteDropAlert({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);

  const fonts = useMemo<Fonts>(
    () => ({
      title: matchFont({ fontFamily: SANS, fontSize: 17, fontWeight: '600' }),
      body: matchFont({ fontFamily: SANS, fontSize: 14, fontWeight: '400' }),
      button: matchFont({ fontFamily: SANS, fontSize: 16, fontWeight: '600' }),
    }),
    [],
  );
  const g = useMemo<Layout | null>(() => (size ? makeLayout(size.width, size.height, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const art = useMemo(() => {
    if (!g) return null;
    const scale = Math.min(3, PixelRatio.get());
    const app = bakeApp(g, Math.min(2, scale));
    const card = bakeCard(g, fonts, scale);
    return app && card ? { app, card } : null;
  }, [g, fonts]);

  const now = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  const mode = useSharedValue(AUTO);
  const sim = useSharedValue<Sim>(makeSim());
  const ignoring = useSharedValue(0);
  /** Reduced motion: [shown 0/1, since]. */
  const quiet = useSharedValue<number[]>([1, -10]);

  useFrameCallback((frame) => {
    const at = frame.timestamp / 1000;
    if (loopStart.get() < 0) loopStart.set(at);
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    if (reduced) {
      const q = quiet.get();
      if (q[0] === 0 && at - q[1] > 2.3) quiet.set([1, at]);
    }
    const m = mode.get();
    if (m === TOUCH && g) {
      const s = sim.get();
      const n = 4;
      const h = dt / n;
      for (let i = 0; i < n; i += 1) {
        let ax = 0;
        if (s.grab === 0) {
          // Back to its place on a spring; the swing keeps going and dies out.
          const w = 8;
          const z = 0.6;
          ax = -w * w * (s.x - g.landX) - 2 * z * w * s.vx;
          const ay = -w * w * (s.y - g.landY) - 2 * z * w * s.vy;
          s.vx += ax * h;
          s.vy += ay * h;
          s.x += s.vx * h;
          s.y += s.vy * h;
        }
        // A pendulum hanging from the card's lines, pushed by the card's sideways acceleration.
        const alpha = -(GRAVITY / ROPE) * Math.sin(s.theta) - 1.1 * s.omega - (s.grab === 1 ? 0 : (ax / ROPE) * Math.cos(s.theta));
        s.omega += alpha * h;
        s.theta += s.omega * h;
      }
      s.theta = Math.max(-0.7, Math.min(0.7, s.theta));
      // A chute still open lands with the card, then deflates.
      if (s.grab === 0 && s.chute === 1 && s.collapseAt < 0 && Math.hypot(s.x - g.landX, s.y - g.landY) < 4 && Math.hypot(s.vx, s.vy) < 80) {
        s.collapseAt = at;
        scheduleOnRN(feel, 'land');
      }
      if (s.grab === 0 && at - s.lastTouch > 2) {
        s.backAt = at;
        mode.set(BACK);
      }
    } else if (m === BACK) {
      const s = sim.get();
      if (at - s.backAt >= BACK_FOR) {
        sim.set(makeSim());
        mode.set(AUTO);
        loopStart.set(at);
      }
    }
    now.set(at);
  });

  /** Takes the card from wherever the loop has it. */
  function takeOver(at: number, p: Pose) {
    'worklet';
    const s = makeSim();
    s.x = p.cardX;
    s.y = p.cardY;
    s.theta = p.cardRot / 0.6;
    s.lastTouch = at;
    const t = auto ? (((at - loopStart.get()) % LOOP) + LOOP) % LOOP : 5;
    if (t < LAND_AT || t >= LIFT_AT) s.chute = 1;
    else if (t < LAND_AT + 1.2) {
      s.chute = 1;
      s.collapseAt = at - (t - LAND_AT);
    }
    sim.set(s);
    mode.set(TOUCH);
  }

  const pose = useDerivedValue<Pose | null>(() => {
    if (!g) return null;
    const at = now.get();
    const m = mode.get();
    if (reduced) {
      const p = loopPose(5.5, g);
      const q = quiet.get();
      const k = Math.min(1, Math.max(0, (at - q[1]) / 0.3));
      p.fade = q[0] === 1 ? k : 1 - k;
      return p;
    }
    if (m === AUTO) {
      const t = auto ? (((at - loopStart.get()) % LOOP) + LOOP) % LOOP : 5.5;
      return loopPose(t, g);
    }
    const s = sim.get();
    const breath = Math.sin(2 * Math.PI * 2.1 * at);
    if (m === BACK) {
      const p = loopPose(LIFT_AT + (8.0 - LIFT_AT) * Math.min(1, (at - s.backAt) / BACK_FOR), g);
      if (s.collapseAt >= 0 && at - s.collapseAt < 1.2) p.canopies.push(collapsing(g, hangingCanopy(g, g.landX, g.landY, 0, breath, 1), g.landX, g.landY, at - s.collapseAt));
      return p;
    }
    const canopies: Canopy[] = [];
    if (s.chute === 1) {
      if (s.collapseAt < 0) canopies.push(hangingCanopy(g, s.x, s.y, s.theta, breath, 1));
      else if (at - s.collapseAt < 1.2) canopies.push(collapsing(g, hangingCanopy(g, g.landX, g.landY, 0, breath, 1), s.x, s.y, at - s.collapseAt));
    }
    return { cardX: s.x, cardY: s.y, cardRot: s.theta * 0.6, sx: 1, sy: 1, shown: 1, scrim: 0.36, fade: 1, canopies };
  });

  function dismiss(at: number) {
    'worklet';
    if (reduced) {
      quiet.set([0, at]);
      return;
    }
    const s = sim.get();
    s.backAt = at;
    if (mode.get() === AUTO) {
      s.collapseAt = -1;
      s.x = g ? g.landX : 0;
    }
    mode.set(BACK);
  }

  const pan = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => {
      const p = pose.get();
      if (!g || !p || p.shown === 0 || p.fade < 0.5 || mode.get() === BACK) {
        ignoring.set(1);
        return;
      }
      const inside = Math.abs(e.x - p.cardX) < g.cardW / 2 + 10 && Math.abs(e.y - p.cardY) < g.cardH / 2 + 10;
      if (!inside) {
        ignoring.set(1);
        return;
      }
      ignoring.set(0);
      const at = now.get();
      if (!reduced && mode.get() === AUTO) takeOver(at, p);
      const s = sim.get();
      s.downAt = at;
      s.downX = e.x;
      s.downY = e.y;
      s.moved = 0;
      s.pressed = Math.abs(p.cardRot) < 0.05 && Math.hypot(p.cardX - g.landX, p.cardY - g.landY) < 6 ? buttonAt(g, e.x, e.y) : -1;
      if (reduced) return;
      s.grab = 1;
      s.offX = s.x - e.x;
      s.offY = s.y - e.y;
      s.vx = 0;
      s.vy = 0;
      s.lastTouch = at;
      scheduleOnRN(feel, 'grab');
    })
    .onUpdate((e) => {
      if (ignoring.get() === 1 || reduced) return;
      const s = sim.get();
      const at = now.get();
      s.moved = Math.max(s.moved, Math.hypot(e.x - s.downX, e.y - s.downY));
      if (s.moved > 10) s.pressed = -1;
      const nx = e.x + s.offX;
      const ny = e.y + s.offY;
      const dt = Math.max(1 / 240, at - s.lastTouch);
      const ax = (e.velocityX - s.vx) / dt;
      // The finger's sideways acceleration swings the card.
      s.omega += (-(ax / ROPE) * Math.cos(s.theta)) * Math.min(dt, 1 / 30);
      s.x = nx;
      s.y = ny;
      s.vx = e.velocityX;
      s.vy = e.velocityY;
      s.lastTouch = at;
    })
    .onFinalize(() => {
      if (ignoring.get() === 1) {
        ignoring.set(0);
        return;
      }
      const s = sim.get();
      const at = now.get();
      const tap = s.moved < 10 && at - s.downAt < 0.35 && s.pressed >= 0;
      s.grab = 0;
      s.lastTouch = at;
      s.vx = Math.max(-2500, Math.min(2500, s.vx));
      s.vy = Math.max(-2500, Math.min(2500, s.vy));
      if (tap) {
        s.pressed = -1;
        scheduleOnRN(feel, 'send');
        dismiss(at);
      }
      s.pressed = -1;
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size?.width ?? 1, size?.height ?? 1));
    const p = pose.get();
    if (g && art && p) drawAlert(canvas, g, art.card, p, mode.get() === TOUCH ? sim.get().pressed : -1);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || width !== size.width || height !== size.height) setSize({ width, height });
  };

  const rows = [
    ['Linen napkins, set of 4', 'Parcel locker 14 · Thu', '2 items'],
    ['Warm desk lamp bulb', 'Front door · Fri', '1 item'],
    ['Oat milk frother', 'Front door · Sat', '1 item'],
  ];

  return (
    <View style={styles.root} onLayout={onLayout}>
      {g && art ? (
        <>
          <View pointerEvents="none" style={StyleSheet.absoluteFill}>
            <Canvas style={StyleSheet.absoluteFill}>
              <Image image={art.app} x={0} y={0} width={g.w} height={g.h} fit="fill" />
            </Canvas>
          </View>

          <View pointerEvents="none" style={[styles.header, { top: insets.top + 14 }]}>
            <Text style={styles.kicker}>PARCELPOINT</Text>
            <Text style={styles.title}>Package arriving</Text>
            <Text style={styles.subtitle}>Today · 2 deliveries on the way</Text>
          </View>

          <View pointerEvents="none" style={[styles.card1, { top: g.card1Y, height: g.card1H }]}>
            <View style={styles.card1Head}>
              <View style={styles.flex}>
                <Text style={styles.rowTitle} numberOfLines={1}>
                  Ceramic mug set
                </Text>
                <Text style={styles.rowSub} numberOfLines={1}>
                  from Kiln & Clay · 2 items
                </Text>
              </View>
              <View style={styles.eta}>
                <Text style={styles.etaLabel}>ETA</Text>
                <Text style={styles.etaValue}>6:42 pm</Text>
              </View>
            </View>
          </View>

          <View pointerEvents="none" style={[styles.card1Abs, { top: g.card1Y, height: g.card1H }]}>
            {['Packed', 'Shipped', 'Out for delivery', 'Arriving'].map((label, i) => {
              const x = 30 + ((g.w - 32 - 60) * i) / 3;
              const place = i === 0 ? { left: x - 10, textAlign: 'left' as const } : i === 3 ? { left: x - 74, textAlign: 'right' as const } : { left: x - 42 };
              return (
                <Text key={label} style={[styles.stop, place, i === 3 ? styles.stopOn : null]} numberOfLines={1}>
                  {label}
                </Text>
              );
            })}
            <Text style={styles.courier}>Courier Leo · Van 12 · 0.6 mi away</Text>
          </View>

          <Text pointerEvents="none" style={[styles.section, { top: g.sectionY }]}>
            Also coming
          </Text>
          {rows.slice(0, g.rows).map(([title, sub, meta], i) => (
            <View key={title} pointerEvents="none" style={[styles.row, { top: g.rowsY + i * 78 }]}>
              <View style={styles.flex}>
                <Text style={styles.rowTitle} numberOfLines={1}>
                  {title}
                </Text>
                <Text style={styles.rowSub} numberOfLines={1}>
                  {sub}
                </Text>
              </View>
              <Text style={styles.meta}>{meta}</Text>
            </View>
          ))}

          <View pointerEvents="none" style={[styles.tabs, { top: g.tabY + 32 }]}>
            {['Home', 'Track', 'Send', 'Profile'].map((label, i) => (
              <Text key={label} style={[styles.tab, i === 1 ? styles.tabOn : null]}>
                {label}
              </Text>
            ))}
          </View>

          <GestureDetector gesture={pan}>
            <View
              style={StyleSheet.absoluteFill}
              accessible
              accessibilityRole="alert"
              accessibilityLabel="Your parcel is 4 min away. Leo is on Linden Street now. Door code 2417 is ready. Track or Later."
            >
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </View>
          </GestureDetector>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#F3EEE7', overflow: 'hidden' },
  flex: { flex: 1 },
  header: { position: 'absolute', left: 24, right: 24, gap: 4 },
  kicker: { color: CORAL, fontSize: 12, fontWeight: '700', letterSpacing: 3 },
  title: { color: INK, fontSize: 34, fontWeight: '700', letterSpacing: -0.6 },
  subtitle: { color: '#3E4B5C', fontSize: 15, fontWeight: '500' },
  card1: { position: 'absolute', left: 16, right: 16, paddingLeft: 72, paddingRight: 16, paddingTop: 18 },
  card1Abs: { position: 'absolute', left: 16, right: 16 },
  card1Head: { flexDirection: 'row', alignItems: 'flex-start', gap: 8 },
  eta: { alignItems: 'flex-end' },
  etaLabel: { color: MUTE, fontSize: 11, fontWeight: '700', letterSpacing: 1.2 },
  etaValue: { color: INK, fontSize: 20, fontWeight: '700', fontVariant: ['tabular-nums'] },
  rowTitle: { color: INK, fontSize: 17, fontWeight: '600' },
  rowSub: { color: MUTE, fontSize: 13, fontWeight: '500', marginTop: 2 },
  stop: { position: 'absolute', top: 116, width: 84, textAlign: 'center', color: MUTE, fontSize: 11, fontWeight: '600' },
  stopOn: { color: INK },
  courier: { position: 'absolute', left: 16, top: 148, color: INK, fontSize: 13, fontWeight: '500' },
  section: { position: 'absolute', left: 24, color: INK, fontSize: 17, fontWeight: '700' },
  row: { position: 'absolute', left: 16, right: 16, height: 68, paddingLeft: 72, paddingRight: 18, flexDirection: 'row', alignItems: 'center' },
  meta: { color: MUTE, fontSize: 13, fontWeight: '600', fontVariant: ['tabular-nums'] },
  tabs: { position: 'absolute', left: 0, right: 0, flexDirection: 'row' },
  tab: { flex: 1, textAlign: 'center', color: '#8A93A0', fontSize: 11, fontWeight: '600' },
  tabOn: { color: CORAL },
});
