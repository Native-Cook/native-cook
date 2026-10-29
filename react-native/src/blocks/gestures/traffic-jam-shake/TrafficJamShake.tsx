import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { C, drawCar, drawHonk, makePaints } from './trafficDraw';
import {
  BOUNDS,
  CARS,
  DIR_X,
  DIR_Y,
  DT,
  LOOP,
  SETTLE,
  TAP_CAR,
  USER_HOLD,
  finger,
  loopWindow,
  makeSim,
  resetSim,
  runSim,
  scaleSim,
  simTime,
  smooth,
  tapCar,
  targetX,
  targetY,
  tremble,
  type Sim,
} from './trafficSim';

// A commute card for an invented app, Laneway: "Heavy traffic on Ring Rd". Its hero is a flat pile
// of chunky extruded cars that wobble, lean, stretch and bump each other, each on its own spring
// (see trafficSim). Drag anywhere to shake the jam; tap a car to make it hop and honk. A port of
// TrafficJamShake.swift, number for number.
//
// autoPlay timeline (LOOP = 8 s), the simulation stepped from rest at t = 0:
//   0.00-0.75  rest (a 0.35 pt engine-idle tremble)
//   0.75-0.95  a ghost finger lands on the red front car
//   0.95       tap: the red car hops 28 pt and honks; it lands at ~1.25 s and bumps its neighbours
//   1.70-1.95  the ghost finger moves to the pile's centre
//   1.95-5.00  it shakes the ground left-right (0.66 s swings, up to 10 pt, smoothstep envelope
//              in over 1.95-2.6 s and out over 4.45-5.0 s); the finger fades out over 5.0-5.25 s
//   2.80-4.80  peak: cars lag, lean, stretch into limos, bump, honk and hop
//   5.00-6.40  the pile settles on its springs
//   6.40-7.20  what is left fades out (smoothstep), so the frame is exactly the rest frame
//   7.20-8.00  rest, identical to t = 0

export { LOOP };

const MARGIN = 20;
/** Bottom card: 18 + row 36 + 14 + bar 10 + 8 + labels 16 + 18 + button 52 + 18. */
const CARD_HEIGHT = 190;
/** Top copy below the safe area: 8 + header 32 + 18 + label 16 + 6 + title 76 + 8 + subtitle 20. */
const TOP_HEIGHT = 184;
const SEGMENTS: [number, string][] = [
  [0.22, C.green],
  [0.14, C.orange],
  [0.3, C.jam],
  [0.12, C.orange],
  [0.22, C.green],
];

