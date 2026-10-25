import { Canvas, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { type Fonts, type Layers, type Layout, bakeLayers, drawFrame, makeLayout } from './corkArt';
import { BUBBLES_DONE, type Clocks, LOOP, POP_AT, type Pose, SWAP_AT, SWAP_FOR, autoClocks, makePose } from './corkMotion';
import { FOAM_SKSL } from './corkShader';

/*
 Cork Pop Button — "Maison Vale", a wine shop's order-placed screen whose call to action is a
 bottle of champagne: the "Celebrate" label on its shoulder, gold foil and a wire cage on the neck.

 Drag the cork up: it twists against the finger (sideways drag rocks it), the cage's key spins
 loose and the cork inches out (16 pt at most, falling off: 16 (1 - e^(-pull / 70))). Past 105 pt
 (or a fast flick past 55 pt) it pops: cork and cage fly off with a spin, a cold puff of CO2,
 foam gushes up and runs down the neck, gold bubbles rise across the screen, the order card lifts
 with a sheen, and the line under it rolls to "Cheers". Let go before that and the cork settles
 back. 2 s after the last touch the open bottle sinks away and a corked one rises in its place,
 then the loop starts again (corkMotion.ts has the timeline).
 Reduced motion: no loop; a tap on the cork rolls the line (a fade) and nothing flies.
*/

type Props = {
  /** Plays the 9 s twist-pop-celebrate loop until touched. */
  autoPlay?: boolean;
};

const EFFECT = Skia.RuntimeEffect.Make(FOAM_SKSL);
const P3 = Platform.OS === 'ios' ? 1 : 0;
const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });
const INK = '#F3EBD6';
const MUTE = 'rgba(243,235,214,0.64)';
const GOLD = '#D9B56A';

const AUTO = 0;
const TOUCH = 1;
/** The swap under a finger: the loop's 1.6 s one, played in this long. */
const SWAP_TOUCH = 0.9;
const POP_PULL = 105;
const ROLL = 24;

type Sim = { inch: number; rock: number; loose: number; strain: number; popAt: number; backAt: number; swapFor: number; lastTouch: number; grab: number; tick: number };

function freshSim(): Sim {
  'worklet';
  return { inch: 0, rock: 0, loose: 0, strain: 0, popAt: -1, backAt: -1, swapFor: SWAP_TOUCH, lastTouch: 0, grab: 0, tick: 0 };
}

function feel(kind: 'tick' | 'pop' | 'grab') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'pop') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  else if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function RollingLine({ pose, a, b, reduced }: { pose: SharedValue<Pose | null>; a: string; b: string; reduced: boolean }) {
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
      <Animated.Text style={[styles.hint, first]} numberOfLines={1}>
        {a}
      </Animated.Text>
      <Animated.Text style={[styles.hint, styles.cheers, second]} numberOfLines={1}>
        {b}
      </Animated.Text>
    </View>
  );
}

