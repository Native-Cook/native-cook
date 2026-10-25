import { Canvas, Fill, Path, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  type SharedValue,
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { RollLabel, RollNumber } from './PotteryWheelShapeRoll';
import {
  COS_E,
  FAST,
  LOOP,
  P_H,
  PHASES,
  PT_PER_CM,
  type PotteryGeo,
  SLOW,
  SPRAY_N,
  SPRAY_PERIOD,
  easeInOut,
  fingerOnScreen,
  fingersAt,
  hash,
  makeGeo,
  mixPose,
  phaseAt,
  poseAt,
  radiusAt,
  restPose,
  settlePose,
  shapeByFinger,
  speedAt,
  spinAt,
  sprayAt,
  uniformsOf,
  vasePose,
  widest,
  wobbleX,
} from './potteryWheelShapeMotion';
import { POTTERY_SKSL } from './potteryWheelShapeShader';

/*
 Pottery Wheel Shape — a ceramics-class screen ("Kiln Club — Throw a vase") with a lump of wet
 clay turning on a wheel. The whole studio is one shader (potteryWheelShapeShader.ts); slip drops
 are one Path rebuilt per frame on the UI thread; the readouts are rolling digits in RN views.

 Touch: drag on either side of the clay. Pressing towards the middle squeezes the wall at that
 height (and pushes the clay up), dragging up along the wall pulls it taller, down squats it; slip
 flies off while the finger works and a selection tick marks every whole centimetre of height. The
 wheel speeds up under the hand. Two seconds after the last touch the clay eases back to the lump
 (0.9 s, ease-in-out) and the loop starts again from t = 0.

 Reduced motion: the finished vase, still, no spray; the clay can still be shaped by hand.
 */

const BACKGROUND = '#130F0C';
const INK = '#F3E7DA';
const MUTE = 'rgba(243,231,218,0.6)';
const ACCENT = '#E39A5B';
const LINE = 'rgba(255,236,218,0.12)';
/** Seconds after the last touch before the clay goes back to the lump, and how long that takes. */
const IDLE = 2;
const RETURN = 0.9;
/** Under reduced motion the screen shows this moment of the loop (the finished vase). */
const STILL_T = 6.6;

const MODE_AUTO = 0;
const MODE_HAND = 1;
const MODE_RETURN = 2;

type Frame = {
  pose: number[];
  spin: number;
  speed: number;
  wob: number;
  pads: number[];
  phase: number;
  /** Spray: strength, height share of H, side bias, and the time its drops are aged by. */
  spray: number[];
};

function tick() {
  Haptics.selectionAsync().catch(() => {});
}

function soft() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** Slip drops flung off the turning wall: pure functions of the clock and hash(i). */
function sprayPath(g: PotteryGeo, f: Frame, clock: number, auto: boolean) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const u = g.unit;
  for (let i = 0; i < SPRAY_N; i += 1) {
    const age = (clock / SPRAY_PERIOD + hash(i)) % 1;
    let strength = f.spray[0];
    let share = f.spray[1];
    if (auto) {
      // the drop was thrown at its own birth time, by the script's spray at that moment
      const born = (((clock - age * SPRAY_PERIOD) % LOOP) + LOOP) % LOOP;
      const s = sprayAt(born);
      strength = s[0];
      share = s[1];
    }
    if (hash(i + 71) > strength || age > 0.8) continue;
    const side = f.spray[2] !== 0 ? (hash(i + 13) < 0.8 ? f.spray[2] : -f.spray[2]) : hash(i + 13) < 0.5 ? -1 : 1;
    const h0 = Math.max(4, Math.min(f.pose[P_H] - 2, f.pose[P_H] * share + (hash(i + 29) - 0.5) * 26));
    const r = radiusAt(f.pose, h0) * u;
    const vx = (70 + 120 * hash(i + 3)) * u;
    const vy = (30 + 90 * hash(i + 5)) * u;
    const a = age * SPRAY_PERIOD;
    const x = g.cx + f.wob + side * (r + vx * a);
    const y = g.baseY - h0 * u * COS_E - vy * a + 0.5 * 900 * u * a * a;
    const size = (1.2 + 2.2 * hash(i + 41)) * (1 - age * 0.7) * u;
    b.addCircle(x, y, size);
    // a short tail along the flight
    b.addCircle(x - side * vx * 0.012, y + (vy - 900 * u * a) * 0.012, size * 0.6);
  }
  return b.build();
}

