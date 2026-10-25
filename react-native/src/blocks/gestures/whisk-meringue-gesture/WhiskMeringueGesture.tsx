import { BlurMask, Canvas, Fill, Path, Shader, Skia, SweepGradient, vec } from '@shopify/react-native-skia';
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

import { RollLabel, RollNumber } from './WhiskMeringueRoll';
import {
  type BowlGeo,
  F_AGIT,
  F_FOAM,
  F_HX,
  F_HY,
  F_LIFT,
  F_LPEAK,
  F_LX,
  F_LY,
  F_PEAK,
  F_SWIRL,
  LOOP,
  SQUASH,
  STAGES,
  easeInOut,
  makeGeo,
  mixPose,
  poseAt,
  restPose,
  stageOf,
  stiffPose,
  whiskPaths,
} from './whiskMeringueMotion';
import { WHISK_SKSL } from './whiskMeringueShader';

/*
 Whisk Meringue Gesture — a baking app's step ("Whisk — Step 3 of 6: stiff peaks"): a glass bowl
 of egg whites seen from above. Draw circles in the bowl and the whisk follows the finger on a
 spring; the faster it circles, the faster the whites foam (clear → foamy → soft → firm → stiff),
 the contents swirl behind it and the progress ring advances. Stop, and the foam holds while its
 peaks slowly relax; two seconds after the last touch the bowl eases back to fresh whites (0.9 s,
 the bubbles pop cell by cell) and the loop starts again from t = 0.

 One Canvas holds the bowl (whiskMeringueShader.ts) and the whisk (two paths rebuilt per frame on
 the UI thread, with a blurred shadow); a second small Canvas draws the progress ring in the card.
 Reduced motion: the stiff meringue, still; the whisk still answers the finger.
 */

const INK = '#2A2520';
const MUTE = 'rgba(42,37,32,0.6)';
const ACCENT = '#D98E2B';
const CARD = '#FBF7F0';
const IDLE = 2;
const RETURN = 0.9;
const MODE_AUTO = 0;
const MODE_HAND = 1;
const MODE_RETURN = 2;
/** How much foam one radian of whisking adds, and the finger's spring (per second²). */
const WORK = 0.028;
const STIFF = 260;
const DAMP = 26;
const RING = 64;

function tickHaptic() {
  Haptics.selectionAsync().catch(() => {});
}

function softHaptic() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** A finger in bowl units, kept inside the bowl with a rubber band past 0.8 of the radius. */
function fingerInBowl(g: BowlGeo, x: number, y: number): [number, number] {
  'worklet';
  const bx = (x - g.cx) / g.R;
  const by = (y - g.cy) / (g.R * SQUASH);
  const r = Math.hypot(bx, by);
  const lim = 0.8;
  if (r <= lim) return [bx, by];
  const over = r - lim;
  const rr = lim + over / (1 + over / 0.12);
  return [(bx / r) * rr, (by / r) * rr];
}

type Motion = {
  now: SharedValue<number>;
  mode: SharedValue<number>;
  autoStart: SharedValue<number>;
  lastTouch: SharedValue<number>;
  touching: SharedValue<number>;
  hand: SharedValue<number[]>;
  /** The whisk head's velocity under the finger (bowl units / s), and where the finger is. */
  vel: SharedValue<number[]>;
  target: SharedValue<number[]>;
  returnFrom: SharedValue<number[]>;
  returnStart: SharedValue<number>;
};