export function CorkPopButton({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);

  const fonts = useMemo<Fonts>(
    () => ({
      kicker: matchFont({ fontFamily: SANS, fontSize: 11, fontWeight: '700' }),
      mono: matchFont({ fontFamily: MONO, fontSize: 22, fontWeight: '700' }),
      row: matchFont({ fontFamily: SANS, fontSize: 15, fontWeight: '400' }),
      rowBold: matchFont({ fontFamily: SANS, fontSize: 15, fontWeight: '600' }),
      script: matchFont({ fontFamily: SERIF, fontSize: 27, fontStyle: 'italic', fontWeight: '400' }),
      tiny: matchFont({ fontFamily: SANS, fontSize: 10, fontWeight: '700' }),
    }),
    [],
  );
  const g = useMemo<Layout | null>(() => (size ? makeLayout(size.width, size.height, insets.top) : null), [size, insets.top]);
  const layers = useMemo<Layers | null>(() => (g ? bakeLayers(g, fonts, Math.min(3, PixelRatio.get())) : null), [g, fonts]);

  const now = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  const mode = useSharedValue(AUTO);
  const sim = useSharedValue<Sim>(freshSim());
  const ignoring = useSharedValue(0);
  const quiet = useSharedValue<number[]>([0, -10]);

  useFrameCallback((frame) => {
    const at = frame.timestamp / 1000;
    if (loopStart.get() < 0) loopStart.set(at);
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    if (mode.get() === TOUCH) {
      const s = sim.get();
      if (s.grab === 0 && s.popAt < 0) {
        // Let go before the pop: the cork settles back and the cage tightens.
        const k = 1 - Math.exp(-dt * 9);
        s.inch -= s.inch * k;
        s.rock -= s.rock * k;
        s.loose -= s.loose * k;
        s.strain -= s.strain * k;
      }
      const idle = s.grab === 0 && at - s.lastTouch > 2;
      if (idle && s.popAt < 0) {
        sim.set(freshSim());
        mode.set(AUTO);
        loopStart.set(at);
      } else if (idle && s.backAt < 0 && at - s.popAt > 2.2) {
        s.backAt = at;
        s.swapFor = SWAP_TOUCH;
      } else if (s.backAt >= 0 && at - s.backAt > s.swapFor && at - s.popAt > BUBBLES_DONE) {
        sim.set(freshSim());
        mode.set(AUTO);
        loopStart.set(at);
      }
    }
    now.set(at);
  });

  /** Hands what the loop shows to the simulation, so a touch never makes anything jump. */
  function takeOver(at: number) {
    'worklet';
    const s = freshSim();
    s.lastTouch = at;
    if (mode.get() === AUTO && auto) {
      const t = (((at - loopStart.get()) % LOOP) + LOOP) % LOOP;
      const c = autoClocks(t);
      if (t < POP_AT) {
        s.inch = c.inch;
        s.rock = c.rock;
        s.loose = c.loose;
      } else {
        s.popAt = at - (t - POP_AT);
        if (t >= SWAP_AT) {
          s.backAt = at - (t - SWAP_AT);
          s.swapFor = SWAP_FOR;
        }
      }
    }
    sim.set(s);
    mode.set(TOUCH);
  }

  const pan = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => {
      if (!g) return;
      const near = Math.hypot(e.x - g.cx, e.y - (g.lipY - g.corkH * 0.5)) < 84;
      if (!near) {
        ignoring.set(1);
        return;
      }
      ignoring.set(0);
      const at = now.get();
      if (reduced) {
        const q = quiet.get();
        quiet.set([1 - q[0], at]);
        scheduleOnRN(feel, 'grab');
        return;
      }
      if (mode.get() === TOUCH) sim.get().lastTouch = at;
      else takeOver(at);
      const s = sim.get();
      if (s.popAt < 0) {
        s.grab = 1;
        s.tick = 0;
        scheduleOnRN(feel, 'grab');
      }
    })
    .onUpdate((e) => {
      if (ignoring.get() === 1 || reduced) return;
      const s = sim.get();
      const at = now.get();
      s.lastTouch = at;
      if (s.grab === 0 || s.popAt >= 0) return;
      const pull = Math.max(0, -e.translationY);
      s.inch = 16 * (1 - Math.exp(-pull / 70));
      s.rock = Math.max(-8, Math.min(8, e.translationX * 0.12));
      s.loose = Math.min(1, pull / 95 + Math.abs(e.translationX) / 300);
      s.strain = Math.max(0, Math.min(1, (pull - 60) / 45));
      const step = Math.floor(pull / 22);
      if (step > s.tick) {
        s.tick = step;
        scheduleOnRN(feel, 'tick');
      } else if (step < s.tick) s.tick = step;
      if (pull >= POP_PULL || (pull > 55 && e.velocityY < -1100)) {
        s.popAt = at;
        s.grab = 0;
        s.inch = 0;
        s.rock = 0;
        s.loose = 0;
        s.strain = 0;
        scheduleOnRN(feel, 'pop');
      }
    })
    .onFinalize(() => {
      if (ignoring.get() === 1 || reduced) {
        ignoring.set(0);
        return;
      }
      const s = sim.get();
      s.grab = 0;
      s.lastTouch = now.get();
    });

  const pose = useDerivedValue<Pose | null>(() => {
    if (!g) return null;
    const at = now.get();
    if (reduced) {
      const q = quiet.get();
      const k = Math.min(1, Math.max(0, (at - q[1]) / 0.3));
      const p = makePose(autoClocks(0), g);
      p.label = q[0] === 1 ? k : 1 - k;
      return p;
    }
    if (mode.get() === AUTO) {
      const t = auto ? (((at - loopStart.get()) % LOOP) + LOOP) % LOOP : 0;
      return makePose(autoClocks(t), g);
    }
    const s = sim.get();
    const c: Clocks = {
      inch: s.inch,
      rock: s.rock,
      loose: s.loose,
      shake: s.strain * 0.8 * Math.sin(2 * Math.PI * 41 * at),
      finger: 0,
      popE: s.popAt >= 0 ? at - s.popAt : -1,
      swapE: s.backAt >= 0 ? at - s.backAt : -1,
      swapFor: s.swapFor,
    };
    return makePose(c, g);
  });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size?.width ?? 1, size?.height ?? 1));
    const p = pose.get();
    if (g && layers && p) drawFrame(canvas, g, layers, p, EFFECT, P3);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {g && layers ? (
        <GestureDetector gesture={pan}>
          <View
            style={StyleSheet.absoluteFill}
            accessible
            accessibilityRole="button"
            accessibilityLabel="Celebrate. Drag the champagne cork up until it pops."
          >
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          </View>
        </GestureDetector>
      ) : null}

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 14 }]}>
        <Text style={styles.kicker}>MAISON VALE</Text>
        <Text style={styles.title}>Thank you, Ines</Text>
        <Text style={styles.subtitle}>Your order is placed and on its way</Text>
      </View>

      {g ? (
        <View pointerEvents="none" style={[styles.hintRow, { top: g.hintY }]}>
          <RollingLine pose={pose} a="Twist the cork up to celebrate" b="Cheers! See you on Thursday" reduced={reduced} />
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#0C1E18', overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24, gap: 4 },
  kicker: { color: GOLD, fontSize: 12, fontWeight: '700', letterSpacing: 3 },
  title: { color: INK, fontSize: 32, fontWeight: '700', letterSpacing: -0.6 },
  subtitle: { color: MUTE, fontSize: 15, fontWeight: '500' },
  hintRow: { position: 'absolute', left: 24, right: 24 },
  rollBox: { height: 24, overflow: 'hidden' },
  hint: { position: 'absolute', left: 0, right: 0, top: 2, textAlign: 'center', color: MUTE, fontSize: 15, fontWeight: '500' },
  cheers: { color: GOLD, fontWeight: '600' },
});
