import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { type Layout, bakeScene, drawFrame, makeLayout } from './slinkyArt';
import { LIFT_FOR, LIFT_FROM, LOOP, N, type Pose, STEP_FOR, WALK_FROM, easeInOutCubic, liftRings, loopPose, stretchRings } from './slinkyMotion';

/*
 Slinky Stairs — "Desk Toys", a toy-physics playground: a steel slinky on a three-step maple
 staircase on a walnut desk, under a warm lamp.

 It walks down by itself: the top end tips over and lands on the next step, the coils pour through
 the arch, the last end flips over onto the new stack (slinkyMotion.ts has the timeline). Grab the
 top coil and pull: the slinky stretches from its stack after the finger, rubber-banding past
 300 pt; let go and it collapses back on an underdamped spring (16 rad/s, damping 0.32), squashing
 below its height and ringing. A touch while it walks lets the current step finish at 4x first.
 2 s after the last touch it is lifted back to the top step (0.9 s) and the loop starts again.
 Reduced motion: no loop; the slinky rests on the top step, and a drag still stretches it.
*/

type Props = {
  /** Plays the 9 s walk-down-and-lift loop until touched. */
  autoPlay?: boolean;
};

const AUTO = 0;
const TOUCH = 1;
const BACK = 2;
const BACK_FOR = 0.9;
const MAX_PULL = 300;
const ROLL = 26;
const LABELS = ['Ready', 'Step 1 of 3', 'Step 2 of 3', 'Step 3 of 3', 'Landed', 'Ready'];

const INK = '#F1E9DE';
const MUTE = 'rgba(241,233,222,0.62)';
const FAINT = 'rgba(241,233,222,0.42)';
const AMBER = '#F0BE7E';

type Sim = {
  k: number;
  px: number;
  py: number;
  vx: number;
  vy: number;
  grab: number;
  offX: number;
  offY: number;
  fingerX: number;
  fingerY: number;
  lastTouch: number;
  ff: number;
  ffTo: number;
  backAt: number;
  snapped: number;
  tick: number;
};

function makeSim(): Sim {
  'worklet';
  return { k: 0, px: 0, py: 0, vx: 0, vy: 0, grab: 0, offX: 0, offY: 0, fingerX: 0, fingerY: 0, lastTouch: 0, ff: -1, ffTo: 0, backAt: -1, snapped: 1, tick: 0 };
}

/** Where the top coil rests on step k. */
function restTop(g: Layout, k: number, out: number[]) {
  'worklet';
  out[0] = g.baseX[k];
  out[1] = g.baseY[k] - (N - 1) * g.pitch;
}

function feel(kind: 'grab' | 'snap' | 'tick') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function RollText({ pose, i, reduced }: { pose: SharedValue<Pose | null>; i: number; reduced: boolean }) {
  const roll = reduced ? 0 : ROLL;
  const style = useAnimatedStyle(() => {
    const d = i - (pose.get()?.label ?? 0);
    return { opacity: Math.max(0, 1 - Math.abs(d)), transform: [{ translateY: d * roll }] };
  });
  return (
    <Animated.Text style={[styles.step, style]} numberOfLines={1}>
      {LABELS[i]}
    </Animated.Text>
  );
}

