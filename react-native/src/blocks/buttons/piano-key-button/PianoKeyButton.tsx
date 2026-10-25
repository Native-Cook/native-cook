import { Canvas, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Defs, Path, Stop, LinearGradient as SvgGradient } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { type Fonts, type Layers, bakeBackground, bakeOverlay, drawFrame, makeIvoryGrain, makeScene } from './pianoKeyArt';
import {
  BACK_FOR,
  IDLE_BEFORE_BACK,
  KEY_COUNT,
  KEY_SPRING,
  LOOP,
  type Pose,
  STREAK_FROM,
  clamp01,
  loopPose,
  restPose,
  seg,
} from './pianoKeyMotion';

/*
 Piano Key Button — "Keys", a music-lessons app whose three calls to action are real piano keys.

 Look: ebony lacquer, a lesson card with a small stave (C, E, G and the C + G chord), a fallboard
 rail with a gold pinstripe, a dark recess where the hammers and their brass strings sit, a
 burgundy felt strip, then three big ivory keys (lengthwise grain, side shading, a front lip) with
 two black keys between them. Each key is a CTA: "Warm up", "Lesson 14", "Free play".

 Press: the key pivots down about its far edge (it foreshortens 5 pt, narrows 3 pt a side, its
 lip shrinks, it darkens toward the front and its neighbours shade its sides), the felt's shadow
 tightens, the hammer behind flicks up 12 pt and falls back, its three strings brighten while they
 ring, three rings of the key's colour spread from it (1.3 s, 0.12 s apart), the label rolls up
 into the note name (C / E / G), the note on the stave lights and the streak chip pulses. Two
 fingers on C and G play the chord, which completes the exercise: the streak rolls 12 → 13 days.

 Motion: the key is a spring (ω 40 rad/s, ζ 0.62); autoPlay is the analytic response of that
 spring to the script (pianoKeyMotion.ts), so every frame is a function of the loop time. A touch
 takes over at once; 2 s after the last finger lifts the screen eases back to rest (0.6 s) and the
 loop starts again from t = 0. Reduced motion: no loop, no rings or hammer flicks; keys still press.
*/

type Props = {
  /** Plays the 8 s practice loop (C, E, G, then the chord) until touched. */
  autoPlay?: boolean;
};

const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });
const INK = '#F3E7D6';
const MUTE = 'rgba(243,231,214,0.62)';
const FAINT = 'rgba(243,231,214,0.42)';
const AMBER = '#F2B35B';
const DIGIT_H = 22;

/** Mode of the block: the loop plays, a finger plays, or the screen eases back to rest. */
const AUTO = 0;
const TOUCH = 1;
const BACK = 2;

function buzz(kind: 'key' | 'chord') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'chord') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** The units column of the streak: rolls between whole days, so it rests on a digit. */
function StreakDigits({ streak }: { streak: SharedValue<number> }) {
  const units = useAnimatedStyle(() => ({ transform: [{ translateY: -(streak.get() - 10) * DIGIT_H }] }));
  const rows = [];
  for (let i = 0; i <= 9; i += 1) rows.push(i);
  return (
    <View style={styles.digits}>
      <Text style={styles.digit}>1</Text>
      <View style={styles.digitWindow}>
        <Animated.View style={units}>
          {rows.map((d) => (
            <Text key={d} style={styles.digit}>
              {d}
            </Text>
          ))}
        </Animated.View>
      </View>
    </View>
  );
}

function FlameIcon() {
  return (
    <Svg width={12} height={15} viewBox="0 0 12 15">
      <Defs>
        <SvgGradient id="pkb-flame" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor="#FFD58A" />
          <Stop offset="0.55" stopColor="#F2A24B" />
          <Stop offset="1" stopColor="#D9682F" />
        </SvgGradient>
      </Defs>
      <Path
        d="M6.2 0.6C6.8 3 9.2 4.4 10.4 6.8C11.9 9.9 10.1 14.2 6 14.4C2.2 14.6 0.2 11.4 1.1 8.4C1.6 6.8 2.8 5.9 3.2 4.4C4 5.3 4.2 6.4 4.1 7.4C5.6 6 6.6 3.4 6.2 0.6Z"
        fill="url(#pkb-flame)"
      />
    </Svg>
  );
}

