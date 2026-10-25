import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
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

import { drawCradle, type Frame, makeGeo, recordBack, recordBall, recordFront } from './newtonCradleArt';
import {
  BALLS,
  LOOP,
  PULL,
  ballAngles,
  easeInOut,
  loopEnergy,
  loopFinger,
  loopHits,
  loopPsi,
  rubberBand,
  seg,
  simAmplitude,
  stepSim,
} from './newtonCradleMotion';

/*
 Newton Cradle Swing — "Desk Toys · Momentum": a chrome Newton's cradle on a walnut desk under a
 lamp. Pull a ball (or two, three…) out and let go: the swing passes through the still balls,
 one collision every half period, a light click each time, 3 % of the swing lost per hit.

 One Skia canvas; the room, the frame and one ball are pictures recorded once, each frame draws
 five balls, ten strings and five contact shadows (newtonCradleArt.ts). The motion is a folded
 pendulum (newtonCradleMotion.ts): analytic in autoPlay (LOOP = 8 s), integrated under the finger.
 React renders only when the status line's words change (a few times a second at most).

 Modes: 0 loop · 1 finger on a ball · 2 free swing · 3 easing back to rest before the loop
 restarts (2 s after the last touch, 0.9 s ease-in-out) · 4 reduced motion's gentle return.
 */

type Props = {
  /** Plays the 8 s demo: pull ball 1, release, five seconds of swinging, settle. */
  autoPlay?: boolean;
};

const BACKGROUND = '#120e0b';
const INK = '#f3ebe1';
const INK_SOFT = '#ab9b8b';
const RETURN_AFTER = 2;
const CARD_H = 104;
const RETURN_FOR = 0.9;
const STATUS_WORDS = ['Ready', 'Pulling back', 'Swinging'];

function click() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function grabFeel() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.selectionAsync().catch(() => {});
}

/** The status line, in its own component so a new word re-renders only this text. */
function StatusLine({ code }: { code: SharedValue<number> }) {
  const [state, setState] = useState(0);
  useAnimatedReaction(
    () => code.get(),
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setState, v);
    },
  );
  const kind = Math.floor(state / 1000);
  const hits = state % 1000;
  const text = kind === 2 && hits > 0 ? `${hits} ${hits === 1 ? 'collision' : 'collisions'}` : STATUS_WORDS[kind];
  return (
    <Text style={styles.status} numberOfLines={1}>
      {text}
    </Text>
  );
}