export function SlinkyStairs({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);

  const g = useMemo<Layout | null>(() => (size ? makeLayout(size.width, size.height, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const scene = useMemo(() => (g ? bakeScene(g, insets.top, Math.min(2, PixelRatio.get())) : null), [g, insets.top]);

  const now = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  const mode = useSharedValue(AUTO);
  const sim = useSharedValue<Sim>(makeSim());
  const ignoring = useSharedValue(0);

  useFrameCallback((frame) => {
    const at = frame.timestamp / 1000;
    if (loopStart.get() < 0) loopStart.set(at);
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    const m = mode.get();
    if (m !== AUTO && g) {
      const s = sim.get();
      const rest = [0, 0];
      restTop(g, s.k, rest);
      if (s.ff >= 0) {
        // The current step finishes at 4x before the finger takes the top coil.
        s.ff += dt * 4;
        if (s.ff >= s.ffTo) {
          s.ff = -1;
          s.px = rest[0];
          s.py = rest[1];
          s.vx = 0;
          s.vy = 0;
          s.offX = rest[0] - s.fingerX;
          s.offY = rest[1] - s.fingerY;
        }
      } else if (s.grab === 0) {
        // Underdamped spring back onto the stack, in small steps.
        const w = 16;
        const z = 0.32;
        const n = 4;
        const h = dt / n;
        const before = s.py - rest[1];
        for (let i = 0; i < n; i += 1) {
          s.vx += (-w * w * (s.px - rest[0]) - 2 * z * w * s.vx) * h;
          s.vy += (-w * w * (s.py - rest[1]) - 2 * z * w * s.vy) * h;
          s.px += s.vx * h;
          s.py += s.vy * h;
        }
        if (s.snapped === 0 && before < 0 && s.py - rest[1] >= 0) {
          s.snapped = 1;
          scheduleOnRN(feel, 'snap');
        }
      }
      if (m === TOUCH && s.grab === 0 && s.ff < 0 && at - s.lastTouch > 2) {
        if (s.k === 0) {
          sim.set(makeSim());
          mode.set(AUTO);
          loopStart.set(at);
        } else {
          s.backAt = at;
          mode.set(BACK);
        }
      } else if (m === BACK && at - s.backAt >= BACK_FOR) {
        sim.set(makeSim());
        mode.set(AUTO);
        loopStart.set(at);
      }
    }
    now.set(at);
  });

  /** Freezes the loop where it is (finishing a step at 4x if one is under way). */
  function takeOver(at: number) {
    'worklet';
    if (!g) return;
    const s = makeSim();
    s.lastTouch = at;
    const t = auto ? (((at - loopStart.get()) % LOOP) + LOOP) % LOOP : 0;
    if (t >= WALK_FROM && t < 6.4) {
      const step = Math.min(2, Math.floor((t - WALK_FROM) / STEP_FOR));
      s.ff = t;
      s.ffTo = WALK_FROM + (step + 1) * STEP_FOR;
      s.k = step + 1;
    } else if (t >= LIFT_FROM && t < LIFT_FROM + LIFT_FOR) {
      s.ff = t;
      s.ffTo = LIFT_FROM + LIFT_FOR;
      s.k = 0;
    } else {
      s.k = t >= 6.4 && t < LIFT_FROM ? 3 : 0;
    }
    const rest = [0, 0];
    restTop(g, s.k, rest);
    s.px = rest[0];
    s.py = rest[1];
    sim.set(s);
    mode.set(TOUCH);
  }

  const pan = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => {
      if (!g || mode.get() === BACK || e.y > g.panelY || e.y < insets.top + 96) {
        ignoring.set(1);
        return;
      }
      const at = now.get();
      if (mode.get() === AUTO) takeOver(at);
      const s = sim.get();
      const rest = [0, 0];
      restTop(g, s.k, rest);
      const nearX = Math.abs(e.x - g.baseX[s.k]) < g.R + 40;
      const nearY = e.y > Math.min(rest[1], s.py) - 60 && e.y < g.baseY[s.k] + 20;
      if (s.ff < 0 && !(nearX && nearY)) {
        ignoring.set(1);
        s.lastTouch = at;
        return;
      }
      ignoring.set(0);
      s.grab = 1;
      s.snapped = 0;
      s.tick = 0;
      s.lastTouch = at;
      s.fingerX = e.x;
      s.fingerY = e.y;
      s.offX = s.px - e.x;
      s.offY = s.py - e.y;
      scheduleOnRN(feel, 'grab');
    })
    .onUpdate((e) => {
      if (ignoring.get() === 1 || !g) return;
      const s = sim.get();
      s.lastTouch = now.get();
      s.fingerX = e.x;
      s.fingerY = e.y;
      if (s.ff >= 0) return;
      const bx = g.baseX[s.k];
      const by = g.baseY[s.k];
      let dx = e.x + s.offX - bx;
      let dy = e.y + s.offY - by;
      // It cannot be pushed into the step, nor squashed below half its height.
      dy = Math.min(dy, -(N - 1) * g.pitch * 0.5);
      const L = Math.hypot(dx, dy);
      if (L > MAX_PULL) {
        const over = L - MAX_PULL;
        const L2 = MAX_PULL + over / (1 + over / 100);
        dx *= L2 / L;
        dy *= L2 / L;
      }
      s.px = bx + dx;
      s.py = by + dy;
      s.vx = e.velocityX;
      s.vy = e.velocityY;
      const step = Math.floor(Math.hypot(dx, dy) / 60);
      if (step !== s.tick) {
        s.tick = step;
        scheduleOnRN(feel, 'tick');
      }
    })
    .onFinalize(() => {
      if (ignoring.get() === 1) {
        ignoring.set(0);
        return;
      }
      const s = sim.get();
      s.grab = 0;
      s.lastTouch = now.get();
      s.vx = Math.max(-1500, Math.min(1500, s.vx));
      s.vy = Math.max(-1500, Math.min(1500, s.vy));
    });

  const pose = useDerivedValue<Pose | null>(() => {
    if (!g) return null;
    const at = now.get();
    const m = mode.get();
    if (m === AUTO) {
      const t = auto ? (((at - loopStart.get()) % LOOP) + LOOP) % LOOP : 0;
      return loopPose(t, g);
    }
    const s = sim.get();
    if (s.ff >= 0) return loopPose(s.ff, g);
    const rings: number[] = [];
    if (m === BACK) {
      const v = Math.min(1, (at - s.backAt) / BACK_FOR);
      liftRings(g, s.k, v, rings);
      const e = easeInOutCubic(v);
      return { rings, shadows: [g.baseX[s.k], g.baseY[s.k], g.R, 1 - Math.min(1, v * 4), g.baseX[0], g.baseY[0], g.R, Math.max(0, (v - 0.7) / 0.3)], label: 4 + e };
    }
    stretchRings(g, s.k, s.px, s.py, rings);
    return { rings, shadows: [g.baseX[s.k], g.baseY[s.k], g.R, 1], label: s.k === 3 ? 4 : s.k };
  });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size?.width ?? 1, size?.height ?? 1));
    const p = pose.get();
    const s = sim.get();
    const touch = [s.fingerX, s.fingerY, mode.get() === TOUCH && s.grab === 1 ? 1 : 0];
    if (g && scene && p) drawFrame(canvas, g, scene, p, touch);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {g && scene ? (
        <GestureDetector gesture={pan}>
          <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="A steel slinky walking down three wooden steps. Drag its top coil to stretch it.">
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          </View>
        </GestureDetector>
      ) : null}

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 14 }]}>
        <Text style={styles.kicker}>DESK TOYS</Text>
        <Text style={styles.title}>Slinky</Text>
        <Text style={styles.subtitle}>A walk down three maple steps</Text>
      </View>

      {g ? (
        <View pointerEvents="none" style={[styles.panel, { top: g.panelY, height: g.panelH }]}>
          <View style={styles.panelTop}>
            <View style={styles.rollBox}>
              {LABELS.map((_, i) => (
                <RollText key={i} pose={pose} i={i} reduced={reduced} />
              ))}
            </View>
            <View style={styles.chip}>
              <Text style={styles.chipText}>Spring steel</Text>
            </View>
          </View>
          <Text style={styles.hint}>Drag the top coil to stretch it, then let go</Text>
          <View style={styles.stats}>
            {[
              ['40', 'coils'],
              ['1.7 s', 'per step'],
              ['92 g', 'weight'],
            ].map(([value, label]) => (
              <Text key={label} style={styles.stat}>
                <Text style={styles.statValue}>{value}</Text> {label}
              </Text>
            ))}
          </View>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#212B2A', overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24, gap: 4 },
  kicker: { color: AMBER, fontSize: 12, fontWeight: '700', letterSpacing: 3 },
  title: { color: INK, fontSize: 34, fontWeight: '700', letterSpacing: -0.6 },
  subtitle: { color: MUTE, fontSize: 15, fontWeight: '500' },
  panel: { position: 'absolute', left: 16, right: 16, paddingHorizontal: 20, paddingTop: 16, gap: 6 },
  panelTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  rollBox: { height: 30, flex: 1, overflow: 'hidden' },
  step: { position: 'absolute', left: 0, right: 0, top: 1, color: INK, fontSize: 22, fontWeight: '700', fontVariant: ['tabular-nums'] },
  chip: { paddingHorizontal: 12, height: 26, borderRadius: 13, justifyContent: 'center', backgroundColor: 'rgba(240,190,126,0.14)' },
  chipText: { color: AMBER, fontSize: 12, fontWeight: '600' },
  hint: { color: MUTE, fontSize: 14, fontWeight: '500' },
  stats: { flexDirection: 'row', gap: 18, marginTop: 8 },
  stat: { color: FAINT, fontSize: 13, fontWeight: '500' },
  statValue: { color: INK, fontWeight: '600', fontVariant: ['tabular-nums'] },
});
