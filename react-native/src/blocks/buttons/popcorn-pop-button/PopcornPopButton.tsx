import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { type Layers, type Layout, bakeLayers, drawFrame, makeLayout } from './popcornArt';
import {
  AUTO_POP,
  BY_RANK,
  ENOUGH,
  LOOP,
  N,
  NEVER,
  type Pose,
  type Story,
  autoHeat,
  autoPress,
  autoStory,
  cookNeeded,
  makePose,
} from './popcornMotion';

/*
 Popcorn Pop Button — "Reel Night", a movie-night screen whose "Start the movie" button is a little
 copper pan of kernels under a glass lid.

 Hold the button: the pan heats (an orange glow under it, the thermometer arc around the button
 fills gold to red, the kernels jitter), then they pop one by one — each bursts into a puffy corn
 with a squash and a spin, flies under the lid and bounces into the heap — and the heap lifts the
 lid. Let go once 15 have popped and the label rolls to "Enjoy the show"; let go earlier and the
 kernels settle back as the pan cools. 2 s after the last touch the pan tips into the bowl on the
 movie card, fresh kernels drop in and the loop starts again (popcornMotion.ts has the timeline).
 Reduced motion: no loop and no popping; a press rolls the label (a fade) and the pan stays still.
*/

type Props = {
  /** Plays the 8 s heat-pop-serve loop until touched. */
  autoPlay?: boolean;
};

const AUTO = 0;
const TOUCH = 1;
const BACK = 2;
/** The bowl-and-refill under a finger is the loop's 1.6 s one, played in this long. */
const BACK_FOR = 0.9;
/** Distance the label travels when it rolls (pt). */
const ROLL = 26;

const INK = '#F7E6DA';
const MUTE = 'rgba(247,230,218,0.62)';
const FAINT = 'rgba(247,230,218,0.4)';
const GOLD = '#F4B85C';

type Sim = Story & { holding: number; cook: number; lastTouch: number; backAt: number; ignoring: number };

function emptySim(): Sim {
  'worklet';
  const popAt: number[] = [];
  const goneAt: number[] = [];
  for (let i = 0; i < N; i += 1) {
    popAt.push(NEVER);
    goneAt.push(NEVER);
  }
  return {
    now: 0,
    popAt,
    goneAt,
    emptyFor: BACK_FOR,
    lidAt: NEVER,
    lidBack: NEVER,
    labelAt: NEVER,
    labelOff: NEVER,
    heat: 0,
    press: 0,
    holding: 0,
    cook: 0,
    lastTouch: 0,
    backAt: 0,
    ignoring: 0,
  };
}

/** Corns in the pan right now (popped and not yet tipped into the bowl). */
function cornCount(s: Sim, at: number) {
  'worklet';
  let n = 0;
  for (let i = 0; i < N; i += 1) if (s.popAt[i] <= at && s.goneAt[i] > at) n += 1;
  return n;
}

function feel(kind: 'press' | 'pop' | 'ready') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'ready') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else if (kind === 'pop') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function RollingLine({ pose, a, b, style, reduced }: { pose: SharedValue<Pose | null>; a: string; b: string; style: object; reduced: boolean }) {
  const roll = reduced ? 0 : ROLL;
  const first = useAnimatedStyle(() => {
    const k = pose.get()?.label ?? 0;
    return { opacity: 1 - k, transform: [{ translateY: -roll * k }] };
  });
  const second = useAnimatedStyle(() => {
    const k = pose.get()?.label ?? 0;
    return { opacity: k, transform: [{ translateY: roll * (1 - k) }] };
  });
  return (
    <View style={styles.rollBox}>
      <Animated.Text style={[style, styles.rollText, first]} numberOfLines={1}>
        {a}
      </Animated.Text>
      <Animated.Text style={[style, styles.rollText, second]} numberOfLines={1}>
        {b}
      </Animated.Text>
    </View>
  );
}