export function NewtonCradleSwing({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const g = useMemo(() => (size.width > 0 ? makeGeo(size.width, size.height) : null), [size]);
  const art = useMemo<Frame | null>(() => (g ? { back: recordBack(g), front: recordFront(g), ball: recordBall(g.R) } : null), [g]);

  const now = useSharedValue(-1);
  const loopStart = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 2);
  /** [ψ, ψ', group size, collisions]. */
  const sim = useSharedValue<number[]>([0, 0, 1, 0]);
  /** The finger: [ball, last ψ, ψ', last time] or [] when no ball is held. */
  const grab = useSharedValue<number[]>([]);
  const lastTouch = useSharedValue(0);
  /** Mode 3 / 4: [start time, ψ at start]. */
  const back = useSharedValue<number[]>([0, 0]);
  const psi = useSharedValue(0);
  const group = useSharedValue(1);
  const finger = useSharedValue(0);
  const energy = useSharedValue(0);
  const status = useSharedValue(0);

  useFrameCallback((info) => {
    const t0 = info.timestamp / 1000;
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 1 / 30);
    if (now.get() < 0) loopStart.set(t0);
    now.set(t0);
    const m = mode.get();
    if (m === 0) {
      const t = (t0 - loopStart.get()) % LOOP;
      psi.set(loopPsi(t));
      group.set(1);
      finger.set(loopFinger(t));
      energy.set(loopEnergy(t));
      const kind = t < 1.2 || t > 7.05 ? 0 : t < 2 ? 1 : 2;
      status.set(kind * 1000 + loopHits(t));
      return;
    }
    finger.set(0);
    if (m === 1) {
      const s = sim.get();
      energy.set(Math.min(1, Math.abs(s[0]) / PULL));
      status.set(1000);
      return;
    }
    const s = sim.get().slice();
    if (m === 4) {
      const b = back.get();
      s[0] = b[1] * (1 - easeInOut(seg(t0 - b[0], 0, 0.5)));
      s[1] = 0;
    } else if (stepSim(s, dt) === 1 && !reduced) scheduleOnRN(click);
    sim.set(s);
    let shown = s[0];
    let amp = simAmplitude(s);
    if (m === 3) {
      const k = easeInOut(seg(t0 - back.get()[0], 0, RETURN_FOR));
      shown *= 1 - k;
      amp *= 1 - k;
      if (k >= 1) {
        mode.set(0);
        loopStart.set(t0);
        shown = 0;
      }
    } else if (m === 2 && auto && t0 - lastTouch.get() > RETURN_AFTER) {
      mode.set(3);
      back.set([t0, s[0]]);
    }
    psi.set(shown);
    group.set(s[2]);
    energy.set(Math.min(1, amp / PULL));
    status.set(amp < 0.004 ? 0 : 2000 + Math.min(999, s[3]));
  });

  const angles = useDerivedValue(() => ballAngles(psi.get(), group.get(), [0, 0, 0, 0, 0]));

  const picture = useDerivedValue(() => {
    const rec = Skia.PictureRecorder();
    const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    if (g && art) drawCradle(canvas, g, art, angles.get(), finger.get());
    return rec.finishRecordingAsPicture();
  });

  const gesture = useMemo(() => {
    if (!g) return Gesture.Pan();
    /** The finger's angle around ball i's pivot, folded onto the side it pulls toward. */
    function pull(i: number, x: number, y: number) {
      'worklet';
      const theta = Math.atan2(x - g!.xs[i], y - g!.pivotY);
      const left = x < g!.xs[i];
      // Pulling left takes ball i and every ball left of it; pulling right, i and those right of it.
      return [left ? Math.min(0, rubberBand(theta)) : Math.max(0, rubberBand(theta)), left ? i + 1 : BALLS - i];
    }
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        const a = angles.get();
        let best = -1;
        let bestD = g.R * 2.4;
        for (let i = 0; i < BALLS; i++) {
          const d = Math.hypot(e.x - (g.xs[i] + g.L * Math.sin(a[i])), e.y - (g.pivotY + g.L * Math.cos(a[i])));
          if (d < bestD) {
            bestD = d;
            best = i;
          }
        }
        if (best < 0) return;
        const p = pull(best, e.x, e.y);
        grab.set([best, p[0], 0, now.get()]);
        sim.set([p[0], 0, p[1], 0]);
        mode.set(1);
        scheduleOnRN(grabFeel);
      })
      .onUpdate((e) => {
        const h = grab.get();
        if (h.length !== 4) return;
        const p = pull(h[0], e.x, e.y);
        const t = now.get();
        const dt = Math.max(1 / 120, t - h[3]);
        const v = 0.65 * h[2] + (0.35 * (p[0] - h[1])) / dt;
        grab.set([h[0], p[0], v, t]);
        sim.set([p[0], v, p[1], 0]);
      })
      .onFinalize(() => {
        const h = grab.get();
        if (h.length !== 4) return;
        grab.set([]);
        const s = sim.get();
        lastTouch.set(now.get());
        if (reduced) {
          back.set([now.get(), s[0]]);
          mode.set(4);
        } else {
          sim.set([s[0], Math.max(-6, Math.min(6, h[2])), s[2], 0]);
          mode.set(2);
        }
      });
    // Shared values are stable; the gesture depends only on the geometry and these flags.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [g, reduced]);

  const meter = useAnimatedStyle(() => ({ transform: [{ scaleX: Math.max(0.001, energy.get()) }] }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {g && art ? (
        <>
          <GestureDetector gesture={gesture}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityRole="adjustable" accessibilityLabel="Newton's cradle. Pull a sphere sideways and let go.">
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </View>
          </GestureDetector>

          <View pointerEvents="none" style={[styles.header, { top: insets.top + 18 }]}>
            <Text style={styles.kicker}>DESK TOYS</Text>
            <Text style={styles.title}>Momentum</Text>
            <Text style={styles.subtitle}>Five chrome spheres on a steel frame</Text>
          </View>

          <View pointerEvents="none" style={[styles.card, { top: Math.min(g.baseBottom + 28, size.height - Math.max(insets.bottom, 12) - 40 - CARD_H) }]}>
            <View style={styles.cardRow}>
              <View style={styles.cardCol}>
                <Text style={styles.label}>SESSION</Text>
                <StatusLine code={status} />
              </View>
              <View style={[styles.cardCol, styles.meterCol]}>
                <Text style={styles.label}>ENERGY</Text>
                <View style={styles.track}>
                  <Animated.View style={[styles.fill, meter]} />
                </View>
              </View>
            </View>
            <Text style={styles.meta}>Period 1.02 s · 3 % lost at each collision</Text>
          </View>

          <Text pointerEvents="none" style={[styles.hint, { bottom: Math.max(insets.bottom, 12) + 14 }]}>
            Pull a sphere and let go · grab two for a double
          </Text>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: BACKGROUND, overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24, gap: 4 },
  kicker: { color: '#d2b48f', fontSize: 11, fontWeight: '700', letterSpacing: 2.4 },
  title: { color: INK, fontSize: 34, fontWeight: '700', letterSpacing: -0.6 },
  subtitle: { color: INK_SOFT, fontSize: 15, fontWeight: '500' },
  card: {
    position: 'absolute',
    left: 20,
    right: 20,
    paddingHorizontal: 20,
    paddingVertical: 16,
    gap: 12,
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(22,15,11,0.78)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,232,206,0.14)',
    boxShadow: '0 1px 3px rgba(0,0,0,0.35), 0 14px 36px rgba(0,0,0,0.32), inset 0 1px 0 rgba(255,240,222,0.08)',
  },
  cardRow: { flexDirection: 'row', gap: 16 },
  cardCol: { flex: 1, gap: 6 },
  meterCol: { justifyContent: 'flex-start' },
  label: { color: INK_SOFT, fontSize: 11, fontWeight: '600', letterSpacing: 1.4 },
  status: { color: INK, fontSize: 17, fontWeight: '600', fontVariant: ['tabular-nums'] },
  track: { height: 6, marginTop: 8, borderRadius: 3, backgroundColor: 'rgba(255,240,222,0.1)', overflow: 'hidden' },
  fill: { flex: 1, borderRadius: 3, backgroundColor: '#e7c49b', transformOrigin: 'left center' },
  meta: { color: INK_SOFT, fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  hint: { position: 'absolute', left: 24, right: 24, textAlign: 'center', color: 'rgba(243,235,225,0.5)', fontSize: 12, fontWeight: '500' },
});
