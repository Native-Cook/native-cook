import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { RollLabel, RollNumber } from './LaundrySpinRoll';
import {
  GARMENTS,
  LOOP,
  PHASES,
  S_ANGLE,
  S_FOG,
  S_LEVEL,
  S_PLASTER,
  S_SLOSH,
  S_SPEED,
  S_SUDS,
  S_TUMBLE,
  easeInOut,
  garmentAt,
  minutesLeft,
  phaseAt,
  progressAt,
  restScene,
  sceneAt,
} from './laundrySpinMotion';
import { LAUNDRY_SKSL } from './laundrySpinShader';

/*
 Laundry Spin Progress — a laundromat app's machine screen ("Suds — Washer 4"): the progress of a
 wash cycle shown through the washer's door. The water level, the tumbling clothes, the suds and
 the spin (clothes pressed to the wall, smeared by motion blur) tell the phase; the phase name,
 the minutes left and a four-step bar roll and fill below.

 Touch: turn the drum by dragging round the door (it follows the finger's angle), flick it and it
 keeps turning and slows down; slow turns tumble the clothes, past ~3 rev/s they plaster to the
 wall. Two seconds after the last touch, once the drum has slowed, the scene eases back to its
 rest (0.9 s) and the loop restarts from t = 0.

 One Canvas, one shader (laundrySpinShader.ts); all motion is uniforms. Reduced motion: the
 washing phase, still; the drum still turns under a finger.
 */

const INK = '#1F2A33';
const MUTE = 'rgba(31,42,51,0.58)';
const ACCENT = '#2F8FB8';
const STEPS = ['Wash', 'Rinse', 'Spin', 'Done'];
const IDLE = 2;
const RETURN = 0.9;
/** The still moment shown under reduced motion (washing). */
const STILL_T = 3.0;
const MODE_AUTO = 0;
const MODE_HAND = 1;
const MODE_RETURN = 2;

function tick() {
  Haptics.selectionAsync().catch(() => {});
}

function thud() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Geo = { w: number; h: number; unit: number; cx: number; cy: number; Rg: number };

function makeGeo(w: number, h: number): Geo {
  'worklet';
  const unit = Math.max(0.74, Math.min(1.15, Math.min(w / 393, h / 852)));
  return { w, h, unit, cx: w / 2, cy: h * 0.45, Rg: Math.min(138 * unit, w * 0.35) };
}

/** A hand-turned drum's scene from what it was when grabbed and how fast it turns now. */
function handScene(base: number[], angle: number, speed: number): number[] {
  'worklet';
  const s = base.slice();
  const v = Math.abs(speed);
  const plaster = Math.min(1, Math.max(0, (v - 2.5) / 2));
  s[S_ANGLE] = angle;
  s[S_SPEED] = v;
  s[S_PLASTER] = plaster * plaster * (3 - 2 * plaster);
  s[S_TUMBLE] = Math.min(1, v / 0.4) * (1 - s[S_PLASTER]);
  s[S_SLOSH] = Math.min(1, v / 0.8) * (1 - s[S_PLASTER]);
  s[S_FOG] = 0;
  return s;
}

function uniformsOf(g: Geo, s: number[], phase: number) {
  'worklet';
  const G: number[][] = [];
  for (let i = 0; i < GARMENTS; i += 1) G.push(garmentAt(i, s));
  return {
    size: [g.w, g.h],
    door: [g.cx, g.cy, g.Rg, g.unit],
    scene: [s[S_ANGLE] % (Math.PI * 2), s[S_SPEED], s[S_LEVEL], s[S_SUDS]],
    extra: [s[S_FOG], s[S_SLOSH], phase, 0],
    g0: [G[0][0], G[0][1], G[0][2], G[0][3]],
    g1: [G[1][0], G[1][1], G[1][2], G[1][3]],
    g2: [G[2][0], G[2][1], G[2][2], G[2][3]],
    g3: [G[3][0], G[3][1], G[3][2], G[3][3]],
    g4: [G[4][0], G[4][1], G[4][2], G[4][3]],
    g5: [G[5][0], G[5][1], G[5][2], G[5][3]],
    st: [G[0][4], G[1][4], G[2][4], G[3][4]],
    st2: [G[4][4], G[5][4], 0, 0],
  };
}

function StepBar({ i, progress }: { i: number; progress: SharedValue<number> }) {
  const fill = useAnimatedStyle(() => ({ transform: [{ scaleX: Math.min(1, Math.max(0, progress.get() * 4 - i)) }] }));
  const label = useAnimatedStyle(() => ({ opacity: progress.get() * 4 >= i - 0.001 ? 1 : 0.45 }));
  return (
    <View style={styles.step}>
      <View style={styles.track}>
        <Animated.View style={[styles.fill, fill]} />
      </View>
      <Animated.Text style={[styles.stepLabel, label]}>{STEPS[i]}</Animated.Text>
    </View>
  );
}

