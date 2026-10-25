import { Canvas, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { type Fonts, type Layers, bakeDesk, bakeRegister, drawFrame, makeScene } from './morseKeyArt';
import {
  BACK_FOR,
  IDLE_BEFORE_BACK,
  LETTER_GAP,
  LOOP,
  type Pose,
  type Sim,
  keyDown,
  keyUp,
  loopPose,
  makeSim,
  simPose,
  stepSim,
} from './morseKeyMotion';

/*
 Morse Key Input — "Dot Dash", a puzzle app where the answer is keyed on a brass telegraph key.

 Look: a green leather desk, a telegram card with the clue typed on it and two answer boxes, a
 brass register (with a paper reel turning behind a window) that pays out a strip of tape, and a
 straight key on a walnut base: a brass lever on a trunnion, a black bakelite knob, a spring, a
 silver contact, binding posts wired to the register.

 Press the key: the lever pivots 3.3° about the trunnion onto the contact (a spring, ω 60 rad/s,
 ζ 0.5, stopped by the contact with a little bounce), the contact sparks blue-white and hums while
 closed, the register's pen drops onto the tape, and the tape — running at 34 pt/s while you key
 — prints a dot or a dash (longer than 0.2 s). After a 0.55 s pause the letter is decoded and rolls
 up into the next answer box; two letters show "Correct · +40" (or "Not quite"). 2 s after the
 last touch the printed tape tears off and falls, the answer rolls out and a fresh leader feeds
 out (0.9 s); then the loop (morseKeyMotion.ts, LOOP = 9 s, "HI" in real timing) starts again.
 Reduced motion: no loop; the key still works, without sparks.
*/

type Props = {
  /** Plays the 9 s loop that keys "HI" until touched. */
  autoPlay?: boolean;
};

const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });
const INK = '#EFE6D2';
const MUTE = 'rgba(239,230,210,0.64)';
const FAINT = 'rgba(239,230,210,0.44)';
const BRASS = '#D8B670';

const AUTO = 0;
const TOUCH = 1;
const BACK = 2;

function feel(kind: 'key' | 'letter' | 'done') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'key') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  else if (kind === 'letter') Haptics.selectionAsync().catch(() => {});
  else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}