/** The frame the loop shows at t (wheel angle offset by `offset`). */
function autoFrame(g: PotteryGeo, t: number, offset: number): Frame {
  'worklet';
  const pose = poseAt(t);
  const spin = spinAt(t) + offset;
  const wob = wobbleX(pose, spin, g.unit);
  const f = fingersAt(t, pose);
  return {
    pose,
    spin,
    speed: speedAt(t),
    wob,
    pads: [...fingerOnScreen(g, pose, f, 0, wob), ...fingerOnScreen(g, pose, f, 4, wob)],
    phase: phaseAt(t),
    spray: [0, 0.5, 0],
  };
}

type Motion = {
  now: SharedValue<number>;
  mode: SharedValue<number>;
  autoStart: SharedValue<number>;
  spinOffset: SharedValue<number>;
  lastTouch: SharedValue<number>;
  touching: SharedValue<number>;
  hand: SharedValue<number[]>;
  handSpin: SharedValue<number>;
  handSpeed: SharedValue<number>;
  /** The finger on screen [x, y, alpha] and the spray it raises [strength, share, side]. */
  pad: SharedValue<number[]>;
  spray: SharedValue<number[]>;
  returnFrom: SharedValue<number[]>;
  returnStart: SharedValue<number>;
};

export function PotteryWheelShape({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const g = useMemo(() => (size.width > 0 ? makeGeo(size.width, size.height) : null), [size]);
  const effect = useMemo(() => Skia.RuntimeEffect.Make(POTTERY_SKSL), []);

  const m: Motion = {
    now: useSharedValue(-1),
    mode: useSharedValue(MODE_AUTO),
    autoStart: useSharedValue(0),
    spinOffset: useSharedValue(0),
    lastTouch: useSharedValue(0),
    touching: useSharedValue(0),
    hand: useSharedValue<number[]>(restPose()),
    handSpin: useSharedValue(0),
    handSpeed: useSharedValue(SLOW),
    pad: useSharedValue<number[]>([0, 0, 0]),
    spray: useSharedValue<number[]>([0, 0.5, 0]),
    returnFrom: useSharedValue<number[]>(restPose()),
    returnStart: useSharedValue(0),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    const prev = m.now.get();
    const dt = prev < 0 ? 0 : Math.min(0.05, now - prev);
    if (prev < 0) m.autoStart.set(now);
    m.now.set(now);
    const mode = m.mode.get();
    if (mode === MODE_HAND) {
      // the wheel spins up under the hand; the pose relaxes back inside its limits once let go
      const target = m.touching.get() === 1 ? FAST : m.handSpeed.get();
      m.handSpeed.set(m.handSpeed.get() + (target - m.handSpeed.get()) * Math.min(1, dt * 3));
      if (m.touching.get() === 0) {
        m.hand.set(settlePose(m.hand.get(), Math.min(1, dt * 8)));
        const pd = m.pad.get();
        m.pad.set([pd[0], pd[1], Math.max(0, pd[2] - dt * 4)]);
        const sp = m.spray.get();
        m.spray.set([Math.max(0, sp[0] - dt * 3), sp[1], sp[2]]);
        if (now - m.lastTouch.get() > IDLE) {
          m.mode.set(MODE_RETURN);
          m.returnStart.set(now);
          m.returnFrom.set(m.hand.get());
        }
      } else {
        const sp = m.spray.get();
        m.spray.set([sp[0] * Math.exp(-dt * 2.5), sp[1], sp[2]]);
      }
    } else if (mode === MODE_RETURN) {
      m.handSpeed.set(m.handSpeed.get() + (SLOW - m.handSpeed.get()) * Math.min(1, dt * 4));
      if (now - m.returnStart.get() >= RETURN) {
        m.mode.set(MODE_AUTO);
        m.autoStart.set(now);
        m.spinOffset.set(m.handSpin.get() % (Math.PI * 2));
      }
    }
    if (mode !== MODE_AUTO) m.handSpin.set(m.handSpin.get() + m.handSpeed.get() * Math.PI * 2 * dt);
  });

  const frame = useDerivedValue<Frame | null>(() => {
    const now = m.now.get();
    if (!g) return null;
    const mode = m.mode.get();
    if (mode === MODE_AUTO) {
      if (reduced) {
        const f = autoFrame(g, STILL_T, 0);
        f.pose = vasePose();
        f.spin = 0;
        f.speed = 0;
        f.pads = [0, 0, 0, 1, 0, 0, 0, 1];
        return f;
      }
      if (auto) return autoFrame(g, (now - m.autoStart.get()) % LOOP, m.spinOffset.get());
      // without autoPlay the lump just turns slowly
      const f = autoFrame(g, 0, 0);
      f.spin = m.spinOffset.get() + SLOW * Math.PI * 2 * (now - m.autoStart.get());
      f.wob = wobbleX(f.pose, f.spin, g.unit);
      return f;
    }
    let pose = m.hand.get();
    let padAlpha = m.pad.get()[2];
    let phase = PHASES.length;
    if (mode === MODE_RETURN) {
      const k = easeInOut(Math.min(1, (now - m.returnStart.get()) / RETURN));
      pose = mixPose(m.returnFrom.get(), reduced ? vasePose() : restPose(), k);
      padAlpha = 0;
      phase = 0;
    }
    const spin = m.handSpin.get();
    const pd = m.pad.get();
    return {
      pose,
      spin,
      speed: m.handSpeed.get(),
      wob: wobbleX(pose, spin, g.unit),
      pads: [pd[0], pd[1], padAlpha, 15 * g.unit, 0, 0, 0, 1],
      phase,
      spray: reduced ? [0, 0.5, 0] : m.spray.get(),
    };
  });

  const uniforms = useDerivedValue(() => {
    const f = frame.get();
    if (!g || !f) return uniformsOf(makeGeo(1, 1), restPose(), 0, 0, 0, [0, 0, 0, 1, 0, 0, 0, 1]);
    return uniformsOf(g, f.pose, f.spin, f.speed, f.wob, f.pads);
  });

  const drops = useDerivedValue(() => {
    const f = frame.get();
    if (!g || !f || reduced) return Skia.PathBuilder.Make().build();
    const isAuto = m.mode.get() === MODE_AUTO && auto;
    const clock = isAuto ? (m.now.get() - m.autoStart.get()) % LOOP : m.now.get();
    return sprayPath(g, f, clock, isAuto);
  });

  const heightCm = useDerivedValue(() => {
    const f = frame.get();
    return f ? f.pose[P_H] / PT_PER_CM : 0;
  });
  const widthCm = useDerivedValue(() => {
    const f = frame.get();
    return f ? widest(f.pose) / PT_PER_CM : 0;
  });
  const rpm = useDerivedValue(() => {
    const f = frame.get();
    return f ? f.speed * 60 : 0;
  });
  const phase = useDerivedValue(() => frame.get()?.phase ?? 0);

  // A selection tick for every whole centimetre the hand adds or takes away.
  useAnimatedReaction(
    () => (m.mode.get() === MODE_HAND && m.touching.get() === 1 ? Math.round(heightCm.get()) : -1),
    (cm, prev) => {
      if (cm >= 0 && prev !== null && prev >= 0 && cm !== prev) scheduleOnRN(tick);
    },
  );

  function takeHold(x: number, y: number) {
    'worklet';
    const f = frame.get();
    const now = m.now.get();
    if (f && m.mode.get() !== MODE_HAND) {
      m.hand.set(f.pose.slice());
      m.handSpin.set(f.spin);
      m.handSpeed.set(f.speed);
    }
    m.mode.set(MODE_HAND);
    m.touching.set(1);
    m.lastTouch.set(now);
    m.pad.set([x, y, 1]);
  }

  function work(x: number, y: number, dx: number, dy: number, vx: number, vy: number) {
    'worklet';
    if (!g) return;
    m.hand.set(shapeByFinger(g, m.hand.get(), x, y, dx, dy));
    m.pad.set([x, y, 1]);
    m.lastTouch.set(m.now.get());
    const H = m.hand.get()[P_H];
    const share = Math.min(1, Math.max(0, (g.baseY - y) / (g.unit * COS_E) / H));
    const s = Math.min(1, Math.hypot(vx, vy) / 900);
    m.spray.set([Math.max(m.spray.get()[0], s), share, x >= g.cx ? 1 : -1]);
  }

  function letGo() {
    'worklet';
    m.touching.set(0);
    m.lastTouch.set(m.now.get());
  }

  const gesture = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => {
      takeHold(e.x, e.y);
      scheduleOnRN(soft);
    })
    .onChange((e) => {
      work(e.x, e.y, e.changeX, e.changeY, e.velocityX, e.velocityY);
    })
    .onFinalize(() => {
      letGo();
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Clay on a potter's wheel. Drag the wall in, up or down to shape it.">
          {g && effect ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <Shader source={effect} uniforms={uniforms} />
              </Fill>
              <Path path={drops} color="rgba(150,98,72,0.9)" />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 16 }]}>
        <Text style={styles.kicker}>KILN CLUB · LESSON 4 OF 8</Text>
        <Text style={styles.title}>Throw a vase</Text>
        <Text style={styles.sub}>Keep your hands wet. Press the wall in, then pull it up slowly.</Text>
        <View style={styles.pill}>
          <View style={styles.dot} />
          <RollLabel labels={[...PHASES, 'Shaping by hand']} index={phase} line={20} style={styles.pillText} />
        </View>
      </View>

      <View pointerEvents="none" style={[styles.card, { bottom: insets.bottom + 16 }]}>
        <View style={styles.stat}>
          <Text style={styles.statLabel}>HEIGHT</Text>
          <RollNumber value={heightCm} digits={2} line={28} style={styles.statValue} suffix=" cm" suffixStyle={styles.unit} />
        </View>
        <View style={styles.rule} />
        <View style={styles.stat}>
          <Text style={styles.statLabel}>WIDEST</Text>
          <RollNumber value={widthCm} digits={2} line={28} style={styles.statValue} prefix="Ø " suffix=" cm" suffixStyle={styles.unit} />
        </View>
        <View style={styles.rule} />
        <View style={styles.stat}>
          <Text style={styles.statLabel}>WHEEL</Text>
          <RollNumber value={rpm} digits={3} line={28} style={styles.statValue} suffix=" rpm" suffixStyle={styles.unit} />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BACKGROUND, overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24 },
  kicker: { color: ACCENT, fontSize: 11, fontWeight: '700', letterSpacing: 2 },
  title: { color: INK, fontSize: 34, fontWeight: '800', letterSpacing: -0.6, marginTop: 8 },
  sub: { color: MUTE, fontSize: 15, lineHeight: 21, marginTop: 6, maxWidth: 300 },
  pill: {
    marginTop: 16,
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    height: 32,
    paddingHorizontal: 12,
    borderRadius: 16,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: LINE,
    backgroundColor: 'rgba(255,236,218,0.06)',
  },
  dot: { width: 6, height: 6, borderRadius: 3, backgroundColor: ACCENT, boxShadow: '0 0 6px rgba(227,154,91,0.8)' },
  pillText: { color: INK, fontSize: 13, fontWeight: '600' },
  card: {
    position: 'absolute',
    left: 16,
    right: 16,
    height: 84,
    borderRadius: 24,
    borderCurve: 'continuous',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    backgroundColor: '#211913',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,236,218,0.16)',
    boxShadow: '0 1px 3px rgba(0,0,0,0.35), 0 16px 36px rgba(0,0,0,0.35), inset 0 1px 0 rgba(255,236,218,0.08)',
  },
  stat: { flex: 1, gap: 4 },
  statLabel: { color: MUTE, fontSize: 11, fontWeight: '600', letterSpacing: 1.2 },
  statValue: { color: INK, fontSize: 22, fontWeight: '700', fontVariant: ['tabular-nums'] },
  unit: { color: MUTE, fontSize: 14, fontWeight: '600' },
  rule: { width: StyleSheet.hairlineWidth, height: 40, backgroundColor: LINE, marginHorizontal: 12 },
});