export function WhiskMeringueGesture({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const cardH = 96;
  const g = useMemo(
    () => (size.width > 0 ? makeGeo(size.width, size.height, insets.top + 150, size.height - insets.bottom - 16 - cardH - 12) : null),
    [size, insets.top, insets.bottom],
  );
  const effect = useMemo(() => Skia.RuntimeEffect.Make(WHISK_SKSL), []);
  const ringPath = useMemo(() => Skia.PathBuilder.Make().addArc(Skia.XYWHRect(5, 5, RING - 10, RING - 10), -90, 359.9).build(), []);

  const m: Motion = {
    now: useSharedValue(-1),
    mode: useSharedValue(MODE_AUTO),
    autoStart: useSharedValue(0),
    lastTouch: useSharedValue(0),
    touching: useSharedValue(0),
    hand: useSharedValue<number[]>(restPose()),
    vel: useSharedValue<number[]>([0, 0]),
    target: useSharedValue<number[]>([0, 0]),
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
      const p = m.hand.get().slice();
      const v = m.vel.get();
      const tg = m.target.get();
      let vx = v[0];
      let vy = v[1];
      if (m.touching.get() === 1) {
        vx += ((tg[0] - p[F_HX]) * STIFF - vx * DAMP) * dt;
        vy += ((tg[1] - p[F_HY]) * STIFF - vy * DAMP) * dt;
      } else {
        vx *= Math.exp(-dt * 10);
        vy *= Math.exp(-dt * 10);
      }
      const ox = p[F_HX];
      const oy = p[F_HY];
      p[F_HX] += vx * dt;
      p[F_HY] += vy * dt;
      // how far the whisk turned round the bowl's middle this frame
      let dA = Math.atan2(p[F_HY], p[F_HX]) - Math.atan2(oy, ox);
      if (dA > Math.PI) dA -= Math.PI * 2;
      if (dA < -Math.PI) dA += Math.PI * 2;
      const reach = Math.min(1, Math.hypot(p[F_HX], p[F_HY]) / 0.25);
      const work = Math.abs(dA) * reach;
      if (!reduced) p[F_FOAM] = Math.min(1, p[F_FOAM] + work * WORK);
      p[F_SWIRL] += dA * 0.62 * reach;
      const omega = dt > 0 ? work / dt : 0;
      p[F_AGIT] += (Math.min(1, omega / (2.3 * Math.PI * 2)) - p[F_AGIT]) * Math.min(1, dt * 6);
      // peaks stand while whisking; once the whisk stops they relax a little
      const want = Math.max(0, (p[F_FOAM] - 0.45) / 0.55);
      p[F_PEAK] += ((m.touching.get() === 1 ? want : want * 0.6) - p[F_PEAK]) * Math.min(1, dt * 1.5);
      p[F_LIFT] *= Math.exp(-dt * 6);
      p[F_LPEAK] *= Math.exp(-dt * 0.5);
      m.hand.set(p);
      m.vel.set([vx, vy]);
      if (m.touching.get() === 0 && now - m.lastTouch.get() > IDLE) {
        m.mode.set(MODE_RETURN);
        m.returnStart.set(now);
        m.returnFrom.set(p);
      }
    } else if (mode === MODE_RETURN && now - m.returnStart.get() >= RETURN) {
      m.mode.set(MODE_AUTO);
      m.autoStart.set(now);
    }
  });

  const pose = useDerivedValue(() => {
    const now = m.now.get();
    const mode = m.mode.get();
    const rest = reduced ? stiffPose() : restPose();
    if (mode === MODE_HAND) return m.hand.get();
    if (mode === MODE_RETURN) return mixPose(m.returnFrom.get(), rest, easeInOut(Math.min(1, (now - m.returnStart.get()) / RETURN)));
    if (!auto) return rest;
    return poseAt((now - m.autoStart.get()) % LOOP);
  });

  const uniforms = useDerivedValue(() => {
    const p = pose.get();
    const now = m.now.get();
    const t = (now - m.autoStart.get()) % LOOP;
    const phase = m.mode.get() === MODE_AUTO ? (t / LOOP) * Math.PI * 2 * 9 : (now % 100) * Math.PI * 2;
    const geo = g ?? makeGeo(1, 1, 0, 1);
    return {
      size: [geo.w, geo.h],
      bowl: [geo.cx, geo.cy, geo.R, geo.unit],
      foam: [p[F_FOAM], p[F_PEAK], p[F_SWIRL], p[F_AGIT]],
      whisk: [p[F_HX], p[F_HY], p[F_LIFT], phase],
      lpeak: [p[F_LX], p[F_LY], p[F_LPEAK], 0],
    };
  });

  const whisk = useDerivedValue(() => {
    const p = pose.get();
    if (!g) return [Skia.PathBuilder.Make().build(), Skia.PathBuilder.Make().build()];
    return whiskPaths(g, p[F_HX], p[F_HY], p[F_LIFT], 0, 0);
  });
  const shadow = useDerivedValue(() => {
    const p = pose.get();
    if (!g) return [Skia.PathBuilder.Make().build(), Skia.PathBuilder.Make().build()];
    const l = p[F_LIFT];
    return whiskPaths(g, p[F_HX], p[F_HY], l, (6 + 22 * l) * g.unit, (9 + 30 * l) * g.unit);
  });
  const wires = useDerivedValue(() => whisk.get()[0]);
  const handle = useDerivedValue(() => whisk.get()[1]);
  const wiresShadow = useDerivedValue(() => shadow.get()[0]);
  const handleShadow = useDerivedValue(() => shadow.get()[1]);
  const shadowAlpha = useDerivedValue(() => 0.2 - 0.08 * pose.get()[F_LIFT]);

  const foam = useDerivedValue(() => pose.get()[F_FOAM]);
  const percent = useDerivedValue(() => pose.get()[F_FOAM] * 100);
  const stage = useDerivedValue<number>(() => stageOf(pose.get()[F_FOAM]));

  useAnimatedReaction(
    () => (m.mode.get() === MODE_HAND ? stage.get() : -1),
    (s, prev) => {
      if (s >= 0 && prev !== null && prev >= 0 && s !== prev) scheduleOnRN(tickHaptic);
    },
  );

  function grab(x: number, y: number) {
    'worklet';
    if (!g) return;
    if (m.mode.get() !== MODE_HAND) {
      m.hand.set(pose.get().slice());
      m.vel.set([0, 0]);
    }
    m.mode.set(MODE_HAND);
    m.touching.set(1);
    m.lastTouch.set(m.now.get());
    m.target.set(fingerInBowl(g, x, y));
  }

  function follow(x: number, y: number) {
    'worklet';
    if (!g) return;
    m.target.set(fingerInBowl(g, x, y));
    m.lastTouch.set(m.now.get());
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
      scheduleOnRN(softHaptic);
    })
    .onUpdate((e) => {
      follow(e.x, e.y);
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
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="A bowl of egg whites. Draw circles in the bowl to whisk them into stiff peaks.">
          {g && effect ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <Shader source={effect} uniforms={uniforms} />
              </Fill>
              <Path path={handleShadow} style="stroke" strokeWidth={13 * g.unit} strokeCap="round" color="#3A2C1E" opacity={shadowAlpha}>
                <BlurMask blur={5} style="normal" />
              </Path>
              <Path path={wiresShadow} style="stroke" strokeWidth={2.6} color="#3A2C1E" opacity={shadowAlpha}>
                <BlurMask blur={2.5} style="normal" />
              </Path>
              <Path path={handle} style="stroke" strokeWidth={13 * g.unit} strokeCap="round" color="#646B71" />
              <Path path={handle} style="stroke" strokeWidth={4 * g.unit} strokeCap="round" color="#D9DEE1" />
              <Path path={wires} style="stroke" strokeWidth={2.6} strokeJoin="round" color="#6A7177" />
              <Path path={wires} style="stroke" strokeWidth={1} strokeJoin="round" color="#F1F4F5" />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 16 }]}>
        <Text style={styles.kicker}>WHISK · STEP 3 OF 6</Text>
        <Text style={styles.title}>Stiff peaks</Text>
        <Text style={styles.sub}>Beat in quick circles until the whites hold a peak that stands up straight.</Text>
        <View style={styles.chips}>
          <Text style={styles.chip}>4 egg whites</Text>
          <Text style={styles.chip}>Pinch of salt</Text>
          <Text style={styles.chip}>About 4 min</Text>
        </View>
      </View>

      <View pointerEvents="none" style={[styles.card, { bottom: insets.bottom + 16, height: cardH }]}>
        <View style={{ width: RING, height: RING }}>
          <Canvas style={{ width: RING, height: RING }}>
            <Path path={ringPath} style="stroke" strokeWidth={6} color="rgba(42,37,32,0.09)" />
            <Path path={ringPath} style="stroke" strokeWidth={6} strokeCap="round" end={foam}>
              <SweepGradient c={vec(RING / 2, RING / 2)} colors={['#F4D27A', '#E9A93F', '#D98E2B', '#F4D27A']} />
            </Path>
          </Canvas>
          <View style={styles.ringLabel}>
            <RollNumber value={percent} digits={3} line={18} style={styles.ringValue} suffix="%" suffixStyle={styles.ringUnit} align="center" />
          </View>
        </View>
        <View style={styles.cardText}>
          <Text style={styles.cardKicker}>SOFT · FIRM · STIFF</Text>
          <RollLabel labels={STAGES} index={stage} line={24} style={styles.stage} />
          <Text style={styles.caption} numberOfLines={1}>
            Tip the bowl: stiff whites stay put.
          </Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#E4DED5', overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24 },
  kicker: { color: ACCENT, fontSize: 11, fontWeight: '700', letterSpacing: 2 },
  title: { color: INK, fontSize: 34, fontWeight: '800', letterSpacing: -0.6, marginTop: 8 },
  sub: { color: MUTE, fontSize: 15, lineHeight: 21, marginTop: 6, maxWidth: 320 },
  chips: { flexDirection: 'row', gap: 8, marginTop: 14 },
  chip: {
    color: INK,
    fontSize: 12,
    fontWeight: '600',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 14,
    overflow: 'hidden',
    backgroundColor: 'rgba(255,252,246,0.7)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(42,37,32,0.14)',
  },
  card: {
    position: 'absolute',
    left: 16,
    right: 16,
    borderRadius: 24,
    borderCurve: 'continuous',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
    paddingHorizontal: 16,
    backgroundColor: CARD,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(42,37,32,0.1)',
    boxShadow: '0 1px 3px rgba(60,40,20,0.12), 0 14px 32px rgba(60,40,20,0.14), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  ringLabel: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, alignItems: 'center', justifyContent: 'center' },
  ringValue: { color: INK, fontSize: 15, fontWeight: '700', fontVariant: ['tabular-nums'] },
  ringUnit: { color: MUTE, fontSize: 11, fontWeight: '700' },
  cardText: { flex: 1, gap: 2 },
  cardKicker: { color: MUTE, fontSize: 11, fontWeight: '700', letterSpacing: 1.4 },
  stage: { color: INK, fontSize: 19, fontWeight: '700' },
  caption: { color: MUTE, fontSize: 13 },
});
