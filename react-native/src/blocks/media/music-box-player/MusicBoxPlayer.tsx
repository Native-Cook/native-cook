import { Canvas, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { type Layout, bakeScene, drawFrame, makeLayout, makeNote } from './musicBoxArt';
import { CRANK_RATIO, LOOP, PINS, PIN_COUNT, PLUCKS, type Pose, TEETH, loopPose, passed, poseFrom, turnsAt } from './musicBoxMotion';

/*
 Music Box Player — "Lullaby", a sleep-sounds app playing "Willow Waltz" on an open music box: a
 brass cylinder studded with pins, a steel comb of 18 teeth, a crank on the front.

 Turn the crank (a circular drag around its hub; it only turns forward, like a ratchet) or roll the
 cylinder down with a finger: the cylinder turns a quarter of the crank's angle, every pin that
 passes the comb bends its tooth back and lets it ring (decaying in 0.42 s, a warm glow at its tip),
 a note floats up, the governor's fan spins and the playhead follows. Let go and the cylinder runs
 on and slows (friction 1.6 /s). 2 s after the last touch (once it has stopped) it eases to the
 song's start (0.9 s) and the loop plays again (musicBoxMotion.ts has the timeline).
 Reduced motion: no loop; the crank still plays it.
*/

type Props = {
  /** Plays the 8 s crank-the-song loop until touched. */
  autoPlay?: boolean;
};

const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });
const AUTO = 0;
const TOUCH = 1;
const BACK = 2;
const BACK_FOR = 0.9;
const NOTE_LIFE = 1.7;
const INK = '#F1ECFF';
const MUTE = 'rgba(241,236,255,0.62)';
const FAINT = 'rgba(241,236,255,0.42)';
const GOLD = '#F3C987';

type Sim = {
  a: number;
  omega: number;
  grab: number;
  how: number;
  lastAng: number;
  lastY: number;
  lastMove: number;
  lastTouch: number;
  last: number[];
  recent: number[];
  backAt: number;
  backFrom: number;
  backTo: number;
  tick: number;
};

function makeSim(): Sim {
  'worklet';
  const last: number[] = [];
  for (let j = 0; j < TEETH; j += 1) last.push(-1);
  return { a: 0, omega: 0, grab: 0, how: 0, lastAng: 0, lastY: 0, lastMove: 0, lastTouch: 0, last, recent: [], backAt: -1, backFrom: 0, backTo: 0, tick: 0 };
}

function wrapPi(x: number) {
  'worklet';
  const k = Math.PI * 2;
  let y = ((x % k) + k) % k;
  if (y > Math.PI) y -= k;
  return y;
}

/** Turns the cylinder to angle a, plucking every pin that passes the comb on the way. */
function advance(s: Sim, a: number, at: number) {
  'worklet';
  if (a <= s.a) return false;
  const hit: number[] = [];
  passed(s.a, a, hit);
  for (let i = 0; i < hit.length; i += 1) {
    const j = PINS[hit[i] * 2 + 1];
    s.last[j] = at;
    s.recent.push(j, at);
  }
  while (s.recent.length > 0 && at - s.recent[1] > NOTE_LIFE) s.recent.splice(0, 2);
  if (s.recent.length > 40) s.recent.splice(0, s.recent.length - 40);
  s.a = a;
  return hit.length > 0;
}

function byTime(x: number[], y: number[]) {
  'worklet';
  return x[1] - y[1];
}