export function PopcornPopButton({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);

  const g = useMemo<Layout | null>(() => (size ? makeLayout(size.width, size.height, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const layers = useMemo<Layers | null>(() => (g ? bakeLayers(g, insets.top, Math.min(3, PixelRatio.get())) : null), [g, insets.top]);

  const now = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  const mode = useSharedValue(AUTO);
  const sim = useSharedValue<Sim>(emptySim());
  /** Reduced motion: the label's state, faded with the frame clock. */
  const quiet = useSharedValue<number[]>([0, -10]);

  useFrameCallback((frame) => {
    const at = frame.timestamp / 1000;
    if (loopStart.get() < 0) loopStart.set(at);
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    const m = mode.get();
    if (m !== AUTO) {
      const s = sim.get();
      if (s.holding === 1) s.heat += (1 - s.heat) * (1 - Math.exp(-dt * 1.6));
      else s.heat = Math.max(0, s.heat - dt * (m === BACK ? 2.5 : 0.6));
      s.press += (s.holding - s.press) * (1 - Math.exp(-dt * 28));
      // Heat above the threshold cooks the kernels; they pop in rank order (left-over heat pops a few more).
      if (s.heat > 0.45 && m === TOUCH) s.cook += (s.heat - 0.45) * dt * 2.2;
      let popped = false;
      for (let r = 0; r < N; r += 1) {
        const i = BY_RANK[r];
        const ready = s.popAt[i] > at || s.goneAt[i] <= at - s.emptyFor;
        if (ready && m === TOUCH && s.cook >= cookNeeded(r)) {
          s.popAt[i] = at;
          s.goneAt[i] = NEVER;
          popped = true;
        }
      }
      if (popped) scheduleOnRN(feel, 'pop');
      const count = cornCount(s, at);
      const lifted = s.lidAt <= at && s.lidBack < s.lidAt;
      if (!lifted && count >= ENOUGH) s.lidAt = at;
      if (m === TOUCH && s.holding === 0 && at - s.lastTouch > 2) {
        // Into the bowl, fresh kernels in, lid down, label back.
        mode.set(BACK);
        s.backAt = at;
        s.emptyFor = BACK_FOR;
        for (let i = 0; i < N; i += 1) if (s.popAt[i] <= at && s.goneAt[i] > at) s.goneAt[i] = at;
        if (s.lidAt <= at && s.lidBack < s.lidAt) s.lidBack = at;
        if (s.labelAt <= at && s.labelOff < s.labelAt) s.labelOff = at;
      } else if (m === BACK && at > s.backAt + BACK_FOR + 0.05 && s.heat < 0.01) {
        sim.set(emptySim());
        mode.set(AUTO);
        loopStart.set(at);
      }
    }
    now.set(at);
  });

  /** Hands what the loop shows to the simulation, so a press never makes anything jump. */
  function takeOver(at: number) {
    'worklet';
    const s = emptySim();
    s.lastTouch = at;
    if (mode.get() === AUTO && auto) {
      const t = (((at - loopStart.get()) % LOOP) + LOOP) % LOOP;
      const base = at - t;
      let count = 0;
      for (let i = 0; i < N; i += 1) {
        if (AUTO_POP[i] <= t) {
          s.popAt[i] = base + AUTO_POP[i];
          count += 1;
        }
        if (t >= 6.4) s.goneAt[i] = base + 6.4;
      }
      s.emptyFor = t >= 6.4 ? 1.6 : BACK_FOR;
      if (t >= 4.6) s.lidAt = base + 4.6;
      if (t >= 6.4) s.lidBack = base + 6.4;
      if (t >= 5.0) s.labelAt = base + 5.0;
      if (t >= 6.4) s.labelOff = base + 6.4;
      s.heat = autoHeat(t);
      s.press = autoPress(t);
      s.cook = t < 6.4 && count > 0 ? cookNeeded(count - 1) + 0.001 : 0;
    }
    sim.set(s);
    mode.set(TOUCH);
  }

  const press = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .shouldCancelWhenOutside(false)
    .onBegin((e) => {
      if (!g) return;
      const inside = Math.hypot(e.x - g.bx, e.y - g.by) < g.D / 2 + 6;
      if (!inside || mode.get() === BACK) {
        sim.get().ignoring = 1;
        return;
      }
      if (reduced) {
        const q = quiet.get();
        quiet.set([1 - q[0], now.get()]);
        scheduleOnRN(feel, 'press');
        return;
      }
      takeOver(now.get());
      const s = sim.get();
      s.holding = 1;
      scheduleOnRN(feel, 'press');
    })
    .onFinalize(() => {
      const s = sim.get();
      if (s.ignoring === 1) {
        s.ignoring = 0;
        return;
      }
      if (reduced || mode.get() !== TOUCH) return;
      const at = now.get();
      s.holding = 0;
      s.lastTouch = at;
      const labelOn = s.labelAt <= at && s.labelOff < s.labelAt;
      if (!labelOn && cornCount(s, at) >= ENOUGH) {
        s.labelAt = at;
        scheduleOnRN(feel, 'ready');
      }
    });

  const pose = useDerivedValue<Pose | null>(() => {
    if (!g) return null;
    const at = now.get();
    if (reduced) {
      const q = quiet.get();
      const k = Math.min(1, Math.max(0, (at - q[1]) / 0.3));
      const p = makePose(autoStory(0), g);
      p.label = q[0] === 1 ? k : 1 - k;
      return p;
    }
    if (mode.get() === AUTO) {
      const t = auto ? (((at - loopStart.get()) % LOOP) + LOOP) % LOOP : 0;
      return makePose(autoStory(t), g);
    }
    const s = sim.get();
    s.now = at;
    return makePose(s, g);
  });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size?.width ?? 1, size?.height ?? 1));
    const p = pose.get();
    if (g && layers && p) drawFrame(canvas, g, layers, p);
    return recorder.finishRecordingAsPicture();
  });

  const labelY = g ? g.by + g.D * 0.3 : 0;
  const centre = g ? g.by : 0;
  const pressStyle = useAnimatedStyle(() => {
    const s = 1 - 0.03 * (pose.get()?.press ?? 0);
    return { transform: [{ translateY: (labelY - centre) * (s - 1) }, { scale: s }] };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {g && layers ? (
        <GestureDetector gesture={press}>
          <View style={StyleSheet.absoluteFill} accessible accessibilityRole="button" accessibilityLabel="Start the movie. Hold the pan to pop the popcorn, then let go.">
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          </View>
        </GestureDetector>
      ) : null}

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 14 }]}>
        <Text style={styles.kicker}>REEL NIGHT</Text>
        <Text style={styles.title}>Friday, 8 pm</Text>
        <Text style={styles.subtitle}>Living room · 4 guests · Sofa seats</Text>
      </View>

      {g ? (
        <>
          <View pointerEvents="none" style={[styles.card, { top: g.cardY + 17, left: g.cardX + 86, right: g.w - g.bowlX + 40 }]}>
            <Text style={styles.cardKicker}>TONIGHT’S PICK</Text>
            <Text style={styles.cardTitle} numberOfLines={1}>
              Moonlit Harbor
            </Text>
            <Text style={styles.cardMeta} numberOfLines={1}>
              Drama · 1 h 52 min · Rated 4.6
            </Text>
          </View>

          <Animated.View pointerEvents="none" style={[styles.label, { top: g.by + g.D * 0.215, left: g.bx - g.D * 0.36, width: g.D * 0.72 }, pressStyle]}>
            <RollingLine pose={pose} a="Hold to pop" b="Fresh, salted, buttered" style={styles.caption} reduced={reduced} />
            <RollingLine pose={pose} a="Start the movie" b="Enjoy the show" style={styles.cta} reduced={reduced} />
          </Animated.View>

          <View pointerEvents="none" style={[styles.footer, { top: g.footerY }]}>
            {[
              ['22', 'Kernels'],
              ['Sea salt', 'Seasoning'],
              ['3 min', 'Pop time'],
            ].map(([value, label]) => (
              <View key={label} style={styles.footItem}>
                <Text style={styles.footValue} numberOfLines={1}>
                  {value}
                </Text>
                <Text style={styles.footLabel}>{label}</Text>
              </View>
            ))}
          </View>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#16070C', overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24, gap: 4 },
  kicker: { color: GOLD, fontSize: 12, fontWeight: '700', letterSpacing: 3 },
  title: { color: INK, fontSize: 32, fontWeight: '700', letterSpacing: -0.6 },
  subtitle: { color: MUTE, fontSize: 15, fontWeight: '500' },
  card: { position: 'absolute', gap: 3 },
  cardKicker: { color: GOLD, fontSize: 11, fontWeight: '700', letterSpacing: 1.4 },
  cardTitle: { color: INK, fontSize: 20, fontWeight: '600' },
  cardMeta: { color: MUTE, fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  label: { position: 'absolute', alignItems: 'center', gap: 2 },
  rollBox: { height: 28, alignSelf: 'stretch', overflow: 'hidden' },
  rollText: { position: 'absolute', left: 0, right: 0, textAlign: 'center' },
  caption: { color: FAINT, fontSize: 12, fontWeight: '600', letterSpacing: 1.2, top: 8 },
  cta: { color: INK, fontSize: 21, fontWeight: '700', letterSpacing: -0.2, top: 1 },
  footer: { position: 'absolute', left: 16, right: 16, flexDirection: 'row' },
  footItem: { flex: 1, alignItems: 'center', gap: 2, paddingTop: 6 },
  footValue: { color: INK, fontSize: 16, fontWeight: '600', fontVariant: ['tabular-nums'] },
  footLabel: { color: FAINT, fontSize: 12, fontWeight: '500' },
});