type Motion = {
  now: SharedValue<number>;
  mode: SharedValue<number>;
  autoStart: SharedValue<number>;
  angleOffset: SharedValue<number>;
  lastTouch: SharedValue<number>;
  touching: SharedValue<number>;
  /** Hand state [drum angle, speed rev/s, finger angle], and the scene it started from. */
  hand: SharedValue<number[]>;
  base: SharedValue<number[]>;
  /** Progress and phase frozen while the hand turns the drum. */
  held: SharedValue<number[]>;
  returnFrom: SharedValue<number[]>;
  returnStart: SharedValue<number>;
};

export function LaundrySpinProgress({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const g = useMemo(() => (size.width > 0 ? makeGeo(size.width, size.height) : null), [size]);
  const effect = useMemo(() => Skia.RuntimeEffect.Make(LAUNDRY_SKSL), []);

  const m: Motion = {
    now: useSharedValue(-1),
    mode: useSharedValue(MODE_AUTO),
    autoStart: useSharedValue(0),
    angleOffset: useSharedValue(0),
    lastTouch: useSharedValue(0),
    touching: useSharedValue(0),
    hand: useSharedValue<number[]>([0, 0, 0]),
    base: useSharedValue<number[]>(restScene()),
    held: useSharedValue<number[]>([0, 0]),
    returnFrom: useSharedValue<number[]>(restScene()),
    returnStart: useSharedValue(0),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    const prev = m.now.get();
    const dt = prev < 0 ? 0 : Math.min(0.033, now - prev);
    if (prev < 0) m.autoStart.set(now);
    m.now.set(now);
    const mode = m.mode.get();
    if (mode !== MODE_AUTO) {
      const h = m.hand.get().slice();
      if (m.touching.get() === 0) {
        // a flicked drum keeps turning and slows down
        h[1] *= Math.exp(-dt / 1.2);
        h[0] += h[1] * Math.PI * 2 * dt;
      }
      m.hand.set(h);
      if (mode === MODE_HAND && m.touching.get() === 0 && now - m.lastTouch.get() > IDLE && Math.abs(h[1]) < 0.3 && autoPlay) {
        m.mode.set(MODE_RETURN);
        m.returnStart.set(now);
        m.returnFrom.set(handScene(m.base.get(), h[0], h[1]));
      }
      if (mode === MODE_RETURN && now - m.returnStart.get() >= RETURN) {
        m.mode.set(MODE_AUTO);
        m.autoStart.set(now);
        m.angleOffset.set(h[0] % (Math.PI * 2));
      }
    }
  });

  /** The scene, the loop phase (0 … 1) for the bubbles, progress, phase index. */
  const frame = useDerivedValue(() => {
    const now = m.now.get();
    const mode = m.mode.get();
    if (mode === MODE_HAND) {
      const h = m.hand.get();
      return { s: handScene(m.base.get(), h[0], h[1]), phase: (now % LOOP) / LOOP, progress: m.held.get()[0], index: m.held.get()[1] };
    }
    if (mode === MODE_RETURN) {
      const k = easeInOut(Math.min(1, (now - m.returnStart.get()) / RETURN));
      const from = m.returnFrom.get();
      const rest = reduced ? sceneAt(STILL_T) : restScene();
      const s = from.slice();
      for (let i = 1; i < s.length; i += 1) s[i] = from[i] + (rest[i] - from[i]) * k;
      s[0] = m.hand.get()[0];
      const p0 = m.held.get()[0];
      return { s, phase: (now % LOOP) / LOOP, progress: p0 * (1 - k), index: k > 0.5 ? 0 : m.held.get()[1] };
    }
    const t = reduced ? STILL_T : auto ? (now - m.autoStart.get()) % LOOP : 0;
    const s = sceneAt(t);
    s[S_ANGLE] += m.angleOffset.get();
    return { s, phase: t / LOOP, progress: progressAt(t), index: phaseAt(t) };
  });

  const uniforms = useDerivedValue(() => {
    const f = frame.get();
    return uniformsOf(g ?? makeGeo(1, 1), f.s, f.phase);
  });
  const progress = useDerivedValue(() => frame.get().progress);
  const phase = useDerivedValue(() => frame.get().index);
  const minutes = useDerivedValue(() => minutesLeft(frame.get().progress));
  const plastered = useDerivedValue(() => (m.mode.get() === MODE_HAND && frame.get().s[S_PLASTER] > 0.5 ? 1 : 0));
  const quarterTurns = useDerivedValue(() => (m.mode.get() === MODE_HAND && m.touching.get() === 1 ? Math.floor(m.hand.get()[0] / (Math.PI / 2)) : 0));

  useAnimatedReaction(
    () => plastered.get(),
    (v, prev) => {
      if (prev !== null && v === 1 && prev === 0) scheduleOnRN(thud);
    },
  );
  useAnimatedReaction(
    () => quarterTurns.get(),
    (v, prev) => {
      if (prev !== null && v !== prev && m.touching.get() === 1) scheduleOnRN(tick);
    },
  );

  function fingerAngle(x: number, y: number) {
    'worklet';
    return g ? Math.atan2(-(y - g.cy), x - g.cx) : 0;
  }

  function grab(x: number, y: number) {
    'worklet';
    if (!g) return;
    const f = frame.get();
    if (m.mode.get() !== MODE_HAND) {
      m.base.set(f.s.slice());
      m.held.set([f.progress, f.index]);
      m.hand.set([f.s[S_ANGLE], f.s[S_SPEED], fingerAngle(x, y)]);
    } else {
      const h = m.hand.get();
      m.hand.set([h[0], h[1], fingerAngle(x, y)]);
    }
    m.mode.set(MODE_HAND);
    m.touching.set(1);
    m.lastTouch.set(m.now.get());
  }

  function turn(x: number, y: number) {
    'worklet';
    if (!g || m.touching.get() === 0) return;
    const h = m.hand.get();
    const a = fingerAngle(x, y);
    let d = a - h[2];
    if (d > Math.PI) d -= Math.PI * 2;
    if (d < -Math.PI) d += Math.PI * 2;
    // only a drag far enough from the centre turns the drum (near it, angles jump)
    const r = Math.hypot(x - g.cx, y - g.cy);
    const k = Math.min(1, r / (g.Rg * 0.35));
    const now = m.now.get();
    const dt = Math.max(1 / 120, now - m.lastTouch.get());
    const v = (d * k) / (Math.PI * 2) / dt;
    m.hand.set([h[0] + d * k, h[1] * 0.7 + v * 0.3, a]);
    m.lastTouch.set(now);
  }

  function release() {
    'worklet';
    m.touching.set(0);
    m.lastTouch.set(m.now.get());
  }

  const gesture = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => {
      grab(e.x, e.y);
    })
    .onUpdate((e) => {
      turn(e.x, e.y);
    })
    .onFinalize(() => {
      release();
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Washer 4, seen through its door. Drag round the door to turn the drum.">
          {g && effect ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <Shader source={effect} uniforms={uniforms} />
              </Fill>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 16 }]}>
        <View style={styles.kickerRow}>
          <Text style={styles.kicker}>SUDS · WASHER 4</Text>
          <View style={styles.live}>
            <View style={styles.liveDot} />
            <Text style={styles.liveText}>Live</Text>
          </View>
        </View>
        <Text style={styles.title}>Cottons 40°</Text>
        <Text style={styles.sub}>Hale Street Laundromat · 1,200 rpm spin</Text>
      </View>

      <View pointerEvents="none" style={[styles.card, { bottom: insets.bottom + 16 }]}>
        <View style={styles.cardTop}>
          <RollLabel labels={PHASES} index={phase} line={34} style={styles.phase} />
          <View style={styles.timer}>
            <RollNumber value={minutes} digits={2} line={34} style={styles.minutes} suffix=" min" suffixStyle={styles.minUnit} />
          </View>
        </View>
        <View style={styles.steps}>
          {STEPS.map((s, i) => (
            <StepBar key={s} i={i} progress={progress} />
          ))}
        </View>
        <View style={styles.notify}>
          <Text style={styles.notifyText}>We will ping you when it is done.</Text>
          <View style={styles.toggle}>
            <View style={styles.knob} />
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#ECEDEC', overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24 },
  kickerRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  kicker: { color: ACCENT, fontSize: 11, fontWeight: '700', letterSpacing: 2 },
  live: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 10, height: 24, borderRadius: 12, backgroundColor: 'rgba(47,143,184,0.12)' },
  liveDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: ACCENT },
  liveText: { color: ACCENT, fontSize: 12, fontWeight: '700' },
  title: { color: INK, fontSize: 34, fontWeight: '800', letterSpacing: -0.6, marginTop: 8 },
  sub: { color: MUTE, fontSize: 15, marginTop: 4 },
  card: {
    position: 'absolute',
    left: 16,
    right: 16,
    borderRadius: 26,
    borderCurve: 'continuous',
    padding: 20,
    gap: 16,
    backgroundColor: '#FAFBFB',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(31,42,51,0.1)',
    boxShadow: '0 1px 3px rgba(20,40,60,0.12), 0 16px 36px rgba(20,40,60,0.12), inset 0 1px 0 rgba(255,255,255,0.95)',
  },
  cardTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  phase: { color: INK, fontSize: 26, fontWeight: '800', letterSpacing: -0.4 },
  timer: { alignItems: 'flex-end' },
  minutes: { color: INK, fontSize: 26, fontWeight: '700', fontVariant: ['tabular-nums'] },
  minUnit: { color: MUTE, fontSize: 15, fontWeight: '600' },
  steps: { flexDirection: 'row', gap: 6 },
  step: { flex: 1, gap: 8 },
  track: { height: 6, borderRadius: 3, overflow: 'hidden', backgroundColor: 'rgba(31,42,51,0.09)' },
  fill: { flex: 1, borderRadius: 3, backgroundColor: ACCENT, transformOrigin: 'left' },
  stepLabel: { color: INK, fontSize: 12, fontWeight: '600' },
  notify: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  notifyText: { color: MUTE, fontSize: 13 },
  toggle: { width: 44, height: 26, borderRadius: 13, padding: 2, alignItems: 'flex-end', backgroundColor: ACCENT },
  knob: { width: 22, height: 22, borderRadius: 11, backgroundColor: '#FBFCFC', boxShadow: '0 1px 2px rgba(0,0,0,0.25)' },
});