type Props = {
  app?: string;
  route?: string;
  label?: string;
  title?: string;
  subtitle?: string;
  hint?: string;
  duration?: string;
  delay?: string;
  arrival?: string;
  action?: string;
  /** Shakes the jam by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

/** Wall-clock milliseconds, for telling a tap from a hold. */
function wallClock() {
  'worklet';
  return Date.now();
}

function haptic(soft: boolean) {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(soft ? Haptics.ImpactFeedbackStyle.Soft : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function TrafficJamShake({
  app = 'Laneway',
  route = 'Home → Studio',
  label = 'MORNING COMMUTE',
  title = 'Heavy traffic\non Ring Rd',
  subtitle = 'Leave at 8:40 to make your 9:15.',
  hint = 'Shake the jam · tap a car to honk',
  duration = '34 min',
  delay = '+12 min',
  arrival = 'Arrive 9:14',
  action = 'Remind me at 8:35',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();

  // Where the pile sits: design point → screen point. Centred, bottom-aligned (room above for hops).
  const cardTop = height - insets.bottom - 16 - CARD_HEIGHT;
  const heroTop = insets.top + TOP_HEIGHT + 12;
  const heroBottom = cardTop - 14 - 16 - 12;
  const bw = BOUNDS.right - BOUNDS.left;
  const bh = BOUNDS.bottom - BOUNDS.top;
  const s = Math.min(1.1, (width - 24) / bw, (heroBottom - heroTop - 30) / bh);
  const ox = width / 2 - ((BOUNDS.left + BOUNDS.right) / 2) * s;
  const oy = heroBottom - BOUNDS.bottom * s;

  const sim = useSharedValue<Sim>(makeSim());
  // Frame time, ms; and the factor the deviations are drawn with plus the loop time (-1 out of it).
  const clock = useSharedValue(0);
  const view = useSharedValue({ k: 0, t: -1 });
  const origin = useSharedValue(-1);
  const auto = useSharedValue(autoPlay && !reduced);
  const still = useSharedValue(reduced);
  // True from a touch until the pile has settled and the loop has started over.
  const manual = useSharedValue(false);
  const manualStart = useSharedValue(0);
  const manualBase = useSharedValue(0);
  // When the residual of a touch started to fade out (-1: not fading).
  const settleStart = useSharedValue(-1);
  const dragging = useSharedValue(false);
  const moved = useSharedValue(false);
  const touchStart = useSharedValue(0);
  // Honks under reduced motion (no simulation): car index and when.
  const stillHonkCar = useSharedValue(-1);
  const stillHonkAt = useSharedValue(0);
  const lastBumps = useSharedValue(0);
  const lastHaptic = useSharedValue(0);
  // Changes on every touch, which restarts the loop behind a pause.
  const [touches, setTouches] = useState(0);

  useEffect(() => {
    auto.set(autoPlay && !reduced);
    still.set(reduced);
  }, [autoPlay, reduced, auto, still]);

  /** Advances the simulation to `now` (ms) and records how it is drawn. */
  const advance = (now: number) => {
    'worklet';
    const st = sim.get();
    if (manual.get()) {
      runSim(st, manualBase.get() + (now - manualStart.get()) / 1000, 0.25);
      const fade = settleStart.get();
      const k = fade >= 0 ? 1 - smooth((now - fade) / 1000, 0, SETTLE) : 1;
      view.set({ k, t: -1 });
      return;
    }
    if (!auto.get()) {
      view.set({ k: 0, t: -1 });
      return;
    }
    if (origin.get() < 0) origin.set(now);
    const t = Math.max(0, ((now - origin.get()) / 1000) % LOOP);
    if (!st.scripted || t + DT < simTime(st)) resetSim(st);
    runSim(st, t, Infinity);
    view.set({ k: loopWindow(t), t });
  };

  useFrameCallback((info) => {
    const now = info.timestamp;
    if (!auto.get() && !manual.get() && stillHonkCar.get() < 0) return;
    // A still honk starts on the first frame after the tap (the clock may have been paused).
    if (stillHonkCar.get() >= 0 && stillHonkAt.get() < 0) stillHonkAt.set(now);
    clock.set(now);
    advance(now);
    const st = sim.get();
    if (manual.get() && st.bumps !== lastBumps.get()) {
      lastBumps.set(st.bumps);
      if (now - lastHaptic.get() > 250) {
        lastHaptic.set(now);
        scheduleOnRN(haptic, true);
      }
    }
  });

  const picture = useDerivedValue(() => {
    const now = clock.get();
    const v = view.get();
    const st = sim.get();
    const k = v.k;
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const paints = makePaints();
    canvas.save();
    canvas.translate(ox, oy);
    canvas.scale(s, s);
    for (let i = 0; i < CARS.length; i++) {
      const c = CARS[i];
      canvas.save();
      canvas.translate(c.x + st.px[i] * k, c.y + st.py[i] * k + (v.t >= 0 ? tremble(i, v.t) : 0));
      canvas.rotate(((c.angle + st.a[i] * k) * 180) / Math.PI, 0, 0);
      drawCar(canvas, paints, c, st.e[i] * k, st.z[i] * k);
      canvas.restore();
    }
    const honkCar = stillHonkCar.get();
    for (let i = 0; i < CARS.length; i++) {
      const c = CARS[i];
      canvas.save();
      canvas.translate(c.x + st.px[i] * k, c.y + st.py[i] * k);
      canvas.rotate(((c.angle + st.a[i] * k) * 180) / Math.PI, 0, 0);
      if (honkCar === i) {
        const at = stillHonkAt.get();
        if (at >= 0) drawHonk(canvas, paints, c, 0, 0, (now - at) / 1000, 1);
      } else if (manual.get() || v.t >= 0) {
        drawHonk(canvas, paints, c, st.e[i] * k, st.z[i] * k, st.honk[i], k);
      }
      canvas.restore();
    }
    canvas.restore();

    // The ghost finger of the loop.
    if (v.t >= 0) {
      const f = finger(v.t);
      if (f.opacity > 0.001) {
        let cx = 0;
        let cy = 0;
        if (f.onTap) {
          const c = CARS[TAP_CAR];
          cx = ox + (c.x + 0.1 * c.w) * s;
          cy = oy + (c.y - c.hb * 0.6) * s;
        } else {
          cx = ox + ((BOUNDS.left + BOUNDS.right) / 2 + 3 * targetX(v.t)) * s;
          cy = oy + ((BOUNDS.top + BOUNDS.bottom) / 2 + 3 * targetY(v.t)) * s;
        }
        const r = 21 * f.scale;
        paints.fill.setColor(Skia.Color(C.ink));
        paints.fill.setAlphaf(0.1 * f.opacity);
        canvas.drawCircle(cx, cy, r, paints.fill);
        paints.line.setColor(Skia.Color(C.ink));
        paints.line.setAlphaf(0.5 * f.opacity);
        paints.line.setStrokeWidth(1.5);
        canvas.drawCircle(cx, cy, r, paints.line);
      }
    }
    return recorder.finishRecordingAsPicture();
  });

  // 2 s after the last touch the pile's residual fades out and the loop starts over from rest.
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  useEffect(() => {
    if (touches === 0) return;
    const list = timers.current;
    list.push(
      setTimeout(() => {
        if (dragging.get()) return;
        stillHonkCar.set(-1);
        if (!manual.get()) return;
        settleStart.set(clock.get());
        list.push(
          setTimeout(() => {
            if (dragging.get()) return;
            scheduleOnUI(() => {
              'worklet';
              settleStart.set(-1);
              resetSim(sim.get());
              origin.set(clock.get());
              manual.set(false);
            });
          }, SETTLE * 1000),
        );
      }, USER_HOLD * 1000),
    );
    return () => {
      list.forEach(clearTimeout);
      timers.current = [];
    };
  }, [touches, dragging, stillHonkCar, manual, settleStart, clock, sim, origin]);

  const touched = () => setTouches((n) => n + 1);

  /** Hands the pile to the finger: from here the simulation runs on real time. */
  const takeOver = (now: number) => {
    'worklet';
    if (!manual.get()) {
      advance(now);
      const st = sim.get();
      scaleSim(st, view.get().k);
      st.scripted = false;
      st.tx = 0;
      st.ty = 0;
      manualBase.set(simTime(st));
      manualStart.set(now);
      lastBumps.set(st.bumps);
      manual.set(true);
    }
    settleStart.set(-1);
  };

  /** The car under a screen point, front to back. */
  const carIndex = (px: number, py: number) => {
    'worklet';
    const st = sim.get();
    const x = (px - ox) / s;
    const y = (py - oy) / s;
    for (let i = CARS.length - 1; i >= 0; i--) {
      const c = CARS[i];
      const ax = c.x + st.px[i];
      const ay = c.y + st.py[i] - st.z[i];
      const top = -(c.hb + c.hc) + 0.82 * c.l * DIR_Y;
      if (x >= ax - c.w / 2 && x <= ax + c.w / 2 + c.l * DIR_X && y >= ay + top && y <= ay + 4) {
        // Inside the box's sloping outline: below the roof line that rises to the right.
        const rel = (x - (ax + c.w / 2)) / (c.l * DIR_X);
        const roof = ay - c.hb - c.hc + Math.max(0, rel) * c.l * DIR_Y * 0.82;
        const floor = ay + Math.max(0, rel) * c.l * DIR_Y + 4;
        if (y >= roof && y <= floor) return i;
      }
    }
    return -1;
  };

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      dragging.set(true);
      moved.set(false);
      touchStart.set(wallClock());
      scheduleOnRN(touched);
    })
    .onUpdate((e) => {
      if (!moved.get() && Math.hypot(e.translationX, e.translationY) > 8) {
        moved.set(true);
        if (!still.get()) {
          takeOver(clock.get());
          scheduleOnRN(haptic, false);
        }
      }
      if (!moved.get() || still.get()) return;
      // Rubber-banded: the ground follows the finger less and less.
      const st = sim.get();
      st.tx = (0.6 * e.translationX) / (1 + Math.abs(e.translationX) / 60) / s;
      st.ty = (0.6 * e.translationY) / (1 + Math.abs(e.translationY) / 60) / s;
    })
    .onFinalize((e) => {
      dragging.set(false);
      if (!moved.get() && wallClock() - touchStart.get() < 350) {
        const i = carIndex(e.x, e.y);
        if (i >= 0) {
          scheduleOnRN(haptic, false);
          const now = clock.get();
          if (still.get()) {
            stillHonkAt.set(-1);
            stillHonkCar.set(i);
          } else {
            takeOver(now);
            const st = sim.get();
            runSim(st, manualBase.get() + (now - manualStart.get()) / 1000, 0.25);
            tapCar(st, i);
          }
        }
      }
      const st = sim.get();
      st.tx = 0;
      st.ty = 0;
      scheduleOnRN(touched);
    });

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View style={styles.screen}>
          <View style={[styles.copy, { paddingTop: insets.top + 8, paddingBottom: insets.bottom + 16 }]} pointerEvents="none">
            <View style={styles.header}>
              <Text style={styles.app}>{app}</Text>
              <View style={styles.route}>
                <Text style={styles.routeText}>{route}</Text>
              </View>
            </View>
            <Text style={styles.label}>{label}</Text>
            <Text style={styles.title}>{title}</Text>
            <Text style={styles.subtitle} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.8}>
              {subtitle}
            </Text>

            <View style={{ flex: 1 }} />

            <Text style={styles.hint}>{hint}</Text>
            <View style={styles.card}>
              <View style={styles.row}>
                <Text style={styles.duration}>{duration}</Text>
                <View style={styles.delay}>
                  <Text style={styles.delayText}>{delay}</Text>
                </View>
                <View style={{ flex: 1 }} />
                <Text style={styles.arrival}>{arrival}</Text>
              </View>
              <View style={styles.bar}>
                {SEGMENTS.map(([f, color], i) => (
                  <View key={i} style={{ flex: f, height: 10, borderRadius: 5, backgroundColor: color }} />
                ))}
              </View>
              <View style={styles.labels}>
                <Text style={[styles.small, styles.left]}>Elm Bridge</Text>
                <Text style={[styles.small, styles.center]}>Ring Rd · 2.4 km jam</Text>
                <Text style={[styles.small, styles.right]}>Studio</Text>
              </View>
              <View style={styles.button} accessible accessibilityRole="button">
                <Text style={styles.action}>{action}</Text>
              </View>
            </View>
          </View>

          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none" accessible accessibilityLabel="A jam of eight cartoon cars">
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.background },
  copy: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, paddingHorizontal: MARGIN },
  header: { height: 32, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  app: { fontSize: 20, fontWeight: '800', color: C.ink },
  route: { height: 28, paddingHorizontal: 12, borderRadius: 14, backgroundColor: C.card, justifyContent: 'center' },
  routeText: { fontSize: 13, fontWeight: '600', color: C.ink },
  label: { marginTop: 18, height: 16, fontSize: 12, lineHeight: 16, fontWeight: '600', letterSpacing: 1.2, color: C.ink, opacity: 0.5 },
  title: { marginTop: 6, height: 76, fontSize: 34, lineHeight: 38, fontWeight: '800', color: C.ink },
  subtitle: { marginTop: 8, height: 20, fontSize: 16, lineHeight: 20, color: C.ink, opacity: 0.62 },
  hint: { height: 16, fontSize: 13, lineHeight: 16, fontWeight: '500', color: C.ink, opacity: 0.45, textAlign: 'center', marginBottom: 14 },
  card: { marginHorizontal: 16 - MARGIN, height: CARD_HEIGHT, borderRadius: 26, backgroundColor: C.card, padding: 18 },
  row: { height: 36, flexDirection: 'row', alignItems: 'center' },
  duration: { fontSize: 30, fontWeight: '800', color: C.ink },
  delay: { marginLeft: 10, height: 24, paddingHorizontal: 9, borderRadius: 12, backgroundColor: C.redSoft, justifyContent: 'center' },
  delayText: { fontSize: 13, fontWeight: '700', color: C.red },
  arrival: { fontSize: 14, fontWeight: '500', color: C.ink, opacity: 0.55 },
  bar: { marginTop: 14, height: 10, flexDirection: 'row', gap: 2 },
  labels: { marginTop: 8, height: 16 },
  small: { position: 'absolute', top: 0, fontSize: 12, lineHeight: 16, fontWeight: '500', color: C.ink, opacity: 0.5 },
  left: { left: 0 },
  center: { left: 0, right: 0, textAlign: 'center' },
  right: { right: 0 },
  button: { marginTop: 18, height: 52, borderRadius: 26, backgroundColor: C.ink, alignItems: 'center', justifyContent: 'center' },
  action: { fontSize: 17, fontWeight: '600', color: '#FFFFFF' },
});