export function PianoKeyButton({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);

  const scene = useMemo(() => (size ? makeScene(size.width, size.height, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const fonts = useMemo<Fonts>(
    () => ({
      title: matchFont({ fontFamily: SANS, fontSize: 17, fontWeight: '600' }),
      sub: matchFont({ fontFamily: SANS, fontSize: 13, fontWeight: '400' }),
      note: matchFont({ fontFamily: SERIF, fontSize: 46, fontWeight: '700' }),
      meter: matchFont({ fontFamily: SERIF, fontSize: 15, fontWeight: '700' }),
    }),
    [],
  );
  const layers = useMemo<Layers | null>(() => {
    if (!scene) return null;
    const scale = Math.min(3, PixelRatio.get());
    const background = bakeBackground(scene, fonts, scale);
    const overlay = bakeOverlay(scene, scale);
    return background && overlay ? { background, overlay } : null;
  }, [scene, fonts]);
  const ivory = useMemo(() => makeIvoryGrain(), []);

  // The clock and the mode, on the UI thread.
  const now = useSharedValue(0);
  /** The frame clock, kept running for gestures even while nothing is drawn. */
  const clock = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  const mode = useSharedValue(AUTO);
  const lastTouch = useSharedValue(0);
  const backUntil = useSharedValue(0);
  // A finger's state per key: travel (spring), label roll, last down and up times, fingers on it.
  const d0 = useSharedValue(0);
  const d1 = useSharedValue(0);
  const d2 = useSharedValue(0);
  const l0 = useSharedValue(0);
  const l1 = useSharedValue(0);
  const l2 = useSharedValue(0);
  const downAt = useSharedValue<number[]>([-99, -99, -99]);
  const upAt = useSharedValue<number[]>([-99, -99, -99]);
  const fingers = useSharedValue<number[]>([0, 0, 0]);
  const owner = useSharedValue<Record<number, number>>({});
  const streak = useSharedValue(STREAK_FROM);
  const pulseAt = useSharedValue(-99);
  const chorded = useSharedValue(0);

  useFrameCallback((frame) => {
    const t = frame.timestamp / 1000;
    clock.set(t);
    if (loopStart.get() < 0) loopStart.set(t);
    // With nothing playing and no finger, the frame stays as it is.
    if (!auto && mode.get() === AUTO && now.get() > 0) return;
    now.set(t);
    const m = mode.get();
    if (m === TOUCH) {
      const f = fingers.get();
      if (f[0] + f[1] + f[2] === 0 && t - lastTouch.get() > IDLE_BEFORE_BACK) {
        mode.set(BACK);
        backUntil.set(t + BACK_FOR);
        streak.set(withTiming(STREAK_FROM, { duration: BACK_FOR * 1000 }));
        chorded.set(0);
      }
    } else if (m === BACK && t >= backUntil.get()) {
      mode.set(AUTO);
      loopStart.set(t);
    }
  });

  function depthOf(i: number) {
    'worklet';
    return i === 0 ? d0 : i === 1 ? d1 : d2;
  }

  function labelOf(i: number) {
    'worklet';
    return i === 0 ? l0 : i === 1 ? l1 : l2;
  }

  /** The touch pose, built from the springs and the press times. */
  function touchPose(at: number): Pose {
    'worklet';
    const dn = downAt.get();
    const up = upAt.get();
    const keys = [];
    for (let i = 0; i < KEY_COUNT; i += 1) {
      const held = up[i] < dn[i];
      keys.push({ downAge: at - dn[i], upAge: held ? -1 : at - up[i], depth: depthOf(i).get(), label: labelOf(i).get() });
    }
    const notes = [0, 0, 0, 0];
    for (let i = 0; i < KEY_COUNT; i += 1) {
      const held = up[i] < dn[i];
      notes[i] = seg(at, dn[i], dn[i] + 0.08) * (held ? 1 : 1 - seg(at, up[i] + 0.5, up[i] + 1.1));
    }
    notes[3] = Math.min(notes[0], notes[2]);
    const p = (at - pulseAt.get()) / 0.6;
    let pulse = p > 0 && p < 1 ? Math.sin(Math.PI * p) * (1 - p * 0.4) : 0;
    for (let i = 0; i < KEY_COUNT; i += 1) if (at >= dn[i]) pulse = Math.max(pulse, 0.3 * Math.exp(-(at - dn[i]) * 6));
    return { keys, notes, streak: streak.get(), pulse };
  }

  const pose = useDerivedValue<Pose>(() => {
    const at = now.get();
    const m = mode.get();
    if (m !== AUTO) return touchPose(at);
    if (!auto) return restPose();
    return loopPose((((at - loopStart.get()) % LOOP) + LOOP) % LOOP);
  });

  /** Hands the loop's pose to the springs, so a finger never makes anything jump. */
  function takeOver(at: number) {
    'worklet';
    if (mode.get() === TOUCH) return;
    const p = pose.get();
    const dn = [-99, -99, -99];
    const up = [-99, -99, -99];
    for (let i = 0; i < KEY_COUNT; i += 1) {
      const k = p.keys[i];
      depthOf(i).set(k.depth);
      labelOf(i).set(k.label);
      if (k.downAge < 50) {
        dn[i] = at - k.downAge;
        up[i] = k.upAge < 0 ? at - k.downAge - 1 : at - k.upAge;
      }
      // A key the loop holds down is let go now.
      if (k.upAge < 0) {
        up[i] = at;
        depthOf(i).set(withSpring(0, KEY_SPRING));
        labelOf(i).set(withDelay(350, withTiming(0, { duration: 260 })));
      }
    }
    downAt.set(dn);
    upAt.set(up);
    streak.set(withTiming(Math.round(p.streak), { duration: 300 }));
    mode.set(TOUCH);
  }

  function press(i: number, at: number) {
    'worklet';
    const f = fingers.get().slice();
    f[i] += 1;
    fingers.set(f);
    if (f[i] > 1) return;
    const dn = downAt.get().slice();
    dn[i] = at;
    downAt.set(dn);
    depthOf(i).set(withSpring(1, KEY_SPRING));
    labelOf(i).set(withTiming(1, { duration: 240 }));
    // C and G held together is the exercise's chord.
    if (f[0] > 0 && f[2] > 0 && chorded.get() === 0) {
      chorded.set(1);
      pulseAt.set(at);
      streak.set(withDelay(200, withTiming(STREAK_FROM + 1, { duration: 400 })));
      scheduleOnRN(buzz, 'chord');
    } else {
      scheduleOnRN(buzz, 'key');
    }
  }

  function release(i: number, at: number) {
    'worklet';
    const f = fingers.get().slice();
    f[i] = Math.max(0, f[i] - 1);
    fingers.set(f);
    if (f[i] > 0) return;
    const up = upAt.get().slice();
    up[i] = at;
    upAt.set(up);
    depthOf(i).set(withSpring(0, KEY_SPRING));
    labelOf(i).set(withDelay(350, withTiming(0, { duration: 260 })));
  }

  function keyAt(x: number, y: number) {
    'worklet';
    if (!scene || y < scene.keyTop - 12 || y > scene.keyBottom + 14) return -1;
    for (let i = 0; i < KEY_COUNT; i += 1) {
      if (x >= scene.keyX[i] - scene.gap / 2 && x <= scene.keyX[i] + scene.keyW + scene.gap / 2) return i;
    }
    return -1;
  }

  const gesture = Gesture.Manual()
    .onTouchesDown((e, manager) => {
      const at = clock.get();
      let any = false;
      const own = { ...owner.get() };
      for (const t of e.changedTouches) {
        const i = keyAt(t.x, t.y);
        if (i < 0) continue;
        if (!any) {
          takeOver(at);
          any = true;
        }
        own[t.id] = i;
        press(i, at);
      }
      owner.set(own);
      lastTouch.set(at);
      if (any) manager.activate();
      else if (Object.keys(own).length === 0) manager.fail();
    })
    .onTouchesUp((e, manager) => {
      const at = clock.get();
      const own = { ...owner.get() };
      for (const t of e.changedTouches) {
        const i = own[t.id];
        if (i === undefined) continue;
        delete own[t.id];
        release(i, at);
      }
      owner.set(own);
      lastTouch.set(at);
      if (Object.keys(own).length === 0) manager.end();
    })
    .onTouchesCancelled((e, manager) => {
      const at = clock.get();
      const own = { ...owner.get() };
      for (const t of e.changedTouches) {
        const i = own[t.id];
        if (i === undefined) continue;
        delete own[t.id];
        release(i, at);
      }
      owner.set(own);
      lastTouch.set(at);
      if (Object.keys(own).length === 0) manager.end();
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size?.width ?? 1, size?.height ?? 1));
    if (scene && layers) {
      const p = pose.get();
      if (reduced) {
        // No ripples or hammer flicks: the key still travels and its label still turns.
        for (let i = 0; i < p.keys.length; i += 1) p.keys[i].downAge = 99;
      }
      drawFrame(canvas, scene, layers, p, fonts, ivory);
    }
    return recorder.finishRecordingAsPicture();
  });

  const chipStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 + 0.08 * pose.get().pulse }] }));
  const chipGlow = useAnimatedStyle(() => ({ opacity: clamp01(pose.get().pulse) }));
  const streakValue = useDerivedValue(() => pose.get().streak);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {scene && layers ? (
        <GestureDetector gesture={gesture}>
          <View
            style={StyleSheet.absoluteFill}
            accessible
            accessibilityRole="button"
            accessibilityLabel="Three piano keys: Warm up, Lesson 14 and Free play. Press C and G together to finish the exercise."
          >
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          </View>
        </GestureDetector>
      ) : null}

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 12 }]}>
        <View style={styles.headerText}>
          <Text style={styles.kicker}>KEYS</Text>
          <Text style={styles.title}>Daily practice</Text>
          <Text style={styles.subtitle}>Wednesday · 18 of 20 min</Text>
        </View>
        <Animated.View style={[styles.chip, chipStyle]}>
          <Animated.View style={[styles.chipGlow, chipGlow]} />
          <FlameIcon />
          <StreakDigits streak={streakValue} />
          <Text style={styles.chipLabel}>days</Text>
        </Animated.View>
      </View>

      {scene ? (
        <>
          <View pointerEvents="none" style={[styles.cardText, { top: scene.cardY + 20, left: scene.cardX + 20, width: scene.staffL - scene.cardX - 32 }]}>
            <Text style={styles.cardKicker}>TODAY’S EXERCISE</Text>
            <Text style={styles.cardTitle} numberOfLines={2}>
              C, E, G, then the chord
            </Text>
            <Text style={styles.cardMeta}>Lesson 14 · 72 BPM</Text>
          </View>
          <Text pointerEvents="none" style={[styles.hint, { top: scene.keyBottom + 28 }]}>
            Tap a key · Hold C and G together for the chord
          </Text>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#15100D', overflow: 'hidden' },
  header: { position: 'absolute', left: 20, right: 16, flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between' },
  headerText: { gap: 4, flexShrink: 1 },
  kicker: { color: AMBER, fontSize: 12, fontWeight: '700', letterSpacing: 3 },
  title: { color: INK, fontSize: 30, fontWeight: '700', letterSpacing: -0.5 },
  subtitle: { color: MUTE, fontSize: 15, fontWeight: '500' },
  chip: {
    marginTop: 20,
    height: 36,
    paddingHorizontal: 12,
    borderRadius: 18,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: '#2A1F18',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,214,160,0.22)',
    boxShadow: 'inset 0 1px 0 rgba(255,236,210,0.12), 0 1px 2px rgba(0,0,0,0.45), 0 8px 20px rgba(0,0,0,0.3)',
  },
  chipGlow: {
    position: "absolute", left: 0, right: 0, top: 0, bottom: 0,
    borderRadius: 18,
    backgroundColor: 'rgba(242,179,91,0.16)',
    boxShadow: '0 0 18px rgba(242,179,91,0.55)',
  },
  digits: { flexDirection: 'row', height: DIGIT_H },
  digitWindow: { height: DIGIT_H, overflow: 'hidden' },
  digit: { height: DIGIT_H, lineHeight: DIGIT_H, color: INK, fontSize: 17, fontWeight: '700', fontVariant: ['tabular-nums'] },
  chipLabel: { color: MUTE, fontSize: 13, fontWeight: '600' },
  cardText: { position: 'absolute', gap: 4 },
  cardKicker: { color: AMBER, fontSize: 11, fontWeight: '700', letterSpacing: 1.6 },
  cardTitle: { color: INK, fontSize: 17, fontWeight: '600' },
  cardMeta: { color: FAINT, fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  hint: { position: 'absolute', left: 24, right: 24, textAlign: 'center', color: FAINT, fontSize: 13, fontWeight: '500' },
});