function feel(kind: 'grab' | 'pluck') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'pluck') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function MusicBoxPlayer({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);

  const time = useMemo(() => matchFont({ fontFamily: MONO, fontSize: 12, fontWeight: '600' }), []);
  const g = useMemo<Layout | null>(() => (size ? makeLayout(size.width, size.height, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const art = useMemo(() => {
    if (!g) return null;
    const scene = bakeScene(g, time, Math.min(2, PixelRatio.get()));
    return scene ? { scene, note: makeNote() } : null;
  }, [g, time]);

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
    if (m === TOUCH) {
      const s = sim.get();
      if (s.grab === 0) {
        s.omega *= Math.exp(-dt * 1.6);
        if (s.omega < 0.05) s.omega = 0;
        if (advance(s, s.a + s.omega * dt, at) && at - s.tick > 0.09) {
          s.tick = at;
          scheduleOnRN(feel, 'pluck');
        }
      } else if (at - s.lastMove > 0.08) {
        s.omega *= Math.exp(-dt * 12);
      }
      if (s.grab === 0 && s.omega === 0 && at - s.lastTouch > 2) {
        s.backAt = at;
        s.backFrom = s.a;
        s.backTo = Math.round(s.a / (Math.PI * 2)) * Math.PI * 2;
        mode.set(BACK);
      }
    } else if (m === BACK) {
      const s = sim.get();
      const newest = s.recent.length > 0 ? s.recent[s.recent.length - 1] : -10;
      if (at - s.backAt >= BACK_FOR && at - newest > NOTE_LIFE) {
        sim.set(makeSim());
        mode.set(AUTO);
        loopStart.set(at);
      }
    }
    now.set(at);
  });

  /** Takes the cylinder from wherever the loop has it, with its ringing teeth and notes. */
  function takeOver(at: number) {
    'worklet';
    const s = makeSim();
    s.lastTouch = at;
    if (auto) {
      const t = (((at - loopStart.get()) % LOOP) + LOOP) % LOOP;
      const base = at - t;
      s.a = turnsAt(t) * Math.PI * 2;
      for (let p = 0; p < PIN_COUNT; p += 1) {
        const tp = PLUCKS[p];
        if (tp > t) continue;
        const j = PINS[p * 2 + 1];
        if (base + tp > s.last[j]) s.last[j] = base + tp;
        if (t - tp < NOTE_LIFE) s.recent.push(j, base + tp);
      }
      // Keep the recent plucks in time order.
      const pairs: number[][] = [];
      for (let i = 0; i < s.recent.length; i += 2) pairs.push([s.recent[i], s.recent[i + 1]]);
      pairs.sort(byTime);
      s.recent = [];
      for (let i = 0; i < pairs.length; i += 1) s.recent.push(pairs[i][0], pairs[i][1]);
    }
    sim.set(s);
    mode.set(TOUCH);
  }

  const pan = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => {
      if (!g || mode.get() === BACK) {
        ignoring.set(1);
        return;
      }
      const onCrank = Math.hypot(e.x - g.hubX, e.y - g.hubY) < g.arm + 64;
      const onCylinder = e.x > g.cylX0 - 10 && e.x < g.cylX1 + 10 && e.y > g.cylY - g.cylR - 30 && e.y < g.combY + 20;
      if (!onCrank && !onCylinder) {
        ignoring.set(1);
        return;
      }
      ignoring.set(0);
      const at = now.get();
      if (mode.get() === AUTO) takeOver(at);
      const s = sim.get();
      s.grab = 1;
      s.how = onCrank ? 0 : 1;
      s.lastAng = Math.atan2(e.y - g.hubY, e.x - g.hubX);
      s.lastY = e.y;
      s.lastMove = at;
      s.lastTouch = at;
      scheduleOnRN(feel, 'grab');
    })
    .onUpdate((e) => {
      if (ignoring.get() === 1 || !g) return;
      const s = sim.get();
      const at = now.get();
      let d = 0;
      if (s.how === 0) {
        const ang = Math.atan2(e.y - g.hubY, e.x - g.hubX);
        d = wrapPi(ang - s.lastAng) / CRANK_RATIO;
        s.lastAng = ang;
      } else {
        d = (e.y - s.lastY) / (g.cylR * 1.6);
        s.lastY = e.y;
      }
      // A ratchet: it only turns forward.
      if (d > 0) {
        const dt = Math.max(1 / 240, at - s.lastMove);
        s.omega = 0.6 * s.omega + 0.4 * Math.min(14, d / dt);
        s.lastMove = at;
        if (advance(s, s.a + d, at) && at - s.tick > 0.09) {
          s.tick = at;
          scheduleOnRN(feel, 'pluck');
        }
      }
      s.lastTouch = at;
    })
    .onFinalize(() => {
      if (ignoring.get() === 1) {
        ignoring.set(0);
        return;
      }
      const s = sim.get();
      const at = now.get();
      s.grab = 0;
      s.lastTouch = at;
      if (at - s.lastMove > 0.1) s.omega = 0;
    });

  const pose = useDerivedValue<Pose | null>(() => {
    if (!g) return null;
    const at = now.get();
    const m = mode.get();
    if (m === AUTO) return loopPose(g, auto ? (((at - loopStart.get()) % LOOP) + LOOP) % LOOP : 0);
    const s = sim.get();
    const turn = Math.PI * 2;
    if (m === BACK) {
      const k = Math.min(1, (at - s.backAt) / BACK_FOR);
      const e = k * k * (3 - 2 * k);
      const a = s.backFrom + (s.backTo - s.backFrom) * e;
      const from = (((s.backFrom % turn) + turn) % turn) / turn;
      return poseFrom(g, a, a * CRANK_RATIO, at, s.last, s.recent, from * (1 - e));
    }
    return poseFrom(g, s.a, s.a * CRANK_RATIO, at, s.last, s.recent, (((s.a % turn) + turn) % turn) / turn);
  });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size?.width ?? 1, size?.height ?? 1));
    const p = pose.get();
    if (g && art && p) drawFrame(canvas, g, art.scene, art.note, time, p);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {g && art ? (
        <GestureDetector gesture={pan}>
          <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Willow Waltz on a music box. Turn the crank, or roll the cylinder down, to play it.">
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          </View>
        </GestureDetector>
      ) : null}

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 14 }]}>
        <Text style={styles.kicker}>LULLABY</Text>
        <Text style={styles.title}>Willow Waltz</Text>
        <Text style={styles.subtitle}>Music box · 3/4 · Sleep sounds</Text>
      </View>

      {g ? (
        <View pointerEvents="none" style={[styles.panel, { top: g.panelY, height: g.panelH }]}>
          <View style={styles.panelTop}>
            <View style={styles.flex}>
              <Text style={styles.song} numberOfLines={1}>
                Willow Waltz
              </Text>
              <Text style={styles.by} numberOfLines={1}>
                Brass comb, 18 notes · Ida Morrow
              </Text>
            </View>
            <Text style={styles.hint}>Turn the crank</Text>
          </View>
          <View style={styles.chips}>
            {['Timer 30 min', 'Fade out', 'Night light'].map((label, i) => (
              <View key={label} style={[styles.chip, i === 0 ? styles.chipOn : null]}>
                <Text style={[styles.chipText, i === 0 ? styles.chipTextOn : null]} numberOfLines={1}>
                  {label}
                </Text>
              </View>
            ))}
          </View>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#161328', overflow: 'hidden' },
  flex: { flex: 1 },
  header: { position: 'absolute', left: 24, right: 24, gap: 4 },
  kicker: { color: GOLD, fontSize: 12, fontWeight: '700', letterSpacing: 3 },
  title: { color: INK, fontSize: 34, fontWeight: '700', letterSpacing: -0.6 },
  subtitle: { color: MUTE, fontSize: 15, fontWeight: '500' },
  panel: { position: 'absolute', left: 16, right: 16, paddingHorizontal: 20, paddingTop: 14 },
  panelTop: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  song: { color: INK, fontSize: 17, fontWeight: '700' },
  by: { color: FAINT, fontSize: 13, fontWeight: '500', marginTop: 1 },
  hint: { color: GOLD, fontSize: 13, fontWeight: '600' },
  chips: { position: 'absolute', left: 20, right: 20, bottom: 22, flexDirection: 'row', gap: 8 },
  chip: { height: 32, paddingHorizontal: 12, borderRadius: 16, justifyContent: 'center', backgroundColor: 'rgba(241,236,255,0.08)' },
  chipOn: { backgroundColor: 'rgba(243,201,135,0.16)' },
  chipText: { color: MUTE, fontSize: 12, fontWeight: '600' },
  chipTextOn: { color: GOLD },
});
