import { Canvas, Path, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedProps, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// A step of an invented cooking app, Simmer, with its timer living in one dark island in the
// middle of the page. At rest it is a small pill — "Timers" and an orange stopwatch. Tapped, it
// swells into a card with a minutes wheel, which rolls from 24 to 26; Start is pressed and the card
// squeezes into a wide bar with an orange ring that counts down from 26:00, Cancel and Pause beside
// it; Cancel shrinks it back into the pill. Each change of shape is one springy morph of the same
// black island. Tap the island to move it on.
//
// Layout (393 x 852 pt): 34 pt top row; step heading 34 pt heavy and a line; the island centred at
// 46 % of the height: pill 200 x 58 (r 29), card 312 x 250 (r 40), bar 344 x 100 (r 50); the
// wheel's rows 38 pt apart, the chosen one 40 pt; ring 72 pt; a 58 pt button at the bottom.
//
// autoPlay timeline (LOOP = 9 s):
//   0.0-0.8  rest: the pill
//   0.8-1.3  pill -> card (ease-out back); "Timers" fades out, the wheel in
//   1.6-2.6  the wheel rolls 24 -> 26 (ease-in-out cubic)
//   2.6-2.9  Start is pressed (scale 0.9 and back)
//   2.9-3.4  card -> bar (ease-out back); the ring and 26:00 fade in
//   3.4-7.6  the countdown runs (whole seconds) and the ring empties a little
//   7.3-7.6  Cancel is pressed
//   7.6-8.1  bar -> pill (ease-out back)
//   8.1-9.0  rest, identical to t = 0
// Tapping the island jumps to its next change (light haptic); the loop goes on from there.

/** Seconds. */
export const LOOP = 9;
const PILL = { w: 200, h: 58, r: 29 };
const CARD = { w: 312, h: 250, r: 40 };
const BAR = { w: 344, h: 100, r: 50 };
const ROW = 38;
const RING = 72;
const FIRST = 18;
const COUNT = 14;

const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function easeOutBack(x: number) {
  'worklet';
  const k = 1.3;
  return 1 + (k + 1) * Math.pow(x - 1, 3) + k * Math.pow(x - 1, 2);
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
function lerp(a: number, b: number, u: number) {
  'worklet';
  return a + (b - a) * u;
}

/** The island's shape and what it shows, `t` s into the loop. */
function islandAt(t: number) {
  'worklet';
  let w = PILL.w;
  let h = PILL.h;
  let r = PILL.r;
  if (t >= 0.8 && t < 2.9) {
    const u = easeOutBack(seg(t, 0.8, 1.3));
    w = lerp(PILL.w, CARD.w, u);
    h = lerp(PILL.h, CARD.h, u);
    r = lerp(PILL.r, CARD.r, u);
  } else if (t >= 2.9 && t < 7.6) {
    const u = easeOutBack(seg(t, 2.9, 3.4));
    w = lerp(CARD.w, BAR.w, u);
    h = lerp(CARD.h, BAR.h, u);
    r = lerp(CARD.r, BAR.r, u);
  } else if (t >= 7.6) {
    const u = easeOutBack(seg(t, 7.6, 8.1));
    w = lerp(BAR.w, PILL.w, u);
    h = lerp(BAR.h, PILL.h, u);
    r = lerp(BAR.r, PILL.r, u);
  }
  const pill = 1 - seg(t, 0.8, 1.0) + seg(t, 7.8, 8.1);
  const card = seg(t, 1.0, 1.3) * (1 - seg(t, 2.9, 3.1));
  const bar = seg(t, 3.1, 3.4) * (1 - seg(t, 7.6, 7.8));
  const minutes = 24 + 2 * easeInOutCubic(seg(t, 1.6, 2.6));
  const start = 1 - 0.1 * Math.sin(Math.PI * seg(t, 2.6, 2.9));
  const cancel = 1 - 0.12 * Math.sin(Math.PI * seg(t, 7.3, 7.6));
  const running = Math.max(0, t - 3.4);
  return { w, h, r, pill, card, bar, minutes, start, cancel, running };
}

function WheelRow({ n, minutes }: { n: number; minutes: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const d = Math.abs(n - minutes.get());
    const near = clamp01(1 - d);
    return { opacity: 0.28 + 0.72 * near, transform: [{ scale: 0.68 + 0.32 * near }] };
  });
  return (
    <Animated.Text style={[styles.wheelText, style]}>
      {n}
    </Animated.Text>
  );
}

type Props = {
  brand?: string;
  /** Plays the timer's round by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function TimerPillExpand({ brand = 'Simmer', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.46;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const clock = useSharedValue(reduced ? 3.6 : 0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    if (!autoPlay) return;
    clock.set((((elapsed.get() - origin.get()) % LOOP) + LOOP) % LOOP);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const island = useDerivedValue(() => islandAt(clock.get()));
  const minutes = useDerivedValue(() => island.get().minutes);

  const shapeStyle = useAnimatedStyle(() => {
    const s = island.get();
    return { width: s.w, height: s.h, borderRadius: s.r, left: cx - s.w / 2, top: cy - s.h / 2 };
  });
  const pillStyle = useAnimatedStyle(() => ({ opacity: island.get().pill }));
  const cardStyle = useAnimatedStyle(() => ({ opacity: island.get().card }));
  const barStyle = useAnimatedStyle(() => ({ opacity: island.get().bar }));
  const wheelStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -(island.get().minutes - FIRST) * ROW }] }));
  const startStyle = useAnimatedStyle(() => ({ transform: [{ scale: island.get().start }] }));
  const cancelStyle = useAnimatedStyle(() => ({ transform: [{ scale: island.get().cancel }] }));
  const countProps = useAnimatedProps(() => {
    const left = Math.max(0, 26 * 60 - Math.floor(island.get().running));
    const mm = Math.floor(left / 60);
    const ss = left % 60;
    const text = `${mm}:${ss < 10 ? '0' : ''}${ss}`;
    return { text, defaultValue: text } as never;
  });
  const ringEnd = useDerivedValue(() => 1 - island.get().running / (26 * 60) * 6);
  const ringPath = useDerivedValue(() => {
    const p = Skia.Path.Make();
    p.addArc(Skia.XYWHRect(4, 4, RING - 8, RING - 8), -90, 359.9);
    return p;
  });

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const tap = Gesture.Tap()
    .enabled(!reduced && autoPlay)
    .onEnd(() => {
      const t = clock.get();
      // Jump to the next change of shape.
      const next = t < 0.8 ? 0.8 : t < 2.6 ? 2.6 : t < 7.3 ? 7.3 : 0.8;
      origin.set(elapsed.get() - next);
      scheduleOnRN(haptic);
    });

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <View style={[styles.head, { top: insets.top + 8 }]} pointerEvents="none">
        <View style={styles.row}>
          <Text style={styles.brand}>{brand}</Text>
          <Text style={styles.meta}>Step 3 of 7</Text>
        </View>
        <Text style={styles.title}>Let the ragù simmer</Text>
        <Text style={styles.sub}>Lid half on, lowest flame. Stir every few minutes so nothing catches.</Text>
      </View>

      <GestureDetector gesture={tap}>
        <Animated.View style={[styles.island, shapeStyle]} accessible accessibilityRole="button" accessibilityLabel="Timer">
          <Animated.View style={[styles.layer, styles.pillLayer, pillStyle]} pointerEvents="none">
            <Text style={styles.pillText}>Timers</Text>
            <View style={styles.watch}>
              <View style={styles.watchHand} />
            </View>
          </Animated.View>

          <Animated.View style={[styles.layer, cardStyle]} pointerEvents="none">
            <View style={styles.wheelWindow}>
              <Animated.View style={[styles.wheel, wheelStyle]}>
                {Array.from({ length: COUNT }, (_, i) => (
                  <WheelRow key={i} n={FIRST + i} minutes={minutes} />
                ))}
              </Animated.View>
            </View>
            <Text style={styles.mins}>mins</Text>
            <Animated.View style={[styles.start, startStyle]}>
              <Text style={styles.startText}>Start</Text>
            </Animated.View>
          </Animated.View>

          <Animated.View style={[styles.layer, styles.barLayer, barStyle]} pointerEvents="none">
            <View style={styles.ringBox}>
              <Canvas style={StyleSheet.absoluteFill}>
                <Path path={ringPath} style="stroke" strokeWidth={3} color="rgba(255,159,10,0.25)" />
                <Path path={ringPath} style="stroke" strokeWidth={3} strokeCap="round" color="#FF9F0A" start={0} end={ringEnd} />
              </Canvas>
              <AnimatedTextInput editable={false} style={styles.count} animatedProps={countProps} defaultValue="26:00" />
            </View>
            <View style={styles.barButtons}>
              <Animated.View style={[styles.cancel, cancelStyle]}>
                <Text style={styles.cancelText}>Cancel</Text>
              </Animated.View>
              <View style={styles.pause}>
                <Text style={styles.pauseText}>Pause</Text>
              </View>
            </View>
          </Animated.View>
        </Animated.View>
      </GestureDetector>

      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]} pointerEvents="none">
        <View style={styles.tips}>
          <Text style={styles.tipTitle}>{"Chef's note"}</Text>
          <Text style={styles.tip}>It is ready when the oil pools on top and the sauce coats a spoon.</Text>
        </View>
        <View style={styles.button}>
          <Text style={styles.action}>Next step</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#ECEBE8' },
  head: { position: 'absolute', left: 24, right: 24 },
  row: { height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontSize: 18, fontWeight: '800', color: '#1C1C1E' },
  meta: { fontSize: 14, fontWeight: '600', color: 'rgba(28,28,30,0.55)' },
  title: { marginTop: 14, fontSize: 34, lineHeight: 38, fontWeight: '900', color: '#1C1C1E', letterSpacing: -1 },
  sub: { marginTop: 8, fontSize: 16, lineHeight: 22, color: 'rgba(28,28,30,0.65)' },
  island: { position: 'absolute', backgroundColor: '#2C2C2E', overflow: 'hidden', shadowColor: '#000', shadowOpacity: 0.18, shadowRadius: 18, shadowOffset: { width: 0, height: 10 }, elevation: 8 },
  layer: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0 },
  pillLayer: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 22 },
  pillText: { fontSize: 21, fontWeight: '500', color: '#FFFFFF' },
  watch: { width: 28, height: 28, borderRadius: 14, borderWidth: 2.5, borderColor: '#FF9F0A', alignItems: 'center', justifyContent: 'center' },
  watchHand: { width: 2.5, height: 9, borderRadius: 2, backgroundColor: '#FF9F0A', marginTop: -6, transform: [{ rotate: '-35deg' }] },
  wheelWindow: { position: 'absolute', left: 28, top: 0, bottom: 0, width: 64, overflow: 'hidden' },
  wheel: { position: 'absolute', left: 0, right: 0, top: 125 - ROW / 2 },
  wheelText: { height: ROW, lineHeight: ROW, fontSize: 40, fontWeight: '600', color: '#FFFFFF', fontVariant: ['tabular-nums'] },
  mins: { position: 'absolute', left: 92, top: 125 - 14, fontSize: 24, fontWeight: '500', color: '#FFFFFF' },
  start: { position: 'absolute', right: 18, bottom: 18, width: 74, height: 74, borderRadius: 37, backgroundColor: '#0E3B12', alignItems: 'center', justifyContent: 'center' },
  startText: { fontSize: 19, fontWeight: '600', color: '#32D74B' },
  barLayer: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14 },
  ringBox: { width: RING, height: RING, alignItems: 'center', justifyContent: 'center' },
  count: { fontSize: 17, fontWeight: '600', color: '#FFFFFF', textAlign: 'center', padding: 0, fontVariant: ['tabular-nums'] },
  barButtons: { flex: 1, flexDirection: 'row', justifyContent: 'flex-end', gap: 10 },
  cancel: { width: 64, height: 64, borderRadius: 32, backgroundColor: '#48484A', alignItems: 'center', justifyContent: 'center' },
  cancelText: { fontSize: 14, fontWeight: '600', color: '#FFFFFF' },
  pause: { width: 64, height: 64, borderRadius: 32, backgroundColor: '#3A2500', alignItems: 'center', justifyContent: 'center' },
  pauseText: { fontSize: 14, fontWeight: '600', color: '#FF9F0A' },
  footer: { position: 'absolute', left: 20, right: 20, bottom: 0 },
  tips: { padding: 18, borderRadius: 22, backgroundColor: '#FFFFFF', marginBottom: 14 },
  tipTitle: { fontSize: 15, fontWeight: '800', color: '#1C1C1E' },
  tip: { marginTop: 4, fontSize: 15, lineHeight: 21, color: 'rgba(28,28,30,0.65)' },
  button: { height: 58, borderRadius: 29, backgroundColor: '#1C1C1E', alignItems: 'center', justifyContent: 'center' },
  action: { fontSize: 17, fontWeight: '700', color: '#FFFFFF' },
});