export function MorseKeyInput({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);

  const scene = useMemo(() => (size ? makeScene(size.width, size.height, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const fonts = useMemo<Fonts>(
    () => ({
      type: matchFont({ fontFamily: MONO, fontSize: 12.5, fontWeight: '400' }),
      typeBold: matchFont({ fontFamily: MONO, fontSize: 11, fontWeight: '700' }),
      slot: matchFont({ fontFamily: MONO, fontSize: 36, fontWeight: '700' }),
      small: matchFont({ fontFamily: MONO, fontSize: 8.5, fontWeight: '700' }),
      chip: matchFont({ fontFamily: SANS, fontSize: 13, fontWeight: '600' }),
    }),
    [],
  );
  const layers = useMemo<Layers | null>(() => {
    if (!scene) return null;
    const scale = Math.min(3, PixelRatio.get());
    const desk = bakeDesk(scene, fonts, scale);
    const register = bakeRegister(scene, fonts, scale);
    return desk && register ? { desk, register } : null;
  }, [scene, fonts]);
  const leader = scene?.leader ?? 0;

  const now = useSharedValue(0);
  /** The frame clock, kept running for gestures even while nothing is drawn. */
  const clock = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  const mode = useSharedValue(AUTO);
  const lastTouch = useSharedValue(0);
  const sim = useSharedValue<Sim>(makeSim(0));

  const pose = useDerivedValue<Pose>(() => {
    const at = now.get();
    if (mode.get() !== AUTO) return simPose(sim.get(), at, leader);
    if (!auto) return loopPose(0, leader);
    return loopPose((((at - loopStart.get()) % LOOP) + LOOP) % LOOP, leader);
  });

  useFrameCallback((frame) => {
    const at = frame.timestamp / 1000;
    clock.set(at);
    if (loopStart.get() < 0) loopStart.set(at);
    if (!auto && mode.get() === AUTO && now.get() > 0) return;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    const m = mode.get();
    if (m !== AUTO) {
      const s = sim.get();
      if (stepSim(s, at, dt) === 1) {
        const done = s.decoded.length === 2 && s.correct === 1;
        scheduleOnRN(feel, done ? 'done' : 'letter');
      }
      if (m === TOUCH && !s.held && s.buffer.length === 0 && at - lastTouch.get() > IDLE_BEFORE_BACK) {
        mode.set(BACK);
        s.torn = { len: s.base + s.run, run: s.run, marks: s.marks.slice() };
        s.backAt = at;
      } else if (m === BACK && at >= s.backAt + BACK_FOR) {
        sim.set(makeSim(leader));
        mode.set(AUTO);
        loopStart.set(at);
      }
    }
    now.set(at);
  });

  /** Hands whatever the loop shows to the simulation, so a finger never makes anything jump. */
  function takeOver(at: number) {
    'worklet';
    if (mode.get() === TOUCH) return;
    const p = pose.get();
    const s = makeSim(leader);
    if (mode.get() === AUTO && p.fall === 0) {
      s.base = p.tape.len - p.tape.run;
      s.run = p.tape.run;
      s.marks = p.tape.marks.slice();
      s.depth = p.depth;
      for (let i = 0; i < 2; i += 1) {
        if (p.slots[i].ch && p.slots[i].in > 0.5 && p.slots[i].out < 0.5) {
          s.decoded.push(p.slots[i].ch);
          s.slotAt.push(at - 1);
        }
      }
      // A letter the loop was in the middle of keying is finished as dots.
      s.buffer = p.buffer;
      s.upAt = at;
      if (s.decoded.length === 2) {
        s.chipAt = at - 1;
        s.correct = 1;
      }
    } else {
      s.base = p.tape.len;
    }
    sim.set(s);
    mode.set(TOUCH);
  }

  const key = Gesture.Manual()
    .onTouchesDown((e, manager) => {
      if (!scene) return;
      const t = e.changedTouches[0];
      if (e.numberOfTouches > 1 || !t || t.y < scene.tapeY + scene.tapeH) {
        if (e.numberOfTouches <= 1) manager.fail();
        return;
      }
      const at = clock.get();
      takeOver(at);
      keyDown(sim.get(), at);
      lastTouch.set(at);
      manager.activate();
      scheduleOnRN(feel, 'key');
    })
    .onTouchesUp((e, manager) => {
      const at = clock.get();
      if (e.numberOfTouches > 0) return;
      keyUp(sim.get(), at);
      lastTouch.set(at);
      manager.end();
    })
    .onTouchesCancelled((_e, manager) => {
      const at = clock.get();
      keyUp(sim.get(), at);
      lastTouch.set(at);
      manager.end();
    });

  /** "Clear tape": tear it off now. */
  const clear = () => {
    scheduleOnUI(() => {
      'worklet';
      const at = clock.get();
      takeOver(at);
      const s = sim.get();
      s.held = false;
      s.buffer = '';
      s.torn = { len: s.base + s.run, run: s.run, marks: s.marks.slice() };
      s.backAt = at;
      mode.set(BACK);
    });
  };

  /** "Send": finish the letter in progress at once. */
  const send = () => {
    scheduleOnUI(() => {
      'worklet';
      const at = clock.get();
      takeOver(at);
      const s = sim.get();
      s.upAt = at - LETTER_GAP - 0.01;
      lastTouch.set(at);
    });
  };

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size?.width ?? 1, size?.height ?? 1));
    if (scene && layers) {
      const p = pose.get();
      if (reduced) p.sparkAge = 99;
      drawFrame(canvas, scene, layers, p, fonts);
    }
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {scene && layers ? (
        <GestureDetector gesture={key}>
          <View style={StyleSheet.absoluteFill} accessible accessibilityRole="button" accessibilityLabel="Telegraph key. Tap for a dot, hold for a dash; pause to finish a letter.">
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          </View>
        </GestureDetector>
      ) : null}

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 12 }]}>
        <Text style={styles.kicker}>DOT DASH</Text>
        <Text style={styles.title}>Send your answer</Text>
        <Text style={styles.subtitle}>Puzzle 27 · The telegraph room</Text>
      </View>

      {scene ? (
        <View style={[styles.bottom, { top: scene.baseTop + 76, bottom: insets.bottom + 8 }]}>
          <View pointerEvents="none" style={styles.legend}>
            <View style={styles.legendItem}>
              <View style={styles.dot} />
              <Text style={styles.legendText}>Tap</Text>
            </View>
            <View style={styles.legendItem}>
              <View style={styles.dash} />
              <Text style={styles.legendText}>Hold</Text>
            </View>
            <View style={styles.legendItem}>
              <View style={styles.space} />
              <Text style={styles.legendText}>Pause for the next letter</Text>
            </View>
          </View>
          <View style={styles.buttons}>
            <Pressable accessibilityRole="button" onPress={clear} style={({ pressed }) => [styles.button, pressed && styles.pressed]}>
              <Text style={styles.buttonText}>Clear tape</Text>
            </Pressable>
            <Pressable accessibilityRole="button" onPress={send} style={({ pressed }) => [styles.button, styles.primary, pressed && styles.pressed]}>
              <Text style={styles.primaryText}>Send letter</Text>
            </Pressable>
          </View>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#111D1A', overflow: 'hidden' },
  header: { position: 'absolute', left: 20, right: 20, gap: 4 },
  kicker: { color: BRASS, fontSize: 12, fontWeight: '700', letterSpacing: 3 },
  title: { color: INK, fontSize: 30, fontWeight: '700', letterSpacing: -0.5 },
  subtitle: { color: MUTE, fontSize: 15, fontWeight: '500' },
  bottom: { position: 'absolute', left: 20, right: 20, justifyContent: 'space-evenly', gap: 12 },
  legend: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 18 },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  dot: { width: 7, height: 7, borderRadius: 3.5, backgroundColor: INK },
  dash: { width: 18, height: 6, borderRadius: 3, backgroundColor: INK },
  space: { width: 18, height: 6, borderLeftWidth: 1.5, borderRightWidth: 1.5, borderBottomWidth: 1.5, borderColor: INK, marginTop: 2 },
  legendText: { color: FAINT, fontSize: 13, fontWeight: '500' },
  buttons: { flexDirection: 'row', gap: 12 },
  button: {
    flex: 1,
    height: 52,
    borderRadius: 26,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(239,230,210,0.07)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(239,230,210,0.18)',
  },
  primary: {
    backgroundColor: '#D8B670',
    borderColor: '#E9CC8E',
    boxShadow: 'inset 0 1px 0 rgba(255,246,220,0.6), 0 1px 2px rgba(0,0,0,0.45), 0 10px 24px rgba(216,182,112,0.18)',
  },
  pressed: { transform: [{ scale: 0.97 }] },
  buttonText: { color: INK, fontSize: 16, fontWeight: '600' },
  primaryText: { color: '#2A2112', fontSize: 16, fontWeight: '700' },
});
